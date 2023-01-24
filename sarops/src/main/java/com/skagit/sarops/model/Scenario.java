package com.skagit.sarops.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.w3c.dom.Element;

import com.skagit.sarops.model.preDistressModel.PreDistressModel;
import com.skagit.sarops.model.preDistressModel.sail.SailData;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.LsFormatter;
import com.skagit.util.TimeUtilities;
import com.skagit.util.cdf.area.Area;
import com.skagit.util.cdf.area.EllipticalArea;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.randomx.Randomx;
import com.skagit.util.randomx.TimeDistribution;

public abstract class Scenario implements Comparable<Scenario> {
	final public static String _RegularScenarioType = "Voyage";
	final public static String _DebrisSightingType = "Debris_Sighting";
	final public static String _LobType = "LOB";
	final public static String _FlareType = "Flare";
	final public static String _R21Type = "R21";
	final private int _iScenario;
	final private int _baseParticleIndex;
	private int[] _sotOrdToCount;
	final private int _nParticles;
	final private int _id;
	final private String _name;
	protected final String _type;
	final private SimCaseManager.SimCase _simCase;
	private double _scenarioWeight;
	private boolean _noDistress;
	protected Area _departureArea = null;
	private TimeDistribution _departureTimeDistribution = null;
	private PreDistressModel _preDistressModel = null;
	private SailData _sailData;
	final private ArrayList<SotWithWt> _distressSotWithWts = new ArrayList<>();
	private Map<Integer, Double> _distressSearchObjectIdToInitialPriorWithinScenario;

	protected Scenario(final SimCaseManager.SimCase simCase, final int id, final String name, final String type,
			final double scenarioWeight, final int iScenario, final int baseParticleIndex, final int nParticles) {
		_simCase = simCase;
		_id = id;
		_name = name;
		_type = type;
		_iScenario = iScenario;
		_baseParticleIndex = baseParticleIndex;
		_nParticles = nParticles;
		_scenarioWeight = scenarioWeight;
		_distressSearchObjectIdToInitialPriorWithinScenario = null;
		_noDistress = false;
		_sailData = null;
		_sotOrdToCount = null;
	}

	public SimCaseManager.SimCase getSimCase() {
		return _simCase;
	}

	public int getId() {
		return _id;
	}

	public String getName() {
		return _name;
	}

	final public String getType() {
		return _type;
	}

	public double getScenarioWeight() {
		return _scenarioWeight;
	}

	public void setInitialPrior(final double scenarioWeight) {
		_scenarioWeight = scenarioWeight;
	}

	public void setNoDistressToTrue() {
		_noDistress = true;
	}

	public boolean getNoDistress() {
		return _preDistressModel == null ? false : _noDistress;
	}

	public double getInitialPriorWithinScenario(final SearchObjectType searchObjectType) {
		final int searchObjectTypeId = searchObjectType.getId();
		return getInitialPriorWithinScenario(searchObjectTypeId);
	}

	public double getInitialPriorWithinScenario(final int searchObjectTypeId) {
		final Double d = _distressSearchObjectIdToInitialPriorWithinScenario.get(searchObjectTypeId);
		return d == null ? 0d : d.doubleValue();
	}

	public int getIScenario() {
		return _iScenario;
	}

	public int getBaseParticleIndex() {
		return _baseParticleIndex;
	}

	public int getNParticles() {
		return _nParticles;
	}

	public boolean checkAndFinalize(final Model model) {
		boolean result = specificCheckAndFinalize(model);
		double ttlWt = 0d;
		for (final SotWithWt sotWithWt : _distressSotWithWts) {
			final double workingWt = sotWithWt.getWorkingWeight();
			if (workingWt < 0d) {
				return false;
			}
			ttlWt += sotWithWt.getWorkingWeight();
		}
		if (0.99 <= ttlWt && ttlWt <= 1.01f) {
		} else {
			SimCaseManager.err(_simCase,
					String.format("Cumulated object weight[%g] for scenario[%d].", ttlWt, getId()));
		}
		for (final SotWithWt sotWithWt : _distressSotWithWts) {
			final double newWorkingWt = sotWithWt.getWorkingWeight() / ttlWt;
			if (newWorkingWt >= 0d) {
				sotWithWt.setWorkingWeight(newWorkingWt);
			} else {
				return false;
			}
		}
		result &= getDepartureArea() != null;
		setSotOrdToCount(model);
		return _departureTimeDistribution != null && result;
	}

	public abstract boolean specificCheckAndFinalize(final Model model);

