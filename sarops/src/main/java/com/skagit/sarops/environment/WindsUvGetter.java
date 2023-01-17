package com.skagit.sarops.environment;

import java.util.BitSet;

import org.w3c.dom.Element;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.navigation.LatLng3;

/**
 * Key method is getWindUv. Just slightly different than a
 * {@link com.skagit.sarops.environment.CurrentsUvGetter}.
 *
 */
public interface WindsUvGetter {
	String _WindsTag = "Winds";

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
	DataForOnePointAndTime getDownWindData(long refSecs, LatLng3 latLng,
			String interpolationMode);

	long getHalfLifeSecs();

	long getPreDistressHalfLifeSecs();

	void close(String interpolationMode);

	/**
	 * Be ready to get (latLng,refSecs), but if there's work to do to do so,
	 * make sure that you can get any latLng in refSecs.
	 */
	void incrementalPrepare(long refSecs, LatLng3 latLng,
			BoxDefinition boxDefinition);

	boolean prepareIsTrivial();

	void finishPrepare();

	boolean useRandomDuringUpdates();

	WindsUvGetter getWindsUvGetter2(BitSet iViews,
			final boolean interpolateInTime);

	String[] getViewNames();

	void writeElement(Element outputWindsElement, Element inputWindsElement,
			Model model);

	boolean isEmpty(SimCaseManager.SimCase simCase);

	void freeMemory();
}
