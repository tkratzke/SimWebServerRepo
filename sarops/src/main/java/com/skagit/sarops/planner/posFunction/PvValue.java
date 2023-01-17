package com.skagit.sarops.planner.posFunction;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.w3c.dom.Element;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.Sortie;
import com.skagit.sarops.model.io.ModelReader;
import com.skagit.sarops.model.io.ModelReader.ReaderException;
import com.skagit.sarops.model.io.ModelReader.StringPlus;
import com.skagit.sarops.planner.ParticlesManager;
import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.plannerModel.PatternKind;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.simCaseManager.MainRunner;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.simCaseManager.SimCaseManager.SimCase;
import com.skagit.sarops.tracker.CoverageToPodCurve;
import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.sarops.tracker.lrcSet.LrcSet;
import com.skagit.sarops.util.patternUtils.DiscreteLpSpecToTs;
import com.skagit.sarops.util.patternUtils.DiscreteSsSpecToTs;
import com.skagit.sarops.util.patternUtils.MyStyle;
import com.skagit.sarops.util.patternUtils.PatternUtilStatics;
import com.skagit.sarops.util.patternUtils.PsCsInfo;
import com.skagit.sarops.util.patternUtils.SimLibPattern;
import com.skagit.sarops.util.patternUtils.SphericalTimedSegs;
import com.skagit.sarops.util.patternUtils.SphericalTimedSegs.LoopType;
import com.skagit.sarops.util.patternUtils.SsInfo;
import com.skagit.sarops.util.patternUtils.VsInfo;
import com.skagit.util.CombinatoricTools;
import com.skagit.util.Constants;
import com.skagit.util.ElementIterator;
import com.skagit.util.MathX;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.StringUtilities;
import com.skagit.util.TimeUtilities;
import com.skagit.util.cdf.CcwGcas;
import com.skagit.util.colorUtils.ColorUtils;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.geometry.gcaSequence.Loop3Statics;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.MotionType;

public class PvValue implements Comparable<PvValue> {
	final private static double _NmiToR = MathX._NmiToR;

	/** Standard Comparator. */
	final public static Comparator<? super PvValue> _ByPvGrandOrdinalFirst =
			new Comparator<>() {

				@Override
				public int compare(final PvValue pvValue0, final PvValue pvValue1) {
					if ((pvValue0 == null) != (pvValue1 == null)) {
						return pvValue0 == null ? -1 : 1;
					}
					if (pvValue0 == null) {
						return 0;
					}
					final PatternVariable pv0 = pvValue0.getPv();
					final PatternVariable pv1 = pvValue1.getPv();
					int compareValue = PatternVariable._ByGrandOrd.compare(pv0, pv1);
					if (compareValue != 0) {
						return compareValue;
					}
					final Sortie sortie0 = pvValue0._sortie;
					final Sortie sortie1 = pvValue1._sortie;
					if ((sortie0 != null) != (sortie1 == null)) {
						return sortie0 == null ? -1 : 1;
					}
					if (sortie0 != null) {
						return sortie0.deepCompareTo(sortie1);
					}
					final MyStyle myStyle0 = pvValue0._myStyle;
					final MyStyle myStyle1 = pvValue1._myStyle;
					compareValue = myStyle0.deepCompareTo(myStyle1);
					if (compareValue != 0) {
						return compareValue;
					}
					return 0;
				}
			};
	final public static Comparator<? super PvValue> _ByPvOrdWithinPvSeq =
			new Comparator<>() {

				@Override
				public int compare(final PvValue pvValue0, final PvValue pvValue1) {
					if (pvValue0 == null) {
						return pvValue1 == null ? 0 : -1;
					} else if (pvValue1 == null) {
						return 1;
					}
					final PatternVariable pv0 = pvValue0.getPv();
					final PatternVariable pv1 = pvValue1.getPv();
					final int compareValue =
							PatternVariable._ByOrdWithinPvSeq.compare(pv0, pv1);
					if (compareValue != 0) {
						return compareValue;
					}
					return _ByPvGrandOrdinalFirst.compare(pvValue0, pvValue1);
				}
			};

	/** Data. */
	final PatternVariable _pv;
	/**
	 * If and only if _myStyle == null, this is a TrackLine _pv is a TrackLine
	 * PV.
	 */
	final private MyStyle _myStyle;

	/**
	 * The sortie, the TsBoxes, and the exclusion area. Note that in creating
	 * Loop3's, we do not retain the order of the LatLngs. Hence we store the
	 * original LatLng arrays as well.
	 */
	final private Sortie _sortie;
	final private LatLng3[] _looseSpecLatLngs;
	final private LatLng3[] _tightTsLatLngs;
	final private LatLng3[] _looseTsLatLngs;
	final private LatLng3[] _tightExcLatLngs;
	final private Loop3 _tightTsLoop;
	final private CcwGcas _tightExcCcwGcas;

	/**
	 * To avoid re-computing this for each particlette that is within the
	 * TsBox, we store it here. Note that we use pos = poc * pod only for
	 * optimization, so we store only the viz2 LrcSets.
	 */
	final private HashMap<Integer, Double> _objectTypeToViz2Pod;

	public boolean onMars() {
		return _myStyle != null && _myStyle.onMars();
	}

	public double getSpecAlongR() {
		if (_myStyle == null) {
			return Double.NaN;
		}
		return _myStyle.getSpecAlongR();
	}

	public double getSpecSgndAcrossR() {
		if (_myStyle == null) {
			return Double.NaN;
		}
		return _myStyle.getSpecSgndAcrossR();
	}

	public double getSpecAlongNmi() {
		return getSpecAlongR() / _NmiToR;
	}

	public double getSpecSgndAcrossNmi() {
		if (_pv.getPatternKind().isVs()) {
			return getFirstTurnRight() ? 1d : -1d;
		}
		return getSpecSgndAcrossR() / _NmiToR;
	}

