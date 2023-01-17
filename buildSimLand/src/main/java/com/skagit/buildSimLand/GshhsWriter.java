package com.skagit.buildSimLand;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import com.skagit.util.StaticUtilities;
import com.skagit.util.geometry.IntLoopData;
import com.skagit.util.geometry.LoopAndIldUtils;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.gshhs.GshhsReader;
import com.skagit.util.gshhs.GshhsReader2;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;

public class GshhsWriter {
	/** For convenience: */

	final static private double[] _NorthernDivisions = {
			170d, -129d, -117d, -93d, -81.5, -66d
	};
	final static private double[] _SouthernDivisions = {
			-180d
	};

	public static void dumpLoops(final List<Loop3> loops,
			final String outputFilePath) {
		final ArrayList<IntLoopData> ilds = LoopAndIldUtils.getIlds(loops,
				/* destroyInput= */false);
		dumpIlds(ilds, outputFilePath);
		StaticUtilities.clearList(ilds);
	}

	public static void dumpIlds(final List<IntLoopData> ilds,
			final String outputFilePath) {
		final int nNorthExtents = _NorthernDivisions.length;
		final int nSouthExtents = _SouthernDivisions.length;
		final int nExtents = 1 + nNorthExtents + nSouthExtents;
		final Extent[] extents = new Extent[nExtents];
		for (int k = 0; k < nExtents; ++k) {
			final double lt, rt, lo, hi;
			if (k == 0) {
				lt = -180d;
				lo = -90d;
				rt = 180d;
				hi = 90d;
			} else {
				final int kk = k - 1;
				if (kk < nNorthExtents) {
					lt = _NorthernDivisions[kk];
					lo = 0d;
					rt = _NorthernDivisions[(kk + 1) % nNorthExtents];
					hi = 90d;
				} else {
					final int index = kk - nNorthExtents;
					lt = _SouthernDivisions[index];
					lo = -90d;
					rt = _SouthernDivisions[(index + 1) % nSouthExtents];
					hi = 0d;
				}
			}
			extents[k] = new Extent(lt, lo, rt, hi);
		}
		final int[][] stats = new int[nExtents][2];
		/**
		 * Partition the Loops, and build some counts. Also, keep track of the
		 * border Loops. Automatically put the top 50 in the group of bigs. The
		 * group of bigs, other than these, refers to the ones that sit on a border.
		 */
		@SuppressWarnings("unchecked")
		final ArrayList<IntLoopData>[] partition = new ArrayList[1 + nExtents];
		final int nBigOnes = 50;
		int k = 0;
		for (final IntLoopData ild : ilds) {
			int iExtent = 0;
			/**
			 * iExtent == 0 means "this one is big." Recompute iExtent if we already
			 * have 50.
			 */
			if (k++ >= nBigOnes) {
				final Extent ildExtent = ild.createExtent();
				for (int iExtent2 = 1; iExtent2 < nExtents; ++iExtent2) {
					final boolean mustBeClean = true;
					if (extents[iExtent2].surrounds(ildExtent, mustBeClean)) {
						iExtent = iExtent2;
						break;
					}
				}
			}
			/** Put it in the iExtent set. */
			ArrayList<IntLoopData> partiteSet = partition[iExtent];
			if (partiteSet == null) {
				partiteSet = partition[iExtent] = new ArrayList<>();
			}
			partiteSet.add(ild);
			++stats[iExtent][0];
			stats[iExtent][1] += ild.getNGcas() + 1;
		}
		/** Set up the output File. */
		final int nIntsInHeader = GshhsReader.Header._NIntsInHeader;
		final File outputFile = new File(outputFilePath);
		try (final OutputStream outStream = new FileOutputStream(outputFile)) {
			try (final DataOutputStream dos = new DataOutputStream(outStream)) {
				/** Put in the flag that says this is a converted file. */
				try {
					int nIntsInContent = 0;
					for (int iExtent = 0; iExtent < nExtents; ++iExtent) {
						final int nLoops = stats[iExtent][0];
						final int nTotalLatLngsInClosedLoops = stats[iExtent][1];
						nIntsInContent += GshhsReader2._NIntsInClusterHeader
								+ nLoops * nIntsInHeader + 2 * nTotalLatLngsInClosedLoops;
					}
					dos.writeInt(-nIntsInContent);
				} catch (final IOException e1) {
				}
				int nWrittenOut = 0;
				int nTotalLatLngsWrittenOut = 0;
				for (int iExtent = 0; iExtent < nExtents; ++iExtent) {
					/** Write out the cluster header. */
					try {
						final Extent extent = extents[iExtent];
						final int ltUnits;
						final int rtUnits;
						if (extent.getLngRng() == 360d) {
							ltUnits = -LatLng3._HalfCircleOfNewUnits;
							rtUnits = LatLng3._HalfCircleOfNewUnits;
						} else {
							final double lt = extent.getLeftLng();
							final double rt = extent.getRightLng();
							ltUnits = LatLng3.degsToUnits180_180I(lt);
							rtUnits = LatLng3.degsToUnits180_180I(rt);
						}
						dos.writeInt(ltUnits);
						dos.writeInt(rtUnits);
						final double lo = extent.getMinLat();
						final double hi = extent.getMaxLat();
						final int loUnits = LatLng3.degsToUnits180_180I(lo);
						final int hiUnits = LatLng3.degsToUnits180_180I(hi);
						dos.writeInt(loUnits);
						dos.writeInt(hiUnits);
						dos.writeInt(stats[iExtent][0]);
						dos.writeInt(stats[iExtent][1]);
					} catch (final IOException e) {
					}
					/** Write out the Loops in this cluster if there are any. */
					if (partition[iExtent] == null) {
						continue;
					}
					for (final IntLoopData ild : partition[iExtent]) {
						ild.writeOut(/* logger= */null, dos, nWrittenOut,
								nTotalLatLngsWrittenOut);
						++nWrittenOut;
						final int nLatLngsInClosed = ild.getNGcas() + 1;
						nTotalLatLngsWrittenOut += nLatLngsInClosed;
					}
				}
			} catch (final IOException e) {
			}
		} catch (final IOException e) {
		}
	}
}
