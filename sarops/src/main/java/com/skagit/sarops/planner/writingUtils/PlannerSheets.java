package com.skagit.sarops.planner.writingUtils;

import java.io.File;
import java.util.Iterator;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFName;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.planner.posFunction.pvValueArrayPlus.PvValueArrayPlus;
import com.skagit.sarops.tracker.Tracker;
import com.skagit.util.poiUtils.ExcelDumper;
import com.skagit.util.poiUtils.PoiUtils;

public class PlannerSheets {

	public enum ColumnType1 {
		PARTICLE(Tracker.SaropsClmnType.PARTICLE._shortString), //
		OT(Tracker.SaropsClmnType.OT._shortString), //
		ALPHA_PRIOR(Tracker.SaropsClmnType.ALPHA_PRIOR._shortString), //
		PF_ALPHA(Tracker.SaropsClmnType.PF_ALPHA._shortString), //
		BRAVO_PRIOR(Tracker.SaropsClmnType.BRAVO_PRIOR._shortString), //
		PF_BRAVO("Pf-Bravo"), //
		PASS_BOTH("PassBoth"), //
		PASS_LA("PassLA"), //
		SLCTD("Slctd"), //
		LANDED1("Landed1", true), //
		LANDED2("Landed2", true); //

		final public String _shortString;
		final public boolean _afterPttrnVbls;

		final private static ColumnType1[] _ColumnType1s;
		final private static int _LastColNumBeforePttrnVbls;

		static {
			_ColumnType1s = values();
			int last = -1;
			final int nRegular = _ColumnType1s.length;
			for (int k = 0; k < nRegular; ++k) {
				final ColumnType1 type = _ColumnType1s[k];
				if (!type._afterPttrnVbls) {
					last = Math.max(last, type.ordinal());
				}
			}
			_LastColNumBeforePttrnVbls = last;
		}

		private ColumnType1(final String shortString, final boolean afterPttrnVbls) {
			_shortString = shortString;
			_afterPttrnVbls = afterPttrnVbls;
		}

		private ColumnType1(final String shortString) {
			this(shortString, false);
		}

		private int getClmnNmbr(final int nNonNullPttrnVbls) {
			final int returnValue = ordinal() + (_afterPttrnVbls ? 2 : 0) * nNonNullPttrnVbls;
			return returnValue;
		}

		private static int getPvPfColNum(final int nNonNullPttrnVbls, final int iPv) {
			return _LastColNumBeforePttrnVbls + 1 + iPv;
		}

		private static int getPvSlctdColNum(final int nNonNullPttrnVbls, final int iPv) {
			return getPvPfColNum(nNonNullPttrnVbls, iPv) + nNonNullPttrnVbls;
		}

		private static int getNCols(final int nNonNullPttrnVbls) {
			return _ColumnType1s.length + 2 * nNonNullPttrnVbls;
		}
	}

	final PlannerReportsData _plannerReportsData;
	final XSSFCellStyle _centerStyle;
	final XSSFCellStyle _perCentStyle;
	final Sheet _plannerParticleSheet;
	Sheet _dashboardSheet = null;
	Sheet _externalSheet = null;
	Sheet _evalSheet = null;
	final boolean _useSeparateSheets;
	final boolean _useSheetScope;
	final String _suffix;
	final String[] _pvPfNameNames;
	final String[] _pvSlctdNameNames;
	final String _passesLa2NameName;
	final String _bravoPriorNameName;
	final String _initWtNameName;
	final String _pfAlphaNameName;
	final String _pfCumNameName;
	final String _slctdNameName;
	final String _pfBravoNameName;
	final String _otNameName;
	final String _landed2NameName;
	final XSSFWorkbook _workBook;

