package com.skagit.sarops.tracker;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.SearchObjectType;
import com.skagit.sarops.model.io.ModelReader;
import com.skagit.sarops.model.preDistressModel.PreDistressModel.Itinerary;
import com.skagit.sarops.model.preDistressModel.VoyageItinerary;
import com.skagit.sarops.model.preDistressModel.sail.Sdi;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.util.SimGlobalStrings;
import com.skagit.util.CombinatoricTools;
import com.skagit.util.Constants;
import com.skagit.util.DirsTracker;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.StringUtilities;
import com.skagit.util.TimeUtilities;
import com.skagit.util.navigation.GreatCircleCalculator;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.MotionType;
import com.skagit.util.navigation.NavigationCalculatorStatics;
import com.skagit.util.navigation.TangentCylinder;
import com.skagit.util.netCdfUtil.NetCdfUtil;
import com.skagit.util.randomx.WeightedPairReDataAcc;

import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;

@SuppressWarnings("deprecation")
public class ParticlesFile {

	/**
	 * <pre>
	 *   _FileContentAttribute MUST be "model." Outside programs look for this attribute.
	 * Moreover, the outside programs cannot necessarily read any xml, so we reproduce
	 * the exact file content in this attribute.
	 *   _ModelStringAttribute can be anything.  echoModel is an xml version of the model,
	 * after it has been read in and processed.
	 * </pre>
	 */
	final public static String _FileContentAttribute = "model";
	final public static String _ModelStringAttribute = "simEchoModel";
	final public static String _OldModelStringAttribute = "xmlmodel";
	final public static String _StateVectorTypesAttribute = "stateVectorTypesString";
	final public static String _StateVectorTypeColorsAttribute = "stateVectorTypeColorsString";

	/** Names of dimensions: */
	final public static String _DimTime = "time";
	final public static String _DimScen = "scenario";
	final public static String _DimParticle = "particle";
	final public static String _DimSot = "sot";
	final public static String _DimSvt = "svt";

	/** int var, indexed by _DimTime: */
	final public static String _VarTime = "time";

	/** string var, indexed by _DimSvt: */
	final public static String _VarSvt = "svts";

	/** float vars, indexed by _DimTime, _DimScen, and _DimParticle: */
	final public static String _VarLat = "lat";
	final public static String _VarLng = "lng";
	final public static String _VarProbability = "probability";
	final public static String _VarPFail = "pFail";

	/** int var, indexed by _DimScen: */
	final public static String _VarScenOrdinal = "scenarioOrdinal";

	/** float var, indexed by _DimScen: */
	final public static String _VarScenWeight = "scenarioWeight";

	/** int vars, indexed by _DimScen and _DimParticle: */
	final public static String _VarAnchoringTime = "anchoringTime";
	final public static String _VarBirthTime = "birthTime";
	final public static String _VarLandingTime = "landingTime";
	final public static String _VarDistressTime = "distressTime";
	final public static String _VarExpirationTime = "expirationTime";
	final public static String _VarUnderwayTypeName = "underwayType";
	final public static String _VarDistressType = "distressType";

	/** float vars, indexed by _DimScen and _DimParticle: */
	final public static String _VarInitPrior0 = "initPrior";
	final public static String _VarInitPrior1 = "originalPrior";
	final public static String _VarDistressLat = "distressLat";
	final public static String _VarDistressLng = "distressLng";

	/** int var, indexed by _DimTime, _DimScen, and _DimParticle: */
	final public static String _VarSvtOrd = "svtOrdinal";
	final public static String _OldVarSvtOrd = "stateVectorTypeOrdinal";

	/**
	 * (Means, not used by SAROPS) int vars, indexed by _DimScen and _DimSot:
	 */
	final public static String _VarMeanBirthTime = "meanBirthTime";
	final public static String _VarMeanLandingTime = "meanLandingTime";

	/**
	 * (Means, not used by SAROPS) float vars, indexed by _DimTime, _DimScen and
	 * _DimSot:
	 */
	final public static String _VarMeanLat = "meanLat";
	final public static String _VarMeanLng = "meanLng";

	/** (If S/V case) float vars, indexed by _DimScen and _DimParticle: */
	final public static String _VarSailorQuality = "sailorQualities";
	final public static String _VarSailorForbiddenAngleIncrease = "sailorForbiddenAngleIncrease";
	final public static String _VarSailorSpeedMultiplier = "sailorSpeedMultiplier";
	final public static String _VarSailorZeroZeroMotor = "zeroZeroMotor";

	/**
	 * (If writeOcTables) float vars, indexed by _DimTime, _DimScen, and
	 * _DimParticle:
	 */
	final public static String _VarOcLat = "ocLat";
	final public static String _VarOcLng = "ocLng";
	final public static String _VarOcEastDnWind = "ocEastDnWind";
	final public static String _VarOcNorthDnWind = "ocNorthDnWind";
	final public static String _VarOcWindSpd = "ocWindSpd";
	final public static String _VarOcEastDnCurrent = "ocEastDnCurrent";
	final public static String _VarOcNorthDnCurrent = "ocNorthDnCurrent";
	final public static String _VarOcCurrentSpd = "ocCurrentSpd";
	final public static String _VarOcEastBoat = "ocEastBoat";
	final public static String _VarOcNorthBoat = "ocNorthBoat";
	final public static String _VarOcBoatSpd = "ocBoatSpd";

	/**
	 * (If writeOcTables) int var, indexed by _DimTime, _DimScen, and _DimParticle:
	 */
	final public static String _VarOcSvtOrd = "ocSvtOrdinal";

	final private SimCaseManager.SimCase _simCase;
	final private Model _model;
	private long[] _refSecsS;
	private int[][] _distressTypes;
	private int[][] _underwayTypes;
	/** Regular Particles' variables. */
	private long[][] _anchoringRefSecs;
	private long[][] _birthRefSecs;
	private long[][] _landingRefSecs;
	private long[][] _distressRefSecs;
	private long[][] _expirationRefSecs;
	private float[][] _initPriors;
	private float[][][] _lats;
	private float[][][] _lngs;
	private float[][] _distressLats;
	private float[][] _distressLngs;
	private float[][][] _probabilities;
	private float[][][] _cumPFails;
	private int[][][] _svtOrdinals;
	/** Mean Particles. */
	private long[][] _meanBirthRefSecs;
	private long[][] _meanLandingRefSecs;
	private float[][][] _meanLats;
	private float[][][] _meanLngs;
	private boolean _status;
	/** ParticleSailData data. */
	private float[][] _sailorQualities;
	private float[][] _sailorForbiddenAngleIncreases;
	private float[][] _sailorSpeedMultipliers;
	private boolean[][] _zeroZeroMotor;
	/** ocParticles data. */
	private float[][][] _ocLats;
	private float[][][] _ocLngs;
	private float[][][] _ocEastDnWinds;
	private float[][][] _ocNorthDnWinds;
	private float[][][] _ocEastDnCurrents;
	private float[][][] _ocNorthDnCurrents;
	private float[][][] _ocEastBoats;
	private float[][][] _ocNorthBoats;
	private int[][][] _ocSvtOrdinals;

