package com.skagit.sarops.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.skagit.util.MathX;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.TangentCylinder;

public class AnnulusIntersector {
	final private static double _NmiToR = MathX._NmiToR;
	/**
	 * <pre>
	 * The problem is:
	 * 1.	The Sru's field-of-view given by:
	 *    a.	Two angles relative to its direction (e.g., 92.5 ccw to 87.5 ccw
	 *    		(I'm using "math angles" here, but in degrees instead of radians))
	 *    		i. The angles in this example arise from an Sru moving from the origin along the positive x axis,
	 *    				and looking to its left (because say, creep is to the left), with a half angle of 2.5 degrees.
	 *    b.	minRange, maxRange
	 * 2. A particle moving in a straight line.
	 * 3.	This occurs between times 0 and 1.
	Find the interval(s)  in [0,1] when the second straight line is within the annular section described by the first one.
	 * </pre>
	 */
	/** A collection of disjoint intervals between 0 and 1. */
	final public double[][] _tPairs;

	/**
	 * If the Sru is looking to its left, yMustBePositive is true, and
	 * yMustBeNegative is false. We use abs(offsetAngleD) under any
	 * circumstances.
	 */
	public AnnulusIntersector(final TangentCylinder.FlatLatLng flatLegLatLng0,
			final TangentCylinder.FlatLatLng flatLegLatLng1,
			final TangentCylinder.FlatLatLng flatPtclLatLng0,
			final TangentCylinder.FlatLatLng flatPtclLatLng1,
			final double[] radiiNmi, final double offsetAngleD,
			final boolean yMustBePositiveIn, final boolean yMustBeNegativeIn) {
		final double absOffsetAngleD = Math.abs(offsetAngleD);
		final boolean yMustBePositive, yMustBeNegative;
		final boolean anglesAreOfInterest;
		if ((0d < absOffsetAngleD && absOffsetAngleD < 90d) &&
				(yMustBePositiveIn != yMustBeNegativeIn)) {
			/** Legitimate angles; there's a cone on exactly one side. */
			yMustBePositive = yMustBePositiveIn;
			yMustBeNegative = yMustBeNegativeIn;
			anglesAreOfInterest = true;
		} else {
			/** No angle. Still have ranges. */
			yMustBePositive = yMustBeNegative = false;
			anglesAreOfInterest = false;
		}
		final TangentCylinder tc = flatLegLatLng0.getOwningTangentCylinder();
		final TangentCylinder.FlatLatLng flat0P =
				tc.convertToMyFlatLatLng(flatLegLatLng0);
		final TangentCylinder.FlatLatLng flat1P =
				tc.convertToMyFlatLatLng(flatLegLatLng1);
		final TangentCylinder.FlatLatLng flat0Q =
				tc.convertToMyFlatLatLng(flatPtclLatLng0);
		final TangentCylinder.FlatLatLng flat1Q =
				tc.convertToMyFlatLatLng(flatPtclLatLng0);
		/**
		 * We form pathPNmi and pathQNmi in earthR, and convert to nmi to make
		 * debugging easier.
		 */
		final double[][] pathPNmi = new double[][] { flat0P.createOffsetPair(),
				flat1P.createOffsetPair() };
		final double[][] pathQNmi = new double[][] { flat0Q.createOffsetPair(),
				flat1Q.createOffsetPair() };
		for (int k1 = 0; k1 < 2; ++k1) {
			for (int k2 = 0; k2 < 2; ++k2) {
				pathPNmi[k1][k2] /= _NmiToR;
				pathQNmi[k1][k2] /= _NmiToR;
			}
		}

		/** Shift the origin to pathPNmi[0]. */
		final double offset0 = pathPNmi[0][0];
		final double offset1 = pathPNmi[0][1];
		pathPNmi[0][0] = pathPNmi[0][1] = 0d;
		pathPNmi[1][0] -= offset0;
		pathPNmi[1][1] -= offset1;
		pathQNmi[0][0] -= offset0;
		pathQNmi[0][1] -= offset1;
		pathQNmi[1][0] -= offset0;
		pathQNmi[1][1] -= offset1;

		/**
		 * Change coordinates; p will run from (0,0) to (magP,0), so that P10 =
		 * magP. The unit vectors in our new basis are dP and dP_Perp.
		 */
		final double[] dP = new double[] { pathPNmi[1][0], pathPNmi[1][1] };
		final double magP = NumericalRoutines.convertToUnitLength(dP);
		final double P10 = magP;
		final double[] dP_Perp = new double[] { -dP[1], dP[0] };
		final double Q00 = NumericalRoutines.dotProduct(dP, pathQNmi[0]);
		final double Q01 = NumericalRoutines.dotProduct(dP_Perp, pathQNmi[0]);
		final double Q10 = NumericalRoutines.dotProduct(dP, pathQNmi[1]);
		final double Q11 = NumericalRoutines.dotProduct(dP_Perp, pathQNmi[1]);
		/**
		 * <pre>
		 * x1 = t * P10
		 * y1 = 0;
		 * x2 = Q00 + t * (Q10-Q00) - t * P10 =
		 *      Q00 + t * (Q10-Q00-P10) =
		 *      Q00 + t * A (defining A to be Q10-Q00-P10).
		 * y2 = Q01 + t * (Q11-Q01) =
		 *      Q01 + t * B (defining B to be Q11-Q01).
		 * </pre>
		 */
		final double startingRSq = Q00 * Q00 + Q01 * Q01;
		final double A = Q10 - Q00 - P10;
		final double B = Q11 - Q01;

		final double smallRNmi = radiiNmi[0];
		final double bigRNmi = radiiNmi[1];
		final boolean startSmallEnough = startingRSq < bigRNmi * bigRNmi;
		final boolean startBigEnough =
				smallRNmi == 0d || (startingRSq > smallRNmi * smallRNmi);
		final boolean startInRange = startSmallEnough && startBigEnough;
		final double[][] rangeTPairs = getTPairsForRanges(P10, Q00, Q01, A, B,
				smallRNmi, bigRNmi, startInRange, yMustBePositive, yMustBeNegative);
		/** Now for the angles if they apply. */
		final double[][] angleTPairs;
		if (!anglesAreOfInterest) {
			angleTPairs = getZeroOneTPairs();
		} else {
			final double lowThetaD =
					(yMustBePositive ? 90d : -90d) - absOffsetAngleD;
			final double highThetaD =
					(yMustBePositive ? 90d : -90d) + absOffsetAngleD;
			final double thetaStartD = Math.toDegrees(MathX.atan2X(Q01, Q00));
			final boolean startInCone =
					lowThetaD < thetaStartD && thetaStartD < highThetaD;
			final double[] angleTPair =
					getTPairForAngles(P10, Q00, Q01, A, B, lowThetaD, highThetaD,
							startInCone, yMustBePositive, yMustBeNegative);
			if (angleTPair == null) {
				angleTPairs = new double[0][];
			} else {
				angleTPairs = new double[][] { angleTPair };
			}
		}
		final List<double[]> tPairList = intersect(rangeTPairs, angleTPairs);
		final int nTPairs = tPairList.size();
		_tPairs = new double[nTPairs][];
		for (int k = 0; k < nTPairs; ++k) {
			_tPairs[k] = tPairList.get(k);
		}
	}

