#pragma once

// The following ifdef block is the standard way of creating macros which make exporting 
// from a DLL simpler. All files within this DLL are compiled with the SIMLIB_EXPORTS
// symbol defined on the command line. This symbol should not be defined on any project
// that uses this DLL. This way any other project whose source files include this file see 
// SIMLIB_API functions as being imported from a DLL, whereas this DLL sees symbols
// defined with this macro as being exported.
#ifdef SIMLIB_EXPORTS
#define SIMLIB_API __declspec(dllexport)
#else
#define SIMLIB_API __declspec(dllimport)
#endif

// This class is exported from the SimLib.dll
class SIMLIB_API CSimLib {
public:
	CSimLib(void);
	// TODO: add your methods here.
};

extern SIMLIB_API int nSimLib;

SIMLIB_API int fnSimLib(void);
