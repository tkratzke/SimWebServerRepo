package com.skagit.sarops.planner.plannerModel;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import org.w3c.dom.Element;

import com.skagit.sarops.model.Sortie;
import com.skagit.sarops.model.io.ModelReader;
import com.skagit.sarops.model.io.ModelReader.ReaderException;
import com.skagit.sarops.model.io.ModelReader.StringPlus;
import com.skagit.sarops.planner.plannerModel.pvSeq.PvSeq;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.simCaseManager.MainRunner;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.CoverageToPodCurve;
import com.skagit.sarops.tracker.lrcSet.LateralRangeCurve;
import com.skagit.sarops.tracker.lrcSet.LrcSet;
import com.skagit.sarops.util.patternUtils.PatternUtilStatics;
import com.skagit.sarops.util.patternUtils.SphericalTimedSegs;
import com.skagit.util.ElementIterator;
import com.skagit.util.TimeUtilities;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.MotionType;

public class PatternVariable {
	final public static int _MinSearchDurationSecs = 600;

	/** Backpointer. */
	final private PlannerModel _plannerModel;

	/** Identifiers. */
	final private String _id;
	final private String _name;

	/**
	 * _fraction is used to determine where to place an onMars value. It
	 * references the fraction of the way from 0 (North) to 360 (cw).
	 */
	final public double _fraction;

	/** _grandOrd will be reset after everything is read in. */
	private int _grandOrd;

	/** Data. */
	final private PatternKind _patternKind;
	final private MotionType _motionType;
	final private PvType _pvType;
	final private double _rawSearchKts;

	/** "viz1" means "can see." "viz2" means "consider when optimizing." */
	final private HashMap<Integer, LrcSet> _viz1LrcSets;
	final private HashMap<Integer, LrcSet> _viz2LrcSets;

	final private double _excBufferNmi;

	/** Just if non-TrackLine. */
	final private double _minTsNmi;

	/** Just if StandAlone. */
	final private long _pvCstRefSecs;
	final private int _pvRawSearchDurationSecs;

	/** Just if non-StandAlone. */
	final private double _transitKts;
	final private PvSeq _pvSeq;
	/** _ordWithinPvSeq will be reset after everything is read in. */
	private int _ordWithinPvSeq;

	final private PvValue _initialPvValue;

	private PvValue _userFrozenPvValue;
	private boolean _isActive;

	final public static Comparator<PatternVariable> _ByGrandOrd =
			new Comparator<>() {

				@Override
				public int compare(final PatternVariable pv0,
						final PatternVariable pv1) {
					final int grandOrd0 = pv0._grandOrd;
					final int grandOrd1 = pv1._grandOrd;
					return grandOrd0 < grandOrd1 ? -1 :
							(grandOrd0 > grandOrd1 ? 1 : 0);
				}
			};

	final public static Comparator<PatternVariable> _ByOrdWithinPvSeq =
			new Comparator<>() {

				@Override
				public int compare(final PatternVariable pv0,
						final PatternVariable pv1) {
					final int ordWithinPvSeq0 = pv0._ordWithinPvSeq;
					final int ordWithinPvSeq1 = pv1._ordWithinPvSeq;
					if (ordWithinPvSeq0 != ordWithinPvSeq1) {
						return ordWithinPvSeq0 < ordWithinPvSeq1 ? -1 : 1;
					}
					return _ByGrandOrd.compare(pv0, pv1);
				}
			};

