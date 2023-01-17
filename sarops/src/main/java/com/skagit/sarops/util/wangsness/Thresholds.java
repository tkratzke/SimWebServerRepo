package com.skagit.sarops.util.wangsness;

public class Thresholds {
	final public double _areaThresholdSqNmi;
	final public double _distanceThresholdNmi;
	final public double _semiMajorThresholdNmi;
	final public double _majorToMinorThreshold;
	final public double _minAngleD;

	public Thresholds(final double areaThresholdSqNmi,
			final double distanceThresholdNmi, final double semiMajorThresholdNmi,
			final double majorToMinor, final double minAngleD) {
		_areaThresholdSqNmi = areaThresholdSqNmi;
		_distanceThresholdNmi = distanceThresholdNmi;
		_semiMajorThresholdNmi = semiMajorThresholdNmi;
		_majorToMinorThreshold = majorToMinor;
		_minAngleD = minAngleD;
	}

	public String getString() {
		final String s = String.format(
				"AreaNM[%f] dNM[%f] semiM_NM[%f] M/m[%f] minAngleD[%f]", //
				_areaThresholdSqNmi, _distanceThresholdNmi, _semiMajorThresholdNmi,
				_majorToMinorThreshold, _minAngleD);
		return s;
	}
}