/*
 * Created on Jan 29, 2004
 */
package com.skagit.sarops.model.preDistressModel;

import java.util.List;

import org.w3c.dom.Element;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.Scenario;
import com.skagit.sarops.model.preDistressModel.sail.Sdi;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.sarops.tracker.UnderwayStateVector;
import com.skagit.util.LsFormatter;
import com.skagit.util.cdf.Cdf;
import com.skagit.util.cdf.area.Area;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.randomx.Randomx;
import com.skagit.util.randomx.TimeDistribution;

/**
 * A PreDistressModel is mostly interesting because it has the
 * getItineraryBuilder method. This allows the tracker to use the
 * PreDistressModel from the scenario and (if it's not null) generate the
 * pre-distress motion.
 */
abstract public class PreDistressModel {
	/** For Debugging; we'll dump the Itineraries of troublesome particles. */
	public static boolean _DumpItinerary = false;

	final private Scenario _scenario;

	/** This is the prescribed distress interval; we may overrule it. */
	protected long _distressRefSecsMean;
	protected double _distressPlusMinusHrs;

	public PreDistressModel(final Scenario scenario,
			final long distressRefSecsMean, final double distressPlusMinusHrs) {
		_scenario = scenario;
		if (distressRefSecsMean > 0 && distressPlusMinusHrs >= 0d) {
			/**
			 * Distress distribution is given, but if it stretches back to the max
			 * birth time, we set the distress distributions's values so that they
			 * are ignored. We will later set the distress distribution's values
			 * to be ignored if we learn that the min arrival is smaller than the
			 * high endpoint of the distress interval.
			 */
			final TimeDistribution departureTimeDistribution =
					scenario.getDepartureTimeDistribution();
			final double departureMeanRefMins =
					departureTimeDistribution.getMeanRefMins();
			final double plusMinusMins =
					departureTimeDistribution.getPlusMinusMins();
			final double maxDepartureRefSecs =
					60d * (departureMeanRefMins + plusMinusMins);
			final int distressRefSecsMin = (int) Math
					.floor(distressRefSecsMean - distressPlusMinusHrs * 3600d);
			if (maxDepartureRefSecs > distressRefSecsMin) {
				_distressRefSecsMean = -1;
				_distressPlusMinusHrs = -1d;
				return;
			}
		}
		_distressRefSecsMean = distressRefSecsMean;
		_distressPlusMinusHrs = distressPlusMinusHrs;
	}

	public void reactToMinArrivalRefSecs(final long minArrivalRefSecs) {
		if (!(_distressRefSecsMean > 0 && _distressPlusMinusHrs >= 0d)) {
			/** Already wiped out. */
			return;
		}
		final long distressRefSecsMax =
				(long) (_distressRefSecsMean + (3600d * _distressPlusMinusHrs));
		if (minArrivalRefSecs < distressRefSecsMax) {
			/**
			 * minArrival falls in the prescribed distress interval; wipe out the
			 * prescribed distress interval.
			 */
			_distressRefSecsMean = -1;
			_distressPlusMinusHrs = -1d;
			return;
		}
	}

	public boolean getRefSecsIsInDistressRange(final long distressRefSecs) {
		final long[] distressInterval = getDistressInterval();
		if (distressInterval == null) {
			return true;
		}
		return distressInterval[0] <= distressRefSecs &&
				distressRefSecs <= distressInterval[1];
	}

	public long[] getDistressInterval() {
		if (!(_distressRefSecsMean > 0 && _distressPlusMinusHrs >= 0d)) {
			return null;
		}
		final long lo =
				Math.round(_distressRefSecsMean - 3600d * _distressPlusMinusHrs);
		final long hi =
				Math.round(_distressRefSecsMean + 3600d * _distressPlusMinusHrs);
		return new long[] { lo, hi };
	}

	abstract public boolean deepEquals(PreDistressModel compared);

	abstract public void write(LsFormatter formatter, Element root,
			Model model);

	abstract public List<Area> getAreas();

	abstract public long getMinArrivalRefSecs(SimCaseManager.SimCase simCase);

	/** The Tracker will call this routine and then build the Itineraries. */
	abstract public ItineraryBuilder getItineraryBuilder(
			SimCaseManager.SimCase simCase, Area startingArea);

	abstract public static class ItineraryBuilder {
		abstract public Itinerary buildItinerary(Model model, Randomx r,
				long startRefSecs, ParticleIndexes prtclIndxs, long[] refSecsS)
				throws Cdf.BadCdfException;
	}

	public static class ItineraryWaypoint {
		final LatLng3 _latLng;
		final long _simSecs;
		Object _extraInformation;

		public ItineraryWaypoint(final LatLng3 latLng, final long simSecs) {
			_latLng = latLng;
			_simSecs = simSecs;
			_extraInformation = null;
		}
	}

	abstract public static class Itinerary {
		final protected LatLng3 _startLatLng;
		final protected long _birthRefSecs;
		protected long _distressRefSecs;
		protected LatLng3 _distressLatLng;
		public final Sdi _sdi;

		protected Itinerary(final LatLng3 startLatLng, final long birthRefSecs,
				final Sdi sdi) {
			_startLatLng = LatLng3.makeBasicLatLng3(startLatLng);
			_birthRefSecs = birthRefSecs;
			_sdi = sdi;
		}

		public final void setDistressRefSecs(final Scenario scenario,
				final long distressRefSecs) {
			_distressRefSecs = distressRefSecs;
			setDistressLatLng(scenario);
		}

		private void setDistressLatLng(final Scenario scenario) {
			if (_distressRefSecs < 0) {
				_distressLatLng = null;
				return;
			}
			final ItineraryWaypoint waypoint0 =
					new ItineraryWaypoint(_startLatLng, _birthRefSecs);
			final long durationSecs = _distressRefSecs - waypoint0._simSecs;
			final ItineraryWaypoint waypoint1 =
					move(scenario, waypoint0, durationSecs);
			_distressLatLng = waypoint1._latLng;
		}

		abstract public UnderwayStateVector move(
				final SimCaseManager.SimCase simCase, Scenario scenario,
				final UnderwayStateVector stateVector0, final long durationSecs);

		abstract public ItineraryWaypoint move(Scenario scenario,
				ItineraryWaypoint waypoint0, long durationSecs);

		final public long getDistressRefSecs() {
			return _distressRefSecs;
		}

		public LatLng3 getDistressLatLng() {
			return _distressLatLng;
		}

		final public LatLng3 getStartLatLng() {
			return _startLatLng;
		}

		final public long getBirthRefSecs() {
			return _birthRefSecs;
		}

		public Sdi getSdi() {
			return _sdi;
		}
	}

	public Scenario getScenario() {
		return _scenario;
	}

	public long getDistressRefSecsMean() {
		return _distressRefSecsMean;
	}

	public double getDistressPlusMinusHrs() {
		return _distressPlusMinusHrs;
	}
}
