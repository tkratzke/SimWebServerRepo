package com.skagit.sarops.model.io;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.skagit.sarops.AbstractOutFilesManager;
import com.skagit.sarops.model.ExtraGraphicsClass;
import com.skagit.sarops.model.LobScenario;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.RegularScenario;
import com.skagit.sarops.model.Scenario;
import com.skagit.sarops.model.SearchObjectType;
import com.skagit.sarops.model.Sortie;
import com.skagit.sarops.model.SotWithWt;
import com.skagit.sarops.model.preDistressModel.DeadReckon;
import com.skagit.sarops.model.preDistressModel.PreDistressModel;
import com.skagit.sarops.model.preDistressModel.Voyage;
import com.skagit.sarops.model.preDistressModel.sail.SailData;
import com.skagit.sarops.simCaseManager.MainRunner;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.simCaseManager.SimCaseManager.SimCase;
import com.skagit.sarops.tracker.lrcSet.InverseCube;
import com.skagit.sarops.tracker.lrcSet.LateralRangeCurve;
import com.skagit.sarops.tracker.lrcSet.Logit;
import com.skagit.sarops.tracker.lrcSet.LrcSet;
import com.skagit.sarops.tracker.lrcSet.MBeta;
import com.skagit.sarops.util.CppToJavaTracer;
import com.skagit.sarops.util.wangsness.Thresholds;
import com.skagit.util.Constants;
import com.skagit.util.DirsTracker;
import com.skagit.util.ElementIterator;
import com.skagit.util.Graphics;
import com.skagit.util.LsFormatter;
import com.skagit.util.SaropsDirsTracker;
import com.skagit.util.StaticUtilities;
import com.skagit.util.StringUtilities;
import com.skagit.util.TimeUtilities;
import com.skagit.util.cdf.BivariateNormalCdf;
import com.skagit.util.cdf.area.Area;
import com.skagit.util.cdf.area.EllipticalArea;
import com.skagit.util.cdf.area.Polygon;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.GreatCircleCalculator;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.MotionType;
import com.skagit.util.randomx.TimeDistribution;

/**
 * This class uses Sim.properties and the two potential overriding versions of
 * this file to fill in or override values. Within the code, there is a
 * Sim.properties, and within the data directory, there may be one that
 * overrides the one in the jar file. In addition, the user can specify one that
 * overrides these two.
 */
public class ModelReader {

	final private static Extent _DefaultExtent = new Extent(-124d, 44d, -121, 48d);

	final public static long _IntMod = -((long) Integer.MIN_VALUE);

	final public static String _ConfidenceAtt = "confidence";
	final public static String _PreDistressConfidenceAtt = "confidenceForPreDistress";
	final public static String _WriteOcTablesAtt = "writeOcTables";
	final public static String _WriteSortiesWorkbookAtt = "writeSortiesWorkbook";
	public static final String _XmlSimPropertiesFilePathAtt = "XmlSimPropertiesFilePath";
	public static final String _DistinctDetectionThresholdAtt = "distinctDetectionThreshold";
	public static final String _DistinctDetectionThresholdAttUnits = "mins";

	public static final long _UnsetTime = TimeUtilities._UnsetTime;
	public static final int _BadDuration = -1;

	public static boolean _ReadLatLngTo6Only;
	static {
		final String indicator = StringUtilities.getSystemProperty("Read.LatLng.To.6.Only", /* useSpaceProxy= */false);
		_ReadLatLngTo6Only = indicator != null;
	}

	final public static Comparator<StringPlus> _StringPlusComparator = new Comparator<>() {

		@Override
		public int compare(final StringPlus stringPlus0, final StringPlus stringPlus1) {
			final String key0 = stringPlus0._key;
			final String key1 = stringPlus1._key;
			final int compareValue = key0.compareTo(key1);
			if (compareValue != 0) {
				return compareValue;
			}
			final int id0 = stringPlus0._id;
			final int id1 = stringPlus1._id;
			return id0 < id1 ? -1 : (id0 > id1 ? 1 : 0);
		}
	};

	public enum DataType {
		PROPERTIES_OVRD, XML, PROPERTIES_DFLT, CODE_ONLY;

		private String _fullName;

		public String getFullName() {
			return _fullName;
		}

		static {
			int max = 0;
			for (final DataType dataType : values()) {
				max = Math.max(max, dataType.name().length());
			}
			final String formatString = String.format("%%-%ds", max);
			for (final DataType dataType : values()) {
				dataType._fullName = String.format(formatString, dataType.name());
			}
		}
	}

	public static class StringPlus {
		final public DataType _dataType;
		final public String _key;
		final public int _id;
		final public String _value;

		private StringPlus(final DataType dataType, final String key, final String value) {
			_dataType = dataType;
			_key = key;
			_id = Integer.MIN_VALUE;
			_value = value;
		}

		private StringPlus(final DataType dataType, final String key, final int id, final String value) {
			_dataType = dataType;
			_key = key;
			_id = id;
			_value = value;
		}

		@Override
		public boolean equals(final Object o) {
			if (!(o instanceof StringPlus)) {
				return false;
			}
			final StringPlus stringPlus = (StringPlus) o;
			return _key.equals(stringPlus._key) && _id == stringPlus._id;
		}

		@Override
		public int hashCode() {
			return _key.hashCode();
		}

		public String getString() {
			if (_id == Integer.MAX_VALUE) {
				final String s = String.format("%s: %s = %s", //
						_dataType.getFullName(), _key, _value);
				return s;
			}
			final String s = String.format("%s: %s(%d) = %s", //
					_dataType.getFullName(), _key, _id, _value);
			return s;
		}

		@Override
		public String toString() {
			return getString();
		}
	}

	private static StringPlus constructStringPlus(final DataType dataType, final String key, final String value) {
		return new StringPlus(dataType, key, value);
	}

	private static StringPlus constructBooleanStringPlus(final DataType dataType, final String key, final boolean b) {
		final String value = Boolean.toString(b);
		return new StringPlus(dataType, key, value);
	}

	private static StringPlus constructIntStringPlus(final DataType dataType, final String key, final int i,
			final String unit) {
		final String value = Integer.toString(i) + (unit == null ? "" : (" " + unit));
		return new StringPlus(dataType, key, value);
	}

	private static StringPlus constructLongStringPlus(final DataType dataType, final String key, final long ell,
			final String unit) {
		final String value = Long.toString(ell) + (unit == null ? "" : (" " + unit));
		return new StringPlus(dataType, key, value);
	}

	private static StringPlus constructDoubleStringPlus(final DataType dataType, final String key, final double d,
			final String unit) {
		final String value = Double.toString(d) + (unit == null ? "" : (" " + unit));
		return new StringPlus(dataType, key, value);
	}

	/**
	 * If an attribute is one of the Special Strings, we look for its property in
	 * Sim.properties by looking in the value of the following map.
	 */
	final private static TreeMap<String, String> _SpecialStrings;

	static {
		_SpecialStrings = new TreeMap<>();
		_SpecialStrings.put("force_koopman", "SIM.COMPLETED_SEARCH.force_koopman");
		_SpecialStrings.put("motion_type", "SIM.COMPLETED_SEARCH.motion_type");
	}

	private static Properties _JarFileSimProperties;

	static {
		/** Load up _JarFileSimProperties. */
		_JarFileSimProperties = new Properties();
		try (final InputStream is = new Object() {
		}.getClass().getResourceAsStream("Sim.properties")) {
			_JarFileSimProperties.load(is);
			cleanProperties(_JarFileSimProperties);
			/** Back up the cleaned version. */
			final File dataDir = SaropsDirsTracker.getDataDir();
			final File backupFile = new File(dataDir, "CopyOfJarFileSim.properties");
			/* If we cannot dump the backup copy, that's ok. */
			try (final FileOutputStream outputStream = new FileOutputStream(backupFile)) {
				_JarFileSimProperties.store(outputStream, "");
			} catch (final Exception e) {
				if (true) {
				} else {
					System.out.printf("\nNon-fatal error: Couldn't back up Sim.properties");
				}
			}
		} catch (final IOException e) {
			MainRunner.HandleFatal(/* simCase= */null, new RuntimeException(e));
		}
	}

	public static Properties getSimPropertiesBeforeXmlOverrides(final SimCaseManager.SimCase simCase) {
		final Properties simProperties = new Properties();
		simProperties.putAll(_JarFileSimProperties);
		final File dataDir = SaropsDirsTracker.getDataDir();
		final File overrideFile = new File(dataDir, "Sim.properties");
		if (overrideFile.exists() && !overrideFile.isDirectory()) {
			final String overrideFilePath = StringUtilities.getCanonicalPath(overrideFile);
			SimCaseManager.out(simCase,
					String.format("Overriding SimProperties with DataDir SimProperties(%s).", overrideFilePath));
			addAndOverrideSimPropertiesFromIoFile(null, simProperties, overrideFilePath);
		}
		return simProperties;
	}

	private static void cleanProperties(final Properties properties) {
		final ArrayList<String> overriddenDefaults = new ArrayList<>();
		final Enumeration<?> e = properties.propertyNames();
		while (e.hasMoreElements()) {
			final String key = (String) e.nextElement();
			if (key.endsWith(".OVERRIDE")) {
				final int lastDot = key.lastIndexOf('.');
				final String defaultKey = key.substring(0, lastDot) + ".DEFAULT";
				if (properties.containsKey(defaultKey)) {
					overriddenDefaults.add(defaultKey);
				}
			}
		}
		for (final String defaultKey : overriddenDefaults) {
			properties.remove(defaultKey);
		}
	}

	/**
	 * Properties that have ".SRU." in them are duplicated, replacing ".SRU." with
	 * ".PATTERN_VARIABLE" and ".PATTERN_VARIABLE_SEQUENCE.PATTERN_VARIABLE."
	 */
	public static void addSruToPvSimProperties(final Properties simProperties) {
		final Enumeration<?> e = simProperties.propertyNames();
		final TreeMap<String, String> newOnes = new TreeMap<>();
		while (e.hasMoreElements()) {
			final String key = (String) e.nextElement();
			if (key.contains(".SRU.")) {
				final String value = simProperties.getProperty(key);
				final String newKey0 = key.replaceAll("\\.SRU\\.", ".PATTERN_VARIABLE.");
				newOnes.put(newKey0, value);
				final String newKey1 = key.replaceAll("\\.SRU\\.", ".PATTERN_VARIABLE_SEQUENCE.PATTERN_VARIABLE.");
				newOnes.put(newKey1, value);
			}
		}
		for (final Map.Entry<String, String> entry : newOnes.entrySet()) {
			final String key = entry.getKey();
			final String value = entry.getValue();
			simProperties.setProperty(key, value);
		}
	}

	public static void addAndOverrideXmlSimProperties(final SimCaseManager.SimCase simCase, final Element root,
			final TreeSet<StringPlus> stringPluses) {
		final Properties simProperties = simCase.getSimProperties();
		final String xmlSimPropertiesFilePath0 = getString(simCase, root, _XmlSimPropertiesFilePathAtt,
				/* attName= */null, stringPluses);
		if (xmlSimPropertiesFilePath0 != null) {
			final String xmlSimPropertiesFilePath = StringUtilities.cleanUpFilePath(xmlSimPropertiesFilePath0);
			addAndOverrideSimPropertiesFromIoFile(simCase, simProperties, xmlSimPropertiesFilePath);
			simCase.setXmlSimPropertiesFilePath(xmlSimPropertiesFilePath);
		}
	}

	public static void dumpSimProperties(final SimCaseManager.SimCase simCase) {
		final Properties simProperties = simCase.getSimProperties();
		final Enumeration<?> e = simProperties.propertyNames();
		String s = "";
		while (e.hasMoreElements()) {
			final String key = (String) e.nextElement();
			final String value = simProperties.getProperty(key);
			s += String.format("\n%s:%s", key, value);
		}
		if (s.length() > 0) {
			s += "\n";
		}
		SimCaseManager.out(simCase, s);
	}

	private static void addAndOverrideSimPropertiesFromIoFile(final SimCaseManager.SimCase simCase,
			final Properties simProperties, final String overrideFilePath) {
		if (overrideFilePath == null) {
			return;
		}
		final File overrideFile = new File(overrideFilePath);
		if (!overrideFile.exists() || !overrideFile.canRead() || !overrideFile.isFile()) {
			return;
		}
		try (final FileInputStream overrideFileInputStream = new FileInputStream(overrideFile)) {
			SimCaseManager.out(simCase, String.format("Overriding SimProperties from %s.", overrideFilePath));
			simProperties.load(overrideFileInputStream);
			cleanProperties(simProperties);
		} catch (final IOException e) {
			MainRunner.HandleFatal(simCase, new RuntimeException(e));
		}
	}

	public static Model readModel(final SimCaseManager.SimCase simCase, final File caseDirFile,
			final String modelFilePath) {
		final Document document;
		{
			Document documentX = null;
			try (FileInputStream fis = new FileInputStream(modelFilePath)) {
				documentX = LsFormatter._DocumentBuilder.parse(fis);
			} catch (final Exception e) {
			}
			document = documentX;
		}
		if (document == null) {
			return null;
		}
		final String topTag = document.getDocumentElement().getTagName();
		final Element root = document.getDocumentElement();
		final TreeSet<StringPlus> stringPluses1 = new TreeSet<>(_StringPlusComparator);
		addAndOverrideXmlSimProperties(simCase, root, stringPluses1);
		addSruToPvSimProperties(simCase.getSimProperties());
		stashSimProperties(simCase, modelFilePath, /* simulator= */true);
		dumpSimProperties(simCase);

		if ("SIM".equals(topTag) || "DISP".equals(topTag)) {
			final boolean displayOnly = !("SIM".equals(topTag));
			/**
			 * Following is an empty model. Start by adding the StringPluses from all of the
			 * Sim.properties files.
			 */
			final Model model = new Model(simCase, modelFilePath, displayOnly);
			model.addStringPluses(stringPluses1);
			try {
				final TreeSet<StringPlus> stringPluses2 = traverse(simCase, model, caseDirFile, modelFilePath, root);
				model.addStringPluses(stringPluses2);
				if (model.getDisplayOnly() || model.check(simCase)) {
					final String xmlSimPropertiesFilePath = simCase.getXmlSimPropertiesFilePath();
					if (xmlSimPropertiesFilePath != null) {
						model.setXmlSimPropertiesFilePath(xmlSimPropertiesFilePath);
					}
				}
			} catch (final ReaderException e) {
				throw new RuntimeException("Bad SimModel Read");
			}
			return model;
		}
		return null;
	}

	/**
	 * Reads the XML content from the decoded particlesFile string.
	 *
	 * @param decodedString the text to be read.
	 * @return a model fully populated or null if the reading fails.
	 */
	public static Model readFromParticlesFileModelString(final SimCaseManager.SimCase simCase,
			final String decodedString) {
		Model model;
		try {
			final byte[] textBytes = decodedString.getBytes(LsFormatter._CharsetName);
			final Document document = LsFormatter._DocumentBuilder.parse(new ByteArrayInputStream(textBytes));
			try {
				model = new Model(simCase, /* simFilePath= */"TEXT", /* displayOnly= */false);
				final Element root = document.getDocumentElement();
				/**
				 * caseDirFile is null since this is not a case; just a model stored in some
				 * text String.
				 */
				traverse(simCase, model, /* caseDirFile= */null, /* modelFilePath= */null, root);
				model.close(simCase);
			} catch (final ReaderException e) {
				SimCaseManager.err(simCase, String.format("Reader error.%s", StringUtilities.getStackTraceString(e)));
				model = null;
			}
		} catch (final SAXException e) {
			SimCaseManager.err(simCase, String.format("XML syntax error.%s", StringUtilities.getStackTraceString(e)));
			model = null;
		} catch (final IOException e) {
			SimCaseManager.err(simCase, String.format("TEXT%s", StringUtilities.getStackTraceString(e)));
			model = null;
		}
		if (model == null) {
			return null;
		}
		return model.check(simCase) ? model : null;
	}

