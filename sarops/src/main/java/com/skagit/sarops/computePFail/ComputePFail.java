package com.skagit.sarops.computePFail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

import com.skagit.sarops.model.Sortie;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.CpaCalculator;
import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.sarops.tracker.ParticlesFile;
import com.skagit.sarops.tracker.lrcSet.LateralRangeCurve;
import com.skagit.sarops.tracker.lrcSet.LrcSet;
import com.skagit.util.TimeUtilities;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.navigation.LatLng3;

public class ComputePFail {

	public static class PdInfo {
		final public Sortie.Leg _leg;
		final public CpaCalculator.Result _bestResult;

		public PdInfo(final Sortie.Leg leg,
				final CpaCalculator.Result bestResult) {
			_leg = leg;
			_bestResult = bestResult;
		}

		public long getBestRefSecs() {
			return _bestResult == null ? _leg.getLegRefSecs0() :
					_bestResult.getCpaRefSecs();
		}

		public double getPFail(final DetectValues.PFailType pFailType) {
			return _bestResult == null ? Double.NaN :
					_bestResult.getPFail(pFailType);
		}

		public String getString() {
			String s = "";
			for (int k = 0; k < 2; ++k) {
				final GreatCircleArc myGca;
				final GreatCircleArc toCpa;
				if (k == 0) {
					myGca = _leg.getGca();
					toCpa = GreatCircleArc.CreateGca(_bestResult.getSruAtCpa(),
							_bestResult.getParticleAtCpa());
				} else {
					myGca = GreatCircleArc.CreateGca(_bestResult.getPtclStart(),
							_bestResult.getPtclStop());
					toCpa = GreatCircleArc.CreateGca(_bestResult.getParticleAtCpa(),
							_bestResult.getSruAtCpa());
				}
				if (k == 0) {
					s += String.format("\nLeg#[%d]", _leg.getEdgeNumber());
				} else {
					s += "\nPrtcl";
				}
				s += String.format(": %s\n    toCpa: %s",
						myGca.getStraightforwardString(),
						toCpa.getStraightforwardString());
			}
			final String cpaTimeString =
					TimeUtilities.formatTime(_bestResult.getCpaRefSecs(),
							/* includeSecs= */true, /* trimAtZ= */false);
			s += String.format(
					"\n  CpaTime[%s]  CpaNmi[%.4f] ftPFail[%.4f] AiftPFail[%.4f]\n",
					cpaTimeString, _bestResult.getCpaNmi(),
					_bestResult.getPFail(DetectValues.PFailType.FT),
					_bestResult.getPFail(DetectValues.PFailType.AIFT));
			return s;
		}

		@Override
		public String toString() {
			return getString();
		}
	}

