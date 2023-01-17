package com.skagit.sarops.planner.solver.pqSolver;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.Locale;

import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.plannerModel.PatternKind;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.planner.plannerModel.pvSeq.PvValuesFitter;
import com.skagit.sarops.planner.posFunction.PosFunction;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.planner.posFunction.pvValueArrayPlus.CtV;
import com.skagit.sarops.planner.posFunction.pvValueArrayPlus.PvValueArrayPlus;
import com.skagit.sarops.planner.solver.Solver;
import com.skagit.sarops.planner.solver.SolversManager;
import com.skagit.sarops.planner.solver.pqSolver.PvValuePerturber.PertType;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.CombinatoricTools;
import com.skagit.util.Constants;

public class PqRefiner {
	final private static long _MsForPosStage = 7500;
	final private static int _NMaxItersForPos = 100;
	final private static double _MinRelGrowthPos = 0.01;
	final private static int _NLookBacks = 10;
	final private static double _MaxDegradeForStartingZeroIn = 0.2;

	final private PqSolver _pqSolver;
	private int _nDoPMoves;

	PqRefiner(final PqSolver pqSolver) {
		_pqSolver = pqSolver;
		_nDoPMoves = 0;
	}

	void doPMoves(final Planner planner, final PqSolver pqSolver,
			final PosFunction ftPosFunction, final PosFunction nftPosFunction,
			final boolean clearCtVOnly) {
		final SimCaseManager.SimCase simCase = planner.getSimCase();
		final int nRefinementStages = RefinementStage._Values.length;
		/** Clear constraints in the first pass. */
		for (int iPass = 0; iPass < 2; ++iPass) {
			if (iPass == 1 && clearCtVOnly) {
				break;
			}
			for (int k = 0; k < nRefinementStages; ++k) {
				if (!_pqSolver.getKeepRefining()) {
					return;
				}
				final RefinementStage stage = RefinementStage._Values[k];
				/** Skip ZERO_IN2; it's too time-consuming. */
				if ((stage == RefinementStage.ZERO_IN2) || (iPass == 0 && !stage._forClearingCts)) {
					continue;
				}
				final String stageName = stage.name();
				final char passChar = iPass == 0 ? Constants._UpwardsArrow :
						Constants._DownwardsArrow;
				final String format1 = "Into %s%c of nDoPMoves[%d]";
				final String s1 =
						String.format(format1, stageName, passChar, _nDoPMoves);
				SimCaseManager.out(simCase, s1);
				final PvValueArrayPlus oldPlus = pqSolver.getCurrentPlus();
				final PvValueArrayPlus newPlus = doStage(pqSolver, ftPosFunction,
						nftPosFunction, oldPlus, stage, /* roughAndReady= */true);
				if (newPlus != null && newPlus != oldPlus) {
					pqSolver.setCurrentPlus(newPlus,
							String.format("pqRefiner %s", stageName));
				}
				final String format2 = "Out of %s%c of nDoPMoves[%d]";
				final String s2 =
						String.format(format2, stageName, passChar, _nDoPMoves);
				SimCaseManager.out(simCase, s2);
				if (_pqSolver.getMustReactToNewActive()) {
					_pqSolver.stopRefining();
				}
			}
			if (clearCtVOnly) {
				break;
			}
		}
		++_nDoPMoves;
	}

