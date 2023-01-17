// ConsoleApp.cpp : Defines the entry point for the console application.

#ifndef USE_CONSOLE_APP2
#include "stdafx.h"

#ifdef USE_VLD
#include <vld.h>
#endif

#include <stdio.h>
#include <vector>
#include <string>
#include <iostream>
#include <fstream>

#include <CParameterGenerator.h>

#undef TEST_SIMLIB_PATTERN_A
#undef TEST_SIMLIB_PATTERN_B
#undef TEST_SIMLIB_PATTERN_C
#undef TEST_SIMLIB_PATTERN_D

#undef TEST_SIMLIB_PATTERN_VS
#define TEST_SIMLIB_PATTERN_SS

#undef TEST_MAKE_NAV_CALC
#undef TEST_LOG_ODDS_MAX_PD
#undef TEST_LOBS_ELLIPSE
#define TEST_SWEEPWIDTH

/**
There are 3 steps to building and running a program:
1. Compile it: You need the includes in <SimInstallDir>\SimLib\Include.
2. Link it: You need the SimLib.lib that's in <SimInstallDir>\SimLib\<Configuration>.
3. Run it: You need <SimInstallDir>\jre\bin\server or <SimInstallDir>\jre64\bin\server, and
<SimInstallDir>\SimLib\<Configuration> in your path.

Note: <Configuration> is either Debug or Release.
*/
#include <SimLib.h>
#include <SimCodeHook.h>
#include <SimPattern.h>
#include <LobsEllipse.h>
#include <NavCalc.h>

/** Machinery for checking memory leaks: */
#ifdef _DEBUG
#define _CRTDBG_MAP_ALLOC
#include <stdlib.h>
#include <crtdbg.h>
#define DBG_NEW new ( _NORMAL_BLOCK , __FILE__ , __LINE__ )
#define new DBG_NEW
#endif

/** JMH supplied debugging routines: */
void dumpOutOfBounds(std::ofstream &dumpFile, bool isOut, double bound, double value)
{
	if (isOut) {
		char buffer[1024];
		sprintf_s(buffer, "    <===== !!!! OUT OF BOUNDS !!! bound[%f] value[%f]", bound, value);
		dumpFile << buffer << std::flush;;
		std::cout << buffer << std::flush;;
	}
}

void dumpPoint(std::ofstream &dumpFile, char *prefix, double lat, double lng)
{
	dumpFile
		<< prefix
		<< "[" << lat << "," << lng << "]"
		<< std::flush;

	std::cout
		<< prefix
		<< "[" << lat << "," << lng << "]"
		<< std::flush;
}

const int NDoublesPerBearingCall = 7;

