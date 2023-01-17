package com.skagit.sarops.util.patternUtils;

import com.skagit.sarops.planner.plannerModel.PatternKind;
import com.skagit.sarops.util.CppToJavaTracer;
import com.skagit.sarops.util.patternUtils.SphericalTimedSegs.LoopType;
import com.skagit.util.TimeUtilities;
import com.skagit.util.colorUtils.ColorUtils;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.MotionType;

public class SimLibPattern {
	final public double _rawSearchKts;
	final public long _baseSecs;
	final public boolean _baseEqualsUnix;
	final public int _searchDurationSecs;
	final public boolean _firstTurnRight;
	final public double _minTsNmi;

	/** Output. */
	final boolean _isPsCs, _isSs, _isVs;
	final public SphericalTimedSegs _sphericalTimedSegs;
	final public double _centerLat, _centerLng;
	final public long[] _waypointSecsS;
	final public double[] _pathLats, _pathLngs;
	final public double[] _specLooseLats, _specLooseLngs;
	final public double[] _tsLooseLats, _tsLooseLngs;
	final public double[] _tsTightLats, _tsTightLngs;
	final public double[] _excTightLats, _excTightLngs;

	/** "TheirStyle" outputs. */
	final public double _orntn;
	final public double _tsNmi, _sllNmi;
	final public double _lenNmi, _widNmi;
	final public boolean _ps;

	final public double _eplNmi;

	/** Number to use for repeated results with this output: */
	final public double _rawSearchKtsToUse;

	/** The C++ entry point; not used except with JNI. */
	public SimLibPattern(final double rawSearchKts, final long baseSecs,
			final int searchDurationSecs, //
			final double centerLat0, final double centerLng0, //
			final double orntn0, final boolean firstTurnRight, //
			final double minTsNmi0, final double fxdTsNmi,
			final double excBufferNmi, //
			final double lenNmi0, final double widNmi0, final boolean ps0, //
			final String motionTypeId, final boolean expandSpecsIfNeeded) {
		this(rawSearchKts, baseSecs, /* baseEqualsUnix= */true,
				searchDurationSecs, centerLat0, centerLng0, orntn0, firstTurnRight,
				minTsNmi0, fxdTsNmi, excBufferNmi, lenNmi0, widNmi0, ps0,
				motionTypeId, expandSpecsIfNeeded);
	}

