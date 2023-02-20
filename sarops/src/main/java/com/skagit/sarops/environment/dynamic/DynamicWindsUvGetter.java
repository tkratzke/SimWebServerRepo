package com.skagit.sarops.environment.dynamic;

import java.util.BitSet;

import org.w3c.dom.Element;

import com.skagit.sarops.environment.BoxDefinition;
import com.skagit.sarops.environment.DataForOnePointAndTime;
import com.skagit.sarops.environment.NetCdfUvGetter;
import com.skagit.sarops.environment.NetCdfUvGetter.NetCdfUvGetterException;
import com.skagit.sarops.environment.NetCdfWindsUvGetter;
import com.skagit.sarops.environment.SummaryRefSecs;
import com.skagit.sarops.environment.SummaryRefSecs.SummaryBuilder;
import com.skagit.sarops.environment.WindsUvGetter;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.gshhs.CircleOfInterest;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.LatLng3;

import ucar.nc2.NetcdfFile;

public class DynamicWindsUvGetter extends DynamicEnvUvGetter implements WindsUvGetter, SummaryBuilder {
	final private static int _MaxUvGettersToKeep = 100;
	final private boolean _useStandardDirection;

	public DynamicWindsUvGetter(final SimCaseManager.SimCase simCase, final Model model, final String interpolationMode,
			final long halfLifeSecs, final long preDistressHalfLifeSecs, final boolean useStandardDirection,
			final double uStandardDeviation, final double vStandardDeviation, final String scheme,
			final String userInfo, final String host, final int port, final String path, final String clientKey,
			final String sourceID, final String sourceName, final int outputType, final int timeOut,
			final boolean zipped) {
		super(simCase, model, _WindsTag, _MaxUvGettersToKeep, interpolationMode, halfLifeSecs, preDistressHalfLifeSecs,
				uStandardDeviation, vStandardDeviation, scheme, userInfo, host, port, path, clientKey, sourceID,
				sourceName, outputType, timeOut, zipped);
		_useStandardDirection = useStandardDirection;
	}

