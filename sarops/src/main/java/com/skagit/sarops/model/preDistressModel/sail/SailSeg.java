package com.skagit.sarops.model.preDistressModel.sail;

import com.skagit.sarops.tracker.StateVectorType;
import com.skagit.util.HdgKts;
import com.skagit.util.TimeUtilities;
import com.skagit.util.greatCircleArc.GreatCircleArc;

public class SailSeg {
	final public GreatCircleArc _gca;
	final public int _edgeNumber;
	public StateVectorType _svt0;
	public StateVectorType _svt1;
	final public long _refSecs0;
	final public long _refSecs1;
	final public HdgKts _upWind;
	final public HdgKts _dnCurrent;

	public SailSeg(final GreatCircleArc gca, final int edgeNumber,
			final long refSecs0, final long refSecs1, final HdgKts upWind,
			final HdgKts dnCurrent) {
		_gca = gca;
		_edgeNumber = edgeNumber;
		_refSecs0 = refSecs0;
		/** Every sailSeg has a duration of at least 1. */
		_refSecs1 = Math.max(refSecs0 + 1, refSecs1);
		_svt0 = _svt1 = null;
		_upWind = upWind;
		_dnCurrent = dnCurrent;
	}

	public long getDurationSecs() {
		return _refSecs1 - _refSecs0;
	}

	public double getKts() {
		final double kts = _gca.getTtlNmi() / (getDurationSecs() / 3600d);
		return kts;
	}

	public double getApparentWindKts() {
		return _upWind.add(_dnCurrent).getKts();
	}

	public final String getSvtName0() {
		return (_svt0 == null ? StateVectorType.UNDEFINED :
				_svt0)._shortString;
	}

	public final String getColorName0() {
		return (_svt0 == null ? StateVectorType.UNDEFINED :
				_svt1).getColorName();
	}

	public final String getSvtName1() {
		return (_svt1 == null ? StateVectorType.UNDEFINED :
				_svt1)._shortString;
	}

	public final String getColorName1() {
		return (_svt1 == null ? StateVectorType.UNDEFINED :
				_svt1).getColorName();
	}

	public String getString() {
		String s = String.format("%s→%s:%s", getSvtName0(), getSvtName1(),
				_gca.getString());
		final long durationSecs;
		if (_refSecs1 > _refSecs0) {
			durationSecs = _refSecs1 - _refSecs0;
		} else {
			durationSecs = 0;
		}

		final String endString =
				TimeUtilities.formatTime(_refSecs1, /* includeSecs= */false);
		final double hrs = durationSecs / 3600d;
		final double kts = _gca.getTtlNmi() / hrs;
		final double hdg = _gca.getRawInitialHdg();
		s += String.format(":\n\t[Kts,Hdg,Hrs]=[%.3f,%.3f,%.3f,%s]", //
				kts, hdg, hrs, endString);
		return s;
	}

	@Override
	public String toString() {
		return "\n" + getString();
	}

}
