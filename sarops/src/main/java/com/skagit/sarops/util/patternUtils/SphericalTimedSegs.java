package com.skagit.sarops.util.patternUtils;

import java.util.ArrayList;
import java.util.Arrays;

import com.skagit.util.Constants;
import com.skagit.util.MathX;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.cdf.CcwGcas;
import com.skagit.util.geometry.gcaSequence.GcaSequenceStatics;
import com.skagit.util.geometry.gcaSequence.Loop3;
import com.skagit.util.geometry.gcaSequence.Loop3Statics;
import com.skagit.util.navigation.LatLng3;
import com.skagit.util.navigation.TangentCylinder;

public class SphericalTimedSegs {
	final private static double _NmiToR = MathX._NmiToR;

	public enum LoopType {
		SPEC(300), TS(300), EXC(400);

		final public int _loopId;

		LoopType(final int loopId) {
			_loopId = loopId;
		}

		final public static LoopType[] _LoopTypes = LoopType.values();
	}

	final public LatLng3 _center;
	final public LatLng3[] _path;
	final public long[] _waypointSecsS;

	/**
	 * Spec is always "loose." exc is always "tight." exc is always ccw. The
	 * rest are cw or ccw depending on firstTurnRight.
	 */
	final private LatLng3[] _looseSpecLatLngs;
	final private LatLng3[] _tightTsLatLngs;
	final private LatLng3[] _looseTsLatLngs;
	final private LatLng3[] _tightExcLatLngs;

	final private boolean _firstTurnRight;
	final private int _nLegs;
	final private int _nSearchLegs;

	final private Loop3 _tightTsLoop;
	final private CcwGcas _tightExcCcwGcas;

