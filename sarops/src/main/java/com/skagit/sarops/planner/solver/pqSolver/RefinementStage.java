package com.skagit.sarops.planner.solver.pqSolver;

public enum RefinementStage {
	PRELIM(false, true), //
	CLR_OVL(true, false), //
	ZERO_IN1(false, true), //
	ZERO_IN2(false, true);

	final public static RefinementStage[] _Values = RefinementStage.values();
	final public static String _StageInfoDelimiter = "::";
	final public boolean _forClearingCts;
	final public boolean _forPos;

	private RefinementStage(final boolean forClearingCts,
			final boolean isPos) {
		_forClearingCts = forClearingCts;
		_forPos = isPos;
	}

	public boolean forZeroingIn() {
		return this == ZERO_IN1 || this == ZERO_IN2;
	}
}