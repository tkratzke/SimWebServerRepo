package com.skagit.sarops.environment;

import java.util.BitSet;

import org.w3c.dom.Element;

import com.skagit.sarops.environment.SummaryRefSecs.SummaryBuilder;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.Scenario;
import com.skagit.sarops.model.io.ModelWriter;
import com.skagit.sarops.tracker.DistressStateVector;
import com.skagit.util.MathX;
import com.skagit.util.gshhs.CircleOfInterest;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.LatLng3;

/** Used to retrieve uv when the currents are deemed constant. */
public class ConstantCurrentsUvGetter implements CurrentsUvGetter, SummaryBuilder {
	final private Model _model;
	private SummaryRefSecs _summaryRefSecs;
	private boolean _haveLoggedInvalid;
	final private DataForOnePointAndTime _dataForOnePointAndTime;
	final private long _halfLifeSecs;
	final private long _preDistressHalfLifeSecs;

	public ConstantCurrentsUvGetter(final Model model, final double speed, final double direction,
			final double uStandardDeviation, final double vStandardDeviation, final long halfLifeSecs,
			final long preDistressHalfLifeSecs) {
		_model = model;
		/**
		 * Right now, we ignore the configuration file and simply form a circular
		 * bivariate normal around the tip of the Cartesian translation of the polar
		 * description that was input. 90 - direction is the normal way of looking at
		 * it, but switching sin and cos has the same effect.
		 */
		final float u = (float) (MathX.sinX(Math.toRadians(direction)) * speed);
		final float v = (float) (MathX.cosX(Math.toRadians(direction)) * speed);
		final float dU = (float) uStandardDeviation;
		final float dV = (float) vStandardDeviation;
		final float altDU = dU;
		final float altDV = dV;
		_dataForOnePointAndTime = new DataForOnePointAndTime(u, v, dU, dV, altDU, altDV);
		_halfLifeSecs = halfLifeSecs;
		_preDistressHalfLifeSecs = preDistressHalfLifeSecs;
		_haveLoggedInvalid = false;
	}

	@Override
	public DataForOnePointAndTime getCurrentData(final MyLogger logger, final long refSecs, final LatLng3 latLng,
			final String interpolationMode) {
		return _dataForOnePointAndTime;
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
	public void close(final String interpolationMode) {
	}

	@Override
	public void incrementalPrepare(final long secs, final LatLng3 latLng, final BoxDefinition boxDefinition) {
	}

	@Override
	public CurrentsUvGetter getCurrentsUvGetter2(final MyLogger logger, final BitSet iViews,
			final boolean interpolateInTime) {
		return null;
	}

	@Override
	public void finishPrepare() {
	}

	@Override
	public String[] getViewNames() {
		return new String[] {
				"Currents"
		};
	}

	@Override
	public void writeElement(final Element outputDriftsElement, final Element inputDriftsElement, final Model model) {
		ModelWriter.writeConstantDistribution(outputDriftsElement, inputDriftsElement);
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
	public boolean isEmpty(final MyLogger logger) {
		final boolean valid = _dataForOnePointAndTime != null && _dataForOnePointAndTime.isValid();
		if (!valid && !_haveLoggedInvalid && logger != null) {
			MyLogger.err(logger, "Invalid ConstantCurrentsUvGetter!!");
			_haveLoggedInvalid = true;
		}
		return !valid;
	}

	@Override
	public void freeMemory() {
	}

	@Override
	public SummaryRefSecs getSummaryForRefSecs(final CircleOfInterest coi, final long refSecs, final int iView,
			final boolean interpolateInTime) {

		if (_summaryRefSecs != null) {
			return _summaryRefSecs;
		}
		synchronized (this) {
			if (_summaryRefSecs != null) {
				return _summaryRefSecs;
			}
			final float u = _dataForOnePointAndTime.getValue(NetCdfUvGetter.DataComponent.U);
			final float v = _dataForOnePointAndTime.getValue(NetCdfUvGetter.DataComponent.V);
			final float[] uv = new float[] {
					u, v
			};
			_summaryRefSecs = new SummaryRefSecs(_model.getCoi(), "ConstantCurrents", uv, /* forRiver= */false);
		}
		return _summaryRefSecs;
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
	public DistressStateVector fillInStateVectorsIfAppropriate(final WindsUvGetter windsUvGetter,
			final Scenario scenario, final long[] simSecsS, final DistressStateVector distressStateVector,
			final long simSecs) {
		return null;
	}
}