	public static PvValueArrayPlus doStage(final Solver solver,
			final PosFunction ftPosFunction, final PosFunction nftPosFunction,
			final PvValueArrayPlus plusInA, final RefinementStage stage,
			final boolean roughAndReady) {
		if (plusInA == null) {
			return null;
		}

		final PvValueArrayPlus plusIn;
		if (stage == RefinementStage.CLR_OVL) {
			plusIn = plusInA;
		} else {
			/** If zeroing in, and we have ovl constraints, clear them first. */
			if (stage.forZeroingIn() && !plusInA.hasClearOvl()) {
				plusIn = doStage(solver, ftPosFunction, nftPosFunction, plusInA,
						RefinementStage.CLR_OVL, roughAndReady);
				if (!plusIn.isFeasible()) {
					return plusIn;
				}
			} else {
				plusIn = plusInA;
			}
		}

		final Planner planner = plusIn.getPlanner();
		final SimCaseManager.SimCase simCase = planner.getSimCase();
		final PlannerModel plannerModel = planner.getPlannerModel();
		final int nPttrnVbls = plannerModel.getNPttrnVbls();
		final SolversManager solversManager = planner.getSolversManager();

		/** Establish which posFunction we use for this stage. */
		final PosFunction posFunction;
		switch (stage) {
		case PRELIM:
		case CLR_OVL:
			posFunction = nftPosFunction;
			break;
		case ZERO_IN1:
		case ZERO_IN2:
		default:
			posFunction = ftPosFunction;
			break;
		}

		/** Get information about where we are starting. */
		final PvValueArrayPlus incomingBestPlus;
		if (solver != null) {
			incomingBestPlus = solver.getBestForCurrentActive();
		} else {
			incomingBestPlus = null;
		}
		final boolean incomingHaveFeasible =
				incomingBestPlus != null && incomingBestPlus.isFeasible();

		/** Check to see if this is a worthwhile stage. */
		if (incomingHaveFeasible) {
			final PosFunction fblePosFunction =
					planner.getPosFunctionForFbleOptn();
			final double incomingBestPos =
					incomingBestPlus.getPos(fblePosFunction);
			final double incomingCurrentPos = plusIn.getPos(fblePosFunction);
			if (stage.forZeroingIn()) {
				if (incomingCurrentPos < incomingBestPos *
						(1d - _MaxDegradeForStartingZeroIn)) {
					return plusIn;
				}
			}
		}
		final PvValueArrayPlus firstPlus =
				Solver.computeBestPlus(planner, posFunction, plusIn);
		if (solver != null) {
			final String s = String.format("Entered DoStage[%s]", stage.name());
			solver.setCurrentPlus(firstPlus, s);
		}
		PvValueArrayPlus lastPlus = firstPlus;

		/**
		 * If we're clearing overlap, start by using a deconflicter, unless we
		 * have some strategy specifically in mind.
		 */
		boolean haveCleanClrOvl = false;

		/** Prepare for iterations. */
		PvValuePerturber.PertType lastPertType = null;
		PatternVariable lastPv = null;
		final double[] lookBackPosValues = new double[_NLookBacks];

		/** Compute the array of perturbable grandOrds. */
		final int[] perturbableGrandOrds = firstPlus.computePerturbableGrandOrds();
		final int nPerturbables = perturbableGrandOrds.length;
		int lastWinningIdx = -1;

		/** Start the passes through the Pv/PertType combinations. */
		long entranceMs = System.currentTimeMillis();
		/**
		 * Use a fibonacci sequence if we're going very slowly and trying to
		 * clear ovl.
		 */
		final CombinatoricTools.FibSeq fibSeq0 = new CombinatoricTools.FibSeq();
		fibSeq0.nextFib();
		int nextFib0 = fibSeq0.nextFib();

		boolean haveNewCurrentPlus = false;
		HAVE_NEW_PLUS: for (int passNumber = 0;; ++passNumber) {
			if (solver != null && !solver.getKeepRefining()) {
				return lastPlus;
			}
			boolean shiftsAndTwistsOnly = false;
			if (!incomingHaveFeasible && stage == RefinementStage.CLR_OVL &&
					passNumber > 10) {
				final int nextFib = fibSeq0.nextFib();
				if (nextFib > 0) {
					nextFib0 = nextFib;
				}
				shiftsAndTwistsOnly = true;
			}
			/** Set solver's currentPlus to lastPlus. */
			if (haveNewCurrentPlus) {
				final String stageInfoString =
						getStageInfoString(planner, posFunction, stage, passNumber,
								firstPlus, lastPlus, lastPv, lastPertType);
				if (solver != null) {
					solver.setCurrentPlus(lastPlus, stageInfoString);
				}
				SimCaseManager.out(simCase, stageInfoString);
			} else if (passNumber == 0) {
				final String stageInfoString =
						String.format("Just started stage[%s].", stage.name());
				if (solver != null) {
					solver.setCurrentPlus(lastPlus, stageInfoString);
				}
				SimCaseManager.out(simCase, stageInfoString);
			}
			haveNewCurrentPlus = false;
			if (stage._forClearingCts) {
				/**
				 * If Pos has gotten really bad, and there is a feasible solution
				 * already, so that we are not desperate, it probably won't improve
				 * enough.
				 */
				final PvValueArrayPlus thisBestPlus =
						solver == null ? null : solver.getBestForCurrentActive();
				if (thisBestPlus != null && thisBestPlus.isFeasible()) {
					final double thisBestPos = thisBestPlus.getPos(posFunction);
					final double lastPos = lastPlus.getPos(posFunction);
					if (lastPos < thisBestPos *
							(1d - 2d * _MaxDegradeForStartingZeroIn)) {
						/** Give up on this stage. */
						return lastPlus;
					}
				}
			}

			/** Decide if we want to go on. */
			if (stage == RefinementStage.CLR_OVL) {
				if (lastPlus.hasVeryClearOvl()) {
					return lastPlus;
				}
				if (lastPlus.hasClearOvl()) {
					haveCleanClrOvl = true;
				}
			}
			if (stage._forPos) {
				final double lastPos = lastPlus.getPos(posFunction);
				if (passNumber < _NLookBacks) {
					lookBackPosValues[passNumber] = lastPos;
				} else {
					final double lookBackPos = lookBackPosValues[0];
					if (lastPos < lookBackPos * (1d + _MinRelGrowthPos)) {
						/** Not enough progress. Done with this stage. */
						return lastPlus;
					}
					for (int k0a = 0; k0a < _NLookBacks - 1; ++k0a) {
						lookBackPosValues[k0a] = lookBackPosValues[k0a + 1];
					}
					lookBackPosValues[_NLookBacks - 1] = lastPos;
					if (stage.forZeroingIn()) {
						/**
						 * If we're making progress while zeroing in, well, "play out,
						 * Barney." In other words, let it ride by resetting entrancsMs.
						 */
						entranceMs = System.currentTimeMillis();
					}
				}
				if (passNumber >= _NMaxItersForPos ||
						System.currentTimeMillis() > entranceMs + _MsForPosStage) {
					/** Done with this stage. */
					return lastPlus;
				}
			}

			/** Get information about where we are now. */
			final boolean haveFeasibleNow;
			if (solver != null) {
				final PvValueArrayPlus bestPlusNow =
						solver.getBestForCurrentActive();
				haveFeasibleNow = bestPlusNow != null && bestPlusNow.isFeasible();
			} else {
				haveFeasibleNow = lastPlus.isFeasible();
			}

			/**
			 * If we have no feasible, we probably want to keep going with the Pv
			 * that just won to get a feasible as rapidly as possible. If we have
			 * a feasible, we'll give the other PttrnVbls a chance by pushing the
			 * one that just won down in the list.
			 */
			if (lastWinningIdx >= 0) {
				final int grandOrdToPushDown = perturbableGrandOrds[lastWinningIdx];
				if (!haveFeasibleNow) {
					for (int idx = lastWinningIdx; idx > 0; --idx) {
						perturbableGrandOrds[idx] = perturbableGrandOrds[idx - 1];
					}
					perturbableGrandOrds[0] = grandOrdToPushDown;
				} else {
					for (int idx = lastWinningIdx; idx < nPerturbables - 1; ++idx) {
						perturbableGrandOrds[idx] = perturbableGrandOrds[idx + 1];
					}
					perturbableGrandOrds[nPerturbables - 1] = grandOrdToPushDown;
				}
			}
			lastWinningIdx = -1;

			/**
			 * When clearing overlap, we might not find anything to move if we
			 * restrict ourselves only to small moves.
			 */
			final int maxRepeatCount;
			if (stage == RefinementStage.CLR_OVL) {
				maxRepeatCount = Integer.MAX_VALUE / 2;
			} else {
				maxRepeatCount = 1;
			}

			/**
			 * Use a Fibonacci sequence in repeatCount; this forces us to make
			 * some improvement.
			 */
			final CombinatoricTools.FibSeq fibSeq1 =
					new CombinatoricTools.FibSeq();
			for (int nextFib1 = fibSeq1.nextFib(); nextFib1 > 0;
					nextFib1 = fibSeq1.nextFib()) {
				if (nextFib1 > maxRepeatCount) {
					final String s = String.format(
							"Stage[%s], Pass#[%d]. Ran out of repeatCount[%d].",
							stage.name(), passNumber, nextFib1);
					if (solver != null) {
						solver.setCurrentPlus(lastPlus, s);
					}
					return lastPlus;
				}
				final int repeatCount = Math.max(nextFib0, nextFib1);
				/**
				 * If we don't have an automatic win, we'll settle for the
				 * following.
				 */
				PatternVariable pvToSettleFor = null;
				PvValuePerturber.PertType pertTypeToSettleFor = null;
				PvValueArrayPlus plusToSettleFor = null;
				int levelToSettleFor = Integer.MAX_VALUE;
				double scoreToSettleFor = Double.NEGATIVE_INFINITY;
				final int idxToSettleFor = -1;

				/** Pick a PV. */
				NEXT_PV: for (int idx = 0; idx < nPerturbables; ++idx) {
					final int grandOrd0 = perturbableGrandOrds[idx];
					final PvValue pvValue0 = lastPlus.getPvValue(grandOrd0);
					final PatternVariable pv0 = pvValue0.getPv();

					if (stage == RefinementStage.CLR_OVL) {
						/**
						 * If we already have a feasible, and have found something to
						 * settle for, skip this Pv.
						 */
						if (haveFeasibleNow && plusToSettleFor != null) {
							continue NEXT_PV;
						}
						final boolean pvIsVeryClear = lastPlus.pvIsVeryClear(pv0);
						if (pvIsVeryClear) {
							continue NEXT_PV;
						}
					}

					/** Never move an initial one. */
					final PvValuePerturber.PertType[] pertTypes;
					final PatternVariable pv = pvValue0.getPv();
					final boolean freezeThisOne = pvValue0 == pv.getInitialPvValue();
					if (freezeThisOne) {
						pertTypes = new PvValuePerturber.PertType[0];
					} else {
						pertTypes = getPertTypes(pvValue0, stage, shiftsAndTwistsOnly,
								haveFeasibleNow, haveCleanClrOvl);
					}

					final int nPertTypes = pertTypes.length;
					NEXT_PERTTYPE: for (int kPertType = 0; kPertType < nPertTypes;
							++kPertType) {
						if (solver != null && !solver.getKeepRefining()) {
							return lastPlus;
						}
						final PvValuePerturber.PertType pertType = pertTypes[kPertType];

						/** Perturb a PvValue and create a new PvValueArrayPlus. */
						/** We are not rough and ready if this is a zeroing in stage. */
						final boolean thisRoughAndReady =
								roughAndReady && !stage.forZeroingIn();
						final PvValue oldPvValue = lastPlus.getPvValue(grandOrd0);
						final PvValue newPvValue = PvValuePerturber.perturbPvValue(
								oldPvValue, pertType, thisRoughAndReady, repeatCount);
						if (newPvValue == oldPvValue) {
							continue;
						}

						/** Build a PvValueArrayPlus. */
						final PvValue[] sandboxOrig = lastPlus.getCopyOfPvValues();
						final PvValue[] sandboxMod =
								PvValuesFitter.fitPvValues(sandboxOrig, newPvValue,
										PvValuesFitter.FitType.SCALE_BOXES);
						final PvValue[] sandbox;
						if (sandboxMod == sandboxOrig) {
							/** Couldn't make the change, so there's nothing to do. */
							continue NEXT_PERTTYPE;
						}
						sandbox =
								solversManager.convertToIsActiveAndFrozens(sandboxMod);
						/**
						 * sandboxMod and sandbox should be the same unless something
						 * has changed. If that's the case, get the heck out of Dodge.
						 */
						for (int grandOrd1 = 0; grandOrd1 < nPttrnVbls; ++grandOrd1) {
							if (sandbox[grandOrd1] != sandboxMod[grandOrd1]) {
								return lastPlus;
							}
						}

						final PvValueArrayPlus sandboxPlus =
								new PvValueArrayPlus(planner, sandbox);
						if (stage._forPos) {
							final PvValueArrayPlus bestPlus = Solver.computeBestPlus(
									planner, posFunction, sandboxPlus, grandOrd0);
							/**
							 * For PRELIM, it is allowable to introduce constraint
							 * violation. Not so when zeroing in.
							 */
							if (stage.forZeroingIn() && !bestPlus.isFeasible()) {
								continue NEXT_PERTTYPE;
							}
							final double newPos = bestPlus.getPos(posFunction);
							final double lastPos = lastPlus.getPos(posFunction);
							if (newPos <= lastPos) {
								continue NEXT_PERTTYPE;
							}
							/** We have an improvement and wish to run with it. */
							lastPv = pv0;
							lastPertType = pertType;
							lastPlus = bestPlus;
							lastWinningIdx = idx;
							haveNewCurrentPlus = true;
							continue HAVE_NEW_PLUS;
						}

						/** Compare constraints. */
						final CtV[] lastCriticalForOptnCtVs =
								lastPlus.getCriticalForOptnCtVs(stage);
						final CtV[] newCriticalForOptnCtVs =
								sandboxPlus.getCriticalForOptnCtVs(stage);
						final int nCriticalForOptnCts = lastCriticalForOptnCtVs.length;

						/**
						 * We are clearing overlap. Find one that improves the best
						 * level that we can. The "level" of a Plus is the first ct that
						 * is improved.
						 */
						NEXT_LEVEL: for (int level = 0; level < nCriticalForOptnCts;
								++level) {
							if (level > levelToSettleFor) {
								continue NEXT_PERTTYPE;
							}
							final CtV lastCtV = lastCriticalForOptnCtVs[level];
							final CtV newCtV = newCriticalForOptnCtVs[level];
							final double lastRaw = lastCtV.getForOptnV();
							final double newRaw = newCtV.getForOptnV();
							if (newRaw > lastRaw) {
								continue NEXT_PERTTYPE;
							}
							if (newRaw == lastRaw) {
								/** Jury is still out on whether we wish to use pertType. */
								continue NEXT_LEVEL;
							}
							/**
							 * We are at least tied for levelToSettleFor and we improved
							 * the violation. Perhaps this is an automatic win.
							 */
							final PvValueArrayPlus newPlus = Solver.computeBestPlus(
									planner, posFunction, sandboxPlus, grandOrd0);
							if (shiftsAndTwistsOnly) {
								/**
								 * This is an automatic win; shiftsAndTwistsOnly indicates a
								 * desperation to clear overlap.
								 */
								lastPv = pv0;
								lastPertType = pertType;
								lastPlus = newPlus;
								lastWinningIdx = idx;
								haveNewCurrentPlus = true;
								continue HAVE_NEW_PLUS;
							}
							final double newPos = newPlus.getPos(posFunction);
							if (lastRaw > 0d) {
								final double lastPos = lastPlus.getPos(posFunction);
								final double posGain = newPos - lastPos;
								if (posGain <= 0d) {
									final double allowableCtImprv =
											lastRaw - Math.max(0d, newRaw);
									final double posLoss = -posGain;
									if (posLoss < allowableCtImprv) {
										/** Automatic win. */
										lastPv = pv0;
										lastPertType = pertType;
										lastPlus = newPlus;
										lastWinningIdx = idx;
										haveNewCurrentPlus = true;
										continue HAVE_NEW_PLUS;
									}
								}
							}
							final double newScore = newPos - newRaw;
							boolean newSettler = false;
							if (level < levelToSettleFor) {
								newSettler = true;
							} else {
								newSettler = newScore > scoreToSettleFor;
							}
							if (newSettler) {
								pvToSettleFor = pv0;
								pertTypeToSettleFor = pertType;
								levelToSettleFor = level;
								plusToSettleFor = newPlus;
								scoreToSettleFor = newScore;
							}
						}
					}
				}
				if (plusToSettleFor != null) {
					lastPv = pvToSettleFor;
					lastPertType = pertTypeToSettleFor;
					lastPlus = plusToSettleFor;
					lastWinningIdx = idxToSettleFor;
					haveNewCurrentPlus = true;
					continue HAVE_NEW_PLUS;
				}
			}
		}
	}

