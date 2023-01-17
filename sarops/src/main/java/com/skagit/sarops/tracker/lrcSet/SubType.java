package com.skagit.sarops.tracker.lrcSet;

public enum SubType {
	LOGIT("Logit"), //
	MBETA("MBeta"), //
	INVERSE_CUBE("InverseCube"); //
	final public String _xmlString;

	static final public String _SubTypeAttributeName = "SubType";

	SubType(final String xmlString) {
		_xmlString = xmlString;
	}

	public static SubType stringToSubType(final String subTypeString) {
		final SubType[] subTypes = values();
		for (final SubType subType : subTypes) {
			if (subType._xmlString.equals(subTypeString)) {
				return subType;
			}
		}
		return null;
	}

}