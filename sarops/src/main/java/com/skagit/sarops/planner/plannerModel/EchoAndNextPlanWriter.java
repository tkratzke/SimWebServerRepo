package com.skagit.sarops.planner.plannerModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.w3c.dom.Element;

import com.skagit.sarops.model.Sortie;
import com.skagit.sarops.model.io.ModelReader;
import com.skagit.sarops.model.io.ModelReader.StringPlus;
import com.skagit.sarops.model.io.ModelWriter;
import com.skagit.sarops.planner.plannerModel.pvSeq.PvSeq;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.planner.posFunction.pvValueArrayPlus.PvValueArrayPlus;
import com.skagit.sarops.simCaseManager.MainRunner;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.tracker.lrcSet.LrcSet;
import com.skagit.sarops.util.patternUtils.MyStyle;
import com.skagit.sarops.util.patternUtils.SphericalTimedSegs;
import com.skagit.util.Constants;
import com.skagit.util.Graphics;
import com.skagit.util.LsFormatter;
import com.skagit.util.StaticUtilities;
import com.skagit.util.TimeUtilities;
import com.skagit.util.colorUtils.ColorUtils;
import com.skagit.util.navigation.LatLng3;

public class EchoAndNextPlanWriter {
	private static long _UnsetTime = ModelReader._UnsetTime;

	final PlannerModel _plannerModel;

	public EchoAndNextPlanWriter(final PlannerModel plannerModel) {
		_plannerModel = plannerModel;
	}