	/** Represents a failure occurring when reading an XML description. */
	public static class ReaderException extends Exception {
		/** An Exception is serializable so the following is recommended. */
		final private static long serialVersionUID = 1L;

		public ReaderException(final String message) {
			super(message);
		}
	}

	/**
	 * @param root  the root of the tree containing the description of the model.
	 * @param model the model to be populated.
	 * @throws ReaderException when an incorrect definition is given.
	 */
	private static TreeSet<StringPlus> traverse(final SimCaseManager.SimCase simCase, final Model model,
			final File caseDirFile, final String modelFilePath, final Element root) throws ReaderException {
		/**
		 * We do not track the StringPluses if we are reading from a particle file's
		 * string.
		 */
		final TreeSet<StringPlus> stringPluses = (caseDirFile != null && modelFilePath != null)
				? new TreeSet<>(_StringPlusComparator)
				: null;
		final boolean displayOnly = model.getDisplayOnly();
		if (displayOnly) {
			model.setRunStudy(true);
			model.setDumpGshhsKmz(false);
		} else {
			final boolean runStudy = getBoolean(simCase, root, "runStudy", false, /* stringPluses= */null);
			model.setRunStudy(runStudy);
			final boolean dumpGshhsKmz = getBoolean(simCase, root, "dumpGshhsKmz", false, /* stringPluses= */null);
			model.setDumpGshhsKmz(dumpGshhsKmz);
		}
		final boolean writeOcTables = displayOnly ? false : getBoolean(simCase, root, _WriteOcTablesAtt, stringPluses);
		model.setWriteOcTables(writeOcTables);
		final boolean writeSortiesWorkbook = displayOnly ? false
				: getBoolean(simCase, root, _WriteSortiesWorkbookAtt, stringPluses);
		model.setWriteSortiesWorkbook(writeSortiesWorkbook);

		/** The first time through, we check to see if there is a SAIL. */
		boolean hasSailScenario = false;
		ElementIterator it0 = new ElementIterator(root);
		LOOKING_FOR_SAIL: while (it0.hasNextElement()) {
			final Element child = it0.nextElement();
			final String childTag = child.getTagName();
			if ("SCENARIO".equals(childTag)) {
				final ElementIterator it1 = new ElementIterator(child);
				while (it1.hasNextElement()) {
					final Element grandChild = it1.nextElement();
					final String grandChildTag = grandChild.getTagName();
					if (grandChildTag.toLowerCase().contains("sail")) {
						hasSailScenario = true;
						break LOOKING_FOR_SAIL;
					}
				}
			}
		}

		/**
		 * The 2nd time through, we get only the Request so we can set model's extent,
		 * TangentCylinder, and water/land issues.
		 */
		boolean requestRead = false;
		boolean environmentalDataRead = false;
		boolean searchObjectTypeRead = false;
		boolean scenarioRead = false;
		it0 = new ElementIterator(root);
		while (it0.hasNextElement()) {
			final Element child = it0.nextElement();
			final String childTag = child.getTagName();
			if ("REQUEST".equals(childTag)) {
				readRequest(simCase, model, caseDirFile, modelFilePath, child, hasSailScenario, stringPluses);
				requestRead = true;
				break;
			}
		}

		/** 3rd time through; bulk of the work. */
		final ArrayList<Element> graphicsElements = new ArrayList<>();
		it0 = new ElementIterator(root);
		final HashSet<Short> usedScenarioIds = new HashSet<>();
		while (it0.hasNextElement()) {
			final Element child = it0.nextElement();
			final String childTag = child.getTagName();
			if ("SCENARIO".equals(childTag)) {
				readScenario(simCase, usedScenarioIds, child, model, stringPluses);
				scenarioRead = true;
			} else if ("COMPLETED_SEARCH".equals(childTag)) {
				readCompletedSearch(simCase, child, model, /* addToModel= */true, stringPluses);
			} else if ("GRAPHICS".equals(childTag)) {
				graphicsElements.add(child);
			} else if ("REQUEST".equals(childTag)) {
				/** This is already done. Hence, do nothing. */
			} else if ("SEARCH_OBJECT_TYPE".equals(childTag)) {
				readDistressSot(simCase, child, model, stringPluses);
				searchObjectTypeRead = true;
			} else if ("ENVDATA".equals(childTag)) {
				readEnvironmentalData(simCase, model, modelFilePath, child, stringPluses);
				environmentalDataRead = true;
			} else if ("FIX_HAZARD".equals(childTag)) {
				readFixHazard(simCase, child, model, stringPluses);
			} else if ("ORIGINATING_CRAFT".equals(childTag) || "ORIGINATING_SOT".equals(childTag)) {
				final String unit = null;
				int originatingObjectTypeId = getInt(simCase, child, "id", unit, stringPluses);
				final String name = getString(simCase, child, "name", "" + originatingObjectTypeId, stringPluses);
				SearchObjectType searchObjectType = model.getSearchObjectType(originatingObjectTypeId);
				/**
				 * We have a special situation of the originating SearchObjectType. If there is
				 * no such searchObjectType, we create one. Doing so will guarantee a unique id,
				 * which we then use as our originating id. This created one will have no sweep
				 * width information though.
				 */
				if (searchObjectType == null) {
					searchObjectType = model.addSearchObjectType(originatingObjectTypeId, name);
					originatingObjectTypeId = searchObjectType.getId();
				}
				model.setOriginatingObjectTypeWithWeight(new SotWithWt.OriginatingSotWithWt(searchObjectType));
			} else {
				AnnounceIgnoreTag(simCase, childTag);
			}
		}
		if (!requestRead) {
			throw new ReaderException("Missing request definition");
		}
		if (!displayOnly && !environmentalDataRead) {
			throw new ReaderException("Missing environmental data definition");
		}
		if (!displayOnly && !searchObjectTypeRead) {
			throw new ReaderException("Missing search object type definition");
		}
		if (!displayOnly && !scenarioRead) {
			throw new ReaderException("Missing scenario definition");
		}

		/**
		 * We had to defer the reading of the Graphics Elements until we learned the
		 * times. We can process them now.
		 */
		final int nGraphicsElements = graphicsElements.size();
		for (int k = 0; k < nGraphicsElements; ++k) {
			final Element graphicsElement = graphicsElements.get(k);
			readGraphics(simCase, graphicsElement, model, model.getExtraGraphicsObject(), /* stringPluses= */null);
		}
		return stringPluses;
	}

	/**
	 * Reads a fix hazard description.
	 *
	 * @param element the node containing the description of the fix hazard.
	 * @param model   the model to be populated.
	 * @throws ReaderException when an incorrect definition is given.
	 */
	private static void readFixHazard(final SimCaseManager.SimCase simCase, final Element element, final Model model,
			final TreeSet<StringPlus> stringPluses) throws ReaderException {
		double intensity = -1;
		String intensityString = element.getAttribute("intensity");
		if (intensityString != null && intensityString.length() > 0) {
			try {
				intensity = Double.parseDouble(intensityString);
			} catch (final NumberFormatException e) {
				intensity = -1;
			}
			if (intensity < 0) {
				intensityString = intensityString.substring(0, 1).toUpperCase();
				intensity = getDouble(simCase, element, intensityString, "", stringPluses);
			}
		}
		if (intensity < 0) {
			intensity = getDouble(simCase, element, "intensity", "", stringPluses);
		}
		final ElementIterator childIterator = new ElementIterator(element);
		final List<Area> areas = new ArrayList<>();
		final boolean interpretAsHazard = true;
		final boolean isUniform = false;
		final double truncateDistanceNmi = Double.POSITIVE_INFINITY;
		while (childIterator.hasNextElement()) {
			final Element child = childIterator.nextElement();
			areas.add(readArea(simCase, child, interpretAsHazard, isUniform, truncateDistanceNmi, stringPluses));
		}
		final String startTimeString = element.getAttribute("start");
		if (startTimeString == null || startTimeString.length() == 0) {
			model.addFixHazard(intensity, areas, -1L, Integer.MAX_VALUE / 2);
		} else {
			final long startRefSecs = TimeUtilities.dtgToRefSecs(startTimeString);
			int durationSecs = getDurationSecs(simCase, element, "duration", stringPluses);
			if (durationSecs < 0) {
				final String endTimeString = element.getAttribute("end");
				final long endRefSecs = TimeUtilities.dtgToRefSecs(endTimeString);
				durationSecs = (int) (endRefSecs - startRefSecs);
			}
			model.addFixHazard(intensity, areas, startRefSecs, durationSecs);
		}
	}

	private static void readEnvironmentalData(final SimCaseManager.SimCase simCase, final Model model,
			final String modelFilePath, final Element envDataElement, final TreeSet<StringPlus> stringPluses)
			throws ReaderException {
		/** Winds. */
		String windsFilePath = null;
		String windsFileType = null;
		Element windsElement = null;
		/** Currents. */
		String currentsFilePath = null;
		String currentsFileType = null;
		Element currentsElement = null;

		final ElementIterator childIterator = new ElementIterator(envDataElement);
		while (childIterator.hasNextElement()) {
			final Element child = childIterator.nextElement();
			final String tagName = child.getTagName();
			if ("WIND".equals(tagName)) {
				windsFileType = getString(simCase, child, "type", null, stringPluses);
				if ("CONSTANT".equalsIgnoreCase(windsFileType)) {
					/** Check for valid constant input. */
					try {
						final double speed = getDouble(simCase, child, "speed", " kts", Double.NaN, stringPluses);
						final double direction = getDouble(simCase, child, "dir", " T", Double.NaN, stringPluses);
						if ((speed < 0d) || Double.isNaN(direction)) {
							throw new ReaderException("Bad Constant Winds Specification.");
						}
					} catch (final ReaderException ignored) {
						windsFileType = null;
						SimCaseManager.err(simCase, String.format("Bad Constant Winds Specification:\n%s",
								StringUtilities.getStackTraceString(ignored)));
					}
				} else {
					final String windsFilePathFromXml = getString(simCase, child, "file", /* attName= */null,
							stringPluses);
					windsFilePath = AbstractOutFilesManager.GetEnvFilePath(modelFilePath, windsFilePathFromXml);
					child.setAttribute("file", windsFilePath);
				}
				windsElement = windsFileType != null ? child : null;
			} else if ("CURRENT".equals(tagName)) {
				currentsFileType = getString(simCase, child, "type", null, stringPluses);
				if ("CONSTANT".equalsIgnoreCase(currentsFileType)) {
					/** Check for valid constant input. */
					try {
						final double speed = getDouble(simCase, child, "speed", " kts", Double.NaN, stringPluses);
						final double direction = getDouble(simCase, child, "dir", " T", Double.NaN, stringPluses);
						if ((speed < 0d) || Double.isNaN(direction)) {
							throw new ReaderException("Bad Constant Currents Specification.");
						}
					} catch (final ReaderException ignored) {
						currentsFileType = null;
						SimCaseManager.err(simCase, String.format("Bad Constant Currents Specification:\n%s",
								StringUtilities.getStackTraceString(ignored)));
					}
				} else {
					final String currentsFilePathFromXml = getString(simCase, child, "file", /* attName= */null,
							stringPluses);
					currentsFilePath = AbstractOutFilesManager.GetEnvFilePath(modelFilePath, currentsFilePathFromXml);
					child.setAttribute("file", currentsFilePath);
				}
				currentsElement = currentsFileType != null ? child : null;
			} else {
				AnnounceIgnoreTag(simCase, tagName);
			}
		}
		if (!simCase._forCompareKeyFiles && windsFileType == null) {
			throw new ReaderException("Incomplete environmental data description: no wind data");
		} else if (!simCase._forCompareKeyFiles && currentsFileType == null) {
			throw new ReaderException("Incomplete environmental data description: no current data");
		}
		model.setEnvironmentalData(windsFilePath, windsFileType, windsElement, currentsFilePath, currentsFileType,
				currentsElement);
	}

	/**
	 * Reads the definition of an ObjectType: survival and leeway data.
	 *
	 * @param element the node holding the data.
	 * @param model   the model to be populated.
	 * @throws ReaderException when an incorrect definition is given.
	 */
	private static void readDistressSot(final SimCaseManager.SimCase simCase, final Element element, final Model model,
			final TreeSet<StringPlus> stringPluses) throws ReaderException {
		final ElementIterator childIterator = new ElementIterator(element);
		final String unit = null;
		final int id = getInt(simCase, element, "id", unit, stringPluses);
		final String name = getString(simCase, element, "name", "" + id, stringPluses);
		SearchObjectType distressSot = model.getSearchObjectType(id);
		if (distressSot != null) {
			throw new ReaderException("Multiple definition of search object type " + id);
		}
		distressSot = model.addSearchObjectType(id, name);
		/**
		 * ProbabilityOfAnchoring, maximumAnchorableDepthInMeters, and alwaysAnchor
		 */
		final boolean alwaysAnchor = getBoolean(simCase, element, "alwaysAnchor", false, stringPluses);
		final double perCentAnchoring = getDouble(simCase, element, "probabilityOfAnchoring", " %", 0d, stringPluses);
		final double probabilityOfAnchoring = 0.01 * perCentAnchoring;
		distressSot.setProbabilityOfAnchoring(probabilityOfAnchoring);
		final float maximumAnchorDepthM = (float) getDouble(simCase, element, "maximumAnchorDepth", " M", 0d,
				stringPluses);
		distressSot.setMaximumAnchorDepthM(maximumAnchorDepthM);
		distressSot.setAlwaysAnchor(alwaysAnchor);
		/** Get and set the survival data for this object type. */
		double scaleHrs = Double.NaN;
		double shape = Double.NaN;
		scaleHrs = getDouble(simCase, element, "scale", " hrs", Double.NaN, /* stringPluses= */null);
		shape = getDouble(simCase, element, "shape", "", Double.NaN, /* stringPluses= */null);
		if (Double.isNaN(scaleHrs) || Double.isNaN(shape)) {
			throw new ReaderException("Incomplete survival description");
		}
		distressSot.setSurvivalData(scaleHrs, shape);
		/** Leeway has its own subtag. */
		boolean haveLeewayTag = false;
		while (childIterator.hasNextElement()) {
			final Element child = childIterator.nextElement();
			final String tagName = child.getTagName();
			if ("LEEWAY".equals(tagName)) {
				haveLeewayTag = true;
				/** If I forcefully declare "no leeway data," I'm ok. */
				if (!getBoolean(simCase, element, "LeewayData", true, stringPluses)) {
					continue;
				}
				double nominalSpeed = Double.NaN;
				double gibingRate = Double.NaN;
				double dwlSlope = Double.NaN;
				double dwlConstant = 0d;
				double dwlStandardDeviation = Double.NaN;
				double cwlPlusSlope = Double.NaN;
				double cwlPlusConstant = Double.NaN;
				double cwlPlusStandardDeviation = Double.NaN;
				double cwlMinusSlope = Double.NaN;
				double cwlMinusConstant = Double.NaN;
				double cwlMinusStandardDeviation = Double.NaN;
				boolean useRayleigh = false;
				nominalSpeed = getDouble(simCase, child, "nominalSpeed", " kts", Double.NaN, stringPluses);
				gibingRate = getDouble(simCase, child, "gibingRate", "% perHr", Double.NaN, stringPluses);
				final ElementIterator grandChildIterator = new ElementIterator(child);
				while (grandChildIterator.hasNextElement()) {
					final Element grandChild = grandChildIterator.nextElement();
					final String grandChildTag = grandChild.getTagName();
					if ("DWL".equals(grandChildTag)) {
						if (!Double.isNaN(dwlSlope)) {
							throw new ReaderException("Multiple definition of dwl");
						}
						dwlSlope = getDouble(simCase, grandChild, "slope", "", stringPluses);
						dwlConstant = getDouble(simCase, grandChild, "constant", " kts", 0d, stringPluses);
						dwlStandardDeviation = getDouble(simCase, grandChild, "Syx", " kts", stringPluses);
						useRayleigh = getBoolean(simCase, grandChild, "useRayleigh", false, stringPluses);
					} else if ("CWLPOS".equals(grandChildTag)) {
						if (!Double.isNaN(cwlPlusSlope)) {
							throw new ReaderException("Multiple definition of cwlPlus");
						}
						cwlPlusSlope = getDouble(simCase, grandChild, "slope", "", stringPluses);
						cwlPlusConstant = getDouble(simCase, grandChild, "constant", " kts", 0d, stringPluses);
						cwlPlusStandardDeviation = getDouble(simCase, grandChild, "Syx", " kts", stringPluses);
					} else if ("CWLNEG".equals(grandChildTag)) {
						if (!Double.isNaN(cwlMinusSlope)) {
							throw new ReaderException("Multiple definition of cwlMinus");
						}
						cwlMinusSlope = getDouble(simCase, grandChild, "slope", "", stringPluses);
						cwlMinusConstant = getDouble(simCase, grandChild, "constant", " kts", 0d, stringPluses);
						cwlMinusStandardDeviation = getDouble(simCase, grandChild, "Syx", " kts", stringPluses);
					} else {
						AnnounceIgnoreTag(simCase, grandChildTag);
					}
				}
				if (Double.isNaN(cwlPlusSlope)) {
					cwlPlusSlope = -cwlMinusSlope;
					cwlPlusStandardDeviation = cwlMinusStandardDeviation;
				}
				if (Double.isNaN(cwlMinusSlope)) {
					cwlMinusSlope = -cwlPlusSlope;
					cwlMinusStandardDeviation = cwlPlusStandardDeviation;
				}
				if (Double.isNaN(dwlSlope) || Double.isNaN(cwlPlusSlope) || Double.isNaN(gibingRate)
						|| Double.isNaN(nominalSpeed)) {
					throw new ReaderException("Incomplete leeway description");
				}
				/**
				 * setLeewayData is expecting gibingRate as a frequency per second.
				 */
				final double gibingFrequencyPerSecond = gibingRate / 100d / 3600d;
				distressSot.setLeewayData(gibingFrequencyPerSecond, dwlSlope, dwlConstant, dwlStandardDeviation,
						cwlPlusSlope, cwlPlusConstant, cwlPlusStandardDeviation, cwlMinusSlope, cwlMinusConstant,
						cwlMinusStandardDeviation, nominalSpeed, useRayleigh);
			} else {
				AnnounceIgnoreTag(simCase, tagName);
			}
		}
		if (!haveLeewayTag) {
			if (getBoolean(simCase, element, "LeewayData", true, stringPluses)) {
				throw new ReaderException("Bad Object Type");
			}
		}
	}