	/**
	 * This ctor creates a ParticlesFile object for a tracker, which populates it;
	 * there is no NetCdf file here.
	 */
	public ParticlesFile(final Tracker tracker, final long firstOutputRefSecs, final long lastOutputRefSecs) {
		_simCase = tracker.getSimCase();
		final SimGlobalStrings simGlobalStrings = _simCase.getSimGlobalStrings();
		_model = tracker.getModel();
		final int nSearchObjectTypes = _model.getNSearchObjectTypes();
		final boolean storeMeans = simGlobalStrings.storeMeans();
		final boolean writeOcTables = _model.getWriteOcTables();
		/** Gather the set of times. */
		_refSecsS = _model.computeRefSecsS();
		final int nRefSecsS = _refSecsS.length;
		final int nScenarii = _model.getNScenarii();
		final int nParticlesPerScenario = _model.getNParticlesPerScenario();
		_distressTypes = new int[nScenarii][nParticlesPerScenario];
		_underwayTypes = new int[nScenarii][nParticlesPerScenario];
		_anchoringRefSecs = new long[nScenarii][nParticlesPerScenario];
		_birthRefSecs = new long[nScenarii][nParticlesPerScenario];
		_landingRefSecs = new long[nScenarii][nParticlesPerScenario];
		_distressRefSecs = new long[nScenarii][nParticlesPerScenario];
		_expirationRefSecs = new long[nScenarii][nParticlesPerScenario];
		_initPriors = new float[nScenarii][nParticlesPerScenario];
		if (_model.hasSailData()) {
			_sailorQualities = new float[nScenarii][nParticlesPerScenario];
			_sailorForbiddenAngleIncreases = new float[nScenarii][nParticlesPerScenario];
			_sailorSpeedMultipliers = new float[nScenarii][nParticlesPerScenario];
			_zeroZeroMotor = new boolean[nScenarii][nParticlesPerScenario];
		} else {
			_sailorQualities = _sailorForbiddenAngleIncreases = _sailorSpeedMultipliers = null;
			_zeroZeroMotor = null;
		}
		_lats = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
		_lngs = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
		_probabilities = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
		_distressLats = new float[nScenarii][nParticlesPerScenario];
		_distressLngs = new float[nScenarii][nParticlesPerScenario];
		_cumPFails = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
		_svtOrdinals = new int[nRefSecsS][nScenarii][nParticlesPerScenario];
		for (int timeIdx = 0; timeIdx < nRefSecsS; ++timeIdx) {
			for (int k0 = 0; k0 < nScenarii; ++k0) {
				Arrays.fill(_lats[timeIdx][k0], Float.NaN);
				Arrays.fill(_lngs[timeIdx][k0], Float.NaN);
				Arrays.fill(_probabilities[timeIdx][k0], Float.NaN);
				Arrays.fill(_cumPFails[timeIdx][k0], 1f);
				Arrays.fill(_svtOrdinals[timeIdx][k0], StateVectorType.UNDEFINED.ordinal());
			}
		}
		for (int k0 = 0; k0 < nScenarii; ++k0) {
			Arrays.fill(_distressLats[k0], Float.NaN);
			Arrays.fill(_distressLngs[k0], Float.NaN);
		}
		if (writeOcTables) {
			_ocLats = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
			_ocLngs = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
			_ocEastDnWinds = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
			_ocNorthDnWinds = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
			_ocEastDnCurrents = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
			_ocNorthDnCurrents = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
			_ocEastBoats = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
			_ocNorthBoats = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
			_ocSvtOrdinals = new int[nRefSecsS][nScenarii][nParticlesPerScenario];
			for (int timeIdx = 0; timeIdx < nRefSecsS; ++timeIdx) {
				for (int k0 = 0; k0 < nScenarii; ++k0) {
					Arrays.fill(_ocLats[timeIdx][k0], Float.NaN);
					Arrays.fill(_ocLngs[timeIdx][k0], Float.NaN);
					Arrays.fill(_ocEastDnWinds[timeIdx][k0], Float.NaN);
					Arrays.fill(_ocNorthDnWinds[timeIdx][k0], Float.NaN);
					Arrays.fill(_ocEastDnCurrents[timeIdx][k0], Float.NaN);
					Arrays.fill(_ocNorthDnCurrents[timeIdx][k0], Float.NaN);
					Arrays.fill(_ocEastBoats[timeIdx][k0], Float.NaN);
					Arrays.fill(_ocNorthBoats[timeIdx][k0], Float.NaN);
					Arrays.fill(_ocSvtOrdinals[timeIdx][k0], StateVectorType.UNDEFINED.ordinal());
				}
			}
		} else {
			_ocLats = _ocLngs = _ocEastDnWinds = _ocNorthDnWinds = null;
			_ocEastDnCurrents = _ocNorthDnCurrents = _ocEastBoats = _ocNorthBoats = null;
			_ocSvtOrdinals = null;
		}
		/**
		 * Initialize the arrays. _probabilities is not initialized here. _cumPFails is
		 * initialized to 1f. The rest are initialized to "not-set."
		 */
		for (int timeIdx = 0; timeIdx < nRefSecsS; ++timeIdx) {
			for (int k0 = 0; k0 < nScenarii; ++k0) {
				Arrays.fill(_lats[timeIdx][k0], Float.NaN);
				Arrays.fill(_lngs[timeIdx][k0], Float.NaN);
				Arrays.fill(_cumPFails[timeIdx][k0], 1f);
				Arrays.fill(_svtOrdinals[timeIdx][k0], StateVectorType.UNDEFINED.ordinal());
				Arrays.fill(_anchoringRefSecs[k0], -1);
				Arrays.fill(_birthRefSecs[k0], -1);
				Arrays.fill(_landingRefSecs[k0], -1);
				Arrays.fill(_distressRefSecs[k0], -1);
				Arrays.fill(_expirationRefSecs[k0], -1);
				Arrays.fill(_underwayTypes[k0], Integer.MIN_VALUE);
				if (writeOcTables) {
					Arrays.fill(_ocLats[timeIdx][k0], Float.NaN);
					Arrays.fill(_ocLngs[timeIdx][k0], Float.NaN);
					Arrays.fill(_ocEastDnWinds[timeIdx][k0], Float.NaN);
					Arrays.fill(_ocNorthDnWinds[timeIdx][k0], Float.NaN);
					Arrays.fill(_ocEastDnCurrents[timeIdx][k0], Float.NaN);
					Arrays.fill(_ocNorthDnCurrents[timeIdx][k0], Float.NaN);
					Arrays.fill(_ocEastBoats[timeIdx][k0], Float.NaN);
					Arrays.fill(_ocNorthBoats[timeIdx][k0], Float.NaN);
					Arrays.fill(_ocSvtOrdinals[timeIdx][k0], StateVectorType.UNDEFINED.ordinal());
				}
			}
		}
		if (storeMeans && nSearchObjectTypes > 0) {
			_meanBirthRefSecs = new long[nScenarii][nSearchObjectTypes];
			_meanLandingRefSecs = new long[nScenarii][nSearchObjectTypes];
			_meanLats = new float[nRefSecsS][nScenarii][nSearchObjectTypes];
			_meanLngs = new float[nRefSecsS][nScenarii][nSearchObjectTypes];
			for (int k0 = 0; k0 < nScenarii; ++k0) {
				Arrays.fill(_meanBirthRefSecs[k0], -1);
				Arrays.fill(_meanLandingRefSecs[k0], -1);
				for (int sotOrd = 0; sotOrd < nSearchObjectTypes; ++sotOrd) {
					for (int timeIdx = 0; timeIdx < nRefSecsS; ++timeIdx) {
						_meanLats[timeIdx][k0][sotOrd] = Float.NaN;
						_meanLngs[timeIdx][k0][sotOrd] = Float.NaN;
					}
				}
			}
		} else {
			_meanBirthRefSecs = null;
			_meanLandingRefSecs = null;
			_meanLats = _meanLngs = null;
		}
		for (int k0 = 0; k0 < nScenarii; ++k0) {
			Arrays.fill(_distressLats[k0], Float.NaN);
			Arrays.fill(_distressLngs[k0], Float.NaN);
		}
		/** Set _initPriors and initialize _probabilities. */
		for (int k0 = 0; k0 < nScenarii; ++k0) {
			final ParticleSet particleSet = tracker.getParticleSet(k0);
			final Particle[] particles = particleSet._particles;
			for (int k1 = 0; k1 < nParticlesPerScenario; ++k1) {
				_initPriors[k0][k1] = (float) particles[k1].getCompletePrior();
				for (int timeIdx = 0; timeIdx < nRefSecsS; ++timeIdx) {
					_probabilities[timeIdx][k0][k1] = _initPriors[k0][k1];
				}
			}
		}
		_status = false;
	}

