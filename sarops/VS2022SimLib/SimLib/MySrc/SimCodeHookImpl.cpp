#include "StdAfx.h"
#include <SimLib.h>
#include <SimCodeHookImpl.h>

#include <stdio.h>

#include <iostream>
#include <fstream>
#include <string>
#include <algorithm>

#include <vector>

#include <MyHdrs/SimPattern.h>
#include <MyHdrs/LobsEllipse.h>
#include <MyHdrs/NavCalc.h>

#ifdef USE_VLD
#include <vld.h>
#endif

HMODULE _jvmHModule = NULL;
DWORD _moduleFileName = NULL;

const DWORD _BufLen = 1024;

const int NThresholdValues = 5;
const int NDoublesPerBearingCall = 7;

void stripCrAndLf(std::string &str);
jchar *getJCharArray(const char *cString);

const std::string SimCodeHookImpl::_BadString("BAD BAD STRING");

SimCodeHookImpl::SimCodeHookImpl(const char *simLibDirName) : _simLibDirName(simLibDirName)
{
	if (_jvmHModule == NULL) {
		std::wstring jvmName(L"jvm.dll");
		_jvmHModule = LoadLibrary(jvmName.c_str());
	}
	if (_jvmHModule == NULL) {
		return;
	}

	/* const discussion: */
	/* https://en.wikipedia.org/wiki/Const_(computer_programming) */
	/* JNI Tutorial: */
	/* https://www3.ntu.edu.sg/home/ehchua/programming/java/JavaNativeInterface.html */
	/* GetModuleFileName: */
	/* http://msdn.microsoft.com/en-us/library/ms683197%28v=VS.85%29.aspx */
	LPTSTR jvmDllPathW = new WCHAR[_BufLen];
	DWORD moduleFileName = GetModuleFileName(_jvmHModule, jvmDllPathW, _BufLen);
	char jvmDirBuff[_BufLen];
	sprintf_s(jvmDirBuff, _BufLen, "%ls", jvmDllPathW);
	delete[] jvmDllPathW;
	std::string jvmPath = jvmDirBuff;
	const int coreLen = static_cast<const int>(jvmPath.length() - strlen("jvm.dll"));
	std::string jvmDirPath = jvmPath.substr(0, coreLen);

	/* Accumulate the vm args. */
	std::vector<std::string> allVmArgs;

	/* Start with the class path. */
	std::string jarSuffix = ".JAR";
	int jarSuffixSize = static_cast<int>(jarSuffix.length());
	std::string installDirPath = jvmDirPath + "..\\..\\..\\";
	std::string jarFilesDirPath = installDirPath + _simLibDirName + "\\lib";
	std::string commandPath = "dir \"";
	commandPath += jarFilesDirPath;
	commandPath += "\" /b /s";
	FILE *in;
	if (!(in = _popen(commandPath.c_str(), "r"))) {
		return;
	}
	std::string classPath;
	char jarFileBuff[_BufLen];
	while (fgets(jarFileBuff, _BufLen, in) != NULL) {
		std::string jarFileStr = jarFileBuff;
		stripCrAndLf(jarFileStr);
		int jarFileStrSize = int(jarFileStr.length());
		int nInMeat = jarFileStrSize - jarSuffixSize;
		if (nInMeat < 1) {
			continue;
		}
		std::string thisSuffix = jarFileStr.substr(nInMeat, jarFileStr.length());
		std::transform(thisSuffix.begin(), thisSuffix.end(), thisSuffix.begin(), ::toupper);
		if (thisSuffix.compare(jarSuffix) != 0) {
			continue;
		}
		if (classPath.length() == 0) {
			classPath = "-Djava.class.path=";
		}
		else {
			classPath += ";";
		}
		classPath += jarFileStr;
	}
	_pclose(in);
	allVmArgs.push_back(classPath);

	/* Make sure that Sws22's "AbstractToImpl" classes get loaded.*/
	allVmArgs.push_back("-DAbstract.Classes.Map=Sws22");

	/* Get the java library path for MathLib. */
#ifdef _WIN64
	std::string javaLibPath = "-Djava.library.path=" + installDirPath + "MathLib\\x64\\Release";
#else
	std::string javaLibPath = "-Djava.library.path=" + installDirPath + "MathLib\\Release";
#endif
	allVmArgs.push_back(javaLibPath);

	std::string maxSizeString = "-Xmx100M";
	// allVmArgs.push_back(maxSizeString);
	std::string incrementString = "-Xms20M";
	// allVmArgs.push_back(incrementString);

#ifdef DEBUG_JAVA
	// TMK!! Turn OFF the DEBUG_JAVA in the properties we build with.
	std::string debugJavaString = "-DDEBUG_JAVA=true";
	allVmArgs.push_back(debugJavaString);
#endif

	/* JDK/JRE 8 VM initialization arguments */
	JavaVMInitArgs vm_args;
	vm_args.version = JNI_VERSION_1_8;
	vm_args.ignoreUnrecognized = JNI_TRUE;
	const int nOptions = vm_args.nOptions = int(allVmArgs.size());
	vm_args.options = _vm_argOptions = new JavaVMOption[nOptions];
	int kOption = 0;
	for (std::vector<std::string>::iterator it = allVmArgs.begin(); it != allVmArgs.end(); ++it) {
		const char *vmArg = it->c_str();
		const int len = static_cast<const int>(strlen(vmArg));
		char *argBuffer = new char[len + 1];
		sprintf_s(argBuffer, len + 1, "%s", it->c_str());
		vm_args.options[kOption++].optionString = argBuffer;
		_optionStrings.push_back(argBuffer);
	}

	/* Load and initialize a Java VM, return a JNI interface pointer in _envP. */
	const jint createVmReturn = JNI_CreateJavaVM(&_jvmP, (void **)&_envP, &vm_args);
	/* Get the PrintArgs main program's methodId. PrintArgs is just a simple example. */
	const char *printArgsClassName = "com/skagit/util/PrintArgs";
	jclass localPrintArgsClass = _envP->FindClass(printArgsClassName);
	_printArgsClass = (jclass)_envP->NewGlobalRef(localPrintArgsClass);
	_envP->DeleteLocalRef(localPrintArgsClass);
	_printArgsMainId = _envP->GetStaticMethodID(_printArgsClass, "main", "([Ljava/lang/String;)V");

	/* Get System's exit methodId. */
	const char *systemClassName = "java/lang/System";
	jclass localSystemClass = _envP->FindClass(systemClassName);
	_systemClass = (jclass)_envP->NewGlobalRef(localSystemClass);
	_envP->DeleteLocalRef(localSystemClass);
	_systemExitId = _envP->GetStaticMethodID(_systemClass, "exit", "(I)V");

	/* Get the acos's methodId. acos tests the use of using java and then java's jni. */
	const char *mathXClassName = "com/skagit/util/MathX";
	jclass localMathXClass = _envP->FindClass(mathXClassName);
	_mathXClass = (jclass)_envP->NewGlobalRef(localMathXClass);
	_envP->DeleteLocalRef(localMathXClass);
	_mathXAcosXId = _envP->GetStaticMethodID(_mathXClass, "acosX", "(D)D");

	/* Get the SimLibPattern's ctor's methodId. */
	/* Get the field Ids. */
	const char *doubleSig = "D";
	const char *longSig = "L";
	const char *booleanSig = "Z";
	const char *longArraySig = "[J";
	const char *doubleArraySig = "[D";
	const char *simLibPatternClassName = "com/skagit/sarops/util/patternUtils/SimLibPattern";
	jclass localSimLibPatternClass = _envP->FindClass(simLibPatternClassName);
	_simLibPatternClass = (jclass)_envP->NewGlobalRef(localSimLibPatternClass);
	_envP->DeleteLocalRef(localSimLibPatternClass);
	_simLibPatternCtorId = _envP->GetMethodID(_simLibPatternClass, "<init>",
		"(DJIDDDZDDDDDZLjava/lang/String;Z)V"
	);

	_centerLatId = _envP->GetFieldID(_simLibPatternClass, "_centerLat", doubleSig);
	_centerLngId = _envP->GetFieldID(_simLibPatternClass, "_centerLng", doubleSig);
	_orntnId = _envP->GetFieldID(_simLibPatternClass, "_orntn", doubleSig);
	_firstTurnRightId = _envP->GetFieldID(_simLibPatternClass, "_firstTurnRight", booleanSig);
	_minTsNmiId = _envP->GetFieldID(_simLibPatternClass, "_minTsNmi", doubleSig);
	_fixedTsNmiId = _envP->GetFieldID(_simLibPatternClass, "_fixedTsNmi", doubleSig);
	_excBufferNmiId = _envP->GetFieldID(_simLibPatternClass, "_excBufferNmi", doubleSig);
	_lenNmiId = _envP->GetFieldID(_simLibPatternClass, "_lenNmi", doubleSig);
	_widNmiId = _envP->GetFieldID(_simLibPatternClass, "_widNmi", doubleSig);
	_psId = _envP->GetFieldID(_simLibPatternClass, "_ps", booleanSig);
	_eplNmiId = _envP->GetFieldID(_simLibPatternClass, "_eplNmi", doubleSig);
	_rawSearchKtsToUseId = _envP->GetFieldID(_simLibPatternClass, "_rawSearchKtsToUse", doubleSig);
	_tsNmiId = _envP->GetFieldID(_simLibPatternClass, "_tsNmi", doubleSig);
	_sllNmiId = _envP->GetFieldID(_simLibPatternClass, "_sllNmi", doubleSig);

	_unixSecsId = _envP->GetFieldID(_simLibPatternClass, "_waypointSecsS", longArraySig);
	_pathLatsId = _envP->GetFieldID(_simLibPatternClass, "_pathLats", doubleArraySig);
	_pathLngsId = _envP->GetFieldID(_simLibPatternClass, "_pathLngs", doubleArraySig);
	_specLooseLatsId = _envP->GetFieldID(_simLibPatternClass, "_specLooseLats", doubleArraySig);
	_specLooseLngsId = _envP->GetFieldID(_simLibPatternClass, "_specLooseLngs", doubleArraySig);
	_tsLooseLatsId = _envP->GetFieldID(_simLibPatternClass, "_tsLooseLats", doubleArraySig);
	_tsLooseLngsId = _envP->GetFieldID(_simLibPatternClass, "_tsLooseLngs", doubleArraySig);
	_tsTightLatsId = _envP->GetFieldID(_simLibPatternClass, "_tsTightLats", doubleArraySig);
	_tsTightLngsId = _envP->GetFieldID(_simLibPatternClass, "_tsTightLngs", doubleArraySig);
	_excTightLatsId = _envP->GetFieldID(_simLibPatternClass, "_excTightLats", doubleArraySig);
	_excTightLngsId = _envP->GetFieldID(_simLibPatternClass, "_excTightLngs", doubleArraySig);

	/* Get the versionName method. */
	const char *patternUtilStaticsClassName = "com/skagit/sarops/util/patternUtils/PatternUtilStatics";
	jclass localPatternUtilStaticsClass = _envP->FindClass(patternUtilStaticsClassName);
	_patternUtilStaticsClass = (jclass)_envP->NewGlobalRef(localPatternUtilStaticsClass);
	_envP->DeleteLocalRef(localPatternUtilStaticsClass);
	_patternUtilsGetVersionNameId =
		_envP->GetStaticMethodID(_patternUtilStaticsClass, "getVersionName", "()Ljava/lang/String;");

	/* Get NavigationCalculator's critical static method's id. */
	const char *navigationCalculatorStaticsClassName = "com/skagit/util/navigation/NavigationCalculatorStatics";
	jclass localNavigationCalculatorStaticsClass = _envP->FindClass(navigationCalculatorStaticsClassName);
	_navigationCalculatorStaticsClass = (jclass)_envP->NewGlobalRef(localNavigationCalculatorStaticsClass);
	_envP->DeleteLocalRef(localNavigationCalculatorStaticsClass);
	_answerBasicQuestionId = _envP->GetStaticMethodID(_navigationCalculatorStaticsClass, "answerBasicQuestion",
		"(DDDDLjava/lang/String;Z)[D");

	/* Get SimUtils' getLogOddsMaxPd. */
	const char *simUtilsClassName = "com/skagit/sarops/util/SimUtils";
	jclass localSimUtilsClass = _envP->FindClass(simUtilsClassName);
	_simUtilsClass = (jclass)_envP->NewGlobalRef(localSimUtilsClass);
	_envP->DeleteLocalRef(localSimUtilsClass);
	_getLogOddsMaxPdId = _envP->GetStaticMethodID(_simUtilsClass, "getLogOddsMaxPd",
		"(DDDD)D");

	/* Get Wangsness' getEllipse. */
	const char *wangsnessClassName = "com/skagit/sarops/util/wangsness/Wangsness";
	jclass localWangsnessClass = _envP->FindClass(wangsnessClassName);
	_wangsnessClass = (jclass)_envP->NewGlobalRef(localWangsnessClass);
	_envP->DeleteLocalRef(localWangsnessClass);
	_getEllipseId0 = _envP->GetStaticMethodID(_wangsnessClass, "getEllipse",
		"(Z)[D");
	_getEllipseId1 = _envP->GetStaticMethodID(_wangsnessClass, "getEllipse",
		"([D)[D");
	_getEllipseId2 = _envP->GetStaticMethodID(_wangsnessClass, "getEllipse",
		"([D[[D)[D");

	/*Get ModelReader's computeSweepWidth. */
	const char *modelReaderClassName = "com/skagit/sarops/model/io/ModelReader";
	jclass localModelReaderClass = _envP->FindClass(modelReaderClassName);
	_modelReaderClass = (jclass)_envP->NewGlobalRef(localModelReaderClass);
	_envP->DeleteLocalRef(localModelReaderClass);
	_computeSweepWidthId = _envP->GetStaticMethodID(_modelReaderClass, "computeSweepWidth",
		"(Ljava/lang/String;)D");
}

