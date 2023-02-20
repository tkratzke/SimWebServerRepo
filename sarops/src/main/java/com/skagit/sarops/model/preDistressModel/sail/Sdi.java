package com.skagit.sarops.model.preDistressModel.sail;

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.math3.exception.OutOfRangeException;

import com.skagit.sarops.environment.CurrentsUvGetter;
import com.skagit.sarops.environment.DataForOnePointAndTime;
import com.skagit.sarops.environment.NetCdfUvGetter;
import com.skagit.sarops.environment.WindsUvGetter;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.sarops.tracker.StateVectorType;
import com.skagit.util.Constants;
import com.skagit.util.HdgKts;
import com.skagit.util.MathX;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.GreatCircleCalculator;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.randomx.Randomx;
import com.skagit.util.shorelineFinder.ShorelineFinder;
import com.skagit.util.shorelineFinder.ShorelineFinderResult;

/** Sdi stands for SailDataInstance. */
public class Sdi {
	final private static double _NmiToR = MathX._NmiToR;

	final private static boolean _UseModelShorelineFinder = true;
	final private static double _ClosingDefinition = 89d;
	final private static double _MinSailSegNmi = 0d; // TMK!! 0.25;
	final private static long _MinSecsToAssignSailSeg = 15;
	final private static boolean _Examine = false;

	final public ParticleIndexes _prtclIndxs;
	final public SailData _sailData;
	final public double _sailorQuality;
	final public double _speedMultiplier;
	final public double _forbiddenAngleIncrease;
	final public boolean _useZeroZeroMotoring;
	final public double _nearToEndNmi;
	/**
	 * The following are separate draws and, because they are stored when the Sdi is
	 * created, are constant for this particle for the entire voyage.
	 */
	final public double _angleOffDirect;
	final public double _proportionOfDistanceToTargetToIgnoreOffsetAngle;
	final public double _hoveToKts;
	final public double _hoveToOffsetFromApprntDnWnd;
	final public boolean _twistCwFirst;
	final public long _meanTackSecs;

	final private Randomx _r;

	final private static double _HdgIncrement = 5d;

	public Sdi(final SailData sailData, final ParticleIndexes prtclIndxs, final Randomx r) {
		_r = r;
		_prtclIndxs = prtclIndxs;
		_sailData = sailData;
		if (_r == null) {
			/** Since we have no Rng, we are computing minArrivalRefSecs. */
			_sailorQuality = 1d;
			_speedMultiplier = sailData.getSpeedMultiplier(_sailorQuality);
			_forbiddenAngleIncrease = sailData.getForbiddenAngleIncrease(_sailorQuality);
			_angleOffDirect = 0d;
			_proportionOfDistanceToTargetToIgnoreOffsetAngle = sailData._lowProportionOfDistanceToTargetToIgnoreOffsetAngle;
			_hoveToKts = sailData._hoveToMaxKts;
			_hoveToOffsetFromApprntDnWnd = 0d;
			_twistCwFirst = true;
			_meanTackSecs = SailData._TenYears;
			_useZeroZeroMotoring = false;
			_nearToEndNmi = 0d;
			return;
		}

		/** Choose a "sailor quality." */
		double speedMultiplier = 0d;
		double forbiddenAngleIncrease = 0d;
		double angleOffDirect = 0d;
		double proportionOfDistanceToTargetToIgnoreOffsetAngle = 0d;
		double sailorQuality = 1d;
		double nearToEndNmi = 0d;
		try {
			sailorQuality = sailData._betaDistribution.inverseCumulativeProbability(_r.nextDouble());
			speedMultiplier = sailData.getSpeedMultiplier(sailorQuality);
			forbiddenAngleIncrease = sailData.getForbiddenAngleIncrease(sailorQuality);
			angleOffDirect = _r.getTruncatedGaussian() * sailData._angleOffRhumblineSd;
			proportionOfDistanceToTargetToIgnoreOffsetAngle = sailData._lowProportionOfDistanceToTargetToIgnoreOffsetAngle
					+ _r.nextDouble() * (sailData._highProportionOfDistanceToTargetToIgnoreOffsetAngle
							- sailData._lowProportionOfDistanceToTargetToIgnoreOffsetAngle);
			nearToEndNmi = sailData._nearToEndNmi;
		} catch (final OutOfRangeException e) {
			speedMultiplier = 0d;
			forbiddenAngleIncrease = 0d;
			angleOffDirect = 0d;
			proportionOfDistanceToTargetToIgnoreOffsetAngle = 0d;
		}
		_sailorQuality = sailorQuality;
		_speedMultiplier = speedMultiplier;
		_forbiddenAngleIncrease = forbiddenAngleIncrease;
		_proportionOfDistanceToTargetToIgnoreOffsetAngle = proportionOfDistanceToTargetToIgnoreOffsetAngle;

		/** Put in zeroZeroMotoring. */
		_useZeroZeroMotoring = _r == null ? false : _r.nextDouble() <= sailData._probZeroZeroMotoring;
		_nearToEndNmi = nearToEndNmi;
		if (-_ClosingDefinition <= angleOffDirect && angleOffDirect <= _ClosingDefinition) {
			_angleOffDirect = angleOffDirect;
		} else {
			_angleOffDirect = 0d;
		}
		_hoveToKts = sailData._hoveToMinKts + _r.nextDouble() * (sailData._hoveToMaxKts - sailData._hoveToMinKts);
		final boolean use90 = _r == null ? true : _r.nextBoolean();
		final double hoveToOffset = sailData._hoveToOffsetFromUpwindMean
				+ _r.getTruncatedGaussian() * sailData._hoveToBearingSd;
		_hoveToOffsetFromApprntDnWnd = use90 ? hoveToOffset : (360d - hoveToOffset);
		_twistCwFirst = _r.nextBoolean();
		if (0L < sailData._meanAverageTackSecs && sailData._averageTackSecsSd >= 0L) {
			final long meanTackSecs = (long) Math.min(SailData._TenYears,
					_r.getTruncatedGaussian() * sailData._averageTackSecsSd + sailData._meanAverageTackSecs);
			_meanTackSecs = Math.max(10, meanTackSecs);
		} else {
			_meanTackSecs = SailData._TenYears;
		}
	}

