package com.skagit.buildSimLand.minorAdj;

import com.skagit.util.colorUtils.ColorUtils;

public enum AdjType {
	DELETE_INNERMOST(ColorUtils.ColorGrouping.BLUE), //
	DELETE_POLYGON(ColorUtils.ColorGrouping.BLUE), //
	DELETE_THRESHOLD(ColorUtils.ColorGrouping.BLUE), //
	SUBSTITUTE_PATH(ColorUtils.ColorGrouping.GREEN), //
	ADD_CONNECTOR(ColorUtils.ColorGrouping.BROWN), //
	ADD_POLYGON(ColorUtils.ColorGrouping.ORANGE); //
	final public ColorUtils.ColorGrouping _colorGrouping;

	private AdjType(final ColorUtils.ColorGrouping colorGrouping) {
		_colorGrouping = colorGrouping;
	}

	private static final AdjType[] _AdjTypes = values();

	public static AdjType getAdjType(final String nameString) {
		if (nameString == null) {
			return null;
		}
		for (final AdjType adjType : _AdjTypes) {
			if (nameString.equalsIgnoreCase(adjType.name())) {
				return adjType;
			}
		}
		return null;
	}
}