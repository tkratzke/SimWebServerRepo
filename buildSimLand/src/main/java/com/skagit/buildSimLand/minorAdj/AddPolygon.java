package com.skagit.buildSimLand.minorAdj;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.skagit.util.CartesianUtil;
import com.skagit.util.colorUtils.ColorUtils;
import com.skagit.util.geometry.crossingPair.CrossingPair2;
import com.skagit.util.geometry.gcaSequence.CleanOpenLoop;
import com.skagit.util.geometry.gcaSequence.GcaSequenceStatics;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.geometry.gcaSequence.Loop3Statics;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.gshhs.GshhsReaderStatics;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;

public class AddPolygon extends MinorAdj {
	final private static int _AddPolygonVersion = 9;
	final private static int _AddPolygonSource = 13;
	final private static int _AddPolygonRiver = 0;
	final private Loop3 _loopToAdd;

	public AddPolygon(final MyLogger logger, final boolean verboseDump,
			final File f, final String description, final int colorIdx,
			final List<LatLng3> latLngList0, final boolean isWater) {
		super(AdjType.ADD_POLYGON, verboseDump, f, description, colorIdx);
		final LatLng3[] latLngArray0 =
				latLngList0.toArray(new LatLng3[latLngList0.size()]);
		final LatLng3[] latLngArray = GcaSequenceStatics.trimEnd(latLngArray0);
		final int flag =
				Loop3Statics.createGenericFlag(/* clockwise= */isWater);
		final Loop3 loopToAdd = Loop3.getLoop(logger, /* id= */0, /* subId= */0,
				flag, /* ancestorId= */-1, latLngArray,
				CleanOpenLoop._StandardAllChecks, /* logChanges= */false,
				/* debug= */false);
		final boolean isCw = loopToAdd.isClockwise();
		if (isCw != isWater) {
			_loopToAdd = loopToAdd.createReverseLoop(logger);
		} else {
			_loopToAdd = loopToAdd;
		}
	}

	@Override
	protected Boolean isWater() {
		return _loopToAdd == null ? null :
				(_loopToAdd.isClockwise() ? Boolean.TRUE : Boolean.FALSE);
	}

	@Override
	public void coreProcessAdjSummaries(final MyLogger logger,
			final ArrayList<AdjSummary> adjSummaries) {
		/**
		 * We reject this AddPolygon if the polygon conflicts with an existing
		 * polygon. If it contains an existing polygon, we will delete that
		 * polygon. While looking for reasons to reject, we also find the
		 * smallest AdjSummary that surrounds me. If that's not null but has the
		 * same orientation as I do, we reject. If it is null, I better be land.
		 */
		final double loopSqNmi = _loopToAdd.getSqNmi();
		final Extent loopExtent = _loopToAdd.getFullExtent();
		final LatLng3 sampleLatLng = _loopToAdd.getZeroPoint();
		AdjSummary winningSummary = null;
		double winningSqNmi = Double.POSITIVE_INFINITY;
		final int nAdjSummaries = adjSummaries.size();
		final Iterator<AdjSummary> it0 = adjSummaries.iterator();
		final ArrayList<AdjSummary> summariesToDelete = new ArrayList<>();
		final String summaryString = getSummaryString();
		for (int k = 0; k < nAdjSummaries; ++k) {
			final AdjSummary summary = it0.next();
			final Loop3 summaryLoop = summary.getNominalLoop();
			final CrossingPair2 xingPair =
					_loopToAdd.findCrossingPair(logger, summaryLoop);
			if (xingPair != null) {
				/** We're done, and we're not happy. */
				MyLogger.wrn(logger,
						String.format("Minor Adj %s conflicts with %s!!.  Ignored.",
								summaryString, summaryLoop.getString()));
				return;
			}
			/** If I surround it, mark it for deletion. */
			final Extent summaryExtent = summaryLoop.getFullExtent();
			final double summarySqNmi = summaryLoop.getSqNmi();
			if (loopExtent.surrounds(summaryExtent, /* mustBeClean= */false)) {
				if (loopSqNmi >= summarySqNmi) {
					if (summaryLoop.borderOrInteriorContains(logger, sampleLatLng)) {
						summariesToDelete.add(summary);
						continue;
					}
				}
			}
			/** If it surrounds me, update winningSummary. */
			if (summaryExtent.surrounds(loopExtent, /* mustBeClean= */false)) {
				if (summarySqNmi >= loopSqNmi) {
					if (summaryLoop.borderOrInteriorContains(logger, sampleLatLng)) {
						if (summarySqNmi < winningSqNmi) {
							winningSummary = summary;
							winningSqNmi = summarySqNmi;
						}
					}
				}
			}
		}

		/** Check for compatible direction. */
		final boolean cw = _loopToAdd.isClockwise();
		if (winningSummary != null) {
			final Loop3 summaryLoop = winningSummary.getNominalLoop();
			if (cw == summaryLoop.isClockwise()) {
				MyLogger.wrn(logger, String.format(
						"Minor Adj %s has same direction with its encloser %s!!  Ignored.",
						summaryString, summaryLoop.getString()));
				return;
			}
		} else if (cw) {
			MyLogger.wrn(logger, String.format(
					"Minor Adj %s is top-level, but cw!!  Ignored.", summaryString));
			return;
		}

		/** Compute the Id. */
		int maxId = -1;
		for (final AdjSummary summary : adjSummaries) {
			maxId = Math.max(maxId, summary.getNominalLoop().getId());
		}
		final int loopId = maxId + 1;
		_loopToAdd.setIds(loopId, 0);
		final int newLevel;
		final int statedEncloserId;
		if (winningSummary != null) {
			final Loop3 winningLoop = winningSummary.getNominalLoop();
			final int winningId = winningLoop.getId();
			final int winningLevel = winningLoop.getLevel();
			newLevel = winningLevel + 1;
			statedEncloserId = winningId;
		} else {
			newLevel = 1;
			statedEncloserId = -1;
		}
		/** Validate the clockwise-ness of _loopToAdd. */
		final boolean oldCw = _loopToAdd.isClockwise();
		final boolean newCw = newLevel % 2 == 0;
		final Loop3 loopToAdd =
				newCw == oldCw ? _loopToAdd : _loopToAdd.createReverseLoop(logger);
		/** Work on the new one. */
		loopToAdd.setStatedEncloserId(statedEncloserId);
		final int newFlag = GshhsReaderStatics.computeFlag(newLevel,
				_AddPolygonVersion, _AddPolygonSource, _AddPolygonRiver);
		loopToAdd.setFlag(newFlag);
		/**
		 * This is the only type of MinorAdjustment that we have to adjust the
		 * set of AdjustmentSummaries. There already is an AdjustmentSummary for
		 * the other types, since they modify existing loops.
		 */
		final AdjSummary newAdjSummary = new AdjSummary(loopToAdd);
		newAdjSummary._resultLoop = loopToAdd;
		newAdjSummary.setNew(this);
		adjSummaries.add(newAdjSummary);
		/** Flag as deleted those that need to be deleted. */
		if (summariesToDelete.size() > 0) {
			final int nToDelete = summariesToDelete.size();
			for (int k = 0; k < nToDelete; ++k) {
				final AdjSummary toDelete = summariesToDelete.get(k);
				toDelete.setDeleted(this);
				final Loop3 loopToDelete = toDelete.getNominalLoop();
				final String toDeleteString = loopToDelete == null ?
						"Unknown Loop" : loopToDelete.getString();
				logger
						.wrn(String.format("Loop %s deleted because of %s!!.  Ignored.",
								toDeleteString, getSummaryString()));
			}
		}
	}

