package com.skagit.sarops.planner.solver;

import java.util.ArrayList;
import java.util.Arrays;

import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.planner.posFunction.pvValueArrayPlus.PvValueArrayPlus;
import com.skagit.sarops.planner.solver.pqSolver.PqSolver;
import com.skagit.sarops.planner.solver.pqSolver.PvValuePerturber;
import com.skagit.sarops.planner.solver.pqSolver.deconflicter.Deconflicter;
import com.skagit.sarops.planner.solver.pqSolver.pvPlacer.PvPlacerStatics;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.studyController.AbstractStudyRunner;

public class SolversManager {
	final private static boolean _PqPlagiarizes = true;
	final private Planner _planner;
	final private PqSolver _pqSolver;
	final private Solver _solver2;
	final private ArrayList<Solver> _solvers;
	final private ArrayList<OptimizationEvent.Listener> _optimizationEventListeners;
	final private ArrayList<JumpEvent.Listener> _jumpEventListeners;
	final private ArrayList<DeconflictEvent.Listener> _deconflictionEventListeners;
	final private ArrayList<ChangeActiveSetEvent.Listener> _changeActiveSetEventListeners;

	public SolversManager(final Planner planner) {
		_planner = planner;
		/** Construct Solvers and their colors if a real planning problem. */
		_solvers = new ArrayList<>(2);
		_optimizationEventListeners = new ArrayList<>();
		_jumpEventListeners = new ArrayList<>();
		_deconflictionEventListeners = new ArrayList<>();
		_changeActiveSetEventListeners = new ArrayList<>();
		if (planner.amRealPlannerProblem()) {
			_pqSolver = new PqSolver(planner, /* isSolver0= */true);
			_solver2 = AbstractStudyRunner.BuildSolver2(planner);
		} else {
			_pqSolver = null;
			_solver2 = null;
		}
		_solvers.add(_pqSolver);
		_solvers.add(_solver2);
	}

	public Planner getPlanner() {
		return _planner;
	}

	public void shutDownSolvers() {
		synchronized (this) {
			for (final Solver solver : _solvers) {
				if (solver != null) {
					solver.stopIfGoing(/* cancel= */false);
					synchronized (solver) {
						/** Causes an interrupt to pqSolver.wait(...) statements. */
						solver.notifyAll();
					}
					while (solver.isGoing()) {
					}
				}
			}
		}
	}

	public void unloadSolvers() {
		_solvers.clear();
	}

	public ArrayList<Solver> getSolvers() {
		return _solvers;
	}

	public PqSolver getPqSolver() {
		return _pqSolver;
	}

	public Solver getSolver2() {
		return _solver2;
	}

	public void perturbManual(final PatternVariable pv, final PvValuePerturber.PertType pertType) throws Exception {
		if (_solver2 == null) {
			/** Nothing to perturb. */
			return;
		}
		if (pv == null) {
			if (pertType != PvValuePerturber.PertType.JUMP) {
				return;
			}
			/** Do full jump. */
			final PvValueArrayPlus manualCurrentPlus = _solver2.getCurrentPlus();
			final Deconflicter deconflicter = PvPlacerStatics.nextPlus(_planner, /* ftPosFunction= */null,
					/* nftPosFunction= */null, manualCurrentPlus);
			final PvValueArrayPlus newPlus = deconflicter._outPlus;
			final String explanatoryString = "@Manual Adjustment Jump@";
			_solver2.setCurrentPlus(newPlus, explanatoryString);
			return;
		}
		final PlannerModel plannerModel = _planner.getPlannerModel();
		final int grandOrd = pv.getGrandOrd();
		/** If it's permanently frozen or not active, disallow this. */
		final PvValue permanentlyFrozenPvValue = pv.getPermanentFrozenPvValue();
		if (permanentlyFrozenPvValue != null || !pv.isActive()) {
			return;
		}
		/**
		 * If pv == null && pertType == JUMP, then this is a q-move for the Manual
		 * solver. Else, it's a perturbation for the Manual solver; nudge the current
		 * solution, as per pv and pertType.
		 * <p>
		 * If pertType is TOGGLE_FROZEN_BEST or TOGGLE_FROZEN_MANUAL, toggle and set
		 * pvValue to one of the two PvValues. If it's equal to TOGGLE_ACTIVE, toggle
		 * the active status.
		 */
		/** Freeze or unfreeze pv? */
		if (pertType == PvValuePerturber.PertType.TOGGLE_FROZEN_BEST
				|| pertType == PvValuePerturber.PertType.TOGGLE_FROZEN_MANUAL) {
			/** Which PvValue do we wish to use? */
			if (pv.getUserFrozenPvValue() != null) {
				/** It's frozen. Set it to unfrozen. */
				pv.setUserFrozenPvValue(/* userFrozenPvValue= */null);
				return;
			}
			PvValue userFrozenPvValue = pv.getUserFrozenPvValue();
			if (pertType == PvValuePerturber.PertType.TOGGLE_FROZEN_BEST) {
				/** Freeze to whatever the pqBest one is now. */
				final PvValueArrayPlus pqBest = _pqSolver.getBestForCurrentActive();
				userFrozenPvValue = pqBest.getPvValue(grandOrd);
			} else {
				/** Freeze to whatever the manualCurrent one is now. */
				final PvValueArrayPlus manualCurrent = _solver2.getCurrentPlus();
				userFrozenPvValue = manualCurrent.getPvValue(grandOrd);
			}
			pv.setUserFrozenPvValue(userFrozenPvValue);
			return;
		}
		if (pertType == PvValuePerturber.PertType.TOGGLE_ACTIVE) {
			plannerModel.toggleAndReactToIsActiveToggle(pv);
			return;
		}
		/** Regular perturbs (if it's not frozen). */
		if (pv.getUserFrozenPvValue() != null) {
			return;
		}
		final PvValueArrayPlus manualCurrent = _solver2.getCurrentPlus();
		final boolean currentIsFeasible = manualCurrent.isFeasible();
		final PvValue[] manualFullArray = manualCurrent.getCopyOfPvValues();
		final PvValue origPvValue = manualFullArray[grandOrd];
		PvValue newPvValue;
		if (pertType == PvValuePerturber.PertType.JUMP) {
			final long cstRefSecs = origPvValue.getCstRefSecs();
			final int searchDurationSecs = origPvValue.getSearchDurationSecs();
			newPvValue = PvPlacerStatics.jumpSinglePv(manualFullArray, pv, cstRefSecs, searchDurationSecs);
		} else {
			newPvValue = PvValuePerturber.perturbPvValue(origPvValue, pertType, /* roughAndReady= */!currentIsFeasible,
					/* repeatCount= */1);
		}
		if (newPvValue.compareTo(origPvValue) != 0) {
			manualFullArray[grandOrd] = newPvValue;
			final PvValueArrayPlus newPlus = new PvValueArrayPlus(_planner, manualFullArray);
			final String explanatoryString = "@Manual Adjustment@";
			_solver2.setCurrentPlus(newPlus, explanatoryString);
		}
	}

