package com.skagit.sarops.environment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.TreeSet;

import com.skagit.sarops.environment.SummaryRefSecs.SummaryBuilder;
import com.skagit.sarops.environment.riverSeqLcrUvCalculator.RiverSeqLcrUvCalculator;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.simCaseManager.MainRunner;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.Constants;
import com.skagit.util.MathX;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.StringUtilities;
import com.skagit.util.TimeUtilities;
import com.skagit.util.gshhs.CircleOfInterest;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;

import ucar.ma2.Array;
import ucar.ma2.Index;
import ucar.ma2.IndexIterator;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

/**
 * Collects the data from the NetCdf file, puts it into a
 * {@link PointCollection}, and uses it to get uv vectors. The key routines are
 * {@link #read()}, (which populates the PointCollection), and
 * {@link #getDataForOnePointAndTime(long, LatLng3, String, boolean)}.
 */

@SuppressWarnings("deprecation")
public class NetCdfUvGetter implements SummaryBuilder {
	public enum DataComponent {
		U, V, DU, DV, ALT_DU, ALT_DV
	}

	public static final DataComponent[] _DataComponents = DataComponent.values();
	public static final int _NDataComponents = _DataComponents.length;

	final private SimCaseManager.SimCase _simCase;

	/** The data from the NetCdf file. */
	protected final PointCollection _pointCollection;
	final private String[] _uNames;
	final private String[] _vNames;
	final private String[] _speedNames;
	final private String[] _directionNames;
	final private String[] _uUncNames;
	final private String[] _vUncNames;
	final private String[] _altUUncNames;
	final private String[] _altVUncNames;
	final private String[] _riverNames;
	final private String[] _seqNames;
	final private String[] _lcrNames;
	final private boolean[] _warnOnReadBeforeInitialTime = new boolean[] {
			true
	};
	final private boolean[] _warnOnReadAfterLastTime = new boolean[] {
			true
	};
	final private NetcdfFile _netCdfFile;
	final private String _filePath;
	final private Model _model;
	/** The following identifies whether or not it's Drifts or Winds. */
	final private String _tag;
	/** The following are the distinct times. */
	final private long[] _refSecsS;
	/** The following are the distinct lats and lngs. */
	private double[] _lats;
	private double[] _lngs;

	final private boolean _dataIsDownStreamOrDownWind;
	final private long _halfLifeInSecs;
	final private long _preDistressHalfLifeInSecs;
	final private double _defaultDU;
	final private double _defaultDV;
	final private double _defaultAltDU;
	final private double _defaultAltDV;

	public static int getValueIndex(final int function, final int timeIdx) {
		return timeIdx * 6 + function;
	}

	public static class NetCdfUvGetterException extends Exception {
		final private static long serialVersionUID = 8070614064150612619L;

		public NetCdfUvGetterException(final NetCdfUvGetter netCdfUvGetter, final String message) {
			this(netCdfUvGetter, message, null);
		}

		public NetCdfUvGetterException(final NetCdfUvGetter netCdfUvGetter, final String message,
				final Throwable error) {
			super("Error reading netcdf file: " + netCdfUvGetter._filePath + " :" + message, error);
		}
	}

	/** For reading from the disc. */
	public NetCdfUvGetter(final SimCaseManager.SimCase simCase, final Model model, final String tag,
			final String filePath, final String[] uNames, final String[] vNames, final String[] speedNames,
			final String[] directionNames, final String[] uUncNames, final String[] vUncNames,
			final String[] altUUncNames, final String[] altVUncNames, final String[] riverNames,
			final String[] seqNames, final String[] inputLcrNames, //
			final double defaultDU, final double defaultDV, //
			final double defaultAltDU, final double defaultAltDV, //
			final long halfLifeInSecs, final long preDistressHalfLifeInSecs, final boolean dataIsDownStreamOrDownWind) {
		NetCdfUvGetter netCdfUvGetter = null;
		try (NetcdfFile netCdfFile = NetcdfFile.open(filePath)) {
			netCdfUvGetter = new NetCdfUvGetter(simCase, model, tag, filePath, netCdfFile, uNames, vNames, speedNames,
					directionNames, uUncNames, vUncNames, altUUncNames, altVUncNames, riverNames, seqNames,
					inputLcrNames, defaultDU, defaultDV, defaultAltDU, defaultAltDV, halfLifeInSecs,
					preDistressHalfLifeInSecs, dataIsDownStreamOrDownWind);
		} catch (final Exception e) {
			MainRunner.HandleFatal(simCase, new RuntimeException(e));
		}
		_simCase = simCase;
		_pointCollection = netCdfUvGetter._pointCollection;
		_uNames = netCdfUvGetter._uNames;
		_vNames = netCdfUvGetter._vNames;
		_speedNames = netCdfUvGetter._speedNames;
		_directionNames = netCdfUvGetter._directionNames;
		_uUncNames = netCdfUvGetter._uUncNames;
		_vUncNames = netCdfUvGetter._vUncNames;
		_altUUncNames = netCdfUvGetter._altUUncNames;
		_altVUncNames = netCdfUvGetter._altVUncNames;
		_riverNames = netCdfUvGetter._riverNames;
		_seqNames = netCdfUvGetter._seqNames;
		_lcrNames = netCdfUvGetter._lcrNames;
		_netCdfFile = netCdfUvGetter._netCdfFile;
		_filePath = netCdfUvGetter._filePath;
		_model = netCdfUvGetter._model;
		_tag = netCdfUvGetter._tag;
		_refSecsS = netCdfUvGetter._refSecsS;
		_lats = netCdfUvGetter._lats;
		_lngs = netCdfUvGetter._lngs;
		_dataIsDownStreamOrDownWind = netCdfUvGetter._dataIsDownStreamOrDownWind;
		_halfLifeInSecs = netCdfUvGetter._halfLifeInSecs;
		_preDistressHalfLifeInSecs = netCdfUvGetter._preDistressHalfLifeInSecs;
		_defaultDU = netCdfUvGetter._defaultDU;
		_defaultDV = netCdfUvGetter._defaultDV;
		_defaultAltDU = netCdfUvGetter._defaultAltDU;
		_defaultAltDV = netCdfUvGetter._defaultAltDV;
	}

