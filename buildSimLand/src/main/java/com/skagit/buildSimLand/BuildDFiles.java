package com.skagit.buildSimLand;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.io.FilenameUtils;

// import org.apache.commons.io.FilenameUtils;

import com.skagit.util.MathX;
import com.skagit.util.geometry.IntLoopData;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.gshhs.FileCache;
import com.skagit.util.gshhs.GshhsReader;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.LatLng3;

public class BuildDFiles {
	final private static double _NmiToR = MathX._NmiToR;
	final private static int _NToStore = 1024;
	/** We set the following to give Antarctica its own array. */
	final private static int _ThresholdForIndividualArrays = 14730;
	final private static int _MinimumNIndividualArrays = 50;

	private static class Project {
		final ArrayList<IntLoopData> _ilds;
		double _sum;
		@SuppressWarnings("unused")
		double _sumOfSquares;

		Project() {
			_ilds = new ArrayList<>();
			_sum = _sumOfSquares = 0;
		}

		void addIntLoopData(final IntLoopData ild) {
			_ilds.add(ild);
			final double nGcas = ild.getNLatLngsClosed() - 1;
			_sum += nGcas;
			_sumOfSquares += nGcas * nGcas;
		}
	}

	static private double[] getDistanceArray(final IntLoopData ild, final int nProcessors) {
		final int nGcas = ild.getNLatLngsClosed() - 1;
		/**
		 * If the gap is more than halfway around the cycle, don't compute it; let the
		 * smaller direction get it and then let maxing out bump up
		 * theseDistances[gap-1]. Hence, we only compute at most nGcas/2 gaps.
		 */
		final int nGaps = Math.min(_NToStore, nGcas / 2);
		final int smallN = nGcas / nProcessors;
		final int nBig = nGcas - nProcessors * smallN;
		final double[][] distanceArrays = new double[nProcessors][];
		final Thread[] threads = new Thread[nProcessors];
		int oldEnd = 0;
		for (int iThread = 0; iThread < nProcessors; ++iThread) {
			final int start = oldEnd;
			final int end = oldEnd = start + smallN + (iThread < nBig ? 1 : 0);
			final double[] haversines = distanceArrays[iThread] = new double[nGaps];
			for (int k = 0; k < nGaps; ++k) {
				haversines[k] = 0.0;
			}
			threads[iThread] = new Thread(
					String.format("P-proc(Thread[%d] loopId[%d] start[%d])", iThread, ild.getId(), start)) {
				@Override
				public void run() {
					for (int k = start; k < end; ++k) {
						final LatLng3 latLng0 = ild.getLatLng(k);
						for (int gap = 1; gap <= nGaps; ++gap) {
							final LatLng3 latLng1 = ild.getLatLng((k + gap) % nGcas);
							final double thisR = MathX.haversineX(latLng0, latLng1);
							haversines[gap - 1] = Math.max(haversines[gap - 1], thisR);
						}
					}
				}
			};
			if (iThread < nProcessors - 1) {
				threads[iThread].start();
			}
		}
		threads[nProcessors - 1].run();
		try {
			for (int iThread = 0; iThread < nProcessors - 1; ++iThread) {
				threads[iThread].join();
			}
		} catch (final InterruptedException ignored) {
		}
		final double[] distances = new double[nGaps];
		for (int k = 0; k < nGaps; ++k) {
			distances[k] = 0;
			for (final double[] distanceArray : distanceArrays) {
				distances[k] = Math.max(distances[k], distanceArray[k]);
			}
			if (k > 0) {
				distances[k] = Math.max(distances[k - 1], distances[k]);
			}
		}
		return distances;
	}

	static private double[] runSlice(final ArrayList<IntLoopData> ilds, final String sentinel) {
		double[] oldOne = null;
		for (final IntLoopData ild : ilds) {
			final double[] theseDistances = getDistanceArray(ild, 1);
			oldOne = merge(oldOne, theseDistances);
		}
		return oldOne;
	}

