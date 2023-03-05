package com.skagit.sarops.model.preDistressModel.sail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.exceptions.InvalidOperationException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.Element;

import com.skagit.sarops.model.io.ModelReader;
import com.skagit.sarops.model.io.ModelReader.ReaderException;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.simCaseManager.SimCaseManager.SimCase;
import com.skagit.sarops.tracker.StateVectorType;
import com.skagit.util.CombinatoricTools;
import com.skagit.util.ElementIterator;
import com.skagit.util.HdgKts;
import com.skagit.util.LsFormatter;
import com.skagit.util.MathX;
import com.skagit.util.ResourcesLister;
import com.skagit.util.SaropsDirsTracker;
import com.skagit.util.StaticUtilities;
import com.skagit.util.StringUtilities;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.poiUtils.PoiUtils;
import com.skagit.util.randomx.Randomx;

public class Polars {
	/** For specification of the spreadsheet in the xml: */
	final static String _PolarsWorkBookAttributeName = "POLARS_WORKBOOK";
	final static String _PolarsSheetAttributeName = "POLARS_SHEET";
	final static String _ManufacturerAttributeName = "Manufacturer";
	final static String _BoatClassAttributeName = "Class";
	/** For reading from a spreadsheet that's in the data directory. */
	final static String _PolarsSubdirectoryName = "Polars";
	/** For reading in from a spreadsheet: */
	final private static String[] _LcLegalSuffixes = { "xlsx", "xls" };
	/** For reading the polars directly from the xml: */
	final static String _WindSpeedTag = "WIND_SPEED";
	final static String _WindSpeedAttributeName = "WindSpeed";
	final static String _RelativeAngleTag = "RELATIVE_ANGLE";
	final static String _RelativeAngleAttributeName = "RelativeAngle";
	final static String _BoatSpeedAttributeName = "BoatSpeed";

	/**
	 * The following is both a caption within the spreadsheet, and an attribute name
	 * when reading the polars in directly from the xml.
	 */
	final private static String _LoaAttributeName = "LOA";

	/** Just information about the spreadsheet line if there is one. */
	final public boolean _fromResource;
	final public boolean _fromJavaIoFile;
	final public boolean _fromXml;
	final public String _manufacturer;
	final public String _boatClass;
	final public String _workBookName;
	final public String _sheetName;
	/** Important data. */
	final public double _lengthOverallFt;
	final public SpeedAndTable[] _speedAndOneSidedTables;

	public static Comparator<HdgKts> _HdgOnly = new Comparator<>() {

		@Override
		public int compare(final HdgKts hdgKts0, final HdgKts hdgKts1) {
			final double hdg0 = hdgKts0.getHdg();
			final double hdg1 = hdgKts1.getHdg();
			return hdg0 < hdg1 ? -1 : (hdg0 > hdg1 ? 1 : 0);
		}
	};

	public Polars(final SimCase simCase, final String workBookName, final String sheetName0, final String manufacturer,
			final String boatClass) {
		_fromXml = false;
		final String lcSuffix;
		final int lastDot0 = workBookName.lastIndexOf('.');
		if (lastDot0 >= 0) {
			final String lcSuffixX = workBookName.substring(lastDot0 + 1).toLowerCase();
			String lcSuffixY = null;
			for (final String lcLegalSuffix : _LcLegalSuffixes) {
				if (lcLegalSuffix.equalsIgnoreCase(lcSuffixX)) {
					lcSuffixY = lcLegalSuffix;
					break;
				}
			}
			lcSuffix = lcSuffixY;
		} else {
			lcSuffix = null;
		}

		final String lcCore;
		if (lcSuffix == null) {
			lcCore = workBookName.toLowerCase();
		} else {
			lcCore = workBookName.substring(0, lastDot0).toLowerCase();
		}

		SpreadsheetLine spreadSheetLine = null;

		@SuppressWarnings("rawtypes")
		final Class clazz = StaticUtilities.getMyClass();
		final Package packaje = clazz.getPackage();
		final String regEx = "polars[/\\\\].*\\.(xls|xlsx)";
		final Pattern pattern = Pattern.compile(regEx, Pattern.CASE_INSENSITIVE);
		final ArrayList<ResourcesLister.ResourceNamePlus> resourceNamePluses = ResourcesLister
				.getRelativeResourceNamePluses(/* _logger= */null, packaje, pattern, /* lookInDirectories= */true,
						/* lookInJarFiles= */true, /* debug= */false);
		final int nWorkbooks = resourceNamePluses.size();

		/** Find the matching resource. */
		ResourcesLister.ResourceNamePlus matchingResourceNamePlus = null;
		for (int k = 0; k < nWorkbooks; ++k) {
			final ResourcesLister.ResourceNamePlus resourceNamePlus = resourceNamePluses.get(k);
			final String resourceName0 = resourceNamePlus._resourceName;
			/**
			 * Compute resourceName1, which is the fileName including the suffix.
			 */
			String resourceName1 = StringUtilities.cleanUpString(resourceName0, "\\/", '?');
			final int lastHook = resourceName1.lastIndexOf('?');
			if (lastHook >= 0) {
				resourceName1 = resourceName1.substring(lastHook + 1);
			}
			/** Break up resourceName1 into the core and the suffix. */
			final int lastDot1 = resourceName1.lastIndexOf('.');
			final String lcResourceCoreName1;
			final String lcSuffix1;
			if (lastDot1 >= 0) {
				lcResourceCoreName1 = resourceName1.substring(0, lastDot1).toLowerCase();
				lcSuffix1 = resourceName1.substring(lastDot1 + 1).toLowerCase();
			} else {
				lcResourceCoreName1 = resourceName1.toLowerCase();
				lcSuffix1 = "";
			}
			final boolean matches;
			if (lcSuffix == null) {
				matches = lcResourceCoreName1.equals(lcCore);
			} else {
				matches = lcResourceCoreName1.equals(lcCore) && lcSuffix.equals(lcSuffix1);
			}
			if (matches) {
				if (matchingResourceNamePlus == null) {
					matchingResourceNamePlus = resourceNamePlus;
				} else {
					matchingResourceNamePlus = null;
					break;
				}
			}
		}

		if (matchingResourceNamePlus != null) {
			final String resourceName = matchingResourceNamePlus._resourceName;
			final int lastDot1 = resourceName.lastIndexOf('.');
			if (lastDot1 >= 0) {
				final String lcSuffix1 = resourceName.substring(lastDot1 + 1).toLowerCase();
				try (final InputStream is = clazz.getResourceAsStream(resourceName)) {
					spreadSheetLine = getSpreadsheetLine(simCase, lcSuffix1, is, sheetName0, manufacturer, boatClass);
				} catch (final IOException e) {
				}
			}
			if (spreadSheetLine != null) {
				_fromResource = true;
				_fromJavaIoFile = false;
				_workBookName = workBookName;
				_sheetName = spreadSheetLine._sheetName;
				_manufacturer = manufacturer;
				_boatClass = boatClass;
				_speedAndOneSidedTables = spreadSheetLine._speedAndOneSidedTable;
				_lengthOverallFt = spreadSheetLine._lengthOverallFt;
				return;
			}
		}
		_fromResource = false;

		/**
		 * Now try from the javaIoFile. Here, we're pickier; it must have the exact
		 * filePath.
		 */
		final File dataDir = new File(SaropsDirsTracker.getDataDir(), _PolarsSubdirectoryName);
		/** Break up resourceName1 into the core and the suffix. */
		final String[] fileNames;
		final int lastDot1 = workBookName.lastIndexOf('.');
		if (lastDot1 >= 0) {
			fileNames = new String[] { workBookName };
		} else {
			final int nSuffixes = _LcLegalSuffixes.length;
			fileNames = new String[nSuffixes];
			for (int k = 0; k < nSuffixes; ++k) {
				fileNames[k] = String.format("%s.%s", workBookName, _LcLegalSuffixes[k]);
			}
		}
		final int nFiles = fileNames.length;
		for (int k = 0; k < nFiles; ++k) {
			final String fileName = fileNames[k];
			final int lastDot2 = fileName.lastIndexOf('.');
			final String lcSuffix2 = fileName.substring(lastDot2 + 1);
			final File workbookFile = new File(dataDir, fileName);
			final String workbookPath = StringUtilities.getCanonicalPath(workbookFile);
			final File inputFile = new File(workbookPath);
			if (inputFile.exists() && !inputFile.isDirectory()) {
				try (FileInputStream fis = new FileInputStream(inputFile)) {
					spreadSheetLine = getSpreadsheetLine(simCase, lcSuffix2, fis, sheetName0, manufacturer, boatClass);
				} catch (final IOException e) {
				}
			}
			if (spreadSheetLine != null) {
				_fromJavaIoFile = true;
				_workBookName = workBookName;
				_sheetName = spreadSheetLine._sheetName;
				_manufacturer = manufacturer;
				_boatClass = boatClass;
				_speedAndOneSidedTables = spreadSheetLine._speedAndOneSidedTable;
				_lengthOverallFt = spreadSheetLine._lengthOverallFt;
				return;
			}
		}
		_fromJavaIoFile = false;
		_workBookName = _sheetName = _manufacturer = _boatClass = null;
		_speedAndOneSidedTables = null;
		_lengthOverallFt = Double.NaN;
	}

