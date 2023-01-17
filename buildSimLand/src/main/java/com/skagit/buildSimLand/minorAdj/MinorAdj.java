package com.skagit.buildSimLand.minorAdj;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.skagit.util.colorUtils.ColorUtils;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;

public abstract class MinorAdj {
	protected final static int _MaxNLatLngsToShow = 100;

	final public AdjType _adjType;
	final public boolean _verboseDump;
	final public File _f;
	final public String _description;
	final public int _colorIdx;

	protected MinorAdj(final AdjType adjType, final boolean verboseDump, final File f, final String description,
			final int colorIdx) {
		_adjType = adjType;
		_verboseDump = verboseDump;
		_f = f;
		_description = description;
		_colorIdx = colorIdx;
	}

	public static final Comparator<MinorAdj> _StandardComparator = new Comparator<>() {

		@Override
		public int compare(final MinorAdj minorAdj0, final MinorAdj minorAdj1) {
			if ((minorAdj0 == null) != (minorAdj1 == null)) {
				return minorAdj0 == null ? -1 : 1;
			}
			if (minorAdj0 == null) {
				return 0;
			}
			final int compareValue = minorAdj0.getAdjType().compareTo(minorAdj1.getAdjType());
			if (compareValue != 0) {
				return compareValue;
			}
			return minorAdj0.littleCompareTo(minorAdj1);
		}
	};

	final public String getColorName() {
		return ColorUtils.getColorName(_adjType._colorGrouping, _colorIdx);
	}

	protected abstract String getDataString(List<Loop3> oldLoops);

	protected abstract String getSummaryString();

	protected abstract String extraDataString();

	public abstract Extent getExtent();

	public abstract int littleCompareTo(MinorAdj minorAdj);

	public abstract void coreProcessAdjSummaries(MyLogger logger, ArrayList<AdjSummary> adjSummaries);

	public String getString() {
		final String s = String.format("%s %s(%s)", _adjType.name(), _description, extraDataString());
		return s;
	}

	public String getString(final List<Loop3> oldLoops) {
		String s = "";
		final Boolean isWaterBoolean = isWater();
		if (isWaterBoolean == null) {
			s += String.format("\n<Type>%s</Type>", _adjType.name());
		} else {
			s += String.format("\n<Type>%s %s</Type>", _adjType.name(), isWaterBoolean ? "WATER" : "LAND");
		}
		s += String.format("\n<Description>%s</Description>", _description);
		s += String.format("\n%s\n", getDataString(oldLoops));
		return s;
	}

	protected abstract Boolean isWater();

	@Override
	public String toString() {
		return getString();
	}

	public static ArrayList<Loop3> processMinorAdjs(final MyLogger logger, final File logDir,
			final List<Loop3> oldLoops, final MinorAdj[] adjs) {
		final int nOldLoops = oldLoops == null ? 0 : oldLoops.size();
		/** Create the initial set of AdjSummaries. */
		final ArrayList<AdjSummary> adjSummaries = new ArrayList<>(nOldLoops);
		for (int k = 0; k < nOldLoops; ++k) {
			final Loop3 loop = oldLoops.get(k);
			adjSummaries.add(new AdjSummary(loop));
		}
		final int nAdjs = adjs == null ? 0 : adjs.length;
		for (int k0 = 0; k0 < nAdjs; ++k0) {
			final int nBefore = adjSummaries.size();
			final MinorAdj adj = adjs[k0];
			logger.out(String.format("Starting Minor Adj %03d of %03d: %s.", k0, nAdjs, adj.getSummaryString()));
			adj.coreProcessAdjSummaries(logger, adjSummaries);
			/**
			 * adjSummaries is now a mix of new loops, loops that have been modified, and
			 * loops that are marked for deletion. Here, we delete the AdjSummaries that
			 * correspond to loops that are marked for deletion, and log information about
			 * them. The reason we get rid of the ones marked for deletion is to avoid
			 * interference with subsequent adjustments.
			 */
			final ArrayList<AdjSummary> survivingAdjSummaries = new ArrayList<>();
			final int nAdjSummaries = adjSummaries.size();
			for (int k1 = 0; k1 < nAdjSummaries; ++k1) {
				final AdjSummary adjSummary = adjSummaries.get(k1);
				final Loop3 nominalLoop = adjSummary.getNominalLoop();
				if (!adjSummary.getDeleted()) {
					if (adjSummary.getChanged()) {
						logger.out(String.format((adjSummary.getNew() ? "New" : "Changed") + "Loop %s",
								nominalLoop.getSmallString()));
						adjSummary.reset();
					}
					survivingAdjSummaries.add(adjSummary);
				} else {
					logger.out(String.format("Lost %s", nominalLoop.getSmallString()));
					adjSummary.freeMemory();
				}
			}
			adjSummaries.clear();
			adjSummaries.addAll(survivingAdjSummaries);
			final int nAfter = adjSummaries.size();
			logger.out(String.format("Ending Minor Adj %03d.  Before/After Processing[%d/%d].\n", k0, nBefore, nAfter));
		}

		/** All MinorAdjs have been processed. Can gather loopsOut now. */
		final ArrayList<Loop3> loopsOut = new ArrayList<>(nOldLoops);
		final int nAdjSummaries = adjSummaries.size();
		for (int k = 0; k < nAdjSummaries; ++k) {
			final AdjSummary adjSummary = adjSummaries.get(k);
			loopsOut.add(adjSummary.getNominalLoop());
		}
		return loopsOut;
	}

	final private static String _Format = "Loop contains %s.  Id[%d] NGcas[%d] SqNmi[%.4f] Level[%d] Loop Extent: %s";

	protected static AdjSummary getInnermostContainer(final MyLogger logger, final ArrayList<AdjSummary> adjSummaries,
			final LatLng3 latLng) {
		final int nSummaries = adjSummaries == null ? 0 : adjSummaries.size();
		final String latLngString = latLng.getString(/* nDigits= */4);
		logger.out(String.format("Finding innermost that contains %s", latLngString));
		double winningSqNmi = Double.POSITIVE_INFINITY;
		AdjSummary winningSummary = null;
		for (int k = 0; k < nSummaries; ++k) {
			final AdjSummary summary = adjSummaries.get(k);
			if (summary.getDeleted()) {
				continue;
			}
			final Loop3 nominalLoop = summary.getNominalLoop();
			final double sqNmi = nominalLoop.getSqNmi();
			if (sqNmi < winningSqNmi) {
				final Extent fullExtent = nominalLoop.getFullExtent();
				if (fullExtent.contains(latLng)) {
					final boolean contains = nominalLoop.interiorContains(logger, latLng);
					if (contains) {
						final int loopId = nominalLoop.getId();
						final int nGcas = nominalLoop.getNGcas();
						final int loopLevel = nominalLoop.getLevel();
						final String s2 = String.format(_Format, //
								latLngString, loopId, nGcas, sqNmi, loopLevel, fullExtent.getString());
						logger.out(s2);
						winningSummary = summary;
						winningSqNmi = sqNmi;
					}
				}
			}
		}
		return winningSummary;
	}

	public String getDescription() {
		return _description;
	}

	public AdjType getAdjType() {
		return _adjType;
	}

	public static String getDescriptionFromFile(final File f, final int k) {
		final String description = String.format("%s*%s[%02d]", f.getParentFile().getName(), f.getName(), k);
		return description;
	}

}
