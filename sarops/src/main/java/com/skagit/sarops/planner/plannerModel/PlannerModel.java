package com.skagit.sarops.planner.plannerModel;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.skagit.sarops.AbstractOutFilesManager;
import com.skagit.sarops.model.ExtraGraphicsClass;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.io.ModelReader;
import com.skagit.sarops.model.io.ModelReader.ReaderException;
import com.skagit.sarops.model.io.ModelReader.StringPlus;
import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.plannerModel.pvSeq.PvSeq;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.planner.posFunction.pvValueArrayPlus.PvValueArrayPlus;
import com.skagit.sarops.planner.solver.SolversManager;
import com.skagit.sarops.simCaseManager.MainRunner;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.simCaseManager.SimCaseManager.SimCase;
import com.skagit.sarops.tracker.CoverageToPodCurve;
import com.skagit.sarops.tracker.ParticlesFile;
import com.skagit.sarops.tracker.lrcSet.LrcSet;
import com.skagit.sarops.util.patternUtils.MyStyle;
import com.skagit.util.CombinatoricTools;
import com.skagit.util.ElementIterator;
import com.skagit.util.LsFormatter;
import com.skagit.util.StringUtilities;
import com.skagit.util.TimeUtilities;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.randomx.Randomx;

import ucar.ma2.Array;
import ucar.ma2.Index;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

public class PlannerModel {

	public final static long _UnsetTime = ModelReader._UnsetTime;
	public final static int _BadDuration = ModelReader._BadDuration;

	public static final String _LatestSeedAttributeName = "latestSeed";
	public static final String _FirstTurnRightSeedAttributeName =
			"firstTurnRightSeed";
	public static final String _OriginalSeedAttributeName = "originalSeed";

	final private SimCaseManager.SimCase _simCase;
	final private Model _simModel;
	final private String _particlesFilePath;
	final private String _xmlSimPropertiesFilePath;
	final private String _plannerFilePath;
	final private boolean _includeLanded;
	final private boolean _includeAdrift;
	final private boolean _erf;

	final private boolean _runStudy;

	final private String _stashedPlannerModelPath;

	/**
	 * Even though it's stored as a long, we never set _originalSeed or
	 * _latestSeed to a number as big as Integer.MAX_VALUE, so we can
	 * consistently go back and forth in the xml.
	 */
	final private long _originalSeed;
	private long _latestSeed;
	final private boolean _allPttrnVblsAreInitialized;

	final private long _simStartRefSecs;
	final private long _simEndRefSecs;

	final private int _plannerTimeSecs;

	/** The PvSeqs and PatternVariables. */
	final private PvSeq[] _pvSeqs;
	final private PatternVariable[] _pttrnVbls;

	final private double _psCsThreshold;
	final private boolean _psCsMayFirstTurnRight, _psCsMayFirstTurnLeft;
	final private boolean _ssMayFirstTurnRight, _ssMayFirstTurnLeft;
	final private boolean _vsMayFirstTurnRight, _vsMayFirstTurnLeft;
	final private Randomx _firstTurnRightRandom;
	/**
	 * A visible Object Type Id is one for which there is an Lrc for some Pv,
	 * even it it's not active.
	 */
	final private HashSet<Integer> _viz1ObjectTypeIds;
	final private HashSet<Integer> _viz2ObjectTypeIds;
	private long _minPvRefSecs;
	private long _maxPvRefSecs;

	final TreeSet<String[]> _overlapExceptions;

	final private ExtraGraphicsClass _extraGraphicsObject;

	public static Comparator<PatternVariable[]> _PvArrayComparator =
			new Comparator<>() {
				@Override
				public int compare(final PatternVariable[] pttrnVbls0,
						final PatternVariable[] pttrnVbls1) {
					if (pttrnVbls0 == pttrnVbls1) {
						return 0;
					}
					if ((pttrnVbls0 == null) != (pttrnVbls1 == null)) {
						return pttrnVbls0 == null ? -1 : 1;
					}
					if (pttrnVbls0 == null) {
						return 0;
					}
					final int n0 = pttrnVbls0.length;
					final int n1 = pttrnVbls1.length;
					if (n0 != n1) {
						return n0 - n1;
					}
					for (int k = 0; k < n0; ++k) {
						final PatternVariable pv0 = pttrnVbls0[k];
						final PatternVariable pv1 = pttrnVbls1[k];
						if ((pv0 == null) != (pv1 == null)) {
							return pv0 == null ? -1 : 1;
						}
					}
					return 0;
				}
			};

