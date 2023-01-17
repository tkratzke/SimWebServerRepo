package com.skagit.sarops.planner.solver;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

import com.skagit.sarops.planner.ParticlesManager;
import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.planner.plannerModel.pvSeq.PvSeq;
import com.skagit.sarops.planner.posFunction.PosFunction;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.CoverageToPodCurve;
import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.sarops.tracker.lrcSet.LrcSet;
import com.skagit.sarops.util.SimGlobalStrings;
import com.skagit.sarops.util.patternUtils.PatternUtilStatics;
import com.skagit.util.CombinatoricTools;
import com.skagit.util.MathX;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.TangentCylinder;

public class TheoreticalPos {
	final private static double _NmiToR = MathX._NmiToR;
	final private static double _MinimumDelta = 1d / 8d * _NmiToR;

	/** "Coverage" is unitless. Area and Aes are in sqNmi. */
	private static class Cell {
		private double _probability;
		private double _dPosDAesAt0;
		private double _allocatedAes;

		private Cell() {
			_probability = _dPosDAesAt0 = _allocatedAes = 0d;
		}
	}

	final private double _pos2;

	public TheoreticalPos(final Planner planner) {
		/**
		 * Set up the array of cells. We store an array as well as the matrix,
		 * because it's easier to update the matrix as we run through the
		 * particles, and we wish to sort the array.
		 */
		final SimCaseManager.SimCase simCase = planner.getSimCase();
		final SimGlobalStrings simGlobalStrings = simCase.getSimGlobalStrings();
		final int nGridDivisions =
				simGlobalStrings.getNGridDivisionsForTheoretical();
		final Cell[][] cellMatrix = new Cell[nGridDivisions][];
		final int nCells = nGridDivisions * nGridDivisions;
		final Cell[] cells = new Cell[nCells];
		for (int i = 0, k = 0; i < nGridDivisions; ++i) {
			cellMatrix[i] = new Cell[nGridDivisions];
			for (int j = 0; j < nGridDivisions; ++j) {
				cells[k++] = cellMatrix[i][j] = new Cell();
			}
		}
		final PlannerModel plannerModel = planner.getPlannerModel();
		final TangentCylinder tangentCylinder =
				planner.getSimModel().getTangentCylinder();
		/** Boilerplate unloading of variables. */
		final PosFunction posFunction =
				planner.getPosFunctionForTheoreticalMaxPos();
		final ParticleIndexes[] prtclIndxsS = posFunction.getParticleIndexesS();
		final double[] priors = posFunction.getPriors();
		final int nPrtcls = priors.length;
		/**
		 * Initialization: compute the specifications of the grid and the
		 * weights of the cells.
		 */
		double minX, minY, maxX, maxY;
		double deltaX, deltaY;
		minX = minY = Double.POSITIVE_INFINITY;
		maxX = maxY = Double.NEGATIVE_INFINITY;
		deltaX = deltaY = Double.NaN;
		double totalWeight1 = 0d;
		final long firstFencePost = planner.getEarliestCriticalRefSecs();
		final long lastFencePost = planner.getLatestCriticalRefSecs();
		final long[] refSecsS = CombinatoricTools.getFenceposts(firstFencePost,
				lastFencePost, ParticlesManager._NumberOfStages);
		for (int iPass = 0; iPass < 2; ++iPass) {
			if (iPass == 1) {
				deltaX = (maxX - minX) / nGridDivisions;
				deltaY = (maxY - minY) / nGridDivisions;
				/**
				 * We might have to adjust minX, maxX, and deltaX if they're too
				 * small.
				 */
				if (deltaX < _MinimumDelta) {
					final double centerX = (minX + maxX) / 2d;
					minX = centerX - _MinimumDelta * nGridDivisions / 2d;
					maxX = centerX + _MinimumDelta * nGridDivisions / 2d;
					deltaX = (maxX - minX) / nGridDivisions;
				}
				if (deltaY < _MinimumDelta) {
					final double centerY = (minY + maxY) / 2d;
					minY = centerY - _MinimumDelta * nGridDivisions / 2d;
					maxY = centerY + _MinimumDelta * nGridDivisions / 2d;
					deltaY = (maxY - minY) / nGridDivisions;
				}
			}
			final ParticlesManager particlesManager =
					planner.getParticlesManager();
			for (int iParticle = 0; iParticle < nPrtcls; ++iParticle) {
				final ParticleIndexes particle = prtclIndxsS[iParticle];
				double particleWeight = priors[iParticle];
				if (iPass == 0) {
					totalWeight1 += particleWeight;
				} else {
					if (totalWeight1 > 0d) {
						particleWeight /= totalWeight1;
					}
				}
				for (int iStage = 0; iStage < ParticlesManager._NumberOfStages;
						++iStage) {
					final ParticleIndexes.ParticleIndexesState prtclIndxsState =
							particle.refSecsToPrtclIndxsState(particlesManager,
									refSecsS[iStage]);
					final LatLng3 latLng = prtclIndxsState.getLatLng();
					final TangentCylinder.FlatLatLng flatLatLng =
							tangentCylinder.convertToMyFlatLatLng(latLng);
					final double x = flatLatLng.getEastOffset();
					final double y = flatLatLng.getNorthOffset();
					if (iPass == 0) {
						if (x > maxX) {
							maxX = x;
						}
						if (x < minX) {
							minX = x;
						}
						if (y > maxY) {
							maxY = y;
						}
						if (y < minY) {
							minY = y;
						}
						continue;
					}
					final int i = Math.min(nGridDivisions - 1,
							(int) Math.floor((x - minX) / deltaX));
					final int j = Math.min(nGridDivisions - 1,
							(int) Math.floor((y - minY) / deltaY));
					cellMatrix[i][j]._probability +=
							particleWeight / ParticlesManager._NumberOfStages;
				}
			}
		}
		final double nmiToRSq = _NmiToR * _NmiToR;
		final double cellArea = deltaX * deltaY / nmiToRSq;
		/** Set the total Aes. */
		final double totalAes;
		{
			double totalAesX = 0d;
			final ParticlesManager particlesManager =
					planner.getParticlesManager();
			for (final PatternVariable pv : plannerModel.getPttrnVbls()) {
				final double afterTransitsFullNmi;
				final PvSeq pvSeq = pv.getPvSeq();
				if (pvSeq == null) {
					afterTransitsFullNmi = pv.getRawSearchKts() *
							pv.getPvRawSearchDurationSecs() / 3600d;
				} else {
					final double searchHrs = pvSeq._totalDurationSecs / 3600d;
					final int n = pvSeq.getNMyPttrnVbls();
					if (n == 0) {
						continue;
					}
					afterTransitsFullNmi = pv.getRawSearchKts() * (searchHrs / n);
				}
				double aes = 0d;
				double totalWeight2 = 0d;
				final Map<Integer, LrcSet> objectTypeToLrcSet = pv.getViz2LrcSets();
				final double afterTransitsEffNmi = afterTransitsFullNmi *
						PatternUtilStatics._EffectiveSpeedReduction;
				for (final Map.Entry<Integer, LrcSet> entry : objectTypeToLrcSet
						.entrySet()) {
					final int objectType = entry.getKey().intValue();
					double weight = 0d;
					for (int iStage = 0; iStage < ParticlesManager._NumberOfStages;
							++iStage) {
						weight += particlesManager
								.objectTypeAndStageToWeight(objectType, iStage);
					}
					totalWeight2 += weight;
					final LrcSet lrcSet = entry.getValue();
					final double thisSweepWidth = lrcSet.getSweepWidth();
					aes += weight * thisSweepWidth * afterTransitsEffNmi;
				}
				if (totalWeight2 > 0d) {
					totalAesX += aes / totalWeight2;
				}
				totalAesX += aes / totalWeight2;
			}
			totalAes = totalAesX;
		}
		/** The rest of the ctor is setting the allocations. */
		final CoverageToPodCurve coverageToPodCurve =
				planner.getPlannerModel().getCoverageToPodCurve();
		/**
		 * Do a binary search for the target derivative; Either a cell gets no
		 * effort or we give it enough to bring its derivative down to the
		 * target derivative. The goal is to find the derivative's value that
		 * uses up exactly aes.
		 */
		for (int i = 0; i < nCells; ++i) {
			final double cellProbability = cells[i]._probability;
			if (cellProbability == 0d) {
				cells[i]._dPosDAesAt0 = 0d;
			} else {
				final double pod =
						coverageToPodCurve.derivativeOfPodWrtCoverage(0d);
				cells[i]._dPosDAesAt0 = cellProbability * pod / cellArea;
			}
		}
		/**
		 * Sort in descending order of probability so that the first cell will
		 * have the highest derivative of pos wrt aes.
		 */
		Arrays.sort(cells, new Comparator<Cell>() {
			@Override
			public int compare(final Cell cell1, final Cell cell2) {
				return Double.compare(cell2._probability, cell1._probability);
			}
		});
		double derivativeHigh = cells[0]._dPosDAesAt0;
		double derivativeLow = 0;
		boolean alreadyAllocated = false;
		BinarySearchLoop: while (derivativeLow < 0.99 * derivativeHigh) {
			final double targetDerivativeWrtAes =
					(derivativeLow + derivativeHigh) / 2;
			double thisTotalAes = 0;
			double pos2 = 0;
			int lastCell = 0;
			for (; lastCell < nCells; ++lastCell) {
				/**
				 * If this cell's derivative is already less than the target
				 * derivative, we can't push this cell's derivative down by adding
				 * effort. Hence we give it zero.
				 */
				final Cell cell = cells[lastCell];
				final double cellProbability = cell._probability;
				if (cell._dPosDAesAt0 < targetDerivativeWrtAes) {
					cell._allocatedAes = 0d;
					continue;
				}
				/**
				 * Compute how much aes we need to pour into this cell to bring its
				 * derivative down to the target.
				 */
				final double coverage =
						coverageToPodCurve.inverseOfDerivativeOfPodWrtCoverage(
								targetDerivativeWrtAes / cellProbability * cellArea);
				final double cellAes = coverage * cellArea;
				thisTotalAes += cellAes;
				cell._allocatedAes = cellAes;
				/**
				 * If we ran out of effort before we could bring this down, then the
				 * target derivative is too low, and we want to continue in the
				 * Outside loop.
				 */
				if (thisTotalAes > totalAes) {
					derivativeLow = targetDerivativeWrtAes;
					continue BinarySearchLoop;
				}
				pos2 +=
						cellProbability * coverageToPodCurve.coverageToPod(coverage);
				/**
				 * If the probability of success is now essentially 1d, simply scale
				 * the effort assigned so far, and set the other allocations to 0.
				 */
				if (pos2 >= 0.99) {
					final double scale = totalAes / thisTotalAes;
					for (int i = 0; i < nCells; ++i) {
						final Cell cell2 = cells[i];
						cell2._allocatedAes =
								i > lastCell ? 0 : cell2._allocatedAes * scale;
					}
					alreadyAllocated = true;
					break BinarySearchLoop;
				}
			}
			/**
			 * We ran through all of the cells. Unless we used up exactly the aes
			 * given to us, lower the target derivative and try again. In the
			 * following, ">=" really means "==."
			 */
			derivativeHigh = targetDerivativeWrtAes;
			if (thisTotalAes >= totalAes) {
				break;
			}
		}
		/** Allocate effort to cells. */
		if (!alreadyAllocated) {
			/** Allocate as per derivativeHigh. */
			for (int i = 0; i < nCells; ++i) {
				final Cell cell = cells[i];
				if (cell._dPosDAesAt0 > derivativeHigh) {
					final double cellProbability = cell._probability;
					final double coverage =
							coverageToPodCurve.inverseOfDerivativeOfPodWrtCoverage(
									derivativeHigh / cellProbability * cellArea);
					final double cellAes = coverage * cellArea;
					cell._allocatedAes = cellAes;
				} else {
					cell._allocatedAes = 0d;
				}
			}
		}
		/** Compute the pos. */
		double pos2 = 0;
		for (int i = 0; i < nCells; ++i) {
			final Cell cell = cells[i];
			final double cellAes = cell._allocatedAes;
			final double coverage = cellAes / cellArea;
			final double pod = coverageToPodCurve.coverageToPod(coverage);
			pos2 += cell._probability * pod;
		}
		_pos2 = pos2;
	}

	public double getPos2() {
		return _pos2;
	}
}
