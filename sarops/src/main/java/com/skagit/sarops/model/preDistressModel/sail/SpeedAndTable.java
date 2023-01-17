package com.skagit.sarops.model.preDistressModel.sail;

import com.skagit.util.HdgKts;

public class SpeedAndTable implements Comparable<SpeedAndTable> {
	final public double _windKts;
	final public HdgKtsPlus[] _table;

	/**
	 * Indexes: 1st one is Port/Starboard, 2nd is section, 3rd is vector within
	 * section.
	 */
	SpeedAndTable(final double wndKts, final HdgKtsPlus[] table) {
		_windKts = wndKts;
		_table = table;
	}

	@Override
	public int compareTo(final SpeedAndTable speedAndTable) {
		if (_windKts < speedAndTable._windKts) {
			return -1;
		}
		if (_windKts > speedAndTable._windKts) {
			return 1;
		}
		return 0;
	}

	public String getString() {
		String s = String.format("WndKts[%.3f]", _windKts);
		final int n = _table.length;
		for (int k = 0; k < n; ++k) {
			final HdgKts hdgKts = _table[k];
			s += String.format("\n\t%s", hdgKts.getString());
		}
		return s;
	}

	@Override
	public String toString() {
		return getString();
	}
}