package com.skagit.sarops.util;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.skagit.util.GlobalStrings;
import com.skagit.util.StringUtilities;

public class SimGlobalStrings {
	final private static SimGlobalStrings _SimGlobalStrings;
	final private String _versionName;
	final private String _productName;
	final private TreeMap<String, String> _keyValue;

	static {
		_SimGlobalStrings = new SimGlobalStrings();
	}

	public SimGlobalStrings() {
		@SuppressWarnings("rawtypes")
		final Class clazz = getClass();
		final GlobalStrings globalStrings = new GlobalStrings(clazz.getName());
		_versionName = globalStrings.getString("Version.Name");
		final int firstDash = _versionName.indexOf('-');
		_productName = _versionName.substring(0, firstDash);
		/**
		 * Any key is overridden by one that is there but has the product name in it.
		 */
		final String overrideSuffix = String.format("-%s", _productName);
		final Set<String> origKeys = globalStrings.getAllKeys();
		final int overrideSuffixLength = overrideSuffix == null ? 0 : overrideSuffix.length();
		_keyValue = new TreeMap<>();
		for (final String key : origKeys) {
			final String value = globalStrings.getString(key);
			final String overridingKey = String.format("%s-%s", key, _productName);
			if (origKeys.contains(overridingKey)) {
				continue;
			}
			if (key.endsWith(overrideSuffix)) {
				/** Strip the suffix. */
				final String newKey = key.substring(0, key.length() - overrideSuffixLength);
				_keyValue.put(newKey, value);
				continue;
			}
			if (key.indexOf('-') >= 0) {
				/** It has a dash. We don't want it. */
				continue;
			}
			_keyValue.put(key, value);
		}
	}

	public String getString() {
		String s = "";
		for (final Map.Entry<String, String> entry : _keyValue.entrySet()) {
			final String key = entry.getKey();
			final String value = entry.getValue();
			s += String.format("\n%s→%s", key, value);
		}
		return s;
	}

	/** Generic workhorses. */
	boolean getBoolean(final String name, final boolean defaultValue) {
		final String s = getString(name);
		final Boolean b = StringUtilities.getBoolean(s);
		return b == null ? defaultValue : b;
	}

	int getInt(final String name, final int defaultValue) {
		final String s = getString(name);
		if (s == null) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(s);
		} catch (final NumberFormatException e) {
		}
		return defaultValue;
	}

	double getDouble(final String name, final double defaultValue) {
		final String s = getString(name);
		if (s == null) {
			return defaultValue;
		}
		try {
			return Double.parseDouble(s);
		} catch (final NumberFormatException e1) {
		}
		return defaultValue;
	}

	@SuppressWarnings("unused")
	private String getString(final String name, final String defaultValue) {
		final String s = getString(name);
		return s == null ? defaultValue : s;
	}

	public boolean getLogToErrFiles() {
		return getBoolean("Log.To.Err.Files", true);
	}

	public boolean storeMeans() {
		return getBoolean("Store.Means", false);
	}

	public int getDebugLevel() {
		return getInt("Debug.Level", 0);
	}

	public int getNForFirstStratum() {
		return getInt("N.For.First.Stratum", 1000);
	}

	public int getNGridDivisionsForTheoretical() {
		return getInt("N.Grid.Divisions.Theoretical", 25);
	}

	public int getNProgressSteps() {
		return getInt("N.Progress.Steps", 20);
	}

	public int getMinNPerSliceInTracker() {
		return getInt("Min.N.Per.Slice.In.Tracker", 250);
	}

	public int getMinSampleSize() {
		return getInt("Min.Sample.Size", 250);
	}

	public int getMinNStrataForStratified() {
		return getInt("Min.N.Strata.For.Stratified", 4);
	}

	public double getGshhsBufferD() {
		return getDouble("Gshhs.Buffer.LatDegs", 0.5);
	}

	public double getMaxSigma() {
		return getDouble("Max.Sigma", 0.005);
	}

	public double getMaxSigOverNu1() {
		return getDouble("Max.Sig.Over.Nu1", 0.75);
	}

	public double getMaxSigOverNu2() {
		return getDouble("Max.Sig.Over.Nu2", 0.25);
	}

	public double getMinProbDetect() {
		return getDouble("Min.Prob.Detect", 0.0001);
	}

	public double getMaxProbDetect() {
		return getDouble("Max.Prob.Detect", 1d - getMinProbDetect());
	}

	public double getNominalContainmentValue() {
		return getDouble("Nominal.Containment.Value", 0.9);
	}

	public static String getStaticVersionName() {
		return _SimGlobalStrings._versionName;
	}

	public static SimGlobalStrings getStaticSimGlobalStrings() {
		return _SimGlobalStrings;
	}

	private String getString(final String core) {
		final String s2 = _keyValue.get(core);
		return s2;
	}

	public int getTooManyLoopsForComplicated() {
		return getInt("Too.Many.Loops.For.Complicated", 50);
	}

	public int getDeconflicterMaxNAnglesToTry() {
		return getInt("Deconficter.MaxNAnglesToTry", 4);
	}

	public double getDeconflicterMinShare() {
		return getDouble("Deconficter.MinShare", 0.05);
	}

	public static boolean getUseUserDirForCppToJavaTrace() {
		final String debugFlag = StringUtilities.getSystemProperty("DEBUG_JAVA", /* useSpaceProxy= */false);
		final boolean debugging = StringUtilities.stringToBoolean(debugFlag);
		final boolean b;
		if (debugging) {
			b = _SimGlobalStrings.getBoolean("Use.User.Dir.For.CppToJava.Trace.Debug", false);
		} else {
			b = _SimGlobalStrings.getBoolean("Use.User.Dir.For.CppToJava.Trace", false);
		}
		return b;
	}

	public static String getCppToJavaTraceDirName() {
		final String s = _SimGlobalStrings.getString("CppToJava.Trace.Dir.Name");
		return s;
	}

	public static String getFullStaticVersionName() {
		final String saropsVersionName = getStaticVersionName();
		final String javaVersionName = StringUtilities.getSystemProperty("java.version", /* useSpaceProxy= */false);
		final String s = String.format("SaropsVersion(%s), JavaVersion(%s).", saropsVersionName, javaVersionName);
		return s;
	}
}
