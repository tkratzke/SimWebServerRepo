package com.skagit.sarops.model;

import com.skagit.sarops.simCaseManager.SimCaseManager.SimCase;

public class DebrisSighting extends RegularScenario {

	public DebrisSighting(final SimCase simCase, final short id, final String name, final int iDebrisSighting) {
		super(simCase, id, name, Scenario._DebrisSightingType, /* scenarioWeight= */ 0d, iDebrisSighting,
				/* baseParticleIndex= */0, /* nParticles= */0);
	}

	@Override
	public boolean isDebrisSighting() {
		return true;
	}
}
