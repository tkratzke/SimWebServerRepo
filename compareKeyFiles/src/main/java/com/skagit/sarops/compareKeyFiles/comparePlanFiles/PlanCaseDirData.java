package com.skagit.sarops.compareKeyFiles.comparePlanFiles;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.TreeSet;

import com.skagit.sarops.compareKeyFiles.CompareKeyFiles;
import com.skagit.sarops.compareKeyFiles.comparePlanFiles.DashboardTables.PttrnVblStructure;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.SearchObjectType;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.ParticlesFile;
import com.skagit.util.myLogger.MyLogger;

public class PlanCaseDirData {

	final public SimCaseManager.SimCase _simCase;
	final public File _caseDir;
	final public Model _model;
	final public PlannerModel _plannerModel;
	final public DashboardTables _newDashboardTables;
	final public DashboardTables _origDashboardTables;
	final public PlanDataRow[] _planDataRows;

	public PlanCaseDirData(final SimCaseManager.SimCase simCase, final File caseDir) {
		_caseDir = caseDir;
		_simCase = simCase;
		final File origDir = new File(caseDir, CompareKeyFiles._OrigDirName);
		if (!caseDir.isDirectory() || !caseDir.canRead() || !origDir.isDirectory() || !origDir.canRead()) {
			_model = null;
			_plannerModel = null;
			_newDashboardTables = _origDashboardTables = null;
			_planDataRows = null;
			return;
		}

		final FileFilter filter = new FileFilter() {

			@Override
			public boolean accept(final File f) {
				final String lc = f.getName().toLowerCase();
				return lc.endsWith("eval_plannerdashboardtables.xml");
			}
		};
		final File[] candidates0 = caseDir.listFiles(filter);
		final File[] candidates1 = origDir.listFiles(filter);
		File[] pairToCompare = null;
		final int nNew = candidates0.length;
		final int nOrig = candidates1.length;
		PlannerModel plannerModel = null;

		/**
		 * <pre>
		 * We would like to get a PlannerModel, but to do that, we need a valid
		 * ParticlesFile path, which is what we need for a SimModel anyway. Hence,
		 * we get particlesFilePath and a SimModel from it first.
		 *
		 * NB: We get particle.nc and the plannerModel file from ORIG, not caseDir.
		 * </pre>
		 */
		Model modelA = null;
		String particlesFilePathA = null;
		final FileFilter ncFilter = new FileFilter() {

			@Override
			public boolean accept(final File f) {
				return f.getName().toLowerCase().endsWith(".nc");
			}
		};
		final File[] particlesFileCandidates = origDir.listFiles(ncFilter);
		final int nNcFiles = particlesFileCandidates.length;
		for (int k = 0; k < nNcFiles; ++k) {
			final File ncFile = particlesFileCandidates[k];
			try {
				final String thisParticlesFilePath = ncFile.getAbsolutePath();
				final ParticlesFile particlesFile = new ParticlesFile(simCase, thisParticlesFilePath);
				modelA = particlesFile.getModel();
				particlesFilePathA = thisParticlesFilePath;
				break;
			} catch (final Exception e) {
				continue;
			}
		}
		if (modelA == null) {
			_model = null;
			_plannerModel = null;
			_newDashboardTables = _origDashboardTables = null;
			_planDataRows = null;
			return;
		}
		final Model simModel = modelA;
		final String particlesFilePath = particlesFilePathA;

		/**
		 * Armed with a valid ParticlesFilePath (and we validated it by reading a
		 * SimModel from it, so we use that SimModel), we can get the dashboardTables
		 * file and the PlannerModel.
		 */
		for (int k0 = 0; k0 < nNew; ++k0) {
			final File f0 = candidates0[k0];
			final String name0 = f0.getName();
			final String nameLc0 = name0.toLowerCase();
			for (int k1 = 0; k1 < nOrig; ++k1) {
				final File f1 = candidates1[k1];
				final String name1 = f1.getName();
				final String nameLc1 = name1.toLowerCase();
				if (nameLc1.equals(nameLc0)) {
					/**
					 * Need a PlannerModel with this name, acquired from origDir.
					 */
					final int lastUnderscore = nameLc0.lastIndexOf("_plannerdashboardtables.xml");
					if (lastUnderscore < 0) {
						continue;
					}
					final String planFileName = name0.substring(0, lastUnderscore) + ".xml";
					final File planFile = new File(origDir, planFileName);
					try {
						plannerModel = new PlannerModel(simCase, simModel, planFile.getAbsolutePath(),
								particlesFilePath);
					} catch (final Exception e) {
						plannerModel = null;
						continue;
					}
					pairToCompare = new File[] {
							f0, f1
					};
					break;
				}
			}
		}
		if (pairToCompare == null) {
			_model = null;
			_plannerModel = null;
			_newDashboardTables = _origDashboardTables = null;
			_planDataRows = null;
			return;
		}

		/** We have _plannerModel and _model. */
		_plannerModel = plannerModel;
		_model = simModel;

		final File newDashboardTablesFile = pairToCompare[0];
		final File origDashboardTablesFile = pairToCompare[1];
		DashboardTables newDashboardTables = null;
		DashboardTables origDashboardTables = null;
		try {
			newDashboardTables = new DashboardTables(_plannerModel, newDashboardTablesFile);
			origDashboardTables = new DashboardTables(_plannerModel, origDashboardTablesFile);
		} catch (final Exception e) {
			newDashboardTables = origDashboardTables = null;
		}
		if (newDashboardTables == null || origDashboardTables == null) {
			_newDashboardTables = _origDashboardTables = null;
			_planDataRows = null;
			return;
		}
		_newDashboardTables = newDashboardTables;
		_origDashboardTables = origDashboardTables;

		/**
		 * Build the set of all PvIds and partition it into frozens and floaters.
		 */
		final TreeMap<String, Integer> allPvIds = new TreeMap<>();
		final TreeSet<String> frozenPvIds = new TreeSet<>();
		final TreeSet<String> floatingPvIds = new TreeSet<>();
		final PatternVariable[] pttrnVbls = _plannerModel.getPttrnVbls();
		final int nPttrnVbls = pttrnVbls.length;
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			final PatternVariable pv = _plannerModel.grandOrdToPv(grandOrd);
			final String pvId = pv.getId();
			allPvIds.put(pvId, grandOrd);
			if (pv.getPermanentFrozenPvValue() != null) {
				frozenPvIds.add(pvId);
			} else {
				floatingPvIds.add(pvId);
			}
		}
		final boolean isEvalRun = floatingPvIds.isEmpty();

