package com.skagit.buildSimLand.minorAdj;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.w3c.dom.Element;

import com.skagit.util.StaticUtilities;
import com.skagit.util.StringUtilities;
import com.skagit.util.geometry.gcaSequence.DistinctLatLngFinder;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.geometry.gcaSequence.Loop3Statics;
import com.skagit.util.kmlObjects.KmlObject;
import com.skagit.util.kmlObjects.kmlFeature.KmlDocument;
import com.skagit.util.kmlObjects.kmlFeature.KmlPlacemark;
import com.skagit.util.kmlObjects.kmlGeometry.KmlGeometry;
import com.skagit.util.kmlObjects.kmlGeometry.KmlLineString;
import com.skagit.util.kmlObjects.kmlGeometry.KmlLinearRing;
import com.skagit.util.kmlObjects.kmlGeometry.KmlPoint;
import com.skagit.util.kmlObjects.kmlGeometry.KmlPolygon;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;

public class CollectMinorAdjsFromUmbrellaDir {

	private static class DoublePlus {
		final double _d;
		final ArrayList<MinorAdj> _adjs;

		DoublePlus(final double d) {
			_d = d;
			_adjs = new ArrayList<>();
		}
	}

	private static class MinorAdjData {
		final private static double _DefaultMaxSqNmiForDeleteInnermost = 500d;

		final private AdjType _adjType;
		final private String _description;
		final private List<LatLng3> _latLngList;
		final private boolean _isWater;
		final private int _levelForDeletion;
		final private double _maxSqNmi;

		private MinorAdjData(final MyLogger logger, final KmlPlacemark placemark) {
			_description = placemark.getDescription();
			final String placemarkName = placemark.getName().trim();
			_adjType = AdjType.getAdjType(placemarkName);
			if (_adjType == null) {
				_latLngList = null;
				_levelForDeletion = -1;
				_maxSqNmi = Double.NaN;
				_isWater = false;
				return;
			}

			double maxSqNmi = Double.NaN;
			final Element placemarkElement = placemark.getElement();

			switch (_adjType) {
			case ADD_CONNECTOR:
			case ADD_POLYGON:
			case DELETE_POLYGON:
				final KmlPolygon polygon = (KmlPolygon) placemark.getGeometry();
				final KmlLinearRing outerBoundaryIs = polygon.getOuterBoundaryIs();
				final List<LatLng3> outerBoundaryLatLngList = outerBoundaryIs.getCoordinates();
				final int nLatLngsIn = outerBoundaryLatLngList.size();
				final LatLng3[] latLngArray = outerBoundaryLatLngList.toArray(new LatLng3[nLatLngsIn]);
				_isWater = KmlObject.getBooleanFromChild(placemarkElement, "water", false);
				final int flag = Loop3Statics.createGenericFlag(_isWater);
				/** Make sure that the LatLngArray agrees with cw. */
				Loop3 loop = Loop3.getLoop(logger, /* id= */0, /* subId= */0, flag, /* ancestorId= */-1, latLngArray,
						/* logCahnges= */false, /* debug= */false);
				loop = Loop3Statics.convertToCwOrCcw(logger, loop, _isWater);
				_latLngList = Arrays.asList(loop.getLatLngArray());
				_levelForDeletion = -1;
				_maxSqNmi = Double.NaN;
				return;
			case DELETE_INNERMOST:
				final KmlGeometry geometry = placemark.getGeometry();
				final KmlPoint point = (KmlPoint) geometry;
				_latLngList = point.getCoordinates();
				_levelForDeletion = -1;
				maxSqNmi = _DefaultMaxSqNmiForDeleteInnermost;
				try {
					final String maxSqNmiText = KmlObject.getTextFromChild(placemarkElement, "maxSqNmi");
					maxSqNmi = Double.parseDouble(maxSqNmiText);
				} catch (final NullPointerException | NumberFormatException e) {
				}
				_maxSqNmi = maxSqNmi;
				_isWater = false;
				return;
			case DELETE_THRESHOLD:
				_latLngList = null;
				int levelForDeletion = 1;
				try {
					final String levelText = KmlObject.getTextFromChild(placemarkElement, "level");
					levelForDeletion = Integer.parseInt(levelText);
				} catch (final NullPointerException | NumberFormatException e) {
				}
				_levelForDeletion = levelForDeletion;
				maxSqNmi = 0d;
				try {
					final String maxSqNmiText = KmlObject.getTextFromChild(placemarkElement, "maxSqNmi");
					maxSqNmi = Double.parseDouble(maxSqNmiText);
				} catch (final NullPointerException | NumberFormatException e) {
				}
				_maxSqNmi = maxSqNmi;
				_isWater = false;
				return;
			case SUBSTITUTE_PATH:
				final KmlLineString lineString = (KmlLineString) placemark.getGeometry();
				final List<LatLng3> lineStringLatLngList = lineString.getCoordinates();
				final DistinctLatLngFinder distinctLatLngFinder1 = new DistinctLatLngFinder(lineStringLatLngList,
						/* connectLastToFirst= */false);
				_latLngList = Arrays.asList(distinctLatLngFinder1._distinctLatLngArray);
				_levelForDeletion = -1;
				_maxSqNmi = Double.NaN;
				_isWater = false;
				return;
			default:
				_latLngList = null;
				_levelForDeletion = -1;
				_maxSqNmi = Double.NaN;
				_isWater = false;
				return;
			}
		}
	}

