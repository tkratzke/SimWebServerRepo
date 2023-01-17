package com.skagit.sarops.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import com.skagit.sarops.computePFail.DetectValues;
import com.skagit.sarops.model.io.ModelReader;
import com.skagit.sarops.tracker.lrcSet.LateralRangeCurve;
import com.skagit.sarops.tracker.lrcSet.Logit;
import com.skagit.sarops.tracker.lrcSet.LrcSet;
import com.skagit.sarops.tracker.lrcSet.MBeta;
import com.skagit.sarops.util.patternUtils.LegInfo;
import com.skagit.sarops.util.patternUtils.LegInfo.LegType;
import com.skagit.sarops.util.patternUtils.PsCsInfo;
import com.skagit.sarops.util.patternUtils.SsInfo;
import com.skagit.sarops.util.patternUtils.VsInfo;
import com.skagit.util.Graphics;
import com.skagit.util.MathX;
import com.skagit.util.TimeUtilities;
import com.skagit.util.colorUtils.ColorUtils;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.MotionType;
import com.skagit.util.navigation.TangentCylinder;

public class Sortie implements Comparable<Sortie> {
	final private static double _LegJacketSweepWidthMultiplier = 3d;
	final private static double _NmiToR = MathX._NmiToR;

	final private List<Leg> _distinctInputLegs = new ArrayList<>();
	final private TreeMap<DetectValues.PFailType, List<Leg>> _pFailTypeToLegList;
	final private Map<Integer, LrcSet> _viz1LrcSets;
	final private Map<Integer, LrcSet> _viz2LrcSets;
	/** The following 2 are used to identify Completed Searches. */
	final private String _id;
	final private String _name;

	final private Model _model;
	final private MotionType _motionType;
	private long _sortieRefSecs0;
	private long _sortieRefSecs1;
	final private double _creepHdgIn;
	final private double _tsNmiIn;
	final private double _tsNmiForSingleLeg;
	private PsCsInfo _psCsInfo;
	private VsInfo _vsInfo;
	private SsInfo _ssInfo;

	public Sortie(final Model model, final String id, final String name, final MotionType motionType,
			final double creepHdgIn, final double tsNmiIn, final double tsNmiForSingleLeg) {
		_motionType = motionType;
		_model = model;
		_id = id;
		_name = name;
		_viz1LrcSets = new HashMap<>();
		_viz2LrcSets = new HashMap<>();
		_sortieRefSecs0 = ModelReader._UnsetTime;
		_sortieRefSecs1 = ModelReader._UnsetTime;
		_creepHdgIn = creepHdgIn;
		_tsNmiIn = tsNmiIn > 0d ? tsNmiIn : Double.NaN;
		_tsNmiForSingleLeg = tsNmiForSingleLeg > 0d ? tsNmiForSingleLeg : _tsNmiIn;
		_psCsInfo = null;
		_vsInfo = null;
		_ssInfo = null;
		_pFailTypeToLegList = new TreeMap<>();
	}

	public String getId() {
		return _id;
	}

	public String getName() {
		return _name;
	}

	public MotionType getMotionType() {
		return _motionType;
	}

	private boolean isAreaSortie() {
		return _motionType == null;
	}

	public void addLegIfNotVacuous(LatLng3 latLng0, final LatLng3 latLng1, long refSecs0, final long refSecs1) {
		if (isAreaSortie()) {
			return;
		}
		/**
		 * A leg must have a non-zero length. If there is already a leg, we ignore the
		 * startRefSecs and startLatLng, and just use the last existing refSecs and
		 * latLng.
		 */
		if (!latLng0.equals(latLng1)) {
			synchronized (_distinctInputLegs) {
				final int nOldLegs = _distinctInputLegs.size();
				if (nOldLegs == 0) {
					if (refSecs1 > refSecs0) {
						_distinctInputLegs
								.add(new Leg(latLng0, latLng1, refSecs0, refSecs1, _distinctInputLegs.size()));
					}
				} else {
					final Leg oldLeg = _distinctInputLegs.get(nOldLegs - 1);
					latLng0 = oldLeg._gca.getLatLng1();
					refSecs0 = oldLeg._legRefSecs1;
					if (refSecs1 > refSecs0) {
						_distinctInputLegs
								.add(new Leg(latLng0, latLng1, refSecs0, refSecs1, _distinctInputLegs.size()));
					}
				}
			}
		}
	}