	/**
	 * For PsCs. The conversion from SPEC to TS has been done. The argument
	 * list includes both SPEC and TS so we can generate both the SPEC
	 * Loop3/LatLng3Array, and the TS Loop3/LatLng3Arrays.
	 */
	public SphericalTimedSegs( //
			final long baseSecs, final int searchDurationSecs, //
			final LatLng3 center, final double firstLegHdg, //
			final double specAlongNmi, final double specAcrossNmi, //
			/** The TS box' along and across: */
			final double tsAlongNmi, final double tsAcrossNmi, //
			/** The TS box' sll and sgndTs: */
			final double sllNmi, final double sgndTsNmi, //
			final double excBufferNmi) {

		/** Store _center and create _tc. */
		_center = TangentCylinder.convertToCentered(center);
		final TangentCylinder tc =
				((TangentCylinder.FlatLatLng) _center).getOwningTangentCylinder();

		/**
		 * This routine only works with perfect patterns since we depend on the
		 * following calculation of nSearchLegs, which we need to calculate
		 * ttlLegLengthPlusTs, which in turn is a critical input to the
		 * PathFinder. Note that nSearchLegs * (tsNmi0 + sllNmi) is one ts more
		 * than the total of the lengths of the legs. PsCsPathFinder will
		 * subtract that ts off before calculating the legs.
		 */
		final double tsNmi = Math.abs(sgndTsNmi);
		final int nSearchLegs = (int) Math.round(tsAcrossNmi / tsNmi);
		final double ttlLegLengthPlusTs = nSearchLegs * (tsNmi + sllNmi);
		final PsCsPathFinder pathFinderNmi =
				new PsCsPathFinder(PatternUtilStatics._TsInc, ttlLegLengthPlusTs,
						firstLegHdg, sllNmi, sgndTsNmi);
		_firstTurnRight = pathFinderNmi._sgndTs > 0d;
		_nSearchLegs = pathFinderNmi.getNSearchLegs();
		_nLegs = pathFinderNmi.getNLegs();

		/** Put the times onto the Segments. */
		final PathAndWaypointSecs pathAndWaypointSecs = new PathAndWaypointSecs(
				tc, baseSecs, searchDurationSecs, pathFinderNmi._points);
		_path = pathAndWaypointSecs._path;
		_waypointSecsS = pathAndWaypointSecs._waypointSecsS;

		/**
		 * SPEC is not buffered; appeal to center, firstLegHdg, specAlongNmi,
		 * and specAcrossNmi. We do only loose for SPEC.
		 */
		_looseSpecLatLngs = new LatLng3[4];
		final double theta = Math.toRadians(90d - firstLegHdg);
		final double s = MathX.sinX(theta);
		final double c = MathX.cosX(theta);
		final double[] b0 = new double[] { c, s };
		final double[] b1 = new double[] { -s, c };
		final double specAlongROver2 = (specAlongNmi * _NmiToR) / 2d;
		final double specAcrossROver2 = (specAcrossNmi * _NmiToR) / 2d;
		final double[] lc0, lc1, lc2, lc3;
		if (_firstTurnRight) {
			lc0 = NumericalRoutines.linearCombination(-specAlongROver2,
					specAcrossROver2, b0, b1);
			lc1 = NumericalRoutines.linearCombination(specAlongROver2,
					specAcrossROver2, b0, b1);
			lc2 = NumericalRoutines.linearCombination(specAlongROver2,
					-specAcrossROver2, b0, b1);
			lc3 = NumericalRoutines.linearCombination(-specAlongROver2,
					-specAcrossROver2, b0, b1);
		} else {
			lc0 = NumericalRoutines.linearCombination(-specAlongROver2,
					-specAcrossROver2, b0, b1);
			lc1 = NumericalRoutines.linearCombination(specAlongROver2,
					-specAcrossROver2, b0, b1);
			lc2 = NumericalRoutines.linearCombination(specAlongROver2,
					specAcrossROver2, b0, b1);
			lc3 = NumericalRoutines.linearCombination(-specAlongROver2,
					specAcrossROver2, b0, b1);
		}
		_looseSpecLatLngs[0] = tc.new FlatLatLng(lc0);
		_looseSpecLatLngs[1] = tc.new FlatLatLng(lc1);
		_looseSpecLatLngs[2] = tc.new FlatLatLng(lc2);
		_looseSpecLatLngs[3] = tc.new FlatLatLng(lc3);

		/**
		 * Do the 2 buffered ones. Start by gathering the critical points of the
		 * path.
		 */
		final double[][] unrotatedPoints = pathFinderNmi._unrotatedPoints;
		final int nPoints = unrotatedPoints.length;
		final ArrayList<double[]> criticalUnrotatedPoints = new ArrayList<>();
		/** Critical points include the first two points... */
		criticalUnrotatedPoints.add(unrotatedPoints[0]);
		if (nPoints > 1) {
			criticalUnrotatedPoints.add(unrotatedPoints[1]);
		}
		/** ... and some of the last points. */
		if (nPoints <= 4) {
			/** Grab them all. */
			if (nPoints >= 3) {
				criticalUnrotatedPoints.add(unrotatedPoints[2]);
				if (nPoints == 4) {
					criticalUnrotatedPoints.add(unrotatedPoints[3]);
				}
			}
		} else if (nPoints > 4) {
			/** Always grab the last one. */
			criticalUnrotatedPoints.add(unrotatedPoints[nPoints - 1]);
			if (nPoints % 2 == 0) {
				/** End on a search leg. */
				criticalUnrotatedPoints.add(unrotatedPoints[nPoints - 2]);
				criticalUnrotatedPoints.add(unrotatedPoints[nPoints - 4]);
			} else {
				/** End on a cross leg. */
				criticalUnrotatedPoints.add(unrotatedPoints[nPoints - 3]);
			}
		}

		/** Build TS and EXC polygons. */
		final LatLng3[][] tightTsLooseTsAndTightExc = getTightTsLooseTsTightExc(
				criticalUnrotatedPoints, tsNmi, excBufferNmi, firstLegHdg);
		_tightTsLatLngs = tightTsLooseTsAndTightExc[0];
		_looseTsLatLngs = tightTsLooseTsAndTightExc[1];
		_tightExcLatLngs = tightTsLooseTsAndTightExc[2];
		final int tsFlag =
				Loop3Statics.createGenericFlag(/* isClockwise= */_firstTurnRight);
		_tightTsLoop = Loop3.getLoop(/* _logger= */null, /* id= */0,
				/* subId= */0, tsFlag, /* ancestorId= */-1, _tightTsLatLngs,
				/* logChanges= */false, /* debug= */false);
		_tightExcCcwGcas = new CcwGcas(/* _logger= */null, _tightExcLatLngs);
	}