	private static List<MinorAdj> collectAdjsFromFile(final MyLogger logger, final File f) {
		final KmlDocument kmlDocument = KmlDocument.buildKmlDocument(f);
		final List<KmlPlacemark> placemarks = kmlDocument.gatherPlacemarks();
		final int nPlacemarks = placemarks.size();
		final List<MinorAdj> adjList = new ArrayList<>();
		for (int k = 0; k < nPlacemarks; ++k) {
			final KmlPlacemark placemark = placemarks.get(k);
			final Element element = placemark.getElement();
			final MinorAdjData minorAdjData = new MinorAdjData(logger, placemark);
			if (minorAdjData._adjType == null) {
				continue;
			}
			MinorAdj minorAdj = null;
			String description = minorAdjData._description;
			if (description == null || description.length() == 0) {
				description = MinorAdj.getDescriptionFromFile(f, k);
			}
			switch (minorAdjData._adjType) {
			case ADD_CONNECTOR:
			case ADD_POLYGON:
			case SUBSTITUTE_PATH:
				final boolean verboseDump = KmlObject.getBooleanFromChild(element, "verboseDump", false);
				switch (minorAdjData._adjType) {
				case ADD_CONNECTOR:
					minorAdj = new AddConnector(logger, verboseDump, f, description, /* colorIdx= */k,
							minorAdjData._latLngList, minorAdjData._isWater);
					break;
				case ADD_POLYGON:
					minorAdj = new AddPolygon(logger, verboseDump, f, description, /* colorIdx= */k,
							minorAdjData._latLngList, minorAdjData._isWater);
					break;
				case SUBSTITUTE_PATH:
					minorAdj = new SubstitutePath(logger, verboseDump, f, description, /* colorIdx= */k,
							minorAdjData._latLngList);
					break;
				default:
					break;
				}
				break;
			case DELETE_POLYGON:
				minorAdj = new DeletePolygon(logger, f, description, /* colorIdx= */k, minorAdjData._latLngList);
				break;
			case DELETE_INNERMOST:
				minorAdj = new DeleteInnermost(logger, f, description, /* colorIdx= */k,
						minorAdjData._latLngList.get(0), minorAdjData._maxSqNmi);
				break;
			case DELETE_THRESHOLD:
				minorAdj = new DeleteThreshold(logger, f, description, /* colorIdx= */k, minorAdjData._levelForDeletion,
						minorAdjData._maxSqNmi);
				break;
			default:
				break;
			}
			if (minorAdj != null) {
				adjList.add(minorAdj);
			}
		}
		return adjList;

	}

	private static MinorAdj[] collectAdjsFromFlatDir(final MyLogger logger, final File flatDir) {
		final ArrayList<MinorAdj> adjList = new ArrayList<>();
		if (!flatDir.exists() || !flatDir.isDirectory()) {
			return new MinorAdj[0];
		}
		final File[] adjFiles = flatDir.listFiles(new FileFilter() {

			@Override
			public boolean accept(final File f) {
				if (!f.isFile()) {
					return false;
				}
				final String lcName = f.getName().toLowerCase();
				return lcName.endsWith(".kml");
			}
		});

		for (final File f : adjFiles) {
			final List<MinorAdj> theseAdjs = collectAdjsFromFile(logger, f);
			final int nTheseAdjs = theseAdjs == null ? 0 : theseAdjs.size();
			if (nTheseAdjs > 0) {
				adjList.addAll(theseAdjs);
			}
		}
		final int nAdjs = adjList.size();
		final MinorAdj[] adjs = adjList.toArray(new MinorAdj[nAdjs]);
		return adjs;
	}

