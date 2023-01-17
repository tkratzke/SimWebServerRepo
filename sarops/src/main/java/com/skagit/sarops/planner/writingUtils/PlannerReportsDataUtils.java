package com.skagit.sarops.planner.writingUtils;

import java.util.HashMap;

import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.planner.posFunction.pvValueArrayPlus.PvValueArrayPlus;

public class PlannerReportsDataUtils {

	public static double[] getAllPttrnVblsJointPos(
			final PlannerReportsData plannerReportsData) {
		final ParticleData[] toUse = plannerReportsData._passLa;
		final int nToUse = toUse.length;
		double num = 0d;
		double den = 0d;
		for (int k = 0; k < nToUse; ++k) {
			final ParticleData particleData = toUse[k];
			final double bravoPrior = particleData._bravoPrior;
			final double pos = 1d - particleData.getPfBravo();
			num += bravoPrior * pos;
			den += bravoPrior;
		}
		return new double[] {
				num, den
		};
	}

	public static double[] getCondPos(
			final PlannerReportsData plannerReportsData, final int ot) {
		final ParticleData[] toUse = plannerReportsData._passLa;
		final int nToUse = toUse.length;
		double num = 0d;
		double den = 0d;
		for (int k = 0; k < nToUse; ++k) {
			final ParticleData particleData = toUse[k];
			final double bravoPrior = particleData._bravoPrior;
			if (particleData._ot == ot) {
				final double pos = 1d - particleData.getPfBravo();
				num += bravoPrior * pos;
				den += bravoPrior;
			}
		}
		return new double[] {
				num, den
		};
	}

	public static double[] getEvalAllPttrnVblsJointPos(
			final PlannerReportsData plannerReportsData) {
		final ParticleData[] toUse = plannerReportsData._allParticleDatas;
		final int nToUse = toUse.length;
		double num = 0d;
		double den = 0d;
		for (int k = 0; k < nToUse; ++k) {
			final ParticleData particleData = toUse[k];
			final double bravoPrior = particleData._bravoPrior;
			final double pos = 1d - particleData.getPfBravo();
			num += bravoPrior * pos;
			den += bravoPrior;
		}
		return new double[] {
				num, den
		};
	}

	public static double[] getEvalCum(
			final PlannerReportsData plannerReportsData) {
		final ParticleData[] toUse = plannerReportsData._allParticleDatas;
		final int nToUse = toUse.length;
		double num = 0d;
		double den = 0d;
		for (int k = 0; k < nToUse; ++k) {
			final ParticleData particleData = toUse[k];
			final double initWt = particleData._initWt;
			final double pos = 1d - particleData.getPfCum();
			num += initWt * pos;
			den += initWt;
		}
		return new double[] {
				num, den
		};
	}

	public static double[] getInitCondPos(
			final PlannerReportsData plannerReportsData, final int ot) {
		final ParticleData[] toUse = plannerReportsData._allParticleDatas;
		final int nToUse = toUse.length;
		double num = 0d;
		double den = 0d;
		for (int k = 0; k < nToUse; ++k) {
			final ParticleData particleData = toUse[k];
			if (particleData._ot == ot) {
				final double initWt = particleData._initWt;
				final double pos = 1d - particleData.getPfCum();
				num += initWt * pos;
				den += initWt;
			}
		}
		return new double[] {
				num, den
		};
	}

	public static double[] getInitJointPos(
			final PlannerReportsData plannerReportsData, final int ot) {
		final ParticleData[] toUse = plannerReportsData._allParticleDatas;
		final int nToUse = toUse.length;
		double num = 0d;
		double den = 0d;
		for (int k = 0; k < nToUse; ++k) {
			final ParticleData particleData = toUse[k];
			final double initWt = particleData._initWt;
			if (particleData._ot == ot) {
				final double pos = 1d - particleData.getPfCum();
				num += initWt * pos;
			}
			den += initWt;
		}
		return new double[] {
				num, den
		};
	}

	public static double[] getInitObjProb(
			final PlannerReportsData plannerReportsData, final int ot) {
		final ParticleData[] toUse = plannerReportsData._allParticleDatas;
		final int nToUse = toUse.length;
		double num = 0d;
		double den = 0d;
		for (int k = 0; k < nToUse; ++k) {
			final ParticleData particleData = toUse[k];
			final double initWt = particleData._initWt;
			if (particleData._ot == ot) {
				num += initWt;
			}
			den += initWt;
		}
		return new double[] {
				num, den
		};
	}

