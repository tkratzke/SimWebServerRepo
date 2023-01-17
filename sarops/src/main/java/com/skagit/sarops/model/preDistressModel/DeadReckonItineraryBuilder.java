package com.skagit.sarops.model.preDistressModel;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.preDistressModel.PreDistressModel.Itinerary;
import com.skagit.sarops.model.preDistressModel.PreDistressModel.ItineraryBuilder;
import com.skagit.sarops.model.preDistressModel.sail.SailData;
import com.skagit.sarops.model.preDistressModel.sail.Sdi;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.util.randomx.Randomx;

public class DeadReckonItineraryBuilder extends ItineraryBuilder {
	final SimCaseManager.SimCase _simCase;
	final private DeadReckon _deadReckon;

	public DeadReckonItineraryBuilder(final SimCaseManager.SimCase simCase,
			final DeadReckon deadReckon) {
		_simCase = simCase;
		_deadReckon = deadReckon;
	}

	@Override
	public Itinerary buildItinerary(final Model model, final Randomx r,
			final long birthRefSecs, final ParticleIndexes prtclIndxs,
			final long[] refSecsS) {
		final SailData sailData = _deadReckon.getScenario().getSailData();
		final Sdi sdi;
		if (sailData != null) {
			sdi = new Sdi(sailData, prtclIndxs, r);
		} else {
			sdi = null;
		}
		return new DeadReckonItinerary(_simCase, _deadReckon, birthRefSecs, r,
				sdi);
	}
}
