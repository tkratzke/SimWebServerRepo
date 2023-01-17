package com.skagit.sarops.util.patternUtils;

import com.skagit.sarops.planner.plannerModel.PatternKind;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.pvSeq.PvSeq;
import com.skagit.util.Constants;
import com.skagit.util.MathX;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.TimeUtilities;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;

public class MyStyle {
	final private static double _NmiToR = MathX._NmiToR;
	final private static int _OnMarsNSearchLegs = 1;
	final private static int _OnMarsNHalfLaps = 2;

	final private PatternKind _patternKind;
	final private long _cstRefSecs;
	final private int _searchDurationSecs;
	final private LatLng3 _center;

	/** _firstLegDirR is radians, ccw from E. */
	final private double _firstLegDirR;
	/** _onMarsTsR is NaN unless this is onMars. */
	final private double _onMarsTsR;

	/**
	 * <pre>
	 * _specAlongR:
	 *   LP: (Positive) Length of the side that is
	 *       parallel to the 1st leg.
	 *   SS:  NaN
	 *   VS:  NaN
	 * _specSgndAcrossR:
	 *   LP: Length of the side that is parallel to the 2nd leg.
	 *       Sign gives firstTurnRight or not.
	 *   SS: Length of the side of the box.
	 *       Sign gives firstTurnRight or not.
	 *   VS: 1 or -1.
	 *       Sign gives firstTurnRight.
	 * </pre>
	 */
	final private double _specAlongR, _specSgndAcrossR;

	private class KeyValues {
		final private double _rawSearchKts;
		final private double _eplNmi;
		final private double _minTsNmi;
		final private DiscreteLpSpecToTs _discLpTsBoxAdjuster;
		final private DiscreteSsSpecToTs _discSsTsBoxAdjuster;

		private KeyValues() {
			_rawSearchKts = _eplNmi = _minTsNmi = Double.NaN;
			_discLpTsBoxAdjuster = null;
			_discSsTsBoxAdjuster = null;
		}

		/**
		 * We cache key values in this private object. In general, since the
		 * rawSearchKts could change, this is only good for the most recent
		 * rawSearchKts.
		 */
		private KeyValues(final double rawSearchKts, final double minTsNmi) {
			_rawSearchKts = rawSearchKts;
			_minTsNmi = minTsNmi;
			final double searchHrs = _searchDurationSecs / 3600d;
			_eplNmi = (rawSearchKts * searchHrs) *
					PatternUtilStatics._EffectiveSpeedReduction;
			final double acrossNmi = Math.abs(_specSgndAcrossR) / _NmiToR;
			if (_patternKind.isPsCs()) {
				final double alongNmi = _specAlongR / _NmiToR;
				_discLpTsBoxAdjuster = new DiscreteLpSpecToTs(_eplNmi, minTsNmi,
						/* fxdTsNmi= */Double.NaN, alongNmi, acrossNmi,
						/* expandSpecsIfNeeded= */true);
				_discSsTsBoxAdjuster = null;
			} else if (_patternKind.isSs()) {
				_discLpTsBoxAdjuster = null;
				_discSsTsBoxAdjuster = new DiscreteSsSpecToTs(_eplNmi, minTsNmi,
						/* fxdTsNmi= */Double.NaN, acrossNmi,
						/* expandSpecsIfNeeded= */true);
			} else {
				_discLpTsBoxAdjuster = null;
				_discSsTsBoxAdjuster = null;
			}
		}

		private int getNSearchLegs() {
			if (_patternKind.isPsCs()) {
				return _discLpTsBoxAdjuster._nSearchLegs;
			}
			if (_patternKind.isSs()) {
				final int nHalfLaps = _discSsTsBoxAdjuster._nHalfLaps;
				return nHalfLaps == 0 ? 0 :
						(2 * _discSsTsBoxAdjuster._nHalfLaps - 1);
			}
			if (_patternKind.isVs()) {
				return PatternUtilStatics._NVsSearchLegs;
			}
			return -1;
		}

		private double getSllNmi() {
			/** Should only be called for LP. */
			return _discLpTsBoxAdjuster._sll;
		}

		private double getTsNmi() {
			if (_patternKind.isPsCs()) {
				return _discLpTsBoxAdjuster._ts;
			}
			if (_patternKind.isVs()) {
				return PatternUtilStatics.computeVsTsNmi(_eplNmi);
			}
			if (_patternKind.isSs()) {
				return _discSsTsBoxAdjuster._ts;
			}
			return Double.NaN;
		}

		public int getNHalfLaps() {
			if (!_patternKind.isSs()) {
				return -1;
			}
			return _discSsTsBoxAdjuster._nHalfLaps;
		}
	}

	private KeyValues _keyValues = new KeyValues();

