package com.skagit.sarops.util.patternUtils;

import com.skagit.util.greatCircleArc.GreatCircleArc;

public class LegInfo {
	public static enum LegType {
		CREEP_IS_TO_RIGHT, CROSS_LEG, CREEP_IS_TO_LEFT, GAP_FILLER, GENERIC;

		public boolean isSearchLeg() {
			return this == CREEP_IS_TO_LEFT || this == CREEP_IS_TO_RIGHT;
		}
	}

	final public GreatCircleArc _gca;
	public LegType _legType;

	public LegInfo(final GreatCircleArc gca, final LegType legType) {
		_gca = gca;
		_legType = legType;
	}
}