	private static void readRequest(final SimCaseManager.SimCase simCase, final Model model, final File caseDirFile,
			final String modelFilePath, final Element requestElement, final boolean hasSailScenario,
			final TreeSet<StringPlus> stringPluses) throws ReaderException {
		final boolean displayOnly = model.getDisplayOnly();
		final String typeString = getStringNoDefault(simCase, requestElement, "type", stringPluses).toLowerCase();
		model.setReverseDrift(typeString.contains("runreversedrift"));
		// final String fgksString = getStringNoDefault(simCase, requestElement, "type",
		// stringPluses).toLowerCase();
		// model.setReverseDrift(typeString.contains("runreversedrift"));
		final ElementIterator childIterator = new ElementIterator(requestElement);
		String particlesFilePath = null;
		long lastOutputTimeInRefSecs = _UnsetTime;
		long firstOutputTimeRefSecs = _UnsetTime;
		long monteCarloSecsForDisplayOnly = 30 * 60;
		while (childIterator.hasNextElement()) {
			final Element child = childIterator.nextElement();
			final String childTag = child.getTagName();
			if ("INPUT".equals(childTag)) {
				if (!displayOnly) {
					/**
					 * SAIL overrides everything, including whether or not we are river. If not a
					 * SAIL, River overrides everything. So it's good to learn whether we are river
					 * or not
					 */
					final boolean riverine = hasSailScenario ? false
							: getBoolean(simCase, child, "river", stringPluses);
					model.setRiverine(riverine);
					/**
					 * Set the interpolation mode and nMonteCarloStepsPerDump. Both are
					 * straightforward and have defaults in Sim.properties.
					 */
					final String interpolationMode;
					int nMonteCarloStepsPerDump = 0;
					if (hasSailScenario) {
						interpolationMode = getString(simCase, child, "interpolationModeForSAIL", Model._3Closest,
								stringPluses);
						nMonteCarloStepsPerDump = getInt(simCase, child, "numberOfMonteCarloStepsPerDumpForSAIL", "", 1,
								/* stringPluses= */null);
					} else if (riverine) {
						interpolationMode = getString(simCase, child, "interpolationModeForRIVER",
								Model._CenterDominated, stringPluses);
						nMonteCarloStepsPerDump = getInt(simCase, child, "numberOfMonteCarloStepsPerDumpForRiver", "",
								1, stringPluses);
					} else {
						interpolationMode = getString(simCase, child, "interpolationModeForNonRiver", Model._3Closest,
								stringPluses);
						nMonteCarloStepsPerDump = getInt(simCase, child, "numberOfMonteCarloStepsPerDumpForNonRiver",
								"", 1, /* stringPluses= */null);
					}
					model.setInterpolationMode(interpolationMode);
					model.setNMonteCarloStepsPerDump(nMonteCarloStepsPerDump);
					/** Set monteCarloMinutes and nParticlesPerScenario. */
					final long monteCarloMinutes;
					final int nParticlesPerScenario;
					if (hasSailScenario) {
						int monteCarloMinutesA = 0;
						int nParticlesPerScenarioA = 0;
						int monteCarloMinutesB = 0;
						int nParticlesPerScenarioB = 0;
						try {
							monteCarloMinutesA = getInt(simCase, child, "timeStepForSAIL", " mins",
									/* stringPluses= */null);
							final String unit = null;
							nParticlesPerScenarioA = getInt(simCase, child, "particlesForSAIL", unit,
									/* stringPluses= */null);
						} catch (final ReaderException e) {
						}
						if (monteCarloMinutesA > 0 && nParticlesPerScenarioA > 0) {
							monteCarloMinutes = monteCarloMinutesA;
							nParticlesPerScenario = nParticlesPerScenarioA;
						} else {
							final String mode = getString(simCase, child, "mode", /* attName= */null,
									/* stringPluses= */null);
							final String ucMode = (mode == null || mode.length() == 0) ? null : mode.toUpperCase();
							if (ucMode != null) {
								try {
									monteCarloMinutesB = getInt(simCase, child, "timeStepFor" + ucMode, " mins",
											/* stringPluses= */null);
									nParticlesPerScenarioB = getInt(simCase, child, "particlesFor" + ucMode, "", 0,
											/* stringPluses= */null);
								} catch (final ReaderException e) {
									throw new ReaderException("Cannot get timeStep or nParticles.");
								}
								monteCarloMinutes = monteCarloMinutesB;
								nParticlesPerScenario = nParticlesPerScenarioB;
								model.setModeName(mode);
							} else {
								throw new ReaderException("Cannot get timeStep or nParticles.");
							}
						}
					} else if (riverine) {
						/**
						 * River must have monteCarloMinutes set explicitly. It's in Sim.properties
						 * anyway so no try/catch.
						 */
						monteCarloMinutes = getInt(simCase, child, "timeStepForRIVER", " mins", 0, stringPluses);
						/**
						 * For nParticlesPerScenario, check for "particles." If there, go with that. If
						 * not, check for a mode and try to get it from that. If not, we have no hope.
						 */
						final String unit = null;
						int nParticlesPerScenarioA = 0;
						int nParticlesPerScenarioB = 0;
						try {
							nParticlesPerScenarioA = getInt(simCase, child, "particles", unit, /* stringPluses= */null);
						} catch (final ReaderException e) {
						}
						if (nParticlesPerScenarioA == 0) {
							final String mode = getString(simCase, child, "mode", /* attName= */null,
									/* stringPluses= */null);
							final String ucMode = (mode == null || mode.length() == 0) ? null : mode.toUpperCase();
							if (ucMode != null) {
								try {
									nParticlesPerScenarioB = getInt(simCase, child, "particlesFor" + ucMode, "", 0,
											/* stringPluses= */null);
								} catch (final ReaderException e) {
									throw new ReaderException("Cannot get nParticles.");
								}
							}
						}
						if (nParticlesPerScenarioA != 0) {
							getInt(simCase, child, "particles", unit, stringPluses);
							nParticlesPerScenario = nParticlesPerScenarioA;
						} else if (nParticlesPerScenarioB != 0) {
							final String mode = getString(simCase, child, "mode", /* attName= */null, stringPluses);
							final String ucMode = (mode == null || mode.length() == 0) ? null : mode.toUpperCase();
							getInt(simCase, child, "particlesFor" + ucMode, "", 0, stringPluses);
							model.setModeName(mode);
							nParticlesPerScenario = nParticlesPerScenarioB;
						} else {
							throw new ReaderException("Cannot get nParticles.");
						}
					} else {
						/**
						 * Regular. If they're both set explicitly, we do not appeal to mode.
						 */
						int monteCarloMinutesA = 0;
						int monteCarloMinutesB = 0;
						int nParticlesPerScenarioA = 0;
						int nParticlesPerScenarioB = 0;
						try {
							monteCarloMinutesA = getInt(simCase, child, "timeStep", " mins", /* stringPluses= */null);
							final String unit = null;
							nParticlesPerScenarioA = getInt(simCase, child, "particles", unit, /* stringPluses= */null);
						} catch (final ReaderException e) {
						}
						if (monteCarloMinutesA > 0 && nParticlesPerScenarioA > 0) {
							monteCarloMinutes = monteCarloMinutesA;
							nParticlesPerScenario = nParticlesPerScenarioA;
						} else {
							final String mode = getString(simCase, child, "mode", /* attName= */null,
									/* stringPluses= */null);
							final String ucMode = (mode == null || mode.length() == 0) ? null : mode.toUpperCase();
							if (ucMode != null) {
								try {
									monteCarloMinutesB = getInt(simCase, child, "timeStepFor" + ucMode, " mins",
											/* stringPluses= */null);
									nParticlesPerScenarioB = getInt(simCase, child, "particlesFor" + ucMode, "", 0,
											/* stringPluses= */null);
								} catch (final ReaderException e) {
									throw new ReaderException("Cannot get timeStep or nParticles.");
								}
								monteCarloMinutes = monteCarloMinutesB;
								nParticlesPerScenario = nParticlesPerScenarioB;
								model.setModeName(mode);
							} else {
								throw new ReaderException("Cannot get timeStep or nParticles.");
							}
						}
					}
					final long monteCarloSecs = Model.roundTimeStepDown(monteCarloMinutes * 60);
					model.setNParticlesPerScenario(nParticlesPerScenario);
					model.setMonteCarloSecs(monteCarloSecs);
					final String lastOutputTimeString = getStringNoDefault(simCase, child, "datumTime", stringPluses);
					lastOutputTimeInRefSecs = TimeUtilities.dtgToRefSecs(lastOutputTimeString);
				}
				if (displayOnly) {
					model.setNMonteCarloStepsPerDump(1);
					/** Set monteCarloMinutes and nParticlesPerScenario. */
					final long monteCarloMinutes;
					int monteCarloMinutesA = 30;
					try {
						monteCarloMinutesA = getInt(simCase, child, "timeStep", " mins", /* stringPluses= */null);
					} catch (final Exception e1) {
					}
					monteCarloMinutes = monteCarloMinutesA;
					monteCarloSecsForDisplayOnly = Model.roundTimeStepDown(monteCarloMinutes * 60);
					try {
						final String lastOutputTimeString = getStringNoDefault(simCase, child, "datumTime",
								stringPluses);
						lastOutputTimeInRefSecs = TimeUtilities.dtgToRefSecs(lastOutputTimeString);
					} catch (final Exception e) {
						lastOutputTimeInRefSecs = _UnsetTime;
					}
				}
				final long randomSeed = getLong(simCase, child, "randomSeed", "", 979235L, stringPluses);
				model.setRandomSeed(randomSeed);
				final int nForExcelDump = getInt(simCase, child, "nForExcelDump", "", 25, stringPluses);
				model.setNForExcelDump(Math.max(0, nForExcelDump));
				final double leftD = getDouble(simCase, child, "left", " degs", Double.NaN, stringPluses);
				final double lowD = getDouble(simCase, child, "bottom", " degs", Double.NaN, stringPluses);
				final double rightD = getDouble(simCase, child, "right", " degs", Double.NaN, stringPluses);
				final double highD = getDouble(simCase, child, "top", " degs", Double.NaN, stringPluses);
				final double centerLat = getDouble(simCase, child, "centerLat", " degs", Double.NaN, stringPluses);
				final double centerLng = getDouble(simCase, child, "centerLng", " degs", Double.NaN, stringPluses);
				final double nmi = getDouble(simCase, child, "radius", "NM", Double.NaN, stringPluses);
				if (!Double.isNaN(centerLat) && !Double.isNaN(centerLat) && !Double.isNaN(nmi)) {
					final LatLng3 center = LatLng3.getLatLngB(centerLat, centerLng);
					model.setExtent(simCase, center, nmi);
					model.setRealExtentSet();
				} else {
					/** Clean up the longitude window. */
					double leftDx = LatLng3.getInRange180_180(leftD);
					double rightDx = LatLng3.getInRange180_180(rightD);
					if (-180d <= leftDx && leftDx < 180d) {
						if (-180d <= rightDx && rightDx < 180d) {
							if (leftDx == rightDx && leftD != rightD) {
								leftDx = -180d;
								rightDx = 180d;
							}
							if (!Double.isNaN(leftDx) && !Double.isNaN(lowD) && !Double.isNaN(rightDx)
									&& !Double.isNaN(highD)) {
								model.setExtent(simCase, leftD, lowD, rightD, highD);
								model.setRealExtentSet();
							}
						}
					}
				}
				if (!displayOnly) {
					final boolean excludeInitialLandDraws = getBoolean(simCase, child, "excludeInitialLandDraws",
							stringPluses);
					model.setExcludeInitialLandDraws(excludeInitialLandDraws);
					final boolean excludeInitialWaterDraws = getBoolean(simCase, child, "excludeInitialWaterDraws",
							stringPluses);
					model.setExcludeInitialWaterDraws(excludeInitialWaterDraws);
					/** Slippery shore For either river or reverse. */
					double proportionOfSticky = Double.NaN;
					try {
						proportionOfSticky = getDouble(simCase, child, "proportionOfSticky", "", stringPluses);
						if (proportionOfSticky < 0d || proportionOfSticky > 1d) {
							proportionOfSticky = Double.NaN;
						}
					} catch (final ReaderException e) {
					}
					if (Double.isNaN(proportionOfSticky)) {
						boolean b1 = false;
						try {
							b1 = getBoolean(simCase, child, "sticky", stringPluses);
						} catch (final ReaderException e) {
						}
						boolean b2 = false;
						try {
							b2 = getBoolean(simCase, child, "slippery", stringPluses);
						} catch (final ReaderException e) {
						}
						if (b1 && b2) {
							proportionOfSticky = 0.5;
						} else if (b1) {
							proportionOfSticky = 1d;
						} else if (b2) {
							proportionOfSticky = 0d;
						} else {
							final boolean b2a = model.isRiverine();
							final boolean b2b = model.getReverseDrift();
							proportionOfSticky = (b2a || b2b) ? 0d : 1d;
						}
					}
					model.setProportionOfSticky(proportionOfSticky);
				} else {
					model.setExtent(_DefaultExtent);
				}
			} else if ("OUTPUT".equals(childTag)) {
				if (!displayOnly) {
					final String firstOutputTimeString = getStringNoDefault(simCase, child, "firstOutputTime",
							stringPluses);
					firstOutputTimeRefSecs = TimeUtilities.dtgToRefSecs(firstOutputTimeString);
					final String particlesFilePathFromXml = getStringNoDefault(simCase, child, "file", stringPluses);
					SimCaseManager.out(simCase, String.format("ParticlesFilePath(0): %s", particlesFilePathFromXml));
					particlesFilePath = AbstractOutFilesManager.GetParticlesFilePath(modelFilePath, particlesFilePathFromXml);
					SimCaseManager.out(simCase, String.format("ParticlesFilePath(1): %s", particlesFilePath));
					child.setAttribute("file", particlesFilePath);
					/** Read in the containment questions. */
					for (int i = 1;; ++i) {
						final String attributeName = "ContainmentRadius" + '.' + i;
						final double containmentRadius = getDouble(simCase, child, attributeName, "",
								/* stringPluses= */null);
						if (Double.isNaN(containmentRadius)) {
							break;
						}
						model.addContainmentRequest(containmentRadius);
					}
				} else {
					try {
						final String firstOutputTimeString = getStringNoDefault(simCase, child, "firstOutputTime",
								stringPluses);
						firstOutputTimeRefSecs = TimeUtilities.dtgToRefSecs(firstOutputTimeString);
					} catch (final Exception e) {
						firstOutputTimeRefSecs = _UnsetTime;
					}
				}
			} else {
				if (!displayOnly && !childTag.equalsIgnoreCase("case")) {
					AnnounceIgnoreTag(simCase, childTag);
				}
			}
		}
		if (displayOnly) {
			model.setMonteCarloSecs(monteCarloSecsForDisplayOnly);
			model.setFirstOutputRefSecs(firstOutputTimeRefSecs);
			model.setLastOutputRefSecs(lastOutputTimeInRefSecs);
		} else {
			model.setFirstOutputRefSecs(firstOutputTimeRefSecs);
			SimCaseManager.out(simCase, String.format("ParticlesFilePath(2): %s", particlesFilePath));
			final File dirFile = StaticUtilities.makeDirectory(new File(particlesFilePath).getParentFile());
			if (dirFile == null) {
				MainRunner.HandleFatal(simCase, new RuntimeException(
						String.format("Could not create parent directory of %s", particlesFilePath)));
			}
			model.setParticlesFilePath(particlesFilePath);
			model.setLastOutputRefSecs(lastOutputTimeInRefSecs);
		}
	}