	/**
	 * For SS. The conversion from SPEC to TS has been done. The argument list
	 * includes both SPEC and TS so we can generate both the SPEC
	 * Loop3/LatLng3Array, and the TS Loop3/LatLng3Arrays.
	 */
	public SphericalTimedSegs( //
			final long baseSecs, final int searchDurationSecs, //
			final LatLng3 center, final double firstLegHdg, //
			final double specAcrossNmi, //
			/** The TS Box' across and sgndTs: */
			final double tsAcrossNmi, //
			final double sgndTsNmi, //
			final double excBufferNmi) {

		/** Store _center and create _tc. */
		_center = TangentCylinder.convertToCentered(center);
		final TangentCylinder tc =
				((TangentCylinder.FlatLatLng) _center).getOwningTangentCylinder();

		/**
		 * This routine only works with perfect patterns since we depend on the
		 * following calculation of nHalfLaps, which we need to calculate
		 * ttlLegLengthPlusTs, which in turn is a critical input to the
		 * PathFinder. Note that nHalfLaps * nHalfLaps * tsNmi0 is one ts more
		 * than the total of the lengths of the legs. SsPathFinder will subtract
		 * that ts off before calculating the legs.
		 */
		final double tsNmi = Math.abs(sgndTsNmi);
		final int nHalfLaps = (int) Math.round(tsAcrossNmi / tsNmi);
		final double ttlLegLengthPlusTs = nHalfLaps * nHalfLaps * tsNmi;
		final SsPathFinder pathFinderNmi =
				new SsPathFinder(PatternUtilStatics._TsInc, ttlLegLengthPlusTs,
						firstLegHdg, sgndTsNmi, /* shiftPoints= */nHalfLaps % 2 == 0);
		_firstTurnRight = pathFinderNmi._sgndTs > 0d;
		_nLegs = pathFinderNmi.getNLegs();
		_nSearchLegs = pathFinderNmi.getNSearchLegs();
		final double[][] unrotatedPointsNmi = pathFinderNmi._unrotatedPoints;
		final double[][] pointsNmi = pathFinderNmi._pointsNmi;

		/** Set _path and _waypointSecsS. */
		final PathAndWaypointSecs pathAndWaypointSecs = new PathAndWaypointSecs(
				tc, baseSecs, searchDurationSecs, pointsNmi);
		_path = pathAndWaypointSecs._path;
		_waypointSecsS = pathAndWaypointSecs._waypointSecsS;

		/**
		 * SPEC is not buffered; appeal to center, firstLegHdg, and
		 * specAcrossNmi.
		 */
		_looseSpecLatLngs = new LatLng3[4];
		final double theta = Math.toRadians(90d - firstLegHdg);
		final double s = MathX.sinX(theta);
		final double c = MathX.cosX(theta);
		final double[] b0 = new double[] { c, s };
		final double[] b1 = new double[] { -s, c };
		final double specAcrossROver2 = (specAcrossNmi * _NmiToR) / 2d;
		final double[] lc0, lc1, lc2, lc3;
		if (_firstTurnRight) {
			lc0 = NumericalRoutines.linearCombination(-specAcrossROver2,
					specAcrossROver2, b0, b1);
			lc1 = NumericalRoutines.linearCombination(specAcrossROver2,
					specAcrossROver2, b0, b1);
			lc2 = NumericalRoutines.linearCombination(specAcrossROver2,
					-specAcrossROver2, b0, b1);
			lc3 = NumericalRoutines.linearCombination(-specAcrossROver2,
					-specAcrossROver2, b0, b1);
		} else {
			lc0 = NumericalRoutines.linearCombination(-specAcrossROver2,
					-specAcrossROver2, b0, b1);
			lc1 = NumericalRoutines.linearCombination(specAcrossROver2,
					-specAcrossROver2, b0, b1);
			lc2 = NumericalRoutines.linearCombination(specAcrossROver2,
					specAcrossROver2, b0, b1);
			lc3 = NumericalRoutines.linearCombination(-specAcrossROver2,
					specAcrossROver2, b0, b1);
		}
		_looseSpecLatLngs[0] = tc.new FlatLatLng(lc0);
		_looseSpecLatLngs[1] = tc.new FlatLatLng(lc1);
		_looseSpecLatLngs[2] = tc.new FlatLatLng(lc2);
		_looseSpecLatLngs[3] = tc.new FlatLatLng(lc3);

		/** Do the 2 buffered ones. Start by gathering the critical points. */
		final int nPoints = unrotatedPointsNmi.length;
		final ArrayList<double[]> criticalUnrotatedPoints = new ArrayList<>();
		/** Up to the last 5 points will always do. */
		for (int k = 0; k < 5; ++k) {
			final int kPoint = nPoints - 1 - k;
			if (kPoint < 0) {
				break;
			}
			criticalUnrotatedPoints.add(unrotatedPointsNmi[kPoint]);
		}

		/** Build TS and EXC polygons. */
		final LatLng3[][] tightTsLooseTsAndTightExc = getTightTsLooseTsTightExc(
				criticalUnrotatedPoints, tsNmi, excBufferNmi, firstLegHdg);
		_tightTsLatLngs = tightTsLooseTsAndTightExc[0];
		_looseTsLatLngs = tightTsLooseTsAndTightExc[1];
		_tightExcLatLngs = tightTsLooseTsAndTightExc[2];
		final int tsFlag =
				Loop3Statics.createGenericFlag(/* isClockwise= */_firstTurnRight);
		_tightTsLoop = Loop3.getLoop(/* _logger= */null, /* id= */0,
				/* subId= */0, tsFlag, /* ancestorId= */-1, _tightTsLatLngs,
				/* logChanges= */false, /* debug= */false);
		_tightExcCcwGcas = new CcwGcas(/* _logger= */null, _tightExcLatLngs);
	}

