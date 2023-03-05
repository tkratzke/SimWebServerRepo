/*
 * Created on Nov 19, 2003
 */
package com.skagit.sarops.model;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.io.FilenameUtils;
import org.apache.poi.hssf.util.HSSFColor.HSSFColorPredefined;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.Element;

import com.skagit.sarops.AbstractOutFilesManager;
import com.skagit.sarops.environment.BoxDefinition;
import com.skagit.sarops.environment.ConstantCurrentsUvGetter;
import com.skagit.sarops.environment.ConstantWindsUvGetter;
import com.skagit.sarops.environment.CurrentsUvGetter;
import com.skagit.sarops.environment.NetCdfCurrentsUvGetter;
import com.skagit.sarops.environment.NetCdfWindsUvGetter;
import com.skagit.sarops.environment.WindsUvGetter;
import com.skagit.sarops.model.io.ModelReader;
import com.skagit.sarops.model.io.ModelReader.ReaderException;
import com.skagit.sarops.model.io.ModelWriter;
import com.skagit.sarops.model.preDistressModel.sail.SailData;
import com.skagit.sarops.simCaseManager.MainRunner;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.sarops.util.SimGlobalStrings;
import com.skagit.sarops.util.wangsness.Thresholds;
import com.skagit.util.CombinatoricTools;
import com.skagit.util.Constants;
import com.skagit.util.LsFormatter;
import com.skagit.util.MathX;
import com.skagit.util.PermutationTools;
import com.skagit.util.StaticUtilities;
import com.skagit.util.StringUtilities;
import com.skagit.util.TimeUtilities;
import com.skagit.util.cdf.area.Area;
import com.skagit.util.cdf.area.Polygon;
import com.skagit.util.etopo.Etopo;
import com.skagit.util.gshhs.CircleOfInterest;
import com.skagit.util.gshhs.GshhsReader;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.TangentCylinder;
import com.skagit.util.poiUtils.PoiUtils;
import com.skagit.util.randomx.Randomx;
import com.skagit.util.randomx.TimeDistribution;
import com.skagit.util.shorelineFinder.ShorelineFinder;

public class Model {

	public static final int _WildCard = Integer.MIN_VALUE;
	public static final String _WildCardString = "*";

	final private static double _NmiToR = MathX._NmiToR;

	final public static String _DynamicEnvIndicator = "DYNAMIC";
	final public static String _EngineFilesName = "EngineFiles";

	final public static String _2Closest = "2Closest";
	final public static String _3Closest = "3Closest";
	final public static String _CenterDominated = "centerDominated";
	final public static String _UseAllStrips = "useAllStrips";

	/** Reading/Writing basics: */
	final private String _simFilePath;
	private String _xmlSimPropertiesFilePath;
	private String _particlesFilePath;
	/** When we read in the xml, we populate the following. */
	private TreeSet<ModelReader.StringPlus> _stringPluses;

	/** Scenarii and DebrisSightings: */
	final private ArrayList<Scenario> _scenarii;
	final private ArrayList<DebrisSighting> _debrisSightings;

	private SotWithDbl.OriginatingSotWithWt _originatingSotWithWt;

	final private ArrayList<Sortie> _sorties;
	final private ArrayList<FixHazard> _fixHazards;
	final private ArrayList<SearchObjectType> _searchObjectTypes;
	final private ArrayList<DebrisObjectType> _debrisObjectTypes;

	/** Critical times and counts. */
	private long _firstOutputRefSecs;
	private long _lastOutputRefSecs;
	private String _modeName = null;
	private long _monteCarloSecs;
	private int _nMonteCarloStepsPerDump;
	private int _nParticlesPerScenario;
	private long[] _fullRefSecsExtent = null;

	/** Environmental data: */
	private String _windsFilePath;
	private String _windsFileType;
	private String _currentsFilePath;
	private String _currentsFileType;
	private Element _windsElement;
	private Element _currentsElement;
	private CurrentsUvGetter _currentsUvGetter;
	private WindsUvGetter _windsUvGetter;
	private Etopo _etopo;
	private ShorelineFinder _shorelineFinder;
	private Extent _extent;
	private CircleOfInterest _coi;
	private TangentCylinder _tangentCylinder = null;
	private boolean _riverine;
	private String _interpolationMode = null;
	private boolean _realExtentSet = false;
	final private ArrayList<BoxDefinition> _currentsBoxDefinitions;
	final private ArrayList<BoxDefinition> _windsBoxDefinitions;
	/** Regarding Stickies: */
	private double _proportionOfSticky;
	private BitSet _stickies;

	/** Misc _model data. */
	private long _randomSeed;
	private boolean _excludeInitialLandDraws;
	private boolean _excludeInitialWaterDraws;
	private boolean _reverseDrift;
	final private TreeMap<ParticleIndexes, OutOfAreaIncident> _outOfAreaIncidents;
	private double[] _containmentRequests;

	/** Misc auxiliary data. */
	private boolean _writeOcTables;
	private boolean _runStudy = false;
	private boolean _dumpGshhsKmz = false;
	final private boolean _displayOnly;
	final private ExtraGraphicsClass _extraGraphicsObject;
	private String _stashedSimFilePath;

	/** For dumping particles to excel. */
	private int _nForExcelDump;

	/** For Sorties Excel dump: */
	private XSSFWorkbook _sortiesWorkbook;
	private CellStyle _cellStyle1;
	private CellStyle _cellStyle2a;
	private CellStyle _cellStyle2b;
	private CellStyle _cellStyle2c;

	public Model(final SimCaseManager.SimCase simCase, final String modelFilePath, final boolean displayOnly) {
		_displayOnly = displayOnly;

		/** Scenarii and DebrisSightings: */
		if (_displayOnly) {
			_scenarii = null;
			_debrisSightings = null;
		} else {
			_scenarii = new ArrayList<>();
			_debrisSightings = new ArrayList<>();
		}

		_sorties = new ArrayList<>();
		_fixHazards = new ArrayList<>();
		_searchObjectTypes = new ArrayList<>();
		_debrisObjectTypes = new ArrayList<>();
		_outOfAreaIncidents = new TreeMap<>();

		_writeOcTables = false;
		_extraGraphicsObject = new ExtraGraphicsClass();
		_extent = null;
		_coi = null;
		_nForExcelDump = 0;
		_stashedSimFilePath = null;
		_reverseDrift = false;
		_riverine = false;
		_simFilePath = StringUtilities.cleanUpFilePath(modelFilePath);
		_xmlSimPropertiesFilePath = null;
		_shorelineFinder = null;
		_currentsUvGetter = null;
		_windsUvGetter = null;
		_currentsBoxDefinitions = new ArrayList<>();
		_windsBoxDefinitions = new ArrayList<>();
		_proportionOfSticky = 1d;
		_stickies = null;
	}

	public void setXmlSimPropertiesFilePath(final String xmlSimPropertiesFilePath) {
		_xmlSimPropertiesFilePath = xmlSimPropertiesFilePath;
	}

	public String getXmlSimPropertiesFilePath() {
		return _xmlSimPropertiesFilePath;
	}

	public void add(final Sortie sortie) {
		_sorties.add(sortie);
	}

	public SearchObjectType sotIdToSot(final int sotId) {
		for (int nSots = _searchObjectTypes.size(), k = 0; k < nSots; ++k) {
			final SearchObjectType sot = _searchObjectTypes.get(k);
			if (sot.getId() == sotId) {
				return sot;
			}
		}
		return null;
	}

	public ArrayList<SearchObjectType> getSearchObjectTypes() {
		return _searchObjectTypes;
	}

	public DebrisObjectType dotIdToDot(final int dotId) {
		for (int nDots = _debrisObjectTypes.size(), k = 0; k < nDots; ++k) {
			final DebrisObjectType dot = _debrisObjectTypes.get(k);
			if (dot.getId() == dotId) {
				return dot;
			}
		}
		return null;
	}

	public ArrayList<DebrisObjectType> getDebrisObjectTypes() {
		return _debrisObjectTypes;
	}

	public ArrayList<Sortie> getSorties() {
		return _sorties;
	}

	public long getLastOutputRefSecs() {
		return _lastOutputRefSecs;
	}

	public long getFirstOutputRefSecs() {
		return _firstOutputRefSecs;
	}

	public void setLastOutputRefSecs(final long lastOutputRefSecs) {
		_lastOutputRefSecs = lastOutputRefSecs;
	}