jchar *getJCharArray(const char *cString) {
	int len = int(strlen(cString));
	jchar *jChars = new jchar[len];
	for (int k = 0; k < len; ++k) {
		jChars[k] = cString[k];
	}
	return jChars;
}

bool SimCodeHookImpl::callPrintArgsMain(std::vector<std::string> &strings) {
	int n = static_cast<int>(strings.size());
	const char **buffer = new const char *[n];
	for (int k = 0; k < n; ++k) {
		buffer[k] = strings[k].c_str();
	}
	jobjectArray javaStringArray = getJavaStringArray(n, buffer);
	_envP->CallStaticVoidMethod(_printArgsClass, _printArgsMainId, javaStringArray);
	bool success = !errorExists();
	delete[] buffer;
	return success;
}

std::string SimCodeHookImpl::getVersionName() {
	jstring str = (jstring)_envP->CallStaticObjectMethod(_patternUtilStaticsClass, _patternUtilsGetVersionNameId, NULL);
	if (errorExists()) {
		return _BadString;
	}
	const char *nativeString = _envP->GetStringUTFChars(str, NULL);
	std::string s(nativeString);
	_envP->ReleaseStringUTFChars(str, nativeString);
	return s;
}

double SimCodeHookImpl::acos(double x) {
	jdouble thetaRads = _envP->CallStaticDoubleMethod(_mathXClass, _mathXAcosXId, x);
	if (errorExists()) {
		/** acos should return something between 0 and pi. */
		return -1.0;
	}
	return static_cast<double>(thetaRads);
}

