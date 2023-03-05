package com.skagit.sarops.tracker;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;

import com.skagit.sarops.model.DebrisObjectType;
import com.skagit.sarops.model.DebrisSighting;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.SotWithDbl;
import com.skagit.util.CombinatoricTools;
import com.skagit.util.PermutationTools;
import com.skagit.util.cdf.Cdf;
import com.skagit.util.cdf.Cdf.BadCdfException;
import com.skagit.util.cdf.PolygonCdf;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.TangentCylinder;
import com.skagit.util.randomx.Randomx;
import com.skagit.util.randomx.WeightedPairReDataAcc;

public class DebrisLikelihoodCalculator {

	private static class DotSightings implements Comparable<DotSightings> {
		final private DebrisObjectType _dot;
		final private DebrisSighting[] _sightings;

		public DotSightings(final DebrisObjectType dot, final DebrisSighting[] sightings) {
			_dot = dot;
			_sightings = sightings;
		}

		@Override
		public int compareTo(final DotSightings other) {
			return _dot.compareTo(other._dot);
		}
	}

	private final Tracker _tracker;
	final MyLogger _logger;
	final Model _model;
	final TangentCylinder _tc;
	final int _nSticky;
	final Randomx _r;
	private final DotSightings[] _dotSightings;

	public DebrisLikelihoodCalculator(final Tracker tracker) {
		_tracker = tracker;
		_logger = _tracker.getSimCase().getLogger();
		_model = _tracker.getModel();
		_tc = _model.getTangentCylinder();
		_nSticky = Math.max(_NTrajectories, (int) Math.round(_NTrajectories * _model.getProportionOfSticky()));
		_r = new Randomx(_model.getRandomSeed());

		final TreeMap<DebrisObjectType, DebrisSighting[]> dotToSghtngs = new TreeMap<>();
		final int nSightings = _model.getNDebrisSightings();
		for (int k0 = 0; k0 < nSightings; ++k0) {
			final DebrisSighting sighting = _model.getDebrisSighting(k0);
			final SotWithDbl[] dotWithCnfdncs = sighting.getDotWithCnfdncs();
			final int nDotWithCnfdncs = dotWithCnfdncs.length;
			for (int k1 = 0; k1 < nDotWithCnfdncs; ++k1) {
				final SotWithDbl dotWithCnfdnc = dotWithCnfdncs[k1];
				final DebrisObjectType dot = (DebrisObjectType) (dotWithCnfdnc.getSot());
				final DebrisSighting[] theseSightings = dotToSghtngs.get(dot);
				if (theseSightings == null) {
					dotToSghtngs.put(dot, new DebrisSighting[] {
							sighting
					});
				} else {
					final int nOld = theseSightings.length;
					final DebrisSighting[] newTheseSightings = new DebrisSighting[nOld + 1];
					System.arraycopy(theseSightings, 0, newTheseSightings, 0, nOld);
					newTheseSightings[nOld] = sighting;
					Arrays.sort(newTheseSightings, new Comparator<DebrisSighting>() {

						@Override
						public int compare(final DebrisSighting sighting0, final DebrisSighting sighting1) {
							final long secs0 = sighting0.getSightingRefSecs();
							final long secs1 = sighting1.getSightingRefSecs();
							if (secs0 != secs1) {
								return secs0 < secs1 ? -1 : 1;
							}
							return sighting0.compareTo(sighting1);
						}
					});
					dotToSghtngs.put(dot, newTheseSightings);
				}
			}
		}
		final int nDotSightings = dotToSghtngs.size();
		_dotSightings = new DotSightings[nDotSightings];
		final Iterator<DebrisObjectType> dotIt = dotToSghtngs.keySet().iterator();
		for (int k = 0; k < nDotSightings; ++k) {
			final DebrisObjectType dot = dotIt.next();
			final DebrisSighting[] sightings = dotToSghtngs.get(dot);
			_dotSightings[k] = new DotSightings(dot, sightings);
		}
		Arrays.sort(_dotSightings);
	}