	/** ctor 1: Used with a non-null MyStyle, including OnMars. */
	public PvValue(final PatternVariable pv, final MyStyle myStyle) {
		_pv = pv;
		_myStyle = myStyle;
		final LatLng3 center = _myStyle.getCenter();
		final double centerLat = center.getLat();
		final double centerLng = center.getLng();
		final double minTsNmi = _pv.getMinTsNmi();
		final double rawSearchKts = _pv.getRawSearchKts();
		final double orntn = _myStyle.computeOrntn(rawSearchKts, minTsNmi);
		final long cstRefSecs = _myStyle.getCstRefSecs();
		final int searchDurationSecs = _myStyle.getSearchDurationSecs();
		final double searchHrs = searchDurationSecs / 3600d;

		/** Convenience values. */
		final PatternKind patternKind = _pv.getPatternKind();
		final double excBufferNmi = _pv.getExcBufferNmi();

		final boolean onMars = _myStyle.onMars();
		if (onMars) {
			_objectTypeToViz2Pod = new HashMap<>();
			final SphericalTimedSegs sphericalTimedSegs;
			/**
			 * We need a rawSearchKtsForOnMars that will use up all the time for
			 * the tiny onMars pattern. Start by computing the length of
			 * _myStyle's pattern. For OnMars, _myStyle's along and across are so
			 * small that, together with minTsNmi, the epl computed is very small.
			 */
			final double eplNmiForOnMars =
					_myStyle.computeEplNmi(rawSearchKts, minTsNmi);
			final double rawSearchKtsForOnMars = (eplNmiForOnMars / searchHrs) /
					PatternUtilStatics._EffectiveSpeedReduction;
			final SimLibPattern simLibPattern;
			final double creepHdg;
			final double tsNmiForSingleLeg;
			if (patternKind.isPsCs()) {
				final double lenNmi =
						_myStyle.computeLengthNmi(rawSearchKtsForOnMars, minTsNmi);
				final double widNmi =
						_myStyle.computeWidthNmi(rawSearchKtsForOnMars, minTsNmi);
				simLibPattern = new SimLibPattern(rawSearchKtsForOnMars, cstRefSecs,
						/* baseEqualsUnix= */false, searchDurationSecs, centerLat,
						centerLng, orntn, //
						/* firstTurnRight= */true, //
						/* minTsNmi= */minTsNmi, /* fxdTsNmi= */minTsNmi, excBufferNmi,
						lenNmi, widNmi, /* ps= */true, //
						MotionType.GREAT_CIRCLE.getId(),
						/* expandSpecsIfNeeded= */true);
				creepHdg = _myStyle.computeCreepHdg(rawSearchKts, minTsNmi);
				tsNmiForSingleLeg = pv.getMinTsNmi();
			} else if (patternKind.isSs()) {
				final double lenNmi =
						_myStyle.computeLengthNmi(rawSearchKtsForOnMars, minTsNmi);
				simLibPattern = new SimLibPattern(rawSearchKtsForOnMars, cstRefSecs,
						/* baseEqualsUnix= */false, searchDurationSecs, centerLat,
						centerLng, orntn, //
						/* firstTurnRight= */true, //
						/* minTsNmi= */minTsNmi, /* fxdTsNmi= */minTsNmi, excBufferNmi,
						lenNmi, /* widNmi= */Double.NaN, /* ps= */true, //
						MotionType.GREAT_CIRCLE.getId(),
						/* expandSpecsIfNeeded= */true);
				creepHdg = tsNmiForSingleLeg = Double.NaN;
			} else if (patternKind.isVs()) {
				simLibPattern = new SimLibPattern(rawSearchKtsForOnMars, cstRefSecs,
						/* baseEqualsUnix= */false, searchDurationSecs, centerLat,
						centerLng, orntn, //
						/* firstTurnRight= */true, //
						/* minTsNmi= */minTsNmi, /* fxdTsNmi= */minTsNmi, excBufferNmi,
						/* lenNmi= */Double.NaN, /* widNmi= */Double.NaN, /* ps= */true, //
						MotionType.GREAT_CIRCLE.getId(),
						/* expandSpecsIfNeeded= */true);
				creepHdg = tsNmiForSingleLeg = Double.NaN;
			} else {
				_sortie = null;
				_looseSpecLatLngs = null;
				_tightTsLatLngs = null;
				_looseTsLatLngs = null;
				_tightExcLatLngs = null;
				_tightTsLoop = null;
				_tightExcCcwGcas = null;
				return;
			}
			sphericalTimedSegs = simLibPattern.getSphericalTimedSegs();
			_sortie = _pv.computeSortie(sphericalTimedSegs, creepHdg,
					tsNmiForSingleLeg, /* onMars= */true);
			_looseSpecLatLngs = sphericalTimedSegs
					.getLooseLatLngs(SphericalTimedSegs.LoopType.SPEC);
			_tightTsLatLngs = sphericalTimedSegs
					.getTightLatLngs(SphericalTimedSegs.LoopType.TS);
			_looseTsLatLngs = sphericalTimedSegs
					.getLooseLatLngs(SphericalTimedSegs.LoopType.TS);
			_tightExcLatLngs = sphericalTimedSegs
					.getTightLatLngs(SphericalTimedSegs.LoopType.EXC);
			_tightTsLoop = sphericalTimedSegs.getTightTsLoop();
			_tightExcCcwGcas = sphericalTimedSegs.getTightExcCcwGcas();
			return;
		}

		/** Not onMars. */
		final boolean firstTurnRight = _myStyle.getFirstTurnRight();
		final boolean ps = _myStyle.computePs(rawSearchKts, minTsNmi);

		final SphericalTimedSegs sphericalTimedSegs;
		final double tsNmiForSingleLeg;
		if (patternKind.isPsCs()) {
			final double lenNmi =
					myStyle.computeLengthNmi(rawSearchKts, minTsNmi);
			final double widNmi = myStyle.computeWidthNmi(rawSearchKts, minTsNmi);
			tsNmiForSingleLeg =
					Math.abs(myStyle.computeSgndAcrossNmi(rawSearchKts, minTsNmi));
			final SimLibPattern ladderPattern = new SimLibPattern(rawSearchKts,
					cstRefSecs, /* baseEqualsUnix= */false, searchDurationSecs, //
					centerLat, centerLng, orntn, firstTurnRight, //
					minTsNmi, /* fxdTsNmi= */Double.NaN, excBufferNmi, //
					lenNmi, widNmi, ps, //
					MotionType.GREAT_CIRCLE.getId(), /* expandSpecsIfNeeded= */true);
			sphericalTimedSegs = ladderPattern.getSphericalTimedSegs();
		} else if (patternKind.isSs()) {
			final double lenNmi =
					myStyle.computeLengthNmi(rawSearchKts, minTsNmi);
			final SimLibPattern simLibPattern = new SimLibPattern(rawSearchKts,
					cstRefSecs, /* baseEqualsUnix= */false, searchDurationSecs, //
					centerLat, centerLng, orntn, firstTurnRight, //
					minTsNmi, /* fxdTsNmi= */Double.NaN, excBufferNmi, //
					lenNmi, /* widNmi= */Double.NaN, /* ps= */true, //
					MotionType.GREAT_CIRCLE.getId(), /* expandSpecsIfNeeded= */true);
			sphericalTimedSegs = simLibPattern.getSphericalTimedSegs();
			tsNmiForSingleLeg = Double.NaN;
		} else if (patternKind.isVs()) {
			final SimLibPattern simLibPattern = new SimLibPattern(rawSearchKts,
					cstRefSecs, /* baseEqualsUnix= */false, searchDurationSecs, //
					centerLat, centerLng, orntn, firstTurnRight, //
					minTsNmi, /* fxdTsNmi= */Double.NaN, excBufferNmi, //
					/* lenNmi= */Double.NaN, /* widNmi= */Double.NaN, /* ps= */true, //
					MotionType.GREAT_CIRCLE.getId(), /* expandSpecsIfNeeded= */true);
			sphericalTimedSegs = simLibPattern.getSphericalTimedSegs();
			tsNmiForSingleLeg = Double.NaN;
		} else {
			sphericalTimedSegs = null;
			tsNmiForSingleLeg = Double.NaN;
		}

		/** Compute the sortie and the geometries. */
		final double creepHdg =
				_myStyle.computeCreepHdg(rawSearchKts, minTsNmi);
		_sortie = _pv.computeSortie(sphericalTimedSegs, creepHdg,
				tsNmiForSingleLeg, /* onMars= */false);
		_looseSpecLatLngs = sphericalTimedSegs
				.getLooseLatLngs(SphericalTimedSegs.LoopType.SPEC);
		_tightTsLatLngs =
				sphericalTimedSegs.getTightLatLngs(SphericalTimedSegs.LoopType.TS);
		_looseTsLatLngs =
				sphericalTimedSegs.getLooseLatLngs(SphericalTimedSegs.LoopType.TS);
		_tightExcLatLngs =
				sphericalTimedSegs.getTightLatLngs(SphericalTimedSegs.LoopType.EXC);
		_tightTsLoop = sphericalTimedSegs.getTightTsLoop();
		_tightExcCcwGcas = sphericalTimedSegs.getTightExcCcwGcas();
		_objectTypeToViz2Pod = computeObjectTypeToPod();
	}

