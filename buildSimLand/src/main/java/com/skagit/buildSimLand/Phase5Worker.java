package com.skagit.buildSimLand;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.skagit.buildSimLand.util.CompareGshhsFiles;
import com.skagit.util.Constants;
import com.skagit.util.SizeOf;
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

class Phase5Worker {

	private static class CharDouble {
		final private char _suffix;
		final private double _targetProportion;

		private CharDouble(final char suffix, final double targetProportion) {
			_suffix = suffix;
			_targetProportion = targetProportion;
		}
	}

	final private static ShpFileWriter _ShpFileWriter =
			BuildSimLand._ShpFileWriter;
	/**
	 * See comments on Simplifier.ParamSet.
	 *
	 * <pre>
	 * We only specify a Standard ParamSet and scale it to try to get the
	 * correct number of edges.
	 * </pre>
	 */
	final private static Simplifier.ParamSet _StandardParamSet =
			new Simplifier.ParamSet(Constants._EmptySet, 0.0005, 0.1, 24, 80,
					0.08);
	/**
	 * For each resolution above, we start with the given ParamSet, but our
	 * ultimate goal is to reduce the number of edges as per the values in the
	 * following map. We will scale the paramSets above, using a binary
	 * search, until we get some set of edges that are within range of the
	 * appropriate TargetProportion.
	 */
	final private static int _MaxNTries = 4;
	final private static double _ProportionTolerance = 0.1;
	final private static CharDouble[] _ResolutionCharTargetProportions =
			new CharDouble[] { new CharDouble('h', 0.3), //
					new CharDouble('i', 0.15), //
			// new CharDouble('l', 0.075),
			// new CharDouble('c', 0.0375)
			};

	final private BuildSimLand _buildSimLand;
	final private MyLogger _logger;
	final private File _homeDir;
	final private File _logDir;
	final private File _shpDir;
	final File _resultDir;
	final File _fullResolutionBFile;

	public Phase5Worker(final BuildSimLand buildSimLand) {
		_buildSimLand = buildSimLand;
		_logger = _buildSimLand._logger;
		_homeDir = _buildSimLand._homeDir;
		_logDir = new File(_homeDir, "Phase5Log");
		_shpDir = new File(_homeDir, "Phase5Shp");
		_resultDir = new File(_homeDir, BuildSimLand._Phase5ResultDirName);
		final String binaryFilePath =
				new File(_resultDir, BuildSimLand._FullResolutionBinaryFileName)
						.getAbsolutePath();
		_fullResolutionBFile = new File(binaryFilePath);
		StaticUtilities.makeDirectory(
				_fullResolutionBFile.getAbsoluteFile().getParentFile());
	}

	void doPhase5() {
		doPhase5(null, /* doSimpleCopy= */true);
	}

