package com.skagit.buildSimLand.minorAdj;

import com.skagit.util.geometry.gcaSequence.Loop3;

class AdjSummary {
	Loop3 _origLoop;
	Loop3 _resultLoop;
	private MinorAdj _causingMinorAdj;
	private boolean _changed;
	private boolean _deleted;
	private boolean _new;

	AdjSummary(final Loop3 origLoop) {
		_origLoop = origLoop;
		_resultLoop = null;
		_changed = _deleted = _new = false;
		_causingMinorAdj = null;
	}

	Loop3 getNominalLoop() {
		return _resultLoop != null ? _resultLoop : _origLoop;
	}

	public void reset() {
		if (!_changed || _deleted) {
			return;
		}
		_origLoop = _resultLoop;
		_resultLoop = null;
		_changed = _deleted = _new = false;
		_causingMinorAdj = null;
	}

	public String getString() {
		final String s = String.format(
				"\tOld Loop: %s\n\tNew Loop: %s\n\tchanged[%b] deleted[%b] new[%b]", //
				_origLoop.getSmallString(),
				_resultLoop == null ? "No New Loop" : _resultLoop.getSmallString(),
				_changed, _deleted, _new);
		return s;
	}

	public void setChanged(final MinorAdj causingMinorAdj) {
		_causingMinorAdj = causingMinorAdj;
		_changed = true;
	}

	public void setDeleted(final MinorAdj causingMinorAdj) {
		_causingMinorAdj = causingMinorAdj;
		_changed = _deleted = true;
	}

	public void setNew(final MinorAdj causingMinorAdj) {
		_causingMinorAdj = causingMinorAdj;
		_changed = _new = true;
	}

	public boolean getChanged() {
		return _changed;
	}

	public boolean getDeleted() {
		return _deleted;
	}

	public boolean getNew() {
		return _new;
	}

	public MinorAdj getCausingMinorAdj() {
		return _causingMinorAdj;
	}

	@Override
	public String toString() {
		return getString();
	}

	public void freeMemory() {
		if (_origLoop != null) {
			_origLoop.freeMemory();
		}
		if (_resultLoop != null) {
			_resultLoop.freeMemory();
		}
	}

}