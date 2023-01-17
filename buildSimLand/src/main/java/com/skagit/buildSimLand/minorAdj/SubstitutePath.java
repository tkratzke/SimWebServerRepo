package com.skagit.buildSimLand.minorAdj;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.skagit.util.CartesianUtil;
import com.skagit.util.colorUtils.ColorUtils;
import com.skagit.util.geometry.crossingPair.CheckForUnknownPairs;
import com.skagit.util.geometry.crossingPair.CrossingPair2;
import com.skagit.util.geometry.gcaSequence.CleanOpenLoop;
import com.skagit.util.geometry.gcaSequence.GcaSequenceStatics;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.geometry.gcaSequence.Loop3Statics;
import com.skagit.util.geometry.gcaSequence.NonLoop;
import com.skagit.util.geometry.geoMtx.GeoMtx;
import com.skagit.util.geometry.geoMtx.xing1Bundle.Xing1Bundle;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;

public class SubstitutePath extends MinorAdj {
	final private NonLoop _nonLoop;

	public SubstitutePath(final MyLogger logger, final boolean verboseDump,
			final File f, final String description, final int colorIdx,
			final List<LatLng3> latLngList) {
		super(AdjType.SUBSTITUTE_PATH, verboseDump, f, description, colorIdx);
		_nonLoop = new NonLoop(logger, /* id= */0, latLngList);
	}

	@Override
	protected Boolean isWater() {
		return null;
	}

	public LatLng3[] getLatLngArray() {
		return _nonLoop.getLatLngArray();
	}

	private GreatCircleArc.Projection[] getElAndEmProjections(
			final MyLogger logger, final List<Loop3> oldLoops) {
		final GreatCircleArc[] substituteGcaArray = _nonLoop.getGcaArray();
		final int nSubstituteGcas = substituteGcaArray.length;
		final LatLng3 latLng0 = substituteGcaArray[0].getLatLng0();
		final LatLng3 latLngN =
				substituteGcaArray[nSubstituteGcas - 1].getLatLng1();
		final GeoMtx gcaMtxForFindingProjections =
				Loop3Statics.createGcaMtx(logger, oldLoops);
		final GreatCircleArc.Projection elPrj =
				gcaMtxForFindingProjections.findProjection(latLng0);
		final GreatCircleArc.Projection emPrj =
				gcaMtxForFindingProjections.findProjection(latLngN);
		return new GreatCircleArc.Projection[] { elPrj, emPrj };
	}

