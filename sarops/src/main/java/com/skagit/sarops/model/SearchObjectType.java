package com.skagit.sarops.model;

import java.text.NumberFormat;

import org.w3c.dom.Element;

import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.LsFormatter;
import com.skagit.util.etopo.Etopo;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.randomx.Randomx;

public class SearchObjectType implements Comparable<SearchObjectType>, Cloneable {
	final static NumberFormat _Format = java.text.NumberFormat.getInstance();
	static {
		_Format.setMaximumFractionDigits(8);
	}

	final private int _id;
	final private String _name;
	private LeewayData _leewayData;
	private SurvivalData _survivalData;
	private float _maximumAnchorDepthInM;
	private double _probabilityOfAnchoring;
	private boolean _alwaysAnchor;

	public static class LeewayData implements Cloneable {
		final public double _gibingFrequencyPerSecond;
		final public double _dwlSlope;
		final public double _dwlConstant;
		final public double _dwlStandardDeviation;
		final public double _cwlPlusSlope;
		final public double _cwlPlusConstant;
		final public double _cwlPlusStandardDeviation;
		final public double _cwlMinusSlope;
		final public double _cwlMinusConstant;
		final public double _cwlMinusStandardDeviation;
		final public double _nominalSpeed;
		final public boolean _useRayleigh;

		public LeewayData(final double gibingFrequencyPerSecond, final double dwlSlope, final double dwlConstant,
				final double dwlStandardDeviation, final double cwlPlusSlope, final double cwlPlusConstant,
				final double cwlPlusStandardDeviation, final double cwlMinusSlope, final double cwlMinusConstant,
				final double cwlMinusStandardDeviation, final double nominalSpeed, final boolean useRayleigh) {
			_gibingFrequencyPerSecond = gibingFrequencyPerSecond;
			_dwlSlope = dwlSlope;
			_dwlConstant = dwlConstant;
			_useRayleigh = useRayleigh;
			_dwlStandardDeviation = dwlStandardDeviation;
			_cwlPlusSlope = cwlPlusSlope;
			_cwlPlusConstant = cwlPlusConstant;
			_cwlPlusStandardDeviation = cwlPlusStandardDeviation;
			_cwlMinusSlope = cwlMinusSlope;
			_cwlMinusConstant = cwlMinusConstant;
			_cwlMinusStandardDeviation = cwlMinusStandardDeviation;
			_nominalSpeed = nominalSpeed;
		}

		public boolean deepEquals(final LeewayData leewayData) {
			return _gibingFrequencyPerSecond == leewayData._gibingFrequencyPerSecond
					&& _dwlSlope == leewayData._dwlSlope && _dwlConstant == leewayData._dwlConstant
					&& Math.abs(_dwlStandardDeviation - leewayData._dwlStandardDeviation) < 1.E-6
					&& _cwlPlusSlope == leewayData._cwlPlusSlope && _cwlPlusConstant == leewayData._cwlPlusConstant
					&& Math.abs(_cwlPlusStandardDeviation - leewayData._cwlPlusStandardDeviation) < 1.E-6
					&& _cwlMinusSlope == leewayData._cwlMinusSlope && _cwlMinusConstant == leewayData._cwlMinusConstant
					&& Math.abs(_cwlMinusStandardDeviation - leewayData._cwlMinusStandardDeviation) < 1.E-6
					&& _nominalSpeed == leewayData._nominalSpeed && _useRayleigh == leewayData._useRayleigh;
		}

	}

	public static class SurvivalData implements Cloneable {
		final private static int _MaxDraw = Integer.MAX_VALUE / 2;
		final public double _scaleInHours;
		final public double _shape;

		public SurvivalData(final double scaleInHours, final double shape) {
			if (Double.isFinite(scaleInHours) && Double.isFinite(shape)) {
				_scaleInHours = scaleInHours;
				_shape = shape;
			} else {
				_scaleInHours = _shape = Double.MAX_VALUE;
			}
		}

