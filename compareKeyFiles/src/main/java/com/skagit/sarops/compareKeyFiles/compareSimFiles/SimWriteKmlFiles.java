package com.skagit.sarops.compareKeyFiles.compareSimFiles;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.skagit.util.colorUtils.ColorUtils;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.geometry.gcaSequence.Loop3Statics;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.kmlObjects.kmlFeature.KmlDocument;
import com.skagit.util.kmlObjects.kmlFeature.KmlFolder;
import com.skagit.util.kmlObjects.kmlFeature.KmlPlacemark;
import com.skagit.util.kmlObjects.kmlGeometry.KmlGeometry;
import com.skagit.util.kmlObjects.kmlGeometry.KmlLineString;
import com.skagit.util.kmlObjects.kmlGeometry.KmlMultiGeometry;
import com.skagit.util.kmlObjects.kmlGeometry.KmlPolygon;
import com.skagit.util.kmlObjects.kmlStyleSelector.KmlStyle;
import com.skagit.util.kmlObjects.kmlStyleSelector.KmlStyleMap;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;

class SimWriteKmlFiles {

	static void simWriteKmlFiles(
			final TreeMap<File, String> caseDirToShortName,
			final TreeMap<File, TreeMap<Treatment, SimDataRow>> caseDirToTreeMap) {
		final int nCaseDirs = caseDirToTreeMap.size();
		final Iterator<Map.Entry<File, TreeMap<Treatment, SimDataRow>>> it =
				caseDirToTreeMap.entrySet().iterator();
		for (int k = 0; k < nCaseDirs; ++k) {
			final Map.Entry<File, TreeMap<Treatment, SimDataRow>> entry =
					it.next();
			final File caseDir = entry.getKey();
			final TreeMap<Treatment, SimDataRow> treeMap = entry.getValue();
			final int nDataRows = treeMap.size();
			if (nDataRows == 0) {
				return;
			}
			final String kmlName = caseDirToShortName.get(caseDir);
			final String description = getStandardDescription(treeMap.values());
			final KmlDocument kmlDocument =
					new KmlDocument(/* id= */null, /* name= */kmlName,
							/* visibility= */true, /* open= */true, description);
			final KmlStyleMap origStyleMap = kmlDocument
					.createStyleMap(/* coreString= */"orig", ColorUtils._Blue);
			final KmlStyleMap newStyleMap = kmlDocument
					.createStyleMap(/* coreString= */"new", ColorUtils._Red);
			final String radioListStyleId = "radioListStyleId";
			final KmlStyle radioListStyle =
					kmlDocument.createRadioListStyle(radioListStyleId);
			kmlDocument.setStyleUrl(radioListStyle);

			/**
			 * For each Treatment~TimeStep, build a KmlFolder. Into that folder, put
			 * the TimeSteps.
			 */
			Treatment currentKey = null;
			final Iterator<Treatment> it0 = treeMap.keySet().iterator();
			final ArrayList<SimDataRow> theseDataRows =
					new ArrayList<>();
			boolean open = true;
			for (;; open = false) {
				if (!it0.hasNext()) {
					break;
				}
				final Treatment key = it0.next();
				if (currentKey == null) {
					currentKey = key;
				}
				final SimDataRow dataRow = treeMap.get(key);
				if (currentKey.equalExceptForTimeStep(key)) {
					theseDataRows.add(dataRow);
					continue;
				}
				buildFolderForScenObjTp(kmlDocument, origStyleMap, newStyleMap,
						radioListStyle, open, theseDataRows);
				currentKey = key;
				theseDataRows.clear();
				theseDataRows.add(dataRow);
			}
			if (!theseDataRows.isEmpty()) {
				buildFolderForScenObjTp(kmlDocument, origStyleMap, newStyleMap,
						radioListStyle, open, theseDataRows);
			}
			kmlDocument.writeKmlDoc(new File(caseDir, "KmlPair(Sim)"));
		}
	}

