package com.skagit.sarops.model.preDistressModel.sail;

import java.util.Comparator;
import java.util.TreeSet;

import org.apache.commons.math3.distribution.BetaDistribution;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.model.io.ModelReader;
import com.skagit.sarops.model.io.ModelReader.ReaderException;
import com.skagit.sarops.model.io.ModelReader.StringPlus;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.ElementIterator;
import com.skagit.util.LsFormatter;

public class SailData {

	final public static long _TenYears = 365 * 24 * 3600 * 10;
	final public Model _model;
	final public String _sailorType;
	final public Polars _polars;
	/** All speeds are in kts. */
	final public double _enterHoveToKts;
	final public double _exitHoveToKts;
	final public double _hoveToMinKts;
	final public double _hoveToMaxKts;
	final public double _hoveToBearingSd;
	final public double _hoveToOffsetFromUpwindMean;
	final public double _enterMotorKts;
	final public double _exitMotorKts;
	final public long _checkIntervalSecs;
	final BetaDistribution _betaDistribution;
	final double _lowSpeedMultiplier;
	final double _highSpeedMultiplier;
	final double _lowForbiddenAngleIncrease;
	final double _highForbiddenAngleIncrease;
	final double _angleOffRhumblineSd;
	final double _lowProportionOfDistanceToTargetToIgnoreOffsetAngle;
	final double _highProportionOfDistanceToTargetToIgnoreOffsetAngle;
	final public double _arrivalRadiusNmi;
	final long _meanAverageTackSecs;
	final long _averageTackSecsSd;
	final double _probZeroZeroMotoring;
	final double _nearToEndNmi;

	public static Comparator<SailData> _BySailorType =
			new Comparator<>() {

				@Override
				public int compare(final SailData sailData0,
						final SailData sailData1) {
					final String sailorType0 =
							sailData0 == null ? null : sailData0._sailorType;
					final String sailorType1 =
							sailData1 == null ? null : sailData1._sailorType;
					if ((sailorType0 == null) != (sailorType1 == null)) {
						return sailorType0 == null ? -1 : 1;
					}
					if (sailorType0 == null) {
						return 0;
					}
					return sailorType0.compareTo(sailorType1);
				}
			};

