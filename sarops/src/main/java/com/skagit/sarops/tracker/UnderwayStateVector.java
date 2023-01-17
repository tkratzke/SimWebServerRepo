package com.skagit.sarops.tracker;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.Scenario;
import com.skagit.sarops.model.SearchObjectType;
import com.skagit.sarops.model.preDistressModel.PreDistressModel;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.randomx.Randomx;
import com.skagit.util.shorelineFinder.ShorelineFinder;

public class UnderwayStateVector extends StateVector {
	public UnderwayStateVector(final StateVector predecessor,
			final StateVectorType stateVectorType, final long simSecs,
			final boolean updateParticleTail) {
		super(predecessor, stateVectorType, simSecs, updateParticleTail);
		setLatLng(getLatLng());
		setStateVectorType(stateVectorType);
	}

	public UnderwayStateVector(final Particle particle, final long simSecs,
			final LatLng3 latLng, final StateVectorType stateVectorType,
			final boolean updateParticleTail) {
		super(particle, simSecs, latLng, stateVectorType, updateParticleTail);
	}

	@Override
	public StateVector timeUpdate(final Tracker tracker,
			final Scenario scenario, final long[] simSecsS, final int iT) {
		final long particleSimSecs = getSimSecs();
		final long simSecsToUpdateTo = simSecsS[iT];
		if (particleSimSecs > simSecsToUpdateTo) {
			return this;
		}
		final SimCaseManager.SimCase simCase = tracker.getSimCase();
		final MyLogger logger = SimCaseManager.getLogger(simCase);
		final UnderwayParticle particle = (UnderwayParticle) getParticle();
		final long distressSimSecs = particle.getDistressSimSecs();
		final long underwayDuration =
				Math.min(distressSimSecs, simSecsToUpdateTo) - particleSimSecs;
		final UnderwayStateVector underwayStateVector0 = this;
		final PreDistressModel.Itinerary itinerary = particle.getItinerary();
		final UnderwayStateVector underwayStateVector1;
		if (underwayDuration > 0) {
			underwayStateVector1 = itinerary.move(simCase, scenario,
					underwayStateVector0, underwayDuration);
		} else {
			underwayStateVector1 = underwayStateVector0;
		}
		/** If there was no distress, we have our answer. */
		if (simSecsToUpdateTo < distressSimSecs) {
			return underwayStateVector1;
		}
		/** Distress occurred. */
		final ParticlesFile particlesFile = tracker.getParticlesFile();
		final ParticleIndexes prtclIndxs = particle.getParticleIndexes();
		final boolean updateParticleTail = true;
		final DistressStateVector adriftStateVector = new DistressStateVector(
				underwayStateVector1, distressSimSecs, updateParticleTail);
		final LatLng3 distressLatLng = underwayStateVector1.getLatLng();
		adriftStateVector.setLatLng(distressLatLng);
		if (particle.getDistressLatLng() == null) {
			particle.setDistressLatLng(distressLatLng);
			particlesFile.setDistressLatLng(prtclIndxs, distressLatLng);
		}
		/** Check for landed. */
		final Model model = tracker.getModel();
		final ShorelineFinder shorelineFinder = model.getShorelineFinder();
		final int distressLevel =
				shorelineFinder.getLevel(logger, distressLatLng);
		final boolean landed = distressLevel % 2 == 1;
		final long distressRefSecs = model.getRefSecs(distressSimSecs);
		if (landed) {
			if (particle.setLandingSimSecs(distressSimSecs)) {
				particlesFile.setLandingRefSecs(prtclIndxs, distressRefSecs);
			}
			adriftStateVector.setIsStuckOnLand();
		} else {
			/** Check for anchoring. */
			final SearchObjectType searchObjectType =
					particle.getDistressObjectType();
			final Randomx random = particle.getRandom();
			final boolean isAnchored = searchObjectType.anchors(distressLatLng,
					random.nextDouble(), model.getEtopo());
			if (isAnchored) {
				particle.setAnchoringSimSecs(distressSimSecs);
				particlesFile.setAnchoringRefSecs(prtclIndxs, distressRefSecs);
				adriftStateVector.setIsAnchored();
			}
		}
		return adriftStateVector.timeUpdate(tracker, scenario, simSecsS, iT);
	}

	@Override
	public String getDescription() {
		return "Underway particle of type " + getSearchObjectType().getId();
	}

	@Override
	public boolean isDistress() {
		return false;
	}
}
