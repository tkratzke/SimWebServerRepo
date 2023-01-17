package com.skagit.sarops.planner.solver.pqSolver.deconflicter;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import com.skagit.sarops.planner.ParticlesManager;
import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.plannerModel.PatternKind;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.sarops.tracker.ParticleIndexes.ParticleIndexesState;
import com.skagit.sarops.tracker.lrcSet.LrcSet;
import com.skagit.sarops.util.patternUtils.PatternUtilStatics;
import com.skagit.util.CombinatoricTools;
import com.skagit.util.MathX;
import com.skagit.util.navigation.LatLng3;

/** Used in BirdsNestDetangler. */
class Accordion2 {
	final private static double _NmiToR = MathX._NmiToR;
	final public static int _NStages = 3;
	final static double _NominalSqNmiPerCell = 1d;
	final static int _MinNCellsInDimension = 3;
	final static int _MaxNCellsInDimension = 25;

	final private int[] _objectTypes;
	final private double[] _sweepWidths;
	final private double _deltaXNmi;
	final private double _deltaYNmi;

	final public double _winningHeight;
	final public double _winningWidth;
	final public LatLng3 _winningCenter;
	final public double[] _winningTwXy;

	public class Accordion2Cell {
		final int _iX, _iY;
		final double[] _objectTypeWeights;

		public Accordion2Cell(final int iX, final int iY) {
			final int nObjectTypes = Accordion2.this._objectTypes.length;
			_objectTypeWeights = new double[nObjectTypes];
			for (int objTpIdx = 0; objTpIdx < nObjectTypes; ++objTpIdx) {
				_objectTypeWeights[objTpIdx] = 0d;
			}
			_iX = iX;
			_iY = iY;
		}
	}

	final private Accordion2Cell[][] _grid;

	final private static int[][] _LpAdjs;
	final private static int[][] _VsAdjs;
	final private static int[][] _SsAdjs;
	final private static int[] _LpExpanders = { 0, 1, 4, 5 };
	final private static int[] _SsExpanders = { 8, 9, 10, 11 };
	static {
		/**
		 * <pre>
		 * _LpAdjs is the adjustments to a cellArray for LP.
		 * A cellArray is an array of length 4 that is:
		 * {leftCol, lowRow, nCols, nRows}.
		 * The following 8 arrays do the following:
		 * 0. Increase #Clmns by decreasing left boundary.
		 * 1. Increase #Clmns by increasing nCols.
		 * 2. Decrease #Clmns by increasing left boundary.
		 * 3. Decrease #Clmns by decreasing nCols.
		 * 4. Increase #Rows by decreasing low boundary.
		 * 5. Increase #Rows by increasing nRows.
		 * 6. Decrease #Rows by increasing low boundary.
		 * 7. Decrease #Rows by decreasing nRows.
		 * </pre>
		 */
		_LpAdjs = new int[8][];
		final int nLpAdjs = _LpAdjs.length;
		for (int iAdj = 0; iAdj < nLpAdjs; ++iAdj) {
			final int[] adj = _LpAdjs[iAdj] = new int[4];
			if (iAdj < 4) {
				/** Adjust columns; leave rows alone. */
				adj[1] = adj[3] = 0;
				/** Adjust nCols. */
				adj[2] = iAdj < 2 ? 1 : -1;
				if (iAdj == 0) {
					adj[0] = -1;
				} else if (iAdj == 2) {
					adj[0] = 1;
				} else {
					adj[0] = 0;
				}
				continue;
			}
			/** Adjust rows; leave columns alone. */
			adj[0] = adj[2] = 0;
			/** Adjust nRows. */
			adj[3] = iAdj < 6 ? 1 : -1;
			if (iAdj == 4) {
				adj[1] = -1;
			} else if (iAdj == 6) {
				adj[1] = 1;
			} else {
				adj[1] = 0;
			}
		}
		/**
		 * <pre>
		 * _VsAdjs is the adjustments to a cellArray for a VS.
		 * A cellArray is an array of length 4 that is:
		 * {leftCol, lowRow, nCols, nRows}.
		 * 0. Shift cells N.
		 * 1. Shift cells NE.
		 * 2. Shift cells E.
		 * 3. Shift cells SE.
		 * 4. Shift cells S.
		 * 5. Shift cells SW.
		 * 6. Shift cells W.
		 * 7. Shift cells NW.
		 * </pre>
		 */
		_VsAdjs = new int[8][];
		final int nVsAdjs = _VsAdjs.length;
		for (int iAdj = 0; iAdj < nVsAdjs; ++iAdj) {
			final int[] adj = _VsAdjs[iAdj] = new int[4];
			/** We never adjust nClmns or nRows. */
			adj[2] = adj[3] = 0;
			/** Adjust the columns. */
			if (iAdj == 0 || iAdj == 4) {
				adj[0] = 0;
			} else if (iAdj < 4) {
				adj[0] = 1;
			} else {
				adj[0] = -1;
			}
			/** Adjust the rows. */
			if (iAdj == 2 || iAdj == 6) {
				adj[1] = 0;
			} else if (iAdj == 0 || iAdj == 1 || iAdj == 7) {
				adj[1] = 1;
			} else {
				adj[1] = -1;
			}
		}
		/**
		 * <pre>
		 * _SsAdjs is the adjustments to a cellArray for a SS.
		 * A cellArray is an array of length 4 that is:
		 * {leftCol, lowRow, nCols, nRows}.
		 * 0-7. As with VS.
		 * 8. Expand to the NE.
		 * 9. Expand to the SE.
		 * 10. Expand to the SW.
		 * 11. Expand to the NW.
		 * 12. Contract from the NE.
		 * 13. Contract from the SE.
		 * 14. Contract from the SW.
		 * 15. Contract from the NW.
		 * </pre>
		 */
		_SsAdjs = new int[16][];
		final int nSsAdjs = _SsAdjs.length;
		System.arraycopy(_VsAdjs, 0, _SsAdjs, 0, nVsAdjs);
		for (int iAdj = nVsAdjs; iAdj < nSsAdjs; ++iAdj) {
			final int[] adj = _SsAdjs[iAdj] = new int[4];
			/** Adjust the lengths. */
			if (iAdj < 12) {
				/** Both dimensions increase for "expands." */
				adj[2] = adj[3] = 1;
			} else {
				/** Both dimensions decrease for "contracts." */
				adj[2] = adj[3] = -1;
			}
			switch (iAdj) {
			case 8:
				adj[0] = adj[1] = 0;
				break;
			case 9:
				adj[0] = 0;
				adj[1] = -1;
				break;
			case 10:
				adj[0] = -1;
				adj[1] = -1;
				break;
			case 11:
				adj[0] = -1;
				adj[1] = 0;
				break;
			case 12:
				adj[0] = adj[1] = 0;
				break;
			case 13:
				adj[0] = 0;
				adj[1] = 1;
				break;
			case 14:
				adj[0] = 1;
				adj[1] = 1;
				break;
			case 15:
				adj[0] = 1;
				adj[1] = 0;
				break;
			default:
				break;
			}
		}
	}

