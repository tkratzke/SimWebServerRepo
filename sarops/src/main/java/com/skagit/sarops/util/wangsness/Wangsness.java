package com.skagit.sarops.util.wangsness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;

import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.simCaseManager.SimCaseManager.SimCase;
import com.skagit.sarops.util.CppToJavaTracer;
import com.skagit.util.MathX;
import com.skagit.util.NumericalRoutines;
import com.skagit.util.cdf.BivariateNormalCdf;
import com.skagit.util.cdf.area.EllipticalArea;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.navigation.GreatCircleCalculator;
import com.skagit.util.navigation.LatLng3;

public class Wangsness {
	final public static boolean _Clockwise = true;
	final public static double _ContainmentValueOfInput = 0.95;
	final private static double _ThresholdForMoveNmi = 0.05;
	final private static int _MaxNumberOfIterations = 8;
	final private static double _NmiToR = MathX._NmiToR;

	private static class EigenPair {
		final private double _realPartOfEigenValue;
		@SuppressWarnings("unused")
		final private double _imagPartOfEigenValue;
		final private double[] _vector;

		private EigenPair(final EigenDecomposition eigenDecomposition, final int k) {
			_realPartOfEigenValue = eigenDecomposition.getRealEigenvalue(k);
			_imagPartOfEigenValue = eigenDecomposition.getImagEigenvalue(k);
			_vector = eigenDecomposition.getEigenvector(k).toArray();
		}
	}

	private static Comparator<EigenPair> _EigenPairComparator = new Comparator<>() {
		@Override
		public int compare(final EigenPair arg0, final EigenPair arg1) {
			if (arg0._realPartOfEigenValue != arg1._realPartOfEigenValue) {
				return arg0._realPartOfEigenValue < arg1._realPartOfEigenValue ? -1 : 1;
			}
			return NumericalRoutines._ByAllInOrder1.compare(arg0._vector, arg1._vector);
		}
	};
	final public LatLng3 _fix;
	final public double _sigmaA_Nmi;
	final public double _sigmaB_Nmi;
	final public double _dirA_D_CwFromN;
	final public int[] _idxsInUse;
	final public CppToJavaTracer _cppToJavaTracer;
	public boolean _valid;

	private Wangsness(final LatLng3 fix, final double sigmaAInNmi, final double sigmaBInNmi,
			final double dirA_D_CwFromN, final int[] idxsInUse, final CppToJavaTracer cppToJavaTracer) {
		_fix = fix;
		_sigmaA_Nmi = sigmaAInNmi;
		_sigmaB_Nmi = sigmaBInNmi;
		_dirA_D_CwFromN = dirA_D_CwFromN;
		_idxsInUse = idxsInUse;
		_cppToJavaTracer = cppToJavaTracer;
		_valid = false;
	}

	private static EigenPair[] getEigenPairs(final double[][] matrix) {
		final Array2DRowRealMatrix realMtx = new Array2DRowRealMatrix(matrix);
		final double unused = Double.NaN;
		@SuppressWarnings("deprecation")
		final EigenDecomposition eigenDecomposition = new EigenDecomposition(realMtx, unused);
		final EigenPair[] eigenPairs = new EigenPair[3];
		for (int i = 0; i < 3; ++i) {
			eigenPairs[i] = new EigenPair(eigenDecomposition, i);
		}
		Arrays.sort(eigenPairs, _EigenPairComparator);
		return eigenPairs;
	}

