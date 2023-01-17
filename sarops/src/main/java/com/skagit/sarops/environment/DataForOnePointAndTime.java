package com.skagit.sarops.environment;

final public class DataForOnePointAndTime implements Cloneable {

	final private float _u;
	final private float _v;
	final private float _dU;
	final private float _dV;
	final private float _altDu;
	final private float _altDv;

	public DataForOnePointAndTime(final float[] uVDuDvAltDuAltDv) {
		_u = uVDuDvAltDuAltDv[NetCdfUvGetter.DataComponent.U.ordinal()];
		_v = uVDuDvAltDuAltDv[NetCdfUvGetter.DataComponent.V.ordinal()];
		_dU = uVDuDvAltDuAltDv[NetCdfUvGetter.DataComponent.DU.ordinal()];
		_dV = uVDuDvAltDuAltDv[NetCdfUvGetter.DataComponent.DV.ordinal()];
		_altDu =
				uVDuDvAltDuAltDv[NetCdfUvGetter.DataComponent.ALT_DU.ordinal()];
		_altDv =
				uVDuDvAltDuAltDv[NetCdfUvGetter.DataComponent.ALT_DV.ordinal()];
	}

	public DataForOnePointAndTime(final float u, final float v,
			final float dU, final float dV, final float altDu,
			final float altDv) {
		_u = u;
		_v = v;
		_dU = dU;
		_dV = dV;
		_altDu = altDu;
		_altDv = altDv;
		isValid();
	}

	private DataForOnePointAndTime(
			final DataForOnePointAndTime dataForOnePointAndTime) {
		_u = dataForOnePointAndTime._u;
		_v = dataForOnePointAndTime._v;
		_dU = dataForOnePointAndTime._dU;
		_dV = dataForOnePointAndTime._dV;
		_altDu = dataForOnePointAndTime._altDu;
		_altDv = dataForOnePointAndTime._altDv;
	}

	@Override
	public DataForOnePointAndTime clone() {
		return new DataForOnePointAndTime(this);
	}

	/** Accessors. */
	public float getValue(final NetCdfUvGetter.DataComponent dataComponent) {
		switch (dataComponent) {
		case U:
			return _u;
		case V:
			return _v;
		case DU:
			return _dU;
		case DV:
			return _dV;
		case ALT_DU:
			return _altDu;
		case ALT_DV:
			return _altDv;
		default:
			return Float.NaN;
		}
	}

	public boolean isValid() {
		final boolean valid = !Float.isNaN(_u) && !Float.isNaN(_v) &&
				_dU >= 0f && _dV >= 0f && _altDu >= 0f && _altDv >= 0f;
		if (!valid) {
			System.err.print("\n**** Invalid DataPoint!!");
		}
		return valid;
	}

}