	public SimLibPattern(final double rawSearchKts, final long baseSecs,
			final boolean baseEqualsUnix, final int searchDurationSecs, //
			final double centerLat0, final double centerLng0, //
			final double orntn0, final boolean firstTurnRight, //
			final double minTsNmi0, final double fxdTsNmi,
			final double excBufferNmi, //
			final double lenNmi0, final double widNmi0, final boolean ps0, //
			final String motionTypeId, final boolean expandSpecsIfNeeded) {
		/** The following are read in and do not change. */
		_firstTurnRight = firstTurnRight;
		_rawSearchKts = rawSearchKts;
		_baseSecs = baseSecs;
		_baseEqualsUnix = baseEqualsUnix;
		_searchDurationSecs = searchDurationSecs;
		double minTsNmi = Double.NaN;
		double centerLat = 91d;
		double centerLng = -181d;
		double orntn = Double.NaN;
		double tsNmi = Double.NaN;
		double sllNmi = Double.NaN;
		double lenNmi = Double.NaN;
		double widNmi = Double.NaN;
		boolean ps = true;

		SphericalTimedSegs sphericalTimedSegs = null;
		double eplNmi = Double.NaN;

		_isPsCs = widNmi0 > 0d;
		_isSs = !_isPsCs && lenNmi0 > 0d;
		_isVs = !_isPsCs && !_isSs;
		final CppToJavaTracer cppToJavaTracer;
		if (_isPsCs) {
			cppToJavaTracer = new CppToJavaTracer("LadderPattern");
		} else if (_isSs) {
			cppToJavaTracer = new CppToJavaTracer("SsPattern");
		} else {
			cppToJavaTracer = new CppToJavaTracer("VsPattern");
		}
		PsCsPattern psCsPattern = null;
		SsPattern ssPattern = null;
		VsPattern vsPattern = null;

		try {
			centerLat = PatternUtilStatics.DiscretizeLat(centerLat0);
			centerLng = PatternUtilStatics.DiscretizeLat(centerLng0);
			final LatLng3 center = LatLng3.getLatLngB(centerLat, centerLng);
			if (minTsNmi0 > 0d) {
				minTsNmi = PatternUtilStatics
						.roundWithMinimum(PatternUtilStatics._TsInc, minTsNmi0);
			} else {
				minTsNmi = PatternUtilStatics._TsInc;
			}

			if (cppToJavaTracer.isActive()) {
				final double searchHrs = searchDurationSecs / 3600d;
				final double rnddOrntn = PatternUtilStatics.DiscretizeOrntn(orntn);
				final String firstTurnRightString = firstTurnRight ? "Rt" : "Lt";
				final String psCsString;
				if (_isPsCs) {
					psCsString = ps0 ? "PS" : "CS";
				} else {
					psCsString = "";
				}
				final String expandTruncateString;
				if (_isPsCs || _isSs) {
					expandTruncateString =
							expandSpecsIfNeeded ? "EXPAND" : "TRUNCATE";
				} else {
					expandTruncateString = "";
				}
				final String s = String.format(
						"\nRounded Inputs:\n\trawSpeedKts[%f] searchHrs[%f] " + //
								"center%s orntn[%f] %s " + //
								"\n\tminTs[%f] fxdTs[%f] buffer[%f] " + //
								"\n\tlen/wid[%f/%f]%s" + //
								"\n\tmotionTypeId[%s] %s", //
						rawSearchKts, searchHrs, //
						center.getString(), rnddOrntn, firstTurnRightString, //
						minTsNmi, fxdTsNmi, excBufferNmi, //
						lenNmi0, widNmi0, psCsString, //
						motionTypeId, expandTruncateString);
				cppToJavaTracer.writeTrace(s);
			}

			if (_isPsCs) {
				psCsPattern = new PsCsPattern(cppToJavaTracer, rawSearchKts,
						_baseSecs, searchDurationSecs, center, orntn0, firstTurnRight,
						minTsNmi0, fxdTsNmi, excBufferNmi, lenNmi0, widNmi0, ps0,
						motionTypeId, expandSpecsIfNeeded);
				sphericalTimedSegs = psCsPattern._sphericalTimedSegs;
				/** "TheirStyle" outputs. */
				lenNmi = psCsPattern._lenNmi;
				widNmi = psCsPattern._widNmi;
				ps = psCsPattern._ps;
				tsNmi = Math.abs(psCsPattern._sgndTsNmi);
				sllNmi = psCsPattern._sllNmi;
				orntn = psCsPattern._orntn;
				eplNmi = psCsPattern._eplNmi;
				centerLat = psCsPattern._center.getLat();
				centerLng = psCsPattern._center.getLng();
			} else if (_isSs) {
				ssPattern = new SsPattern(cppToJavaTracer, rawSearchKts, _baseSecs,
						searchDurationSecs, center, orntn0, firstTurnRight, minTsNmi,
						fxdTsNmi, excBufferNmi, lenNmi0, motionTypeId,
						expandSpecsIfNeeded);
				sphericalTimedSegs = ssPattern._sphericalTimedSegs;
				/** "TheirStyle" outputs. */
				tsNmi = Math.abs(ssPattern._sgndTsNmi);
				sllNmi = Double.NaN;
				lenNmi = ssPattern._lenNmi;
				widNmi = Double.NaN;
				orntn = ssPattern._orntn;
				ps = true;
				eplNmi = ssPattern._eplNmi;
				centerLat = ssPattern._center.getLat();
				centerLng = ssPattern._center.getLng();
			} else {
				/** This is a VS pattern. */
				vsPattern = new VsPattern(cppToJavaTracer, rawSearchKts, _baseSecs,
						searchDurationSecs, center, orntn0, firstTurnRight,
						excBufferNmi, motionTypeId);
				sphericalTimedSegs = vsPattern._sphericalTimedSegs;
				/** "TheirStyle" outputs. */
				tsNmi = Math.abs(vsPattern._sgndTsNmi);
				sllNmi = Double.NaN;
				lenNmi = Math.abs(vsPattern._sgndAcrossNmi);
				widNmi = Double.NaN;
				orntn = vsPattern._orntn;
				ps = true;
				eplNmi = vsPattern._eplNmi;
				centerLat = vsPattern._center.getLat();
				centerLng = vsPattern._center.getLng();
			}
		} catch (final Exception e) {
			minTsNmi = Double.NaN;
			centerLat = 91d;
			centerLng = -181d;
			orntn = Double.NaN;
			tsNmi = sllNmi = Double.NaN;
			lenNmi = widNmi = Double.NaN;
			ps = true;
			eplNmi = Double.NaN;
			sphericalTimedSegs = null;
		}
		_minTsNmi = minTsNmi;
		_centerLat = centerLat;
		_centerLng = centerLng;
		_orntn = orntn;
		_tsNmi = tsNmi;
		_sllNmi = sllNmi;
		_lenNmi = lenNmi;
		_widNmi = widNmi;
		_ps = ps;
		_eplNmi = eplNmi;
		final double searchHrs = _searchDurationSecs / 3600d;
		_rawSearchKtsToUse =
				(_eplNmi / searchHrs) / PatternUtilStatics._EffectiveSpeedReduction;
		_sphericalTimedSegs = sphericalTimedSegs;
		if (_sphericalTimedSegs != null) {
			final LatLng3[] path = _sphericalTimedSegs._path;
			_waypointSecsS = _sphericalTimedSegs._waypointSecsS;
			final int nPoints = path == null ? 0 : path.length;
			_pathLats = new double[nPoints];
			_pathLngs = new double[nPoints];
			for (int k = 0; k < nPoints; ++k) {
				final LatLng3 latLng0 = path[k];
				_pathLats[k] = latLng0.getLat();
				_pathLngs[k] = latLng0.getLng();
			}
			final double[][] specLooseAndTightArrays =
					getLooseAndTightArrays(SphericalTimedSegs.LoopType.SPEC);
			_specLooseLats = specLooseAndTightArrays[0];
			_specLooseLngs = specLooseAndTightArrays[1];
			final double[][] tsLooseAndTightArrays =
					getLooseAndTightArrays(SphericalTimedSegs.LoopType.TS);
			_tsLooseLats = tsLooseAndTightArrays[0];
			_tsLooseLngs = tsLooseAndTightArrays[1];
			_tsTightLats = tsLooseAndTightArrays[2];
			_tsTightLngs = tsLooseAndTightArrays[3];
			final double[][] excLooseAndTightArrays =
					getLooseAndTightArrays(SphericalTimedSegs.LoopType.EXC);
			_excTightLats = excLooseAndTightArrays[2];
			_excTightLngs = excLooseAndTightArrays[3];
		} else {
			_waypointSecsS = null;
			_pathLats = _pathLngs = null;
			_specLooseLats = _specLooseLngs = null;
			_tsLooseLats = _tsLooseLngs = null;
			_tsTightLats = _tsTightLngs = null;
			_excTightLats = _excTightLngs = null;
		}

		if (cppToJavaTracer.isActive()) {
			final String myStyleString;
			if (_isPsCs && psCsPattern != null) {
				myStyleString = psCsPattern.getMyStyleString();
			} else if (_isSs && ssPattern != null) {
				myStyleString = ssPattern.getMyStyleString();
			} else if (_isVs && vsPattern != null) {
				myStyleString = vsPattern.getMyStyleString();
			} else {
				myStyleString = "BAD MyStyleString";
			}
			final String waypointsAndPolygonsString =
					getWaypointsAndPolygonsString();
			final String s = String.format("\n\n\tSummary-\t%s\n%s", //
					myStyleString, waypointsAndPolygonsString);
			cppToJavaTracer.writeTrace(s);
		}
	}