	/** Reads a sortie (a combination of legs and LrcSets). */
	private static Sortie readCompletedSearch(final SimCaseManager.SimCase simCase, final Element element,
			final Model model, final boolean addToModel, final TreeSet<StringPlus> stringPluses)
			throws ReaderException {
		final String motionTypeString = getString(simCase, element, "motion_type", "GC", stringPluses);
		final MotionType motionType = MotionType.get(motionTypeString);
		final ElementIterator childIterator = new ElementIterator(element);
		Sortie sortie = null;
		final String sortieId = element.getAttribute("id");
		final String sortieName = element.getAttribute("name");
		final boolean viz2 = true;
		while (childIterator.hasNextElement()) {
			final Element child = childIterator.nextElement();
			final String childTag = child.getTagName();
			if ("FACILITY".equals(childTag) || "SRU".equals(childTag) || "PATTERN".equals(childTag)
					|| "COMPLETED_SEARCH".equals(childTag)) {
				final double[] creepHdgAndTsNmiIn = readCreepHdgAndTs(simCase, child, stringPluses);
				double creepHdgIn = creepHdgAndTsNmiIn[0];
				if (Double.isFinite(creepHdgIn)) {
					creepHdgIn = LatLng3.getInRange0_360(creepHdgIn);
				}
				final double tsNmiIn = creepHdgAndTsNmiIn[1];
				sortie = new Sortie(model, sortieId, sortieName, motionType, creepHdgIn, tsNmiIn,
						/* tsNmiForSingleLeg= */Double.NaN);
				double constantSpeed = Double.NaN;
				long sortieStartTime = -1;
				try {
					sortieStartTime = TimeUtilities
							.dtgToRefSecs(getStringNoDefault(simCase, child, "startTime", stringPluses));
					constantSpeed = getDouble(simCase, child, "constantSpeed", " kts", stringPluses);
				} catch (final ReaderException e) {
					constantSpeed = Double.NaN;
				}
				readSortieLegs(simCase, child, sortie, constantSpeed, sortieStartTime, stringPluses);
			} else if ("COMP_OBJECT_TYPE".equals(childTag)) {
				final String unit = null;
				final int searchObjectTypeId = getInt(simCase, child, "id", unit, stringPluses);
				final SearchObjectType searchObjectType = model.getSearchObjectType(searchObjectTypeId);
				if (searchObjectType == null) {
					String errString = "Search object type " + searchObjectTypeId + " is not in {";
					final Collection<SearchObjectType> searchObjectTypes = model.getSearchObjectTypes();
					boolean printedOne = false;
					for (final SearchObjectType searchObjectType2 : searchObjectTypes) {
						if (printedOne) {
							errString += ",";
						}
						printedOne = true;
						errString += searchObjectType2.getId();
					}
					errString += "}";
					final ReaderException readerError = new ReaderException(errString + searchObjectTypeId);
					SimCaseManager.err(simCase, String.format(errString + "\nWe used to throw an error here.\n%s",
							StringUtilities.getStackTraceString(readerError)));
				}
				final LrcSet lrcSet = getLrcSet(simCase, child, stringPluses);
				if (lrcSet.getNLrcs() == 0) {
					lrcSet.add(LateralRangeCurve._NearSighted);
				}
				sortie.addLrcSet(searchObjectTypeId, lrcSet, viz2);
			} else {
				AnnounceIgnoreTag(simCase, childTag);
			}
		}
		sortie.fillInSortieDataFromDistinctInputLegs();
		if (addToModel) {
			model.add(sortie);
		}
		return sortie;
	}

	public static LrcSet getLrcSet(final SimCaseManager.SimCase simCase, final Element element,
			final TreeSet<StringPlus> stringPluses) {
		final LrcSet lrcSet = new LrcSet();
		addToLrcSet(simCase, lrcSet, element, stringPluses, /* recursionLevel= */0);
		return lrcSet;
	}

	private static void addToLrcSet(final SimCaseManager.SimCase simCase, final LrcSet lrcSet, final Element element,
			final TreeSet<StringPlus> stringPluses, final int recursionLevel) {

		/**
		 * Look for the sweep width parameter first by trying to read in an InverseCube.
		 */
		final InverseCube inverseCube = new InverseCube(simCase, element, /* stringPluses= */null);
		final double sweepWidthIn = inverseCube.getSweepWidthIn();
		if (0 <= sweepWidthIn && sweepWidthIn <= LateralRangeCurve._MinimumSweepWidth) {
			/**
			 * We are using this as a flag for blind or NearSighted. Update stringPluses and
			 * then clear the LrcSet, adding either Blind or NearSighted as the case may be.
			 */
			@SuppressWarnings("unused")
			final InverseCube inverseCube2 = new InverseCube(simCase, element, stringPluses);
			lrcSet.replaceAllLrcs(sweepWidthIn == 0d ? LateralRangeCurve._Blind : LateralRangeCurve._NearSighted);
			return;
		}

		Logit logit = new Logit(simCase, element, /* stringPluses= */null);
		final double logitSweepWidth = logit.getSweepWidth();
		if (logitSweepWidth > 0d) {
			/** Again, update stringPluses. */
			@SuppressWarnings("unused")
			final Logit logit2 = new Logit(simCase, element, stringPluses);
			lrcSet.add(logit);
		} else {
			logit = null;
		}

		MBeta mBeta = null;
		if (logit == null) {
			mBeta = new MBeta(simCase, element, /* stringPluses= */null);
			final double mBetaSweepWidth = mBeta.getSweepWidth();
			if (mBetaSweepWidth > 0d) {
				/** Again, update stringPluses. */
				@SuppressWarnings("unused")
				final MBeta mBeta2 = new MBeta(simCase, element, stringPluses);
				lrcSet.add(mBeta);
			} else {
				mBeta = null;
			}
		}

		if (logit == null && mBeta == null && sweepWidthIn > 0d) {
			/** We're using inverseCube; update stringPluses. */
			@SuppressWarnings("unused")
			final InverseCube inverseCube2 = new InverseCube(simCase, element, stringPluses);
			lrcSet.add(inverseCube);
		}

		/** Recursively add more. */
		final ElementIterator childIterator = new ElementIterator(element);
		while (childIterator.hasNextElement()) {
			final Element child = childIterator.nextElement();
			final String childTag = child.getTagName();
			if ("SENSOR".equals(childTag)) {
				addToLrcSet(simCase, lrcSet, child, stringPluses, recursionLevel + 1);
			}
		}
	}

	public static double[] readCreepHdgAndTs(final SimCaseManager.SimCase simCase, final Element elt,
			final TreeSet<StringPlus> stringPluses) {
		double creepHdgX = Double.NaN;
		try {
			creepHdgX = getDouble(simCase, elt, "creepDirection", "degrees clockwise from north", creepHdgX,
					stringPluses);
		} catch (final ReaderException e) {
		}
		final double creepHdg;
		if (Double.isFinite(creepHdgX)) {
			creepHdg = LatLng3.getInRange0_360(creepHdgX);
		} else {
			creepHdg = Double.NaN;
		}
		double tsNmiX = Double.NaN;
		try {
			tsNmiX = getDouble(simCase, elt, "trackSpacing", "NM", tsNmiX, stringPluses);
		} catch (final Exception e) {
		}
		final double tsNmi;
		if (Double.isFinite(tsNmiX) && tsNmiX > 0d) {
			tsNmi = tsNmiX;
		} else {
			tsNmi = Double.NaN;
		}
		return new double[] {
				creepHdg, tsNmi
		};
	}

	public static void readGraphics(final SimCaseManager.SimCase simCase, final Element element, final Model model,
			final ExtraGraphicsClass extraGraphics, final TreeSet<StringPlus> stringPluses) {
		/** Set the graphics data. */
		final ElementIterator childIterator = new ElementIterator(element);
		while (childIterator.hasNextElement()) {
			final Element child = childIterator.nextElement();
			final String childTag = child.getTagName();
			Graphics.GraphicsType graphicsType = null;
			try {
				graphicsType = Graphics.GraphicsType.valueOf(childTag);
			} catch (final IllegalArgumentException e1) {
				continue;
			}

			try {
				switch (graphicsType) {
				case CIRCLE:
				case ELLIPSE:
					final double centerLat = getDouble(simCase, child, "CenterLat", "", Double.NaN, stringPluses);
					final double centerLng = getDouble(simCase, child, "CenterLng", "", Double.NaN, stringPluses);
					if (graphicsType == Graphics.GraphicsType.CIRCLE) {
						final double radiusNmi = getDouble(simCase, child, "Radius", " NM", Double.NaN, stringPluses);
						if (!Double.isNaN(centerLat) && !Double.isNaN(centerLng) && !Double.isNaN(radiusNmi)) {
							final String colorName = getString(simCase, child, "color", "LightGoldenRodYellow",
									stringPluses);
							extraGraphics.addCircleGraphics(centerLat, centerLng, radiusNmi, colorName);
						}
					} else {
						final double smiMjrNmi = getDouble(simCase, child, "SemiMajor", " NM", Double.NaN,
								stringPluses);
						final double smiMnrNmi = getDouble(simCase, child, "SemiMinor", " NM", Double.NaN,
								stringPluses);
						final double smiMjrHdg = getDouble(simCase, child, "Orientation", " degs", Double.NaN,
								stringPluses);
						if (!Double.isNaN(smiMjrNmi) && !Double.isNaN(smiMnrNmi) && !Double.isNaN(smiMjrHdg)) {
							final String colorName = getString(simCase, child, "color", "LightGoldenRodYellow",
									stringPluses);
							extraGraphics.addEllipseGraphics(centerLat, centerLng, smiMjrNmi, smiMnrNmi, smiMjrHdg,
									colorName);
						}
					}
					break;
				case R21:
					if (model != null) {
						try {
							final File r21MessageFile = model.getR21MessageFile();
							Document document = null;
							try (final FileInputStream r21InputStream = new FileInputStream(r21MessageFile)) {
								document = LsFormatter._DocumentBuilder.parse(r21InputStream);
							} catch (final IOException e) {
								MainRunner.HandleFatal(simCase, new RuntimeException(e));
							}
							final Element rootElement = document.getDocumentElement();
							final Element fixElement = findElement(rootElement, "Fix");
							if (fixElement != null) {
								double lat = Double.NaN;
								double lng = Double.NaN;
								double smiMjr = Double.NaN;
								double smiMnr = Double.NaN;
								double rotationAngle = Double.NaN;
								final ElementIterator fieldIterator = new ElementIterator(fixElement);
								while (fieldIterator.hasNextElement()) {
									final Element fieldElement = fieldIterator.nextElement();
									final String fieldTag = fieldElement.getTagName();
									final String fieldTagLc = fieldTag.toLowerCase();
									final boolean forSmiMjr = fieldTagLc.equals("semimajoraxis");
									final boolean forSmiMnr = fieldTagLc.equals("semiminoraxis");
									final boolean forRotationAngle = fieldTagLc.equals("rotationangle");
									final boolean forLat = fieldTagLc.equals("lat");
									final boolean forLng = fieldTagLc.equals("lon");
									if (forSmiMjr || forSmiMnr || forRotationAngle || forLat || forLng) {
										final String text = fieldElement.getTextContent();
										try {
											final double d = Double.parseDouble(text);
											if (forLat) {
												lat = d;
											} else if (forLng) {
												lng = d;
											} else if (forSmiMjr) {
												smiMjr = d;
											} else if (forSmiMnr) {
												smiMnr = d;
											} else if (forRotationAngle) {
												rotationAngle = d;
											}
										} catch (final NumberFormatException e) {
										}
									}
								}
								final String colorName = getStringNoDefault(simCase, child, "color", stringPluses);
								extraGraphics.addEllipseGraphics(lat, lng, smiMjr, smiMnr, rotationAngle, colorName);
							}
						} catch (final SAXException e) {
							MainRunner.HandleFatal(simCase, new RuntimeException(e));
						}
						final double lat = getDouble(simCase, child, "CenterLat", "", Double.NaN, stringPluses);
						final double lng = getDouble(simCase, child, "CenterLng", "", Double.NaN, stringPluses);
						final double smiMjrNmi = getDouble(simCase, child, "SemiMajor", " NM", Double.NaN,
								stringPluses);
						final double smiMnrNmi = getDouble(simCase, child, "SemiMinor", " NM", Double.NaN,
								stringPluses);
						final double smiMjrHdg = getDouble(simCase, child, "Orientation", " degs", Double.NaN,
								stringPluses);
						if (!Double.isNaN(lat) && !Double.isNaN(lng) && !Double.isNaN(smiMjrNmi)
								&& !Double.isNaN(smiMnrNmi) && !Double.isNaN(smiMjrHdg)) {
							final String colorName = getString(simCase, child, "color", "LightGoldenRodYellow",
									stringPluses);
							extraGraphics.addEllipseGraphics(lat, lng, smiMjrNmi, smiMnrNmi, smiMjrHdg, colorName);
						}
					}
					break;
				case LL_GRID_GAPS:
					final double latGap = getDouble(simCase, child, "LatGap", "", Double.NaN, stringPluses);
					final double lngGap = getDouble(simCase, child, "LngGap", "", Double.NaN, stringPluses);
					if (!Double.isNaN(latGap) && !Double.isNaN(lngGap)) {
						extraGraphics.addLatLngGridGaps(latGap, lngGap);
					}
					break;
				case POINT_TO_DRAW:
				case LL_EDGE:
					final String color = getString(simCase, child, "color", "black", stringPluses);
					if (graphicsType == Graphics.GraphicsType.POINT_TO_DRAW) {
						try {
							final LatLng3 latLngToDraw = readLatLng(simCase, child, stringPluses);
							if (color != null) {
								extraGraphics.addPointToDrawGraphics(latLngToDraw.getLat(), latLngToDraw.getLng(),
										color);
							}
						} catch (final ReaderException e) {
						}
					} else {
						final double lat0 = getDouble(simCase, child, "lat0", "", Double.NaN, stringPluses);
						final double lng0 = getDouble(simCase, child, "lng0", "", Double.NaN, stringPluses);
						if (!Double.isNaN(lat0) || !Double.isNaN(lng0)) {
							double lat1 = getDouble(simCase, child, "lat1", "", Double.NaN, stringPluses);
							double lng1 = getDouble(simCase, child, "lng1", "", Double.NaN, stringPluses);
							if (Double.isNaN(lat1) || Double.isNaN(lng1)) {
								final double hdg = getDouble(simCase, child, "hdg", " degs", Double.NaN, stringPluses);
								if (0d <= hdg && hdg < 360d) {
									final double meters = getDouble(simCase, child, "length", " M", Double.NaN,
											stringPluses);
									if (meters >= 0d) {
										final LatLng3 latLng0 = LatLng3.getLatLngB(lat0, lng0);
										final double nmi = meters / Constants._NmiToM;
										final GreatCircleArc gca = GreatCircleArc.CreateGca(latLng0, hdg, nmi);
										if (gca != null && !gca.isDegenerate()) {
											final LatLng3 latLng1 = gca.getLatLng1();
											lat1 = latLng1.getLat();
											lng1 = latLng1.getLng();
										}
									}
								}
							}
							final int number = getInt(simCase, child, "number", "", -1, stringPluses);
							final long t0;
							final long t1;
							final String t0String = child.getAttribute("time0");
							final String t1String = child.getAttribute("time1");
							if (t0String == null || t0String.length() == 0 || t1String == null
									|| t1String.length() == 0) {
								t0 = t1 = -1L;
							} else {
								t0 = TimeUtilities.dtgToRefSecs(t0String);
								t1 = TimeUtilities.dtgToRefSecs(t1String);
							}
							if (!Double.isNaN(lat1) && !Double.isNaN(lng1) && color != null) {
								extraGraphics.addLatLngEdgeGraphics(lat0, lng0, lat1, lng1, number, t0, t1, color);
							}
						}
					}
					break;
				case EXTENT:
					final String extentColor = getString(simCase, child, "color", "black", stringPluses);
					final double left = getDouble(simCase, child, "left", "", Double.NaN, stringPluses);
					final double low = getDouble(simCase, child, "low", "", Double.NaN, stringPluses);
					final double right = getDouble(simCase, child, "right", "", Double.NaN, stringPluses);
					final double high = getDouble(simCase, child, "high", "", Double.NaN, stringPluses);
					final int number = getInt(simCase, child, "number", "", -1, stringPluses);
					if (!Double.isNaN(left) && !Double.isNaN(low) && !Double.isNaN(right) && !Double.isNaN(high)
							&& extentColor != null) {
						extraGraphics.addExtentGraphics(left, low, right, high, number, extentColor);
					}

					break;
				default:
					break;
				}
			} catch (final DOMException e) {
				SimCaseManager.err(simCase, "DomException");
				e.printStackTrace();
			} catch (final ReaderException e) {
				SimCaseManager.err(simCase, "ReaderException");
				e.printStackTrace();
			}
		}
	}

