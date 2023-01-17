#pragma once

class SIMLIB_API NavCalc
{
public:
	const double _lat0;
	const double _lng0;
	const double _lat1;
	const double _lng1;
	const double _rangeInNmi;
	const double _bearing;
	const char *const _motionTypeId;
	const bool _foundRangeBearing;

	NavCalc(
		double lat0, double lng0,
		double lat1, double lng1,
		const double rangeNmi, const double bearing,
		const char *const motionTypeId,
		const bool foundRangeBearing) :
		_lat0(lat0),
		_lng0(lng0),
		_lat1(lat1),
		_lng1(lng1),
		_rangeInNmi(rangeNmi),
		_bearing(bearing),
		_motionTypeId(motionTypeId),
		_foundRangeBearing(foundRangeBearing)
	{}

	NavCalc() :
		_lat0(91.0),
		_lng0(181.0),
		_lat1(91.0),
		_lng1(181.0),
		_rangeInNmi(-1.0),
		_bearing(361.0),
		_motionTypeId(NULL),
		_foundRangeBearing(true)
	{}

	~NavCalc() {}
};
