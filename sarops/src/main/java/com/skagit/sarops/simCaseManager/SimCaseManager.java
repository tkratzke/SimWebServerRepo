package com.skagit.sarops.simCaseManager;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.skagit.sarops.MainSaropsObject;
import com.skagit.sarops.AbstractOutFilesManager;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.io.ModelReader;
import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.tracker.ParticlesFile;
import com.skagit.sarops.tracker.Tracker;
import com.skagit.sarops.util.SimGlobalStrings;
import com.skagit.util.DirsTracker;
import com.skagit.util.LsFormatter;
import com.skagit.util.MathX;
import com.skagit.util.SaropsDirsTracker;
import com.skagit.util.ShortAlphaStringMap;
import com.skagit.util.SizeOf;
import com.skagit.util.StaticUtilities;
import com.skagit.util.StringUtilities;
import com.skagit.util.TimeUtilities;
import com.skagit.util.WorkStopper;
import com.skagit.util.etopo.Etopo;
import com.skagit.util.gshhs.GshhsReader;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.myThreadPool.MyThreadPool;

public class SimCaseManager {

	final private static boolean _DEBUG_SIM_CASES =
			StringUtilities.getSystemProperty("Debug.SimCases",
					/* useSpaceProxy= */false) != null;

	final public static String _SimEndingLc = "-sim.xml";
	final public static String _PlanEndingLc = "-plan.xml";
	final public static String _DispEndingLc = "-disp.xml";

	final public static String _PlannerClassName = Planner.class.getName();
	final public static String _TrackerClassName = Tracker.class.getName();

	final private static int _OldMs = 5 * 60 * 1000;
	final private static int _VeryOldMs = 12 * 60 * 60 * 1000;

	final private static boolean _ForgiveMissingParticlesFile =
			System.getProperty("Forgive.Missing.ParticlesFile") != null;

	public enum ProgressState {
		WAITING("Waiting"), ACTIVE("Active"), DONE("Done"), DROPPED("Dropped");

		final private String _string;

		ProgressState(final String s) {
			_string = s;
		}

		public String getString() {
			return _string;
		}
	}

	private static SimCaseManager _OnlySimCaseManager = null;

	final public static int _EnginePriorityAdjustment = -1;
	final public static int _WorkerPriorityAdjustment = -3;

	final private static String _DisplayOnlyString = "DISPLAY ONLY";
	final private static boolean[] _LockOnSimCaseManagerProperties = {true};
	private static Properties _SimCaseManagerProperties = null;
	private static int _MaxNProcessorsToUse;
	private static int _NEngines;
	private static int _MaxNParticlesFiles;
	private static int _MaxNWorkerThreadsInOneCall;

	private static class ThreadLoggerInfo {
		final private File _logDir;
		final private String _dbgCoreName;
		final private String _outCoreName;
		final private String _wrnCoreName;
		final private String _errCoreName;
		final private MyLogger _logger;

		private ThreadLoggerInfo(final File logDir, final String dbgCoreName,
				final String outCoreName, final String wrnCoreName,
				final String errCoreName, final MyLogger logger) {
			_logDir = logDir;
			_dbgCoreName = dbgCoreName;
			_outCoreName = outCoreName;
			_wrnCoreName = wrnCoreName;
			_errCoreName = errCoreName;
			_logger = logger;
		}
	}

	final private TreeMap<String, ThreadLoggerInfo> _engineNameToThreadLoggerInfo;

	final private ShortAlphaStringMap _shortAlphaStringMap =
			new ShortAlphaStringMap();

	static class ParticlesFilePlus {
		final String _key;
		final long _fileTimeSecs;
		final ParticlesFile _particlesFile;
		long _lastAccessSecs;

		private ParticlesFilePlus(final String key,
				final ParticlesFile particlesFile, final long fileTimeSecs) {
			_key = key;
			_fileTimeSecs = fileTimeSecs;
			_particlesFile = particlesFile;
			_lastAccessSecs = System.currentTimeMillis() / 1000L;
		}
	}

	public class SimCase extends WorkStopper.Standard implements Runnable {
		public String _engineName = null;
		final public String _rawClassName;
		final public boolean _forCompareKeyFiles;
		final public String[] _runnerArgs;
		public ProgressState _progressState;
		private MyLogger _logger;
		private SimGlobalStrings _simGlobalStrings;
		private Properties _simProperties;
		private String _xmlSimPropertiesFilePath;
		/**
		 * We store _nProgressSteps from _chunkReporter for when _chunkReporter
		 * is gone. _nProgressSteps gets reset when _chunkReporter is set.
		 */
		private int _nProgressSteps;
		private ChunkReporter _chunkReporter;
		/** The following is when the case was created and finished. */
		final private long _creationMs;
		private long _finishMs;
		/** We use the following to time certain steps in the tracker. */
		public long _scratchTimeInMillis;
		/** The following is the tracker or the planner or ... */
		private MainSaropsObject _mainSaropsObject;
		/** For interruptions. */
		public boolean _interrupted = false;

		private SimCase(final String rawClassName, final String[] runnerArgs) {
			super(getReadableSimCaseName(rawClassName, runnerArgs));
			_progressState = ProgressState.WAITING;
			_rawClassName = rawClassName;
			_forCompareKeyFiles = false;
			_runnerArgs = runnerArgs;
			_logger = null;
			_simGlobalStrings = null;
			_simProperties = null;
			_chunkReporter = null;
			_mainSaropsObject = null;
			_creationMs = System.currentTimeMillis();
			_finishMs = Long.MAX_VALUE / 2;
			_nProgressSteps =
					SimCaseManager.getSimGlobalStrings(this).getNProgressSteps();
			_xmlSimPropertiesFilePath = null;
			allowGoing();
		}

		public String getXmlSimPropertiesFilePath() {
			return _xmlSimPropertiesFilePath;
		}

		private SimCase(final MyLogger logger, final String engnName) {
			super(engnName);
			_logger = logger;
			_progressState = ProgressState.WAITING;
			_rawClassName = null;
			_forCompareKeyFiles = true;
			_runnerArgs = null;
			_simGlobalStrings = null;
			_simProperties = null;
			resetSimGlobalStringsAndProperties();
			_chunkReporter = null;
			_mainSaropsObject = null;
			_creationMs = System.currentTimeMillis();
			_finishMs = Long.MAX_VALUE / 2;
			_nProgressSteps =
					SimCaseManager.getSimGlobalStrings(this).getNProgressSteps();
			_xmlSimPropertiesFilePath = null;
			allowGoing();
		}

