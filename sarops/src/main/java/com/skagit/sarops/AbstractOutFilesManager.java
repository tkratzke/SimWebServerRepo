package com.skagit.sarops;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.simCaseManager.MainRunner;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.AbstractToImpl;
import com.skagit.util.ElementIterator;
import com.skagit.util.LsFormatter;
import com.skagit.util.StaticUtilities;
import com.skagit.util.myLogger.MyLogger;

abstract public class AbstractOutFilesManager {
	final private static AbstractOutFilesManager _Singleton;
	static {
		final AbstractOutFilesManager singleton = (AbstractOutFilesManager) AbstractToImpl
				.GetImplObject(StaticUtilities.getMyClass());
		if (singleton != null) {
			_Singleton = singleton;
		} else {
			_Singleton = new AbstractOutFilesManager() {
				@Override
				protected String getParticlesFilePath(final String modelFilePath,
						final String particlesFilePathFromXml) {
					return Functions.addSuffix(particlesFilePathFromXml, ".nc");
				}

				@Override
				protected String getEnvFilePath(final String modelFilePath, final String envFilePathFromXml) {
					return Functions.addSuffix(envFilePathFromXml, ".nc");
				}
			};
		}
	}

	public static String GetEnvFilePath(final String xmlFilePath, final String envFilePathFromXml) {
		return _Singleton.getEnvFilePath(xmlFilePath, envFilePathFromXml);
	}

	public static File GetEngineFilesDir(final SimCaseManager.SimCase simCase, final String xmlFilePath,
			final String subDirName) {
		try {
			final File xmlFile = new File(xmlFilePath);
			if (!xmlFile.isFile()) {
				throw new FileNotFoundException(xmlFilePath);
			}
			final String particlesFilePath = GetParticlesFilePathFromXml(xmlFile.getAbsolutePath());
			final File outDir;
			/** If there is no ParticlesFile, we use xmlFile's directory. */
			if (particlesFilePath == null) {
				outDir = new File(xmlFile.getParentFile(), "Out");
			} else {
				outDir = new File(particlesFilePath).getParentFile();
			}
			final File engineFilesDirFile = new File(outDir, Model._EngineFilesName);
			final File subDirFile = new File(engineFilesDirFile, subDirName);
			return StaticUtilities.makeDirectory(subDirFile);
		} catch (final Exception e) {
			MainRunner.HandleFatal(simCase, new RuntimeException(
					String.format("No EngineFilesDir xmlFilePath[%s] subDirName[%s]", xmlFilePath, subDirName)));
		}
		return null;
	}

	/** _Singleton will provide the rest. */
	abstract protected String getParticlesFilePath(String xmlFilePath, String particlesFilePathFromXml);

	abstract protected String getEnvFilePath(final String xmlFilePath, String envFilePathFromXml);

	protected static class Functions {
		protected static String addSuffix(final String filePath, final String suffix) {
			if (suffix == null) {
				return filePath;
			}
			final String suffixWithDot = suffix.indexOf('.') == 0 ? suffix : ("." + suffix);
			if (filePath == null) {
				return null;
			}
			final File origFile = new File(filePath);
			final File parentFile = origFile.getParentFile();
			final String fileName = origFile.getName();
			if (fileName.toLowerCase().endsWith(suffixWithDot.toLowerCase())) {
				return filePath;
			}
			/**
			 * We would like to strip the fileName's suffix (if there), but because of the
			 * increasing use of dots in fileNames (and filePaths), we dare not. We'll
			 * simply have to live with results such as fileNameCore.xml.nc.
			 */
			final File newFile = new File(parentFile, fileName + suffixWithDot);
			try {
				return newFile.getCanonicalPath();
			} catch (final IOException e) {
			}
			return null;
		}
	}

	public static String GetParticlesFilePath(final String xmlFilePath, final String particlesFilePathFromXml) {
		return _Singleton.getParticlesFilePath(xmlFilePath, particlesFilePathFromXml);
	}

	public static String GetParticlesFilePathFromXml(final String xmlFilePath) {
		if (xmlFilePath == null) {
			return null;
		}
		final File f = new File(xmlFilePath);
		if (!f.isFile()) {
			return null;
		}
		try (final FileInputStream fis = new FileInputStream(f)) {
			final Document d = LsFormatter._DocumentBuilder.parse(fis);
			final Element root = d.getDocumentElement();
			final String rootTagLc = root.getTagName().toLowerCase();
			final String particlesFilePathFromXml;
			if (rootTagLc.equals("plan") || rootTagLc.equals("eval")) {
				particlesFilePathFromXml = root.getAttribute("particleFile");
			} else if (rootTagLc.equals("sim")) {
				final Element requestElement = ElementIterator.getChildIgnoreCase(root, "REQUEST");
				final Element outputElement = ElementIterator.getChildIgnoreCase(requestElement, "OUTPUT");
				particlesFilePathFromXml = outputElement.getAttribute("file");
			} else {
				return null;
			}
			return GetParticlesFilePath(xmlFilePath, particlesFilePathFromXml);
		} catch (final Exception e) {
			MyLogger.wrn(/* _logger= */null, "Error getting ParticlesFilePathFromXml");
		}
		return null;
	}

}
