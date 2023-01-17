package com.skagit.sarops.util.patternUtils;

import java.util.Arrays;

import com.skagit.util.CombinatoricTools;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.randomx.Randomx;

/**
 * Planar, Lat/Lng-free, and no center. Adjusts the box to a perfect rounded
 * pattern. This pattern has rounded values for sll and ts, and the
 * corresponding epl may have decreased.
 */
public class DiscreteLpSpecToTs {
	final private static double _FudgeFactor =
			PatternUtilStatics._FudgeFactor;

	/** Constants. */
	final private double _tsInc;

	/** Input; the epl of the specBox. This is true for forward or reverse. */
	final private double _epl;

	/** Input for forward, output for reverse */
	final private double _along;
	final private double _across;

	/** "Adjusted" inputs for forward. */
	final private double _minTs;
	final private double _fxdTs;

	/** Intermediate results for forward. We also use them in the printout. */
	final private double _targetAlong;
	final private double _targetAcross;

	/** Outputs for forward. For reverse, we derive them from other inputs */
	final public double _sll;
	final public double _ts;
	final public int _nSearchLegs;

	/** Debugging statistic: */
	private int _nSymDiffs;

	/** Assumes units are nmi and the inc's are from LadderPattern2. */
	public DiscreteLpSpecToTs(final double eplNmi, final double minTsNmi,
			final double fxdTsNmi, final double alongNmi, final double acrossNmi,
			final boolean adjustSpecsIfNeeded) {
		this(PatternUtilStatics._TsInc, eplNmi, minTsNmi, fxdTsNmi, alongNmi,
				acrossNmi, adjustSpecsIfNeeded);
	}

	/**
	 * Assumes units are radians and the inc's are from LadderPatterns after
	 * being converted to radians. We use a dummy argument at the end to
	 * distinguish it from the "nmi" version.
	 */
	public DiscreteLpSpecToTs(final double eplR, final double minTsR,
			final double fxdTsR, final double alongR, final double acrossR,
			final boolean adjustSpecsIfNeeded, final char dummyForR) {
		this(PatternUtilStatics._TsIncR, eplR, minTsR, fxdTsR, alongR, acrossR,
				adjustSpecsIfNeeded);
	}

	/**
	 * adjustSpecsIfNeeded comes into play only if we have too much epl for
	 * the given minTs or fxdTs. If that's the case, we cannot "cram" the
	 * entire epl into the given box. We have 2 choices; truncate the epl, or
	 * expand the box proportionately so we can fit the epl into the new box.
	 */
	private DiscreteLpSpecToTs(final double tsInc, final double epl0,
			final double minTs0, final double fxdTs0, final double along0,
			final double across0, final boolean adjustSpecsIfNeeded) {
		/** Constants defining the problem: */
		_tsInc = tsInc;

		/** Round the inputs fxdTs0 and minTs0. We may adjust _fxdTs. */
		final double fxdTs1 = fxdTs0 > 0d ?
				PatternUtilStatics.roundWithMinimum(_tsInc, fxdTs0) : Double.NaN;
		_minTs = minTs0 > 0d ?
				PatternUtilStatics.roundWithMinimum(_tsInc, minTs0) : _tsInc;

		/** Adjust _epl to make sure it's big enough for fxdTs and minTs. */
		final double minSll = _tsInc;
		final double minEpl = minSll + (fxdTs1 > 0d ? fxdTs1 : _minTs);
		_epl = epl0 >= minEpl ? epl0 : minEpl;

		/**
		 * Just in case, across0 was sent in as sgndAcross0, we take the
		 * absolute value.
		 */
		_along = along0;
		_across = Math.abs(across0);

		/** Note that _epl is positive. Also, idealTs0 might be 0. */
		final double idealTs0 = (_along * _across) / _epl;

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
			 * No fixed TS. _targetAlong/TargetAcross is just _along/_across, and
			 * idealTs is just what we computed it to be.
			 */
			_targetAlong = _along;
			_targetAcross = _across;
			idealTs = idealTs0;
		} else {
			if (!adjustSpecsIfNeeded) {
				/**
				 * We have a fxdTs and we're stuck with this box. Algorithm is this
				 * block of code. Note; here we may drastically underuse _epl.
				 */
				_ts = _fxdTs;
				/**
				 * We know _ts. Set _sll as per _along and _ts, and set _targetAlong
				 * as if that's what we wanted.
				 */
				_sll = PatternUtilStatics.roundWithMinimum(_tsInc, _along - _ts);
				_targetAlong = _ts + _sll;
				final double fudgedEpl = _epl * _FudgeFactor;
				/** Don't overuse epl: */
				final int n0 = (int) Math.floor(fudgedEpl / _targetAlong);
				/** Don't dramatically expand the box: */
				final int n1 = (int) Math.round(_across / _ts);
				_nSearchLegs = Math.max(1, Math.min(n0, n1));
				_targetAcross = _nSearchLegs * _ts;
				_nSymDiffs = 0;
				return;
			}
			/**
			 * Fixed TS, and we will scale the box. Scale
			 * _targetAlong/_targetAcross and fall through to Updating.
			 */
			final double oldArea = _along * _across;
			final double newArea = _fxdTs * _epl;
			final double scale = Math.sqrt(newArea / oldArea);
			_targetAlong = scale * _along;
			_targetAcross = scale * _across;
			idealTs = _fxdTs;
		}

