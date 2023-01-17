package com.skagit.sarops.planner.solver.pqSolver.pvPlacer;

import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.util.navigation.LatLng3;

public class ParticlePlus implements Comparable<ParticlePlus> {
	final ParticleIndexes _prtclIndxs;
	final public LatLng3 _midLatLng;
	final public int _objectType;
	public double _weight;

	public ParticlePlus(final ParticleIndexes prtclIndxs,
			final int objectType, final LatLng3 midLatLng) {
		this(prtclIndxs, objectType, Double.NaN, midLatLng);
	}

	public ParticlePlus(final ParticleIndexes prtclIndxs,
			final int objectType, final double weight, final LatLng3 midLatLng) {
		_prtclIndxs = prtclIndxs;
		_objectType = objectType;
		_midLatLng = midLatLng;
		_weight = weight;
	}

	public String getString() {
		return String.format("%s ObjTp%d %f",
				_prtclIndxs.getString(/* includeScenario= */false), _objectType,
				_weight);
	}

	@Override
	public String toString() {
		return getString();
	}

	@Override
	final public int hashCode() {
		return _prtclIndxs.hashCode();
	}

	@Override
	public boolean equals(final Object o) {
		try {
			final ParticlePlus particlePlus = (ParticlePlus) o;
			return compareTo(particlePlus) == 0;
		} catch (final ClassCastException e) {
		}
		return false;
	}

	@Override
	public int compareTo(final ParticlePlus o) {
		return _prtclIndxs.compareTo(o._prtclIndxs);
	}
}
