package com.skagit.sarops.compareKeyFiles.comparePlanFiles;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.TreeMap;

import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.ShortNameFinder;
import com.skagit.util.myLogger.MyLogger;

public class ComparePlanFiles {

	public static void BuildPlanCompareFiles(
			final SimCaseManager.SimCase simCase, final File dir) {
		final MyLogger logger = SimCaseManager.getLogger(simCase);

		/** Gather Cases to compare. */
		final PlanCaseDirData[] caseDirDatas =
				gatherPlanCaseDirs(logger, simCase, dir);
		final int nCaseDirDatas = caseDirDatas.length;
		final File[] caseDirs = new File[nCaseDirDatas];
		for (int k = 0; k < nCaseDirDatas; ++k) {
			caseDirs[k] = caseDirDatas[k].getCaseDir();
		}

		/** For creating a short name. */
		final TreeMap<File, String> caseDirToShortName =
				ShortNameFinder.computeCaseDirToShortName(dir, caseDirs);

		/** Log the summary. */
		for (int k = 0; k < nCaseDirDatas; ++k) {
			String summaryString = "";
			final PlanCaseDirData caseDirData = caseDirDatas[k];
			final File caseDir = caseDirData.getCaseDir();
			if (k > 0) {
				summaryString += "\n";
			}
			final String shortName = caseDirToShortName.get(caseDir);
			summaryString += String.format("Gathered PlanCaseDir %s(%s)",
					shortName, caseDir.getAbsolutePath());
			MyLogger.out(logger, summaryString);
		}

		PlanWriteExcelFile.planWriteExcelFile(dir, caseDirToShortName,
				caseDirDatas);
	}

	private static PlanCaseDirData[] gatherPlanCaseDirs(final MyLogger logger,
			final SimCaseManager.SimCase simCase, final File dir) {
		final ArrayList<PlanCaseDirData> caseDirDatas = new ArrayList<>();
		addToPlanCaseDirDatas(simCase, dir, caseDirDatas);
		final int nCaseDirDatas = caseDirDatas.size();
		return caseDirDatas.toArray(new PlanCaseDirData[nCaseDirDatas]);
	}

	private static void addToPlanCaseDirDatas(
			final SimCaseManager.SimCase simCase, final File dir,
			final ArrayList<PlanCaseDirData> caseDirDatas) {
		final PlanCaseDirData caseDirData = new PlanCaseDirData(simCase, dir);
		if (caseDirData._planDataRows != null &&
				caseDirData._origDashboardTables != null) {
			caseDirDatas.add(caseDirData);
		}
		final File[] subDirs = dir.listFiles(new FileFilter() {

			@Override
			public boolean accept(final File f) {
				final String fName = f.getName();
				return f.isDirectory() && fName.length() > 0 &&
						!fName.equalsIgnoreCase("(ignore)");
			}
		});
		final int nSubDirs = subDirs.length;
		for (int k = 0; k < nSubDirs; ++k) {
			final File subDir = subDirs[k];
			addToPlanCaseDirDatas(simCase, subDir, caseDirDatas);
		}
	}
}