	/** Main ctor. */
	public PatternVariable(final PlannerModel plannerModel,
			final Map<String[], Integer> nameIdMap, final PvSeq pvSeq,
			final Element pvElt, final TreeSet<StringPlus> stringPluses) {
		_isActive = true;
		_grandOrd = -1;
		_plannerModel = plannerModel;
		final SimCaseManager.SimCase simCase = _plannerModel.getSimCase();

		/** Identifiers. */
		_name = pvElt.getAttribute("name");
		_id = pvElt.getAttribute("id");
		_fraction = ((double) nameIdMap.get(new String[] { _name, _id })) /
				nameIdMap.size();

		/** Set _patternKind. If there is a TrackLine, _patternKind = null. */
		if (ElementIterator.getChildIgnoreCase(pvElt, "TRACKLINE") != null) {
			_patternKind = null;
		} else if (ElementIterator.getChildIgnoreCase(pvElt,
				"PATTERN") != null) {
			_patternKind = null;
		} else {
			final String patternKindString = pvElt.getAttribute("patternKind");
			if (patternKindString == null || patternKindString.length() == 0) {
				_patternKind = PatternKind.PSCS;
			} else {
				_patternKind = PatternKind.getPatternKind(patternKindString);
				if (_patternKind == null) {
					MainRunner.HandleFatal(simCase,
							new RuntimeException("Cannot determine PatternKind"));
				}
			}
		}

		/**
		 * Determine if StandAlone. If pvSeq is null, it's StandAlone. If it's
		 * missing transits data, it's StandAlone.
		 */
		if (pvSeq != null) {
			int ordWithinPvSeq = -1;
			double transitKts = Double.NaN;
			try {
				ordWithinPvSeq = ModelReader.getInt(simCase, pvElt, "ordinal", "",
						/* default= */-1, stringPluses);
				transitKts = ModelReader.getDouble(simCase, pvElt, "transitSpeed",
						" kts", Double.NaN, stringPluses);
			} catch (final ReaderException e) {
			}
			if (ordWithinPvSeq < 0 || !(transitKts > 0d)) {
				/** It's a standAlone now. */
				_ordWithinPvSeq = -1;
				_transitKts = Double.NaN;
			} else {
				_ordWithinPvSeq = ordWithinPvSeq;
				_transitKts = transitKts;
			}
		} else {
			_ordWithinPvSeq = -1;
			_transitKts = Double.NaN;
		}
		_pvSeq = (_ordWithinPvSeq > 0 && _transitKts > 0d) ? pvSeq : null;

		/** MotionType, PvType, and _excBuffer. */
		final String motionTypeString = ModelReader.getString(simCase, pvElt,
				"motion_type", "GC", stringPluses);
		_motionType = MotionType.get(motionTypeString);
		final String xmlPvTypeString = ModelReader.getString(simCase, pvElt,
				"type", /* default= */null, stringPluses);
		_pvType = PvType.getPvType(xmlPvTypeString);
		double excBufferNmi = Double.NaN;
		try {
			excBufferNmi = ModelReader.getDouble(simCase, pvElt,
					"separationBuffer", "NM", 0d, stringPluses);
		} catch (final ReaderException e) {
		}
		if (!(excBufferNmi > 0d)) {
			_excBufferNmi = PatternUtilStatics._TsInc;
		} else {
			_excBufferNmi = excBufferNmi;
		}

		/** Convenience values. */
		final boolean trackLine = _patternKind == null;
		final boolean standAlone = _pvSeq == null;

		/** If not trackLine, we need rawSearchKts and minTs. */
		if (!trackLine) {
			double rawSearchKts = Double.NaN;
			double minTsNmi = Double.NaN;
			try {
				rawSearchKts = ModelReader.getDouble(simCase, pvElt, "speed",
						" kts", Double.NaN, stringPluses);
				if (!(rawSearchKts > 0d)) {
					rawSearchKts = ModelReader.getDouble(simCase, pvElt,
							"searchSpeed", " kts", Double.NaN, stringPluses);
				}
				minTsNmi =
						ModelReader.getDouble(simCase, pvElt, "minimumTrackSpacing",
								"NM", Double.NaN, /* stringPluses= */null);
			} catch (final ReaderException e) {
			}
			if (!(rawSearchKts > 0d) || !(minTsNmi > 0d)) {
				MainRunner.HandleFatal(simCase,
						new RuntimeException("Require searchSpeed and minTs."));
			}
			_rawSearchKts = rawSearchKts;
			_minTsNmi = minTsNmi > 0d ? minTsNmi : PatternUtilStatics._TsInc;
		} else {
			_rawSearchKts = _minTsNmi = Double.NaN;
		}

		/** Set the LrcSets and overlapExceptions. */
		_viz1LrcSets = new HashMap<>();
		_viz2LrcSets = new HashMap<>();
		final ElementIterator childIt1 = new ElementIterator(pvElt);
		while (childIt1.hasNextElement()) {
			final Element childElt = childIt1.nextElement();
			String childTag = childElt.getTagName();
			childTag = childTag.toLowerCase().contains("pattern") ? "TRACKLINE" :
					childTag;
			final String childTagLc = childTag.toLowerCase();
			if ("comp_object_type".equals(childTagLc)) {
				try {
					final int objectTypeId =
							ModelReader.getInt(simCase, childElt, "id", "", stringPluses);
					final LrcSet lrcSet =
							ModelReader.getLrcSet(simCase, childElt, stringPluses);
					final boolean viz2 = ModelReader.getBoolean(simCase, childElt,
							"isActive", true, stringPluses);
					if (lrcSet != null) {
						if (viz2 && !lrcSet.isBlind()) {
							_viz2LrcSets.put(objectTypeId, lrcSet);
						}
						_viz1LrcSets.put(objectTypeId, lrcSet);
						_plannerModel.addViz1ObjectTypeId(objectTypeId);
						if (viz2) {
							_plannerModel.addViz2ObjectTypeId(objectTypeId);
						}
					}
				} catch (final ReaderException e) {
				}
			} else if ("overlap_exception".equals(childTagLc)) {
				try {
					final boolean mayOverlap = ModelReader.getBoolean(simCase,
							childElt, "may_overlap", false, stringPluses);
					String nameId1 = null;
					nameId1 = ModelReader.getString(simCase, childElt, "sru",
							/* defaultValue= */null, stringPluses);
					if (nameId1 == null) {
						nameId1 = ModelReader.getString(simCase, childElt,
								"pattern_variable", "", stringPluses);
					}
					final String id1 = PlannerModel.getIdFromNameId(nameId1);
					if (id1.length() == 0) {
						continue;
					}
					_plannerModel.registerOverlapException(
							new String[] { _id, id1, Boolean.toString(mayOverlap) });
				} catch (final ReaderException e) {
				}
			}
		}

		/**
		 * To keep this in the optimization problem, if he has nothing he is
		 * supposed to look for, make it so that he has a weak sensor for each
		 * object type.
		 */
		if (_viz2LrcSets.isEmpty()) {
			final LrcSet lrcSet = new LrcSet();
			lrcSet.add(LateralRangeCurve._NearSighted);
			for (final int objectTypeId : _viz1LrcSets.keySet()) {
				_viz2LrcSets.put(objectTypeId, lrcSet);
			}
		}

		/** Set _initialPvValue for trackLine. */
		PvValue initialPvValue = null;
		if (trackLine) {
			final ElementIterator childIt = new ElementIterator(pvElt);
			while (childIt.hasNextElement()) {
				final Element initPvValueElt = childIt.nextElement();
				final String initPvValueTag = initPvValueElt.getTagName();
				final String initPvValueTagLc = initPvValueTag.toLowerCase();
				if (initPvValueTagLc.equals("trackline") ||
						initPvValueTagLc.equals("pattern")) {
					initialPvValue =
							new PvValue(simCase, this, initPvValueElt, stringPluses);
					break;
				}
			}
		}

		/** Set _pvCstRefSecs and _pvRawSearchDurationSecs. */
		if (trackLine) {
			/**
			 * With a TrackLine, we always get pvCstRefSecs and
			 * pvRawSearchDurationSecs from initialPvValue.
			 */
			_pvCstRefSecs = initialPvValue.getCstRefSecs();
			_pvRawSearchDurationSecs = initialPvValue.getSearchDurationSecs();
		} else if (standAlone) {
			/**
			 * For a Standalone, we need to get _pvCstRefSecs and
			 * _pvRawSearchDurationSecs from pvElt.
			 */
			final String pvCstString = pvElt.getAttribute("cst");
			_pvCstRefSecs = TimeUtilities.dtgToRefSecs(pvCstString);
			_pvRawSearchDurationSecs = ModelReader.getDurationSecs(simCase, pvElt,
					"duration", stringPluses);
		} else {
			/**
			 * For non-trackLine, non-standAlone, there is no pvCstRefSecs or
			 * pvSarchDurationSecs.
			 */
			_pvCstRefSecs = PlannerModel._UnsetTime;
			_pvRawSearchDurationSecs = PlannerModel._BadDuration;
		}

		/**
		 * With _pvCstRefSecs and _pvRawSearchDurationSecs set, we can look for
		 * a box for non-trackLine..
		 */
		if (!trackLine) {
			final ElementIterator childIt = new ElementIterator(pvElt);
			while (childIt.hasNextElement()) {
				final Element initPvValueElt = childIt.nextElement();
				final String initPvValueTag = initPvValueElt.getTagName();
				final String initPvValueTagLc = initPvValueTag.toLowerCase();
				if (initPvValueTagLc.equals("box")) {
					final PvValue initialPvValueX =
							new PvValue(simCase, this, initPvValueElt, stringPluses);
					initialPvValue =
							initialPvValueX.onMars() ? null : initialPvValueX;
					break;
				}
			}
		}
		_initialPvValue = initialPvValue;

		/** Update plannerModel's pvCst. */
		final long myRefSecs0;
		final long myRefSecs1;
		if (isTrackLine()) {
			myRefSecs0 = _initialPvValue.getCstRefSecs();
			myRefSecs1 = _initialPvValue.getEstRefSecs();
		} else if (pvSeq == null) {
			myRefSecs0 = _pvCstRefSecs;
			myRefSecs1 = _pvCstRefSecs + _pvRawSearchDurationSecs;
		} else if (_initialPvValue != null) {
			myRefSecs0 = _initialPvValue.getCstRefSecs();
			myRefSecs1 = _initialPvValue.getEstRefSecs();
		} else {
			/** PvSeq != null but there is no initial PvValue. */
			myRefSecs0 = myRefSecs1 = ModelReader._UnsetTime;
		}
		plannerModel.updatePvRefSecsS(myRefSecs0, myRefSecs1);
	}

