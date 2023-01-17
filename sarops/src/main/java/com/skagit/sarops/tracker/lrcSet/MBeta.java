package com.skagit.sarops.tracker.lrcSet;

import java.util.TreeSet;

import org.w3c.dom.Element;

import com.skagit.sarops.model.io.ModelReader;
import com.skagit.sarops.model.io.ModelReader.ReaderException;
import com.skagit.sarops.model.io.ModelReader.StringPlus;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.CpaCalculator;
import com.skagit.sarops.util.patternUtils.LegInfo;
import com.skagit.util.LsFormatter;

public class MBeta extends LateralRangeCurve {
	final protected static String _LtPodAttributeName = "POD_Left";
	final protected static String _RtPodAttributeName = "POD_Right";
	final protected static String _UpPodAttributeName = "POD_Up";
	final protected static String _DnPodAttributeName = "POD_Down";

	final double _betaLtOrUp;
	final double _betaRtOrDn;

	public MBeta(final SimCaseManager.SimCase simCase, final Element element,
			final TreeSet<StringPlus> stringPluses) {
		super(simCase, SubType.MBETA, element, stringPluses);
		if (isLtRt()) {
			double podLt = Double.NaN;
			double podRt = Double.NaN;
			try {
				podLt =
						ModelReader.getDouble(simCase, element, _LtPodAttributeName, "", //
								Double.NaN, stringPluses);
				podRt =
						ModelReader.getDouble(simCase, element, _RtPodAttributeName, "", //
								Double.NaN, stringPluses);
			} catch (final ReaderException e) {
			}
			if (podLt >= 0d && podRt >= 0d && Math.max(podLt, podRt) > 0d) {
				setLtSide(getLtMinRange(), getLtMaxRange(), 0d, 180d);
				setRtSide(getRtMinRange(), getRtMaxRange(), 0d, 180d);
				setUpSide(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
				setDnSide(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
				_betaLtOrUp = podLt;
				_betaRtOrDn = podRt;
				_sweepWidth = computeSweepWidth();
				return;
			}
		}
		double podUp = Double.NaN;
		double podDn = Double.NaN;
		try {
			podUp =
					ModelReader.getDouble(simCase, element, _UpPodAttributeName, "", //
							Double.NaN, stringPluses);
			podDn =
					ModelReader.getDouble(simCase, element, _DnPodAttributeName, "", //
							Double.NaN, stringPluses);
		} catch (final ReaderException e) {
		}
		if (podUp >= 0d && podDn >= 0d && Math.max(podUp, podDn) > 0d) {
			setUpSide(getUpMinRange(), getUpMaxRange(), 0d, 180d);
			setDnSide(getDnMinRange(), getDnMaxRange(), 0d, 180d);
			setLtSide(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
			setRtSide(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
			_betaLtOrUp = podUp;
			_betaRtOrDn = podDn;
			_sweepWidth = computeSweepWidth();
			return;
		}
		_betaLtOrUp = _betaRtOrDn = _sweepWidth = Double.NaN;
	}

	@Override
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
			minLkAngl = ltOrUp ? getUpMinLkAngl() : getUpMinLkAngl();
			maxLkAngl = ltOrUp ? getUpMaxLkAngl() : getUpMaxLkAngl();
		}
		if (!(maxLkAngl > minLkAngl)) {
			return 0d;
		}
		final double deltaRange = maxRange - minRange;
		if (!(deltaRange > 0d)) {
			return 0;
		}
		final double beta = ltOrUp ? _betaLtOrUp : _betaRtOrDn;
		final double halfIntegral = deltaRange * beta;
		return halfIntegral;
	}

	/** Just for building blind and near-sighted. */
	protected MBeta(final boolean completelyBlind) {
		super(SubType.MBETA, /* distinctDetectionThresholdMinsMins= */60d);
		/** Set m = 1d and make it a LtRt. */
		setLtSide(0d, 1d, 0d, 180d);
		setRtSide(0d, 1d, 0d, 180d);
		setUpSide(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
		setDnSide(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
		if (completelyBlind) {
			_betaLtOrUp = _betaRtOrDn = _sweepWidth = 0d;
		} else {
			_betaLtOrUp = _betaRtOrDn = _MinimumSweepWidth / 2d;
			_sweepWidth = _MinimumSweepWidth;
		}
	}

	@Override
	protected int littleCompare(final LateralRangeCurve lrc) {
		final MBeta mBeta = (MBeta) lrc;
		if (_betaLtOrUp != mBeta._betaLtOrUp) {
			return _betaLtOrUp < mBeta._betaLtOrUp ? -1 : 1;
		}
		if (_betaRtOrDn != mBeta._betaRtOrDn) {
			return _betaRtOrDn < mBeta._betaRtOrDn ? -1 : 1;
		}
		return 0;
	}

	@Override
	protected boolean symmetric() {
		if (!super.symmetric()) {
			return false;
		}
		return _betaLtOrUp == _betaRtOrDn;
	}

	@Override
	protected double littleLtCpaToPod(final double cpaNmi) {
		return _betaLtOrUp;
	}

	@Override
	protected double littleRtCpaToPod(final double cpaNmi) {
		return _betaRtOrDn;
	}

	@Override
	protected double littleUpCpaToPod(final double cpaNmi) {
		return _betaLtOrUp;
	}

	@Override
	protected double littleDnCpaToPod(final double cpaNmi) {
		return _betaRtOrDn;
	}

	@Override
	protected double littleResultToPod(final CpaCalculator.Result result) {
		final double cpaNmi = result.getCpaNmi();
		if (symmetric()) {
			return isLtRt() ? ltCpaToPod(cpaNmi) : upCpaToPod(cpaNmi);
		}
		final double ccwRelativeHdg = result.getCcwTwistToPtrtclAtCpa();
		final boolean ptclOnLt = ccwRelativeHdg <= 180d;
		if (isLtRt()) {
			return ptclOnLt ? ltCpaToPod(cpaNmi) : rtCpaToPod(cpaNmi);
		}
		final LegInfo.LegType legType = result.getLeg().getLegType();
		final boolean creepIsToLeft =
				legType == LegInfo.LegType.CREEP_IS_TO_LEFT;
		final boolean ptclOnUp = ptclOnLt != creepIsToLeft;
		return ptclOnUp ? upCpaToPod(cpaNmi) : dnCpaToPod(cpaNmi);
	}

	@Override
	protected void littleAddAttributes(final Element element) {
		if (isLtRt()) {
			element.setAttribute(_LtPodAttributeName,
					LsFormatter.StandardFormat(_betaLtOrUp));
			element.setAttribute(_RtPodAttributeName,
					LsFormatter.StandardFormat(_betaRtOrDn));
		} else {
			element.setAttribute(_UpPodAttributeName,
					LsFormatter.StandardFormat(_betaLtOrUp));
			element.setAttribute(_DnPodAttributeName,
					LsFormatter.StandardFormat(_betaRtOrDn));
		}
	}

	public double getBetaLtOrUp() {
		return _betaLtOrUp;
	}

	public double getBetaRtOrDn() {
		return _betaRtOrDn;
	}

}
