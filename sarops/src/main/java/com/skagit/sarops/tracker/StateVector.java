package com.skagit.sarops.tracker;

import com.skagit.sarops.model.Scenario;
import com.skagit.sarops.model.SearchObjectType;
import com.skagit.util.TimeUtilities;
import com.skagit.util.navigation.LatLng3;

public abstract class StateVector {
	final private long _simSecs;
	protected LatLng3 _latLng;
	private StateVectorType _svt;
	private StateVector _predecessor;
	private StateVector _successor = null;
	final protected Particle _particle;
	private boolean _stuckOnLand;
	private boolean _anchored;

	public final String getString() {
		final StringBuffer b = new StringBuffer();
		final String timeString = TimeUtilities.formatTime(_simSecs, true);
		b.append(' ').append(getParticleIndexes().getString());
		b.append(' ').append(timeString);
		b.append('(').append(getSearchObjectType().getName());
		b.append(") ").append(_latLng.getString(4));
		b.append(' ').append(_svt.name());
		b.append('(').append(isDistress() ? "Adrift" : "Underway").append(')');
		return new String(b);
	}

	@Override
	public final String toString() {
		return getString();
	}

	public abstract boolean isDistress();

	protected StateVector(final Particle particle, final long simSecs, final LatLng3 latLng,
			final StateVectorType stateVectorType, final boolean updateParticleTail) {
		_simSecs = simSecs;
		_latLng = latLng;
		_svt = stateVectorType;
		_particle = particle;
		/** Add to the master list of timeSteps of the particle. */
		if (updateParticleTail) {
			_predecessor = null;
			particle.setRootStateVector(this);
			particle.setLatestStateVector(this);
		}
		_stuckOnLand = false;
		_anchored = false;
	}

	public boolean isStuckOnLand() {
		return _stuckOnLand;
	}

	public boolean isAnchored() {
		return _anchored;
	}

	public void setIsStuckOnLand() {
		_stuckOnLand = true;
	}

	public void setIsAnchored() {
		_anchored = true;
	}

	protected StateVector(final StateVector predecessor, final StateVectorType stateVectorType, final long simSecs,
			final boolean updateParticleTail) {
		_particle = predecessor._particle;
		_simSecs = simSecs;
		_latLng = predecessor._latLng;
		_svt = stateVectorType;
		if (updateParticleTail) {
			_predecessor = predecessor;
			_predecessor._successor = this;
			_particle.setLatestStateVector(this);
		}
	}

	public Particle.LeewayCalculator getLeewayCalculator() {
		return _particle.getLeewayCalculator();
	}

	/** add the successors that are at or after time to stateVectors */
	public void cleanUp(final long simSecs) {
		if (_simSecs <= simSecs) {
			_particle.setRootStateVector(this);
			_predecessor = null;
			if (_successor != null) {
				_successor.cleanUp(simSecs);
			}
		}
	}

	final public long getSimSecs() {
		return _simSecs;
	}

	final public LatLng3 getLatLng() {
		return _latLng;
	}

	final public StateVectorType getSvt() {
		return _svt;
	}

	public StateVector getAncestor(final long simSecs) {
		if (simSecs >= _simSecs || _predecessor == null) {
			return this;
		}
		return _predecessor.getAncestor(simSecs);
	}

	public StateVector getPredecessor() {
		return _predecessor;
	}

	public SearchObjectType getSearchObjectType() {
		return _particle.getSearchObjectTypeFromSimSecs(_simSecs);
	}

	public ParticleIndexes getParticleIndexes() {
		return _particle.getParticleIndexes();
	}

	public void setLatLng(final LatLng3 latLng) {
		_latLng = latLng;
	}

	public void setStateVectorType(final StateVectorType stateVectorType) {
		_svt = stateVectorType;
	}

	abstract public StateVector timeUpdate(Tracker tracker, Scenario scenario, long[] simSecsS, int iT);

	abstract public String getDescription();

	public StateVector getSuccessor() {
		return _successor;
	}

	public void freeMemory() {
		if (_successor != null) {
			_successor.freeMemory();
			_successor = null;
		}
	}

}