	/**
	 * This ctor reads the file using the provided NetcdfFile (3rd party class) and
	 * populates the UvGetter. The caller must close netcdfFile. filePath is
	 * provided in the argument list only for reference and reporting.
	 */
	public NetCdfUvGetter(final SimCaseManager.SimCase simCase, final Model model, final String tag,
			final String filePath, final NetcdfFile netCdfFile, final String[] uNames, final String[] vNames,
			final String[] speedNames, final String[] directionNames, //
			final String[] uUncNames, final String[] vUncNames, //
			final String[] altUUncNames, final String[] altVUncNames, //
			final String[] riverNames, final String[] seqNames, final String[] inputLcrNames, //
			final double defaultDU, final double defaultDV, //
			final double defaultAltDU, final double defaultAltDV, //
			final long halfLifeInSecs, final long preDistressHalfLifeInSecs, final boolean dataIsDownStreamOrDownWind)
			throws NetCdfUvGetterException {
		_simCase = simCase;
		_model = model;
		_tag = tag;
		_dataIsDownStreamOrDownWind = dataIsDownStreamOrDownWind;
		_defaultDU = defaultDU;
		_defaultDV = defaultDV;
		_defaultAltDU = defaultAltDU;
		_defaultAltDV = defaultAltDV;
		_halfLifeInSecs = halfLifeInSecs;
		_preDistressHalfLifeInSecs = preDistressHalfLifeInSecs;
		_filePath = filePath;
		if (netCdfFile == null) {
			final Exception e = new Exception("Bad NetCdfFile");
			final String errorString = String.format("\nCannot open file[%s]%s", filePath,
					StringUtilities.getStackTraceString(e));
			throw new NetCdfUvGetterException(this, errorString, e);
		}
		final Attribute classAttribute = netCdfFile.findGlobalAttribute("netcdf_class");
		if (classAttribute == null || "1".equals(classAttribute.getStringValue())) {
			_pointCollection = new PointCollection();
			_uNames = uNames;
			_vNames = vNames;
			_speedNames = speedNames;
			_directionNames = directionNames;
			_uUncNames = uUncNames;
			_vUncNames = vUncNames;
			_altUUncNames = altUUncNames;
			_altVUncNames = altVUncNames;
			_riverNames = riverNames;
			_seqNames = seqNames;
			_lcrNames = inputLcrNames;
		} else {
			throw new NetCdfUvGetterException(this, "Unknown netcdf_class: " + classAttribute.getStringValue());
		}
		_netCdfFile = netCdfFile;
		_refSecsS = setTimeDimension();
		_lats = _lngs = null;
		/** Read the data in now. */
		final String[] cellDimensionNames = {
				"ncell", "ncells", "station", "stations", "nstation", "nstations"
		};
		Dimension cellDimension = null;
		for (int dimensionIndex = 0; cellDimension == null
				&& dimensionIndex < cellDimensionNames.length; ++dimensionIndex) {
			cellDimension = _netCdfFile.findDimension(cellDimensionNames[dimensionIndex]);
		}
		if (cellDimension == null) {
			throw new NetCdfUvGetterException(NetCdfUvGetter.this, "Unable to find cell dimension");
		}
		try {
			final Variable lngVariable = _netCdfFile.findVariable("lon");
			final Array lngs = lngVariable.read();
			final Variable latVariable = _netCdfFile.findVariable("lat");
			final Array lats = latVariable.read();
			final String riverName = findValidVariableName(_riverNames);
			final String seqName = findValidVariableName(_seqNames);
			final String lcrName = findValidVariableName(_lcrNames);
			final Array rivers;
			final Array seqs;
			final Array inputLcrs;
			if (riverName != null && seqName != null && lcrName != null) {
				final Variable riverVariable = _netCdfFile.findVariable(riverName);
				rivers = riverVariable.read();
				final Variable seqNoVariable = _netCdfFile.findVariable(seqName);
				seqs = seqNoVariable.read();
				final Variable inputLcrVariable = _netCdfFile.findVariable(lcrName);
				inputLcrs = inputLcrVariable.read();
			} else {
				rivers = seqs = inputLcrs = null;
			}
			final IndexIterator lngIndexIterator = lngs.getIndexIterator();
			final IndexIterator latIndexIterator = lats.getIndexIterator();
			final IndexIterator riverIndexIterator;
			final IndexIterator seqIndexIterator;
			final IndexIterator inputLcrIndexIterator;
			if (rivers != null && seqs != null && inputLcrs != null) {
				riverIndexIterator = rivers.getIndexIterator();
				seqIndexIterator = seqs.getIndexIterator();
				inputLcrIndexIterator = inputLcrs.getIndexIterator();
			} else {
				riverIndexIterator = seqIndexIterator = inputLcrIndexIterator = null;
			}
			/** Compute the vectors _lats and _lngs. */
			final TreeSet<Float> distinctLats = new TreeSet<>();
			final TreeSet<Float> distinctLngs = new TreeSet<>();
			double leftLng = Double.NaN, lngRange = 0;
			/**
			 * We assume latIndexIterator will run out the same time lngIndexIterator does.
			 * Hence, we just check on lngIndexIterator.hasNext().
			 */
			while (lngIndexIterator.hasNext()) {
				final float lat = (float) LatLng3.roundToLattice180_180I(latIndexIterator.getFloatNext());
				final float lng = (float) LatLng3.roundToLattice180_180I(lngIndexIterator.getFloatNext());
				distinctLats.add(lat);
				distinctLngs.add(lng);
				if (Double.isNaN(leftLng)) {
					leftLng = lng;
					lngRange = 0;
				} else {
					if (LatLng3.degsToEast0_360L(leftLng, lng) > lngRange) {
						final double lngRange1 = LatLng3.degsToEast0_360L(leftLng, lng);
						final double lngRange2 = LatLng3.degsToEast0_360L(lng, leftLng + lngRange);
						if (lngRange1 < lngRange2) {
							lngRange = lngRange1;
						} else {
							leftLng = lng;
							lngRange = lngRange2;
						}
					}
				}
				/** Deal with rivers. */
				final int[] riverSeqLcr;
				if (rivers == null || seqs == null) {
					riverSeqLcr = null;
				} else {
					final int intRiver = riverIndexIterator.getIntNext();
					final byte river = (byte) intRiver;
					final int seq = seqIndexIterator.getIntNext();
					final int intInputLcr = inputLcrs == null ? RiverSeqLcrUvCalculator._InputCenter
							: inputLcrIndexIterator.getIntNext();
					final byte inputLcr = (byte) intInputLcr;
					final byte lcr = RiverSeqLcrUvCalculator._InputLcrToLcr.get(inputLcr);
					riverSeqLcr = new int[] {
							river, seq, lcr
					};
				}
				final NetCdfDataPoint netCdfDataPoint = new NetCdfDataPoint(LatLng3.getLatLngB(lat, lng), _refSecsS,
						_defaultDU, _defaultDV, riverSeqLcr);
				_pointCollection.add(_simCase, netCdfDataPoint);
			}
			if (lngIndexIterator.hasNext() != latIndexIterator.hasNext()) {
				SimCaseManager.err(_simCase, "@@@ LngIndexIterator out of synch with LatIndexIterator!! @@@");
			}
			/** Convert distinctLats to an array. */
			final int nLats = distinctLats.size();
			_lats = new double[nLats];
			final Iterator<Float> latIterator = distinctLats.iterator();
			for (int k = 0; k < nLats; ++k) {
				_lats[k] = latIterator.next();
			}
			/**
			 * Convert distinctLngs to a sorted array; _lngs[0] will be the "leftLng."
			 */
			final int nLngs = distinctLngs.size();
			final Float[] sortedLngs = distinctLngs.toArray(new Float[nLngs]);
			final double finalLeftLng = leftLng;
			final Comparator<Float> floatComparator = new Comparator<>() {
				@Override
				public int compare(final Float o1, final Float o2) {
					final double degToEast1 = LatLng3.degsToEast0_360L(finalLeftLng, o1);
					final double degToEast2 = LatLng3.degsToEast0_360L(finalLeftLng, o2);
					return degToEast1 < degToEast2 ? -1 : (degToEast1 > degToEast2 ? 1 : 0);
				}
			};
			Arrays.sort(sortedLngs, floatComparator);
			_lngs = new double[nLngs];
			for (int k = 0; k < nLngs; ++k) {
				_lngs[k] = sortedLngs[k];
			}
			final String uName = findValidVariableName(_uNames);
			final String vName = findValidVariableName(_vNames);
			final String uUncName = findValidVariableName(_uUncNames);
			final String vUncName = findValidVariableName(_vUncNames);
			final String altUUncName = findValidVariableName(_altUUncNames);
			final String altVUncName = findValidVariableName(_altVUncNames);
			final List<NetCdfDataPoint> dataPoints = _pointCollection.getDataPoints();
			if (uUncName != null) {
				final float missingValue = readCartesianValues(simCase, uUncName, DataComponent.DU);
				for (final NetCdfDataPoint dataPoint : dataPoints) {
					final DataForOnePointAndTime dataForOnePointAndTime = dataPoint.getDataForOnePointAndTime(0);
					if (dataForOnePointAndTime.getValue(DataComponent.DU) == missingValue) {
						dataPoint.setValues(DataComponent.DU, /* values= */null);
					}
				}
			}
			if (vUncName != null) {
				final float missingValue = readCartesianValues(simCase, vUncName, DataComponent.DV);
				for (final NetCdfDataPoint dataPoint : dataPoints) {
					final DataForOnePointAndTime dataForOnePointAndTime = dataPoint.getDataForOnePointAndTime(0);
					final float dv = dataForOnePointAndTime.getValue(DataComponent.DV);
					if (Math.abs(dv) == missingValue) {
						dataPoint.setValues(DataComponent.DV, /* values= */null);
					}
				}
			}
			if (altUUncName != null) {
				final float missingValue = readCartesianValues(simCase, altUUncName, DataComponent.ALT_DU);
				for (final NetCdfDataPoint dataPoint : dataPoints) {
					final DataForOnePointAndTime dataForOnePointAndTime = dataPoint.getDataForOnePointAndTime(0);
					final float altDu = dataForOnePointAndTime.getValue(DataComponent.ALT_DU);
					if (Math.abs(altDu) == missingValue) {
						dataPoint.setValues(DataComponent.ALT_DU, /* values= */null);
					}
				}
			}
			if (altVUncName != null) {
				final float missingValue = readCartesianValues(simCase, altVUncName, DataComponent.ALT_DV);
				for (final NetCdfDataPoint dataPoint : dataPoints) {
					final DataForOnePointAndTime dataForOnePointAndTime = dataPoint.getDataForOnePointAndTime(0);
					final float altDv = dataForOnePointAndTime.getValue(DataComponent.ALT_DV);
					if (Math.abs(altDv) == missingValue) {
						dataPoint.setValues(DataComponent.ALT_DV, /* values= */null);
					}
				}
			}
			if (uName != null && vName != null) {
				final float missingUValue = readCartesianValues(simCase, uName, DataComponent.U);
				final float missingVValue = readCartesianValues(simCase, vName, DataComponent.V);
				final ArrayList<NetCdfDataPoint> copy = new ArrayList<>(dataPoints);
				for (final NetCdfDataPoint dataPoint : copy) {
					final DataForOnePointAndTime firstDataForPointAndTime = dataPoint.getDataForOnePointAndTime(0);
					final float firstU = firstDataForPointAndTime.getValue(DataComponent.U);
					final float firstV = firstDataForPointAndTime.getValue(DataComponent.V);
					if (firstU == missingUValue || firstV == missingVValue) {
						dataPoints.remove(dataPoint);
					}
				}
				return;
			}
			final String directionName = findValidVariableName(_directionNames);
			final String speedName = findValidVariableName(_speedNames);
			if (speedName != null && directionName != null) {
				/**
				 * readPolarValues converts to u and v. readPolarValues returns the missing
				 * speed value.
				 */
				final float missingSpeedValue = readPolarValues(speedName, directionName);
				final ArrayList<NetCdfDataPoint> copy = new ArrayList<>(dataPoints);
				for (final NetCdfDataPoint dataPoint : copy) {
					final DataForOnePointAndTime firstDataForPointAndTime = dataPoint.getDataForOnePointAndTime(0);
					final float firstU = firstDataForPointAndTime.getValue(DataComponent.U);
					final float firstV = firstDataForPointAndTime.getValue(DataComponent.V);
					if (firstU == missingSpeedValue || firstV == missingSpeedValue) {
						dataPoints.remove(dataPoint);
					}
				}
			} else {
				throw new NetCdfUvGetterException(NetCdfUvGetter.this, "Unable to find variables for speed");
			}
		} catch (final IOException e) {
			throw new NetCdfUvGetterException(NetCdfUvGetter.this, "Error while reading variables", e);
		}
	}

