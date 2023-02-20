package com.skagit.sarops.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.w3c.dom.Element;

import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.simCaseManager.SimCaseManager.SimCase;
import com.skagit.sarops.util.CppToJavaTracer;
import com.skagit.sarops.util.SimGlobalStrings;
import com.skagit.sarops.util.wangsness.BearingCall;
import com.skagit.sarops.util.wangsness.Thresholds;
import com.skagit.sarops.util.wangsness.Wangsness;
import com.skagit.util.Ellipse;
import com.skagit.util.Ellipse2;
import com.skagit.util.LsFormatter;
import com.skagit.util.MathX;
import com.skagit.util.cdf.BivariateNormalCdf;
import com.skagit.util.cdf.area.Area;
import com.skagit.util.cdf.area.EllipticalArea;
import com.skagit.util.cdf.area.Polygon;
import com.skagit.util.geometry.crossingPair.CrossingPair2;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.geometry.gcaSequence.Loop3Statics;
import com.skagit.util.geometry.loopsFinder.LoopsFinder;
import com.skagit.util.geometry.loopsFinder.TopLoopCreator;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.TangentCylinder;
import com.skagit.util.randomx.Randomx;
import com.skagit.util.randomx.TimeDistribution;

/**
 * Represents a "line of bearing (LOB)" or a Flare scenario. These Scenarios
 * have, for input, {@link BearingCall}s. For a Flare scenario, these are stored
 * and intersected to form a {@link com.skagit.util.ConvexPolygon} which is used
 * for draws (using a uniform distribution over the polygon) if there are
 * multiple bearing calls. If there is only one, the draw is made directly;
 * choose a point from the center, a bearing (uniform), and a range (weighted
 * towards the larger radius).
 */
public class LobScenario extends Scenario {
	final private static double _NmiToR = MathX._NmiToR;
	/** The BearingCalls. */
	final private ArrayList<BearingCall> _bearingCalls;
	final private Thresholds _wangsnessThresholds;
	/** The EllipseDatas. */
	final private ArrayList<EllipseData> _ellipseDatas;
	private EllipseData _wangsnessEllipseData = null;
	private EllipseData _invalidWangsnessEllipseData = null;

	public static class EllipseData {
		final private static int _NPointsPerQuadrant = 16;
		final public TangentCylinder _centeredTangentCylinder;
		final public double _sigmaMjrNmi;
		final public double _sigmanMnrNmi;
		final public double _smiMjrHdg;
		final public Loop3 _ellipseCallLoop;

		/**
		 * The given ellipse is for the containment of
		 * Wangsness._ContainmentValueOfInput. As of 14Mar2018, this is set to 95%.
		 */
		public EllipseData(final SimCase simCase, final LatLng3 center, double smiMjrNmi, double smiMnrNmi,
				double smiMjrHdg) {
			final MyLogger logger = SimCaseManager.getLogger(simCase);
			if (!(smiMjrNmi >= smiMnrNmi)) {
				final double smiNmi = smiMjrNmi;
				smiMjrNmi = smiMnrNmi;
				smiMnrNmi = smiNmi;
				smiMjrHdg += 90d;
			}
			smiMjrHdg = LatLng3.getInRange0_360(smiMjrHdg);
			if (smiMjrHdg >= 180d) {
				smiMjrHdg -= 180d;
			}
			_smiMjrHdg = smiMjrHdg;
			final double c = MathX.cosX(Math.toRadians(90 - _smiMjrHdg));
			final double s = MathX.sinX(Math.toRadians(90 - _smiMjrHdg));
			_centeredTangentCylinder = TangentCylinder.getTangentCylinder(center);
			/** Set the TangentCylinder of lobScenario. */
			/** Compute the departure area. */
			final Ellipse ellipse = new Ellipse2(smiMjrNmi * _NmiToR, smiMnrNmi * _NmiToR, _NPointsPerQuadrant);
			final double[][] ellPts = ellipse.getFullCycle();
			final int nPoints = ellPts.length;
			final LatLng3[] perimeter = new LatLng3[nPoints];
			for (int k = 0; k < nPoints; ++k) {
				final double x = c * ellPts[k][0] - s * ellPts[k][1];
				final double y = s * ellPts[k][0] + c * ellPts[k][1];
				perimeter[k] = _centeredTangentCylinder.new FlatLatLng(x, y);
			}
			final int loopId = 0;
			final int subId = 0;
			final boolean isClockwise = false;
			final int flag = Loop3Statics.createGenericFlag(isClockwise);
			final int ancestorId = -1;
			final boolean logChanges = false;
			final boolean debug = false;
			final Loop3 ellipseCallLoop = Loop3.getLoop(logger, loopId, subId, flag, ancestorId, perimeter, logChanges,
					debug);
			if (ellipseCallLoop != null) {
				_ellipseCallLoop = Loop3Statics.convertToCwOrCcw(logger, ellipseCallLoop, Wangsness._Clockwise);
				/**
				 * For getting a draw, we have to go from containment radius to standard
				 * deviations.
				 */
				final double[] sigmas = BivariateNormalCdf
						.containmentRadiiToStandardDeviations(Wangsness._ContainmentValueOfInput, smiMjrNmi, smiMnrNmi);
				_sigmaMjrNmi = sigmas[0];
				_sigmanMnrNmi = sigmas[1];
			} else {
				_ellipseCallLoop = null;
				_sigmaMjrNmi = _sigmanMnrNmi = Double.NaN;
			}
		}

