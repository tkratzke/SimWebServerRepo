package com.skagit.sarops.util.patternUtils;

import com.skagit.sarops.planner.plannerModel.PatternKind;
import com.skagit.sarops.util.CppToJavaTracer;
import com.skagit.util.navigation.LatLng3;

public class SsPattern {

	/** Inputs. */
	final private double _rawSearchKts0;
	final private long _baseSecs;
	final private int _searchDurationSecs;
	final private boolean _firstTurnRight;
	final private double _minTsNmi;

	/** Output. */
	final public LatLng3 _center;
	final public double _sgndTsNmi;
	final public int _nHalfLaps;
	/** "TheirStyle" outputs. */
	final public double _lenNmi;
	final public double _orntn;

	final public double _eplNmi;

	final SphericalTimedSegs _sphericalTimedSegs;

	SsPattern(final CppToJavaTracer cppToJavaTracer,
			final double rawSearchKts0, final long baseSecs,
			final int searchDurationSecs, //
			final LatLng3 center, final double orntn0,
			final boolean firstTurnRight, //
			final double minTsNmi, final double fxdTsNmi,
			final double excBufferNmi, //
			final double lenNmi, //
			final String motionTypeId, final boolean expandSpecsIfNeeded) {
		/** Unpack. */
		_rawSearchKts0 = rawSearchKts0;
		_baseSecs = baseSecs;
		_searchDurationSecs = searchDurationSecs;
		_firstTurnRight = firstTurnRight;
		_minTsNmi = minTsNmi;

		/** Discretize the center point and orientation. */
		_center = PatternUtilStatics.DiscretizeLatLng(center);
		_orntn = PatternUtilStatics.DiscretizeOrntn(orntn0);

		/** Compute eplNmi0. */
		final double specAcrossNmi = lenNmi;
		final double searchHrs = searchDurationSecs / 3600d;
		final double eplNmi = (_rawSearchKts0 * searchHrs) *
				PatternUtilStatics._EffectiveSpeedReduction;

		/** Convert from spec to ts. */
		final DiscreteSsSpecToTs discSsTsBoxAdjuster = new DiscreteSsSpecToTs(
				eplNmi, minTsNmi, fxdTsNmi, specAcrossNmi, expandSpecsIfNeeded);
		/** We have the ts box. */
		final double tsNmi = discSsTsBoxAdjuster._ts;
		_sgndTsNmi = tsNmi * (_firstTurnRight ? 1d : -1d);
		_nHalfLaps = discSsTsBoxAdjuster._nHalfLaps;
		final double tsBoxAcrossNmi = _nHalfLaps * tsNmi;
		_eplNmi = _nHalfLaps * _nHalfLaps * tsNmi;

		/** Fill in "TheirStyle" values: */
		_lenNmi = tsBoxAcrossNmi;

		if (cppToJavaTracer != null && cppToJavaTracer.isActive()) {
			final double ttlLegLengthPlusTs = _nHalfLaps * _nHalfLaps * tsNmi;
			final double rawSearchKtsToUse = (ttlLegLengthPlusTs / searchHrs) /
					PatternUtilStatics._EffectiveSpeedReduction;
			final String f = "\nAfter perfecting and rounding:\n\t" +
					"EplNmi[%f] RawSpeedToUse[%f] orntn[%f] Len[%f].";
			final String s = String.format(f, //
					ttlLegLengthPlusTs, rawSearchKtsToUse, _orntn, _lenNmi);
			cppToJavaTracer.writeTrace(s);
		}

		_sphericalTimedSegs =
				new SphericalTimedSegs(baseSecs, searchDurationSecs, //
						_center, _orntn, //
						specAcrossNmi, //
						tsBoxAcrossNmi, //
						_sgndTsNmi, //
						excBufferNmi);
	}

	public String getMyStyleString() {
		final MyStyle myStyle = new MyStyle(PatternKind.SS, _baseSecs,
				_searchDurationSecs, _center, _orntn, _firstTurnRight, _lenNmi,
				/* widNmi= */Double.NaN, /* ps= */true);
		final double searchHrs = _searchDurationSecs / 3600d;
		final double rawSearchKtsToUse =
				(_eplNmi / searchHrs) / PatternUtilStatics._EffectiveSpeedReduction;
		final String s =
				myStyle.getTheirStyleString(rawSearchKtsToUse, _minTsNmi);
		return s;
	}
}