	public long getHalfLifeSecs() {
		return _halfLifeInSecs;
	}

	public long getPreDistressHalfLifeSecs() {
		return _preDistressHalfLifeInSecs;
	}

	private void setValue(final DataComponent dataComponent, final int timeIdx, final int pointIndex,
			final float value) {
		final List<NetCdfDataPoint> dataPoints = _pointCollection.getDataPoints();
		final NetCdfDataPoint dataPoint = dataPoints.get(pointIndex);
		dataPoint.setValue(dataComponent, timeIdx, value);
	}

	private String findValidVariableName(final String[] names) {
		for (final String name : names) {
			if (_netCdfFile.findVariable(name) != null) {
				return name;
			}
		}
		return null;
	}

	private double getConversionToKnotsFactor(final Variable variable) throws NetCdfUvGetterException {
		final Attribute unitAttribute = variable.findAttribute("units");
		if (unitAttribute == null) {
			throw new NetCdfUvGetterException(NetCdfUvGetter.this, "Cannot find units for " + variable.getFullName());
		}
		final String unitString = cleanUp(unitAttribute.getStringValue().toLowerCase());
		double conversionToKnotsFactor;
		if ("knots".equals(unitString) || "kts".equals(unitString)) {
			conversionToKnotsFactor = 1;
		} else if ("meters/second".equals(unitString) || "m/s".equals(unitString) || "ms-1".equals(unitString)
				|| "meter/sec".equals(unitString)) {
			conversionToKnotsFactor = Constants._MpsToKts;
		} else if ("cm/s".equals(unitString) || "cm/sec".equals(unitString) || "cms-1".equals(unitString)) {
			conversionToKnotsFactor = Constants._MpsToKts / 100.;
		} else {
			throw new NetCdfUvGetterException(NetCdfUvGetter.this,
					"Cannot undertand units " + unitString + "for " + variable.getFullName());
		}
		return conversionToKnotsFactor;
	}

