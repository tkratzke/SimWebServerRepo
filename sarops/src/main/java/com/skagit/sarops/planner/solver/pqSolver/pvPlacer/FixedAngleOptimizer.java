package com.skagit.sarops.planner.solver.pqSolver.pvPlacer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.io.ModelReader;
import com.skagit.sarops.planner.plannerModel.PatternKind;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.util.patternUtils.MyStyle;
import com.skagit.sarops.util.patternUtils.PatternUtilStatics;
import com.skagit.util.IntDouble;
import com.skagit.util.MathX;
import com.skagit.util.massFinder.MassFinder;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.TangentCylinder;

/**
 * <pre>
 * Used when placing a single PV.
 * In contrast, Accordion2 is used when deconflicting.
 * For LP, this one sets bestLlrh to the entire extent.
 * It then splits bestLlrh into a matrix of cells and exhaustively
 * searches this matrix of cells to improve bestLlrh. It repeats
 * this process as per PvPlacer._NTimesToSplit.
 * For VS, it forms an appropriately sized square and rattles it around.
 * For SS, it ??
 * </pre>
 */
public class FixedAngleOptimizer {

	final private static double _NmiToR = MathX._NmiToR;
	final private static int _NInVsLattice = 10;
	final private static int _NInSsLattices = 10;

	final private PatternVariable _pv;
	final long _cstRefSecs;
	final int _searchDurationSecs;
	final private HashMap<IntDouble, IntDouble> _objTpToSweepWidth;
	final public PvValue[] _competitors;
	final private TangentCylinder _tangentCylinder;
	final private double _firstLegHdg;
	final private double _c, _s;
	final private MassFinder _massFinder;
	final private double _allMass;
	/** Output. */
	final public PvValue _bestPvValue;
	final public double _score;
	final public double[] _bestLlrh;
	final private IndexFinder _bestXIndexFinder;
	final private IndexFinder _bestYIndexFinder;

	private static class IndexFinder {
		final private double _mid;
		final private double _cellWidth;
		final private int _lowIdx;
		final private int _hghIdx;

		private IndexFinder(final double mid, final double cellWidth, final int nCells) {
			_mid = mid;
			_cellWidth = cellWidth;
			final double moveFromCenter = (nCells / 2d - 0.5) * cellWidth;
			_lowIdx = coordToIndex(mid - moveFromCenter);
			_hghIdx = coordToIndex(mid + moveFromCenter);
		}

		private int coordToIndex(final double d) {
			final int returnValue = (int) Math.round((d - _mid) / _cellWidth);
			return returnValue;
		}

		private double cellIdxToLowCoord(final int index) {
			final double returnValue = _mid + (index - 0.5) * _cellWidth;
			return returnValue;
		}

		public String getString() {
			final double midNmi = _mid / _NmiToR;
			final double cellWidthNmi = _cellWidth / _NmiToR;
			final String s = String.format("MidNmi[%.2f] CellWidthNmi[%.2f] (lowIdx,HghIdx)=(%d,%d)", midNmi,
					cellWidthNmi, _lowIdx, _hghIdx);
			return s;
		}

		@Override
		public String toString() {
			return getString();
		}
	}

	public class CornersAndWeights implements Comparable<CornersAndWeights> {
		final public LatLng3[] _corners;
		final public HashMap<IntDouble, IntDouble> _objTpsToMass;

		public CornersAndWeights(final LatLng3[] corners, final HashMap<IntDouble, IntDouble> objTpToMass) {
			_corners = corners;
			_objTpsToMass = objTpToMass;
		}

		@Override
		final public int hashCode() {
			return _corners[0].hashCode();
		}

		@Override
		public boolean equals(final Object o) {
			try {
				return compareTo((CornersAndWeights) o) == 0;
			} catch (final ClassCastException e) {
				return false;
			}
		}

		@Override
		public int compareTo(final CornersAndWeights o) {
			final LatLng3[] corners = o._corners;
			final int n1 = _corners.length;
			final int n2 = corners.length;
			if (n1 != n2) {
				return n1 < n2 ? -1 : 1;
			}
			for (int k = 0; k < n1; ++k) {
				final LatLng3 latLng1 = _corners[k];
				final LatLng3 latLng2 = corners[k];
				final int compareValue = latLng1.compareLatLng(latLng2);
				if (compareValue != 0) {
					return compareValue;
				}
			}
			return 0;
		}

	}