		public LatLng3 generateLatLngForLob(final Randomx randomForParticle) {
			/** Get the offset in nm. */
			final double drawForANmi = randomForParticle.getTruncatedGaussian() * _sigmaMjrNmi;
			final double drawForBNmi = randomForParticle.getTruncatedGaussian() * _sigmanMnrNmi;
			final double c = MathX.cosX(Math.toRadians(90 - _smiMjrHdg));
			final double s = MathX.sinX(Math.toRadians(90 - _smiMjrHdg));
			final double eastOffsetNmi = c * drawForANmi - s * drawForBNmi;
			final double northOffsetNmi = s * drawForANmi + c * drawForBNmi;
			/** Convert to EarthRadii. */
			final double eastOffset = eastOffsetNmi * _NmiToR;
			final double northOffset = northOffsetNmi * _NmiToR;
			/** Create the FlatLatLng. */
			return _centeredTangentCylinder.new FlatLatLng(eastOffset, northOffset);
		}
	}

	public LobScenario(final SimCaseManager.SimCase simCase, final short id, final String name, final String type,
			final Thresholds wangsnessThresholds, final double scenarioWeight, final int iScenario,
			final int baseParticleIndex, final int nParticles, final TimeDistribution timeDistribution) {
		super(simCase, id, name, type, scenarioWeight, iScenario, baseParticleIndex, nParticles);
		_wangsnessThresholds = wangsnessThresholds;
		setDepartureTimeDistribution(timeDistribution);
		_bearingCalls = new ArrayList<>();
		_ellipseDatas = new ArrayList<>();
	}

