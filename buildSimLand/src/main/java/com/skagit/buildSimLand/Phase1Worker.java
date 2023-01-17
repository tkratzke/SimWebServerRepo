package com.skagit.buildSimLand;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.skagit.buildSimLand.minorAdj.CollectMinorAdjsFromUmbrellaDir;
import com.skagit.buildSimLand.minorAdj.MinorAdj;
import com.skagit.util.Constants;
import com.skagit.util.MathX;
import com.skagit.util.StaticUtilities;
import com.skagit.util.geometry.IldList;
import com.skagit.util.geometry.IntLoopData;
import com.skagit.util.geometry.LoopAndIldUtils;
import com.skagit.util.geometry.LoopList;
import com.skagit.util.geometry.crossingPair.CrossingPair2;
import com.skagit.util.geometry.gcaSequence.CleanOpenLoop;
import com.skagit.util.geometry.gcaSequence.GcaSequenceStatics;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.geometry.gcaSequence.Loop3Statics;
import com.skagit.util.geometry.geoMtx.xing1Bundle.Xing1Bundle;
import com.skagit.util.geometry.loopsFinder.LoopsFinder;
import com.skagit.util.geometry.loopsFinder.LoopsNester;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.gshhs.GshhsReader;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.GreatCircleCalculator;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.shpFileUtils.ReadShpFile;
import com.skagit.util.shpFileUtils.ShpFileWriter;

class Phase1Worker {
	/** Simplify Nhd. */
	final private static Simplifier.ParamSet _SimplifierNhdParamSet = new Simplifier.ParamSet('n', 0.004, 00.05,
			/* tooFewToLoseAny= */24, /* maxToSkip= */06, 0.015);
	final private static int _GshhsBaseId = 0;
	final private static int _UsgsBaseId = 100000;
	final private static int _NhdBaseId = 200000;
	final private ShpFileWriter _ShpFileWriter = BuildSimLand._ShpFileWriter;

	final private BuildSimLand _buildSimLand;
	final private MyLogger _logger;
	final private File _homeDir;
	final private File _gshhsAdjDir;
	final private File _usgsAdjDir;
	final private File _nhdAdjDir;
	final private File _logDir;
	final private File _shpDir;
	final File _resultDir;

	public Phase1Worker(final BuildSimLand buildSimLand) {
		_buildSimLand = buildSimLand;
		_homeDir = _buildSimLand._homeDir;
		_logger = _buildSimLand._logger;

		final File gshhsFile = _buildSimLand._gshhsFile;
		final File dataDir = _buildSimLand._dataDir;
		if (gshhsFile != null) {
			_gshhsAdjDir = new File(dataDir, BuildSimLand._GshhsAdjsDirName);
			_usgsAdjDir = new File(dataDir, BuildSimLand._UsgsAdjsDirName);
			_nhdAdjDir = new File(dataDir, BuildSimLand._NhdAdjsDirName);
		} else {
			_gshhsAdjDir = _usgsAdjDir = _nhdAdjDir = null;
		}
		_logDir = new File(_homeDir, "Phase1Log");
		_shpDir = new File(_homeDir, "Phase1Shp");
		_resultDir = new File(_homeDir, "Phase1Result");
	}

