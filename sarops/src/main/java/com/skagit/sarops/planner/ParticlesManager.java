package com.skagit.sarops.planner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.skagit.sarops.computePFail.DetectValues;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.Scenario;
import com.skagit.sarops.model.SearchObjectType;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.planner.posFunction.PosFunction;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.planner.posFunction.pFailsCache.PFailsCache;
import com.skagit.sarops.simCaseManager.MainRunner;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.sarops.tracker.ParticlesFile;
import com.skagit.sarops.util.SimGlobalStrings;
import com.skagit.util.CombinatoricTools;
import com.skagit.util.IntDouble;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.TimeUtilities;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.randomx.Randomx;
import com.skagit.util.randomx.StratifiedSample;

public class ParticlesManager {

	/**
	 * The worst possible variance is when 1/2 of the particles have a pos of
	 * 0 and the other half have a pos of 1. In this case, the true variance
	 * is 0.25.
	 */
	final private static double _WorstPossibleSigSq = 0.25;

	final public static int _NumberOfStages = 3;

	/**
	 * We start by implementing a "friend" mechanism. The access will always
	 * be buried in posFunction's private field so only posFunction can get at
	 * these.
	 */
	public void grantAccessToKeyPrivates(final PosFunction posFunction) {
		posFunction.receiveAccessToKeyPrivates(new AccessToKeyPrivates());
	}

	public class AccessToKeyPrivates {
		/** Need a private ctor for this scheme to work. */
		private AccessToKeyPrivates() {
		}

		public ParticleIndexes[] getSelectedParticles() {
			return _selectedParticles;
		}

		public ParticleIndexes[] getForOptnParticles() {
			return _particlesForOptn;
		}

		public double[] getForOptnPriors() {
			return _priorsForOptn;
		}

		public int[] getSelectedParticlesTypeIds() {
			return _selectedObjectTypeIds;
		}

		public double[] getPriorsGivenSelection() {
			return _priorsGivenSelected;
		}

		public double[] getPriorsGivenLandedAdrift() {
			return _priorsGivenLandedAdrift;
		}

		public ParticleIndexes[] getAllPrtclIndxsS() {
			return _allParticles;
		}

		public ParticleIndexes[] getSampleParticles() {
			return _sampleParticles;
		}

		public double[] getSamplePriors() {
			return _samplePriors;
		}

		/** For non-growing sampled PosFunctions. */
		public ParticlesAndPriors getSampleParticlesAndPrior(
				final ParticleIndexes[] prtclIndxsS0, final double[] priors0,
				final int nInSample, final Randomx r) {
			final ParticleIndexes[] prtclIndxsS1;
			final double[] priors1;
			if (prtclIndxsS0 == null || priors0 == null ||
					prtclIndxsS0.length != priors0.length) {
				prtclIndxsS1 = _particlesForOptn;
				priors1 = _priorsForOptn;
			} else {
				prtclIndxsS1 = prtclIndxsS0;
				priors1 = priors0;
			}
			final int nPrtcls1 = prtclIndxsS1.length;
			if (r == null || nInSample < 0 || nInSample >= nPrtcls1) {
				return new ParticlesAndPriors(prtclIndxsS1, priors1);
			}
			final double[] cums = NumericalRoutines.buildCumsArray(priors1);
			final ParticleIndexes[] prtclIndxsS = new ParticleIndexes[nInSample];
			for (int k = 0; k < nInSample; ++k) {
				final int idx = r.getDrawFromTable(cums);
				prtclIndxsS[k] = prtclIndxsS1[idx];
			}
			final double[] priors = new double[nInSample];
			final double prior = 1d / nInSample;
			for (int k = 0; k < nInSample; ++k) {
				priors[k] = prior;
			}
			return new ParticlesAndPriors(prtclIndxsS, priors);
		}

		public ParticleIndexes[] getLandedAdriftParticles() {
			return _landedAdriftParticles;
		}

		public double[] getAllPriors() {
			return _allPriors;
		}

		public double[] getSelectedInitialPriors() {
			return _initPriorsSelected;
		}

		public double[] getLandedAdriftInitialPriors() {
			return _initPriorsOfLandedAdrift;
		}

		public double[] getOldPFailsOfSelected() {
			return _oldPFailsSelecteds;
		}

		public double[] getOldPFailsOfLandedAdrift() {
			return _oldPFailsOfLandedAdrift;
		}

		public double[] getAllInitialPriors() {
			return _allInitPriors;
		}

		public double[] getAllOldPFails() {
			return _allOldPFails;
		}
	}

	/** The L/A and selected specifications of the problem: */
	public static class Specs {
		final public boolean _includeLanded;
		final public boolean _includeAdrift;
		final private int[] _viz1ObjectTypeIds;
		final private int[] _viz2ObjectTypeIds;
		final public long _firstRefSecsOfInterest;
		final public long _lastRefSecsOfInterest;

		public Specs(final boolean includeLandedParticles,
				final boolean includeAdriftParticles,
				final long firstRefSecsOfInterest, final long lastRefSecsOfInterest,
				final HashSet<Integer> viz1ObjectTypeIds,
				final HashSet<Integer> viz2ObjectTypeIds) {
			_includeLanded = includeLandedParticles;
			_includeAdrift = includeAdriftParticles;
			_firstRefSecsOfInterest = firstRefSecsOfInterest;
			_lastRefSecsOfInterest = lastRefSecsOfInterest;
			_viz1ObjectTypeIds = makeToArray(viz1ObjectTypeIds);
			_viz2ObjectTypeIds = makeToArray(viz2ObjectTypeIds);
		}

