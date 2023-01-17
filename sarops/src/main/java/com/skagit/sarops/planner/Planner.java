package com.skagit.sarops.planner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.skagit.sarops.MainSaropsObject;
import com.skagit.sarops.AbstractOutFilesManager;
import com.skagit.sarops.computePFail.DetectValues;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.Sortie;
import com.skagit.sarops.model.io.ModelWriter;
import com.skagit.sarops.planner.plannerModel.EchoAndNextPlanWriter;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.planner.plannerModel.pvSeq.PvSeq;
import com.skagit.sarops.planner.posFunction.PosFunction;
import com.skagit.sarops.planner.posFunction.PosFunction.EvalType;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.planner.posFunction.pFailsCache.PFailsCache;
import com.skagit.sarops.planner.posFunction.pvValueArrayPlus.PvValueArrayPlus;
import com.skagit.sarops.planner.solver.DeconflictEvent;
import com.skagit.sarops.planner.solver.JumpEvent;
import com.skagit.sarops.planner.solver.Solver;
import com.skagit.sarops.planner.solver.SolversManager;
import com.skagit.sarops.planner.solver.TheoreticalPos;
import com.skagit.sarops.planner.solver.pqSolver.PqSolver;
import com.skagit.sarops.planner.solver.pqSolver.deconflicter.Deconflicter;
import com.skagit.sarops.planner.solver.pqSolver.pvPlacer.PvPlacer;
import com.skagit.sarops.planner.summarySums.SummarySums;
import com.skagit.sarops.planner.summarySums.SummarySumsBag;
import com.skagit.sarops.planner.writingUtils.PlannerReportsData;
import com.skagit.sarops.planner.writingUtils.PlannerReportsDataUtils;
import com.skagit.sarops.planner.writingUtils.PlannerSheets;
import com.skagit.sarops.simCaseManager.MainRunner;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.simCaseManager.SimCaseManager.SimCase;
import com.skagit.sarops.studyController.AbstractStudyRunner;
import com.skagit.sarops.tracker.CoverageToPodCurve;
import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.sarops.tracker.ParticlesFile;
import com.skagit.sarops.tracker.lrcSet.LateralRangeCurve;
import com.skagit.sarops.tracker.lrcSet.Logit;
import com.skagit.sarops.tracker.lrcSet.LrcSet;
import com.skagit.sarops.util.SimGlobalStrings;
import com.skagit.sarops.util.patternUtils.MyStyle;
import com.skagit.sarops.util.patternUtils.SphericalTimedSegs;
import com.skagit.util.LsFormatter;
import com.skagit.util.StringUtilities;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.randomx.Randomx;

public class Planner implements MainSaropsObject {

	final public static PosFunction.EvalType _EvalTypeForTracker =
			PosFunction.EvalType.COMPLETE_FT_ALL;

	final private static int _NominalSecsPerCoreBodyChunk = 4;
	final private static int _MaxNCoreBodyChunks = 20;
	final private static int _MaxNPolishingPasses = 5;
	final private static int _NForOnePv = 32;
	final public static int _PFailsCacheSize = 100;
	final public static String[] _CornerNamesIf4 = { //
			"LeftLow", "LeftHigh", "RightHigh", "RightLow" };

	/** Data of a Planner. */
	final private PlannerModel _plannerModel;
	final private ParticlesManager _particlesManager;
	final private PFailsCache _pFailsCache;
	final private PosFunction _growingSampleNftSelected;
	final private PosFunction _growingSampleFtSelected;
	final private PosFunction _completeFtSelected;
	final private PosFunction _completeFtAll;
	final private PosFunction _completeFtLandedAdrift;
	private SolversManager _solversManager;
	private boolean _plannerWasToldToSit = false;
	final private SimCase _simCase;
	final private String[] _introChunks;
	final private String[] _bodyChunks;
	final private String[] _shutDownChunks;
	final private String[] _wrapUpChunks;
	final private String[] _evalChunks;
	private long _entryTimeInMillis = -1L;

	/** Just for Real Planner Problems. */
	private Planner(final SimCaseManager.SimCase simCase,
			final ParticlesFile particlesFile, final PlannerModel plannerModel,
			final String[] introChunks, final String[] bodyChunks,
			final String[] shutDownChunks, final String[] wrapUpChunks,
			final String[] evalChunks) {
		_displayOnlySimModel = null;
		_simCase = simCase;
		_introChunks = introChunks;
		_bodyChunks = bodyChunks;
		_shutDownChunks = shutDownChunks;
		_wrapUpChunks = wrapUpChunks;
		_evalChunks = evalChunks;
		if (simCase != null) {
			simCase.setMainSaropsObject(this);
		}
		allowGoing();
		boolean amRealPlannerProblem =
				plannerModel != null && plannerModel.getNPttrnVbls() > 0;
		final HashSet<Integer> viz1ObjectTypeIds = plannerModel == null ?
				new HashSet<>() : plannerModel.getViz1ObjectTypeIds();
		final HashSet<Integer> viz2ObjectTypeIds = plannerModel == null ?
				new HashSet<>() : plannerModel.getViz2ObjectTypeIds();
		amRealPlannerProblem = particlesFile != null &&
				viz2ObjectTypeIds != null && viz2ObjectTypeIds.size() > 0;
		_solversManager = null;
		if (!amRealPlannerProblem) {
			_plannerModel = null;
			_amRealPlannerProblem = false;
			_pFailsCache = null;
			_growingSampleNftSelected = null;
			_growingSampleFtSelected = null;
			_completeFtSelected = null;
			_completeFtAll = null;
			_completeFtLandedAdrift = null;
			_particlesManager = null;
			_firstPvPlacers = null;
			_firstDeconflicter = null;
			simCase.setMainSaropsObject(null);
			return;
		}
		_plannerModel = plannerModel;
		/**
		 * Here we are interested only in those times for which there is at
		 * least one Pv.
		 */
		final long firstRefSecsOfInterest = _plannerModel.getMinPvRefSecs();
		final long lastRefSecsOfInterest = _plannerModel.getMaxPvRefSecs();
		final ParticlesManager.Specs specs =
				new ParticlesManager.Specs(_plannerModel.includeLanded(),
						_plannerModel.includeAdrift(), firstRefSecsOfInterest,
						lastRefSecsOfInterest, viz1ObjectTypeIds, viz2ObjectTypeIds);
		final ParticlesManager particlesManager = ParticlesManager
				.createParticlesManager(simCase, particlesFile, specs);
		final int[] selectedObjectTypes = particlesManager == null ? null :
				particlesManager.getSelectedObjectTypeIds();
		if (selectedObjectTypes == null || selectedObjectTypes.length == 0) {
			_amRealPlannerProblem = false;
			_pFailsCache = null;
			_growingSampleNftSelected = null;
			_growingSampleFtSelected = null;
			_completeFtSelected = null;
			_completeFtAll = null;
			_completeFtLandedAdrift = null;
			_particlesManager = null;
			_firstPvPlacers = null;
			simCase.setMainSaropsObject(null);
			_firstDeconflicter = null;
			return;
		}
		_particlesManager = particlesManager;

		_amRealPlannerProblem = true;

		/**
		 * Create the PFailsCache before setting the Optn particles and priors
		 * so we can use the above computed frozens to compute the Optn
		 * Particles and Priors.
		 */
		_pFailsCache =
				new PFailsCache(_particlesManager, simCase, _PFailsCacheSize);

		/** Set the Optn particles and priors. */
		_particlesManager.setOptnPrtclsAndPriors(this);

		/** Build the Pos functions before creating the solvers. */
		_completeFtAll = new PosFunction(this, _EvalTypeForTracker);
		_completeFtSelected =
				new PosFunction(this, PosFunction.EvalType.COMPLETE_FT_SLCTD);
		_completeFtLandedAdrift = new PosFunction(this,
				PosFunction.EvalType.COMPLETE_FT_LANDED_ADRIFT);

		final int nAllSlctd = _completeFtSelected.getParticleIndexesS().length;
		_growingSampleFtSelected =
				new PosFunction(this, PosFunction.EvalType.GROWING_SAMPLE_FT);
		if (nAllSlctd > 1000) {
			_growingSampleNftSelected =
					new PosFunction(this, PosFunction.EvalType.GROWING_SAMPLE_NFT);
		} else {
			_growingSampleNftSelected = _growingSampleFtSelected;
		}
		final int nPttrnVbls = _plannerModel.getNPttrnVbls();
		_firstPvPlacers = new PvPlacer[nPttrnVbls];
		Arrays.fill(_firstPvPlacers, null);
		_firstDeconflicter = null;
	}