	private float readCartesianValues(final SimCaseManager.SimCase simCase, final String variableName,
			final DataComponent dataComponent) throws NetCdfUvGetterException {
		final Variable variable = _netCdfFile.findVariable(variableName);
		if (variable == null) {
			throw new NetCdfUvGetterException(NetCdfUvGetter.this, "Cannot find variable " + variableName);
		}
		final Attribute missingValueAttribute = variable.findAttribute("missing_value");
		final float missingValue;
		float missingValueX = Float.NaN;
		if (missingValueAttribute != null) {
			final String missingValueStringValue = missingValueAttribute.getStringValue();
			try {
				missingValueX = Float.valueOf(missingValueStringValue);
			} catch (final Exception e) {
				/** Try as a number. */
				try {
					final Number nn = missingValueAttribute.getNumericValue();
					missingValueX = nn.floatValue();
				} catch (final Exception e1) {
				}
			}
			if (Float.isNaN(missingValueX)) {
				final String errorMsg = String.format("Cannot Find Missing Value String for %s.", variableName);
				SimCaseManager.err(simCase, errorMsg);
			}
			missingValue = Float.isNaN(missingValueX) ? -9999.0f : missingValueX;
		} else {
			missingValue = -9999.0f;
		}
		final double conversionToKnotsFactor = getConversionToKnotsFactor(variable);
		final List<NetCdfDataPoint> dataPoints = _pointCollection.getDataPoints();
		try {
			final Array values = variable.read();
			final Index index = values.getIndex();
			final int nPoints = dataPoints.size();
			for (int pointIdx = 0; pointIdx < nPoints; ++pointIdx) {
				float previousValue = missingValue;
				setValue(dataComponent, 0, pointIdx, missingValue);
				for (int timeIndex = 0; timeIndex < _refSecsS.length; ++timeIndex) {
					index.set(timeIndex, pointIdx);
					float value = values.getFloat(index);
					if (value != missingValue) {
						value = (float) (conversionToKnotsFactor * value);
						if (dataComponent == DataComponent.U || dataComponent == DataComponent.V) {
							if (!_dataIsDownStreamOrDownWind) {
								value = -value;
							}
						}
						setValue(dataComponent, timeIndex, pointIdx, value);
						if (previousValue == missingValue) {
							for (int previousTimeIndex = 0; previousTimeIndex < timeIndex; ++previousTimeIndex) {
								setValue(dataComponent, previousTimeIndex, pointIdx, value);
							}
						}
						previousValue = value;
					} else {
						setValue(dataComponent, timeIndex, pointIdx, previousValue);
					}
				}
			}
			return missingValue;
		} catch (final IOException e) {
			throw new NetCdfUvGetterException(NetCdfUvGetter.this, "Error while reading variable " + variableName, e);
		}
	}