	private static PvValuePerturber.PertType[] getPertTypes(
			final PvValue oldPvValue, final RefinementStage stage,
			final boolean shiftsAndTwistsOnly, final boolean haveFeasible,
			final boolean haveCleanClrOvl) {
		final PatternVariable pv = oldPvValue.getPv();
		final PatternKind patternKind = pv.getPatternKind();
		final boolean isLp = patternKind.isPsCs();
		final boolean isVs = patternKind.isVs();
		final boolean isSs = patternKind.isSs();
		final ArrayList<PvValuePerturber.PertType> pertTypeList =
				new ArrayList<>();
		for (final PvValuePerturber.PertType pertType : PvValuePerturber.PertType._Values) {
			if (pertType == PvValuePerturber.PertType.DEC_HDG_90 ||
					pertType == PvValuePerturber.PertType.SUB_90_FROM_DIR_FIRST_LEG || pertType._sameBox || !pertType._standard) {
				/**
				 * We rotate when we decide on something anyway, so any sameBox is
				 * not necessary.
				 */
				continue;
			}
			if (stage._forPos) {
				/** Anything goes for this case, which is preLim or zeroing in. */
				if (isLp && !pertType._forVsOrSsOnly) {
					pertTypeList.add(pertType);
				}
				if (isVs && !pertType._changesShape &&
						(pertType != PertType.ADD_90_TO_DIR_FIRST_LEG &&
								pertType != PertType.SUB_90_FROM_DIR_FIRST_LEG)) {
					pertTypeList.add(pertType);
				}
				if (isSs) {
					if (!pertType._changesShape) {
						pertTypeList.add(pertType);
					}
					/**
					 * For SS, the only "shape changers" allowed are expand and
					 * contract.
					 */
					if (pertType == PvValuePerturber.PertType.EXPAND ||
							pertType == PvValuePerturber.PertType.CONTRACT) {
						pertTypeList.add(pertType);
					}
				}
				continue;
			}

			/** We're clearing overlap. */
			if (shiftsAndTwistsOnly) {
				if (pertType == PvValuePerturber.PertType.MOVE_ACROSS ||
						pertType == PvValuePerturber.PertType.MOVE_NEG_ACROSS) {
					pertTypeList.add(pertType);
					continue;
				}
				if (pertType == PvValuePerturber.PertType.MOVE_ALONG ||
						pertType == PvValuePerturber.PertType.MOVE_NEG_ALONG) {
					pertTypeList.add(pertType);
					continue;
				}
				if (pertType == PvValuePerturber.PertType.INC_HDG_FIRST_LEG ||
						pertType == PvValuePerturber.PertType.DEC_HDG_FIRST_LEG) {
					pertTypeList.add(pertType);
					continue;
				}
				continue;
			}

			/**
			 * We're clearing overlap; these are the only ones that might help.
			 */
			if (isLp && (pertType._shift || pertType._contract)) {
				pertTypeList.add(pertType);
				continue;
			}
			if (isVs) {
				pertTypeList.add(pertType);
				continue;
			}
			if (isSs && (pertType._shift ||
					pertType == PvValuePerturber.PertType.CONTRACT)) {
				pertTypeList.add(pertType);
				continue;
			}
		}

		final int n = pertTypeList.size();
		final PvValuePerturber.PertType[] pertTypeArray =
				pertTypeList.toArray(new PvValuePerturber.PertType[n]);
		return pertTypeArray;
	}

