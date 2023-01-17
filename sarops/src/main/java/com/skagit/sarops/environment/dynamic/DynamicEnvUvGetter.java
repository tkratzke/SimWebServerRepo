package com.skagit.sarops.environment.dynamic;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.w3c.dom.DOMException;
import org.w3c.dom.Element;

import com.skagit.sarops.environment.BoxDefinition;
import com.skagit.sarops.environment.DataForOnePointAndTime;
import com.skagit.sarops.environment.NetCdfDataPoint;
import com.skagit.sarops.environment.NetCdfUvGetter;
import com.skagit.sarops.environment.PointCollection;
import com.skagit.sarops.environment.SummaryRefSecs;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.simCaseManager.MainRunner;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.simCaseManager.SimCaseManager.SimCase;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.StringUtilities;
import com.skagit.util.TimeUtilities;
import com.skagit.util.gshhs.CircleOfInterest;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;

import ucar.nc2.NetcdfFile;

/**
 * Maintains the sets of BoxDefinitions, including finding a BoxDefinition
 * big enough for a particular time,LatLng.
 */
abstract public class DynamicEnvUvGetter {
	final static Comparator<BoxDefinition> _IgnoreLastAccessTime =
			new Comparator<>() {
				@Override
				public int compare(final BoxDefinition o0, final BoxDefinition o1) {
					final long lowRefSecs0 = o0._lowRefSecs;
					final long lowRefSecs1 = o1._lowRefSecs;
					if (lowRefSecs0 != lowRefSecs1) {
						return lowRefSecs0 < lowRefSecs1 ? -1 : 1;
					}
					final long highRefSecs0 = o0._highRefSecs;
					final long highRefSecs1 = o1._highRefSecs;
					if (highRefSecs0 != highRefSecs1) {
						return highRefSecs0 < highRefSecs1 ? -1 : 1;
					}
					return Extent._SouthToNorth.compare(o0._extent, o1._extent);
				}
			};
	final static Comparator<BoxDefinition> _ConsiderLastAccessTime =
			new Comparator<>() {
				@Override
				public int compare(final BoxDefinition o1, final BoxDefinition o2) {
					/**
					 * To make sure the FIRST one is not useful, we sort by increasing
					 * timeOfLastAccess and then the old way.
					 */
					if (o1._lastAccessTimeInMillis < o2._lastAccessTimeInMillis) {
						return -1;
					} else if (o1._lastAccessTimeInMillis > o2._lastAccessTimeInMillis) {
						return 1;
					}
					return _IgnoreLastAccessTime.compare(o1, o2);
				}
			};
	/**
	 * For combining boxes. If the "Big Rectangle" divided by the sum of the
	 * two "Small Rectangles" is smaller than CriticalRatio, do not combine.
	 */
	final private static double _CriticalRatio = 1.1;
	/** Standard backpointers. */
	protected final SimCaseManager.SimCase _simCase;
	protected final Model _model;
	protected final String _tag;
	/** parameters for building BoxDefinitions. */
	final private String _interpolationMode;
	/** Separate fields in the construction of a URI: */
	final private String _scheme;
	final private String _userInfo;
	final private String _host;
	final private int _port;
	final private String _path;
	final private String _clientKey;
	final private String _sourceID;
	final private String _sourceName;
	final private int _outputType;
	final private int _timeOut;
	final private boolean _zipped;
	/** The part of the query that does not change: */
	final private String _fixedPartOfQuery;
	/** Parameters for the UvGetter itself. */
	protected final long _halfLifeSecs;
	protected final long _preDistressHalfLifeSecs;
	protected final float _dU;
	protected final float _dV;
	protected final float _altDU;
	protected final float _altDV;
	final private boolean _isValid;
	/** The BoxDefinitions. */
	final private int _maxUvGettersToKeep;
	protected final TreeSet<BoxDefinition> _allBoxDefinitions;
	protected final TreeSet<BoxDefinition> _pendingBoxDefinitions;
	protected final TreeSet<BoxDefinition> _requiredBoxDefinitions;
	protected final TreeSet<BoxDefinition> _activeBoxDefinitionsByLastAccessTime;
	protected final TreeMap<BoxDefinition, NetCdfUvGetter> _uvGetters;

