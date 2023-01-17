package com.skagit.sarops.planner.solver.pqSolver;

import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.plannerModel.PatternKind;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.util.patternUtils.DiscreteLpSpecToTs;
import com.skagit.sarops.util.patternUtils.DiscreteSsSpecToTs;
import com.skagit.sarops.util.patternUtils.MyStyle;
import com.skagit.sarops.util.patternUtils.PatternUtilStatics;
import com.skagit.util.Constants;
import com.skagit.util.MathX;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.TangentCylinder;

public class PvValuePerturber {
	final private static double _NmiToR = MathX._NmiToR;

	/**
	 * <pre>
	 * Suppose the first (search) leg is from top left to top right so that we
	 * have firstTurnRight = true.  Except for the #26, firstTurnRight is irrelevant.
	 * 1,2.   Increase the "along direction" by
	 *        a. Decreasing the left-hand side (IncAlong1) or
	 *        b. Increasing the right-hand side (IncAlong2).
	 * 3,4.   Decrease the "along direction" by
	 *        a. Increasing the left-hand side (DecAlong1) or
	 *        b. Decreasing the right-hand side (DecAlong2).
	 * 5,6.   Increase the across direction by
	 *        a. Increasing the top border (IncAcross1).
	 *        b. Decreasing the bottom border (IncAcross2) or
	 * 7,8.   Decrease the across direction by
	 *        a. Decreasing the top border (DecAcross1).
	 *        b. Increasing the bottom border (DecAcross2) or
	 * 9,10.  Expand or contract both sides simultaneously (Expand or Contract)
	 * 11.    Shift to the right (MoveAlong).
	 * 12.    Shift to the left (MoveNegAlong).
	 * 13.    Shift down (MoveAcross).
	 * 14.    Shift up (MoveNegAcross).
	 * 19.    Shift cw the heading of the first leg (IncFirstLegHdg).
	 * 20.    Shift ccw the heading of the first leg (DecFirstLegHdg).
	 * 21.    Shift cw the heading of the first leg by 90, but keep the same box as much as possible (Add90).
	 * 22.    Shift ccw the heading of the first leg by 90, but keep the same box as much as possible (Sub90).
	 * 23.    Rotate the entire box 90 degrees cw (IncHdg90).
	 * 24.    Rotate the entire box 90 degrees ccw (DecHdg90).
	 * 25.    Start at the opposite corner (Rotate180).
	 * 26.    Toggle firstTurnRight (ToggleFirstTurnRight).
	 *
	 * For VS, only the Shifts, Rotations, and Toggle apply.
	 * We add the following 4 for SS and VS:
	 * 15.    Shift to the right and down (MoveAlongAndAcross).
	 * 16.    Shift to the right and up (MoveAlongAndNegAcross).
	 * 17.    Shift to the left and down (MoveNegAlongAndAcross).
	 * 18.    Shift to the left and and up (MoveNegAlongAndNegAcross).
	 *
	 * For SS, anything that can be applied to VS applies.  In addition,
	 * Expand and contract apply.
	 *
	 * </pre>
	 */
	public enum PertType {
		/** Expanders: */
		INC_ALONG1(false, true, false, false, false), //
		INC_ALONG2(false, true, false, false, false), //
		INC_ACROSS1(false, true, false, false, false), //
		INC_ACROSS2(false, true, false, false, false), //
		EXPAND(false, true, false, false, false), //
		/** Contracters. */
		DEC_ALONG1(false, false, true, false, false), //
		DEC_ALONG2(false, false, true, false, false), //
		DEC_ACROSS2(false, false, true, false, false), //
		DEC_ACROSS1(false, false, true, false, false), //
		CONTRACT(false, false, true, false, false), //
		/** Other shape changers. */
		ADD_90_TO_DIR_FIRST_LEG(false), //
		SUB_90_FROM_DIR_FIRST_LEG(false), //
		/** Shifts. */
		MOVE_ALONG(true, false, false, false, false), //
		MOVE_NEG_ALONG(true, false, false, false, false), //
		MOVE_ACROSS(true, false, false, false, false), //
		MOVE_NEG_ACROSS(true, false, false, false, false), //
		/** For VS or SS Only shifts. */
		MOVE_ALONG_ACROSS(true, false, false, false, true), //
		MOVE_NEG_ALONG_ACROSS(true, false, false, false, true), //
		MOVE_ALONG_NEG_ACROSS(true, false, false, false, true), //
		MOVE_NEG_ALONG_NEG_ACROSS(true, false, false, false, true), //
		/** Twists. */
		INC_HDG_FIRST_LEG(true, false, false, false, false), //
		DEC_HDG_FIRST_LEG(true, false, false, false, false), //
		INC_HDG_90(true, false, false, false, false), //
		DEC_HDG_90(true, false, false, false, false), //
		/** SameBox. */
		ROTATE_180(false, false, false, true, false), //
		TOGGLE_FIRST_TURN_RIGHT(false, false, false, true, false), //
		/** Manual Moves. */
		JUMP(true), TOGGLE_FROZEN_MANUAL(true), TOGGLE_FROZEN_BEST(true),
		TOGGLE_ACTIVE(true);

