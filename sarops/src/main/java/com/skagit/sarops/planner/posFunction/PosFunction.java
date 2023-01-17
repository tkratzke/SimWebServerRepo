package com.skagit.sarops.planner.posFunction;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.skagit.sarops.computePFail.DetectValues;
import com.skagit.sarops.planner.ParticlesAndPriors;
import com.skagit.sarops.planner.ParticlesManager;
import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.planner.plannerModel.pvSeq.PvSeq;
import com.skagit.sarops.planner.posFunction.pFailsCache.PFailsCache;
import com.skagit.sarops.planner.posFunction.pvValueArrayPlus.PvValueArrayPlus;
import com.skagit.sarops.planner.summarySums.SummarySums;
import com.skagit.sarops.planner.summarySums.SummarySumsBag;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.sarops.tracker.lrcSet.LrcSet;
import com.skagit.sarops.util.SimGlobalStrings;
import com.skagit.util.randomx.Randomx;
import com.skagit.util.randomx.WeightedPairReDataAcc;

public class PosFunction {
	public enum EvalType {
		/** For optimization: */
		GROWING_SAMPLE_NFT("GrowingSmplNft", true, true, true, false,
				DetectValues.PFailType.NFT), //
		GROWING_SAMPLE_FT("GrowingSmplFt", true, true, true, false,
				DetectValues.PFailType.FT), //
		FXD_SAMPLE_FT("FxdSmplFt", true, true, false, false,
				DetectValues.PFailType.FT), //
		FXD_SAMPLE_NFT("FxdSmplNFt", true, true, false, false,
				DetectValues.PFailType.NFT), //
		/** For reports: */
		COMPLETE_FT_SLCTD("CmpltFtSlctd", true, true, false, true,
				DetectValues.PFailType.AIFT), //
		COMPLETE_FT_LANDED_ADRIFT("CmpltFtL/A", false, false, false, true,
				DetectValues.PFailType.AIFT), //
		COMPLETE_FT_ALL("CmpltFtAll", false, false, false, true,
				DetectValues.PFailType.AIFT);
		final public String _shortString;
		final public boolean _useViz2;
		final public boolean _selectedOnly;
		final public boolean _useGrowingSampleStrategy;
		final public boolean _forReportsOnly;
		final public DetectValues.PFailType _pFailType;

		private EvalType(final String shortString, //
				final boolean useViz2, //
				final boolean selectedOnly, //
				final boolean useGrowingSampleStrategy, //
				final boolean forReportsOnly, //
				final DetectValues.PFailType pFailType) {
			_shortString = shortString;
			_useViz2 = useViz2;
			_selectedOnly = selectedOnly;
			_useGrowingSampleStrategy = useGrowingSampleStrategy;
			_forReportsOnly = forReportsOnly;
			_pFailType = pFailType;
		}

		public static EvalType[] _EvalTypes = EvalType.values();
	}

	final public EvalType _evalType;
	final private ParticleIndexes[] _prtclIndxsS;
	final private double[] _priors;
	final private int[] _relevantObjectTypeIds;
	final private double[] _initPriors;
	final private double[] _oldPFails;
	final private double _maxSigOverNu2;
	final private double _maxSigOverNu1;
	final private int _minSampleSize;

	/** "friend" mechanism with particlesManager. */
	private ParticlesManager.AccessToKeyPrivates _accessToKeyPrivates = null;
	final private Planner _planner;

	public void receiveAccessToKeyPrivates(
			final ParticlesManager.AccessToKeyPrivates accessToKeyPrivates) {
		_accessToKeyPrivates = accessToKeyPrivates;
	}

	private ParticlesManager.AccessToKeyPrivates getAccessToKeyPrivates(
			final ParticlesManager particlesManager) {
		particlesManager.grantAccessToKeyPrivates(this);
		final ParticlesManager.AccessToKeyPrivates accessToKeyPrivates =
				_accessToKeyPrivates;
		_accessToKeyPrivates = null;
		return accessToKeyPrivates;
	}