	public double[][] getLooseAndTightArrays(final LoopType loopType) {
		final LatLng3[] looseLatLngs =
				_sphericalTimedSegs.getLooseLatLngs(loopType);
		final double[] looseLats, looseLngs;
		if (looseLatLngs == null) {
			looseLats = looseLngs = null;
		} else {
			final int nLoose = looseLatLngs.length;
			looseLats = new double[nLoose];
			looseLngs = new double[nLoose];
			for (int k = 0; k < nLoose; ++k) {
				final LatLng3 latLng = looseLatLngs[k];
				looseLats[k] = latLng.getLat();
				looseLngs[k] = latLng.getLng();
			}
		}
		final double[] tightLats, tightLngs;
		final LatLng3[] tightLatLngs =
				_sphericalTimedSegs.getTightLatLngs(loopType);
		if (tightLatLngs == null) {
			tightLats = tightLngs = null;
		} else {
			final int nTight = tightLatLngs.length;
			tightLats = new double[nTight];
			tightLngs = new double[nTight];
			for (int k = 0; k < nTight; ++k) {
				final LatLng3 latLng = tightLatLngs[k];
				tightLats[k] = latLng.getLat();
				tightLngs[k] = latLng.getLng();
			}
		}
		return new double[][] { looseLats, looseLngs, tightLats, tightLngs };
	}

