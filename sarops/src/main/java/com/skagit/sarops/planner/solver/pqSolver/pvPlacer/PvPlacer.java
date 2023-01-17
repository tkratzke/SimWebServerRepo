package com.skagit.sarops.planner.solver.pqSolver.pvPlacer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.skagit.sarops.computePFail.DetectValues;
import com.skagit.sarops.planner.ParticlesManager;
import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.planner.posFunction.PosFunction;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.planner.posFunction.pFailsCache.PFailsCache;
import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.sarops.tracker.ParticleIndexes.ParticleIndexesState;
import com.skagit.sarops.tracker.lrcSet.LrcSet;
import com.skagit.util.IntDouble;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.navigation.LatLng3;

public class PvPlacer {

	/**
	 * <pre>
	 * Executive Decisions:
	 *     1. Number of headings to try between 0 and 180.
	 *     2. Within each heading:
	 *        a. The number of times to split into cells
	 *           in FixedAngleOptimizer
	 *        b. The number of cells to split the longer side into
	 *        c. The minimum number of cells to split the smaller side into.
	 * This is only used for non-PvSeq PVs.
	 * </pre>
	 */
	final private static int _NFixedAnglesToTry = 12;
	final protected static int _NTimesToSplit = 4;
	final protected static int _NCellsOnBigSide = 7;
	final protected static int _MinNCellsOnSmallSide = 3;

	/** The inputs. */
	final public PatternVariable _pv;
	final public long _cstRefSecs;
	final public int _searchDurationSecs;
	final public PvValue[] _knowns;
	/** The answer. */
	final public FixedAngleOptimizer _bestFixedAngleOptimizer;

