package com.skagit.sarops.compareKeyFiles.compareSimFiles;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import com.skagit.sarops.compareKeyFiles.CompareKeyFiles;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.SearchObjectType;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.ParticleIndexes;
import com.skagit.sarops.tracker.ParticlesFile;
import com.skagit.util.CombinatoricTools;
import com.skagit.util.ShortNameFinder;
import com.skagit.util.myLogger.MyLogger;

public class CompareSimFiles {

	public static void BuildSimCompareFiles(
			final SimCaseManager.SimCase simCase, final File homeDir) {
		final MyLogger logger = SimCaseManager.getLogger(simCase);
		final File[] caseDirs = gatherSimCaseDirs(simCase, homeDir);
		final int nCaseDirs = caseDirs.length;
		final TreeMap<File, String> caseDirToShortName =
				ShortNameFinder.computeCaseDirToShortName(homeDir, caseDirs);
		MyLogger.out(logger, ShortNameFinder.getString(caseDirToShortName));

		final TreeMap<File, TreeMap<Treatment, SimDataRow>> caseDirToTreatmentToDataRow =
				new TreeMap<>();
		for (int kCase = 0; kCase < nCaseDirs; ++kCase) {
			String summaryString = "";
			final File caseDir = caseDirs[kCase];
			if (kCase > 0) {
				summaryString += "\n";
			}
			final String shortName = caseDirToShortName.get(caseDir);
			summaryString += String.format("Starting %d of %d: %s(%s)", kCase,
					nCaseDirs, shortName, caseDir.getAbsolutePath());
			MyLogger.out(logger, summaryString);

			final TreeMap<Treatment, SimDataRow> treatmentToDataRow =
					new TreeMap<>();
			caseDirToTreatmentToDataRow.put(caseDir, treatmentToDataRow);
			final File[] ncFilePair = getMainAndOrigNcFiles(simCase, caseDir);
			final File mainNcFile = ncFilePair[0];
			final File origNcFile = ncFilePair[1];
			final ParticlesFile particlesFileA =
					new ParticlesFile(simCase, origNcFile.getAbsolutePath());
			final ParticlesFile particlesFileB =
					new ParticlesFile(simCase, mainNcFile.getAbsolutePath());
			final Model modelA = particlesFileA.getModel();
			final Model modelB = particlesFileB.getModel();
			if (!modelA.deepEquals(simCase, modelB,
					/* checkEnvironmentalFiles= */false)) {
				SimCaseManager.wrn(simCase,
						String.format("Case[%s] has mismatched models.", shortName));
				continue;
			}
			final long[] refSecsS_A = particlesFileA.getRefSecsS();
			final long[] refSecsS_B = particlesFileB.getRefSecsS();
			if (CombinatoricTools._ByAllInOrderL.compare(refSecsS_A,
					refSecsS_B) != 0) {
				SimCaseManager.wrn(simCase, String
						.format("Case[%s] has mismatched time arrays.", shortName));
				continue;
			}
			final TreeMap<Treatment, ArrayList<LatLngWt>> allA =
					createTreatmentToLatLngWts(caseDir, shortName, particlesFileA);
			final TreeMap<Treatment, ArrayList<LatLngWt>> allB =
					createTreatmentToLatLngWts(caseDir, shortName, particlesFileB);

			/** Ensure that allA and allB have the same Treatments. */
			final int nA0 = allA.size();
			final Iterator<Map.Entry<Treatment, ArrayList<LatLngWt>>> itA =
					allA.entrySet().iterator();
			for (int k = 0; k < nA0; ++k) {
				final Map.Entry<Treatment, ArrayList<LatLngWt>> entryA = itA.next();
				final Treatment treatment = entryA.getKey();
				final ArrayList<LatLngWt> latLngWtsB = allB.get(treatment);
				if (latLngWtsB == null) {
					allB.put(treatment, new ArrayList<LatLngWt>());
				}
			}
			final int nB1 = allB.size();
			final Iterator<Map.Entry<Treatment, ArrayList<LatLngWt>>> itB =
					allB.entrySet().iterator();
			for (int k = 0; k < nB1; ++k) {
				final Map.Entry<Treatment, ArrayList<LatLngWt>> entryB = itB.next();
				final Treatment treatment = entryB.getKey();
				final ArrayList<LatLngWt> latLngWtsA = allA.get(treatment);
				if (latLngWtsA == null) {
					allA.put(treatment, new ArrayList<LatLngWt>());
				}
			}

			/** A and B now have the same Treatments. */
			final int nTreatments = allA.size();
			final Iterator<Map.Entry<Treatment, ArrayList<LatLngWt>>> itA1 =
					allA.entrySet().iterator();
			for (int k = 0; k < nTreatments; ++k) {
				final Map.Entry<Treatment, ArrayList<LatLngWt>> entryA =
						itA1.next();
				final Treatment treatment = entryA.getKey();
				final ArrayList<LatLngWt> latLngWtsA = entryA.getValue();
				final ArrayList<LatLngWt> latLngWtsB = allB.get(treatment);
				final SimDataRow dataRow =
						new SimDataRow(logger, treatment, latLngWtsA, latLngWtsB);
				treatmentToDataRow.put(treatment, dataRow);
			}
		}

		/** Now Dump. */
		SimWriteExcelFile.simWriteExcelFile(homeDir, caseDirToShortName,
				caseDirToTreatmentToDataRow);
		SimWriteKmlFiles.simWriteKmlFiles(caseDirToShortName,
				caseDirToTreatmentToDataRow);
	}