	/** Generate the SailSegs for a single leg of the voyage. */
	public ArrayList<SailSeg> generateSailSegs(final SimCaseManager.SimCase simCase, final long startRefSecs,
			final LatLng3 legStart, final LatLng3 legStop, final int iLeg, final int nLegs, final long[] refSecsS) {

		final MyLogger logger = SimCaseManager.getLogger(simCase);
		/** Unpack values. */
		final Model model = _sailData._model;
		final ShorelineFinder shorelineFinder = _UseModelShorelineFinder ? model.getShorelineFinder()
				: ShorelineFinder.createNoPolygonShorelineFinder();
		final Polars polars = _sailData._polars;
		final CurrentsUvGetter currentsUvGetter = model.getCurrentsUvGetter();
		final WindsUvGetter windsUvGetter = model.getWindsUvGetter();
		final boolean firstLeg = iLeg == 0;
		final boolean lastLeg = iLeg == nLegs - 1;
		final int nRefSecsS = refSecsS == null ? 0 : refSecsS.length;

		final long[] currentsAndWindsHalfLivesSecsS = new long[] {
				currentsUvGetter.getPreDistressHalfLifeSecs(), windsUvGetter.getPreDistressHalfLifeSecs()
		};
		final double closeEnoughForArrival = _sailData._arrivalRadiusNmi;
		final long checkIntervalSecs = _sailData._checkIntervalSecs;
		final String interpolationModeC = model.getInterpolationMode(/* forCurrents= */true);
		final String interpolationModeW = model.getInterpolationMode(/* forCurrents= */false);
		final double enterHoveToSpeed = _sailData._enterHoveToKts;
		final double exitHoveToSpeed = _sailData._exitHoveToKts;
		final long lastOutputRefSecs = model.getLastOutputRefSecs();

		/** We use the same motorKts for this entire Leg. */
		final double motorKts = _sailData._polars.generateMotorKts(_r);
		/**
		 * Initialize the inputs to the loop. Start with oldT and oldZ to make
		 * correlated draws from the environment:
		 */
		final double[][] oldZ = new double[2][2];
		Arrays.fill(oldZ[0], Double.NaN);
		Arrays.fill(oldZ[1], Double.NaN);
		long oldRefSecsForCorrelatedCrrntsAndWnds = Long.MIN_VALUE;
		HdgKts dnCurrent = null;
		HdgKts upWind = null;

		/**
		 * closeEnoughToIgnoreOffsetAngle is how close we have to get to abandon the
		 * offset hdg. When we get that close, we will re-set
		 * closeEnoughToIgnoreOffsetAngle to 0. If we lose progress and hence are no
		 * longer that close, we do not re-set closeEnoughToIgnoreOffsetAngle; we simply
		 * keep it at 0.
		 */
		final double legNmi = GreatCircleCalculator.getNmi(legStart, legStop);
		double closeEnoughToIgnoreOffsetAngle = (1d - _proportionOfDistanceToTargetToIgnoreOffsetAngle) * legNmi;

		/** Force a new environment by setting secsLeftInEnv to 0. */
		long secsLeftInEnv = 0L;
		/**
		 * Fill in all of the SailSegs for this Voyage leg. We do this checkIntervalSecs
		 * at a time. Because of land, there may be several SailSegs within a
		 * checkIntervalSecs, but the environment is assumed to be constant over both
		 * time and position, for the entire checkIntervalSecs.
		 */
		int nLandHitsInRow = 0;
		boolean wasMotor = false;
		boolean wasHoveTo = false;
		boolean forceHoveToFromPreviousSailSeg = false;
		HdgKtsPlus[][] portAndStrbrdWedges = null;
		double apprntWindKts = Double.NaN;

		final ArrayList<SailSeg> sailSegs = new ArrayList<>();

		/** If we start on land, we go nowhere. Else, we never get onto land. */
		final int legStartLevel = shorelineFinder.getLevel(logger, legStart);
		final boolean landed = legStartLevel % 2 == 1;
		if (landed) {
			final GreatCircleArc gca = GreatCircleArc.CreateGca(legStart, legStart);
			final SailSeg sailSeg = new SailSeg(gca, /* edgeNumber= */0, startRefSecs, lastOutputRefSecs, upWind,
					dnCurrent);
			sailSeg._svt0 = sailSeg._svt1 = StateVectorType.BLOCKED;
			addAndValidateSailSeg(logger, sailSeg, sailSegs);
			return dumpAndReturn(simCase, iLeg, sailSegs);
		}

		NEXT_SAIL_SEG: for (;;) {
			final int sailSegNumber = sailSegs.size();
			final SailSeg pvsSeg = sailSegNumber == 0 ? null : sailSegs.get(sailSegNumber - 1);
			final LatLng3 segStart = pvsSeg == null ? legStart : pvsSeg._gca.getLatLng1();
			final double enterMotorKts;
			final double exitMotorKts;
			if (!_useZeroZeroMotoring) {
				/** This particle never uses zeroZero motoring. */
				enterMotorKts = _sailData._enterMotorKts;
				exitMotorKts = _sailData._exitMotorKts;
			} else if (firstLeg) {
				final double nmiToStart = MathX.haversineX(legStart, segStart) / _NmiToR;
				if (nmiToStart < _nearToEndNmi) {
					enterMotorKts = _sailData._enterMotorKts;
					exitMotorKts = _sailData._exitMotorKts;
				} else if (lastLeg) {
					/**
					 * Even though we are firstLeg, we might be (probably are) lastLeg as well. In
					 * that case, we must check for the end as well.
					 */
					final double nmiToEnd = MathX.haversineX(segStart, legStop) / _NmiToR;
					if (nmiToEnd < _nearToEndNmi) {
						enterMotorKts = _sailData._enterMotorKts;
						exitMotorKts = _sailData._exitMotorKts;
					} else {
						enterMotorKts = exitMotorKts = 0d;
					}
				} else {
					/** We're firstLeg and not close to start or end. */
					enterMotorKts = exitMotorKts = 0d;
				}
			} else if (lastLeg) {
				/** Since we are not firstLeg, so we check only for closeToEnd. */
				final double nmiToEnd = MathX.haversineX(segStart, legStop) / _NmiToR;
				if (nmiToEnd < _nearToEndNmi) {
					enterMotorKts = _sailData._enterMotorKts;
					exitMotorKts = _sailData._exitMotorKts;
				} else {
					/** We're lastLeg, not firstLeg, and we're not closeToEnd. */
					enterMotorKts = exitMotorKts = 0d;
				}
			} else {
				/** Middle legs always use zeroZero motoring. */
				enterMotorKts = exitMotorKts = 0d;
			}

			final long segStartRefSecs;
			if (pvsSeg == null) {
				segStartRefSecs = startRefSecs;
			} else {
				segStartRefSecs = pvsSeg._refSecs1;
			}
			if (segStartRefSecs >= lastOutputRefSecs) {
				/**
				 * We are hopelessly stuck; this is where that particle ends.
				 */
				return dumpAndReturn(simCase, iLeg, sailSegs);
			}

			/** Get the environment if necessary. */
			if (secsLeftInEnv <= 0L) {
				/**
				 * Get the environment for this checkIntervalSecs, and reset how much time is
				 * left in this checkIntervalSecs.
				 */
				final DataForOnePointAndTime rawCrrnt;
				if (currentsUvGetter.isEmpty(logger)) {
					rawCrrnt = new DataForOnePointAndTime(0f, 0f, 0f, 0f, 0f, 0f);
				} else {
					rawCrrnt = currentsUvGetter.getCurrentData(logger, segStartRefSecs, segStart, interpolationModeC);
				}
				final DataForOnePointAndTime rawDnWind;
				if (windsUvGetter.isEmpty(logger)) {
					rawDnWind = new DataForOnePointAndTime(0f, 0f, 0f, 0f, 0f, 0f);
				} else {
					rawDnWind = windsUvGetter.getDownWindData(segStartRefSecs, segStart, interpolationModeW);
				}
				final DataForOnePointAndTime[] rawCrrntAndDnWnd = new DataForOnePointAndTime[] {
						rawCrrnt, rawDnWind
				};
				final double[][] crrntAndDnWnd = getCorrelatedCurrentAndDownWind(rawCrrntAndDnWnd,
						oldRefSecsForCorrelatedCrrntsAndWnds, oldZ, segStartRefSecs, currentsAndWindsHalfLivesSecsS);
				dnCurrent = new HdgKts(crrntAndDnWnd[0][0], crrntAndDnWnd[0][1], /* doublesAreHdgKts= */false);
				upWind = new HdgKts(-crrntAndDnWnd[1][0], -crrntAndDnWnd[1][1], /* doublesAreHdgKts= */false);
				oldRefSecsForCorrelatedCrrntsAndWnds = segStartRefSecs;

				/**
				 * Setting secsLeftInEnv. Do not set it so that we go past the next refSecs or
				 * lastOutputRefSecs. Other than that, simply add checkIntervalSecs to
				 * segStartRefSecs.
				 */
				long endIntervalSecs = Math.min(segStartRefSecs + checkIntervalSecs, lastOutputRefSecs);
				if (nRefSecsS > 0) {
					final int idx0 = Arrays.binarySearch(refSecsS, segStartRefSecs);
					final int cannotCrossIdx;
					if (idx0 >= 0) {
						/**
						 * segStartRefSecs is on some refSecsS. The one that we cannot cross is the next
						 * one.
						 */
						cannotCrossIdx = idx0 + 1;
					} else {
						cannotCrossIdx = -idx0 - 1;
					}
					if (cannotCrossIdx < nRefSecsS) {
						endIntervalSecs = Math.min(endIntervalSecs, refSecsS[cannotCrossIdx]);
					}
				}
				secsLeftInEnv = endIntervalSecs - segStartRefSecs;
				apprntWindKts = dnCurrent.add(upWind).getKts();
				portAndStrbrdWedges = polars.getPortAndStrbrdWedges(logger, dnCurrent, upWind, /* port= */true,
						/* strbrd= */true, _forbiddenAngleIncrease, _speedMultiplier, /* addInCurrent= */true,
						/* cull= */true);
				/** With new environment, reset nLandHitsInRow. */
				nLandHitsInRow = 0;
			}

			/** This is the direction we need to go. */
			final double directCog = MathX.initialHdgX(segStart, legStop);

			/** This is the direction we will try to go for this SailSeg. */
			final double dsrdCog = LatLng3
					.getInRange0_360(directCog + (closeEnoughToIgnoreOffsetAngle == 0d ? 0d : _angleOffDirect));

			/** Determine what we are doing at refSecs. */
			final boolean hoveTo;
			final boolean motor;

			if (forceHoveToFromPreviousSailSeg) {
				motor = false;
				hoveTo = true;
				/** Only use the forceHoveTo once. */
				forceHoveToFromPreviousSailSeg = false;
			} else if (apprntWindKts < enterMotorKts) {
				motor = true;
				hoveTo = false;
			} else if (wasMotor && apprntWindKts < exitMotorKts) {
				motor = true;
				hoveTo = false;
			} else if (apprntWindKts >= enterHoveToSpeed) {
				motor = false;
				hoveTo = true;
			} else if (wasHoveTo && apprntWindKts > exitHoveToSpeed) {
				motor = false;
				hoveTo = true;
			} else {
				/** Sailing. */
				motor = hoveTo = false;
			}

			if (_Examine) {
				final String s = String.format("%s: Particle%s S/Q[%f] Wind %s Current %s",
						motor ? "Motor" : (hoveTo ? "Hoveto" : "Sailing"), _prtclIndxs.getString(), _sailorQuality,
						upWind.getString(), dnCurrent.getString());
				SimCaseManager.err(simCase, s);
			}
			if (motor) {
				wasMotor = true;
				wasHoveTo = false;
				/**
				 * Compute what direction and how fast we will go if we wish to go straight at
				 * LegStop. If we cannot go straight at LegStop, the following returns what
				 * happens if we aim straight into the current.
				 */
				final HdgKts motorHdgKtsForDsrdCog = getMotorHdgKts(dnCurrent, motorKts, dsrdCog);
				final double hdgKtsHdgForDsrdCog = motorHdgKtsForDsrdCog.getHdg();
				if (Math.abs(LatLng3.degsToEast180_180(dsrdCog, hdgKtsHdgForDsrdCog)) > 0.1) {
					/**
					 * He is overwhelmed. Go at most 15 minutes, hoping for a better environment.
					 * If, in those 15 minutes, we hit land, there we stay, overwhelmed and stuck on
					 * land.
					 */
					final long secsToUse = Math.min(15 * 60, secsLeftInEnv);
					final SailSeg sailSeg = createSailSegFromHdgKts(logger, _prtclIndxs, iLeg, motorHdgKtsForDsrdCog,
							secsToUse, shorelineFinder, sailSegNumber, segStart, segStartRefSecs, /* goal= */null,
							/* inControl= */true, /* closeEnough0= */0d, /* closeEnoughToIgnoreOffsetAngle= */0d,
							upWind, dnCurrent);
					final long durationSecs = sailSeg._refSecs1 - sailSeg._refSecs0;
					sailSeg._svt0 = StateVectorType.MOTOR_OVERWHELMED;
					/**
					 * Motor, overwhelmed, stuck, and we are done if it is blocked.
					 */
					if (sailSeg._svt1 == StateVectorType.BLOCKED) {
						sailSeg._svt1 = StateVectorType.MOTOR_OVERWHELMED_BLOCKED;
						addAndValidateSailSeg(logger, sailSeg, sailSegs);
						return dumpAndReturn(simCase, iLeg, sailSegs);
					}
					/** Went full. Just overwhelmed. */
					sailSeg._svt1 = StateVectorType.MOTOR_OVERWHELMED;
					secsLeftInEnv -= durationSecs;
					addAndValidateSailSeg(logger, sailSeg, sailSegs);
					continue NEXT_SAIL_SEG;
				}

				/**
				 * We are motoring and can make progress towards dsrdCog. Find which direction
				 * to go.
				 */
				SailSeg bestBlockedSailSeg = null;
				double cwTwist = 0d;
				double ccwTwist = 0d;
				for (int k = 0; cwTwist + ccwTwist < 360d; ++k) {
					final HdgKts motorHdgKts;
					final double hdg;
					if (k == 0) {
						hdg = dsrdCog;
						motorHdgKts = motorHdgKtsForDsrdCog;
					} else {
						if ((k % 2 == 1) == _twistCwFirst) {
							cwTwist += _HdgIncrement;
							hdg = LatLng3.getInRange0_360(directCog + cwTwist);
						} else {
							ccwTwist += _HdgIncrement;
							hdg = LatLng3.getInRange0_360(directCog - ccwTwist);
						}
						if ((LatLng3.degsToEast180_180(directCog, hdg)) > _ClosingDefinition) {
							/** Not interested. */
							continue;
						}
						motorHdgKts = getMotorHdgKts(dnCurrent, motorKts, hdg);
						final double hdgKtsHdg = motorHdgKts.getHdg();
						if (Math.abs(LatLng3.degsToEast180_180(hdg, hdgKtsHdg)) > 0.1) {
							/** We cannot go in this hdg. */
							continue;
						}
					}
					final SailSeg sailSeg = createSailSegFromHdgKts(logger, _prtclIndxs, iLeg, motorHdgKts,
							secsLeftInEnv, shorelineFinder, sailSegNumber, segStart, segStartRefSecs, legStop,
							/* inControl= */true, closeEnoughForArrival, closeEnoughToIgnoreOffsetAngle, upWind,
							dnCurrent);
					final double sailSegNmi = sailSeg._gca.getTtlNmi();
					final long durationSecs = sailSeg.getDurationSecs();
					sailSeg._svt0 = StateVectorType.MOTOR;
					if (sailSeg._svt1 == StateVectorType.ARRIVE) {
						if (!lastLeg || closeEnoughToIgnoreOffsetAngle > 0d) {
							sailSeg._svt1 = StateVectorType.NONFINAL_ARRIVE;
						}
						addAndValidateSailSeg(logger, sailSeg, sailSegs);
						return dumpAndReturn(simCase, iLeg, sailSegs);
					}
					if (sailSeg._svt1 == StateVectorType.CLOSE_ENOUGH_TO_ABANDON_OFFSET_ANGLE) {
						addAndValidateSailSeg(logger, sailSeg, sailSegs);
						nLandHitsInRow = 0;
						closeEnoughToIgnoreOffsetAngle = 0d;
						secsLeftInEnv -= durationSecs;
						continue NEXT_SAIL_SEG;
					}
					if (sailSeg._svt1 == StateVectorType.PRJCTN) {
						/**
						 * It went as far in this direction as it could usefully go. We'll use it if it
						 * has length.
						 */
						sailSeg._svt1 = StateVectorType.MOTOR_PRJCTN;
						secsLeftInEnv -= durationSecs;
						addAndValidateSailSeg(logger, sailSeg, sailSegs);
						nLandHitsInRow = 0;
						continue NEXT_SAIL_SEG;
					}
					if (sailSeg._svt1 == null) {
						/** It went full. We'll use it. We might not be blocked */
						if (k == 0) {
							sailSeg._svt1 = StateVectorType.MOTOR;
						} else if (bestBlockedSailSeg == null) {
							/**
							 * Even though k > 0, we were not deflected; we just tried headings that were
							 * not closing. We are still classified as motoring.
							 */
							sailSeg._svt1 = StateVectorType.MOTOR;
						} else {
							sailSeg._svt1 = StateVectorType.MOTOR_DEFLECTED;
						}
						secsLeftInEnv -= durationSecs;
						addAndValidateSailSeg(logger, sailSeg, sailSegs);
						nLandHitsInRow = 0;
						continue NEXT_SAIL_SEG;
					}
					/**
					 * It didn't arrive, it didn't go full, and it didn't have to stop because of
					 * projections. It competes for best blocked.
					 */
					if (bestBlockedSailSeg == null || bestBlockedSailSeg._gca.getTtlNmi() < sailSegNmi) {
						bestBlockedSailSeg = sailSeg;
						continue;
					}
				}
				/**
				 * Motoring and couldn't go full. Use half of the best one unless this is our
				 * 3rd bump. In that case, use up the rest of checkInterval and go 3/4 of the
				 * way to land.
				 */
				if (bestBlockedSailSeg == null) {
					/** We are confused. Just give up. */
					return dumpAndReturn(simCase, iLeg, sailSegs);
				}
				final long startSecs = bestBlockedSailSeg._refSecs0;
				final GreatCircleArc origGca = bestBlockedSailSeg._gca;
				final double origHdg = origGca.getRawInitialHdg();
				final double origNmi = origGca.getTtlNmi();
				final boolean lastOne = ++nLandHitsInRow >= 3;
				final LatLng3 latLng1 = GreatCircleCalculator.getLatLng(segStart, origHdg,
						(lastOne ? 0.75 : 0.5) * origNmi);
				final GreatCircleArc gca = GreatCircleArc.CreateGca(segStart, latLng1);
				final double nmi = gca.getTtlNmi();
				final double hrs = nmi / motorKts;
				final long durationSecs = Math.round(3600d * hrs);
				final long stopSecs = segStartRefSecs + durationSecs;
				final SailSeg sailSeg = new SailSeg(gca, sailSegNumber, startSecs, stopSecs, upWind, dnCurrent);
				sailSeg._svt0 = StateVectorType.MOTOR;
				sailSeg._svt1 = StateVectorType.MOTOR_BLOCKED;
				addAndValidateSailSeg(logger, bestBlockedSailSeg, sailSegs);
				if (lastOne) {
					return dumpAndReturn(simCase, iLeg, sailSegs);
				}
				secsLeftInEnv -= durationSecs;
				continue NEXT_SAIL_SEG;
			}

			if (hoveTo) {
				wasMotor = false;
				wasHoveTo = true;
				final HdgKts apprntUpWind = upWind.add(dnCurrent);
				final double apprntDnWndHdg = apprntUpWind.getHdg() + 180d;
				final double apprntHoveToHdg = apprntDnWndHdg + _hoveToOffsetFromApprntDnWnd;
				final double apprntHoveToTheta = Math.toRadians(90d - apprntHoveToHdg);
				final double c = MathX.cosX(apprntHoveToTheta);
				final double s = MathX.sinX(apprntHoveToTheta);
				final double trueEastKts = c * _hoveToKts + dnCurrent.getEastKts();
				final double trueNorthKts = s * _hoveToKts + dnCurrent.getNorthKts();
				final HdgKtsPlus trueHdgKts = new HdgKtsPlus(trueEastKts, trueNorthKts, /* doublesAreHdgKts= */false,
						StateVectorType.HOVETO);
				final SailSeg sailSeg = createSailSegFromHdgKts(logger, _prtclIndxs, iLeg, trueHdgKts, secsLeftInEnv,
						shorelineFinder, sailSegNumber, segStart, segStartRefSecs, legStop, /* inControl= */false,
						closeEnoughForArrival, /* closeEnoughToIgnoreOffsetAngle= */0d, upWind, dnCurrent);
				sailSeg._svt0 = StateVectorType.HOVETO;
				if (sailSeg._svt1 == StateVectorType.ARRIVE) {
					if (!lastLeg || closeEnoughToIgnoreOffsetAngle > 0d) {
						sailSeg._svt1 = StateVectorType.NONFINAL_ARRIVE;
					}
					addAndValidateSailSeg(logger, sailSeg, sailSegs);
					return dumpAndReturn(simCase, iLeg, sailSegs);
				}
				if (sailSeg._svt1 == null) {
					/** It went full. */
					sailSeg._svt1 = StateVectorType.HOVETO;
					secsLeftInEnv = 0;
					addAndValidateSailSeg(logger, sailSeg, sailSegs);
					nLandHitsInRow = 0;
					continue NEXT_SAIL_SEG;
				}
				/** It got blocked. Give up. */
				sailSeg._svt1 = StateVectorType.HOVETO_BLOCKED;
				addAndValidateSailSeg(logger, sailSeg, sailSegs);
				return dumpAndReturn(simCase, iLeg, sailSegs);
			}

			/** We're sailing. */
			wasMotor = wasHoveTo = false;
			double bestClosingKts = Double.NEGATIVE_INFINITY;
			SailSeg bestBlockedSailSeg = null;
			/**
			 * We prefer port or strbrd if the last SailSeg ended with a TACK_END. If such a
			 * preference exists, we will take the preferred side if it closes.
			 */
			final boolean preferPort, preferStrbrd;
			if (sailSegs.size() == 0) {
				preferPort = preferStrbrd = false;
			} else {
				final SailSeg pvsSailSeg = sailSegs.get(sailSegs.size() - 1);
				if (pvsSailSeg._svt1 == StateVectorType.TACK_END) {
					preferPort = pvsSailSeg._svt0.sailingStrbrd();
					preferStrbrd = !preferPort;
				} else {
					preferPort = preferStrbrd = false;
				}
			}
			/**
			 * We initialize cwTwist and ccwTwist. First, assuming directHdg is north, find
			 * the quadrant Q of dsrdCog. If Q=I or Q=II, we're in a "normal" situation;
			 * dsrdCog is within 90 of directHdg and dsrdCog is a good place to start
			 * looking. If Q=III, then initialize ccwTwist to 90 so we make no ccw twists,
			 * and cwTwist to -90 so the cwTwists run through the entire view. If Q=IV,
			 * initialize cwTwist to 90 and ccwTwist to -90.
			 */
			final double dsrdOffsetFromDrct = LatLng3.degsToEast0_360L(directCog, dsrdCog);
			final int q;
			if (dsrdOffsetFromDrct < 90d) {
				q = 1;
			} else if (dsrdOffsetFromDrct < 180d) {
				q = 4;
			} else if (dsrdOffsetFromDrct < 271d) {
				q = 3;
			} else {
				q = 2;
			}
			final double initCwTwist;
			final double initCcwTwist;
			if (q <= 2) {
				initCwTwist = _twistCwFirst ? _HdgIncrement : 0d;
				initCcwTwist = _twistCwFirst ? 0d : _HdgIncrement;
			} else if (q == 3) {
				initCcwTwist = 90d;
				initCwTwist = -90d;
			} else {
				initCcwTwist = -90d;
				initCwTwist = 90d;
			}
			double cwTwist = initCwTwist;
			double ccwTwist = initCcwTwist;
			/** Create the tacking constraint. */
			final long tackSecs;
			if (_meanTackSecs >= SailData._TenYears) {
				tackSecs = SailData._TenYears;
			} else {
				tackSecs = Math.max(10L, (long) Math.min(SailData._TenYears, _r.getExponentialDraw(_meanTackSecs)));
			}
			final long maxSegSecs = Math.min(tackSecs, secsLeftInEnv);

			/** Start looking for a good cog. */
			for (int k = 0; cwTwist + ccwTwist < 360d; ++k) {
				final double cog;
				if (cwTwist <= ccwTwist) {
					cog = LatLng3.getInRange0_360(dsrdCog + cwTwist);
					cwTwist += _HdgIncrement;
				} else {
					cog = LatLng3.getInRange0_360(dsrdCog - ccwTwist);
					ccwTwist += _HdgIncrement;
				}
				final HdgKtsPlus hdgKtsForCog = Polars.getHdgKtsForCog(portAndStrbrdWedges, cog, preferPort,
						preferStrbrd);
				final double thisCog = hdgKtsForCog.getHdg();
				final double absOffset = Math.abs(LatLng3.degsToEast180_180(directCog, thisCog));
				if (absOffset > _ClosingDefinition) {
					/** Not interested. */
					continue;
				}
				/** hdgKtsForCog closes. */
				final SailSeg sailSeg = createSailSegFromHdgKts(logger, _prtclIndxs, iLeg, hdgKtsForCog, maxSegSecs,
						shorelineFinder, sailSegNumber, segStart, segStartRefSecs, legStop, /* inControl= */true,
						closeEnoughForArrival, closeEnoughToIgnoreOffsetAngle, upWind, dnCurrent);
				sailSeg._svt0 = hdgKtsForCog._svt;
				final long durationSecs = sailSeg.getDurationSecs();
				final StateVectorType svt1 = sailSeg._svt1;
				if (svt1 == StateVectorType.ARRIVE) {
					if (!lastLeg || closeEnoughToIgnoreOffsetAngle > 0d) {
						sailSeg._svt1 = StateVectorType.NONFINAL_ARRIVE;
					}
					addAndValidateSailSeg(logger, sailSeg, sailSegs);
					return dumpAndReturn(simCase, iLeg, sailSegs);
				}

				if (svt1 == StateVectorType.CLOSE_ENOUGH_TO_ABANDON_OFFSET_ANGLE) {
					addAndValidateSailSeg(logger, sailSeg, sailSegs);
					nLandHitsInRow = 0;
					closeEnoughToIgnoreOffsetAngle = 0d;
					secsLeftInEnv -= durationSecs;
					continue NEXT_SAIL_SEG;
				}
				if (svt1 == StateVectorType.PRJCTN) {
					nLandHitsInRow = 0;
					closeEnoughToIgnoreOffsetAngle = 0d;
					addAndValidateSailSeg(logger, sailSeg, sailSegs);
					secsLeftInEnv -= durationSecs;
					continue NEXT_SAIL_SEG;
				} else if (svt1 == null) {
					/** It went full without arriving or being blocked. Use it. */
					nLandHitsInRow = 0;
					if (durationSecs < secsLeftInEnv) {
						/** Got stopped because of tacking constraint. */
						sailSeg._svt1 = StateVectorType.TACK_END;
					} else if (k == 0 || bestBlockedSailSeg == null) {
						sailSeg._svt1 = hdgKtsForCog._svt;
					} else {
						/**
						 * We went full, this isn't our first choice for cog; we were deflected.
						 */
						sailSeg._svt1 = StateVectorType.DEFLECTED;
					}
					addAndValidateSailSeg(logger, sailSeg, sailSegs);
					secsLeftInEnv -= durationSecs;
					continue NEXT_SAIL_SEG;
				}
				/**
				 * hdgKtsForCog is blocked. It's competing for bestBlockedSailSeg.
				 */
				final double thisClosingKts = hdgKtsForCog.getClosingKts(segStart, legStop);
				if (thisClosingKts > bestClosingKts) {
					bestBlockedSailSeg = sailSeg;
					bestClosingKts = thisClosingKts;
				}
			}
			/**
			 * Sailing and couldn't go full. Use half of the best one unless this is our 3rd
			 * bump. In that case, use up the rest of checkInterval and go 3/4 of the way to
			 * land.
			 */
			if (bestBlockedSailSeg == null) {
				/** We could not close. Aim for direct and take what you get. */
				final HdgKts desperateChoice = Polars.getHdgKtsForCog(portAndStrbrdWedges, directCog, preferPort,
						preferStrbrd);
				final SailSeg desperateSailSeg = createSailSegFromHdgKts(logger, _prtclIndxs, iLeg, desperateChoice,
						Math.min(secsLeftInEnv, tackSecs), shorelineFinder, sailSegNumber, segStart, segStartRefSecs,
						/* goal= */null, /* inControl= */false, closeEnoughForArrival,
						/* closeEnoughToIgnoreOffsetAngle= */0d, upWind, dnCurrent);
				desperateSailSeg._svt0 = StateVectorType.OVERWHELMED;
				final StateVectorType svt1 = desperateSailSeg._svt1 == StateVectorType.BLOCKED
						? StateVectorType.OVERWHELMED_BLOCKED
						: StateVectorType.OVERWHELMED;
				desperateSailSeg._svt1 = svt1;
				addAndValidateSailSeg(logger, desperateSailSeg, sailSegs);
				if (svt1 == StateVectorType.OVERWHELMED_BLOCKED) {
					return dumpAndReturn(simCase, iLeg, sailSegs);
				}
				final long durationSecs = desperateSailSeg.getDurationSecs();
				secsLeftInEnv -= durationSecs;
				continue NEXT_SAIL_SEG;
			}
			bestBlockedSailSeg._svt1 = StateVectorType.BLOCKED;
			final long startSecs = bestBlockedSailSeg._refSecs0;
			final GreatCircleArc origGca = bestBlockedSailSeg._gca;
			final double origHdg = origGca.getRawInitialHdg();
			final double origNmi = origGca.getTtlNmi();
			final boolean lastOne = ++nLandHitsInRow >= 3;
			final LatLng3 latLng1 = GreatCircleCalculator.getLatLng(segStart, origHdg,
					(lastOne ? 0.75 : 0.5) * origNmi);
			final GreatCircleArc gca = GreatCircleArc.CreateGca(segStart, latLng1);
			final double nmi = gca.getTtlNmi();
			final double hrs = nmi / motorKts;
			final long stopSecs = Math.round(segStartRefSecs + 3600d * hrs);
			final SailSeg sailSeg = new SailSeg(gca, sailSegNumber, startSecs, stopSecs, upWind, dnCurrent);
			sailSeg._svt0 = bestBlockedSailSeg._svt0;
			sailSeg._svt1 = StateVectorType.BLOCKED;
			addAndValidateSailSeg(logger, bestBlockedSailSeg, sailSegs);
			if (lastOne) {
				return dumpAndReturn(simCase, iLeg, sailSegs);
			}
			final long durationSecs = bestBlockedSailSeg._refSecs1 - bestBlockedSailSeg._refSecs0;
			secsLeftInEnv -= durationSecs;
			continue NEXT_SAIL_SEG;
		}

	}

