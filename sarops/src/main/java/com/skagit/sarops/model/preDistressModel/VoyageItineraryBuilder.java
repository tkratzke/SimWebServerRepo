package com.skagit.sarops.model.preDistressModel;

import java.util.List;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.Scenario;
import com.skagit.sarops.model.preDistressModel.PreDistressModel.Itinerary;
import com.skagit.sarops.model.preDistressModel.PreDistressModel.ItineraryBuilder;
import com.skagit.sarops.model.preDistressModel.sail.SailData;
import com.skagit.sarops.model.preDistressModel.sail.SailSeg;
import com.skagit.sarops.model.preDistressModel.sail.Sdi;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.sarops.tracker.StateVectorType;
import com.skagit.util.CartesianUtil;
import com.skagit.util.Constants;
import com.skagit.util.MathX;
import com.skagit.util.cdf.Cdf;
import com.skagit.util.cdf.Cdf.BadCdfException;
import com.skagit.util.cdf.area.Area;
import com.skagit.util.cdf.area.LatLngAndCdfs;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.MotionType;
import com.skagit.util.navigation.NavigationCalculator;
import com.skagit.util.navigation.NavigationCalculatorStatics;
import com.skagit.util.navigation.TangentCylinder;
import com.skagit.util.randomx.Randomx;
import com.skagit.util.randomx.TimeDistribution;

public class VoyageItineraryBuilder extends ItineraryBuilder {
	final private static double _NmiToR = MathX._NmiToR;
	final private Voyage _voyage;
	final private Area _startingArea;

	public VoyageItineraryBuilder(final SimCaseManager.SimCase simCase, final Voyage voyage, final Area startingArea) {
		final MyLogger logger = SimCaseManager.getLogger(simCase);
		_voyage = voyage;
		_startingArea = startingArea;
		final List<Area> subsequentAreas = _voyage.getAreas();
		final int nSubsequentAreas = subsequentAreas == null ? 0 : subsequentAreas.size();
		/** Set up the successors and the predecessors. */
		Area previousArea = _startingArea;
		LatLng3 previousCenterOfMass = previousArea.getFlatCenterOfMass();
		for (int k = 0; k < nSubsequentAreas; ++k) {
			/**
			 * Set the relationship between this area and its predecessor; _startingArea is
			 * the predecessor of the first one in this list.
			 */
			final Area area = subsequentAreas.get(k);
			final LatLng3 centerOfMass = area.getFlatCenterOfMass();
			area.setPredecessorLatLng(logger, previousCenterOfMass);
			previousArea.setSuccessorLatLng(logger, centerOfMass);
			previousArea = area;
			previousCenterOfMass = centerOfMass;
		}
		/** Print out the areas. */
		String s = "";
		for (int k = -1; k < nSubsequentAreas; ++k) {
			final Area area = k == -1 ? _startingArea : subsequentAreas.get(k);
			final TangentCylinder.FlatLatLng flatCenterOfMass = area.getFlatCenterOfMass();
			s += String.format("\nArea[%d: CenterOfMass%s, type[%s]", k, flatCenterOfMass.getString(5),
					area.getClass().getSimpleName());
		}
		SimCaseManager.out(simCase, s + '\n');
	}

	@Override
	public Itinerary buildItinerary(final Model model, final Randomx r, final long birthRefSecs,
			final ParticleIndexes prtclIndxs, final long[] refSecsS) {
		final SailData sailData = _voyage.getScenario().getSailData();
		final Sdi sdi;
		if (sailData != null) {
			sdi = new Sdi(sailData, prtclIndxs, r);

		} else {
			sdi = null;
		}
		return myBuildItinerary(model, r, birthRefSecs, prtclIndxs, refSecsS, sdi);
	}

