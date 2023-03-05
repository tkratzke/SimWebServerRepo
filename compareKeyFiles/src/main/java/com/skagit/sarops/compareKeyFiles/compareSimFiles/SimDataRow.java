package com.skagit.sarops.compareKeyFiles.compareSimFiles;

import java.util.ArrayList;

import com.skagit.util.Constants;
import com.skagit.util.Ellipse;
import com.skagit.util.Ellipse2;
import com.skagit.util.MathX;
import com.skagit.util.cdf.BivariateNormalCdf;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.geometry.gcaSequence.Loop3Statics;
import com.skagit.util.geometry.loopsFinder.LoopsFinder;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.TangentCylinder;
import com.skagit.util.randomx.WeightedPairReDataAcc;

class SimDataRow implements Comparable<SimDataRow> {
	final private static double _NmiToR = MathX._NmiToR;

	final Treatment _treatment;
	final int _nOrig;
	final LatLng3 _origCenter;
	final LatLng3 _newCenter;
	final double _orig50SmiMjrNmi;
	final double _orig50SmiMnrNmi;
	final double _orig50SmiMjrHdg;
	final int _nNew;
	final double _new50SmiMjrNmi;
	final double _new50SmiMnrNmi;
	final double _new50SmiMjrHdg;
	final double _nmiBetweenCenters;
	final double _newInOrigCntnmnt;
	final double _origInNewCntnmnt;
	final double _intersectionScore;

