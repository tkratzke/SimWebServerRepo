package com.skagit.sarops.model.preDistressModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import com.skagit.sarops.model.FixHazard;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.Scenario;
import com.skagit.sarops.model.preDistressModel.PreDistressModel.ItineraryWaypoint;
import com.skagit.sarops.model.preDistressModel.sail.SailSeg;
import com.skagit.sarops.model.preDistressModel.sail.Sdi;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.StateVectorType;
import com.skagit.sarops.tracker.UnderwayStateVector;
import com.skagit.util.CombinatoricTools;
import com.skagit.util.HdgKts;
import com.skagit.util.MathX;
import com.skagit.util.cdf.area.Area;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.MotionType;
import com.skagit.util.navigation.NavigationCalculator;
import com.skagit.util.navigation.NavigationCalculatorStatics;
import com.skagit.util.randomx.Randomx;

public class VoyageItinerary extends PreDistressModel.Itinerary {
	private static final double _NmiToR = MathX._NmiToR;
	private long _arrivalRefSecs;
	private LatLng3 _destinationLatLng;
	final private ArrayList<ItineraryLeg> _itineraryLegs = new ArrayList<>();
	private boolean _sorted = true;

	public VoyageItinerary(final LatLng3 startLatLng, final long birthRefSecs, final Sdi sdi) {
		super(startLatLng, birthRefSecs, sdi);
		_distressRefSecs = Long.MIN_VALUE;
		_distressLatLng = null;
	}

	public static class ItineraryLeg {
		final SailSeg _sailSeg;
		final LatLng3 _latLng0;
		final LatLng3 _latLng1;
		final long _refSecs0;
		final long _refSecs1;
		final double _kts;
		final StateVectorType _svt;
		final NavigationCalculator _navCalc;

		ItineraryLeg(final LatLng3 latLng0, final LatLng3 latLng1, final StateVectorType svt, final long refSecs0,
				final long refSecs1, final double kts, final NavigationCalculator navCalc) {
			_sailSeg = null;
			_latLng0 = latLng0;
			_latLng1 = latLng1;
			_refSecs0 = refSecs0;
			_refSecs1 = refSecs1;
			_kts = kts;
			_svt = svt;
			_navCalc = navCalc;
		}

		ItineraryLeg(final SailSeg sailSeg) {
			_sailSeg = sailSeg;
			final GreatCircleArc segGca = _sailSeg._gca;
			_latLng0 = segGca.getLatLng0();
			_latLng1 = segGca.getLatLng1();
			_refSecs0 = _sailSeg._refSecs0;
			_refSecs1 = _sailSeg._refSecs1;
			_kts = _sailSeg.getKts();
			_svt = _sailSeg._svt1;
			_navCalc = NavigationCalculatorStatics.build(_latLng0, _refSecs0, _latLng1, _kts, MotionType.GREAT_CIRCLE);
		}

		/** For looking up by refSecs0. */
		private ItineraryLeg(final long refSecs0) {
			_sailSeg = null;
			_latLng0 = _latLng1 = null;
			_refSecs0 = refSecs0;
			_refSecs1 = Long.MIN_VALUE;
			_kts = Double.NaN;
			_svt = null;
			_navCalc = null;
		}

		public StateVectorType getSvt() {
			return _svt;
		}
	}

