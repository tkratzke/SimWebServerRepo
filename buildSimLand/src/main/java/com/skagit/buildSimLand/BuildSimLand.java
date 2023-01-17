package com.skagit.buildSimLand;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Stack;

import com.skagit.util.DirsTracker;
import com.skagit.util.SizeOf;
import com.skagit.util.StaticUtilities;
import com.skagit.util.StringUtilities;
import com.skagit.util.geometry.IntLoopData;
import com.skagit.util.geometry.gcaSequence.GcaSequence;
import com.skagit.util.geometry.gcaSequence.GcaSequenceStatics;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.shpFileUtils.ShpFileWriter;

/**
 * <pre>
 * 1. AMEND or startPhase.  If AMEND, then startPhase will be set to Phase4.
 * 2. Home Directory.
 * 3. If AMEND, or we want a comparison, OldHomeDir.
 * 4. If and only if PHASE1, gshhs file.
 * 5. If and only if oldHomeDir != null, then redBFile name.
 * 6. Can run multiple cases.  It's nice to end with: STOP
 * </pre>
 */

public class BuildSimLand {
	final static boolean _InitialPhasesWritePolylines = false;
	final static boolean _Phase5WritePolylines = false;
	final private static File _UserDir = DirsTracker.getUserDir();
	final static String _StableDirName = "Stable";
	final static File _StableDir = new File(_UserDir, _StableDirName);

	final public static String _DataDirName = "data";
	/** Phase 1 constants. */
	final static String _GshhsAdjsDirName = "Phase1GshhsAdjs";
	final static String _UsgsShpFilesName = "Phase1Usgs";
	final static String _NhdShpFilesName = "Phase1Region17_Area_Dissolve";
	final static String _UsgsAdjsDirName = "Phase1UsgsAdjs";
	final static String _NhdAdjsDirName = "Phase1NhdAdjs";
	final static String _Phase3RefinementsDirName = "Phase3Refinements";
	final static String _GshhsResultBFileName = "Phase1Gshhs.b";
	final static String _HydroShpFileName = "Hydro";
	/** Phase 2 constants. */
	final static String _Phase2ResultBFileName = "Phase2.b";
	/** Phase 3 constants. */
	final static String _Phase3ResultBFileName = "Phase3.b";
	/** Phase 4 constants. */
	final static String _Phase4ResultBFileName = "Phase4.b";
	final static String _Phase4AdjsDirName = "Phase4Adjs";
	/** Phase 5 constants. */
	final static String _Phase5ResultDirName = "/LandJarDir/com/skagit/util/gshhs/data";
	final static String _FullResolutionCoreName = "f";
	final static String _CoreResultName = "SimLand2.2";
	final static String _FullResolutionBinaryFileName = String.format("%s_%c.b", _CoreResultName,
			_FullResolutionCoreName.charAt(0));
	final static String _FullResolutionBinaryFileRelativeName = _Phase5ResultDirName + "/"
			+ _FullResolutionBinaryFileName;
	/** Phase 6 constants. */
	final static String _Phase6LandJarDirName = "LandJarDir";
	final static String _Phase6ResultFileStart;
	final static String _Phase6EtopoLandJarDirName;
	static {
		final String phase6EtopoLandJarDirName = StringUtilities.getSystemProperty("EtopoLandJarDir",
				/* useSpaceProxy= */false);
		if (phase6EtopoLandJarDirName == null || phase6EtopoLandJarDirName.length() == 0) {
			_Phase6EtopoLandJarDirName = null;
			_Phase6ResultFileStart = "Gshhs22";
		} else {
			_Phase6EtopoLandJarDirName = phase6EtopoLandJarDirName;
			_Phase6ResultFileStart = "GshhsAndEtopo22";
		}
	}

	/** General. */
	final static ShpFileWriter _ShpFileWriter = new ShpFileWriter();
	final static String _LogDirName = "BuildSimLandLog";

	public enum Phase {
		PHASE1, PHASE2, PHASE3, PHASE4, PHASE5, PHASE6
	}

	final boolean _amend;
	final Phase _startPhase;
	final File _oldHomeDir;
	final File _homeDir;
	final File _dataDir;
	final File _gshhsFile;
	final private Phase1Worker _phase1Worker;
	final private Phase2Worker _phase2Worker;
	final private Phase3Worker _phase3Worker;
	final private Phase4Worker _phase4Worker;
	final private Phase5Worker _phase5Worker;
	final private Phase6Worker _phase6Worker;
	final File _logDir;
	final PrintStream _timesStream;
	final MyLogger _logger;
	final File _redBFile;

