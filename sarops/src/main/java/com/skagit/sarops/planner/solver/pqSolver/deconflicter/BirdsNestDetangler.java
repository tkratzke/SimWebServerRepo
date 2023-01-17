package com.skagit.sarops.planner.solver.pqSolver.deconflicter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.planner.ParticlesManager;
import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.plannerModel.PatternKind;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.planner.posFunction.PosFunction;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.planner.posFunction.pvValueArrayPlus.PvValueArrayPlus;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.sarops.tracker.ParticleIndexes.ParticleIndexesState;
import com.skagit.sarops.tracker.lrcSet.LrcSet;
import com.skagit.sarops.util.SimGlobalStrings;
import com.skagit.sarops.util.patternUtils.MyStyle;
import com.skagit.util.CombinatoricTools;
import com.skagit.util.MathX;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.cdf.CcwGcas;
import com.skagit.util.cdf.Cdf;
import com.skagit.util.cdf.Cdf.BadCdfException;
import com.skagit.util.cdf.PolygonCdf;
import com.skagit.util.geometry.gcaSequence.DistinctLatLngFinder;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.geometry.gcaSequence.Loop3Statics;
import com.skagit.util.geometry.loopsFinder.TopLoopCreator;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.TangentCylinder;

public class BirdsNestDetangler {
	final private static double _NmiToR = MathX._NmiToR;

	final PosFunction _ftPosFunction;
	final PosFunction _nftPosFunction;
	final private PvValue[] _inputBirdsNest;
	final private BearingResult[] _bearingResults;
	final private BearingResult _bestBearingResult;
	final private Loop3 _topLoop;

	final private static boolean _UseAccordion2 = true;
	final private static boolean _UseExpansion = false;
	final private static boolean _SkinnifyBirdsNests = false;

	final private static int _MaxNAnglesToTry = 8;
	final private static boolean _UseCriticalGcas = true;

	/**
	 * inputBirdsNest is a canonically complete set, but it contains nulls for
	 * the PttrnVbls that are not in this BirdsNest.
	 *
	 * @param fullPlus
	 */
	BirdsNestDetangler(final Planner planner, final PosFunction ftPosFunction,
			final PosFunction nftPosFunction, final PvValueArrayPlus fullPlus,
			final PvValue[] inputBirdsNest) {
		_ftPosFunction = ftPosFunction;
		_nftPosFunction = nftPosFunction;
		_inputBirdsNest = inputBirdsNest;

		/** Boilerplate Constants. */
		final SimCaseManager.SimCase simCase = planner.getSimCase();
		final MyLogger logger = SimCaseManager.getLogger(simCase);
		final SimGlobalStrings simGlobalStrings = simCase.getSimGlobalStrings();
		final ParticlesManager particlesManager = planner.getParticlesManager();
		final PlannerModel plannerModel = planner.getPlannerModel();
		final int nPttrnVbls = plannerModel.getNPttrnVbls();
		final Model simModel = plannerModel.getSimModel();
		final int nStages = Accordion2._NStages;

		/** Compute topLoop. */
		final ArrayList<Loop3> birdsNestInputLoops = new ArrayList<>();
		@SuppressWarnings("unused")
		double ttlSqNmiLoops = 0d;
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PvValue pvValue = _inputBirdsNest[grandOrd];
			if (pvValue == null) {
				continue;
			}
			final CcwGcas excCcwGcas = pvValue.getTightExcCcwGcas();
			final Loop3 ccwLoop = excCcwGcas.getCcwLoop();
			ttlSqNmiLoops += ccwLoop.getSqNmi();
			birdsNestInputLoops.add(ccwLoop.clone());
		}
		final int tooManyLoopsForComplicated =
				simGlobalStrings.getTooManyLoopsForComplicated();
		final TopLoopCreator topLoopCreator =
				new TopLoopCreator(logger, "BirdsNestDetangler",
						birdsNestInputLoops, tooManyLoopsForComplicated);
		final Loop3 topLoopB = topLoopCreator._topLoop;

