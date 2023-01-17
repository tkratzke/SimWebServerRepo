package com.skagit.buildSimLand;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

import com.skagit.util.CombinatoricTools;
import com.skagit.util.Constants;
import com.skagit.util.MathX;
import com.skagit.util.geometry.GcaArrayList;
import com.skagit.util.geometry.IldList;
import com.skagit.util.geometry.IntLoopData;
import com.skagit.util.geometry.crossingPair.CheckForUnknownPairs;
import com.skagit.util.geometry.crossingPair.CrossingPair2;
import com.skagit.util.geometry.gcaSequence.CleanOpenLoop;
import com.skagit.util.geometry.gcaSequence.GcaSequence;
import com.skagit.util.geometry.gcaSequence.GcaSequenceStatics;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.geometry.geoMtx.GcaSkein;
import com.skagit.util.geometry.geoMtx.GeoBox;
import com.skagit.util.geometry.geoMtx.GeoMtx;
import com.skagit.util.geometry.geoMtx.xing1Bundle.Xing1Bundle;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.gshhs.GshhsReader;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.shpFileUtils.ShpFileWriter;

/**
 * Assumes that everything is decrossed. Simplifies each GcaSkein in each
 * loop.
 */
public class Simplifier {
	public static class ParamSet {
		final public char _resolutionChar;
		final public double _maxSqKmToDelete;
		final public double _maxDiameterKmToDelete;
		final public int _tooFewToLoseAny;
		final public int _maxToSkip;
		final public double _strayKm;

		/**
		 * <pre>
		 * Simplification is the process of eliminating vertices in the polygon
		 * without introducing crossings.  For example, we might replace the
		 * sequence of Gcas <(A,B), (B,C), (C,D)> by the single Gca (A,D), thereby
		 * eliminating the vertices, B and C.  In this case, we call (A,D) a
		 * 'chord.'
		 *
		 * We won't do this if (A,D) would introduce a crossing or if B or C is
		 * far from (A,D) or a variety of other possible reasons.  If, for whatever
		 * reason, we reject (A,D), we will fall back to (A,C), eliminating only B.
		 *
		 * There are parameters for the simplification listed below. The most
		 * important is the last one that we call "strayKm."  This parameter causes
		 * us to reject a proposed chord (e.g., (A,D)) if any of the intermediate
		 * vertices (in this case, B and C) would be farther from the chord than strayKm.
		 * The following are the parameters and their meanings.
		 * 0.  Suffix
		 * 1.  maxSqKmToDelete: (Increase to discard more):
		 *     A polygon can be deleted only if it is smaller than maxSqKmToDelete.
		 * 2.  maxDiameterKmToDelete: (Increase to discard more):
		 *     A polygon can be deleted only if no 2 points in the polygon are more
		 *     than maxDiameterKmToDelete apart.
		 * 2a. If both of the above parameters are positive, we WILL delete any
		 *     polygon that passes both tests.
		 * 3.  tooFewToLoseAny (Decrease to edit more):
		 *     If a polygon survived the above, and has at most tooFewToLoseAny vertices,
		 *     it will be kept as is.
		 * 4.  maxToSkip (Increase to edit more):
		 *     We never discard more than maxToSkip consecutive vertices.
		 * 5.  strayKm (Increase to edit more):
		 *     No point in the original polygon will be farther away than strayKm
		 *     from its corresponding chord in the simplified polygon.
		 * </pre>
		 */
		public ParamSet(final char suffix, final double maxSqKmToDelete,
				final double maxDiameterKmToDelete, final int tooFewToLoseAny,
				final int maxToSkip, final double strayKm) {
			_resolutionChar = suffix;
			_maxSqKmToDelete = maxSqKmToDelete;
			_maxDiameterKmToDelete = maxDiameterKmToDelete;
			_tooFewToLoseAny = tooFewToLoseAny;
			_maxToSkip = maxToSkip;
			_strayKm = strayKm;
		}

		public String getShortString() {
			return String.format("%c-%4.2f", _resolutionChar, _strayKm);
		}

		public String getString() {
			return String.format(
					"%c minSqM[%4f] minDiamKm[%.4f] tooFewToLoseAny[%d] maxToSkip[%d] strayKm[%.4f]",
					_resolutionChar, _maxSqKmToDelete * 1.0e6, _maxDiameterKmToDelete,
					_tooFewToLoseAny, _maxToSkip, _strayKm);
		}

		@Override
		public String toString() {
			return getString();
		}

		@Override
		public ParamSet clone() {
			return cloneAndScale(_resolutionChar, 1d);
		}

