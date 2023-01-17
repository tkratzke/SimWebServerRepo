package com.skagit.sarops.planner;

import com.skagit.sarops.tracker.ParticleIndexes;

public class ParticlesAndPriors {
	final public ParticleIndexes[] _particleIndexesS;
	final public double[] _priors;

	public ParticlesAndPriors(final ParticleIndexes[] particleIndexesS,
			final double[] priors) {
		_particleIndexesS = particleIndexesS;
		_priors = priors;
	}
}