		/** Used for standard ones except for add90 and sub90. */
		private PertType(final boolean shift, final boolean expand,
				final boolean contract, final boolean sameBox,
				final boolean forVsOrSsOnly) {
			_standard = true;
			_shift = shift;
			_expand = expand;
			_contract = contract;
			_changesShape = expand || contract;
			_plusOrMinus90 = false;
			_sameBox = sameBox;
			_forVsOrSsOnly = forVsOrSsOnly;
			_manualMove = false;
			_inverse = null;
		}

		/** Used for ADD_90 and SUB_90 and manual. */
		private PertType(final boolean manualMove) {
			_standard = _changesShape = _plusOrMinus90 = !manualMove;
			_shift = _expand = _contract = _sameBox = _forVsOrSsOnly = false;
			_manualMove = manualMove;
			_inverse = null;
		}

		final public boolean _standard;
		final public boolean _shift;
		final public boolean _expand;
		final public boolean _contract;
		final public boolean _changesShape;
		final public boolean _sameBox;
		final public boolean _forVsOrSsOnly;
		final public boolean _plusOrMinus90;
		final public boolean _manualMove;
		private PertType _inverse;

		public PertType getInverse() {
			return _inverse;
		}

		public boolean isCongruent() {
			return _shift || _inverse == this;
		}

		static {
			pairUpInverses(INC_ACROSS1, DEC_ACROSS1);
			pairUpInverses(INC_ACROSS2, DEC_ACROSS2);
			pairUpInverses(INC_ALONG1, DEC_ALONG1);
			pairUpInverses(INC_ALONG2, DEC_ALONG2);
			pairUpInverses(EXPAND, CONTRACT);
			pairUpInverses(ADD_90_TO_DIR_FIRST_LEG, SUB_90_FROM_DIR_FIRST_LEG);
			pairUpInverses(INC_HDG_FIRST_LEG, DEC_HDG_FIRST_LEG);
			pairUpInverses(INC_HDG_90, DEC_HDG_90);
			pairUpInverses(MOVE_ALONG, MOVE_NEG_ALONG);
			pairUpInverses(MOVE_ACROSS, MOVE_NEG_ACROSS);
			pairUpInverses(MOVE_ALONG_ACROSS, MOVE_NEG_ALONG_NEG_ACROSS);
			pairUpInverses(MOVE_NEG_ALONG_ACROSS, MOVE_ALONG_NEG_ACROSS);
			pairUpInverses(TOGGLE_FIRST_TURN_RIGHT, TOGGLE_FIRST_TURN_RIGHT);
			pairUpInverses(ROTATE_180, ROTATE_180);
			assert validInverses() : "Messed up PertType Inverses.";
		}