	private static void writeDispCasesDir(final File dispCasesDir, final String flatDirName, final MinorAdj[] adjs,
			final List<Loop3> oldLoops) {
		if (dispCasesDir == null || flatDirName == null || adjs == null || adjs.length == 0) {
			return;
		}
		final File dispCasesDirForFlatDir = new File(dispCasesDir, flatDirName);
		final int nAdjs = adjs.length;
		/**
		 * Build the "-Disp.xml" file corresponding to flatDir inside of
		 * dispCasesDirForFlatDir.
		 */
		final File dispCaseFile = new File(dispCasesDirForFlatDir, String.format("%s-Disp.xml", flatDirName));
		final File dispCaseFileParent = dispCaseFile.getParentFile();
		if (dispCaseFileParent.exists()) {
			if (dispCaseFileParent.isDirectory()) {
				if (!StaticUtilities.clearDirectory(dispCaseFileParent)) {
					return;
				}
			} else {
				return;
			}
		} else {
			if (!dispCaseFileParent.mkdirs()) {
				return;
			}
		}
		try (PrintStream adjsPrintStream = new PrintStream(dispCaseFile)) {
			final ArrayList<Extent> extentList = new ArrayList<>();
			adjsPrintStream.print("" //
					+ "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" //
					+ "\n<DISP>" //
					+ "\n  <GRAPHICS>");
			for (int k0 = 0; k0 < nAdjs; ++k0) {
				final MinorAdj adj = adjs[k0];
				adjsPrintStream.printf("\n");
				final String adjString = adj.getString(oldLoops);
				adjsPrintStream.print(adjString);
				final Extent extent = adj.getExtent();
				if (extent != null && !extent.isWholeWorld() && extent.valuesAreSet()) {
					extentList.add(extent);
				}
			}
			final boolean[] isLngGapFree = new boolean[] { false };
			final Extent extent = new Extent(extentList, isLngGapFree);
			final double lt = extent.getLeftLng();
			final double lo = extent.getMinLat();
			final double rt = extent.getRightLng();
			final double hi = extent.getMaxLat();
			adjsPrintStream.print(String.format("" //
					+ "\n  </GRAPHICS>\n  <REQUEST type=\"Display\">" //
					+ "\n    <INPUT " //
					+ "bottom=\"%.2f degs\" left=\"%.2f degs\" " //
					+ "right=\"%.2f degs\" top=\"%.2f degs\" />" //
					+ "\n    <CASE case_name=\"DisplayOnly\" />" //
					+ "\n  </REQUEST>" + "\n</DISP>", lo, lt, rt, hi));
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	public static MinorAdj[] collectMinorAdjsFromUmbrellaDir(final MyLogger logger, final File primaryUmbrellaDir,
			final File alternateUmbrellaDir) {
		final ArrayList<File> allFlatDirs = new ArrayList<>();
		for (int iPass = 0; iPass < 2; ++iPass) {
			final File umbrellaDir = iPass == 0 ? primaryUmbrellaDir : alternateUmbrellaDir;
			if (umbrellaDir != null && umbrellaDir.isDirectory()) {
				final File[] flatDirs = umbrellaDir.listFiles(new FileFilter() {

					@Override
					public boolean accept(final File f) {
						if (!f.isDirectory()) {
							return false;
						}
						final String lcName = f.getName().toLowerCase();
						final int firstDash = lcName.indexOf('-');
						if (firstDash < 0) {
							return false;
						}
						final String s = lcName.substring(0, firstDash);
						try {
							return Double.parseDouble(s) >= 0d;
						} catch (final NumberFormatException e) {
							return false;
						}
					}
				});
				allFlatDirs.addAll(Arrays.asList(flatDirs));
			}
		}
		final File[] flatDirs = allFlatDirs.toArray(new File[allFlatDirs.size()]);

		final TreeMap<Double, DoublePlus> bigMap = new TreeMap<>();
		final int nFlatDirs = flatDirs.length;
		for (int k = 0; k < nFlatDirs; ++k) {
			final File flatDir = flatDirs[k];
			final String flatDirName = flatDir.getName();
			final String lcName = flatDirName.toLowerCase();
			final int firstDash = lcName.indexOf('-');
			final String s = lcName.substring(0, firstDash);
			final double d = Double.parseDouble(s);
			final MinorAdj[] theseMinorAdjs = collectAdjsFromFlatDir(logger, flatDir);
			final int nTheseMinorAdjs = theseMinorAdjs == null ? 0 : theseMinorAdjs.length;
			if (nTheseMinorAdjs > 0) {
				DoublePlus thisDoublePlus = bigMap.get(d);
				if (thisDoublePlus == null) {
					thisDoublePlus = new DoublePlus(d);
					bigMap.put(d, thisDoublePlus);
				}
				thisDoublePlus._adjs.addAll(Arrays.asList(theseMinorAdjs));
			}
		}

		final int nOrders = bigMap.size();
		final DoublePlus[] doublePluses = bigMap.values().toArray(new DoublePlus[nOrders]);
		Arrays.sort(doublePluses, new Comparator<DoublePlus>() {

			@Override
			public int compare(final DoublePlus doublePlus0, final DoublePlus doublePlus1) {
				final double d0 = doublePlus0._d;
				final double d1 = doublePlus1._d;
				return d0 < d1 ? -1 : (d0 > d1 ? 1 : 0);
			}
		});

		final ArrayList<MinorAdj> allAdjList = new ArrayList<>();
		for (int k0 = 0; k0 < nOrders; ++k0) {
			final DoublePlus doublePlus = doublePluses[k0];
			allAdjList.addAll(doublePlus._adjs);
		}
		final MinorAdj[] allAdjs = allAdjList.toArray(new MinorAdj[allAdjList.size()]);
		return allAdjs;
	}

	public static void writeDispCasesDirsIfAskedTo(final MyLogger logger, final File umbrellaDir,
			final MinorAdj[] minorAdjs, final List<Loop3> oldLoops) {
		final String writeDispDirsString = StringUtilities.getSystemProperty("Write.DispDirs",
				/* useSpaceProxy= */false);
		if (writeDispDirsString == null || writeDispDirsString.length() == 0 || !StringUtilities.stringToBoolean(writeDispDirsString)) {
			return;
		}
		final TreeMap<String, ArrayList<MinorAdj>> flatDirNameToMinorAdjList = new TreeMap<>();
		final int nMinorAdjs = minorAdjs.length;
		for (int k = 0; k < nMinorAdjs; ++k) {
			final MinorAdj minorAdj = minorAdjs[k];
			final String flatDirName = minorAdj._f.getParentFile().getName();
			ArrayList<MinorAdj> theseMinorAdjs = flatDirNameToMinorAdjList.get(flatDirName);
			if (theseMinorAdjs == null) {
				theseMinorAdjs = new ArrayList<>();
				flatDirNameToMinorAdjList.put(flatDirName, theseMinorAdjs);
			}
			theseMinorAdjs.add(minorAdj);
		}
		final Iterator<Map.Entry<String, ArrayList<MinorAdj>>> it = flatDirNameToMinorAdjList.entrySet().iterator();
		final String umbrellaDirName = umbrellaDir.getName();
		/** We need a 1st cousin to primaryUmbrellaDir */
		final File umbrellaParent = umbrellaDir.getParentFile();
		final File dispCasesUmbrella = new File(umbrellaParent, String.format("%s-DispDir", umbrellaDirName));
		if (dispCasesUmbrella.exists()) {
			if (dispCasesUmbrella.isFile()) {
				return;
			}
			if (!StaticUtilities.clearDirectory(dispCasesUmbrella)) {
				return;
			}
		} else {
			if (!dispCasesUmbrella.mkdirs()) {
				return;
			}
		}
		while (it.hasNext()) {
			final Map.Entry<String, ArrayList<MinorAdj>> entry = it.next();
			final String flatDirName = entry.getKey();
			final ArrayList<MinorAdj> theseMinorAdjsList = entry.getValue();
			final MinorAdj[] theseMinorAdjs = theseMinorAdjsList.toArray(new MinorAdj[theseMinorAdjsList.size()]);
			writeDispCasesDir(dispCasesUmbrella, flatDirName, theseMinorAdjs, oldLoops);
		}
	}

	public static void main(final String[] args) {
		final File umbrellaDir = new File(args[0]);
		collectMinorAdjsFromUmbrellaDir(/* logger= */null, umbrellaDir, /* altUmbrellaDir= */null);
	}
}
