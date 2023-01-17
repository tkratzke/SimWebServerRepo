package com.skagit.buildSimLand;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeSet;

import com.skagit.buildSimLand.minorAdj.CollectMinorAdjsFromUmbrellaDir;
import com.skagit.buildSimLand.minorAdj.MinorAdj;
import com.skagit.util.StaticUtilities;
import com.skagit.util.geometry.IldList;
import com.skagit.util.geometry.IntLoopData;
import com.skagit.util.geometry.LoopAndIldUtils;
import com.skagit.util.geometry.crossingPair.CrossingPair2;
import com.skagit.util.geometry.gcaSequence.GcaSequenceStatics;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.geometry.gcaSequence.Loop3Statics;
import com.skagit.util.geometry.loopsFinder.LoopsNester;
import com.skagit.util.gshhs.GshhsReader;
import com.skagit.util.gshhs.GshhsReaderStatics;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.shpFileUtils.ShpFileWriter;

public class Phase4Worker {
	final private static ShpFileWriter _ShpFileWriter =
			BuildSimLand._ShpFileWriter;

	final private static boolean _DumpProjectionsWithSubstitutePaths = true;
	final private BuildSimLand _buildSimLand;
	final private MyLogger _logger;
	final private File _homeDir;
	final private File _logDir4;
	final private File _shpDir4;
	final private File _phase4AdjsDir;
	final File _resultDir;

	public Phase4Worker(final BuildSimLand buildSimLand) {
		_buildSimLand = buildSimLand;
		_logger = _buildSimLand._logger;
		_homeDir = _buildSimLand._homeDir;
		_phase4AdjsDir =
				new File(_buildSimLand._dataDir, BuildSimLand._Phase4AdjsDirName);
		_logDir4 = new File(_homeDir, "Phase4Log");
		_shpDir4 = new File(_homeDir, "Phase4Shp");
		_resultDir = new File(_homeDir, "Phase4Result");
	}

	void doPhase4() {
		doPhase4(null);
	}

