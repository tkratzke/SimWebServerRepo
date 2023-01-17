package com.skagit.sarops.planner.plannerModel.pvSeq;

import java.util.Arrays;

import com.skagit.sarops.model.io.ModelReader;
import com.skagit.sarops.planner.plannerModel.PatternKind;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.navigation.LatLng3;

public class PvValuesFitter {

	final private static int _MinSearchDurationSecs =
			PatternVariable._MinSearchDurationSecs;
	final private static int _SearchDurationSecsInc = 10;

	public enum FitType {
		NOTHING, CST_ONLY, SCALE_BOXES, NO_SCALE_BOXES
	}

	/** Input is not full-canonical. Output is an item-by-item overwrite. */
	public static PvValue[] fitPvValues(final PvValue[] pvValues,
			final PvValue keyPvValue, final FitType fitType) {
		final int nPvValues = pvValues == null ? 0 : pvValues.length;
		if (keyPvValue == null || nPvValues == 0) {
			return pvValues == null ? null : pvValues.clone();
		}
		final PatternVariable keyPv = keyPvValue.getPv();
		final PvSeq pvSeq = keyPv.getPvSeq();

		/** Find the one it's supposed to replace. */
		int keyIdx = -1;
		for (int idx = 0; idx < nPvValues; ++idx) {
			final PvValue pvValue = pvValues[idx];
			if (pvValue != null && pvValue.getPv() == keyPv) {
				keyIdx = idx;
				break;
			}
		}
		if (keyIdx == -1) {
			return pvValues;
		}
		final PvValue keyPvValueIn = pvValues[keyIdx];

		/** Get rid of simple cases. */
		if (keyPv.getUserFrozenPvValue() != null) {
			return pvValues;
		}

		if (pvSeq == null || fitType == FitType.NOTHING) {
			if (PvValue._ByPvGrandOrdinalFirst.compare(keyPvValue,
					keyPvValueIn) == 0) {
				return pvValues;
			}
			final PvValue[] pvValuesClone = pvValues.clone();
			pvValuesClone[keyPv.getGrandOrd()] = keyPvValue;
			return pvValuesClone;
		}

		/** Gather the ones from the key PvSeq. */
		final PvValue[] pvValuesClone = pvValues.clone();
		final PvValue[] myPvValues0 = pvSeq.gatherMine(pvValues);
		final int nMyPvValues = myPvValues0.length;

		/** If only shifting the times, then just use pvSeq.adjustMyCsts. */
		if (fitType == FitType.CST_ONLY) {
			final PvValue[] myPvValues1 = pvSeq.adjustMyCsts(myPvValues0);
			for (int idx = 0; idx < nPvValues; ++idx) {
				final PvValue pvValue = pvValuesClone[idx];
				final PatternVariable pv = pvValue.getPv();
				for (int myPvValueIdx = 0; myPvValueIdx < nMyPvValues;
						++myPvValueIdx) {
					final PvValue myPvValue = myPvValues1[myPvValueIdx];
					if (myPvValue.getPv() == pv) {
						pvValuesClone[idx] = myPvValue;
						break;
					}
				}
			}
			return pvValuesClone;
		}

		/** Find the frozens that surround keyPv. */
		int startIdx = -1;
		int stopIdx = nMyPvValues;
		final int keyOrdWithinPvSeq = keyPv.getOrdWithinPvSeq();
		for (int myPvValueIdx = 0; myPvValueIdx < nMyPvValues; ++myPvValueIdx) {
			final PatternVariable pv = myPvValues0[myPvValueIdx].getPv();
			if (pv.getUserFrozenPvValue() != null) {
				final int ordWithinPvSeq = pv.getOrdWithinPvSeq();
				if (ordWithinPvSeq <= keyOrdWithinPvSeq) {
					startIdx = myPvValueIdx;
				}
				if (ordWithinPvSeq >= keyOrdWithinPvSeq) {
					stopIdx = myPvValueIdx;
					break;
				}
			}
		}
		if (startIdx == -1 || stopIdx == nMyPvValues) {
			return pvValues;
		}

		/**
		 * Compute starter, stopper, the old floaters in the block, and the new
		 * floaters in the block.
		 */
		final PvValue starter = startIdx == -1 ? null : myPvValues0[startIdx];
		final PvValue stopper =
				stopIdx == nMyPvValues ? null : myPvValues0[stopIdx];
		final int nBlockFloaters = stopIdx - (startIdx + 1);
		final PvValue[] blockFloaters0 = new PvValue[nBlockFloaters];
		for (int myPvValueIdx = startIdx + 1; myPvValueIdx < stopIdx;
				++myPvValueIdx) {
			blockFloaters0[myPvValueIdx - (startIdx + 1)] =
					myPvValues0[myPvValueIdx];
		}
		final PvValue[] blockFloaters1 = blockFloaters0.clone();
		for (int floaterIdx = 0; floaterIdx < nBlockFloaters; ++floaterIdx) {
			final PvValue floater = blockFloaters1[floaterIdx];
			if (floater.getPv().getOrdWithinPvSeq() == keyOrdWithinPvSeq) {
				blockFloaters1[floaterIdx] = keyPvValue;
				break;
			}
		}

		/**
		 * Compute the max duration for the nFloaters+1 transits and the
		 * nFloaters PttrnVbls.
		 */
		final long maxDurationSecs;
		if (starter == null && stopper == null) {
			/** The only block. */
			maxDurationSecs = pvSeq._totalDurationSecs;
		} else if (starter == null) {
			final long startRefSecs;
			if (pvSeq._launchRefSecs != ModelReader._UnsetTime) {
				startRefSecs = pvSeq._launchRefSecs;
			} else {
				/**
				 * First of multiple blocks, launch time not given, start with the
				 * launch, and stopper is not null. Must find the last one for
				 * pvSeq, which definitely exists, but is not necessarily stopper.
				 */
				final PvValue lastPvValue = myPvValues0[nMyPvValues - 1];
				final long estRefSecs = lastPvValue.getEstRefSecs();
				final long transitToRecoverySecs;
				if (pvSeq.hasRecoveryTransit()) {
					final GreatCircleArc transitGca = GreatCircleArc
							.CreateGca(lastPvValue.getEsp(), pvSeq._recoveryLatLng);
					final double nmi = transitGca.getTtlNmi();
					final double transitKts = pvSeq._recoveryKts;
					transitToRecoverySecs = Math.round(3600d * nmi / transitKts);
				} else {
					transitToRecoverySecs = 0L;
				}
				startRefSecs =
						estRefSecs + transitToRecoverySecs - pvSeq._totalDurationSecs;
			}
			maxDurationSecs = stopper.getCstRefSecs() - startRefSecs;
		} else if (stopper == null) {
			long endRefSecs;
			if (pvSeq._launchRefSecs != ModelReader._UnsetTime) {
				endRefSecs = pvSeq._launchRefSecs + pvSeq._totalDurationSecs;
			} else {
				/**
				 * Last of multiple blocks, launch time not given, and starter is
				 * not null.
				 */
				final PvValue firstPvValue = myPvValues0[0];
				final PatternVariable firstPv = firstPvValue.getPv();
				final GreatCircleArc transitGca = GreatCircleArc
						.CreateGca(pvSeq._launchLatLng, firstPvValue.getCsp());
				final double nmi = transitGca.getTtlNmi();
				final double transitKts = firstPv.getTransitKts();
				final long transitFromLaunchSecs =
						Math.round(3600d * nmi / transitKts);
				endRefSecs = pvSeq._pvSeqCstRefSecs - transitFromLaunchSecs +
						pvSeq._totalDurationSecs;
			}
			maxDurationSecs = endRefSecs - starter.getEstRefSecs();
		} else {
			maxDurationSecs = stopper.getCstRefSecs() - starter.getEstRefSecs();
		}

		/** Binary search for the right scaling. */
		double pTooHigh = -1d;
		for (int k = 0; k < 10; ++k) {
			final double p = Math.pow(2d, k);
			final PvValue[] blockFloatersX =
					scaleAll(pvSeq, starter, blockFloaters1, fitType, p);
			final long durationSecsX =
					pvSeq.getDurationSecs(starter, blockFloatersX, stopper);
			if (durationSecsX > maxDurationSecs) {
				pTooHigh = p;
				break;
			}
		}
		double pLowEnough = 0d;
		PvValue[] blockFloaters = null;
		while (Math.round(pLowEnough * maxDurationSecs) + 1 < Math
				.round(pTooHigh * maxDurationSecs)) {
			final double p = (pLowEnough + pTooHigh) / 2d;
			final PvValue[] blockFloatersX =
					scaleAll(pvSeq, starter, blockFloaters1, fitType, p);
			final long durationSecsX =
					pvSeq.getDurationSecs(starter, blockFloatersX, stopper);
			if (durationSecsX <= maxDurationSecs) {
				pLowEnough = p;
				blockFloaters = blockFloatersX;
			} else {
				pTooHigh = p;
			}
		}

		/** Copy over winning blockFloaters into pvValuesClone. */
		final PlannerModel plannerModel = keyPv.getPlannerModel();
		final int nPttrnVbls = plannerModel.getNPttrnVbls();
		final int[] grandOrdToIdx = new int[nPttrnVbls];
		Arrays.fill(grandOrdToIdx, -1);
		for (int k = 0; k < nPvValues; ++k) {
			final PvValue pvValue = pvValuesClone[k];
			if (pvValue == null) {
				continue;
			}
			final int grandOrd = pvValue.getPv().getGrandOrd();
			grandOrdToIdx[grandOrd] = k;
		}
		for (int k0 = 0; k0 < nBlockFloaters; ++k0) {
			final PvValue blockFloater = blockFloaters[k0];
			final PatternVariable pv = blockFloater.getPv();
			final int idx = grandOrdToIdx[pv.getGrandOrd()];
			pvValuesClone[idx] = blockFloater;
		}
		return pvValuesClone;
	}