	/** Keep track of the various types of Listeners. */
	public void addOptimizationEventListener(final OptimizationEvent.Listener listener) {
		_optimizationEventListeners.add(listener);
	}

	public void addJumpEventListener(final JumpEvent.Listener listener) {
		_jumpEventListeners.add(listener);
	}

	public void addChangeActiveSetEventListener(final ChangeActiveSetEvent.Listener listener) {
		_changeActiveSetEventListeners.add(listener);
	}

	/** Firing sections here. */
	public void fireOptimizationEvent(final OptimizationEvent optimizationEvent, final boolean plagiarize) {
		if (optimizationEvent == null) {
			return;
		}
		final Solver solver = optimizationEvent.getSolver();
		if (solver == null) {
			return;
		}
		synchronized (_optimizationEventListeners) {
			final int nListeners = _optimizationEventListeners.size();
			for (int k = 0; k < nListeners; ++k) {
				final OptimizationEvent.Listener listener = _optimizationEventListeners.get(k);
				listener.react(optimizationEvent);
			}
		}
		final PvValueArrayPlus eventPlus = optimizationEvent.getPlus();
		if (eventPlus == null) {
			return;
		}
		/** Plagiarize if called upon to do so. */
		if (plagiarize && solver != _pqSolver && optimizationEvent.getIsBest() && _PqPlagiarizes) {
			/**
			 * We handle our plagiarism here. updateBest does not setCurrent, nor does it
			 * fire anything.
			 */
			final String explanatoryString = "Plagiarism";
			final OptimizationEvent pqBestEvent = _pqSolver.updateBest(eventPlus, explanatoryString);
			if (pqBestEvent != null) {
				_pqSolver.runWith(_planner.getSimCase(), pqBestEvent.getPlus());
				synchronized (_optimizationEventListeners) {
					final int nListeners = _optimizationEventListeners.size();
					for (int k = 0; k < nListeners; ++k) {
						final OptimizationEvent.Listener listener = _optimizationEventListeners.get(k);
						listener.react(optimizationEvent);
					}
				}
			}
		}
		/** If manual has nothing at all, set it to this. */
		if (_solver2 != null && solver != _solver2) {
			final PvValueArrayPlus manualCurrent = _solver2.getCurrentPlus();
			if (manualCurrent == null) {
				/**
				 * Override plagiarize; we don't want plagiarism when _solver2 is being given
				 * something by _pqSolver.
				 */
				final String explanatoryString = "Inherited";
				_solver2.setCurrentPlus(eventPlus, explanatoryString, /* plagiarize= */false);
				final OptimizationEvent optimizationEvent2 = _solver2.updateBest(eventPlus, explanatoryString);
				if (optimizationEvent2 != null) {
					synchronized (_optimizationEventListeners) {
						for (final OptimizationEvent.Listener listener : _optimizationEventListeners) {
							listener.react(optimizationEvent2);
						}
					}
				}
			}
		}
	}