#define USE_CRITICAL

SimPattern *SimCodeHookImpl::makeSimLibPattern(
	const jdouble rawSpeedKts,
	const jlong startUnixSecs,
	const jlong durationSecs,
	const jdouble centerLat, const jdouble centerLng,
	const jdouble orntn, const jboolean firstTurnRight,
	const jdouble minTsNmi,
	const jdouble fixedTsNmi,
	const jdouble excBufferNmi,
	const jdouble lengthNmi, const jdouble widthNmi, const jboolean ps,
	const char *const motionTypeId,
	const jboolean expandSpecsIfNeeded
)
{
	jvalue jValues[15];
	jValues[0].d = rawSpeedKts;
	jValues[1].j = startUnixSecs;
	jValues[2].i = int(durationSecs);
	jValues[3].d = centerLat;
	jValues[4].d = centerLng;
	jValues[5].d = orntn;
	jValues[6].z = firstTurnRight;
	jValues[7].d = minTsNmi;
	jValues[8].d = fixedTsNmi;
	jValues[9].d = excBufferNmi;
	jValues[10].d = lengthNmi;
	jValues[11].d = widthNmi;
	jValues[12].z = ps;
	jValues[13].l = _envP->NewStringUTF(motionTypeId);
	jValues[14].z = expandSpecsIfNeeded;
	jobject simLibPattern = _envP->NewObjectA(
		_simLibPatternClass, _simLibPatternCtorId, jValues
	);
	if (errorExists() || simLibPattern == NULL) {
		_envP->DeleteLocalRef(jValues[14].l);
		return NULL;
	}
	SimPattern *simPattern = simLibPatternToSimPattern(simLibPattern);
	return simPattern;
}

