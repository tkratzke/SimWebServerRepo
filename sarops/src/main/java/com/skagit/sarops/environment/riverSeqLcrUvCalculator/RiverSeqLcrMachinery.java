package com.skagit.sarops.environment.riverSeqLcrUvCalculator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.skagit.sarops.environment.NetCdfDataPoint;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.MathX;
import com.skagit.util.geometry.crossingPair.CrossingPair2;
import com.skagit.util.geometry.geoMtx.GeoMtx;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.navigation.LatLng3;

/**
 * The constructor takes all of the points and creates the Rivers, which
 * create their own River.Strips. This machinery is what is necessary for
 * the types of riverine interpolation.
 */
public class RiverSeqLcrMachinery {
	final private static double _NmiToR = MathX._NmiToR;
	final private Map<Integer, River> _riverIdToRiver;
	final private Map<LatLng3, NetCdfDataPoint> _latLngToDataPoint;
	final private Map<NetCdfDataPoint, LatLng3> _dataPointToLatLng;
	final private boolean _centerDominated;
	final private boolean _debug;

	public RiverSeqLcrMachinery(final SimCaseManager.SimCase simCase,
			final NetCdfDataPoint[] sortedDataPoints,
			final String interpolationMode, final boolean debug) {
		_debug = debug;
		_centerDominated =
				interpolationMode.compareTo(Model._CenterDominated) == 0;
		_latLngToDataPoint = new HashMap<>();
		_dataPointToLatLng = new TreeMap<>(_ByFlagOnly);
		final Map<Integer, ArrayList<NetCdfDataPoint>> riverToRiverPoints =
				new HashMap<>();
		for (final NetCdfDataPoint dataPoint : sortedDataPoints) {
			final int[] riverSeqLcr = dataPoint.getRiverSeqLcr();
			if (riverSeqLcr != null) {
				final int river = riverSeqLcr[0];
				ArrayList<NetCdfDataPoint> riverPoints =
						riverToRiverPoints.get(river);
				if (riverPoints == null) {
					riverPoints = new ArrayList<>();
					riverToRiverPoints.put(river, riverPoints);
				}
				riverPoints.add(dataPoint);
			}
			final LatLng3 latLng = dataPoint.getLatLng();
			/** If any of the following 3 tests fails, we have invalid data. */
			if (dataPoint.getRiverSeqLcr() == null) {
				final String message =
						String.format("%s lacks RiverSeqLcr info.  " +
								"Abandoning River Interpolation.", latLng.getString());
				SimCaseManager.out(simCase, message);
				_riverIdToRiver = null;
				return;
			}
			final NetCdfDataPoint oldDataPoint =
					_latLngToDataPoint.put(latLng, dataPoint);
			if (oldDataPoint != null) {
				final String message =
						String.format("%s is a duplicated LatLng.  " +
								"Abandoning River Interpolation.", latLng.getString());
				SimCaseManager.wrn(simCase, message);
				_riverIdToRiver = null;
				return;
			}
			final LatLng3 oldLatLng = _dataPointToLatLng.put(dataPoint, latLng);
			if (oldLatLng != null && riverSeqLcr != null) {
				final int riverId = riverSeqLcr[0];
				final int lcr = riverSeqLcr[2];
				final String message =
						String.format("[%d/%d] is a duplicated RiverId/Lcr.  " +
								"Abandoning River Interpolation.", riverId, lcr);
				SimCaseManager.out(simCase, message);
				_riverIdToRiver = null;
				return;
			}
		}
		_riverIdToRiver = new HashMap<>();
		for (final Map.Entry<Integer, ArrayList<NetCdfDataPoint>> entry : riverToRiverPoints
				.entrySet()) {
			_riverIdToRiver.put(entry.getKey(),
					new River(simCase, entry.getValue()));
			final int river = entry.getKey();
			final River riverObject = _riverIdToRiver.get(river);
			if (_debug) {
				SimCaseManager.out(simCase,
						String.format(
								"In FlagMachinery Ctor, river[%d] RiverObject[%s]", river,
								riverObject.getString()));
			}
		}
		SimCaseManager.out(simCase, String.format(
				"\nAfterRiverIdToRiver, %d RiverIds.", _riverIdToRiver.size()));
	}

	class PrjctnInfo {
		final double _nmi;
		final LatLng3 _projectedLatLng;
		final int _seqIndex;

