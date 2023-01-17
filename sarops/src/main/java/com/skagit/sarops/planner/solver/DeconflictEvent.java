package com.skagit.sarops.planner.solver;

import java.util.EventListener;
import java.util.EventObject;

import com.skagit.sarops.planner.solver.pqSolver.PqSolver;
import com.skagit.sarops.planner.solver.pqSolver.deconflicter.Deconflicter;

public class DeconflictEvent extends EventObject {
	final private static long serialVersionUID = 1L;
	final public Deconflicter _deconflicter;

	public DeconflictEvent(final PqSolver pqSolver,
			final Deconflicter deconflicter) {
		super(pqSolver);
		_deconflicter = deconflicter;
	}

	final public PqSolver getPqSolver() {
		return (PqSolver) getSource();
	}

	public static interface Listener extends EventListener {
		public void react(DeconflictEvent jumpEvent);
	}
}
