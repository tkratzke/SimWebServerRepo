package com.skagit.sarops.environment;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import com.skagit.util.CombinatoricTools;
import com.skagit.util.MathX;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.navigation.LatLng3;

public class PointFinderData {
	final public Comparator<LatLng3> _lngToEast2ThenLatComparator;
	final public double _leftLng;
	final public Map<LatLng3, LatLng3> _latLngs;
	final public double[] _distinctLats;
	final public double[] _distinctLngToEast2s;
	final public double _cosMidLat;

	public PointFinderData(final Collection<LatLng3> latLngs,
			final double leftLng) {
		_leftLng = leftLng;
		_lngToEast2ThenLatComparator = new Comparator<>() {
			@Override
			public int compare(final LatLng3 o1, final LatLng3 o2) {
				final double d1 = LatLng3.degsToEast180_180(_leftLng, o1.getLng());
				final double d2 = LatLng3.degsToEast180_180(_leftLng, o2.getLng());
				if (d1 != d2) {
					return d1 < d2 ? -1 : 1;
				}
				final double d = o1.getLat() - o2.getLat();
				return d < 0 ? -1 : (d == 0 ? 0 : 1);
			}
		};
		_latLngs = new HashMap<>();
		final TreeSet<LatLng3> latLngSet =
				new TreeSet<>(_lngToEast2ThenLatComparator);
		double minLat = 90;
		double maxLat = -90;
		final TreeSet<Double> latSet = new TreeSet<>();
		final TreeSet<Double> lngToEast2Set = new TreeSet<>();
		for (final LatLng3 latLng : latLngs) {
			/** Deal with the lat. */
			final double lat = latLng.getLat();
			minLat = Math.min(minLat, lat);
			maxLat = Math.max(maxLat, lat);
			latSet.add(lat);
			/** Deal with the lng. */
			final double lng = latLng.getLng();
			final double lngToEast2 = LatLng3.degsToEast180_180(_leftLng, lng);
			lngToEast2Set.add(lngToEast2);
			/** Deal with the LatLng. */
			_latLngs.put(latLng, latLng);
			latLngSet.add(latLng);
		}
		_cosMidLat = MathX.cosX(Math.toRadians((minLat + maxLat) / 2));
		_distinctLats = new double[latSet.size()];
		int k = 0;
		for (final Double lat : latSet) {
			_distinctLats[k++] = lat;
		}
		_distinctLngToEast2s = new double[lngToEast2Set.size()];
		k = 0;
		for (final Double lngToEast2 : lngToEast2Set) {
			_distinctLngToEast2s[k++] = lngToEast2;
		}
	}

	public String getBigString() {
		String s = "Lats" + NumericalRoutines.getString(_distinctLats, 6, 2);
		s += String.format("\nBaseLng[%f], lngToEast2s%s.", _leftLng,
				NumericalRoutines.getString(_distinctLngToEast2s, 7, 2));
		final int nLngs = _distinctLngToEast2s.length;
		final double[] originalLngs = new double[nLngs];
		for (int k = 0; k < nLngs; ++k) {
			originalLngs[k] = LatLng3
					.roundToLattice180_180I(_leftLng + _distinctLngToEast2s[k]);
		}
		s += String.format("\nLngs%s.",
				NumericalRoutines.getString(originalLngs, 7, 2));
		return s;
	}

	public String getString() {
		final int nLats = _distinctLats.length;
		final int nLngs = _distinctLngToEast2s.length;
		final int nLatLngs = _latLngs.size();
		final int excess = nLats * nLngs - nLatLngs;
		final String s = String.format(", #[lats,lngs,latLngs,excess]=%s",
				CombinatoricTools.getString( //
						new int[] { nLats, nLngs, nLatLngs, excess } //
				));
		return s;
	}

	@Override
	public String toString() {
		return getString();
	}
}
