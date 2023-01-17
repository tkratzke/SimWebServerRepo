package com.skagit.sarops.planner.posFunction.pvValueArrayPlus;

import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.plannerModel.pvSeq.PvSeq;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.util.HermitePlus;

public abstract class CtV implements Cloneable {
	protected double _forOptnV;
	protected double _forReportsV;
	protected double _splineV;
	protected String _string;
	protected PvValue _pvValue0;
	protected PvValue _pvValue1;
	protected PvSeq _pvSeq;

	public CtV() {
		_forReportsV = Double.NaN;
		_string = null;
		_pvValue0 = _pvValue1 = null;
		_pvSeq = null;
	}

	protected Planner getPlanner() {
		return _pvValue0 != null ?
				_pvValue0.getPv().getPlannerModel().getPlanner() : null;
	}

	protected void setValues(final double[] forOptnAndReports,
			final String ctvId) {
		_forOptnV = forOptnAndReports[0];
		_forReportsV = forOptnAndReports[1];
		final HermitePlus hermitePlus = PvValueArrayPlus._HermitePlusOvl;
		_splineV = hermitePlus.value(_forOptnV);
		final String simpleClassName = getClass().getSimpleName();
		final int first_ = simpleClassName.lastIndexOf('_');
		final String shortClassName;
		if (first_ < 0) {
			shortClassName = simpleClassName;
		} else {
			shortClassName = simpleClassName.substring(0, first_);
		}
		_string = String.format("%s+%s %f", shortClassName, ctvId, _forOptnV);
	}

	public abstract int getIndexWithinType();

	public abstract CtType getCtType();

	public String getString() {
		return _string;
	}

	@Override
	public String toString() {
		return getString();
	}

	public double getForOptnV() {
		return _forOptnV;
	}

	public double getForReportsV() {
		return _forReportsV;
	}

	public PvValue getPvValue0() {
		return _pvValue0;
	}
}
