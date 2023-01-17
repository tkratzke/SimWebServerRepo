/**
 *
 */
package com.skagit.sarops.environment;

import com.skagit.util.navigation.LatLng3;

/**
 * <pre>
 * When we read in the data from a NetCdf file, we organize the data into a
 * collection of NetCdfDataPoints. This collection is stored in a
 * {@link PointCollection}, which finds the right set to use for the
 * different types of interpolation. The point is "constructed piecemeal"
 * via the ctors and the setters. The setters should all have been called to
 * populate the arrays before any of the getters are called.
 */
final public class NetCdfDataPoint {
	final private long[] _refSecsS;
	final private double _defaultDU;
	final private double _defaultDV;
	final private double _altDefaultDU;
	final private double _altDefaultDV;
	final private int[] _riverSeqLcr;
	/** _u, _v, _dU, and _dV are indexed by times. */
	final private float[] _u;
	final private float[] _v;
	private float[] _dU = null;
	private float[] _dV = null;
	private float[] _altDU = null;
	private float[] _altDV = null;
	final private LatLng3 _latLng;

	/**
	 * The main ctor. The values of the vectors _u and _v are set as data is
	 * read, by using the "setter" methods (e.g. {@link #setU(int, double)}).
	 */
	public NetCdfDataPoint(final LatLng3 latLng, final long[] refSecondsS,
			final double defaultDU, final double defaultDV,
			final int[] riverSeqLcr) {
		this(latLng, refSecondsS, defaultDU, defaultDV, riverSeqLcr,
				/* altDefaultDU= */defaultDU, /* altDefaultDV= */defaultDV);
	}

	public NetCdfDataPoint(final LatLng3 latLng, final long[] refSecondsS,
			final double defaultDU, final double defaultDV,
			final int[] riverSeqLcr, final double altDefaultDU,
			final double altDefaultDV) {
		_latLng = latLng;
		_refSecsS = refSecondsS;
		_defaultDU = defaultDU;
		_defaultDV = defaultDV;
		_altDefaultDU = altDefaultDU;
		_altDefaultDV = altDefaultDV;
		final int nRefSecondsS = _refSecsS.length;
		_u = new float[nRefSecondsS];
		_v = new float[nRefSecondsS];
		_riverSeqLcr = riverSeqLcr;
	}

	/** Just used to create something so we can look up by LatLng3. */
	NetCdfDataPoint(final LatLng3 latLng) {
		_latLng = latLng;
		_refSecsS = null;
		_defaultDU = _defaultDV = Double.NaN;
		_altDefaultDU = _defaultDU;
		_altDefaultDV = _defaultDV;
		_u = _v = null;
		_riverSeqLcr = null;
	}

	@Override
	public int hashCode() {
		return _latLng.hashCode();
	}

	@Override
	public boolean equals(final Object o) {
		if (!(o instanceof NetCdfDataPoint)) {
			return false;
		}
		return _latLng.equals(((NetCdfDataPoint) o)._latLng);
	}

	public LatLng3 getLatLng() {
		return _latLng;
	}

	public int[] getRiverSeqLcr() {
		return _riverSeqLcr;
	}

	public DataForOnePointAndTime getDataForOnePointAndTime(
			final int timeIdx) {
		final float[] data = new float[NetCdfUvGetter._NDataComponents];
		data[NetCdfUvGetter.DataComponent.U.ordinal()] = 0.0f;
		data[NetCdfUvGetter.DataComponent.V.ordinal()] = 0.0f;

		data[NetCdfUvGetter.DataComponent.DU.ordinal()] = (float) _defaultDU;
		data[NetCdfUvGetter.DataComponent.DV.ordinal()] = (float) _defaultDV;

		data[NetCdfUvGetter.DataComponent.ALT_DU.ordinal()] =
				(float) _altDefaultDU;
		data[NetCdfUvGetter.DataComponent.ALT_DV.ordinal()] =
				(float) _altDefaultDV;

		if (_u != null) {
			final int uIndex = Math.max(0, Math.min(_u.length - 1, timeIdx));
			data[NetCdfUvGetter.DataComponent.U.ordinal()] = _u[uIndex];
		}
		if (_v != null) {
			final int vIndex = Math.max(0, Math.min(_v.length - 1, timeIdx));
			data[NetCdfUvGetter.DataComponent.V.ordinal()] = _v[vIndex];
		}
		if (_dU != null) {
			final int dUIndex = Math.max(0, Math.min(_dU.length - 1, timeIdx));
			data[NetCdfUvGetter.DataComponent.DU.ordinal()] = _dU[dUIndex];
		}
		if (_dV != null && (0 <= timeIdx && timeIdx < _dV.length)) {
			final int dVIndex = Math.max(0, Math.min(_dV.length - 1, timeIdx));
			data[NetCdfUvGetter.DataComponent.DV.ordinal()] = _dV[dVIndex];
		}
		if (_altDU != null) {
			final int altDUIndex =
					Math.max(0, Math.min(_altDU.length - 1, timeIdx));
			data[NetCdfUvGetter.DataComponent.ALT_DU.ordinal()] =
					_altDU[altDUIndex];
		}
		if (_altDV != null) {
			final int altDVIndex =
					Math.max(0, Math.min(_altDV.length - 1, timeIdx));
			data[NetCdfUvGetter.DataComponent.ALT_DV.ordinal()] =
					_altDU[altDVIndex];
		}
		return new DataForOnePointAndTime(data);
	}

	public void setValue(final NetCdfUvGetter.DataComponent dataComponent,
			final int timeIdx, final float value) {
		switch (dataComponent) {
		case U:
			_u[timeIdx] = value;
			return;
		case V:
			_v[timeIdx] = value;
			return;
		case DU:
			if (_dU == null) {
				_dU = new float[_refSecsS.length];
			}
			_dU[timeIdx] = value;
			return;
		case DV:
			if (_dV == null) {
				_dV = new float[_refSecsS.length];
			}
			_dV[timeIdx] = value;
			return;
		case ALT_DU:
			if (_altDU == null) {
				_altDU = new float[_refSecsS.length];
			}
			_altDU[timeIdx] = value;
			return;
		case ALT_DV:
			if (_altDV == null) {
				_altDV = new float[_refSecsS.length];
			}
			_altDV[timeIdx] = value;
			return;
		default:
		}
		return;
	}

	public void setValues(final NetCdfUvGetter.DataComponent dataComponent,
			final float[] values) {
		switch (dataComponent) {
		case DU:
			_dU = values;
			break;
		case DV:
			_dV = values;
			break;
		case ALT_DU:
			_altDU = values;
			break;
		case ALT_DV:
			_altDV = values;
			break;
		default:
		}
		return;
	}
}
