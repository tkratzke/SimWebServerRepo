package com.skagit.sarops.compareKeyFiles.comparePlanFiles;

import java.io.File;
import java.util.TreeMap;

import com.skagit.sarops.compareKeyFiles.CompareUtils;

public class PlanDataRow {
	final public File _caseDir;
	final public String _fieldName;
	final public String _pvId;
	final public int _grandOrd;
	final public String _sotName;
	final public int _sotInt;
	final public String _treatmentString;
	final public String _trtmntString;
	final public double _newValue;
	final public double _origValue;
	final public double _increase;
	final public boolean _violation;

	public PlanDataRow(final File caseDir, final String fieldName,
			final String pvId, final int grandOrd, final String sotName,
			final int sotInt, final double newValue, final double origValue,
			final boolean checkEval, final boolean checkOptn) {
		_caseDir = caseDir;
		_fieldName = fieldName;
		_pvId = pvId;
		_grandOrd = grandOrd;
		_sotName = sotName;
		_sotInt = sotInt;
		_newValue = newValue;
		_origValue = origValue;
		_increase = _newValue - _origValue;
		final String[] bigAndLittleStrings = CompareUtils
				.getBigAndLittleStrings(_pvId, _grandOrd, _sotName, _sotInt);
		_treatmentString =
				String.format("[%s/%s]", bigAndLittleStrings[0], _fieldName);
		_trtmntString =
				String.format("[%s/%s]", bigAndLittleStrings[1], _fieldName);
		boolean violation = false;
		if (checkEval) {
			violation = Math.abs(_increase) > 1.0e-6;
		}
		if (checkOptn) {
			violation = _newValue < 0.999 * _origValue;
		}
		_violation = violation;
	}

	public String createTreatmentName(
			final TreeMap<File, String> caseDirToShortName) {
		final String shortName = caseDirToShortName.get(_caseDir);
		final String s = String.format("%s|%s", shortName, _treatmentString);
		return s;
	}

	public String createTrtmntName(
			final TreeMap<File, String> caseDirToShortName) {
		final String shortName = caseDirToShortName.get(_caseDir);
		final String s = String.format("%s|%s", shortName, _trtmntString);
		return s;
	}
}