	public Accordion2(final Planner planner,
			final ParticleIndexes[] selectedParticles,
			final double[] selectedWeights, final PatternVariable pv,
			final long cstRefSecs, final int searchDurationSecs,
			final Twister twister, final double minTwX, final double maxTwX,
			final double minTwY, final double maxTwY) {
		/** Boilerplate constants. */
		final ParticlesManager particlesManager = planner.getParticlesManager();
		final int nStages = _NStages;
		final int nSelected = selectedParticles.length;

		final Map<Integer, LrcSet> viz2 = pv.getViz2LrcSets();

		/** Determine the grid structure and initialize the Accordion2Cells. */
		final double twXExtent = maxTwX - minTwX;
		final double twYExtent = maxTwY - minTwY;
		final double aspectRatio = twYExtent / twXExtent;
		final double twXExtentNmi = twXExtent / _NmiToR;
		final double twYExtentNmi = twYExtent / _NmiToR;
		final PatternKind patternKind = pv.getPatternKind();

		final double nominalDeltaXNmi =
				Math.sqrt(_NominalSqNmiPerCell / aspectRatio);
		final int nXCells0 = (int) Math.ceil(twXExtentNmi / nominalDeltaXNmi);
		final int nXCells1 = Math.max(_MinNCellsInDimension,
				Math.min(_MaxNCellsInDimension, nXCells0));
		final double deltaX0 = twXExtent / nXCells1;
		final double nominalDeltaYNmi = nominalDeltaXNmi * aspectRatio;
		final int nYCells0 = (int) Math.ceil(twYExtentNmi / nominalDeltaYNmi);
		final int nYCells1 = Math.max(_MinNCellsInDimension,
				Math.min(_MaxNCellsInDimension, nYCells0));
		final double deltaY0 = twYExtent / nYCells1;
		final int nXCells;
		final int nYCells;
		final double deltaX, deltaY;
		if (patternKind.isPsCs()) {
			nXCells = nXCells1;
			nYCells = nYCells1;
			deltaX = deltaX0;
			deltaY = deltaY0;
		} else if (patternKind.isVs() || patternKind.isSs()) {
			/**
			 * For VS and SS, the cells must be square so that we can easily
			 * create a square region.
			 */
			deltaX = deltaY = Math.min(deltaX0, deltaY0);
			final double deltaNmi = deltaX / _NmiToR;
			final int nXCells2 = (int) Math.ceil(twXExtentNmi / deltaNmi);
			nXCells = Math.max(_MinNCellsInDimension,
					Math.min(_MaxNCellsInDimension, nXCells2));
			final int nYCells2 = (int) Math.ceil(twYExtentNmi / deltaNmi);
			nYCells = Math.max(_MinNCellsInDimension,
					Math.min(_MaxNCellsInDimension, nYCells2));
		} else {
			nXCells = nYCells = 0;
			deltaX = deltaY = Double.NaN;
		}
		_deltaXNmi = deltaX / _NmiToR;
		_deltaYNmi = deltaY / _NmiToR;
		/** _grid is stored by columns. */
		_grid = new Accordion2Cell[nXCells][nYCells];

		/** Get the time fenceposts. */
		final long firstFencePost = cstRefSecs;
		final long lastFencePost = firstFencePost + searchDurationSecs;
		final long[] fencePostRefSecsS = CombinatoricTools
				.getFenceposts(firstFencePost, lastFencePost, nStages);

		/** Build the arrays so we can reference everything by index. */
		final int nViz2 = viz2.size();
		_objectTypes = new int[nViz2];
		Arrays.fill(_objectTypes, -1);
		_sweepWidths = new double[nViz2];
		Arrays.fill(_sweepWidths, 0d);
		int nSoFar = 0;
		NEXT_LRC: for (final Map.Entry<Integer, LrcSet> entry : viz2
				.entrySet()) {
			final LrcSet lrcSet = entry.getValue();
			if (lrcSet != null && !lrcSet.isBlind()) {
				final double sweepWidth = lrcSet.getSweepWidth();
				if (sweepWidth > 0d) {
					final int objectType = entry.getKey();
					for (int index = 0; index < nSoFar; ++index) {
						if (_objectTypes[index] == objectType) {
							continue NEXT_LRC;
						}
					}
					_objectTypes[nSoFar] = objectType;
					_sweepWidths[nSoFar] = sweepWidth;
					++nSoFar;
				}
			}
		}

		/** Now we can build blank cells. */
		for (int iX = 0; iX < nXCells; ++iX) {
			for (int iY = 0; iY < nYCells; ++iY) {
				_grid[iX][iY] = new Accordion2Cell(iX, iY);
			}
		}

		/** Fill in the the Accordion2Cells' weights, and compute average sw. */
		double ttlSw = 0d;
		int nForTtlSw = 0;
		final int nObjectTypes = _objectTypes.length;
		for (int k = 0; k < nSelected; ++k) {
			final ParticleIndexes particleIndexes = selectedParticles[k];
			final double particleWeight = selectedWeights[k];
			for (int iStage = 0; iStage < nStages; ++iStage) {
				final long refSecs = fencePostRefSecsS[iStage];
				final ParticleIndexesState prtclIndxsState = particleIndexes
						.refSecsToPrtclIndxsState(particlesManager, refSecs);
				final int objectType = prtclIndxsState.getObjectType();
				for (int index = 0; index < nObjectTypes; ++index) {
					if (_objectTypes[index] == objectType) {
						final LatLng3 latLng = prtclIndxsState.getLatLng();
						final double[] twXY = twister.convert(latLng);
						final double offsetX = twXY[0] - minTwX;
						final int iX = (int) Math.floor(offsetX / deltaX);
						if (iX < 0 || iX >= nXCells) {
							continue;
						}
						final double offsetY = twXY[1] - minTwY;
						final int iY = (int) Math.floor(offsetY / deltaY);
						if (iY < 0 || iY >= nYCells) {
							continue;
						}
						final Accordion2Cell cell = _grid[iX][iY];
						cell._objectTypeWeights[index] += particleWeight;
						ttlSw += _sweepWidths[index];
						++nForTtlSw;
					}
				}
			}
		}
		final double avgSw = ttlSw / nForTtlSw;

		/** Constants about the grid and Adjustments. */
		final int nAllCols = _grid.length;
		final int nAllRows = _grid[0].length;
		final int midCol = nAllCols / 2;
		final int midRow = nAllRows / 2;

		final int[][] adjs =
				patternKind.isPsCs() ? _LpAdjs : (patternKind.isVs() ? _VsAdjs :
						(patternKind.isSs() ? _SsAdjs : null));
		final int nAdjs = adjs.length;

		/**
		 * Start with a single cell in the middle and expand it until we have a
		 * big enough one.
		 */
		final double eplNmi =
				pv.getRawSearchKts() * (searchDurationSecs / 3600d) *
						PatternUtilStatics._EffectiveSpeedReduction;
		final double minTsNmi = pv.getMinTsNmi();
		final int[] winningCellArray = new int[] { midCol, midRow, 1, 1 };
		double winningEval = Double.NaN;
		final double minSqNmi;
		if (patternKind.isPsCs()) {
			minSqNmi = eplNmi * Math.max(minTsNmi, avgSw);
			while (true) {
				final int nCols = winningCellArray[2];
				final int nRows = winningCellArray[3];
				final double widthNmi = nCols * _deltaXNmi;
				final double heightNmi = nRows * _deltaYNmi;
				final double areaSqNmi = widthNmi * heightNmi;
				if (areaSqNmi >= minSqNmi) {
					break;
				}
				int[] bestMove = null;
				double bestMoveEval = Double.NaN;
				for (int iAdj = 0; iAdj < nAdjs; ++iAdj) {
					final int[] adj = adjs[iAdj];
					/** We restrict ourselves to expanders. */
					if (Arrays.binarySearch(_LpExpanders, iAdj) < 0) {
						continue;
					}
					final int[] cellArray = winningCellArray.clone();
					for (int kk = 0; kk < adj.length; ++kk) {
						cellArray[kk] += adj[kk];
					}
					if (isLegal(cellArray)) {
						final double eval = evaluate(pv, searchDurationSecs, cellArray);
						if (bestMove == null || eval > bestMoveEval) {
							bestMove = cellArray;
							bestMoveEval = eval;
						}
					}
				}
				if (bestMove == null) {
					/** No legal expanders; stay with winningCellArray. */
					break;
				}
				/** Update winningCellArray. */
				winningEval = bestMoveEval;
				System.arraycopy(bestMove, 0, winningCellArray, 0, 4);
			}
		} else if (patternKind.isVs()) {
			final double vsTsNmi = PatternUtilStatics.computeVsTsNmi(eplNmi);
			final double tsBoxLengthNmi = 3d * vsTsNmi;
			minSqNmi = tsBoxLengthNmi * tsBoxLengthNmi;
			while (true) {
				boolean didExpansion = false;
				--winningCellArray[0];
				if (!isLegal(winningCellArray)) {
					/**
					 * Could not decrease left. Restore left and try to increase nCols
					 * by 1.
					 */
					++winningCellArray[0];
					++winningCellArray[2];
					if (!isLegal(winningCellArray)) {
						/** Restore nCols. */
						--winningCellArray[2];
					} else {
						didExpansion = true;
					}
				} else {
					/**
					 * We decreased left. We're guaranteed that we can increase nCols.
					 * Try doing that twice. If we cannot, decrease nCols just one.
					 */
					didExpansion = true;
					++winningCellArray[2];
					++winningCellArray[2];
					if (!isLegal(winningCellArray)) {
						--winningCellArray[2];
					}
				}
				/** Repeat for the rows. */
				--winningCellArray[1];
				if (!isLegal(winningCellArray)) {
					++winningCellArray[1];
					++winningCellArray[3];
					if (!isLegal(winningCellArray)) {
						--winningCellArray[3];
					} else {
						didExpansion = true;
					}
				} else {
					didExpansion = true;
					++winningCellArray[3];
					++winningCellArray[3];
					if (!isLegal(winningCellArray)) {
						--winningCellArray[3];
					}
				}
				if (!didExpansion) {
					break;
				}
				/** If this is big enough, we're done. */
				final int nCols = winningCellArray[2];
				final int nRows = winningCellArray[3];
				final double width = nCols * _deltaXNmi;
				final double height = nRows * _deltaYNmi;
				if (width >= tsBoxLengthNmi && height >= tsBoxLengthNmi) {
					break;
				}
			}
			winningEval = evaluate(pv, searchDurationSecs, winningCellArray);
		} else {
			/** SS. */
			minSqNmi = eplNmi * Math.max(minTsNmi, avgSw);
			while (true) {
				final int nCols = winningCellArray[2];
				final int nRows = winningCellArray[3];
				final double widthNmi = nCols * _deltaXNmi;
				final double heightNmi = nRows * _deltaYNmi;
				final double areaSqNmi = widthNmi * heightNmi;
				if (areaSqNmi >= minSqNmi) {
					break;
				}
				int[] bestMove = null;
				double bestMoveEval = Double.NaN;
				for (int iAdj = 0; iAdj < nAdjs; ++iAdj) {
					final int[] adj = adjs[iAdj];
					/** We restrict ourselves to expanders. */
					if (Arrays.binarySearch(_SsExpanders, iAdj) < 0) {
						continue;
					}
					final int[] cellArray = winningCellArray.clone();
					for (int kk = 0; kk < adj.length; ++kk) {
						cellArray[kk] += adj[kk];
					}
					if (isLegal(cellArray)) {
						final double eval = evaluate(pv, searchDurationSecs, cellArray);
						if (bestMove == null || eval > bestMoveEval) {
							bestMove = cellArray;
							bestMoveEval = eval;
						}
					}
					if (bestMove == null) {
						/** No legal expanders; stay with winningCellArray. */
						break;
					}
					/** Update winningCellArray. */
					winningEval = bestMoveEval;
					System.arraycopy(bestMove, 0, winningCellArray, 0, 4);
				}
			}
		}

		/** We have initialized winningCellArray and winningEval. Iterate. */
		final TreeMap<int[], Double> trys =
				new TreeMap<>(CombinatoricTools._ByAllInOrder);
		trys.put(winningCellArray, winningEval);
		int iAdj = 0;
		int[] baseCellArray = winningCellArray.clone();
		final int maxNFailsInRow = adjs.length;
		BIG_LOOP: for (int nFailsInRow = 0; nFailsInRow < maxNFailsInRow;) {
			/** Look for the best move from baseCellArray. */
			int[] bestMove = null;
			double bestMoveEval = Double.NaN;
			for (int k = 0; k < nAdjs; ++k) {
				if (planner.wasToldToSit()) {
					break;
				}
				iAdj = (++iAdj) % nAdjs;
				final int[] adj = adjs[iAdj];
				final int[] cellArray = baseCellArray.clone();
				for (int kk = 0; kk < adj.length; ++kk) {
					cellArray[kk] += adj[kk];
				}
				if (trys.containsKey(cellArray)) {
					continue;
				}
				if (isLegal(cellArray)) {
					final double eval = evaluate(pv, searchDurationSecs, cellArray);
					trys.put(cellArray, eval);
					if (bestMove == null || eval > bestMoveEval) {
						bestMove = cellArray;
						bestMoveEval = eval;
						final int nCells = bestMove[2] * bestMove[3];
						/**
						 * This is our bestMove from baseCellArray. Is it the overall
						 * best?
						 */
						if (eval > winningEval &&
								nCells * _deltaXNmi * _deltaYNmi >= minSqNmi) {
							winningEval = eval;
							System.arraycopy(cellArray, 0, baseCellArray, 0, 4);
							System.arraycopy(cellArray, 0, winningCellArray, 0, 4);
							nFailsInRow = 0;
							continue BIG_LOOP;
						}
					}
				}
			}
			if (bestMove == null) {
				break;
			}
			/**
			 * We have a bestMove, but don't have a new overall best. Move to
			 * that, but we increase nFailsInRow.
			 */
			baseCellArray = bestMove;
			++nFailsInRow;
		}
		final int leftX = winningCellArray[0];
		final int lowY = winningCellArray[1];
		final int nCols = winningCellArray[2];
		final int nRows = winningCellArray[3];
		final double offsetX = (leftX + nCols / 2d) * deltaX;
		final double offsetY = (lowY + nRows / 2d) * deltaY;
		final double twCenterX = offsetX + minTwX;
		final double twCenterY = offsetY + minTwY;
		_winningWidth = nCols * deltaX;
		_winningHeight = nRows * deltaY;
		_winningCenter = twister.unconvert(twCenterX, twCenterY);
		_winningTwXy = new double[] { twCenterX, twCenterY };
	}

