#include "StdAfx.h"

#include <LobsEllipse.h>

std::ostream &LobsEllipse::dump(std::ostream &o) const {
	char buffer[2048];
	sprintf_s(buffer, "\n\tInC++: CntrLat[%.1f] CntrLng[%.1f] SemiMajor95Nmi[%.2f] SemiMinor95Nmi[%.2f] "
		"semiMajorDegsCwFromN[%.2f]",
		_centerLat, _centerLng, _semiMajorNmi95PerCent, _semiMinorNmi95PerCent, _semiMajorDegsCwFromN);
	o << buffer;
	return o;
}