		final DistinctLatLngFinder distinctLatLngList =
				new DistinctLatLngFinder(topLoopB.getLatLngArray(),
						/* connectLastToFirst= */true);
		final LatLng3[] distinctLatLngArray =
				distinctLatLngList._distinctLatLngArray;
		final Loop3 topLoopA = Loop3Statics.getConvexHull(logger,
				Arrays.asList(distinctLatLngArray), topLoopB.isClockwise());
		final double sqNmiA = topLoopA.getSqNmi();
		if (_UseExpansion && sqNmiA < ttlSqNmiLoops) {
			final double expansionFactor = ttlSqNmiLoops / sqNmiA;
			final LatLng3 topLoopCoM = topLoopA.getCenterOfMass(logger);
			final TangentCylinder.FlatLatLng flatCoM =
					TangentCylinder.convertToCentered(topLoopCoM);
			final TangentCylinder tangentCylinderA =
					flatCoM.getOwningTangentCylinder();
			final int nGcasInTopLoopA = topLoopA.getNGcas();
			final LatLng3[] openLoopA = new LatLng3[nGcasInTopLoopA];
			for (int k = 0; k < nGcasInTopLoopA; ++k) {
				final LatLng3 topLoopLatLng = topLoopA.getLatLng(k);
				final TangentCylinder.FlatLatLng flatLatLng =
						tangentCylinderA.convertToMyFlatLatLng(topLoopLatLng);
				final double newEast = flatLatLng.getEastOffset() * expansionFactor;
				final double newNorth =
						flatLatLng.getNorthOffset() * expansionFactor;
				final TangentCylinder.FlatLatLng flatLatLngA =
						tangentCylinderA.new FlatLatLng(newEast, newNorth);
				openLoopA[k] = LatLng3.makeBasicLatLng3(flatLatLngA);
			}
			final int loopId = 0;
			final int subId = 0;
			final int flag =
					Loop3Statics.createGenericFlag(topLoopA.isClockwise());
			final int ancestorId = -1;
			final boolean logChanges = false;
			final boolean debug = false;
			_topLoop = Loop3.getLoop(logger, loopId, subId, flag, ancestorId,
					openLoopA, logChanges, debug);
		} else {
			_topLoop = topLoopA;
		}