	public List<Leg> getDistinctInputLegs() {
		if (isAreaSortie()) {
			return null;
		}
		return Collections.unmodifiableList(_distinctInputLegs);
	}

	public Map<Integer, LrcSet> getViz1LrcSets() {
		if (isAreaSortie()) {
			return null;
		}
		return _viz1LrcSets;
	}

	public Map<Integer, LrcSet> getViz2LrcSets() {
		if (isAreaSortie()) {
			return null;
		}
		return _viz2LrcSets;
	}

	public void addLrcSet(final int objectTypeId, final LrcSet lrcSet, final boolean viz2) {
		if (isAreaSortie()) {
			return;
		}
		if (lrcSet != null) {
			_viz1LrcSets.put(objectTypeId, lrcSet);
			if (viz2) {
				_viz2LrcSets.put(objectTypeId, lrcSet);
			}
		}
	}

	public LrcSet getLrcSet(final int objectTypeId, final boolean viz2) {
		if (isAreaSortie()) {
			return null;
		}
		return (viz2 ? _viz2LrcSets : _viz1LrcSets).get(objectTypeId);
	}

	public class Leg implements Comparable<Leg> {
		private LegInfo.LegType _legType;
		final private long _legRefSecs0;
		final private long _legRefSecs1;
		final private TangentCylinder _tc;
		final private GreatCircleArc _gca;
		final private double _lengthD;
		final private int _edgeNumber;
		final private Map<Integer, Extent> _twExtents;

		private Leg(final LatLng3 latLng0, final LatLng3 latLng1, final long refSecs0, final long refSecs1,
				final int edgeNumber) {
			_legType = null;
			_legRefSecs0 = refSecs0;
			_legRefSecs1 = refSecs1;
			_tc = TangentCylinder.getTangentCylinder(new LatLng3[] { latLng0, latLng1 }, null);
			/** Build _flatLatLng0 and _flatLatLng1 to put into _gca. */
			final TangentCylinder.FlatLatLng flatLatLng0 = _tc.convertToMyFlatLatLng(latLng0);
			final TangentCylinder.FlatLatLng flatLatLng1 = _tc.convertToMyFlatLatLng(latLng1);
			_gca = GreatCircleArc.CreateGca(flatLatLng0, flatLatLng1);
			_lengthD = Math.toDegrees(_gca.getHaversine());
			_twExtents = new HashMap<>();
			_edgeNumber = edgeNumber;
		}

		public TangentCylinder getTangentCylinder() {
			return _tc;
		}

		public boolean closeEnoughToCompute(final int objectType, final LatLng3 latLng0, final LatLng3 latLng1) {
			if (!_viz1LrcSets.containsKey(objectType)) {
				return false;
			}
			final Extent twExtent = _twExtents.get(objectType);
			final LatLng3 twLatLng0 = _gca.getTwLatLng(latLng0);
			final LatLng3 twLatLng1 = _gca.getTwLatLng(latLng1);
			final GreatCircleArc twGca = GreatCircleArc.CreateGca(twLatLng0, twLatLng1);
			final Extent gcaTwExtent = twGca.createExtent();
			final boolean b = gcaTwExtent.overlaps(twExtent);
			return b;
		}

		public Sortie getSortie() {
			return Sortie.this;
		}

		public long getLegRefSecs0() {
			return _legRefSecs0;
		}

		public LatLng3 getLegLatLng0() {
			return _gca.getLatLng0();
		}

		public long getLegRefSecs1() {
			return _legRefSecs1;
		}

		public LatLng3 getLegLatLng1() {
			return _gca.getLatLng1();
		}

