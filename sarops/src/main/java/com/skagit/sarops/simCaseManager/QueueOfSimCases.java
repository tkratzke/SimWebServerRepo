package com.skagit.sarops.simCaseManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.TreeSet;

import com.skagit.util.Constants;
import com.skagit.util.StringUtilities;

class QueueOfSimCases {
	private static Comparator<SimCaseManager.SimCase> _ByNameOnly =
			new Comparator<>() {
				@Override
				public int compare(final SimCaseManager.SimCase o1,
						final SimCaseManager.SimCase o2) {
					final int compareValue = o1.getCleanedUpFilePath()
							.compareTo(o2.getCleanedUpFilePath());
					if (compareValue != 0) {
						return compareValue;
					}
					return o1._rawClassName.compareTo(o2._rawClassName);
				}
			};

	private static Comparator<SimCaseManager.SimCase> _ByTimeOnly =
			new Comparator<>() {
				@Override
				public int compare(final SimCaseManager.SimCase o1,
						final SimCaseManager.SimCase o2) {
					final long ms1 = o1.getCreationMs();
					final long ms2 = o2.getCreationMs();
					return Long.compare(ms1, ms2);
				}
			};

	static Comparator<SimCaseManager.SimCase> _ByNameAndTime =
			new Comparator<>() {
				@Override
				public int compare(final SimCaseManager.SimCase o1,
						final SimCaseManager.SimCase o2) {
					final int compareValue = _ByNameOnly.compare(o1, o2);
					if (compareValue != 0) {
						return compareValue;
					}
					return _ByTimeOnly.compare(o1, o2);
				}
			};

	static Comparator<SimCaseManager.SimCase> _ByTimeAndName =
			new Comparator<>() {
				@Override
				public int compare(final SimCaseManager.SimCase o1,
						final SimCaseManager.SimCase o2) {
					int compareValue = _ByTimeOnly.compare(o1, o2);
					if (compareValue == 0) {
						compareValue = _ByNameAndTime.compare(o1, o2);
					}
					return _ByNameOnly.compare(o1, o2);
				}
			};

	final private TreeSet<SimCaseManager.SimCase> _byNameAndTime =
			new TreeSet<>(_ByNameAndTime);

	final private TreeSet<SimCaseManager.SimCase> _byTimeAndName =
			new TreeSet<>(_ByTimeAndName);
	private int _nDone;
	private int _nDropped;

	final private SimCaseManager _simCaseManager;
	private int _nStatusOnlyInRow = 0;
	private String _statusOnlyFilePath = null;

	QueueOfSimCases(final SimCaseManager simCaseManager) {
		_simCaseManager = simCaseManager;
		_nDone = _nDropped = 0;
	}

	boolean add(final SimCaseManager.SimCase simCase) {
		synchronized (this) {
			_byNameAndTime.add(simCase);
			_byTimeAndName.add(simCase);
			return true;
		}
	}

	void remove(final SimCaseManager.SimCase simCase) {
		synchronized (this) {
			_byNameAndTime.remove(simCase);
			_byTimeAndName.remove(simCase);
		}
	}

	SimCaseManager.SimCase[] getSimCasesOldestFirst() {
		final SimCaseManager.SimCase[] returnValue;
		synchronized (this) {
			final int n = _byTimeAndName.size();
			returnValue = _byTimeAndName.toArray(new SimCaseManager.SimCase[n]);
		}
		return returnValue;
	}

	final private static String _EmptyFilePath = "" + Constants._EmptySet;

	void cleanUpQueue(final boolean statusOnly, final String filePathIn,
			String m) {
		if (m == null) {
			m = "NoMsg";
		}
		final String filePath;
		if (filePathIn == null) {
			filePath = _EmptyFilePath;
		} else {
			filePath = StringUtilities.cleanUpFilePath(filePathIn);
		}
		int nWaiting = 0;
		int nActive = 0;
		final ArrayList<SimCaseManager.SimCase> toRemove =
				new ArrayList<>();
		synchronized (this) {
			for (final SimCaseManager.SimCase simCase : _byNameAndTime) {
				final SimCaseManager.ProgressState progressState =
						simCase.getProgressState();
				if (progressState == SimCaseManager.ProgressState.DONE
						|| progressState == SimCaseManager.ProgressState.DROPPED) {
					if (simCase.isVeryOld()) {
						toRemove.add(simCase);
					}
				} else if (progressState == SimCaseManager.ProgressState.WAITING) {
					++nWaiting;
				} else if (progressState == SimCaseManager.ProgressState.ACTIVE) {
					++nActive;
				}
			}
			_byTimeAndName.removeAll(toRemove);
			_byNameAndTime.removeAll(toRemove);
		}
		if (statusOnly) {
			if ((filePath == null) != (_statusOnlyFilePath == null)) {
				_nStatusOnlyInRow = 0;
				_statusOnlyFilePath = filePath;
			} else if (filePath != null) {
				if (filePath.equalsIgnoreCase(_statusOnlyFilePath)) {
					++_nStatusOnlyInRow;
				} else {
					_nStatusOnlyInRow = 0;
					_statusOnlyFilePath = filePath;
				}
			} else {
				++_nStatusOnlyInRow;
			}
		}
		final String basicMessageF =
				"%s nStatusOnlyInRow[%d] filePath[%s] nWaiting[%d] nActive[%d] nDone[%d] nDropped[%d]";
		final String basicMessage =
				String.format(basicMessageF, m, _nStatusOnlyInRow,
						_statusOnlyFilePath, nWaiting, nActive, _nDone, _nDropped);
		if (statusOnly) {
			if (_nStatusOnlyInRow < 3 || (_nStatusOnlyInRow % 500 == 0)) {
				_simCaseManager._globalLogger.out(basicMessage);
			}
			return;
		}
		_simCaseManager._globalLogger.out(basicMessage);
		_nStatusOnlyInRow = 0;
	}

	SimCaseManager.SimCase getSimCase(final String cleanedUpFilePath) {
		synchronized (this) {
			for (final SimCaseManager.SimCase simCase : _byNameAndTime) {
				if (simCase.getCleanedUpFilePath()
						.equalsIgnoreCase(cleanedUpFilePath)) {
					return simCase;
				}
			}
		}
		return null;
	}

	int getNDone() {
		return _nDone;
	}

	int getNDropped() {
		return _nDropped;
	}

	int[] getNWaitingActiveDoneDropped() {
		int nWaiting = 0;
		int nActive = 0;
		for (final SimCaseManager.SimCase simCase : _byNameAndTime) {
			final SimCaseManager.ProgressState progressState =
					simCase._progressState;
			if (progressState == SimCaseManager.ProgressState.WAITING) {
				++nWaiting;
			} else if (progressState == SimCaseManager.ProgressState.ACTIVE) {
				++nActive;
			}
		}
		return new int[] {
				nWaiting, nActive, _nDone, _nDropped
		};
	}

	void recordEndOfCase(
			final SimCaseManager.ProgressState completionProgressState) {
		if (completionProgressState == SimCaseManager.ProgressState.DONE) {
			++_nDone;
		} else {
			++_nDropped;
		}
	}
}