	private LatLng3[][] getTightTsLooseTsTightExc(
			final ArrayList<double[]> criticalUnrotatedPoints, final double tsNmi,
			final double excBufferNmi, final double firstLegHdg) {
		LatLng3[] tightTsLatLngs = null;
		LatLng3[] looseTsLatLngs = null;
		LatLng3[] tightExcLatLngs = null;
		for (int k0 = 0; k0 < 2; ++k0) {
			final LoopType loopType;
			final double bufferNmi;
			if (k0 == 0) {
				loopType = LoopType.TS;
				bufferNmi = tsNmi / 2d;
			} else {
				loopType = LoopType.EXC;
				bufferNmi = NumericalRoutines.round(excBufferNmi,
						PatternUtilStatics._BufferInc);
			}
			final LatLng3[][] ccwTightAndLoose = getBufferedLatLngArrays(
					bufferNmi, firstLegHdg, loopType, criticalUnrotatedPoints);
			for (int k1 = 0; k1 < 2; ++k1) {
				final boolean tight = k1 == 0;
				if (loopType == LoopType.EXC && !tight) {
					/** Not interested in loose EXC. */
					continue;
				}
				final LatLng3[] latLngArray = ccwTightAndLoose[k1];
				/**
				 * Flip the ccw array to cw for TS and firstTurnRight. Exc must
				 * always be ccw.
				 */
				if (loopType == LoopType.TS && _firstTurnRight) {
					GcaSequenceStatics.flipLatLngList(Arrays.asList(latLngArray),
							/* tgtLatLngArray= */null, /* fromLoop= */true);
				}
				if (loopType == LoopType.TS && tight) {
					tightTsLatLngs = latLngArray;
				} else if (loopType == LoopType.TS && !tight) {
					looseTsLatLngs = latLngArray;
				} else if (loopType == LoopType.EXC && tight) {
					tightExcLatLngs = latLngArray;
				}
			}
		}
		return new LatLng3[][] { tightTsLatLngs, looseTsLatLngs,
				tightExcLatLngs };
	}

