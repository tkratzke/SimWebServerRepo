package com.skagit.util;

import java.io.File;

import com.skagit.sarops.simCaseManager.SimCaseManager;

public class SaropsDirsTracker extends DirsTracker {

	final private static String _DataDirName = "data";
	final private static File _DataDir;

	static {
		final File runDir = getRunDir();
		if (runDir != null && runDir.exists() && runDir.isDirectory()) {
			_DataDir = new File(runDir, _DataDirName);
		} else {
			_DataDir = null;
		}
	}

	public static void dumpSaropsDirs() {
		String s = "";
		s += "\nCurrent Working Dir: " +
				StringUtilities.getCanonicalPath(getUserDir());
		s += "\nRunDir: " + StringUtilities.getCanonicalPath(getRunDir());
		s += "\nDataDir: " + StringUtilities.getCanonicalPath(getDataDir());
		s += "\nLogDir: " + StringUtilities.getCanonicalPath(getLogDir());
		SimCaseManager.out(/* simCase= */null, s);
	}

	public static File getDataDir() {
		return _DataDir;
	}

}