	/** Used when we are trying to read directly from the xml. */
	public Polars(final SimCaseManager.SimCase simCase, final Element sailorTypeElement,
			final TreeSet<ModelReader.StringPlus> stringPluses) {
		_fromXml = true;
		_fromResource = _fromJavaIoFile = false;
		_workBookName = _sheetName = _manufacturer = _boatClass = null;
		double lengthOverallFt = Double.NaN;
		try {
			lengthOverallFt = ModelReader.getDouble(simCase, sailorTypeElement, _LoaAttributeName, "ft", 10d,
					stringPluses);
		} catch (final ReaderException e) {
		}
		_lengthOverallFt = lengthOverallFt > 0d ? lengthOverallFt : 10d;
		final TreeMap<Double, TreeSet<HdgKts>> wndKtsToHdgKtsS = new TreeMap<>();
		final ElementIterator windKtsIt = new ElementIterator(sailorTypeElement);
		while (windKtsIt.hasNextElement()) {
			final Element windSpeedElement = windKtsIt.nextElement();
			final String tagName = windSpeedElement.getTagName();
			if (tagName.equals(_WindSpeedTag)) {
				try {
					final double windKts = ModelReader.getDouble(simCase, windSpeedElement, _WindSpeedAttributeName,
							"kts", 0d, stringPluses);
					final TreeSet<HdgKts> relativeHdgToKtsS = new TreeSet<>(_HdgOnly);
					final ElementIterator relativeHdgIterator = new ElementIterator(windSpeedElement);
					while (relativeHdgIterator.hasNextElement()) {
						final Element relativeHdgElement = relativeHdgIterator.nextElement();
						final String relativeHdgElementTag = relativeHdgElement.getTagName();
						if (relativeHdgElementTag.compareTo(_RelativeAngleTag) != 0) {
							continue;
						}
						final double relativeHdg = ModelReader.getDouble(simCase, relativeHdgElement,
								_RelativeAngleAttributeName, "degs", Double.NaN, stringPluses);
						final double boatKts = ModelReader.getDouble(simCase, relativeHdgElement,
								_BoatSpeedAttributeName, "kts", 0d, stringPluses);
						if (0d <= relativeHdg && relativeHdg <= 180d && boatKts >= 0d) {
							final HdgKts relativeHdgAndKts = new HdgKts(relativeHdg, boatKts,
									/* doublesAreHdgKts= */true);
							relativeHdgToKtsS.add(relativeHdgAndKts);
						}
					}
					if (relativeHdgToKtsS.size() > 0) {
						wndKtsToHdgKtsS.put(windKts, relativeHdgToKtsS);
					}
				} catch (final ReaderException e) {
				}
			}
		}
		_speedAndOneSidedTables = fleshOutOneSidedTables(wndKtsToHdgKtsS);
	}

	private static SpreadsheetLine getSpreadsheetLine(final SimCase simCase, final String lcSuffix1,
			final InputStream inputStream, final String sheetName, final String manufacturer, final String boatClass) {
		SpreadsheetLine spreadSheetLine = null;
		if (lcSuffix1.equalsIgnoreCase(_LcLegalSuffixes[0])) {
			try (final XSSFWorkbook xssfWorkBook = new XSSFWorkbook(inputStream)) {
				final Sheet sheet;
				if (sheetName != null) {
					sheet = xssfWorkBook.getSheet(sheetName);
				} else {
					final int nSheets = xssfWorkBook.getNumberOfSheets();
					if (nSheets == 0) {
						sheet = null;
					} else {
						sheet = xssfWorkBook.getSheetAt(0);
					}
				}
				spreadSheetLine = new SpreadsheetLine(simCase, sheet, manufacturer, boatClass);
			} catch (final InvalidOperationException e) {
			} catch (final IOException e1) {
			}
		} else if (lcSuffix1.equalsIgnoreCase(_LcLegalSuffixes[1])) {
			try (final HSSFWorkbook hssfWorkBook = new HSSFWorkbook(inputStream)) {
				final Sheet sheet;
				if (sheetName != null) {
					sheet = hssfWorkBook.getSheet(sheetName);
				} else {
					final int nSheets = hssfWorkBook.getNumberOfSheets();
					if (nSheets == 0) {
						sheet = null;
					} else {
						sheet = hssfWorkBook.getSheetAt(0);
					}
				}
				spreadSheetLine = new SpreadsheetLine(simCase, sheet, manufacturer, boatClass);
			} catch (final InvalidOperationException e) {
			} catch (final IOException e1) {
			}
		}
		return spreadSheetLine;
	}

	private static class SpreadsheetLine {
		final public String _sheetName;
		SpeedAndTable[] _speedAndOneSidedTable;
		final double _lengthOverallFt;