		public int deepCompareTo(final Leg leg) {
			int compareValue = (int) (_legRefSecs0 - leg._legRefSecs0);
			if (compareValue != 0) {
				return compareValue;
			}
			compareValue = (int) (_legRefSecs1 - leg._legRefSecs1);
			if (compareValue != 0) {
				return compareValue;
			}
			final LatLng3 myLatLng0 = getLegLatLng0();
			final LatLng3 legLatLng0 = leg.getLegLatLng0();
			compareValue = myLatLng0.compareLatLng(legLatLng0);
			if (compareValue != 0) {
				return compareValue;
			}
			final LatLng3 myLatLng1 = getLegLatLng1();
			final LatLng3 legLatLng1 = leg.getLegLatLng1();
			compareValue = myLatLng1.compareLatLng(legLatLng1);
			if (compareValue != 0) {
				return compareValue;
			}
			return 0;
		}

		@Override
		public int compareTo(final Leg otherLeg) {
			final int sortieDiff = getSortie().getId().compareTo(otherLeg.getSortie().getId());
			if (sortieDiff == 0) {
				if (_legRefSecs0 < otherLeg._legRefSecs0) {
					return -1;
				} else if (_legRefSecs0 > otherLeg._legRefSecs0) {
					return 1;
				} else {
					return 0;
				}
			}
			return sortieDiff;
		}

		public String getXmlString() {
			final LatLng3 latLng0 = getLegLatLng0();
			final LatLng3 latLng1 = getLegLatLng1();
			final double lat0 = latLng0.getLat();
			final double lng0 = latLng0.getLng();
			final double lat1 = latLng1.getLat();
			final double lng1 = latLng1.getLng();
			final GreatCircleArc gca = getGca();
			final double haversine = gca.getHaversine();
			final double initialHdg = gca.getRawInitialHdg();
			final String colorName = ColorUtils.getForegroundColorName(_edgeNumber);
			String s = String.format(
					"<%s lat0=\"%.7f\" lng0=\"%.7f\""
							+ " lat1=\"%.7f\" lng1=\"%.7f\" nmi=\"%.7f\" hdg=\"%.7f\" number=\"%d\" color=\"%s\"",
					Graphics.GraphicsType.LL_EDGE.name(), lat0, lng0, lat1, lng1, haversine / _NmiToR, initialHdg,
					_edgeNumber, colorName);

			final String startTimeString = TimeUtilities.formatTime(_legRefSecs0, true, true);
			final String stopTimeString = TimeUtilities.formatTime(_legRefSecs1, true, true);
			s += String.format(" StartTime=\"%s\" EndTime=\"%s\"", startTimeString, stopTimeString);
			s += " />";
			return s;
		}

		public String getString() {
			final GreatCircleArc gca = getGca();
			final LatLng3 latLng0 = gca.getLatLng0();
			final LatLng3 latLng1 = gca.getLatLng1();
			String s = String.format("%s→%s", latLng0.getString(), latLng1.getString());
			final double haversine = gca.getHaversine();
			final double initialHdg = gca.getRawInitialHdg();
			s += String.format(" nmi/hdg/#[%.7f/%.7f/%d]", haversine / _NmiToR, initialHdg, _edgeNumber);
			final String startTimeString = TimeUtilities.formatTime(_legRefSecs0, /* includeSecs= */true,
					/* trimAtZ= */false);
			final String stopTimeString = TimeUtilities.formatTime(_legRefSecs1, /* includeSecs= */true,
					/* trimAtZ= */false);
			s += String.format(" Start/End[%s/%s]", startTimeString, stopTimeString);
			return s;
		}

		@Override
		public String toString() {
			return getString();
		}

		public GreatCircleArc getGca() {
			return _gca;
		}

		public LegInfo.LegType getLegType() {
			return _legType;
		}

		public Object getEdgeNumber() {
			return _edgeNumber;
		}
	}

