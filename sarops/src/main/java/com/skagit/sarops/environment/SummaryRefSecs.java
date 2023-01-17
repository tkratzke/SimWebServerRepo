package com.skagit.sarops.environment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.skagit.sarops.environment.riverSeqLcrUvCalculator.RiverSeqLcrUvCalculator;
import com.skagit.util.CombinatoricTools;
import com.skagit.util.gshhs.CircleOfInterest;
import com.skagit.util.navigation.GreatCircleCalculator;
import com.skagit.util.navigation.LatLng3;

/** Primarily used for collecting data to examine when debugging. */
public class SummaryRefSecs {
	final public LatLng3[] _latLngs;
	final public float[] _u;
	final public float[] _v;
	final public String _explanatoryString;
	final public int[][] _riverSeqLcr;

	public SummaryRefSecs(final CircleOfInterest coi, final long refSecs,
			final long[] refSecsS, final PointCollection pointCollection,
			final boolean interpolateInTime, final String explanatoryString) {
		_explanatoryString = explanatoryString;
		final int glbIndex =
				Math.max(0, CombinatoricTools.getGlbIndex(refSecsS, refSecs));
		final List<NetCdfDataPoint> dataPoints =
				pointCollection.getDataPoints();
		final int nDataPoints = dataPoints.size();
		final LatLng3[] latLngs = new LatLng3[nDataPoints];
		final float[] u = new float[nDataPoints];
		final float[] v = new float[nDataPoints];
		final boolean riverine = pointCollection.canDoRiverineInterpolation();
		final int[][] riverSeqLcr = riverine ? new int[nDataPoints][] : null;
		final float p;
		final int lowIndex;
		final int highIndex;
		if (interpolateInTime) {
			lowIndex = Math.min(refSecsS.length - 2, glbIndex);
			final double timeFromLowToData = refSecs - refSecsS[lowIndex];
			highIndex = lowIndex + 1;
			final double timeFromLowToHigh =
					refSecsS[highIndex] - refSecsS[lowIndex];
			p = (float) (timeFromLowToData / timeFromLowToHigh);
		} else {
			p = Float.NaN;
			lowIndex = highIndex = Integer.MIN_VALUE;
		}
		for (int k = 0; k < nDataPoints; ++k) {
			final NetCdfDataPoint dataPoint = dataPoints.get(k);
			final LatLng3 latLng = dataPoint.getLatLng();
			latLngs[k] = latLng;
			if (Float.isNaN(p)) {
				final DataForOnePointAndTime dataForOnePointAndTime =
						dataPoint.getDataForOnePointAndTime(glbIndex);
				u[k] =
						dataForOnePointAndTime.getValue(NetCdfUvGetter.DataComponent.U);
				v[k] =
						dataForOnePointAndTime.getValue(NetCdfUvGetter.DataComponent.V);
			} else {
				final DataForOnePointAndTime lowDataPoint =
						dataPoint.getDataForOnePointAndTime(lowIndex);
				final DataForOnePointAndTime highDataPoint =
						dataPoint.getDataForOnePointAndTime(highIndex);
				u[k] = (1 - p) *
						lowDataPoint.getValue(NetCdfUvGetter.DataComponent.U) +
						p * highDataPoint.getValue(NetCdfUvGetter.DataComponent.U);
				v[k] = (1 - p) *
						lowDataPoint.getValue(NetCdfUvGetter.DataComponent.V) +
						p * highDataPoint.getValue(NetCdfUvGetter.DataComponent.V);
			}
			if (riverine) {
				riverSeqLcr[k] = dataPoint.getRiverSeqLcr();
			}
		}
		if (nDataPoints > 1) {
			_latLngs = latLngs;
			_u = u;
			_v = v;
			_riverSeqLcr = riverSeqLcr;
			return;
		}
		final float[] uV = new float[] { u[0], v[0] };
		final SummaryRefSecs summaryRefSecs = new SummaryRefSecs(coi,
				explanatoryString, uV, /* riverine= */false);
		_latLngs = summaryRefSecs._latLngs;
		_u = summaryRefSecs._u;
		_v = summaryRefSecs._v;
		_riverSeqLcr = summaryRefSecs._riverSeqLcr;
	}

	public SummaryRefSecs(final LatLng3[] latLngs, final float[] u,
			final float[] v, final String explanatoryString) {
		_latLngs = latLngs;
		_u = u;
		_v = v;
		_explanatoryString = explanatoryString;
		_riverSeqLcr = null;
	}

	/** For constant environments: */
	public SummaryRefSecs(final CircleOfInterest coi,
			final String explanatoryString, final float[] uv,
			final boolean riverine) {
		_explanatoryString = explanatoryString;
		if (coi == null) {
			_latLngs = null;
			_u = _v = null;
			_riverSeqLcr = null;
			return;
		}
		final ArrayList<LatLng3> latLngList = new ArrayList<>();
		final LatLng3 center = coi.getCentralLatLng();
		final double rNmiInc = 10d;
		for (int iPass0 = 0; iPass0 < 2; ++iPass0) {
			for (int row = 0;; ++row) {
				final double offset0;
				final double hdg0;
				if (iPass0 == 0) {
					offset0 = row * rNmiInc;
					hdg0 = 180d;
				} else {
					offset0 = (1 + row) * rNmiInc;
					hdg0 = 0d;
				}
				final LatLng3 spinalPoint =
						GreatCircleCalculator.getLatLng(center, hdg0, offset0);
				if (!coi.contains(spinalPoint)) {
					break;
				}
				for (int iPass1 = 0; iPass1 < 2; ++iPass1) {
					for (int clmn = 0;; ++clmn) {
						final double offset1;
						final double hdg1;
						if (iPass1 == 0) {
							offset1 = clmn * rNmiInc;
							hdg1 = 90d;
						} else {
							offset1 = (1 + clmn) * rNmiInc;
							hdg1 = 270d;
						}
						final LatLng3 point =
								GreatCircleCalculator.getLatLng(spinalPoint, hdg1, offset1);
						if (!coi.contains(point)) {
							break;
						}
						latLngList.add(point);
					}
				}
			}
		}
		final int nPoints = latLngList.size();
		_latLngs = latLngList.toArray(new LatLng3[nPoints]);
		Arrays.sort(_latLngs, LatLng3._ByLatThenLng);
		_u = new float[nPoints];
		_v = new float[nPoints];
		_riverSeqLcr = riverine ? new int[nPoints][] : null;
		for (int k = 0; k < nPoints; ++k) {
			_u[k] = uv[0];
			_v[k] = uv[1];
			if (riverine) {
				_riverSeqLcr[k] =
						new int[] { 0, 0, RiverSeqLcrUvCalculator._InputCenter };
			}
		}
	}

	public interface SummaryBuilder {
		SummaryRefSecs getSummaryForRefSecs(CircleOfInterest coi,
				final long refSecs, final int iView,
				final boolean interpolateInTime);
	}
}
