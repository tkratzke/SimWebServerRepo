#pragma once

#include <SimCodeHookImpl.h>

class BufferToImpl
{
	const char *const _simLibDirName;
	SimCodeHookImpl *_simCodeHookImpl;
	void resetImpl();

public:
	BufferToImpl(const char *simLibDirName);
	~BufferToImpl();

	bool callPrintArgsMain(std::vector<std::string> &strings);
	std::string getVersionName();
	double acos(double x);

	SimPattern *makePattern(
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
		const jboolean expandSpecsIfNeeded
	);

	LobsEllipse *makeLobsEllipse(
		jdouble *thresholds,
		jint nLobs,
		jdouble **lobs
	);

	LobsEllipse *makeLobsEllipse(
		jboolean throwException
	);

	NavCalc *makeNavCalc(
		const jdouble lat0,
		const jdouble lng0,
		const jdouble arg3,
		const jdouble arg4,
		const char *const motionTypeId,
		const jboolean findRangeBearing
	);

	jdouble getLogOddsMaxPd(jdouble ap0, jdouble a1, jdouble a2, jdouble maxXValue);

	jdouble computeSweepWidth(const char *const eltString);
};