	protected DynamicEnvUvGetter(final SimCase simCase, final Model model,
			final String tag, final int maxUvGettersToKeep,
			final String interpolationMode, final long halfLifeSecs,
			final long preDistressHalfLifeSecs, final double dU, final double dV,
			final String scheme, final String userInfo, final String host,
			final int port, final String path, final String clientKey,
			final String sourceID, final String sourceName, final int outputType,
			final int timeOut, final boolean zipped) {
		super();
		_simCase = simCase;
		_model = model;
		_tag = tag;
		_maxUvGettersToKeep = maxUvGettersToKeep;
		_interpolationMode = interpolationMode;
		_halfLifeSecs = halfLifeSecs;
		_preDistressHalfLifeSecs = preDistressHalfLifeSecs;
		_altDU = _dU = (float) dU;
		_altDV = _dV = (float) dV;
		_scheme = scheme;
		_userInfo = userInfo;
		_host = host;
		_port = port;
		_path = path;
		_clientKey = clientKey;
		_sourceID = sourceID;
		_sourceName = sourceName;
		_outputType = outputType;
		_timeOut = timeOut;
		_zipped = zipped;
		_fixedPartOfQuery = String.format(
				"clientKey=%s&sourceID=%s&sourceName=%s" +
						"&outputType=%d&timeout=%03d", //
				clientKey, sourceID, sourceName, outputType, timeOut);
		_allBoxDefinitions = new TreeSet<>(_IgnoreLastAccessTime);
		_pendingBoxDefinitions = new TreeSet<>(_IgnoreLastAccessTime);
		_requiredBoxDefinitions = new TreeSet<>(_IgnoreLastAccessTime);
		_activeBoxDefinitionsByLastAccessTime =
				new TreeSet<>(_ConsiderLastAccessTime);
		_pendingBoxDefinitions.clear();
		_requiredBoxDefinitions.clear();
		_uvGetters = new TreeMap<>(_IgnoreLastAccessTime);
		/** Perhaps run a connection test. */
		_isValid = true;
	}

	public URI getUriForLastTry(final BoxDefinition boxDefinition,
			final boolean zipped, final boolean debug, final boolean giveDetails)
			throws URISyntaxException {
		/**
		 * <pre>
		 * fixedPartOfQuery has: clientKey, sourceID, sourceName, outputType,
		 * and timeout all built into it. scheme, userInfo, host, port, and path
		 * are different sections of the URI.
		 */
		final String fixedPartOfQuery = getFixedPartOfQuery();
		final String scheme = getScheme();
		final String userInfo = getUserInfo();
		final String host = getHost();
		final int port = getPort();
		final String path = getPath();
		final String variablePartOfQuery = createVariablePartOfQueryForLastTry(
				boxDefinition, zipped, debug, giveDetails);
		final String fragment = null;
		final String query = fixedPartOfQuery + variablePartOfQuery;
		return new URI(scheme, userInfo, host, port, path, query, fragment);
	}