SimPattern *SimCodeHookImpl::simLibPatternToSimPattern(jobject simLibPattern)
{
	const jdouble centerLat_X = _envP->GetDoubleField(simLibPattern, _centerLatId);
	const jdouble centerLng_X = _envP->GetDoubleField(simLibPattern, _centerLngId);
	const jdouble orntn_X = _envP->GetDoubleField(simLibPattern, _orntnId);
	const jboolean firstTurnRight_X = _envP->GetBooleanField(simLibPattern, _firstTurnRightId);
	const jdouble eplNmi_X = _envP->GetDoubleField(simLibPattern, _eplNmiId);
	const jdouble rawSearchKtsToUse_X = _envP->GetDoubleField(simLibPattern, _rawSearchKtsToUseId);
	const jdouble minTsNmi_X = _envP->GetDoubleField(simLibPattern, _minTsNmiId);
	const jdouble fixedTsNmi_X = _envP->GetDoubleField(simLibPattern, _fixedTsNmiId);
	const jdouble excBufferNmi_X = _envP->GetDoubleField(simLibPattern, _excBufferNmiId);
	const jdouble lenNmi_X = _envP->GetDoubleField(simLibPattern, _lenNmiId);
	const jdouble widNmi_X = _envP->GetDoubleField(simLibPattern, _widNmiId);
	const jboolean ps_X = _envP->GetBooleanField(simLibPattern, _psId);
	const jdouble tsNmi_X = _envP->GetDoubleField(simLibPattern, _tsNmiId);
	const jdouble sllNmi_X = _envP->GetDoubleField(simLibPattern, _sllNmiId);

	jobject longArrayObject = _envP->GetObjectField(simLibPattern, _unixSecsId);
	jobject doubleArrayObject1 = _envP->GetObjectField(simLibPattern, _pathLatsId);
	jobject doubleArrayObject2 = _envP->GetObjectField(simLibPattern, _pathLngsId);
	jobject doubleArrayObject3 = _envP->GetObjectField(simLibPattern, _specLooseLatsId);
	jobject doubleArrayObject4 = _envP->GetObjectField(simLibPattern, _specLooseLngsId);
	jobject doubleArrayObject5 = _envP->GetObjectField(simLibPattern, _tsLooseLatsId);
	jobject doubleArrayObject6 = _envP->GetObjectField(simLibPattern, _tsLooseLngsId);
	jobject doubleArrayObject7 = _envP->GetObjectField(simLibPattern, _tsTightLatsId);
	jobject doubleArrayObject8 = _envP->GetObjectField(simLibPattern, _tsTightLngsId);
	jobject doubleArrayObject9 = _envP->GetObjectField(simLibPattern, _excTightLatsId);
	jobject doubleArrayObject10 = _envP->GetObjectField(simLibPattern, _excTightLngsId);

	jlongArray longArray = reinterpret_cast<jlongArray&>(longArrayObject);
	jdoubleArray doubleArray1 = reinterpret_cast<jdoubleArray&>(doubleArrayObject1);
	jdoubleArray doubleArray2 = reinterpret_cast<jdoubleArray&>(doubleArrayObject2);
	jdoubleArray doubleArray3 = reinterpret_cast<jdoubleArray&>(doubleArrayObject3);
	jdoubleArray doubleArray4 = reinterpret_cast<jdoubleArray&>(doubleArrayObject4);
	jdoubleArray doubleArray5 = reinterpret_cast<jdoubleArray&>(doubleArrayObject5);
	jdoubleArray doubleArray6 = reinterpret_cast<jdoubleArray&>(doubleArrayObject6);
	jdoubleArray doubleArray7 = reinterpret_cast<jdoubleArray&>(doubleArrayObject7);
	jdoubleArray doubleArray8 = reinterpret_cast<jdoubleArray&>(doubleArrayObject8);
	jdoubleArray doubleArray9 = reinterpret_cast<jdoubleArray&>(doubleArrayObject9);
	jdoubleArray doubleArray10 = reinterpret_cast<jdoubleArray&>(doubleArrayObject10);

	jboolean isCopy0 = false;
	jboolean isCopy1 = false;
	jboolean isCopy2 = false;
	jboolean isCopy3 = false;
	jboolean isCopy4 = false;
	jboolean isCopy5 = false;
	jboolean isCopy6 = false;
	jboolean isCopy7 = false;
	jboolean isCopy8 = false;
	jboolean isCopy9 = false;
	jboolean isCopy10 = false;

#ifdef USE_CRITICAL
	jlong *longData = NULL;
	if (longArray != NULL) {
		longData = reinterpret_cast<jlong*>(_envP->GetPrimitiveArrayCritical(longArray, &isCopy0));
	}
	jdouble *doubleData1 = NULL;
	if (doubleArray1 != NULL) {
		doubleData1 = reinterpret_cast<jdouble*>(_envP->GetPrimitiveArrayCritical(doubleArray1, &isCopy1));
	}
	jdouble *doubleData2 = NULL;
	if (doubleArray2 != NULL) {
		doubleData2 = reinterpret_cast<jdouble*>(_envP->GetPrimitiveArrayCritical(doubleArray2, &isCopy2));
	}
	jdouble *doubleData3 = NULL;
	if (doubleArray3 != NULL) {
		doubleData3 = reinterpret_cast<jdouble*>(_envP->GetPrimitiveArrayCritical(doubleArray3, &isCopy3));
	}
	jdouble *doubleData4 = NULL;
	if (doubleArray4 != NULL) {
		doubleData4 = reinterpret_cast<jdouble*>(_envP->GetPrimitiveArrayCritical(doubleArray4, &isCopy4));
	}
	jdouble *doubleData5 = NULL;
	if (doubleArray5 != NULL) {
		doubleData5 = reinterpret_cast<jdouble*>(_envP->GetPrimitiveArrayCritical(doubleArray5, &isCopy5));
	}
	jdouble *doubleData6 = NULL;
	if (doubleArray6 != NULL) {
		doubleData6 = reinterpret_cast<jdouble*>(_envP->GetPrimitiveArrayCritical(doubleArray6, &isCopy6));
	}
	jdouble *doubleData7 = NULL;
	if (doubleArray7 != NULL) {
		doubleData7 = reinterpret_cast<jdouble*>(_envP->GetPrimitiveArrayCritical(doubleArray7, &isCopy7));
	}
	jdouble *doubleData8 = NULL;
	if (doubleArray8 != NULL) {
		doubleData8 = reinterpret_cast<jdouble*>(_envP->GetPrimitiveArrayCritical(doubleArray8, &isCopy8));
	}
	jdouble *doubleData9 = NULL;
	if (doubleArray9 != NULL) {
		doubleData9 = reinterpret_cast<jdouble*>(_envP->GetPrimitiveArrayCritical(doubleArray9, &isCopy9));
	}
	jdouble *doubleData10 = NULL;
	if (doubleArray10 != NULL) {
		doubleData10 = reinterpret_cast<jdouble*>(_envP->GetPrimitiveArrayCritical(doubleArray10, &isCopy10));
	}
#else
	jlong *longData = _envP->GetLongArrayElements(longArray, &isCopy0);
	jdouble *doubleData1 = _envP->GetDoubleArrayElements(doubleArray1, &isCopy1);
	jdouble *doubleData2 = _envP->GetDoubleArrayElements(doubleArray2, &isCopy2);
	jdouble *doubleData3 = _envP->GetDoubleArrayElements(doubleArray3, &isCopy3);
	jdouble *doubleData4 = _envP->GetDoubleArrayElements(doubleArray4, &isCopy4);
	jdouble *doubleData5 = _envP->GetDoubleArrayElements(doubleArray5, &isCopy5);
	jdouble *doubleData6 = _envP->GetDoubleArrayElements(doubleArray6, &isCopy6);
	jdouble *doubleData7 = _envP->GetDoubleArrayElements(doubleArray7, &isCopy7);
	jdouble *doubleData8 = _envP->GetDoubleArrayElements(doubleArray8, &isCopy8);
	jdouble *doubleData9 = _envP->GetDoubleArrayElements(doubleArray9, &isCopy9);
	jdouble *doubleData10 = _envP->GetDoubleArrayElements(doubleArray10, &isCopy10);
#endif

	/* Do path and waypoint secs. */
	int nPath = 0;
	if (longArray != NULL) {
		nPath = _envP->GetArrayLength(longArray);
	}
	long *waypointSecsS = new long[nPath];
	double *pathLats = new double[nPath];
	double *pathLngs = new double[nPath];
	for (int k = 0; k < nPath; ++k) {
		waypointSecsS[k] = static_cast<long>(longData[k]);
		pathLats[k] = static_cast<double>(doubleData1[k]);
		pathLngs[k] = static_cast<double>(doubleData2[k]);
	}

	/* Do specLoose. */
	int nSpecLoose = 0;
	if (doubleArray3 != NULL) {
		nSpecLoose = _envP->GetArrayLength(doubleArray3);
	}
	double *specLooseLats = new double[nSpecLoose];
	double *specLooseLngs = new double[nSpecLoose];
	for (int k = 0; k < nSpecLoose; ++k) {
		specLooseLats[k] = static_cast<double>(doubleData3[k]);
		specLooseLngs[k] = static_cast<double>(doubleData4[k]);
	}

	/* Do tsLoose and tsTight. */
	int nTsLoose = 0;
	if (doubleArray5 != NULL) {
		nTsLoose = _envP->GetArrayLength(doubleArray5);
	}
	double *tsLooseLats = new double[nTsLoose];
	double *tsLooseLngs = new double[nTsLoose];
	for (int k = 0; k < nTsLoose; ++k) {
		tsLooseLats[k] = static_cast<double>(doubleData5[k]);
		tsLooseLngs[k] = static_cast<double>(doubleData6[k]);
	}
	int nTsTight = 0;
	if (doubleArray7 != NULL) {
		nTsTight = _envP->GetArrayLength(doubleArray7);
	}
	double *tsTightLats = new double[nTsTight];
	double *tsTightLngs = new double[nTsTight];
	for (int k = 0; k < nTsTight; ++k) {
		tsTightLats[k] = static_cast<double>(doubleData7[k]);
		tsTightLngs[k] = static_cast<double>(doubleData8[k]);
	}

	/* Do excTight. */
	int nExcTight = 0;
	if (doubleArray9 != NULL) {
		nExcTight = _envP->GetArrayLength(doubleArray9);
	}
	double *excTightLats = new double[nExcTight];
	double *excTightLngs = new double[nExcTight];
	for (int k = 0; k < nExcTight; ++k) {
		excTightLats[k] = static_cast<double>(doubleData9[k]);
		excTightLngs[k] = static_cast<double>(doubleData10[k]);
	}

#ifdef USE_CRITICAL
	if (longData != NULL) {
		_envP->ReleasePrimitiveArrayCritical(longArray, longData, JNI_ABORT);
	}
	if (doubleData1 != NULL) {
		_envP->ReleasePrimitiveArrayCritical(doubleArray1, doubleData1, JNI_ABORT);
	}
	if (doubleData2 != NULL) {
		_envP->ReleasePrimitiveArrayCritical(doubleArray2, doubleData2, JNI_ABORT);
	}
	if (doubleData3 != NULL) {
		_envP->ReleasePrimitiveArrayCritical(doubleArray3, doubleData3, JNI_ABORT);
	}
	if (doubleData4 != NULL) {
		_envP->ReleasePrimitiveArrayCritical(doubleArray4, doubleData4, JNI_ABORT);
	}
	if (doubleData5 != NULL) {
		_envP->ReleasePrimitiveArrayCritical(doubleArray5, doubleData5, JNI_ABORT);
	}
	if (doubleData6 != NULL) {
		_envP->ReleasePrimitiveArrayCritical(doubleArray6, doubleData6, JNI_ABORT);
	}
	if (doubleData7 != NULL) {
		_envP->ReleasePrimitiveArrayCritical(doubleArray7, doubleData7, JNI_ABORT);
	}
	if (doubleData8 != NULL) {
		_envP->ReleasePrimitiveArrayCritical(doubleArray8, doubleData8, JNI_ABORT);
	}
	if (doubleData9 != NULL) {
		_envP->ReleasePrimitiveArrayCritical(doubleArray9, doubleData9, JNI_ABORT);
	}
	if (doubleData10 != NULL) {
		_envP->ReleasePrimitiveArrayCritical(doubleArray10, doubleData10, JNI_ABORT);
	}

#else
	_envP->ReleaseLongArrayElements(longArray, longData, 0);
	_envP->ReleaseDoubleArrayElements(doubleArray1, doubleData1, 0);
	_envP->ReleaseDoubleArrayElements(doubleArray2, doubleData2, 0);
	_envP->ReleaseDoubleArrayElements(doubleArray3, doubleData3, 0);
	_envP->ReleaseDoubleArrayElements(doubleArray4, doubleData4, 0);
	_envP->ReleaseDoubleArrayElements(doubleArray5, doubleData5, 0);
	_envP->ReleaseDoubleArrayElements(doubleArray6, doubleData6, 0);
	_envP->ReleaseDoubleArrayElements(doubleArray7, doubleData7, 0);
	_envP->ReleaseDoubleArrayElements(doubleArray8, doubleData8, 0);
	_envP->ReleaseDoubleArrayElements(doubleArray9, doubleData9, 0);
	_envP->ReleaseDoubleArrayElements(doubleArray10, doubleData10, 0);
#endif
	_envP->DeleteLocalRef(simLibPattern);

	if (nPath == 0) {
		return new SimPattern();
	}

	const double dCenterLat = static_cast<double>(centerLat_X);
	const double dCenterLng = static_cast<double>(centerLng_X);
	const double dOrntn = static_cast<double>(orntn_X);
	const bool bFirstTurnRight = firstTurnRight_X ? true : false;
	const double dMinTsNmi = static_cast<double>(minTsNmi_X);
	const double dFixedTsNmi = static_cast<double>(fixedTsNmi_X);
	const double dExcBufferNmi = static_cast<double>(excBufferNmi_X);
	const double dLenNmi = static_cast<double>(lenNmi_X);
	const double dWidNmi = static_cast<double>(widNmi_X);
	const bool bPs = ps_X ? true : false;
	const double dEplNmi = static_cast<double>(eplNmi_X);
	const double dRawSearchKtsToUse = static_cast<double>(rawSearchKtsToUse_X);
	const double dTsNmi = static_cast<double>(tsNmi_X);
	const double dSllNmi = static_cast<double>(sllNmi_X);

	return new SimPattern(
		dCenterLat, dCenterLng, dOrntn, bFirstTurnRight,
		dMinTsNmi, dFixedTsNmi, dExcBufferNmi,
		dLenNmi, dWidNmi, bPs,
		dEplNmi, dRawSearchKtsToUse,
		dTsNmi, dSllNmi,
		nPath, pathLats, pathLngs, waypointSecsS,
		nSpecLoose, specLooseLats, specLooseLngs,
		nTsLoose, tsLooseLats, tsLooseLngs,
		nTsTight, tsTightLats, tsTightLngs,
		nExcTight, excTightLats, excTightLngs
	);
}

