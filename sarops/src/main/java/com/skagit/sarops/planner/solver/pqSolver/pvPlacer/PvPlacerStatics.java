package com.skagit.sarops.planner.solver.pqSolver.pvPlacer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;

import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.planner.posFunction.PosFunction;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.planner.posFunction.pvValueArrayPlus.PvValueArrayPlus;
import com.skagit.sarops.planner.solver.pqSolver.PqSolver;
import com.skagit.sarops.planner.solver.pqSolver.deconflicter.Deconflicter;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.simCaseManager.SimCaseManager.SimCase;
import com.skagit.util.CombinatoricTools;
import com.skagit.util.PermutationTools;
import com.skagit.util.randomx.Randomx;

public class PvPlacerStatics {
	final private static boolean _UseRandom = true;

	public static PvValue jumpSinglePv(final PvValue[] knowns,
			final PatternVariable pv, final long cstRefSecs,
			final int searchDurationSecs) {
		final PlannerModel plannerModel = pv.getPlannerModel();
		final Planner planner = plannerModel.getPlanner();
		final PosFunction ftPosFunction = planner.getPosFunctionForFbleOptn();
		final PosFunction nftPosFunction = planner.getPosFunctionForInfblOptn();
		final ArrayList<PvPlacer> pvPlacers = new ArrayList<>();
		final PvPlacer pvPlacer = new PvPlacer(ftPosFunction, nftPosFunction,
				pv, cstRefSecs, searchDurationSecs, knowns);
		pvPlacers.add(pvPlacer);
		planner.updateFirstPvPlacers(pvPlacers);
		final PvValue newPvValue =
				pvPlacer._bestFixedAngleOptimizer._bestPvValue;
		return newPvValue;
	}

	public static Deconflicter nextPlus(final Planner planner,
			final PosFunction ftPosFunction, final PosFunction nftPosFunction,
			final PvValueArrayPlus inPlus) {
		final PlannerModel plannerModel = planner.getPlannerModel();
		boolean haveActiveFloater = false;
		final int nPttrnVbls = plannerModel.getNPttrnVbls();
		for (final PatternVariable pv : plannerModel.getPttrnVbls()) {
			final boolean isActive = pv.isActive();
			final boolean isUserFrozen = pv.getUserFrozenPvValue() != null;
			if (isActive && !isUserFrozen) {
				haveActiveFloater = true;
				break;
			}
		}
		if (!haveActiveFloater) {
			/** If no floater is active, the answer is pretty obvious. */
			final PvValue[] pvValues = new PvValue[nPttrnVbls];
			for (final PatternVariable pv : plannerModel.getPttrnVbls()) {
				final int grandOrd = pv.getGrandOrd();
				if (pv.isActive()) {
					pvValues[grandOrd] = pv.getUserFrozenPvValue();
				}
			}
			final PvValueArrayPlus plus = new PvValueArrayPlus(planner, pvValues);
			final Deconflicter deconflicter =
					new Deconflicter(planner, ftPosFunction, nftPosFunction, plus);
			return deconflicter;
		}
		final PvValueArrayPlus newPlus;
		if (planner.isValidAsPerCurrentActive(inPlus)) {
			newPlus = makeShufflingQMove(planner, ftPosFunction, nftPosFunction,
					inPlus);
		} else {
			/** Make one from scratch. */
			newPlus = makeFromScratch(planner, ftPosFunction, nftPosFunction);
		}

		final PvValueArrayPlus adjustedPlus =
				adjustToActive(newPlus, ftPosFunction, nftPosFunction);
		final Deconflicter deconflicter = new Deconflicter(planner,
				ftPosFunction, nftPosFunction, adjustedPlus);
		planner.updateDeconfliction(deconflicter);
		return deconflicter;
	}

