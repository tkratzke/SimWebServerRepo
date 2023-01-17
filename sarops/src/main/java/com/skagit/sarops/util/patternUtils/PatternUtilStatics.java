package com.skagit.sarops.util.patternUtils;

import com.skagit.sarops.util.SimGlobalStrings;
import com.skagit.util.MathX;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.navigation.LatLng3;

public class PatternUtilStatics {

	final public static double _FudgeFactor = 1.00005;

	final public static double _TsInc = 0.1;
	public static final double _TsIncR = _TsInc * MathX._NmiToR;
	final static double _BufferInc = _TsInc;
	final static double _BufferIncR = _BufferInc * MathX._NmiToR;
	final public static double _EffectiveSpeedReduction = 0.85;

	/** For rounding: */
	final public static double _LatInc = 1d / 600d;
	final public static double _LngInc = 1d / 600d;
	final public static double _OrntnDegInc = 1d;

	public static double DiscretizeLat(final double latIn) {
		final double lat =
				LatLng3.getInRange180_180(NumericalRoutines.round(latIn, _LatInc));
		return lat;
	}

	public static double DiscretizeLng(final double lngIn) {
		final double lng =
				LatLng3.getInRange180_180(NumericalRoutines.round(lngIn, _LngInc));
		return lng;
	}

	public static double DiscretizeOrntn(final double orntn0) {
		final double orntn1 = LatLng3.getInRange0_360(orntn0);
		final double orntn = NumericalRoutines.round(orntn1, _OrntnDegInc);
		return orntn == 360d ? 0d : orntn;
	}

	public static String getVersionName() {
		return SimGlobalStrings.getStaticVersionName();
	}

	public static double roundWithMinimum(final double inc, final double d) {
		return NumericalRoutines.round(Math.max(inc, d), inc);
	}

	public static LatLng3 DiscretizeLatLng(final LatLng3 latLng) {
		final double lat = latLng.getLat();
		final double lng = latLng.getLng();
		final double discLat = DiscretizeLat(lat);
		final double discLng = DiscretizeLng(lng);
		if (lat == discLat) {
			if (lng == discLng) {
				return latLng;
			}
		}
		return LatLng3.getLatLngB(discLat, discLng);
	}

	/** For VS Patterns: */
	/**
	 * If we build a VS ourselves, we use the following for the number of
	 * legs. Should be either 7 or 9.
	 */
	public static final int _NVsSearchLegs = 9;

	final public static double _VsTrackLengthInc =
			9d * PatternUtilStatics._TsInc;

	public static double computeVsEplNmi(final double eplNmi) {
		final double d = _VsTrackLengthInc *
				Math.floor((eplNmi / _VsTrackLengthInc) * _FudgeFactor);
		return Math.max(d, _VsTrackLengthInc);
	}

	public static double computeVsTsNmi(final double eplNmi) {
		return computeVsEplNmi(eplNmi) / 9d;
	}

	public static double computeVsTsBoxLengthNmi(final double eplNmi) {
		return computeVsEplNmi(eplNmi) / 3d;
	}

	/** For SS Patterns: */
	final public static boolean _OddNHalfLapsOnly = false;
	final public static int _HalfLapsIncrement = _OddNHalfLapsOnly ? 2 : 1;

	public static int AdjustNHalfLaps(final boolean increase,
			final int oldNHalfLaps) {
		if (increase) {
			return oldNHalfLaps + _HalfLapsIncrement;
		}
		return Math.max(1, oldNHalfLaps - _HalfLapsIncrement);
	}

}
