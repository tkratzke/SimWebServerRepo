package com.skagit.sarops.tracker;

import java.util.ArrayList;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.Scenario;
import com.skagit.sarops.model.SearchObjectType;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.util.SimGlobalStrings;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.randomx.Randomx;

/** A ParticleSet holds the particles for a given scenario. */
public class ParticleSet {
	final public Tracker _tracker;
	/**
	 * The scenario corresponding to the ParticleSet. Not a back-pointer since
	 * _scenario does not contain "this" as a field.
	 */
	final public Scenario _scenario;
	/**
	 * The storage of the particles.
	 * <p>
	 * The ctor creates the array, but does not populate it. That occurs in
	 * {@link Tracker#createInitialCollectionOfParticles()}.
	 */
	final public Particle[] _particles;
	final private Particle[] _envMeanParticles;
	final private TreeMap<Integer, Integer> _distressTypeToCount;

	/** Simple constructor; does not initialize _particles. */
	ParticleSet(final Tracker tracker, final Scenario scenario,
			final int numberOfParticles) {
		_scenario = scenario;
		_particles = new Particle[numberOfParticles];
		_distressTypeToCount = new TreeMap<>();
		final int nSearchObjectTypes =
				tracker.getModel().getNSearchObjectTypes();
		_envMeanParticles = new Particle[nSearchObjectTypes];
		_tracker = tracker;
	}

	public int getNParticles(final int sotId) {
		final Integer numberOfParticles = _distressTypeToCount.get(sotId);
		return numberOfParticles == null ? 0 : numberOfParticles;
	}

	/** Advances all particles to simSecsS[iT]. */
	void timeUpdate(final long[] simSecsS, final int iT,
			final ParticlesFile particlesFile) {
		/**
		 * Create and start the threads, saving one "slice" for the current
		 * thread.
		 */
		final int nToCompute = _particles.length;
		final SimCaseManager.SimCase simCase = _tracker.getSimCase();
		final SimCaseManager simCaseManager = simCase.getSimCaseManager();
		final SimGlobalStrings simGlobalStrings = simCase.getSimGlobalStrings();
		/** Boilerplate for multithreading... */
		final int minNPerSlice = simGlobalStrings.getMinNPerSliceInTracker();
		final Object lockOnWorkersThreadPool =
				simCaseManager.getLockOnWorkersThreadPool();
		Future<?>[] futures = null;
		final ArrayList<Integer> notTaskedISlices = new ArrayList<>();
		int nSlices = (nToCompute + (minNPerSlice - 1)) / minNPerSlice;
		int nWorkers = 0;
		if (nSlices > 1) {
			synchronized (lockOnWorkersThreadPool) {
				final int nFreeWorkers = simCaseManager
						.getNFreeWorkerThreads(simCase, "Time Update In ParticleSet");
				nWorkers = Math.max(0, Math.min(nSlices - 1, nFreeWorkers));
				if (nWorkers < 2) {
					nWorkers = 0;
					nSlices = 1;
				} else {
					final int finalNSlices = nSlices = nWorkers + 1;
					futures = new Future<?>[nWorkers];
					for (int iWorker = 0; iWorker < nWorkers; ++iWorker) {
						final int finalIWorker = iWorker;
						final Runnable runnable = new Runnable() {
							@Override
							public void run() {
								/** ... to here. */
								runTimeUpdateSlice(finalIWorker, finalNSlices, simSecsS, iT,
										particlesFile);
							}
						};
						futures[iWorker] =
								simCaseManager.submitToWorkers(simCase, runnable);
						if (futures[iWorker] == null) {
							notTaskedISlices.add(iWorker);
						}
					}
				}
			}
		}
		for (int iSlice = nWorkers; iSlice < nSlices; ++iSlice) {
			notTaskedISlices.add(iSlice);
		}
		for (final int iSlice : notTaskedISlices) {
			runTimeUpdateSlice(iSlice, nSlices, simSecsS, iT, particlesFile);
			if (!_tracker.getKeepGoing()) {
				break;
			}
		}
		try {
			for (int iWorker = 0; iWorker < nWorkers; ++iWorker) {
				if (futures[iWorker] != null) {
					futures[iWorker].get();
				}
			}
		} catch (final ExecutionException e) {
			SimCaseManager.standardLogError(simCase, e);
		} catch (final InterruptedException e) {
			SimCaseManager.standardLogError(simCase, e);
		}
	}

