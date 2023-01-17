package com.skagit.sarops.planner.solver.pqSolver.deconflicter;

import com.skagit.util.MathX;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.TangentCylinder;

class Twister {
	final TangentCylinder _tangentCylinder;
	final double[] _v;
	final double[] _u;

	Twister(final LatLng3 latLng, final double hdgOfUDirection) {
		_tangentCylinder = TangentCylinder.getTangentCylinder(latLng);
		final double alpha = Math.toRadians(90d - hdgOfUDirection);
		final double c = MathX.cosX(alpha);
		final double s = MathX.sinX(alpha);
		_u = new double[] {
				c, s
		};
		_v = new double[] {
				-s, c
		};
	}

	double[] convert(final LatLng3 latLng) {
		final TangentCylinder.FlatLatLng flatLatLng = _tangentCylinder
				.convertToMyFlatLatLng(latLng);
		final double[] xy = new double[] {
				flatLatLng.getEastOffset(), flatLatLng.getNorthOffset()
		};
		final double[] converted = new double[2];
		converted[0] = NumericalRoutines.dotProduct(xy, _u);
		converted[1] = NumericalRoutines.dotProduct(xy, _v);
		return converted;
	}

	TangentCylinder.FlatLatLng unconvert(final double twX, final double twY) {
		final double e = (twX * _u[0]) + (twY * _v[0]);
		final double n = (twX * _u[1]) + (twY * _v[1]);
		final TangentCylinder.FlatLatLng flatLatLng = _tangentCylinder.new FlatLatLng(
				e, n);
		return flatLatLng;
	}
}