package com.skagit.buildSimLand;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import org.apache.commons.io.FilenameUtils;

import com.skagit.util.Constants;
import com.skagit.util.SizeOf;
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
import com.skagit.util.geometry.loopsFinder.TopLoopCreator;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.gshhs.GshhsReader;
import com.skagit.util.gshhs.GshhsReaderStatics;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.shpFileUtils.ReadShpFile;
import com.skagit.util.shpFileUtils.ShpFileWriter;

class Phase3Worker {
	final private static ShpFileWriter _ShpFileWriter =
			BuildSimLand._ShpFileWriter;

	final private BuildSimLand _buildSimLand;
	final private MyLogger _logger;
	final private File _homeDir;
	final private File _logDir;
	final private File _shpDir;
	final private File _refinementsDir;
	final File _resultDir;

	public Phase3Worker(final BuildSimLand buildSimLand) {
		_buildSimLand = buildSimLand;
		_logger = _buildSimLand._logger;
		_homeDir = _buildSimLand._homeDir;
		_refinementsDir = new File(_buildSimLand._dataDir,
				BuildSimLand._Phase3RefinementsDirName);
		_logDir = new File(_homeDir, "Phase3Log");
		_shpDir = new File(_homeDir, "Phase3Shp");
		_resultDir = new File(_homeDir, "Phase3Result");
	}

	/**
	 * Manipulating ArcMap: Coord.sys, modify, change central
	 * Tools,options,DataView
	 */
	void doPhase3() {
		doPhase3(null);
	}