	private void runTimeUpdateSlice(final int iWorker, final int nSlices,
			final long[] simSecsS, final int iT,
			final ParticlesFile particlesFile) {
		final int nCoreParticles = _particles.length;
		final Model model = _tracker.getModel();
		final int nSearchObjectTypes = model.getNSearchObjectTypes();
		final int nAllParticles = nCoreParticles + nSearchObjectTypes;
		final int iScenario = _scenario.getIScenario();
		for (int iParticleX = iWorker; iParticleX < nAllParticles;
				iParticleX += nSlices) {
			final Particle particle;
			final ParticleIndexes prtclIndxs;
			/** sotOrd stands for searchObjectTypeOrdinal. */
			final int iParticle;
			final int sotOrd;
			if (iParticleX < nCoreParticles) {
				particle = _particles[iParticleX];
				prtclIndxs =
						ParticleIndexes.getStandardOne(model, iScenario, iParticleX);

				iParticle = iParticleX;
				sotOrd = -1;
			} else {
				iParticle = -1;
				sotOrd = iParticleX - nCoreParticles;
				particle = _envMeanParticles[sotOrd];
				if (particle == null) {
					continue;
				}
				prtclIndxs = ParticleIndexes.getMeanOne(model, iScenario, sotOrd);
			}
			final StateVector latestStateVectorA =
					particle.getLatestStateVector();
			timeUpdateOneParticle(simSecsS, iT, latestStateVectorA);
			if (!_tracker.getKeepGoing()) {
				return;
			}
			final StateVector latestStateVector = particle.getLatestStateVector();
			if (latestStateVector.isStuckOnLand()) {
				/**
				 * Does not override if it had previously landed. Also, returns true
				 * iff it is newly stuck.
				 */
				final long thisSimSecs = latestStateVector.getSimSecs();
				final long birthSimSecs = particle.getBirthSimSecs();
				final long landingSimSecs;
				if (model.getReverseDrift()) {
					landingSimSecs = Math.min(thisSimSecs, birthSimSecs);
				} else {
					landingSimSecs = Math.max(thisSimSecs, birthSimSecs);
				}
				if (particle.setLandingSimSecs(landingSimSecs)) {
					particlesFile.setLandingRefSecs(prtclIndxs,
							model.getRefSecs(landingSimSecs));
				}
			}
			final LatLng3 position = latestStateVector.getLatLng();
			final StateVectorType svt = latestStateVector.getSvt();
			final long simSecs = simSecsS[iT];
			final long refSecs = model.getRefSecs(simSecs);
			particlesFile.setPosition(prtclIndxs, position, refSecs);
			if (iParticle >= 0) {
				particlesFile.setSvtOrdinal(prtclIndxs, svt, refSecs);
			}
			/**
			 * The next warning is just an "FYI." It's not critical, but it is the
			 * only place that causes an incident to be reported in the logfile.
			 */
			if (model.isOutOfArea(position)) {
				final String typeOfParticle = latestStateVector.getDescription();
				model.logOutOfArea(refSecs, particle.getParticleIndexes(),
						typeOfParticle, position);
			}
		}
	}

	/** Advances one particle to simSecs. */
	private void timeUpdateOneParticle(final long[] simSecsS, final int iT,
			final StateVector previousStateVector) {
		final boolean updateParticleTail = true;
		final long simSecs = simSecsS[iT];
		if (previousStateVector.isStuckOnLand()) {
			final StateVector stateVector = new DistressStateVector(
					previousStateVector, simSecs, updateParticleTail);
			stateVector.setIsStuckOnLand();
		} else if (previousStateVector.isAnchored()) {
			final StateVector stateVector = new DistressStateVector(
					previousStateVector, simSecs, updateParticleTail);
			stateVector.setIsAnchored();
		} else {
			/**
			 * The side effect updates the particle. That's all we need here, we
			 * don't need the return value from timeUpdate.
			 */
			previousStateVector.timeUpdate(_tracker, _scenario, simSecsS, iT);
		}
	}

	public void setSotCount(final int id, final int count) {
		_distressTypeToCount.put(id, count);
	}

	public Particle getEnvMeanParticle(final Tracker tracker,
			final int searchObjectTypeId) {
		final Model model = tracker.getModel();
		final int sotOrd = model.getSotOrd(searchObjectTypeId);
		return _envMeanParticles[sotOrd];
	}

	public void setInitialMeanPosition(final Tracker tracker,
			final SearchObjectType originatingSot, final SearchObjectType sot,
			final long refSecs, final LatLng3 meanLatLng) {
		final Model model = tracker.getModel();
		final int iScenario = _scenario.getIScenario();
		final int searchObjectTypeId = sot.getId();
		final int sotOrd = model.getSotOrd(searchObjectTypeId);
		/** For the "mean" one, we use the same "generic" Randomx. */
		final Randomx r = new Randomx(/* useCurrentTimeMs= */false);
		final long simSecs = model.getSimSecs(refSecs);
		final long birthSimSecs = simSecs;
		final long distressSimSecs = simSecs;
		final ParticleIndexes prtclIndxs =
				ParticleIndexes.getMeanOne(model, iScenario, sotOrd);
		final Particle particle = new Particle(tracker, _scenario, prtclIndxs,
				originatingSot, birthSimSecs, sot, distressSimSecs, r);
		@SuppressWarnings("unused")
		final DistressStateVector distressStateVector = new DistressStateVector(
				particle, simSecs, meanLatLng, /* updateParticleTail= */true);
		_envMeanParticles[sotOrd] = particle;
	}

	public static void freeMemory(final ParticleSet[] _particleSets) {
		final int nParticleSets =
				_particleSets == null ? 0 : _particleSets.length;
		for (int k = 0; k < nParticleSets; ++k) {
			_particleSets[k].freeMemory();
		}
	}

	private void freeMemory() {
		final int nParticles = _particles.length;
		for (int k = 0; k < nParticles; ++k) {
			_particles[k].freeMemory();
		}
		final int nEnvParticles =
				_envMeanParticles == null ? 0 : _envMeanParticles.length;
		for (int k = 0; k < nEnvParticles; ++k) {
			final Particle envMeanParticle = _envMeanParticles[k];
			if (envMeanParticle != null) {
				_envMeanParticles[k].freeMemory();
			} else {
				assert k == 0 : "Only EnvMeanParticle that is null should be OC.";
			}
		}
		_distressTypeToCount.clear();
	}
}