		public void setXmlSimPropertiesFilePath(
				final String xmlSimPropertiesFilePath) {
			_xmlSimPropertiesFilePath = xmlSimPropertiesFilePath;
		}

		private void resetSimGlobalStringsAndProperties() {
			_simGlobalStrings = new SimGlobalStrings();
			try {
				_simProperties =
						ModelReader.getSimPropertiesBeforeXmlOverrides(SimCase.this);
			} catch (final Exception e) {
				_logger.err(String.format("%s may Not proceed(2).", getName()));
				finishRun(ProgressState.DROPPED);
				return;
			}
		}

		private String[] getSimProperty(final String mustContain) {
			if (_simProperties == null) {
				return null;
			}
			final String lcMustContain = mustContain.toLowerCase();
			for (final Map.Entry<Object, Object> entry : _simProperties
					.entrySet()) {
				final String key = (String) entry.getKey();
				if (key.toLowerCase().contains(lcMustContain)) {
					final String value = (String) entry.getValue();
					final String[] fields = value.split("\\s+");
					if (fields.length > 0) {
						final ArrayList<String> strings = new ArrayList<>();
						for (final String field : fields) {
							if (field != null && field.length() > 0) {
								strings.add(field);
							}
						}
						if (strings.size() > 0) {
							return strings.toArray(new String[strings.size()]);
						}
					}
				}
			}
			return null;
		}

		public boolean getSimPropertyBoolean(final String mustContain,
				final boolean defaultValue) {
			final String[] fields = getSimProperty(mustContain);
			if (fields == null) {
				return defaultValue;
			}
			final Boolean isOn = StringUtilities.getBoolean(fields[0]);
			return isOn == null ? defaultValue : isOn;
		}

		public double getSimPropertyDouble(final String mustContain,
				final double defaultValue) {
			final String[] fields = getSimProperty(mustContain);
			if (fields == null) {
				return defaultValue;
			}
			final String numericField = fields[0];
			try {
				return Double.parseDouble(numericField);
			} catch (final NumberFormatException e) {
				return defaultValue;
			} catch (final Exception e) {
				return defaultValue;
			}
		}

		public SimGlobalStrings getSimGlobalStrings() {
			return _simGlobalStrings;
		}

		public Properties getSimProperties() {
			return _simProperties;
		}

		public MainSaropsObject getMainSaropsObject() {
			return _mainSaropsObject;
		}

		public void setMainSaropsObject(
				final MainSaropsObject mainSaropsObject) {
			_mainSaropsObject = mainSaropsObject;
		}

		public Model getSimModel() {
			if (_mainSaropsObject == null) {
				return null;
			}
			if (_mainSaropsObject instanceof Tracker) {
				return ((Tracker) _mainSaropsObject).getModel();
			}
			if (_mainSaropsObject instanceof Planner) {
				return ((Planner) _mainSaropsObject).getSimModel();
			}
			return null;
		}

		public long getCreationMs() {
			return _creationMs;
		}

		public long getFinishMs() {
			return _finishMs;
		}

		@Override
		public void run() {
			_progressState = ProgressState.ACTIVE;
			_engineName = Thread.currentThread().getName();
			final ThreadLoggerInfo threadLoggerInfo =
					_engineNameToThreadLoggerInfo.get(_engineName);
			_logger = threadLoggerInfo._logger;
			_logger.out(String.format("Activating case %s", getName()));
			final String modelFilePath =
					StringUtilities.cleanUpFilePath(_runnerArgs[0]);
			final File modelFile = new File(modelFilePath);
			final File outAndErrDir = modelFile.getParentFile();
			final String versionName = SimGlobalStrings.getStaticVersionName();
			final String currentClassName =
					convertToCurrentPckgName(_rawClassName);
			final String caseName = getName();
			if (currentClassName.equals(_TrackerClassName)) {
				_logger.out(
						String.format("Running Tracker, VersionName[%s], Case[%s].",
								versionName, caseName));
				_logger.resetAppenders(outAndErrDir,
						_DEBUG_SIM_CASES ? "sim.dbg" : null, "sim.out", "sim.wrn",
						"sim.err", MyLogger._DefaultSuffix, /* append= */false);
				_logger.out(
						String.format("Running Tracker, VersionName[%s], Case[%s].",
								versionName, caseName));
			} else {
				_logger.out(
						String.format("Running Planner, VersionName[%s], Case[%s].",
								versionName, caseName));
				_logger.resetAppenders(outAndErrDir,
						_DEBUG_SIM_CASES ? "plan.dbg" : null, "plan.out", "plan.wrn",
						"plan.err", MyLogger._DefaultSuffix, /* append= */false);
				_logger.out(
						String.format("Running Planner, VersionName[%s], Case[%s].",
								versionName, caseName));
			}
			if (!_mayProceed) {
				_logger.err(String.format("%s may Not proceed(1).", getName()));
				finishRun(ProgressState.DROPPED);
				return;
			}
			/** Reset _simGlobalStrings and _simProperties for this run. */
			resetSimGlobalStringsAndProperties();
			/** Check the input file. */
			_logger.out(String.format("Reading input for %s.", getName()));
			final boolean canReadInputFile = canReadInputFile();
			if (!canReadInputFile) {
				_logger
						.err(String.format("Failed to read input for %s.", getName()));
				finishRun(ProgressState.DROPPED);
				return;
			}
			ProgressState completionProgressState =
					_interrupted ? ProgressState.DROPPED : ProgressState.DONE;
			try {
				_logger.out(String.format("Finished reading input."));
				_logger.out("Input Args:");
				for (final String arg : _runnerArgs) {
					_logger.out(arg);
				}
				SimCaseManager.LogMemory(this);
				if (currentClassName.equals(_TrackerClassName)) {
					Tracker.runSimulator(this, _runnerArgs);
				} else if (currentClassName.equals(_PlannerClassName)) {
					Planner.runPlanner(this, _runnerArgs);
				}
				_logger.out(String.format("%s/%s completed successfully.",
						currentClassName, getName()));
			} catch (final Exception e) {
				completionProgressState = ProgressState.DROPPED;
				final String name = getName();
				final String eString = e.toString();
				_logger.err(String.format("Dropped %s because of \"%s.\"\n", name,
						eString));
				final String stackTraceString =
						StringUtilities.getStackTraceString(e);
				_logger.err(String.format("Stack trace:\n%s", stackTraceString));
			}
			finishRun(completionProgressState);
		}

