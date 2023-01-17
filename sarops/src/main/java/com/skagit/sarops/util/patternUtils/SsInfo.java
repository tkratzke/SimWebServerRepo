package com.skagit.sarops.util.patternUtils;

import java.util.HashMap;

import com.skagit.util.MathX;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.navigation.GreatCircleCalculator;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.MotionType;

public class SsInfo {
	final private static double _LegalRelativeError =
			PatternUtilStatics._FudgeFactor - 1d;

	final public LatLng3 _center;
	final private double _sgndTsNmi;
	final private double _firstLegHdg;
	final private int _nSearchLegs;
	final private int _nHalfLaps;
	final private double _eplNmi;
	final private HashMap<GreatCircleArc, LegInfo> _legInfos;

	public SsInfo(final GreatCircleArc[] gcas) {
		final int nGcas = gcas == null ? 0 : gcas.length;
		boolean bad = nGcas == 0;
		if (!bad) {
			if (nGcas == 1) {
				final GreatCircleArc gca = gcas[0];
				if (gca.getTtlNmi() == 0d) {
					_center = PatternUtilStatics.DiscretizeLatLng(gca.getLatLng0());
					_sgndTsNmi = 0d;
					_firstLegHdg = 0d;
					_nSearchLegs = _nHalfLaps = 1;
					_eplNmi = 0d;
					_legInfos = new HashMap<>();
					_legInfos.put(gca, new LegInfo(gca, LegInfo.LegType.GENERIC));
					return;
				}
				bad = true;
			}
		}
		final GreatCircleArc gca0 = gcas[0];
		final GreatCircleArc gcaN = gcas[nGcas - 1];

		/** We must have an odd number of edges. */
		if (nGcas % 2 == 0) {
			bad = true;
		}

		/** Make sure they are heel-toe. */
		if (!bad) {
			for (int k = 0; k < nGcas - 1 && !bad; ++k) {
				final GreatCircleArc gcaA = gcas[k];
				final GreatCircleArc gcaB = gcas[k + 1];
				if (LatLng3._ByLatThenLng.compare(gcaA.getLatLng1(),
						gcaB.getLatLng0()) != 0) {
					bad = true;
				}
			}
		}
		/** Check lengths. */
		final double tsNmi = bad ? Double.NaN : gca0.getTtlNmi();
		if (!bad) {
			/** 1,1,2,2,3,3, ..., except for the last one. */
			for (int k = 0; k < nGcas - 1 && !bad; ++k) {
				final double nmi = gcas[k].getTtlNmi();
				final double targetNmi = ((k + 2) / 2) * tsNmi;
				final double relativeError =
						NumericalRoutines.getRelativeError(nmi, targetNmi);
				if (relativeError > _LegalRelativeError) {
					bad = true;
				}
			}
		}

		boolean firstTurnRight = true;
		if (!bad) {
			/** All turns should be the same and either 90 or -90. */
			final GreatCircleArc gca1 = gcas[1];
			final double turnToLeft0 = GreatCircleCalculator.getTurnToLeftD(gca0,
					gca1, /* round= */true);
			final double relativeErrorTo90 =
					NumericalRoutines.getRelativeError(turnToLeft0, 90d);
			final double targetTurnToLeft;
			if (relativeErrorTo90 <= _LegalRelativeError) {
				targetTurnToLeft = 90d;
				firstTurnRight = false;
			} else {
				final double relativeErrorToMinus90 =
						NumericalRoutines.getRelativeError(turnToLeft0, -90d);
				if (relativeErrorToMinus90 <= _LegalRelativeError) {
					targetTurnToLeft = -90d;
					firstTurnRight = true;
				} else {
					targetTurnToLeft = Double.NaN;
					firstTurnRight = false;
					bad = true;
				}
			}
			for (int k = 2; k < nGcas && !bad; ++k) {
				final double turnToLeft = GreatCircleCalculator
						.getTurnToLeftD(gcas[k - 1], gcas[k], /* round= */true);
				final double relativeError = NumericalRoutines
						.getRelativeError(turnToLeft, targetTurnToLeft);
				if (relativeError > _LegalRelativeError) {
					firstTurnRight = false;
					bad = true;
					break;
				}
			}
		}
		if (bad) {
			_nSearchLegs = _nHalfLaps = -1;
			_center = null;
			_sgndTsNmi = _firstLegHdg = _eplNmi = Double.NaN;
			_legInfos = null;
			return;
		}

		/** We're good. */
		_firstLegHdg = gca0.getRawInitialHdg();
		_sgndTsNmi = (firstTurnRight ? 1d : -1d) * tsNmi;
		_legInfos = new HashMap<>();
		_nSearchLegs = nGcas;
		for (int k = 0; k < nGcas; ++k) {
			final GreatCircleArc gca = gcas[k];
			_legInfos.put(gca, new LegInfo(gca, LegInfo.LegType.GENERIC));
		}
		/**
		 * Compute _nHalfLaps. Since we have at least 2 edges, we have at least
		 * 2 HalfLaps. We also have an odd number of edges. gcaN's length
		 * determines the number of halfLaps. Eg, if we have 3 edges, we should
		 * have 2 halfLaps and ||gcaN||/||gca0|| should be at most 1. In this
		 * case, if the ratio is bigger than 1, we have 3 halfLaps.
		 */
		final double lastLenNmi = gcaN.getTtlNmi();
		final double ratio = lastLenNmi / tsNmi;
		final int targetRatio = (nGcas - 1) / 2;
		if (ratio / PatternUtilStatics._FudgeFactor <= targetRatio) {
			_nHalfLaps = targetRatio + 1;
		} else {
			_nHalfLaps = targetRatio + 2;
		}

		/** If _nHalfLaps is even, finding the center is hard. */
		if (_nHalfLaps % 2 == 1) {
			_center = PatternUtilStatics.DiscretizeLatLng(gca0.getLatLng0());
		} else {
			final LatLng3 midPoint0 = gca0.computeMidpoint();
			final double lenNmi = gca0.getTtlNmi() / 2d;
			final double hdg = MathX.initialHdgX(midPoint0, gca0.getLatLng0()) +
					(firstTurnRight ? -90d : 90d);
			_center = PatternUtilStatics.DiscretizeLatLng(
					GreatCircleCalculator.getLatLng(midPoint0, hdg, lenNmi));
		}

		/** Compute ttlLegLen + ts. */
		final double perfect = _nHalfLaps * _nHalfLaps * tsNmi;
		final double allButLastLeg = perfect - (_nHalfLaps - 1) * tsNmi;
		_eplNmi = allButLastLeg + gcaN.getTtlNmi();
	}

