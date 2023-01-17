package com.skagit.sarops.planner.solver;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.planner.posFunction.PosFunction;
import com.skagit.sarops.planner.posFunction.PosFunction.EvalType;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.planner.posFunction.pvValueArrayPlus.CtV;
import com.skagit.sarops.planner.posFunction.pvValueArrayPlus.PvValueArrayPlus;
import com.skagit.sarops.planner.solver.pqSolver.PvValuePerturber;
import com.skagit.sarops.planner.solver.pqSolver.pvPlacer.PvPlacerStatics;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.StringUtilities;
import com.skagit.util.TimeUtilities;
import com.skagit.util.WorkStopper;

abstract public class Solver extends WorkStopper.Standard
		implements Comparable<Solver> {
	final public static double _LookBackRatioForDoNotStop = -1d;

	protected enum JumpStyle {
		NO_PREVIOUS_SOLUTION("NoPrvSln"), //
		FOREIGN_REPLACEMENT("FrgnRplc"), //
		USE_JUMP_SEQUENCE("JmpSqnce"), //
		MODIFY_CURRENT("MdfyCrnt"), //
		MAKE_FROM_SCRATCH("MkFrmScx");

		final private String _name;

		private JumpStyle(final String name) {
			_name = name;
		}

		public String getString() {
			return _name;
		}

		static final JumpStyle[] _Values = JumpStyle.values();
	}

	protected final Planner _planner;
	final private Semaphore _iteratorBlocker;
	private java.util.concurrent.Future<?> _solverFuture;
	private boolean _keepRefining;

	final private TreeMap<PatternVariable[], OptimizationEvent> _bestOptimizationEvents;
	final private TreeMap<PatternVariable[], OptimizationEvent> _baseLineOptimizationEvents;
	final private TreeMap<PatternVariable[], TreeMap<Long, OptimizationEvent>> _timeTables;
	private PvValueArrayPlus _currentPlus;
	final private String _startDateString;
	private String _bestDateString;
	protected int _nJumpsSinceLastImprovement;
	protected long _startTimeMs;
	protected long _endTimeMs;
	private PvValueArrayPlus _lastJump;
	protected JumpStyle _lastJumpStyle;
	protected String _lastJumpString;
	protected final int[] _nLikeThis;
	private boolean _mustReactToNewActive;
	final private long[] _coreBodyTimeStepCompletions;
	private int _nJumps = 0;

	private final boolean _isSolver0;

	abstract public JumpStyle jump(final String caption,
			final PosFunction ftPosFunction, final PosFunction nftPosFunction);

	abstract protected void refine(final PosFunction ftPosFunction,
			final PosFunction nftPosFunction);

	final public void clearSmallSamplePosFunctionEvals() {
		for (final OptimizationEvent e : _bestOptimizationEvents.values()) {
			e.getPlus().clearSmallSamplePosFunctionEvals();
		}
		for (final OptimizationEvent e : _baseLineOptimizationEvents.values()) {
			e.getPlus().clearSmallSamplePosFunctionEvals();
		}
		final int n1 = _timeTables.size();
		final Iterator<Map.Entry<PatternVariable[], TreeMap<Long, OptimizationEvent>>> it1 =
				_timeTables.entrySet().iterator();
		for (int k1 = 0; k1 < n1; ++k1) {
			final Map.Entry<PatternVariable[], TreeMap<Long, OptimizationEvent>> entry1 =
					it1.next();
			final TreeMap<Long, OptimizationEvent> entry2 = entry1.getValue();
			final int n3 = entry2.size();
			final Iterator<Map.Entry<Long, OptimizationEvent>> it3 =
					entry2.entrySet().iterator();
			for (int k3 = 0; k3 < n3; ++k3) {
				final Map.Entry<Long, OptimizationEvent> entry3 = it3.next();
				final OptimizationEvent optimizationEvent = entry3.getValue();
				optimizationEvent.getPlus().clearSmallSamplePosFunctionEvals();
			}
		}
		if (_currentPlus != null) {
			_currentPlus.clearSmallSamplePosFunctionEvals();
		}
	}

	/** The rest do not need to be overridden. */
	public void stopRefining() {
		_keepRefining = false;
	}

	public void allowRefining() {
		_keepRefining = true;
	}

	public boolean getMustReactToNewActive() {
		return _mustReactToNewActive;
	}

	public void setMustReactToNewActive(final boolean mustReactToNewActive) {
		_mustReactToNewActive = mustReactToNewActive;
	}

	@Override
	public boolean getKeepGoing() {
		/**
		 * If the SimCase is not supposed to keep going, then neither is this
		 * Solver.
		 */
		final SimCaseManager.SimCase simCase = _planner.getSimCase();
		if (!simCase.getKeepGoing()) {
			return false;
		}
		final boolean plannerWasToldToSit = !_planner.wasToldToSit();
		if (!plannerWasToldToSit) {
			return false;
		} /**
			 * simCase has a different _keepGoing that was referenced in
			 * simCase.getKeepGoing(). The following _keepGoing is the solver's
			 * _keepGoing.
			 */
		return _keepGoing;
	}

	public boolean isGoing() {
		return _solverFuture != null && !_solverFuture.isDone();
	}

	public boolean getKeepRefining() {
		if (!getKeepGoing() || _planner.wasToldToSit() || !_keepRefining || _mustReactToNewActive) {
			return false;
		}
		return true;
	}

	public Solver(final Planner planner, final String name,
			final boolean isSolver0) {
		super(name);
		_planner = planner;
		final SimCaseManager.SimCase simCase = _planner.getSimCase();
		_iteratorBlocker = new Semaphore(0);
		allowGoing();
		_nLikeThis = new int[JumpStyle._Values.length];
		_currentPlus = null;
		_bestOptimizationEvents =
				new TreeMap<>(PlannerModel._PvArrayComparator);
		_baseLineOptimizationEvents =
				new TreeMap<>(PlannerModel._PvArrayComparator);
		_timeTables = new TreeMap<>(PlannerModel._PvArrayComparator);
		final long nowInRefSecs =
				TimeUtilities.convertToRefSecs(System.currentTimeMillis() / 1000L);
		_startDateString = TimeUtilities.formatTime(nowInRefSecs, true);
		_bestDateString = _startDateString;
		SimCaseManager.out(simCase,
				String.format("%s constructed at %s", name, _startDateString));
		_bestDateString = _startDateString;
		_nJumpsSinceLastImprovement = 0;
		_lastJump = null;
		_lastJumpStyle = JumpStyle.NO_PREVIOUS_SOLUTION;
		_lastJumpString = "";
		_coreBodyTimeStepCompletions = null;
		_startTimeMs = _endTimeMs = -1L;
		_isSolver0 = isSolver0;
	}

	public void suspendIteration(final String comment) {
		try {
			_iteratorBlocker.acquire();
		} catch (final InterruptedException e) {
		}
	}

	public long getStartTimeMs() {
		return _startTimeMs;
	}

	public void resumeIteration(final String comment) {
		_iteratorBlocker.release();
	}

	final public void startIterating(final int secondsToSolve) {
		_startTimeMs = System.currentTimeMillis();
		_endTimeMs = _startTimeMs + Math.max(1, secondsToSolve) * 1000;
		final SimCaseManager.SimCase simCase = _planner.getSimCase();
		final SolversManager solversManager = _planner.getSolversManager();
		final PlannerModel plannerModel = _planner.getPlannerModel();
		if (!isManual()) {
			allowGoing();
			allowRefining();
			final Runnable pqRunnable = new Runnable() {
				@Override
				public void run() {
					final long nowRefSecs = TimeUtilities
							.convertToRefSecs(System.currentTimeMillis() / 1000L);
					final String currentDateString =
							TimeUtilities.formatTime(nowRefSecs, true);
					SimCaseManager.out(simCase, String.format("%s started up at %s",
							getName(), currentDateString));
					while (getKeepGoing()) {
						/** If there currently is no active floater, wait. */
						if (!plannerModel.isEvalRun() &&
								!plannerModel.hasActiveFloater()) {
							final PvValueArrayPlus convertedCurrent = solversManager
									.convertToIsActiveAndFrozens(getCurrentPlus());
							try {
								final String explanatoryString = "Conversion";
								setCurrentPlus(convertedCurrent, explanatoryString);
							} catch (final Exception e) {
							}
							/**
							 * The following statement forces us to call resumeIterating
							 * after we change an active set.
							 */
							Solver.this.suspendIteration("@No Active Floaters@");
						}
						final PatternVariable[] activeSet = plannerModel.getActiveSet();
						int nFloaters = 0;
						for (final PatternVariable pv : activeSet) {
							if (pv.getCanUserMove()) {
								++nFloaters;
							}
						}

						/**
						 * We're about to create new PosFunctions, and the small sample
						 * evaluations are now no longer valid.
						 */
						clearSmallSamplePosFunctionEvals();

						final PosFunction[] posFunctionPair =
								_planner.createPosFunctionPairForOptnPass(nFloaters);
						final PosFunction ftPosFunction = posFunctionPair[0];
						final PosFunction nftPosFunction = posFunctionPair[1];

						/** Make a jump. */
						JumpStyle jumpStyle = null;
						try {
							final String jumpCaption =
									String.format("Jump #[%d].", _nJumps);
							jumpStyle = jump(jumpCaption, ftPosFunction, nftPosFunction);
							if (_nJumps == 0 && nFloaters > 0) {
								clearCtV(ftPosFunction, nftPosFunction);
							}
							++_nJumps;
						} catch (final Exception e) {
							final String errorString =
									String.format("PlannerMain: Unknown Error 1:\n%s",
											StringUtilities.getStackTraceString(e));
							SimCaseManager.err(simCase, errorString);
							simCase._interrupted = true;
							return;
						}
						/** If this is an eval, we're done. */
						if (plannerModel.isEvalRun()) {
							return;
						}
						/** React to a changed active set. */
						if (getMustReactToNewActive()) {
							/**
							 * This is out of date with active. Bring it up to date,
							 * starting with the old best.
							 */
							final OptimizationEvent optimizationEvent =
									getBestOptimizationEvent(activeSet);
							final PvValueArrayPlus oldBest;
							if (optimizationEvent == null) {
								oldBest = _currentPlus;
							} else {
								oldBest = optimizationEvent.getPlus();
							}
							final PvValueArrayPlus adjusted = PvPlacerStatics
									.adjustToActive(oldBest, ftPosFunction, nftPosFunction);
							try {
								final String explanatoryString =
										"@React to New Active Set@";
								setCurrentPlus(adjusted, explanatoryString);
							} catch (final Exception e) {
							}
							setMustReactToNewActive(false);
							resumeIteration("Reacted to Change Active");
						}
						/** Refine. */
						assert jumpStyle != null : "JumpStyle should be something!";
						/**
						 * Do not refine if all PatternVariables were initialized and
						 * this is our first jump. Otherwise, refine.
						 */
						final boolean doRefine;
						if (plannerModel.getAllPttrnVblesAreInitialized()) {
							doRefine = jumpStyle != JumpStyle.NO_PREVIOUS_SOLUTION;
						} else {
							doRefine = _currentPlus != null;
						}
						if (doRefine) {
							try {
								refine(ftPosFunction, nftPosFunction);
							} catch (final Exception e) {
								e.printStackTrace();
							}
							if (!getKeepGoing()) {
								continue;
							}
						}
						plannerModel.updateLatestSeed();
					}
					SimCaseManager.out(simCase, "\nPqSolver is finished.");
					final Solver solver = Solver.this;
					synchronized (solver) {
						solver.notifyAll();
					}
				}
			};
			if (getKeepGoing()) {
				/**
				 * We don't have to find how many are free right now because we are
				 * going to submit this to the worker pool no matter what. But we do
				 * this to keep a consistent paradigm. See
				 * #PvValueBox2.DetectValuesArray2Getter for a good example of the
				 * full paradigm.
				 */
				final SimCaseManager simCaseManager = _planner.getSimCaseManager();
				final Object lockOnWorkersThreadPool =
						simCaseManager.getLockOnWorkersThreadPool();
				int nFreeWorkers = 0;
				while (nFreeWorkers == 0) {
					synchronized (lockOnWorkersThreadPool) {
						nFreeWorkers =
								simCaseManager.getNFreeWorkerThreads(simCase, "Solver");
						if (nFreeWorkers > 0) {
							_solverFuture =
									simCaseManager.submitToWorkers(simCase, pqRunnable);
							if (_solverFuture != null) {
								break;
							}
						}
					}
					final String warning =
							"Had trouble starting a solver iteration." +
									"  Increase # of threads in SimCaseManager.properties.";
					SimCaseManager.err(simCase, warning);
					try {
						/**
						 * Wait for a bit, but give the Solver a bit more extra time.
						 */
						Thread.sleep(3000);
						addMsToEndSolveTime(3000);
					} catch (final InterruptedException e) {
					}
				}
			}
		}
	}

	@Override
	public void stopIfGoing(final boolean cancel) {
		super.stopIfGoing(cancel);
		resumeIteration("Stop if Going");
		if (_solverFuture != null) {
			try {
				_solverFuture.get();
			} catch (final CancellationException e) {

			} catch (final InterruptedException e) {

			} catch (final ExecutionException e) {

			}
		}
		_solverFuture = null;
	}

	public OptimizationEvent updateBest(final PvValueArrayPlus newPlus,
			final String explanatoryString) {
		if (newPlus == null) {
			return null;
		}
		final SimCaseManager.SimCase simCase = _planner.getSimCase();
		final PlannerModel plannerModel = _planner.getPlannerModel();
		final PatternVariable[] activeSet =
				plannerModel.getActiveSetFor(newPlus);

		synchronized (_bestOptimizationEvents) {
			final OptimizationEvent incumbent =
					getBestOptimizationEventWithoutSynchronizing(activeSet);
			final PvValueArrayPlus oldPlus =
					incumbent == null ? null : incumbent.getPlus();
			if (!newPlus.iAmBetterForOptn(activeSet, oldPlus)) {
				return null;
			}

			/** new is better. */
			final boolean newIsFeasible = newPlus.isFeasible();
			final PosFunction posFunctionToUse =
					newIsFeasible ? _planner.getPosFunctionForFbleOptn() :
							_planner.getPosFunctionForInfblOptn();

			final double newPos = newPlus.getPos(posFunctionToUse);
			final OptimizationEvent optimizationEvent =
					new OptimizationEvent(this, newPlus, explanatoryString, true);
			final String solverName = getName();
			final CtV worstForOptnCtv = newPlus.getWorstForOptnCtV();
			if (!getKeepGoing()) {
				return null;
			}
			final String s;
			if (worstForOptnCtv == null) {
				final String formatString = "New Best for %s (fble) Pos[%f].";
				s = String.format(formatString, solverName, newPos);
			} else {
				final String formatString =
						"New Best for %s.  " + "Ctv[%s/%.3f] Pos[%.3f]";
				s = String.format(formatString, solverName,
						worstForOptnCtv.getString(), worstForOptnCtv.getForOptnV(),
						newPos);
			}
			SimCaseManager.out(simCase, s);
			final long nowInRefSecs = TimeUtilities
					.convertToRefSecs(System.currentTimeMillis() / 1000L);
			_bestDateString = TimeUtilities.formatTime(nowInRefSecs, true);
			_bestOptimizationEvents.put(activeSet, optimizationEvent);

			/**
			 * Since this is best, it might be baseLine. We update "baseLine" if
			 * old is null or old is infeasible or oldPos == 0.
			 */
			final OptimizationEvent baseLineOptimizationEvent =
					_baseLineOptimizationEvents.get(activeSet);
			boolean updateBaseLine = baseLineOptimizationEvent == null;
			if (!updateBaseLine) {
				final PvValueArrayPlus baseLinePlus =
						baseLineOptimizationEvent.getPlus();
				updateBaseLine = !baseLinePlus.isFeasible();
				if (!updateBaseLine) {
					updateBaseLine = baseLinePlus.getPos(posFunctionToUse) == 0;
					if (!getKeepGoing()) {
						return null;
					}
				}
			}
			if (updateBaseLine) {
				_baseLineOptimizationEvents.put(activeSet, optimizationEvent);
			}
			_nJumpsSinceLastImprovement = 0;
			/** Update the TimeTable for activeSet. */
			if (newPlus.isFeasible()) {
				TreeMap<Long, OptimizationEvent> timeTable =
						_timeTables.get(activeSet);
				if (timeTable == null) {
					timeTable = new TreeMap<>();
					_timeTables.put(activeSet, timeTable);
				}
				timeTable.put(nowInRefSecs, optimizationEvent);
			}
			/**
			 * The following is necessary if we are doing this from solversManager
			 * for initializing _solver2.
			 */
			if (_currentPlus == null) {
				_currentPlus = newPlus;
			}
			return optimizationEvent;
		}
	}

	public double getLookBackRatio(final long lookBackIntervalSecs) {
		final long nowRefSecs =
				TimeUtilities.convertToRefSecs(System.currentTimeMillis() / 1000L);
		final long thenRefSecs = nowRefSecs - lookBackIntervalSecs;
		final PlannerModel plannerModel = _planner.getPlannerModel();
		final PatternVariable[] activeSet = plannerModel.getActiveSet();
		final TreeMap<Long, OptimizationEvent> timeTable =
				_timeTables.get(activeSet);
		if (timeTable == null) {
			return _LookBackRatioForDoNotStop;
		}
		final Long floorKey = timeTable.floorKey(thenRefSecs);
		if (floorKey == null) {
			return _LookBackRatioForDoNotStop;
		}
		final OptimizationEvent bestThen = timeTable.get(floorKey);
		if (bestThen == null) {
			return _LookBackRatioForDoNotStop;
		}
		final PvValueArrayPlus bestThenPlus = bestThen.getPlus();
		if (!bestThenPlus.isFeasible()) {
			return _LookBackRatioForDoNotStop;
		}
		if (!getKeepGoing()) {
			return Double.NaN;
		}
		final PosFunction posFunction = _planner.getPosFunctionForFbleOptn();
		final double bestThenPos = bestThenPlus.getPos(posFunction);
		if (!(bestThenPos > 0d)) {
			return _LookBackRatioForDoNotStop;
		}
		final Long nowKey = timeTable.floorKey(nowRefSecs);
		final OptimizationEvent bestNow = timeTable.get(nowKey);
		if (bestNow == null) {
			return _LookBackRatioForDoNotStop;
		}
		final PvValueArrayPlus bestNowPlus = bestNow.getPlus();
		if (!bestNowPlus.isFeasible()) {
			return _LookBackRatioForDoNotStop;
		}
		if (!getKeepGoing()) {
			return Double.NaN;
		}
		final double bestNowPos = bestNowPlus.getPos(posFunction);
		if (!(bestNowPos > 0d)) {
			return _LookBackRatioForDoNotStop;
		}
		return bestNowPos / bestThenPos;
	}

	final public void setCurrentPlus(final PvValueArrayPlus plus,
			final String explanatoryString) {
		setCurrentPlus(plus, explanatoryString, /* plagiarize= */true);
	}

	public static PvValueArrayPlus computeBestPlus(final Planner planner,
			final PosFunction posFunction, final PvValueArrayPlus plus,
			final int grandOrd) {
		PvValueArrayPlus bestPlus = plus;
		double bestPos = bestPlus.getPos(posFunction);
		final PvValue[] sandbox = bestPlus.getCopyOfPvValues();
		/** For Nft evaluations, it doesn't matter. */
		if (sandbox[grandOrd] == null || sandbox[grandOrd].onMars() || (posFunction._evalType == EvalType.GROWING_SAMPLE_NFT) ||
				(posFunction._evalType == EvalType.FXD_SAMPLE_NFT)) {
			return plus;
		}
		/** Rotate around. */
		for (int k = 0; k < 3; ++k) {
			final PvValue oldPvValue = sandbox[grandOrd];
			final PatternVariable pv = oldPvValue.getPv();
			if (pv.getUserFrozenPvValue() != null) {
				break;
			}
			// TMK!! Should make sure that transit does not grow.
			if (pv.getPvSeq() != null) {
				break;
			}
			final PvValuePerturber.PertType littlePertType =
					(k % 2 == 0) ? PvValuePerturber.PertType.TOGGLE_FIRST_TURN_RIGHT :
							PvValuePerturber.PertType.ROTATE_180;
			final int repeatCount = 1;
			final PvValue newPvValue = PvValuePerturber.perturbPvValue(oldPvValue,
					littlePertType, /* roughAndReady= */true, repeatCount);
			if (newPvValue == oldPvValue) {
				break;
			}
			sandbox[grandOrd] = newPvValue;
			final PvValueArrayPlus thisPlus =
					new PvValueArrayPlus(planner, sandbox);
			final double thisPos = thisPlus.getPos(posFunction);
			if (thisPos > bestPos) {
				bestPos = thisPos;
				bestPlus = thisPlus;
			}
		}
		return bestPlus;
	}

	public static PvValueArrayPlus computeBestPlus(final Planner planner,
			final PosFunction posFunction, final PvValueArrayPlus plusIn) {
		final PlannerModel plannerModel = planner.getPlannerModel();
		final int nPttrnVbls = plannerModel.getNPttrnVbls();
		PvValueArrayPlus currentPlus = plusIn;
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			currentPlus =
					computeBestPlus(planner, posFunction, currentPlus, grandOrd);
		}
		return currentPlus;
	}

	public void setCurrentPlus(final PvValueArrayPlus plus,
			final String explanatoryString, final boolean plagiarize) {
		final SolversManager solversManager = _planner.getSolversManager();
		_currentPlus = plus;
		final OptimizationEvent optimizationEvent1 = new OptimizationEvent(this,
				_currentPlus, explanatoryString, /* isBest= */false);
		/** We never plagiarize a non-best. */
		solversManager.fireOptimizationEvent(optimizationEvent1,
				/* plagiarize= */false);
		final OptimizationEvent optimizationEvent2 =
				updateBest(_currentPlus, explanatoryString);
		if (optimizationEvent2 != null) {
			solversManager.fireOptimizationEvent(optimizationEvent2, plagiarize);
		}
		blockIfStepping();
	}

	public PvValueArrayPlus getCurrentPlus() {
		return _currentPlus;
	}

	public boolean isManual() {
		return false;
	}

	public String getProgressString() {
		return null;
	}

	public String getStartDateString() {
		return _startDateString;
	}

	public String getBestDateString() {
		return _bestDateString;
	}

	public void addMsToEndSolveTime(final long ms) {
		if (_coreBodyTimeStepCompletions != null) {
			for (int k = 0; k < _coreBodyTimeStepCompletions.length; ++k) {
				_coreBodyTimeStepCompletions[k] += ms;
			}
		}
		_endTimeMs += ms;
	}

	public int getNJumpsSinceLastImprovement() {
		return _nJumpsSinceLastImprovement;
	}

	public int getNJumps() {
		return _nJumps;
	}

	public long getMsRemaining() {
		final long msRemaining = _endTimeMs - System.currentTimeMillis();
		return Math.max(0, msRemaining);
	}

	@Override
	public int compareTo(final Solver solver) {
		if (getName() == null && solver.getName() != null) {
			return -1;
		}
		if (getName() != null && solver.getName() == null) {
			return 1;
		}
		if (getName() != null) {
			final int compareValue = getName().compareTo(solver.getName());
			if (compareValue != 0) {
				/** Just to keep pq solvers in front of manual solvers. */
				return -compareValue;
			}
		}
		return 0;
	}

	public Planner getPlanner() {
		return _planner;
	}

	/** Manipulating _bestOptimizationEvents. */
	public boolean getHaveFeasible(final PatternVariable[] activeSet) {
		final OptimizationEvent bestOptimizationEvent =
				getBestOptimizationEvent(activeSet);
		if (bestOptimizationEvent == null) {
			return false;
		}
		final PvValueArrayPlus bestPlus = bestOptimizationEvent.getPlus();
		return bestPlus.isFeasible();
	}

	public OptimizationEvent getBestOptimizationEvent(
			final PatternVariable[] activeSet) {
		synchronized (_bestOptimizationEvents) {
			return getBestOptimizationEventWithoutSynchronizing(activeSet);
		}
	}

	public OptimizationEvent getBaseLineOptimizationEvent(
			final PatternVariable[] activeSet) {
		synchronized (_baseLineOptimizationEvents) {
			final OptimizationEvent baseLineOptimizationEvent =
					_baseLineOptimizationEvents.get(activeSet);
			return baseLineOptimizationEvent;
		}
	}

	private OptimizationEvent getBestOptimizationEventWithoutSynchronizing(
			final PatternVariable[] activeSet) {
		return _bestOptimizationEvents.get(activeSet);
	}

	protected int getNLikeThis(final JumpStyle jumpStyle) {
		return _nLikeThis[jumpStyle.ordinal()];
	}

	protected void increment(final JumpStyle jumpStyle) {
		++_nLikeThis[jumpStyle.ordinal()];
	}

	public String getLastJumpString() {
		return _lastJumpString;
	}

	protected PvValueArrayPlus getLastJump() {
		return _lastJump;
	}

	protected void setLastJump(final PvValueArrayPlus lastJump) {
		_lastJump = lastJump;
	}

	public abstract void clearCtV(final PosFunction ftPosFunction,
			final PosFunction nftPosFunction);

	public PvValueArrayPlus getBestForActiveSet(
			final PatternVariable[] activeSet) {
		final OptimizationEvent optimizationEvent =
				getBestOptimizationEvent(activeSet);
		return optimizationEvent == null ? null : optimizationEvent.getPlus();
	}

	/** This convenience function is called very often. */
	public PvValueArrayPlus getBestForCurrentActive() {
		final PlannerModel plannerModel = _planner.getPlannerModel();
		final PatternVariable[] activeSet = plannerModel.getActiveSet();
		return getBestForActiveSet(activeSet);
	}

	public boolean isSolver0() {
		return _isSolver0;
	}

}
