#pragma once

#include <random>
#include <functional>
/** Good article on C++ Random Number Generator: http://www.johndcook.com/cpp_TR1_random.html#seed */

class CParameterGenerator {
	std::ostream &dump(std::ostream &o) const;
	friend std::ostream &operator<<(std::ostream &o, const CParameterGenerator &parameterGenerator) {
		return parameterGenerator.dump(o);
	}

	/** Random Number Generator data. */
	unsigned long _mySeed;
	mutable std::mt19937 _mersenneTwisterEngine;
	std::uniform_real<double> const _speedDistribution;
	std::uniform_int<long> const _durationDistribution;
	std::uniform_real<double> const _latDistribution;
	std::uniform_real<double> const _lngDistribution;
	std::uniform_real<double> const _dirDistribution;
	std::uniform_int<int> const _firstTurnRightDistribution;
	std::uniform_real<double> const _minTsDistribution;
	std::uniform_int<int> const _useFixedTsDistribution;
	std::uniform_real<double> const _lengthMultiplierDistribution;
	std::uniform_real<double> const _widthDistribution;
	std::uniform_int<int> const _psCsDistribution;
public:
	/** Answers. */
	double _speedInKts;
	long _durationInSeconds;
	double _lat, _lng;
	double _orntn;
	bool _firstTurnRight;
	double _minTsInNmi;
	double _fxdTsNmi;
	double _lenNmi;
	double _widNmi;
	bool _ps;

public:
	CParameterGenerator(
		double lowSpeed = 60.0, double highSpeed = 180.0,
		long lowDurationInSeconds = 1800, long highDurationInSeconds = 7200,
		double lowLat = -90.0, double highLat = 90.0,
		double lowLng = -180.0, double highLng = 180.0,
		double lowDirInDegsCwFromNorth = 0.0, double highDirInDegsCwFromNorth = 360.0,
		int lowFirstTurnRight = 0, int highFirstTurnRight = 1,
		double lowMinimumTsInNmi = 0.1, double highMinimumTsInNmi = 3.0,
		int lowUseFixedTs = 0, int highUseFixedTs = 1,
		double lowLengthMultiplier = 0.5, double highLengthMultiplier = 10.0,
		double lowWidth = 5.0, double highWidth = 20.0,
		int lowPsCs = 0, int highPsCs = 1,
		unsigned long mySeed = 820305);
	void setNext();
	void setNext(
		const double speedInKts,
		const long durationInSeconds,
		const double lat,
		const double lng,
		const double orntn,
		const bool firstTurnRight,
		const double minTsInNmi,
		const double fixedTsInNmi,
		const double lengthInNmi,
		const double widthInNmi,
		const bool ps
	)
	{
		_speedInKts = speedInKts;
		_durationInSeconds = durationInSeconds;
		_lat = lat;
		_lng = lng;
		_orntn = orntn;
		_firstTurnRight = firstTurnRight;
		_minTsInNmi = minTsInNmi;
		_fxdTsNmi = fixedTsInNmi;
		_lenNmi = lengthInNmi;
		_widNmi = widthInNmi;
		_ps = ps;
	}
	bool haveFixedTs() {
		return _fxdTsNmi > 0;
	}
	void reset();
	static char boolToChar(bool b) {
		return b ? 'T' : 'F';
	}
};