		/** Build sotIntToName. */
		final TreeMap<Integer, String> sotIntToName = new TreeMap<>();
		if (_model != null) {
			final int nSots = _model.getNSearchObjectTypes();
			for (int sotOrd = 0; sotOrd < nSots; ++sotOrd) {
				final SearchObjectType sot = _model.getSotFromOrd(sotOrd);
				sotIntToName.put(sot.getId(), sot.getName());
			}
		}
		for (int iPass = 0; iPass < 2; ++iPass) {
			final DashboardTables dashboardTables = iPass == 0 ? _newDashboardTables : _origDashboardTables;
			if (dashboardTables != null) {
				for (final int sotInt : dashboardTables._sotIntToSrchObjctStructure.keySet()) {
					if (sotIntToName.get(sotInt) == null) {
						sotIntToName.put(sotInt, Integer.toString(sotInt));
					}
				}
				for (final PttrnVblStructure pvStruct : dashboardTables._idToPttrnVblStructure.values()) {
					for (final int sotInt : pvStruct._sotIntToLittlePvStructure.keySet()) {
						if (sotIntToName.get(sotInt) == null) {
							sotIntToName.put(sotInt, Integer.toString(sotInt));
						}
					}
				}
			}
		}

		/** Build the set of PlanDataRows. */
		final ArrayList<PlanDataRow> planDataRows = new ArrayList<>();

		/** Start with the globals. */
		final MyLogger logger = simCase.getLogger();
		final double newNetGain = _newDashboardTables._netGain;
		final double origNetGain = _origDashboardTables._netGain;
		final PlanDataRow netGainDataRow = new PlanDataRow(caseDir, "NetGain", /* pvId= */null,
				/* grandOrd= */Model._WildCard, /* sotName= */null, /* sotInt= */Model._WildCard, newNetGain,
				origNetGain, /* checkEval= */isEvalRun, /* checkOptn= */false);
		addToIfValid(logger, netGainDataRow, planDataRows);

		final double newThisPlanPos = _newDashboardTables._thisPlan;
		final double origThisPlanPos = _origDashboardTables._thisPlan;
		final PlanDataRow thisPlanPosDataRow = new PlanDataRow(caseDir, "ThisPlanPOS", /* pvId= */null,
				/* grandOrd= */Model._WildCard, /* sotName= */null, /* sotInt= */Model._WildCard, newThisPlanPos,
				origThisPlanPos, /* checkEval= */isEvalRun, /* checkOptn= */false);
		addToIfValid(logger, thisPlanPosDataRow, planDataRows);

