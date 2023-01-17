package com.skagit.sarops.simWebServer;

import com.skagit.util.Constants;

import jakarta.servlet.http.HttpServlet;

public class MyHttpServlet extends HttpServlet {
	final private static long serialVersionUID = 1L;
	final private String _idString;

	public MyHttpServlet(final String idString) {
		_idString = String.format("%c%s", Constants._SectionSymbol, idString);
	}

	protected String getIdString() {
		return _idString;
	}
}
