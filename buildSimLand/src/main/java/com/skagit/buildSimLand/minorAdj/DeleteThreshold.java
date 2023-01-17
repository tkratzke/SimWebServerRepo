package com.skagit.buildSimLand.minorAdj;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.skagit.util.Constants;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;

public class DeleteThreshold extends MinorAdj {

	final private double _maxSqNmi;
	final private int _level;

	public DeleteThreshold(final MyLogger logger, final File f,
			final String description, final int colorIdx, final int level,
			final double maxSqNmi) {
		super(AdjType.DELETE_THRESHOLD, /* verboseDump= */false, f, description,
				colorIdx);
		_maxSqNmi = maxSqNmi;
		_level = level;
	}

	@Override
	protected Boolean isWater() {
		return null;
	}

	public double getMaxAreaSqNmi() {
		return _maxSqNmi;
	}

	public int getLevel() {
		return _level;
	}

	@Override
	public void coreProcessAdjSummaries(final MyLogger logger,
			final ArrayList<AdjSummary> adjSummaries) {
		final int nAdjSummaries =
				adjSummaries == null ? 0 : adjSummaries.size();
		final ArrayList<AdjSummary> mainAdjSummaries = new ArrayList<>();
		for (int k1 = 0; k1 < nAdjSummaries; ++k1) {
			final AdjSummary adjSummary1 = adjSummaries.get(k1);
			if (adjSummary1.getDeleted()) {
				continue;
			}
			final Loop3 loop1 = adjSummary1.getNominalLoop();
			if (loop1 == null) {
				continue;
			}
			final int loopLevel1 = loop1.getLevel();
			if (loopLevel1 < _level) {
				continue;
			}
			if (loopLevel1 == _level) {
				final double sqNmi = loop1.getSqNmi();
				if (sqNmi <= _maxSqNmi) {
					adjSummary1.setDeleted(this);
					mainAdjSummaries.add(adjSummary1);
				}
			}
		}
		/** Delete anything that is within any of mainClusters. */
		final int nMainSummaries = mainAdjSummaries.size();
		final boolean mustBeClean = true;
		final Iterator<AdjSummary> it2 = adjSummaries.iterator();
		K2_LOOP: for (int k2 = 0; k2 < nAdjSummaries; ++k2) {
			final AdjSummary adjSummary2 = it2.next();
			if (adjSummary2.getDeleted()) {
				continue;
			}
			final Loop3 loop2 = adjSummary2.getNominalLoop();
			if (loop2 == null) {
				continue;
			}
			final int level2 = loop2.getLevel();
			if (level2 <= _level) {
				continue;
			}
			final Extent loop2FullExtent = loop2.getFullExtent();
			for (int k3 = 0; k3 < nMainSummaries; ++k3) {
				final AdjSummary adjSummary3 = mainAdjSummaries.get(k3);
				final Loop3 loop3 = adjSummary3.getNominalLoop();
				final Extent loop3FullExtent = loop3.getFullExtent();
				if (!loop3FullExtent.surrounds(loop2FullExtent, mustBeClean)) {
					continue;
				}
				final LatLng3 samplePoint = loop2.getZeroPoint();
				if (loop3.interiorContains(logger, samplePoint)) {
					adjSummary2.setDeleted(this);
					continue K2_LOOP;
				}
			}
		}
	}

	@Override
	public int littleCompareTo(final MinorAdj minorAdj) {
		final DeleteThreshold deleteThreshold = (DeleteThreshold) minorAdj;
		if (_level < deleteThreshold._level) {
			return -1;
		}
		if (_level > deleteThreshold._level) {
			return 1;
		}
		if (_maxSqNmi < deleteThreshold._maxSqNmi) {
			return -1;
		}
		if (_maxSqNmi > deleteThreshold._maxSqNmi) {
			return 1;
		}
		return 0;
	}

	@Override
	protected String getDataString(final List<Loop3> oldLoops) {
		final String s =
				String.format("Level[%d] MaxSqNmi[%f]", _level, _maxSqNmi);
		return s;
	}

	@Override
	protected String getSummaryString() {
		final String s = String.format(
				"DeleteThreshold(%s) All Level[%d] polygons of at most %f sqNmi",
				_description, _level, _maxSqNmi);
		return s;
	}

	@Override
	protected String extraDataString() {
		final String s = String.format("Level[%d] %c %f sqNmi", _level,
				Constants._LessThanOrEqual, _maxSqNmi);
		return s;
	}

	@Override
	public Extent getExtent() {
		return Extent.createWholeWorldExtent();
	}

}