		final double newOptnScore = _newDashboardTables._optnScore;
		final double origOptnScore = _origDashboardTables._optnScore;
		final PlanDataRow optnScoreDataRow = new PlanDataRow(caseDir, "OptnScore", /* pvId= */null,
				/* grandOrd= */Model._WildCard, /* sotName= */null, /* sotId= */Model._WildCard, newOptnScore,
				origOptnScore, /* checkEval= */false, /* checkOptn= */!isEvalRun);
		addToIfValid(logger, optnScoreDataRow, planDataRows);

		final double newGlobalJointPos = _newDashboardTables._globalJoint;
		final double origGlobalJointPos = _origDashboardTables._globalJoint;
		final PlanDataRow globalJointPosDataRow = new PlanDataRow(caseDir, "GlobalJointPOS", /* pvId= */null,
				/* grandOrd= */Model._WildCard, /* sotName= */null, /* sotInt= */Model._WildCard, newGlobalJointPos,
				origGlobalJointPos, /* checkEval= */isEvalRun, /* checkOptn= */false);
		addToIfValid(logger, globalJointPosDataRow, planDataRows);

		final double newGlobalObjProb = _newDashboardTables._globalObjectProbability;
		final double origGlobalObjProb = _origDashboardTables._globalObjectProbability;
		final PlanDataRow globalObjectProbabilityDataRow = new PlanDataRow(caseDir, "GlobalObjectProbability",
				/* pvId= */null, /* grandOrd= */Model._WildCard, /* sotName= */null, /* sotInt= */Model._WildCard,
				newGlobalObjProb, origGlobalObjProb, /* checkEval= */true, /* checkOptn= */false);
		addToIfValid(logger, globalObjectProbabilityDataRow, planDataRows);

		final double newGlobalRemProb = _newDashboardTables._globalRemainingProbability;
		final double origGlobalRemProb = _origDashboardTables._globalRemainingProbability;
		final PlanDataRow globalRemainingProbabilityDataRow = new PlanDataRow(caseDir, "GlobalRemainingProbability",
				/* pvId= */null, /* grandOrd= */Model._WildCard, /* sotName= */null, /* sotInt= */Model._WildCard,
				newGlobalRemProb, origGlobalRemProb, /* checkEval= */isEvalRun, /* checkOptn= */false);
		addToIfValid(logger, globalRemainingProbabilityDataRow, planDataRows);

