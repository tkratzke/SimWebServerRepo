package com.skagit.sarops.model.io;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;

import com.skagit.sarops.environment.CurrentsUvGetter;
import com.skagit.sarops.environment.WindsUvGetter;
import com.skagit.sarops.model.DebrisObjectType;
import com.skagit.sarops.model.ExtraGraphicsClass;
import com.skagit.sarops.model.FixHazard;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.SearchObjectType;
import com.skagit.sarops.model.Sortie;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.lrcSet.LrcSet;
import com.skagit.sarops.util.patternUtils.PsCsInfo;
import com.skagit.util.Graphics;
import com.skagit.util.LsFormatter;
import com.skagit.util.TimeUtilities;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;

public class ModelWriter {
	final private LsFormatter _formatter;

	public ModelWriter() {
		_formatter = new LsFormatter();
	}

	public LsFormatter getFormatter() {
		return _formatter;
	}

	public Element createModelElement(final SimCaseManager.SimCase simCase, final Model model) {
		final Element mainElement = _formatter.newElement("SIM");
		mainElement.setAttribute(ModelReader._WriteOcTablesAtt, "" + model.getWriteOcTables());
		final String xmlSimPropertiesFilePath = model.getXmlSimPropertiesFilePath();
		if (xmlSimPropertiesFilePath != null) {
			mainElement.setAttribute(ModelReader._XmlSimPropertiesFilePathAtt, xmlSimPropertiesFilePath);
		}

		/** Start with the environment. */
		final Element envDataElement = _formatter.newChild(mainElement, "ENVDATA");
		/** Winds. */
		final Element outputWindsElement = _formatter.newChild(envDataElement, "WIND");
		final Element inputWindsElement = model.getWindsElement();
		final String directionFrom = inputWindsElement == null ? null : inputWindsElement.getAttribute("directionFrom");
		if (directionFrom != null && directionFrom.length() != 0) {
			outputWindsElement.setAttribute("directionFrom", directionFrom);
		}
		if (inputWindsElement != null) {
			final String confidenceAttribute = inputWindsElement.getAttribute(ModelReader._ConfidenceAtt);
			final boolean haveConfidenceAttribute = confidenceAttribute != null && confidenceAttribute.length() > 0;
			if (!haveConfidenceAttribute) {
				outputWindsElement.setAttribute(ModelReader._ConfidenceAtt, "Not_Supplied");
			} else {
				outputWindsElement.setAttribute(ModelReader._ConfidenceAtt, confidenceAttribute);
				final String preDistressConfidenceAttribute = inputWindsElement
						.getAttribute(ModelReader._PreDistressConfidenceAtt);
				final boolean havePreConfidenceAttribute = confidenceAttribute != null
						&& confidenceAttribute.length() > 0;
				if (havePreConfidenceAttribute) {
					outputWindsElement.setAttribute(ModelReader._PreDistressConfidenceAtt,
							preDistressConfidenceAttribute);
				}
			}
		}
		WindsUvGetter windsUvGetter = model.getWindsUvGetter();
		if (windsUvGetter == null) {
			/** Discard the StringPluses that the following creates. */
			model.setEnvironment(simCase, /* stashEnvFiles= */false, /* overwriteEnvFiles = */false);
			windsUvGetter = model.getWindsUvGetter();
		}
		windsUvGetter.writeElement(outputWindsElement, inputWindsElement, model);

		/** Currents. */
		final Element outputCurrentsElement = _formatter.newChild(envDataElement, "CURRENT");
		final Element inputCurrentsElement = model.getCurrentsElement();
		if (inputCurrentsElement != null) {
			String confidenceAttribute = inputCurrentsElement.getAttribute(ModelReader._ConfidenceAtt);
			confidenceAttribute = confidenceAttribute == null ? "" : confidenceAttribute.trim();
			final boolean haveConfidenceAttribute = confidenceAttribute.length() > 0;
			if (haveConfidenceAttribute) {
				outputCurrentsElement.setAttribute(ModelReader._ConfidenceAtt, confidenceAttribute);
				String preConfidenceAttribute = inputCurrentsElement.getAttribute(ModelReader._ConfidenceAtt);
				preConfidenceAttribute = preConfidenceAttribute == null ? "" : preConfidenceAttribute.trim();
				final boolean havePreConfidenceAttribute = preConfidenceAttribute.length() > 0;
				if (havePreConfidenceAttribute) {
					outputCurrentsElement.setAttribute(ModelReader._PreDistressConfidenceAtt, preConfidenceAttribute);
				}
			}
		}
		CurrentsUvGetter currentsUvGetter = model.getCurrentsUvGetter();
		if (currentsUvGetter == null) {
			/** Discard the StringPluses that the following creates. */
			model.setEnvironment(simCase, /* stashEnvFiles= */false, /* overwriteEnvFiles = */false);
			currentsUvGetter = model.getCurrentsUvGetter();
		}
		currentsUvGetter.writeElement(outputCurrentsElement, inputCurrentsElement, model);

		/** Search Object types. */
		final ArrayList<SearchObjectType> searchObjectTypes = model.getSearchObjectTypes();
		for (final SearchObjectType searchObjectType : searchObjectTypes) {
			searchObjectType.write(_formatter, mainElement);
		}

		/** Debris Object types. */
		final ArrayList<DebrisObjectType> debrisObjectTypes = model.getDebrisObjectTypes();
		for (final DebrisObjectType debrisObjectType : debrisObjectTypes) {
			debrisObjectType.write(_formatter, mainElement);
		}

		/** Scenarios. */
		model.getOriginatingSotWithWt().write(_formatter, mainElement);
		final int nScenarii = model.getNScenarii();
		for (int scenarioIndex = 0; scenarioIndex < nScenarii; ++scenarioIndex) {
			model.getScenario(scenarioIndex).write(_formatter, mainElement, model);
		}

		/** DebrisSightings. */
		final int nDebrisSightings = model.getNDebrisSightings();
		for (int debrisSightingIndex = 0; debrisSightingIndex < nDebrisSightings; ++debrisSightingIndex) {
			model.getDebrisSighting(debrisSightingIndex).write(_formatter, mainElement, model);
		}

		/** Sorties. */
		final List<Sortie> modelSorties = model.getSorties();
		final Iterator<Sortie> sortieIt = modelSorties.iterator();
		while (sortieIt.hasNext()) {
			final Sortie sortie = sortieIt.next();
			addCompletedSearchElement(_formatter, mainElement, sortie);
		}
		addGraphicsElement(_formatter, mainElement, model.getExtraGraphicsObject());

		/** Fixed hazards. */
		final Iterator<FixHazard> fixHazardIterator = model.getFixHazards().iterator();
		while (fixHazardIterator.hasNext()) {
			final FixHazard hazard = fixHazardIterator.next();
			hazard.write(_formatter, mainElement);
		}

		/** The Request tag. */
		final Element requestElement = _formatter.newChild(mainElement, "REQUEST");
		final String typeString = model.getReverseDrift() ? "RunReverseDrift" : "RunSimulation";
		requestElement.setAttribute("type", typeString);
		final Element inputElement = _formatter.newChild(requestElement, "INPUT");
		inputElement.setAttribute("datumTime", TimeUtilities.formatTime(model.getLastOutputRefSecs(), false));
		final String modeName = model.getModeName();
		if (modeName != null) {
			inputElement.setAttribute("mode", modeName);
		} else {
			final int nParticlesPerScenario = model.getNParticlesPerScenario();
			final long monteCarloTimeStepSecs = model.getMonteCarloSecs();
			inputElement.setAttribute("particles", LsFormatter.StandardFormat(nParticlesPerScenario));
			inputElement.setAttribute("timeStep", LsFormatter.StandardFormat(monteCarloTimeStepSecs / 60) + " mins");
		}
		final boolean riverine = model.isRiverine();
		inputElement.setAttribute("river", riverine ? "yes" : "no");
		/**
		 * We're just writing out the interpolation mode so we pretend we're getting it
		 * for currents; that's just a "parroting."
		 */
		final boolean forCurrents = true;
		final String interpolationMode = model.getInterpolationMode(forCurrents);
		inputElement.setAttribute("interpolationMode", interpolationMode);
		final double proportionOfSticky = model.getProportionOfSticky();
		inputElement.setAttribute("proportionOfSticky", String.format("%.2f", proportionOfSticky));
		inputElement.setAttribute("randomSeed", LsFormatter.StandardFormat(model.getRandomSeed()));
		final Extent extent = model.getExtent();
		final float leftLng = (float) extent.getLeftLng();
		final float minLat = (float) extent.getMinLat();
		final float maxLat = (float) extent.getMaxLat();
		inputElement.setAttribute("top", maxLat + " degs");
		inputElement.setAttribute("bottom", minLat + " degs");
		inputElement.setAttribute("left", leftLng + " degs");
		inputElement.setAttribute("right", extent.getRightLng() + " degs");
		inputElement.setAttribute("excludeInitialLandDraws", (model.getExcludeInitialLandDraws()) ? "true" : "false");
		inputElement.setAttribute("excludeInitialWaterDraws", (model.getExcludeInitialWaterDraws()) ? "true" : "false");
		final Element outputElement = _formatter.newChild(requestElement, "OUTPUT");
		outputElement.setAttribute("firstOutputTime", TimeUtilities.formatTime(model.getFirstOutputRefSecs(), false));
		outputElement.setAttribute("file", model.getParticlesFilePath());
		return mainElement;
	}

