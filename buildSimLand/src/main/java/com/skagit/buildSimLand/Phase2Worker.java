package com.skagit.buildSimLand;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import com.skagit.util.StaticUtilities;
import com.skagit.util.StringUtilities;
import com.skagit.util.geometry.IldList;
import com.skagit.util.geometry.IntLoopData;
import com.skagit.util.geometry.LoopAndIldUtils;
import com.skagit.util.geometry.LoopList;
import com.skagit.util.geometry.crossingPair.CheckForUnknownPairs;
import com.skagit.util.geometry.crossingPair.CrossingPair2;
import com.skagit.util.geometry.gcaSequence.GcaSequenceStatics;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.geometry.gcaSequence.Loop3Statics;
import com.skagit.util.geometry.geoMtx.GeoMtx;
import com.skagit.util.geometry.geoMtx.xing1Bundle.Xing1Bundle;
import com.skagit.util.geometry.loopsFinder.LoopsFinder;
import com.skagit.util.geometry.loopsFinder.LoopsNester;
import com.skagit.util.gshhs.GshhsReader;
import com.skagit.util.gshhs.GshhsReaderStatics;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.shpFileUtils.ReadShpFile;
import com.skagit.util.shpFileUtils.ReadShpFile.ShpFileStyle;
import com.skagit.util.shpFileUtils.ShpFileWriter;

class Phase2Worker {
	final private static ShpFileWriter _ShpFileWriter =
			BuildSimLand._ShpFileWriter;

	final private BuildSimLand _buildSimLand;
	final private MyLogger _logger;
	final private File _homeDir;
	final private File _logDir;
	final private File _shpDir;
	final File _resultDir;

	public Phase2Worker(final BuildSimLand buildSimLand) {
		_buildSimLand = buildSimLand;
		_logger = _buildSimLand._logger;
		_homeDir = _buildSimLand._homeDir;
		_logDir = new File(_homeDir, "Phase2Log");
		_shpDir = new File(_homeDir, "Phase2Shp");
		_resultDir = new File(_homeDir, "Phase2Result");
	}

	void doPhase2() {
		doPhase2(null, null);
	}