	public PatternKind getPatternKind() {
		return _patternKind;
	}

	public MotionType getMotionType() {
		return _motionType;
	}

	public double getExcBufferNmi() {
		return _excBufferNmi;
	}

	public String getNameId() {
		String s = "";
		if (_name != null && _name.length() > 0) {
			s += _name;
		}
		if (_id != null && _id.length() > 0) {
			if (s.length() > 0) {
				s += "_";
			}
			s += _id;
		}
		return s;
	}

	public PvSeq getPvSeq() {
		return _pvSeq;
	}

	public int getGrandOrd() {
		return _grandOrd;
	}

	public int getOrdWithinPvSeq() {
		return _ordWithinPvSeq;
	}

	public void setIsActive(final boolean isActive) {
		if (getPermanentFrozenPvValue() != null) {
			_isActive = true;
		}
		_isActive = isActive;
	}

	public boolean isActive() {
		return _isActive;
	}

	public double getTransitKts() {
		if (_pvSeq == null) {
			return 0d;
		}
		return _transitKts;
	}

	public double getOutgoingTransitKts() {
		if (_pvSeq == null) {
			return Double.NaN;
		}
		final int nextOrdWithinPvSeq = _ordWithinPvSeq + 1;
		final PatternVariable nextPv = _pvSeq.getPttrnVbl(nextOrdWithinPvSeq);
		if (nextPv == null) {
			return _pvSeq.hasRecoveryTransit() ? _pvSeq._recoveryKts : Double.NaN;
		}
		return nextPv.getTransitKts();
	}

