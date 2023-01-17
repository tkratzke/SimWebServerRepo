#pragma once

#include <SimLib.h>
#include <string>

/** Using the indirect SimCodeHookImpl so if we ever
figure out how to reset a SimCodeHookImpl, we'll be set. */
// #define WorkingClass SimCodeHookImpl
#define WorkingClass BufferToImpl

class WorkingClass;

class SIMLIB_API SimPattern;

class SIMLIB_API LobsEllipse;

class SIMLIB_API NavCalc;

class SIMLIB_API SimCodeHook {
	WorkingClass *_workingObject;

public:
	SimCodeHook(const char *simLibDirName = "SimLib");
	void callPrintArgsMain();
	double acosX(double x);
	std::string getVersionName();
	SimPattern *makePattern(
		/* Self-explanatory: */
		const double rawSpeedKts, const long startSecs, const long durationSecs,

		/* Self-explanatory and apply to all 3 PatternKinds:*/
		const double centerLat, const double centerLng,
		const double orntn, const bool firstTurnRight,

		/** minTsNmi and fxdTsNmi apply only to LP and SS.
		minTsNmi should be at least 0.1, which is the track-spacing increment.
		excBufferNmi applies to all 3 and should be positive. */
		const double minTsNmi, const double fxdTsNmi, const double excBufferNmi,

		/** For LP, both lenNmi and widNmi are positive.  For SS, only lenNmi is positive,
		for VS, neither is positive.  I only pay attention to ps for LP.
		*/
		const double lenNmi, const double widNmi, const bool ps,
		const char *const motionTypeId,

		/* The following applies only to LP and SS.  If minTsNmi is such that I can't
		fit the path into the box, then I have 2 choices:
		1. Expand the box specs (len and/or wid)
		2. Don't use all of the path.
		The following argument specifies which of these I do. */
		const bool expandSpecsIfNeeded = true
	);

	LobsEllipse *makeLobsEllipse(
		double *thresholds,
		int nLobs,
		double **lobs
	);

	/* First 2 arguments are (lat0,lng0).  If you want
	me to find the range and bearing, the 3rd and 4th arguments
	are (lat1,lng1) and you set the last argument to true.
	If you want me to find (lat1,lng1), then the 3rd and 4th
	arguments are (rangeNmi,bearing), and you set the last
	argument to false.  Regardless, you set the 5th argument
	as follows (copied from ConsoleApp.cpp):
	"GC" for GREAT_CIRCLE
	"RL" for RHUMBLINE
	"TC" for TANGENT_CYLINDER (practically speaking, this is Great Circle)
	"ML" for MID_LAT.
	*/
	NavCalc *makeNavCalc(
		const double lat0,
		const double lng0,
		const double lat1OrNmi,
		const double lng1OrHdg,
		const char *const motionTypeId,
		const bool findRangeBearing);

	double getLogOddsMaxPd(double ap0, double a1, double a2, double maxXValue);

	double computeSweepWidth(const char *eltString);

	~SimCodeHook(void);
};
