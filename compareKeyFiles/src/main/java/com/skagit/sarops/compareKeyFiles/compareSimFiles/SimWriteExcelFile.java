package com.skagit.sarops.compareKeyFiles.compareSimFiles;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
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

class SimWriteExcelFile {
	final private static String _ClmnHdgsString = //
			"Treatment Trtmnt " //
					+ "||Orig-New|| NewInOrigCntnmnt OrigInNewCntnmnt IntersectionScore " //
					+ "nOrig OrigCenter Orig50SmiMjr Orig50SmiMnr Orig50SmiMjrHdg " //
					+ "nNew NewCenter New50SmiMjr New50SmiMnr New50SmiMjrHdg";
	final private static String[] _ClmnHdgs;

	final private static int _JForOrigInNew;
	final private static int _JForNewInOrig;

	static {
		_ClmnHdgs = _ClmnHdgsString.split("\\s+");
		int jForOrigInNew = -1, jForNewInOrig = -1;
		for (int k = 0; k < _ClmnHdgs.length; ++k) {
			final String clmnHdg = _ClmnHdgs[k];
			if (clmnHdg.equals("NewInOrigCntnmnt")) {
				jForNewInOrig = k;
			}
			if (clmnHdg.equals("OrigInNewCntnmnt")) {
				jForOrigInNew = k;
			}
		}
		_JForOrigInNew = jForOrigInNew;
		_JForNewInOrig = jForNewInOrig;
	}

