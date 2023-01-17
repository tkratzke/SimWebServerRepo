package com.skagit.sarops.tracker;

import com.skagit.util.colorUtils.ColorUtils;

public enum StateVectorType {
	/** Port (Reds) */
	PORT_DIRECT(ColorUtils._Red, "PortDrct", true, false, true), //
	PORT_TACK(ColorUtils._Crimson, "PortTack", true, false, true), //
	PORT_GIBE(ColorUtils._FireBrick, "PortGibe", true, false, true), //
	/** Starboard (Limes) */
	STRBRD_DIRECT(ColorUtils._Lime, "StrbrdDrct", false, true, true), //
	STRBRD_TACK(ColorUtils._SpringGreen, "StrbrdTack", false, true, true), //
	STRBRD_GIBE(ColorUtils._Chartreuse, "StrbrdGibe", false, true, true), //
	TACK_END(ColorUtils._LemonChiffon, "TackEnd", false, false, true), //
	/** Projections (Purples) */
	PRJCTN(ColorUtils._Magenta, "Prjctn", false, false, true), //
	CLOSE_ENOUGH_TO_ABANDON_OFFSET_ANGLE(ColorUtils._Violet, "AbndnOffAng",
			false, false, true), //
	/** BLOCKED and deflected mean sailing (not motoring or hove-to). */
	DEFLECTED(ColorUtils._MediumPurple, "Dflctd", false, false, true), //
	BLOCKED(ColorUtils._BlueViolet, "Blckd", false, false, false), //
	/** Overwhelmeds (Browns) */
	OVERWHELMED(ColorUtils._Goldenrod, "Overwhelmed", false, false, true), //
	OVERWHELMED_BLOCKED(ColorUtils._Chocolate, "OverwhelmedBlckd", false,
			false, true), //
	/** Motoring (Blues). */
	MOTOR(ColorUtils._DeepSkyBlue, "Motor", false, false, true), //
	MOTOR_PRJCTN(ColorUtils._Turquoise, "MotorPrjctn", false, false, true), //
	MOTOR_DEFLECTED(ColorUtils._CornflowerBlue, "MotorDflctd", false, false,
			true), //
	MOTOR_OVERWHELMED(ColorUtils._DodgerBlue, "MotorOverwhelmed", false,
			false, true), //
	MOTOR_BLOCKED(ColorUtils._Blue, "MotorBlckd", false, false, true), //
	MOTOR_OVERWHELMED_BLOCKED(ColorUtils._CadetBlue, "MotorOverwhelmedBlckd",
			false, false, true), //
	/** HoveTo (Grays) */
	HOVETO(ColorUtils._DarkGray, "HoveTo", false, false, true), //
	HOVETO_BLOCKED(ColorUtils._Silver, "HoveToBlocked", false, false, true), //
	/** Non-Sailing (Whites). */
	UNDERWAY(ColorUtils._White, "Undrwy", false, false, false), //
	/** This will never show up since the objectType color will take over. */
	DISTRESS(ColorUtils._MistyRose, "Dstrss", //
			false, false, false, /* doNotShowInKey= */true), //
	/** Success (Yellows). */
	NONFINAL_ARRIVE(ColorUtils._PaleGoldenrod, "NF_Arrive", false, false,
			false), //
	ARRIVE(ColorUtils._Yellow, "Arrive", false, false, false), //
	/** Misc (Black). */
	UNDEFINED(ColorUtils._Black, "Undfnd", false, false, false,
			/* doNotShowInKey= */true); //

	final public static String _StateVectorTypesString;
	final public static String _StateVectorColorsString;
	final public int[] _rgb;
	final public String _shortString;
	final public boolean _port;
	final public boolean _strbrd;
	final public boolean _sailing;
	final public boolean _doNotShowInKey;

	public boolean sailingPort() {
		return _port;
	}

	public boolean sailingStrbrd() {
		return _strbrd;
	}

	static {
		final StateVectorType[] values = values();
		final int n = values.length;
		String stateVectorTypesString = "";
		String stateVectorColorsString = "";
		for (int k = 0; k < n; ++k) {
			if (stateVectorTypesString.length() > 0) {
				stateVectorTypesString += " ";
				stateVectorColorsString += " ";
			}
			stateVectorTypesString += values[k].name();
			stateVectorColorsString += values[k].getColorName();
		}
		_StateVectorTypesString = stateVectorTypesString;
		_StateVectorColorsString = stateVectorColorsString;
	}

	private StateVectorType(final int[] rgb, final String shortString,
			final boolean port, final boolean strbrd, final boolean sailing) {
		this(rgb, shortString, port, strbrd, sailing,
				/* doNotShowInKey= */false);
	}

	private StateVectorType(final int[] rgb, final String shortString,
			final boolean port, final boolean strbrd, final boolean sailing,
			final boolean doNotShowInKey) {
		_rgb = rgb;
		_shortString = shortString;
		_port = port;
		_strbrd = strbrd;
		_sailing = sailing;
		_doNotShowInKey = doNotShowInKey;
	}

	public String getColorName() {
		return ColorUtils.rgbToName(_rgb);
	}

	public String getDescriptiveString() {
		return String.format("%s[%d]: %s", name(), ordinal(), getColorName());
	}
}