	void doPhase1() {
		_buildSimLand.LogTimeAndMemory("Phase 1-Just Entering");
		if (_buildSimLand.doThisPhase(BuildSimLand.Phase.PHASE1)) {
			StaticUtilities.makeDirectory(_shpDir);
			StaticUtilities.makeDirectory(_resultDir);
			StaticUtilities.clearDirectoryWithFilter(_logDir, /* filenameFilter= */null);
			StaticUtilities.clearDirectory(_shpDir);
			StaticUtilities.clearDirectory(_resultDir);
		}
		/** Preliminary. */
		_logger.resetAppenders(_logDir, //
				/* dbgCoreName= */null, //
				/* outCoreName= */"Phase1Prelim.out", //
				/* wrnCoreName= */"Phase1Prelim.wrn", //
				/* errCoreName= */"Phase1Prelim.err", //
				"txt", /* append= */true);

		/** Collect the 3 sets of adjustments. */
		final File altGshhsAdjDir = BuildSimLand.getStableCousin(_gshhsAdjDir);
		MinorAdj[] gshhsAdjs = CollectMinorAdjsFromUmbrellaDir.collectMinorAdjsFromUmbrellaDir(_logger, _gshhsAdjDir,
				altGshhsAdjDir);
		CollectMinorAdjsFromUmbrellaDir.writeDispCasesDirsIfAskedTo(_logger, _gshhsAdjDir, gshhsAdjs,
				/* oldLoops= */null);
		final File altUsgsAdjDir = BuildSimLand.getStableCousin(_usgsAdjDir);
		MinorAdj[] usgsAdjs = CollectMinorAdjsFromUmbrellaDir.collectMinorAdjsFromUmbrellaDir(_logger, _usgsAdjDir,
				altUsgsAdjDir);
		CollectMinorAdjsFromUmbrellaDir.writeDispCasesDirsIfAskedTo(_logger, _usgsAdjDir, usgsAdjs,
				/* oldLoops= */null);
		final File altNhdAdjDir = BuildSimLand.getStableCousin(_nhdAdjDir);
		MinorAdj[] nhdAdjs = CollectMinorAdjsFromUmbrellaDir.collectMinorAdjsFromUmbrellaDir(_logger, _nhdAdjDir,
				altNhdAdjDir);
		CollectMinorAdjsFromUmbrellaDir.writeDispCasesDirsIfAskedTo(_logger, _nhdAdjDir, nhdAdjs, /* oldLoops= */null);

		/************************** Gshhs *****************************/
		_logger.resetAppenders(_logDir, //
				/* dbgCoreName= */null, //
				/* outCoreName= */"Gshhs.out", //
				/* wrnCoreName= */"Gshhs.wrn", //
				/* errCoreName= */"Gshhs.err", //
				"txt", /* append= */true);
		/** Get cleaned up Gshhs Loops and shpFile them. */
		ArrayList<Loop3> gshhs0 = getCleanedUpGshhsLoops(_buildSimLand._gshhsFile);

		LoopAndIldUtils.convertLoopListToZeroSubIds(_GshhsBaseId, gshhs0);
		LoopList gshhs0LoopList = new LoopList("Gshhs0", gshhs0);
		gshhs0LoopList.writeToShpFiles(_logger, BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter, _shpDir,
				/* clearIdlProblems= */true, /* writeAsSections= */false, /* nMaxInSection= */-1);
		gshhs0LoopList = null;

		/** Deconflict them and shpFile that. */
		ArrayList<Loop3> gshhs1 = FilterSmallAndBadLoops(_logger, gshhs0);
		GcaSequenceStatics.freeMemory(gshhs0, /* clearList= */true);
		gshhs0 = null;
		LoopList gshhs1LoopList = new LoopList("Gshhs1", gshhs1);
		gshhs1LoopList.writeToShpFiles(_logger, BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter, _shpDir,
				/* clearIdlProblems= */true, /* writeAsSections= */false, /* nMaxInSection= */-1);
		gshhs1LoopList = null;
		/**
		 * Adjust them, and then set their enclosers; this is our final Gshhs list,
		 * which we both shpFile and dump.
		 */
		ArrayList<Loop3> gshhs = MinorAdj.processMinorAdjs(_logger, _logDir, gshhs1, gshhsAdjs);
		BuildSimLand.dumpByRngs(_logger, "After Gshhs loops' adjustments, we have the following Rngs:", gshhs);

		GcaSequenceStatics.freeMemory(gshhs1, /* clearList= */true);
		gshhs1 = null;
		LoopAndIldUtils.convertLoopListToZeroSubIds(_GshhsBaseId, gshhs);
		gshhsAdjs = null;
		LoopsNester.setEnclosers(_logger, "Phase 1 FinalGshhs", gshhs, /* topIsCw= */false, /* fixOrientation= */false,
				/* minLevel= */Loop3Statics._BaseLevelForLand, /* trackComputations= */true);
		final ArrayList<IntLoopData> gshhsIlds = LoopAndIldUtils.getIlds(gshhs, /* destroyInput= */true);
		gshhs = null;
		IldList gshhsIldList = new IldList("Gshhs", gshhsIlds);
		gshhsIldList.writeToShpFiles(_logger, BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter, _shpDir,
				/* clearIdlProblems= */true, /* writeAsSections= */false, /* nMaxInSection= */-1);
		gshhsIldList = null;
		String gshhsFilePath = null;
		try {
			gshhsFilePath = new File(_resultDir, BuildSimLand._GshhsResultBFileName).getCanonicalPath();
		} catch (final IOException e) {
		}
		GshhsWriter.dumpIlds(gshhsIlds, gshhsFilePath);
		_buildSimLand.LogTimeAndMemory("Phase 1-Done with Gshhs");

		/************************** Usgs *****************************/
		_logger.resetAppenders(_logDir, //
				/* dbgCoreName= */null, //
				/* outCoreName= */"Usgs.out", //
				/* wrnCoreName= */"Usgs.wrn", //
				/* errCoreName= */"Usgs.err", //
				"txt", /* append= */true);
		/** Get raw Usgs Loops and shpFile them. */
		ArrayList<Loop3> usgs0 = null;
		final File usgsShpFilesDir = new File(_buildSimLand._dataDir, BuildSimLand._UsgsShpFilesName);
		for (int iPass = 0; iPass < 2; ++iPass) {
			final File shpFilesDir = iPass == 0 ? usgsShpFilesDir : BuildSimLand.getStableCousin(usgsShpFilesDir);
			try {
				usgs0 = ReadShpFile.getLoopsFromShpFile(shpFilesDir.getAbsolutePath(), Loop3Statics._BaseLevelForWater,
						/* acceptInnerLoops= */true, ReadShpFile.ShpFileStyle.USGS);
			} catch (final Exception e) {
			}
			if (usgs0 != null && usgs0.size() > 0) {
				break;
			}
		}
		LoopList usgs0LoopList = new LoopList("Usgs0", usgs0);
		usgs0LoopList.writeToShpFiles(_logger, BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter, _shpDir,
				/* clearIdlProblems= */true, /* writeAsSections= */false, /* nMaxInSection= */-1);
		usgs0LoopList = null;
		/** Clean them up and shpFile that. */
		ArrayList<Loop3> usgs1 = LoopsFinder.findLoopsFromLoops(_logger, usgs0.toArray(new Loop3[usgs0.size()]),
				/* waterWins= */true);
		usgs0 = null;
		LoopList usgs1LoopList = new LoopList("Usgs1", usgs1);
		usgs1LoopList.writeToShpFiles(_logger, BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter, _shpDir,
				/* clearIdlProblems= */true, /* writeAsSections= */false, /* nMaxInSection= */-1);
		usgs1LoopList = null;
		/**
		 * Apply the Adjs, convert to 0 subIds, and shpFile the result.
		 */
		ArrayList<Loop3> usgs = MinorAdj.processMinorAdjs(_logger, _logDir, usgs1, usgsAdjs);
		GcaSequenceStatics.freeMemory(usgs1, /* clearList= */true);
		usgs1 = null;
		usgsAdjs = null;
		LoopAndIldUtils.convertLoopListToZeroSubIds(_UsgsBaseId, usgs);
		LoopList usgsLoopList = new LoopList("Usgs", usgs);
		usgsLoopList.writeToShpFiles(_logger, BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter, _shpDir,
				/* clearIdlProblems= */true, /* writeAsSections= */false, /* nMaxInSection= */-1);
		usgsLoopList = null;
		_buildSimLand.LogTimeAndMemory("Phase 1-Read in and adjusted Usgs");

		/***************************** Nhd *******************************/
		_logger.resetAppenders(_logDir, //
				/* dbgCoreName= */null, //
				/* outCoreName= */"Nhd.out", //
				/* wrnCoreName= */"Nhd.wrn", //
				/* errCoreName= */"Nhd.err", //
				"txt", /* append= */true);
		/** Get raw Nhd Loops and shpFile them. */
		ArrayList<Loop3> nhd0 = null;
		final File nhdShpFilesDir = new File(_buildSimLand._dataDir, BuildSimLand._NhdShpFilesName);
		for (int iPass = 0; iPass < 2; ++iPass) {
			final File shpFilesDir = iPass == 0 ? nhdShpFilesDir : BuildSimLand.getStableCousin(nhdShpFilesDir);
			try {
				nhd0 = ReadShpFile.getLoopsFromShpFile(shpFilesDir.getAbsolutePath(), Loop3Statics._BaseLevelForWater,
						/* acceptInnerLoops= */true, ReadShpFile.ShpFileStyle.AREA);
			} catch (final Exception e) {
			}
			if (nhd0 != null && nhd0.size() > 0) {
				break;
			}
		}
		LoopList nhd0LoopList = new LoopList("Nhd0", nhd0);
		nhd0LoopList.writeToShpFiles(_logger, BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter, _shpDir,
				/* clearIdlProblems= */true, /* writeAsSections= */false, /* nMaxInSection= */-1);
		nhd0LoopList = null;
		/** Clean them up and shpFile that. */
		ArrayList<Loop3> nhd1 = LoopsFinder.findLoopsFromLoops(_logger, nhd0.toArray(new Loop3[nhd0.size()]),
				/* waterWins= */true);
		GcaSequenceStatics.freeMemory(nhd0, /* clearList= */true);
		nhd0 = null;
		LoopList nhd1LoopList = new LoopList("Nhd1", nhd1);
		nhd1LoopList.writeToShpFiles(_logger, BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter, _shpDir,
				/* clearIdlProblems= */true, /* writeAsSections= */false, /* nMaxInSection= */-1);
		nhd1LoopList = null;
		/** Apply the Adjs, and shpFile them. */
		ArrayList<Loop3> nhd2 = MinorAdj.processMinorAdjs(_logger, _logDir, nhd1, nhdAdjs);
		GcaSequenceStatics.freeMemory(nhd1, /* clearList= */true);
		nhd1 = null;
		nhdAdjs = null;
		LoopList nhd2LoopList = new LoopList("Nhd2", nhd2);
		nhd2LoopList.writeToShpFiles(_logger, BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter, _shpDir,
				/* clearIdlProblems= */true, /* writeAsSections= */false, /* nMaxInSection= */-1);
		nhd2LoopList = null;
		/** Simplify, convert to 0 subIds, and shpFile the result. */
		final ArrayList<IntLoopData> nhdIlds0 = LoopAndIldUtils.getIlds(nhd2, /* destroyInput= */true);
		nhd2 = null;
		ArrayList<IntLoopData> nhdIlds = Simplifier.simplifyIlds(_logger, _SimplifierNhdParamSet, nhdIlds0,
				_ShpFileWriter, _shpDir);
		IldList nhdIldList = new IldList("Nhd", nhdIlds);
		nhdIldList.writeToShpFiles(_logger, BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter, _shpDir,
				/* clearIdlProblems= */true, /* writeAsSections= */false, /* nMaxInSection= */-1);
		nhdIldList = null;
		ArrayList<Loop3> nhd = IldList.getLoops(nhdIlds, /* destroyInput= */true);
		nhdIlds = null;
		LoopAndIldUtils.convertLoopListToZeroSubIds(_NhdBaseId, nhd);
		_buildSimLand.LogTimeAndMemory("Phase 1-Read in and adjusted Nhd");

		/************************** Hydro *****************************/
		_logger.resetAppenders(_logDir, //
				/* dbgCoreName= */null, //
				/* outCoreName= */"Hydro.out", //
				/* wrnCoreName= */"Hydro.wrn", //
				/* errCoreName= */"Hydro.err", //
				"txt", /* append= */true);
		ArrayList<Loop3> hydro0 = new ArrayList<>();
		hydro0.addAll(usgs);
		hydro0.addAll(nhd);
		StaticUtilities.clearList(usgs);
		StaticUtilities.clearList(nhd);
		usgs = nhd = null;
		LoopList hydro0LoopList = new LoopList("Hydro0", hydro0);
		hydro0LoopList.writeToShpFiles(_logger, BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter, _shpDir,
				/* clearIdlProblems= */true, /* writeAsSections= */false, /* nMaxInSection= */-1);
		hydro0LoopList = null;

		ArrayList<Loop3> hydro1 = LoopsFinder.findLoopsFromLoops(_logger, hydro0.toArray(new Loop3[hydro0.size()]),
				/* waterWins= */true);
		GcaSequenceStatics.freeMemory(hydro0, /* clearList= */true);
		hydro0 = null;
		assert Loop3Statics.loopsLinksAreValid(hydro1) : "hydro1Loops has bad links.";
		LoopList hydro1LoopList = new LoopList("Hydro1", hydro1);
		hydro1LoopList.writeToShpFiles(_logger, BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter, _shpDir,
				/* clearIdlProblems= */true, /* writeAsSections= */false, /* nMaxInSection= */-1);
		hydro1LoopList = null;

		ArrayList<Loop3> hydro = new ArrayList<>(hydro1);
		GcaSequenceStatics.freeMemory(hydro1, /* clearList= */true);
		hydro1 = null;
		LoopsNester.setEnclosers(_logger, "Phase 1 FinalHydro", hydro, /* topIsCw= */true, /* fixOrientation= */true,
				/* minLevel= */Loop3Statics._BaseLevelForWater);
		LoopAndIldUtils.convertLoopListToZeroSubIds(/* baseId= */0, hydro);
		final ArrayList<IntLoopData> hydroIlds = LoopAndIldUtils.getIlds(hydro, /* destroyInput= */true);
		hydro = null;
		IldList hydroIldListA = new IldList(BuildSimLand._HydroShpFileName, hydroIlds);
		/**
		 * Since Phase2 might read this shape file, I do not clear the Idl problems.
		 * Note that since this a "result," I put it in _ResultDir1 and not _ShpDir1.
		 */
		hydroIldListA.writeToShpFiles(_logger, BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter, _resultDir,
				/* clearIdlProblems= */false, /* writeAsSections= */false, /* nMaxInSection= */-1);
		hydroIldListA = null;
		/** For standard display, clear the Idl problems. */
		IldList hydroIldListB = new IldList(BuildSimLand._HydroShpFileName + "-Disp", hydroIlds);
		hydroIldListB.writeToShpFiles(_logger, BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter, _shpDir,
				/* clearIdlProblems= */true, /* writeAsSections= */false, /* nMaxInSection= */-1);
		hydroIldListB = null;
		_buildSimLand.LogTimeAndMemory("Phase 1-Combined Usgs and Nhd");

		_buildSimLand.doPhase2(gshhsIlds, hydroIlds);
	}

