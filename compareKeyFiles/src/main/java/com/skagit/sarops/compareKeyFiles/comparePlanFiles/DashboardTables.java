package com.skagit.sarops.compareKeyFiles.comparePlanFiles;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.TreeMap;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.util.ElementIterator;
import com.skagit.util.LsFormatter;

public class DashboardTables {

	final public static double _SmallNumber = 1.0e-20;

	final public PlannerModel _plannerModel;
	final public double _netGain;
	final public double _thisPlan;
	final public double _optnScore;
	final public double _globalJoint;
	final public double _globalObjectProbability;
	final public double _globalRemainingProbability;
	final public TreeMap<String, PttrnVblStructure> _idToPttrnVblStructure;
	final public TreeMap<Integer, SotStructure> _sotIntToSrchObjctStructure;

	public static class PttrnVblStructure {
		/** From TopLeft: */
		public final String _pvId;
		public final double _selectedPos;
		/** From TopRight: */
		public final double _area;
		public final double _jointPos;
		public final double _objectProbability;
		public final double _ts;
		public TreeMap<Integer, LittlePvStructure> _sotIntToLittlePvStructure;

		private PttrnVblStructure(final String pvId, final double selectedPos,
				final double area, final double jointPos,
				final double objectProbability, final double ts) {
			_pvId = pvId;
			_selectedPos = selectedPos;
			_area = area;
			_jointPos = jointPos;
			_objectProbability = objectProbability;
			_ts = ts;
			_sotIntToLittlePvStructure =
					new TreeMap<>();
		}
	}

	public static class LittlePvStructure {
		public final double _littleConditionalPos;
		public final double _littleCoverage;
		public final double _littleJointPos;
		public final double _littleObjectProbability;
		public final double _littleSw;

		private LittlePvStructure(final double littleConditionalPos,
				final double littleCoverage, final double littleJointPos,
				final double littleObjectProbability, final double littleSw) {
			_littleConditionalPos = littleConditionalPos;
			_littleCoverage = littleCoverage;
			_littleJointPos = littleJointPos;
			_littleObjectProbability = littleObjectProbability;
			_littleSw = littleSw;
		}
	}

	public static class SotStructure {
		public final int _sotInt;
		public final double _conditionalPos;
		public final double _jointPosSot;
		public final double _netGainSot;
		public final double _objectProbability;
		public final double _remainingProbability;

		private SotStructure(final int sotInt, final double conditionalPos,
				final double jointPosSot, final double netGainSot,
				final double objectProbability, final double remainingProbability) {
			_sotInt = sotInt;
			_conditionalPos = conditionalPos;
			_jointPosSot = jointPosSot;
			_netGainSot = netGainSot;
			_objectProbability = objectProbability;
			_remainingProbability = remainingProbability;
		}
	}

