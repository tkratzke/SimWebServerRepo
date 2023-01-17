package com.skagit.sarops.model.preDistressModel;

import java.util.List;

import org.w3c.dom.Element;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.Scenario;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.LsFormatter;
import com.skagit.util.TimeUtilities;
import com.skagit.util.cdf.area.Area;
import com.skagit.util.navigation.MotionType;

public class DeadReckon extends PreDistressModel {
	/**
	 * _course and courseError are in radians, but course is clockwise from north.
	 * The "speeds" are in kts.
	 */
	final private double _courseCwFromNorthR;
	final private double _courseErrorR;
	final private double _minSpeed, _cruisingSpeed, _maxSpeed;
	final private long _maxDistressRefSecs;
	final private long _minArrivalRefSecs;
	final private MotionType _motionType;

	/**
	 * Represents a situation where the pre-distress is essentially a straight
	 * line (as per the MotionType).
	 */
	public DeadReckon(final Scenario scenario, final long distressRefSecsMean,
			final double distressPlusMinusHrs, final double courseDegs,
			final double courseErrorDegs, final double minSpeed,
			final double cruisingSpeed, final double maxSpeed,
			final long maxDistressRefSecs, final long minArrivalRefSecs,
			final MotionType motionType) {
		super(scenario, distressRefSecsMean, distressPlusMinusHrs);
		_courseCwFromNorthR = Math.toRadians(courseDegs);
		_courseErrorR = Math.toRadians(courseErrorDegs);
		_minSpeed = minSpeed;
		_cruisingSpeed = cruisingSpeed;
		_maxSpeed = maxSpeed;
		_motionType = motionType;
		_maxDistressRefSecs = maxDistressRefSecs;
		_minArrivalRefSecs = minArrivalRefSecs;
	}

	@Override
	public boolean deepEquals(final PreDistressModel compared) {
		if ((compared == null) || !(compared instanceof DeadReckon)) {
			return false;
		}
		final DeadReckon deadReckon = (DeadReckon) compared;
		final boolean compareValue = _courseCwFromNorthR == deadReckon._courseCwFromNorthR
				&& _courseErrorR == deadReckon._courseErrorR
				&& _minSpeed == deadReckon._minSpeed
				&& _cruisingSpeed == deadReckon._cruisingSpeed
				&& _maxSpeed == deadReckon._maxSpeed
				&& _motionType == deadReckon._motionType
				&& _maxDistressRefSecs == deadReckon._maxDistressRefSecs;
		if (!compareValue) {
			return false;
		}
		if (_distressPlusMinusHrs < 0d != deadReckon._distressPlusMinusHrs < 0d) {
			return false;
		}
		if (_distressPlusMinusHrs < 0d) {
			return true;
		}
		if (_distressPlusMinusHrs != deadReckon._distressPlusMinusHrs) {
			return false;
		}
		if (_distressRefSecsMean != deadReckon._distressRefSecsMean) {
			return false;
		}
		return true;
	}

	@Override
	public void write(final LsFormatter formatter, final Element root,
			final Model model) {
		final Element deadReckonElement = formatter.newChild(root, "DEAD_RECKON");
		if (getScenario().getNoDistress()) {
			deadReckonElement.setAttribute("noDistress", "true");
		}
		deadReckonElement.setAttribute("course",
				Math.toDegrees(_courseCwFromNorthR) + " T");
		deadReckonElement.setAttribute("courseError",
				Math.toDegrees(_courseErrorR) + " deg");
		deadReckonElement.setAttribute("minSpeed", _minSpeed + " kts");
		deadReckonElement.setAttribute("cruisingSpeed", _cruisingSpeed + " kts");
		deadReckonElement.setAttribute("maxSpeed", _maxSpeed + " kts");
		deadReckonElement.setAttribute("motion_type", _motionType.getId());
		if (_maxDistressRefSecs != Long.MAX_VALUE) {
			final Element distressElement = formatter.newChild(deadReckonElement,
					"DISTRESS_TIME");
			distressElement.setAttribute("dtg",
					TimeUtilities.formatTime(_maxDistressRefSecs, false));
			distressElement.setAttribute("dtgs",
					TimeUtilities.formatTime(_maxDistressRefSecs, true));
		}
	}

	@Override
	public ItineraryBuilder getItineraryBuilder(
			final SimCaseManager.SimCase simCase, final Area startingArea) {
		return new DeadReckonItineraryBuilder(simCase, this);
	}

	public double getCourseCwFromNorthInRads() {
		return _courseCwFromNorthR;
	}

	public double getCourseErrorInRads() {
		return _courseErrorR;
	}

	public double getCruisingSpeed() {
		return _cruisingSpeed;
	}

	public double getMaxSpeed() {
		return _maxSpeed;
	}

	public double getMinSpeed() {
		return _minSpeed;
	}

	public MotionType getMotionType() {
		return _motionType;
	}

	@Override
	public List<Area> getAreas() {
		return java.util.Collections.<Area>emptyList();
	}

	public long getMaxDistressRefSecs(final SimCaseManager.SimCase simCase) {
		return _maxDistressRefSecs;
	}

	@Override
	public long getMinArrivalRefSecs(final SimCaseManager.SimCase simCase) {
		return _minArrivalRefSecs;
	}
}
