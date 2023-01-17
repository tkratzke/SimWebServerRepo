package com.skagit.sarops.tracker;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.PrintStream;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.TimeUtilities;
import com.skagit.util.poiUtils.ExcelDumper;

public class ParticlesFileExcelDumper {

	final public ExcelDumper _excelDumper;
	final public PrintStream _printStream;
	final public ParticlesFile _particlesFile;

	public ParticlesFileExcelDumper(final String particlesFilePath, final boolean dumpExcel, final boolean dumpCsv) {
		/** Preliminary nuisance constants. */
		final SimCaseManager.SimCase simCase = null;
		final boolean includeSeconds = false;
		/** Read in the ParticlesFile and get the model. */
		ParticlesFile particlesFile = null;
		try {
			particlesFile = new ParticlesFile(simCase, particlesFilePath);
		} catch (final RuntimeException e) {
			_particlesFile = null;
			_printStream = null;
			_excelDumper = null;
			return;
		}
		_particlesFile = particlesFile;
		final Model model = _particlesFile.getModel();
		/** Create the File and ExcelDumper. */
		final int lastDot = particlesFilePath.lastIndexOf('.');
		if (dumpCsv) {
			PrintStream printStream = null;
			final String csvPath = particlesFilePath.substring(0, lastDot) + ".csv";
			final File csvFile = new File(csvPath);
			try {
				printStream = new PrintStream(csvFile);
			} catch (final FileNotFoundException e) {
			}
			printStream.printf("\nDTG,Particle ID,LAT,LON");
			_printStream = printStream;
		} else {
			_printStream = null;
		}
		final String excelName;
		if (dumpExcel) {
			final String excelPath = particlesFilePath.substring(0, lastDot) + ExcelDumper._ListSuffix;
			final File excelFile = new File(excelPath);
			excelName = excelFile.getName();
			final File outputDir = excelFile.getParentFile();
			final boolean clearOutDir = true;
			_excelDumper = new ExcelDumper(outputDir, clearOutDir);
		} else {
			excelName = null;
			_excelDumper = null;
		}
		/** Get the Workbook, build the sheet, and create the header row. */
		int iRow = 0;
		int j = 0;
		final Sheet sheet;
		if (_excelDumper != null) {
			final XSSFWorkbook workBook = model.getSortiesWorkbook();
			sheet = workBook.createSheet("Tracks");
			final Row headerRow = sheet.createRow(iRow++);
			headerRow.createCell(j++).setCellValue("DTG");
			headerRow.createCell(j++).setCellValue("Particle ID");
			headerRow.createCell(j++).setCellValue("LAT");
			headerRow.createCell(j++).setCellValue("LNG");
		} else {
			sheet = null;
		}
		if (_printStream != null) {
			_printStream.printf("\nDTG, Particle ID,LAT,LON");
		}
		/** Add the data rows. */
		final int nScenarii = model.getNScenarii();
		final int nParticlesPerScenario = model.getNParticlesPerScenario();
		final long[] refSecsS = _particlesFile.getRefSecsS();
		final int nRefSecsS = refSecsS.length;
		for (int k = 0; k < nRefSecsS; ++k) {
			final long refSecs = refSecsS[k];
			for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
				for (int iParticle = 0; iParticle < nParticlesPerScenario; ++iParticle) {
					final ParticleIndexes prtclIndxs = ParticleIndexes.getStandardOne(model, iScenario, iParticle);
					final long birthRefSecs = _particlesFile.getBirthRefSecs(prtclIndxs);
					if (birthRefSecs <= refSecs) {
						final int overallIndex = prtclIndxs.getOverallIndex();
						final double[] latLngPair = _particlesFile.getLatLngPair(k, prtclIndxs);
						final double lat = latLngPair[0];
						final double lng = latLngPair[1];
						final String dtg = TimeUtilities.formatTime(refSecs, includeSeconds);
						j = 0;
						if (_excelDumper != null) {
							final Row row = sheet.createRow(iRow++);
							row.createCell(j++).setCellValue(dtg);
							row.createCell(j++).setCellValue(overallIndex);
							row.createCell(j++).setCellValue(lat);
							row.createCell(j++).setCellValue(lng);
						}
						if (_printStream != null) {
							_printStream.printf("\n%s,%d,%.5f,%.5f", dtg, overallIndex, lat, lng);
						}
					}
				}
			}
		}
		_excelDumper.close(excelName);
	}

	public static void main(final String[] args) {
		final File dirFile = new File(args[0]);
		final boolean dumpExcel = false;
		final boolean dumpCsv = true;
		if (args.length == 1 && dirFile.isDirectory()) {
			final File[] fileList = dirFile.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(final File file, final String name) {
					return name.toLowerCase().endsWith(".nc");
				}
			});
			if (fileList != null) {
				for (final File f : fileList) {
					@SuppressWarnings("unused")
					final ParticlesFileExcelDumper particlesFileExcelDumper = new ParticlesFileExcelDumper(
							f.getAbsolutePath(), dumpExcel, dumpCsv);
				}
			}
		} else {
			final File f = new File(dirFile, args[1]);
			@SuppressWarnings("unused")
			final ParticlesFileExcelDumper particlesFileExcelDumper = new ParticlesFileExcelDumper(f.getAbsolutePath(),
					dumpExcel, dumpCsv);
		}
	}

}