	public static LatLng3 readLatLng(final SimCaseManager.SimCase simCase, final Element element,
			final TreeSet<StringPlus> stringPluses) throws ReaderException {
		final String latString = getStringNoDefault(simCase, element, "lat", stringPluses);
		final String lngString = getStringNoDefault(simCase, element, "lng", stringPluses);
		final String latLngString = latString + " " + lngString;
		if (_ReadLatLngTo6Only) {
			return LatLng3.getLatLngD6(latLngString);
		}
		return LatLng3.getLatLngD(latLngString);
	}

	/**
	 * Reads the legs of a given sortie.
	 *
	 * @param element the node containing the data.
	 * @param sortie  the sortie to be populated.
	 * @param model   the model being populated.
	 * @throws ReaderException when an incorrect definition is given.
	 */
	private static void readSortieLegs(final SimCaseManager.SimCase simCase, final Element element, final Sortie sortie,
			final double speedKts, final long sortieStartTime, final TreeSet<StringPlus> stringPluses)
			throws ReaderException {
		final boolean computeTimes = !Double.isNaN(speedKts) && sortieStartTime >= 0;
		long lastRefSecs = _UnsetTime;
		LatLng3 lastLatLng = null;
		final ElementIterator childIterator = new ElementIterator(element);
		while (childIterator.hasNextElement()) {
			final Element child = childIterator.nextElement();
			final String childTag = child.getTagName();
			if ("WAYPOINT".equals(childTag)) {
				final LatLng3 currentLatLng = readLatLng(simCase, child, stringPluses);
				final long currentRefSecs;
				if (computeTimes) {
					if (lastLatLng == null) {
						currentRefSecs = sortieStartTime;
					} else {
						final double nmi = GreatCircleCalculator.getNmi(lastLatLng, currentLatLng);
						final long deltaSecs = Math.round(3600d * nmi / speedKts);
						currentRefSecs = lastRefSecs + deltaSecs;
					}
					lastRefSecs = currentRefSecs;
				} else {
					currentRefSecs = readDtg(simCase, child, stringPluses);
				}
				if (lastLatLng != null) {
					sortie.addLegIfNotVacuous(lastLatLng, currentLatLng, lastRefSecs, currentRefSecs);
				}
				lastLatLng = currentLatLng;
				lastRefSecs = currentRefSecs;
			} else {
				AnnounceIgnoreTag(simCase, childTag);
			}
		}
	}

	/** Reads the scenario and updates the model. */
	private static void readScenario(final SimCaseManager.SimCase simCase, final HashSet<Short> usedScenarioIds,
			final Element element, final Model model, final TreeSet<StringPlus> stringPluses) throws ReaderException {
		final int nParticlesPerScenario = model.getNParticlesPerScenario();
		final String unit = null;
		short idX = getShort(simCase, element, "id", unit, stringPluses);
		while (!usedScenarioIds.add(idX)) {
			idX *= 10;
		}
		final short id = idX;
		final String name = getString(simCase, element, "name", "", stringPluses);
		final String preType = getString(simCase, element, "type", "", /* stringPluses= */null);
		final String type;
		if (preType.equalsIgnoreCase(Scenario._RegularScenarioType) || preType.length() == 0) {
			type = Scenario._RegularScenarioType;
		} else if (preType.equalsIgnoreCase(Scenario._LobType)) {
			getString(simCase, element, "type", "", stringPluses);
			type = Scenario._LobType;
		} else if (preType.equalsIgnoreCase(Scenario._FlareType)) {
			getString(simCase, element, "type", "", stringPluses);
			type = Scenario._FlareType;
		} else if (preType.equalsIgnoreCase(Scenario._R21Type)) {
			getString(simCase, element, "type", "", stringPluses);
			type = Scenario._R21Type;
		} else {
			type = null;
		}
		final double scenarioWeight = getWeight(simCase, element, "weight", stringPluses);
		if (scenarioWeight > 0d) {
			if (type == Scenario._RegularScenarioType) {
				/** A Regular (Voyage, DR, LKP, etc) scenario. */
				final RegularScenario regularScenario = model.addRegularScenario(simCase, id, name, scenarioWeight,
						nParticlesPerScenario);
				/** Check to see if there IS a SailElement. */
				boolean sailElementExists = false;
				final ElementIterator childIterator0 = new ElementIterator(element);
				while (childIterator0.hasNextElement()) {
					final Element child = childIterator0.nextElement();
					final String tagName = child.getTagName();
					if ("SAIL".equals(tagName)) {
						sailElementExists = true;
						break;
					}
				}
				final ElementIterator childIterator = new ElementIterator(element);
				Element sailElement = null;
				while (childIterator.hasNextElement()) {
					final Element child = childIterator.nextElement();
					final String tagName = child.getTagName();
					/** Add the path and object types. */
					if ("PATH".equals(tagName)) {
						long distressRefSecsMean = -1;
						double distressPlusMinusHrs = -1d;
						try {
							distressRefSecsMean = readDtg(simCase, element, "distressTime", /* stringPluses= */null);
							distressPlusMinusHrs = getDouble(simCase, element, "distressTimePlusOrMinus", " hrs", -1d,
									/* stringPluses= */null);
						} catch (final Exception e) {
							/**
							 * This exception indicates no distress time given.
							 */
						}
						if (((distressRefSecsMean < 0) || (distressPlusMinusHrs < 0d))) {
							distressRefSecsMean = -1;
							distressPlusMinusHrs = -1d;
						}
						readPath(simCase, model, child, regularScenario, distressRefSecsMean, distressPlusMinusHrs,
								sailElementExists, stringPluses);
					} else if ("SCEN_OBJECT_TYPE".equals(tagName)) {
						readSearchObject(simCase, child, regularScenario, model, stringPluses);
					} else if ("SAIL".equals(tagName)) {
						sailElement = child;
						continue;
					} else {
						AnnounceIgnoreTag(simCase, tagName);
					}
				}
				final PreDistressModel preDistressModel = regularScenario.getPreDistressModel();
				if (preDistressModel != null && sailElement != null) {
					final SailData sailData = readSailData(simCase, model, preDistressModel, sailElement, stringPluses);
					if (sailData == null) {
						SimCaseManager.err(simCase, "Bad Sail Data Tag.");
					}
					regularScenario.setSailData(sailData);
				}
			} else if (type == Scenario._LobType || type == Scenario._FlareType || type == Scenario._R21Type) {
				final boolean isR21Type = type == Scenario._R21Type;
				/**
				 * LOB and Flare scenario. Verify that we have a decent Scenario.
				 */
				ElementIterator childIterator = new ElementIterator(element);
				TimeDistribution timeDistribution = null;
				boolean haveSighting = isR21Type;
				boolean haveSearchObjectType = false;
				while (childIterator.hasNextElement()) {
					final Element child = childIterator.nextElement();
					final String tagName = child.getTagName();
					if ("TIME".equals(tagName)) {
						timeDistribution = readTimeDistribution(simCase, child, /* readAsDuration= */false,
								stringPluses);
					} else if ("SIGHTING".equals(tagName) || "BEARING_CALL".equals(tagName)
							|| "ELLIPSE".equals(tagName)) {
						haveSighting = true;
					} else if ("SCEN_OBJECT_TYPE".equals(tagName)) {
						haveSearchObjectType = true;
					} else {
						AnnounceIgnoreTag(simCase, tagName);
					}
				}
				if (timeDistribution != null && haveSighting && haveSearchObjectType) {
					/**
					 * Create the Scenario and add the bearing calls and ellipses to it.
					 */
					final double areaThresholdSqNmi = getDouble(simCase, element, "wangsnessAreaThreshold", "NMSq",
							Double.NaN, stringPluses);
					final double distanceThresholdNmi = getDouble(simCase, element, "wangsnessDistanceThreshold", "NM",
							Double.NaN, stringPluses);
					final double smiMjrThresholdNmi = getDouble(simCase, element, "wangsnessSemiMajorThreshold", "NM",
							Double.NaN, stringPluses);
					final double mjrToMnr = getDouble(simCase, element, "wangsnessMajorToMinor", "", Double.NaN,
							stringPluses);
					final double minAngleD = getDouble(simCase, element, "wangsnessMinAngle", "Degs", Double.NaN,
							stringPluses);
					final Thresholds wangsnessThresholds;
					if (Double.isNaN(areaThresholdSqNmi) || Double.isNaN(distanceThresholdNmi)
							|| Double.isNaN(smiMjrThresholdNmi) || Double.isNaN(mjrToMnr) || Double.isNaN(minAngleD)) {
						wangsnessThresholds = null;
					} else {
						wangsnessThresholds = new Thresholds(areaThresholdSqNmi, distanceThresholdNmi,
								smiMjrThresholdNmi, mjrToMnr, minAngleD);
					}
					final LobScenario lobScenario = model.addLobScenario(simCase, id, name,
							isR21Type ? Scenario._LobType : type, wangsnessThresholds, scenarioWeight,
							nParticlesPerScenario, timeDistribution);
					if (isR21Type) {
						final double bearingSd = getDouble(simCase, element, "r21BearingStandardDeviation", " Degs",
								Double.NaN, stringPluses);
						final double centerProbableErrorNmi = getDouble(simCase, element, "r21CenterProbableError",
								" NM", Double.NaN, stringPluses);
						readR21Message(simCase, model, bearingSd, centerProbableErrorNmi, lobScenario);
					}
					childIterator = new ElementIterator(element);
					while (childIterator.hasNextElement()) {
						final Element child = childIterator.nextElement();
						final String tagName = child.getTagName();
						if ("SIGHTING".equals(tagName) || "BEARING_CALL".equals(tagName)) {
							readBearingCall(simCase, child, lobScenario, stringPluses);
						} else if ("ELLIPSE".equals(tagName)) {
							readEllipse(simCase, child, lobScenario, stringPluses);
						} else if ("SCEN_OBJECT_TYPE".equals(tagName)) {
							readSearchObject(simCase, child, lobScenario, model, stringPluses);
						} else {
							continue;
						}
					}
				}
			}
		}
	}

	/** Used to log a surprising tag. */
	public static void AnnounceIgnoreTag(final SimCaseManager.SimCase simCase, final String tagName) {
		SimCaseManager.out(simCase, String.format("Ignoring tag[%s].", tagName));
	}

	/**
	 * Read a SearchObjectTypeWithWeight.
	 *
	 * @param element  the node containing the information.
	 * @param scenario the scenario for this SearchObjectType.
	 * @param model    the model being populated.
	 * @throws ReaderException when an incorrect definition is given.
	 */
	private static void readSearchObject(final SimCaseManager.SimCase simCase, final Element element,
			final Scenario scenario, final Model model, final TreeSet<StringPlus> stringPluses) throws ReaderException {
		final String unit = null;
		final int id = getInt(simCase, element, "id", unit, stringPluses);
		final SearchObjectType distressSot = model.getSearchObjectType(id);
		if (distressSot == null) {
			throw new ReaderException("Unknown search object type " + id);
		}
		final double weight = getWeight(simCase, element, "weight", stringPluses);
		final SotWithWt searchObjectTypeWithWeight = new SotWithWt(distressSot, weight);
		scenario.add(searchObjectTypeWithWeight);
	}

	/**
	 * Reads one of the three kinds of path for a scenario.
	 *
	 * @param element           the node containing the information.
	 * @param regularScenario   the scenario to be populated.
	 * @param model             the model to be populated.
	 * @param sailElementExists
	 * @throws ReaderException when an incorrect definition is given.
	 */
	private static void readPath(final SimCaseManager.SimCase simCase, final Model model, final Element element,
			final RegularScenario regularScenario, final long distressRefSecsMean, final double distressPlusMinusHrs,
			final boolean sailElementExists, final TreeSet<StringPlus> stringPluses) throws ReaderException {
		PreDistressModel preDistressModel = null;
		final ElementIterator childIterator = new ElementIterator(element);
		/**
		 * Reading a path means the following: First element should be the departure
		 * location. There will be at most one subsequent element and it will be either
		 * a VOYAGE or a DEAD_RECKON tag.
		 */
		while (childIterator.hasNextElement()) {
			final Element child = childIterator.nextElement();
			final String childTag = child.getTagName();
			if ("DEPARTURE_LOCATION".equals(childTag)) {
				readDepartureArea(simCase, child, regularScenario, stringPluses);
			} else if ("VOYAGE".equals(childTag)) {
				final boolean noDistress = getBoolean(simCase, child, "NoDistress", false, /* stringPluses= */null);
				if (noDistress) {
					regularScenario.setNoDistressToTrue();
				}
				preDistressModel = readVoyage(simCase, child, regularScenario, distressRefSecsMean,
						distressPlusMinusHrs, sailElementExists, stringPluses);
				if (!preDistressModel.getAreas().isEmpty()) {
					regularScenario.setPreDistressModel(preDistressModel);
				}
				break;
			} else if ("DEAD_RECKON".equals(childTag)) {
				final boolean noDistress = getBoolean(simCase, child, "NoDistress", false, /* stringPluses= */null);
				if (noDistress) {
					regularScenario.setNoDistressToTrue();
				}
				preDistressModel = readDeadReckon(simCase, child, regularScenario, distressRefSecsMean,
						distressPlusMinusHrs, stringPluses);
				regularScenario.setPreDistressModel(preDistressModel);
				break;
			} else {
				throw new ReaderException("Unexpected tag " + childTag + " under " + element.getTagName());
			}
		}
	}