	/**
	 * <pre>
	 * For PSCS using along/across, or for VS/SS.
	 * For SS and VS, alongR should be Double.NaN.
	 * For SS, sgndAcrossR gives firstTurnRight and the length of one
	 *   side of the (square) tsBox.
	 * For VS, sgndAcross should be 1 or -1, and specifies firstTurnRight.
	 * firstLegDir is in radians ccw from E if inputIsRadians, and
	 * degrees cw from N otherwise.
	 * </pre>
	 */
	public MyStyle(final PatternVariable pv, final long cstRefSecs,
			final int searchDurationSecs, //
			final LatLng3 center, final double firstLegDir, //
			final double along, final double sgndAcross, //
			final char inputIsRadiansChar) {
		_patternKind = pv.getPatternKind();
		_cstRefSecs = cstRefSecs;
		_searchDurationSecs = searchDurationSecs;
		_center = PatternUtilStatics.DiscretizeLatLng(center);
		final char inputIsRadiansCharLc =
				Character.toLowerCase(inputIsRadiansChar);
		final boolean inputIsRadians =
				inputIsRadiansCharLc == 'y' || inputIsRadiansCharLc == 't';
		final double degrees0;
		if (inputIsRadians) {
			degrees0 = Math.toDegrees(Constants._PiOver2 - firstLegDir);
			_specAlongR = _patternKind.isPsCs() ? along : Double.NaN;
			_specSgndAcrossR =
					_patternKind.isVs() ? Math.signum(sgndAcross) : sgndAcross;
		} else {
			degrees0 = firstLegDir;
			_specAlongR = _patternKind.isPsCs() ? (along * _NmiToR) : Double.NaN;
			_specSgndAcrossR = _patternKind.isVs() ? Math.signum(sgndAcross) :
					(sgndAcross * _NmiToR);
		}
		final double degrees = PatternUtilStatics.DiscretizeOrntn(degrees0);
		_firstLegDirR = Math.toRadians(90d - degrees);
		/** Not onMars. */
		_onMarsTsR = Double.NaN;
		/** Set keyValues as per pv's searchSpeed. */
		getKeyValues(pv.getRawSearchKts(), pv.getMinTsNmi());
	}

	/**
	 * for LP or SS, and using length/width; strictly for reading in from xml.
	 * For Ss, lenNmi is the dimension of interest, and widNmi should be NaN.
	 */
	public MyStyle(final PatternKind patternKind, final long cstRefSecs,
			final int searchDurationSecs, //
			final LatLng3 center, final double orntn,
			final boolean firstTurnRight, //
			final double lenNmi, final double widNmi, final boolean ps,
			final double tsNmiForSingleLeg) {
		_patternKind = patternKind;
		_cstRefSecs = cstRefSecs;
		_searchDurationSecs = searchDurationSecs;
		_center = PatternUtilStatics.DiscretizeLatLng(center);
		final double firstLegHdg;
		if (_patternKind.isSs() || ps) {
			firstLegHdg = orntn;
		} else {
			final double dirCreepD = orntn;
			if (firstTurnRight) {
				firstLegHdg = dirCreepD - 90d;
			} else {
				firstLegHdg = dirCreepD + 90d;
			}
		}
		final double degrees =
				PatternUtilStatics.DiscretizeOrntn(90d - firstLegHdg);
		_firstLegDirR = Math.toRadians(degrees);
		if (_patternKind.isSs()) {
			_specAlongR = Double.NaN;
			_specSgndAcrossR = lenNmi * (firstTurnRight ? 1d : -1d) * _NmiToR;
		} else {
			/** It's an LP. */
			final double alongNmi = ps ? lenNmi : widNmi;
			final double acrossNmi = ps ? widNmi : lenNmi;
			final double sgndAcrossNmi = firstTurnRight ? acrossNmi : -acrossNmi;
			_specAlongR = alongNmi * _NmiToR;
			_specSgndAcrossR = sgndAcrossNmi * _NmiToR;
		}
		_onMarsTsR = Double.NaN;
	}

	/** For LP or SS. This should only be used for printing. */
	public MyStyle(final PatternKind patternKind, final long cstRefSecs,
			final int searchDurationSecs, final LatLng3 center,
			final double orntn, final boolean firstTurnRight, //
			final double lengthNmi, final double widthNmi, final boolean ps) {
		_patternKind = patternKind;
		_cstRefSecs = cstRefSecs;
		_searchDurationSecs = searchDurationSecs;
		_center = PatternUtilStatics.DiscretizeLatLng(center);
		final double firstLegHdg;
		if ((patternKind.isPsCs() && ps) || patternKind.isSs()) {
			firstLegHdg = orntn;
		} else {
			final double dirCreepD = orntn;
			if (firstTurnRight) {
				firstLegHdg = dirCreepD - 90d;
			} else {
				firstLegHdg = dirCreepD + 90d;
			}
		}
		final double degrees =
				PatternUtilStatics.DiscretizeOrntn(90d - firstLegHdg);
		_firstLegDirR = Math.toRadians(degrees);
		final double alongNmi, acrossNmi;
		if (patternKind.isPsCs()) {
			alongNmi = ps ? lengthNmi : widthNmi;
			acrossNmi = ps ? widthNmi : lengthNmi;
		} else {
			/** Should be SS. */
			alongNmi = Double.NaN;
			acrossNmi = lengthNmi;
		}
		_specAlongR = alongNmi * _NmiToR;
		final double sgndAcrossNmi = firstTurnRight ? acrossNmi : -acrossNmi;
		_specSgndAcrossR = sgndAcrossNmi * _NmiToR;
		_onMarsTsR = Double.NaN;
	}