LobsEllipse *SimCodeHookImpl::makeLobsEllipse(
	jdouble *thresholds,
	jint nLobs,
	jdouble **lobs,
	jboolean throwException)
{
	if (thresholds == NULL || nLobs <= 0 || lobs == NULL) {
		/* The input is forcing just a plumbing check. */
		jdoubleArray javaDoubleArray0 = (jdoubleArray)_envP->CallStaticObjectMethod(
			_wangsnessClass, _getEllipseId0, throwException
		);
		if (errorExists() || javaDoubleArray0 == NULL) {
			return new LobsEllipse();
		}
		jboolean isCopy0;
		jdouble *cDoubles0 = _envP->GetDoubleArrayElements(javaDoubleArray0, &isCopy0);
		double centerLat0 = cDoubles0[0];
		double centerLng0 = cDoubles0[1];
		double semiMajorNmi95PerCent0 = cDoubles0[2];
		double semiMinorNmi95PerCent0 = cDoubles0[3];
		double degsCwFromNorthOfSemiMajor0 = cDoubles0[4];
		if (isCopy0 == JNI_TRUE) {
			/* The "0" forces the freeing of cDoubles0. */
			_envP->ReleaseDoubleArrayElements(javaDoubleArray0, cDoubles0, 0);
		}

		/* Plumbing check 2. Intermediate. */
		jdouble doublesIn1[8];
		for (int k = 0; k < 8; ++k) {
			doublesIn1[k] = 2 * k + 100;
		}
		jdoubleArray inJavaDoubleArray1 = getJavaDoubleArray(8, doublesIn1);
		jdoubleArray javaDoubleArray1 = (jdoubleArray)_envP->CallStaticObjectMethod(
			_wangsnessClass, _getEllipseId1, inJavaDoubleArray1
		);
		if (errorExists() || javaDoubleArray1 == NULL) {
			return new LobsEllipse();
		}
		jboolean isCopy1;
		jdouble *cDoubles1 = _envP->GetDoubleArrayElements(javaDoubleArray1, &isCopy1);
		double centerLat1 = cDoubles1[0];
		double centerLng1 = cDoubles1[1];
		double semiMajorNmi95PerCent1 = cDoubles1[2];
		double semiMinorNmi95PerCent1 = cDoubles1[3];
		double degsCwFromNorthOfSemiMajor1 = cDoubles1[4];
		if (isCopy1 == JNI_TRUE) {
			/* The "0" forces the freeing of cDoubles1. */
			_envP->ReleaseDoubleArrayElements(javaDoubleArray1, cDoubles1, 0);
		}
		int *inUse = new int[3];
		return new LobsEllipse(
			centerLat1, centerLng1,
			semiMajorNmi95PerCent1, semiMinorNmi95PerCent1,
			degsCwFromNorthOfSemiMajor1, 3, inUse);
	}

	/* Real McCoy. */
	jdoubleArray javaThresholds = getJavaDoubleArray(NThresholdValues, thresholds);
	jdoubleArray *javaLobArrays = new jdoubleArray[nLobs];
	for (int k = 0; k < nLobs; ++k) {
		javaLobArrays[k] = getJavaDoubleArray(NDoublesPerBearingCall, lobs[k]);
	}
	jobjectArray javaLobArrayCluster = getJavaMatrix(nLobs, javaLobArrays);
	jdoubleArray javaDoubleArray2 = (jdoubleArray)_envP->CallStaticObjectMethod(
		_wangsnessClass, _getEllipseId2, javaThresholds, javaLobArrayCluster
	);
	if (_envP->ExceptionCheck()) {
		_envP->ExceptionDescribe();
		_envP->ExceptionClear();
		return new LobsEllipse();
	}
	int nDoubles = _envP->GetArrayLength(javaDoubleArray2);
	jboolean isCopy2;
	jdouble *cDoubles2 = _envP->GetDoubleArrayElements(javaDoubleArray2, &isCopy2);
	double centerLat2 = cDoubles2[0];
	double centerLng2 = cDoubles2[1];
	double semiMajorNmi95PerCent2 = cDoubles2[2];
	double semiMinorNmi95PerCent2 = cDoubles2[3];
	double degsCwFromNorthOfSemiMajor2 = cDoubles2[4];
	int nBasicReturnValues = 5;
	int nInUse = nDoubles - nBasicReturnValues;
	int *idxsInUse = new int[nInUse];
	for (int k = 0; k < nInUse; ++k) {
		idxsInUse[k] = (int)(cDoubles2[nBasicReturnValues + k] + 0.5);
	}
	if (isCopy2 == JNI_TRUE) {
		/* The "0" forces the freeing of cDoubles2. */
		_envP->ReleaseDoubleArrayElements(javaDoubleArray2, cDoubles2, 0);
	}
	delete[] javaLobArrays;
	return new LobsEllipse(
		centerLat2, centerLng2,
		semiMajorNmi95PerCent2, semiMinorNmi95PerCent2,
		degsCwFromNorthOfSemiMajor2,
		nInUse, idxsInUse);
}