	public VoyageItinerary myBuildItinerary(final Model model, final Randomx r, final long birthRefSecs,
			final ParticleIndexes prtclIndxs, final long[] refSecsS, final Sdi sdi) {
		final Scenario scenario = _voyage.getScenario();
		final SimCaseManager.SimCase simCase = scenario.getSimCase();
		final MyLogger logger = SimCaseManager.getLogger(simCase);
		final LatLngAndCdfs startLatLngAndCdfs;
		if (r != null) {
			LatLngAndCdfs startLatLngAndCdfsX = null;
			try {
				final double[] cdfsFromPredecessor = null;
				startLatLngAndCdfsX = _startingArea.generateLatLngFromCdfs(logger, cdfsFromPredecessor, r);
			} catch (final Cdf.BadCdfException e) {
			}
			startLatLngAndCdfs = startLatLngAndCdfsX;
		} else {
			startLatLngAndCdfs = new LatLngAndCdfs(_startingArea.getFlatCenterOfMass(), null);
		}
		LatLng3 startLatLng = startLatLngAndCdfs._latLng;
		double[] cdfs = startLatLngAndCdfs._cdfs;
		if (cdfs != null) {
			final double cdf0 = cdfs[0];
			cdfs[0] = Math.min(0.999, Math.max(0.001, cdf0));
			final double cdf1 = cdfs[1];
			cdfs[1] = Math.min(0.999, Math.max(0.001, cdf1));
		}
		LatLng3 destinationLatLng = null;
		final VoyageItinerary voyageItinerary = new VoyageItinerary(startLatLng, birthRefSecs, sdi);
		final List<Voyage.VoyageLeg> legList = _voyage.getLegs();
		final int nLegs = legList.size();
		long refSecs1 = birthRefSecs;
		long arrivalRefSecs = refSecs1;

		final boolean deadEnd = false;
		LEG_LOOP: for (int iLeg = 0; iLeg < nLegs; ++iLeg) {
			final boolean lastLeg = iLeg == nLegs - 1;
			final Voyage.VoyageLeg leg = legList.get(iLeg);
			final Area destinationArea = leg.getDestination();
			LatLngAndCdfs destinationLatLngAndCdfs = null;
			if (r != null) {
				try {
					destinationLatLngAndCdfs = destinationArea.generateLatLngFromCdfs(logger, cdfs, r);
				} catch (final BadCdfException e) {
				}
			} else {
				destinationLatLngAndCdfs = new LatLngAndCdfs(destinationArea.getFlatCenterOfMass(), null);
			}

			/** For this leg... */
			destinationLatLng = destinationLatLngAndCdfs._latLng;
			/** For the next one... */
			cdfs = destinationLatLngAndCdfs._cdfs;
			if (cdfs != null) {
				cdfs[0] = Math.min(0.999, Math.max(0.001, cdfs[0]));
				cdfs[1] = Math.min(0.999, Math.max(0.001, cdfs[1]));
			}
			if (sdi == null) {
				final double maxMotorSpeed = leg.getMaxSpeed();
				final double minMotorSpeed = leg.getMinSpeed();
				final double cruisingMotorSpeed = leg.getCruisingSpeed();
				final double kts;
				final NavigationCalculator navCalculator;
				if (r != null) {
					kts = r.getSplitGaussianDraw(minMotorSpeed, cruisingMotorSpeed, maxMotorSpeed);
				} else {
					/** We're building a quickie. */
					kts = leg.getMaxSpeed();
				}
				navCalculator = NavigationCalculatorStatics.build(startLatLng, refSecs1, destinationLatLng, kts,
						leg.getMotionType());
				final double nmi = navCalculator.getNmiFromLatLng0(destinationLatLng);
				final long durationSecs = Math.round(nmi / kts * 3600d);
				final VoyageItinerary.ItineraryLeg newItineraryLeg = new VoyageItinerary.ItineraryLeg(startLatLng,
						destinationLatLng, lastLeg ? StateVectorType.ARRIVE : StateVectorType.NONFINAL_ARRIVE, refSecs1,
						refSecs1 + durationSecs, kts, navCalculator);
				voyageItinerary.add(newItineraryLeg);
				refSecs1 += durationSecs;
				arrivalRefSecs = refSecs1;
			} else {
				/**
				 * We have an Sdi for this particle. Generate sailSegs for this Leg and
				 * translate to ItineraryLegs.
				 */
				final List<SailSeg> sailSegs = sdi.generateSailSegs(simCase, refSecs1, startLatLng, destinationLatLng,
						iLeg, nLegs, refSecsS);
				final int nSailSegs = sailSegs.size();
				for (int k = 0; k < nSailSegs; ++k) {
					final SailSeg sailSeg = sailSegs.get(k);
					final VoyageItinerary.ItineraryLeg newItineraryLeg = new VoyageItinerary.ItineraryLeg(sailSeg);
					voyageItinerary.add(newItineraryLeg);
					/**
					 * Update startLatLng and refSecs1 to the end of this SailSeg.
					 */
					final GreatCircleArc segGca = sailSeg._gca;
					startLatLng = segGca.getLatLng1();
					refSecs1 = sailSeg._refSecs1;
					/**
					 * Update arrivalRefSecs if this is this leg's last SailSeg.
					 */
					if (k == nSailSegs - 1) {
						arrivalRefSecs = refSecs1;
						destinationLatLng = segGca.getLatLng1();
					}
				}
				/**
				 * If we have a dead end, we add another leg, but only if this is real. If this
				 * is just for computing the minimum arrival, we don't care. That's a good thing
				 * because _model will be null in that case.
				 */
				if (model != null && sailSegs.size() > 0) {
					final SailSeg sailSeg = sailSegs.get(nSailSegs - 1);
					final StateVectorType svt = sailSeg._svt1;
					if (svt != StateVectorType.ARRIVE && svt != StateVectorType.NONFINAL_ARRIVE) {
						/**
						 * Something bad happened and we couldn't finish. Add the extra leg.
						 */
						final long legStartSecs = sailSeg._refSecs1;
						arrivalRefSecs = model.getLastOutputRefSecs();
						destinationLatLng = sailSeg._gca.getLatLng1();
						/** Add one more itinerary leg. */
						final NavigationCalculator navCalc = NavigationCalculatorStatics.build(destinationLatLng,
								legStartSecs, /* hdg= */0d, /* kts= */0d, MotionType.GREAT_CIRCLE);
						final VoyageItinerary.ItineraryLeg deadItineraryLeg = new VoyageItinerary.ItineraryLeg(
								destinationLatLng, destinationLatLng, svt, legStartSecs, arrivalRefSecs, //
								/* kts= */0d, navCalc);
						voyageItinerary.add(deadItineraryLeg);
						/** That was the last leg. */
						break LEG_LOOP;
					}
				}
			}
			startLatLng = destinationLatLng;
			/**
			 * If we have finished all of our legs, we have one more if we are NoDistress.
			 * In such a case, simply extend the last leg.
			 */
			if (!deadEnd && lastLeg && _voyage.getScenario().getNoDistress()) {
				final VoyageItinerary.ItineraryLeg lastItineraryLeg = voyageItinerary.getLastItineraryLeg();
				final NavigationCalculator navCalc = lastItineraryLeg._navCalc;
				final double kts = lastItineraryLeg._kts;
				final double longDistanceNmi = Constants._PiOver4 / _NmiToR;
				final long travelTimeSecs = Math.round(longDistanceNmi / kts * 3600d);
				final long endRefSecs = refSecs1 + travelTimeSecs;
				destinationLatLng = navCalc.getPosition(endRefSecs);
				final VoyageItinerary.ItineraryLeg newItineraryLeg = new VoyageItinerary.ItineraryLeg(startLatLng,
						destinationLatLng, StateVectorType.ARRIVE, refSecs1, endRefSecs, kts, navCalc);
				voyageItinerary.add(newItineraryLeg);
				refSecs1 = arrivalRefSecs = endRefSecs;
			}

			/**
			 * We don't need to update destinationLatLng, estimatedArrival, or startLatLng
			 * if we're on the last leg. On the last leg, we don't necessarily read in the
			 * last TimeDistribution but if we do, we must update the arrival time after the
			 * dwell.
			 */
			final TimeDistribution dwellTimeDistribution = leg.getDwellTimeDistribution();
			if (dwellTimeDistribution != null) {
				final double plusMinusMins = dwellTimeDistribution.getPlusMinusMins();
				final int meanDwellMinutes = dwellTimeDistribution.getMeanRefMins();
				final double offsetMinutesA;
				if (r == null) {
					/** We're building a quickie. */
					offsetMinutesA = -plusMinusMins;
				} else {
					final double randomDraw = r.getTruncatedGaussian();
					final double standardDeviation = plusMinusMins / Randomx._StandardDeviations;
					offsetMinutesA = randomDraw * standardDeviation;
				}
				final long dwellSecsA = (long) ((meanDwellMinutes + offsetMinutesA) * 60d);
				final long dwellSecs = Math.max(0, dwellSecsA);
				refSecs1 += dwellSecs;
				if (lastLeg) {
					arrivalRefSecs = refSecs1;
					break;
				}
			}
		}

		/** Set the last destination. */
		voyageItinerary.setDestinationLatLng(destinationLatLng);
		voyageItinerary.setArrivalRefSecs(arrivalRefSecs);
		final long distressRefSecs;
		if (r != null) {
			distressRefSecs = voyageItinerary.computeDistressRefSecs(model, r);
		} else {
			distressRefSecs = arrivalRefSecs;
		}
		voyageItinerary.setDistressRefSecs(_voyage.getScenario(), distressRefSecs);
		if (PreDistressModel._DumpItinerary) {
			final LatLng3[] latLngs = voyageItinerary.getLatLngs();
			final int nSegments = latLngs.length - 1;
			if (nSegments > 0) {
				final int numberEdgeInc = 1;
				final double[][][] segmentLngLats = new double[nSegments][][];
				for (int k = 0; k < nSegments; ++k) {
					final LatLng3 latLng0 = latLngs[k];
					final LatLng3 latLng1 = latLngs[k + 1];
					segmentLngLats[k] = new double[][] { latLng0.toLngLatArray(), latLng1.toLngLatArray() };
				}
				final String s = CartesianUtil.getSegmentsDump(segmentLngLats, /* colorNames= */null, numberEdgeInc);
				SimCaseManager.out(simCase, "\nPath:\n" + s + "\n");
			}
		}
		return voyageItinerary;
	}
}