	/**
	 * Fills in sortie's start and end, as well as its legs' twisted extents and its
	 * legs' LegTypes.
	 */
	public void fillInSortieDataFromDistinctInputLegs() {
		if (isAreaSortie()) {
			return;
		}
		if (_sortieRefSecs0 == ModelReader._UnsetTime) {
			synchronized (this) {
				if (_sortieRefSecs0 == ModelReader._UnsetTime) {
					/** Start by filling in _startRefSecsX and _stopInRefSecsX. */
					_sortieRefSecs0 = Long.MAX_VALUE;
					_sortieRefSecs1 = Long.MIN_VALUE;
					for (final Leg leg : _distinctInputLegs) {
						_sortieRefSecs0 = Math.min(_sortieRefSecs0, leg.getLegRefSecs0());
						_sortieRefSecs1 = Math.max(_sortieRefSecs1, leg.getLegRefSecs1());
					}
					/** Fill in the legs' twisted extents. */
					final int nLegs = _distinctInputLegs.size();
					if (nLegs == 0) {
						return;
					}
					final GreatCircleArc[] gcas = new GreatCircleArc[nLegs];
					final LatLng3[] latLngs = new LatLng3[nLegs + 1];
					for (int k = 0; k < nLegs; ++k) {
						final Leg leg = _distinctInputLegs.get(k);
						latLngs[k] = leg.getLegLatLng0();
						latLngs[k + 1] = leg.getLegLatLng1();
						gcas[k] = _distinctInputLegs.get(k)._gca;
					}
					/** Find out if this is sortie is a LP, SS, or VS. */
					_psCsInfo = new PsCsInfo(gcas, _creepHdgIn, _tsNmiIn, _tsNmiForSingleLeg);
					if (_psCsInfo.isValid()) {
						_vsInfo = null;
						_ssInfo = null;
					} else {
						_psCsInfo = null;
						_vsInfo = new VsInfo(gcas);
						if (_vsInfo.isValid()) {
							_ssInfo = null;
						} else {
							_vsInfo = null;
							_ssInfo = new SsInfo(gcas);
							if (!_ssInfo.isValid()) {
								_ssInfo = null;
							}
						}
					}
					/** This sortie's legs will have extents for each LrcSet. */
					for (final Leg leg : _distinctInputLegs) {
						leg._twExtents.clear();
						for (final Map.Entry<Integer, LrcSet> entry : _viz1LrcSets.entrySet()) {
							final int objectTypeId = entry.getKey();
							final LrcSet lrcSet = entry.getValue();
							/**
							 * We will look at particles that come within _LegJacketSweepWidthMultiplier
							 * times the sweep width.
							 */
							final double bufferNmi = lrcSet.getSweepWidth() * _LegJacketSweepWidthMultiplier;
							final double bufferR = bufferNmi * _NmiToR;
							final double bufferD = Math.toDegrees(bufferR);
							final double w = -bufferD;
							final double e = leg._lengthD + bufferD;
							final double s = -bufferD;
							final double n = bufferD;
							final Extent twLegExtent = new Extent(w, s, e, n);
							/**
							 * We use _twExtents with leg's gc twisted coordinate system. In that system,
							 * the gc lies on the equator so the twExtent given by the above is correct.
							 * When we use a twExtent with a candidate LatLng3, we will convert it to gca's
							 * twisted coordinates. If it is at all close to leg's gc, the poiont's twisted
							 * coordinates' latitude will be small.
							 */
							leg._twExtents.put(objectTypeId, twLegExtent);
						}
						if (_psCsInfo != null) {
							leg._legType = _psCsInfo.getGcaType(leg._gca);
						} else {
							leg._legType = LegType.GENERIC;
						}
					}
				}
			}
		}
		for (final DetectValues.PFailType pFailType : DetectValues.PFailType.values()) {
			_pFailTypeToLegList.put(pFailType, computeLegList(pFailType));
		}
	}

	public Extent getBoundingExtent() {
		Extent extent = Extent.getUnsetExtent();
		for (final Leg leg : _distinctInputLegs) {
			extent = extent.buildExtension(leg._gca.createExtent());
		}
		return extent;
	}

	public PsCsInfo getPsCsInfo() {
		return _psCsInfo;
	}

	public VsInfo getVsInfo() {
		return _vsInfo;
	}

	public SsInfo getSsInfo() {
		return _ssInfo;
	}

