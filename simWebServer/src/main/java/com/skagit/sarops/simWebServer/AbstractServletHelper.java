package com.skagit.sarops.simWebServer;

import java.util.TreeMap;

import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.AbstractToImpl;
import com.skagit.util.StaticUtilities;

abstract public class AbstractServletHelper {
	final private static AbstractServletHelper _Singleton;
	static {
		final AbstractServletHelper singleton = (AbstractServletHelper) AbstractToImpl
				.GetImplObject(StaticUtilities.getMyClass());
		if (singleton != null) {
			_Singleton = singleton;
		} else {
			_Singleton = new AbstractServletHelper() {

				@Override
				protected boolean sendRedirectAdmin() {
					return false;
				}

				@Override
				protected TreeMap<String, MyHttpServlet> createExtraServlets(final SimCaseManager simCaseManager) {
					return new TreeMap<>();
				}

				@Override
				protected String addToTableString(final String s, final SimCaseManager simCaseManager,
						final boolean statusOnly) {
					return s;
				}
			};
		}
	}

	abstract protected boolean sendRedirectAdmin();

	abstract protected TreeMap<String, MyHttpServlet> createExtraServlets(final SimCaseManager simCaseManager);

	abstract protected String addToTableString(final String s, final SimCaseManager simCaseManager,
			final boolean statusOnly);

	public static TreeMap<String, MyHttpServlet> CreateExtraServlets(final SimCaseManager simCaseManager) {
		return _Singleton.createExtraServlets(simCaseManager);
	}

	public static String AddToTableString(final String s, final SimCaseManager simCaseManager,
			final boolean statusOnly) {
		return _Singleton.addToTableString(s, simCaseManager, statusOnly);
	}

	public static boolean SendRedirectAdmin() {
		return _Singleton.sendRedirectAdmin();
	}

}
