package com.skagit.sarops.tracker;

import org.apache.commons.math3.special.Erf;

import com.skagit.sarops.tracker.lrcSet.LateralRangeCurve;
import com.skagit.util.Constants;
import com.skagit.util.Integrator;

public class CoverageToPodCurve implements Integrator.IntegrableFunction {

	private double[][] _cache;
	final private LateralRangeCurve _lateralRangeCurve;

	public CoverageToPodCurve(final LateralRangeCurve lateralRangeCurve) {
		_cache = null;
		_lateralRangeCurve = lateralRangeCurve;
	}

	/**
	 * Used to approximate coverageToPod values by interpolating from a cache.
	 */
	private double interpolate(final int tooLow, final double x) {
		final double w = (x - _cache[tooLow][0]) /
				(_cache[tooLow + 1][0] - _cache[tooLow][0]);
		return w * _cache[tooLow + 1][1] + (1 - w) * _cache[tooLow][1];
	}

	private boolean interpolationIsSufficient(final int tooLow,
			final double x) {
		final int lengthOfCache = _cache == null ? 0 : _cache.length;
		if (lengthOfCache < 8 || tooLow < 0 || tooLow >= lengthOfCache - 1) {
			return false;
		} else if (_cache[tooLow][1] >= 1.001 * _cache[tooLow + 1][1]) {
			return false;
		} else {
			return true;
		}
	}

	private double functionValue(final double spacingInSweepWidths) {
		final int oldLength = _cache == null ? 0 : _cache.length;
		int tooLow = -1;
		int tooHigh = oldLength;
		/** Find tooLow or an exact match. */
		while (tooLow < tooHigh - 1) {
			final int pairIndex = (tooLow + tooHigh) / 2;
			final double[] pair = _cache[pairIndex];
			if (pair[0] == spacingInSweepWidths) {
				return pair[1];
			} else if (pair[0] < spacingInSweepWidths) {
				tooLow = pairIndex;
			} else {
				tooHigh = pairIndex;
			}
		}
		/** No exact match. If interpolation is legal, interpolate. */
		if (interpolationIsSufficient(tooLow, spacingInSweepWidths)) {
			return interpolate(tooLow, spacingInSweepWidths);
		}
		/** Compute and store the result in _cache... */
		final double value = coreFunction(spacingInSweepWidths);
		final double[] cacheEntry =
				new double[] { spacingInSweepWidths, value };
		if (oldLength == 0) {
			_cache = new double[][] { cacheEntry };
		} else {
			final double[][] newCache = new double[oldLength + 1][];
			boolean havePutAwayNewOne = false;
			for (int i = oldLength - 1; i >= 0; --i) {
				if (havePutAwayNewOne) {
					newCache[i] = _cache[i];
				} else {
					final double[] incumbentCacheEntry = _cache[i];
					if (incumbentCacheEntry[0] > cacheEntry[0]) {
						newCache[i + 1] = incumbentCacheEntry;
					} else {
						newCache[i + 1] = cacheEntry;
						newCache[i] = incumbentCacheEntry;
						havePutAwayNewOne = true;
					}
				}
			}
			if (!havePutAwayNewOne) {
				newCache[0] = cacheEntry;
			}
			_cache = newCache;
		}
		/** and return what you computed. */
		return value;
	}

	private double cpaToPod(final double cpaNmi) {
		if (_lateralRangeCurve != null) {
			if (_lateralRangeCurve.isLtRt()) {
				return Math.max(_lateralRangeCurve.ltCpaToPod(cpaNmi),
						_lateralRangeCurve.rtCpaToPod(cpaNmi));
			}
			return Math.max(_lateralRangeCurve.upCpaToPod(cpaNmi),
					_lateralRangeCurve.dnCpaToPod(cpaNmi));
		}
		return 0d;
	}

	@Override
	public double valueAt(final double x) {
		return cpaToPod(x);
	}

	private double coreFunction(final double spacingInSweepWidths) {
		/**
		 * Keep adding in passes until they amount to virtually nothing. Start
		 * with a pass at 0d. Then passes at 1d and -1d, passes at 2d and -2d,
		 * etc..
		 */
		final double sweepWidth = _lateralRangeCurve.getSweepWidth();
		double lnOfMissing = Math.log(1d - cpaToPod(sweepWidth));
		for (int i = 1;; ++i) {
			final double thisLnOfMissing1 =
					Math.log(1d - cpaToPod((spacingInSweepWidths + i) * sweepWidth));
			final double thisLnOfMissing2 =
					Math.log(1 - cpaToPod((spacingInSweepWidths - i) * sweepWidth));
			lnOfMissing += thisLnOfMissing1 + thisLnOfMissing2;
			if (thisLnOfMissing1 > -0.001 && thisLnOfMissing2 > -0.001) {
				break;
			}
		}
		return 1 - Math.exp(lnOfMissing);
	}