	public static TreeMap<LateralRangeCurve, PdInfo[]> getPdInfosForLegs(
			final SimCaseManager.SimCase simCase,
			final ParticlesFile particlesFile, final ParticleIndexes prtclIndxs,
			final Sortie sortie, final boolean forOptnOnly,
			final DetectValues.PFailType pFailType) {
		/** We get possibly an abbreviated list of legs for this pFailType. */
		final List<Sortie.Leg> legList = sortie.getLegList(pFailType);
		final int nLegs = legList.size();
		final long distressRefSecs =
				particlesFile.getDistressRefSecs(prtclIndxs);
		long expirationRefSecs = particlesFile.getExpirationRefSecs(prtclIndxs);
		if (expirationRefSecs < 0) {
			expirationRefSecs = Long.MAX_VALUE / 2;
		}
		final long[] refSecsS = particlesFile.getRefSecsS();
		final TreeMap<LateralRangeCurve, ArrayList<PdInfo>> lrcToPdInfoList =
				new TreeMap<>();
		for (int kLeg = 0; kLeg < nLegs; ++kLeg) {
			final Sortie.Leg leg = legList.get(kLeg);
			final TreeMap<LateralRangeCurve, CpaCalculator.Result> bestResultsForThisLegAndLrc =
					new TreeMap<>();
			final long legRefSecs0 = leg.getLegRefSecs0();
			if (legRefSecs0 >= expirationRefSecs) {
				break;
			}
			final long legRefSecs1 =
					Math.min(leg.getLegRefSecs1(), expirationRefSecs);
			if (legRefSecs0 >= legRefSecs1) {
				continue;
			}
			final CpaCalculator cpaCalculator = new CpaCalculator(leg);
			/**
			 * Loop through the time intervals and process those that intersect
			 * this Leg. If we're not using every time interval, just use one
			 * interval; the leg itself. Our code has guaranteed that each leg has
			 * positive duration and length.
			 */
			assert (legRefSecs1 > legRefSecs0 && leg.getGca()
					.getTtlNmi() > 0d) : "Vacuous leg should be impossible here.";
			final long[] intrvlRefSecsS =
					pFailType == DetectValues.PFailType.AIFT ? refSecsS :
							new long[] { legRefSecs0, legRefSecs1 };
			/**
			 * The following routine finds the first index we are interested in,
			 * and the first one that we are not interested in.
			 */
			final int[] startAndStopIndexes =
					getStartAndStopIndexes(intrvlRefSecsS, legRefSecs0, legRefSecs1);
			final int startIndex = startAndStopIndexes[0];
			final int stopIndex = startAndStopIndexes[1];
			for (int k1 = startIndex; k1 < stopIndex; ++k1) {
				final long intrvlRefSecs0 =
						Math.max(intrvlRefSecsS[k1], legRefSecs0);
				final long intrvlRefSecs1 =
						Math.min(intrvlRefSecsS[k1 + 1], legRefSecs1);
				int nPasses;
				if (distressRefSecs <= intrvlRefSecs0 ||
						intrvlRefSecs1 <= distressRefSecs) {
					nPasses = 1;
				} else {
					nPasses = 2;
				}
				for (int iPass = 0; iPass < nPasses; ++iPass) {
					final long refSecs0, refSecs1;
					if (nPasses == 1) {
						refSecs0 = intrvlRefSecs0;
						refSecs1 = intrvlRefSecs1;
					} else {
						if (iPass == 0) {
							refSecs0 = intrvlRefSecs0;
							refSecs1 = distressRefSecs;
						} else {
							refSecs0 = distressRefSecs;
							refSecs1 = intrvlRefSecs1;
						}
					}
					final LatLng3 prtclLatLng0 =
							particlesFile.getLatLng(refSecs0, prtclIndxs);
					final LatLng3 prtclLatLng1 =
							particlesFile.getLatLng(refSecs1, prtclIndxs);
					final int objectTypeId =
							particlesFile.getObjectTypeId(refSecs0, prtclIndxs);
					final boolean closeEnoughToCompute = leg.closeEnoughToCompute(
							objectTypeId, prtclLatLng0, prtclLatLng1);
					if (!closeEnoughToCompute) {
						/** We cannot have a detection in this interval. */
						continue;
					}

					final LrcSet lrcSet =
							sortie.getLrcSet(objectTypeId, /* viz2= */forOptnOnly);
					final int nLrcs = lrcSet == null ? 0 : lrcSet.getNLrcs();
					for (int k = 0; k < nLrcs; ++k) {
						final LateralRangeCurve lrc = lrcSet.getLrc(k);
						final CpaCalculator.Result thisResultForLrc =
								lrc.getCpaCalculatorResult(cpaCalculator, refSecs0,
										refSecs1, prtclLatLng0, prtclLatLng1, prtclIndxs);
						if (thisResultForLrc == null) {
							/** We were never legal in this interval. */
							continue;
						}
						/**
						 * This Lrc's best, over its tPairs for this interval, was
						 * stored in thisResultForLrc.getTempPFailValue()
						 */
						final double tempPFailValue =
								thisResultForLrc.getTempPFailValue();
						final CpaCalculator.Result bestResultForThisLegAndLrc =
								bestResultsForThisLegAndLrc.get(lrc);
						if (bestResultForThisLegAndLrc == null ||
								tempPFailValue < bestResultForThisLegAndLrc
										.getTempPFailValue()) {
							/** thisResultForLrc wins. */
							bestResultsForThisLegAndLrc.put(lrc, thisResultForLrc);
						}
					}
				}
			}

			/** For this leg, we have a map from LRC to PdInfo. */
			final int nLrcs = bestResultsForThisLegAndLrc.size();
			final Iterator<Map.Entry<LateralRangeCurve, CpaCalculator.Result>> it0 =
					bestResultsForThisLegAndLrc.entrySet().iterator();
			for (int k = 0; k < nLrcs; ++k) {
				final Map.Entry<LateralRangeCurve, CpaCalculator.Result> entry =
						it0.next();
				final LateralRangeCurve lrc = entry.getKey();
				final CpaCalculator.Result bestResultForThisLegAndLrc =
						entry.getValue();
				/**
				 * We have our result over all subIntervals (for changing object
				 * types, tPairs, and possibly using all time-steps) of this leg for
				 * this Lrc, in its tempPFailValue. Copy it over to the pFail we're
				 * interested in, and set its tempPFailValue to Nan.
				 */
				bestResultForThisLegAndLrc.setTempPFailToPfail(pFailType);
				final PdInfo pdInfo = new PdInfo(leg, bestResultForThisLegAndLrc);
				ArrayList<PdInfo> thesePdInfos = lrcToPdInfoList.get(lrc);
				if (thesePdInfos == null) {
					thesePdInfos = new ArrayList<>();
					lrcToPdInfoList.put(lrc, thesePdInfos);
				}
				thesePdInfos.add(pdInfo);
			}
		}

		/** We now have Lrc->List<PdInfo>. */
		final int nLrcs = lrcToPdInfoList.size();
		final Iterator<Map.Entry<LateralRangeCurve, ArrayList<PdInfo>>> it1 =
				lrcToPdInfoList.entrySet().iterator();
		final TreeMap<LateralRangeCurve, PdInfo[]> lrcToPdInfoArray =
				new TreeMap<>();
		for (int k = 0; k < nLrcs; ++k) {
			final Map.Entry<LateralRangeCurve, ArrayList<PdInfo>> entry =
					it1.next();
			final LateralRangeCurve lrc = entry.getKey();
			final ArrayList<PdInfo> pdInfoList = entry.getValue();
			final int nPdInfos = pdInfoList.size();
			final PdInfo[] pdInfoArray = pdInfoList.toArray(new PdInfo[nPdInfos]);
			lrcToPdInfoArray.put(lrc, pdInfoArray);
		}
		return lrcToPdInfoArray;
	}