	static void buildDFile(final MyLogger logger, final String bFilePath) {
		/** Get all of the IntLoopDatas in and sorted into an array. */
		final GshhsReader reader = GshhsReader.constructGshhsReader(new File(bFilePath), /* headerFilter= */null);
		final ArrayList<IntLoopData> inputIlds = new ArrayList<>();
		while (true) {
			final GshhsReader.HeaderPlusLoops headerPlusLoops = reader.getThePartsOfNextLoop(logger,
					/* knownToBeClean= */true);
			if (headerPlusLoops == null) {
				break;
			}
			final Loop3[] readInLoops = headerPlusLoops._loops;
			final int nReadInLoops = readInLoops == null ? 0 : readInLoops.length;
			if (nReadInLoops == 0) {
				continue;
			}
			final Loop3 loop = readInLoops[0];
			final IntLoopData ild = new IntLoopData(loop);
			inputIlds.add(ild);
		}

		final int nIlds = inputIlds.size();
		Collections.sort(inputIlds, IntLoopData._BySizeStructure);
		final int nProcessors = Runtime.getRuntime().availableProcessors();
		final TreeMap<Integer, double[]> distancesArrayMap = new TreeMap<>();
		/** Parallelize the individual big ones. */
		int nIndividualArrays = 0;
		for (int k = 0; k < nIlds; ++k) {
			final IntLoopData ild = inputIlds.get(k);
			final int nGcas = ild.getNLatLngsClosed() - 1;
			if (k >= _MinimumNIndividualArrays && nGcas < _ThresholdForIndividualArrays) {
				break;
			}
			++nIndividualArrays;
			final double[] distances = getDistanceArray(ild, nProcessors);
			distancesArrayMap.put(ild.getId(), distances);
		}
		/** Spread the remaining Ilds among the projects. */
		final Project[] projects = new Project[nProcessors];
		for (int iThread = 0; iThread < nProcessors; ++iThread) {
			projects[iThread] = new Project();
		}
		for (int k = nIndividualArrays; k < nIlds; ++k) {
			/** Figure out which Project to put loop into. */
			double[] choice = new double[] { -1, Double.MAX_VALUE };
			for (int iThread = 0; iThread < nProcessors; ++iThread) {
				final Project project = projects[iThread];
				if (project._sum < choice[1]) {
					choice = new double[] { iThread, project._sum };
				}
			}
			projects[(int) Math.round(choice[0])].addIntLoopData(inputIlds.get(k));
		}

		/** Do the work now, which is spread among the different slices. */
		final Thread[] threads = new Thread[nProcessors];
		final double[][] distanceArrays = new double[nProcessors][];
		for (int iThread = 0; iThread < nProcessors; ++iThread) {
			final int iThreadx = iThread;
			final Project project = projects[iThread];
			threads[iThread] = new Thread("P-Processing[" + iThread + "]") {
				@Override
				public void run() {
					distanceArrays[iThreadx] = runSlice(project._ilds, getName());
				}
			};
			if (iThread < nProcessors - 1) {
				threads[iThread].start();
			} else {
				distanceArrays[iThread] = runSlice(projects[iThread]._ilds, threads[iThread].getName());
			}
		}
		try {
			for (int iThread = 0; iThread < nProcessors - 1; ++iThread) {
				threads[iThread].join();
			}
		} catch (final InterruptedException ignored) {
		}

		/** Update the miscellaneous one. */
		double[] distances = null;
		for (int iThread = 0; iThread < nProcessors; ++iThread) {
			distances = merge(distances, distanceArrays[iThread]);
		}
		distancesArrayMap.put(-1, distances);
		/** Dump the map to the d file, putting it next to the b file. */
		final File bFile = new File(bFilePath);
		final File directoryFile = bFile.getParentFile();
		final String coreFileName = bFile.getName();
		final String dFileNameCore = FilenameUtils.getBaseName(coreFileName) + GshhsReader._DFileSuffix;
		final File dFile = new File(directoryFile, dFileNameCore);
		final String fullOutputPath = dFile.getAbsolutePath();
		try (final FileOutputStream fis = new FileOutputStream(dFile)) {
			try (final ObjectOutputStream oos = new ObjectOutputStream(fis)) {
				oos.writeObject(distancesArrayMap);
			} catch (final IOException e) {
			}
		} catch (final FileNotFoundException e) {
			logger.out(String.format("FileNotFound when writing to %s.", fullOutputPath));
		} catch (final IOException e) {
			logger.out(String.format("IOException when writing to %s.", fullOutputPath));
		}
		logger.out(String.format("\tFinished D-filing %s.", bFilePath));
	}

	private static double[] merge(final double[] oldOne, final double[] newOne) {
		if (oldOne == null) {
			return newOne;
		}
		if (newOne == null) {
			return oldOne;
		}
		final double[] longOne = oldOne.length >= newOne.length ? oldOne : newOne;
		final double[] shortOne = longOne == oldOne ? newOne : oldOne;
		final int longLength = longOne.length;
		final int shortLength = shortOne.length;
		if (shortLength == 0) {
			return longOne;
		}
		for (int k = 0; k < shortLength; ++k) {
			longOne[k] = Math.max(longOne[k], shortOne[k]);
			if (k > 0) {
				longOne[k] = Math.max(longOne[k], longOne[k - 1]);
			}
		}
		for (int k = shortLength; k < longLength; ++k) {
			if (longOne[k] >= longOne[k - 1]) {
				break;
			}
			longOne[k] = longOne[k - 1];
		}
		return longOne;
	}

	public static class Tester {
		public static void main(final String[] args) {
			int iArg = 0;
			final File dFile = new File(args[iArg++]);
			final File csvFile = new File(args[iArg++]);
			TreeMap<Integer, double[]> distancesArrayMap = null;
			try (InputStream dInputStream = new FileInputStream(dFile)) {
				distancesArrayMap = FileCache.getDistancesObject(dInputStream);
			} catch (final FileNotFoundException e1) {
			} catch (final IOException e1) {
			}
			try (final PrintStream printStream = new PrintStream(csvFile)) {
				boolean first = true;
				int maxLength = 0;
				for (final Map.Entry<Integer, double[]> entry : distancesArrayMap.entrySet()) {
					printStream.format("%s%d", first ? "" : ",", entry.getKey());
					first = false;
					maxLength = Math.max(maxLength, entry.getValue().length);
				}
				for (int k = 0; k < maxLength; ++k) {
					printStream.print("\n");
					first = true;
					for (final Map.Entry<Integer, double[]> entry : distancesArrayMap.entrySet()) {
						final double[] distances = entry.getValue();
						String s = "";
						if (k < distances.length) {
							final double nmi = distances[k] / _NmiToR;
							s = String.format("%.3f", nmi);
						}
						printStream.format("%s%s", first ? "" : ",", s);
						first = false;
					}
				}
			} catch (final IOException e) {
			}
		}
	}
}