	public synchronized void add(final SotWithWt distressSotWithWt) {
		_distressSotWithWts.add(distressSotWithWt);
		_distressSearchObjectIdToInitialPriorWithinScenario = null;
	}

	public List<SotWithWt> getDistressSotWithWts() {
		return _distressSotWithWts;
	}

	public PreDistressModel hasPreDistressModel() {
		return _preDistressModel;
	}

	public Element write(final LsFormatter formatter, final Element root, final Model model) {
		final boolean isDebrisSighting = this instanceof DebrisSighting;
		final Element element = formatter.newChild(root, !isDebrisSighting ? "SCENARIO" : "DEBRIS_SIGHTING");
		if (_preDistressModel != null) {
			final long distressRefSecs = _preDistressModel.getDistressRefSecsMean();
			if (distressRefSecs > 0) {
				element.setAttribute("distressTime", TimeUtilities.formatTime(distressRefSecs, false));
				final double distressPlusMinusHrs = _preDistressModel.getDistressPlusMinusHrs();
				if (distressPlusMinusHrs > 0d) {
					element.setAttribute("distressTimePlusOrMinus", distressPlusMinusHrs + " hrs");
				}
			}
		}
		if (_sailData != null) {
			final Element sailElement = formatter.newChild(element, "SAIL");
			_sailData.write(formatter, sailElement, model);
		}
		element.setAttribute("id", LsFormatter.StandardFormat(_id));
		element.setAttribute("name", _name);
		if (!isDebrisSighting) {
			element.setAttribute("weight", _scenarioWeight * 100 + "%");
		}
		final Element pathElement = formatter.newChild(element, "PATH");
		if (_departureArea != null && _departureTimeDistribution != null) {
			final Element departureElement = formatter.newChild(pathElement,
					!isDebrisSighting ? "DEPARTURE_LOCATION" : "DEBRIS_LOCATION");
			final double truncateDistanceInNmi = _departureArea.getTruncateDistanceInNmi();
			if (!Double.isInfinite(truncateDistanceInNmi)) {
				departureElement.setAttribute("truncate_distance", "" + truncateDistanceInNmi + " NM");
			}
			final String errorTag;
			if (_departureArea instanceof EllipticalArea) {
				final EllipticalArea bivariateNormal = (EllipticalArea) _departureArea;
				if (bivariateNormal.getIsUniform()) {
					departureElement.setAttribute("uniform", "" + true);
				}
				errorTag = "x_error";
			} else {
				errorTag = null;
			}
			_departureArea.write(formatter, departureElement, errorTag);
			_departureTimeDistribution.write(formatter, departureElement, false, "TIME");
			for (final SotWithWt searchObjectTypeWithWeight : _distressSotWithWts) {
				searchObjectTypeWithWeight.write(formatter, element, model);
			}
			if (_preDistressModel != null) {
				_preDistressModel.write(formatter, pathElement, model);
			}
			return element;
		}
		return null;
	}

	public void setPreDistressModel(final PreDistressModel preDistressModel) {
		_preDistressModel = preDistressModel;
	}

	public Area getDepartureArea() {
		return _departureArea;
	}

	public LatLng3 getInitialLatLng(final Randomx r, final int overallIdx) {
		final MyLogger logger = SimCaseManager.getLogger(_simCase);
		return _departureArea.generateLatLng(logger, r);
	}

	public TimeDistribution getDepartureTimeDistribution() {
		return _departureTimeDistribution;
	}

	public PreDistressModel getPreDistressModel() {
		return _preDistressModel;
	}

	public void setDepartureArea(final Area area) {
		_departureArea = area;
	}

	public void setDepartureTimeDistribution(final TimeDistribution distribution) {
		_departureTimeDistribution = distribution;
	}