	/** This is strictly for running a real Planner problem. */
	public static void main(final SimCaseManager.SimCase simCase,
			final String[] args) {
		/** Boilerplate. */
		final String versionName = SimGlobalStrings.getStaticVersionName();
		SimCaseManager.out(simCase,
				String.format("Running Planner(%s), Version %s.", simCase.getName(),
						versionName));
		final long entryMs = System.currentTimeMillis();

		/** Get plannerModelFilePath. */
		int iArg = 0;
		String plannerFilePath = args.length > iArg ? args[iArg++] : null;
		plannerFilePath = StringUtilities.cleanUpFilePath(plannerFilePath);
		final File plannerModelFile = new File(plannerFilePath);
		if (!plannerModelFile.exists() || !plannerModelFile.canRead()) {
			MainRunner.HandleFatal(simCase,
					new RuntimeException("Bad Planner File"));
		}

		/** Log some interesting information. */
		final String saropsVersionName =
				SimGlobalStrings.getStaticVersionName();
		final String javaVersion = StringUtilities
				.getSystemProperty("java.version", /* useSpaceProxy= */false);
		SimCaseManager.out(simCase, "Running Planner, SaropsVersion:" +
				saropsVersionName + ", JavaVersion:" + javaVersion + ".");
		SimCaseManager.out(simCase, "Input Args:");
		for (final String arg : args) {
			SimCaseManager.out(simCase, arg);
		}
		SimCaseManager.LogMemory(simCase);

		final SimCaseManager simCaseManager = simCase.getSimCaseManager();

		/**
		 * <pre>
		 * We could put everything into the ctor of a Planner,
		 * but we break it up to make it more modular.
		 * However, we still must follow this sequence:
		 * 1. Read a ParticlesFile, which is where we get the SimModel.
		 * 2. Give the SimModel to the PlannerModel ctor.
		 * 3. Give the ParticlesFile and PlannerModel to the Planner ctor.
		 * To do this, we have to extract the ParticlesFilePath
		 * from the PlannerFilePath, which we will re-do within the
		 * PlannerModel ctor.  Hence, we subroutinize this.
		 * </pre>
		 */
		/** Get particlesFilePath and particlesFile. */
		final String particlesFilePath =
				AbstractOutFilesManager.GetParticlesFilePathFromXml(plannerFilePath);
		if (particlesFilePath == null) {
			MainRunner.HandleFatal(simCase,
					new RuntimeException("Could not find ParticlesFilePath"));
		}
		final ParticlesFile particlesFile =
				simCaseManager.getParticlesFile(simCase, particlesFilePath);
		final Model simModel = particlesFile.getModel();

		/**
		 * Get plannerModel; we need to do this to get plannerTimeSecs, which we
		 * need for setting up secsPerCoreBodyChunk.
		 */
		final PlannerModel plannerModel = new PlannerModel(simCase, simModel,
				plannerFilePath, /* particlesFilePath= */null);

		/** Set up progressSteps. */
		final String[] introChunks =
				new String[] { "ReadInPlannerModel", "CreatePlanner",
						"ResetSolversManager", "ClearShp", "StartIterating" };
		final int nIntroChunks = introChunks.length;
		final int plannerTimeSecs = plannerModel.getPlannerTimeSecs();
		final int secsPerCoreBodyChunk;
		if (_NominalSecsPerCoreBodyChunk *
				_MaxNCoreBodyChunks < plannerTimeSecs) {
			secsPerCoreBodyChunk = plannerTimeSecs / _MaxNCoreBodyChunks + 1;
		} else {
			secsPerCoreBodyChunk = _NominalSecsPerCoreBodyChunk;
		}

		/** Set bodyChunkList. */
		final ArrayList<String> bodyChunkList = new ArrayList<>();
		final int nCoreBodyChunks =
				(plannerTimeSecs + secsPerCoreBodyChunk - 1) / secsPerCoreBodyChunk;
		for (int k = 0; k < nCoreBodyChunks; ++k) {
			bodyChunkList.add(String.format("%d of %d seconds gone.",
					secsPerCoreBodyChunk * (k + 1), plannerTimeSecs));
		}
		bodyChunkList.add("Polishing");
		final String[] bodyChunks =
				bodyChunkList.toArray(new String[bodyChunkList.size()]);
		final int nBodyChunks = bodyChunks.length;
		final String[] shutDownChunks = new String[] { "ShutDown" };
		final String[] wrapUpChunks = new String[] { //
				"Round and ClearOvl", "CheckForImprovement", "ComputeSummary1",
				"ComputeSummary2", "ComputeSummary3", "ComputeSummary4" };
		final String[] evalChunks = new String[] { "WriteEvalFile", "DoEval" };
		final String[][] sections = new String[][] { introChunks, bodyChunks,
				shutDownChunks, wrapUpChunks, evalChunks };
		final String[] sectionNames =
				new String[] { "Intro", "Body", "ShutDown", "WrapUp", "Eval" };
		final boolean[] criticalSections =
				new boolean[] { true, true, true, true, true };
		int nProgressSteps = 0;
		for (final String[] section : sections) {
			nProgressSteps += section.length;
		}
		final File progressDirectory = null;
		simCase.setChunkReporter(progressDirectory, nProgressSteps,
				sectionNames, criticalSections, sections);
		/** We've already read in the PlannerModel. */
		final int readInPlannerModelI = 1;
		simCase.reportChunksDone(readInPlannerModelI);

		/**
		 * Create planner. This will set simCase's MainSaropsObject to planner,
		 * and plannerModel will get at planner that way.
		 */
		final Planner planner =
				new Planner(simCase, particlesFile, plannerModel, introChunks,
						bodyChunks, shutDownChunks, wrapUpChunks, evalChunks);
		if (!planner._amRealPlannerProblem) {
			MainRunner.HandleFatal(simCase,
					new RuntimeException("Bad PlannerProblem"));
		}
		planner.setEntryMs(entryMs);
		final int createPlannerI = readInPlannerModelI + 1;
		simCase.reportChunksDone(createPlannerI);

		/** Set the solvers. */
		planner.resetSolversManager();
		final int resetSolversManagerI = createPlannerI + 1;
		simCase.reportChunksDone(resetSolversManagerI);
		final PqSolver pqSolver = planner._solversManager.getPqSolver();
		if (pqSolver == null) {
			final String errorString = String.format(
					"\nCould not create a PqSolver.  " + "Bad Planner Problem?%s",
					StringUtilities.getStackTraceString(new Exception()));
			planner.shutDownSolversAndRunOutChunks(errorString);
			MainRunner.HandleFatal(simCase, new RuntimeException(errorString));
		}

		/** Run a study if need be. */
		if (AbstractStudyRunner.RunStudy(simCase)) {
			/** The "runStudy" completed the planner run. */
			return;
		}

		/**
		 * runStudy did not complete the Planner Run. Compute the initial, and
		 * clear the constraints.
		 */
		final boolean isEvalRun = plannerModel.isEvalRun();
		final long allowedMs = Math.max(1, plannerTimeSecs) * 1000;
		if (!simCase.getKeepGoing()) {
			planner.shutDownSolversAndRunOutChunks(String.format(
					"Didn't start, and we were supposed to iterate for %d secs.",
					allowedMs / 1000));
			return;
		}
		final int clearTsvI = resetSolversManagerI + 1;
		simCase.reportChunksDone(clearTsvI);

		/** Loop until we get our full complement of time. */
		final long[] coreBodyMsS = new long[1 + nCoreBodyChunks];
		coreBodyMsS[0] = pqSolver.getStartTimeMs();
		for (int k = 1; k < nCoreBodyChunks; ++k) {
			coreBodyMsS[k] = coreBodyMsS[0] + secsPerCoreBodyChunk * 1000 * k;
		}
		final int secsToSolve = (int) (allowedMs / 1000);
		/**
		 * Read in termination criteria. If we look back lookBackIntervalSecs
		 * seconds, and pos hasn't improved by at least a ratio of
		 * lookBackMinRatio since then, quit.
		 */
		final int maxNJumpsWithNoImprovement = (int) Math.round(simCase
				.getSimPropertyDouble("PLAN.maxNJumpsWithNoImprovement", 10000));
		final long lookBackIntervalSecs = Math.round(simCase
				.getSimPropertyDouble("PLAN.lookBackIntervalInSeconds", 10000));
		final double lookBackMinRatio =
				simCase.getSimPropertyDouble("PLAN.lookBackMinimumRatio", 1d);

		/** Start iterating. */
		pqSolver.startIterating(secsToSolve);
		final int startIteratingI = clearTsvI + 1;
		simCase.reportChunksDone(startIteratingI);
		boolean onPolishingPass = false;
		int iPolishPass = 0;
		int iPass = 0;
		for (;; ++iPass) {
			final PvValueArrayPlus bestPlus = pqSolver.getBestForCurrentActive();
			if (onPolishingPass) {
				/** If this is the first polishing pass, demand feasible. */
				final boolean demandFeasible = iPolishPass == 0;
				final boolean mustContinue =
						bestPlus == null || (demandFeasible && !bestPlus.isFeasible());
				if (!mustContinue || iPolishPass > _MaxNPolishingPasses) {
					SimCaseManager.out(simCase,
							"PlannerMain: Done with Polishing Passes.");
					break;
				}
				++iPolishPass;
			}
			if (!simCase.getKeepGoing()) {
				planner.shutDownSolversAndRunOutChunks("Shut Down(3)!");
				simCase._interrupted = true;
				return;
			}
			try {
				synchronized (pqSolver) {
					pqSolver.wait(secsPerCoreBodyChunk * 1000);
					if (!onPolishingPass && iPass <= nBodyChunks) {
						simCase.reportChunksDone(nIntroChunks + iPass + 1);
					}
					final int nJumpsSinceLastImprovement =
							pqSolver.getNJumpsSinceLastImprovement();
					final long msRemaining = pqSolver.getMsRemaining();
					final double lookBackRatio =
							pqSolver.getLookBackRatio(lookBackIntervalSecs);
					final String s2;
					if (lookBackRatio == Solver._LookBackRatioForDoNotStop) {
						s2 = String.format(
								"\nWaited: iPass[%d] nJumps[%d] maxNJumps[%d] " +
										"secsRemaining[%d] Guarantee No Look Back.",
								iPass, nJumpsSinceLastImprovement,
								maxNJumpsWithNoImprovement, msRemaining / 1000);
					} else {
						s2 = String.format(
								"\nWaited: iPass[%d] nJumps[%d] maxNJumps[%d] " +
										"secsRemaining[%d] lookBackRatio[%f] lookBackMinRatio[%f].",
								iPass, nJumpsSinceLastImprovement,
								maxNJumpsWithNoImprovement, msRemaining / 1000,
								lookBackRatio, lookBackMinRatio);
					}
					SimCaseManager.out(simCase, s2);
					/**
					 * Check to see if we flip over to polishing passes; we need at
					 * least SOME solution before flipping over to polishing.
					 */
					if (!onPolishingPass && bestPlus != null) {
						final boolean stopBecauseOfLookBack =
								lookBackRatio != Solver._LookBackRatioForDoNotStop &&
										lookBackRatio < lookBackMinRatio;
						if (nJumpsSinceLastImprovement >= maxNJumpsWithNoImprovement ||
								msRemaining <= 0 || stopBecauseOfLookBack ||
								!pqSolver.getKeepRefining()) {
							onPolishingPass = true;
							final String s3 = "PlannerMain: Got through Phase 1.";
							SimCaseManager.out(simCase, s3);
							continue;
						}
					}
				}
			} catch (final InterruptedException e) {
				/**
				 * Should only happen if pqSolver was notified; that would interrupt
				 * the pqSolver.wait(). SolversManager.shutDownSolvers() does this.
				 */
				SimCaseManager.err(simCase,
						String.format("\nInterrupted: iPass[%d].", iPass));
				planner.shutDownSolversAndRunOutChunks("Shut Down(4)!");
				simCase._interrupted = true;
				return;
			}
		}

		SimCaseManager.out(simCase,
				"PlannerMain: Successful completion of Planner. Still must write out xml.");
		planner.shutDownSolversNoRunOutChunks("Done iterating.");
		simCase.reportChunksDone(nIntroChunks + nBodyChunks);

		/** Write things out. */
		final int nShutDownChunks = planner._shutDownChunks.length;
		simCase.reportChunksDone(nIntroChunks + nBodyChunks + nShutDownChunks);
		/** Write out the answer from pqSolver best. */
		final PvValueArrayPlus bestPlus = pqSolver.getBestForCurrentActive();
		final String startDateString = pqSolver.getStartDateString();
		final String bestDateString = pqSolver.getBestDateString();

		simCase
				.reportChunksDone(nIntroChunks + nBodyChunks + nShutDownChunks + 1);
		final double ttlCtV = bestPlus
				.getForReportsTtlCtV(/* countTheOnesThatMayOverlap= */false);

		/** Always write out the xml. */
		planner.writeXmlFiles(bestPlus, startDateString, bestDateString,
				ttlCtV);
		final int nWrapUpChunks = wrapUpChunks.length;
		simCase.reportChunksDone(
				nIntroChunks + nBodyChunks + nShutDownChunks + nWrapUpChunks);
		/** Only do an eval if ... */
		if (isEvalRun) {
			Eval.doAnEval(bestPlus);
			SimCaseManager.out(simCase, "PlannerMain: Completed the Eval.");
		}
		final int nEvalChunks = planner._evalChunks.length;
		simCase.reportChunksDone(nIntroChunks + nBodyChunks + nShutDownChunks +
				nWrapUpChunks + nEvalChunks);
		planner.shutDownSolversAndRunOutChunks("Done iterating.");
		planner.writeTimesFile(plannerFilePath);
	}

	private void allowGoing() {
		if (_simCase != null) {
			_simCase.allowGoing();
		}
	}

	public boolean wasToldToSit() {
		return _plannerWasToldToSit;
	}

	public void tellPlannerToSit() {
		_plannerWasToldToSit = true;
	}

	public Model getSimModel() {
		if (_particlesManager == null) {
			return _displayOnlySimModel;
		}
		final Model particlesManagerSimModel =
				_particlesManager.getParticlesFile().getModel();
		if (!_amRealPlannerProblem) {
			return particlesManagerSimModel;
		}
		assert _plannerModel.getSimModel() == particlesManagerSimModel;
		return _plannerModel.getSimModel();
	}