	public SimDataRow(final MyLogger logger, final Treatment treatment,
			final ArrayList<LatLngWt> origLatLngWts,
			final ArrayList<LatLngWt> newLatLngWts) {
		_treatment = treatment;
		TangentCylinder origTc = null;
		TangentCylinder newTc = null;
		WeightedPairReDataAcc origAcc = null;
		WeightedPairReDataAcc newAcc = null;
		int nOrig = 0;
		int nNew = 0;
		for (int iPass = 0; iPass < 2; ++iPass) {
			final ArrayList<LatLngWt> latLngWts =
					iPass == 0 ? origLatLngWts : newLatLngWts;
			final int nLatLngWts = latLngWts == null ? 0 : latLngWts.size();
			final LatLng3[] latLngs = new LatLng3[nLatLngWts];
			final double[] wts = new double[nLatLngWts];
			for (int k = 0; k < nLatLngWts; ++k) {
				final LatLngWt latLngWt = latLngWts.get(k);
				latLngs[k] = LatLng3.getLatLngB(latLngWt._lat, latLngWt._lng);
				wts[k] = latLngWt._wt;
			}
			final WeightedPairReDataAcc acc = new WeightedPairReDataAcc();
			final TangentCylinder tc = nLatLngWts > 0 ?
					TangentCylinder.getTangentCylinder(latLngs, wts) : null;
			for (int k = 0; k < nLatLngWts; ++k) {
				final TangentCylinder.FlatLatLng flatLatLng =
						tc.convertToMyFlatLatLng(latLngs[k]);
				final double eastNmi = flatLatLng.getEastOffsetNmi();
				final double northNmi = flatLatLng.getNorthOffsetNmi();
				final double wt = wts[k];
				acc.add(eastNmi, northNmi, wt);
			}
			if (iPass == 0) {
				origTc = tc;
				origAcc = acc;
				nOrig = nLatLngWts;
			} else {
				newTc = tc;
				newAcc = acc;
				nNew = nLatLngWts;
			}
		}
		_nOrig = nOrig;
		_nNew = nNew;

		/** Set the centers. */
		_origCenter = origTc == null ? null : origTc.getCentralFlatLatLng();
		_newCenter = newTc == null ? null : newTc.getCentralFlatLatLng();

		final BivariateNormalCdf origBvn =
				origAcc.createBivariateNormalCdf(logger);
		if (origBvn != null && origBvn.hasSpread()) {
			final double[] orig50SmiMjrSmiMnrSmiMjrHdg =
					origBvn.get50SmiMjrSmiMnrSmiMjrHdg();
			_orig50SmiMjrNmi = orig50SmiMjrSmiMnrSmiMjrHdg[0];
			_orig50SmiMnrNmi = orig50SmiMjrSmiMnrSmiMjrHdg[1];
			_orig50SmiMjrHdg = orig50SmiMjrSmiMnrSmiMjrHdg[2];
		} else {
			_orig50SmiMjrNmi = _orig50SmiMnrNmi = -1d;
			_orig50SmiMjrHdg = 361d;
		}
		final BivariateNormalCdf newBvn =
				newAcc.createBivariateNormalCdf(logger);
		if (newBvn != null && newBvn.hasSpread()) {
			final double[] new50SmiMjrSmiMnrSmiMjrHdg =
					newBvn.get50SmiMjrSmiMnrSmiMjrHdg();
			_new50SmiMjrNmi = new50SmiMjrSmiMnrSmiMjrHdg[0];
			_new50SmiMnrNmi = new50SmiMjrSmiMnrSmiMjrHdg[1];
			_new50SmiMjrHdg = new50SmiMjrSmiMnrSmiMjrHdg[2];
		} else {
			_new50SmiMjrNmi = _new50SmiMnrNmi = -1d;
			_new50SmiMjrHdg = 361d;
		}

		/** Set the containments and distance. */
		if (origBvn != null && origBvn.hasSpread() && _newCenter != null) {
			final TangentCylinder.FlatLatLng newInOrig =
					origTc.convertToMyFlatLatLng(_newCenter);
			final double xForNewInOrig = newInOrig.getEastOffsetNmi();
			final double yForNewInOrig = newInOrig.getNorthOffsetNmi();
			_newInOrigCntnmnt =
					origBvn.getContainmentProportion(xForNewInOrig, yForNewInOrig);
		} else {
			_newInOrigCntnmnt = 1d;
		}
		if (newBvn != null && newBvn.hasSpread() && _origCenter != null) {
			final TangentCylinder.FlatLatLng origInNew =
					newTc.convertToMyFlatLatLng(_origCenter);
			final double xForOrigInNew = origInNew.getEastOffsetNmi();
			final double yForOrigInNew = origInNew.getNorthOffsetNmi();
			_origInNewCntnmnt =
					newBvn.getContainmentProportion(xForOrigInNew, yForOrigInNew);
		} else {
			_origInNewCntnmnt = 1d;
		}
		if (_origCenter != null && _newCenter != null) {
			_nmiBetweenCenters =
					MathX.haversineX(_origCenter, _newCenter) / _NmiToR;
		} else {
			_nmiBetweenCenters = Double.POSITIVE_INFINITY;
		}

		final Loop3 origCcwLoop = computeCcw50Ellipse(/* orig= */true);
		final Loop3 newCcwLoop = computeCcw50Ellipse(/* orig= */false);
		/** Set waterWins to true to get the intersection. */
		final double areaOfIntersection;
		if (origCcwLoop == null || newCcwLoop == null) {
			areaOfIntersection = 0d;
		} else {
			final ArrayList<Loop3> intersectionLoops =
					LoopsFinder.findLoopsFromLoops(/* _logger= */null,
							new Loop3[] { origCcwLoop, newCcwLoop },
							/* waterWins= */true);
			if (intersectionLoops == null || intersectionLoops.size() != 1) {
				areaOfIntersection = 0d;
			} else {
				final Loop3 intersection = intersectionLoops.get(0);
				areaOfIntersection = intersection.getSqNmi();
			}
		}
		/** Set waterWins to false to get the union. */
		final double areaOfUnion;
		if (origCcwLoop == null) {
			areaOfUnion = newCcwLoop != null ? newCcwLoop.getSqNmi() : 0d;
		} else if (newCcwLoop == null) {
			areaOfUnion = origCcwLoop.getSqNmi();
		} else {
			final ArrayList<Loop3> unionLoops = LoopsFinder.findLoopsFromLoops(
					/* _logger= */null, new Loop3[] { origCcwLoop, newCcwLoop },
					/* waterWins= */false);
			if (unionLoops == null) {
				areaOfUnion = 0d;
			} else {
				double ttlUnion = 0d;
				for (final Loop3 unionLoop : unionLoops) {
					ttlUnion += unionLoop.getSqNmi();
				}
				areaOfUnion = ttlUnion;
			}
		}
		final double ratio =
				areaOfUnion > 0d ? areaOfIntersection / areaOfUnion : 0d;
		_intersectionScore = 1d - ratio;
	}