	final private static SailData readSailData(final SimCaseManager.SimCase simCase, final Model model,
			final PreDistressModel preDistressModel, final Element sailElement,
			final TreeSet<StringPlus> stringPluses) {
		final ElementIterator elementIterator = new ElementIterator(sailElement);
		while (elementIterator.hasNextElement()) {
			final Element sailorTypeElement = elementIterator.nextElement();
			final String elementTopTag = sailorTypeElement.getTagName();
			if ("SAILOR_TYPE".equals(elementTopTag)) {
				final SailData sailData = new SailData(simCase, model, sailorTypeElement, stringPluses);
				return sailData;
			}
		}
		return null;
	}

	/**
	 * Reads in a succession of legs, defining a Voyage.
	 *
	 * @param sailElementExists
	 */
	private static Voyage readVoyage(final SimCaseManager.SimCase simCase, final Element element,
			final Scenario scenario, final long distressRefSecsMean, final double distressPlusMinusHrs,
			final boolean sailElementExists, final TreeSet<StringPlus> stringPluses) throws ReaderException {
		/** Note: Voyage is a "PreDistressModel. */
		final Voyage voyage = new Voyage(scenario, distressRefSecsMean, distressPlusMinusHrs);
		final ElementIterator childIterator = new ElementIterator(element);
		while (childIterator.hasNextElement()) {
			final Element child = childIterator.nextElement();
			final String tagName = child.getTagName();
			/** Just look for LEG and FINAL_LEG here. */
			if (!tagName.equals("LEG") && !tagName.equals("FINAL_LEG")) {
				continue;
			}
			/**
			 * Whenever we read in the speeds, straighten them out. The convention of the
			 * code assumes that minSpeed, cruisingSpeed, maxSpeed are straightened out as
			 * soon as they are read in.
			 */
			final String motionTypeId = getString(simCase, child, "motion_type", "GC", stringPluses);
			final double minSpeed, cruisingSpeed, maxSpeed;
			if (sailElementExists) {
				minSpeed = cruisingSpeed = maxSpeed = Double.NaN;
			} else {
				final double[] speeds = new double[3];
				speeds[0] = getDouble(simCase, child, "minSpeed", " kts", stringPluses);
				speeds[1] = getDouble(simCase, child, "cruisingSpeed", " kts", stringPluses);
				speeds[2] = getDouble(simCase, child, "maxSpeed", " kts", stringPluses);
				Arrays.sort(speeds);
				minSpeed = speeds[0];
				cruisingSpeed = speeds[1];
				maxSpeed = speeds[2];
			}
			Area legArea = null;
			TimeDistribution dwellTimeDistributionForLeg = null;
			final ElementIterator grandChildIterator = new ElementIterator(child);
			final boolean last = "FINAL_LEG".equals(tagName);
			while (grandChildIterator.hasNextElement()) {
				final Element grandChild = grandChildIterator.nextElement();
				final boolean interpretAsHazard = false;
				final boolean isUniform = false;
				final double truncateDistanceNmi = Double.POSITIVE_INFINITY;
				final Area area = readArea(simCase, grandChild, interpretAsHazard, isUniform, truncateDistanceNmi,
						stringPluses);
				if (area != null) {
					legArea = area;
				} else {
					if (!last) {
						dwellTimeDistributionForLeg = readTimeDistribution(simCase, grandChild,
								/* readAsDuration= */true, stringPluses);
					} else {
						dwellTimeDistributionForLeg = null;
					}
				}
			}
			voyage.addLeg(legArea, minSpeed, cruisingSpeed, maxSpeed, dwellTimeDistributionForLeg,
					MotionType.get(motionTypeId));
		}
		return voyage;
	}

	private static DeadReckon readDeadReckon(final SimCaseManager.SimCase simCase, final Element element,
			final Scenario scenario, final long distressRefSecsMean, final double distressPlusMinusHrs,
			final TreeSet<StringPlus> stringPluses) throws ReaderException {

		final double hdg = getDouble(simCase, element, "course", " T", stringPluses);
		final double courseErrorD = getDouble(simCase, element, "courseError", " deg", stringPluses);
		final double[] speeds = new double[3];
		speeds[0] = getDouble(simCase, element, "minSpeed", " kts", stringPluses);
		speeds[1] = getDouble(simCase, element, "cruisingSpeed", " kts", stringPluses);
		speeds[2] = getDouble(simCase, element, "maxSpeed", " kts", stringPluses);
		Arrays.sort(speeds);
		final double minSpeed = speeds[0];
		final double cruisingSpeed = speeds[1];
		final double maxSpeed = speeds[2];
		final String motionTypeId = getString(simCase, element, "motion_type", "GC", stringPluses);
		long maxDistressRefSecs = Long.MAX_VALUE;
		final boolean haveDistressSpecs = distressPlusMinusHrs >= 0d;
		if (!scenario.getNoDistress() && !haveDistressSpecs) {
			final ElementIterator childIterator = new ElementIterator(element);
			while (childIterator.hasNextElement()) {
				final Element child = childIterator.nextElement();
				if ("DISTRESS_TIME".equals(child.getTagName())) {
					maxDistressRefSecs = readDtg(simCase, child, stringPluses);
				}
			}
			if (maxDistressRefSecs == Long.MAX_VALUE) {
				try {
					final String dtg = getStringNoDefault(simCase, element, "DISTRESS_TIME", stringPluses);
					final long ell = TimeUtilities.dtgToRefSecs(dtg);
					if (ell > 0) {
						maxDistressRefSecs = ell;
					}
				} catch (final ReaderException e) {
				}
			}
			if (maxDistressRefSecs == Long.MAX_VALUE) {
				SimCaseManager.err(simCase,
						String.format("\nNo distress time given!  " + "Particles do not go into distress."));
			}
		}
		final long minArrivalRefSecs = Long.MAX_VALUE / 2;
		return new DeadReckon(scenario, distressRefSecsMean, distressPlusMinusHrs, hdg, courseErrorD, minSpeed,
				cruisingSpeed, maxSpeed, maxDistressRefSecs, minArrivalRefSecs, MotionType.get(motionTypeId));
	}

	private static void readDepartureArea(final SimCaseManager.SimCase simCase, final Element element,
			final Scenario scenario, final TreeSet<StringPlus> stringPluses) throws ReaderException {
		Area departureArea = null;
		TimeDistribution departureTimeDistribution = null;
		final ElementIterator childIterator = new ElementIterator(element);
		final boolean isUniform = getBoolean(simCase, element, "uniform", false, stringPluses);
		final double truncateDistanceNmi = getDouble(simCase, element, "truncate_distance", " NM",
				Double.POSITIVE_INFINITY, /* stringPluses= */null);
		if (!Double.isInfinite(truncateDistanceNmi)) {
			getDouble(simCase, element, "truncate_distance", " NM", Double.POSITIVE_INFINITY, stringPluses);
		}
		while (childIterator.hasNextElement()) {
			final Element child = childIterator.nextElement();
			/** Try first the departure area. */
			if (departureArea == null) {
				final boolean interpretAsHazard = false;
				departureArea = readArea(simCase, child, interpretAsHazard, isUniform, truncateDistanceNmi,
						stringPluses);
				if (departureArea != null) {
					/** If we got an area out of this, go to the next child. */
					continue;
				}
			}
			/**
			 * We did not successfully read in the departureArea with child.
			 */
			if (departureTimeDistribution == null) {
				departureTimeDistribution = readTimeDistribution(simCase, child, /* readAsDuration= */false,
						stringPluses);
				if (departureTimeDistribution != null) {
					continue;
				}
			}
		}
		if (departureArea == null) {
			throw new ReaderException("Missing departure area definition for scenario " + scenario.getId());
		}
		if (departureTimeDistribution == null) {
			throw new ReaderException("Missing departure time definition for scenario " + scenario.getId());
		}
		scenario.setDepartureArea(departureArea);
		scenario.setDepartureTimeDistribution(departureTimeDistribution);
	}

	/**
	 * Reads an absolute or relative time distribution.
	 *
	 * @param element        the node containing the information.
	 * @param model          the model to be populated.
	 * @param readAsDuration a flag indicating that we read a time or a duration.
	 * @return the corresponding distribution description.
	 * @throws ReaderException when an incorrect definition is given.
	 */
	private static TimeDistribution readTimeDistribution(final SimCaseManager.SimCase simCase, final Element element,
			final boolean readAsDuration, final TreeSet<StringPlus> stringPluses) throws ReaderException {
		final String tagName = element.getTagName();
		if (readAsDuration) {
			if (!"DWELL_TIME".equals(tagName)) {
				return null;
			}
		} else {
			if (!"TIME".equals(tagName)) {
				return null;
			}
		}
		final int plusMinusInMinutes = getDurationMinutes(simCase, element, "plus_minus", stringPluses);
		int timeInMinutes;
		if (readAsDuration) {
			timeInMinutes = getDurationMinutes(simCase, element, "duration", stringPluses);
		} else {
			timeInMinutes = (int) (readDtg(simCase, element, stringPluses) / 60);
		}
		return new TimeDistribution(timeInMinutes, plusMinusInMinutes);
	}

	public static long readDtg(final SimCaseManager.SimCase simCase, final Element element, final String attNameCore,
			final TreeSet<StringPlus> stringPluses) throws ReaderException {
		String s = null;
		try {
			s = getStringNoDefault(simCase, element, attNameCore + "s", stringPluses);
		} catch (final Exception e1) {
			s = getStringNoDefault(simCase, element, attNameCore, stringPluses);
		}
		return TimeUtilities.dtgToRefSecs(s);
	}

	public static long readDtg(final SimCaseManager.SimCase simCase, final Element element,
			final TreeSet<StringPlus> stringPluses) throws ReaderException {
		return readDtg(simCase, element, "dtg", stringPluses);
	}

	/**
	 * Reads a position distribution. This distribution can be a bivariate normal or
	 * a uniform polygon.
	 *
	 * @param element the node containing the information.
	 * @return the corresponding area distribution.
	 * @throws ReaderException when an incorrect definition is given.
	 */
	private static Area readArea(final SimCaseManager.SimCase simCase, final Element element,
			final boolean interpretAsHazard, final boolean isUniform, final double truncateDistanceNmi,
			final TreeSet<StringPlus> stringPluses) throws ReaderException {
		final MyLogger logger = SimCaseManager.getLogger(simCase);
		final String tagName = element.getTagName();
		if ("POLYGON".equals(tagName)) {
			final List<LatLng3> latLngList = new ArrayList<>();
			final ElementIterator childIterator = new ElementIterator(element);
			while (childIterator.hasNextElement()) {
				final Element child = childIterator.nextElement();
				try {
					final LatLng3 latLng = readLatLng(simCase, child, stringPluses);
					latLngList.add(latLng);
				} catch (final ReaderException e) {
				}
			}
			return new Polygon(logger, latLngList.toArray(new LatLng3[latLngList.size()]));
		} else if ("POINT".equals(tagName)) {
			final LatLng3 centralLatLng = readLatLng(simCase, element, stringPluses);
			double radius50Nmi = Double.NaN;
			final String[] attributeNames = {
					"probable_error", "x_error", "radius"
			};
			for (final String attributeName : attributeNames) {
				try {
					radius50Nmi = getDouble(simCase, element, attributeName, " NM", /* stringPluses= */null);
				} catch (final ReaderException ignored) {
				}
				if (!Double.isNaN(radius50Nmi)) {
					break;
				}
			}
			double x50smiMjrNmi = Double.NaN;
			double x50smiMnrNmi = Double.NaN;
			double xsmiMjrHdg = Double.NaN;
			if (Double.isNaN(radius50Nmi)) {
				try {
					x50smiMjrNmi = getDouble(simCase, element, "semiMajor_probable_error", " NM",
							/* stringPluses= */null);
				} catch (final ReaderException ignored) {
				}
				try {
					x50smiMnrNmi = getDouble(simCase, element, "semiMinor_probable_error", " NM",
							/* stringPluses= */null);
				} catch (final ReaderException ignored) {
				}
				try {
					xsmiMjrHdg = getDouble(simCase, element, "semiMajor_orientation", " T", /* stringPluses= */null);
				} catch (final ReaderException ignored) {
				}
			}

			if (!interpretAsHazard) {
				/**
				 * Not a hazard. probableErrorNM is a containment radius for containment = 0.5;
				 * must convert it to a standard deviation. Unless we're "Uniform."
				 */
				if (!Double.isNaN(radius50Nmi)) {
					final double sigmaANmi;
					if (!isUniform) {
						sigmaANmi = BivariateNormalCdf.containmentRadiiToStandardDeviations(0.5, radius50Nmi,
								radius50Nmi)[0];
					} else {
						sigmaANmi = radius50Nmi;
					}
					final double sigmaBNmi = sigmaANmi;
					final double dirA_R = 0.0;
					return new EllipticalArea(logger, centralLatLng, sigmaANmi, sigmaBNmi, dirA_R, isUniform,
							truncateDistanceNmi);
				}
				/** probableErrorNmi is NaN. We have 2 probable errors given. */
				final double sigmaANmi;
				final double sigmaBNmi;
				if (!isUniform) {
					final double[] sigmas = BivariateNormalCdf.containmentRadiiToStandardDeviations(0.5, x50smiMjrNmi,
							x50smiMnrNmi);
					sigmaANmi = sigmas[0];
					sigmaBNmi = sigmas[1];
				} else {
					sigmaANmi = x50smiMjrNmi;
					sigmaBNmi = x50smiMnrNmi;
				}
				final double dirA_R = Math.toRadians(90d - xsmiMjrHdg);
				return new EllipticalArea(logger, centralLatLng, sigmaANmi, sigmaBNmi, dirA_R, isUniform,
						truncateDistanceNmi);
			}
			/**
			 * Hazard. probableErrorNM is already the one-dimensional standard deviation; no
			 * need to convert to a standard deviation.
			 */
			final double sigmaA_NM = radius50Nmi;
			return new EllipticalArea(logger, centralLatLng, sigmaA_NM);
		}
		return null;
	}

	private static void readBearingCall(final SimCaseManager.SimCase simCase, final Element bearingCallElement,
			final LobScenario lobScenario, final TreeSet<StringPlus> stringPluses) {
		EllipticalArea centerArea = null;
		double calledBearing = Double.NaN;
		double bearingSd = Double.NaN;
		double minRangeNmi = Double.NaN;
		double maxRangeNmi = Double.NaN;
		final ElementIterator childIterator = new ElementIterator(bearingCallElement);
		while (childIterator.hasNextElement()) {
			final Element child = childIterator.nextElement();
			final String childTag = child.getTagName();
			try {
				if (childTag.compareTo("POINT") == 0) {
					/**
					 * We set this to false so that the standard deviation is interpreted as
					 * probable error.
					 */
					final boolean interpretAsHazard = false;
					final boolean isUniform = false;
					final double truncateDistanceNmi = Double.POSITIVE_INFINITY;
					/**
					 * The probable error is read in but converted to a standard deviation by
					 * readArea. Hence, the resulting standard deviation of centerArea below is a
					 * standard deviation, and not a probable error.
					 */
					centerArea = (EllipticalArea) readArea(simCase, child, interpretAsHazard, isUniform,
							truncateDistanceNmi, stringPluses);
				} else if (childTag.compareTo("BEARING") == 0) {
					calledBearing = getDouble(simCase, child, "center", " T", stringPluses);
					final double sd = getDouble(simCase, child, "sd", " deg", Double.NaN, stringPluses);
					if (Double.isNaN(sd)) {
						bearingSd = getDouble(simCase, child, "plus_minus", " deg", stringPluses);
					} else {
						bearingSd = sd;
					}
				} else if (childTag.compareTo("RANGE") == 0) {
					minRangeNmi = getDouble(simCase, child, "min", " NM", stringPluses);
					maxRangeNmi = getDouble(simCase, child, "max", " NM", stringPluses);
				}
			} catch (final ReaderException e) {
				e.printStackTrace();
				return;
			}
		}
		lobScenario.addBearingCall(simCase, centerArea, calledBearing, bearingSd, minRangeNmi, maxRangeNmi);
	}

