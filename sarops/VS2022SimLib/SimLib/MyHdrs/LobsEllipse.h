#pragma once

#include <SimLib.h>
#include <ostream>

class SIMLIB_API LobsEllipse
{
	std::ostream &dump(std::ostream &o) const;
public:
	const double _centerLat;
	const double _centerLng;
	const double _semiMajorNmi95PerCent;
	const double _semiMinorNmi95PerCent;
	const double _semiMajorDegsCwFromN;
	const int _nInUse;
	const int *_idxsInUse;

	LobsEllipse() :
		_centerLat(91.0),
		_centerLng(181.0),
		_semiMajorNmi95PerCent(0.0),
		_semiMinorNmi95PerCent(0.0),
		_semiMajorDegsCwFromN(361.0),
		_nInUse(0),
		_idxsInUse(0)
	{}

	LobsEllipse(
		const double centerLat,
		const double centerLng,
		const double semiMajorNmi95PerCent,
		const double semiMinorNmi95PerCent,
		const double semiMajorDegsCwFromN,
		const int nInUse,
		const int *idxsInUse) :
		_centerLat(centerLat),
		_centerLng(centerLng),
		_semiMajorNmi95PerCent(semiMajorNmi95PerCent),
		_semiMinorNmi95PerCent(semiMinorNmi95PerCent),
		_semiMajorDegsCwFromN(semiMajorDegsCwFromN),
		_nInUse(nInUse),
		_idxsInUse(idxsInUse)
	{}

	friend std::ostream &operator<<(std::ostream &o, const LobsEllipse &lobsEllipse) {
		return lobsEllipse.dump(o);
	}

	~LobsEllipse()
	{
		if (_idxsInUse != NULL) {
			delete[] _idxsInUse;
		}
	}
};
