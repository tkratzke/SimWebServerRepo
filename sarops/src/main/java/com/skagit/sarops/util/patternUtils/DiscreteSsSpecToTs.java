package com.skagit.sarops.util.patternUtils;

import java.util.Arrays;

import com.skagit.util.CombinatoricTools;
import com.skagit.util.NumericalRoutines;

/**
 * Planar, Lat/Lng-free, and no center. Adjusts the box to a perfect rounded
 * pattern. But this pattern has a rounded value for ts, and the
 * corresponding epl may have decreased.
 */
public class DiscreteSsSpecToTs {
	final private static double _FudgeFactor =
			PatternUtilStatics._FudgeFactor;

	/**
	 * Input; the increment and the epl of the specBox; for either forward or
	 * reverse.
	 */
	final private double _tsInc;
	final private double _epl;

	/** Input for forward, output for reverse */
	final private double _across;

	/** "Adjusted" inputs for forward. */
	final private double _minTs;
	final private double _fxdTs;

	/** Intermediate results for forward. We also use them in the printout. */
	final private double _targetAcross;

	/**
	 * Outputs for forward. For reverse, we have a simple derivation for them
	 * from other inputs
	 */
	final public double _ts;
	final public int _nHalfLaps;

	/** Debugging statistic: */
	private int _nSymDiffs;

	/** Assumes units are nmi and tsInc is from LadderPattern2. */
	public DiscreteSsSpecToTs(final double eplNmi, final double minTsNmi,
			final double fxdTsNmi, final double acrossNmi,
			final boolean adjustSpecsIfNeeded) {
		this(PatternUtilStatics._TsInc, eplNmi, minTsNmi, fxdTsNmi, acrossNmi,
				adjustSpecsIfNeeded);
	}

	/**
	 * Assumes units are radians and tsInc is from LadderPatterns after being
	 * converted to radians. We use a dummy argument at the end to distinguish
	 * it from the "nmi" version.
	 */
	public DiscreteSsSpecToTs(final double eplR, final double minTsR,
			final double fxdTsR, final double acrossR,
			final boolean adjustSpecsIfNeeded, final char dummyForR) {
		this(PatternUtilStatics._TsIncR, eplR, minTsR, fxdTsR, acrossR,
				adjustSpecsIfNeeded);
	}

