package com.skagit.sarops.tracker.lrcSet;

import java.util.TreeSet;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.skagit.sarops.model.io.ModelReader;
import com.skagit.sarops.model.io.ModelReader.ReaderException;
import com.skagit.sarops.model.io.ModelReader.StringPlus;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.CpaCalculator.Result;
import com.skagit.sarops.util.AnnulusIntersector;
import com.skagit.sarops.util.patternUtils.LegInfo;
import com.skagit.util.LsFormatter;
import com.skagit.util.navigation.TangentCylinder.FlatLatLng;

class LrcCore {

	final private static double _MaxRange = 100d;
	final protected static String _RangeUnits = "NM";
	final protected static String _LkAnglUnits = "degs";
	final protected static String _SubTypeAttributeName = "SubType";
	final protected static String _ApplyToCrossLegsAttributeName =
			"ApplyToCrossLegs";

	protected static SubType getSubType(final String shortName) {
		for (final SubType subType : SubType.values()) {
			if (subType._xmlString.equalsIgnoreCase(shortName)) {
				return subType;
			}
		}
		return null;
	}

	final static private DoubleAttribute[] _DoubleAttributes =
			DoubleAttribute.values();

	private static DoubleAttribute getDoubleAttribute(
			final String shortName) {
		for (final DoubleAttribute doubleAttributeName : _DoubleAttributes) {
			if (doubleAttributeName._attributeName.equalsIgnoreCase(shortName)) {
				return doubleAttributeName;
			}
		}
		return null;
	}

	private static class FilterDoubles {
		double _upMinRange;
		double _upMaxRange;
		double _upMinLkAngl;
		double _upMaxLkAngl;
		double _dnMinRange;
		double _dnMaxRange;
		double _dnMinLkAngl;
		double _dnMaxLkAngl;
		double _ltMinRange;
		double _ltMaxRange;
		double _ltMinLkAngl;
		double _ltMaxLkAngl;
		double _rtMinRange;
		double _rtMaxRange;
		double _rtMinLkAngl;
		double _rtMaxLkAngl;
		double _maxRangeForDetect;

		private FilterDoubles() {
			_upMinRange = _upMaxRange = _upMinLkAngl = _upMaxLkAngl = Double.NaN;
			_dnMinRange = _dnMaxRange = _dnMinLkAngl = _dnMaxLkAngl = Double.NaN;
			_ltMinRange = _ltMaxRange = _ltMinLkAngl = _ltMaxLkAngl = Double.NaN;
			_rtMinRange = _rtMaxRange = _rtMinLkAngl = _rtMaxLkAngl = Double.NaN;
			_maxRangeForDetect = Double.POSITIVE_INFINITY;
		}

		private void set(final DoubleAttribute doubleAttribute,
				final double d0) {
			final double d;
			if (Double.isNaN(d0)) {
				d = Double.NaN;
			} else if (doubleAttribute._isRange) {
				d = Math.max(0d, d0);
			} else {
				d = Math.max(0d, Math.min(d0, 180d));
			}
			switch (doubleAttribute) {
			case DN_MAX_LK_ANGL:
				_dnMaxLkAngl = d;
				return;
			case DN_MAX_RANGE:
				_dnMaxRange = d;
				return;
			case DN_MIN_LK_ANGL:
				_dnMinLkAngl = d;
				return;
			case DN_MIN_RANGE:
				_dnMinRange = d;
				return;
			case UP_MAX_LK_ANGL:
				_upMaxLkAngl = d;
				return;
			case UP_MAX_RANGE:
				_upMaxRange = d;
				return;
			case UP_MIN_LK_ANGL:
				_upMinLkAngl = d;
				return;
			case UP_MIN_RANGE:
				_upMinRange = d;
				return;
			case LT_MAX_LK_ANGL:
				_ltMaxLkAngl = d;
				return;
			case LT_MAX_RANGE:
				_ltMaxRange = d;
				return;
			case LT_MIN_LK_ANGL:
				_ltMinLkAngl = d;
				return;
			case LT_MIN_RANGE:
				_ltMinRange = d;
				return;
			case RT_MAX_LK_ANGL:
				_rtMaxLkAngl = d;
				return;
			case RT_MAX_RANGE:
				_rtMaxRange = d;
				return;
			case RT_MIN_LK_ANGL:
				_rtMinLkAngl = d;
				return;
			case RT_MIN_RANGE:
				_rtMinRange = d;
				return;
			default:
				return;
			}
		}