		public boolean deepEquals(final SurvivalData survivalData) {
			return _scaleInHours == survivalData._scaleInHours && _shape == survivalData._shape;
		}

		public long getLifeLengthAfterDistressSecs(final Randomx random) {
			if (random == null || _scaleInHours == Double.MAX_VALUE || _shape == Double.MAX_VALUE) {
				return Math.round(Math.sqrt(Integer.MAX_VALUE));
			}
			/** Make a draw for lifeLengthInHours, but cap it at 1 year. */
			final double lifeLengthInHoursX = random.getWeibullDraw(_scaleInHours, _shape);
			final double lifeLengthInHours = Math.min(365 * 24, lifeLengthInHoursX);
			final double seconds = 3600.0 * lifeLengthInHours;
			if (seconds >= _MaxDraw) {
				return _MaxDraw;
			}
			return Math.round(seconds);
		}
	}

	public SearchObjectType(final int id, final String name) {
		_id = id;
		_name = name;
		_leewayData = null;
		_maximumAnchorDepthInM = 0f;
		_probabilityOfAnchoring = 0d;
		_alwaysAnchor = false;
		_survivalData = null;
	}

	public boolean isDebris() {
		return false;
	}

	public void setLeewayData(final LeewayData leewayData) {
		_leewayData = leewayData;
	}

	public void setSurvivalData(final double scaleInHours, final double shape) {
		_survivalData = new SurvivalData(scaleInHours, shape);
	}

	@Override
	public int compareTo(final SearchObjectType other) {
		return _id < other._id ? -1 : (_id > other._id ? 1 : 0);
	}

	public LeewayData getLeewayData() {
		return _leewayData;
	}

	public SurvivalData getSurvivalData() {
		return _survivalData;
	}

	static Element createComponentElement(final LsFormatter formatter, final Element leewayElement,
			final String elementName, final double slope, final double constant, final double standardDeviation,
			final boolean useRayleigh, final boolean writeRayleighBoolean) {
		final Element componentElement = formatter.newChild(leewayElement, elementName);
		componentElement.setAttribute("slope", LsFormatter.StandardFormat(slope));
		componentElement.setAttribute("constant", LsFormatter.StandardFormat(constant) + " kts");
		if (writeRayleighBoolean) {
			componentElement.setAttribute("useRayleigh", LsFormatter.StandardFormat(useRayleigh));
		}
		componentElement.setAttribute("Syx", _Format.format(standardDeviation) + " kts");
		return componentElement;
	}

	static Element createLeewayElement(final LsFormatter formatter, final Element element, final LeewayData leewayData,
			final int id) {
		final Element leewayElement = formatter.newChild(element, "LEEWAY");
		if (leewayData == null || id < 0) {
			leewayElement.setAttribute("LeewayData", "false");
		} else {
			leewayElement.setAttribute("nominalSpeed", LsFormatter.StandardFormat(leewayData._nominalSpeed) + " kts");
			leewayElement.setAttribute("gibingRate",
					String.valueOf(leewayData._gibingFrequencyPerSecond * 3600.0f * 100.0f) + "% perHr");
			SearchObjectType.createComponentElement(formatter, leewayElement, "DWL", leewayData._dwlSlope,
					leewayData._dwlConstant, leewayData._dwlStandardDeviation, leewayData._useRayleigh,
					/* writeRayleigh= */true);
			SearchObjectType.createComponentElement(formatter, leewayElement, "CWLPOS", leewayData._cwlPlusSlope,
					leewayData._cwlPlusConstant, leewayData._cwlPlusStandardDeviation, /* useRayleigh= */false,
					/* writeRayleigh= */false);
			SearchObjectType.createComponentElement(formatter, leewayElement, "CWLNEG", leewayData._cwlMinusSlope,
					leewayData._cwlMinusConstant, leewayData._cwlMinusStandardDeviation, /* useRayleigh= */false,
					/* writeRayleigh= */false);
		}
		return leewayElement;
	}

