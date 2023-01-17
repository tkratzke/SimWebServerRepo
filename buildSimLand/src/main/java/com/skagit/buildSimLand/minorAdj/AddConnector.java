package com.skagit.buildSimLand.minorAdj;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import com.skagit.util.CartesianUtil;
import com.skagit.util.SizeOf;
import com.skagit.util.colorUtils.ColorUtils;
import com.skagit.util.geometry.crossingPair.CrossingPair2;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.geometry.gcaSequence.Loop3Statics;
import com.skagit.util.geometry.loopsFinder.LoopsFinder;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;

public class AddConnector extends MinorAdj {
	final private Loop3 _carver;

	public AddConnector(final MyLogger logger, final boolean verboseDump, final File f, final String description,
			final int colorIdx, final List<LatLng3> latLngList, final boolean isWater) {
		super(AdjType.ADD_CONNECTOR, verboseDump, f, description, colorIdx);
		final int nLatLngs = latLngList.size();
		final int flag = Loop3Statics.createGenericFlag(/* clockwise= */isWater);
		final Loop3 carver = Loop3.getLoop(logger, /* id= */0, /* subId= */0, flag, /* ancestorId= */-1,
				latLngList.toArray(new LatLng3[nLatLngs]), /* logChanges= */false, /* debug= */false);
		if (carver.isClockwise() != isWater) {
			_carver = carver.createReverseLoop(logger);
		} else {
			_carver = carver;
		}
	}

	@Override
	protected Boolean isWater() {
		return _carver == null ? null : _carver.isClockwise();
	}

	@Override
	public void coreProcessAdjSummaries(final MyLogger logger, final ArrayList<AdjSummary> adjSummaries) {
		final int nAdjSummaries = adjSummaries == null ? 0 : adjSummaries.size();
		final ArrayList<Loop3> primaryLoops = new ArrayList<>(adjSummaries.size());

		/**
		 * Gather all of the loops into primaryLoops. Furthermore, we must mark the
		 * AdjSummaries that correspond to disappearing Loops for deletion. To do this,
		 * we start by setting up a map between Loops and AdjSummaries.
		 */
		final TreeMap<Loop3, AdjSummary> loopsToDelete = new TreeMap<>(Loop3._SizeStructure);
		for (int k = 0; k < nAdjSummaries; ++k) {
			final AdjSummary adjSummary = adjSummaries.get(k);
			if (adjSummary.getDeleted()) {
				continue;
			}
			final Loop3 loop = adjSummary.getNominalLoop();
			primaryLoops.add(loop);
			loopsToDelete.put(loop, adjSummary);
		}

		/** Do the merge. */
		SizeOf.runGC(logger);
		final ArrayList<Loop3> resultLoops = doTheMerge(logger, primaryLoops);
		final int nResultLoops = resultLoops.size();

		/** Find which Ilds are new and which ones did not change. */
		for (int k = 0; k < nResultLoops; ++k) {
			final Loop3 loop = resultLoops.get(k);
			final AdjSummary adjSummary = loopsToDelete.get(loop);
			if (adjSummary != null) {
				/**
				 * This Loop3 was originally there, so we do nothing with it. Since we do not
				 * want to delete it, we remove it from the map that identifies what we will
				 * delete.
				 */
				loopsToDelete.remove(loop);
			} else {
				/** This Loop is a new one. Create a new AdjSummary for it. */
				final AdjSummary adjSummaryForNew = new AdjSummary(null);
				adjSummaryForNew._resultLoop = loop;
				adjSummaryForNew.setNew(this);
				adjSummaries.add(adjSummaryForNew);
			}
		}

		/**
		 * The original Loops that showed up in resultLoops have been removed from
		 * loopsToDelete. The rest must be flagged for deletion.
		 */
		for (final AdjSummary adjSummaryForDelete : loopsToDelete.values()) {
			adjSummaryForDelete.setDeleted(this);
		}
	}

	@Override
	public int littleCompareTo(final MinorAdj minorAdj) {
		final int compareValue = Loop3._SizeStructure.compare(_carver, ((AddConnector) minorAdj)._carver);
		return compareValue;
	}