		private static int[] makeToArray(final Set<Integer> idSet) {
			if (idSet == null) {
				return null;
			}
			final int n = idSet.size();
			final int[] idArray = new int[n];
			final Iterator<Integer> it = idSet.iterator();
			for (int k = 0; k < n; ++k) {
				idArray[k] = it.next();
			}
			Arrays.sort(idArray);
			return idArray;
		}

		@SuppressWarnings("unused")
		private boolean viz1(final int objectTypeId) {
			return contains(objectTypeId, _viz1ObjectTypeIds);
		}

		private boolean viz2(final int objectTypeId) {
			return contains(objectTypeId, _viz2ObjectTypeIds);
		}

		private static boolean contains(final int objectTypeId,
				final int[] array) {
			final int n = array == null ? 0 : array.length;
			for (int k = 0; k < n; ++k) {
				if (array[k] == objectTypeId) {
					return true;
				}
			}
			return false;
		}

		private boolean isRealPlannerProblem() {
			return _viz2ObjectTypeIds != null && _viz2ObjectTypeIds.length > 0;
		}
	}

	/** The data of the ParticlesManager: */
	final private Specs _specs;
	final private ParticlesFile _particlesFile;

	/** Particle sets except for frozens. */
	final private ParticleIndexes[] _allParticles;
	final private ParticleIndexes[] _selectedParticles;
	final private ParticleIndexes[] _landedAdriftParticles;
	final private ParticleIndexes[] _discardedParticles;
	/**
	 * More double arrays. "Priors" means relative probability AFTER
	 * simModel's completed searches. "InitialPriors" means prior BEFORE
	 * simModel's completed searches. "OldPFails" means the pFails of
	 * simModel's completed searches. All priors are normalized.
	 */
	final private double[] _allPriors;
	final private double[] _allInitPriors;
	final private double[] _allOldPFails;
	/**
	 * Selected; those for which some Pv (frozen or not) is looking for, and
	 * pass the L/A test.
	 */
	final private double[] _priorsGivenSelected;
	final private double[] _initPriorsSelected;
	final private double[] _oldPFailsSelecteds;
	/** Landed/Adrift; those that pass the L/A test. */
	final private double[] _priorsGivenLandedAdrift;
	final private double[] _initPriorsOfLandedAdrift;
	final private double[] _oldPFailsOfLandedAdrift;
	/** Object types of interest. */
	final private int[] _allObjectTypeIds;
	final private int[] _selectedObjectTypeIds;
	final private Map<Integer, double[]> _objectTypeToTotalWeightsByStage;
	/**
	 * For Optn and sampling; _particlesForOptn includes only those that can
	 * be seen by some Pv that is not permanently frozen, and pass the L/A
	 * test. We also take into account the frozens when computing
	 * _priorsForOptn, and thereafter, any PosFunction that is created for
	 * optimization, ignores the permanently frozen PttrnVbls.
	 */
	private ParticleIndexes[] _particlesForOptn;
	private double[] _priorsForOptn;
	private double[] _cumPriorsForOptn;
	private ParticleIndexes[] _sampleParticles;
	private double[] _samplePriors;
	private boolean _usingStratified;

	/** For the environmental means. */
	final private ParticleIndexes[] _envPrtclIndxsS;

	public static ParticlesManager createParticlesManager(
			final SimCaseManager.SimCase simCase,
			final ParticlesFile particlesFile, final Specs specs) {
		try {
			final ParticlesManager particlesManager =
					new ParticlesManager(simCase, particlesFile, specs);
			final ParticleIndexes[] selectedPrtclIndxsS =
					particlesManager._selectedParticles;
			final int nSelected =
					selectedPrtclIndxsS == null ? 0 : selectedPrtclIndxsS.length;
			if (nSelected == 0) {
				throw new RuntimeException("No selected particles");
			}
			return particlesManager;
		} catch (final Exception e) {
			MainRunner.HandleFatal(simCase, new RuntimeException(e));
		}
		return null;
	}

	public ParticleIndexes.ParticleIndexesState computePrtclIndxsState(
			final ParticleIndexes prtclIndxs, final long refSecs) {
		final LatLng3 latLng = _particlesFile.getLatLng(refSecs, prtclIndxs);
		if (latLng == null) {
			return null;
		}
		final int objectType =
				_particlesFile.getObjectTypeId(refSecs, prtclIndxs);
		final long landingRefSecs =
				_particlesFile.getLandingRefSecs(prtclIndxs);
		final boolean isLanded =
				landingRefSecs >= 0 && landingRefSecs <= refSecs;
		return prtclIndxs.new ParticleIndexesState(latLng, refSecs, objectType,
				isLanded);
	}