	final private ArrayList<SailSeg> dumpAndReturn(final SimCaseManager.SimCase simCase, final int iLeg,
			final ArrayList<SailSeg> sailSegs) {
		if (_Examine) {
			String s = "";
			final int nSailSegs = sailSegs == null ? 0 : sailSegs.size();
			for (int k = 0; k < nSailSegs; ++k) {
				final SailSeg sailSeg = sailSegs.get(k);
				final String prtclIndxsString = _prtclIndxs == null ? "NULL"
						: _prtclIndxs.getString(/* includeScenario= */true);
				final String name = String.format("%s iLeg[%d]", prtclIndxsString, iLeg);
				final String sailSegString = sailSeg._gca.getXmlString(sailSeg._svt1.getColorName(), k, name);
				s += "\n" + sailSegString;
			}
			s += "\n";
			SimCaseManager.err(simCase, s);
		}
		return sailSegs;
	}

	final private boolean addAndValidateSailSeg(final MyLogger logger, final SailSeg sailSeg,
			final ArrayList<SailSeg> sailSegs) {
		sailSegs.add(sailSeg);
		return true;
	}

	private static final SailSeg createSailSegFromHdgKts(final MyLogger logger, final ParticleIndexes prtclIndxs,
			final int iLeg, final HdgKts hdgKts, final long maxLenSecs, final ShorelineFinder shorelineFinder,
			final int edgeNumber, final LatLng3 start, final long segStartRefSecs, final LatLng3 goal,
			final boolean inControl, final double closeEnoughForArrival, final double closeEnoughToIgnoreOffsetAngle,
			final HdgKts upWind, final HdgKts dnCurrent) {
		/** Unpack some numbers. */
		final double kts = hdgKts.getKts();
		final double hdg = hdgKts.getHdg();
		final double fullNmi = kts * maxLenSecs / 3600d;

		/** Create the full Gca. */
		final GreatCircleArc fullGca = GreatCircleArc.CreateGca(start, hdg, fullNmi);

		/**
		 * Find shorelineXing and compute nmiToXing. If there is no xing, then nmiToXing
		 * = fullNmi.
		 */
		final ShorelineFinderResult sfr = shorelineFinder.getShorelineFinderResult(logger, fullGca,
				/* findFirst= */true);
		final LatLng3 shorelineXing = sfr.getShorelineXing();
		final GreatCircleArc maxGca;
		if (shorelineXing == null) {
			maxGca = fullGca;
		} else {
			maxGca = GreatCircleArc.CreateGca(start, shorelineXing);
		}
		final double maxNmi = maxGca.getTtlNmi();

		/** Make sure it does not go past closeEnoughForArrival. */
		if (closeEnoughForArrival > 0d && goal != null) {
			final LatLng3 entrance0 = GreatCircleCalculator.getFirstLatLngWithinTolerance(start, hdg, goal,
					closeEnoughForArrival);
			if (entrance0 != null) {
				final double nmiToEntrance0 = GreatCircleCalculator.getNmi(start, entrance0);
				if (nmiToEntrance0 <= maxNmi + _MinSailSegNmi) {
					/** It is at least close to arriving. We're done. */
					final GreatCircleArc arrival0Gca = GreatCircleArc.CreateGca(start, entrance0);
					final double ttlNmi = arrival0Gca.getTtlNmi();
					final double arrivalHrs = ttlNmi / kts;
					final long duration0Secs = Math.max(_MinSecsToAssignSailSeg, Math.round(3600d * arrivalHrs));
					final long stopSecs = segStartRefSecs + duration0Secs;
					final SailSeg sailSeg = new SailSeg(arrival0Gca, edgeNumber, segStartRefSecs, stopSecs, upWind,
							dnCurrent);
					sailSeg._svt1 = StateVectorType.ARRIVE;
					return sailSeg;
				}
			}
		}

		/** Make sure it does not go past closeEnoughToIgnoreOffsetAngle. */
		if (closeEnoughToIgnoreOffsetAngle > 0d && goal != null) {
			final LatLng3 entrance1 = GreatCircleCalculator.getFirstLatLngWithinTolerance(start, hdg, goal,
					closeEnoughToIgnoreOffsetAngle);
			if (entrance1 != null) {
				final double nmiToEntrance1 = GreatCircleCalculator.getNmi(start, entrance1);
				if (nmiToEntrance1 <= maxNmi + _MinSailSegNmi) {
					/** It got close enough. We're done. */
					final GreatCircleArc arrival1Gca = GreatCircleArc.CreateGca(start, entrance1);
					final double arrival1Hrs = arrival1Gca.getTtlNmi() / kts;
					final long duration1Secs = Math.max(_MinSecsToAssignSailSeg, Math.round(3600d * arrival1Hrs));
					final long stopSecs = segStartRefSecs + duration1Secs;
					final SailSeg sailSeg = new SailSeg(arrival1Gca, edgeNumber, segStartRefSecs, stopSecs, upWind,
							dnCurrent);
					sailSeg._svt1 = StateVectorType.CLOSE_ENOUGH_TO_ABANDON_OFFSET_ANGLE;
					return sailSeg;
				}
			}
		}

		/** Make sure it does not go past projection. */
		if (inControl && goal != null) {
			final GreatCircleArc.Projection projection = maxGca.new Projection(goal);
			if (projection.getProjectionToGcIsInGca()) {
				final LatLng3 projectionLatLng = projection.getClosestPointOnGca();
				final GreatCircleArc projectionGca = GreatCircleArc.CreateGca(start, projectionLatLng);
				final double arrival2Hrs = projectionGca.getTtlNmi() / kts;
				final long duration2Secs = Math.max(_MinSecsToAssignSailSeg, Math.round(3600d * arrival2Hrs));
				final long stopSecs = segStartRefSecs + duration2Secs;
				final SailSeg sailSeg = new SailSeg(projectionGca, edgeNumber, segStartRefSecs, stopSecs, upWind,
						dnCurrent);
				sailSeg._svt1 = StateVectorType.PRJCTN;
				return sailSeg;
			}
		}

		if (fullGca == maxGca) {
			/** No reason to stop and it wasn't blocked. */
			final long duration3Secs = Math.max(_MinSecsToAssignSailSeg, maxLenSecs);
			final long stopSecs = segStartRefSecs + duration3Secs;
			final SailSeg sailSeg = new SailSeg(fullGca, edgeNumber, segStartRefSecs, stopSecs, upWind, dnCurrent);
			sailSeg._svt1 = null;
			return sailSeg;
		}

		/** This hdg got blocked. */
		final double duration4Hrs = maxGca.getTtlNmi() / kts;
		final long duration4Secs = Math.max(_MinSecsToAssignSailSeg, Math.round(3600d * duration4Hrs));
		final long stopSecs = segStartRefSecs + duration4Secs;
		final SailSeg sailSeg = new SailSeg(maxGca, edgeNumber, segStartRefSecs, stopSecs, upWind, dnCurrent);
		sailSeg._svt1 = StateVectorType.BLOCKED;
		return sailSeg;
	}

