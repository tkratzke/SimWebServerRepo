package com.skagit.sarops.planner.solver;

import java.util.EventListener;
import java.util.EventObject;

import com.skagit.sarops.planner.solver.pqSolver.PqSolver;

public class JumpEvent extends EventObject {
	final private static long serialVersionUID = 1L;

	public JumpEvent(final PqSolver pqSolver) {
		/**
		 * An EventObject needs something; we need to stash the Solver somewhere.
		 * Happy serendipity.
		 */
		super(pqSolver);
	}

	final public PqSolver getPqSolver() {
		return (PqSolver) getSource();
	}

	public static interface Listener extends EventListener {
		public void react(JumpEvent jumpEvent);
	}
}