	public boolean isTrackLine() {
		return _initialPvValue != null && _initialPvValue.getMyStyle() == null;
	}

	public PvValue getInitialPvValue() {
		return _initialPvValue;
	}

	public boolean getCanUserMove() {
		return _isActive && getUserFrozenPvValue() == null;
	}

	public PvValue getPermanentFrozenPvValue() {
		return isTrackLine() ? _initialPvValue : null;
	}

	public PvValue getUserFrozenPvValue() {
		final PvValue permanentFrozenPvValue = getPermanentFrozenPvValue();
		if (permanentFrozenPvValue != null) {
			return permanentFrozenPvValue;
		}
		return _userFrozenPvValue;
	}

	public void setUserFrozenPvValue(final PvValue userFrozenPvValue) {
		_userFrozenPvValue = userFrozenPvValue;
	}

	/** Simple accessors. */
	public String getDisplayName() {
		final PvValue permanentFrozenPvValue = getPermanentFrozenPvValue();
		if (permanentFrozenPvValue != null) {
			return "FR_" + getNameId();
		}
		return getNameId();
	}

	public String getName() {
		return _name == null ? "" : _name;
	}

	public String getId() {
		return _id;
	}

	public PvType getPvType() {
		return _pvType;
	}

	public PlannerModel getPlannerModel() {
		return _plannerModel;
	}