	private boolean isLegal(final int[] cellArray) {
		final int leftX = cellArray[0];
		if (leftX < 0) {
			return false;
		}
		final int nCols = cellArray[2];
		final int nColsInGrid = _grid.length;
		if (leftX + nCols >= nColsInGrid) {
			return false;
		}
		final int lowY = cellArray[1];
		if (lowY < 0) {
			return false;
		}
		final int nRows = cellArray[3];
		final int nRowsInGrid = _grid[0].length;
		if (lowY + nRows >= nRowsInGrid) {
			return false;
		}
		return true;
	}

	/** Should only call this with a legal cellArray. */
	private double evaluate(final PatternVariable pv,
			final int searchDurationSecs, final int[] cellArray) {
		final int leftX = cellArray[0];
		final int lowY = cellArray[1];
		/** Only use the lower left if this is an SS. */
		final int nCols, nRows;
		final PatternKind patternKind = pv.getPatternKind();
		if (patternKind.isPsCs()) {
			nCols = cellArray[2];
			nRows = cellArray[3];
		} else if (patternKind.isSs()) {
			/**
			 * Cells are square so to get a square region, we only have to make
			 * sure nCols = nRows.
			 */
			nCols = nRows = Math.min(cellArray[2], cellArray[3]);
		} else {
			nCols = nRows = -1;
		}

		/** Compute averageSweepWidth. */
		double ttlSw = 0d;
		double poc = 0d;
		final int nObjectTypes = _objectTypes.length;
		for (int iX = 0; iX < nCols; ++iX) {
			final int iCol = leftX + iX;
			final Accordion2Cell[] column = _grid[iCol];
			for (int iY = 0; iY < nRows; ++iY) {
				final int iRow = lowY + iY;
				final Accordion2Cell cell = column[iRow];
				final double[] objectTypeWeights = cell._objectTypeWeights;
				for (int objTpIdx = 0; objTpIdx < nObjectTypes; ++objTpIdx) {
					final double objTpWt = objectTypeWeights[objTpIdx];
					ttlSw += _sweepWidths[objTpIdx] * objTpWt;
					poc += objTpWt;
				}
			}
		}
		if (poc == 0d) {
			return 0d;
		}
		final double avgSw = ttlSw / poc;

		final double dim0Nmi = nCols * _deltaXNmi;
		final double dim1Nmi = nRows * _deltaYNmi;
		final double pod =
				pv.getNftPod(searchDurationSecs, avgSw, dim0Nmi, dim1Nmi);
		final double pos = pod * poc;
		return pos;
	}