	@Override
	public void coreProcessAdjSummaries(final MyLogger logger,
			final ArrayList<AdjSummary> adjSummaries) {
		final int nAdjSummaries =
				adjSummaries == null ? 0 : adjSummaries.size();
		final GreatCircleArc[] substituteGcaArray = _nonLoop.getGcaArray();
		final int nSubstituteGcas = substituteGcaArray.length;

		final ArrayList<Loop3> oldLoops = new ArrayList<>(adjSummaries.size());
		for (int k = 0; k < nAdjSummaries; ++k) {
			final AdjSummary adjSummary = adjSummaries.get(k);
			if (adjSummary.getDeleted()) {
				continue;
			}
			final Loop3 loop = adjSummary.getNominalLoop();
			oldLoops.add(loop);
		}
		final GreatCircleArc.Projection[] elAndEmProjections =
				getElAndEmProjections(logger, oldLoops);

		/**
		 * Call the first point of the SubstitutePath elBridge1. Find the
		 * closest point among all of the loops' points, to elBridge1 and call
		 * it elBridge0. Similarly, call the last point of the SubstitutePath
		 * emBridge0 and let emBridge1 be the point among all of the loops'
		 * points, that is closest to emBridge0.
		 */
		final LatLng3 elBridge1 = substituteGcaArray[0].getLatLng0();
		final LatLng3 emBridge0 =
				substituteGcaArray[nSubstituteGcas - 1].getLatLng1();
		final GreatCircleArc.Projection elPrj = elAndEmProjections[0];
		final GreatCircleArc.Projection emPrj = elAndEmProjections[1];
		MyLogger.out(logger, String.format("\n\tElPrj%s\n\tEmPrj%s",
				elPrj.getString(), emPrj.getString()));
		final GreatCircleArc elOwningGca = elPrj.getOwningGca();
		final GreatCircleArc emOwningGca = emPrj.getOwningGca();
		final Loop3 elLoop = (Loop3) elOwningGca.getGcaSequence();
		final Loop3 emLoop = (Loop3) emOwningGca.getGcaSequence();
		/** It's a bad pair if the two are not closest to the same loop. */
		if ((elLoop != emLoop) || !elLoop.checkLinkedList()) {
			return;
		}
		oldLoops.clear();
		oldLoops.trimToSize();

		/**
		 * Build the sequence of Edges. Start with elBridge, substitutePath,
		 * emBridge. If that conflicts with any loop, we give up.
		 */
		Extent extentToCheck = Extent.getUnsetExtent();
		final ArrayList<GreatCircleArc> newGcaList = new ArrayList<>();
		final LatLng3 elBridge0 = elOwningGca.getLatLng0();
		if (!elBridge1.equals(elBridge0)) {
			final GreatCircleArc elBridge =
					GreatCircleArc.CreateGca(elBridge0, elBridge1);
			newGcaList.add(elBridge);
			extentToCheck = extentToCheck.buildExtension(elBridge0, elBridge1);
		}
		for (int k = 0; k < nSubstituteGcas; ++k) {
			final GreatCircleArc gca = substituteGcaArray[k].clone();
			newGcaList.add(gca);
			extentToCheck =
					extentToCheck.buildExtension(gca.getLatLng0(), gca.getLatLng1());
		}
		final LatLng3 emBridge1 = emOwningGca.getLatLng1();
		if (!emBridge0.equals(emBridge1)) {
			final GreatCircleArc emBridge =
					GreatCircleArc.CreateGca(emBridge0, emBridge1);
			newGcaList.add(emBridge);
			extentToCheck = extentToCheck.buildExtension(emBridge0, emBridge1);
		}

		/**
		 * Find conflicts with newGcaList; first from the loops not equal to
		 * elLoop.
		 */
		GeoMtx geoMtx0 = new GeoMtx(
				newGcaList.toArray(new GreatCircleArc[newGcaList.size()]));
		for (int k = 0; k < nAdjSummaries; ++k) {
			final AdjSummary adjSummary = adjSummaries.get(k);
			if (adjSummary.getDeleted()) {
				continue;
			}
			final Loop3 loop = adjSummary.getNominalLoop();
			if (loop == elLoop) {
				continue;
			}
			if (!loop.getFullExtent().overlaps(extentToCheck)) {
				continue;
			}
			final GeoMtx geoMtx1 = loop.getGcaMtx();
			final CheckForUnknownPairs checkForUnknownPairs =
					CheckForUnknownPairs.CreatePvsNxtOnly0();
			final Xing1Bundle xing1Bndl =
					new Xing1Bundle(checkForUnknownPairs, /* getOnlyOne= */true);
			Xing1Bundle.updateXing1Bundle(xing1Bndl, geoMtx0, geoMtx1);
			final CrossingPair2 xingPair = xing1Bndl.getCrossingPair();
			if (xingPair != null) {
				MyLogger.wrn(logger,
						String.format("SubstitutePath(1) %s ignored: xingPair %s.",
								getId(), xingPair.getString()));
				return;
			}
			loop.freeMemory();
		}
		geoMtx0 = null;

		/** To check elLoop itself, finish the loop. */
		for (GreatCircleArc nextGca = emOwningGca.getNxt();
				nextGca != elOwningGca; nextGca = nextGca.getNxt()) {
			newGcaList.add(nextGca.clone());
		}
		final int nNewGcas = newGcaList.size();
		final GreatCircleArc[] newLoopGcaArray =
				newGcaList.toArray(new GreatCircleArc[nNewGcas]);
		final Loop3 newLoop = Loop3.getLoop(logger, elLoop.getId(),
				elLoop.getSubId(), elLoop.getFlag(), elLoop.getAncestorId(),
				newLoopGcaArray, CleanOpenLoop._StandardAllChecks,
				/* logChanges= */false, /* debug= */false);
		if (newLoop == null || !newLoop.isValid() ||
				newLoop.getNGcas() != nNewGcas) {
			MyLogger.wrn(logger, String
					.format("SubstitutePath(2) %s ignored: lost an edge.", getId()));
			return;
		}
		MyLogger.out(logger,
				String.format("%s processing:\n\tLoop[%s] replaced by:\n\tLoop[%s]",
						_description, elLoop.getSmallString(),
						newLoop.getSmallString()));

		/** Put newLoop away. */
		final Iterator<AdjSummary> it1 = adjSummaries.iterator();
		boolean foundWhereToPutIt = false;
		for (int k = 0; k < nAdjSummaries; ++k) {
			final AdjSummary adjSummary = it1.next();
			if (elLoop == adjSummary.getNominalLoop()) {
				/** All done, and we know where to put newLoop. */
				adjSummary._resultLoop = newLoop;
				adjSummary.setChanged(this);
				foundWhereToPutIt = true;
				break;
			}
		}
		assert foundWhereToPutIt : "Couldn't put away SubstitutePath-adjusted Loop.";
	}