	private BuildSimLand(final Phase startPhase, final boolean amend, final File oldHomeDir, final File homeDir,
			final File gshhsFile, final File redBFile) {
		_oldHomeDir = oldHomeDir;
		_homeDir = homeDir;
		_dataDir = new File(_homeDir, _DataDirName);
		_redBFile = redBFile;
		_startPhase = startPhase;
		_amend = amend;

		_logDir = new File(_homeDir, _LogDirName);
		StaticUtilities.makeDirectory(_logDir);
		final File f = new File(_logDir, "Times.txt");
		PrintStream timesStream = null;
		try {
			timesStream = new PrintStream(f);
		} catch (final FileNotFoundException e) {
		}
		_timesStream = timesStream;

		_logger = MyLogger.CreateMyLogger("Bsl", _logDir, /* dbgCoreName= */null, /* outCoreName= */"BslOut",
				/* wrnCoreName= */"BslWrn", /* errCoreName= */"BslErr", "txt", /* append= */false);
		_gshhsFile = gshhsFile;

		_phase1Worker = new Phase1Worker(this);
		_phase2Worker = new Phase2Worker(this);
		_phase3Worker = new Phase3Worker(this);
		_phase4Worker = new Phase4Worker(this);
		_phase5Worker = new Phase5Worker(this);
		_phase6Worker = new Phase6Worker(this);
	}

	boolean doThisPhase(final Phase phase) {
		return _startPhase.ordinal() <= phase.ordinal();
	}

	private static Phase stringToPhase(final String s) {
		if (s != null) {
			for (final Phase phase : Phase.values()) {
				if (phase.name().equals(s)) {
					return phase;
				}
			}
		}
		return null;
	}

	static File getStableCousin(final File f) {
		if (f == null) {
			return null;
		}
		final Stack<String> forebearNames = new Stack<>();
		final File stableParent = _StableDir.getAbsoluteFile().getParentFile();
		for (File ff = f; !ff.equals(stableParent); ff = ff.getParentFile()) {
			forebearNames.push(ff.getName());
		}
		forebearNames.pop();
		File ff = null;
		for (ff = _StableDir; !forebearNames.isEmpty(); ff = new File(ff, forebearNames.pop())) {
		}
		return ff;
	}

	private static void doTheBuild(final Phase startPhase, final boolean amend, final File homeDir,
			final File oldHomeDir, final File gshhsFile, final File redBFile) {

		final String doTheBuildString = String.format("\ndoTheBuild:" + //
				"\n\t%s%s" + //
				"\n\thomeDir[%s]" + //
				"\n\toldHomeDir[%s]" + //
				"\n\tgshhsFile[%s]" + //
				"\n\tredBFile[%s]\n\n", //
				startPhase.name(), amend ? "(AMEND)" : "", //
				homeDir == null ? "NULL" : homeDir.getPath(), //
				oldHomeDir == null ? "NULL" : oldHomeDir.getPath(), //
				gshhsFile == null ? "NULL" : gshhsFile.getPath(), //
				redBFile == null ? "NULL" : redBFile.getPath() //
		);
		MyLogger.out(null, doTheBuildString);
		if (amend) {
			final boolean oldHomeDirWorks = oldHomeDir != null && oldHomeDir.isDirectory();
			if (!oldHomeDirWorks) {
				return;
			}
			final boolean redBFileWorks = redBFile != null && redBFile.isFile();
			if (!redBFileWorks) {
				return;
			}
		}
		final BuildSimLand bsl = new BuildSimLand(startPhase, amend, oldHomeDir, homeDir, gshhsFile, redBFile);
		StaticUtilities.clearDirectoryWithFilter(bsl._logDir, /* filenameFilter= */null);
		final MyLogger bslLogger = bsl._logger;
		bslLogger.resetAppenders(bsl._logDir, /* dbgCoreName= */null, "Prelim.out", "Prelim.wrn", "Prelim.err", "txt",
				/* append= */true);
		bslLogger.out(doTheBuildString);
		switch (bsl._startPhase) {
		case PHASE2:
			bsl._phase2Worker.doPhase2();
			break;
		case PHASE3:
			bsl._phase3Worker.doPhase3();
			break;
		case PHASE4:
			bsl._phase4Worker.doPhase4();
			break;
		case PHASE5:
			bsl._phase5Worker.doPhase5();
			break;
		case PHASE6:
			bsl._phase6Worker.doPhase6();
			break;
		case PHASE1:
		default:
			bsl._phase1Worker.doPhase1();
			break;
		}
	}

	void LogTimeAndMemory(final String caption) {
		SizeOf.LogTimeAndMemory(_logger, _timesStream, caption);
	}

	File getPhase1ResultDir() {
		return _phase1Worker._resultDir;
	}

	File getPhase2ResultDir() {
		return _phase2Worker._resultDir;
	}

	File getPhase3ResultDir() {
		return _phase3Worker._resultDir;
	}

	File getPhase4ResultDir() {
		return _phase4Worker._resultDir;
	}

	File getPhase5FullResolutionBFile() {
		return _phase5Worker._fullResolutionBFile;
	}

	void doPhase2(final ArrayList<IntLoopData> gshhsIlds, final ArrayList<IntLoopData> hydroIlds) {
		_phase2Worker.doPhase2(gshhsIlds, hydroIlds);
	}

	void doPhase3(final ArrayList<IntLoopData> phase2Ilds) {
		_phase3Worker.doPhase3(phase2Ilds);
	}