	public AnnulusIntersector(final TangentCylinder.FlatLatLng flatLegLatLng0,
			final TangentCylinder.FlatLatLng flatLegLatLng1,
			final TangentCylinder.FlatLatLng flatPtclLatLng0,
			final TangentCylinder.FlatLatLng flatPtclLatLng1,
			final double[] ltRadiiNmi, final double[] ltLkAngls,
			final double[] rtRadiiNmi, final double[] rtLkAngls) {
		final double ltMinNmi = ltRadiiNmi[0];
		final double ltMaxNmi = ltRadiiNmi[1];
		final double rtMinNmi = rtRadiiNmi[0];
		final double rtMaxNmi = rtRadiiNmi[1];
		final double ltMinLkAngl = ltLkAngls[0];
		final double ltMaxLkAngl = ltLkAngls[1];
		final double rtMinLkAngl = rtLkAngls[0];
		final double rtMaxLkAngl = rtLkAngls[1];
		if (ltMinNmi == 0d && ltMaxNmi >= 100d) {
			if (rtMinNmi == 0d && rtMaxNmi >= 100d) {
				if (ltMinLkAngl == 0d && ltMaxLkAngl == 180d) {
					if (rtMinLkAngl == 0d && rtMaxLkAngl == 180d) {
						_tPairs = getZeroOneTPairs();
						return;
					}
				}
			}
		}

		/** "P" indicates "leg" and "Q" indicates "particle." */
		final TangentCylinder tc = flatLegLatLng0.getOwningTangentCylinder();
		final TangentCylinder.FlatLatLng flat0P =
				tc.convertToMyFlatLatLng(flatLegLatLng0);
		final TangentCylinder.FlatLatLng flat1P =
				tc.convertToMyFlatLatLng(flatLegLatLng1);
		final TangentCylinder.FlatLatLng flat0Q =
				tc.convertToMyFlatLatLng(flatPtclLatLng0);
		final TangentCylinder.FlatLatLng flat1Q =
				tc.convertToMyFlatLatLng(flatPtclLatLng1);
		final double[][] pathPNmi = new double[][] { flat0P.createOffsetPair(),
				flat1P.createOffsetPair() };
		final double[][] pathQNmi = new double[][] { flat0Q.createOffsetPair(),
				flat1Q.createOffsetPair() };
		/** Convert both P and Q to nmi. */
		for (int iPass = 0; iPass < 2; ++iPass) {
			final double[][] pathNmi = iPass == 0 ? pathPNmi : pathQNmi;
			for (int k0 = 0; k0 < 2; ++k0) {
				pathNmi[k0][0] /= _NmiToR;
				pathNmi[k0][1] /= _NmiToR;
			}
		}
		/** Shift the origin to pathPNmi[0]. */
		final double offset0 = pathPNmi[0][0];
		final double offset1 = pathPNmi[0][1];
		for (int iPass = 0; iPass < 2; ++iPass) {
			final double[][] pathNmi = iPass == 0 ? pathPNmi : pathQNmi;
			for (int k0 = 0; k0 < 2; ++k0) {
				pathNmi[k0][0] -= offset0;
				pathNmi[k0][1] -= offset1;
			}
		}
		pathPNmi[0][0] = pathPNmi[0][1] = 0d;
		/**
		 * Change coordinates; P will run from (0,0) to (magP,0), so that P10 =
		 * magP. The unit vectors in our new basis are dP and dP_Perp.
		 */
		final double[] dP = new double[] { pathPNmi[1][0], pathPNmi[1][1] };
		final double magP = NumericalRoutines.convertToUnitLength(dP);
		final double P10 = magP;
		final double[] dP_Perp = new double[] { -dP[1], dP[0] };
		final double Q00 = NumericalRoutines.dotProduct(dP, pathQNmi[0]);
		final double Q01 = NumericalRoutines.dotProduct(dP_Perp, pathQNmi[0]);
		final double Q10 = NumericalRoutines.dotProduct(dP, pathQNmi[1]);
		final double Q11 = NumericalRoutines.dotProduct(dP_Perp, pathQNmi[1]);

		/**
		 * <pre>
		 * (Particle-SRU) goes from ((Q00-P00),(Q01-P01)) to ((Q10-P10),(Q11-P11)).
		 * Since P00=P01=P01 = 0, this reduces to:
		 * (Particle-SRU) goes from (Q00,Q01) to (Q10-P10,Q11).
		 * We let (A,B) = (Q10-P10,Q11) - (Q00,Q01).
		 *
		 * </pre>
		 */
		final double A = (Q10 - P10) - Q00;
		final double B = Q11 - Q01;

		final ArrayList<double[]> rawTPairList = new ArrayList<>();
		for (int iPass = 0; iPass < 2; ++iPass) {
			final boolean lt = iPass == 0;
			final double minNmi = lt ? ltMinNmi : rtMinNmi;
			final double maxNmi = lt ? ltMaxNmi : rtMaxNmi;
			final double minLkAngl = lt ? ltMinLkAngl : rtMinLkAngl;
			final double maxLkAngl = lt ? ltMaxLkAngl : rtMaxLkAngl;
			if (minNmi == maxNmi || minLkAngl == maxLkAngl) {
				continue;
			}
			final double[][] anglTPairs;
			final boolean noAnglFilter = 0d == minLkAngl && maxLkAngl == 180d;
			if (noAnglFilter) {
				anglTPairs = getZeroOneTPairs();
			} else {
				/**
				 * The thetas must be in degrees, ccw from the positive x axis, and
				 * the cone goes ccw from lowThetaD to highThetaD.
				 */
				final double lowThetaD, highThetaD;
				if (lt) {
					lowThetaD = minLkAngl;
					highThetaD = maxLkAngl;
				} else {
					lowThetaD = 360d - maxLkAngl;
					highThetaD = 360d - minLkAngl;
				}
				anglTPairs =
						getTPairsForLkAngls(Q00, Q01, A, B, lowThetaD, highThetaD);
			}
			final double[][] rangeTPairs;
			final boolean noRangeFilter = 0d == minNmi && maxNmi >= 100d;
			if (noRangeFilter) {
				rangeTPairs = getZeroOneTPairs();
			} else {
				final double startingNmiSq = Q00 * Q00 + Q01 * Q01;
				final boolean startSmallEnough = startingNmiSq < maxNmi * maxNmi;
				final boolean startBigEnough =
						minNmi == 0d || (startingNmiSq > minNmi * minNmi);
				final boolean startInRange = startSmallEnough && startBigEnough;
				rangeTPairs = getTPairsForRanges(P10, Q00, Q01, A, B, minNmi,
						maxNmi, startInRange, /* yMustBePositive= */lt,
						/* yMustBeNegative= */!lt);
			}
			final ArrayList<double[]> theseTPairs =
					intersect(anglTPairs, rangeTPairs);
			rawTPairList.addAll(theseTPairs);
		}
		final ArrayList<double[]> union = union(rawTPairList);
		_tPairs = union.toArray(new double[union.size()][]);
	}

