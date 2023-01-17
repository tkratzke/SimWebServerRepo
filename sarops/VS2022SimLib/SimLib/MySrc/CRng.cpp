#include <CRng.h>

#include <random>

CRng::CRng(unsigned long mySeed) :
	_mySeed(mySeed), _mersenneTwisterEngine(mySeed), _doubleDistribution(0, 1)
{
}

const double CRng::nextDouble() const {
	double d = _doubleDistribution(_mersenneTwisterEngine);
	return d;
}

void CRng::reset() {
	_mersenneTwisterEngine.seed(_mySeed);
}

void CRng::quickTest()
{
	CRng *rngP = new CRng();
	CRng &rng = *rngP;
	double d1 = rng.nextDouble();
	double d2 = rng.nextDouble();
	rng.reset();
	double d3 = rng.nextDouble();
	delete rngP;
}