		private boolean canReadInputFile() {
			final String currentClassName =
					convertToCurrentPckgName(_rawClassName);
			if (currentClassName.equals(_TrackerClassName) &&
					_runnerArgs == null && _runnerArgs.length == 0) {
				/** Display only; we don't really care. */
				return true;
			}
			/** Better be a readable, parsable xml. */
			final String filePath = _runnerArgs[0];
			if (filePath == null) {
				return false;
			}
			final File f = new File(filePath);
			if (!f.exists() || !f.canRead()) {
				return false;
			}
			Element root = null;
			try (final FileInputStream fis =
					new FileInputStream(new File(filePath))) {
				final Document document = LsFormatter._DocumentBuilder.parse(fis);
				root = document.getDocumentElement();
			} catch (final IOException | SAXException e2) {
				return false;
			}
			/** More scrutiny for Planner; we're ok with everyone else. */
			if (!currentClassName.equals(SimCaseManager._PlannerClassName)) {
				return true;
			}
			final String tagName = root.getTagName();
			if (!"PLAN".equals(tagName)) {
				return false;
			}
			if (_ForgiveMissingParticlesFile) {
				return true;
			}
			/**
			 * Parse it enough to find the particlesFile and verify that we can
			 * read it.
			 */
			final String particlesFilePath =
					AbstractOutFilesManager.GetParticlesFilePathFromXml(filePath);
			final File particlesFile = new File(particlesFilePath);
			if (!particlesFile.exists() || !particlesFile.canRead() ||
					!particlesFilePath.toLowerCase().endsWith(".nc")) {
				return false;
			}
			return true;
		}

		public SimCaseManager getSimCaseManager() {
			return SimCaseManager.this;
		}

		public String getCleanedUpFilePath() {
			if (_runnerArgs == null || _runnerArgs.length < 1 ||
					_runnerArgs[0] == null) {
				return _DisplayOnlyString;
			}
			return _runnerArgs[0];
		}

		public String getNameForDisplay() {
			if (_runnerArgs == null || _runnerArgs.length < 1 ||
					_runnerArgs[0] == null) {
				return _DisplayOnlyString;
			}
			return getName();
		}

		public void setChunkReporter(final ChunkReporter chunkReporter) {
			_chunkReporter = chunkReporter;
			_nProgressSteps = _chunkReporter.getNProgressSteps();
		}

		public ChunkReporter getChunkReporter() {
			return _chunkReporter;
		}

		private void finishRun(final ProgressState completionProgressState) {
			final ThreadLoggerInfo threadLoggerInfo =
					_engineNameToThreadLoggerInfo.get(_engineName);
			_logger.resetAppenders(threadLoggerInfo._logDir,
					threadLoggerInfo._dbgCoreName, threadLoggerInfo._outCoreName,
					threadLoggerInfo._wrnCoreName, threadLoggerInfo._errCoreName,
					MyLogger._DefaultSuffix, /* append= */true);
			String filePathString =
					(_runnerArgs == null || _runnerArgs.length < 1) ? "NoFilePath" :
							_runnerArgs[0];
			filePathString = StringUtilities.cleanUpFilePath(filePathString);
			final String m = String.format("finishRun(%s)", getName());
			final boolean statusOnly = false;
			_queueOfSimCases.recordEndOfCase(completionProgressState);
			_progressState = completionProgressState;
			cleanUpQueue(statusOnly, filePathString, m);
			runOutChunks();
			_logger = null;
			_chunkReporter = null;
			_simGlobalStrings = null;
			_simProperties = null;
			_mainSaropsObject = null;
			_finishMs = System.currentTimeMillis();
			_engineName = null;
			SizeOf.runGC(_logger);
		}

		public ProgressState getProgressState() {
			return _progressState;
		}

		public boolean runStudy() {
			if (_mainSaropsObject == null) {
				return false;
			}
			if (_mainSaropsObject instanceof Tracker) {
				final Tracker tracker = (Tracker) _mainSaropsObject;
				return tracker.runStudy();
			}
			if (_mainSaropsObject instanceof Planner) {
				final Planner planner = (Planner) _mainSaropsObject;
				return planner.runStudy();
			}
			return false;
		}

		@Override
		public boolean getKeepGoing() {
			if (amShuttingDown()) {
				return false;
			}
			return _keepGoing;
		}

		public void setChunkReporter(final File progressDirectory,
				final int nProgressSteps, final String[] sectionNames,
				final boolean[] criticalSections, final String[][] sections) {
			_chunkReporter = new ChunkReporter(progressDirectory, nProgressSteps,
					sectionNames, criticalSections, sections);
		}

		public void runOutChunks() {
			runOutChunks(/* inFatalHandler= */false);
		}

		public void runOutChunks(final boolean inFatalHandler) {
			if (_chunkReporter != null) {
				_chunkReporter.runOutChunks(this, inFatalHandler);
			}
		}

		public void reportChunksDone(final int nChunksDone) {
			if (_chunkReporter != null) {
				_chunkReporter.reportChunksDone(this, nChunksDone);
			}
		}

		public void reportChunkDone() {
			if (_chunkReporter != null) {
				_chunkReporter.reportChunkDone(this);
			}
		}

		public void dumpCriticalTimes(final String caption,
				final long entryTimeMs, final File timesFile) {
			if (_chunkReporter != null) {
				_chunkReporter.dumpCriticalTimes(this, caption, entryTimeMs,
						timesFile);
			}
		}