	/** For VS. This should only be used for printing. */
	public MyStyle(final long cstRefSecs, final int searchDurationSecs,
			final LatLng3 center, final double orntn,
			final boolean firstTurnRight) {
		_patternKind = PatternKind.VS;
		_cstRefSecs = cstRefSecs;
		_searchDurationSecs = searchDurationSecs;
		_center = PatternUtilStatics.DiscretizeLatLng(center);
		final double firstLegHdg = orntn;
		final double degrees =
				PatternUtilStatics.DiscretizeOrntn(90d - firstLegHdg);
		_firstLegDirR = Math.toRadians(degrees);
		_specAlongR = _specSgndAcrossR = Double.NaN;
		_onMarsTsR = Double.NaN;
	}

	/** For OnMars. */
	public MyStyle(final Extent modelExtent, final PatternVariable pv) {
		_patternKind = pv.getPatternKind();
		_onMarsTsR = pv.getMinTsNmi() * _NmiToR;
		final PatternKind patternKind = pv.getPatternKind();
		final PvSeq pvSeq = pv.getPvSeq();
		final double leftLng = modelExtent.getLeftLng();
		final double rightLng = modelExtent.getRightLng();
		final double minLat = modelExtent.getMinLat();
		final double maxLat = modelExtent.getMaxLat();
		final LatLng3 leftLow = LatLng3.getLatLngB(minLat, leftLng);
		final LatLng3 rightHigh = LatLng3.getLatLngB(maxLat, rightLng);
		final LatLng3 aoiPseudoCenter =
				GreatCircleArc.CreateGca(leftLow, rightHigh).computeMidpoint();
		final double haversine = MathX.haversineX(aoiPseudoCenter, leftLow);
		final double hdg =
				PatternUtilStatics.DiscretizeOrntn(pv._fraction * 360d);
		_center = PatternUtilStatics.DiscretizeLatLng(
				MathX.getLatLngX(aoiPseudoCenter, hdg, haversine));
		final double degrees = PatternUtilStatics.DiscretizeOrntn(90d - hdg);
		_firstLegDirR = Math.toRadians(degrees);
		if (pvSeq == null) {
			_cstRefSecs = pv.getPvCstRefSecs();
		} else {
			_cstRefSecs = pvSeq._pvSeqCstRefSecs;
		}
		final double onMarsTsNmi = pv.getMinTsNmi();
		final double ascribedNmi;
		if (patternKind.isPsCs()) {
			final double onMarsSllNmi = PatternUtilStatics._TsInc;
			final double alongNmi = onMarsSllNmi + onMarsTsNmi;
			final double sgndAcrossNmi = _OnMarsNSearchLegs * onMarsTsNmi;
			ascribedNmi = alongNmi * _OnMarsNSearchLegs;
			_specAlongR = alongNmi * _NmiToR;
			_specSgndAcrossR = sgndAcrossNmi * _NmiToR;
		} else if (patternKind.isSs()) {
			_specAlongR = Double.NaN;
			final double acrossNmi = _OnMarsNHalfLaps * onMarsTsNmi;
			_specSgndAcrossR = acrossNmi * _NmiToR;
			ascribedNmi = _OnMarsNHalfLaps * _OnMarsNHalfLaps * onMarsTsNmi;
		} else if (patternKind.isVs()) {
			_specAlongR = Double.NaN;
			_specSgndAcrossR = 1d;
			ascribedNmi = 9d * onMarsTsNmi;
		} else {
			_specAlongR = _specSgndAcrossR = Double.NaN;
			ascribedNmi = Double.NaN;
		}
		final double rawSearchKts = pv.getRawSearchKts();
		final double searchHrs = ascribedNmi /
				(rawSearchKts * PatternUtilStatics._EffectiveSpeedReduction);
		_searchDurationSecs = (int) Math.ceil(searchHrs * 3600d);
	}

	private MyStyle(final MyStyle myStyle, final long cstRefSecs) {
		_patternKind = myStyle._patternKind;
		_cstRefSecs = cstRefSecs;
		_searchDurationSecs = myStyle._searchDurationSecs;
		_center = myStyle._center;
		_firstLegDirR = myStyle._firstLegDirR;
		_specAlongR = myStyle._specAlongR;
		_specSgndAcrossR = myStyle._specSgndAcrossR;
		_onMarsTsR = Double.NaN;
	}

	/**
	 * Accessors; start with the ones from the specs, not the resulting box.
	 */
	public LatLng3 getCenter() {
		return _center;
	}

	public double getFirstLegDirR() {
		return _firstLegDirR;
	}

	public long getCstRefSecs() {
		return _cstRefSecs;
	}

	public int getSearchDurationSecs() {
		return _searchDurationSecs;
	}