	private static void readR21Message(final SimCase simCase, final Model model, final double bearingSd,
			final double centerProbableErrorNmi, final LobScenario lobScenario) {
		final File r21MessageFile = model.getR21MessageFile();
		Document document = null;
		try (final FileInputStream r21InputStream = new FileInputStream(r21MessageFile)) {
			try {
				document = LsFormatter._DocumentBuilder.parse(r21InputStream);
			} catch (final SAXException e) {
				e.printStackTrace();
			}
		} catch (final IOException e) {
		}
		final Element rootElement = document.getDocumentElement();
		final Element fixElement = findElement(rootElement, "Fix");
		if (fixElement == null) {
			return;
		}
		final ElementIterator elobIterator = new ElementIterator(fixElement);
		while (elobIterator.hasNextElement()) {
			final Element elobElement = elobIterator.nextElement();
			final String elobTag = elobElement.getTagName();
			if (elobTag.equalsIgnoreCase("elob")) {
				final ElementIterator fieldIterator = new ElementIterator(elobElement);
				double lat = Double.NaN;
				double lng = Double.NaN;
				double bearing = Double.NaN;
				double length = Double.NaN;
				while (fieldIterator.hasNextElement()) {
					final Element fieldElement = fieldIterator.nextElement();
					final String fieldTag = fieldElement.getTagName();
					final String fieldTagLc = fieldTag.toLowerCase();
					final boolean forLat = fieldTagLc.equals("lat");
					final boolean forLng = fieldTagLc.equals("lon");
					final boolean forBearing = fieldTagLc.equals("bearing");
					final boolean forLength = fieldTagLc.equals("length");
					if (forLat || forLng || forBearing || forLength) {
						final String text = fieldElement.getTextContent();
						try {
							final double d = Double.parseDouble(text);
							if (forLat) {
								lat = d;
							} else if (forLng) {
								lng = d;
							} else if (forBearing) {
								bearing = d;
							} else {
								length = d;
							}
						} catch (final NumberFormatException e) {
						}
					}
				}
				/** Build the Origin's uncertainty. */
				final LatLng3 centralLatLng = LatLng3.getLatLngB(lat, lng);
				final double sigmaXNmi = BivariateNormalCdf.containmentRadiiToStandardDeviations(0.5,
						centerProbableErrorNmi, centerProbableErrorNmi)[0];
				final EllipticalArea bivariateNormal = new EllipticalArea(SimCaseManager.getLogger(simCase),
						centralLatLng, sigmaXNmi, /* sigmaYNmi= */sigmaXNmi, /* directionOfX_R= */0d,
						/* isUniform= */false, /* truncateDistanceNmi= */Double.POSITIVE_INFINITY);
				final double minRangeNmi = 0d;
				final double maxRangeNmi = length;
				lobScenario.addBearingCall(simCase, bivariateNormal, bearing, bearingSd, minRangeNmi, maxRangeNmi);
			}
		}
	}

	private static Element findElement(final Element element, final String magicTag) {
		final String tag = element.getTagName();
		if (magicTag.equalsIgnoreCase(tag)) {
			return element;
		}
		final ElementIterator childIterator = new ElementIterator(element);
		while (childIterator.hasNextElement()) {
			final Element child = childIterator.nextElement();
			final Element winner = findElement(child, magicTag);
			if (winner != null) {
				return winner;
			}
		}
		return null;
	}

	private static void readEllipse(final SimCaseManager.SimCase simCase, final Element ellipseElement,
			final LobScenario lobScenario, final TreeSet<StringPlus> stringPluses) {
		try {
			final String latString = getStringNoDefault(simCase, ellipseElement, "lat", stringPluses);
			final String lngString = getStringNoDefault(simCase, ellipseElement, "lng", stringPluses);
			final String latLngString = latString + " " + lngString;
			final LatLng3 centralLatLng = LatLng3.getLatLngD(latLngString);
			final double smiMjrNmi = getDouble(simCase, ellipseElement, "semiMajor", " NM", stringPluses);
			final double smiMnrNmi = getDouble(simCase, ellipseElement, "semiMinor", " NM", stringPluses);
			final double smiMjrHdg = getDouble(simCase, ellipseElement, "orientation", " T", stringPluses);
			lobScenario.addEllipse(centralLatLng, smiMjrNmi, smiMnrNmi, smiMjrHdg);
		} catch (final ReaderException e) {
			e.printStackTrace();
			return;
		}
	}

	/**
	 * Reads a percentage, checking that the % sign is given.
	 *
	 * @param element       the node containing the information.
	 * @param attributeName the attribute to be evaluated.
	 * @return a decimal value.
	 * @throws ReaderException when an incorrect definition is given.
	 */
	private static double getWeight(final SimCaseManager.SimCase simCase, final Element element,
			final String attributeName, final TreeSet<StringPlus> stringPluses) throws ReaderException {
		final String stringValue = getStringNoDefault(simCase, element, attributeName, stringPluses);
		if (!stringValue.endsWith("%")) {
			throw new ReaderException("Bad perCent format " + stringValue);
		}
		final String decimalValue = stringValue.substring(0, stringValue.length() - 1);
		try {
			final double weight = Double.parseDouble(decimalValue);
			return weight / 100;
		} catch (final NumberFormatException exception) {
			throw new ReaderException(exception.getMessage());
		}
	}

	public static String getSpecialString(final String attributeName) {
		return _SpecialStrings.get(attributeName);
	}

	public static String getStringNoDefault(final SimCaseManager.SimCase simCase, final Element element,
			final String attributeName, final TreeSet<StringPlus> stringPluses) throws ReaderException {
		final Properties simProperties;
		if (simCase != null) {
			simProperties = simCase.getSimProperties();
		} else {
			simProperties = getSimPropertiesBeforeXmlOverrides(simCase);
		}
		DataType dataType = DataType.PROPERTIES_OVRD;
		final String[] keys = getKeys(simCase, element, attributeName);
		final String fullKey = keys[0];
		final String headTruncatedCoreKey = keys[1];
		String coreKey = null;
		String result = null;
		for (int iPass = 0; iPass < 2; ++iPass) {
			if (iPass == 0) {
				coreKey = fullKey;
			} else if (fullKey.toLowerCase().startsWith("disp")) {
				coreKey = fullKey.replaceFirst("^([^.]+)", "SIM");
			} else {
				break;
			}
			result = simProperties.getProperty(coreKey + ".OVERRIDE");
			result = result == null ? "" : result.trim();
			if (result.length() == 0) {
				result = simProperties.getProperty(headTruncatedCoreKey + ".OVERRIDE");
				result = result == null ? "" : result.trim();
			}
			if (result.length() == 0) {
				dataType = DataType.XML;
				result = element.getAttribute(attributeName);
				result = result == null ? "" : result.trim();
				if (result.length() == 0) {
					dataType = DataType.PROPERTIES_DFLT;
					result = simProperties.getProperty(coreKey + ".DEFAULT");
					result = result == null ? "" : result.trim();
					if (result.length() == 0) {
						result = simProperties.getProperty(headTruncatedCoreKey + ".DEFAULT");
						result = result == null ? "" : result.trim();
					}
				}
			}
			if (result.length() > 0) {
				break;
			}
		}
		if (result.length() == 0) {
			throw new ReaderException("Missing attribute " + attributeName + " for " + element.getTagName());
		}
		if (stringPluses != null) {
			addToStringPluses(constructStringPlus(dataType, coreKey, result), stringPluses);
		}
		return result;
	}

	private static StringPlus addToStringPluses(final StringPlus stringPlus, final TreeSet<StringPlus> stringPluses) {
		for (int id = 0;; ++id) {
			final StringPlus thisStringPlus = new StringPlus(stringPlus._dataType, stringPlus._key, id,
					stringPlus._value);
			if (stringPluses.add(thisStringPlus)) {
				return thisStringPlus;
			}
		}
	}

	public static TreeSet<StringPlus> addStringPluses(final TreeSet<StringPlus> newStringPluses,
			final TreeSet<StringPlus> oldStringPluses) {
		if (newStringPluses == null) {
			/** With no new ones, simply return the old ones. */
			return oldStringPluses;
		}
		final TreeSet<StringPlus> returnValue;
		if (oldStringPluses == null) {
			returnValue = new TreeSet<>(_StringPlusComparator);
		} else {
			returnValue = oldStringPluses;
		}
		for (final StringPlus stringPlus : newStringPluses) {
			addToStringPluses(stringPlus, returnValue);
		}
		return returnValue;
	}

	public static String getString(final SimCaseManager.SimCase simCase, final Element element,
			final String attributeName, final String defaultValue, final TreeSet<StringPlus> stringPluses) {
		try {
			return getStringNoDefault(simCase, element, attributeName, stringPluses);
		} catch (final ReaderException readerError) {
			if (stringPluses != null && defaultValue != null) {
				final String[] keys = getKeys(simCase, element, attributeName);
				final DataType dataType = DataType.CODE_ONLY;
				final String coreKey = keys[0];
				final StringPlus stringPlus = constructStringPlus(dataType, coreKey, defaultValue);
				addToStringPluses(stringPlus, stringPluses);
			}
			return defaultValue;
		}
	}

	private static DataType getDataTypeForStringWithBackupAttribute(final SimCaseManager.SimCase simCase,
			final Element element, final String attributeName) {
		final String[] keys = getKeys(simCase, element, attributeName);
		final String coreKey = keys[0];
		final String headTruncatedCoreKey = keys[1];
		final Properties simProperties;
		if (simCase != null) {
			simProperties = simCase.getSimProperties();
		} else {
			simProperties = getSimPropertiesBeforeXmlOverrides(simCase);
		}
		String result = simProperties.getProperty(coreKey + ".OVERRIDE");
		result = result == null ? "" : result.trim();
		if (result.length() > 0) {
			return DataType.PROPERTIES_OVRD;
		}
		result = simProperties.getProperty(headTruncatedCoreKey + ".OVERRIDE");
		result = result == null ? "" : result.trim();
		if (result.length() > 0) {
			return DataType.PROPERTIES_OVRD;
		}
		result = element.getAttribute(attributeName);
		result = result == null ? "" : result.trim();
		if (result.length() > 0) {
			return DataType.XML;
		}
		result = simProperties.getProperty(coreKey + ".DEFAULT");
		result = result == null ? "" : result.trim();
		if (result.length() > 0) {
			return DataType.PROPERTIES_DFLT;
		}
		result = simProperties.getProperty(headTruncatedCoreKey + ".DEFAULT");
		result = result == null ? "" : result.trim();
		if (result.length() > 0) {
			return DataType.PROPERTIES_DFLT;
		}
		return null;
	}

	public static String getStringWithBackupAttribute(final SimCaseManager.SimCase simCase, final Element element,
			final String preferredAttributeName, final String alternateAttributeName,
			final TreeSet<StringPlus> stringPluses) throws ReaderException {
		final DataType dataType0 = getDataTypeForStringWithBackupAttribute(simCase, element, preferredAttributeName);
		final boolean useAlternate;
		final DataType dataType;
		if (dataType0 == null || dataType0 == DataType.PROPERTIES_DFLT) {
			final DataType dataType1 = getDataTypeForStringWithBackupAttribute(simCase, element,
					alternateAttributeName);
			useAlternate = dataType1 == DataType.XML || dataType1 == DataType.PROPERTIES_OVRD;
			dataType = useAlternate ? dataType1 : dataType0;
		} else {
			useAlternate = false;
			dataType = dataType0;
		}
		final String returnValue = getStringNoDefault(simCase, element,
				useAlternate ? alternateAttributeName : preferredAttributeName, /* stringPluses= */null);
		final String[] keys = getKeys(simCase, element, preferredAttributeName);
		final StringPlus stringPlus = new StringPlus(dataType, keys[0], returnValue);
		addToStringPluses(stringPlus, stringPluses);
		return returnValue;
	}

	private static String[] getKeys(final SimCaseManager.SimCase simCase, final Element element,
			final String attributeName) {
		String coreKey = element.getTagName() + '.' + attributeName;
		String headTruncatedCoreKey = coreKey;
		Element element2 = element;
		while (true) {
			if (!(element2.getParentNode() instanceof Element)) {
				break;
			}
			element2 = (Element) element2.getParentNode();
			headTruncatedCoreKey = coreKey;
			coreKey = element2.getTagName() + '.' + coreKey;
		}
		/**
		 * If this is a special string, the coreKey is gotten from _SpecialStringsMap.
		 */
		final String specialString = getSpecialString(attributeName);
		if (specialString != null) {
			coreKey = specialString;
			final int firstDotIndex = coreKey.indexOf('.');
			if (firstDotIndex >= 0) {
				headTruncatedCoreKey = coreKey.substring(coreKey.indexOf('.') + 1);
			}
		}
		return new String[] {
				coreKey, headTruncatedCoreKey
		};
	}

	private static ArrayList<String[]> getSimProperties(final SimCaseManager.SimCase simCase, final Element element,
			final String startWith) {
		final ArrayList<String[]> properties = new ArrayList<>();
		String coreKey = element.getTagName() + '.' + startWith;
		Element element2 = element;
		while (true) {
			if (!(element2.getParentNode() instanceof Element)) {
				break;
			}
			element2 = (Element) element2.getParentNode();
			coreKey = element2.getTagName() + '.' + coreKey;
		}
		final Properties simProperties = simCase.getSimProperties();
		final Enumeration<?> e = simProperties.propertyNames();
		while (e.hasMoreElements()) {
			final String key = (String) e.nextElement();
			if (key.toLowerCase().startsWith(coreKey.toLowerCase())) {
				final String strippedPropertyName = key.substring(0, key.lastIndexOf('.'));
				final String value = simProperties.getProperty(key);
				properties.add(new String[] {
						strippedPropertyName, value
				});
			}
		}
		return properties;
	}