	public void setFirstOutputRefSecs(final long firstOutputRefSecs) {
		_firstOutputRefSecs = firstOutputRefSecs;
	}

	public boolean getExcludeInitialLandDraws() {
		if (hasSailData()) {
			return true;
		}
		return _excludeInitialLandDraws;
	}

	public void setExcludeInitialLandDraws(final boolean excludeInitialLandDraws) {
		_excludeInitialLandDraws = excludeInitialLandDraws;
	}

	public boolean getExcludeInitialWaterDraws() {
		if (hasSailData()) {
			return false;
		}
		return _excludeInitialWaterDraws;
	}

	public void setExcludeInitialWaterDraws(final boolean excludeInitialWaterDraws) {
		_excludeInitialWaterDraws = excludeInitialWaterDraws;
	}

	public long getMonteCarloSecs() {
		return _monteCarloSecs;
	}

	public void setMonteCarloSecs(final long monteCarloSecs) {
		_monteCarloSecs = monteCarloSecs;
	}

	public int getNMonteCarloStepsPerDump() {
		return _nMonteCarloStepsPerDump;
	}

	public void setNMonteCarloStepsPerDump(final int nMonteCarloStepsPerDump) {
		_nMonteCarloStepsPerDump = nMonteCarloStepsPerDump;
	}

	public int getNParticlesPerScenario() {
		return _nParticlesPerScenario;
	}

	public int getTotalNParticles() {
		return getNScenarii() * _nParticlesPerScenario;
	}

	public void setNParticlesPerScenario(final int nParticlesPerScenario) {
		_nParticlesPerScenario = nParticlesPerScenario;
	}

	public boolean deepEquals(final SimCaseManager.SimCase simCase, final Model model) {
		return deepEquals(simCase, model, /* checkEnvironmentalFiles= */true);
	}