	public void write(final LsFormatter formatter, final Element root) {
		final String tag;
		if (_leewayData == null && _id < 0) {
			tag = "ORIGINATING_CRAFT";
		} else if (!isDebris()) {
			tag = "SEARCH_OBJECT_TYPE";
		} else {
			tag = "DEBRIS_OBJECT_TYPE";
		}
		final Element element = formatter.newChild(root, tag);
		element.setAttribute("id", LsFormatter.StandardFormat(_id));
		if (_name != null) {
			element.setAttribute("name", _name);
		}
		if (!isDebris()) {
			/** ProbabilityOfAnchoring and maximumAnchorableDepthInMeters */
			final String probabilityOfAnchoringString = String.format("%.2f %%", _probabilityOfAnchoring * 100);
			element.setAttribute("probabilityOfAnchoring", probabilityOfAnchoringString);
			final String maximumAnchorDepthString = String.format("%f M", _maximumAnchorDepthInM);
			element.setAttribute("maximumAnchorDepth", maximumAnchorDepthString);
			final String alwaysAnchorString = _alwaysAnchor ? "true" : "false";
			element.setAttribute("alwaysAnchor", alwaysAnchorString);
			if (_survivalData == null && _id < 0) {
				element.setAttribute("SurvivalData", "false");
			} else {
				element.setAttribute("scale", LsFormatter.StandardFormat(_survivalData._scaleInHours) + " hrs");
				element.setAttribute("shape", LsFormatter.StandardFormat(_survivalData._shape));
			}
		}
		createLeewayElement(formatter, element, _leewayData, _id);
	}

	public int getId() {
		return _id;
	}

	public String getName() {
		return _name;
	}

	public boolean deepEquals(final SearchObjectType other) {
		if (_id != other._id || !_name.equals(other._name) || isDebris() != other.isDebris()) {
			return false;
		}
		if (((_leewayData == null) != (other._leewayData == null))
				|| (_leewayData != null && !_leewayData.deepEquals(other._leewayData))) {
			return false;
		}
		if (isDebris()) {
			return true;
		}
		return _probabilityOfAnchoring == other._probabilityOfAnchoring
				&& _maximumAnchorDepthInM == other._maximumAnchorDepthInM && _alwaysAnchor == other._alwaysAnchor;
	}

	public boolean check(final SimCaseManager.SimCase simCase) {
		if (_leewayData == null) {
			SimCaseManager.err(simCase,
					String.format("No leeway defined for " + (isDebris() ? "DOT" : "SOT") + "[%d]", _id));
			return false;
		}
		return true;
	}

	public void setMaximumAnchorDepthM(final float maximumAnchorDepthInM) {
		_maximumAnchorDepthInM = maximumAnchorDepthInM;
	}

	public void setProbabilityOfAnchoring(final double probabilityOfAnchoring) {
		_probabilityOfAnchoring = probabilityOfAnchoring;
	}

	public void setAlwaysAnchor(final boolean alwaysAnchor) {
		_alwaysAnchor = alwaysAnchor;
	}

	public boolean mightAnchor() {
		return _alwaysAnchor || _probabilityOfAnchoring > 0d;
	}

	public boolean anchors(final LatLng3 latLng, final double randomDraw, final Etopo etopo) {
		if (_alwaysAnchor) {
			return true;
		}
		if (etopo == null || !etopo.getIsValid() || (_probabilityOfAnchoring == 0d)) {
			return false;
		}
		final float depthInM = etopo.getDepthM(latLng);
		if (!(depthInM <= _maximumAnchorDepthInM)) {
			return false;
		}
		final boolean anchors = randomDraw < _probabilityOfAnchoring;
		return anchors;
	}

	public float getMaximumAnchorDepthInM() {
		return _maximumAnchorDepthInM;
	}

	public String getString() {
		return String.format("SO-Name/ID[%s/%d]", _name, _id);
	}

	@Override
	public String toString() {
		return getString();
	}
}
