package com.skagit.sarops.environment;

import java.util.BitSet;

import org.w3c.dom.Element;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.Scenario;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.DistressStateVector;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.LatLng3;

import ucar.nc2.NetcdfFile;

/**
 * Implementation that assumes that the underlying data comes from a NetCdf
 * File. By extending a NetCdfUvGetter, most of the work is done for the key
 * routine {@link #getDriftUv(long, LatLng3, String, boolean)}.
 */
public class NetCdfCurrentsUvGetter extends NetCdfUvGetter implements CurrentsUvGetter {
	final private SimCaseManager.SimCase _simCase;
	private boolean _haveLoggedInvalid;

	public NetCdfCurrentsUvGetter(final SimCaseManager.SimCase simCase, final Model model, final String filePath,
			final double dU, final double dV, final double altDU, final double altDV, final long halfLifeSecs,
			final long preDistressHalfLifeSecs) {
		/** The final true in the following is "useStandardDirection." */
		super(simCase, model, _DriftsTag, filePath, new String[] {
				"U", "u", "water_u"
		}, new String[] {
				"V", "v", "water_v"
		}, new String[] {
				"Speed", "speed", "water_speed"
		}, new String[] {
				"dir", "Dir", "direction", "Direction", "water_dir", "water_direction"
		}, new String[] {
				"U_Unc", "u_Unc", "u_unc", "water_u_Unc"
		}, new String[] {
				"V_Unc", "v_Unc", "v_unc", "water_v_Unc"
		}, new String[] {
				"U_AltUnc", "u_AltUnc", "u_altunc", "water_u_AltUnc"
		}, new String[] {
				"V_AltUnc", "v_AltUnc", "v_altunc", "water_v_AltUnc"
		}, new String[] {
				"riverid"
		}, new String[] {
				"seqno"
		}, new String[] {
				"flag"
		}, dU, dV, altDU, altDV, halfLifeSecs, preDistressHalfLifeSecs, true);
		_simCase = simCase;
		_haveLoggedInvalid = false;
	}

	public NetCdfCurrentsUvGetter(final SimCaseManager.SimCase simCase, final Model model, final String nominalFilePath,
			final NetcdfFile netCdfFile, final double defaultDU, final double defaultDV, final double defaultAltDU,
			final double defaultAltDV, final long halfLifeSecs, final long preDistressHalfLifeSecs)
			throws NetCdfUvGetterException {
		super(simCase, model, _DriftsTag, nominalFilePath, netCdfFile, new String[] {
				"U", "u", "water_u"
		}, new String[] {
				"V", "v", "water_v"
		}, new String[] {
				"Speed", "speed", "water_speed"
		}, new String[] {
				"dir", "Dir", "direction", "Direction", "water_dir", "water_direction"
		}, new String[] {
				"U_Unc", "u_Unc", "u_unc", "water_u_Unc"
		}, new String[] {
				"V_Unc", "v_Unc", "v_unc", "water_v_Unc"
		}, new String[] {
				"U_AltUnc", "u_AltUnc", "u_altunc", "water_u_AltUnc"
		}, new String[] {
				"V_AltUnc", "v_AltUnc", "v_altunc", "water_v_AltUnc"
		}, new String[] {
				"riverid"
		}, new String[] {
				"seqno"
		}, new String[] {
				"flag"
		}, defaultDU, defaultDV, defaultAltDU, defaultAltDV, halfLifeSecs, preDistressHalfLifeSecs,
				/* useStandardDeviation= */true);
		_simCase = simCase;
		_haveLoggedInvalid = false;
	}

	@Override
	public DataForOnePointAndTime getCurrentData(final MyLogger logger, final long refSecs, final LatLng3 latLng,
			final String interpolationMode) {
		return getDataForOnePointAndTime(_simCase, refSecs, latLng, interpolationMode);
	}

	/**
	 * This routine is explicitly here even though it simply calls super. The reason
	 * for this is that we are explicitly implementing a promised routine from
	 * {@link com.skagit.sarops.environment.CurrentsUvGetter}.
	 */
	@Override
	public long getHalfLifeSecs() {
		return super.getHalfLifeSecs();
	}

	@Override
	public long getPreDistressHalfLifeSecs() {
		return super.getPreDistressHalfLifeSecs();
	}

	@Override
	public void close(final String interpolationMode) {
		closePointCollection(_simCase, interpolationMode);
	}

	@Override
	public void incrementalPrepare(final long refSeconds, final LatLng3 latLng, final BoxDefinition boxDefinition) {
	}

	@Override
	public void finishPrepare() {
	}

	@Override
	public boolean prepareIsTrivial() {
		return true;
	}

	@Override
	public boolean useRandomDuringUpdates() {
		return true;
	}

	@Override
	public CurrentsUvGetter getCurrentsUvGetter2(final MyLogger logger, final BitSet iViews,
			final boolean interpolateInTime) {
		return null;
	}

	@Override
	public String[] getViewNames() {
		return new String[] {
				"Currents"
		};
	}

	@Override
	public void writeElement(final Element outputDriftsElement, final Element inputDriftsElement, final Model model) {
		outputDriftsElement.setAttribute("file", model.getCurrentsFilePath());
		outputDriftsElement.setAttribute("type", model.getCurrentsFileType());
	}

	@Override
	public boolean isEmpty(final MyLogger logger) {
		final boolean empty = super.isEmpty(logger);
		if (empty && !_haveLoggedInvalid && logger != null) {
			MyLogger.err(logger, "Invalid Drifts UvGetter!");
			_haveLoggedInvalid = true;
		}
		return empty;
	}

	@Override
	public void freeMemory() {
		freePointCollectionMemory();
	}

	@Override
	public int[] getFirstSampleAndPredictionIndex(final int nViews) {
		return null;
	}

	@Override
	public boolean hasAuxiliaryProcessing() {
		return false;
	}

	@Override
	public DistressStateVector fillInStateVectorsIfAppropriate(final WindsUvGetter windsUvGetter,
			final Scenario scenario, final long[] simSecsS, final DistressStateVector distressStateVector,
			final long simSecs) {
		return null;
	}
}
