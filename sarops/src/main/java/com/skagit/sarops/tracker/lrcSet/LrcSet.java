package com.skagit.sarops.tracker.lrcSet;

import java.util.ArrayList;
import java.util.Collections;

import org.w3c.dom.Element;

import com.skagit.util.Integrator;
import com.skagit.util.LsFormatter;

public class LrcSet implements Comparable<LrcSet> {
	final private static double _RelativeErrorThreshold =
			LateralRangeCurve._RelativeErrorThreshold;
	final ArrayList<LateralRangeCurve> _lrcList;
	private double _sweepWidth;

	public LrcSet() {
		_lrcList = new ArrayList<>();
		_sweepWidth = Double.NaN;
	}

	public double getSweepWidth() {
		if (_sweepWidth >= 0d) {
			return _sweepWidth;
		}
		synchronized (this) {
			if (_sweepWidth >= 0d) {
				return _sweepWidth;
			}
			final int nLrcs = _lrcList == null ? 0 : _lrcList.size();
			if (nLrcs == 0) {
				_sweepWidth = 0d;
				return _sweepWidth;
			}
			if (nLrcs == 1) {
				_sweepWidth = _lrcList.get(0)._sweepWidth;
				return _lrcList.get(0)._sweepWidth;
			}
			final long ell0 = System.currentTimeMillis();
			final double ltOnUp =
					computeHalfIntegral(/* lt= */true, /* up= */true, nLrcs);
			final long ell1 = System.currentTimeMillis();
			final double rtOnDn =
					computeHalfIntegral(/* lt= */false, /* up= */false, nLrcs);
			final long ell2 = System.currentTimeMillis();
			final double ltIsUp = ltOnUp + rtOnDn;
			final double ltOnDn =
					computeHalfIntegral(/* lt= */true, /* up= */false, nLrcs);
			final long ell3 = System.currentTimeMillis();
			final double rtOnUp =
					computeHalfIntegral(/* lt= */false, /* up= */true, nLrcs);
			final long ell4 = System.currentTimeMillis();
			final double ltIsDn = ltOnDn + rtOnUp;
			_sweepWidth = (ltIsUp + ltIsDn) / 2d;
			if (true) {
			} else {
				System.out.printf("\n%d\n%d\n%d\n%d", ell1 - ell0, ell2 - ell1,
						ell3 - ell2, ell4 - ell3);
			}
		}
		return _sweepWidth;
	}

	private double computeHalfIntegral(final boolean lt, final boolean up,
			final int nLrcs) {
		double maxRange = 0d;
		double minRange = Double.POSITIVE_INFINITY;
		for (int k = 0; k < nLrcs; ++k) {
			final LateralRangeCurve lrc = _lrcList.get(k);
			final double thisMinRange;
			final double thisMaxRange;
			final double thisMinLkAngl;
			final double thisMaxLkAngl;
			if (lrc.isLtRt()) {
				thisMinRange = lt ? lrc.getLtMinRange() : lrc.getRtMinRange();
				thisMaxRange = lt ? lrc.getLtMaxRange() : lrc.getRtMaxRange();
				thisMinLkAngl = lt ? lrc.getLtMinLkAngl() : lrc.getRtMinLkAngl();
				thisMaxLkAngl = lt ? lrc.getLtMaxLkAngl() : lrc.getRtMaxLkAngl();
			} else {
				thisMinRange = up ? lrc.getUpMinRange() : lrc.getDnMinRange();
				thisMaxRange = up ? lrc.getUpMaxRange() : lrc.getDnMaxRange();
				thisMinLkAngl = up ? lrc.getUpMinLkAngl() : lrc.getDnMinLkAngl();
				thisMaxLkAngl = up ? lrc.getUpMaxLkAngl() : lrc.getDnMaxLkAngl();
			}
			if (!(thisMaxRange > thisMinRange) || !(thisMaxLkAngl > thisMinLkAngl)) {
				continue;
			}
			minRange = Math.min(minRange, thisMinRange);
			maxRange = Math.max(maxRange, thisMaxRange);
		}
		if (!(maxRange > minRange)) {
			return 0d;
		}
		final Integrator.IntegrableFunction f =
				new Integrator.IntegrableFunction() {

					@Override
					public double valueAt(final double x) {
						double pFail = 1d;
						for (int k = 0; k < nLrcs; ++k) {
							final LateralRangeCurve lrc = _lrcList.get(k);
							final double thisPod;
							if (lrc.isLtRt()) {
								thisPod = lt ? lrc.ltCpaToPod(x) : lrc.rtCpaToPod(x);
							} else {
								thisPod = up ? lrc.upCpaToPod(x) : lrc.dnCpaToPod(x);
							}
							if (thisPod > 0d) {
								pFail *= 1d - thisPod;
							}
						}
						return 1d - pFail;
					}
				};
		final double integral = Integrator.simpson(f, minRange, maxRange,
				_RelativeErrorThreshold / 2d);
		return integral;
	}

