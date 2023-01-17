package com.skagit.sarops.util.patternUtils;

import com.skagit.sarops.util.CppToJavaTracer;
import com.skagit.util.navigation.LatLng3;

public class VsPattern {

	/** Inputs. */
	final private double _rawSearchKts;
	final private long _baseSecs;
	final private int _searchDurationSecs;
	final private boolean _firstTurnRight;

	/** Output. */
	final public LatLng3 _center;
	final public double _sgndTsNmi;
	final public double _orntn;
	final public double _sgndAcrossNmi;

	final public double _eplNmi;

	final SphericalTimedSegs _sphericalTimedSegs;

	VsPattern(final CppToJavaTracer cppToJavaTracer,
			final double rawSearchKts, final long baseSecs,
			final int searchDurationSecs, //
			final LatLng3 center, final double orntn0,
			final boolean firstTurnRight, //
			final double excBufferNmi, //
			final String motionTypeId) {
		/** Unpack. */
		_rawSearchKts = rawSearchKts;
		_baseSecs = baseSecs;
		_searchDurationSecs = searchDurationSecs;
		_firstTurnRight = firstTurnRight;

		/** Discretize the center point and orientation. */
		_center = PatternUtilStatics.DiscretizeLatLng(center);
		_orntn = PatternUtilStatics.DiscretizeOrntn(orntn0);

		final double searchHrs = searchDurationSecs / 3600d;
		final double eplNmi = (_rawSearchKts * searchHrs) *
				PatternUtilStatics._EffectiveSpeedReduction;

		/** Convert from spec to ts. */
		final double tsNmi = PatternUtilStatics.computeVsTsNmi(eplNmi);
		_sgndTsNmi = tsNmi * (_firstTurnRight ? 1d : -1d);
		_sgndAcrossNmi = PatternUtilStatics.computeVsTsBoxLengthNmi(eplNmi) *
				Math.signum(_sgndTsNmi);
		_eplNmi = PatternUtilStatics.computeVsEplNmi(eplNmi);

		if (cppToJavaTracer != null && cppToJavaTracer.isActive()) {
			final double rawSearchKtsToUse = (_eplNmi / searchHrs) /
					PatternUtilStatics._EffectiveSpeedReduction;
			final String f = "\nAfter perfecting and rounding:\n\t" +
					"eplNmi[%f] rawSpeedToUse[%f] orntn[%f].";
			final String s = String.format(f, //
					_eplNmi, rawSearchKtsToUse, _orntn);
			cppToJavaTracer.writeTrace(s);
		}

		/** Generate the paths and polygons. */
		_sphericalTimedSegs = new SphericalTimedSegs(_rawSearchKts, //
				_baseSecs, _searchDurationSecs, //
				_center, _orntn, //
				_firstTurnRight, //
				excBufferNmi);
	}

	public String getMyStyleString() {
		final MyStyle myStyle = new MyStyle(_baseSecs, _searchDurationSecs,
				_center, _orntn, _firstTurnRight);
		final double searchHrs = _searchDurationSecs / 3600d;
		final double rawSearchKtsToUse =
				(_eplNmi / searchHrs) / PatternUtilStatics._EffectiveSpeedReduction;
		final String s = myStyle.getTheirStyleString(rawSearchKtsToUse,
				/* _minTsNmi= */Double.NaN);
		return s;
	}
}