	void doPhase3(ArrayList<IntLoopData> phase2Ilds) {
		if (_buildSimLand.doThisPhase(BuildSimLand.Phase.PHASE2)) {
			StaticUtilities.makeDirectory(_shpDir);
			StaticUtilities.makeDirectory(_resultDir);
			StaticUtilities.clearDirectoryWithFilter(_logDir,
					/* filenameFilter= */null);
			StaticUtilities.clearDirectory(_shpDir);
			StaticUtilities.clearDirectory(_resultDir);
		}
		_logger.resetAppenders(_logDir, //
				/* dbgCoreName= */null, //
				/* outCoreName= */"Phase3.out", //
				/* wrnCoreName= */"Phase3.wrn", //
				/* errCoreName= */"Phase3.err", //
				"txt", /* append= */true);
		SizeOf.runGC(_logger);
		ArrayList<Loop3> phase2 = null;
		final File phase2ResultDir = _buildSimLand.getPhase2ResultDir();
		if (phase2Ilds == null) {
			final File phase2File =
					new File(phase2ResultDir, BuildSimLand._Phase2ResultBFileName);
			final GshhsReader gshhsReader = GshhsReader.constructGshhsReader(
					phase2File, GshhsReaderStatics._BareMinimumHeaderFilter);
			phase2 = gshhsReader.getAllSubLoops(_logger);
		} else {
			phase2 = IldList.getLoops(phase2Ilds, /* destroyInput= */true);
			phase2Ilds = null;
		}
		_logger.resetAppenders(_logDir, //
				/* dbgCoreName= */null, //
				/* outCoreName= */"Phase3InputCheck.out", //
				/* wrnCoreName= */"Phase3InputCheck.wrn", //
				/* errCoreName= */"Phase3InputCheck.err", //
				"txt", /* append= */true);
		final CrossingPair2 xingPairIn = Loop3Statics.findCrossingPair(_logger,
				/* loopsName= */"Phase3InputCheck", phase2);
		assert xingPairIn == null : "Bad input for Phase3";
		GcaSequenceStatics.freeMemory(phase2, /* clearList= */false);
		_buildSimLand.LogTimeAndMemory("Phase 3-Read in and checked Phase2");

		_logger.resetAppenders(_logDir, //
				/* dbgCoreName= */null, //
				/* outCoreName= */"Phase3FillInMskAndDtl.out", //
				/* wrnCoreName= */"Phase3FillInMskAndDtl.wrn", //
				/* errCoreName= */"Phase3FillInMskAndDtl.err", //
				"txt", /* append= */true);

		/** Create the phase3BFilePath. */
		final String phase3BFilePath =
				new File(_resultDir, BuildSimLand._Phase3ResultBFileName)
						.getAbsolutePath();

		/**
		 * This paragraph produces two sets of Loops, and dumps them to
		 * shpFiles. Each loop will be intrinsically sound.
		 */
		ArrayList<Loop3> rawMsk0 = new ArrayList<>();
		ArrayList<Loop3> rawDtl0 = new ArrayList<>();
		fillInMskAndDtlLoopLists(rawMsk0, rawDtl0);

		final ArrayList<IntLoopData> phase3Ilds;

		/**
		 * If we have something, process phase2 to phase3Ilds. Otherwise, we
		 * just use phase2.
		 */
		if (rawMsk0.size() > 0 || rawDtl0.size() > 0) {
			LoopList rawMsk0LoopList = new LoopList("RawMsk0", rawMsk0);
			rawMsk0LoopList.writeToShpFiles(_logger,
					BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter,
					_shpDir, /* clearIdlProblems= */true, /* writeAsSections= */false,
					/* nMaxInSection= */-1);
			rawMsk0LoopList = null;
			final LoopList rawDtl0LoopList = new LoopList("RawDtl0", rawDtl0);
			rawDtl0LoopList.writeToShpFiles(_logger,
					BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter,
					_shpDir, /* clearIdlProblems= */true, /* writeAsSections= */false,
					/* nMaxInSection= */-1);

			/** Get all dtl loops. */
			_logger.out("Distilling and Nesting Dtl Loops");
			final int nRawDtl0 = rawDtl0.size();
			ArrayList<Loop3> rawDtl1 = LoopsFinder.findLoopsFromLoops(_logger,
					rawDtl0.toArray(new Loop3[nRawDtl0]), /* waterWins= */false);
			GcaSequenceStatics.freeMemory(rawDtl0, /* clearList= */true);
			rawDtl0 = null;
			LoopsNester.setEnclosers(_logger, "Phase 3 RawDtl1", rawDtl1,
					/* topIsCw= */false, /* fixOrientation= */false,
					/* minLevel= */Loop3Statics._BaseLevelForLand);
			LoopList rawDtl1LoopList = new LoopList("RawDtl1", rawDtl1);
			rawDtl1LoopList.writeToShpFiles(_logger,
					BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter,
					_shpDir, /* clearIdlProblems= */true, /* writeAsSections= */false,
					/* nMaxInSection= */-1);
			rawDtl1LoopList = null;
			_buildSimLand.LogTimeAndMemory("Phase 3-Got all Dtl loops");

			/** For each TlDtlLoop, create a cw copy to add to the masks. */
			ArrayList<Loop3> tlDtl = LoopsNester.getTlLoops(rawDtl1);
			final int nTlDtlLoops = tlDtl.size();
			for (int k = 0; k < nTlDtlLoops; ++k) {
				final Loop3 tlDtlLoop = tlDtl.get(k);
				if (tlDtlLoop.isClockwise()) {
					rawMsk0.add(tlDtlLoop);
				} else {
					final Loop3 revTlDtlLoop = tlDtlLoop.createReverseLoop(_logger);
					rawMsk0.add(revTlDtlLoop);
				}
			}
			/** Done with tlDtl. */
			GcaSequenceStatics.freeMemory(tlDtl, /* clearList= */true);
			tlDtl = null;

			/** Form tlMsk. */
			_logger
					.out("Merging, nesting, and taking just the top level Msk Loops");
			ArrayList<Loop3> msk = LoopsFinder.findLoopsFromLoops(_logger,
					rawMsk0.toArray(new Loop3[rawMsk0.size()]), /* waterWins= */true);
			GcaSequenceStatics.freeMemory(rawMsk0, /* clearList= */true);
			rawMsk0 = null;
			LoopsNester.setEnclosers(_logger, "Phase 3 Msk", msk,
					/* topIsCw= */true, /* fixOrientation= */false,
					/* minLevel= */-1);
			ArrayList<Loop3> tlMsk = LoopsNester.getTlLoops(msk);
			GcaSequenceStatics.clearList(msk);
			msk = null;
			final int nTlMskLoops = tlMsk.size();
			for (int k = 0; k < nTlMskLoops; ++k) {
				final Loop3 tlMskLoop = tlMsk.get(k);
				tlMskLoop.clearEncloserAndEnclosees();
			}
			LoopList tlMskLoopList = new LoopList("TlMsk", tlMsk);
			tlMskLoopList.writeToShpFiles(_logger,
					BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter,
					_shpDir, /* clearIdlProblems= */true, /* writeAsSections= */false,
					/* nMaxInSection= */-1);
			tlMskLoopList = null;
			_buildSimLand.LogTimeAndMemory("Phase 3-Got all Msk loops");

			/**
			 * <pre>
			 *    Filter out phase2.  For each phase2Loop p2L:
			 * 1. If p2L is surrounded by some TlMsk loop, discard p2L.
			 * 2. Else if p2L crosses some TlMsk, put it in toCarve.
			 * 3. Else put it in setAside.
			 * </pre>
			 */
			GeoMtx tlMskMtx = Loop3Statics.createGcaMtx(_logger, tlMsk);
			ArrayList<Loop3> toCarve = new ArrayList<>();

			SizeOf.runGC(_logger);
			ArrayList<IntLoopData> setAsideIlds = new ArrayList<>();
			final int nPhase2 = phase2.size();
			NEXT_PHASE2_LOOP: for (int k = 0; k < nPhase2; ++k) {
				final Loop3 phase2Loop = phase2.get(k);
				for (final Loop3 tlMskLoop : tlMsk) {
					if (tlMskLoop.surrounds(_logger, phase2Loop)) {
						/** Pitch phase2Loop by ignoring it. */
						continue NEXT_PHASE2_LOOP;
					}
				}
				final CheckForUnknownPairs alwaysCheck0 =
						CheckForUnknownPairs.CreateAlwaysCheck0();
				final GeoMtx phase2GcaMtx = phase2Loop.getGcaMtx();
				final Xing1Bundle xing1Bndl =
						new Xing1Bundle(alwaysCheck0, /* getOnlyOne= */true);
				Xing1Bundle.updateXing1Bundle(xing1Bndl, phase2GcaMtx, tlMskMtx);
				final CrossingPair2 xingPair = xing1Bndl.getCrossingPair();
				if (xingPair != null) {
					toCarve.add(phase2Loop);
				} else {
					setAsideIlds.add(new IntLoopData(phase2Loop));
				}
			}
			tlMskMtx = null;
			GcaSequenceStatics.freeMemory(phase2, /* clearList= */true);
			phase2 = null;
			_buildSimLand.LogTimeAndMemory("Phase 3-Partitioned Phase2");

			LoopList toCarveLoopList = new LoopList("ToCarve", toCarve);
			toCarveLoopList.writeToShpFiles(_logger,
					BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter,
					_shpDir, /* clearIdlProblems= */true, /* writeAsSections= */false,
					/* nMaxInSection= */-1);
			toCarveLoopList = null;
			IldList setAsideIldList = new IldList("SetAside", setAsideIlds);
			setAsideIldList.writeToShpFiles(_logger,
					BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter,
					_shpDir, /* clearIdlProblems= */true, /* writeAsSections= */false,
					/* nMaxInSection= */-1);
			setAsideIldList = null;

			ArrayList<Loop3> toMerge0 = new ArrayList<>();
			toMerge0.addAll(toCarve);
			toMerge0.addAll(tlMsk);
			GcaSequenceStatics.clearList(toCarve);
			GcaSequenceStatics.clearList(tlMsk);
			toCarve = tlMsk = null;

			_logger.resetAppenders(_logDir, //
					/* dbgCoreName= */null, //
					/* outCoreName= */"Phase3Merging.out", //
					/* wrnCoreName= */"Phase3Merging.wrn", //
					/* errCoreName= */"Phase3Merging.err", //
					"txt", /* append= */true);
			ArrayList<Loop3> merged0 = LoopsFinder.findLoopsFromLoops(_logger,
					toMerge0.toArray(new Loop3[toMerge0.size()]),
					/* waterWins= */true);
			GcaSequenceStatics.freeMemory(toMerge0, /* clearList= */true);
			toMerge0 = null;
			LoopList merged0LoopList = new LoopList("Merged0", merged0);
			merged0LoopList.writeToShpFiles(_logger,
					BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter,
					_shpDir, /* clearIdlProblems= */true, /* writeAsSections= */false,
					/* nMaxInSection= */-1);
			merged0LoopList = null;
			_buildSimLand.LogTimeAndMemory("Phase 3-Preliminary Carve");

			ArrayList<Loop3> toMerge1 = new ArrayList<>();
			toMerge1.addAll(merged0);
			toMerge1.addAll(rawDtl1);
			GcaSequenceStatics.clearList(merged0);
			GcaSequenceStatics.clearList(rawDtl1);
			merged0 = rawDtl1 = null;
			ArrayList<Loop3> merged = LoopsFinder.findLoopsFromLoops(_logger,
					toMerge1.toArray(new Loop3[toMerge1.size()]),
					/* waterWins= */true);
			GcaSequenceStatics.freeMemory(toMerge1, /* clearList= */true);
			toMerge1 = null;
			LoopList merged1LoopList = new LoopList("Merged1", merged);
			merged1LoopList.writeToShpFiles(_logger,
					BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter,
					_shpDir, /* clearIdlProblems= */true, /* writeAsSections= */false,
					/* nMaxInSection= */-1);
			merged1LoopList = null;
			_buildSimLand.LogTimeAndMemory("Phase 3-Secondary Carve");

			_logger.resetAppenders(_logDir, //
					/* dbgCoreName= */null, //
					/* outCoreName= */"Phase3NestingAndDumping.out", //
					/* wrnCoreName= */"Phase3NestingAndDumping.wrn", //
					/* errCoreName= */"Phase3NestingAndDumping.err", //
					"txt", /* append= */true);

			ArrayList<Loop3> phase3 = new ArrayList<>();
			phase3.addAll(merged);
			ArrayList<Loop3> setAside =
					IldList.getLoops(setAsideIlds, /* destroyInput= */true);
			setAsideIlds = null;
			phase3.addAll(setAside);
			GcaSequenceStatics.clearList(merged);
			GcaSequenceStatics.clearList(setAside);
			merged = setAside = null;
			LoopsNester.setEnclosers(_logger, "Final Phase 3", phase3,
					/* topIsCw= */false, /* fixOrientation= */false,
					/* minLevel= */Loop3Statics._BaseLevelForLand);
			_buildSimLand.LogTimeAndMemory("Phase 3-Set enclosers");
			/* */
			/** Write phase3 in both shp and binary formats. */
			_logger.resetAppenders(_logDir, //
					/* dbgCoreName= */null, //
					/* outCoreName= */"WritingAndDFiling.out", //
					/* wrnCoreName= */"WritingAndDFiling.wrn", //
					/* errCoreName= */"WritingAndDFiling.err", //
					"txt", /* append= */true);

			BuildSimLand.dumpByRngs(_logger,
					"After applying Phase3 Refinements, we have the following Rngs:",
					phase3);

			phase3Ilds = LoopAndIldUtils.getIlds(phase3, /* destroyInput= */true);
			phase3 = null;

			/** shpFiles. */
			final String shpFileName =
					FilenameUtils.getBaseName(BuildSimLand._Phase3ResultBFileName);
			IldList phase3IldList = new IldList(shpFileName, phase3Ilds);
			phase3IldList.writeToShpFiles(_logger,
					BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter,
					_shpDir, /* correctIdlProblem= */true,
					/* writeAsSections= */false, /* nMaxInSection= */-1);
			phase3IldList = null;
			_buildSimLand.LogTimeAndMemory("Phase 3-ShpFiled result");
		} else {
			phase3Ilds = LoopAndIldUtils.getIlds(phase2, /* destroyInput= */true);
		}

		GshhsWriter.dumpIlds(phase3Ilds, phase3BFilePath);
		_buildSimLand.doPhase4(phase3Ilds);
	}

