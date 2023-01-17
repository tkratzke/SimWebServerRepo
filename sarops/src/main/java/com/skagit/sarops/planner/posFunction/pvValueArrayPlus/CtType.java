package com.skagit.sarops.planner.posFunction.pvValueArrayPlus;

public enum CtType {
	OVL(true, false), //
	PV_TRNST(false, true), //
	PVSEQ_TRNST(false, true);
	final public boolean _isOvl;
	final public boolean _isTrnst;

	private CtType(final boolean isOvl, final boolean isTrnst) {
		_isOvl = isOvl;
		_isTrnst = isTrnst;
	}
}