		/** For scale, bigger means coarser. 1 means clone. */
		public ParamSet cloneAndScale(final char suffix, final double scale) {
			final double maxSqKmToDelete = scale * _maxSqKmToDelete;
			final double maxDiameterKmToDelete = scale * _maxDiameterKmToDelete;
			final int tooFewToLoseAny =
					(int) Math.round(Math.max(3d, (1d / scale) * _tooFewToLoseAny));
			final int maxToSkip =
					(int) Math.round(Math.max(1d, scale * _maxToSkip));
			final double strayKm = _strayKm * scale;
			final ParamSet paramSet = new ParamSet(suffix, maxSqKmToDelete,
					maxDiameterKmToDelete, tooFewToLoseAny, maxToSkip, strayKm);
			return paramSet;
		}
	}

	final private static double _NmiToR = MathX._NmiToR;
	final private static double _KmToNmi = 1000d / Constants._NmiToM;

	public static ArrayList<IntLoopData> simplifyIlds(final MyLogger logger,
			final ParamSet paramSet, final List<IntLoopData> ildsIn,
			final ShpFileWriter shpFileWriter, final File shpFileDir) {
		final double minSqKm = paramSet._maxSqKmToDelete;
		final double minDiameterKm = paramSet._maxDiameterKmToDelete;
		final int tooFewToLoseAny = paramSet._tooFewToLoseAny;
		final double minSqNmi = minSqKm * Constants._SqKmToSqNmi;
		final double minDiameterNmi = minDiameterKm * _KmToNmi;

		final int nIldsIn = ildsIn.size();

		/** Discard the loops that are too small. */
		final ArrayList<IntLoopData> ilds = new ArrayList<>();
		int nIldsToDrop = 0;
		int nGcasToDrop = 0;
		for (int k0 = 0; k0 < nIldsIn; ++k0) {
			final IntLoopData ild = ildsIn.get(k0);
			if (minSqNmi > 0d && minDiameterNmi > 0d) {
				/** The filter is active. */
				final double sqNmi = ild.getSqNmi();
				if (sqNmi < minSqNmi) {
					final double bigEnoughNmi =
							ild.diameterBiggerThan(minDiameterNmi);
					if (bigEnoughNmi < minDiameterNmi) {
						/** Drop this Ild. */
						++nIldsToDrop;
						nGcasToDrop += ild.getNLatLngsClosed() - 1;
						continue;
					}
				}
			}
			ilds.add(ild);
		}

		ArrayList<Loop3> loops =
				IldList.getLoops(ilds, /* destroyInput= */false);
		final int nLoops = loops.size();
		int nLoopsToPreserve = 0;
		int nGcasToPreserve = 0;
		final ArrayList<GcaSkein> skeinsToPreserve = new ArrayList<>();
		int nLoopsToSimplify = 0;
		int nGcasToSimplify = 0;
		final ArrayList<GcaSkein> skeinsToSimplify = new ArrayList<>();
		for (int k0 = 0; k0 < nLoops; ++k0) {
			final Loop3 loop = loops.get(k0);
			final int nGcas = loop.getNGcas();
			final GcaSkein[] theseSkeins = loop.getGcaSkeins();
			if (nGcas <= tooFewToLoseAny) {
				++nLoopsToPreserve;
				nGcasToPreserve += nGcas;
				skeinsToPreserve.addAll(Arrays.asList(theseSkeins));
			} else {
				++nLoopsToSimplify;
				nGcasToSimplify += nGcas;
				skeinsToSimplify.addAll(Arrays.asList(theseSkeins));
			}
			loop.freeMemory();
		}
		loops.clear();
		loops.trimToSize();
		loops = null;
		skeinsToPreserve.trimToSize();
		skeinsToSimplify.trimToSize();
		final int nSkeinsToPreserve = skeinsToPreserve.size();
		final int nSkeinsToSimplify = skeinsToSimplify.size();
		logger.out(String.format(
				"nLoop(drop,preserve,simplify)=(%d,%d,%d), " +
						"nSkeins(preserve,simplify)=(%d,%d), " +
						"nGcas(drop,preserve,simplify)=(%d,%d,%d)",
				nIldsToDrop, nLoopsToPreserve, nLoopsToSimplify, //
				nSkeinsToPreserve, nSkeinsToSimplify, //
				nGcasToDrop, nGcasToPreserve, nGcasToSimplify));

		final String coreString = paramSet.getShortString();
		final ArrayList<IntLoopData> simplifiedIlds =
				coreSimplify(logger, shpFileWriter, shpFileDir, coreString,
						paramSet, /* phaseNumber= */0, /* maxToDrop= */ nGcasToSimplify,
						skeinsToPreserve, skeinsToSimplify);
		return simplifiedIlds;
	}