	/** This ctor reads in the data from a NetCdfFile. */
	public ParticlesFile(final SimCaseManager.SimCase simCase, String particlesFilePath) {
		_simCase = simCase;
		/** Try to find a non-directory existing file. */
		File particlesFile = new File(particlesFilePath);
		if (particlesFile.exists() && particlesFile.isFile()) {
			particlesFilePath = StringUtilities.cleanUpFilePath(particlesFilePath);
		} else {
			final File runDir = DirsTracker.getRunDir();
			final String runDirPath = runDir.getAbsolutePath() + Constants._FileSeparatorChar;
			particlesFilePath = runDirPath + particlesFilePath;
			particlesFile = new File(particlesFilePath);
			if (particlesFile.exists() && particlesFile.isFile()) {
				particlesFilePath = StringUtilities.cleanUpFilePath(particlesFilePath);
			} else {
				_model = null;
				_refSecsS = null;
				_distressTypes = _underwayTypes = null;
				_anchoringRefSecs = _birthRefSecs = _landingRefSecs = null;
				_distressRefSecs = _expirationRefSecs = null;
				_initPriors = null;
				_sailorQualities = _sailorForbiddenAngleIncreases = _sailorSpeedMultipliers = null;
				_zeroZeroMotor = null;
				_lats = _lngs = null;
				_distressLats = _distressLngs = null;
				_probabilities = _cumPFails = null;
				_svtOrdinals = null;
				_meanBirthRefSecs = _meanLandingRefSecs = null;
				_meanLats = _meanLngs = null;
				_status = false;
				return;
			}
		}
		boolean status = false;
		Model model = null;
		int nRefSecsS = 0;
		int nScenarii = 0;
		int nParticlesPerScenario = 0;
		SimCaseManager.out(simCase, "Reading ParticlesFile: " + particlesFilePath);
		try (NetcdfFile netCdfFile = NetcdfFile.open(particlesFilePath)) {
			SimCaseManager.out(simCase, String.format("FileTypeId: %s, Particle file Version: %s, Description: %s.",
					netCdfFile.getFileTypeId(), netCdfFile.getFileTypeVersion(), netCdfFile.getFileTypeDescription()));
			final String modelString = NetCdfUtil.getGlobalAttributeValue(netCdfFile, _ModelStringAttribute,
					_OldModelStringAttribute);
			model = ModelReader.readFromParticlesFileModelString(simCase, modelString);
			SimCaseManager.out(simCase, "Decoded model from particlesFile.");
			nScenarii = model.getNScenarii();
			nParticlesPerScenario = model.getNParticlesPerScenario();
			/** Get the basic dimension lengths. */
			final Dimension timeDimension = netCdfFile.findDimension("time");
			nRefSecsS = timeDimension.getLength();
			final Dimension particleDimension = netCdfFile.findDimension(_DimParticle);
			/**
			 * Because reading nParticlesPerScenario from the model, even the one within the
			 * particlesFile, might refer to the current set of SimProperties, we must get
			 * it from the dimension within the actual NetCdf file to not crash while
			 * reading.
			 */
			nParticlesPerScenario = particleDimension.getLength();
			model.setNParticlesPerScenario(nParticlesPerScenario);

			/** Read in the data. */
			try {
				_refSecsS = new long[nRefSecsS];
				NetCdfUtil.read1DLongs(netCdfFile, nRefSecsS, _VarTime, _refSecsS);
				_anchoringRefSecs = new long[nScenarii][nParticlesPerScenario];
				NetCdfUtil.read2DLongs(netCdfFile, nScenarii, nParticlesPerScenario, _VarAnchoringTime,
						_anchoringRefSecs);
				_birthRefSecs = new long[nScenarii][nParticlesPerScenario];
				NetCdfUtil.read2DLongs(netCdfFile, nScenarii, nParticlesPerScenario, _VarBirthTime, _birthRefSecs);
				_landingRefSecs = new long[nScenarii][nParticlesPerScenario];
				NetCdfUtil.read2DLongs(netCdfFile, nScenarii, nParticlesPerScenario, _VarLandingTime, _landingRefSecs);
				_distressRefSecs = new long[nScenarii][nParticlesPerScenario];
				NetCdfUtil.read2DLongs(netCdfFile, nScenarii, nParticlesPerScenario, _VarDistressTime,
						_distressRefSecs);
				_expirationRefSecs = new long[nScenarii][nParticlesPerScenario];
				NetCdfUtil.read2DLongs(netCdfFile, nScenarii, nParticlesPerScenario, _VarExpirationTime,
						_expirationRefSecs);
				_underwayTypes = new int[nScenarii][nParticlesPerScenario];
				NetCdfUtil.read2DInts(netCdfFile, nScenarii, nParticlesPerScenario, _VarUnderwayTypeName,
						_underwayTypes);
				_distressTypes = new int[nScenarii][nParticlesPerScenario];
				NetCdfUtil.read2DInts(netCdfFile, nScenarii, nParticlesPerScenario, _VarDistressType, _distressTypes);
				_distressLats = new float[nScenarii][nParticlesPerScenario];
				NetCdfUtil.read2DFloats(netCdfFile, nScenarii, nParticlesPerScenario, _VarDistressLat, _distressLats);
				_distressLngs = new float[nScenarii][nParticlesPerScenario];
				NetCdfUtil.read2DFloats(netCdfFile, nScenarii, nParticlesPerScenario, _VarDistressLng, _distressLngs);
				_lats = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
				NetCdfUtil.read3DFloats(netCdfFile, nRefSecsS, nScenarii, nParticlesPerScenario, _VarLat, _lats);
				_lngs = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
				NetCdfUtil.read3DFloats(netCdfFile, nRefSecsS, nScenarii, nParticlesPerScenario, _VarLng, _lngs);
				_svtOrdinals = new int[nRefSecsS][nScenarii][nParticlesPerScenario];
				try {
					NetCdfUtil.read3DInts(netCdfFile, nRefSecsS, nScenarii, nParticlesPerScenario, _VarSvtOrd,
							_svtOrdinals);
				} catch (final Exception e2) {
					NetCdfUtil.read3DInts(netCdfFile, nRefSecsS, nScenarii, nParticlesPerScenario, _OldVarSvtOrd,
							_svtOrdinals);
				}
				_probabilities = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
				NetCdfUtil.read3DFloats(netCdfFile, nRefSecsS, nScenarii, nParticlesPerScenario, _VarProbability,
						_probabilities);
				_cumPFails = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
				NetCdfUtil.read3DFloats(netCdfFile, nRefSecsS, nScenarii, nParticlesPerScenario, _VarPFail, _cumPFails);
				_initPriors = new float[nScenarii][nParticlesPerScenario];
				try {
					NetCdfUtil.read2DFloats(netCdfFile, nScenarii, nParticlesPerScenario, _VarInitPrior0, _initPriors);
				} catch (final Exception e0) {
					try {
						NetCdfUtil.read2DFloats(netCdfFile, nScenarii, nParticlesPerScenario, _VarInitPrior1,
								_initPriors);
					} catch (final Exception e1) {
						for (int k0 = 0; k0 < nScenarii; ++k0) {
							for (int k1 = 0; k1 < nParticlesPerScenario; ++k1) {
								_initPriors[k0][k1] = _probabilities[0][k0][k1];
							}
						}
					}
				}
				_sailorQualities = new float[nScenarii][nParticlesPerScenario];
				_sailorForbiddenAngleIncreases = new float[nScenarii][nParticlesPerScenario];
				_sailorSpeedMultipliers = new float[nScenarii][nParticlesPerScenario];
				_zeroZeroMotor = new boolean[nScenarii][nParticlesPerScenario];
				try {
					NetCdfUtil.read2DFloats(netCdfFile, nScenarii, nParticlesPerScenario, _VarSailorQuality,
							_sailorQualities);
					NetCdfUtil.read2DFloats(netCdfFile, nScenarii, nParticlesPerScenario,
							_VarSailorForbiddenAngleIncrease, _sailorForbiddenAngleIncreases);
					NetCdfUtil.read2DFloats(netCdfFile, nScenarii, nParticlesPerScenario, _VarSailorSpeedMultiplier,
							_sailorSpeedMultipliers);
					NetCdfUtil.read2DBools(netCdfFile, nScenarii, nParticlesPerScenario, _VarSailorZeroZeroMotor,
							_zeroZeroMotor);
				} catch (final Exception e) {
					_sailorQualities = _sailorForbiddenAngleIncreases = _sailorSpeedMultipliers = null;
					_zeroZeroMotor = null;
				}
				final int nSearchObjectTypes = model.getNSearchObjectTypes();
				try {
					_meanBirthRefSecs = new long[nScenarii][nSearchObjectTypes];
					NetCdfUtil.read2DLongs(netCdfFile, nScenarii, nSearchObjectTypes, _VarMeanBirthTime,
							_meanBirthRefSecs);
					_meanLandingRefSecs = new long[nScenarii][nSearchObjectTypes];
					NetCdfUtil.read2DLongs(netCdfFile, nScenarii, nSearchObjectTypes, _VarMeanLandingTime,
							_meanLandingRefSecs);
					_meanLats = new float[nRefSecsS][nScenarii][nSearchObjectTypes];
					NetCdfUtil.read3DFloats(netCdfFile, nRefSecsS, nScenarii, nSearchObjectTypes, _VarMeanLat,
							_meanLats);
					_meanLngs = new float[nRefSecsS][nScenarii][nSearchObjectTypes];
					NetCdfUtil.read3DFloats(netCdfFile, nRefSecsS, nScenarii, nSearchObjectTypes, _VarMeanLng,
							_meanLngs);
				} catch (final Exception e) {
					_meanBirthRefSecs = null;
					_meanLandingRefSecs = null;
					_meanLats = _meanLngs = null;
				}
				/** See if we read in ocTables. */
				_ocLats = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
				try {
					NetCdfUtil.read3DFloats(netCdfFile, nRefSecsS, nScenarii, nParticlesPerScenario, _VarOcLat,
							_ocLats);
				} catch (final Exception e) {
					_ocLats = null;
				}
				if (_ocLats != null) {
					_ocLngs = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
					_ocEastDnWinds = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
					_ocNorthDnWinds = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
					_ocEastDnCurrents = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
					_ocNorthDnCurrents = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
					_ocEastBoats = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
					_ocNorthBoats = new float[nRefSecsS][nScenarii][nParticlesPerScenario];
					_ocSvtOrdinals = new int[nRefSecsS][nScenarii][nParticlesPerScenario];
					try {
						NetCdfUtil.read3DFloats(netCdfFile, nRefSecsS, nScenarii, nParticlesPerScenario, _VarOcLng,
								_ocLngs);
						NetCdfUtil.read3DFloats(netCdfFile, nRefSecsS, nScenarii, nParticlesPerScenario,
								_VarOcEastDnWind, _ocEastDnWinds);
						NetCdfUtil.read3DFloats(netCdfFile, nRefSecsS, nScenarii, nParticlesPerScenario,
								_VarOcNorthDnWind, _ocNorthDnWinds);
						NetCdfUtil.read3DFloats(netCdfFile, nRefSecsS, nScenarii, nParticlesPerScenario,
								_VarOcEastDnCurrent, _ocEastDnCurrents);
						NetCdfUtil.read3DFloats(netCdfFile, nRefSecsS, nScenarii, nParticlesPerScenario,
								_VarOcNorthDnCurrent, _ocNorthDnCurrents);
						NetCdfUtil.read3DFloats(netCdfFile, nRefSecsS, nScenarii, nParticlesPerScenario, _VarOcEastBoat,
								_ocEastBoats);
						NetCdfUtil.read3DFloats(netCdfFile, nRefSecsS, nScenarii, nParticlesPerScenario,
								_VarOcNorthBoat, _ocNorthBoats);
						NetCdfUtil.read3DInts(netCdfFile, nRefSecsS, nScenarii, nParticlesPerScenario, _VarOcSvtOrd,
								_ocSvtOrdinals);
					} catch (final Exception e) {
						_ocLats = null;
					}
				} else {
					_ocLats = _ocLngs = null;
					_ocLats = _ocLngs = _ocEastDnWinds = _ocNorthDnWinds = null;
					_ocEastDnCurrents = _ocNorthDnCurrents = _ocEastBoats = _ocNorthBoats = null;
					_ocSvtOrdinals = null;
				}
				status = true;
			} catch (final Exception e1) {
				_distressTypes = _underwayTypes = null;
				_anchoringRefSecs = _birthRefSecs = _landingRefSecs = null;
				_distressRefSecs = _expirationRefSecs = null;
				_initPriors = null;
				_sailorQualities = _sailorForbiddenAngleIncreases = _sailorSpeedMultipliers = null;
				_zeroZeroMotor = null;
				_lats = _lngs = null;
				_distressLats = _distressLngs = null;
				_probabilities = _cumPFails = null;
				_svtOrdinals = null;
				_meanBirthRefSecs = _meanLandingRefSecs = null;
				_meanLats = _meanLngs = null;
				_ocLats = _ocLngs = null;
				_ocLats = _ocLngs = _ocEastDnWinds = _ocNorthDnWinds = null;
				_ocEastDnCurrents = _ocNorthDnCurrents = _ocEastBoats = _ocNorthBoats = null;
				_ocSvtOrdinals = null;
				status = false;
			}
		} catch (final IOException e) {
		}
		_model = model;
		_status = status;
	}