	public int deepCompareTo(final Sortie comparedSortie) {
		if (comparedSortie == null) {
			return 1;
		}
		int compareValue = _distinctInputLegs.size() - comparedSortie._distinctInputLegs.size();
		if (compareValue != 0) {
			return compareValue;
		}
		final Iterator<Leg> legIterator = _distinctInputLegs.iterator();
		final Iterator<Leg> comparedLegIterator = comparedSortie._distinctInputLegs.iterator();
		while (legIterator.hasNext() && comparedLegIterator.hasNext()) {
			final Leg leg = legIterator.next();
			final Leg comparedLeg = comparedLegIterator.next();
			compareValue = leg.deepCompareTo(comparedLeg);
			if (compareValue != 0) {
				return compareValue;
			}
		}
		compareValue = _viz1LrcSets.size() - comparedSortie._viz1LrcSets.size();
		if (compareValue != 0) {
			return compareValue;
		}
		final Iterator<Integer> lrc1It = _viz1LrcSets.keySet().iterator();
		final Iterator<Integer> lrc2It = comparedSortie._viz1LrcSets.keySet().iterator();
		while (lrc1It.hasNext() && lrc2It.hasNext()) {
			final int objectTypeId1 = lrc1It.next();
			final int objectTypeId2 = lrc2It.next();
			if (objectTypeId1 != objectTypeId2) {
				return objectTypeId1 < objectTypeId2 ? -1 : 1;
			}
			final int objectTypeId = objectTypeId1;
			final boolean isViz2_1 = _viz2LrcSets.containsKey(objectTypeId);
			final boolean isViz2_2 = comparedSortie._viz2LrcSets.containsKey(objectTypeId);
			if (isViz2_1 != isViz2_2) {
				return isViz2_1 ? 1 : -1;
			}
			final LrcSet lrcSet1 = _viz1LrcSets.get(objectTypeId);
			final LrcSet lrcSet2 = comparedSortie._viz1LrcSets.get(objectTypeId);
			compareValue = lrcSet1.compareTo(lrcSet2);
			if (compareValue != 0) {
				return compareValue;
			}
		}
		if (_motionType != comparedSortie._motionType) {
			compareValue = _motionType.toString().compareTo(comparedSortie._motionType.toString());
			if (compareValue != 0) {
				return compareValue;
			}
		}
		return 0;
	}

	public long getStartRefSecs() {
		return _sortieRefSecs0;
	}

	public long getStopRefSecs() {
		return _sortieRefSecs1;
	}

	@Override
	public int compareTo(final Sortie other) {
		return _id.compareTo(other._id);
	}

	public double getTtlLegsNmi() {
		double ttlLegsR = 0d;
		for (final Leg leg : _distinctInputLegs) {
			ttlLegsR += leg._gca.getHaversine();
		}
		final double ttlLegsNmi = ttlLegsR / _NmiToR;
		return ttlLegsNmi;
	}