	@Override
	public void incrementalPrepare(final long secs, final LatLng3 latLng, final BoxDefinition inputBoxDefinition) {
		cacheNecessaryBoxDefinition(secs, latLng, inputBoxDefinition);
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
	public void close(final String interpolationMode) {
		/**
		 * Closing is done by each NetCdfWindsUvGetter. Hence, we do nothing here.
		 */
	}

	@Override
	public boolean useRandomDuringUpdates() {
		return true;
	}

	@Override
	public WindsUvGetter getWindsUvGetter2(final BitSet iViews0, final boolean interpolateInTime0) {
		/** If iView is not 0, ..., well, we're just not up to that. */
		if (iViews0 == null || !iViews0.get(0)) {
			return null;
		}
		return new WindsUvGetter() {
			@Override
			public DataForOnePointAndTime getDownWindData(final long refSecs, final LatLng3 latLng,
					final String interpolationMode) {
				return getDataForOnePointAndTime(refSecs, latLng, getInterpolationMode());
			}

			@Override
			public long getHalfLifeSecs() {
				return DynamicWindsUvGetter.this.getHalfLifeSecs();
			}

			@Override
			public long getPreDistressHalfLifeSecs() {
				return DynamicWindsUvGetter.this.getPreDistressHalfLifeSecs();
			}

			@Override
			public void incrementalPrepare(final long secs, final LatLng3 latLng, final BoxDefinition boxDefinition) {
			}

			@Override
			public void finishPrepare() {
			}

			@Override
			public boolean prepareIsTrivial() {
				return true;
			}

			@Override
			public void close(final String inerpolationMode) {
			}

			@Override
			public boolean useRandomDuringUpdates() {
				return true;
			}

			@Override
			public WindsUvGetter getWindsUvGetter2(final BitSet iViews1, final boolean interpolateInTime1) {
				return null;
			}

			@Override
			public String[] getViewNames() {
				return null;
			}

			@Override
			public void writeElement(final Element outputWindsElement, final Element inputWindsElement,
					final Model model) {
			}

			@Override
			public boolean isEmpty(final MyLogger logger) {
				final DynamicWindsUvGetter dynamicWindsUvGetter = DynamicWindsUvGetter.this;
				return dynamicWindsUvGetter.isEmpty(logger);
			}

			@Override
			public void freeMemory() {
			}
		};
	}

	@Override
	public String[] getViewNames() {
		return new String[] {
				"Winds"
		};
	}

	@Override
	public DataForOnePointAndTime getDownWindData(final long refSecs, final LatLng3 latLng,
			final String interpolationMode) {
		NetCdfUvGetter winner = null;
		for (final BoxDefinition boxDefinition : _uvGetters.keySet()) {
			if (boxDefinition.contains(refSecs, latLng)) {
				winner = _uvGetters.get(boxDefinition);
				break;
			}
		}
		/**
		 * We should only get here when we didn't call prepare. So we better call it
		 * now. But since this routine is called in parallel, we have to synchronize
		 * this block of code. Also, chances are that we don't want a lot of time and we
		 * do want a lot of breadth. We also assume we wish to go forward.
		 */
		final MyLogger logger = _simCase.getLogger();
		if (winner == null) {
			synchronized (this) {
				final double nmiBuffer = 60d;
				final BoxDefinition newBoxDefinition = new BoxDefinition(_simCase, _model, refSecs, latLng, nmiBuffer);
				incrementalPrepare(-1L, null, newBoxDefinition);
				finishPrepare();
				/** Now try again. */
				for (final BoxDefinition boxDefinition : _uvGetters.keySet()) {
					if (boxDefinition.contains(refSecs, latLng)) {
						winner = _uvGetters.get(boxDefinition);
						return winner.getDataForOnePointAndTime(_simCase, refSecs, latLng, interpolationMode);
					}
				}
			}
		}
		/**
		 * Take the last one you see if you can't get a perfect fit. You should always
		 * get a perfect fit.
		 */
		if (winner == null) {
			winner = _uvGetters.lastEntry().getValue();
		}
		final NetCdfWindsUvGetter netCdfWindsUvGetter = (NetCdfWindsUvGetter) winner;
		if (netCdfWindsUvGetter.isEmpty(logger)) {
			return new DataForOnePointAndTime(0f, 0f, 0f, 0f, 0f, 0f);
		}
		final DataForOnePointAndTime downWindUv = netCdfWindsUvGetter.getDownWindData(refSecs, latLng,
				interpolationMode);
		return downWindUv;
	}

	@Override
	public SummaryRefSecs getSummaryForRefSecs(final CircleOfInterest coi, final long refSecs, final int iView,
			final boolean interpolateInTime) {
		return getSummaryForRefSecsSXYZ(coi, refSecs, iView);
	}

	@Override
	public long getHalfLifeSecs() {
		return _halfLifeSecs;
	}

	@Override
	public long getPreDistressHalfLifeSecs() {
		return _preDistressHalfLifeSecs;
	}

	@Override
	public void writeElement(final Element outputWindsElement, final Element inputWindsElement, final Model model) {
		writeFixedPart(outputWindsElement);
	}

	@Override
	protected NetCdfUvGetter buildNetCdfUvGetter(final String uriString, final NetcdfFile netCdfFile)
			throws NetCdfUvGetter.NetCdfUvGetterException {
		try {
			return new NetCdfWindsUvGetter(_simCase, _model, uriString, netCdfFile, _dU, _dV, _altDU, _altDV,
					_halfLifeSecs, _preDistressHalfLifeSecs, _useStandardDirection);
		} catch (final NetCdfUvGetterException e) {
			SimCaseManager.standardLogError(_simCase, e);
		}
		return null;
	}

	@Override
	void addBoxDefinitionToModel(final BoxDefinition boxDefinition) {
		_model.addWindsBoxDefinition(boxDefinition);
	}

	@Override
	void closeNetCdfUvGetter(final NetCdfUvGetter netCdfUvGetter) {
		final NetCdfWindsUvGetter netCdfWindsUvGetter = (NetCdfWindsUvGetter) netCdfUvGetter;
		netCdfWindsUvGetter.close(getInterpolationMode());
	}

	@Override
	public boolean isEmpty(final MyLogger logger) {
		final boolean b = super.isEmpty(logger);
		return b;
	}

	@Override
	public void freeMemory() {
		freeDynamicEnvGetterMemory();
	}
}