	private static int[] getStartAndStopIndexes(final long[] intrvlRefSecsS,
			final long legRefSecs0, final long legRefSecs1) {
		final int k0 = Arrays.binarySearch(intrvlRefSecsS, (int) legRefSecs0);
		final int[] answer = new int[2];
		if (k0 < 0) {
			answer[0] = Math.max(0, -k0 - 2);
		} else {
			answer[0] = k0;
		}
		final int nIntrvls = intrvlRefSecsS.length - 1;
		final int k1 = Arrays.binarySearch(intrvlRefSecsS, (int) legRefSecs1);
		answer[1] = Math.min(k1 < 0 ? -k1 - 1 : k1, nIntrvls);
		return answer;
	}

	public static Stack<PdInfoPlus> getSurvivingLegs(
			final ComputePFail.PdInfo[] pdInfosOfLegs,
			final double distinctDetectionThresholdMins,
			final DetectValues.PFailType pFailType) {
		final Stack<PdInfoPlus> survivors = new Stack<>();
		final int nPdInfos = pdInfosOfLegs == null ? 0 : pdInfosOfLegs.length;
		final long distinctDetectionThresholdSecs =
				Math.round(60d * distinctDetectionThresholdMins);
		for (int k = 0; k < nPdInfos; ++k) {
			final ComputePFail.PdInfo pdInfo = pdInfosOfLegs[k];
			PdInfoPlus pdInfoPlus = new PdInfoPlus(pdInfo, k);
			final CpaCalculator.Result bestResult = pdInfo._bestResult;
			if (bestResult != null) {
				if (survivors.size() > 0) {
					final PdInfoPlus oldPdInfoPlus = survivors.pop();
					final ComputePFail.PdInfo oldPdInfo = oldPdInfoPlus._pdInfo;
					final long timeDiff =
							pdInfo.getBestRefSecs() - oldPdInfo.getBestRefSecs();
					if (timeDiff >= distinctDetectionThresholdSecs) {
						/**
						 * They're far apart in time. Save the old one; we'll save the
						 * new one later on.
						 */
						survivors.push(oldPdInfoPlus);
					} else {
						/** Pick one of them to save. */
						final double pFail = pdInfo.getPFail(pFailType);
						final double oldPFail = oldPdInfo.getPFail(pFailType);
						pdInfoPlus = pFail < oldPFail ? pdInfoPlus : oldPdInfoPlus;
					}
				}
				/**
				 * If they were far apart in time, the following saves the new one.
				 * Otherwise, the following saves the better one.
				 */
				survivors.push(pdInfoPlus);
			}
		}
		return survivors;
	}

