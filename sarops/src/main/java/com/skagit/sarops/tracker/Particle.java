package com.skagit.sarops.tracker;

import java.util.LinkedList;
import java.util.TreeMap;

import com.skagit.sarops.model.DebrisObjectType;
import com.skagit.sarops.model.Scenario;
import com.skagit.sarops.model.SearchObjectType;
import com.skagit.sarops.model.Sortie;
import com.skagit.sarops.model.preDistressModel.sail.Sdi;
import com.skagit.util.Constants;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.TangentCylinder;
import com.skagit.util.randomx.Randomx;

public class Particle {

	final private ParticleIndexes _prtclIndxs;
	final private Boolean _guaranteedSticky;
	private StateVector _root;
	final private LinkedList<CpaCalculator.Result> _cpaCalculatorResults;
	private StateVector _tail;
	final private Scenario _scenario;
	final private Tracker _tracker;
	final private LeewayCalculator _leewayCalculator;
	final private Randomx _random;
	final private long _birthSimSecs;
	final private long _distressSimSecs;
	private long _expirationSimSecs;
	private long _anchoringSimSecs = -1;
	private long _landingSimSecs = -1;
	private LatLng3 _distressLatLng;
	/** _tangentCylinder is set when the particle is in distress. */
	private TangentCylinder _tangentCylinder;
	final private SearchObjectType _originatingSot;
	final private SearchObjectType _distressObjectType;
	final private TreeMap<Sortie.Leg, CpaCalculator.Result> _legCpaCalculatorResultMap = new TreeMap<>();
	final private TreeMap<Sortie, CpaCalculator.Result> _sortieCpaCalculatorResultMap = new TreeMap<>();

	public int _fullPenalty;
	public int _remainingPenalty;

	public LinkedList<CpaCalculator.Result> getCpaCalculatorResults() {
		return _cpaCalculatorResults;
	}

	public Particle(final Tracker tracker, final Scenario scenario, final boolean guaranteedSticky,
			final SearchObjectType originatingSot, final long birthSimSecs, final DebrisObjectType distressSot,
			final long distressSimSecs, final Randomx random) {
		this(tracker, scenario, /* prtclIndxs= */null, guaranteedSticky, originatingSot, birthSimSecs, distressSot,
				distressSimSecs, random);
	}

	public Particle(final Tracker tracker, final Scenario scenario, final ParticleIndexes prtclIndxs,
			final SearchObjectType originatingSot, final long birthSimSecs, final SearchObjectType distressSot,
			final long distressSimSecs, final Randomx random) {
		this(tracker, scenario, prtclIndxs, /* guaranteedSticky= */null, originatingSot, birthSimSecs, distressSot,
				distressSimSecs, random);
	}

	public Particle(final Tracker tracker, final Scenario scenario, final ParticleIndexes prtclIndxs,
			final Boolean guaranteedSticky, final SearchObjectType originatingSot, final long birthSimSecs,
			final SearchObjectType distressSot, final long distressSimSecs, final Randomx random) {
		_scenario = scenario;
		_tracker = tracker;
		_prtclIndxs = prtclIndxs;
		_guaranteedSticky = guaranteedSticky;
		_cpaCalculatorResults = new LinkedList<>();
		_originatingSot = originatingSot;
		_distressObjectType = distressSot;
		_birthSimSecs = birthSimSecs;
		_distressLatLng = null;
		final SearchObjectType.LeewayData leewayData = distressSot.getLeewayData();
		_random = random;
		/**
		 * For the leeway calculator, use _random once and only once, to keep _random in
		 * synch.
		 */
		final Randomx r = _random == null ? null : new Randomx(_random.nextLong(), /* nToAdvance= */6);
		_leewayCalculator = new LeewayCalculator(r, leewayData);
		_fullPenalty = _remainingPenalty = 0;
		_distressSimSecs = distressSimSecs;
		final SearchObjectType.SurvivalData survivalData = distressSot.getSurvivalData();
		final long lifeLengthAfterDistressSecs;
		if (survivalData != null) {
			lifeLengthAfterDistressSecs = survivalData.getLifeLengthAfterDistressSecs(_random);
		} else {
			lifeLengthAfterDistressSecs = Long.MAX_VALUE / 2;
		}
		_expirationSimSecs = distressSimSecs + lifeLengthAfterDistressSecs;
	}

	public boolean isEnvMean() {
		return _prtclIndxs.isEnvMean();
	}

	public TreeMap<Sortie.Leg, CpaCalculator.Result> getLegCpaCalculatorResultMap() {
		return _legCpaCalculatorResultMap;
	}

	public TreeMap<Sortie, CpaCalculator.Result> getSortieCpaCalculatorResultMap() {
		return _sortieCpaCalculatorResultMap;
	}

	final public void setLatestStateVector(final StateVector tail) {
		_tail = tail;
	}

	final public void setRootStateVector(final StateVector root) {
		_root = root;
	}

	final public StateVector getLatestStateVector() {
		return _tail;
	}

	final public StateVector getRootStateVector() {
		return _root;
	}