	@Override
	public boolean specificCheckAndFinalize(final Model model) {
		boolean result = true;
		final SimCaseManager.SimCase simCase = getSimCase();
		final MyLogger logger = SimCaseManager.getLogger(simCase);
		final SimGlobalStrings simGlobalStrings = simCase.getSimGlobalStrings();
		double totalWeight = 0;
		for (final SotWithDbl sotWithDbl : getDistressSotWithWts()) {
			totalWeight += sotWithDbl.getWorkingDbl();
		}
		result &= (Math.abs(totalWeight - 1) <= 0.001);
		result &= getDepartureTimeDistribution() != null;
		boolean haveGoodData;
		if (_type == _LobType) {
			_wangsnessEllipseData = null;
			final boolean tryWangsness = _wangsnessThresholds != null;
			final int nBearingCalls = _bearingCalls.size();
			final Wangsness wangsness;
			if (_ellipseDatas.size() == 0 && nBearingCalls > 1 && tryWangsness) {
				final CppToJavaTracer cppToJavaTracer = new CppToJavaTracer("LobScenario");
				wangsness = Wangsness.getWangsness(simCase, cppToJavaTracer, _bearingCalls, _wangsnessThresholds);
				SimCaseManager.out(simCase, wangsness == null ? "Failed Wangsness" : wangsness.getString());
			} else {
				wangsness = null;
			}
			if (wangsness != null) {
				/**
				 * The lengths provided to the ctor of EllipseData are assumed to be the 95%
				 * containment radii. The ctor converts to sigmas.
				 */
				final LatLng3 fix = wangsness._fix;
				final double sigmaA_NM = wangsness._sigmaA_Nmi;
				final double sigmaB_NM = wangsness._sigmaB_Nmi;
				final double[] smiMjrMnrNmi = BivariateNormalCdf
						.standardDeviationsToContainmentRadii(Wangsness._ContainmentValueOfInput, sigmaA_NM, sigmaB_NM);
				final double smiMjrNmi = smiMjrMnrNmi[0];
				final double smiMnrNmi = smiMjrMnrNmi[1];
				final double smiMjrHdg = wangsness._dirA_D_CwFromN;
				final EllipseData ellipseData = new EllipseData(simCase, wangsness._fix, smiMjrNmi, smiMnrNmi,
						smiMjrHdg);
				if (wangsness._valid) {
					_wangsnessEllipseData = ellipseData;
					_invalidWangsnessEllipseData = null;
					final double directionOfAInRads = Math.toRadians(90d - ellipseData._smiMjrHdg);
					final boolean isUniform = false;
					final double truncateDistanceNmi = Double.POSITIVE_INFINITY;
					_departureArea = new EllipticalArea(logger, fix, sigmaA_NM, sigmaB_NM, directionOfAInRads,
							isUniform, truncateDistanceNmi);
				} else {
					_wangsnessEllipseData = null;
					_invalidWangsnessEllipseData = ellipseData;
				}
			}
			if (_wangsnessEllipseData == null) {
				haveGoodData = _ellipseDatas.size() + _bearingCalls.size() > 0;
				/**
				 * _sighitngPolygon is the largest polygon in the union of the LOBs and
				 * Ellipses.
				 */
				final int n = nBearingCalls + _ellipseDatas.size();
				final ArrayList<Loop3> topLoopCreatorLoops = new ArrayList<>();
				for (int k = 0; k < n; ++k) {
					Loop3 cwLoop;
					if (k < nBearingCalls) {
						final BearingCall bearingCall = _bearingCalls.get(k);
						cwLoop = bearingCall._bearingCallLoop;
					} else {
						final EllipseData ellipseData = _ellipseDatas.get(k - nBearingCalls);
						cwLoop = ellipseData._ellipseCallLoop;
					}
					topLoopCreatorLoops.add(cwLoop.clone());
				}
				final int tooManyLoopsForComplicated = simGlobalStrings.getTooManyLoopsForComplicated();
				final TopLoopCreator topLoopCreator = new TopLoopCreator(logger, "LobScenario", topLoopCreatorLoops,
						tooManyLoopsForComplicated);
				final Loop3 topLoop = topLoopCreator._topLoop;
				final Polygon departureArea = new Polygon(logger, topLoop);
				_departureArea = departureArea;
			}
		} else if (_type == Scenario._FlareType) {
			/**
			 * It was only for convenience that we allowed EllipseData. Flare Scenarios just
			 * ignore them.
			 */
			haveGoodData = _bearingCalls.size() > 0;
			if (haveGoodData) {
				Loop3 finalLoop = null;
				for (final BearingCall bearingCall : _bearingCalls) {
					final Loop3 bearingCallLoopA = bearingCall._bearingCallLoop;
					/** If this is the first bearing call, handle it. */
					if (finalLoop == null) {
						finalLoop = bearingCallLoopA;
						continue;
					}
					/**
					 * We must update finalLoop, which is guaranteed to be a polygon, with
					 * bearingCall's polygon.
					 */
					final Loop3[] intersection;
					if (bearingCallLoopA == null) {
						intersection = null;
					} else {
						final LatLng3 latLng0 = finalLoop.getZeroPoint();
						final LatLng3 latLng1 = bearingCallLoopA.getZeroPoint();
						final CrossingPair2 xingPair = finalLoop.findCrossingPair(logger, bearingCallLoopA);
						if (xingPair == null || !xingPair.hasCrossing()) {
							/** No crossing. */
							if (finalLoop.interiorContains(logger, latLng1)) {
								intersection = new Loop3[] {
										bearingCallLoopA
								};
							} else if (bearingCallLoopA.interiorContains(logger, latLng0)) {
								intersection = new Loop3[] {
										finalLoop
								};
							} else {
								intersection = null;
							}
						} else {
							final boolean cw0 = finalLoop.isClockwise();
							final boolean cw1 = bearingCallLoopA.isClockwise();
							final Loop3 bearingCallLoop = cw0 == cw1 ? bearingCallLoopA
									: bearingCallLoopA.createReverseLoop(logger);
							final ArrayList<Loop3> loops = LoopsFinder.findLoopsFromLoops(logger, new Loop3[] {
									finalLoop, bearingCallLoop
							}, /* waterWins= */!cw0);
							final int n = loops == null ? 0 : loops.size();
							intersection = n == 0 ? new Loop3[0] : loops.toArray(new Loop3[n]);
						}
					}
					final int nInIntersection = intersection == null ? 0 : intersection.length;
					if (nInIntersection == 0) {
						continue;
					}
					int maxNGcas = 0;
					Loop3 winner = null;
					for (final Loop3 loop : intersection) {
						final int nGcas = loop.getNGcas();
						if (winner == null || nGcas > maxNGcas) {
							maxNGcas = nGcas;
							winner = loop;
						}
					}
					if (maxNGcas >= 3) {
						finalLoop = winner;
					}
				}
				final int nInOpen = finalLoop == null ? 0 : finalLoop.getNLatLngs();
				if (nInOpen < 3) {
					_departureArea = null;
					haveGoodData = false;
				} else {
					_departureArea = new Polygon(logger, finalLoop);
					haveGoodData = true;
				}
			}
		} else {
			haveGoodData = false;
		}
		result &= _departureArea != null;
		return result;
	}

