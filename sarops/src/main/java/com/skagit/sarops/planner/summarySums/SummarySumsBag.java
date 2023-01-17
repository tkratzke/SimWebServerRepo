package com.skagit.sarops.planner.summarySums;

import java.util.Map;
import java.util.TreeMap;

import com.skagit.sarops.planner.posFunction.PosFunction;
import com.skagit.util.Constants;

public class SummarySumsBag implements Constants {
	public static class SummarySumsMapPlus {
		final public PosFunction _posFunction;
		final public Map<String, SummarySums> _summarySumsMap;
		final public int _nToWorkWith;
		final public double _sumOfNewWeights;
		final public double _muHat;
		final public double _varOfEstmt;

		public SummarySumsMapPlus(final PosFunction posFunction,
				final TreeMap<String, SummarySums> summarySumsMap,
				final int nToWorkWith, final double sumOfNewWeights,
				final double muHat, final double varOfEstmt) {
			_posFunction = posFunction;
			_summarySumsMap = summarySumsMap;
			_nToWorkWith = nToWorkWith;
			_sumOfNewWeights = sumOfNewWeights;
			_muHat = muHat;
			_varOfEstmt = varOfEstmt;
		}
	}

	public static String getKey(final String sortieId,
			final Integer objectType) {
		String returnValue = sortieId == null ? "AllSorties" : sortieId;
		returnValue += Constants._SectionSymbol;
		returnValue += (objectType != null ? ("SO[" + objectType + "]")
				: "AllObjectTypes");
		return returnValue;
	}
}
