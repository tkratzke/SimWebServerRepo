package com.skagit.sarops.model;

import org.w3c.dom.Element;

import com.skagit.util.LsFormatter;

public class SotWithDbl implements Comparable<SotWithDbl> {
	final private SearchObjectType _sot;
	final private double _origDbl;
	private double _workingDbl;

	public SotWithDbl(final SearchObjectType sot, final double dbl) {
		_sot = sot;
		_workingDbl = _origDbl = dbl;
	}

	public void write(final LsFormatter formatter, final Element root) {
		makeElement(formatter, root);
	}

	protected Element makeElement(final LsFormatter formatter, final Element root) {
		final Element element;
		final String dblString = _origDbl * 100d + "%";
		if (_sot.isDebris()) {
			element = formatter.newChild(root, "DEBRIS_OBJECT_TYPE");
			element.setAttribute("confidence", dblString);
		} else {
			element = formatter.newChild(root, "SCEN_OBJECT_TYPE");
			element.setAttribute("weight", dblString);
		}
		element.setAttribute("id", LsFormatter.StandardFormat(_sot.getId()));
		return element;
	}

	public boolean deepEquals(final SotWithDbl other) {
		if ((other == null) || (_origDbl != other._origDbl) || (_sot.getId() != other._sot.getId())) {
			return false;
		}
		return true;
	}

	public static class OriginatingSotWithWt extends SotWithDbl {
		public OriginatingSotWithWt(final SearchObjectType sot) {
			super(sot, 1f);
		}

		@Override
		public boolean deepEquals(final SotWithDbl other) {
			if (!(other instanceof OriginatingSotWithWt)) {
				return false;
			}
			final OriginatingSotWithWt compared = (OriginatingSotWithWt) other;
			return super.deepEquals(compared);
		}

		@Override
		protected Element makeElement(final LsFormatter formatter, final Element root) {
			final Element element = formatter.newChild(root, "ORIGINATING_CRAFT");
			element.setAttribute("id", LsFormatter.StandardFormat(getSot().getId()));
			return element;
		}
	}

	public SearchObjectType getSot() {
		return _sot;
	}

	public void setWorkingDbl(final double workingDbl) {
		_workingDbl = workingDbl;
	}

	public double getWorkingDbl() {
		return _workingDbl;
	}

	@Override
	public int compareTo(final SotWithDbl other) {
		final int compareValue = _sot.compareTo(other._sot);
		if (compareValue != 0) {
			return compareValue;
		}
		final boolean isFinite = Double.isFinite(_workingDbl);
		if (isFinite != Double.isFinite(other._workingDbl)) {
			return isFinite ? 1 : -1;
		}
		if (!isFinite) {
			return 0;
		}
		return _workingDbl < other._workingDbl ? -1 : (_workingDbl > other._workingDbl ? 1 : 0);
	}
}