	@Override
	public Element write(final LsFormatter formatter, final Element root, final Model model) {
		final Element element = formatter.newChild(root, "SCENARIO");
		element.setAttribute("id", LsFormatter.StandardFormat(getId()));
		element.setAttribute("name", getName());
		element.setAttribute("weight", getScenarioWeight() * 100 + "%");
		element.setAttribute("type", getType());
		if (_wangsnessThresholds != null) {
			element.setAttribute("wangsnessAreaThreshold", _wangsnessThresholds._areaThresholdSqNmi + " NMSq");
			element.setAttribute("wangsnessDistanceThreshold", _wangsnessThresholds._distanceThresholdNmi + " NM");
			element.setAttribute("wangsnessSemiMajorThreshold", _wangsnessThresholds._semiMajorThresholdNmi + " NM");
			element.setAttribute("wangsnessMajorToMinor", _wangsnessThresholds._majorToMinorThreshold + "");
			element.setAttribute("wangsnessMinAngle", _wangsnessThresholds._minAngleD + " Degs");
		}
		final Element timeElement = formatter.newChild(element, "TIME");
		getDepartureTimeDistribution().write(formatter, timeElement, false, null);
		final Area departureArea = getDepartureArea();
		if (departureArea instanceof Polygon) {
			final Polygon polygonDepartureArea = (Polygon) departureArea;
			polygonDepartureArea.write("EFFECTIVE_AREA", formatter, element);
		} else if (departureArea instanceof EllipticalArea) {
			final EllipticalArea bivariateNormalDepartureArea = (EllipticalArea) departureArea;
			bivariateNormalDepartureArea.write("EFFECTIVE_AREA", formatter, element);
		}
		getDepartureTimeDistribution().write(formatter, root, false, "TIME");
		for (final SotWithDbl searchObjectTypeWithWeight : getDistressSotWithWts()) {
			searchObjectTypeWithWeight.write(formatter, element);
		}
		for (final BearingCall bearingCall : _bearingCalls) {
			final Element lobElement = formatter.newChild(element, "SIGHTING");
			final LatLng3 center = bearingCall._centerArea.getFlatCenterOfMass();
			final boolean encloseInBrackets = false;
			final String dmString = center.toDmString(encloseInBrackets);
			final Element pointElement = formatter.writeAsPoint(lobElement, center.toArray(), dmString);
			/**
			 * We are storing the origin's sigma. We need to write out the probable error.
			 */
			final double probableErrorNmi = BivariateNormalCdf.standardDeviationsToContainmentRadii(0.5,
					bearingCall._centerArea.getSigmaA_NM(), bearingCall._centerArea.getSigmaB_NM())[0];
			pointElement.setAttribute("x_error", String.format("%.3g NM", probableErrorNmi));
			final Element bearingElement = formatter.newChild(lobElement, "BEARING");
			bearingElement.setAttribute("center", "" + bearingCall._inputCalledBearing + " T");
			bearingElement.setAttribute("plus_minus", "" + bearingCall._inputSdD + " deg");
			final Element rangeElement = formatter.newChild(lobElement, "RANGE");
			rangeElement.setAttribute("min", "" + bearingCall._inputMinRangeNmi + " NM");
			rangeElement.setAttribute("max", "" + bearingCall._inputMaxRangeNmi + " NM");
		}
		final int nEllipseDatas = _ellipseDatas.size()
				+ ((_wangsnessEllipseData != null || _invalidWangsnessEllipseData != null) ? 1 : 0);
		for (int k = 0; k < nEllipseDatas; ++k) {
			final EllipseData ellipseData;
			final String ellipseTag;
			if (k < _ellipseDatas.size()) {
				ellipseData = _ellipseDatas.get(k);
				ellipseTag = "ELLIPSE";
			} else if (_wangsnessEllipseData != null) {
				ellipseData = _wangsnessEllipseData;
				ellipseTag = "WANGSNESS_ELLIPSE";
			} else {
				ellipseData = _invalidWangsnessEllipseData;
				ellipseTag = "INVALID_WANGSNESS_ELLIPSE";
			}
			final Element ellipseElement = formatter.newChild(element, ellipseTag);
			final LatLng3 centeredLatLng = ellipseData._centeredTangentCylinder.getCentralFlatLatLng();
			ellipseElement.setAttribute("lat", String.format("%.4g ", centeredLatLng.getLat()));
			ellipseElement.setAttribute("lng", String.format("%.4g ", centeredLatLng.getLng()));
			ellipseElement.setAttribute("orientation", String.format("%.4g T", ellipseData._smiMjrHdg));
			final double[] containments = BivariateNormalCdf.standardDeviationsToContainmentRadii(
					Wangsness._ContainmentValueOfInput, ellipseData._sigmaMjrNmi, ellipseData._sigmanMnrNmi);
			final double containmentSemiMajorNmi = containments[0];
			final double containmentSemiMinorNmi = containments[1];
			ellipseElement.setAttribute("semiMajor", String.format("%.4g NM", containmentSemiMajorNmi));
			ellipseElement.setAttribute("semiMinor", String.format("%.4g NM", containmentSemiMinorNmi));
		}
		return element;
	}