	public PlannerSheets(final Planner planner, final PlannerReportsData plannerReportsData) {
		/** Boilerplate. */
		_plannerReportsData = plannerReportsData;
		/** Unpack the data. */
		final PvValueArrayPlus pvValueArrayPlus = _plannerReportsData._pvValueArrayPlus;
		final PvValue[] nonNullPvValues = pvValueArrayPlus.getNonNullPvValues();
		final int nNonNullPttrnVbls = nonNullPvValues.length;
		final ParticleData[] _particleDatas = _plannerReportsData._allParticleDatas;
		final int nParticleDatas = _particleDatas.length;
		_useSeparateSheets = true;
		/**
		 * If we're using separate sheets, we must use global scope for our names.
		 */
		_useSheetScope = !_useSeparateSheets;

		_workBook = new XSSFWorkbook();
		_centerStyle = _workBook.createCellStyle();
		_centerStyle.setAlignment(HorizontalAlignment.CENTER);
		_perCentStyle = _workBook.createCellStyle();
		_perCentStyle.setDataFormat(_workBook.createDataFormat().getFormat("0.000%"));

		/** Build a planner sheet with a unique sheet name. */
		{
			Sheet particleSheetX = null;
			String suffixX = "";
			for (;;) {
				final String particleSheetNameX = "PlannerPrtcls" + suffixX;
				if (_workBook.getSheet(particleSheetNameX) == null) {
					_suffix = suffixX;
					particleSheetX = _workBook.createSheet(particleSheetNameX);
					break;
				}
				suffixX += "_";
			}
			_plannerParticleSheet = particleSheetX;
		}

		/** Create the non-Pv specific column headings first. */
		final int nCols = ColumnType1.getNCols(nNonNullPttrnVbls);
		final String[] colNumToHdg = new String[nCols];
		final Row headingsRow = PoiUtils.getOrCreateRow(_plannerParticleSheet, 0);
		final ColumnType1[] types = ColumnType1._ColumnType1s;
		final int nRegular = types.length;
		for (int k = 0; k < nRegular; ++k) {
			final ColumnType1 type = types[k];
			final int j = type.getClmnNmbr(nNonNullPttrnVbls);
			final Cell cell = headingsRow.createCell(j);
			cell.setCellValue(type._shortString);
			cell.setCellStyle(_centerStyle);
			colNumToHdg[j] = cell.getStringCellValue();
		}
		/** Squeeze in the headings for the Pv. */
		for (int k = 0; k < nNonNullPttrnVbls; ++k) {
			final PvValue pvValue = nonNullPvValues[k];
			final PatternVariable pv = pvValue.getPv();
			final int jA = ColumnType1.getPvPfColNum(nNonNullPttrnVbls, k);
			final Cell cellA = headingsRow.createCell(jA);
			cellA.setCellValue(String.format("Pf-%s", pv.getId()));
			cellA.setCellStyle(_centerStyle);
			colNumToHdg[jA] = cellA.getStringCellValue();
			final int jB = ColumnType1.getPvSlctdColNum(nNonNullPttrnVbls, k);
			final Cell cellB = headingsRow.createCell(jB);
			cellB.setCellValue(String.format("Slctd-%s", pv.getId()));
			cellB.setCellStyle(_centerStyle);
			colNumToHdg[jB] = cellB.getStringCellValue();
		}

		/** Create the lines. */
		int lastIData = headingsRow.getRowNum();
		final int firstIData = lastIData + 1;
		for (int k = 0; k < nParticleDatas; ++k) {
			final ParticleData particleData = _particleDatas[k];
			final Row dataRow = PoiUtils.getOrCreateRow(_plannerParticleSheet, lastIData + 1);
			lastIData = dataRow.getRowNum();

			final int j1 = ColumnType1.PARTICLE.getClmnNmbr(nNonNullPttrnVbls);
			final Cell cell1 = dataRow.createCell(j1);
			cell1.setCellValue(particleData._prtclIndxs.getString());

			final int j2 = ColumnType1.OT.getClmnNmbr(nNonNullPttrnVbls);
			final Cell cell2 = dataRow.createCell(j2);
			cell2.setCellValue(particleData._ot);

			final int j3 = ColumnType1.ALPHA_PRIOR.getClmnNmbr(nNonNullPttrnVbls);
			final Cell cell3 = dataRow.createCell(j3);
			cell3.setCellValue(particleData._initWt);

			final int j5 = ColumnType1.BRAVO_PRIOR.getClmnNmbr(nNonNullPttrnVbls);
			final Cell cell5 = dataRow.createCell(j5);
			cell5.setCellValue(particleData._bravoPrior);

			final int j6 = ColumnType1.PF_BRAVO.getClmnNmbr(nNonNullPttrnVbls);
			final Cell cell6 = dataRow.createCell(j6);
			cell6.setCellValue(particleData.getPfBravo());

			/** PttrnVbls go here. */
			final int nPFails = particleData._pFails.length;
			for (int kPFail = 0; kPFail < nPFails; ++kPFail) {
				final int jB1 = ColumnType1.getPvPfColNum(nNonNullPttrnVbls, kPFail);
				final Cell cellB1 = dataRow.createCell(jB1);
				cellB1.setCellValue(particleData._pFails[kPFail]);
				final int jB2 = ColumnType1.getPvSlctdColNum(nNonNullPttrnVbls, kPFail);
				final Cell cellB2 = dataRow.createCell(jB2);
				cellB2.setCellValue(particleData._slctds[kPFail] ? 1 : 0);
			}

			final int jA1 = ColumnType1.SLCTD.getClmnNmbr(nNonNullPttrnVbls);
			final Cell cellA1 = dataRow.createCell(jA1);
			cellA1.setCellValue(particleData.isSlctd() ? 1 : 0);

			final int jA2 = ColumnType1.PASS_LA.getClmnNmbr(nNonNullPttrnVbls);
			final Cell cellA2 = dataRow.createCell(jA2);
			cellA2.setCellValue(particleData._passLa ? 1 : 0);

			final int jA3 = ColumnType1.PASS_BOTH.getClmnNmbr(nNonNullPttrnVbls);
			final Cell cellA3 = dataRow.createCell(jA3);
			cellA3.setCellValue(particleData.passBoth() ? 1 : 0);

			final int jA4 = ColumnType1.LANDED1.getClmnNmbr(nNonNullPttrnVbls);
			final Cell cellA4 = dataRow.createCell(jA4);
			cellA4.setCellValue(particleData._adrift ? 0 : 1);

			final int jA6 = ColumnType1.PF_ALPHA.getClmnNmbr(nNonNullPttrnVbls);
			final Cell cellA6 = dataRow.createCell(jA6);
			cellA6.setCellValue(particleData._pfAlpha);

			final int jA7 = ColumnType1.LANDED2.getClmnNmbr(nNonNullPttrnVbls);
			final Cell cellA7 = dataRow.createCell(jA7);
			cellA7.setCellValue(particleData._adrift2 ? 0 : 1);
		}

		final String particleSheetName = _plannerParticleSheet.getSheetName();
		final int plannerSheetIndex = _workBook.getSheetIndex(_plannerParticleSheet);
		final boolean absRow = true;
		final boolean absCol = true;

		/**
		 * Create Named Ranges and associate them with the strings in clmnNameSqs.
		 */
		final String[] clmnNameSqs = new String[nCols];
		for (int j = 0; j < nCols; ++j) {
			final String cellName1 = ExcelDumper.getCellRef(firstIData, absRow, j, absCol);
			final String cellName2 = ExcelDumper.getCellRef(lastIData, absRow, j, absCol);
			final String rangeString = String.format("%s!%s:%s", particleSheetName, cellName1, cellName2);
			final String colHeading = colNumToHdg[j];
			final XSSFName clmnName = _workBook.createName();
			if (_useSheetScope) {
				clmnName.setSheetIndex(plannerSheetIndex);
			}
			final String excelNameX = ExcelDumper.createExcelName(colHeading) + _suffix;
			for (int r = 0;; ++r) {
				final String excelName;
				if (r == 0) {
					excelName = excelNameX;
				} else {
					excelName = String.format("%s%d", excelNameX, r);
				}
				try {
					clmnName.setNameName(excelName);
				} catch (final IllegalArgumentException e) {
					continue;
				}
				/** Successfully found a name. Make it refer to rangeString. */
				clmnName.setRefersToFormula(rangeString);
				final String nameSq = clmnName.getNameName();
				clmnNameSqs[j] = nameSq;
				break;
			}
		}

		/** Gather the references to the columns. */
		final int jBravoPrior = ColumnType1.BRAVO_PRIOR.getClmnNmbr(nNonNullPttrnVbls);
		_bravoPriorNameName = clmnNameSqs[jBravoPrior];

		final int jPassesLa2 = ColumnType1.PASS_LA.getClmnNmbr(nNonNullPttrnVbls);
		_passesLa2NameName = clmnNameSqs[jPassesLa2];

		final int jInitWt = ColumnType1.ALPHA_PRIOR.getClmnNmbr(nNonNullPttrnVbls);
		_initWtNameName = clmnNameSqs[jInitWt];

		final int jPfAlpha = ColumnType1.PF_ALPHA.getClmnNmbr(nNonNullPttrnVbls);
		_pfAlphaNameName = clmnNameSqs[jPfAlpha];

		final int jSlctd = ColumnType1.SLCTD.getClmnNmbr(nNonNullPttrnVbls);
		_slctdNameName = clmnNameSqs[jSlctd];

		final int jForPfBravo = ColumnType1.PF_BRAVO.getClmnNmbr(nNonNullPttrnVbls);
		_pfBravoNameName = clmnNameSqs[jForPfBravo];

		_pfCumNameName = String.format("(%s*%s)", _pfAlphaNameName, _pfBravoNameName);

		final int jForOt = ColumnType1.OT.getClmnNmbr(nNonNullPttrnVbls);
		_otNameName = clmnNameSqs[jForOt];

		final int jForLanded2 = ColumnType1.LANDED2.getClmnNmbr(nNonNullPttrnVbls);
		_landed2NameName = clmnNameSqs[jForLanded2];

		_pvPfNameNames = new String[nNonNullPttrnVbls];
		_pvSlctdNameNames = new String[nNonNullPttrnVbls];
		for (int kPv = 0; kPv < nNonNullPttrnVbls; ++kPv) {
			final int j2A = ColumnType1.getPvPfColNum(nNonNullPttrnVbls, kPv);
			_pvPfNameNames[kPv] = clmnNameSqs[j2A];
			final int j2B = ColumnType1.getPvSlctdColNum(nNonNullPttrnVbls, kPv);
			_pvSlctdNameNames[kPv] = clmnNameSqs[j2B];
		}
	}