	/**
	 * coreSimplify does everything. There are some parameters for foo that
	 * can be played with for debugging.
	 */
	private static ArrayList<IntLoopData> coreSimplify(final MyLogger logger,
			final ShpFileWriter shpFileWriter, final File shpFileDir,
			final String coreString, final ParamSet paramSet,
			final int phaseNumber, final int maxToDrop,
			final List<GcaSkein> skeinsToPreserve,
			final List<GcaSkein> skeinsToSimplify) {

		/**
		 * Create the array allSkeins and then organize them into a GeoMtx so
		 * that, for an individual GcaSkein, we can pick out only the few
		 * GcaSkeins that are players.
		 */
		final int nSkeinsToPreserve = skeinsToPreserve.size();
		final int nSkeinsToSimplify = skeinsToSimplify.size();
		final int nAllSkeins = nSkeinsToSimplify + nSkeinsToPreserve;
		final GcaSkein[] allSkeins = new GcaSkein[nAllSkeins];
		for (int k0 = 0; k0 < nSkeinsToPreserve; ++k0) {
			allSkeins[k0] = skeinsToPreserve.get(k0);
		}
		for (int k = nSkeinsToPreserve, k0 = 0; k0 < nSkeinsToSimplify; ++k0) {
			allSkeins[k++] = skeinsToSimplify.get(k0);
		}
		final GeoMtx skeinMtx = new GeoMtx(allSkeins);

		/** For each GcaSkein, record its starting point, for reconstructing. */
		final TreeMap<LatLng3, GcaSkein> byLatLng0 =
				new TreeMap<>(LatLng3._ByLatThenLng);
		for (int k = 0; k < nAllSkeins; ++k) {
			final GcaSkein skein = allSkeins[k];
			final GreatCircleArc[] gcaArray = skein.getGcaArray();
			final LatLng3 latLng0 = gcaArray[0].getLatLng0();
			final GcaSkein incumbent0 = byLatLng0.put(latLng0, skein);
			assert incumbent0 == null : "Should not have 2 skeins with the same zero-point.";
		}

		/** Simplify each skein. */
		for (int nDropped = 0, k = 0;
				k < nSkeinsToSimplify && nDropped < maxToDrop; ++k) {
			final GcaSkein skein = skeinsToSimplify.get(k);
			final int nOldGcas = skein.getNGcas();
			final int nLeftToDrop = maxToDrop - nDropped;
			simplifySkein(paramSet, nLeftToDrop, skeinMtx, skein);
			final int nNewGcas = skein.getNGcas();
			nDropped += (nOldGcas - nNewGcas);
		}
		/** We'll skip this assert right now. */
		if (true) {
		} else {
			final Extent[] allExtents = new Extent[nAllSkeins];
			for (int k = 0; k < nAllSkeins; ++k) {
				allExtents[k] = allSkeins[k].createExtent();
			}
			assert validateSkeins(logger, "Done With Simplifying", allSkeins,
					/* onesToCheck= */null, allExtents) : "What happened?";
		}

		if (shpFileWriter != null && shpFileDir != null && phaseNumber > 1) {
			ArrayList<GshhsReader.HeaderPlusGcaArray> after =
					new ArrayList<>(nAllSkeins);
			for (int k = 0; k < nAllSkeins; ++k) {
				after.add(new GshhsReader.HeaderPlusGcaArray(allSkeins[k]));
			}
			GcaArrayList afterList =
					new GcaArrayList(String.format("After-%d-%d%s", phaseNumber,
							maxToDrop, coreString), after);
			afterList.writeToShpFiles(logger,
					BuildSimLand._InitialPhasesWritePolylines, shpFileWriter,
					shpFileDir);
			after.clear();
			after = null;
			afterList = null;
		}

		/** Reconstruct the loops from their simplified GcaSkeins. */
		final ArrayList<IntLoopData> simplified = reconstructIlds(logger,
				byLatLng0, coreString, shpFileWriter, shpFileDir);
		return simplified;
	}

	private enum ChordViolation {
		TOO_MANY_DROPS, //
		SPIKE, //
		CRUCIAL_POINT, TOO_MUCH_SKIP, //
		TOO_MUCH_STRAY, GREW_EXTENT, //
		BEND_BACK0, HAVE_CROSSING0, //
		BEND_BACK1, HAVE_CROSSING1, //
		HAVE_CROSSING, //
		BEND_BACK2, HAVE_CROSSING2, //
		BEND_BACK3, HAVE_CROSSING3 //
	}

