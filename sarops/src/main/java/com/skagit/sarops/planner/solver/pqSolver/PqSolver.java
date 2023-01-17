package com.skagit.sarops.planner.solver.pqSolver;

import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.planner.posFunction.PosFunction;
import com.skagit.sarops.planner.posFunction.pvValueArrayPlus.PvValueArrayPlus;
import com.skagit.sarops.planner.solver.Solver;
import com.skagit.sarops.planner.solver.pqSolver.deconflicter.Deconflicter;
import com.skagit.sarops.planner.solver.pqSolver.pvPlacer.PvPlacerStatics;
import com.skagit.sarops.simCaseManager.SimCaseManager;

public class PqSolver extends Solver {
	private static final boolean _AlwaysMakeFromScratch = true;
	final private PqRefiner _pqRefiner;
	private PvValueArrayPlus _foreignPvValuePlus;

	public PqSolver(final Planner planner, final boolean isSolver0) {
		super(planner, "PqSolver", isSolver0);
		_foreignPvValuePlus = null;
		stopRefining();
		_pqRefiner = new PqRefiner(this);
	}

	@Override
	public JumpStyle jump(final String caption,
			final PosFunction ftPosFunction, final PosFunction nftPosFunction) {
		allowRefining();
		final SimCaseManager.SimCase simCase = _planner.getSimCase();
		final PvValueArrayPlus currentPlus = getCurrentPlus();
		final JumpStyle thisJumpStyle;
		final PvValueArrayPlus plusToStartWith;
		if (_foreignPvValuePlus != null) {
			/**
			 * Adjust to Active from something that was left there for us to pick
			 * up.
			 */
			setLastJump(PvPlacerStatics.adjustToActive(_foreignPvValuePlus,
					ftPosFunction, nftPosFunction));
			_foreignPvValuePlus = null;
			thisJumpStyle = JumpStyle.FOREIGN_REPLACEMENT;
			_nJumpsSinceLastImprovement = 0;
			plusToStartWith = null;
		} else if (currentPlus == null) {
			plusToStartWith = null;
			thisJumpStyle = JumpStyle.NO_PREVIOUS_SOLUTION;
		} else {
			switch (_lastJumpStyle) {
			case MODIFY_CURRENT:
				thisJumpStyle = JumpStyle.USE_JUMP_SEQUENCE;
				plusToStartWith = getLastJump();
				break;
			case MAKE_FROM_SCRATCH:
				/**
				 * Right now, we're always making from scratch. If we were to try
				 * MODIFY_CURRENT, we'd also set plusToStartWith to _lastJump.
				 */
				if (_AlwaysMakeFromScratch) {
					thisJumpStyle = JumpStyle.MAKE_FROM_SCRATCH;
					plusToStartWith = null;
				} else {
					thisJumpStyle = JumpStyle.MODIFY_CURRENT;
					plusToStartWith = currentPlus.cloneInitialNonFrozens();
				}
				break;
			case FOREIGN_REPLACEMENT:
			case NO_PREVIOUS_SOLUTION:
			case USE_JUMP_SEQUENCE:
			default:
				thisJumpStyle = JumpStyle.MAKE_FROM_SCRATCH;
				plusToStartWith = null;
				break;
			}
		}

		if (thisJumpStyle != JumpStyle.FOREIGN_REPLACEMENT) {
			final Deconflicter deconflicter =
					PvPlacerStatics.nextPlus(_planner, ftPosFunction, nftPosFunction,
							thisJumpStyle == JumpStyle.MAKE_FROM_SCRATCH ? null :
									plusToStartWith);
			setLastJump(deconflicter._outPlus);
			++_nJumpsSinceLastImprovement;
		}
		_lastJumpStyle = thisJumpStyle;
		increment(_lastJumpStyle);
		final int nLikeThis = getNLikeThis(_lastJumpStyle);
		_lastJumpString =
				String.format("[%s/%d]", _lastJumpStyle.getString(), nLikeThis);
		final PvValueArrayPlus lastJump = getLastJump();
		if (lastJump != null) {
			final String jumpString = lastJump.getString1(_lastJumpString);
			SimCaseManager.out(simCase, jumpString);
			setCurrentPlus(lastJump, _lastJumpString);
		} else {
			_lastJumpString = String.format(
					"LastJump should not be null here, but it is.  LastJumpStyle:%s",
					thisJumpStyle == null ? "No Jump Style" :
							thisJumpStyle.getString());
			SimCaseManager.out(simCase, _lastJumpString);
		}
		return thisJumpStyle;
	}

	public boolean freezeInitialPvValues() {
		if (getNJumps() > 1) {
			return false;
		}
		final PvValueArrayPlus currentPlus = getCurrentPlus();
		if ((currentPlus == null) || !currentPlus.isFeasible()) {
			return true;
		}
		final PlannerModel plannerModel = getPlanner().getPlannerModel();
		final PatternVariable[] activeSet = plannerModel.getActiveSet();
		if (!currentPlus.isComplete(activeSet)) {
			return true;
		}
		return false;
	}

	@Override
	protected void refine(final PosFunction ftPosFunction,
			final PosFunction nftPosFunction) {
		final SimCaseManager.SimCase simCase = _planner.getSimCase();
		if (!simCase.getKeepGoing() || !getKeepRefining()) {
			return;
		}
		/** Ba da Boom. */
		_pqRefiner.doPMoves(_planner, this, ftPosFunction, nftPosFunction,
				/* clearCtVOnly= */false);
	}

	public void runWith(final SimCaseManager.SimCase simCase,
			final PvValueArrayPlus pvValuePlus) {
		SimCaseManager.out(simCase, "Setting Foreign Solution");
		_foreignPvValuePlus = pvValuePlus;
		stopRefining();
	}

	@Override
	public void clearCtV(final PosFunction ftPosFunction,
			final PosFunction nftPosFunction) {
		_pqRefiner.doPMoves(_planner, this, ftPosFunction, nftPosFunction,
				/* clearCtVOnly= */true);
	}
}
