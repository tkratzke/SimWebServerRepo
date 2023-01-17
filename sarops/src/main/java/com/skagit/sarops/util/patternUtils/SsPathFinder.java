package com.skagit.sarops.util.patternUtils;

import java.util.ArrayList;

import com.skagit.util.MathX;
import com.skagit.util.NumericalRoutines;

/**
 * Planar and units only need to be consistent. Results are created with the
 * assumption that the center of the box is (0,0).
 */
public class SsPathFinder {
	final private static double _FudgeFactor =
			PatternUtilStatics._FudgeFactor;

	final public double _sgndTs;
	/**
	 * _nTs is convenient for printing. It includes the "extra one." For a
	 * perfect SS, _nTs = nHalfLaps*nHalfLaps.
	 */
	final public int _nTs;
	/**
	 * Storing the unrotated points makes it easier to generate exclusion
	 * polygons and ts boxes.
	 */
	final public double[][] _unrotatedPoints;
	final public double[][] _pointsNmi;

	public SsPathFinder(final double tsInc, final double ttlLegLengthPlusTs,
			final double firstLegHdg, final double sgndTsIn,
			final boolean shiftPoints) {
		final double ts =
				PatternUtilStatics.roundWithMinimum(tsInc, Math.abs(sgndTsIn));
		_sgndTs = ts * Math.signum(sgndTsIn);
		_nTs = (int) Math.floor((ttlLegLengthPlusTs * _FudgeFactor) / ts);

		/**
		 * The following statement lays out the unrotated segments, in integer
		 * #TS coordinates.
		 */
		final PointStats[] pointStatsS =
				getPointStatsS(ttlLegLengthPlusTs, sgndTsIn);
		final int nPoints = pointStatsS.length;
		_unrotatedPoints = new double[nPoints][];
		_pointsNmi = new double[nPoints][];
		/** Overhead for rotation. */
		final double theta = Math.toRadians(90d - firstLegHdg);
		final double c = MathX.cosX(theta);
		final double s = MathX.sinX(theta);
		/** We do linear combinations of the basis vectors (c,s) and (-s,c). */
		final double[] b0 = new double[] { c, s };
		final double[] b1 = new double[] { -s, c };

		final double xShift, yShift;
		if (shiftPoints) {
			final double absShift = shiftPoints ? (ts / 2d) : 0d;
			xShift = -absShift;
			final boolean firstTurnRight = sgndTsIn > 0d;
			yShift = firstTurnRight ? absShift : -absShift;
		} else {
			xShift = yShift = 0d;
		}

		for (int k = 0; k < nPoints; ++k) {
			final PointStats pointStats = pointStatsS[k];
			final double rawX0 = ts * pointStats._xCoord + xShift;
			final double rawY0 = ts * pointStats._yCoord + yShift;
			_unrotatedPoints[k] = new double[] { rawX0, rawY0 };
			_pointsNmi[k] =
					NumericalRoutines.linearCombination(rawX0, rawY0, b0, b1);
		}
	}

	public static class PointStats {
		final private int _xCoord;
		final private int _yCoord;
		final private int _len;
		final private int _cumLen;

		private PointStats(final int xCoord, final int yCoord, final int len,
				final int cumLen) {
			_xCoord = xCoord;
			_yCoord = yCoord;
			_len = len;
			_cumLen = cumLen;
		}

		public String getString() {
			return String.format("(%d,%d) len/cum[%d/%d]", _xCoord, _yCoord, _len,
					_cumLen);
		}

		@Override
		public String toString() {
			return getString();
		}
	}

	/** Lays out points as if firstLegHdg is 90. */
	private static PointStats[] getPointStatsS(
			final double ttlLegLengthPlusTs, final double sgndTs) {
		final double ts = Math.abs(sgndTs);
		final boolean firstTurnRight = sgndTs > 0d;
		final int nTs = (int) Math.round(ttlLegLengthPlusTs / ts);

		final ArrayList<PointStats> pointStatsList = new ArrayList<>();
		pointStatsList.add(new PointStats(0, 0, 0, 1));
		for (int iPoint = 1;; ++iPoint) {
			final PointStats lastPointStats = pointStatsList.get(iPoint - 1);
			final int lastCum = lastPointStats._cumLen;
			final int avail = nTs - lastCum;
			if (avail <= 0) {
				break;
			}
			/**
			 * Points 1 and 2 are the 1st pair and have a shift length of 1.
			 * Points 3 and 4 are the 2nd pair and have a shift length of 2, etc.
			 */
			final int kPair = (iPoint + 1) / 2;
			final int len = Math.min(avail, kPair);
			/** For the odd points, we shift the x coordinate. */
			final boolean shiftX = iPoint % 2 == 1;
			if (shiftX) {
				/** We alternate our x-shifts between right and left. */
				final boolean right = kPair % 2 == 1;
				final PointStats pointStats = new PointStats(//
						lastPointStats._xCoord + (right ? len : -len), //
						lastPointStats._yCoord, //
						len, //
						lastPointStats._cumLen + len);
				pointStatsList.add(pointStats);
			} else {
				/**
				 * We alternate our y-shifts between down and up. Our first one is
				 * down if firstTurnRight.
				 */
				final boolean down = firstTurnRight == (kPair % 2 == 1);
				final PointStats pointStats = new PointStats(//
						lastPointStats._xCoord, //
						lastPointStats._yCoord - (down ? len : -len), //
						len, //
						lastPointStats._cumLen + len);
				pointStatsList.add(pointStats);
			}
		}
		return pointStatsList.toArray(new PointStats[pointStatsList.size()]);
	}

	public double getTtlLegLength() {
		final double ts = Math.abs(_sgndTs);
		final int nHalfLaps = (int) Math.floor(Math.sqrt(_nTs) * _FudgeFactor);
		final int nHalfLapsSq = nHalfLaps * nHalfLaps;
		final int nLeftOverTs = _nTs - nHalfLapsSq;
		final double ttlLegLength = ((nHalfLapsSq + nLeftOverTs) - 1) * ts;
		return ttlLegLength;
	}

	public String getString() {
		final double ts = Math.abs(_sgndTs);
		final int nHalfLaps = (int) Math.floor(Math.sqrt(_nTs) * _FudgeFactor);
		final int nLeftOverTs = _nTs - nHalfLaps * nHalfLaps;
		final double ttlLegLength =
				(nHalfLaps * nHalfLaps + nLeftOverTs - 1) * ts;
		final String s = String.format(
				"\nnPoints[%d] sgndTs[%.3f] nHalfLaps[%d] nLeftOverTs[%d] TtlLegLength[%f]",
				_pointsNmi.length, _sgndTs, nHalfLaps, nLeftOverTs, ttlLegLength);
		return s;
	}

	@Override
	public String toString() {
		return getString();
	}

	public int getNLegs() {
		return _pointsNmi.length - 1;
	}

	public int getNSearchLegs() {
		return getNLegs();
	}

	public static void main(final String[] args) {
		final SsPathFinder ssPathFinderNmi = new SsPathFinder(
				PatternUtilStatics._TsInc, 16d, 60d, 1d, /* shiftPoints= */false);
		System.out.printf("\n%s", ssPathFinderNmi.getString());
	}

}