	/** For VS. NB: there is no conversion from SPEC to TS. */
	public SphericalTimedSegs(final double rawSearchKts, //
			final long baseSecs, final int searchDurationSecs, //
			final LatLng3 center, final double firstLegHdg, //
			final boolean firstTurnRight, //
			final double excBufferNmi) {
		_firstTurnRight = firstTurnRight;
		final double searchHrs = searchDurationSecs / 3600d;
		final double eplNmi0 = rawSearchKts * searchHrs *
				PatternUtilStatics._EffectiveSpeedReduction;
		final double tsNmi = PatternUtilStatics.computeVsTsNmi(eplNmi0);
		_nLegs = _nSearchLegs = PatternUtilStatics._NVsSearchLegs;
		_center = center;

		/** Store the twisted longitudes of the 6 magic points, in ccw order. */
		final double[] cLngs = new double[6];
		final double[] sLngs = new double[6];
		for (int k = 0; k < 6; ++k) {
			final double twLng = 180d - firstLegHdg + (k * 60d);
			final double twLngR = Math.toRadians(twLng);
			cLngs[k] = MathX.cosX(twLngR);
			sLngs[k] = MathX.sinX(twLngR);
		}

		/**
		 * <pre>
		 * Form a basis just from _center's latitude:
		 * b2 = lat=centerLat, lng=0
		 * b0 = lat=centerLat-90, lng=0
		 * b1 = (0,1,0)
		 * When we convert back to normal coordinates
		 * (before making the longitude correction), we
		 * take a linear combination of these basis vectors.
		 * It is simple to appeal just to cLat and cLng when
		 * doing this.  For example, when we add A(b1) within
		 * our linear combination, we do nothing for components
		 * 0 and 2 and simply add A to component 1.
		 * </pre>
		 */
		final double c = _center.getCLat();
		final double s = _center.getSLat();
		final double centerLng = _center.getLng();
		/**
		 * Compute the path and the exclusion hexagon. There are 6 important
		 * points for both of these; there are only 4 for the TsBox.
		 */
		final double[] b2 = new double[] { c, 0d, s };
		final double[] b0 = new double[] { s, 0d, -c };
		final double[] b1 = new double[] { 0d, 1d, 0d };
		LatLng3[] path = null;
		LatLng3[] excHexagon = null;
		for (int iPass = 0; iPass < 2; ++iPass) {
			final double radiusNmi;
			if (iPass == 0) {
				radiusNmi = tsNmi;
			} else {
				radiusNmi = getNmiBufferToUseAtCorners(/* use45= */false, tsNmi,
						excBufferNmi);
			}
			if (!(radiusNmi > 0d)) {
				continue;
			}
			final double twLat = 90d - Math.toDegrees(radiusNmi * _NmiToR);
			final double sTwLat = MathX.sinX(Math.toRadians(twLat));
			final double cTwLat = MathX.cosX(Math.toRadians(twLat));

			final LatLng3[] latLngs = new LatLng3[6];
			for (int k = 0; k < 6; ++k) {
				final double cLng = cLngs[k];
				final double sLng = sLngs[k];
				final double twXyzX = cLng * cTwLat;
				final double twXyzY = sLng * cTwLat;
				final double twXyzZ = sTwLat;
				/** Form the linear combination of twXyz with our basis. */
				final double[] xyz0 = NumericalRoutines.linearCombination(twXyzX,
						twXyzY, twXyzZ, b0, b1, b2);
				final double[] latLngArray0 = MathX.toLatLngArrayX(xyz0);
				/** Add back the base longitude. */
				latLngArray0[1] += centerLng;
				latLngs[k] = LatLng3.getLatLngC2(latLngArray0);
			}
			if (iPass == 0) {
				if (_nLegs == 7) {
					if (firstTurnRight) {
						/** 7-edge convention. */
						path = new LatLng3[] { //
								_center, latLngs[0], latLngs[5], //
								latLngs[2], latLngs[1], //
								latLngs[4], latLngs[3], _center //
						};
					} else {
						path = new LatLng3[] { //
								_center, latLngs[0], latLngs[1], //
								latLngs[4], latLngs[5], //
								latLngs[2], latLngs[3], _center //
						};
					}
				} else {
					/** 9-edge convention. */
					if (firstTurnRight) {
						path = new LatLng3[] { //
								_center, latLngs[0], latLngs[5], _center, //
								latLngs[2], latLngs[1], _center, //
								latLngs[4], latLngs[3], _center //
						};
					} else {
						path = new LatLng3[] { //
								_center, latLngs[0], latLngs[1], _center, //
								latLngs[4], latLngs[5], _center, //
								latLngs[2], latLngs[3], _center //
						};
					}
				}
			} else {
				/** excHexagons are ccw, which is how we built latLngs. */
				excHexagon = latLngs;
			}
		}
		_path = path;
		_tightExcLatLngs = excHexagon;
		final int nPoints = _path.length;

		/**
		 * <pre>
		/** For the 4-cornered TsBox, we use the same basis.  We want
		 * the length to be 3/2 the track spacing, but we need to compute
		 * the length at the diagonals because the diagonal points define the TsBox.
		 * Let rX = 3/2 tsR and imagine points p0 and p1 rX from the north pole,
		 * and on the prime and 90 meridians. Their partners are (0,1,0) and
		 * (-1,0,0) and we cross p0 and p1 with their partners to find
		 * the normals defining the two great circles whose intersection is
		 * the diagonal of interest.  We could then cross these normals, but
		 * we solve n0 x D = 0, n1 x D = 0 for D.  Doing this yields that
		 * the diagonal's z coordinate is s/sqrt(1+cc), where s and c are the
		 * sin and cosine of the latitude of points p0 and p1.
		 * </pre>
		 */
		final double halfBoxLengthNmi = 1.5 * tsNmi;
		final double twLatBox =
				90d - Math.toDegrees(halfBoxLengthNmi * _NmiToR);
		final double sTwLatBox0 = MathX.sinX(Math.toRadians(twLatBox));
		final double cTwLatBox0 = MathX.cosX(Math.toRadians(twLatBox));
		final double sTwLatBox =
				sTwLatBox0 / Math.sqrt(1d + cTwLatBox0 * cTwLatBox0);
		final double cTwLatBox = Math.sqrt(1d - sTwLatBox * sTwLatBox);

		final LatLng3[] ccwTsTightCorners = new LatLng3[4];
		for (int k = 0; k < 4; ++k) {
			/** Build the corners ccw, starting at the top right. */
			final double twLng = 180d - firstLegHdg - 45d + (k * 90d);
			final double twLngR = Math.toRadians(twLng);
			final double cLng = MathX.cosX(twLngR);
			final double sLng = MathX.sinX(twLngR);
			final double twXyzX = cLng * cTwLatBox;
			final double twXyzY = sLng * cTwLatBox;
			final double twXyzZ = sTwLatBox;
			final double x = s * twXyzX + c * twXyzZ;
			final double y = twXyzY;
			final double z = -c * twXyzX + s * twXyzZ;
			final double[] xyz0 = new double[] { x, y, z };
			final double[] latLngArray0 = MathX.toLatLngArrayX(xyz0);
			latLngArray0[1] += centerLng;
			ccwTsTightCorners[k] = LatLng3.getLatLngC2(latLngArray0);
		}
		if (!firstTurnRight) {
			_tightTsLatLngs = new LatLng3[] { //
					ccwTsTightCorners[3], //
					ccwTsTightCorners[0], //
					ccwTsTightCorners[1], //
					ccwTsTightCorners[2] //
			};
		} else {
			_tightTsLatLngs = new LatLng3[] { //
					ccwTsTightCorners[2], //
					ccwTsTightCorners[1], //
					ccwTsTightCorners[0], //
					ccwTsTightCorners[3] //
			};
		}
		_looseSpecLatLngs = _looseTsLatLngs = _tightTsLatLngs;

		/**
		 * Regardless of whether we consider VS patterns to have 7 or 9 legs,
		 * the 1st cross leg is between path[1] and path[2].
		 */
		final double crossLegNmi = MathX.haversineX(path[1], path[2]) / _NmiToR;
		final double[] lengthsNmi;
		if (_nLegs == 7) {
			lengthsNmi = new double[] { //
					tsNmi, crossLegNmi, //
					2d * tsNmi, crossLegNmi, //
					2d * tsNmi, crossLegNmi, //
					tsNmi };
		} else {
			lengthsNmi = new double[] { //
					tsNmi, crossLegNmi, //
					tsNmi, tsNmi, crossLegNmi, //
					tsNmi, tsNmi, crossLegNmi, //
					tsNmi };
		}
		/** Adjust effKts and compute _waypointSecsS. */
		final double ttlLegLengthNmi = 6d * tsNmi + 3d * crossLegNmi;

		_waypointSecsS = new long[nPoints];
		double accNmi = 0d;
		_waypointSecsS[0] = baseSecs;
		for (int k = 1; k < nPoints; ++k) {
			accNmi += lengthsNmi[k - 1];
			_waypointSecsS[k] = Math.round(
					baseSecs + (accNmi / ttlLegLengthNmi) * searchDurationSecs);
		}
		final int tsFlag =
				Loop3Statics.createGenericFlag(/* isClockwise= */_firstTurnRight);
		_tightTsLoop = Loop3.getLoop(/* _logger= */null, /* id= */0,
				/* subId= */0, tsFlag, /* ancestorId= */-1, _tightTsLatLngs,
				/* logChanges= */false, /* debug= */false);
		_tightExcCcwGcas = new CcwGcas(/* _logger= */null, _tightExcLatLngs);
	}