	public static class LeewayCalculator {
		/**
		 * We could just store the 3 random draws and the SearchObjectType.LeewayData,
		 * and re-create the slopes.
		 */
		final public double _downWindSlope;
		final public double _downWindConstant;
		final public double _crossWindPlusSlope;
		final public double _crossWindPlusConstant;
		final public double _crossWindMinusSlope;
		final public double _crossWindMinusConstant;
		final public double _gibingFrequencyPerSecond;
		public boolean _usingPlus;
		public long _nextFlipSecs;

		private static double getSlopeIfConstantIsZero(final double slope, final double standardDeviation,
				final double nominalSpeed, final double z) {
			return (nominalSpeed * slope - (z * standardDeviation)) / nominalSpeed;
		}

		private LeewayCalculator(final Randomx localR, final SearchObjectType.LeewayData leewayData) {
			final double nominalSpeed = leewayData._nominalSpeed;
			if (leewayData._useRayleigh) {
				final double b = leewayData._dwlSlope / Math.sqrt(Constants._PiOver2);
				final double rayleighDraw = localR == null ? 0d : localR.getStandardTruncatedRayleighDraw();
				_downWindSlope = b * rayleighDraw;
				_downWindConstant = 0d;
			} else {
				final double slope = leewayData._dwlSlope;
				final double constant = leewayData._dwlConstant;
				final double standardDeviation = leewayData._dwlStandardDeviation;
				if (constant == 0d) {
					final double gaussianDraw0 = localR == null ? 0d : localR.getTruncatedGaussian();
					_downWindSlope = getSlopeIfConstantIsZero(slope, standardDeviation, nominalSpeed, gaussianDraw0);
					_downWindConstant = 0d;
				} else {
					final double gaussianDraw1 = localR == null ? 0d : localR.getTruncatedGaussian();
					final double q = gaussianDraw1 * standardDeviation;
					final double rValue = q / 2d;
					_downWindSlope = slope + rValue / nominalSpeed;
					_downWindConstant = constant + rValue;
				}
			}
			double slope = leewayData._cwlPlusSlope;
			double constant = leewayData._cwlPlusConstant;
			double standardDeviation = leewayData._cwlPlusStandardDeviation;
			if (constant == 0d) {
				final double gaussianDraw2 = localR == null ? 0d : localR.getTruncatedGaussian();
				_crossWindPlusSlope = getSlopeIfConstantIsZero(slope, standardDeviation, nominalSpeed, gaussianDraw2);
				_crossWindPlusConstant = 0d;
			} else {
				final double gaussianDraw3 = localR == null ? 0d : localR.getTruncatedGaussian();
				final double q = gaussianDraw3 * standardDeviation;
				final double rValue = q / 2d;
				_crossWindPlusSlope = slope + rValue / nominalSpeed;
				_crossWindPlusConstant = constant + rValue;
			}
			slope = leewayData._cwlMinusSlope;
			constant = leewayData._cwlMinusConstant;
			standardDeviation = leewayData._cwlMinusStandardDeviation;
			if (constant == 0d) {
				final double gaussianDraw4 = localR == null ? 0d : localR.getTruncatedGaussian();
				_crossWindMinusSlope = getSlopeIfConstantIsZero(slope, standardDeviation, nominalSpeed, gaussianDraw4);
				_crossWindMinusConstant = 0d;
			} else {
				final double gaussianDraw5 = localR == null ? 0d : localR.getTruncatedGaussian();
				final double q = gaussianDraw5 * standardDeviation;
				final double rValue = q / 2d;
				_crossWindMinusSlope = slope + rValue / nominalSpeed;
				_crossWindMinusConstant = constant + rValue;
			}
			_gibingFrequencyPerSecond = leewayData._gibingFrequencyPerSecond;
			_nextFlipSecs = Long.MIN_VALUE;
			_usingPlus = true;
		}

