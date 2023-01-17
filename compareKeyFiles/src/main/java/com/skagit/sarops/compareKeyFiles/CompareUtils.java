package com.skagit.sarops.compareKeyFiles;

import java.awt.Color;
import java.io.File;
import java.io.IOException;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.skagit.sarops.model.Model;
import com.skagit.util.Constants;
import com.skagit.util.colorUtils.ColorUtils;
import com.skagit.util.poiUtils.ExcelDumper;

public class CompareUtils {
	public final static int _MaxSheetNameLength = 31;

	public static class CellStyles {
		final public XSSFCellStyle _stringStyle;
		final public XSSFCellStyle _leftStringStyle;
		final public XSSFCellStyle _intStyle;
		final public XSSFCellStyle _doubleStyle;
		final public XSSFCellStyle _bigDoubleStyle;
		final public XSSFCellStyle _badDoubleStyle;
		final public XSSFCellStyle _bigBadDoubleStyle;
		final public XSSFCellStyle _perCentStyle;
		final public XSSFCellStyle _latOrLngStyle;
		final public XSSFCellStyle _origWorstStyle;
		final public XSSFCellStyle _origBadStyle;
		final public XSSFCellStyle _newWorstStyle;
		final public XSSFCellStyle _newBadStyle;

		public CellStyles(final XSSFWorkbook workBook) {
			_stringStyle = workBook.createCellStyle();
			_stringStyle.setAlignment(HorizontalAlignment.CENTER);
			_leftStringStyle = workBook.createCellStyle();
			_leftStringStyle.cloneStyleFrom(_stringStyle);
			_leftStringStyle.setAlignment(HorizontalAlignment.LEFT);
			_intStyle = workBook.createCellStyle();
			_intStyle.setAlignment(HorizontalAlignment.RIGHT);
			_doubleStyle = workBook.createCellStyle();
			_doubleStyle.setAlignment(HorizontalAlignment.RIGHT);
			_doubleStyle.setDataFormat(workBook.createDataFormat().getFormat("0.00"));
			_bigDoubleStyle = workBook.createCellStyle();
			_bigDoubleStyle.setAlignment(HorizontalAlignment.RIGHT);
			_bigDoubleStyle.setDataFormat(workBook.createDataFormat().getFormat("0.00E+00"));
			_badDoubleStyle = workBook.createCellStyle();
			_badDoubleStyle.cloneStyleFrom(_doubleStyle);
			final int[] badDoubleRgb = ColorUtils._LightBlue;
			final Color badDoubleColor = new Color(badDoubleRgb[0], badDoubleRgb[1], badDoubleRgb[2]);
			final XSSFColor badXssfColor = new XSSFColor(badDoubleColor, null);
			_badDoubleStyle.setFillForegroundColor(badXssfColor);
			_badDoubleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			_bigBadDoubleStyle = workBook.createCellStyle();
			_bigBadDoubleStyle.cloneStyleFrom(_doubleStyle);
			_bigBadDoubleStyle.setFillForegroundColor(badXssfColor);
			_bigBadDoubleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			_perCentStyle = workBook.createCellStyle();
			_perCentStyle.setAlignment(HorizontalAlignment.RIGHT);
			_perCentStyle.setDataFormat(workBook.createDataFormat().getFormat("0.00%"));
			_latOrLngStyle = workBook.createCellStyle();
			_latOrLngStyle.setAlignment(HorizontalAlignment.RIGHT);
			_latOrLngStyle.setDataFormat(workBook.createDataFormat().getFormat("0.000" + Constants._DegreeSymbol));
			/** origWorstStyle. */
			_origWorstStyle = workBook.createCellStyle();
			_origWorstStyle.cloneStyleFrom(_perCentStyle);
			final int[] origWorstRgb = ColorUtils._LightBlue;
			final Color origWorstColor = new Color(origWorstRgb[0], origWorstRgb[1], origWorstRgb[2]);
			final XSSFColor origWorstXssfColor = new XSSFColor(origWorstColor, null);
			_origWorstStyle.setFillForegroundColor(origWorstXssfColor);
			_origWorstStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			/** origBadStyle. */
			_origBadStyle = workBook.createCellStyle();
			_origBadStyle.cloneStyleFrom(_perCentStyle);
			final int[] origBadRgb = ColorUtils._LightSeaGreen;
			final Color origBadColor = new Color(origBadRgb[0], origBadRgb[1], origBadRgb[2]);
			final XSSFColor origBadXssfColor = new XSSFColor(origBadColor, null);
			_origBadStyle.setFillForegroundColor(origBadXssfColor);
			_origBadStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			/** newWorstStyle. */
			_newWorstStyle = workBook.createCellStyle();
			_newWorstStyle.cloneStyleFrom(_perCentStyle);
			final int[] newWorstRgb = ColorUtils._Pink;
			final Color newWorstColor = new Color(newWorstRgb[0], newWorstRgb[1], newWorstRgb[2]);
			final XSSFColor newWorstXssfColor = new XSSFColor(newWorstColor, null);
			_newWorstStyle.setFillForegroundColor(newWorstXssfColor);
			/** newBadStyle. */
			_newBadStyle = workBook.createCellStyle();
			_newBadStyle.cloneStyleFrom(_perCentStyle);
			final int[] newBadRgb = ColorUtils._DeepPink;
			final Color newBadColor = new Color(newBadRgb[0], newBadRgb[1], newBadRgb[2]);
			final XSSFColor newBadXssfColor = new XSSFColor(newBadColor, null);
			_newBadStyle.setFillForegroundColor(newBadXssfColor);
			_newBadStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		}
	}

