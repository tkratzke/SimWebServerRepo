package com.skagit.sarops.simWebServer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;

import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.util.SimGlobalStrings;
import com.skagit.util.DirsTracker;
import com.skagit.util.TimeUtilities;
import com.skagit.util.myThreadPool.MyThreadPool;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class SimWebServer {
	final private SimCaseManager _simCaseManager;
	final private Server _server;

	private SimWebServer(final int portNumber) {
		System.setProperty("org.eclipse.jetty.util.log.class",
				"org.eclipse.jetty.util.log.StdErrLog");
		System.setProperty("org.eclipse.jetty.LEVEL", "OFF");
		_simCaseManager = new SimCaseManager();

		/** Create the Server... */
		_server = new Server(portNumber) {
			@Override
			public void handle(final HttpChannel connection)
					throws IOException, ServletException {
				final Request request = connection.getRequest();
				final Response response = connection.getResponse();
				if ("TRACE".equals(request.getMethod())) {
					request.setHandled(true);
					response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
				} else {
					super.handle(connection);
				}
			}
		};
		/**
		 * ... and the ServletContextHandler for adding servlets, as well as the
		 * SecurityHandler to disable tracing.
		 */
		final ServletContextHandler servletContextHandler =
				new ServletContextHandler(_server, "/",
						ServletContextHandler.SESSIONS);

		/** Disable servletContextHandler's Trace method. */
		SecurityHandler securityHandler =
				servletContextHandler.getSecurityHandler();
		if (securityHandler == null) {
			securityHandler = new ConstraintSecurityHandler();
			servletContextHandler.setSecurityHandler(securityHandler);
		}
		if (securityHandler instanceof ConstraintSecurityHandler) {
			final ConstraintSecurityHandler constraintSecurityHandler =
					(ConstraintSecurityHandler) securityHandler;
			final ConstraintMapping disableTraceMapping = new ConstraintMapping();
			final Constraint disableTraceConstraint = new Constraint();
			disableTraceConstraint.setName("Disable TRACE");
			disableTraceConstraint.setAuthenticate(true);
			disableTraceMapping.setConstraint(disableTraceConstraint);
			disableTraceMapping.setPathSpec("/");
			disableTraceMapping.setMethod("TRACE");
			constraintSecurityHandler.addConstraintMapping(disableTraceMapping);

			final ConstraintMapping enableEverythingButTraceMapping =
					new ConstraintMapping();
			final Constraint enableEverythingButTraceConstraint =
					new Constraint();
			enableEverythingButTraceConstraint
					.setName("Enable everything but TRACE");
			enableEverythingButTraceMapping
					.setConstraint(enableEverythingButTraceConstraint);
			enableEverythingButTraceMapping
					.setMethodOmissions(new String[] { "TRACE" });
			enableEverythingButTraceMapping.setPathSpec("/");
			constraintSecurityHandler
					.addConstraintMapping(enableEverythingButTraceMapping);
		}

		final TreeMap<String, MyHttpServlet> myHttpServlets = new TreeMap<>();

		/** Create the RunCaseServlet. */
		myHttpServlets.put("RunCase",
				new RunCaseServlet("RunCase", _simCaseManager));

		/** Create the CatchAll Servlets. */
		myHttpServlets.put("Admin",
				new CatchAllServlet("Admin", _simCaseManager, false, false));
		myHttpServlets.put("GetStatus",
				new CatchAllServlet("GetStatus", _simCaseManager, true, false));
		myHttpServlets.put("Cancel",
				new CatchAllServlet("Cancel", _simCaseManager, false, true));

		/**
		 * Create the ShutDown Servlet. It's more convenient NOT to make this
		 * its separate class.
		 */
		myHttpServlets.put("ShutDown", new MyHttpServlet("ShutDown") {
			final private static long serialVersionUID = 1L;

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
				final Thread t = new Thread(new Runnable() {
					@Override
					public void run() {
						final SimCaseManager.SimCase[] simCases =
								_simCaseManager.getRecentSimCasesInQueue();
						for (final SimCaseManager.SimCase simCase : simCases) {
							if (simCase
									.getProgressState() == SimCaseManager.ProgressState.ACTIVE) {
								if (simCase.runStudy()) {
									return;
								}
							}
						}
						_simCaseManager.shutDown("SWS Shutdown");
						try {
							_server.stop();
						} catch (final Exception e) {
						}
					}
				});
				MyThreadPool.adjustPriority(t, +4);
				t.start();
			}
		});

		/** Create the extra servlets. */
		myHttpServlets
				.putAll(AbstractServletHelper.CreateExtraServlets(_simCaseManager));

		/** Add the Servlets. */
		final int nServlets = myHttpServlets.size();
		final Iterator<Map.Entry<String, MyHttpServlet>> it =
				myHttpServlets.entrySet().iterator();
		for (int k = 0; k < nServlets; ++k) {
			final Map.Entry<String, MyHttpServlet> entry = it.next();
			final String uri = "/" + entry.getKey();
			final MyHttpServlet servlet = entry.getValue();
			servletContextHandler.addServlet(new ServletHolder(servlet), uri);
		}

		_simCaseManager._globalLogger.out("Created Servlets.");
	}

	private void runServer() {
		try {
			_server.start();
			_simCaseManager._globalLogger.out("Did server.start().");
		} catch (final Exception e) {
			e.printStackTrace();
		}
		try {
			_simCaseManager._globalLogger.out("Starting server.join().");
			_server.join();
			_simCaseManager._globalLogger.out("Did server.join().");
		} catch (final InterruptedException e) {
			e.printStackTrace();
		}
	}

	void stopServer() {
		try {
			_server.stop();
		} catch (final Exception e) {
		}
	}

	public static void main(final String[] args) {
		final String globalLogFileName =
				String.format("%s+%s.txt", SimGlobalStrings.getStaticVersionName(),
						TimeUtilities.formatNowForFileName());
		final File f = DirsTracker.getLogFile(globalLogFileName);
		if (f == null) {
			System.exit(41);
		}
		int portNumber = -1;
		try (final PrintStream printStream =
				new PrintStream(new FileOutputStream(f))) {
			String s = String.format(
					"Started SimWebServer.main(String[] args), %s.  Args are:",
					SimGlobalStrings.getStaticVersionName());
			final int nArgs = args.length;
			for (int iArg = 0; iArg < nArgs; ++iArg) {
				s += String.format("\n\t%s", args[iArg]);
			}
			DirsTracker.logStream(printStream, s + "\n");
			portNumber = Integer.parseInt(args[0]);
		} catch (final IOException e) {
		}
		final SimWebServer simWebServer = new SimWebServer(portNumber);
		simWebServer.runServer();
	}
}