	/** tPairs0 and tPairs1 must be internally disjoint. */
	private static ArrayList<double[]> intersect(final double[][] tPairs0,
			final double[][] tPairs1) {
		final ArrayList<double[]> winningTPairs = new ArrayList<>();
		final int n0 = tPairs0 == null ? 0 : tPairs0.length;
		final int n1 = tPairs1 == null ? 0 : tPairs1.length;
		for (int k0 = 0; k0 < n0; ++k0) {
			final double[] tPair0 = tPairs0[k0];
			for (int k1 = 0; k1 < n1; ++k1) {
				final double[] tPair1 = tPairs1[k1];
				final double maxLeft = Math.max(tPair0[0], tPair1[0]);
				final double minRight = Math.min(tPair0[1], tPair1[1]);
				if (minRight > maxLeft) {
					winningTPairs.add(new double[] { maxLeft, minRight });
				}
			}
		}
		winningTPairs.sort(NumericalRoutines._ByFirstOnly);
		/**
		 * By construction, the intervals have positive length, and do not touch
		 * each other.
		 */
		Collections.sort(winningTPairs, NumericalRoutines._ByAllInOrder1);
		assert legalPairs(winningTPairs) : "Strange Pairs(1)";
		return winningTPairs;
	}

	public static ArrayList<double[]> union(
			final ArrayList<double[]> rawTPairList) {
		if (rawTPairList.isEmpty()) {
			return new ArrayList<>(0);
		}
		Collections.sort(rawTPairList, NumericalRoutines._ByAllInOrder1);
		final int n = rawTPairList.size();
		final ArrayList<double[]> listOfPairs = new ArrayList<>();
		double[] currentTPair = rawTPairList.get(0).clone();
		for (int k = 1; k < n; ++k) {
			final double[] rawTPair = rawTPairList.get(k);
			if (rawTPair[0] <= currentTPair[1]) {
				currentTPair[1] = Math.max(currentTPair[1], rawTPair[1]);
			} else {
				listOfPairs.add(currentTPair);
				currentTPair = rawTPair.clone();
			}
		}
		listOfPairs.add(currentTPair);
		assert legalPairs(listOfPairs) : "Strange Pairs(2)";
		return listOfPairs;
	}