	@Override
	public UnderwayStateVector move(final SimCaseManager.SimCase simCasex, final Scenario scenario,
			final UnderwayStateVector underwayStateVector0, final long durationSecs) {
		if (durationSecs == 0) {
			return underwayStateVector0;
		}
		final boolean updateParticleTail = true;
		/** Underway; refSecs == simSecs. */
		final long refSecs0 = underwayStateVector0.getSimSecs();
		final long refSecs1 = refSecs0 + durationSecs;
		if (refSecs0 >= _arrivalRefSecs) {
			/** underwayStateVector0 is at destination. */
			return new UnderwayStateVector(underwayStateVector0, StateVectorType.ARRIVE, refSecs1, updateParticleTail);
		}
		final LatLng3 latLng0 = underwayStateVector0.getLatLng();
		final ItineraryWaypoint waypoint0 = new ItineraryWaypoint(latLng0, refSecs0);
		/**
		 * We did not arrive. If this is a non-sailing voyage, all legs say "arrive" so
		 * we cannot take that as our waypoint0's StateVectorType.
		 */
		if (scenario.getSailData() != null) {
			waypoint0._extraInformation = underwayStateVector0.getSvt();
		} else {
			waypoint0._extraInformation = StateVectorType.UNDERWAY;
		}
		final ItineraryWaypoint waypoint1 = move(scenario, waypoint0, durationSecs);
		final UnderwayStateVector underwayStateVector1 = new UnderwayStateVector(underwayStateVector0,
				(StateVectorType) waypoint1._extraInformation, waypoint1._simSecs, updateParticleTail);
		underwayStateVector1.setLatLng(waypoint1._latLng);
		return underwayStateVector1;
	}

	@Override
	public ItineraryWaypoint move(final Scenario scenario, final ItineraryWaypoint waypoint0,
			final long durationSecs0) {
		/**
		 * For a PreDistressModel, simSecs = refSecs, so there is no conversion in this
		 * routine.
		 */
		if (durationSecs0 == 0) {
			return waypoint0;
		}
		final long simSecs0 = waypoint0._simSecs;
		final LatLng3 latLng0 = waypoint0._latLng;
		if (simSecs0 >= _arrivalRefSecs) {
			/** We basically arrived at destination. */
			final ItineraryWaypoint waypoint = new ItineraryWaypoint(latLng0, _arrivalRefSecs);
			waypoint._extraInformation = StateVectorType.ARRIVE;
			return waypoint;
		}

		/** Find the leg for simSecs0. */
		ItineraryLeg theLeg = null;
		final Iterator<ItineraryLeg> legIterator = _itineraryLegs.iterator();
		while (legIterator.hasNext()) {
			final ItineraryLeg leg = legIterator.next();
			theLeg = leg;
			if (simSecs0 < leg._refSecs1) {
				break;
			}
		}

		final long simSecs1 = simSecs0 + durationSecs0;
		final long theLegSimSecs0 = theLeg._refSecs0;
		if (simSecs1 < theLegSimSecs0) {
			/** We start and end at rest. */
			final ItineraryWaypoint waypoint = new ItineraryWaypoint(latLng0, simSecs1);
			waypoint._extraInformation = theLeg._svt;
			return waypoint;
		}

		final long simSecs, durationSecs;
		if (simSecs0 <= theLegSimSecs0) {
			/** We start at rest, but start moving with theLeg's simSecs0. */
			durationSecs = durationSecs0 - (theLegSimSecs0 - simSecs0);
			simSecs = theLegSimSecs0;
		} else {
			/** We don't start at rest. */
			simSecs = simSecs0;
			durationSecs = durationSecs0;
		}

		/** We are moving within theLeg. */
		final long theLegSimSecs1 = theLeg._refSecs1;
		final LatLng3 theLegLatLng1 = theLeg._latLng1;
		if (simSecs1 >= theLegSimSecs1) {
			/**
			 * We know where and when we get to the end of the leg and then use a recursive
			 * call to use up the rest of duration.
			 */
			final ItineraryWaypoint waypoint = new ItineraryWaypoint(theLegLatLng1, theLegSimSecs1);
			waypoint._extraInformation = theLeg._svt;
			final long durationSecs2 = durationSecs - (theLegSimSecs1 - simSecs);
			final ItineraryWaypoint waypoint1 = move(scenario, waypoint, durationSecs2);
			return waypoint1;
		}
		/**
		 * We finish within the current leg and use up the entire duration.
		 */
		final LatLng3 latLng1 = theLeg._navCalc.getPosition(simSecs1);
		final ItineraryWaypoint waypoint = new ItineraryWaypoint(latLng1, simSecs1);
		if (scenario.getSailData() == null) {
			waypoint._extraInformation = StateVectorType.UNDERWAY;
		} else {
			waypoint._extraInformation = theLeg._svt;
		}
		return waypoint;
	}