	private String createVariablePartOfQueryForLastTry(
			final BoxDefinition boxDefinition, final boolean zipped,
			final boolean debugx, final boolean giveDetails) {
		final BoxDefinition lastTry = boxDefinition._tries.lastElement();
		final long lowRefSecs = lastTry._lowRefSecs;
		final long highRefSecs = lastTry._highRefSecs;
		final int[] timeArray1a = TimeUtilities.getTimeArray(lowRefSecs);
		final int y1a = timeArray1a[0];
		final int m1a = timeArray1a[1];
		final int d1a = timeArray1a[2];
		final int h1a = timeArray1a[3];
		final int mm1a = timeArray1a[4];
		/** "2014-03-05T00:00:00" */
		final String coreDateFormat = "%sDate=%04d-%02d-%02dT%02d:%02d:00";
		final String coreBoxFormat = "x1=%.2f&x2=%.2f&y1=%.2f&y2=%.2f";
		final String startDateString =
				String.format(coreDateFormat, "start", y1a, m1a, d1a, h1a, mm1a);
		final int[] timeArray1b = TimeUtilities.getTimeArray(highRefSecs);
		final int y1b = timeArray1b[0];
		final int m1b = timeArray1b[1];
		final int d1b = timeArray1b[2];
		final int h1b = timeArray1b[3];
		final int mm1b = timeArray1b[4];
		final String endDateString1 = String.format(coreDateFormat, //
				"end", y1b, m1b, d1b, h1b, mm1b);
		final Extent extent1 = lastTry._extent;
		final double w1 = extent1.getLeftLng();
		final double s1 = extent1.getMinLat();
		final double e1 = extent1.getRightLng();
		final double n1 = extent1.getMaxLat();
		final String boxString1 = String.format(coreBoxFormat, w1, e1, s1, n1);
		final String zippedString =
				String.format("zipped=%s", zipped ? "true" : "false");
		final String query = "&" + startDateString + "&" + endDateString1 +
				"&" + boxString1 + "&" + zippedString;

		if (giveDetails) {
			final String startDateString1X = String.format(coreDateFormat, //
					"start", y1a, m1a, d1a, h1a, mm1a);
			final String endDateString1X = String.format(coreDateFormat, //
					"end", y1b, m1b, d1b, h1b, mm1b);
			final String boxString1X =
					String.format(coreBoxFormat, w1, e1, s1, n1);
			final int[] timeArray2a =
					TimeUtilities.getTimeArray(boxDefinition._lowRefSecs);
			final int y2a = timeArray2a[0];
			final int m2a = timeArray2a[1];
			final int d2a = timeArray2a[2];
			final int h2a = timeArray2a[3];
			final int mm2a = timeArray2a[4];
			final int[] timeArray2b = TimeUtilities.getTimeArray(highRefSecs);
			final int y2b = timeArray2b[0];
			final int m2b = timeArray2b[1];
			final int d2b = timeArray2b[2];
			final int h2b = timeArray2b[3];
			final int mm2b = timeArray2b[4];
			final double w2 = boxDefinition._extent.getLeftLng();
			final double s2 = boxDefinition._extent.getMinLat();
			final double e2 = boxDefinition._extent.getRightLng();
			final double n2 = boxDefinition._extent.getMaxLat();
			final String startDateString2 = String.format(coreDateFormat, //
					"start", y2a, m2a, d2a, h2a, mm2a);
			final String endDateString2 = String.format(coreDateFormat, //
					"end", y2b, m2b, d2b, h2b, mm2b);
			final String boxString2 =
					String.format(coreBoxFormat, w2, e2, s2, n2);
			final String details = String.format(
					"\n\n\tStubborn case:\n\t" +
							"%s:%s covers(?) %s:%s\n\t%s covers(?) %s",
					startDateString1X, endDateString1X, startDateString2,
					endDateString2, boxString1X, boxString2);
			SimCaseManager.out(_simCase, details);
		}
		return query;
	}

	protected SimCase getSimCase() {
		return _simCase;
	}

	protected Model getModel() {
		return _model;
	}

	public boolean getIsValid() {
		return _isValid;
	}

	protected String getInterpolationMode() {
		return _interpolationMode;
	}

	protected String getScheme() {
		return _scheme;
	}

	protected String getHost() {
		return _host;
	}

	protected int getPort() {
		return _port;
	}

	protected String getPath() {
		return _path;
	}

	protected String getUserInfo() {
		return _userInfo;
	}

	protected String getFixedPartOfQuery() {
		return _fixedPartOfQuery;
	}

