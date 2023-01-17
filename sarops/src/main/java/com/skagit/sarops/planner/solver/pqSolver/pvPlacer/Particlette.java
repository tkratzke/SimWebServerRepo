package com.skagit.sarops.planner.solver.pqSolver.pvPlacer;

import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.sarops.tracker.ParticleIndexes.ParticleIndexesState;

public class Particlette implements Comparable<Particlette> {
	final public ParticleIndexes.ParticleIndexesState _stateVector;
	public double _nonSweepWidthWeight;
	final public double _sweepWidth;
	public int _count;

	Particlette(final ParticleIndexesState stateVector, final double nonSweepWidthWeight,
			final double sweepWidth) {
		_stateVector = stateVector;
		_nonSweepWidthWeight = nonSweepWidthWeight;
		_count = 0;
		_sweepWidth = sweepWidth;
	}

	@Override
	final public int hashCode() {
		return _stateVector.getPrtclIndxs().hashCode();
	}

	@Override
	public boolean equals(final Object o) {
		try {
			final Particlette p = (Particlette) o;
			return compareTo(p) == 0;
		} catch (final ClassCastException e) {
			return false;
		}
	}

	@Override
	public int compareTo(final Particlette o) {
		final ParticleIndexes.ParticleIndexesState stateVector0 = _stateVector;
		final ParticleIndexes.ParticleIndexesState stateVector1 = o._stateVector;
		return stateVector0.compareTo(stateVector1);
	}

	public static void normalizeNonSweepWidths(
			final Particlette[] particlettes) {
		final int n = particlettes.length;
		double totalWt = 0d;
		for (int k = 0; k < n; ++k) {
			final Particlette p = particlettes[k];
			final double wt = p._nonSweepWidthWeight * p._count;
			totalWt += wt;
		}
		final double fillValue = 1d / n;
		for (int k = 0; k < n; ++k) {
			final Particlette p = particlettes[k];
			if (totalWt == 0d) {
				p._nonSweepWidthWeight = fillValue;
			} else {
				final double wt = p._nonSweepWidthWeight * p._count;
				p._nonSweepWidthWeight = wt / totalWt;
			}
			p._count = 1;
		}
	}

}