	public static double[] getInitRemainingProbability(
			final PlannerReportsData plannerReportsData, final int ot) {
		final ParticleData[] toUse = plannerReportsData._allParticleDatas;
		final int nToUse = toUse.length;
		double num = 0d;
		double den = 0d;
		for (int k = 0; k < nToUse; ++k) {
			final ParticleData particleData = toUse[k];
			final double initWt = particleData._initWt;
			if (particleData._ot == ot) {
				final double pFail = particleData.getPfCum();
				num += initWt * pFail;
			}
			den += initWt;
		}
		return new double[] {
				num, den
		};
	}

	static int getGrandOrd(final PlannerReportsData plannerReportsData,
			final String pvId) {
		final PvValueArrayPlus plus = plannerReportsData._pvValueArrayPlus;
		final PvValue[] nonNullPvValueArray = plus
				.getNonNullPvValues();
		final int nNonNullPttrnVbls = nonNullPvValueArray.length;
		for (int k = 0; k < nNonNullPttrnVbls; ++k) {
			final PvValue pvValue = nonNullPvValueArray[k];
			if (pvValue.getPv().getId().equalsIgnoreCase(pvId)) {
				return k;
			}
		}
		return -1;
	}

	public static double[] getJointPos(
			final PlannerReportsData plannerReportsData, final int ot) {
		final ParticleData[] toUse = plannerReportsData._passLa;
		final int nToUse = toUse.length;
		double num = 0d;
		double den = 0d;
		for (int k = 0; k < nToUse; ++k) {
			final ParticleData particleData = toUse[k];
			final double bravoPrior = particleData._bravoPrior;
			if (particleData._ot == ot) {
				final double pos = 1d - particleData.getPfBravo();
				num += bravoPrior * pos;
			}
			den += bravoPrior;
		}
		return new double[] {
				num, den
		};
	}

	public static HashMap<Integer, int[]> getNumberAdriftAndLanded2(
			final PlannerReportsData plannerReportsData) {
		final ParticleData[] toUse = plannerReportsData._allParticleDatas;
		final int nToUse = toUse.length;
		final HashMap<Integer, int[]> returnValue = new HashMap<>();
		for (int k = 0; k < nToUse; ++k) {
			final ParticleData particleData = toUse[k];
			final int ot = particleData._ot;
			int[] adriftAndLanded = returnValue.get(ot);
			if (adriftAndLanded == null) {
				adriftAndLanded = new int[] {
						0, 0
				};
				returnValue.put(ot, adriftAndLanded);
			}
			final boolean adrift2 = particleData._adrift2;
			++adriftAndLanded[adrift2 ? 0 : 1];
		}
		return returnValue;
	}

	public static double[] getRemainingProbability(
			final PlannerReportsData plannerReportsData, final int ot) {
		final ParticleData[] toUse = plannerReportsData._passLa;
		final int nToUse = toUse.length;
		double num = 0d;
		double den = 0d;
		for (int k = 0; k < nToUse; ++k) {
			final ParticleData particleData = toUse[k];
			final double bravoPrior = particleData._bravoPrior;
			if (particleData._ot == ot) {
				final double pFail = particleData.getPfBravo();
				num += bravoPrior * pFail;
			}
			den += bravoPrior;
		}
		return new double[] {
				num, den
		};
	}

	public static double[] getNetGain(
			final PlannerReportsData plannerReportsData, final Integer otInteger) {
		final int ot = otInteger == null ? Integer.MIN_VALUE : otInteger;
		final ParticleData[] toUse = plannerReportsData._allParticleDatas;
		final int nToUse = toUse.length;
		double num = 0d;
		double den = 0d;
		for (int k = 0; k < nToUse; ++k) {
			final ParticleData particleData = toUse[k];
			if (otInteger == null || particleData._ot == ot) {
				final double initWt = particleData._initWt;
				final double delta = particleData._pfAlpha - particleData.getPfCum();
				num += initWt * delta;
				den += initWt;
			}
		}
		return new double[] {
				num, den
		};
	}

	public static double[] getThisPlan(
			final PlannerReportsData plannerReportsData) {
		final ParticleData[] toUse = plannerReportsData._passBoth;
		final int nToUse = toUse.length;
		double num = 0d;
		double den = 0d;
		for (int k = 0; k < nToUse; ++k) {
			final ParticleData particleData = toUse[k];
			final double bravoPrior = particleData._bravoPrior;
			final double pos = 1d - particleData.getPfBravo();
			num += bravoPrior * pos;
			den += bravoPrior;
		}
		return new double[] {
				num, den
		};
	}