	private void cacheNecessaryBoxDefinition(final long refSecs,
			final LatLng3 latLng, final BoxDefinition inputBoxDefinition,
			final double criticalRatio) {
		final boolean pointIsOfInterest = refSecs >= 0 && latLng != null;
		/** Is there an active one that will do? */
		for (final BoxDefinition boxDefinition : _activeBoxDefinitionsByLastAccessTime) {
			final boolean haveLowTime;
			final boolean haveHighTime;
			final boolean haveLatLng;
			if (pointIsOfInterest) {
				/** We're interested only in a particular point. */
				haveLowTime = haveHighTime = boxDefinition.contains(refSecs);
				haveLatLng = boxDefinition.contains(latLng);
			} else {
				haveLowTime =
						boxDefinition.contains(inputBoxDefinition.getLowRefSecs());
				haveHighTime =
						boxDefinition.contains(inputBoxDefinition.getHighRefSecs());
				haveLatLng = boxDefinition.contains(inputBoxDefinition.getExtent());
			}
			if (haveLowTime && haveHighTime && haveLatLng) {
				_activeBoxDefinitionsByLastAccessTime.remove(boxDefinition);
				boxDefinition.setLastAccessTimeInMillis(System.currentTimeMillis());
				_activeBoxDefinitionsByLastAccessTime.add(boxDefinition);
				_requiredBoxDefinitions.add(boxDefinition);
				return;
			}
		}
		/** Is there a pending one that will work? */
		for (final BoxDefinition boxDefinition : _pendingBoxDefinitions) {
			final boolean haveLowTime;
			final boolean haveHighTime;
			final boolean haveLatLng;
			if (pointIsOfInterest) {
				haveLowTime = haveHighTime = boxDefinition.contains(refSecs);
				haveLatLng = boxDefinition.contains(latLng);
			} else {
				haveLowTime =
						boxDefinition.contains(inputBoxDefinition.getLowRefSecs());
				haveHighTime =
						boxDefinition.contains(inputBoxDefinition.getHighRefSecs());
				haveLatLng = boxDefinition.contains(inputBoxDefinition.getExtent());
			}
			if (haveLowTime && haveHighTime && haveLatLng) {
				return;
			}
		}
		/**
		 * Need a new one. At this point, always appeal to inputBoxDefinition,
		 * and union it into an existing pending one if advantageous.
		 */
		BoxDefinition winner = null;
		double bestRatio = criticalRatio;
		for (final BoxDefinition pendingBoxDefinition : _pendingBoxDefinitions) {
			final double thisRatio =
					pendingBoxDefinition.getRatio(inputBoxDefinition);
			if (thisRatio < bestRatio) {
				winner = pendingBoxDefinition;
				bestRatio = thisRatio;
			}
		}
		if (winner != null) {
			_pendingBoxDefinitions.remove(winner);
			final BoxDefinition union = winner.union(inputBoxDefinition);
			final String winnerString = String.format(
					"Combining:%s with%s to get:%s", winner.getString(false),
					inputBoxDefinition.getString(false), union.getString(false));
			SimCaseManager.out(_simCase, winnerString);
			_pendingBoxDefinitions.add(union);
		} else {
			_pendingBoxDefinitions.add(inputBoxDefinition);
		}
	}

	protected void cacheNecessaryBoxDefinition(final long refSecs,
			final LatLng3 latLng, final BoxDefinition inputBoxDefinition) {
		cacheNecessaryBoxDefinition(refSecs, latLng, inputBoxDefinition,
				_CriticalRatio);
	}

	protected void writeFixedPart(final Element element) {
		if (_userInfo != null) {
			element.setAttribute("userInfo", _userInfo);
		}
		element.setAttribute("host", _userInfo);
		element.setAttribute("path", _path);
		element.setAttribute("sourceID", _sourceID);
		element.setAttribute("sourceName", _sourceName);
		element.setAttribute("scheme", _scheme);
		element.setAttribute("clientKey", _clientKey);
		element.setAttribute("port", "" + _port);
		element.setAttribute("outputType", "" + _outputType);
		element.setAttribute("timeout", "" + _timeOut);
		element.setAttribute("zipped", "" + _zipped);
	}

	/** Utility class for getting a summary for a specific time. */
	protected static class TimeAndUvDuDv {
		protected final long _refSecs;
		protected final DataForOnePointAndTime _dataForOnePointAndTime;

		protected TimeAndUvDuDv(final long refSecs,
				final DataForOnePointAndTime dataForOnePointAndTime) {
			_refSecs = refSecs;
			_dataForOnePointAndTime = dataForOnePointAndTime;
		}
	}

