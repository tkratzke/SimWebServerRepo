package com.skagit.sarops.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import com.skagit.util.DirsTracker;
import com.skagit.util.StringUtilities;
import com.skagit.util.TimeUtilities;

public class CppToJavaTracer {

	final private static String _CppToJavaTraceDirName =
			SimGlobalStrings.getCppToJavaTraceDirName();
	final private File _f;

	public CppToJavaTracer(final String fileNamePrefixIn) {

		final File magicDir;
		if (SimGlobalStrings.getUseUserDirForCppToJavaTrace()) {
			/**
			 * We got here by setting -DDEBUG_JAVA=true as a system parameter.
			 * This is a signal to look for magicDir not in java.home's parent,
			 * but rather in the current working directory.
			 */
			magicDir = new File(DirsTracker.getUserDir(), _CppToJavaTraceDirName);
		} else {
			/**
			 * For a SAROPS install, we want the jre's parent to contain magicDir.
			 */
			final String lowDirPath = StringUtilities
					.getSystemProperty("java.home", /* useSpaceProxy= */false);
			magicDir = new File(new File(lowDirPath).getParent(),
					_CppToJavaTraceDirName);
		}
		if (!magicDir.isDirectory() || !magicDir.canWrite()) {
			_f = null;
			return;
		}
		final String nowString = TimeUtilities.formatNowForFileName();
		File f = null;
		/** CppToJava tracing is turned on. */
		final String fileNamePrefix =
				fileNamePrefixIn == null ? "" : fileNamePrefixIn;
		for (int k = 5;; ++k) {
			final String fileName2 =
					String.format("%s-%s.%02d.txt", fileNamePrefix, nowString, k);
			final File f1 = new File(magicDir, fileName2);
			if (f1.exists()) {
				continue;
			}
			final boolean append = true;
			final boolean autoFlush = true;
			try (final PrintStream ps =
					new PrintStream(new FileOutputStream(f1, append), autoFlush)) {
				ps.printf("CppToJavaTracing Turned on to %s.", f1.getPath());
				f = f1;
				break;
			} catch (final IOException e) {
			}
		}
		_f = f;
	}

	public void writeTrace(final String m) {
		if (_f == null || m == null) {
			return;
		}
		try (final PrintStream ps =
				new PrintStream(new FileOutputStream(_f, /* append= */true),
						/* autoFlush= */true)) {
			ps.print(m);
		} catch (final IOException e) {
		}
	}

	public boolean isActive() {
		return _f != null && _f.canWrite();
	}
}
