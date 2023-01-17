package com.skagit.sarops.util.wangsness;

import java.util.ArrayList;

import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.Constants;
import com.skagit.util.MathX;
import com.skagit.util.cdf.BivariateNormalCdf;
import com.skagit.util.cdf.area.EllipticalArea;
import com.skagit.util.geometry.gcaSequence.CleanOpenLoop;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.geometry.gcaSequence.Loop3Statics;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.GreatCircleCalculator;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.randomx.Randomx;

/** Represents a SIGHTING. */
public class BearingCall {
	final private static double _NmiToR = MathX._NmiToR;
	/** Stay away from 0 in the bearing and range distributions. */
	final public static double _MinRingWidthNmi = 0.25;
	final public static double _MinAngleWidthD = 1d / 2d;
	final public static double _MaxAngleWidthD = 90d - _MinAngleWidthD;
	/**
	 * For creating a perimeter; along the arc, we insert points at the
	 * following interval.
	 */
	final public static double _IntervalWidthNmi = 2d;
	/** Position uncertainty of the origin. */
	final public EllipticalArea _centerArea;
	/** Half-range of the bearing interval. */
	final public double _inputSdD;
	final public double _inputMinRangeNmi;
	final public double _inputMaxRangeNmi;
	final public double _inputCalledBearing;
	/**
	 * The minimum range in nmi after making sure that it is at least 0.
	 * Computed from {@link #_vertex} and takes into account the radius of the
	 * center.
	 */
	final private double _workingMinRangeNmi;
	/**
	 * The maximum range in nmi after making sure that the maximum range is at
	 * least 1/4 mile bigger than the minimum range. Computed from
	 * {@link #_vertex} and takes into account the radius of the center.
	 */
	final private double _workingMaxRangeNmi;
	/** Bearing interval that is used (at least 1/2 degree). */
	final private double _bearingSd;
	/**
	 * The following 3 are used to build initial distributions for the LOB.
	 */
	final public LatLng3 _vertex;
	final private double _workingBearingSd;
	final private double _workingCalledBearing;
	/** Intermediate result and stored just for for display. */
	final private LatLng3 _tangentPointA;
	final private LatLng3 _tangentPointB;
	/**
	 * For building a departure area. It is wrt LobScenario's
	 * _tangentCylinder.
	 */
	final public Loop3 _bearingCallLoop;
	final public int _bearingCallIdx;
	public boolean _inUseForWangsness;