	/** Simplify an individual GcaSkein. This is the guts of the algorithm. */
	private static void simplifySkein(final ParamSet paramSet,
			final int maxToDrop, final GeoMtx skeinMtx, final GcaSkein skein) {
		final Extent mainSkeinExtent = skein.createExtent();
		if (mainSkeinExtent.crossesIdl()) {
			return;
		}
		final GreatCircleArc[] gcaArray = skein.getGcaArray();
		final int nGcas = gcaArray.length;
		if (nGcas < 3) {
			return;
		}
		final GreatCircleArc firstGca = gcaArray[0];
		final GreatCircleArc lastGca = gcaArray[nGcas - 1];
		GreatCircleArc pvsOfFirstX = firstGca.getPvs();
		GreatCircleArc nxtOfLastX = lastGca.getNxt();
		final boolean isCompleteLoop = pvsOfFirstX == lastGca;
		assert (nxtOfLastX == firstGca) == isCompleteLoop : "Strange loop/nonLoop skein (01).";
		assert (nxtOfLastX == firstGca) == isCompleteLoop : "Strange loop/nonLoop skein (02).";

		/**
		 * Using skeinMtx, find all of the GcaSkeins that might interfere with
		 * skein's simplification. We could then check each of these, using its
		 * GeoMtx, or put all of their Gcas into one GeoMtx. We choose the
		 * latter. When we build this GeoMtx, we exclude pvsOfFirst and
		 * nxtOfLast; those will always be checked separately anyway.
		 */
		final ArrayList<GreatCircleArc> opposingGcaList = new ArrayList<>();
		final GeoBox[] players = skeinMtx.computeIntersectors(mainSkeinExtent);
		final int nPlayers = players == null ? 0 : players.length;
		boolean seenMyself = false;
		for (int k = 0; k < nPlayers; ++k) {
			final GcaSkein player = (GcaSkein) players[k];
			if (player == skein) {
				seenMyself = true;
				continue;
			}
			opposingGcaList.addAll(Arrays.asList(player.getGcaArray()));
		}
		assert seenMyself : "Should have \"interfered\" with myself.";

		final int nOpposingGcas = opposingGcaList.size();
		final GreatCircleArc[] opposingGcaArray =
				opposingGcaList.toArray(new GreatCircleArc[nOpposingGcas]);
		final GeoMtx opposingGcaMtx = new GeoMtx(opposingGcaArray);

		/**
		 * We use nested extents to determine which previous chords or
		 * yet-to-be-considered Gcas, we need to be concerned about.
		 */
		final Extent[] nestedGcaExtents = new Extent[nGcas];
		/**
		 * Form the decreasing nest of gcaExtents; do this by building it from
		 * the last one backward.
		 */
		for (int k = nGcas - 1; k >= 0; --k) {
			final Extent gcaExtent = gcaArray[k].createExtent();
			if (k == nGcas - 1) {
				nestedGcaExtents[k] = gcaExtent;
			} else {
				nestedGcaExtents[k] =
						nestedGcaExtents[k + 1].buildExtension(gcaExtent);
			}
		}

		/** K0_LOOP builds the chords. */
		final ArrayList<GreatCircleArc> chords = new ArrayList<>();
		final ArrayList<Extent> nestedChordExtents = new ArrayList<>();
		final GcaSequence skeinGcaSequence = skein.getGcaSequence();
		assert skeinGcaSequence != null : "Simplifier A";

		/** Compute the crucial indices. */
		final TreeSet<Integer> crucialIndicesA = new TreeSet<>();
		final double nCrucialA = 6d;
		for (int kCrucial = 0; kCrucial < nCrucialA; ++kCrucial) {
			final int iCrucial = (int) Math.round(kCrucial / nCrucialA * nGcas);
			crucialIndicesA.add(iCrucial);
		}
		final int nCrucial = crucialIndicesA.size();
		final int[] crucialIndices = new int[nCrucial];
		final Iterator<Integer> it = crucialIndicesA.iterator();
		for (int k = 0; k < nCrucial; ++k) {
			crucialIndices[k] = it.next();
		}
		Arrays.sort(crucialIndices);
		final int nViolationTypes = ChordViolation.values().length;
		final int[] violationCounts = new int[nViolationTypes];

		int nDropped = 0;
		K0_LOOP: for (int k0 = 0; k0 < nGcas;) {
			final int nChords = chords.size();
			/** The new chord will start at k0Gca.getLatLng0(). */
			final GreatCircleArc k0Gca = gcaArray[k0];
			final GcaSequence gcaSequence = k0Gca.getGcaSequence();
			assert gcaSequence == skeinGcaSequence : "Simplifier B";
			final LatLng3 k0LatLng0 = k0Gca.getLatLng0();
			for (int k1 = k0 + 1; k1 < nGcas; ++k1) {
				final GreatCircleArc k1Gca = gcaArray[k1];
				final LatLng3 k1LatLng1 = k1Gca.getLatLng1();
				/**
				 * We form chord from chord0 to chord1. If it IS a legal chord, we
				 * will NOT use it, but rather continue looking at the next value of
				 * k1. If it is NOT a legal chord, we will form a chord from latLng0
				 * to k1Gca.getLatLng0() and use that.
				 */
				final ChordViolation chordViolation;
				final int crucialIndex = Arrays.binarySearch(crucialIndices, k1);
				final GreatCircleArc possibleChord;
				if (nDropped >= maxToDrop) {
					chordViolation = ChordViolation.TOO_MANY_DROPS;
				} else if (crucialIndex >= 0) {
					chordViolation = ChordViolation.CRUCIAL_POINT;
					possibleChord = null;
				} else {
					possibleChord = GreatCircleArc.CreateGca(k0LatLng0, k1LatLng1);
					chordViolation = getChordViolation(paramSet, possibleChord,
							mainSkeinExtent, chords, nestedChordExtents, nestedGcaExtents,
							opposingGcaMtx, pvsOfFirstX, nxtOfLastX, k0, k1, gcaArray);
				}

				if (chordViolation != null) {
					++violationCounts[chordViolation.ordinal()];
					/** Cannot use chord. Form newChord from k1's latLng0. */
					final LatLng3 k1LatLng0 = k1Gca.getLatLng0();
					final GreatCircleArc newChord =
							GreatCircleArc.CreateGca(k0LatLng0, k1LatLng0);
					if (isCompleteLoop && k0Gca == firstGca) {
						nxtOfLastX = newChord;
					}
					final GreatCircleArc pvs = k0Gca.getPvs();
					assert pvs != null &&
							pvs.getGcaSequence() == skeinGcaSequence : "Simplifier C1";
					pvs.setGcaSequence(skeinGcaSequence, pvs.getPvs(), newChord);
					newChord.setGcaSequence(skeinGcaSequence, pvs, k1Gca);
					assert k1Gca != null &&
							k1Gca.getGcaSequence() == skeinGcaSequence : "Simplifier C2";
					k1Gca.setGcaSequence(skeinGcaSequence, newChord, k1Gca.getNxt());
					/** Expand the increasing nest of chordExtents. */
					final Extent newChordExtent = newChord.createExtent();
					final Extent nestedChordExtent;
					if (nChords > 0) {
						nestedChordExtent = nestedChordExtents.get(nChords - 1)
								.buildExtension(newChordExtent);
					} else {
						nestedChordExtent = newChordExtent;
					}
					chords.add(newChord);
					nestedChordExtents.add(nestedChordExtent);
					/**
					 * We did not use k1; that's where we want to start for the next
					 * one.
					 */
					k0 = k1;
					continue K0_LOOP;
				}
				++nDropped;
			}

			/**
			 * We never found something we couldn't use, and we're out of edges.
			 * Keep the chord, reset its pointers, and quit.
			 */
			final LatLng3 latLng1 = lastGca.getLatLng1();
			final GreatCircleArc newChord =
					GreatCircleArc.CreateGca(k0LatLng0, latLng1);
			if (isCompleteLoop) {
				pvsOfFirstX = newChord;
			}
			final GreatCircleArc gca0Pvs = k0Gca.getPvs();
			assert gca0Pvs != null &&
					gca0Pvs.getGcaSequence() == skeinGcaSequence : "Simplifier D";
			newChord.setGcaSequence(skeinGcaSequence, gca0Pvs, nxtOfLastX);
			gca0Pvs.setGcaSequence(skeinGcaSequence, gca0Pvs.getPvs(), newChord);
			assert nxtOfLastX != null &&
					nxtOfLastX.getGcaSequence() == skeinGcaSequence : "Simplifier E";
			nxtOfLastX.setGcaSequence(skeinGcaSequence, newChord,
					nxtOfLastX.getNxt());
			chords.add(newChord);
			break;
		}
		if (true) {
		} else {
			System.out.print("\nViolationCounts:");
			final ChordViolation[] chordViolationValues = ChordViolation.values();
			for (int k = 0; k < chordViolationValues.length; ++k) {
				System.out.printf("\n\t%s: %d", chordViolationValues[k],
						violationCounts[k]);
			}
		}

		assert GcaSequenceStatics.validGcaList(chords, isCompleteLoop,
				/* checkGcaSequence= */true) : "Invalid links after simplifying skein.";

		for (int k = 0; k < nPlayers; ++k) {
			final GcaSkein player = (GcaSkein) players[k];
			player.freeMemory();
		}
		/** Update skein's edges. */
		skein.replaceGcas(chords);
	}