	private static PvValue[] scaleAll(final PvSeq pvSeq,
			final PvValue starter, final PvValue[] blockFloaters,
			final FitType fitType, final double p) {
		final int nBlockFloaters =
				blockFloaters == null ? 0 : blockFloaters.length;
		if (nBlockFloaters == 0) {
			return new PvValue[0];
		}

		/** Initialize pvsRefSecs. */
		long pvsRefSecs = -1l;
		LatLng3 pvsExit = null;
		if (starter == null) {
			pvsRefSecs = pvSeq._launchRefSecs;
			if (pvsRefSecs == ModelReader._UnsetTime) {
				/** We need to get the scaled version so we can find the transit. */
				final PvValue pvValue0 = blockFloaters[0];
				final PvValue pvValue1 = getScaledPvValue(pvValue0, fitType, p);
				final GreatCircleArc transitGca =
						GreatCircleArc.CreateGca(pvsExit, pvValue1.getCsp());
				final double nmi = transitGca.getTtlNmi();
				final double transitKts = pvSeq._recoveryKts;
				final long transitSecs = Math.round(3600d * nmi / transitKts);
				pvsRefSecs = pvSeq._pvSeqCstRefSecs - transitSecs;
			}
		} else {
			pvsRefSecs = starter.getEstRefSecs();
			pvsExit = starter.getEsp();
		}
		final PvValue[] blockFloatersOut = new PvValue[nBlockFloaters];
		for (int k = 0; k < nBlockFloaters; ++k) {
			final PvValue pvValue0 = blockFloaters[k];
			blockFloatersOut[k] = getScaledPvValue(pvValue0, fitType, p);
		}
		return blockFloatersOut;
	}