		private SpreadsheetLine(final SimCaseManager.SimCase simCase, final Sheet sheet, final String manufacturer,
				final String boatClass) {
			if (sheet == null) {
				_sheetName = null;
				_speedAndOneSidedTable = null;
				_lengthOverallFt = Double.NaN;
				return;
			}
			final MyLogger logger = SimCaseManager.getLogger(simCase);
			_sheetName = sheet.getSheetName();
			/** Find the captions row. */
			final int firstRowNumber = sheet.getFirstRowNum();
			final int lastRowNumber = sheet.getLastRowNum();
			/**
			 * Find the row of captions and record the captions and what columns they refer
			 * to.
			 */
			int captionRowNumber = 0;
			final TreeMap<String, Integer> captionToColNumber = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			for (captionRowNumber = firstRowNumber; captionRowNumber <= lastRowNumber; ++captionRowNumber) {
				final Row captionRow = sheet.getRow(captionRowNumber);
				if (captionRow == null) {
					continue;
				}
				final int firstCellNumber = captionRow.getFirstCellNum();
				final int lastCellNumber = captionRow.getLastCellNum();
				for (int iCell = firstCellNumber; iCell <= lastCellNumber; ++iCell) {
					final Cell cell = captionRow.getCell(iCell);
					if (cell != null && cell.getCellType() == CellType.STRING) {
						final String caption = StringUtilities.cleanUpString(cell.getStringCellValue(), " \t\n",
								(char) 0);
						if (caption.startsWith(_ManufacturerAttributeName) || caption.startsWith(_BoatClassAttributeName) || caption.startsWith(_LoaAttributeName)) {
							captionToColNumber.put(caption, iCell);
							continue;
						}
						final String[] fields1 = caption.toLowerCase().split("(twaup|vmgup|twadn|vmgdn|vgmup|vgmdn)+");
						/** To pass, split will return an empty string and one other. */
						if (fields1.length == 2 && fields1[0].length() == 0) {
							try {
								final double d = Double.parseDouble(fields1[1]);
								if (d > 0) {
									captionToColNumber.put(caption, iCell);
									continue;
								}
							} catch (final NumberFormatException e) {
							}
						}
						final String[] fields2 = caption.split("deg");
						/**
						 * To pass, split will return 2 strings, both of them numeric.
						 */
						if (fields2.length == 2) {
							try {
								final double d1 = Double.parseDouble(fields2[0]);
								final double d2 = Double.parseDouble(fields2[1]);
								if (d1 > 0 && d2 > 0) {
									captionToColNumber.put(caption, iCell);
									continue;
								}
							} catch (final NumberFormatException e) {
							}
						}
					}
				}
				if (!captionToColNumber.isEmpty()) {
					break;
				}
			}
			/**
			 * For any row bigger than captionRowNumber, if there's a string in manufacturer
			 * and boatClass, we have a potential Polar.
			 */
			Row winningRow = null;
			NextRow: for (int iRow = captionRowNumber + 1; iRow <= lastRowNumber; ++iRow) {
				final Row row = sheet.getRow(iRow);
				if (row == null) {
					continue;
				}
				boolean rightManufacturer = false;
				boolean rightBoatClass = false;
				for (final Map.Entry<String, Integer> entry : captionToColNumber.entrySet()) {
					final String caption = entry.getKey();
					final int colNumber = entry.getValue();
					final Cell cell = row.getCell(colNumber);
					if (caption.startsWith(_ManufacturerAttributeName)) {
						if (cell == null || (cell.getCellType() != CellType.STRING)) {
							/** Wrong row. */
							continue NextRow;
						}
						final String thisManufacturer = cell.getStringCellValue();
						/** Is this doesn't match the manufacturer, skip it. */
						if (!thisManufacturer.equalsIgnoreCase(manufacturer)) {
							continue NextRow;
						}
						rightManufacturer = true;
					} else if (caption.startsWith(_BoatClassAttributeName)) {
						if (cell == null || (cell.getCellType() != CellType.STRING)) {
							/** Wrong row. */
							continue NextRow;
						}
						final String thisBoatClass = cell.getStringCellValue();
						/** Is this doesn't match the class, skip it. */
						if (!thisBoatClass.equalsIgnoreCase(boatClass)) {
							continue NextRow;
						}
						rightBoatClass = true;
					} else {
						/** It must be a positive number. */
						if (cell == null || (cell.getCellType() != CellType.NUMERIC) || (cell.getNumericCellValue() <= 0d)) {
							continue NextRow;
						}
					}
				}
				if (!rightManufacturer || !rightBoatClass) {
					continue;
				}
				/** We have a winner. */
				winningRow = row;
				break;
			}
			if (winningRow == null) {
				_speedAndOneSidedTable = null;
				_lengthOverallFt = Double.NaN;
				return;
			}

			/** We have a valid row. Start with the length overall. */
			final Integer loaColNumberI = captionToColNumber.get(_LoaAttributeName.toLowerCase());
			if (loaColNumberI == null) {
				_speedAndOneSidedTable = null;
				_lengthOverallFt = Double.NaN;
				return;
			}
			final Cell loaCell = winningRow.getCell(loaColNumberI);
			_lengthOverallFt = loaCell.getNumericCellValue();

			/** Now gather _windKtsToHdgKtsS. */
			final TreeMap<Double, TreeSet<HdgKts>> windKtsToHdgKtsS = new TreeMap<>();
			final TreeMap<Double, HdgKts[]> windKtsToLimits = new TreeMap<>();
			for (final Map.Entry<String, Integer> entry : captionToColNumber.entrySet()) {
				final String caption = entry.getKey();
				final String captionLc = caption.toLowerCase();
				final int colNumber = entry.getValue();
				final Cell cell = winningRow.getCell(colNumber);
				if (captionLc.startsWith(_ManufacturerAttributeName.toLowerCase())
						|| captionLc.startsWith(_BoatClassAttributeName.toLowerCase())
						|| captionLc.startsWith(_LoaAttributeName.toLowerCase())) {
					continue;
				}
				/** It's a number. */
				final double cellValue = cell.getNumericCellValue();
				final String[] fields1 = captionLc.split("twaup|vmgup|twadn|vmgdn|vgmup|vgmdn");
				if (fields1.length == 2 && fields1[0].length() == 0) {
					final double windKts = Double.parseDouble(fields1[1]);
					HdgKts[] limits = windKtsToLimits.get(windKts);
					if (limits == null) {
						limits = new HdgKts[] { new HdgKts(/* dummy= */' '), new HdgKts(/* dummy= */' ') };
						windKtsToLimits.put(windKts, limits);
					}
					if (captionLc.contains("twaup")) {
						limits[0] = limits[0].replaceHdg(cellValue);
					} else if (captionLc.contains("vmgup") || captionLc.contains("vgmup")) {
						limits[0] = limits[0].replaceKts(cellValue);
					} else if (captionLc.contains("twadn")) {
						limits[1] = limits[1].replaceHdg(cellValue);
					} else {
						limits[1] = limits[1].replaceKts(cellValue);
					}
				} else {
					final String[] fields2 = captionLc.split("deg");
					if (fields2.length == 2) {
						final double windKts = Double.parseDouble(fields2[1]);
						TreeSet<HdgKts> hdgKtsS = windKtsToHdgKtsS.get(windKts);
						if (hdgKtsS == null) {
							hdgKtsS = new TreeSet<>(_HdgOnly);
							windKtsToHdgKtsS.put(windKts, hdgKtsS);
						}
						final double hdg = Double.parseDouble(fields2[0]);
						final double normalizedHdg0 = Math.abs(LatLng3.getInRange0_360(hdg));
						final double normalizedHdg;
						if (normalizedHdg0 <= 180d) {
							normalizedHdg = normalizedHdg0;
						} else {
							normalizedHdg = 360d - normalizedHdg0;
							SimCaseManager.wrn(simCase,
									String.format("\nStrange Heading: wind=%f, hdg= %f", windKts, hdg));
						}
						final HdgKts hdgKts = new HdgKts(normalizedHdg, cellValue, /* doublesAreHdgKts= */true);
						if (!hdgKtsS.add(hdgKts)) {
							MyLogger.wrn(logger,
									String.format("Duplicate hdg[%.3f] for WindSpeed[%.3f]", hdgKts.getHdg(), windKts));
						}
					}
				}
			}

			/** Remove the hdgs that are outside of the limits. */
			for (final Map.Entry<Double, TreeSet<HdgKts>> entry : windKtsToHdgKtsS.entrySet()) {
				final double windKts = entry.getKey();
				final HdgKts[] limits = windKtsToLimits.get(windKts);
				final double lowAngle = limits[0].getHdg();
				final double highAngle = limits[1].getHdg();
				final TreeSet<HdgKts> hdgKtsS = entry.getValue();
				final ArrayList<Double> deadHdgs = new ArrayList<>();
				for (final HdgKts hdgKts : entry.getValue()) {
					final double angle = hdgKts.getHdg();
					if (angle <= lowAngle || angle >= highAngle) {
						deadHdgs.add(angle);
					}
				}
				for (final double hdg : deadHdgs) {
					hdgKtsS.remove(new HdgKts(hdg, Double.NaN, /* doublesAreHdgKts= */true));
				}
				/** Add the limits. */
				hdgKtsS.add(new HdgKts(limits[0].getHdg(), limits[0].getKts(), /* doublesAreHdgKts= */true));
				hdgKtsS.add(new HdgKts(limits[1].getHdg(), limits[1].getKts(), /* doublesAreHdgKts= */true));
			}
			_speedAndOneSidedTable = fleshOutOneSidedTables(windKtsToHdgKtsS);
		}
	}