	void doPhase5(final ArrayList<IntLoopData> phase4IldsIn,
			final boolean doSimpleCopy0) {

		if (_buildSimLand.doThisPhase(BuildSimLand.Phase.PHASE5)) {
			StaticUtilities.makeDirectory(_shpDir);
			StaticUtilities.makeDirectory(_resultDir);
			StaticUtilities.clearDirectoryWithFilter(_logDir,
					/* filenameFilter= */null);
			StaticUtilities.clearDirectory(_shpDir);
			StaticUtilities.clearDirectoryOfSuffix(_resultDir, "b");
			StaticUtilities.clearDirectoryOfSuffix(_resultDir, "d");
		}
		_logger.resetAppenders(_logDir, //
				/* dbgCoreName= */null, //
				/* outCoreName= */"Phase5.out", //
				/* wrnCoreName= */"Phase5.wrn", //
				/* errCoreName= */"Phase5.err", //
				"txt", /* append= */true);

		/**
		 * Get phase4 and check input; after that, we are interested only in
		 * phase4Ilds.
		 */
		final ArrayList<IntLoopData> phase4Ilds;
		ArrayList<Loop3> phase4 = null;
		final File phase4ResultDir = _buildSimLand.getPhase4ResultDir();
		final boolean doSimpleCopy;
		final File phase4File =
				new File(phase4ResultDir, BuildSimLand._Phase4ResultBFileName);
		if (phase4IldsIn == null) {
			final GshhsReader gshhsReader = GshhsReader.constructGshhsReader(
					phase4File, GshhsReaderStatics._BareMinimumHeaderFilter);
			phase4 = gshhsReader.getAllSubLoops(_logger);
			phase4Ilds =
					LoopAndIldUtils.getIlds(phase4, /* destroyInput= */false);
			doSimpleCopy = true;
		} else {
			phase4Ilds = phase4IldsIn;
			phase4 = IldList.getLoops(phase4Ilds, /* destroyInput= */false);
			doSimpleCopy = doSimpleCopy0;
		}

		final CrossingPair2 xingPair = Loop3Statics.findCrossingPair(_logger,
				/* loopsName= */"Phase5InputCheck", phase4);
		assert xingPair == null : "Bad input for Phase5";
		_buildSimLand.LogTimeAndMemory("Phase 5-Read in and checked Phase4");
		GcaSequenceStatics.freeMemory(phase4, /* clearList= */true);
		phase4 = null;

		final int nResolutions = _ResolutionCharTargetProportions.length;
		final TreeMap<Double, Integer> scaleToNGcas = new TreeMap<>();

		for (int k = -1; k < nResolutions; ++k) {
			SizeOf.runGC(_logger);
			final String coreName;
			final char suffix;
			List<IntLoopData> toBuildAndDumpIlds;
			if (k < 0) {
				coreName = BuildSimLand._FullResolutionCoreName;
				suffix = coreName.charAt(0);
				/**
				 * Make a copy so we do not destroy phase4Ilds; we need that for
				 * subsequent resolutions.
				 */
				toBuildAndDumpIlds = new ArrayList<>(phase4Ilds);
			} else {
				final CharDouble suffixTargetProportion =
						_ResolutionCharTargetProportions[k];
				suffix = suffixTargetProportion._suffix;
				final double targetProportion =
						suffixTargetProportion._targetProportion;
				coreName = String.format("_%.3f%c", targetProportion, suffix);
				final String coreName0 = String.format("Simplifying%s", coreName);
				_logger.resetAppenders(_logDir, //
						/* dbgCoreName= */null, //
						/* outCoreName= */coreName0 + ".out", //
						/* wrnCoreName= */coreName0 + ".wrn", //
						/* errCoreName= */coreName0 + ".err", //
						"txt", /* append= */true);
				final IldList ildList = doPhase5Core(phase4Ilds, suffix,
						targetProportion, scaleToNGcas);
				toBuildAndDumpIlds = ildList.getIlds();
			}
			if (toBuildAndDumpIlds instanceof ArrayList) {
				((ArrayList<IntLoopData>) toBuildAndDumpIlds).trimToSize();
			}

			/** Dump the binary. */
			final String binaryFilePath;
			if (k < 0) {
				binaryFilePath = _fullResolutionBFile.getAbsolutePath();
				if (doSimpleCopy && phase4File.isFile()) {
					StaticUtilities.copyNonDirectoryFile(_logger,
							phase4File.getAbsolutePath(), binaryFilePath);
				} else {
					GshhsWriter.dumpIlds(toBuildAndDumpIlds, binaryFilePath);
				}
				/** Do the compare here. */
				final File redBFile = _buildSimLand._redBFile;
				if (redBFile != null && redBFile.isFile()) {
					final File limeBFile = _fullResolutionBFile;
					if (limeBFile != null && limeBFile.isFile()) {
						final File kmlFile = new File(_homeDir, "RedVsLime.kml");
						CompareGshhsFiles.compareGshhsFiles(_logger, redBFile,
								limeBFile, kmlFile);
					}
				}
			} else {
				_buildSimLand.LogTimeAndMemory(
						String.format("Phase 5-Simplified %s", coreName));
				/**
				 * Some Loops might now have gotten swallowed by a big concave
				 * neighbor. Hence, we set the enclosers to get rid of them.
				 */
				LoopAndIldUtils.convertIldListToZeroSubIds(0, toBuildAndDumpIlds);
				final ArrayList<Loop3> toBuildAndDump =
						IldList.getLoops(toBuildAndDumpIlds, /* destroyInput= */true);
				LoopsNester.setEnclosers(_logger, "Phase5 " + coreName,
						toBuildAndDump, /* topIsCw= */false, /* fixOrientation= */false,
						/* minLevel= */Loop3Statics._BaseLevelForLand);
				toBuildAndDumpIlds = LoopAndIldUtils.getIlds(toBuildAndDump,
						/* destroyInput= */true);
				final String binaryFileName =
						String.format("%s_%s.b", BuildSimLand._CoreResultName, suffix);
				binaryFilePath =
						new File(_resultDir, binaryFileName).getAbsolutePath();
				GshhsWriter.dumpIlds(toBuildAndDumpIlds, binaryFilePath);
			}

			/** Build the d file from the new disc file. */
			BuildDFiles.buildDFile(_logger, binaryFilePath);

			/** Write the ShpFiles. */
			final String shpFileName;
			if (k == -1) {
				shpFileName = String.format("%s-f", BuildSimLand._CoreResultName);
			} else {
				shpFileName =
						String.format("%s%s", BuildSimLand._CoreResultName, coreName);
			}
			final IldList ildList = new IldList(shpFileName, toBuildAndDumpIlds);
			ildList.writeToShpFiles(_logger, BuildSimLand._Phase5WritePolylines,
					_ShpFileWriter, _shpDir, /* clearIdlProblems= */true,
					/* writeAsSections= */false, /* nMaxInSection= */-1);
			LoopAndIldUtils.clearList(toBuildAndDumpIlds);
			_buildSimLand.LogTimeAndMemory(
					String.format("Phase 5-Dumped and shpFiled %s", coreName));
		}
		_buildSimLand.doPhase6();
	}