int main()
{
	/** Machinery for checking memory leaks: */
#ifdef _DEBUG
	int flag = _CrtSetDbgFlag(_CRTDBG_REPORT_FLAG);
	flag |= _CRTDBG_LEAK_CHECK_DF;
	_CrtSetDbgFlag(flag);
	_CrtDumpMemoryLeaks();
#endif

	SimCodeHook simCodeHook;
	/** Enclose everything important in {} so that automatic variables
	go out of scope and are deleted before the memory leak report. */
	{
		CParameterGenerator parameterGenerator;
		/**
		You need a "SimCodeHook."  You are responsible for deleting it.
		You need just one for as many calls as you're going to make.  All functionality is
		accessed by using methods of a single SimCodeHook.*/
		std::string versionName = simCodeHook.getVersionName();
		std::ofstream dumpFile;
		dumpFile.open("Dump.txt");
		dumpFile << "\nVersionName: " << versionName << "\n";
		std::cout << "\nVersionName: " << versionName << "\n";
		simCodeHook.callPrintArgsMain();
		dumpFile << "\n" << simCodeHook.acosX(0.0) << "\n" << std::flush;
		std::cout << "\n" << simCodeHook.acosX(0.0) << "\n" << std::flush;

#ifdef TEST_LOBS_ELLIPSE
		/** Test makeLobsEllipse. */
		/** Setting thresholds to NULL means use the default ones.
		* The defaults are in Sim.properties, but I reprint them here.
		* If they change in Sim.properties, there's no guarantee that
		* I'll get them changed in this comment!
		* SIM.SCENARIO.wangsnessAreaThreshold.DEFAULT = 3000 NMSq
		* SIM.SCENARIO.wangsnessDistanceThreshold.DEFAULT = 500 NM
		* SIM.SCENARIO.wangsnessSemiMajorThreshold.DEFAULT = 250 NM
		* SIM.SCENARIO.wangsnessMajorToMinor.DEFAULT = 64
		* SIM.SCENARIO.wangsnessMinAngle.DEFAULT = 5 Degs
		*/
		double *thresholds = NULL;

		/** The LOBs. */
#ifdef USE_OLD_CASE
		int nLobs = 3;
#else
		int nLobs = 2;
#endif
		double **lobData = new double*[nLobs];
		for (int k = 0; k < nLobs; ++k) {
			lobData[k] = new double[NDoublesPerBearingCall];
		}
		if (nLobs == 2) {
			lobData[0][0] = 36.13577780; // lat of origin
			lobData[0][1] = -75.82419440; // lng of origin
			lobData[0][2] = 1.00; // NM of probable-error of origin
			lobData[0][3] = 111.0; // Called bearing
			lobData[0][4] = 3.0; // bearingSdInDegrees
			lobData[0][5] = 0; // NM of minimum ring
			lobData[0][6] = 36.41; // NM of maximum ring;

			lobData[1][0] = 35.79591670; // lat of origin
			lobData[1][1] = -75.55038890; // lng of origin
			lobData[1][2] = 1.0; // NM of probable-error of origin
			lobData[1][3] = 11.0; // Called bearing
			lobData[1][4] = 4.0; // bearingSdInDegrees
			lobData[1][5] = 0; // NM of minimum ring
			lobData[1][6] = 25.59; // NM of maximum ring;
		}
		else {
			lobData[0][0] = 37.16333; // lat of origin
			lobData[0][1] = -76.53555; // lng of origin
			lobData[0][2] = 0.1; // NM of probable-error of origin
			lobData[0][3] = 284; // Called bearing
			lobData[0][4] = 2; // bearingSdInDegrees
			lobData[0][5] = 0; // NM of minimum ring
			lobData[0][6] = 31.76; // NM of maximum ring;

			lobData[1][0] = 36.7302; // lat of origin
			lobData[1][1] = -76.009; // lng of origin
			lobData[1][2] = 0.1; // NM of probable-error of origin
			lobData[1][3] = 225; // Called bearing
			lobData[1][4] = 2; // bearingSdInDegrees
			lobData[1][5] = 0; // NM of minimum ring
			lobData[1][6] = 29.35; // NM of maximum ring;

			lobData[2][0] = 37.26255; // lat of origin
			lobData[2][1] = -76.01228; // lng of origin
			lobData[2][2] = 0.1; // NM of probable-error of origin
			lobData[2][3] = 315; // Called bearing
			lobData[2][4] = 2; // bearingSdInDegrees
			lobData[2][5] = 0; // NM of minimum ring
			lobData[2][6] = 34.65; // NM of maximum ring;
		}

		/** The punchline: */
		LobsEllipse *lobsEllipseP0 = simCodeHook.makeLobsEllipse(NULL, 0, NULL);
		delete lobsEllipseP0;
		LobsEllipse *lobsEllipseP = simCodeHook.makeLobsEllipse(thresholds, nLobs, lobData);
		/** Print it out. */
		dumpFile << (*lobsEllipseP) << "\n" << std::flush;
		std::cout << (*lobsEllipseP) << "\n" << std::flush;
		/** Clean up memory... */
		for (int k = 0; k < nLobs; ++k) {
			delete[] lobData[k];
		}
		delete[] lobData;
		/** ...including the ellipse itself. */
		delete lobsEllipseP;

		/** NB: The Ellipse will come back with the following values if the LOBs were
		* such that no sensible ellipse (as per the thresholds) could be constructed.
		centerLat = 91d;
		centerLng = 181d;
		semiMajorNmi = -1d;
		semiMinorNmi = -1d;
		degCwFromNorthOfSemiMajor = -1d;
		*/
#endif

#ifdef TEST_LOG_ODDS_MAX_PD
		/** Test getLogOddsMaxPd. */
		const double ap0 = 5.28395899644369926;
		const double a1 = -2.62862019317526929;
		const double a2 = -0.28910017589801196;
		const double maxXValue = 1.0e+20;
		const double maxPd = simCodeHook.getLogOddsMaxPd(ap0, a1, a2, maxXValue);
		dumpFile << maxPd << "\n" << std::flush;
		std::cout << maxPd << "\n" << std::flush;
#endif

#ifdef TEST_SWEEPWIDTH
		/** New: test sweepwidth. */
		std::string s = std::string("<LRC_SET>");
		s += "\n<SENSOR SubType = \"InverseCube\"";
		s += "\nsw = \"1.2435 NM\"";
		s += "\nApplyToCrossLegs = \"true\"";
		s += "\nUpCreepMinRange = \"0.0 NM\"";
		s += "\nUpCreepMaxRange = \"100.0 NM\"";
		s += "\nUpCreepMinLookAngle = \"0.0 degs\"";
		s += "\nUpCreepMaxLookAngle = \"180.0 degs\"";
		s += "\nDownCreepMinRange = \"0.0 NM\"";
		s += "\nDownCreepMaxRange = \"100.0 NM\"";
		s += "\nDownCreepMinLookAngle = \"0.0 degs\"";
		s += "\nDownCreepMaxLookAngle = \"180.0 degs\" />";
		s += "\n</LRC_SET>";
		const char *const cStr = s.c_str();
		const double sw = simCodeHook.computeSweepWidth(cStr);
		dumpFile << cStr << "\n" << sw << "\n" << std::flush;
		std::cout << cStr << "\n" << sw << "\n" << std::flush;
#endif

#ifdef TEST_SIMLIB_PATTERN_A
		{
			const double speedInKts = 40.0 / 0.85;
			const long startTimeSecs = 0;
			const long durationSecs = 3600;
			const double centerLat = 47.0;
			const double centerLng = -122.0;
			const double orntn = 90.0;
			const bool firstTurnRight = true;
			const double minTsNmi = 0.1;
			const double fxdTsNmi = -1.0;
			const double excBufferNmi = 0.5;
			const double lenNmi = 10.0;
			const double widNmi = 4.0;
			const bool ps = false;
			const bool expandSpecsIfNeeded = false;
			char *const motionTypeId1 = "GC";
			SimPattern *simPatternP = simCodeHook.makePattern(
				speedInKts, startTimeSecs, durationSecs,
				centerLat, centerLng,
				orntn, firstTurnRight,
				minTsNmi, fxdTsNmi, excBufferNmi,
				lenNmi, widNmi, ps,
				motionTypeId1,
				expandSpecsIfNeeded
			);
			dumpFile << "\n\t=>" << (*simPatternP) << std::flush;
			std::cout << "\n\t=>" << (*simPatternP) << std::flush;
			delete simPatternP;
		}
#endif

#ifdef TEST_SIMLIB_PATTERN_B
		{
			/** JMH-supplied case to see Creeping Line bug better. */
			double speedInKts = 90.0;
			long startTimeSecs = 0;
			long durationSecs = 7200;
			double centerLat = 0.0;
			double centerLng = 0.0;
			double orntn = 0.0;
			bool firstTurnRight = false;
			double minTsNmi = 0.1;
			double fxdTsNmi = -1.0;
			double excBufferNmi = 0.5;
			double lenNmi = 19.2;
			double widNmi = 6.0;
			bool ps = false;
			char *const motionTypeId1 = "GC";
			bool expandSpecsIfNeeded = false;
			SimPattern *simPatternP = simCodeHook.makePattern(
				speedInKts, startTimeSecs, durationSecs,
				centerLat, centerLng,
				orntn, firstTurnRight,
				minTsNmi, fxdTsNmi, excBufferNmi,
				lenNmi, widNmi, ps,
				motionTypeId1,
				expandSpecsIfNeeded
			);
			dumpFile << "\n\t=>(1)" << (*simPatternP) << std::flush;
			std::cout << "\n\t=>(1)" << (*simPatternP) << std::flush;
			delete simPatternP;
		}

#endif

#ifdef TEST_SIMLIB_PATTERN_C
		const double rawKts = 90.0;
		const double HRS = 2.0;
		const double CNTR_LAT = 19.503333;
		const double CNTR_LNG = -159.125000;
		const double ORTN = 12.0;
		const bool FRST_TRN_RT = true;
		const double MIN_TS = 0.1;
		const double FXD_TS = -1.0;
		const double EXC_BFFR = 1.0;
		const double LEN = 888.0;
		const double WID = 0.1;
		const bool PS = true;
		const char *MTN_TYPE = "GC";
		const bool EXP_SPECS_IF_NEEDED = true;

		SimPattern *simPatternP = simCodeHook.makePattern(
			90.0, 0, 3600,
			CNTR_LAT, CNTR_LNG, ORTN, FRST_TRN_RT,
			MIN_TS, FXD_TS, EXC_BFFR,
			LEN, WID, PS,
			MTN_TYPE,
			EXP_SPECS_IF_NEEDED
		);
		dumpFile << "\n\t=>" << (*simPatternP) << std::flush;
		std::cout << "\n\t=>" << (*simPatternP) << std::flush;
#endif

#ifdef TEST_SIMLIB_PATTERN_VS
		const double rawKts = 90.0;
		const double HRS = 2.0;
		const double CNTR_LAT = 90.0 - 1.0 / 600.0;
		const double CNTR_LNG = 0.0;
		const double ORTN = 30.0;
		const bool FRST_TRN_RT = false;
		const double MIN_TS = 0.1;
		const double FXD_TS = -1.0;
		const double EXC_BFFR = 1.0;
		const double LEN = -1.0;
		const double WID = -1.0;
		const bool PS = true;
		const char *MTN_TYPE = "GC";
		const bool EXP_SPECS_IF_NEEDED = true;

		SimPattern *simPatternP = simCodeHook.makePattern(
			rawKts, 0, int(HRS * 3600),
			CNTR_LAT, CNTR_LNG, ORTN, FRST_TRN_RT,
			MIN_TS, FXD_TS, EXC_BFFR,
			LEN, WID, PS,
			MTN_TYPE,
			EXP_SPECS_IF_NEEDED
		);
		dumpFile << "\n\t=>" << (*simPatternP) << std::flush;
		std::cout << "\n\t=>" << (*simPatternP) << std::flush;
#endif

#ifdef TEST_SIMLIB_PATTERN_SS
		const double rawKts = 15.0;
		const double HRS = 4.0;
		const double CNTR_LAT = 90.0 - 1.0 / 600.0;
		const double CNTR_LNG = 0.0;
		const double ORTN = 30.0;
		const bool FRST_TRN_RT = false;
		const double MIN_TS = 0.1;
		const double FXD_TS = -1.0;
		const double EXC_BFFR = 1.0;
		const double LEN = 10.0;
		const double WID = -1.0;
		const bool PS = true;
		const char *MTN_TYPE = "GC";
		const bool EXP_SPECS_IF_NEEDED = true;

		SimPattern *simPatternP = simCodeHook.makePattern(
			rawKts, 0, int(HRS * 3600),
			CNTR_LAT, CNTR_LNG, ORTN, FRST_TRN_RT,
			MIN_TS, FXD_TS, EXC_BFFR,
			LEN, WID, PS,
			MTN_TYPE,
			EXP_SPECS_IF_NEEDED
		);
		dumpFile << "\n\t=>" << (*simPatternP) << std::flush;
		std::cout << "\n\t=>" << (*simPatternP) << std::flush;
#endif

#ifdef TEST_SIMLIB_PATTERN_D
		/** Case starts here. */
		double speedInKts = 90.0;
		long startTimeSecs = 0;
		long durationSecs = 7200;
		double centerLat = 36.0;
		double centerLng = -74.0;
		double orntn = 297.0;
		bool firstTurnRight = false;
		double minTsNmi = 0.1;
		double fxdTsNmi = -1.0;
		double excBufferNmi = 0.5;
		double lenNmi = 19.2;
		double widNmi = 19.1;
		bool ps = false;
		char *const motionTypeId1 = "GC";
		bool expandSpecsIfNeeded = false;
		/** JMH-supplied overridden initial values to see Creeping Line bug better. */
		centerLat = 0.0;
		centerLng = 0.0;
		orntn = 0.0;
		widNmi = 6.0;
		for (int iPass = 0; iPass < 1; ++iPass) {
			for (int k = 0; k < 3; ++k) {
				/** Set the parameters as per the original ones above or the
				updated ones from the previous loop. Here, this is printing
				out the parameters. setNext sets only the critical values
				for testing. */
				parameterGenerator.setNext(
					speedInKts, durationSecs, centerLat, centerLng, orntn, firstTurnRight,
					minTsNmi, fxdTsNmi, lenNmi, widNmi, ps);
				dumpFile << "\n" << parameterGenerator << std::flush;
				std::cout << "\n\t" << parameterGenerator << std::flush;
				SimPattern *simPatternP;
				if (iPass == 0) {
					/** Old one: */
					simPatternP = simCodeHook.makePattern(
						speedInKts, startTimeSecs, durationSecs,
						centerLat, centerLng,
						orntn, firstTurnRight,
						minTsNmi, fxdTsNmi, excBufferNmi,
						lenNmi, widNmi, ps,
						motionTypeId1, expandSpecsIfNeeded
					);
				}
				else {
					/** New one: */
					simPatternP = simCodeHook.makePattern(
						speedInKts, startTimeSecs, durationSecs,
						centerLat, centerLng,
						orntn, firstTurnRight,
						minTsNmi, fxdTsNmi, excBufferNmi,
						lenNmi, widNmi, ps,
						motionTypeId1,
						expandSpecsIfNeeded
					);
				}
				dumpFile << "\n\t=>" << (*simPatternP) << std::flush;
				std::cout << "\n\t=>" << (*simPatternP) << std::flush;
				/** Update the values from *simPatternP. */
				double newEplNmi = simPatternP->_eplNmi;
				double oldEplNmi = speedInKts * durationSecs / 3600.0;
				speedInKts *= newEplNmi / oldEplNmi;
				minTsNmi = simPatternP->_minTsNmi;
				lenNmi = simPatternP->_lenNmi;
				widNmi = simPatternP->_widNmi;
				fxdTsNmi = simPatternP->_fxdTsNmi;
				ps = simPatternP->_ps;
				firstTurnRight = simPatternP->_firstTurnRight;
				orntn = simPatternP->_orntn;
				centerLat = simPatternP->_centerLat;
				centerLng = simPatternP->_centerLng;
				/** JMH-supplied debug. Dump the generated points; only need to do this
				the first time. */
				if (k == 0) {
					dumpFile << "\n\tTrackline points";
					std::cout << "\n\tTrackline points";
					double northMostLat, southMostLat, eastMostLng, westMostLng;
					for (int nPathPt = 0; nPathPt < simPatternP->_nPath; nPathPt++) {
						dumpFile << "\n\t\tpoint #" << nPathPt << ": ";
						std::cout << "\n\t\tpoint #" << nPathPt << ": ";
						double thisLat = simPatternP->_pathLats[nPathPt];
						double thisLng = simPatternP->_pathLngs[nPathPt];
						dumpPoint(dumpFile, "", thisLat, thisLng);
						/** Save extremes for comparison below. */
						if (nPathPt == 0) {
							northMostLat = southMostLat = thisLat;
							eastMostLng = westMostLng = thisLng;
						}
						else {
							if (thisLat > northMostLat) northMostLat = thisLat;
							if (thisLat < southMostLat) southMostLat = thisLat;
							if (thisLng > eastMostLng)  eastMostLng = thisLng;
							if (thisLng < westMostLng)  westMostLng = thisLng;
						}
					}
					/** Use NavCalc to validate points. */
					const double lat0 = centerLat;
					const double lng0 = centerLng;
					double rng = widNmi / 2.0;
					double brg = 270.0;
					const char *const motionTypeId2 = "GC";
					const bool findRangeBearing = false;
					NavCalc *navCalcP = simCodeHook.makeNavCalc(
						lat0, lng0, rng, brg, motionTypeId2, findRangeBearing);
					if (navCalcP != NULL) {
						dumpPoint(dumpFile, "\n\tWestern edge point is ",
							navCalcP->_lat1, navCalcP->_lng1);
						dumpOutOfBounds(dumpFile, (westMostLng < navCalcP->_lng1), westMostLng, navCalcP->_lng1);
						delete navCalcP;
					}
					brg = 90;
					navCalcP = simCodeHook.makeNavCalc(
						lat0, lng0, rng, brg, motionTypeId2, findRangeBearing);
					if (navCalcP != NULL) {
						dumpPoint(dumpFile, "\n\tEastern edge point is ",
							navCalcP->_lat1, navCalcP->_lng1);
						dumpOutOfBounds(dumpFile, (eastMostLng > navCalcP->_lng1), eastMostLng, navCalcP->_lng1);
						delete navCalcP;
					}
					brg = 0;
					rng = lenNmi / 2.0;
					navCalcP = simCodeHook.makeNavCalc(
						lat0, lng0, rng, brg, motionTypeId2, findRangeBearing);
					if (navCalcP != NULL) {
						dumpPoint(dumpFile, "\n\tNorthern edge point is ",
							navCalcP->_lat1, navCalcP->_lng1);
						dumpOutOfBounds(dumpFile, (northMostLat > navCalcP->_lat1), northMostLat, navCalcP->_lat1);
						delete navCalcP;
					}
					brg = 180;
					navCalcP = simCodeHook.makeNavCalc(
						lat0, lng0, rng, brg, motionTypeId2, findRangeBearing);
					if (navCalcP != NULL) {
						dumpPoint(dumpFile, "\n\tSouthern edge point is ",
							navCalcP->_lat1, navCalcP->_lng1);
						dumpOutOfBounds(dumpFile, (southMostLat < navCalcP->_lat1), southMostLat, navCalcP->_lat1);
						delete navCalcP;
					}
				}
				/** Done with simPatternP. */
				delete simPatternP;
				/** Get new random values for lat, lng, and direction. */
				parameterGenerator.setNext();
				orntn = parameterGenerator._orntn;
				centerLat = parameterGenerator._lat;
				centerLng = parameterGenerator._lng;
			}
		}
		dumpFile << "\n" << std::flush;
		std::cout << "\n" << std::flush;

		/** I create and delete a SimPattern nRepeat times just to check
		the repeatability of this operation. */
		const int nRepeat = 0;
		for (int k = 0; k < nRepeat; ++k) {
			const long cstPosixSecs = 0;
			parameterGenerator.setNext();
			dumpFile << "\n" << parameterGenerator << std::flush;
			std::cout << "\n\t" << parameterGenerator << std::flush;
			const double speedInKts = parameterGenerator._speedInKts;
			const long durationInSeconds = parameterGenerator._durationInSeconds;
			const double centerLat = 48.0;
			const double centerLng = -123.0;
			const double orntn = parameterGenerator._orntn;
			const bool firstTurnRight = parameterGenerator._firstTurnRight;
			const double minTsNmi = parameterGenerator._minTsInNmi;
			const double fxdTsNmi = parameterGenerator._fxdTsNmi;
			const double excBufferNmi = 0.5;
			const double lenNmi = parameterGenerator._lenNmi;
			const double widNmi = parameterGenerator._widNmi;
			const bool ps = parameterGenerator._ps;
			char *const motionTypeId1 = "GC";
			/** The hammer.  The next line of code creates a SimPattern.
			Everything in *simPatternP is public so you simply
			read off the fields.  The fields are described after the function
			call.
			*/
			SimPattern *simPatternP = simCodeHook.makePattern(
				speedInKts, cstPosixSecs, durationInSeconds,
				centerLat, centerLng,
				orntn, firstTurnRight,
				minTsNmi, fxdTsNmi, excBufferNmi,
				lenNmi, widNmi, ps,
				motionTypeId1,
				/*expandSpecsIfNeeded=*/false
			);
			dumpFile << "\n\t=>" << (*simPatternP) << std::flush;
			std::cout << "\n\t=>" << (*simPatternP) << std::flush;
			/** I return a pointer that you have to delete. */
			if (simPatternP != NULL) {
				delete simPatternP;
			}
		}
#endif

#ifdef TEST_MAKE_NAV_CALC
		/** Similar illustratation of makeNavCalc. */
		const double lat0 = 39;
		const double lng0 = -179;
		const double lat1 = 39;
		const double lng1 = 179;
		const char *const motionTypeId2 = "RL";
		const bool findRangeBearing = true;
		NavCalc *navCalcP = simCodeHook.makeNavCalc(
			lat0, lng0, lat1, lng1, motionTypeId2, findRangeBearing
		);
		if (navCalcP != NULL) {
			delete navCalcP;
		}
#endif
		dumpFile.close();
	}
#ifdef _DEBUG
	_CrtDumpMemoryLeaks();
#endif
	return 0;
}
#endif