		private static void pairUpInverses(final PertType pertType1,
				final PertType pertType2) {
			pertType1._inverse = pertType2;
			pertType2._inverse = pertType1;
		}

		public static final PertType[] _Values = PertType.values();

		private static boolean validInverses() {
			/**
			 * PertType.values() doesn't exist yet so we have to manually create
			 * it by copy/paste of the actual enums.
			 */
			final PertType[] pertTypeValues = new PertType[] { //
					INC_ALONG1, INC_ALONG2, DEC_ALONG1, DEC_ALONG2, //
					INC_ACROSS1, INC_ACROSS2, DEC_ACROSS2, DEC_ACROSS1, //
					MOVE_ALONG, MOVE_NEG_ALONG, MOVE_ACROSS, MOVE_NEG_ACROSS, //
					MOVE_ALONG_ACROSS, MOVE_NEG_ALONG_ACROSS, MOVE_NEG_ALONG_ACROSS,
					MOVE_NEG_ALONG_NEG_ACROSS, //
					EXPAND, CONTRACT, //
					INC_HDG_FIRST_LEG, DEC_HDG_FIRST_LEG, //
					INC_HDG_90, DEC_HDG_90, //
					ADD_90_TO_DIR_FIRST_LEG, SUB_90_FROM_DIR_FIRST_LEG, //
					ROTATE_180, TOGGLE_FIRST_TURN_RIGHT, //
					JUMP, TOGGLE_FROZEN_MANUAL, TOGGLE_FROZEN_BEST, TOGGLE_ACTIVE };
			for (final PertType pertType : pertTypeValues) {
				if (pertType._manualMove) {
					return pertType._inverse == null;
				}
				if ((pertType._inverse == null) || (pertType._inverse._inverse != pertType)) {
					return false;
				}
			}
			return true;
		}
	}

	/**
	 * The following parameters determine how drastic twists and moves are.
	 * "Rough and Ready" uses the 1st one, refining uses the 2nd.
	 */
	final private static double _NmiToMove0 = 0.5;
	final private static double _NmiToMove1 = 0.25;
	/** Note; we need 3d * PatternUtilStatics._OrntnDegInc to divide 360. */
	final private static double _RToTwist0 =
			Math.toRadians(3d * PatternUtilStatics._OrntnDegInc);

	final private static double _RToTwist1 =
			Math.toRadians(PatternUtilStatics._OrntnDegInc);
	/** For growing and shrinking "across," we need an "increase factor." */
	final static double _IncFactor = 9d / 8d;

