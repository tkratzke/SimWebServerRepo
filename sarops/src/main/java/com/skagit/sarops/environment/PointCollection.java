package com.skagit.sarops.environment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.skagit.sarops.environment.riverSeqLcrUvCalculator.RiverSeqLcrMachinery;
import com.skagit.sarops.environment.riverSeqLcrUvCalculator.RiverSeqLcrUvCalculator;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.CombinatoricTools;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.StaticUtilities;
import com.skagit.util.massFinder.MassFinderPointFinder;
import com.skagit.util.massFinder.PointFinder;
import com.skagit.util.massFinder.PointFinderData;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.LatLng3;

/**
 * Collects points and uses them to produce the two main uv interpolations.
 */
public class PointCollection {
	/** The collection of points. We do not allow duplicate LatLngs. */
	private ArrayList<NetCdfDataPoint> _dataPoints = null;
	private Set<LatLng3> _distinctLatLngs = null;
	private NetCdfDataPoint[] _sortedDataPoints = null;
	private RiverSeqLcrMachinery _riverSeqLcrMachinery = null;
	private PointFinderData _pointFinderData;
	private Comparator<NetCdfDataPoint> _netCdfDataPointComparator;
	private PointFinder _pointFinder;
	public long _totalEstimatorTime = 0;
	public int _nEstimatesMade = 0;
	private final boolean _haveClosed = false;

	/** ctor and basic add routine. */
	public PointCollection() {
	}

	public void add(final MyLogger logger, final NetCdfDataPoint dataPoint) {
		if (_dataPoints == null) {
			synchronized (this) {
				if (_dataPoints == null) {
					_dataPoints = new ArrayList<>();
					_distinctLatLngs = new HashSet<>();
				}
			}
		}
		final LatLng3 latLng = dataPoint.getLatLng();
		if (_distinctLatLngs.add(latLng)) {
			_dataPoints.add(dataPoint);
		} else {
			final String errorMessage = String.format("Duplicate LatLng %s in set of environmental Points.",
					latLng.getString());
			MyLogger.err(logger, errorMessage);
		}
	}

	public void close(final SimCaseManager.SimCase simCase, final String interpolationMode) {
		if (_haveClosed) {
			return;
		}
		synchronized (this) {
			if (_haveClosed) {
				return;
			}
			final Set<LatLng3> allLatLngSet = new HashSet<>();
			for (final NetCdfDataPoint netCdfDataPoint : _dataPoints) {
				allLatLngSet.add(netCdfDataPoint.getLatLng());
			}
			final int nLatLngs = allLatLngSet.size();
			final LatLng3[] allLatLngs = allLatLngSet.toArray(new LatLng3[nLatLngs]);
			_pointFinderData = new PointFinderData(allLatLngs, /* referenceObjects= */null);
		}
		final Comparator<LatLng3> pointFinderDataXyComparator = _pointFinderData._pointFinderDataXyComparator;
		_netCdfDataPointComparator = new Comparator<>() {
			@Override
			public int compare(final NetCdfDataPoint o1, final NetCdfDataPoint o2) {
				return pointFinderDataXyComparator.compare(o1.getLatLng(), o2.getLatLng());
			}
		};
		_sortedDataPoints = _dataPoints.toArray(new NetCdfDataPoint[_dataPoints.size()]);
		Arrays.sort(_sortedDataPoints, _netCdfDataPointComparator);
		if (interpolationMode.compareTo(Model._CenterDominated) == 0
				|| interpolationMode.compareTo(Model._UseAllStrips) == 0) {
			_riverSeqLcrMachinery = new RiverSeqLcrMachinery(simCase, _sortedDataPoints, interpolationMode,
					/* debug= */false);
			if (!_riverSeqLcrMachinery.canDoRiverineInterpolation()) {
				final String infoString = "@@@ Inadequate data for riverine " + "interpolation (missing sequence "
						+ "numbers(?)).  Switching to 2Closest. @@@";
				SimCaseManager.out(simCase, infoString);
				_riverSeqLcrMachinery = null;
			}
		}
		if (_riverSeqLcrMachinery != null) {
			_pointFinder = null;
			return;
		}
		/**
		 * At this point, we are not using riverine. We must prepare a "standard"
		 * PointFinder. We do this by using the PointFinderData.
		 */
		final MassFinderPointFinder massFinderPointFinder = new MassFinderPointFinder(_pointFinderData);
		_pointFinder = massFinderPointFinder;
	}

