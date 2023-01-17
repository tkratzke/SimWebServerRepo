package com.skagit.sarops.planner.posFunction.pvValueArrayPlus;

import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.cdf.CcwGcas;
import com.skagit.util.myLogger.MyLogger;

public class OvlCtV extends CtV {

	public OvlCtV(final PvValue pvValue0, final PvValue pvValue1) {
		super();
		final int compareValue =
				PvValue._ByPvGrandOrdinalFirst.compare(pvValue0, pvValue1);
		if (compareValue <= 0) {
			_pvValue0 = pvValue0;
			_pvValue1 = pvValue1;
		} else {
			_pvValue0 = pvValue1;
			_pvValue1 = pvValue0;
		}
		final PatternVariable pv0 = getPvValue0().getPv();
		final String pv0Id = pv0.getId();
		final PatternVariable pv1 = _pvValue1.getPv();
		final String pv1Id = pv1.getId();
		final String ctvId = String.format("%s:%s", pv0Id, pv1Id);
		if (compareValue == 0) {
			return;
		}
		if (getPvValue0().onMars() || _pvValue1.onMars()) {
			setValues(new double[] { 0d, 0d }, ctvId);
			return;
		}

		final Planner planner = getPlanner();
		final SimCaseManager.SimCase simCase = planner.getSimCase();
		final MyLogger logger = SimCaseManager.getLogger(simCase);
		final CcwGcas ccwGcas0 = _pvValue0.getTightExcCcwGcas();
		final CcwGcas ccwGcas1 = _pvValue1.getTightExcCcwGcas();
		final double[] rawAndNormalized =
				ccwGcas0.getRawAndNormalizedOvl(logger, ccwGcas1);
		final double raw = rawAndNormalized[0];
		final double normalized = rawAndNormalized[1];
		final double[] forOptnAndReports = new double[] { normalized, raw };
		setValues(forOptnAndReports, ctvId);
	}

	@Override
	public int getIndexWithinType() {
		final PatternVariable pv0 = getPvValue0().getPv();
		final PatternVariable pv1 = _pvValue1.getPv();
		final Planner planner = getPlanner();
		final PlannerModel plannerModel = planner.getPlannerModel();
		final int idx0 = pv0.getGrandOrd();
		final int idx1 = pv1.getGrandOrd();
		final int nPttrnVbls = plannerModel.getNPttrnVbls();
		return idx0 * nPttrnVbls + idx1;
	}

	@Override
	public CtType getCtType() {
		return CtType.OVL;
	}
}