	void doPhase4(final ArrayList<IntLoopData> phase3Ilds) {
		_phase4Worker.doPhase4(phase3Ilds);
	}

	void doPhase5(final ArrayList<IntLoopData> phase4Ilds, final boolean doSimpleCopy) {
		_phase5Worker.doPhase5(phase4Ilds, doSimpleCopy);
	}

	void doPhase6() {
		_phase6Worker.doPhase6();
	}

	public static void main(final String[] args) {
		final int nArgs = args.length;
		int iArg = 0;
		while (iArg < nArgs) {
			/** Amend or startPhase. */
			MyLogger.out(null, String.format("\niArg[%d] string0:%s", iArg, args[iArg]));
			final String string0 = args[iArg++];
			if ("^".equals(string0)) {
				continue;
			}
			if ("stop".equalsIgnoreCase(string0)) {
				break;
			}
			final boolean amend = string0.toLowerCase().equals("amend");
			final Phase startPhase;
			if (amend) {
				startPhase = Phase.PHASE4;
			} else {
				startPhase = stringToPhase(string0);
				if (startPhase == null) {
					break;
				}
			}
			final String startPhaseString = String.format("\nstartPhase:%s", startPhase.name());
			MyLogger.out(null, startPhaseString + (amend ? "(amend)" : "."));

			/** Get homeDir and its data directory. */
			MyLogger.out(null, String.format("\niArg[%d] homeDir:[%s]", iArg, args[iArg]));
			final File homeDir = new File(_UserDir, args[iArg++]);
			final File dataDir = new File(homeDir, _DataDirName);

			/** Get oldHomeDir for amend and/or comparing. */
			final File oldHomeDir;
			if (iArg < nArgs) {
				/** We do not advance iArg when looking for oldHomeDirX. */
				final File oldHomeDirX = new File(_UserDir, args[iArg]);
				if (oldHomeDirX.isDirectory() && oldHomeDirX.canRead()) {
					oldHomeDir = oldHomeDirX;
					MyLogger.out(null, String.format("\niArg[%d] oldHomeDir[%s]", iArg, oldHomeDir));
					/** We have an oldHomeDir. NOW we advance iArg. */
					++iArg;
				} else {
					oldHomeDir = null;
				}
			} else {
				oldHomeDir = null;
			}

			/** If amending, we require oldHomeDir. */
			if (oldHomeDir == null && amend) {
				System.exit(0);
			}

			/** Get gshhsFile if and only if Phase1. */
			final File gshhsFile;
			if (startPhase == Phase.PHASE1) {
				final File gshhsFile0 = new File(dataDir, args[iArg++]);
				if (!gshhsFile0.isDirectory() && gshhsFile0.canRead()) {
					gshhsFile = gshhsFile0;
				} else {
					gshhsFile = getStableCousin(gshhsFile0);
				}
				MyLogger.out(null, String.format("\niArg[%d] gshhsFile[%s]", iArg, gshhsFile.getAbsolutePath()));
			} else {
				gshhsFile = null;
			}

			/** Get redBFile if and only if oldHomeDir is not null. */
			final File redBFile;
			if (oldHomeDir != null) {
				final File redFileDir = new File(oldHomeDir, _Phase5ResultDirName);
				MyLogger.out(null, String.format("\niArg[%d] redBFile:%s", iArg, args[iArg]));
				redBFile = new File(redFileDir, args[iArg++]);
			} else {
				redBFile = null;
			}

			/** Have the inputs. Do the !@#$ work. */
			doTheBuild(startPhase, amend, homeDir, oldHomeDir, gshhsFile, redBFile);
		}
	}

	final private static int _NDivisionsForLngExtents = 20000;
	final private static int _MaxDivisionsToPrint = 25;
	final private static int nDigits = (int) Math.ceil(Math.log10(_NDivisionsForLngExtents));
	final private static String _Format = "\n%7.5f(%0" + nDigits + "d) %s";

	public static void dumpByRngs(final MyLogger logger, final String caption,
			final Collection<? extends GcaSequence> gcaSequences) {
		for (int iPass = 0; iPass < 2; ++iPass) {
			final boolean focusOnLng = iPass == 0;
			String s = "\n" + caption;
			final GcaSequenceStatics.GcaRngInfo[] gcaRngInfos = GcaSequenceStatics.sortByRngs(gcaSequences, focusOnLng);
			final int nGcaRngInfos = gcaRngInfos == null ? 0 : gcaRngInfos.length;
			for (int k0 = 0; k0 < Math.min(_MaxDivisionsToPrint, _NDivisionsForLngExtents); ++k0) {
				final double proportion = ((double) k0) / _NDivisionsForLngExtents;
				final int k1 = (int) Math.round(proportion * nGcaRngInfos);
				if (k1 >= nGcaRngInfos) {
					break;
				}
				s += String.format(_Format, proportion, k1, gcaRngInfos[k1].getString(focusOnLng));
			}
			MyLogger.out(logger, s);
		}
	}

}
