package com.skagit.buildSimLand.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.io.FilenameUtils;

import com.skagit.util.Constants;
import com.skagit.util.MathX;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.TimeUtilities;
import com.skagit.util.colorUtils.ColorUtils;
import com.skagit.util.geometry.gcaSequence.NonLoop;
import com.skagit.util.geometry.geoMtx.GeoMtx;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.gshhs.GshhsReader;
import com.skagit.util.kmlObjects.kmlFeature.KmlDocument;
import com.skagit.util.kmlObjects.kmlFeature.KmlFolder;
import com.skagit.util.kmlObjects.kmlFeature.KmlPlacemark;
import com.skagit.util.kmlObjects.kmlGeometry.KmlLineString;
import com.skagit.util.kmlObjects.kmlGeometry.KmlPolygon;
import com.skagit.util.kmlObjects.kmlStyleSelector.KmlStyleMap;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;

public class CompareGshhsFiles {
	final private static double _RToM = Constants._NmiToM / MathX._NmiToR;
	final private static double _ToleranceM = 10d;

	public static void compareGshhsFiles(final MyLogger logger,
			final File redBFile, final File limeBFile, final File kmlFile) {
		if (redBFile == null || !redBFile.isFile() || limeBFile == null ||
				!limeBFile.isFile()) {
			return;
		}
		ArrayList<double[][]> inputRedCoordsPairs =
				GshhsReader.getAllCoordsPairs(logger, redBFile);
		TreeSet<double[]> redNotLimeSet =
				new TreeSet<>(NumericalRoutines._ByAllInOrder1);
		final int nRedLoops = inputRedCoordsPairs.size();
		for (int k0 = 0; k0 < nRedLoops; ++k0) {
			final double[][] coordsPairsOfLoop = inputRedCoordsPairs.get(k0);
			final int nGcas = coordsPairsOfLoop.length;
			for (int k1 = 0; k1 < nGcas; ++k1) {
				final double[] latLngArray0 = coordsPairsOfLoop[k1];
				final double[] latLngArray1 = coordsPairsOfLoop[(k1 + 1) % nGcas];
				final double[] edge = new double[] { latLngArray0[0],
						latLngArray0[1], latLngArray1[0], latLngArray1[1] };
				redNotLimeSet.add(edge);
			}
		}
		inputRedCoordsPairs = null;
		ArrayList<double[][]> inputLimeCoordsPairs =
				GshhsReader.getAllCoordsPairs(logger, limeBFile);
		ArrayList<double[]> limeNotRedList = new ArrayList<>();
		final int nLimeLoops = inputLimeCoordsPairs.size();
		for (int k0 = 0; k0 < nLimeLoops; ++k0) {
			final double[][] coordsPairsOfLoop = inputLimeCoordsPairs.get(k0);
			final int nGcas = coordsPairsOfLoop.length;
			for (int k1 = 0; k1 < nGcas; ++k1) {
				final double[] latLngArray0 = coordsPairsOfLoop[k1];
				final double[] latLngArray1 = coordsPairsOfLoop[(k1 + 1) % nGcas];
				final double[] edge = new double[] { latLngArray0[0],
						latLngArray0[1], latLngArray1[0], latLngArray1[1] };
				if (redNotLimeSet.contains(edge)) {
					redNotLimeSet.remove(edge);
				} else {
					limeNotRedList.add(edge);
				}
			}
		}
		inputLimeCoordsPairs = null;
		final int nRedNotLime = redNotLimeSet.size();
		final double[][] redNotLimeEdges =
				redNotLimeSet.toArray(new double[nRedNotLime][]);
		redNotLimeSet = null;
		final ArrayList<LatLng3[]> redPaths = new ArrayList<>();
		final ArrayList<LatLng3[]> redLoops = new ArrayList<>();
		createLoopsAndPaths(logger, redNotLimeEdges, redLoops, redPaths);

		final int nLimeNotRed = limeNotRedList.size();
		final double[][] limeNotRedEdges =
				limeNotRedList.toArray(new double[nLimeNotRed][]);
		limeNotRedList = null;
		final ArrayList<LatLng3[]> limePaths = new ArrayList<>();
		final ArrayList<LatLng3[]> limeLoops = new ArrayList<>();
		createLoopsAndPaths(logger, limeNotRedEdges, limeLoops, limePaths);

		/**
		 * Convert the path arrays to arrays of singles, and create the array of
		 * PathPairs.
		 */
		final PathPair[] pathPairs = getPathPairs(logger, redPaths, limePaths);

		/** Write everything out. */
		buildKmlFile(logger, redLoops, limeLoops, redPaths, limePaths,
				pathPairs, kmlFile);
	}