		private double get(final DoubleAttribute doubleAttribute) {
			switch (doubleAttribute) {
			case DN_MAX_LK_ANGL:
				return _dnMaxLkAngl;
			case DN_MIN_LK_ANGL:
				return _dnMinLkAngl;
			case UP_MAX_LK_ANGL:
				return _upMaxLkAngl;
			case UP_MIN_LK_ANGL:
				return _upMinLkAngl;
			case LT_MAX_LK_ANGL:
				return _ltMaxLkAngl;
			case LT_MIN_LK_ANGL:
				return _ltMinLkAngl;
			case RT_MAX_LK_ANGL:
				return _rtMaxLkAngl;
			case RT_MIN_LK_ANGL:
				return _rtMinLkAngl;
			case DN_MIN_RANGE:
				return _dnMinRange;
			case UP_MIN_RANGE:
				return _upMinRange;
			case LT_MIN_RANGE:
				return _ltMinRange;
			case RT_MIN_RANGE:
				return _rtMinRange;
			case DN_MAX_RANGE:
				return Math.min(_maxRangeForDetect, _dnMaxRange);
			case UP_MAX_RANGE:
				return Math.min(_maxRangeForDetect, _upMaxRange);
			case LT_MAX_RANGE:
				return Math.min(_maxRangeForDetect, _ltMaxRange);
			case RT_MAX_RANGE:
				return Math.min(_maxRangeForDetect, _rtMaxRange);
			default:
				return 0d;
			}
		}

		public String getString() {
			String s = "";
			final int nDoubleAttributes = _DoubleAttributes.length;
			for (int k = 0; k < nDoubleAttributes; ++k) {
				final DoubleAttribute doubleAttribute = _DoubleAttributes[k];
				switch (doubleAttribute) {
				case DN_MAX_LK_ANGL:
					s = addToString(s, doubleAttribute, _dnMaxLkAngl);
					break;
				case DN_MAX_RANGE:
					s = addToString(s, doubleAttribute, _dnMaxRange);
					break;
				case DN_MIN_LK_ANGL:
					s = addToString(s, doubleAttribute, _dnMinLkAngl);
					break;
				case DN_MIN_RANGE:
					s = addToString(s, doubleAttribute, _dnMinRange);
					break;
				case LT_MAX_LK_ANGL:
					s = addToString(s, doubleAttribute, _ltMaxLkAngl);
					break;
				case LT_MAX_RANGE:
					s = addToString(s, doubleAttribute, _ltMaxRange);
					break;
				case LT_MIN_LK_ANGL:
					s = addToString(s, doubleAttribute, _ltMinLkAngl);
					break;
				case LT_MIN_RANGE:
					s = addToString(s, doubleAttribute, _ltMinRange);
					break;
				case RT_MAX_LK_ANGL:
					s = addToString(s, doubleAttribute, _rtMaxLkAngl);
					break;
				case RT_MAX_RANGE:
					s = addToString(s, doubleAttribute, _rtMaxRange);
					break;
				case RT_MIN_LK_ANGL:
					s = addToString(s, doubleAttribute, _rtMinLkAngl);
					break;
				case RT_MIN_RANGE:
					s = addToString(s, doubleAttribute, _rtMinRange);
					break;
				case UP_MAX_LK_ANGL:
					s = addToString(s, doubleAttribute, _upMaxLkAngl);
					break;
				case UP_MAX_RANGE:
					s = addToString(s, doubleAttribute, _upMaxRange);
					break;
				case UP_MIN_LK_ANGL:
					s = addToString(s, doubleAttribute, _upMinLkAngl);
					break;
				case UP_MIN_RANGE:
					s = addToString(s, doubleAttribute, _upMinRange);
					break;
				default:
					break;
				}
			}
			return s;
		}

		@Override
		public String toString() {
			return getString();
		}

		private static String addToString(String s,
				final DoubleAttribute doubleAttribute, final double v) {
			if (!Double.isNaN(v)) {
				if (s.length() > 0) {
					s += "\n";
				}
				s += String.format("\t%s[%.2f]", doubleAttribute._attributeName, v);
			}
			return s;
		}
	}

	public enum DoubleAttribute {
		UP_MIN_RANGE("UpCreepMinRange", _RangeUnits, true), //
		UP_MAX_RANGE("UpCreepMaxRange", _RangeUnits, true), //
		UP_MIN_LK_ANGL("UpCreepMinLookAngle", _LkAnglUnits, false), //
		UP_MAX_LK_ANGL("UpCreepMaxLookAngle", _LkAnglUnits, false), //
		DN_MIN_RANGE("DownCreepMinRange", _RangeUnits, true), //
		DN_MAX_RANGE("DownCreepMaxRange", _RangeUnits, true), //
		DN_MIN_LK_ANGL("DownCreepMinLookAngle", _LkAnglUnits, false), //
		DN_MAX_LK_ANGL("DownCreepMaxLookAngle", _LkAnglUnits, false), //
		LT_MIN_RANGE("LeftMinRange", _RangeUnits, true), //
		LT_MAX_RANGE("LeftMaxRange", _RangeUnits, true), //
		LT_MIN_LK_ANGL("LeftMinLookAngle", _LkAnglUnits, false), //
		LT_MAX_LK_ANGL("LeftMaxLookAngle", _LkAnglUnits, false), //
		RT_MIN_RANGE("RightMinRange", _RangeUnits, true), //
		RT_MAX_RANGE("RightMaxRange", _RangeUnits, true), //
		RT_MIN_LK_ANGL("RightMinLookAngle", _LkAnglUnits, false), //
		RT_MAX_LK_ANGL("RightMaxLookAngle", _LkAnglUnits, false); //