	private static final String[] _PossibleSuffixes =
			{ "shp", "dbf", "prj", "qix", "sbn", "sbx", "xml", "shx" };
	private static final FileFilter _ForShpFiles = new FileFilter() {
		@Override
		public boolean accept(final File f) {
			final String lcFName = f.getName().toLowerCase();
			for (final String suffix : _PossibleSuffixes) {
				if (lcFName.endsWith(suffix)) {
					return true;
				}
			}
			return false;
		}
	};

	private void fillInMskAndDtlLoopLists(final ArrayList<Loop3> msk,
			final ArrayList<Loop3> dtl) {
		final ArrayList<File> dirsOfPairsList = new ArrayList<>();
		for (int iPass = 0; iPass < 2; ++iPass) {
			final File refinementsDir = iPass == 0 ? _refinementsDir :
					BuildSimLand.getStableCousin(_refinementsDir);
			final File[] theseDirsOfPairs =
					refinementsDir.listFiles(new FileFilter() {
						@Override
						public boolean accept(final File f) {
							if (!f.isDirectory()) {
								return false;
							}
							final String fName = f.getName();
							final int lastLetter = fName.length() - 1;
							return fName.charAt(0) != '(' ||
									fName.charAt(lastLetter) != ')';
						}
					});
			if (theseDirsOfPairs != null) {
				dirsOfPairsList.addAll(Arrays.asList(theseDirsOfPairs));
			}
		}
		final int nDirsOfPairs = dirsOfPairsList.size();
		final File[] dirsOfPairs =
				dirsOfPairsList.toArray(new File[nDirsOfPairs]);
		for (int k0 = 0; k0 < nDirsOfPairs; ++k0) {
			final File dirOfPair = dirsOfPairs[k0];
			/** Add to the mask loops. */
			final File maskDir = new File(dirOfPair, "Mask");
			if (maskDir.isDirectory()) {
				final File[] shpFileCandidates = maskDir.listFiles(_ForShpFiles);
				if (shpFileCandidates != null && shpFileCandidates.length > 0) {
					final String[] shpFilePathCandidates =
							new String[shpFileCandidates.length];
					for (int k = 0; k < shpFileCandidates.length; ++k) {
						shpFilePathCandidates[k] =
								StringUtilities.getCanonicalPath(shpFileCandidates[k]);
					}
					final ReadShpFile.ShpFileReadReturn shpFileReadReturn =
							getAndCleanUpLoops(shpFilePathCandidates,
									/* waterWins= */true);
					final ArrayList<Loop3> inputMsk = shpFileReadReturn._loops;
					final String s = String.format("Masks from %s, count[%d].",
							maskDir.getAbsolutePath(), inputMsk.size());
					_logger.out(s);
					msk.addAll(inputMsk);
				}
			}
			/** Add to the Dtl loops. */
			final File dtlDir = new File(dirOfPair, "Detail");
			if (dtlDir.isDirectory()) {
				final File[] shpFileCandidates = dtlDir.listFiles(_ForShpFiles);
				if (shpFileCandidates != null && shpFileCandidates.length > 0) {
					final String[] shpFilePaths =
							new String[shpFileCandidates.length];
					for (int k = 0; k < shpFileCandidates.length; ++k) {
						shpFilePaths[k] =
								StringUtilities.getCanonicalPath(shpFileCandidates[k]);
					}
					final ReadShpFile.ShpFileReadReturn shpFileReadReturn =
							getAndCleanUpLoops(shpFilePaths, /* waterWins= */false);
					final ArrayList<Loop3> inputDtl = shpFileReadReturn._loops;
					final String shpFilePath = shpFileReadReturn._shpFilePath;
					if (inputDtl.size() > 0) {
						if (shpFilePath.toLowerCase().contains("greatlakes")) {
							shrinkWrap(inputDtl);
						}
					}
					final String s2 = String.format("Dtls from %s, count[%d].",
							dtlDir.getAbsolutePath(), inputDtl.size());
					_logger.out(s2);
					dtl.addAll(inputDtl);
				}
			}
		}
	}