		private PrjctnInfo(final double nmi, final LatLng3 projectedLatLng,
				final int seqIndex) {
			_nmi = nmi;
			_projectedLatLng = projectedLatLng;
			_seqIndex = seqIndex;
		}
	}

	final public static Comparator<NetCdfDataPoint> _BySeqOnly =
			new Comparator<>() {
				@Override
				public int compare(final NetCdfDataPoint o1,
						final NetCdfDataPoint o2) {
					final int seq1 = o1.getRiverSeqLcr()[1];
					final int seq2 = o2.getRiverSeqLcr()[1];
					return seq1 < seq2 ? -1 : (seq1 > seq2 ? 1 : 0);
				}
			};

	final private static Comparator<NetCdfDataPoint> _ByFlagOnly =
			new Comparator<>() {
				@Override
				public int compare(final NetCdfDataPoint o1,
						final NetCdfDataPoint o2) {
					/** The flag uniquely identifies the River, Strip, and seq. */
					final int[] riverSeqLcr1 = o1.getRiverSeqLcr();
					final int[] riverSeqLcr2 = o2.getRiverSeqLcr();
					for (int k = 0; k < riverSeqLcr1.length; ++k) {
						if (riverSeqLcr1[k] < riverSeqLcr2[k]) {
							return -1;
						} else if (riverSeqLcr1[k] > riverSeqLcr2[k]) {
							return 1;
						}
					}
					return 0;
				}
			};

	/**
	 * Reason for RiverSeqLcrMachinery; this is the pot of data and the
	 * routine that builds it.
	 */
	class InterpolationPot {
		final public NetCdfDataPoint _upstream;
		final public NetCdfDataPoint _downstream;
		final public LatLng3 _projection;
		final public double _nmiToUpstreamPoint;
		final public double _nmiToDownstreamPoint;

		private InterpolationPot(final NetCdfDataPoint upstream,
				final NetCdfDataPoint downstream, final LatLng3 projection,
				final double nmiToUpstreamPoint,
				final double nmiToDownstreamPoint) {
			_upstream = upstream;
			_downstream = downstream;
			_projection = projection;
			_nmiToUpstreamPoint = nmiToUpstreamPoint;
			_nmiToDownstreamPoint = nmiToDownstreamPoint;
		}
	}