	private static String translate(final int[] adj) {
		String clmnString = "";
		if (adj[2] == 0) {
			if (adj[0] != 0) {
				clmnString += "Shifting " + (adj[0] < 0 ? "Left" : "Right");
			}
		} else {
			clmnString =
					(adj[2] > 0 ? "Increase #Clmns by " : "Decrease #Clmns by ");
			if (adj[0] != 0) {
				clmnString +=
						(adj[0] < 0 ? "Decreasing " : "Increasing ") + "LeftBoundary";
			} else {
				clmnString =
						(adj[2] > 0 ? "Increasing nClmns" : "Decreasing #nClmns");
			}
		}
		String rowString = "";
		if (adj[3] == 0) {
			if (adj[1] != 0) {
				rowString += "Shifting " + (adj[1] < 0 ? "Down" : "Up");
			}
		} else {
			rowString =
					(adj[3] > 0 ? "Increase #Rows by " : "Decrease #Rows by ");
			if (adj[1] != 0) {
				rowString +=
						(adj[1] < 0 ? "Decreasing " : "Increasing ") + "LowBoundary";
			} else {
				rowString = (adj[3] > 0 ? "Increasing nRows" : "Decreasing #nRows");
			}
		}
		String s = clmnString;
		if (rowString.length() > 0) {
			if (s.length() > 0) {
				s += ", ";
			}
			s += rowString;
		}
		return s;
	}

	public static void main(final String[] args) {
		System.out.printf("Lp Adjs:");
		final int nLpAdjs = _LpAdjs.length;
		for (int k = 0; k < nLpAdjs; ++k) {
			System.out.printf("\n%2d: %s", k, translate(_LpAdjs[k]));
		}
		System.out.printf("\n\nVs Adjs:");
		final int nVsAdjs = _VsAdjs.length;
		for (int k = 0; k < nVsAdjs; ++k) {
			System.out.printf("\n%2d: %s", k, translate(_VsAdjs[k]));
		}
		System.out.printf("\n\nSs Adjs:");
		final int nSsAdjs = _SsAdjs.length;
		for (int k = 0; k < nSsAdjs; ++k) {
			System.out.printf("\n%2d: %s", k, translate(_SsAdjs[k]));
		}
	}
}