	@Override
	protected String getDataString(final List<Loop3> oldLoops) {
		final int nGcas = _nonLoop == null ? 0 : _nonLoop.getNGcas();
		final int nLatLngsToShow =
				_verboseDump ? nGcas : Math.min(nGcas, _MaxNLatLngsToShow);
		final GreatCircleArc[] gcaArray;
		if (nLatLngsToShow == nGcas && nGcas > 0) {
			if (_verboseDump && oldLoops != null) {
				final GreatCircleArc[] oldGcaArray = _nonLoop.getGcaArray();
				final int nOldGcas = oldGcaArray.length;
				gcaArray = new GreatCircleArc[1 + nOldGcas + 1];
				final GreatCircleArc.Projection[] elAndEmProjections =
						getElAndEmProjections(/* logger= */null, oldLoops);
				final GreatCircleArc.Projection elPrj = elAndEmProjections[0];
				gcaArray[0] = GreatCircleArc.CreateGca(elPrj.getClosestPointOnGca(),
						_nonLoop.getGca(0).getLatLng0());
				System.arraycopy(oldGcaArray, 0, gcaArray, 1, nOldGcas);
				final GreatCircleArc.Projection emPrj = elAndEmProjections[1];
				gcaArray[nOldGcas + 1] = GreatCircleArc.CreateGca(
						_nonLoop.getGca(nOldGcas - 1).getLatLng1(),
						emPrj.getClosestPointOnGca());
			} else {
				gcaArray = _nonLoop.getGcaArray();
			}
		} else {
			gcaArray = new GreatCircleArc[nLatLngsToShow];
			final int lowerPart = nLatLngsToShow / 2;
			for (int k = 0; k < lowerPart; ++k) {
				gcaArray[k] = _nonLoop.getGca(k);
			}
			final int upperPart = nLatLngsToShow - lowerPart;
			for (int k = 0; k < upperPart; ++k) {
				gcaArray[lowerPart + k] = _nonLoop.getGca(nGcas - upperPart + k);
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
	public int littleCompareTo(final MinorAdj minorAdj) {
		final int compareValue = GcaSequenceStatics._ByLatLngArrays
				.compare(_nonLoop, ((SubstitutePath) minorAdj)._nonLoop);
		return compareValue;
	}

	@Override
	protected String getSummaryString() {
		final GreatCircleArc[] gcaArray = _nonLoop.getGcaArray();
		final int nGcas = gcaArray.length;
		final GreatCircleArc gca0 = gcaArray[0];
		final GreatCircleArc gcaN = gcaArray[nGcas - 1];
		final String s = String.format(
				"SubstitutePath(%s)\n\tGca0:%s→(%d Gcas)→\n\tGcaN:%s from %s.",
				_description, gca0.getDisplayString(), nGcas,
				gcaN.getDisplayString(), _description);
		return s;
	}

	@Override
	protected String extraDataString() {
		return getSummaryString();
	}

	public int getId() {
		return _nonLoop.getId();
	}

	@Override
	public Extent getExtent() {
		return _nonLoop.getExtent();
	}

}