	public void dumpToSortiesWorkbook() {
		if (_model == null) {
			return;
		}
		final Workbook sortiesWorkbook = _model.getSortiesWorkbook();
		if (sortiesWorkbook == null) {
			return;
		}
		final Sheet sheet1 = sortiesWorkbook.createSheet(String.format("Sortie-%s WyPts", _id));
		int iRow = 0;
		int iCol = 0;
		Row headerRow = sheet1.createRow(iRow++);
		headerRow.createCell(iCol++).setCellValue("Leg Number");
		headerRow.createCell(iCol++).setCellValue("Start Time");
		headerRow.createCell(iCol++).setCellValue("Start Lat");
		headerRow.createCell(iCol++).setCellValue("Start Lng");
		headerRow.createCell(iCol++).setCellValue("End Time");
		headerRow.createCell(iCol++).setCellValue("End Lat");
		headerRow.createCell(iCol++).setCellValue("End Lng");
		headerRow.createCell(iCol++).setCellValue("Nmi");
		final int nCols1 = iCol;
		int legNumber = 0;
		final boolean includeSeconds = true;
		for (final Leg leg : _distinctInputLegs) {
			final Row legRow = sheet1.createRow(iRow++);
			iCol = 0;
			long timeSeconds = leg._legRefSecs0;
			LatLng3 latLng = leg.getLegLatLng0();
			String timeString = TimeUtilities.formatTime(timeSeconds, includeSeconds);
			double lat = latLng.getLat();
			double lng = latLng.getLng();
			legRow.createCell(iCol++).setCellValue(legNumber++);
			legRow.createCell(iCol++).setCellValue(timeString);
			legRow.createCell(iCol++).setCellValue(lat);
			legRow.createCell(iCol++).setCellValue(lng);
			timeSeconds = leg._legRefSecs1;
			latLng = leg.getLegLatLng1();
			timeString = TimeUtilities.formatTime(timeSeconds, includeSeconds);
			lat = latLng.getLat();
			lng = latLng.getLng();
			legRow.createCell(iCol++).setCellValue(timeString);
			legRow.createCell(iCol++).setCellValue(lat);
			legRow.createCell(iCol++).setCellValue(lng);
			final double nmi = leg._gca.getHaversine() / _NmiToR;
			legRow.createCell(iCol++).setCellValue(nmi);
		}
		for (iCol = 0; iCol < nCols1; ++iCol) {
			sheet1.autoSizeColumn(iCol);
		}
		final Sheet sheet2 = sortiesWorkbook.createSheet(String.format("Sortie-%s Params", _id));
		iRow = 0;
		headerRow = sheet2.createRow(iRow++);
		iCol = 0;
		headerRow.createCell(iCol++).setCellValue("Object Id");
		headerRow.createCell(iCol++).setCellValue("LtRt or UpDn");
		headerRow.createCell(iCol++).setCellValue("Parameter 1");
		headerRow.createCell(iCol++).setCellValue("Parameter 2");
		headerRow.createCell(iCol++).setCellValue("Max Range");
		headerRow.createCell(iCol++).setCellValue("Sweep Width");
		final int nCols2 = iCol;
		for (final Map.Entry<Integer, LrcSet> entry : _viz1LrcSets.entrySet()) {
			final Integer objectType = entry.getKey();
			final LrcSet lrcSet = entry.getValue();
			final int nLrcs = lrcSet.getNLrcs();
			for (int k = 0; k < nLrcs; ++k) {
				final LateralRangeCurve lrc = lrcSet.getLrc(k);
				final String ltRtOrUpDnString = lrc.isLtRt() ? "LtRt" : "UpDn";
				final double maxRange = lrc.getMaxRange();
				final double sweepWidth = lrc.getSweepWidth();
				double param1 = 0d;
				double param2 = 0d;
				try {
					param1 = param2 = 0d;
				} catch (final ClassCastException e1) {
					try {
						final Logit logit = (Logit) lrc;
						param1 = logit.getAp0();
						param2 = logit.getA1();
					} catch (final ClassCastException e2) {
						try {
							final MBeta mBeta = (MBeta) lrc;
							param1 = mBeta.getBetaLtOrUp();
							param2 = mBeta.getBetaRtOrDn();
						} catch (final ClassCastException e3) {
						}
					}
					final Row dataRow = sheet2.createRow(iRow++);
					iCol = 0;
					dataRow.createCell(iCol++).setCellValue(objectType);
					dataRow.createCell(iCol++).setCellValue(ltRtOrUpDnString);
					dataRow.createCell(iCol++).setCellValue(param1);
					dataRow.createCell(iCol++).setCellValue(param2);
					dataRow.createCell(iCol++).setCellValue(maxRange);
					dataRow.createCell(iCol++).setCellValue(sweepWidth);
				}
			}
		}
		for (iCol = 0; iCol < nCols2; ++iCol) {
			sheet2.autoSizeColumn(iCol);
		}
	}