	/** Ctor 3; For reading in an initial PvValue. */
	public PvValue(final SimCase simCase, final PatternVariable pv,
			final Element initPvValueElt,
			final TreeSet<StringPlus> stringPluses) {
		/** Boilerplate */
		_pv = pv;
		final PlannerModel plannerModel = _pv.getPlannerModel();
		final Model simModel = plannerModel.getSimModel();
		final Extent modelExtent = simModel.getExtent();
		final PatternKind patternKind = _pv.getPatternKind();
		final boolean isTrackLine = patternKind == null;
		final boolean standAlone = _pv.getPvSeq() == null;

		final double tsNmiIn;
		if (isTrackLine) {
			_myStyle = null;
			final double[] creepHdgAndTs = ModelReader.readCreepHdgAndTs(simCase,
					initPvValueElt, stringPluses);
			/**
			 * Note; creepDirectionIn is used only for a single leg. In that case,
			 * we assume that it's a ladder pattern and we actually get the creep
			 * direction from the heading of the 1st leg, but use creepDirectionIn
			 * to figure out if the "creep" is to the left or right of the single
			 * leg.
			 */
			double creepHdgIn = creepHdgAndTs[0];
			if (Double.isFinite(creepHdgIn)) {
				creepHdgIn = LatLng3.getInRange0_360(creepHdgIn);
			}
			tsNmiIn = creepHdgAndTs[1];
			/** Construct a Legless sortie. */
			final double tsNmiForSingleLeg;
			if (tsNmiIn > 0d) {
				tsNmiForSingleLeg = tsNmiIn;
			} else {
				tsNmiForSingleLeg = PatternUtilStatics._TsInc;
			}
			_sortie = new Sortie(/* model= */null, /* sortieId= */_pv.getId(),
					/* sortieName= */_pv.getName(), MotionType.GREAT_CIRCLE,
					creepHdgIn, tsNmiIn, tsNmiForSingleLeg);
			long recentRefSecs = Long.MIN_VALUE;
			LatLng3 recentLatLng = null;
			final ElementIterator childIterator =
					new ElementIterator(initPvValueElt);
			/** Add waypoints. */
			while (childIterator.hasNextElement()) {
				final Element child = childIterator.nextElement();
				final String childTag = child.getTagName();
				if ("WAYPOINT".equals(childTag)) {
					try {
						final LatLng3 latLng =
								ModelReader.readLatLng(simCase, child, stringPluses);
						final long refSecs =
								ModelReader.readDtg(simCase, child, stringPluses);
						if (recentLatLng != null) {
							_sortie.addLegIfNotVacuous(recentLatLng, latLng,
									recentRefSecs, refSecs);
						}
						recentLatLng = latLng;
						recentRefSecs = refSecs;
					} catch (final ReaderException e) {
						MainRunner.HandleFatal(simCase, new RuntimeException(e));
					}
				}
			}
			/**
			 * Flesh out this TrackLine PvValue. We need the viz1 and viz2 for
			 * _sortie to work.
			 */
			_sortie.setViz1LrcSets(_pv.getViz1LrcSets());
			_sortie.setViz2LrcSets(_pv.getViz2LrcSets());
			_sortie.fillInSortieDataFromDistinctInputLegs();
			final PsCsInfo psCsInfo = _sortie.getPsCsInfo();
			final SsInfo ssInfo = _sortie.getSsInfo();
			final VsInfo vsInfo = _sortie.getVsInfo();
			if (psCsInfo != null || ssInfo != null || vsInfo != null) {
				final long cst = _sortie.getStartRefSecs();
				final long est = _sortie.getStopRefSecs();
				final int searchDurationSecs = (int) (est - cst);
				final double bufferNmi = pv.getExcBufferNmi();
				final SphericalTimedSegs sphericalTimedSegs;
				if (psCsInfo != null) {
					sphericalTimedSegs = psCsInfo.computeSphericalTimedSegs(cst,
							searchDurationSecs, bufferNmi);
				} else if (ssInfo != null) {
					sphericalTimedSegs = ssInfo.computeSphericalTimedSegs(cst,
							searchDurationSecs, bufferNmi);
				} else {
					/** vsi != null */
					sphericalTimedSegs = vsInfo.computeSphericalTimedSegs(cst,
							searchDurationSecs, bufferNmi);
				}
				_looseSpecLatLngs =
						sphericalTimedSegs.getLooseLatLngs(LoopType.SPEC);
				_tightTsLoop = sphericalTimedSegs.getTightTsLoop();
				_tightTsLatLngs = sphericalTimedSegs
						.getTightLatLngs(SphericalTimedSegs.LoopType.TS);
				_looseTsLatLngs = sphericalTimedSegs.getLooseLatLngs(LoopType.TS);
				_tightExcCcwGcas = sphericalTimedSegs.getTightExcCcwGcas();
			} else {
				final double tsNmi = _sortie.getTsNmi();
				final GreatCircleArc[] distinctGcas = _sortie.getDistinctGcas();
				_looseSpecLatLngs = null;
				_tightTsLoop = computeBufferedCcwCvxHull(/* logger= */null,
						distinctGcas, tsNmi / 2d);
				/**
				 * The screwed up order within _tightTsLoop is acceptable here, and
				 * keeping it ccw is fine as well.
				 */
				_looseTsLatLngs = _tightTsLatLngs = _tightTsLoop.getLatLngArray();
				final double excBufferNmi = _pv.getExcBufferNmi();
				final Loop3 excLoop = computeBufferedCcwCvxHull(/* logger= */null,
						distinctGcas, excBufferNmi);
				_tightExcCcwGcas = new CcwGcas(/* logger= */null, excLoop);
			}
			_tightExcLatLngs = _tightExcCcwGcas.getCcwLoop().getLatLngArray();
			_objectTypeToViz2Pod = computeObjectTypeToPod();
			return;
		}

		/** Not trackLine. Read in myStyle. */
		final long cstRefSecs;
		final int searchDurationSecs;
		if (standAlone) {
			/** Get _cstRefSecs and _estRefSecs from the PV element. */
			final Element pvElt = (Element) initPvValueElt.getParentNode();
			final String pvCstString = pvElt.getAttribute("cst");
			cstRefSecs = TimeUtilities.dtgToRefSecs(pvCstString);
			searchDurationSecs = ModelReader.getDurationSecs(simCase, pvElt,
					"duration", stringPluses);
		} else {
			/**
			 * Get _cstRefSecs and _estRefSecs from this element. Even onMars
			 * requires cst and duration.
			 */
			final String pvCstString = initPvValueElt.getAttribute("cst");
			cstRefSecs = TimeUtilities.dtgToRefSecs(pvCstString);
			searchDurationSecs = ModelReader.getDurationSecs(simCase,
					initPvValueElt, "duration", stringPluses);
		}
		/** Learn about Mars. */
		boolean onMars = false;
		try {
			onMars = ModelReader.getBoolean(simCase, initPvValueElt, "onMars",
					/* default= */false, stringPluses);
		} catch (final ReaderException e) {
		}

		MyStyle myStyle0 = null;
		if (onMars) {
			myStyle0 = new MyStyle(modelExtent, pv);
		} else {
			try {
				final double rawSearchKts = _pv.getRawSearchKts();
				/**
				 * Not onMars. center, orntn, and firstTurnRight are all mandatory
				 * for LP, VS, and SS.
				 */
				LatLng3 center = null;
				double orntn = Double.NaN;
				boolean firstTurnRight = true;
				final double centerLat = ModelReader.getDouble(simCase,
						initPvValueElt, "centerPointLat", "", Double.NaN, stringPluses);
				final double centerLng = ModelReader.getDouble(simCase,
						initPvValueElt, "centerPointLng", "", Double.NaN, stringPluses);
				final LatLng3 center0 = LatLng3.getLatLngB(centerLat, centerLng);
				center = PatternUtilStatics.DiscretizeLatLng(center0);
				final double orntn0 =
						ModelReader.getDouble(simCase, initPvValueElt, "orientation",
								" degrees clockwise from north", Double.NaN, stringPluses);
				orntn = PatternUtilStatics.DiscretizeOrntn(orntn0);
				firstTurnRight = ModelReader.getBoolean(simCase, initPvValueElt,
						"firstTurnRight", true, stringPluses);

				/** Read in the rest of the data. */
				if (patternKind.isPsCs()) {
					final boolean readInPs;
					final String psOrCsBooleanString =
							initPvValueElt.getAttribute("PS");
					if (psOrCsBooleanString == null ||
							psOrCsBooleanString.length() == 0) {
						String psOrCsString = null;
						try {
							psOrCsString = ModelReader.getStringNoDefault(simCase,
									initPvValueElt, "pathType", stringPluses);
						} catch (final ReaderException e) {
							e.printStackTrace();
							MainRunner.HandleFatal(simCase,
									new RuntimeException("Bad pathType value."));
						}
						readInPs = psOrCsString == null ? true :
								psOrCsString.compareToIgnoreCase("PS") == 0;
					} else {
						final Boolean bb =
								StringUtilities.getBoolean(psOrCsBooleanString);
						readInPs = bb == null ? false : bb;
					}
					final double specLenNmi;
					final double specWidNmi;
					final boolean ps;
					/** Read in the length and width of the SpecBox. */
					final double specLenNmi0 =
							ModelReader.getDouble(simCase, initPvValueElt, "specLength",
									" NM", Double.NaN, stringPluses);
					final double specWidNmi0 = ModelReader.getDouble(simCase,
							initPvValueElt, "specWidth", " NM", Double.NaN, stringPluses);
					if (specLenNmi0 > 0d && specWidNmi0 > 0d) {
						/** They gave us the Spec box. Use the read-in PS. */
						specLenNmi = specLenNmi0;
						specWidNmi = specWidNmi0;
						ps = readInPs;
					} else {
						/**
						 * They're giving us the rounded TsBox. Use the reverse
						 * mechanism to get the SpecBox.
						 */
						final double tsLenNmi = ModelReader.getDouble(simCase,
								initPvValueElt, "length", " NM", Double.NaN, stringPluses);
						final double tsWidNmi = ModelReader.getDouble(simCase,
								initPvValueElt, "width", " NM", Double.NaN, stringPluses);
						/** Convert to tsAlongNmi/tsAcrossNmi. */
						final double tsAlongNmi = readInPs ? tsLenNmi : tsWidNmi;
						final double tsAcrossNmi = readInPs ? tsWidNmi : tsLenNmi;
						/**
						 * Use DiscLpTsBoxAdjuster' inversion ctor to find some
						 * length/width we can use to get the rounded tsLength/tsWidth
						 * we just read in.
						 */
						final double eplNmi =
								rawSearchKts * (searchDurationSecs / 3600d) *
										PatternUtilStatics._EffectiveSpeedReduction;
						final DiscreteLpSpecToTs discLpTsBoxAdjuster =
								new DiscreteLpSpecToTs(eplNmi, tsAlongNmi, tsAcrossNmi);
						if (discLpTsBoxAdjuster.getAcross() > 0d) {
							final double alongNmiY = discLpTsBoxAdjuster.getAlong();
							final double acrossNmiY = discLpTsBoxAdjuster.getAcross();
							ps = alongNmiY >= acrossNmiY;
							specLenNmi = ps ? alongNmiY : acrossNmiY;
							specWidNmi = ps ? acrossNmiY : alongNmiY;
						} else {
							/**
							 * Couldn't reverse it. "Forward" with the given data, and
							 * allow adjustments. Just get something.
							 */
							final DiscreteLpSpecToTs discLpTsBoxAdjuster2 =
									new DiscreteLpSpecToTs(eplNmi, _pv.getMinTsNmi(),
											/* fxdTsNmi= */Double.NaN, tsAlongNmi, tsAcrossNmi,
											/* adjustSpecsIfNeeded= */true);
							final double alongNmiY = discLpTsBoxAdjuster2.getAlong();
							final double acrossNmiY = discLpTsBoxAdjuster2.getAcross();
							ps = alongNmiY >= acrossNmiY;
							specLenNmi = ps ? alongNmiY : acrossNmiY;
							specWidNmi = ps ? acrossNmiY : alongNmiY;
						}
					}
					if ((specLenNmi <= 0d)) {
						MainRunner.HandleFatal(simCase,
								new RuntimeException("Bad BOX dimensions."));
					}
					myStyle0 = new MyStyle(patternKind, cstRefSecs,
							searchDurationSecs, center, orntn, firstTurnRight, specLenNmi,
							specWidNmi, ps, /* tsNmi= */Double.NaN);
				} else if (patternKind.isSs()) {
					final double specLenNmi;
					/** Read in the length of the SpecBox. */
					final double specLenNmi0 =
							ModelReader.getDouble(simCase, initPvValueElt, "specLength",
									" NM", Double.NaN, stringPluses);
					if (specLenNmi0 > 0d) {
						/** They gave us the Spec box. Use the read-in PS. */
						specLenNmi = specLenNmi0;
					} else {
						/**
						 * They're giving us the rounded TsBox. Use the reverse
						 * mechanism to get the SpecBox.
						 */
						final double tsLenNmi = ModelReader.getDouble(simCase,
								initPvValueElt, "length", " NM", Double.NaN, stringPluses);
						/**
						 * Use DiscSsTsBoxAdjuster' inversion ctor to find some length
						 * we can use to get the rounded tsLength we just read in.
						 */
						final double eplNmi =
								rawSearchKts * (searchDurationSecs / 3600d) *
										PatternUtilStatics._EffectiveSpeedReduction;
						final DiscreteSsSpecToTs discSsTsBoxAdjuster =
								new DiscreteSsSpecToTs(eplNmi, /* acrossNmi= */tsLenNmi);
						if (discSsTsBoxAdjuster.getAcross() > 0d) {
							final double acrossNmiY = discSsTsBoxAdjuster.getAcross();
							specLenNmi = acrossNmiY;
						} else {
							/**
							 * Couldn't reverse it. "Forward" with the given data, and
							 * allow adjustments. Just get something.
							 */
							final DiscreteSsSpecToTs discSsTsBoxAdjuster2 =
									new DiscreteSsSpecToTs(eplNmi, _pv.getMinTsNmi(),
											/* fxdTsNmi= */Double.NaN, /* acrossNmi= */tsLenNmi,
											/* adjustSpecsIfNeeded= */true);
							specLenNmi = discSsTsBoxAdjuster2.getAcross();
						}
					}
					if ((specLenNmi <= 0d)) {
						MainRunner.HandleFatal(simCase,
								new RuntimeException("Bad BOX dimensions."));
					}
					myStyle0 = new MyStyle(patternKind, cstRefSecs,
							searchDurationSecs, center, orntn, firstTurnRight, specLenNmi,
							/* widthNmi= */Double.NaN, /* ps= */true,
							/* tsNmi= */Double.NaN);
				} else if (patternKind.isVs()) {
					/** Nothing else to read. */
					myStyle0 = new MyStyle(_pv, cstRefSecs, searchDurationSecs,
							center, orntn, /* alongR= */Double.NaN,
							/* sgndAcrossR= */firstTurnRight ? 1d : -1d,
							/* inputIsRadians= */'n');
				}
			} catch (final ReaderException e) {
				MainRunner.HandleFatal(simCase,
						new RuntimeException("Bad BOX tag."));
			}
		}
		final MyStyle myStyle = myStyle0;

		/**
		 * We now have myStyle. Create a PvValue from it and copy the fields
		 * over.
		 */
		final PvValue pvValue = new PvValue(pv, myStyle);
		/** Copy fields over. */
		_myStyle = pvValue._myStyle;
		_sortie = pvValue._sortie;
		_looseSpecLatLngs = pvValue._looseSpecLatLngs;
		_tightTsLatLngs = pvValue._tightTsLatLngs;
		_looseTsLatLngs = pvValue._looseTsLatLngs;
		_tightExcLatLngs = pvValue._tightExcLatLngs;
		_tightTsLoop = pvValue._tightTsLoop;
		_tightExcCcwGcas = pvValue._tightExcCcwGcas;
		_objectTypeToViz2Pod = pvValue._objectTypeToViz2Pod;
	}