	@Override
	public boolean deepEquals(final Scenario comparedScenario) {
		if (getType() != comparedScenario.getType()) {
			return false;
		}
		final LobScenario him = (LobScenario) comparedScenario;
		if ((getId() != him.getId()) || ((_wangsnessThresholds != null) != (him._wangsnessThresholds != null))) {
			return false;
		}
		if (_wangsnessThresholds != null) {
			if (_wangsnessThresholds._areaThresholdSqNmi != him._wangsnessThresholds._areaThresholdSqNmi) {
				return false;
			}
			if (_wangsnessThresholds._distanceThresholdNmi != him._wangsnessThresholds._distanceThresholdNmi) {
				return false;
			}
			if (_wangsnessThresholds._semiMajorThresholdNmi != him._wangsnessThresholds._semiMajorThresholdNmi) {
				return false;
			}
			if (_wangsnessThresholds._majorToMinorThreshold != him._wangsnessThresholds._majorToMinorThreshold) {
				return false;
			}
			if (_wangsnessThresholds._minAngleD != him._wangsnessThresholds._minAngleD) {
				return false;
			}
		}
		final String myName = getName();
		final String hisName = him.getName();
		if (myName != null && myName.length() > 0 && hisName != null && hisName.length() > 0) {
			if (myName.compareTo(hisName) != 0) {
				return false;
			}
		}
		if (getScenarioWeight() != him.getScenarioWeight()) {
			return false;
		}
		final ArrayList<BearingCall> hisBearingCalls = him._bearingCalls;
		if (_bearingCalls.size() != him._bearingCalls.size()) {
			return false;
		}
		OutsideLoop: for (final BearingCall bearingCall : _bearingCalls) {
			for (final BearingCall hisBearingCall : hisBearingCalls) {
				if (bearingCall.deepEquals(hisBearingCall)) {
					continue OutsideLoop;
				}
			}
			return false;
		}
		final List<SotWithDbl> mySotWithWts = getDistressSotWithWts();
		final List<SotWithDbl> hisSotWithWts = him.getDistressSotWithWts();
		if (mySotWithWts.size() != hisSotWithWts.size()) {
			return false;
		}
		final Iterator<SotWithDbl> myIt = mySotWithWts.iterator();
		final Iterator<SotWithDbl> hisIt = hisSotWithWts.iterator();
		while (myIt.hasNext()) {
			final SotWithDbl sotWithDbl = myIt.next();
			final SotWithDbl compared = hisIt.next();
			if (!sotWithDbl.deepEquals(compared)) {
				return false;
			}
		}
		return true;
	}

