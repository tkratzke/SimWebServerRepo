package com.skagit.sarops.planner.solver;

import java.util.EventListener;
import java.util.EventObject;

import com.skagit.sarops.planner.plannerModel.PlannerModel;

public class ChangeActiveSetEvent extends EventObject {
	final private static long serialVersionUID = 1L;

	public ChangeActiveSetEvent(final PlannerModel plannerModel) {
		super(plannerModel);
	}

	final public PlannerModel getPlannerModel() {
		return (PlannerModel) getSource();
	}

	public static interface Listener extends EventListener {
		public void react(ChangeActiveSetEvent changeActiveSetEvent);
	}
}