	public double[] getLatLngPair(final int timeIdx, final ParticleIndexes prtclIndxs) {
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		final double lat;
		final double lng;
		if (j >= 0) {
			lat = _lats[timeIdx][i][j];
			lng = _lngs[timeIdx][i][j];
		} else {
			if (_meanLats != null) {
				final int k = prtclIndxs.getSotOrd();
				lat = _meanLats[timeIdx][i][k];
				lng = _meanLngs[timeIdx][i][k];
			} else {
				lat = lng = Float.NaN;
			}
		}
		return new double[] {
				lat, lng
		};
	}

	public double[] getDistressLatLngPair(final ParticleIndexes prtclIndxs) {
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		final double distressLat = _distressLats[i][j];
		final double distressLng = _distressLngs[i][j];
		return new double[] {
				distressLat, distressLng
		};
	}

	public StateVectorType getSvtFromTimeIdx(final int timeIdx, final ParticleIndexes prtclIndxs) {
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		final int ordinal = _svtOrdinals[timeIdx][i][j];
		return StateVectorType.values()[ordinal];
	}

	public StateVectorType getSvtFromRefSecs(final long refSecs, final ParticleIndexes prtclIndxs) {
		final int timeIdx = Math.max(0, CombinatoricTools.getGlbIndex(_refSecsS, refSecs));
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		final int ordinal = _svtOrdinals[timeIdx][i][j];
		return StateVectorType.values()[ordinal];
	}