	public static void writeConstantDistribution(final Element outputElement, final Element inputElement) {
		outputElement.setAttribute("type", "constant");
		if (inputElement != null) {
			outputElement.setAttribute("speed", inputElement.getAttribute("speed"));
			outputElement.setAttribute("dir", inputElement.getAttribute("dir"));
		} else {
			outputElement.setAttribute("speed", "0 kts");
			outputElement.setAttribute("dir", "0 T");
		}
	}

	public static void attachWaypoints(final LsFormatter formatter, final Element patternElement, final Sortie sortie) {
		final List<Sortie.Leg> legs = sortie.getDistinctInputLegs();
		final int numberOfLegs = legs.size();
		for (int legIndex = 0; legIndex < numberOfLegs; ++legIndex) {
			final Sortie.Leg leg = legs.get(legIndex);
			Element wayPointElement = formatter.newChild(patternElement, "WAYPOINT");
			wayPointElement.setAttribute("lat", LsFormatter.StandardFormatForLatOrLng(leg.getLegLatLng0().getLat()));
			wayPointElement.setAttribute("lng", LsFormatter.StandardFormatForLatOrLng(leg.getLegLatLng0().getLng()));
			wayPointElement.setAttribute("dtg", TimeUtilities.formatTime(leg.getLegRefSecs0(), false));
			wayPointElement.setAttribute("dtgs", TimeUtilities.formatTime(leg.getLegRefSecs0(), true));
			if (legIndex != numberOfLegs - 1) {
				final Sortie.Leg nextLeg = legs.get(legIndex + 1);
				if (!nextLeg.getLegLatLng0().equals(leg.getLegLatLng1())) {
					wayPointElement = formatter.newChild(patternElement, "WAYPOINT");
					wayPointElement.setAttribute("lat",
							LsFormatter.StandardFormatForLatOrLng(leg.getLegLatLng1().getLat()));
					wayPointElement.setAttribute("lng",
							LsFormatter.StandardFormatForLatOrLng(leg.getLegLatLng1().getLng()));
					wayPointElement.setAttribute("dtg", TimeUtilities.formatTime(leg.getLegRefSecs1(), false));
					wayPointElement.setAttribute("dtgs", TimeUtilities.formatTime(leg.getLegRefSecs1(), true));
				}
			} else {
				wayPointElement = formatter.newChild(patternElement, "WAYPOINT");
				wayPointElement.setAttribute("lat",
						LsFormatter.StandardFormatForLatOrLng(leg.getLegLatLng1().getLat()));
				wayPointElement.setAttribute("lng",
						LsFormatter.StandardFormatForLatOrLng(leg.getLegLatLng1().getLng()));
				wayPointElement.setAttribute("dtg", TimeUtilities.formatTime(leg.getLegRefSecs1(), false));
				wayPointElement.setAttribute("dtgs", TimeUtilities.formatTime(leg.getLegRefSecs1(), true));
			}
		}
	}