	public SphericalTimedSegs getSphericalTimedSegs() {
		return _sphericalTimedSegs;
	}

	public String getWaypointsAndPolygonsString() {
		double n1 = -90d;
		double s1 = 90d;
		double e1 = Double.NEGATIVE_INFINITY;
		double w1 = Double.POSITIVE_INFINITY;
		final int nWaypoints = _waypointSecsS.length;
		for (int k = 0; k < nWaypoints; ++k) {
			final double latK = _pathLats[k];
			final double lngK = _pathLngs[k];
			s1 = Math.min(s1, latK);
			n1 = Math.max(n1, latK);
			w1 = Math.min(w1, lngK);
			e1 = Math.max(e1, lngK);
		}
		final Extent extent1 = new Extent(w1, s1, e1, n1);
		final String extentString1 = extent1.getString();
		String s = String.format("\tWaypoints %s:", extentString1);
		for (int k = 0; k < nWaypoints; ++k) {
			final long refSecs = _baseEqualsUnix ?
					TimeUtilities.convertToRefSecs(_waypointSecsS[k]) :
					_waypointSecsS[k];
			final boolean includeSeconds = true;
			final boolean trimAtZ = false;
			final String str1 =
					TimeUtilities.formatTime(refSecs, includeSeconds, trimAtZ);
			final double latK = _pathLats[k];
			final double lngK = _pathLngs[k];
			s += String.format("\nTime[%s] Lat[%f] Lng[%f]", str1, latK, lngK);
			s1 = Math.min(s1, latK);
			n1 = Math.max(n1, latK);
			w1 = Math.min(w1, lngK);
			e1 = Math.max(e1, lngK);
		}
		s += "\n\tWaypoint Edges:";
		final String colorNameA = ColorUtils.getForegroundColorName(0);
		for (int k0 = 0; k0 < nWaypoints - 1; ++k0) {
			final double lat0 = _pathLats[k0];
			final double lng0 = _pathLngs[k0];
			final int k1 = k0 + 1;
			final double lat1 = _pathLats[k1];
			final double lng1 = _pathLngs[k1];
			final LatLng3 latLng0 = LatLng3.getLatLngB(lat0, lng0);
			final LatLng3 latLng1 = LatLng3.getLatLngB(lat1, lng1);
			final GreatCircleArc gca = GreatCircleArc.CreateGca(latLng0, latLng1);
			final String xmlString = gca.getXmlString(colorNameA, 1, "Path");
			s += "\n" + xmlString;
		}

		/** Dump _tsTight and _excTight. */
		for (int k0 = 0; k0 < SphericalTimedSegs.LoopType._LoopTypes.length;
				++k0) {
			final SphericalTimedSegs.LoopType loopType =
					SphericalTimedSegs.LoopType._LoopTypes[k0];
			final String colorNameB = ColorUtils.getForegroundColorName(k0 + 1);
			final double[] tightLats, tightLngs;
			switch (loopType) {
			case TS:
				if (_isVs) {
					continue;
				}
				tightLats = _tsTightLats;
				tightLngs = _tsTightLngs;
				break;
			case EXC:
				tightLats = _excTightLats;
				tightLngs = _excTightLngs;
				break;
			default:
				continue;
			}
			final String bufferName = loopType.name();
			final int nTight = tightLats.length;
			Extent tightExtent = Extent.getUnsetExtent();
			for (int k = 0; k < nTight; ++k) {
				final double lat0 = tightLats[(k == 0 ? nTight : k) - 1];
				final double lng0 = tightLngs[(k == 0 ? nTight : k) - 1];
				final LatLng3 latLng0 = LatLng3.getLatLngB(lat0, lng0);
				final double lat1 = tightLats[k];
				final double lng1 = tightLngs[k];
				final LatLng3 latLng1 = LatLng3.getLatLngB(lat1, lng1);
				final GreatCircleArc gca =
						GreatCircleArc.CreateGca(latLng0, latLng1);
				tightExtent = tightExtent.buildExtension(gca.createExtent());
			}
			s += String.format("\n\t%sTight %s, Edges:", bufferName,
					tightExtent.getString());
			for (int k = 0; k < nTight; ++k) {
				final double lat0 = tightLats[(k == 0 ? nTight : k) - 1];
				final double lng0 = tightLngs[(k == 0 ? nTight : k) - 1];
				final LatLng3 latLng0 = LatLng3.getLatLngB(lat0, lng0);
				final double lat1 = tightLats[k];
				final double lng1 = tightLngs[k];
				final LatLng3 latLng1 = LatLng3.getLatLngB(lat1, lng1);
				final GreatCircleArc gca =
						GreatCircleArc.CreateGca(latLng0, latLng1);
				final String xmlString =
						gca.getXmlString(colorNameB, 1, bufferName);
				s += "\n" + xmlString;
			}
		}
		/** Dump _specLoose and _tsLoose. */
		for (int k0 = 0; k0 < SphericalTimedSegs.LoopType._LoopTypes.length;
				++k0) {
			final SphericalTimedSegs.LoopType loopType =
					SphericalTimedSegs.LoopType._LoopTypes[k0];
			final String colorNameC = ColorUtils.getForegroundColorName(k0 + 5);
			final double[] looseLats, looseLngs;
			switch (loopType) {
			case TS:
				if (_isVs) {
					continue;
				}
				looseLats = _tsLooseLats;
				looseLngs = _tsLooseLngs;
				break;
			case SPEC:
				looseLats = _specLooseLats;
				looseLngs = _specLooseLngs;
				break;
			default:
				continue;
			}
			final String bufferName = loopType.name();
			final int nLoose = looseLats.length;
			Extent looseExtent = Extent.getUnsetExtent();
			for (int k1 = 0; k1 < nLoose; ++k1) {
				final double lat0 = looseLats[(k1 == 0 ? nLoose : k1) - 1];
				final double lng0 = looseLngs[(k1 == 0 ? nLoose : k1) - 1];
				final LatLng3 latLng0 = LatLng3.getLatLngB(lat0, lng0);
				final double lat1 = looseLats[k1];
				final double lng1 = looseLngs[k1];
				final LatLng3 latLng1 = LatLng3.getLatLngB(lat1, lng1);
				final GreatCircleArc gca =
						GreatCircleArc.CreateGca(latLng0, latLng1);
				looseExtent = looseExtent.buildExtension(gca.createExtent());
			}
			s += String.format("\n\t%sLoose %s, Edges:", bufferName,
					looseExtent.getString());
			for (int k1 = 0; k1 < nLoose; ++k1) {
				final double lat0 = looseLats[(k1 == 0 ? nLoose : k1) - 1];
				final double lng0 = looseLngs[(k1 == 0 ? nLoose : k1) - 1];
				final LatLng3 latLng0 = LatLng3.getLatLngB(lat0, lng0);
				final double lat1 = looseLats[k1];
				final double lng1 = looseLngs[k1];
				final LatLng3 latLng1 = LatLng3.getLatLngB(lat1, lng1);
				final GreatCircleArc gca =
						GreatCircleArc.CreateGca(latLng0, latLng1);
				final String xmlString =
						gca.getXmlString(colorNameC, 1, bufferName);
				s += "\n" + xmlString;
			}
		}
		return s;
	}