	public LatLng3 getLatLng(final long refSecs, final ParticleIndexes prtclIndxs) {
		final int nRefsM1 = _refSecsS.length - 1;
		final int timeIdx1X = CombinatoricTools.getGlbIndex(_refSecsS, refSecs);
		final int timeIdx1 = Math.max(0, Math.min(nRefsM1, timeIdx1X));
		final int timeIdx2X = CombinatoricTools.getLubIndex(_refSecsS, refSecs);
		final int timeIdx2 = Math.max(0, Math.min(nRefsM1, timeIdx2X));
		final double[] latLng1Pair = getLatLngPair(timeIdx1, prtclIndxs);
		if (Double.isNaN(latLng1Pair[0])) {
			return null;
		}
		final LatLng3 latLng1 = LatLng3.getLatLngC2(latLng1Pair);
		if (timeIdx1 == timeIdx2) {
			return latLng1;
		}
		/** Interpolate. */
		final double[] latLng2Pair = getLatLngPair(timeIdx2, prtclIndxs);
		final LatLng3 latLng2 = LatLng3.getLatLngC2(latLng2Pair);
		final long t1 = _refSecsS[timeIdx1];
		final long t2 = _refSecsS[timeIdx2];
		final long durationInSecs = t2 - t1;
		final GreatCircleCalculator greatCircleCalculator = (GreatCircleCalculator) NavigationCalculatorStatics
				.buildWithSeconds(latLng1, t1, latLng2, durationInSecs, MotionType.GREAT_CIRCLE);
		final LatLng3 latLng = greatCircleCalculator.getPosition(refSecs);
		return latLng;
	}

	public void setDistressType(final ParticleIndexes prtclIndxs, final int distressTypeId) {
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		_distressTypes[i][j] = distressTypeId;
	}

	public void setUnderwayType(final ParticleIndexes prtclIndxs, final int underwayTypeId) {
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		_underwayTypes[i][j] = underwayTypeId;
	}

	public int getUnderwayType(final ParticleIndexes prtclIndxs) {
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		return _underwayTypes[i][j];
	}

	public int getDistressType(final ParticleIndexes prtclIndxs) {
		final int i = prtclIndxs.getScenarioIndex();
		final int k = prtclIndxs.getSotOrd();
		if (k >= 0) {
			final SearchObjectType searchObjectType = _model.getSearchObjectTypeFromOrd(k);
			final int objectType = searchObjectType.getId();
			if (objectType < 0) {
				assert false : "Bad distressType in ParticlesFile: " + objectType;
			}
			return objectType;
		}
		final int j = prtclIndxs.getParticleIndex();
		final int objectType = _distressTypes[i][j];
		return objectType;
	}

	public int getObjectTypeId(final long refSecs, final ParticleIndexes prtclIndxs) {
		final int objectType;
		if (_model.getReverseDrift()) {
			objectType = getDistressType(prtclIndxs);
		} else {
			final int k = prtclIndxs.getSotOrd();
			if (k >= 0) {
				final SearchObjectType searchObjectType = _model.getSearchObjectTypeFromOrd(k);
				objectType = searchObjectType.getId();
			} else {
				final long distressRefSecs = getDistressRefSecs(prtclIndxs);
				if (refSecs < distressRefSecs) {
					objectType = getUnderwayType(prtclIndxs);
				} else {
					objectType = getDistressType(prtclIndxs);
				}
			}
		}
		return objectType;
	}

	public long getAnchoringRefSecs(final ParticleIndexes prtclIndxs) {
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		if (j >= 0) {
			return _anchoringRefSecs[i][j];
		}
		return -1;
	}

	public long getBirthRefSecs(final ParticleIndexes prtclIndxs) {
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		final long t;
		if (j >= 0) {
			t = _birthRefSecs[i][j];
		} else {
			final int k = prtclIndxs.getSotOrd();
			t = _meanBirthRefSecs == null ? -1 : _meanBirthRefSecs[i][k];
		}
		return t;
	}

	public long getDistressRefSecs(final ParticleIndexes prtclIndxs) {
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		final long t = _distressRefSecs[i][j];
		return t;
	}

	public long getExpirationRefSecs(final ParticleIndexes prtclIndxs) {
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		final long t = _expirationRefSecs[i][j];
		return t;
	}

	public long getLandingRefSecs(final ParticleIndexes prtclIndxs) {
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		final long t;
		if (j >= 0) {
			t = _landingRefSecs[i][j];
		} else {
			final int k = prtclIndxs.getSotOrd();
			t = _meanLandingRefSecs == null ? -1 : _meanLandingRefSecs[i][k];
		}
		return t;
	}

	public float getProbabilityForIdx(final int refSecsIdx, final ParticleIndexes prtclIndxs) {
		if (refSecsIdx < 0 || refSecsIdx >= _refSecsS.length) {
			return 0f;
		}
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		return _probabilities[refSecsIdx][i][j];
	}

	public float getProbability(final long refSecs, final ParticleIndexes prtclIndxs) {
		final int nRefsM1 = _refSecsS.length - 1;
		final int timeIdxX = CombinatoricTools.getLubIndex(_refSecsS, refSecs);
		final int timeIdx = Math.max(0, Math.min(nRefsM1, timeIdxX));
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		return _probabilities[Math.max(0, timeIdx)][i][j];
	}

	public float getCumPFail(final long refSecs, final ParticleIndexes prtclIndxs) {
		/** CumPFail is the probability at or AFTER refSecs. */
		final int nRefsM1 = _refSecsS.length - 1;
		final int timeIdxX = CombinatoricTools.getLubIndex(_refSecsS, refSecs);
		final int timeIdx = Math.max(0, Math.min(nRefsM1, timeIdxX));
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		return _cumPFails[timeIdx][i][j];
	}

	public float getInitPrior(final ParticleIndexes prtclIndxs) {
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		return _initPriors[i][j];
	}

	public float getSailorQuality(final ParticleIndexes prtclIndxs) {
		if (_sailorQualities == null) {
			return Float.NaN;
		}
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		return _sailorQualities[i][j];
	}

	public float getSailorForbiddenAngleIncrease(final ParticleIndexes prtclIndxs) {
		if (_sailorForbiddenAngleIncreases == null) {
			return Float.NaN;
		}
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		return _sailorForbiddenAngleIncreases[i][j];
	}

	public float getSailorSpeedMultiplier(final ParticleIndexes prtclIndxs) {
		if (_sailorSpeedMultipliers == null) {
			return Float.NaN;
		}
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		return _sailorSpeedMultipliers[i][j];
	}

	public boolean getSailorIsZeroZeroMotor(final ParticleIndexes prtclIndxs) {
		if (_zeroZeroMotor == null) {
			return false;
		}
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		return _zeroZeroMotor[i][j];
	}

	public boolean getStatus() {
		return _status;
	}

	public Model getModel() {
		return _model;
	}

	public long[] getRefSecsS() {
		return _refSecsS;
	}