	private static boolean legalPairs(final List<double[]> winningTPairs) {
		final int nPairs = winningTPairs.size();
		for (int k = 0; k < nPairs; ++k) {
			final double[] oldPair = k == 0 ? null : winningTPairs.get(k - 1);
			final double[] newPair = winningTPairs.get(k);
			if ((k > 0 && !(newPair[0] > oldPair[1])) || !(newPair[1] > newPair[0])) {
				return false;
			}
		}
		return true;
	}

	private static double[][] getTPairsForRanges(final double P10,
			final double Q00, final double Q01, final double A, final double B,
			final double smallRNmi, final double bigRNmi,
			final boolean startsLegal, final boolean yMustBePositive,
			final boolean yMustBeNegative) {
		/**
		 * <pre>
		 * QQ(t) = (Q00 + t*A, Q01 + t*B).
		 * ||QQ(t)||squared =
		 *       Q00*Q00 + 2t*(Q00*A) + tt*AA +
		 *       Q01*Q01 + 2t*(Q01*B) + tt*BB =
		 *       tt*(AA+BB) + 2t(Q00*A + Q01*B) + (Q00*Q00 + Q01*Q01) =
		 *         targetR*targetR, or:
		 * 0 =   tt*(AA+BB) + 2t(Q00*A + Q01*B) + (Q00*Q00 + Q01*Q01 -
		 *         targetR*targetR)
		 * </pre>
		 */
		final double AA = A * A;
		final double BB = B * B;
		final double quadA = AA + BB;
		final double quadB = 2d * (Q00 * A + Q01 * B);
		final ArrayList<Double> criticalTimes = new ArrayList<>();
		for (int k0 = 0; k0 < 2; ++k0) {
			final double targetNmi = k0 == 0 ? smallRNmi : bigRNmi;
			if (!(targetNmi >= 0d)) {
				continue;
			}
			final double quadC = Q00 * Q00 + Q01 * Q01 - targetNmi * targetNmi;
			final double[] roots =
					NumericalRoutines.quadratic(quadA, quadB, quadC, null);
			for (int k1 = 0; k1 < 2; ++k1) {
				final double t = roots[k1];
				if (!(0 < t && t < 1)) {
					continue;
				}
				final double y = Q01 + t * B;
				if ((yMustBePositive && !(y > 0d)) || (yMustBeNegative && !(y < 0d))) {
					continue;
				}
				if (criticalTimes.contains(t)) {
					criticalTimes.remove(t);
				} else {
					criticalTimes.add(t);
				}
			}
		}
		return createTPairs(criticalTimes, startsLegal);
	}

