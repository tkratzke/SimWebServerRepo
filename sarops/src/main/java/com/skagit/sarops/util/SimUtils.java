package com.skagit.sarops.util;

public class SimUtils {
	final public static double _MinProbDetect;
	final public static double _MaxProbDetect;
	static {
		final SimGlobalStrings simGlobalStrings =
				SimGlobalStrings.getStaticSimGlobalStrings();
		_MinProbDetect = simGlobalStrings.getMinProbDetect();
		_MaxProbDetect = simGlobalStrings.getMaxProbDetect();
	}

	public static double getLogOddsMaxPd(double ap0, double a1, double a2,
			double maxXValue) {
		/** Clean up inputs. */
		if (!Double.isFinite(ap0)) {
			ap0 = 0d;
		}
		if (!Double.isFinite(a1)) {
			a1 = 0d;
		}
		if (!Double.isFinite(a2)) {
			a2 = 0d;
		}

		if (!Double.isFinite(maxXValue) || maxXValue < 0d) {
			maxXValue = Double.POSITIVE_INFINITY;
		}
		/** 0d is one endpoint. */
		final double q0 = ap0;
		double maxQ = q0;
		if (a2 != 0d) {
			/** bona fide quadratic. */
			/** Compute the right hand border; either maxXValue or +inf. */
			if (Double.isInfinite(maxXValue)) {
				if (a2 > 0d) {
					return 1d;
				}
			} else {
				final double q1 = (a2 * maxXValue + a1) * maxXValue + ap0;
				maxQ = Math.max(maxQ, q1);
			}
			/** Interior critical point as well. */
			final double midRange = -a1 / (2d * a2);
			if (0d < midRange && midRange < maxXValue) {
				final double q2 = (a2 * midRange + a1) * midRange + ap0;
				maxQ = Math.max(maxQ, q2);
			}
		} else if (a1 != 0d) {
			/** Not quadratic, but bona fide linear. */
			if (Double.isInfinite(maxXValue)) {
				if (a1 > 0d) {
					return 1d;
				}
			} else {
				final double q3 = a1 * maxXValue + ap0;
				maxQ = Math.max(maxQ, q3);
			}
		}
		final double maxPd = 1d / (1d + Math.exp(-maxQ));
		return maxPd >= _MinProbDetect ? maxPd : 0d;
	}

	public static void main(final String[] args) {
		final double ap0 = 5.28395899644369926;
		final double a1 = -2.62862019317526929;
		final double a2 = -0.28910017589801196;
		final double maxXValue = 1.0e+20;
		final double maxPd = getLogOddsMaxPd(ap0, a1, a2, maxXValue);
		System.out.printf(
				"[ap0,a1,a2,maxRange]=[%.3f,%.3f,%.3g,%.3f]: MaxPd[%.3f]", ap0, a1,
				a2, maxXValue, maxPd);
	}
}