	public int getNScenarii() {
		return _lats[0].length;
	}

	public int getNParticlesPerScenario() {
		return _lats[0][0].length;
	}

	public boolean deepEquals(final ParticlesFile other) {
		final int nRefSecsS = _refSecsS.length;
		if (nRefSecsS != other._refSecsS.length) {
			SimCaseManager.err(_simCase, "Different number of times: " + nRefSecsS + " vs. " + other._refSecsS.length);
			return false;
		}
		for (int timeIdx = 0; timeIdx < nRefSecsS; ++timeIdx) {
			if (_refSecsS[timeIdx] != other._refSecsS[timeIdx]) {
				SimCaseManager.err(_simCase, "Different times at index " + timeIdx + ": " + _refSecsS[timeIdx] + " vs."
						+ other._refSecsS[timeIdx]);
				return false;
			}
		}
		/**
		 * _distressTypes' first index indexes the scenarios; so the following line is
		 * right.
		 */
		final int nScenarii = _distressTypes.length;
		if (nScenarii != other._distressTypes.length) {
			SimCaseManager.err(_simCase,
					"Different number of scenarios: " + nScenarii + " vs. " + other._distressTypes.length);
			return false;
		}
		final int nParticles = _distressTypes[0].length;
		if (nParticles != other._distressTypes[0].length) {
			SimCaseManager.err(_simCase,
					"Different number of particles: " + nParticles + " vs. " + other._distressTypes[0].length);
			return false;
		}
		if (!deepEquals("Underway Type", _underwayTypes, other._underwayTypes, nScenarii, nParticles)
				|| !deepEquals("Birth Time", _birthRefSecs, other._birthRefSecs, nScenarii, nParticles)
				|| !deepEquals("Distress Time", _distressRefSecs, other._distressRefSecs, nScenarii, nParticles)
				|| !deepEquals("Expiration Time", _expirationRefSecs, other._expirationRefSecs, nScenarii,
						nParticles)) {
			return false;
		}
		if (!deepEquals("Anchoring Time", _anchoringRefSecs, other._anchoringRefSecs, nScenarii, nParticles)) {
			return false;
		}
		if (!deepEquals("Distress Type", _distressTypes, other._distressTypes, nScenarii, nParticles)) {
			return false;
		}
		if (!deepEquals("Landing Time", _landingRefSecs, other._landingRefSecs, nScenarii, nParticles)) {
			return false;
		}
		if (!deepEquals(_VarLat, _lats, other._lats, nRefSecsS, nScenarii, nParticles)) {
			return false;
		}
		if (!deepEquals(_VarLng, _lngs, other._lngs, nRefSecsS, nScenarii, nParticles)) {
			return false;
		}

		if (!deepEquals(_VarDistressLat, _distressLats, other._distressLats, nScenarii, nParticles)) {
			return false;
		}
		if (!deepEquals(_VarDistressLng, _distressLngs, other._distressLngs, nScenarii, nParticles)) {
			return false;
		}

		if (!deepEquals(_VarSvtOrd, _svtOrdinals, other._svtOrdinals, nRefSecsS, nScenarii, nParticles)) {
			return false;
		}
		if (!deepEquals(_VarProbability, _probabilities, other._probabilities, nRefSecsS, nScenarii, nParticles)) {
			return false;
		}
		if (!deepEquals(_VarPFail, _cumPFails, other._cumPFails, nRefSecsS, nScenarii, nParticles)) {
			return false;
		}
		return true;
	}

	private boolean deepEquals(final String message, final int[][] numbers, final int[][] otherNumbers,
			final int nScenarii, final int nParticles) {
		if (numbers.length != nScenarii || otherNumbers.length != nScenarii) {
			SimCaseManager.err(_simCase, String.format("\nMessage[%s].  Incorrect number of scenarios " + //
					"(expected[%d], found[%d and %d]).", //
					message, nScenarii, numbers.length, otherNumbers.length));
			return false;
		}
		for (int k0 = 0; k0 < nScenarii; ++k0) {
			final int[] row = numbers[k0];
			final int[] otherRow = otherNumbers[k0];
			if (row.length != nParticles || otherRow.length != nParticles) {
				SimCaseManager.err(_simCase,
						String.format(
								"\nMessage[%s].  Incorrect number of "
										+ "particles for scenario (expected[%d], found[%d and %d]).",
								message, nParticles, row.length, otherRow.length));
				return false;
			}
			for (int k1 = 0; k1 < nParticles; ++k1) {
				if (row[k1] != otherRow[k1]) {
					SimCaseManager.err(_simCase,
							String.format(
									"\nMessage[%s].  ScenarioIndex[%s], " + "particleIndex[%d].  Found %d vs. %d.",
									message, k0, k1, row[k1], otherRow[k1]));
					return false;
				}
			}
		}
		return true;
	}

	private boolean deepEquals(final String message, final long[][] numbers, final long[][] otherNumbers,
			final int nScenarii, final int nParticles) {
		if (numbers.length != nScenarii || otherNumbers.length != nScenarii) {
			SimCaseManager.err(_simCase,
					String.format(
							"Message[%s].  Incorrect Number of Scenarios.  " + "Expected[%d], found(%d] and %d]).",
							message, nScenarii, numbers.length, otherNumbers.length));
			return false;
		}
		for (int k0 = 0; k0 < nScenarii; ++k0) {
			final long[] row = numbers[k0];
			final long[] otherRow = otherNumbers[k0];
			if (row.length != nParticles || otherRow.length != nParticles) {
				SimCaseManager.err(_simCase,
						String.format(
								"Message[%s].  Incorrect Number of Particles "
										+ "for ScenarioIndex[%d].  Expected[%d], found(%d] and %d]).",
								message, k0, nParticles, row.length, otherRow.length));
				return false;
			}
			for (int k1 = 0; k1 < nParticles; ++k1) {
				if (row[k1] != otherRow[k1]) {
					SimCaseManager.err(_simCase,
							String.format(
									"\nMessage[%s].  ScenarioIndex[%d], " + "ParticleIndex[%d].  Found (%d, vs %d).",
									message, k0, k1, row[k1], otherRow[k1]));
					return false;
				}
			}
		}
		return true;
	}

	private boolean deepEquals(final String msg, final float[][][] array, final float[][][] otherArray,
			final int numberOfTimeSteps, final int nScenarii, final int nParticlesPerScenario) {
		if (array.length != numberOfTimeSteps || otherArray.length != numberOfTimeSteps) {
			SimCaseManager.err(_simCase,
					String.format(
							"\nMessage[%s].  Incorrect Number of TimeSteps.  " + "Expected[%d], found(%d] and %d]).",
							msg, numberOfTimeSteps, array.length, otherArray.length));
			return false;
		}
		for (int timeIdx = 0; timeIdx < numberOfTimeSteps; ++timeIdx) {
			final float[][] numbers = array[timeIdx];
			final float[][] otherNumbers = otherArray[timeIdx];
			final String message = msg + " time step " + timeIdx;
			if (numbers.length != nScenarii || otherNumbers.length != nScenarii) {
				SimCaseManager.err(_simCase,
						String.format(
								"\nMessage[%s].  Incorrect Number of Scenarios.  "
										+ "Expected[%d], found(%d] and %d]).",
								message, nScenarii, numbers.length, otherNumbers.length));
				return false;
			}
			for (int k0 = 0; k0 < nScenarii; ++k0) {
				final float[] row = numbers[k0];
				final float[] otherRow = otherNumbers[k0];
				if (row.length != nParticlesPerScenario || otherRow.length != nParticlesPerScenario) {
					SimCaseManager.err(_simCase,
							String.format(
									"\nMessage[%s].  Incorrect Number of " + "Particles for ScenarioIndex[%d].  "
											+ "Expected[%d], found(%d] and %d]).",
									message, k0, nParticlesPerScenario, row.length, otherRow.length));
					return false;
				}
				for (int k1 = 0; k1 < nParticlesPerScenario; ++k1) {
					if (row[k1] != otherRow[k1]) {
						SimCaseManager.err(_simCase,
								String.format(
										"\nMessage[%s].  ScenarioIndex[%d], " + "ParticleIndex, found(%d] and %d]).",
										message, k0, k1, row[k1], otherRow[k1]));
						return false;
					}
				}
			}
		}
		return true;
	}