	private static Wangsness getWangsness(final SimCaseManager.SimCase simCase, final CppToJavaTracer cppToJavaTracer,
			final double[][] latLngBearingSdIdxs) {
		final int nGcas = latLngBearingSdIdxs.length;
		final GreatCircleArc[] gcasx = new GreatCircleArc[nGcas];
		for (int k = 0; k < nGcas; ++k) {
			final double[] latLngBearingSdIdx = latLngBearingSdIdxs[k];
			final double lat0 = latLngBearingSdIdx[0];
			final double lng0 = latLngBearingSdIdx[1];
			final LatLng3 latLng0 = LatLng3.getLatLngB(lat0, lng0);
			final double bearing = latLngBearingSdIdx[2];
			final double[] ninetyOffXyz = MathX.crossProductX(latLng0, bearing + 90d);
			final LatLng3 ninetyOffLatLng = LatLng3.getLatLngC3(ninetyOffXyz);
			gcasx[k] = GreatCircleArc.CreateGca(latLng0, ninetyOffLatLng);
		}
		/**
		 * Step 1 is to get an estimate of the emitter location. Form the matrix a,
		 * which has n rows and 3 columns.
		 */
		final double[][] a = new double[nGcas][];
		for (int k = 0; k < nGcas; ++k) {
			a[k] = gcasx[k].getNormal();
		}
		/**
		 * Form aTa, but do the matrix multiplication manually to guarantee symmetry.
		 */
		final double[][] aTa = new double[3][3];
		for (int i = 0; i < 3; ++i) {
			for (int j = i; j < 3; ++j) {
				aTa[i][j] = 0d;
				for (int k = 0; k < nGcas; ++k) {
					aTa[i][j] += a[k][i] * a[k][j];
				}
				aTa[j][i] = aTa[i][j];
			}
		}
		final EigenPair[] eigenPairsA = getEigenPairs(aTa);
		LatLng3 fix = choosePlusOrMinus(eigenPairsA[0]._vector, gcasx);
		for (int iterNumber = 0; iterNumber < _MaxNumberOfIterations; ++iterNumber) {
			/**
			 * Step 2 is to use that fix and the bearing errors to get sigma1 and sigma2.
			 */
			final double[] vInvDiag = new double[nGcas];
			for (int k = 0; k < nGcas; ++k) {
				final GreatCircleArc gca = gcasx[k];
				final double haversineToFix = MathX.haversineX(gca.getLatLng0(), fix);
				final double sin = MathX.sinX(haversineToFix);
				final double sd = latLngBearingSdIdxs[k][3];
				final double sigmaB = Math.toRadians(sd);
				vInvDiag[k] = 1d / (sin * sin * sigmaB * sigmaB);
				if (!isValidDouble(vInvDiag[k])) {
					final String f = "\n\t***Wangsness Reject: vInvDiag[%s] is invalid on iterNumber[%d].";
					final String rejectString = String.format(f, NumericalRoutines.getString(vInvDiag), iterNumber);
					if (cppToJavaTracer.isActive()) {
						cppToJavaTracer.writeTrace(rejectString);
					}
					SimCaseManager.out(simCase, rejectString);
					return null;
				}
			}
			final double[][] aTVInvA = new double[3][3];
			for (int i = 0; i < 3; ++i) {
				for (int j = i; j < 3; ++j) {
					aTVInvA[i][j] = 0d;
					for (int k = 0; k < nGcas; ++k) {
						aTVInvA[i][j] += a[k][i] * vInvDiag[k] * a[k][j];
					}
					aTVInvA[j][i] = aTVInvA[i][j];
				}
			}
			final EigenPair[] eigenPairsB = getEigenPairs(aTVInvA);
			final double lambda0 = eigenPairsB[0]._realPartOfEigenValue;
			final LatLng3 oldFix = fix;
			fix = choosePlusOrMinus(eigenPairsB[0]._vector, gcasx);
			final double moveNmi = GreatCircleCalculator.getNmi(oldFix, fix);
			if (moveNmi > _ThresholdForMoveNmi) {
				final String f = "\n\nWangsness Iterating from %s to %s.  Move[%f NM].";
				final String rejectString = String.format(f, oldFix.toString(), fix.toString(), moveNmi);
				if (cppToJavaTracer.isActive()) {
					cppToJavaTracer.writeTrace(rejectString);
				}
				SimCaseManager.out(simCase, rejectString);
				continue;
			}
			final LatLng3 latLngA = LatLng3.getLatLngC3(eigenPairsB[1]._vector.clone());
			final GreatCircleArc gcaA = GreatCircleArc.CreateGca(fix, latLngA);
			final double hdgA_D_CwFromN = gcaA.getRawInitialHdg();
			final double f;
			if (lambda0 > nGcas - 2 && nGcas > 2) {
				f = Math.sqrt(lambda0 / (nGcas - 2d));
			} else {
				f = 1d;
			}
			final double lambda1 = eigenPairsB[1]._realPartOfEigenValue;
			final double sigmaA_R = f / Math.sqrt(lambda1);
			final double sigmaA_Nmi = sigmaA_R / _NmiToR;
			final double lambda2 = eigenPairsB[2]._realPartOfEigenValue;
			final double sigmaB_R = f / Math.sqrt(lambda2);
			final double sigmaB_Nmi = sigmaB_R / _NmiToR;

			final int[] inUseIdxs = new int[nGcas];
			for (int k = 0; k < nGcas; ++k) {
				final double[] latLngBearingSdIdx = latLngBearingSdIdxs[k];
				inUseIdxs[k] = (int) Math.round(latLngBearingSdIdx[4]);
			}
			final Wangsness wangsness = new Wangsness(fix, sigmaA_Nmi, sigmaB_Nmi, hdgA_D_CwFromN, inUseIdxs,
					cppToJavaTracer);
			return wangsness;
		}
		final String f = "\n\t***Wangsness Reject: Could not stabilize estimate after [%d] moves.";
		final String rejectString = String.format(f, _MaxNumberOfIterations);
		if (cppToJavaTracer.isActive()) {
			cppToJavaTracer.writeTrace(rejectString);
		}
		SimCaseManager.out(simCase, rejectString);
		return null;
	}