	/**
	 * There is at most 1 tPair so we return a double[] or null instead of a
	 * double[][].
	 */
	private static double[] getTPairForAngles(final double P10,
			final double Q00, final double Q01, final double A, final double B,
			final double lowThetaD, final double highThetaD,
			final boolean startsLegal, final boolean yMustBePositive,
			final boolean yMustBeNegative) {
		/**
		 * <pre>
		 * x(t) = Q00 + t * A.
		 * y(t) = Q01 + t * B;
		 * QQ(t) = (x(t), y(t)).
		 * y(t) * cos(targetTheta) = x(t) * sin(targetTheta);
		 * Defining the constants c and s as cos and sin of targetTheta:
		 * s*Q00 + s*t*A = c*Q01 + c*t*B,
		 * t(s*A - c*B) = c*Q01 -b s*Q00,
		 * t = (c*Q01 - s*Q00)/(s*A - c*B).
		 * If s*A - c*B == 0, it's parallel, and there are no crosses.
		 * </pre>
		 */
		final ArrayList<Double> criticalTimes = new ArrayList<>(2);
		for (int k = 0; k < 2; ++k) {
			final double thetaD = k == 0 ? lowThetaD : highThetaD;
			final double c = MathX.cosX(Math.toRadians(thetaD));
			final double s = MathX.sinX(Math.toRadians(thetaD));
			final double den = (s * A) - (c * B);
			final double t = (c * Q01 - s * Q00) / den;
			if (!(0 < t && t < 1)) {
				continue;
			}
			final double y = Q01 + t * B;
			if ((yMustBePositive && !(y > 0d)) || (yMustBeNegative && !(y < 0d))) {
				continue;
			}
			if (!criticalTimes.remove(t)) {
				criticalTimes.add(t);
			}
		}
		if (criticalTimes.isEmpty()) {
			return null;
		}
		final double[][] tPairs = createTPairs(criticalTimes, startsLegal);
		assert tPairs.length == 1;
		return tPairs[0];
	}