	/** Used only for when there is nothing to optimize. */
	public FixedAngleOptimizer(final PatternVariable pv) {
		_pv = pv;
		_cstRefSecs = ModelReader._UnsetTime;
		_searchDurationSecs = ModelReader._BadDuration;
		_objTpToSweepWidth = new HashMap<>();
		_competitors = new PvValue[0];
		_tangentCylinder = null;
		_firstLegHdg = 90d;
		final double radsCcwFromE = Math.toRadians(90d - _firstLegHdg);
		_c = MathX.cosX(radsCcwFromE);
		_s = MathX.sinX(radsCcwFromE);
		_massFinder = new MassFinder(new ArrayList<MassFinder.Item>());
		_allMass = 0d;
		final Model simModel = pv.getPlannerModel().getSimModel();
		final Extent modelExtent = simModel.getExtent();
		final MyStyle onMarsMyStyle = new MyStyle(modelExtent, pv);
		_bestPvValue = new PvValue(pv, onMarsMyStyle);
		_score = 0d;
		_bestLlrh = null;
		_bestXIndexFinder = _bestYIndexFinder = null;
		return;
	}

	public FixedAngleOptimizer(final PatternVariable pv, final long cstRefSecs, final int searchDurationSecs,
			final PvValue[] knowns, final List<ParticlePlus> prtclPlusS,
			final HashMap<IntDouble, IntDouble> objTpToSweepWidth, final double firstLegHdg, final double avgSw) {
		/** Unpack the simple inputs. */
		_pv = pv;
		_cstRefSecs = cstRefSecs;
		_searchDurationSecs = searchDurationSecs;
		_firstLegHdg = firstLegHdg;
		final double radsCcwFromE = Math.toRadians(90d - _firstLegHdg);
		_c = MathX.cosX(radsCcwFromE);
		_s = MathX.sinX(radsCcwFromE);
		_objTpToSweepWidth = objTpToSweepWidth;
		final int nPrtclPlusS = prtclPlusS.size();

		/** Boilerplate. */
		final PlannerModel plannerModel = pv.getPlannerModel();
		final Model simModel = plannerModel.getSimModel();
		final int nKnowns = knowns == null ? 0 : knowns.length;

		/** Set _competitors; those fixedPvValues that conflict. */
		final ArrayList<PvValue> competitors = new ArrayList<>();
		final boolean forOptn = true;
		for (int k = 0; k < nKnowns; ++k) {
			final PvValue known = knowns[k];
			if (known == null || known.onMars()) {
				continue;
			}
			final PatternVariable pvOfKnown = known.getPv();
			if (pvOfKnown == pv) {
				continue;
			}
			if (!plannerModel.mayOverlap(pv, pvOfKnown, forOptn)) {
				competitors.add(known);
			}
		}
		_competitors = competitors.toArray(new PvValue[competitors.size()]);

		/** Compute the set of distinct ParticlePluses and distinct LatLngs. */
		final HashMap<ParticlePlus, double[]> prtclPluSToWt = new HashMap<>();
		final HashMap<LatLng3, double[]> latLngToWt = new HashMap<>();
		double allMass = 0d;
		for (int k = 0; k < nPrtclPlusS; ++k) {
			final ParticlePlus prtclPlus = prtclPlusS.get(k);
			double[] wtArray0 = prtclPluSToWt.get(prtclPlus);
			if (wtArray0 == null) {
				wtArray0 = new double[] {
						0d
				};
				prtclPluSToWt.put(prtclPlus, wtArray0);
			}
			final LatLng3 midLatLng = prtclPlus._midLatLng;
			double[] wtArray1 = latLngToWt.get(midLatLng);
			if (wtArray1 == null) {
				wtArray1 = new double[] {
						0d
				};
				latLngToWt.put(midLatLng, wtArray1);
			}
			final double wt = prtclPlus._weight;
			wtArray0[0] += wt;
			wtArray1[0] += wt;
			allMass += wt;
		}
		_allMass = allMass;
		final int nDistinctPrtclPlusS = prtclPluSToWt.size();
		final int nDistinctLatLngs = latLngToWt.size();
		if (nDistinctLatLngs < 1) {
			_massFinder = new MassFinder(new ArrayList<MassFinder.Item>());
			_tangentCylinder = null;
			final Extent modelExtent = simModel.getExtent();
			final MyStyle onMarsMyStyle = new MyStyle(modelExtent, pv);
			_bestPvValue = new PvValue(pv, onMarsMyStyle);
			_score = 0d;
			_bestLlrh = null;
			_bestXIndexFinder = _bestYIndexFinder = null;
			return;
		}

		/** Set _tangentCylinder. */
		final double[] wts = new double[nDistinctLatLngs];
		final LatLng3[] latLngs = new LatLng3[nDistinctLatLngs];
		final Iterator<Map.Entry<LatLng3, double[]>> it0 = latLngToWt.entrySet().iterator();
		for (int k = 0; k < nDistinctLatLngs; ++k) {
			final Map.Entry<LatLng3, double[]> entry = it0.next();
			latLngs[k] = entry.getKey();
			wts[k] = entry.getValue()[0];
		}
		_tangentCylinder = TangentCylinder.getTangentCylinder(latLngs, wts);

		/** Set MassFinder in rotated coordinates. */
		final Iterator<Map.Entry<ParticlePlus, double[]>> it1 = prtclPluSToWt.entrySet().iterator();
		final ArrayList<MassFinder.Item> items = new ArrayList<>(nDistinctPrtclPlusS);
		for (int k = 0; k < nDistinctPrtclPlusS; ++k) {
			final Map.Entry<ParticlePlus, double[]> entry = it1.next();
			final ParticlePlus prtclPlus = entry.getKey();
			final LatLng3 latLng = prtclPlus._midLatLng;
			final int objTp = prtclPlus._objectType;
			final double[] xy = getTwistedXy(latLng);
			final double wt = entry.getValue()[0];
			items.add(new MassFinder.Item(xy, wt, objTp, null));
			assert _objTpToSweepWidth.get(new IntDouble(objTp)) != null : "FixedAngleOptProblem1";
		}
		_massFinder = new MassFinder(items);

		final PatternKind patternKind = pv.getPatternKind();
		final double rawSearchKts = _pv.getRawSearchKts();
		final double eplNmi = (rawSearchKts * (searchDurationSecs / 3600d))
				* PatternUtilStatics._EffectiveSpeedReduction;
		final double minTsNmi = PatternUtilStatics.roundWithMinimum(PatternUtilStatics._TsInc, _pv.getMinTsNmi());
		if (patternKind.isPsCs()) {
			/** bestLlrh starts off as everything. */
			final double minSqNmi = eplNmi * minTsNmi;
			final double minSqR = minSqNmi * _NmiToR * _NmiToR;
			final double minSideR = Math.sqrt(minSqR);
			final double bigMinX = Math.min(-minSideR, _massFinder.getMinX());
			final double bigMaxX = Math.max(minSideR, _massFinder.getMaxX());
			final double bigMidX = (bigMinX + bigMaxX) / 2d;
			final double bigMinY = Math.min(-minSideR, _massFinder.getMinY());
			final double bigMaxY = Math.max(minSideR, _massFinder.getMaxY());
			final double bigMidY = (bigMinY + bigMaxY) / 2d;
			final double bigXWidth = bigMaxX - bigMinX;
			final double bigYWidth = bigMaxY - bigMinY;
			/**
			 * The following 3 are what we use to get bestScore as big as possible. The
			 * initialization of bestLlrh is one big cell.
			 */
			double[] bestLlrh = new double[] {
					bigMinX, bigMinY, bigMaxX, bigMaxY
			};
			final int nCellsForBig = 1;
			IndexFinder bestXIndexFinder = new IndexFinder(bigMidX, bigXWidth, nCellsForBig);
			IndexFinder bestYIndexFinder = new IndexFinder(bigMidY, bigYWidth, nCellsForBig);
			double bestScore = getScore(bestLlrh);

			for (int k = 0; k < PvPlacer._NTimesToSplit; ++k) {
				final double minX = bestLlrh[0];
				final double minY = bestLlrh[1];
				final double maxX = bestLlrh[2];
				final double maxY = bestLlrh[3];
				final double xExtent = maxX - minX;
				final double yExtent = maxY - minY;
				final double bestArea = xExtent * yExtent;
				final IntDouble[] splits = getSplit(xExtent, yExtent, PvPlacer._NCellsOnBigSide);
				/**
				 * nX is the number of cells + 1, since nX refers to the grid lines not the
				 * middles of the cells.
				 */
				final int nX = splits[0]._i;
				final int nY = splits[1]._i;
				final double xCellWidth = splits[0]._d;
				final double yCellWidth = splits[1]._d;
				final double midX = minX + xExtent / 2d;
				final double midY = minY + yExtent / 2d;
				final IndexFinder xIdxFinder = new IndexFinder(midX, xCellWidth, nX);
				final IndexFinder yIdxFinder = new IndexFinder(midY, yCellWidth, nY);
				final int xIdxLow = xIdxFinder._lowIdx;
				final int xIdxHgh = xIdxFinder._hghIdx;
				final int yIdxLow = yIdxFinder._lowIdx;
				final int yIdxHgh = yIdxFinder._hghIdx;

				double[] myBestLlrh = null;
				double myBestScore = Double.NEGATIVE_INFINITY;
				for (int xIdx1 = xIdxLow; xIdx1 <= xIdxHgh; ++xIdx1) {
					final double left = xIdxFinder.cellIdxToLowCoord(xIdx1);
					for (int xIdx2 = xIdx1; xIdx2 <= xIdxHgh; ++xIdx2) {
						final double right = xIdxFinder.cellIdxToLowCoord(xIdx2 + 1);
						final double deltaX = right - left;
						for (int yIdx1 = yIdxLow; yIdx1 <= yIdxHgh; ++yIdx1) {
							final double low = yIdxFinder.cellIdxToLowCoord(yIdx1);
							for (int yIdx2 = yIdx1; yIdx2 <= yIdxHgh; ++yIdx2) {
								final double high = yIdxFinder.cellIdxToLowCoord(yIdx2 + 1);
								final double deltaY = high - low;
								final double area = deltaX * deltaY;
								if (area < bestArea / 2d) {
									continue;
								}
								final double[] llrh = new double[] {
										left, low, right, high
								};
								final double score = getScore(llrh);
								if (score > myBestScore) {
									myBestScore = score;
									myBestLlrh = llrh;
								}
							}
						}
					}
				}
				/** If someone beat it, reset bestLlrh. */
				if (myBestScore > bestScore) {
					bestScore = myBestScore;
					bestLlrh = myBestLlrh;
					bestXIndexFinder = xIdxFinder;
					bestYIndexFinder = yIdxFinder;
				}
			}
			/**
			 * computePvValue(bestLlrh) might come back null. We just assume that it won't.
			 */
			_bestPvValue = computePvValue(bestLlrh);
			_score = bestScore;
			_bestLlrh = bestLlrh;
			_bestXIndexFinder = bestXIndexFinder;
			_bestYIndexFinder = bestYIndexFinder;
		} else if (patternKind.isVs()) {
			final double vsTsNmi = PatternUtilStatics.computeVsTsNmi(eplNmi);
			final double boxLengthNmi = vsTsNmi * 3d;
			final double[] bestLlrhAndScore = getBestLlrhAndScoreForSquares(boxLengthNmi, _NInVsLattice);
			_bestLlrh = new double[4];
			System.arraycopy(bestLlrhAndScore, 0, _bestLlrh, 0, 4);
			_score = bestLlrhAndScore[4];
			_bestPvValue = computePvValue(_bestLlrh);
			_bestXIndexFinder = _bestYIndexFinder = null;
		} else if (patternKind.isSs()) {
			/** Try the minimum track spacing, and the avgSw. */
			double[] bestLlrhAndScore = null;
			for (int iPass = 0; iPass < 2; ++iPass) {
				final double tsNmi0 = iPass == 0 ? minTsNmi
						: PatternUtilStatics.roundWithMinimum(PatternUtilStatics._TsInc, avgSw);
				final int nHalfLaps = (int) Math.floor(Math.sqrt(eplNmi / tsNmi0));
				final double ssTsNmi = Math.floor((eplNmi / (nHalfLaps * nHalfLaps)) / PatternUtilStatics._TsInc)
						* PatternUtilStatics._TsInc;
				final double boxLengthNmi = ssTsNmi * nHalfLaps;
				final double[] bestLlrhAndScoreX = getBestLlrhAndScoreForSquares(boxLengthNmi, _NInSsLattices);
				if (bestLlrhAndScore == null || bestLlrhAndScoreX[4] > bestLlrhAndScore[4]) {
					bestLlrhAndScore = bestLlrhAndScoreX;
				}
			}
			_bestLlrh = new double[4];
			System.arraycopy(bestLlrhAndScore, 0, _bestLlrh, 0, 4);
			_score = bestLlrhAndScore[4];
			_bestPvValue = computePvValue(_bestLlrh);
			_bestXIndexFinder = _bestYIndexFinder = null;
		} else {
			_bestPvValue = null;
			_score = Double.NaN;
			_bestLlrh = null;
			_bestXIndexFinder = _bestYIndexFinder = null;
		}
	}