	public long getEstRefSecs() {
		return _cstRefSecs + _searchDurationSecs;
	}

	public boolean onMars() {
		return _onMarsTsR > 0d;
	}

	public double getFirstLegHdg() {
		final double firstLegHdg =
				Math.toDegrees(Constants._PiOver2 - _firstLegDirR);
		return firstLegHdg;
	}

	public boolean getFirstTurnRight() {
		return _specSgndAcrossR > 0d;
	}

	public double getSpecAlongR() {
		return _specAlongR;
	}

	public double getSpecSgndAcrossR() {
		if (_patternKind.isVs()) {
			return getFirstTurnRight() ? 1d : -1d;
		}
		return _specSgndAcrossR;
	}

	public double getSpecAlongNmi() {
		if (_patternKind.isVs()) {
			return getFirstTurnRight() ? 1d : -1d;
		}
		final double alongNmi = _specAlongR / _NmiToR;
		return alongNmi;
	}

	public double getSpecSgndAcrossNmi() {
		final double sgndAcrossNmi = _specSgndAcrossR / _NmiToR;
		return sgndAcrossNmi;
	}

	public double getSpecOrntn() {
		final double firstLegHdg = getFirstLegHdg();
		final double specAlongNmi = getSpecAlongNmi();
		final double specAcrossNmi = Math.abs(getSpecSgndAcrossNmi());
		if (specAlongNmi >= specAcrossNmi) {
			return firstLegHdg;
		}
		final boolean firstTurnRight = _specSgndAcrossR >= 0d;
		final double creepHdg = LatLng3
				.getInRange0_360(firstLegHdg + (firstTurnRight ? 90d : -90d));
		return PatternUtilStatics.roundWithMinimum(creepHdg,
				PatternUtilStatics._OrntnDegInc);
	}

	/** End of accessors of the specs. */

	public MyStyle adjustCst(final long cstRefSecs) {
		if (onMars()) {
			return this;
		}
		return new MyStyle(this, cstRefSecs);
	}

	/** Accessing Values (all in nmi) of the rounded box. */
	public KeyValues getKeyValues(final double rawSearchKts,
			final double minTsNmi) {
		if (!(rawSearchKts > 0d)) {
			if (Double.isNaN(_keyValues._rawSearchKts)) {
				final KeyValues keyValues = new KeyValues();
				_keyValues = keyValues;
				return keyValues;
			}
		}
		if (_keyValues._rawSearchKts == rawSearchKts &&
				_keyValues._minTsNmi == minTsNmi) {
			return _keyValues;
		}
		final KeyValues keyValues = new KeyValues(rawSearchKts, minTsNmi);
		_keyValues = keyValues;
		return keyValues;
	}

	public double computeAlongNmi(final double rawSearchKts,
			final double minTsNmi) {
		if (!_patternKind.isPsCs()) {
			return Double.NaN;
		}
		if (onMars()) {
			final double onMarsSllNmi = PatternUtilStatics._TsInc;
			return onMarsSllNmi + (_onMarsTsR / _NmiToR);
		}
		if (!(rawSearchKts > 0d)) {
			return Double.NaN;
		}
		final KeyValues keyValues = getKeyValues(rawSearchKts, minTsNmi);
		return keyValues.getTsNmi() + keyValues.getSllNmi();
	}

	public double computeSgndAcrossNmi(final double rawSearchKts,
			final double minTsNmi) {
		if (!_patternKind.isPsCs() && !_patternKind.isSs()) {
			return Double.NaN;
		}
		if (onMars()) {
			return _OnMarsNSearchLegs * (_onMarsTsR / _NmiToR);
		}
		if (!(rawSearchKts > 0d)) {
			return Double.NaN;
		}
		final KeyValues keyValues = getKeyValues(rawSearchKts, minTsNmi);
		return (getFirstTurnRight() ? 1d : -1d) * keyValues.getNSearchLegs() *
				keyValues.getTsNmi();
	}

	public double computeSllNmi(final double rawSearchKts,
			final double minTsNmi) {
		if (!_patternKind.isPsCs()) {
			return Double.NaN;
		}
		if (onMars()) {
			return PatternUtilStatics._TsInc;
		}
		if (!(rawSearchKts > 0d)) {
			return Double.NaN;
		}
		final KeyValues keyValues = getKeyValues(rawSearchKts, minTsNmi);
		return keyValues.getSllNmi();
	}

	public int computeNSearchLegs(final double rawSearchKts,
			final double minTsNmi) {
		if (_patternKind.isPsCs()) {
			if (onMars()) {
				return _OnMarsNSearchLegs;
			}
			if (!(rawSearchKts > 0d)) {
				return -1;
			}
			final KeyValues keyValues = getKeyValues(rawSearchKts, minTsNmi);
			return keyValues.getNSearchLegs();
		}
		if (_patternKind == PatternKind.VS) {
			return 7;
		}
		if (_patternKind == PatternKind.SS) {
			if (onMars()) {
				return 2 * _OnMarsNHalfLaps - 1;
			}
			if (!(rawSearchKts > 0d)) {
				return -1;
			}
			final KeyValues keyValues = getKeyValues(rawSearchKts, minTsNmi);
			return keyValues.getNSearchLegs();
		}
		return -1;
	}