	private NetCdfUvGetter createUvGetter(final BoxDefinition boxDefinition,
			final boolean debug) {
		final long lowTimeRefSecs = boxDefinition.getLowRefSecs();
		final long highTimeRefSecs = boxDefinition.getHighRefSecs();
		final Extent extent = boxDefinition.getExtent();
		double[] oldRequiredBuffers = null;
		NetCdfUvGetter netCdfUvGetter = null;
		boolean success = false;
		Exception e = null;
		OUTSIDE_LOOP: for (int k = 0; !success; ++k) {
			final boolean giveDetails = debug || k >= 3;
			for (int kk = 0; kk < 5 && !success; ++kk) {
				URI uri = null;
				try {
					uri =
							getUriForLastTry(boxDefinition, _zipped, debug, giveDetails);
				} catch (final URISyntaxException e1) {
					e1.printStackTrace();
					e = e1;
					continue;
				}
				try (final NetcdfFile netCdfFile =
						boxDefinition.getNetCdfFile(uri, _zipped)) {
					final String uriString = uri.toASCIIString();
					netCdfUvGetter = buildNetCdfUvGetter(uriString, netCdfFile);
					closeNetCdfUvGetter(netCdfUvGetter);
					/**
					 * How much MORE do we have to push out to cover the original?
					 * Note that netCdfUvGetter was built with the most recent
					 * BoxDefinition.
					 */
					final double[] requiredBuffers = netCdfUvGetter
							.getRequiredBuffers(lowTimeRefSecs, highTimeRefSecs, extent);
					final NetCdfUvGetter.Summary uvGetterSummary =
							netCdfUvGetter.new Summary();
					boxDefinition.addUvGetterSummary(uvGetterSummary);
					boolean allDone = requiredBuffers == null;
					/**
					 * If every buffer is 0 or the same as the old buffer, we're done.
					 */
					if (!allDone) {
						allDone = true;
						for (int k1 = 0; k1 < requiredBuffers.length; ++k1) {
							final double buffer = requiredBuffers[k1];
							if (buffer == 0 || (oldRequiredBuffers != null &&
									buffer == oldRequiredBuffers[k1])) {
								continue;
							}
							allDone = false;
							break;
						}
					}
					if (allDone || k > 3) {
						SimCaseManager.out(_simCase,
								"\n\n" + boxDefinition.getString(true));
						if (k > 3) {
							SimCaseManager.out(_simCase,
									String.format(
											"Problem here though; Note that this is our " +
													"%dth try and we simply gave up.",
											k));
						}
						if (allDone) {
							success = true;
						}
						break OUTSIDE_LOOP;
					}
					/** Get ready for the next one. */
					final BoxDefinition thisTry = boxDefinition.getLastTry();
					final long lowRefSecs2 = thisTry.getLowRefSecs();
					final long highRefSecs2 = thisTry.getHighRefSecs();
					final Extent extent2 = thisTry.getExtent();
					final BoxDefinition nextTry = new BoxDefinition(lowRefSecs2,
							highRefSecs2, extent2, requiredBuffers);
					boxDefinition.addTry(nextTry);
					oldRequiredBuffers = requiredBuffers;
				} catch (IOException | DOMException |
						NetCdfUvGetter.NetCdfUvGetterException e1) {
					e = e1;
					SimCaseManager.standardLogError(_simCase, e1);
				}
			}
		}
		if (!success) {
			/** Do something that crashes this case. */
			final String s = String.format(
					"Failed on Environmental Data Pull.\n" +
							"Message[%s]:  StackTrace:%s",
					e.toString(), StringUtilities.getStackTraceString(e));
			SimCaseManager.err(_simCase, s);
			MainRunner.HandleFatal(_simCase, new RuntimeException(s));
		}
		return netCdfUvGetter;
	}

	/**
	 * Named with an XYZ to avoid confusing it with the Interface requirements
	 * of this guy's derived classes.
	 */