	private void shrinkWrap(final ArrayList<Loop3> inputDtlLoops) {
		/**
		 * This is just for the Great Lakes. Find the two largest loops. The
		 * biggest will be replaced by a blanket around the second biggest.
		 */
		final Iterator<Loop3> it0 = inputDtlLoops.iterator();
		Loop3 loopToReplace = null;
		Loop3 loopToCover = null;
		double biggestSqNmi = 0d;
		double secondBiggestSqNmi = 0d;
		final int nLoops = inputDtlLoops.size();
		for (int k = 0; k < nLoops; ++k) {
			final Loop3 loop = it0.next();
			final double sqNmi = loop.getSqNmi();
			if (sqNmi > secondBiggestSqNmi) {
				secondBiggestSqNmi = sqNmi;
				loopToCover = loop;
				if (sqNmi > biggestSqNmi) {
					secondBiggestSqNmi = biggestSqNmi;
					loopToCover = loopToReplace;
					biggestSqNmi = sqNmi;
					loopToReplace = loop;
				}
			}
		}

		final ArrayList<Loop3> threeLoops = new ArrayList<>(3);
		threeLoops.add(loopToReplace);
		threeLoops.add(loopToCover);
		String s = "\n Using\n\tCreating a shrinkWrap for " +
				loopToCover.getString() + "\n from " + loopToReplace.getString();
		_logger.out(s);
		final double bufferM = 2d * Constants._NmiToM;
		final Loop3 blanketLoop =
				buildBlanket(loopToReplace, loopToCover, bufferM);
		threeLoops.add(blanketLoop);
		_logger.out(s);
		LoopList threeLoopsLoopList = new LoopList("ThreeLoops", threeLoops);
		threeLoopsLoopList.writeToShpFiles(_logger,
				BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter, _shpDir,
				/* clearIdlProblems= */true, /* writeAsSections= */false,
				/* nMaxInSection= */-1);
		threeLoopsLoopList = null;

		inputDtlLoops.remove(loopToReplace);
		inputDtlLoops.add(blanketLoop);
		s = "";
		s += "\n After Replacement, we now use\n\t" + blanketLoop.getString();
		_logger.out(s);
	}