	@Override
	protected String getDataString(final List<Loop3> oldLoops) {
		final int nGcas = _loopToAdd == null ? 0 : _loopToAdd.getNGcas();
		final int nLatLngsToShow =
				_verboseDump ? nGcas : Math.min(nGcas, _MaxNLatLngsToShow);
		final GreatCircleArc[] gcaArray;
		if (nLatLngsToShow == nGcas && nGcas > 0) {
			gcaArray = _loopToAdd.getGcaArray();
		} else {
			gcaArray = new GreatCircleArc[nLatLngsToShow];
			final int lowerPart = nLatLngsToShow / 2;
			for (int k = 0; k < lowerPart; ++k) {
				gcaArray[k] = _loopToAdd.getGca(k);
			}
			final int upperPart = nLatLngsToShow - lowerPart;
			for (int k = 0; k < upperPart; ++k) {
				gcaArray[lowerPart + k] = _loopToAdd.getGca(nGcas - upperPart + k);
			}
		}

		final ColorUtils.ColorGrouping colorGrouping = _adjType._colorGrouping;
		final ColorUtils.ColorGroup colorGroup =
				ColorUtils.getColorGroup(colorGrouping);
		final int nColors = colorGroup._nameAndRgbs.length;
		final String colorName0 = ColorUtils.getColorName(colorGrouping, 0);
		final String colorName1 =
				ColorUtils.getColorName(colorGrouping, nColors / 2);
		final String[] colorNames = new String[] { colorName0, colorName1 };
		final String s = CartesianUtil.getXmlDump(gcaArray, colorNames,
				/* numberEdgeInc= */1, /* GcaFilter= */null);
		return s;
	}

	@Override
	protected String getSummaryString() {
		final GreatCircleArc[] gcaArray = _loopToAdd.getGcaArray();
		final GreatCircleArc gca0 = gcaArray[0];
		final String s = String.format("AddPolygon-%s(%s), gca0:%s.",
				isWater() ? "WATER" : "LAND", _description, gca0.getString());
		return s;
	}

	@Override
	protected String extraDataString() {
		final String s = String.format("n[%d] %s", _loopToAdd.getNLatLngs(),
				_loopToAdd.isClockwise() ? "CW" : "CCW");
		return s;
	}

	public Loop3 getLoopToAdd() {
		return _loopToAdd;
	}

	@Override
	public Extent getExtent() {
		if (_loopToAdd == null) {
			return Extent.getUnsetExtent();
		}
		return _loopToAdd.getFullExtent();
	}

	@Override
	public int littleCompareTo(final MinorAdj minorAdj) {
		final int compareValue = Loop3._SizeStructure.compare(_loopToAdd,
				((AddPolygon) minorAdj)._loopToAdd);
		return compareValue;
	}

}