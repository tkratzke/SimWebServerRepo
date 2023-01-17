package com.skagit.sarops.environment.riverSeqLcrUvCalculator;

import java.util.TreeMap;

import com.skagit.sarops.environment.DataForOnePointAndTime;
import com.skagit.sarops.environment.NetCdfDataPoint;
import com.skagit.sarops.environment.NetCdfUvGetter;
import com.skagit.sarops.environment.UvCalculator;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.CombinatoricTools;
import com.skagit.util.MathX;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.navigation.LatLng3;

/**
 * We get the uv values differently when we are using a river. There are
 * different interpolation modes than the standard "2-closest" or
 * "3-closest." This class uses a {@link RiverSeqLcrMachinery} to do that.
 * The key routine is {@link #getUVDuDv(int, boolean)}.
 */
public class RiverSeqLcrUvCalculator implements UvCalculator {
	final LatLng3 _latLng;
	final RiverSeqLcrMachinery.InterpolationPot _interpolationPot2;
	final double _weightForUpstream, _weightForDownstream;
	/**
	 * Simple initializer of a map from the flag in the file to the flag that
	 * we store internally.
	 */
	final public static byte _InputLeft = -1, _InputCenter = 0,
			_InputRight = 1;
	final public static byte _Left = 1, _Center = 2, _Right = 3;
	final public static TreeMap<Byte, Byte> _InputLcrToLcr;
	static {
		_InputLcrToLcr = new TreeMap<>();
		_InputLcrToLcr.put(_InputLeft, _Left);
		_InputLcrToLcr.put(_InputCenter, _Center);
		_InputLcrToLcr.put(_InputRight, _Right);
	}

	/**
	 * Primarily for Debugging; given a flag that indicates the strip, what is
	 * the readable string that goes with it.
	 */
	public static String getStripName(final int lcr) {
		return lcr == _Left ? "Left" :
				(lcr == _Center ? "Center" : (lcr == _Right ? "Right" : "NULL"));
	}

	public static String getRiverSeqLcrString(final int[] riverSeqLcr) {
		return CombinatoricTools.getString(riverSeqLcr);
	}

	/** Debugging routine. */
	public static void logDataPoint(final SimCaseManager.SimCase simCase,
			final String comment, final NetCdfDataPoint dataPoint,
			final double weight) {
		final String riverSeqLcrString =
				getRiverSeqLcrString(dataPoint.getRiverSeqLcr());
		SimCaseManager.out(simCase,
				String.format("%s: L/L%s [River,Seq,Lcr]=%s Weight[%.5f]", comment,
						dataPoint.getLatLng(), riverSeqLcrString, weight));
	}

	/**
	 * Key routine. This ctor sets up the machinery for a point of interest.
	 */
	public RiverSeqLcrUvCalculator(final SimCaseManager.SimCase simCase,
			final RiverSeqLcrMachinery riverSeqLcrMachinery,
			final LatLng3 latLng) {
		_latLng = LatLng3.makeBasicLatLng3(latLng);
		_interpolationPot2 =
				riverSeqLcrMachinery.getInterpolationPot(simCase, _latLng);
		final LatLng3 projection = _interpolationPot2._projection;
		final NetCdfDataPoint upstreamDataPoint = _interpolationPot2._upstream;
		final NetCdfDataPoint downstreamDataPoint =
				_interpolationPot2._downstream;
		if (upstreamDataPoint == downstreamDataPoint) {
			_weightForUpstream = 1d;
			_weightForDownstream = 0d;
		} else {
			final LatLng3 upstreamLatLng = upstreamDataPoint.getLatLng();
			final LatLng3 downstreamLatLng = downstreamDataPoint.getLatLng();
			final double haversineToUpstreamFromProjection =
					MathX.haversineX(projection, upstreamLatLng);
			final double haversineToDownstreamFromProjection =
					MathX.haversineX(projection, downstreamLatLng);
			final double haversineBetweenUpstreamAndDownstream =
					haversineToUpstreamFromProjection +
							haversineToDownstreamFromProjection;
			_weightForUpstream = haversineToDownstreamFromProjection /
					haversineBetweenUpstreamAndDownstream;
			_weightForDownstream = 1d - _weightForUpstream;
		}
	}