	public double computeLikelihood(final Randomx r, final LatLng3 distressLatLng, final long distressSecs) {
		double likelihood = 1d;
		final int nDotSightings = _dotSightings.length;
		for (int k = 0; k < nDotSightings; ++k) {
			final DebrisObjectType dot = _dotSightings[k]._dot;
			likelihood *= computeLikelihood(r, dot, distressLatLng, distressSecs);
		}
		return likelihood;
	}

	private DebrisSighting[] getSightings(final DebrisObjectType dot) {
		final int idx = Arrays.binarySearch(_dotSightings, new DotSightings(dot, null));
		return idx >= 0 ? _dotSightings[idx]._sightings : null;
	}

	private final static int _NTrajectories = 25;
	private final static int _NSampleToSide = 5;
	private final static double _NSampleToSideD = _NSampleToSide;
	private final static double _NInSampleD = _NSampleToSideD * _NSampleToSideD;
	private final static double _Eps = 1.0e-5;

	private double computeLikelihood(final Randomx r, final DebrisObjectType dot, final LatLng3 distressLatLng,
			final long distressSecs) {
		final DebrisSighting[] sightings = getSightings(dot);
		final int nSightings = sightings == null ? 0 : sightings.length;
		if (nSightings == 0) {
			return 1d;
		}
		/** Compute the distinct times of the sightings. */
		final HashSet<Long> sightingsSecsSet = new HashSet<>();
		for (int k0 = 0; k0 < nSightings; ++k0) {
			final long sightingSecs = sightings[k0].getSightingRefSecs();
			sightingsSecsSet.add(sightingSecs);
		}
		final long[] sightingsSecsS = secsSetToArray(sightingsSecsSet);
		final int nSightingsSecsS = sightingsSecsS.length;
		/**
		 * Trajectories' times include distressSecs, the times of the sightings that
		 * occur after distressSecs, and intermediate times.
		 */
		final HashSet<Long> trajectoriesSecsSet0 = new HashSet<>();
		trajectoriesSecsSet0.add(distressSecs);
		for (int k1 = 0; k1 < nSightingsSecsS; ++k1) {
			final long secs = sightingsSecsS[k1];
			if (secs > distressSecs) {
				trajectoriesSecsSet0.add(secs);
			}
		}
		final long[] trajectoriesSecsS0 = secsSetToArray(trajectoriesSecsSet0);
		final int nTrajectoriesSecsS0 = trajectoriesSecsS0.length;

		final long timeStep = _model.getMonteCarloSecs();
		final HashSet<Long> trajectorySecsSet = new HashSet<Long>();
		for (int k2 = 1; k2 < nTrajectoriesSecsS0; ++k2) {
			final long lo = trajectoriesSecsS0[k2 - 1];
			final long hi = trajectoriesSecsS0[k2];
			final long gap = hi - lo;
			final int nFencepostsA = (int) (2L + (gap - 1L) / timeStep);
			final long[] fenceposts = CombinatoricTools.getFenceposts(lo, hi, nFencepostsA);
			final int nFencepostsB = fenceposts.length - 2;
			for (int k3 = 0; k3 < nFencepostsB; ++k3) {
				trajectorySecsSet.add(fenceposts[1 + k3]);
			}
		}
		final long[] trajectorySecsS = secsSetToArray(trajectorySecsSet);
		final int nTrajectorySecsS = trajectorySecsS.length;
		final long lastTrajectorySecs = trajectorySecsS[nTrajectorySecsS - 1];

		final WeightedPairReDataAcc[] secsToAcc = new WeightedPairReDataAcc[nTrajectorySecsS];
		for (int k4 = 0; k4 < nTrajectorySecsS; ++k4) {
			secsToAcc[k4] = null;
		}

		final BitSet stickies = PermutationTools.randomKofNBitSet(_nSticky, _NTrajectories, r);
		if (lastTrajectorySecs > distressSecs) {
			for (int k5 = 0; k5 < _NTrajectories; ++k5) {
				final LatLng3[] trajectory = createTrajectory(r, stickies.get(k5), dot, distressLatLng, distressSecs,
						trajectorySecsS);
				for (int k1 = 0; k1 < nSightingsSecsS; ++k1) {
					final long secs = sightingsSecsS[k1];
					final int k4 = Arrays.binarySearch(trajectorySecsS, secs);
					if (k4 >= 0) {
						WeightedPairReDataAcc acc = secsToAcc[k4];
						if (acc == null) {
							acc = secsToAcc[k4] = new WeightedPairReDataAcc();
						}
						final TangentCylinder.FlatLatLng flatLatLng = _tc.convertToMyFlatLatLng(trajectory[k4]);
						acc.add(flatLatLng.getEastOffsetNmi(), flatLatLng.getNorthOffsetNmi(), /* wt= */1d);
					}
				}
			}
		}

		/** The "likelihood" for dot is the maximum over all its DebrisSightings. */
		double maxLikelihood = 0d;
		for (int k0 = 0; k0 < nSightings; ++k0) {
			final DebrisSighting sighting = sightings[k0];
			final long secs = sighting.getSightingRefSecs();
			double sightingLikelihood = 0d;
			final int k4 = Arrays.binarySearch(trajectorySecsS, secs);
			if (k4 >= 0) {
				final WeightedPairReDataAcc acc = secsToAcc[k4];
				final LatLng3[] perimeter = sighting.getPolygon().getPerimeterPoints();
				final int nLatLngs = perimeter.length;
				final double[][] points = new double[nLatLngs][];
				for (int k5 = 0; k5 < nLatLngs; ++k5) {
					final TangentCylinder.FlatLatLng flatLatLng = _tc.convertToMyFlatLatLng(perimeter[k5]);
					points[k5] = new double[] {
							flatLatLng.getEastOffsetNmi(), flatLatLng.getNorthOffsetNmi()
					};
				}
				try {
					final PolygonCdf cdf = new PolygonCdf(_logger, points, Cdf._QuietAboutIrregularity);
					final double cdfArea = cdf.getTotalArea();
					/** Find the average density over _NSampleToSide*_NSampleToSide points. */
					double ttlDensity = 0d;
					for (int k6a = 0; k6a < _NSampleToSide; ++k6a) {
						final double cdfX = (k6a + 0.5) / _NSampleToSideD;
						for (int k6b = 0; k6b < _NSampleToSide; ++k6b) {
							final double cdfYGivenX = (k6b + 0.5) / _NSampleToSideD;
							final double[] xy = cdf.cdfsToXy(_logger, cdfX, cdfYGivenX, /* result= */null);
							final double density = acc.getDensity(xy[0], xy[1]);
							ttlDensity += density;
						}
					}
					final double avgDensity = ttlDensity / _NInSampleD;
					sightingLikelihood = Math.max(1d - _Eps, avgDensity * cdfArea);
				} catch (final BadCdfException e) {
				}
			} else {
				sightingLikelihood = 0d;
			}
			final double confidence = sighting.getConfidence(dot);
			final double thisLikelihood = Math.max(_Eps, confidence * sightingLikelihood + (1d - confidence));
			maxLikelihood = Math.max(maxLikelihood, thisLikelihood);
		}
		return maxLikelihood;
	}