	private ArrayList<Loop3> getCleanedUpGshhsLoops(final File bFile) {
		final GshhsReader gshhsReader = GshhsReader.constructGshhsReader(bFile, /* headerFilter= */null);
		final ArrayList<Loop3> gshhs = new ArrayList<>();
		for (int k = 0;; ++k) {
			final GshhsReader.HeaderPlusLoops headerPlusLoops = gshhsReader.getThePartsOfNextLoop(_logger,
					/* knownToBeClean= */false);
			if (headerPlusLoops == null) {
				break;
			}
			final Loop3[] loops = headerPlusLoops._loops;
			final int nLoops = loops == null ? 0 : loops.length;
			if (nLoops == 0) {
				/**
				 * No good loop; there was supposed to be something there, but spikes or some
				 * such killed it.
				 */
				_logger.out(String.format("k[%d] Have %d Loops, this one is bad though.\nHeader[%s].", k, gshhs.size(),
						headerPlusLoops._header.getString()));
			} else {
				final Loop3 loop = loops[0];
				gshhs.add(loop);
				final int nGotten = gshhs.size();
				if (nGotten <= 20 || nGotten % 5000 == 0) {
					_logger.out(String.format("Got %d Loops, this one has [id,nOpen]=[%d,%d].", nGotten, loop.getId(),
							loop.getNGcas()));
				}
			}
		}
		final int nReturnLoops = gshhs.size();
		final Loop3 lastLoop = gshhs.get(nReturnLoops - 1);
		final String f = "Done with Gshhs Read: %d Loops, last one has [id,nOpen]=[%d,%d].";
		_logger.out(String.format(f, //
				gshhs.size(), lastLoop.getId(), lastLoop.getNGcas()));
		gshhs.trimToSize();
		GcaSequenceStatics.freeMemory(gshhs, /* clearList= */false);
		return gshhs;
	}