	/** For All, including ComparePlanCase. */
	@SuppressWarnings("deprecation")
	public PlannerModel(final SimCaseManager.SimCase simCase,
			final Model simModel, final String plannerFilePath,
			final String particlesFilePath) {

		_simCase = simCase;
		_simModel = simModel;
		_plannerFilePath = plannerFilePath;
		/**
		 * Distinguish whether or not this is for ComparePlanCase or regular
		 * Plannenr.
		 */
		final boolean forComparePlanCase = particlesFilePath != null;

		Document document = null;
		try (final FileInputStream fis =
				new FileInputStream(new File(_plannerFilePath))) {
			document = LsFormatter._DocumentBuilder.parse(fis);
		} catch (SAXException | IOException e) {
		}
		final Element root = document.getDocumentElement();

		final TreeSet<ModelReader.StringPlus> stringPluses;
		if (forComparePlanCase) {
			_particlesFilePath = particlesFilePath;
			root.setAttribute("particleFile", _particlesFilePath);
			_stashedPlannerModelPath = null;
			stringPluses = null;
		} else {
			stringPluses = new TreeSet<>(ModelReader._StringPlusComparator);
			_particlesFilePath =
					AbstractOutFilesManager.GetParticlesFilePathFromXml(plannerFilePath);
			_stashedPlannerModelPath = ModelReader.stashEngineFile(_simCase,
					plannerFilePath, plannerFilePath, SimCaseManager._PlanEndingLc,
					"PlanInput", /* overwrite= */false);
		}

		final String tagName = root.getTagName();
		if ((tagName.compareToIgnoreCase("PLAN") != 0)) {
			MainRunner.HandleFatal(_simCase,
					new RuntimeException("Planner case); root should be PLAN."));
		}
		_xmlSimPropertiesFilePath =
				root.getAttribute(ModelReader._XmlSimPropertiesFilePathAtt);
		if (_xmlSimPropertiesFilePath != null) {
			ModelReader.addAndOverrideXmlSimProperties(_simCase, root,
					stringPluses);
		}
		final Properties simProperties = _simCase.getSimProperties();
		/**
		 * Replicate SRU properties to PATTERN_VARIABLE and
		 * PATTERN_VARIABLE_SEQUENCE.PATTERN_VARIABLE properties, and then dump
		 * the properties.
		 */
		ModelReader.addSruToPvSimProperties(simProperties);
		/** "Outstream" simProperties. */
		ModelReader.dumpSimProperties(_simCase);

		/** Necessary constants that we simply get from SimProperties. */
		_psCsThreshold =
				_simCase.getSimPropertyDouble("PLAN.psCsThreshold", 3d);
		_psCsMayFirstTurnRight =
				_simCase.getSimPropertyBoolean("PLAN.PsCsMayFirstTurnRight", true);
		_psCsMayFirstTurnLeft = !_psCsMayFirstTurnRight ||
				_simCase.getSimPropertyBoolean("PLAN.PsCsMayFirstTurnLeft", true);
		_ssMayFirstTurnRight =
				_simCase.getSimPropertyBoolean("PLAN.SsMayFirstTurnRight", true);
		_ssMayFirstTurnLeft = !_ssMayFirstTurnRight ||
				_simCase.getSimPropertyBoolean("PLAN.SsMayFirstTurnLeft", true);
		_vsMayFirstTurnRight =
				_simCase.getSimPropertyBoolean("PLAN.VsMayFirstTurnRight", true);
		_vsMayFirstTurnLeft = !_vsMayFirstTurnRight ||
				_simCase.getSimPropertyBoolean("PLAN.VsMayFirstTurnLeft", true);

		/** Obscure values from Sim.properties only. */
		final String coverageMode = ModelReader.getString(_simCase, root,
				"mode", "normal", stringPluses);
		_erf = "ideal".equals(coverageMode);

		/** Landed, Adrift, Seeds. */
		boolean includeLanded = true;
		boolean includeAdrift = true;
		long longSeed = -1L;
		long firstTurnRightSeed = -1L;
		int plannerTimeSecs = -1;
		boolean runStudy = false;
		try {
			includeLanded = ModelReader.getBoolean(_simCase, root,
					"searchForLandedParticles", includeLanded, stringPluses);
			includeAdrift = ModelReader.getBoolean(_simCase, root,
					"searchForAdriftParticles", includeAdrift, stringPluses);
			/** Seeds. */
			longSeed = ModelReader.getLong(_simCase, root,
					_LatestSeedAttributeName, "", stringPluses);
			firstTurnRightSeed = ModelReader.getLong(_simCase, root,
					_FirstTurnRightSeedAttributeName, "", stringPluses);
			/** Time allotted. */
			plannerTimeSecs = Math.min(3600, ModelReader.getInt(_simCase, root,
					"plannerTimeInSeconds", " secs", -1, stringPluses));
			runStudy = ModelReader.getBoolean(_simCase, root, "runStudy", false,
					stringPluses);
		} catch (final ReaderException e) {
			MainRunner.HandleFatal(_simCase,
					new RuntimeException("Planner Simple Specs Read failed."));
		}
		_includeLanded = includeLanded;
		_includeAdrift = includeAdrift;

		/**
		 * Because the outside world cannot read a long, we must keep our seeds
		 * as ints.
		 */
		_originalSeed = _latestSeed = CombinatoricTools.longToInt(longSeed);
		_firstTurnRightRandom = new Randomx(firstTurnRightSeed);

		_plannerTimeSecs = plannerTimeSecs;

		_runStudy = runStudy && !forComparePlanCase;

		/**
		 * Read in _simStartRefSecs and simEndRefSecs from the particlesFile.
		 */
		long simStartRefSecs = _UnsetTime;
		long simEndRefSecs = _UnsetTime;
		try (
				final NetcdfFile netCdfFile = NetcdfFile.open(_particlesFilePath)) {
			final Dimension timeDimension = netCdfFile.findDimension("time");
			final Variable timeVariable =
					netCdfFile.findVariable(ParticlesFile._VarTime);
			final Array timeArray = timeVariable.read();
			final Index timeIndex = timeArray.getIndex();
			timeIndex.set(0);
			simStartRefSecs = timeArray.getInt(timeIndex);
			timeIndex.set(timeDimension.getLength() - 1);
			simEndRefSecs = timeArray.getInt(timeIndex);
			SimCaseManager.out(_simCase,
					String.format("Simulation data available between %s and %s.",
							TimeUtilities.formatTime(simStartRefSecs, true),
							TimeUtilities.formatTime(simEndRefSecs, true)));
		} catch (final IOException e) {
			MainRunner.HandleFatal(_simCase, new RuntimeException(e));
		}
		_simStartRefSecs = simStartRefSecs;
		_simEndRefSecs = simEndRefSecs;

		/**
		 * Create the global set of OverlapExceptions, to be updated while
		 * reading in PlannerVariables.
		 */
		_overlapExceptions = new TreeSet<>(new Comparator<String[]>() {
			@Override
			public int compare(final String[] o1, final String[] o2) {
				final int compareValue = o1[0].compareTo(o2[0]);
				if (compareValue != 0) {
					return compareValue;
				}
				return o1[1].compareTo(o2[1]);
			}
		});

		/**
		 * Initialize values that are referenced while reading in
		 * PatternVariables.
		 */
		_minPvRefSecs = _simEndRefSecs;
		_maxPvRefSecs = _simStartRefSecs;
		_viz1ObjectTypeIds = new HashSet<>();
		_viz2ObjectTypeIds = new HashSet<>();

		/**
		 * For onMars placements, we need a map from (name,id) to an integer.
		 * Create that before we start constructing the PatternVariables.
		 */
		final TreeMap<String[], Integer> nameIdMap =
				new TreeMap<>(new Comparator<String[]>() {

					@Override
					public int compare(final String[] nameId0,
							final String[] nameId1) {
						for (int iPass = 0; iPass < 2; ++iPass) {
							final String s0 = nameId0[iPass];
							final String s1 = nameId1[iPass];
							if ((s0 == null) != (s1 == null)) {
								return s0 == null ? -1 : 1;
							}
							final int compareValue = s0 == null ? 0 : s0.compareTo(s1);
							if (compareValue != 0) {
								return compareValue;
							}
						}
						return 0;
					}
				});
		final ElementIterator rootIt0 = new ElementIterator(root);
		while (rootIt0.hasNextElement()) {
			final Element pvSeqElt = rootIt0.nextElement();
			final String pvSeqEltTagLc = pvSeqElt.getTagName().toLowerCase();
			if ("pattern_variable_sequence".equals(pvSeqEltTagLc) ||
					"sortie".equals(pvSeqEltTagLc)) {
				addToNameIdMap(nameIdMap, pvSeqElt);
			}
		}
		addToNameIdMap(nameIdMap, root);

		/**
		 * Read in the PatternVariables; start with the PvSeqs. While doing so,
		 * accumulate the standAlones that are unintentionally within PvSeq
		 * tags.
		 */
		final ArrayList<PatternVariable> unintendedStandAlones =
				new ArrayList<>();
		final ElementIterator rootIt = new ElementIterator(root);
		final ArrayList<PvSeq> pvSeqList = new ArrayList<>();
		while (rootIt.hasNextElement()) {
			final Element pvSeqElt = rootIt.nextElement();
			final String pvSeqEltTagLc = pvSeqElt.getTagName().toLowerCase();
			if ("pattern_variable_sequence".equals(pvSeqEltTagLc) ||
					"sortie".equals(pvSeqEltTagLc)) {
				final PvSeq pvSeq = new PvSeq(this, nameIdMap, pvSeqElt,
						unintendedStandAlones, stringPluses);
				if (pvSeq.getNMyPttrnVbls() > 0) {
					pvSeqList.add(pvSeq);
				}
			}
		}
		final Iterator<PvSeq> it = pvSeqList.iterator();
		while (it.hasNext()) {
			final PvSeq pvSeq = it.next();
			if (pvSeq.getNMyPttrnVbls() == 0) {
				it.remove();
			}
		}
		final int nPvSeqs = pvSeqList.size();
		_pvSeqs = pvSeqList.toArray(new PvSeq[nPvSeqs]);
		for (int pvSeqOrd = 0; pvSeqOrd < nPvSeqs; ++pvSeqOrd) {
			_pvSeqs[pvSeqOrd].resetPvSeqOrd(pvSeqOrd);
		}

		/** Get the stand-alone PatternVariables. */
		final ArrayList<PatternVariable> pttrnVblList =
				readPttrnVbls(nameIdMap, /* pvSeq= */null,
						/* unintendedStandAlones= */null, root, stringPluses);
		pttrnVblList.addAll(unintendedStandAlones);
		/** Add the pvSeq PatternVariables. */
		for (int k = 0; k < nPvSeqs; ++k) {
			pttrnVblList.addAll(Arrays.asList(_pvSeqs[k].getMyPttrnVbls()));
		}
		final int nPttrnVbls = pttrnVblList.size();

		/** Store the PatternVariables and reset their GrandOrds. */
		_pttrnVbls = pttrnVblList.toArray(new PatternVariable[nPttrnVbls]);
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			_pttrnVbls[grandOrd].resetGrandOrd(grandOrd);
		}