	public int getNLrcs() {
		return _lrcList == null ? 0 : _lrcList.size();
	}

	public void add(final LateralRangeCurve lrc) {
		_lrcList.add(lrc);
		_lrcList.trimToSize();
		Collections.sort(_lrcList);
		_sweepWidth = Double.NaN;
	}

	public void replaceAllLrcs(final LateralRangeCurve lrc) {
		_lrcList.clear();
		add(lrc);
	}

	@Override
	public int compareTo(final LrcSet lrcSet) {
		final int myN = getNLrcs();
		final int hisN = lrcSet.getNLrcs();
		if (myN != hisN) {
			return myN < hisN ? -1 : 1;
		}
		for (int k = 0; k < myN; ++k) {
			final LateralRangeCurve myLrc = getLrc(k);
			final LateralRangeCurve hisLrc = lrcSet.getLrc(k);
			final int compareValue = myLrc.compareTo(hisLrc);
			if (compareValue != 0) {
				return compareValue;
			}
		}
		return 0;
	}

	public LateralRangeCurve getLrc(final int k) {
		final int n = getNLrcs();
		if (k < 0 || k > n) {
			return null;
		}
		return _lrcList.get(k);
	}

	public double getMaxRange() {
		double maxRange = 0d;
		final int n = getNLrcs();
		for (int k = 0; k < n; ++k) {
			final LateralRangeCurve lrc = getLrc(k);
			maxRange = Math.max(maxRange, lrc.getMaxRange());
		}
		return maxRange;
	}

	public boolean isBlind() {
		final int n = getNLrcs();
		for (int k = 0; k < n; ++k) {
			final LateralRangeCurve lrc = getLrc(k);
			if (!lrc.isBlind()) {
				return false;
			}
		}
		return true;
	}

	public boolean isNearSighted() {
		final int n = getNLrcs();
		for (int k = 0; k < n; ++k) {
			final LateralRangeCurve lrc = getLrc(k);
			if (!lrc.isNearSighted()) {
				return false;
			}
		}
		return true;
	}

	public void write(final LsFormatter formatter, final Element element) {
		final int n = getNLrcs();
		for (int k = 0; k < n; ++k) {
			final LateralRangeCurve lrc = getLrc(k);
			final Element sensorElement = formatter.newChild(element, "SENSOR");
			lrc.addAttributes(sensorElement);
		}
	}

	public String getString() {
		final int nLrcs = _lrcList.size();
		String s = String.format("LrcSet with %d Lrc's and SW[%.2f]", nLrcs,
				getSweepWidth());
		for (int k = 0; k < nLrcs; ++k) {
			final LateralRangeCurve lrc = _lrcList.get(k);
			s += "\n" + lrc.getString();
		}
		return s;
	}

	@Override
	public String toString() {
		return getString();
	}

}
