/**
 *
 */
package com.skagit.sarops.environment;

/**
 * A class implementing this interface must, for any time index, produce
 * speeds and standard deviations for east and north.
 */
public interface UvCalculator {
	DataForOnePointAndTime getDataForOnePointAndTime(int timeIdx);
}