	private static boolean isValidDouble(final double d) {
		return !Double.isNaN(d) && !Double.isInfinite(d);
	}

	private static LatLng3 choosePlusOrMinus(final double[] v, final GreatCircleArc[] gcas) {
		/** getLatLngC3 normalizes its input, which we might not want. */
		final LatLng3 estA = LatLng3.getLatLngC3(v.clone());
		final LatLng3 estB = LatLng3.getLatLngB(-estA.getLat(), estA.getLng() + 180d);
		double measureA = 0;
		double measureB = 0;
		for (final GreatCircleArc gca : gcas) {
			final LatLng3 latLng = gca.getLatLng0();
			measureA += MathX.haversineX(estA, latLng);
			measureB += MathX.haversineX(estB, latLng);
		}
		return measureA <= measureB ? estA : estB;
	}

	public String getString() {
		final String f = "Fix%s sigmaANmi/sigmaBNmi[%.3f/%.3f] dirACwFromN[%.4f]";
		final String s = String.format(f, _fix.toString(), _sigmaA_Nmi, _sigmaB_Nmi, _dirA_D_CwFromN);
		return s;
	}

	public static Wangsness getWangsness(final SimCase simCase, final CppToJavaTracer cppToJavaTracer,
			final List<BearingCall> bearingCalls, final Thresholds thresholds) {
		final ArrayList<BearingCall> bearingCallsInUse = new ArrayList<>();
		for (final BearingCall bearingCall : bearingCalls) {
			bearingCall._inUseForWangsness = false;
			bearingCallsInUse.add(bearingCall);
		}
		for (;;) {
			final int nBearingCallsInUse = bearingCallsInUse.size();
			if (nBearingCallsInUse < 2) {
				return null;
			}
			final double[][] latLngBearingSdIdxs = new double[nBearingCallsInUse][];
			for (int k = 0; k < nBearingCallsInUse; ++k) {
				final BearingCall bearingCall = bearingCallsInUse.get(k);
				final double lat = bearingCall._vertex.getLat();
				final double lng = bearingCall._vertex.getLng();
				final double bearing = bearingCall.getWorkingCalledBearing();
				final double sd = bearingCall.getWorkingSd();
				final int idx = bearingCall._bearingCallIdx;
				latLngBearingSdIdxs[k] = new double[] {
						lat, lng, bearing, sd, idx
				};
			}
			final Wangsness wangsness = getWangsness(simCase, cppToJavaTracer, latLngBearingSdIdxs);
			if (wangsness == null) {
				if (cppToJavaTracer.isActive()) {
					cppToJavaTracer.writeTrace("\n*** Wangsness came back NULL!! ***");
				}
				return null;
			}
			/**
			 * Find the bearing call that is the most "too far" from the fix. That's the one
			 * we'll delete before trying again.
			 */
			final LatLng3 fix = wangsness._fix;
			double worstRatio = 0d;
			int worstIdx = -1;
			for (int idx = 0; idx < nBearingCallsInUse; ++idx) {
				final BearingCall bearingCall = bearingCallsInUse.get(idx);
				final LatLng3 center = bearingCall.getCenterArea().getFlatCenterOfMass();
				final double dNmi = MathX.haversineX(center, fix) / _NmiToR;
				final double maxNmi = bearingCall.getInputMaxRangeNmi();
				final double ratio = dNmi / maxNmi;
				if (Double.isNaN(ratio)) {
					worstRatio = Double.NaN;
					worstIdx = idx;
					break;
				}
				if (ratio > worstRatio) {
					worstRatio = ratio;
					worstIdx = idx;
				}
			}
			/**
			 * Make checks. The first check is if we have one whose center is farther from
			 * the fix than its max range. That's detected simply by "worstRatio > 1."
			 */
			boolean reject = false;
			if ((worstRatio > 1d)) {
				final BearingCall badBearingCall = bearingCallsInUse.get(worstIdx);
				final double maxNmi = badBearingCall.getInputMaxRangeNmi();
				final String f = "\n\t***Wangsness Reject: " + "BearingCall[%s]: nmiToFix/maxNmi[%f] maxNmi[%f].";
				final String rejectString = String.format(f, badBearingCall.getString(), worstRatio, maxNmi);
				if (cppToJavaTracer.isActive()) {
					cppToJavaTracer.writeTrace(rejectString);
				}
				SimCaseManager.out(simCase, rejectString);
				reject = true;
			}
			/** Run through the static checks. */
			if (!reject) {
				for (int localIdx = 0; localIdx < nBearingCallsInUse; ++localIdx) {
					final BearingCall badBearingCall = bearingCallsInUse.get(localIdx);
					final LatLng3 center = badBearingCall.getCenterArea().getFlatCenterOfMass();
					final double dNmi = MathX.haversineX(center, fix) / _NmiToR;
					final double thresholdNmi = thresholds._distanceThresholdNmi;
					if (dNmi > thresholdNmi) {
						/** We have an outlier. */
						final String f = "\n\t***Wangsness Reject: " + "BearingCall[%s] is %f away from center%s. "
								+ "Global max distance is[%f]";
						final String rejectString = String.format(f, badBearingCall.getString(), dNmi, center,
								thresholdNmi);
						if (cppToJavaTracer.isActive()) {
							cppToJavaTracer.writeTrace(rejectString);
						}
						SimCaseManager.out(simCase, rejectString);
						reject = true;
						break;
					}
					/** Check for back bearing; based on center, not vertex. */
					final double inputCalledBearing = badBearingCall.getInputCalledBearing();
					final double[] ninetyOffXyz = MathX.crossProductX(center, inputCalledBearing + 90d);
					final LatLng3 ninetyOffLatLng = LatLng3.getLatLngC3(ninetyOffXyz);
					final GreatCircleArc gca = GreatCircleArc.CreateGca(center, ninetyOffLatLng);
					final GreatCircleArc.Projection projection = gca.new Projection(fix);
					final double rFromStart = projection.getROnGcToGcaLatLng0();
					if (rFromStart < 0d) {
						final GreatCircleArc gcaToFix = GreatCircleArc.CreateGca(center, fix);
						final LatLng3 oneHundredNmiOff = MathX.getLatLngX(center, inputCalledBearing, 100d * _NmiToR);
						final GreatCircleArc gcaOfCalledBearing = GreatCircleArc.CreateGca(center, oneHundredNmiOff);
						final String xml1 = gcaToFix.getXmlString("Red", 0, "Fix");
						final String xml2 = gcaOfCalledBearing.getXmlString("Cyan", 0, "Brng");
						final String f = "\n\t***Wangsness Reject: BearingCall[%s]"
								+ "\n\t   points the wrong direction(%.3f) to fix%s." + "\n\t   %s\n\t   %s";
						final double hdgToFix = MathX.initialHdgX(center, fix);
						final String rejectString = String.format(f, badBearingCall.getString(), hdgToFix,
								fix.getString(), xml1, xml2);
						cppToJavaTracer.writeTrace(rejectString);
						SimCaseManager.out(simCase, rejectString);
						reject = true;
						break;
					}
				}
			}
			final double sigmaANmi = wangsness._sigmaA_Nmi;
			final double sigmaBNmi = wangsness._sigmaB_Nmi;
			final double bigSigmaNmi = Math.max(sigmaANmi, sigmaBNmi);
			final double smallSigmaNmi = Math.min(sigmaANmi, sigmaBNmi);
			final double[] containmentsNmi = BivariateNormalCdf
					.standardDeviationsToContainmentRadii(_ContainmentValueOfInput, bigSigmaNmi, smallSigmaNmi);
			final double semiMajorNmi = containmentsNmi[0];
			final double semiMinorNmi = containmentsNmi[1];
			if (!reject) {
				if (!isValidDouble(sigmaANmi) || !isValidDouble(sigmaBNmi)) {
					final String f = "\n\t***Wangsness Reject: Ellipse has bad sigma lengths"
							+ " ([%f NM] and [%f NM]).";
					final String rejectString = String.format(f, sigmaANmi, sigmaBNmi);
					cppToJavaTracer.writeTrace(rejectString);
					SimCaseManager.out(simCase, rejectString);
					reject = true;
				}
			}
			if (!reject) {
				if (semiMajorNmi > thresholds._semiMajorThresholdNmi) {
					final String f = "\n\t***Wangsness Reject: Ellipse has too large a semiMajor [%f NM].";
					final String rejectString = String.format(f, semiMajorNmi);
					cppToJavaTracer.writeTrace(rejectString);
					SimCaseManager.out(simCase, rejectString);
					reject = true;
				}
			}
			if (!reject) {
				final double areaSqNmi = semiMajorNmi * semiMinorNmi * Math.PI;
				if (areaSqNmi > thresholds._areaThresholdSqNmi) {
					final String f = "\n\t***Wangsness Reject: Ellipse has too large an area [%f sqNM].";
					final String rejectString = String.format(f, areaSqNmi);
					cppToJavaTracer.writeTrace(rejectString);
					SimCaseManager.out(simCase, rejectString);
					reject = true;
				}
			}
			if (!reject) {
				final double majorToMinor = semiMajorNmi / semiMinorNmi;
				if (Double.isNaN(majorToMinor) || Double.isInfinite(majorToMinor)
						|| majorToMinor > thresholds._majorToMinorThreshold) {
					final String f = "\n\t***Wangsness Reject: " + "Ellipse has bad aspect ratio ([%f and %f NM]).";
					final String rejectString = String.format(f, semiMajorNmi, semiMinorNmi);
					cppToJavaTracer.writeTrace(rejectString);
					SimCaseManager.out(simCase, rejectString);
					reject = true;
				}
			}
			/** We check for angle of intersection only if nBearingCalls is 2. */
			if (!reject && nBearingCallsInUse == 2) {
				final BearingCall bearingCall0 = bearingCallsInUse.get(0);
				final BearingCall bearingCall1 = bearingCallsInUse.get(1);
				final LatLng3 vertex0 = bearingCall0._vertex;
				final LatLng3 vertex1 = bearingCall1._vertex;
				final double hdg0 = MathX.initialHdgX(fix, vertex0);
				final double hdg1 = MathX.initialHdgX(fix, vertex1);
				final double minAngleD = thresholds._minAngleD;
				final double turnToLeft = GreatCircleCalculator.getTurnToLeftD(vertex0, fix, vertex1);
				if (Math.abs(turnToLeft) < minAngleD) {
					final String f = "\n\t***Wangsness Reject: 2-LOB case; "
							+ "difference in bearings from fix to centers "
							+ "is too close to 180 ([%f and %f], Turn[%f]).";
					final String rejectString = String.format(f, hdg0, hdg1, turnToLeft);
					cppToJavaTracer.writeTrace(rejectString);
					SimCaseManager.out(simCase, rejectString);
					reject = true;
				}
			}
			if (reject) {
				if (worstIdx < 0 || worstIdx >= bearingCallsInUse.size()) {
					worstIdx = 0;
				}
				bearingCallsInUse.remove(worstIdx);
				continue;
			}
			for (final BearingCall bearingCall : bearingCallsInUse) {
				bearingCall._inUseForWangsness = true;
			}
			wangsness._valid = true;
			return wangsness;
		}
	}