	public static String[] getStringFromAttributeStartingWith(final SimCase simCase, final Element element,
			final String startWith, final Collection<StringPlus> stringPluses) {
		final String attributeNameLc = startWith.toLowerCase();
		final NamedNodeMap namedNodeMap = element.getAttributes();
		for (int index = 0; index < namedNodeMap.getLength(); ++index) {
			final Node node = namedNodeMap.item(index);
			if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
				final String nodeName = node.getNodeName();
				if (nodeName.toLowerCase().startsWith(attributeNameLc)) {
					return new String[] {
							nodeName, node.getNodeValue()
					};
				}
			}
		}
		final ArrayList<String[]> properties = getSimProperties(simCase, element, startWith);
		/** We simply take the first one. */
		return properties.isEmpty() ? null : properties.get(0);
	}

	/**
	 * Returns the boolean value of a String. True value are words starting with a
	 * y, or on, or true. The input value is converted to lower case to do the
	 * check.
	 *
	 * @param attribute the value to be tested.
	 * @return a boolean value.
	 */
	public static boolean getBoolean(final SimCaseManager.SimCase simCase, final Element element,
			final String attributeName, final boolean defaultValue, final TreeSet<StringPlus> stringPluses)
			throws ReaderException {
		try {
			return getBoolean(simCase, element, attributeName, stringPluses);
		} catch (final ReaderException readerError) {
			final String message = readerError.getMessage();
			if (message != null && message.length() > 0) {
				throw readerError;
			}
			if (stringPluses != null) {
				final String[] keys = getKeys(simCase, element, attributeName);
				final DataType dataType = DataType.CODE_ONLY;
				final String coreKey = keys[0];
				final StringPlus stringPlus = constructBooleanStringPlus(dataType, coreKey, defaultValue);
				addToStringPluses(stringPlus, stringPluses);
			}
			return defaultValue;
		}
	}

	public static boolean getBoolean(final SimCaseManager.SimCase simCase, final Element element,
			final String attributeName, final TreeSet<StringPlus> stringPluses) throws ReaderException {
		final String defaultValueX = null;
		final String fullValue = getString(simCase, element, attributeName, defaultValueX, stringPluses);
		return stringToBoolean(fullValue);
	}

	public static boolean getBoolean(final SimCaseManager.SimCase simCase, final Element element,
			final String attributeName, final String attributeName2, final boolean defaultValue,
			final TreeSet<StringPlus> stringPluses) {
		final String defaultValueX = null;
		String fullValue = getString(simCase, element, attributeName, defaultValueX, stringPluses);
		if (fullValue != null && fullValue.length() > 0) {
			try {
				return stringToBoolean(fullValue);
			} catch (final ReaderException e) {
			}
		}
		fullValue = getString(simCase, element, attributeName2, defaultValueX, stringPluses);
		if (fullValue != null && fullValue.length() > 0) {
			try {
				return stringToBoolean(fullValue);
			} catch (final ReaderException e) {
			}
		}
		return defaultValue;
	}

	private static boolean stringToBoolean(final String fullValue) throws ReaderException {
		if (fullValue == null || fullValue.length() == 0) {
			throw new ReaderException("");
		}
		final String lowerCaseFullValue = fullValue.toLowerCase();
		final Boolean b = StringUtilities.getBoolean(lowerCaseFullValue);
		if (b != null) {
			return b;
		}
		throw new ReaderException("Bad Boolean: " + fullValue);
	}

	public static long getLong(final SimCaseManager.SimCase simCase, final Element element, final String attributeName,
			final String unit, final TreeSet<StringPlus> stringPluses) throws ReaderException {
		final String fullValue = getString(simCase, element, attributeName, /* default= */null, stringPluses);
		if (fullValue == null || fullValue.length() == 0) {
			throw new ReaderException("Missing long: " + element.getTagName() + '.' + attributeName);
		}
		return stringToLong(fullValue, unit);
	}

	public static long getLong(final SimCaseManager.SimCase simCase, final Element element, final String attributeName,
			final String unit, final long defaultValue, final TreeSet<StringPlus> stringPluses) {
		final String fullValue = getString(simCase, element, attributeName, /* defaultValue= */null, stringPluses);
		if (fullValue == null || fullValue.length() == 0) {
			if (stringPluses != null) {
				final String[] keys = getKeys(simCase, element, attributeName);
				final DataType dataType = DataType.CODE_ONLY;
				final String coreKey = keys[0];
				final StringPlus stringPlus = constructLongStringPlus(dataType, coreKey, defaultValue, unit);
				addToStringPluses(stringPlus, stringPluses);
			}
			return defaultValue;
		}
		try {
			return stringToLong(fullValue, unit);
		} catch (final ReaderException e) {
		}
		return defaultValue;
	}

	public static double getDouble(final SimCaseManager.SimCase simCase, final Element element,
			final String attributeName, final String unit, final TreeSet<StringPlus> stringPluses)
			throws ReaderException {
		return getDouble(simCase, element, attributeName, unit, Double.NaN, stringPluses);
	}

	public static double getDouble(final SimCaseManager.SimCase simCase, final Element element,
			final String attributeName, final String unit, final double defaultValue,
			final TreeSet<StringPlus> stringPluses) throws ReaderException {
		final String fullValue = getString(simCase, element, attributeName, /* defaultString= */null, stringPluses);
		if (fullValue == null || fullValue.length() == 0) {
			if (stringPluses != null) {
				final String[] keys = getKeys(simCase, element, attributeName);
				final String coreKey = keys[0];
				final StringPlus stringPlus = constructDoubleStringPlus(DataType.CODE_ONLY, coreKey, defaultValue,
						unit);
				addToStringPluses(stringPlus, stringPluses);
			}
			return defaultValue;
		}
		return stringToDouble(fullValue, unit);
	}

	private static double stringToDouble(final String fullValue, final String unit) throws ReaderException {
		try {
			return Double.parseDouble(validateAndStripUnit(fullValue, unit));
		} catch (final NumberFormatException exception) {
			throw new ReaderException("Bad Double: " + fullValue);
		}
	}

	public static int getInt(final SimCaseManager.SimCase simCase, final Element element, final String attributeName,
			final String unit, final int defaultValue, final TreeSet<StringPlus> stringPluses) throws ReaderException {
		final String fullValue = getString(simCase, element, attributeName, /* defaultString= */null, stringPluses);
		if (fullValue == null || fullValue.length() == 0) {
			if (stringPluses != null) {
				final String[] keys = getKeys(simCase, element, attributeName);
				final DataType dataType = DataType.CODE_ONLY;
				final String coreKey = keys[0];
				final StringPlus stringPlus = constructIntStringPlus(dataType, coreKey, defaultValue, unit);
				addToStringPluses(stringPlus, stringPluses);
			}
			return defaultValue;
		}
		return stringToInt(fullValue, unit);
	}

	public static int getInt(final SimCaseManager.SimCase simCase, final Element element, final String attributeName,
			final String unit, final TreeSet<StringPlus> stringPluses) throws ReaderException {
		final String fullValue = getString(simCase, element, attributeName, /* default= */null, stringPluses);
		if (fullValue == null || fullValue.length() == 0) {
			throw new ReaderException("Missing int: " + element.getTagName() + '.' + attributeName);
		}
		return stringToInt(fullValue, unit);
	}

	public static short getShort(final SimCaseManager.SimCase simCase, final Element element,
			final String attributeName, final String unit, final TreeSet<StringPlus> stringPluses)
			throws ReaderException {
		final String defaultValue = null;
		final String fullValue = getString(simCase, element, attributeName, defaultValue, stringPluses);
		if (fullValue == null || fullValue.length() == 0) {
			throw new ReaderException("Missing int: " + element.getTagName() + '.' + attributeName);
		}
		return stringToShort(fullValue, unit);
	}

	public static int getDurationMinutes(final SimCaseManager.SimCase simCase, final Element element,
			final String attributeName, final TreeSet<StringPlus> stringPluses) {
		final long durationSecs = getDurationSecs(simCase, element, attributeName, stringPluses);
		if (durationSecs < 0) {
			return -1;
		}
		final long durationInMinutes = durationSecs / 60L;
		return (int) durationInMinutes;
	}

	public static int getDurationSecs(final SimCaseManager.SimCase simCase, final Element element,
			final String attributeName, final TreeSet<StringPlus> stringPluses) {
		try {
			final double durationHrs = getDouble(simCase, element, attributeName, " hrs", /* stringPluses= */null);
			if (durationHrs > 0d) {
				final int durationSecs = (int) Math.round(durationHrs * 3600d);
				return durationSecs;
			}
		} catch (final ReaderException error) {
		}
		try {
			int durationMins = getInt(simCase, element, attributeName, " DDHHMM", /* stringPluses= */null);
			durationMins = toMinutes(durationMins);
			final int durationSecs = durationMins * 60;
			return durationSecs;
		} catch (final ReaderException error2) {
		}
		try {
			final double durationMinsD = getDouble(simCase, element, attributeName, " mins", /* stringPluses= */null);
			if (durationMinsD > 0d) {
				final int durationSecs = (int) Math.round(durationMinsD * 60L);
				return durationSecs;
			}
		} catch (final ReaderException e) {
		}
		try {
			final int durationSecs = getInt(simCase, element, attributeName, " secs", /* stringPluses= */null);
			return durationSecs;
		} catch (final ReaderException e) {
		}
		return _BadDuration;
	}

	private static int toMinutes(final int ddhhmm) {
		final int dd = ddhhmm / 10000, hh = ddhhmm / 100 % 100, mm = ddhhmm % 100;
		return (dd * 24 + hh) * 60 + mm;
	}

	private static int stringToInt(final String fullValue, final String unit) throws ReaderException {
		try {
			return Integer.parseInt(validateAndStripUnit(fullValue, unit));
		} catch (final NumberFormatException exception) {
			throw new ReaderException("Bad int: " + fullValue);
		}
	}

	private static long stringToLong(final String fullValue, final String unit) throws ReaderException {
		try {
			return Long.parseLong(validateAndStripUnit(fullValue, unit));
		} catch (final NumberFormatException exception) {
			throw new ReaderException("Bad int: " + fullValue);
		}
	}

	private static short stringToShort(final String fullValue, final String unit) throws ReaderException {
		try {
			final int intValue = Integer.parseInt(validateAndStripUnit(fullValue, unit));
			if (intValue < 0) {
				return intValue == -1 ? (short) -1 : Short.MIN_VALUE;
			}
			return (short) (intValue % (-Short.MIN_VALUE));
		} catch (final NumberFormatException exception) {
			throw new ReaderException("Bad short: " + fullValue);
		}
	}

	private static String validateAndStripUnit(final String fullValue, final String unit) throws ReaderException {
		if (unit == null || unit.length() == 0) {
			return fullValue;
		} else if (!fullValue.endsWith(unit)) {
			throw new ReaderException("Missing unit \"" + unit + "\" in " + fullValue);
		} else {
			/** Strip the unit and then all trailing spaces. */
			String s = fullValue.substring(0, fullValue.length() - unit.length());
			s = s.trim();
			return s;
		}
	}

	/** The suffix here is the "-xxx.xml". */
	public static String stashEngineFile(final SimCaseManager.SimCase simCase, final String pathOfFileToStash,
			final String modelFilePath, final String suffixLc, final String subDirName, final boolean overwrite) {
		final MyLogger logger = SimCaseManager.getLogger(simCase);
		final File stashDir = AbstractOutFilesManager.GetEngineFilesDir(simCase, modelFilePath, subDirName);
		/** If suffix ends with a "-xxx.xml,", create a fileName. */
		final String nameOfFileToStash = new File(pathOfFileToStash).getName();
		final String lcName = nameOfFileToStash.toLowerCase();
		final String standardSuffixLc;
		if (suffixLc == null) {
			standardSuffixLc = "";
		} else if (suffixLc.endsWith(SimCaseManager._SimEndingLc)) {
			standardSuffixLc = SimCaseManager._SimEndingLc;
		} else if (suffixLc.endsWith(SimCaseManager._PlanEndingLc)) {
			standardSuffixLc = SimCaseManager._PlanEndingLc;
		} else {
			standardSuffixLc = suffixLc;
		}
		final String lcSuffix = standardSuffixLc.toLowerCase();
		final String coreName;
		/**
		 * Peel off the entire -xxx.xml if possible and the suffix if it exists
		 */
		if (standardSuffixLc.length() > 0 && lcName.endsWith(lcSuffix)) {
			final int suffixStartsAt = lcName.length() - lcSuffix.length();
			coreName = nameOfFileToStash.substring(0, suffixStartsAt);
		} else {
			final int lastDot = nameOfFileToStash.lastIndexOf('.');
			if (lastDot >= 0) {
				coreName = nameOfFileToStash.substring(0, lastDot);
			} else {
				coreName = nameOfFileToStash;
			}
		}
		for (int k = 0;; ++k) {
			final String distinguishingString = overwrite ? "" : String.format("@%02d", k);
			final File proposedFile = new File(stashDir, coreName + distinguishingString + standardSuffixLc);
			/**
			 * The proposed file is good if we are overwriting or it doesn't exist.
			 */
			if (overwrite || !proposedFile.exists()) {
				final File runDir = DirsTracker.getRunDir();
				/**
				 * We are planning to stash this file. We log any time that the number of .nc
				 * files has increased, because those are the big ones. Hence, we start by
				 * counting the number of .nc files.
				 */
				final FilenameFilter ncFilter = new FilenameFilter() {
					@Override
					public boolean accept(final File dir, final String name) {
						return name.toLowerCase().endsWith(".nc");
					}
				};
				final File[] priorNcFiles = runDir.listFiles(ncFilter);
				final int nPriorNcFiles = priorNcFiles == null ? 0 : priorNcFiles.length;
				/** Now get targetFilePath and stash the file. */
				final String targetFilePath = StringUtilities.getCanonicalPath(proposedFile);
				if (targetFilePath == null) {
					continue;
				}
				StaticUtilities.copyNonDirectoryFile(logger, pathOfFileToStash, targetFilePath);
				final File[] postNcFiles = runDir.listFiles(ncFilter);
				final int nPostNcFiles = postNcFiles == null ? 0 : postNcFiles.length;
				if (nPostNcFiles > nPriorNcFiles) {
					final String f = "\n@@@ Stashed %s into RunDirectory. @@@";
					SimCaseManager.out(simCase, String.format(f, targetFilePath));
				}
				return targetFilePath;
			}
		}
	}

	public static void stashSimProperties(final SimCase simCase, final String xmlFilePath, final boolean simulator) {
		final File stashDir = AbstractOutFilesManager.GetEngineFilesDir(simCase, xmlFilePath,
				simulator ? "SimInput" : "PlanInput");
		File f = null;
		for (int k = 0;; ++k) {
			final String fileName = String.format("Sim-%02d.properties", k);
			f = new File(stashDir, fileName);
			if (!f.exists()) {
				break;
			}
			f = null;
		}
		try (final FileOutputStream fos = new FileOutputStream(f)) {
			simCase.getSimProperties().store(fos, "Properties in use.");
		} catch (final Exception e) {
			SimCaseManager.err(simCase, String.format("Stashing Sim Properties caused %s", e.getMessage()));
			MainRunner.HandleFatal(simCase, new RuntimeException(e));
		}
	}

	static LrcSet getLrcSet(final SimCaseManager.SimCase simCase, final String eltString) {
		final MyLogger logger = SimCaseManager.getLogger(simCase);
		final Document document = LsFormatter.buildDocumentFromString(logger, eltString);
		final Element root = document.getDocumentElement();
		final ElementIterator eltIt = new ElementIterator(root);
		final LrcSet lrcSet = new LrcSet();
		while (eltIt.hasNextElement()) {
			final Element elt = eltIt.nextElement();
			addToLrcSet(/* simCase= */null, lrcSet, elt, /* stringPluses= */null, /* recursionLevel= */0);
		}
		return lrcSet;
	}

	static LrcSet getLrcSet(final String eltString) {
		return getLrcSet(/* simCase= */null, eltString);
	}

	/**
	 * The following is for C++ only. The API for C++'s computeSweepWidth is a dom
	 * so this routine is here and not in LrcSet.
	 */
	public static double computeSweepWidth(String eltString0) {
		eltString0 = eltString0.trim();
		final CppToJavaTracer cppToJavaTracer = new CppToJavaTracer("SweepWidthComputer");
		if (cppToJavaTracer.isActive()) {
			cppToJavaTracer.writeTrace("\nString from C++:\n" + eltString0);
		}
		final String eltString;
		if (eltString0.startsWith("<SENSOR")) {
			eltString = String.format("<LRC_SET>\n%s\n</LRC_SET>", eltString0);
		} else if (eltString0.startsWith("<LRC_SET")) {
			eltString = eltString0;
		} else {
			if (cppToJavaTracer.isActive()) {
				cppToJavaTracer.writeTrace(
						"\nInput String Must start with LRC_SET or <SENSOR:" + StringUtilities.getStackTraceString());
			}
			return -2d;
		}
		LrcSet lrcSet = null;
		try {
			lrcSet = getLrcSet(/* simCase= */null, eltString);
		} catch (final Exception e1) {
		}
		if (lrcSet == null) {
			if (cppToJavaTracer.isActive()) {
				cppToJavaTracer.writeTrace("\nFailed to Create LrcSet");
			}
			return -3d;
		}
		if (cppToJavaTracer.isActive()) {
			cppToJavaTracer.writeTrace("\nCreated LrcSet.");
		}
		try {
			final double sweepWidth = lrcSet.getSweepWidth();
			if (cppToJavaTracer.isActive()) {
				cppToJavaTracer.writeTrace(String.format("\nGot SW[%.4f] from:\n%s", sweepWidth, lrcSet.getString()));
			}
			return sweepWidth;
		} catch (final Exception e) {
			if (cppToJavaTracer.isActive()) {
				cppToJavaTracer.writeTrace("\nComputation of LrcSet's sweepWidth failed: StackTrace:"
						+ StringUtilities.getStackTraceString());
			}
		}
		return -4d;
	}
}
