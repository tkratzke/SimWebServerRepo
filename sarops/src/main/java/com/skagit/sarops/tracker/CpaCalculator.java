package com.skagit.sarops.tracker;

import com.skagit.sarops.computePFail.DetectValues;
import com.skagit.sarops.model.Sortie;
import com.skagit.sarops.model.Sortie.Leg;
import com.skagit.util.MathX;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.navigation.GreatCircleCalculator;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.NavigationCalculator;
import com.skagit.util.navigation.NavigationCalculatorStatics;
import com.skagit.util.navigation.TangentCylinder;

public class CpaCalculator {

	static final public int _NLatLngDigits = 5;
	static final public String _DistanceKey = "Ds";
	static final public String _TimeKey = "Tm";
	static final public String _TimeKey1 = "Tm1";
	static final public String _Leg1Key = "L1";
	static final public String _Leg2Key = "L2";
	static final public String _ParticleKey = "Pt";

	final private Sortie.Leg _leg;
	final NavigationCalculator _legNavCalc;
	final private long _legRefSecs0;
	final private long _legRefSecs1;
	final private long _legDurationSecs;

	public CpaCalculator(final Sortie.Leg leg) {
		_leg = leg;
		final LatLng3 legLatLng0 = leg.getLegLatLng0();
		final LatLng3 legLatLng1 = leg.getLegLatLng1();
		_legRefSecs0 = leg.getLegRefSecs0();
		_legRefSecs1 = leg.getLegRefSecs1();
		_legDurationSecs = _legRefSecs1 - _legRefSecs0;
		_legNavCalc = NavigationCalculatorStatics.buildWithSeconds(legLatLng0,
				_legRefSecs0, legLatLng1, _legDurationSecs,
				_leg.getSortie().getMotionType());
	}

	public TangentCylinder.FlatLatLng getLegPosition(final long refSecs) {
		if (refSecs <= _legRefSecs0) {
			return (TangentCylinder.FlatLatLng) _leg.getLegLatLng0();
		}
		final long legRefSecs1 = _legRefSecs0 + _legDurationSecs;
		if (refSecs >= legRefSecs1) {
			return (TangentCylinder.FlatLatLng) _leg.getLegLatLng1();
		}
		final LatLng3 latLng = _legNavCalc.getPosition(refSecs);
		final TangentCylinder tc = _leg.getTangentCylinder();
		return tc.convertToMyFlatLatLng(latLng);
	}

	public double getLegHdg(final long refSecs) {
		final GreatCircleArc gca = _leg.getGca();
		if (refSecs <= _legRefSecs0) {
			return gca.getRawInitialHdg();
		}
		if (refSecs >= _legRefSecs1) {
			final double hdg =
					LatLng3.getInRange0_360(gca.getRawReverseHdg() + 180d);
			return hdg;
		}
		final LatLng3 latLng = _legNavCalc.getPosition(refSecs);
		final LatLng3 latLng1 = gca.getLatLng1();
		final double hdg = MathX.initialHdgX(latLng, latLng1);
		return hdg;
	}

	public Leg getLeg() {
		return _leg;
	}

	public class Result {
		final private LatLng3 _ptclStart, _ptclStop;
		final private LatLng3 _sruAtCpa, _ptclAtCpa;
		final private double _cpaNmi;
		private double _ccwTwistToPtrtclAtCpa;
		final private long _cpaRefSecs;
		private double _tempPFailValue;
		private double _ftPFail;
		private double _aiftPFail;
		@SuppressWarnings("unused")
		final private ParticleIndexes _prtclIndxs;

