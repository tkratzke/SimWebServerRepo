package com.skagit.sarops.simCaseManager;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TreeSet;

import com.skagit.util.Constants;
import com.skagit.util.TimeUtilities;

class ChunkReporter {
	final private long _activeTimeInMillis;
	final private int _nProgressSteps;
	final private File _progressDirectory;
	final private String[][] _sections;
	final private String[] _sectionNames;
	final private boolean[] _criticalSections;
	final private int[] _chunkStopper;
	final private long[] _chunkTimesMs;
	final private int _nChunks;
	private int _nChunksReported;

	/**
	 * A Critical Section is one for which every chunk within it demands at
	 * least one progress step.
	 */
	ChunkReporter(final File progressDirectory, final int nProgressSteps,
			final String[] sectionNames, final boolean[] criticalSections,
			final String[][] sections) {
		/** Unpack the input data. */
		final ArrayList<Integer> keptSections = new ArrayList<>();
		final int nRawSections = sections.length;
		int nKeptChunks = 0;
		for (int i = 0; i < nRawSections; ++i) {
			final String[] section = sections[i];
			final int m = section == null ? 0 : section.length;
			if (m > 0) {
				keptSections.add(i);
				nKeptChunks += m;
			}
		}
		_nChunks = nKeptChunks;
		final int n = keptSections.size();
		_sectionNames = new String[n];
		_criticalSections = new boolean[n];
		_sections = new String[n][];
		for (int i = 0; i < n; ++i) {
			final int rawI = keptSections.get(i);
			_sections[i] = sections[rawI];
			_sectionNames[i] = sectionNames[rawI];
			if (criticalSections == null || rawI >= criticalSections.length) {
				_criticalSections[i] = false;
			} else {
				_criticalSections[i] = criticalSections[rawI];
			}
		}
		_progressDirectory = progressDirectory;
		/** Recall the definition of a Critical Section from above. */
		final TreeSet<Integer> criticalKs = new TreeSet<>();
		for (int i = 0; i < n; ++i) {
			final int m = _sections[i].length;
			if (_criticalSections[i]) {
				/** Every one is critical. */
				for (int j = 0; j < m; ++j) {
					final int[] indexPair = new int[] { i, j };
					final int k = indexPairToK(indexPair);
					criticalKs.add(k);
				}
			} else {
				/** Only the last one is critical. */
				final int[] indexPair = new int[] { i, m - 1 };
				final int k = indexPairToK(indexPair);
				criticalKs.add(k);
			}
		}
		final int nCriticalKs = criticalKs.size();
		_nProgressSteps = Math.max(nCriticalKs, Math.max(1, nProgressSteps));
		_chunkTimesMs = new long[_nChunks];
		for (int k = 0; k < _nChunks; ++k) {
			_chunkTimesMs[k] = -1;
		}
		final int[] willGet =
				computeWillGet(_nChunks, _nProgressSteps, criticalKs);
		_chunkStopper = new int[_nChunks];
		int runningTotal = 0;
		for (int k = 0; k < _nChunks; ++k) {
			runningTotal += willGet[k];
			_chunkStopper[k] = runningTotal;
		}
		_nChunksReported = 0;
		_activeTimeInMillis = System.currentTimeMillis();
	}

	/** Compute the number of progress steps for each chunk. */
	private static int[] computeWillGet(final int nChunks,
			final int nProgressSteps, final TreeSet<Integer> criticalKs) {
		final int[] willGet = new int[nChunks];
		final boolean tooFew = nProgressSteps < nChunks;
		final int nCriticals = criticalKs.size();
		final double nProgressStepsToAllocate;
		final double nChunksToAward;
		if (tooFew) {
			nProgressStepsToAllocate = nProgressSteps - nCriticals;
			nChunksToAward = nChunks - nCriticals;
		} else {
			/** Everyone will get at least 1 anyway, so we ignore criticalKs. */
			nProgressStepsToAllocate = nProgressSteps;
			nChunksToAward = nChunks;
		}
		final double fairShare = nProgressStepsToAllocate / nChunksToAward;
		int kk = 0;
		int cumAward = 0;
		for (int k = 0; k < nChunks; ++k) {
			if (tooFew && criticalKs.contains(k)) {
				willGet[k] = 1;
			} else {
				++kk;
				final double currentPot = kk * fairShare - cumAward;
				final int award = (int) Math.rint(currentPot);
				willGet[k] = award;
				cumAward += award;
			}
		}
		return willGet;
	}