	/**
	 * Pitches small Loops that conflict with larger ones. Each input loop is
	 * assumed to be internally clean.
	 */
	final private static double _NudgeM = LoopsFinder._InitialNudgeM / 2d;
	final private static double _NudgeR = (_NudgeM / Constants._NmiToM) * MathX._NmiToR;

	static ArrayList<Loop3> FilterSmallAndBadLoops(final MyLogger mainBslLogger, final List<Loop3> loopsIn) {
		final int nLoopsIn = loopsIn.size();
		final ArrayList<Loop3> sortedLoopsIn = new ArrayList<>(nLoopsIn);
		sortedLoopsIn.addAll(loopsIn);
		sortedLoopsIn.sort(Loop3._SizeStructure);

		/** Nudge LatLng3s inward. */
		final ArrayList<Loop3> loopsOut = new ArrayList<>(nLoopsIn);
		final HashSet<LatLng3> allLatLngs = new HashSet<>();
		for (int k0 = 0; k0 < nLoopsIn; ++k0) {
			final Loop3 loop = sortedLoopsIn.get(k0);
			final boolean loopIsCw = loop.isClockwise();
			final ArrayList<LatLng3> nudgedLatLngs = new ArrayList<>();
			/**
			 * If loop "shares" a vertex with another (bigger) loop, nudge the offending
			 * vertex inward to try to get out of the way of the bigger loop.
			 */
			final int nGcas = loop.getNGcas();
			final ArrayList<Integer> idxsToNudge = new ArrayList<>();
			for (int k = 0; k < nGcas; ++k) {
				final LatLng3 latLng = loop.getGca(k).getLatLng0();
				if (!allLatLngs.add(latLng)) {
					idxsToNudge.add(k);
					nudgedLatLngs.add(latLng);
				}
			}
			final int nToNudge = idxsToNudge.size();
			final Loop3 theLoop;
			final boolean needsFixing = nToNudge > 0;
			if (needsFixing) {
				theLoop = loop;
			} else {
				final GreatCircleArc[] patchedGcaArray = loop.getGcaArray();
				for (int k1 = 0; k1 < nToNudge; ++k1) {
					final int idx = idxsToNudge.get(k1);
					final int pvsIdx = (idx + nGcas - 1) % nGcas;
					final GreatCircleArc oldPvsGca = patchedGcaArray[pvsIdx];
					final GreatCircleArc oldThisGca = patchedGcaArray[idx];
					final double turnToLeft = GreatCircleCalculator.getTurnToLeftD(oldPvsGca, oldThisGca,
							/* round= */false);
					final LatLng3 pvsLatLng = oldPvsGca.getLatLng0();
					final LatLng3 thisLatLng = oldThisGca.getLatLng0();
					final LatLng3 nxtLatLng = oldThisGca.getLatLng1();
					final double oldHdg = oldThisGca.getRoundedInitialHdg();
					final double hdgToNewPoint;
					if (loopIsCw) {
						hdgToNewPoint = oldHdg + (180d + turnToLeft) / 2d;
					} else {
						hdgToNewPoint = oldHdg + (turnToLeft - 180d) / 2d;
					}
					/** Go _NudgeM meters and relink them. */
					final LatLng3 newLatLng = MathX.getLatLngX(thisLatLng, hdgToNewPoint, _NudgeR);
					allLatLngs.add(newLatLng);
					final GreatCircleArc newPvsGca = GreatCircleArc.CreateGca(pvsLatLng, newLatLng);
					patchedGcaArray[pvsIdx] = newPvsGca;
					final GreatCircleArc newThisGca = GreatCircleArc.CreateGca(newLatLng, nxtLatLng);
					patchedGcaArray[idx] = newThisGca;
					newPvsGca.setGcaSequence(loop, oldPvsGca.getPvs(), newThisGca);
					final GreatCircleArc nxtGca = oldThisGca.getNxt();
					newThisGca.setGcaSequence(loop, newPvsGca, nxtGca);
					nxtGca.setGcaSequence(loop, newThisGca, nxtGca.getNxt());
				}
				final int id = loop.getId();
				Loop3 newLoop = null;
				final int subId = loop.getSubId();
				final int flag = loop.getFlag();
				final int ancestorId = loop.getAncestorId();
				final boolean logChanges = false;
				final boolean debug = false;
				newLoop = Loop3.getLoop(mainBslLogger, id, subId, flag, ancestorId, patchedGcaArray,
						CleanOpenLoop._StandardAllChecks, logChanges, debug);
				theLoop = newLoop;
			}
			if (theLoop == null) {
				final String errorMsg = String.format(
						"\nLost loop[%s] because of internal problems; inward nudges didn't help.",
						loop.getBigString(/* dumpAll= */false));
				mainBslLogger.err(errorMsg);
				continue;
			}
			loopsOut.add(theLoop);
			if (needsFixing) {
				final int nNudgedLatLngs = nudgedLatLngs.size();
				String statusMsg = "";
				if (nudgedLatLngs.size() == 1) {
					final String f = "Salvaged Loop[%d] with %d vertices, nudging %s.";
					statusMsg = String.format(f, theLoop.getId(), theLoop.getNGcas(), nudgedLatLngs.get(0).getString());
				} else {
					final String f0 = "Salvaged Loop[%d] with %d vertices, nudging:";
					statusMsg = String.format(f0, theLoop.getId(), theLoop.getNGcas());
					final String f1 = "\n\t%s";
					for (int k = 0; k < nNudgedLatLngs; ++k) {
						statusMsg += String.format(f1, nudgedLatLngs.get(k).getString());
					}
				}
				mainBslLogger.out(statusMsg);
			} else {
				final int nKept = loopsOut.size();
				if (nKept % 5000 == 0) {
					final String f = "Loop[%d] with %d vertices needs no fixing.  "
							+ "Considered %d so far, keeping %d.";
					final String statusMsg = String.format(f, theLoop.getId(), theLoop.getNGcas(), k0 + 1, nKept);
					mainBslLogger.out(statusMsg);
				}
			}
		}

		/** If 2 loops in loopsOut conflict, delete the smaller. */
		for (int pass = 0;; ++pass) {
			GcaSequenceStatics.freeMemory(loopsOut, /* clearList= */false);
			final Xing1Bundle xing1Bndl = GcaSequenceStatics
					.getXing1Bundle(loopsOut.toArray(new Loop3[loopsOut.size()]), /* getOnlyOne= */true);
			final CrossingPair2 xingPair = xing1Bndl.getCrossingPair();
			if (xingPair != null) {
				mainBslLogger.out(String.format("Pass[%d] of Final SmallAndBadCheck, got crossing:\n%s", pass,
						xingPair.getString()));
				final GreatCircleArc gca0 = xingPair._gca0;
				final GreatCircleArc gca1 = xingPair._gca1;
				final Loop3 loop0 = (Loop3) gca0.getGcaSequence();
				final Loop3 loop1 = (Loop3) gca1.getGcaSequence();
				/** Keep the smaller one wrt Loop3._SizeStructure. */
				final int compareValue = Loop3._SizeStructure.compare(loop0, loop1);
				final Loop3 loopToKeep = compareValue <= 0 ? loop0 : loop1;
				final Loop3 loopToDiscard = loopToKeep == loop0 ? loop1 : loop0;
				final String f = "Conflict between:\n\t(Keep)%s\n\tand\n\t(Discard)%s";
				final String s = String.format(f, loopToKeep.getString(), loopToDiscard.getString());
				mainBslLogger.out(s);
				loopsOut.remove(loopToDiscard);
			} else {
				break;
			}
		}
		return loopsOut;
	}
}