	/**
	 * Sample from a given set of Particles/Priors; r == null means use
	 * everything that is given. prtclIndxsS == null means to use all forOptn
	 * particles for the sampling. This is for Small Sample and FEW_LEGS only.
	 */
	public PosFunction(final Planner planner, final Randomx r,
			final ParticleIndexes[] prtclIndxsS0, final double[] priors0,
			final int nInSample, final EvalType evalType) {
		_planner = planner;
		final SimCaseManager.SimCase simCase = _planner.getSimCase();
		final SimGlobalStrings simGlobalStrings = simCase.getSimGlobalStrings();
		_maxSigOverNu1 = simGlobalStrings.getMaxSigOverNu1();
		_maxSigOverNu2 = simGlobalStrings.getMaxSigOverNu2();
		_minSampleSize = simGlobalStrings.getMinSampleSize();
		final ParticlesManager particlesManager = planner.getParticlesManager();
		if (evalType._pFailType == DetectValues.PFailType.NFT) {
			_evalType = EvalType.FXD_SAMPLE_NFT;
		} else {
			_evalType = EvalType.FXD_SAMPLE_FT;
		}
		final ParticlesManager.AccessToKeyPrivates accessToKeyPrivates =
				getAccessToKeyPrivates(particlesManager);
		final ParticlesAndPriors particlesAndPriors = accessToKeyPrivates
				.getSampleParticlesAndPrior(prtclIndxsS0, priors0, nInSample, r);
		_prtclIndxsS = particlesAndPriors._particleIndexesS;
		_priors = particlesAndPriors._priors;
		_initPriors = _oldPFails = null;
		_relevantObjectTypeIds = particlesManager.getSelectedObjectTypeIds();
	}

	/**
	 * This is used to collect ParticleIndexesS and priors. The caller will do
	 * something else with them besides using them as a PosFunction.
	 * forOptnOnly is true means just get the ones for optimization.
	 */
	public PosFunction(final ParticlesManager particlesManager,
			final boolean forOptnOnly) {
		_accessToKeyPrivates = getAccessToKeyPrivates(particlesManager);
		if (forOptnOnly) {
			_prtclIndxsS = _accessToKeyPrivates.getForOptnParticles();
			_priors = _accessToKeyPrivates.getForOptnPriors();
		} else {
			_prtclIndxsS = _accessToKeyPrivates.getAllPrtclIndxsS();
			_priors = _accessToKeyPrivates.getAllPriors();
		}
		_planner = null;
		_maxSigOverNu1 = _maxSigOverNu2 = Double.NaN;
		_minSampleSize = Integer.MIN_VALUE;
		_relevantObjectTypeIds = null;
		_oldPFails = null;
		_initPriors = null;
		_evalType = null;
	}

	public double[] getForOptnPriors() {
		return _accessToKeyPrivates.getForOptnPriors();
	}

	/** For anything but small sample PosFunctions. */
	public PosFunction(final Planner planner, final EvalType evalType) {
		_planner = planner;
		final SimCaseManager.SimCase simCase = _planner.getSimCase();
		final SimGlobalStrings simGlobalStrings = simCase.getSimGlobalStrings();
		_maxSigOverNu1 = simGlobalStrings.getMaxSigOverNu1();
		_maxSigOverNu2 = simGlobalStrings.getMaxSigOverNu2();
		_minSampleSize = simGlobalStrings.getMinSampleSize();
		final ParticlesManager particlesManager =
				_planner.getParticlesManager();
		final ParticlesManager.AccessToKeyPrivates accessToKeyPrivates =
				getAccessToKeyPrivates(particlesManager);
		_evalType = evalType;
		if (_evalType._selectedOnly) {
			_relevantObjectTypeIds = particlesManager.getSelectedObjectTypeIds();
		} else {
			_relevantObjectTypeIds = particlesManager.getAllObjectTypeIds();
		}
		if (_evalType._useGrowingSampleStrategy) {
			_prtclIndxsS = accessToKeyPrivates.getSampleParticles();
			_priors = accessToKeyPrivates.getSamplePriors();
			_initPriors = _oldPFails = null;
			return;
		}
		if (_evalType == EvalType.COMPLETE_FT_SLCTD) {
			_prtclIndxsS = accessToKeyPrivates.getSelectedParticles();
			_priors = accessToKeyPrivates.getPriorsGivenSelection();
			_initPriors = accessToKeyPrivates.getSelectedInitialPriors();
			_oldPFails = accessToKeyPrivates.getOldPFailsOfSelected();
			return;
		}
		if (_evalType == EvalType.COMPLETE_FT_LANDED_ADRIFT) {
			_prtclIndxsS = accessToKeyPrivates.getLandedAdriftParticles();
			_priors = accessToKeyPrivates.getPriorsGivenLandedAdrift();
			_initPriors = accessToKeyPrivates.getLandedAdriftInitialPriors();
			_oldPFails = accessToKeyPrivates.getOldPFailsOfLandedAdrift();
			return;
		}
		if (_evalType == Planner._EvalTypeForTracker) {
			_prtclIndxsS = accessToKeyPrivates.getAllPrtclIndxsS();
			_priors = accessToKeyPrivates.getAllPriors();
			_initPriors = accessToKeyPrivates.getAllInitialPriors();
			_oldPFails = accessToKeyPrivates.getAllOldPFails();
			return;
		}
		_prtclIndxsS = null;
		_priors = _initPriors = _oldPFails = null;
	}

