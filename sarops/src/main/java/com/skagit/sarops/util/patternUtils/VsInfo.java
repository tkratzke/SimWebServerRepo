package com.skagit.sarops.util.patternUtils;

import java.util.HashMap;

import com.skagit.util.NumericalRoutines;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.navigation.GreatCircleCalculator;
import com.skagit.util.navigation.LatLng3;

public class VsInfo {

	final private static double _RelativeError =
			PatternUtilStatics._FudgeFactor - 1d;

	final public LatLng3 _center;
	final private double _sgndTsNmi;
	final private double _firstLegHdg;
	final private HashMap<GreatCircleArc, LegInfo> _legInfos;

	public VsInfo(final GreatCircleArc[] gcas) {
		final int nGcas = gcas == null ? 0 : gcas.length;
		boolean bad = nGcas != 7 && nGcas != 9;
		if (!bad) {
			/** Make sure they are heel-toe, including last to first. */
			for (int k = 0; k < nGcas && !bad; ++k) {
				final GreatCircleArc gcaA = gcas[k];
				final GreatCircleArc gcaB = gcas[(k + 1) % nGcas];
				if (LatLng3._ByLatThenLng.compare(gcaA.getLatLng1(),
						gcaB.getLatLng0()) != 0) {
					bad = true;
					break;
				}
			}
		}
		if (!bad) {
			/** Make sure cross legs are the same length. */
			final int[] legIndxsToCheck;
			if (nGcas == 7) {
				legIndxsToCheck = new int[] { 1, 3, 5 };
			} else {
				legIndxsToCheck = new int[] { 1, 4, 7 };
			}
			bad = !sameLength(legIndxsToCheck, gcas);
		}
		if (!bad) {
			/** Make sure legs going through the center are the same length. */
			final int[] legIndxsToCheck;
			if (nGcas == 7) {
				legIndxsToCheck = new int[] { 2, 4 };
			} else {
				legIndxsToCheck = null;
			}
			bad = !sameLength(legIndxsToCheck, gcas);
		}
		if (!bad) {
			/** Make sure legs going touching the center are the same length. */
			final int[] legIndxsToCheck;
			if (nGcas == 7) {
				legIndxsToCheck = new int[] { 0, 6 };
			} else {
				legIndxsToCheck = new int[] { 0, 2, 3, 5, 6, 8 };
			}
			bad = !sameLength(legIndxsToCheck, gcas);
		}
		if (bad) {
			_center = null;
			_sgndTsNmi = _firstLegHdg = Double.NaN;
			_legInfos = null;
			return;
		}

		final GreatCircleArc gca0 = gcas[0];
		_center = gca0.getLatLng0();
		final GreatCircleArc gca1 = gcas[1];

		/**
		 * <pre>
		 * Compute the area. The following returns a number in [0,2Pi), which is
		 * the interior angle, assuming ccw:
		 * public static double getInteriorAngleForCcwR(
		 *   final LatLng3 latLng00, final LatLng3 pivot,
		 *   final LatLng3 latLng11);
		 * </pre>
		 */
		final double halfInteriorAngleForCcwR =
				GreatCircleCalculator.getInteriorAngleForCcwR(gca0.getLatLng0(),
						gca0.getLatLng1(), gca1.getLatLng1());
		final boolean firstTurnRight = halfInteriorAngleForCcwR >= Math.PI;
		_sgndTsNmi = gca0.getTtlNmi() * (firstTurnRight ? 1d : -1d);
		_firstLegHdg = gca0.getRoundedInitialHdg();
		_legInfos = new HashMap<>();
		for (int k = 0; k < nGcas; ++k) {
			final GreatCircleArc gca = gcas[k];
			_legInfos.put(gca, new LegInfo(gca, LegInfo.LegType.GENERIC));
		}
	}

	private static boolean sameLength(final int[] legIndxsToCheck,
			final GreatCircleArc[] gcas) {
		final int nLegsToCheck =
				legIndxsToCheck == null ? 0 : legIndxsToCheck.length;
		if (nLegsToCheck < 2) {
			return true;
		}
		final GreatCircleArc refGca = gcas[legIndxsToCheck[0]];
		final double refGcaNmi = refGca.getTtlNmi();
		for (int k = 1; k < nLegsToCheck; ++k) {
			final GreatCircleArc gca = gcas[legIndxsToCheck[k]];
			final double gcaNmi = gca.getTtlNmi();
			if (!NumericalRoutines.relativeErrorIsSmall(refGcaNmi, gcaNmi,
					_RelativeError)) {
				return false;
			}
		}
		return true;
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
		return 9d * Math.abs(_sgndTsNmi);
	}

	public int getNSearchLegs() {
		return _legInfos == null ? 0 : _legInfos.size();
	}

	public SphericalTimedSegs computeSphericalTimedSegs(final long cst,
			final int searchDurationSecs, final double excBufferNmi) {
		final double eplNmi = Math.abs(_sgndTsNmi * 9d);
		final double searchHrs = searchDurationSecs / 3600d;
		final double rawSearchKts =
				(eplNmi / searchHrs) / PatternUtilStatics._EffectiveSpeedReduction;
		return new SphericalTimedSegs(//
				rawSearchKts, cst, searchDurationSecs, //
				_center, _firstLegHdg, /* firstTurnRight= */_sgndTsNmi > 0d, //
				excBufferNmi);
	}

}