package com.skagit.sarops.util.patternUtils;

import java.util.ArrayList;

import com.skagit.util.MathX;
import com.skagit.util.NumericalRoutines;

/**
 * Planar and units only need to be consistent. Results are created with the
 * assumption that the center of the box is (0,0).
 */
public class PsCsPathFinder {

	final double _sll, _ts, _sgndTs;
	/**
	 * Storing the unrotated points makes it easier to generate exclusion
	 * polygons and ts boxes.
	 */
	final public double[][] _unrotatedPoints;
	final public double[][] _points;

	private enum LegCategory {
		/**
		 * Right and left are the search legs; we build an unrotated path, which
		 * has the search legs going right and left.
		 */
		RIGHT, CROSS1, LEFT, CROSS2;
		static final LegCategory[] _LegCategories = LegCategory.values();
	}

	public PsCsPathFinder(final double tsInc, final double ttlLegLengthPlusTs,
			final double firstLegHdg, final double sllIn, final double sgndTsIn) {
		_ts = PatternUtilStatics.roundWithMinimum(tsInc, Math.abs(sgndTsIn));
		_sgndTs = _ts * Math.signum(sgndTsIn);

		double remainingPath = Math.max(0d, ttlLegLengthPlusTs - _ts);
		final double sll = Math.abs(Math.min(remainingPath, sllIn));
		_sll = PatternUtilStatics.roundWithMinimum(tsInc, sll);

		/** Compute the creep extent for initializing y. */
		final double creepExtent;
		if (remainingPath == _sll) {
			creepExtent = 0d;
		} else {
			final double ellLen = _sll + _ts;
			final double nElls = (remainingPath - _sll) / ellLen;
			final int nFloor =
					(int) Math.floor(nElls + (PatternUtilStatics._FudgeFactor - 1d));
			final double leftOver = (nElls - nFloor) * ellLen;
			creepExtent = (nFloor * _ts) + Math.min(_ts, leftOver);
		}
		final boolean firstTurnRight = _sgndTs >= 0d;
		final double startX = -_sll / 2d;
		final double startY =
				firstTurnRight ? creepExtent / 2 : -creepExtent / 2;
		final double small =
				(PatternUtilStatics._FudgeFactor - 1d) * Math.max(_sll, _ts);
		final int nLegCategories = LegCategory._LegCategories.length;

		/** Build the lists of unrotated points and lengths. */
		final ArrayList<double[]> unrotatedPointList = new ArrayList<>();
		unrotatedPointList.add(new double[] { startX, startY });
		final ArrayList<Double> lengthList = new ArrayList<>();
		double x = startX;
		double y = startY;
		lengthList.add(0d);
		for (int k = 0;; ++k) {
			final LegCategory legCategory =
					LegCategory._LegCategories[k % nLegCategories];
			final double legLength;
			if (legCategory == LegCategory.RIGHT ||
					legCategory == LegCategory.LEFT) {
				/** If remainingPath is approximately _sll, give it to him. */
				final double num = Math.abs(_sll - remainingPath);
				final double den = Math.max(_sll, remainingPath);
				final double relDiff = num / den;
				if (relDiff < PatternUtilStatics._FudgeFactor - 1d) {
					legLength = _sll;
				} else {
					legLength = Math.min(remainingPath, _sll);
				}
				final double xNew =
						x + (legCategory == LegCategory.RIGHT ? legLength : -legLength);
				unrotatedPointList.add(new double[] { xNew, y });
				x = xNew;
			} else {
				/**
				 * Cross legs; the y value always moves down if firstTurnRight and
				 * up if !firstTurnRight.
				 */
				legLength = Math.min(remainingPath, _ts);
				final double yNew = y - (firstTurnRight ? legLength : -legLength);
				unrotatedPointList.add(new double[] { x, yNew });
				y = yNew;
			}
			lengthList.add(legLength);
			remainingPath -= legLength;
			if (remainingPath <= small) {
				break;
			}
		}

		/** Store them. */
		final int nPoints = unrotatedPointList.size();
		_unrotatedPoints = new double[nPoints][2];
		_points = new double[nPoints][2];
		final double theta = Math.toRadians(90d - firstLegHdg);
		final double c = MathX.cosX(theta);
		final double s = MathX.sinX(theta);
		for (int k = 0; k < nPoints; ++k) {
			System.arraycopy(unrotatedPointList.get(k), 0, _unrotatedPoints[k], 0,
					2);
			final double thisX = _unrotatedPoints[k][0];
			final double thisY = _unrotatedPoints[k][1];
			final double[] newPoint =
					NumericalRoutines.rotateXy(c, s, thisX, thisY);
			System.arraycopy(newPoint, 0, _points[k], 0, 2);
		}
	}

	public int getNLegs() {
		final int nLegs = _points.length - 1;
		return nLegs;
	}

	public int getNSearchLegs() {
		final int nLegs = getNLegs();
		final int returnValue = (nLegs + 1) / 2;
		return returnValue;
	}

}