	public DashboardTables(final PlannerModel plannerModel,
			final File dashboardTablesFile) {
		_plannerModel = plannerModel;
		_idToPttrnVblStructure = new TreeMap<>();
		_sotIntToSrchObjctStructure = new TreeMap<>();

		Document document = null;
		try (final FileInputStream fis =
				new FileInputStream(dashboardTablesFile)) {
			document = LsFormatter._DocumentBuilder.parse(fis);
		} catch (final SAXException e) {
		} catch (final IOException e) {
		}
		final Element elt0 = document.getDocumentElement();

		final ElementIterator it0 = new ElementIterator(elt0);
		double netGainPos = Double.NaN;
		double thisPlanPos = Double.NaN;
		double optnScore = Double.NaN;
		double globalJointPos = Double.NaN;
		double globalObjectProbability = Double.NaN;
		double globalRemainingProbability = Double.NaN;
		final TreeMap<String, Double> pvIdToSelectedPos =
				new TreeMap<>();
		while (it0.hasNextElement()) {
			final Element elt1 = it0.nextElement();
			final String tag1 = elt1.getTagName();
			if (tag1.equals("TOP_LEFT")) {
				final ElementIterator it1 = new ElementIterator(elt1);
				while (it1.hasNextElement()) {
					final Element elt2 = it1.nextElement();
					final String tag2 = elt2.getTagName();
					if (tag2.equals("PATTERN")) {
						final String pvId = elt2.getAttribute("SruId");
						final double selectedPos =
								getDouble(elt2, "SelectedObjects-POS");
						if (pvId != null && !Double.isNaN(selectedPos)) {
							pvIdToSelectedPos.put(pvId, selectedPos);
						}
					}
				}
			}
			if (tag1.equals("BOTTOM_LEFT")) {
				netGainPos = getDouble(elt1, "NetGain");
				thisPlanPos = getDouble(elt1, "ThisPlan");
			}
			if (tag1.equals("TOP_RIGHT")) {
				final ElementIterator it1 = new ElementIterator(elt1);
				while (it1.hasNextElement()) {
					final Element elt2 = it1.nextElement();
					final String tag2 = elt2.getTagName();
					if (tag2.equals("PATTERN")) {
						final String pvId = elt2.getAttribute("SruId");
						final Double selectedPosD = pvIdToSelectedPos.get(pvId);
						final double selectedPos =
								selectedPosD == null ? 0d : selectedPosD;
						final double area = getDouble(elt2, "Area");
						final double jointPos = getDouble(elt2, "JointPOS");
						final double objectProbability =
								getDouble(elt2, "ObjectProbability");
						final double ts = getDouble(elt2, "Ts");
						final PttrnVblStructure pvStruct = new PttrnVblStructure(pvId,
								selectedPos, area, jointPos, objectProbability, ts);
						_idToPttrnVblStructure.put(pvId, pvStruct);

						final ElementIterator it2 = new ElementIterator(elt2);
						while (it2.hasNextElement()) {
							final Element elt3 = it2.nextElement();
							final String tag3 = elt3.getTagName();
							if (tag3.equals("SO")) {
								int sotInt = -1;
								final String sotIntString = elt2.getAttribute("SoId");
								if (sotIntString == null) {
									continue;
								}
								try {
									sotInt = Integer.parseInt(sotIntString);
								} catch (final NumberFormatException e) {
									continue;
								}
								if (sotInt == -1) {
									continue;
								}
								final double littleConditionalPos =
										getDouble(elt3, "ConditionalPOS");
								final double littleCoverage = getDouble(elt3, "Coverage");
								final double littleJointPos = getDouble(elt3, "JointPOS");
								final double littleObjectProbability =
										getDouble(elt3, "ObjectProbability");
								final double littleSw = getDouble(elt3, "SW");
								final LittlePvStructure littlePvStructure =
										new LittlePvStructure(littleConditionalPos,
												littleCoverage, littleJointPos,
												littleObjectProbability, littleSw);
								pvStruct._sotIntToLittlePvStructure.put(sotInt,
										littlePvStructure);
							}
						}
					}
				}
			}
			if (tag1.equals("BOTTOM_RIGHT")) {
				optnScore = getDouble(elt1, "OptnScore");
				final ElementIterator it1 = new ElementIterator(elt1);
				while (it1.hasNextElement()) {
					final Element elt2 = it1.nextElement();
					final String tag2 = elt2.getTagName();
					if (tag2.equals("SO")) {
						int sotInt = -1;
						final String sotIntString = elt2.getAttribute("SoId");
						if (sotIntString == null) {
							continue;
						}
						try {
							sotInt = Integer.parseInt(sotIntString);
						} catch (final NumberFormatException e) {
							continue;
						}
						if (sotInt == -1) {
							continue;
						}
						final double conditionalPos = getDouble(elt2, "ConditionalPOS");
						final double jointPos = getDouble(elt2, "JointPOS");
						final double netGain = getDouble(elt2, "NetGain");
						final double objectProbability =
								getDouble(elt2, "ObjectProbability");
						final double remainingProbability =
								getDouble(elt2, "RemainingProbability");
						final SotStructure srchObjctStructure =
								new SotStructure(sotInt, conditionalPos, jointPos, netGain,
										objectProbability, remainingProbability);
						_sotIntToSrchObjctStructure.put(sotInt, srchObjctStructure);
					}
					if (tag2.equals("ALL_SOs")) {
						globalJointPos = getDouble(elt2, "JointPOS");
						globalObjectProbability = getDouble(elt2, "ObjectProbability");
						globalRemainingProbability =
								getDouble(elt2, "RemainingProbability");
					}
				}
			}
		}
		_netGain = netGainPos;
		_thisPlan = thisPlanPos;
		_optnScore = optnScore;
		_globalJoint = globalJointPos;
		_globalObjectProbability = globalObjectProbability;
		_globalRemainingProbability = globalRemainingProbability;
	}

	private static double getDouble(final Element elt,
			final String attributeName) {
		String numberString = elt.getAttribute(attributeName);
		final int numberStringLength =
				numberString == null ? -1 : numberString.length();
		if (numberStringLength == 0) {
			return Double.NaN;
		}
		if (numberString.startsWith("<")) {
			return _SmallNumber;
		}
		final boolean havePerCent =
				numberString.lastIndexOf('%') == numberStringLength - 1;
		if (havePerCent) {
			numberString = numberString.substring(0, numberStringLength - 1);
		}
		try {
			double proportion = Double.parseDouble(numberString);
			if (havePerCent) {
				proportion /= 100d;
			}
			return proportion;
		} catch (final NumberFormatException e) {
		}
		return Double.NaN;
	}
}
