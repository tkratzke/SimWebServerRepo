#include "StdAfx.h"

#include <SimCodeHook.h>

#include <BufferToImpl.h>
#include <SimCodeHookImpl.h>

SimCodeHook::SimCodeHook(const char *simLibDirName) :
	_workingObject(new WorkingClass(simLibDirName)) {
}

void SimCodeHook::callPrintArgsMain() {
	std::vector<std::string> strings;
	strings.push_back("Extra Arg");
	_workingObject->callPrintArgsMain(strings);
}

double SimCodeHook::acosX(double x) {
	return _workingObject->acos(x);
}

std::string SimCodeHook::getVersionName() {
	return _workingObject->getVersionName();
}

SimPattern *SimCodeHook::makePattern(
	const double rawSpeedKts, const long startSecs, const long durationSecs,
	const double centerLat, const double centerLng,
	const double orntn, const bool firstTurnRight,
	const double minTsNmi, const double fixedTsNmi, const double excBufferNmi,
	const double lengthNmi, const double widthNmi, const bool ps,
	const char *const motionTypeId,
	const bool expandSpecsIfNeeded
) {
	return _workingObject->makePattern(
		rawSpeedKts, startSecs, durationSecs,
		centerLat, centerLng, orntn, firstTurnRight,
		minTsNmi, fixedTsNmi, excBufferNmi,
		lengthNmi, widthNmi, ps,
		motionTypeId, expandSpecsIfNeeded
	);
}

LobsEllipse *SimCodeHook::makeLobsEllipse(
	double *thresholds,
	int nLobs,
	double **lobs
) {
	if (thresholds == NULL) {
		// SIM.SCENARIO.wangsnessAreaThreshold.DEFAULT = 3000 NMSq
		// SIM.SCENARIO.wangsnessDistanceThreshold.DEFAULT = 500 NM
		// SIM.SCENARIO.wangsnessSemiMajorThreshold.DEFAULT = 250 NM
		// SIM.SCENARIO.wangsnessMajorToMinor.DEFAULT = 64
		// SIM.SCENARIO.wangsnessMinAngle.DEFAULT = 5 Degs
		const double areaThresholdInSqNmi = 3000.0;
		const double distanceThresholdInNmi = 500.0;
		const double semiMajorThresholdInNmi = 250.0;
		const double majorToMinorThreshold = 64.0;
		const double minAngleInDegs = 5.0;
		double *thresholds = new double[5];
		thresholds[0] = areaThresholdInSqNmi;
		thresholds[1] = distanceThresholdInNmi;
		thresholds[2] = semiMajorThresholdInNmi;
		thresholds[3] = majorToMinorThreshold;
		thresholds[4] = minAngleInDegs;
		LobsEllipse *lobsEllipseP = makeLobsEllipse(thresholds, nLobs, lobs);
		delete[] thresholds;
		return lobsEllipseP;
	}
	return _workingObject->makeLobsEllipse((jdouble*)thresholds, (jint)nLobs, (jdouble**)lobs);
}

NavCalc *SimCodeHook::makeNavCalc(
	const double lat0,
	const double lng0,
	const double lat1OrNmi,
	const double lng1OrHdg,
	const char *const motionTypeId,
	const bool findRangeBearing
) {
	return _workingObject->makeNavCalc(lat0, lng0, lat1OrNmi, lng1OrHdg, motionTypeId, findRangeBearing);
}

double SimCodeHook::getLogOddsMaxPd(double ap0, double a1, double a2, double maxXValue) {
	double maxPd = _workingObject->getLogOddsMaxPd(ap0, a1, a2, maxXValue);
	return maxPd;
}

double SimCodeHook::computeSweepWidth(const char *eltString) {
	double sw = _workingObject->computeSweepWidth(eltString);
	return sw;
}

SimCodeHook::~SimCodeHook() {
	if (_workingObject != NULL) {
		delete _workingObject;
	}
}