	public boolean deepEquals(final Scenario comparedScenario) {
		if ((_id != comparedScenario._id) || (getType() != comparedScenario.getType())) {
			return false;
		}
		if (_name != null && _name.length() > 0 && comparedScenario._name != null
				&& comparedScenario._name.length() > 0) {
			if (_name.compareTo(comparedScenario._name) != 0) {
				return false;
			}
		}
		if (_scenarioWeight != comparedScenario._scenarioWeight) {
			return false;
		}
		if (!(_departureArea.deepEquals(comparedScenario._departureArea))) {
			return false;
		}
		if (!(_departureTimeDistribution.deepEquals(comparedScenario._departureTimeDistribution))) {
			return false;
		}
		if (_preDistressModel == null) {
			if (comparedScenario._preDistressModel != null) {
				return false;
			}
		} else {
			if (!(_preDistressModel.deepEquals(comparedScenario._preDistressModel))) {
				return false;
			} else if (getNoDistress() != comparedScenario.getNoDistress()) {
				return false;
			} else {
				final SailData hisSailData = comparedScenario._sailData;
				if ((_sailData == null) != (hisSailData == null)) {
					return false;
				}
				if (_sailData != null) {
					if (!_sailData.deepEquals(hisSailData)) {
						return false;
					}
				}
			}
		}
		if (_distressSotWithWts.size() != comparedScenario._distressSotWithWts.size()) {
			return false;
		}
		final Iterator<SotWithWt> it = _distressSotWithWts.iterator();
		final Iterator<SotWithWt> comparedIt = comparedScenario._distressSotWithWts.iterator();
		while (it.hasNext()) {
			final SotWithWt searchObjectTypeWithWeight = it.next();
			final SotWithWt compared = comparedIt.next();
			if (!searchObjectTypeWithWeight.deepEquals(compared)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int compareTo(final Scenario other) {
		return other._id - _id;
	}

	public void close(final SimCaseManager.SimCase simCase) {
		/**
		 * Do whatever one needs to do now that the inputs are known and we're
		 * interested in running a tracker.
		 */
		_distressSearchObjectIdToInitialPriorWithinScenario = new TreeMap<>();
		double totalWeight = 0d;
		for (int iPass = 0; iPass < 2; ++iPass) {
			for (final SotWithWt distressSotWithWt : _distressSotWithWts) {
				final double wt = distressSotWithWt.getWorkingWeight();
				if (iPass == 0) {
					totalWeight += wt;
				} else {
					final int id = distressSotWithWt.getSot().getId();
					final double thisWt = wt / totalWeight;
					_distressSearchObjectIdToInitialPriorWithinScenario.put(id, thisWt);
				}
			}
			if (totalWeight == 0d) {
				return;
			}
		}
	}

	public SailData getSailData() {
		return _sailData;
	}

	public void setSailData(final SailData sailData) {
		_sailData = sailData;
	}

	public boolean hasBearingCalls() {
		if (_LobType.equals(getType()) || _FlareType.equals(getType()) || _R21Type.equals(getType())) {
			return true;
		}
		return false;
	}

	private void setSotOrdToCount(final Model model) {
		final ArrayList<SotWithWt> mySotWithWts = new ArrayList<>();
		for (final SotWithWt sotWithWt : _distressSotWithWts) {
			final double wt = sotWithWt.getWorkingWeight();
			if (wt > 0) {
				mySotWithWts.add(sotWithWt);
			}
		}
		final int nMySots = mySotWithWts.size();
		final int nAllSots = model.getNSearchObjectTypes();
		_sotOrdToCount = new int[nAllSots];
		Arrays.fill(_sotOrdToCount, 0);
		/**
		 * Each of mine gets base, but we award an extra one to the members of
		 * mySearchObjectTypeWithWeights that have low sotOrds. Hence, sort
		 * mySearchObjectTypeWithWeights by sotOrd.
		 */
		mySotWithWts.sort(new Comparator<SotWithWt>() {

			@Override
			public int compare(final SotWithWt o1, final SotWithWt o2) {
				final int id1 = o1.getSot().getId();
				final int sotOrd1 = model.getSotOrd(id1);
				final int id2 = o2.getSot().getId();
				final int sotOrd2 = model.getSotOrd(id2);
				if (sotOrd1 < sotOrd2) {
					return -1;
				}
				return sotOrd1 > sotOrd2 ? 1 : 0;
			}
		});
		final int base = _nParticles / nMySots;
		final int nLeftOver = _nParticles % nMySots;
		for (int k = 0; k < nMySots; ++k) {
			final SotWithWt sotWithWt = mySotWithWts.get(k);
			final int sotId = sotWithWt.getSot().getId();
			final int sotOrd = model.getSotOrd(sotId);
			_sotOrdToCount[sotOrd] = base;
			if (k < nLeftOver) {
				++_sotOrdToCount[sotOrd];
			}
		}
	}

	public static int getSotOrd(final int iParticle, final int[] sotOrdToCount) {
		final int nSots = sotOrdToCount.length;
		int total = 0;
		for (int sotOrd = 0; sotOrd < nSots; ++sotOrd) {
			total += sotOrdToCount[sotOrd];
			if (total > iParticle) {
				return sotOrd;
			}
		}
		return -1;
	}

	public int[] getSotOrdToCount() {
		return _sotOrdToCount;
	}

	public boolean isDebrisSighting() {
		return false;
	}

}