		public int[] getNProgressStepsInfo() {
			final int nProgressSteps;
			final int nProgressStepsDone;
			if (_chunkReporter != null) {
				nProgressSteps = _chunkReporter.getNProgressSteps();
				nProgressStepsDone = _chunkReporter.getNProgressStepsDone();
			} else {
				final SimGlobalStrings simGlobalStrings =
						SimCaseManager.getSimGlobalStrings(this);
				final int defaultNProgressSteps =
						simGlobalStrings.getNProgressSteps();
				switch (_progressState) {
				case WAITING:
					nProgressSteps = defaultNProgressSteps;
					nProgressStepsDone = 0;
					break;
				case ACTIVE:
					nProgressSteps = defaultNProgressSteps;
					nProgressStepsDone = 0;
					break;
				case DONE:
				case DROPPED:
				default:
					nProgressSteps = _nProgressSteps;
					nProgressStepsDone = _nProgressSteps + 1;
					break;
				}
			}
			return new int[] { nProgressSteps, nProgressStepsDone };
		}

		public boolean isOld() {
			if (_progressState != ProgressState.DONE &&
					_progressState != ProgressState.DROPPED) {
				return false;
			}
			return _finishMs < System.currentTimeMillis() - _OldMs;
		}

		public boolean isVeryOld() {
			if (_progressState != ProgressState.DONE &&
					_progressState != ProgressState.DROPPED) {
				return false;
			}
			return _finishMs < System.currentTimeMillis() - _VeryOldMs;
		}

		public MyLogger getLogger() {
			return _logger;
		}
	}

	private static Comparator<SimCase> _SimCaseComparator =
			new Comparator<>() {
				@Override
				public int compare(final SimCase o1, final SimCase o2) {
					final String filePath1 =
							(o1._runnerArgs == null || o1._runnerArgs.length == 0) ?
									null : o1._runnerArgs[0];
					final String filePath2 =
							(o2._runnerArgs == null || o2._runnerArgs.length == 0) ?
									null : o2._runnerArgs[0];
					if ((filePath1 == null) != (filePath2 == null)) {
						return filePath1 == null ? 1 : -1;
					}
					int compareValue = filePath1.compareToIgnoreCase(filePath2);
					if (compareValue != 0) {
						return compareValue;
					}
					if ((o1._rawClassName == null) != (o2._rawClassName == null)) {
						return o1._rawClassName == null ? 1 : -1;
					}
					if (o1._rawClassName == null) {
						return 0;
					}
					compareValue = o1._rawClassName.compareTo(o2._rawClassName);
					return compareValue;
				}
			};

	final private ThreadPoolExecutor _enginesThreadPool;
	private int _nextEngineId;
	final private MyThreadPool _workersThreadPool;
	final private QueueOfSimCases _queueOfSimCases;
	private volatile boolean _mayProceed;
	/** Miscellaneous duties. */
	final private ArrayList<ParticlesFilePlus> _particlesFilePluses;
	public Object _umbrellaObject;
	final public MyLogger _globalLogger;