	private float readPolarValues(final String speedVariableName, final String directionVariableName)
			throws NetCdfUvGetterException {
		final Variable speedVariable = _netCdfFile.findVariable(speedVariableName);
		if (speedVariableName == null) {
			throw new NetCdfUvGetterException(NetCdfUvGetter.this, "Cannot find variable " + speedVariableName);
		}
		final double conversionToKnotsFactor = getConversionToKnotsFactor(speedVariable);
		final Variable directionVariable = _netCdfFile.findVariable(directionVariableName);
		try {
			final Attribute missingSpeedValueAttribute = speedVariable.findAttribute("missing_value");
			float missingSpeedValue = -9999.0f;
			if (missingSpeedValueAttribute != null) {
				missingSpeedValue = Float.valueOf(missingSpeedValueAttribute.getStringValue());
			}
			final Attribute missingDirectionValueAttribute = directionVariable.findAttribute("missing_value");
			float missingDirectionValue = -9999.0f;
			if (missingDirectionValueAttribute != null) {
				missingDirectionValue = Float.valueOf(missingDirectionValueAttribute.getStringValue());
			}
			final Array speedValues = speedVariable.read();
			final Array directionValues = directionVariable.read();
			final Index speedIndex = speedValues.getIndex();
			final Index directionIndex = directionValues.getIndex();
			final int numberOfPoints = _pointCollection.getDataPoints().size();
			for (int pointIndex = 0; pointIndex < numberOfPoints; ++pointIndex) {
				setValue(DataComponent.U, 0, pointIndex, missingSpeedValue);
				setValue(DataComponent.V, 0, pointIndex, missingSpeedValue);
				float previousU = missingSpeedValue;
				float previousV = missingSpeedValue;
				for (int timeIdx = 0; timeIdx < _refSecsS.length; ++timeIdx) {
					speedIndex.set(timeIdx, pointIndex);
					directionIndex.set(timeIdx, pointIndex);
					double speed = speedValues.getFloat(speedIndex);
					double direction = directionValues.getFloat(directionIndex);
					if (speed != missingSpeedValue && direction != missingDirectionValue) {
						speed = conversionToKnotsFactor * speed;
						direction = Math.toRadians(90 - direction);
						float u = (float) (speed * MathX.cosX(direction));
						float v = (float) (speed * MathX.sinX(direction));
						if (!_dataIsDownStreamOrDownWind) {
							u = -u;
							v = -v;
						}
						setValue(DataComponent.U, timeIdx, pointIndex, u);
						setValue(DataComponent.V, timeIdx, pointIndex, v);
						if (previousU == missingSpeedValue) {
							for (int previousTimeIndex = 0; previousTimeIndex < timeIdx; ++previousTimeIndex) {
								setValue(DataComponent.U, previousTimeIndex, pointIndex, u);
								setValue(DataComponent.V, previousTimeIndex, pointIndex, v);
							}
						}
						previousU = u;
						previousV = v;
					} else if (previousU != missingSpeedValue) {
						setValue(DataComponent.U, timeIdx, pointIndex, previousU);
						setValue(DataComponent.V, timeIdx, pointIndex, previousV);
					}
				}
			}
			return missingSpeedValue;
		} catch (final IOException e) {
			throw new NetCdfUvGetterException(NetCdfUvGetter.this,
					"Error while reading variables " + speedVariableName + ", " + directionVariableName, e);
		}
	}