	private static ChordViolation getChordViolation(final ParamSet paramSet,
			final GreatCircleArc chord, final Extent mainSkeinExtent,
			final ArrayList<GreatCircleArc> chords,
			final ArrayList<Extent> nestedChordExtents,
			final Extent[] nestedGcaExtents, final GeoMtx opposingGcaMtx,
			final GreatCircleArc pvsOfFirst, final GreatCircleArc nxtOfLast,
			final int k0, final int k1, final GreatCircleArc[] gcaArray) {

		/** Guard against too large a stray. */
		final double maxStrayNmi = paramSet._strayKm * _KmToNmi;
		for (int k2 = k0; k2 < k1; ++k2) {
			final LatLng3 latLng = gcaArray[k2].getLatLng1();
			final GreatCircleArc.Projection projection =
					chord.new Projection(latLng);
			final double rToChord = projection.getRToGca();
			final double nmiToChord = rToChord / _NmiToR;
			if (nmiToChord > maxStrayNmi) {
				return ChordViolation.TOO_MUCH_STRAY;
			}
		}

		/** Guard against introducing a spike. */
		final double hdg00 = chord.getRawReverseHdg();
		final GreatCircleArc nxt = gcaArray[k1].getNxt();
		final double hdg01 = nxt.getRawInitialHdg();
		final double deltaHdg0 =
				Math.abs(LatLng3.getInRange180_180(hdg00 - hdg01));
		if (deltaHdg0 < 2.5d) {
			return ChordViolation.SPIKE;
		}
		final GreatCircleArc pvs = gcaArray[k0].getPvs();
		final double hdg10 = pvs.getRawReverseHdg();
		final double hdg11 = chord.getRawInitialHdg();
		final double deltaHdg1 =
				Math.abs(LatLng3.getInRange180_180(hdg10 - hdg11));
		if (deltaHdg1 < 10d) {
			return ChordViolation.SPIKE;
		}

		/** Guard against skipping too many. */
		final int nSkipped = k1 - k0;
		if (nSkipped > paramSet._maxToSkip) {
			return ChordViolation.TOO_MUCH_SKIP;
		}

		/** Guard against growing extentOfBoxBeingSimplified. */
		final Extent chordExtent = chord.createExtent();
		final boolean surrounds =
				mainSkeinExtent.surrounds(chordExtent, /* mustBeClean= */false);
		if (!surrounds) {
			return ChordViolation.GREW_EXTENT;
		}

		/**
		 * Guard against crossing an existing chord. Do a binary search for the
		 * first extent to be concerned about.
		 */
		final int nChords = chords.size();
		int tooLow = -1;
		int highEnough = nChords;
		while (highEnough > tooLow + 1) {
			final int n = (highEnough + tooLow) / 2;
			final Extent nestedChordExtent = nestedChordExtents.get(n);
			if (nestedChordExtent.overlaps(chordExtent)) {
				highEnough = n;
			} else {
				tooLow = n;
			}
		}
		for (int k2 = highEnough; k2 < nChords; ++k2) {
			final GreatCircleArc oldChord = chords.get(k2);
			if (k2 == nChords - 1) {
				/** Special check for the most recent chord. */
				final double hdgA = oldChord.getRoundedReverseHdg();
				final double hdgB = chord.getRoundedInitialHdg();
				if (hdgA == hdgB) {
					return ChordViolation.BEND_BACK0;
				}
			} else {
				final CrossingPair2 crossingPair =
						new CrossingPair2(chord, oldChord);
				if (crossingPair.hasCrossing()) {
					return ChordViolation.HAVE_CROSSING0;
				}
			}
		}

		/** Guard against crossing a later Gca in gcaArray. */
		final int nGcas = gcaArray.length;
		int lowEnough = k1;
		int tooHigh = nGcas;
		while (tooHigh > lowEnough + 1) {
			final int n = (tooHigh + lowEnough) / 2;
			final Extent nestedGcaExtent = nestedGcaExtents[n];
			if (nestedGcaExtent.overlaps(chordExtent)) {
				lowEnough = n;
			} else {
				tooHigh = n;
			}
		}
		for (int k2 = k1 + 1; k2 < tooHigh; ++k2) {
			final GreatCircleArc oldGca = gcaArray[k2];
			if (k2 == k1 + 1) {
				/** Special check for the next Gca. */
				final double hdgA = oldGca.getRoundedInitialHdg();
				final double hdgB = chord.getRoundedReverseHdg();
				if (hdgA == hdgB) {
					return ChordViolation.BEND_BACK1;
				}
			} else {
				final CrossingPair2 crossingPair = new CrossingPair2(chord, oldGca);
				if (crossingPair.hasCrossing()) {
					return ChordViolation.HAVE_CROSSING1;
				}
			}
		}

		/**
		 * Guard against crossing any opponent except pvsOfFirst and nxtOfLast.
		 */
		final CrossingPair2 xingPair0 =
				opposingGcaMtx.findXing0(chord, /* findFirst= */false);
		if (xingPair0 != null && xingPair0.hasCrossing()) {
			return ChordViolation.HAVE_CROSSING;
		}

		/** Guard against crossing pvsOfFirst. */
		if (pvsOfFirst.getLatLng1() == chord.getLatLng0()) {
			/** Check bend-back headings. */
			if (pvsOfFirst.getRoundedReverseHdg() == chord
					.getRoundedInitialHdg()) {
				return ChordViolation.BEND_BACK2;
			}
		} else {
			final CrossingPair2 xingPair1 = new CrossingPair2(pvsOfFirst, chord);
			if (xingPair1.hasCrossing()) {
				return ChordViolation.HAVE_CROSSING2;
			}
		}

		/** Guard against crossing nxtOfLast. */
		if (chord.getLatLng1() == nxtOfLast.getLatLng0()) {
			/** Check bend-back headings. */
			if (chord.getRoundedReverseHdg() == nxtOfLast
					.getRoundedInitialHdg()) {
				return ChordViolation.BEND_BACK3;
			}
		} else {
			final CrossingPair2 xingPair2 = new CrossingPair2(chord, nxtOfLast);
			if (xingPair2.hasCrossing()) {
				return ChordViolation.HAVE_CROSSING3;
			}
		}

		return null;
	}

