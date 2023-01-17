package com.skagit.sarops.compareKeyFiles;

import java.io.File;

import com.skagit.sarops.compareKeyFiles.comparePlanFiles.ComparePlanFiles;
import com.skagit.sarops.compareKeyFiles.compareSimFiles.CompareSimFiles;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.DirsTracker;
import com.skagit.util.StaticUtilities;
import com.skagit.util.StringUtilities;
import com.skagit.util.myLogger.MyLogger;

public class CompareKeyFiles {
	public final static String _AllCasesName = "ALL_CASES";
	public final static String _BadDataRowsName = "BAD_DATA_ROWS";
	public final static String _OrigDirName = "ORIG";
	public final static boolean _NoSimCompare =
			StringUtilities.getSystemProperty("No.Sim.Compare",
					/* useSpaceProxy= */false) != null;
	public final static boolean _NoPlanCompare =
			StringUtilities.getSystemProperty("No.Plan.Compare",
					/* useSpaceProxy= */false) != null;

	public static void main(final String[] args) {
		final int nArgs = args.length;
		int iArg = 0;
		final File userDir = DirsTracker.getUserDir();
		final File umbrellaDir;
		if (nArgs > 0) {
			umbrellaDir = new File(args[iArg++]);
		} else {
			umbrellaDir = userDir;
		}
		final File logDir = new File(umbrellaDir, "LogFiles");
		StaticUtilities.makeDirectory(logDir);
		StaticUtilities.clearDirectory(logDir);
		final MyLogger logger = MyLogger.CreateMyLogger(//
				/* relativeLoggerName= */"GetCmprData", logDir, //
				/* dbgCoreName= */null, //
				/* outCoreName= */"CmprOut", //
				/* wrnCoreName= */"CmprWrn", //
				/* errCoreName= */"CmprErr", //
				"txt", /* append= */false);
		final SimCaseManager simCaseManager = new SimCaseManager();
		final SimCaseManager.SimCase simCase = simCaseManager
				.buildCompareKeyFilesSimCase(logger, /* engnName= */"GetCmprData");

		for (int simOrPlan = 0; simOrPlan < 2; ++simOrPlan) {
			final boolean comparingSim = simOrPlan == 0;
			if (comparingSim && _NoSimCompare) {
				continue;
			}
			final boolean comparingPlan = simOrPlan == 1;
			if (comparingPlan && _NoPlanCompare) {
				continue;
			}
			logger.out(String.format("Gathering Data for comparing %s runs." //
					+ "\n\thomeDir: %s" //
					+ "\n\tuserDir %s" //
					+ "\n\tlogDir %s", //
					comparingSim ? "Sim" : "Plan", umbrellaDir.getAbsolutePath(),
					userDir.getAbsolutePath(), logDir.getAbsolutePath()));
			if (comparingSim) {
				CompareSimFiles.BuildSimCompareFiles(simCase, umbrellaDir);
			}
			if (comparingPlan) {
				ComparePlanFiles.BuildPlanCompareFiles(simCase, umbrellaDir);
			}
		}
	}
}