	/**
	 * There must be exactly one matching .nc file from which we can extract a
	 * model, or else we return null.
	 */
	private static File[] getMainAndOrigNcFiles(
			final SimCaseManager.SimCase simCase, final File caseDir) {
		final FileFilter ncFilter = new FileFilter() {

			@Override
			public boolean accept(final File f) {
				return f.getName().toLowerCase().endsWith(".nc");
			}
		};
		final File[] mainNcFiles = caseDir.listFiles(ncFilter);
		final File origDir = new File(caseDir, CompareKeyFiles._OrigDirName);
		if (!origDir.isDirectory()) {
			return null;
		}
		final File[] origNcFiles = origDir.listFiles(ncFilter);
		final int nMain = mainNcFiles.length;
		final int nOrig = origNcFiles.length;
		File[] winningPair = null;
		for (int k0 = 0; k0 < nMain; ++k0) {
			final File mainNcFile = mainNcFiles[k0];
			/** Can we extract a model from this? */
			try {
				@SuppressWarnings("unused")
				final ParticlesFile particlesFile =
						new ParticlesFile(simCase, mainNcFile.getAbsolutePath());
			} catch (final Exception e) {
				continue;
			}
			final String mainNcFileNameLc = mainNcFile.getName().toLowerCase();
			for (int k1 = 0; k1 < nOrig; ++k1) {
				final File origNcFile = origNcFiles[k1];
				final String origNcFileNameLc = origNcFile.getName().toLowerCase();
				if (mainNcFileNameLc.equals(origNcFileNameLc)) {
					try {
						@SuppressWarnings("unused")
						final ParticlesFile particlesFile =
								new ParticlesFile(simCase, mainNcFile.getAbsolutePath());
					} catch (final Exception e) {
						continue;
					}
					if (winningPair != null) {
						/** We have 2. We have none. */
						return null;
					}
					winningPair = new File[] { mainNcFile, origNcFile };
				}
			}
		}
		return winningPair;
	}

	final private static int _MaxNTimeSteps = 32;

