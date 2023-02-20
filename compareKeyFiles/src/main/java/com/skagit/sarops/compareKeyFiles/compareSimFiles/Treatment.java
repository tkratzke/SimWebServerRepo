package com.skagit.sarops.compareKeyFiles.compareSimFiles;

import java.io.File;

import com.skagit.sarops.compareKeyFiles.CompareUtils;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.SearchObjectType;
import com.skagit.util.TimeUtilities;

public class Treatment implements Comparable<Treatment> {
	final File _caseDir;
	final String _shortName;
	final int _refSecsIdx;
	final int _iScenario;
	final int _sotId;
	final private String _scenAndSotString;
	final private String _dtgString;
	final private String _treatmentName0;
	final private String _treatmentName1;
	final private int _maxNParticles;

	Treatment(final File caseDir, final String shortName, final Model model, final int iScenario, final int sotId,
			final int refSecsIdx, final int nRefSecsS, final long refSecs) {
		_maxNParticles = model == null ? Integer.MAX_VALUE : model.getMaxNParticles(iScenario, sotId);
		_caseDir = caseDir;
		_shortName = shortName;
		_iScenario = iScenario;
		_sotId = sotId;
		_refSecsIdx = refSecsIdx;
		final SearchObjectType sot;
		if (_sotId != Model._WildCard) {
			sot = model.getSotFromId(_sotId);
		} else {
			sot = null;
		}

		final String scName = _iScenario != Model._WildCard ? model.getScenario(_iScenario).getName() : null;
		final String sotName = _sotId != Model._WildCard ? sot.getName() : null;
		final String[] bigAndLittleStrings = CompareUtils.getBigAndLittleStrings(scName, _iScenario, sotName, _sotId);
		_scenAndSotString = bigAndLittleStrings[0];
		final String littleString = bigAndLittleStrings[1];

		/** We have no wild cards for the time. */
		final String tmString1 = String.format("%02d", _refSecsIdx);
		final String tmString = TimeUtilities.formatTime(refSecs, /* includeSecs= */false);
		_dtgString = String.format("%s(%s)", tmString1, tmString);
		_treatmentName0 = String.format("%s|[%s/%s]", _shortName, _scenAndSotString, tmString);
		_treatmentName1 = String.format("%s|[%s/%s]", _shortName, littleString, tmString1);
	}

	public int getMaxNParticles() {
		return _maxNParticles;
	}

	@Override
	public int compareTo(final Treatment treatment) {
		final int compareValue = _caseDir.compareTo(treatment._caseDir);
		if (compareValue != 0) {
			return compareValue;
		}
		if (_iScenario != treatment._iScenario) {
			return _iScenario < treatment._iScenario ? -1 : 1;
		}
		if (_sotId != treatment._sotId) {
			return _sotId < treatment._sotId ? -1 : 1;
		}
		if (_refSecsIdx != treatment._refSecsIdx) {
			return _refSecsIdx < treatment._refSecsIdx ? -1 : 1;
		}
		return 0;
	}

	public String getTreatmentName0() {
		return _treatmentName0;
	}

	public String getTreatmentName1() {
		return _treatmentName1;
	}

	@Override
	public String toString() {
		return getTreatmentName0();
	}

	public String getScenAndSotString() {
		return _scenAndSotString;
	}

	public String getDtgString() {
		return _dtgString;
	}

	public boolean equalExceptForTimeStep(final Treatment treatment) {
		return _iScenario == treatment._iScenario && _sotId == treatment._sotId;
	}
}