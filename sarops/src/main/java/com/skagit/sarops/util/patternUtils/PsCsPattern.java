package com.skagit.sarops.util.patternUtils;

import com.skagit.sarops.planner.plannerModel.PatternKind;
import com.skagit.sarops.util.CppToJavaTracer;
import com.skagit.util.navigation.LatLng3;

public class PsCsPattern {

	/** Inputs. */
	final private double _rawSearchKts0;
	final private long _baseSecs;
	final private int _searchDurationSecs;
	final private boolean _firstTurnRight;
	final private double _minTsNmi;

	/** Output. */
	final public LatLng3 _center;
	final public double _firstLegHdg;
	final public double _sgndTsNmi;
	final public double _sllNmi;
	final public int _nSearchLegs;

	/** "TheirStyle" outputs. */
	final public double _lenNmi;
	final public double _widNmi;
	final public double _orntn;
	final public boolean _ps;

	final public double _eplNmi;

	final SphericalTimedSegs _sphericalTimedSegs;

	PsCsPattern(final CppToJavaTracer cppToJavaTracer,
			final double rawSearchKts0, final long baseSecs,
			final int searchDurationSecs, //
			final LatLng3 center, final double orntn0,
			final boolean firstTurnRight, //
			final double minTsNmi, final double fxdTsNmi,
			final double excBufferNmi, //
			final double lenNmi0, final double widNmi0, final boolean ps, //
			final String motionTypeId, final boolean expandSpecsIfNeeded) {
		/** Unpack. */
		_rawSearchKts0 = rawSearchKts0;
		_baseSecs = baseSecs;
		_searchDurationSecs = searchDurationSecs;
		_firstTurnRight = firstTurnRight;
		_minTsNmi = minTsNmi;

		/** Discretize the center point and orientation. */
		_center = PatternUtilStatics.DiscretizeLatLng(center);
		/**
		 * Since the orientation might change when we round, we defer the
		 * setting of _orntn until then.
		 */
		final double orntn = PatternUtilStatics.DiscretizeOrntn(orntn0);

		/** Convert to along/across, and compute eplNmi0. */
		final double lenNmi = Math.max(lenNmi0, widNmi0);
		final double widNmi = Math.min(lenNmi0, widNmi0);
		double specAlongNmi, specAcrossNmi, firstLegHdg;
		if (ps) {
			specAlongNmi = lenNmi;
			specAcrossNmi = widNmi;
			firstLegHdg = orntn;
		} else {
			specAlongNmi = widNmi;
			specAcrossNmi = lenNmi;
			firstLegHdg = orntn - (_firstTurnRight ? 90d : -90d);
		}
		_firstLegHdg = PatternUtilStatics.DiscretizeOrntn(firstLegHdg);
		final double searchHrs = searchDurationSecs / 3600d;
		final double eplNmi = (_rawSearchKts0 * searchHrs) *
				PatternUtilStatics._EffectiveSpeedReduction;

		/** Convert from spec to ts. */
		final DiscreteLpSpecToTs discLpTsBoxAdjuster =
				new DiscreteLpSpecToTs(eplNmi, minTsNmi, fxdTsNmi, specAlongNmi,
						specAcrossNmi, expandSpecsIfNeeded);
		/** We have the ts box. */
		final double tsNmi = discLpTsBoxAdjuster._ts;
		_sgndTsNmi = tsNmi * (_firstTurnRight ? 1d : -1d);
		_sllNmi = discLpTsBoxAdjuster._sll;
		_nSearchLegs = discLpTsBoxAdjuster._nSearchLegs;
		final double tsAcrossNmi = _nSearchLegs * tsNmi;
		final double tsAlongNmi = tsNmi + _sllNmi;
		_eplNmi = _nSearchLegs * tsAlongNmi;

		/** Fill in "TheirStyle" values: */
		_ps = tsAlongNmi >= tsAcrossNmi;
		if (_ps) {
			_orntn = _firstLegHdg;
		} else {
			_orntn = LatLng3
					.getInRange0_360(_firstLegHdg + (_firstTurnRight ? 90d : -90d));
		}
		_lenNmi = Math.max(tsAlongNmi, tsAcrossNmi);
		_widNmi = Math.min(tsAlongNmi, tsAcrossNmi);

		if (cppToJavaTracer != null && cppToJavaTracer.isActive()) {
			final double ttlLegLengthPlusTs = _nSearchLegs * tsAlongNmi;
			final double rawSearchKtsToUse = (ttlLegLengthPlusTs / searchHrs) /
					PatternUtilStatics._EffectiveSpeedReduction;
			final String f = "\nAfter perfecting and rounding:\n\t" +
					"eplNmi[%f] rawSpeedToUse[%f] orntn[%f] len[%f] wid[%f] %s";
			final String s = String.format(f, //
					ttlLegLengthPlusTs, rawSearchKtsToUse, _orntn, _lenNmi, _widNmi,
					_ps ? "PS" : "CS");
			cppToJavaTracer.writeTrace(s);
		}

		/** Generate the paths and polygons. */
		_sphericalTimedSegs =
				new SphericalTimedSegs(baseSecs, searchDurationSecs, //
						_center, _firstLegHdg, //
						specAlongNmi, specAcrossNmi, //
						tsAlongNmi, tsAcrossNmi, //
						_sllNmi, _sgndTsNmi, //
						excBufferNmi);
	}

	public String getMyStyleString() {
		final MyStyle myStyle =
				new MyStyle(PatternKind.PSCS, _baseSecs, _searchDurationSecs,
						_center, _orntn, _firstTurnRight, _lenNmi, _widNmi, _ps);
		final double searchHrs = _searchDurationSecs / 3600d;
		final double rawSearchKtsToUse =
				(_eplNmi / searchHrs) / PatternUtilStatics._EffectiveSpeedReduction;
		final String s =
				myStyle.getTheirStyleString(rawSearchKtsToUse, _minTsNmi);
		return s;
	}
}