	/** Destroys byLatLng0. */
	private static ArrayList<IntLoopData> reconstructIlds(
			final MyLogger logger, final TreeMap<LatLng3, GcaSkein> byLatLng0,
			final String coreString, final ShpFileWriter shpFileWriter,
			final File shpFileDir) {
		final ArrayList<IntLoopData> outputIlds = new ArrayList<>();
		for (int k0 = 0; !byLatLng0.isEmpty(); ++k0) {
			final LatLng3 keySkeinLatLng = byLatLng0.keySet().iterator().next();
			final GcaSkein seedGcaSkein = byLatLng0.get(keySkeinLatLng);
			final GreatCircleArc[] gcaArray00 = seedGcaSkein.getGcaArray();
			final GreatCircleArc gca00 = gcaArray00[0];
			final LatLng3 latLng00 = gca00.getLatLng0();
			GcaSkein gcaSkein = seedGcaSkein;
			final GcaSequence seedGcaSequence = seedGcaSkein.getGcaSequence();
			assert seedGcaSequence != null : String.format(
					"k0[%d] Skeins should retain GcaSequence throughout simplification.",
					k0);
			final ArrayList<GreatCircleArc> gcasInLoop = new ArrayList<>();
			for (int k1 = 0;; ++k1) {
				final GreatCircleArc[] gcaArray = gcaSkein.getGcaArray();
				final GreatCircleArc gca0 = gcaArray[0];
				final LatLng3 latLng0 = gca0.getLatLng0();
				byLatLng0.remove(latLng0);
				gcasInLoop.addAll(Arrays.asList(gcaArray));
				final int nGcas = gcaArray.length;
				for (int k2 = 0; k2 < nGcas; ++k2) {
					final GreatCircleArc gca = gcaArray[k2];
					final GcaSequence gcaSequence = gca.getGcaSequence();
					assert gcaSequence == seedGcaSequence : "Funky loop reconstruct(1a).";
				}
				final GreatCircleArc gcaN = gcaArray[nGcas - 1];
				final LatLng3 latLngN = gcaN.getLatLng1();
				gcaSkein = byLatLng0.get(latLngN);
				if (gcaSkein == null) {
					assert latLngN
							.equals(latLng00) : "Should have looped back to latLng0";
					if (k0 % 500 == 0 && k1 % 100 == 0) {
						logger.out(String.format(
								"Gathered %d skeins for %d-th loop, %d skeins remaining.",
								k1 + 1, k0, byLatLng0.size()));
					}
					break;
				}
				final GcaSequence gcaSkeinSequence = gcaSkein.getGcaSequence();
				assert gcaSkeinSequence == seedGcaSequence : "Funky loop reconstruct(1b).";
			}
			assert GcaSequenceStatics.validGcaList(gcasInLoop, /* isLoop= */true,
					/* checkGcaSequence= */true) : "Funky Loop reconstruct(2)";
			final int nGcas = gcasInLoop.size();
			final GreatCircleArc[] gcaArray =
					gcasInLoop.toArray(new GreatCircleArc[nGcas]);
			final Loop3 oldLoop = (Loop3) seedGcaSequence;
			final int id = oldLoop.getId();
			final int subId = oldLoop.getSubId();
			final int ancestorId = oldLoop.getAncestorId();
			final int flag = oldLoop.getFlag();
			final Loop3 loop = Loop3.getLoop(logger, id, subId, flag, ancestorId,
					gcaArray, CleanOpenLoop._NoChecks, /* logChanges= */true,
					/* debug= */false);
			if (!loop.isValid() || loop.isClockwise() != oldLoop.isClockwise()) {
				if (shpFileWriter != null && shpFileDir != null) {
					final int nRawGcas = gcaArray.length;
					final ArrayList<GshhsReader.HeaderPlusGcaArray> rawGcas =
							new ArrayList<>(nRawGcas);
					for (int k = 0; k < nRawGcas; ++k) {
						rawGcas.add(new GshhsReader.HeaderPlusGcaArray(gcaArray[k], k));
					}
					GcaArrayList rawGcaList =
							new GcaArrayList("BadResult" + coreString, rawGcas);
					rawGcaList.writeToShpFiles(logger,
							BuildSimLand._InitialPhasesWritePolylines, shpFileWriter,
							shpFileDir);
					rawGcas.clear();
					rawGcaList = null;
				}
			}
			assert loop.isValid() : "Funky loop reconstruct(3).";
			if (loop.isClockwise() != oldLoop.isClockwise()) {
				return null;
			}
			assert loop.isClockwise() == oldLoop
					.isClockwise() : "Funky loop reconstruct(4).";
			outputIlds.add(new IntLoopData(loop));
		}
		outputIlds.trimToSize();
		return outputIlds;
	}

