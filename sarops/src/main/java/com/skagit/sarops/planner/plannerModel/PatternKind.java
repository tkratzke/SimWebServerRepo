package com.skagit.sarops.planner.plannerModel;

public enum PatternKind {
	PSCS("LP"), SS("SS"), VS("vs");
	final private String _outsideName;

	PatternKind(final String outsideName) {
		_outsideName = outsideName;
	}

	public boolean isPsCs() {
		return this == PSCS;
	}

	public boolean isSs() {
		return this == SS;
	}

	public boolean isVs() {
		return this == VS;
	}

	public String outsideName() {
		return _outsideName;
	}

	public static final PatternKind[] _AllPatternKinds = PatternKind.values();

	public static PatternKind getPatternKind(final String outsideName) {
		if (outsideName == null) {
			return null;
		}
		for (final PatternKind patternKind : _AllPatternKinds) {
			if (patternKind.outsideName().equalsIgnoreCase(outsideName) ||
					patternKind.name().equalsIgnoreCase(outsideName)) {
				return patternKind;
			}
		}
		return null;
	}
}
