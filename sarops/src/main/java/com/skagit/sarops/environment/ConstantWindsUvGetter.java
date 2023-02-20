package com.skagit.sarops.environment;

import java.util.BitSet;

import org.w3c.dom.Element;

import com.skagit.sarops.environment.SummaryRefSecs.SummaryBuilder;
import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.io.ModelWriter;
import com.skagit.util.MathX;
import com.skagit.util.gshhs.CircleOfInterest;
import com.skagit.util.myLogger.MyLogger;
import com.skagit.util.navigation.LatLng3;

/** Used to retrieve uv when the winds are deemed constant. */
public class ConstantWindsUvGetter implements WindsUvGetter, SummaryBuilder {
	final private Model _model;
	private SummaryRefSecs _summaryRefSecs;
	final private DataForOnePointAndTime _dnWindData;
	final private long _halfLifeSecs;
	final private long _preDistressHalfLifeSecs;
	final private boolean _haveLoggedInvalid;

	public ConstantWindsUvGetter(final Model model, final double speed, final double downwindDirection, final double dU,
			final double dV, final double altDU, final double altDV, final long halfLifeSecs,
			final long preDistressHalfLifeSecs) {
		_model = model;
		/**
		 * Right now, we ignore the configuration file and simply form a circular
		 * bivariate normal around the tip of the Cartesian translation of the polar
		 * description that was input.
		 */
		final float dnWindU = (float) (MathX.cosX(Math.toRadians(90d - downwindDirection)) * speed);
		final float dnWindV = (float) (MathX.sinX(Math.toRadians(90d - downwindDirection)) * speed);
		_dnWindData = new DataForOnePointAndTime(dnWindU, dnWindV, (float) dU, (float) dV, (float) altDU,
				(float) altDV);
		_halfLifeSecs = halfLifeSecs;
		_preDistressHalfLifeSecs = preDistressHalfLifeSecs;
		_haveLoggedInvalid = false;
		final CircleOfInterest coi = _model.getCoi();
		_summaryRefSecs = getSummaryForRefSecs(coi, /* refSecs= */0L, /* iView= */0, /* interpolateInTime= */false);
	}

	@Override
	public DataForOnePointAndTime getDownWindData(final long refSecs, final LatLng3 latLng,
			final String interpolationMode) {
		return _dnWindData.clone();
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
	public boolean useRandomDuringUpdates() {
		return true;
	}

	@Override
	public WindsUvGetter getWindsUvGetter2(final BitSet iViews, final boolean interpolateInTime) {
		return null;
	}

	@Override
	public String[] getViewNames() {
		return new String[] {
				"Winds"
		};
	}

	@Override
	public void incrementalPrepare(final long refSecs, final LatLng3 latLng, final BoxDefinition boxDefinition) {
	}

	@Override
	public void finishPrepare() {
	}

	@Override
	public void writeElement(final Element outputWindsElement, final Element inputWindsElement, final Model model) {
		ModelWriter.writeConstantDistribution(outputWindsElement, inputWindsElement);
	}

	@Override
	public boolean prepareIsTrivial() {
		return true;
	}

	@Override
	public boolean isEmpty(final MyLogger logger) {
		final boolean valid = _dnWindData != null && _dnWindData.isValid();
		if (!valid && !_haveLoggedInvalid) {
			MyLogger.err(logger, "Invalid ConstantWindsUvGetter!  Using 0.");
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
			final float u = _dnWindData.getValue(NetCdfUvGetter.DataComponent.U);
			final float v = _dnWindData.getValue(NetCdfUvGetter.DataComponent.V);
			final float[] uv = new float[] {
					u, v
			};
			_summaryRefSecs = new SummaryRefSecs(coi, "ConstantWinds", uv, /* forRiver= */false);
		}
		return _summaryRefSecs;
	}
}