	public void fireJumpEvent(final JumpEvent jumpEvent) {
		synchronized (_jumpEventListeners) {
			for (final JumpEvent.Listener listener : _jumpEventListeners) {
				listener.react(jumpEvent);
			}
		}
	}

	public void fireDeconflictionEvent(final DeconflictEvent deconflictionEvent) {
		synchronized (_deconflictionEventListeners) {
			for (final DeconflictEvent.Listener listener : _deconflictionEventListeners) {
				listener.react(deconflictionEvent);
			}
		}
	}

	public void reactToChangedActiveSet() throws Exception {
		/** pqSolver takes care of itself, but we must give it time. */
		_pqSolver.setMustReactToNewActive(true);
		_pqSolver.resumeIteration("SolversManager: React to Changed Active Set 1");
		final SimCaseManager.SimCase simCase = _planner.getSimCase();
		do {
			try {
				Thread.sleep(100);
			} catch (final InterruptedException e) {
			}
		} while (_pqSolver.getMustReactToNewActive());
		if (_solver2 != null) {
			final PvValueArrayPlus oldManual = _solver2.getCurrentPlus();
			final boolean plagiarize = false;
			final PvValueArrayPlus newManual = PvPlacerStatics.adjustToActive(oldManual, /* ftPosFunction= */null,
					/* nftPosFunction= */null);
			if (!simCase.getKeepGoing()) {
				return;
			}
			final String explanatoryString = "@Adjust to Active@";
			_solver2.setCurrentPlus(newManual, explanatoryString, plagiarize);
			_solver2.resumeIteration("SolversManager: React to Changed Active Set 2");
		}
		/** Inform any interested party that the active set changed. */
		final PlannerModel plannerModel = _planner.getPlannerModel();
		final ChangeActiveSetEvent changeActiveSetEvent = new ChangeActiveSetEvent(plannerModel);
		synchronized (_changeActiveSetEventListeners) {
			for (final ChangeActiveSetEvent.Listener listener : _changeActiveSetEventListeners) {
				listener.react(changeActiveSetEvent);
			}
		}
	}

	// TMK!! Get rid of this.
	public void reactToUserFreezeOrUnfreeze() throws Exception {
		final PvValueArrayPlus oldPlus = _pqSolver.getCurrentPlus();
		final PvValueArrayPlus newPlus = convertToIsActiveAndFrozens(oldPlus);
		_pqSolver.runWith(_planner.getSimCase(), newPlus);
		if (_solver2 != null) {
			final PvValueArrayPlus oldPlus2 = _solver2.getCurrentPlus();
			final PvValueArrayPlus newPlus2 = convertToIsActiveAndFrozens(oldPlus2);
			final String explanatoryString = "@React to Freeze@";
			_solver2.setCurrentPlus(newPlus2, explanatoryString);
			_solver2.resumeIteration("SolversManager: React to Freeze 1");
		}
		/** Something might have changed. */
		_pqSolver.resumeIteration("SolversManager: React to Freeze 2");
	}

	/**
	 * Wrapper for simpleConvertToIsActiveAndFrozens to have an api of a plus
	 * instead of an array.
	 */
	public PvValueArrayPlus convertToIsActiveAndFrozens(final PvValueArrayPlus plus) {
		if (plus == null) {
			return null;
		}
		final Planner planner = plus.getPlanner();
		final PvValue[] pvValuesIn = plus.getCopyOfPvValues();
		final PvValue[] pvValues = convertToIsActiveAndFrozens(pvValuesIn);
		return new PvValueArrayPlus(planner, pvValues);
	}

	/**
	 * Fills in nulls for the userInactives, eliminates onMars, and overwrites
	 * supplied PvValues with userFrozen ones. Input does not have to be canonical-
	 * full, but this returns canonical-full.
	 */
	public PvValue[] convertToIsActiveAndFrozens(final PvValue[] pvValues) {
		final PlannerModel plannerModel = _planner.getPlannerModel();
		final int nPttrnVbls = plannerModel.getNPttrnVbls();
		final PvValue[] returnValue = new PvValue[nPttrnVbls];
		Arrays.fill(returnValue, null);
		/** Fill in frozens. */
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PatternVariable pv = plannerModel.grandOrdToPv(grandOrd);
			if (pv.isActive()) {
				final PvValue userFrozenPvValue = pv.getUserFrozenPvValue();
				if (userFrozenPvValue != null) {
					returnValue[grandOrd] = userFrozenPvValue;
				}
			}
		}
		/** Fill more in with pvValues. */
		final int nIn = pvValues == null ? 0 : pvValues.length;
		for (int k = 0; k < nIn; ++k) {
			final PvValue pvValue = pvValues[k];
			if (pvValue == null) {
				continue;
			}
			final PatternVariable pv = pvValue.getPv();
			final int grandOrd = pv.getGrandOrd();
			if (returnValue[grandOrd] == null) {
				returnValue[grandOrd] = pvValue.convertToFull();
			}
		}
		return returnValue;
	}

}
