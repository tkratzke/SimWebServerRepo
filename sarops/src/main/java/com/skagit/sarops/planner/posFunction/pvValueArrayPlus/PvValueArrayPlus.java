package com.skagit.sarops.planner.posFunction.pvValueArrayPlus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.planner.plannerModel.pvSeq.PvSeq;
import com.skagit.sarops.planner.posFunction.PosEvaluation;
import com.skagit.sarops.planner.posFunction.PosFunction;
import com.skagit.sarops.planner.posFunction.PosFunction.EvalType;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.planner.solver.SolversManager;
import com.skagit.sarops.planner.solver.pqSolver.PqSolver;
import com.skagit.sarops.planner.solver.pqSolver.RefinementStage;
import com.skagit.sarops.util.patternUtils.MyStyle;
import com.skagit.util.Constants;
import com.skagit.util.HermitePlus;
import com.skagit.util.navigation.Extent;

public class PvValueArrayPlus implements Cloneable {
	final static HermitePlus _HermitePlusOvl = getHermitePlus(-0.5, 1d, 1, 1);

	private static HermitePlus getHermitePlus(final double yForNeg1,
			final double yForPos1, final int nDerivativesAtNeg1,
			final int nDerivativesAtPos1) {
		final double[][] xy = new double[3][];
		final double[] thisXyForNeg = new double[2 + nDerivativesAtNeg1];
		thisXyForNeg[0] = -1d;
		thisXyForNeg[1] = yForNeg1;
		for (int k = 0; k < nDerivativesAtNeg1; ++k) {
			thisXyForNeg[2 + k] = 0d;
		}
		xy[0] = thisXyForNeg;
		xy[1] = new double[] { 0d, 0d };
		final double[] thisXyForPos = new double[2 + nDerivativesAtPos1];
		thisXyForPos[0] = 1d;
		thisXyForPos[1] = yForPos1;
		for (int k = 0; k < nDerivativesAtPos1; ++k) {
			thisXyForPos[2 + k] = 0d;
		}
		xy[2] = thisXyForPos;
		return new HermitePlus(xy);
	}

	private static Comparator<CtV> _StructureCompare = new Comparator<>() {
		@Override
		public int compare(final CtV ctV0, final CtV ctV1) {
			final int ctTypeOrd0 = ctV0.getCtType().ordinal();
			final int ctTypeOrd1 = ctV1.getCtType().ordinal();
			if (ctTypeOrd0 < ctTypeOrd1) {
				return 1;
			} else if (ctTypeOrd0 > ctTypeOrd1) {
				return -1;
			} else {
				final int idx0 = ctV0.getIndexWithinType();
				final int idx1 = ctV1.getIndexWithinType();
				if (idx0 < idx1) {
					return 1;
				} else if (idx0 > idx1) {
					return -1;
				} else {
					return 0;
				}
			}
		}
	};
	private static Comparator<CtV> _ForOptnCompare = new Comparator<>() {
		@Override
		public int compare(final CtV ctV1, final CtV ctV2) {

			if (ctV1._forOptnV > ctV2._forOptnV) {
				return -1;
			} else if (ctV1._forOptnV < ctV2._forOptnV) {
				return 1;
			} else {
				return _StructureCompare.compare(ctV1, ctV2);
			}
		}
	};

	final private Planner _planner;
	final private PvValue[] _pvValues;
	final private BitSet _equalToInitial;
	final private PosEvaluation[] _posEvals;
	/**
	 * In the following arrays, big violations are bad and will appear at the
	 * top of the array. This array is only for optimization. For end-of-run
	 * reports, similar arrays will be constructed on the fly.
	 */
	final CtV[] _descendingForOptnCtVs;
	final PvTrnstCtV[] _descendingForOptnPvTrnstCtVs;
	final PvSeqTrnstCtV[] _descendingForOptnPvSeqTrnstCtVs;

	public Planner getPlanner() {
		return _planner;
	}

	/** pvValues might have nulls, but it must be canonical-full. */
	public PvValueArrayPlus(final Planner planner, final PvValue[] pvValues) {
		_planner = planner;
		final int nPttrnVbls = pvValues == null ? 0 : pvValues.length;
		_equalToInitial = new BitSet(nPttrnVbls);
		final SolversManager solversManager = planner.getSolversManager();
		final int nEvalTypes = EvalType._EvalTypes.length;
		_posEvals = new PosEvaluation[nEvalTypes];
		Arrays.fill(_posEvals, null);

		if (pvValues == null) {
			/** Stop event. */
			_pvValues = null;
			_descendingForOptnCtVs = null;
			_descendingForOptnPvTrnstCtVs = null;
			_descendingForOptnPvSeqTrnstCtVs = null;
			return;
		}
		/**
		 * Eliminate ones that are not isActive, make sure userFrozens are
		 * there, and eliminate onMars. _pvValues will end up a full array,
		 * possibly with nulls.
		 */
		_pvValues = solversManager.convertToIsActiveAndFrozens(pvValues);

		/** Set _equalToInitial. */
		final PatternVariable[] pttrnVbls =
				_planner.getPlannerModel().getPttrnVbls();
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PatternVariable pv = pttrnVbls[grandOrd];
			if (pv.getInitialPvValue() == _pvValues[grandOrd]) {
				_equalToInitial.set(grandOrd);
			}
		}

