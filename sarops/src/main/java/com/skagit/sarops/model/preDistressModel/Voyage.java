package com.skagit.sarops.model.preDistressModel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.w3c.dom.Element;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.Scenario;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.simCaseManager.SimCaseManager.SimCase;
import com.skagit.util.LsFormatter;
import com.skagit.util.cdf.area.Area;
import com.skagit.util.navigation.MotionType;
import com.skagit.util.randomx.TimeDistribution;

/**
 * This is the type of PreDistressModel that the "Voyage" Scenario uses.
 * It's essentially a sequence of VoyageLegs (see below).
 */
public class Voyage extends PreDistressModel {
	final List<VoyageLeg> _legs = new ArrayList<>();

	public Voyage(final Scenario scenario, final long distressRefSecsMean,
			final double distressPlusMinusHrs) {
		super(scenario, distressRefSecsMean, distressPlusMinusHrs);
	}

	public static class VoyageLeg {
		final private Area _destination;
		final private double _minSpeed, _cruisingSpeed, _maxSpeed;
		final private TimeDistribution _dwellTimeDistribution;
		final private MotionType _motionType;

		private VoyageLeg(final Area destination, final double minSpeed,
				final double cruisingSpeed, final double maxSpeed,
				final TimeDistribution dwellTimeDistribution,
				final MotionType motionType) {
			_dwellTimeDistribution = dwellTimeDistribution;
			_minSpeed = minSpeed;
			_cruisingSpeed = cruisingSpeed;
			_maxSpeed = maxSpeed;
			_destination = destination;
			_motionType = motionType;
		}

		public Area getDestination() {
			return _destination;
		}

		public MotionType getMotionType() {
			return _motionType;
		}

		public double getMinSpeed() {
			return _minSpeed;
		}

		public double getCruisingSpeed() {
			return _cruisingSpeed;
		}

		public double getMaxSpeed() {
			return _maxSpeed;
		}

		public TimeDistribution getDwellTimeDistribution() {
			return _dwellTimeDistribution;
		}

		public boolean deepEquals(final VoyageLeg otherLeg) {
			if ((_destination == null) != (otherLeg._destination == null)) {
				return false;
			}
			if (_destination != null) {
				if (!_destination.deepEquals(otherLeg._destination)) {
					return false;
				}
			}
			if ((_dwellTimeDistribution == null) != (otherLeg._dwellTimeDistribution == null)) {
				return false;
			}
			if (_dwellTimeDistribution != null) {
				if (!_dwellTimeDistribution
						.deepEquals(otherLeg._dwellTimeDistribution)) {
					return false;
				}
			}
			return _minSpeed == otherLeg._minSpeed //
					&& _cruisingSpeed == otherLeg._cruisingSpeed //
					&& _maxSpeed == otherLeg._maxSpeed //
					&& _motionType == otherLeg._motionType;
		}
	}

	public void addLeg(final Area destination, final double minSpeed,
			final double cruisingSpeed, final double maxSpeed,
			final TimeDistribution dwellTimeDistribution,
			final MotionType motionType) {
		final VoyageLeg newLeg = new VoyageLeg(destination, minSpeed,
				cruisingSpeed, maxSpeed, dwellTimeDistribution, motionType);
		_legs.add(newLeg);
	}

	@Override
	public boolean deepEquals(final PreDistressModel compared) {
		if ((compared == null) || !(compared instanceof Voyage)) {
			return false;
		}
		final Voyage other = (Voyage) compared;
		if (_legs.size() != other._legs.size()) {
			return false;
		}
		final Iterator<VoyageLeg> legIterator = _legs.iterator();
		final Iterator<VoyageLeg> otherLegIterator = other._legs.iterator();
		while (legIterator.hasNext()) {
			final VoyageLeg leg = legIterator.next();
			final VoyageLeg otherLeg = otherLegIterator.next();
			if (!leg.deepEquals(otherLeg)) {
				return false;
			}
		}
		if (_distressPlusMinusHrs < 0d != compared._distressPlusMinusHrs < 0d) {
			return false;
		}
		if (_distressPlusMinusHrs < 0d) {
			return true;
		}
		if (_distressPlusMinusHrs != compared._distressPlusMinusHrs) {
			return false;
		}
		if (_distressRefSecsMean != compared._distressRefSecsMean) {
			return false;
		}
		return true;
	}

	@Override
	public void write(final LsFormatter formatter, final Element root,
			final Model model) {
		final Element voyageElement = formatter.newChild(root, "VOYAGE");
		if (getScenario().getNoDistress()) {
			voyageElement.setAttribute("noDistress", "true");
		}
		final Iterator<VoyageLeg> legIterator = _legs.iterator();
		while (legIterator.hasNext()) {
			final VoyageLeg leg = legIterator.next();
			final boolean last = !legIterator.hasNext();
			final Element element =
					formatter.newChild(voyageElement, last ? "FINAL_LEG" : "LEG");
			final String spreadAttributeName = "x_error";
			leg._destination.write(formatter, element, spreadAttributeName);
			if (leg._dwellTimeDistribution != null) {
				leg._dwellTimeDistribution.write(formatter, element, !last,
						last ? "TIME" : "DWELL_TIME");
			}
			element.setAttribute("motion_type", leg._motionType.getId());
			element.setAttribute("minSpeed", leg._minSpeed + " kts");
			element.setAttribute("cruisingSpeed", leg._cruisingSpeed + " kts");
			element.setAttribute("maxSpeed", leg._maxSpeed + " kts");
		}
	}

	public List<VoyageLeg> getLegs() {
		return _legs;
	}

	@Override
	public List<Area> getAreas() {
		final Iterator<VoyageLeg> legIterator = _legs.iterator();
		final ArrayList<Area> areas = new ArrayList<>();
		while (legIterator.hasNext()) {
			final VoyageLeg leg = legIterator.next();
			areas.add(leg._destination);
		}
		return areas;
	}

	@Override
	public ItineraryBuilder getItineraryBuilder(
			final SimCaseManager.SimCase simCase, final Area startingArea) {
		return new VoyageItineraryBuilder(simCase, this, startingArea);
	}

	@Override
	public long getMinArrivalRefSecs(final SimCase simCase) {
		final Scenario scenario = getScenario();
		final TimeDistribution timeDistribution =
				scenario.getDepartureTimeDistribution();
		final Area startingArea = scenario.getDepartureArea();
		final long birthRefSecs = (timeDistribution.getMeanRefMins() -
				timeDistribution.getPlusMinusMins()) * 60;
		final VoyageItineraryBuilder voyageItineraryBuilder =
				(VoyageItineraryBuilder) getItineraryBuilder(simCase, startingArea);
		final VoyageItinerary quickieItinerary = voyageItineraryBuilder
				.myBuildItinerary(/* _model= */null, /* r= */null, birthRefSecs,
						/* prtclIndxs= */null, /* refSecsS= */null, /* sdi= */null);
		return quickieItinerary.getArrivalRefSecs();
	}
}
