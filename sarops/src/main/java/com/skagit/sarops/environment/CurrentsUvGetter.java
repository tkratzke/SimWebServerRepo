package com.skagit.sarops.environment;

import java.util.BitSet;

import org.w3c.dom.Element;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.Scenario;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.simCaseManager.SimCaseManager.SimCase;
import com.skagit.sarops.tracker.DistressStateVector;
import com.skagit.util.navigation.LatLng3;

/**
 * Key method is {@link #getDriftUv(long, LatLng3, String, boolean)}. Just
 * slightly different than a
 * {@link com.skagit.sarops.environment.WindsUvGetter} .
 */
public interface CurrentsUvGetter {
	String _DriftsTag = "Drifts";

	/**
	 * The most important method.
	 *
	 * @param refSecs           time at which we want the drift calculated. In
	 *                          reference time (seconds since 2000-01-01,
	 *                          00:00:00)
	 * @param latLng            position at which we want the drift
	 *                          calculated.
	 * @param interpolationMode flag (e.g., {@link Model#_2Closest _2Closest}.
	 * @return A float array [u,v,dU,dV], which are mean east-speed,
	 *         north-speed, east-uncertainty, and north-uncertainty
	 *         respectively (all in knots; uncertainty is standard deviation).
	 */
	DataForOnePointAndTime getCurrentData(long refSecs, LatLng3 latLng,
			String interpolationMode);

	/**
	 * @return The decay factor assuming an exponential decay in correlation
	 *         for a particle.
	 */
	long getHalfLifeSecs(int overallIndex);

	long getPreDistressHalfLifeSecs();

	/**
	 * Be ready to get (latLng, refSecs), but if there's work to do to do so,
	 * make sure that you can get any latLng at refSecs.
	 */
	void incrementalPrepare(long refSecs, LatLng3 latLng,
			BoxDefinition boxDefinition);

	boolean prepareIsTrivial();

	void finishPrepare();

	void close(String inerpolationMode);

	boolean useRandomDuringUpdates();

	CurrentsUvGetter getCurrentsUvGetter2(BitSet iViews,
			boolean interpolateInTime);

	String[] getViewNames();

	void writeElement(Element outputDriftsElement, Element inputDriftsElement,
			Model model);

	boolean isEmpty(SimCaseManager.SimCase simCase);

	void freeMemory();

	int[] getFirstSampleAndPredictionIndex(int nViews);

	boolean hasAuxiliaryProcessing();

	DistressStateVector fillInStateVectorsIfAppropriate(SimCase simCase,
			WindsUvGetter windsUvGetter, Scenario scenario, long[] simSecsS,
			DistressStateVector distressStateVector, long simSecs);
}
