package com.skagit.sarops.tracker;

import java.util.ArrayList;
import java.util.TreeMap;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.Scenario;
import com.skagit.sarops.model.SearchObjectType;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.Constants;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;

public class MapForTimeStep {
	final private static Key _GlobalKey = new Key(null, null);
	final private TreeMap<Key, Value> _theMap = new TreeMap<>();

	private static class Key implements Comparable<Key> {
		final private Scenario _scenario;
		final private SearchObjectType _sot;
		final private String _string;

		private Key(final Scenario scenario,
				final SearchObjectType searchObjectType) {
			_scenario = scenario;
			_sot = searchObjectType;
			String s =
					_scenario != null ? ("Scen" + _scenario.getId()) : "AllScenarii";
			s += Constants._SectionSymbol;
			s += (_sot != null ? ("SO[" + _sot.getId() + "]") : "AllObjectTypes");
			_string = s;
		}

		@Override
		public int compareTo(final Key key) {
			if ((_scenario == null) != (key._scenario == null)) {
				return _scenario == null ? -1 : 1;
			}
			if (_scenario != null) {
				final int returnValue = _scenario.getId() - key._scenario.getId();
				if (returnValue != 0) {
					return returnValue < 0 ? -1 : 1;
				}
			}
			/** Must appeal to _searchObjectType. */
			if ((_sot == null) != (key._sot == null)) {
				return _sot == null ? -1 : 1;
			}
			if (_sot != null) {
				final int returnValue = _sot.getId() - key._sot.getId();
				if (returnValue != 0) {
					return returnValue < 0 ? -1 : 1;
				}
			}
			return 0;
		}

		public String getString() {
			return _string;
		}
	}

	public static class Value {
		public double _sumOfAllWeights;
		public double _sumOfWeightTimesPos;
		public double _sumOfWeightTimesPFail;
		public int _countOfAll;
		public double _countOfUnderway;
		/**
		 * Underway is not "stuck on land." Hence, "landed" implies distress.
		 */
		public double _sumOfLandedWeights;
		public double _landedSumOfWeightTimesPos;
		public double _landedSumOfWeightTimesPFail;
		public int _countOfLanded;
		/** distress includes both landed and non-landed distress. */
		public double _distressSumOfPrior;
		public double _distressSumOfWeightTimesPos;
		public double _distressSumOfWeightTimesPFail;
		public int _countOfDistress;
		public Extent _extent;

		private Value() {
			_sumOfAllWeights = 0d;
			_sumOfWeightTimesPos = 0d;
			_sumOfWeightTimesPFail = 0d;
			_countOfAll = 0;
			_countOfUnderway = 0;
			_sumOfLandedWeights = 0d;
			_landedSumOfWeightTimesPos = 0d;
			_landedSumOfWeightTimesPFail = 0d;
			_countOfLanded = 0;
			_distressSumOfPrior = 0d;
			_distressSumOfWeightTimesPos = 0d;
			_distressSumOfWeightTimesPFail = 0d;
			_countOfDistress = 0;
			_extent = Extent.getUnsetExtent();
		}
	}

	public MapForTimeStep(final SimCaseManager.SimCase _simCase,
			final long refSecs, final boolean isLastRefSecs, final Model model,
			final ParticleSet[] particleSets, final ParticlesFile particlesFile) {
		final int nScenarii = model.getNScenarii();
		final int nParticlesPerScenario = model.getNParticlesPerScenario();
		for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
			final ParticleSet particleSet = particleSets[iScenario];
			final Scenario scenario = particleSet._scenario;
			for (int iParticle = 0; iParticle < nParticlesPerScenario;
					++iParticle) {
				final ParticleIndexes prtclIndxs =
						ParticleIndexes.getStandardOne(model, iScenario, iParticle);
				final int searchObjectTypeId =
						particlesFile.getObjectTypeId(refSecs, prtclIndxs);
				final SearchObjectType searchObjectType =
						model.getSearchObjectType(searchObjectTypeId);
				final double origPrior = particlesFile.getInitPrior(prtclIndxs);
				final double cumPFail =
						particlesFile.getCumPFail(refSecs, prtclIndxs);
				final LatLng3 latLng = particlesFile.getLatLng(refSecs, prtclIndxs);
				final boolean distress;
				if (model.getReverseDrift()) {
					distress = true;
				} else {
					final long distressRefSecs =
							particlesFile.getDistressRefSecs(prtclIndxs);
					distress = refSecs >= distressRefSecs;
				}
				final long landedRefSecs =
						particlesFile.getLandingRefSecs(prtclIndxs);
				final boolean landed =
						0 <= landedRefSecs && landedRefSecs <= refSecs;
				/** There are 4 entries in the map that we have to update. */
				updateAnEntry(_simCase, scenario, searchObjectType, origPrior,
						cumPFail, distress, landed, latLng);
				updateAnEntry(_simCase, scenario, null, origPrior, cumPFail,
						distress, landed, latLng);
				updateAnEntry(_simCase, null, searchObjectType, origPrior, cumPFail,
						distress, landed, latLng);
				updateAnEntry(_simCase, null, null, origPrior, cumPFail, distress,
						landed, latLng);
			}
		}
	}

	private void updateAnEntry(final SimCaseManager.SimCase simCase,
			final Scenario scenario, final SearchObjectType searchObjectType,
			final double origWeight, final double cumPFail,
			final boolean distress, final boolean landed, final LatLng3 latLng) {
		final Key key = new Key(scenario, searchObjectType);
		Value value = _theMap.get(key);
		if (value == null) {
			value = new Value();
			_theMap.put(key, value);
		}
		value._sumOfAllWeights += origWeight;
		final double pos = 1d - cumPFail;
		value._sumOfWeightTimesPos += origWeight * pos;
		value._sumOfWeightTimesPFail += origWeight * cumPFail;
		++value._countOfAll;
		if (distress) {
			value._distressSumOfPrior += origWeight;
			value._distressSumOfWeightTimesPos += origWeight * pos;
			value._distressSumOfWeightTimesPFail += origWeight * cumPFail;
			++value._countOfDistress;
			if (landed) {
				value._sumOfLandedWeights += origWeight;
				value._landedSumOfWeightTimesPos += origWeight * pos;
				value._landedSumOfWeightTimesPFail += origWeight * cumPFail;
				++value._countOfLanded;
			}
		} else {
			++value._countOfUnderway;
		}
		value._extent = value._extent.buildExtension(latLng);
	}

	public Value getGlobalValue() {
		final Value value = _theMap.get(_GlobalKey);
		return value;
	}

	public Value getValueFor(final Scenario scenario) {
		final Value value = _theMap.get(new Key(scenario, null));
		return value;
	}

	public Value getValueFor(final SearchObjectType sot) {
		final Value value = _theMap.get(new Key(null, sot));
		return value;
	}

	public static String getGlobalString() {
		final String returnValue = _GlobalKey.getString();
		return returnValue;
	}

	public static String getStringFor(final SearchObjectType sot) {
		final String returnValue = new Key(null, sot).getString();
		return returnValue;
	}

	public static String getStringFor(final Scenario scenario) {
		final String returnValue = new Key(scenario, null).getString();
		return returnValue;
	}

	public ArrayList<SearchObjectType> getSots() {
		final ArrayList<SearchObjectType> returnValue = new ArrayList<>();
		for (final Key key : _theMap.keySet()) {
			if (key._scenario == null && key._sot != null) {
				returnValue.add(key._sot);
			}
		}
		return returnValue;
	}

}