	final private static double _Cos30Sq = 0.75;
	final private static double _Cos45Sq = 0.5;

	private static double getNmiBufferToUseAtCorners(final boolean use45,
			final double radiusNmi, final double bufferNmi) {
		/**
		 * Find the highest latitude when going from (lat=90-radiusNmi,lng=-30)
		 * to (lat=90-radiusNmi,lng=30).
		 */
		final double _CosSq = use45 ? _Cos45Sq : _Cos30Sq;
		final double latAR = Constants._PiOver2 - (radiusNmi * _NmiToR);
		final double sA = MathX.sinX(latAR);
		final double cA = Math.sqrt(1d - sA * sA);
		final double tA = sA / cA;
		final double c0 = Math.sqrt(1d / ((tA * tA / _CosSq) + 1d));
		final double degs0 = Math.toDegrees(MathX.acosX(c0));
		/** Subtract off the buffer. */
		final double lat1R = Math.toRadians(degs0) - (bufferNmi * _NmiToR);
		final double s1 = MathX.sinX(lat1R);
		final double c1 = MathX.cosX(lat1R);
		final double t1 = s1 / c1;
		final double cB = Math.sqrt(1d / ((t1 * t1 * _CosSq) + 1d));
		final double latBR = MathX.acosX(cB);
		final double cornerRadiusNmi = (Constants._PiOver2 - latBR) / _NmiToR;
		return cornerRadiusNmi;
	}

	static private class Corner {
		double[] _coords;
		final boolean _xDominates;
		/**
		 * Every Corner is either left or right, and either low or high. Hence,
		 * we store only left and low.
		 */
		final boolean _left, _low;

		private Corner(final boolean xDominates, final boolean left,
				final boolean low) {
			_coords = null;
			_xDominates = xDominates;
			_left = left;
			_low = low;
		}

		/** Ignores the xDominates field. */
		private boolean smallEquals(final Corner corner) {
			if (_coords != corner._coords) {
				if ((_coords[0] != corner._coords[0]) ||
						(_coords[1] != corner._coords[1])) {
					return false;
				}
			}
			return _left == corner._left && _low == corner._low;
		}
	}