	private static boolean validateSkeins(final MyLogger logger,
			final String caption, final GcaSkein[] allSkeins,
			final int[] onesToCheck, final Extent[] allExtents) {
		final int nSkeins = allSkeins.length;
		final int nSkeinsToCheck =
				onesToCheck == null ? nSkeins : onesToCheck.length;
		if (nSkeinsToCheck == 0) {
			return true;
		}
		/** Create a map from Gca to skein-number, position-within-skein. */
		final TreeMap<GreatCircleArc, int[]> theMap =
				new TreeMap<>(GreatCircleArc._BySmallThenBig);
		for (int k0 = 0; k0 < nSkeins; ++k0) {
			final GcaSkein skein0 = allSkeins[k0];
			final GreatCircleArc[] gcaArray0 = skein0.getGcaArray();
			final int nGcas0 = gcaArray0.length;
			for (int k1 = 0; k1 < nGcas0; ++k1) {
				final GreatCircleArc gca = gcaArray0[k1];
				theMap.put(gca, new int[] { k0, k1 });
			}
		}

		for (int k0A = 0; k0A < nSkeinsToCheck; ++k0A) {
			final int k0;
			if (onesToCheck == null) {
				k0 = k0A;
			} else {
				k0 = onesToCheck[k0A];
			}
			logger.out(String.format(
					"Check at %s; k(k0)=%d(%d) nToCheck(nSkeins)=%d(%d)", caption,
					k0A, k0, nSkeinsToCheck, nSkeins));
			final GcaSkein skein0 = allSkeins[k0];
			final Extent extent0 = allExtents[k0];
			final GreatCircleArc[] gcaArray0 = skein0.getGcaArray();
			final int nGcas0 = gcaArray0.length;
			for (int k1 = 0; k1 < nGcas0; ++k1) {
				final GreatCircleArc gca = gcaArray0[k1];
				final String k0String =
						CombinatoricTools.getString(new int[] { k0, k1 });
				if (!extent0.surrounds(gca.createExtent(),
						/* mustBeClean= */false)) {
					logger.err(String.format("Extent grew! %s %s.", k0String,
							gca.getXmlString()));
					return false;
				}
			}

			final GeoMtx gcaMtx0 = new GeoMtx(gcaArray0);
			final ArrayList<GreatCircleArc> opposingGcaList = new ArrayList<>();
			for (int k1A = 0; k1A < nSkeinsToCheck; ++k1A) {
				final int k1;
				if (onesToCheck == null) {
					k1 = k1A;
				} else {
					k1 = onesToCheck[k1A];
				}
				if (k1 == k0) {
					final Xing1Bundle xing1Bndl0 =
							gcaMtx0.computeInternalXings(/* getOnlyOne= */true);
					final CrossingPair2 xingPair0 = xing1Bndl0.getCrossingPair();
					if (xingPair0 != null) {
						final GreatCircleArc gca0 = xingPair0._gca0;
						final GreatCircleArc gca1 = xingPair0._gca1;
						final String k0String =
								CombinatoricTools.getString(theMap.get(gca0));
						final String k1String =
								CombinatoricTools.getString(theMap.get(gca1));
						logger.err(String.format("Intra-Crossing %s %s\n%s", k0String,
								k1String, xingPair0.getXmlString()));
						return false;
					}
					continue;
				}
				final GcaSkein skein1 = allSkeins[k1];
				final Extent extent1 = skein1.createExtent();
				if (extent1.overlaps(extent0)) {
					final GreatCircleArc[] gcaArray1 = skein1.getGcaArray();
					opposingGcaList.addAll(Arrays.asList(gcaArray1));
				}
			}
			final int nOpposingGcas = opposingGcaList.size();
			final GreatCircleArc[] opposingGcas =
					opposingGcaList.toArray(new GreatCircleArc[nOpposingGcas]);
			final GeoMtx gcaMtx1 = new GeoMtx(opposingGcas);
			final CheckForUnknownPairs checkForUnknownPairs1 =
					CheckForUnknownPairs.CreatePvsNxtPlusHdg();
			final Xing1Bundle xing1Bndl1 =
					new Xing1Bundle(checkForUnknownPairs1, /* getOnlyOne= */true);
			Xing1Bundle.updateXing1Bundle(xing1Bndl1, gcaMtx0, gcaMtx1);
			final CrossingPair2 xingPair1 = xing1Bndl1.getCrossingPair();
			if (xingPair1 != null) {
				final GreatCircleArc gca0 = xingPair1._gca0;
				final GreatCircleArc gca1 = xingPair1._gca1;
				final String k0String =
						CombinatoricTools.getString(theMap.get(gca0));
				final String k1String =
						CombinatoricTools.getString(theMap.get(gca1));
				logger.err(String.format("Inter-Crossing %s %s\n%s", k0String,
						k1String, xingPair1.getXmlString()));
				return false;
			}
		}
		return true;
	}
}