	/** The only non-private method: */
	static void simWriteExcelFile(final File homeDir, final TreeMap<File, String> caseDirToShortName,
			final TreeMap<File, TreeMap<Treatment, SimDataRow>> caseDirToTreatmentToDataRow) {
		final XSSFWorkbook workBook = new XSSFWorkbook();
		final CompareUtils.CellStyles cellStyles = new CompareUtils.CellStyles(workBook);
		final int nCaseDirs = caseDirToTreatmentToDataRow.size();

		/** Build the map from caseDir to sheetName. */
		final TreeMap<File, String> caseDirToSheetName = new TreeMap<>();
		{
			final TreeSet<String> sheetNames = new TreeSet<>();
			sheetNames.add(CompareKeyFiles._AllCasesName);
			final Iterator<File> it = caseDirToTreatmentToDataRow.keySet().iterator();
			for (int k0 = 0; k0 < nCaseDirs; ++k0) {
				final File caseDir = it.next();
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

		final Iterator<Map.Entry<File, TreeMap<Treatment, SimDataRow>>> it = caseDirToTreatmentToDataRow.entrySet()
				.iterator();
		final ArrayList<SimDataRow> badDataRows = new ArrayList<>();
		for (int k = 0; k < nCaseDirs; ++k) {
			final Map.Entry<File, TreeMap<Treatment, SimDataRow>> entry = it.next();
			final File caseDir = entry.getKey();
			if (caseDir != null) {
				final TreeMap<Treatment, SimDataRow> treatmentToDataRow = entry.getValue();
				final String sheetName = caseDirToSheetName.get(caseDir);
				final SimDataRow badDataRow = buildCaseSheet(workBook, caseDir, sheetName, treatmentToDataRow,
						cellStyles);
				if (badDataRow != null) {
					badDataRows.add(badDataRow);
				}
			}
		}

		/**
		 * Build allCasesSheet from the bad DataRows of the individual caseDirs.
		 */
		final Sheet allCasesSheet = workBook.createSheet(CompareKeyFiles._AllCasesName);
		buildAllCasesSheet(allCasesSheet, badDataRows, cellStyles);
		workBook.setSheetOrder(CompareKeyFiles._AllCasesName, 0);

		/** Dump it to the first allowable fileName that we invent. */
		CompareUtils.DumpExcelFile(homeDir, "Sim", workBook);
	}

	private static SimDataRow buildCaseSheet(final XSSFWorkbook workBook, final File caseDir, final String sheetName,
			final TreeMap<Treatment, SimDataRow> treatmentToDataRow, final CompareUtils.CellStyles cellStyles) {
		final int nDataRows = treatmentToDataRow.size();
		if (nDataRows == 0) {
			return null;
		}

		int maxNInDataRows = 0;
		for (final SimDataRow dataRow : treatmentToDataRow.values()) {
			maxNInDataRows = Math.max(Math.max(maxNInDataRows, dataRow._nNew), dataRow._nOrig);
		}

		Sheet sheet = null;
		try {
			sheet = workBook.createSheet(sheetName);
		} catch (final IllegalArgumentException e) {
		}
		/** Hdg lines. */
		int iRow = 0;
		final Row excelHdgRow = PoiUtils.getOrCreateRow(sheet, iRow++);
		final int nClmns = _ClmnHdgs.length;
		final int jForTopLeft = 0;
		for (int k = 0; k < nClmns; ++k) {
			final String clmnHdg = _ClmnHdgs[k];
			CompareUtils.createStringCell(excelHdgRow, jForTopLeft + k, clmnHdg, cellStyles);
		}

		final Iterator<Map.Entry<Treatment, SimDataRow>> it = treatmentToDataRow.entrySet().iterator();
		Row worstOrigInNewRow = null;
		Row worstNewInOrigRow = null;
		double worstNewInOrig = 0d;
		double worstOrigInNew = 0d;
		Row badOrigInNewRow = null;
		Row badNewInOrigRow = null;
		double badNewInOrig = 0d;
		double badOrigInNew = 0d;
		SimDataRow badNewInOrigDataRow = null;
		SimDataRow badOrigInNewDataRow = null;

		for (int k0 = 0; k0 < nDataRows; ++k0) {
			final Map.Entry<Treatment, SimDataRow> entry = it.next();
			final Treatment treatment = entry.getKey();
			final SimDataRow dataRow = entry.getValue();
			final Row row = PoiUtils.getOrCreateRow(sheet, iRow++);
			int j = jForTopLeft;
			CompareUtils.createLeftStringCell(row, j++, treatment.getTreatmentName0(), cellStyles);
			CompareUtils.createLeftStringCell(row, j++, treatment.getTreatmentName1(), cellStyles);
			CompareUtils.createNonnegativeCell(row, j++, dataRow._nmiBetweenCenters, cellStyles);
			CompareUtils.createPerCentCell(row, j++, dataRow._newInOrigCntnmnt, cellStyles);
			CompareUtils.createPerCentCell(row, j++, dataRow._origInNewCntnmnt, cellStyles);
			CompareUtils.createPerCentCell(row, j++, dataRow._intersectionScore, cellStyles);
			if (worstNewInOrigRow == null || dataRow._newInOrigCntnmnt > worstNewInOrig) {
				worstNewInOrigRow = row;
				worstNewInOrig = dataRow._newInOrigCntnmnt;
			}
			if (worstOrigInNewRow == null || dataRow._origInNewCntnmnt > worstOrigInNew) {
				worstOrigInNewRow = row;
				worstOrigInNew = dataRow._origInNewCntnmnt;
			}
			/** Check for bad. */
			final int maxNParticles = treatment.getMaxNParticles();
			final boolean qualifiesForBad = Math.min(dataRow._nNew, dataRow._nOrig) > 0.75 * maxNParticles;
			if (qualifiesForBad) {
				if (badNewInOrigRow == null || dataRow._newInOrigCntnmnt > badNewInOrig) {
					badNewInOrigRow = row;
					badNewInOrig = dataRow._newInOrigCntnmnt;
					badNewInOrigDataRow = dataRow;
				}
				if (badOrigInNewRow == null || dataRow._origInNewCntnmnt > badOrigInNew) {
					badOrigInNewRow = row;
					badOrigInNew = dataRow._origInNewCntnmnt;
					badOrigInNewDataRow = dataRow;
				}
			}
			CompareUtils.createIntCell(row, j++, dataRow._nOrig, cellStyles);
			CompareUtils.createStringCell(row, j++, dataRow._origCenter == null ? "" : dataRow._origCenter.getString(),
					cellStyles);
			CompareUtils.createNonnegativeCell(row, j++, dataRow._orig50SmiMjrNmi, cellStyles);
			CompareUtils.createNonnegativeCell(row, j++, dataRow._orig50SmiMnrNmi, cellStyles);
			CompareUtils.createHdgCell(row, j++, dataRow._orig50SmiMjrHdg, cellStyles);
			CompareUtils.createIntCell(row, j++, dataRow._nNew, cellStyles);
			CompareUtils.createStringCell(row, j++, dataRow._newCenter == null ? "" : dataRow._newCenter.getString(),
					cellStyles);
			CompareUtils.createNonnegativeCell(row, j++, dataRow._new50SmiMjrNmi, cellStyles);
			CompareUtils.createNonnegativeCell(row, j++, dataRow._new50SmiMnrNmi, cellStyles);
			CompareUtils.createHdgCell(row, j++, dataRow._new50SmiMjrHdg, cellStyles);
		}
		final Cell worstOrigInNewCell = worstOrigInNewRow.getCell(_JForOrigInNew);
		worstOrigInNewCell.setCellStyle(cellStyles._origWorstStyle);
		final Cell worstNewInOrigCell = worstNewInOrigRow.getCell(_JForNewInOrig);
		worstNewInOrigCell.setCellStyle(cellStyles._newWorstStyle);
		/** Overrule worst with Bad. */
		if (badOrigInNewRow != null) {
			final Cell badOrigInNewCell = badOrigInNewRow.getCell(_JForOrigInNew);
			badOrigInNewCell.setCellStyle(cellStyles._origBadStyle);
			final Cell badNewInOrigCell = badNewInOrigRow.getCell(_JForNewInOrig);
			badNewInOrigCell.setCellStyle(cellStyles._newBadStyle);
			/** Return the worst "bad" DataRow. */
			final SimDataRow dataRowToReturn = badNewInOrig > badOrigInNew ? badNewInOrigDataRow : badOrigInNewDataRow;
			return dataRowToReturn;
		}
		return null;
	}

	private static void buildAllCasesSheet(final Sheet allCasesSheet, final ArrayList<SimDataRow> badDataRows,
			final CompareUtils.CellStyles cellStyles) {
		final int nBadDataRows = badDataRows.size();
		if (nBadDataRows == 0) {
			return;
		}
		/** Hdg lines. */
		int iRow = 0;
		final Row excelHdgRow = PoiUtils.getOrCreateRow(allCasesSheet, iRow++);
		final int nClmns = _ClmnHdgs.length;
		final int jForTopLeft = 0;
		for (int k = 0; k < nClmns; ++k) {
			final String clmnHdg = _ClmnHdgs[k];
			final Cell hdgCell = excelHdgRow.createCell(jForTopLeft + k);
			// hdgCell.setCellType(CellType.STRING);
			hdgCell.setCellValue(clmnHdg);
			hdgCell.setCellStyle(cellStyles._stringStyle);
		}

		Row badOrigInNewRow = null;
		Row badNewInOrigRow = null;
		double badNewInOrig = 0d;
		double badOrigInNew = 0d;
		for (int k0 = 0; k0 < nBadDataRows; ++k0) {
			final SimDataRow dataRow = badDataRows.get(k0);
			final Treatment treatment = dataRow._treatment;
			final Row row = PoiUtils.getOrCreateRow(allCasesSheet, iRow++);
			int j = jForTopLeft;
			CompareUtils.createLeftStringCell(row, j++, treatment.getTreatmentName0(), cellStyles);
			CompareUtils.createLeftStringCell(row, j++, treatment.getTreatmentName1(), cellStyles);
			CompareUtils.createNonnegativeCell(row, j++, dataRow._nmiBetweenCenters, cellStyles);
			CompareUtils.createPerCentCell(row, j++, dataRow._newInOrigCntnmnt, cellStyles);
			CompareUtils.createPerCentCell(row, j++, dataRow._origInNewCntnmnt, cellStyles);
			CompareUtils.createPerCentCell(row, j++, dataRow._intersectionScore, cellStyles);
			if (badNewInOrigRow == null || dataRow._newInOrigCntnmnt > badNewInOrig) {
				badNewInOrigRow = row;
				badNewInOrig = dataRow._newInOrigCntnmnt;
			}
			if (badOrigInNewRow == null || dataRow._origInNewCntnmnt > badOrigInNew) {
				badOrigInNewRow = row;
				badOrigInNew = dataRow._origInNewCntnmnt;
			}
			CompareUtils.createIntCell(row, j++, dataRow._nOrig, cellStyles);
			CompareUtils.createLeftStringCell(row, j++,
					dataRow._origCenter == null ? "" : dataRow._origCenter.getString(), cellStyles);
			CompareUtils.createNonnegativeCell(row, j++, dataRow._orig50SmiMjrNmi, cellStyles);
			CompareUtils.createNonnegativeCell(row, j++, dataRow._orig50SmiMnrNmi, cellStyles);
			CompareUtils.createHdgCell(row, j++, dataRow._orig50SmiMjrHdg, cellStyles);
			CompareUtils.createIntCell(row, j++, dataRow._nNew, cellStyles);
			CompareUtils.createLeftStringCell(row, j++,
					dataRow._newCenter == null ? "" : dataRow._newCenter.getString(), cellStyles);
			CompareUtils.createNonnegativeCell(row, j++, dataRow._new50SmiMjrNmi, cellStyles);
			CompareUtils.createNonnegativeCell(row, j++, dataRow._new50SmiMnrNmi, cellStyles);
			CompareUtils.createHdgCell(row, j++, dataRow._new50SmiMjrHdg, cellStyles);
		}
		final Cell badOrigInNewCell = badOrigInNewRow.getCell(_JForOrigInNew);
		badOrigInNewCell.setCellStyle(cellStyles._origBadStyle);
		final Cell badNewInOrigCell = badNewInOrigRow.getCell(_JForNewInOrig);
		badNewInOrigCell.setCellStyle(cellStyles._newBadStyle);
	}
}