	public static double[] getEllipse(final boolean throwException) throws Exception {
		if (throwException) {
			throw new Exception("SampleException");
		}
		return new double[] {
				0, 1, 2, 3, 4
		};
	}

	/** Exclusively for CppToJava. */
	public static double[] getEllipse(double[] thresholdArray, final double[][] bearingCallArrays) throws Exception {
		final CppToJavaTracer cppToJavaTracer = new CppToJavaTracer("CppToJavaWangsness");
		final int nBearingCalls = bearingCallArrays == null ? 0 : bearingCallArrays.length;
		final String scenarioName = "CppToJava";
		final ArrayList<BearingCall> bearingCallList = new ArrayList<>();
		for (int bearingCallIdx = 0; bearingCallIdx < nBearingCalls; ++bearingCallIdx) {
			final double[] bearingCallArray = bearingCallArrays[bearingCallIdx];
			/** Create a Bearing Call from the CppToJava data. */
			final double bearingCallCentralLat = bearingCallArray[0];
			final double bearingCallCentralLng = bearingCallArray[1];
			final double semiMajorProbableErrorNmi = bearingCallArray[2];
			/** Right now, the center area has to be a circle. */
			final double semiMinorProbableErrorNmi = semiMajorProbableErrorNmi;
			final double dirA_R = 0d;
			final double[] sigmas = BivariateNormalCdf.containmentRadiiToStandardDeviations(0.5,
					semiMajorProbableErrorNmi, semiMinorProbableErrorNmi);
			final double sigmaANmi = sigmas[0];
			final double sigmaBNmi = sigmas[1];
			final double calledBearing = bearingCallArray[3];
			final double bearingSd = bearingCallArray[4];
			final double minNmi = bearingCallArray[5];
			final double maxNmi = bearingCallArray[6];
			final LatLng3 centralLatLng = LatLng3.getLatLngB(bearingCallCentralLat, bearingCallCentralLng);
			final EllipticalArea centralArea = new EllipticalArea(/* simCase= */null, centralLatLng, sigmaANmi,
					sigmaBNmi, dirA_R, /* isUniform= */false, maxNmi);
			final BearingCall bearingCall = new BearingCall(/* simCase= */null, scenarioName, centralArea,
					calledBearing, bearingSd, minNmi, maxNmi, bearingCallIdx);
			bearingCallList.add(bearingCall);
			if (cppToJavaTracer.isActive()) {
				cppToJavaTracer.writeTrace(String.format("\nBearingCall %s", bearingCall.getString()));
			}
		}
		if (thresholdArray == null) {
			final double thresholdSqNmi = 3000d;
			final double thresholdNmi = 500d;
			final double semiMajorThresholdNmi = 250d;
			final double majorToMinorThreshold = 64d;
			final double minAngleInDegs = 5d;
			thresholdArray = new double[5];
			thresholdArray[0] = thresholdSqNmi;
			thresholdArray[1] = thresholdNmi;
			thresholdArray[2] = semiMajorThresholdNmi;
			thresholdArray[3] = majorToMinorThreshold;
			thresholdArray[4] = minAngleInDegs;
		}
		final double areaThresholdSqNmi = thresholdArray[0];
		final double distanceThresholdNmi = thresholdArray[1];
		final double semiMajorThresholdNmi = thresholdArray[2];
		final double majorToMinorThreshold = thresholdArray[3];
		final double minAngleD = thresholdArray[4];
		final Thresholds thresholds = new Thresholds(areaThresholdSqNmi, distanceThresholdNmi, semiMajorThresholdNmi,
				majorToMinorThreshold, minAngleD);
		if (cppToJavaTracer.isActive()) {
			cppToJavaTracer.writeTrace(String.format("\nTresholds%s", thresholds.getString()));
		}
		final Wangsness wangsness = getWangsness(/* simCase= */null, cppToJavaTracer, bearingCallList, thresholds);
		if (wangsness != null && cppToJavaTracer.isActive()) {
			cppToJavaTracer.writeTrace(String.format("\nWangsness Ellipse: %s\n", wangsness.getString()));
		}
		final double centerLat;
		final double centerLng;
		final double semiMajor95Nmi;
		final double semiMinor95Nmi;
		final double degCwFromNorthOfSemiMajor;
		final int[] idxsInUse;
		if (wangsness == null) {
			centerLat = 91d;
			centerLng = 181d;
			semiMajor95Nmi = -1d;
			semiMinor95Nmi = -1d;
			degCwFromNorthOfSemiMajor = -1d;
			idxsInUse = new int[0];
		} else {
			centerLat = wangsness._fix.getLat();
			centerLng = wangsness._fix.getLng();
			final double sigmaANmi = wangsness._sigmaA_Nmi;
			final double sigmaBNmi = wangsness._sigmaB_Nmi;
			final double dirA_D_CwFromN = wangsness._dirA_D_CwFromN;
			final double[] semiA95Nmis = BivariateNormalCdf
					.standardDeviationsToContainmentRadii(_ContainmentValueOfInput, sigmaANmi, sigmaBNmi);
			final double semiA95Nmi = semiA95Nmis[0];
			final double semiB95Nmi = semiA95Nmis[1];
			if (semiA95Nmi >= semiB95Nmi) {
				semiMajor95Nmi = semiA95Nmi;
				semiMinor95Nmi = semiB95Nmi;
				degCwFromNorthOfSemiMajor = dirA_D_CwFromN;
			} else {
				semiMajor95Nmi = semiB95Nmi;
				semiMinor95Nmi = semiA95Nmi;
				degCwFromNorthOfSemiMajor = 90d + dirA_D_CwFromN;
			}
			idxsInUse = wangsness._idxsInUse;
		}
		final int nBasicReturnValues = 5;
		final int nIdxsInUse = idxsInUse.length;
		final int nDoubles = nBasicReturnValues + nIdxsInUse;
		final double[] returnValue = new double[nDoubles];
		returnValue[0] = centerLat;
		returnValue[1] = centerLng;
		returnValue[2] = semiMajor95Nmi;
		returnValue[3] = semiMinor95Nmi;
		returnValue[4] = degCwFromNorthOfSemiMajor;
		for (int k = 0; k < nIdxsInUse; ++k) {
			returnValue[nBasicReturnValues + k] = idxsInUse[k];
		}
		return returnValue;
	}