		/** Now using updating mechanism. Dispense with nuisance cases. */
		if (_epl <= 2d * _tsInc || _epl <= _targetAlong ||
				_epl <= _targetAcross + _tsInc) {
			_nSearchLegs = 1;
			final int nIncs = (int) Math.round(_epl / _tsInc);
			if (nIncs < 2) {
				_ts = _sll = _tsInc;
				return;
			}
			final double big = (nIncs - 1) * _tsInc;
			final double small = _tsInc;
			if (_epl <= _targetAcross + _tsInc) {
				_ts = big;
				_sll = small;
				return;
			}
			_ts = small;
			_sll = big;
			return;
		}

		/**
		 * <pre>
		 * Set idealNSearchLegs. There are 2 ways of "guessing" nSearchLegs;
		 * across / idealTs and (along*across)/idealTs.
		 * Since idealTs is not rounded, these are equal.
		 * </pre>
		 */
		final double nSearchLegsD = _targetAcross / idealTs;
		final int ceil = (int) Math.ceil(nSearchLegsD / _FudgeFactor);
		final int floor = (int) Math.floor(nSearchLegsD);

		/**
		 * Start above ceil and go down until we get below floor and have an
		 * answer. For monitoring performance, we initialize _nSymDiffs here.
		 */
		_nSymDiffs = 0;
		LpUpdateResult bestUpdateResult = null;
		for (int nSearchLegs = ceil; nSearchLegs > 0; --nSearchLegs) {
			if (nSearchLegs < floor && bestUpdateResult != null) {
				break;
			}
			final LpUpdateResult updateResult = new LpUpdateResult(nSearchLegs);
			if (Double.isFinite(updateResult._symDiff)) {
				if (bestUpdateResult == null ||
						updateResult._symDiff < bestUpdateResult._symDiff) {
					bestUpdateResult = updateResult;
				}
			}
		}
		_nSearchLegs = bestUpdateResult._myNSearchLegs;
		_sll = bestUpdateResult._nSllIncs * _tsInc;
		_ts = bestUpdateResult._nTsIncs * _tsInc;
	}

	private class LpUpdateResult {
		final private int _nTsIncs;
		final private int _nSllIncs;
		final double _symDiff;

		/** Constants during the search; */
		final private int _myNSearchLegs;

		LpUpdateResult(final int nSearchLegs) {
			_myNSearchLegs = nSearchLegs;
			final double eplOverN = _epl / _myNSearchLegs;

			/** Initialize nTsIncs, nSllIncs, and symDiff. */
			if (_fxdTs > 0d) {
				/** Not much choice here. */
				_nTsIncs = (int) Math.round(_fxdTs / _tsInc);
				final double ts = _nTsIncs * _tsInc;
				final double sll0 = eplOverN - ts;
				final double fudgedSll = sll0 * _FudgeFactor;
				if (fudgedSll < _tsInc) {
					_nSllIncs = 0;
					_symDiff = Double.POSITIVE_INFINITY;
					return;
				}
				_nSllIncs = (int) Math.floor(fudgedSll / _tsInc);
				_symDiff = mySymDiff(_nSllIncs, _nTsIncs);
				return;
			}

			/**
			 * <pre>
			 * Starting at nTsIncs0, we keep increasing nTsIncs. BECAUSE
			 * _sllInc AND _tsInc ARE THE SAME, THE RESULTING VALUE FOR
			 * along WILL NOT CHANGE.  This is not true in general, but
			 * that could be handled with some tricky algebra.  We do not
			 * do that here.
			 * But what this means is that once our across value
			 * exceeds _targetAcross, there is no point in increasing
			 * nTsIncs; along doesn't change anyway. Similarly, once our
			 * across value is below _targetAcross,
			 * there's no point in decreasing nTssIncs.
			 * </pre>
			 */
			int bestNTsIncs = -1;
			int bestNSllIncs = -1;
			double bestSymDiff = Double.POSITIVE_INFINITY;
			final double targetTs = _targetAcross / _myNSearchLegs;
			final double targetNTsIncs = targetTs / _tsInc;
			final int nTsIncs0 = (int) Math.ceil(targetNTsIncs);
			final double ts0 = nTsIncs0 * _tsInc;
			/**
			 * <pre>
			 * Critical line for logic in reversing. It says that if I were to
			 * increase nSllIncs, I would violate the constraint:
			 * nSearchLegs * (sll + ts) <= _epl
			 * </pre>
			 */
			final double fudgedSll0 = (eplOverN - ts0) * _FudgeFactor;
			final int nSllIncs0 = (int) Math.floor(fudgedSll0 / _tsInc);
			final double symDiff0 = mySymDiff(nSllIncs0, nTsIncs0);
			if (symDiff0 < bestSymDiff) {
				bestSymDiff = symDiff0;
				bestNTsIncs = nTsIncs0;
				bestNSllIncs = nSllIncs0;
			}
			if (nTsIncs0 > targetNTsIncs) {
				final int nTsIncs1 = nTsIncs0 - 1;
				final double ts1 = nTsIncs1 * _tsInc;
				final double fudgedSll1 = (eplOverN - ts1) * _FudgeFactor;
				final int nSllIncs1 = (int) Math.floor(fudgedSll1 / _tsInc);
				final double symDiff1 = mySymDiff(nSllIncs1, nTsIncs1);
				if (symDiff1 < bestSymDiff) {
					bestSymDiff = symDiff1;
					bestNTsIncs = nTsIncs1;
					bestNSllIncs = nSllIncs1;
				}
			}
			_nTsIncs = bestNTsIncs;
			_nSllIncs = bestNSllIncs;
			_symDiff = bestSymDiff;
		}

		private double mySymDiff(final int nSllIncs, final int nTsIncs) {
			if (nSllIncs <= 0 || nTsIncs <= 0) {
				return Double.POSITIVE_INFINITY;
			}
			final double sll = nSllIncs * _tsInc;
			final double ts = nTsIncs * _tsInc;
			final double along = sll + ts;
			final double across = _myNSearchLegs * ts;
			return symDiffWithTarget(along, across);
		}
	}

	/** For reversing. */
	public DiscreteLpSpecToTs(final double eplNmi, final double alongNmi,
			final double acrossNmi) {
		this(PatternUtilStatics._TsInc, eplNmi, alongNmi, acrossNmi);
	}

	/** For reversing. */
	public DiscreteLpSpecToTs(final double eplR, final double alongR,
			final double acrossR, final char dummyForR) {
		this(PatternUtilStatics._TsIncR, eplR, alongR, acrossR);
	}

	/** For reversing. */
	private DiscreteLpSpecToTs(final double tsInc, final double epl0,
			final double alongZ, final double acrossZ) {
		_tsInc = tsInc;
		_epl = epl0;
		_minTs = _tsInc;
		_fxdTs = Double.NaN;
		_targetAlong = alongZ;
		_targetAcross = acrossZ;

		/**
		 * <pre>
		 * Find tsZ for the rounded box. Because the rounded box is a
		 * perfect box for some eplZ that is at most _epl, we can substitute _epl
		 * for eplZ in the formula "(alongZ * acrossZ)/eplZ = tsZ,"
		 * to get a lower bound on tsZ.
		 * </pre>
		 */
		final double fudgedEpl = _epl * _FudgeFactor;
		final double lowerBoundTsZ = (alongZ * acrossZ / fudgedEpl);
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
			 * nSearchLegsZ and sllZ.
			 */
			final int nTsIncsZ = (int) divisors[k];
			final double tsZ = nTsIncsZ * _tsInc;
			final int nSearchLegsZ = nAcrossTsIncsZ / nTsIncsZ;
			final double sllZ =
					PatternUtilStatics.roundWithMinimum(_tsInc, alongZ - tsZ);
			/** Expand sllZ to sllY to use up _epl. */
			final double sllY = _epl / nSearchLegsZ - tsZ;
			if (check(alongZ, acrossZ, tsZ, sllY, nSearchLegsZ)) {
				/**
				 * (tsZ,sllY,nSearchLegsZ) is a winner; it uses up _epl by
				 * construction of sllY, and if we use it on the forward, we get
				 * alongZ/acrossZ.
				 */
				/** Record the "answers" as if we were going forward. */
				_ts = tsZ;
				_sll = sllZ;
				_nSearchLegs = nSearchLegsZ;
				/**
				 * The following are the real answers for this problem; the
				 * along/across that produces alongZ/acrossZ.
				 */
				_across = _ts * _nSearchLegs;
				_along = _ts + sllY;
				return;
			}
		}
		/** Couldn't find one. Give up. */
		_ts = _sll = Double.NaN;
		_nSearchLegs = 0;
		_across = _along = Double.NaN;
	}

	/** Check if forwarding a reverse results with the same along/across. */
	private boolean check(final double alongZ, final double acrossZ,
			final double tsY, final double sllY, final int nSearchLegsY) {
		/**
		 * tsY/sllY/nSearchLegsY form a box alongY/acrossY. This alongY/acrossY,
		 * when combined with _epl and given to the forward problem, should
		 * result in alongZ/acrossZ.
		 */
		final double acrossY = nSearchLegsY * tsY;
		final double alongY = sllY + tsY;
		final DiscreteLpSpecToTs discLpTsBoxAdjuster = new DiscreteLpSpecToTs(
				_tsInc, _epl, /* minTs= */_tsInc, /* fxdTs= */Double.NaN, alongY,
				acrossY, /* adjustSpecsIfNeeded= */true);
		final double tsZZ = discLpTsBoxAdjuster._ts;
		final double sllZZ = discLpTsBoxAdjuster._sll;
		final int nSearchLegsZZ = discLpTsBoxAdjuster._nSearchLegs;
		final double alongZZ = tsZZ + sllZZ;
		final double acrossZZ = nSearchLegsZZ * tsZZ;
		if ((NumericalRoutines.getRelativeError(alongZ, alongZZ) > 0.0001) || (NumericalRoutines.getRelativeError(acrossZ, acrossZZ) > 0.0001)) {
			return false;
		}
		return true;
	}

	private double symDiffWithTarget(final double along,
			final double across) {
		++_nSymDiffs;
		return NumericalRoutines.symDiff(_targetAcross, _targetAlong, across,
				along);
	}

	private double symDiffWithOrig(final double along, final double across) {
		++_nSymDiffs;
		return NumericalRoutines.symDiff(_across, _along, across, along);
	}

	/**
	 * The following 2 accessors are useful only after a reverse ctor call; In
	 * that case, _along/_across are outputs.
	 */
	public double getAlong() {
		return _along;
	}

	public double getAcross() {
		return _across;
	}

	public boolean isValid() {
		return _nSearchLegs > 0 && _sll > 0d && _ts > 0d;
	}

	public String getString() {
		final double origArea = Math.abs(_along * _across);
		final double along = _sll + _ts;
		final double across = _nSearchLegs * _ts;
		final double targetArea = _targetAlong * _targetAcross;
		final double area = along * across;
		final double epl = along * _nSearchLegs;
		final double symDiff = symDiffWithOrig(along, across);
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

		s += String.format(" ALNG:%.2f", _along);
		if (_targetAlong != _along) {
			s += String.format(";%.2f", _targetAlong);
		} else {
			s += ";*";
		}
		if (along != _along) {
			s += String.format(";%.2f", along);
		} else {
			s += ";*";
		}

		s += String.format(" ACRSS:%.2f", _across);
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

		s += String.format("  TSN[%.2f/%.2f/%d]", _ts, _sll, _nSearchLegs);
		s += String.format(" SymDff,RltvErr[%.4f/%.4f]", symDiff,
				relativeError);

		s += String.format(" nSymDiffs[%d]", _nSymDiffs);
		return s;
	}

	@Override
	public String toString() {
		return getString();
	}

	public static void main(final String[] args) {
		final Randomx r = new Randomx(/* useCurrentTimeMs= */false);
		/** Assume ts = 1. */
		double worstSymDiff = 0d;
		for (int k = 0; k < 100; ++k) {
			final int n0 = r.nextInt(10);
			final double alongNmi0 = (1d + r.nextDouble()) * (1 + r.nextInt(3));
			final double acrossNmi0 = (1d + r.nextDouble()) * (1 + r.nextInt(3));
			final double logScale = -2d + r.nextDouble() * 4d;
			final double scale = Math.pow(10d, logScale);
			final double eplNmi = scale * n0 * alongNmi0;
			final DiscreteLpSpecToTs discreteLpSpecToTs0 =
					new DiscreteLpSpecToTs(eplNmi, 0.1, Double.NaN, alongNmi0,
							acrossNmi0, /* adjustSpecsIfNeeded= */false);
			final double alongNmi1 =
					discreteLpSpecToTs0._sll + discreteLpSpecToTs0._ts;
			final double acrossNmi1 =
					discreteLpSpecToTs0._nSearchLegs * discreteLpSpecToTs0._ts;
			final DiscreteLpSpecToTs discreteLpSpecToTs1 =
					new DiscreteLpSpecToTs(eplNmi, 0.1, Double.NaN, alongNmi1,
							acrossNmi1, /* adjustSpecsIfNeeded= */false);
			final double alongNmi2 =
					discreteLpSpecToTs1._sll + discreteLpSpecToTs1._ts;
			final double acrossNmi2 =
					discreteLpSpecToTs1._nSearchLegs * discreteLpSpecToTs1._ts;
			final double symDiff = NumericalRoutines.symDiff(alongNmi1,
					acrossNmi1, alongNmi2, acrossNmi2);
			if (k % 10 == 0 || symDiff > worstSymDiff) {
				System.out.printf(
						"\n%2d scale[%f] symDiff[%.3f] along0/across0[%.2f,%.2f] along1/across1[%.2f,%.2f] along2/across2[%.2f,%.2f]",
						k, scale, symDiff, alongNmi0, acrossNmi0, alongNmi1, acrossNmi1,
						alongNmi2, acrossNmi2);
				worstSymDiff = symDiff;
			}
		}
	}

}