	public int computeNHalfLaps(final double rawSearchKts,
			final double minTsNmi) {
		if (!_patternKind.isSs()) {
			return -1;
		}
		if (onMars()) {
			return _OnMarsNHalfLaps;
		}
		if (!(rawSearchKts > 0d)) {
			return -1;
		}
		final KeyValues keyValues = getKeyValues(rawSearchKts, minTsNmi);
		return keyValues.getNHalfLaps();
	}

	public double computeTsNmi(final double rawSearchKts,
			final double minTsNmi) {
		if (onMars()) {
			return _onMarsTsR / _NmiToR;
		}
		if (_patternKind.isVs()) {
			final double searchHrs = _searchDurationSecs / 3600d;
			final double eplNmi = (rawSearchKts * searchHrs) *
					PatternUtilStatics._EffectiveSpeedReduction;
			final double tsNmi = PatternUtilStatics.computeVsTsNmi(eplNmi);
			return tsNmi;
		}
		if (_patternKind.isPsCs() || _patternKind.isSs()) {
			final KeyValues keyValues = getKeyValues(rawSearchKts, minTsNmi);
			return keyValues.getTsNmi();
		}
		return Double.NaN;
	}

	public double computeLengthNmi(final double rawSearchKts,
			final double minTsNmi) {
		if (_patternKind.isPsCs()) {
			if (onMars()) {
				final double onMarsTsNmi = _onMarsTsR / _NmiToR;
				final double alongNmi = PatternUtilStatics._TsInc + onMarsTsNmi;
				final double acrossNmi = _OnMarsNSearchLegs * onMarsTsNmi;
				return Math.max(alongNmi, acrossNmi);
			}
			if (!(rawSearchKts > 0d)) {
				return Double.NaN;
			}
			final KeyValues keyValues = getKeyValues(rawSearchKts, minTsNmi);
			final double alongNmi = keyValues.getSllNmi() + keyValues.getTsNmi();
			final double acrossNmi =
					keyValues.getNSearchLegs() * keyValues.getTsNmi();
			return Math.max(alongNmi, acrossNmi);
		}
		if (_patternKind.isVs()) {
			return 3d * computeTsNmi(rawSearchKts, minTsNmi);
		}
		if (_patternKind.isSs()) {
			if (onMars()) {
				final double acrossNmi = _OnMarsNHalfLaps * (_onMarsTsR / _NmiToR);
				return acrossNmi;
			}
			if (!(rawSearchKts > 0d)) {
				return Double.NaN;
			}
			final KeyValues keyValues = getKeyValues(rawSearchKts, minTsNmi);
			final double acrossNmi =
					keyValues.getNHalfLaps() * keyValues.getTsNmi();
			return acrossNmi;
		}
		return Double.NaN;
	}

	public double computeWidthNmi(final double rawSearchKts,
			final double minTsNmi) {
		if (_patternKind.isPsCs()) {
			if (onMars()) {
				final double onMarsTsNmi = _onMarsTsR / _NmiToR;
				final double alongNmi = PatternUtilStatics._TsInc + onMarsTsNmi;
				final double acrossNmi = _OnMarsNSearchLegs * onMarsTsNmi;
				return Math.min(alongNmi, acrossNmi);
			}
			if (!(rawSearchKts > 0d)) {
				return Double.NaN;
			}
			final KeyValues keyValues = getKeyValues(rawSearchKts, minTsNmi);
			final double alongNmi = keyValues.getSllNmi() + keyValues.getTsNmi();
			final double acrossNmi =
					keyValues.getNSearchLegs() * keyValues.getTsNmi();
			return Math.min(alongNmi, acrossNmi);
		}
		if (_patternKind.isVs() || _patternKind.isSs()) {
			return computeLengthNmi(rawSearchKts, minTsNmi);
		}
		return Double.NaN;
	}

	public double computeOrntn(final double rawSearchKts,
			final double minTsNmi) {
		final double degrees;
		if (onMars()) {
			degrees = Math.toDegrees(Constants._PiOver2 - _firstLegDirR);
		} else if (!(rawSearchKts > 0d)) {
			return Double.NaN;
		} else if (_patternKind.isPsCs()) {
			if (computePs(rawSearchKts, minTsNmi)) {
				degrees = Math.toDegrees(Constants._PiOver2 - _firstLegDirR);
			} else {
				degrees = Math.toDegrees(Constants._PiOver2 - _firstLegDirR) +
						(_specSgndAcrossR >= 0d ? 90d : -90d);
			}
		} else if (_patternKind.isVs() || _patternKind.isSs()) {
			degrees = Math.toDegrees(Constants._PiOver2 - _firstLegDirR);
		} else {
			return Double.NaN;
		}
		final double orntn = PatternUtilStatics.DiscretizeOrntn(degrees);
		return orntn;
	}

