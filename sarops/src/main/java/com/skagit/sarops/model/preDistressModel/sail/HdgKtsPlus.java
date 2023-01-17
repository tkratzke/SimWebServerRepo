package com.skagit.sarops.model.preDistressModel.sail;

import com.skagit.sarops.tracker.StateVectorType;
import com.skagit.util.HdgKts;

public class HdgKtsPlus extends HdgKts {
	public final StateVectorType _svt;
	private int _ordinal;
	private double _ordinalD;

	HdgKtsPlus(final double d0, final double d1,
			final boolean doublesAreHdgKts, final StateVectorType svt) {
		super(d0, d1, doublesAreHdgKts);
		_ordinalD = _ordinal = Integer.MIN_VALUE;
		_svt = svt;
	}

	public int getOrdinal() {
		return _ordinal;
	}

	public double getOrdinalD() {
		return _ordinalD;
	}

	protected HdgKtsPlus(final HdgKtsPlus hdgKts) {
		super(hdgKts);
		_svt = hdgKts._svt;
		_ordinal = hdgKts._ordinal;
		_ordinalD = hdgKts._ordinalD;
	}

	@Override
	public HdgKtsPlus clone() {
		return new HdgKtsPlus(this);
	}

	public void setOrdinal(final int ordinal) {
		_ordinalD = _ordinal = ordinal;
	}

	public void setOrdinalD(final double ordinalD) {
		_ordinalD = ordinalD;
	}

	@Override
	public String getString() {
		final String s = super.getString();
		if (_ordinal == _ordinalD) {
			if (_svt != null) {
				return String.format("%s %s ord[%d]", s,
						_svt._shortString, _ordinal);
			}
			return String.format("%s ord[%d]", s, _ordinal);
		}
		if (_svt != null) {
			return String.format("%s %s ord[%.2f]", s,
					_svt._shortString, _ordinalD);
		}
		return String.format("%s ord[%.2f]", s, _ordinalD);
	}

	@Override
	public String toString() {
		return getString();
	}

}