	private static PathPair[] getPathPairs(final MyLogger logger,
			final ArrayList<LatLng3[]> redPaths,
			final ArrayList<LatLng3[]> limePaths) {
		final TreeMap<LatLng3, LatLng3[]> redByStart =
				new TreeMap<>(LatLng3._ByLatThenLng);
		final TreeMap<LatLng3, LatLng3[]> redByEnd =
				new TreeMap<>(LatLng3._ByLatThenLng);
		final TreeMap<LatLng3, LatLng3[]> limeByStart =
				new TreeMap<>(LatLng3._ByLatThenLng);
		final TreeMap<LatLng3, LatLng3[]> limeByEnd =
				new TreeMap<>(LatLng3._ByLatThenLng);
		for (int iPass = 0; iPass < 2; ++iPass) {
			final ArrayList<LatLng3[]> paths;
			final TreeMap<LatLng3, LatLng3[]> byStart;
			final TreeMap<LatLng3, LatLng3[]> byEnd;
			if (iPass == 0) {
				paths = redPaths;
				byStart = redByStart;
				byEnd = redByEnd;
			} else {
				paths = limePaths;
				byStart = limeByStart;
				byEnd = limeByEnd;
			}
			final int n = paths.size();
			for (int k0 = 0; k0 < n; ++k0) {
				final LatLng3[] path = paths.get(k0);
				final int nInPath = path.length;
				byStart.put(path[0], path);
				byEnd.put(path[nInPath - 1], path);
			}
		}
		final int nRed = redByStart.size();
		final ArrayList<PathPair> pathPairList = new ArrayList<>();
		final Iterator<LatLng3[]> redIt = redByStart.values().iterator();
		redPaths.clear();
		for (int k0 = 0; k0 < nRed; ++k0) {
			final LatLng3[] redLatLngArray = redIt.next();
			final int nInRedPath = redLatLngArray.length;
			final LatLng3 start = redLatLngArray[0];
			final LatLng3 end = redLatLngArray[nInRedPath - 1];
			final LatLng3[] limeMatchingStart = limeByStart.get(start);
			final LatLng3[] limeMatchingEnd = limeByEnd.get(end);
			if (limeMatchingStart != null &&
					limeMatchingStart == limeMatchingEnd) {
				limeByStart.remove(start);
				limeByEnd.remove(end);
				final PathPair pathPair =
						new PathPair(logger, redLatLngArray, limeMatchingStart);
				if (pathPair._distanceM >= _ToleranceM) {
					pathPairList.add(pathPair);
				}
			} else {
				redPaths.add(redLatLngArray);
			}
		}
		limePaths.clear();
		limePaths.addAll(limeByStart.values());

		/** Build and sort pathPairs for returning. */
		final int nPathPairs = pathPairList.size();
		final PathPair[] pathPairs =
				pathPairList.toArray(new PathPair[nPathPairs]);
		Arrays.sort(pathPairs, new Comparator<PathPair>() {

			@Override
			public int compare(final PathPair pathPair0,
					final PathPair pathPair1) {
				final double d0 = pathPair0._distanceM;
				final double d1 = pathPair1._distanceM;
				if (d0 > d1) {
					return -1;
				}
				if (d0 < d1) {
					return 1;
				}
				for (int iPass = 0; iPass < 2; ++iPass) {
					final LatLng3[] path0 =
							iPass == 0 ? pathPair0._red : pathPair0._lime;
					final LatLng3[] path1 =
							iPass == 0 ? pathPair1._red : pathPair1._lime;
					final int n0 = path0.length;
					final int n1 = path1.length;
					if (n0 != n1) {
						return n0 < n1 ? -1 : 1;
					}
					for (int k = 0; k < n0; ++k) {
						final LatLng3 latLng0 = path0[k];
						final LatLng3 latLng1 = path0[k];
						final int compareValue =
								LatLng3._ByLatThenLng.compare(latLng0, latLng1);
						if (compareValue != 0) {
							return compareValue;
						}
					}
				}
				return 0;
			}
		});
		return pathPairs;
	}

