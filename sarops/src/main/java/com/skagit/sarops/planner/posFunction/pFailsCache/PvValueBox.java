package com.skagit.sarops.planner.posFunction.pFailsCache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.skagit.sarops.computePFail.ComputePFail;
import com.skagit.sarops.computePFail.DetectValues;
import com.skagit.sarops.model.Sortie;
import com.skagit.sarops.planner.ParticlesManager;
import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.sarops.tracker.ParticlesFile;

class PvValueBox {
	final private static int _MinNPerSlice = 100;

	final static Comparator<PvValueBox> _PvValueBoxComparator =
			new Comparator<>() {
				@Override
				public int compare(final PvValueBox box0, final PvValueBox box1) {
					if (box0 == box1) {
						return 0;
					}
					final PvValue pvValue0 = box0.getPvValue();
					final PvValue pvValue1 = box1.getPvValue();
					return pvValue0.deepCompareTo(pvValue1);
				}
			};

	final private Planner _planner;
	final private PvValue _pvValue;
	final private HashMap<ParticleIndexes, DetectValues> _particleIndexesToDetectValues;

	PvValueBox(final Planner planner, final PvValue pvValue,
			final boolean forLookUpOnly) {
		_planner = planner;
		_pvValue = pvValue;
		if (forLookUpOnly) {
			_particleIndexesToDetectValues = null;
		} else {
			_particleIndexesToDetectValues = new HashMap<>();
		}
	}

	PvValue getPvValue() {
		return _pvValue;
	}

	Sortie getSortie() {
		if (_pvValue == null) {
			return null;
		}
		return _pvValue.getSortie();
	}

	public String getString() {
		final String pvString;
		if (_pvValue == null) {
			pvString = "NullPv";
		} else {
			pvString = _pvValue.getPv().getString();
		}
		final String pvValueString;
		if (_pvValue == null) {
			pvValueString = "NullPvValue";
		} else {
			pvValueString = _pvValue.getString();
		}
		final String s = String.format("PvValueBox for Pv[%s], PvValue[%s]",
				pvString, pvValueString);
		return s;
	}

	@Override
	public String toString() {
		return getString();
	}

	DetectValues[] getDetectValuesArray(
			final ParticlesManager particlesManager, final boolean forOptnOnly,
			final DetectValues.PFailType pFailType,
			final ParticleIndexes[] prtclIndxsS, final int currentN,
			final int newN) {
		final SimCaseManager.SimCase simCase = _planner.getSimCase();
		if (!simCase.getKeepGoing()) {
			return null;
		}
		final int nOfInterest = newN - currentN;
		final DetectValues[] detectValuesArray = new DetectValues[nOfInterest];

		/** Fill with empty if _pvValue is frozen and we're just optimizing. */
		if (_pvValue.getPv().getPermanentFrozenPvValue() != null &&
				forOptnOnly) {
			Arrays.fill(detectValuesArray, DetectValues.getEmpty());
			return detectValuesArray;
		}

		/** Build a HashSet of things to compute. */
		final HashSet<ParticleIndexes> toComputeHashSet = new HashSet<>();
		for (int k = currentN; k < newN; ++k) {
			final ParticleIndexes prtclIndxs = prtclIndxsS[k];
			DetectValues detectValues =
					_particleIndexesToDetectValues.get(prtclIndxs);
			if (detectValues == null) {
				detectValues = new DetectValues();
				_particleIndexesToDetectValues.put(prtclIndxs, detectValues);
			}
			detectValuesArray[k - currentN] = detectValues;
			final double d = detectValues.getPFail(pFailType);
			if (Double.isNaN(d)) {
				toComputeHashSet.add(prtclIndxs);
			}
		}
		final int nToCompute = toComputeHashSet.size();
		if (nToCompute == 0) {
			return detectValuesArray;
		}
		final ParticleIndexes[] toCompute =
				toComputeHashSet.toArray(new ParticleIndexes[nToCompute]);
		/**
		 * To make it easier to watch the debugging, sort them by overallIndex.
		 * ParticleIndexes has a default comparator which sorts them by
		 * overallIndex.
		 */
		Arrays.sort(toCompute);

		final ParticlesFile particlesFile = particlesManager.getParticlesFile();
		final SimCaseManager simCaseManager = simCase.getSimCaseManager();

		/** Compute them. */
		/** Boilerplate for multithreading... */
		Future<?>[] futures = null;
		final ArrayList<Integer> notTaskedISlices = new ArrayList<>();
		int nSlices = (nToCompute + (_MinNPerSlice - 1)) / _MinNPerSlice;
		int nWorkers = 0;
		if (nSlices > 1) {
			final Object lockOnWorkersThreadPool =
					simCaseManager.getLockOnWorkersThreadPool();
			synchronized (lockOnWorkersThreadPool) {
				final int nFreeWorkers = simCaseManager
						.getNFreeWorkerThreads(simCase, "DetectValuesArray2Getter");
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
								runSliceForDetectVals(simCase, particlesFile, toCompute,
										forOptnOnly, pFailType, finalIWorker, finalNSlices);
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
			runSliceForDetectVals(simCase, particlesFile, toCompute, forOptnOnly,
					pFailType, iSlice, nSlices);
		}
		try {
			for (int iWorker = 0; iWorker < nWorkers; ++iWorker) {
				if (futures[iWorker] != null) {
					futures[iWorker].get();
				}
			}
		} catch (final ExecutionException e) {
		} catch (final InterruptedException e) {
		}
		return detectValuesArray;
	}

	private void runSliceForDetectVals(final SimCaseManager.SimCase simCase,
			final ParticlesFile particlesFile, final ParticleIndexes[] toCompute,
			final boolean forOptnOnly, final DetectValues.PFailType pFailType,
			final int firstIndex, final int nSlices) {
		final int nToCompute = toCompute.length;
		for (int k = firstIndex; k < nToCompute; k += nSlices) {
			final ParticleIndexes prtclIndxs = toCompute[k];
			final DetectValues detectValues =
					_particleIndexesToDetectValues.get(prtclIndxs);
			if (_pvValue == null || _pvValue.onMars()) {
				/** Never update for onMars PvValues. */
				continue;
			}
			/** We always want proportionIn and nft. */
			final double nftPFail =
					detectValues.getPFail(DetectValues.PFailType.NFT);
			if (Double.isNaN(nftPFail)) {
				final double proportionInAndNftPFail[] =
						_pvValue.getProportionInAndNftPFail(prtclIndxs);
				detectValues.setProportionIn(proportionInAndNftPFail[0]);
				detectValues.setPFail(DetectValues.PFailType.NFT,
						proportionInAndNftPFail[1]);
			}
			if (pFailType != DetectValues.PFailType.NFT) {
				final Sortie sortie = getSortie();
				final double thisPFail = ComputePFail.computeFtPFail(simCase,
						particlesFile, prtclIndxs, sortie, forOptnOnly, pFailType,
						/* updateParticlesFile= */false);
				if (0d <= thisPFail && thisPFail <= 1d) {
					detectValues.setPFail(pFailType, thisPFail);
				}
			}
		}
	}
}