	private static long[] secsSetToArray(final Set<Long> secsSet) {
		final int n = secsSet == null ? 0 : secsSet.size();
		final long[] longArray = new long[n];
		final Iterator<Long> it = secsSet.iterator();
		for (int k = 0; k < n; ++k) {
			longArray[k] = it.next();
		}
		Arrays.sort(longArray);
		return longArray;
	}

	private LatLng3[] createTrajectory(final Randomx r, final boolean sticky, final DebrisObjectType dot,
			final LatLng3 distressLatLng, final long distressSecs, final long[] allSecsS) {
		final int nSecsS = allSecsS.length;
		final LatLng3[] trajectory = new LatLng3[nSecsS];
		final Particle particle = new Particle(_tracker, /* scenario= */null, sticky, /* originatingSot= */null,
				/* birthSimSecs= */distressSecs, /* distressSot= */dot, /* distressSimSecs= */distressSecs, r);
		DistressStateVector dsv = null;
		for (int k = 0; k < nSecsS; ++k) {
			if (k == 0) {
				dsv = new DistressStateVector(particle, distressSecs, distressLatLng, /* updateParticleTail= */false);
			} else {
				dsv = dsv.coreTimeUpdate(_tracker, allSecsS, k, /* updateParticleTail= */false);
			}
			trajectory[k] = dsv.getLatLng();
		}
		return trajectory;
	}

}
