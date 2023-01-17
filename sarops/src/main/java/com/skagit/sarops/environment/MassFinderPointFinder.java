package com.skagit.sarops.environment;

import java.util.ArrayList;

import com.skagit.util.MassFinder;
import com.skagit.util.navigation.LatLng3;

public class MassFinderPointFinder extends PointFinder {
	final MassFinder _massFinder;

	public MassFinderPointFinder(final PointFinderData pointFinderData) {
		super(pointFinderData);
		final double cosMidLat = pointFinderData._cosMidLat;
		final ArrayList<MassFinder.Item> items = new ArrayList<>();
		for (final LatLng3 latLng : pointFinderData._latLngs.keySet()) {
			final double x = LatLng3.degsToEast180_180(_pointFinderData._leftLng,
					latLng.getLng()) * cosMidLat;
			final double y = latLng.getLat();
			final MassFinder.Item item = new MassFinder.Item(x, y, 1.0, 0, latLng);
			items.add(item);
		}
		_massFinder = items.size() == 0 ? null : new MassFinder(items);
	}

	@Override
	public ArrayList<LatLng3> getClosestPoints(final LatLng3 latLng,
			final int nPointsToFind) {
		final ArrayList<LatLng3> returnValue = new ArrayList<>();
		final double lat = latLng.getLat();
		final double lng = latLng.getLng();
		final double x = LatLng3.degsToEast180_180(_pointFinderData._leftLng,
				lng) * _pointFinderData._cosMidLat;
		final double y = lat;
		final MassFinder.Item[] items = _massFinder.getClosestItems(nPointsToFind,
				x, y);
		for (final MassFinder.Item item : items) {
			if (item != null) {
				returnValue.add((LatLng3) item._referenceObject);
			}
		}
		return returnValue;
	}

	@Override
	public boolean isEmpty() {
		return _massFinder == null || _massFinder.isEmpty();
	}

	@Override
	public void freeMemory() {
		if (_massFinder != null) {
			_massFinder.freeMemory();
		}
	}
}
