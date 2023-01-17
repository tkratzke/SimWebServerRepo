package com.skagit.sarops.planner.solver;

import java.util.EventListener;
import java.util.EventObject;

import com.skagit.sarops.planner.posFunction.pvValueArrayPlus.PvValueArrayPlus;

public class OptimizationEvent extends EventObject {
	final private static long serialVersionUID = 1L;
	final private PvValueArrayPlus _plus;
	final private String _explanatoryString;
	final private boolean _isBest;

	/** A "Stop Event" is indicated by _pvValueArray == null. */
	public OptimizationEvent(final Solver solver, final PvValueArrayPlus plus,
			final String explanatoryString, final boolean isBest) {
		/**
		 * An EventObject needs something; we need to stash the Solver somewhere.
		 * Happy serendipity.
		 */
		super(solver);
		if (plus != null) {
			_plus = plus;
			_isBest = isBest;
		} else {
			_plus = null;
			_isBest = false;
		}
		_explanatoryString = explanatoryString;
	}

	final public Solver getSolver() {
		return (Solver) getSource();
	}

	final public PvValueArrayPlus getPlus() {
		return _plus;
	}

	final public boolean getIsBest() {
		return _isBest;
	}

	final public String getExplanatoryString() {
		return _explanatoryString;
	}

	public static interface Listener extends EventListener {
		public void react(OptimizationEvent optimizationEvent);
	}
}