	/** This can NOT be used for pFailType == NFT. */
	public static double computeFtPFail(final SimCaseManager.SimCase simCase,
			final ParticlesFile particlesFile, final ParticleIndexes prtclIndxs,
			final Sortie sortie, final boolean forOptnOnly,
			final DetectValues.PFailType pFailType,
			final boolean updateParticlesFile) {
		if (pFailType == DetectValues.PFailType.NFT) {
			return Double.NaN;
		}
		/**
		 * Each PdInfo[] in the following will start by being smaller than
		 * sortie's distinct input legs. Its length will depend on pFailType.
		 */
		double grandPFail = 1d;
		final TreeMap<LateralRangeCurve, ComputePFail.PdInfo[]> lrcToPdInfos =
				ComputePFail.getPdInfosForLegs(simCase, particlesFile, prtclIndxs,
						sortie, forOptnOnly, pFailType);
		final int nLrcs = lrcToPdInfos.size();
		final Iterator<Map.Entry<LateralRangeCurve, ComputePFail.PdInfo[]>> it =
				lrcToPdInfos.entrySet().iterator();
		for (int k0 = 0; k0 < nLrcs; ++k0) {
			final Map.Entry<LateralRangeCurve, ComputePFail.PdInfo[]> entry =
					it.next();
			final LateralRangeCurve lrc = entry.getKey();
			final double distinctDetectionThresholdMins =
					lrc.getDistinctDetectionThresholdMins();
			final ComputePFail.PdInfo[] pdInfos = entry.getValue();
			final Stack<PdInfoPlus> survivors = getSurvivingLegs(pdInfos,
					distinctDetectionThresholdMins, pFailType);

			for (final PdInfoPlus pdInfoSpec : survivors) {
				final ComputePFail.PdInfo pdInfo = pdInfoSpec._pdInfo;
				final double pFail = pdInfo.getPFail(pFailType);
				if (updateParticlesFile) {
					final long stopRefSecs = pdInfo._leg.getLegRefSecs1();
					particlesFile.updateCumPFails(prtclIndxs, pFail, stopRefSecs);
				}
				grandPFail *= pFail;
			}
		}
		return grandPFail;
	}
}
// 28.5786628723145 -83.986038208008