	/** Public pseudo-ctor for LP and SS. */
	public static PvValue createPvValue(final PatternVariable pv,
			final long cstRefSecs, final int searchDurationSecs,
			final LatLng3 center, final double firstLegDirR0,
			final double alongR0, final double sgndAcrossR0) {
		final PatternKind patternKind = pv.getPatternKind();
		final MyStyle myStyle;
		final double firstLegDirR;
		final PlannerModel plannerModel = pv.getPlannerModel();
		if (patternKind.isPsCs()) {
			final double psCsThreshold = plannerModel.getPsCsThreshold();
			final double alongR;
			final double sgndAcrossR;
			if (alongR0 * psCsThreshold < Math.abs(sgndAcrossR0)) {
				/** Turn it around to avoid a violation. */
				alongR = Math.abs(sgndAcrossR0);
				sgndAcrossR = alongR0 * Math.signum(sgndAcrossR0);
				if (sgndAcrossR > 0d) {
					/**
					 * Add 90 to firstLegHdg. Same as subtracting 90 from firstLegDir.
					 */
					firstLegDirR = firstLegDirR0 - Constants._PiOver2;
				} else {
					firstLegDirR = firstLegDirR0 + Constants._PiOver2;
				}
			} else {
				alongR = alongR0;
				sgndAcrossR = sgndAcrossR0;
				firstLegDirR = firstLegDirR0;
			}
			myStyle = new MyStyle(pv, cstRefSecs, searchDurationSecs, center,
					firstLegDirR, alongR, sgndAcrossR, /* inputIsRadiansChar= */'y');
		} else if (patternKind.isSs()) {
			firstLegDirR = firstLegDirR0;
			myStyle = new MyStyle(pv, cstRefSecs, searchDurationSecs, center,
					firstLegDirR, /* alongR= */Double.NaN, sgndAcrossR0,
					/* inputIsRadiansChar= */'y');
		} else {
			myStyle = null;
		}
		final PvValue pvValue = new PvValue(pv, myStyle);
		return pvValue;
	}

