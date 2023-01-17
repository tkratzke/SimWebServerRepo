package com.skagit.sarops.tracker;

import java.io.FileOutputStream;
import java.util.Map;
import java.util.TreeMap;

import org.w3c.dom.Element;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.Scenario;
import com.skagit.sarops.model.SearchObjectType;
import com.skagit.sarops.simCaseManager.MainRunner;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.LsFormatter;
import com.skagit.util.TimeUtilities;

public class SummaryStatistics {
	final private Model _model;
	final private Tracker _tracker;
	final private LsFormatter _statsFormatter;
	final private Element _statsRootElement;
	final private Element[] _scenarioElements;

	public SummaryStatistics(final Model model, final Tracker tracker) {
		_model = model;
		_tracker = tracker;
		_statsFormatter = new LsFormatter();
		_statsRootElement =
				_statsFormatter.newElement("SIM_SUMMARY_STATISTICS");
		final long nowRefSecs =
				TimeUtilities.convertToRefSecs(System.currentTimeMillis() / 1000L);
		_statsRootElement.setAttribute("generated",
				TimeUtilities.formatTime(nowRefSecs, true));
		final int nScenarii = _model.getNScenarii();
		_scenarioElements = new Element[nScenarii];
		listSearchObjectTypes();
		listScenarios();
	}

	private void writeWithTimeCommentChoice(
			final SimCaseManager.SimCase simCase, final String filePath,
			final boolean addTimeComment) {
		try (FileOutputStream fos = new FileOutputStream(filePath)) {
			if (addTimeComment) {
				_statsFormatter.dumpWithTimeComment(_statsRootElement, fos);
			} else {
				_statsFormatter.dumpWithNoTimeComment(_statsRootElement, fos);
			}
		} catch (final Exception e) {
			MainRunner.HandleFatal(simCase, new RuntimeException(e));
		}
	}

	public void writeWithTimeComment(final SimCaseManager.SimCase simCase,
			final String filePath) {
		writeWithTimeCommentChoice(simCase, filePath,
				/* addTimeComment= */true);
	}

	public void writeWithNoTimeComment(final SimCaseManager.SimCase simCase,
			final String filePath) {
		writeWithTimeCommentChoice(simCase, filePath,
				/* addTimeComment= */false);
	}

	private void listSearchObjectTypes() {
		final Element searchObjectTypesElement =
				_statsFormatter.newChild(_statsRootElement, "SEARCH_OBJECT_TYPES");
		for (final SearchObjectType searchObjectType : _model
				.getSearchObjectTypes()) {
			searchObjectType.write(_statsFormatter, searchObjectTypesElement);
		}
	}

	private void listScenarios() {
		final Element scenariosElement =
				_statsFormatter.newChild(_statsRootElement, "SCENARIOS");
		final int numberOfScenarios = _model.getNScenarii();
		for (int scenarioIndex = 0; scenarioIndex < numberOfScenarios;
				++scenarioIndex) {
			final Scenario scenario = _model.getScenario(scenarioIndex);
			final Element scenarioElement =
					scenario.write(_statsFormatter, scenariosElement, _model);
			_scenarioElements[scenarioIndex] = scenarioElement;
		}
	}

	public void endSimulation() {
		final Model model = _tracker.getModel();
		final boolean reverseDrift = model.getReverseDrift();
		final long[] refSecsS = _tracker.getParticlesFile().getRefSecsS();
		final long firstRefSecs = refSecsS[0];
		final int nScenarii = _model.getNScenarii();
		final int nParticles = _model.getNParticlesPerScenario();
		for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
			final Map<SearchObjectType, long[]> timesPerSearchObjectType =
					new TreeMap<>();
			final Particle[] particles =
					_tracker.getParticleSet(iScenario)._particles;
			for (int iParticle = 0; iParticle < nParticles; ++iParticle) {
				final Particle particle = particles[iParticle];
				final long landingSimSecs = particle.getLandingSimSecs();
				final long distressRefSecs;
				if (reverseDrift) {
					distressRefSecs = firstRefSecs;
				} else {
					final long distressSimSecs = particle.getDistressSimSecs();
					distressRefSecs = _model.getRefSecs(distressSimSecs);
				}
				final long landingRefSecs = _model.getRefSecs(landingSimSecs);
				final SearchObjectType searchObjectType =
						particle.getDistressObjectType();
				long[] searchObjectTypeRefSecsS =
						timesPerSearchObjectType.get(searchObjectType);
				if (searchObjectTypeRefSecsS == null) {
					searchObjectTypeRefSecsS = new long[] { distressRefSecs,
							distressRefSecs, landingRefSecs, landingRefSecs };
					timesPerSearchObjectType.put(searchObjectType,
							searchObjectTypeRefSecsS);
				} else {
					searchObjectTypeRefSecsS[0] =
							Math.min(searchObjectTypeRefSecsS[0], distressRefSecs);
					searchObjectTypeRefSecsS[1] =
							Math.max(searchObjectTypeRefSecsS[1], distressRefSecs);
					if (landingRefSecs > 0) {
						searchObjectTypeRefSecsS[2] =
								(searchObjectTypeRefSecsS[2] <= 0) ? landingRefSecs :
										Math.min(searchObjectTypeRefSecsS[2], landingRefSecs);
						searchObjectTypeRefSecsS[3] =
								Math.max(searchObjectTypeRefSecsS[3], landingRefSecs);
					}
				}
			}
			{
				final Element distressTimeElement = _statsFormatter
						.newChild(_scenarioElements[iScenario], "DISTRESS_TIMES");
				for (final Map.Entry<SearchObjectType, long[]> entry : timesPerSearchObjectType
						.entrySet()) {
					final Element element = _statsFormatter
							.newChild(distressTimeElement, "SEARCH_OBJECT_TYPE");
					element.setAttribute("id",
							LsFormatter.StandardFormat(entry.getKey().getId()));
					element.setAttribute("earliestDistressTime",
							TimeUtilities.formatTime(entry.getValue()[0], false));
					element.setAttribute("latestDistressTime",
							TimeUtilities.formatTime(entry.getValue()[1], false));
				}
			}
			{
				final Element landingTimeElement = _statsFormatter
						.newChild(_scenarioElements[iScenario], "LANDING_TIMES");
				for (final Map.Entry<SearchObjectType, long[]> entry : timesPerSearchObjectType
						.entrySet()) {
					if (entry.getValue()[2] > 0) {
						final Element element = _statsFormatter
								.newChild(landingTimeElement, "SEARCH_OBJECT_TYPE");
						element.setAttribute("id",
								LsFormatter.StandardFormat(entry.getKey().getId()));
						element.setAttribute("earliestLandingTime",
								TimeUtilities.formatTime(entry.getValue()[2], true));
						element.setAttribute("latestLandingTime",
								TimeUtilities.formatTime(entry.getValue()[3], true));
					}
				}
			}
		}
	}
}