	private ReadShpFile.ShpFileReadReturn getAndCleanUpLoops(
			final String[] shpFilePaths, final boolean waterWins) {
		final int outsideLoopLevel;
		final boolean acceptInnerLoops;
		final ReadShpFile.ShpFileStyle shpFileStyle;
		if (waterWins) {
			outsideLoopLevel = Loop3Statics._BaseLevelForWater;
			acceptInnerLoops = false;
			shpFileStyle = ReadShpFile.ShpFileStyle.MASK;
		} else {
			outsideLoopLevel = Loop3Statics._BaseLevelForLand;
			acceptInnerLoops = true;
			shpFileStyle = ReadShpFile.ShpFileStyle.DETAIL;
		}

		/**
		 * For each outer ring, find the sections that have the correct
		 * orientation.
		 */
		final ReadShpFile.ShpFileReadReturn shpFileReadReturn =
				ReadShpFile.getLoopsFromShpFileCandidates(shpFilePaths,
						outsideLoopLevel, acceptInnerLoops, shpFileStyle);
		final ArrayList<Loop3> source0 =
				shpFileReadReturn == null ? null : shpFileReadReturn._loops;
		final int nLoops0 = source0 == null ? 0 : source0.size();
		_logger.out(String.format(
				"Total Number of Loops (including islands) accepted[%d].",
				nLoops0));
		if (nLoops0 > 0) {
			/** shpFile source0. */
			String shpFileName = new File(shpFilePaths[0]).getName();
			final int lastDot =
					shpFileName.lastIndexOf(FilenameUtils.EXTENSION_SEPARATOR);
			if (lastDot >= 0) {
				final String origShpFileName = shpFileName;
				shpFileName = shpFileName.substring(0, lastDot);
				if (shpFileName.length() == 0 && origShpFileName.length() > 1) {
					shpFileName = origShpFileName.substring(1);
				}
			}
			LoopList sourceLoopList = new LoopList(shpFileName, source0);
			sourceLoopList.writeToShpFiles(_logger,
					BuildSimLand._InitialPhasesWritePolylines, _ShpFileWriter,
					_shpDir, /* clearIdlProblems= */true, /* writeAsSections= */false,
					/* nMaxInSection= */-1);
			sourceLoopList = null;

			final ArrayList<Loop3> source1 = LoopsFinder.findLoopsFromLoops(
					_logger, source0.toArray(new Loop3[source0.size()]), waterWins);
			LoopsNester.setEnclosers(_logger,
					"Phase 3 Find loops from " + shpFilePaths[0], source1,
					/* topIsCw= */waterWins, /* fixOrientation= */false,
					/* minLevel= */-1);
			return new ReadShpFile.ShpFileReadReturn(source1,
					shpFileReadReturn._shpFilePath);
		}
		return new ReadShpFile.ShpFileReadReturn(new ArrayList<Loop3>(),
				shpFileReadReturn == null ? null : shpFileReadReturn._shpFilePath);
	}