	/** Testing PsCsPatterns. */
	public static void mainLp(final String[] args) {
		final double rawSearchKts =
				720d / PatternUtilStatics._EffectiveSpeedReduction;
		final long baseSecs = 0;
		final int searchDurationSecs = 3600;
		final double centerLat = 0d;
		final double centerLng = 0d;
		final double orntn = 45d;
		final boolean firstTurnRight = true;
		final double minTsNmi = 0.1;
		final double fxdTsNmi = Double.NaN;
		final double excBufferNmi = 10d;
		final double lenNmi = 120d;
		final double widNmi = 6d;
		final boolean ps = true;
		final String motionTypeId = MotionType.GREAT_CIRCLE.getId();
		@SuppressWarnings("unused")
		final SimLibPattern simLibPattern2 = new SimLibPattern(rawSearchKts,
				baseSecs, /* baseEqualsUnix= */false, searchDurationSecs, //
				centerLat, centerLng, //
				orntn, firstTurnRight, //
				minTsNmi, fxdTsNmi, excBufferNmi, //
				lenNmi, widNmi, ps, //
				motionTypeId, /* expandSpecsIfNeeded= */true);
		final SimLibPattern simLibPattern =
				new SimLibPattern(4d, 0L, true, 75 * 60, 0d, 0d, 0d, true, 0.1,
						Double.NaN, 0.5, 1.8, 1.4, false, "GC", false);
		final String s = simLibPattern.getString();
		System.out.printf("\n%s", s);
	}