	final private long computeDistressRefSecs(final Model model, final Randomx random, final long birthRefSecs) {
		final DistressRefSecsCalculator distressRefSecsCalculator = new DistressRefSecsCalculator(birthRefSecs,
				_arrivalRefSecs);
		final List<FixHazard> fixHazards = model.getFixHazards();
		final int nFixHazards = fixHazards == null ? 0 : fixHazards.size();
		final int nLegs = _itineraryLegs == null ? 0 : _itineraryLegs.size();
		for (int k1 = 0; k1 < nFixHazards; ++k1) {
			final FixHazard hazard = fixHazards.get(k1);
			final long hazardStartRefSecs = hazard.getStartRefSecs();
			final long hazardEndRefSecs = hazardStartRefSecs + hazard.getDurationSecs();
			final double intensity = hazard.getIntensity();
			final List<Area> areas = hazard.getAreas();
			final int nAreas = areas == null ? 0 : areas.size();
			for (int k2 = 0; k2 < nAreas; ++k2) {
				final Area area = areas.get(k2);
				for (int k3 = 0; k3 < nLegs; ++k3) {
					final ItineraryLeg leg = _itineraryLegs.get(k3);
					final LatLng3 legStartLatLng = leg._latLng0;
					final LatLng3 legEndLatLng = leg._latLng1;
					final long legStartRefSecs = leg._refSecs0;
					final long legEndRefSecs = leg._refSecs1;
					final List<long[]> timeIntervals = area.getIntersectingTimeIntervals(legStartLatLng, legEndLatLng,
							legStartRefSecs, legEndRefSecs);
					for (final long[] interval : timeIntervals) {
						final long startIntersection = Math.max(legStartRefSecs,
								Math.max(hazardStartRefSecs, interval[0]));
						final long endIntersection = Math.min(legEndRefSecs, Math.min(hazardEndRefSecs, interval[1]));
						distressRefSecsCalculator.add(intensity, startIntersection, endIntersection);
					}
					/**
					 * If this is not the last leg, add the dwell time-hazard intensity before the
					 * next leg.
					 */
					if (area.isIn(legEndLatLng)) {
						if (k3 < nLegs - 1) {
							final ItineraryLeg nextLeg = _itineraryLegs.get(k3 + 1);
							final long startDwellSecs = legEndRefSecs;
							final long endDwellRefSecs = nextLeg._refSecs0;
							final long startDwellHazardSecs = Math.max(startDwellSecs, hazardStartRefSecs);
							final long endDwellHazardSecs = Math.min(endDwellRefSecs, hazardEndRefSecs);
							distressRefSecsCalculator.add(intensity, startDwellHazardSecs, endDwellHazardSecs);
						}
					}
				}
			}
		}
		final long distressRefSecs = distressRefSecsCalculator.computeDistressRefSecs(random, _birthRefSecs,
				_arrivalRefSecs);
		return distressRefSecs;
	}

	public void add(final ItineraryLeg newLeg) {
		_itineraryLegs.add(newLeg);
		_sorted = false;
	}

	public ItineraryLeg getLastItineraryLeg() {
		if (_itineraryLegs.isEmpty()) {
			return null;
		}
		return _itineraryLegs.get(_itineraryLegs.size() - 1);
	}

	public void addAll(final Collection<ItineraryLeg> newLegs) {
		_itineraryLegs.addAll(newLegs);
		_sorted = false;
	}

	long computeDistressRefSecs(final Model model, final Randomx random) {
		final long distressRefSecs = computeDistressRefSecs(model, random, _birthRefSecs);
		return distressRefSecs;
	}