	private static void createLoopsAndPaths(final MyLogger logger,
			final double[][] edges, final ArrayList<LatLng3[]> loops,
			final ArrayList<LatLng3[]> paths) {
		final int nEdges = edges.length;
		final TreeMap<LatLng3, ArrayList<LatLng3>> byFirst =
				new TreeMap<>(LatLng3._ByLatThenLng);
		final TreeMap<LatLng3, ArrayList<LatLng3>> byLast =
				new TreeMap<>(LatLng3._ByLatThenLng);

		for (int k = 0; k < nEdges; ++k) {
			final double[] edge = edges[k];
			final LatLng3 latLng0 = LatLng3.getLatLngB(edge[0], edge[1]);
			final LatLng3 latLng1 = LatLng3.getLatLngB(edge[2], edge[3]);
			final ArrayList<LatLng3> pathA = byFirst.get(latLng1);
			final ArrayList<LatLng3> pathB = byLast.get(latLng0);
			if (pathA == null && pathB == null) {
				final ArrayList<LatLng3> littlePath = new ArrayList<>(2);
				littlePath.add(latLng0);
				littlePath.add(latLng1);
				byFirst.put(latLng0, littlePath);
				byLast.put(latLng1, littlePath);
				continue;
			}
			if (pathA == null) {
				/** Append pathB. */
				byLast.remove(latLng0);
				pathB.add(latLng1);
				byLast.put(latLng1, pathB);
				continue;
			}
			if (pathB == null) {
				/** Prepend pathA. */
				byFirst.remove(latLng1);
				/** Shift pathA to the right to make room for latLng0. */
				final int nInOldPath = pathA.size();
				/** Expand it to make it big enough. */
				pathA.add(null);
				/** Do the shift. */
				for (int k1 = 0; k1 < nInOldPath; ++k1) {
					pathA.set(nInOldPath - k1, pathA.get(nInOldPath - k1 - 1));
				}
				pathA.set(0, latLng0);
				byFirst.put(latLng0, pathA);
				continue;
			}
			/** Link together. pathA and pathB don't exist as we know them. */
			byLast.remove(latLng0);
			byFirst.remove(latLng1);
			if (pathA == pathB) {
				/**
				 * Completes a loop; we don't need the new edge for the loop.
				 */
				final LatLng3[] latLngArray =
						pathA.toArray(new LatLng3[pathA.size()]);
				loops.add(latLngArray);
				continue;
			}
			/** Link them together into pathB. */
			pathB.addAll(pathA);
			byFirst.put(pathB.get(0), pathB);
			byLast.put(pathB.get(pathB.size() - 1), pathB);
		}
		for (final ArrayList<LatLng3> list : byFirst.values()) {
			paths.add(list.toArray(new LatLng3[list.size()]));
		}
	}

	private static class PathPair {
		final LatLng3[] _red;
		final LatLng3[] _lime;
		final Extent _extent;
		final double _distanceM;

		private PathPair(final MyLogger logger, final LatLng3[] red,
				final LatLng3[] lime) {
			_red = red;
			_lime = lime;
			Extent extent = Extent.getUnsetExtent();
			for (int iPass = 0; iPass < 2; ++iPass) {
				final LatLng3[] path = iPass == 0 ? _red : _lime;
				final int nGcas = path.length - 1;
				for (int k = 0; k < nGcas; ++k) {
					final LatLng3 latLng0 = path[k];
					final LatLng3 latLng1 = path[k + 1];
					extent = extent.buildExtension(latLng0, latLng1);
				}
			}
			_extent = extent;
			double disanceM = 0d;
			for (int iPass = 0; iPass < 2; ++iPass) {
				final LatLng3[] latLngArray = iPass == 0 ? red : lime;
				final NonLoop nonLoop =
						new NonLoop(logger, /* id= */0, Arrays.asList(latLngArray));
				final GeoMtx gcaMtx = nonLoop.getGcaMtx();
				final LatLng3[] otherLatLngList = iPass == 0 ? lime : red;
				final int nOther = otherLatLngList.length;
				for (int k = 0; k < nOther; ++k) {
					final LatLng3 limeLatLng = otherLatLngList[k];
					final GreatCircleArc.Projection projection =
							gcaMtx.findProjection(limeLatLng);
					final double thisDistanceM = projection.getRToGca() * _RToM;
					disanceM = Math.max(disanceM, thisDistanceM);
				}
			}
			_distanceM = disanceM;
		}
	}