	private static String getStandardDescription(
			final Collection<SimDataRow> dataRows) {
		final int nDataRows = dataRows == null ? 0 : dataRows.size();
		if (nDataRows == 0) {
			return "Vacuous List.";
		}
		Extent extent = Extent.getUnsetExtent();
		final Iterator<SimDataRow> it = dataRows.iterator();
		for (int k = 0; k < nDataRows; ++k) {
			final SimDataRow dataRow = it.next();
			final LatLng3 latLngA = dataRow._origCenter;
			final LatLng3 latLngB = dataRow._newCenter;
			extent = extent.buildExtension(latLngA, latLngB);
		}
		final String description =
				String.format("%d dataRows, %s", nDataRows, extent.getString());
		return description;
	}

	private static String getLittleFolderDescription(final SimDataRow dataRow,
			final Loop3 origLoop, final Loop3 newLoop) {
		final int nOrig = dataRow._nOrig;
		final String origExtentString = origLoop == null ? "No Orig Loop"
				: origLoop.getFullExtent().getCenterAndRadiusString();
		final double newInOrig = dataRow._newInOrigCntnmnt;
		final int nNew = dataRow._nNew;
		final String newExtentString = newLoop == null ? "No New Loop"
				: newLoop.getFullExtent().getCenterAndRadiusString();
		final double origInNew = dataRow._origInNewCntnmnt;
		final double dNmi = dataRow._nmiBetweenCenters;
		final String description = String.format(
				"Orig(%d Ptcls, newInOrig:%.4f%%) %s\n" //
						+ "New(%d Ptcls, origInNew:%.4f%%) %s\n"
						+ "NM between Centers: %.5f", //
				nOrig, newInOrig * 100d, origExtentString, //
				nNew, origInNew * 100d, newExtentString, //
				dNmi);
		return description;
	}

	private static void buildFolderForScenObjTp(final KmlDocument kmlDocument,
			final KmlStyleMap origStyleMap, final KmlStyleMap newStyleMap,
			final KmlStyle radioListStyle, final boolean open,
			final ArrayList<SimDataRow> dataRows) {

		final int nDataRows = dataRows.size();
		if (nDataRows == 0) {
			return;
		}

		final String mainName =
				dataRows.get(0)._treatment.getScenAndSotString();
		final String mainDescription = getStandardDescription(dataRows);

		final KmlFolder mainFolder = new KmlFolder( //
				/* id= */null, //
				mainName, //
				/* visibility= */true, //
				open, //
				mainDescription //
		);
		mainFolder.setStyleUrl(radioListStyle);
		kmlDocument.addFeature(mainFolder);

		/** For each dataRow, create a folder. */
		for (int k = 0; k < nDataRows; ++k) {
			final SimDataRow dataRow = dataRows.get(k);
			final Treatment treatment = dataRow._treatment;
			final Loop3 origLoop = dataRow.computeCcw50Ellipse(/* orig= */true);
			final Loop3 newLoop = dataRow.computeCcw50Ellipse(/* orig= */false);
			final String littleFolderName = treatment.getDtgString();
			final String littleFolderDescription =
					getLittleFolderDescription(dataRow, origLoop, newLoop);
			final KmlFolder littleFolder = new KmlFolder( //
					/* id= */null, //
					/* name= */littleFolderName, //
					/* visibility= */true, //
					/* open= */k == 0, //
					/* description= */littleFolderDescription //
			);
			final LatLng3 newCenter = dataRow._newCenter;
			final LatLng3 origCenter = dataRow._origCenter;
			final LatLng3 midpoint;
			if (newCenter == null || origCenter == null) {
				midpoint = null;
			} else if (newCenter.equals(origCenter)) {
				midpoint = newCenter;
			} else {
				final GreatCircleArc gca =
						GreatCircleArc.CreateGca(newCenter, origCenter);
				midpoint = gca.computeMidpoint();
			}
			mainFolder.addFeature(littleFolder);
			if (origLoop != null) {
				final String origDescription =
						String.format("%.3SqNmi %s", origLoop.getSqNmi(),
								origLoop.getFullExtent().getCenterAndRadiusString());
				final GreatCircleArc origGca =
						GreatCircleArc.CreateGca(origCenter, midpoint);
				addLoopAndGcaWithStyleToFolder(origLoop, origGca, origStyleMap,
						littleFolder, /* id= */null, "Orig", origDescription);
			}
			if (newLoop != null) {
				final String newDescription =
						String.format("%.3SqNmi %s", newLoop.getSqNmi(),
								newLoop.getFullExtent().getCenterAndRadiusString());
				final GreatCircleArc newGca =
						GreatCircleArc.CreateGca(newCenter, midpoint);
				addLoopAndGcaWithStyleToFolder(newLoop, newGca, newStyleMap,
						littleFolder, /* id= */null, "New", newDescription);
			}
		}
	}