		/** Do the individual PatternVariables. */
		for (final String pvId : allPvIds.keySet()) {
			final int grandOrd = allPvIds.get(pvId);
			final boolean frozen = frozenPvIds.contains(pvId);
			final DashboardTables.PttrnVblStructure newPvStruct = _newDashboardTables._idToPttrnVblStructure.get(pvId);
			final DashboardTables.PttrnVblStructure origPvStruct = _origDashboardTables._idToPttrnVblStructure
					.get(pvId);

			final double newSelectedPos = newPvStruct == null ? 0d : newPvStruct._selectedPos;
			final double origSelectedPos = origPvStruct == null ? 0d : origPvStruct._selectedPos;
			final PlanDataRow selectedPosDataRow = new PlanDataRow(caseDir, "SelectedPOS", pvId, grandOrd,
					/* sotName= */null, /* sotInt= */Model._WildCard, newSelectedPos, origSelectedPos,
					/* checkEval= */frozen, /* checkOptn= */false);
			addToIfValid(logger, selectedPosDataRow, planDataRows);

			final double newArea = newPvStruct == null ? 0d : newPvStruct._area;
			final double origArea = origPvStruct == null ? 0d : origPvStruct._area;
			final PlanDataRow areaDataRow = new PlanDataRow(caseDir, "Area", pvId, grandOrd, /* sotName= */null,
					/* sotInt= */Model._WildCard, newArea, origArea, /* checkEval= */frozen, /* checkOptn= */false);
			addToIfValid(logger, areaDataRow, planDataRows);

			final double newJointPos = newPvStruct == null ? 0d : newPvStruct._jointPos;
			final double origJointPos = origPvStruct == null ? 0d : origPvStruct._jointPos;
			final PlanDataRow jointPosDataRow = new PlanDataRow(caseDir, "JointPOS", pvId, grandOrd, /* sotName= */null,
					/* sotInt= */Model._WildCard, newJointPos, origJointPos, /* checkEval= */frozen,
					/* checkOptn= */false);
			addToIfValid(logger, jointPosDataRow, planDataRows);

			final double newObjProb = newPvStruct == null ? 0d : newPvStruct._objectProbability;
			final double origObjProb = origPvStruct == null ? 0d : origPvStruct._objectProbability;
			final PlanDataRow objectProbabilityDataRow = new PlanDataRow(caseDir, "ObjectProbability", pvId, grandOrd,
					/* sotName= */null, /* sotInt= */Model._WildCard, newObjProb, origObjProb, /* checkEval= */true,
					/* checkOptn= */false);
			addToIfValid(logger, objectProbabilityDataRow, planDataRows);

			final double newTs = newPvStruct == null ? 0d : newPvStruct._ts;
			final double origTs = origPvStruct == null ? 0d : origPvStruct._ts;
			final PlanDataRow tsDataRow = new PlanDataRow(caseDir, "Ts", pvId, grandOrd, /* sotName= */null,
					/* sotInt= */Model._WildCard, newTs, origTs, /* checkEval= */true, /* checkOptn= */false);
			addToIfValid(logger, tsDataRow, planDataRows);

			for (final int sotInt : sotIntToName.keySet()) {
				final DashboardTables.LittlePvStructure newLittlePvStruct = newPvStruct == null ? null
						: newPvStruct._sotIntToLittlePvStructure.get(sotInt);
				final DashboardTables.LittlePvStructure origLittlePvStruct = origPvStruct == null ? null
						: origPvStruct._sotIntToLittlePvStructure.get(sotInt);

				final double newLittleCondPos = newLittlePvStruct == null ? 0d
						: newLittlePvStruct._littleConditionalPos;
				final double origLittleCondPos = origLittlePvStruct == null ? 0d
						: origLittlePvStruct._littleConditionalPos;
				final String sotName = sotIntToName.get(sotInt);
				final PlanDataRow littleConditionalPosDataRow = new PlanDataRow(caseDir, "JointPOS", pvId, grandOrd,
						sotName, sotInt, newLittleCondPos, origLittleCondPos, /* checkEval= */frozen,
						/* checkOptn= */false);
				addToIfValid(logger, littleConditionalPosDataRow, planDataRows);

				final double newLittleCoverage = newLittlePvStruct == null ? 0d : newLittlePvStruct._littleCoverage;
				final double origLittleCoverage = origLittlePvStruct == null ? 0d : origLittlePvStruct._littleCoverage;
				final PlanDataRow littleCoverageDataRow = new PlanDataRow(caseDir, "Coverage", pvId, grandOrd, sotName,
						sotInt, newLittleCoverage, origLittleCoverage, /* checkEval= */frozen, /* checkOptn= */false);
				addToIfValid(logger, littleCoverageDataRow, planDataRows);

				final double newLittleJointPos = newLittlePvStruct == null ? 0d : newLittlePvStruct._littleJointPos;
				final double origLittleJointPos = origLittlePvStruct == null ? 0d : origLittlePvStruct._littleJointPos;
				final PlanDataRow littleJointPosDataRow = new PlanDataRow(caseDir, "JointPOS", pvId, grandOrd, sotName,
						sotInt, newLittleJointPos, origLittleJointPos, /* checkEval= */frozen, /* checkOptn= */false);
				addToIfValid(logger, littleJointPosDataRow, planDataRows);

				final double newLittleObjProb = newLittlePvStruct == null ? 0d
						: newLittlePvStruct._littleObjectProbability;
				final double origLittleObjProb = origLittlePvStruct == null ? 0d
						: origLittlePvStruct._littleObjectProbability;
				final PlanDataRow littleObjectProbabilityDataRow = new PlanDataRow(caseDir, "ObjectProbability", pvId,
						grandOrd, sotName, sotInt, newLittleObjProb, origLittleObjProb, /* checkEval= */frozen,
						/* checkOptn= */false);
				addToIfValid(logger, littleObjectProbabilityDataRow, planDataRows);

				final double newSw = newLittlePvStruct == null ? 0d : newLittlePvStruct._littleSw;
				final double origSw = origLittlePvStruct == null ? 0d : origLittlePvStruct._littleSw;
				final PlanDataRow littleSwDataRow = new PlanDataRow(caseDir, "SW", pvId, grandOrd, sotName, sotInt,
						newSw, origSw, /* checkEval= */frozen, /* checkOptn= */false);
				addToIfValid(logger, littleSwDataRow, planDataRows);
			}
		}