	/**
	 * Override if there is a nice closed form. The default is to use
	 * interpolation, and getting the sample points to interpolate between, is
	 * quite heavy.
	 */
	public double coverageToPod(final double coverage) {
		if (_lateralRangeCurve.getSweepWidth() == 0d) {
			return 0d;
		}
		final double spacingInSweepWidths = 1d / coverage;
		return functionValue(spacingInSweepWidths);
	}

	/**
	 * Override if there is a nice closed form. The default is to use the
	 * following binary search.
	 */
	public double podToCoverage(final double pod) {
		if (coverageToPod(0d) > pod) {
			return -1d;
		}
		double coverageLow = 0d;
		double coverageHigh = 1d;
		while (coverageToPod(coverageHigh) < pod) {
			coverageLow = coverageHigh;
			coverageHigh *= 2d;
		}
		while (coverageLow < coverageHigh * 0.99) {
			final double coverage = (coverageLow + coverageHigh) / 2d;
			final double thisPod = coverageToPod(coverage);
			if (thisPod > pod) {
				coverageHigh = coverage;
			} else if (thisPod < pod) {
				coverageLow = coverage;
			} else {
				return coverage;
			}
		}
		return coverageLow;
	}

	/** Override if there is a nice closed form. */
	public double derivativeOfPodWrtCoverage(final double coverage) {
		final double spacingInSweepWidths = 1d / coverage;
		double h = 1d;
		double low, high;
		double quotient1 = Double.NaN, quotient2 = Double.NaN;
		for (int i = 0; i < 10; ++i) {
			quotient1 = quotient2;
			h /= 2d;
			low = functionValue(spacingInSweepWidths - h / 2d);
			high = functionValue(spacingInSweepWidths + h / 2d);
			quotient2 = (high - low) / h;
			if (!Double.isNaN(quotient1)) {
				final double lhs;
				if (quotient1 > quotient2) {
					lhs = quotient1 - quotient2;
				} else {
					lhs = quotient2 - quotient1;
				}
				final double epsilon = 1d / 16d;
				final double rhs =
						(quotient1 > 0 ? quotient1 : -quotient1) * epsilon;
				if (lhs <= rhs) {
					return quotient2;
				}
			}
		}
		return quotient2;
	}

	/**
	 * Use a binary search to find the coverage for which the derivative of
	 * pod wrt coverage is targetDerivative. We assume that the derivative is
	 * decreasing. util is some user-defined Object, typically null.
	 */
	public double inverseOfDerivativeOfPodWrtCoverage(
			final double targetDerivative) {
		/**
		 * If we're being asked to find a coverage for which the derivative is
		 * greater than the derivative at 0d, return -1d.
		 */
		if (targetDerivative > derivativeOfPodWrtCoverage(0)) {
			return -1d;
		}
		/** Binary search. Find the upper coverage. */
		double coverageLow = 0d, coverageHigh = 1d;
		while (derivativeOfPodWrtCoverage(coverageHigh) > targetDerivative) {
			coverageLow = coverageHigh;
			coverageHigh *= 2;
		}
		while (coverageLow < coverageHigh * 0.99) {
			final double coverage = (coverageLow + coverageHigh) / 2;
			final double thisDerivative = derivativeOfPodWrtCoverage(coverage);
			if (thisDerivative > targetDerivative) {
				coverageLow = coverage;
			} else if (thisDerivative < targetDerivative) {
				coverageHigh = coverage;
			} else {
				return coverage;
			}
		}
		return coverageHigh;
	}

	/** The 2 standard CoverageToPodCurves; exp and erf. */
	final public static CoverageToPodCurve _ExpCurve =
			new CoverageToPodCurve(/* lateralRangeCurve= */null) {
				@Override
				public double coverageToPod(final double coverage) {
					return 1d - Math.exp(-coverage);
				}

				@Override
				public double derivativeOfPodWrtCoverage(final double coverage) {
					return Math.exp(-coverage);
				}

				@Override
				public double inverseOfDerivativeOfPodWrtCoverage(
						final double derivative) {
					return -Math.log(derivative);
				}

				@Override
				public double podToCoverage(final double pod) {
					return -Math.log(1d - pod);
				}
			};

	final public static CoverageToPodCurve _ErfCurve =
			new CoverageToPodCurve(/* lateralRangeCurve= */null) {
				@Override
				public double coverageToPod(final double coverage) {
					return Erf.erf(Math.sqrt(Math.PI) / 2d * coverage);
				}

				@Override
				public double derivativeOfPodWrtCoverage(final double coverage) {
					return Math.exp(-Constants._PiOver4 * coverage * coverage);
				}

				@Override
				public double inverseOfDerivativeOfPodWrtCoverage(
						final double derivative) {
					return Math.sqrt(-Math.log(derivative) / Math.PI * 4d);
				}

				@Override
				public double podToCoverage(final double pod) {
					/**
					 * Until further notice, we'll just let the binary search do its
					 * thing.
					 */
					return super.podToCoverage(pod);
				}
			};
}
