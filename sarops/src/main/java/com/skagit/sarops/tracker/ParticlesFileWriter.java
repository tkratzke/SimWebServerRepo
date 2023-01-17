package com.skagit.sarops.tracker;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.w3c.dom.Element;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.SearchObjectType;
import com.skagit.sarops.model.io.ModelWriter;
import com.skagit.sarops.simCaseManager.MainRunner;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.Snapshot.VarInfoOf3dStandardVars;
import com.skagit.sarops.util.SimGlobalStrings;
import com.skagit.util.CombinatoricTools;
import com.skagit.util.LsFormatter;
import com.skagit.util.StaticUtilities;
import com.skagit.util.StringUtilities;
import com.skagit.util.netCdfUtil.NetCdfUtil;

import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFileWriter;
import ucar.nc2.Variable;

@SuppressWarnings("deprecation")
public class ParticlesFileWriter {
	final private SimCaseManager.SimCase _simCase;
	final private String _particlesFilePath;
	final private ParticlesFile _particlesFile;

	final private String _fileContent;
	final private String _modelString;
	final private ArrayList<Snapshot> _snapshots;
	private int[] _timeIndexesToDump;
	private long[] _dumpTimes;

	public ParticlesFileWriter(final Tracker tracker) {
		_simCase = tracker.getSimCase();
		final Model model = tracker.getModel();
		_particlesFile = tracker.getParticlesFile();
		_particlesFilePath = model.getParticlesFilePath();
		final ModelWriter modelWriter = new ModelWriter();
		/**
		 * Create the Strings that will store the fileContent and the echoModel
		 * in the ParticlesFile.
		 */
		final String modelFilePath = model.getSimFilePath();
		_fileContent = StaticUtilities.getFileContent(modelFilePath);
		final Element modelEchoElement =
				modelWriter.createModelElement(_simCase, model);
		final LsFormatter lsFormatter = modelWriter.getFormatter();
		_modelString = lsFormatter.dump(modelEchoElement);
		_snapshots = new ArrayList<>();
		_timeIndexesToDump = null;
		_dumpTimes = null;
	}