	void doPhase2(ArrayList<IntLoopData> gshhsIlds,
			ArrayList<IntLoopData> hydroIlds) {
		if (_buildSimLand.doThisPhase(BuildSimLand.Phase.PHASE2)) {
			StaticUtilities.makeDirectory(_shpDir);
			StaticUtilities.makeDirectory(_resultDir);
			StaticUtilities.clearDirectoryWithFilter(_logDir,
					/* filenameFilter= */null);
			StaticUtilities.clearDirectory(_shpDir);
			StaticUtilities.clearDirectory(_resultDir);
		}
		ArrayList<Loop3> gshhs = null;
		final File phase1ResultDir = _buildSimLand.getPhase1ResultDir();
		if (gshhsIlds == null) {
			final File gshhsFile =
					new File(phase1ResultDir, BuildSimLand._GshhsResultBFileName);
			final GshhsReader gshhsReader = GshhsReader.constructGshhsReader(
					gshhsFile, GshhsReaderStatics._BareMinimumHeaderFilter);
			gshhs = gshhsReader.getAllSubLoops(_logger);
		} else {
			gshhs = IldList.getLoops(gshhsIlds, /* destroyInput= */true);
			gshhsIlds = null;
		}

		GcaSequenceStatics.freeMemory(gshhs, /* clearList= */false);
		ArrayList<Loop3> hydro = null;
		if (hydroIlds == null) {
			final boolean acceptInnerLoops = true;
			final int outsideLoopLevelForHydro = Loop3Statics._BaseLevelForWater;
			final String hydroShpPath = StringUtilities.getCanonicalPath(
					new File(phase1ResultDir, BuildSimLand._HydroShpFileName));
			hydro = ReadShpFile.getLoopsFromShpFile(hydroShpPath,
					outsideLoopLevelForHydro, acceptInnerLoops,
					ShpFileStyle.WRITE_SHP_FILES);
		} else {
			hydro = IldList.getLoops(hydroIlds, /* destroyInput= */true);
			hydroIlds = null;
		}
		GcaSequenceStatics.freeMemory(hydro, /* clearList= */false);
		_buildSimLand.LogTimeAndMemory("Phase 2-Read in Gshhs and Hydro");

		final CrossingPair2 xingPair0In = Loop3Statics.findCrossingPair(_logger,
				/* loopsName= */"Phase2GshhsInputCheck", gshhs);
		assert xingPair0In == null : "Bad Gshhs input for Phase2";
		final CrossingPair2 xingPair1In = Loop3Statics.findCrossingPair(_logger,
				/* loopsName= */"Phase2HydroInputCheck", hydro);
		assert xingPair1In == null : "Bad Hydro input for Phase2";
		_buildSimLand.LogTimeAndMemory("Phase 2-Checked input Gshhs and Hydro");

		/**
		 * Partition gshhs into those that conflict with hydro, and those that
		 * do not.
		 */
		_logger.resetAppenders(_logDir, //
				/* dbgCoreName= */null, //
				/* outCoreName= */"Phase2XflctCalcs.out", //
				/* wrnCoreName= */"Phase2XflctCalcs.wrn", //
				/* errCoreName= */"Phase2XflctCalcs.err", //
				"txt", /* append= */true);
		ArrayList<Loop3> gshhsXflct = new ArrayList<>();
		ArrayList<Loop3> clear = new ArrayList<>();

		GeoMtx hydroGcaMtx = Loop3Statics.createGcaMtx(_logger, hydro);
		final int nGshhs = gshhs.size();
		for (int k = 0; k < nGshhs; ++k) {
			final Loop3 loop = gshhs.get(k);
			final CheckForUnknownPairs checkForUnknownPairs =
					CheckForUnknownPairs.CreateAlwaysCheck0();
			final Xing1Bundle xing1Bndl =
					new Xing1Bundle(checkForUnknownPairs, /* getOnlyOne= */true);
			final GeoMtx loopGcaMtx = loop.getGcaMtx();
			Xing1Bundle.updateXing1Bundle(xing1Bndl, loopGcaMtx, hydroGcaMtx);
			final CrossingPair2 xingPair = xing1Bndl.getCrossingPair();
			if (xingPair != null) {
				gshhsXflct.add(loop);
			} else {
				clear.add(loop);
			}
		}
		hydroGcaMtx = null;
		GcaSequenceStatics.freeMemory(gshhs, /* clearList= */true);
		gshhs = null;

		final int nGshhsXflct = gshhsXflct.size();
		final int nGshhsClear = clear.size();

		/** ShpFile gshhsXflct. */
		LoopList gshhsXflctList = new LoopList("GshhsXflct", gshhsXflct);
		gshhsXflctList.writeToShpFiles(_logger,
				BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter, _shpDir,
				/* clearIdlProblems= */true, /* writeAsSections= */false,
				/* nMaxInSection= */-1);
		gshhsXflctList = null;

		/**
		 * Partition hydro into those that conflict with gshhsXflct, and add
		 * those that do not, to "clear."
		 */
		ArrayList<Loop3> hydroXflct = new ArrayList<>();
		GeoMtx gshhsGcaMtx = Loop3Statics.createGcaMtx(_logger, gshhsXflct);
		final int nHydro = hydro.size();
		for (int k = 0; k < nHydro; ++k) {
			final Loop3 loop = hydro.get(k);
			final CheckForUnknownPairs checkForUnknownPairs =
					CheckForUnknownPairs.CreateAlwaysCheck0();
			final Xing1Bundle xing1Bndl =
					new Xing1Bundle(checkForUnknownPairs, /* getOnlyOne= */true);
			final GeoMtx loopGcaMtx = loop.getGcaMtx();
			Xing1Bundle.updateXing1Bundle(xing1Bndl, loopGcaMtx, gshhsGcaMtx);
			final CrossingPair2 crossingPair = xing1Bndl.getCrossingPair();
			if (crossingPair != null) {
				hydroXflct.add(loop);
			} else {
				clear.add(loop);
			}
		}
		gshhsGcaMtx = null;
		GcaSequenceStatics.freeMemory(hydro, /* clearList= */true);
		hydro = null;

		final int nHydroXflct = hydroXflct.size();
		final int nHydroClear = clear.size() - nGshhsClear;

		_logger.out(String.format(
				"Gshhs:(Orig/Xflct/Clr)[%d/%d] Hydro:(Orig/Xflct/Clr)[%d/%d/%d]", //
				nGshhs, nGshhsXflct, nGshhsClear, //
				nHydro, nHydroXflct, nHydroClear));

		/** ShpFile hydroXflct. */
		LoopList hydroXflctList = new LoopList("HydroXflct", hydroXflct);
		hydroXflctList.writeToShpFiles(_logger,
				BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter, _shpDir,
				/* clearIdlProblems= */true, /* writeAsSections= */false,
				/* nMaxInSection= */-1);
		hydroXflctList = null;

		/** Partition the conflicting ones into cw and ccw. */
		ArrayList<Loop3> ccw = new ArrayList<>();
		final ArrayList<Loop3> cw = new ArrayList<>();
		for (int k = 0; k < nGshhsXflct; ++k) {
			final Loop3 loop = gshhsXflct.get(k);
			if (loop.isClockwise()) {
				cw.add(loop);
			} else {
				ccw.add(loop);
			}
		}
		GcaSequenceStatics.freeMemory(gshhsXflct, /* clearList= */true);
		gshhsXflct = null;

		for (int k = 0; k < nHydroXflct; ++k) {
			final Loop3 loop = hydroXflct.get(k);
			if (loop.isClockwise()) {
				cw.add(loop);
			} else {
				ccw.add(loop);
			}
		}
		GcaSequenceStatics.freeMemory(hydroXflct, /* clearList= */true);
		hydroXflct = null;
		_buildSimLand.LogTimeAndMemory("Phase 2-(Gshhs,Hydro)→(Xflct,Clear)");

		/** Union the ccw ones. */
		final int nCcw = ccw.size();
		ArrayList<Loop3> ccwResult = LoopsFinder.findLoopsFromLoops(_logger,
				ccw.toArray(new Loop3[nCcw]), /* waterWins= */false);
		GcaSequenceStatics.freeMemory(ccw, /* clearList= */true);
		ccw = null;
		LoopList ccwRsltList = new LoopList("Ccw", ccwResult);
		ccwRsltList.writeToShpFiles(_logger,
				BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter, _shpDir,
				/* clearIdlProblems= */true, /* writeAsSections= */false,
				/* nMaxInSection= */-1);
		ccwRsltList = null;

		/** Union the cw ones. */
		final int nCw = cw.size();
		ArrayList<Loop3> cwResult = LoopsFinder.findLoopsFromLoops(_logger,
				cw.toArray(new Loop3[nCw]), /* waterWins= */true);
		GcaSequenceStatics.freeMemory(cw, /* clearList= */true);
		LoopList cwRsltList = new LoopList("Cw", cwResult);
		cwRsltList.writeToShpFiles(_logger,
				BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter, _shpDir,
				/* clearIdlProblems= */true, /* writeAsSections= */false,
				/* nMaxInSection= */-1);
		cwRsltList = null;
		_buildSimLand.LogTimeAndMemory("Phase 2-Preliminary Combining");

		/** Carve the ccw with the cw. */
		ArrayList<Loop3> finalXflctList = new ArrayList<>();
		finalXflctList.addAll(ccwResult);
		GcaSequenceStatics.freeMemory(ccwResult, /* clearList= */true);
		ccwResult = null;
		finalXflctList.addAll(cwResult);
		GcaSequenceStatics.freeMemory(cwResult, /* clearList= */true);
		cwResult = null;
		final int nFinalXflct = finalXflctList.size();
		ArrayList<Loop3> carved = LoopsFinder.findLoopsFromLoops(_logger,
				finalXflctList.toArray(new Loop3[nFinalXflct]),
				/* waterWins= */true);
		GcaSequenceStatics.freeMemory(finalXflctList, /* clearList= */true);
		finalXflctList = null;
		LoopList carvedLoopList = new LoopList("Carved", carved);
		carvedLoopList.writeToShpFiles(_logger,
				BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter, _shpDir,
				/* clearIdlProblems= */true, /* writeAsSections= */false,
				/* nMaxInSection= */-1);
		carvedLoopList = null;
		_buildSimLand.LogTimeAndMemory("Phase 2-Secondary Combining");

		/** Add carved and clear, then nest, and we're done. */
		ArrayList<Loop3> phase2 = new ArrayList<>(carved.size() + clear.size());
		phase2.addAll(carved);
		GcaSequenceStatics.freeMemory(carved, /* clearList= */true);
		carved = null;
		phase2.addAll(clear);
		GcaSequenceStatics.freeMemory(clear, /* clearList= */true);
		clear = null;
		LoopsNester.setEnclosers(_logger, "Final Phase 2", phase2,
				/* topIsCw= */false, /* fixOrientation= */true,
				/* minLevel= */Loop3Statics._BaseLevelForLand);
		_buildSimLand.LogTimeAndMemory("Phase 2-Done with Nesting");

		/** Write phase2 in shp and binary format. */
		_logger.resetAppenders(_logDir, //
				/* dbgCoreName= */null, //
				/* outCoreName= */"Phase2Dumping.out", //
				/* wrnCoreName= */"Phase2Dumping.wrn", //
				/* errCoreName= */"Phase2Dumping.err", //
				"txt", /* append= */true);

		BuildSimLand.dumpByRngs(_logger,
				"After applying Hydro, we have the following Rngs:", phase2);

		final ArrayList<IntLoopData> phase2Ilds =
				LoopAndIldUtils.getIlds(phase2, /* destroyInput= */true);
		phase2 = null;

		IldList phase2IldList = new IldList("Phase2", phase2Ilds);
		phase2IldList.writeToShpFiles(_logger,
				BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter, _shpDir,
				/* clearIdlProblems= */true, /* writeAsSections= */false,
				/* nMaxInSection= */-1);
		phase2IldList = null;
		_buildSimLand.LogTimeAndMemory("Phase 2-ShpFiled result");

		/** Dump binary for Phase3 to use. */
		String phase2BFilePathX = null;
		try {
			phase2BFilePathX =
					new File(_resultDir, BuildSimLand._Phase2ResultBFileName)
							.getCanonicalPath();
		} catch (final IOException e) {
		}
		final String phase2BFilePath = phase2BFilePathX;
		GshhsWriter.dumpIlds(phase2Ilds, phase2BFilePath);
		_buildSimLand.LogTimeAndMemory("Phase 2-Dumped Binary");

		_buildSimLand.doPhase3(phase2Ilds);
	}
}
