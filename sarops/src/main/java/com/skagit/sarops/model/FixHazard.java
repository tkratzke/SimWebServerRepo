package com.skagit.sarops.model;

import java.util.Iterator;
import java.util.List;

import org.w3c.dom.Element;

import com.skagit.util.LsFormatter;
import com.skagit.util.TimeUtilities;
import com.skagit.util.cdf.area.Area;

public class FixHazard {
	final private double _intensity;
	final private List<Area> _areas;
	final private long _startRefSecs;
	final private long _durationSecs;

	public FixHazard(final double intensity, final List<Area> areas, final long startRefSecs, final long durationSecs) {
		_intensity = intensity;
		_areas = areas;
		_startRefSecs = startRefSecs;
		_durationSecs = durationSecs;
	}

	public boolean deepEquals(final FixHazard hazard) {
		if (_intensity != hazard._intensity || _areas.size() != hazard._areas.size()
				|| _startRefSecs != hazard._startRefSecs || _durationSecs != hazard._durationSecs) {
			return false;
		}
		final Iterator<Area> areaIterator = _areas.iterator();
		final Iterator<Area> otherAreaIterator = hazard._areas.iterator();
		while (areaIterator.hasNext()) {
			final Area area = areaIterator.next();
			final Area otherArea = otherAreaIterator.next();
			if (!area.deepEquals(otherArea)) {
				return false;
			}
		}
		return true;
	}

	public void write(final LsFormatter formatter, final Element element) {
		final Element hazardElement = formatter.newChild(element, "FIX_HAZARD");
		hazardElement.setAttribute("intensity", LsFormatter.StandardFormat(_intensity));
		if (_startRefSecs > 0) {
			hazardElement.setAttribute("start", TimeUtilities.formatTime(_startRefSecs, false));
			hazardElement.setAttribute("end", TimeUtilities.formatTime(_startRefSecs + _durationSecs, false));
		}
		for (final Area area : _areas) {
			area.write(formatter, hazardElement, /* spreadAttributeName= */null);
		}
	}

	public List<Area> getAreas() {
		return _areas;
	}

	public double getIntensity() {
		return _intensity;
	}

	public boolean isTimeBased() {
		return _startRefSecs > 0;
	}

	public long getDurationSecs() {
		return _durationSecs;
	}

	public long getStartRefSecs() {
		return _startRefSecs;
	}
}
