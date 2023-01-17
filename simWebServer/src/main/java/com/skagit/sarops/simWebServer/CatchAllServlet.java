package com.skagit.sarops.simWebServer;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

import com.skagit.sarops.MainSaropsObject;
import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.planner.posFunction.PosFunction;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.planner.posFunction.pvValueArrayPlus.PvValueArrayPlus;
import com.skagit.sarops.planner.solver.OptimizationEvent;
import com.skagit.sarops.planner.solver.SolversManager;
import com.skagit.sarops.planner.solver.pqSolver.PqSolver;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.util.SimGlobalStrings;
import com.skagit.sarops.util.patternUtils.MyStyle;
import com.skagit.sarops.util.patternUtils.SphericalTimedSegs;
import com.skagit.util.StaticUtilities;
import com.skagit.util.StringUtilities;
import com.skagit.util.TimeUtilities;
import com.skagit.util.geometry.gcaSequence.GcaSequenceStatics;
import com.skagit.util.navigation.LatLng3;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Excellent site for html experimentation:
 * http://www.w3schools.com/html/html_tables.asp
 */
public class CatchAllServlet extends MyHttpServlet {
	final private static long serialVersionUID = 1L;
	static final private boolean _DumpExc = true;

	final private SimCaseManager _simCaseManager;
	final private boolean _statusOnly;
	final private boolean _cancel;
	static int _counter = 0;

	CatchAllServlet(final String idString,
			final SimCaseManager simCaseManager, final boolean statusOnly,
			final boolean cancel) {
		super(idString);
		_simCaseManager = simCaseManager;
		_statusOnly = statusOnly;
		_cancel = cancel;
	}

	@Override
	protected void doGet(final HttpServletRequest request,
			final HttpServletResponse response)
			throws ServletException, IOException {
		doPost(request, response);
	}