	private boolean deepEquals(final String msg, final float[][] array, final float[][] otherArray, final int nScenarii,
			final int nParticlesPerScenario) {
		final float[][][] array1 = new float[][][] {
				array
		};
		final float[][][] otherArray1 = new float[][][] {
				otherArray
		};
		return deepEquals(msg, array1, otherArray1, 1, nScenarii, nParticlesPerScenario);
	}

	private boolean deepEquals(final String msg, final int[][][] array, final int[][][] otherArray,
			final int numberOfTimeSteps, final int nScenarii, final int nParticles) {
		if (array.length != numberOfTimeSteps || otherArray.length != numberOfTimeSteps) {
			final String formatString = "\nMessage[%s].  Incorrect Number of TimeSteps.  "
					+ "Expected[%d], found(%d] and %d]).";
			SimCaseManager.err(_simCase,
					String.format(formatString, msg, numberOfTimeSteps, array.length, otherArray.length));
			return false;
		}
		for (int timeIdx = 0; timeIdx < numberOfTimeSteps; ++timeIdx) {
			final int[][] numbers = array[timeIdx];
			final int[][] otherNumbers = otherArray[timeIdx];
			final String message = msg + " time step " + timeIdx;
			if (numbers.length != nScenarii || otherNumbers.length != nScenarii) {
				SimCaseManager.err(_simCase,
						String.format(
								"\nMessage[%s].  Incorrect Number of Scenarios.  "
										+ "Expected[%d], found(%d] and %d]).",
								message, nScenarii, numbers.length, otherNumbers.length));
				return false;
			}
			for (int k0 = 0; k0 < nScenarii; ++k0) {
				final int[] row = numbers[k0];
				final int[] otherRow = otherNumbers[k0];
				if (row.length != nParticles || otherRow.length != nParticles) {
					SimCaseManager.err(_simCase, String.format(
							"\nMessage[%s].  Incorrect Number of Particles for ScenarioIndex[%d].  Expected[%d], found(%d] and %d]).",
							message, k0, nParticles, row.length, otherRow.length));
					return false;
				}
				for (int k1 = 0; k1 < nParticles; ++k1) {
					if (row[k1] != otherRow[k1]) {
						SimCaseManager.err(_simCase,
								String.format("\nMessage[%s].  ScenarioIndex[%d], ParticleIndex, found(%d] and %d]).",
										message, k0, k1, row[k1], otherRow[k1]));
						return false;
					}
				}
			}
		}
		return true;
	}

	public void setAnchoringRefSecs(final ParticleIndexes prtclIndxs, final long refSecs) {
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		_anchoringRefSecs[i][j] = refSecs;
	}

	public void setBirthRefSecs(final ParticleIndexes prtclIndxs, final long refSecs) {
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		if (j >= 0) {
			_birthRefSecs[i][j] = refSecs;
		} else {
			if (_meanBirthRefSecs != null) {
				final int k = prtclIndxs.getSotOrd();
				_meanBirthRefSecs[i][k] = refSecs;
			}
		}
	}

	public void setLandingRefSecs(final ParticleIndexes prtclIndxs, final long refSecs) {
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		if (j >= 0) {
			_landingRefSecs[i][j] = refSecs;
		} else {
			if (_meanLandingRefSecs != null) {
				final int k = prtclIndxs.getSotOrd();
				_meanLandingRefSecs[i][k] = refSecs;
			}
		}
	}

	public void setDistressRefSecs(final ParticleIndexes prtclIndxs, final long refSecs) {
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		_distressRefSecs[i][j] = refSecs;
	}

	public void setExpirationRefSecs(final ParticleIndexes prtclIndxs, final long refSecs) {
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		_expirationRefSecs[i][j] = refSecs;
	}

	public void setPosition(final ParticleIndexes prtclIndxs, final LatLng3 latLng, final long refSecs) {
		final int timeIdx = getTimeIndexForSetting(refSecs);
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		final float[][] theseLats;
		final float[][] theseLngs;
		final int q;
		if (j >= 0) {
			q = j;
			theseLats = _lats[timeIdx];
			theseLngs = _lngs[timeIdx];
		} else {
			if (_meanLats == null) {
				return;
			}
			final int sotOrd = prtclIndxs.getSotOrd();
			q = sotOrd;
			theseLats = _meanLats[timeIdx];
			theseLngs = _meanLngs[timeIdx];
		}
		theseLats[i][q] = (float) latLng.getLat();
		theseLngs[i][q] = (float) latLng.getLng();
	}

	public void setDistressLatLng(final ParticleIndexes prtclIndxs, final LatLng3 latLng) {
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		_distressLats[i][j] = (float) latLng.getLat();
		_distressLngs[i][j] = (float) latLng.getLng();
	}

	public void setSailorQuality(final ParticleIndexes prtclIndxs, final Sdi sdi) {
		if (sdi == null) {
			return;
		}
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		_sailorQualities[i][j] = (float) sdi._sailorQuality;
		_sailorForbiddenAngleIncreases[i][j] = (float) sdi._forbiddenAngleIncrease;
		_sailorSpeedMultipliers[i][j] = (float) sdi._speedMultiplier;
		_zeroZeroMotor[i][j] = sdi._useZeroZeroMotoring;
	}

	public void setSvtOrdinal(final ParticleIndexes prtclIndxs, final StateVectorType svt, final long refSecs) {
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		final int timeIdx = getTimeIndexForSetting(refSecs);
		_svtOrdinals[timeIdx][i][j] = svt.ordinal();
	}

	public void updateCumPFails(final ParticleIndexes prtclIndxs, final double pFail, final long refSecs) {
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		final int nRefSecsS = _cumPFails.length;
		final int timeIdx0 = getTimeIndexForSetting(refSecs);
		if (_model.getReverseDrift()) {
			for (int timeIndex = timeIdx0; timeIndex >= 0; --timeIndex) {
				_cumPFails[timeIndex][i][j] *= pFail;
				_probabilities[timeIndex][i][j] *= pFail;
			}
		} else {
			for (int timeIdx = timeIdx0; timeIdx < nRefSecsS; ++timeIdx) {
				_cumPFails[timeIdx][i][j] *= pFail;
				_probabilities[timeIdx][i][j] *= pFail;
			}
		}
	}

	private int getTimeIndexForSetting(final long refSecs) {
		final int index;
		if (!_model.getReverseDrift()) {
			/**
			 * For a regular run, the timeIndex is the earliest time at or AFTER refSecs.
			 */
			index = CombinatoricTools.getLubIndex(_refSecsS, refSecs);
		} else {
			index = CombinatoricTools.getGlbIndex(_refSecsS, refSecs);
		}
		return Math.min(_refSecsS.length - 1, Math.max(0, index));
	}