	private LatLng3[][] getBufferedLatLngArrays(final double bufferNmi,
			final double firstLegHdg, final LoopType loopType,
			final ArrayList<double[]> criticalUnrotatedPoints) {
		/**
		 * <pre>
		 * Find the 8 side points:
		 * hiLt and loLt: hi and lo of lt (x dominates)
		 * ltLo and rtLo: lt and rt of lo (y dominates)
		 * hiRt and loRt: hi and lo of rt (x dominates)
		 * ltHi and rtHi: lt and rt of hi (y dominates)
		 * </pre>
		 */
		final Corner hiLt =
				new Corner(/* xDominates= */true, /* left= */true, /* low= */false);
		final Corner loLt = new Corner(true, true, true);
		final Corner hiRt = new Corner(true, false, false);
		final Corner loRt = new Corner(true, false, true);
		final Corner rtHi = new Corner(false, false, false);
		final Corner ltHi = new Corner(false, true, false);
		final Corner rtLo = new Corner(false, false, true);
		final Corner ltLo = new Corner(false, true, true);
		final Corner[] orderedCorners;
		if (_firstTurnRight) {
			orderedCorners =
					new Corner[] { hiLt, loLt, ltLo, rtLo, loRt, hiRt, rtHi, ltHi };
		} else {
			orderedCorners =
					new Corner[] { ltLo, rtLo, loRt, hiRt, rtHi, ltHi, hiLt, loLt };
		}
		final int nOrderedCorners = orderedCorners.length;

		/** Use each critical point to update all of the corners. */
		final int nCriticalPoints = criticalUnrotatedPoints.size();
		for (int k0 = 0; k0 < nCriticalPoints; ++k0) {
			final double[] point = criticalUnrotatedPoints.get(k0);
			final double x0 = point[0];
			final double y0 = point[1];
			for (int k1 = 0; k1 < nOrderedCorners; ++k1) {
				final Corner corner = orderedCorners[k1];
				if (corner._coords == null) {
					corner._coords = point;
					continue;
				}
				final boolean lt = corner._left;
				final boolean lo = corner._low;
				final boolean rt = !lt;
				final boolean hi = !lo;
				final double x1 = corner._coords[0];
				final double y1 = corner._coords[1];
				if (corner._xDominates) {
					if ((lt && x0 > x1) || (rt && x0 < x1)) {
						continue;
					}
					if ((lt && x0 < x1) || (rt && x0 > x1)) {
						corner._coords = point;
						continue;
					}
					if ((lo && y0 < y1) || (hi && y0 > y1)) {
						corner._coords = point;
						continue;
					}
					continue;
				}
				/** y dominates. */
				if ((lo && y0 > y1) || (hi && y0 < y1)) {
					continue;
				}
				if ((lo && y0 < y1) || (hi && y0 > y1)) {
					corner._coords = point;
					continue;
				}
				if ((lt && x0 < x1) || (rt && x0 > x1)) {
					corner._coords = point;
					continue;
				}
			}
		}

		/** Now rotate the points about _tc's center. */
		final TangentCylinder.FlatLatLng flatCenter =
				TangentCylinder.convertToCentered(_center);
		final TangentCylinder tc = flatCenter.getOwningTangentCylinder();
		final double theta = Math.toRadians(90d - firstLegHdg);
		final double c = MathX.cosX(theta);
		final double s = MathX.sinX(theta);
		final double[] b0 = new double[] { c, s };
		final double[] b1 = new double[] { -s, c };

		/** Compute looseLatLngs. */
		final LatLng3[] looseLatLngs;
		if (loopType == LoopType.EXC) {
			looseLatLngs = null;
		} else {
			final double ltNmi = hiLt._coords[0] - bufferNmi;
			final double loNmi = ltLo._coords[1] - bufferNmi;
			final double rtNmi = loRt._coords[0] + bufferNmi;
			final double hiNmi = rtHi._coords[1] + bufferNmi;
			final double ltR = ltNmi * _NmiToR;
			final double loR = loNmi * _NmiToR;
			final double rtR = rtNmi * _NmiToR;
			final double hiR = hiNmi * _NmiToR;
			final double[] eastNorthLtLo =
					NumericalRoutines.linearCombination(ltR, loR, b0, b1);
			final LatLng3 ltLoLatLng =
					tc.new FlatLatLng(eastNorthLtLo[0], eastNorthLtLo[1]);
			final double[] eastNorthLoRt =
					NumericalRoutines.linearCombination(rtR, loR, b0, b1);
			final LatLng3 loRtLatLng =
					tc.new FlatLatLng(eastNorthLoRt[0], eastNorthLoRt[1]);
			final double[] eastNorthRtHi =
					NumericalRoutines.linearCombination(rtR, hiR, b0, b1);
			final LatLng3 rtHiLatLng =
					tc.new FlatLatLng(eastNorthRtHi[0], eastNorthRtHi[1]);
			final double[] eastNorthHiLt =
					NumericalRoutines.linearCombination(ltR, hiR, b0, b1);
			final LatLng3 hiLtLatLng =
					tc.new FlatLatLng(eastNorthHiLt[0], eastNorthHiLt[1]);
			if (_firstTurnRight) {
				looseLatLngs = new LatLng3[] { hiLtLatLng, ltLoLatLng, loRtLatLng,
						rtHiLatLng };
			} else {
				looseLatLngs = new LatLng3[] { ltLoLatLng, loRtLatLng, rtHiLatLng,
						hiLtLatLng };
			}
		}

		/** Compute tightLatLngs. */
		final ArrayList<LatLng3> latLng3List = new ArrayList<>();
		for (int k = 0; k < nOrderedCorners; ++k) {
			final Corner corner = orderedCorners[k];
			if ((k > 0 && corner.smallEquals(orderedCorners[k - 1])) || (k == nOrderedCorners - 1 &&
					corner.smallEquals(orderedCorners[0]))) {
				continue;
			}
			final double eastNmi =
					corner._coords[0] + (corner._left ? -bufferNmi : bufferNmi);
			final double northNmi =
					corner._coords[1] + (corner._low ? -bufferNmi : bufferNmi);
			final double eastR = eastNmi * _NmiToR;
			final double northR = northNmi * _NmiToR;
			final double[] eastNorthR =
					NumericalRoutines.linearCombination(eastR, northR, b0, b1);
			final LatLng3 flatLatLng =
					tc.new FlatLatLng(eastNorthR[0], eastNorthR[1]);
			latLng3List.add(flatLatLng);
		}
		final LatLng3[] tightLatLngs =
				latLng3List.toArray(new LatLng3[latLng3List.size()]);
		return new LatLng3[][] { tightLatLngs, looseLatLngs };
	}