	/**
	 * Used only for VS and SS; we simply "rattle" squares around in the allowable
	 * area.
	 */
	private double[] getBestLlrhAndScoreForSquares(final double boxLengthNmi, final int _nInLattice) {
		final double minXNmi = _massFinder.getMinX() / _NmiToR;
		final double maxXNmi = _massFinder.getMaxX() / _NmiToR;
		final double[] xNmis;
		if (boxLengthNmi > maxXNmi - minXNmi) {
			xNmis = new double[] {
					(minXNmi + maxXNmi) / 2d
			};
		} else {
			/** We'll do the left, the right, and _nInLattice-2 more. */
			xNmis = new double[_nInLattice];
			final double firstMidpoint = minXNmi + boxLengthNmi / 2d;
			final double lastMidpoint = maxXNmi - boxLengthNmi / 2d;
			final double nmiGap = (lastMidpoint - firstMidpoint) / 9d;
			xNmis[0] = firstMidpoint;
			for (int k = 1; k <= _nInLattice - 2; ++k) {
				xNmis[k] = firstMidpoint + k * nmiGap;
			}
			xNmis[_nInLattice - 1] = lastMidpoint;
		}
		final double minYNmi = _massFinder.getMinY() / _NmiToR;
		final double maxYNmi = _massFinder.getMaxY() / _NmiToR;
		final double[] yNmis;
		if (boxLengthNmi > maxYNmi - minYNmi) {
			yNmis = new double[] {
					(minYNmi + maxYNmi) / 2d
			};
		} else {
			/** We'll do the bottom, the top, and _nInLattice more. */
			yNmis = new double[_nInLattice];
			final double firstMidpoint = minYNmi + boxLengthNmi / 2d;
			final double lastMidpoint = maxYNmi - boxLengthNmi / 2d;
			final double nmiGap = (lastMidpoint - firstMidpoint) / (_nInLattice - 1);
			yNmis[0] = firstMidpoint;
			for (int k = 1; k <= _nInLattice - 2; ++k) {
				yNmis[k] = firstMidpoint + k * nmiGap;
			}
			yNmis[_nInLattice - 1] = lastMidpoint;
		}
		final double[] bestLlrhAndScore = new double[5];
		Arrays.fill(bestLlrhAndScore, Double.NEGATIVE_INFINITY);
		for (final double midXNmi : xNmis) {
			final double left = (midXNmi - boxLengthNmi / 2d) * _NmiToR;
			final double right = (midXNmi + boxLengthNmi / 2d) * _NmiToR;
			for (final double midYNmi : yNmis) {
				final double low = (midYNmi - boxLengthNmi / 2d) * _NmiToR;
				final double high = (midYNmi + boxLengthNmi / 2d) * _NmiToR;
				final double[] llrh = new double[] {
						left, low, right, high
				};
				final double score = getScore(llrh);
				if (score > bestLlrhAndScore[4]) {
					System.arraycopy(llrh, 0, bestLlrhAndScore, 0, 4);
					bestLlrhAndScore[4] = score;
				}
			}
		}
		return bestLlrhAndScore;
	}