	private static PvValue getScaledPvValue(final PvValue pvValue,
			final FitType fitType, final double p) {
		final PatternVariable pv = pvValue.getPv();
		final PatternKind patternKind = pv.getPatternKind();
		final long origSearchDurationSecs = pvValue.getSearchDurationSecs();
		if (origSearchDurationSecs <= _MinSearchDurationSecs) {
			return pvValue;
		}
		/** Constant values: */
		final double firstLegDirR = pvValue.getFirstLegDirR();
		final LatLng3 center = pvValue.getCenter();
		/** Scale search duration, and perhaps along and sgndAcross. */
		for (int k = 0; k < 10; ++k) {
			final int rawSearchDurationSecs0 = (int) Math
					.round(p * origSearchDurationSecs + k * _SearchDurationSecsInc);
			final int rawSearchDurationSecs =
					Math.max(rawSearchDurationSecs0, _MinSearchDurationSecs);
			final double scaleValue;
			if (fitType == FitType.SCALE_BOXES) {
				scaleValue =
						((double) rawSearchDurationSecs) / origSearchDurationSecs;
			} else {
				scaleValue = 1d;
			}
			final PvValue pvValue1;
			if (patternKind.isPsCs()) {
				final double alongR = scaleValue * pvValue.getSpecAlongR();
				final double sgndAcrossR =
						scaleValue * pvValue.getSpecSgndAcrossR();
				pvValue1 = PvValue.createPvValue(pv, /* cstRefSecs= */0L,
						rawSearchDurationSecs, center, firstLegDirR, alongR,
						sgndAcrossR);
			} else if (patternKind.isVs()) {
				pvValue1 = PvValue.createPvValue(pv, /* cstRefSecs= */0L,
						rawSearchDurationSecs, center, firstLegDirR,
						pvValue.getFirstTurnRight());
			} else if (patternKind.isSs()) {
				final double sgndAcrossR =
						scaleValue * pvValue.getSpecSgndAcrossR();
				pvValue1 = PvValue.createPvValue(pv, /* cstRefSecs= */0L,
						rawSearchDurationSecs, center, firstLegDirR,
						/* alongR= */Double.NaN, sgndAcrossR);
			} else {
				pvValue1 = null;
			}
			final long thisSearchDurationSecs1 = pvValue1.getSearchDurationSecs();
			if (thisSearchDurationSecs1 >= _MinSearchDurationSecs) {
				/** We have our winner. */
				return pvValue1;
			}
		}
		return null;
	}
}
