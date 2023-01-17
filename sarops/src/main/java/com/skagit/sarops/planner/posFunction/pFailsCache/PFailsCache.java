package com.skagit.sarops.planner.posFunction.pFailsCache;

import com.skagit.sarops.computePFail.DetectValues;
import com.skagit.sarops.planner.ParticlesManager;
import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.util.Cache;

public class PFailsCache {
	public static int _NRealBuildsCalled = 0;
	public static int _NSuccessfulLookups = 0;
	final private ParticlesManager _particlesManager;
	final private Cache<PvValueBox> _cache;

	public PFailsCache(final ParticlesManager particlesManager,
			final SimCaseManager.SimCase simCase, final int cacheSize) {
		_particlesManager = particlesManager;
		_cache = new Cache<>(PvValueBox._PvValueBoxComparator, cacheSize);
	}

	public DetectValues[] getDetectValuesArray(final Planner planner,
			final boolean forOptnOnly, final DetectValues.PFailType pFailType,
			final ParticleIndexes[] prtclIndxsS, final int currentN,
			final int newN, final PvValue pvValue) {
		final SimCaseManager.SimCase simCase = planner.getSimCase();
		/**
		 * Don't create the sortie while we're just looking up the PvValueBox.
		 */
		final PvValueBox forLookUp =
				new PvValueBox(planner, pvValue, /* forLookUpOnly= */true);
		PvValueBox pvValueBox = null;
		synchronized (_cache) {
			pvValueBox = _cache.get(forLookUp);
			if (pvValueBox == null) {
				pvValueBox =
						new PvValueBox(planner, pvValue, /* forLookUpOnly= */false);
				_cache.put(pvValueBox);
				if (++_NRealBuildsCalled % 100 == 0) {
					SimCaseManager.out(simCase, "Built " + _NRealBuildsCalled + ".");
				}
			} else {
				if (++_NSuccessfulLookups % 10000 == 0) {
					SimCaseManager.out(simCase,
							"Looked up " + _NSuccessfulLookups + ".");
				}
			}
		}
		synchronized (pvValueBox) {
			final DetectValues[] returnValue =
					pvValueBox.getDetectValuesArray(_particlesManager, forOptnOnly,
							pFailType, prtclIndxsS, currentN, newN);
			return returnValue;
		}
	}

	public void clear() {
		synchronized (_cache) {
			_cache.clear();
		}
	}
}