	public SimCaseManager() {
		/** Load all the classes. */
		assert _OnlySimCaseManager == null : "Should not build more than one SimCaseManager.";
		_OnlySimCaseManager = this;
		/** Build the global logger. */
		final File glblLogDir = DirsTracker.getLogDir();
		if (StringUtilities.getSystemProperty("Clean.Global.Log.Files",
				/* useSpaceProxy= */false) != null) {
			final File[] files = glblLogDir.listFiles(new FileFilter() {
				@Override
				public boolean accept(final File f) {
					if (f.isDirectory()) {
						return false;
					}
					final String fNameLc = f.getName().toLowerCase();
					if (!fNameLc.endsWith("." + MyLogger._DefaultSuffix)) {
						return false;
					}
					if (fNameLc.startsWith("glbldbg") ||
							fNameLc.startsWith("glblout") ||
							fNameLc.startsWith("glblerr") ||
							fNameLc.startsWith("nulldbg") ||
							fNameLc.startsWith("nullout") ||
							fNameLc.startsWith("nullwrn") ||
							fNameLc.startsWith("nullerr")) {
						return true;
					}
					if (fNameLc.startsWith("engn")) {
						if (fNameLc.contains("dbg." + MyLogger._DefaultSuffix) || fNameLc.contains("out." + MyLogger._DefaultSuffix) || fNameLc.contains("wrn." + MyLogger._DefaultSuffix) || fNameLc.contains("err." + MyLogger._DefaultSuffix)) {
							return true;
						}
					}
					return false;
				}
			});
			final int nFiles = files == null ? 0 : files.length;
			for (int k = 0; k < nFiles; ++k) {
				final File f = files[k];
				f.delete();
			}
		}

		SimpleDateFormat fileSdf = new SimpleDateFormat("yyyyMMMddzzz");
		String tmStringA = fileSdf.format(new Date());
		String outCoreName = "GlblOut" + tmStringA;
		File f = new File(glblLogDir, outCoreName + ".txt");
		if (f.exists()) {
			fileSdf = new SimpleDateFormat("yyyyMMMdd+HH00zzz");
			tmStringA = fileSdf.format(new Date());
			outCoreName = "GlblOut" + tmStringA;
			f = new File(glblLogDir, outCoreName + ".txt");
			if (f.exists()) {
				fileSdf = new SimpleDateFormat("yyyyMMMdd+HHmmzzz");
				tmStringA = fileSdf.format(new Date());
				outCoreName = "GlblOut" + tmStringA;
				f = new File(glblLogDir, outCoreName + ".txt");
				if (f.exists()) {
					fileSdf = new SimpleDateFormat("yyyyMMMdd+HHmmsszzz");
					tmStringA = fileSdf.format(new Date());
					outCoreName = "GlblOut" + tmStringA;
					f = new File(glblLogDir, outCoreName + ".txt");
					if (f.exists()) {
						for (int k = 0;; ++k) {
							final String tmStringAA = tmStringA + "&" + k;
							outCoreName = "GlblOut" + tmStringAA;
							f = new File(glblLogDir, outCoreName + ".txt");
							if (!f.exists()) {
								tmStringA = tmStringAA;
								break;
							}
						}
					}
				}
			}
		}
		final String tmString = tmStringA;
		_globalLogger = MyLogger.CreateMyLogger(//
				/* relativeLoggerName= */"Global", glblLogDir,
				/*
				 * debugCoreName=
				 */_DEBUG_SIM_CASES ? ("GlblDbg" + tmString) : null, //
				/* outCoreName= */"GlblOut" + tmString, //
				/* errCoreName= */"GlblWrn" + tmString, //
				/* errCoreName= */"GlblErr" + tmString, MyLogger._DefaultSuffix,
				/* append= */true);

		/** Load MathXLib. */
		MathX.acosX(0d);

		/** Load Gshhs and Etopo. */
		GshhsReader.loadLandCaches();
		Etopo.loadEtopo();
		_globalLogger.out("Loaded Land and Etopo.");

		final File dataDir = SaropsDirsTracker.getDataDir();
		final RuntimeMXBean runtimeMxBean =
				ManagementFactory.getRuntimeMXBean();
		final List<String> runtimeArguments = runtimeMxBean.getInputArguments();
		final int nRuntimeArguments = runtimeArguments.size();
		String runtimeArgumentsString = "\nRuntime Arguments:";
		for (int k = 0; k < nRuntimeArguments; ++k) {
			runtimeArgumentsString +=
					String.format("\n%s", runtimeArguments.get(k));
		}
		runtimeArgumentsString +=
				String.format("\nEnd of Runtime Arguments.\n");
		_globalLogger.out(runtimeArgumentsString);
		_globalLogger.out(
				String.format("\n%s", SimGlobalStrings.getFullStaticVersionName()));

		synchronized (_LockOnSimCaseManagerProperties) {
			if (_SimCaseManagerProperties == null) {
				final Properties simCaseManagerProperties = new Properties();
				final Class<? extends SimCaseManager> clazz = getClass();
				try (final InputStream is =
						clazz.getResourceAsStream("SimCaseManager.properties")) {
					simCaseManagerProperties.load(is);
				} catch (final IOException e) {
				}
				/**
				 * Reset (or set) maxNProcessorsToUse and then save the copyOf.
				 */
				int maxNProcessorsToUse = 0;
				final String s1a = simCaseManagerProperties
						.getProperty("SimCaseManager.MaxNProcessorsToUse");
				try {
					maxNProcessorsToUse = Integer.parseInt(s1a);
				} catch (final NumberFormatException e1) {
					maxNProcessorsToUse =
							Runtime.getRuntime().availableProcessors() - 1;
					simCaseManagerProperties.setProperty(
							"SimCaseManager.MaxNProcessorsToUse",
							"" + maxNProcessorsToUse);
				}
				/** Back it up, since we have reset maxNProcessorsToUse. */
				final File backupFile =
						new File(dataDir, "CopyOfJarFileSimCaseManager.properties");
				try (final FileOutputStream backupFos =
						new FileOutputStream(backupFile)) {
					simCaseManagerProperties.store(backupFos,
							"Copy of JarFile SimCaseManager.Properties.");
					backupFos.close();
				} catch (final IOException e1) {
				}
				final File overrideFile =
						new File(dataDir, "SimCaseManager.properties");
				if (overrideFile.exists() && !overrideFile.isDirectory()) {
					final String overrideFilePath =
							StringUtilities.getCanonicalPath(overrideFile);
					_globalLogger.out(String.format(
							"Overriding SimCaseManager.properties.  " + "filePath:%s.\n",
							overrideFilePath));
					try (final FileInputStream fis =
							new FileInputStream(overrideFile)) {
						simCaseManagerProperties.load(fis);
					} catch (final IOException e) {
					}
				}
				_SimCaseManagerProperties = simCaseManagerProperties;
				final String s1 = _SimCaseManagerProperties
						.getProperty("SimCaseManager.MaxNProcessorsToUse");
				_MaxNProcessorsToUse = Integer.parseInt(s1);
				_SimCaseManagerProperties.setProperty(
						"SimCaseManager.MaxNProcessorsToUse",
						"" + _MaxNProcessorsToUse);
				final String s2 = _SimCaseManagerProperties
						.getProperty("SimCaseManager.MaxNParticlesFiles");
				_MaxNParticlesFiles = Integer.parseInt(s2);
				_SimCaseManagerProperties.setProperty(
						"SimCaseManager.MaxNParticlesFiles", "" + _MaxNParticlesFiles);
				final String s3 = _SimCaseManagerProperties
						.getProperty("SimCaseManager.MaxNEngines");
				final int maxNEngines = Integer.parseInt(s3);
				/** Print it out when it is set for good (below). */
				final String s4 = _SimCaseManagerProperties
						.getProperty("SimCaseManager.MinNThreadsPerEngine");
				/** Must have at least 1 thread per engine. */
				final int minNThreadsPerEngine = Math.max(1, Integer.parseInt(s4));
				_SimCaseManagerProperties.setProperty(
						"SimCaseManager.MinNThreadsPerEngine",
						"" + minNThreadsPerEngine);
				final String s5 = _SimCaseManagerProperties
						.getProperty("SimCaseManager.MaxNWorkerThreadsInOneCall");
				_MaxNWorkerThreadsInOneCall = Integer.parseInt(s5);
				_SimCaseManagerProperties.setProperty(
						"SimCaseManager.MaxNWorkerThreadsInOneCall",
						"" + _MaxNWorkerThreadsInOneCall);
				_NEngines = Math.max(1, Math.min(maxNEngines,
						_MaxNProcessorsToUse / minNThreadsPerEngine));
				_SimCaseManagerProperties.setProperty("SimCaseManager.MaxNEngines",
						"" + _NEngines);
				/** Dump the the working copy of the parameters. */
				final File workingFile =
						new File(dataDir, "WorkingSimCaseManager.properties");
				try (final FileOutputStream fos =
						new FileOutputStream(workingFile)) {
					simCaseManagerProperties.store(fos,
							"Copy of JarFile SimCaseManager.Properties.");
				} catch (final IOException e) {
				}
			}
		}
		/** Set _mayProceed so that something happens. */
		_mayProceed = true;
		_enginesThreadPool = new ThreadPoolExecutor(_NEngines, _NEngines, 0L,
				TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>()) {
			@Override
			protected <T> RunnableFuture<T> newTaskFor(final Runnable runnable,
					final T value) {
				return new FutureTask<>(runnable, value);
			}
		};
		/**
		 * We will use, for the name of the logger associated with this Engine
		 * thread, the name of the engine.
		 */
		_engineNameToThreadLoggerInfo = new TreeMap<>();
		_enginesThreadPool.setThreadFactory(new ThreadFactory() {
			@Override
			public Thread newThread(final Runnable r) {
				for (;;) {
					final String engnName =
							String.format("Engn%02d", _nextEngineId++);
					if (_engineNameToThreadLoggerInfo.containsKey(engnName)) {
						_globalLogger.err(
								String.format("\tFailed to produce Engine[%s].", engnName));
						continue;
					}
					/** Create this Engine's Logger. */
					final String engnDbgCoreName;
					if (_DEBUG_SIM_CASES) {
						engnDbgCoreName = String.format("%sDbg+%s", engnName, tmString);
					} else {
						engnDbgCoreName = null;
					}
					final String engnOutCoreName =
							String.format("%sOut+%s", engnName, tmString);
					final String engnWrnCoreName =
							String.format("%sWrn+%s", engnName, tmString);
					final String engnErrCoreName =
							String.format("%sErr+%s", engnName, tmString);
					final MyLogger engineLogger = MyLogger.CreateMyLogger(//
							/* relativeLoggerName= */engnName, glblLogDir, //
							engnDbgCoreName, //
							engnOutCoreName, //
							engnWrnCoreName, //
							engnErrCoreName, //
							MyLogger._DefaultSuffix, /* append= */true);
					_engineNameToThreadLoggerInfo.put(engnName,
							new ThreadLoggerInfo(glblLogDir, engnDbgCoreName,
									engnOutCoreName, engnWrnCoreName, engnErrCoreName,
									engineLogger));
					final Thread thread = new Thread(r, engnName);
					MyThreadPool.adjustPriority(thread, _EnginePriorityAdjustment);
					return thread;
				}
			}
		});

		/** Use the rest for the worker pool. */
		final int nForWorkerPool =
				Math.max(2, _MaxNProcessorsToUse - _NEngines);
		_workersThreadPool =
				new MyThreadPool(nForWorkerPool, _MaxNWorkerThreadsInOneCall,
						_WorkerPriorityAdjustment, "SimWorkers");
		_queueOfSimCases =
				new QueueOfSimCases(this);/**
																	 * For miscellaneous duties.
																	 */
		_particlesFilePluses = new ArrayList<>();
		_umbrellaObject = null;
	}