	/** Public pseudo-ctor: Used only for VS PVs. */
	public static PvValue createPvValue(final PatternVariable pv,
			final long cstRefSecs, final int rawSearchDurationSecs,
			final LatLng3 center, final double firstLegDirR,
			final boolean firstTurnRight) {
		final MyStyle myStyle =
				new MyStyle(pv, cstRefSecs, rawSearchDurationSecs, center,
						firstLegDirR, /* alongNmi= */Double.NaN,
						/* sgndAcrossNmi= */firstTurnRight ? 1d : -1d,
						/* inputIsRads= */'y');
		final PvValue pvValue = new PvValue(pv, myStyle);
		return pvValue;
	}

	private static SimCaseManager.SimCase getSimCase(
			final PatternVariable pv) {
		final PlannerModel plannerModel = pv.getPlannerModel();
		if (plannerModel == null) {
			return null;
		}
		final Planner planner = plannerModel.getPlanner();
		return planner == null ? null : planner.getSimCase();
	}

	/**
	 * We need this for pos = poc * pod, and we use that only during
	 * optimization, so we always use viz2.
	 */
	private HashMap<Integer, Double> computeObjectTypeToPod() {
		final double tsNmi;
		if (_myStyle != null) {
			tsNmi =
					_myStyle.computeTsNmi(_pv.getRawSearchKts(), _pv.getMinTsNmi());
		} else {
			tsNmi = _sortie.getTsNmiIn();
		}
		return computeObjectTypeToPod(tsNmi);
	}

