package com.skagit.sarops.tracker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFName;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.Element;

import com.skagit.sarops.AbstractOutFilesManager;
import com.skagit.sarops.MainSaropsObject;
import com.skagit.sarops.computePFail.ComputePFail;
import com.skagit.sarops.computePFail.DetectValues;
import com.skagit.sarops.environment.BoxDefinition;
import com.skagit.sarops.environment.CurrentsUvGetter;
import com.skagit.sarops.environment.WindsUvGetter;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.Scenario;
import com.skagit.sarops.model.SearchObjectType;
import com.skagit.sarops.model.Sortie;
import com.skagit.sarops.model.SotWithWt;
import com.skagit.sarops.model.io.ModelReader;
import com.skagit.sarops.model.preDistressModel.PreDistressModel;
import com.skagit.sarops.simCaseManager.MainRunner;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.studyRunner.AbstractStudyRunner;
import com.skagit.sarops.util.SimGlobalStrings;
import com.skagit.util.LsFormatter;
import com.skagit.util.MathX;
import com.skagit.util.SizeOf;
import com.skagit.util.StaticUtilities;
import com.skagit.util.StringUtilities;
import com.skagit.util.TimeUtilities;
import com.skagit.util.cdf.BivariateNormalCdf;
import com.skagit.util.cdf.Cdf;
import com.skagit.util.cdf.area.Area;
import com.skagit.util.colorUtils.ColorUtils;
import com.skagit.util.etopo.Etopo;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.kmlObjects.kmlFeature.KmlDocument;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.TangentCylinder;
import com.skagit.util.poiUtils.ExcelDumper;
import com.skagit.util.randomx.Randomx;
import com.skagit.util.randomx.TimeDistribution;
import com.skagit.util.shorelineFinder.LevelFinderResult;
import com.skagit.util.shorelineFinder.ShorelineFinder;
import com.skagit.util.shpFileUtils.ShpFileWriter;

/**
 * A tracker generates the particles according to a given model, performs the
 * simulation, and updates the weights of each particle according to searches.
 */
public class Tracker implements MainSaropsObject {

	final private static boolean _WritePolylines = true;

	final private static double _NmiToR = MathX._NmiToR;
	final public static DetectValues.PFailType _TrackerPFailType = DetectValues.PFailType.AIFT;
	final private static boolean _TrackerForOptnOnly = false;
	final public static long _RefSecsForNoDistress = 100L * Integer.MAX_VALUE;
	final private static double _NParticleTimeStepsPerChunk = 10000d;
	final private static double _NParticleLegsPerChunk = 2d * _NParticleTimeStepsPerChunk;

	final private SimCaseManager.SimCase _simCase;
	final private ParticlesFile _particlesFile;
	/**
	 * These formatters manages the generation of the corresponding DOM objects and
	 * the output to files.
	 */
	final private LsFormatter _logFormatter1 = new LsFormatter();
	final private LsFormatter _logFormatter2 = new LsFormatter();
	/**
	 * The root element for logFile; this is used to collect and report statistics
	 * for each time step.
	 */
	final private Element _logElement1;
	final private Element _logElement2;
	/** For collecting statistics for each object type and scenario. */
	final private SummaryStatistics _summaryStatistics;
	/** The ParticleSets (one per scenario). */
	final private ParticleSet[] _particleSets;
	final private Randomx[][] _randoms;
	/**
	 * The original model, containing all the information needed to run a
	 * simulation.
	 */
	final private Model _model;
	/** The location where execution progress is being reported. */
	final private File _progressDirectory;
	final private String[] _introChunks;
	final private String[] _motioningChunks;
	final private String[] _legChunks;
	final private String[] _particlesFileWriterChunks;
	final private String[] _wrapUpChunks;

	/** For Display-only. */
	private Tracker(final SimCaseManager.SimCase simCase, final Model model) {
		_simCase = simCase;
		final MyLogger logger = SimCaseManager.getLogger(_simCase);
		if (simCase != null) {
			_simCase.allowGoing();
			_simCase.setMainSaropsObject(this);
		}
		final String versionName = SimGlobalStrings.getStaticVersionName();
		_model = model;
		/** Set up the ChunkReporter. */
		_introChunks = null;
		_motioningChunks = null;
		_legChunks = null;
		_particlesFileWriterChunks = null;
		_wrapUpChunks = null;
		_progressDirectory = null;
		simCase.reportChunkDone();
		_model.writeEcho(_simCase);
		simCase.runOutChunks();
		_particlesFile = null;
		_particleSets = null;
		_summaryStatistics = null;
		simCase.runOutChunks();
		/** Initialize the execution log. */
		_logElement1 = _logElement2 = null;
		final Date currentDate = new Date(System.currentTimeMillis());
		final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy MMM dd  hh.mm.ss a.z");
		final String timeString = simpleDateFormat.format(currentDate);
		logger.out(String.format("Finished initializing Display at %s: %s", timeString, versionName));
		_randoms = null;
	}