		/** Gather the forOptn CtVs. */
		final ArrayList<CtV> ctVList = new ArrayList<>();
		final OvlCtV[] ovlCtVsArray = computeForOptnOvlCtVs();
		ctVList.addAll(Arrays.asList(ovlCtVsArray));
		_descendingForOptnCtVs = ctVList.toArray(new CtV[ctVList.size()]);
		Arrays.sort(_descendingForOptnCtVs, _ForOptnCompare);
		_descendingForOptnPvTrnstCtVs = computeForOptnPvTrnstCtVs();
		Arrays.sort(_descendingForOptnPvTrnstCtVs, _ForOptnCompare);
		_descendingForOptnPvSeqTrnstCtVs = computeForOptnPvSeqTrnstCtVs();
		Arrays.sort(_descendingForOptnPvSeqTrnstCtVs, _ForOptnCompare);
	}

	/** Strictly for cloning; fancify means to replace nulls by onMars. */
	private PvValueArrayPlus(final PvValueArrayPlus plus,
			final boolean fancify) {
		_planner = plus._planner;
		_pvValues = plus._pvValues == null ? null : plus._pvValues.clone();
		_equalToInitial = (BitSet) plus._equalToInitial.clone();
		if (_pvValues == null) {
			_posEvals = null;
			_descendingForOptnCtVs = null;
			_descendingForOptnPvTrnstCtVs = null;
			_descendingForOptnPvSeqTrnstCtVs = null;
			return;
		}
		_posEvals = plus._posEvals.clone();
		_descendingForOptnCtVs = plus._descendingForOptnCtVs.clone();
		_descendingForOptnPvTrnstCtVs =
				plus._descendingForOptnPvTrnstCtVs.clone();
		_descendingForOptnPvSeqTrnstCtVs =
				plus._descendingForOptnPvSeqTrnstCtVs.clone();
		if (fancify) {
			final int n = _pvValues.length;
			final PlannerModel plannerModel = _planner.getPlannerModel();
			final Model simModel = plannerModel.getSimModel();
			final Extent modelExtent = simModel.getExtent();
			for (int grandOrd = 0; grandOrd < n; ++grandOrd) {
				final PvValue pvValue = _pvValues[grandOrd];
				if (pvValue == null) {
					/** Make onMars. */
					final PatternVariable pv = plannerModel.grandOrdToPv(grandOrd);
					final MyStyle onMarsMyStyle = new MyStyle(modelExtent, pv);
					_pvValues[grandOrd] = new PvValue(pv, onMarsMyStyle);
				}
			}
		}
	}

	/** Clone any initial that is movable and is its PV's initial. */
	private PvValueArrayPlus(final PvValueArrayPlus plus) {
		_planner = plus._planner;
		_pvValues = plus._pvValues == null ? null : plus._pvValues.clone();
		_equalToInitial = (BitSet) plus._equalToInitial.clone();
		if (_pvValues == null) {
			_posEvals = null;
			_descendingForOptnCtVs = null;
			_descendingForOptnPvTrnstCtVs = null;
			_descendingForOptnPvSeqTrnstCtVs = null;
			return;
		}
		_posEvals = plus._posEvals.clone();
		_descendingForOptnCtVs = plus._descendingForOptnCtVs.clone();
		_descendingForOptnPvTrnstCtVs =
				plus._descendingForOptnPvTrnstCtVs.clone();
		_descendingForOptnPvSeqTrnstCtVs =
				plus._descendingForOptnPvSeqTrnstCtVs.clone();
		final int n = _pvValues.length;
		final PlannerModel plannerModel = _planner.getPlannerModel();
		for (int grandOrd = 0; grandOrd < n; ++grandOrd) {
			final PatternVariable pv = plannerModel.grandOrdToPv(grandOrd);
			if (pv.getUserFrozenPvValue() != null) {
				continue;
			}
			final PvValue pvValue = _pvValues[grandOrd];
			if (pvValue != null && pv.getInitialPvValue() == pvValue) {
				final MyStyle myStyle = pvValue.getMyStyle();
				final PvValue pvValueClone = new PvValue(pv, myStyle);
				_pvValues[grandOrd] = pvValueClone;
			}
		}
	}

	public PvValueArrayPlus cloneInitialNonFrozens() {
		return new PvValueArrayPlus(this);
	}

	public ArrayList<PvValue[]> computeBirdsNests() {
		final PlannerModel plannerModel = _planner.getPlannerModel();
		final int nPttrnVbls = plannerModel.getNPttrnVbls();
		final ArrayList<PvValue[]> birdsNests = new ArrayList<>();
		final int nCtVs = _descendingForOptnCtVs.length;
		for (int k0 = 0; k0 < nCtVs; ++k0) {
			final CtV ctV = _descendingForOptnCtVs[k0];
			if (ctV.getCtType() != CtType.OVL || ctV._forReportsV <= 0d) {
				continue;
			}
			final OvlCtV ovlCtV = (OvlCtV) ctV;
			final PvValue pvValue0 = ovlCtV._pvValue0;
			final PvValue pvValue1 = ovlCtV._pvValue1;
			final PatternVariable pv0 = pvValue0.getPv();
			final int grandOrd0 = pv0.getGrandOrd();
			final PatternVariable pv1 = pvValue1.getPv();
			final int grandOrd1 = pv1.getGrandOrd();

			/** Adjust birdsNests. */
			PvValue[] birdsNest0 = null;
			PvValue[] birdsNest1 = null;
			for (final PvValue[] birdsNest : birdsNests) {
				/**
				 * If we haven't found the birdsNest containing pv0, and birdsNest
				 * has pv0, then birdsNest0 is birdsNest.
				 */
				if ((birdsNest0 == null) && (birdsNest[grandOrd0] != null)) {
					birdsNest0 = birdsNest;
				}
				if ((birdsNest1 == null) && (birdsNest[grandOrd1] != null)) {
					birdsNest1 = birdsNest;
				}
				if ((birdsNest0 != null) && (birdsNest1 != null)) {
					break;
				}
			}
			if (birdsNest0 != null) {
				if (birdsNest1 != null) {
					if (birdsNest0 != birdsNest1) {
						/** Must combine. */
						for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
							if (birdsNest1[grandOrd] != null) {
								birdsNest0[grandOrd] = birdsNest1[grandOrd];
							}
						}
						birdsNests.remove(birdsNest1);
					}
				} else {
					/**
					 * birdsNest0 is not null, birdsNest1 is null, so put pvValue1
					 * into birdsNest0, and we're done.
					 */
					birdsNest0[grandOrd1] = pvValue1;
				}
			} else if (birdsNest1 != null) {
				/** Likewise */
				birdsNest1[grandOrd0] = pvValue0;
			} else {
				/** Both are null; start a new BirdsNest. */
				final PvValue[] birdsNest = new PvValue[nPttrnVbls];
				Arrays.fill(birdsNest, null);
				birdsNest[grandOrd0] = pvValue0;
				birdsNest[grandOrd1] = pvValue1;
				birdsNests.add(birdsNest);
			}
		}

		/**
		 * Order partition by decreasing size so that we do the big BirdsNests
		 * first.
		 */
		final int nBirdsNest = birdsNests.size();
		for (int k0 = 0; k0 < nBirdsNest; ++k0) {
			PvValue[] birdsNest0 = birdsNests.get(k0);
			int n0 = 0;
			for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
				if (birdsNest0[grandOrd] != null) {
					++n0;
				}
			}
			for (int k1 = k0 + 1; k1 < nBirdsNest; ++k1) {
				final PvValue[] birdsNest1 = birdsNests.get(k1);
				int n1 = 0;
				for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
					if (birdsNest1[grandOrd] != null) {
						++n1;
					}
				}
				if (n1 > n0) {
					birdsNests.set(k0, birdsNest1);
					birdsNests.set(k1, birdsNest0);
					birdsNest0 = birdsNest1;
				}
			}
		}
		return birdsNests;
	}

	public boolean isFeasible() {
		if (_descendingForOptnCtVs != null &&
				_descendingForOptnCtVs.length > 0) {
			if (_descendingForOptnCtVs[0]._forOptnV > 0d) {
				return false;
			}
		}
		if (_descendingForOptnPvTrnstCtVs != null &&
				_descendingForOptnPvTrnstCtVs.length > 0) {
			if (_descendingForOptnPvTrnstCtVs[0]._forOptnV > 0d) {
				return false;
			}
		}
		if (_descendingForOptnPvSeqTrnstCtVs != null &&
				_descendingForOptnPvSeqTrnstCtVs.length > 0) {
			if (_descendingForOptnPvSeqTrnstCtVs[0]._forOptnV > 0d) {
				return false;
			}
		}
		return true;
	}

	public char getFeasibleChar() {
		return isFeasible() ? Constants._IsMemberOf : Constants._IsNotMemberOf;
	}

	public PosEvaluation getPosEval(final PosFunction posFunction) {
		final EvalType evalType = posFunction._evalType;
		PosEvaluation posEval = _posEvals[evalType.ordinal()];
		if (posEval == null) {
			synchronized (_posEvals) {
				posEval = _posEvals[evalType.ordinal()];
				if (posEval == null) {
					posEval = posFunction.computeEvaluation(this);
					_posEvals[evalType.ordinal()] = posEval;
				}
			}
		}
		return posEval;
	}

	public PvValue getPvValue(final int grandOrd) {
		return _pvValues[grandOrd];
	}

	public double getPos(final PosFunction posFunction) {
		final PosEvaluation eval = getPosEval(posFunction);
		return eval == null ? Double.NaN : eval._pos;
	}

	public CtV getWorstForOptnCtV() {
		final int n =
				_descendingForOptnCtVs == null ? 0 : _descendingForOptnCtVs.length;
		for (int k = 0; k < n; ++k) {
			return _descendingForOptnCtVs[k];
		}
		return null;
	}

	/** Routines computing Ovl violations. */

	/** The 1st 3 Ovl routines are used exclusively for optimization. */
	final private static double _VERY_CLEAR_OVL_FOR_OPTN = 0d;

	public boolean hasClearOvl() {
		final int n = _descendingForOptnCtVs.length;
		for (int k = 0; k < n; ++k) {
			final CtV ctV = _descendingForOptnCtVs[k];
			if (ctV.getCtType()._isOvl) {
				final double violation = ctV._forOptnV;
				/** If the worst one is at most 0, they're all clear. */
				return violation <= 0d;
			}
		}
		return true;
	}

	public boolean hasVeryClearOvl() {
		final int n = _descendingForOptnCtVs.length;
		for (int k = 0; k < n; ++k) {
			final CtV ctV = _descendingForOptnCtVs[k];
			if (ctV.getCtType()._isOvl) {
				final double violation = ctV._forOptnV;
				return violation < -_VERY_CLEAR_OVL_FOR_OPTN;
			}
		}
		return true;
	}

	public boolean pvIsVeryClear(final PatternVariable pvIn) {
		final int n = _descendingForOptnCtVs.length;
		for (int k = 0; k < n; ++k) {
			final CtV ctV = _descendingForOptnCtVs[k];
			if (ctV.getCtType()._isOvl) {
				final PvValue pvValue0 = ctV._pvValue0;
				final PvValue pvValue1 = ctV._pvValue1;
				final PatternVariable pv0 = pvValue0.getPv();
				final PatternVariable pv1 = pvValue1.getPv();
				if (pvIn == pv0 || pvIn == pv1) {
					final double violation = ctV._forOptnV;
					return violation < -_VERY_CLEAR_OVL_FOR_OPTN;
				}
			}
		}
		return true;
	}

	public double getForOptnOvlV(final PatternVariable pvIn) {
		double ttlV = 0d;
		for (final CtV ctV : _descendingForOptnCtVs) {
			if (ctV instanceof OvlCtV) {
				final PvValue pvValue0 = ctV._pvValue0;
				final PatternVariable pv0 = pvValue0.getPv();
				final PvValue pvValue1 = ctV._pvValue1;
				final PatternVariable pv1 = pvValue1.getPv();
				if (pv0 == pvIn || pv1 == pvIn || pvIn == null) {
					if (ctV._forOptnV > 0d) {
						ttlV += ctV._forOptnV;
					}
				}
			}
		}
		return ttlV;
	}

	public double getForReportsTtlOvlV(final PatternVariable pv,
			final boolean countTheOnesThatMayOverlap) {
		final OvlCtV[] ovlCtVs =
				computeForReportsOvlCtVs(countTheOnesThatMayOverlap);
		double ttlV = 0d;
		for (final OvlCtV ctV : ovlCtVs) {
			final PvValue pvValue0 = ctV._pvValue0;
			final PatternVariable pv0 = pvValue0.getPv();
			final PvValue pvValue1 = ctV._pvValue1;
			final PatternVariable pv1 = pvValue1.getPv();
			if (pv == null || pv == pv0 || pv == pv1) {
				final double forReportsV = ctV._forReportsV;
				if (forReportsV > 0d) {
					ttlV += forReportsV;
				}
			}
		}
		return ttlV;
	}

	private OvlCtV[] computeForOptnOvlCtVs() {
		final PlannerModel plannerModel = _planner.getPlannerModel();
		final int nPttrnVbls = plannerModel.getNPttrnVbls();
		final ArrayList<OvlCtV> ovlCtVList = new ArrayList<>();
		for (int grandOrd0 = 0; grandOrd0 < nPttrnVbls; ++grandOrd0) {
			final PvValue pvValue0 = _pvValues[grandOrd0];
			if (pvValue0 == null || pvValue0.onMars()) {
				continue;
			}
			final PatternVariable pv0 = pvValue0.getPv();
			if (!pv0.isActive()) {
				continue;
			}
			final boolean isFrozen0 = pv0.getPermanentFrozenPvValue() != null;
			for (int grandOrd1 = grandOrd0 + 1; grandOrd1 < nPttrnVbls;
					++grandOrd1) {
				final PvValue pvValue1 = _pvValues[grandOrd1];
				if (pvValue1 == null || pvValue1.onMars()) {
					continue;
				}
				final PatternVariable pv1 = pvValue1.getPv();
				if (!pv1.isActive()) {
					continue;
				}
				final boolean isFrozen1 = pv1.getPermanentFrozenPvValue() != null;
				if ((isFrozen0 && isFrozen1) || plannerModel.mayOverlap(pv0, pv1, /* forOptn= */true)) {
					continue;
				}
				final OvlCtV ovlCtV = new OvlCtV(pvValue0, pvValue1);
				if (!Double.isNaN(ovlCtV._forOptnV)) {
					ovlCtVList.add(ovlCtV);
				}
			}
		}
		final OvlCtV[] ovlCtVsArray =
				ovlCtVList.toArray(new OvlCtV[ovlCtVList.size()]);
		return ovlCtVsArray;
	}

	private OvlCtV[] computeForReportsOvlCtVs(
			final boolean countTheOnesThatMayOverlap) {
		final PlannerModel plannerModel = _planner.getPlannerModel();
		final int nPttrnVbls = _pvValues == null ? 0 : _pvValues.length;
		final ArrayList<OvlCtV> ovlCtVList = new ArrayList<>();
		for (int grandOrd0 = 0; grandOrd0 < nPttrnVbls; ++grandOrd0) {
			final PvValue pvValue0 = _pvValues[grandOrd0];
			if (pvValue0 == null || pvValue0.onMars()) {
				continue;
			}
			final PatternVariable pv0 = pvValue0.getPv();
			for (int grandOrd1 = grandOrd0 + 1; grandOrd1 < nPttrnVbls;
					++grandOrd1) {
				final PvValue pvValue1 = _pvValues[grandOrd1];
				if (pvValue1 == null || pvValue1.onMars()) {
					continue;
				}
				final PatternVariable pv1 = pvValue1.getPv();
				if (!countTheOnesThatMayOverlap &&
						plannerModel.mayOverlap(pv0, pv1, /* forOptn= */false)) {
					continue;
				}
				final OvlCtV ovlCtV = new OvlCtV(pvValue0, pvValue1);
				if (!Double.isNaN(ovlCtV._forReportsV)) {
					ovlCtVList.add(ovlCtV);
				}
				ovlCtVList.add(ovlCtV);
			}
		}
		return ovlCtVList.toArray(new OvlCtV[ovlCtVList.size()]);
	}

	public double[] getForOptnOvlPlusAndMinus(final PatternVariable pv) {
		double posOvl = 0d;
		double negOvl = 0d;
		for (final CtV ctV : _descendingForOptnCtVs) {
			if (ctV instanceof OvlCtV) {
				if (pv == null || ctV._pvValue0.getPv() == pv) {
					final double v = ctV._forOptnV;
					if (v >= 0d) {
						posOvl += v;
					} else {
						negOvl += v;
					}
				}
			}
		}
		return new double[] { posOvl, negOvl };
	}

	/** Similar for PvTrnst. */
	public double getForOptnPvTrnstV(final PatternVariable pv) {
		final PvSeq pvSeq = pv == null ? null : pv.getPvSeq();
		if (pv != null && pvSeq == null) {
			return 0d;
		}
		double ttlV = 0d;
		for (final PvTrnstCtV pvTrnstCtV : _descendingForOptnPvTrnstCtVs) {
			final PvValue pvValue0 = pvTrnstCtV._pvValue0;
			if (pv != null && pvValue0.getPv() != pv) {
				continue;
			}
			ttlV += pvTrnstCtV._forOptnV;
			if (pv != null) {
				return ttlV;
			}
		}
		return ttlV;
	}

	public double getForReportsTtlPvTrnstV(final PatternVariable pv) {
		final PvSeq pvSeq = pv == null ? null : pv.getPvSeq();
		if (pv != null && pvSeq == null) {
			return 0d;
		}
		double ttlV = 0d;
		final PvTrnstCtV[] pvTrnstCtVs = computeForReportsPvTrnstCtVs();
		for (final PvTrnstCtV pvTrnstCtV : pvTrnstCtVs) {
			final PvValue pvValue0 = pvTrnstCtV._pvValue0;
			if (pv != null && pvValue0.getPv() != pv) {
				continue;
			}
			ttlV += Math.max(0d, pvTrnstCtV._forReportsV);
			if (pv != null) {
				return ttlV;
			}
		}
		return ttlV;
	}

	public double[] getPvTrnstPlusAndMinus(final PatternVariable pv,
			final boolean forOptn) {
		double posPvSeq = 0d;
		double negPvSeq = 0d;
		final PvTrnstCtV[] pvTrnstCtVs = forOptn ?
				_descendingForOptnPvTrnstCtVs : computeForReportsPvTrnstCtVs();
		final int nPvTrnstCtVs = pvTrnstCtVs == null ? 0 : pvTrnstCtVs.length;
		for (int k0 = 0; k0 < nPvTrnstCtVs; ++k0) {
			final PvTrnstCtV pvTrnstCtV = pvTrnstCtVs[k0];
			final PvValue pvValue = pvTrnstCtV._pvValue0;
			final boolean useThisOne = pv == null || pvValue.getPv() == pv;
			if (useThisOne) {
				final double v =
						forOptn ? pvTrnstCtV._forOptnV : pvTrnstCtV._forReportsV;
				if (v >= 0d) {
					posPvSeq += v;
				} else {
					negPvSeq += v;
				}
			}
		}
		return new double[] { posPvSeq, negPvSeq };
	}

	final PvTrnstCtV[] computeForOptnPvTrnstCtVs() {
		return computePvTrnstCtVs(/* forOptn= */true);
	}

	final PvTrnstCtV[] computeForReportsPvTrnstCtVs() {
		return computePvTrnstCtVs(/* forOptn= */false);
	}

	private PvTrnstCtV[] computePvTrnstCtVs(final boolean forOptn) {
		final PlannerModel plannerModel = _planner.getPlannerModel();
		final int nPttrnVbls = plannerModel.getNPttrnVbls();
		final ArrayList<PvTrnstCtV> pvTrnstCtVList = new ArrayList<>();
		final PvAndPvSeqTrnstConstraintArray pvAndPvSeqTrnstConstraintArray =
				new PvAndPvSeqTrnstConstraintArray(_planner, _pvValues);
		GRAND_ORD_LOOP: for (int grandOrd = 0; grandOrd < nPttrnVbls;
				++grandOrd) {
			final PvValue pvValue = _pvValues[grandOrd];
			final PatternVariable pv = pvValue == null ? null : pvValue.getPv();
			final PvSeq pvSeq = pv == null ? null : pv.getPvSeq();
			if (pvValue == null || pvValue.onMars() || !pv.isActive() ||
					pvSeq == null) {
				continue;
			}
			if (forOptn && pv.getPermanentFrozenPvValue() != null) {
				/**
				 * Since I am perm-frozen, if my first isActive, offMars predecessor
				 * is also perm-frozen, there's nothing to be done for optimization.
				 */
				final int ordWithinPvSeq = pv.getOrdWithinPvSeq();
				for (int k = ordWithinPvSeq - 1; k >= 0; --k) {
					final PatternVariable pvsPv =
							pvSeq.getPttrnVbl(ordWithinPvSeq - 1);
					if (pvsPv.isActive()) {
						final int pvsGrandOrd = pvsPv.getGrandOrd();
						final PvValue pvsPvValue = _pvValues[pvsGrandOrd];
						if (!pvsPvValue.onMars()) {
							if (pvsPv.getPermanentFrozenPvValue() != null) {
								continue GRAND_ORD_LOOP;
							}
							/**
							 * Previous one is not permanently frozen. There is a
							 * constraint.
							 */
							break;
						}
					}
				}
			}
			final double[] forOptnAndReports =
					pvAndPvSeqTrnstConstraintArray._pvToOptnAndReports[grandOrd]
							.clone();
			pvTrnstCtVList.add(new PvTrnstCtV(pvValue, forOptnAndReports));
		}
		final PvTrnstCtV[] pvTrnstCtVsArray =
				pvTrnstCtVList.toArray(new PvTrnstCtV[pvTrnstCtVList.size()]);
		return pvTrnstCtVsArray;
	}

	/** Similar for PvSeqTrnst. */
	public double getForOptnPvSeqTrnstV(final PvSeq pvSeqIn) {
		double ttlV = 0d;
		for (final PvSeqTrnstCtV pvSeqTrnstCtV : _descendingForOptnPvSeqTrnstCtVs) {
			final PvSeq pvSeq = pvSeqTrnstCtV._pvSeq;
			if (pvSeqIn != null && pvSeq != pvSeqIn) {
				continue;
			}
			ttlV += pvSeqTrnstCtV._forOptnV;
			if (pvSeqIn != null) {
				return ttlV;
			}
		}
		return ttlV;
	}

	public double getForReportsTtlPvSeqTransitV(final PvSeq pvSeqIn) {
		double ttlV = 0d;
		final PvSeqTrnstCtV[] pvSeqTrnstCtVs =
				computeForReportsPvSeqTrnstCtVs();
		for (final PvSeqTrnstCtV pvSeqTrnstCtV : pvSeqTrnstCtVs) {
			final PvSeq pvSeq = pvSeqTrnstCtV._pvSeq;
			if (pvSeqIn != null && pvSeq != pvSeqIn) {
				continue;
			}
			ttlV += Math.max(0d, pvSeqTrnstCtV._forReportsV);
			if (pvSeqIn != null) {
				return ttlV;
			}
		}
		return ttlV;
	}

	private PvSeqTrnstCtV[] computePvSeqTrnstCtVs(final boolean forOptn) {
		final ArrayList<PvSeqTrnstCtV> pvSeqTrnstCtVList = new ArrayList<>();
		final PvAndPvSeqTrnstConstraintArray pvSeqConstraintArray =
				new PvAndPvSeqTrnstConstraintArray(_planner, _pvValues);
		final PlannerModel plannerModel = _planner.getPlannerModel();
		final int nPvSeqs = plannerModel.getNPvSeqs();
		for (int pvSeqOrd = 0; pvSeqOrd < nPvSeqs; ++pvSeqOrd) {
			final PvSeq pvSeq = plannerModel.getPvSeq(pvSeqOrd);
			boolean canDoSomething = false;
			final int nMine = pvSeq == null ? 0 : pvSeq.getNMyPttrnVbls();
			if (nMine > 0) {
				for (int k = nMine - 1; k >= 0; --k) {
					final PatternVariable pv = pvSeq.getPttrnVbl(k);
					final PvValue pvValue = _pvValues[pv.getGrandOrd()];
					if (!pv.isActive() || pvValue.onMars()) {
						continue;
					}
					canDoSomething = pv.getPermanentFrozenPvValue() == null;
					break;
				}
				if (!canDoSomething) {
					for (int k = nMine - 1; k >= 0; --k) {
						final PatternVariable pv = pvSeq.getPttrnVbl(k);
						final PvValue pvValue = _pvValues[pv.getGrandOrd()];
						if (!pv.isActive() || pvValue.onMars()) {
							continue;
						}
						canDoSomething = pv.getPermanentFrozenPvValue() == null;
						break;
					}
				}
			}
			if (forOptn && !canDoSomething) {
				continue;
			}
			final PvSeqTrnstCtV pvSeqTrnstCtV = new PvSeqTrnstCtV(pvSeq,
					pvSeqConstraintArray._pvSeqToOptnAndReports[pvSeqOrd]);
			pvSeqTrnstCtVList.add(pvSeqTrnstCtV);
		}
		final PvSeqTrnstCtV[] pvSeqTrnstCtVsArray = pvSeqTrnstCtVList
				.toArray(new PvSeqTrnstCtV[pvSeqTrnstCtVList.size()]);
		return pvSeqTrnstCtVsArray;
	}

	private PvSeqTrnstCtV[] computeForOptnPvSeqTrnstCtVs() {
		return computePvSeqTrnstCtVs(/* forOptn= */true);
	}

	private PvSeqTrnstCtV[] computeForReportsPvSeqTrnstCtVs() {
		return computePvSeqTrnstCtVs(/* forOptn= */false);
	}

	public double[] getPvSeqTrnstPlusAndMinus(final PvSeq pvSeq,
			final boolean forOptn) {
		double posPvSeq = 0d;
		double negPvSeq = 0d;
		final PvSeqTrnstCtV[] pvSeqTrnstCtVs =
				forOptn ? _descendingForOptnPvSeqTrnstCtVs :
						computeForReportsPvSeqTrnstCtVs();
		final int nPvSeqTrnstCtVs =
				pvSeqTrnstCtVs == null ? 0 : pvSeqTrnstCtVs.length;
		for (int k0 = 0; k0 < nPvSeqTrnstCtVs; ++k0) {
			final PvSeqTrnstCtV pvSeqTrnstCtV = pvSeqTrnstCtVs[k0];
			final PvSeq pvSeq0 = pvSeqTrnstCtV._pvSeq;
			final boolean useThisOne = pvSeq == null || pvSeq0 == pvSeq;
			if (useThisOne) {
				final double v =
						forOptn ? pvSeqTrnstCtV._forOptnV : pvSeqTrnstCtV._forReportsV;
				if (v >= 0d) {
					posPvSeq += v;
				} else {
					negPvSeq += v;
				}
			}
		}
		return new double[] { posPvSeq, negPvSeq };
	}

	/** Utility for PqRefiner. */
	public CtV[] getCriticalForOptnCtVs(final RefinementStage stage) {
		final ArrayList<CtV> ctVList = new ArrayList<>();
		final int n = _descendingForOptnCtVs.length;
		for (int k = 0; k < n; ++k) {
			final CtV ctV = _descendingForOptnCtVs[k];
			final CtType ctType = ctV.getCtType();
			if (stage == RefinementStage.CLR_OVL && ctType._isOvl) {
				ctVList.add(ctV);
			}
		}
		return ctVList.toArray(new CtV[ctVList.size()]);
	}

	public CtV[] getDescendingForOptnCtVs() {
		return _descendingForOptnCtVs;
	}

	public double getForReportsTtlCtV(
			final boolean countTheOnesThatMayOverlap) {
		final double ovlV =
				getForReportsTtlOvlV(/* pv= */null, countTheOnesThatMayOverlap);
		final double pvTrnstV = getForReportsTtlPvTrnstV(/* pv= */null);
		final double pvSeqTrnstV =
				getForReportsTtlPvSeqTransitV(/* pvSeq= */null);
		return ovlV + pvTrnstV + pvSeqTrnstV;
	}

	public String getSummaryString() {
		boolean firstOne = true;
		String s = "Ovls[";
		for (final CtV ctV : _descendingForOptnCtVs) {
			if (!(ctV instanceof OvlCtV)) {
				continue;
			}
			final OvlCtV ovlCtV = (OvlCtV) ctV;
			final double raw = ovlCtV._forReportsV;
			if (raw <= 0d) {
				break;
			}
			final String id1 = ovlCtV._pvValue0.getPv().getId();
			final String id2 = ovlCtV._pvValue1.getPv().getId();
			if (!firstOne) {
				s += ", ";
			}
			s += String.format("{%s*%s}Raw[%f]", id1, id2, raw);
			firstOne = false;
		}
		firstOne = true;
		for (final PosFunction.EvalType evalType : PosFunction.EvalType
				.values()) {
			final PosEvaluation posEval = _posEvals[evalType.ordinal()];
			if (posEval != null) {
				final String evalName = evalType.name();
				final double posValue = posEval._pos;
				if (!firstOne) {
					s += ", ";
				}
				s += String.format("[%s,%g]", evalName, posValue);
				firstOne = false;
			}
		}
		return s;
	}

	public String getString1(final String caption) {
		String s = caption + ":";
		final int n = _pvValues == null ? 0 : _pvValues.length;
		for (int k = 0; k < n; ++k) {
			final PvValue pvValue = _pvValues[k];
			if (pvValue == null) {
				s += "\n\tNULL PvValue for grandOrd(@1) " + k;
			} else {
				final String string1 = pvValue.getString();
				s += "\n" + string1;
			}
		}
		return s;
	}

	public String getString2(final String caption) {
		String s = caption + ":";
		final int n = _pvValues == null ? 0 : _pvValues.length;
		for (int k = 0; k < n; ++k) {
			final PvValue pvValue = _pvValues[k];
			s += "\n\t";
			if (pvValue == null) {
				s += "NULL PvValue for grandOrd(@2) " + k;
			} else {
				s += "\n" + pvValue.getExcLoopString();
			}
		}
		return s;
	}

	public String getString3(final String caption) {
		String s = caption + ":";
		final int n = _pvValues == null ? 0 : _pvValues.length;
		for (int k = 0; k < n; ++k) {
			final PvValue pvValue = _pvValues[k];
			if (pvValue == null) {
				s += "\n\tNULL PvValue for grandOrd(@3) " + k;
			} else {
				final PatternVariable pv = pvValue.getPv();
				s += "\n" + pv.getNameId();
				final MyStyle myStyle = pvValue.getMyStyle();
				if (myStyle != null) {
					final double rawSearchKts = pvValue.getPv().getRawSearchKts();
					final double minTsNmi = pvValue.getPv().getMinTsNmi();
					final String theirStyleString =
							myStyle.getTheirStyleString(rawSearchKts, minTsNmi);
					s += " " + theirStyleString;
				}
				s += pvValue.getSortieString();
			}
		}
		return s;
	}

	public PvValue[] getCopyOfPvValues() {
		if (_pvValues == null) {
			return null;
		}
		return _pvValues.clone();
	}

	public PvValue[] getNonNullPvValues() {
		if (_pvValues == null) {
			return new PvValue[0];
		}
		final int nPttrnVbls = _pvValues.length;
		PvValue[] nonNullPvValues = null;
		for (int iPass = 0; iPass < 2; ++iPass) {
			int nNonNulls = 0;
			for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
				final PvValue pvValue = _pvValues[grandOrd];
				if (pvValue != null) {
					if (iPass == 1) {
						nonNullPvValues[nNonNulls] = pvValue;
					}
					++nNonNulls;
				}
			}
			if (iPass == 0) {
				nonNullPvValues = new PvValue[nNonNulls];
			}
		}
		return nonNullPvValues;
	}

	public boolean hasPvValueArray() {
		return _pvValues != null;
	}

	@Override
	public PvValueArrayPlus clone() {
		return new PvValueArrayPlus(this, /* fancify= */false);
	}

	public PvValueArrayPlus cloneAndFancify() {
		return new PvValueArrayPlus(this, /* fancify= */true);
	}

	public void clearSmallSamplePosFunctionEvals() {
		_posEvals[PosFunction.EvalType.FXD_SAMPLE_FT.ordinal()] = null;
		_posEvals[PosFunction.EvalType.FXD_SAMPLE_NFT.ordinal()] = null;
	}

	public static void main(final String[] args) {
		final HermitePlus hermitePlus = _HermitePlusOvl;
		double xOfMaxY = Double.NaN;
		double xOfMinY = Double.NaN;
		double maxY = Double.NEGATIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double oldY = Double.NEGATIVE_INFINITY;
		String s = "ListPlot[{";
		boolean doneOne = false;
		for (int k = -10001; k <= 10001; ++k) {
			final double thisX = k * 0.0001;
			final double thisY = hermitePlus.value(thisX);
			if (k == -1001 || k == 1001 || 200 * (k / 200) == k) {
				final String introString = doneOne ? "," : "";
				s += String.format("%s{%f,%f}", introString, thisX, thisY);
				doneOne = true;
			}
			if (thisY > maxY) {
				maxY = thisY;
				xOfMaxY = thisX;
			}
			if (thisY < minY) {
				minY = thisY;
				xOfMinY = thisX;
			}
			if (thisY < oldY) {
				System.out.printf("\n@@@ thisY went down! [x,oldY,y] = [%f,%f,%f]",
						thisX, oldY, thisY);
			}
			oldY = thisY;
		}
		s += "}, Joined → True]";
		System.out.printf("\n%s", s);
		System.out.printf("\n[x,y(min)] = [%f,%f]", xOfMinY, minY);
		System.out.printf("\n[x,y(max)] = [%f,%f]", xOfMaxY, maxY);
	}

	/** activeSet = null means "consider all." */
	public boolean isComplete(final PatternVariable[] activeSet) {
		final int nPttrnVbls = _planner.getPlannerModel().getNPttrnVbls();
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			if (activeSet == null || activeSet[grandOrd] != null) {
				final PvValue pvValue = _pvValues[grandOrd];
				if (pvValue == null || pvValue.onMars()) {
					return false;
				}
			}
		}
		return true;
	}

	public int[] computePerturbableGrandOrds() {
		final PlannerModel plannerModel = _planner.getPlannerModel();
		final int nPttrnVbls = plannerModel.getNPttrnVbls();
		final PqSolver pqSolver = _planner.getSolversManager().getPqSolver();
		final boolean freezeInitialPvValues = pqSolver.freezeInitialPvValues();
		final ArrayList<Integer> perturbableGrandOrdList = new ArrayList<>();
		final ArrayList<Integer> exceptions = new ArrayList<>();
		GRANDORD0_LOOP: for (int grandOrd0 = 0; grandOrd0 < nPttrnVbls;
				++grandOrd0) {
			final PatternVariable pv0 = plannerModel.grandOrdToPv(grandOrd0);
			final PvValue pvValue0 = _pvValues[grandOrd0];
			if (pvValue0 == null || pvValue0.onMars() ||
					pv0.getUserFrozenPvValue() != null || !pv0.isActive()) {
				/** Straightforwardly not perturbable. */
				continue;
			}
			/** grandOrd0 appears to be perturbable. */
			if (!freezeInitialPvValues || pvValue0 != pv0.getInitialPvValue()) {
				/** grandOrd0 is perturbable. */
				perturbableGrandOrdList.add(grandOrd0);
				continue;
			}
			/**
			 * grandOrd0 appears to be an exception. If it is, then it is not
			 * perturbable. But it IS perturbable if there is some other exception
			 * that grandOrd0 conflicts with. In this case, we must make grandOrd0
			 * perturbable so it can skip away from the other "exceptional"
			 * grandOrd.
			 */
			final int nExceptions = exceptions.size();
			for (int k = 0; k < nExceptions; ++k) {
				final int grandOrd1 = exceptions.get(k);
				final PatternVariable pv1 = plannerModel.grandOrdToPv(grandOrd1);
				if (plannerModel.mayOverlap(pv0, pv1, /* forOptn= */true)) {
					/** pv1 is irrelevant. */
					continue;
				}
				final PvValue pvValue1 = _pvValues[grandOrd1];
				final OvlCtV ovlCtV = new OvlCtV(pvValue0, pvValue1);
				if (!(ovlCtV.getForOptnV() > 0d)) {
					continue;
				}
				/**
				 * We must make grandOrd0 perturbable so it can skip out of the way
				 * of grandOrd1.
				 */
				perturbableGrandOrdList.add(grandOrd0);
				continue GRANDORD0_LOOP;
			}
			/**
			 * We were never forced to make grandOrd0 perturbable. Hence, it is
			 * not, and is therefore an exception.
			 */
			exceptions.add(grandOrd0);
		}
		final int nPerturbables = perturbableGrandOrdList.size();
		final int[] perturbableGrandOrds = new int[nPerturbables];
		for (int k = 0; k < nPerturbables; ++k) {
			perturbableGrandOrds[k] = perturbableGrandOrdList.get(k);
		}
		return perturbableGrandOrds;
	}

	public boolean iAmBetterForOptn(final PatternVariable[] activeSet,
			final PvValueArrayPlus plus) {
		if (plus == null) {
			return true;
		}
		final boolean plusIsFeasible = plus.isFeasible();
		final boolean iAmFeasible = isFeasible();
		if (plusIsFeasible != iAmFeasible) {
			return iAmFeasible;
		}
		final PosFunction posFunctionToUse =
				iAmFeasible ? _planner.getPosFunctionForFbleOptn() :
						_planner.getPosFunctionForInfblOptn();
		final boolean hisIsComplete = plus.isComplete(activeSet);
		final boolean iAmComplete = isComplete(activeSet);
		if (!iAmFeasible) {
			/** Both are infeasible; prefer complete. */
			if (hisIsComplete != iAmComplete) {
				return iAmComplete;
			}
			/**
			 * Both are infeasible and either both are complete or both are
			 * incomplete. Appeal to POS.
			 */
			final CtV[] hisDescendingForOptnCtVs =
					plus.getDescendingForOptnCtVs();
			final CtV[] myDescendingForOptnCtVs = plus.getDescendingForOptnCtVs();
			final int nHis = hisDescendingForOptnCtVs.length;
			final int nMine = myDescendingForOptnCtVs.length;
			assert nHis == nMine : "Unequal Ct Lengths in Solver.";
			for (int k = 0; k < nHis; ++k) {
				final CtV hisCtV = hisDescendingForOptnCtVs[k];
				final CtV myCtV = myDescendingForOptnCtVs[k];
				final double hisForOptnV = hisCtV.getForOptnV();
				final double myForOptnV = myCtV.getForOptnV();
				if (hisForOptnV <= 0d && myForOptnV <= 0d) {
					break;
				}
				if (myForOptnV > hisForOptnV) {
					return false;
				}
				if (myForOptnV < hisForOptnV) {
					return true;
				}
			}
			final double hisPos = plus.getPos(posFunctionToUse);
			final double myPos = getPos(posFunctionToUse);
			if (Double.isNaN(myPos)) {
				return false;
			}
			if (Double.isNaN(hisPos)) {
				return true;
			}
			return myPos > hisPos;
		}

		/** Both are feasible. Appeal to Pos. */
		final double hisPos = plus.getPos(posFunctionToUse);
		final double myPos = getPos(posFunctionToUse);
		if (myPos > hisPos || hisPos > myPos) {
			return myPos > hisPos;
		}
		/** Prefer complete. */
		if (!hisIsComplete && iAmComplete) {
			return true;
		}
		return false;
	}

}
