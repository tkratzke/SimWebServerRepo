#pragma once

#include <random>
#include <functional>

class CRng {
	unsigned long _mySeed;
	mutable std::mt19937 _mersenneTwisterEngine;
	std::uniform_real<double> _doubleDistribution;

public:
	CRng(unsigned long mySeed = 820305);
	const double nextDouble() const;
	void reset();
	static void quickTest();
};