	private static SpeedAndTable[] fleshOutOneSidedTables(final TreeMap<Double, TreeSet<HdgKts>> wndKtsToHdgKtsS) {
		if (wndKtsToHdgKtsS == null) {
			return null;
		}
		final int nWndKtsS = wndKtsToHdgKtsS.size();
		final SpeedAndTable[] speedAndTables = new SpeedAndTable[nWndKtsS];
		if (nWndKtsS == 0) {
			return speedAndTables;
		}

		/** Gather all the Hdgs across all of the wndKtsS. */
		final TreeSet<Double> allHdgSet = new TreeSet<>();
		for (final TreeSet<HdgKts> hdgKtsS : wndKtsToHdgKtsS.values()) {
			for (final HdgKts hdgKts : hdgKtsS) {
				allHdgSet.add(hdgKts.getHdg());
			}
		}
		final int nAllHdgs = allHdgSet.size();
		final double[] allHdgs = new double[nAllHdgs];
		final Iterator<Double> allHdgSetIt = allHdgSet.iterator();
		for (int k = 0; k < nAllHdgs; ++k) {
			allHdgs[k] = allHdgSetIt.next();
		}
		Arrays.sort(allHdgs);

		/** Make sure each wind speed's TreeSet has all of allHdgs. */
		final Iterator<Map.Entry<Double, TreeSet<HdgKts>>> it = wndKtsToHdgKtsS.entrySet().iterator();
		for (int k0 = 0; k0 < nWndKtsS; ++k0) {
			final Map.Entry<Double, TreeSet<HdgKts>> entry = it.next();
			final double windKts = entry.getKey();
			final TreeSet<HdgKts> hdgKtsSet = entry.getValue();
			final HdgKts firstHdgKts = hdgKtsSet.first();
			final HdgKts lastHdgKts = hdgKtsSet.last();
			final double firstHdg = firstHdgKts.getHdg();
			final double lastHdg = lastHdgKts.getHdg();
			final HdgKtsPlus[] hdgKtsS = new HdgKtsPlus[nAllHdgs];
			for (int ordinal = 0; ordinal < nAllHdgs; ++ordinal) {
				final double hdg = allHdgs[ordinal];
				final double kts;
				if (hdg < firstHdg || hdg > lastHdg) {
					/**
					 * We'll make an entry for hdg, but we cannot go more upwind or dnwind than the
					 * data given.
					 */
					kts = Double.NaN;
				} else {
					final HdgKts forLookUp = new HdgKts(hdg, Double.NaN, /* doublesAreHdgKts= */true);
					final HdgKts floor = hdgKtsSet.floor(forLookUp);
					if (floor.getHdg() == hdg) {
						kts = floor.getKts();
					} else {
						final HdgKts ceiling = hdgKtsSet.ceiling(forLookUp);
						final InterpolationReturn interpolationReturn = interpolateToFindKts(floor, ceiling, hdg,
								/* allowExtrapolation= */false);
						kts = interpolationReturn._kts;
					}
				}
				hdgKtsS[ordinal] = new HdgKtsPlus(hdg, kts, /* doublesAreHdgKts= */true, /* svt= */null);
				hdgKtsS[ordinal].setOrdinal(ordinal);
			}
			speedAndTables[k0] = new SpeedAndTable(windKts, hdgKtsS);
		}
		return speedAndTables;
	}

	private static class InterpolationReturn {
		final public double _p, _kts;

		public InterpolationReturn(final double p, final double kts) {
			_p = p;
			_kts = kts;
		}
	}

	/** Returns p, newX, newY, and kts. */
	private static InterpolationReturn interpolateToFindKts(final HdgKts hdgKts0, final HdgKts hdgKts1,
			final double hdg, final boolean allowExtrapolation) {
		/**
		 * <pre>
		 * Let n0 = north0, etc., and A = tan(hdg).
		 * [n0 + p(n1-n0)]/[e0 + p(e1-e0)] = A
		 * A[e0 + p(e1-e0)] = n0 + p(n1-n0)
		 * p[(n1-n0) - A(e1-e0)] = Ae0-n0
		 * p = (Ae0-n0)/[(n1-n0) - A(e1-e0)]
		 * And:
		 * 1. 0 <= p <= 1 and
		 * 2. The resulting x and y values must have the same sign
		 *    as hdg's x and y values.
		 * </pre>
		 */
		final double hdg0 = hdgKts0.getHdg();
		final double to0 = Math.abs(LatLng3.getInRange180_180(hdg0 - hdg));
		if (to0 < 0.01) {
			return new InterpolationReturn(0d, hdgKts0.getKts());
		}
		final double hdg1 = hdgKts1.getHdg();
		final double to1 = Math.abs(LatLng3.getInRange180_180(hdg1 - hdg));
		if (to1 < 0.01) {
			return new InterpolationReturn(1d, hdgKts1.getKts());
		}
		final double e0 = hdgKts0.getEastKts();
		final double n0 = hdgKts0.getNorthKts();
		final double e1 = hdgKts1.getEastKts();
		final double n1 = hdgKts1.getNorthKts();
		final double theta = Math.toRadians(90d - hdg);
		final double A = MathX.tanX(theta);
		final double den = n1 - n0 - A * (e1 - e0);
		final double num = A * e0 - n0;
		final double p = num / den;
		if (!allowExtrapolation && ((0 > p) || (p > 1d))) {
			return null;
		}
		/** If we are not in the same quadrant, we return null. */
		final double newX = e0 + p * (e1 - e0);
		if (newX >= 0d != hdg <= 180d) {
			return null;
		}
		final double newY = n0 + p * (n1 - n0);
		if (newY >= 0d != (hdg <= 90d || hdg >= 270d)) {
			return null;
		}
		final double kts = Math.sqrt(newX * newX + newY * newY);
		return new InterpolationReturn(p, kts);
	}