	private static void createBlankCell(final Row row, final int j) {
		final Cell cell = row.createCell(j);
		cell.setBlank();
	}

	public static void createDoubleCell(final Row row, final int j, final double content, final CellStyles cellStyles) {
		createDoubleCell(row, j, content, cellStyles, /* bad= */false);
	}

	public static void createBadDoubleCell(final Row row, final int j, final double content,
			final CellStyles cellStyles) {
		createDoubleCell(row, j, content, cellStyles, /* bad= */true);
	}

	public static void createDoubleCell(final Row row, final int j, double content, final CellStyles cellStyles,
			final boolean bad) {
		final XSSFCellStyle style;
		if (Double.isNaN(content)) {
			content = 0d / 0d;
			style = bad ? cellStyles._badDoubleStyle : cellStyles._doubleStyle;
		} else {
			content = Math.max(-1e99, Math.min(1e99, content));
			final double absContent = Math.abs(content);
			if (absContent == 0d || (0.005 <= absContent && absContent <= 10000000d)) {
				style = bad ? cellStyles._badDoubleStyle : cellStyles._doubleStyle;
			} else {
				style = bad ? cellStyles._bigBadDoubleStyle : cellStyles._bigDoubleStyle;
			}
		}
		final Cell cell = row.createCell(j);
		// cell.setCellType(CellType.NUMERIC);
		cell.setCellStyle(style);
		cell.setCellValue(content);
	}

	public static void createHdgCell(final Row row, final int j, final double content, final CellStyles cellStyles) {
		final Cell cell = row.createCell(j);
		if (-0d <= content && content < 360d) {
			// cell.setCellType(CellType.NUMERIC);
			cell.setCellStyle(cellStyles._latOrLngStyle);
			cell.setCellValue(content);
			return;
		}
		createBlankCell(row, j);
	}

	public static void createIntCell(final Row row, final int j, final int content, final CellStyles cellStyles) {
		final Cell cell = row.createCell(j);
		// cell.setCellType(CellType.NUMERIC);
		cell.setCellStyle(cellStyles._intStyle);
		cell.setCellValue(content);
	}

	public static void createPerCentCell(final Row row, final int j, final double proportion,
			final CellStyles cellStyles) {
		if (!(proportion >= 0d)) {
			createBlankCell(row, j);
			return;
		}
		final Cell cell = row.createCell(j);
		// cell.setCellType(CellType.NUMERIC);
		cell.setCellStyle(cellStyles._perCentStyle);
		cell.setCellValue(proportion);
	}

	public static void createNonnegativeCell(final Row row, final int j, final double content,
			final CellStyles cellStyles) {
		if (-0d <= content) {
			createDoubleCell(row, j, Math.min(1e50, content), cellStyles);
			return;
		}
		createBlankCell(row, j);
	}

	public static void createStringCell(final Row row, final int j, final String content, final CellStyles cellStyles) {
		final Cell cell = row.createCell(j);
		// cell.setCellType(CellType.STRING);
		cell.setCellStyle(cellStyles._stringStyle);
		cell.setCellValue(content);
	}

	public static void createLeftStringCell(final Row row, final int j, final String content,
			final CellStyles cellStyles) {
		final Cell cell = row.createCell(j);
		// cell.setCellType(CellType.STRING);
		cell.setCellStyle(cellStyles._leftStringStyle);
		cell.setCellValue(content);
	}

	public static void DumpExcelFile(final File homeDir, final String simOrPlan, final XSSFWorkbook workBook) {
		String excelFileNameCore = null;
		try {
			excelFileNameCore = homeDir.getCanonicalFile().getName();
		} catch (final IOException e) {
		}
		for (int k = 0;; ++k) {
			final String fileName;
			if (k == 0) {
				fileName = String.format("%s(%s).xlsx", excelFileNameCore, simOrPlan);
			} else {
				fileName = String.format("%s(%s)-%d.xlsx", excelFileNameCore, simOrPlan, k - 1);
			}
			final File f = new File(homeDir, fileName);
			if (!f.exists()) {
				ExcelDumper.closeAndWriteWorkBook(workBook, f);
				break;
			}
		}
	}

	public static String[] getBigAndLittleStrings(final String name0, final int int0, final String name1,
			final int int1) {
		final String bigString0;
		final String littleString0;
		if (int0 == Model._WildCard) {
			bigString0 = littleString0 = Model._WildCardString;
		} else {
			final int name0Len = name0.length();
			if (name0.length() > 7) {
				bigString0 = name0.substring(0, 3) + "~" + name0.substring(name0Len - 3);
			} else {
				bigString0 = name0;
			}
			littleString0 = String.format("%02d", int0);
		}
		final String bigString1;
		final String littleString1;
		if (int1 == Model._WildCard) {
			bigString1 = littleString1 = Model._WildCardString;
		} else {
			final int name1Len = name1.length();
			if (name1.length() > 7) {
				bigString1 = name1.substring(0, 3) + "~" + name1.substring(name1Len - 3);
			} else {
				bigString1 = name1;
			}
			littleString1 = String.format("%02d", int1);
		}
		final String bigString = String.format("%s/%s", bigString0, bigString1);
		final String littleString = String.format("%s/%s", littleString0, littleString1);
		return new String[] { bigString, littleString };
	}
}
