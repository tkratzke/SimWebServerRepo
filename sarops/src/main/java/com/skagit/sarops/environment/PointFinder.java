package com.skagit.sarops.environment;

import java.util.ArrayList;

import com.skagit.util.navigation.LatLng3;

abstract public class PointFinder {
	final public PointFinderData _pointFinderData;

	protected PointFinder(final PointFinderData pointFinderData) {
		_pointFinderData = pointFinderData;
	}

	public String toBigString() {
		return _pointFinderData.getString();
	}

	abstract public ArrayList<LatLng3> getClosestPoints(LatLng3 latLng,
			int nPointsToFind);

	abstract public boolean isEmpty();

	abstract public void freeMemory();
}