	/**
	 * Can only be called after the polars have been "fleshed out," so that they all
	 * have the same set of headings.
	 */
	private HdgKtsPlus[] createOneSidedTable(final MyLogger logger, final double windKts) {
		if ((windKts < 0d)) {
			return null;
		}
		final double lowestWndKts = _speedAndOneSidedTables[0]._windKts;
		HdgKtsPlus[] table = null;
		final SpeedAndTable speedAndtable0 = _speedAndOneSidedTables[0];
		final int nTables = _speedAndOneSidedTables.length;
		final SpeedAndTable speedAndtableN = _speedAndOneSidedTables[nTables - 1];
		final double highestWndKts = speedAndtableN._windKts;
		if (windKts <= lowestWndKts) {
			/** apprntWndKts is too low; interpolate between 0 and lowest. */
			final HdgKtsPlus[] srcHdgKtsS = speedAndtable0._table;
			final int nHdgKts = srcHdgKtsS.length;
			for (int iPass = 0; iPass < 2; ++iPass) {
				int nInWedge = 0;
				for (int k = 0; k < nHdgKts; ++k) {
					final HdgKtsPlus src = srcHdgKtsS[k];
					final double srcKts = src.getKts();
					if (Double.isNaN(srcKts)) {
						continue;
					}
					if (iPass == 1) {
						/**
						 * This interpolation is between tables, not between headings within a table.
						 */
						final double p = windKts / speedAndtable0._windKts;
						final double hdg = src.getHdg();
						final StateVectorType svt = src._svt;
						table[nInWedge] = new HdgKtsPlus(hdg, srcKts * p, /* doublesAreHdgKts= */true, svt);
					}
					++nInWedge;
				}
				if (iPass == 0) {
					table = new HdgKtsPlus[nInWedge];
				}
			}
		} else if (windKts >= highestWndKts) {
			/**
			 * apprntWndKts is too high or exactly the top. In either case, return the top
			 * one.
			 */
			final HdgKtsPlus[] srcHdgKtsS = speedAndtableN._table;
			final int nHdgKts = srcHdgKtsS.length;
			for (int iPass = 0; iPass < 2; ++iPass) {
				int nInWedge = 0;
				for (int k = 0; k < nHdgKts; ++k) {
					final HdgKtsPlus src = srcHdgKtsS[k];
					final double srcKts = src.getKts();
					if (Double.isNaN(srcKts)) {
						continue;
					}
					if (iPass == 1) {
						final double hdg = src.getHdg();
						final StateVectorType svt = src._svt;
						table[nInWedge] = new HdgKtsPlus(hdg, srcKts, /* doublesAreHdgKts= */true, svt);
					}
					++nInWedge;
				}
				if (iPass == 0) {
					table = new HdgKtsPlus[nInWedge];
				}
			}
		} else {
			/**
			 * apprentWndKts is bigger than the smallest and less than the largest.
			 */
			final SpeedAndTable toLookUp = new SpeedAndTable(windKts, /* table= */null);
			final int idx = Arrays.binarySearch(_speedAndOneSidedTables, toLookUp);
			final int idxForHigh = idx >= 0 ? idx : (-idx - 1);
			final int idxForLow = idxForHigh - 1;
			final SpeedAndTable speedAndTableForLow = idxForLow >= 0 ? _speedAndOneSidedTables[idxForLow] : null;
			final SpeedAndTable speedAndTableForHigh = _speedAndOneSidedTables[idxForHigh];
			final double windKtsForLow = speedAndTableForLow._windKts;
			final double windKtsForHigh = speedAndTableForHigh._windKts;
			final double p = (windKts - windKtsForLow) / (windKtsForHigh - windKtsForLow);
			final HdgKts[] tableForLow = speedAndTableForLow._table;
			final HdgKts[] tableForHigh = speedAndTableForHigh._table;
			final int nHdgs = tableForLow.length;
			for (int iPass = 0; iPass < 2; ++iPass) {
				int nInTable = 0;
				for (int k = 0; k < nHdgs; ++k) {
					final HdgKts hdgKtsForLow = tableForLow[k];
					final double hdg = hdgKtsForLow.getHdg();
					final double ktsForLow = hdgKtsForLow.getKts();
					if (Double.isNaN(ktsForLow)) {
						continue;
					}
					final HdgKts hdgKtsForHigh = tableForHigh[k];
					final double ktsForHigh = hdgKtsForHigh.getKts();
					if (Double.isNaN(ktsForHigh)) {
						continue;
					}
					if (iPass == 1) {
						final double kts = ktsForLow + p * (ktsForHigh - ktsForLow);
						table[nInTable] = new HdgKtsPlus(hdg, kts, /* doublesAreHdgKts= */true, /* svt= */null);
					}
					++nInTable;
				}
				if (iPass == 0) {
					table = new HdgKtsPlus[nInTable];
				}
			}
		}
		return table;
	}