	/**
	 * <pre>
	 * We will return at most 1, but it's easier if we return a singleton as
	 * an array of length 1. theta0Dx and theta1Dx describe the cone. We
	 * assume that the cone goes from theta0Dx ccw to theta1Dx and that these
	 * are measured in degrees, ccw from the positive x axis.
	 * To define (A,B), we note that prior to calling this, we shifted the
	 * origin so that the SRU starts at (0,0) and the particle starts at (Q00,Q01).
	 * Moreover, with the SRU considered stationary, the particle will move to
	 * (Q00,Q01) + (A,B), thus defining (A,B).
	 * move
	 * </pre>
	 */
	private static double[][] getTPairsForLkAngls(final double Q00,
			final double Q01, final double A, final double B,
			final double theta0Dx, final double theta1Dx) {
		final ArrayList<Double> criticalTimes = new ArrayList<>();
		final double theta0Dy = LatLng3.getInRange0_360(theta0Dx);
		final double theta1Dy = LatLng3.getInRange0_360(theta1Dx);
		final double theta0D = theta0Dy <= theta1Dy ? theta0Dy : theta1Dy;
		final double theta1D = theta0Dy <= theta1Dy ? theta1Dy : theta0Dy;
		final boolean yMustBePositive;
		final boolean yMustBeNegative;
		if (theta0D <= 180d && theta1D <= 180d) {
			yMustBePositive = true;
			yMustBeNegative = false;
		} else if (theta0D >= 180d && theta1D >= 180d) {
			yMustBePositive = false;
			yMustBeNegative = true;
		} else {
			/** This routine only works if both angles are on the same side. */
			return null;
		}
		for (int k = 0; k < 2; ++k) {
			final double targetThetaD = k == 0 ? theta0D : theta1D;
			/**
			 * <pre>
			 * Q is the particle, and the SRU is stuck at the origin.
			 * x(t) = Q00 + t*A.
			 * y(t) = Q01 + t*B;
			 * QQ(t) = (x(t), y(t)).
			 * tan(targetTheta) = y(t) / x(t), or:
			 * y(t) * cos(targetTheta) = x(t) * sin(targetTheta).
			 * Let c = cos(targetTheta) and s = sin(targetTheta).
			 * s*Q00 + s*t*A = c*Q01 + c*t*B,
			 * t(s*A - c*B) = c*Q01 - s*Q00,
			 * t = (c*Q01 - s*Q00)/(s*A - c*B).
			 * If s*A - c*B == 0, it's parallel, and there are no crosses.
			 * </pre>
			 */
			final double c = MathX.cosX(Math.toRadians(targetThetaD));
			final double s = MathX.sinX(Math.toRadians(targetThetaD));
			final double den = (s * A) - (c * B);
			final double t = (c * Q01 - s * Q00) / den;
			if (!(0 < t && t < 1)) {
				continue;
			}
			final double y = Q01 + t * B;
			if ((yMustBePositive && !(y > 0d)) || (yMustBeNegative && !(y < 0d))) {
				continue;
			}
			if (!criticalTimes.remove(t)) {
				criticalTimes.add(t);
			}
		}
		final double thetaD = LatLng3.getInRange0_360(MathX.atan2X(Q01, Q00));
		final boolean startsLegal = (theta0D <= thetaD) && (thetaD <= theta1D);
		return createTPairs(criticalTimes, startsLegal);
	}

	private static double[][] createTPairs(
			final ArrayList<Double> criticalTimes, final boolean startsLegal) {
		if (startsLegal) {
			/**
			 * We never put 0d or 1d into criticalTimes, so if we start legal, we
			 * must put in 0.
			 */
			criticalTimes.add(0d);
		}
		/**
		 * If we have an odd number, 1d goes in, since these will be the pairs
		 * of live intervals.
		 */
		if (criticalTimes.size() % 2 == 1) {
			criticalTimes.add(1d);
		}
		Collections.sort(criticalTimes);
		final int nTimes = criticalTimes.size();
		final int nPairs = nTimes / 2;
		final double[][] tPairs = new double[nPairs][];
		for (int k = 0; k < nPairs; ++k) {
			final double t0 = criticalTimes.get(2 * k);
			final double t1 = criticalTimes.get(2 * k + 1);
			tPairs[k] = new double[] { t0, t1 };
		}
		return tPairs;
	}

	public static double[][] getZeroOneTPairs() {
		return new double[][] { new double[] { 0d, 1d } };
	}
}
