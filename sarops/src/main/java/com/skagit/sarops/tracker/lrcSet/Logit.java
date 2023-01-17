package com.skagit.sarops.tracker.lrcSet;

import java.util.TreeSet;

import org.w3c.dom.Element;

import com.skagit.sarops.model.io.ModelReader;
import com.skagit.sarops.model.io.ModelReader.ReaderException;
import com.skagit.sarops.model.io.ModelReader.StringPlus;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.CpaCalculator;
import com.skagit.sarops.util.SimUtils;
import com.skagit.util.LsFormatter;
import com.skagit.util.MathX;
import com.skagit.util.NumericalRoutines;

/** LogOdds Detection function from from p. 123, 124 of Elements,... */
public class Logit extends LateralRangeCurve {
	final public static String _Ap0AttributeName = "ap0";
	final public static String _A1AttributeName = "a1";
	final public static String _A2AttributeName = "a2";
	final public static String _EssHalfAngleUnits = "degs";
	final public static String _DisplayScaleAttributeName = "displayScale";
	final public static String _DisplayScaleUnits = _RangeUnits;
	final private static double _HalfWorldNmi = Math.PI / MathX._NmiToR;

	final double _ap0, _a1, _a2;
	final double _displayScale;
	final private static double _MaxExponent = Math.log(1000000d);

