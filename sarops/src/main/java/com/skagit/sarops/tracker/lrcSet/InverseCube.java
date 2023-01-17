package com.skagit.sarops.tracker.lrcSet;

import java.util.TreeSet;

import org.w3c.dom.Element;

import com.skagit.sarops.model.io.ModelReader;
import com.skagit.sarops.model.io.ModelReader.ReaderException;
import com.skagit.sarops.model.io.ModelReader.StringPlus;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.CpaCalculator;
import com.skagit.util.Constants;
import com.skagit.util.LsFormatter;

/**
 * <pre>
 * This is Koopman's inverse cube, from p. 123 of Elements,...
 * We assume that it is LtRt, symmetric, and has minRange=0d.
 * </pre>
 */
public class InverseCube extends LateralRangeCurve {
	final private double _minNmiForUncertainPd;

	public InverseCube(final SimCaseManager.SimCase simCase,
			final Element element, final TreeSet<StringPlus> stringPluses) {
		super(simCase, SubType.INVERSE_CUBE, element, stringPluses);
		_sweepWidthIn = Double.NaN;
		try {
			_sweepWidthIn =
					ModelReader.getDouble(simCase, element, _SweepWidthAttributeName,
							_SweepWidthUnits, Double.NaN, stringPluses);
		} catch (final ReaderException e) {
		}

		if (!(_sweepWidthIn > 0d)) {
			_sweepWidth = _minNmiForUncertainPd = Double.NaN;
			return;
		}
		final double maxRangeForDetect = _sweepWidthIn /
				Math.sqrt(4d * Math.PI * Math.log(1d / (1d - _MinProbDetect)));
		_minNmiForUncertainPd = _sweepWidthIn /
				Math.sqrt(4d * Math.PI * Math.log(1d / (1d - _MaxProbDetect)));
		setMaxRangeForDetect(maxRangeForDetect);
		_sweepWidth = computeSweepWidth();
	}

	@Override
	protected int littleCompare(final LateralRangeCurve lrc) {
		final InverseCube inverseCube = (InverseCube) lrc;
		return _sweepWidthIn < inverseCube._sweepWidth ? -1 :
				(_sweepWidthIn > inverseCube._sweepWidth ? 1 : 0);
	}

	private double formula(final double cpaNmi) {
		if (0d < cpaNmi && cpaNmi <= _minNmiForUncertainPd) {
			return _MaxProbDetect;
		}
		final double ratio = _sweepWidthIn / cpaNmi;
		final double exponent = (ratio * ratio) / Constants._4Pi;
		final double returnValue = 1d - Math.exp(-exponent);
		return returnValue;
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
		element.setAttribute(_SweepWidthAttributeName,
				LsFormatter.StandardFormat(_sweepWidthIn) + " " + _SweepWidthUnits);
	}

}
