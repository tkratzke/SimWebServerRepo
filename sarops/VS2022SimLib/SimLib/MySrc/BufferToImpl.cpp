#include "StdAfx.h"
#include <BufferToImpl.h>

#include <MyHdrs/SimPattern.h>
#include <MyHdrs/LobsEllipse.h>
#include <MyHdrs/NavCalc.h>

BufferToImpl::BufferToImpl(const char *simLibDirName) :
	_simLibDirName(simLibDirName)
{
	_simCodeHookImpl = new SimCodeHookImpl(_simLibDirName);
}

bool BufferToImpl::callPrintArgsMain(std::vector<std::string> &strings)
{
	bool success = _simCodeHookImpl->callPrintArgsMain(strings);
	if (!success) {
		resetImpl();
		success = _simCodeHookImpl->callPrintArgsMain(strings);
	}
	return success;
}

std::string BufferToImpl::getVersionName()
{
	std::string versionName = _simCodeHookImpl->getVersionName();
	if (versionName.compare(SimCodeHookImpl::_BadString) == 0) {
		resetImpl();
		versionName = _simCodeHookImpl->getVersionName();
	}
	return versionName;
}

double BufferToImpl::acos(double x)
{
	double thetaRad = _simCodeHookImpl->acos(x);
	if (!(thetaRad >= 0.0)) {
		resetImpl();
		thetaRad = _simCodeHookImpl->acos(x);
	}
	return thetaRad;
}

SimPattern *BufferToImpl::makePattern(
	const jdouble rawSpeedKts,
	const jlong startUnixSecs,
	const jlong durationSecs,
	const jdouble centerLat,
	const jdouble centerLng,
	const jdouble orntn,
	const jboolean firstTurnRight,
	const jdouble minTsNmi,
	const jdouble fixedTsNmi,
	const jdouble excBufferNmi,
	const jdouble lengthNmi,
	const jdouble widthNmi,
	const jboolean ps,
	const char *const motionTypeId,
	const jboolean expandSpecsIfNeeded)
{
	SimPattern *simPatternP = _simCodeHookImpl->makeSimLibPattern(
		rawSpeedKts, startUnixSecs, durationSecs,
		centerLat, centerLng, orntn, firstTurnRight,
		minTsNmi, fixedTsNmi, excBufferNmi,
		lengthNmi, widthNmi, ps,
		motionTypeId, expandSpecsIfNeeded);
	if (simPatternP == NULL) {
		resetImpl();
		simPatternP = _simCodeHookImpl->makeSimLibPattern(
			rawSpeedKts, startUnixSecs, durationSecs,
			centerLat, centerLng, orntn, firstTurnRight,
			minTsNmi, fixedTsNmi, excBufferNmi,
			lengthNmi, widthNmi, ps,
			motionTypeId, expandSpecsIfNeeded);
	}
	if (simPatternP == NULL) {
		simPatternP = new SimPattern();
	}
	return simPatternP;
}

LobsEllipse *BufferToImpl::makeLobsEllipse(
	jdouble *thresholds,
	jint nLobs,
	jdouble **lobs)
{
	return _simCodeHookImpl->makeLobsEllipse(thresholds, nLobs, lobs);
}

LobsEllipse *BufferToImpl::makeLobsEllipse(
	jboolean throwException)
{
	return _simCodeHookImpl->makeLobsEllipse(throwException);
}

void BufferToImpl::resetImpl()
{
	/** NoOp until I can figure out how to create a clean SimCodeHookImpl. */
	// delete _simCodeHookImpl;
	// _simCodeHookImpl = new SimCodeHookImpl(_simLibDirName);
}

NavCalc *BufferToImpl::makeNavCalc(
	const jdouble lat0,
	const jdouble lng0,
	const jdouble arg3,
	const jdouble arg4,
	const char *const motionTypeId,
	const jboolean findRangeBearing)
{
	NavCalc *navCalcP = _simCodeHookImpl->makeNavCalc(lat0, lng0, arg3, arg4, motionTypeId, findRangeBearing);
	if (navCalcP == NULL) {
		resetImpl();
		navCalcP = _simCodeHookImpl->makeNavCalc(lat0, lng0, arg3, arg4, motionTypeId, findRangeBearing);
	}
	if (navCalcP == NULL) {
		navCalcP = new NavCalc();
	}
	return navCalcP;
}

double BufferToImpl::getLogOddsMaxPd(
	const double ap0,
	const double a1,
	const double a2,
	const double maxXValue)
{
	double d = _simCodeHookImpl->getLogOddsMaxPd(ap0, a1, a2, maxXValue);
	if (!(d >= 0.0)) {
		resetImpl();
		d = _simCodeHookImpl->getLogOddsMaxPd(ap0, a1, a2, maxXValue);
	}
	return d;
}

double BufferToImpl::computeSweepWidth(
	const char *const eltString)
{
	double sw = _simCodeHookImpl->computeSweepWidth(eltString);
	if (!(sw >= 0.0)) {
		resetImpl();
		sw = _simCodeHookImpl->computeSweepWidth(eltString);
	}
	return sw;
}

BufferToImpl::~BufferToImpl()
{
	delete _simCodeHookImpl;
}