	public double computeCreepHdg(final double rawSearchKts,
			final double minTsNmi) {
		if (!_patternKind.isPsCs()) {
			return Double.NaN;
		}
		final boolean firstTurnRight = _specSgndAcrossR > 0d;
		final double firstLegHdg0 =
				Math.toDegrees(Constants._PiOver2 - _firstLegDirR);
		final double creepHdg0 = firstLegHdg0 + (firstTurnRight ? 90d : -90d);
		final double creepHdg = PatternUtilStatics.DiscretizeOrntn(creepHdg0);
		return creepHdg;
	}

	public boolean computePs(final double rawSearchKts,
			final double minTsNmi) {
		if (!_patternKind.isPsCs() || onMars() || !(rawSearchKts > 0d)) {
			return true;
		}
		final KeyValues keyValues = getKeyValues(rawSearchKts, minTsNmi);
		final double alongNmi = keyValues.getSllNmi() + keyValues.getTsNmi();
		final double acrossNmi =
				keyValues.getNSearchLegs() * keyValues.getTsNmi();
		return alongNmi >= acrossNmi;
	}

	public boolean isCloseTo(final MyStyle myStyle) {
		if (myStyle == null || onMars() || myStyle.onMars()) {
			return false;
		}
		final PatternKind hisPatternKind = myStyle._patternKind;
		if ((_patternKind != hisPatternKind) || !_center.isWithinU(myStyle._center, 100)) {
			return false;
		}
		final double myFirstLegDirD = Math.toDegrees(_firstLegDirR);
		final double hisFirstLegDirD = Math.toDegrees(myStyle._firstLegDirR);
		if (LatLng3.degsToEast180_180(myFirstLegDirD, hisFirstLegDirD) > 0.01) {
			return false;
		}
		/** We ignore cst in this test. */
		if (Math.abs(_searchDurationSecs - myStyle._searchDurationSecs) > 5) {
			return false;
		}
		if (!NumericalRoutines.relativeErrorIsSmall(_specSgndAcrossR,
				myStyle._specSgndAcrossR, /* relativeErrorThreshold= */0.01)) {
			return false;
		}
		if (_patternKind.isPsCs()) {
			if (!NumericalRoutines.relativeErrorIsSmall(_specAlongR,
					myStyle._specAlongR, /* relativeErrorThreshold= */0.01)) {
				return false;
			}
		}
		return true;
	}

	public int deepCompareTo(final MyStyle myStyle) {
		if (myStyle == null) {
			return 1;
		}
		if (_patternKind != myStyle._patternKind) {
			return _patternKind.compareTo(myStyle._patternKind);
		}
		if (onMars() && !myStyle.onMars()) {
			return -1;
		}
		if (!onMars() && myStyle.onMars()) {
			return 1;
		}
		if (onMars()) {
			return 0;
		}
		int compareValue = 0;
		compareValue = Long.compare(_cstRefSecs, myStyle._cstRefSecs);
		if (compareValue != 0) {
			return compareValue;
		}
		compareValue =
				Long.compare(_searchDurationSecs, myStyle._searchDurationSecs);
		if (compareValue != 0) {
			return compareValue;
		}
		final LatLng3 myCenter = getCenter();
		final LatLng3 hisCenter = myStyle.getCenter();
		compareValue = myCenter.compareLatLng(hisCenter);
		if (compareValue != 0) {
			return compareValue;
		}
		final double myFirstLegHdg = getFirstLegHdg();
		final double hisFirstLegHdg = myStyle.getFirstLegHdg();
		compareValue = Double.compare(myFirstLegHdg, hisFirstLegHdg);
		if (compareValue != 0) {
			return compareValue;
		}
		if (getFirstTurnRight() != myStyle.getFirstTurnRight()) {
			return getFirstTurnRight() ? 1 : -1;
		}
		if (_patternKind.isVs()) {
			return 0;
		}
		compareValue =
				Double.compare(_specSgndAcrossR, myStyle._specSgndAcrossR);
		if (_patternKind.isSs()) {
			return 0;
		}
		compareValue = Double.compare(_specAlongR, myStyle._specAlongR);
		if (compareValue != 0) {
			return compareValue;
		}
		return 0;
	}

	public String getTheirStyleString(final double rawSearchKts,
			final double minTsNmi) {
		final double len = computeLengthNmi(rawSearchKts, minTsNmi);
		final double wid = computeWidthNmi(rawSearchKts, minTsNmi);
		final double tsNmi = computeTsNmi(rawSearchKts, minTsNmi);
		final double sllNmi = computeSllNmi(rawSearchKts, minTsNmi);
		final int nSearchLegs = computeNSearchLegs(rawSearchKts, minTsNmi);
		final String booleansString = String.format("%s-%s", //
				computePs(rawSearchKts, minTsNmi) ? "PS" : "CS",
				getFirstTurnRight() ? "Rt" : "Lt");
		final double orntn = computeOrntn(rawSearchKts, minTsNmi);
		final String centerString =
				_center == null ? "NO CENTER" : _center.getString();
		return String.format(
				"Cntr%s len/wid[%f/%f] %s ts/sll/nSearchLegs[%f/%f/%d] orntn[%f]",
				centerString, len, wid, booleansString, tsNmi, sllNmi, nSearchLegs,
				orntn);
	}