		/** Clean up the ObjectTypeIds. */
		for (int iPass = 0; iPass < 2; ++iPass) {
			final HashSet<Integer> vizObjectTypeIds =
					iPass == 0 ? _viz2ObjectTypeIds : _viz1ObjectTypeIds;
			vizObjectTypeIds.clear();
			for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
				final PatternVariable pv = _pttrnVbls[grandOrd];
				final HashMap<Integer, LrcSet> vizLrcSets =
						iPass == 0 ? pv.getViz2LrcSets() : pv.getViz1LrcSets();
				vizObjectTypeIds.addAll(vizLrcSets.keySet());
			}
		}

		/**
		 * We will not refine the first iteration if all the Pattern Variables
		 * were initialized.
		 */
		boolean allPttrnVblsAreInitialized = true;
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PatternVariable pv = _pttrnVbls[grandOrd];
			final PvValue initialPvValue = pv.getInitialPvValue();
			if (initialPvValue == null) {
				/** Found a PatternVariable that is not initialized. */
				allPttrnVblsAreInitialized = false;
				break;
			}
		}
		_allPttrnVblsAreInitialized = allPttrnVblsAreInitialized;

		final TreeSet<String[]> overlapExceptions =
				new TreeSet<>(_overlapExceptions.comparator());
		overlapExceptions.addAll(_overlapExceptions);
		_overlapExceptions.clear();
		for (final String[] overlapException : overlapExceptions) {
			final String id0 = overlapException[0];
			final String id1 = overlapException[1];
			final PatternVariable pv0 = getPv(id0);
			final PatternVariable pv1 = getPv(id1);
			if (pv0 == null || pv1 == null) {
				SimCaseManager.wrn(_simCase, "Bad Overlap Exception.");
				continue;
			}
			if (defaultMayOverlap(pv0, pv1) != Boolean
					.parseBoolean(overlapException[2])) {
				_overlapExceptions.add(overlapException);
			}
		}

		/** Get the Graphics. For this, we need a SimulatorModel. */
		if (_runStudy) {
			_extraGraphicsObject = _simModel.getExtraGraphicsObject();
			/** Add the ones from our own Graphics Element. */
			final ElementIterator childIt = new ElementIterator(root);
			while (childIt.hasNextElement()) {
				final Element childElt = childIt.nextElement();
				final String tagLc = childElt.getTagName().toLowerCase();
				if ("graphics".equals(tagLc)) {
					ModelReader.readGraphics(_simCase, childElt, _simModel,
							_extraGraphicsObject, stringPluses);
				}
			}
		} else {
			_extraGraphicsObject = new ExtraGraphicsClass();
		}

		/** Write the echo and fancy SimProperties files. */
		if (!forComparePlanCase) {
			new EchoAndNextPlanWriter(this).writeEchoFiles(_simCase,
					stringPluses);
			/** Simple Stash of SimProperties. */
			ModelReader.stashSimProperties(_simCase, _plannerFilePath,
					/* simulator= */false);

			/** Write out the Overlap constraints. */
			String s1 = "\nPlannerMain: Cleaned up overlapExceptions:";
			for (final String[] overlapException : _overlapExceptions) {
				s1 += "\n" + CombinatoricTools.toString(overlapException);
			}
			s1 += "\n\nAll Overlaps:";
			for (int grandOrd0 = 0; grandOrd0 < nPttrnVbls - 1; ++grandOrd0) {
				final PatternVariable pv0 = grandOrdToPv(grandOrd0);
				for (int grandOrd1 = grandOrd0 + 1; grandOrd1 < nPttrnVbls;
						++grandOrd1) {
					final PatternVariable pv1 = grandOrdToPv(grandOrd1);
					final boolean reportMayOverlap =
							mayOverlap(pv0, pv1, /* forOptn= */false);
					final String reportString =
							reportMayOverlap ? "ForReportMAY" : "ForReportMayNOT";
					final boolean optMayOverlap =
							mayOverlap(pv0, pv1, /* forOptn= */true);
					final String optString =
							optMayOverlap ? "ForOptMAY" : "ForOptMayNOT";
					s1 += String.format("\n%s|%s: (%s,%s)", pv0.getId(), pv1.getId(),
							reportString, optString);
				}
			}
			s1 += "\n";
			SimCaseManager.out(_simCase, s1);
		}
	}

	/** Accessors. */
	public SimCase getSimCase() {
		return _simCase;
	}

	public Planner getPlanner() {
		final Object mso = _simCase.getMainSaropsObject();
		return mso == null ? null : (Planner) mso;
	}

	public PatternVariable getPv(final String id) {
		final int nPttrnVbls = getNPttrnVbls();
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PatternVariable pv = _pttrnVbls[grandOrd];
			if (pv.getId().equals(id)) {
				return pv;
			}
		}
		return null;
	}

	public HashSet<Integer> getViz1ObjectTypeIds() {
		return _viz1ObjectTypeIds;
	}

	public HashSet<Integer> getViz2ObjectTypeIds() {
		return _viz2ObjectTypeIds;
	}

	public ArrayList<PatternVariable> readPttrnVbls(
			final Map<String[], Integer> nameIdMap, final PvSeq pvSeq,
			final List<PatternVariable> unintendedStandAlones,
			final Element parentElt, final TreeSet<StringPlus> stringPluses) {
		final ElementIterator pvEltIt = new ElementIterator(parentElt);
		final ArrayList<PatternVariable> newPvList = new ArrayList<>();
		while (pvEltIt.hasNextElement()) {
			final Element pvElt = pvEltIt.nextElement();
			final String pvEltTagLc = pvElt.getTagName().toLowerCase();
			if ("sru".equals(pvEltTagLc) ||
					"pattern_variable".equals(pvEltTagLc)) {
				final PatternVariable pv = new PatternVariable(this, nameIdMap,
						pvSeq, pvElt, stringPluses);
				if (unintendedStandAlones != null && pvSeq != null &&
						pv.getPvSeq() == null) {
					/** This is an unintended standAlone. */
					unintendedStandAlones.add(pv);
				}
				newPvList.add(pv);
			}
		}
		return newPvList;
	}

	private static void addToNameIdMap(
			final TreeMap<String[], Integer> nameIdMap, final Element parentElt) {
		final ElementIterator pvEltIt = new ElementIterator(parentElt);
		while (pvEltIt.hasNextElement()) {
			final Element pvElt = pvEltIt.nextElement();
			final String pvEltTagLc = pvElt.getTagName().toLowerCase();
			if ("sru".equals(pvEltTagLc) ||
					"pattern_variable".equals(pvEltTagLc)) {
				final String name = pvElt.getAttribute("name");
				final String id = pvElt.getAttribute("id");
				final String[] nameIdPair = new String[] { name, id };
				if (!nameIdMap.containsKey(nameIdPair)) {
					nameIdMap.put(nameIdPair, nameIdMap.size());
				}
			}
		}
	}

	public boolean getErf() {
		return _erf;
	}

	public CoverageToPodCurve getCoverageToPodCurve() {
		return _erf ? CoverageToPodCurve._ErfCurve :
				CoverageToPodCurve._ExpCurve;
	}

	public String getParticlesFilePath() {
		return _particlesFilePath;
	}

	public void updatePvRefSecsS(final long pvRefSecs0,
			final long pvRefSecs1) {
		_minPvRefSecs = Math.min(_minPvRefSecs, pvRefSecs0);
		_maxPvRefSecs = Math.max(_maxPvRefSecs, pvRefSecs1);
	}

	public long getMinPvRefSecs() {
		return _minPvRefSecs;
	}

	public long getMaxPvRefSecs() {
		return _maxPvRefSecs;
	}

	public long getMidPvRefSecs() {
		return (_minPvRefSecs + _maxPvRefSecs) / 2;
	}

	public boolean includeLanded() {
		return _includeLanded;
	}

	public boolean includeAdrift() {
		return _includeAdrift;
	}

	public int getPlannerTimeSecs() {
		return Math.max(0, _plannerTimeSecs);
	}

	public boolean runStudy() {
		return _runStudy;
	}

	static String getIdFromNameId(final String nameId) {
		final int last_ = nameId.lastIndexOf('_');
		if (0 <= last_ && last_ <= nameId.length() - 2) {
			return nameId.substring(last_ + 1);
		}
		return null;
	}

	void registerOverlapException(final String[] overlapException) {
		final String id0 = overlapException[0];
		final String id1 = overlapException[1];
		final int compareValue = id0.compareTo(id1);
		if (compareValue == 0) {
			return;
		}
		String[] workingOverlapException = overlapException;
		if (compareValue > 0) {
			/** They're in the wrong order. Reverse them. */
			workingOverlapException =
					new String[] { id1, id0, overlapException[2] };
		}
		/** Remove the old one (if necessary) and enter the new one. */
		if (!_overlapExceptions.add(workingOverlapException)) {
			_overlapExceptions.remove(workingOverlapException);
			_overlapExceptions.add(workingOverlapException);
		}
	}

	public double getPsCsThreshold() {
		return _psCsThreshold;
	}

	public boolean getFirstTurnRight(final PatternKind patternKind,
			final boolean firstTurnRight0, final boolean randomize) {
		switch (patternKind) {
		case PSCS:
			if (!_psCsMayFirstTurnRight) {
				return false;
			}
			if (!_psCsMayFirstTurnLeft) {
				return true;
			}
			break;
		case SS:
			if (!_ssMayFirstTurnRight) {
				return false;
			}
			if (!_ssMayFirstTurnLeft) {
				return true;
			}
			break;
		case VS:
			if (!_vsMayFirstTurnRight) {
				return false;
			}
			if (!_vsMayFirstTurnLeft) {
				return true;
			}
			break;
		default:
		}
		final boolean firstTurnRight =
				randomize ? _firstTurnRightRandom.nextBoolean() : firstTurnRight0;
		return firstTurnRight;
	}

	public String getPlannerFilePath() {
		return _plannerFilePath;
	}

	public String getStashedPlannerModelPath() {
		return _stashedPlannerModelPath;
	}

	/**
	 * In Planner, there is no "outFile" specified. Hence, we dump where the
	 * input file is.
	 */
	private String getCorePath() {
		final String corePath =
				_plannerFilePath.substring(0, _plannerFilePath.length() - 4);
		return corePath;
	}

	public String getPlannerDashboardTablesPath() {
		final String corePath = getCorePath();
		return corePath + "_PlannerDashboardTables.xml";
	}

	public String getExternalReportPath() {
		final String corePath = getCorePath();
		return corePath + "_ExternalReport.xml";
	}

	public String getEvalReportPath() {
		final String corePath = getCorePath();
		return corePath + "_EvalReport.xml";
	}

	public String getResultFilePath() {
		final String corePath = getCorePath();
		return corePath + "_result.xml";
	}

	/**
	 * Returns the directory path, the echo file name, and the file name of
	 * the used properties.
	 */
	String[] getEchoFileNames() {
		final File echoDir = AbstractOutFilesManager.GetEngineFilesDir(_simCase,
				_plannerFilePath, "PlanEcho");
		final String echoDirPath = StringUtilities.getCanonicalPath(echoDir);
		if (echoDirPath == null) {
			MainRunner.HandleFatal(_simCase,
					new RuntimeException(new IOException("Find EchoFileDirPath")));
			return null;
		}
		final String stashedPlanFilePath = _stashedPlannerModelPath;
		final String stashedPlanFileName =
				new File(stashedPlanFilePath).getName();
		final int lastIndex =
				stashedPlanFileName.lastIndexOf(SimCaseManager._PlanEndingLc);
		final String stashedEchoFileName =
				stashedPlanFileName.substring(0, lastIndex) + "-echo.xml";
		final String stashedSimPropertiesFileName =
				stashedPlanFileName.substring(0, lastIndex) + "-Plan.properties";
		return new String[] { echoDirPath, stashedEchoFileName,
				stashedSimPropertiesFileName };
	}

	/** Very similar to the result file name. */
	public String getEvalFileName() {
		final String corePath = getCorePath();
		String returnValue = corePath + "-Eval.xml";
		final String returnValueLc = returnValue.toLowerCase();
		if (!returnValueLc.endsWith("-plan-eval.xml")) {
			return returnValue;
		}
		returnValue = returnValue.substring(0,
				returnValue.length() - "-plan-eval.xml".length()) + "-Eval.xml";
		return returnValue;
	}

	public boolean mayOverlap(final PatternVariable pv0,
			final PatternVariable pv1, final boolean forOptn) {
		if (pv0 == null || pv1 == null) {
			return false;
		}
		if (PatternVariable._ByGrandOrd.compare(pv0, pv1) == 0) {
			return true;
		}
		/**
		 * If they're both frozen then, for optimization purposes, they may
		 * overlap.
		 */
		if (forOptn) {
			if (pv0.getPermanentFrozenPvValue() != null &&
					pv1.getPermanentFrozenPvValue() != null) {
				return true;
			}
		}
		/** For right now, we don't worry about PvSeq-PVs. */
		if (pv0.getPvSeq() != null || pv1.getPvSeq() != null) {
			return true;
		}
		/** If this pair is mentioned in the exceptions, that trumps. */
		final String pv0Id = pv0.getId();
		final String pv1Id = pv1.getId();
		final String[] forLookingUp = new String[] { pv0Id, pv1Id };
		if (pv0Id.compareTo(pv1Id) > 0) {
			final String s = forLookingUp[0];
			forLookingUp[0] = forLookingUp[1];
			forLookingUp[1] = s;
		}
		final String[] incumbent = _overlapExceptions.floor(forLookingUp);
		if (incumbent != null) {
			final String pv0Idx = incumbent[0];
			if (pv0Idx.equals(forLookingUp[0])) {
				final String pv1Idx = incumbent[1];
				if (pv1Idx.equals(forLookingUp[1])) {
					return Boolean.parseBoolean(incumbent[2]);
				}
			}
		}
		return defaultMayOverlap(pv0, pv1);
	}

	public TreeSet<String[]> getOverlapExceptions() {
		return _overlapExceptions;
	}

	public int getNPttrnVbls() {
		return _pttrnVbls.length;
	}

	public void toggleAndReactToIsActiveToggle(final PatternVariable pv)
			throws Exception {
		final SolversManager solversManager =
				pv.getPlannerModel().getPlanner().getSolversManager();
		final boolean oldIsActive = pv.isActive();
		pv.setIsActive(!oldIsActive);
		if (oldIsActive != pv.isActive()) {
			solversManager.reactToChangedActiveSet();
		}
	}

	public int getNFloaters() {
		int nFloaters = 0;
		final int nPttrnVbls = getNPttrnVbls();
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PatternVariable pv = _pttrnVbls[grandOrd];
			final PvValue permanentlyFrozenPvValue =
					pv.getPermanentFrozenPvValue();
			if (permanentlyFrozenPvValue == null) {
				++nFloaters;
			}
		}
		return nFloaters;
	}

	public void setUserFrozen(final SolversManager solversManager,
			final PatternVariable pv, final PvValue pvValue) throws Exception {
		/** Can't do anything with something that's permanently frozen. */
		final boolean isPermanentlyFrozen =
				pv.getPermanentFrozenPvValue() != null;
		if (isPermanentlyFrozen) {
			return;
		}
		/** Not permanently frozen. Is it userFrozen? */
		final boolean isUserFrozen = pv.getUserFrozenPvValue() != null;
		if (isUserFrozen == (pvValue != null)) {
			/**
			 * Trying to freeze something that is already frozen, or release
			 * something that is not frozen. Either way, do nothing.
			 */
			return;
		}
		pv.setUserFrozenPvValue(pvValue);
		solversManager.reactToUserFreezeOrUnfreeze();
	}

	/** Manage overlap exceptions. */
	private static boolean defaultMayOverlap(final PatternVariable pv0,
			final PatternVariable pv1) {
		final PvType pvType0 = pv0.getPvType();
		final PvType pvType1 = pv1.getPvType();
		if (pvType0.isAircraft() != pvType1.isAircraft()) {
			return true;
		}
		final PvSeq pvSeq0 = pv0.getPvSeq();
		final PvSeq pvSeq1 = pv1.getPvSeq();
		if (pvSeq0 != null && pvSeq0 == pvSeq1) {
			/** Part of same non-null PvSeq. */
			return true;
		}
		return false;
	}

	public PatternVariable grandOrdToPv(final int grandOrd) {
		return 0 <= grandOrd && grandOrd < _pttrnVbls.length ?
				_pttrnVbls[grandOrd] : null;
	}

	public void setIsActiveForAll(final boolean isActive) throws Exception {
		/** If something changes, ... */
		boolean change = false;
		final int nPttrnVbls = getNPttrnVbls();
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PatternVariable pv = _pttrnVbls[grandOrd];
			final boolean oldIsActive = pv.isActive();
			final PvValue permanentlyFrozenPvValue =
					pv.getPermanentFrozenPvValue();
			if (permanentlyFrozenPvValue != null && oldIsActive) {
				/** Cannot deactivate a permanently frozen one. */
				continue;
			}
			if (oldIsActive != isActive) {
				pv.setIsActive(isActive);
				change = true;
			}
		}
		if (change) {
			getPlanner().getSolversManager().reactToChangedActiveSet();
		}
	}

	public boolean hasActiveFloater() {
		synchronized (_pttrnVbls) {
			final int nPttrnVbls = getNPttrnVbls();
			for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
				final PatternVariable pv = _pttrnVbls[grandOrd];
				if (pv.getCanUserMove()) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean nothingIsActive() {
		synchronized (_pttrnVbls) {
			final int nPttrnVbls = getNPttrnVbls();
			for (int k = 0; k < nPttrnVbls; ++k) {
				final PatternVariable pv = _pttrnVbls[k];
				if (pv.isActive()) {
					return false;
				}
			}
		}
		return true;
	}

	public boolean isUserCurrent(final PvValueArrayPlus plus) {
		final int nPttrnVbls = getNPttrnVbls();
		synchronized (_pttrnVbls) {
			for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
				final PvValue pvValue = plus.getPvValue(grandOrd);
				final PatternVariable pv = _pttrnVbls[grandOrd];
				if (pvValue != null) {
					if (!pv.isActive()) {
						return false;
					}
					final PvValue userFrozenPvValue = pv.getUserFrozenPvValue();
					if (userFrozenPvValue != null && userFrozenPvValue != pvValue) {
						return false;
					}
				} else {
					if (pv.isActive()) {
						return false;
					}
				}
			}
			return true;
		}
	}

	public PatternVariable[] getActiveSetFor(final PvValueArrayPlus plus) {
		final int nPttrnVbls = getNPttrnVbls();
		final PatternVariable[] returnValue = new PatternVariable[nPttrnVbls];
		Arrays.fill(returnValue, null);
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PvValue pvValue = plus.getPvValue(grandOrd);
			if (pvValue != null) {
				final PatternVariable pv = pvValue.getPv();
				returnValue[pv.getGrandOrd()] = pv;
			}
		}
		return returnValue;
	}

	public PatternVariable[] getActiveSet() {
		final int nPttrnVbls = getNPttrnVbls();
		final PatternVariable[] returnValue = new PatternVariable[nPttrnVbls];
		Arrays.fill(returnValue, null);
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PatternVariable pv = _pttrnVbls[grandOrd];
			if (pv.isActive()) {
				returnValue[grandOrd] = pv;
			}
		}
		return returnValue;
	}

	public boolean isConsistentWithIsActiveSet(final PvValueArrayPlus plus) {
		final int nPttrnVbls = getNPttrnVbls();
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PatternVariable pv = grandOrdToPv(grandOrd);
			final boolean isActive0 = pv.isActive();
			final PvValue pvValue = plus.getPvValue(grandOrd);
			final boolean isActive1 = pvValue != null;
			if (isActive0 != isActive1) {
				return false;
			}
		}
		return true;
	}

	public PatternVariable[] getNonNullIsActivePttrnVbls() {
		final int nPttrnVbls = getNPttrnVbls();
		final ArrayList<PatternVariable> pvList = new ArrayList<>();
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PatternVariable pv = _pttrnVbls[grandOrd];
			if (pv.isActive()) {
				pvList.add(pv);
			}
		}
		final int nIsActive = pvList.size();
		final PatternVariable[] returnValue = new PatternVariable[nIsActive];
		for (int k = 0; k < nIsActive; ++k) {
			returnValue[k] = pvList.get(k);
		}
		return returnValue;
	}

	public static void addTsBoxInfoToElement(final PatternVariable pv,
			final MyStyle myStyle, final Element pvElt) {
		final boolean offEarth = myStyle == null || myStyle.onMars();
		pvElt.setAttribute("onMars", offEarth ? "true" : "false");
		final PatternKind patternKind = pv.getPatternKind();
		final double rawSearchKts = pv.getRawSearchKts();
		final double minTsNmi = pv.getMinTsNmi();
		final LatLng3 centralLatLng = myStyle.getCenter();
		final double orntn = myStyle.computeOrntn(rawSearchKts, minTsNmi);
		final double tsNmi = myStyle.computeTsNmi(rawSearchKts, minTsNmi);
		final long cstRefSecs;
		final int searchDurationSecs;
		if (pv.getPvSeq() == null) {
			cstRefSecs = pv.getPvCstRefSecs();
			searchDurationSecs = pv.getPvRawSearchDurationSecs();
		} else {
			cstRefSecs = myStyle.getCstRefSecs();
			searchDurationSecs = myStyle.getSearchDurationSecs();
		}
		pvElt.setAttribute("patternKind", patternKind.outsideName());
		pvElt.setAttribute("cst", TimeUtilities.formatTime(cstRefSecs, false));
		final String durationMinsString = String.format("%d mins",
				(int) Math.round(searchDurationSecs / 60d));
		pvElt.setAttribute("duration", durationMinsString);
		final boolean firstTurnRight = myStyle.getFirstTurnRight();
		pvElt.setAttribute("orientation",
				orntn + " degrees clockwise from north");
		pvElt.setAttribute("centerPointLat",
				LsFormatter.StandardFormatForLatOrLng(centralLatLng.getLat()));
		pvElt.setAttribute("centerPointLng",
				LsFormatter.StandardFormatForLatOrLng(centralLatLng.getLng()));
		pvElt.setAttribute("firstTurnRight",
				(firstTurnRight) ? "true" : "false");
		pvElt.setAttribute("trackSpacing",
				LsFormatter.StandardFormat(tsNmi) + " NM");
		final int nSearchLegs =
				myStyle.computeNSearchLegs(rawSearchKts, minTsNmi);
		pvElt.setAttribute("nSearchLegs", "" + nSearchLegs);
		final double lengthNmi =
				myStyle.computeLengthNmi(rawSearchKts, minTsNmi);
		pvElt.setAttribute("length",
				LsFormatter.StandardFormat(lengthNmi) + " NM");
		if (patternKind.isPsCs()) {
			/** For LP, write out width, pathType, specLen, and specWid. */
			final double widthNmi =
					myStyle.computeWidthNmi(rawSearchKts, minTsNmi);
			pvElt.setAttribute("width",
					LsFormatter.StandardFormat(widthNmi) + " NM");
			pvElt.setAttribute("pathType",
					myStyle.computePs(rawSearchKts, minTsNmi) ? "PS" : "CS");
			final double sllNmi = myStyle.computeSllNmi(rawSearchKts, minTsNmi);
			pvElt.setAttribute("searchLegLength", sllNmi + " NM");
			final double specAlongNmi = myStyle.getSpecAlongNmi();
			final double specAcrossNmi = Math.abs(myStyle.getSpecSgndAcrossNmi());
			final double specLenNmi = Math.max(specAlongNmi, specAcrossNmi);
			final double specWidNmi = Math.min(specAlongNmi, specAcrossNmi);
			pvElt.setAttribute("specLength",
					LsFormatter.StandardFormat(specLenNmi) + " NM");
			pvElt.setAttribute("specWidth",
					LsFormatter.StandardFormat(specWidNmi) + " NM");
		} else if (patternKind.isSs()) {
			/** For SS, write out specLen. */
			final double specAcrossNmi = myStyle.getSpecSgndAcrossNmi();
			final double specLenNmi = Math.abs(specAcrossNmi);
			pvElt.setAttribute("specLength",
					LsFormatter.StandardFormat(specLenNmi) + " NM");
			final int nHalfLaps =
					myStyle.computeNHalfLaps(rawSearchKts, minTsNmi);
			pvElt.setAttribute("nHalfLaps", "" + nHalfLaps);
		} else if (patternKind.isVs()) {
			/** For VS, there's nothing else to write. */
		}
	}

	public File getEngineFilesResultFile(final SimCaseManager.SimCase simCase,
			final String suffix) {
		final String stashedPlannerModelName =
				new File(_stashedPlannerModelPath).getName();
		final int lastIndex =
				stashedPlannerModelName.lastIndexOf(SimCaseManager._PlanEndingLc);
		final String stashedResultFileName =
				stashedPlannerModelName.substring(0, lastIndex) + suffix;
		final File stashDir = getEngineFilesResultDir(simCase);
		final String filePathToDumpTo = StringUtilities
				.getCanonicalPath(new File(stashDir, stashedResultFileName));
		return new File(filePathToDumpTo);
	}

	private File getEngineFilesResultDir(
			final SimCaseManager.SimCase simCase) {
		final File stashDir = AbstractOutFilesManager.GetEngineFilesDir(simCase,
				_plannerFilePath, "PlanResult");
		return stashDir;
	}

	public long getOriginalSeed() {
		return _originalSeed;
	}

	public long getLatestSeed() {
		return _latestSeed;
	}

	public boolean getAllPttrnVblesAreInitialized() {
		return _allPttrnVblsAreInitialized;
	}

	public ExtraGraphicsClass getExtraGraphicsObject() {
		return _extraGraphicsObject;
	}

	public int getNPvSeqs() {
		return _pvSeqs == null ? 0 : _pvSeqs.length;
	}

	public PvSeq getPvSeq(final int pvSeqOrd) {
		return (pvSeqOrd < 0 || _pvSeqs == null || pvSeqOrd >= _pvSeqs.length) ?
				null : _pvSeqs[pvSeqOrd];
	}

	public PatternVariable[] getPttrnVbls() {
		return _pttrnVbls.clone();
	}

	public int[] computeGrandOrdToIdx(final PvValue[] pvValues) {
		final int nPttrnVbls = _pttrnVbls.length;
		final int[] returnValue = new int[nPttrnVbls];
		Arrays.fill(returnValue, -1);
		final int nPvValues = pvValues == null ? 0 : pvValues.length;
		for (int k = 0; k < nPvValues; ++k) {
			final PvValue pvValue = pvValues[k];
			if (pvValue != null) {
				returnValue[pvValue.getPv().getGrandOrd()] = k;
			}
		}
		return returnValue;
	}

	public Model getSimModel() {
		return _simModel;
	}

	public void updateLatestSeed() {
		_latestSeed = new Randomx(_latestSeed, /* nToAdvance= */4).nextInt();
	}

	public PvValue[] getPermanentlyFrozenPvValues() {
		final int nPttrnVbls = getNPttrnVbls();
		PvValue[] permanentlyFrozenPvValues = null;
		for (int iPass = 0; iPass < 2; ++iPass) {
			int nPermanentlyFrozen = 0;
			for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
				final PatternVariable pv = _pttrnVbls[grandOrd];
				final PvValue permanentlyFrozenPvValue =
						pv.getPermanentFrozenPvValue();
				if (permanentlyFrozenPvValue != null) {
					if (iPass == 1) {
						permanentlyFrozenPvValues[nPermanentlyFrozen] =
								permanentlyFrozenPvValue;
					}
					++nPermanentlyFrozen;
				}
			}
			if (iPass == 0) {
				permanentlyFrozenPvValues = new PvValue[nPermanentlyFrozen];
			}
		}
		return permanentlyFrozenPvValues;
	}

	public PatternVariable[] getFloaters() {
		final int nPttrnVbls = getNPttrnVbls();
		PatternVariable[] floaters = null;
		for (int iPass = 0; iPass < 2; ++iPass) {
			int nFloaters = 0;
			for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
				final PatternVariable pv = _pttrnVbls[grandOrd];
				final PvValue permanentlyFrozenPvValue =
						pv.getPermanentFrozenPvValue();
				if (permanentlyFrozenPvValue == null) {
					if (iPass == 0) {
						++nFloaters;
					} else {
						floaters[nFloaters++] = pv;
					}
				}
			}
			if (iPass == 0) {
				floaters = new PatternVariable[nFloaters];
			}
		}
		return floaters;
	}

	public boolean isEvalRun() {
		final int nFloaters = getNFloaters();
		if (nFloaters == 0) {
			return true;
		}
		final int nPttrnVbls = getNPttrnVbls();
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PatternVariable pv = grandOrdToPv(grandOrd);
			if (pv.getInitialPvValue() == null) {
				return false;
			}
		}
		return getPlannerTimeSecs() == 0;
	}

	public String getXmlSimiPropertiesFilePath() {
		return _xmlSimPropertiesFilePath;
	}

	long getSimulationStartRefSecs() {
		return _simStartRefSecs;
	}

	long getSimulationEndRefSecs() {
		return _simEndRefSecs;
	}

	public void addViz1ObjectTypeId(final int objectTypeId) {
		_viz1ObjectTypeIds.add(objectTypeId);
	}

	public void addViz2ObjectTypeId(final int objectTypeId) {
		_viz2ObjectTypeIds.add(objectTypeId);
	}

	public static PlannerModel getPlannerModel(final PvValue[] pvValues) {
		if (pvValues == null) {
			return null;
		}
		for (final PvValue pvValue : pvValues) {
			if (pvValue != null) {
				return pvValue.getPv().getPlannerModel();
			}
		}
		return null;
	}

	public static PlannerModel getPlannerModel(
			final PatternVariable[] pttrnVbls) {
		if (pttrnVbls == null) {
			return null;
		}
		for (final PatternVariable pttrnVbl : pttrnVbls) {
			if (pttrnVbl != null) {
				return pttrnVbl.getPlannerModel();
			}
		}
		return null;
	}

}