	/** Times and half-lives are in seconds. oldZ is updated. */
	private double[][] getCorrelatedCurrentAndDownWind(final DataForOnePointAndTime[] currntAndDnWindAtRefSecs,
			final long oldRefSecs, final double[][] oldZ, final long refSecs, final long[] halfLivesSecs) {
		final double[][] crrntAndDnWndOut = new double[2][2];
		/** k0 indicates current or wind. k1 indicates east or north. */
		for (int k0 = 0; k0 < 2; ++k0) {
			final DataForOnePointAndTime currentOrDnWind = currntAndDnWindAtRefSecs[k0];
			final long halfLifeSecs = halfLivesSecs[k0];
			final double decayRate = Math.log(2d) / halfLifeSecs;
			for (int k1 = 0; k1 < 2; ++k1) {
				/** Update oldZ[k0][k1]. This will be our draw. */
				if (_r == null) {
					/**
					 * When computing minArrivalRefSecs, we settle for the means.
					 */
					oldZ[k0][k1] = 0d;
				} else {
					final double freshZ = _r.getTruncatedGaussian();
					if (refSecs > oldRefSecs && halfLifeSecs > 0 && !Double.isNaN(oldZ[k0][k1])) {
						final long deltaSecs = refSecs - oldRefSecs;
						final double rho = Math.exp(-decayRate * deltaSecs);
						final double rhoPrime = Math.sqrt(1 - rho * rho);
						oldZ[k0][k1] = oldZ[k0][k1] * rho + freshZ * rhoPrime;
					} else {
						oldZ[k0][k1] = freshZ;
					}
				}
				final float k1Mean = currentOrDnWind
						.getValue(k1 == 0 ? NetCdfUvGetter.DataComponent.U : NetCdfUvGetter.DataComponent.V);
				final float k1StdDev = currentOrDnWind
						.getValue(k1 == 0 ? NetCdfUvGetter.DataComponent.DU : NetCdfUvGetter.DataComponent.DV);
				crrntAndDnWndOut[k0][k1] = k1Mean + k1StdDev * oldZ[k0][k1];
			}
		}
		return crrntAndDnWndOut;
	}

