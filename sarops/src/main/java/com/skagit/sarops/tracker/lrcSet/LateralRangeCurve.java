package com.skagit.sarops.tracker.lrcSet;

import java.util.TreeSet;

import org.w3c.dom.Element;

import com.skagit.sarops.model.Sortie;
import com.skagit.sarops.model.io.ModelReader;
import com.skagit.sarops.model.io.ModelReader.StringPlus;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.CpaCalculator;
import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.sarops.util.SimUtils;
import com.skagit.sarops.util.patternUtils.LegInfo;
import com.skagit.util.Constants;
import com.skagit.util.Integrator;
import com.skagit.util.LsFormatter;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.TangentCylinder;

public abstract class LateralRangeCurve extends LrcCore
		implements Comparable<LateralRangeCurve> {
	final public static String _SweepWidthAttributeName = "sw";
	final public static String _SweepWidthUnits = _RangeUnits;

	public static final double _RelativeErrorThreshold = 1.0e-5;
	public static final double _MinimumSweepWidth = 1d / Constants._NmiToM;
	protected static final double _MinProbDetect = SimUtils._MinProbDetect;
	protected static final double _MaxProbDetect = SimUtils._MaxProbDetect;
	final public static LateralRangeCurve _Blind = new MBeta(true);
	final public static LateralRangeCurve _NearSighted = new MBeta(false);

	protected double _sweepWidthIn;
	protected double _sweepWidth;

	protected LateralRangeCurve(final SubType subType,
			final double distinctDetectionThresholdMins) {
		super(subType, distinctDetectionThresholdMins);
		_sweepWidth = Double.NaN;
	}

	protected LateralRangeCurve(final SimCaseManager.SimCase simCase,
			final SubType subType, final Element element,
			final TreeSet<StringPlus> stringPluses) {
		super(simCase, subType, element, stringPluses);
		_sweepWidth = Double.NaN;
	}

	@Override
	public final int compareTo(final LateralRangeCurve lrc) {
		if (this == lrc) {
			return 0;
		}
		if (lrc == null) {
			return 1;
		}
		if ((this == _Blind) != (lrc == _Blind)) {
			return this == _Blind ? -1 : 1;
		}
		if (this == _Blind) {
			return 0;
		}
		if ((this == _NearSighted) != (lrc == _NearSighted)) {
			return this == _NearSighted ? -1 : 1;
		}
		if (this == _NearSighted) {
			return 0;
		}
		final String myClassName = getClass().getName();
		final String hisClassName = lrc.getClass().getName();
		int compareValue = myClassName.compareTo(hisClassName);
		if (compareValue != 0) {
			return compareValue;
		}
		compareValue = super.coreCompare(lrc);
		if (compareValue != 0) {
			return compareValue;
		}
		return littleCompare(lrc);
	}

	abstract protected int littleCompare(final LateralRangeCurve lrc);

	final public double ltCpaToPod(final double cpaNmi) {
		assert isLtRt() : "Should not call ltCpaToPod if not LtRt.";
		if (!thisRangeIsOk(cpaNmi, /* lt= */true, /* rt= */false,
				/* up= */false, /* dn= */false)) {
			return 0d;
		}
		return littleLtCpaToPod(cpaNmi);
	}

	final public double rtCpaToPod(final double cpaNmi) {
		assert isLtRt() : "Should not call rtCpaToPod if not LtRt.";
		if (!thisRangeIsOk(cpaNmi, /* lt= */false, /* rt= */true,
				/* up= */false, /* dn= */false)) {
			return 0d;
		}
		return littleRtCpaToPod(cpaNmi);
	}

	final public double upCpaToPod(final double cpaNmi) {
		assert isUpDn() : "Should not call upCpaToPod if not UpDn.";
		if (!thisRangeIsOk(cpaNmi, /* lt= */false, /* rt= */false,
				/* up= */true, /* dn= */false)) {
			return 0d;
		}
		return littleUpCpaToPod(cpaNmi);
	}

	final public double dnCpaToPod(final double cpaNmi) {
		assert isUpDn() : "Should not call dnCpaToPod if not UpDn.";
		if (!thisRangeIsOk(cpaNmi, /* lt= */false, /* rt= */false,
				/* up= */false, /* dn= */true)) {
			return 0d;
		}
		return littleDnCpaToPod(cpaNmi);
	}

	abstract protected double littleLtCpaToPod(final double cpaNmi);

	abstract protected double littleRtCpaToPod(final double cpaNmi);

	abstract protected double littleUpCpaToPod(final double cpaNmi);

	abstract protected double littleDnCpaToPod(final double cpaNmi);

	abstract protected double littleResultToPod(
			final CpaCalculator.Result result);

	final public double resultToPod(final CpaCalculator.Result result) {
		if (!thisResultIsOk(result)) {
			return 0d;
		}
		return littleResultToPod(result);
	}

	public double getSweepWidthIn() {
		return _sweepWidthIn;
	}

	public final double getSweepWidth() {
		return _sweepWidth;
	}

	@Override
	public String getString() {
		String s = super.getString();
		s += String.format("\n%.3f", _sweepWidth);
		return s;
	}

	@Override
	public String toString() {
		return getString();
	}

	public final boolean isBlind() {
		if (isLtRt()) {
			final boolean okLt = getLtMaxRange() > getLtMinRange() &&
					getLtMaxLkAngl() > getLtMinLkAngl();
			final boolean okRt = getRtMaxRange() > getRtMinRange() &&
					getRtMaxLkAngl() > getRtMinLkAngl();
			if (!okLt && !okRt) {
				return true;
			}
		}
		if (isUpDn()) {
			final boolean okUp = getUpMaxRange() > getUpMinRange() &&
					getUpMaxLkAngl() > getUpMinLkAngl();
			final boolean okDn = getDnMaxRange() > getDnMinRange() &&
					getDnMaxLkAngl() > getDnMinLkAngl();
			if (!okUp && !okDn) {
				return true;
			}
		}
		if (!(getSweepWidth() > 0d)) {
			return true;
		}
		return littleIsBlind();
	}

	protected boolean littleIsBlind() {
		return false;
	}

	public final boolean isNearSighted() {
		if (isBlind()) {
			return true;
		}
		return !(getSweepWidth() > _MinimumSweepWidth);
	}

	public double getDistinctDetectionThresholdMins() {
		return _distinctDetectionThresholdMins;
	}

	/**
	 * Returns a Result, but only fills in the _tempPFailValue of that result.
	 */
	public CpaCalculator.Result getCpaCalculatorResult(
			final CpaCalculator cpaCalculator, final long refSecs0,
			final long refSecs1, final LatLng3 ptclLatLng0,
			final LatLng3 ptclLatLng1, final ParticleIndexes prtclIndxs) {
		final Sortie.Leg leg = cpaCalculator.getLeg();
		final LegInfo.LegType legType = leg.getLegType();
		final TangentCylinder tc = leg.getTangentCylinder();
		final TangentCylinder.FlatLatLng flatLegLatLng0 =
				cpaCalculator.getLegPosition(refSecs0);
		final TangentCylinder.FlatLatLng flatLegLatLng1 =
				cpaCalculator.getLegPosition(refSecs1);
		final TangentCylinder.FlatLatLng flatPtclLatLng0 =
				tc.convertToMyFlatLatLng(ptclLatLng0);
		final TangentCylinder.FlatLatLng flatPtclLatLng1 =
				tc.convertToMyFlatLatLng(ptclLatLng1);

		final double[][] tPairs = getTPairs(legType, flatLegLatLng0,
				flatLegLatLng1, flatPtclLatLng0, flatPtclLatLng1);
		final int nTPairs = tPairs.length;
		if (nTPairs == 0) {
			return null;
		}
		final double sruStartEastOffset = flatLegLatLng0.getEastOffset();
		final double sruStartNorthOffset = flatLegLatLng0.getNorthOffset();
		final double sruStopEastOffset = flatLegLatLng1.getEastOffset();
		final double sruStopNorthOffset = flatLegLatLng1.getNorthOffset();
		final double ptclStartEastOffset = flatPtclLatLng0.getEastOffset();
		final double ptclStartNorthOffset = flatPtclLatLng0.getNorthOffset();
		final double ptclStopEastOffset = flatPtclLatLng1.getEastOffset();
		final double ptclStopNorthOffset = flatPtclLatLng1.getNorthOffset();
		CpaCalculator.Result bestResult = null;
		final long intrvlLength = refSecs1 - refSecs0;
		for (int k = 0; k < nTPairs; ++k) {
			final double[] tPair = tPairs[k];
			final double tStart = tPair[0];
			final double tStop = tPair[1];
			final long tPairStartRefSecs =
					Math.round(refSecs0 + tPair[0] * intrvlLength);
			final long tPairStopRefSecs =
					Math.round(refSecs0 + tPair[1] * intrvlLength);
			if (tPairStopRefSecs == tPairStartRefSecs) {
				continue;
			}
			/**
			 * Get the offsets of the Sru at the start and stop of this interval.
			 */
			final double a00 =
					(1d - tStart) * sruStartEastOffset + tStart * sruStopEastOffset;
			final double a01 =
					(1d - tStart) * sruStartNorthOffset + tStart * sruStopNorthOffset;
			final double a10 =
					(1d - tStop) * sruStartEastOffset + tStop * sruStopEastOffset;
			final double a11 =
					(1d - tStop) * sruStartNorthOffset + tStop * sruStopNorthOffset;
			/**
			 * Get the offsets of the Ptcl at the start and stop of this interval.
			 */
			final double b00 =
					(1d - tStart) * ptclStartEastOffset + tStart * ptclStopEastOffset;
			final double b01 = (1d - tStart) * ptclStartNorthOffset +
					tStart * ptclStopNorthOffset;
			final TangentCylinder.FlatLatLng thisPtclStart =
					tc.new FlatLatLng(b00, b01);
			final double b10 =
					(1d - tStop) * ptclStartEastOffset + tStop * ptclStopEastOffset;
			final double b11 =
					(1d - tStop) * ptclStartNorthOffset + tStop * ptclStopNorthOffset;
			final TangentCylinder.FlatLatLng thisPtclStop =
					tc.new FlatLatLng(b10, b11);
			/** NumericalRoutines finds p in [0,1]. */
			final double p = NumericalRoutines.getProportionOfCpa(a00, a01, a10,
					a11, b00, b01, b10, b11);
			final long cpaRefSecs = Math.round(
					tPairStartRefSecs + p * (tPairStopRefSecs - tPairStartRefSecs));
			final CpaCalculator.Result thisResult =
					cpaCalculator.new Result(tPairStartRefSecs, tPairStopRefSecs,
							cpaRefSecs, thisPtclStart, thisPtclStop, prtclIndxs);
			/**
			 * We set only the tempPFail; it's not really a pFail for the entire
			 * leg.
			 */
			final double tempPFailValue = 1d - resultToPod(thisResult);
			if (bestResult == null ||
					tempPFailValue < bestResult.getTempPFailValue()) {
				bestResult = thisResult;
				bestResult.setTempPFailValue(tempPFailValue);
			}
		}
		return bestResult;
	}

	protected double computeSweepWidth() {
		final boolean isLtRt = isLtRt();
		final double ltOrUpIntegral =
				computeHalfIntegral(isLtRt, /* ltOrUp= */true);
		final double rtOrDnIntegral =
				computeHalfIntegral(isLtRt, /* ltOrUp= */false);
		final double integral = ltOrUpIntegral + rtOrDnIntegral;
		return integral;
	}

	protected double computeHalfIntegral(final boolean isLtRt,
			final boolean ltOrUp) {
		final double minRange;
		final double maxRange;
		final double minLkAngl;
		final double maxLkAngl;
		if (isLtRt) {
			minRange = ltOrUp ? getLtMinRange() : getRtMinRange();
			maxRange = ltOrUp ? getLtMaxRange() : getRtMaxRange();
			minLkAngl = ltOrUp ? getLtMinLkAngl() : getRtMinLkAngl();
			maxLkAngl = ltOrUp ? getLtMaxLkAngl() : getRtMaxLkAngl();
		} else {
			minRange = ltOrUp ? getUpMinRange() : getDnMinRange();
			maxRange = ltOrUp ? getUpMaxRange() : getDnMaxRange();
			minLkAngl = ltOrUp ? getUpMinLkAngl() : getDnMinLkAngl();
			maxLkAngl = ltOrUp ? getUpMaxLkAngl() : getDnMaxLkAngl();
		}
		if (!(maxRange > minRange) || !(maxLkAngl > minLkAngl)) {
			return 0d;
		}
		final Integrator.IntegrableFunction f =
				new Integrator.IntegrableFunction() {

					@Override
					public double valueAt(final double x) {
						if (isLtRt) {
							return ltOrUp ? ltCpaToPod(x) : rtCpaToPod(x);
						}
						return ltOrUp ? upCpaToPod(x) : dnCpaToPod(x);
					}
				};
		final double halfIntegral =
				Integrator.simpson(f, minRange, maxRange, _RelativeErrorThreshold);
		return halfIntegral;
	}

	final public void addAttributes(final Element element) {
		final String distinctDetectionThresholdAttributeValue =
				String.format("%s %s",
						LsFormatter.StandardFormat(_distinctDetectionThresholdMins),
						ModelReader._DistinctDetectionThresholdAttUnits);
		element.setAttribute(ModelReader._DistinctDetectionThresholdAtt,
				distinctDetectionThresholdAttributeValue);
		addLrcCoreAttributes(element);
		littleAddAttributes(element);
	}

	abstract protected void littleAddAttributes(final Element element);

}
