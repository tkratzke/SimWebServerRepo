package com.skagit.sarops.planner.summarySums;

import com.skagit.sarops.planner.posFunction.WorkplaceRow;
import com.skagit.util.NumericalRoutines;

public class SummarySums {
	final public double _sumPriors;
	final public double _sumPriorXPosFromNew;
	final public double _sumInitWts;
	final public double _sumInitWtXOldPos;
	final public double _sumInitWtXPosFromAll;
	final public double _sumPriorXPropIn;
	final private double[] _normalizingSums;
	final public double _ovlV;
	final public double _ovl;
	final public double _pvTrnstV;
	final public double _pvSeqTrnstV;

	@SuppressWarnings("unused")
	private boolean isValid() {
		if (!(_sumPriors > 0d) || !(_sumPriorXPosFromNew >= 0d) || !(_sumInitWts > 0d) || !(_sumInitWtXOldPos >= 0d)) {
			return false;
		}
		if (!(_sumInitWtXPosFromAll >= 0d)) {
			return false;
		}
		if (!(_sumPriorXPropIn >= 0d)) {
			return false;
		}
		if (_normalizingSums == null || _normalizingSums.length != 2) {
			return false;
		}
		if (!(_normalizingSums[0] > 0d)) {
			return false;
		}
		if (!(_normalizingSums[1] > 0d)) {
			return false;
		}
		if (!(Double.isFinite(_ovlV))) {
			return false;
		}
		if (!(Double.isFinite(_pvTrnstV))) {
			return false;
		}
		if (!(Double.isFinite(_pvSeqTrnstV))) {
			return false;
		}
		return true;
	}

	public SummarySums(final WorkplaceRow workPlaceRow,
			final double[] normalizingSums) {
		_sumPriors = workPlaceRow._sumNewWts;
		_sumPriorXPosFromNew = workPlaceRow._sumWtXPos;
		_sumInitWts = workPlaceRow._sumInitWt;
		_sumInitWtXOldPos = workPlaceRow._sumInitWtXPvsPos;
		_sumInitWtXPosFromAll = workPlaceRow._sumInitWtXCumPos;
		_sumPriorXPropIn = workPlaceRow._sumWtXPropIn;
		_ovlV = workPlaceRow._ovlV;
		_ovl = workPlaceRow._ovl;
		_pvTrnstV = workPlaceRow._pvTrnstV;
		_pvSeqTrnstV = workPlaceRow._pvSeqTrnstV;
		_normalizingSums = normalizingSums;
	}

	public SummarySums() {
		_sumPriors = _sumPriorXPosFromNew = _sumInitWts =
				_sumInitWtXOldPos = _sumInitWtXPosFromAll = _sumPriorXPropIn = 0d;
		_ovlV = _ovl = 1d;
		_pvTrnstV = _pvSeqTrnstV = 0d;
		_normalizingSums = new double[] { 1d, 1d };
	}

	public String getString() {
		final String s = String.format(
				"SumOfPriors[%.3f], " + "SumOfPriorTimesPosFromNew[%.3f], " +
						"\n\tSumOfInitPriors[%.3f], " +
						"SumOfInitPriorTimesPosFromOld[%.3f], " +
						"SumOfInitPriorTimesPosFromOldAndNew[%.3f], " +
						"SumOfInitPriorTimesProportionIn[%.3f] //" +
						"\n\tnormalizingSums[%s]",
				_sumPriors, _sumPriorXPosFromNew, _sumInitWts, _sumInitWtXOldPos,
				_sumInitWtXPosFromAll, _sumPriorXPropIn,
				NumericalRoutines.getString(_normalizingSums));
		return s;
	}
}