	public int getNSearchLegs() {
		return _nSearchLegs;
	}

	public LegInfo.LegType getGcaType(final GreatCircleArc gca) {
		final LegInfo legInfo = _legInfos.get(gca);
		return legInfo == null ? LegInfo.LegType.GENERIC : legInfo._legType;
	}

	public boolean isValid() {
		return Math.abs(_sgndTsNmi) > 0d;
	}

	public double getSgndTsNmi() {
		return _sgndTsNmi;
	}

	public double getFirstLegHdg() {
		return _firstLegHdg;
	}

	public double getEplNmi() {
		return _eplNmi;
	}

	public SphericalTimedSegs computeSphericalTimedSegs(final long cst,
			final int searchDurationSecs, final double excBufferNmi) {
		final double searchHrs = searchDurationSecs / 3600d;
		final double rawSearchKts =
				(_eplNmi / searchHrs) / PatternUtilStatics._EffectiveSpeedReduction;
		final double tsNmi = Math.abs(_sgndTsNmi);
		final double lenNmi = tsNmi * _nHalfLaps;
		final boolean firstTurnRight = _sgndTsNmi > 0d;
		final SsPattern ssPattern = new SsPattern(/* cppToJavaTracer= */null, //
				rawSearchKts, cst, searchDurationSecs, //
				_center, _firstLegHdg, firstTurnRight, //
				/* minTsNmi= */tsNmi, /* fxdTsNmi= */tsNmi, excBufferNmi, //
				lenNmi, //
				MotionType.GREAT_CIRCLE.name(), /* expandSpecsIfNeeded= */true);
		return ssPattern._sphericalTimedSegs;
	}

}