		/** Build objectTypeToSweepWidth. */
		@SuppressWarnings("unchecked")
		final TreeMap<Integer, Double>[] objectTypeToSweepWidths =
				new TreeMap[nPttrnVbls];
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			objectTypeToSweepWidths[grandOrd] = null;
		}
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PvValue pvValue = _inputBirdsNest[grandOrd];
			if (pvValue == null) {
				objectTypeToSweepWidths[grandOrd] = null;
				continue;
			}
			final TreeMap<Integer, Double> objectTypeToSweepWidth =
					new TreeMap<>();
			final PatternVariable pv = pvValue.getPv();
			final Map<Integer, LrcSet> objectTypeToLrcSet = pv.getViz2LrcSets();
			for (final Map.Entry<Integer, LrcSet> entry : objectTypeToLrcSet
					.entrySet()) {
				final Integer objectType = entry.getKey();
				final LrcSet lrcSet = entry.getValue();
				final double sweepWidth = lrcSet.getSweepWidth();
				if (sweepWidth > 0d) {
					objectTypeToSweepWidth.put(objectType, sweepWidth);
				}
			}
			objectTypeToSweepWidths[grandOrd] = objectTypeToSweepWidth;
		}

		/** Compute the centers of the pvValues. */
		final LatLng3[] centersOfMass = new LatLng3[nPttrnVbls];
		Arrays.fill(centersOfMass, null);
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PvValue pvValue = _inputBirdsNest[grandOrd];
			if (pvValue != null) {
				centersOfMass[grandOrd] = pvValue.getCenter();
			}
		}

		/**
		 * Compute pvWeights for all pvValues in birdsNest, even the frozen, VS,
		 * and SS ones.
		 */
		final ParticleIndexes[] prtclIndxsS =
				_ftPosFunction.getParticleIndexesS();
		final int nPrtclIndxsS = prtclIndxsS.length;
		final double[] priors = _ftPosFunction.getPriors();
		final double[] pvWeights = new double[nPttrnVbls];
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PvValue pvValue = _inputBirdsNest[grandOrd];
			if (pvValue == null) {
				pvWeights[grandOrd] = 0d;
				continue;
			}
			final double pvWeight;
			/** Get the time fenceposts for this Pv. */
			final long firstFencePost = pvValue.getCstRefSecs();
			final long lastFencePost = pvValue.getEstRefSecs();
			final long[] fencePostRefSecsS = CombinatoricTools
					.getFenceposts(firstFencePost, lastFencePost, nStages);
			double weightedSweepWidth = 0d;
			for (int k = 0; k < nPrtclIndxsS; ++k) {
				final ParticleIndexes particleIndexes = prtclIndxsS[k];
				final double prior = priors[k];
				for (int iStage = 0; iStage < nStages; ++iStage) {
					final long refSecs = fencePostRefSecsS[iStage];
					final ParticleIndexesState prtclIndxsState = particleIndexes
							.refSecsToPrtclIndxsState(particlesManager, refSecs);
					final int objectType = prtclIndxsState.getObjectType();
					final Double sweepWidthD =
							objectTypeToSweepWidths[grandOrd].get(objectType);
					if (sweepWidthD != null) {
						final LatLng3 latLng = prtclIndxsState.getLatLng();
						if (_topLoop.borderOrInteriorContains(logger, latLng)) {
							final double summand = prior * sweepWidthD;
							weightedSweepWidth += summand;
						}
					}
				}
			}
			pvWeight = weightedSweepWidth * pvValue.getEplNmi();
			pvWeights[grandOrd] = pvWeight;
		}
		NumericalRoutines.normalizeWeights(pvWeights);

		/**
		 * Every relevant PvValue must get at least _MinShare * 1d/nRelevant.
		 */
		int nRelevant = 0;
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PvValue pvValue = _inputBirdsNest[grandOrd];
			if (pvValue != null && pvWeights[grandOrd] > 0d) {
				++nRelevant;
			}
		}
		final double minShare = simGlobalStrings.getDeconflicterMinShare();
		final double minWt = minShare / nRelevant;
		final double[] minWts = new double[nPttrnVbls];
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PvValue pvValue = _inputBirdsNest[grandOrd];
			minWts[grandOrd] = pvValue == null ? 0d : minWt;
		}
		/** Reweight pvWeights. */
		final int nWts = pvWeights.length;
		if (nWts > 0) {
			final double[] newWts = new double[nWts];
			for (int k = 0; k < nWts; ++k) {
				newWts[k] = 0d;
				final double wt = pvWeights[k];
				final double thisMinWt = minWts[k];
				newWts[k] = Math.max(thisMinWt, wt);
			}
			NumericalRoutines.normalizeWeights(newWts);
			System.arraycopy(newWts, 0, pvWeights, 0, nWts);
		}

		/**
		 * Find the critical Gcas. These are the angles of the longest sides of
		 * topLoop. Because we do not care if a hdg is (e.g.) 90 or 270, we
		 * "normalize" these hdgs to be less than 180.
		 */
		final ArrayList<GreatCircleArc> criticalGcaList = new ArrayList<>();
		final int nGcasInTopLoop = _topLoop.getNGcas();
		K0_LOOP: for (int k0 = 0; k0 < nGcasInTopLoop; ++k0) {
			final GreatCircleArc gca0a = _topLoop.getGca(k0);
			final double nmi0 = gca0a.getHaversine() / _NmiToR;
			final double hdg0a = gca0a.getRoundedInitialHdg();
			final double hdg0b = LatLng3.roundToLattice0_360L(hdg0a + 180d);
			final GreatCircleArc gca0;
			final double hdg0;
			if (hdg0b < hdg0a) {
				gca0 = gca0a.createReverse();
				hdg0 = hdg0b;
			} else {
				gca0 = gca0a;
				hdg0 = hdg0a;
			}
			/**
			 * If there is a Gca already in the list whose hdg is close to hdg0,
			 * then gca0 either replaces the incumbent or it gets forgotten. If
			 * there is no such incumbent, gca0 becomes one.
			 */
			for (int k1 = 0; k1 < criticalGcaList.size(); ++k1) {
				final GreatCircleArc gca1 = criticalGcaList.get(k1);
				final double hdg1 = gca1.getRoundedInitialHdg();
				final double delta1a = LatLng3.degsToEast180_180(hdg0, hdg1);
				final double delta1b = LatLng3.degsToEast180_180(hdg0, hdg1 + 180d);
				if (Math.abs(delta1a) < 360d / 16d || delta1b < 360d / 16d) {
					final double nmi1 = gca1.getHaversine() / _NmiToR;
					if (nmi0 > nmi1) {
						criticalGcaList.set(k1, gca0);
					}
					continue K0_LOOP;
				}
			}
			criticalGcaList.add(gca0);
		}

		/**
		 * Sort criticalGcaList by decreasing length and then Gca's standard
		 * comparator.
		 */
		final int nCriticalGcas = criticalGcaList.size();
		final GreatCircleArc[] criticalGcas =
				criticalGcaList.toArray(new GreatCircleArc[nCriticalGcas]);
		Arrays.sort(criticalGcas, new Comparator<GreatCircleArc>() {

			@Override
			public int compare(final GreatCircleArc gca0,
					final GreatCircleArc gca1) {
				final double nmi0 = gca0.getHaversine() / _NmiToR;
				final double nmi1 = gca1.getHaversine() / _NmiToR;
				if (nmi0 > nmi1) {
					return -1;
				}
				if (nmi0 < nmi1) {
					return 1;
				}
				return GreatCircleArc._ByZeroThenOne.compare(gca0, gca1);
			}
		});

		/**
		 * Put the ones not in _inputBirdsNest, plus the ones in _inputBirdsNest
		 * that we are not moving, into constSandbox.
		 */
		final PvValue[] constSandbox = new PvValue[nPttrnVbls];
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PvValue pvValue = _inputBirdsNest[grandOrd];
			if (pvValue == null) {
				constSandbox[grandOrd] = fullPlus.getPvValue(grandOrd);
				continue;
			}
			/** Don't move it if... */
			final PatternVariable pv = pvValue.getPv();
			final PatternKind patternKind = pv.getPatternKind();
			final boolean freezeThisOne = pv.getUserFrozenPvValue() != null ||
					pvValue == pv.getInitialPvValue() || patternKind.isSs() ||
					patternKind.isVs();
			if (freezeThisOne) {
				constSandbox[grandOrd] = pvValue;
			} else {
				constSandbox[grandOrd] = null;
			}
		}

		final int nAnglesToTry = _UseCriticalGcas ?
				Math.min(nCriticalGcas, _MaxNAnglesToTry) : _MaxNAnglesToTry;
		_bearingResults = new BearingResult[nAnglesToTry];
		BearingResult bestBearingResult = null;

		for (int k = 0; k < nAnglesToTry; ++k) {
			final GreatCircleArc gca;
			if (_UseCriticalGcas) {
				gca = criticalGcas[k];
			} else {
				final LatLng3 latLng0 = _topLoop.getCenterOfMass(logger);
				final double coreHdg = k * (180d / nAnglesToTry);
				final double nominalNmi = 5d;
				final double nominalR = nominalNmi * _NmiToR;
				final LatLng3 latLng1 =
						MathX.getLatLngX(latLng0, coreHdg, nominalR);
				gca = GreatCircleArc.CreateGca(latLng0, latLng1);
			}
			final double gcaHdg = gca.getRoundedInitialHdg();
			final LatLng3 latLng0 = gca.getLatLng0();
			final LatLng3[] topLoopLatLngArray = _topLoop.getLatLngArray();
			final double hdgOfUDirection = gcaHdg + 90d;
			final Twister twister = new Twister(latLng0, hdgOfUDirection);
			/** Form the twisted polygonCdf of topLoop. */
			final double[][] twPoints = new double[nGcasInTopLoop][2];
			for (int kInTopLoop = 0; kInTopLoop < nGcasInTopLoop; ++kInTopLoop) {
				final LatLng3 loopLatLng = topLoopLatLngArray[kInTopLoop];
				final double[] twLoopPoint = twister.convert(loopLatLng);
				twPoints[kInTopLoop] = twLoopPoint;
			}
			PolygonCdf twPolygonCdfX = null;
			try {
				twPolygonCdfX =
						new PolygonCdf(logger, twPoints, Cdf._QuietAboutIrregularity);
			} catch (final BadCdfException e1) {
			}
			final PolygonCdf twPolygonCdf = twPolygonCdfX;

			/**
			 * <pre>
			 * Sort the centers of mass (and hence the PvValues) by increasing
			 * twX, and keep track of where they came from. This is to retain some
			 * of the order that they were in upon arrival.
			 * The 4 elements of each twInfo array below are;
			 * twX, twY, realGrandOrd, and pvWt.
			 * </pre>
			 */
			final double[][] twInfos = new double[nPttrnVbls][4];
			for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
				twInfos[grandOrd][2] = grandOrd;
				final PvValue pvValue = _inputBirdsNest[grandOrd];
				if (pvValue == null || !(pvWeights[grandOrd] > 0d)) {
					/**
					 * For sorting purposes, we want the nulls to go at the end and
					 * since we sort by increasing twCenterOfMassX, we do the
					 * following.
					 */
					twInfos[grandOrd][0] = Double.MAX_VALUE;
					twInfos[grandOrd][1] = Double.MAX_VALUE;
					twInfos[grandOrd][3] = 0d;
				} else {
					final LatLng3 centerOfMass = centersOfMass[grandOrd];
					final double[] twCom = twister.convert(centerOfMass);
					twInfos[grandOrd][0] = twCom[0];
					twInfos[grandOrd][1] = twCom[1];
					twInfos[grandOrd][3] = pvWeights[grandOrd];
				}
			}

			/** Sort by increasing twX, twY, and grandOrd. */
			Arrays.sort(twInfos, NumericalRoutines._ByAllInOrder1);

			/** Find the first unmoving one that is in _inputBirdsNest. */
			int firstStoneWallGrandOrd = -1;
			for (int twGrandOrd = 0; twGrandOrd < nPttrnVbls; ++twGrandOrd) {
				final double[] twInfo = twInfos[twGrandOrd];
				final int grandOrd = (int) Math.round(twInfo[2]);
				final boolean notMovingAndInBirdsNest =
						constSandbox[grandOrd] != null &&
								_inputBirdsNest[grandOrd] != null;
				if (notMovingAndInBirdsNest) {
					firstStoneWallGrandOrd = twGrandOrd;
					break;
				}
			}

			/**
			 * Get the twisted coordinates of the boxes' centers and then convert
			 * them back. Also, with respect to this ordering of the PttrnVbls,
			 * set the pair of cums for each PttrnVbl.
			 */
			final double[][] cumPvWts = new double[nPttrnVbls][];
			double cumPvWt = 0d;
			for (int twGrandOrd = 0; twGrandOrd < nPttrnVbls; ++twGrandOrd) {
				final double[] twInfo = twInfos[twGrandOrd];
				final int grandOrd = (int) Math.round(twInfo[2]);
				final PvValue pvValue = _inputBirdsNest[grandOrd];
				final double pvWeight = pvWeights[grandOrd];
				if (pvValue == null || !(pvWeight > 0d)) {
					cumPvWts[twGrandOrd] = null;
					continue;
				}
				/** Update cums. */
				final double oldCumPvWt = cumPvWt;
				cumPvWt += pvWeight;
				cumPvWts[twGrandOrd] = new double[] { oldCumPvWt, cumPvWt };
			}

			/**
			 * Sandbox0 is what will come out of Accordion2. sandbox1 is what
			 * happens when we move them after Accordion2.
			 */
			final PvValue[] sandbox0 = constSandbox.clone();
			final PvValue[] sandbox1 = constSandbox.clone();
			/**
			 * Fill sandbox0 in two parts by looping through grandOrdX twice. Each
			 * time, start at frozenGrandOrdX. The first pass, increment
			 * grandOrdX. The second time, decrement grandOrdX.
			 */
			final Loop3[] bigLoops = new Loop3[nPttrnVbls];
			final Loop3[] accordionLoops = new Loop3[nPttrnVbls];
			for (int iPass = 0; iPass < 2; ++iPass) {
				final int idxInc = iPass == 0 ? 1 : -1;
				for (int kk = 1;; ++kk) {
					final int twGrandOrd = firstStoneWallGrandOrd + kk * idxInc;
					if (twGrandOrd < 0 || twGrandOrd >= nPttrnVbls) {
						/** Done with this pass. */
						break;
					}
					final double[] twInfo = twInfos[twGrandOrd];
					final int grandOrd = (int) Math.round(twInfo[2]);
					final PvValue pvValue0 = _inputBirdsNest[grandOrd];
					if (pvValue0 == null || constSandbox[grandOrd] != null) {
						continue;
					}
					/** We can move pvValue0. */
					final PatternVariable pv = plannerModel.grandOrdToPv(grandOrd);
					final PatternKind patternKind = pv.getPatternKind();
					final boolean firstTurnRight = plannerModel
							.getFirstTurnRight(patternKind, true, /* randomize= */true);
					final double firstTurnRightD = firstTurnRight ? 1d : -1d;

					if (pvWeights[grandOrd] > 0d) {
						/** Find the initial edges from cumPvWts. */
						final double[] twLtLw = new double[2];
						final double[] twRtLw = new double[2];
						final double[] twRtHh = new double[2];
						final double[] twLtHh = new double[2];
						final double leftCum = cumPvWts[twGrandOrd][0];
						final double rightCum = cumPvWts[twGrandOrd][1];
						try {
							final double eps = 0d;
							twPolygonCdf.cdfsToXy(logger, leftCum + eps, eps, twLtLw);
							twPolygonCdf.cdfsToXy(logger, rightCum - eps, eps, twRtLw);
							twPolygonCdf.cdfsToXy(logger, rightCum - eps, 1d - eps,
									twRtHh);
							twPolygonCdf.cdfsToXy(logger, leftCum + eps, 1d - eps,
									twLtHh);
						} catch (final BadCdfException e) {
						}
						final double[][][] regionsBetweenLeftAndRightCum =
								twPolygonCdf.getRegions(logger, leftCum, rightCum);
						double minTwX = Double.POSITIVE_INFINITY;
						double maxTwX = Double.NEGATIVE_INFINITY;
						double minTwY = Double.POSITIVE_INFINITY;
						double maxTwY = Double.NEGATIVE_INFINITY;
						/**
						 * As of Jan 14, 2019, the following is always 1, because we
						 * don't find all parts of the PolygonCdf between left and right
						 * cum.
						 */
						final int nRegions = regionsBetweenLeftAndRightCum.length;
						for (int k0 = 0; k0 < nRegions; ++k0) {
							final double[][] region = regionsBetweenLeftAndRightCum[k0];
							final int nPoints = region.length;
							for (int k1 = 0; k1 < nPoints; ++k1) {
								final double[] point = region[k1];
								final double x = point[0];
								final double y = point[1];
								minTwX = Math.min(minTwX, x);
								maxTwX = Math.max(maxTwX, x);
								minTwY = Math.min(minTwY, y);
								maxTwY = Math.max(maxTwY, y);
							}
						}
						final LatLng3[] bigOpenLoopArray =
								new LatLng3[] { twister.unconvert(minTwX, minTwY), //
										twister.unconvert(maxTwX, minTwY), //
										twister.unconvert(maxTwX, maxTwY), //
										twister.unconvert(minTwX, maxTwY) //
								};
						final int loopId = grandOrd;
						final int subId = 0;
						final boolean isClockwise = false;
						final int flag = Loop3Statics.createGenericFlag(isClockwise);
						final int ancestorId = -1;
						final boolean logChanges = false;
						final boolean debug = false;
						final Loop3 bigLoop = Loop3.getLoop(logger, loopId, subId, flag,
								ancestorId, bigOpenLoopArray, logChanges, debug);
						bigLoops[twGrandOrd] = bigLoop;

						if (_UseAccordion2) {
							final long cstRefSecs = pvValue0.getCstRefSecs();
							final int searchDurationSecs =
									pvValue0.getSearchDurationSecs();
							final Accordion2 accordion2 = new Accordion2(planner,
									prtclIndxsS, priors, pv, cstRefSecs, searchDurationSecs,
									twister, minTwX, maxTwX, minTwY, maxTwY);
							final double[] twCenterXy = accordion2._winningTwXy;
							final double widthOver2 = accordion2._winningWidth / 2d;
							final double heightOver2 = accordion2._winningHeight / 2d;
							twLtLw[0] = twCenterXy[0] - widthOver2;
							twLtLw[1] = twCenterXy[1] - heightOver2;
							twRtLw[0] = twCenterXy[0] + widthOver2;
							twRtLw[1] = twCenterXy[1] - heightOver2;
							twRtHh[0] = twCenterXy[0] + widthOver2;
							twRtHh[1] = twCenterXy[1] + heightOver2;
							twLtHh[0] = twCenterXy[0] - widthOver2;
							twLtHh[1] = twCenterXy[1] + heightOver2;
							final LatLng3[] accordionOpenLoop = new LatLng3[4];
							accordionOpenLoop[0] =
									twister.unconvert(twLtLw[0], twLtLw[1]);
							accordionOpenLoop[1] =
									twister.unconvert(twRtLw[0], twRtLw[1]);
							accordionOpenLoop[2] =
									twister.unconvert(twRtHh[0], twRtHh[1]);
							accordionOpenLoop[3] =
									twister.unconvert(twLtHh[0], twLtHh[1]);
							final Loop3 accordionLoop =
									Loop3.getLoop(logger, loopId, subId, flag, ancestorId,
											accordionOpenLoop, logChanges, debug);
							accordionLoops[twGrandOrd] = accordionLoop;
						} else {
							accordionLoops[twGrandOrd] = bigLoop;
						}
						final double twLtB = (twLtLw[0] + twLtHh[0]) / 2d;
						final double twRtB = (twRtLw[0] + twRtHh[0]) / 2d;
						final double twLwB = (twLtLw[1] + twRtLw[1]) / 2d;
						final double twHhB = (twLtHh[1] + twRtHh[1]) / 2d;
						final double twCenterXB = (twLtB + twRtB) / 2d;
						final double twCenterYB = (twLwB + twHhB) / 2d;
						final LatLng3 center =
								twister.unconvert(twCenterXB, twCenterYB);

						final double alongNmi = (twHhB - twLwB) / _NmiToR;
						/** By construction, the following is nonnegative. */
						final double absAcrossNmi = (twRtB - twLtB) / _NmiToR;
						final PvValue pvValue;
						final long cstRefSecs = pv.getPvCstRefSecs();
						final int searchDurationSecs = pv.getPvRawSearchDurationSecs();
						if (alongNmi >= absAcrossNmi) {
							pvValue = PvValue.createPvValue(pv, cstRefSecs,
									searchDurationSecs, center, Math.toRadians(90d - gcaHdg),
									alongNmi * _NmiToR,
									absAcrossNmi * _NmiToR * firstTurnRightD);
						} else {
							pvValue =
									PvValue.createPvValue(pv, cstRefSecs, searchDurationSecs,
											center, Math.toRadians(90d - (gcaHdg + 90d)),
											absAcrossNmi * _NmiToR,
											alongNmi * _NmiToR * firstTurnRightD);
						}
						if (pvValue == null) {
							_bestBearingResult = null;
							return;
						}

						sandbox0[grandOrd] = pvValue;
						if (_SkinnifyBirdsNests) {
							final double desiredBufferNmi = pv.getExcBufferNmi();
							final double absTsNmi = pvValue.computeTsNmi();
							final double currentBufferNmi = absTsNmi / 2d;
							if (currentBufferNmi < desiredBufferNmi) {
								final double acrossNmiToLose =
										(desiredBufferNmi - currentBufferNmi) * 2d;
								final double bigEnoughNmi = absAcrossNmi;
								final double bestPossibleAbsAcrossNmi =
										absAcrossNmi - acrossNmiToLose;
								final int nSearchLegs = pvValue.computeNSearchLegs();
								final int nSearchLegsToLose = Math.min(nSearchLegs - 2,
										(int) Math.ceil(acrossNmiToLose / absTsNmi));
								final double tsToSlNmi = nSearchLegsToLose * absTsNmi;
								final double newAbsAcrossNmiWeUse =
										absAcrossNmi - tsToSlNmi;
								final double sllNmi = pvValue.computeSllNmi();
								final int nSearchLegsLeft = nSearchLegs - nSearchLegsToLose;
								final double newSllNmi =
										sllNmi + tsToSlNmi / nSearchLegsLeft;
								final double newAlongNmiWeUse = newSllNmi + absTsNmi;
								final PvValue skinnifiedPvValue = PvValue.createPvValue(pv,
										cstRefSecs, searchDurationSecs, center,
										Math.toRadians(90d - gcaHdg),
										newAlongNmiWeUse * _NmiToR,
										newAbsAcrossNmiWeUse * _NmiToR);
								sandbox1[grandOrd] =
										skinnifiedPvValue == null ? pvValue : skinnifiedPvValue;
							} else {
								/** No skinnifying necessary. */
								sandbox1[grandOrd] = pvValue;
							}
						} else {
							sandbox1[grandOrd] = pvValue;
						}
					} else {
						bigLoops[twGrandOrd] = null;
						accordionLoops[twGrandOrd] = null;
						final MyStyle onMarsMyStyle =
								new MyStyle(simModel.getExtent(), pv);
						final PvValue onMarsPvValue = new PvValue(pv, onMarsMyStyle);
						sandbox1[grandOrd] = onMarsPvValue;
					}
				}
			}

			final PvValueArrayPlus plus0 =
					new PvValueArrayPlus(planner, sandbox0);
			final PvValueArrayPlus finalPlus =
					new PvValueArrayPlus(planner, sandbox1);
			final BearingResult bearingResult = new BearingResult(this, gca,
					plus0, finalPlus, bigLoops, accordionLoops);
			_bearingResults[k] = bearingResult;
			final double thisPos = finalPlus.getPos(_ftPosFunction);
			final double bestPos = bestBearingResult == null ? -1d :
					bestBearingResult._finalPlus.getPos(_ftPosFunction);
			if (thisPos > bestPos) {
				bestBearingResult = bearingResult;
			}
			bestBearingResult = bearingResult;
		}
		_bestBearingResult = bestBearingResult;
	}

	public String getShortString() {
		String shortString = null;
		for (final PvValue pvValue : getInputBirdsNest()) {
			if (pvValue == null) {
				continue;
			}
			final String id = pvValue.getPv().getId();
			if (shortString == null) {
				shortString = id;
			} else {
				shortString += "_" + id;
			}
		}
		return shortString;
	}

	public Loop3 getTopLoop() {
		return _topLoop;
	}

	public BearingResult getBestBearingResult() {
		return _bestBearingResult;
	}

	public PvValue[] getInputBirdsNest() {
		return _inputBirdsNest;
	}

	public BearingResult[] getBearingResults() {
		return _bearingResults;
	}
}