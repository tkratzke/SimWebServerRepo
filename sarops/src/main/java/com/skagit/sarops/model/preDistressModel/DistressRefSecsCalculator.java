package com.skagit.sarops.model.preDistressModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;

import com.skagit.util.NumericalRoutines;
import com.skagit.util.randomx.Randomx;

class DistressRefSecsCalculator {
	final private ArrayList<Step> _steps;
	final private long _startSecs;
	final private long _endSecs;
	/**
	 * _intervalEndpoints is the set of starting points. Its last entry is
	 * _endSecs.
	 */
	long[] _intervalEndpoints;
	/**
	 * _intensities[k] is the intensity from _intervalEndpoints[k] to
	 * _intervalEndpoints[k+1]. It has one fewer entry since there is no interval
	 * corresponding to the last _intervalEndpoints[k].
	 */
	double[] _intensities;
	/**
	 * _cumScaledTimes[k] is the amount of scaled time before
	 * _intervalEndpoints[k]. _cumScaledTimes[0] is by definition 0.
	 */
	double[] _cumScaledTimes;

	private class Step {
		long _stepStartSecs;
		long _stepEndSecs;
		double _intensity;

		Step(final long startSecs, final long endSecs, final double intensity) {
			_stepStartSecs = startSecs;
			_stepEndSecs = endSecs;
			_intensity = intensity;
		}
	}

	public String getString() {
		final int nEndpoints = _intervalEndpoints.length;
		String s = "";
		final long baseTime = _intervalEndpoints[0];
		for (int k = 0; k < nEndpoints - 1; ++k) {
			if (k > 0) {
				s += ", ";
			}
			s += String.format("[Strt%d,I%.1f,CumRange[%.2f,%.2f], End%d]",
					_intervalEndpoints[k] - baseTime, _intensities[k],
					_cumScaledTimes[k], _cumScaledTimes[k + 1],
					_intervalEndpoints[k + 1] - baseTime);
		}
		return s;
	}

	@Override
	public String toString() {
		return getString();
	}

	DistressRefSecsCalculator(final long startSecs, final long endSecs) {
		_startSecs = startSecs;
		_endSecs = endSecs;
		_steps = new ArrayList<>();
		add(1d, _startSecs, _endSecs);
	}

	void add(final double intensity, long startStepSecs, long endStepSecs) {
		startStepSecs = Math.max(startStepSecs, _startSecs);
		endStepSecs = Math.min(endStepSecs, _endSecs);
		if (endStepSecs <= startStepSecs) {
			return;
		}
		synchronized (this) {
			_steps.add(new Step(startStepSecs, endStepSecs, intensity));
			_intervalEndpoints = null;
			_intensities = _cumScaledTimes = null;
		}
	}

	final private static boolean _UseFullDuration =
			System.getProperty("UseFullDuration") != null;

	long computeDistressRefSecs(final Randomx random, final long birthRefSecs,
			final long arrivalRefSecs) {
		if (_intervalEndpoints == null) {
			synchronized (this) {
				if (_intervalEndpoints == null) {
					_steps.trimToSize();
					/** Sort _steps by ascending left endpoint. */
					_steps.sort(new Comparator<Step>() {

						@Override
						public int compare(final Step o1, final Step o2) {
							final long start1 = o1._stepStartSecs;
							final long start2 = o2._stepStartSecs;
							return Long.compare(start1, start2);
						}
					});
					/** Gather and sort _steps' endpoints. */
					final TreeSet<Long> setOfEndpoints = new TreeSet<>();
					final int nSteps = _steps.size();
					for (int k = 0; k < nSteps; ++k) {
						final Step step = _steps.get(k);
						final long stepStart = step._stepStartSecs;
						final long stepEnd = step._stepEndSecs;
						setOfEndpoints.add(stepStart);
						setOfEndpoints.add(stepEnd);
					}
					final int nEndpoints = setOfEndpoints.size();
					if (nEndpoints == 0) {
						return -1L;
					}
					_intervalEndpoints = new long[nEndpoints];
					final Iterator<Long> it = setOfEndpoints.iterator();
					for (int k = 0; k < nEndpoints; ++k) {
						_intervalEndpoints[k] = it.next();
					}
					Arrays.sort(_intervalEndpoints);
					/** Compute the intensities in the gaps between the endpoints. */
					_intensities = new double[nEndpoints - 1];
					for (int k = 0; k < nEndpoints - 1; ++k) {
						final long intervalStart = _intervalEndpoints[k];
						final long intervalEnd = _intervalEndpoints[k + 1];
						/**
						 * For each Step, find the intersection of [stepStart,stepEnd) and
						 * [intervalStart, intervalEnd).
						 */
						double intensity = 1d;
						for (int k2 = 0; k2 < nSteps; ++k2) {
							final Step step = _steps.get(k2);
							final long stepStart = step._stepStartSecs;
							if (stepStart >= intervalEnd) {
								/** No more steps can contribute to interval's intensity. */
								break;
							}
							final long stepEnd = step._stepEndSecs;
							final long intersectionStart =
									Math.max(stepStart, intervalStart);
							final long intersectionEnd = Math.min(stepEnd, intervalEnd);
							if (intersectionEnd > intersectionStart) {
								/** step contributes to this interval's intensity. */
								intensity *= step._intensity;
							}
						}
						_intensities[k] = intensity;
					}
					/** Compute _cumScaledTimes. */
					_cumScaledTimes = new double[nEndpoints];
					_cumScaledTimes[0] = 0d;
					for (int k = 1; k < nEndpoints; ++k) {
						final double intensity = _intensities[k - 1];
						final long gap =
								_intervalEndpoints[k] - _intervalEndpoints[k - 1];
						_cumScaledTimes[k] = _cumScaledTimes[k - 1] + intensity * gap;
					}
				}
			}
		}
		/** Now make the draw. */
		final int nEndpoints = _intervalEndpoints.length;
		final double totalScaledTime = _cumScaledTimes[nEndpoints - 1];
		final double uValue;
		if (_UseFullDuration) {
			uValue = 0.9999;
		} else {
			uValue = Math.max(0.00001, Math.min(0.9999, random.nextDouble()));
		}
		final double scaledTimeDraw = uValue * totalScaledTime;
		final int glbIdx =
				NumericalRoutines.getGlbIndex(_cumScaledTimes, scaledTimeDraw);
		final double localScaledTime = scaledTimeDraw - _cumScaledTimes[glbIdx];
		final double intensity = _intensities[glbIdx];
		final long deltaT = Math.round(localScaledTime / intensity);
		final long returnValue = _intervalEndpoints[glbIdx] + deltaT;
		return returnValue;
	}
}