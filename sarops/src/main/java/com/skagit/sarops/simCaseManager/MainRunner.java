package com.skagit.sarops.simCaseManager;

/**
 * Simple class that provides one main, that receives, as its first
 * argument, the name of the class containing the actual main function to
 * run, and passes the rest of its arguments to that class' main.
 */
public class MainRunner {
	/**
	 * We no longer redirect err and out. In fact, we don't use System.err and
	 * System.out. We use just MyLogger and the console appender is only used
	 * within the debugging environment.
	 */
	/**
	 * @param args args[0] is the full class name of the class containing the
	 *             main to be run, and the remaining Strings in args are
	 *             simply passed to this function. We add the capability of
	 *             redefining the output and error streams after the class
	 *             name.
	 */
	public static void main(final SimCaseManager simCaseManager,
			final String[] args) {
		/**
		 * To simplify debugging, we don't use reflection. For SAROPS, we have
		 * only a few classes that we're interested in and we do a simple
		 * if-then to get the right one.
		 */
		final String[] runnerArgs = new String[args.length - 1];
		for (int i = 1; i < args.length; ++i) {
			runnerArgs[i - 1] = args[i];
		}
		final String rawClassName = args[0];
		simCaseManager.queueSimCase(rawClassName, runnerArgs);
	}

	/** simCase can be null in this call. */
	public static void HandleFatal(final SimCaseManager.SimCase simCase,
			final RuntimeException e) {
		if (simCase != null) {
			/**
			 * This is the only place where inFatalHandler should be SET to true.
			 * Setting it to false induces an infinite loop.
			 */
			simCase.runOutChunks(/* inFatalHandler= */true);
		}
		SimCaseManager.standardLogError(simCase, e, "HandleFatal");
		throw e;
	}
}
