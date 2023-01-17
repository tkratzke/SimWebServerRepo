package com.skagit.buildSimLand.minorAdj;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;

public class DeleteInnermost extends MinorAdj {

	final private LatLng3 _latLng;
	final private double _maxSqNmi;

	public DeleteInnermost(final MyLogger logger, final File f,
			final String description, final int colorIdx, final LatLng3 latLng,
			final double maxSqNmi) {
		super(AdjType.DELETE_INNERMOST, /* verboseDump= */false, f, description,
				colorIdx);
		_latLng = latLng;
		_maxSqNmi = maxSqNmi;
	}

	@Override
	protected Boolean isWater() {
		return null;
	}

	public LatLng3 getLatLng() {
		return _latLng;
	}

	@Override
	public void coreProcessAdjSummaries(final MyLogger logger,
			final ArrayList<AdjSummary> adjSummaries) {
		final int nSummaries = adjSummaries == null ? 0 : adjSummaries.size();
		final String latLngString = _latLng.getString(4);
		final AdjSummary winningSummary =
				MinorAdj.getInnermostContainer(logger, adjSummaries, _latLng);
		if (winningSummary != null) {
			/** Print out some info. */
			final Loop3 winningLoop = winningSummary.getNominalLoop();
			final int winningLoopId = winningLoop.getId();
			final double winningSqNmi = winningLoop.getSqNmi();
			final int winnnigLoopLevel = winningLoop.getLevel();
			final int winningNGcas = winningLoop.getNGcas();
			final Extent winningExtent = winningLoop.getFullExtent();
			if (winningSqNmi > _maxSqNmi) {
				final String format1 = _adjType.name() +
						", For %s, we cannot delete: Id[%d] NGcas[%d] SqNmi[%.4f] Level[%d] %s";
				final String s1 = String.format(format1, //
						latLngString, winningLoopId, winningNGcas, winningSqNmi,
						winnnigLoopLevel, winningExtent.getString());
				MyLogger.wrn(logger, s1);
				return;
			}
			final String format1 = _adjType.name() +
					", For %s, we are deleting: Id[%d] NGcas[%d] Level[%d] %s";
			final String s1 = String.format(format1, //
					latLngString, winningLoopId, winningNGcas, winnnigLoopLevel,
					winningExtent.getString());
			MyLogger.out(logger, s1);
			winningSummary.setDeleted(this);
			/** Delete everything that has a point within winningLoop. */
			for (int k = 0; k < nSummaries; ++k) {
				final AdjSummary summary = adjSummaries.get(k);
				if (summary.getDeleted()) {
					continue;
				}
				final Loop3 loop = summary.getNominalLoop();
				final int level = loop.getLevel();
				if (level <= winnnigLoopLevel) {
					continue;
				}
				final Extent loopExtent = loop.getFullExtent();
				if (!winningExtent.surrounds(loopExtent, /* mustBeClean= */true)) {
					continue;
				}
				final double loopSqNmi = loop.getSqNmi();
				if ((winningSqNmi <= loopSqNmi) || !winningLoop.interiorContains(logger, loop.getZeroPoint())) {
					continue;
				}
				final String s2 =
						String.format("\n\tand its enclosee %s", loop.getSmallString());
				MyLogger.out(logger, s2);
				summary.setDeleted(this);
			}
		} else {
			final String format1 = "Found no polygon containing %s.";
			final String s1 = String.format(format1, //
					latLngString);
			MyLogger.wrn(logger, s1);
		}
	}

	@Override
	public int littleCompareTo(final MinorAdj minorAdj) {
		final int compareValue = LatLng3._ByLatThenLng.compare(_latLng,
				((DeleteInnermost) minorAdj)._latLng);
		return compareValue;
	}

	@Override
	protected String getDataString(final List<Loop3> oldLoops) {
		final String colorName = "Lime";
		final String s = _latLng.getPointToDrawString(colorName);
		return s;
	}

	@Override
	protected String getSummaryString() {
		final String s =
				String.format(_adjType.name() + ", %s", _latLng.getString());
		return s;
	}

	@Override
	protected String extraDataString() {
		return _latLng.getString();
	}

	@Override
	public Extent getExtent() {
		final double lat = _latLng.getLat();
		final double lng = _latLng.getLng();
		final double[] llrh = new double[] { lng - 0.5,
				Math.max(-90d, lat - 0.5), lng + 0.5, Math.min(90d, lat + 0.5) };
		return new Extent(llrh);
	}
}