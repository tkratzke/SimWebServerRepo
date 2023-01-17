package com.skagit.sarops.tracker;

import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeSet;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.util.SimGlobalStrings;
import com.skagit.util.netCdfUtil.NetCdfUtil;

/**
 * A Snapshot is the data associated with a particular time-step. Hence,
 * none of the arrays here has a dimension corresponding to time.
 */
public class Snapshot {
	/**
	 * The members of this enum are precisely those tables that are indexed by
	 * _DimTime, _DimScen, and _DimParticle. This also provides the map
	 * between the field names of a snapshot and the variable names within a
	 * ParticlesFile.
	 */
	public enum VarInfoOf3dStandardVars {
		LAT(false, NetCdfUtil.MyDataType.FLOAT, "_lat", ParticlesFile._VarLat),
		LNG(false, NetCdfUtil.MyDataType.FLOAT, "_lng", ParticlesFile._VarLng),
		PROB(false, NetCdfUtil.MyDataType.FLOAT, "_probability",
				ParticlesFile._VarProbability),
		PFAIL(false, NetCdfUtil.MyDataType.FLOAT, "_cumPFail",
				ParticlesFile._VarPFail),
		SVT_ORDINAL(false, NetCdfUtil.MyDataType.INT, "_svtOrdinal",
				ParticlesFile._VarSvtOrd),
		/**
		 * MeanLat and MeanLng do not have standard dimensions and hence, do not
		 * appear hear.
		 */
		OC_LAT(true, NetCdfUtil.MyDataType.FLOAT, "_ocLat",
				ParticlesFile._VarOcLat),
		OC_LNG(true, NetCdfUtil.MyDataType.FLOAT, "_ocLng",
				ParticlesFile._VarOcLng),
		OC_EAST_DNWIND(true, NetCdfUtil.MyDataType.FLOAT, "_ocEastDnWind",
				ParticlesFile._VarOcEastDnWind),
		OC_NORTH_DNWIND(true, NetCdfUtil.MyDataType.FLOAT, "_ocNorthDnWind",
				ParticlesFile._VarOcNorthDnWind),
		OC_WIND_SPD(true, NetCdfUtil.MyDataType.FLOAT, "_ocWindSpd",
				ParticlesFile._VarOcWindSpd),
		OC_EAST_DNCURRENT(true, NetCdfUtil.MyDataType.FLOAT, "_ocEastDnCurrent",
				ParticlesFile._VarOcEastDnCurrent),
		OC_NORTH_DNCURRENT(true, NetCdfUtil.MyDataType.FLOAT,
				"_ocNorthDnCurrent", ParticlesFile._VarOcNorthDnCurrent),
		OC_CURRENT_SPD(true, NetCdfUtil.MyDataType.FLOAT, "_ocCurrentSpd",
				ParticlesFile._VarOcCurrentSpd),
		OC_EAST_BOAT(true, NetCdfUtil.MyDataType.FLOAT, "_ocEastBoat",
				ParticlesFile._VarOcEastBoat),
		OC_NORTH_BOAT(true, NetCdfUtil.MyDataType.FLOAT, "_ocNorthBoat",
				ParticlesFile._VarOcNorthBoat),
		OC_BOAT_SPD(true, NetCdfUtil.MyDataType.FLOAT, "_ocBoatSpd",
				ParticlesFile._VarOcBoatSpd),
		OC_SVT_ORDINAL(true, NetCdfUtil.MyDataType.INT, "_ocSvtOrdinal",
				ParticlesFile._VarOcSvtOrd);
		final boolean _oc;
		final NetCdfUtil.MyDataType _myDataType;
		final String _fieldName;
		final String _varName;

		private VarInfoOf3dStandardVars(final boolean oc,
				final NetCdfUtil.MyDataType myDataType, final String fieldName,
				final String varName) {
			_oc = oc;
			_myDataType = myDataType;
			_fieldName = fieldName;
			_varName = varName;
		}
	}

