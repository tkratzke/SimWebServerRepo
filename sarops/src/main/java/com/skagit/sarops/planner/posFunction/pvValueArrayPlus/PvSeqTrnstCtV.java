package com.skagit.sarops.planner.posFunction.pvValueArrayPlus;

import com.skagit.sarops.planner.plannerModel.pvSeq.PvSeq;

public class PvSeqTrnstCtV extends CtV {

	PvSeqTrnstCtV(final PvSeq pvSeq, final double[] forOptnAndReports) {
		_pvValue0 = _pvValue1 = null;
		_pvValue0 = null;
		_pvSeq = pvSeq;
		setValues(forOptnAndReports, /* ctvId= */_pvSeq._id);
	}

	@Override
	public CtType getCtType() {
		return CtType.PVSEQ_TRNST;
	}

	@Override
	public int getIndexWithinType() {
		return _pvSeq.getPvSeqOrd();
	}

}