	/**
	 * Computes the result for times on either side of time, and then interpolates
	 * the results. Most of the work is done finding the closest points.
	 */
	public DataForOnePointAndTime getDataForOnePointAndTime(final SimCaseManager.SimCase simCase, long refSecs,
			final LatLng3 latLng, String interpolationMode) {
		if (_pointCollection.isEmpty()) {
			return null;
		}
		if (refSecs < _refSecsS[0]) {
			if (_warnOnReadBeforeInitialTime[0]) {
				synchronized (_warnOnReadBeforeInitialTime) {
					if (_warnOnReadBeforeInitialTime[0]) {
						_warnOnReadBeforeInitialTime[0] = false;
						final String errorString = String.format(
								"\nSimulation runs before all environmental " + "data. %s is before %s",
								TimeUtilities.formatTime(refSecs, true), TimeUtilities.formatTime(_refSecsS[0], true));
						SimCaseManager.out(_simCase, errorString);
					}
				}
			}
			refSecs = _refSecsS[0];
		} else if (refSecs > _refSecsS[_refSecsS.length - 1]) {
			if (_warnOnReadAfterLastTime[0]) {
				synchronized (_warnOnReadAfterLastTime) {
					if (_warnOnReadAfterLastTime[0]) {
						_warnOnReadAfterLastTime[0] = false;
						final String warningString = String.format(
								"\nSimulation runs after all " + "environmental data.  %s is after %s.",
								TimeUtilities.formatTime(refSecs, true),
								TimeUtilities.formatTime(_refSecsS[_refSecsS.length - 1], true));
						SimCaseManager.out(_simCase, warningString);
					}
				}
			}
			refSecs = _refSecsS[_refSecsS.length - 1];
		}
		if (_pointCollection.getDataPoints().size() == 0) {
			return new DataForOnePointAndTime(0f, 0f, (float) _defaultDU, (float) _defaultDV, (float) _defaultAltDU,
					(float) _defaultAltDV);
		}
		UvCalculator uvCalculator = null;
		if (interpolationMode.compareTo(Model._CenterDominated) == 0
				|| interpolationMode.compareTo(Model._UseAllStrips) == 0) {
			if (_pointCollection.canDoRiverineInterpolation()) {
				uvCalculator = _pointCollection.getRiverSeqLcrUvCalculator(simCase, latLng, interpolationMode);
			} else {
				interpolationMode = Model._2Closest;
			}
		}
		if (interpolationMode.compareTo(Model._2Closest) == 0 || interpolationMode.compareTo(Model._3Closest) == 0) {
			final int nToInterpolateWith = interpolationMode.compareTo(Model._2Closest) == 0 ? 2 : 3;
			uvCalculator = _pointCollection.getStandardUvCalculator(latLng, nToInterpolateWith);
		}
		final int timeIdx = Arrays.binarySearch(_refSecsS, refSecs);
		if (timeIdx >= 0) {
			return uvCalculator.getDataForOnePointAndTime(timeIdx);
		}
		final int timeIdx0 = -timeIdx - 2;
		final long time0 = _refSecsS[timeIdx0];
		final int timeIdx1 = timeIdx0 + 1;
		final long time1 = _refSecsS[timeIdx1];
		final DataForOnePointAndTime dataPoint0 = uvCalculator.getDataForOnePointAndTime(timeIdx0);
		final DataForOnePointAndTime dataPoint1 = uvCalculator.getDataForOnePointAndTime(timeIdx1);
		final float u0 = dataPoint0.getValue(DataComponent.U);
		final float u1 = dataPoint1.getValue(DataComponent.U);
		final float u = interpolate(u0, u1, refSecs, time0, time1);
		final float v0 = dataPoint0.getValue(DataComponent.V);
		final float v1 = dataPoint1.getValue(DataComponent.V);
		final float v = interpolate(v0, v1, refSecs, time0, time1);
		final float du0 = dataPoint0.getValue(DataComponent.DU);
		final float du1 = dataPoint1.getValue(DataComponent.DU);
		final float dU = interpolate(du0, du1, refSecs, time0, time1);
		final float dv0 = dataPoint0.getValue(DataComponent.DV);
		final float dv1 = dataPoint1.getValue(DataComponent.DV);
		final float dV = interpolate(dv0, dv1, refSecs, time0, time1);
		final float altDu0 = dataPoint0.getValue(DataComponent.ALT_DU);
		final float altDu1 = dataPoint1.getValue(DataComponent.ALT_DU);
		final float altDU = interpolate(altDu0, altDu1, refSecs, time0, time1);
		final float altDv0 = dataPoint0.getValue(DataComponent.ALT_DV);
		final float altDv1 = dataPoint1.getValue(DataComponent.ALT_DV);
		final float altDV = interpolate(altDv0, altDv1, refSecs, time0, time1);
		return new DataForOnePointAndTime(u, v, dU, dV, altDU, altDV);
	}

	/**
	 * return[0] is the number of seconds per unit in the time values. return[1] is
	 * the base time in seconds.
	 * <p>
	 * Format is "minutes since 2000-01-01 00:00." This splits into: [minutes,
	 * since, 2000, 01, 01, 00, 00]. TimeZone is assumed to be UTC.
	 */
	private static long[] getTimeInterpreter(final String timeUnitsString) {
		final String[] subFields = timeUnitsString.split("[-:\\s]+");
		final long scale;
		final String unitString = subFields[0].toLowerCase();
		if (unitString.startsWith("hour")) {
			scale = 3600;
		} else if (unitString.startsWith("minute")) {
			scale = 60;
		} else {
			scale = 1;
		}
		int year = 2000;
		int monthOrdinal = 1;
		int dayOfMonth = 1;
		int hourOfDay = 0;
		int minute = 0;
		String timeZoneId = "UTC";
		try {
			year = Integer.parseInt(subFields[2]);
			monthOrdinal = Integer.parseInt(subFields[3]);
			dayOfMonth = Integer.parseInt(subFields[4]);
			hourOfDay = Integer.parseInt(subFields[5]);
			minute = Integer.parseInt(subFields[6]);
			if (subFields.length > 7) {
				timeZoneId = subFields[7];
			}
		} catch (final NumberFormatException e) {
			year = 2000;
			monthOrdinal = 1;
			dayOfMonth = 1;
			hourOfDay = 0;
			minute = 0;
			timeZoneId = "UTC";
		}
		final TimeZone timeZone = TimeZone.getTimeZone(timeZoneId);
		final Calendar calendar = Calendar.getInstance(timeZone);
		calendar.set(Calendar.MILLISECOND, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MINUTE, minute);
		calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
		calendar.set(Calendar.MONTH, TimeUtilities._OrdinalToMonth[monthOrdinal]);
		calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
		calendar.set(Calendar.YEAR, year);
		final long offset = calendar.getTimeInMillis() / 1000L;
		return new long[] {
				scale, offset
		};
	}

	private long[] setTimeDimension() throws NetCdfUvGetterException {
		final Dimension timeDimension = _netCdfFile.findDimension("time");
		if (timeDimension == null) {
			throw new NetCdfUvGetterException(this, "No time dimension");
		}
		final Variable timeVariable = _netCdfFile.findVariable("time");
		final String timeUnitsString = timeVariable.findAttribute("units").getStringValue();
		final long[] scaleAndBaseTime = getTimeInterpreter(timeUnitsString);
		final int timeScaleX = (int) scaleAndBaseTime[0];
		final long offsetX = scaleAndBaseTime[1];
		long[] refSecS = null;
		boolean ascending = true;
		try {
			final Array timeValues = timeVariable.read();
			final int nTimes = timeDimension.getLength();
			refSecS = new long[nTimes];
			final IndexIterator timeIndexIterator = timeValues.getIndexIterator();
			for (int timeIndex = 0; timeIndexIterator.hasNext(); ++timeIndex) {
				final int delta = timeIndexIterator.getIntNext();
				final long unixSecs = offsetX + delta * timeScaleX;
				final long refSecs = TimeUtilities.convertToRefSecs(unixSecs);
				if (timeIndex > 0 && refSecs <= refSecS[timeIndex - 1]) {
					ascending = false;
					break;
				}
				refSecS[timeIndex] = refSecs;
			}
		} catch (final IOException exception) {
			throw new NetCdfUvGetterException(this, "Unable to read time variable", exception);
		}
		if (!ascending) {
			final Throwable throwable = null;
			throw new NetCdfUvGetterException(this, "Times are not ascending!", throwable);
		}
		return refSecS;
	}

