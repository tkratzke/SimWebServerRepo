package com.skagit.sarops.environment.dynamic;

import java.util.BitSet;

import org.w3c.dom.Element;

import com.skagit.sarops.environment.BoxDefinition;
import com.skagit.sarops.environment.CurrentsUvGetter;
import com.skagit.sarops.environment.DataForOnePointAndTime;
import com.skagit.sarops.environment.NetCdfCurrentsUvGetter;
import com.skagit.sarops.environment.NetCdfUvGetter;
import com.skagit.sarops.environment.NetCdfUvGetter.NetCdfUvGetterException;
import com.skagit.sarops.environment.SummaryRefSecs;
import com.skagit.sarops.environment.SummaryRefSecs.SummaryBuilder;
import com.skagit.sarops.environment.WindsUvGetter;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.Scenario;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.sarops.simCaseManager.SimCaseManager.SimCase;
import com.skagit.sarops.tracker.DistressStateVector;
import com.skagit.util.gshhs.CircleOfInterest;
import com.skagit.util.navigation.LatLng3;

import ucar.nc2.NetcdfFile;

public class DynamicCurrentsUvGetter extends DynamicEnvUvGetter
		implements CurrentsUvGetter, SummaryBuilder {
	final private static int _MaxUvGettersToKeep = 100;

	public DynamicCurrentsUvGetter(final SimCaseManager.SimCase simCase,
			final Model model, final String interpolationMode,
			final long halfLifeSecs, final long preDistressHalfLifeSecs,
			final double uStandardDeviation, final double vStandardDeviation,
			final String scheme, final String userInfo, final String host,
			final int port, final String path, final String clientKey,
			final String sourceID, final String sourceName, final int outputType,
			final int timeOut, final boolean zipped) {
		super(simCase, model, _DriftsTag, _MaxUvGettersToKeep,
				interpolationMode, halfLifeSecs, preDistressHalfLifeSecs,
				uStandardDeviation, vStandardDeviation, scheme, userInfo, host,
				port, path, clientKey, sourceID, sourceName, outputType, timeOut,
				zipped);
	}

	@Override
	public void incrementalPrepare(final long refSeconds,
			final LatLng3 latLng, final BoxDefinition inputBoxDefinition) {
		cacheNecessaryBoxDefinition(refSeconds, latLng, inputBoxDefinition);
	}

	@Override
	public void finishPrepare() {
		finishPrepareXYZ();
	}

	@Override
	public boolean prepareIsTrivial() {
		return false;
	}

	@Override
	public boolean useRandomDuringUpdates() {
		return true;
	}

	@Override
	public void close(final String interpolationMode) {
		/**
		 * Closing is done by each NetCdfCurrentsUvGetter. Hence, we do nothing
		 * here.
		 */
	}

	@Override
	public CurrentsUvGetter getCurrentsUvGetter2(final BitSet iViews0,
			final boolean interpolateInTime0) {
		/** If iView is not 0, ..., well, we're just not up to that. */
		if (!iViews0.get(0)) {
			return null;
		}
		return new CurrentsUvGetter() {
			@Override
			public DataForOnePointAndTime getCurrentData(final long refSeconds,
					final LatLng3 latLng, final String interpolationMode) {
				return getDataForOnePointAndTime(refSeconds, latLng,
						getInterpolationMode());
			}

			@Override
			public long getHalfLifeSecs(final int overallIndex) {
				return DynamicCurrentsUvGetter.this.getHalfLifeSecs(overallIndex);
			}

			@Override
			public long getPreDistressHalfLifeSecs() {
				return DynamicCurrentsUvGetter.this.getPreDistressHalfLifeSecs();
			}

			@Override
			public void incrementalPrepare(final long refSeconds,
					final LatLng3 latLng, final BoxDefinition boxDefinition) {
			}

			@Override
			public void finishPrepare() {
			}

			@Override
			public boolean prepareIsTrivial() {
				return true;
			}

			@Override
			public boolean useRandomDuringUpdates() {
				return true;
			}

			@Override
			public void close(final String inerpolationMode) {
			}

			@Override
			public CurrentsUvGetter getCurrentsUvGetter2(final BitSet iViews1,
					final boolean interpolateInTime1) {
				return null;
			}

			@Override
			public String[] getViewNames() {
				return null;
			}

			@Override
			public void writeElement(final Element outputDriftsElement,
					final Element inputDriftsElement, final Model model) {
			}

			@Override
			public boolean isEmpty(final SimCaseManager.SimCase simCase) {
				final DynamicCurrentsUvGetter dynamicCurrentsUvGetter =
						DynamicCurrentsUvGetter.this;
				return dynamicCurrentsUvGetter.isEmpty(simCase);
			}

			@Override
			public void freeMemory() {
			}

			@Override
			public int[] getFirstSampleAndPredictionIndex(final int nViews) {
				return null;
			}

			@Override
			public boolean hasAuxiliaryProcessing() {
				return false;
			}

			@Override
			public DistressStateVector fillInStateVectorsIfAppropriate(
					final SimCase simCase, final WindsUvGetter windsUvGetter,
					final Scenario scenario, final long[] simSecsS,
					final DistressStateVector distressStateVector,
					final long simSecs) {
				return distressStateVector;
			}
		};
	}

	@Override
	public String[] getViewNames() {
		return new String[] { "Currents" };
	}

	@Override
	public DataForOnePointAndTime getCurrentData(final long refSeconds,
			final LatLng3 latLng, final String interpolationMode) {
		NetCdfUvGetter winner = null;
		for (final BoxDefinition boxDefinition : _uvGetters.keySet()) {
			if (boxDefinition.contains(refSeconds, latLng)) {
				winner = _uvGetters.get(boxDefinition);
				break;
			}
		}
		/**
		 * We should only get here when we didn't call prepare. So we better
		 * call it now. But since this routine is called in parallel, we have to
		 * synchronize this block of code.
		 */
		if (winner == null) {
			synchronized (this) {
				final double nmiBuffer = 45.0;
				final BoxDefinition newBoxDefinition = new BoxDefinition(_simCase,
						_model, refSeconds, latLng, nmiBuffer);
				incrementalPrepare(-1L, null, newBoxDefinition);
				finishPrepare();
				/** Now try again. */
				for (final BoxDefinition boxDefinition : _uvGetters.keySet()) {
					if (boxDefinition.contains(refSeconds, latLng)) {
						winner = _uvGetters.get(boxDefinition);
						return winner.getDataForOnePointAndTime(_simCase, refSeconds,
								latLng, interpolationMode);
					}
				}
			}
		}
		/**
		 * Take the last one you see if you can't get a perfect fit. You should
		 * always get a perfect fit.
		 */
		if (winner == null) {
			winner = _uvGetters.lastEntry().getValue();
		}
		final NetCdfCurrentsUvGetter netCdfCurrentsUvGetter =
				(NetCdfCurrentsUvGetter) winner;
		final DataForOnePointAndTime currentsUv;
		if (netCdfCurrentsUvGetter.isEmpty(_simCase)) {
			currentsUv = new DataForOnePointAndTime(0f, 0f, 0f, 0f, 0f, 0f);
		} else {
			currentsUv = netCdfCurrentsUvGetter.getCurrentData(refSeconds, latLng,
					interpolationMode);
		}
		return currentsUv;
	}

	@Override
	public SummaryRefSecs getSummaryForRefSecs(final CircleOfInterest coi,
			final long refSecs, final int iView,
			final boolean interpolateInTime) {
		return getSummaryForRefSecsSXYZ(coi, refSecs, iView);
	}

	@Override
	public long getHalfLifeSecs(final int overallIndex) {
		return _halfLifeSecs;
	}

	@Override
	public long getPreDistressHalfLifeSecs() {
		return _preDistressHalfLifeSecs;
	}

	@Override
	public void writeElement(final Element outputDriftsElement,
			final Element inputDriftsElement, final Model model) {
		writeFixedPart(outputDriftsElement);
	}

	@Override
	protected NetCdfUvGetter buildNetCdfUvGetter(final String uriString,
			final NetcdfFile netCdfFile)
			throws NetCdfUvGetter.NetCdfUvGetterException {
		try {
			return new NetCdfCurrentsUvGetter(_simCase, _model, uriString,
					netCdfFile, _dU, _dV, _altDU, _altDV, _halfLifeSecs,
					_preDistressHalfLifeSecs);
		} catch (final NetCdfUvGetterException e) {
			SimCaseManager.standardLogError(_simCase, e);
		}
		return null;
	}

	@Override
	void addBoxDefinitionToModel(final BoxDefinition boxDefinition) {
		_model.addCurrentsBoxDefinition(boxDefinition);
	}

	@Override
	void closeNetCdfUvGetter(final NetCdfUvGetter netCdfUvGetter) {
		final NetCdfCurrentsUvGetter netCdfCurrentsUvGetter =
				(NetCdfCurrentsUvGetter) netCdfUvGetter;
		netCdfCurrentsUvGetter.close(getInterpolationMode());
	}

	@Override
	public boolean isEmpty(final SimCaseManager.SimCase simCase) {
		final boolean b = super.isEmpty(simCase);
		return b;
	}

	@Override
	public void freeMemory() {
		freeDynamicEnvGetterMemory();
	}

	@Override
	public int[] getFirstSampleAndPredictionIndex(final int nViews) {
		return null;
	}

	@Override
	public boolean hasAuxiliaryProcessing() {
		return false;
	}

	@Override
	public DistressStateVector fillInStateVectorsIfAppropriate(
			final SimCase simCase, final WindsUvGetter windsUvGetter,
			final Scenario scenario, final long[] simSecsS,
			final DistressStateVector distressStateVector, final long simSecs) {
		return distressStateVector;
	}
}