	/**
	 * Interpolates between an upstream and downstream point. This is for
	 * rivers only, and most of the work has been done before we get here. We
	 * have already identified the upstream and downstream point, and all that
	 * remains is the interpolation.
	 * <p>
	 * The direction comes from the upstream point, and we interpolate the
	 * speed.
	 * </P>
	 *
	 */
	@Override
	public DataForOnePointAndTime getDataForOnePointAndTime(
			final int timeIdx) {
		/**
		 * We combine the upstream and downstream data. The direction of what
		 * our "downstream" depends exclusively on the upstream datum.
		 */
		final DataForOnePointAndTime upstreamData =
				_interpolationPot2._upstream.getDataForOnePointAndTime(timeIdx);
		final double upstreamUInKts =
				upstreamData.getValue(NetCdfUvGetter.DataComponent.U);
		final double upstreamVInKts =
				upstreamData.getValue(NetCdfUvGetter.DataComponent.V);
		/**
		 * Create a vector of what "downstream means." We create a unit vector
		 * and get its magnitude.
		 */
		final double[] unitUv1 =
				new double[] { upstreamUInKts, upstreamVInKts };
		final double uvSpeedFromUpstream =
				NumericalRoutines.convertToUnitLength(unitUv1);
		final DataForOnePointAndTime downstreamData =
				_interpolationPot2._downstream.getDataForOnePointAndTime(timeIdx);
		final double downstreamU =
				downstreamData.getValue(NetCdfUvGetter.DataComponent.U);
		final double downstreamV =
				downstreamData.getValue(NetCdfUvGetter.DataComponent.V);
		/** We'll ignore unitUv2 and be interested only in its magnitude. */
		final double[] unitUv2 = new double[] { downstreamU, downstreamV };
		final double uvSpeedFromDownstream =
				NumericalRoutines.convertToUnitLength(unitUv2);
		final double speed = _weightForUpstream * uvSpeedFromUpstream +
				_weightForDownstream * uvSpeedFromDownstream;
		/**
		 * To get the standard deviations for u and v, we have to work some
		 * since they are given as factors of the downstream speed. Step 1 is to
		 * get the std dev of the downstream and crossstream speeds. Everything
		 * is in kts.
		 */
		final double sdDnStreamFactor =
				(upstreamData.getValue(NetCdfUvGetter.DataComponent.DU) +
						downstreamData.getValue(NetCdfUvGetter.DataComponent.DU)) /
						100d / 2d;
		final double sdCrossStreamFactor =
				(upstreamData.getValue(NetCdfUvGetter.DataComponent.DV) +
						downstreamData.getValue(NetCdfUvGetter.DataComponent.DV)) / 2d;
		final double sdDnStream = speed * sdDnStreamFactor;
		final double sdCrossStream = sdDnStream * sdCrossStreamFactor;
		/** For the alternates. */
		final double altStdDevDownstreamFactor =
				(upstreamData.getValue(NetCdfUvGetter.DataComponent.ALT_DU) +
						downstreamData.getValue(NetCdfUvGetter.DataComponent.ALT_DU)) /
						100d / 2d;
		final double altStdDevCrossStreamFactor =
				(upstreamData.getValue(NetCdfUvGetter.DataComponent.ALT_DV) +
						downstreamData.getValue(NetCdfUvGetter.DataComponent.ALT_DV)) /
						2d;
		final double altStdDevDownStream = speed * altStdDevDownstreamFactor;
		final double altStdDevCrossStream =
				altStdDevDownStream * altStdDevCrossStreamFactor;
		/**
		 * End of step 1. Convert these std devs to std devs for u and v and the
		 * alternate versions.
		 */
		final double uOfDownStream = unitUv1[0];
		final double vOfDownStream = unitUv1[1];
		final double uOfCrossStream = -unitUv1[1];
		final double vOfCrossStream = unitUv1[0];
		final double stdDevU1 = sdDnStream * uOfDownStream;
		final double stdDevU2 = sdCrossStream * uOfCrossStream;
		final double stdDevU =
				Math.sqrt(stdDevU1 * stdDevU1 + stdDevU2 * stdDevU2);
		final double stdDevV1 = sdDnStream * vOfDownStream;
		final double stdDevV2 = sdCrossStream * vOfCrossStream;
		final double stdDevV =
				Math.sqrt(stdDevV1 * stdDevV1 + stdDevV2 * stdDevV2);
		/** For the alternates. */
		final double altStdDevU1 = altStdDevDownStream * uOfDownStream;
		final double altStdDevU2 = altStdDevCrossStream * uOfCrossStream;
		final double altStdDevU =
				Math.sqrt(altStdDevU1 * altStdDevU1 + altStdDevU2 * altStdDevU2);
		final double altStdDevV1 = altStdDevDownStream * vOfDownStream;
		final double altStdDevV2 = altStdDevCrossStream * vOfCrossStream;
		final double altStdDevV =
				Math.sqrt(altStdDevV1 * altStdDevV1 + altStdDevV2 * altStdDevV2);
		/** Return the answers. */
		return new DataForOnePointAndTime((float) (speed * unitUv1[0]),
				(float) (speed * unitUv1[1]), (float) stdDevU, (float) stdDevV,
				(float) altStdDevU, (float) altStdDevV);
	}
}