	public ParticleIndexes[] getParticleIndexesS() {
		return _prtclIndxsS;
	}

	public double[] getPriors() {
		return _priors;
	}

	public HashMap<Integer, LrcSet> getVizLrcSets(final PatternVariable pv) {
		final HashMap<Integer, LrcSet> viz2LrcSets = pv.getViz2LrcSets();
		final HashMap<Integer, LrcSet> viz1LrcSets = pv.getViz1LrcSets();
		return _evalType._selectedOnly ? viz2LrcSets : viz1LrcSets;
	}

	/** Computational routines. */
	public String getBigEvalTypeString(
			final ParticlesManager particlesManager) {
		if (_evalType._useGrowingSampleStrategy &&
				particlesManager.getUsingStratified()) {
			return "Stratified Sample";
		}
		return _evalType.toString();
	}

	public PosEvaluation computeEvaluation(final PvValueArrayPlus plus) {
		final PlannerModel plannerModel = _planner.getPlannerModel();
		final int nPttrnVbls = plannerModel.getNPttrnVbls();
		final SimCaseManager.SimCase simCase = _planner.getSimCase();
		if (!simCase.getKeepGoing()) {
			return null;
		}
		final ParticlesManager particlesManager =
				_planner.getParticlesManager();

		final double maxSigOverNu =
				plus.isFeasible() ? _maxSigOverNu2 : _maxSigOverNu1;
		final WeightedPairReDataAcc weightedPairReDataAcc =
				new WeightedPairReDataAcc();
		int nDone = 0;
		int nPassesDone = 0;
		final int nAll = _prtclIndxsS.length;
		if (nAll == 0) {
			final PosEvaluation evaluation = new PosEvaluation(this, /* mu= */0d,
					/* varOfEstmt= */0d, /* totalWeight= */0d, /* nDone= */0);
			return evaluation;
		}
		final boolean doAllInOnePass = !_evalType._useGrowingSampleStrategy ||
				particlesManager.getUsingStratified();
		while (true) {
			/** Compute how many we think we'll need. */
			final int nToDo;
			if (doAllInOnePass) {
				nToDo = nAll;
			} else if (nPassesDone == 0) {
				nToDo = Math.min(nAll, _minSampleSize);
			} else {
				final double muHat = weightedPairReDataAcc.getMean()[0];
				final double varOfOne = weightedPairReDataAcc.getVar()[0][0];
				/**
				 * We assume that varOfEstmt is proportional to nDone, and that
				 * muHat is essentially constant. We check for
				 * sig/Max(muHat,1-muHat).
				 */
				final double varOfEstmt = varOfOne / nDone;
				final double nu = Math.max(muHat, 1 - muHat);
				final double sigOverNu = Math.sqrt(varOfEstmt) / nu;
				if (sigOverNu < maxSigOverNu) {
					break;
				}
				final int totalToDo =
						(int) Math.ceil(nDone * sigOverNu / maxSigOverNu);
				nToDo = Math.min(nAll, Math.max(totalToDo, nDone + 20));
			}
			if (!simCase.getKeepGoing()) {
				return null;
			}
			/**
			 * Update the pFail computations to newN. The only time we're
			 * interested in all of the LRCs is CompleteFlyThroughAll.
			 */
			final DetectValues[][] detectValuesArrays =
					getDetectValuesArrays(nDone, nToDo, _prtclIndxsS, plus);
			/** Add in the new pos values and their squares. */
			for (int k = nDone; k < nToDo; ++k) {
				double pFail = 1d;
				for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
					final DetectValues detectValues;
					if (detectValuesArrays == null) {
						detectValues = null;
					} else if (detectValuesArrays[grandOrd] == null) {
						detectValues = null;
					} else if (detectValuesArrays[grandOrd][k - nDone] == null) {
						detectValues = null;
					} else {
						detectValues = detectValuesArrays[grandOrd][k - nDone];
					}
					final double thisPFail;
					if (detectValues == null) {
						if (simCase.getKeepGoing()) {
							final String message = String.format(
									"Null detectValues: k[%d] nDone[%d] " +
											"nToDo[%d] iPv[%d] nPttrnVbls[%d]",
									k, nDone, nToDo, grandOrd, nPttrnVbls);
							SimCaseManager.err(simCase, message);
							new Exception().printStackTrace();
						}
						thisPFail = 1d;
					} else {
						thisPFail = detectValues.getPFail(_evalType._pFailType);
					}
					pFail *= thisPFail;
				}
				final double thisPos = 1d - pFail;
				final double thisWt = _priors[k];
				weightedPairReDataAcc.add(/* u= */thisPos, /* v= */0d, thisWt);
			}
			nDone = nToDo;
			++nPassesDone;
			if (doAllInOnePass || nDone >= _prtclIndxsS.length) {
				break;
			}
		}
		final double mu = weightedPairReDataAcc.getMean()[0];
		final double varOfEstmt = weightedPairReDataAcc.getVar()[0][0] / nDone;
		final double totalWeight = weightedPairReDataAcc.getTotalWeight();
		final PosEvaluation evaluation =
				new PosEvaluation(this, mu, varOfEstmt, totalWeight, nDone);
		return evaluation;
	}

	public DetectValues[][] getDetectValuesArrays(final int currentN,
			final int newN, final ParticleIndexes[] particleIndexesWeUse,
			final PvValueArrayPlus plus) {
		final PlannerModel plannerModel = _planner.getPlannerModel();
		final int nPttrnVbls = plannerModel.getNPttrnVbls();
		final DetectValues[][] detectValuesArrays =
				new DetectValues[nPttrnVbls][];
		final int nOfInterest = newN - currentN;
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			/** Fill in emptyArray if it's null and we need it. */
			final PvValue pvValue = plus.getPvValue(grandOrd);
			final PatternVariable pv = pvValue == null ? null : pvValue.getPv();
			final boolean useEmptyArray = pv == null ||
					(_evalType._useViz2 && pv.getPermanentFrozenPvValue() != null) ||
					pvValue.onMars() || !pv.isActive();
			if (useEmptyArray) {
				final DetectValues[] emptyArray = new DetectValues[nOfInterest];
				for (int k1 = currentN; k1 < newN; ++k1) {
					emptyArray[k1 - currentN] = DetectValues.getEmpty();
				}
				detectValuesArrays[grandOrd] = emptyArray;
				continue;
			}
			final PFailsCache pFailsCache = _planner.getPFailsCache();
			detectValuesArrays[grandOrd] = pFailsCache.getDetectValuesArray(
					_planner, _evalType._useViz2, _evalType._pFailType,
					particleIndexesWeUse, currentN, newN, pvValue);
			if (detectValuesArrays[grandOrd] == null) {
				return null;
			}
		}
		return detectValuesArrays;
	}

	/** Build a structure useful for display and writing. */
	public SummarySumsBag.SummarySumsMapPlus computeSummarySumsMapPlus(
			final PvValueArrayPlus plus, final boolean allowNullReturn,
			final boolean forOptn) {
		if (plus == null) {
			return null;
		}
		final PvValue[] pvValues = plus.getCopyOfPvValues();
		final ParticlesManager particlesManager =
				_planner.getParticlesManager();
		final PlannerModel plannerModel = _planner.getPlannerModel();
		final int nPttrnVbls = plannerModel.getNPttrnVbls();
		final SimCaseManager.SimCase simCase = _planner.getSimCase();

		final ParticleIndexes[] prtclIndxsS = _prtclIndxsS;
		final double[] priors = _priors;

		final int nPrtcls;
		final double sumOfNewWeights;
		final double muHat;
		final double varOfEstmt;

		if (_evalType._useGrowingSampleStrategy) {
			/**
			 * Since we're sampling, we have to figure out what the set of
			 * particles is, and how big a sample we're taking.
			 */
			final PosEvaluation evaluation = computeEvaluation(plus);
			if (evaluation == null) {
				if (allowNullReturn) {
					return null;
				}
			}
			/**
			 * If evaluation came back null and we're not allowing null returns,
			 * we simply crash.
			 */
			nPrtcls = evaluation._nUsedOfArray;
			sumOfNewWeights = evaluation._sumOfNewWeights;
			muHat = evaluation._pos;
			varOfEstmt = evaluation._varOfEstmt;
		} else if (_evalType._selectedOnly) {
			nPrtcls = _prtclIndxsS.length;
			sumOfNewWeights = Double.NaN;
			muHat = Double.NaN;
			varOfEstmt = Double.NaN;
		} else if (_evalType == PosFunction.EvalType.COMPLETE_FT_LANDED_ADRIFT) {
			nPrtcls = _prtclIndxsS.length;
			sumOfNewWeights = Double.NaN;
			muHat = Double.NaN;
			varOfEstmt = Double.NaN;
		} else if (_evalType == Planner._EvalTypeForTracker) {
			nPrtcls = prtclIndxsS.length;
			sumOfNewWeights = Double.NaN;
			muHat = Double.NaN;
			varOfEstmt = Double.NaN;
		} else {
			assert false : "Trouble setting initPriors and oldPFails in PosFunction.";
			return null;
		}
		if (!simCase.getKeepGoing()) {
			return null;
		}
		final DetectValues[][] detectValuesArrays =
				new DetectValues[nPttrnVbls][];
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PvValue pvValue = pvValues[grandOrd];
			final PatternVariable pv = pvValue == null ? null : pvValue.getPv();
			final boolean useEmptyArray =
					(pv == null) || pvValue.onMars() || !pv.isActive();
			if (useEmptyArray) {
				final DetectValues[] emptyArray = new DetectValues[nPrtcls];
				for (int k1 = 0; k1 < nPrtcls; ++k1) {
					emptyArray[k1] = DetectValues.getEmpty();
				}
				detectValuesArrays[grandOrd] = emptyArray;
				continue;
			}
			/** Do the work. */
			final PFailsCache pFailsCache = _planner.getPFailsCache();
			detectValuesArrays[grandOrd] =
					pFailsCache.getDetectValuesArray(_planner, _evalType._useViz2,
							_evalType._pFailType, prtclIndxsS, 0, nPrtcls, pvValue);
			if (detectValuesArrays[grandOrd] == null) {
				return null;
			}
		}
		final HashMap<String, WorkplaceRow> workPlace = new HashMap<>();
		/** Fill in rows corresponding to individual PatternVariables. */
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			if (!simCase.getKeepGoing()) {
				return null;
			}
			final PvValue pvValue = pvValues[grandOrd];
			if (pvValue == null) {
				continue;
			}
			final String sortieId = pvValue.getPv().getId();
			final PatternVariable pv = pvValue.getPv();
			final String key0 = SummarySumsBag.getKey(sortieId, null);
			final WorkplaceRow workPlaceRow = new WorkplaceRow();
			final PvSeq pvSeq = pv.getPvSeq();
			if (pvSeq != null) {
				workPlaceRow._pvTrnstV = plus.getForOptnPvTrnstV(pv);
				workPlaceRow._pvSeqTrnstV = plus.getForOptnPvSeqTrnstV(pvSeq);
			}
			if (_evalType._forReportsOnly) {
				workPlaceRow._ovlV = plus.getForReportsTtlOvlV(pv,
						/* countTheOnesThatMayOverlap= */false);
				workPlaceRow._ovl = plus.getForReportsTtlOvlV(pv,
						/* countTheOnesThatMayOverlap= */true);
				workPlaceRow._pvTrnstV = plus.getForReportsTtlPvTrnstV(pv);
				workPlaceRow._pvSeqTrnstV =
						plus.getForReportsTtlPvSeqTransitV(pvSeq);
			} else {
				workPlaceRow._ovl = workPlaceRow._ovlV = plus.getForOptnOvlV(pv);
				workPlaceRow._pvTrnstV = plus.getForOptnPvTrnstV(pv);
				workPlaceRow._pvSeqTrnstV = plus.getForOptnPvSeqTrnstV(pvSeq);
			}

			workPlace.put(key0, workPlaceRow);
			final Map<Integer, LrcSet> objectTypeToLrcSet = getVizLrcSets(pv);
			for (final Map.Entry<Integer, LrcSet> entry : objectTypeToLrcSet
					.entrySet()) {
				final int objectType = entry.getKey();
				final String key1 = SummarySumsBag.getKey(sortieId, objectType);
				workPlace.put(key1, new WorkplaceRow());
			}
			for (int iParticle = 0; iParticle < nPrtcls; ++iParticle) {
				final ParticleIndexes particle = prtclIndxsS[iParticle];
				final double prior = priors[iParticle];
				final double initPrior =
						_initPriors == null ? Double.NaN : _initPriors[iParticle];
				final double oldPFail =
						_oldPFails == null ? Double.NaN : _oldPFails[iParticle];
				final DetectValues detectValues =
						detectValuesArrays[grandOrd][iParticle];
				final double pFail = detectValues.getPFail(_evalType._pFailType);
				final double proportionIn = detectValues.getProportionIn();
				final double pos = 1d - pFail;
				final double cumPos = 1d - (oldPFail * pFail);
				final double oldPos = 1d - oldPFail;
				workPlaceRow._sumNewWts += prior;
				workPlaceRow._sumWtXPos += prior * pos;
				workPlaceRow._sumInitWt += initPrior;
				workPlaceRow._sumInitWtXPvsPos += initPrior * oldPos;
				workPlaceRow._sumInitWtXCumPos += initPrior * cumPos;
				workPlaceRow._sumWtXPropIn += prior * proportionIn;
				/**
				 * A particle's ObjectType is what it is at the end of the planner
				 * epoch.
				 */
				final int objectType = particlesManager
						.getObjectType(plannerModel.getMaxPvRefSecs(), particle);
				final String key1 = SummarySumsBag.getKey(sortieId, objectType);
				final WorkplaceRow workPlaceRow1 = workPlace.get(key1);
				if (workPlaceRow1 != null) {
					workPlaceRow1._sumNewWts += prior;
					workPlaceRow1._sumWtXPos += prior * pos;
					workPlaceRow1._sumInitWt += initPrior;
					workPlaceRow1._sumInitWtXPvsPos += initPrior * oldPos;
					workPlaceRow1._sumInitWtXCumPos += initPrior * cumPos;
					workPlaceRow1._sumWtXPropIn += prior * proportionIn;
				} else {
					/**
					 * Since workPlace is set up as per pv's set of LRCs, and there
					 * might not be an LRC in that set for this ObjectType,
					 * workPlaceRow1 might be null, which would put us in this block.
					 * In that case, we do nothing.
					 */
				}
			}
		}
		/** Work on the "grand one.". */
		if (!simCase.getKeepGoing()) {
			return null;
		}
		final WorkplaceRow grandWorkPlaceRow = new WorkplaceRow();
		if (_evalType._forReportsOnly) {
			grandWorkPlaceRow._ovlV = plus.getForReportsTtlOvlV(/* pv= */null,
					/* countTheOnesThatMayOverlap= */false);
			grandWorkPlaceRow._ovl = plus.getForReportsTtlOvlV(/* pv= */null,
					/* countTheOnesThatMayOverlap= */false);
			grandWorkPlaceRow._pvTrnstV =
					plus.getForReportsTtlPvTrnstV(/* pv= */null);
			grandWorkPlaceRow._pvSeqTrnstV =
					plus.getForReportsTtlPvSeqTransitV(/* pv= */null);
		} else {
			grandWorkPlaceRow._ovlV = plus.getForOptnOvlV(/* pv= */null);
			grandWorkPlaceRow._ovl = plus.getForOptnOvlV(/* pv= */null);
			grandWorkPlaceRow._pvTrnstV = plus.getForOptnPvTrnstV(/* pv= */null);
			grandWorkPlaceRow._pvSeqTrnstV =
					plus.getForOptnPvSeqTrnstV(/* pv= */null);
		}
		final String key0 = SummarySumsBag.getKey(null, null);
		workPlace.put(key0, grandWorkPlaceRow);
		for (final int objectType : _relevantObjectTypeIds) {
			final String key1 = SummarySumsBag.getKey(null, objectType);
			workPlace.put(key1, new WorkplaceRow());
		}
		for (int iParticle = 0; iParticle < nPrtcls; ++iParticle) {
			final ParticleIndexes particle = prtclIndxsS[iParticle];
			final double prior = priors[iParticle];
			final double initPrior =
					_initPriors == null ? Double.NaN : _initPriors[iParticle];
			final double oldPFail =
					_oldPFails == null ? Double.NaN : _oldPFails[iParticle];
			double newPFail = 1d;
			for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
				newPFail *= detectValuesArrays[grandOrd][iParticle]
						.getPFail(_evalType._pFailType);
			}
			final double pos = 1d - newPFail;
			final double oldPos = 1d - oldPFail;
			final double cumPos = 1d - (oldPFail * newPFail);
			double propIn = 0d;
			for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
				propIn += detectValuesArrays[grandOrd][iParticle].getProportionIn();
			}
			propIn = Math.min(propIn, 1d);
			grandWorkPlaceRow._sumNewWts += prior;
			grandWorkPlaceRow._sumWtXPos += prior * pos;
			grandWorkPlaceRow._sumInitWt += initPrior;
			grandWorkPlaceRow._sumInitWtXPvsPos += initPrior * oldPos;
			grandWorkPlaceRow._sumInitWtXCumPos += initPrior * cumPos;
			grandWorkPlaceRow._sumWtXPropIn += prior * propIn;
			final int objectType = particlesManager
					.getObjectType(plannerModel.getMaxPvRefSecs(), particle);
			final String key1 = SummarySumsBag.getKey(null, objectType);
			final WorkplaceRow grandWorkPlaceRow1 = workPlace.get(key1);
			if (grandWorkPlaceRow1 != null) {
				grandWorkPlaceRow1._sumNewWts += prior;
				grandWorkPlaceRow1._sumWtXPos += prior * pos;
				grandWorkPlaceRow1._sumInitWt += initPrior;
				grandWorkPlaceRow1._sumInitWtXPvsPos += initPrior * oldPos;
				grandWorkPlaceRow1._sumInitWtXCumPos += initPrior * cumPos;
				grandWorkPlaceRow1._sumWtXPropIn += prior * propIn;
			}
		}
		/** Compute the normalizing constants. */
		final double[] normalizingSums = new double[] { 0d, 0d };
		for (int iParticle = 0; iParticle < nPrtcls; ++iParticle) {
			final double prior = priors[iParticle];
			final double initPrior =
					_initPriors == null ? Double.NaN : _initPriors[iParticle];
			normalizingSums[0] += prior;
			normalizingSums[1] += initPrior;
		}
		final TreeMap<String, SummarySums> summarySumsMap = new TreeMap<>();
		for (final Map.Entry<String, WorkplaceRow> entry : workPlace
				.entrySet()) {
			final String key = entry.getKey();
			final WorkplaceRow workPlaceRow = entry.getValue();
			final SummarySums summarySums =
					new SummarySums(workPlaceRow, normalizingSums);
			summarySumsMap.put(key, summarySums);
		}
		return new SummarySumsBag.SummarySumsMapPlus(this, summarySumsMap,
				nPrtcls, sumOfNewWeights, muHat, varOfEstmt);
	}
}
