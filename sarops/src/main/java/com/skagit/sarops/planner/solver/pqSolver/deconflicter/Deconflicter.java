package com.skagit.sarops.planner.solver.pqSolver.deconflicter;

import java.util.ArrayList;
import java.util.Arrays;

import com.skagit.sarops.computePFail.DetectValues;
import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.plannerModel.PatternKind;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.planner.posFunction.PosFunction;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.planner.posFunction.pvValueArrayPlus.PvValueArrayPlus;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.util.NumericalRoutines;

public class Deconflicter {

	final public PvValueArrayPlus _inPlus;
	final public BirdsNestDetangler[] _birdsNestDetanglers;
	final public PvValueArrayPlus _outPlus;

	/**
	 * <pre>
	 * This is the main entry point for doing "gross" deconfliction. We:
	 * 1. Identify the Birds Nests.
	 * 2. For each Birds Nest, we detangle it.
	 * Note; we only try moving non-frozen LP PatternVariables.
	 * </pre>
	 */
	public Deconflicter(final Planner planner,
			final PosFunction ftPosFunction, final PosFunction nftPosFunction,
			final PvValueArrayPlus inPlus) {
		/** Boilerplate Constants. */
		_inPlus = inPlus;

		final SimCaseManager.SimCase simCase = planner.getSimCase();
		simCase.getSimGlobalStrings();
		final PlannerModel plannerModel = planner.getPlannerModel();
		final int nPttrnVbls = plannerModel.getNPttrnVbls();

		/** Compute the original BirdsNests. */
		final ArrayList<PvValue[]> origBirdsNests = inPlus.computeBirdsNests();
		final int nOrigBirdsNests = origBirdsNests.size();

		/**
		 * Compute the array of PvValues that we will not move; frozen, VS or
		 * SS, or in no BirdsNest.
		 */
		final PvValue[] notMoving = new PvValue[nPttrnVbls];
		Arrays.fill(notMoving, null);
		int nNotMoving = 0;
		int nFloaters = 0;
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PvValue pvValue = inPlus.getPvValue(grandOrd);
			if (pvValue == null || pvValue.onMars()) {
				continue;
			}
			final PatternVariable pv = pvValue.getPv();
			final PvValue userFrozenPvValue = pv.getUserFrozenPvValue();
			if (userFrozenPvValue != null) {
				notMoving[grandOrd] = pvValue;
				++nNotMoving;
				continue;
			}
			final PatternKind patternKind = pv.getPatternKind();
			if (patternKind.isSs() || patternKind.isVs() || (pvValue == pv.getInitialPvValue())) {
				/** We don't move it if it's an initial PvValue. */
				notMoving[grandOrd] = pvValue;
				++nNotMoving;
				continue;
			}
			/** If this is in no Birds Nest, it's not moving. */
			boolean inSomeBirdsNest = false;
			for (int k = 0; k < nOrigBirdsNests; ++k) {
				final PvValue[] pvValuesOfThisBirdsNest = origBirdsNests.get(k);
				if (pvValuesOfThisBirdsNest[grandOrd] != null) {
					inSomeBirdsNest = true;
					break;
				}
			}
			if (!inSomeBirdsNest) {
				notMoving[grandOrd] = pvValue;
				++nNotMoving;
				continue;
			}
			/** We can move it. */
			++nFloaters;
		}

