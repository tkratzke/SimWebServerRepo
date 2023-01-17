#pragma once

#include <SimLib.h>
#include <ostream>

class SIMLIB_API SimPattern
{
	std::ostream &dump(std::ostream &o) const;
	void deletePair(const double *lats, const double *lngs);

public:
	/** The following 3 will get rounded and are inputs for all 3 pattern kinds. */
	const double _centerLat, _centerLng;
	const double _orntn;

	/** Input that applies to all 3 pattern kinds. */
	const bool _firstTurnRight;

	/** See comments in SimCodeHook.makePattern for the following 3.
	For LP and SS, I will assign a minTsNmi if you don't give me one,
	and I'll make sure that these are rounded.  excBufferNmi
	applies to all 3. */
	const double _minTsNmi;
	const double _fxdTsNmi;
	const double _excBufferNmi;

	/** See comments in SimCodeHook.makePattern for the following 3. */
	const double _lenNmi;
	const double _widNmi;
	const bool _ps;

	/* _eplNmi is what I use.  For LP and SS, this is the total track length
	plus one TS.  For VS, this is simply the total track length. */
	const double _eplNmi;

	/* _rawSearchKtsToUse is what you should use to get this back. */
	const double _rawSearchKtsToUse;

	/* Output for all 3 pattern kinds.*/
	const double _tsNmi;

	/* Output for LP only.*/
	const double _sllNmi;

	/* Description of the path. _waypointSecsS tells when each of the nPath points is reached. */
	const int _nPath;
	const double *_pathLats;
	const double *_pathLngs;
	const long *_waypointSecsS;

	/* The polygon that is derived directly from the input center/orntn/len/wid.
	Useful if you want to graph what the user actually asked for. There's even a
	(square) one of these for VS.*/
	const int _nSpec;
	const double *_specLooseLats;
	const double *_specLooseLngs;

	/** The resulting TS box; it has 4 corners and surrounds the path with a half-track-spacing buffer. */
	const int _nTsLoose;
	const double *_tsLooseLats;
	const double *_tsLooseLngs;

	/** The resulting TS box; As long as we restrict ourselves to perfect
	LP and SS patterns, this will be the same as tsLoose.  Otherwise, it
	could have 5 points. It's always the same as tsLoose for VS.*/
	const int _nTsTight;
	const double *_tsTightLats;
	const double *_tsTightLngs;

	/** The exclusion polygon; As long as we restrict ourselves to perfect
	LP and SS patterns, LP and SS will have 4 points.  Otherwise, it
	could have 5 points. For VS, it always has 6 points.*/
	const int _nExcTight;
	const double *_excTightLats;
	const double *_excTightLngs;

	SimPattern(
		const double centerLat, const double centerLng,
		const double orntn, const bool firstTurnRight,
		const double minTsNmi, const double fxdTsNmi, const double excBufferNmi,
		const double lenNmi, const double widNmi, const bool ps,
		const double eplNmi,
		const double rawSearchKtsToUse,
		const double tsNmi, const double sllNmi,
		/* Pattern and polygons output. */
		const int nPath, const double *pathLats, const double *pathLngs, const long *unixSeconds,
		const int nSpec, const double *specLooseLats, const double *specLooseLngs,
		const int nTsLoose, const double *tsLooseLats, const double *tsLooseLngs,
		const int nTsTight, const double *tsTightLats, const double *tsTightLngs,
		const int nExcTight, const double *excTightLats, const double *excTightLngs
	);
	SimPattern();
	~SimPattern();

	friend std::ostream &operator<<(std::ostream &o, const SimPattern &simPattern) {
		return simPattern.dump(o);
	}
};