		public double[] getEastAndNorthLeewaySpeeds(final double rawDownwindU, final double rawDownwindV,
				final long simSecs, final Particle particle) {
			final double[] eastAndNorthLeewaySpeeds = new double[] {
					0d, 0d
			};
			/**
			 * Jack Frost's version (given here) eliminates several calculations in the old
			 * version.
			 */
			final double rawWindSpeedSquared = rawDownwindU * rawDownwindU + rawDownwindV * rawDownwindV;
			if (rawWindSpeedSquared <= 0d) {
				return eastAndNorthLeewaySpeeds;
			}
			final double rawWindSpeed = Math.sqrt(rawWindSpeedSquared);
			final double normalizedRawUComponent = rawDownwindU / rawWindSpeed;
			final double normalizedRawVComponent = rawDownwindV / rawWindSpeed;
			if (_downWindConstant == 0d) {
				eastAndNorthLeewaySpeeds[0] = rawDownwindU * _downWindSlope;
				eastAndNorthLeewaySpeeds[1] = rawDownwindV * _downWindSlope;
			} else {
				final double downWindParticleSpeed = _downWindSlope * rawWindSpeed + _downWindConstant;
				eastAndNorthLeewaySpeeds[0] = normalizedRawUComponent * downWindParticleSpeed;
				eastAndNorthLeewaySpeeds[1] = normalizedRawVComponent * downWindParticleSpeed;
			}
			/** Find the crosswind slope. */
			if (!particle.isEnvMean() && simSecs > _nextFlipSecs) {
				if (_nextFlipSecs == Long.MIN_VALUE) {
					_usingPlus = particle.getRandom().nextBoolean();
				} else {
					_usingPlus = !_usingPlus;
				}
				if (_gibingFrequencyPerSecond > 0d) {
					_nextFlipSecs = (long) (particle.getRandom().getExponentialDraw(1d / _gibingFrequencyPerSecond)
							+ simSecs);
				} else {
					_nextFlipSecs = Long.MAX_VALUE;
				}
			}
			final double crossWindSlope, crossWindConstant;
			if (_usingPlus) {
				crossWindSlope = _crossWindPlusSlope;
				crossWindConstant = _crossWindPlusConstant;
			} else {
				crossWindSlope = _crossWindMinusSlope;
				crossWindConstant = _crossWindMinusConstant;
			}
			if (crossWindConstant == 0d) {
				/**
				 * Add in the cross wind contribution; By now, the crosswind is defined to be 90
				 * degrees clockwise from downwind.
				 */
				eastAndNorthLeewaySpeeds[0] += rawDownwindV * crossWindSlope;
				eastAndNorthLeewaySpeeds[1] += -rawDownwindU * crossWindSlope;
			} else {
				final double crossWindParticleSpeed = crossWindSlope * rawWindSpeed + crossWindConstant;
				eastAndNorthLeewaySpeeds[0] += normalizedRawVComponent * crossWindParticleSpeed;
				eastAndNorthLeewaySpeeds[1] += -normalizedRawUComponent * crossWindParticleSpeed;
			}
			return eastAndNorthLeewaySpeeds;
		}
	}

	public ParticleIndexes getParticleIndexes() {
		return _prtclIndxs;
	}

	final public Scenario getScenario() {
		return _scenario;
	}

	final public Tracker getTracker() {
		return _tracker;
	}

	public LeewayCalculator getLeewayCalculator() {
		return _leewayCalculator;
	}

	public SearchObjectType getDistressObjectType() {
		return _distressObjectType;
	}

	public long getBirthSimSecs() {
		return _birthSimSecs;
	}

	public long getDistressSimSecs() {
		return _distressSimSecs;
	}

	public long getExpirationSimSecs() {
		return _expirationSimSecs;
	}

	final public SearchObjectType getSearchObjectTypeFromSimSecs(final long simSecs) {
		return (simSecs < _distressSimSecs) ? _originatingSot : getDistressObjectType();
	}

	public long getLandingSimSecs() {
		return _landingSimSecs;
	}

	public boolean setLandingSimSecs(final long landingSimSecs) {
		if (_landingSimSecs == -1) {
			_landingSimSecs = landingSimSecs;
			return true;
		}
		return false;
	}

	public LatLng3 getDistressLatLng() {
		return _distressLatLng;
	}

	public void setDistressLatLng(final LatLng3 distressLatLng) {
		_distressLatLng = distressLatLng;
	}

	public Sdi getSdi() {
		return null;
	}

	public void setAnchoringSimSecs(final long anchoringSimSecs) {
		_anchoringSimSecs = anchoringSimSecs;
	}

	public TangentCylinder getTangentCylinderFromSimSecs(final LatLng3 latLng, final long simSecs) {
		if (simSecs < _distressSimSecs) {
			return null;
		}
		if (_tangentCylinder == null) {
			_tangentCylinder = TangentCylinder.getTangentCylinder(latLng);
		}
		return _tangentCylinder;
	}

	public double getCompletePrior() {
		final double w0 = _scenario.getScenarioWeight();
		final double w1 = _scenario.getInitialPriorWithinScenario(_distressObjectType);
		final ParticleSet particleSet = _tracker.getParticleSet(_scenario.getIScenario());
		final int sotId = _distressObjectType.getId();
		final int nParticlesObjectType = particleSet.getNParticles(sotId);
		if (nParticlesObjectType == 0) {
			return 0;
		}
		return w0 * w1 / nParticlesObjectType;
	}

	public Randomx getRandom() {
		return _random;
	}

	public long getAnchoringSimSecs() {
		return _anchoringSimSecs;
	}

	public boolean getGuaranteedSticky() {
		return _guaranteedSticky;
	}

	public void capExpirationSimSecs(final long lastSimSecs) {
		_expirationSimSecs = Math.min(_expirationSimSecs, lastSimSecs);
	}

	public void freeMemory() {
		_root.freeMemory();
		_tail.freeMemory();
		_root = _tail = null;
		_cpaCalculatorResults.clear();
		_distressLatLng = null;
		_tangentCylinder = null;
		_legCpaCalculatorResultMap.clear();
		_sortieCpaCalculatorResultMap.clear();
	}

}