NavCalc *SimCodeHookImpl::makeNavCalc(
	const jdouble lat0,
	const jdouble lng0,
	const jdouble lat1OrNmi,
	const jdouble lng1OrHdg,
	const char *const motionTypeId,
	const jboolean findRangeBearing)
{
	jvalue jValues[6];
	jValues[0].d = lat0;
	jValues[1].d = lng0;
	jValues[2].d = lat1OrNmi;
	jValues[3].d = lng1OrHdg;
	jValues[4].l = _envP->NewStringUTF(motionTypeId);
	jValues[5].z = findRangeBearing;

	jobject answerPair = _envP->CallStaticObjectMethodA(
		_navigationCalculatorStaticsClass, _answerBasicQuestionId, jValues
	);
	_envP->DeleteLocalRef(jValues[4].l);
	jboolean isCopy = false;
	if (errorExists()) {
		if (answerPair != NULL) {
			jdoubleArray doubleArray = reinterpret_cast<jdoubleArray&>(answerPair);
#ifdef USE_CRITICAL
			jdouble *doubleData = reinterpret_cast<jdouble*>(_envP->GetPrimitiveArrayCritical(doubleArray, &isCopy));
			_envP->ReleasePrimitiveArrayCritical(doubleArray, doubleData, JNI_ABORT);
#else
			jdouble *doubleData = _envP->GetDoubleArrayElements(doubleArray, &isCopy);
			_envP->ReleaseDoubleArrayElements(doubleArray, doubleData, 0);
#endif
		}
		return NULL;
	}
	jdoubleArray doubleArray = reinterpret_cast<jdoubleArray&>(answerPair);
#ifdef USE_CRITICAL
	jdouble *doubleData = reinterpret_cast<jdouble*>(_envP->GetPrimitiveArrayCritical(doubleArray, &isCopy));
# else
	jdouble *doubleData = _envP->GetDoubleArrayElements(doubleArray, &isCopy);
#endif
	double answer1 = static_cast<double>(doubleData[0]);
	double answer2 = static_cast<double>(doubleData[1]);
#ifdef USE_CRITICAL
	_envP->ReleasePrimitiveArrayCritical(doubleArray, doubleData, JNI_ABORT);
#else
	_envP->ReleaseDoubleArrayElements(doubleArray, doubleData, 0);
#endif
	const bool newFindRangeBearing = findRangeBearing ? true : false;
	const double lat1 = newFindRangeBearing ? lat1OrNmi : answer1;
	const double lng1 = newFindRangeBearing ? lng1OrHdg : answer2;
	const double rangeNmi = newFindRangeBearing ? answer1 : lat1OrNmi;
	const double bearing = newFindRangeBearing ? answer2 : lng1OrHdg;
	return new NavCalc(lat0, lng0, lat1, lng1, rangeNmi, bearing, motionTypeId, newFindRangeBearing);
}