		final public String _attributeName;
		final public String _units;
		final public boolean _isRange;

		DoubleAttribute(final String attributeName, final String units,
				final boolean isRange) {
			_attributeName = attributeName;
			_units = units;
			_isRange = isRange;
		}
	}

	final public static DoubleAttribute[] _LtRtDoubleAttributes =
			new DoubleAttribute[] { DoubleAttribute.LT_MIN_RANGE,
					DoubleAttribute.LT_MAX_RANGE, DoubleAttribute.LT_MIN_LK_ANGL,
					DoubleAttribute.LT_MAX_LK_ANGL, DoubleAttribute.RT_MIN_RANGE,
					DoubleAttribute.RT_MAX_RANGE, DoubleAttribute.RT_MIN_LK_ANGL,
					DoubleAttribute.RT_MAX_LK_ANGL };
	final public static DoubleAttribute[] _UpDnDoubleAttributes =
			new DoubleAttribute[] { DoubleAttribute.UP_MIN_RANGE,
					DoubleAttribute.UP_MAX_RANGE, DoubleAttribute.UP_MIN_LK_ANGL,
					DoubleAttribute.UP_MAX_LK_ANGL, DoubleAttribute.DN_MIN_RANGE,
					DoubleAttribute.DN_MAX_RANGE, DoubleAttribute.DN_MIN_LK_ANGL,
					DoubleAttribute.DN_MAX_LK_ANGL };

	final protected SubType _subType;
	protected final double _distinctDetectionThresholdMins;
	protected FilterDoubles _filterDoubles;
	protected boolean _applyToCrossLegs;

	/** Builds no-Filters. */
	protected LrcCore(final SubType subType,
			final double distinctDetectionThresholdMins) {
		_distinctDetectionThresholdMins = distinctDetectionThresholdMins;
		_subType = subType;
		_filterDoubles = new FilterDoubles();
		setNoFilters();
	}

	private void setNoFilters() {
		setUpSide(Double.NaN, Double.NaN, Double.NaN, Double.NaN,
				/* doCleanUp= */false);
		setDnSide(Double.NaN, Double.NaN, Double.NaN, Double.NaN,
				/* doCleanUp= */false);
		setLtSide(0d, Double.POSITIVE_INFINITY, 0d, 180d,
				/* doCleanUp= */false);
		setRtSide(0d, Double.POSITIVE_INFINITY, 0d, 180d,
				/* doCleanUp= */false);
		_applyToCrossLegs = true;
	}

	private void cleanUpFields() {
		final boolean isUp = isUp();
		final boolean isDn = isDn();
		if (isUp || isDn) {
			if (!isUp) {
				setUpSide(0d, 0d, 0d, 0d, /* doCleanUp= */false);
			}
			if (!isDn) {
				setDnSide(0d, 0d, 0d, 0d, /* doCleanUp= */false);
			}
			setLtSide(Double.NaN, Double.NaN, Double.NaN, Double.NaN,
					/* doCleanUp= */false);
			setRtSide(Double.NaN, Double.NaN, Double.NaN, Double.NaN,
					/* doCleanUp= */false);
			/** No cross legs. */
			_applyToCrossLegs = false;
			return;
		}
		final boolean isLt = isLt();
		final boolean isRt = isRt();
		if (isLt || isRt) {
			if (!isLt) {
				setLtSide(0d, 0d, 0d, 0d, /* doCleanUp= */false);
			}
			if (!isRt) {
				setRtSide(0d, 0d, 0d, 0d, /* doCleanUp= */false);
			}
		}
	}

	protected void setUpSide(final double upMinRange, final double upMaxRange,
			final double upMinLkAngl, final double upMaxLkAngl) {
		setUpSide(upMinRange, upMaxRange, upMinLkAngl, upMaxLkAngl,
				/* doCleanUp= */true);
	}

	private void setUpSide(final double upMinRange, final double upMaxRange,
			final double upMinLkAngl, final double upMaxLkAngl,
			final boolean doCleanUp) {
		setUpMinRange(upMinRange);
		setUpMaxRange(upMaxRange);
		setUpMinLkAngl(upMinLkAngl);
		setUpMaxLkAngl(upMaxLkAngl);
		if (doCleanUp) {
			cleanUpFields();
		}
	}

	protected void setDnSide(final double dnMinRange, final double dnMaxRange,
			final double dnMinLkAngl, final double dnMaxLkAngl) {
		setDnSide(dnMinRange, dnMaxRange, dnMinLkAngl, dnMaxLkAngl,
				/* doCleanUp= */true);
	}

