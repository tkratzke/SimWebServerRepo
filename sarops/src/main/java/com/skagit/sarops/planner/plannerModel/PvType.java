package com.skagit.sarops.planner.plannerModel;

public enum PvType {
	UNKNOWN("unknown"), SMALL_BOAT("boat"), VESSEL("vessel"), HELO("helo"),
	FIXED_WING("fixedwing");
	final public String _xmlString;
	final private String _idenifyingString;

	private PvType(final String xmlString) {
		_xmlString = xmlString;
		_idenifyingString = _xmlString.toLowerCase().substring(0, 4);
	}

	final public static PvType[] _AllPvTypes = PvType.values();

	public boolean isSurfaceShip() {
		return this == SMALL_BOAT || this == VESSEL;
	}

	public boolean isAircraft() {
		return this == HELO || this == FIXED_WING;
	}

	public static PvType getPvType(final String xmlString) {
		final String lc = xmlString.toLowerCase();
		for (final PvType pvType : _AllPvTypes) {
			if (lc.contains(pvType._idenifyingString)) {
				return pvType;
			}
		}
		if (lc.contains("fwac")) {
			return PvType.FIXED_WING;
		}
		return null;
	}
}