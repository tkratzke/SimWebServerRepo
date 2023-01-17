package com.skagit.sarops.planner.solver.pqSolver.deconflicter;

import com.skagit.sarops.planner.posFunction.PosFunction;
import com.skagit.sarops.planner.posFunction.pvValueArrayPlus.PvValueArrayPlus;
import com.skagit.util.Constants;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.navigation.LatLng3;

public class BearingResult {
	final public BirdsNestDetangler _birdsNestDetangler;
	final public GreatCircleArc _gca;
	final public Loop3[] _bigLoops;
	final public Loop3[] _accordionLoops;
	final public PvValueArrayPlus _plus0;
	final public PvValueArrayPlus _finalPlus;

	public BearingResult(final BirdsNestDetangler birdsNestDetangler,
			final GreatCircleArc gca, final PvValueArrayPlus plus0,
			final PvValueArrayPlus finalPlus, final Loop3[] bigLoops,
			final Loop3[] accordionLoops) {
		_birdsNestDetangler = birdsNestDetangler;
		_gca = gca;
		_plus0 = plus0;
		_finalPlus = finalPlus;
		_bigLoops = bigLoops;
		_accordionLoops = accordionLoops;
	}

	public String getString() {
		final PosFunction posFunction = _birdsNestDetangler._ftPosFunction;
		final double pos = _finalPlus.getPos(posFunction);
		final LatLng3 latLng0 = _gca.getLatLng0();
		final double hdg = _gca.getRoundedInitialHdg();
		final String s;
		if (_birdsNestDetangler.getBestBearingResult() == this) {
			s = String.format("%c%.3f: %s/%.2f", Constants._DaggerSymbol, pos,
					latLng0.getString(5), hdg);
		} else {
			s = String.format("%.3f: %s/%.2f", pos, latLng0.getString(5), hdg);
		}
		return s;
	}

}