	@Override
	protected void doPost(final HttpServletRequest request,
			final HttpServletResponse response)
			throws ServletException, IOException {
		final String timeReceivedString =
				StaticUtilities.getDateString(System.currentTimeMillis());
		response.setContentType("text/html");
		// response.setHeader("Pragma", "no-cache");
		// response.setHeader("Cache-Control", "no-cache");
		// response.setHeader("Expires", "0");
		response.setStatus(HttpServletResponse.SC_OK);
		/** Find out where we are. */
		final String nowString = TimeUtilities.formatNow();
		final int[] nWaitingActiveDoneDropped =
				_simCaseManager.getNWaitingActiveDoneDropped();
		final int nWaiting = nWaitingActiveDoneDropped[0];
		final int nActive = nWaitingActiveDoneDropped[1];
		final int nDone = nWaitingActiveDoneDropped[2];
		final int nDropped = nWaitingActiveDoneDropped[3];
		final int nEngines = SimCaseManager.getNEngines();
		String s1 = "";
		s1 += String.format(
				"Waiting[%d] Active[%d] Capacity[%d] Done[%d] Dropped[%d]",
				nWaiting, nActive, nEngines, nDone, nDropped);
		final String requestFilePath = request.getParameter("file");
		final boolean noCaseSpecified = requestFilePath == null;
		final String cleanedUpRequestFilePath =
				StringUtilities.cleanUpFilePath(requestFilePath);
		SimCaseManager.SimCase matchingSimCase = null;
		if (!noCaseSpecified) {
			matchingSimCase =
					_simCaseManager.getSimCase(cleanedUpRequestFilePath);
			if (matchingSimCase == null) {
				final String thisS =
						String.format("RequestFilePath[%s] does not match!",
								cleanedUpRequestFilePath);
				s1 += String.format("<br/>Ts", thisS);
				_simCaseManager._globalLogger.out(thisS);
			}
		}
		final SimCaseManager.SimCase[] simCasesInQueue =
				_simCaseManager.getRecentSimCasesInQueue();
		for (final SimCaseManager.SimCase simCase : simCasesInQueue) {
			final String rawClassName = simCase._rawClassName;
			final String filePath = simCase.getCleanedUpFilePath();
			final String displayName = simCase.getNameForDisplay();
			/**
			 * If no input file is specified, or an input file is specified and
			 * matches this one, print it out.
			 */
			if (noCaseSpecified || simCase == matchingSimCase) {
				if (_statusOnly || (_cancel && matchingSimCase != null)) {
					if (_cancel) {
						final boolean ignored = false;
						simCase.stopIfGoing(ignored);
					} else {
						final String statusString =
								simCase.getProgressState().getString();
						s1 += String.format(
								"<br>RawClassName[%s] FileName[%s] Status[%s]", //
								rawClassName, filePath, statusString);
					}
				} else {
					final String statusString =
							simCase.getProgressState().getString();
					s1 += String.format(
							"<br>RawClassName[%s] DisplayName[%s] Status[%s]",
							rawClassName, displayName, statusString);
				}
				final int[] progressStepsInfo = simCase.getNProgressStepsInfo();
				final int nProgressSteps = progressStepsInfo[0];
				final int nProgressStepsDone = progressStepsInfo[1];
				s1 += String.format(" progressIndex[%d] of [%d]",
						nProgressStepsDone, nProgressSteps);
				s1 += String.format(" at [%s]", nowString);
				final MainSaropsObject mainSaropsObject =
						simCase.getMainSaropsObject();
				if (mainSaropsObject != null &&
						mainSaropsObject instanceof Planner) {
					final Planner planner = (Planner) mainSaropsObject;
					final PlannerModel plannerModel = planner.getPlannerModel();
					s1 += "<br/>";
					if (plannerModel != null) {
						final OptimizationEvent bestOptimizationEvent;
						final OptimizationEvent baseLineOptimizationEvent;
						final int nJumpsSinceLastImprovement;
						final int secondsRemaining;
						final SolversManager solversManager =
								planner.getSolversManager();
						if (solversManager != null) {
							final PqSolver pqSolver = solversManager.getPqSolver();
							if (pqSolver != null) {
								final PatternVariable[] activeSet =
										plannerModel.getActiveSet();
								bestOptimizationEvent =
										pqSolver.getBestOptimizationEvent(activeSet);
								baseLineOptimizationEvent =
										pqSolver.getBaseLineOptimizationEvent(activeSet);
								nJumpsSinceLastImprovement =
										pqSolver.getNJumpsSinceLastImprovement();
								secondsRemaining = (int) (pqSolver.getMsRemaining() / 1000);
							} else {
								bestOptimizationEvent = baseLineOptimizationEvent = null;
								nJumpsSinceLastImprovement = secondsRemaining = -1;
							}
						} else {
							bestOptimizationEvent = baseLineOptimizationEvent = null;
							nJumpsSinceLastImprovement = secondsRemaining = -1;
						}
						if (bestOptimizationEvent != null) {
							final PvValueArrayPlus bestPlus =
									bestOptimizationEvent.getPlus();
							final double ovlV = bestPlus.getForReportsTtlOvlV(
									/* pv= */null, /* countTheOnesThatMayOverlap= */false);
							final double pvSeqV =
									bestPlus.getForReportsTtlPvTrnstV(/* pv= */null);
							final double ovl = bestPlus.getForReportsTtlOvlV(
									/* pv= */null, /* countTheOnesThatMayOverlap= */true);
							final PosFunction posFunction =
									planner.getPosFunctionForStatusReport();
							final double pos = bestPlus.getPos(posFunction);
							s1 += String.format(
									" ovlV[%f] ovl[%f] pvSeqV[%f] estimatedPos[%f]", //
									ovlV, ovl, pvSeqV, pos);
							final double gain;
							if (baseLineOptimizationEvent != null) {
								final PvValueArrayPlus baseLinePlus =
										baseLineOptimizationEvent.getPlus();
								final double firstPos = baseLinePlus.getPos(posFunction);
								if (baseLinePlus.isFeasible() && firstPos > 0d) {
									gain = pos / firstPos;
								} else {
									gain = 0d;
								}
							} else {
								gain = 0d;
							}
							s1 += String.format(" gain[%f]", gain);
							s1 +=
									String.format(" secondsRemaining[%d]", secondsRemaining);
							s1 += String.format(" nJumpsSinceLastImprovement[%d]",
									nJumpsSinceLastImprovement);
							if (simCase == matchingSimCase) {
								/** Here, we have a specified case, and this one is it. */
								final PvValue[] copyOfPvValueArray =
										bestPlus.getCopyOfPvValues();
								for (final PvValue pvValue : copyOfPvValueArray) {
									final PatternVariable pv = pvValue.getPv();
									final String nameId = pv.getNameId();
									final boolean isPermanentlyFrozen =
											pv.getPermanentFrozenPvValue() != null;
									if (!isPermanentlyFrozen) {
										final LatLng3[] tsTightCornersA =
												pvValue.getTightLatLngArray(
														SphericalTimedSegs.LoopType.TS);
										final LatLng3[] tsLooseCornersA =
												pvValue.getLooseLatLngArray(
														SphericalTimedSegs.LoopType.TS);
										final LatLng3[] excTightCorners =
												_DumpExc ? pvValue.getTightLatLngArray(
														SphericalTimedSegs.LoopType.EXC) : null;
										final MyStyle myStyle = pvValue.getMyStyle();
										final LatLng3[] tsTightCorners;
										final LatLng3[] tsLooseCorners;
										if (myStyle != null) {
											if (!myStyle.getFirstTurnRight()) {
												tsTightCorners = tsTightCornersA;
												tsLooseCorners = tsLooseCornersA;
											} else {
												tsTightCorners = tsTightCornersA.clone();
												GcaSequenceStatics.flipLatLngList(
														Arrays.asList(tsTightCorners),
														/* tgtLatLngList= */null, /* fromLoop= */true);
												tsLooseCorners = tsLooseCornersA.clone();
												GcaSequenceStatics.flipLatLngList(
														Arrays.asList(tsLooseCorners),
														/* tgtLatLngList= */null, /* fromLoop= */true);
											}
										} else {
											tsTightCorners = tsTightCornersA;
											tsLooseCorners = tsLooseCornersA;
										}
										final int nTsTight =
												tsTightCorners == null ? 0 : tsTightCorners.length;
										final int nExcTight = excTightCorners == null ? 0 :
												excTightCorners.length;
										if (nExcTight > 0) {
											s1 += String.format(
													"<br/>{<br/> PttrnVbl[%s] %s nPoints[%d] nExcPoints[%d]",
													nameId,
													MyStyle.legacyGetTheirStyleString(
															pv.getRawSearchKts(), pv.getMinTsNmi(),
															pvValue.getMyStyle()),
													nTsTight, nExcTight);
										} else {
											s1 += String.format(
													"<br/>{<br/> PttrnVbl[%s] %s nPoints[%d]", nameId,
													MyStyle.legacyGetTheirStyleString(
															pv.getRawSearchKts(), pv.getMinTsNmi(),
															pvValue.getMyStyle()),
													nTsTight);
										}
										final double pvOvlV = bestPlus.getForReportsTtlOvlV(pv,
												/* countTheOnesThatMayOverlap= */false);
										final double pvOvl = bestPlus.getForReportsTtlOvlV(pv,
												/* countTheOnesThatMayOverlap= */true);
										final double pvPvSeqV =
												bestPlus.getForReportsTtlPvTrnstV(pv);
										s1 += String.format(
												" ovlV[%f NM] ovl[%f NM] pvSeqV[%f Mins]", //
												pvOvlV, pvOvl, pvPvSeqV);
										for (int iPass = 0; iPass < (nExcTight > 0 ? 3 : 2);
												++iPass) {
											final LatLng3[] corners;
											if (iPass == 0) {
												corners = tsLooseCorners;
											} else if (iPass == 1) {
												corners = tsTightCorners;
											} else {
												corners = excTightCorners;
											}
											final int nPoints =
													corners == null ? 0 : corners.length;
											for (int k = 0; k < nPoints; ++k) {
												final LatLng3 corner = corners[k];
												final double lat = corner.getLat();
												final double lng = corner.getLng();
												final String name;
												if (iPass == 0) {
													name = Planner._CornerNamesIf4[k];
												} else if (iPass == 1) {
													name = String.format("TsTightPoint-%d", k);
												} else {
													name = String.format("ExcTightPoint-%d", k);
												}
												if (k % 2 == 0) {
													s1 += "<br/>";
												}
												final String f1 = " %s-lat[%f] %s-lng[%f]";
												s1 += String.format(f1, name, lat, name, lng);
											}
										}
										s1 += "<br/>}";
									}
								}
							}
						}
					}
				}
			}
		}
		try (PrintWriter responsePrintWriter = response.getWriter()) {
			if (_statusOnly) {
				responsePrintWriter.print(s1);
				_simCaseManager.cleanUpQueue(_statusOnly, cleanedUpRequestFilePath,
						getIdString());
				return;
			}
			final int secondsToRefresh;
			if (nActive > 0) {
				secondsToRefresh = 5;
			} else {
				secondsToRefresh = 60;
			}
			responsePrintWriter
					.printf("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.0 ");
			responsePrintWriter.printf("Transitional//EN\">\n");
			responsePrintWriter.printf("<HTML>\n");
			responsePrintWriter.printf("<HEAD>\n");
			responsePrintWriter.printf(
					"<META HTTP-EQUIV=\"Refresh\" CONTENT=\"%d\"\\>\n",
					secondsToRefresh);
			responsePrintWriter.printf("<TITLE>Available Cases: %d</TITLE>\n",
					_counter++);
			responsePrintWriter.printf("</HEAD>\n");
			responsePrintWriter.printf("<BODY>\n");
			final String fullVersionName =
					SimGlobalStrings.getFullStaticVersionName();
			responsePrintWriter.printf("<H1>%s", fullVersionName);
			responsePrintWriter.printf("<H2>Or ");
			responsePrintWriter.printf("<a href=\"ShutDown\">Shut Down</a>, ");
			responsePrintWriter
					.printf("<a href=\"CleanCases\">Clean Cases</a>, or ");
			responsePrintWriter
					.printf("<a href=\"Flush\">Flush Cached Planners</a>.");
			responsePrintWriter.print("</H2>");
			String s = "";
			if (nActive > 0) {
				String tableString = "<table border=\"1\" bordercolor=\"#FFCC00\" ";
				tableString += "style=\"background-color:#FFFFCC\" ";
				tableString +=
						"width=\"400\" cellpadding=\"3\" cellspacing=\"3\">\n";
				tableString += "<tr>\n";
				tableString += String.format(
						"<td>%s</td><td>nActive[%d]</td>\n<td>nWaiting[%d]</td>",
						timeReceivedString, nActive, nWaiting);
				int caseNumber = 0;
				for (final SimCaseManager.SimCase simCase : simCasesInQueue) {
					if (simCase
							.getProgressState() != SimCaseManager.ProgressState.ACTIVE) {
						continue;
					}
					final String simpleClassName = simCase._rawClassName
							.substring(simCase._rawClassName.lastIndexOf('.') + 1);
					final String displayName = simCase.getNameForDisplay();
					final String engineName = simCase._engineName;
					if (caseNumber++ > 0) {
						tableString += "<tr>\n";
					}
					tableString += "<tr>\n";
					tableString += String.format("  <td>%s</td>", simpleClassName);
					tableString += String.format("<td>%s</td>\n", displayName);
					tableString += String.format("<td>%s</td>\n",
							engineName == null ? "-" : engineName);
					if (caseNumber == 0) {
						tableString += String.format("<td>%d</td>\n", nWaiting);
					}
					final int[] progressStepsInfo = simCase.getNProgressStepsInfo();
					final int nProgressSteps = progressStepsInfo[0];
					final int nProgressStepsDone = progressStepsInfo[1];
					s1 += String.format(" progressIndex[%d] of [%d]",
							nProgressStepsDone, nProgressSteps);
					tableString += String.format("<td>%d/%d</td>\n",
							nProgressStepsDone, nProgressSteps);
				}
				tableString += "</tr>\n";
				tableString += "</table>\n";
				s += tableString;
			}
			s = AbstractServletHelper.AddToTableString(s, _simCaseManager, _statusOnly);
			responsePrintWriter.print(s);
			responsePrintWriter.print("</BODY>\n</HTML>\n");
			final String matchingSimCaseName =
					matchingSimCase == null ? "No Match" : matchingSimCase.getName();
			if (_cancel) {
				final String m =
						String.format("%s", getIdString(), matchingSimCaseName);
				_simCaseManager.cleanUpQueue(false, requestFilePath, m);
				return;
			}
			final String m =
					String.format("%s:%s", getIdString(), matchingSimCaseName);
			_simCaseManager.cleanUpQueue(_statusOnly, cleanedUpRequestFilePath,
					m);
		} catch (final IOException e) {
		}
	}
}