	private boolean isSelected(final ParticleIndexes prtclIndxs) {
		final long firstSearchRefSecs = _specs._firstRefSecsOfInterest;
		final long lastSearchRefSecs = _specs._lastRefSecsOfInterest;
		final int objectTypeId1 =
				_particlesFile.getObjectTypeId(firstSearchRefSecs, prtclIndxs);
		if (!_specs.viz2(objectTypeId1)) {
			final int objectTypeId2 =
					_particlesFile.getObjectTypeId(lastSearchRefSecs, prtclIndxs);
			if (!_specs.viz2(objectTypeId2)) {
				return false;
			}
		}
		/** Now check for landed/adrift. */
		final boolean passesLandedAdrift = passesLandedAdriftTest(prtclIndxs);
		return passesLandedAdrift;
	}

	/**
	 * With our ParticlesFile, a particle passes the landed/Adrift test if it
	 * passes it as of the first search time.
	 */
	private boolean passesLandedAdriftTest(final ParticleIndexes prtclIndxs) {
		final long landingRefSecs =
				_particlesFile.getLandingRefSecs(prtclIndxs);
		final boolean isAdrift = landingRefSecs < 0 ||
				landingRefSecs > _specs._firstRefSecsOfInterest;
		if ((_specs._includeLanded && !isAdrift) || (_specs._includeAdrift && isAdrift)) {
			return true;
		}
		return false;
	}

	private static class SetOfStrata {
		final private TreeSet<Stratum> _strata;

		private SetOfStrata() {
			_strata = new TreeSet<>(new Comparator<Stratum>() {
				@Override
				public int compare(final Stratum o1, final Stratum o2) {
					if (o1._iScenario != o2._iScenario) {
						return o1._iScenario < o2._iScenario ? -1 : 1;
					}
					return o1._distressType < o2._distressType ? -1 :
							(o1._distressType > o2._distressType ? 1 : 0);
				}
			});
		}

		private void add(final ParticleIndexes prtclIndxs, final int iScenario,
				final int distressType, final double weight) {
			Stratum stratum = new Stratum(iScenario, distressType);
			if (!_strata.add(stratum)) {
				stratum = _strata.floor(stratum);
			}
			stratum.add(prtclIndxs, weight);
		}

		private Stratum[] getStrata() {
			return _strata.toArray(new Stratum[_strata.size()]);
		}
	}

	private static class Stratum {
		final private int _iScenario;
		final private int _distressType;
		final private ArrayList<StratumMember> _members;

		private Stratum(final int iScenario, final int distressType) {
			_iScenario = iScenario;
			_distressType = distressType;
			_members = new ArrayList<>();
		}

		private void add(final ParticleIndexes prtclIndxs,
				final double weight) {
			final StratumMember stratumMember =
					new StratumMember(prtclIndxs, weight);
			_members.add(stratumMember);
		}
	}

	private static class StratumMember {
		final private ParticleIndexes _prtclIndxs;
		final private double _weight;

		private StratumMember(final ParticleIndexes prtclIndxs,
				final double weight) {
			_prtclIndxs = prtclIndxs;
			_weight = weight;
		}
	}

	private ParticlesManager(final SimCaseManager.SimCase simCase,
			final ParticlesFile particlesFile, final Specs specs)
			throws Exception {
		_specs = specs;
		_usingStratified = false;
		_particlesForOptn = _sampleParticles = null;
		_priorsForOptn = _cumPriorsForOptn = _samplePriors = null;

		final Model model = particlesFile.getModel();
		_particlesFile = particlesFile;
		final long[] refSecsS = _particlesFile.getRefSecsS();
		final int nRefSecsS = refSecsS.length;
		/** Fill in stageMarkers. */
		TimeMarker[] stageMarkers;
		stageMarkers = new TimeMarker[_NumberOfStages];
		long firstRefSecsOfInterest = _specs._firstRefSecsOfInterest;
		long lastRefSecsOfInterest = _specs._lastRefSecsOfInterest;
		if (firstRefSecsOfInterest < 0 || lastRefSecsOfInterest < 0 ||
				firstRefSecsOfInterest >= lastRefSecsOfInterest) {
			firstRefSecsOfInterest = refSecsS[0];
			lastRefSecsOfInterest = refSecsS[nRefSecsS - 1];
		}
		final long[] stageRefSecsS = CombinatoricTools.getFenceposts(
				firstRefSecsOfInterest, lastRefSecsOfInterest, _NumberOfStages);
		for (int iStage = 0; iStage < _NumberOfStages; ++iStage) {
			stageMarkers[iStage] =
					findClosestRefSecs(refSecsS, stageRefSecsS[iStage]);
		}
		if (stageMarkers[0] == null ||
				stageMarkers[_NumberOfStages - 1] == null) {
			final String time1String =
					TimeUtilities.formatTime(firstRefSecsOfInterest, true);
			final String time2String =
					TimeUtilities.formatTime(lastRefSecsOfInterest, true);
			SimCaseManager.err(simCase, "Insufficient simulation data at " +
					time1String + " or " + time2String);
		}

		/** The time for "priors" is the last time of the simulation. */
		final long refSecsForPrior = refSecsS[nRefSecsS - 1];
		final int nParticlesPerScenario = model.getNParticlesPerScenario();
		final int nScenarii = model.getNScenarii();

		/**
		 * Compute nAll, nSelected and nLandedAdrift; these are automatically
		 * everything for a non-planner problem.
		 */
		final boolean isRealPlannerProblem = _specs.isRealPlannerProblem();
		int nAll = 0;
		int nSelected = 0;
		int nLandedAdrift = 0;
		final HashSet<Integer> selectedObjectTypeIdSet = new HashSet<>();
		for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
			for (int iParticle = 0; iParticle < nParticlesPerScenario;
					++iParticle) {
				final ParticleIndexes prtclIndxs =
						ParticleIndexes.getStandardOne(model, iScenario, iParticle);
				final boolean isSelected =
						isRealPlannerProblem ? isSelected(prtclIndxs) : true;
				if (isSelected) {
					final int objectTypeId1 = _particlesFile
							.getObjectTypeId(_specs._firstRefSecsOfInterest, prtclIndxs);
					if (_specs.viz2(objectTypeId1)) {
						selectedObjectTypeIdSet.add(objectTypeId1);
					}
					final int objectTypeId2 = _particlesFile
							.getObjectTypeId(_specs._lastRefSecsOfInterest, prtclIndxs);
					if (_specs.viz2(objectTypeId2)) {
						selectedObjectTypeIdSet.add(objectTypeId2);
					}
					++nSelected;
				}
				final boolean passesLandedAdrift = isRealPlannerProblem ?
						passesLandedAdriftTest(prtclIndxs) : true;
				if (passesLandedAdrift) {
					++nLandedAdrift;
				}
				++nAll;
			}
		}