	/**
	 * adjustSpecsIfNeeded comes into play only if we have too much epl for
	 * the given minTs or fxdTs. If that's the case, we cannot "cram" the
	 * entire epl into the given box. We have 2 choices; truncate the epl, or
	 * expand the box proportionately so we can fit the epl into the new box.
	 */
	private DiscreteSsSpecToTs(final double tsInc, final double epl0,
			final double minTs0, final double fxdTs0, final double across0,
			final boolean adjustSpecsIfNeeded) {
		/** Constants defining the problem: */
		_tsInc = tsInc;

		/** Round the inputs fxdTs0 and minTs0. We may adjust _fxdTs. */
		final double fxdTs1 = fxdTs0 > 0d ?
				PatternUtilStatics.roundWithMinimum(_tsInc, fxdTs0) : Double.NaN;
		_minTs = minTs0 > 0d ?
				PatternUtilStatics.roundWithMinimum(_tsInc, minTs0) : _tsInc;

		/** Adjust _epl to make sure it's big enough for fxdTs and minTs. */
		if (fxdTs1 > 0d) {
			_epl = (epl0 >= fxdTs1) ? epl0 : fxdTs1;
		} else {
			_epl = (epl0 >= _minTs) ? epl0 : _minTs;
		}

		_across = Math.abs(across0);

		/** Note that _epl is positive. Also, idealTs0 might be 0. */
		final double idealTs0 = (_across * _across) / _epl;

		/** Set _fxdTs as per fxdTs1, _minTs, and idealTs0. */
		if (0d < fxdTs1) {
			/** We have a fixed. Set _fxd to the larger of min and fxd. */
			_fxdTs = Math.max(fxdTs1, _minTs);
		} else if (idealTs0 < _minTs) {
			/** We have no fxd, but minTs is big. minTs is the new fixed ts. */
			_fxdTs = _minTs;
		} else {
			_fxdTs = Double.NaN;
		}

		/**
		 * Set our "idealTs." If _fxdTs > 0, that's our ideal. Otherwise, it's
		 * simply idealTs0.
		 */
		final double idealTs;
		if (!(_fxdTs > 0d)) {
			/**
			 * No fixed TS. _targetAcross is just _across, and idealTs is just
			 * what we computed it to be.
			 */
			_targetAcross = _across;
			idealTs = idealTs0;
		} else {
			if (!adjustSpecsIfNeeded) {
				/**
				 * We have a fxdTs but we're stuck with this spec. Algorithm in this
				 * case, is this block of code. Note; here we may drastically
				 * under-use _epl.
				 */
				_ts = _fxdTs;
				/**
				 * We know _ts. The number of halfLaps is the lesser of the rounded
				 * value of across/n and sqrt(epl/ts). Each of these possibly must
				 * be odd.
				 */
				final int n0;
				final int n1;
				if (PatternUtilStatics._OddNHalfLapsOnly) {
					/**
					 * Rounding to the nearest odd integer: Subtract 1, divide by 2,
					 * round to the nearest integer, multiply the 2 back, and add the
					 * 1 back.
					 */
					n0 = (int) Math
							.round(Math.round((_across / _ts - 1d) / 2d) * 2d + 1d);
					/** For n1, we must round down to the nearest odd integer. */
					final int n1A = (int) Math.floor(Math.sqrt(_epl / _ts));
					n1 = n1A - (n1A % 2 == 0 ? 1 : 0);
				} else {
					n0 = (int) Math.round(_across / _ts);
					n1 = (int) Math.floor(Math.sqrt(_epl / _ts));
				}
				_nHalfLaps = Math.max(PatternUtilStatics._OddNHalfLapsOnly ? 3 : 2,
						Math.min(n0, n1));
				_targetAcross = _nHalfLaps * _ts;
				_nSymDiffs = 0;
				return;
			}
			/** Fixed TS, and we scale the box so that we will use up _epl. */
			final double oldArea = _across * _across;
			final double newArea = _fxdTs * _epl;
			final double scale = Math.sqrt(newArea / oldArea);
			idealTs = _fxdTs;
			/**
			 * With an adjusted _targetAcross, we can fall through to the rest of
			 * the algorithm.
			 */
			_targetAcross = scale * _across;
		}

		final double idealNHalfLaps = _targetAcross / idealTs;
		final int ceilA = (int) Math.ceil(idealNHalfLaps / _FudgeFactor);
		final int floorA = (int) Math.floor(idealNHalfLaps);
		if (floorA < 2 ||
				(floorA == 2 && PatternUtilStatics._OddNHalfLapsOnly)) {
			/**
			 * We don't have enough epl for a non-vacuous pattern with idealTs.
			 */
			_nHalfLaps = PatternUtilStatics._OddNHalfLapsOnly ? 3 : 2;
			_ts = PatternUtilStatics.roundWithMinimum(_tsInc,
					_epl / (_nHalfLaps * _nHalfLaps));
			return;
		}

		final int ceil, floor;
		if (PatternUtilStatics._OddNHalfLapsOnly) {
			ceil = Math.max(3, ceilA + (ceilA % 2 == 0 ? 1 : 0));
			floor = Math.max(3, floorA - (ceilA % 2 == 0 ? 1 : 0));
		} else {
			ceil = Math.max(2, ceilA);
			floor = Math.max(2, floorA);
		}

		/**
		 * Start above ceil and go down until we get below floor and have an
		 * answer. For monitoring performance, we initialize _nSymDiffs here.
		 */
		_nSymDiffs = 0;
		SsUpdateResult bestUpdateResult = null;
		for (int nHalfLaps = ceil; nHalfLaps > 0;
				nHalfLaps -= PatternUtilStatics._OddNHalfLapsOnly ? 2 : 1) {
			if (nHalfLaps < floor && bestUpdateResult != null) {
				break;
			}
			final SsUpdateResult updateResult = new SsUpdateResult(nHalfLaps);
			if (Double.isFinite(updateResult._symDiff)) {
				if (bestUpdateResult == null ||
						updateResult._symDiff < bestUpdateResult._symDiff) {
					bestUpdateResult = updateResult;
				}
			}
		}
		if (bestUpdateResult == null) {
			_nHalfLaps = 1;
			_ts = _tsInc;
			return;
		}
		/** We have our answer. */
		_nHalfLaps = bestUpdateResult._myNHalfLaps;
		_ts = bestUpdateResult._nTsIncs * _tsInc;
	}