	/**
	 * Do this one to prepare to write xml for a successful completion of a
	 * Planner run.
	 */
	public void shutDownSolversNoRunOutChunks(final String shutDownReason) {
		/** We are done. The SolversManager no longer has a relevant Solver. */
		if (_solversManager != null) {
			_solversManager.shutDownSolvers();
		}
		SimCaseManager.out(_simCase,
				String.format("PlannerMain: Shut down; %s.", shutDownReason));
	}

	/** Do this after writing xml or a non-planner problem. */
	public void shutDownSolversAndRunOutChunks(final String shutDownReason) {
		shutDownSolversNoRunOutChunks(shutDownReason);
		_simCase.runOutChunks();
		if (_amRealPlannerProblem) {
			_solversManager.unloadSolvers();
		}
	}

	public ParticlesManager getParticlesManager() {
		return _particlesManager;
	}

	public PlannerModel getPlannerModel() {
		return _plannerModel;
	}

	public boolean amRealPlannerProblem() {
		return _amRealPlannerProblem;
	}

	/** Manipulate arrays of PvValues. */
	public boolean isValidAsPerCurrentActive(final PvValueArrayPlus plus) {
		if (_plannerModel == null) {
			return false;
		}
		final int nPttrnVbls = _plannerModel.getNPttrnVbls();
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PvValue pvValue =
					plus == null ? null : plus.getPvValue(grandOrd);
			if (pvValue == null) {
				final PatternVariable pv = _plannerModel.grandOrdToPv(grandOrd);
				if (pv.getPermanentFrozenPvValue() == null) {
					/**
					 * Null value for a PV that's not permanently frozen. Invalid.
					 */
					return false;
				}
			}
		}
		return true;
	}

	public PFailsCache getPFailsCache() {
		return _pFailsCache;
	}

	/** Creating small sample PosFunctions. */
	public PosFunction createSmallSampleFunctionForDeconflicter(
			final PvValue[] knowns, final int nFloaters) {
		final PosFunction posFunction = createSmallSamplePosFunction(knowns,
				EvalType.FXD_SAMPLE_NFT, nFloaters,
				new Randomx(_plannerModel.getLatestSeed(), /* nToAdvance= */2));
		return posFunction;
	}

	public PosFunction[] createPosFunctionPairForOptnPass(
			final int nFloaters) {
		/**
		 * To ensure a different set of particles than the one used for
		 * deconflicting, use a different random number generator.
		 */
		final PosFunction smallSampleFtPosFunction =
				createSmallSamplePosFunction(/* knowns= */null,
						EvalType.GROWING_SAMPLE_FT, nFloaters,
						new Randomx(_plannerModel.getLatestSeed(), /* nToAdvance= */3));
		final ParticleIndexes[] ftPrtclIndxsS =
				smallSampleFtPosFunction.getParticleIndexesS();
		final double[] ftPriors = smallSampleFtPosFunction.getPriors();
		final PosFunction smallSampleNftPosFunction =
				new PosFunction(this, /* r= */null, ftPrtclIndxsS, ftPriors,
						/* nInSample= */-1, EvalType.GROWING_SAMPLE_NFT);
		return new PosFunction[] { smallSampleFtPosFunction,
				smallSampleNftPosFunction };
	}

	/**
	 * This is only for use during optimization. Get ParticleIndexes/Priors
	 * that reflect knowns.
	 */
	public PosFunction createSmallSamplePosFunction(final PvValue[] knowns,
			final EvalType evalType, final int nFloaters, final Randomx r) {
		/** Collect the forOptn ParticleIndexesS and priors. */
		final PosFunction prePosFunction =
				new PosFunction(_particlesManager, /* forOptnOnly= */true);
		final ParticleIndexes[] prePrtclIndxsS =
				prePosFunction.getParticleIndexesS().clone();
		final double[] postPriors = prePosFunction.getPriors().clone();
		final int nAll = postPriors.length;
		/**
		 * Adjust postPriors to reflect the knowns that are not permanently
		 * frozen.
		 */
		final int nKnown = knowns == null ? 0 : knowns.length;
		for (int k0 = 0; k0 < nKnown; ++k0) {
			final PvValue pvValue = knowns[k0];
			if (pvValue == null || pvValue.onMars()) {
				continue;
			}
			final PatternVariable pv = pvValue.getPv();
			if (!pv.isActive() || pv.getPermanentFrozenPvValue() != null) {
				continue;
			}
			/**
			 * Update as per viz2 only; this is just for optimization. But use
			 * AIFT.
			 */
			final DetectValues[] detectValuesS =
					_pFailsCache.getDetectValuesArray(this, /* forOptnOnly= */true,
							DetectValues.PFailType.AIFT, prePrtclIndxsS, 0, nAll,
							pvValue);
			for (int k1 = 0; k1 < nAll; ++k1) {
				final DetectValues detectValues = detectValuesS[k1];
				postPriors[k1] *= detectValues._aiftPFail;
			}
		}
		final int nInSample = getNParticles(nFloaters);
		/** Now we can build a PosFunction. */
		final PosFunction posFunction = new PosFunction(this, r, prePrtclIndxsS,
				postPriors, nInSample, evalType);
		return posFunction;
	}

	/** Utils for creating Small Sample PosFunctions. */
	private static int getNParticles(final int nFloaters) {
		final int nToUse = Math.max(0, nFloaters - 1);
		final int n = (int) Math.round(Math.pow(2d, nToUse));
		return n * _NForOnePv;
	}

	/** Simply retrieve existing PosFunctions. */
	public PosFunction getPosFunctionForFinalReports_All() {
		return _completeFtAll;
	}

	public PosFunction getPosFunctionForFinalReports_Slctd() {
		return _completeFtSelected;
	}

	public PosFunction getPosFunctionForLandedAdrift() {
		return _completeFtLandedAdrift;
	}

	public PosFunction getPosFunctionForTheoreticalMaxPos() {
		return _completeFtSelected;
	}

	public PosFunction getPosFunctionForInfblOptn() {
		return _growingSampleNftSelected;
	}

	public PosFunction getPosFunctionForFbleOptn() {
		return _growingSampleFtSelected;
	}

	public PosFunction getPosFunctionForStatusReport() {
		return getPosFunctionForFbleOptn();
	}

	private static PosFunction.EvalType[] _OrderForXml =
			{ PosFunction.EvalType.COMPLETE_FT_ALL,
					PosFunction.EvalType.COMPLETE_FT_SLCTD,
					PosFunction.EvalType.COMPLETE_FT_LANDED_ADRIFT,
					PosFunction.EvalType.GROWING_SAMPLE_FT };

	public void writeXmlFiles(final PvValueArrayPlus newPlus,
			final String startDateString, final String bestDateString,
			final double ttlCtV) {
		if (!_simCase.getKeepGoing()) {
			return;
		}
		final String simCaseName = _simCase.getName();
		/** We've done through the first wrapUp chunk. Announce that. */
		final int nIntroChunks = _introChunks.length;
		final int nBodyChunks = _bodyChunks.length;
		final int nShutDownChunks = _shutDownChunks.length;
		_simCase.reportChunksDone(nIntroChunks + nBodyChunks + nShutDownChunks);
		SimCaseManager.out(_simCase, "PlannerMain: Started Writing Xml");
		_pFailsCache.clear();
		final int nPttrnVbls = _plannerModel.getNPttrnVbls();
		final Model simModel = _plannerModel.getSimModel();
		final Extent modelExtent = simModel.getExtent();

		/**
		 * Set winningPlus0 to either the original one, or to the best one. We
		 * use the original one if the best one is not significantly better than
		 * the original one.
		 */
		/** Flesh out the original's values with onMars PvValues. */
		final PvValue[] arrayOfOrigs = new PvValue[nPttrnVbls];
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PatternVariable pv = _plannerModel.grandOrdToPv(grandOrd);
			final PvValue initialPvValue = pv.getInitialPvValue();
			if (initialPvValue == null || !pv.isActive()) {
				final MyStyle onMarsMyStyle = new MyStyle(modelExtent, pv);
				arrayOfOrigs[grandOrd] = new PvValue(pv, onMarsMyStyle);
			} else {
				arrayOfOrigs[grandOrd] = initialPvValue;
			}
		}
		final PvValueArrayPlus plusOfOrig =
				new PvValueArrayPlus(this, arrayOfOrigs);

		final boolean origIsFeasible = plusOfOrig.isFeasible();
		final boolean newIsFeasible = newPlus.isFeasible();

		/** For comparing, we must use the one we used when optimizing. */
		final PosFunction posFunctionForOptn;
		if (origIsFeasible || newIsFeasible) {
			posFunctionForOptn = _growingSampleFtSelected;
		} else {
			posFunctionForOptn = _growingSampleNftSelected;
		}

		SimCaseManager.out(_simCase,
				String.format("%s: PlannerMain: Computing Orig", simCaseName));
		final double posOfOrig = plusOfOrig.getPos(posFunctionForOptn);
		if (!_simCase.getKeepGoing() || Double.isNaN(posOfOrig)) {
			return;
		}
		final double ovlVOfOrig = plusOfOrig.getForReportsTtlOvlV(/* pv= */null,
				/* countTheOnesThatMayOverlap= */false);
		final double pvSeqVOfOrig =
				plusOfOrig.getForReportsTtlPvTrnstV(/* pv= */null);
		final String captionOfOrig =
				String.format("Orig (OvlV,PvSeqV: Pos)[%f,%f: %f]", //
						ovlVOfOrig, pvSeqVOfOrig, posOfOrig);
		SimCaseManager.out(_simCase, plusOfOrig.getString1(captionOfOrig));

		/** Compute Optimizer's current answer's values. */
		final double posOfNew = newPlus.getPos(posFunctionForOptn);
		if (!_simCase.getKeepGoing() || Double.isNaN(posOfNew)) {
			return;
		}
		SimCaseManager.out(_simCase,
				String.format("%s", simCaseName, "PlannerMain: Computing New."));
		SimCaseManager.out(_simCase,
				"PlannerMain: Starting to Compute New Values.");

		final boolean newWins =
				newPlus.iAmBetterForOptn(/* activeSet= */null, plusOfOrig);
		final PvValueArrayPlus winningPlus0;
		if (newWins) {
			winningPlus0 = newPlus;
		} else {
			winningPlus0 = plusOfOrig;
		}
		/** Fill in the nulls with OnMars values. */
		boolean haveNull = false;
		final PvValue[] pvValues0 = winningPlus0.getCopyOfPvValues();
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			if (pvValues0[grandOrd] == null) {
				final PatternVariable pv = _plannerModel.grandOrdToPv(grandOrd);
				final MyStyle onMarsMyStyle = new MyStyle(modelExtent, pv);
				pvValues0[grandOrd] = new PvValue(pv, onMarsMyStyle);
				haveNull = true;
			}
		}
		final PvValueArrayPlus winningPlusA =
				haveNull ? new PvValueArrayPlus(this, pvValues0) : winningPlus0;

		SimCaseManager.out(_simCase, String.format(
				"***%s:PlannerMain Done with compare, %s wins***, origPos[%f] newPos[%f]",
				simCaseName, newWins ? "NEW" : "ORIG", posOfOrig, posOfNew));
		if (!_simCase.getKeepGoing()) {
			return;
		}

		/**
		 * We could clear the cache of PvValue->double[] by using
		 * _pFailsCache2.clear(), but there doesn't seem to be a need.
		 */

		/**
		 * Make this easier to print out the reports by making the onMars values
		 * fancy.
		 */
		final PvValueArrayPlus winningPlus = winningPlusA.cloneAndFancify();

		/** Get all 4 types of SummarySumsMapPluses. */
		final SummarySumsBag.SummarySumsMapPlus[] summarySumsMapPluses =
				new SummarySumsBag.SummarySumsMapPlus[4];
		final int nPasses = summarySumsMapPluses.length;
		PosFunction oldPosFunction = null;
		for (int iPass = 0; iPass < nPasses; ++iPass) {
			if (!_simCase.getKeepGoing()) {
				return;
			}
			final PosFunction posFunction;
			if (iPass == 0) {
				posFunction = getPosFunctionForFinalReports_All();
			} else if (iPass == 1) {
				posFunction = getPosFunctionForFinalReports_Slctd();
			} else if (iPass == 2) {
				posFunction = getPosFunctionForLandedAdrift();
			} else if (iPass == 3) {
				posFunction = getPosFunctionForStatusReport();
			} else {
				continue;
			}
			final SummarySumsBag.SummarySumsMapPlus summarySumsMapPlus;
			if (posFunction == oldPosFunction) {
				summarySumsMapPlus = summarySumsMapPluses[iPass - 1];
			} else {
				final boolean allowNullReturn = false;
				summarySumsMapPlus = posFunction.computeSummarySumsMapPlus(
						winningPlus, allowNullReturn, /* forOptn= */false);
				if (summarySumsMapPlus == null) {
					return;
				}
				oldPosFunction = posFunction;
			}
			_simCase.reportChunkDone();
			summarySumsMapPluses[iPass] = summarySumsMapPlus;
		}

		/** Use these 4 for each of verbose and non-verbose. */
		final String versionName = SimGlobalStrings.getStaticVersionName();
		final String javaVersion = StringUtilities
				.getSystemProperty("java.version", /* useSpaceProxy= */false);
		for (int verboseOrNot = 0; verboseOrNot < 2; ++verboseOrNot) {
			final boolean verbose = verboseOrNot == 1;
			final LsFormatter formatter = new LsFormatter();
			final String rootTag =
					verboseOrNot == 0 ? "PLAN_RESULTS" : "VERBOSE_PLAN_RESULTS";
			final Element root = formatter.newElement(rootTag);
			final Date currentDate = new Date(System.currentTimeMillis());
			final SimpleDateFormat simpleDateFormat =
					new SimpleDateFormat("yyyy MMM dd  hh.mm.ss a.z");
			final String timeString = simpleDateFormat.format(currentDate);
			root.setAttribute("timeCreated", timeString);
			root.setAttribute("versionName", versionName);
			root.setAttribute("javaVersionName", javaVersion);
			root.setAttribute(PlannerModel._LatestSeedAttributeName,
					"" + _plannerModel.getLatestSeed());
			root.setAttribute(PlannerModel._OriginalSeedAttributeName,
					"" + _plannerModel.getOriginalSeed());
			final int nChunksDoneUponEntering = nIntroChunks + nBodyChunks + 2 +
					summarySumsMapPluses.length + (!verbose ? 0 : 3);
			addPvValuesElements(formatter, root, ovlVOfOrig, posOfOrig,
					winningPlus, newWins, posOfNew, startDateString, bestDateString,
					ttlCtV, summarySumsMapPluses, verbose, nChunksDoneUponEntering);
			if (!_simCase.getKeepGoing()) {
				return;
			}
			try {
				final String resultFilePath;
				final String nextSimPath;
				final String nextPlanPath;
				final String nextEvalPath;
				if (verbose) {
					final File f0 = _plannerModel.getEngineFilesResultFile(_simCase,
							"-verboseResult.xml");
					resultFilePath = f0.getCanonicalPath();
					final File f1 = _plannerModel.getEngineFilesResultFile(_simCase,
							"-nextSim.xml");
					final File f2 = _plannerModel.getEngineFilesResultFile(_simCase,
							"-nextPlan.xml");
					final File f3 = _plannerModel.getEngineFilesResultFile(_simCase,
							"-nextEval.xml");
					nextSimPath = f1.getCanonicalPath();
					nextPlanPath = f2.getCanonicalPath();
					nextEvalPath = f3.getCanonicalPath();
				} else {
					resultFilePath = _plannerModel.getResultFilePath();
					nextSimPath = nextPlanPath = nextEvalPath = null;
				}
				SimCaseManager.out(_simCase,
						String.format("Writing %sResult to %s.",
								verbose ? "Verbose-" : "", resultFilePath));
				try (final FileOutputStream fos =
						new FileOutputStream(resultFilePath)) {
					formatter.dumpWithTimeComment(root, fos);
				} catch (final Exception e) {
					MainRunner.HandleFatal(_simCase, new RuntimeException(e));
					return;
				}
				SimCaseManager.out(_simCase,
						String.format("Pass[%d] Wrote %sResult to %s.", verboseOrNot,
								verbose ? "Verbose-" : "Plan-", resultFilePath));
				/** Now do the nextSim, nextPlan, and nextEval files. */
				if (verbose) {
					final Model model = getSimModel();
					for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
						final PvValue pvValue = winningPlus.getPvValue(grandOrd);
						if (pvValue != null && !pvValue.onMars()) {
							final Sortie sortie = pvValue.getSortie();
							model.add(sortie);
						}
					}
					final ModelWriter modelWriter = new ModelWriter();
					final Element nextSimModelElement =
							modelWriter.createModelElement(_simCase, model);
					try (final FileOutputStream fos =
							new FileOutputStream(nextSimPath)) {
						final LsFormatter modelFormatter = modelWriter.getFormatter();
						modelFormatter.dumpWithTimeComment(nextSimModelElement, fos);
					} catch (final Exception e) {
						MainRunner.HandleFatal(_simCase, new RuntimeException(e));
						return;
					}
					/** Write out the planner models. */
					new EchoAndNextPlanWriter(_plannerModel).writeNextPlans(_simCase,
							winningPlus, nextPlanPath, nextEvalPath);
				}
			} catch (final IOException e) {
				MainRunner.HandleFatal(_simCase, new RuntimeException(e));
				return;
			}
		}

		/** Write out the special reports and the spreadsheet. */
		final PosFunction posFunctionForFinalReports =
				getPosFunctionForFinalReports_All();
		final PlannerReportsData plannerReportsDataForAll =
				new PlannerReportsData(this, posFunctionForFinalReports,
						winningPlus);
		final PlannerReportsData plannerReportsDataForOptnScore =
				new PlannerReportsData(this, posFunctionForOptn, winningPlus);
		if (!_simCase.getKeepGoing()) {
			return;
		}
		writePlannerDashboard(versionName, plannerReportsDataForAll,
				plannerReportsDataForOptnScore);
		writeExternalReport(versionName, plannerReportsDataForAll);
		writeEvalReport(versionName, plannerReportsDataForAll);

		/** Write out the excel file. */
		final int nPrtcls = plannerReportsDataForAll._allParticleDatas.length;
		final Model model = getSimModel();
		final int nForExcelDump = model.getNForExcelDump();
		if (nPrtcls <= nForExcelDump) {
			final PlannerSheets plannerSheets =
					new PlannerSheets(this, plannerReportsDataForAll);
			plannerSheets.buildDashboardSheet();
			plannerSheets.buildExternalSheet();
			plannerSheets.buildEvalSheet();
			final File f1 =
					_plannerModel.getEngineFilesResultFile(_simCase, ".xlsx");
			try {
				final File f = f1.getCanonicalFile();
				plannerSheets.closeAndWrite(f);
			} catch (final IOException e) {
			}
			/** Repeat for orig. */
			final PlannerReportsData plannerReportsDataForOrig =
					new PlannerReportsData(this, posFunctionForFinalReports,
							plusOfOrig);
			final PlannerSheets plannerSheetsForOrig =
					new PlannerSheets(this, plannerReportsDataForOrig);
			plannerSheetsForOrig.buildDashboardSheet();
			plannerSheetsForOrig.buildExternalSheet();
			plannerSheetsForOrig.buildEvalSheet();
			final File f1Orig =
					_plannerModel.getEngineFilesResultFile(_simCase, "Orig.xlsx");
			try {
				final File fForOrig = f1Orig.getCanonicalFile();
				plannerSheetsForOrig.closeAndWrite(fForOrig);
			} catch (final IOException e) {
			}
		}

		SimCaseManager.out(_simCase, "PlannerMain: Finished Writing Xmls.");
		final int nWrapUpChunks = _wrapUpChunks.length;
		_simCase.reportChunksDone(
				nIntroChunks + nBodyChunks + nShutDownChunks + nWrapUpChunks);
	}

	private void addPvValuesElements(final LsFormatter formatter,
			final Element rootElement, final double ovlVOfOrig,
			final double posOfOrig, final PvValueArrayPlus winningPlus,
			final boolean newWins, final double posOfNew,
			final String startDateString, final String bestDateString,
			final double ttlCtVOfOrig,
			final SummarySumsBag.SummarySumsMapPlus[] summarySumsMapPluses,
			final boolean doVerbose, final int nChunksDoneUponEntering) {

		final Document document = rootElement.getOwnerDocument();
		final int nEvalTypes =
				Math.min(summarySumsMapPluses.length, doVerbose ? 4 : 3);
		final int nPttrnVbls = _plannerModel.getNPttrnVbls();

		for (int iPass = 0; iPass < nEvalTypes; ++iPass) {
			if (!_simCase.getKeepGoing()) {
				return;
			}
			final SummarySumsBag.SummarySumsMapPlus summarySumsMapPlus =
					summarySumsMapPluses[iPass];
			if (summarySumsMapPlus == null) {
				continue;
			}
			final PosFunction posFunction = summarySumsMapPlus._posFunction;
			final Map<String, SummarySums> summarySumsMap =
					summarySumsMapPlus._summarySumsMap;
			final String globalKey = SummarySumsBag.getKey(null, null);
			final SummarySums grandSummarySums = summarySumsMap.get(globalKey);
			final double grandSumOfPriors = grandSummarySums._sumPriors;
			final double grandSumOfInitPriors = grandSummarySums._sumInitWts;
			final double grandSumOfPriorTimesPosFromNew =
					grandSummarySums._sumPriorXPosFromNew;
			final double grandSumOfInitPriorTimesPosFromOldAndNew =
					grandSummarySums._sumInitWtXPosFromAll;
			final double grandSumOfInitPriorTimesPosFromOld =
					grandSummarySums._sumInitWtXOldPos;
			final double grandOvlV = grandSummarySums._ovlV;
			final double grandOvl = grandSummarySums._ovl;
			final double grandPvTransitV = grandSummarySums._pvTrnstV;
			final double grandPvSeqTransitV = grandSummarySums._pvSeqTrnstV;
			/**
			 * We cannot use posFunction._evalType for the boxes tag because the
			 * (e.g.) LandedAdrift posFunction could be using the flyThroughAll
			 * PosFunction, if it's appropriate.
			 */
			final String boxesTag;
			final PosFunction.EvalType evalType = _OrderForXml[iPass];
			if (evalType == _EvalTypeForTracker) {
				boxesTag = "BOXES";
			} else if (evalType == PosFunction.EvalType.COMPLETE_FT_SLCTD) {
				boxesTag = "SELECTED_PARTICLES_ONLY";
			} else if (evalType == PosFunction.EvalType.COMPLETE_FT_LANDED_ADRIFT) {
				boxesTag = "LANDED_ADRIFT";
			} else if (evalType == PosFunction.EvalType.GROWING_SAMPLE_FT) {
				boxesTag = "SAMPLE";
			} else {
				boxesTag = null;
			}
			final Element boxesElement =
					formatter.newChild(rootElement, boxesTag);
			final double globalPos;
			if (grandSumOfPriors > 0d) {
				globalPos = grandSumOfPriorTimesPosFromNew / grandSumOfPriors;
			} else {
				globalPos = 0d;
			}

			/**
			 * <pre>
			 * We print out more for the "main" block, as opposed to
			 * "selected" or "sample."  The following could have been
			 * mainBlock =
			 *    intendedEvalType == PosFunction.EvalType.COMPLETE_FT_ALL,
			 * but we prefer to tie it to the evaluation type of the
			 * subsequent simulator.
			 * </pre>
			 */
			final boolean mainBlock = evalType == _EvalTypeForTracker;
			boxesElement.setAttribute("POS",
					LsFormatter.StandardFormat(globalPos));
			final double cumPos;
			final double oldPos;
			if (grandSumOfInitPriors > 0d) {
				cumPos =
						grandSumOfInitPriorTimesPosFromOldAndNew / grandSumOfInitPriors;
				oldPos = grandSumOfInitPriorTimesPosFromOld / grandSumOfInitPriors;
			} else {
				cumPos = oldPos = 0d;
			}
			boxesElement.setAttribute("cumPOS",
					LsFormatter.StandardFormat(cumPos));
			boxesElement.setAttribute("oldPOS",
					LsFormatter.StandardFormat(oldPos));
			final double deltaPos = cumPos - oldPos;
			boxesElement.setAttribute("deltaPOS",
					LsFormatter.StandardFormat(deltaPos));
			if (evalType == PosFunction.EvalType.COMPLETE_FT_SLCTD) {
				boxesElement.setAttribute("NewWins",
						LsFormatter.StandardFormat(newWins));
				boxesElement.setAttribute("PosOfNew",
						LsFormatter.StandardFormat(posOfNew));
				boxesElement.setAttribute("PosOfOrig",
						LsFormatter.StandardFormat(posOfOrig));
				if (posOfOrig >= 0d) {
					boxesElement.setAttribute("OvlVOfOrig",
							LsFormatter.StandardFormat(ovlVOfOrig));
				}
			}
			final double totalOvlV = grandOvlV;
			boxesElement.setAttribute("totalOvlV",
					LsFormatter.StandardFormat(totalOvlV));
			final double totalOvl = grandOvl;
			boxesElement.setAttribute("totalOvl",
					LsFormatter.StandardFormat(totalOvl));
			final double totalPvTransitV = grandPvTransitV;
			final double totalPvSeqTransitV = grandPvSeqTransitV;
			final double totalPvSeqV = totalPvTransitV + totalPvSeqTransitV;
			boxesElement.setAttribute("totalPvSeqV",
					LsFormatter.StandardFormat(totalPvSeqV));
			if (doVerbose) {
				boxesElement.setAttribute("ttlCtVOfPreRounded",
						LsFormatter.StandardFormat(ttlCtVOfOrig));
				final ParticleIndexes[] prtclIndxsS =
						posFunction.getParticleIndexesS();
				if (mainBlock) {
					boxesElement.setAttribute("theoreticalMaxPOS", LsFormatter
							.StandardFormat(new TheoreticalPos(this).getPos2()));
				}
				final int nPrtcls = prtclIndxsS == null ? 0 : prtclIndxsS.length;
				boxesElement.setAttribute("nParticles",
						LsFormatter.StandardFormat(nPrtcls));
				/** Add the "general info" text nodes. */
				final String legalTag =
						StringUtilities.getLegalXmlName(globalKey, null);
				final Element verboseElement =
						formatter.newChild(boxesElement, legalTag);
				final String entryCommentBody = "Ancillary Results for Above.";
				final Comment entryComment =
						document.createComment(entryCommentBody);
				boxesElement.insertBefore(entryComment, verboseElement);
				StringUtilities.fillInAncillaryElement(formatter, verboseElement,
						this);
				final Element posFunctionElement =
						formatter.newChild(verboseElement, "PosFunction");
				posFunctionElement.appendChild(document.createTextNode(
						posFunction.getBigEvalTypeString(_particlesManager)));
				if (posFunction._evalType._useGrowingSampleStrategy) {
					if (startDateString != null) {
						final Element startDateElement =
								formatter.newChild(verboseElement, "StartDate");
						startDateElement
								.appendChild(document.createTextNode(startDateString));
					}
					if (bestDateString != null) {
						final Element bestDateElement =
								formatter.newChild(verboseElement, "BestDate");
						bestDateElement
								.appendChild(document.createTextNode(bestDateString));
					}
				}
				final boolean buildElement = false;
				final double[] extraNumbers = null;
				final String[] extraLabels = null;
				StringUtilities.fillInAncillaryElement(grandSummarySums, formatter,
						verboseElement, buildElement, globalKey, extraNumbers,
						extraLabels);
			}
			/** Do the "key-ed" entries. */
			final int[] objectTypes = _particlesManager.getAllObjectTypeIds();
			for (final int objectType : objectTypes) {
				final Element objectTypeElement =
						formatter.newChild(boxesElement, "SEARCH_OBJECT_TYPE");
				objectTypeElement.setAttribute("id",
						LsFormatter.StandardFormat(objectType));
				final String objectTypeKey =
						SummarySumsBag.getKey(null, objectType);
				final SummarySums objectTypeSummarySums =
						summarySumsMap.get(objectTypeKey);
				final double sumOfPriors;
				final double sumOfInitPriors;
				if (objectTypeSummarySums != null) {
					sumOfPriors = objectTypeSummarySums._sumPriors;
					sumOfInitPriors = objectTypeSummarySums._sumInitWts;
				} else {
					sumOfPriors = sumOfInitPriors = 0d;
				}
				if (sumOfPriors > 0d) {
					final double objectProbability = sumOfPriors / grandSumOfPriors;
					objectTypeElement.setAttribute("initialProbability",
							LsFormatter.StandardFormat(objectProbability));
					final double conditionalPos =
							objectTypeSummarySums._sumPriorXPosFromNew / sumOfPriors;
					objectTypeElement.setAttribute("conditionalPOS",
							LsFormatter.StandardFormat(conditionalPos));
					final double jointPos = conditionalPos * objectProbability;
					objectTypeElement.setAttribute("jointPOS",
							LsFormatter.StandardFormat(jointPos));
					/** If this guy is viz2 for some Pv, he's viz2. */
					boolean viz2 = false;
					for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
						final PvValue pvValue = winningPlus.getPvValue(grandOrd);
						if (pvValue == null) {
							continue;
						}
						final PatternVariable pv = pvValue.getPv();
						if (!pv.isActive()) {
							continue;
						}
						viz2 = pv.getViz2LrcSets().keySet().contains(objectType);
						if (viz2) {
							break;
						}
						objectTypeElement.setAttribute("isActive",
								viz2 ? "true" : "false");
					}
					if (doVerbose) {
						final double[] extraNumbers =
								new double[] { grandSumOfPriors, grandSumOfInitPriors };
						final String[] extraLabels =
								new String[] { "GrandSumOfPriors", "GrandSumOfInitPriors" };
						final boolean buildAncillaryElement = true;
						StringUtilities.fillInAncillaryElement(objectTypeSummarySums,
								formatter, objectTypeElement, buildAncillaryElement,
								objectTypeKey, extraNumbers, extraLabels);
					}
				} else {
					objectTypeElement.setAttribute("initialProbability", "0.0");
					objectTypeElement.setAttribute("conditionalPOS", "0.0");
					objectTypeElement.setAttribute("jointPOS", "0.0");
				}
				if (sumOfInitPriors > 0d) {
					final double initialProbabilityForCum =
							sumOfInitPriors / grandSumOfInitPriors;
					objectTypeElement.setAttribute("initialProbabilityForCum",
							LsFormatter.StandardFormat(initialProbabilityForCum));
					final double cumConditionalPos =
							objectTypeSummarySums._sumInitWtXPosFromAll / sumOfInitPriors;
					objectTypeElement.setAttribute("cumConditionalPOS",
							LsFormatter.StandardFormat(cumConditionalPos));
					objectTypeElement.setAttribute("cumJointPOS",
							LsFormatter.StandardFormat(
									cumConditionalPos * initialProbabilityForCum));
				} else {
					objectTypeElement.setAttribute("initialProbabilityForCum", "0.0");
					objectTypeElement.setAttribute("cumJointPOS", "0.0");
					objectTypeElement.setAttribute("cumConditionalPOS", "0.0");
				}
			}
			for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
				final PvValue pvValue = winningPlus.getPvValue(grandOrd);
				if (pvValue == null) {
					continue;
				}
				final PatternVariable pv = pvValue.getPv();
				if (!pv.isActive()) {
					continue;
				}
				final HashMap<Integer, LrcSet> lrcSetsInUse =
						posFunction.getVizLrcSets(pv);
				final boolean isTrackLine = pvValue.isTrackLine();
				final Element pvElt = formatter.newChild(boxesElement,
						isTrackLine ? "TRACKLINE" : "BOX");
				final MyStyle myStyle = pvValue.getMyStyle();
				pvElt.setAttribute("SruNameId", pv.getNameId());
				final Sortie pvValueSortie = pvValue.getSortie();
				final String sortieId = pv.getId();
				final String pvKey = SummarySumsBag.getKey(sortieId, null);
				pvElt.setAttribute("sru", pv.getNameId());
				/** Add onMars unless we have a (frozen) trackline. */
				if (!isTrackLine) {
					pvElt.setAttribute("onMars", "" + pvValue.onMars());
				}
				final SummarySums pvValueSummarySums = summarySumsMap.get(pvKey);
				final double pvValuePos =
						pvValueSummarySums._sumPriorXPosFromNew / grandSumOfPriors;
				pvElt.setAttribute("POS", LsFormatter.StandardFormat(pvValuePos));
				if (doVerbose) {
					final boolean buildAncillaryElement = true;
					final double[] extraNumbers = null;
					final String[] extraLabels = null;
					StringUtilities.fillInAncillaryElement(pvValueSummarySums,
							formatter, pvElt, buildAncillaryElement, pvKey, extraNumbers,
							extraLabels);
				}
				if (grandSumOfInitPriors > 0d) {
					final double pvValueCumPos =
							pvValueSummarySums._sumInitWtXPosFromAll /
									grandSumOfInitPriors;
					pvElt.setAttribute("cumPOS",
							LsFormatter.StandardFormat(pvValueCumPos));
				} else {
					pvElt.setAttribute("cumPOS", "0.0");
				}

				/**
				 * Add csp, violation information, and lists of points to pvElement
				 * if it's the main block
				 */
				if (mainBlock) {
					/** Add Csp to pvElt. */
					final LatLng3 csp = pvValue.getCsp();
					pvElt.setAttribute("CspLat",
							LsFormatter.StandardFormatForLatOrLng(csp.getLat()));
					pvElt.setAttribute("CspLng",
							LsFormatter.StandardFormatForLatOrLng(csp.getLng()));
					pvElt.setAttribute("ovlV",
							LsFormatter.StandardFormat(pvValueSummarySums._ovlV));
					pvElt.setAttribute("ovl",
							LsFormatter.StandardFormat(pvValueSummarySums._ovl));
					final PvSeq pvSeq = pv.getPvSeq();
					if (pvSeq != null) {
						final double pvSeqTransitV = pvValueSummarySums._pvSeqTrnstV;
						if (pvSeqTransitV > 0d) {
							pvElt.setAttribute("pvSeqV",
									LsFormatter.StandardFormat(pvSeqTransitV));
						}
					}
					if (isTrackLine) {
						/** TRACKLINE. Write the waypoints out. */
						ModelWriter.attachWaypoints(formatter, pvElt, pvValueSortie);
						/**
						 * Add the creepDirection and trackSpacing to a separate BOX
						 * sub-tag, if they both exist.
						 */
						final double creepHdg = pvValue.getCreepHdg();
						final double tsNmi = pvValue.computeTsNmi();
						if (0d <= creepHdg && creepHdg < 360d && tsNmi > 0d) {
							final Element boxElement = formatter.newChild(pvElt, "BOX");
							boxElement.setAttribute("creepDirection",
									String.format("%s degrees clockwise from north",
											LsFormatter.StandardFormat(creepHdg)));
							boxElement.setAttribute("trackSpacing", String.format("%s NM",
									LsFormatter.StandardFormat(tsNmi)));
						}
					} else {
						/** Add the BOX parameters to pvElt. */
						PlannerModel.addTsBoxInfoToElement(pv, myStyle, pvElt);
						/** Throw the pattern in under pvElt. */
						final Element patternElement =
								formatter.newChild(pvElt, "PATTERN");
						patternElement.setAttribute("SruNameId", pv.getNameId());
						ModelWriter.attachWaypoints(formatter, patternElement,
								pvValueSortie);
					}
					/** Add the polygons. */
					for (int kPass = 0; kPass < 4; ++kPass) {
						final String positionElementTag;
						final LatLng3[] corners;
						if (kPass == 0) {
							corners = pvValue
									.getLooseLatLngArray(SphericalTimedSegs.LoopType.SPEC);
							if (corners == null || corners.length == 0) {
								continue;
							}
							positionElementTag = "SPEC_POINT";
						} else if (kPass == 1) {
							corners = pvValue
									.getLooseLatLngArray(SphericalTimedSegs.LoopType.TS);
							if (corners == null || corners.length == 0) {
								continue;
							}
							positionElementTag = "POINT";
						} else if (kPass == 2) {
							corners = pvValue
									.getTightLatLngArray(SphericalTimedSegs.LoopType.TS);
							if (corners == null || corners.length == 0) {
								continue;
							}
							pvElt.setAttribute("NPOINTS_IN_TS_TIGHT",
									LsFormatter.StandardFormat(corners.length));
							positionElementTag = "TIGHT_CORNER";
						} else {
							corners = pvValue
									.getTightLatLngArray(SphericalTimedSegs.LoopType.EXC);
							if (corners == null || corners.length == 0) {
								continue;
							}
							pvElt.setAttribute("NPOINTS_IN_EXC",
									LsFormatter.StandardFormat(corners.length));
							positionElementTag = "EXC_CORNER";
						}
						final int nPoints = corners.length;
						for (int kPoint = 0; kPoint < nPoints; ++kPoint) {
							final LatLng3 cornerLatLng = corners[kPoint];
							final Element positionElement =
									formatter.newChild(pvElt, positionElementTag);
							positionElement.setAttribute("lat", LsFormatter
									.StandardFormatForLatOrLng(cornerLatLng.getLat()));
							positionElement.setAttribute("lng", LsFormatter
									.StandardFormatForLatOrLng(cornerLatLng.getLng()));
							final boolean tsLoose = kPass == 1;
							if (tsLoose && nPoints == 4) {
								positionElement.setAttribute("position",
										_CornerNamesIf4[kPoint]);
							} else {
								positionElement.setAttribute("index", "" + kPoint);
							}
						}
					}
				}

				/** main block or not, add information about object types. */
				for (final int objectType : objectTypes) {
					final Element pvObjectTypeElement =
							formatter.newChild(pvElt, "SEARCH_OBJECT_TYPE");
					final boolean viz2 =
							pv.getViz2LrcSets().keySet().contains(objectType);
					pvObjectTypeElement.setAttribute("isActive",
							viz2 ? "true" : "false");
					pvObjectTypeElement.setAttribute("id",
							LsFormatter.StandardFormat(objectType));
					final String pvObjectTypeKey =
							SummarySumsBag.getKey(sortieId, objectType);
					final SummarySums pvObjectTypeSummarySums =
							summarySumsMap.get(pvObjectTypeKey);
					final double sumOfPriors, sumOfInitPriors;
					if (pvObjectTypeSummarySums != null) {
						sumOfPriors = pvObjectTypeSummarySums._sumPriors;
						sumOfInitPriors = pvObjectTypeSummarySums._sumInitWts;
						if (doVerbose) {
							final boolean buildAncillaryElement = true;
							final double[] extraNumbers = null;
							final String[] extraLabels = null;
							StringUtilities.fillInAncillaryElement(
									pvObjectTypeSummarySums, formatter, pvObjectTypeElement,
									buildAncillaryElement, pvObjectTypeKey, extraNumbers,
									extraLabels);
						}
					} else {
						sumOfPriors = sumOfInitPriors = 0d;
					}
					if (sumOfPriors > 0d) {
						final double objectProbability = sumOfPriors / grandSumOfPriors;
						pvObjectTypeElement.setAttribute("initialProbability",
								LsFormatter.StandardFormat(objectProbability));
						final double conditionalPos =
								pvObjectTypeSummarySums._sumPriorXPosFromNew / sumOfPriors;
						pvObjectTypeElement.setAttribute("conditionalPOS",
								LsFormatter.StandardFormat(conditionalPos));
						final double jointPos = conditionalPos * objectProbability;
						pvObjectTypeElement.setAttribute("jointPOS",
								LsFormatter.StandardFormat(jointPos));
					} else {
						pvObjectTypeElement.setAttribute("initialProbability", "0.0");
						pvObjectTypeElement.setAttribute("conditionalPOS", "0.0");
						pvObjectTypeElement.setAttribute("jointPOS", "0.0");
					}
					final LrcSet lrcSet = lrcSetsInUse.get(objectType);
					if (lrcSet != null) {
						final double sweepWidth = lrcSet.getSweepWidth();
						final double inputSweepWidth;
						if (lrcSet.getNLrcs() == 1) {
							final LateralRangeCurve lrc = lrcSet.getLrc(0);
							if (lrc instanceof Logit) {
								inputSweepWidth = ((Logit) lrc).getInputSweepWidth();
							} else {
								inputSweepWidth = Double.NaN;
							}
						} else {
							inputSweepWidth = Double.NaN;
						}
						pvObjectTypeElement.setAttribute("sw", String
								.format(LsFormatter.StandardFormat(sweepWidth) + " NM"));
						if (!Double.isNaN(inputSweepWidth)) {
							pvObjectTypeElement.setAttribute("inputSweepWidth",
									LsFormatter.StandardFormat(inputSweepWidth) + " NM");
						}

						/** Do the coverage output. */
						if (!pvValue.onMars()) {
							/** Do the coverage output for SAROPS. */
							final double coverage2;
							if (pvValue.onMars()) {
								coverage2 = 0d;
							} else {
								final double tsNmi = pvValue.computeTsNmi();
								if (!Double.isNaN(tsNmi)) {
									coverage2 = sweepWidth / tsNmi;
								} else {
									coverage2 = 0d;
								}
							}
							pvObjectTypeElement.setAttribute("coverage",
									LsFormatter.StandardFormat(coverage2));
							final CoverageToPodCurve coverageToPodCurve =
									_plannerModel.getCoverageToPodCurve();
							final double pod2 =
									coverageToPodCurve.coverageToPod(coverage2);
							pvObjectTypeElement.setAttribute("coverageToPod",
									LsFormatter.StandardFormat(pod2));
						} else {
							/** Coverage values for onMars are 0. */
							pvObjectTypeElement.setAttribute("coverage",
									LsFormatter.StandardFormat(0d));
							final CoverageToPodCurve coverageToPodCurve =
									_plannerModel.getCoverageToPodCurve();
							final double pod2 = coverageToPodCurve.coverageToPod(0d);
							pvObjectTypeElement.setAttribute("coverageToPod",
									LsFormatter.StandardFormat(pod2));
						}
					}
					if (sumOfInitPriors > 0d) {
						final double initialProbabilityForCum =
								sumOfInitPriors / grandSumOfInitPriors;
						pvObjectTypeElement.setAttribute("initialProbabilityForCum",
								LsFormatter.StandardFormat(initialProbabilityForCum));
						final double cumConditionalPos =
								pvObjectTypeSummarySums._sumInitWtXPosFromAll /
										sumOfInitPriors;
						pvObjectTypeElement.setAttribute("cumConditionalPOS",
								LsFormatter.StandardFormat(cumConditionalPos));
						final double cumJointPos =
								cumConditionalPos * initialProbabilityForCum;
						pvObjectTypeElement.setAttribute("cumJointPOS",
								LsFormatter.StandardFormat(cumJointPos));
					} else {
						pvObjectTypeElement.setAttribute("initialProbabilityForCum",
								"0.0");
						pvObjectTypeElement.setAttribute("cumPOS", "0.0");
					}
				}
			}
			final int nChunksDone = nChunksDoneUponEntering + iPass + 1;
			_simCase.reportChunksDone(nChunksDone);
		}
	}

	private void writePlannerDashboard(final String versionName,
			final PlannerReportsData plannerReportsDataForAll,
			final PlannerReportsData plannerReportsDataForOptnScore) {
		final LsFormatter formatter = new LsFormatter();
		final String rootTag = "PLANNER_DASHBOARD_TABLES";
		final Element rootElement = formatter.newElement(rootTag);
		final Date currentDate = new Date(System.currentTimeMillis());
		final SimpleDateFormat simpleDateFormat =
				new SimpleDateFormat("yyyy MMM dd  hh.mm.ss a.z");
		final String timeString = simpleDateFormat.format(currentDate);
		rootElement.setAttribute("timeCreated", timeString);
		rootElement.setAttribute("versionName", versionName);
		final PvValueArrayPlus plus =
				plannerReportsDataForAll._pvValueArrayPlus;

		/** Top left. */
		final Element topLeftElement =
				formatter.newChild(rootElement, "TOP_LEFT");
		final PvValue[] nonNullPvValues = plus.getNonNullPvValues();
		final int nLivePttrnVbls = nonNullPvValues.length;
		for (int k = 0; k < nLivePttrnVbls; ++k) {
			final PvValue pvValue = nonNullPvValues[k];
			final PatternVariable pv = pvValue.getPv();
			final String pvId = pv.getId();
			final Element patternElement =
					formatter.newChild(topLeftElement, "PATTERN");
			patternElement.setAttribute("SruId", pvId);
			final double[] numDen = PlannerReportsDataUtils
					.getTopLeftSlctdObjsPos(plannerReportsDataForAll, pvId);
			final String s1 = LsFormatter.StandardPercentFormat(numDen);
			patternElement.setAttribute("SelectedObjects-POS", s1);
		}

		/** Bottom left. */
		final Element bottomLeftElement =
				formatter.newChild(rootElement, "BOTTOM_LEFT");
		final double[] thisPlanNumDen =
				PlannerReportsDataUtils.getThisPlan(plannerReportsDataForAll);
		final String s1 = LsFormatter.StandardPercentFormat(thisPlanNumDen);
		bottomLeftElement.setAttribute("ThisPlan", s1);

		/** NetGain is for All. */
		final double[] netGainNumDen = PlannerReportsDataUtils
				.getNetGain(plannerReportsDataForAll, /* otInteger= */null);
		final String s2 = LsFormatter.StandardPercentFormat(netGainNumDen);
		bottomLeftElement.setAttribute("NetGain", s2);

		/** Top Right. */
		final Element topRightElement =
				formatter.newChild(rootElement, "TOP_RIGHT");
		double ttlCoveredArea = 0d;
		for (int k = 0; k < nLivePttrnVbls; ++k) {
			final PvValue pvValue = nonNullPvValues[k];
			final boolean offEarth = pvValue.onMars();
			final PatternVariable pv = pvValue.getPv();
			final double rawSearchKts = pv.getRawSearchKts();
			final double minTsNmi = pv.getMinTsNmi();
			final String pvId = pv.getId();
			final Sortie sortie = pvValue.getSortie();
			final Element patternElement =
					formatter.newChild(topRightElement, "PATTERN");
			patternElement.setAttribute("SruId", pvId);
			final double tsNmi = pvValue.computeTsNmi();
			/** We'll put this out even if ts is Nan. */
			addDoubleAttribute(patternElement, "Ts", tsNmi);
			final double ttlLegsNmi = sortie.getTtlLegsNmi();

			final double pvTsAreaSqNmi;
			if (offEarth) {
				pvTsAreaSqNmi = 0d;
			} else {
				final MyStyle myStyle = pvValue.getMyStyle();
				if (myStyle != null) {
					pvTsAreaSqNmi = myStyle.computeTsSqNmi(rawSearchKts, minTsNmi);
				} else if (!Double.isNaN(tsNmi)) {
					pvTsAreaSqNmi = ttlLegsNmi * tsNmi;
				} else {
					final Loop3 tightTsLoop = pvValue.getTightTsLoop();
					pvTsAreaSqNmi = tightTsLoop.getSqNmi();
				}
			}
			addDoubleAttribute(patternElement, "Area", pvTsAreaSqNmi);
			ttlCoveredArea += pvTsAreaSqNmi;
			final double[] objProbNumDen =
					PlannerReportsDataUtils.getObjProb(plannerReportsDataForAll);
			final String s3 = LsFormatter.StandardPercentFormat(objProbNumDen);
			patternElement.setAttribute("ObjectProbability", s3);
			final double[] jointPosNumDen = PlannerReportsDataUtils
					.getJointPos(plannerReportsDataForAll, pvId);
			final String s4 = LsFormatter.StandardPercentFormat(jointPosNumDen);
			patternElement.setAttribute("JointPOS", s4);
			final HashMap<Integer, LrcSet> vizLrcSets = pv.getViz1LrcSets();
			for (final Map.Entry<Integer, LrcSet> entry : vizLrcSets.entrySet()) {
				final LrcSet lrcSet = entry.getValue();
				if (lrcSet == null || lrcSet.getSweepWidth() <= 0d) {
					continue;
				}
				final int ot = entry.getKey();
				final Element otElement = formatter.newChild(patternElement, "SO");
				otElement.setAttribute("SoId", "" + ot);
				final double sw = lrcSet.getSweepWidth();
				addDoubleAttribute(otElement, "SW", sw);
				final double pvOtCoverage;
				if (offEarth) {
					pvOtCoverage = 0d;
				} else if (tsNmi > 0d) {
					pvOtCoverage = sw / tsNmi;
				} else if (pvTsAreaSqNmi > 0d) {
					pvOtCoverage = ttlLegsNmi * sw / pvTsAreaSqNmi;
				} else {
					pvOtCoverage = 0d;
				}
				addDoubleAttribute(otElement, "Coverage", pvOtCoverage);
				final double[] objProbNumDenForOt = PlannerReportsDataUtils
						.getObjProb(plannerReportsDataForAll, ot);
				final String s5 =
						LsFormatter.StandardPercentFormat(objProbNumDenForOt);
				otElement.setAttribute("ObjectProbability", s5);

				final double[] pairJointPosNumDen = PlannerReportsDataUtils
						.getJointPos(plannerReportsDataForAll, pvId, ot);
				final String s7 =
						LsFormatter.StandardPercentFormat(pairJointPosNumDen);
				otElement.setAttribute("JointPOS", s7);

				final double[] condNumDen = PlannerReportsDataUtils
						.getCondPos(plannerReportsDataForAll, pvId, ot);
				final String s8 = LsFormatter.StandardPercentFormat(condNumDen);
				otElement.setAttribute("ConditionalPOS", s8);
			}
		}
		final Element allPttrnVblsElement =
				formatter.newChild(topRightElement, "ALL_SRUS");
		addDoubleAttribute(allPttrnVblsElement, "Area", ttlCoveredArea);
		final double[] allPttrnVblsJointPosNumDen = PlannerReportsDataUtils
				.getAllPttrnVblsJointPos(plannerReportsDataForAll);
		final String s10 =
				LsFormatter.StandardPercentFormat(allPttrnVblsJointPosNumDen);
		allPttrnVblsElement.setAttribute("JointPOS", s10);

		/** Bottom Right. */
		final Element bottomRightElement =
				formatter.newChild(rootElement, "BOTTOM_RIGHT");

		/**
		 * We put the optimization score in an attribute of the bottom right.
		 */
		final double[] optnScoreNumDen =
				PlannerReportsDataUtils.getThisPlan(plannerReportsDataForOptnScore);
		final String s11 = LsFormatter.StandardPercentFormat(optnScoreNumDen);
		bottomRightElement.setAttribute("OptnScore", s11);

		final HashSet<Integer> viz1ObjectTypeIds =
				_plannerModel.getViz1ObjectTypeIds();
		final int nOts = viz1ObjectTypeIds.size();
		final Iterator<Integer> it = viz1ObjectTypeIds.iterator();
		double totalObjProb = 0d;
		double totalJointPos = 0d;
		double totalRemProb = 0d;
		for (int k = 0; k < nOts; ++k) {
			final int ot = it.next();
			final Element otElement =
					formatter.newChild(bottomRightElement, "SO");
			otElement.setAttribute("SoId", "" + ot);

			final double[] objProbNumDen =
					PlannerReportsDataUtils.getObjProb(plannerReportsDataForAll, ot);
			final String s12 = LsFormatter.StandardPercentFormat(objProbNumDen);
			otElement.setAttribute("ObjectProbability", s12);
			final double objProbNum = objProbNumDen[0];
			final double objProbDen = objProbNumDen[1];
			if (objProbDen > 0d) {
				totalObjProb += objProbNum / objProbDen;
			}

			final double[] jointPosNumDen =
					PlannerReportsDataUtils.getJointPos(plannerReportsDataForAll, ot);
			final String s13 = LsFormatter.StandardPercentFormat(jointPosNumDen);
			otElement.setAttribute("JointPOS", s13);
			final double jointPosNum = jointPosNumDen[0];
			final double jointPosDen = jointPosNumDen[1];
			if (jointPosDen > 0d) {
				totalJointPos += jointPosNum / jointPosDen;
			}

			final double[] condPosNumDen =
					PlannerReportsDataUtils.getCondPos(plannerReportsDataForAll, ot);
			final String s14 = LsFormatter.StandardPercentFormat(condPosNumDen);
			otElement.setAttribute("ConditionalPOS", s14);

			final double[] remProbNumDen = PlannerReportsDataUtils
					.getRemainingProbability(plannerReportsDataForAll, ot);
			final String s15 = LsFormatter.StandardPercentFormat(remProbNumDen);
			otElement.setAttribute("RemainingProbability", s15);
			final double remProbNum = remProbNumDen[0];
			final double remProbDen = remProbNumDen[1];
			if (remProbDen > 0d) {
				totalRemProb += remProbNum / remProbDen;
			}

			/** NetGain is for All. */
			final double[] netGainNumDenForOt =
					PlannerReportsDataUtils.getNetGain(plannerReportsDataForAll, ot);
			final String s15a =
					LsFormatter.StandardPercentFormat(netGainNumDenForOt);
			otElement.setAttribute("NetGain", s15a);
		}

		/** Final line in bottom right table. */
		final Element allOtsElement =
				formatter.newChild(bottomRightElement, "ALL_SOs");
		final double[] totalObjProbNumDen = new double[] { totalObjProb, 1d };
		final String s16 =
				LsFormatter.StandardPercentFormat(totalObjProbNumDen);
		allOtsElement.setAttribute("ObjectProbability", s16);
		final double[] totalJointPosNumDen = new double[] { totalJointPos, 1d };
		final String s17 =
				LsFormatter.StandardPercentFormat(totalJointPosNumDen);
		allOtsElement.setAttribute("JointPOS", s17);
		final double[] totalRemProbNumDen = new double[] { totalRemProb, 1d };
		final String s18 =
				LsFormatter.StandardPercentFormat(totalRemProbNumDen);
		allOtsElement.setAttribute("RemainingProbability", s18);

		final String filePathToDumpTo =
				_plannerModel.getPlannerDashboardTablesPath();
		try (final FileOutputStream fos =
				new FileOutputStream(filePathToDumpTo)) {
			formatter.dumpWithTimeComment(rootElement, fos);
		} catch (final Exception e) {
			MainRunner.HandleFatal(_simCase, new RuntimeException(e));
		}
	}

	private void writeExternalReport(final String versionName,
			final PlannerReportsData plannerReportsData) {
		final LsFormatter formatter = new LsFormatter();
		final String rootTag = "EXTERNAL_REPORT";
		final Element rootElement = formatter.newElement(rootTag);
		final Date currentDate = new Date(System.currentTimeMillis());
		final SimpleDateFormat simpleDateFormat =
				new SimpleDateFormat("yyyy MMM dd  hh.mm.ss a.z");
		final String timeString = simpleDateFormat.format(currentDate);
		rootElement.setAttribute("timeCreated", timeString);
		rootElement.setAttribute("versionName", versionName);

		final int[] ots = plannerReportsData._ots;
		final int nOts = ots.length;
		final HashMap<Integer, int[]> adriftAndLanded2 = PlannerReportsDataUtils
				.getNumberAdriftAndLanded2(plannerReportsData);
		int nTotalAdrift = 0;
		int nTotalLanded = 0;
		double totalObjProb = 0d;
		double totalJointPos = 0d;
		double totalRemProb = 0d;
		for (int k = 0; k < nOts; ++k) {
			final int ot = ots[k];
			final Element soElement = formatter.newChild(rootElement, "SO");
			soElement.setAttribute("SoId", "" + ot);
			final int[] thisAdriftAndLanded2 = adriftAndLanded2.get(ot);
			final int nAdrift;
			final int nLanded;
			if (thisAdriftAndLanded2 == null) {
				nAdrift = 0;
				nLanded = 0;
			} else {
				nAdrift = thisAdriftAndLanded2[0];
				nLanded = thisAdriftAndLanded2[1];
			}
			addDoubleAttribute(soElement, "NumberAdrift", nAdrift);
			addDoubleAttribute(soElement, "NumberOnLand", nLanded);
			nTotalAdrift += nAdrift;
			nTotalLanded += nLanded;

			final double[] initCondPosNumDen =
					PlannerReportsDataUtils.getInitCondPos(plannerReportsData, ot);
			final String s1 =
					LsFormatter.StandardPercentFormat(initCondPosNumDen);
			soElement.setAttribute("ConditionalPOS", s1);

			final double[] initObjProbNumDen =
					PlannerReportsDataUtils.getInitObjProb(plannerReportsData, ot);
			final String s2 =
					LsFormatter.StandardPercentFormat(initObjProbNumDen);
			soElement.setAttribute("ObjectProbability", s2);
			if (initObjProbNumDen[1] > 0d) {
				totalObjProb += initObjProbNumDen[0] / initObjProbNumDen[1];
			}

			final double[] initJointPosNumDen =
					PlannerReportsDataUtils.getInitJointPos(plannerReportsData, ot);
			final String s3 =
					LsFormatter.StandardPercentFormat(initJointPosNumDen);
			soElement.setAttribute("JointPOS", s3);
			if (initJointPosNumDen[1] > 0d) {
				totalJointPos += initJointPosNumDen[0] / initJointPosNumDen[1];
			}

			final double[] initRemProbNumDen = PlannerReportsDataUtils
					.getInitRemainingProbability(plannerReportsData, ot);
			final String s4 =
					LsFormatter.StandardPercentFormat(initRemProbNumDen);
			soElement.setAttribute("RemainingProbability", s4);
			if (initRemProbNumDen[1] > 0d) {
				totalRemProb += initRemProbNumDen[0] / initRemProbNumDen[1];
			}
		}
		final Element allObjectTypesElement =
				formatter.newChild(rootElement, "Totals");
		addDoubleAttribute(allObjectTypesElement, "NumberAdrift", nTotalAdrift);
		addDoubleAttribute(allObjectTypesElement, "NumberOnLand", nTotalLanded);

		final String s2 = LsFormatter.StandardPercentFormat(totalObjProb, 1d);
		allObjectTypesElement.setAttribute("ObjectProbability", s2);

		final String s3 = LsFormatter.StandardPercentFormat(totalJointPos, 1d);
		allObjectTypesElement.setAttribute("JointPOS", s3);

		final String s4 = LsFormatter.StandardPercentFormat(totalRemProb, 1d);
		allObjectTypesElement.setAttribute("RemainingProbability", s4);
		final String filePathToDumpTo = _plannerModel.getExternalReportPath();
		try (final FileOutputStream fos =
				new FileOutputStream(filePathToDumpTo)) {
			formatter.dumpWithTimeComment(rootElement, fos);
		} catch (final Exception e) {
			MainRunner.HandleFatal(_simCase, new RuntimeException(e));
		}
	}

	private void writeEvalReport(final String versionName,
			final PlannerReportsData plannerReportsData) {
		final LsFormatter formatter = new LsFormatter();
		final String rootTag = "EVAL_REPORT";
		final Element rootElement = formatter.newElement(rootTag);
		final Date currentDate = new Date(System.currentTimeMillis());
		final SimpleDateFormat simpleDateFormat =
				new SimpleDateFormat("yyyy MMM dd  hh.mm.ss a.z");
		final String timeString = simpleDateFormat.format(currentDate);
		rootElement.setAttribute("timeCreated", timeString);
		rootElement.setAttribute("versionName", versionName);

		final PvValueArrayPlus plus = plannerReportsData._pvValueArrayPlus;
		final PvValue[] nonNullPvValues = plus.getNonNullPvValues();
		final int nLivePttrnVbls = nonNullPvValues.length;
		for (int k = 0; k < nLivePttrnVbls; ++k) {
			final PvValue pvValue = nonNullPvValues[k];
			final PatternVariable pv = pvValue.getPv();
			final String pvId = pv.getId();
			final Element patternElement =
					formatter.newChild(rootElement, "PATTERN");
			patternElement.setAttribute("SruId", pvId);

			final double[] jointPosNumDen =
					PlannerReportsDataUtils.getEvalJointPos(plannerReportsData, pvId);
			final String s1 = LsFormatter.StandardPercentFormat(jointPosNumDen);
			patternElement.setAttribute("POS-PERCENT", s1);
		}
		final Element allPttrnVblsElement =
				formatter.newChild(rootElement, "ALL_SRUS");
		final double[] allPttrnVblsJointPos = PlannerReportsDataUtils
				.getEvalAllPttrnVblsJointPos(plannerReportsData);
		final String s1 =
				LsFormatter.StandardPercentFormat(allPttrnVblsJointPos);
		allPttrnVblsElement.setAttribute("PlanTotal", s1);

		final double[] evalCum =
				PlannerReportsDataUtils.getEvalCum(plannerReportsData);
		final String s2 = LsFormatter.StandardPercentFormat(evalCum);
		allPttrnVblsElement.setAttribute("Cumulative", s2);
		final String filePathToDumpTo = _plannerModel.getEvalReportPath();
		try (final FileOutputStream fos =
				new FileOutputStream(filePathToDumpTo)) {
			formatter.dumpWithTimeComment(rootElement, fos);
		} catch (final Exception e) {
			MainRunner.HandleFatal(_simCase, new RuntimeException(e));
		}
	}

	private static void addDoubleAttribute(final Element element,
			final String attributeName, final double d) {
		if (Double.isNaN(d)) {
			element.setAttribute(attributeName, LsFormatter._BadNumberString);
		} else {
			final String s = LsFormatter.StandardFormat(d);
			element.setAttribute(attributeName, s);
		}
	}

	public void writeTimesFile(final String plannerModelFilePath) {
		/** Write out the times file. */
		final String modelStashedPlanFilePath =
				_plannerModel.getStashedPlannerModelPath();
		final String modelStashedPlanFileName =
				new File(modelStashedPlanFilePath).getName();
		final int lastIndex =
				modelStashedPlanFileName.lastIndexOf(SimCaseManager._PlanEndingLc);
		final String stashedTimesFileName =
				modelStashedPlanFileName.substring(0, lastIndex) + "-planTimes.txt";
		final String thisModelFilePath = _plannerModel.getPlannerFilePath();
		final File stashDir = AbstractOutFilesManager.GetEngineFilesDir(_simCase,
				thisModelFilePath, "PlanResult");
		final File timesFile = new File(stashDir, stashedTimesFileName);
		_simCase.dumpCriticalTimes("Planner", _entryTimeInMillis, timesFile);
		_simCase.runOutChunks();
	}

	private void setEntryMs(final long entryTimeInMillis) {
		_entryTimeInMillis = entryTimeInMillis;
	}

	private void resetSolversManager() {
		if (_solversManager != null) {
			_solversManager.shutDownSolvers();
		}
		_solversManager = new SolversManager(this);
	}

	public SolversManager getSolversManager() {
		return _solversManager;
	}

	public long getEarliestCriticalRefSecs() {
		if (amRealPlannerProblem()) {
			return _plannerModel.getMinPvRefSecs();
		}
		if (getSimModel().getDisplayOnly()) {
			return -1;
		}
		final long[] refSecsS =
				_particlesManager.getParticlesFile().getRefSecsS();
		return refSecsS[0];
	}

	public long getLatestCriticalRefSecs() {
		if (amRealPlannerProblem()) {
			return _plannerModel.getMaxPvRefSecs();
		}
		if (_particlesManager == null) {
			return -1;
		}
		final long[] realRefSecsS =
				_particlesManager.getParticlesFile().getRefSecsS();
		return realRefSecsS[realRefSecsS.length - 1];
	}

	public SimCase getSimCase() {
		return _simCase;
	}

	public SimCaseManager getSimCaseManager() {
		return _simCase == null ? null : _simCase.getSimCaseManager();
	}

	public boolean haveEnv() {
		final Model model = getSimModel();
		if (model == null) {
			return false;
		}
		return model.getCurrentsUvGetter() != null &&
				model.getWindsUvGetter() != null;
	}

	public boolean runStudy() {
		return _plannerModel != null && _plannerModel.runStudy();
	}

	public PvPlacer[] getFirstPvPlacers() {
		return _firstPvPlacers;
	}

	public Deconflicter getFirstDeconflicter() {
		return _firstDeconflicter;
	}

	public void updateFirstPvPlacers(final ArrayList<PvPlacer> pvPlacers) {
		boolean fireJumpEvent = false;
		for (final PvPlacer pvPlacer : pvPlacers) {
			final PatternVariable pv = pvPlacer._pv;
			final int k = pv.getGrandOrd();
			final PvPlacer old = _firstPvPlacers[k];
			if (old == null) {
				_firstPvPlacers[k] = pvPlacer;
				fireJumpEvent = true;
			}
		}
		if (fireJumpEvent) {
			final PqSolver pqSolver = _solversManager.getPqSolver();
			final JumpEvent jumpEvent = new JumpEvent(pqSolver);
			_solversManager.fireJumpEvent(jumpEvent);
		}
	}

	public void updateDeconfliction(final Deconflicter deconflicter) {
		_firstDeconflicter = deconflicter;
		final PqSolver pqSolver = _solversManager.getPqSolver();
		final DeconflictEvent deconflictionEvent =
				new DeconflictEvent(pqSolver, deconflicter);
		_solversManager.fireDeconflictionEvent(deconflictionEvent);
	}