	private void setDnSide(final double dnMinRange, final double dnMaxRange,
			final double dnMinLkAngl, final double dnMaxLkAngl,
			final boolean doCleanUp) {
		setDnMinRange(dnMinRange);
		setDnMaxRange(dnMaxRange);
		setDnMinLkAngl(dnMinLkAngl);
		setDnMaxLkAngl(dnMaxLkAngl);
		if (doCleanUp) {
			cleanUpFields();
		}
	}

	protected void setLtSide(final double ltMinRange, final double ltMaxRange,
			final double ltMinLkAngl, final double ltMaxLkAngl) {
		setLtSide(ltMinRange, ltMaxRange, ltMinLkAngl, ltMaxLkAngl,
				/* doCleanUp= */true);
	}

	protected void setLtSide(final double ltMinRange, final double ltMaxRange,
			final double ltMinLkAngl, final double ltMaxLkAngl,
			final boolean doCleanUp) {
		setLtMinRange(ltMinRange);
		setLtMaxRange(ltMaxRange);
		setLtMinLkAngl(ltMinLkAngl);
		setLtMaxLkAngl(ltMaxLkAngl);
		if (doCleanUp) {
			cleanUpFields();
		}
	}

	protected void setRtSide(final double rtMinRange, final double rtMaxRange,
			final double rtMinLkAngl, final double rtMaxLkAngl) {
		setRtSide(rtMinRange, rtMaxRange, rtMinLkAngl, rtMaxLkAngl,
				/* doCleanUp= */true);
	}

	private void setRtSide(final double rtMinRange, final double rtMaxRange,
			final double rtMinLkAngl, final double rtMaxLkAngl,
			final boolean doCleanUp) {
		setRtMinRange(rtMinRange);
		setRtMaxRange(rtMaxRange);
		setRtMinLkAngl(rtMinLkAngl);
		setRtMaxLkAngl(rtMaxLkAngl);
		if (doCleanUp) {
			cleanUpFields();
		}
	}

	protected void setApplyToCrossLegs(final boolean applyToCrossLegs) {
		_applyToCrossLegs = applyToCrossLegs;
	}

	protected LrcCore(final SimCaseManager.SimCase simCase,
			final SubType subType, final Element element,
			final TreeSet<StringPlus> stringPluses) {
		_subType = subType;
		/** Get the distinct detection threshold. */
		{
			double distinctDetectionThresholdMins = 1d;
			try {
				distinctDetectionThresholdMins = ModelReader.getDouble(simCase,
						element, ModelReader._DistinctDetectionThresholdAtt,
						ModelReader._DistinctDetectionThresholdAttUnits,
						distinctDetectionThresholdMins, stringPluses);
			} catch (final ReaderException e) {
			}
			_distinctDetectionThresholdMins = distinctDetectionThresholdMins;
		}

		/** Read in _filterDoubles. */
		_filterDoubles = new FilterDoubles();
		boolean noFilters = true;
		final NamedNodeMap namedNodeMap = element.getAttributes();
		final int len = namedNodeMap.getLength();
		for (int k1 = 0; k1 < len; ++k1) {
			final Node nd = namedNodeMap.item(k1);
			if (nd.getNodeType() == Node.ATTRIBUTE_NODE) {
				final String nodeName = nd.getNodeName();
				final DoubleAttribute doubleAttribute =
						getDoubleAttribute(nodeName);
				if (doubleAttribute != null) {
					try {
						final double d = ModelReader.getDouble(simCase, element,
								doubleAttribute._attributeName, doubleAttribute._units,
								Double.NaN, stringPluses);
						if (d >= 0d) {
							_filterDoubles.set(doubleAttribute, d);
							noFilters = false;
						}
					} catch (final ReaderException ignored) {
					}
				}
			}
		}
		if (noFilters) {
			setNoFilters();
		}
		/** Every double in _filterDoubles is now either NaN or nonnegative. */
		cleanUpFields();
		if (isLtRt()) {
			boolean applyToCrossLegs = true;
			try {
				applyToCrossLegs = ModelReader.getBoolean(simCase, element,
						_ApplyToCrossLegsAttributeName, stringPluses);
			} catch (final ReaderException e) {
			}
			_applyToCrossLegs = applyToCrossLegs;
		}
	}

	public boolean isUpDn() {
		return isUp() || isDn();
	}

	protected boolean symmetric() {
		if (isLtRt()) {
			if (getLtMinRange() != getRtMinRange()) {
				return false;
			}
			if (getLtMaxRange() != getRtMaxRange()) {
				return false;
			}
			if (getLtMinLkAngl() != getRtMinLkAngl()) {
				return false;
			}
			if (getLtMaxLkAngl() != getRtMaxLkAngl()) {
				return false;
			}
			return true;
		}
		if ((getUpMinRange() != getDnMinRange()) || (getUpMaxRange() != getDnMaxRange()) || (getUpMinLkAngl() != getDnMinLkAngl()) || (getUpMaxLkAngl() != getDnMaxLkAngl())) {
			return false;
		}
		return true;
	}

