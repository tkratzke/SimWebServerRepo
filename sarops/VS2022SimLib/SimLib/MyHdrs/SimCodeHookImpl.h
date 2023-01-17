#pragma once
#include <jni.h>

#include <string>
#include <vector>

class SimPattern;
class LobsEllipse;
class NavCalc;

class SimCodeHookImpl
{
	const char *_simLibDirName;
	/* To be deleted. */
	std::vector<char*> _optionStrings;
	JavaVMOption *_vm_argOptions;
	JavaVM *_jvmP;
	JNIEnv *_envP;
	JavaVMInitArgs _vm_args;
	/* Global refs that we cache to avoid looking up all the time. */
	/* PrintArgs: */
	jclass _printArgsClass;
	jmethodID _printArgsMainId;
	/* System.exit*/
	jclass _systemClass;
	jmethodID _systemExitId;
	/* acos: */
	jclass _mathXClass;
	jmethodID _mathXAcosXId;

	/* SimLibMakePattern: */
	jclass _simLibPatternClass;
	jmethodID _simLibPatternCtorId;
	jfieldID _centerLatId, _centerLngId;
	jfieldID _orntnId, _firstTurnRightId;
	jfieldID _minTsNmiId, _fixedTsNmiId, _excBufferNmiId;
	jfieldID _lenNmiId, _widNmiId, _psId;
	jfieldID _eplNmiId, _rawSearchKtsToUseId;
	jfieldID _tsNmiId, _sllNmiId;
	jfieldID _pathLatsId, _pathLngsId;
	jfieldID _unixSecsId;
	jfieldID _specLooseLatsId, _specLooseLngsId;
	jfieldID _tsLooseLatsId, _tsLooseLngsId;
	jfieldID _tsTightLatsId, _tsTightLngsId;
	jfieldID _excTightLatsId, _excTightLngsId;

	/* PatternUtils:*/
	jclass _patternUtilStaticsClass;
	jmethodID _patternUtilsGetVersionNameId;

	/* NavigationCalculator: */
	jclass _navigationCalculatorStaticsClass;
	jmethodID _answerBasicQuestionId;

	/* SimUtils. */
	jclass _simUtilsClass;
	jmethodID _getLogOddsMaxPdId;

	/* Wangsness. */
	jclass _wangsnessClass;
	jmethodID _getEllipseId0;
	jmethodID _getEllipseId1;
	jmethodID _getEllipseId2;

	/*ModelReader. */
	jclass _modelReaderClass;
	jmethodID _computeSweepWidthId;

	/* Utilities */
	jobjectArray getJavaStringArray(int n, const char **messages);
	jdoubleArray getJavaDoubleArray(int n, const double *jdoubles);
	jobjectArray getJavaMatrix(int nRows, const jdoubleArray *jDoubleArrays);

	SimPattern *simLibPatternToSimPattern(jobject simLibPattern);

	bool errorExists();

	LobsEllipse *makeLobsEllipse(
		jdouble *thresholds,
		jint nLobs,
		jdouble **lobs,
		jboolean throwException
	);

public:
	const static std::string _BadString;
	SimCodeHookImpl(const char *simLibDirName);
	~SimCodeHookImpl(void);
	bool callPrintArgsMain(std::vector<std::string> &strings);
	std::string getVersionName();
	double acos(double x);

	SimPattern *makeSimLibPattern(
		const jdouble rawSpeedKts,
		const jlong startUnixSecs,
		const jlong durationSecs,
		const jdouble centerLat, const jdouble centerLng,
		const jdouble orntn, const jboolean firstTurnRight,
		const jdouble minTsNmi, const jdouble fixedTsNmi, const jdouble excBufferNmi,
		const jdouble lengthNmi, const jdouble widthNmi, const jboolean ps,
		const char *const motionTypeId,
		const jboolean expandSpecsIfNeeded
	);

	LobsEllipse *makeLobsEllipse(
		jboolean throwException
	)
	{
		return makeLobsEllipse(NULL, -1, NULL, throwException);
	}

	LobsEllipse *makeLobsEllipse(
		jdouble *thresholds,
		jint nLobs,
		jdouble **lobs
	)
	{
		return makeLobsEllipse(thresholds, nLobs, lobs, /*throwException=*/false);
	}

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