		for (final int sotInt : sotIntToName.keySet()) {
			final String sotName = sotIntToName.get(sotInt);
			final DashboardTables.SotStructure newSotStruct = _newDashboardTables._sotIntToSrchObjctStructure
					.get(sotInt);
			final DashboardTables.SotStructure origSotStruct = _origDashboardTables._sotIntToSrchObjctStructure
					.get(sotInt);

			final double newCondPos = newSotStruct == null ? 0d : newSotStruct._conditionalPos;
			final double origCondPos = origSotStruct == null ? 0d : origSotStruct._conditionalPos;
			final PlanDataRow conditionalPosDataRow = new PlanDataRow(caseDir, "ConditionalPOS", /* pvId= */null,
					/* grandOrd= */Model._WildCard, sotName, sotInt, newCondPos, origCondPos, /* checkEval= */isEvalRun,
					/* checkOptn= */false);
			addToIfValid(logger, conditionalPosDataRow, planDataRows);

			final double newJointPos = newSotStruct == null ? 0d : newSotStruct._jointPosSot;
			final double origJointPos = origSotStruct == null ? 0d : origSotStruct._jointPosSot;
			final PlanDataRow jointPosDataRow = new PlanDataRow(caseDir, "JointPOS", /* pvId= */null,
					/* grandOrd= */Model._WildCard, sotName, sotInt, newJointPos, origJointPos,
					/* checkEval= */isEvalRun, /* checkOptn= */false);
			addToIfValid(logger, jointPosDataRow, planDataRows);

			final double newLittleNetGain = newSotStruct == null ? 0d : newSotStruct._netGainSot;
			final double origLittleNetGain = origSotStruct == null ? 0d : origSotStruct._netGainSot;
			final PlanDataRow littleNetGainDataRow = new PlanDataRow(caseDir, "NetGain", /* pvId= */null,
					/* grandOrd= */Model._WildCard, sotName, sotInt, newLittleNetGain, origLittleNetGain,
					/* checkEval= */isEvalRun, /* checkOptn= */false);
			addToIfValid(logger, littleNetGainDataRow, planDataRows);

			final double newLittleObjProb = newSotStruct == null ? 0d : newSotStruct._objectProbability;
			final double origLittleObjProb = origSotStruct == null ? 0d : origSotStruct._objectProbability;
			final PlanDataRow littleObjectProbabilityDataRow = new PlanDataRow(caseDir, "ObjectProbability",
					/* pvId= */null, /* grandOrd= */Model._WildCard, sotName, sotInt, newLittleObjProb,
					origLittleObjProb, /* checkEval= */isEvalRun, /* checkOptn= */false);
			addToIfValid(logger, littleObjectProbabilityDataRow, planDataRows);

			final double newLittleRemProb = newSotStruct == null ? 0d : newSotStruct._remainingProbability;
			final double origLittleRemProb = origSotStruct == null ? 0d : origSotStruct._remainingProbability;
			final PlanDataRow littleRemainingProbabilityDataRow = new PlanDataRow(caseDir, "RemainingProbability",
					/* pvId= */null, /* grandOrd= */Model._WildCard, sotName, sotInt, newLittleRemProb,
					origLittleRemProb, /* checkEval= */isEvalRun, /* checkOptn= */false);
			addToIfValid(logger, littleRemainingProbabilityDataRow, planDataRows);
		}
		_planDataRows = planDataRows.toArray(new PlanDataRow[planDataRows.size()]);
	}

	private static void addToIfValid(final MyLogger logger, final PlanDataRow planDataRow,
			final ArrayList<PlanDataRow> planDataRows) {
		boolean haveProblem = false;
		for (int iPass = 0; iPass < 2; ++iPass) {
			final boolean newProblem = iPass == 0;
			if (Double.isNaN(newProblem ? planDataRow._newValue : planDataRow._origValue)) {
				logger.wrn(String.format("Nan found for field %s in %s(%s)", planDataRow._fieldName,
						planDataRow._caseDir, newProblem ? "New" : "Orig"));
				haveProblem = true;
			}
		}
		if (!haveProblem) {
			planDataRows.add(planDataRow);
		}
	}

	public File getCaseDir() {
		return _caseDir;
	}
}