	/**
	 * The main routine. No validation of PsCs or track spacing. If the
	 * perturbation yields an invalid array of PvValues, it returns the
	 * original.
	 */
	public static PvValue perturbPvValue(final PvValue pvValue,
			final PertType pertType, final boolean roughAndReady,
			final int repeatCount) {
		final PatternVariable pv = pvValue.getPv();
		final PatternKind patternKind = pv.getPatternKind();
		final PlannerModel plannerModel = pv.getPlannerModel();
		final Planner planner = plannerModel.getPlanner();
		if (pv.getUserFrozenPvValue() != null) {
			return pvValue;
		}

		final TangentCylinder plannerTc =
				planner.getSimModel().getTangentCylinder();
		final double minTsR = pv.getMinTsNmi() * _NmiToR;
		final double oldSpecAlongR = pvValue.getSpecAlongR();
		final double oldSpecSgndAcrossR = pvValue.getSpecSgndAcrossR();
		final boolean oldFirstTurnRight = pvValue.getFirstTurnRight();
		final double oldSpecAcrossRx = Math.abs(oldSpecSgndAcrossR);
		final double oldSignum = Math.signum(oldSpecSgndAcrossR);
		final double oldFirstLegDirR = pvValue.getFirstLegDirR();
		final double oldCos = MathX.cosX(oldFirstLegDirR);
		final double oldSin = MathX.sinX(oldFirstLegDirR);

		/** Convert the old center to plannerTangentCylinder's coordinates. */
		final TangentCylinder.FlatLatLng oldFlatCenter =
				plannerTc.convertToMyFlatLatLng(pvValue.getCenter());
		final double oldCenterX = oldFlatCenter.getEastOffset();
		final double oldCenterY = oldFlatCenter.getNorthOffset();
		{
			final double newAlongR = oldSpecAlongR;
			double newSgndAcrossR = oldSpecSgndAcrossR;
			double newFirstLegDirR = oldFirstLegDirR;
			final double newCenterX = oldCenterX;
			final double newCenterY = oldCenterY;
			boolean done = true;
			switch (pertType) {
			case INC_HDG_FIRST_LEG:
			case DEC_HDG_FIRST_LEG:
			case INC_HDG_90:
			case DEC_HDG_90:
				final double adjustment;
				if (pertType == PertType.INC_HDG_FIRST_LEG) {
					/** The direction goes down since the heading is going up. */
					adjustment =
							-repeatCount * (roughAndReady ? _RToTwist0 : _RToTwist1);
				} else if (pertType == PertType.DEC_HDG_FIRST_LEG) {
					adjustment =
							repeatCount * (roughAndReady ? _RToTwist0 : _RToTwist1);
				} else if (pertType == PertType.INC_HDG_90) {
					adjustment = -Constants._PiOver2;
				} else {
					adjustment = Constants._PiOver2;
				}
				newFirstLegDirR += adjustment;
				newFirstLegDirR = LatLng3.getInRange0_2Pi(newFirstLegDirR);
				break;
			case MOVE_ALONG:
			case MOVE_NEG_ALONG:
			case MOVE_ACROSS:
			case MOVE_NEG_ACROSS:
			case MOVE_ALONG_ACROSS: // For SS or VS only
			case MOVE_NEG_ALONG_ACROSS: // For SS or VS only
			case MOVE_ALONG_NEG_ACROSS: // For SS or VS only
			case MOVE_NEG_ALONG_NEG_ACROSS: // For SS or VS only
				final TangentCylinder.FlatLatLng newFlatLatLng;
				final double rIncrement =
						(roughAndReady ? _NmiToMove0 : _NmiToMove1) * _NmiToR;
				final double oldLat =
						PatternUtilStatics.DiscretizeLat(oldFlatCenter.getLat());
				final double oldLng =
						PatternUtilStatics.DiscretizeLat(oldFlatCenter.getLng());
				for (int k = 0;; ++k) {
					final double distance = (repeatCount + k) * rIncrement;
					final double dTimesCos = distance * oldCos;
					final double dTimesSin = distance * oldSin;
					final double newCenterXB, newCenterYB;
					if (pertType == PertType.MOVE_ALONG) {
						newCenterXB = oldCenterX + dTimesCos;
						newCenterYB = oldCenterY + dTimesSin;
					} else if (pertType == PertType.MOVE_NEG_ALONG) {
						newCenterXB = oldCenterX - dTimesCos;
						newCenterYB = oldCenterY - dTimesSin;
					} else if (pertType == PertType.MOVE_ACROSS) {
						newCenterXB = oldCenterX + dTimesSin;
						newCenterYB = oldCenterY - dTimesCos;
					} else if (pertType == PertType.MOVE_NEG_ACROSS) {
						newCenterXB = oldCenterX - dTimesSin;
						newCenterYB = oldCenterY + dTimesCos;
					} else if (pertType == PertType.MOVE_ALONG_ACROSS) {
						newCenterXB = oldCenterX + dTimesCos * dTimesSin;
						newCenterYB = oldCenterY + dTimesSin - dTimesCos;
					} else if (pertType == PertType.MOVE_NEG_ALONG_ACROSS) {
						newCenterXB = oldCenterX - dTimesCos + dTimesSin;
						newCenterYB = oldCenterY - dTimesSin - dTimesCos;
					} else if (pertType == PertType.MOVE_ALONG_NEG_ACROSS) {
						newCenterXB = oldCenterX + dTimesCos - dTimesSin;
						newCenterYB = oldCenterY + dTimesSin + dTimesCos;
					} else { // if (pertType == PertType.MOVE_NEG_ALONG_NEG_ACROSS) {
						newCenterXB = oldCenterX - dTimesCos - dTimesSin;
						newCenterYB = oldCenterY - dTimesSin + dTimesCos;
					}
					final TangentCylinder.FlatLatLng newFlatLatLngB =
							plannerTc.new FlatLatLng(newCenterXB, newCenterYB);
					final double newLat =
							PatternUtilStatics.DiscretizeLat(newFlatLatLngB.getLat());
					final double newLng =
							PatternUtilStatics.DiscretizeLat(newFlatLatLngB.getLng());
					final LatLng3 newLatLngB = LatLng3.getLatLngB(newLat, newLng);
					if (newLatLngB.getLat() != oldLat ||
							newLatLngB.getLng() != oldLng) {
						newFlatLatLng = plannerTc.convertToMyFlatLatLng(newLatLngB);
						break;
					}
				}
				/**
				 * If it fails, modifyAndTest will simply return the original value.
				 */
				final PvValue newPvValue =
						modifyAndTest(planner, pvValue, newFlatLatLng, oldFirstLegDirR,
								oldSpecAlongR, oldSpecSgndAcrossR);
				return newPvValue;
			case ROTATE_180:
				newFirstLegDirR += Math.PI;
				break;
			case TOGGLE_FIRST_TURN_RIGHT:
				if (oldFirstTurnRight == plannerModel.getFirstTurnRight(patternKind,
						!oldFirstTurnRight, /* randomize= */false)) {
					/** Hands are tied. */
					return pvValue;
				}
				newSgndAcrossR = -oldSpecSgndAcrossR;
				break;
			default:
				done = false;
				break;
			}
			/**
			 * If we have finished, test the solution and patch it if necessary.
			 * After the patch attempt, we return, whether or not we were
			 * successful.
			 */
			if (done) {
				final TangentCylinder.FlatLatLng newFlatCenter =
						plannerTc.new FlatLatLng(newCenterX, newCenterY);
				final PvValue newPvValue = modifyAndTest(planner, pvValue,
						newFlatCenter, newFirstLegDirR, newAlongR, newSgndAcrossR);
				return newPvValue;
			}
		}

		/**
		 * The remaining ones are tough. <br>
		 * IncAlong: n goes down, sl goes up, ts goes down. <br>
		 * DecAlong: n goes up, sl goes down, ts goes up. <br>
		 * IncAcross: n is unchanged, sl goes down, ts goes up. <br>
		 * DecAcross: n is unchanged, sl goes up, ts goes down. <br>
		 * Expand: n goes down, sl is unchanged, ts goes up. <br>
		 * Contract: n goes up, sl is unchanged, ts goes down. <br>
		 */
		final double afterTransitsEffNmi =
				pv.getRawSearchKts() * (pvValue.getSearchDurationSecs() / 3600d) *
						PatternUtilStatics._EffectiveSpeedReduction;
		final double afterTransitsEffR = afterTransitsEffNmi * _NmiToR;

		final DiscreteLpSpecToTs oldTsBoxAdjuster;
		final DiscreteSsSpecToTs oldSsBoxAdjuster;
		final double oldN;
		final double oldTsR;
		final double oldSllR;
		if (patternKind.isPsCs()) {
			oldTsBoxAdjuster = new DiscreteLpSpecToTs(afterTransitsEffR, minTsR,
					/* fixedTsR= */Double.NaN, oldSpecAlongR, oldSpecSgndAcrossR,
					/* expandSpecsIfNeeded= */true, /* dummyCharForR= */' ');
			if (!oldTsBoxAdjuster.isValid()) {
				return pvValue;
			}
			oldN = oldTsBoxAdjuster._nSearchLegs;
			oldTsR = oldTsBoxAdjuster._ts;
			oldSllR = oldTsBoxAdjuster._sll;
			oldSsBoxAdjuster = null;
		} else if (patternKind.isSs()) {
			oldTsBoxAdjuster = null;
			oldSsBoxAdjuster = new DiscreteSsSpecToTs(afterTransitsEffR, minTsR,
					/* fixedTsR= */Double.NaN, oldSpecSgndAcrossR,
					/* expandSpecsIfNeeded= */true, /* dummyCharForR= */' ');
			oldN = oldSsBoxAdjuster.getNSearchLegs();
			oldTsR = oldSsBoxAdjuster._ts;
			oldSllR = Double.NaN;
		} else if (patternKind.isVs()) {
			oldTsBoxAdjuster = null;
			oldSsBoxAdjuster = null;
			oldN = -1;
			oldTsR = oldSllR = Double.NaN;
		} else {
			return pvValue;
		}

		switch (pertType) {
		case INC_ALONG1:
		case INC_ALONG2:
		case DEC_ALONG1:
		case DEC_ALONG2: {
			if (!patternKind.isPsCs()) {
				return pvValue;
			}
			/**
			 * For these, we decrease and increase n, but keep the width constant.
			 */
			final boolean growAlong = pertType == PertType.INC_ALONG1 ||
					pertType == PertType.INC_ALONG2;
			final double newN = Math.round(oldN + (growAlong ? -1d : 1d));
			/** Make sure we have at least 2 legs. */
			if (newN < 1.5) {
				return pvValue;
			}
			final double newAlongR = afterTransitsEffR / newN;
			final double newTsR = oldN / newN * oldTsR;
			final double newSllR = newAlongR - newTsR;
			if (newSllR <= 0d) {
				return pvValue;
			}
			PvValue newPvValue = modifyAndTest(planner, pvValue, oldFlatCenter,
					oldFirstLegDirR, newAlongR, oldSpecSgndAcrossR);
			if (newPvValue == pvValue) {
				return pvValue;
			}
			/** Adjust alongR, sgnsAcrossR, and the center. */
			final double alongDelta =
					(newPvValue.getSpecAlongR() - oldSpecAlongR) / 2d;
			final double newSpecSgndAcrossR = newPvValue.getSpecSgndAcrossR();
			final double newAcrossR = Math.abs(newSpecSgndAcrossR);
			final double absAcrossDelta = (newAcrossR - oldSpecAcrossRx) / 2d;
			final double[] rotated = NumericalRoutines.rotateXy(oldCos, oldSin,
					alongDelta, absAcrossDelta);
			double newCenterX, newCenterY;
			if (pertType == PertType.INC_ALONG2 ||
					pertType == PertType.DEC_ALONG2) {
				newCenterX = oldCenterX + rotated[0];
				newCenterY = oldCenterY + rotated[1];
			} else {
				newCenterX = oldCenterX - rotated[0];
				newCenterY = oldCenterY - rotated[1];
			}
			final TangentCylinder.FlatLatLng newFlatCenter =
					plannerTc.new FlatLatLng(newCenterX, newCenterY);
			newPvValue = modifyAndTest(planner, pvValue, newFlatCenter,
					oldFirstLegDirR, newAlongR, newSpecSgndAcrossR);
			return newPvValue;
		}
		case INC_ACROSS2:
		case INC_ACROSS1:
		case DEC_ACROSS2:
		case DEC_ACROSS1: {
			if (!patternKind.isPsCs()) {
				/**
				 * These would destroy the square, which is required for SS and VS.
				 */
				return pvValue;
			}
			/**
			 * We increase across by increasing the track spacing. To keep along
			 * constant, the identity along = pathLength/n forces us to keep n
			 * constant.
			 */
			final boolean growAcross = pertType == PertType.INC_ACROSS2 ||
					pertType == PertType.INC_ACROSS1;
			/** Note: _IncFactor is 1 + eps. */
			final double incFactor = growAcross ? _IncFactor : 1d / _IncFactor;
			final double newTrackSpacing = oldTsR * incFactor;
			final double newSgndAcross = oldN * newTrackSpacing * oldSignum;
			final double newSearchLegLength = oldSpecAlongR - newTrackSpacing;
			if (newSearchLegLength <= 0d) {
				return pvValue;
			}
			PvValue newPvValue = modifyAndTest(planner, pvValue, oldFlatCenter,
					oldFirstLegDirR, oldSpecAlongR, newSgndAcross);
			if (newPvValue == pvValue) {
				return pvValue;
			}
			/** Adjust alongR, sgndAcrossR, and the center. */
			final double newSpecAcrossR =
					Math.abs(newPvValue.getSpecSgndAcrossR());
			final double alongSpecDeltaR =
					(newPvValue.getSpecAlongR() - oldSpecAlongR) / 2d;
			final double deltaSpecAcrossR =
					(newSpecAcrossR - oldSpecAcrossRx) / 2d;
			final double[] rotated = NumericalRoutines.rotateXy(oldCos, oldSin,
					alongSpecDeltaR, deltaSpecAcrossR);
			double newCenterX, newCenterY;
			if (pertType == PertType.INC_ACROSS1 ||
					pertType == PertType.DEC_ACROSS1) {
				newCenterX = oldCenterX + rotated[0];
				newCenterY = oldCenterY + rotated[1];
			} else {
				newCenterX = oldCenterX - rotated[0];
				newCenterY = oldCenterY - rotated[1];
			}
			final TangentCylinder.FlatLatLng newFlatCenter =
					plannerTc.new FlatLatLng(newCenterX, newCenterY);
			newPvValue = modifyAndTest(planner, pvValue, newFlatCenter,
					oldFirstLegDirR, oldSpecAlongR, newSgndAcross);
			return newPvValue;
		}
		case EXPAND:
		case CONTRACT: {
			if (patternKind.isVs()) {
				return pvValue;
			}
			if (patternKind.isSs()) {
				final MyStyle myStyle = pvValue.getMyStyle();
				final double rawSearchKts = pv.getRawSearchKts();
				final double minTsNmi = pv.getMinTsNmi();
				final int oldNHalfLaps =
						myStyle.computeNHalfLaps(rawSearchKts, minTsNmi);
				final int newNHalfLaps;
				if (pertType == PertType.EXPAND) {
					newNHalfLaps = PatternUtilStatics
							.AdjustNHalfLaps(/* increase= */false, oldNHalfLaps);
				} else {
					newNHalfLaps = PatternUtilStatics
							.AdjustNHalfLaps(/* increase= */true, oldNHalfLaps);
				}
				/** Keep the same track spacing. */
				final double oldSgndAcrossNmi = myStyle.getSpecSgndAcrossNmi();
				final double oldAcrossNmi = Math.abs(oldSgndAcrossNmi);
				final double oldTs = (oldAcrossNmi / oldNHalfLaps);
				final double newSgndAcrossNmi =
						oldTs * newNHalfLaps * Math.signum(oldSgndAcrossNmi);
				final double newSgndAcross = newSgndAcrossNmi * _NmiToR;
				final PvValue newPvValue =
						modifyAndTest(planner, pvValue, oldFlatCenter, oldFirstLegDirR,
								/* newAlong= */Double.NaN, newSgndAcross);
				return newPvValue;
			}

			/**
			 * For these, we subtract or add a leg, but keep the search leg length
			 * constant.
			 */
			final double newN =
					Math.round(oldN + (pertType == PertType.EXPAND ? -1d : 1d));
			if (newN < 1.5) {
				return pvValue;
			}
			double newAlong = afterTransitsEffR / newN;
			double newTrackSpacing = newAlong - oldSllR;
			if (newTrackSpacing <= 0d) {
				return pvValue;
			}
			double newSgndAcross = oldSignum * newN * newTrackSpacing;
			PvValue newPvValue = modifyAndTest(planner, pvValue, oldFlatCenter,
					oldFirstLegDirR, newAlong, newSgndAcross);
			/**
			 * This is almost guaranteed to fail if n is 1d. In that case, try for
			 * 2 legs, with a minimum track spacing, and turn the direction
			 * around.
			 */
			if (newPvValue == pvValue && oldN < 1.5) {
				newTrackSpacing = pv.getMinTsNmi() * _NmiToR;
				final double newSearchLegLength =
						(afterTransitsEffR - 2d * newTrackSpacing) / 2d;
				if (newSearchLegLength > 0d) {
					newAlong = newTrackSpacing + newSearchLegLength;
					newSgndAcross = -oldSignum * 2d * newTrackSpacing;
					newPvValue = modifyAndTest(planner, pvValue, oldFlatCenter,
							oldFirstLegDirR, newAlong, newSgndAcross);
				}
			}
			return newPvValue;
		}
		case ADD_90_TO_DIR_FIRST_LEG:
		case SUB_90_FROM_DIR_FIRST_LEG: {
			final double newAlong, newSgndAcross;
			if (patternKind.isPsCs()) {
				newAlong = Math.abs(oldSpecSgndAcrossR);
				newSgndAcross = oldSpecAlongR * Math.signum(oldSpecSgndAcrossR);
			} else if (patternKind.isSs()) {
				newAlong = Double.NaN;
				newSgndAcross = oldSpecSgndAcrossR;
			} else {
				/**
				 * These pertTypes represent the notion that we keep the same box,
				 * bug go across the box instead of along it. Because of the
				 * hexagonal nature of VS, it doesn't make sense to use this here
				 * for vs. Moreover, if we were to turn it 90 degrees, the hexagonal
				 * nature of the exclusion zone changes and this defeats the purpose
				 * of "turning 90 degrees."
				 */
				return pvValue;
			}
			final double newFirstLegDirR;
			if (pertType == PertType.ADD_90_TO_DIR_FIRST_LEG) {
				newFirstLegDirR = oldFirstLegDirR - Constants._PiOver2;
			} else {
				newFirstLegDirR = oldFirstLegDirR + Constants._PiOver2;
			}
			final PvValue newPvValue = modifyAndTest(planner, pvValue,
					oldFlatCenter, newFirstLegDirR, newAlong, newSgndAcross);
			return newPvValue;
		}
		case MOVE_ALONG:
		case MOVE_NEG_ALONG:
		case MOVE_ACROSS:
		case MOVE_NEG_ACROSS:
			assert false : "Should have been done earlier.";
		default: {
			return null;
		}
		}
	}