	private int[] kToIndexPair(final int k) {
		int cum = 0;
		final int n = _sections.length;
		for (int i = 0; i < n; ++i) {
			final String[] section = _sections[i];
			final int m = section.length;
			if (cum + m <= k) {
				cum += m;
				continue;
			}
			final int j = k - cum;
			return new int[] { i, j };
		}
		return null;
	}

	private int indexPairToK(final int[] indexPair) {
		int cum = 0;
		final int thisI = indexPair[0];
		final int thisJ = indexPair[1];
		for (int i = 0; i < thisI; ++i) {
			cum += _sections[i].length;
		}
		final int k = cum + thisJ;
		return k;
	}

	void reportChunkDone(final SimCaseManager.SimCase simCase) {
		reportChunkDone(simCase, /* inFatalHandler= */false);
	}

	void reportChunkDone(final SimCaseManager.SimCase simCase,
			final boolean inFatalHandler) {
		if (_nChunksReported >= _nChunks) {
			return;
		}
		final int first =
				_nChunksReported == 0 ? 0 : _chunkStopper[_nChunksReported - 1];
		final int stopper = _chunkStopper[_nChunksReported];
		final int[] indexPair = kToIndexPair(_nChunksReported);
		final int i = indexPair[0];
		final String[] section = _sections[i];
		final int m = section.length;
		final int j = indexPair[1];
		final String chunkName = section[j];
		final String progressStepsString;
		if (first == stopper) {
			progressStepsString = String
					.format("No Progress Steps for \"%s\" (%d,%d).", chunkName, i, j);
		} else {
			progressStepsString =
					String.format("Progress Steps[%d-%d]", first, stopper - 1);
			/** If _nChunksReported is a critical chunk, info the message. */
			if (j == m - 1) {
				final String sectionNamesI = _sectionNames[i];
				final String sectionJ = section[j];
				final String message =
						String.format("Done with Section \"%s\"(\"%s\") k[%d]).", //
								sectionNamesI, sectionJ, _nChunksReported);
				SimCaseManager.out(simCase, message);
				final String message2 = String.format("%s Section Completion: %s",
						simCase.getName(), message);
				SimCaseManager.out(simCase, message2);
			}
		}
		/** Record the times of completion. */
		_chunkTimesMs[_nChunksReported] = System.currentTimeMillis();
		SimCaseManager.out(simCase, progressStepsString);
		/** Dump the progress steps files. If there is a problem here. */
		if (_progressDirectory != null) {
			for (int step = first; step < stopper; ++step) {
				final String fileName =
						"progress_" + (step + 1) + "_" + _nProgressSteps + ".txt";
				final File stepFile = new File(_progressDirectory, fileName);
				final long nowRefSecs = TimeUtilities
						.convertToRefSecs(System.currentTimeMillis() / 1000L);
				try (final PrintWriter writer = new PrintWriter(stepFile)) {
					writer.printf("Time %s: %s",
							TimeUtilities.formatTime(nowRefSecs, true),
							progressStepsString);
					writer.close();
				} catch (final IOException e) {
					if (!inFatalHandler) {
						MainRunner.HandleFatal(simCase, new RuntimeException(e));
					}
					++_nChunksReported;
					return;
				}
				final int nStepsDone = step + 1;
				final int mod10 = nStepsDone % 10;
				final String suffix = mod10 == 1 ? "st" :
						(mod10 == 2 ? "nd" : (mod10 == 3 ? "rd" : "th"));
				final String format =
						"Succeeded recording %d-%s Progress Step (Chunk %d(%s))";
				final String s = String.format(format, step + 1, suffix,
						_nChunksReported, chunkName);
				SimCaseManager.out(simCase, s);
				final String s2 = String.format("%s ProgressStep Recorded: %s",
						simCase.getName(), s);
				SimCaseManager.out(simCase, s2);
			}
		}
		++_nChunksReported;
	}

	void reportChunksDone(final SimCaseManager.SimCase simCase,
			final int nChunksDone, final boolean inFatalHandler) {
		while (_nChunksReported < Math.min(_nChunks, nChunksDone)) {
			reportChunkDone(simCase, inFatalHandler);
		}
	}

	void reportChunksDone(final SimCaseManager.SimCase simCase,
			final int nChunksDone) {
		reportChunksDone(simCase, nChunksDone, /* inFatalHandler= */false);
	}