	public void writeNetCdfFile(final int nChunksToReport) {
		final Model model = _particlesFile.getModel();
		final boolean writeOcTables = model.getWriteOcTables();
		final NetcdfFileWriter.Version[] versionValues =
				NetcdfFileWriter.Version.values();
		SimCaseManager.out(_simCase, "NetCdf versions (in order):");
		final int nVersionValues = versionValues.length;
		for (int k = 0; k < nVersionValues; ++k) {
			SimCaseManager.out(_simCase, versionValues[k].toString());
		}
		final SimGlobalStrings simGlobalStrings =
				_simCase.getSimGlobalStrings();
		final boolean storeMeans = simGlobalStrings.storeMeans();
		int nChunksReportedHere = 0;
		if (nChunksReportedHere < nChunksToReport) {
			_simCase.reportChunkDone();
			++nChunksReportedHere;
		}
		final long[] refSecsS = _particlesFile.getRefSecsS();
		final int nRefSecsS = refSecsS.length;
		final int nMonteCarloStepsPerDump = model.getNMonteCarloStepsPerDump();
		final int nTimeIndexesToDump =
				(nRefSecsS + 2 * nMonteCarloStepsPerDump - 2) /
						nMonteCarloStepsPerDump;
		final int nScenarii = model.getNScenarii();
		final int nParticlesPerScenario = model.getNParticlesPerScenario();
		final int nSearchObjectTypes = model.getNSearchObjectTypes();
		_timeIndexesToDump = new int[nTimeIndexesToDump];
		_dumpTimes = new long[nTimeIndexesToDump];
		for (int k = 0; k < nTimeIndexesToDump; ++k) {
			final int timeIndex =
					Math.min(k * nMonteCarloStepsPerDump, nRefSecsS - 1);
			_timeIndexesToDump[k] = timeIndex;
			_dumpTimes[k] = refSecsS[timeIndex];
		}

		try (NetcdfFileWriter netCdfFileWriter =
				NetcdfFileWriter.createNew(NetCdfUtil._NetCdfVersion,
						_particlesFilePath, /* chunker= */null)) {
			/** Create dimensions and sets of dimensions. */
			final Dimension timeDim = NetCdfUtil.createDimension(netCdfFileWriter,
					ParticlesFile._DimTime, nTimeIndexesToDump);
			final ArrayList<Dimension> timeDimList = new ArrayList<>(1);
			timeDimList.add(timeDim);
			final Variable timeVar = NetCdfUtil.addVariable(netCdfFileWriter,
					ParticlesFile._VarTime, NetCdfUtil.MyDataType.INT, timeDimList);
			if (timeVar != null) {
				NetCdfUtil.addVariableAttribute(netCdfFileWriter, timeVar, "units",
						"seconds since 01/01/00");
			}
			final Dimension stateVectorTypesDim;
			if (NetCdfUtil._NetCdfVersion == NetcdfFileWriter.Version.netcdf4) {
				stateVectorTypesDim = NetCdfUtil.createDimension(netCdfFileWriter,
						ParticlesFile._DimSvt, StateVectorType.values().length);
				final ArrayList<Dimension> stateVectorTypeList = new ArrayList<>(1);
				stateVectorTypeList.add(stateVectorTypesDim);
				NetCdfUtil.addVariable(netCdfFileWriter, ParticlesFile._VarSvt,
						NetCdfUtil.MyDataType.STRING, stateVectorTypeList);
			} else {
				stateVectorTypesDim = null;
			}
			/**
			 * Declare other Dimensions and then create combinations of
			 * Dimensions.
			 */
			final Dimension scenarioDim = NetCdfUtil.createDimension(
					netCdfFileWriter, ParticlesFile._DimScen, nScenarii);
			final Dimension particleDim =
					NetCdfUtil.createDimension(netCdfFileWriter,
							ParticlesFile._DimParticle, nParticlesPerScenario);
			final Dimension searchObjectTypeDim = NetCdfUtil.createDimension(
					netCdfFileWriter, ParticlesFile._DimSot, nSearchObjectTypes);
			final ArrayList<Dimension> timedDims = NetCdfUtil.createDimList(
					new Dimension[] { timeDim, scenarioDim, particleDim });
			final ArrayList<Dimension> untimedDims = NetCdfUtil
					.createDimList(new Dimension[] { scenarioDim, particleDim });
			final ArrayList<Dimension> timedDimsWithSot =
					NetCdfUtil.createDimList(new Dimension[] { timeDim, scenarioDim,
							searchObjectTypeDim });
			final ArrayList<Dimension> untimedDimsWithSot =
					NetCdfUtil.createDimList(
							new Dimension[] { scenarioDim, searchObjectTypeDim });
			final ArrayList<Dimension> scenarioOnly =
					NetCdfUtil.createDimList(new Dimension[] { scenarioDim });
			/** Add the non-standard variables. */
			NetCdfUtil.addVariable(netCdfFileWriter,
					ParticlesFile._VarScenOrdinal, NetCdfUtil.MyDataType.SHORT,
					scenarioOnly);
			NetCdfUtil.addVariable(netCdfFileWriter, ParticlesFile._VarScenWeight,
					NetCdfUtil.MyDataType.FLOAT, scenarioOnly);
			NetCdfUtil.addVariable(netCdfFileWriter,
					ParticlesFile._VarAnchoringTime, NetCdfUtil.MyDataType.INT,
					untimedDims);
			NetCdfUtil.addVariable(netCdfFileWriter, ParticlesFile._VarBirthTime,
					NetCdfUtil.MyDataType.INT, untimedDims);
			NetCdfUtil.addVariable(netCdfFileWriter,
					ParticlesFile._VarLandingTime, NetCdfUtil.MyDataType.INT,
					untimedDims);
			NetCdfUtil.addVariable(netCdfFileWriter,
					ParticlesFile._VarDistressTime, NetCdfUtil.MyDataType.INT,
					untimedDims);
			NetCdfUtil.addVariable(netCdfFileWriter,
					ParticlesFile._VarExpirationTime, NetCdfUtil.MyDataType.INT,
					untimedDims);
			NetCdfUtil.addVariable(netCdfFileWriter,
					ParticlesFile._VarDistressType, NetCdfUtil.MyDataType.INT,
					untimedDims);
			NetCdfUtil.addVariable(netCdfFileWriter,
					ParticlesFile._VarUnderwayTypeName, NetCdfUtil.MyDataType.INT,
					untimedDims);
			NetCdfUtil.addVariable(netCdfFileWriter, ParticlesFile._VarInitPrior0,
					NetCdfUtil.MyDataType.FLOAT, untimedDims);
			NetCdfUtil.addVariable(netCdfFileWriter,
					ParticlesFile._VarDistressLat, NetCdfUtil.MyDataType.FLOAT,
					untimedDims);
			NetCdfUtil.addVariable(netCdfFileWriter,
					ParticlesFile._VarDistressLng, NetCdfUtil.MyDataType.FLOAT,
					untimedDims);
			if (model.hasSailData()) {
				NetCdfUtil.addVariable(netCdfFileWriter,
						ParticlesFile._VarSailorQuality, NetCdfUtil.MyDataType.FLOAT,
						untimedDims);
				NetCdfUtil.addVariable(netCdfFileWriter,
						ParticlesFile._VarSailorForbiddenAngleIncrease,
						NetCdfUtil.MyDataType.FLOAT, untimedDims);
				NetCdfUtil.addVariable(netCdfFileWriter,
						ParticlesFile._VarSailorSpeedMultiplier,
						NetCdfUtil.MyDataType.FLOAT, untimedDims);
				NetCdfUtil.addVariable(netCdfFileWriter,
						ParticlesFile._VarSailorZeroZeroMotor,
						NetCdfUtil.MyDataType.SHORT, untimedDims);
			}

			/**
			 * Add the standard variables; those that are indexed by time,
			 * scenario, and particle-within-scenario. These correspond to the
			 * fields within Snapshot.
			 */
			final Iterator<VarInfoOf3dStandardVars> it0 =
					Snapshot._SnapshotVarInfos.iterator();
			while (it0.hasNext()) {
				final VarInfoOf3dStandardVars varInfo = it0.next();
				if (!varInfo._oc || writeOcTables) {
					NetCdfUtil.addVariable(netCdfFileWriter, varInfo._varName,
							varInfo._myDataType, timedDims);
				}
			}
			if (storeMeans) {
				NetCdfUtil.addVariable(netCdfFileWriter, ParticlesFile._VarMeanLat,
						NetCdfUtil.MyDataType.FLOAT, timedDimsWithSot);
				NetCdfUtil.addVariable(netCdfFileWriter, ParticlesFile._VarMeanLng,
						NetCdfUtil.MyDataType.FLOAT, timedDimsWithSot);
				/** The following means are not in snapshots. */
				NetCdfUtil.addVariable(netCdfFileWriter,
						ParticlesFile._VarMeanBirthTime, NetCdfUtil.MyDataType.INT,
						untimedDimsWithSot);
				NetCdfUtil.addVariable(netCdfFileWriter,
						ParticlesFile._VarMeanLandingTime, NetCdfUtil.MyDataType.INT,
						untimedDimsWithSot);
			}
			/** Add the global attributes. */
			try {
				NetCdfUtil.createGlobalAttribute(netCdfFileWriter,
						ParticlesFile._FileContentAttribute, _fileContent);
				NetCdfUtil.createGlobalAttribute(netCdfFileWriter,
						ParticlesFile._ModelStringAttribute, _modelString);
				NetCdfUtil.createGlobalAttribute(netCdfFileWriter,
						ParticlesFile._StateVectorTypesAttribute,
						StateVectorType._StateVectorTypesString);
				NetCdfUtil.createGlobalAttribute(netCdfFileWriter,
						ParticlesFile._StateVectorTypeColorsAttribute,
						StateVectorType._StateVectorColorsString);
			} catch (final Exception e) {
				SimCaseManager.err(_simCase,
						"Error encoding Misc strings for NetCdfFile");
			}
			/**
			 * The structure and comments are set up. "Create" the Writer and then
			 * populate it.
			 */
			try {
				netCdfFileWriter.create();
			} catch (final Exception e) {
				throw new RuntimeException(e);
			}

			final Object lock = getClass();
			synchronized (lock) {
				final int[] refSecsAsInts = getRefSecsSAsInts();
				final int[] refSecsSToDump = new int[nTimeIndexesToDump];
				for (int k = 0; k < nTimeIndexesToDump; ++k) {
					refSecsSToDump[k] = refSecsAsInts[_timeIndexesToDump[k]];
				}
				final short[] scenarioIds = new short[nScenarii];
				final float[] scenarioWeights = new float[nScenarii];
				for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
					scenarioIds[iScenario] =
							(short) model.getScenario(iScenario).getId();
					scenarioWeights[iScenario] =
							(float) model.getScenario(iScenario).getScenarioWeight();
				}
				NetCdfUtil.write1DInt(netCdfFileWriter, ParticlesFile._VarTime,
						refSecsSToDump);
				NetCdfUtil.write1DShort(netCdfFileWriter,
						ParticlesFile._VarScenOrdinal, scenarioIds);
				NetCdfUtil.write1DFloat(netCdfFileWriter,
						ParticlesFile._VarScenWeight, scenarioWeights);
				NetCdfUtil.write2DInt(netCdfFileWriter,
						ParticlesFile._VarDistressTime, getDistressSecsS());
				final float[][][] distressLatsAndDistressLngs =
						getDistressLatsAndDistressLngs();
				NetCdfUtil.write2DFloat(netCdfFileWriter,
						ParticlesFile._VarDistressLat, distressLatsAndDistressLngs[0]);
				NetCdfUtil.write2DFloat(netCdfFileWriter,
						ParticlesFile._VarDistressLng, distressLatsAndDistressLngs[1]);
				NetCdfUtil.write2DInt(netCdfFileWriter,
						ParticlesFile._VarAnchoringTime, getAnchoringRefSecsS());
				NetCdfUtil.write2DInt(netCdfFileWriter, ParticlesFile._VarBirthTime,
						getBirthRefSecsS());
				NetCdfUtil.write2DInt(netCdfFileWriter,
						ParticlesFile._VarLandingTime, getLandingRefSecsS());
				NetCdfUtil.write2DInt(netCdfFileWriter,
						ParticlesFile._VarDistressType, getDistressTypes());
				NetCdfUtil.write2DInt(netCdfFileWriter,
						ParticlesFile._VarExpirationTime, getExpirationRefSecsS());
				NetCdfUtil.write2DInt(netCdfFileWriter,
						ParticlesFile._VarUnderwayTypeName, getUnderwayTypes());
				NetCdfUtil.write2DFloat(netCdfFileWriter,
						ParticlesFile._VarInitPrior0, getOriginalPriors());
				if (model.hasSailData()) {
					final float[][] sailorQualities =
							new float[nScenarii][nParticlesPerScenario];
					final float[][] sailorForbiddenAngleIncreases =
							new float[nScenarii][nParticlesPerScenario];
					final float[][] sailorSpeedMultipliers =
							new float[nScenarii][nParticlesPerScenario];
					final boolean[][] sailorZeroZeroMotors =
							new boolean[nScenarii][nParticlesPerScenario];
					for (int k0 = 0; k0 < nScenarii; ++k0) {
						for (int k1 = 0; k1 < nParticlesPerScenario; ++k1) {
							final ParticleIndexes prtclIndxs =
									ParticleIndexes.getStandardOne(model, k0, k1);
							sailorQualities[k0][k1] =
									_particlesFile.getSailorQuality(prtclIndxs);
							sailorForbiddenAngleIncreases[k0][k1] = _particlesFile
									.getSailorForbiddenAngleIncrease(prtclIndxs);
							sailorSpeedMultipliers[k0][k1] =
									_particlesFile.getSailorSpeedMultiplier(prtclIndxs);
							sailorZeroZeroMotors[k0][k1] =
									_particlesFile.getSailorIsZeroZeroMotor(prtclIndxs);
						}
					}
					NetCdfUtil.write2DFloat(netCdfFileWriter,
							ParticlesFile._VarSailorQuality, sailorQualities);
					NetCdfUtil.write2DFloat(netCdfFileWriter,
							ParticlesFile._VarSailorForbiddenAngleIncrease,
							sailorForbiddenAngleIncreases);
					NetCdfUtil.write2DFloat(netCdfFileWriter,
							ParticlesFile._VarSailorSpeedMultiplier,
							sailorSpeedMultipliers);
					NetCdfUtil.write2DBool(netCdfFileWriter,
							ParticlesFile._VarSailorZeroZeroMotor, sailorZeroZeroMotors);
				}
				final StateVectorType[] stateVectorTypes = StateVectorType.values();
				final int nStateVectorTypes = stateVectorTypes.length;
				final String[] stateVectorTypeNames = new String[nStateVectorTypes];
				for (int k = 0; k < nStateVectorTypes; ++k) {
					stateVectorTypeNames[k] = stateVectorTypes[k].name();
				}
				NetCdfUtil.write1DString(netCdfFileWriter, ParticlesFile._VarSvt,
						stateVectorTypeNames);
			}

			/** Snapshot arrays. */
			final Iterator<VarInfoOf3dStandardVars> it1 =
					Snapshot._SnapshotVarInfos.iterator();
			while (it1.hasNext()) {
				final VarInfoOf3dStandardVars varInfo = it1.next();
				if (!varInfo._oc || writeOcTables) {
					writeSnapshotArray(model, netCdfFileWriter, varInfo);
				}
			}
			if (storeMeans) {
				/**
				 * We cannot appeal to VarInfoOf3dStandardVars for the names here,
				 * since the dimensions are different than
				 * time/scenario/particle-within-scenario..
				 */
				writeSnapshotArray(model, netCdfFileWriter,
						ParticlesFile._VarMeanLat, "_meanLat");
				writeSnapshotArray(model, netCdfFileWriter,
						ParticlesFile._VarMeanLng, "_meanLng");
				NetCdfUtil.write2DInt(netCdfFileWriter,
						ParticlesFile._VarMeanBirthTime, getMeanBirthRefSecsS());
				NetCdfUtil.write2DInt(netCdfFileWriter,
						ParticlesFile._VarMeanLandingTime, getMeanLandingRefSecsS());
			}
		} catch (final Exception e1) {
			e1.printStackTrace();
			MainRunner.HandleFatal(_simCase, new RuntimeException(e1));
		}
		if (nChunksReportedHere < nChunksToReport) {
			_simCase.reportChunkDone();
			++nChunksReportedHere;
		}
	}

	/** Creating java arrays from _particlesFile. */
	private int[][] getUnderwayTypes() {
		final Model model = _particlesFile.getModel();
		final int nScenarii = model.getNScenarii();
		final int nParticlesPerScenario = model.getNParticlesPerScenario();
		final int[][] underwayTypes = new int[nScenarii][nParticlesPerScenario];
		for (int k0 = 0; k0 < nScenarii; ++k0) {
			for (int k1 = 0; k1 < nParticlesPerScenario; ++k1) {
				final ParticleIndexes prtclIndxs =
						ParticleIndexes.getStandardOne(model, k0, k1);
				final int underwayType = _particlesFile.getUnderwayType(prtclIndxs);
				underwayTypes[k0][k1] = underwayType;
			}
		}
		return underwayTypes;
	}

	private int[][] getDistressTypes() {
		final Model model = _particlesFile.getModel();
		final int nScenarii = model.getNScenarii();
		final int nParticlesPerScenario = model.getNParticlesPerScenario();
		final int[][] distressTypes = new int[nScenarii][nParticlesPerScenario];
		for (int k0 = 0; k0 < nScenarii; ++k0) {
			for (int k1 = 0; k1 < nParticlesPerScenario; ++k1) {
				final ParticleIndexes prtclIndxs =
						ParticleIndexes.getStandardOne(model, k0, k1);
				final int distressType = _particlesFile.getDistressType(prtclIndxs);
				distressTypes[k0][k1] = distressType;
			}
		}
		return distressTypes;
	}

	private int[][] getAnchoringRefSecsS() {
		final Model model = _particlesFile.getModel();
		final int nScenarii = model.getNScenarii();
		final int nParticlesPerScenario = model.getNParticlesPerScenario();
		final int[][] anchoringRefSecsS =
				new int[nScenarii][nParticlesPerScenario];
		for (int k0 = 0; k0 < nScenarii; ++k0) {
			for (int k1 = 0; k1 < nParticlesPerScenario; ++k1) {
				final ParticleIndexes prtclIndxs =
						ParticleIndexes.getStandardOne(model, k0, k1);
				final int anchoringRefSecs =
						truncateRefSecs(_particlesFile.getAnchoringRefSecs(prtclIndxs));
				anchoringRefSecsS[k0][k1] = anchoringRefSecs;
			}
		}
		return anchoringRefSecsS;
	}

	private int[][] getBirthRefSecsS() {
		final Model model = _particlesFile.getModel();
		final int nScenarii = model.getNScenarii();
		final int nParticlesPerScenario = model.getNParticlesPerScenario();
		final int[][] birthRefSecsS = new int[nScenarii][nParticlesPerScenario];
		for (int k0 = 0; k0 < nScenarii; ++k0) {
			for (int k1 = 0; k1 < nParticlesPerScenario; ++k1) {
				final ParticleIndexes prtclIndxs =
						ParticleIndexes.getStandardOne(model, k0, k1);
				final int birthRefSecs =
						truncateRefSecs(_particlesFile.getBirthRefSecs(prtclIndxs));
				birthRefSecsS[k0][k1] = birthRefSecs;
			}
		}
		return birthRefSecsS;
	}

	private int[][] getLandingRefSecsS() {
		final Model model = _particlesFile.getModel();
		final int nScenarii = model.getNScenarii();
		final int nParticlesPerScenario = model.getNParticlesPerScenario();
		final int[][] landingRefSecsS =
				new int[nScenarii][nParticlesPerScenario];
		for (int k0 = 0; k0 < nScenarii; ++k0) {
			for (int k1 = 0; k1 < nParticlesPerScenario; ++k1) {
				final ParticleIndexes prtclIndxs =
						ParticleIndexes.getStandardOne(model, k0, k1);
				final int landingRefSecs =
						truncateRefSecs(_particlesFile.getLandingRefSecs(prtclIndxs));
				landingRefSecsS[k0][k1] = landingRefSecs;
			}
		}
		return landingRefSecsS;
	}

	private int[][] getDistressSecsS() {
		final Model model = _particlesFile.getModel();
		final int nScenarii = model.getNScenarii();
		final int nParticlesPerScenario = model.getNParticlesPerScenario();
		final int[][] distressRefSecsS =
				new int[nScenarii][nParticlesPerScenario];
		for (int k0 = 0; k0 < nScenarii; ++k0) {
			for (int k1 = 0; k1 < nParticlesPerScenario; ++k1) {
				final ParticleIndexes prtclIndxs =
						ParticleIndexes.getStandardOne(model, k0, k1);
				final long distressRefSecsL =
						_particlesFile.getDistressRefSecs(prtclIndxs);
				final int distressRefSecs = truncateRefSecs(distressRefSecsL);
				distressRefSecsS[k0][k1] = distressRefSecs;
			}
		}
		return distressRefSecsS;
	}

	private static int truncateRefSecs(final long refSecs) {
		final int intRefSecs =
				refSecs > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) refSecs;
		return intRefSecs;
	}

	private int[][] getExpirationRefSecsS() {
		final Model model = _particlesFile.getModel();
		final int nScenarii = model.getNScenarii();
		final int nParticlesPerScenario = model.getNParticlesPerScenario();
		final int[][] expirationRefSecsS =
				new int[nScenarii][nParticlesPerScenario];
		for (int k0 = 0; k0 < nScenarii; ++k0) {
			for (int k1 = 0; k1 < nParticlesPerScenario; ++k1) {
				final ParticleIndexes prtclIndxs =
						ParticleIndexes.getStandardOne(model, k0, k1);
				final int expirationRefSecs = truncateRefSecs(
						_particlesFile.getExpirationRefSecs(prtclIndxs));
				expirationRefSecsS[k0][k1] = expirationRefSecs;
			}
		}
		return expirationRefSecsS;
	}

	private float[][] getOriginalPriors() {
		final Model model = _particlesFile.getModel();
		final int nScenarii = model.getNScenarii();
		final int nParticlesPerScenario = model.getNParticlesPerScenario();
		final float[][] originalPriors =
				new float[nScenarii][nParticlesPerScenario];
		for (int k0 = 0; k0 < nScenarii; ++k0) {
			for (int k1 = 0; k1 < nParticlesPerScenario; ++k1) {
				final ParticleIndexes prtclIndxs =
						ParticleIndexes.getStandardOne(model, k0, k1);
				final float originalPrior = _particlesFile.getInitPrior(prtclIndxs);
				originalPriors[k0][k1] = originalPrior;
			}
		}
		return originalPriors;
	}

	private float[][][] getDistressLatsAndDistressLngs() {
		final Model model = _particlesFile.getModel();
		final int nScenarii = model.getNScenarii();
		final int nParticlesPerScenario = model.getNParticlesPerScenario();
		final float[][][] distressLatsAndDistressLngs =
				new float[2][nScenarii][nParticlesPerScenario];
		for (int k0 = 0; k0 < nScenarii; ++k0) {
			for (int k1 = 0; k1 < nParticlesPerScenario; ++k1) {
				final ParticleIndexes prtclIndxs =
						ParticleIndexes.getStandardOne(model, k0, k1);
				final double[] distressLatLngPair =
						_particlesFile.getDistressLatLngPair(prtclIndxs);
				distressLatsAndDistressLngs[0][k0][k1] =
						(float) distressLatLngPair[0];
				distressLatsAndDistressLngs[1][k0][k1] =
						(float) distressLatLngPair[1];
			}
		}
		return distressLatsAndDistressLngs;
	}

	/** Getters for the means. */
	private int[][] getMeanBirthRefSecsS() {
		final Model model = _particlesFile.getModel();
		final int nScenarii = model.getNScenarii();
		final Collection<SearchObjectType> searchObjectTypes =
				model.getSearchObjectTypes();
		final int nSearchObjectTypes = searchObjectTypes.size();
		final int[][] meanBirthRefSecsS =
				new int[nScenarii][nSearchObjectTypes];
		for (int k0 = 0; k0 < nScenarii; ++k0) {
			for (final SearchObjectType searchObjectType : searchObjectTypes) {
				final int searchObjectTypeId = searchObjectType.getId();
				final int k1 = model.getSotOrd(searchObjectTypeId);
				final ParticleIndexes prtclIndxs =
						ParticleIndexes.getMeanOne(model, k0, k1);
				final int meanBirthRefSecs =
						truncateRefSecs(_particlesFile.getBirthRefSecs(prtclIndxs));
				meanBirthRefSecsS[k0][k1] = meanBirthRefSecs;
			}
		}
		return meanBirthRefSecsS;
	}

	private int[][] getMeanLandingRefSecsS() {
		final Model model = _particlesFile.getModel();
		final int nScenarii = model.getNScenarii();
		final Collection<SearchObjectType> searchObjectTypes =
				model.getSearchObjectTypes();
		final int nSearchObjectTypes = searchObjectTypes.size();
		final int[][] meanLandingRefSecsS =
				new int[nScenarii][nSearchObjectTypes];
		for (int k0 = 0; k0 < nScenarii; ++k0) {
			for (final SearchObjectType searchObjectType : searchObjectTypes) {
				final int searchObjectTypeId = searchObjectType.getId();
				final int k1 = model.getSotOrd(searchObjectTypeId);
				final ParticleIndexes prtclIndxs =
						ParticleIndexes.getMeanOne(model, k0, k1);
				final int meanLandingRefSecs =
						truncateRefSecs(_particlesFile.getLandingRefSecs(prtclIndxs));
				meanLandingRefSecsS[k0][k1] = meanLandingRefSecs;
			}
		}
		return meanLandingRefSecsS;
	}

	/** Utilities. */
	private int[] getRefSecsSAsInts() {
		final long[] refSecsS = _particlesFile.getRefSecsS();
		final int nRefSecsS = refSecsS.length;
		final int[] intRefSecsS = new int[nRefSecsS];
		for (int iTime = 0; iTime < nRefSecsS; ++iTime) {
			intRefSecsS[iTime] = (int) refSecsS[iTime];
		}
		return intRefSecsS;
	}

	private void writeSnapshotArray(final Model model,
			final NetcdfFileWriter netCdfFileWriter,
			final VarInfoOf3dStandardVars varInfo) throws RuntimeException {
		if (_snapshots.isEmpty()) {
			return;
		}
		final Snapshot template = _snapshots.get(0);
		if (!template.haveOcData() && varInfo._oc) {
			return;
		}
		writeSnapshotArray(model, netCdfFileWriter, varInfo._varName,
				varInfo._fieldName);
	}

	private void writeSnapshotArray(final Model model,
			final NetcdfFileWriter netCdfFileWriter, final String varName,
			final String fieldName) throws RuntimeException {
		if (_snapshots.isEmpty()) {
			return;
		}
		RuntimeException e = null;
		final int nTimesToDump = _timeIndexesToDump.length;
		final Snapshot template = _snapshots.get(0);
		final Class<? extends Snapshot> snapshotClass = template.getClass();
		try {
			final Field field = snapshotClass.getDeclaredField(fieldName);
			final String fieldTypeName = field.getType().getName();
			final boolean isFloatArray = fieldTypeName.contains("[F");
			if (isFloatArray) {
				final float[][][] fullData = new float[nTimesToDump][][];
				for (int k0 = 0; k0 < nTimesToDump; ++k0) {
					final int timeIndex = _timeIndexesToDump[k0];
					final Snapshot snapshot = _snapshots.get(timeIndex);
					fullData[k0] = (float[][]) field.get(snapshot);
				}
				NetCdfUtil.write3DFloat(netCdfFileWriter, varName, fullData);
				return;
			}
			final boolean isIntArray = fieldTypeName.contains("[I");
			if (isIntArray) {
				final int[][][] fullData = new int[nTimesToDump][][];
				for (int iT = 0; iT < nTimesToDump; ++iT) {
					final int timeIndex = _timeIndexesToDump[iT];
					final Snapshot snapshot = _snapshots.get(timeIndex);
					fullData[iT] = (int[][]) field.get(snapshot);
				}
				NetCdfUtil.write3DInt(netCdfFileWriter, varName, fullData);
				return;
			}
		} catch (final NoSuchFieldException eX) {
			e = new RuntimeException(eX);
		} catch (final SecurityException eX) {
			e = eX;
		} catch (final IllegalArgumentException eX) {
			e = eX;
		} catch (final IllegalAccessException eX) {
			e = new RuntimeException(eX);
		} catch (final Exception eX) {
			e = new RuntimeException(eX);
		}
		if (e != null) {
			SimCaseManager.err(_simCase, String.format(
					"Error %s while writing variable[%s]\nStackTrace:\n%s",
					e.getMessage(), varName, StringUtilities.getStackTraceString(e)));
			throw e;
		}
	}

	public void buildSnapshot(final long simSecs) {
		final SimGlobalStrings simGlobalStrings =
				_simCase.getSimGlobalStrings();
		final Model model = _particlesFile.getModel();
		final int nScenarii = model.getNScenarii();
		final int nParticlesPerScenario = model.getNParticlesPerScenario();
		final int nSearchObjectTypes = model.getNSearchObjectTypes();
		final Snapshot currentSnapshot = new Snapshot(_simCase, nScenarii,
				nParticlesPerScenario, nSearchObjectTypes);
		final long[] refSecsS = _particlesFile.getRefSecsS();
		final long refSecs = model.getRefSecs(simSecs);
		int timeIdx;
		if (model.getReverseDrift()) {
			timeIdx = CombinatoricTools.getLubIndex(refSecsS, (int) refSecs);
			while (_snapshots.size() < timeIdx + 1) {
				_snapshots.add(null);
			}
			_snapshots.set(timeIdx, currentSnapshot);
		} else {
			timeIdx = CombinatoricTools.getGlbIndex(refSecsS, (int) refSecs);
			_snapshots.add(currentSnapshot);
		}

		float normalizationFactor = 0f;
		for (int iPass = 0; iPass < 2; ++iPass) {
			for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
				for (int iParticle = 0; iParticle < nParticlesPerScenario;
						++iParticle) {
					final ParticleIndexes prtclIndxs =
							ParticleIndexes.getStandardOne(model, iScenario, iParticle);
					final float probability =
							_particlesFile.getProbability(refSecs, prtclIndxs);
					if (iPass == 0) {
						normalizationFactor += probability;
					} else {
						final double[] latLngPair =
								_particlesFile.getLatLngPair(timeIdx, prtclIndxs);
						final float pFail =
								_particlesFile.getCumPFail(refSecs, prtclIndxs);
						currentSnapshot._lat[iScenario][iParticle] =
								(float) latLngPair[0];
						currentSnapshot._lng[iScenario][iParticle] =
								(float) latLngPair[1];
						if (normalizationFactor == 0f) {
							currentSnapshot._probability[iScenario][iParticle] =
									1f / (nScenarii * nParticlesPerScenario);
						} else {
							currentSnapshot._probability[iScenario][iParticle] =
									probability / normalizationFactor;
						}
						currentSnapshot._cumPFail[iScenario][iParticle] = pFail;
					}
				}
			}
		}
		for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
			for (int iParticle = 0; iParticle < nParticlesPerScenario;
					++iParticle) {
				final ParticleIndexes prtclIndxs =
						ParticleIndexes.getStandardOne(model, iScenario, iParticle);
				final int stateVectorTypeOrdinal =
						_particlesFile.getSvtFromTimeIdx(timeIdx, prtclIndxs).ordinal();
				currentSnapshot._svtOrdinal[iScenario][iParticle] =
						stateVectorTypeOrdinal;
			}
		}
		if (simGlobalStrings.storeMeans()) {
			for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
				for (int sotOrd = 0; sotOrd < nSearchObjectTypes; ++sotOrd) {
					final ParticleIndexes prtclIndxs =
							ParticleIndexes.getMeanOne(model, iScenario, sotOrd);
					final double[] meanLatLngPair =
							_particlesFile.getLatLngPair(timeIdx, prtclIndxs);
					final double meanLat = meanLatLngPair[0];
					final double meanLng = meanLatLngPair[1];
					currentSnapshot._meanLat[iScenario][sotOrd] = (float) meanLat;
					currentSnapshot._meanLng[iScenario][sotOrd] = (float) meanLng;
				}
			}
		}
		/** For Oc. */
		if (!currentSnapshot.haveOcData()) {
			return;
		}
		for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
			for (int iParticle = 0; iParticle < nParticlesPerScenario;
					++iParticle) {
				final ParticleIndexes prtclIndxs =
						ParticleIndexes.getStandardOne(model, iScenario, iParticle);
				final float ocLat = _particlesFile.getOcLat(timeIdx, prtclIndxs);
				currentSnapshot._ocLat[iScenario][iParticle] = ocLat;
				final float ocLng = _particlesFile.getOcLng(timeIdx, prtclIndxs);
				currentSnapshot._ocLng[iScenario][iParticle] = ocLng;
				final float ocEastDnWind =
						_particlesFile.getOcEastDnWind(timeIdx, prtclIndxs);
				currentSnapshot._ocEastDnWind[iScenario][iParticle] = ocEastDnWind;
				final float ocNorthDnWind =
						_particlesFile.getOcNorthDnWind(timeIdx, prtclIndxs);
				currentSnapshot._ocNorthDnWind[iScenario][iParticle] =
						ocNorthDnWind;
				currentSnapshot._ocWindSpd[iScenario][iParticle] =
						(float) Math.sqrt(ocEastDnWind * ocEastDnWind +
								ocNorthDnWind * ocNorthDnWind);
				final float ocEastDnCurrent =
						_particlesFile.getOcEastDnCurrent(timeIdx, prtclIndxs);
				currentSnapshot._ocEastDnCurrent[iScenario][iParticle] =
						ocEastDnCurrent;
				final float ocNorthDnCurrent =
						_particlesFile.getOcNorthDnCurrent(timeIdx, prtclIndxs);
				currentSnapshot._ocNorthDnCurrent[iScenario][iParticle] =
						ocNorthDnCurrent;
				currentSnapshot._ocCurrentSpd[iScenario][iParticle] =
						(float) Math.sqrt(ocEastDnCurrent * ocEastDnCurrent +
								ocNorthDnCurrent * ocNorthDnCurrent);
				final float ocEastBoat =
						_particlesFile.getOcEastBoat(timeIdx, prtclIndxs);
				currentSnapshot._ocEastBoat[iScenario][iParticle] = ocEastBoat;
				final float ocNorthBoat =
						_particlesFile.getOcNorthBoat(timeIdx, prtclIndxs);
				currentSnapshot._ocNorthBoat[iScenario][iParticle] = ocNorthBoat;
				currentSnapshot._ocBoatSpd[iScenario][iParticle] = (float) Math
						.sqrt(ocEastBoat * ocEastBoat + ocNorthBoat * ocNorthBoat);
				final int ocSvtOrdinal =
						_particlesFile.getOcSvtOrdinal(timeIdx, prtclIndxs);
				currentSnapshot._ocSvtOrdinal[iScenario][iParticle] = ocSvtOrdinal;
			}
		}
	}
}