	public static void attachCompObjectTypes(final LsFormatter formatter, final Element element, final Sortie sortie) {
		for (final Map.Entry<Integer, LrcSet> entry : sortie.getViz1LrcSets().entrySet()) {
			final LrcSet lrcSet = entry.getValue();
			final int searchObjectTypeId = entry.getKey();
			final Element soElement = formatter.newChild(element, "COMP_OBJECT_TYPE");
			soElement.setAttribute("id", LsFormatter.StandardFormat(searchObjectTypeId));
			lrcSet.write(formatter, soElement);
		}
	}

	private static Element addCompletedSearchElement(final LsFormatter formatter, final Element root,
			final Sortie sortie) {
		final Element element = (root == null) ? formatter.newElement("COMPLETED_SEARCH")
				: formatter.newChild(root, "COMPLETED_SEARCH");
		element.setAttribute("id", sortie.getId());
		final String sortieName = sortie.getName();
		if (sortieName != null) {
			element.setAttribute("name", sortieName);
		}
		element.setAttribute("motion_type", sortie.getMotionType().getId());
		final Element patternElement = formatter.newChild(element, "PATTERN");
		final PsCsInfo psCsInfo = sortie.getPsCsInfo();
		if (psCsInfo != null) {
			final double creepHdgIn = psCsInfo.getCreepHdg();
			if (0d <= creepHdgIn && creepHdgIn < 360d) {
				patternElement.setAttribute("creepDirection",
						String.format("%s degrees clockwise from north", LsFormatter.StandardFormat(creepHdgIn)));
			}
			final double tsNmiIn = psCsInfo.getTsNmiIn();
			if (tsNmiIn > 0d) {
				patternElement.setAttribute("trackSpacing",
						String.format("%s NM", LsFormatter.StandardFormat(tsNmiIn)));
			}
		}
		attachWaypoints(formatter, patternElement, sortie);
		attachCompObjectTypes(formatter, element, sortie);
		final Extent extent = sortie.getBoundingExtent();
		final Element extentElement = formatter.newChild(element, "EXTENT");
		final Element nwElement = formatter.newChild(extentElement, "NW");
		final LatLng3 nwLatLng = LatLng3.getLatLngB(extent.getLeftLng(), extent.getMaxLat());
		nwElement.setAttribute("Degrees", nwLatLng.getString(5));
		nwElement.setAttribute("DMS", nwLatLng.toNavyString(' ', true));
		final Element seElement = formatter.newChild(extentElement, "SE");
		final LatLng3 seLatLng = LatLng3.getLatLngB(extent.getRightLng(), extent.getMinLat());
		seElement.setAttribute("Degrees", seLatLng.getString(5));
		seElement.setAttribute("DMS", seLatLng.toNavyString(' ', true));
		final long startRefSecs = sortie.getStartRefSecs();
		element.setAttribute("start", TimeUtilities.formatTime(startRefSecs, false));
		final long stopTimeRefSecs = sortie.getStopRefSecs();
		element.setAttribute("end", TimeUtilities.formatTime(stopTimeRefSecs, false));
		return element;
	}