	public String legacyGetTheirStyleString(final double rawSearchKts,
			final double minTsNmi) {
		String s = "";
		s += String.format(" length[%f] width[%f]",
				computeLengthNmi(rawSearchKts, minTsNmi),
				computeWidthNmi(rawSearchKts, minTsNmi));
		s += String.format(" dir[%f]", computeOrntn(rawSearchKts, minTsNmi));
		s += String.format(" type[%s] firstTurn[%s]", //
				computePs(rawSearchKts, minTsNmi) ? "PS" : "CS",
				getFirstTurnRight() ? "R" : "L");
		s += String.format(" centerLat[%f] centerLng[%f]", _center.getLat(),
				_center.getLng());
		return s;
	}

	public String getString(final PatternVariable pv) {
		return getString(pv.getRawSearchKts(), pv.getMinTsNmi());
	}

	public String getString(final double rawSearchKts,
			final double minTsNmi) {
		String s = "";
		if (_onMarsTsR > 0d) {
			return "OnMars";
		}
		final double searchHrs = _searchDurationSecs / 3600d;
		if (_patternKind.isPsCs()) {
			final double eplNmiA = rawSearchKts * searchHrs *
					PatternUtilStatics._EffectiveSpeedReduction;
			final double eplNmiB = computeEplNmi(rawSearchKts, minTsNmi);
			if (eplNmiA != eplNmiB) {
				s += String.format("Epl:%.2f;%.2f", eplNmiA, eplNmiB);
			} else {
				s += String.format("Epl:%.2f", eplNmiA);
			}
			final double alongNmiA = getSpecAlongNmi();
			final double alongNmiB = computeAlongNmi(rawSearchKts, minTsNmi);
			if (alongNmiA != alongNmiB) {
				s += String.format(" Al:%.2f;%.2f", alongNmiA, alongNmiB);
			} else {
				s += String.format(" Al:%.2f", alongNmiA);
			}
			final double sgndAcrossNmiA = getSpecSgndAcrossNmi();
			final double sgndAcrossNmiB =
					computeSgndAcrossNmi(rawSearchKts, minTsNmi);
			if (sgndAcrossNmiA != sgndAcrossNmiB) {
				s += String.format(" SgndAc:%.2f;%.2f", sgndAcrossNmiA,
						sgndAcrossNmiB);
			} else {
				s += String.format(" SgndAc:%.2f", sgndAcrossNmiA);
			}
			final double tsNmiB = computeTsNmi(rawSearchKts, minTsNmi);
			final double sllNmiB = computeSllNmi(rawSearchKts, minTsNmi);
			final int nSearchLegsB = computeNSearchLegs(rawSearchKts, minTsNmi);
			s += String.format("\n\tTSN:%.2f;%.2f,%d", tsNmiB, sllNmiB,
					nSearchLegsB);
			final double firstLegHdg = getFirstLegHdg();
			final boolean firstTurnRight = getFirstTurnRight();
			if (firstTurnRight) {
				s += String.format(" Hdg:%.2f-Rt", firstLegHdg);
			} else {
				s += String.format(" Hdg:%.2f-Lt", firstLegHdg);
			}
			final String refSecsString =
					TimeUtilities.formatTime(_cstRefSecs, /* includeSecs= */true);
			s += String.format(" %dSecs;%s", _searchDurationSecs, refSecsString);
			final String centerString =
					_center == null ? "NO CENTER" : _center.getString(2);
			s += String.format(" %s", centerString);
		} else if (_patternKind.isVs()) {
			final double eplNmiA = rawSearchKts * searchHrs *
					PatternUtilStatics._EffectiveSpeedReduction;
			final double eplNmiB = PatternUtilStatics.computeVsEplNmi(eplNmiA);
			if (eplNmiA != eplNmiB) {
				s += String.format("Epl:%.2f;%.2f", eplNmiA, eplNmiB);
			} else {
				s += String.format("Epl:%.2f", eplNmiA);
			}
			final double firstLegHdg = getFirstLegHdg();
			final boolean firstTurnRight = getFirstTurnRight();
			if (firstTurnRight) {
				s += String.format(" Hdg:%.2f-Rt", firstLegHdg);
			} else {
				s += String.format(" Hdg:%.2f-Lt", firstLegHdg);
			}
			final String refSecsString =
					TimeUtilities.formatTime(_cstRefSecs, /* includeSecs= */true);
			s += String.format(" %dSecs;%s", _searchDurationSecs, refSecsString);
			final String centerString =
					_center == null ? "NO CENTER" : _center.getString(2);
			s += String.format(" %s", centerString);
		} else if (_patternKind.isSs()) {
			final double eplNmiA = rawSearchKts * searchHrs *
					PatternUtilStatics._EffectiveSpeedReduction;
			final double eplNmiB = computeEplNmi(rawSearchKts, minTsNmi);
			if (eplNmiA != eplNmiB) {
				s += String.format("Epl:%.2f;%.2f", eplNmiA, eplNmiB);
			} else {
				s += String.format("Epl:%.2f", eplNmiA);
			}
			final double sgndAcrossNmiA = getSpecSgndAcrossNmi();
			final double sgndAcrossNmiB =
					computeSgndAcrossNmi(rawSearchKts, minTsNmi);
			if (sgndAcrossNmiA != sgndAcrossNmiB) {
				s += String.format(" SgndAc:%.2f;%.2f", sgndAcrossNmiA,
						sgndAcrossNmiB);
			} else {
				s += String.format(" SgndAc:%.2f", sgndAcrossNmiA);
			}
			final double tsNmiB = computeTsNmi(rawSearchKts, minTsNmi);
			final int nSearchLegsB = computeNSearchLegs(rawSearchKts, minTsNmi);
			final int nHalfLapsB = computeNHalfLaps(rawSearchKts, minTsNmi);
			s += String.format("\n\tTsNhlNsl:%.2f,%d,%d", tsNmiB, nHalfLapsB,
					nSearchLegsB);
			final double firstLegHdg = getFirstLegHdg();
			final boolean firstTurnRight = getFirstTurnRight();
			if (firstTurnRight) {
				s += String.format(" Hdg:%.2f-Rt", firstLegHdg);
			} else {
				s += String.format(" Hdg:%.2f-Lt", firstLegHdg);
			}
			final String refSecsString =
					TimeUtilities.formatTime(_cstRefSecs, /* includeSecs= */true);
			s += String.format(" %dSecs;%s", _searchDurationSecs, refSecsString);
			final String centerString = _center == null ? "NO CENTER" :
					String.format("Cntr%s", _center.getString(2));
			s += String.format(" %s", centerString);
		}
		return s;
	}

