package com.skagit.sarops.tracker;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
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
	private final DotSightings[] _dotSightings;
	final Randomx _r;

	public DebrisLikelihoodCalculator(final Tracker tracker) {
		_tracker = tracker;
		final Model model = _tracker.getModel();
		_r = new Randomx(model.getRandomSeed());
		final TreeMap<DebrisObjectType, DebrisSighting[]> dotToSghtngs = new TreeMap<>();
		final int nAllSightings = model.getNDebrisSightings();
		for (int k0 = 0; k0 < nAllSightings; ++k0) {
			final DebrisSighting sighting = model.getDebrisSighting(k0);
			final SotWithDbl[] dotWithCnfdncs = sighting.getDotWithCnfdncs();
			final int nDotWithCnfdncs = dotWithCnfdncs.length;
			for (int k1 = 0; k1 < nDotWithCnfdncs; ++k1) {
				final SotWithDbl dotWithCnfdnc = dotWithCnfdncs[k1];
				final DebrisObjectType dot = (DebrisObjectType) (dotWithCnfdnc.getSot());
				final DebrisSighting[] sightings = dotToSghtngs.get(dot);
				if (sightings == null) {
					dotToSghtngs.put(dot, new DebrisSighting[] {
							sighting
					});
				} else {
					final int nOld = sightings.length;
					final DebrisSighting[] sightingsX = new DebrisSighting[nOld + 1];
					System.arraycopy(sightings, 0, sightingsX, 0, nOld);
					sightingsX[nOld] = sighting;
					Arrays.sort(sightingsX, new Comparator<DebrisSighting>() {

						@Override
						public int compare(final DebrisSighting sighting0, final DebrisSighting sighting1) {
							final long refSecs0 = sighting0.getSightingRefSecs();
							final long refSecs1 = sighting1.getSightingRefSecs();
							if (refSecs0 != refSecs1) {
								return refSecs0 < refSecs1 ? -1 : 1;
							}
							return sighting0.compareTo(sighting1);
						}
					});
					dotToSghtngs.put(dot, sightingsX);
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
		return _dotSightings[idx]._sightings;
	}

	private final static int _NTrajectories = 25;
	private final static int _NSampleToSide = 5;
	private final static double _NSampleToSideD = _NSampleToSide;
	private final static double _NInSampleD = _NSampleToSideD * _NSampleToSideD;
	private final static double _Eps = 1.0e-5;

	private double computeLikelihood(final Randomx r, final DebrisObjectType dot, final LatLng3 distressLatLng,
			final long distressSecs) {
		final DebrisSighting[] sightings = getSightings(dot);
		final int nSightings = sightings.length;
		final long lastSightingSecs = sightings[nSightings - 1].getSightingRefSecs();
		/**
		 * Compute all the secs that we want our drifts to stop at. These include the
		 * sightings' secs as well as intermediate steps.
		 */
		final HashSet<Long> secsSet = new HashSet<>();
		secsSet.add(distressSecs);
		for (int k = 0; k < nSightings; ++k) {
			final long refSecs = sightings[k].getSightingRefSecs();
			if (refSecs > distressSecs) {
				secsSet.add(refSecs);
			}
		}
		final long gap = lastSightingSecs - distressSecs;
		if (gap > 0) {
			final long timeStep = _tracker.getModel().getMonteCarloSecs();
			final int nFenceposts0 = (int) (2L + (gap - 1L) / timeStep);
			final long[] fenceposts = CombinatoricTools.getFenceposts(distressSecs, lastSightingSecs, nFenceposts0);
			final int nFenceposts1 = fenceposts.length;
			for (int k = 0; k < nFenceposts1; ++k) {
				secsSet.add(fenceposts[k]);
			}
		}
		final int nAllSecsS = secsSet.size();
		final long[] allSecsS = new long[nAllSecsS];
		final Iterator<Long> it = secsSet.iterator();
		for (int k = 0; k < nAllSecsS; ++k) {
			allSecsS[k] = it.next();
		}
		Arrays.sort(allSecsS);
		if (true) {
			return 1d;
		}

		final LatLng3[][] trajectories = new LatLng3[_NTrajectories][];
		final int nSticky = Math.max(_NTrajectories,
				(int) Math.round(_NTrajectories * _tracker.getModel().getProportionOfSticky()));
		final BitSet stickies = PermutationTools.randomKofNBitSet(nSticky, _NTrajectories, r);

		for (int k = 0; k < _NTrajectories; ++k) {
			trajectories[k] = createTrajectory(r, stickies.get(k), dot, distressLatLng, distressSecs, allSecsS);
		}
		final TreeMap<Long, WeightedPairReDataAcc> secsToAcc = new TreeMap<>();
		final TreeMap<Long, TangentCylinder> secsToTc = new TreeMap<>();
		for (int k0 = 0; k0 < nSightings; ++k0) {
			final DebrisSighting sighting = sightings[k0];
			final long secs = sighting.getSightingRefSecs();
			if (secsToAcc.get(secs) == null) {
				final int k1 = Arrays.binarySearch(allSecsS, secs);
				final LatLng3[] latLngs = new LatLng3[_NTrajectories];
				for (int k2 = 0; k2 < _NTrajectories; ++k2) {
					latLngs[k2] = trajectories[k2][k1];
				}
				final TangentCylinder tc = TangentCylinder.getTangentCylinder(latLngs, /* wts= */null);
				secsToTc.put(secs, tc);
				final WeightedPairReDataAcc acc = new WeightedPairReDataAcc();
				for (int k2 = 0; k2 < _NTrajectories; ++k2) {
					final TangentCylinder.FlatLatLng flatLatLng = tc.convertToMyFlatLatLng(latLngs[k2]);
					acc.add(flatLatLng.getEastOffsetNmi(), flatLatLng.getNorthOffsetNmi(), /* wt= */1d);
				}
				secsToAcc.put(secs, acc);
			}
		}

		/** The "likelihood" for dot is the maximum over all its DebrisSightings. */
		double maxLikelihood = 0d;
		for (int k0 = 0; k0 < nSightings; ++k0) {
			final DebrisSighting sighting = sightings[k0];
			final long secs = sighting.getSightingRefSecs();
			final WeightedPairReDataAcc acc = secsToAcc.get(secs);
			final TangentCylinder tc = secsToTc.get(sighting.getSightingRefSecs());
			//
			final LatLng3[] polygon0 = sighting.getPolygon().getPerimeterPoints();
			final int nLatLngs = polygon0.length;
			final double[][] points = new double[nLatLngs][];
			for (int k = 0; k < nLatLngs; ++k) {
				final TangentCylinder.FlatLatLng flatLatLng = tc.convertToMyFlatLatLng(polygon0[k]);
				points[k] = new double[] {
						flatLatLng.getEastOffsetNmi(), flatLatLng.getNorthOffsetNmi()
				};
			}
			double liveLikelihood = 0d;
			try {
				final MyLogger logger = _tracker.getSimCase().getLogger();
				final PolygonCdf cdf = new PolygonCdf(logger, points, Cdf._QuietAboutIrregularity);
				final double cdfArea = cdf.getTotalArea();
				/** Find the average density over _NSampleToSide*_NSampleToSide points. */
				double ttlDensity = 0d;
				for (int kA = 0; kA < _NSampleToSide; ++kA) {
					final double cdfX = kA / _NSampleToSideD + 0.5;
					for (int kB = 0; kB < _NSampleToSide; ++kB) {
						final double cdfYGivenX = kB / _NSampleToSideD + 0.5;
						final double[] xy = cdf.cdfsToXy(logger, cdfX, cdfYGivenX, /* result= */null);
						final double density = acc.getDensity(xy[0], xy[1]);
						ttlDensity += density;
					}
				}
				final double avgDensity = ttlDensity / _NInSampleD;
				liveLikelihood = Math.max(1d - _Eps, avgDensity * cdfArea);
			} catch (final BadCdfException e) {
				liveLikelihood = 0d;
			}
			final double confidence = sighting.getConfidence(dot);
			final double thisLikelihood = Math.max(_Eps, confidence * liveLikelihood + (1d - confidence));
			maxLikelihood = Math.max(maxLikelihood, thisLikelihood);
		}
		return maxLikelihood;
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