	InterpolationPot getInterpolationPot(final SimCaseManager.SimCase simCase,
			final LatLng3 latLng) {
		/** Find the winnning strip and hence the winning river. */
		River.Strip winningStrip = null;
		double winningNmiToGca = Double.POSITIVE_INFINITY;
		GreatCircleArc.Projection winningPrj = null;
		for (final River river : _riverIdToRiver.values()) {
			for (int iPass = 0; iPass < 3; ++iPass) {
				if (_centerDominated && iPass != 1) {
					continue;
				}
				final River.Strip strip = iPass == 0 ? river._leftStrip :
						(iPass == 1 ? river._centerStrip : river._rightStrip);
				final GeoMtx gcaMtx = strip == null ? null : strip._gcaMtx;
				if (gcaMtx == null) {
					continue;
				}
				final GreatCircleArc.Projection prj = gcaMtx.findProjection(latLng);
				final double nmiToGca = prj.getRToGca() / _NmiToR;
				if (nmiToGca < winningNmiToGca) {
					winningNmiToGca = nmiToGca;
					winningStrip = strip;
					winningPrj = prj;
				}
			}
		}
		assert winningStrip != null : "Should have found something.";
		final LatLng3 prjLatLng = winningPrj.getClosestPointOnGca();
		if (!_centerDominated) {
			/** We're done. */
			final GreatCircleArc winningGca = winningPrj.getOwningGca();
			final LatLng3 upstreamLatLng = winningGca.getLatLng0();
			final LatLng3 downstreamLatLng = winningGca.getLatLng1();
			final NetCdfDataPoint upstream =
					_latLngToDataPoint.get(upstreamLatLng);
			final NetCdfDataPoint downstream =
					_latLngToDataPoint.get(downstreamLatLng);
			final double nmiToUpstreamPoint =
					MathX.haversineX(latLng, upstreamLatLng) / _NmiToR;
			final double nmiToDownstreamPoint =
					MathX.haversineX(latLng, downstreamLatLng) / _NmiToR;
			return new InterpolationPot(upstream, downstream, prjLatLng,
					nmiToUpstreamPoint, nmiToDownstreamPoint);
		}
		/**
		 * We are center-dominated, and we've found the closest center-strip
		 * point.
		 */
		final River winningRiver = winningStrip.getOwningRiver();
		final LatLng3 tgt0 = prjLatLng;
		final double nmi0 = winningPrj.getRToGca() / _NmiToR;
		final double hdgAwayFromTgt0 = MathX.initialHdgX(latLng, tgt0) + 180d;
		final GreatCircleArc gcaAwayFromTgt0 =
				GreatCircleArc.CreateGca(latLng, hdgAwayFromTgt0, nmi0);
		GreatCircleArc winningGca = winningPrj.getOwningGca();
		boolean ltWins = false;
		if (winningRiver._leftStrip != null) {
			final GeoMtx gcaMtx = winningRiver._leftStrip._gcaMtx;
			final CrossingPair2 xingPair =
					gcaMtx.findXing0(gcaAwayFromTgt0, /* findFirst= */true);
			if (xingPair != null && xingPair.hasCrossing()) {
				winningStrip = winningRiver._leftStrip;
				winningGca = xingPair._gca0 == gcaAwayFromTgt0 ? xingPair._gca1 :
						xingPair._gca0;
				ltWins = true;
			}
		}
		if (!ltWins && winningRiver._rightStrip != null) {
			final GeoMtx gcaMtx = winningRiver._rightStrip._gcaMtx;
			final CrossingPair2 xingPair =
					gcaMtx.findXing0(gcaAwayFromTgt0, /* findFirst= */true);
			if (xingPair != null && xingPair.hasCrossing()) {
				winningStrip = winningRiver._leftStrip;
				winningGca = xingPair._gca0 == gcaAwayFromTgt0 ? xingPair._gca1 :
						xingPair._gca0;
			}
		}

		final LatLng3 upstreamLatLng = winningGca.getLatLng0();
		final LatLng3 downstreamLatLng = winningGca.getLatLng1();
		final NetCdfDataPoint upstream = _latLngToDataPoint.get(upstreamLatLng);
		final NetCdfDataPoint downstream =
				_latLngToDataPoint.get(downstreamLatLng);
		final double nmiToUpstreamPoint =
				MathX.haversineX(latLng, upstreamLatLng) / _NmiToR;
		final double nmiToDownstreamPoint =
				MathX.haversineX(latLng, downstreamLatLng) / _NmiToR;
		return new InterpolationPot(upstream, downstream, prjLatLng,
				nmiToUpstreamPoint, nmiToDownstreamPoint);
	}

	/**
	 * The constructor partitions a collection of {@link NetCdfDataPoint}s and
	 * partitions them into {@link Strip}s, also computing a searchable set of
	 * the points that are not on the center. This "pot of data" class is
	 * critical when interpolating for river cases.
	 */
	class River {
		final Strip _leftStrip;
		final Strip _centerStrip;
		final Strip _rightStrip;

		class Strip {
			final int _lcr;
			GeoMtx _gcaMtx;
			final GreatCircleArc[] _gcaArray;

			Strip(final ArrayList<NetCdfDataPoint> dataPoints) {
				if (dataPoints == null || dataPoints.size() == 0) {
					_lcr = RiverSeqLcrUvCalculator._Center;
					_gcaMtx = null;
					_gcaArray = null;
					return;
				}
				final ArrayList<NetCdfDataPoint> myPointList = new ArrayList<>();
				final NetCdfDataPoint example = dataPoints.get(0);
				_lcr = example.getRiverSeqLcr()[2];
				for (final NetCdfDataPoint dataPoint : dataPoints) {
					if (dataPoint.getRiverSeqLcr()[2] == _lcr) {
						myPointList.add(dataPoint);
					}
				}
				final int nMine = myPointList.size();
				Collections.sort(myPointList, _BySeqOnly);
				if (nMine > 1) {
					final int nGcas = nMine - 1;
					_gcaArray = new GreatCircleArc[nGcas];
					for (int k = 0; k < nGcas; ++k) {
						final NetCdfDataPoint point0 = myPointList.get(k);
						final NetCdfDataPoint point1 = myPointList.get(k + 1);
						final LatLng3 latLng0 = point0.getLatLng();
						final LatLng3 latLng1 = point1.getLatLng();
						_gcaArray[k] = GreatCircleArc.CreateGca(latLng0, latLng1);
					}
					for (int k = 0; k < nGcas; ++k) {
						final GreatCircleArc pvs = k == 0 ? null : _gcaArray[k - 1];
						final GreatCircleArc gca = _gcaArray[k];
						final GreatCircleArc nxt =
								k == (nGcas - 1) ? null : _gcaArray[k + 1];
						gca.setGcaSequence(/* gcaSequence= */null, pvs, nxt);
					}
					_gcaMtx = new GeoMtx(_gcaArray);
				} else {
					_gcaArray = null;
					_gcaMtx = null;
				}
			}