	public BearingCall(final SimCaseManager.SimCase simCase,
			final String scenarioName, final EllipticalArea centerArea,
			final double calledBearing, double bearingSd,
			final double minRangeNmi, final double maxRangeNmi,
			final int bearingCallIdx) {
		final LatLng3 centerOfMass = centerArea.getFlatCenterOfMass();
		final MyLogger logger = SimCaseManager.getLogger(simCase);

		/** Make sure that our center area is a circle. */
		final double sigmaA_Nmi = centerArea.getSigmaA_NM();
		final double sigmaB_Nmi = centerArea.getSigmaB_NM();
		if (sigmaA_Nmi == sigmaB_Nmi) {
			_centerArea = centerArea;
		} else {
			final double sigmaNmi = Math.max(sigmaA_Nmi, sigmaB_Nmi);
			_centerArea = new EllipticalArea(logger, centerOfMass, sigmaNmi);
		}
		_inputSdD = bearingSd;
		_inputMinRangeNmi = minRangeNmi;
		_inputMaxRangeNmi = maxRangeNmi;
		_inputCalledBearing = calledBearing;
		bearingSd = Math.abs(_inputSdD);
		_bearingSd =
				Math.max(_MinAngleWidthD, Math.min(_MaxAngleWidthD, bearingSd));
		final double correctedMinRangeNmi = Math.max(0d, minRangeNmi);
		final double correctedMaxRangeNmi =
				Math.max(minRangeNmi + _MinRingWidthNmi, maxRangeNmi);
		final double sigmaNmi = _centerArea.getSigmaA_NM();
		final double preProbableErrorNmi = BivariateNormalCdf
				.standardDeviationsToContainmentRadii(0.5, sigmaNmi, sigmaNmi)[0];
		final double probableErrorNmi;
		/** If the prescribed error is less than a meter, set it to 0. */
		if (preProbableErrorNmi < 1d / Constants._NmiToM) {
			probableErrorNmi = 0d;
		} else {
			probableErrorNmi = preProbableErrorNmi;
		}
		if (probableErrorNmi == 0d) {
			_vertex = centerOfMass;
			_workingMinRangeNmi = correctedMinRangeNmi;
			_workingMaxRangeNmi = correctedMaxRangeNmi;
			_tangentPointA = _tangentPointB = null;
			_workingCalledBearing = _inputCalledBearing;
			_workingBearingSd = _bearingSd;
		} else {
			/**
			 * We need to find the distance between the center and the vertex.
			 * alpha and beta are measured in degrees for the
			 * getDistanceFromCenterToVertex routine.
			 */
			final double alpha = probableErrorNmi / 60d;
			final double beta = _bearingSd;
			final double centerToVertexR =
					getDistanceFromCenterToVertexR(alpha, beta);
			final double hdgFromCenterToVertex = _inputCalledBearing + 180d;
			_vertex = MathX.getLatLngX(centerOfMass, hdgFromCenterToVertex,
					centerToVertexR);
			_workingCalledBearing = MathX.initialHdgX(_vertex, centerOfMass);
			/**
			 * The tangent points are on the circle and almost at 90 degrees to
			 * the arc connecting center and vertex; they are closer by the
			 * bearing error.
			 */
			final double probableErrorR = probableErrorNmi * _NmiToR;
			final double hdg0A = _inputCalledBearing - (90d + _bearingSd);
			_tangentPointA =
					MathX.getLatLngX(centerOfMass, hdg0A, probableErrorR);
			final double hdg0B = _inputCalledBearing + (90d + _bearingSd);
			_tangentPointB =
					MathX.getLatLngX(centerOfMass, hdg0B, probableErrorR);
			final double hdg1A = MathX.initialHdgX(_vertex, _tangentPointA);
			final double hdg1B = MathX.initialHdgX(_vertex, _tangentPointB);
			_workingBearingSd =
					Math.abs(LatLng3.getInRange180_180(hdg1B - hdg1A)) / 2d;
			/**
			 * We'll have to add some more distance to the annulus since we'll be
			 * starting from _vertex and not centerOfMass.
			 */
			final double additionalDistanceNmi =
					GreatCircleCalculator.getNmi(_vertex, centerOfMass);
			_workingMinRangeNmi = Math
					.max(minRangeNmi + additionalDistanceNmi - probableErrorNmi, 0d);
			_workingMaxRangeNmi =
					maxRangeNmi + additionalDistanceNmi + probableErrorNmi;
		}
		/** Compute the perimeter LatLngs. */
		final ArrayList<LatLng3> perimeterLatLngList = new ArrayList<>();
		for (int iPass = 0; iPass < 2; ++iPass) {
			final double dNmi =
					iPass == 0 ? _workingMinRangeNmi : _workingMaxRangeNmi;
			final double dRadians = dNmi * _NmiToR;
			final int nPoints;
			if (dRadians > 0d) {
				/** Compute the number of points on this arc. */
				if (iPass == 0) {
					/** We could cut off the minimum range to ensure convexity. */
					final boolean ensureConvexity = false;
					if (ensureConvexity) {
						nPoints = 2;
					} else {
						nPoints = 2 + (int) Math.floor(dNmi / _IntervalWidthNmi);
					}
				} else {
					nPoints = 2 + (int) Math.floor(dNmi / _IntervalWidthNmi);
				}
			} else {
				nPoints = 1;
			}
			/**
			 * Form the perimeter by adding points along the small ring and then
			 * the large ring, going the opposite direction.
			 */
			if (nPoints > 1) {
				final int nPointsM1 = nPoints - 1;
				final double highHdg = _workingCalledBearing + _workingBearingSd;
				final double lowHdg = _workingCalledBearing - _workingBearingSd;
				/** To be clockwise, start with highHeading on pass # 0. */
				final double startHdg = iPass == 0 ? highHdg : lowHdg;
				final double endHdg = iPass == 0 ? lowHdg : highHdg;
				final double hdgDiff = LatLng3.degsToEast180_180(startHdg, endHdg);
				for (int k = 0; k < nPoints; ++k) {
					final double hdg = startHdg + k * hdgDiff / nPointsM1;
					final LatLng3 perimeterLatLng =
							MathX.getLatLngX(_vertex, hdg, dRadians);
					perimeterLatLngList.add(perimeterLatLng);
				}
			} else {
				perimeterLatLngList.add(_vertex);
			}
		}
		perimeterLatLngList.trimToSize();
		/** We have the perimeter LatLngs. Make a cw Loop3 out of them. */
		Loop3 bearingCallLoop = null;
		final int id = 0;
		final int subId = 0;
		final int flag = Loop3Statics.createGenericFlag(/* cw= */true);
		final int ancestorId = -1;
		final boolean logChanges = false;
		final boolean debug = false;
		bearingCallLoop = Loop3.getLoop(logger, id, subId, flag, ancestorId,
				perimeterLatLngList
						.toArray(new LatLng3[perimeterLatLngList.size()]),
				CleanOpenLoop._StandardAllChecks, logChanges, debug);
		if (bearingCallLoop.isClockwise() != Wangsness._Clockwise) {
			bearingCallLoop = bearingCallLoop.createReverseLoop(logger);
		}
		_bearingCallLoop = bearingCallLoop;
		_inUseForWangsness = false;
		_bearingCallIdx = bearingCallIdx;
	}