	/** Creates a new PvValue. If it fails, it returns the old one. */
	private static PvValue modifyAndTest(final Planner planner,
			final PvValue pvValue0, final LatLng3 newCenter,
			final double firstLegDirR, final double newAlongR,
			final double newSgndAcrossR) {
		final PatternVariable pv = pvValue0.getPv();
		if (pv.getUserFrozenPvValue() != null) {
			return pvValue0;
		}
		final PatternKind patternKind = pv.getPatternKind();
		final long cstRefSecs = pvValue0.getCstRefSecs();
		final int searchDurationSecs = pvValue0.getSearchDurationSecs();
		final PvValue pvValue1;
		if (patternKind.isPsCs()) {
			pvValue1 = PvValue.createPvValue(pv, cstRefSecs, searchDurationSecs,
					newCenter, firstLegDirR, newAlongR, newSgndAcrossR);
		} else if (patternKind.isVs()) {
			pvValue1 = PvValue.createPvValue(pv, cstRefSecs, searchDurationSecs,
					newCenter, firstLegDirR,
					/* firstTurnRight= */newSgndAcrossR > 0d);
		} else if (patternKind.isSs()) {
			pvValue1 = PvValue.createPvValue(pv, cstRefSecs, searchDurationSecs,
					newCenter, firstLegDirR, /* alongR= */Double.NaN, newSgndAcrossR);
		} else {
			return null;
		}
		if (pvValue1 == null || pvValue1.compareTo(pvValue0) == 0) {
			return pvValue0;
		}
		return pvValue1;
	}

}