	public boolean deepEquals(final SimCaseManager.SimCase simCase, final Model model,
			final boolean checkEnvironmentalFiles) {
		if ((_firstOutputRefSecs != model._firstOutputRefSecs) || (_randomSeed != model._randomSeed)
				|| (_nForExcelDump != model._nForExcelDump) || (_lastOutputRefSecs != model._lastOutputRefSecs)) {
			return false;
		}
		if (_monteCarloSecs != model._monteCarloSecs) {
			return false;
		}
		if (_nParticlesPerScenario != model._nParticlesPerScenario) {
			return false;
		}
		if (_nMonteCarloStepsPerDump != model._nMonteCarloStepsPerDump) {
			return false;
		}
		if (_riverine != model._riverine) {
			return false;
		}
		if (_proportionOfSticky != model._proportionOfSticky) {
			return false;
		}
		if (_interpolationMode.compareTo(model._interpolationMode) != 0) {
			return false;
		}
		if (!scenariiAreDeepEqual(model)) {
			return false;
		}
		if (!debrisSightingsAreDeepEqual(model)) {
			return false;
		}
		if (_sorties.size() != model._sorties.size()) {
			return false;
		}
		final Iterator<Sortie> sortieIterator = _sorties.iterator();
		final Iterator<Sortie> comparedSortieIterator = model._sorties.iterator();
		while (sortieIterator.hasNext() && comparedSortieIterator.hasNext()) {
			final Sortie sortie = sortieIterator.next();
			final Sortie comparedSortie = comparedSortieIterator.next();
			if (sortie.deepCompareTo(comparedSortie) != 0) {
				return false;
			}
		}
		if (!_originatingSotWithWt.deepEquals(model._originatingSotWithWt)) {
			return false;
		}
		if (_searchObjectTypes.size() != model._searchObjectTypes.size()) {
			return false;
		}
		final int nSots = _searchObjectTypes.size();
		final int nComparedSots = model._searchObjectTypes.size();
		if (nSots != nComparedSots) {
			return false;
		}
		for (int k = 0; k < nSots; ++k) {
			final SearchObjectType sot = _searchObjectTypes.get(k);
			final SearchObjectType comparedSot = model._searchObjectTypes.get(k);
			if (!sot.deepEquals(comparedSot)) {
				return false;
			}

		}
		final int nDebrisObjectTypes = _debrisObjectTypes.size();
		if (nDebrisObjectTypes != model._debrisObjectTypes.size()) {
			return false;
		}
		for (int k = 0; k < nDebrisObjectTypes; ++k) {
			final DebrisObjectType dot = _debrisObjectTypes.get(k);
			final DebrisObjectType comparedDot = model._debrisObjectTypes.get(k);
			if (!dot.deepEquals(comparedDot)) {
				return false;
			}
		}
		if (_fixHazards.size() != model._fixHazards.size()) {
			return false;
		}
		final Iterator<FixHazard> fixHazardIterator = _fixHazards.iterator();
		final Iterator<FixHazard> comparedFixHazardIterator = model._fixHazards.iterator();
		while (fixHazardIterator.hasNext()) {
			final FixHazard fixHazard = fixHazardIterator.next();
			final FixHazard comparedFixHazard = comparedFixHazardIterator.next();
			if (!fixHazard.deepEquals(comparedFixHazard)) {
				return false;
			}
		}
		if (checkEnvironmentalFiles) {
			if ((!_windsFilePath.equals(model._windsFilePath)) || (!_windsFileType.equals(model._windsFileType))
					|| (!_currentsFilePath.equals(model._currentsFilePath))
					|| (!_currentsFileType.equals(model._currentsFileType))
					|| (!_particlesFilePath.equals(model._particlesFilePath))) {
				return false;
			}
			if ("CONSTANT".equalsIgnoreCase(_windsFileType)) {
				if (!compareConstantDescription(simCase, _windsElement, model._windsElement)) {
					return false;
				}
			}
			if ("CONSTANT".equalsIgnoreCase(_currentsFileType)) {
				if (!compareConstantDescription(simCase, _currentsElement, model._currentsElement)) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean compareConstantDescription(final SimCaseManager.SimCase simCase, final Element element0,
			final Element element1) {
		try {
			final double speed0 = ModelReader.getDouble(simCase, element0, "speed", " kts", 0d,
					/* stringPluses= */null);
			String confidenceString = ModelReader.getString(simCase, element0, ModelReader._ConfidenceAtt, "LOW",
					/* stringPluses= */null);
			final boolean high0 = "HIGH".compareToIgnoreCase(confidenceString) == 0;
			final double speed1 = ModelReader.getDouble(simCase, element1, "speed", " kts", 0d,
					/* stringPluses= */null);
			confidenceString = ModelReader.getString(simCase, element1, ModelReader._ConfidenceAtt, "LOW",
					/* stringPluses= */null);
			final boolean high1 = "HIGH".compareToIgnoreCase(confidenceString) == 0;
			final double direction0 = ModelReader.getDouble(simCase, element0, "dir", " T", 0d,
					/* stringPluses= */null);
			final double direction1 = ModelReader.getDouble(simCase, element1, "dir", " T", 0d,
					/* stringPluses= */null);
			return (speed0 == speed1 && direction0 == direction1 && high0 == high1);
		} catch (final Exception e) {
			return false;
		}
	}

	public static class OutOfAreaIncident implements Comparable<OutOfAreaIncident> {
		private long _refSecs;
		final public ParticleIndexes _prtclIndxs;
		private LatLng3 _latLng;
		private String _typeOfParticle;
		public int _numberOfAdditionalIncidents = 0;

		public OutOfAreaIncident(final long refSecs, final ParticleIndexes prtclIndxs, final String typeOfParticle,
				final LatLng3 latLng) {
			_refSecs = refSecs;
			_latLng = latLng;
			_prtclIndxs = prtclIndxs;
			_typeOfParticle = typeOfParticle;
		}

		@Override
		public int compareTo(final OutOfAreaIncident outOfAreaIncident) {
			if (_refSecs < outOfAreaIncident._refSecs) {
				return -1;
			} else if (_refSecs > outOfAreaIncident._refSecs) {
				return -1;
			} else {
				return 0;
			}
		}

		public String getTypeOfParticle() {
			return _typeOfParticle;
		}

		public LatLng3 getLatLng() {
			return _latLng;
		}

		public long getRefSecs() {
			return _refSecs;
		}
	}

	public SearchObjectType addSearchObjectType(final int id, final String name) {
		final SearchObjectType newOne = new SearchObjectType(id, name);
		final int idx = Collections.binarySearch(_searchObjectTypes, newOne);
		if (idx < 0) {
			_searchObjectTypes.add(newOne);
			Collections.sort(_searchObjectTypes);
			return newOne;
		}
		/** Old one existed. Return null to indicate an unsuccessful put. */
		return null;
	}

	public DebrisObjectType addDebrisObjectType(final int id, final String name) {
		final DebrisObjectType newOne = new DebrisObjectType(id, name);
		final int idx = Collections.binarySearch(_debrisObjectTypes, newOne);
		if (idx < 0) {
			_debrisObjectTypes.add(newOne);
			Collections.sort(_debrisObjectTypes);
			return newOne;
		}
		/** Old one existed. Return null to indicate an unsuccessful put. */
		return null;
	}

	public void setEnvironmentalData(final String windsFilePath, final String windsFileType, final Element windsElement,
			final String currentsFilePath, final String currentsFileType, final Element currentsElement) {
		_windsFilePath = StringUtilities.cleanUpFilePath(windsFilePath);
		_windsFileType = windsFileType;
		_windsElement = windsElement;
		_currentsFileType = currentsFileType;
		_currentsFilePath = StringUtilities.cleanUpFilePath(currentsFilePath);
		_currentsElement = currentsElement;
	}

	public String getCurrentsFilePath() {
		return _currentsFilePath;
	}

	public String getWindsFilePath() {
		return _windsFilePath;
	}

	public String getCurrentsFileType() {
		return _currentsFileType;
	}

	public String getWindsFileType() {
		return _windsFileType;
	}

	public String getParticlesFilePath() {
		return _particlesFilePath;
	}

	public String getParticlesFilePathCore() {
		return _particlesFilePath.substring(0, _particlesFilePath.lastIndexOf('.'));
	}

	public boolean getWriteOcTables() {
		return _writeOcTables;
	}

	public boolean getBuildSortiesWorkbook() {
		return _sortiesWorkbook != null;
	}

	public void setParticlesFilePath(final String particlesFilePath) {
		_particlesFilePath = StringUtilities.cleanUpFilePath(particlesFilePath);
	}

	public long getRandomSeed() {
		return _randomSeed;
	}

	public void setRandomSeed(final long longSeed) {
		_randomSeed = CombinatoricTools.longToInt(longSeed);
	}

	public int getNForExcelDump() {
		return _nForExcelDump;
	}

	public void setNForExcelDump(final int nForExcelDump) {
		_nForExcelDump = nForExcelDump;
	}

	public boolean check(final SimCaseManager.SimCase simCase) {
		if (getDisplayOnly()) {
			return true;
		}
		boolean result = true;
		if (_searchObjectTypes.size() == 0) {
			SimCaseManager.err(simCase, "No search object type defined");
			result = false;
		}
		for (final SearchObjectType searchObjectType : _searchObjectTypes) {
			if (searchObjectType != getOriginatingSotWithWt().getSot()) {
				result &= searchObjectType.check(simCase);
			}
		}
		result &= checkAndNormalizeScenarioWeights(simCase, this);
		final Iterator<Sortie> sortieIterator = _sorties.iterator();
		while (sortieIterator.hasNext()) {
			final Sortie sortie = sortieIterator.next();
			final Iterator<Sortie.Leg> legIterator = sortie.getDistinctInputLegs().iterator();
			while (legIterator.hasNext()) {
				final Sortie.Leg leg = legIterator.next();
				if (leg.getLegRefSecs1() < leg.getLegRefSecs0()) {
					final String startString = TimeUtilities.formatTime(leg.getLegRefSecs0(), true);
					final String stopString = TimeUtilities.formatTime(leg.getLegRefSecs1(), true);
					SimCaseManager.err(simCase, String.format("Invalid leg[%s→%s]", startString, stopString));
					result = false;
				}
			}
		}
		return result;
	}

	public TreeSet<ModelReader.StringPlus> setEnvironment(final SimCaseManager.SimCase simCase,
			final boolean stashEnvFiles, final boolean overwriteEnvFiles) {
		final TreeSet<ModelReader.StringPlus> stringPluses = new TreeSet<>(ModelReader._StringPlusComparator);
		if (_currentsUvGetter != null && _windsUvGetter != null) {
			return stringPluses;
		}
		synchronized (this) {
			if (_currentsUvGetter != null && _windsUvGetter != null) {
				return stringPluses;
			}

			/** Currents. */
			final String currentsConfidenceAttributeName;
			final String preDistressCurrentsConfidenceAttributeName;
			if (_riverine) {
				currentsConfidenceAttributeName = "standardDeviationForRIVERConfidence";
				preDistressCurrentsConfidenceAttributeName = "standardDeviationForPreDistressRIVERConfidence";
			} else {
				String currentsConfidenceString = null;
				String preDistressCurrentsConfidenceString = null;
				try {
					currentsConfidenceString = ModelReader.getStringNoDefault(simCase, _currentsElement,
							ModelReader._ConfidenceAtt, stringPluses);
					preDistressCurrentsConfidenceString = ModelReader.getStringWithBackupAttribute(simCase,
							_currentsElement, ModelReader._PreDistressConfidenceAtt, ModelReader._ConfidenceAtt,
							stringPluses);
				} catch (final ReaderException e) {
					currentsConfidenceString = null;
				}
				_currentsElement.setAttribute(ModelReader._ConfidenceAtt, currentsConfidenceString);
				_currentsElement.setAttribute(ModelReader._PreDistressConfidenceAtt,
						preDistressCurrentsConfidenceString);
				currentsConfidenceAttributeName = "standardDeviationFor" + currentsConfidenceString.toUpperCase()
						+ "Confidence";
				preDistressCurrentsConfidenceAttributeName = "standardDeviationForPreDistress"
						+ preDistressCurrentsConfidenceString.toUpperCase() + "Confidence";
			}

			long currentsHalfLifeSecs = 0;
			double currentsSd = Double.NaN;
			double preDistressCurrentsSd = Double.NaN;
			long preDistressCurrentsHalfLifeSecs = 0;
			try {
				currentsHalfLifeSecs = ModelReader.getInt(simCase, _currentsElement, "halfLife", " mins", stringPluses)
						* 60;
				preDistressCurrentsHalfLifeSecs = ModelReader.getInt(simCase, _currentsElement, "preDistressHalfLife",
						" mins", stringPluses) * 60;
				if (!_riverine) {
					currentsSd = ModelReader.getDouble(simCase, _currentsElement, currentsConfidenceAttributeName,
							" kts", stringPluses);
					preDistressCurrentsSd = ModelReader.getDouble(simCase, _currentsElement,
							preDistressCurrentsConfidenceAttributeName, " kts", stringPluses);
				}
			} catch (final ModelReader.ReaderException e) {
				SimCaseManager.err(simCase, String.format("Error in general currents description%s",
						StringUtilities.getStackTraceString(e)));
			}
			/** Override the confidence if we are riverine. */
			if (_riverine) {
				double riverStandardDeviation = Double.NaN;
				double preDistressRiverStandardDeviation = Double.NaN;
				try {
					riverStandardDeviation = ModelReader.getDouble(simCase, _currentsElement,
							currentsConfidenceAttributeName, "%", Double.NaN, stringPluses);
					preDistressRiverStandardDeviation = ModelReader.getDouble(simCase, _currentsElement,
							preDistressCurrentsConfidenceAttributeName, "%", riverStandardDeviation, stringPluses);
				} catch (final ModelReader.ReaderException ignored) {
					riverStandardDeviation = Double.NaN;
				}
				if (!Double.isNaN(riverStandardDeviation)) {
					currentsSd = riverStandardDeviation;
					preDistressCurrentsSd = preDistressRiverStandardDeviation;
				}
			}
			_currentsUvGetter = null;
			if ("constant".equals(_currentsFileType)) {
				double speed, direction;
				speed = direction = Double.NaN;
				try {
					speed = ModelReader.getDouble(simCase, _currentsElement, "speed", " kts", 0d, stringPluses);
					direction = ModelReader.getDouble(simCase, _currentsElement, "dir", " T", 0d, stringPluses);
				} catch (final ModelReader.ReaderException ignored) {
					SimCaseManager.err(simCase, String.format("Error in constant currents description%s",
							StringUtilities.getStackTraceString(ignored)));
				}
				_currentsUvGetter = new ConstantCurrentsUvGetter(this, speed, direction, currentsSd, currentsSd,
						currentsHalfLifeSecs, preDistressCurrentsHalfLifeSecs);
			}
			if (_currentsUvGetter == null && ("netcdf".equals(_currentsFileType) || "nc".equals(_currentsFileType)
					|| "rotating".equals(_currentsFileType)) //
			) {
				try {
					final double dU = currentsSd;
					final double altDU = preDistressCurrentsSd;
					double dV = currentsSd;
					double altDV = preDistressCurrentsSd;
					if (_riverine) {
						try {
							final double factor = ModelReader.getDouble(simCase, _currentsElement, "riverAspectRatio",
									"", stringPluses);
							dV = factor;
							altDV = dV;
						} catch (final ReaderException e) {
						}
					}
					if (_currentsUvGetter == null) {
						/** Normal. */
						SimCaseManager.out(simCase,
								String.format("\nBuilding Currents Reader from:%s", _currentsFilePath));
						_currentsUvGetter = new NetCdfCurrentsUvGetter(simCase, this, _currentsFilePath, dU, dV, altDU,
								altDV, currentsHalfLifeSecs, preDistressCurrentsHalfLifeSecs);
						if (stashEnvFiles) {
							ModelReader.stashEngineFile(simCase, _currentsFilePath, _simFilePath,
									/* suffixLc= */FilenameUtils.EXTENSION_SEPARATOR_STR
											+ FilenameUtils.getExtension(_currentsFilePath).toLowerCase(),
									"SimEnv", overwriteEnvFiles);
						}
					}
				} catch (final Exception e) {
					_currentsUvGetter = null;
					SimCaseManager.standardLogError(simCase, e, "Unable to build currents reader.");
				}

				/** Now that we have it, "close it" to set the PointsFinder. */
				if (_currentsUvGetter != null) {
					_currentsUvGetter.close(getInterpolationMode(/* forCurrents= */true));
				}
			}
			if (_currentsUvGetter == null) {
				SimCaseManager.err(simCase, "Error in getting CurrentsUvGetter.%s");
				return stringPluses;
			}

			/** Winds. */
			String windsConfidenceString = null;
			String preDistressWindsConfidenceString = null;
			{
				try {
					windsConfidenceString = ModelReader.getStringNoDefault(simCase, _windsElement,
							ModelReader._ConfidenceAtt, stringPluses);
					preDistressWindsConfidenceString = ModelReader.getStringWithBackupAttribute(simCase, _windsElement,
							ModelReader._PreDistressConfidenceAtt, ModelReader._ConfidenceAtt, stringPluses);
				} catch (final ReaderException e) {
					windsConfidenceString = null;
				}
			}
			_windsElement.setAttribute(ModelReader._ConfidenceAtt, windsConfidenceString);
			_windsElement.setAttribute(ModelReader._PreDistressConfidenceAtt, preDistressWindsConfidenceString);
			final String windsConfidenceAttributeName = "standardDeviationFor" + windsConfidenceString.toUpperCase()
					+ "Confidence";
			final String preDistressWindsConfidenceAttributeName = "standardDeviationForPreDistress"
					+ preDistressWindsConfidenceString.toUpperCase() + "Confidence";

			long windsHalfLifeSecs = 0;
			long preDistressWindsHalfLifeSecs = 0;
			double windsSd = Double.NaN;
			double windsAltSd = Double.NaN;
			boolean windDirectionIsDn = true;
			try {
				windsSd = ModelReader.getDouble(simCase, _windsElement, windsConfidenceAttributeName, " kts",
						stringPluses);
				windsAltSd = ModelReader.getDouble(simCase, _windsElement, preDistressWindsConfidenceAttributeName,
						" kts", stringPluses);
				windsHalfLifeSecs = ModelReader.getInt(simCase, _windsElement, "halfLife", " mins", 0, stringPluses)
						* 60;
				preDistressWindsHalfLifeSecs = ModelReader.getInt(simCase, _windsElement, "preDistressHalfLife",
						" mins", 0, stringPluses) * 60;
				final boolean windDirectionIsUpwind = ModelReader.getBoolean(simCase, _windsElement, "directionFrom",
						false, stringPluses);
				windDirectionIsDn = !windDirectionIsUpwind;
			} catch (final ModelReader.ReaderException ignored) {
				SimCaseManager.err(simCase, String.format("Error in general wind description.%s",
						StringUtilities.getStackTraceString(ignored)));
			}
			if ("constant".equals(_windsFileType)) {
				double speed = Double.NaN;
				double direction = Double.NaN;
				try {
					speed = ModelReader.getDouble(simCase, _windsElement, "speed", " kts", 0d, stringPluses);
					direction = ModelReader.getDouble(simCase, _windsElement, "dir", " T", 0d, stringPluses);
				} catch (final ModelReader.ReaderException ignored) {
					SimCaseManager.err(simCase, String.format("Error in constant winds description.%s",
							StringUtilities.getStackTraceString(ignored)));
				}
				final double downWindDirection = direction + (windDirectionIsDn ? 0d : 180d);
				_windsUvGetter = new ConstantWindsUvGetter(this, speed, downWindDirection, windsSd, windsSd, windsAltSd,
						windsAltSd, windsHalfLifeSecs, preDistressWindsHalfLifeSecs);
			} else if ("netcdf".equals(_windsFileType) || "nc".equals(_windsFileType)) {
				try {
					final double dU = windsSd;
					final double dV = windsSd;
					final double altDU = windsAltSd;
					final double altDV = windsAltSd;
					SimCaseManager.out(simCase, String.format("\nBuilding Winds Reader from:%s", _windsFilePath));
					_windsUvGetter = new NetCdfWindsUvGetter(simCase, this, _windsFilePath, dU, dV, altDU, altDV,
							windsHalfLifeSecs, preDistressWindsHalfLifeSecs, windDirectionIsDn);
					if (stashEnvFiles) {
						ModelReader.stashEngineFile(simCase, _windsFilePath, _simFilePath,
								/* suffixLc= */FilenameUtils.EXTENSION_SEPARATOR_STR
										+ FilenameUtils.getExtension(_windsFilePath).toLowerCase(),
								"SimEnv", overwriteEnvFiles);
					}
				} catch (final Exception e) {
					_windsUvGetter = null;
					SimCaseManager.standardLogError(simCase, e, "Unable to build Winds Reader.");
				}
			} else {
				_windsUvGetter = null;
			}
			/** Now that we have it, "close it" to set the PointFinder. */
			if (_windsUvGetter != null) {
				_windsUvGetter.close(getInterpolationMode(/* forCurrents= */false));
			}
		}
		return stringPluses;
	}

	public Element getCurrentsElement() {
		return _currentsElement;
	}

	public Element getWindsElement() {
		return _windsElement;
	}

	public CurrentsUvGetter getCurrentsUvGetter() {
		return _currentsUvGetter;
	}

	public WindsUvGetter getWindsUvGetter() {
		return _windsUvGetter;
	}

	public void addFixHazard(final double intensity, final List<Area> areas, final long startRefSecs,
			final long durationSecs) {
		_fixHazards.add(new FixHazard(intensity, areas, startRefSecs, durationSecs));
	}

	public List<FixHazard> getFixHazards() {
		return _fixHazards;
	}

	public SotWithDbl.OriginatingSotWithWt getOriginatingSotWithWt() {
		return _originatingSotWithWt;
	}

	public void setOriginatingObjectTypeWithWeight(final SotWithDbl.OriginatingSotWithWt originating) {
		_originatingSotWithWt = originating;
	}

	public String getLogFilePath() {
		final String particlesFilePathCore = getParticlesFilePathCore();
		final String logFilePathX = particlesFilePathCore + "Log.xml";
		final String logFilePath = new File(logFilePathX).getAbsolutePath();
		return logFilePath;
	}

	public void addContainmentRequest(final double containmentRadius) {
		final int nOldContainmentRequests = _containmentRequests == null ? 0 : _containmentRequests.length;
		final double[] containmentRequests = new double[nOldContainmentRequests + 1];
		for (int i = 0; i < nOldContainmentRequests; ++i) {
			containmentRequests[i] = _containmentRequests[i];
		}
		_containmentRequests = containmentRequests;
		_containmentRequests[nOldContainmentRequests] = containmentRadius;
	}

	public double[] getContainmentRequests() {
		return _containmentRequests.clone();
	}

	public void setModeName(final String mode) {
		_modeName = mode;
	}

	public String getModeName() {
		return _modeName;
	}

	public void setExtent(final SimCaseManager.SimCase simCase, final double lt, final double lo, final double rt,
			final double hi) {
		if (_realExtentSet) {
			return;
		}
		final Extent extent = new Extent(lt, lo, rt, hi);
		setExtent(extent);
	}

	public void setExtent(final SimCaseManager.SimCase simCase, final LatLng3 center, final double nmi) {
		if (_realExtentSet) {
			return;
		}
		_coi = new CircleOfInterest(SimCaseManager.getLogger(simCase), center, nmi);
		final double r = nmi * _NmiToR;
		final double degs = Math.toDegrees(r);
		_extent = new Extent(center, degs);
		final TangentCylinder.FlatLatLng flatCenter = TangentCylinder.convertToCentered(center);
		_tangentCylinder = flatCenter.getOwningTangentCylinder();
		_realExtentSet = true;
	}

	public void setExtent(final Extent extent) {
		if (_realExtentSet) {
			return;
		}
		_extent = extent;
		_coi = _extent.computeCircleOfInterest();
		final LatLng3 center = _coi.getCentralLatLng();
		final TangentCylinder.FlatLatLng flatCenter = TangentCylinder.convertToCentered(center);
		_tangentCylinder = flatCenter.getOwningTangentCylinder();
		_realExtentSet = true;
	}

	public boolean isOutOfArea(final LatLng3 latLng) {
		final boolean returnValue = !_extent.contains(latLng);
		return returnValue;
	}

	public void logOutOfArea(final long refSecs, final ParticleIndexes prtclIndxs, final String typeOfParticle,
			final LatLng3 latLng) {
		if (!prtclIndxs.isEnvMean()) {
			synchronized (_outOfAreaIncidents) {
				final OutOfAreaIncident outOfAreaIncident = _outOfAreaIncidents.remove(prtclIndxs);
				if (outOfAreaIncident != null) {
					outOfAreaIncident._numberOfAdditionalIncidents += 1;
					final boolean newWinner;
					if (!_reverseDrift) {
						newWinner = refSecs < outOfAreaIncident._refSecs;
					} else {
						newWinner = refSecs > outOfAreaIncident._refSecs;
					}
					if (newWinner) {
						outOfAreaIncident._refSecs = refSecs;
						outOfAreaIncident._latLng = latLng;
						outOfAreaIncident._typeOfParticle = typeOfParticle;
					}
					_outOfAreaIncidents.put(prtclIndxs, outOfAreaIncident);
					return;
				}
				try {
					final OutOfAreaIncident outOfAreaIncident2 = new OutOfAreaIncident(refSecs, prtclIndxs,
							typeOfParticle, latLng);
					_outOfAreaIncidents.put(prtclIndxs, outOfAreaIncident2);
				} catch (final NullPointerException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public Collection<Model.OutOfAreaIncident> getOutOfAreaIncidents() {
		return _outOfAreaIncidents.values();
	}

	public String getSimFilePath() {
		return _simFilePath;
	}

	public boolean getRunStudy() {
		return _runStudy;
	}

	public void setRunStudy(final boolean runStudy) {
		_runStudy = runStudy;
	}

	public boolean getDumpGshhsKmz() {
		return _dumpGshhsKmz;
	}

	public void setDumpGshhsKmz(final boolean dumpGshhsKmz) {
		_dumpGshhsKmz = dumpGshhsKmz;
	}

	public void setWriteOcTables(final boolean writeOcTables) {
		_writeOcTables = writeOcTables;
	}

	public boolean isRiverine() {
		return _riverine;
	}

	public void setRiverine(final boolean riverine) {
		_riverine = riverine;
	}

	public void setWriteSortiesWorkbook(final boolean writeSortiesWorkbook) {
		if (writeSortiesWorkbook) {
			_sortiesWorkbook = new XSSFWorkbook();
			/** NB: Ensure Foreground color is set prior to background */
			/** Set cell style 1. */
			_cellStyle1 = _sortiesWorkbook.createCellStyle();
			_cellStyle1.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			_cellStyle1.setFillForegroundColor(HSSFColorPredefined.GREY_25_PERCENT.getIndex());
			_cellStyle1.setFillBackgroundColor(HSSFColorPredefined.BLACK.getIndex());
			/** Set cell style 2a. */
			_cellStyle2a = _sortiesWorkbook.createCellStyle();
			_cellStyle2a.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			_cellStyle2a.setFillForegroundColor(HSSFColorPredefined.AQUA.getIndex());
			_cellStyle2a.setFillBackgroundColor(HSSFColorPredefined.DARK_BLUE.getIndex());
			/** Set cell style 2b. */
			_cellStyle2b = _sortiesWorkbook.createCellStyle();
			_cellStyle2b.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			_cellStyle2b.setFillForegroundColor(HSSFColorPredefined.DARK_BLUE.getIndex());
			_cellStyle2b.setFillBackgroundColor(HSSFColorPredefined.AQUA.getIndex());
			/** Set cell style 2c. */
			_cellStyle2c = _sortiesWorkbook.createCellStyle();
			_cellStyle2c.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			_cellStyle2c.setFillForegroundColor(HSSFColorPredefined.RED.getIndex());
			_cellStyle2c.setFillBackgroundColor(HSSFColorPredefined.BLUE_GREY.getIndex());
		} else {
			_sortiesWorkbook = null;
			_cellStyle1 = _cellStyle2a = _cellStyle2b = _cellStyle2c = null;
		}
	}

	public String getInterpolationMode(final boolean forCurrents) {
		if (forCurrents) {
			return _interpolationMode;
		}
		return (_interpolationMode == null || _interpolationMode.compareTo(_2Closest) == 0) ? _2Closest : _3Closest;
	}

	public void setInterpolationMode(final String interpolationMode) {
		_interpolationMode = interpolationMode;
	}

	public boolean getIsSticky(final ParticleIndexes prtclIndxs) {
		if (_proportionOfSticky == 0d) {
			return false;
		} else if (_proportionOfSticky == 1d) {
			return true;
		}
		if (_stickies == null) {
			synchronized (this) {
				if (_stickies == null) {
					final Randomx r = new Randomx(_randomSeed, /* nToAdvace= */1);
					final int n = getTotalNParticles();
					final int k = (int) Math.round(_proportionOfSticky * n);
					_stickies = PermutationTools.randomKofNBitSet(k, n, r);
				}
			}
		}
		final int overallIndex = prtclIndxs.getOverallIndex();
		return _stickies.get(overallIndex);
	}

	public double getProportionOfSticky() {
		return _proportionOfSticky;
	}

	public void setProportionOfSticky(final double proportionOfSticky) {
		_proportionOfSticky = proportionOfSticky;
		_stickies = null;
	}

	public boolean getReverseDrift() {
		return _reverseDrift;
	}

	public void setReverseDrift(final boolean reverseDrift) {
		_reverseDrift = reverseDrift;
	}

	public Extent getExtent() {
		return _extent;
	}

	public CircleOfInterest getCoi() {
		return _coi;
	}

	public TangentCylinder getTangentCylinder() {
		return _tangentCylinder;
	}

	public static long roundTimeStepDown(final long targetInSecs) {
		final long[] goodTimeStepsInMinutes = {
				1, 2, 3, 4, 5, 6, 10, 12, 15, 20, 30, 60
		};
		/**
		 * Round targetInSecs down to a value of minutes, or a multiple of the last one.
		 */
		final long targetInMinutes = targetInSecs / 60;
		final int index = CombinatoricTools.getGlbIndex(goodTimeStepsInMinutes, targetInMinutes);
		if (index <= 0) {
			return goodTimeStepsInMinutes[0] * 60;
		}
		if (index < goodTimeStepsInMinutes.length - 1) {
			return goodTimeStepsInMinutes[index] * 60;
		}
		return (targetInSecs / 3600L) * 3600L;
	}

	public void setCurrentsFilePath(final String filePath) {
		_currentsFilePath = StringUtilities.cleanUpFilePath(filePath);
	}

	public void setWindsFilePath(final String filePath) {
		_windsFilePath = StringUtilities.cleanUpFilePath(filePath);
	}

	public String getEtopoShpFilePath() {
		final String etopoDirName = "etopoShp";
		final File outFile = new File(_particlesFilePath);
		final File outParent = outFile.getParentFile();
		final File answerDir = new File(outParent, etopoDirName);
		final String answerDirPath = StringUtilities.getCanonicalPath(answerDir);
		return answerDirPath;
	}

	public String getGshhsShpFilePath() {
		final String shpFileName = "gshhsShp";
		final File outFile = new File(_particlesFilePath);
		final File outParent = outFile.getParentFile();
		final File shpDir = new File(outParent, shpFileName);
		final String shpDirPath = StringUtilities.getCanonicalPath(shpDir);
		return shpDirPath;
	}

	public String getKmzFilePath() {
		final File shpFileDir = new File(getGshhsShpFilePath());
		final String kmzFileName = "gshhs.kmz";
		final File kmzFile = new File(shpFileDir.getParent(), kmzFileName);
		final String kmzFilePath = StringUtilities.getCanonicalPath(kmzFile);
		return kmzFilePath;
	}

	public ShorelineFinder getShorelineFinder() {
		return _shorelineFinder;
	}

	public void setShorelineFinderAndEtopo(final SimCaseManager.SimCase simCase) {
		if (_extent == null || _shorelineFinder != null) {
			return;
		}
		final MyLogger logger = SimCaseManager.getLogger(simCase);
		final SimGlobalStrings simGlobalStrings = SimCaseManager.getSimGlobalStrings(simCase);
		synchronized (this) {
			if (_shorelineFinder != null) {
				return;
			}
			final double gshhsBufferD = simGlobalStrings.getGshhsBufferD();
			final double expandedRadiusNmi = _coi.getRadiusNmi() + Math.toRadians(gshhsBufferD);
			final LatLng3 centralLatLng = _coi.getCentralLatLng();
			final CircleOfInterest bigCoi = new CircleOfInterest(logger, centralLatLng, expandedRadiusNmi);

			final double lowestResRadiusNmi = GshhsReader._LowestResRadiusNmi;
			final double highestResRadiusNmi = GshhsReader._HighestResRadiusNmi;
			final double lowestResRadiusR = lowestResRadiusNmi * _NmiToR;
			final double highestResRadiusR = highestResRadiusNmi * _NmiToR;
			final double radiusR = bigCoi.getRadiusNmi() * _NmiToR;
			final int iResolution = GshhsReader.selectIResolution(radiusR, lowestResRadiusR, highestResRadiusR);

			ShorelineFinder shorelineFinder = null;
			try {
				shorelineFinder = ShorelineFinder.getShorelineFinder(logger, bigCoi, iResolution);
			} catch (final Exception e) {
			}
			if (shorelineFinder == null) {
				SimCaseManager.err(simCase, "No land data; All is Ocean.");
				shorelineFinder = ShorelineFinder.createNoPolygonShorelineFinder();
			}
			SimCaseManager.out(simCase,
					String.format(
							"ShorelineFinder, Resolution[%d(%c)] CircleOfInterest[%s] has [NLoops/NGcas]=[%d/%d].",
							shorelineFinder.getIResolution(), shorelineFinder.getResolutionChar(),
							shorelineFinder.getCoiString(), shorelineFinder.getNLoops(), shorelineFinder.getNGcas()));
			Etopo etopo = null;
			if (Etopo.haveData()) {
				try {
					etopo = new Etopo(logger, _extent, shorelineFinder);
				} catch (final Exception e) {
				}
			}
			if (etopo == null) {
				final String s = "Etopo Building Failed! Using all-deep Etopo.";
				SimCaseManager.err(simCase, s);
				etopo = Etopo.getAllDeepEtopo();
			}
			_etopo = etopo;
			_shorelineFinder = shorelineFinder;
		}
	}

	public Etopo getEtopo() {
		return _etopo;
	}

	public CircleOfInterest getShorelineFinderCoi() {
		return _shorelineFinder == null ? null : _shorelineFinder.getCoi();
	}

	public boolean getDisplayOnly() {
		return _displayOnly;
	}

	public float[] getMaximumAnchorDepthsInM() {
		final TreeSet<Float> maximumAnchorDepthsInMetersSet = new TreeSet<>();
		for (final SearchObjectType sot : _searchObjectTypes) {
			if (sot.mightAnchor()) {
				maximumAnchorDepthsInMetersSet.add(sot.getMaximumAnchorDepthInM());
			}
		}
		final int nDifferent = maximumAnchorDepthsInMetersSet.size();
		final float[] depths = new float[nDifferent];
		int k = 0;
		for (final Float depth : maximumAnchorDepthsInMetersSet) {
			depths[k++] = depth;
		}
		Arrays.sort(depths);
		return depths;
	}

	public long[] computeRefSecsS() {
		final TreeSet<Long> refSecsSet = new TreeSet<>();
		refSecsSet.add(_firstOutputRefSecs);
		refSecsSet.add(_lastOutputRefSecs);
		/** Get the intermediate steps. Set the first one and then add them all in. */
		final long dayBoundary = TimeUtilities.roundDownToDayBoundary(_firstOutputRefSecs);
		for (long refSecs = dayBoundary; refSecs < _lastOutputRefSecs; refSecs += _monteCarloSecs) {
			if (refSecs > _firstOutputRefSecs) {
				refSecsSet.add(refSecs);
			}
		}
		final long[] refSecsS = new long[refSecsSet.size()];
		int k = 0;
		for (final long refSecs : refSecsSet) {
			refSecsS[k++] = refSecs;
		}
		return refSecsS;
	}

	public long getRefSecs(final long simSecs) {
		return !_reverseDrift ? simSecs : Long.MAX_VALUE - simSecs;
	}

	public long getSimSecs(final long refSecs) {
		return !_reverseDrift ? refSecs : Long.MAX_VALUE - refSecs;
	}

	public CurrentsUvGetter getCurrentsUvGetter2(final MyLogger logger, final BitSet iViews,
			final boolean interpolateInTime) {
		return _currentsUvGetter == null ? null
				: _currentsUvGetter.getCurrentsUvGetter2(logger, iViews, interpolateInTime);
	}

	public WindsUvGetter getWindsUvGetter2(final BitSet iViews, final boolean interpolateInTime) {
		return _windsUvGetter == null ? null : _windsUvGetter.getWindsUvGetter2(iViews, interpolateInTime);
	}

	public String[] getCurrentsViewNames() {
		return _currentsUvGetter == null ? null : _currentsUvGetter.getViewNames();
	}

	public String[] getWindsViewNames() {
		return _windsUvGetter == null ? null : _windsUvGetter.getViewNames();
	}

	public File getOutDir() {
		final String particlesFilePath = getParticlesFilePath();
		if (particlesFilePath == null) {
			return null;
		}
		return new File(particlesFilePath).getParentFile();
	}

	public int[] getFirstSampleAndPredictionIndex(final boolean forWinds) {
		final String[] viewNames = forWinds ? getWindsViewNames() : getCurrentsViewNames();
		final int nViews = viewNames == null ? 0 : viewNames.length;
		if (forWinds) {
			return new int[] {
					nViews, nViews
			};
		}
		final CurrentsUvGetter currentsUvGetter = getCurrentsUvGetter();
		if (currentsUvGetter == null) {
			return new int[] {
					nViews, nViews
			};
		}
		return currentsUvGetter.getFirstSampleAndPredictionIndex(nViews);
	}

	public void addCurrentsBoxDefinition(final BoxDefinition boxDefinition) {
		_currentsBoxDefinitions.add(boxDefinition);
	}

	public ArrayList<BoxDefinition> getCurrentsBoxDefinitions() {
		return _currentsBoxDefinitions;
	}

	public void addWindsBoxDefinition(final BoxDefinition boxDefinition) {
		_windsBoxDefinitions.add(boxDefinition);
	}

	public ArrayList<BoxDefinition> getWindsBoxDefinitions() {
		return _windsBoxDefinitions;
	}

	public long[] getFullRefSecsExtent() {
		return _fullRefSecsExtent;
	}

	public void setFullRefSecsExtent(final long[] fullRefSecsExtent) {
		_fullRefSecsExtent = fullRefSecsExtent;
	}

	public XSSFWorkbook getSortiesWorkbook() {
		return _sortiesWorkbook;
	}

	public void dumpSortiesWorkbook(final SimCaseManager.SimCase simCase) {
		if (_sortiesWorkbook != null) {
			if (_sortiesWorkbook.getNumberOfSheets() > 0) {
				final String stashedSimFilePath = getStashedSimFilePath();
				final String stashedSimFileName = new File(stashedSimFilePath).getName();
				final int lastIndex = stashedSimFileName.lastIndexOf(SimCaseManager._SimEndingLc);
				final String sortiesWorkbookFileName = stashedSimFileName.substring(0, lastIndex) + "-sorties.xlsx";
				final String modelFilePath = getSimFilePath();
				final File stashResultDir = AbstractOutFilesManager.GetEngineFilesDir(simCase, modelFilePath,
						"SimResult");
				final File sortiesWorkbookFile = new File(stashResultDir, sortiesWorkbookFileName);
				if (!sortiesWorkbookFile.exists() || sortiesWorkbookFile.delete()) {
					try (final FileOutputStream stream = new FileOutputStream(sortiesWorkbookFile)) {
						PoiUtils.autoFit(_sortiesWorkbook);
						_sortiesWorkbook.write(stream);
					} catch (final IOException e) {
					}
				}
			}
			try {
				_sortiesWorkbook.close();
			} catch (final IOException e) {
			}
			_sortiesWorkbook = null;
		}
		_cellStyle1 = _cellStyle2a = _cellStyle2b = _cellStyle2c = null;
	}

	public File getR21MessageFile() {
		final String simFilePath = getSimFilePath();
		final String simFilePathLc = simFilePath.toLowerCase();
		final int endOfCore = simFilePathLc.lastIndexOf(SimCaseManager._SimEndingLc);
		if (endOfCore < 0) {
			return null;
		}
		final String r21FilePath = simFilePath.substring(0, endOfCore) + ".xml";
		final File r21MessageFile = new File(r21FilePath);
		return r21MessageFile;
	}

	public boolean hasBearingCalls() {
		final int nScenarii = getNScenarii();
		for (int k = 0; k < nScenarii; ++k) {
			final Scenario scenario = getScenario(k);
			if (scenario.hasBearingCalls()) {
				return true;
			}
		}
		return false;
	}

	public int getNSearchObjectTypes() {
		return _searchObjectTypes.size();
	}

	public int getSotOrd(final int sotId) {
		final int nSots = _searchObjectTypes.size();
		for (int sotOrd = 0; sotOrd < nSots; ++sotOrd) {
			if (_searchObjectTypes.get(sotOrd).getId() == sotId) {
				return sotOrd;
			}
		}
		return -1;
	}

	public SearchObjectType getSotFromOrd(final int sotOrd) {
		if (sotOrd < 0 || sotOrd >= _searchObjectTypes.size()) {
			return null;
		}
		return _searchObjectTypes.get(sotOrd);
	}

	public SearchObjectType getSotFromId(final int sotId) {
		final int nSots = _searchObjectTypes.size();
		for (int sotOrd = 0; sotOrd < nSots; ++sotOrd) {
			final SearchObjectType sot = _searchObjectTypes.get(sotOrd);
			if (sot.getId() == sotId) {
				return sot;
			}
		}
		return null;
	}

	public long getHighRefSecs() {
		final long[] fullRefSecsExtent = getFullRefSecsExtent();
		if (fullRefSecsExtent != null) {
			return fullRefSecsExtent[1];
		}
		return getLastOutputRefSecs() + 2 * getMonteCarloSecs();
	}

	public TreeSet<ModelReader.StringPlus> getStringPluses() {
		return _stringPluses;
	}

	public void addStringPluses(final TreeSet<ModelReader.StringPlus> newStringPluses) {
		_stringPluses = ModelReader.addStringPluses(newStringPluses, _stringPluses);
	}

	public String getStashedSimFilePath() {
		return _stashedSimFilePath;
	}

	public void setStashedSimFilePath(final String stashedSimFilePath) {
		_stashedSimFilePath = stashedSimFilePath;
	}

	public void setDepthInfos(final SimCaseManager.SimCase simCase, final boolean augmentDepths,
			final boolean useInclusionExclusion) {
		if (_etopo != null) {
			final MyLogger logger = SimCaseManager.getLogger(simCase);
			final float[] maximumAnchorDepthsInM = getMaximumAnchorDepthsInM();
			if (maximumAnchorDepthsInM != null && maximumAnchorDepthsInM.length > 0) {
				SimCaseManager.out(simCase, String.format("Setting DepthInfos."));
				_etopo.setCellsAndDepthInfos(logger, maximumAnchorDepthsInM, augmentDepths, useInclusionExclusion);
				SimCaseManager.out(simCase, String.format("Finished Setting DepthInfos."));
			}
		}
	}

	public void setRealExtentSet() {
		_realExtentSet = true;
	}

	public boolean getRealExtentSet() {
		return _realExtentSet;
	}

	/**
	 * Returns the directory path, the echo file name, and the file name of the used
	 * properties.
	 */
	private String[] getEchoFileNames(final SimCaseManager.SimCase simCase) {
		final String modelFilePath = getSimFilePath();
		final File echoDir = AbstractOutFilesManager.GetEngineFilesDir(simCase, modelFilePath, "SimEcho");
		final String echoDirPath = StringUtilities.getCanonicalPath(echoDir);
		if (echoDirPath == null) {
			MainRunner.HandleFatal(simCase, new RuntimeException("No Echo Dir"));
		}
		final String stashedSimFilePath = getStashedSimFilePath();
		final String stashedSimFileName = new File(stashedSimFilePath).getName();
		final int lastIndex = stashedSimFileName.toLowerCase().lastIndexOf(SimCaseManager._SimEndingLc);
		final String stashedEchoFileName = stashedSimFileName.substring(0, lastIndex) + "-echo.xml";
		final String stashedSimPropertiesFileName = stashedSimFileName.substring(0, lastIndex) + "-Sim.properties";
		return new String[] {
				echoDirPath, stashedEchoFileName, stashedSimPropertiesFileName
		};
	}

	public void writeEcho(final SimCaseManager.SimCase simCase) {
		try {
			final String[] echoFileNames = getEchoFileNames(simCase);
			final File echoDir = new File(echoFileNames[0]);
			StaticUtilities.makeDirectory(echoDir);
			final String echoFileName = echoFileNames[1];
			final File echoFile = new File(echoDir, echoFileName);
			final String echoFilePath = StringUtilities.getCanonicalPath(echoFile);
			if (getNParticlesPerScenario() > 0) {
				final ModelWriter modelWriter = new ModelWriter();
				final Element modelElement = modelWriter.createModelElement(simCase, this);
				try (final FileOutputStream fos = new FileOutputStream(echoFilePath)) {
					final LsFormatter formatter = modelWriter.getFormatter();
					formatter.dumpWithTimeComment(modelElement, fos);
				}
			}
			final TreeSet<ModelReader.StringPlus> stringPluses = getStringPluses();
			if (stringPluses != null) {
				final File echoPropertiesFile = new File(echoDir, echoFileNames[2]);
				try (final PrintStream printStream = new PrintStream(echoPropertiesFile)) {
					for (final ModelReader.StringPlus stringPlus : stringPluses) {
						printStream.print(stringPlus.getString() + Constants._NewLine);
					}
				}
			}
		} catch (final Exception e) {
			MainRunner.HandleFatal(simCase, new RuntimeException(e));
		}
	}

	public void freeMemory() {
		StaticUtilities.clearList(_sorties);
		StaticUtilities.clearList(_fixHazards);
		if (_searchObjectTypes != null) {
			_searchObjectTypes.clear();
		}
		_windsFilePath = _windsFileType = null;
		_windsElement = null;
		_currentsFilePath = _currentsFileType = null;
		_currentsElement = null;
		_containmentRequests = null;
		_modeName = null;
		/** Environmental data: */
		if (_currentsUvGetter != null) {
			_currentsUvGetter.freeMemory();
		}
		_currentsUvGetter = null;
		if (_windsUvGetter != null) {
			_windsUvGetter.freeMemory();
		}
		_windsUvGetter = null;
		if (_etopo != null) {
			_etopo.freeMemory();
		}
		_etopo = null;
		if (_shorelineFinder != null) {
			_shorelineFinder.freeMemory();
		}
	}

	public ExtraGraphicsClass getExtraGraphicsObject() {
		return _extraGraphicsObject;
	}

	/**
	 * Returns the maximum number of particles for a time step, for this scenario
	 * and objTp. WildCards are permitted for both iScenario and objTpId.
	 */
	public int getMaxNParticles(final int iScenario, final int sotId) {
		if (iScenario == _WildCard) {
			int n = 0;
			final int nScenarii = _scenarii.size();
			for (int iSc = 0; iSc < nScenarii; ++iSc) {
				n += getMaxNParticles(iSc, sotId);
			}
			return n;
		}
		final Scenario scenario = _scenarii.get(iScenario);
		if (sotId == _WildCard || sotId < 0) {
			return getNParticlesPerScenario();
		}
		final int sotOrd = getSotOrd(sotId);
		final int count = scenario.getSotOrdToCount()[sotOrd];
		return count;
	}

	private synchronized Scenario addScenario(final double scenarioWeight, final Scenario scenario) {
		if (scenario != null && scenarioWeight > 0d) {
			_scenarii.add(scenario);
			Collections.sort(_scenarii);
			return scenario;
		}
		return null;
	}

	public RegularScenario addRegularScenario(final SimCaseManager.SimCase simCase, final int id, final String name,
			final double scenarioWeight) {
		final int iScenario = getNScenarii();
		int baseParticleIndex = 0;
		for (final Scenario scenario : _scenarii) {
			baseParticleIndex += scenario.getNParticles();
		}
		final RegularScenario regularScenario = new RegularScenario(simCase, id, name, Scenario._RegularScenarioType,
				scenarioWeight, iScenario, baseParticleIndex, getNParticlesPerScenario());
		return (RegularScenario) addScenario(scenarioWeight, regularScenario);
	}

	public LobScenario addLobScenario(final SimCaseManager.SimCase simCase, final short id, final String name,
			final String type, final Thresholds wangsnessThresholds, final double scenarioWeight,
			final TimeDistribution timeDistribution) {
		final int iScenario = getNScenarii();
		int baseParticleIndex = 0;
		for (final Scenario scenario : _scenarii) {
			baseParticleIndex += scenario.getNParticles();
		}
		final LobScenario lobScenario = new LobScenario(simCase, id, name, type, wangsnessThresholds, scenarioWeight,
				iScenario, baseParticleIndex, getNParticlesPerScenario(), timeDistribution);
		return (LobScenario) addScenario(scenarioWeight, lobScenario);
	}

	public int getNScenarii() {
		return _scenarii.size();
	}

	public Scenario getScenario(final int iScenario) {
		if (iScenario < 0 || iScenario >= _scenarii.size()) {
			return null;
		}
		return _scenarii.get(iScenario);
	}

	public DebrisSighting getDebrisSighting(final int iDebrisSighting) {
		if (iDebrisSighting < 0 || iDebrisSighting >= _scenarii.size()) {
			return null;
		}
		return _debrisSightings.get(iDebrisSighting);
	}

	public boolean checkAndNormalizeScenarioWeights(final SimCaseManager.SimCase simCase, final Model model) {
		boolean result = true;
		double ttlNonZeroWt = 0d;
		final int nScenarii = getNScenarii();
		for (int iScenario = 0; iScenario < nScenarii && result; ++iScenario) {
			final Scenario scenario = _scenarii.get(iScenario);
			result &= scenario.checkAndFinalize(model);
			ttlNonZeroWt += scenario.getScenarioWeight();
		}
		if (0.99 <= ttlNonZeroWt && ttlNonZeroWt <= 1.01f) {
		} else {
			SimCaseManager.err(simCase, String.format("Cumulated scenarii weight[%g]", ttlNonZeroWt));
		}
		result &= ttlNonZeroWt > 0d;
		/** Normalize the scenario weights. */
		if (result) {
			for (int iScenario = 0; iScenario < nScenarii && result; ++iScenario) {
				final Scenario scenario = _scenarii.get(iScenario);
				final double scenarioWt = scenario.getScenarioWeight() / ttlNonZeroWt;
				if (scenarioWt >= 0d) {
					scenario.setInitialPrior(scenarioWt);
				} else {
					result = false;
					break;
				}
			}
		}
		return result;
	}

	/** Done reading everything in. */
	public void close() {
		final int nScenarii = _scenarii.size();
		for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
			final Scenario scenario = _scenarii.get(iScenario);
			scenario.close();
		}
	}

	public boolean hasSailData() {
		if (_scenarii == null) {
			return false;
		}
		for (final Scenario scenario : _scenarii) {
			if (scenario.getSailData() != null) {
				return true;
			}
		}
		return false;
	}

	public SailData getSailData(final int iScenario) {
		final Scenario scenario = getScenario(iScenario);
		if (scenario == null) {
			return null;
		}
		return scenario.getSailData();
	}

	public boolean hasSomePreDistress() {
		if (_scenarii == null) {
			return false;
		}
		for (final Scenario scenario : _scenarii) {
			if (scenario.getPreDistressModel() != null) {
				return true;
			}
		}
		return false;
	}

	public int scenarioIdToIndex(final int id) {
		for (int n = _scenarii.size(), k = 0; k < n; ++k) {
			if (_scenarii.get(k).getId() == id) {
				return k;
			}
		}
		return -1;
	}

	public int debrisSightingIdToIndex(final int id) {
		for (int n = _debrisSightings.size(), k = 0; k < n; ++k) {
			if (_debrisSightings.get(k).getId() == id) {
				return k;
			}
		}
		return -1;
	}

	public boolean scenariiAreDeepEqual(final Model model) {
		if (_scenarii.size() != model._scenarii.size()) {
			return false;
		}
		final Iterator<Scenario> scenarioIterator = _scenarii.iterator();
		final Iterator<Scenario> comparedScenarioIterator = model._scenarii.iterator();
		while (scenarioIterator.hasNext() && comparedScenarioIterator.hasNext()) {
			final Scenario scenario = scenarioIterator.next();
			final Scenario comparedScenario = comparedScenarioIterator.next();
			if ((scenario.getScenarioWeight() != comparedScenario.getScenarioWeight())
					|| (scenario.getId() != comparedScenario.getId()) || !scenario.deepEquals(comparedScenario)) {
				return false;
			}
		}
		return true;
	}

	public boolean debrisSightingsAreDeepEqual(final Model model) {
		final int n = _debrisSightings.size();
		if (n != model._debrisSightings.size()) {
			return false;
		}
		for (int k = 0; k < n; ++k) {
			final DebrisSighting debrisSighting = _debrisSightings.get(k);
			final DebrisSighting otherDebrisSighting = model._debrisSightings.get(k);
			if ((debrisSighting.getId() != otherDebrisSighting.getId())
					|| !debrisSighting.deepEquals(otherDebrisSighting)) {
				return false;
			}
		}
		return true;
	}

	public int getNDebrisSightings() {
		return _debrisSightings.size();
	}

	public long[] getDebrisSightingSecsS() {
		final HashSet<Long> secsSet = new HashSet<>();
		final int nSightings = _debrisSightings.size();
		for (int k = 0; k < nSightings; ++k) {
			final DebrisSighting sighting = _debrisSightings.get(k);
			secsSet.add(sighting.getSightingRefSecs());
		}
		final int nSecsS = secsSet.size();
		final long[] toReturn = new long[nSecsS];
		final Iterator<Long> it = secsSet.iterator();
		for (int k = 0; k < nSecsS; ++k) {
			toReturn[k] = it.next();
		}
		Arrays.sort(toReturn);
		return toReturn;
	}

	public int getNDebrisObjectTypes() {
		return _debrisObjectTypes.size();
	}

	public synchronized DebrisSighting addDebrisSighting(final SimCaseManager.SimCase simCase, final String name,
			final int id, final Polygon polygon, final long sightingRefSecs,
			final ArrayList<SotWithDbl> dotWithCnfdncsList) {
		final int nDotWithCnfdcs = dotWithCnfdncsList.size();
		final SotWithDbl[] dotWithCnfdncs = dotWithCnfdncsList.toArray(new SotWithDbl[nDotWithCnfdcs]);
		Arrays.sort(dotWithCnfdncs);
		final DebrisSighting debrisSighting = new DebrisSighting(simCase, id, name, polygon, sightingRefSecs,
				dotWithCnfdncs);
		_debrisSightings.add(debrisSighting);
		Collections.sort(_debrisSightings);
		return debrisSighting;
	}

}