	public LatLng3 getDestinationLatLng() {
		return _destinationLatLng;
	}

	@Override
	public LatLng3 getDistressLatLng() {
		return _distressLatLng;
	}

	public LatLng3[] getLatLngs() {
		sortItineraryLegs();
		final int nLegs = _itineraryLegs == null ? 0 : _itineraryLegs.size();
		if (nLegs == 0) {
			return null;
		}
		final LatLng3[] returnValue = new LatLng3[nLegs + 1];
		returnValue[0] = _itineraryLegs.get(0)._latLng0;
		for (int k = 0; k < nLegs; ++k) {
			returnValue[k + 1] = _itineraryLegs.get(k)._latLng1;
		}
		return returnValue;
	}

	private static boolean isLookUpOnly(final ItineraryLeg leg) {
		return leg._sailSeg == null && leg._latLng0 == null;
	}

	private static Comparator<ItineraryLeg> _ByRefSecs0Only = new Comparator<>() {

		@Override
		public int compare(final ItineraryLeg o0, final ItineraryLeg o1) {
			if (o0 == o1) {
				return 0;
			}
			if (o0._refSecs0 != o1._refSecs0) {
				return o0._refSecs0 < o1._refSecs0 ? -1 : 1;
			}
			assert isLookUpOnly(o0) || isLookUpOnly(o1);
			return 0;
		}
	};

	private void sortItineraryLegs() {
		if (_sorted) {
			return;
		}
		synchronized (this) {
			if (_sorted) {
				return;
			}
			Collections.sort(_itineraryLegs, _ByRefSecs0Only);
			_sorted = true;
		}
	}

	public long getArrivalRefSecs() {
		return _arrivalRefSecs;
	}

	public void setDestinationLatLng(final LatLng3 latLng) {
		_destinationLatLng = latLng;
	}

	public void setArrivalRefSecs(final long arrivalRefSecs) {
		_arrivalRefSecs = arrivalRefSecs;
	}

	public static void main(final String[] args) {
		final int[] ints = {
				0, 2, 4
		};
		for (int d = -1; d <= 5; ++d) {
			final int glbIdx = CombinatoricTools.getGlbIndex(ints, d);
			System.out.println(d + " " + glbIdx);
		}
		final ArrayList<String> strings = new ArrayList<>();
		strings.add("A1");
		strings.add("B1");
		strings.add("B");
		strings.add("A");
		strings.add("A2");
		strings.add("B2");
		strings.sort(new Comparator<String>() {

			@Override
			public int compare(final String o1, final String o2) {
				return Character.compare(o1.charAt(0), o2.charAt(0));
			}
		});
		final int nStrings = strings.size();
		for (int k = 0; k < nStrings; ++k) {
			final String string = strings.get(k);
			System.out.println(string);
		}
	}

	public static class OcInfo {
		public final float _lat;
		public final float _lng;
		public final float _eastDnWind;
		public final float _northDnWind;
		public final float _eastDnCurrent;
		public final float _northDnCurrent;
		public final float _eastBoat;
		public final float _northBoat;
		public final StateVectorType _svt;