	private IldList doPhase5Core(final List<IntLoopData> phase4Ilds,
			final char suffix, final double targetProportion,
			final TreeMap<Double, Integer> scaleToNGcas) {
		SizeOf.runGC(_logger);

		final int nOrigGcas = new IldList("", phase4Ilds).getTotalNGcas();
		final double targetNGcas =
				Math.min(nOrigGcas, nOrigGcas * targetProportion);
		final int tooFew =
				(int) Math.floor(targetNGcas * (1d - _ProportionTolerance));
		final int tooMany =
				(int) Math.ceil(targetNGcas * (1d + _ProportionTolerance));

		double tooBigScale = Double.NaN;
		double tooSmallScale = Double.NaN;
		double bestScale = Double.NaN;
		double closestNEdges = Double.POSITIVE_INFINITY;
		boolean haveWinner = false;
		int minTooMany = Integer.MAX_VALUE;
		int maxTooFew = 0;
		for (final Map.Entry<Double, Integer> entry : scaleToNGcas.entrySet()) {
			final double scale = entry.getKey();
			final int nGcas = entry.getValue();
			final boolean haveTooFew = nGcas <= tooFew;
			final boolean haveTooMany = nGcas >= tooMany;
			if (haveWinner && (haveTooMany || haveTooFew)) {
				continue;
			}
			if (haveTooMany) {
				minTooMany = Math.min(minTooMany, nGcas);
				/** Since nGcas is too big, scale is too small. */
				if (Double.isNaN(tooSmallScale)) {
					tooSmallScale = scale;
				} else {
					tooSmallScale = Math.max(tooSmallScale, scale);
				}
				final int excess = nGcas - tooMany;
				if (excess < closestNEdges) {
					bestScale = scale;
					closestNEdges = excess;
				}
				continue;
			}
			if (haveTooFew) {
				maxTooFew = Math.max(maxTooFew, nGcas);
				/** Since nGcas is too small, scale is too big. */
				if (Double.isNaN(tooBigScale)) {
					tooBigScale = scale;
				} else {
					tooBigScale = Double.min(tooBigScale, scale);
				}
				final int shortage = tooFew - nGcas;
				if (shortage < closestNEdges) {
					bestScale = scale;
					closestNEdges = shortage;
				}
				continue;
			}
			/**
			 * We don't have tooMany and we don't have tooFew. We have a winner.
			 * Is it the best one?
			 */
			final double closeness = Math.abs(nGcas - targetNGcas);
			if (!haveWinner || closeness < closestNEdges) {
				haveWinner = true;
				closestNEdges = closeness;
				bestScale = scale;
			}
		}

		final double startingScale;
		if (haveWinner) {
			startingScale = bestScale;
		} else if (!Double.isNaN(tooSmallScale) && !Double.isNaN(tooBigScale)) {
			startingScale = (tooSmallScale + tooBigScale) / 2d;
		} else if (!Double.isNaN(tooBigScale)) {
			startingScale = tooBigScale / 2d;
		} else if (!Double.isNaN(tooSmallScale)) {
			startingScale = tooSmallScale * 2d;
		} else {
			startingScale = 1d;
		}

		double scale = startingScale;
		for (int k = 0;; ++k) {
			final Simplifier.ParamSet paramSet =
					_StandardParamSet.cloneAndScale(suffix, scale);
			final ArrayList<IntLoopData> newIlds =
					Simplifier.simplifyIlds(_logger, paramSet, phase4Ilds,
							/* shpFileWriter= */null, /* shpFileDir= */ null);
			final IldList ildList = new IldList("", newIlds);
			final int nGcasNew = ildList.getTotalNGcas();
			scaleToNGcas.put(scale, nGcasNew);
			final boolean haveTooFew = nGcasNew <= tooFew;
			final boolean haveTooMany = nGcasNew >= tooMany;
			_logger.out(String.format("\n%d. scale[%f] between %f and %f (%s)" //
					+ "\n\t%s→\n\t%s" //
					+ "\n\tTarget[%d,%d] Current[%d]", //
					k, scale, tooSmallScale, tooBigScale,
					haveTooMany ? "tooMany" : (haveTooFew ? "tooFew" : "inRange"), //
					_StandardParamSet.getString(), //
					paramSet.getString(), tooFew, tooMany, nGcasNew));
			if (!haveTooFew && !haveTooMany) {
				return ildList;
			}
			if (Math.abs(nGcasNew - targetNGcas) < closestNEdges) {
				bestScale = scale;
				closestNEdges = Math.abs(nGcasNew - targetNGcas);
			}
			if (k >= _MaxNTries) {
				final Simplifier.ParamSet finalParamSet =
						_StandardParamSet.cloneAndScale(suffix, bestScale);
				final ArrayList<IntLoopData> finalNewIlds =
						Simplifier.simplifyIlds(_logger, finalParamSet, phase4Ilds,
								/* shpFileWriter= */null, /** shpFileDir= */
								null);
				final IldList finalIldList = new IldList("", finalNewIlds);
				return finalIldList;
			}
			if (haveTooMany) {
				/** Since nGcasNew is too big, scale is too small. */
				tooSmallScale = scale;
				if (Double.isNaN(tooBigScale)) {
					scale *= 2d;
				} else {
					/**
					 * If this improved our previous "minTooMany," it's a standard
					 * binary search and we split the difference. Otherwise, go 3/4 of
					 * the way to tooBigScale.
					 */
					if (nGcasNew < minTooMany) {
						scale = 0.5 * tooSmallScale + 0.5 * tooBigScale;
						minTooMany = nGcasNew;
					} else {
						scale = 0.25 * tooSmallScale + 0.75 * tooBigScale;
					}
				}
			} else {
				/** Since nGcasNew is too small, scale is too big. */
				tooBigScale = scale;
				if (Double.isNaN(tooSmallScale)) {
					scale /= 2d;
				} else {
					/**
					 * If this improved our previous "maxTooFew," it's a standard
					 * binary search and we split the difference. Otherwise, go 3/4 of
					 * the way to tooSmallScale.
					 */
					if (nGcasNew > maxTooFew) {
						scale = 0.5 * tooSmallScale + 0.5 * tooBigScale;
						maxTooFew = nGcasNew;
					} else {
						scale = 0.75 * tooSmallScale + 0.25 * tooBigScale;
					}
				}
			}
		}
	}
}
