package com.skagit.sarops.util.patternUtils;

import java.util.ArrayList;
import java.util.HashMap;

import com.skagit.util.MathX;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.navigation.GreatCircleCalculator;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.MotionType;

public class PsCsInfo {
	final private static double _NmiToR = MathX._NmiToR;
	final private static double _LowThresholdTurn = 90d * 0.9;
	final private static double _HighThresholdTurn = 90d / 0.9;

	final public LatLng3 _center;
	/**
	 * The following is ignored except for re-writing a _model file's completed
	 * searches, and when we have a single leg.
	 */
	final private double _tsNmiIn;

	final private double _creepHdg;
	final private double _sgndTsNmi;
	final private double _sllNmi;
	final private double _ttlAcrossNmi;
	final private double _eplNmi;
	final private double _firstLegHdg;
	final private int _nSearchLegs;
	final private HashMap<GreatCircleArc, LegInfo> _legInfos;
	final private boolean _isOneLeggedLadderPattern;

	public PsCsInfo(final GreatCircleArc[] gcasIn, final double creepHdgIn0,
			final double tsNmiIn, final double tsNmiForSingleLeg) {
		final double creepHdgIn = Double.isFinite(creepHdgIn0) ?
				LatLng3.getInRange0_360(creepHdgIn0) : Double.NaN;
		_tsNmiIn = tsNmiIn > 0d ? tsNmiIn : Double.NaN;
		_legInfos = new HashMap<>();
		final int nGcasIn = gcasIn == null ? 0 : gcasIn.length;

		/** Nuisance cases. */
		if (nGcasIn == 0) {
			_center = null;
			_eplNmi = _sgndTsNmi =
					_creepHdg = _firstLegHdg = _sllNmi = _ttlAcrossNmi = Double.NaN;
			_isOneLeggedLadderPattern = false;
			_nSearchLegs = -1;
			return;
		}
		final GreatCircleArc firstGca = gcasIn[0];
		_firstLegHdg = firstGca.getRoundedInitialHdg();
		if (nGcasIn == 1) {
			_center =
					PatternUtilStatics.DiscretizeLatLng(firstGca.computeMidpoint());
			_sllNmi = firstGca.getTtlNmi();
			final double tsNmi =
					_tsNmiIn > 0d ? _tsNmiIn : (tsNmiForSingleLeg > 0d ?
							tsNmiForSingleLeg : PatternUtilStatics._TsInc);
			_ttlAcrossNmi = tsNmi;
			if (creepHdgIn >= 0d) {
				final double cwTwist =
						LatLng3.getInRange0_360(creepHdgIn - _firstLegHdg);
				if (_LowThresholdTurn <= cwTwist && cwTwist <= _HighThresholdTurn) {
					_isOneLeggedLadderPattern = true;
					_legInfos.put(firstGca,
							new LegInfo(firstGca, LegInfo.LegType.CREEP_IS_TO_RIGHT));
					_sgndTsNmi = roundToTsInc(tsNmi);
					_creepHdg = LatLng3.getInRange0_360(_firstLegHdg + 90d);
				} else {
					/** Assume !firstTurnRight. */
					_isOneLeggedLadderPattern = true;
					_legInfos.put(firstGca,
							new LegInfo(firstGca, LegInfo.LegType.CREEP_IS_TO_LEFT));
					_sgndTsNmi = -roundToTsInc(tsNmi);
					_creepHdg = LatLng3.getInRange0_360(_firstLegHdg + 270d);
				}
				_eplNmi = _sllNmi + tsNmi;
				_nSearchLegs = 1;
				return;
			}
			_eplNmi = _sgndTsNmi = _creepHdg = Double.NaN;
			_isOneLeggedLadderPattern = false;
			_nSearchLegs = -1;
			return;
		}

		/**
		 * More than one leg. Add "gap_filler" legs if necessary to connect the
		 * legs and, for each leg, create a LegInfo.
		 */
		_isOneLeggedLadderPattern = false;
		final ArrayList<GreatCircleArc> gcaList = new ArrayList<>();
		gcaList.add(firstGca);
		_legInfos.put(firstGca, new LegInfo(firstGca, LegInfo.LegType.GENERIC));
		GreatCircleArc lastGca = firstGca;
		for (int k = 1; k < nGcasIn; ++k) {
			final GreatCircleArc gca = gcasIn[k];
			if (!gca.getLatLng0().equals(lastGca.getLatLng1())) {
				final GreatCircleArc gapFillerGca = GreatCircleArc
						.CreateGca(lastGca.getLatLng1(), gca.getLatLng0());
				gcaList.add(gapFillerGca);
				_legInfos.put(gapFillerGca,
						new LegInfo(gapFillerGca, LegInfo.LegType.GAP_FILLER));
			}
			gcaList.add(gca);
			_legInfos.put(gca, new LegInfo(gca, LegInfo.LegType.GENERIC));
			lastGca = gca;
		}
		final int nGcas = gcaList.size();

		/** Check angles, and compute creepHdg and firstTurnRight. */
		double creepHdg = Double.NaN;
		boolean firstTurnRight = false;
		for (int k = 0; k < nGcas - 1; ++k) {
			/** Compute the turn at the end of leg k. */
			final GreatCircleArc gca0 = gcaList.get(k);
			final GreatCircleArc gca1 = gcaList.get(k + 1);
			/** turnD is in [-180,180). */
			final double turnD = GreatCircleCalculator.getTurnToLeftD(gca0, gca1,
					/* round= */true);
			final double absTurnD = Math.abs(turnD);
			if (absTurnD < _LowThresholdTurn || absTurnD > _HighThresholdTurn) {
				creepHdg = Double.NaN;
				break;
			}
			final boolean thisTurnRight = turnD < 0d;
			if (k == 0) {
				firstTurnRight = thisTurnRight;
				creepHdg = LatLng3
						.getInRange0_360((firstTurnRight ? 90d : 270d) + _firstLegHdg);
			}
			/** Should go Same, Same, Different, Different, etc. */
			final boolean sameAsFirst = thisTurnRight == firstTurnRight;
			if (k % 4 < 2 != sameAsFirst) {
				creepHdg = Double.NaN;
				break;
			}
		}
		if (!(creepHdg >= 0d)) {
			_eplNmi =
					_sllNmi = _sgndTsNmi = _creepHdg = _ttlAcrossNmi = Double.NaN;
			_center = null;
			_nSearchLegs = -1;
			return;
		}

		/**
		 * We define the SLL as the average of the odd-numbered legs unless we
		 * have an odd number of legs and the last leg has a different length
		 * than the average of the others.
		 */
		double ttlSearchLegLenNmi = 0d;
		if (nGcas <= 2) {
			_sllNmi = roundToTsInc(gcaList.get(0).getTtlNmi());
			_nSearchLegs = 1;
		} else {
			/** At least 2 searchLegs legs. */
			final int nSearchLegsForComputingSllNmi = nGcas / 2;
			double ttlSllNmi = 0d;
			for (int k = 0; k < nSearchLegsForComputingSllNmi; ++k) {
				final GreatCircleArc gca = gcaList.get(2 * k);
				final double nmi = gca.getHaversine() / _NmiToR;
				ttlSllNmi += nmi;
			}
			final double ttlRnddSllNmi = roundToInc(
					nSearchLegsForComputingSllNmi * PatternUtilStatics._TsInc,
					ttlSllNmi);
			final double sllNmi0 =
					roundToTsInc(ttlRnddSllNmi / nSearchLegsForComputingSllNmi);
			if (nGcas % 2 == 1) {
				_nSearchLegs = nSearchLegsForComputingSllNmi + 1;
				final double lastLegNmi = gcaList.get(nGcas - 1).getTtlNmi();
				if (NumericalRoutines.relativeErrorIsSmall(sllNmi0, lastLegNmi,
						PatternUtilStatics._FudgeFactor - 1d)) {
					_sllNmi = roundToTsInc((ttlSllNmi + lastLegNmi) / _nSearchLegs);
					ttlSearchLegLenNmi = _nSearchLegs * _sllNmi;
				} else {
					_sllNmi = sllNmi0;
					ttlSearchLegLenNmi = nSearchLegsForComputingSllNmi * _sllNmi +
							roundToTsInc(lastLegNmi);
				}
			} else {
				_sllNmi = sllNmi0;
				ttlSearchLegLenNmi = nSearchLegsForComputingSllNmi * _sllNmi;
				_nSearchLegs = nSearchLegsForComputingSllNmi;
			}
		}

		/**
		 * We define the TS as the average of the even-numbered legs unless we
		 * have an even number of legs and then we do not count the last leg.
		 * Moreover, ttlAcrossNmi is the total of the even-numbered legs plus 1
		 * TS.
		 */
		final double tsNmi;
		final double ttlCrossLegsNmi;
		if (nGcas <= 3) {
			tsNmi = roundToTsInc(gcaList.get(1).getTtlNmi());
			ttlCrossLegsNmi = tsNmi;
		} else {
			/** At least 2 cross legs. */
			final int nCrossLegsForComputingAbsTsNmi = (nGcas - 1) / 2;
			double ttlAcrossNmiForComputingAbsTsNmi0 = 0d;
			for (int k = 0; k < nCrossLegsForComputingAbsTsNmi; ++k) {
				final GreatCircleArc gca = gcaList.get(2 * k + 1);
				final double nmi = gca.getHaversine() / _NmiToR;
				ttlAcrossNmiForComputingAbsTsNmi0 += nmi;
			}
			final double ttlAcrossNmiForComputingAbsTsNmi = roundToInc(
					nCrossLegsForComputingAbsTsNmi * PatternUtilStatics._TsInc,
					ttlAcrossNmiForComputingAbsTsNmi0);
			tsNmi = roundToTsInc(ttlAcrossNmiForComputingAbsTsNmi /
					nCrossLegsForComputingAbsTsNmi);
			if (nGcas % 2 == 1) {
				ttlCrossLegsNmi = ttlAcrossNmiForComputingAbsTsNmi;
			} else {
				ttlCrossLegsNmi = roundToTsInc(ttlAcrossNmiForComputingAbsTsNmi +
						gcaList.get(nGcas - 1).getTtlNmi());
			}
		}
		_ttlAcrossNmi = ttlCrossLegsNmi + tsNmi;
		final double ttlLegsLenNmi = ttlSearchLegLenNmi + ttlCrossLegsNmi;

		/** Finally, we can set _sgndTsNmi. */
		final double rnddTsNmi = PatternUtilStatics
				.roundWithMinimum(PatternUtilStatics._TsInc, tsNmi);
		_sgndTsNmi = firstTurnRight ? rnddTsNmi : -rnddTsNmi;
		_creepHdg = creepHdg;
		_eplNmi = ttlLegsLenNmi + tsNmi;

		/** Set the legTypes. */
		boolean toRight = firstTurnRight;
		for (int k = 0; k < nGcas; k += 2) {
			final GreatCircleArc gca = gcaList.get(k);
			final LegInfo legInfo = _legInfos.get(gca);
			legInfo._legType = toRight ? LegInfo.LegType.CREEP_IS_TO_RIGHT :
					LegInfo.LegType.CREEP_IS_TO_LEFT;
			toRight = !toRight;
		}
		for (int k = 1; k < nGcas; k += 2) {
			final GreatCircleArc gca = gcaList.get(k);
			final LegInfo legInfo = _legInfos.get(gca);
			legInfo._legType = LegInfo.LegType.CROSS_LEG;
		}

		/**
		 * Find the center by finding the midPoint of the general search leg,
		 * and then moving perpendicular an amount based on _ttlAcrossNmi.
		 */
		final LatLng3 gca0Start = firstGca.getLatLng0();
		final LatLng3 intermediatePoint =
				MathX.getLatLngX(gca0Start, _firstLegHdg, (_sllNmi / 2d) * _NmiToR);
		final double hdg = MathX.initialHdgX(intermediatePoint, gca0Start) +
				(firstTurnRight ? -90d : 90d);
		final LatLng3 center = MathX.getLatLngX(intermediatePoint, hdg,
				ttlCrossLegsNmi / 2d * _NmiToR);
		_center = PatternUtilStatics.DiscretizeLatLng(center);
	}