	private double getScore(final double[] llrh) {
		final double left = llrh[0];
		final double low = llrh[1];
		final double right = llrh[2];
		final double high = llrh[3];
		final boolean includeLeft = true;
		final boolean includeLow = true;
		final boolean includeRight = true;
		final boolean includeHigh = true;

		/** Compute poc and average sweepWidth. */
		double weightedSw = 0d;
		double totalMass = 0d;
		for (final IntDouble objTpSw : _objTpToSweepWidth.keySet()) {
			final int objTp = objTpSw._i;
			final double sw = objTpSw._d;
			final double mass = _massFinder.getMass(left, low, right, high, includeLeft, includeLow, includeRight,
					includeHigh, objTp);
			weightedSw += mass * sw;
			totalMass += mass;
		}
		final double avgSw = totalMass > 0d ? (weightedSw / totalMass) : 0d;
		final double poc = totalMass / _allMass;
		final double dim0Nmi = (llrh[2] - llrh[0]) / _NmiToR;
		final double dim1Nmi = (llrh[3] - llrh[1]) / _NmiToR;
		final double pod = _pv.getNftPod(_searchDurationSecs, avgSw, dim0Nmi, dim1Nmi);
		final double score = poc * pod;
		return score;
	}

	private PvValue computePvValue(final double[] bestLlrh) {
		final double midX = (bestLlrh[0] + bestLlrh[2]) / 2d;
		final double midY = (bestLlrh[1] + bestLlrh[3]) / 2d;
		final LatLng3 midLatLng = getLatLng(midX, midY);
		final PatternKind patternKind = _pv.getPatternKind();
		final PlannerModel plannerModel = _pv.getPlannerModel();
		final boolean firstTurnRight = plannerModel.getFirstTurnRight(patternKind, true, /* randomize= */true);
		final double firstTurnRightD = firstTurnRight ? 1d : -1d;
		if (patternKind.isPsCs()) {
			final double alongR0 = bestLlrh[2] - bestLlrh[0];
			final double acrossR0 = bestLlrh[3] - bestLlrh[1];
			final double alongNmi0 = alongR0 / _NmiToR;
			final double acrossNmi0 = acrossR0 / _NmiToR;
			final double firstLegDirR;
			final double alongR, acrossR;
			if (alongNmi0 >= acrossNmi0) {
				alongR = alongR0;
				acrossR = acrossR0;
				firstLegDirR = Math.toRadians(90d - _firstLegHdg);
			} else {
				alongR = acrossR0;
				acrossR = alongR0;
				final double firstLegHdg = _firstLegHdg + 90d;
				firstLegDirR = Math.toRadians(90d - firstLegHdg);
			}
			final PvValue pvValue = PvValue.createPvValue(_pv, _pv.getPvCstRefSecs(), _pv.getPvRawSearchDurationSecs(),
					midLatLng, firstLegDirR, alongR, acrossR * firstTurnRightD);
			return pvValue;
		}
		if (patternKind.isSs()) {
			final double acrossR = bestLlrh[3] - bestLlrh[1];
			final double firstLegHdg = _firstLegHdg + 90d;
			final double firstLegDirR = Math.toRadians(90d - firstLegHdg);
			final PvValue pvValue = PvValue.createPvValue(_pv, _pv.getPvCstRefSecs(), _pv.getPvRawSearchDurationSecs(),
					midLatLng, firstLegDirR, /* alongR= */Double.NaN, acrossR * firstTurnRightD);
			return pvValue;
		}
		if (patternKind.isVs()) {
			/**
			 * All we can do is use the center point and play with the firstLegHdg and
			 * firstTurnRight.
			 */
			final PvValue pvValue = PvValue.createPvValue(_pv, _pv.getPvCstRefSecs(), _pv.getPvRawSearchDurationSecs(),
					midLatLng, /* firstLegDirR= */Math.toRadians(90d - _firstLegHdg), firstTurnRight);
			return pvValue;
		}
		return null;
	}

