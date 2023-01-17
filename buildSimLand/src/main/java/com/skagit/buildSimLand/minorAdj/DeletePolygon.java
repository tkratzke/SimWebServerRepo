package com.skagit.buildSimLand.minorAdj;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.skagit.util.CartesianUtil;
import com.skagit.util.geometry.gcaSequence.GcaSequenceStatics;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;

public class DeletePolygon extends MinorAdj {

	final private Loop3 _loopToClear;

	DeletePolygon(final MyLogger logger, final File f,
			final String description, final int colorIdx,
			final List<LatLng3> latLngList0) {
		super(AdjType.DELETE_POLYGON, /* verboseDump= */false, f, description,
				colorIdx);
		final LatLng3[] latLngArray =
				latLngList0.toArray(new LatLng3[latLngList0.size()]);
		_loopToClear = Loop3.getLoop(logger, /* id= */0, /* subId= */0,
				/* flag= */-1, /* ancestorId= */-1, latLngArray,
				/* logChanges= */false, /* debug= */false);
	}

	public DeletePolygon(final MyLogger logger, final File f,
			final String description, final int colorNumber,
			final Loop3 loopToClear) {
		super(AdjType.DELETE_POLYGON, /* verboseDump= */false, f, description,
				colorNumber);
		_loopToClear = loopToClear;
	}

	@Override
	protected Boolean isWater() {
		return _loopToClear == null ? null :
				(_loopToClear.isClockwise() ? Boolean.TRUE : Boolean.FALSE);
	}

	@Override
	public Extent getExtent() {
		return _loopToClear.getFullExtent();
	}

	@Override
	public void coreProcessAdjSummaries(final MyLogger logger,
			final ArrayList<AdjSummary> adjSummaries) {
		final int nAdjSummaries = adjSummaries.size();
		for (int k = 0; k < nAdjSummaries; ++k) {
			final AdjSummary adjSummary = adjSummaries.get(k);
			if (adjSummary.getDeleted()) {
				continue;
			}
			final Loop3 hisLoop = adjSummary.getNominalLoop();
			if (hisLoop == null) {
				continue;
			}
			if (_loopToClear.surrounds(logger, hisLoop)) {
				adjSummary.setDeleted(this);
			}
		}
	}

	@Override
	public int littleCompareTo(final MinorAdj minorAdj) {
		final int compareValue = Loop3._SizeStructure.compare(_loopToClear,
				((DeletePolygon) minorAdj)._loopToClear);
		return compareValue;
	}

	@Override
	protected String getDataString(final List<Loop3> oldLoops) {
		final LatLng3[] latLngArray = _loopToClear.getLatLngArray();
		final boolean connectLastToFirstIn = true;
		final boolean forceLinkedList = false;
		final GreatCircleArc[] gcaArray = GcaSequenceStatics
				.createGcaArray(latLngArray, connectLastToFirstIn, forceLinkedList);
		final String[] colorNames = null;
		final int numberEdgeInc = 1;
		final String s = "\n" + CartesianUtil.getXmlDump(gcaArray, colorNames,
				numberEdgeInc, /* GcaFilter= */null);
		return s;
	}

	@Override
	protected String getSummaryString() {
		final String s = String.format("DeletePolygon(%s), within %s",
				_description, _loopToClear.getSmallString());
		return s;
	}

	@Override
	protected String extraDataString() {
		final String s = String.format("n[%d] %f sqNmi",
				_loopToClear.getNLatLngs(), _loopToClear.getSqNmi());
		return s;
	}

	public Loop3 getLoopToClear() {
		return _loopToClear;
	}

}