	public void addBearingCall(final SimCaseManager.SimCase simCase, final EllipticalArea centerArea,
			final double calledBearing, final double bearingSd, final double minRangeNmi, final double maxRangeNmi) {
		if (_type == _FlareType && _bearingCalls.size() > 0) {
			return;
		}
		if (centerArea == null || Double.isNaN(calledBearing) || !(bearingSd >= 0d) || !(minRangeNmi >= 0d)
				|| !(maxRangeNmi > 0d)) {
			return;
		}
		final int bearingCallIdx = _bearingCalls.size();
		final BearingCall bearingCall = new BearingCall(simCase, getName(), centerArea, calledBearing, bearingSd,
				minRangeNmi, maxRangeNmi, bearingCallIdx);
		_bearingCalls.add(bearingCall);
	}

	@Override
	public LatLng3 getInitialLatLng(final Randomx randomForParticle, final int overallIdx) {
		if (_type == _FlareType) {
			final SimCaseManager.SimCase simCase = getSimCase();
			final MyLogger logger = SimCaseManager.getLogger(simCase);
			if (_bearingCalls.size() > 1) {
				return _departureArea.generateLatLng(logger, randomForParticle);
			}
			final boolean useBivariateNormalForOneBearingCall = false;
			if (useBivariateNormalForOneBearingCall) {
				return _bearingCalls.get(0).generateLatLngForFlare(logger, randomForParticle);
			}
			return _departureArea.generateLatLng(logger, randomForParticle);
		}
		/** type == LOB. */
		if (_wangsnessEllipseData != null) {
			return _wangsnessEllipseData.generateLatLngForLob(randomForParticle);
		}
		final int nBearingCalls = _bearingCalls.size();
		final int n = nBearingCalls + _ellipseDatas.size();
		final int kComponent = overallIdx % n;
		if (kComponent < nBearingCalls) {
			final BearingCall bearingCall = _bearingCalls.get(kComponent);
			return bearingCall.generateLatLngForLob(randomForParticle);
		}
		final EllipseData ellipseData = _ellipseDatas.get(kComponent - nBearingCalls);
		return ellipseData.generateLatLngForLob(randomForParticle);
	}

	public ArrayList<BearingCall> getBearingCalls() {
		return _bearingCalls;
	}

	public EllipseData getWangsnessEllipseData() {
		return _wangsnessEllipseData;
	}

	/**
	 * These smiMjrNmi, smiMnrNmi refer to the containment found in Wangsness. As of
	 * 14Mar2018, this is 95%.
	 */
	public void addEllipse(final LatLng3 center, final double smiMjrNmi, final double smiMnrNmi,
			final double smiMjrHdg) {
		final SimCaseManager.SimCase simCase = getSimCase();
		_ellipseDatas.add(new EllipseData(simCase, center, smiMjrNmi, smiMnrNmi, smiMjrHdg));
	}

	public EllipseData getInvalidWangsnessEllipseData() {
		return _invalidWangsnessEllipseData;
	}
}