	private HashMap<Integer, Double> computeObjectTypeToPod(
			final double tsNmi) {
		final HashMap<Integer, Double> objectTypeToPod = new HashMap<>();
		final PlannerModel plannerModel = _pv.getPlannerModel();
		final CoverageToPodCurve coverageToPodCurve =
				plannerModel.getCoverageToPodCurve();
		final Map<Integer, LrcSet> viz2LrcSets = _pv.getViz2LrcSets();
		for (final Map.Entry<Integer, LrcSet> entry : viz2LrcSets.entrySet()) {
			final int objectType = entry.getKey();
			final LrcSet lrcSet = entry.getValue();
			final double sweepWidth =
					lrcSet == null ? 0d : lrcSet.getSweepWidth();
			if (sweepWidth > 0d && tsNmi > 0d) {
				final double coverage = sweepWidth / tsNmi;
				objectTypeToPod.put(objectType,
						coverageToPodCurve.coverageToPod(coverage));
			}
		}
		return objectTypeToPod;
	}

	/**
	 * This is only used during optimization (pos = poc * poc). Hence, we
	 * always use viz2.
	 */
	public double getPod(final int objectType) {
		final Double d = _objectTypeToViz2Pod.get(objectType);
		return d == null ? 0d : d;
	}

	public MyStyle getMyStyle() {
		return _myStyle;
	}

	public double getFirstLegDirR() {
		return _myStyle == null ? Double.NaN : _myStyle.getFirstLegDirR();
	}

	public LatLng3 getCenter() {
		if (_myStyle != null) {
			return _myStyle.getCenter();
		}
		final LatLng3 center = _tightTsLoop.getCenterOfMass(/* logger= */null);
		return center;
	}

	public double getFirstLegHdg() {
		final double firstLegDir = getFirstLegDirR();
		double firstLegHdg = 90d - Math.toDegrees(firstLegDir);
		firstLegHdg = LatLng3.roundToLattice0_360L(firstLegHdg);
		return firstLegHdg;
	}

	@Override
	public int compareTo(final PvValue pvValue) {
		return deepCompareTo(pvValue);
	}

	public int deepCompareTo(final PvValue pvValue) {
		if (pvValue == null) {
			return 1;
		}
		final PatternVariable hisPv = pvValue._pv;
		int compareValue = PatternVariable._ByGrandOrd.compare(_pv, hisPv);
		if (compareValue != 0) {
			return compareValue;
		}
		if (_myStyle != null) {
			compareValue = _myStyle.deepCompareTo(pvValue._myStyle);
		} else if (pvValue._myStyle != null) {
			compareValue = -pvValue._myStyle.deepCompareTo(_myStyle);
		}
		final Sortie hisSortie = pvValue._sortie;
		if ((_sortie == null) != (hisSortie == null)) {
			return _sortie == null ? -1 : 1;
		}
		if (_sortie != null) {
			return _sortie.deepCompareTo(hisSortie);
		} else if (hisSortie != null) {
			return -hisSortie.deepCompareTo(_sortie);
		}
		return 0;
	}

	public boolean isCloseTo(final PvValue pvValue) {
		if (pvValue == null) {
			return false;
		}
		final MyStyle myStyle0 = getMyStyle();
		final MyStyle myStyle1 = pvValue.getMyStyle();
		if ((myStyle0 == null) != (myStyle1 == null)) {
			return false;
		}
		if (myStyle0 != null) {
			return myStyle0.isCloseTo(myStyle1);
		}

		final Sortie sortie0 = _sortie;
		final Sortie sortie1 = pvValue._sortie;
		final List<Sortie.Leg> legs0 = sortie0.getDistinctInputLegs();
		final List<Sortie.Leg> legs1 = sortie1.getDistinctInputLegs();
		final int n0 = legs0.size();
		final int n1 = legs1.size();
		if (n0 != n1) {
			return false;
		}
		for (int k = 0; k < n0; ++k) {
			final Sortie.Leg leg0 = legs0.get(k);
			final Sortie.Leg leg1 = legs1.get(k);
			final GreatCircleArc gca0 = leg0.getGca();
			final GreatCircleArc gca1 = leg1.getGca();
			final LatLng3 gca00 = gca0.getLatLng0();
			final LatLng3 gca10 = gca1.getLatLng0();
			if (!gca00.isWithinD(gca10, /* nmiThreshold= */0.05)) {
				return false;
			}
			final LatLng3 gca01 = gca0.getLatLng1();
			final LatLng3 gca11 = gca1.getLatLng1();
			if (!gca01.isWithinD(gca11, /* nmiThreshold= */0.05)) {
				return false;
			}
		}
		return true;
	}

	public static int ComparePvValueArrays(final PvValue[] pvValueArray0,
			final PvValue[] pvValueArray1) {
		if (pvValueArray0 == null) {
			return pvValueArray1 == null ? 0 : -1;
		} else if (pvValueArray1 == null) {
			return 1;
		}
		final int n0 = pvValueArray0.length;
		final int n1 = pvValueArray1.length;
		final TreeSet<PvValue> pvValues0 = new TreeSet<>();
		final TreeSet<PvValue> pvValues1 = new TreeSet<>();
		for (int k = 0; k < n0; ++k) {
			if (pvValueArray0[k] != null) {
				pvValues0.add(pvValueArray0[k]);
			}
		}
		for (int k = 0; k < n1; ++k) {
			if (pvValueArray1[k] != null) {
				pvValues1.add(pvValueArray1[k]);
			}
		}
		final int nNonNulls0 = pvValues0.size();
		final int nNonNulls1 = pvValues1.size();
		if (nNonNulls0 != nNonNulls1) {
			return nNonNulls0 - nNonNulls1;
		}
		final Iterator<PvValue> it0 = pvValues0.iterator();
		final Iterator<PvValue> it1 = pvValues1.iterator();
		while (it0.hasNext()) {
			final PvValue pvValue0 = it0.next();
			final PvValue pvValue1 = it1.next();
			final int compareValue = pvValue0.compareTo(pvValue1);
			if (compareValue != 0) {
				return compareValue;
			}
		}
		return 0;
	}

	/** Used only for TrackLine PvValues. */
	private static Loop3 computeBufferedCcwCvxHull(final MyLogger logger,
			final GreatCircleArc[] gcas, final double bufferNmi) {
		final int nGcas = gcas.length;
		final TreeSet<LatLng3> distinctLatLngs =
				new TreeSet<>(LatLng3._ByLatThenLng);
		for (int k = 0; k < nGcas; ++k) {
			final GreatCircleArc gca = gcas[k];
			final LatLng3[] ccwBox = Loop3Statics.createCcwBox(gca.getLatLng0(),
					gca.getLatLng1(), bufferNmi, bufferNmi, bufferNmi, bufferNmi);
			distinctLatLngs.addAll(Arrays.asList(ccwBox));
		}
		final Loop3 cvxHull =
				Loop3Statics.getConvexHull(logger, distinctLatLngs, /* cw= */false);
		final List<LatLng3> separated = cvxHull.cullSmallEdges(0.005);
		if (separated.size() == cvxHull.getNLatLngs()) {
			return cvxHull;
		}
		final LatLng3[] latLngArray =
				separated.toArray(new LatLng3[separated.size()]);
		final Loop3 newLoop = Loop3.getLoop(logger, /* id= */0, /* subId= */0,
				cvxHull.getFlag(), /* ancestorId= */-1, latLngArray,
				/* logChanges= */false, /* debug= */false);
		if (newLoop.getNLatLngs() != latLngArray.length ||
				latLngArray.length < 4) {
			return cvxHull;
		}
		return newLoop;
	}

