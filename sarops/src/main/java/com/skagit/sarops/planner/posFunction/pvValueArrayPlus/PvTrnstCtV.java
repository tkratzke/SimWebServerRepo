package com.skagit.sarops.planner.posFunction.pvValueArrayPlus;

import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.posFunction.PvValue;

public class PvTrnstCtV extends CtV {

	PvTrnstCtV(final PvValue pvValue, final double[] forOptnAndReports) {
		final PatternVariable pv = pvValue.getPv();
		_pvValue1 = null;
		_pvValue0 = pvValue;
		_pvSeq = null;
		setValues(forOptnAndReports, /* ctvId= */pv.getDisplayName());
	}

	@Override
	public CtType getCtType() {
		return CtType.PV_TRNST;
	}

	@Override
	public int getIndexWithinType() {
		return _pvValue0.getPv().getGrandOrd();
	}

}