		private OcInfo(final ItineraryLeg leg, final long refSecs) {
			_svt = leg.getSvt();
			final long refSecs0;
			final long refSecs1;
			final LatLng3 latLng0;
			final LatLng3 latLng1;
			if (leg._sailSeg == null) {
				refSecs0 = leg._refSecs0;
				refSecs1 = leg._refSecs1;
				latLng0 = leg._latLng0;
				latLng1 = leg._latLng1;
			} else {
				final SailSeg sailSeg = leg._sailSeg;
				refSecs0 = sailSeg._refSecs0;
				refSecs1 = sailSeg._refSecs1;
				latLng0 = sailSeg._gca.getLatLng0();
				latLng1 = sailSeg._gca.getLatLng1();
			}
			if (refSecs < refSecs0) {
				/** Emphatically undefined. */
				_lat = _lng = _eastDnWind = _northDnWind = _eastDnCurrent = _northDnCurrent = _eastBoat = _northBoat = Float.NaN;
				return;
			}
			if (refSecs0 >= refSecs1) {
				/** Everything except _lat and _lng are undefined. */
				_lat = _lng = _eastBoat = _northBoat = _eastDnWind = _northDnWind = _eastDnCurrent = _northDnCurrent = Float.NaN;
				return;
			}
			if (refSecs > refSecs1) {
				/** Everything except _lat and _lng are undefined. */
				_lat = (float) latLng1.getLat();
				_lng = (float) latLng1.getLng();
				_eastBoat = _northBoat = _eastDnWind = _northDnWind = _eastDnCurrent = _northDnCurrent = Float.NaN;
				return;
			}
			/** refSecs0 <= refSecs <= refSecs1 && refSecs0 < refSecs1. */
			final double hdg0;
			final double legNmi;
			if (leg._sailSeg == null) {
				_eastDnWind = _northDnWind = _eastDnCurrent = _northDnCurrent = Float.NaN;
				hdg0 = MathX.initialHdgX(latLng0, latLng1);
				legNmi = MathX.haversineX(latLng0, latLng1) / _NmiToR;
			} else {
				final HdgKts upWind0 = leg._sailSeg._upWind;
				_eastDnWind = (float) -upWind0.getEastKts();
				_northDnWind = (float) -upWind0.getNorthKts();
				final HdgKts dnCurrent0 = leg._sailSeg._dnCurrent;
				_eastDnCurrent = (float) dnCurrent0.getEastKts();
				_northDnCurrent = (float) dnCurrent0.getNorthKts();
				final SailSeg sailSeg = leg._sailSeg;
				hdg0 = sailSeg._gca.getRawInitialHdg();
				legNmi = sailSeg._gca.getTtlNmi();
			}
			final double legDurationSecs = refSecs1 - refSecs0;
			final double legDurationHrs = legDurationSecs / 3600d;
			final double p = (refSecs - refSecs0) / legDurationSecs;
			final double haversine = p * legNmi * _NmiToR;
			final LatLng3 latLng = MathX.getLatLngX(latLng0, hdg0, haversine);
			_lat = (float) latLng.getLat();
			_lng = (float) latLng.getLng();
			final double hdg1 = MathX.initialHdgX(latLng, latLng1);
			final double kts = legNmi / legDurationHrs;
			final double theta = Math.toRadians(90d - hdg1);
			_eastBoat = (float) (MathX.cosX(theta) * kts);
			_northBoat = (float) (MathX.sinX(theta) * kts);
		}
	}

	public OcInfo getOcInfo(final long[] refSecsS, final int timeIdx) {
		final long refSecs = refSecsS[timeIdx];
		final ItineraryLeg leg = getItineraryLeg(refSecs);
		return leg == null ? null : new OcInfo(leg, refSecs);
	}

	/** Find the ItineraryLeg whose refSecs0 <= refSecs. */
	private ItineraryLeg getItineraryLeg(final long refSecs) {
		sortItineraryLegs();
		final int nLegs = _itineraryLegs.size();
		if (nLegs == 0) {
			return null;
		}
		int idx = Collections.binarySearch(_itineraryLegs, new ItineraryLeg(refSecs), _ByRefSecs0Only);
		if (idx >= 0) {
			/**
			 * refSecs exactly matches some ItineraryLeg. Return the first such one.
			 */
			return _itineraryLegs.get(idx);
		}
		/**
		 * Want the leg with the smallest refSecs0 for which its refSecs0 is less than
		 * refSecs. If I took the insert index (ie -idx-1), I would get a leg whose
		 * refSecs0 is bigger than refSecs. Hence I take -idx-2.
		 */
		idx = -idx - 2;
		if (idx < 0) {
			/** All of the legs have refSecs0 bigger than refSecs. */
			return null;
		}
		return _itineraryLegs.get(idx);
	}

}