	private LatLng3 getLatLng(final double twistedX, final double twistedY) {
		final double east = _c * twistedX - _s * twistedY;
		final double north = _s * twistedX + _c * twistedY;
		return _tangentCylinder.new FlatLatLng(east, north);
	}

	/** The x-coordinate will be in the direction of firstDirDegs. */
	private double[] getTwistedXy(final LatLng3 latLng) {
		final TangentCylinder.FlatLatLng flatLatLng = _tangentCylinder.convertToMyFlatLatLng(latLng);
		final double east = flatLatLng.getEastOffset();
		final double north = flatLatLng.getNorthOffset();
		final double x = east * _c + north * _s;
		final double y = east * -_s + north * _c;
		return new double[] {
				x, y
		};
	}

	private static IntDouble[] getSplit(final double xExtent, final double yExtent, final int nCellsOnBigSide) {
		final double big, small;
		final boolean xIsBig;
		if (xExtent >= yExtent) {
			big = xExtent;
			small = yExtent;
			xIsBig = true;
		} else {
			big = yExtent;
			small = xExtent;
			xIsBig = false;
		}
		final double bigCellSize = big / nCellsOnBigSide;

		final double targetNCellsOnSmallSide = small / bigCellSize;
		final int nCellsOnSmallSideA = Math.max(PvPlacer._MinNCellsOnSmallSide,
				(int) Math.floor(targetNCellsOnSmallSide));
		final double smallCellSizeA = small / nCellsOnSmallSideA;
		final int nCellsOnSmallSideB = Math.max(PvPlacer._MinNCellsOnSmallSide,
				(int) Math.ceil(targetNCellsOnSmallSide));
		final double smallCellSizeB = small / nCellsOnSmallSideB;
		final int nCellsOnSmallSide;
		final double smallCellSize;
		if (Math.abs(smallCellSizeA - bigCellSize) < Math.abs(smallCellSizeB - bigCellSize)) {
			smallCellSize = smallCellSizeA;
			nCellsOnSmallSide = nCellsOnSmallSideA;
		} else {
			smallCellSize = smallCellSizeB;
			nCellsOnSmallSide = nCellsOnSmallSideB;
		}
		final IntDouble intDouble1 = new IntDouble(nCellsOnBigSide + 1, bigCellSize);
		final IntDouble intDouble2 = new IntDouble(nCellsOnSmallSide + 1, smallCellSize);
		if (xIsBig) {
			return new IntDouble[] {
					intDouble1, intDouble2
			};
		}
		return new IntDouble[] {
				intDouble2, intDouble1
		};
	}