	private boolean isUp() {
		final double upMinRange = getUpMinRange();
		final double upMaxRange = getUpMaxRange();
		if (((0d > upMinRange) || (upMinRange >= upMaxRange))) {
			return false;
		}
		final double upMinLkAngl = getUpMinLkAngl();
		final double upMaxLkAngl = getUpMaxLkAngl();
		return 0d <= upMinLkAngl && upMinLkAngl < upMaxLkAngl;
	}

	private boolean isDn() {
		final double dnMinRange = getDnMinRange();
		final double dnMaxRange = getDnMaxRange();
		if (((0d > dnMinRange) || (dnMinRange >= dnMaxRange))) {
			return false;
		}
		final double dnMinLkAngl = getDnMinLkAngl();
		final double dnMaxLkAngl = getDnMaxLkAngl();
		return 0d <= dnMinLkAngl && dnMinLkAngl < dnMaxLkAngl;
	}

	private boolean isLt() {
		final double ltMinRange = getLtMinRange();
		final double ltMaxRange = getLtMaxRange();
		if (((0d > ltMinRange) || (ltMinRange >= ltMaxRange))) {
			return false;
		}
		final double ltMinLkAngl = getLtMinLkAngl();
		final double ltMaxLkAngl = getLtMaxLkAngl();
		return 0d <= ltMinLkAngl && ltMinLkAngl < ltMaxLkAngl;
	}

	private boolean isRt() {
		final double rtMinRange = getRtMinRange();
		final double rtMaxRange = getRtMaxRange();
		if (((0d > rtMinRange) || (rtMinRange >= rtMaxRange))) {
			return false;
		}
		final double rtMinLkAngl = getRtMinLkAngl();
		final double rtMaxLkAngl = getRtMaxLkAngl();
		return 0d <= rtMinLkAngl && rtMinLkAngl < rtMaxLkAngl;
	}

	public boolean isLtRt() {
		return isLt() || isRt();
	}

	protected void setMaxRangeForDetect(final double maxRangeForDetect) {
		_filterDoubles._maxRangeForDetect = maxRangeForDetect;
	}

	/** basic getters. */
	public double getUpMinRange() {
		return _filterDoubles.get(DoubleAttribute.UP_MIN_RANGE);
	}

	public double getUpMaxRange() {
		return _filterDoubles.get(DoubleAttribute.UP_MAX_RANGE);
	}

	public final double getUpMinLkAngl() {
		return _filterDoubles.get(DoubleAttribute.UP_MIN_LK_ANGL);
	}

	public final double getUpMaxLkAngl() {
		return _filterDoubles.get(DoubleAttribute.UP_MAX_LK_ANGL);
	}

	public double getDnMinRange() {
		return _filterDoubles.get(DoubleAttribute.DN_MIN_RANGE);
	}

	public double getDnMaxRange() {
		return _filterDoubles.get(DoubleAttribute.DN_MAX_RANGE);
	}

	public final double getDnMinLkAngl() {
		return _filterDoubles.get(DoubleAttribute.DN_MIN_LK_ANGL);
	}

	public final double getDnMaxLkAngl() {
		return _filterDoubles.get(DoubleAttribute.DN_MAX_LK_ANGL);
	}

	public double getLtMinRange() {
		return _filterDoubles.get(DoubleAttribute.LT_MIN_RANGE);
	}

	public double getLtMaxRange() {
		return _filterDoubles.get(DoubleAttribute.LT_MAX_RANGE);
	}

	public double getLtMinLkAngl() {
		return _filterDoubles.get(DoubleAttribute.LT_MIN_LK_ANGL);
	}

	public final double getLtMaxLkAngl() {
		return _filterDoubles.get(DoubleAttribute.LT_MAX_LK_ANGL);
	}

	public double getRtMinRange() {
		return _filterDoubles.get(DoubleAttribute.RT_MIN_RANGE);
	}

	public double getRtMaxRange() {
		return _filterDoubles.get(DoubleAttribute.RT_MAX_RANGE);
	}

	public final double getRtMinLkAngl() {
		return _filterDoubles.get(DoubleAttribute.RT_MIN_LK_ANGL);
	}

	public final double getRtMaxLkAngl() {
		return _filterDoubles.get(DoubleAttribute.RT_MAX_LK_ANGL);
	}

	public final double getMaxRange() {
		if (isLtRt()) {
			return Math.max(getLtMaxRange(), getRtMaxRange());
		}
		return Math.max(getUpMaxRange(), getDnMaxRange());
	}

