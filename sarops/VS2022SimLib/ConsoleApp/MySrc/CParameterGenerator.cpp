#include <CParameterGenerator.h>

CParameterGenerator::CParameterGenerator(
	double lowSpeed, double highSpeed,
	long lowDurationInSeconds, long highDurationInSeconds,
	double lowLat, double highLat,
	double lowLng, double highLng,
	double lowDirInDegsCwFromNorth, double highDirInDegsCwFromNorth,
	int lowFirstTurnRight, int highFirstTurnRight,
	double lowMinimumTsInNmi, double highMinimumTsInNmi,
	int lowUseFixedTs, int highUseFixedTs,
	double lowLengthMultiplier, double highLengthMultiplier,
	double lowWidth, double highWidth,
	int lowPsCs, int highPsCs,
	const unsigned long mySeed) :
	_mySeed(mySeed), _mersenneTwisterEngine(mySeed),
	_speedDistribution(lowSpeed, highSpeed),
	_durationDistribution(lowDurationInSeconds, highDurationInSeconds),
	_latDistribution(lowLat, highLat),
	_lngDistribution(lowLng, highLng),
	_dirDistribution(lowDirInDegsCwFromNorth, highDirInDegsCwFromNorth),
	_firstTurnRightDistribution(lowFirstTurnRight, highFirstTurnRight),
	_minTsDistribution(lowMinimumTsInNmi, highMinimumTsInNmi),
	_useFixedTsDistribution(lowUseFixedTs, highUseFixedTs),
	_lengthMultiplierDistribution(lowLengthMultiplier, highLengthMultiplier),
	_widthDistribution(lowWidth, highWidth),
	_psCsDistribution(lowPsCs, highPsCs)
{}

void CParameterGenerator::setNext() {
	_speedInKts = _speedDistribution(_mersenneTwisterEngine);
	_durationInSeconds = _durationDistribution(_mersenneTwisterEngine);
	_lat = _latDistribution(_mersenneTwisterEngine);
	_lng = _lngDistribution(_mersenneTwisterEngine);
	_orntn = _dirDistribution(_mersenneTwisterEngine);
	_firstTurnRight = _firstTurnRightDistribution(_mersenneTwisterEngine) != 0;
	_minTsInNmi = _minTsDistribution(_mersenneTwisterEngine);
	bool useFixedTs = _useFixedTsDistribution(_mersenneTwisterEngine) != 0;
	_fxdTsNmi = useFixedTs ? _minTsInNmi : -1;
	double widthInNmi = _widthDistribution(_mersenneTwisterEngine);
	_lenNmi = widthInNmi * _lengthMultiplierDistribution(_mersenneTwisterEngine);
	_widNmi = widthInNmi;
	_ps = _psCsDistribution(_mersenneTwisterEngine) != 0;
}

void CParameterGenerator::reset() {
	_mersenneTwisterEngine.seed(_mySeed);
}

std::ostream &CParameterGenerator::dump(std::ostream &o) const
{
	char buffer[2048];
	bool haveFixedTs = _fxdTsNmi > 0;
	if (haveFixedTs) {
		sprintf_s(buffer, "Spd[%.4f] DrtnInScnds[%ld] Lat/Lng[%.4f/%.4f] "
			"Orntn[%.4f] FrstTrnRt[%c] MnTs[%.4f] FixedTs[%.4f] "
			"Len[%.4f] Wid[%.4f] Ps[%c] ",
			_speedInKts, _durationInSeconds,
			_lat, _lng, _orntn, boolToChar(_firstTurnRight),
			_minTsInNmi, _fxdTsNmi,
			_lenNmi, _widNmi,
			boolToChar(_ps)
		);
	}
	else {
		sprintf_s(buffer, "Spd[%.4f] DrtnInScnds[%ld] Lat/Lng[%.4f/%.4f] Orntn[%.4f] FrstTrnRt[%c] "
			"MnTs[%.4f] Len[%.4f] Wid[%.4f] Ps[%c]",
			_speedInKts, _durationInSeconds,
			_lat, _lng,
			_orntn, boolToChar(_firstTurnRight),
			_minTsInNmi,
			_lenNmi, _widNmi,
			boolToChar(_ps)
		);
	}
	o << buffer;
	return o;
}