//TMK!! Delete this. (The rest of this file)
	final private boolean _amRealPlannerProblem;
	final private Model _displayOnlySimModel;
	final private PvPlacer[] _firstPvPlacers;
	private Deconflicter _firstDeconflicter;

	/** This is only for displaying non-planner cases. */
	public Planner(final SimCaseManager.SimCase simCase,
			final Model simModel) {
		_displayOnlySimModel = simModel;
		final String particlesFilePath = simModel.getParticlesFilePath();
		_introChunks = _bodyChunks = null;
		_shutDownChunks = _wrapUpChunks = _evalChunks = null;
		_firstDeconflicter = null;
		if (simCase != null) {
			simCase.allowGoing();
			simCase.setMainSaropsObject(this);
		}
		_simCase = simCase;
		_plannerModel = null;
		_firstPvPlacers = null;
		_amRealPlannerProblem = false;
		_pFailsCache = null;
		_growingSampleNftSelected = null;
		_growingSampleFtSelected = null;
		_completeFtSelected = null;
		_completeFtAll = null;
		_completeFtLandedAdrift = null;
		_solversManager = null;
		final ParticlesFile particlesFile;
		if (particlesFilePath == null) {
			particlesFile = null;
		} else {
			final SimCaseManager simCaseManager = simCase.getSimCaseManager();
			particlesFile =
					simCaseManager.getParticlesFile(simCase, particlesFilePath);
		}
		if (simModel.getDisplayOnly()) {
			_particlesManager = null;
		} else {
			/**
			 * Putting in -1's for the first and last time of interest indicates
			 * that we are interested in the entire particlesFile. The times of
			 * interest indicate where the stage markers, or fence posts go.
			 */
			final ParticlesManager.Specs specs = new ParticlesManager.Specs( //
					/* includeLandedParticles= */true, //
					/* includeAdriftParticles= */true, //
					/* firstRefSecsOfInterest= */-1L, //
					/* lastRefSecsOfInterest= */-1L, //
					/* viz1ObjectTypeIds= */null, //
					/* viz2ObjectTypeIds= */null);
			_particlesManager = ParticlesManager.createParticlesManager(simCase,
					particlesFile, specs);
			if (_particlesManager == null) {
				return;
			}
		}
	}

}

/**
 * <pre>
https://www.datamation.com/cloud-computing/aws-vs-azure-vs-google-cloud-comparison.html
 * </pre>
 */