	private Tracker(final SimCaseManager.SimCase simCase, final long entryTimeInMillis, final Model model) {
		_simCase = simCase;
		final MyLogger logger = SimCaseManager.getLogger(_simCase);
		if (simCase != null) {
			_simCase.allowGoing();
			_simCase.setMainSaropsObject(this);
		}
		final SimGlobalStrings simGlobalStrings = SimCaseManager.getSimGlobalStrings(simCase);
		logger.out("Starting Tracker Ctor.");
		final String versionName = SimGlobalStrings.getStaticVersionName();
		final boolean logToErrFiles = simGlobalStrings.getLogToErrFiles();
		_model = model;
		/** Set up the ChunkReporter. */
		_introChunks = new String[] { //
				"Read in Model", //
				"Set Currents and Winds", //
				"Set up Land", //
				"Dumped Land to Shp Files", //
				"Set up Etopo", //
				"Dumped Etopo to Shp Files", //
				"Created Initial Particles", //
				"Miscellaneous Initialization" //
		};
		final int nIntroChunks = _introChunks.length;
		/** Set up the motioning chunks. */
		final long firstOutputRefSecsX = _model.getFirstOutputRefSecs();
		final long lastOutputRefSecsX = _model.getLastOutputRefSecs();
		final long monteCarloSecs = model.getMonteCarloSecs();
		final int totalNParticles = _model.getTotalNParticles();
		final long estSecs = lastOutputRefSecsX - firstOutputRefSecsX;
		final boolean includeSecs = true;
		final String firstOutputTimeStringX = TimeUtilities.formatTime(firstOutputRefSecsX, includeSecs);
		final String lastOutputTimeStringX = TimeUtilities.formatTime(lastOutputRefSecsX, includeSecs);
		final int estNTimes = (int) Math.ceil(((double) estSecs) / monteCarloSecs);
		final long nMotioningChunksX = Math.round((totalNParticles * estNTimes) / _NParticleTimeStepsPerChunk);
		final int nMotioningChunks = Math.max(2, Math.min(25, (int) Math.abs(nMotioningChunksX)));
		String[] motioningChunks = null;
		String s = String.format(
				"TotalNParticles[%d] firstOutputTimeX[%s] " + "lastOutputTimeX[%s], estNTimes[%d] "
						+ "nMotioningChunksX[%d] nMotioningChunks[%d].", //
				totalNParticles, firstOutputTimeStringX, lastOutputTimeStringX, estNTimes, nMotioningChunksX,
				nMotioningChunks);
		logger.out(s);
		try {
			motioningChunks = new String[nMotioningChunks];
		} catch (final Exception e1) {
			s = String.format("\n\nException!! TotalNParticles[%d] estNTimes[%d] " + "nMotioningChunks[%d].\n", //
					totalNParticles, estNTimes, nMotioningChunks);
			SimCaseManager.err(_simCase, s);
			s = StringUtilities.getStackTraceString(e1);
			SimCaseManager.err(_simCase, s);
		}
		_motioningChunks = motioningChunks;
		for (int iChunk = 0; iChunk < nMotioningChunks; ++iChunk) {
			_motioningChunks[iChunk] = String.format("Motioning chunk %d of %d", //
					iChunk, nMotioningChunks);
		}
		/** Set up the Leg chunks. */
		final List<Sortie> sorties = _model.getSorties();
		int nLegsTotal = 0;
		for (final Sortie element : sorties) {
			nLegsTotal += element.getDistinctInputLegs().size();
		}
		final int nLegChunks = (int) Math.round((_model.getTotalNParticles() * nLegsTotal) / _NParticleLegsPerChunk);
		_legChunks = new String[nLegChunks];
		for (int iChunk = 0; iChunk < nLegChunks; ++iChunk) {
			_legChunks[iChunk] = String.format("Sortie-Legs Chunk %d of %d", //
					iChunk, nLegChunks);
		}
		/** Set up the last two chunks. */
		_particlesFileWriterChunks = new String[] {
				"Write Ptcl File 1", "Write Ptcl File 2", "Write Ptcl File 3", "Write Ptcl File 4", "Write Ptcl File 5"
		};
		_wrapUpChunks = new String[] {
				"Write Xml"
		};
		final String[][] sections = new String[][] {
				_introChunks, _motioningChunks, _legChunks, _particlesFileWriterChunks, _wrapUpChunks
		};
		final String[] sectionNames = new String[] {
				"Intro", "Motion", "Legs", "ParticlesFileWrite", "WrapUp"
		};

		/**
		 * Before we can announce anything, we have to set up the progress directory.
		 */
		final String particlesFilePathCore = _model.getParticlesFilePathCore();
		_progressDirectory = new File(particlesFilePathCore + "_progress/");
		/** Clean up the progress directory. */
		_progressDirectory.mkdirs();
		final File[] filesX = _progressDirectory.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(final File dir, final String name) {
				return name.toLowerCase().startsWith("progress") || name.toLowerCase().endsWith(".txt");
			}
		});
		final File[] files = filesX == null ? new File[0] : filesX;
		for (final File file : files) {
			file.delete();
		}
		SimCaseManager.out(_simCase, String.format("Progress _progressDirectory[%s] cleaned up.",
				StringUtilities.getCanonicalPath(_progressDirectory)));
		/** Set up the ChunkReporter and report the first Chunk. */
		final boolean[] criticalSections = new boolean[] {
				true, false, false, true, true
		};
		final int nProgressSteps = simGlobalStrings.getNProgressSteps();
		_simCase.setChunkReporter(_progressDirectory, nProgressSteps, sectionNames, criticalSections, sections);
		/** Announce that the model has been read in. */
		simCase.reportChunkDone();
		/**
		 * Set the various forms of environmental data, and write out shp files. This is
		 * the only place where we MIGHT choose NOT to overwrite the environmental
		 * files, but we do overwrite, thereby saving space in EngineFiles.
		 */
		final TreeSet<ModelReader.StringPlus> envStringPluses = _model.setEnvironment(simCase, /* stashEnvFiles= */true,
				/* overwriteEnvFiles = */true);
		_model.addStringPluses(envStringPluses);
		final String badCurrentsString = _model.getCurrentsUvGetter() == null ? "Bad Drifts" : "";
		final String badWindsString = _model.getWindsUvGetter() == null ? "Bad Winds" : "";
		if (badCurrentsString.length() > 0 || badWindsString.length() > 0) {
			_particlesFile = null;
			_particleSets = null;
			_summaryStatistics = null;
			_logElement1 = _logElement2 = null;
			final boolean ignored = true;
			_simCase.stopIfGoing(ignored);
			throw new RuntimeException(String.format("%s %s", badCurrentsString, badWindsString));
		}
		simCase.reportChunkDone();
		_model.setShorelineFinderAndEtopo(simCase);
		/**
		 * Two chunks are now done because ShorelineFinder and Etopo are both set.
		 */
		simCase.reportChunkDone();
		simCase.reportChunkDone();
		if (!getKeepGoing()) {
			simCase.runOutChunks();
			_particlesFile = null;
			_particleSets = null;
			_summaryStatistics = null;
			_logElement1 = _logElement2 = null;
			_randoms = null;
			return;
		}
		/** Write out the echo, now that the environment has been set. */
		_model.writeEcho(_simCase);
		/**
		 * Record what we are using about Land and Etopo in shape files here.
		 */
		final String gshhsShpFilePath = _model.getGshhsShpFilePath();
		StaticUtilities.deleteAnyFile(new File(gshhsShpFilePath));
		final ShorelineFinder shorelineFinder = _model.getShorelineFinder();
		if (shorelineFinder.getNGcas() > 0) {
			final ArrayList<Loop3> gshhsLoops = shorelineFinder.getLoops();
			try {
				ShpFileWriter.writeShpFileOfLoop3s(logger, _WritePolylines, new File(gshhsShpFilePath),
						/* addShpToName= */true, gshhsLoops, /* clearIdlProblems= */true, /* writeAsSections= */false,
						/* nMaxInSection= */-1, logToErrFiles);
				if (model.getDumpGshhsKmz()) {
					final String filePath = _model.getKmzFilePath();
					KmlDocument.dumpGshhsKmz(filePath, gshhsLoops, /* kmlDocumentId= */"SimLand Context",
							/* kmlDocumentName= */"SimLand Context", /* description= */"Context Loops",
							/* normalWidth= */1.5, /* highlightWidth= */2.0, /* coreString= */"Context",
							/* color= */ColorUtils._Cyan, /* fill= */false, /* outline= */true);
				}
			} catch (final Exception e) {
				MainRunner.HandleFatal(_simCase, new RuntimeException(e));
			}
			simCase.reportChunkDone();
			if (!getKeepGoing()) {
				simCase.runOutChunks();
				_particlesFile = null;
				_particleSets = null;
				_summaryStatistics = null;
				_logElement1 = _logElement2 = null;
				simCase.runOutChunks();
				_randoms = null;
				return;
			}
		}
		final String etopoShpFilePath = _model.getEtopoShpFilePath();
		StaticUtilities.deleteAnyFile(new File(etopoShpFilePath));
		final Etopo etopo = _model.getEtopo();
		if (etopo != null && etopo.getIsValid()) {
			final float[] depths = _model.getMaximumAnchorDepthsInM();
			if (depths != null && depths.length > 0) {
				final boolean augmentDepths = false;
				final boolean useInclusionExclusion = false;
				_model.setDepthInfos(simCase, augmentDepths, useInclusionExclusion);
				try {
					etopo.dumpShpFiles(logger, _WritePolylines, etopoShpFilePath, logToErrFiles);
				} catch (final Exception e) {
					MainRunner.HandleFatal(_simCase, new RuntimeException(e));
				}
			}
			simCase.reportChunkDone();
			if (!getKeepGoing()) {
				simCase.runOutChunks();
				_particlesFile = null;
				_particleSets = null;
				_summaryStatistics = null;
				_logElement1 = _logElement2 = null;
				simCase.runOutChunks();
				_randoms = null;
				return;
			}
		}
		/** Finish creating the Scenarios. */
		_model.close(simCase);

		/**
		 * Before we set the particles, set their random number generators; there's one
		 * for each particle.
		 */
		final long coreSeed = model.getRandomSeed();
		final Randomx r = new Randomx(coreSeed);
		final Random r2 = new Random(r.nextLong());
		final int nScenarii = _model.getNScenarii();
		final int nParticlesPerScenario = _model.getNParticlesPerScenario();
		_randoms = new Randomx[nScenarii][nParticlesPerScenario];
		HashSet<Long> seeds = new HashSet<>();
		for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
			for (int iParticle = 0; iParticle < nParticlesPerScenario; ++iParticle) {
				long seed;
				for (seed = r.nextLong() + r2.nextLong(); !seeds.add(seed); seed = r.nextLong() + r2.nextLong()) {
				}
				_randoms[iScenario][iParticle] = new Randomx(seed);
			}
		}
		seeds = null;

		/**
		 * Build the particles, and from them, get the real start time and end time.
		 */
		_particleSets = new ParticleSet[nScenarii];
		long firstOutputRefSecsY = _model.getFirstOutputRefSecs();
		long lastOutputRefSecsY = _model.getLastOutputRefSecs();
		/**
		 * Create the initial cloud and adjust the real start (end) time for regular
		 * (reverse) scenario.
		 */
		final PreDistressModel.Itinerary[][] itineraries = new PreDistressModel.Itinerary[nScenarii][];
		for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
			final Scenario scenario = model.getScenario(iScenario);
			_particleSets[iScenario] = new ParticleSet(this, scenario, nParticlesPerScenario);
			final ParticleSet particleSet = _particleSets[iScenario];
			itineraries[iScenario] = createInitialCloud(particleSet, iScenario, nParticlesPerScenario,
					_randoms[iScenario]);
			SimCaseManager.out(simCase,
					"Finished Setting initial cloud for scenario # " + iScenario + " of " + nScenarii + " scenarii.");
			for (int iParticle = 0; iParticle < nParticlesPerScenario; ++iParticle) {
				final Particle particle = _particleSets[iScenario]._particles[iParticle];
				final StateVector stateVector = particle.getLatestStateVector();
				final long particleSimSecs = stateVector.getSimSecs();
				final long particleRefSecs = _model.getRefSecs(particleSimSecs);
				if (_model.getReverseDrift()) {
					lastOutputRefSecsY = Math.max(lastOutputRefSecsY, particleRefSecs);
				} else {
					firstOutputRefSecsY = Math.min(firstOutputRefSecsY, particleRefSecs);
				}
			}
			SimCaseManager.out(simCase,
					String.format("Created %d particles for scenario %d.", nParticlesPerScenario, iScenario));
			final String firstOutputTimeStringY = TimeUtilities.formatTime(firstOutputRefSecsY, includeSecs);
			final String lastOutputTimeStringY = TimeUtilities.formatTime(lastOutputRefSecsY, includeSecs);
			final String sY = String.format("TotalNParticles[%d] firstOutputTimeY[%s] " + "lastOutputTimeY[%s].", //
					totalNParticles, firstOutputTimeStringY, lastOutputTimeStringY, estNTimes);
			logger.out(sY);
		}
		/**
		 * Now that we have the first and last output times, we can build the
		 * ParticlesFile object, which calls model's "computeRefTimes," which uses
		 * model's monteCarloTimeStep.
		 */
		_particlesFile = new ParticlesFile(this, firstOutputRefSecsY, lastOutputRefSecsY);
		/**
		 * For each particle, set the initial probability, the distressType, the
		 * distressTime, and possibly the distressLatLng in _particlesFile.
		 */
		final int originatingSotId = model.getOriginatingSotWithWt().getSot().getId();
		for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
			final ParticleSet particleSet = _particleSets[iScenario];
			final boolean writeOcTablesForThisScenario = model.getWriteOcTables() && itineraries[iScenario] != null;
			for (int iParticle = 0; iParticle < nParticlesPerScenario; ++iParticle) {
				final ParticleIndexes prtclIndxs = ParticleIndexes.getStandardOne(_model, iScenario, iParticle);
				final Particle particle = particleSet._particles[iParticle];
				/**
				 * We cannot do this here for a reverse drift. We want birth and distress to be
				 * the beginning of the run.
				 */
				if (!_model.getReverseDrift()) {
					final long birthSimSecs = particle.getBirthSimSecs();
					final long birthRefSecs = _model.getRefSecs(birthSimSecs);
					final long distressSimSecs = particle.getDistressSimSecs();
					final long distressRefSecs = _model.getRefSecs(distressSimSecs);
					final long expirationSimSecs = particle.getExpirationSimSecs();
					final long expirationRefSecs = _model.getRefSecs(expirationSimSecs);
					_particlesFile.setBirthRefSecs(prtclIndxs, birthRefSecs);
					_particlesFile.setDistressRefSecs(prtclIndxs, distressRefSecs);
					_particlesFile.setExpirationRefSecs(prtclIndxs, expirationRefSecs);
					final LatLng3 distressLatLng = particle.getDistressLatLng();
					if (distressLatLng != null) {
						_particlesFile.setDistressLatLng(prtclIndxs, distressLatLng);
					}
					_particlesFile.setSailorQuality(prtclIndxs, particle.getPsd());
				}
				final int distressTypeId = particle.getDistressObjectType().getId();
				_particlesFile.setDistressType(prtclIndxs, distressTypeId);
				_particlesFile.setUnderwayType(prtclIndxs, originatingSotId);
				if (writeOcTablesForThisScenario) {
					_particlesFile.setItinerary(prtclIndxs, itineraries[iScenario][iParticle]);
				}
			}
			final Collection<SearchObjectType> searchObjectTypes = _model.getSearchObjectTypes();
			for (final SearchObjectType sot : searchObjectTypes) {
				final int sotId = sot.getId();
				if (sotId >= 0) {
					final int sotOrd = _model.getSotOrd(sotId);
					final ParticleIndexes prtclIndxs = ParticleIndexes.getMeanOne(_model, iScenario, sotOrd);
					final Particle meanParticle = particleSet.getEnvMeanParticle(this, sotId);
					if (meanParticle != null) {
						final long birthSimSecs = meanParticle.getBirthSimSecs();
						final long birthRefSecs = _model.getRefSecs(birthSimSecs);
						_particlesFile.setBirthRefSecs(prtclIndxs, birthRefSecs);
					}
				}
			}
		}
		if (!getKeepGoing()) {
			simCase.runOutChunks();
			_summaryStatistics = null;
			_logElement1 = _logElement2 = null;
			return;
		}
		/** Initialize the execution log. */
		_logElement1 = _logFormatter1.newElement("SIMLOG");
		_logElement2 = _logFormatter2.newElement("SIMLOG");
		final Date currentDate = new Date(System.currentTimeMillis());
		final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy MMM dd  hh.mm.ss a.z");
		final String timeString = simpleDateFormat.format(currentDate);
		_logElement1.setAttribute("timeCreated", timeString);
		_logElement2.setAttribute("timeCreated", timeString);
		_logElement1.setAttribute("versionName", versionName);
		_logElement2.setAttribute("versionName", versionName);
		_summaryStatistics = new SummaryStatistics(_model, this);
		SimCaseManager.out(_simCase,
				String.format("Finished initializing Tracker and files.  " + "StartTime[%s] to EndTime[%s]",
						TimeUtilities.formatTime(firstOutputRefSecsY, true),
						TimeUtilities.formatTime(lastOutputRefSecsY, true)));
		/** Catch up (if necessary) with the chunk reporting. */
		simCase.reportChunksDone(nIntroChunks);
	}

	public SimCaseManager.SimCase getSimCase() {
		return _simCase;
	}

	/** Sorted by time then particle. */
	final public static TreeMap<ParticleTime, ParticleTime> _TimeThenParticle = new TreeMap<>(
			new Comparator<ParticleTime>() {

				@Override
				public int compare(final ParticleTime o1, final ParticleTime o2) {
					final long time1 = o1.getRefSecs();
					final long time2 = o2.getRefSecs();
					if (time1 != time2) {
						return time1 < time2 ? -1 : 1;
					}
					final int overallIndex1 = o1.getOverallIndex();
					final int overallIndex2 = o2.getOverallIndex();
					if (overallIndex1 != overallIndex2) {
						return overallIndex1 < overallIndex2 ? -1 : 1;
					}
					return 0;
				}
			});
	/** Sorted by particle then time. */
	final public static TreeMap<ParticleTime, ParticleTime> _ParticleThenTime = new TreeMap<>(
			new Comparator<ParticleTime>() {

				@Override
				public int compare(final ParticleTime o1, final ParticleTime o2) {
					final int overallIndex1 = o1.getOverallIndex();
					final int overallIndex2 = o2.getOverallIndex();
					if (overallIndex1 != overallIndex2) {
						return overallIndex1 < overallIndex2 ? -1 : 1;
					}
					final long time1 = o1.getRefSecs();
					final long time2 = o2.getRefSecs();
					if (time1 != time2) {
						return time1 < time2 ? -1 : 1;
					}
					return 0;
				}
			});
	final public static TreeSet<ParticleIndexes> _Distincts = new TreeSet<>();
	final public static TreeSet<long[]> _DistinctRefSecsS = new TreeSet<>(new Comparator<long[]>() {

		@Override
		public int compare(final long[] o1, final long[] o2) {
			final long refSecs1 = o1[0];
			final long refSecs2 = o2[0];
			return refSecs1 < refSecs2 ? -1 : (refSecs1 == refSecs2 ? 0 : 1);
		}
	});

	private void doTimeSteps() {
		final MyLogger logger = SimCaseManager.getLogger(_simCase);
		final long[] refSecsS = _particlesFile.getRefSecsS();
		final int nRefSecsS = refSecsS.length;
		final int nIntroChunks = _introChunks.length;
		final int nMotioningChunks = _motioningChunks == null ? 0 : _motioningChunks.length;
		final int nLegChunks = _legChunks == null ? 0 : _legChunks.length;
		final int nParticlesFileWriterChunks = _particlesFileWriterChunks.length;
		final int nWrapUpChunks = _wrapUpChunks.length;
		/**
		 * Now that we have the full time extent of the simulation, let the model know
		 * about it.
		 */
		_model.setFullRefSecsExtent(new long[] {
				refSecsS[0], refSecsS[nRefSecsS - 1]
		});
		/** Don't let anything expire after the end of the simulation. */
		if (!_model.getReverseDrift()) {
			updateExpirationRefSecsS();
		}
		final long[] simSecsS = new long[nRefSecsS];
		/** Do the intro. */
		for (int k = 0; k < nRefSecsS; ++k) {
			if (!_model.getReverseDrift()) {
				simSecsS[k] = refSecsS[k];
			} else {
				simSecsS[k] = _model.getSimSecs(refSecsS[nRefSecsS - 1 - k]);
			}
		}
		/**
		 * Do the motioning. To log elapsed time in millis during the particle track
		 * generation, we set the base time.
		 */
		int nMotioningChunksReported = 0;
		for (int timeIdx = 0; timeIdx < nRefSecsS; ++timeIdx) {
			if (timeIdx % 5 == 0) {
				logger.out(String.format("Starting work on timeIndex %d of %d.", timeIdx, nRefSecsS));
			}
			timeUpdate(simSecsS, timeIdx);
			final int nMotioningChunksDone = (int) Math.round((double) (timeIdx + 1) / nRefSecsS * nMotioningChunks);
			if (nMotioningChunksDone > nMotioningChunksReported) {
				_simCase.reportChunksDone(nIntroChunks + nMotioningChunksDone);
				nMotioningChunksReported = nMotioningChunksDone;
			}
			if (!getKeepGoing()) {
				_simCase.runOutChunks();
				return;
			}
		}
		_simCase.reportChunksDone(nIntroChunks + nMotioningChunks);
		logger.out("Did all Time Updates.");

		/** Sorties. */
		final List<Sortie> sorties = _model.getSorties();
		int nLegsTotal = 0;
		for (final Sortie element : sorties) {
			nLegsTotal += element.getDistinctInputLegs().size();
		}
		int nLegsDone = 0;
		int nLegChunksReported = 0;
		for (final Sortie sortie : sorties) {
			final String id = sortie.getId() == null ? "Null-Id" : sortie.getId();
			final String name = sortie.getName() == null ? "Null-Name" : sortie.getName();
			updateParticlesFileForSortie(sortie);
			logger.out(String.format("Did Sortie[%s/%s].", id, name));
			nLegsDone += sortie.getDistinctInputLegs().size();
			final int nLegChunksDone = (int) Math.round(((double) nLegsDone / nLegsTotal) * nLegChunks);
			if (nLegChunksDone > nLegChunksReported) {
				_simCase.reportChunksDone(nIntroChunks + nMotioningChunks + nLegChunksDone);
				nLegChunksReported = nLegChunksDone;
			}
			if (!getKeepGoing()) {
				_simCase.runOutChunks();
				return;
			}
		}

		if (!getKeepGoing()) {
			_simCase.runOutChunks();
			return;
		}
		/** Catch up (if necessary) with the chunk reporting. */
		_simCase.reportChunksDone(nIntroChunks + nMotioningChunks + nLegChunks);
		logger.out("Did all Sorties.");
		/** A minor step; normalize the probabilities. */
		_particlesFile.normalizeProbabilities();
		logger.out("Probabilities Normalized.");
		/**
		 * At this point, we know each particle's anchoring time. Record them in
		 * _particlesFile.
		 */
		updateParticlesFileForAnchoring();
		logger.out("Particles file updated for anchoring.");
		if (!getKeepGoing()) {
			_simCase.runOutChunks();
			return;
		}
		/**
		 * For reverse drift, we wait until now to set the birth, distress, and
		 * expiration times.
		 */
		final long firstRefSecs = refSecsS[0];
		final long lastRefSecs = refSecsS[nRefSecsS - 1];
		updateBirthDistressAndExpirationTimesForReverseDrift(firstRefSecs, lastRefSecs);
		logger.out("Particles file updated for birth, distress, and expiration times.");

		/** Create particlesFileWriter and populate its snapshots. */
		final ParticlesFileWriter particlesFileWriter = new ParticlesFileWriter(this);
		for (int timeIdx = 0; timeIdx < nRefSecsS; ++timeIdx) {
			final boolean isLastRefSecs = timeIdx == nRefSecsS - 1;
			final long simSecs;
			final int realTimeStep;
			final long refSecs;
			if (!_model.getReverseDrift()) {
				refSecs = simSecs = refSecsS[timeIdx];
				realTimeStep = timeIdx;
			} else {
				/**
				 * With reverse drift, we work backwards in time, but use the reverse of these
				 * times.
				 */
				realTimeStep = nRefSecsS - 1 - timeIdx;
				refSecs = refSecsS[realTimeStep];
				simSecs = _model.getSimSecs(refSecs);
			}
			dumpTimeStepToLogFile(simSecs, isLastRefSecs);
			if (!getKeepGoing()) {
				_simCase.runOutChunks();
				return;
			}
			particlesFileWriter.buildSnapshot(simSecs);
			if (timeIdx % 25 == 0) {
				logger.out(String.format("Time Step %d(of %d) processed and logged.", timeIdx, nRefSecsS));
			}
		}

		logger.out(String.format("All %d time steps processed and logged.", nRefSecsS));
		/** Print out bad LatLngs (if any) for Reverse Drift. */
		if (_model.getReverseDrift()) {
			if (_particlesFile.dumpBadLatsAndLngs()) {
				logger.out("Bad LatLngs dumped");
			}
		}
		logger.out(String.format("End of simulation"));
		/** Write out the Particles file. */
		try {
			particlesFileWriter.writeNetCdfFile(nParticlesFileWriterChunks);
		} catch (final Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		_simCase.reportChunksDone(nIntroChunks + nMotioningChunks + nParticlesFileWriterChunks);
		/** Wrap up the stats collecting. */
		_summaryStatistics.endSimulation();
		/** Set key variables for stashing. */
		final String stashedSimFilePath = _model.getStashedSimFilePath();
		final String stashedSimFileName = new File(stashedSimFilePath).getName();
		final int lastStashedSimFilePathIndex = stashedSimFileName.lastIndexOf(SimCaseManager._SimEndingLc);
		final String modelFilePath = _model.getSimFilePath();
		/** Write out the SAROPS version of summaryStatistics. */
		final String statsFilePath = _model.getParticlesFilePathCore() + "_stats.xml";
		_summaryStatistics.writeWithNoTimeComment(_simCase, statsFilePath);
		/** Write out the EngineFiles version of summaryStatistics. */
		final File stashResultDir = AbstractOutFilesManager.GetEngineFilesDir(_simCase, modelFilePath, "SimResult");
		final String stashedStatsFileName = stashedSimFileName.substring(0, lastStashedSimFilePathIndex) + "_stats.xml";
		final String stashedStatsFilePath = StringUtilities
				.getCanonicalPath(new File(stashResultDir, stashedStatsFileName));
		_summaryStatistics.writeWithTimeComment(_simCase, stashedStatsFilePath);

		/**
		 * Add to the logFormatters an element that contains the information about the
		 * Out-Of-Area violations.
		 */
		for (int k = 0; k < 2; ++k) {
			final boolean doingStashCopy = k == 1;
			final Element outOfAreaIncidentsElement;
			if (!doingStashCopy) {
				outOfAreaIncidentsElement = _logFormatter1.newChild(_logElement1, "OUT_OF_AREA_INCIDENTS");
			} else {
				outOfAreaIncidentsElement = _logFormatter2.newChild(_logElement2, "OUT_OF_AREA_INCIDENTS");
			}
			final Collection<Model.OutOfAreaIncident> outOfAreaIncidents = _model.getOutOfAreaIncidents();
			if (outOfAreaIncidents != null) {
				for (final Model.OutOfAreaIncident outOfAreaIncident : outOfAreaIncidents) {
					final Element outOfAreaElement;
					if (!doingStashCopy) {
						outOfAreaElement = _logFormatter1.newChild(outOfAreaIncidentsElement, "Incident");
					} else {
						outOfAreaElement = _logFormatter2.newChild(outOfAreaIncidentsElement, "Incident");
					}
					final ParticleIndexes prtclIndxs = outOfAreaIncident._prtclIndxs;
					final int iScenario = prtclIndxs.getScenarioIndex();
					final int scenarioId = _model.getScenario(iScenario).getId();
					final int iParticle = prtclIndxs.getParticleIndex();
					final long outOfAreaRefSecs = outOfAreaIncident.getRefSecs();
					outOfAreaElement.setAttribute("time", "" + outOfAreaRefSecs);
					outOfAreaElement.setAttribute("dtg",
							"" + TimeUtilities.formatTime(outOfAreaRefSecs, /* includeSecs= */true));
					outOfAreaElement.setAttribute("particle", "" + iParticle);
					outOfAreaElement.setAttribute("scenario", "" + scenarioId);
					outOfAreaElement.setAttribute("typeOfParticle", "" + outOfAreaIncident.getTypeOfParticle());
					outOfAreaElement.setAttribute("lat",
							LsFormatter.StandardFormatForLatOrLng(outOfAreaIncident.getLatLng().getLat()));
					outOfAreaElement.setAttribute("lng",
							LsFormatter.StandardFormatForLatOrLng(outOfAreaIncident.getLatLng().getLng()));
					outOfAreaElement.setAttribute("otherIncidents",
							LsFormatter.StandardFormat(outOfAreaIncident._numberOfAdditionalIncidents));
				}
			}
			/**
			 * Add to the logFormatters, elements that give the dynamic environment work.
			 */
			final Collection<BoxDefinition> currentsBoxDefinitions = _model.getCurrentsBoxDefinitions();
			if (currentsBoxDefinitions != null && currentsBoxDefinitions.size() > 0) {
				final Element currentsBoxesElement;
				if (!doingStashCopy) {
					currentsBoxesElement = _logFormatter1.newChild(_logElement1, "CURRENTS_BOXES");
				} else {
					currentsBoxesElement = _logFormatter2.newChild(_logElement2, "CURRENTS_BOXES");
				}
				for (final BoxDefinition boxDefinition : currentsBoxDefinitions) {
					final Element currentsBoxElement;
					if (!doingStashCopy) {
						currentsBoxElement = _logFormatter1.newChild(currentsBoxesElement, "CURRENTS_BOX");
					} else {
						currentsBoxElement = _logFormatter2.newChild(currentsBoxesElement, "CURRENTS_BOX");
					}
					final String[] stringsInUse = boxDefinition.getStringsInUse();
					currentsBoxElement.setAttribute("lowTime", stringsInUse[0]);
					currentsBoxElement.setAttribute("highTime", stringsInUse[1]);
					currentsBoxElement.setAttribute("left", stringsInUse[2]);
					currentsBoxElement.setAttribute("bottom", stringsInUse[3]);
					currentsBoxElement.setAttribute("right", stringsInUse[4]);
					currentsBoxElement.setAttribute("top", stringsInUse[5]);
				}
			}
			final Collection<BoxDefinition> windsBoxDefinitions = _model.getWindsBoxDefinitions();
			if (windsBoxDefinitions != null && windsBoxDefinitions.size() > 0) {
				final Element windsBoxesElement;
				if (!doingStashCopy) {
					windsBoxesElement = _logFormatter1.newChild(_logElement1, "WINDS_BOXES");
				} else {
					windsBoxesElement = _logFormatter2.newChild(_logElement2, "WINDS_BOXES");
				}
				for (final BoxDefinition boxDefinition : windsBoxDefinitions) {
					final Element windsBoxElement;
					if (!doingStashCopy) {
						windsBoxElement = _logFormatter1.newChild(windsBoxesElement, "WINDS_BOX");
					} else {
						windsBoxElement = _logFormatter2.newChild(windsBoxesElement, "WINDS_BOX");
					}
					final String[] stringsInUse = boxDefinition.getStringsInUse();
					windsBoxElement.setAttribute("lowTime", stringsInUse[0]);
					windsBoxElement.setAttribute("highTime", stringsInUse[1]);
					windsBoxElement.setAttribute("left", stringsInUse[2]);
					windsBoxElement.setAttribute("bottom", stringsInUse[3]);
					windsBoxElement.setAttribute("right", stringsInUse[4]);
					windsBoxElement.setAttribute("top", stringsInUse[5]);
				}
			}
		}

		/** Write out the SAROPS LogFormatter. */
		final String logFilePath = _model.getLogFilePath();
		try (final FileOutputStream fos1 = new FileOutputStream(logFilePath)) {
			_logFormatter1.dumpWithNoTimeComment(_logElement1, fos1);
		} catch (final Exception e) {
			MainRunner.HandleFatal(_simCase, new RuntimeException(e));
		}
		/** Write out the EngineFiles LogFormatter. */
		final String stashedLogFileName = stashedSimFileName.substring(0, lastStashedSimFilePathIndex) + "-simLog.xml";
		final File stashedLogFile = new File(stashResultDir, stashedLogFileName);
		final String stashedLogFilePath = StringUtilities.getCanonicalPath(stashedLogFile);
		try (final FileOutputStream fos2 = new FileOutputStream(stashedLogFilePath)) {
			if (stashedLogFilePath == null) {
				throw new IOException("Tracker, Stashed Log Files");
			}
			_logFormatter2.dumpWithTimeComment(_logElement2, fos2);
		} catch (final Exception e) {
			MainRunner.HandleFatal(_simCase, new RuntimeException(e));
		}

		_simCase.reportChunksDone(nIntroChunks + nMotioningChunks + nParticlesFileWriterChunks + nWrapUpChunks);
		_simCase.runOutChunks();
	}

	private void updateExpirationRefSecsS() {
		if (_model.getReverseDrift()) {
			return;
		}
		final long[] fullRefSecsExtent = _model.getFullRefSecsExtent();
		final long lastRefSecs = fullRefSecsExtent[fullRefSecsExtent.length - 1];
		final long lastSimSecs = _model.getSimSecs(lastRefSecs);
		final int nScenarii = _model.getNScenarii();
		final int nParticlesPerScenario = _model.getNParticlesPerScenario();
		for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
			final ParticleSet particleSet = _particleSets[iScenario];
			for (int iParticle = 0; iParticle < nParticlesPerScenario; ++iParticle) {
				final ParticleIndexes prtclIndxs = ParticleIndexes.getStandardOne(_model, iScenario, iParticle);
				final Particle particle = particleSet._particles[iParticle];
				particle.capExpirationSimSecs(lastSimSecs);
				final long expirationSimSecs = particle.getExpirationSimSecs();
				final long expirationRefSecs = _model.getRefSecs(expirationSimSecs);
				_particlesFile.setExpirationRefSecs(prtclIndxs, expirationRefSecs);
			}
		}
	}

	public enum SaropsClmnType {
		PARTICLE("PrtclName"), //
		OT("ObjTp"), //
		ALPHA_PRIOR("AlphaPrior"), //
		PF_ALPHA("Pf-Alpha"), //
		BRAVO_PRIOR("BravoPrior"), //
		END_LAT("EndLat"), //
		END_LNG("EndLng"); //

		final public String _shortString;

		final private static SaropsClmnType[] _SaropsClmnTypes = values();

		SaropsClmnType(final String shortString) {
			_shortString = shortString;
		}

		private static String getHdg(final int j, final List<Sortie> sorties) {
			if (j < _SaropsClmnTypes.length) {
				return _SaropsClmnTypes[j]._shortString;
			}
			try {
				final int iSortie = j - _SaropsClmnTypes.length;
				final Sortie sortie = sorties.get(iSortie);
				return String.format("Pf-%s", sortie.getId());
			} catch (final Exception e) {
				e.printStackTrace();
				return null;
			}
		}

		private static int getNCols(final int nSorties) {
			return _SaropsClmnTypes.length + nSorties;
		}
	}

	private void buildParticlesSpreadsheet() {
		final int nParticlesPerScenario = _model.getNParticlesPerScenario();
		final int nScenarii = _model.getNScenarii();
		final long[] refSecsS = _particlesFile.getRefSecsS();
		final int nRefSecsS = refSecsS.length;
		final long lastRefSecs = refSecsS[nRefSecsS - 1];
		final List<Sortie> allSorties = _model.getSorties();
		final int nSorties = allSorties == null ? 0 : allSorties.size();
		try (final XSSFWorkbook particlesWorkbook = new XSSFWorkbook()) {
			final XSSFCellStyle centerStyle = particlesWorkbook.createCellStyle();
			centerStyle.setAlignment(HorizontalAlignment.CENTER);
			final XSSFCellStyle perCentStyle = particlesWorkbook.createCellStyle();
			perCentStyle.setDataFormat(particlesWorkbook.createDataFormat().getFormat("0.000%"));
			final Sheet particlesSheet = particlesWorkbook.createSheet("Prtcls");
			final int iHdgsRow = 0;
			final Row hdgsRow = particlesSheet.createRow(iHdgsRow);
			final int nClmns = SaropsClmnType.getNCols(nSorties);
			final String[] hdgs = new String[nClmns];
			int firstDataJx = -1;
			int lastDataJx = -1;
			for (int j = 0; j < nClmns; ++j) {
				final Cell cell = hdgsRow.createCell(j);
				lastDataJx = cell.getColumnIndex();
				if (firstDataJx < 0) {
					firstDataJx = lastDataJx;
				}
				final String hdg = SaropsClmnType.getHdg(j, allSorties);
				cell.setCellValue(hdg);
				hdgs[j] = hdg;
				cell.setCellStyle(centerStyle);
			}
			final int firstDataJ = firstDataJx;
			final int lastDataJ = lastDataJx;
			final int hdgsRowNum = hdgsRow.getRowNum();
			int firstDataIx = -1;
			int lastDataIx = -1;
			int iData = hdgsRowNum;
			for (int k1 = 0; k1 < nScenarii; ++k1) {
				for (int k2 = 0; k2 < nParticlesPerScenario; ++k2) {
					final ParticleIndexes prtclIndxs = ParticleIndexes.getStandardOne(_model, k1, k2);
					double pfAlpha = 1d;
					final double[] pFails = new double[nSorties];
					for (int iSortie = 0; iSortie < nSorties; ++iSortie) {
						final Sortie sortie = allSorties.get(iSortie);
						pFails[iSortie] = ComputePFail.computeFtPFail(_simCase, _particlesFile, prtclIndxs, sortie,
								_TrackerForOptnOnly, _TrackerPFailType, /* updateParticlesFile= */false);
						pfAlpha *= pFails[iSortie];
					}
					final LatLng3 endLatLng = _particlesFile.getLatLng(lastRefSecs, prtclIndxs);
					final Row dataRow = particlesSheet.createRow(++iData);
					lastDataIx = dataRow.getRowNum();
					if (firstDataIx < 0) {
						firstDataIx = lastDataIx;
					}
					for (int jj = 0; jj < nClmns; ++jj) {
						final int j = jj - firstDataJ;
						final Cell cell = dataRow.createCell(j);
						if (j == SaropsClmnType.PARTICLE.ordinal()) {
							cell.setCellValue(prtclIndxs.getString());
						} else if (j == SaropsClmnType.OT.ordinal()) {
							cell.setCellValue(_particlesFile.getObjectTypeId(lastRefSecs, prtclIndxs));
						} else if (j == SaropsClmnType.ALPHA_PRIOR.ordinal()) {
							cell.setCellValue(_particlesFile.getInitPrior(prtclIndxs));
							cell.setCellStyle(centerStyle);
						} else if (j == SaropsClmnType.PF_ALPHA.ordinal()) {
							cell.setCellValue(pfAlpha);
						} else if (j == SaropsClmnType.BRAVO_PRIOR.ordinal()) {
							cell.setCellValue(_particlesFile.getProbability(lastRefSecs, prtclIndxs));
							cell.setCellStyle(centerStyle);
						} else if (j == SaropsClmnType.END_LAT.ordinal()) {
							cell.setCellValue(endLatLng.getLat());
							cell.setCellStyle(centerStyle);
						} else if (j == SaropsClmnType.END_LNG.ordinal()) {
							cell.setCellValue(endLatLng.getLng());
							cell.setCellStyle(centerStyle);
						} else {
							final int iSortie = j - SaropsClmnType._SaropsClmnTypes.length;
							cell.setCellValue(pFails[iSortie]);
						}
					}
				}
			}
			final int firstDataI = firstDataIx;
			final int lastDataI = lastDataIx;
			/** Name the columns. */
			final String sheetName = particlesSheet.getSheetName();
			if (lastDataI >= firstDataI) {
				final boolean absRow = true;
				final boolean absCol = true;
				for (int j = firstDataJ; j <= lastDataJ; ++j) {
					final XSSFName columnName = particlesWorkbook.createName();
					final String cellName1 = ExcelDumper.getCellRef(firstDataI, absRow, j, absCol);
					final String cellName2 = ExcelDumper.getCellRef(lastDataI, absRow, j, absCol);
					final String hdg = hdgs[j - firstDataJ];
					final String excelName = ExcelDumper.createExcelName(hdg);
					columnName.setNameName(excelName);
					final String rangeString = String.format("%s!%s:%s", sheetName, cellName1, cellName2);
					columnName.setRefersToFormula(rangeString);
				}
			}
			final String stashedSimFilePath = _model.getStashedSimFilePath();
			final String stashedSimFileName = new File(stashedSimFilePath).getName();
			final int lastIndex = stashedSimFileName.lastIndexOf(SimCaseManager._SimEndingLc);
			final String particlesWorkbookName = stashedSimFileName.substring(0, lastIndex) + "-prtcls.xlsx";
			final String modelFilePath = _model.getSimFilePath();
			final File stashResultDir = AbstractOutFilesManager.GetEngineFilesDir(_simCase, modelFilePath, "SimResult");
			final File particlesWorkbookFile = new File(stashResultDir, particlesWorkbookName);
			ExcelDumper.closeAndWriteWorkBook(particlesWorkbook, particlesWorkbookFile);
		} catch (final IOException e) {
		}
	}

	/**
	 * For TimeUpdate, we turned the task over to the ParticleSet. We don't do that
	 * here because we don't do it scenario by scenario.
	 */
	private void updateParticlesFileForSortie(final Sortie sortie) {
		final int nScenarii = _model.getNScenarii();
		final int nParticlesPerScenario = _model.getNParticlesPerScenario();
		final int nParticles = nParticlesPerScenario * nScenarii;
		if (nParticles == 0) {
			return;
		}
		final SimGlobalStrings simGlobalStrings = _simCase.getSimGlobalStrings();
		final SimCaseManager simCaseManager = _simCase.getSimCaseManager();
		/** Boilerplate for multithreading... */
		final int minNPerSlice = simGlobalStrings.getMinNPerSliceInTracker();
		int nSlices = (nParticles + (minNPerSlice - 1)) / minNPerSlice;
		final Object lockOnWorkersThreadPool = simCaseManager.getLockOnWorkersThreadPool();
		Future<?>[] futures = null;
		final ArrayList<Integer> notTaskedISlices = new ArrayList<>();
		int nWorkers = 0;
		final boolean useMultithreading = true;
		if (useMultithreading && nSlices > 1) {
			synchronized (lockOnWorkersThreadPool) {
				final int nFreeWorkers = simCaseManager.getNFreeWorkerThreads(_simCase, "UpdateParticlesFileForSortie");
				nWorkers = Math.max(0, Math.min(nSlices - 1, nFreeWorkers));
				if (nWorkers < 2) {
					nWorkers = 0;
					nSlices = 1;
				} else {
					final int finalNSlices = nSlices = nWorkers + 1;
					futures = new Future<?>[nWorkers];
					for (int iWorker = 0; iWorker < nWorkers; ++iWorker) {
						final int finalIWorker = iWorker;
						final Runnable runnable = new Runnable() {
							@Override
							public void run() {
								/** ... to here. */
								runSliceForSortie(sortie, finalIWorker, finalNSlices);
							}
						};
						futures[iWorker] = simCaseManager.submitToWorkers(_simCase, runnable);
						if (futures[iWorker] == null) {
							notTaskedISlices.add(iWorker);
						}
					}
				}
			}
		}
		for (int iSlice = nWorkers; iSlice < nSlices; ++iSlice) {
			notTaskedISlices.add(iSlice);
		}
		for (final int iSlice : notTaskedISlices) {
			runSliceForSortie(sortie, iSlice, nSlices);
		}
		try {
			for (int iWorker = 0; iWorker < nWorkers; ++iWorker) {
				if (futures[iWorker] != null) {
					futures[iWorker].get();
				}
			}
		} catch (final ExecutionException e) {
		} catch (final InterruptedException e) {
		}
	}

	private static class ParticleTime {
		final private ParticleIndexes _prtclIndxs;
		final private ComputePFail.PdInfo _pdInfo;

		private ParticleTime(final ParticleIndexes prtclIndxs, final ComputePFail.PdInfo pdInfo) {
			_prtclIndxs = prtclIndxs;
			_pdInfo = pdInfo;
		}

		private int getOverallIndex() {
			return _prtclIndxs.getOverallIndex();
		}

		private long getRefSecs() {
			return _pdInfo._leg.getLegRefSecs1();
		}
	}

	private void runSliceForSortie(final Sortie sortie, final int myStartIndex, final int nSlices) {
		final int nScenarii = _model.getNScenarii();
		final int nParticlesPerScenario = _model.getNParticlesPerScenario();
		final int nParticles = nParticlesPerScenario * nScenarii;
		for (int iParticle = myStartIndex; iParticle < nParticles; iParticle += nSlices) {
			final int iScenario = iParticle / nParticlesPerScenario;
			final int iParticleThisScenario = iParticle % nParticlesPerScenario;
			final ParticleIndexes prtclIndxs = ParticleIndexes.getStandardOne(_model, iScenario, iParticleThisScenario);
			@SuppressWarnings("unused")
			final double pFail = ComputePFail.computeFtPFail(_simCase, _particlesFile, prtclIndxs, sortie,
					_TrackerForOptnOnly, _TrackerPFailType, /* updateParticlesFile= */true);
		}
	}

	private void updateParticlesFileForAnchoring() {
		if (_model.getReverseDrift()) {
			return;
		}
		final int nScenarii = _model.getNScenarii();
		final int nParticlesPerScenario = _model.getNParticlesPerScenario();
		for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
			final ParticleSet particleSet = _particleSets[iScenario];
			for (int iParticle = 0; iParticle < nParticlesPerScenario; ++iParticle) {
				final ParticleIndexes prtclIndxs = ParticleIndexes.getStandardOne(_model, iScenario, iParticle);
				final Particle particle = particleSet._particles[iParticle];
				final long anchoringSimSecs = particle.getAnchoringSimSecs();
				_particlesFile.setAnchoringRefSecs(prtclIndxs, anchoringSimSecs);
			}
		}
	}

	private void updateBirthDistressAndExpirationTimesForReverseDrift(final long firstRefSecs, final long lastRefSecs) {
		if (!_model.getReverseDrift()) {
			return;
		}
		final int nScenarii = _model.getNScenarii();
		final int nParticlesPerScenario = _model.getNParticlesPerScenario();
		for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
			for (int iParticle = 0; iParticle < nParticlesPerScenario; ++iParticle) {
				final ParticleIndexes prtclIndxs = ParticleIndexes.getStandardOne(_model, iScenario, iParticle);
				_particlesFile.setBirthRefSecs(prtclIndxs, firstRefSecs);
				_particlesFile.setDistressRefSecs(prtclIndxs, firstRefSecs);
				_particlesFile.setExpirationRefSecs(prtclIndxs, lastRefSecs);
			}
		}
	}

	private void timeUpdate(final long[] simSecsS, final int timeIdx) {
		final int nScenarii = _model.getNScenarii();
		final CurrentsUvGetter currentsUvGetter = _model.getCurrentsUvGetter();
		final WindsUvGetter windsUvGetter = _model.getWindsUvGetter();
		/** Do the following chunk serially. */
		final long simSecs = simSecsS[timeIdx];
		if (!currentsUvGetter.prepareIsTrivial() || !windsUvGetter.prepareIsTrivial()) {
			for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
				final ParticleSet particleSet = _particleSets[iScenario];
				final Particle[] particles = particleSet._particles;
				final int nParticles = particles.length;
				for (int iParticle = 0; iParticle < nParticles; ++iParticle) {
					final Particle particle = particles[iParticle];
					final StateVector latestStateVector = particle.getLatestStateVector();
					/**
					 * Unless this particle is adrift at or before simSecs, we don't need to do
					 * anything.
					 */
					if (particle.getDistressSimSecs() > simSecs) {
						/** He's still underway as of simSecs. */
						continue;
					}
					final LatLng3 latLng = latestStateVector.getLatLng();
					final long thisSimSecs = latestStateVector.getSimSecs();
					/**
					 * Constants are built into preparing timeWindow and extent.
					 */
					final long refSecsForEnvLookUp = _model.getRefSecs(thisSimSecs);
					final double nmiBuffer = 30d;
					final BoxDefinition boxDefinition = new BoxDefinition(_simCase, _model, refSecsForEnvLookUp, latLng,
							nmiBuffer);
					currentsUvGetter.incrementalPrepare(refSecsForEnvLookUp, latLng, boxDefinition);
					windsUvGetter.incrementalPrepare(refSecsForEnvLookUp, latLng, boxDefinition);
				}
			}
			currentsUvGetter.finishPrepare();
			windsUvGetter.finishPrepare();
		}
		/** Do the rest in parallel. */
		for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
			final ParticleSet particleSet = _particleSets[iScenario];
			try {
				particleSet.timeUpdate(simSecsS, timeIdx, _particlesFile);
			} catch (final Exception e) {
				final String stackTrace = StringUtilities.getStackTraceString(e);
				final String eString = e.toString();
				final String s = String.format("Failed on Time Update.\n" + "Message[%s]:  StackTrace:%s", eString,
						stackTrace);
				SimCaseManager.err(_simCase, s);
				MainRunner.HandleFatal(_simCase, new RuntimeException(e));
			}
		}
	}

	/**
	 * If, after _SufficientSample draws, our successRate is less than
	 * _MinimumSuccessRate, we automatically win.
	 */
	final private static int _SufficientSample = 1000;
	final private static double _MinimumSuccessRate = 0.01;

	private PreDistressModel.Itinerary[] createInitialCloud(final ParticleSet particleSet, final int iScenario,
			final int nParticlesPerScenario, final Randomx[] randoms) {
		final MyLogger logger = SimCaseManager.getLogger(_simCase);
		final Scenario scenario = particleSet._scenario;
		final ShorelineFinder shorelineFinder = _model.getShorelineFinder();
		final Area departureArea = scenario.getDepartureArea();
		final TimeDistribution departureTimeDistribution = scenario.getDepartureTimeDistribution();
		final long meanRefSecs = departureTimeDistribution.getMeanRefMins() * 60;
		final LatLng3 meanLatLng = departureArea.getFlatCenterOfMass();
		final Particle[] particles = particleSet._particles;
		final int[] sotOrdToCount = scenario.getSotOrdToCount();
		for (final SotWithWt distressSotWithWt : scenario.getDistressSotWithWts()) {
			final int sotId = distressSotWithWt.getSot().getId();
			final int sotOrd = _model.getSotOrd(sotId);
			final int count = sotOrdToCount[sotOrd];
			logger.out("Creating " + count + " particles for search object type (distress) " + sotId);
		}
		final SearchObjectType originatingSot = _model.getOriginatingSotWithWt().getSot();
		/**
		 * Record the number of particles per search object type in the ParticleSet.
		 */
		final List<SotWithWt> scenarioSotWithWtList = scenario.getDistressSotWithWts();
		for (final SotWithWt sotWithWt : scenarioSotWithWtList) {
			final SearchObjectType sot = sotWithWt.getSot();
			final int sotId = sot.getId();
			final int sotOrd = _model.getSotOrd(sotId);
			final int count = sotOrdToCount[sotOrd];
			particleSet.setSotCount(sotId, count);
		}
		final SotWithWt[] particleDistressSotWithWts = new SotWithWt[nParticlesPerScenario];
		int iParticle0 = 0;
		for (final SotWithWt sotWithWt : scenarioSotWithWtList) {
			final SearchObjectType sot = sotWithWt.getSot();
			final int sotId = sot.getId();
			final int sotOrd = _model.getSotOrd(sotId);
			final int count = sotOrdToCount[sotOrd];
			for (int k = 0; k < count; ++k) {
				particleDistressSotWithWts[iParticle0++] = sotWithWt;
			}
		}
		/**
		 * Create the initial mean particle for each distress type in this scenario.
		 */
		for (final SotWithWt sotWithWt : scenarioSotWithWtList) {
			final SearchObjectType sot = sotWithWt.getSot();
			final int sotId = sot.getId();
			if (sotId >= 0) {
				particleSet.setInitialMeanPosition(this, originatingSot, sot, meanRefSecs, meanLatLng);
			}
		}

		final PreDistressModel preDistressModel = scenario.getPreDistressModel();
		if (preDistressModel == null || _model.getReverseDrift()) {
			setImmediateDistress(randoms, particleSet, particleDistressSotWithWts);
			return null;
		}
		final PreDistressModel.Itinerary[] itineraries = new PreDistressModel.Itinerary[nParticlesPerScenario];
		/**
		 * We start underway; one cannot have a reverser in this case. Hence, there is
		 * no difference between SimSecs and RefSecs.
		 */
		final int departurePlusMinusMins = departureTimeDistribution.getPlusMinusMins();
		final float timeStdDev = departurePlusMinusMins / 2f;
		final int meanRefMins = departureTimeDistribution.getMeanRefMins();
		final PreDistressModel.ItineraryBuilder itineraryBuilder = preDistressModel.getItineraryBuilder(_simCase,
				departureArea);
		double nTriesR = 0d;
		double nSuccessesR = 0d;
		LatLng3 startLatLng = null;
		/** Some constants for underway. */
		final boolean updateParticleTail = true;
		final StateVectorType underwaySvt = StateVectorType.UNDERWAY;
		final boolean excludeInitialLandDraws = _model.getExcludeInitialLandDraws();
		final boolean excludeInitialWaterDraws;
		if (excludeInitialLandDraws) {
			excludeInitialWaterDraws = false;
		} else {
			excludeInitialWaterDraws = _model.getExcludeInitialWaterDraws();
		}
		/** Compute minArrival Time. */
		final long minArrivalRefSecs = preDistressModel.getMinArrivalRefSecs(_simCase);
		preDistressModel.reactToMinArrivalRefSecs(minArrivalRefSecs);

		/** Generate the real itineraries. */
		final long[] refSecsS = _model.computeRefSecsS();
		for (int iParticle = 0; iParticle < nParticlesPerScenario; ++iParticle) {
			final ParticleIndexes prtclIndxs = ParticleIndexes.getStandardOne(_model, iScenario, iParticle);
			final Randomx r = randoms[iParticle];
			long birthRefSecs = -1;
			long distressRefSecs = -1;
			PreDistressModel.Itinerary itinerary = null;
			while (true) {
				final double truncatedNormal = r.getTruncatedGaussian(2d);
				final double refMins = truncatedNormal * timeStdDev + meanRefMins;
				birthRefSecs = Math.round(refMins * 60d);
				itinerary = null;
				nTriesR += 1d;
				try {
					itinerary = itineraryBuilder.buildItinerary(_model, r, birthRefSecs, prtclIndxs, refSecsS);
				} catch (final Cdf.BadCdfException e) {
				}
				startLatLng = itinerary.getStartLatLng();
				if (scenario.getNoDistress()) {
					distressRefSecs = _RefSecsForNoDistress;
					nSuccessesR += 1d;
					break;
				}
				distressRefSecs = itinerary.getDistressRefSecs();
				if (preDistressModel.getRefSecsIsInDistressRange(distressRefSecs)) {
					if (!excludeInitialLandDraws && !excludeInitialWaterDraws) {
						nSuccessesR += 1d;
						break;
					}
					final LatLng3 distressLatLng = itinerary.getDistressLatLng();
					final LevelFinderResult lfr = shorelineFinder.getLevelFinderResult(logger, distressLatLng);
					final boolean onBorder = lfr._containmentValueForNorthCrossing == Loop3.ContainmentValue.BORDER;
					final int level = lfr.getLevel();
					final boolean isOnLand = level % 2 == 1;
					if (excludeInitialLandDraws && (!onBorder && !isOnLand)) {
						nSuccessesR += 1d;
						break;
					}
					if (excludeInitialWaterDraws && (!onBorder && isOnLand)) {
						nSuccessesR += 1d;
						break;
					}
				}
				/**
				 * We don't win on our own merit. If we have a sufficient sample of draws, and a
				 * very low success rate, then we use this one but do not update nSucessesR.
				 */
				if (nTriesR >= _SufficientSample) {
					final double successRate = nSuccessesR / nTriesR;
					if (successRate < _MinimumSuccessRate) {
						break;
					}
				}
			}
			final UnderwayParticle particle = new UnderwayParticle(this, scenario, prtclIndxs, birthRefSecs,
					originatingSot, particleDistressSotWithWts[iParticle].getSot(), distressRefSecs, itinerary, r);
			itineraries[iParticle] = itinerary;
			final long birthTimeSimSecs = _model.getSimSecs(birthRefSecs);
			/**
			 * The following stores the new state vector in the particle.
			 */
			@SuppressWarnings("unused")
			final UnderwayStateVector underwayStateVector = new UnderwayStateVector(particle, birthTimeSimSecs,
					startLatLng, underwaySvt, updateParticleTail);
			particles[iParticle] = particle;
		}
		return itineraries;
	}

	private void setImmediateDistress(final Randomx[] randoms, final ParticleSet particleSet,
			final SotWithWt[] sotWithWts) {
		/**
		 * We are already adrift. The random draws for time are N(0,sigma), where sigma
		 * = 1/3 of the given plus-or-minus. This time will also be our distress time.
		 */
		final MyLogger logger = SimCaseManager.getLogger(_simCase);
		final Scenario scenario = particleSet._scenario;
		final int iScenario = scenario.getIScenario();
		final Particle[] particles = particleSet._particles;
		final int nParticlesPerScenario = particles.length;
		final SearchObjectType originatingSot = _model.getOriginatingSotWithWt().getSot();
		final boolean excludeInitialLandDraws = _model.getExcludeInitialLandDraws();
		final boolean excludeInitialWaterDraws;
		if (excludeInitialLandDraws) {
			excludeInitialWaterDraws = false;
		} else {
			excludeInitialWaterDraws = _model.getExcludeInitialWaterDraws();
		}
		final TimeDistribution departureTimeDistribution = scenario.getDepartureTimeDistribution();
		final double timeStandardDeviationMins = departureTimeDistribution.getPlusMinusMins() / 3d;
		final double meanTimeRefMins = departureTimeDistribution.getMeanRefMins();
		final ShorelineFinder shorelineFinder = _model.getShorelineFinder();
		double nTriesR = 0d;
		double nSuccessesR = 0d;
		for (int iParticle = 0; iParticle < nParticlesPerScenario; ++iParticle) {
			final ParticleIndexes prtclIndxs = ParticleIndexes.getStandardOne(_model, iScenario, iParticle);
			final Randomx particleR = randoms[iParticle];
			final Randomx localR = new Randomx(particleR.nextLong(), /* nToAdvance= */7);
			final double randomDraw = localR.getTruncatedGaussian();
			final double refMins = randomDraw * timeStandardDeviationMins + meanTimeRefMins;
			final long refSecs = Math.round(refMins * 60d);
			final long simSecs = _model.getSimSecs(refSecs);
			final long birthSimSecs = simSecs;
			final long distressSimSecs = simSecs;
			final SearchObjectType distressSot = sotWithWts[iParticle].getSot();
			final Particle particle = new Particle(this, scenario, prtclIndxs, originatingSot, birthSimSecs,
					distressSot, distressSimSecs, particleR);
			LatLng3 drawnLatLng = null;
			boolean isOnLand = true;
			final int overallIdx = particle.getParticleIndexes().getOverallIndex();
			while (true) {
				drawnLatLng = scenario.getInitialLatLng(localR, overallIdx);
				final LevelFinderResult lfr = shorelineFinder.getLevelFinderResult(logger, drawnLatLng);
				final boolean onBorder = lfr._containmentValueForNorthCrossing == Loop3.ContainmentValue.BORDER;
				final int level = lfr.getLevel();
				isOnLand = level % 2 == 1;
				final boolean winOnMerit;
				if (excludeInitialLandDraws) {
					/**
					 * We ignore excludeInitialWaterDraws here; assume it's false.
					 */
					winOnMerit = !onBorder && !isOnLand;
				} else if (excludeInitialWaterDraws) {
					/** We know excludeInitialLandDraws is false here. */
					winOnMerit = !onBorder && isOnLand;
				} else {
					/** We're not excluding anything. */
					winOnMerit = true;
				}
				nTriesR += 1d;
				if (winOnMerit) {
					nSuccessesR += 1d;
					break;
				}
				/**
				 * We don't win on our own merit. If we have a sufficient sample of draws, and a
				 * very low success rate, then we use this one but do not update nSucessesR.
				 */
				if (nTriesR >= _SufficientSample) {
					final double successRate = nSuccessesR / nTriesR;
					if (successRate < _MinimumSuccessRate) {
						break;
					}
				}
			}
			/**
			 * The following has the side effect of storing the new state vector in the
			 * particle.
			 */
			final boolean updateParticleTail = true;
			final DistressStateVector adriftStateVector = new DistressStateVector(particle, simSecs, drawnLatLng,
					updateParticleTail);
			particle.setDistressLatLng(drawnLatLng);
			/** If on land then, since we are adrift, we are stuck. */
			if (isOnLand) {
				adriftStateVector.setIsStuckOnLand();
			}
			/** Do the anchoring. */
			if (!adriftStateVector.isStuckOnLand()) {
				final boolean isAnchored = distressSot.anchors(drawnLatLng, localR.nextDouble(), _model.getEtopo());
				if (isAnchored) {
					adriftStateVector.setIsAnchored();
					particle.setAnchoringSimSecs(distressSimSecs);
				}
			}
			particles[iParticle] = particle;
		}
	}

	/**
	 * The ctor computes the means, variances, and rho, all in earth radii.
	 */
	private class Summary {
		final public TangentCylinder _tangentCylinder;
		final public double _meanEastOffset, _meanNorthOffset, _varianceEast, _varianceNorth;
		final public double _minEastOffset, _maxEastOffset, _minNorthOffset, _maxNorthOffset;
		final public LatLng3 _northWest, _southEast, _meanLatLng;
		final public double _rho;
		final private float _sumOfWeights;
		final public float _sumOfPriors;
		final public double[] _containments;
		final public double[] _50smiMjrSmiMnrSmiMjrHdg;

		private Summary(final int iScenario, final long refSecs, final double[] containmentRequests) {
			final ArrayList<double[]> latLngWeights = new ArrayList<>();
			final int nParticlesPerScenario = _model.getNParticlesPerScenario();
			/**
			 * Compute _sumOfWeights, _sumOfPriors, and set tangentCylinder.
			 */
			final MyLogger logger = SimCaseManager.getLogger(Tracker.this._simCase);
			float sumOfWeights = 0f;
			float sumOfPriors = 0f;
			ParticleIndexes soleSurvivorPrtclIndxs = null;
			for (int iParticle = 0; iParticle < nParticlesPerScenario; ++iParticle) {
				final ParticleIndexes prtclIndxs = ParticleIndexes.getStandardOne(_model, iScenario, iParticle);
				final float prior = _particlesFile.getProbability(refSecs, prtclIndxs);
				sumOfPriors += prior;
				final float pFail = _particlesFile.getCumPFail(refSecs, prtclIndxs);
				final float weight = prior * pFail;
				if (weight > 0f) {
					if (latLngWeights.isEmpty()) {
						soleSurvivorPrtclIndxs = prtclIndxs;
					} else {
						soleSurvivorPrtclIndxs = null;
					}
					sumOfWeights += weight;
					final LatLng3 latLng = _particlesFile.getLatLng(refSecs, prtclIndxs);
					latLngWeights.add(new double[] {
							latLng.getLat(), latLng.getLng(), weight
					});
				}
			}
			_sumOfWeights = sumOfWeights;
			_sumOfPriors = sumOfPriors;
			final int numberOfLatLngWeights = latLngWeights.size();
			if (numberOfLatLngWeights < 2 && nParticlesPerScenario > 2) {
				String s = "Probability destroyed by Sorties.";
				if (soleSurvivorPrtclIndxs != null) {
					s += String.format(" Only surviving particle: %s", soleSurvivorPrtclIndxs.getString());
				}
				SimCaseManager.err(_simCase, s);
				if (numberOfLatLngWeights == 0) {
					_tangentCylinder = null;
					_meanEastOffset = _meanNorthOffset = Double.NaN;
					_varianceEast = _varianceNorth = Double.NaN;
					_minEastOffset = _maxEastOffset = _minNorthOffset = _maxNorthOffset = Double.NaN;
					_northWest = _southEast = _meanLatLng = null;
					_rho = Float.NaN;
					_50smiMjrSmiMnrSmiMjrHdg = null;
					_containments = null;
					return;
				}
			}
			final LatLng3[] latLngs = new LatLng3[numberOfLatLngWeights];
			final double[] normalizedWeights = new double[numberOfLatLngWeights];
			for (int i = 0; i < latLngWeights.size(); ++i) {
				final double[] latLngWeight = latLngWeights.get(i);
				latLngs[i] = LatLng3.getLatLngB(latLngWeight[0], latLngWeight[1]);
				normalizedWeights[i] = latLngWeight[2] / _sumOfWeights;
			}
			_tangentCylinder = TangentCylinder.getTangentCylinder(latLngs, normalizedWeights);
			/**
			 * Convert to FlatLatLngs, find the extremes, and get a BivariateNormal.
			 */
			double minEastOffset = Float.POSITIVE_INFINITY;
			double maxEastOffset = Float.NEGATIVE_INFINITY;
			double minNorthOffset = Float.POSITIVE_INFINITY;
			double maxNorthOffset = Float.NEGATIVE_INFINITY;
			final double[] eastOffsets = new double[numberOfLatLngWeights];
			final double[] northOffsets = new double[numberOfLatLngWeights];
			for (int i = 0; i < numberOfLatLngWeights; ++i) {
				latLngs[i] = _tangentCylinder.convertToMyFlatLatLng(latLngs[i]);
				final TangentCylinder.FlatLatLng flatLatLng = (TangentCylinder.FlatLatLng) latLngs[i];
				final double eastOffset = flatLatLng.getEastOffset();
				final double northOffset = flatLatLng.getNorthOffset();
				minEastOffset = Math.min(minEastOffset, eastOffset);
				maxEastOffset = Math.max(maxEastOffset, eastOffset);
				minNorthOffset = Math.min(minNorthOffset, northOffset);
				maxNorthOffset = Math.max(maxNorthOffset, northOffset);
				eastOffsets[i] = eastOffset;
				northOffsets[i] = northOffset;
			}
			_minNorthOffset = minNorthOffset;
			_maxNorthOffset = maxNorthOffset;
			_minEastOffset = minEastOffset;
			_maxEastOffset = maxEastOffset;
			_northWest = _tangentCylinder.new FlatLatLng(_minEastOffset, _maxNorthOffset);
			_southEast = _tangentCylinder.new FlatLatLng(_maxEastOffset, _minNorthOffset);
			/** Compute the containment statistics. */
			final int numberOfContainments = containmentRequests == null ? 0 : containmentRequests.length;
			_containments = new double[numberOfContainments];
			boolean haveDifferentEastOffsets = false;
			for (int k = 1; k < numberOfLatLngWeights; ++k) {
				if (eastOffsets[k] != eastOffsets[0]) {
					haveDifferentEastOffsets = true;
					break;
				}
			}
			boolean haveDifferentNorthOffsets = false;
			for (int k = 1; k < numberOfLatLngWeights; ++k) {
				if (northOffsets[k] != northOffsets[0]) {
					haveDifferentNorthOffsets = true;
					break;
				}
			}
			if (!haveDifferentEastOffsets || !haveDifferentNorthOffsets) {
				_meanEastOffset = eastOffsets[0];
				_meanNorthOffset = northOffsets[0];
				_meanLatLng = _tangentCylinder.new FlatLatLng(_meanEastOffset, _meanNorthOffset);
				_varianceEast = 0d;
				_varianceNorth = 0d;
				_rho = 0d;
				for (int k = 0; k < numberOfContainments; ++k) {
					_containments[k] = 1d;
				}
				_50smiMjrSmiMnrSmiMjrHdg = new double[] {
						0d, 0d, 0d
				};
			} else {
				final BivariateNormalCdf bivariateNormalCdf = BivariateNormalCdf.getBivariateNormalCdf(logger,
						eastOffsets, northOffsets, normalizedWeights);
				_meanEastOffset = bivariateNormalCdf.getMeanX();
				_meanNorthOffset = bivariateNormalCdf.getMeanY();
				_meanLatLng = _tangentCylinder.new FlatLatLng(_meanEastOffset, _meanNorthOffset);
				final double sigmaX = bivariateNormalCdf.getSigmaX();
				final double sigmaY = bivariateNormalCdf.getSigmaY();
				final double sigmaXY = bivariateNormalCdf.getSigmaXY();
				_varianceEast = sigmaX * sigmaX;
				_varianceNorth = sigmaY * sigmaY;
				_rho = sigmaXY / (sigmaX * sigmaY);
				/** Compute the containment statistics. */
				final BivariateNormalCdf[] bivariateNormalCdfs = new BivariateNormalCdf[numberOfContainments];
				for (int containmentIndex = 0; containmentIndex < numberOfContainments; ++containmentIndex) {
					final double numberOfStandardDeviations = containmentRequests[containmentIndex];
					final double varianceEast = numberOfStandardDeviations * numberOfStandardDeviations * _varianceEast;
					final double varianceNorth = numberOfStandardDeviations * numberOfStandardDeviations
							* _varianceNorth;
					final double sigmaEastNorth = _rho * Math.sqrt(varianceEast * varianceNorth);
					bivariateNormalCdfs[containmentIndex] = new BivariateNormalCdf(logger, _meanEastOffset,
							_meanNorthOffset, varianceEast, varianceNorth, sigmaEastNorth, /* warnOnDegenerate= */true);
				}
				for (int i = 0; i < numberOfLatLngWeights; ++i) {
					final double weight = normalizedWeights[i];
					final TangentCylinder.FlatLatLng flatLatLng = (TangentCylinder.FlatLatLng) latLngs[i];
					final double eastOffset = flatLatLng.getEastOffset();
					final double northOffset = flatLatLng.getNorthOffset();
					for (int containmentIndex = 0; containmentIndex < numberOfContainments; ++containmentIndex) {
						if (bivariateNormalCdfs[containmentIndex].isIn(eastOffset, northOffset)) {
							_containments[containmentIndex] += weight;
						}
					}
				}
				/**
				 * Finally for display purposes, we need to compute and report sigmaA, sigmaB,
				 * and the angle.
				 */
				_50smiMjrSmiMnrSmiMjrHdg = bivariateNormalCdf.get50SmiMjrSmiMnrSmiMjrHdg();
			}
		}

		public double getMinLat() {
			return _southEast.getLat();
		}

		public double getMaxLat() {
			return _northWest.getLat();
		}

		public double getMinLng() {
			return _northWest.getLng();
		}

		public double getMaxLng() {
			return _southEast.getLng();
		}

		public double getRho() {
			return _rho;
		}

		public double getStdDevEastNmi() {
			return Math.sqrt(_varianceEast) / _NmiToR;
		}

		public double getStdDevNorthNmi() {
			return Math.sqrt(_varianceNorth) / _NmiToR;
		}

		public double getMeanLat() {
			return _meanLatLng.getLat();
		}

		public double getMeanLng() {
			return _meanLatLng.getLng();
		}

		public double get50SmiMjrNmi() {
			return _50smiMjrSmiMnrSmiMjrHdg[0];
		}

		public double get50SmiMnrNmi() {
			return _50smiMjrSmiMnrSmiMjrHdg[1];
		}

		public double getSmiMjrHdg() {
			return _50smiMjrSmiMnrSmiMjrHdg[2];
		}

		public LatLng3 getMeanLatLng() {
			return _meanLatLng;
		}
	}

	private void dumpTimeStepToLogFile(final long simSecs, final boolean isLastRefSecs) {
		final SimGlobalStrings simGlobalStrings = _simCase.getSimGlobalStrings();
		final MyLogger logger = SimCaseManager.getLogger(_simCase);
		final int debugLevel = simGlobalStrings.getDebugLevel();
		final long refSecs = _model.getRefSecs(simSecs);
		final MapForTimeStep mapForTimeStep = new MapForTimeStep(_simCase, refSecs, isLastRefSecs, _model,
				_particleSets, _particlesFile);
		final int nScenarii = _particleSets.length;
		final Element element1 = _logFormatter1.newChild(_logElement1, "TIME");
		final Element element2 = _logFormatter2.newChild(_logElement2, "TIME");
		/** Get the Global Values. */
		final MapForTimeStep.Value globalValue = mapForTimeStep.getGlobalValue();
		final double globalSumOfWeightTimesPos = globalValue._sumOfWeightTimesPos;
		final double globalSumOfLandedWeights = globalValue._sumOfLandedWeights;
		final double globalSumOfWeights = globalValue._sumOfAllWeights;
		final double globalSumOfLandedWeightTimesPos = globalValue._landedSumOfWeightTimesPos;
		final double globalCountOfAll = globalValue._countOfAll;
		final double globalDistressSumOfPrior = globalValue._distressSumOfPrior;
		final double globalSumOfDistressWeightTimesPFail = globalValue._distressSumOfWeightTimesPFail;
		final double globalSumOfWeightTimesPFail = globalValue._sumOfWeightTimesPFail;
		/** Start processing. */
		for (int k = 0; k < 2; ++k) {
			final boolean doingStashCopy = k == 1;
			final Element element = doingStashCopy ? element2 : element1;
			element.setAttribute("dtg", TimeUtilities.formatTime(refSecs, false));
			if (doingStashCopy) {
				for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
					final ParticleSet particleSet = _particleSets[iScenario];
					final Scenario scenario = particleSet._scenario;
					final int scenarioId = scenario.getId();
					final Summary summary = new Summary(iScenario, refSecs, _model.getContainmentRequests());
					final Element particleSetElement = _logFormatter2.newChild(element2, "STASH_SCENARIO");
					particleSetElement.setAttribute("ID", LsFormatter.StandardFormat(scenarioId));
					final boolean emptyScenarioForSimSecs = summary.getMeanLatLng() == null;
					if (emptyScenarioForSimSecs || globalSumOfWeightTimesPos <= 0d) {
						_logFormatter2.newText(particleSetElement, "Empty Scenario");
					} else {
						final MapForTimeStep.Value scenarioValue = mapForTimeStep.getValueFor(scenario);
						final double probability = LsFormatter
								.zeroOutBad(scenarioValue._sumOfWeightTimesPFail / globalSumOfWeightTimesPFail);
						particleSetElement.setAttribute("probability", LsFormatter.StandardFormat(probability));
						particleSetElement.setAttribute("meanLat",
								LsFormatter.StandardFormatForLatOrLng(summary.getMeanLat()));
						particleSetElement.setAttribute("meanLng",
								LsFormatter.StandardFormatForLatOrLng(summary.getMeanLng()));
						particleSetElement.setAttribute("minLat",
								LsFormatter.StandardFormatForLatOrLng(summary.getMinLat()));
						particleSetElement.setAttribute("minLng",
								LsFormatter.StandardFormatForLatOrLng(summary.getMinLng()));
						particleSetElement.setAttribute("maxLat",
								LsFormatter.StandardFormatForLatOrLng(summary.getMaxLat()));
						particleSetElement.setAttribute("maxLng",
								LsFormatter.StandardFormatForLatOrLng(summary.getMaxLng()));
						particleSetElement.setAttribute("sumOfWeights",
								LsFormatter.StandardFormat(summary._sumOfWeights));
						particleSetElement.setAttribute("sumOfPriors",
								LsFormatter.StandardFormat(summary._sumOfPriors));
						particleSetElement.setAttribute("sigmaLatInNmi",
								LsFormatter.StandardFormat(summary.getStdDevNorthNmi()));
						particleSetElement.setAttribute("sigmaLngInNmi",
								LsFormatter.StandardFormat(summary.getStdDevEastNmi()));
						particleSetElement.setAttribute("rho", LsFormatter.StandardFormat(summary.getRho()));
						final double[] mjrMnrSigma = BivariateNormalCdf.containmentRadiiToStandardDeviations(0.5,
								summary.get50SmiMjrNmi(), summary.get50SmiMnrNmi());
						particleSetElement.setAttribute("sigmaAInNmi", LsFormatter.StandardFormat(mjrMnrSigma[0]));
						particleSetElement.setAttribute("sigmaBInNmi", LsFormatter.StandardFormat(mjrMnrSigma[1]));
						particleSetElement.setAttribute("angleCwFromNorth",
								LsFormatter.StandardFormat(summary.getSmiMjrHdg()));
						final double[] containmentRequests = _model.getContainmentRequests();
						final int numberOfContainments = containmentRequests == null ? 0 : containmentRequests.length;
						for (int containmentIndex = 0; containmentIndex < numberOfContainments; ++containmentIndex) {
							final double containmentRadius = containmentRequests[containmentIndex];
							final String containmentRadiusString = LsFormatter.StandardFormat(containmentRadius);
							final String attributeName = "containment" + containmentRadiusString;
							final double theoreticalContainment = 1d
									- Math.exp(-0.5 * containmentRadius * containmentRadius);
							final String theoreticalContainmentString = LsFormatter
									.StandardFormat(theoreticalContainment);
							particleSetElement.setAttribute(attributeName + "-Theoretical",
									theoreticalContainmentString);
							particleSetElement.setAttribute(attributeName,
									LsFormatter.StandardFormat(summary._containments[containmentIndex]));
						}
					}
				}
				if (isLastRefSecs && doingStashCopy && debugLevel > 0) {
					/** Do the individual POS values. */
					final List<Sortie> allSorties = _model.getSorties();
					final Element particlesElement = _logFormatter2.newChild(element2, "Particles");
					for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
						final ParticleSet particleSet = _particleSets[iScenario];
						final Particle[] particles = particleSet._particles;
						final int nParticles = particles.length;
						double cumPos = 0;
						double cumPrior = 0;
						for (int iParticle = 0; iParticle < nParticles; ++iParticle) {
							final Element particleElement = _logFormatter2.newChild(particlesElement, "Particle");
							final String thisAttribute = String.format("%02d-%02d", iScenario, iParticle);
							particleElement.setAttribute("Scen-Prtcl.", thisAttribute);
							final ParticleIndexes prtclIndxs = ParticleIndexes.getStandardOne(_model, iScenario,
									iParticle);
							final double origPrior = _particlesFile.getInitPrior(prtclIndxs);
							double totalPFail = 1d;
							for (final Sortie sortie : allSorties) {
								final double pFail = ComputePFail.computeFtPFail(_simCase, _particlesFile, prtclIndxs,
										sortie, _TrackerForOptnOnly, _TrackerPFailType,
										/* updateParticlesFile= */false);
								totalPFail *= pFail;
								final String sruString = "Sru-" + sortie.getId();
								final String pFailString = LsFormatter.StandardFormat(pFail);
								particleElement.setAttribute(sruString, pFailString);
							}
							final double thisPos = 1 - totalPFail;
							cumPos += origPrior * thisPos;
							cumPrior += origPrior;
							final String posString = LsFormatter.StandardFormat(cumPos);
							particleElement.setAttribute("cumPos", posString);
							final String cumPriorString = LsFormatter.StandardFormat(cumPrior);
							particleElement.setAttribute("cumPrior", cumPriorString);
						}
					}
				}
			}
			/** Global */
			/** Right now, these go in the element above the posElement. */
			final double landedParticlesNoSearch = LsFormatter
					.zeroOutBad(globalSumOfLandedWeights / globalSumOfWeights);
			element.setAttribute("landedParticlesNoSearch", LsFormatter.StandardFormat(landedParticlesNoSearch));
			final double landedParticlesWithSearch = LsFormatter
					.zeroOutBad(globalSumOfLandedWeightTimesPos / globalSumOfWeightTimesPos);
			element.setAttribute("landedParticlesWithSearch", LsFormatter.StandardFormat(landedParticlesWithSearch));
			element.setAttribute("countOfAll", "" + globalCountOfAll);
			final double distressParticlesNoSearch = LsFormatter
					.zeroOutBad(globalDistressSumOfPrior / globalSumOfWeights);
			element.setAttribute("distressParticlesNoSearch", LsFormatter.StandardFormat(distressParticlesNoSearch));
			final double distressParticlesWithSearch = LsFormatter
					.zeroOutBad(globalSumOfDistressWeightTimesPFail / globalSumOfWeightTimesPFail);
			element.setAttribute("distressParticlesWithSearch",
					LsFormatter.StandardFormat(distressParticlesWithSearch));
			/**
			 * POS goes in its own element, which also has sub-elements for each object
			 * type.
			 */
			final Element posElement;
			if (!doingStashCopy) {
				posElement = _logFormatter1.newChild(element, "POS");
			} else {
				posElement = _logFormatter2.newChild(element, "POS");
			}
			final double totalPos = LsFormatter.zeroOutBad(globalSumOfWeightTimesPos / globalSumOfWeights);
			posElement.setAttribute("totalPOS", LsFormatter.StandardFormat(totalPos));
			if (isLastRefSecs && debugLevel > 0) {
				final String numString = LsFormatter.StandardFormat(globalSumOfWeightTimesPos);
				final String denString = LsFormatter.StandardFormat(globalSumOfWeights);
				logger.out(String.format("numString[%s] denString[%s]", numString, denString));
			}
			/** Add the Global ancillary information. */
			final String globalString = MapForTimeStep.getGlobalString();
			if (isLastRefSecs && doingStashCopy) {
				final boolean buildElement = true;
				StringUtilities.fillInAncillaryElement(globalValue, _logFormatter2, posElement, buildElement,
						globalString, null, null);
			}
			/** Search Object Types; collect them all first. */
			final ArrayList<SearchObjectType> sots = mapForTimeStep.getSots();
			for (final SearchObjectType sot : sots) {
				final MapForTimeStep.Value sotValue = mapForTimeStep.getValueFor(sot);
				final double sotSumOfAllWeights = sotValue._sumOfAllWeights;
				final double sotSumOfAllWeightTimesPos = sotValue._sumOfWeightTimesPos;
				final double sotCountOfLanded = sotValue._countOfLanded;
				final double sotCountOfDistress = sotValue._countOfDistress;
				final double sotCountOfUnderway = sotValue._countOfUnderway;
				final Element sotElement;
				if (!doingStashCopy) {
					sotElement = _logFormatter1.newChild(posElement, "SEARCH_OBJECT_TYPE");
				} else {
					sotElement = _logFormatter2.newChild(posElement, "SEARCH_OBJECT_TYPE");
				}
				sotElement.setAttribute("id", LsFormatter.StandardFormat(sot.getId()));
				final double sotRemainingProbability = LsFormatter
						.zeroOutBad((sotSumOfAllWeights - sotSumOfAllWeightTimesPos) / globalSumOfWeights);
				sotElement.setAttribute("remainingTargetProbability",
						LsFormatter.StandardFormat(sotRemainingProbability));
				/** Landed (which implies distress). */
				/**
				 * The count of particles that are landed and in this scenario.
				 */
				final double nDistressAndLanded = sotCountOfLanded;
				sotElement.setAttribute("numberInDistressAndLanded",
						LsFormatter.StandardFormat((int) nDistressAndLanded));
				final double nDistressAndNotLanded = sotCountOfDistress - sotCountOfLanded;
				sotElement.setAttribute("numberInDistressAndNotLanded",
						LsFormatter.StandardFormat((int) nDistressAndNotLanded));
				if (sot.getId() < 0) {
					final double nUnderway = sotCountOfUnderway;
					sotElement.setAttribute("numberUnderway", LsFormatter.StandardFormat((int) nUnderway));
				}
				/** POS, given the truth is this objectType. */
				final double conditionalPos = LsFormatter.zeroOutBad(sotSumOfAllWeightTimesPos / sotSumOfAllWeights);
				sotElement.setAttribute("conditionalPOS", LsFormatter.StandardFormat(conditionalPos));
				final double initialProbability = LsFormatter.zeroOutBad(sotSumOfAllWeights / globalSumOfWeights);
				sotElement.setAttribute("initialProbability", LsFormatter.StandardFormat(initialProbability));
				/** Probability of it being objectType and finding it. */
				final double jointPos = sotSumOfAllWeightTimesPos / globalSumOfWeights;
				sotElement.setAttribute("jointPOS", LsFormatter.StandardFormat(jointPos));
				/**
				 * The following is redundant with reaminingTargetProbability.
				 */
				sotElement.setAttribute("remainingProbability", LsFormatter.StandardFormat(sotRemainingProbability));
				/** Add the Search Object ancillary information. */
				final String sotString = MapForTimeStep.getStringFor(sot);
				if (isLastRefSecs && doingStashCopy) {
					final boolean buildElement = true;
					StringUtilities.fillInAncillaryElement(sotValue, _logFormatter2, sotElement, buildElement,
							sotString, null, null);
				}
				/** Geography. */
				final Element geographyElement;
				if (!doingStashCopy) {
					geographyElement = _logFormatter1.newChild(sotElement, "Geography");
				} else {
					geographyElement = _logFormatter2.newChild(sotElement, "Geography");
				}
				final Extent sotExtent = sotValue._extent;
				final double minLat = sotExtent.getMinLat(), maxLat = sotExtent.getMaxLat();
				final double leftLng = sotExtent.getLeftLng();
				final double rightLng = sotExtent.getRightLng();
				final double cntrLng = sotExtent.getMidLng();
				geographyElement.setAttribute("minLat", StringUtilities.toDegreesMinutesSeconds(minLat));
				geographyElement.setAttribute("maxLat", StringUtilities.toDegreesMinutesSeconds(maxLat));
				geographyElement.setAttribute("cenLat",
						StringUtilities.toDegreesMinutesSeconds((minLat + maxLat) / 2d));
				geographyElement.setAttribute("minLng", StringUtilities.toDegreesMinutesSeconds(leftLng));
				geographyElement.setAttribute("maxLng", StringUtilities.toDegreesMinutesSeconds(rightLng));
				final String cenLngString = StringUtilities.toDegreesMinutesSeconds(cntrLng);
				geographyElement.setAttribute("cenLng", cenLngString);
			}
			/** Scenarios */
			for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
				final ParticleSet particleSet = _particleSets[iScenario];
				final Scenario sc = particleSet._scenario;
				final int scId = sc.getId();
				final MapForTimeStep.Value scValue = mapForTimeStep.getValueFor(sc);
				final double scCountOfLanded = scValue._countOfLanded;
				final double scSumOfLandedWeights = scValue._sumOfLandedWeights;
				final double scSumOfAllWeightTimesPFail = scValue._sumOfWeightTimesPFail;
				final double scSumOfLandedWeightTimesPFail = scValue._landedSumOfWeightTimesPFail;
				final double scCountOfDistress = scValue._countOfDistress;
				final double scDistressSumOfPrior = scValue._distressSumOfPrior;
				final double scSumOfDistressWeightTimesPFail = scValue._distressSumOfWeightTimesPFail;
				/**
				 * scenarios have their own elements that are at the same level as posElement.
				 */
				final Element scenarioElement;
				if (!doingStashCopy) {
					scenarioElement = _logFormatter1.newChild(element, "SCENARIO");
				} else {
					scenarioElement = _logFormatter2.newChild(element, "SCENARIO");
				}
				scenarioElement.setAttribute("ID", LsFormatter.StandardFormat(scId));
				/** Landed (which implies distress). */
				/**
				 * The count of particles that are landed and in this scenario.
				 */
				final double countOfLanded = scCountOfLanded;
				scenarioElement.setAttribute("landedCount", LsFormatter.StandardFormat(countOfLanded));
				/**
				 * No search, the probability of being landed and in this scenario, given that
				 * it is landed.
				 */
				final double scLandedParticlesNoSearch = LsFormatter
						.zeroOutBad(scSumOfLandedWeights / globalSumOfLandedWeights);
				scenarioElement.setAttribute("landedParticlesNoSearch",
						LsFormatter.StandardFormat(scLandedParticlesNoSearch));
				/**
				 * No search, the probability of being landed and in this scenario.
				 */
				scenarioElement.setAttribute("jointLandedParticlesNoSearch",
						LsFormatter.StandardFormat(scSumOfLandedWeights));
				/**
				 * With search, the probability of being landed, given this scenario is true.
				 */
				final double scLandedParticlesWithSearch = LsFormatter
						.zeroOutBad(scSumOfLandedWeightTimesPFail / scSumOfAllWeightTimesPFail);
				scenarioElement.setAttribute("landedParticlesWithSearch",
						LsFormatter.StandardFormat(scLandedParticlesWithSearch));
				/**
				 * With search, the probability of being landed and scenario.
				 */
				final double jointLandedParticlesWithSearch = scSumOfLandedWeightTimesPFail
						/ scSumOfAllWeightTimesPFail;
				scenarioElement.setAttribute("jointLandedParticlesWithSearch",
						LsFormatter.StandardFormat(jointLandedParticlesWithSearch));
				/** Distress */
				/**
				 * The count of particles that are landed and in this scenario.
				 */
				final double distressCount = scCountOfDistress;
				scenarioElement.setAttribute("distressCount", LsFormatter.StandardFormat(distressCount));
				/**
				 * No search, the probability of being in this scenario, given that it is in
				 * distress.
				 */
				final double scDistressParticlesNoSearch = LsFormatter
						.zeroOutBad(scDistressSumOfPrior / globalDistressSumOfPrior);
				scenarioElement.setAttribute("distressParticlesNoSearch",
						LsFormatter.StandardFormat(scDistressParticlesNoSearch));
				/**
				 * No search, the probability of being in this scenario and distress.
				 */
				scenarioElement.setAttribute("jointDistressParticlesNoSearch",
						LsFormatter.StandardFormat(scDistressSumOfPrior));
				/**
				 * With search, the probability of distress, given this scenario is true.
				 */
				final double scDistressParticlesWithSearch = LsFormatter
						.zeroOutBad(scSumOfDistressWeightTimesPFail / scSumOfAllWeightTimesPFail);
				scenarioElement.setAttribute("distressParticlesWithSearch",
						LsFormatter.StandardFormat(scDistressParticlesWithSearch));
				/**
				 * With search, the probability of distress and scenario is true.
				 */
				final double jointDistressParticlesWithSearch = LsFormatter
						.zeroOutBad(scSumOfDistressWeightTimesPFail / globalSumOfDistressWeightTimesPFail);
				scenarioElement.setAttribute("jointDistressParticlesWithSearch",
						LsFormatter.StandardFormat(jointDistressParticlesWithSearch));
				final String scenarioKeyString = MapForTimeStep.getStringFor(sc);
				if (isLastRefSecs && doingStashCopy) {
					final boolean buildElement = true;
					StringUtilities.fillInAncillaryElement(scValue, _logFormatter2, scenarioElement, buildElement,
							scenarioKeyString, null, null);
				}
				/** Geography. */
				final Element geographyElement;
				if (!doingStashCopy) {
					geographyElement = _logFormatter1.newChild(scenarioElement, "Geography");
				} else {
					geographyElement = _logFormatter2.newChild(scenarioElement, "Geography");
				}
				final Extent scenarioExtent = scValue._extent;
				final double minLat = scenarioExtent.getMinLat(), maxLat = scenarioExtent.getMaxLat();
				final double leftLng = scenarioExtent.getLeftLng();
				final double rightLng = scenarioExtent.getRightLng();
				final double midLng = scenarioExtent.getMidLng();
				geographyElement.setAttribute("minLat", StringUtilities.toDegreesMinutesSeconds(minLat));
				geographyElement.setAttribute("maxLat", StringUtilities.toDegreesMinutesSeconds(maxLat));
				geographyElement.setAttribute("cenLat",
						StringUtilities.toDegreesMinutesSeconds((minLat + maxLat) / 2d));
				geographyElement.setAttribute("minLng", StringUtilities.toDegreesMinutesSeconds(leftLng));
				geographyElement.setAttribute("maxLng", StringUtilities.toDegreesMinutesSeconds(rightLng));
				final String cenLngString = StringUtilities.toDegreesMinutesSeconds(midLng);
				geographyElement.setAttribute("cenLng", cenLngString);
			}
		}
	}

	public Model getModel() {
		return _model;
	}

	public ParticleSet getParticleSet(final int particleSetIndex) {
		return _particleSets[particleSetIndex];
	}

	public int getNumberOfParticleSets() {
		return _particleSets.length;
	}

	public ParticlesFile getParticlesFile() {
		return _particlesFile;
	}

	public static void runSimulator(final SimCaseManager.SimCase simCase, final String[] args) {
		final long entryTimeMs = System.currentTimeMillis();
		final SimCaseManager simCaseManager = simCase.getSimCaseManager();
		final String modelFilePathX = args.length > 0 ? args[0] : null;
		final File modelFile = new File(modelFilePathX);
		final String modelFilePath = modelFile.getPath();
		final File caseDirFile = modelFile.getParentFile();
		simCase._scratchTimeInMillis = System.currentTimeMillis();
		if (modelFilePath == null) {
			MainRunner.HandleFatal(simCase, new RuntimeException("Sim: No FilePath."));
			return;
		}
		final String stashedSimFilePath;
		stashedSimFilePath = ModelReader.stashEngineFile(simCase, modelFilePath, modelFilePath,
				SimCaseManager._SimEndingLc, "SimInput", /* overwrite= */false);
		/** Start working. */
		final Model model = ModelReader.readModel(simCase, caseDirFile, modelFilePath);
		model.setStashedSimFilePath(stashedSimFilePath);
		final long oldUsedMemory = SizeOf.usedMemory();

		if (!model.getDisplayOnly()) {
			final String particlesFilePath = model.getParticlesFilePath();
			final Tracker tracker = new Tracker(simCase, entryTimeMs, model);
			if (!tracker.getKeepGoing()) {
				tracker.freeMemory();
				return;
			}
			if (model.getBuildSortiesWorkbook()) {
				for (final Sortie sortie : model.getSorties()) {
					sortie.dumpToSortiesWorkbook();
				}
				model.dumpSortiesWorkbook(simCase);
			}
			tracker.doTimeSteps();
			if (!tracker.getKeepGoing()) {
				tracker.freeMemory();
				return;
			}
			final long fileModSecs = StaticUtilities.getTimeOfModificationMs(particlesFilePath) / 1000L;
			simCaseManager.addParticlesFile(simCase, particlesFilePath, tracker._particlesFile, fileModSecs);
			final long fileModRefSecs = TimeUtilities.convertToRefSecs(fileModSecs);
			final String fileModTimeString = TimeUtilities.formatTime(fileModRefSecs, true);
			SimCaseManager.out(simCase, String.format("File Mod Time[%s]", fileModTimeString));
			/** Write out the times file. */
			final String modelStashedSimFilePath = model.getStashedSimFilePath();
			final String modelStashedSimFileName = new File(modelStashedSimFilePath).getName();
			final int lastIndex = modelStashedSimFileName.lastIndexOf(SimCaseManager._SimEndingLc);
			final String stashedTimesFileName = modelStashedSimFileName.substring(0, lastIndex) + "-simTimes.txt";
			final String thisModelFilePath = model.getSimFilePath();
			final File stashResultDir = AbstractOutFilesManager.GetEngineFilesDir(simCase, thisModelFilePath,
					"SimResult");
			final int nForExcelDump = model.getNForExcelDump();
			final int totalNParticles = model.getTotalNParticles();
			if (totalNParticles <= nForExcelDump) {
				tracker.buildParticlesSpreadsheet();
			}
			final File timesFile = new File(stashResultDir, stashedTimesFileName);
			simCase.dumpCriticalTimes("Simulation", entryTimeMs, timesFile);
			simCase.runOutChunks();
			if (!SimCaseManager.retainParticlesFileMemory()) {
				tracker.freeMemory();
			}
		} else {
			/**
			 * The following ctor will create a Tracker and it will be the mainSaropsObject
			 * for a display-only.
			 */
			@SuppressWarnings("unused")
			final Tracker tracker = new Tracker(simCase, model);
		}
		final long newUsedMemory = SizeOf.usedMemory();
		SimCaseManager.out(simCase, "oldUsedMemory[" + oldUsedMemory + "] newUsedMemory[" + newUsedMemory
				+ "] difference[" + (newUsedMemory - oldUsedMemory) + "]");
		AbstractStudyRunner.RunStudy(simCase);
	}

	private void freeMemory() {
		_model.freeMemory();
		ParticleSet.freeMemory(_particleSets);
	}

	public boolean getKeepGoing() {
		return _simCase == null ? true : _simCase.getKeepGoing();
	}

	public boolean runStudy() {
		return _model != null && _model.getRunStudy();
	}
}