	public void shutDown(final String shutDownSrc) {
		_mayProceed = false;
		/**
		 * Shut down the active ones. Cannot synchronize here since termination
		 * of a run affects _activeCases. Also, we really want to terminate the
		 * currently active ones. Setting _mayProceed to false will allow the
		 * queued up ones to execute practically instantaneously.
		 */
		synchronized (_queueOfSimCases) {
			for (final SimCase simCase : _queueOfSimCases
					.getSimCasesOldestFirst()) {
				if (simCase._progressState == ProgressState.ACTIVE) {
					simCase.stopIfGoing(false);
					int nTriesToStop = 0;
					for (;
							nTriesToStop < 10 &&
									simCase._progressState == ProgressState.ACTIVE;
							++nTriesToStop) {
						try {
							Thread.sleep(3000);
						} catch (final InterruptedException e) {
						}
					}
					if (simCase._progressState == ProgressState.ACTIVE) {
						final String caseName = simCase.getName();
						final String s = String.format(
								"Hanging Problem; Could not shut down %s.", caseName);
						_globalLogger.err(s);
					} else {
						_globalLogger.out(String.format("Took %d tries to stop %s",
								nTriesToStop, simCase.getName()));
					}
				}
			}
		}
		final boolean statusOnly = false;
		final String filePath = null;
		cleanUpQueue(statusOnly, filePath, shutDownSrc);
		synchronized (_enginesThreadPool) {
			if (!_enginesThreadPool.isShutdown()) {
				_enginesThreadPool.shutdown();
			}
		}
		_globalLogger.out("Shut down engine threads.");
		_workersThreadPool.shutDown();
		_globalLogger.out("Shut down Worker threads.");
	}

	public boolean amShuttingDown() {
		return !_mayProceed;
	}

	public SimCase buildCompareKeyFilesSimCase(final MyLogger logger,
			final String engnName) {
		final SimCase simCase = new SimCase(logger, engnName);
		return simCase;
	}

	public void queueSimCase(final String rawClassName,
			final String[] runnerArgsX) {
		if (!_mayProceed) {
			return;
		}
		final String[] runnerArgs = runnerArgsX.clone();
		final String filePath = StringUtilities.cleanUpFilePath(runnerArgs[0]);
		runnerArgs[0] = filePath;
		final String thisSimCaseName =
				getReadableSimCaseName(rawClassName, filePath);
		_globalLogger.out(String.format("Queuing %s.", thisSimCaseName));
		synchronized (_queueOfSimCases) {
			synchronized (_enginesThreadPool) {
				final SimCase simCase = new SimCase(rawClassName, runnerArgs);
				for (final SimCase simCaseX : _queueOfSimCases
						.getSimCasesOldestFirst()) {
					if (_SimCaseComparator.compare(simCase, simCaseX) == 0) {
						if ((simCaseX._progressState == ProgressState.WAITING) ||
								(simCaseX._progressState == ProgressState.ACTIVE)) {
							/** Since this is already queued, we do nothing. */
							final String s = String.format(
									"Will not run %s; still in queue.", thisSimCaseName);
							_globalLogger.out(s);
							return;
						}
						/**
						 * This is a "killable one;" get rid of it to make room for this
						 * new one.
						 */
						_queueOfSimCases.remove(simCaseX);
						break;
					}
				}
				if (!_queueOfSimCases.add(simCase)) {
					final String s = String.format(
							"Will not run %s; Queue has problems.", thisSimCaseName);
					_globalLogger.err(s);
					return;
				}
				_enginesThreadPool.submit(simCase);
			}
		}
	}

	public boolean hasActive() {
		synchronized (_queueOfSimCases) {
			for (final SimCase simCase : _queueOfSimCases
					.getSimCasesOldestFirst()) {
				if (simCase._progressState == ProgressState.ACTIVE) {
					return true;
				}
			}
		}
		return false;
	}

