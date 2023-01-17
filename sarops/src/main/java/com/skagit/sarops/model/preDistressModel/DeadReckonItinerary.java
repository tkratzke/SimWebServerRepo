package com.skagit.sarops.model.preDistressModel;

import com.skagit.sarops.model.Scenario;
import com.skagit.sarops.model.preDistressModel.PreDistressModel.ItineraryWaypoint;
import com.skagit.sarops.model.preDistressModel.sail.Sdi;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.StateVectorType;
import com.skagit.sarops.tracker.UnderwayStateVector;
import com.skagit.util.Constants;
import com.skagit.util.TimeUtilities;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.NavigationCalculator;
import com.skagit.util.navigation.NavigationCalculatorStatics;
import com.skagit.util.randomx.Randomx;

public class DeadReckonItinerary extends PreDistressModel.Itinerary
		implements Constants {
	final private DeadReckon _deadReckon;
	final private NavigationCalculator _navigationCalculator;

	public DeadReckonItinerary(final SimCaseManager.SimCase simCase,
			final DeadReckon deadReckon, final long birthRefSecs,
			final Randomx random, final Sdi sdi) {
		super(deadReckon.getScenario().getDepartureArea().generateLatLng(
				SimCaseManager.getLogger(simCase), random), birthRefSecs, sdi);
		_deadReckon = deadReckon;
		final long unconstrainedDistressRefSecs;
		/** Calculate the unconstrained distress time. */
		final long unconstrainedMaxDistressRefSecs = deadReckon
				.getMaxDistressRefSecs(simCase);
		if (unconstrainedMaxDistressRefSecs > _birthRefSecs) {
			final long bound = unconstrainedMaxDistressRefSecs - _birthRefSecs;
			if (bound >= Integer.MAX_VALUE) {
				unconstrainedDistressRefSecs = Integer.MAX_VALUE - 1;
			} else {
				unconstrainedDistressRefSecs = _birthRefSecs
						+ random.nextInt((int) bound);
			}
		} else {
			unconstrainedDistressRefSecs = _birthRefSecs;
		}
		/** Calculate the constrained distress time. */
		final long distressRefSecsMean = _deadReckon.getDistressRefSecsMean();
		final double distressPlusMinusHrs = _deadReckon
				.getDistressPlusMinusHrs();
		final long distressRefSecs;
		if (distressRefSecsMean < 0 || distressPlusMinusHrs < 0d) {
			/** No constraint. */
			distressRefSecs = unconstrainedDistressRefSecs;
		} else {
			/** Constrained. */
			final long halfIntervalSecs = Math
					.round(distressPlusMinusHrs * 3600d);
			if (_birthRefSecs > distressRefSecsMean + halfIntervalSecs) {
				/** Birth is too high; go into distress at birth. */
				distressRefSecs = _birthRefSecs;
			} else if (unconstrainedDistressRefSecs <= distressRefSecsMean
					- halfIntervalSecs) {
				/**
				 * Unconstrained distress is too low; go into distress as soon
				 * as we get to the distress interval.
				 */
				distressRefSecs = distressRefSecsMean - halfIntervalSecs;
			} else if (unconstrainedDistressRefSecs <= distressRefSecsMean
					+ halfIntervalSecs) {
				/** Unconstrained distress is in the interval; use it. */
				distressRefSecs = unconstrainedDistressRefSecs;
			} else {
				/**
				 * Unconstrained distress is too high; go into disterss at the
				 * high end of the distress interval.
				 */
				distressRefSecs = distressRefSecsMean + halfIntervalSecs;
			}
		}

		/**
		 * The "course" is in rads, but cw from north. The "ctor" (i.e, "build"
		 * routines) expect "course" to be in degrees cw from north. The
		 * truncated normal returns a value between -Randomx._StandardDeviations
		 * and Randomx._StandardDeviations.
		 */
		final double minusOneToOne = random.getNormalizedTruncatedGaussian();
		final double error = minusOneToOne * deadReckon.getCourseErrorInRads();
		final float hdg = (float) Math
				.toDegrees(deadReckon.getCourseCwFromNorthInRads() + error);
		final float kts = (float) random.getSplitGaussianDraw(
				deadReckon.getMinSpeed(), deadReckon.getCruisingSpeed(),
				deadReckon.getMaxSpeed());
		_navigationCalculator = NavigationCalculatorStatics.build(_startLatLng,
				birthRefSecs, hdg, kts, deadReckon.getMotionType());
		setDistressRefSecs(deadReckon.getScenario(), distressRefSecs);
		if (PreDistressModel._DumpItinerary) {
			final boolean includeSeconds = true;
			final String s = String.format(
					"DistressTime:%s distressLatLng[%s]", TimeUtilities
							.formatTime(getDistressRefSecs(), includeSeconds),
					_distressLatLng.getString());
			SimCaseManager.out(simCase, s);
		}
	}

	@Override
	public UnderwayStateVector move(final SimCaseManager.SimCase simCase,
			final Scenario scenario, final UnderwayStateVector stateVector0,
			final long durationSecs) {
		final boolean updateParticleTail = true;
		final LatLng3 latLng0 = stateVector0.getLatLng();
		final long simSecs0 = stateVector0.getSimSecs();
		final ItineraryWaypoint waypoint0 = new ItineraryWaypoint(latLng0,
				simSecs0);
		final ItineraryWaypoint waypoint1 = move(scenario, waypoint0,
				durationSecs);
		final UnderwayStateVector underwayStateVector = new UnderwayStateVector(
				stateVector0, StateVectorType.UNDERWAY, waypoint1._simSecs,
				updateParticleTail);
		underwayStateVector.setLatLng(waypoint1._latLng);
		return underwayStateVector;
	}

	@Override
	public ItineraryWaypoint move(final Scenario scenario,
			final ItineraryWaypoint waypoint0, final long durationSecs) {
		final long simSecs0 = waypoint0._simSecs;
		final long simSecs1 = simSecs0 + durationSecs;
		final LatLng3 latLng1 = _navigationCalculator.getPosition(simSecs1);
		final ItineraryWaypoint waypoint1 = new ItineraryWaypoint(latLng1,
				simSecs1);
		return waypoint1;
	}

}