jdouble SimCodeHookImpl::getLogOddsMaxPd(
	const jdouble ap0,
	const jdouble a1,
	const jdouble a2,
	const jdouble maxXValue) {
	jdouble maxPd = _envP->CallStaticDoubleMethod(_simUtilsClass, _getLogOddsMaxPdId, ap0, a1, a2, maxXValue);
	if (errorExists()) {
		return -1.0;
	}
	return maxPd;
}

jdouble SimCodeHookImpl::computeSweepWidth(
	const char *const eltString) {
	jstring s = _envP->NewStringUTF(eltString);
	if (errorExists()) {
		return -1.0;
	}
	jdouble sw = _envP->CallStaticDoubleMethod(_modelReaderClass, _computeSweepWidthId, s);
	if (errorExists()) {
		return -1.0;
	}
	_envP->DeleteLocalRef(s);
	return sw;
}

jobjectArray SimCodeHookImpl::getJavaStringArray(int n, const char **messages) {
	jobjectArray jObjectArray = (jobjectArray)_envP->NewObjectArray(
		n, _envP->FindClass("java/lang/String"), NULL
	);
	for (int i = 0; i < n; ++i) {
		_envP->SetObjectArrayElement(jObjectArray, i, _envP->NewStringUTF(messages[i]));
	}
	return jObjectArray;
}