	public boolean tsBoxContains(final LatLng3 latLng) {
		if (_tightTsLoop == null) {
			return false;
		}
		final SimCaseManager.SimCase simCase = getSimCase(_pv);
		final MyLogger logger = SimCaseManager.getLogger(simCase);
		return _tightTsLoop.borderOrInteriorContains(logger, latLng);
	}

	public double[] getProportionInAndNftPFail(
			final ParticleIndexes prtclIndxs) {
		if (onMars()) {
			return new double[] { 0d, 1d };
		}
		int nContained = 0;
		double totalOfPFails = 0d;

		final long cstRefSecs = getCstRefSecs();
		final long searchDurationSecs = getSearchDurationSecs();
		final long endRefSecs = cstRefSecs + searchDurationSecs;
		final long[] fencePosts = CombinatoricTools.getFenceposts(cstRefSecs,
				endRefSecs, ParticlesManager._NumberOfStages);
		final ParticlesManager particlesManager =
				_pv.getPlannerModel().getPlanner().getParticlesManager();
		for (final long fencePost : fencePosts) {
			final LatLng3 latLng =
					particlesManager.getPosition(fencePost, prtclIndxs);
			if (tsBoxContains(latLng)) {
				++nContained;
				final int objectType =
						particlesManager.getObjectType(fencePost, prtclIndxs);
				totalOfPFails += 1d - getPod(objectType);
			} else {
				totalOfPFails += 1d;
			}
		}
		final double propIn =
				(double) nContained / ParticlesManager._NumberOfStages;
		final double pFail = totalOfPFails / ParticlesManager._NumberOfStages;
		return new double[] { propIn, pFail };
	}

	public double getCos() {
		if (_myStyle == null) {
			final PsCsInfo psCsInfo = _sortie.getPsCsInfo();
			if (psCsInfo != null) {
				final double cos =
						MathX.cosX(Math.toRadians(90d - psCsInfo.getFirstLegHdg()));
				return cos;
			}
			final VsInfo vsInfo = _sortie.getVsInfo();
			if (vsInfo != null) {
				final double cos =
						MathX.cosX(Math.toRadians(90d - vsInfo.getFirstLegHdg()));
				return cos;
			}
			final SsInfo ssInfo = _sortie.getSsInfo();
			if (ssInfo != null) {
				final double cos =
						MathX.cosX(Math.toRadians(90d - ssInfo.getFirstLegHdg()));
				return cos;
			}
			/** We have no way of specifying an angle for an irregular sortie. */
			return Double.NaN;
		}
		return MathX.cosX(getMyStyle().getFirstLegDirR());
	}

	public double getSin() {
		if (_myStyle == null) {
			final PsCsInfo psCsInfo = _sortie.getPsCsInfo();
			if (psCsInfo != null) {
				final double sin =
						MathX.sinX(Math.toRadians(90d - psCsInfo.getFirstLegHdg()));
				return sin;
			}
			final VsInfo vsInfo = _sortie.getVsInfo();
			if (vsInfo != null) {
				final double sin =
						MathX.sinX(Math.toRadians(90d - vsInfo.getFirstLegHdg()));
				return sin;
			}
			final SsInfo ssInfo = _sortie.getSsInfo();
			if (ssInfo != null) {
				final double sin =
						MathX.sinX(Math.toRadians(90d - ssInfo.getFirstLegHdg()));
				return sin;
			}
			/** We have no way of specifying an angle for an irregular sortie. */
			return Double.NaN;
		}
		return MathX.sinX(getMyStyle().getFirstLegDirR());
	}

	public double getCreepHdg() {
		if (_myStyle == null) {
			final PsCsInfo psCsInfo = _sortie.getPsCsInfo();
			if (psCsInfo != null) {
				return psCsInfo.getCreepHdg();
			}
			return _sortie.getCreepHdgIn();
		}
		if (_pv.getPatternKind().isPsCs()) {
			final MyStyle myStyle = getMyStyle();
			final double alongHdg =
					Math.toDegrees(Constants._PiOver2 - myStyle.getFirstLegDirR());
			if (myStyle.getFirstTurnRight()) {
				return NumericalRoutines.generalMod(alongHdg + 90d, 360d);
			}
			return NumericalRoutines.generalMod(alongHdg - 90d, 360d);
		}
		return Double.NaN;
	}

	public double computeTsNmi() {
		if (_myStyle == null) {
			return _sortie.getTsNmi();
		}
		return _myStyle.computeTsNmi(_pv.getRawSearchKts(), _pv.getMinTsNmi());
	}

	public double computeTsR() {
		final double tsNmi = computeTsNmi();
		final double tsR = tsNmi * _NmiToR;
		return tsR;
	}

	public double computeSllNmi() {
		if (_myStyle == null) {
			final PsCsInfo psCsInfo = _sortie.getPsCsInfo();
			if (psCsInfo != null) {
				return psCsInfo.getMaxSearchLegLengthNmi();
			}
			/** Can't do anything else for a TrackLine. */
			return Double.NaN;
		}
		return _myStyle.computeSllNmi(_pv.getRawSearchKts(), _pv.getMinTsNmi());
	}

	public double computeSllR() {
		final double sllNmi = computeSllNmi();
		final double sll = sllNmi * _NmiToR;
		return sll;
	}

	public int computeNSearchLegs() {
		if (_myStyle == null) {
			final PsCsInfo psCsInfo = _sortie.getPsCsInfo();
			if (psCsInfo != null) {
				return psCsInfo.getNSearchLegs();
			}
			final VsInfo vsInfo = _sortie.getVsInfo();
			if (vsInfo != null) {
				return vsInfo.getNSearchLegs();
			}
			final SsInfo ssInfo = _sortie.getSsInfo();
			if (ssInfo != null) {
				return ssInfo.getNSearchLegs();
			}
			return _sortie.getDistinctInputLegs().size();
		}
		return _myStyle.computeNSearchLegs(_pv.getRawSearchKts(),
				_pv.getMinTsNmi());
	}

	public CcwGcas getTightExcCcwGcas() {
		if (onMars()) {
			return null;
		}
		return _tightExcCcwGcas;
	}