	private static void buildKmlFile(final MyLogger logger,
			final ArrayList<LatLng3[]> redLoops,
			final ArrayList<LatLng3[]> limeLoops,
			final ArrayList<LatLng3[]> redPaths,
			final ArrayList<LatLng3[]> limePaths, final PathPair[] pathPairs,
			final File kmlFileIn) {
		if (kmlFileIn == null) {
			return;
		}
		final String filePath = kmlFileIn.getAbsolutePath();
		final String extension = FilenameUtils.EXTENSION_SEPARATOR_STR + "kml";
		final String newFilePath =
				FilenameUtils.removeExtension(filePath) + extension;
		final File kmlFile = new File(newFilePath);
		final String kmlName = String.format("%s.%s",
				kmlFile.getParentFile().getName(), kmlFile.getName());
		final KmlDocument kmlDocument = new KmlDocument(/* id= */null, kmlName,
				/* visibility= */false, /* open= */false, /* description= */String
						.format("Created on %s.", TimeUtilities.formatNow()));
		/**
		 * red will have width 1.5/2.5, opacity=f8/ff%. lime will have width
		 * 2/3, and opacity=ef/f0.
		 */
		final KmlStyleMap redStyleMap = kmlDocument.createStyleMap("red",
				ColorUtils._Red, /* fill= */false, /* outline= */true,
				/* normalOpacitiy= */0xf8, /* normalWidth= */2d,
				/* highlightOpacity= */0xff, /* highlightWidth= */3d);
		final KmlStyleMap limeStyleMap = kmlDocument.createStyleMap("lime",
				ColorUtils._Lime, /* fill= */false, /* outline= */true,
				/* normalOpacitiy= */0xef, /* normalWidth= */1d,
				/* highlightOpacity= */0xf0, /* highlightWidth= */2d);

		/** Loops first. */
		for (int iPass = 0; iPass < 2; ++iPass) {
			final KmlStyleMap styleMap = iPass == 0 ? redStyleMap : limeStyleMap;
			final ArrayList<LatLng3[]> loops = iPass == 0 ? redLoops : limeLoops;
			final int nLoops = loops == null ? 0 : loops.size();
			if (nLoops > 0) {
				final String loopsFolderName = String.format("%d %s Loops.", nLoops,
						iPass == 0 ? "Red" : "Lime");
				final KmlFolder loopsFolder = new KmlFolder(/* id= */null,
						loopsFolderName, /* visibility= */true, /* open= */true,
						/* description= */null);
				kmlDocument.addFeature(loopsFolder);
				for (int k0 = 0; k0 < nLoops; ++k0) {
					final LatLng3[] latLngArray = loops.get(k0);
					final int nLatLngs = latLngArray.length;
					final String placemarkName =
							String.format("Loop starting at %s with %d LatLngs",
									latLngArray[0].getString(/* nDigits= */2), nLatLngs);
					final String description = placemarkName;
					final double[][] coordsS = new double[nLatLngs][];
					for (int k1 = 0; k1 < nLatLngs; ++k1) {
						coordsS[k1] = latLngArray[k1].toArray();
					}
					final KmlPolygon polygon =
							new KmlPolygon(/* id= */null, Arrays.asList(coordsS));
					final KmlPlacemark placemark = new KmlPlacemark(/* id= */null,
							placemarkName, /* visibility= */true, /* open= */true,
							description, polygon);
					placemark.setStyleUrl(styleMap);
					loopsFolder.addFeature(placemark);
				}
			}
		}
		/** Singles next. */
		for (int iPass = 0; iPass < 2; ++iPass) {
			final KmlStyleMap styleMap = iPass == 0 ? redStyleMap : limeStyleMap;
			final ArrayList<LatLng3[]> singles =
					iPass == 0 ? redPaths : limePaths;
			final int nSingles = singles == null ? 0 : singles.size();
			if (nSingles > 0) {
				final String singlesFolderName = String.format("%d %s Singles.",
						nSingles, iPass == 0 ? "Red" : "Lime");
				final KmlFolder singlesFolder = new KmlFolder(/* id= */null,
						singlesFolderName, /* visibility= */true, /* open= */true,
						/* description= */null);
				kmlDocument.addFeature(singlesFolder);
				for (int k0 = 0; k0 < nSingles; ++k0) {
					final LatLng3[] latLngArray = singles.get(k0);
					final int nLatLngs = latLngArray.length;
					final String placemarkName =
							String.format("Single starting at %s with %d LatLngs",
									latLngArray[0].getString(/* nDigits= */2), nLatLngs);
					final String description = placemarkName;
					final double[][] coordsS = new double[nLatLngs][];
					for (int k1 = 0; k1 < nLatLngs; ++k1) {
						coordsS[k1] = latLngArray[k1].toArray();
					}
					final KmlLineString lineString =
							new KmlLineString(/* id= */null, Arrays.asList(latLngArray));
					final KmlPlacemark placemark = new KmlPlacemark(/* id= */null,
							placemarkName, /* visibility= */true, /* open= */true,
							description, lineString);
					placemark.setStyleUrl(styleMap);
					singlesFolder.addFeature(placemark);
				}
			}
		}

		/* Finish with descending list of PathPairs. */
		final int nPathPairs = pathPairs.length;
		final String pathPairsFolderName =
				String.format("%d PathPairs.", nPathPairs);
		final KmlFolder pathPairsFolder = new KmlFolder(/* id= */null,
				pathPairsFolderName, /* visibility= */true, /* open= */false,
				/* description= */null);
		kmlDocument.addFeature(pathPairsFolder);
		for (int k0 = 0; k0 < nPathPairs; ++k0) {
			final PathPair pathPair = pathPairs[k0];
			final String pathPairFolderName = String.format(
					"PathPair[%02d Meters[%.3f], Center%s nRed/nLime[%d/%d]", k0,
					pathPair._distanceM,
					pathPair._extent.getCentralLatLng().getString(2),
					pathPair._red.length, pathPair._lime.length);
			final KmlFolder pairFolder = new KmlFolder(/* id= */null,
					pathPairFolderName, /* visibility= */k0 < 10, /* open= */k0 < 10,
					/* description= */null);
			pathPairsFolder.addFeature(pairFolder);
			for (int iPass = 0; iPass < 2; ++iPass) {
				final LatLng3[] latLngArray =
						iPass == 0 ? pathPair._red : pathPair._lime;
				final int nLatLngs = latLngArray.length;
				final String placemarkName =
						String.format("%s starting at %s with %d LatLngs",
								iPass == 0 ? "Red" : "Lime",
								latLngArray[0].getString(/* nDigits= */2), nLatLngs);
				final String description = placemarkName;
				final double[][] coordsS = new double[nLatLngs][];
				for (int k1 = 0; k1 < nLatLngs; ++k1) {
					coordsS[k1] = latLngArray[k1].toArray();
				}
				final KmlLineString lineString =
						new KmlLineString(/* id= */null, Arrays.asList(latLngArray));
				final KmlPlacemark placemark = new KmlPlacemark(/* id= */null,
						placemarkName, /* visibility= */true, /* open= */true,
						description, lineString);
				placemark.setStyleUrl(iPass == 0 ? redStyleMap : limeStyleMap);
				pairFolder.addFeature(placemark);
			}
		}

		/* Write it out. */
		try (final FileOutputStream fos = new FileOutputStream(kmlFile)) {
			kmlDocument.writeKmlDoc(fos);
		} catch (final IOException e) {
		}
	}

	public static void main(final String[] args) {
		int iArg = 0;
		final String redBFilePath = args[iArg++];
		final String limeBFilePath = args[iArg++];
		final String kmlFilePath = args[iArg++];
		compareGshhsFiles(/* logger= */null, new File(redBFilePath),
				new File(limeBFilePath), new File(kmlFilePath));
	}
}