		/** Collect the BirdsNests that have a movable Pv. */
		final ArrayList<PvValue[]> birdsNests = new ArrayList<>();
		final int nBirdsNests;
		NEXT_ORIG_BIRDSNEST: for (int j = 0; j < nOrigBirdsNests; ++j) {
			final PvValue[] origBirdsNest = origBirdsNests.get(j);
			for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
				final PvValue pvValue = origBirdsNest[grandOrd];
				if (pvValue == null) {
					continue;
				}
				if (notMoving[grandOrd] == null) {
					birdsNests.add(origBirdsNest);
					continue NEXT_ORIG_BIRDSNEST;
				}
			}
		}
		nBirdsNests = birdsNests.size();
		if (nBirdsNests == 0) {
			_birdsNestDetanglers = new BirdsNestDetangler[0];
			_outPlus = inPlus;
			return;
		}

		/** Get the initial data and factor in the pFails of notMoving. */
		final PosFunction posFunctionA = planner
				.createSmallSampleFunctionForDeconflicter(notMoving, nFloaters);
		final ParticleIndexes[] thesePrtclIndxsS =
				posFunctionA.getParticleIndexesS();
		final int nPrtclsA = thesePrtclIndxsS.length;
		final double[] priorsA = posFunctionA.getPriors().clone();
		final PosFunction.EvalType evalTypeA = posFunctionA._evalType;

		if (nNotMoving > 0) {
			final PvValue[] notMovingA = notMoving.clone();
			for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
				final PvValue pvValue = notMovingA[grandOrd];
				if (pvValue != null && pvValue.getPv().getPatternKind() == null) {
					/**
					 * This one is permanently frozen; we do not want to compute
					 * detectValues for it. They're already built into the priors for
					 * posFunctionA.
					 */
					notMovingA[grandOrd] = null;
				}
			}
			final PvValueArrayPlus notMovingAPlus =
					new PvValueArrayPlus(planner, notMovingA);
			final DetectValues[][] detectValues2Arrays =
					posFunctionA.getDetectValuesArrays(0, nPrtclsA, thesePrtclIndxsS,
							notMovingAPlus);
			for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
				if (notMovingA[grandOrd] == null) {
					continue;
				}
				final DetectValues[] detectValues2Array =
						detectValues2Arrays[grandOrd];
				for (int k = 0; k < nPrtclsA; ++k) {
					priorsA[k] *=
							detectValues2Array[k].getPFail(evalTypeA._pFailType);
				}
			}
		}

		/** Fill in outArray; start with notMoving. */
		final PvValue[] outArray = notMoving.clone();
		_birdsNestDetanglers = new BirdsNestDetangler[nBirdsNests];
		for (int k0 = 0; k0 < nBirdsNests; ++k0) {
			final PvValue[] birdsNest = birdsNests.get(k0);
			/**
			 * Find the pFails of everything that can move, but is yet to be
			 * processed.
			 */
			final PvValue[] laterPvValues = new PvValue[nPttrnVbls];
			Arrays.fill(laterPvValues, null);
			for (int k2 = k0 + 1; k2 < nBirdsNests; ++k2) {
				final PvValue[] laterBirdsNests = birdsNests.get(k2);
				for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
					final PvValue pvValue = laterBirdsNests[grandOrd];
					if (pvValue != null) {
						laterPvValues[grandOrd] = pvValue;
					}
				}
			}
			final PvValueArrayPlus latersPlus =
					new PvValueArrayPlus(planner, laterPvValues);
			final DetectValues[][] laterDetectValuesArrays = posFunctionA
					.getDetectValuesArrays(0, nPrtclsA, thesePrtclIndxsS, latersPlus);
			final double[] thesePriors = priorsA.clone();
			for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
				final PvValue pvValue = laterPvValues[grandOrd];
				if (pvValue == null) {
					continue;
				}
				final DetectValues[] laterDetectValuesArray =
						laterDetectValuesArrays[grandOrd];
				for (int k = 0; k < nPrtclsA; ++k) {
					thesePriors[k] *=
							laterDetectValuesArray[k].getPFail(evalTypeA._pFailType);
				}
			}
			NumericalRoutines.normalizeWeights(thesePriors);

			/** Detangle birdsNest, stash the results, and update priorsA. */
			final BirdsNestDetangler birdsNestDetangler = new BirdsNestDetangler(
					planner, ftPosFunction, nftPosFunction, _inPlus, birdsNest);

			_birdsNestDetanglers[k0] = birdsNestDetangler;
			final BearingResult bearingResult =
					birdsNestDetangler.getBestBearingResult();
			final PvValueArrayPlus bearingResultPlus = bearingResult._finalPlus;

			/** Update mainPriors from thisDeconflictedPlus. */
			final DetectValues[][] mainDetectValuesArrays =
					posFunctionA.getDetectValuesArrays(0, nPrtclsA, thesePrtclIndxsS,
							bearingResultPlus);
			for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
				final PvValue pvValue = bearingResultPlus.getPvValue(grandOrd);
				if (pvValue == null) {
					continue;
				}
				final DetectValues[] mainDetectValuesArray =
						mainDetectValuesArrays[grandOrd];
				for (int k = 0; k < nPrtclsA; ++k) {
					final double pFail =
							mainDetectValuesArray[k].getPFail(evalTypeA._pFailType);
					priorsA[k] *= pFail;
				}
			}
			/** Update outArray. */
			for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
				final PvValue pvValue = bearingResultPlus.getPvValue(grandOrd);
				if (pvValue == null) {
					continue;
				}
				outArray[grandOrd] = pvValue;
			}
		}
		if (planner.wasToldToSit()) {
			_outPlus = null;
			return;
		}
		_outPlus = new PvValueArrayPlus(planner, outArray);
	}
}