	public double getMinTsNmi() {
		return _minTsNmi;
	}

	public double getRawSearchKts() {
		return _rawSearchKts;
	}

	public long getPvCstRefSecs() {
		if (getPermanentFrozenPvValue() != null) {
			return _initialPvValue.getCstRefSecs();
		}
		return _pvSeq == null ? _pvCstRefSecs : ModelReader._UnsetTime;
	}

	public int getPvRawSearchDurationSecs() {
		if (getPermanentFrozenPvValue() != null) {
			return _initialPvValue.getSearchDurationSecs();
		}
		return _pvSeq == null ? _pvRawSearchDurationSecs :
				ModelReader._BadDuration;
	}

	public LrcSet getViz1LrcSet(final int objectType) {
		return _viz1LrcSets.get(objectType);
	}

	public LrcSet getViz2LrcSet(final int objectType) {
		return _viz2LrcSets.get(objectType);
	}

	public HashMap<Integer, LrcSet> getViz1LrcSets() {
		return _viz1LrcSets;
	}

	public HashMap<Integer, LrcSet> getViz2LrcSets() {
		return _viz2LrcSets;
	}

	/** Debugging */
	@Override
	public String toString() {
		return getString();
	}

	public String getString() {
		final String ordPvSeqString;
		String s0 = "", s1 = "";
		if (_name != null && _name.length() > 0) {
			s0 += "Name";
			s1 += _name;
		}
		if (_id != null && _id.length() > 0) {
			if (s0.length() > 0) {
				s0 += "-";
				s1 += "-";
			}
			s0 += "Id";
			s1 += _id;
		}
		if (_pvSeq != null) {
			ordPvSeqString =
					String.format("PvSeq[%s] Pv%s/GrndOrd/ordWithinPvSeq[%s/%d/%d]",
							_pvSeq._id, s0, s1, _grandOrd, _ordWithinPvSeq);
		} else {
			ordPvSeqString =
					String.format("%s/GrndOrd[%s/%d]", s0, s1, _grandOrd);
		}
		final PvValue initPvValue = getInitialPvValue();
		final String initConfigString;
		if (initPvValue == null) {
			initConfigString = _patternKind.isPsCs() ? "LpBox" :
					(_patternKind.isVs() ? "VsBox" : "SsBox");
		} else {
			if (_patternKind == null) {
				initConfigString = "TRCKLN";
			} else {
				initConfigString = _patternKind.isPsCs() ? "InitLpBox" :
						(_patternKind.isVs() ? "InitVsBox" : "InitSsBox");
			}
		}
		String s = String.format("%s %s/%s", ordPvSeqString, _pvType._xmlString,
				initConfigString);
		s += String.format(" MinTs/Buffer[%.2f/%.2f]", _minTsNmi,
				_excBufferNmi);
		final boolean gotAnLrcSet = false;
		for (final Map.Entry<Integer, LrcSet> entry : _viz1LrcSets.entrySet()) {
			final LrcSet lrcSet = entry.getValue();
			final int nLrcs = lrcSet.getNLrcs();
			if (nLrcs == 0) {
				continue;
			}
			if (!gotAnLrcSet) {
				s += "\n";
			} else {
				s += " ";
			}
			final Integer objectTypeId = entry.getKey();
			final boolean isActive = _viz2LrcSets.containsKey(objectTypeId);
			final String prefix = isActive ? "+" : "-";
			s += String.format(prefix + "SO/SW[%s/%.2f]", //
					objectTypeId.toString(), lrcSet.getSweepWidth());
			if (nLrcs > 1) {
				s += String.format("(%d Lrcs)", nLrcs);
			}
		}
		final PlannerModel plannerModel = getPlannerModel();
		String ovlFriendsString = "\nOvl Friends: ";
		int nFriends = 0;
		for (final PatternVariable pv : plannerModel.getPttrnVbls()) {
			if (pv != this) {
				final boolean forOptnMayOverlap =
						plannerModel.mayOverlap(this, pv, /* forOptn= */true);
				final boolean mayOverlapForReports =
						plannerModel.mayOverlap(this, pv, /* forOptn= */false);
				if (forOptnMayOverlap) {
					ovlFriendsString +=
							String.format("%s%s%s", (nFriends++ == 0 ? "" : ","),
									pv.getId(), (mayOverlapForReports ? "" : "*"));
				}
			}
		}
		if (nFriends == 0) {
			s += "\nNo Ovl Friends";
		} else {
			s += ovlFriendsString;
		}
		return s;
	}