	/** Indexed by scenario and particle-within-scenario. */
	final float[][] _lat;
	final float[][] _lng;
	final float[][] _probability;
	final float[][] _cumPFail;
	final int[][] _svtOrdinal;
	/** Indexed by scenario and object type. */
	final float[][] _meanLat;
	final float[][] _meanLng;
	/**
	 * For Oc: all of these are indexed by scenario and
	 * particle-within-scenario.
	 */
	final float[][] _ocLat;
	final float[][] _ocLng;
	final float[][] _ocEastDnWind;
	final float[][] _ocNorthDnWind;
	final float[][] _ocWindSpd;
	final float[][] _ocEastDnCurrent;
	final float[][] _ocNorthDnCurrent;
	final float[][] _ocCurrentSpd;
	final float[][] _ocEastBoat;
	final float[][] _ocNorthBoat;
	final float[][] _ocBoatSpd;
	final int[][] _ocSvtOrdinal;

	/**
	 * The following is the map between the field names of a Snapshot that are
	 * indexed by scenario and particle-within-scenario, and the variables in
	 * a ParticlesFile.
	 */
	static final TreeSet<VarInfoOf3dStandardVars> _SnapshotVarInfos;

	static {
		_SnapshotVarInfos =
				new TreeSet<>(new Comparator<VarInfoOf3dStandardVars>() {

					@Override
					public int compare(final VarInfoOf3dStandardVars varInfo0,
							final VarInfoOf3dStandardVars varInfo1) {
						final String fieldName0 = varInfo0._fieldName;
						final String fieldName1 = varInfo1._fieldName;
						return fieldName0.compareTo(fieldName1);
					}
				});
		_SnapshotVarInfos
				.addAll(Arrays.asList(VarInfoOf3dStandardVars.values()));
	}

	Snapshot(final SimCaseManager.SimCase simCase, final int nScenarii,
			final int nParticlesPerScenario, final int nSearchObjectTypes) {
		_lat = new float[nScenarii][nParticlesPerScenario];
		_lng = new float[nScenarii][nParticlesPerScenario];
		_probability = new float[nScenarii][nParticlesPerScenario];
		_cumPFail = new float[nScenarii][nParticlesPerScenario];
		_svtOrdinal = new int[nScenarii][nParticlesPerScenario];
		final Object mainSaropsObject = simCase.getMainSaropsObject();
		final Model model = (mainSaropsObject == null ||
				!(mainSaropsObject instanceof Tracker)) ? null :
						((Tracker) mainSaropsObject).getModel();
		final boolean writeOcTables =
				(model == null || !model.hasSomePreDistress()) ? false :
						model.getWriteOcTables();
		if (writeOcTables) {
			_ocLat = new float[nScenarii][nParticlesPerScenario];
			_ocLng = new float[nScenarii][nParticlesPerScenario];
			_ocEastDnWind = new float[nScenarii][nParticlesPerScenario];
			_ocNorthDnWind = new float[nScenarii][nParticlesPerScenario];
			_ocWindSpd = new float[nScenarii][nParticlesPerScenario];
			_ocEastDnCurrent = new float[nScenarii][nParticlesPerScenario];
			_ocNorthDnCurrent = new float[nScenarii][nParticlesPerScenario];
			_ocCurrentSpd = new float[nScenarii][nParticlesPerScenario];
			_ocEastBoat = new float[nScenarii][nParticlesPerScenario];
			_ocNorthBoat = new float[nScenarii][nParticlesPerScenario];
			_ocBoatSpd = new float[nScenarii][nParticlesPerScenario];
			_ocSvtOrdinal = new int[nScenarii][nParticlesPerScenario];
		} else {
			_ocLat = _ocLng = null;
			_ocEastDnWind = _ocNorthDnWind = _ocWindSpd = null;
			_ocEastDnCurrent = _ocNorthDnCurrent = _ocCurrentSpd = null;
			_ocEastBoat = _ocNorthBoat = _ocBoatSpd = null;
			_ocSvtOrdinal = null;
		}

		final SimGlobalStrings simGlobalStrings = simCase.getSimGlobalStrings();
		final boolean storeMeans = simGlobalStrings.storeMeans();
		if (storeMeans) {
			_meanLat = new float[nScenarii][nSearchObjectTypes];
			_meanLng = new float[nScenarii][nSearchObjectTypes];
		} else {
			_meanLat = _meanLng = null;
		}
	}

	boolean haveOcData() {
		return _ocLat != null;
	}
}