package com.skagit.sarops.planner;

import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import org.w3c.dom.Element;

import com.skagit.sarops.computePFail.DetectValues;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.Sortie;
import com.skagit.sarops.model.io.ModelWriter;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.planner.posFunction.PosFunction;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.planner.posFunction.pFailsCache.PFailsCache;
import com.skagit.sarops.planner.posFunction.pvValueArrayPlus.PvValueArrayPlus;
import com.skagit.sarops.planner.summarySums.SummarySumsBag;
import com.skagit.sarops.simCaseManager.MainRunner;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.sarops.tracker.ParticlesFile;
import com.skagit.sarops.util.SimGlobalStrings;
import com.skagit.util.LsFormatter;
import com.skagit.util.StringUtilities;
import com.skagit.util.TimeUtilities;

public class Eval {
	final private PvValueArrayPlus _plus;
	final private long _criticalRefSecs;
	final private String _outputFileName;
	final private Map<String, RawSums> _rawSumsMap;

	private Eval(final PvValueArrayPlus plus) {
		_plus = plus;
		final Planner planner = plus.getPlanner();
		final PlannerModel plannerModel = planner.getPlannerModel();
		final String evalFileName = plannerModel.getEvalFileName();
		final int nPttrnVbls = plannerModel.getNPttrnVbls();
		_outputFileName =
				evalFileName.substring(0, evalFileName.length() - 4) + "Out.xml";
		/**
		 * The time that defines whether something is in distress or not, and
		 * whether something is landed or not, and what the object type is, for
		 * the "conditional" calculations, is the last stopTime of the new
		 * sorties.
		 */
		final ParticlesManager particlesManager = planner.getParticlesManager();
		final ParticlesFile particlesFile = particlesManager.getParticlesFile();
		long criticalRefSecs = particlesFile.getRefSecsS()[0];
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PvValue pvValue = plus.getPvValue(grandOrd);
			if (pvValue != null) {
				final Sortie sortie = pvValue.getSortie();
				criticalRefSecs =
						Math.max(criticalRefSecs, sortie.getStopRefSecs());
			}
		}
		_criticalRefSecs = criticalRefSecs;
		_rawSumsMap = getRawSumsMap(planner.getSimCase(), particlesFile);
	}

	private void fillInSortieElement(final SimCaseManager.SimCase simCase,
			final LsFormatter formatter, final Element rootElement,
			final Sortie sortieX, final String sortieId) {
		final long refSecs;
		if (sortieId != null) {
			rootElement.setAttribute("id", sortieId);
			refSecs = sortieX.getStopRefSecs();
		} else {
			refSecs = _criticalRefSecs;
		}
		rootElement.setAttribute("dtg",
				TimeUtilities.formatTime(refSecs, false));
		final String key = SummarySumsBag.getKey(sortieId, null);
		final RawSums rawSums = _rawSumsMap.get(key);
		final double cumPFail = rawSums._sumOfPriorTimesOldPFailTimesNewPFail /
				rawSums._sumOfPriors;
		final double cumPos = 1d - cumPFail;
		ModelWriter.addDoubleAttribute(simCase, rootElement, "cumPOS", cumPos);
		final double prePFail =
				rawSums._sumOfPriorTimesOldPFail / rawSums._sumOfPriors;
		final double prePos = 1d - prePFail;
		final double deltaCumPos = cumPos - prePos;
		ModelWriter.addDoubleAttribute(simCase, rootElement, "deltaCumPOS",
				deltaCumPos);
		final double totalPFail =
				rawSums._sumOfPriorTimesOldPFailTimesNewPFail /
						rawSums._sumOfPriorTimesOldPFail;
		final double totalPos = 1d - totalPFail;
		ModelWriter.addDoubleAttribute(simCase, rootElement, "totalPOS",
				totalPos);
		final Planner planner = _plus.getPlanner();
		final ParticlesManager particlesManager = planner.getParticlesManager();
		for (final int objectType : particlesManager.getAllObjectTypeIds()) {
			final Element objectTypeElement =
					formatter.newChild(rootElement, "SEARCH_OBJECT_TYPE");
			objectTypeElement.setAttribute("id", String.format("%d", objectType));
			final RawSums rawSums1 =
					_rawSumsMap.get(SummarySumsBag.getKey(sortieId, objectType));
			if (rawSums._sumOfPriors > 0d) {
				final double initialProbability =
						rawSums1._sumOfPriors / rawSums._sumOfPriors;
				ModelWriter.addDoubleAttribute(simCase, objectTypeElement,
						"initialProbability", initialProbability);
				if (rawSums1._sumOfPriors > 0d) {
					final double cumConditionalPFail =
							rawSums1._sumOfPriorTimesOldPFailTimesNewPFail /
									rawSums1._sumOfPriors;
					final double cumConditionalPos = 1d - cumConditionalPFail;
					ModelWriter.addDoubleAttribute(simCase, objectTypeElement,
							"cumConditional", cumConditionalPos);
					final double cumJointPos = cumConditionalPos * initialProbability;
					ModelWriter.addDoubleAttribute(simCase, objectTypeElement,
							"cumJoint", cumJointPos);
					final double oldConditionalPfail =
							rawSums1._sumOfPriorTimesOldPFail / rawSums1._sumOfPriors;
					final double oldConditionalPos = 1d - oldConditionalPfail;
					final double deltaCumConditional =
							cumConditionalPos - oldConditionalPos;
					ModelWriter.addDoubleAttribute(simCase, objectTypeElement,
							"deltaCumConditional", deltaCumConditional);
					final double oldJointPos = oldConditionalPos * initialProbability;
					final double deltaCumJointPos = cumJointPos - oldJointPos;
					ModelWriter.addDoubleAttribute(simCase, objectTypeElement,
							"deltaCumJoint", deltaCumJointPos);
					/**
					 * Do the part of the output for when we assume that the initial
					 * distribution is coming out of Sim.
					 */
					if (rawSums._sumOfPriorTimesOldPFail > 0d) {
						final double zInitialProbability =
								rawSums1._sumOfPriorTimesOldPFail /
										rawSums._sumOfPriorTimesOldPFail;
						ModelWriter.addDoubleAttribute(simCase, objectTypeElement,
								"zInitialProbability", zInitialProbability);
						final double zConditionalPfail =
								rawSums1._sumOfPriorTimesOldPFailTimesNewPFail /
										rawSums1._sumOfPriorTimesOldPFail;
						final double zConditionalPos = 1d - zConditionalPfail;
						ModelWriter.addDoubleAttribute(simCase, objectTypeElement,
								"zConditionalPOS", zConditionalPos);
						final double zJointPos = zConditionalPos * zInitialProbability;
						ModelWriter.addDoubleAttribute(simCase, objectTypeElement,
								"totalJointPOS", zJointPos);
						ModelWriter.addDoubleAttribute(simCase, objectTypeElement,
								"zJointPOS", zJointPos);
					} else {
						objectTypeElement.setAttribute("zInitialProbability", "0.0");
						objectTypeElement.setAttribute("zConditionalPOS", "0.0");
						objectTypeElement.setAttribute("totalJointPOS", "0.0");
						objectTypeElement.setAttribute("zJointPOS", "0.0");
					}
				} else {
					objectTypeElement.setAttribute("cumConditional", "0.0");
					objectTypeElement.setAttribute("cumJoint", "0.0");
					objectTypeElement.setAttribute("deltaCumConditional", "0.0");
					objectTypeElement.setAttribute("deltaCumJoint", "0.0");
					objectTypeElement.setAttribute("zInitialProbability", "0.0");
					objectTypeElement.setAttribute("zConditionalPOS", "0.0");
					objectTypeElement.setAttribute("totalJointPOS", "0.0");
					objectTypeElement.setAttribute("zJointPOS", "0.0");
				}
			} else {
				objectTypeElement.setAttribute("initialProbability", "0.0");
				objectTypeElement.setAttribute("cumConditional", "0.0");
				objectTypeElement.setAttribute("cumJoint", "0.0");
				objectTypeElement.setAttribute("deltaCumConditional", "0.0");
				objectTypeElement.setAttribute("deltaCumJoint", "0.0");
				objectTypeElement.setAttribute("zInitialProbability", "0.0");
				objectTypeElement.setAttribute("zConditionalPOS", "0.0");
				objectTypeElement.setAttribute("totalJointPOS", "0.0");
				objectTypeElement.setAttribute("zJointPOS", "0.0");
			}
		}
	}

	private void fillInSimSummaryElement(final SimCaseManager.SimCase simCase,
			final LsFormatter formatter, final Element rootElement) {
		final Planner planner = _plus.getPlanner();
		final ParticlesManager particlesManager = planner.getParticlesManager();
		final ParticlesFile particlesFile = particlesManager.getParticlesFile();
		final Model model = particlesFile.getModel();
		long lastOldSortieRefSecs =
				particlesManager.getParticlesFile().getRefSecsS()[0];
		for (final Sortie sortie : model.getSorties()) {
			lastOldSortieRefSecs =
					Math.max(lastOldSortieRefSecs, sortie.getStopRefSecs());
		}
		rootElement.setAttribute("dtg",
				TimeUtilities.formatTime(lastOldSortieRefSecs, false));
		final RawSums grandRawSums =
				_rawSumsMap.get(SummarySumsBag.getKey(null, null));
		final double cumPFail =
				grandRawSums._sumOfPriorTimesOldPFail / grandRawSums._sumOfPriors;
		final double cumPos = 1d - cumPFail;
		ModelWriter.addDoubleAttribute(simCase, rootElement, "cumPOS", cumPos);
		for (final int objectType : particlesManager.getAllObjectTypeIds()) {
			final Element objectTypeElement =
					formatter.newChild(rootElement, "SEARCH_OBJECT_TYPE");
			objectTypeElement.setAttribute("id", String.format("%d", objectType));
			final RawSums rawSums1 =
					_rawSumsMap.get(SummarySumsBag.getKey(null, objectType));
			final double initialProbability =
					rawSums1._sumOfPriors / grandRawSums._sumOfPriors;
			ModelWriter.addDoubleAttribute(simCase, objectTypeElement,
					"initialProbability", initialProbability);
			final double cumConditionalPos;
			final double cumJointPos;
			if (rawSums1._sumOfPriors > 0) {
				final double cumConditionalPFail =
						rawSums1._sumOfPriorTimesOldPFail / rawSums1._sumOfPriors;
				cumConditionalPos = 1d - cumConditionalPFail;
				cumJointPos = cumConditionalPos * initialProbability;
			} else {
				cumConditionalPos = 0d;
				cumJointPos = 0d;
			}
			ModelWriter.addDoubleAttribute(simCase, objectTypeElement,
					"cumConditional", cumConditionalPos);
			ModelWriter.addDoubleAttribute(simCase, objectTypeElement, "cumJoint",
					cumJointPos);
		}
	}

	public void reportResults() {
		final Planner planner = _plus.getPlanner();
		final PlannerModel plannerModel = planner.getPlannerModel();
		final SimCaseManager.SimCase simCase = planner.getSimCase();
		if (_outputFileName != null) {
			final LsFormatter outFormatter = new LsFormatter();
			final Element resultsElement =
					outFormatter.newElement("EVAL_RESULTS");
			final Element simSummaryElement =
					outFormatter.newChild(resultsElement, "SIM_SUMMARY");
			fillInSimSummaryElement(simCase, outFormatter, simSummaryElement);
			final Element allEvalElement =
					outFormatter.newChild(resultsElement, "ALLEVAL");
			fillInSortieElement(simCase, outFormatter, allEvalElement,
					/* sortie= */null, /* sortieId= */null);
			final int nPttrnVbls = plannerModel.getNPttrnVbls();
			for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
				final PvValue pvValue = _plus.getPvValue(grandOrd);
				if (pvValue == null) {
					continue;
				}
				final String pvId = pvValue.getPv().getId();
				final Sortie sortie = pvValue.getSortie();
				final Element sortieElement =
						outFormatter.newChild(resultsElement, "SORTIE");
				fillInSortieElement(simCase, outFormatter, sortieElement, sortie,
						/* sortieId= */pvId);
			}
			try (final FileOutputStream fos =
					new FileOutputStream(_outputFileName)) {
				outFormatter.dumpWithTimeComment(resultsElement, fos);
			} catch (final Exception e1) {
				MainRunner.HandleFatal(simCase, new RuntimeException(e1));
			}
			/** Do SimLog. */
			final ParticlesManager particlesManager =
					planner.getParticlesManager();
			final LsFormatter logFormatter = new LsFormatter();
			final Element logElement = logFormatter.newElement("SIMLOG");
			final Date currentDate = new Date(System.currentTimeMillis());
			final SimpleDateFormat simpleDateFormat =
					new SimpleDateFormat("yyyy MMM dd  hh.mm.ss a.z");
			final String timeString = simpleDateFormat.format(currentDate);
			logElement.setAttribute("timeCreated", timeString);
			logElement.setAttribute("saropsVersionName",
					SimGlobalStrings.getStaticVersionName());
			logElement.setAttribute("javaVersionName", StringUtilities
					.getSystemProperty("java.version", /* useSpaceProxy= */false));
			final Element timeElement = logFormatter.newChild(logElement, "TIME");
			timeElement.setAttribute("dtg",
					TimeUtilities.formatTime(_criticalRefSecs, false));
			final Element posElement = logFormatter.newChild(timeElement, "POS");
			final RawSums grandRawSums =
					_rawSumsMap.get(SummarySumsBag.getKey(null, null));
			final double totalPFail =
					grandRawSums._sumOfPriorTimesOldPFailTimesNewPFail /
							grandRawSums._sumOfPriorTimesOldPFail;
			final double totalPos = 1d - totalPFail;
			ModelWriter.addDoubleAttribute(simCase, posElement, "totalPOS",
					totalPos);
			final double cumPFail =
					grandRawSums._sumOfPriorTimesOldPFailTimesNewPFail /
							grandRawSums._sumOfPriors;
			final double cumPos = 1d - cumPFail;
			ModelWriter.addDoubleAttribute(simCase, posElement, "cumPOS", cumPos);
			for (final int objectType : particlesManager.getAllObjectTypeIds()) {
				final Element objectTypeElement =
						logFormatter.newChild(posElement, "SEARCH_OBJECT_TYPE");
				objectTypeElement.setAttribute("id",
						String.format("%d", objectType));
				final RawSums rawSums =
						_rawSumsMap.get(SummarySumsBag.getKey(null, objectType));
				/** Now the non-z values. */
				final double initialProbability =
						rawSums._sumOfPriors / grandRawSums._sumOfPriors;
				objectTypeElement.setAttribute("initialProbability",
						LsFormatter.StandardFormat(initialProbability));
				if (initialProbability > 0d) {
					final double cumConditionalPFail =
							rawSums._sumOfPriorTimesOldPFailTimesNewPFail /
									rawSums._sumOfPriors;
					final double cumConditionalPos = 1d - cumConditionalPFail;
					objectTypeElement.setAttribute("cumConditionalPOS",
							LsFormatter.StandardFormat(cumConditionalPos));
					final double cumJointPos = cumConditionalPos * initialProbability;
					objectTypeElement.setAttribute("cumJointPOS",
							LsFormatter.StandardFormat(cumJointPos));
					/**
					 * We define "remainingProbability" to mean
					 * "veryInitialProbability - cumJointPos."
					 */
					final double remainingProbability =
							initialProbability - cumJointPos;
					objectTypeElement.setAttribute("remainingProbability",
							LsFormatter.StandardFormat(remainingProbability));
				} else {
					objectTypeElement.setAttribute("cumConditionalPOS", "0.0");
					objectTypeElement.setAttribute("cumJointPOS", "0.0");
					objectTypeElement.setAttribute("remainingProbability", "0.0");
				}
				/** Now the counts. */
				objectTypeElement.setAttribute("numberInDistressAndLanded",
						String.format("%d", rawSums._nDistressAndLanded));
				objectTypeElement.setAttribute("numberInDistressAndNotLanded",
						String.format("%d", rawSums._nDistressAndAdrift));
				/** Do the z values. */
				final double zInitialProbability =
						rawSums._sumOfPriorTimesOldPFail /
								grandRawSums._sumOfPriorTimesOldPFail;
				objectTypeElement.setAttribute("zInitialProbability",
						LsFormatter.StandardFormat(zInitialProbability));
				if (zInitialProbability > 0d) {
					final double zConditionalPFail =
							rawSums._sumOfPriorTimesOldPFailTimesNewPFail /
									rawSums._sumOfPriorTimesOldPFail;
					final double zConditionalPos = 1d - zConditionalPFail;
					objectTypeElement.setAttribute("zConditionalPOS",
							LsFormatter.StandardFormat(zConditionalPos));
					final double zJointPos = zConditionalPos * zInitialProbability;
					objectTypeElement.setAttribute("zJointPOS",
							LsFormatter.StandardFormat(zJointPos));
				} else {
					objectTypeElement.setAttribute("zConditionalPOS", "0.0");
					objectTypeElement.setAttribute("zJointPOS", "0.0");
				}
			}
			final Model model = planner.getSimModel();
			final String logFileName =
					model.getParticlesFilePathCore() + "LogEval.xml";
			try (final FileOutputStream logStream =
					new FileOutputStream(logFileName)) {
				logFormatter.dumpWithTimeComment(logElement, logStream);
			} catch (final Exception e) {
				MainRunner.HandleFatal(simCase, new RuntimeException(e));
			}
		}
	}

	public static class RawSums {
		public double _sumOfPriors;
		public double _sumOfPriorTimesOldPFail;
		public double _sumOfPriorTimesOldPFailTimesNewPFail;
		public int _nDistressAndLanded;
		public int _nDistressAndAdrift;

		public RawSums() {
			_sumOfPriors = _sumOfPriorTimesOldPFail =
					_sumOfPriorTimesOldPFailTimesNewPFail = 0d;
			_nDistressAndLanded = _nDistressAndAdrift = 0;
		}
	}

	private Map<String, RawSums> getRawSumsMap(
			final SimCaseManager.SimCase simCase,
			final ParticlesFile particlesFile) {
		final Planner planner = _plus.getPlanner();
		final ParticlesManager particlesManager = planner.getParticlesManager();
		final PosFunction posFunction =
				planner.getPosFunctionForFinalReports_All();
		final ParticleIndexes[] prtclIndxsS = posFunction.getParticleIndexesS();
		final int[] objectTypes = particlesManager.getAllObjectTypeIds();
		final int nParticles = prtclIndxsS.length;
		final long[] refSecsS = particlesFile.getRefSecsS();
		final double[] origPriors = new double[nParticles];
		final double[] cumOldPFails = new double[nParticles];
		final boolean[] inDistressAndLanded = new boolean[nParticles];
		final boolean[] inDistressAndAdrift = new boolean[nParticles];
		final int[] objectTypesArray = new int[nParticles];
		final long refSecs0 = refSecsS[0];
		/**
		 * To get the cumPFail from the simulator, we can just look at the
		 * cumPFail at the end of the simulation.
		 */
		final int nRefSecsS = refSecsS.length;
		final long lastRefSecs = refSecsS[nRefSecsS - 1];
		for (int i = 0; i < nParticles; ++i) {
			final ParticleIndexes prtclIndxs = prtclIndxsS[i];
			origPriors[i] = particlesFile.getProbability(refSecs0, prtclIndxs);
			cumOldPFails[i] = particlesFile.getCumPFail(lastRefSecs, prtclIndxs);
			if (particlesManager.getInDistress(_criticalRefSecs, prtclIndxs)) {
				inDistressAndLanded[i] = particlesManager
						.getInDistressAndLanded(_criticalRefSecs, prtclIndxs);
				inDistressAndAdrift[i] = !inDistressAndLanded[i];
			} else {
				inDistressAndLanded[i] = inDistressAndAdrift[i] = false;
			}
			objectTypesArray[i] =
					particlesManager.getObjectType(_criticalRefSecs, prtclIndxs);
		}
		final PFailsCache pFailsCache = planner.getPFailsCache();
		final PlannerModel plannerModel = planner.getPlannerModel();
		final int nPttrnVbls = plannerModel.getNPttrnVbls();
		final DetectValues[][] detectValuesArrays =
				new DetectValues[nPttrnVbls][];
		final int startingN = 0;
		final PosFunction.EvalType evalType = posFunction._evalType;
		final boolean forOptnOnly = evalType._useViz2;
		final DetectValues.PFailType pFailType = evalType._pFailType;
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PvValue pvValue = _plus.getPvValue(grandOrd);
			if (pvValue == null) {
				detectValuesArrays[grandOrd] = null;
			} else {
				detectValuesArrays[grandOrd] =
						pFailsCache.getDetectValuesArray(planner, forOptnOnly,
								pFailType, prtclIndxsS, startingN, nParticles, pvValue);
			}
		}
		final TreeMap<String, RawSums> rawSumsMap = new TreeMap<>();
		/** iSortie == nPttrnVbls corresponds to "all sorties." */
		for (int grandOrd = 0; grandOrd <= nPttrnVbls; ++grandOrd) {
			final PvValue pvValue =
					grandOrd < nPttrnVbls ? _plus.getPvValue(grandOrd) : null;
			if (grandOrd < nPttrnVbls && pvValue == null) {
				continue;
			}
			final Sortie sortie =
					grandOrd < nPttrnVbls ? pvValue.getSortie() : null;
			final String sortieId;
			if (grandOrd < nPttrnVbls) {
				if (sortie == null) {
					continue;
				}
				final PatternVariable pv = pvValue.getPv();
				sortieId = pv.getId();
			} else {
				sortieId = null;
			}
			final RawSums rawSums0 = new RawSums();
			rawSumsMap.put(SummarySumsBag.getKey(sortieId, null), rawSums0);
			for (final int objectType : objectTypes) {
				rawSumsMap.put(SummarySumsBag.getKey(sortieId, objectType),
						new RawSums());
			}
			for (int i = 0; i < nParticles; ++i) {
				final double originalPrior = origPriors[i];
				final double oldPFail = cumOldPFails[i];
				double newPFail;
				if (sortie != null) {
					final DetectValues detectValues = detectValuesArrays[grandOrd][i];
					newPFail =
							detectValues.getPFail(posFunction._evalType._pFailType);
				} else {
					newPFail = 1d;
					for (int grandOrdX = 0; grandOrdX < nPttrnVbls; ++grandOrdX) {
						final DetectValues[] detectValuesArray =
								detectValuesArrays[grandOrdX];
						if (detectValuesArray != null) {
							newPFail *= detectValuesArray[i]
									.getPFail(posFunction._evalType._pFailType);
						}
					}
				}
				rawSums0._sumOfPriors += originalPrior;
				rawSums0._sumOfPriorTimesOldPFail += originalPrior * oldPFail;
				rawSums0._sumOfPriorTimesOldPFailTimesNewPFail +=
						originalPrior * oldPFail * newPFail;
				rawSums0._nDistressAndAdrift += inDistressAndAdrift[i] ? 1 : 0;
				rawSums0._nDistressAndLanded += inDistressAndLanded[i] ? 1 : 0;
				/** A particle's ObjectType is what it is at endTimeSecs. */
				final int objectType = objectTypesArray[i];
				final RawSums rawSums1 =
						rawSumsMap.get(SummarySumsBag.getKey(sortieId, objectType));
				if (rawSums1 != null) {
					rawSums1._sumOfPriors += originalPrior;
					rawSums1._sumOfPriorTimesOldPFail += originalPrior * oldPFail;
					rawSums1._sumOfPriorTimesOldPFailTimesNewPFail +=
							originalPrior * oldPFail * newPFail;
					rawSums1._nDistressAndAdrift += inDistressAndAdrift[i] ? 1 : 0;
					rawSums1._nDistressAndLanded += inDistressAndLanded[i] ? 1 : 0;
				}
			}
		}
		return rawSumsMap;
	}

	public static void doAnEval(final PvValueArrayPlus plus) {
		final Eval eval = new Eval(plus);
		if (eval._rawSumsMap != null) {
			eval.reportResults();
		}
	}
}