	public void buildDashboardSheet() {
		/** Unpack the data. */
		final PvValueArrayPlus pvValueArrayPlus = _plannerReportsData._pvValueArrayPlus;
		final PvValue[] nonNullPttrnVbls = pvValueArrayPlus.getNonNullPvValues();
		final int nNonNullPttrnVbls = nonNullPttrnVbls.length;
		final String[] pvStrings = _plannerReportsData._pvStrings;
		final int[] ots = _plannerReportsData._ots;
		final String[] otStrings = _plannerReportsData._otStrings;

		final Sheet dashboardSheet;
		final int iForTopLeft;
		if (_useSeparateSheets) {
			_dashboardSheet = dashboardSheet = _workBook.createSheet("Dashboard" + _suffix);
			iForTopLeft = 0;
		} else {
			dashboardSheet = _plannerParticleSheet;
			iForTopLeft = _plannerParticleSheet.getLastRowNum() + 2;
			_dashboardSheet = null;
		}

		final int jForTopLeft = 0;
		final Row topLeftHdgRow = PoiUtils.getOrCreateRow(dashboardSheet, iForTopLeft);
		final Cell topLeftCell = topLeftHdgRow.createCell(jForTopLeft);
		topLeftCell.setBlank();
		final Cell selObjsHdgCell = topLeftHdgRow.createCell(jForTopLeft + 1);
		selObjsHdgCell.setCellValue("Sel Obj(s); POS");
		selObjsHdgCell.setCellStyle(_centerStyle);
		final int nOts = ots.length;
		for (int kPv = 0; kPv < nNonNullPttrnVbls; ++kPv) {
			final String pvPfName = _pvPfNameNames[kPv];
			final String pvSlctdName = _pvSlctdNameNames[kPv];
			final int iForPv = iForTopLeft + 1 + kPv;
			final Row pvRow = PoiUtils.getOrCreateRow(dashboardSheet, iForPv);
			final Cell pvCell = pvRow.createCell(jForTopLeft);
			final String pvString = pvStrings[kPv];
			pvCell.setCellValue(pvString);
			final Cell cell = pvRow.createCell(jForTopLeft + 1);
			cell.setCellStyle(_perCentStyle);
			// cell.setCellType(CellType.FORMULA);
			final String formulaString = String.format("SUMPRODUCT(%s,%s,%s,1-%s)/" //
					+ "SUMPRODUCT(%s,%s,%S)", //
					_passesLa2NameName, pvSlctdName, _bravoPriorNameName, pvPfName, //
					_passesLa2NameName, pvSlctdName, _bravoPriorNameName);
			cell.setCellFormula(formulaString);
		}

		/** Bottom Left of Planner Dashboard. */
		final int iForBottomLeft = iForTopLeft + nNonNullPttrnVbls + 2;
		final int jForBottomLeft = jForTopLeft;
		final Row bottomLeftHdgRow = PoiUtils.getOrCreateRow(dashboardSheet, iForBottomLeft);
		final Row bottomLeftDataRow = PoiUtils.getOrCreateRow(dashboardSheet, iForBottomLeft + 1);

		/** This Plan. */
		final int jForThisPlan = jForBottomLeft;
		final Cell thisPlanHdgCell = bottomLeftHdgRow.createCell(jForThisPlan);
		thisPlanHdgCell.setCellValue("This Plan");
		thisPlanHdgCell.setCellStyle(_centerStyle);
		final Cell thisPlanDatumCell = bottomLeftDataRow.createCell(jForThisPlan);
		thisPlanDatumCell.setCellStyle(_perCentStyle);
		// thisPlanDatumCell.setCellType(CellType.FORMULA);
		final String thisPlanFormulaString = String.format("SUMPRODUCT(%s,%s,%s,1-%s)/" //
				+ "SUMPRODUCT(%s,%s,%s)", //
				_passesLa2NameName, _slctdNameName, _bravoPriorNameName, _pfBravoNameName, //
				_passesLa2NameName, _slctdNameName, _bravoPriorNameName);
		thisPlanDatumCell.setCellFormula(thisPlanFormulaString);

		/** Net Gain. */
		final int jForNetGain = jForThisPlan + 1;
		final Cell netGainHdgCell = bottomLeftHdgRow.createCell(jForNetGain);
		netGainHdgCell.setCellValue("Net Gain");
		netGainHdgCell.setCellStyle(_centerStyle);
		final Cell netGainDatumCell = bottomLeftDataRow.createCell(jForNetGain);
		netGainDatumCell.setCellStyle(_perCentStyle);
		// netGainDatumCell.setCellType(CellType.FORMULA);
		final String netGainFormulaString = //
				String.format("SUMPRODUCT(%s,%s-%s)/SUM(%s)", //
						_initWtNameName, _pfAlphaNameName, _pfCumNameName, //
						_initWtNameName);
		netGainDatumCell.setCellFormula(netGainFormulaString);

		/** Compute the maximum column number for the left tables. */
		int maxLeftColNum = -1;
		for (final Iterator<Row> it1 = dashboardSheet.rowIterator(); it1.hasNext();) {
			final Row r = it1.next();
			if (r != null) {
				maxLeftColNum = Math.max(maxLeftColNum, r.getLastCellNum());
			}
		}

		final int iForTopRight = iForTopLeft;
		final int jForTopRight = maxLeftColNum + 2;

		/** Top Right of Planner Dashboard. */
		final Row topRightHdgRow = PoiUtils.getOrCreateRow(dashboardSheet, iForTopRight);
		final Cell topRightHdgCell = topRightHdgRow.createCell(jForTopRight);
		topRightHdgCell.setBlank();
		final Cell condPosHdgCell = topRightHdgRow.createCell(jForTopRight + 1);
		condPosHdgCell.setCellValue("Cond POS");
		condPosHdgCell.setCellStyle(_centerStyle);
		final Cell objProbHdgCell = topRightHdgRow.createCell(jForTopRight + 2);
		objProbHdgCell.setCellValue("Obj Prob");
		objProbHdgCell.setCellStyle(_centerStyle);
		final Cell jointPosHdgCell = topRightHdgRow.createCell(jForTopRight + 3);
		jointPosHdgCell.setCellValue("Joint POS");
		jointPosHdgCell.setCellStyle(_centerStyle);
		final int j0 = topRightHdgCell.getColumnIndex();
		final int j1 = condPosHdgCell.getColumnIndex();
		final int j2 = objProbHdgCell.getColumnIndex();
		final int j3 = jointPosHdgCell.getColumnIndex();
		for (int kPv = 0; kPv < nNonNullPttrnVbls; ++kPv) {
			final int iForPv = iForTopRight + 1 + kPv * (1 + nOts);
			final Row pvRow = PoiUtils.getOrCreateRow(dashboardSheet, iForPv);
			final Cell pvCell = pvRow.createCell(j0);
			final String pvString = pvStrings[kPv];
			pvCell.setCellValue(pvString);
			pvCell.setCellStyle(_centerStyle);
			/** PV Row. */
			/** Cond POS; nothing. */
			final Cell pvCondPosCell = pvRow.createCell(j1);
			pvCondPosCell.setBlank();
			/** Obj Prob. */
			final Cell pvObjProbCell = pvRow.createCell(j2);
			pvObjProbCell.setCellStyle(_perCentStyle);
			final CellRangeAddress cellRangeAddress2 = new CellRangeAddress(iForPv, iForPv, j2, j2);
			// pvObjProbCell.setCellType(CellType.FORMULA);
			final String formulaString2 = String.format("SUMPRODUCT(%s,%s)/" //
					+ "SUMPRODUCT(%s,%s)", //
					_passesLa2NameName, _bravoPriorNameName, //
					_passesLa2NameName, _bravoPriorNameName);
			dashboardSheet.setArrayFormula(formulaString2, cellRangeAddress2);
			/** Joint POS. */
			final Cell pvJointPosCell = pvRow.createCell(j3);
			pvJointPosCell.setCellStyle(_perCentStyle);
			final CellRangeAddress cellRangeAddress3 = new CellRangeAddress(iForPv, iForPv, j3, j3);
			// pvJointPosCell.setCellType(CellType.FORMULA);
			pvJointPosCell.setCellStyle(_perCentStyle);
			final String pvPfNameSq = _pvPfNameNames[kPv];
			final String formulaString3 = String.format("SUMPRODUCT(%s,%s,1-%s)/" //
					+ "SUMPRODUCT(%s,%s)", //
					_passesLa2NameName, _bravoPriorNameName, pvPfNameSq, //
					_passesLa2NameName, _bravoPriorNameName);
			dashboardSheet.setArrayFormula(formulaString3, cellRangeAddress3);
			for (int k = 0; k < nOts; ++k) {
				final int ot = ots[k];
				final int iForOt = iForPv + 1 + k;
				final Row otRow = PoiUtils.getOrCreateRow(dashboardSheet, iForOt);
				final Cell otCell = otRow.createCell(j0);
				otCell.setCellValue(otStrings[k]);
				otCell.setCellStyle(_centerStyle);
				/** Cond POS. */
				final Cell otCondPosCell = otRow.createCell(j1);
				otCondPosCell.setCellStyle(_perCentStyle);
				final CellRangeAddress cellRangeAddress1a = new CellRangeAddress(iForOt, iForOt, j1, j1);
				// otCondPosCell.setCellType(CellType.FORMULA);
				final String formulaString1a = String.format("SUMPRODUCT(%s,IF(%s=%d,1,0),%s,1-%s)/" //
						+ "SUMPRODUCT(%s,IF(%s=%d,1,0),%s)", //
						_passesLa2NameName, _otNameName, ot, _bravoPriorNameName, pvPfNameSq, //
						_passesLa2NameName, _otNameName, ot, _bravoPriorNameName);
				dashboardSheet.setArrayFormula(formulaString1a, cellRangeAddress1a);
				/** Obj Prob. */
				final Cell otObjProbCell = otRow.createCell(j2);
				otObjProbCell.setCellStyle(_perCentStyle);
				final CellRangeAddress cellRangeAddress2a = new CellRangeAddress(iForOt, iForOt, j2, j2);
				// otObjProbCell.setCellType(CellType.FORMULA);
				final String formulaString2a = String.format("SUMPRODUCT(%s,IF(%s=%d,1,0),%s)/" //
						+ "SUMPRODUCT(%s,%s)", //
						_passesLa2NameName, _otNameName, ot, _bravoPriorNameName, //
						_passesLa2NameName, _bravoPriorNameName);
				dashboardSheet.setArrayFormula(formulaString2a, cellRangeAddress2a);
				/** Joint POS. */
				final Cell otJointPosCell = otRow.createCell(j3);
				otJointPosCell.setCellStyle(_perCentStyle);
				final CellRangeAddress cellRangeAddress3a = new CellRangeAddress(iForOt, iForOt, j3, j3);
				// otJointPosCell.setCellType(CellType.FORMULA);
				final String formulaString3a = String.format(
						"SUMPRODUCT(%s,IF(%s=%d,1,0),%s,1-%s)/" + "SUMPRODUCT(%s,%s)", //
						_passesLa2NameName, _otNameName, ot, _bravoPriorNameName, pvPfNameSq, //
						_passesLa2NameName, _bravoPriorNameName);
				dashboardSheet.setArrayFormula(formulaString3a, cellRangeAddress3a);
			}
		}
		/** Top Right, "All" row. */
		final int iForAllPttrnVbls = iForTopRight + 1 + nNonNullPttrnVbls * (1 + nOts);
		final Row allPttrnVblsRow = PoiUtils.getOrCreateRow(dashboardSheet, iForAllPttrnVbls);
		final Cell allPttrnVblsCell = allPttrnVblsRow.createCell(j0);
		allPttrnVblsCell.setCellValue("All PttrnVbls");
		allPttrnVblsCell.setCellStyle(_centerStyle);
		/** Cond POS; nothing. */
		final Cell allPttrnVblsCondPosCell = allPttrnVblsRow.createCell(j1);
		allPttrnVblsCondPosCell.setBlank();
		/** Obj Prob; nothing. */
		final Cell allPttrnVblsObjProbCell = allPttrnVblsRow.createCell(j2);
		allPttrnVblsObjProbCell.setBlank();
		/** Joint POS. */
		final Cell pvJointPosCell = allPttrnVblsRow.createCell(j3);
		pvJointPosCell.setCellStyle(_perCentStyle);
		final CellRangeAddress cellRangeAddress3 = new CellRangeAddress(iForAllPttrnVbls, iForAllPttrnVbls, j3, j3);
		// pvJointPosCell.setCellType(CellType.FORMULA);
		pvJointPosCell.setCellStyle(_perCentStyle);
		final String formulaString3 = String.format("SUMPRODUCT(%s,%s,1-%s)/SUMPRODUCT(%s,%s)", //
				_passesLa2NameName, _bravoPriorNameName, _pfBravoNameName, //
				_passesLa2NameName, _bravoPriorNameName);
		dashboardSheet.setArrayFormula(formulaString3, cellRangeAddress3);

		/** Bottom Right of Planner Dashboard. */
		final int iForBottomRight = iForTopRight + 1 + nNonNullPttrnVbls * (1 + nOts) + 2;
		final Row bottomRightHdgRow = PoiUtils.getOrCreateRow(dashboardSheet, iForBottomRight);
		final int jForBottomRight = jForTopRight;
		final Cell bottomRightHdgCell = bottomRightHdgRow.createCell(jForBottomRight);
		bottomRightHdgCell.setBlank();
		final Cell brCondPosHdgCell = bottomRightHdgRow.createCell(jForBottomRight + 1);
		brCondPosHdgCell.setCellValue("Cond POS");
		brCondPosHdgCell.setCellStyle(_centerStyle);
		final Cell brObjProbHdgCell = bottomRightHdgRow.createCell(jForBottomRight + 2);
		brObjProbHdgCell.setCellValue("Obj Prob");
		brObjProbHdgCell.setCellStyle(_centerStyle);
		final Cell brJointPosHdgCell = bottomRightHdgRow.createCell(jForBottomRight + 3);
		brJointPosHdgCell.setCellValue("Joint POS");
		brJointPosHdgCell.setCellStyle(_centerStyle);
		final Cell brRmngProbHdgCell = bottomRightHdgRow.createCell(jForBottomRight + 4);
		brRmngProbHdgCell.setCellValue("Remaining Probability");
		brRmngProbHdgCell.setCellStyle(_centerStyle);
		final int brJ0 = topRightHdgCell.getColumnIndex();
		final int brJ1 = condPosHdgCell.getColumnIndex();
		final int brJ2 = objProbHdgCell.getColumnIndex();
		final int brJ3 = jointPosHdgCell.getColumnIndex();
		final int brJ4 = brRmngProbHdgCell.getColumnIndex();
		for (int k = 0; k < nOts; ++k) {
			final int ot = ots[k];
			final int iForOt = iForBottomRight + 1 + k;
			final Row brOtRow = PoiUtils.getOrCreateRow(dashboardSheet, iForOt);
			final Cell brOtCell = brOtRow.createCell(brJ0);
			final String brOtString = otStrings[k];
			brOtCell.setCellValue(brOtString);
			/** Cond POS. */
			final Cell brOtCondPosCell = brOtRow.createCell(brJ1);
			brOtCondPosCell.setCellStyle(_perCentStyle);
			final CellRangeAddress brCellRangeAddress1 = new CellRangeAddress(iForOt, iForOt, brJ1, brJ1);
			// brOtCondPosCell.setCellType(CellType.FORMULA);
			final String brOtFormulaString = String.format("SUMPRODUCT(%s,IF(%s=%d,1,0),%s,1-%s)/" //
					+ "SUMPRODUCT(%s,IF(%s=%d,1,0),%s)", //
					_passesLa2NameName, _otNameName, ot, _bravoPriorNameName, _pfBravoNameName, //
					_passesLa2NameName, _otNameName, ot, _bravoPriorNameName);
			dashboardSheet.setArrayFormula(brOtFormulaString, brCellRangeAddress1);
			/** Obj Prob. */
			final Cell brOtObjProbCell = brOtRow.createCell(brJ2);
			brOtObjProbCell.setCellStyle(_perCentStyle);
			final CellRangeAddress brCellRangeAddress2 = new CellRangeAddress(iForOt, iForOt, brJ2, brJ2);
			// brOtObjProbCell.setCellType(CellType.FORMULA);
			final String brFormulaString2 = String.format(//
					"SUMPRODUCT(%s,IF(%s=%d,1,0),%s)/" //
							+ "SUMPRODUCT(%s,%s)", //
					_passesLa2NameName, _otNameName, ot, _bravoPriorNameName, //
					_passesLa2NameName, _bravoPriorNameName);
			dashboardSheet.setArrayFormula(brFormulaString2, brCellRangeAddress2);
			/** Joint POS. */
			final Cell brOtJointPosCell = brOtRow.createCell(brJ3);
			brOtJointPosCell.setCellStyle(_perCentStyle);
			final CellRangeAddress brCellRangeAddress3a = new CellRangeAddress(iForOt, iForOt, brJ3, brJ3);
			// brOtJointPosCell.setCellType(CellType.FORMULA);
			final String brFormulaString3a = String.format(
					"SUMPRODUCT(%s,IF(%s=%d,1,0),%s,1-%s)/" + "SUMPRODUCT(%s,%s)", //
					_passesLa2NameName, _otNameName, ot, _bravoPriorNameName, _pfBravoNameName, //
					_passesLa2NameName, _bravoPriorNameName);
			dashboardSheet.setArrayFormula(brFormulaString3a, brCellRangeAddress3a);
			/** Remaining Prob. */
			final Cell brOtRemProbCell = brOtRow.createCell(brJ4);
			brOtRemProbCell.setCellStyle(_perCentStyle);
			final CellRangeAddress brCellRangeAddress4 = new CellRangeAddress(iForOt, iForOt, brJ4, brJ4);
			// brOtRemProbCell.setCellType(CellType.FORMULA);
			final String brFormulaString4 = String.format("SUMPRODUCT(%s,IF(%s=%d,1,0),%s,%s)/" //
					+ "SUMPRODUCT(%s,%s)", //
					_passesLa2NameName, _otNameName, ot, _bravoPriorNameName, _pfBravoNameName, //
					_passesLa2NameName, _bravoPriorNameName);
			dashboardSheet.setArrayFormula(brFormulaString4, brCellRangeAddress4);
		}
		/** Bottom Right, All Row. */
		final int iForAll = iForBottomRight + 1 + nOts;
		final Row brAllRow = PoiUtils.getOrCreateRow(dashboardSheet, iForAll);
		final Cell brAllCell = brAllRow.createCell(brJ0);
		brAllCell.setCellStyle(_perCentStyle);
		brAllCell.setCellValue("All");
		/** Cond POS. */
		final Cell allCondPosCell = brAllRow.createCell(brJ1);
		allCondPosCell.setBlank();
		/** The remaining 3 are sums. */
		final boolean brAbsRow = false;
		final boolean brAbsCol = false;
		for (int k = 2; k <= 4; ++k) {
			final int j = k == 2 ? brJ2 : (k == 3 ? brJ3 : brJ4);
			final Cell brCell = brAllRow.createCell(j);
			brCell.setCellStyle(_perCentStyle);
			final String cellName1 = ExcelDumper.getCellRef(iForBottomRight + 1, brAbsRow, j, brAbsCol);
			final String cellName2 = ExcelDumper.getCellRef(iForAll - 1, brAbsRow, j, brAbsCol);
			final String rangeString = String.format("%s:%s", cellName1, cellName2);
			final String brFormulaString = String.format("SUM(%s)", rangeString);
			// brCell.setCellType(CellType.FORMULA);
			brCell.setCellFormula(brFormulaString);
		}

		/** Optn Score. */
		final int jForOptnScore = brJ4 + 1;
		final Row brPvsRow = PoiUtils.getOrCreateRow(dashboardSheet, iForAll - 1);
		final Cell optnScoreHdgCell = brPvsRow.createCell(jForOptnScore);
		optnScoreHdgCell.setCellValue("Opt'n Score");
		optnScoreHdgCell.setCellStyle(_centerStyle);
		final Cell optnScoreDatumCell = brAllRow.createCell(jForOptnScore);
		optnScoreDatumCell.setCellStyle(_perCentStyle);
		// optnScoreDatumCell.setCellType(CellType.FORMULA);
		final String optnScoreFormulaString = String.format("SUMPRODUCT(%s,%s,%s,1-%s)/" //
				+ "SUMPRODUCT(%s,%s,%s)", //
				_passesLa2NameName, _slctdNameName, _bravoPriorNameName, _pfBravoNameName, //
				_passesLa2NameName, _slctdNameName, _bravoPriorNameName);
		optnScoreDatumCell.setCellFormula(optnScoreFormulaString);
	}

