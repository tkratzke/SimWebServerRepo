package com.skagit.sarops.planner.writingUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;

import com.skagit.sarops.computePFail.DetectValues;
import com.skagit.sarops.planner.ParticlesManager;
import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.planner.posFunction.PosFunction;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.planner.posFunction.pFailsCache.PFailsCache;
import com.skagit.sarops.planner.posFunction.pvValueArrayPlus.PvValueArrayPlus;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.sarops.tracker.ParticlesFile;

public class PlannerReportsData {

	final public Planner _planner;
	final public PvValueArrayPlus _pvValueArrayPlus;
	final public String[] _pvStrings;
	final public int[] _ots;
	final public String[] _otStrings;
	final public ParticleData[] _allParticleDatas;
	final public ParticleData[] _passLa;
	final public ParticleData[] _passBoth;
	final public ParticleData[][] _passBothByPv;

	public PlannerReportsData(final Planner planner,
			final PosFunction posFunction, final PvValueArrayPlus plus) {
		/** Boilerplate. */
		_planner = planner;
		final ParticlesManager particlesManager = planner.getParticlesManager();
		final SimCaseManager.SimCase simCase = planner.getSimCase();
		final PlannerModel plannerModel = planner.getPlannerModel();
		final ParticlesFile particlesFile = particlesManager.getParticlesFile();
		final long[] refSecs = particlesFile.getRefSecsS();
		final int nRefSecs = refSecs.length;
		final long lastRefSecs = refSecs[nRefSecs - 1];
		final PFailsCache pFailsCache = planner.getPFailsCache();
		final long minCstRefSecs = plannerModel.getMinPvRefSecs();
		final long maxCstRefSecs = plannerModel.getMaxPvRefSecs();
		SimCaseManager.out(simCase, "Gathering PlannerReportsData.");

		/** We are gathering data for all of the particles. */
		final ParticleIndexes[] prtclIndxsS = posFunction.getParticleIndexesS();
		final int nPrtcls = prtclIndxsS.length;
		final PosFunction.EvalType evalType = posFunction._evalType;
		final boolean forOptnOnly = evalType._useViz2;
		final DetectValues.PFailType pFailType = evalType._pFailType;
		/**
		 * Gather the viz1 object types and distill the array of SruSolutions.
		 */
		final HashSet<Integer> viz1 = plannerModel.getViz1ObjectTypeIds();
		final int nOts = viz1.size();
		_ots = new int[nOts];
		_otStrings = new String[nOts];
		final Iterator<Integer> it = viz1.iterator();
		for (int k = 0; k < nOts; ++k) {
			_ots[k] = it.next();
		}
		Arrays.sort(_ots);
		_pvValueArrayPlus = plus;
		final PvValue[] nonNullPvValues =
				_pvValueArrayPlus.getNonNullPvValues();
		/** Make printable names for both. */
		final int nNonNullPttrnVbls = nonNullPvValues.length;
		_pvStrings = new String[nNonNullPttrnVbls];
		for (int idx = 0; idx < nNonNullPttrnVbls; ++idx) {
			final PvValue pvValue = nonNullPvValues[idx];
			final PatternVariable pv = pvValue.getPv();
			_pvStrings[idx] = String.format("Pttrn-%s", pv.getId());
		}
		for (int kObjectType = 0; kObjectType < nOts; ++kObjectType) {
			final int ot = _ots[kObjectType];
			_otStrings[kObjectType] = String.format("OT-%d", ot);
		}

		/** Get the PFails. */
		final DetectValues[][] detectValuesArrays =
				new DetectValues[nNonNullPttrnVbls][];
		for (int k = 0; k < nNonNullPttrnVbls; ++k) {
			final PvValue pvValue = nonNullPvValues[k];
			detectValuesArrays[k] = pFailsCache.getDetectValuesArray(_planner,
					forOptnOnly, pFailType, prtclIndxsS, 0, nPrtcls, pvValue);
		}

		/** Gather the data and sort by particle. */
		final ArrayList<ParticleData> allParticleDatas = new ArrayList<>();
		/** Need to accumulate the normalizing constant for the bravo prior. */
		double normalizingConstant = 0d;
		for (int k = 0; k < nPrtcls; ++k) {
			final ParticleIndexes prtclIndxs = prtclIndxsS[k];
			final double initWt = particlesFile.getInitPrior(prtclIndxs);
			final double pfAlpha =
					particlesFile.getCumPFail(lastRefSecs, prtclIndxs);
			normalizingConstant += initWt * pfAlpha;
		}

		/**
		 * Build the ParticleData lists; there are global lists of ParticleDatas
		 * and Pv-specific lists of ParticleDatas.
		 */
		final ArrayList<ParticleData> passLaList = new ArrayList<>();
		final ArrayList<ParticleData> passBothList = new ArrayList<>();
		@SuppressWarnings("unchecked")
		final ArrayList<ParticleData>[] passBothByPvLists =
				new ArrayList[nNonNullPttrnVbls];
		for (int idx = 0; idx < nNonNullPttrnVbls; ++idx) {
			passBothByPvLists[idx] = new ArrayList<>();
		}
		for (int k0 = 0; k0 < nPrtcls; ++k0) {
			final ParticleIndexes prtclIndxs = prtclIndxsS[k0];
			final int ot =
					particlesManager.getObjectType(lastRefSecs, prtclIndxs);
			final long landingRefSecs =
					particlesFile.getLandingRefSecs(prtclIndxs);
			final boolean adrift =
					landingRefSecs < 0 || landingRefSecs > minCstRefSecs;
			final boolean adrift2 =
					landingRefSecs < 0 || landingRefSecs > maxCstRefSecs;
			final boolean passLa;
			if (adrift && plannerModel.includeAdrift()) {
				passLa = true;
			} else if (!adrift && plannerModel.includeLanded()) {
				passLa = true;
			} else {
				passLa = false;
			}
			boolean slctdByAtLeastOne = false;
			final double[] pFails = new double[nNonNullPttrnVbls];
			final boolean[] slctds = new boolean[nNonNullPttrnVbls];
			for (int k1 = 0; k1 < nNonNullPttrnVbls; ++k1) {
				final PvValue pvValue = nonNullPvValues[k1];
				final PatternVariable pv = pvValue.getPv();
				final DetectValues detectValues = detectValuesArrays[k1][k0];
				final double pFail = detectValues.getPFail(evalType._pFailType);
				pFails[k1] = pFail;
				slctds[k1] = pv.getViz2LrcSets().containsKey(ot);
				if (slctds[k1]) {
					slctdByAtLeastOne = true;
				}
			}

			final double initWt = particlesFile.getInitPrior(prtclIndxs);
			final double pfAlpha =
					particlesFile.getCumPFail(lastRefSecs, prtclIndxs);
			final double bravoPrior = initWt * pfAlpha / normalizingConstant;
			final ParticleData particleData =
					new ParticleData(prtclIndxs, ot, initWt, pfAlpha, bravoPrior,
							adrift, passLa, pFails, slctds, adrift2);
			allParticleDatas.add(particleData);
			if (passLa) {
				passLaList.add(particleData);
				if (slctdByAtLeastOne) {
					passBothList.add(particleData);
					for (int pvIdx = 0; pvIdx < nNonNullPttrnVbls; ++pvIdx) {
						if (slctds[pvIdx]) {
							passBothByPvLists[pvIdx].add(particleData);
						}
					}
				}
			}
		}
		final int nParticleDatas = allParticleDatas.size();
		final int nPassLa = passLaList.size();
		final int nPassBoth = passBothList.size();
		final String format =
				"Got ParticleDatas.  nAll[%d] nPassLa[%d] nPassBoth[%d].";
		SimCaseManager.out(simCase,
				String.format(format, nParticleDatas, nPassLa, nPassBoth));
		_allParticleDatas =
				allParticleDatas.toArray(new ParticleData[nParticleDatas]);
		_passLa = passLaList.toArray(new ParticleData[nPassLa]);
		_passBoth = passBothList.toArray(new ParticleData[nPassBoth]);
		_passBothByPv = new ParticleData[nNonNullPttrnVbls][];
		String nPassBothByPvString = "NPassBothBySru: ";
		for (int k = 0; k < nNonNullPttrnVbls; ++k) {
			final ArrayList<ParticleData> list = passBothByPvLists[k];
			final int nInList = list.size();
			_passBothByPv[k] = list.toArray(new ParticleData[nInList]);
			nPassBothByPvString +=
					String.format(" [%s:%d]", _pvStrings[k], nInList);
		}
		SimCaseManager.out(simCase, String.format(nPassBothByPvString));
		Arrays.sort(_allParticleDatas, new Comparator<ParticleData>() {

			@Override
			public int compare(final ParticleData o1, final ParticleData o2) {
				final ParticleIndexes prtclIndxs1 = o1._prtclIndxs;
				final ParticleIndexes prtclIndxs2 = o2._prtclIndxs;
				return prtclIndxs1.compareTo(prtclIndxs2);
			}
		});
		SimCaseManager.out(simCase, "Leaving PlannerReportsData ctor.");
	}
}