	@Override
	public String toString() {
		return getString(_keyValues._rawSearchKts, _keyValues._minTsNmi);
	}

	public boolean isValid() {
		if (onMars()) {
			return true;
		}
		if ((_searchDurationSecs <= 0) || (_cstRefSecs <= 0)) {
			return false;
		}
		if (_patternKind.isVs()) {
			return !Double.isNaN(_specSgndAcrossR);
		}
		if (_patternKind.isPsCs()) {
			if (0d < _specAlongR && Double.isFinite(_specAlongR)) {
				final double acrossR = Math.abs(_specSgndAcrossR);
				if (0d < acrossR && Double.isFinite(acrossR)) {
					return true;
				}
			}
			return false;
		}
		if (_patternKind.isSs()) {
			final double acrossR = Math.abs(_specSgndAcrossR);
			if (0d < acrossR && Double.isFinite(acrossR)) {
				return true;
			}
			return false;
		}
		return false;
	}

	public static String legacyGetTheirStyleString(final double rawSearchKts,
			final double minTsNmi, final MyStyle myStyle) {
		String s = myStyle.legacyGetTheirStyleString(rawSearchKts, minTsNmi);
		s += String.format(" trackSpacing[%f]",
				myStyle.computeTsNmi(rawSearchKts, minTsNmi));
		return s;
	}

	public double computeTsSqNmi(final double rawSearchKts,
			final double minTsNmi) {
		if (onMars()) {
			return Double.POSITIVE_INFINITY;
		}
		if (_patternKind.isPsCs() || _patternKind.isSs()) {
			final double lenNmi = computeLengthNmi(rawSearchKts, minTsNmi);
			final double widNmi = computeWidthNmi(rawSearchKts, minTsNmi);
			return lenNmi * widNmi;
		}
		if (_patternKind.isVs()) {
			final double tsNmi = computeTsNmi(rawSearchKts, minTsNmi);
			return 9d * tsNmi;
		}
		return Double.NaN;
	}

	public double computeEplNmi(final double rawSearchKts,
			final double minTsNmi) {
		if (_patternKind.isPsCs()) {
			if (!(rawSearchKts > 0d)) {
				return Double.NaN;
			}
			final double alongNmi = computeAlongNmi(rawSearchKts, minTsNmi);
			final int nSearchLegs = computeNSearchLegs(rawSearchKts, minTsNmi);
			final double eplNmi = nSearchLegs * alongNmi;
			return eplNmi;
		}
		if (_patternKind.isVs()) {
			final double tsNmi = computeTsNmi(rawSearchKts, minTsNmi);
			return 9d * tsNmi;
		}
		if (_patternKind.isSs()) {
			if (!(rawSearchKts > 0d)) {
				return Double.NaN;
			}
			final int nHalfLaps = computeNHalfLaps(rawSearchKts, minTsNmi);
			final double tsNmi = computeTsNmi(rawSearchKts, minTsNmi);
			final double eplNmi = tsNmi * nHalfLaps * nHalfLaps;
			return eplNmi;
		}
		return Double.NaN;
	}

}