	public SailData(final SimCaseManager.SimCase simCase, final Model model,
			final Element sailorTypeElement,
			final TreeSet<StringPlus> stringPluses) {
		_model = model;
		_sailorType = sailorTypeElement.getAttribute("Type");
		/** Try filling in polars from the xml. */
		final Polars polars =
				new Polars(simCase, sailorTypeElement, stringPluses);
		if (polars._speedAndOneSidedTables != null &&
				polars._speedAndOneSidedTables.length > 0 &&
				polars._lengthOverallFt > 0d) {
			_polars = polars;
		} else {
			final String[] attributeNames0 =
					new String[] { Polars._PolarsWorkBookAttributeName, //
							Polars._PolarsSheetAttributeName, //
							Polars._ManufacturerAttributeName, //
							Polars._BoatClassAttributeName //
					};
			String polarsWorkbookName = null;
			String polarsSheetName = null;
			String manufacturer = null;
			String boatClass = null;
			for (final String attributeName : attributeNames0) {
				if (attributeName.equals(Polars._ManufacturerAttributeName)) {
					manufacturer = ModelReader.getString(simCase, sailorTypeElement,
							attributeName, "", null);
				} else if (attributeName.equals(Polars._BoatClassAttributeName)) {
					boatClass = ModelReader.getString(simCase, sailorTypeElement,
							attributeName, "", null);
				} else if (attributeName
						.equals(Polars._PolarsWorkBookAttributeName)) {
					final String defaultWorkbookName = null;
					polarsWorkbookName = ModelReader.getString(simCase,
							sailorTypeElement, attributeName, defaultWorkbookName, null);
				} else if (attributeName.equals(Polars._PolarsSheetAttributeName)) {
					/**
					 * Using null for the sheet name will result in using the first
					 * sheet.
					 */
					final String defaultSheetName = null;
					polarsSheetName = ModelReader.getString(simCase,
							sailorTypeElement, attributeName, defaultSheetName, null);
				}
			}
			_polars = new Polars(simCase, polarsWorkbookName, polarsSheetName,
					manufacturer, boatClass);
		}

		/**
		 * Add the data that you do not get from the Polars; they come from some
		 * sub-element of the SAIL element.
		 */
		final Element sailElement = (Element) sailorTypeElement.getParentNode();

		final Element sailorTypeSpecElement0 =
				ElementIterator.getChildIgnoreCase(sailElement, _sailorType);
		final Element sailorTypeSpecElement;
		if (sailorTypeSpecElement0 == null) {
			final Document document = sailElement.getOwnerDocument();
			sailElement.appendChild(document.createElement(_sailorType));
			sailorTypeSpecElement =
					ElementIterator.getChildIgnoreCase(sailElement, _sailorType);
		} else {
			sailorTypeSpecElement = sailorTypeSpecElement0;
		}

		final String[] attributeNames =
				new String[] { "EnterHoveToSpeed", "ExitHoveToSpeed", //
						"HoveToMinSpeed", "HoveToMaxSpeed", //
						"HoveToOffsetFromUpwindMean", "HoveToBearingSd", //
						"EnterMotorSpeed", "ExitMotorSpeed", //
						"CheckIntervalTime", //
						"BetaA", "BetaB", //
						"LowSpeedMultiplier", "HighSpeedMultiplier", //
						"LowForbiddenAngleIncrease", "HighForbiddenAngleIncrease", //
						"AngleOffRhumblineSd", //
						"LowProportionOfDistanceToTargetToIgnoreOffsetAngle", //
						"HighProportionOfDistanceToTargetToIgnoreOffsetAngle", //
						"ArrivalRadius", //
						"MeanAverageTackSecs", "AverageTackSecsSd", //
						"ProbZeroZeroMotoring", "NearToEnd" };

		double enterHoveToSpeed = Double.NaN;
		double exitHoveToSpeed = Double.NaN;
		double hoveToMinSpeed = Double.NaN;
		double hoveToMaxSpeed = Double.NaN;
		double hoveToBearingSd = Double.NaN;
		double hoveToOffsetFromUpwindMean = Double.NaN;
		double enterMotorSpeed = Double.NaN;
		double exitMotorSpeed = Double.NaN;
		long checkIntervalSecs = Long.MIN_VALUE;
		double betaA = Double.NaN;
		double betaB = Double.NaN;
		double lowSpeedMultiplier = Double.NaN;
		double highSpeedMultiplier = Double.NaN;
		double lowForbiddenAngleIncrease = Double.NaN;
		double highForbiddenAngleIncrease = Double.NaN;
		double angleOffRhumblineSd = Double.NaN;
		double lowProportionOfDistanceToTargetToIgnoreOffsetAngle = Double.NaN;
		double highProportionOfDistanceToTargetToIgnoreOffsetAngle = Double.NaN;
		double arrivalRadiusNmi = Double.NaN;
		long meanAverageTackSecs = _TenYears;
		long averageTackSecsSd = 0L;
		double probZeroZeroMotoring = Double.NaN;
		double nearToEndNmi = Double.NaN;

		for (final String attributeName : attributeNames) {
			try {
				if (attributeName.equals("EnterHoveToSpeed")) {
					enterHoveToSpeed =
							ModelReader.getDouble(simCase, sailorTypeSpecElement,
									attributeName, " kts", Double.NaN, stringPluses);
				} else if (attributeName.equals("ExitHoveToSpeed")) {
					exitHoveToSpeed =
							ModelReader.getDouble(simCase, sailorTypeSpecElement,
									attributeName, " kts", Double.NaN, stringPluses);
				} else if (attributeName.equals("HoveToMinSpeed")) {
					hoveToMinSpeed =
							ModelReader.getDouble(simCase, sailorTypeSpecElement,
									attributeName, " kts", Double.NaN, stringPluses);
				} else if (attributeName.equals("HoveToMaxSpeed")) {
					hoveToMaxSpeed =
							ModelReader.getDouble(simCase, sailorTypeSpecElement,
									attributeName, " kts", Double.NaN, stringPluses);
				} else if (attributeName.equals("HoveToOffsetFromUpwindMean")) {
					hoveToOffsetFromUpwindMean =
							ModelReader.getDouble(simCase, sailorTypeSpecElement,
									attributeName, " degrees", Double.NaN, stringPluses);
				} else if (attributeName.equals("HoveToBearingSd")) {
					hoveToBearingSd =
							ModelReader.getDouble(simCase, sailorTypeSpecElement,
									attributeName, " degrees", Double.NaN, stringPluses);
				} else if (attributeName.equals("EnterMotorSpeed")) {
					enterMotorSpeed =
							ModelReader.getDouble(simCase, sailorTypeSpecElement,
									attributeName, " kts", 4, stringPluses);
				} else if (attributeName.equals("ExitMotorSpeed")) {
					exitMotorSpeed =
							ModelReader.getDouble(simCase, sailorTypeSpecElement,
									attributeName, " kts", 6, stringPluses);
				} else if (attributeName.equals("CheckIntervalTime")) {
					checkIntervalSecs =
							ModelReader.getLong(simCase, sailorTypeSpecElement,
									attributeName, " secs", 3600, stringPluses);
				} else if (attributeName.equals("BetaA")) {
					betaA = ModelReader.getDouble(simCase, sailorTypeSpecElement,
							attributeName, "", Double.NaN, stringPluses);
				} else if (attributeName.equals("BetaB")) {
					betaB = ModelReader.getDouble(simCase, sailorTypeSpecElement,
							attributeName, "", Double.NaN, stringPluses);
				} else if (attributeName.equals("LowSpeedMultiplier")) {
					lowSpeedMultiplier =
							ModelReader.getDouble(simCase, sailorTypeSpecElement,
									attributeName, "", Double.NaN, stringPluses);
				} else if (attributeName.equals("HighSpeedMultiplier")) {
					highSpeedMultiplier =
							ModelReader.getDouble(simCase, sailorTypeSpecElement,
									attributeName, "", Double.NaN, stringPluses);
				} else if (attributeName.equals("LowForbiddenAngleIncrease")) {
					lowForbiddenAngleIncrease =
							ModelReader.getDouble(simCase, sailorTypeSpecElement,
									attributeName, " degrees", Double.NaN, stringPluses);
				} else if (attributeName.equals("HighForbiddenAngleIncrease")) {
					highForbiddenAngleIncrease =
							ModelReader.getDouble(simCase, sailorTypeSpecElement,
									attributeName, " degrees", Double.NaN, stringPluses);
				} else if (attributeName.equals("AngleOffRhumblineSd")) {
					angleOffRhumblineSd =
							ModelReader.getDouble(simCase, sailorTypeSpecElement,
									attributeName, " degrees", Double.NaN, stringPluses);
				} else if (attributeName
						.equals("LowProportionOfDistanceToTargetToIgnoreOffsetAngle")) {
					lowProportionOfDistanceToTargetToIgnoreOffsetAngle =
							ModelReader.getDouble(simCase, sailorTypeSpecElement,
									attributeName, "", Double.NaN, stringPluses);
				} else if (attributeName.equals(
						"HighProportionOfDistanceToTargetToIgnoreOffsetAngle")) {
					highProportionOfDistanceToTargetToIgnoreOffsetAngle =
							ModelReader.getDouble(simCase, sailorTypeSpecElement,
									attributeName, "", Double.NaN, stringPluses);
				} else if (attributeName.equals("ArrivalRadius")) {
					arrivalRadiusNmi =
							ModelReader.getDouble(simCase, sailorTypeSpecElement,
									attributeName, " NM", Double.NaN, stringPluses);
				} else if (attributeName.equals("MeanAverageTackSecs")) {
					meanAverageTackSecs =
							ModelReader.getLong(simCase, sailorTypeSpecElement,
									attributeName, " secs", -1L, stringPluses);
				} else if (attributeName.equals("AverageTackSecsSd")) {
					averageTackSecsSd =
							ModelReader.getLong(simCase, sailorTypeSpecElement,
									attributeName, " secs", 0L, stringPluses);
				} else if (attributeName.equals("ProbZeroZeroMotoring")) {
					probZeroZeroMotoring =
							ModelReader.getDouble(simCase, sailorTypeSpecElement,
									attributeName, "", Double.NaN, stringPluses);
				} else if (attributeName.equals("NearToEnd")) {
					nearToEndNmi =
							ModelReader.getDouble(simCase, sailorTypeSpecElement,
									attributeName, " NM", Double.NaN, stringPluses);
				}
			} catch (final ReaderException e) {
			}
		}
		_enterHoveToKts = enterHoveToSpeed;
		_exitHoveToKts = exitHoveToSpeed;
		_hoveToMinKts = hoveToMinSpeed;
		_hoveToMaxKts = hoveToMaxSpeed;
		_hoveToOffsetFromUpwindMean = hoveToOffsetFromUpwindMean;
		_hoveToBearingSd = hoveToBearingSd;
		_enterMotorKts = enterMotorSpeed;
		_exitMotorKts = exitMotorSpeed;
		_checkIntervalSecs = checkIntervalSecs;
		_betaDistribution = new BetaDistribution(betaA, betaB);
		_lowSpeedMultiplier = lowSpeedMultiplier;
		_highSpeedMultiplier = highSpeedMultiplier;
		_lowForbiddenAngleIncrease = lowForbiddenAngleIncrease;
		_highForbiddenAngleIncrease = highForbiddenAngleIncrease;
		_angleOffRhumblineSd = angleOffRhumblineSd;
		_lowProportionOfDistanceToTargetToIgnoreOffsetAngle =
				lowProportionOfDistanceToTargetToIgnoreOffsetAngle;
		_highProportionOfDistanceToTargetToIgnoreOffsetAngle =
				highProportionOfDistanceToTargetToIgnoreOffsetAngle;
		_arrivalRadiusNmi = arrivalRadiusNmi;
		_meanAverageTackSecs = meanAverageTackSecs;
		_averageTackSecsSd = averageTackSecsSd;
		_probZeroZeroMotoring = probZeroZeroMotoring;
		_nearToEndNmi = nearToEndNmi;
	}

	public double getSpeedMultiplier(final double sailorQuality) {
		final double speedMultiplier = _lowSpeedMultiplier +
				sailorQuality * (_highSpeedMultiplier - _lowSpeedMultiplier);
		return speedMultiplier;
	}

	public double getForbiddenAngleIncrease(final double sailorQuality) {
		final double forbiddenAngleIncrease =
				_lowForbiddenAngleIncrease + (1d - sailorQuality) *
						(_highForbiddenAngleIncrease - _lowForbiddenAngleIncrease);
		return forbiddenAngleIncrease;
	}

	public Polars getPolars() {
		return _polars;
	}

	public void write(final LsFormatter lsFormatter,
			final Element sailElement, final Model model) {
		final Element sailorTypeElement =
				lsFormatter.newChild(sailElement, "SAILOR_TYPE");
		sailorTypeElement.setAttribute("Type", _sailorType);
		final Polars polars = _polars;
		polars.addToElement(lsFormatter, sailorTypeElement);
	}

	public boolean deepEquals(final SailData sailData) {
		// TODO
		return sailData == this;
	}

}