	public ArrayList<CornersAndWeights> getCornersAndWeights() {
		final ArrayList<CornersAndWeights> cornersAndWeightsS = new ArrayList<>();
		final int ixLow = _bestXIndexFinder.coordToIndex(_massFinder.getMinX());
		final int ixHgh = _bestXIndexFinder.coordToIndex(_massFinder.getMaxX());
		final int iyLow = _bestYIndexFinder.coordToIndex(_massFinder.getMinY());
		final int iyHgh = _bestYIndexFinder.coordToIndex(_massFinder.getMaxY());
		for (int k1 = ixLow; k1 <= ixHgh; ++k1) {
			final double xLow = _bestXIndexFinder.cellIdxToLowCoord(k1);
			final double xHgh = _bestXIndexFinder.cellIdxToLowCoord(k1 + 1);
			for (int k2 = iyLow; k2 <= iyHgh; ++k2) {
				final double yLow = _bestYIndexFinder.cellIdxToLowCoord(k2);
				final double yHgh = _bestYIndexFinder.cellIdxToLowCoord(k2 + 1);
				final double d0 = _massFinder.getMass(xLow, yLow, xHgh, yHgh, true, true, true, true,
						MassFinder._AllItemTypeIds);
				if (d0 > 0d) {
					final HashMap<IntDouble, IntDouble> objTpToMass = new HashMap<>();
					final IntDouble intDouble1 = new IntDouble(MassFinder._AllItemTypeIds, d0);
					objTpToMass.put(intDouble1, intDouble1);
					final LatLng3[] corners = new LatLng3[4];
					corners[0] = getLatLng(xLow, yLow);
					corners[1] = getLatLng(xHgh, yLow);
					corners[2] = getLatLng(xHgh, yHgh);
					corners[3] = getLatLng(xLow, yHgh);
					final int nObjTps = _objTpToSweepWidth.size();
					final Iterator<IntDouble> it = _objTpToSweepWidth.keySet().iterator();
					for (int k3 = 0; k3 < nObjTps; ++k3) {
						final IntDouble objTpSweepWidth = it.next();
						final int objTp = objTpSweepWidth._i;
						final double d1 = _massFinder.getMass(xLow, yLow, xHgh, yHgh, true, true, true, true, objTp);
						final IntDouble intDouble2 = new IntDouble(objTp, d1);
						objTpToMass.put(intDouble2, intDouble2);
					}
					cornersAndWeightsS.add(new CornersAndWeights(corners, objTpToMass));
				}
			}
		}
		return cornersAndWeightsS;
	}
}