	void resetGrandOrd(final int grandOrd) {
		_grandOrd = grandOrd;
	}

	public void resetOrdWithinPvSeq(final int ordWithinPvSeq) {
		_ordWithinPvSeq = ordWithinPvSeq;
	}

	/** Very rough estimate of coverage from the dimensions of the box. */
	public double getNftPod(final int searchDurationSecs, final double avgSw,
			final double dimension0Nmi, final double dimension1Nmi) {
		final double eplNmi = _rawSearchKts * (searchDurationSecs / 3600d) *
				PatternUtilStatics._EffectiveSpeedReduction;
		final double tsNmi;
		if (_patternKind.isPsCs() || _patternKind.isSs()) {
			final double minDimNmi = Math.min(dimension0Nmi, dimension1Nmi);
			final double maxDimNmi = Math.max(dimension0Nmi, dimension1Nmi);
			tsNmi = minDimNmi * maxDimNmi / eplNmi;
		} else if (_patternKind.isVs()) {
			tsNmi = PatternUtilStatics.computeVsTsNmi(eplNmi);
		} else {
			tsNmi = Double.NaN;
		}
		final double coverage = avgSw / tsNmi;
		final CoverageToPodCurve coverageToPodCurve =
				_plannerModel.getCoverageToPodCurve();
		final double pod = coverageToPodCurve.coverageToPod(coverage);
		return pod;
	}

	public Sortie computeSortie(final SphericalTimedSegs sphericalTimedSegs,
			final double creepHdg, final double tsNmiForSingleLeg0,
			final boolean onMars) {
		final LatLng3[] path = sphericalTimedSegs._path;
		final double tsNmiForSingleLeg;
		if (tsNmiForSingleLeg0 > 0d) {
			tsNmiForSingleLeg = tsNmiForSingleLeg0;
		} else {
			tsNmiForSingleLeg = PatternUtilStatics._TsInc;
		}
		final Sortie sortie = new Sortie(/* _model= */null, /* sortieId= */_id,
				/* sortieName= */_name, _motionType, creepHdg,
				/* fxdTsNmi= */Double.NaN, tsNmiForSingleLeg);
		final long[] waypointSecsS = sphericalTimedSegs._waypointSecsS;
		/** Build legs. */
		final int nPoints = path.length;
		for (int k = 0; k < nPoints - 1; ++k) {
			final long legStartRefSecs = waypointSecsS[k];
			final long legStopRefSecs = waypointSecsS[k + 1];
			final LatLng3 legStart = path[k];
			final LatLng3 legStop = path[k + 1];
			sortie.addLegIfNotVacuous(legStart, legStop, legStartRefSecs,
					legStopRefSecs);
		}
		if (!onMars) {
			sortie.setViz1LrcSets(_viz1LrcSets);
			sortie.setViz2LrcSets(_viz2LrcSets);
		}
		sortie.fillInSortieDataFromDistinctInputLegs();
		return sortie;
	}
}