	private String getString() {
		/**
		 * <pre>
		 * final boolean _isPsCs, _isSs, _isVs;
		 * final public SphericalTimedSegs _sphericalTimedSegs;
		 * final public double _centerLat, _centerLng;
		 * final public long[] _waypointSecsS;
		 * final public double[] _pathLats, _pathLngs;
		 * final public double[] _specLooseLats, _specLooseLngs;
		 * final public double[] _tsLooseLats, _tsLooseLngs;
		 * final public double[] _tsTightLats, _tsTightLngs;
		 * final public double[] _excTightLats, _excTightLngs;
		 * </pre>
		 */
		final PatternKind patternKind = _isPsCs ? PatternKind.PSCS :
				(_isSs ? PatternKind.SS : PatternKind.VS);
		String s = patternKind.outsideName();
		s += String.format("\nCenter/Orntn[%s/%.2f]",
				LatLng3.getLatLngB(_centerLat, _centerLng).getString(), _orntn);
		s += getString("Path", _pathLats, _pathLngs);
		s += getString("SpecLoose", _specLooseLats, _specLooseLngs);
		s += getString("TsLoose", _tsLooseLats, _tsLooseLngs);
		s += getString("TsTight", _tsTightLats, _tsTightLngs);
		s += getString("ExcTight", _excTightLats, _excTightLngs);
		return s;
	}

	private static String getString(final String caption, final double[] lats,
			final double[] lngs) {
		final int nPoints = lats == null ? 0 : lats.length;
		String s = String.format("\n%s: %d points",
				caption == null ? caption : "NoCaption", nPoints);
		for (int k = 0; k < nPoints; ++k) {
			if (k % 2 == 0) {
				s += "\n";
			}
			s += LatLng3.getLatLngB(lats[k], lngs[k]).getString();
		}
		return s;
	}

	@Override
	public String toString() {
		return getString();
	}

	/** Testing SsPatterns. */
	public static void mainSs(final String[] args) {
		/**
		 * <pre>
		rawSpeedKts0[90.000000] searchHrs[1.000000] center[26.188333/-78.990000] len[6.000000] orntn[0.000000] Rt
		buffer[1.000000] minTs[1.000000] fxdTs[1.000000] motionTypeId[GC]
		rawSpeedKts0[90.000000] searchHrs[1.000000] center[26.188333/-78.990000] len[9.000000] orntn[0.000000] Rt
		buffer[1.000000] minTs[1.000000] fxdTs[1.000000] motionTypeId[GC]
		 *
		 * </pre>
		 */
		@SuppressWarnings("unused")
		final SimLibPattern simLibPattern =
				new SimLibPattern(90d, 0L, /* baseEqualsUnix= */false, 3600, //
						26.188333, -78.990000, //
						/* orntn= */0d, /* firstTurnRt= */true, //
						/* minTs= */1d, /* fxdTs= */1d, /* buffer= */1d, //
						9d, Double.NaN, /* ps= */true, //
						"GC", /* expandSpecsIfNeeded= */true);
	}

	/** Testing VsPatterns. */
	public static void main(final String[] args) {
		/**
		 * <pre>
		rawSpeedKts0[90.000000] searchHrs[1.000000] center[26.188333/-78.990000] len[6.000000] orntn[0.000000] Rt
		buffer[1.000000] minTs[1.000000] fxdTs[1.000000] motionTypeId[GC]
		rawSpeedKts0[90.000000] searchHrs[1.000000] center[26.188333/-78.990000] len[9.000000] orntn[0.000000] Rt
		buffer[1.000000] minTs[1.000000] fxdTs[1.000000] motionTypeId[GC]
		 *
		 * </pre>
		 */
		@SuppressWarnings("unused")
		final SimLibPattern simLibPattern =
				new SimLibPattern(90d, 0L, /* baseEqualsUnix= */false, 3600, //
						26.188333, -78.990000, //
						/* orntn= */0d, /* firstTurnRt= */true, //
						/* minTs= */1d, /* fxdTs= */1d, /* buffer= */1d, //
						Double.NaN, Double.NaN, /* ps= */true, //
						"GC", /* expandSpecsIfNeeded= */true);
	}

}