	public final double getMinRange() {
		if (isLtRt()) {
			return Math.min(getLtMinRange(), getRtMinRange());
		}
		return Math.min(getUpMinRange(), getDnMinRange());
	}

	private void setUpMinRange(final double d) {
		_filterDoubles.set(DoubleAttribute.UP_MIN_RANGE, d);
	}

	private void setUpMaxRange(final double d) {
		_filterDoubles.set(DoubleAttribute.UP_MAX_RANGE, d);
	}

	private void setUpMinLkAngl(final double d) {
		_filterDoubles.set(DoubleAttribute.UP_MIN_LK_ANGL, d);
	}

	private void setUpMaxLkAngl(final double d) {
		_filterDoubles.set(DoubleAttribute.UP_MAX_LK_ANGL, d);
	}

	private void setDnMinRange(final double d) {
		_filterDoubles.set(DoubleAttribute.DN_MIN_RANGE, d);
	}

	private void setDnMaxRange(final double d) {
		_filterDoubles.set(DoubleAttribute.DN_MAX_RANGE, d);
	}

	private void setDnMinLkAngl(final double d) {
		_filterDoubles.set(DoubleAttribute.DN_MIN_LK_ANGL, d);
	}

	private void setDnMaxLkAngl(final double d) {
		_filterDoubles.set(DoubleAttribute.DN_MAX_LK_ANGL, d);
	}

	private void setLtMinRange(final double d) {
		_filterDoubles.set(DoubleAttribute.LT_MIN_RANGE, d);
	}

	private void setLtMaxRange(final double d) {
		_filterDoubles.set(DoubleAttribute.LT_MAX_RANGE, d);
	}

	private void setLtMinLkAngl(final double d) {
		_filterDoubles.set(DoubleAttribute.LT_MIN_LK_ANGL, d);
	}

	private void setLtMaxLkAngl(final double d) {
		_filterDoubles.set(DoubleAttribute.LT_MAX_LK_ANGL, d);
	}

	/** Rt getters and setters. */
	private void setRtMinRange(final double d) {
		_filterDoubles.set(DoubleAttribute.RT_MIN_RANGE, d);
	}

	private void setRtMaxRange(final double d) {
		_filterDoubles.set(DoubleAttribute.RT_MAX_RANGE, d);
	}

	private void setRtMinLkAngl(final double d) {
		_filterDoubles.set(DoubleAttribute.RT_MIN_LK_ANGL, d);
	}

	private void setRtMaxLkAngl(final double d) {
		_filterDoubles.set(DoubleAttribute.RT_MAX_LK_ANGL, d);
	}

	protected final int coreCompare(final LrcCore lrcCore) {
		if (_subType != lrcCore._subType) {
			return _subType.ordinal() > lrcCore._subType.ordinal() ? 1 : -1;
		}
		if (_applyToCrossLegs != lrcCore._applyToCrossLegs) {
			return _applyToCrossLegs ? 1 : -1;
		}
		final int nDoubles = DoubleAttribute.values().length;
		for (int k = 0; k < nDoubles; ++k) {
			final double d0 = _filterDoubles.get(_DoubleAttributes[k]);
			final double d1 = lrcCore._filterDoubles.get(_DoubleAttributes[k]);
			if (Double.isNaN(d0) != Double.isNaN(d1)) {
				return Double.isNaN(d0) ? -1 : 1;
			}
			if (!Double.isNaN(d0)) {
				if (d0 > d1) {
					return 1;
				} else if (d0 < d1) {
					return -1;
				}
			}
		}
		return 0;
	}

	protected boolean thisRangeIsOk(final double cpaNmi, final boolean lt,
			final boolean rt, final boolean up, final boolean dn) {
		final double minRange, maxRange;
		if (lt) {
			minRange = getLtMinRange();
			maxRange = getLtMaxRange();
		} else if (rt) {
			minRange = getRtMinRange();
			maxRange = getRtMaxRange();
		} else if (up) {
			minRange = getUpMinRange();
			maxRange = getUpMaxRange();
		} else {
			minRange = getDnMinRange();
			maxRange = getDnMaxRange();
		}
		return minRange <= cpaNmi && cpaNmi <= maxRange;
	}

