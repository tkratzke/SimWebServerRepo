package com.skagit.sarops.tracker;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.planner.ParticlesManager;
import com.skagit.util.HashFunctions;
import com.skagit.util.navigation.LatLng3;

public class ParticleIndexes implements Comparable<ParticleIndexes> {
	final private int _nDigits;
	final private int _iScenario;
	final private int _iParticle;
	final private int _sotOrd;
	final private int _overallIndex;

	public static ParticleIndexes getStandardOne(final Model model,
			final int iScenario, final int iParticle) {
		return new ParticleIndexes(model, iScenario, iParticle, -1);
	}

	public static ParticleIndexes getStandardOne(final Model model,
			final String readableStringA) {

		final String readableString =
				readableStringA.trim().replaceAll("[()]+", "");
		final String[] fields = readableString.trim().split("[\\s,]+");
		final int nFields = fields.length;
		if (nFields < 1 || nFields > 2) {
			return null;
		}
		try {
			final int iScenario = nFields == 2 ? Integer.parseInt(fields[0]) : 0;
			final int iParticle = Integer.parseInt(fields[nFields == 2 ? 1 : 0]);
			return getStandardOne(model, iScenario, iParticle);
		} catch (final NumberFormatException e) {
			return null;
		}
	}

	public ParticleIndexes(final Model model, final int overallIndex,
			final int sotOrd) {
		_overallIndex = overallIndex;
		final int nParticlesPerScenario = model.getNParticlesPerScenario();
		_iScenario = _overallIndex / nParticlesPerScenario;
		_iParticle = _overallIndex % nParticlesPerScenario;
		_sotOrd = sotOrd;
		_nDigits =
				Math.max(1, (int) Math.ceil(Math.log10(nParticlesPerScenario - 1)));
	}

	public static ParticleIndexes getMeanOne(final Model model,
			final int iScenario, final int sotOrd) {
		return new ParticleIndexes(model, iScenario, -1, sotOrd);
	}

	private ParticleIndexes(final Model model, final int iScenario,
			final int iParticle, final int sotOrd) {
		_iScenario = iScenario;
		_iParticle = iParticle;
		_sotOrd = sotOrd;
		final int nScenarii = model.getNScenarii();
		final int nParticlesPerScenario = model.getNParticlesPerScenario();
		if (_iParticle >= 0) {
			_overallIndex = _iScenario * nParticlesPerScenario + _iParticle;
		} else {
			final int nSearchObjectTypes = model.getNSearchObjectTypes();
			final int base = nScenarii * nParticlesPerScenario;
			_overallIndex = base + _iScenario * nSearchObjectTypes + sotOrd;
		}
		_nDigits =
				Math.max(1, (int) Math.ceil(Math.log10(nParticlesPerScenario - 1)));
	}

	public String getString(final boolean includeScenario) {
		if (_iParticle >= 0) {
			if (includeScenario) {
				final String f = "(%d,%0" + _nDigits + "d):%d";
				return String.format(f, _iScenario, _iParticle, _overallIndex);
			}
			final String f = "%0" + _nDigits + "d";
			return String.format(f, _iParticle);
		}
		return String.format("Sc[%d]", _iScenario);
	}

	public String getReadableString(final boolean includeScenario) {
		if (includeScenario) {
			final String f = "(%d,%0" + _nDigits + "d)";
			return String.format(f, _iScenario, _iParticle);
		}
		final String f = "%0" + _nDigits + "d";
		return String.format(f, _iParticle);
	}

	public String getString() {
		return getString(/* includeScenario= */true);
	}

	@Override
	public String toString() {
		return getString();
	}

	public ParticleIndexesState refSecsToPrtclIndxsState(
			final ParticlesManager particlesManager, final long refSecs) {
		return particlesManager.computePrtclIndxsState(this, refSecs);
	}

	public int getScenarioIndex() {
		return _iScenario;
	}

	public int getParticleIndex() {
		return _iParticle;
	}

	public int getOverallIndex() {
		return _overallIndex;
	}

	public int getSotOrd() {
		return _sotOrd;
	}

	@Override
	public int hashCode() {
		return HashFunctions.computeHash(_iParticle, _iScenario);
	}

	@Override
	public boolean equals(final Object o) {
		if (o instanceof ParticleIndexes) {
			return _overallIndex == ((ParticleIndexes) o)._overallIndex;
		}
		return false;
	}

	@Override
	public int compareTo(final ParticleIndexes prtclIndxs) {
		return _overallIndex > prtclIndxs._overallIndex ? 1 :
				(_overallIndex < prtclIndxs._overallIndex ? -1 : 0);
	}

	public static int compare(final ParticleIndexes[] prtclIndxsS1,
			final ParticleIndexes[] prtclIndxsS2) {
		if (prtclIndxsS1 == prtclIndxsS2) {
			return 0;
		}
		if (prtclIndxsS1.length != prtclIndxsS2.length) {
			return prtclIndxsS1.length - prtclIndxsS2.length;
		}
		final int n = prtclIndxsS1.length;
		for (int i = 0; i < n; ++i) {
			final int compareValue = prtclIndxsS1[i].compareTo(prtclIndxsS2[i]);
			if (compareValue != 0) {
				return compareValue;
			}
		}
		return 0;
	}

	public boolean isEnvMean() {
		return _sotOrd >= 0;
	}

	public class ParticleIndexesState
			implements Comparable<ParticleIndexesState> {
		final private LatLng3 _latLng;
		final private long _refSecs;
		final private int _objectType;
		final private boolean _landed;

		public ParticleIndexesState(final LatLng3 latLng, final long refSecs,
				final int objectType, final boolean landed) {
			_latLng = latLng;
			_objectType = objectType;
			_refSecs = refSecs;
			_landed = landed;
		}

		public ParticleIndexes getPrtclIndxs() {
			return ParticleIndexes.this;
		}

		public long getRefSecs() {
			return _refSecs;
		}

		public LatLng3 getLatLng() {
			return _latLng;
		}

		public int getObjectType() {
			return _objectType;
		}

		public boolean isLanded() {
			return _landed;
		}

		@Override
		public int compareTo(final ParticleIndexesState stateVector) {
			final int compareValue =
					getPrtclIndxs().compareTo(stateVector.getPrtclIndxs());
			if (compareValue != 0) {
				return compareValue;
			}
			if (_refSecs < stateVector._refSecs) {
				return -1;
			}
			if (_refSecs > stateVector._refSecs) {
				return 1;
			}
			return 0;
		}
	}
}