	public HdgKtsPlus[][] getPortAndStrbrdWedges(final MyLogger logger, final HdgKts dnCurrent, final HdgKts upWind,
			final boolean port, final boolean strbrd, final double forbiddenAngleIncrease, final double speedMultiplier,
			final boolean addInCurrent, final boolean cull) {
		final HdgKts apprntUpWind = dnCurrent.add(upWind);
		final double apprntUpWindKts = apprntUpWind.getKts();
		final double apprntUpWindHdg = apprntUpWind.getHdg();
		final HdgKts[] oneSidedTable = createOneSidedTable(logger, apprntUpWindKts);

		final int nOneSidedPolars = oneSidedTable.length;
		final double minimumAcceptableHdg = oneSidedTable[0].getHdg()
				+ ((forbiddenAngleIncrease > 0d) ? forbiddenAngleIncrease : 0d);

		final HdgKtsPlus[][] portAndStrbrdWedges = new HdgKtsPlus[][] { null, null };
		for (int side = 0; side < 2; ++side) {
			final boolean portSide = side == 0;
			if ((portSide && !port) || (!portSide && !strbrd)) {
				continue;
			}
			HdgKtsPlus[] wedgeByOrdinal = null;
			for (int iPass = 0; iPass < 2; ++iPass) {
				int nInWedge = 0;
				for (int k = 0; k < nOneSidedPolars; ++k) {
					final HdgKts hdgKts0 = oneSidedTable[k];
					final double tempHdg0 = hdgKts0.getHdg();
					if ((tempHdg0 < minimumAcceptableHdg)) {
						continue;
					}
					final double tempHdg1 = portSide ? tempHdg0 : (360d - tempHdg0);
					final double hdg0 = tempHdg1 + apprntUpWindHdg;
					final double kts0 = hdgKts0.getKts();
					if (kts0 < 0d || Double.isNaN(kts0)) {
						continue;
					}
					if (iPass == 1) {
						final double kts1 = (speedMultiplier > 0d ? speedMultiplier : 1d) * kts0;
						final StateVectorType svt;
						if (nInWedge == 0) {
							svt = portSide ? StateVectorType.PORT_TACK : StateVectorType.STRBRD_TACK;
						} else if (nInWedge < wedgeByOrdinal.length - 1) {
							svt = portSide ? StateVectorType.PORT_DIRECT : StateVectorType.STRBRD_DIRECT;
						} else {
							svt = portSide ? StateVectorType.PORT_GIBE : StateVectorType.STRBRD_GIBE;
						}
						final HdgKtsPlus hdgKts1 = new HdgKtsPlus(hdg0, kts1, /* doublesAreHdgKts= */true, svt);
						if (addInCurrent) {
							final HdgKts temp0 = hdgKts1.add(dnCurrent);
							final HdgKtsPlus temp1 = new HdgKtsPlus(temp0.getHdg(), temp0.getKts(),
									/* doublesAreHdgKts= */true, svt);
							wedgeByOrdinal[nInWedge] = temp1;
							temp1.setOrdinal(nInWedge);
						} else {
							hdgKts1.setOrdinal(nInWedge);
							wedgeByOrdinal[nInWedge] = hdgKts1;
						}
					}
					++nInWedge;
				}
				if (iPass == 0) {
					wedgeByOrdinal = new HdgKtsPlus[nInWedge];
				}
			}
			/**
			 * Cull wedge or not. Unless we added in the current, there won't be any
			 * culling.
			 */
			HdgKtsPlus[] wedgeByHdg = wedgeByOrdinal.clone();
			shrinkToSmallestWedge(Arrays.asList(wedgeByHdg));
			if (!addInCurrent || !cull) {
				portAndStrbrdWedges[side] = wedgeByHdg;
				continue;
			}
			final int nInOrigWedge = wedgeByOrdinal.length;

			/**
			 * If, in wedgeByHdg, the canonical order survived, there will be no culling.
			 */
			boolean possibleCulling = false;
			for (int k = 0; k < nInOrigWedge; ++k) {
				possibleCulling = wedgeByHdg[portSide ? k : (nInOrigWedge - 1 - k)].getOrdinal() != k;
				if (possibleCulling) {
					break;
				}
			}
			if (!possibleCulling) {
				portAndStrbrdWedges[side] = wedgeByHdg;
				continue;
			}

			/** Possible culling. Build pairs. */
			final HdgKtsPlus[][] pairs = new HdgKtsPlus[nInOrigWedge - 1][];
			for (int k = 0; k < nInOrigWedge - 1; ++k) {
				/** Form the pair so that the 1-th one is cw from the 0-th one. */
				final HdgKtsPlus hdgKts0 = wedgeByOrdinal[k];
				final HdgKtsPlus hdgKts1 = wedgeByOrdinal[k + 1];
				final double hdg0 = hdgKts0.getHdg();
				final double hdg1 = hdgKts1.getHdg();
				final double zeroToOne = LatLng3.getInRange180_180(hdg1 - hdg0);
				if (zeroToOne >= 0d) {
					pairs[k] = new HdgKtsPlus[] { hdgKts0, hdgKts1 };
				} else {
					pairs[k] = new HdgKtsPlus[] { hdgKts1, hdgKts0 };
				}
			}
			/** Sort the pairs by descending minimum kts. */
			Arrays.sort(pairs, new Comparator<HdgKtsPlus[]>() {

				@Override
				public int compare(final HdgKtsPlus[] pair0, final HdgKtsPlus[] pair1) {
					final double minKts0 = Math.min(pair0[0].getKts(), pair0[1].getKts());
					final double minKts1 = Math.min(pair1[0].getKts(), pair1[1].getKts());
					if (minKts0 != minKts1) {
						return minKts0 < minKts1 ? 1 : -1;
					}
					final int minOrdinal0 = Math.min(pair0[0].getOrdinal(), pair0[1].getOrdinal());
					final int minOrdinal1 = Math.min(pair1[0].getOrdinal(), pair1[1].getOrdinal());
					return minOrdinal0 < minOrdinal1 ? -1 : (minOrdinal0 > minOrdinal1 ? 1 : 0);
				}
			});

			/** Cull the members of wedgeByOrdinal. */
			final BitSet toCull = new BitSet(nInOrigWedge);
			NEXT_K0: for (int k0 = 0; k0 < nInOrigWedge; ++k0) {
				final HdgKtsPlus hdgKts0 = wedgeByOrdinal[k0];
				final double hdg0 = hdgKts0.getHdg();
				final double kts0 = hdgKts0.getKts();
				for (int k1 = 0; k1 < nInOrigWedge - 1; ++k1) {
					final HdgKtsPlus[] pair1 = pairs[k1];
					final double minKts1 = Math.min(pair1[0].getKts(), pair1[1].getKts());
					if (minKts1 < kts0) {
						/** The rest of the pairs are too small to cull this one. */
						break;
					}
					final HdgKtsPlus hdgKts10 = pair1[0];
					final HdgKtsPlus hdgKts11 = pair1[1];
					final double hdg10 = hdgKts10.getHdg();
					final double hdg11 = hdgKts11.getHdg();
					final double oneZeroTo0 = LatLng3.getInRange0_360(hdg0 - hdg10);
					final double oneZeroTo11 = LatLng3.getInRange0_360(hdg11 - hdg10);
					if (oneZeroTo0 < oneZeroTo11) {
						/**
						 * Sandwiched between a pair of taller ones, but the interpolation might be
						 * smaller.
						 */
						final InterpolationReturn interpolationReturn = interpolateToFindKts(hdgKts10, hdgKts11, hdg0,
								/* allowExtrapolation= */false);
						if (interpolationReturn != null && interpolationReturn._kts > kts0) {
							toCull.set(k0);
							continue NEXT_K0;
						}
					}
				}
			}
			if (toCull.cardinality() > 0) {
				final int nToCull = toCull.cardinality();
				final HdgKtsPlus[] temp = new HdgKtsPlus[nInOrigWedge - nToCull];
				for (int k0 = toCull.nextClearBit(0),
						k1 = 0; k0 < nInOrigWedge; k0 = toCull.nextClearBit(k0 + 1), ++k1) {
					temp[k1] = wedgeByOrdinal[k0];
					temp[k1].setOrdinal(k1);
				}
				wedgeByOrdinal = temp;
			}
			wedgeByHdg = wedgeByOrdinal.clone();
			shrinkToSmallestWedge(Arrays.asList(wedgeByHdg));

			final TreeSet<double[]> gapsToIgnore = new TreeSet<>(new Comparator<double[]>() {

				@Override
				public int compare(final double[] hdgGap0, final double[] hdgGap1) {
					final double min0 = Math.min(hdgGap0[0], hdgGap0[1]);
					final double min1 = Math.min(hdgGap1[0], hdgGap1[1]);
					if (min0 != min1) {
						return min0 < min1 ? -1 : 1;
					}
					final double max0 = Math.max(hdgGap0[0], hdgGap0[1]);
					final double max1 = Math.max(hdgGap1[0], hdgGap1[1]);
					if (max0 != max1) {
						return max0 < max1 ? -1 : 1;
					}
					return 0;
				}
			});
			NEXT_HDG_INC: for (double hdgInc = 0.001;; hdgInc /= 2d) {
				final int nInWedge = wedgeByHdg.length;
				for (int k0 = 0; k0 < nInWedge - 1; ++k0) {
					final HdgKtsPlus hdgKts0 = wedgeByHdg[k0];
					final HdgKtsPlus hdgKts1 = wedgeByHdg[k0 + 1];
					final int ord0 = hdgKts0.getOrdinal();
					final int ord1 = hdgKts1.getOrdinal();
					if (Math.abs(ord0 - ord1) == 1) {
						continue;
					}
					final double hdg0 = hdgKts0.getHdg();
					final double hdg1 = hdgKts1.getHdg();
					if (gapsToIgnore.contains(new double[] { hdg0, hdg1 })) {
						continue;
					}
					/** Must put in a fix. */
					final double kts0 = hdgKts0.getKts();
					final double kts1 = hdgKts1.getKts();
					final HdgKtsPlus shorter = kts0 <= kts1 ? hdgKts0 : hdgKts1;
					final HdgKtsPlus longer = shorter == hdgKts0 ? hdgKts1 : hdgKts0;
					final int ordOfShorter = shorter.getOrdinal();
					final int ordOfLonger = longer.getOrdinal();
					final double hdgOfShorter = shorter.getHdg();
					final double hdgOfLonger = longer.getHdg();
					final double ktsOfLonger = longer.getKts();
					/**
					 * Go from one ordinal to the other, and keep track of the minimum kts.
					 */
					final int minOrd = Math.min(ordOfShorter, ordOfLonger);
					final int maxOrd = Math.max(ordOfShorter, ordOfLonger);
					double minKts = shorter.getKts();
					for (int k1 = minOrd; k1 <= maxOrd; ++k1) {
						final HdgKtsPlus tweener = wedgeByOrdinal[k1];
						minKts = Math.min(tweener.getKts(), minKts);
					}
					/** Replace shorter by same-hdg, minKts. */
					final HdgKtsPlus shorterX = new HdgKtsPlus(shorter.getHdg(), minKts, /* doublesAreHdgKts= */true,
							shorter._svt);
					shorterX.setOrdinal(shorter.getOrdinal());
					wedgeByOrdinal[shorter.getOrdinal()] = shorterX;

					/** Create 2 to replace longer. */
					final double shorterToLonger = LatLng3.getInRange0_360(hdgOfLonger - hdgOfShorter);
					final boolean cwToLonger = shorterToLonger <= 180d;
					final double newHdg = LatLng3.getInRange0_360(hdgOfLonger + (cwToLonger ? hdgInc : -hdgInc));
					final HdgKtsPlus shortLonger = new HdgKtsPlus(hdgOfLonger, minKts, /* doublesAreHdgKts= */true,
							shorter._svt);
					shortLonger.setOrdinal(cwToLonger ? longer.getOrdinal() : (longer.getOrdinal() + 1));
					final HdgKtsPlus longLonger = new HdgKtsPlus(newHdg, ktsOfLonger, /* doublesAreHdgKts= */true,
							shorter._svt);
					longLonger.setOrdinal(cwToLonger ? (longer.getOrdinal() + 1) : longer.getOrdinal());
					/** Make room for the new one. */
					final HdgKtsPlus[] temp = new HdgKtsPlus[nInWedge + 1];
					for (int k1 = 0; k1 < nInWedge; ++k1) {
						final HdgKtsPlus hdgKts = wedgeByOrdinal[k1];
						if (k1 < ordOfLonger) {
							temp[k1] = hdgKts;
						} else if (k1 > ordOfLonger) {
							hdgKts.setOrdinal(hdgKts.getOrdinal() + 1);
							temp[k1 + 1] = hdgKts;
						}
					}
					temp[shortLonger.getOrdinal()] = shortLonger;
					temp[longLonger.getOrdinal()] = longLonger;
					wedgeByOrdinal = temp;
					wedgeByHdg = wedgeByOrdinal.clone();
					shrinkToSmallestWedge(Arrays.asList(wedgeByHdg));
					gapsToIgnore.add(new double[] { shorter.getHdg(), shortLonger.getHdg() });
					continue NEXT_HDG_INC;
				} /** Got through cleanly. */
				break;
			}
			portAndStrbrdWedges[side] = wedgeByHdg;
		}
		return portAndStrbrdWedges;
	}