	public static PvValueArrayPlus adjustToActive(final PvValueArrayPlus plus,
			final PosFunction ftPosFunction, final PosFunction nftPosFunction) {
		if (plus == null) {
			return null;
		}
		final Planner planner = plus.getPlanner();
		if (planner == null) {
			return null;
		}
		final PlannerModel plannerModel = planner.getPlannerModel();
		final int nPttrnVbls = plannerModel.getNPttrnVbls();
		/** The input might be just fine. */
		boolean needChange = false;
		final PatternVariable[] activeSet = plannerModel.getActiveSet();
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PvValue pvValue = plus.getPvValue(grandOrd);
			if ((pvValue != null) != (activeSet[grandOrd] != null)) {
				needChange = true;
				break;
			}
			if (pvValue == null || pvValue.onMars()) {
				continue;
			}
			final PatternVariable pv = pvValue.getPv();
			final PvValue userFrozenPvValue = pv.getUserFrozenPvValue();
			if (userFrozenPvValue != null && userFrozenPvValue != pvValue) {
				needChange = true;
				break;
			}
		}
		if (!needChange) {
			return plus;
		}
		/**
		 * If we are starting with all nulls for the Active PttrnVbls, then use
		 * the initial configurations.
		 */
		boolean useInitialConfigurations = true;
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PvValue pvValue = plus.getPvValue(grandOrd);
			if (activeSet[grandOrd] != null && pvValue != null) {
				useInitialConfigurations = false;
				break;
			}
		}
		/**
		 * The following creates a Plus from what should be in plus' full array
		 * of PvValues.
		 */
		final PvValueArrayPlus convertedPlus =
				new PvValueArrayPlus(planner, plus.getCopyOfPvValues());
		/**
		 * Build the list of PttrnVbls that we're solving for, and the ones that
		 * are fixed.
		 */
		final PvValue[] knowns = new PvValue[nPttrnVbls];
		Arrays.fill(knowns, null);
		final PatternVariable[] toSolveFor = new PatternVariable[nPttrnVbls];
		Arrays.fill(toSolveFor, null);
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PatternVariable pv = plannerModel.grandOrdToPv(grandOrd);
			if (!pv.isActive()) {
				continue;
			}
			/**
			 * It's Active. If we're using InitialConfigurations, and this Pv has
			 * one, it's known.
			 */
			final PvValue known;
			if (useInitialConfigurations) {
				final PvValue initialPvValue = pv.getInitialPvValue();
				if (initialPvValue != null) {
					known = initialPvValue;
				} else {
					known = null;
				}
			} else {
				known = convertedPlus.getPvValue(grandOrd);
			}
			if (known != null) {
				knowns[grandOrd] = known;
			} else if (pv.isActive()) {
				toSolveFor[grandOrd] = pv;
			}
		}
		final PvValueArrayPlus newPlus = theBigHammer(planner, knowns,
				toSolveFor, ftPosFunction, nftPosFunction);
		return newPlus;
	}

	private static PvValueArrayPlus makeFromScratch(final Planner planner,
			final PosFunction ftPosFunction, final PosFunction nftPosFunction) {
		final PlannerModel plannerModel = planner.getPlannerModel();
		final int nPttrnVbls = plannerModel.getNPttrnVbls();
		final PqSolver pqSolver = planner.getSolversManager().getPqSolver();
		final boolean freezeInitialPvValues = pqSolver.freezeInitialPvValues();
		final PvValue[] knowns = new PvValue[nPttrnVbls];
		Arrays.fill(knowns, null);
		final PatternVariable[] toSolveFor = new PatternVariable[nPttrnVbls];
		Arrays.fill(toSolveFor, null);
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PatternVariable pv = plannerModel.grandOrdToPv(grandOrd);
			if (!pv.isActive()) {
				continue;
			}
			final PvValue userFrozenPvValue = pv.getUserFrozenPvValue();
			if (userFrozenPvValue != null) {
				knowns[grandOrd] = userFrozenPvValue;
			} else {
				final PvValue initialPvValue = pv.getInitialPvValue();
				if (freezeInitialPvValues &&
						(initialPvValue != null && !initialPvValue.onMars())) {
					knowns[grandOrd] = initialPvValue;
				} else {
					toSolveFor[grandOrd] = pv;
				}
			}
		}
		/** Converts toSolveFor to knowns. */
		final PvValueArrayPlus newPlus = theBigHammer(planner, knowns,
				toSolveFor, ftPosFunction, nftPosFunction);
		return newPlus;
	}

	/**
	 * A Q move moves them all, but starts with plusIn, and moves one at a
	 * time. First, it finds the best one to move, moves it, then freezes it
	 * and finds the best to move, and moves it. Etc. We only shuffle
	 * stand-alones.
	 */
	private static PvValueArrayPlus makeShufflingQMove(final Planner planner,
			final PosFunction ftPosFunction, final PosFunction nftPosFunction,
			final PvValueArrayPlus plusIn) {
		if (planner == null) {
			return null;
		}
		final PlannerModel plannerModel = planner.getPlannerModel();
		final int nPttrnVbls = plannerModel.getNPttrnVbls();
		boolean allNull = true;
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PvValue inputPvValue = plusIn.getPvValue(grandOrd);
			if (inputPvValue != null) {
				allNull = false;
				break;
			}
		}
		if (allNull) {
			return null;
		}
		final PvValueArrayPlus activePlus =
				adjustToActive(plusIn, ftPosFunction, nftPosFunction);

		final PvValue[] knowns = activePlus.getCopyOfPvValues();
		/**
		 * Use a BitSet to track the ones that have been placed. We treat
		 * non-stand-alones as "placed."
		 */
		final BitSet knownGrandOrds = new BitSet(nPttrnVbls);
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PvValue known = knowns[grandOrd];
			final PatternVariable pv = known == null ? null : known.getPv();
			if (pv == null || pv.getUserFrozenPvValue() != null ||
					pv.getPvSeq() == null) {
				knownGrandOrds.set(grandOrd);
			}
		}
		final int nToSolveFor = nPttrnVbls - knownGrandOrds.cardinality();

		/** We keep thesePvPlacers for Ui considerations only. */
		final ArrayList<PvPlacer> thesePvPlacers = new ArrayList<>();

		/**
		 * In each pass, find the strongest wrt the ones that have already been
		 * placed and place that one.
		 */
		for (int k0 = 0; k0 < nToSolveFor; ++k0) {
			/** Gather the "eligibles." */
			final ArrayList<PvValue> eligibles = new ArrayList<>();
			for (int grandOrd1 = 0; grandOrd1 < nPttrnVbls; ++grandOrd1) {
				if (knownGrandOrds.get(grandOrd1)) {
					continue;
				}
				final PvValue pvValue = knowns[grandOrd1];
				final PatternVariable pv = pvValue.getPv();
				if (pv.isActive()) {
					eligibles.add(pvValue);
				}
			}
			final int nEligibles = eligibles.size();

			/** Find an eligible one to place. */
			double bestPos = -1d;
			PvPlacer bestPvPlacer = null;
			PvValue[] bestPvValues = null;
			if (_UseRandom) {
				/**
				 * Permute things a little differently than when we solve for the
				 * standalones.
				 */
				final Randomx r = new Randomx(plannerModel.getLatestSeed());
				final int k = r.nextInt(nEligibles);
				final PvValue pvValue0 = eligibles.get(k);
				final PatternVariable pv = pvValue0.getPv();
				final long cstRefSecs = pvValue0.getCstRefSecs();
				final int searchDurationSecs = pvValue0.getSearchDurationSecs();
				bestPvPlacer = new PvPlacer(ftPosFunction, nftPosFunction, pv,
						cstRefSecs, searchDurationSecs, knowns);
				final PvValue pvValue =
						bestPvPlacer._bestFixedAngleOptimizer._bestPvValue;
				bestPvValues = knowns.clone();
				bestPvValues[pv.getGrandOrd()] = pvValue;
			} else {
				for (int k1 = 0; k1 < nEligibles; ++k1) {
					final PvValue pvValue0 = eligibles.get(k1);
					final PatternVariable pv = pvValue0.getPv();
					final long cstRefSsecs = pvValue0.getCstRefSecs();
					final int searchDurationSecs = pvValue0.getSearchDurationSecs();
					final PvPlacer pvPlacer = new PvPlacer(ftPosFunction,
							nftPosFunction, pv, cstRefSsecs, searchDurationSecs, knowns);
					final PvValue pvValue =
							pvPlacer._bestFixedAngleOptimizer._bestPvValue;
					/** Evaluate this proposal. */
					final PvValue[] tempPvValues = knowns.clone();
					tempPvValues[pv.getGrandOrd()] = pvValue;
					final PvValueArrayPlus thisPlus =
							new PvValueArrayPlus(planner, tempPvValues);
					final double thisPos = thisPlus.getPos(ftPosFunction);
					if (thisPos > bestPos) {
						bestPos = thisPos;
						bestPvPlacer = pvPlacer;
						bestPvValues = tempPvValues;
					}
				}
			}
			assert bestPvPlacer != null : "Couldn't find a PvPlacer?";
			thesePvPlacers.add(bestPvPlacer);
			System.arraycopy(bestPvValues, 0, knowns, 0, nPttrnVbls);
			knownGrandOrds.set(bestPvPlacer._pv.getGrandOrd());
		}
		planner.updateFirstPvPlacers(thesePvPlacers);

		final PvValueArrayPlus newPlus = new PvValueArrayPlus(planner, knowns);
		return newPlus;
	}

	/**
	 * The real hammer for building from scratch and fleshing out a plus that
	 * is missing some actives.
	 */
	private static PvValueArrayPlus theBigHammer(final Planner planner,
			final PvValue[] knowns, final PatternVariable[] toSolveFor,
			final PosFunction ftPosFunction, final PosFunction nftPosFunction) {
		final PlannerModel plannerModel = planner.getPlannerModel();
		final SimCase simCase = planner.getSimCase();
		final int nPttrnVbls = plannerModel.getNPttrnVbls();

		/**
		 * Store the grandOrds of the stand-alones we must solve for, so that we
		 * can permute them.
		 */
		int[] standAloneGrandOrdsToSolveFor = null;
		for (int iPass = 0; iPass < 2; ++iPass) {
			int nStandAlonesToSolveFor = 0;
			for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
				final PatternVariable pv = plannerModel.grandOrdToPv(grandOrd);
				if (pv.getPvSeq() == null && toSolveFor[grandOrd] != null) {
					/** pv is a stand-alone that we must solve for. */
					if (iPass == 1) {
						standAloneGrandOrdsToSolveFor[nStandAlonesToSolveFor] =
								grandOrd;
					}
					++nStandAlonesToSolveFor;
				}
			}
			if (iPass == 0) {
				standAloneGrandOrdsToSolveFor = new int[nStandAlonesToSolveFor];
			}
		}

		/**
		 * Solve for the Stand-alones, which is all we do at this point. We
		 * should be solving for the PvSeq PV's first, but we're not ready for
		 * that.
		 */
		final int nStandAlonesToSolveFor = standAloneGrandOrdsToSolveFor.length;
		/** Permute a little differently than when we placed them originally. */
		final Randomx r =
				new Randomx(plannerModel.getLatestSeed(), /* nToAdvance= */5);
		PermutationTools.permute(standAloneGrandOrdsToSolveFor, r);
		/** Bubble the VS and SS to the top. */
		for (int k = 0, nDone = 0; k < nStandAlonesToSolveFor; ++k) {
			final int grandOrd = standAloneGrandOrdsToSolveFor[k];
			final PatternVariable pv = plannerModel.grandOrdToPv(grandOrd);
			final boolean goesToFront =
					pv.getPatternKind().isVs() || pv.getPatternKind().isSs();
			if (goesToFront) {
				final int grandOrd2 = standAloneGrandOrdsToSolveFor[nDone];
				standAloneGrandOrdsToSolveFor[nDone++] = grandOrd;
				standAloneGrandOrdsToSolveFor[k] = grandOrd2;
			}
		}

		final PvPlacer[] standAlonePvPlacers =
				new PvPlacer[nStandAlonesToSolveFor];
		Arrays.fill(standAlonePvPlacers, null);
		for (int k = 0; k < nStandAlonesToSolveFor; ++k) {
			final int grandOrdToSolve = standAloneGrandOrdsToSolveFor[k];
			final PatternVariable pv = plannerModel.grandOrdToPv(grandOrdToSolve);
			/** Get a pvPlacer for pv. */
			final String s = String.format(
					"Phase 1 of init: Starting %d of %d (grandOrd[%d]).; PttrnVbl[%s/%s]",
					k + 1, nStandAlonesToSolveFor, grandOrdToSolve, pv.getName(),
					pv.getId());
			SimCaseManager.out(simCase, s);
			final long cstRefSecs = pv.getPvCstRefSecs();
			final int searchDurationSecs = pv.getPvRawSearchDurationSecs();
			final PvPlacer pvPlacer = new PvPlacer(ftPosFunction, nftPosFunction,
					pv, cstRefSecs, searchDurationSecs, knowns);
			standAlonePvPlacers[k] = pvPlacer;
			final PvValue pvValue =
					pvPlacer._bestFixedAngleOptimizer._bestPvValue;
			/** Add this PvValue to knowns. */
			knowns[grandOrdToSolve] = pvValue;
		}

		/** Now scramble these stand-alones, looking for the best result. */
		PvValueArrayPlus bestPlus = new PvValueArrayPlus(planner, knowns);
		final int nPairs = nStandAlonesToSolveFor / 2;
		final PosFunction posFunction = planner.getPosFunctionForInfblOptn();
		double bestPos = bestPlus.getPos(posFunction);
		final PvValue[] sandbox = bestPlus.getCopyOfPvValues();
		for (int kPair = 0; kPair < nPairs; ++kPair) {
			/** Choose a pair. */
			final int nPairsLeft = nPairs - kPair;
			final int k1 = kPair + r.nextInt(nPairsLeft);
			final int k2 = nStandAlonesToSolveFor - 1 - k1;
			final String s = String.format(
					"Phase 2 of init: StartingPair[%d of %d], switching %d and %d.", //
					kPair + 1, nPairs, //
					k1, k2);
			SimCaseManager.out(simCase, s);
			final PvPlacer oldPvPlacer1 = standAlonePvPlacers[k1];
			final PvPlacer oldPvPlacer2 = standAlonePvPlacers[k2];
			final PatternVariable pv1 = oldPvPlacer1._pv;
			final PatternVariable pv2 = oldPvPlacer2._pv;
			final int grandOrd1 = pv1.getGrandOrd();
			final int grandOrd2 = pv2.getGrandOrd();
			final PvValueArrayPlus plus;
			sandbox[grandOrd1] = sandbox[grandOrd2] = null;
			/** Put "2" back and then "1." */
			final long cstRefSecs2 = pv2.getPvCstRefSecs();
			final int searchDurationSecs2 = pv2.getPvRawSearchDurationSecs();
			final PvPlacer pvPlacer2 = new PvPlacer(ftPosFunction, nftPosFunction,
					pv2, cstRefSecs2, searchDurationSecs2, sandbox);
			final PvValue pvValue2 =
					pvPlacer2._bestFixedAngleOptimizer._bestPvValue;
			sandbox[grandOrd2] = pvValue2;
			final long cstRefSecs1 = pv1.getPvCstRefSecs();
			final int searchDurationSecs1 = pv1.getPvRawSearchDurationSecs();
			final PvPlacer pvPlacer1 = new PvPlacer(ftPosFunction, nftPosFunction,
					pv1, cstRefSecs1, searchDurationSecs1, sandbox);
			final PvValue pvValue1 =
					pvPlacer1._bestFixedAngleOptimizer._bestPvValue;
			sandbox[grandOrd1] = pvValue1;
			plus = new PvValueArrayPlus(planner, sandbox);
			final double pos = plus.getPos(posFunction);
			if (pos > bestPos) {
				bestPos = pos;
				bestPlus = plus;
				standAlonePvPlacers[k1] = pvPlacer1;
				standAlonePvPlacers[k2] = pvPlacer2;
			}
		}
		return bestPlus;
	}

	/** Test our "bubble special to front." */
	public static void main(final String[] args) {
		final int[] intsToSort = { 1, 3, 2, 7, 4, 6, 9 };
		final int nIntsToSort = intsToSort.length;
		for (int k = 0, nDone = 0; k < nIntsToSort; ++k) {
			final int value = intsToSort[k];
			final boolean goesToFront = value % 2 == 0;
			if (goesToFront) {
				final int value2 = intsToSort[nDone];
				intsToSort[nDone++] = value;
				intsToSort[k] = value2;
			}
		}
		System.out.printf("\nOrder: %s",
				CombinatoricTools.getString(intsToSort));
	}
}