	public String getMassiveString() {
		String s = "";
		s += "\n";
		s += "\tLeg Number";
		s += "\tStart Time";
		s += "\tStart Lat";
		s += "\tStart Lng";
		s += "\tEnd Time";
		s += "\t\tEnd Lat";
		s += "\t\tEnd Lng";
		s += "\t\tNmi";
		int legNumber = 0;
		final boolean includeSeconds = true;
		for (final Leg leg : _distinctInputLegs) {
			s += "\n";
			long timeSeconds = leg._legRefSecs0;
			LatLng3 latLng = leg.getLegLatLng0();
			String timeString = TimeUtilities.formatTime(timeSeconds, includeSeconds);
			double lat = latLng.getLat();
			double lng = latLng.getLng();
			s += "\t" + legNumber;
			++legNumber;
			s += "\t" + timeString;
			s += "\t" + lat;
			s += "\t" + lng;
			timeSeconds = leg._legRefSecs1;
			latLng = leg.getLegLatLng1();
			timeString = TimeUtilities.formatTime(timeSeconds, includeSeconds);
			lat = latLng.getLat();
			lng = latLng.getLng();
			s += "\t" + timeString;
			s += "\t" + lat;
			s += "\t" + lng;
			final double nmi = leg._gca.getHaversine() / _NmiToR;
			s += "\t" + nmi;
		}
		s += "\n";
		s += "\n\tObject Id";
		s += "\tParameter 1";
		s += "\tParameter 2";
		s += "\tMax Detect Range";
		s += "\tSweep Width";
		for (final Map.Entry<Integer, LrcSet> entry : _viz1LrcSets.entrySet()) {
			final Integer objectType = entry.getKey();
			final LrcSet lrcSet = entry.getValue();
			final int nLrcs = lrcSet.getNLrcs();
			for (int k = 0; k < nLrcs; ++k) {
				final LateralRangeCurve lrc = lrcSet.getLrc(k);
				final String ltRtOrUpDnString = lrc.isLtRt() ? "LtRt" : "UpDn";
				final double maxRange = lrc.getMaxRange();
				final double sweepWidth = lrc.getSweepWidth();
				double param1 = 0d;
				double param2 = 0d;
				try {
					param1 = param2 = 0d;
				} catch (final ClassCastException e1) {
					try {
						final Logit logit = (Logit) lrc;
						param1 = logit.getAp0();
						param2 = logit.getA1();
					} catch (final ClassCastException e2) {
						try {
							final MBeta mBeta = (MBeta) lrc;
							param1 = mBeta.getBetaLtOrUp();
							param2 = mBeta.getBetaRtOrDn();
						} catch (final ClassCastException e3) {
						}
					}
					s += "\n";
					s += "\t" + objectType;
					s += "\t" + ltRtOrUpDnString;
					s += "\t" + param1;
					s += "\t" + param2;
					s += "\t" + maxRange;
					s += "\t" + sweepWidth;
				}
			}
		}
		return s;
	}

	public String getString() {
		final int nLegs = _distinctInputLegs == null ? 0 : _distinctInputLegs.size();
		final String dtg0;
		final String latLng0String;
		final String dtgN;
		final String latLngNString;
		if (nLegs == 0) {
			dtg0 = latLng0String = dtgN = latLngNString = "";
		} else {
			final Leg leg0 = _distinctInputLegs.get(0);
			latLng0String = leg0._gca.getLatLng0().getString();
			final long refSecs0 = leg0.getLegRefSecs0();
			dtg0 = TimeUtilities.formatTime(refSecs0, /* includeSecs= */false);
			final Leg legN = _distinctInputLegs.get(nLegs - 1);
			latLngNString = legN._gca.getLatLng1().getString();
			final long refSecsN = legN.getLegRefSecs1();
			dtgN = TimeUtilities.formatTime(refSecsN, /* includeSecs= */false);
		}
		final String s = String.format("\nStart[%s/%s]→(%d legs)[%s/%s]", //
				dtg0, latLng0String, nLegs, dtgN, latLngNString);
		return s;
	}

	@Override
	public String toString() {
		return getString();
	}

	public GreatCircleArc[] getDistinctGcas() {
		final int nDistinctLegs = _distinctInputLegs.size();
		final GreatCircleArc[] gcas = new GreatCircleArc[nDistinctLegs];
		for (int k = 0; k < nDistinctLegs; ++k) {
			gcas[k] = _distinctInputLegs.get(k)._gca;
		}
		return gcas;
	}

	public double getCreepHdgIn() {
		return _creepHdgIn;
	}

	public double getTsNmiIn() {
		return _tsNmiIn;
	}

	public void copyLegs(final Sortie sortie) {
		for (final Leg leg : sortie._distinctInputLegs) {
			final LatLng3 startLatLng = leg.getLegLatLng0();
			final LatLng3 stopLatLng = leg.getLegLatLng1();
			final long startRefSecs = leg.getLegRefSecs0();
			final long stopRefSecs = leg.getLegRefSecs1();
			addLegIfNotVacuous(startLatLng, stopLatLng, startRefSecs, stopRefSecs);
		}
	}

	public LatLng3 getStart() {
		return _distinctInputLegs.get(0).getLegLatLng0();
	}

