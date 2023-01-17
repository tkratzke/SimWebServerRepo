package com.skagit.sarops.planner.writingUtils;

import com.skagit.sarops.tracker.ParticleIndexes;

public class ParticleData {
	final public ParticleIndexes _prtclIndxs;
	final public int _ot;
	final public double _initWt;
	final public double _pfAlpha;
	final public double _bravoPrior;
	final public boolean _adrift;
	final public boolean _passLa;
	/** The following are per PatternVariable. */
	final public double[] _pFails;
	final public boolean[] _slctds;
	final public boolean _adrift2;

	public ParticleData(final ParticleIndexes prtclIndxs, final int ot,
			final double initWt, final double pfAlpha, final double bravoPrior,
			final boolean adrift, final boolean passLa, final double[] pFails,
			final boolean[] slctds, final boolean adrift2) {
		_prtclIndxs = prtclIndxs;
		_ot = ot;
		_initWt = initWt;
		_pfAlpha = pfAlpha;
		_bravoPrior = bravoPrior;
		_adrift = adrift;
		_passLa = passLa;
		_pFails = pFails;
		_slctds = slctds;
		_adrift2 = adrift2;
	}

	public boolean isSlctd() {
		for (final boolean slctd : _slctds) {
			if (slctd) {
				return true;
			}
		}
		return false;
	}

	public boolean passBoth() {
		return _passLa && isSlctd();
	}

	public double getPfBravo() {
		double bravoPFail = 1d;
		for (final double pFail : _pFails) {
			bravoPFail *= pFail;
		}
		return bravoPFail;
	}

	public double getPfCum() {
		return _pfAlpha * getPfBravo();
	}
}