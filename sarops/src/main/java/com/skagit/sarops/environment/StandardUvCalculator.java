package com.skagit.sarops.environment;

import java.util.Arrays;

import com.skagit.util.navigation.LatLng3;

/**
 * Implements 2-closest or 3-closest interpolation. The
 * {@link #StandardUvGetter(PointCollection, NetCdfDataPoint[], double[])
 * ctor} 's inputs are the reference points and weights for the
 * interpolation. Most of the work to use this is finding the closest
 * points; that's done within
 * {@link PointCollection#getStandardUvCalculator(LatLng3, int)}, which is
 * where the closest points are found. weights must be normalized coming in
 * for this to work.
 */
public class StandardUvCalculator implements UvCalculator {
	final public PointCollection _pointCollection;
	final public NetCdfDataPoint[] _referencePoints;
	final public double[] _weights;

	public StandardUvCalculator(final PointCollection pointCollection,
			final NetCdfDataPoint[] referenceDataPoints, final double[] weights) {
		_pointCollection = pointCollection;
		_referencePoints = referenceDataPoints;
		_weights = weights;
	}

	@Override
	public DataForOnePointAndTime getDataForOnePointAndTime(
			final int timeIdx) {
		final long start = System.currentTimeMillis();
		final float[] uvDuDvAltDuAltDv = new float[6];
		Arrays.fill(uvDuDvAltDuAltDv, Float.NaN);
		final int nReferencePoints = _referencePoints.length;
		FunctionLoop: for (int iDataComponent = 0; iDataComponent < 6;
				++iDataComponent) {
			float thisValue = 0.0f;
			for (int i = 0; i < nReferencePoints; ++i) {
				final NetCdfDataPoint dataPoint = _referencePoints[i];
				final DataForOnePointAndTime dataForOnePointAndTime =
						dataPoint.getDataForOnePointAndTime(timeIdx);
				final float value = dataForOnePointAndTime
						.getValue(NetCdfUvGetter._DataComponents[iDataComponent]);
				if (Float.isNaN(value)) {
					break FunctionLoop;
				}
				thisValue += _weights[i] * value;
			}
			uvDuDvAltDuAltDv[iDataComponent] = thisValue;
		}
		_pointCollection._totalEstimatorTime +=
				System.currentTimeMillis() - start;
		_pointCollection._nEstimatesMade++;
		return new DataForOnePointAndTime(uvDuDvAltDuAltDv);
	}

}