	/**
	 * Produces the standard (2-closest or 3-closest) "Getters." We find the
	 * k-closest (in position) points and store them for interpolation purposes for
	 * any time step.
	 *
	 * @param latLng             The point we're trying to get uv values for.
	 * @param nToInterpolateWith Either 2 or 3. Determines how many to interpolate
	 *                           with.
	 */
	public StandardUvCalculator getStandardUvCalculator(final LatLng3 latLng, final int nToInterpolateWith) {
		final ArrayList<LatLng3> referenceLatLngs = _pointFinder.getClosestPoints(latLng, nToInterpolateWith);
		final int nReferencePoints = referenceLatLngs.size();
		final NetCdfDataPoint[] referencePoints = new NetCdfDataPoint[nReferencePoints];
		final double[] dSquareds = new double[nReferencePoints];
		final double[] xy = _pointFinderData.getXy(latLng);
		for (int k = 0; k < nReferencePoints; ++k) {
			final LatLng3 referenceLatLng = referenceLatLngs.get(k);
			final double[] referenceXy = _pointFinderData.getXy(referenceLatLng);
			final double xDiff = xy[0] - referenceXy[0];
			final double yDiff = xy[1] - referenceXy[1];
			dSquareds[k] = xDiff * xDiff + yDiff * yDiff;
			final NetCdfDataPoint lookUp = new NetCdfDataPoint(referenceLatLng);
			final int glbIndex = CombinatoricTools.getGlbIndex(_sortedDataPoints, lookUp, _netCdfDataPointComparator);
			referencePoints[k] = _sortedDataPoints[glbIndex];
		}
		/**
		 * We now have reference points and dSquareds for computing the weights.
		 */
		final double[] weights = new double[nReferencePoints];
		double totalWeight = 0d;
		for (int iPass = 0; iPass < 2; ++iPass) {
			for (int i = 0; i < nReferencePoints; ++i) {
				if (iPass == 0) {
					/** If some reference point coincides, it gets all the weight. */
					if (NumericalRoutines.compare(dSquareds[i], 0d) == 0) {
						return new StandardUvCalculator(this, new NetCdfDataPoint[] {
								referencePoints[i]
						}, new double[] {
								1d
						});
					}
					weights[i] = 1d / Math.sqrt(dSquareds[i]);
					totalWeight += weights[i];
				} else {
					weights[i] /= totalWeight;
				}
			}
		}
		return new StandardUvCalculator(this, referencePoints, weights);
	}

	/**
	 * This gives a "Riverine" interpolation scheme.
	 *
	 * @param latLng            Position of interest.
	 * @param interpolationMode See {@link com.skagit.sarops.model.Model#_2Closest},
	 *                          et. al.
	 * @param debug             Set it to false.
	 */
	public RiverSeqLcrUvCalculator getRiverSeqLcrUvCalculator(final SimCaseManager.SimCase simCase,
			final LatLng3 latLng, final String interpolationMode) {
		if (_riverSeqLcrMachinery != null) {
			return new RiverSeqLcrUvCalculator(simCase, _riverSeqLcrMachinery, latLng);
		}
		return null;
	}

	boolean canDoRiverineInterpolation() {
		return _riverSeqLcrMachinery != null;
	}

	public List<NetCdfDataPoint> getDataPoints() {
		return _dataPoints;
	}

	public boolean isEmpty() {
		if (_riverSeqLcrMachinery == null) {
			/** We're relying on _pointFinder. */
			return _pointFinder == null || _pointFinder.isEmpty();
		}
		return _riverSeqLcrMachinery.isEmpty();
	}

	public void freeMemory() {
		if (_pointFinder != null) {
			_pointFinder.freeMemory();
		}
		if (_riverSeqLcrMachinery != null) {
			_riverSeqLcrMachinery.freeMemory();
		}
		StaticUtilities.clearList(_dataPoints);
	}
}