	public LatLng3[] getLooseLatLngArray(
			final SphericalTimedSegs.LoopType loopType) {
		if (loopType == SphericalTimedSegs.LoopType.SPEC) {
			return _looseSpecLatLngs;
		}
		if (loopType == SphericalTimedSegs.LoopType.TS) {
			return _looseTsLatLngs;
		}
		if (loopType == SphericalTimedSegs.LoopType.EXC) {
			return null;
		}
		return null;
	}

	public LatLng3[] getTightLatLngArray(
			final SphericalTimedSegs.LoopType loopType) {
		if (loopType == SphericalTimedSegs.LoopType.SPEC) {
			return null;
		}
		if (loopType == SphericalTimedSegs.LoopType.TS) {
			return _tightTsLatLngs;
		}
		if (loopType == SphericalTimedSegs.LoopType.EXC) {
			return _tightExcLatLngs;
		}
		return null;
	}

	public Loop3 getTightTsLoop() {
		return _tightTsLoop;
	}

	public Sortie getSortie() {
		return _sortie;
	}

	public PatternVariable getPv() {
		return _pv;
	}

	/**
	 * Just for convenience to make it easy to construct a PvValue to look up
	 * a Pv in a list or array of PvValues.
	 */
	public PvValue(final PatternVariable pv) {
		_pv = pv;
		_looseSpecLatLngs =
				_tightTsLatLngs = _looseTsLatLngs = _tightExcLatLngs = null;
		_tightTsLoop = null;
		_tightExcCcwGcas = null;
		_sortie = null;
		_myStyle = null;
		_objectTypeToViz2Pod = null;
	}

	public String getExcLoopString() {
		final int iPv = _pv.getGrandOrd();
		final CcwGcas tightExcCcwGcas = getTightExcCcwGcas();
		final Loop3 ccwLoop = tightExcCcwGcas.getCcwLoop();
		final String colorName =
				ColorUtils.getColorName(ColorUtils.ColorGrouping.BROWN, iPv);
		final String[] colorNames = new String[] { colorName };
		final int numberEdgeInc = iPv + 1;
		final String s =
				ccwLoop.getXmlDump(numberEdgeInc, colorNames, /* GcaFilter= */null);
		return s;
	}

	public String getSortieString() {
		String s = "";
		final List<Sortie.Leg> legs = _sortie.getDistinctInputLegs();
		final int nLegs = legs == null ? 0 : legs.size();
		for (int k = 0; k < nLegs; ++k) {
			final Sortie.Leg leg = legs.get(k);
			s += "\n" + leg.getString();
		}
		return s;
	}

	/** "ESP" means "End Search Point. */
	public LatLng3 getEsp() {
		final List<Sortie.Leg> legs = _sortie.getDistinctInputLegs();
		final int nLegs = legs.size();
		return legs.get(nLegs - 1).getLegLatLng1();
	}

	/** "CSP" means "Commence Search Point. */
	public LatLng3 getCsp() {
		final List<Sortie.Leg> legs = _sortie.getDistinctInputLegs();
		return legs.get(0).getLegLatLng0();
	}

	public long getCstRefSecs() {
		if (_sortie == null) {
			/** Can happen for a NFT-only PvValue. */
			return _myStyle.getCstRefSecs();
		}
		final List<Sortie.Leg> legs = _sortie.getDistinctInputLegs();
		return legs.get(0).getLegRefSecs0();
	}

	public long getEstRefSecs() {
		if (_sortie == null) {
			/** Can happen for a NFT-only PvValue. */
			return _myStyle.getCstRefSecs() + _myStyle.getSearchDurationSecs();
		}
		final List<Sortie.Leg> legs = _sortie.getDistinctInputLegs();
		final int nLegs = legs.size();
		return legs.get(nLegs - 1).getLegRefSecs1();
	}

	public int getSearchDurationSecs() {
		if (_myStyle == null) {
			final List<Sortie.Leg> legs = _sortie.getDistinctInputLegs();
			final int nLegs = legs.size();
			return (int) (legs.get(nLegs - 1).getLegRefSecs1() -
					legs.get(0).getLegRefSecs0());
		}
		return _myStyle.getSearchDurationSecs();
	}

	public boolean getFirstTurnRight() {
		if (_myStyle == null) {
			final PsCsInfo psCsInfo = _sortie.getPsCsInfo();
			if (psCsInfo != null) {
				return psCsInfo.getSgndTsNmi() > 0d;
			}
			final VsInfo vsInfo = _sortie.getVsInfo();
			if (vsInfo != null) {
				return vsInfo.getSgndTsNmi() > 0d;
			}
			final SsInfo ssInfo = _sortie.getSsInfo();
			if (ssInfo != null) {
				return ssInfo.getSgndTsNmi() > 0d;
			}
		}
		return _myStyle.getFirstTurnRight();
	}

	public double getEplNmi() {
		if (_myStyle == null) {
			final PsCsInfo psCsInfo = _sortie.getPsCsInfo();
			if (psCsInfo != null) {
				return psCsInfo.getEplNmi();
			}
			final VsInfo vsInfo = _sortie.getVsInfo();
			if (vsInfo != null) {
				return vsInfo.getEplNmi();
			}
			final SsInfo ssInfo = _sortie.getSsInfo();
			if (ssInfo != null) {
				return ssInfo.getEplNmi();
			}
			return _sortie.getTtlLegsNmi();
		}
		final double eplNmi =
				_myStyle.computeEplNmi(_pv.getRawSearchKts(), _pv.getMinTsNmi());
		return eplNmi;
	}

	public PvValue adjustCst(final long cstRefSecs) {
		if (!_pv.isActive()) {
			return null;
		}
		if (_pv.getUserFrozenPvValue() != null || onMars()) {
			return this;
		}
		final MyStyle myStyle = _myStyle.adjustCst(cstRefSecs);
		final PvValue pvValue = new PvValue(_pv, myStyle);
		return pvValue;
	}

	public boolean isTrackLine() {
		return _myStyle == null;
	}

	public double getTsSqNmi() {
		if (_myStyle != null) {
			return _myStyle.computeTsSqNmi(_pv.getRawSearchKts(),
					_pv.getMinTsNmi());
		}
		return _tightTsLoop.getSqNmi();
	}

	public PvValue convertToFull() {
		if (_myStyle == null) {
			return this;
		}
		if (_sortie == null) {
			return new PvValue(_pv, _myStyle);
		}
		return this;
	}

	public String getString() {
		if (_myStyle == null) {
			return String.format(
					"TrckLn Legs(#/TtlLength/FirstLegHdg):%d/%.3f/%.3f, " +
							"TS(In/Real):%.3f/%.3f, CreepHdg(In/Real):%.1f/%.1f.",
					_sortie.getDistinctGcas().length, _sortie.getTtlLegsNmi(),
					_sortie.getFirstLegHdg(), //
					_sortie.getTsNmiIn(), _sortie.getTsNmi(), //
					_sortie.getCreepHdgIn(), _sortie.getCreepHdg() //
			);
		}
		return String.format("%s",
				_myStyle.getString(_pv.getRawSearchKts(), _pv.getMinTsNmi()));
	}

	@Override
	public String toString() {
		return getString();
	}

}