	public void normalizeProbabilities() {
		final int nRefSecsS = _refSecsS.length;
		for (int timeIdx = 0; timeIdx < nRefSecsS; ++timeIdx) {
			final float ttlWeight = NumericalRoutines.normalize2dMtx(_probabilities[timeIdx]);
			if (ttlWeight == 0f) {
				System.err.printf("\n0 probabilities; using vanilla weights.");
			}
		}
		_status = true;
	}

	public boolean dumpBadLatsAndLngs() {
		final int nRefSecsS = _refSecsS.length;
		boolean haveBadLatLng = false;
		TimeLoop: for (int timeIdx = 0; timeIdx < nRefSecsS; ++timeIdx) {
			for (int k0 = 0; k0 < _model.getNScenarii(); ++k0) {
				for (int k1 = 0; k1 < _model.getNParticlesPerScenario(); ++k1) {
					final float lat = _lats[timeIdx][k0][k1];
					final float lng = _lngs[timeIdx][k0][k1];
					if (Float.isNaN(lat) || Float.isNaN(lng)) {
						SimCaseManager.err(_simCase,
								String.format("TimeIdx[%d] iScenario[%d] iParticle[%d]", timeIdx, k0, k1));
						haveBadLatLng = true;
						break TimeLoop;
					}
				}
			}
		}
		return haveBadLatLng;
	}

	public String getStateVectorsString() {
		final int nRefSecsS = _refSecsS.length;
		final int nScenarii = _model.getNScenarii();
		final int nParticlesPerScenario = _model.getNParticlesPerScenario();
		String s = "";
		for (int iT = 0; iT < nRefSecsS; ++iT) {
			final long refSecs = _refSecsS[iT];
			final String timeString = TimeUtilities.formatTime(refSecs, true);
			for (int k0 = 0; k0 < nScenarii; ++k0) {
				for (int k1 = 0; k1 < nParticlesPerScenario; ++k1) {
					final int svtOrdinal = _svtOrdinals[iT][k0][k1];
					final StateVectorType svt = StateVectorType.values()[svtOrdinal];
					final double lat = _lats[iT][k0][k1];
					final double lng = _lngs[iT][k0][k1];
					final LatLng3 latLng = LatLng3.getLatLngB(lat, lng);
					s += Constants._NewLine + timeString + " " + svt.name() + ":" + svt.getColorName() + ":"
							+ latLng.getString(4);
					/**
					 * This is strictly for debugging; just do it for the first particle.
					 */
					break;
				} /**
					 * This is strictly for debugging; just do it for the first particle.
					 */
				break;
			}
		}
		return s;
	}

	public WeightedPairReDataAcc computeWeightedPairReDataAcc(final TangentCylinder tangentCylinder, final long refSecs,
			final int iScenario) {
		final int nParticlesPerScenario = _model.getNParticlesPerScenario();
		final WeightedPairReDataAcc latLngAcc = new WeightedPairReDataAcc();
		for (int iParticle = 0; iParticle < nParticlesPerScenario; ++iParticle) {
			final ParticleIndexes prtclIndxs = ParticleIndexes.getStandardOne(_model, iScenario, iParticle);
			final LatLng3 latLng = getLatLng(refSecs, prtclIndxs);
			final double wt = getProbability(refSecs, prtclIndxs);
			final TangentCylinder.FlatLatLng flatLatLng = tangentCylinder.convertToMyFlatLatLng(latLng);
			final double eastOffset = flatLatLng.getEastOffset();
			final double northOffset = flatLatLng.getNorthOffset();
			latLngAcc.add(eastOffset, northOffset, wt);
		}
		return latLngAcc;
	}

	public boolean hasEnvMeanData() {
		return _meanLats != null;
	}

	public float getOcLat(final int timeIdx, final ParticleIndexes prtclIndxs) {
		if (_ocLats == null) {
			return Float.NaN;
		}
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		return _ocLats[timeIdx][i][j];
	}

	public float getOcLng(final int timeIdx, final ParticleIndexes prtclIndxs) {
		if (_ocLngs == null) {
			return Float.NaN;
		}
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		return _ocLngs[timeIdx][i][j];
	}

	public float getOcEastDnWind(final int timeIdx, final ParticleIndexes prtclIndxs) {
		if (_ocEastDnWinds == null) {
			return Float.NaN;
		}
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		return _ocEastDnWinds[timeIdx][i][j];
	}

	public float getOcNorthDnWind(final int timeIdx, final ParticleIndexes prtclIndxs) {
		if (_ocNorthDnWinds == null) {
			return Float.NaN;
		}
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		return _ocNorthDnWinds[timeIdx][i][j];
	}

	public float getOcEastDnCurrent(final int timeIdx, final ParticleIndexes prtclIndxs) {
		if (_ocEastDnCurrents == null) {
			return Float.NaN;
		}
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		return _ocEastDnCurrents[timeIdx][i][j];
	}

	public float getOcNorthDnCurrent(final int timeIdx, final ParticleIndexes prtclIndxs) {
		if (_ocNorthDnCurrents == null) {
			return Float.NaN;
		}
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		return _ocNorthDnCurrents[timeIdx][i][j];
	}

	public float getOcEastBoat(final int timeIdx, final ParticleIndexes prtclIndxs) {
		if (_ocEastBoats == null) {
			return Float.NaN;
		}
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		return _ocEastBoats[timeIdx][i][j];
	}

	public float getOcNorthBoat(final int timeIdx, final ParticleIndexes prtclIndxs) {
		if (_ocNorthBoats == null) {
			return Float.NaN;
		}
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		return _ocNorthBoats[timeIdx][i][j];
	}

	public int getOcSvtOrdinal(final int timeIdx, final ParticleIndexes prtclIndxs) {
		if (_ocSvtOrdinals == null) {
			return StateVectorType.UNDEFINED.ordinal();
		}
		final int i = prtclIndxs.getScenarioIndex();
		final int j = prtclIndxs.getParticleIndex();
		return _ocSvtOrdinals[timeIdx][i][j];
	}

	public void setItinerary(final ParticleIndexes prtclIndxs, final Itinerary itinerary) {
		if (itinerary == null || !(itinerary instanceof VoyageItinerary)) {
			return;
		}
		final int k0 = prtclIndxs.getScenarioIndex();
		final int k1 = prtclIndxs.getParticleIndex();
		final VoyageItinerary voyageItinerary = (VoyageItinerary) itinerary;
		final int nRefSecsS = _refSecsS.length;
		for (int timeIdx = 0; timeIdx < nRefSecsS; ++timeIdx) {
			final VoyageItinerary.OcInfo ocInfo = voyageItinerary.getOcInfo(_refSecsS, timeIdx);
			if (ocInfo != null) {
				_ocEastDnWinds[timeIdx][k0][k1] = ocInfo._eastDnWind;
				_ocNorthDnWinds[timeIdx][k0][k1] = ocInfo._northDnWind;
				_ocEastDnCurrents[timeIdx][k0][k1] = ocInfo._eastDnCurrent;
				_ocNorthDnCurrents[timeIdx][k0][k1] = ocInfo._northDnCurrent;
				_ocSvtOrdinals[timeIdx][k0][k1] = ocInfo._svt.ordinal();
				/** For the position of the boats, it's trickier. */
				_ocLats[timeIdx][k0][k1] = ocInfo._lat;
				_ocLngs[timeIdx][k0][k1] = ocInfo._lng;
				_ocEastBoats[timeIdx][k0][k1] = ocInfo._eastBoat;
				_ocNorthBoats[timeIdx][k0][k1] = ocInfo._northBoat;
			}
		}
	}

	public boolean hasOcData() {
		return _ocLats != null;
	}
}