	public LatLng3 getEnd() {
		return _distinctInputLegs.get(_distinctInputLegs.size() - 1).getLegLatLng1();
	}

	public void setViz1LrcSets(final HashMap<Integer, LrcSet> viz1LrcSets) {
		_viz1LrcSets.clear();
		if (viz1LrcSets != null) {
			_viz1LrcSets.putAll(viz1LrcSets);
		}
	}

	public void setViz2LrcSets(final HashMap<Integer, LrcSet> viz2LrcSets) {
		_viz2LrcSets.clear();
		if (viz2LrcSets != null) {
			_viz2LrcSets.putAll(viz2LrcSets);
		}
	}

	public double getTsNmi() {
		if (_psCsInfo != null) {
			return Math.abs(_psCsInfo.getSgndTsNmi());
		}
		if (_vsInfo != null) {
			return Math.abs(_vsInfo.getSgndTsNmi());
		}
		return _tsNmiIn;
	}

	public double getCreepHdg() {
		if (_psCsInfo != null) {
			return _psCsInfo.getCreepHdg();
		}
		return Double.NaN;
	}

	public double getFirstLegHdg() {
		if (_psCsInfo != null) {
			return _psCsInfo.getFirstLegHdg();
		}
		if (_vsInfo != null) {
			return _vsInfo.getFirstLegHdg();
		}
		if (_ssInfo != null) {
			return _ssInfo.getFirstLegHdg();
		}
		final GreatCircleArc[] distinctGcas = getDistinctGcas();
		if (distinctGcas == null || distinctGcas.length == 0) {
			return Double.NaN;
		}
		final GreatCircleArc gca0 = getDistinctGcas()[0];
		return gca0.getRoundedInitialHdg();
	}

	public LatLng3[][] getSegs() {
		final int nGcas = _distinctInputLegs.size();
		final LatLng3[][] segs = new LatLng3[nGcas][];
		for (int k = 0; k < nGcas; ++k) {
			final GreatCircleArc gca = _distinctInputLegs.get(k)._gca;
			segs[k] = new LatLng3[] { gca.getLatLng0(), gca.getLatLng1() };
		}
		return segs;
	}

	private List<Leg> computeLegList(final DetectValues.PFailType pFailType) {
		final List<Sortie.Leg> legList0 = _distinctInputLegs == null ? new ArrayList<>(0) : _distinctInputLegs;
		final int nDistinctLegs = _distinctInputLegs == null ? 0 : _distinctInputLegs.size();
		/**
		 * If this is a ladder pattern and we are not processing all intervals, we will
		 * not process short non-search-legs. We define "short" as less than 10% of the
		 * longest leg.
		 */
		if ((pFailType == DetectValues.PFailType.AIFT) || (_psCsInfo == null)) {
			/**
			 * Not much we can do. We don't know enough about the structure. We only trim
			 * legs for PsCs; not for Vs or Ss, and certainly not for structure-free
			 * patterns.
			 */
			return _distinctInputLegs;
		}

		/** _psCsInfo != null. Find the longest search leg. */
		double maxLegNmi = 0d;
		for (int k0 = 0; k0 < nDistinctLegs; ++k0) {
			final Sortie.Leg leg = legList0.get(k0);
			final double legNmi = leg.getGca().getHaversine() / _NmiToR;
			maxLegNmi = Math.max(maxLegNmi, legNmi);
		}
		final double legThresholdNmiForNonSearchLeg = maxLegNmi * 0.1;
		final ArrayList<Sortie.Leg> goodLegs = new ArrayList<>();
		for (int k0 = 0; k0 < nDistinctLegs; ++k0) {
			final Sortie.Leg leg = _distinctInputLegs.get(k0);
			final double legNmi = leg.getGca().getHaversine() / _NmiToR;
			final LegInfo.LegType legType = leg.getLegType();
			/** This leg is good if it is a search leg or if it is big. */
			if (legType.isSearchLeg() || legNmi >= legThresholdNmiForNonSearchLeg) {
				goodLegs.add(leg);
			}
		}
		return goodLegs;
	}

	public List<Leg> getLegList(final DetectValues.PFailType pFailType) {
		return _pFailTypeToLegList.get(pFailType);
	}
}
