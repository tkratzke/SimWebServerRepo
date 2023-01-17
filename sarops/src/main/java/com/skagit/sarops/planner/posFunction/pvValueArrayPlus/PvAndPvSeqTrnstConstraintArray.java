package com.skagit.sarops.planner.posFunction.pvValueArrayPlus;

import com.skagit.sarops.model.io.ModelReader;
import com.skagit.sarops.planner.Planner;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.planner.plannerModel.pvSeq.PvSeq;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.navigation.LatLng3;

public class PvAndPvSeqTrnstConstraintArray {

	public final double[][] _pvToOptnAndReports;
	public final double[][] _pvSeqToOptnAndReports;

	public PvAndPvSeqTrnstConstraintArray(final Planner planner,
			final PvValue[] pvValues) {
		final PlannerModel plannerModel = planner.getPlannerModel();
		final int nPttrnVbls = plannerModel.getNPttrnVbls();
		_pvToOptnAndReports = new double[nPttrnVbls][];
		for (int grandOrd = 0; grandOrd < nPttrnVbls; ++grandOrd) {
			_pvToOptnAndReports[grandOrd] = new double[] { 0d, 0d };
		}
		final int nPvSeqs = plannerModel.getNPvSeqs();
		_pvSeqToOptnAndReports = new double[nPvSeqs][];
		for (int pvSeqOrd = 0; pvSeqOrd < nPvSeqs; ++pvSeqOrd) {
			_pvSeqToOptnAndReports[pvSeqOrd] = new double[] { 0d, 0d };
		}

		/** Set these values pvSeq by pvSeq. */
		for (int pvSeqOrd = 0; pvSeqOrd < nPvSeqs; ++pvSeqOrd) {
			final PvSeq pvSeq = plannerModel.getPvSeq(pvSeqOrd);
			/**
			 * Collect the ones for pvSeq, are not onMars, and have
			 * isActive==true.
			 */
			final PvValue[] myPvValues = pvSeq.gatherMine(pvValues);
			final int nMyPvValues = myPvValues.length;
			LatLng3 pvsExit = pvSeq._launchLatLng;
			long launchRefSecs = pvSeq._launchRefSecs;
			long pvsRefSecs = launchRefSecs;
			for (int k1 = 0; k1 < nMyPvValues; ++k1) {
				final PvValue pvValue1 = myPvValues[k1];
				final LatLng3 csp = pvValue1.getCsp();
				final PatternVariable pv1 = pvValue1.getPv();
				final GreatCircleArc transitGca =
						GreatCircleArc.CreateGca(pvsExit, csp);
				final double transitNmi = transitGca.getTtlNmi();
				final double transitKts = pv1.getTransitKts();
				final long transitSecs =
						Math.round(3600d * transitNmi / transitKts);
				/** Set launchRefSecs if it isn't already. */
				if (k1 == 0 && launchRefSecs == ModelReader._UnsetTime) {
					pvsRefSecs =
							launchRefSecs = pvValue1.getCstRefSecs() - transitSecs;
				}
				final long arrivalRefSecs = pvsRefSecs + transitSecs;
				final long targetRefSecs = pvValue1.getCstRefSecs();
				final int grandOrd1 = pv1.getGrandOrd();
				final double raw = arrivalRefSecs - targetRefSecs;
				final double duration = targetRefSecs - launchRefSecs;
				_pvToOptnAndReports[grandOrd1] =
						new double[] { raw / duration, raw };
				pvsExit = csp;
				pvsRefSecs = pvValue1.getEstRefSecs();
			}
			/** Now for pvSeq itself. */
			final long arrivalRefSecs;
			if (pvSeq.hasRecoveryTransit()) {
				final double transitKts = pvSeq._recoveryKts;
				final GreatCircleArc transitGca =
						GreatCircleArc.CreateGca(pvsExit, pvSeq._recoveryLatLng);
				final double transitNmi = transitGca.getTtlNmi();
				final long transitSecs =
						Math.round(3600d * transitNmi / transitKts);
				arrivalRefSecs = pvsRefSecs + transitSecs;
			} else if (pvsRefSecs != ModelReader._UnsetTime) {
				arrivalRefSecs = pvsRefSecs;
			} else {
				/**
				 * Nothing to do; there are no pvSeq Pv's here. This ct was already
				 * set to {0,0}.
				 */
				continue;
			}
			final long targetRefSecs = launchRefSecs + pvSeq._totalDurationSecs;
			final double raw = arrivalRefSecs - targetRefSecs;
			final double duration = pvSeq._totalDurationSecs;
			_pvSeqToOptnAndReports[pvSeqOrd] =
					new double[] { raw / duration, raw };
		}
	}

}
