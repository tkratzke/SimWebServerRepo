package com.skagit.sarops.computePFail;

public class DetectValues {

	public enum PFailType {
		NFT, FT, AIFT;
	}

	final public boolean _empty;
	public double _proportionIn;
	public double _nftPFail;
	public double _ftPFail;
	public double _aiftPFail;
	final public static DetectValues _Empty;
	static {
		_Empty = new DetectValues(/* isEmpty= */true);
	}

	private DetectValues(final boolean empty) {
		_empty = empty;
		if (_empty) {
			_proportionIn = 0d;
			_nftPFail = _ftPFail = _aiftPFail = 1d;
		} else {
			_proportionIn = Double.NaN;
			_nftPFail = _ftPFail = _aiftPFail = Double.NaN;
		}
	}

	public DetectValues() {
		this(/* isEmpty= */false);
	}

	public double getPFail(final PFailType pFailType) {
		switch (pFailType) {
		case NFT:
			return _nftPFail;
		case FT:
			return _ftPFail;
		case AIFT:
			return _aiftPFail;
		default:
			break;
		}
		return Double.NaN;
	}

	@Override
	public String toString() {
		return getString();
	}

	public String getString() {
		return String.format("PropIn[%.3f] NFT[%.4g] FT[%.4g] AIFT[%.4g]", //
				_proportionIn, _nftPFail, _ftPFail, _aiftPFail);
	}

	public double getProportionIn() {
		return _proportionIn;
	}

	public static DetectValues getEmpty() {
		return _Empty;
	}

	/** Disable setters for _isEmpty. */
	public void setProportionIn(final double proportionIn) {
		if (!Double.isNaN(proportionIn)) {
			_proportionIn = proportionIn;
		}
	}

	public void setPFail(final PFailType pFailType, final double pFail) {
		assert !_empty : "Should not try to set the pFail of an Empty DetectValues.";
		if (!_empty) {
			switch (pFailType) {
			case NFT:
				/**
				 * We don't always try to set proportionIn even though the api makes
				 * it appear as if we do.
				 */
				_nftPFail = pFail;
				break;
			case FT:
				_ftPFail = pFail;
				break;
			case AIFT:
				_aiftPFail = pFail;
				break;
			default:
				assert false : "Setting WHAT(?!) PFail";
			}
		}
	}
}