	protected SummaryRefSecs getSummaryForRefSecsSXYZ(
			final CircleOfInterest coi, final long refSecs, final int iView) {
		/** No interpolation in time here. */
		/**
		 * For each LatLng latLng that appears anywhere, find the first
		 * BoxDefinition that contains refSecs and latLng. Use the corresponding
		 * uvGetter to assign its u,v for refSecs.
		 */
		final Map<LatLng3, TimeAndUvDuDv> latLngToTimeAndUvDuDv =
				new HashMap<>();
		final Map<BoxDefinition, NetCdfUvGetter> uvGettersInUse =
				new TreeMap<>(_uvGetters.comparator());
		for (final BoxDefinition boxDefinition : _uvGetters.keySet()) {
			if (boxDefinition.contains(refSecs)) {
				final long thisLowTime = boxDefinition.getLowRefSecs();
				final NetCdfUvGetter netCdfUvGetter = _uvGetters.get(boxDefinition);
				final PointCollection pointCollection =
						netCdfUvGetter.getPointCollection();
				final List<NetCdfDataPoint> netCdfDataPoints =
						pointCollection.getDataPoints();
				for (final NetCdfDataPoint netCdfDataPoint : netCdfDataPoints) {
					final LatLng3 latLng = netCdfDataPoint.getLatLng();
					if (boxDefinition.contains(latLng)) {
						uvGettersInUse.put(boxDefinition, netCdfUvGetter);
						final DataForOnePointAndTime dataForOnePointAndTime =
								netCdfUvGetter.getDataForOnePointAndTime(_simCase, refSecs,
										latLng, getInterpolationMode());
						final TimeAndUvDuDv incumbent =
								latLngToTimeAndUvDuDv.get(latLng);
						if (incumbent == null) {
							final TimeAndUvDuDv timeAndUvDuDv =
									new TimeAndUvDuDv(refSecs, dataForOnePointAndTime);
							latLngToTimeAndUvDuDv.put(latLng, timeAndUvDuDv);
						} else {
							if (thisLowTime < incumbent._refSecs) {
								latLngToTimeAndUvDuDv.put(latLng,
										new TimeAndUvDuDv(thisLowTime, dataForOnePointAndTime));
							}
						}
					}
				}
			}
		}
		final int nLatLngs = latLngToTimeAndUvDuDv.size();
		final LatLng3[] latLngs = new LatLng3[nLatLngs];
		final float[] u = new float[nLatLngs];
		final float[] v = new float[nLatLngs];
		int k = 0;
		for (final Map.Entry<LatLng3, TimeAndUvDuDv> entry : latLngToTimeAndUvDuDv
				.entrySet()) {
			latLngs[k] = entry.getKey();
			final DataForOnePointAndTime dataForOnePointAndTime =
					entry.getValue()._dataForOnePointAndTime;
			u[k] =
					dataForOnePointAndTime.getValue(NetCdfUvGetter.DataComponent.U);
			v[k] =
					dataForOnePointAndTime.getValue(NetCdfUvGetter.DataComponent.V);
			++k;
		}
		final long unixSecs =
				TimeUtilities.getUnixTimeInMillis(refSecs * 1000) / 1000;
		final String explanatoryDetails;
		if (uvGettersInUse.size() > 1) {
			String s =
					String.format("Dynamic, using %d Boxes:", uvGettersInUse.size());
			for (final NetCdfUvGetter netCdfUvGetter : uvGettersInUse.values()) {
				final long[] unixSecsS =
						TimeUtilities.convertToUnixSecs(netCdfUvGetter.getRefSecsS());
				final String s2 = NumericalRoutines.getInterpolationDetailsL(
						unixSecs, unixSecsS, /* interpolateInTime= */false);
				s += String.format("\n\t%s", s2);
			}
			explanatoryDetails = s;
		} else {
			String s = "";
			for (final NetCdfUvGetter netCdfUvGetter : uvGettersInUse.values()) {
				final long[] unixSecS =
						TimeUtilities.convertToUnixSecs(netCdfUvGetter.getRefSecsS());
				s += NumericalRoutines.getInterpolationDetailsL(unixSecs, unixSecS,
						/* interpolateInTime= */false);
			}
			explanatoryDetails = s;
		}
		return new SummaryRefSecs(latLngs, u, v, explanatoryDetails);
	}