			public String getString() {
				final int nGcas = _gcaArray == null ? 0 : _gcaArray.length;
				if (nGcas == 0) {
					return "Vacuous Strip";
				}
				final GreatCircleArc gca0 = _gcaArray[0];
				final LatLng3 latLng0 = gca0.getLatLng0();
				final NetCdfDataPoint example = _latLngToDataPoint.get(latLng0);
				final int lcr = example.getRiverSeqLcr()[2];
				final String stripName = RiverSeqLcrUvCalculator.getStripName(lcr);
				final GreatCircleArc gcaN = _gcaArray[nGcas - 1];
				final LatLng3 latLng1 = gcaN.getLatLng1();
				final GreatCircleArc fullLengthGca =
						GreatCircleArc.CreateGca(latLng0, latLng1);
				return String.format("Strip[%s] FullGca: %s, nGcas[%d]", stripName,
						fullLengthGca.getDisplayString(), nGcas);
			}

			River getOwningRiver() {
				return River.this;
			}

			public void freeMemory() {
				_gcaMtx = null;
			}
		}

		River(final SimCaseManager.SimCase simCase,
				final ArrayList<NetCdfDataPoint> dataPoints) {
			final ArrayList<NetCdfDataPoint> leftPoints = new ArrayList<>();
			final ArrayList<NetCdfDataPoint> centerPoints = new ArrayList<>();
			final ArrayList<NetCdfDataPoint> rightPoints = new ArrayList<>();
			for (final NetCdfDataPoint dataPoint : dataPoints) {
				final int lcr = dataPoint.getRiverSeqLcr()[2];
				if (lcr == RiverSeqLcrUvCalculator._Left) {
					leftPoints.add(dataPoint);
				} else if (lcr == RiverSeqLcrUvCalculator._Center) {
					centerPoints.add(dataPoint);
				} else if (lcr == RiverSeqLcrUvCalculator._Right) {
					rightPoints.add(dataPoint);
				}
			}
			_leftStrip = leftPoints.size() > 0 ? new Strip(leftPoints) : null;
			_centerStrip =
					centerPoints.size() > 0 ? new Strip(centerPoints) : null;
			_rightStrip = rightPoints.size() > 0 ? new Strip(rightPoints) : null;
		}

		public String getString() {
			final String leftString =
					_leftStrip == null ? "NULL" : _leftStrip.getString();
			final String centerString =
					_centerStrip == null ? "NULL" : _centerStrip.getString();
			final String rightString =
					_rightStrip == null ? "NULL" : _rightStrip.getString();
			return String.format("\nLeft[%s]\nCenter[%s]\nRight[%s]", leftString,
					centerString, rightString);
		}

		boolean isValid() {
			if (_centerDominated) {
				return _centerStrip != null;
			}
			return _leftStrip != null && _centerStrip != null &&
					_rightStrip != null;
		}

		public void freeMemory() {
			if (_leftStrip != null) {
				_leftStrip.freeMemory();
			}
			if (_centerStrip != null) {
				_centerStrip.freeMemory();
			}
			if (_rightStrip != null) {
				_rightStrip.freeMemory();
			}
		}
	}

	public boolean canDoRiverineInterpolation() {
		if (_riverIdToRiver == null || _riverIdToRiver.isEmpty()) {
			return false;
		}
		for (final River river : _riverIdToRiver.values()) {
			if (!river.isValid()) {
				return false;
			}
		}
		return true;
	}

	public boolean isEmpty() {
		return _latLngToDataPoint == null || _latLngToDataPoint.isEmpty();
	}

	public void freeMemory() {
		if (_riverIdToRiver != null) {
			for (final River river : _riverIdToRiver.values()) {
				river.freeMemory();
			}
			_riverIdToRiver.clear();
		}
		if (_latLngToDataPoint != null) {
			_latLngToDataPoint.clear();
			_dataPointToLatLng.clear();
		}
	}
}
