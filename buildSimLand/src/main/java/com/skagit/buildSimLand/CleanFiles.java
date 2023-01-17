package com.skagit.buildSimLand;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import com.google.common.io.Files;
import com.skagit.util.DirsTracker;
import com.skagit.util.StaticUtilities;

public class CleanFiles {
	final private static File _UserDir = DirsTracker.getUserDir();

	public static void main(final String[] args) {
		cleanFiles(_UserDir);
	}

	private static void cleanFiles(final File f0) {
		final File[] toClean = f0.listFiles(new FileFilter() {

			@Override
			public boolean accept(final File f) {
				if (f.isDirectory() || f.getName().toLowerCase().contains("-adj.kml")) {
					return true;
				}
				return false;
			}
		});
		for (final File f : toClean) {
			final String fName = f.getName();
			final String oldPath = f.getAbsolutePath();
			if (f.isFile()) {
				final String newName =
						f.getName().substring(0, fName.length() - 8) + ".kml";
				final File newFile = new File(f0, newName);
				try {
					Files.move(f, newFile);
				} catch (final IOException e) {
					e.printStackTrace();
					System.exit(22);
				}
				final String newPath = newFile.getAbsolutePath();
				System.out.printf("\n%s to %s", oldPath, newPath);
				continue;
			}
			if (fName.toLowerCase().endsWith("-dispdir")) {
				System.out.printf("\ndeleting %s", oldPath);
				StaticUtilities.clearDirectory(f);
				f.delete();
			} else {
				cleanFiles(f);
			}
		}
	}
}
