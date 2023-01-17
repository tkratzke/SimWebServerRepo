// SimLib.cpp : Defines the exported functions for the DLL application.
//

#include "stdafx.h"
#include "SimLib.h"


// This is an example of an exported variable
SIMLIB_API int nSimLib = 0;

// This is an example of an exported function.
SIMLIB_API int fnSimLib(void)
{
	return 42;
}

// This is the constructor of a class that has been exported.
// see SimLib.h for the class definition
CSimLib::CSimLib()
{
	return;
}