jdoubleArray SimCodeHookImpl::getJavaDoubleArray(int n, const double *jdoubles)
{
	jdoubleArray jDoubleArray = _envP->NewDoubleArray(n);
	_envP->SetDoubleArrayRegion(jDoubleArray, 0, n, jdoubles);
	return jDoubleArray;
}

jobjectArray SimCodeHookImpl::getJavaMatrix(int nRows, const jdoubleArray *jDoubleArrays)
{
	jobjectArray jObjectArray = _envP->NewObjectArray(nRows, _envP->FindClass("[D"), NULL);
	for (int k = 0; k < nRows; ++k) {
		_envP->SetObjectArrayElement(jObjectArray, k, jDoubleArrays[k]);
	}
	return jObjectArray;
}

SimCodeHookImpl::~SimCodeHookImpl(void) {
#ifdef USING_GLOBAL_STRINGS
	_envP->DeleteGlobalRef(_globalStringsClass);
#endif
	_envP->DeleteGlobalRef(_printArgsClass);
	_envP->DeleteGlobalRef(_mathXClass);
	_envP->DeleteGlobalRef(_simLibPatternClass);
	_envP->DeleteGlobalRef(_wangsnessClass);
	_envP->DeleteGlobalRef(_navigationCalculatorStaticsClass);
	_envP->DeleteGlobalRef(_simUtilsClass);
	_jvmP->DestroyJavaVM();
	for (std::vector<char*>::iterator it = _optionStrings.begin(); it != _optionStrings.end(); ++it) {
		char *optionString = *it;
		delete[] optionString;
	}
	delete[] _vm_argOptions;
}

bool SimCodeHookImpl::errorExists()
{
	if (_envP->ExceptionCheck()) {
		_envP->ExceptionDescribe();
		_envP->ExceptionClear();
		return true;
	}
	return false;
}

void stripCrAndLf(std::string &str) {
	int firstKeeper = static_cast<int>(str.find_first_not_of(" \t\n\r"));
	str.erase(0, firstKeeper);
	int lastKeeper = static_cast<int>(str.find_last_not_of(" \t\n\r"));
	str.erase(lastKeeper + 1);
}