	/** knowns is full-canonical. */
	public PvPlacer(final PosFunction ftPosFunction,
			final PosFunction nftPosFunction, final PatternVariable pv,
			final long cstRefSecs, final int searchDurationSecs,
			final PvValue[] knowns) {
		/** Backpointers and boilerplate. */
		_pv = pv;
		_cstRefSecs = cstRefSecs;
		_searchDurationSecs = searchDurationSecs;

		final PlannerModel plannerModel = _pv.getPlannerModel();
		final Planner planner = plannerModel.getPlanner();
		final ParticlesManager particlesManager = planner.getParticlesManager();
		_knowns = knowns.clone();

		/**
		 * <pre>
		 * To find prtclPlusS1, from which we will downsample, we use viz2
		 * particles wrt _pv; just those that are used for optimization.
		 * </pre>
		 */
		final ParticleIndexes[] ftPosFnPrtclsS =
				ftPosFunction.getParticleIndexesS();
		final double[] ftPosFnPriors = ftPosFunction.getPriors();
		final int nFtPosFnPrtclsS = ftPosFnPrtclsS.length;
		final HashMap<IntDouble, IntDouble> sweepWidthMap = new HashMap<>();
		final HashMap<Integer, LrcSet> vizLrcSets = _pv.getViz2LrcSets();
		for (final Map.Entry<Integer, LrcSet> entry : vizLrcSets.entrySet()) {
			final int objTp = entry.getKey();
			final LrcSet lrcSet = entry.getValue();
			if (lrcSet != null && !lrcSet.isBlind() && !lrcSet.isNearSighted()) {
				final double sweepWidth = lrcSet.getSweepWidth();
				if (sweepWidth > 0.0001) {
					final IntDouble intDouble = new IntDouble(objTp, sweepWidth);
					sweepWidthMap.put(intDouble, intDouble);
				}
			}
		}
		/**
		 * Collect the ParticleIndexes we want to compute Nft pFails for, and
		 * store their their priors in a parallel array. We will update these
		 * priors with the pFails from the known PvValues.
		 */
		final long estRefSecs = cstRefSecs + searchDurationSecs;
		final long midRefSecs = Math.round((cstRefSecs + estRefSecs) / 2);
		final ArrayList<ParticleIndexes> toComputeList = new ArrayList<>();
		final ArrayList<Integer> objTpsOfToComputeList = new ArrayList<>();
		final ArrayList<LatLng3> midLatLngOfToComputeList = new ArrayList<>();
		final ArrayList<Double> postKnownPriorsList = new ArrayList<>();
		for (int k = 0; k < nFtPosFnPrtclsS; ++k) {
			final ParticleIndexes prtclIndxs = ftPosFnPrtclsS[k];
			final ParticleIndexesState prtclIndxsMidState =
					particlesManager.computePrtclIndxsState(prtclIndxs, midRefSecs);
			final LatLng3 midLatLng = prtclIndxsMidState.getLatLng();
			final int objTp = prtclIndxsMidState.getObjectType();
			if (sweepWidthMap.get(new IntDouble(objTp)) != null) {
				toComputeList.add(prtclIndxs);
				objTpsOfToComputeList.add(objTp);
				postKnownPriorsList.add(ftPosFnPriors[k]);
				midLatLngOfToComputeList.add(midLatLng);
			}
		}
		/**
		 * Transfer toCompute to an array to make it easy to compute NFT pFails
		 * with pFailsCache.
		 */
		final int nToCompute = toComputeList.size();
		final ParticleIndexes[] toCompute = new ParticleIndexes[nToCompute];
		for (int k = 0; k < nToCompute; ++k) {
			toCompute[k] = toComputeList.get(k);
		}
		/**
		 * Transfer postKnownPriorsList to an array to make it convenient to
		 * update with NFT PFails.
		 */
		final double[] postKnownPriors = new double[nToCompute];
		for (int k = 0; k < nToCompute; ++k) {
			postKnownPriors[k] = postKnownPriorsList.get(k);
		}
		/**
		 * Use pFailsCache to compute the NFT PFails and also update
		 * postKnownPriors.
		 */
		final int nKnowns = _knowns.length;
		final PFailsCache pFailsCache = planner.getPFailsCache();
		for (int k0 = 0; k0 < nKnowns; ++k0) {
			final PvValue known = _knowns[k0];
			if (known == null) {
				continue;
			}
			final DetectValues[] detectValuesS =
					pFailsCache.getDetectValuesArray(planner, /* forOptnOnly= */true,
							DetectValues.PFailType.NFT, toCompute, 0, nToCompute, known);
			for (int k1 = 0; k1 < nToCompute; ++k1) {
				final double nftPFail = detectValuesS[k1]._nftPFail;
				postKnownPriors[k1] *= nftPFail;
			}
		}

		/**
		 * Create a list of ParticlePluses because that's what
		 * FixedAngleOptimizer requires.
		 */
		@SuppressWarnings("unused")
		final double normalizedWt =
				NumericalRoutines.normalizeWeights(postKnownPriors);
		final ArrayList<ParticlePlus> prtclPlusS = new ArrayList<>();
		double avgSw = 0d;
		for (int k = 0; k < nToCompute; ++k) {
			final ParticleIndexes prtclIndxs = toCompute[k];
			final double postKnownPrior = postKnownPriors[k];
			if (postKnownPrior > 0d) {
				final int objTp = objTpsOfToComputeList.get(k);
				final double sw = sweepWidthMap.get(new IntDouble(objTp))._d;
				final LatLng3 midLatLng = midLatLngOfToComputeList.get(k);
				final ParticlePlus particlePlus = new ParticlePlus(prtclIndxs,
						objTp, postKnownPriors[k], midLatLng);
				prtclPlusS.add(particlePlus);
				avgSw += sw * postKnownPriors[k];
			}
		}
		if (prtclPlusS.size() == 0) {
			_bestFixedAngleOptimizer = new FixedAngleOptimizer(pv);
			return;
		}

		/** Do the optimization. */
		FixedAngleOptimizer bestFixedAngleOptimizer = null;
		for (int k = 0; k < _NFixedAnglesToTry; ++k) {
			final double firstLegHdg = k * (180d / (_NFixedAnglesToTry - 1d));
			final FixedAngleOptimizer fixedAngleOptimizer =
					new FixedAngleOptimizer(pv, _cstRefSecs, _searchDurationSecs,
							_knowns, prtclPlusS, sweepWidthMap, firstLegHdg, avgSw);
			final double score = fixedAngleOptimizer._score;
			if (bestFixedAngleOptimizer == null ||
					score > bestFixedAngleOptimizer._score) {
				bestFixedAngleOptimizer = fixedAngleOptimizer;
			}
		}
		_bestFixedAngleOptimizer = bestFixedAngleOptimizer;
	}
}