	private static String getStageInfoString(final Planner planner,
			final PosFunction posFunction, final RefinementStage stage,
			final int passNumber, final PvValueArrayPlus oldPlus,
			final PvValueArrayPlus newPlus, final PatternVariable pv,
			final PvValuePerturber.PertType pertType) {
		final PqSolver pqSolver = planner.getSolversManager().getPqSolver();
		final String lastJumpString = pqSolver.getLastJumpString();
		final String pvPertTypeString;
		if (passNumber == 0) {
			pvPertTypeString = "" + Constants._EmptySet;
		} else if (pv == null || pertType == null) {
			pvPertTypeString = "No Pv or noPertType";
		} else {
			pvPertTypeString =
					String.format("%s+%s", pv.getId(), pertType.name());
		}
		final double oldPos = oldPlus.getPos(posFunction);
		final CtV oldWorstCtV = oldPlus.getWorstForOptnCtV();
		final double newPos = newPlus.getPos(posFunction);
		final CtV newWorstCtV = newPlus.getWorstForOptnCtV();
		final String evalTypeName = posFunction._evalType.name();
		final String stageName = stage.name();
		final double posIncrease = newPos - oldPos;
		final double oldWorstCtValue =
				oldWorstCtV == null ? 0d : oldWorstCtV.getForOptnV();
		final char oldFbleChar = oldPlus.getFeasibleChar();
		final double newWorstCtValue =
				newWorstCtV == null ? 0d : newWorstCtV.getForOptnV();
		final char newFbleChar = newPlus.getFeasibleChar();
		final double ctV_Increase = newWorstCtValue - oldWorstCtValue;
		final String stageInfoString = String.format(
				"%s(%d)%s PvPertType:%s%s{%s PosInc[%f] CtImprvmnt[(%c→%c)%f]}", //
				stageName, passNumber, lastJumpString, pvPertTypeString,
				RefinementStage._StageInfoDelimiter, evalTypeName, posIncrease,
				oldFbleChar, newFbleChar, -ctV_Increase);
		return stageInfoString;
	}