	void runOutChunks(final SimCaseManager.SimCase simCase) {
		reportChunksDone(simCase, _nChunks, /* inFatalHandler= */false);
	}

	void runOutChunks(final SimCaseManager.SimCase simCase,
			final boolean inFatalHandler) {
		reportChunksDone(simCase, _nChunks, inFatalHandler);
	}

	int getNProgressStepsDone() {
		if (_nChunksReported >= _nChunks) {
			return _nProgressSteps;
		}
		return _chunkStopper[_nChunksReported];
	}

	int getNProgressSteps() {
		return _nProgressSteps;
	}

	boolean isDone() {
		return _nChunksReported >= _nChunks;
	}

	String getString() {
		final StringBuffer b = new StringBuffer("{");
		for (int k = 0; k < _nChunks; ++k) {
			final int[] indexPair = kToIndexPair(k);
			final int i = indexPair[0];
			final int j = indexPair[1];
			final String sectionName = _sectionNames[i];
			final String[] section = _sections[i];
			final String chunkName = section[j];
			b.append("\n  ").append(sectionName).append('+').append(chunkName)
					.append(": ");
			final int first = k == 0 ? 0 : _chunkStopper[k - 1];
			final int stopper = _chunkStopper[k];
			b.append(String.format("Steps=[%d,%d)", first, stopper));
		}
		b.append("\n}");
		return new String(b);
	}

	void dumpCriticalTimes(final SimCaseManager.SimCase simCase,
			final String totalCaption, final long entryTimeMs,
			final File timesFile) {
		final ArrayList<int[]> toPrint = new ArrayList<>();
		for (int i = 0; i < _sectionNames.length; ++i) {
			final String[] section = _sections[i];
			final int m = section.length;
			final int start = _criticalSections[i] ? 0 : m - 1;
			for (int j = start; j < m; ++j) {
				toPrint.add(new int[] { i, j });
			}
		}
		try (final PrintWriter timesWriter = new PrintWriter(timesFile)) {
			if (entryTimeMs > 0) {
				final String startDateString =
						new SimpleDateFormat("M/d/yy h:mm:ss a")
								.format(new Date(entryTimeMs));
				timesWriter.printf("Run started at %s.\n", startDateString);
				final String activeDateString =
						new SimpleDateFormat("M/d/yy h:mm:ss a")
								.format(new Date(_activeTimeInMillis));
				timesWriter.printf("Appeared Active at %s.\n\n", activeDateString);
			}
			long lastCriticalTimeInMills = entryTimeMs;
			boolean havePrinted = false;
			final String nl = Constants._NewLine;
			for (final int[] indexPair : toPrint) {
				final int i = indexPair[0];
				final String[] section = _sections[i];
				final int j = indexPair[1];
				final String sectionName = _sectionNames[i];
				final String chunkName = section[j];
				final int k = indexPairToK(indexPair);
				final long elapsedTimeInMillis =
						_chunkTimesMs[k] - lastCriticalTimeInMills;
				final double nSeconds = elapsedTimeInMillis / 1000.0;
				timesWriter.printf(
						(!havePrinted ? "" : Constants._NewLine) +
								" %s(%c%s) took %.3f seconds.",
						chunkName, Constants._SectionSymbol, sectionName, nSeconds);
				lastCriticalTimeInMills = _chunkTimesMs[k];
				havePrinted = true;
			}
			timesWriter.printf(
					"%s%s@@@ Total " + totalCaption + " took %.3f seconds. @@@", nl,
					nl, (System.currentTimeMillis() - entryTimeMs) / 1000.0);
			final String reportDateString =
					new SimpleDateFormat("M/d/yy h:mm:ss a")
							.format(new Date(System.currentTimeMillis()));
			timesWriter.printf("%sTimes reported at %s", nl, reportDateString);
			timesWriter.close();
		} catch (final IOException e) {
			MainRunner.HandleFatal(simCase, new RuntimeException(e));
		}
	}

	public static void main(final String[] args) {
		final String[] sectionNames = new String[] { "A's", "Empty", "B's" };
		final String[][] sections =
				new String[][] { new String[] { "A1", "A2" }, new String[] {},
						new String[] { "B1" } };
		final File progressDirectory = null;
		final int nProgressSteps = 4;
		final ChunkReporter chunkReporter = new ChunkReporter(progressDirectory,
				nProgressSteps, sectionNames, null, sections);
		final String s1 = chunkReporter.getString();
		System.out.printf("\n%s", s1);
	}
}