	/** The blanket will run in the opposite direction of loopToCover. */
	private Loop3 buildBlanket(final Loop3 loopToReplace,
			final Loop3 loopToCover, final double bufferM) {
		final double bufferNmi = bufferM / Constants._NmiToM;
		final GreatCircleArc[] gcaArray = loopToCover.getGcaArray();
		final int nGcas = gcaArray.length;
		final ArrayList<Loop3> bigLoops = new ArrayList<>();
		K0_LOOP: for (int k0 = 0; k0 < nGcas;) {
			final GreatCircleArc startingGca = gcaArray[k0];
			final LatLng3 start = startingGca.getLatLng0();
			final int loopId = k0;
			for (int k1 = k0; k1 <= nGcas; ++k1) {
				Loop3 newLoop1 = null;
				if (k1 < nGcas) {
					final GreatCircleArc endingGca1 = gcaArray[k1];
					final LatLng3 stop1 = endingGca1.getLatLng1();
					newLoop1 = Loop3Statics.createCcwJacket(_logger, loopId, start,
							stop1, bufferNmi);
				}
				if (newLoop1 != null) {
					for (int k2 = k0 + 1; k2 <= k1; ++k2) {
						final LatLng3 tweener = gcaArray[k2].getLatLng0();
						if (!newLoop1.interiorContains(_logger, tweener)) {
							newLoop1 = null;
							break;
						}
					}
				}
				if (newLoop1 == null) {
					final int k2 = k1 - 1;
					final GreatCircleArc endingGca2 = gcaArray[k2];
					final LatLng3 stop = endingGca2.getLatLng1();
					Loop3 newLoop = null;
					newLoop = Loop3Statics.createCcwJacket(_logger, loopId, start,
							stop, bufferNmi);
					bigLoops.add(newLoop);
					k0 = k1;
					continue K0_LOOP;
				}
			}
		}

		final Loop3[] bigLoopsArray =
				bigLoops.toArray(new Loop3[bigLoops.size()]);
		final ArrayList<Loop3> bigLoopsResult = LoopsFinder
				.findLoopsFromLoops(_logger, bigLoopsArray, /* waterWins= */false);
		final TopLoopCreator tlc =
				new TopLoopCreator(_logger, "Blanket Loop", bigLoopsResult, /*
																																		 * tooManLoopsForComplicated=
																																		 */
						Integer.MAX_VALUE);
		assert tlc._topLoops.size() == 1 : "Should have gotten only one loop.";
		final Loop3 blanketLoop = tlc._topLoop;
		final int bigId = loopToReplace.getId();
		final int bigSubId = loopToReplace.getSubId();
		/** We return a loop like the one we're replacing. */
		assert blanketLoop.isClockwise() == loopToReplace
				.isClockwise() : "Blanket loop should have come back in the same direction.";
		blanketLoop.setIds(bigId, bigSubId);
		return blanketLoop;
	}
}