	private static TreeMap<Treatment, ArrayList<LatLngWt>> createTreatmentToLatLngWts(
			final File caseDir, final String shortName,
			final ParticlesFile particlesFile) {
		final Model model = particlesFile.getModel();
		final long[] refSecsS = particlesFile.getRefSecsS();
		final int nRefSecsS = refSecsS.length;
		final int nScenarii = particlesFile.getNScenarii();
		final int nParticlesPerScenario =
				particlesFile.getNParticlesPerScenario();
		final Collection<SearchObjectType> searchObjectTypes =
				model.getSearchObjectTypes();
		final long[] refSecsIdxS = CombinatoricTools.getFenceposts(0,
				nRefSecsS - 1, _MaxNTimeSteps, /* distinct= */true);
		final int nRefSecsIdxS = refSecsIdxS.length;
		final int nSearchObjectTypes = searchObjectTypes.size();
		final TreeMap<Treatment, ArrayList<LatLngWt>> mapOfArrayLists =
				new TreeMap<>();
		for (int iScenario = 0; iScenario < nScenarii; ++iScenario) {
			for (int iParticle = 0; iParticle < nParticlesPerScenario;
					++iParticle) {
				final ParticleIndexes prtclIndxs =
						ParticleIndexes.getStandardOne(model, iScenario, iParticle);
				for (int k0 = 0; k0 < nRefSecsIdxS; ++k0) {
					final int refSecsIdx = (int) refSecsIdxS[k0];
					final long refSecs = refSecsS[refSecsIdx];
					final int sotId =
							particlesFile.getObjectTypeId(refSecs, prtclIndxs);
					final double[] latLngPair =
							particlesFile.getLatLngPair(refSecsIdx, prtclIndxs);
					final double lat = latLngPair[0];
					final double lng = latLngPair[1];
					final double wt =
							particlesFile.getProbabilityForIdx(refSecsIdx, prtclIndxs);
					final LatLngWt latLngWt = new LatLngWt(lat, lng, wt);
					/** Add latLngWt to the relevant Treatments. */
					final Treatment[] treatments = new Treatment[4];
					Arrays.fill(treatments, null);
					/** Treatment 0 is (iSc,objTp). */
					treatments[0] = new Treatment(caseDir, shortName, model,
							iScenario, sotId, refSecsIdx, nRefSecsS, refSecs);
					if (nScenarii > 1) {
						/** Treatment 1 is (*,objTp). */
						treatments[1] = new Treatment(caseDir, shortName, model,
								Model._WildCard, sotId, refSecsIdx, nRefSecsS, refSecs);
						/** Treatments 2 and 3 are (*,objTp) and possibly (*,*). */
						treatments[2] = new Treatment(caseDir, shortName, model,
								Model._WildCard, sotId, refSecsIdx, nRefSecsS, refSecs);
						if (nSearchObjectTypes > 1) {
							treatments[3] =
									new Treatment(caseDir, shortName, model, Model._WildCard,
											Model._WildCard, nRefSecsS, refSecsIdx, refSecs);
						}
					} else if (nSearchObjectTypes > 1) {
						/** nSceanrii = 1. But here we need (iSc,*). */
						treatments[3] = new Treatment(caseDir, shortName, model,
								iScenario, Model._WildCard, refSecsIdx, nRefSecsS, refSecs);
					}
					for (int k1 = 0; k1 < 4; ++k1) {
						final Treatment treatment = treatments[k1];
						if (treatment != null) {
							ArrayList<LatLngWt> datums = mapOfArrayLists.get(treatment);
							if (datums == null) {
								datums = new ArrayList<>();
								mapOfArrayLists.put(treatment, datums);
							}
							datums.add(latLngWt);
						}
					}
				}
			}
		}
		return mapOfArrayLists;
	}

	/**
	 * Gather the caseDirs based on the presence of exactly one matching .nc
	 * file from which we can extract a model.
	 */
	private static final File[] gatherSimCaseDirs(
			final SimCaseManager.SimCase simCase, final File dir) {
		final ArrayList<File> caseDirList = new ArrayList<>();
		if (dir.isDirectory()) {
			if (dir.getName().equalsIgnoreCase("(ignore)")) {
				return new File[0];
			}
			final File[] ncFilePair = getMainAndOrigNcFiles(simCase, dir);
			if (ncFilePair != null) {
				caseDirList.add(dir);
			}
		}
		final File[] subDirs = dir.listFiles(new FileFilter() {

			@Override
			public boolean accept(final File f) {
				final String fName = f.getName();
				return f.isDirectory() && fName.length() > 0 &&
						!fName.equalsIgnoreCase("(ignore)");
			}
		});
		for (final File subDir : subDirs) {
			final File[] subSubDirs = gatherSimCaseDirs(simCase, subDir);
			caseDirList.addAll(Arrays.asList(subSubDirs));
		}
		return caseDirList.toArray(new File[caseDirList.size()]);
	}
}