	@SuppressWarnings("unused")
	private static boolean isStrange(final HdgKtsPlus[] wedgeByHdg) {
		final int n = wedgeByHdg.length;
		if (n < 2) {
			return false;
		}
		final double zeroTo1 = LatLng3.getInRange180_180(wedgeByHdg[1].getHdg() - wedgeByHdg[0].getHdg());
		final boolean goingUp = zeroTo1 > 0d;
		for (int k = 1; k < n - 1; ++k) {
			final double kToKP1 = LatLng3.getInRange180_180(wedgeByHdg[k + 1].getHdg() - wedgeByHdg[k].getHdg());
			final boolean thisGoingUp = kToKP1 > 0d;
			if (thisGoingUp != goingUp) {
				return true;
			}
		}
		return false;
	}

	public static HdgKtsPlus getHdgKtsForCog(final HdgKtsPlus[][] portAndStrbrdWedges, final double cog,
			final boolean preferPort, final boolean preferStrbrd) {
		HdgKtsPlus bestHdgKts = null;
		double bestKts = Double.NEGATIVE_INFINITY;
		final HdgKts toSearchFor = new HdgKts(cog, Double.NaN, /* doublesAreHdgKts= */true);
		for (int k = 0; k < 2; ++k) {
			final int side;
			if (preferStrbrd) {
				side = k == 0 ? 1 : 0;
			} else {
				side = k;
			}
			final HdgKtsPlus[] hdgKtsS = portAndStrbrdWedges[side];
			final int n = hdgKtsS.length;
			final HdgKtsPlus zeroHdgKts = hdgKtsS[0];
			final double zeroHdg = zeroHdgKts.getHdg();
			final HdgKtsPlus thisHdgKts;
			int idx = Arrays.binarySearch(hdgKtsS, toSearchFor, new Comparator<HdgKts>() {

				@Override
				public int compare(final HdgKts hdgKts0, final HdgKts hdgKts1) {
					final double to0 = LatLng3.getInRange0_360(hdgKts0.getHdg() - zeroHdg);
					final double to1 = LatLng3.getInRange0_360(hdgKts1.getHdg() - zeroHdg);
					/**
					 * If these two hdgs are very close to each other, call them equal.
					 */
					if (Math.abs(LatLng3.getInRange180_180(to1 - to0)) > 0.01) {
						/** They're different. */
						return to0 < to1 ? -1 : 1;
					}
					final double kts0 = hdgKts0.getKts();
					final double kts1 = hdgKts1.getKts();
					if (kts0 != kts1) {
						return kts0 > kts1 ? -1 : 1;
					}
					return 0;
				}
			});
			if (idx >= 0) {
				thisHdgKts = hdgKtsS[idx].clone();
				thisHdgKts.setOrdinal(idx);
			} else {
				idx = -idx - 1;
				if (idx == 0 || idx == n) {
					final HdgKtsPlus lastHdgKts = hdgKtsS[n - 1];
					final double lastHdg = lastHdgKts.getHdg();
					final double cogToZero = Math.abs(LatLng3.getInRange180_180(zeroHdg - cog));
					final double lastToCog = Math.abs(LatLng3.getInRange180_180(cog - lastHdg));
					if (cogToZero <= lastToCog) {
						thisHdgKts = zeroHdgKts.clone();
						thisHdgKts.setOrdinal(0);
						thisHdgKts.setOrdinalD(-0.1);
					} else {
						thisHdgKts = lastHdgKts.clone();
						thisHdgKts.setOrdinal(n);
						thisHdgKts.setOrdinalD(n + 0.1);
					}
				} else {
					/** Interpolate. */
					final HdgKtsPlus hdgKts0 = hdgKtsS[idx - 1];
					final HdgKtsPlus hdgKts1 = hdgKtsS[idx];
					final InterpolationReturn interpolationReturn = interpolateToFindKts(hdgKts0, hdgKts1, cog,
							/* allowExtrapolation= */false);
					final double kts = interpolationReturn == null ? Double.NaN : interpolationReturn._kts;
					final StateVectorType svt = side == 0 ? StateVectorType.PORT_DIRECT : StateVectorType.STRBRD_DIRECT;
					thisHdgKts = new HdgKtsPlus(cog, kts, /* doublesAreHdgKts= */true, svt);
					final double p = interpolationReturn._p;
					final double ordinalD0 = hdgKts0.getOrdinalD();
					final double ordinalD1 = hdgKts1.getOrdinalD();
					final double ordinalD = ordinalD0 + p * (ordinalD1 - ordinalD0);
					final int ordinal = hdgKts0.getOrdinal();
					thisHdgKts.setOrdinal(ordinal);
					thisHdgKts.setOrdinalD(ordinalD);
				}
			}
			/**
			 * If there is a preference for a side, and this one closes, use it.
			 */
			final double thisClosingKts = thisHdgKts.getClosingKts(cog);
			if ((preferPort || preferStrbrd) && thisClosingKts > 0d) {
				return thisHdgKts;
			}
			/** Update the choice. */
			if (thisClosingKts > bestKts) {
				bestKts = thisClosingKts;
				bestHdgKts = thisHdgKts;
			}
		}
		return bestHdgKts;
	}

