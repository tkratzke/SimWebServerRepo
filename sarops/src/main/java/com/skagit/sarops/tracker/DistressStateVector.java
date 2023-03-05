/*
 * Created on Jan 30, 2004
 */
package com.skagit.sarops.tracker;

import com.skagit.sarops.environment.CurrentsUvGetter;
import com.skagit.sarops.environment.DataForOnePointAndTime;
import com.skagit.sarops.environment.NetCdfUvGetter;
import com.skagit.sarops.environment.WindsUvGetter;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.Scenario;
import com.skagit.sarops.tracker.Particle.LeewayCalculator;
import com.skagit.util.Constants;
import com.skagit.util.MathX;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.TangentCylinder;
import com.skagit.util.randomx.Randomx;
import com.skagit.util.shorelineFinder.ShorelineFinder;
import com.skagit.util.shorelineFinder.StopAndBlock;

public class DistressStateVector extends StateVector {
	final private static double _NmiToR = MathX._NmiToR;
	final private static double _MToR = 1d / Constants._NmiToM * _NmiToR;

	private float _zSeaNorth;
	private float _zSeaEast;
	private float _zWindNorth;
	private float _zWindEast;

	public DistressStateVector(final Particle particle, final long simSecs, final LatLng3 latLng,
			final boolean updateParticleTail) {
		super(particle, simSecs, latLng, StateVectorType.DISTRESS, updateParticleTail);
		_zSeaNorth = _zSeaEast = _zWindNorth = _zWindEast = Float.NaN;
	}

	public DistressStateVector(final StateVector predecessor, final long simSecs, final boolean updateParticleTail) {
		super(predecessor, StateVectorType.DISTRESS, simSecs, updateParticleTail);
		_zSeaNorth = _zSeaEast = _zWindNorth = _zWindEast = Float.NaN;
	}

	final private static double _Ln2 = Math.log(2d);
	final private static double _MInc = 5d;
	final private static int _NTimesToUseIdealHdg = 3;
	final private static int _NTimesToIterateBeforeGivingUp = 6;

	@Override
	public StateVector timeUpdate(final Tracker tracker, final Scenario scenario, final long[] simSecsS, final int iT) {
		final Model model = tracker.getModel();
		/**
		 * We need to get the CurrentsUvGetter so we can adjust, if necessary, the
		 * interpolation mode.
		 */
		final CurrentsUvGetter currentsUvGetter = model.getCurrentsUvGetter();
		final WindsUvGetter windsUvGetter = model.getWindsUvGetter();
		final long currentSimSecs = getSimSecs();
		final long simSecs = simSecsS[iT];
		final boolean noAuxiliaryProcessing = currentSimSecs >= simSecs || isStuckOnLand() || isAnchored()
				|| !currentsUvGetter.hasAuxiliaryProcessing();
		if (noAuxiliaryProcessing) {
			return coreTimeUpdate(tracker, simSecsS, iT, /* updateParticleTail= */true);
		}
		final DistressStateVector distressStateVector = currentsUvGetter.fillInStateVectorsIfAppropriate(windsUvGetter,
				scenario, simSecsS, this, simSecs);
		return distressStateVector;
	}