	public boolean isVacuous() {
		return !(_newInOrigCntnmnt >= 0d) || !(_origInNewCntnmnt >= 0d) ||
				!(_nmiBetweenCenters >= 0d);
	}

	public String getString() {
		final String treatmentString = _treatment.getTreatmentName1();
		final String origSummary = //
				String.format(
						"OrigCtr%s OrigSMjr[%.2f] OrigSMnr[%.2f] OrigSMjrHdg[%.2f] n[%d]", //
						_origCenter.getString(), //
						_orig50SmiMjrNmi, _orig50SmiMnrNmi, _orig50SmiMjrHdg, _nOrig);
		final String newSummary = //
				String.format(
						"NewCtr%s NewSMjr[%.2f] NewSMnr[%.2f] NewSMjrHdg[%.2f] n[%d]", //
						_newCenter.getString(), //
						_new50SmiMjrNmi, _new50SmiMnrNmi, _new50SmiMjrHdg, _nNew);
		final String resultsString =
				String.format("aInB[%.3f%%] bInA[%.3f%%] ||%.3fNM|| **%f**",
						_origInNewCntnmnt * 100d, _newInOrigCntnmnt * 100d,
						_nmiBetweenCenters, _intersectionScore);
		return String.format("%s\n%s\n%s\n%s", //
				treatmentString, origSummary, newSummary, resultsString);
	}

	@Override
	public int compareTo(final SimDataRow dataRow) {
		if (dataRow == null) {
			return 1;
		}
		return _treatment.compareTo(dataRow._treatment);
	}

	public Loop3 computeCcw50Ellipse(final boolean orig) {
		final LatLng3 center;
		final double smiMjrNmi, smiMnrNmi, smiMjrHdg;
		if (orig) {
			center = _origCenter;
			smiMjrNmi = _orig50SmiMjrNmi;
			smiMnrNmi = _orig50SmiMnrNmi;
			smiMjrHdg = _orig50SmiMjrHdg;
		} else {
			center = _newCenter;
			smiMjrNmi = _new50SmiMjrNmi;
			smiMnrNmi = _new50SmiMnrNmi;
			smiMjrHdg = _new50SmiMjrHdg;
		}
		if (center == null || !(smiMjrNmi > 0d) || !(smiMnrNmi > 0d) ||
				!(0d <= smiMjrHdg && smiMjrHdg < 360d)) {
			return null;
		}
		final Ellipse ellipse = new Ellipse2(smiMjrNmi, smiMnrNmi, 16);

		/**
		 * <pre>
		 * If smiMjrHdg is 75, we must rotate smiMjrNmi from (1,0) to
		 * (cos(15),sin(15)). To do this, we find the coordinates of (1,0)
		 * with respect to the basis:
		 * 	[
		 * 		(cos(-15),sin(-15)),
		 * 		(cos(75),sin(75))
		 * ].
		 * </pre>
		 */
		final double theta = -Math.toRadians(90d - smiMjrHdg);
		final double b00 = MathX.cosX(theta);
		final double b01 = MathX.sinX(theta);
		final double b10 = -b01;
		final double b11 = b00;
		final double[][] fullCycle = ellipse.getFullCycle();
		final int nPoints = fullCycle.length;
		final LatLng3[] latLngArray = new LatLng3[nPoints];
		final TangentCylinder tc = TangentCylinder.getTangentCylinder(center);
		final double nmiToR = Constants.Functions.nmiToR(center.getLat());
		for (int k = 0; k < nPoints; ++k) {
			final double x = fullCycle[k][0];
			final double y = fullCycle[k][1];
			final double eastOffset = (x * b00 + y * b01) * nmiToR;
			final double northOffset = (x * b10 + y * b11) * nmiToR;
			latLngArray[k] = tc.new FlatLatLng(eastOffset, northOffset);
		}

		/** Build a ccw loop. */
		final int ccwFlag =
				Loop3Statics.createGenericFlag(/* isClockwise= */false);
		final Loop3 loop = Loop3.getLoop(/* _logger= */null, /* id= */0,
				/* subId= */0, ccwFlag, /* ancestorId= */-1, latLngArray,
				/* logChanges= */false, /* debug= */false);
		return loop;
	}

}