	/** Returns ACTIVE, WAITING, DONE, DROPPED. */
	public SimCase[] getRecentSimCasesInQueue() {
		final SimCase[] simCases = _queueOfSimCases.getSimCasesOldestFirst();
		final int n = simCases.length;
		final ArrayList<SimCase> recentSimCaseList = new ArrayList<>(n);
		for (int iPass = 0; iPass < 4; ++iPass) {
			for (final SimCase simCase : simCases) {
				if (simCase.isOld()) {
					continue;
				}
				final ProgressState progressState = simCase._progressState;
				if (iPass == 0 && progressState == ProgressState.WAITING) {
					recentSimCaseList.add(simCase);
				}
				if (iPass == 1 && progressState == ProgressState.ACTIVE) {
					recentSimCaseList.add(simCase);
				}
				if (iPass == 2 && progressState == ProgressState.DONE) {
					recentSimCaseList.add(simCase);
				}
				if (iPass == 3 && progressState == ProgressState.DROPPED) {
					recentSimCaseList.add(simCase);
				}
			}
		}
		return recentSimCaseList.toArray(new SimCase[recentSimCaseList.size()]);
	}

	public void cleanUpQueue(final boolean statusOnly, final String filePath,
			final String m) {
		_queueOfSimCases.cleanUpQueue(statusOnly, filePath, m);
	}

	public static void LogMemory(final SimCase simCase) {
		final double scale = 1024d * 1024d;
		final Runtime runtime = Runtime.getRuntime();
		final double maxMemInKBts = runtime.maxMemory() / scale;
		final double totalMemInKBts = runtime.totalMemory() / scale;
		final double freeMemInKBts = runtime.freeMemory() / scale;
		final String logMessage =
				String.format("Memory: Total[%.3fM], Max[%.3fM], and Free[%.3fM]",
						totalMemInKBts, maxMemInKBts, freeMemInKBts);
		out(simCase, logMessage);
	}

	public static void debug(final SimCase simCase, final String message) {
		if (simCase != null && simCase._logger != null) {
			simCase._logger.out(message);
		} else {
			_OnlySimCaseManager._globalLogger.dbg(message);
		}
	}

	public static void dbg(final SimCase simCase, final String message) {
		if (simCase != null && simCase._logger != null) {
			simCase._logger.dbg(message);
		} else if (_OnlySimCaseManager != null &&
				_OnlySimCaseManager._globalLogger != null) {
			_OnlySimCaseManager._globalLogger.dbg(message);
		} else {
			MyLogger.dbg(null, message);
		}
	}

	public static void out(final SimCase simCase, final String message) {
		if (simCase != null && simCase._logger != null) {
			simCase._logger.out(message);
		} else if (_OnlySimCaseManager != null &&
				_OnlySimCaseManager._globalLogger != null) {
			_OnlySimCaseManager._globalLogger.out(message);
		} else {
			MyLogger.out(null, message);
		}
	}

	public static void wrn(final SimCase simCase, final String message) {
		if (simCase != null && simCase._logger != null) {
			simCase._logger.wrn(message);
		} else if (_OnlySimCaseManager != null &&
				_OnlySimCaseManager._globalLogger != null) {
			_OnlySimCaseManager._globalLogger.wrn(message);
		} else {
			MyLogger.wrn(null, message);
		}
	}

	public static void err(final SimCase simCase, final String message) {
		if (simCase != null && simCase._logger != null) {
			simCase._logger.err(message);
		} else if (_OnlySimCaseManager != null &&
				_OnlySimCaseManager._globalLogger != null) {
			_OnlySimCaseManager._globalLogger.err(message);
		} else {
			MyLogger.err(null, message);
		}
	}

	public static void standardLogError(final SimCase simCase,
			final Throwable e) {
		standardLogError(simCase, e, null);
	}

	public static void standardLogError(final SimCase simCase,
			final Throwable e, String message) {
		if (message == null) {
			message = e.getMessage();
		}
		err(simCase, String.format("\n\n@@@\n%s at: %s", message,
				StringUtilities.getStackTraceString(e)));
	}

