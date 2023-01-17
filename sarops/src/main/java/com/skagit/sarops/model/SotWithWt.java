package com.skagit.sarops.model;

import org.w3c.dom.Element;

import com.skagit.util.LsFormatter;

public class SotWithWt {
	final private SearchObjectType _searchObjectType;
	final private double _origWeight;
	private double _workingWeight;

	public SotWithWt(final SearchObjectType searchObjectType,
			final double weight) {
		_searchObjectType = searchObjectType;
		_workingWeight = _origWeight = weight;
	}

	public void write(final LsFormatter formatter, final Element root,
			final Model model) {
		makeElement(formatter, root);
	}

	protected Element makeElement(final LsFormatter formatter,
			final Element root) {
		final Element element = formatter.newChild(root, "SCEN_OBJECT_TYPE");
		element.setAttribute("weight", _origWeight * 100d + "%");
		element.setAttribute("id",
				LsFormatter.StandardFormat(_searchObjectType.getId()));
		return element;
	}

	public boolean deepEquals(final SotWithWt other) {
		if ((other == null) || (_origWeight != other._origWeight) || (_searchObjectType.getId() != other._searchObjectType.getId())) {
			return false;
		}
		return true;
	}

	public static class OriginatingSotWithWt extends SotWithWt {
		public OriginatingSotWithWt(final SearchObjectType sot) {
			super(sot, 1f);
		}

		@Override
		public boolean deepEquals(final SotWithWt other) {
			if (!(other instanceof OriginatingSotWithWt)) {
				return false;
			}
			final OriginatingSotWithWt compared = (OriginatingSotWithWt) other;
			return super.deepEquals(compared);
		}

		@Override
		protected Element makeElement(final LsFormatter formatter,
				final Element root) {
			final Element element = formatter.newChild(root, "ORIGINATING_CRAFT");
			element.setAttribute("id",
					LsFormatter.StandardFormat(getSot().getId()));
			return element;
		}
	}

	public SearchObjectType getSot() {
		return _searchObjectType;
	}

	public void setWorkingWeight(final double workingWeight) {
		_workingWeight = workingWeight;
	}

	public double getWorkingWeight() {
		return _workingWeight;
	}
}