	/** For convenience: */
	private static double roundToTsInc(final double d) {
		return PatternUtilStatics.roundWithMinimum(PatternUtilStatics._TsInc,
				d);
	}

	private static double roundToInc(final double inc, final double d) {
		return PatternUtilStatics.roundWithMinimum(inc, d);
	}

	public SphericalTimedSegs computeSphericalTimedSegs(final long cst,
			final int searchDurationSecs, final double excBufferNmi) {
		final double searchHrs = searchDurationSecs / 3600d;
		final double rawSearchKts =
				(_eplNmi / searchHrs) / PatternUtilStatics._EffectiveSpeedReduction;
		final double tsNmi = Math.abs(_sgndTsNmi);
		final double sllNmi = _sllNmi;
		final double alongNmi = tsNmi + sllNmi;
		final double acrossNmi = _ttlAcrossNmi;
		final boolean firstTurnRight = _sgndTsNmi > 0d;
		final double lenNmi, widNmi, orntn;
		final boolean ps;
		if (alongNmi >= acrossNmi) {
			lenNmi = alongNmi;
			widNmi = acrossNmi;
			orntn = _firstLegHdg;
			ps = true;
		} else {
			lenNmi = acrossNmi;
			widNmi = alongNmi;
			orntn = LatLng3
					.getInRange0_360(_firstLegHdg + (firstTurnRight ? 90d : -90d));
			ps = false;
		}
		final PsCsPattern psCsPatte =
				new PsCsPattern(/* cppToJavaTracer= */null, //
						rawSearchKts, cst, searchDurationSecs, //
						_center, orntn, firstTurnRight, //
						/* minTsNmi= */tsNmi, /* fxdTsNmi= */tsNmi, excBufferNmi, //
						lenNmi, widNmi, ps, //
						MotionType.GREAT_CIRCLE.name(), /* expandSpecsIfNeeded= */true);
		return psCsPatte._sphericalTimedSegs;
	}

	public LegInfo.LegType getGcaType(final GreatCircleArc gca) {
		final LegInfo legInfo = _legInfos.get(gca);
		return legInfo == null ? LegInfo.LegType.GENERIC : legInfo._legType;
	}

	public boolean isValid() {
		return Math.abs(_sgndTsNmi) > 0d || _isOneLeggedLadderPattern;
	}

	public double getMaxSearchLegLengthNmi() {
		return _sllNmi;
	}

	public double getTsSqNmi() {
		return _ttlAcrossNmi * (_sllNmi + Math.abs(_sgndTsNmi));
	}

	public double getTsNmiIn() {
		return _tsNmiIn;
	}

	public double getSgndTsNmi() {
		return _sgndTsNmi;
	}

	public double getFirstLegHdg() {
		return _firstLegHdg;
	}

	public double getCreepHdg() {
		return _creepHdg;
	}

	public double getEplNmi() {
		return _eplNmi;
	}

	public int getNSearchLegs() {
		return _nSearchLegs;
	}

}