	public enum ColumnType2 {
		OT("Search Object"), //
		NUM_ADRFT("Number Adrift"), //
		NUM_LAND("Number on Land"), //
		COND_POS("Cond POS"), //
		OBJ_PROB("Obj Prob"), //
		JOINT_POS("Joint POS"), //
		REM_PROB("Rem Prob"); //

		final public String _shortString;

		final private static ColumnType2[] _ColumnType2s = values();

		private ColumnType2(final String shortString) {
			_shortString = shortString;
		}

	}

	public void buildExternalSheet() {
		/** Unpack the data. */
		final Sheet externalSheet;
		final int iForTopLeft;
		if (_useSeparateSheets) {
			_externalSheet = externalSheet = _workBook.createSheet("External" + _suffix);
			iForTopLeft = 0;
		} else {
			externalSheet = _plannerParticleSheet;
			iForTopLeft = _plannerParticleSheet.getLastRowNum() + 2;
			_externalSheet = null;
		}

		final int jForTopLeft = 0;

		/** Hdg lines. */
		final Row hdgRow = PoiUtils.getOrCreateRow(externalSheet, iForTopLeft);
		for (final ColumnType2 type : ColumnType2._ColumnType2s) {
			final int k = type.ordinal();
			final Cell hdgCell = hdgRow.createCell(jForTopLeft + k);
			hdgCell.setCellValue(type._shortString);
			hdgCell.setCellStyle(_centerStyle);
		}

		/** Data lines. */
		final int[] _ots = _plannerReportsData._ots;
		final String[] _otStrings = _plannerReportsData._otStrings;
		final int nOts = _ots.length;
		int iForTotal = hdgRow.getRowNum() + 1;
		for (int k = 0; k < nOts; ++k) {
			final int ot = _ots[k];
			final String otString = _otStrings[k];
			final int i = hdgRow.getRowNum() + 1 + k;
			final Row otRow = PoiUtils.getOrCreateRow(externalSheet, i);
			iForTotal = otRow.getRowNum() + 1;
			String formulaString = null;
			for (final ColumnType2 type : ColumnType2._ColumnType2s) {
				final int kk = type.ordinal();
				final int j = jForTopLeft + kk;
				final Cell cell = otRow.createCell(j);
				final CellRangeAddress cellRangeAddress = new CellRangeAddress(i, i, j, j);
				switch (type) {
				case OT:
					cell.setCellValue(otString);
					break;
				case NUM_ADRFT:
					// cell.setCellType(CellType.FORMULA);
					formulaString = String.format("SUMPRODUCT(IF(%s=%d,1,0),1-%s)", //
							_otNameName, ot, _landed2NameName);
					externalSheet.setArrayFormula(formulaString, cellRangeAddress);
					break;
				case NUM_LAND:
					// cell.setCellType(CellType.FORMULA);
					formulaString = String.format("SUMPRODUCT(IF(%s=%d,1,0),%s)", //
							_otNameName, ot, _landed2NameName);
					externalSheet.setArrayFormula(formulaString, cellRangeAddress);
					break;
				case COND_POS:
					// cell.setCellType(CellType.FORMULA);
					cell.setCellStyle(_perCentStyle);
					formulaString = String.format("SUMPRODUCT(IF(%s=%d,1,0),%s,1-%s)/" + "SUMPRODUCT(IF(%s=%d,1,0),%s)", //
							_otNameName, ot, _initWtNameName, _pfCumNameName, //
							_otNameName, ot, _initWtNameName);
					externalSheet.setArrayFormula(formulaString, cellRangeAddress);
					break;
				case OBJ_PROB:
					// cell.setCellType(CellType.FORMULA);
					cell.setCellStyle(_perCentStyle);
					formulaString = String.format("SUMPRODUCT(IF(%s=%d,1,0),%s)/SUMPRODUCT(%s)", //
							_otNameName, ot, _initWtNameName, //
							_initWtNameName);
					externalSheet.setArrayFormula(formulaString, cellRangeAddress);
					break;
				case JOINT_POS:
					// cell.setCellType(CellType.FORMULA);
					cell.setCellStyle(_perCentStyle);
					formulaString = String.format("SUMPRODUCT(IF(%s=%d,1,0),%s,1-%s)/" + "SUMPRODUCT(%s)", //
							_otNameName, ot, _initWtNameName, _pfCumNameName, //
							_initWtNameName);
					externalSheet.setArrayFormula(formulaString, cellRangeAddress);
					break;
				case REM_PROB:
					// cell.setCellType(CellType.FORMULA);
					cell.setCellStyle(_perCentStyle);
					formulaString = String.format("SUMPRODUCT(IF(%s=%d,1,0),%s,%s)/" + "SUMPRODUCT(%s)", //
							_otNameName, ot, _initWtNameName, _pfCumNameName, //
							_initWtNameName);
					externalSheet.setArrayFormula(formulaString, cellRangeAddress);
					break;
				default:
					break;
				}
			}
		}
		final Row totalsRow = PoiUtils.getOrCreateRow(externalSheet, iForTotal);
		final boolean absRow = false;
		final boolean absCol = false;

		for (final ColumnType2 type : ColumnType2._ColumnType2s) {
			final int kk = type.ordinal();
			final int j = jForTopLeft + kk;
			final Cell cell = totalsRow.createCell(j);
			switch (type) {
			case OT:
				cell.setCellValue("Totals:");
				cell.setCellStyle(_centerStyle);
				break;
			case COND_POS:
				cell.setBlank();
				break;
			default:
				final String cellName1 = ExcelDumper.getCellRef(hdgRow.getRowNum() + 1, absRow, j, absCol);
				final String cellName2 = ExcelDumper.getCellRef(totalsRow.getRowNum() - 1, absRow, j, absCol);
				final String rangeString = String.format("%s:%s", cellName1, cellName2);
				final String formulaString = String.format("SUM(%s)", rangeString);
				if (type != ColumnType2.NUM_ADRFT && type != ColumnType2.NUM_LAND) {
					cell.setCellStyle(_perCentStyle);
				}
				// cell.setCellType(CellType.FORMULA);
				cell.setCellFormula(formulaString);
				break;
			}
		}
	}