	public static double[] getEvalJointPos(
			final PlannerReportsData plannerReportsData, final String pvId) {
		final int grandOrd = getGrandOrd(plannerReportsData, pvId);
		final ParticleData[] toUse = plannerReportsData._allParticleDatas;
		final int nToUse = toUse.length;
		double num = 0d;
		double den = 0d;
		for (int k = 0; k < nToUse; ++k) {
			final ParticleData particleData = toUse[k];
			final double bravoPrior = particleData._bravoPrior;
			final double pos = 1d - particleData._pFails[grandOrd];
			num += bravoPrior * pos;
			den += bravoPrior;
		}
		return new double[] {
				num, den
		};
	}

	public static double[] getTopLeftSlctdObjsPos(
			final PlannerReportsData plannerReportsData, final String pvId) {
		final int grandOrd = getGrandOrd(plannerReportsData, pvId);
		final ParticleData[] toUse = plannerReportsData._passBothByPv[grandOrd];
		final int nToUse = toUse.length;
		double num = 0d;
		double den = 0d;
		for (int k = 0; k < nToUse; ++k) {
			final ParticleData particleData = toUse[k];
			if (particleData._slctds[grandOrd]) {
				final double bravoPrior = particleData._bravoPrior;
				final double pos = 1d - particleData._pFails[grandOrd];
				num += bravoPrior * pos;
				den += bravoPrior;
			}
		}
		return new double[] {
				num, den
		};
	}

	public static double[] getCondPos(
			final PlannerReportsData plannerReportsData, final String pvId,
			final int ot) {
		final int grandOrd = getGrandOrd(plannerReportsData, pvId);
		final ParticleData[] toUse = plannerReportsData._passLa;
		final int nToUse = toUse.length;
		double num = 0d;
		double den = 0d;
		for (int k = 0; k < nToUse; ++k) {
			final ParticleData particleData = toUse[k];
			if (particleData._ot == ot) {
				final double bravoPrior = particleData._bravoPrior;
				final double pos = 1d - particleData._pFails[grandOrd];
				num += bravoPrior * pos;
				den += bravoPrior;
			}
		}
		return new double[] {
				num, den
		};
	}

	public static double[] getJointPos(
			final PlannerReportsData plannerReportsData, final String pvId) {
		final int grandOrd = getGrandOrd(plannerReportsData, pvId);
		final ParticleData[] toUse = plannerReportsData._passLa;
		final int nToUse = toUse.length;
		double num = 0d;
		double den = 0d;
		for (int k = 0; k < nToUse; ++k) {
			final ParticleData particleData = toUse[k];
			final double bravoPrior = particleData._bravoPrior;
			final double pos = 1d - particleData._pFails[grandOrd];
			num += bravoPrior * pos;
			den += bravoPrior;
		}
		return new double[] {
				num, den
		};
	}

	public static double[] getJointPos(
			final PlannerReportsData plannerReportsData, final String pvId,
			final int ot) {
		final int grandOrd = getGrandOrd(plannerReportsData, pvId);
		final ParticleData[] toUse = plannerReportsData._passLa;
		final int nToUse = toUse.length;
		double num = 0d;
		double den = 0d;
		for (int k = 0; k < nToUse; ++k) {
			final ParticleData particleData = toUse[k];
			final double bravoPrior = particleData._bravoPrior;
			if (particleData._ot == ot) {
				final double pos = 1d - particleData._pFails[grandOrd];
				num += bravoPrior * pos;
			}
			den += bravoPrior;
		}
		return new double[] {
				num, den
		};
	}

	public static double[] getObjProb(
			final PlannerReportsData plannerReportsData) {
		return new double[] {
				1d, 1d
		};
	}

	public static double[] getObjProb(
			final PlannerReportsData plannerReportsData, final int ot) {
		final ParticleData[] toUse = plannerReportsData._passLa;
		final int nToUse = toUse.length;
		double num = 0d;
		double den = 0d;
		for (int k = 0; k < nToUse; ++k) {
			final ParticleData particleData = toUse[k];
			final double bravoPrior = particleData._bravoPrior;
			if (particleData._ot == ot) {
				num += bravoPrior;
			}
			den += bravoPrior;
		}
		return new double[] {
				num, den
		};
	}

}