	/** For reversing. */
	public DiscreteSsSpecToTs(final double eplNmi, final double acrossNmi) {
		this(PatternUtilStatics._TsInc, eplNmi, acrossNmi);
	}

	/** For reversing. */
	public DiscreteSsSpecToTs(final double eplR, final double acrossR,
			final boolean dummyForR) {
		this(PatternUtilStatics._TsIncR, eplR, acrossR);
	}

	/** For reversing. */
	private DiscreteSsSpecToTs(final double tsInc, final double epl0,
			final double acrossZ) {
		_tsInc = tsInc;
		_epl = epl0;
		_targetAcross = acrossZ;
		_minTs = _tsInc;
		_fxdTs = Double.NaN;

		/**
		 * <pre>
		 * Find tsZ for the rounded box. Because the rounded box is a
		 * perfect box for some eplZ that is at most _epl, we can substitute _epl
		 * for eplZ in the formula "(acrossZ * acrossZ)/eplZ = tsZ,"
		 * to get a lower bound on tsZ.
		 * </pre>
		 */
		final double fudgedEpl = _epl * _FudgeFactor;
		final double lowerBoundTsZ = (acrossZ * acrossZ / fudgedEpl);
		final int nTsIncsZ0 =
				Math.max(1, (int) Math.ceil(lowerBoundTsZ / _tsInc));
		final int nAcrossTsIncsZ =
				(int) Math.round(NumericalRoutines.round(acrossZ, tsInc) / tsInc);
		/** tsZ's nTsIncs must divide nAcrossTsIncs'. */
		final long[] divisors = CombinatoricTools.getDivisors(nAcrossTsIncsZ);
		int idx = Arrays.binarySearch(divisors, nTsIncsZ0);
		if (idx < 0) {
			idx = -idx - 1;
		}
		for (int k = idx; k < divisors.length; ++k) {
			/**
			 * We have a candidate for nTsIncsZ, and hence for tsZ and then
			 * nHalfLapsZ.
			 */
			final int nTsIncsZ = (int) divisors[k];
			final double tsZ = nTsIncsZ * _tsInc;
			final int nHalfLapsZ = nAcrossTsIncsZ / nTsIncsZ;
			/** Expand tsZ to tsY to use up _epl. */
			final double tsY = _epl / (nHalfLapsZ * nHalfLapsZ);
			if (check(acrossZ, tsY, nHalfLapsZ)) {
				/**
				 * (tsZ,sllY,nHalfLapsZ) is a winner; it uses up _epl by
				 * construction of sllY, and if we use it on the forward, we get
				 * acrossZ.
				 */
				/** Record the "answers" as if we were going forward. */
				_ts = tsZ;
				_nHalfLaps = nHalfLapsZ;
				/**
				 * The following are the real answers for this problem; the across
				 * that produces acrossZ.
				 */
				_across = _nHalfLaps * tsY;
				return;
			}
		}
		/** Couldn't find one. Give up. */
		_ts = Double.NaN;
		_nHalfLaps = 0;
		_across = Double.NaN;
	}

	/** Check if forwarding a reverse results with the same across. */
	private boolean check(final double acrossZ, final double tsY,
			final int nHalfLapsY) {
		/**
		 * tsY/nHalfLapsY form a box acrossY. This acrossY, when combined with
		 * _epl and given to the forward problem, should result in acrossZ.
		 */
		final double acrossY = nHalfLapsY * tsY;
		final DiscreteSsSpecToTs discSsTsBoxAdjuster = new DiscreteSsSpecToTs(
				_tsInc, _epl, /* minTs= */_tsInc, /* fxdTs= */Double.NaN, acrossY,
				/* adjustSpecsIfNeeded= */true);
		final double tsZZ = discSsTsBoxAdjuster._ts;
		final int nHalfLapsZZ = discSsTsBoxAdjuster._nHalfLaps;
		final double acrossZZ = nHalfLapsZZ * tsZZ;
		if (NumericalRoutines.getRelativeError(acrossZ, acrossZZ) > 0.0001) {
			return false;
		}
		return true;
	}