	/** Create the _path and times from the rotated Nmi values. */
	private static class PathAndWaypointSecs {
		final private TangentCylinder.FlatLatLng[] _path;
		final private long[] _waypointSecsS;

		private PathAndWaypointSecs(final TangentCylinder tc,
				final long baseSecs, final long searchDurationSecs,
				final double[][] pointsNmi) {
			final int nPoints = pointsNmi.length;
			double accNmi = 0d;
			final double[] lengthsNmi = new double[nPoints];
			lengthsNmi[0] = 0d;
			for (int k = 1; k < nPoints; ++k) {
				final double deltaX = pointsNmi[k][0] - pointsNmi[k - 1][0];
				final double deltaY = pointsNmi[k][1] - pointsNmi[k - 1][1];
				final double lengthNmi =
						Math.sqrt(deltaX * deltaX + deltaY * deltaY);
				accNmi += lengthNmi;
				lengthsNmi[k] = lengthNmi;
			}
			final double ttlLegLenNmi = accNmi;
			/** pointsNmi is rotated within _pathFinderNmi. */
			_path = new TangentCylinder.FlatLatLng[nPoints];
			_waypointSecsS = new long[nPoints];
			accNmi = 0d;
			for (int k = 0; k < nPoints; ++k) {
				accNmi += lengthsNmi[k];
				_waypointSecsS[k] = Math
						.round(baseSecs + (accNmi / ttlLegLenNmi) * searchDurationSecs);
				final double eastR = pointsNmi[k][0] * _NmiToR;
				final double northR = pointsNmi[k][1] * _NmiToR;
				_path[k] = tc.new FlatLatLng(eastR, northR);
			}
		}
	}

	public Loop3 getTightTsLoop() {
		return _tightTsLoop;
	}

	public CcwGcas getTightExcCcwGcas() {
		return _tightExcCcwGcas;
	}

	public int getNLegs() {
		return _nLegs;
	}

	public int getNSearchLegs() {
		return _nSearchLegs;
	}

	final public LatLng3[] getTightLatLngs(final LoopType loopType) {
		if (loopType == LoopType.SPEC) {
			return null;
		}
		if (loopType == LoopType.TS) {
			return _tightTsLatLngs;
		} else if (loopType == LoopType.EXC) {
			return _tightExcLatLngs;
		} else {
			return null;
		}
	}

	final public LatLng3[] getLooseLatLngs(final LoopType loopType) {
		if (loopType == LoopType.SPEC) {
			return _looseSpecLatLngs;
		}
		if (loopType == LoopType.TS) {
			return _looseTsLatLngs;
		}
		return null;
	}

}