	final public DistressStateVector coreTimeUpdate(final Tracker tracker, final long[] simSecsS, final int iT,
			final boolean updateParticleTail) {
		final Model model = tracker.getModel();
		final boolean riverine = model.isRiverine();
		final long currentSimSecs = getSimSecs();
		final long simSecs = simSecsS[iT];
		//
		if (currentSimSecs >= simSecs) {
			/**
			 * This happens when particles are initially at different times when we do the
			 * first time updates. We need some of them to catch up.
			 */
			return this;
		}
		final Randomx random = _particle.getRandom();
		final LatLng3 latLng0 = _latLng;
		--_particle._remainingPenalty;
		if (isStuckOnLand() || _particle._remainingPenalty > 0) {
			final DistressStateVector newStateVector = new DistressStateVector(this, simSecs, updateParticleTail);
			newStateVector.setIsStuckOnLand();
			newStateVector.setLatLng(latLng0);
			return newStateVector;
		}
		if (isAnchored()) {
			final DistressStateVector newStateVector = new DistressStateVector(this, simSecs, updateParticleTail);
			newStateVector.setIsAnchored();
			newStateVector.setLatLng(latLng0);
			return newStateVector;
		}

		/** We neither start stuck on land nor anchored; we're moving. */
		final LeewayCalculator leewayCalculator = getLeewayCalculator();
		final double[] z = new double[] {
				Double.NaN, Double.NaN, Double.NaN, Double.NaN
		};
		/* Getting ready to bend around a blocking edge. */
		final long refSecsForEnvLookUp = model.getRefSecs(currentSimSecs);
		/** For river cases, we interpolate with only 2 points. */
		/** For wind, we always use the standard way of doing things. */
		final long deltaSecs = simSecs - currentSimSecs;
		final double nHrs = deltaSecs / 3600d;
		final double speedFactor = nHrs * _NmiToR;
		final double seaEastMoveR;
		final double seaNorthMoveR;
		final double windEastMoveR;
		final double windNorthMoveR;
		final CurrentsUvGetter currentsUvGetter = model.getCurrentsUvGetter();
		final MyLogger logger = tracker.getSimCase().getLogger();
		if (currentsUvGetter != null && !currentsUvGetter.isEmpty(logger)) {
			final boolean forCurrents = true;
			final String interpolationModeD = model.getInterpolationMode(forCurrents);
			final DataForOnePointAndTime currentsUv = currentsUvGetter.getCurrentData(logger, refSecsForEnvLookUp,
					latLng0, interpolationModeD);
			/* Set the draw to zero if ... */
			final double zDrawNorth;
			final double zDrawEast;
			if (!_particle.isEnvMean() && currentsUvGetter.useRandomDuringUpdates()) {
				zDrawNorth = random.getTruncatedGaussian();
				zDrawEast = random.getTruncatedGaussian();
			} else {
				zDrawNorth = 0d;
				zDrawEast = 0d;
			}
			double newZNorth = zDrawNorth;
			double newZEast = zDrawEast;
			if (!Double.isNaN(_zSeaNorth)) {
				/**
				 * rho is 1 for riverine downstream and 0 for riverine cross-stream. It depends
				 * on the half life otherwise.
				 */
				if (riverine) {
					newZEast = _zSeaEast;
					newZNorth = zDrawNorth;
				} else {
					final double rho;
					if (_particle._fullPenalty > 0) {
						/** Just emerged from penalty box; rho is 0. */
						rho = 0d;
					} else {
						final double currentHalfLifeSecs = currentsUvGetter.getHalfLifeSecs();
						if (currentHalfLifeSecs > 0d) {
							final double currentDecayRatePerSec = _Ln2 / currentHalfLifeSecs;
							rho = Math.exp(-currentDecayRatePerSec * deltaSecs);
						} else {
							rho = 0;
						}
					}
					final double rhoPrime = Math.sqrt(1d - rho * rho);
					newZNorth = rho * _zSeaNorth + rhoPrime * zDrawNorth;
					newZEast = rho * _zSeaEast + rhoPrime * zDrawEast;
				}
			}
			z[0] = newZEast;
			z[1] = newZNorth;
			final float currentsDu = currentsUv.getValue(NetCdfUvGetter.DataComponent.DU);
			final float currentsDv = currentsUv.getValue(NetCdfUvGetter.DataComponent.DV);
			float u = currentsUv.getValue(NetCdfUvGetter.DataComponent.U);
			float v = currentsUv.getValue(NetCdfUvGetter.DataComponent.V);
			if (!riverine) {
				/** Non-river case. */
				if (model.getReverseDrift()) {
					u = -u;
					v = -v;
				}
				seaEastMoveR = (u + newZEast * currentsDu) * speedFactor;
				seaNorthMoveR = (v + newZNorth * currentsDv) * speedFactor;
			} else {
				/**
				 * River case: We interpret the zSeaEast and zSeaNorth as down-stream and
				 * cross-stream. Moreover, uStandardDeviation is a ratio, and vStandardDeviation
				 * is a fraction of that ratio.
				 */
				if (model.getReverseDrift()) {
					u = -u;
					v = -v;
				}
				final double[] unitDownstream = new double[] {
						u, v
				};
				final double downstreamSpeed = NumericalRoutines.convertToUnitLength(unitDownstream);
				final double downStreamSd = downstreamSpeed * currentsDu / 100d;
				final double crossStreamSd = downStreamSd * currentsDv;
				final double du, dv;
				if (!Double.isNaN(unitDownstream[0])) {
					final double[] unitCrossStream = new double[] {
							-unitDownstream[1], unitDownstream[0]
					};
					final double downstreamPerturbation = newZEast * downStreamSd;
					final double crossStreamPerturbation = newZNorth * crossStreamSd;
					du = downstreamPerturbation * unitDownstream[0] + crossStreamPerturbation * unitCrossStream[0];
					dv = downstreamPerturbation * unitDownstream[1] + crossStreamPerturbation * unitCrossStream[1];
				} else {
					du = dv = 0;
				}
				seaEastMoveR = (u + du) * speedFactor;
				seaNorthMoveR = (v + dv) * speedFactor;
			}
		} else {
			seaEastMoveR = seaNorthMoveR = 0;
			z[0] = z[1] = Double.NaN;
		}
		final WindsUvGetter windsUvGetter = model.getWindsUvGetter();
		if (windsUvGetter != null && !windsUvGetter.isEmpty(logger)) {
			final String interpolationModeW = model.getInterpolationMode(/* forCurrents= */false);
			final DataForOnePointAndTime dnWindData = windsUvGetter.getDownWindData(refSecsForEnvLookUp, latLng0,
					interpolationModeW);
			/** Perturb the wind velocity. */
			final double zDrawEast;
			final double zDrawNorth;
			if (!_particle.isEnvMean() && windsUvGetter.useRandomDuringUpdates()) {
				zDrawNorth = random.getTruncatedGaussian();
				zDrawEast = random.getTruncatedGaussian();
			} else {
				zDrawNorth = 0;
				zDrawEast = 0;
			}
			double newZNorth = zDrawNorth;
			double newZEast = zDrawEast;
			if (!Double.isNaN(_zWindNorth)) {
				final double windHalfLifeSecs = windsUvGetter.getHalfLifeSecs();
				final double rho;
				if (_particle._fullPenalty == 0 && windHalfLifeSecs > 0d) {
					final double windDecayRatePerSecond = _Ln2 / windHalfLifeSecs;
					rho = Math.exp(-windDecayRatePerSecond * deltaSecs);
				} else {
					rho = 0d;
				}
				final double rhoPrime = Math.sqrt(1d - rho * rho);
				newZNorth = rho * _zWindNorth + rhoPrime * zDrawNorth;
				newZEast = rho * _zWindEast + rhoPrime * zDrawEast;
			}
			z[2] = newZEast;
			z[3] = newZNorth;
			float downWindU = dnWindData.getValue(NetCdfUvGetter.DataComponent.U);
			float downWindV = dnWindData.getValue(NetCdfUvGetter.DataComponent.V);
			if (model.getReverseDrift()) {
				downWindU = -downWindU;
				downWindV = -downWindV;
			}
			final float dnWindDu = dnWindData.getValue(NetCdfUvGetter.DataComponent.DU);
			final float dnWindDv = dnWindData.getValue(NetCdfUvGetter.DataComponent.DV);
			final float dnWindEastSpeed = (float) (downWindU + newZEast * dnWindDu);
			final float downWindNorthSpeed = (float) (downWindV + newZNorth * dnWindDv);
			final double[] eastAndNorthLeewaySpeeds = leewayCalculator.getEastAndNorthLeewaySpeeds(dnWindEastSpeed,
					downWindNorthSpeed, simSecs, _particle);
			/** We do a simple move with the corresponding leeway speed. */
			windEastMoveR = (eastAndNorthLeewaySpeeds[0] * speedFactor);
			windNorthMoveR = (eastAndNorthLeewaySpeeds[1] * speedFactor);
		} else {
			windEastMoveR = windNorthMoveR = 0;
			z[2] = z[3] = Double.NaN;
		}
		final double eastMoveR = seaEastMoveR + windEastMoveR;
		final double northMoveR = seaNorthMoveR + windNorthMoveR;
		/** Update the z draws. */
		final DistressStateVector newStateVector = new DistressStateVector(this, simSecs, updateParticleTail);
		newStateVector._zSeaEast = (float) z[0];
		newStateVector._zSeaNorth = (float) z[1];
		newStateVector._zWindEast = (float) z[2];
		newStateVector._zWindNorth = (float) z[3];

		/**
		 * Deal with land. We now have a target latLng if land does not get in the way.
		 * Recall that we are starting not stuck on land.
		 */
		final long mySimSecs = getSimSecs();
		final TangentCylinder myTangentCylinder = _particle.getTangentCylinderFromSimSecs(latLng0, mySimSecs);
		final TangentCylinder.FlatLatLng initialFlatLatLng = myTangentCylinder.convertToMyFlatLatLng(latLng0);
		final LatLng3 latLngAfterWind = LatLng3.makeBasicLatLng3(initialFlatLatLng.addOffsets(eastMoveR, northMoveR));
		final ParticleIndexes prtclIndxs = _particle.getParticleIndexes();
		final boolean isSticky;
		if (prtclIndxs != null) {
			isSticky = model.getIsSticky(prtclIndxs);
		} else {
			isSticky = _particle.getGuaranteedSticky();
		}
		final ShorelineFinder shorelineFinder = model.getShorelineFinder();
		double hdg = MathX.initialHdgX(latLng0, latLngAfterWind);
		final double completeHaversine = MathX.haversineX(latLng0, latLngAfterWind);
		final double completeMeters = completeHaversine / _MToR;
		/** Sticky is relatively easy; just stick to the landing or float on. */
		if (isSticky) {
			final StopAndBlock stopAndBlock = new StopAndBlock(logger, shorelineFinder, latLng0, hdg, completeMeters,
					_MInc, /* debug= */false);
			if (stopAndBlock.ranIntoSomething()) {
				newStateVector.setLatLng(stopAndBlock._stopLatLng);
				newStateVector.setIsStuckOnLand();
				_particle._fullPenalty = _particle._remainingPenalty = Integer.MAX_VALUE;
			} else {
				newStateVector.setLatLng(latLngAfterWind);
			}
			return newStateVector;
		}

		/**
		 * Slippery, so stay well offshore, and use up the distance between
		 * initialLatLng and latLngAfterWind.
		 */
		LatLng3 latLng = latLng0;
		double remainingM = completeMeters;
		boolean clearedLand = false;
		for (int k0 = 0; k0 < _NTimesToIterateBeforeGivingUp; ++k0) {
			final LatLng3 oldLatLng = latLng;
			final StopAndBlock stopAndBlock0 = new StopAndBlock(logger, shorelineFinder, latLng, hdg, remainingM, _MInc,
					/* debug= */false);
			if (stopAndBlock0.ranIntoSomething()) {
				/**
				 * Got blocked. Update remainingM. We always give credit for at least _MInc.
				 */
				final double mToMove = stopAndBlock0._mToStop;
				remainingM -= Math.max(mToMove, _MInc);

				/** Update latLng. */
				latLng = stopAndBlock0._stopLatLng;
			} else {
				/** We're clear. Update remainingM and latLng. */
				final double remainingHaversine = remainingM * _MToR;
				latLng = MathX.getLatLngX(latLng, hdg, remainingHaversine);
				remainingM = 0d;
			}

			/** We're done if we're very close. */
			if (remainingM < 0.1) {
				clearedLand = true;
				break;
			}
			/** Set hdg from latLng for the next iteration. */
			final double idealHdg = NumericalRoutines.generalMod(MathX.initialHdgX(latLng, oldLatLng) + 180d, 360d);
			if (k0 < _NTimesToUseIdealHdg) {
				final GreatCircleArc blockingGca0 = stopAndBlock0._blockingSfr.getBlockingGca();
				final TangentCylinder tc = TangentCylinder.getTangentCylinder(latLng);
				final TangentCylinder.FlatLatLng flatBlockingLatLng0 = tc
						.convertToMyFlatLatLng(blockingGca0.getLatLng0());
				final TangentCylinder.FlatLatLng flatBlockingLatLng1 = tc
						.convertToMyFlatLatLng(blockingGca0.getLatLng1());
				final double[] A = flatBlockingLatLng0.createOffsetPair();
				final double[] B = flatBlockingLatLng1.createOffsetPair();
				/** Shift A and B so they go through the origin. */
				final double[][] shiftedAandB = getShiftedEndpoints(A, B);
				final double[] shiftedA = shiftedAandB[0];
				final double[] shiftedB = shiftedAandB[1];
				final TangentCylinder.FlatLatLng shiftedFlatLatLngA = tc.new FlatLatLng(shiftedA[0], shiftedA[1]);
				final TangentCylinder.FlatLatLng shiftedFlatLatLngB = tc.new FlatLatLng(shiftedB[0], shiftedB[1]);
				final double hdgA = MathX.initialHdgX(latLng, shiftedFlatLatLngA);
				final double hdgB = MathX.initialHdgX(latLng, shiftedFlatLatLngB);
				double closenessA = Math.abs(idealHdg - hdgA);
				if (closenessA > 180d) {
					closenessA = 360d - closenessA;
				}
				double closenessB = Math.abs(idealHdg - hdgB);
				if (closenessB > 180d) {
					closenessB = 360d - closenessB;
				}
				hdg = closenessA < closenessB ? hdgA : hdgB;
			} else {
				/**
				 * We have given up on using an ideal hdg. Look for a hdg that maximizes the
				 * distance we can move from latLng, or is at least big enough to make
				 * significant progress towards the goal. Our definition of "significant
				 * progress" grows more and more restrictive, since the distance we need to move
				 * gets smaller. In what follows, p is 1/2, 3/4, 7/8, ...
				 */
				final double p = 1d - Math.pow(0.5, k0 + 1 - _NTimesToUseIdealHdg);
				final double thresholdM = p * remainingM;
				/**
				 * If we find a direction that admits a move at least remainingR, take it.
				 * Otherwise, simply take the direction that will admit the largest move.
				 */
				double bestM = 0d;
				double bestHdg = idealHdg;
				for (double hdgShift = 5d; Math.abs(hdgShift) <= 45d; hdgShift = -hdgShift
						+ (hdgShift < 0d ? 5d : -5d)) {
					final double thisHdg = NumericalRoutines.generalMod(hdg + hdgShift, 360d);
					final StopAndBlock stopAndBlock1 = new StopAndBlock(logger, shorelineFinder, latLng, thisHdg,
							remainingM, _MInc, /* debug= */false);
					if (!stopAndBlock1.ranIntoSomething()) {
						/** thisHdg will do; there's no block along thisHdg. */
						bestHdg = thisHdg;
						break;
					}
					final double thisM = MathX.haversineX(latLng, stopAndBlock1._stopLatLng) / _MToR;
					if (thisM > thresholdM) {
						bestHdg = thisHdg;
						break;
					}
					if (thisM > bestM) {
						bestM = thisM;
						bestHdg = thisHdg;
					}
				}
				hdg = bestHdg;
			}
		}
		newStateVector.setLatLng(latLng);
		if (clearedLand || riverine) {
			_particle._fullPenalty = _particle._remainingPenalty = 0;
		} else {
			_particle._fullPenalty = (int) Math.round(1.5 * (_particle._fullPenalty + 1d));
			_particle._remainingPenalty = _particle._fullPenalty;
		}
		return newStateVector;
	}