	private static void shrinkToSmallestWedge(final List<HdgKts> hdgKtsS) {
		Collections.sort(hdgKtsS, _HdgOnly);
		final int nHdgKtsS = hdgKtsS.size();
		/** We'll start the list at the high side of the biggest gap. */
		int kStart = -1;
		double biggestGap = 0d;
		for (int k = 0; k < nHdgKtsS; ++k) {
			final int lowIdx = (k - 1 + nHdgKtsS) % nHdgKtsS;
			final double low = hdgKtsS.get(lowIdx).getHdg();
			final double high = hdgKtsS.get(k).getHdg();
			final double gap = LatLng3.getInRange0_360(high - low);
			if (gap > biggestGap) {
				kStart = k;
				biggestGap = gap;
			}
		}
		/**
		 * Because they were sorted, if the biggest gap is 0, we will take the entire
		 * wedge; it's a bunch of HdgKts that are the same hdg.
		 */
		if (kStart == -1) {
			return;
		}
		final HdgKts[] copy = new HdgKts[nHdgKtsS];
		for (int k = 0; k < nHdgKtsS; ++k) {
			copy[k] = hdgKtsS.get((kStart + k) % nHdgKtsS);
		}
		for (int k = 0; k < nHdgKtsS; ++k) {
			hdgKtsS.set(k, copy[k]);
		}
	}

	public double generateMotorKts(final Randomx r) {
		/** (0.25,0.50, 0.75) x (0.95 x 1.34 x sqrt(LOA)) */
		final double constantFactor = Math.sqrt(_lengthOverallFt) * 0.95 * 1.34;
		final double minMotorKts = 0.25 * constantFactor;
		final double cruisingMotorKts = 0.5 * constantFactor;
		final double maxMotorKts = 0.75 * constantFactor;
		if (r == null) {
			return maxMotorKts;
		}
		final double kts = r.getSplitGaussianDraw(minMotorKts, cruisingMotorKts, maxMotorKts);
		return kts;
	}

	public static TreeMap<String[], Polars> getAllResourcePolarsS(final String workBookName, final String sheetName) {
		final TreeMap<String[], Polars> allPolars = new TreeMap<>(CombinatoricTools._ByAllInOrderForComparatorArrays);
		final File dataDir = SaropsDirsTracker.getDataDir();
		final File workBookFile = new File(dataDir, workBookName);
		final String workBookFilePath = StringUtilities.getCanonicalPath(workBookFile);
		final Sheet sheet = PoiUtils.getSheet(workBookFilePath, sheetName);
		/** We stroll through the sheet looking for possible boat names. */
		/** Find the captions row. */
		final int firstRowNumber = sheet.getFirstRowNum();
		final int lastRowNumber = sheet.getLastRowNum();
		/** Find the captionRow and the boatCol within that row. */
		int captionRowNumber = 0;
		int manufacturerColNumber = -1;
		int boatClassColNumber = -1;
		RowLoop: for (captionRowNumber = firstRowNumber; captionRowNumber <= lastRowNumber; ++captionRowNumber) {
			final Row captionRow = sheet.getRow(captionRowNumber);
			final int firstCellNumber = captionRow.getFirstCellNum();
			final int lastCellNumber = captionRow.getLastCellNum();
			for (int iCell = firstCellNumber; iCell <= lastCellNumber; ++iCell) {
				final Cell cell = captionRow.getCell(iCell);
				if (cell != null && cell.getCellType() == CellType.STRING) {
					final String caption = StringUtilities.cleanUpString(cell.getStringCellValue(), " \t\n", (char) 0);
					final String captionLc = caption.toLowerCase();
					if (captionLc.startsWith(_ManufacturerAttributeName.toLowerCase())) {
						manufacturerColNumber = iCell;
					}
					if (captionLc.startsWith(_BoatClassAttributeName.toLowerCase())) {
						boatClassColNumber = iCell;
					}
					if (manufacturerColNumber >= 0 && boatClassColNumber >= 0) {
						break RowLoop;
					}
				}
			}
		}
		if (manufacturerColNumber < 0 || boatClassColNumber < 0) {
			return allPolars;
		}
		/**
		 * For any row bigger than captionRowNumber, if there's a string in boat's
		 * column and a number in every other column, we have a new Polar.
		 */
		for (int iRow = captionRowNumber + 1; iRow <= lastRowNumber; ++iRow) {
			final Row row = sheet.getRow(iRow);
			if (row == null) {
				continue;
			}
			final Cell cell1 = row.getCell(manufacturerColNumber);
			if (cell1 == null || (cell1.getCellType() != CellType.STRING)) {
				continue;
			}
			final String manufacturer = cell1.getStringCellValue();
			//
			final Cell cell2 = row.getCell(boatClassColNumber);
			if (cell2 == null || (cell1.getCellType() != CellType.STRING)) {
				continue;
			}
			final String boatClass = cell2.getStringCellValue();
			final Polars polars = new Polars(/* simCase= */null, workBookName, sheetName, manufacturer, boatClass);
			if (polars._manufacturer != null && polars._boatClass != null) {
				allPolars.put(new String[] { manufacturer, boatClass }, polars);
			}
		}
		return allPolars;
	}

	public void addToElement(final LsFormatter lsFormatter, final Element sailorTypeElement) {
		if (_workBookName != null && _sheetName != null && _manufacturer != null && _boatClass != null) {
			sailorTypeElement.setAttribute(_PolarsWorkBookAttributeName, _workBookName);
			sailorTypeElement.setAttribute(_PolarsSheetAttributeName, _sheetName);
			sailorTypeElement.setAttribute(_ManufacturerAttributeName, _manufacturer);
			sailorTypeElement.setAttribute(_BoatClassAttributeName, _boatClass);
		}
		/**
		 * If it did not come from a spreadsheet, we need LOA in the sailorTypeElement.
		 * We put it there regardless and also dump the polars.
		 */
		sailorTypeElement.setAttribute(_LoaAttributeName, LsFormatter.StandardFormat(_lengthOverallFt) + " ft");
		final int nWindKtsS = _speedAndOneSidedTables.length;
		for (int k0 = 0; k0 < nWindKtsS; ++k0) {
			final SpeedAndTable speedAndTable = _speedAndOneSidedTables[k0];
			final double windKts = speedAndTable._windKts;
			final Element speedElement = lsFormatter.newChild(sailorTypeElement, _WindSpeedTag);
			speedElement.setAttribute(_WindSpeedAttributeName, LsFormatter.StandardFormat(windKts) + " kts");
			final HdgKts[] hdgKtsS = speedAndTable._table;
			final int nHdgKtsS = hdgKtsS.length;
			for (int k = 0; k < nHdgKtsS; ++k) {
				final HdgKts hdgKts = hdgKtsS[k];
				final Element angleElement = lsFormatter.newChild(speedElement, _RelativeAngleTag);
				angleElement.setAttribute(_RelativeAngleAttributeName,
						LsFormatter.StandardFormat(hdgKts.getHdg()) + " degs");
				angleElement.setAttribute(_BoatSpeedAttributeName,
						LsFormatter.StandardFormat(hdgKts.getKts()) + " kts");
			}
		}
	}
}