	public static void oldMain(final String[] args) throws Exception {

		final double[][] lobData4 = new double[2][7];
		lobData4[0][0] = 35.2455000; // lat of origin
		lobData4[0][1] = -75.5340611; // lng of origin
		lobData4[0][2] = 1d; // NM of probable-error of origin
		lobData4[0][3] = 95d; // Called bearing
		lobData4[0][4] = 2d; // bearingSdInDegrees
		lobData4[0][5] = 0d; // NM of minimum ring
		lobData4[0][6] = 34d; // NM of maximum ring;
		/**
		 * <pre>
		 * BearingCall Origin[35.2455000,-75.5340611] Call[100.0 degs] Sd[2.00 degs] MinRange[0.0 NM] MaxRange[34.0 NM] Idx[1]
		 * </pre>
		 */
		lobData4[1][0] = 35.2455000; // lat of origin
		lobData4[1][1] = -75.5340611; // lng of origin
		lobData4[1][2] = 1d; // NM of probable-error of origin
		lobData4[1][3] = 96d; // Called bearing
		lobData4[1][4] = 2d; // bearingSdInDegrees
		lobData4[1][5] = 0d; // NM of minimum ring
		lobData4[1][6] = 0d; // NM of maximum ring;
		final double[] ellipseDoubles4 = getEllipse(null, lobData4);
		System.out.printf("\nReturning: %s", NumericalRoutines.getString(ellipseDoubles4));
		System.exit(33);

		/**
		 * <pre>
		 * BearingCall Origin[35.2455000,-75.5340611] Call[95.0 degs] Sd[2.00 degs] MinRange[0.0 NM] MaxRange[34.0 NM] Idx[0]
		 * </pre>
		 */
		final double[][] lobData3 = new double[2][7];
		lobData3[0][0] = 35.2455000; // lat of origin
		lobData3[0][1] = -75.5340611; // lng of origin
		lobData3[0][2] = 1d; // NM of probable-error of origin
		lobData3[0][3] = 95d; // Called bearing
		lobData3[0][4] = 2d; // bearingSdInDegrees
		lobData3[0][5] = 0d; // NM of minimum ring
		lobData3[0][6] = 34d; // NM of maximum ring;
		/**
		 * <pre>
		 * BearingCall Origin[35.2455000,-75.5340611] Call[100.0 degs] Sd[2.00 degs] MinRange[0.0 NM] MaxRange[34.0 NM] Idx[1]
		 * </pre>
		 */
		lobData3[1][0] = 35.2455000; // lat of origin
		lobData3[1][1] = -75.5340611; // lng of origin
		lobData3[1][2] = 1d; // NM of probable-error of origin
		lobData3[1][3] = 96d; // Called bearing
		lobData3[1][4] = 2d; // bearingSdInDegrees
		lobData3[1][5] = 0d; // NM of minimum ring
		lobData3[1][6] = 34d; // NM of maximum ring;
		final double[] ellipseDoubles3 = getEllipse(null, lobData3);
		System.out.printf("\nReturning: %s", NumericalRoutines.getString(ellipseDoubles3));
		System.exit(33);

		final double[][] lobData2 = new double[2][7];
		lobData2[0][0] = 36.13577780; // lat of origin
		lobData2[0][1] = -75.82419440; // lng of origin
		lobData2[0][2] = 1d; // NM of probable-error of origin
		lobData2[0][3] = 111d; // Called bearing
		lobData2[0][4] = 3d; // bearingSdInDegrees
		lobData2[0][5] = 0; // NM of minimum ring
		lobData2[0][6] = 36.41; // NM of maximum ring;

		lobData2[1][0] = 35.79591670; // lat of origin
		lobData2[1][1] = -75.55038890; // lng of origin
		lobData2[1][2] = 1d; // NM of probable-error of origin
		lobData2[1][3] = 11d; // Called bearing
		lobData2[1][4] = 4d; // bearingSdInDegrees
		lobData2[1][5] = 0; // NM of minimum ring
		lobData2[1][6] = 25.59; // NM of maximum ring;

		final double[] ellipseDoubles2 = getEllipse(null, lobData2);
		System.out.printf("\nReturning: %s", NumericalRoutines.getString(ellipseDoubles2));

		/** Old case: */
		if (true) {
		} else {
			final double[][] lobData1 = new double[3][7];
			lobData1[0][0] = 37.16333; // lat of origin
			lobData1[0][1] = -76.53555; // lng of origin
			lobData1[0][2] = 0.1; // NM of probable-error of origin
			lobData1[0][3] = 284; // Called bearing
			lobData1[0][4] = 2; // bearingSdInDegrees
			lobData1[0][5] = 0; // NM of minimum ring
			lobData1[0][6] = 31.76; // NM of maximum ring;

			lobData1[1][0] = 36.7302; // lat of origin
			lobData1[1][1] = -76.009; // lng of origin
			lobData1[1][2] = 0.1; // NM of probable-error of origin
			lobData1[1][3] = 225; // Called bearing
			lobData1[1][4] = 2; // bearingSdInDegrees
			lobData1[1][5] = 0; // NM of minimum ring
			lobData1[1][6] = 29.35; // NM of maximum ring;

			lobData1[2][0] = 37.26255; // lat of origin
			lobData1[2][1] = -76.01228; // lng of origin
			lobData1[2][2] = 0.1; // NM of probable-error of origin
			lobData1[2][3] = 315; // Called bearing
			lobData1[2][4] = 2; // bearingSdInDegrees
			lobData1[2][5] = 0; // NM of minimum ring
			lobData1[2][6] = 34.65; // NM of maximum ring;

			final double[] ellipseDoubles1 = getEllipse(null, lobData1);
			System.out.printf("\n%s", NumericalRoutines.getString(ellipseDoubles1));
		}
	}

	public static void main(final String[] args) {
		try {
			@SuppressWarnings("unused")
			final double[] ellipse = getEllipse(/* throwException= */true);
		} catch (final Exception e) {
			e.printStackTrace();
		}

	}
}
