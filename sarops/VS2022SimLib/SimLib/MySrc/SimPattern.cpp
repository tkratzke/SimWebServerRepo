#include "StdAfx.h"

#include <SimPattern.h>


SimPattern::SimPattern(
	const double centerLat, const double centerLng,
	const double orntn, const bool firstTurnRight,
	const double minTsNmi, const double fxdTsNmi, const double excBufferNmi,
	const double lenNmi, const double widNmi, const bool ps,
	const double eplNmi, const double rawSearchKtsToUse,
	const double tsNmi, const double sllNmi,
	/* Pattern and polygons output. */
	const int nPath, const double *pathLats, const double *pathLngs, const long *unixSeconds,
	const int nSpec, const double *specLooseLats, const double *specLooseLngs,
	const int nTsLoose, const double *tsLooseLats, const double *tsLooseLngs,
	const int nTsTight, const double *tsTightLats, const double *tsTightLngs,
	const int nExcTight, const double *excTightLats, const double *excTightLngs
) :
	_centerLat(centerLat), _centerLng(centerLng),
	_orntn(orntn), _firstTurnRight(firstTurnRight),
	_minTsNmi(minTsNmi), _fxdTsNmi(fxdTsNmi), _excBufferNmi(excBufferNmi),
	_lenNmi(lenNmi), _widNmi(widNmi), _ps(ps),
	_eplNmi(eplNmi), _rawSearchKtsToUse(rawSearchKtsToUse),
	_tsNmi(tsNmi), _sllNmi(sllNmi),
	/* Pattern and polygons output. */
	_nPath(nPath), _pathLats(pathLats), _pathLngs(pathLngs), _waypointSecsS(unixSeconds),
	_nSpec(nSpec), _specLooseLats(specLooseLats), _specLooseLngs(specLooseLngs),
	_nTsLoose(nTsLoose), _tsLooseLats(tsLooseLats), _tsLooseLngs(tsLooseLngs),
	_nTsTight(nTsTight), _tsTightLats(tsTightLats), _tsTightLngs(tsTightLngs),
	_nExcTight(nExcTight), _excTightLats(excTightLats), _excTightLngs(excTightLngs)
{}


SimPattern::SimPattern() :
	_centerLat(-91.0), _centerLng(-181.0),
	_orntn(0.0), _firstTurnRight(true),
	_minTsNmi(-1.0), _fxdTsNmi(-1.0), _excBufferNmi(-1.0),
	_lenNmi(-1.0), _widNmi(-1.0), _ps(true),
	_rawSearchKtsToUse(-1.0), _eplNmi(-1.0),
	_tsNmi(-1.0), _sllNmi(-1.0),
	/* Pattern and polygons output. */
	_nPath(-1), _pathLats(NULL), _pathLngs(NULL), _waypointSecsS(NULL),
	_nSpec(-1), _specLooseLats(NULL), _specLooseLngs(NULL),
	_nTsLoose(-1), _tsLooseLats(NULL), _tsLooseLngs(NULL),
	_nTsTight(-1), _tsTightLats(NULL), _tsTightLngs(NULL),
	_nExcTight(-1), _excTightLats(NULL), _excTightLngs(NULL)
{}

std::ostream &SimPattern::dump(std::ostream &o) const {
	char buffer[2048];
	sprintf_s(buffer, "RAW_SPD_TO_USE[%f] LEN[%.4f] WID[%.4f]"
		"\n\tSLL[%.4f] TS[%.4f], PS[%c] FrstTrnRt[%c] "
		"nPath[%d] nSpec[%d] nTsLoose[%d] nTsTight[%d] nExcTight[%d]",
		_rawSearchKtsToUse,
		_lenNmi, _widNmi, _sllNmi, _tsNmi,
		_ps ? 'T' : 'F', _firstTurnRight ? 'T' : 'F',
		_nPath, _nSpec, _nTsLoose, _nTsTight, _nExcTight);
	o << buffer;
	return o;
}

void SimPattern::deletePair(const double *lats, const double *lngs)
{
	if (lats != NULL) {
		delete[] lats;
	}
	if (lngs != NULL) {
		delete[] lngs;
	}
}

SimPattern::~SimPattern()
{
	if (_waypointSecsS != NULL) {
		delete[] _waypointSecsS;
	}
	deletePair(_pathLats, _pathLngs);
	deletePair(_specLooseLats, _specLooseLngs);
	deletePair(_tsLooseLats, _tsLooseLngs);
	deletePair(_tsTightLats, _tsTightLngs);
	deletePair(_excTightLats, _excTightLngs);
}