	@Override
	protected String getDataString(final List<Loop3> oldLoops) {
		final int nGcas = _carver == null ? 0 : _carver.getNGcas();
		final int nLatLngsToShow = _verboseDump ? nGcas : Math.min(nGcas, _MaxNLatLngsToShow);
		final GreatCircleArc[] gcaArray;
		if (nLatLngsToShow == nGcas && nGcas > 0) {
			gcaArray = _carver.getGcaArray();
		} else {
			gcaArray = new GreatCircleArc[nLatLngsToShow];
			final int lowerPart = nLatLngsToShow / 2;
			for (int k = 0; k < lowerPart; ++k) {
				gcaArray[k] = _carver.getGca(k);
			}
			final int upperPart = nLatLngsToShow - lowerPart;
			for (int k = 0; k < upperPart; ++k) {
				gcaArray[lowerPart + k] = _carver.getGca(nGcas - upperPart + k);
			}
		}
		final ColorUtils.ColorGrouping colorGrouping = _adjType._colorGrouping;
		final ColorUtils.ColorGroup colorGroup = ColorUtils.getColorGroup(colorGrouping);
		final int nColors = colorGroup._nameAndRgbs.length;
		final String colorName0 = ColorUtils.getColorName(colorGrouping, 0);
		final String colorName1 = ColorUtils.getColorName(colorGrouping, nColors / 2);
		final String[] colorNames = new String[] {
				colorName0, colorName1
		};
		final String s = CartesianUtil.getXmlDump(gcaArray, colorNames, /* numberEdgeInc= */1, /* GcaFilter= */null);
		return s;
	}

	@Override
	protected String getSummaryString() {
		final GreatCircleArc[] gcaArray = _carver.getGcaArray();
		final GreatCircleArc gca0 = gcaArray[0];
		final String latLng0String = gca0.getLatLng0().getString();
		final GreatCircleArc pvs = gca0.getPvs();
		final String pvsString = pvs.getLatLng0().getString();
		final String s = String.format("AddConnector-%s(%s):%s→%s", isWater() ? "WATER" : "LAND", _description,
				latLng0String, pvsString);
		return s;
	}

	@Override
	protected String extraDataString() {
		final String s = String.format("n[%d] %s", _carver.getNLatLngs(), _carver.isClockwise() ? "CW" : "CCW");
		return s;
	}

	public int getId() {
		return _carver.getId();
	}

	@Override
	public Extent getExtent() {
		return _carver.getFullExtent();
	}

	final private ArrayList<Loop3> doTheMerge(final MyLogger logger, final List<Loop3> primaryLoops) {
		final int nPrimaries = primaryLoops.size();

		final ArrayList<Loop3> xflctLoops = new ArrayList<>();
		final ArrayList<Loop3> clearLoops = new ArrayList<>();
		final Extent carverExtent = _carver.getFullExtent();
		for (int k0 = 0; k0 < nPrimaries; ++k0) {
			final Loop3 primaryLoop = primaryLoops.get(k0);
			final Extent primaryExtent = primaryLoop.getFullExtent();
			final boolean xflct;
			if (!primaryExtent.overlaps(carverExtent)) {
				xflct = false;
			} else {
				final CrossingPair2 crossingPair = primaryLoop.findCrossingPair(logger, _carver);
				xflct = crossingPair != null;
			}
			if (xflct) {
				if (xflctLoops.isEmpty()) {
					/**
					 * We keep _carver around for reference; his links will be destroyed when we
					 * merge loops.
					 */
					xflctLoops.add(_carver.clone());
				}
				xflctLoops.add(primaryLoop);
			} else {
				clearLoops.add(primaryLoop);
			}
		}
		final int nXflct = xflctLoops.size();
		if (nXflct == 0) {
			clearLoops.add(_carver);
			return clearLoops;
		}

		final ArrayList<Loop3> loopsToReturn = LoopsFinder.findLoopsFromLoops(logger,
				xflctLoops.toArray(new Loop3[nXflct]), /* waterWins= */_carver.isClockwise());
		loopsToReturn.addAll(clearLoops);
		return loopsToReturn;
	}

}