		/** Dispose of bad problem. */
		if (isRealPlannerProblem && nSelected == 0) {
			SimCaseManager.err(simCase,
					"No particles selected for optimization problem.  Quitting.");
			_selectedObjectTypeIds = null;
			/** Sets and priors. */
			_allParticles = _selectedParticles = null;
			_discardedParticles = _landedAdriftParticles = null;
			/** All. */
			_allPriors = null;
			_allInitPriors = null;
			_allOldPFails = null;
			/** Selected. */
			_priorsGivenSelected = null;
			_initPriorsSelected = null;
			_oldPFailsSelecteds = null;
			/** _landedAdrift. */
			_priorsGivenLandedAdrift =
					_initPriorsOfLandedAdrift = _oldPFailsOfLandedAdrift = null;
			/** ObjectTypes. */
			_objectTypeToTotalWeightsByStage = null;
			_allObjectTypeIds = null;
			/** Env Means. */
			_envPrtclIndxsS = null;
			return;
		}

		/** Set up the "all" arrays. */
		_allParticles = new ParticleIndexes[nAll];
		_allPriors = new double[nAll];
		_allInitPriors = new double[nAll];
		_allOldPFails = new double[nAll];
		/** These are all "real particles," not "mean particles." */
		for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
			for (int iParticle1 = 0; iParticle1 < nParticlesPerScenario;
					++iParticle1) {
				final ParticleIndexes prtclIndxs =
						ParticleIndexes.getStandardOne(model, iScenario, iParticle1);
				final int overallIndex = prtclIndxs.getOverallIndex();
				_allParticles[overallIndex] = prtclIndxs;
				/**
				 * TMK!! Note 1: _allPriors should be
				 * getProbabilities[refSecsForPrior] and not multiplying in
				 * cumPFail.
				 */
				final double initPrior =
						_particlesFile.getProbability(refSecsS[0], prtclIndxs);
				final double cumPFail =
						_particlesFile.getCumPFail(refSecsForPrior, prtclIndxs);
				_allInitPriors[overallIndex] = initPrior;
				_allOldPFails[overallIndex] = cumPFail;
				_allPriors[overallIndex] = initPrior * cumPFail;
			}
		}

		/** Fill in _allObjectTypes. */
		final TreeSet<Integer> allObjectTypes = new TreeSet<>();
		for (final ParticleIndexes prtclIndxs : _allParticles) {
			final int thisObjectType =
					_particlesFile.getObjectTypeId(refSecsS[0], prtclIndxs);
			allObjectTypes.add(thisObjectType);
			allObjectTypes.add(_particlesFile
					.getObjectTypeId(refSecsS[nRefSecsS - 1], prtclIndxs));
		}
		_allObjectTypeIds = new int[allObjectTypes.size()];
		int kObjectType = 0;
		for (final int objectType : allObjectTypes) {
			_allObjectTypeIds[kObjectType++] = objectType;
		}

		/** Set up "discarded" and "selected" arrays. */
		if (isRealPlannerProblem) {
			final int nDiscarded1 = nAll - nSelected;
			_discardedParticles = new ParticleIndexes[nDiscarded1];
			/**
			 * Normalizing _allInitPriors is unnecessary; _allPriors, since it has
			 * the pFails from Sim's completed searches multiplied in, is
			 * necessary.
			 */
			NumericalRoutines.normalizeWeights(_allInitPriors);
			NumericalRoutines.normalizeWeights(_allPriors);
			if (nDiscarded1 == 0) {
				/** selected and landedAdrift are the same as "all." */
				_selectedParticles = _allParticles;
				_priorsGivenSelected = _allPriors;
				_oldPFailsSelecteds = _allOldPFails;
				_initPriorsSelected = _allInitPriors;
				_selectedObjectTypeIds = _allObjectTypeIds;
			} else {
				/** Must compute selected and discarded. */
				_selectedParticles = new ParticleIndexes[nSelected];
				_oldPFailsSelecteds = new double[nSelected];
				_initPriorsSelected = new double[nSelected];
				_priorsGivenSelected = new double[nSelected];
				int kDiscarded = 0;
				int kSelected = 0;
				for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
					for (int iParticle = 0; iParticle < nParticlesPerScenario;
							++iParticle) {
						final ParticleIndexes prtclIndxs =
								ParticleIndexes.getStandardOne(model, iScenario, iParticle);
						/** TMK!! See Note 1. */
						final double origPrior =
								_particlesFile.getProbability(refSecsS[0], prtclIndxs);
						final double cumPFail =
								_particlesFile.getCumPFail(refSecsForPrior, prtclIndxs);
						final double prior = origPrior * cumPFail;
						if (isSelected(prtclIndxs)) {
							_selectedParticles[kSelected] = prtclIndxs;
							_priorsGivenSelected[kSelected] = prior;
							_initPriorsSelected[kSelected] = origPrior;
							_oldPFailsSelecteds[kSelected] = cumPFail;
							++kSelected;
						} else {
							_discardedParticles[kDiscarded] = prtclIndxs;
							++kDiscarded;
						}
					}
				}
				NumericalRoutines.normalizeWeights(_initPriorsSelected);
				NumericalRoutines.normalizeWeights(_priorsGivenSelected);
				final int nSelectedTypes = selectedObjectTypeIdSet.size();
				_selectedObjectTypeIds = new int[nSelectedTypes];
				kObjectType = 0;
				for (final int objectTypeId : selectedObjectTypeIdSet) {
					_selectedObjectTypeIds[kObjectType++] = objectTypeId;
				}
			}
		} else {
			/** Not a real Planner problem. */
			_discardedParticles = null;
			_selectedParticles = _allParticles;
			_priorsGivenSelected = _allPriors;
			_oldPFailsSelecteds = _allOldPFails;
			_initPriorsSelected = _allInitPriors;
			_selectedObjectTypeIds = _allObjectTypeIds;
		}
		nSelected = _selectedParticles.length;

		/** Set up Landed/Adrift arrays. */
		final int nDiscarded2 = nAll - nLandedAdrift;
		if (nDiscarded2 == 0) {
			/** landedAdrift are the same as "all." */
			_landedAdriftParticles = _allParticles;
			_priorsGivenLandedAdrift = _allPriors;
			_oldPFailsOfLandedAdrift = _allOldPFails;
			_initPriorsOfLandedAdrift = _allInitPriors;
		} else {
			/** Must compute landedAdrift. */
			_landedAdriftParticles = new ParticleIndexes[nLandedAdrift];
			_oldPFailsOfLandedAdrift = new double[nLandedAdrift];
			_initPriorsOfLandedAdrift = new double[nLandedAdrift];
			_priorsGivenLandedAdrift = new double[nLandedAdrift];
			int kLandedAdrift = 0;
			for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
				for (int iParticle = 0; iParticle < nParticlesPerScenario;
						++iParticle) {
					final ParticleIndexes prtclIndxs =
							ParticleIndexes.getStandardOne(model, iScenario, iParticle);
					/** TMK!! See Note 1. */
					final double origPrior =
							_particlesFile.getProbability(refSecsS[0], prtclIndxs);
					final double cumPFail =
							_particlesFile.getCumPFail(refSecsForPrior, prtclIndxs);
					final double prior = origPrior * cumPFail;
					if (passesLandedAdriftTest(prtclIndxs)) {
						_landedAdriftParticles[kLandedAdrift] = prtclIndxs;
						_priorsGivenLandedAdrift[kLandedAdrift] = prior;
						_initPriorsOfLandedAdrift[kLandedAdrift] = origPrior;
						_oldPFailsOfLandedAdrift[kLandedAdrift] = cumPFail;
						++kLandedAdrift;
					}
				}
			}
			NumericalRoutines.normalizeWeights(_initPriorsOfLandedAdrift);
			NumericalRoutines.normalizeWeights(_priorsGivenLandedAdrift);
		}
		/**
		 * Fill in _objectTypeToTotalWeightByStage, as per priorsGivenFrozens.
		 */
		_objectTypeToTotalWeightsByStage = new TreeMap<>();
		long firstFencePost = firstRefSecsOfInterest;
		long lastFencePost = lastRefSecsOfInterest;
		if (firstFencePost == -1 || lastFencePost == -1) {
			firstFencePost = refSecsS[0];
			lastFencePost = refSecsS[refSecsS.length - 1];
		}
		final long[] fencePostsInRefSecsS = CombinatoricTools
				.getFenceposts(firstFencePost, lastFencePost, _NumberOfStages);
		for (int indexForSelected = 0; indexForSelected < nSelected;
				++indexForSelected) {
			final ParticleIndexes prtclIndxs =
					_selectedParticles[indexForSelected];
			final double prior = _priorsGivenSelected[indexForSelected];
			for (int iStage = 0; iStage < _NumberOfStages; ++iStage) {
				final long refSecs = fencePostsInRefSecsS[iStage];
				final ParticleIndexes.ParticleIndexesState prtclIndxsState =
						prtclIndxs.refSecsToPrtclIndxsState(this, refSecs);
				final int objectType = prtclIndxsState.getObjectType();
				double[] weights = _objectTypeToTotalWeightsByStage.get(objectType);
				if (weights == null) {
					weights = new double[_NumberOfStages];
					Arrays.fill(weights, 0d);
					_objectTypeToTotalWeightsByStage.put(objectType, weights);
				}
				weights[iStage] += prior;
			}
		}

		/** Set up _envPrtclIndxsS. */
		final Collection<SearchObjectType> searchObjectTypes =
				model.getSearchObjectTypes();
		final int nSearchObjectTypes = searchObjectTypes.size();
		final int nTotal = nScenarii * nSearchObjectTypes;
		_envPrtclIndxsS = new ParticleIndexes[nTotal];
		int k = 0;
		for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
			final Scenario scenario = model.getScenario(iScenario);
			for (int sotOrd = 0; sotOrd < nSearchObjectTypes; ++sotOrd) {
				final ParticleIndexes prtclIndxs =
						ParticleIndexes.getMeanOne(model, iScenario, sotOrd);
				final SearchObjectType searchObjectType =
						model.getSearchObjectTypeFromOrd(sotOrd);
				final int searchObjectTypeId = searchObjectType.getId();
				if ((searchObjectTypeId < 0) || (scenario.getInitialPriorWithinScenario(searchObjectType) == 0)) {
					_envPrtclIndxsS[k++] = null;
					continue;
				}
				_envPrtclIndxsS[k++] = prtclIndxs;
			}
		}
	}

	public void setOptnPrtclsAndPriors(final Planner planner) {
		final PlannerModel plannerModel = planner.getPlannerModel();
		/**
		 * Compute the selected particles that are looked at by some Pv that is
		 * not permanently frozen. Multiply in the frozen pFails.
		 */
		final PatternVariable[] floaters = plannerModel.getFloaters();
		final int nFloaters = floaters == null ? 0 : floaters.length;

		/** For Eval problems, there is no optimization. */
		if (nFloaters == 0) {
			_particlesForOptn = new ParticleIndexes[0];
			_priorsForOptn = new double[0];
			_cumPriorsForOptn = new double[0];
			_usingStratified = false;
			_sampleParticles = new ParticleIndexes[0];
			_samplePriors = new double[0];
			return;
		}

		final TreeSet<Integer> viz2ObjectTypes = new TreeSet<>();
		for (int kFloater = 0; kFloater < nFloaters; ++kFloater) {
			final PatternVariable pv = floaters[kFloater];
			final Set<Integer> theseViz2ObjectTypes =
					pv.getViz2LrcSets().keySet();
			viz2ObjectTypes.addAll(theseViz2ObjectTypes);
		}
		final long[] refSecsS = _particlesFile.getRefSecsS();
		final int nRefSecsS = refSecsS.length;
		final long refSecsForPrior = refSecsS[nRefSecsS - 1];
		final int nSelected = _selectedParticles.length;
		final ArrayList<Integer> kForOptns = new ArrayList<>(nSelected);
		for (int kSelected = 0; kSelected < nSelected; ++kSelected) {
			final ParticleIndexes prtclIndxs = _selectedParticles[kSelected];
			final int objectType =
					_particlesFile.getObjectTypeId(refSecsForPrior, prtclIndxs);
			if (viz2ObjectTypes.contains(objectType)) {
				kForOptns.add(kSelected);
			}
		}
		final int nForOptn = kForOptns.size();

		final PvValue[] permanentlyFrozenPvValues =
				plannerModel.getPermanentlyFrozenPvValues();
		final int nPermanentlyFrozenPvValues =
				permanentlyFrozenPvValues == null ? 0 :
						permanentlyFrozenPvValues.length;

		/** Update _priorsForOptn with frozens' pFails. */
		if (nForOptn == nSelected && nPermanentlyFrozenPvValues == 0) {
			_particlesForOptn = _selectedParticles;
			_priorsForOptn = _priorsGivenSelected;
		} else {
			_particlesForOptn = new ParticleIndexes[nForOptn];
			_priorsForOptn = new double[nForOptn];
			for (int kForOptn = 0; kForOptn < nForOptn; ++kForOptn) {
				final int kSelected = kForOptns.get(kForOptn);
				_priorsForOptn[kForOptn] = _priorsGivenSelected[kSelected];
				_particlesForOptn[kForOptn] = _selectedParticles[kSelected];
			}
			final PFailsCache pFailsCache = planner.getPFailsCache();
			final boolean forOptnOnly = false;
			final DetectValues.PFailType pFailType = DetectValues.PFailType.AIFT;
			for (int kFrozen = 0; kFrozen < nPermanentlyFrozenPvValues;
					++kFrozen) {
				final PvValue pvValue = permanentlyFrozenPvValues[kFrozen];
				final DetectValues[] detectValuesArray =
						pFailsCache.getDetectValuesArray(planner, forOptnOnly,
								pFailType, _particlesForOptn, 0, nForOptn, pvValue);
				for (int kForOptn = 0; kForOptn < nForOptn; ++kForOptn) {
					_priorsForOptn[kForOptn] *=
							detectValuesArray[kForOptn]._aiftPFail;
				}
			}
		}

		final double ttlWt0 =
				NumericalRoutines.normalizeWeights(_priorsForOptn);
		if (ttlWt0 == 0d) {
			_particlesForOptn = new ParticleIndexes[0];
			_priorsForOptn = new double[0];
			_usingStratified = false;
			_sampleParticles = new ParticleIndexes[0];
			_samplePriors = new double[0];
			return;
		}
		_cumPriorsForOptn = NumericalRoutines.buildCumsArray(_priorsForOptn);

		/** Compute objTpToWt. */
		final HashMap<IntDouble, IntDouble> objTpToWt = new HashMap<>();
		for (int kForOptn = 0; kForOptn < nForOptn; ++kForOptn) {
			final ParticleIndexes prtclIndxs = _particlesForOptn[kForOptn];
			final int objTp =
					_particlesFile.getObjectTypeId(refSecsForPrior, prtclIndxs);
			final double wt = _priorsForOptn[kForOptn];
			final IntDouble intDouble = new IntDouble(objTp, wt);
			final IntDouble incumbent = objTpToWt.put(intDouble, intDouble);
			if (incumbent != null) {
				final IntDouble replacement =
						new IntDouble(objTp, incumbent._d + wt);
				objTpToWt.put(replacement, replacement);
			}
		}
		final int nObjTps = objTpToWt.size();
		final IntDouble[] objTpWts = new IntDouble[nObjTps];
		final Iterator<IntDouble> it = objTpToWt.keySet().iterator();
		double ttlWt1 = 0d;
		for (int iPass = 0; iPass < 2; ++iPass) {
			for (int k = 0; k < nObjTps; ++k) {
				final IntDouble objTpWt = iPass == 0 ? it.next() : objTpWts[k];
				final double wt = objTpWt._d;
				if (iPass == 0) {
					ttlWt1 += wt;
					objTpWts[k] = objTpWt;
				} else {
					final int objTp = objTpWt._i;
					objTpWts[k] = new IntDouble(objTp, wt / ttlWt1);
				}
			}
		}

		/**
		 * Construct the sample, stratified or not. Note that, for consistency's
		 * sake across runs, we always use the same seed. Hence, our
		 * optimization PosFunctions, which depend on sampleParticles, will be
		 * consistent from one run to the next.
		 */
		final Randomx r = new Randomx(/* useCurrentTimeMs= */false);
		/**
		 * If and only if the following 2 arrays do not end up null, we are
		 * using stratified.
		 */
		ParticleIndexes[] sampleParticles = null;
		double[] samplePriors = null;
		final SetOfStrata setOfStrata = new SetOfStrata();
		for (int kForOptn = 0; kForOptn < nForOptn; ++kForOptn) {
			final ParticleIndexes prtclIndxs = _particlesForOptn[kForOptn];
			final int iScenario = prtclIndxs.getScenarioIndex();
			final int distressType = _particlesFile.getDistressType(prtclIndxs);
			final double wt = _priorsForOptn[kForOptn];
			setOfStrata.add(prtclIndxs, iScenario, distressType, wt);
		}
		final Stratum[] strata = setOfStrata.getStrata();
		final int nStrata = strata.length;
		/**
		 * Abandon stratified sampling if we don't have enough strata to make it
		 * worthwhile.
		 */
		final SimGlobalStrings simGlobalStrings =
				planner.getSimCase().getSimGlobalStrings();
		final int minNStrataForStratified =
				simGlobalStrings.getMinNStrataForStratified();
		if (nStrata >= minNStrataForStratified) {
			final double[][] inWts = new double[nStrata][];
			for (int k1 = 0; k1 < nStrata; ++k1) {
				final Stratum stratum = strata[k1];
				final ArrayList<StratumMember> members = stratum._members;
				final int nMembers = members.size();
				inWts[k1] = new double[nMembers];
				for (int k2 = 0; k2 < nMembers; ++k2) {
					final StratumMember member = members.get(k2);
					inWts[k1][k2] = member._weight;
				}
			}
			final double factor = 2d * (1d - Math.pow(0.5, nStrata));
			final double nForFirstStratum =
					simGlobalStrings.getNForFirstStratum();
			final int targetN = (int) Math.ceil(factor * nForFirstStratum);
			final StratifiedSample stratifiedSample =
					new StratifiedSample(inWts, targetN, r);
			final int nInSample = stratifiedSample.getTotalN();
			sampleParticles = new ParticleIndexes[nInSample];
			samplePriors = new double[nInSample];
			for (int k = 0; k < nInSample; ++k) {
				final double[] trio =
						stratifiedSample.getStratumEntryWeightOfChosen(k);
				final int kStratum = (int) Math.round(trio[0]);
				final int kEntry = (int) Math.round(trio[1]);
				final Stratum stratum = strata[kStratum];
				final ArrayList<StratumMember> members = stratum._members;
				final StratumMember member = members.get(kEntry);
				final ParticleIndexes prtclIndxs = member._prtclIndxs;
				sampleParticles[k] = prtclIndxs;
				samplePriors[k] = trio[2];
			}
		}
		if (sampleParticles != null) {
			_usingStratified = true;
			_sampleParticles = sampleParticles;
			_samplePriors = samplePriors;
		} else {
			/** Not using Stratified. */
			_usingStratified = false;
			final double maxSigma = simGlobalStrings.getMaxSigma();
			final int maxSampleSize =
					(int) Math.ceil(_WorstPossibleSigSq / (maxSigma * maxSigma));
			_sampleParticles = new ParticleIndexes[maxSampleSize];
			_samplePriors = new double[maxSampleSize];
			for (int k = 0; k < maxSampleSize; ++k) {
				final int globalIndex = r.getDrawFromTable(_cumPriorsForOptn);
				_sampleParticles[k] = _particlesForOptn[globalIndex];
				_samplePriors[k] = 1d;
			}
		}
	}

	public static class TimeMarker {
		final int _lowerIndex;
		final long _lowerRefSecs;
		final long _refSecs;
		final long _upperRefSecs;

		TimeMarker(final int lowerIndex, final long lowerRefSecs,
				final long refSecs, final long upperRefSecs) {
			_lowerIndex = lowerIndex;
			_upperRefSecs = upperRefSecs;
			_refSecs = refSecs;
			_lowerRefSecs = lowerRefSecs;
		}
	}

	public static TimeMarker findClosestRefSecs(long[] refSecsS,
			final long refSecs) {
		final int nRefSecsS = refSecsS == null ? 0 : refSecsS.length;
		if (nRefSecsS > 1 && refSecsS[0] > refSecsS[nRefSecsS - 1]) {
			/** It's in descending order. */
			refSecsS = refSecsS.clone();
			for (int k = 0; k < nRefSecsS / 2; ++k) {
				final long ll = refSecsS[k];
				refSecsS[k] = refSecsS[nRefSecsS - 1 - k];
				refSecsS[nRefSecsS - 1 - k] = ll;
			}
			final int glbIndex = CombinatoricTools.getGlbIndex(refSecsS, refSecs);
			if (glbIndex == -1) {
				return null;
			}
			final long lowerRefSecs = refSecsS[glbIndex];
			final long upperRefSecs;
			if (lowerRefSecs == refSecs) {
				upperRefSecs = refSecs;
			} else {
				if (glbIndex == refSecsS.length - 1) {
					return null;
				}
				upperRefSecs = refSecsS[glbIndex + 1];
			}
			return new TimeMarker(nRefSecsS - 1 - glbIndex, upperRefSecs, refSecs,
					lowerRefSecs);
		}
		final int glbIndex = CombinatoricTools.getGlbIndex(refSecsS, refSecs);
		if (glbIndex == -1) {
			return null;
		}
		final long lowerRefSecs = refSecsS[glbIndex];
		final long upperRefSecs;
		if (lowerRefSecs == refSecs) {
			upperRefSecs = refSecs;
		} else {
			if (glbIndex == refSecsS.length - 1) {
				return null;
			}
			upperRefSecs = refSecsS[glbIndex + 1];
		}
		return new TimeMarker(glbIndex, lowerRefSecs, refSecs, upperRefSecs);
	}

	public LatLng3 getPosition(final long refSecs,
			final ParticleIndexes prtclIndxs) {
		final LatLng3 latLng = _particlesFile.getLatLng(refSecs, prtclIndxs);
		return latLng;
	}

	public int getObjectType(final long refSecs,
			final ParticleIndexes prtclIndxs) {
		return _particlesFile.getObjectTypeId(refSecs, prtclIndxs);
	}

	public boolean getInDistress(final long refSecs,
			final ParticleIndexes prtclIndxs) {
		return _particlesFile.getDistressRefSecs(prtclIndxs) <= refSecs;
	}

	public boolean getInDistressAndLanded(final long refSecs,
			final ParticleIndexes prtclIndxs) {
		final boolean inDistress =
				_particlesFile.getDistressRefSecs(prtclIndxs) <= refSecs;
		if (!inDistress) {
			return false;
		}
		final long landingTime = _particlesFile.getLandingRefSecs(prtclIndxs);
		return landingTime >= 0 && landingTime <= refSecs;
	}

	public int[] getSelectedObjectTypeIds() {
		final int nObjectTypes = _objectTypeToTotalWeightsByStage == null ? 0 :
				_objectTypeToTotalWeightsByStage.size();
		if (nObjectTypes == 0) {
			return null;
		}
		final Integer[] intArray = _objectTypeToTotalWeightsByStage.keySet()
				.toArray(new Integer[nObjectTypes]);
		final int[] returnValue = new int[nObjectTypes];
		for (int i = 0; i < nObjectTypes; ++i) {
			returnValue[i] = intArray[i];
		}
		return returnValue;
	}

	public int[] getAllObjectTypeIds() {
		return _allObjectTypeIds.clone();
	}

	public ParticlesFile getParticlesFile() {
		return _particlesFile;
	}

	public boolean getIsValid() {
		return _selectedParticles != null && _selectedParticles.length > 0;
	}

	public ParticleIndexes[] getEnvMeanPrtclIndxsS() {
		return _envPrtclIndxsS;
	}

	public ParticleIndexes[] getDiscardedParticles() {
		return _discardedParticles;
	}

	public double objectTypeAndStageToWeight(final int objectType,
			final int stage) {
		final double[] weights =
				_objectTypeToTotalWeightsByStage.get(objectType);
		if (weights == null || stage < 0 || stage >= weights.length) {
			return 0d;
		}
		return weights[stage];
	}

	public boolean getUsingStratified() {
		return _usingStratified;
	}

	/**
	 * Sim does not have a pos function. So to retrieve all of the particles,
	 * we need the following.
	 */
	public ParticleIndexes[] getAllPrtclIndxsSForSim() {
		return _allParticles;
	}
}