	final private static float interpolate(final float y0, final float y1, final float x, final float x0,
			final float x1) {
		if (x == x0) {
			return y0;
		} else if (x == x1) {
			return y1;
		} else if (x0 == x1) {
			return y0 == y1 ? y0 : Float.NaN;
		} else {
			final float y = (y1 - y0) / (x1 - x0) * (x - x0) + y0;
			return y;
		}
	}

	private static String cleanUp(final String name) {
		final StringBuffer result = new StringBuffer();
		final int length = name.length();
		for (int index = 0; index < length; ++index) {
			final char letter = name.charAt(index);
			if (letter != ' ') {
				result.append(letter);
			}
		}
		return result.toString();
	}

	@Override
	public SummaryRefSecs getSummaryForRefSecs(final CircleOfInterest coi, long refSecs, final int iView,
			final boolean interpolateInTime) {
		final int nT = _refSecsS == null ? 0 : _refSecsS.length;
		if (nT == 0) {
			return null;
		}
		/** As of December 4, 2012, this is just used for the display. */
		final double unixSecs = TimeUtilities.getUnixTimeInMillis(refSecs * 1000) / 1000;
		final long[] unixSecsS = TimeUtilities.convertToUnixSecs(_refSecsS);
		final double[] unixSecsS_R = new double[nT];
		for (int k = 0; k < nT; ++k) {
			unixSecsS_R[k] = unixSecsS[k];
		}
		final String explanatoryDetails = NumericalRoutines.getInterpolationDetailsR(unixSecs, unixSecsS_R,
				/* interpolateInTime= */false);
		refSecs = Math.max(refSecs, _refSecsS[0]);
		refSecs = Math.min(refSecs, _refSecsS[nT - 1]);
		return new SummaryRefSecs(coi, refSecs, _refSecsS, _pointCollection, /* interpolateInTime= */false,
				"Std NetCdf-" + explanatoryDetails);
	}

	public long[] getRefSecsS() {
		return _refSecsS;
	}

	public long[] getRefSecsWindow() {
		final int nRefSecsS = _refSecsS == null ? 0 : _refSecsS.length;
		if (nRefSecsS == 0) {
			return new long[] {
					Integer.MIN_VALUE, Integer.MIN_VALUE
			};
		}
		return new long[] {
				_refSecsS[0], _refSecsS[nRefSecsS - 1]
		};
	}

	public BoxDefinition getInnerEdge() {
		/** Convenient names. */
		final int nRefSecsS = _refSecsS.length;
		final long lowRefSecs;
		final long highRefSecs;
		if (nRefSecsS < 2) {
			lowRefSecs = highRefSecs = Long.MIN_VALUE;
		} else {
			final long rawT0 = _refSecsS[0];
			final long rawTLast = _refSecsS[nRefSecsS - 1];
			final double t0 = Math.min(rawT0, rawTLast);
			final double tLast = Math.max(rawT0, rawTLast);
			final double timeGap = (tLast - t0) / (nRefSecsS - 1);
			lowRefSecs = Math.round(Math.ceil(t0 + timeGap));
			highRefSecs = Math.round(Math.floor(tLast - timeGap));
		}
		/** Do the lats. */
		final int nLats = _lats.length;
		final double n, s;
		if (nLats < 2) {
			n = s = -90.0;
		} else {
			final double latGap = (_lats[nLats - 1] - _lats[0]) / (nLats - 1);
			s = _lats[0] + latGap;
			n = _lats[nLats - 1] - latGap;
		}
		/** Do the lngs. */
		final int nLngs = _lngs.length;
		final double w, e;
		if (nLngs < 2) {
			w = e = -180.0;
		} else {
			final double leftLng = _lngs[0];
			final double range = LatLng3.degsToEast0_360L(leftLng, _lngs[nLngs - 1]);
			final double lngGap = range / (nLngs - 1);
			w = leftLng + lngGap;
			e = LatLng3.degsToEast180_180(0, leftLng + range - lngGap);
		}
		final Extent extent = new Extent(w, s, e, n);
		return new BoxDefinition(_simCase, _model, _tag, lowRefSecs, highRefSecs, extent);
	}

	public PointCollection getPointCollection() {
		return _pointCollection;
	}

	protected void closePointCollection(final SimCaseManager.SimCase simCase, final String interpolationMode) {
		_pointCollection.close(simCase, interpolationMode);
	}

	public class Summary {
		final public int _nTimes;
		final public double _timeGap;
		final public long[] _timeWindow1;
		final public long[] _timeWindow2;
		final public int _nLats;
		final public double _latGap;
		final public double _low1, _high1;
		final public double _low2, _high2;
		final public int _nLngs;
		final public double _lngGap;
		final public double _left1, _right1;
		final public double _left2, _right2;

