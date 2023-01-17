package com.skagit.sarops.compareKeyFiles.comparePlanFiles;

import java.io.File;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.skagit.sarops.compareKeyFiles.CompareKeyFiles;
import com.skagit.sarops.compareKeyFiles.CompareUtils;
import com.skagit.util.CombinatoricTools;
import com.skagit.util.poiUtils.PoiUtils;

class PlanWriteExcelFile {
	final private static String _ClmnHdgsString = "Treatment Trtmnt Orig New Increase";
	final private static String[] _ClmnHdgs;

	static {
		_ClmnHdgs = _ClmnHdgsString.trim().split("\\s+");
	}

	/** The only non-private method: */
	static void planWriteExcelFile(final File homeDir, final TreeMap<File, String> caseDirToShortName,
			final PlanCaseDirData[] caseDirDatas) {
		final XSSFWorkbook workBook = new XSSFWorkbook();
		final CompareUtils.CellStyles cellStyles = new CompareUtils.CellStyles(workBook);

		/** Build the map from caseDir to sheetName. */
		final TreeMap<File, String> caseDirToSheetName = new TreeMap<>();
		{
			final TreeSet<String> sheetNames = new TreeSet<>();
			sheetNames.add(CompareKeyFiles._AllCasesName);
			final int nCaseDirDatas = caseDirDatas == null ? 0 : caseDirDatas.length;
			for (int k0 = 0; k0 < nCaseDirDatas; ++k0) {
				final File caseDir = caseDirDatas[k0]._caseDir;
				final String shortName = caseDirToShortName.get(caseDir);
				for (int k1 = 0;; ++k1) {
					final String sheetName = CombinatoricTools.alterString(shortName, CompareUtils._MaxSheetNameLength,
							/* uniquifiers= */"!@#$%^&", k1);
					if (sheetNames.add(sheetName)) {
						caseDirToSheetName.put(caseDir, sheetName);
						break;
					}
				}
			}
		}

		final ArrayList<PlanDataRow> badPlanDataRowList = new ArrayList<>();
		final int nCaseDirDatas = caseDirDatas.length;
		for (int k = 0; k < nCaseDirDatas; ++k) {
			final PlanCaseDirData caseDirData = caseDirDatas[k];
			final File caseDir = caseDirData._caseDir;
			final PlanDataRow[] planDataRows = caseDirData._planDataRows;
			if (planDataRows != null && planDataRows.length > 0) {
				final String sheetName = caseDirToSheetName.get(caseDir);
				buildPlanCaseSheet(workBook, caseDirToShortName, sheetName, planDataRows, badPlanDataRowList,
						cellStyles);
			}
		}
		final PlanDataRow[] badPlanDataRows = badPlanDataRowList.toArray(new PlanDataRow[badPlanDataRowList.size()]);
		buildPlanCaseSheet(workBook, caseDirToShortName, /* sheetName= */CompareKeyFiles._BadDataRowsName,
				badPlanDataRows, /* badPlanDataRows= */null, cellStyles);

		workBook.setSheetOrder(CompareKeyFiles._BadDataRowsName, /* pos= */0);
		/** Dump it to the first allowable fileName that we invent. */
		CompareUtils.DumpExcelFile(homeDir, "Plan", workBook);
	}

	private static void buildPlanCaseSheet(final XSSFWorkbook workBook, final TreeMap<File, String> caseDirToShortName,
			final String sheetName, final PlanDataRow[] planDataRows, final ArrayList<PlanDataRow> badPlanDataRows,
			final CompareUtils.CellStyles cellStyles) {
		final Sheet sheet = workBook.createSheet(sheetName);
		/** Hdg lines. */
		int iRow = 0;
		final Row excelHdgRow = PoiUtils.getOrCreateRow(sheet, iRow++);
		final int nClmns = _ClmnHdgs.length;
		final int jForTopLeft = 0;
		for (int k = 0; k < nClmns; ++k) {
			final String clmnHdg = _ClmnHdgs[k];
			final Cell hdgCell = excelHdgRow.createCell(jForTopLeft + k);
			// hdgCell.setCellType(CellType.STRING);
			hdgCell.setCellValue(clmnHdg);
			hdgCell.setCellStyle(cellStyles._stringStyle);
		}

		final boolean forGlobal = badPlanDataRows == null;

		final int nDataRows = planDataRows.length;
		for (int k0 = 0; k0 < nDataRows; ++k0) {
			final PlanDataRow planDataRow = planDataRows[k0];
			final Row row = PoiUtils.getOrCreateRow(sheet, iRow++);
			int j = jForTopLeft;
			final String treatmentName = planDataRow.createTreatmentName(caseDirToShortName);
			CompareUtils.createLeftStringCell(row, j++, treatmentName, cellStyles);
			final String trtmntName = planDataRow.createTrtmntName(caseDirToShortName);
			CompareUtils.createLeftStringCell(row, j++, trtmntName, cellStyles);
			CompareUtils.createDoubleCell(row, j++, planDataRow._origValue, cellStyles);
			CompareUtils.createDoubleCell(row, j++, planDataRow._newValue, cellStyles);
			CompareUtils.createDoubleCell(row, j++, planDataRow._increase, cellStyles,
					/* bad= */planDataRow._violation);
			if (planDataRow._violation && !forGlobal) {
				badPlanDataRows.add(planDataRow);
			}
		}
	}

}