	void writeEchoFiles(final SimCaseManager.SimCase simCase,
			final TreeSet<StringPlus> stringPluses) {
		try {
			final LsFormatter formatter = new LsFormatter();
			final Element planElt = formatter.newElement("PLAN");
			writePlanEltCore(simCase, planElt, /* forNextEval= */false);
			final int nPvSeqs = _plannerModel.getNPvSeqs();
			for (int pvSeqOrd = 0; pvSeqOrd < nPvSeqs; ++pvSeqOrd) {
				final PvSeq pvSeq = _plannerModel.getPvSeq(pvSeqOrd);
				final Element pvSeqElt =
						formatter.newChild(planElt, "PATTERN_VARIABLE_SEQUENCE");
				writePvSeqCore(pvSeq, pvSeqElt);
				final int nMine = pvSeq.getNMyPttrnVbls();
				for (int ordWithinPvSeq = 0; ordWithinPvSeq < nMine;
						++ordWithinPvSeq) {
					final PatternVariable pv = pvSeq.getPttrnVbl(ordWithinPvSeq);
					final Element pvElt =
							formatter.newChild(pvSeqElt, "PATTERN_VARIABLE");
					pvElt.setAttribute("ordinal", "" + pv.getOrdWithinPvSeq());
					writePvEltUsingPvValue(/* pvValues= */null, pv.getGrandOrd(),
							formatter, pvElt, /* writeEval= */false);
				}
			}
			final int nPttrnVbls = _plannerModel.getNPttrnVbls();
			for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
				final PatternVariable pv = _plannerModel.grandOrdToPv(grandOrd);
				final PvSeq pvSeq = pv.getPvSeq();
				if (pvSeq != null) {
					continue;
				}
				final Element pvElt =
						formatter.newChild(planElt, "PATTERN_VARIABLE");
				writePvEltUsingPvValue(/* pvValues= */null, pv.getGrandOrd(),
						formatter, pvElt, /* writeEval= */false);
			}
			/** Dump it to the output directory */
			final String[] echoFileNames = _plannerModel.getEchoFileNames();
			final File echoDir = new File(echoFileNames[0]);
			final File echoFile = new File(echoDir, echoFileNames[1]);
			try (final FileOutputStream fos = new FileOutputStream(echoFile)) {
				formatter.dumpWithTimeComment(planElt, fos);
			}

			/** Dump the fancy SimProperties dump. */
			if (stringPluses != null) {
				final File echoPropertiesFile = new File(echoDir, echoFileNames[2]);
				try (final PrintStream ps = new PrintStream(echoPropertiesFile)) {
					for (final ModelReader.StringPlus stringPlus : stringPluses) {
						ps.print(stringPlus.getString() + Constants._NewLine);
					}
				} catch (final Exception e) {
					MainRunner.HandleFatal(simCase, new RuntimeException(e));
				}
			}
		} catch (final Exception e) {
			MainRunner.HandleFatal(simCase, new RuntimeException(e));
		}
	}

	public void writeNextPlans(final SimCaseManager.SimCase simCase,
			final PvValueArrayPlus plus, final String nextPlanPath,
			final String nextEvalPath) {
		for (int iPass = 0; iPass < 2; ++iPass) {
			final boolean writeEval = iPass == 1;
			try {
				final LsFormatter formatter = new LsFormatter();
				final Element planElt = formatter.newElement("PLAN");
				writePlanEltCore(simCase, planElt, /* forNextEval= */writeEval);
				final PvValue[] pvValues = plus.getCopyOfPvValues();
				final int nPvSeqs = _plannerModel.getNPvSeqs();
				for (int pvSeqOrd = 0; pvSeqOrd < nPvSeqs; ++pvSeqOrd) {
					final PvSeq pvSeq = _plannerModel.getPvSeq(pvSeqOrd);
					final Element pvSeqElt =
							formatter.newChild(planElt, "PATTERN_VARIABLE_SEQUENCE");
					final String durationString =
							LsFormatter.StandardFormat(pvSeq._totalDurationSecs / 60d) +
									" mins";
					pvSeqElt.setAttribute("totalEndurance", durationString);
					if (pvSeq._launchRefSecs != _UnsetTime) {
						final String launchString = TimeUtilities
								.formatTime(pvSeq._launchRefSecs, /* includeSecs= */false);
						pvSeqElt.setAttribute("launchTime", launchString);
					} else {
						final String pvSeqCstString = TimeUtilities.formatTime(
								pvSeq._pvSeqCstRefSecs, /* includeSecs= */false);
						pvSeqElt.setAttribute("launchTime", pvSeqCstString);
					}
					final double launchLat = pvSeq._launchLatLng.getLat();
					final double launchLng = pvSeq._launchLatLng.getLng();
					pvSeqElt.setAttribute("launchLat",
							LsFormatter.StandardFormatForLatOrLng(launchLat));
					pvSeqElt.setAttribute("launchLng",
							LsFormatter.StandardFormatForLatOrLng(launchLng));
					pvSeqElt.setAttribute("pvSeqID", pvSeq._id);
					if (pvSeq.hasRecoveryTransit()) {
						final String recoveryKtsString =
								LsFormatter.StandardFormat(pvSeq._recoveryKts) + " kts";
						pvSeqElt.setAttribute("recoverySpeed", recoveryKtsString);
						final String recoveryLatString = LsFormatter
								.StandardFormatForLatOrLng(pvSeq._recoveryLatLng.getLat());
						pvSeqElt.setAttribute("recoveryLat", recoveryLatString);
						final String recoveryLngString = LsFormatter
								.StandardFormatForLatOrLng(pvSeq._recoveryLatLng.getLng());
						pvSeqElt.setAttribute("recoveryLng", recoveryLngString);
					}
					final int nMine = pvSeq.getNMyPttrnVbls();
					for (int ordWithinPvSeq = 0; ordWithinPvSeq < nMine;
							++ordWithinPvSeq) {
						final PatternVariable pv = pvSeq.getPttrnVbl(ordWithinPvSeq);
						writePvEltUsingPvValue(pvValues, pv.getGrandOrd(), formatter,
								pvSeqElt, writeEval);
					}
				}

				/** Now write the stand-alones. */
				final int nPttrnVbls = _plannerModel.getNPttrnVbls();
				for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
					final PatternVariable pv = _plannerModel.grandOrdToPv(grandOrd);
					if (pv.getPvSeq() == null) {
						writePvEltUsingPvValue(pvValues, grandOrd, formatter, planElt,
								writeEval);
					}
				}

				/** Dump it. */
				final String path = writeEval ? nextEvalPath : nextPlanPath;
				final File f = new File(path);
				final File dir = f.getParentFile();
				if (StaticUtilities.makeDirectory(dir) != null) {
					try (final FileOutputStream fos = new FileOutputStream(f)) {
						formatter.dumpWithTimeComment(planElt, fos);
					}
				}
			} catch (final Exception e) {
				SimCaseManager.err(simCase, e.getMessage());
				MainRunner.HandleFatal(simCase, new RuntimeException(e));
			}
		}
	}

	private void writePlanEltCore(final SimCaseManager.SimCase simCase,
			final Element planElt, final boolean forNextEval) {
		planElt.setAttribute(ModelReader._XmlSimPropertiesFilePathAtt,
				_plannerModel.getXmlSimiPropertiesFilePath());
		planElt.setAttribute("particleFile",
				_plannerModel.getParticlesFilePath());
		planElt.setAttribute("startTime",
				TimeUtilities.formatTime(_plannerModel.getMinPvRefSecs(), false));
		planElt.setAttribute("endTime",
				TimeUtilities.formatTime(_plannerModel.getMaxPvRefSecs(), false));
		planElt.setAttribute("mode",
				_plannerModel.getErf() ? "ideal" : "normal");
		planElt.setAttribute("searchForLandedParticles",
				_plannerModel.includeLanded() ? "true" : "false");
		planElt.setAttribute("searchForAdriftParticles",
				_plannerModel.includeAdrift() ? "true" : "false");
		final int plannerTimeSecs =
				forNextEval ? 0 : _plannerModel.getPlannerTimeSecs();
		final int maxNJumpsWithNoImprovement =
				(int) Math.round(forNextEval ? 0d : simCase.getSimPropertyDouble(
						"PLAN.maxNJumpsWithNoImprovement", 10000d));
		planElt.setAttribute("plannerTimeInSeconds",
				"" + plannerTimeSecs + " secs");
		planElt.setAttribute("maxNJumpsWithNoImprovement",
				"" + maxNJumpsWithNoImprovement);
		if (!forNextEval) {
			planElt.setAttribute(PlannerModel._LatestSeedAttributeName,
					"" + _plannerModel.getLatestSeed());
		}
	}

	private static void writePvSeqCore(final PvSeq pvSeq,
			final Element pvSeqElt) {
		pvSeqElt.setAttribute("sortieID", pvSeq._id);
		pvSeqElt.setAttribute("recoverySpeed",
				String.format("%.2f kts", pvSeq._recoveryKts));
		pvSeqElt.setAttribute("totalEndurance",
				String.format("%f mins", pvSeq._totalDurationSecs / 60d));
		if (pvSeq._launchLatLng != null) {
			pvSeqElt.setAttribute("launchLat",
					String.format("%.7f", pvSeq._launchLatLng.getLat()));
			pvSeqElt.setAttribute("launchLng",
					String.format("%.7f", pvSeq._launchLatLng.getLng()));
			if (pvSeq._launchRefSecs > 0) {
				pvSeqElt.setAttribute("launchTime", TimeUtilities
						.formatTime(pvSeq._launchRefSecs, /* includeSecs= */true));
			}
		}
		if (pvSeq._recoveryLatLng != null && pvSeq._recoveryKts > 0d) {
			pvSeqElt.setAttribute("recoveryLat",
					String.format("%.7f", pvSeq._recoveryLatLng.getLat()));
			pvSeqElt.setAttribute("recoveryLng",
					String.format("%.7f", pvSeq._recoveryLatLng.getLng()));
			pvSeqElt.setAttribute("recoverySpeed",
					String.format("%.2f kts", pvSeq._recoveryKts));
		}
	}

	private void writePvEltUsingPvValue(final PvValue[] pvValues,
			final int grandOrd, final LsFormatter formatter,
			final Element parentElt, final boolean writeEval) {
		final Element pvElt = formatter.newChild(parentElt, "PATTERN_VARIABLE");
		final PatternVariable pv = _plannerModel.grandOrdToPv(grandOrd);
		final PvSeq pvSeq = pv.getPvSeq();
		final PvValue pvValue = pvValues == null ? null : pvValues[grandOrd];
		final PatternKind patternKind = pv.getPatternKind();

		/** The attributes of pvElt itself. */
		final String pvId = pv.getId();
		pvElt.setAttribute("id", pvId);
		pvElt.setAttribute("name", pv.getName());
		pvElt.setAttribute("type", pv.getPvType()._xmlString);
		final double rawSearchKts = pv.getRawSearchKts();
		pvElt.setAttribute("searchSpeed", rawSearchKts + " kts");
		final double minTsNmi = pv.getMinTsNmi();

		final PvValue frozenPvValue = pv.getPermanentFrozenPvValue();
		final boolean writeBox =
				!writeEval && frozenPvValue == null && patternKind != null;
		if (writeBox) {
			pvElt.setAttribute("minimumTrackSpacing", minTsNmi + " NM");
			pvElt.setAttribute("patternKind", patternKind.outsideName());
		}
		if (pvSeq != null) {
			final double transitKts = pv.getTransitKts();
			pvElt.setAttribute("transitSpeed", transitKts + " kts");
			pvElt.setAttribute("ordinal", "" + pv.getOrdWithinPvSeq());
		} else {
			/** Stand-alone. */
			final long cstRefSecs = pv.getPvCstRefSecs();
			final String cstString = TimeUtilities.formatTime(cstRefSecs, false);
			pvElt.setAttribute("cst", cstString);
			final int searchDurationSecs = pv.getPvRawSearchDurationSecs();
			final int durationMins = (int) Math.round(searchDurationSecs / 60d);
			final String durationString = durationMins + " mins";
			pvElt.setAttribute("duration", durationString);
		}
		final double excBufferNmi = pv.getExcBufferNmi();
		pvElt.setAttribute("separationBuffer", excBufferNmi + " NM");

		/** Add the Viz elements. */
		final Map<Integer, LrcSet> viz1LrcSets = pv.getViz1LrcSets();
		final Map<Integer, LrcSet> viz2LrcSets = pv.getViz2LrcSets();
		final Set<Integer> viz2ObjectTypeIds = viz2LrcSets.keySet();
		final Iterator<Map.Entry<Integer, LrcSet>> viz1LrcSetIt =
				viz1LrcSets.entrySet().iterator();
		while (viz1LrcSetIt.hasNext()) {
			final Map.Entry<Integer, LrcSet> entry = viz1LrcSetIt.next();
			final int objectTypeId = entry.getKey();
			final Element soElt = formatter.newChild(pvElt, "COMP_OBJECT_TYPE");
			soElt.setAttribute("id", "" + objectTypeId);
			final LrcSet lrcSet = entry.getValue();
			final boolean viz2 = viz2ObjectTypeIds.contains(objectTypeId);
			soElt.setAttribute("isActive", viz2 ? "true" : "false");
			lrcSet.write(formatter, soElt);
		}

		/** Add the overlap exception elements. */
		for (final String[] overlapException : _plannerModel
				.getOverlapExceptions()) {
			final String id0 = overlapException[0];
			if (id0.equals(pvId)) {
				final String id1 = overlapException[1];
				final PatternVariable pv1 = _plannerModel.getPv(id1);
				final Element ovlExcElt =
						formatter.newChild(pvElt, "OVERLAP_EXCEPTION");
				ovlExcElt.setAttribute("pattern_variable",
						String.format("%s_%s", pv1.getName(), pv1.getId()));
				ovlExcElt.setAttribute("may_overlap", overlapException[2]);
			}
		}

		/** Add the initial PvValue. */
		if (writeBox) {
			/** Put box information into initConfigElt. */
			final Element initConfigElt = formatter.newChild(pvElt, "BOX");
			if (pvValue != null) {
				final MyStyle myStyle = pvValue.getMyStyle();
				writeMyStyle(pv, myStyle, initConfigElt);
				/** Attach graphics from this PvValue to the Graphics Element. */
				final LatLng3[] tsLooseCorners =
						pvValue.getLooseLatLngArray(SphericalTimedSegs.LoopType.TS);
				final Sortie sortie = pvValue.getSortie();
				final Element graphicsElt =
						formatter.newChild(parentElt, "GRAPHICS");
				graphicsElt.setAttribute("Pattern_Variable", pv.getNameId());
				attachGraphics(formatter, graphicsElt, pv, tsLooseCorners, sortie);
			}
		} else {
			/** top is TrackLine. Write the waypoints. */
			final Element trackLineElt = formatter.newChild(pvElt, "TRACKLINE");
			final PvValue trackLinePvValue =
					frozenPvValue != null ? frozenPvValue : pvValue;
			ModelWriter.attachWaypoints(formatter, trackLineElt,
					trackLinePvValue.getSortie());
			/** Add the creepHdg if it exists. */
			final double creepHdg = trackLinePvValue.getCreepHdg();
			if (0d <= creepHdg && creepHdg < 360d) {
				trackLineElt.setAttribute("creepDirection",
						String.format("%s degrees clockwise from north",
								LsFormatter.StandardFormat(creepHdg)));
			}
			final double tsNmi = trackLinePvValue.computeTsNmi();
			if (tsNmi > 0d) {
				trackLineElt.setAttribute("trackSpacing",
						String.format("%s NM", LsFormatter.StandardFormat(tsNmi)));
			}
		}
	}

	private static void writeMyStyle(final PatternVariable pv,
			final MyStyle myStyle, final Element myStyleElt) {
		final PatternKind patternKind = pv.getPatternKind();
		myStyleElt.setAttribute("onMars", myStyle.onMars() ? "true" : "false");
		if (myStyle.onMars()) {
			return;
		}
		final double rawSearchKts = pv.getRawSearchKts();
		final double minTsNmi = pv.getMinTsNmi();
		final LatLng3 center = myStyle.getCenter();
		final double orntn = myStyle.computeOrntn(rawSearchKts, minTsNmi);
		final boolean firstTurnRight = myStyle.getFirstTurnRight();
		myStyleElt.setAttribute("orientation",
				orntn + " degrees clockwise from north");
		myStyleElt.setAttribute("centerPointLat",
				LsFormatter.StandardFormatForLatOrLng(center.getLat()));
		myStyleElt.setAttribute("centerPointLng",
				LsFormatter.StandardFormatForLatOrLng(center.getLng()));
		/** We write out the length and width of the tsBox, not the specBox. */
		final double lengthNmi =
				myStyle.computeLengthNmi(rawSearchKts, minTsNmi);
		myStyleElt.setAttribute("length",
				LsFormatter.StandardFormat(lengthNmi) + " NM");
		final double absTsNmi = myStyle.computeTsNmi(rawSearchKts, minTsNmi);
		final String s = LsFormatter.StandardFormat(absTsNmi) + " NM";
		myStyleElt.setAttribute("trackSpacing", s);
		myStyleElt.setAttribute("firstTurnRight",
				firstTurnRight ? "true" : "false");
		if (patternKind.isPsCs()) {
			final double widthNmi =
					myStyle.computeWidthNmi(rawSearchKts, minTsNmi);
			myStyleElt.setAttribute("width",
					LsFormatter.StandardFormat(widthNmi) + " NM");
			myStyleElt.setAttribute("pathType",
					myStyle.computePs(rawSearchKts, minTsNmi) ? "PS" : "CS");
		}
		if (patternKind.isVs()) {
			/** Nothing else to add. */
		}
		if (patternKind.isSs()) {
			/** Length is already done. Nothing else to add. */
		}
		if (pv.getPvSeq() != null) {
			/** Add the attributes that only apply for PvSeq PatternVariables. */
			final long cstRefSecs = myStyle.getCstRefSecs();
			myStyleElt.setAttribute("cst",
					TimeUtilities.formatTime(cstRefSecs, false));
			final int searchDurationSecs = myStyle.getSearchDurationSecs();
			final int durationMins = (int) Math.round(searchDurationSecs / 60d);
			myStyleElt.setAttribute("duration", durationMins + " mins");
		}
	}

	private static void attachGraphics(final LsFormatter formatter,
			final Element graphicsElement, final PatternVariable pv,
			final LatLng3[] tsLooseCorners, final Sortie sortie) {
		final List<Sortie.Leg> legs = sortie.getDistinctInputLegs();
		final int nLegs = legs.size();
		final int grandOrd = pv.getGrandOrd();
		final String patternColorName =
				ColorUtils.getForegroundColorName(grandOrd);
		for (int k = 0; k < nLegs; ++k) {
			final Sortie.Leg leg = legs.get(k);
			final LatLng3 latLng0 = leg.getLegLatLng0();
			final LatLng3 latLng1 = leg.getLegLatLng1();
			final double lat0 = latLng0.getLat();
			final double lng0 = latLng0.getLng();
			final double lat1 = latLng1.getLat();
			final double lng1 = latLng1.getLng();
			final Element patternGcaElement = formatter.newChild(graphicsElement,
					Graphics.GraphicsType.LL_EDGE.name());
			patternGcaElement.setAttribute("lat0", String.format("%.7f", lat0));
			patternGcaElement.setAttribute("lng0", String.format("%.7f", lng0));
			patternGcaElement.setAttribute("lat1", String.format("%.7f", lat1));
			patternGcaElement.setAttribute("lng1", String.format("%.7f", lng1));
			if (k % 4 == 0) {
				patternGcaElement.setAttribute("number", "" + k);
			}
			patternGcaElement.setAttribute("color", patternColorName);
		}
		final int nLooseCorners =
				tsLooseCorners == null ? 0 : tsLooseCorners.length;
		for (int k = 0; k < nLooseCorners; ++k) {
			final LatLng3 latLng0 = tsLooseCorners[k];
			final LatLng3 latLng1 = tsLooseCorners[(k + 1) % nLooseCorners];
			final double lat0 = latLng0.getLat();
			final double lng0 = latLng0.getLng();
			final double lat1 = latLng1.getLat();
			final double lng1 = latLng1.getLng();
			final Element patternGcaElement = formatter.newChild(graphicsElement,
					Graphics.GraphicsType.LL_EDGE.name());
			patternGcaElement.setAttribute("lat0", String.format("%.7f", lat0));
			patternGcaElement.setAttribute("lng0", String.format("%.7f", lng0));
			patternGcaElement.setAttribute("lat1", String.format("%.7f", lat1));
			patternGcaElement.setAttribute("lng1", String.format("%.7f", lng1));
			patternGcaElement.setAttribute("number", "" + k);
			patternGcaElement.setAttribute("color", "Magenta");
		}
	}
}
