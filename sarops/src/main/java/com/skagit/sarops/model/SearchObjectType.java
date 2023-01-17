package com.skagit.sarops.model;

import java.text.NumberFormat;

import org.w3c.dom.Element;

import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.LsFormatter;
import com.skagit.util.etopo.Etopo;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.randomx.Randomx;

/**
 * The class that represents the different search object types. A search
 * object type has name, etc., and a @link {#LeewayData}.
 */
public class SearchObjectType
		implements Comparable<SearchObjectType>, Cloneable {
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

		public LeewayData(final double gibingFrequencyPerSecond,
				final double dwlSlope, final double dwlConstant,
				final double dwlStandardDeviation, final double cwlPlusSlope,
				final double cwlPlusConstant, final double cwlPlusStandardDeviation,
				final double cwlMinusSlope, final double cwlMinusConstant,
				final double cwlMinusStandardDeviation, final double nominalSpeed,
				final boolean useRayleigh) {
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
			return _gibingFrequencyPerSecond == leewayData._gibingFrequencyPerSecond &&
					_dwlSlope == leewayData._dwlSlope &&
					_dwlConstant == leewayData._dwlConstant &&
					Math.abs(_dwlStandardDeviation -
							leewayData._dwlStandardDeviation) < 1.E-6 &&
					_cwlPlusSlope == leewayData._cwlPlusSlope &&
					_cwlPlusConstant == leewayData._cwlPlusConstant &&
					Math.abs(_cwlPlusStandardDeviation -
							leewayData._cwlPlusStandardDeviation) < 1.E-6 &&
					_cwlMinusSlope == leewayData._cwlMinusSlope &&
					_cwlMinusConstant == leewayData._cwlMinusConstant &&
					Math.abs(_cwlMinusStandardDeviation -
							leewayData._cwlMinusStandardDeviation) < 1.E-6 &&
					_nominalSpeed == leewayData._nominalSpeed &&
					_useRayleigh == leewayData._useRayleigh;
		}

	}

	public static class SurvivalData implements Cloneable {
		final private static int _MaxDraw = Integer.MAX_VALUE / 2;
		final public double _scaleInHours;
		final public double _shape;

		public SurvivalData(final double scaleInHours, final double shape) {
			_scaleInHours = scaleInHours;
			_shape = shape;
		}

		public boolean deepEquals(final SurvivalData survivalData) {
			return _scaleInHours == survivalData._scaleInHours &&
					_shape == survivalData._shape;
		}

		public long getLifeLengthAfterDistressSecs(final Randomx random) {
			if (random == null) {
				return Math.round(Math.sqrt(Integer.MAX_VALUE));
			}
			/** Make a draw for lifeLengthInHours, but cap it at 1 year. */
			final double lifeLengthInHoursX =
					random.getWeibullDraw(_scaleInHours, _shape);
			final double lifeLengthInHours =
					Math.min(365 * 24, lifeLengthInHoursX);
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

	public void setLeewayData(final double gibingFrequencyPerSecond,
			final double dwlSlope, final double dwlConstant,
			final double dwlStandardDeviation, final double cwlPlusSlope,
			final double cwlPlusConstant, final double cwlPlusStandardDeviation,
			final double cwlMinusSlope, final double cwlMinusConstant,
			final double cwlMinusStandardDeviation, final double nominalSpeed,
			final boolean useRayleigh) {
		_leewayData = new LeewayData(gibingFrequencyPerSecond, dwlSlope,
				dwlConstant, dwlStandardDeviation, cwlPlusSlope, cwlPlusConstant,
				cwlPlusStandardDeviation, cwlMinusSlope, cwlMinusConstant,
				cwlMinusStandardDeviation, nominalSpeed, useRayleigh);
	}

	public void setSurvivalData(final double scaleInHours,
			final double shape) {
		_survivalData = new SurvivalData(scaleInHours, shape);
	}

	@Override
	public int compareTo(final SearchObjectType other) {
		return _id - other._id;
	}

	public LeewayData getLeewayData() {
		return _leewayData;
	}

	public SurvivalData getSurvivalData() {
		return _survivalData;
	}

	private static Element createComponentElement(final LsFormatter formatter,
			final Element leewayElement, final NumberFormat format,
			final String elementName, final double slope, final double constant,
			final double standardDeviation, final boolean useRayleigh) {
		final Element componentElement =
				formatter.newChild(leewayElement, elementName);
		componentElement.setAttribute("slope",
				LsFormatter.StandardFormat(slope));
		componentElement.setAttribute("constant",
				LsFormatter.StandardFormat(constant) + " kts");
		componentElement.setAttribute("useRayleigh",
				LsFormatter.StandardFormat(useRayleigh));
		componentElement.setAttribute("Syx",
				format.format(standardDeviation) + " kts");
		return componentElement;
	}

	private static Element createComponentElement(final LsFormatter formatter,
			final Element leewayElement, final NumberFormat format,
			final String elementName, final double slope, final double constant,
			final double standardDeviation) {
		final Element componentElement =
				formatter.newChild(leewayElement, elementName);
		componentElement.setAttribute("slope",
				LsFormatter.StandardFormat(slope));
		componentElement.setAttribute("constant",
				LsFormatter.StandardFormat(constant) + " kts");
		componentElement.setAttribute("Syx",
				format.format(standardDeviation) + " kts");
		return componentElement;
	}

	public void write(final LsFormatter formatter, final Element root) {
		final NumberFormat format = java.text.NumberFormat.getInstance();
		format.setMaximumFractionDigits(8);
		final String tag;
		if (_leewayData == null && _id < 0) {
			tag = "ORIGINATING_CRAFT";
		} else {
			tag = "SEARCH_OBJECT_TYPE";
		}
		final Element element = formatter.newChild(root, tag);
		element.setAttribute("id", LsFormatter.StandardFormat(_id));
		if (_name != null) {
			element.setAttribute("name", _name);
		}
		/** ProbabilityOfAnchoring and maximumAnchorableDepthInMeters */
		final String probabilityOfAnchoringString =
				String.format("%.2f %%", _probabilityOfAnchoring * 100);
		element.setAttribute("probabilityOfAnchoring",
				probabilityOfAnchoringString);
		final String maximumAnchorDepthString =
				String.format("%f M", _maximumAnchorDepthInM);
		element.setAttribute("maximumAnchorDepth", maximumAnchorDepthString);
		final String alwaysAnchorString = _alwaysAnchor ? "true" : "false";
		element.setAttribute("alwaysAnchor", alwaysAnchorString);
		/**
		 * Survival data goes in the object tag; leeway data goes in its own
		 * subtags.
		 */
		if (_survivalData == null && _id < 0) {
			element.setAttribute("SurvivalData", "false");
		} else {
			element.setAttribute("scale",
					LsFormatter.StandardFormat(_survivalData._scaleInHours) + " hrs");
			element.setAttribute("shape",
					LsFormatter.StandardFormat(_survivalData._shape));
		}
		final Element leewayElement = formatter.newChild(element, "LEEWAY");
		if (_leewayData == null && _id < 0) {
			leewayElement.setAttribute("LeewayData", "false");
		} else {
			leewayElement.setAttribute("nominalSpeed",
					LsFormatter.StandardFormat(_leewayData._nominalSpeed) + " kts");
			leewayElement.setAttribute("gibingRate",
					String.valueOf(
							_leewayData._gibingFrequencyPerSecond * 3600.0f * 100.0f) +
							"% perHr");
			createComponentElement(formatter, leewayElement, format, "DWL",
					_leewayData._dwlSlope, _leewayData._dwlConstant,
					_leewayData._dwlStandardDeviation, _leewayData._useRayleigh);
			createComponentElement(formatter, leewayElement, format, "CWLPOS",
					_leewayData._cwlPlusSlope, _leewayData._cwlPlusConstant,
					_leewayData._cwlPlusStandardDeviation);
			createComponentElement(formatter, leewayElement, format, "CWLNEG",
					_leewayData._cwlMinusSlope, _leewayData._cwlMinusConstant,
					_leewayData._cwlMinusStandardDeviation);
		}
	}

	public int getId() {
		return _id;
	}

	public String getName() {
		return _name;
	}

	public boolean deepEquals(final SearchObjectType compared) {
		if (((_leewayData == null) != (compared._leewayData == null)) || (_leewayData != null &&
				!_leewayData.deepEquals(compared._leewayData))) {
			return false;
		}

		if (_id == compared._id) {
			if (_probabilityOfAnchoring == compared._probabilityOfAnchoring &&
					_maximumAnchorDepthInM == compared._maximumAnchorDepthInM &&
					_alwaysAnchor == compared._alwaysAnchor) {
				return true;
			}
		}
		return false;
	}

	public boolean check(final SimCaseManager.SimCase simCase) {
		if (_leewayData == null) {
			SimCaseManager.err(simCase, String
					.format("No leeway defined for search object type[%d]", _id));
			return false;
		}
		return true;
	}

	public void setMaximumAnchorDepthM(final float maximumAnchorDepthInM) {
		_maximumAnchorDepthInM = maximumAnchorDepthInM;
	}

	public void setProbabilityOfAnchoring(
			final double probabilityOfAnchoring) {
		_probabilityOfAnchoring = probabilityOfAnchoring;
	}

	public void setAlwaysAnchor(final boolean alwaysAnchor) {
		_alwaysAnchor = alwaysAnchor;
	}

	public boolean mightAnchor() {
		return _alwaysAnchor || _probabilityOfAnchoring > 0d;
	}

	public boolean anchors(final LatLng3 latLng, final double randomDraw,
			final Etopo etopo) {
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
