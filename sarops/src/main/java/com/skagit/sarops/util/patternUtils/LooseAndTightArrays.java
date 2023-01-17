package com.skagit.sarops.util.patternUtils;

import com.skagit.util.navigation.LatLng3;

class LooseAndTightArrays {
	final double[] _looseLats;
	final double[] _looseLngs;
	final double[] _tightLats;
	final double[] _tightLngs;

	LooseAndTightArrays(final SphericalTimedSegs sphericalTimedSegs,
			final SphericalTimedSegs.LoopType loopType) {
		final LatLng3[] looseCorners =
				sphericalTimedSegs.getLooseLatLngs(loopType);
		if (looseCorners == null) {
			_looseLats = _looseLngs = null;
		} else {
			final int nLoose = looseCorners.length;
			_looseLats = new double[nLoose];
			_looseLngs = new double[nLoose];
			for (int k = 0; k < nLoose; ++k) {
				final LatLng3 latLng = looseCorners[k];
				_looseLats[k] = latLng.getLat();
				_looseLngs[k] = latLng.getLng();
			}
		}
		final LatLng3[] tightCorners =
				sphericalTimedSegs.getTightLatLngs(loopType);
		if (tightCorners == null) {
			_tightLats = _tightLngs = null;
		} else {
			final int nTight = tightCorners.length;
			_tightLats = new double[nTight];
			_tightLngs = new double[nTight];
			for (int k = 0; k < nTight; ++k) {
				final LatLng3 latLng = tightCorners[k];
				_tightLats[k] = latLng.getLat();
				_tightLngs[k] = latLng.getLng();
			}
		}
	}
}