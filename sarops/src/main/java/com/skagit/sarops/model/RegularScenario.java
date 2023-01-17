package com.skagit.sarops.model;

import com.skagit.sarops.simCaseManager.SimCaseManager.SimCase;

public class RegularScenario extends Scenario {

	protected RegularScenario(final SimCase simCase, final int id,
			final String name, final String type, final double scenarioWeight,
			final int iScenario, final int baseParticleIndex,
			final int nParticles) {
		super(simCase, id, name, type, scenarioWeight, iScenario,
				baseParticleIndex, nParticles);
	}

	@Override
	public boolean specificCheckAndFinalize(final Model model) {
		return true;
	}

}