	private class SsUpdateResult {
		/** Input: */
		final private int _myNHalfLaps;
		/** Output: */
		final private int _nTsIncs;
		final double _symDiff;

		private SsUpdateResult(final int nHalfLaps) {
			_myNHalfLaps = nHalfLaps;

			/** Initialize nTsIncs and symDiff. */
			final double fudgedEpl = _epl * _FudgeFactor;
			if (_fxdTs > 0d) {
				/** Not much choice here. */
				_nTsIncs = Math.max(1, (int) Math.round(_fxdTs / _tsInc));
				/** Is this legal? */
				final double ts = _nTsIncs * _tsInc;
				if (_myNHalfLaps * _myNHalfLaps * ts > fudgedEpl) {
					_symDiff = Double.POSITIVE_INFINITY;
					return;
				}
				_symDiff = mySymDiff(_nTsIncs);
				return;
			}
			/** Set _nTsIncs as high as possible without violating _epl. */
			final double ts = fudgedEpl / (_myNHalfLaps * _myNHalfLaps);
			_nTsIncs = (int) Math.floor(ts / _tsInc);
			if (_nTsIncs <= 0) {
				_symDiff = Double.POSITIVE_INFINITY;
				return;
			}
			_symDiff = mySymDiff(_nTsIncs);
		}

		private double mySymDiff(final int nTsIncs) {
			final double ts = nTsIncs * _tsInc;
			if (ts <= 0d) {
				return Double.POSITIVE_INFINITY;
			}
			final double across = _myNHalfLaps * ts;
			return symDiffWithTarget(across);
		}
	}

	private double symDiffWithTarget(final double across) {
		++_nSymDiffs;
		final double symDiff = NumericalRoutines.symDiff(_targetAcross,
				_targetAcross, across, across);
		return symDiff;
	}

	private double symDiffWithSpec(final double across) {
		++_nSymDiffs;
		final double symDiff =
				NumericalRoutines.symDiff(_across, _across, across, across);
		return symDiff;
	}

	/**
	 * The following is useful only after a reverse ctor call; In that case,
	 * _across is an output.
	 */
	public double getAcross() {
		return _across;
	}

	public boolean isValid() {
		return _nHalfLaps > 0 && _ts > 0d;
	}

	public String getString() {
		final double origArea = _across * _across;
		final double targetArea = _targetAcross * _targetAcross;
		final double across = _nHalfLaps * _ts;
		final double area = across * across;
		final double epl = across * _nHalfLaps;
		final double symDiff = symDiffWithSpec(across);
		/** "Remove" the nSymDiffs contribution of the above. */
		--_nSymDiffs;
		final double relativeError = symDiff / origArea;
		String s = "";

		s += String.format("Epl:%.2f", _epl);
		if (epl != _epl) {
			s += String.format(";%.2f", epl);
		} else {
			s += ";*";
		}

		s += String.format(" ALNG:%.2f", _across);
		if (_targetAcross != _across) {
			s += String.format(";%.2f", _targetAcross);
		} else {
			s += ";*";
		}
		if (across != _across) {
			s += String.format(";%.2f", across);
		} else {
			s += ";*";
		}

		s += String.format(" AREA:%.2f", origArea);
		if (targetArea != origArea) {
			s += String.format(";%.2f", targetArea);
		} else {
			s += ";*";
		}
		if (area != origArea) {
			s += String.format(";%.2f", area);
		} else {
			s += ";*";
		}

		s += String.format("  TNhlNsl[%.2f/%d/%d]", _ts, _nHalfLaps,
				getNSearchLegs());
		s += String.format(" SymDff,RltvErr[%.4f/%.4f]", symDiff,
				relativeError);

		s += String.format(" nSymDiffs[%d]", _nSymDiffs);
		return s;
	}

	@Override
	public String toString() {
		return getString();
	}

	public double getNSearchLegs() {
		if (_nHalfLaps < 2) {
			return 0;
		}
		return 2 * _nHalfLaps - 1;
	}

}