		public Result(final long intrvlStartRefSecs,
				final long intrvlStopRefSecs, final long cpaRefSecs,
				final LatLng3 ptclStart, final LatLng3 ptclStop,
				final ParticleIndexes prtclIndxs) {
			_tempPFailValue = _ftPFail = _aiftPFail = Double.NaN;
			_cpaRefSecs = cpaRefSecs;
			_ptclStart = ptclStart;
			_ptclStop = ptclStop;
			_prtclIndxs = prtclIndxs;
			_sruAtCpa = getLegPosition(_cpaRefSecs);
			final double durationSecs = intrvlStopRefSecs - intrvlStartRefSecs;
			final double p = (cpaRefSecs - intrvlStartRefSecs) / durationSecs;
			final double q = 1d - p;
			final TangentCylinder tc = _leg.getTangentCylinder();
			final TangentCylinder.FlatLatLng flatPtclStart =
					tc.convertToMyFlatLatLng(ptclStart);
			final TangentCylinder.FlatLatLng flatPtclStop =
					tc.convertToMyFlatLatLng(ptclStop);
			final double ptclEastOffset = q * flatPtclStart.getEastOffset() +
					p * flatPtclStop.getEastOffset();
			final double ptclNorthOffset = q * flatPtclStart.getNorthOffset() +
					p * flatPtclStop.getNorthOffset();
			_ptclAtCpa = tc.new FlatLatLng(ptclEastOffset, ptclNorthOffset);
			_cpaNmi = GreatCircleCalculator.getNmi(_sruAtCpa, _ptclAtCpa);
			_ccwTwistToPtrtclAtCpa = Double.NaN;
		}

		public void setTempPFailValue(final double tempPFail) {
			_tempPFailValue = tempPFail;
		}

		public double getTempPFailValue() {
			return _tempPFailValue;
		}

		public double getCcwTwistToPtrtclAtCpa() {
			if (!Double.isNaN(_ccwTwistToPtrtclAtCpa)) {
				return _ccwTwistToPtrtclAtCpa;
			}
			synchronized (this) {
				if (!Double.isNaN(_ccwTwistToPtrtclAtCpa)) {
					return _ccwTwistToPtrtclAtCpa;
				}
				final double sruHdg;
				final GreatCircleArc legGca = _leg.getGca();
				if (_sruAtCpa.equals(legGca.getLatLng1())) {
					sruHdg = legGca.getRawReverseHdg();
				} else {
					sruHdg = MathX.initialHdgX(_sruAtCpa, legGca.getLatLng1());
				}
				final double hdgToPrtcl = MathX.initialHdgX(_sruAtCpa, _ptclAtCpa);
				_ccwTwistToPtrtclAtCpa =
						LatLng3.getInRange0_360(sruHdg - hdgToPrtcl);
			}
			return _ccwTwistToPtrtclAtCpa;
		}

		public void setTempPFailToPfail(
				final DetectValues.PFailType pFailType) {
			switch (pFailType) {
			case AIFT:
				_aiftPFail = _tempPFailValue;
				break;
			case FT:
				_ftPFail = _tempPFailValue;
				break;
			default:
				assert false : "CpaCalculator should only be setting ftPFails.";
				break;
			}
			_tempPFailValue = Double.NaN;
		}

		public Sortie.Leg getLeg() {
			return _leg;
		}

		public double getPFail(final DetectValues.PFailType pFailType) {
			switch (pFailType) {
			case AIFT:
				return _aiftPFail;
			case FT:
				return _ftPFail;
			default:
				assert false : "CpaCalculator should only be retrieving ftPFails.";
				return Double.NaN;
			}
		}

		public LatLng3 getSruAtCpa() {
			return _sruAtCpa;
		}

		public long getCpaRefSecs() {
			return _cpaRefSecs;
		}

		public LatLng3 getParticleAtCpa() {
			return _ptclAtCpa;
		}

		public double getCpaNmi() {
			return _cpaNmi;
		}

		public LatLng3 getPtclStart() {
			return _ptclStart;
		}

		public LatLng3 getPtclStop() {
			return _ptclStop;
		}

		public String getString() {
			final String s = String.format(
					"Ptcl Start/Stop[%s/%s] Nmi[%.4f] ftPFail[%.4g] AiftPFail[%.4g]",
					_ptclStart.getString(), _ptclStop.getString(), _cpaNmi, _ftPFail,
					_aiftPFail);
			return s;
		}

		@Override
		public String toString() {
			return getString();
		}
	}
}
