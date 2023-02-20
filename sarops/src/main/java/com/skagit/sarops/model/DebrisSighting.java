package com.skagit.sarops.model;

import org.w3c.dom.Element;

import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.simCaseManager.SimCaseManager.SimCase;
import com.skagit.util.LsFormatter;
import com.skagit.util.TimeUtilities;
import com.skagit.util.cdf.area.Polygon;

public class DebrisSighting implements Comparable<DebrisSighting> {

	final private SimCaseManager.SimCase _simCase;
	final private int _id;
	final private String _name;
	final private Polygon _polygon;
	final private long _sightingRefSecs;
	final private SotWithDbl[] _dotWithCnfdncs;

	public DebrisSighting(final SimCase simCase, final int id, final String name, final Polygon polygon,
			final long sightingRefSecs, final SotWithDbl[] dotWithCnfdncs) {
		_simCase = simCase;
		_id = id;
		_name = name;
		_polygon = polygon;
		_sightingRefSecs = sightingRefSecs;
		_dotWithCnfdncs = dotWithCnfdncs;
	}

	public SimCaseManager.SimCase getSimCase() {
		return _simCase;
	}

	public int getId() {
		return _id;
	}

	public String getName() {
		return _name;
	}

	public SotWithDbl[] getDotWithCnfdncs() {
		return _dotWithCnfdncs;
	}

	public Element write(final LsFormatter formatter, final Element root, final Model model) {
		final Element element = formatter.newChild(root, "DEBRIS_SIGHTING");
		element.setAttribute("id", LsFormatter.StandardFormat(_id));
		element.setAttribute("name", _name);
		final Element timeElement = formatter.newChild(element, "TIME");
		timeElement.setAttribute("dtg", TimeUtilities.formatTime(_sightingRefSecs, false));
		_polygon.write(formatter, element, /* spreadAttributeName= */null);
		final int nDotWithCnfdncs = _dotWithCnfdncs.length;
		for (int k = 0; k < nDotWithCnfdncs; ++k) {
			final SotWithDbl dotWithCnfdnc = _dotWithCnfdncs[k];
			dotWithCnfdnc.write(formatter, element);
		}
		return element;
	}

	public Polygon getPolygon() {
		return _polygon;
	}

	public boolean deepEquals(final DebrisSighting other) {
		if (_id != other._id) {
			return false;
		}
		if (_name != null && _name.length() > 0 && other._name != null && other._name.length() > 0) {
			if (_name.compareTo(other._name) != 0) {
				return false;
			}
		}
		if (_sightingRefSecs != other._sightingRefSecs) {
			return false;
		}
		if (!_polygon.deepEquals(other._polygon)) {
			return false;
		}
		final int n = _dotWithCnfdncs.length;
		if (n != other._dotWithCnfdncs.length) {
			return false;
		}
		for (int k = 0; k < n; ++k) {
			final SotWithDbl dotWithCnfdnc = _dotWithCnfdncs[k];
			final SotWithDbl compared = other._dotWithCnfdncs[k];
			if (!dotWithCnfdnc.deepEquals(compared)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int compareTo(final DebrisSighting other) {
		final int otherId = other._id;
		return _id < otherId ? -1 : (_id > otherId ? 1 : 0);
	}

	public long getSightingRefSecs() {
		return _sightingRefSecs;
	}

	public double getConfidence(final DebrisObjectType dot) {
		final int n = _dotWithCnfdncs.length;
		for (int k = 0; k < n; ++k) {
			final SotWithDbl dotWithCnfdnc = _dotWithCnfdncs[k];
			if (dotWithCnfdnc.getSot().getId() == dot.getId()) {
				return dotWithCnfdnc.getWorkingDbl();
			}
		}
		return 0d;
	}

}