	/**
	 * Under normal circumstances, we should not have to build new
	 * BoxDefinitions; that should be done long before getUv is called. Hence,
	 * the buffers sent here should be small.
	 */
	protected DataForOnePointAndTime getDataForOnePointAndTime(
			final long refSecs, final LatLng3 latLng,
			final String interpolationMode) {
		NetCdfUvGetter winner = null;
		for (final BoxDefinition boxDefinition : _uvGetters.keySet()) {
			if (boxDefinition.contains(refSecs, latLng)) {
				winner = _uvGetters.get(boxDefinition);
				break;
			}
		}
		if (winner == null) {
			/**
			 * We get here only if we didn't build sufficient BoxDefinitions, so
			 * we have to now. But since this routine is called in parallel, we
			 * have to synchronize this block of code. We make "emergency
			 * preparations that are small.
			 */
			synchronized (this) {
				final BoxDefinition newBoxDefinition =
						new BoxDefinition(_simCase, _model, refSecs, latLng, 15.0);
				/**
				 * We want this one for sure, and there should be no pending ones
				 * anyway.
				 */
				final double criticalRatio = 1.0;
				cacheNecessaryBoxDefinition(-1L, null, newBoxDefinition,
						criticalRatio);
				finishPrepareXYZ();
				/** Now try again. */
				for (final BoxDefinition boxDefinition : _uvGetters.keySet()) {
					if (boxDefinition.contains(refSecs, latLng)) {
						winner = _uvGetters.get(boxDefinition);
						break;
					}
				}
			}
		}
		/**
		 * Take the last one you see if you can't get a perfect fit. We should
		 * get a perfect fit unless, for example, we're up against a coast and
		 * there's no data on one side of us.
		 */
		if (winner == null) {
			winner = _uvGetters.lastEntry().getValue();
		}
		return winner.getDataForOnePointAndTime(_simCase, refSecs, latLng,
				interpolationMode);
	}

	abstract protected NetCdfUvGetter buildNetCdfUvGetter(String uriString,
			NetcdfFile netCdfFile) throws NetCdfUvGetter.NetCdfUvGetterException;

	protected void finishPrepareXYZ() {
		/** Delete the oldest ones that are not required. */
		final int nToDelete = _pendingBoxDefinitions.size() +
				_uvGetters.size() - _maxUvGettersToKeep;
		final ArrayList<BoxDefinition> toDelete = new ArrayList<>();
		for (final BoxDefinition boxDefinition : _activeBoxDefinitionsByLastAccessTime) {
			if (toDelete.size() >= nToDelete) {
				break;
			}
			if (!_requiredBoxDefinitions.contains(boxDefinition)) {
				toDelete.add(boxDefinition);
			}
		}
		for (final BoxDefinition boxDefinition : toDelete) {
			_uvGetters.remove(boxDefinition);
			_activeBoxDefinitionsByLastAccessTime.remove(boxDefinition);
		}
		/**
		 * It's ok if we don't get as much deleted as we wanted to. But now it's
		 * time to go get the data.
		 */
		for (final BoxDefinition boxDefinition : _pendingBoxDefinitions) {
			final boolean debug = false;
			final NetCdfUvGetter uvGetter = createUvGetter(boxDefinition, debug);
			/**
			 * Get the best that we can do with this NetCdfUvGetter; it's the
			 * union of boxDefinition and the "innerEdge" of the uvGetter.
			 */
			final BoxDefinition newBoxDefinition =
					boxDefinition.union(uvGetter.getInnerEdge());
			_uvGetters.put(newBoxDefinition, uvGetter);
			newBoxDefinition
					.setLastAccessTimeInMillis(System.currentTimeMillis());
			_activeBoxDefinitionsByLastAccessTime.add(newBoxDefinition);
			_allBoxDefinitions.add(newBoxDefinition);
			addBoxDefinitionToModel(newBoxDefinition);
		}
		_pendingBoxDefinitions.clear();
		_requiredBoxDefinitions.clear();
	}

	abstract void addBoxDefinitionToModel(BoxDefinition boxDefinition);

	abstract void closeNetCdfUvGetter(NetCdfUvGetter netCdfUvGetter);

	protected boolean isEmpty(final SimCaseManager.SimCase simCase) {
		for (final NetCdfUvGetter uvGetter : _uvGetters.values()) {
			if (!uvGetter.isEmpty(simCase)) {
				return false;
			}
		}
		return true;
	}

	public void freeDynamicEnvGetterMemory() {
		_allBoxDefinitions.clear();
		_pendingBoxDefinitions.clear();
		_requiredBoxDefinitions.clear();
		_activeBoxDefinitionsByLastAccessTime.clear();
		final int nLittleOnes = _uvGetters == null ? 0 : _uvGetters.size();
		if (nLittleOnes > 0) {
			final NetCdfUvGetter[] littleOnes =
					_uvGetters.values().toArray(new NetCdfUvGetter[nLittleOnes]);
			_uvGetters.clear();
			for (int k = 0; k < nLittleOnes; ++k) {
				littleOnes[k].freePointCollectionMemory();
			}
		}
	}
}
