package com.skagit.sarops.planner.posFunction;

public class WorkplaceRow {
	/**
	 * <pre>
	 * In the doubles of the following map, the indexes are:<br>
	 * 0. Sum(i, Weight(i)).<br>
	 * 1. Sum(i, Weight(i) * Pos(i)).<br>
	 * 2. Sum(i, InitialWeight(i)).<br>
	 * 3. Sum(i, InitialWeight(i) * PreviousPos(i)).<br>
	 * 4. Sum(i, InitialWeight(i) * CumPos(i)).<br>
	 * 5. Sum(i, Weight(i) * ProportionIn(i)).<br>
	 * 6. Ovl Normalized.<br>
	 * ProportionIn is defined by the PvValue containment boxes.
	 *
	 * Create the workPlaceRows for the individual PatternVariables,
	 * and their objectTypes.
	 * </pre>
	 */
	public double _sumNewWts;
	public double _sumWtXPos;
	public double _sumInitWt;
	public double _sumInitWtXPvsPos;
	public double _sumInitWtXCumPos;
	public double _sumWtXPropIn;
	public double _ovlV;
	public double _ovl;
	public double _pvTrnstV;
	public double _pvSeqTrnstV;

	public WorkplaceRow() {
		_sumNewWts = _sumWtXPos = _sumInitWt =
				_sumInitWtXPvsPos = _sumInitWtXCumPos = _sumWtXPropIn = 0d;
		_ovlV = _ovl = _pvTrnstV = _pvSeqTrnstV = Double.NaN;
	}
}