	public Logit(final SimCaseManager.SimCase simCase, final Element element,
			final TreeSet<StringPlus> stringPluses) {
		super(simCase, SubType.LOGIT, element, stringPluses);

		double ap0 = Double.NaN;
		try {
			ap0 = ModelReader.getDouble(simCase, element, _Ap0AttributeName, "", //
					Double.NEGATIVE_INFINITY, stringPluses);
		} catch (final ReaderException e) {
		}
		double a1 = Double.NaN;
		try {
			a1 = ModelReader.getDouble(simCase, element, _A1AttributeName, "", //
					Double.NEGATIVE_INFINITY, stringPluses);
		} catch (final ReaderException e) {
		}
		double a2 = 0d;
		try {
			a2 = ModelReader.getDouble(simCase, element, _A2AttributeName, "", //
					0d, stringPluses);
		} catch (final ReaderException e) {
		}
		double displayScale = Double.POSITIVE_INFINITY;
		try {
			displayScale = ModelReader.getDouble(simCase, element,
					_DisplayScaleAttributeName, _DisplayScaleUnits, //
					Double.POSITIVE_INFINITY, stringPluses);
			displayScale = (0d < displayScale && displayScale < _HalfWorldNmi) ?
					displayScale : Double.POSITIVE_INFINITY;
		} catch (final ReaderException e) {
		}
		_displayScale = displayScale;
		if (!Double.isFinite(ap0) || !Double.isFinite(a1) ||
				!Double.isFinite(a2)) {
			_ap0 = _a1 = _a2 = _sweepWidth = Double.NaN;
			return;
		}

		if (Double.isFinite(_displayScale)) {
			if (isLtRt()) {
				setLtSide(getLtMinRange(), Math.min(getLtMaxRange(), _displayScale),
						getLtMinLkAngl(), getLtMaxLkAngl());
				setRtSide(getRtMinRange(), Math.min(getRtMaxRange(), _displayScale),
						getRtMinLkAngl(), getRtMaxLkAngl());
				setUpSide(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
				setDnSide(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
			} else {
				setUpSide(getUpMinRange(), Math.min(getUpMaxRange(), _displayScale),
						getUpMinLkAngl(), getUpMaxLkAngl());
				setDnSide(getDnMinRange(), Math.min(getDnMaxRange(), _displayScale),
						getDnMinLkAngl(), getDnMaxLkAngl());
				setLtSide(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
				setUpSide(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
			}
		}
		if (ap0 > _MaxExponent) {
			/** We don't want a curve that is 1, even at cpa = 0. */
			_ap0 = _a1 = _a2 = _sweepWidth = Double.NaN;
			return;
		}

		/** Read in _sweepWidthIn; if it's 0, it's used as a flag. */
		_sweepWidthIn = Double.NaN;
		try {
			_sweepWidthIn =
					ModelReader.getDouble(simCase, element, _SweepWidthAttributeName,
							_SweepWidthUnits, Double.NaN, stringPluses);
		} catch (final ReaderException e) {
		}

		/**
		 * Adjust the Filter. Compute maxDetectRange, which is based on
		 * MinDetectProb and displayScale.
		 */
		final double maxRangeForDetect;
		if (a2 != 0d) {
			final double a = a2;
			final double b = a1;
			final double c = ap0 + Math.log(1d / _MinProbDetect - 1d);
			final double[] roots = new double[] { Double.NaN, Double.NaN };
			NumericalRoutines.quadratic(a, b, c, roots);
			final double r0 = roots[0];
			final double r1 = roots[1];
			if (r0 > 0d || r1 > 0d) {
				maxRangeForDetect = Math.max(r0, r1);
			} else {
				maxRangeForDetect = Double.POSITIVE_INFINITY;
			}
		} else if (a1 != 0d) {
			final double maxRangeForDetect1 =
					(-Math.log(1d / _MinProbDetect - 1d) - ap0) / a1;
			if (maxRangeForDetect1 > 0d) {
				maxRangeForDetect = maxRangeForDetect1;
			} else {
				maxRangeForDetect = Double.POSITIVE_INFINITY;
			}
		} else {
			maxRangeForDetect = Double.POSITIVE_INFINITY;
		}
		setMaxRangeForDetect(maxRangeForDetect);

		final double currentMaxRange;
		if (isLtRt()) {
			currentMaxRange = Math.max(getLtMaxRange(), getRtMaxRange());
		} else {
			currentMaxRange = Math.max(getUpMaxRange(), getDnMaxRange());
		}
		final double rawMaxRange = Math.min(currentMaxRange, displayScale);
		final double trueMaxRange = Math.min(rawMaxRange, maxRangeForDetect);
		if (Double.isInfinite(trueMaxRange)) {
			if (a2 > 0d || (a2 == 0d && a1 >= 0d)) {
				_ap0 = _a1 = _a2 = _sweepWidth = Double.NaN;
				return;
			}
		}
		_ap0 = ap0;
		_a1 = a1;
		_a2 = a2;

		if (isLtRt()) {
			setLtSide(getLtMinRange(), Math.min(getLtMaxRange(), rawMaxRange),
					getLtMinLkAngl(), getLtMaxLkAngl());
			setRtSide(getRtMinRange(), Math.min(getRtMaxRange(), rawMaxRange),
					getRtMinLkAngl(), getRtMaxLkAngl());
			setUpSide(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
			setDnSide(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
		} else {
			setUpSide(getUpMinRange(), Math.min(getUpMaxRange(), rawMaxRange),
					getUpMinLkAngl(), getUpMaxLkAngl());
			setDnSide(getDnMinRange(), Math.min(getDnMaxRange(), rawMaxRange),
					getDnMinLkAngl(), getDnMaxLkAngl());
			setLtSide(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
			setLtSide(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
		}
		_sweepWidth = computeSweepWidth();
	}

	/** Used only for the EssCurveBuilder. */
	public Logit(final double ap0Ess, final double a1Ess, final double a2Ess,
			final double sweepWidthIn, final double displayScale,
			final double minRangeEss, final double absHalfAngleEss) {
		super(SubType.LOGIT, /* distinctDetectionThresholdMins= */5d);
		_ap0 = ap0Ess;
		_a1 = a1Ess;
		_a2 = a2Ess;
		_sweepWidthIn = sweepWidthIn;
		_displayScale = displayScale;
		setUpSide(0d, 0d, 0d, 0d);
		setDnSide(minRangeEss, displayScale, 90d - absHalfAngleEss,
				90d + absHalfAngleEss);
		setLtSide(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
		setRtSide(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
		_sweepWidth = computeSweepWidth();
	}

	@Override
	protected int littleCompare(final LateralRangeCurve lrc) {
		final Logit logOdds = (Logit) lrc;
		if (_ap0 != logOdds._ap0) {
			return _ap0 < logOdds._ap0 ? -1 : 1;
		}
		if (_a1 != logOdds._a1) {
			return _a1 < logOdds._a1 ? -1 : 1;
		}
		final int compareValue =
				_a2 < logOdds._a2 ? -1 : (_a2 > logOdds._a2 ? 1 : 0);
		return compareValue;
	}

	private double formula(final double cpaNmi) {
		final double exponent = -(_ap0 + _a1 * cpaNmi + _a2 * cpaNmi * cpaNmi);
		if (exponent < _MaxExponent) {
			final double value = 1d / (1d + Math.exp(exponent));
			return value;
		}
		return 0d;
	}

	@Override
	protected double littleLtCpaToPod(final double cpaNmi) {
		return formula(cpaNmi);
	}

	@Override
	protected double littleRtCpaToPod(final double cpaNmi) {
		return formula(cpaNmi);
	}

	@Override
	protected double littleUpCpaToPod(final double cpaNmi) {
		return formula(cpaNmi);
	}

	@Override
	protected double littleDnCpaToPod(final double cpaNmi) {
		return formula(cpaNmi);
	}

	@Override
	protected double littleResultToPod(final CpaCalculator.Result result) {
		final double cpaNmi = result.getCpaNmi();
		return formula(cpaNmi);
	}

	@Override
	protected void littleAddAttributes(final Element element) {
		element.setAttribute(_Ap0AttributeName,
				LsFormatter.StandardFormat(_ap0));
		element.setAttribute(_A1AttributeName, LsFormatter.StandardFormat(_a1));
		if (!Double.isNaN(_a2) && _a2 != 0d) {
			element.setAttribute(_A2AttributeName,
					LsFormatter.StandardFormat(_a2));
		}
		if (0d <= _displayScale && _displayScale < _HalfWorldNmi) {
			element.setAttribute("displayScale",
					LsFormatter.StandardFormat(_displayScale) + " " +
							_SweepWidthUnits);
		}
		element.setAttribute("computedSweepWidth",
				LsFormatter.StandardFormat(_sweepWidth) + " " + _SweepWidthUnits);
		if (_sweepWidthIn >= 0d) {
			element.setAttribute(_SweepWidthAttributeName,
					LsFormatter.StandardFormat(_sweepWidthIn) + " " +
							_SweepWidthUnits);
		}
	}

	@Override
	protected double computeSweepWidth() {
		/**
		 * <pre>
		 * If _a2 = 0, we can get a closed form for the definite integral:
		 *   f(x) = 1/(1 + exp(-L(x)))
		 *   Multiply top and bottom by exp(L(x))
		 *   Let u = 1+exp(L(x)), du = exp(L(x))*L'(x), etc..
		 * Since we're assuming there's no x^2 term in L(x),
		 * du has no extra x term, and we have a closed form
		 * anti-derivative.  But we're too lazy to work out all of the
		 * up/dn or lt/rt details here.  Maybe some day.
		 * </pre>
		 */
		final double sweepWidth = super.computeSweepWidth();
		return sweepWidth;
	}

	public double getAp0() {
		return _ap0;
	}

	public double getA1() {
		return _a1;
	}

	public double getA2() {
		return _a2;
	}

	@Override
	protected boolean littleIsBlind() {
		final double maxRange = getMaxRange();
		final double maxPd = SimUtils.getLogOddsMaxPd(_ap0, _a1, _a2, maxRange);
		return maxPd == 0d;
	}

	public double getInputSweepWidth() {
		return _sweepWidthIn;
	}

}