	@Override
	public String getDescription() {
		if (isStuckOnLand()) {
			return "Distress Landed particle of type " + getSearchObjectType().getId();
		}
		if (isAnchored()) {
			return "Distress Anchored particle of type " + getSearchObjectType().getId();
		}
		return "Distress particle of type " + getSearchObjectType().getId();
	}

	@Override
	public boolean isDistress() {
		return true;
	}

	/**
	 * Shifts the segment (in the plane) from A->B to ShiftedA->ShiftedB, where the
	 * latter is parallel to the former, but goes through the origin, and the
	 * projection of the origin onto the former is mapped to the origin.
	 */
	private static double[][] getShiftedEndpoints(final double[] A, final double[] B) {
		final double Ax = A[0];
		final double Ay = A[1];
		final double Bx = B[0];
		final double By = B[1];
		final double[] shiftedXy = new double[] {
				-Ax, -Ay
		};
		/**
		 * The perp is (-deltaY, deltaX). It's the important vector and we form it here
		 * without forming the regular unit vector.
		 */
		final double[] u = new double[] {
				Bx - Ax, By - Ay
		};
		final double[] v = new double[] {
				-u[1], u[0]
		};
		/**
		 * To avoid rounding, we do not normalize u or v; this means we have to divide
		 * by v's length when we form the dot product, and then by v's length again when
		 * we apply this dot product to v. Hence, the strange looking scalar
		 * "dotProduct(...)/lengthSq."
		 */
		final double lengthSq = v[0] * v[0] + v[1] * v[1];
		final double yPrime = NumericalRoutines.dotProduct(v, shiftedXy) / lengthSq;
		final double[] shift = v.clone();
		NumericalRoutines.multiply(yPrime, shift);
		final double shiftedAx = Ax + shift[0];
		final double shiftedAy = Ay + shift[1];
		final double shiftedBx = Bx + shift[0];
		final double shiftedBy = By + shift[1];
		return new double[][] {
				new double[] {
						shiftedAx, shiftedAy
				}, new double[] {
						shiftedBx, shiftedBy
				}
		};
	}
}