	protected boolean thisResultIsOk(final Result result) {
		final double ccwTwist = result.getCcwTwistToPtrtclAtCpa();
		final double range = result.getCpaNmi();
		final boolean prtclIsLt = ccwTwist <= 180d;
		final double minRange;
		final double maxRange;
		final double minCcwTwist;
		final double maxCcwTwist;
		if (isLtRt()) {
			if (prtclIsLt) {
				minRange = getLtMinRange();
				maxRange = getLtMaxRange();
				minCcwTwist = getLtMinLkAngl();
				maxCcwTwist = getLtMaxLkAngl();
			} else {
				minRange = getRtMinRange();
				maxRange = getRtMaxRange();
				minCcwTwist = 360d - getRtMaxLkAngl();
				maxCcwTwist = 360d - getLtMinLkAngl();
			}
		} else {
			/** Up/Dn. Need to be a ladder pattern and a search leg. */
			final LegInfo.LegType legType = result.getLeg().getLegType();
			if (!legType.isSearchLeg()) {
				return false;
			}
			final boolean creepIsToLt =
					legType == LegInfo.LegType.CREEP_IS_TO_LEFT;
			if (creepIsToLt == prtclIsLt) {
				/** Prtcl is downCreep. */
				minRange = getDnMinRange();
				maxRange = getDnMaxRange();
				if (prtclIsLt) {
					minCcwTwist = getDnMinLkAngl();
					maxCcwTwist = getDnMaxLkAngl();
				} else {
					minCcwTwist = 360d - getDnMaxLkAngl();
					maxCcwTwist = 360d - getDnMinLkAngl();
				}
			} else {
				/** Prtcl is upCreep. */
				minRange = getUpMinRange();
				maxRange = getUpMaxRange();
				if (prtclIsLt) {
					minCcwTwist = getUpMinLkAngl();
					maxCcwTwist = getUpMaxLkAngl();
				} else {
					minCcwTwist = 360d - getUpMaxLkAngl();
					maxCcwTwist = 360d - getUpMinLkAngl();
				}
			}
		}
		if ((minCcwTwist >= maxCcwTwist) || (minCcwTwist > ccwTwist) || (ccwTwist > maxCcwTwist) || (minRange >= maxRange)) {
			return false;
		}
		return minRange <= range && range <= maxRange;
	}

	double[][] getTPairs(final LegInfo.LegType legType,
			final FlatLatLng flatLegLatLng0, final FlatLatLng flatLegLatLng1,
			final FlatLatLng flatPtclLatLng0, final FlatLatLng flatPtclLatLng1) {
		if (noFilters()) {
			return AnnulusIntersector.getZeroOneTPairs();
		}
		final double[] ltRadiiNmi;
		final double[] rtRadiiNmi;
		final double[] ltLkAngls;
		final double[] rtLkAngls;
		final boolean isLtRt = isLtRt();
		if (isLtRt) {
			ltRadiiNmi = new double[] { getLtMinRange(), getLtMaxRange() };
			rtRadiiNmi = new double[] { getRtMinRange(), getRtMaxRange() };
			ltLkAngls = new double[] { getLtMinLkAngl(), getLtMaxLkAngl() };
			rtLkAngls = new double[] { getRtMinLkAngl(), getRtMaxLkAngl() };
		} else if (legType == LegInfo.LegType.CREEP_IS_TO_LEFT) {
			ltRadiiNmi = new double[] { getDnMinRange(), getDnMaxRange() };
			rtRadiiNmi = new double[] { getUpMinRange(), getUpMaxRange() };
			ltLkAngls = new double[] { getDnMinLkAngl(), getDnMaxLkAngl() };
			rtLkAngls = new double[] { getUpMinLkAngl(), getUpMaxLkAngl() };
		} else if (legType == LegInfo.LegType.CREEP_IS_TO_RIGHT) {
			ltRadiiNmi = new double[] { getUpMinRange(), getUpMaxRange() };
			rtRadiiNmi = new double[] { getDnMinRange(), getDnMaxRange() };
			ltLkAngls = new double[] { getUpMinLkAngl(), getUpMaxLkAngl() };
			rtLkAngls = new double[] { getDnMinLkAngl(), getDnMaxLkAngl() };
		} else {
			/**
			 * Sides are UpDn. Return no tPairs by returning an array of length 0.
			 */
			return new double[0][];
		}

		final AnnulusIntersector annulusIntersector = new AnnulusIntersector(
				flatLegLatLng0, flatLegLatLng1, flatPtclLatLng0, flatPtclLatLng1,
				ltRadiiNmi, ltLkAngls, rtRadiiNmi, rtLkAngls);
		return annulusIntersector._tPairs;
	}

	protected final boolean noFilters() {
		if (isLtRt()) {
			if (getLtMinRange() != 0d) {
				return false;
			}
			if ((getLtMaxRange() < _MaxRange)) {
				return false;
			}
			if (getLtMinLkAngl() != 0d) {
				return false;
			}
			if (getLtMaxLkAngl() != 180d) {
				return false;
			}
			if (getRtMinRange() != 0d) {
				return false;
			}
			if ((getRtMaxRange() < _MaxRange)) {
				return false;
			}
			if (getRtMinLkAngl() != 0d) {
				return false;
			}
			if (getRtMaxLkAngl() != 180d) {
				return false;
			}
			return true;
		}
		/** UpDn. */
		if ((getUpMinRange() != 0d) || (getUpMaxRange() < _MaxRange) || (getUpMinLkAngl() != 0d) || (getUpMaxLkAngl() != 180d)) {
			return false;
		}
		if (getDnMinRange() != 0d) {
			return false;
		}
		if ((getDnMaxRange() < _MaxRange)) {
			return false;
		}
		if (getDnMinLkAngl() != 0d) {
			return false;
		}
		if (getDnMaxLkAngl() != 180d) {
			return false;
		}
		return true;
	}