	public static void addLoopAndGcaWithStyleToFolder(final Loop3 loop,
			final GreatCircleArc gca, final KmlStyleMap styleMap,
			final KmlFolder folder, final String id, final String name,
			final String description) {
		final KmlGeometry polygon = new KmlPolygon( //
				/* id= */String.format("Pgon from Loop[%s]", loop.getIdString()), //
				loop //
		);
		final KmlGeometry[] geometries;
		if (gca != null) {
			final List<LatLng3> gcaLatLngs = Arrays.asList(new LatLng3[] {
					gca.getLatLng0(), gca.getLatLng1()
			});
			final KmlLineString lineString =
					new KmlLineString(/* id= */"CntrToMidpnt", gcaLatLngs);
			geometries = new KmlGeometry[] {
					polygon, lineString
			};
		} else {
			geometries = new KmlGeometry[] {
					polygon
			};
		}
		final KmlMultiGeometry multiGeometry =
				new KmlMultiGeometry(loop.getIdString(), geometries);
		final KmlPlacemark placemark = new KmlPlacemark( //
				id, name, /* visibility= */true, //
				/* open= */true, //
				description, //
				multiGeometry //
		);
		placemark.setStyleUrl(styleMap);
		folder.addFeature(placemark);
	}

	public static void main(final String[] args) {
		final KmlDocument kmlDocument = new KmlDocument( //
				/* id= */"Kml0", //
				/* name= */null, //
				/* visibility= */true, //
				/* open= */true, //
				/* description= */"Kml0 D" //
		);

		final KmlFolder folder = new KmlFolder( //
				/* id= */"F1", //
				/* name= */"F1 Nm", //
				/* visibility= */true, //
				/* open= */true, //
				/* description= */"F1 Desc" //
		);
		kmlDocument.addFeature(folder);

		final int flag =
				Loop3Statics.createGenericFlag(/* isClockwise= */false);

		final KmlStyleMap redStyleMap =
				kmlDocument.createStyleMap(/* coreString= */"red", ColorUtils._Red);
		final LatLng3[] redLoopArray = new LatLng3[] { //
				LatLng3.getLatLngB(47d, -123.5), //
				LatLng3.getLatLngB(46.5, -123.5), //
				LatLng3.getLatLngB(46.75, -124d), //
				LatLng3.getLatLngB(47d, -123.5) //
		};
		final Loop3 redLoop = Loop3.getLoop(/* _logger= */null, /* id= */0,
				/* subId= */0, flag, /* ancestorId= */-1, redLoopArray,
				/* logChanges= */false, /* debug= */false);
		addLoopAndGcaWithStyleToFolder(redLoop, /* gca= */null, redStyleMap,
				folder, "RedPMRK", "RedName", "RedPmrk D");

		final KmlStyleMap blueStyleMap = kmlDocument
				.createStyleMap(/* coreString= */"blue", ColorUtils._Blue);
		final LatLng3[] blueLoopArray = new LatLng3[] { //
				LatLng3.getLatLngB(47d, -123.75), //
				LatLng3.getLatLngB(46.5, -123.75), //
				LatLng3.getLatLngB(46.75, -124.25), //
				LatLng3.getLatLngB(47d, -123.75) //
		};
		final Loop3 blueLoop = Loop3.getLoop(/* _logger= */null, /* id= */0,
				/* subId= */0, flag, /* ancestorId= */-1, blueLoopArray,
				/* logChanges= */false, /* debug= */false);
		addLoopAndGcaWithStyleToFolder(blueLoop, /* gca= */null, blueStyleMap,
				folder, "BluePMRK", "BlueName", "BluePmrk D");

		kmlDocument.writeKmlDoc(new File("Kml0.kml"));
	}

}