		public Summary() {
			final long[] times = _refSecsS;
			_nTimes = times.length;
			if (_nTimes == 0) {
				_timeGap = Long.MIN_VALUE;
				_timeWindow1 = _timeWindow2 = null;
			} else {
				_timeWindow1 = new long[] {
						times[0], times[_nTimes - 1]
				};
				if (_nTimes == 1) {
					_timeWindow2 = null;
					_timeGap = Long.MIN_VALUE;
				} else {
					_timeWindow2 = new long[] {
							times[1], times[_nTimes - 2]
					};
					_timeGap = ((double) Math.abs(times[_nTimes - 1] - times[0])) / (_nTimes - 1);
				}
			}
			_nLats = _lats.length;
			if (_nLats == 0) {
				_low1 = _high1 = _low2 = _high2 = Double.NaN;
				_latGap = Double.NaN;
			} else {
				_low1 = _lats[0];
				_high1 = _lats[_nLats - 1];
				if (_nLats == 1) {
					_low2 = _high2 = Double.NaN;
					_latGap = Double.NaN;
				} else {
					_low2 = _lats[1];
					_high2 = _lats[_nLats - 2];
					_latGap = (_high2 - _low2) / (_nLats - 1);
				}
			}
			_nLngs = _lngs.length;
			if (_nLngs == 0) {
				_left1 = _right1 = _left2 = _right2 = Double.NaN;
				_lngGap = Double.NaN;
			} else {
				_left1 = _lngs[0];
				_right1 = _lngs[_nLngs - 1];
				if (_nLngs == 1) {
					_left2 = _right2 = Double.NaN;
					_lngGap = Double.NaN;
				} else {
					_left2 = _lngs[1];
					_right2 = _lngs[_nLngs - 2];
					_lngGap = LatLng3.degsToEast0_360L(_lngs[0], _lngs[_nLngs - 1]) / (_nLngs - 1);
				}
			}
		}
	}

	/**
	 * Compute the additional buffers necessary so that we pull from the second one
	 * in.
	 */
	public double[] getRequiredBuffers(final long lowRefSecs, final long highRefSecs, final Extent extent) {
		/** Do times. */
		final long lowTimeBuffer;
		final long highTimeBuffer;
		/** Convenient names. */
		final long[] times = _refSecsS;
		final int nTimes = times.length;
		if (nTimes < 2) {
			/** No guidance; take a swag. */
			lowTimeBuffer = highTimeBuffer = 3600;
		} else {
			final long rawT0 = times[0];
			final long rawTLast = times[nTimes - 1];
			final double t0 = Math.min(rawT0, rawTLast);
			final double tLast = Math.max(rawT0, rawTLast);
			final double timeGap = (tLast - t0) / (nTimes - 1);
			/** Which gap is lowRefSecs in? */
			final double gapNumber0 = (lowRefSecs - t0) / timeGap;
			if (gapNumber0 >= 1) {
				lowTimeBuffer = 0;
			} else if (gapNumber0 >= 0) {
				lowTimeBuffer = Math.round(Math.ceil(timeGap));
			} else {
				lowTimeBuffer = Math.round(Math.ceil(-gapNumber0 + 1.0) * timeGap);
			}
			/** How many gaps from the end is highRefSecs in? */
			final double gapNumber1 = (tLast - highRefSecs) / timeGap;
			if (gapNumber1 >= 1) {
				highTimeBuffer = 0;
			} else if (gapNumber1 >= 0) {
				highTimeBuffer = Math.round(Math.ceil(timeGap));
			} else {
				highTimeBuffer = Math.round(Math.ceil(-gapNumber1 + 1.0) * timeGap);
			}
		}
		/** Do the lats. */
		final double lowLatBuffer;
		final double highLatBuffer;
		final int nLats = _lats.length;
		if (nLats < 2) {
			/** No guidance; take a swag. */
			lowLatBuffer = highLatBuffer = 0.5;
		} else {
			final double latGap = (_lats[nLats - 1] - _lats[0]) / (nLats - 1);
			final double gapNumber0 = (extent.getMinLat() - _lats[0]) / latGap;
			if (gapNumber0 >= 1) {
				lowLatBuffer = 0;
			} else if (gapNumber0 >= 0) {
				lowLatBuffer = latGap;
			} else {
				lowLatBuffer = Math.ceil(-gapNumber0 + 1.0) * latGap;
			}
			final double gapNumber1 = (_lats[nLats - 1] - extent.getMaxLat()) / latGap;
			if (gapNumber1 >= 1) {
				highLatBuffer = 0;
			} else if (gapNumber1 >= 0) {
				highLatBuffer = latGap;
			} else {
				highLatBuffer = Math.ceil(-gapNumber1 + 1.0) * latGap;
			}
		}
		/** Do the lngs. */
		/** Compute left, low, right, and high buffers to the 2nd point in. */
		final double leftBuffer;
		final double rightBuffer;
		final int nLngs = _lngs.length;
		if (nLngs < 2) {
			/** No guidance; take a swag. */
			leftBuffer = rightBuffer = 0.5;
		} else {
			final double leftLng = _lngs[0];
			final double range = LatLng3.degsToEast0_360L(leftLng, _lngs[nLngs - 1]);
			final double lngGap = range / (nLngs - 1);
			final double gapNumber0 = LatLng3.degsToEast180_180(leftLng, extent.getLeftLng()) / lngGap;
			if (gapNumber0 >= 1) {
				leftBuffer = 0;
			} else if (gapNumber0 >= 0) {
				leftBuffer = lngGap;
			} else {
				leftBuffer = Math.ceil(-gapNumber0 + 1.0) * lngGap;
			}
			final double rightRange = LatLng3.degsToEast180_180(leftLng, extent.getRightLng());
			final double gapNumber1 = (range - rightRange) / lngGap;
			if (gapNumber1 >= 1) {
				rightBuffer = 0;
			} else if (gapNumber1 >= 0) {
				rightBuffer = lngGap;
			} else {
				rightBuffer = Math.ceil(-gapNumber1 + 1.0) * lngGap;
			}
		}
		return new double[] {
				lowTimeBuffer, highTimeBuffer, leftBuffer, lowLatBuffer, rightBuffer, highLatBuffer
		};
	}

	public boolean isEmpty(final MyLogger logger) {
		return _pointCollection == null || _pointCollection.isEmpty();
	}

	public void freePointCollectionMemory() {
		_pointCollection.freeMemory();
	}

}
