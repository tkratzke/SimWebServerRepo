package com.skagit.sarops.studyRunner;

import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.solver.Solver;
import com.skagit.sarops.simCaseManager.SimCaseManager.SimCase;
import com.skagit.util.AbstractToImpl;
import com.skagit.util.StaticUtilities;

abstract public class AbstractStudyRunner {
	final private static AbstractStudyRunner _Singleton;
	static {
		final AbstractStudyRunner singleton = (AbstractStudyRunner) AbstractToImpl
				.GetImplObject(StaticUtilities.getMyClass());
		if (singleton != null) {
			_Singleton = singleton;
		} else {
			_Singleton = new AbstractStudyRunner() {

				@Override
				protected boolean runStudy(final SimCase simCase) {
					/** For Planner, it is NOT done. For Sim, it doesn't matter. */
					return false;
				}

				@Override
				protected Solver buildSolver2(final Planner planner) {
					return null;
				}
			};
		}
	}

	protected abstract boolean runStudy(SimCase simCase);

	protected abstract Solver buildSolver2(final Planner planner);

	public static boolean RunStudy(final SimCase simCase) {
		return _Singleton.runStudy(simCase);
	}

	public static Solver BuildSolver2(final Planner planner) {
		return _Singleton.buildSolver2(planner);

	}

}