	/**
	 * Finds the heading to aim the boat so that the current will bring the boat
	 * around to the desired heading, and returns what direction and how fast we
	 * will go. Strictly for motoring. If we cannot find such a heading, then we
	 * simply aim the boat straight into the current and return the result of that.
	 */
	private static HdgKtsPlus getMotorHdgKts(final HdgKts dnCurrent, final double Bk, final double dsrdCog) {
		/**
		 * <pre>
		 * Let:
		 *   th be the direction we point the boat (which we want to find),
		 *   TDC = tan(dsrdCog),
		 *   Bk=boatKts,
		 *   Ce=crrntEast, and Cn=crrntNorth.
		 *   We want to choose th so that cog is dsrdCog.
		 * Solve for (c,s) = (cos(th),sin(th)).
		 * 		NB: th and dsrdCog are in radians, ccw from east.
		 * (Cn + s*Bk) / (Ce + c*Bk) = TDC
		 * Cn + s*Bk - TDC*(Ce + Bk*c) = 0
		 * s*Bk = TDC*(Ce + Bk*c) - Cn = (TDC*Ce - Cn) + TDC*Bk*c
		 * s = sqrt(1 - cc) and square both sides:
		 * Bk*Bk*(1 - cc) =
		 *     (TDC*Ce - Cn)*(TDC*Ce - Cn) +
		 *     2*TDC*Bk*(TDC*Ce - Cn)*c +
		 *     TDC*TDC*Bk*Bk*cc
		 * Push everything to the right and we have a quadratic in c:
		 * 		A*cc + B*c + C, where:
		 * A = Bk*Bk + TDC*TDC*Bk*Bk
		 * B = 2*TDC*Bk*(TDC*Ce - Cn)
		 * C = (TDC*Ce - Cn)*(TDC*Ce - Cn) - Bk*Bk
		 * </pre>
		 */
		final double Ce = dnCurrent.getEastKts();
		final double Cn = dnCurrent.getNorthKts();
		final double TDC = MathX.tanX(Math.toRadians(90d - dsrdCog));
		final double BkBk = Bk * Bk;
		final double TtTt = TDC * TDC;
		final double TtCe_Cn = TDC * Ce - Cn;
		final double A = BkBk + TtTt * BkBk;
		final double B = 2d * TDC * Bk * TtCe_Cn;
		final double C = TtCe_Cn * TtCe_Cn - BkBk;
		final double[] roots = NumericalRoutines.quadratic(A, B, C, /* result= */null);
		/**
		 * Note: roots are the proposed solution for the cos of the direction to point
		 * the boat.
		 */
		if (Double.isNaN(roots[0])) {
			/**
			 * Couldn't do it. Just aim the boat straight into the current.
			 */
			final double kts = Math.sqrt(Ce * Ce + Cn * Cn) - Bk;
			final double mathHdg = MathX.atan2X(Cn, Ce);
			final double hdg = Math.toDegrees(Constants._PiOver2 - mathHdg);
			final HdgKtsPlus hdgKts = new HdgKtsPlus(hdg, kts, /* doublesAreHdgkts= */true,
					/* stateVectorType= */StateVectorType.MOTOR);
			return hdgKts;
		}
		double bestS = Double.NaN;
		double bestC = Double.NaN;
		double bestAbsDiff = Double.POSITIVE_INFINITY;
		final int nRoots = roots.length;
		for (int k0 = 0; k0 < nRoots; ++k0) {
			final double cos0 = roots[k0];
			if (!(Math.abs(cos0) <= 1d)) {
				continue;
			}
			final double absS = Math.sqrt(1d - cos0 * cos0);
			for (int k1 = 0; k1 < 2; ++k1) {
				final double c = k1 == 0 ? cos0 : -cos0;
				for (int k2 = 0; k2 < 2; ++k2) {
					final double s = k2 == 0 ? absS : -absS;
					final double moveInY = Cn + s * Bk;
					final double moveInX = Ce + c * Bk;
					final double cogR = MathX.atan2X(moveInY, moveInX);
					final double cog = LatLng3.getInRange0_360(90d - Math.toDegrees(cogR));
					final double diff = LatLng3.degsToEast180_180(dsrdCog, cog);
					final double absDiff = Math.abs(diff);
					if (absDiff < bestAbsDiff) {
						bestAbsDiff = absDiff;
						bestC = c;
						bestS = s;
					}
				}
			}
		}
		final double eKts = (Ce + bestC * Bk);
		final double nKts = (Cn + bestS * Bk);
		/** cog should be practically equal to dsrdCog. */
		final HdgKtsPlus hdgKts = new HdgKtsPlus(eKts, nKts, /* doublesAreHdgkts= */false,
				/* stateVectorType= */StateVectorType.MOTOR);
		return hdgKts;
	}
}