	protected void addLrcCoreAttributes(final Element element) {
		element.setAttribute(_SubTypeAttributeName, _subType._xmlString);
		if (noFilters()) {
			return;
		}
		final DoubleAttribute[] doubleAttributes =
				isLtRt() ? _LtRtDoubleAttributes : _UpDnDoubleAttributes;
		for (final DoubleAttribute doubleAttribute : doubleAttributes) {
			switch (doubleAttribute) {
			case DN_MAX_LK_ANGL:
				element.setAttribute(doubleAttribute._attributeName,
						LsFormatter.StandardFormat(getDnMaxLkAngl()) + " " +
								doubleAttribute._units);
				break;
			case DN_MIN_LK_ANGL:
				element.setAttribute(doubleAttribute._attributeName,
						LsFormatter.StandardFormat(getDnMinLkAngl()) + " " +
								doubleAttribute._units);
				break;
			case DN_MIN_RANGE:
				element.setAttribute(doubleAttribute._attributeName,
						LsFormatter.StandardFormat(getDnMinRange()) + " " +
								doubleAttribute._units);
				break;
			case LT_MAX_LK_ANGL:
				element.setAttribute(doubleAttribute._attributeName,
						LsFormatter.StandardFormat(getLtMaxLkAngl()) + " " +
								doubleAttribute._units);
				break;
			case LT_MIN_LK_ANGL:
				element.setAttribute(doubleAttribute._attributeName,
						LsFormatter.StandardFormat(getLtMinLkAngl()) + " " +
								doubleAttribute._units);
				break;
			case LT_MIN_RANGE:
				element.setAttribute(doubleAttribute._attributeName,
						LsFormatter.StandardFormat(getLtMinRange()) + " " +
								doubleAttribute._units);
				break;
			case RT_MAX_LK_ANGL:
				element.setAttribute(doubleAttribute._attributeName,
						LsFormatter.StandardFormat(getRtMaxLkAngl()) + " " +
								doubleAttribute._units);
				break;
			case RT_MIN_LK_ANGL:
				element.setAttribute(doubleAttribute._attributeName,
						LsFormatter.StandardFormat(getRtMinLkAngl()) + " " +
								doubleAttribute._units);
				break;
			case RT_MIN_RANGE:
				element.setAttribute(doubleAttribute._attributeName,
						LsFormatter.StandardFormat(getRtMinRange()) + " " +
								doubleAttribute._units);
				break;
			case UP_MAX_LK_ANGL:
				element.setAttribute(doubleAttribute._attributeName,
						LsFormatter.StandardFormat(getUpMaxLkAngl()) + " " +
								doubleAttribute._units);
				break;
			case UP_MIN_LK_ANGL:
				element.setAttribute(doubleAttribute._attributeName,
						LsFormatter.StandardFormat(getUpMinLkAngl()) + " " +
								doubleAttribute._units);
				break;
			case UP_MIN_RANGE:
				element.setAttribute(doubleAttribute._attributeName,
						LsFormatter.StandardFormat(getUpMinRange()) + " " +
								doubleAttribute._units);
				break;
			/** The maxRanges are different. */
			case DN_MAX_RANGE:
				element.setAttribute(doubleAttribute._attributeName,
						LsFormatter.StandardFormat(_filterDoubles._dnMaxRange) + " " +
								doubleAttribute._units);
				break;
			case LT_MAX_RANGE:
				element.setAttribute(doubleAttribute._attributeName,
						LsFormatter.StandardFormat(_filterDoubles._ltMaxRange) + " " +
								doubleAttribute._units);
				break;
			case RT_MAX_RANGE:
				element.setAttribute(doubleAttribute._attributeName,
						LsFormatter.StandardFormat(_filterDoubles._rtMaxRange) + " " +
								doubleAttribute._units);
				break;
			case UP_MAX_RANGE:
				element.setAttribute(doubleAttribute._attributeName,
						LsFormatter.StandardFormat(_filterDoubles._upMaxRange) + " " +
								doubleAttribute._units);
				break;
			default:
				break;
			}
		}
	}

	public String getString() {
		final String s0 = String.format("%s DstnctDtctThrshld %.1f mins%s",
				_subType == null ? "NullSubType" : _subType._xmlString,
				_distinctDetectionThresholdMins,
				_applyToCrossLegs ? " ApplyToXLegs" : " ~ApplyToXLegs");
		final String s1 = _filterDoubles.getString();
		if (s1.length() == 0) {
			return s0;
		}
		return s0 + "\n" + s1;
	}

	@Override
	public String toString() {
		return getString();
	}

}