	@SuppressWarnings("unused")
	private static String getPreStageInfoString(final PosFunction posFunction,
			final RefinementStage stage, final PvValueArrayPlus plus) {
		final double pos = plus.getPos(posFunction);
		final char fbleChar = plus.getFeasibleChar();
		final double worstForOptn = plus.getWorstForOptnCtV().getForOptnV();
		final String evalTypeName = posFunction._evalType.name();
		final String stageName = String.format("%s(**)", stage.name());
		final String stageInfoString =
				String.format("%s%s{%s Pos[%f] WorstV[%f%c]}", //
						stageName, RefinementStage._StageInfoDelimiter, evalTypeName,
						pos, worstForOptn, fbleChar);
		return stageInfoString;
	}

	@SuppressWarnings("unused")
	private static String getProgressString(final Planner planner,
			final PosFunction posFunction, final int kScore,
			final RefinementStage stage, final PvValueArrayPlus oldPlus,
			final PvValueArrayPlus newPlus, final PatternVariable pv,
			final PvValuePerturber.PertType pertType) {
		final StringBuilder stringBuilder = new StringBuilder();
		String s = null;
		try (final Formatter formatter =
				new Formatter(stringBuilder, Locale.US)) {
			final String stageName = stage.name();
			final double oldPos = oldPlus.getPos(posFunction);
			final double newPos = newPlus.getPos(posFunction);
			final PatternVariable nullPv = null;
			final double oldOvlV = oldPlus.getForOptnOvlV(nullPv);
			final double newOvlV = newPlus.getForOptnOvlV(nullPv);
			final double oldPvTrnstV = oldPlus.getForOptnPvTrnstV(nullPv);
			final double newPvTrnstV = newPlus.getForOptnPvTrnstV(nullPv);
			final double oldPvSeqTrnstV =
					oldPlus.getForOptnPvSeqTrnstV(/* pvSeq= */null);
			final double newPvSeqTrnstV =
					newPlus.getForOptnPvSeqTrnstV(/* pvSeq= */null);
			final String evalTypeString = posFunction._evalType._shortString;
			formatter.format("\n");
			formatter.format("kScore[" + kScore + ']');
			formatter.format(" " + stageName);
			formatter.format(" PV[" + pv.getDisplayName() + ']');
			final String s1 = String.format(" [%s]", pertType.name());
			formatter.format(s1);
			formatter.format("\n\t");
			formatter.format(" oldPos[%.4g]", oldPos);
			formatter.format(" deltaPos[%.4g]", newPos - oldPos);
			formatter.format(" oldOvlV[%.4g]", oldOvlV);
			formatter.format(" deltaOvlV[%.4g]", newOvlV - oldOvlV);
			formatter.format("\n\t");
			formatter.format(" oldPvTrnstV[%.4g]", oldPvTrnstV);
			formatter.format(" deltaPvTrnstV[%.4g]", newPvTrnstV - oldPvTrnstV);
			formatter.format(" oldPvSeqTrnstV[%.4g]", oldPvSeqTrnstV);
			formatter.format(" deltaSeqPvTrnstV[%.4g]",
					newPvSeqTrnstV - oldPvSeqTrnstV);
			formatter.format(" [" + evalTypeString + ']');
			s = formatter.toString();
		}
		return s;
	}
}
