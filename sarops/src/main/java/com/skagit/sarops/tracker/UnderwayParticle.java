/*
 * Created on Jan 30, 2004
 */
package com.skagit.sarops.tracker;

import com.skagit.sarops.model.Scenario;
import com.skagit.sarops.model.SearchObjectType;
import com.skagit.sarops.model.preDistressModel.PreDistressModel;
import com.skagit.sarops.model.preDistressModel.sail.Sdi;
import com.skagit.util.randomx.Randomx;

public class UnderwayParticle extends Particle {
	final private PreDistressModel.Itinerary _itinerary;

	public UnderwayParticle(final Tracker tracker, final Scenario scenario,
			final ParticleIndexes prtclIndxs, final long birthTimeSimSecs,
			final SearchObjectType originatingSot,
			final SearchObjectType distressObjectType, final long distressRefSecs,
			final PreDistressModel.Itinerary itinerary, final Randomx random) {
		super(tracker, scenario, prtclIndxs, originatingSot, birthTimeSimSecs,
				distressObjectType, distressRefSecs, random);
		_itinerary = itinerary;
	}

	public PreDistressModel.Itinerary getItinerary() {
		return _itinerary;
	}

	@Override
	public Sdi getSdi() {
		return _itinerary.getSdi();
	}

}