	public EllipticalArea getCenterArea() {
		return _centerArea;
	}

	public LatLng3 getVertex() {
		return _vertex;
	}

	public double getWorkingMinNmi() {
		return _workingMinRangeNmi;
	}

	public double getWorkingMaxNmi() {
		return _workingMaxRangeNmi;
	}

	public double getInputMaxRangeNmi() {
		return _inputMaxRangeNmi;
	}

	public double getWorkingSd() {
		return _workingBearingSd;
	}

	public LatLng3 getTangentPoint1() {
		return _tangentPointA;
	}

	public LatLng3 getTangentPoint2() {
		return _tangentPointB;
	}

	public Loop3 getBearingCallLoop() {
		return _bearingCallLoop;
	}

	public LatLng3 generateLatLngForLob(final Randomx randomForParticle) {
		final double rangeNmi = randomForParticle
				.getAnnular(_workingMinRangeNmi, _workingMaxRangeNmi);
		final double rangeR = rangeNmi * _NmiToR;
		final double offsetFromCalledBearing =
				randomForParticle.getTruncatedGaussian() * _workingBearingSd;
		final double bearing = _workingCalledBearing + offsetFromCalledBearing;
		final LatLng3 target = MathX.getLatLngX(_vertex, bearing, rangeR);
		return target;
	}

	public LatLng3 generateLatLngForFlare(final MyLogger logger,
			final Randomx randomForParticle) {
		double nmiMin;
		double nmiMax;
		double hdg1;
		double hdg2;
		final LatLng3 startPointForThisDraw =
				_centerArea.generateLatLng(logger, randomForParticle);
		nmiMin = _inputMinRangeNmi;
		nmiMax = Math.max(_inputMaxRangeNmi, nmiMin + _MinRingWidthNmi);
		hdg1 = _inputCalledBearing - _bearingSd;
		hdg2 = _inputCalledBearing + _bearingSd;
		final double nmi = randomForParticle.getAnnular(nmiMin, nmiMax);
		final double signedIntervalWidth =
				LatLng3.degsToEast180_180(hdg1, hdg2);
		final double hdg =
				hdg1 + randomForParticle.nextDouble() * signedIntervalWidth;
		return MathX.getLatLngX(startPointForThisDraw, hdg, nmi * _NmiToR);
	}

	public boolean deepEquals(final BearingCall bearingCall) {
		if ((_inputCalledBearing != bearingCall._inputCalledBearing) || !_centerArea.getFlatCenterOfMass()
				.equals(bearingCall._centerArea.getFlatCenterOfMass()) || (_inputSdD != bearingCall._inputSdD) || (_inputMinRangeNmi != bearingCall._inputMinRangeNmi)) {
			return false;
		}
		if (_inputMaxRangeNmi != bearingCall._inputMaxRangeNmi) {
			return false;
		}
		return true;
	}

	public double getWorkingCalledBearing() {
		return _workingCalledBearing;
	}

	public String getString() {
		String s = "";
		s += String.format("Center%s", _centerArea.getFlatCenterOfMass());
		s += String.format("SigmaA_NM[%f]", _centerArea.getSigmaA_NM());
		s += String.format("SigmaB_NM[%f]", _centerArea.getSigmaB_NM());
		s += String.format("ProbErr[%f]", _centerArea.getProbableError());
		s += String.format(" CalledBearing[%.1f degs]", _inputCalledBearing);
		s += String.format(" BearingSd[%.2f degs]", _bearingSd);
		s += String.format(" MinRange[%.1f NM]", _inputMinRangeNmi);
		s += String.format(" MaxRange[%.1f NM]", _inputMaxRangeNmi);
		s += String.format(" Idx[%d]", _bearingCallIdx);
		return s;
	}

	public double getInputCalledBearing() {
		return _inputCalledBearing;
	}

	private static double getDistanceFromCenterToVertexR(final double alphaD,
			final double betaD) {
		final double alphaR = Math.toRadians(alphaD);
		final double betaR = Math.toRadians(betaD);
		final double cB = MathX.cosX(betaR);
		final double sB = MathX.sinX(betaR);
		final double cA = MathX.cosX(alphaR);
		final double sA = MathX.sinX(alphaR);
		final double x = sB * cA;
		final double z = cB * cB * sA + sB * sB * sA;
		final double n = Math.sqrt(x * x + z * z);
		final double sinForm = MathX.asinX(z / n);
		// final double cosForm = MathX.acosX(x / n);
		// final double d = cosForm - sinForm;
		return sinForm;
	}

}