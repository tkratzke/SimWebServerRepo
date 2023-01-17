package com.skagit.sarops.environment;

import java.util.BitSet;

import org.w3c.dom.Element;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.navigation.LatLng3;

import ucar.nc2.NetcdfFile;

/**
 * Implementation that assumes that the underlying data comes from a NetCdf
 * File. By extending a NetCdfUvGetter, most of the work is done for the key
 * routine {@link #getWindUv(long, LatLng3, String, boolean)}.
 */
public class NetCdfWindsUvGetter extends NetCdfUvGetter
		implements WindsUvGetter {
	final private SimCaseManager.SimCase _simCase;
	private boolean _haveLoggedInvalid;

	public NetCdfWindsUvGetter(final SimCaseManager.SimCase simCase,
			final Model model, final String fileName, final double dU,
			final double dV, final double altDU, final double altDV,
			final long halfLifeSecs, final long preDistressHalfLifeSecs,
			final boolean dataIsDownWind) {//
		super(simCase, model, _WindsTag, fileName,
				new String[] { "U", "u", "wind_u" }, //
				new String[] { "V", "v", "wind_v" }, //
				new String[] { "Speed", "speed", "wind_speed" }, //
				new String[] { "Dir", "dir", "Direction", "direction", "wind_dir" }, //
				new String[] { "U_Unc", "u_Unc", "u_unc", "wind_u_Unc" }, //
				new String[] { "V_Unc", "v_Unc", "v_unc", "wind_v_Unc" }, //
				new String[] { "U_AltUnc", "u_AltUnc", "u_Altunc",
						"wind_u_AltUnc" }, //
				new String[] { "V_AltUnc", "v_AltUnc", "v_Altunc",
						"wind_v_AltUnc" }, //
				new String[] {}, //
				new String[] {}, //
				new String[] {}, //
				dU, dV, altDU, altDV, //
				halfLifeSecs, preDistressHalfLifeSecs, dataIsDownWind);
		_simCase = simCase;
		_haveLoggedInvalid = false;
	}

	/** nominalFileName could be a uri string. */
	public NetCdfWindsUvGetter(final SimCaseManager.SimCase simCase,
			final Model model, final String nominalFileName,
			final NetcdfFile netCdfFile, final double dU, final double dV,
			final double altDU, final double altDV, final long halfLifeSecs,
			final long preDistressHalfLifeSecs,
			final boolean useStandardDirection) throws NetCdfUvGetterException {
		super(simCase, model, _WindsTag, nominalFileName, netCdfFile,
				new String[] { "U", "u", "wind_u" },
				new String[] { "V", "v", "wind_v" },
				new String[] { "Speed", "speed", "wind_speed" },
				new String[] { "Dir", "dir", "Direction", "direction", "wind_dir" },
				new String[] { "U_Unc", "u_Unc", "u_unc", "wind_u_Unc" },
				new String[] { "V_Unc", "v_Unc", "v_unc", "wind_v_Unc" },
				new String[] { "U_AltUnc", "u_AltUnc", "u_Altunc",
						"wind_u_AltUnc" },
				new String[] { "V_AltUnc", "v_AltUnc", "v_Altunc",
						"wind_altv_Unc" },
				new String[] {}, new String[] {}, new String[] {}, dU, dV, altDU,
				altDV, halfLifeSecs, preDistressHalfLifeSecs, useStandardDirection);
		_simCase = simCase;
		_haveLoggedInvalid = false;
	}

	@Override
	public DataForOnePointAndTime getDownWindData(final long refSecs,
			final LatLng3 latLng, final String interpolationMode) {
		final DataForOnePointAndTime data = getDataForOnePointAndTime(_simCase,
				refSecs, latLng, interpolationMode);
		return data;
	}

	/**
	 * This routine is explicitly here even though it simply calls super. The
	 * reason for this is that we are explicitly implementing a promised
	 * routine from {@link com.skagit.sarops.environment.WindsUvGetter}.
	 */
	@Override
	public long getHalfLifeSecs() {
		return super.getHalfLifeSecs();
	}

	@Override
	public void close(final String interpolationMode) {
		closePointCollection(_simCase, interpolationMode);
	}

	@Override
	public boolean useRandomDuringUpdates() {
		return true;
	}

	@Override
	public WindsUvGetter getWindsUvGetter2(final BitSet iViews,
			final boolean interpolateInTime) {
		return null;
	}

	@Override
	public String[] getViewNames() {
		return new String[] { "Winds" };
	}

	@Override
	public void incrementalPrepare(final long secs, final LatLng3 latLng,
			final BoxDefinition boxDefinition) {
	}

	@Override
	public void finishPrepare() {
	}

	@Override
	public boolean prepareIsTrivial() {
		return true;
	}

	@Override
	public boolean isEmpty(final SimCaseManager.SimCase simCase) {
		final boolean empty = super.isEmpty(simCase);
		if (empty && !_haveLoggedInvalid) {
			SimCaseManager.err(simCase, "Invalid Winds UvGetter!");
			_haveLoggedInvalid = true;
		}
		return empty;
	}

	@Override
	public void writeElement(final Element outputWindsElement,
			final Element inputWindsElement, final Model model) {
		outputWindsElement.setAttribute("file", model.getWindsFilePath());
		outputWindsElement.setAttribute("type", model.getWindsFileType());
	}

	@Override
	public void freeMemory() {
		freePointCollectionMemory();
	}
}