	public String getReadableSimCaseName(String rawClassName,
			final String filePath) {
		try {
			final int lastDot1 = rawClassName.lastIndexOf('.');
			rawClassName = lastDot1 < 0 ? rawClassName :
					rawClassName.substring(lastDot1 + 1);
			final String shortString =
					_shortAlphaStringMap.getShortString(filePath);
			final String fileName = new File(filePath).getName();
			final String shortFileName =
					fileName.substring(0, Math.min(8, fileName.length()));
			final String s = String.format("%s<%s>:%s", rawClassName, shortString,
					shortFileName);
			return s;
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public String getReadableSimCaseName(final String rawClassName,
			final String[] runnerArgs) {
		final String filePath;
		if (runnerArgs == null || runnerArgs.length == 0) {
			filePath = "NoFilePath.xml";
		} else {
			filePath = runnerArgs[0];
		}
		return getReadableSimCaseName(rawClassName, filePath);
	}

	/** Use the following 3 routines to submit tasks to the workers. */
	public Object getLockOnWorkersThreadPool() {
		return _workersThreadPool;
	}

	public int getNFreeWorkerThreads(final SimCase simCase,
			final String whereFrom) {
		final MyLogger logger = getLogger(simCase);
		return _workersThreadPool.getNFreeWorkerThreads(logger, whereFrom);
	}

	public Future<?> submitToWorkers(final SimCaseManager.SimCase simCase,
			final Runnable runnable) {
		final MyLogger logger = SimCaseManager.getLogger(simCase);
		return _workersThreadPool.submitToWorkers(logger, runnable);
	}

	/** Manage ParticlesFiles. */
	final private static String getKey(final String particlesFilePath) {
		final String returnValue =
				StringUtilities.cleanUpFilePath(particlesFilePath);
		return returnValue;
	}

	public ParticlesFile getParticlesFile(
			final SimCaseManager.SimCase simCase,
			final String particlesFilePath) {
		if (particlesFilePath == null) {
			return null;
		}
		final String key = getKey(particlesFilePath);
		synchronized (_particlesFilePluses) {
			for (int k = 0; k < _particlesFilePluses.size(); ++k) {
				final ParticlesFilePlus particlesFilePlus =
						_particlesFilePluses.get(k);
				if (particlesFilePlus._key.equals(key)) {
					/** Check to see if there's a newer one on disc. */
					final File particlesFileOnDisc = new File(key);
					if (particlesFileOnDisc.exists()) {
						final long fileTimeSecs =
								StaticUtilities.getTimeOfModificationMs(key) / 1000L;
						if (fileTimeSecs > particlesFilePlus._fileTimeSecs) {
							/**
							 * Must delete it. Would like to log, with the dates.
							 */
							_particlesFilePluses.remove(k);
							break;
						}
					}
					/** Got one, no need to delete. */
					particlesFilePlus._lastAccessSecs =
							System.currentTimeMillis() / 1000L;
					out(simCase, "Retrieving  " + key + "(index[" + k + "]).");
					return particlesFilePlus._particlesFile;
				}
			}
		}
		/** No luck. Read it in. */
		final long modelFileModSecs =
				StaticUtilities.getTimeOfModificationMs(key) / 1000L;
		final long modelFileModInRefSecs =
				TimeUtilities.convertToRefSecs(modelFileModSecs);
		final String modelFileModString =
				TimeUtilities.formatTime(modelFileModInRefSecs, true);
		out(simCase, String.format("Must read in %s, dated %s", key,
				modelFileModString));
		final ParticlesFile particlesFile = new ParticlesFile(simCase, key);
		if (particlesFile.getStatus()) {
			addParticlesFile(simCase, key, particlesFile, modelFileModSecs);
			return particlesFile;
		}
		out(simCase, "COULD NOT GET PARTICLES FIIE.");
		return null;
	}

	public static boolean retainParticlesFileMemory() {
		return _MaxNParticlesFiles > 0;
	}

	public void addParticlesFile(final SimCase simCase,
			final String particlesFilePath, final ParticlesFile particlesFile,
			final long fileSecs) {
		if (_particlesFilePluses != null) {
			final String key = getKey(particlesFilePath);
			final int currentSizeOfCache = _particlesFilePluses.size();
			synchronized (_particlesFilePluses) {
				/**
				 * Delete the one that is already there or, if we need to, the one
				 * that is least recently accessed.
				 */
				int indexToDelete = -1;
				for (int k = 0; k < currentSizeOfCache; ++k) {
					final ParticlesFilePlus particlesFilePlus =
							_particlesFilePluses.get(k);
					if (particlesFilePlus._key.equalsIgnoreCase(key)) {
						indexToDelete = k;
						break;
					}
				}
				if (indexToDelete == -1 &&
						currentSizeOfCache >= _MaxNParticlesFiles) {
					/** Must delete the least recently accessed. */
					long minAccessSecs = Long.MAX_VALUE;
					for (int k = 0; k < currentSizeOfCache; ++k) {
						final ParticlesFilePlus particlesFilePlus =
								_particlesFilePluses.get(k);
						final long thisAccessSecs = particlesFilePlus._lastAccessSecs;
						if (thisAccessSecs < minAccessSecs) {
							indexToDelete = k;
							minAccessSecs = thisAccessSecs;
						}
					}
				}
				if (indexToDelete != -1) {
					/** Must delete something. */
					final ParticlesFilePlus particlesFilePlus =
							_particlesFilePluses.get(indexToDelete);
					out(simCase, "Deleting " + particlesFilePlus._key);
					_particlesFilePluses.remove(indexToDelete);
				}
				/**
				 * Presumably, we have room. We do only if _MaxNParticlesFiles > 0.
				 */
				if (_MaxNParticlesFiles > 0) {
					out(simCase, "Adding " + key);
					_particlesFilePluses
							.add(new ParticlesFilePlus(key, particlesFile, fileSecs));
				}
			}
		}
	}

	public interface WorkerRunnableFactory {
		Runnable getRunnable(int k, int n);
	}

	public void flushParticlesFiles() {
		if (_particlesFilePluses != null) {
			synchronized (_particlesFilePluses) {
				_particlesFilePluses.clear();
			}
		}
		SizeOf.runGC(/* MyLogger= */null);
	}

	public static MyLogger getLogger(final SimCase simCase) {
		return simCase == null ? null : simCase._logger;
	}

	public static int getNEngines() {
		return _NEngines;
	}

	public static void dumpEnv(final SimCaseManager.SimCase simCase) {
		final Map<String, String> env = System.getenv();
		for (final Map.Entry<String, String> entry : env.entrySet()) {
			final String key = entry.getKey();
			final String value = entry.getValue();
			SimCaseManager.out(simCase,
					String.format("|+%s+| |+%s+|", key, value));
		}
		final Properties properties = System.getProperties();
		int k = 0;
		for (final Map.Entry<Object, Object> entry : properties.entrySet()) {
			final String key = (String) entry.getKey();
			final String value = (String) entry.getValue();
			SimCaseManager.out(simCase, String.format("%s|++%s++| |+%s+|",
					k == 0 ? "\n\n" : "", key, value));
			++k;
		}
	}

	public static SimGlobalStrings getSimGlobalStrings(
			final SimCase simCase) {
		if (simCase != null) {
			final SimGlobalStrings simGlobalStrings =
					simCase.getSimGlobalStrings();
			if (simGlobalStrings != null) {
				return simGlobalStrings;
			}
		}
		return SimGlobalStrings.getStaticSimGlobalStrings();
	}

	public static File getCaseDirFile(final SimCase simCase) {
		if (simCase == null) {
			return null;
		}
		final String filePath = simCase.getCleanedUpFilePath();
		final File caseFile = new File(filePath);
		final File caseDirFile = caseFile.getParentFile();
		return caseDirFile;
	}

	public SimCase getSimCase(final String filePath) {
		return _queueOfSimCases.getSimCase(filePath);
	}

	public int getNDone() {
		return _queueOfSimCases.getNDone();
	}

	public int getNDropped() {
		return _queueOfSimCases.getNDropped();
	}

	public int[] getNWaitingActiveDoneDropped() {
		return _queueOfSimCases.getNWaitingActiveDoneDropped();
	}

	final private static String _ReplacementString;
	static {
		final String myClassName = SimCaseManager.class.getName();
		final String[] fields = myClassName.split("\\.");
		_ReplacementString = fields[0] + '.' + fields[1] + '.';
	}

	public static String convertToCurrentPckgName(final String rawClassName) {
		if (rawClassName == null) {
			return null;
		}
		return rawClassName.replaceFirst("com\\..*?\\.", _ReplacementString);
	}
}