	public static void addGraphicsElement(final LsFormatter formatter, final Element root,
			final ExtraGraphicsClass extraGraphics) {
		final List<Graphics> graphics = extraGraphics.getExtraModelGraphics();
		if (graphics == null || graphics.size() == 0) {
			return;
		}
		final Element element = formatter.newChild(root, "GRAPHICS");
		for (final Graphics graphicsEntry : graphics) {
			final Graphics.GraphicsType type = graphicsEntry._type;
			final Element graphicsElement = formatter.newChild(element, type.name());
			final String colorName = graphicsEntry._colorName;
			if (colorName != null) {
				graphicsElement.setAttribute("color", colorName);
			}
			graphicsElement.setAttribute("name", type.name());
			final double[] parameters = graphicsEntry._parameters;
			switch (type) {
			case CIRCLE:
				graphicsElement.setAttribute("CenterLat", String.format("%.3f", parameters[0]));
				graphicsElement.setAttribute("CenterLng", String.format("%.3f", parameters[1]));
				graphicsElement.setAttribute("Radius", String.format("%.3f NM", parameters[2]));
				break;
			case ELLIPSE:
				graphicsElement.setAttribute("CenterLat", String.format("%.3f", parameters[0]));
				graphicsElement.setAttribute("CenterLng", String.format("%.3f", parameters[1]));
				graphicsElement.setAttribute("SemiMajorInNmi", String.format("%.3f NM", parameters[2]));
				graphicsElement.setAttribute("SemiMinorInNmi", String.format("%.3f NM", parameters[3]));
				graphicsElement.setAttribute("Orientation", String.format("%.3f degs", parameters[4]));
				break;
			case LL_GRID_GAPS:
				graphicsElement.setAttribute("LatGap", String.format("%.7f", parameters[0]));
				graphicsElement.setAttribute("LngGap", String.format("%.7f", parameters[1]));
				break;
			case LL_EDGE:
				graphicsElement.setAttribute("lat0", "" + parameters[0]);
				graphicsElement.setAttribute("lng0", "" + parameters[1]);
				graphicsElement.setAttribute("lat1", "" + parameters[2]);
				graphicsElement.setAttribute("lng1", "" + parameters[3]);
				final int number = (int) Math.round(parameters[4]);
				if (number >= 0) {
					graphicsElement.setAttribute("number", "" + number);
				}
				final boolean includeSecs = true;
				final long refSecs0 = Math.round(parameters[5]);
				if (refSecs0 >= 0) {
					final String t0String = TimeUtilities.formatTime(refSecs0, includeSecs);
					graphicsElement.setAttribute("time0", t0String);
				}
				final long refSecs1 = Math.round(parameters[6]);
				if (refSecs1 >= 0) {
					final String t1String = TimeUtilities.formatTime(refSecs1, includeSecs);
					graphicsElement.setAttribute("time1", t1String);
				}
				break;
			case EXTENT:
				graphicsElement.setAttribute("left", "" + parameters[0]);
				graphicsElement.setAttribute("low", "" + parameters[1]);
				graphicsElement.setAttribute("right", "" + parameters[2]);
				graphicsElement.setAttribute("high", "" + parameters[3]);
				final int extentNumber = (int) Math.round(parameters[4]);
				if (extentNumber >= 0) {
					graphicsElement.setAttribute("number", "" + extentNumber);
				}
				break;
			case POINT_TO_DRAW:
				graphicsElement.setAttribute("lat", "" + parameters[0]);
				graphicsElement.setAttribute("lng", "" + parameters[1]);
				break;
			case R21:
				break;
			default:
				break;
			}
		}
	}

	public static void addDoubleAttribute(final SimCaseManager.SimCase simCase, final Element e,
			final String attributeName, final double d) {
		if (Double.isNaN(d)) {
			SimCaseManager.err(simCase, String.format("Converting an NaN to 0 for Element[%s], Attribute[%s]",
					e.getTagName(), attributeName));
		}
		e.setAttribute(attributeName, LsFormatter.StandardFormat(d));
	}
}