	public enum ColumnType3 {
		PTTRN_VBL("Name"), //
		JOINT_POS("POS"); //

		final public String _shortString;

		final private static ColumnType3[] _ColumnType3s = values();

		private ColumnType3(final String shortString) {
			_shortString = shortString;
		}

	}

	public void buildEvalSheet() {
		/** Unpack the data. */
		final Sheet evalSheet;
		final int iForTopLeft;
		if (_useSeparateSheets) {
			_evalSheet = evalSheet = _workBook.createSheet("Eval" + _suffix);
			iForTopLeft = 0;
		} else {
			evalSheet = _plannerParticleSheet;
			iForTopLeft = _plannerParticleSheet.getLastRowNum() + 2;
			_evalSheet = null;
		}

		final int jForTopLeft = 0;

		/** Hdg lines. */
		final Row hdgRow = PoiUtils.getOrCreateRow(evalSheet, iForTopLeft);
		for (final ColumnType3 type : ColumnType3._ColumnType3s) {
			final int k = type.ordinal();
			final Cell hdgCell = hdgRow.createCell(jForTopLeft + k);
			hdgCell.setCellValue(type._shortString);
			hdgCell.setCellStyle(_centerStyle);
		}

		/** Data lines. */
		final PvValueArrayPlus pvValueArrayPlus = _plannerReportsData._pvValueArrayPlus;
		final PvValue[] nonNullPttrnVbls = pvValueArrayPlus.getNonNullPvValues();
		final int nNonNullPttrnVbls = nonNullPttrnVbls.length;
		final String[] pvStrings = _plannerReportsData._pvStrings;

		int iForMisc = hdgRow.getRowNum() + 1;
		int jForMisc = -1;
		for (int kPv = 0; kPv < nNonNullPttrnVbls; ++kPv) {
			final String pvString = pvStrings[kPv];
			final int i = hdgRow.getRowNum() + 1 + kPv;
			final Row pvRow = PoiUtils.getOrCreateRow(evalSheet, i);
			iForMisc = pvRow.getRowNum() + 1;
			String formulaString = null;
			for (final ColumnType3 type : ColumnType3._ColumnType3s) {
				final int kk = type.ordinal();
				final int j = jForTopLeft + kk;
				final Cell cell = pvRow.createCell(j);
				jForMisc = cell.getColumnIndex() - 1;
				final CellRangeAddress cellRangeAddress = new CellRangeAddress(i, i, j, j);
				switch (type) {
				case PTTRN_VBL:
					cell.setCellValue(pvString);
					break;
				default:
					// cell.setCellType(CellType.FORMULA);
					cell.setCellStyle(_perCentStyle);
					final String pfString = _pvPfNameNames[kPv];
					formulaString = String.format("SUMPRODUCT(%s,1-%s)/SUM(%s)", //
							_bravoPriorNameName, pfString, _bravoPriorNameName);
					evalSheet.setArrayFormula(formulaString, cellRangeAddress);
					break;
				}
			}
		}
		/** Plan Total and cum. */
		final int labelClmnNum = jForMisc + 1;
		final int dataClmnNum = labelClmnNum + 1;
		final int iForPlanTotal = iForMisc;
		final Row planTotalRow = PoiUtils.getOrCreateRow(evalSheet, iForPlanTotal);
		final Cell planTotalLabelCell = planTotalRow.createCell(labelClmnNum);
		planTotalLabelCell.setCellValue("Plan Total:");
		final Cell planTotalCell = planTotalRow.createCell(dataClmnNum);
		// planTotalCell.setCellType(CellType.FORMULA);
		planTotalCell.setCellStyle(_perCentStyle);
		final CellRangeAddress planTotalCellRangeAddress = new CellRangeAddress(iForPlanTotal, iForPlanTotal,
				dataClmnNum, dataClmnNum);
		final String planTotalFormulaString = String.format("SUMPRODUCT(%s,1-%s)/SUM(%s)", //
				_bravoPriorNameName, _pfBravoNameName, _bravoPriorNameName);
		evalSheet.setArrayFormula(planTotalFormulaString, planTotalCellRangeAddress);
		/** Cum. */
		final int iForCum = planTotalRow.getRowNum() + 1;
		final Row cumRow = PoiUtils.getOrCreateRow(evalSheet, iForCum);
		final Cell cumLabelCell = cumRow.createCell(labelClmnNum);
		cumLabelCell.setCellValue("Cumulative:");
		final Cell cumCell = cumRow.createCell(dataClmnNum);
		// cumCell.setCellType(CellType.FORMULA);
		cumCell.setCellStyle(_perCentStyle);
		final CellRangeAddress cumCellRangeAddress = new CellRangeAddress(iForCum, iForCum, dataClmnNum, dataClmnNum);
		final String cumFormulaString = String.format("SUMPRODUCT(%s,1-%s)/SUM(%s)", //
				_initWtNameName, _pfCumNameName, _initWtNameName);
		evalSheet.setArrayFormula(cumFormulaString, cumCellRangeAddress);
		/**
		 * Autofitting will destroy a column that has just formulae unless we do
		 * something like the following.
		 */
		if (hdgRow.getCell(dataClmnNum) == null) {
			hdgRow.createCell(dataClmnNum).setCellValue("          ");
		}
	}

	public void closeAndWrite(final File f) {
		ExcelDumper.closeAndWriteWorkBook(_workBook, f);
	}

}