	void doPhase4(ArrayList<IntLoopData> phase3Ilds) {
		if (_buildSimLand.doThisPhase(BuildSimLand.Phase.PHASE4)) {
			StaticUtilities.makeDirectory(_shpDir4);
			StaticUtilities.makeDirectory(_resultDir);
			StaticUtilities.clearDirectoryWithFilter(_logDir4,
					/* filenameFilter= */null);
			StaticUtilities.clearDirectory(_shpDir4);
			StaticUtilities.clearDirectory(_resultDir);
		}
		_logger.resetAppenders(_logDir4, //
				/* dbgCoreName= */null, //
				/* outCoreName= */"Phase4.out", //
				/* wrnCoreName= */"Phase4.wrn", //
				/* errCoreName= */"Phase4.err", //
				"txt", /* append= */true);

		/** Collect the adjustments. */
		final File stablePhase4AdjsDir =
				BuildSimLand.getStableCousin(_phase4AdjsDir);
		final MinorAdj[] minorAdjs;
		final boolean amend = _buildSimLand._amend;
		if (amend) {
			final File oldDataDir =
					new File(_buildSimLand._oldHomeDir, BuildSimLand._DataDirName);
			final File oldPhase4AdjsDir =
					new File(oldDataDir, BuildSimLand._Phase4AdjsDirName);
			final MinorAdj[] oldMinorAdjsArray =
					CollectMinorAdjsFromUmbrellaDir.collectMinorAdjsFromUmbrellaDir(
							_logger, oldPhase4AdjsDir, stablePhase4AdjsDir);
			final TreeSet<MinorAdj> oldMinorAdjs =
					new TreeSet<>(MinorAdj._StandardComparator);
			oldMinorAdjs.addAll(Arrays.asList(oldMinorAdjsArray));
			final MinorAdj[] newMinorAdjsArray =
					CollectMinorAdjsFromUmbrellaDir.collectMinorAdjsFromUmbrellaDir(
							_logger, _phase4AdjsDir, stablePhase4AdjsDir);
			/**
			 * Keep only those that are not in oldMinorAdjs, but retain the order.
			 */
			final ArrayList<MinorAdj> newMinorAdjsList = new ArrayList<>();
			for (final MinorAdj minorAdj : newMinorAdjsArray) {
				if (!oldMinorAdjs.contains(minorAdj)) {
					newMinorAdjsList.add(minorAdj);
				}
			}
			final int nNewMinorAdjs = newMinorAdjsList.size();
			minorAdjs = newMinorAdjsList.toArray(new MinorAdj[nNewMinorAdjs]);
		} else {
			minorAdjs =
					CollectMinorAdjsFromUmbrellaDir.collectMinorAdjsFromUmbrellaDir(
							_logger, _phase4AdjsDir, stablePhase4AdjsDir);
		}
		_buildSimLand.LogTimeAndMemory("Phase 4-Read in adjustments");
		final int nMinorAdjs = minorAdjs == null ? 0 : minorAdjs.length;
		for (int k = 0; k < nMinorAdjs; ++k) {
			final MinorAdj minorAdj = minorAdjs[k];
			final String s = String.format("\nMinorAdj %d of %d.%s", k,
					nMinorAdjs, minorAdj.getString(/* oldLoops= */null));
			_logger.out(s);
		}

		/** Get phase3. */
		ArrayList<Loop3> phase3 = null;
		if (amend) {
			final GshhsReader gshhsReader =
					GshhsReader.constructGshhsReader(_buildSimLand._redBFile,
							GshhsReaderStatics._BareMinimumHeaderFilter);
			phase3 = gshhsReader.getAllSubLoops(_logger);
			phase3.trimToSize();
		} else {
			final File phase3ResultDir = _buildSimLand.getPhase3ResultDir();
			if (phase3Ilds == null) {
				final File phase3File =
						new File(phase3ResultDir, BuildSimLand._Phase3ResultBFileName);
				final GshhsReader gshhsReader = GshhsReader.constructGshhsReader(
						phase3File, GshhsReaderStatics._BareMinimumHeaderFilter);
				phase3 = gshhsReader.getAllSubLoops(_logger);
				phase3.trimToSize();
			} else {
				phase3 = IldList.getLoops(phase3Ilds, /* destroyInput= */true);
				phase3Ilds = null;
			}
		}
		_buildSimLand.LogTimeAndMemory("Phase 4-Read in Phase3");

		/**
		 * Now collect and dump the adjustments; we need to do it this way so we
		 * have access to oldLoops (i.e., phase3).
		 */
		CollectMinorAdjsFromUmbrellaDir.writeDispCasesDirsIfAskedTo(_logger,
				_phase4AdjsDir, minorAdjs,
				_DumpProjectionsWithSubstitutePaths ? /* oldLoops= */phase3 : null);

		_logger.resetAppenders(_logDir4, //
				/* dbgCoreName= */null, /* outCoreName= */"Phase4InputCheck.out", //
				/* wrnCoreName= */"Phase4InputCheck.wrn", //
				/* errCoreName= */"Phase4InputCheck.err", //
				"txt", /* append= */true);
		final CrossingPair2 xingPair = Loop3Statics.findCrossingPair(_logger,
				/* loopsName= */"Phase4InputCheck", phase3);
		assert xingPair == null : "Bad input for Phase4";
		_buildSimLand.LogTimeAndMemory("Phase 4-Checked Phase3");
		LoopsNester.setEnclosers(_logger, "Phase 4 Set Encloser/Enclosees",
				phase3, /* topIsCw= */false, /* fixOrientation= */false,
				/* minLevel= */Loop3Statics._BaseLevelForLand);

		_logger.resetAppenders(_logDir4, //
				/* dbgCoreName= */null, //
				/* outCoreName= */"Adjustments.out", //
				/* wrnCoreName= */"Adjustments.wrn", //
				/* errCoreName= */"Adjustments.err", //
				"txt", /* append= */true);

		ArrayList<Loop3> phase4 =
				MinorAdj.processMinorAdjs(_logger, _logDir4, phase3, minorAdjs);
		GcaSequenceStatics.freeMemory(phase3, /* clearList= */true);
		phase3 = null;
		LoopsNester.setEnclosers(_logger, "Finish Phase4", phase4,
				/* topIsCw= */false, /* fixOrientation= */false,
				/* minLevel= */Loop3Statics._BaseLevelForLand);
		_buildSimLand.LogTimeAndMemory("Phase 4-Did all adjusting");

		BuildSimLand.dumpByRngs(_logger,
				"After applying Phase4Adjs, we have the following Rngs:", phase4);

		/** Write phase4 in both shp and binary formats. */
		final ArrayList<IntLoopData> phase4Ilds =
				LoopAndIldUtils.getIlds(phase4, /* destroyInput= */true);

		phase4 = null;

		IldList phase4IldList = new IldList("Phase4", phase4Ilds);
		phase4IldList.writeToShpFiles(_logger,
				BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter, _shpDir4,
				/* clearIdlProblem= */false, /* writeAsSections= */false,
				/* nMaxInSection= */-1);
		phase4IldList = null;
		_buildSimLand.LogTimeAndMemory("Phase 4-ShpFiled phase4");

		try {
			final String phase4FilePath =
					new File(_resultDir, BuildSimLand._Phase4ResultBFileName)
							.getCanonicalPath();
			GshhsWriter.dumpIlds(phase4Ilds, phase4FilePath);
		} catch (final IOException e1) {
		}
		_buildSimLand.LogTimeAndMemory("Phase 4-Dumped phase4");

		_buildSimLand.doPhase5(phase4Ilds, /* doSimplecopy */true);
	}
}
