package com.skagit.buildSimLand;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import com.skagit.util.StaticUtilities;
import com.skagit.util.myLogger.MyLogger;

class Phase6Worker {

	final private BuildSimLand _buildSimLand;
	final private MyLogger _logger;
	final private File _homeDir;
	final private File _logDir;
	final File _jarFile;
	final File _newLandJarDir;
	final File _etopoLandJarDir;

	public Phase6Worker(final BuildSimLand buildSimLand) {
		_buildSimLand = buildSimLand;
		_logger = _buildSimLand._logger;
		_homeDir = _buildSimLand._homeDir;
		_logDir = new File(_homeDir, "Phase6Log");
		final String restOfName = _homeDir.getName().replaceAll("-", "");
		final String jarFileName = String.format("%s-%s.jar",
				BuildSimLand._Phase6ResultFileStart, restOfName);
		_jarFile = new File(_homeDir, jarFileName);
		_newLandJarDir = new File(_homeDir, BuildSimLand._Phase6LandJarDirName);
		if (BuildSimLand._Phase6EtopoLandJarDirName != null) {
			_etopoLandJarDir = new File(BuildSimLand._StableDir,
					BuildSimLand._Phase6EtopoLandJarDirName);
		} else {
			_etopoLandJarDir = null;
		}
	}

	void doPhase6() {
		if (_buildSimLand.doThisPhase(BuildSimLand.Phase.PHASE6)) {
			StaticUtilities.deleteAnyFile(_jarFile);
			StaticUtilities.clearDirectoryWithFilter(_logDir,
					/* filenameFilter= */null);
		}
		_logger.resetAppenders(_logDir, //
				/* dbgCoreName= */null, //
				/* outCoreName= */"Phase6.out", //
				/* wrnCoreName= */"Phase6.wrn", //
				/* errCoreName= */"Phase6.err", //
				"txt", /* append= */true);
		try {
			createJarFile(_jarFile, _newLandJarDir, _etopoLandJarDir);
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	private static void createJarFile(final File jarFile,
			final File... sources) throws IOException {
		final Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION,
				"1.0");
		final int nSources = sources.length;
		try (final JarOutputStream jos =
				new JarOutputStream(new FileOutputStream(jarFile), manifest)) {
			for (int k = 0; k < nSources; ++k) {
				final File source = sources[k];
				try {
					add(source, jos);
				} catch (final IOException e) {
					throw (e);
				}
			}
		} catch (final IOException e) {
		}
	}

	final private static byte[] _Buffer = new byte[1024 * 1024];

	private static void add(final File source, final JarOutputStream jos)
			throws IOException {
		if (source == null) {
			return;
		}
		String sourcePath = source.getPath().replace("\\", "/");
		if (source.isDirectory()) {
			if (!sourcePath.isEmpty()) {
				if (!sourcePath.endsWith("/")) {
					sourcePath += "/";
				}
			}
			for (final File nestedFile : source.listFiles()) {
				add(nestedFile, jos);
			}
			return;
		}
		/** Non-directory here. */
		final String entryName =
				sourcePath.substring(sourcePath.indexOf("com"));
		final JarEntry entry = new JarEntry(entryName);
		entry.setTime(source.lastModified());
		jos.putNextEntry(entry);
		try (BufferedInputStream in =
				new BufferedInputStream(new FileInputStream(source))) {
			while (true) {
				final int count = in.read(_Buffer);
				if (count == -1) {
					break;
				}
				jos.write(_Buffer, 0, count);
			}
			jos.closeEntry();
		} catch (final IOException e) {
		}
	}
}
