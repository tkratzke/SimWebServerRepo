/**
 *
 */
package com.skagit.sarops.simWebServer;

import java.io.IOException;

import com.skagit.sarops.MainSaropsObject;
import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.StaticUtilities;
import com.skagit.util.StringUtilities;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RunCaseServlet extends MyHttpServlet {

	final private SimCaseManager _simCaseManager;
	final private static long serialVersionUID = 1L;

	public RunCaseServlet(final String idString, final SimCaseManager simCaseManager) {
		super(idString);
		_simCaseManager = simCaseManager;
	}

	@Override
	protected void doGet(final HttpServletRequest request, final HttpServletResponse resp)
			throws ServletException, IOException {
		doPost(request, resp);
	}

	@Override
	protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
			throws ServletException, IOException {

		final String timeReceivedString = StaticUtilities.getDateString(System.currentTimeMillis());
		final String rawClassName = request.getParameter("class");
		final String filePathX = request.getParameter("file");
		final String filePath = StringUtilities.cleanUpFilePath(filePathX);
		final String simCaseName = _simCaseManager.getReadableSimCaseName(rawClassName, filePath);
		final String concludeString = request.getParameter("conclude");
		final String s = String.format("%s Posted[%s] SimCase[%s] %s (%s)", getIdString(), timeReceivedString,
				simCaseName, concludeString == null ? "" : concludeString, filePath);
		_simCaseManager._globalLogger.out(s);
		if (!AbstractServletHelper.SendRedirectAdmin()) {
			/**
			 * For running regular SimWebServer, it would be nice if the following works,
			 * but since it does not, we comment it out.
			 */
			// response.sendRedirect("GetStatus");
		} else {
			/** For running any "pocket" SimWebServer: */
			response.sendRedirect("Admin");
		}
		if (filePath == null) {
			return;
		}
		/** Determine if the conclude parameter is set. */
		boolean tryingToConclude = concludeString != null;
		if (tryingToConclude) {
			final String lc = concludeString.toLowerCase();
			tryingToConclude = lc.startsWith("y") || lc.startsWith("t");
		}
		if (tryingToConclude) {
			final SimCaseManager.SimCase[] simCases = _simCaseManager.getRecentSimCasesInQueue();
			for (final SimCaseManager.SimCase simCase : simCases) {
				final String filePathY = simCase.getCleanedUpFilePath();
				if (filePathY.compareToIgnoreCase(filePath) != 0) {
					continue;
				}
				if (simCase.getProgressState() != SimCaseManager.ProgressState.ACTIVE) {
					break;
				}
				final MainSaropsObject mainSaropsObject = simCase.getMainSaropsObject();
				if (mainSaropsObject == null || !(mainSaropsObject instanceof Planner)) {
					break;
				}
				final Planner planner = (Planner) mainSaropsObject;
				planner.tellPlannerToSit();
				break;
			}
		} else {
			_simCaseManager.queueSimCase(rawClassName, new String[] {
					filePath
			});
		}
	}
}
