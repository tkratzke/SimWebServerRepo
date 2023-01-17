package com.skagit.sarops.planner.posFunction;

import com.skagit.sarops.tracker.ParticleIndexes;

public class PosEvaluation implements Cloneable {
	final public PosFunction _posFunction;
	final public double _pos;
	final public double _varOfEstmt;
	final public double _sumOfNewWeights;
	final public int _nUsedOfArray;

	public PosEvaluation(PosFunction posFunction, final double pos,
			final double varOfEstmt, final double sumOfNewWeights,
			final int nUsedOfArray) {
		_posFunction = posFunction;
		_pos = pos;
		_varOfEstmt = varOfEstmt;
		_sumOfNewWeights = sumOfNewWeights;
		_nUsedOfArray = nUsedOfArray;
	}

	public String getString() {
		ParticleIndexes[] prtclIndxsS = _posFunction == null ? null
				: _posFunction.getParticleIndexesS();
		if (prtclIndxsS != null) {
			return String.format(
					"Pos[%.3f] nUsed/Of[%d/%d] sdOfEstimate[%.3f], sumOfWts[%.3f]",
					_pos, _nUsedOfArray, prtclIndxsS.length, Math.sqrt(_varOfEstmt),
					_sumOfNewWeights);
		}
		return String.format(
				"Pos[%.3f] nUsed[%d] sdOfEstimate[%.3f], sumOfWts[%.3f]", _pos,
				_nUsedOfArray, Math.sqrt(_varOfEstmt), _sumOfNewWeights);
	}

	@Override
	public String toString() {
		return getString();
	}
}