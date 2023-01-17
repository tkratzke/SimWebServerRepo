package com.skagit.sarops.planner.plannerModel.pvSeq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.w3c.dom.Element;

import com.skagit.sarops.model.Sortie;
import com.skagit.sarops.model.io.ModelReader;
import com.skagit.sarops.planner.plannerModel.PatternVariable;
import com.skagit.sarops.planner.plannerModel.PlannerModel;
import com.skagit.sarops.planner.posFunction.PvValue;
import com.skagit.sarops.simCaseManager.MainRunner;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.TimeUtilities;
import com.skagit.util.greatCircleArc.GreatCircleArc;
import com.skagit.util.navigation.LatLng3;

public class PvSeq {

	public static final int _NominalTransitSecs = 1800;

	/** Identifiers; _pvSeqOrd can be reset. */
	final public String _id;
	private int _pvSeqOrd;

	/** Information about the start. */
	final public int _totalDurationSecs;
	final public long _launchRefSecs;
	final public long _pvSeqCstRefSecs;
	final public LatLng3 _launchLatLng;

	/** Information about the end. */
	final public double _recoveryKts;
	final public LatLng3 _recoveryLatLng;

	/** The core information. */
	final private PatternVariable[] _myPttrnVbls;

	final public static Comparator<PvSeq> _ById = new Comparator<>() {

		@Override
		public int compare(final PvSeq pvSeq0, final PvSeq pvSeq1) {
			final String id0 = pvSeq0 == null ? null : pvSeq0._id;
			final String id1 = pvSeq1 == null ? null : pvSeq1._id;
			if (id0 == id1) {
				return 0;
			}
			if (id0 == null) {
				return -1;
			}
			if (id1 == null) {
				return 1;
			}
			return id0.compareTo(id1);
		}
	};

	/**
	 * Main ctor.
	 *
	 * @param simModel
	 */
	public PvSeq(final PlannerModel plannerModel,
			final Map<String[], Integer> nameIdMap, final Element pvSeqElt,
			final List<PatternVariable> unintendedStandAlones,
			final TreeSet<ModelReader.StringPlus> stringPluses) {
		/** _pvSeqOrd will be reset after all PvSeqs have been read in. */
		_pvSeqOrd = -1;
		final SimCaseManager.SimCase simCase = plannerModel.getSimCase();
		int totalDurationSecs = -1;
		long launchRefSecs = -1L;
		LatLng3 launchLatLng = null;
		long pvSeqCstRefSecs = -1L;
		String id = ModelReader.getString(simCase, pvSeqElt, "sortieID",
				/* default= */null, stringPluses);
		if (id == null) {
			id = ModelReader.getString(simCase, pvSeqElt, "pvSeqID",
					/* default= */null, stringPluses);
		}
		if (id == null) {
			MainRunner.HandleFatal(simCase,
					new RuntimeException("Need Id for PvSeq."));
		}
		_id = id;
		try {
			final double ttlEnduranceMins = ModelReader.getDouble(simCase,
					pvSeqElt, "totalEndurance", " mins", stringPluses);
			totalDurationSecs = (int) Math.round(60d * ttlEnduranceMins);
		} catch (final ModelReader.ReaderException e) {
		}
		if (totalDurationSecs <= 0) {
			MainRunner.HandleFatal(simCase,
					new RuntimeException("Need Total Endurance for PvSeq."));
		}
		_totalDurationSecs = totalDurationSecs;

		final String launchTimeString = pvSeqElt.getAttribute("launchTime");
		launchRefSecs = TimeUtilities.dtgToRefSecs(launchTimeString);
		if (launchRefSecs == PlannerModel._UnsetTime) {
			final String pvSeqCstString = pvSeqElt.getAttribute("sortieCst");
			pvSeqCstRefSecs = TimeUtilities.dtgToRefSecs(pvSeqCstString);
		} else {
			pvSeqCstRefSecs = PlannerModel._UnsetTime;
		}
		if ((launchRefSecs == PlannerModel._UnsetTime) == (pvSeqCstRefSecs == PlannerModel._UnsetTime)) {
			MainRunner
					.HandleFatal(simCase,
							new RuntimeException(String.format(
									"Inconsistent Launch/Cst for PvSeq %s.  " +
											"Treating its PatternVariables as StandAlones.",
									id)));
		}
		_pvSeqCstRefSecs = pvSeqCstRefSecs;
		_launchRefSecs = launchRefSecs;

		try {
			final double launchLat = ModelReader.getDouble(simCase, pvSeqElt,
					"launchLat", "", stringPluses);
			final double launchLng = ModelReader.getDouble(simCase, pvSeqElt,
					"launchLng", "", stringPluses);
			launchLatLng = LatLng3.getLatLngB(launchLat, launchLng);
		} catch (final ModelReader.ReaderException e) {
		}
		if (launchLatLng == null) {
			MainRunner
					.HandleFatal(simCase,
							new RuntimeException(String.format(
									"Invalid Launch LatLng for PvSeq %s.  " +
											"Treating its PatternVariables as StandAlones.",
									id)));
		}
		_launchLatLng = launchLatLng;

		double recoveryLat = Double.NaN;
		double recoveryLng = Double.NaN;
		double recoveryKts = Double.NaN;
		try {
			recoveryLat = ModelReader.getDouble(simCase, pvSeqElt, "recoveryLat",
					"", stringPluses);
			recoveryLng = ModelReader.getDouble(simCase, pvSeqElt, "recoveryLng",
					"", stringPluses);
			recoveryKts = ModelReader.getDouble(simCase, pvSeqElt,
					"recoverySpeed", " kts", /* default= */Double.NaN, stringPluses);
		} catch (final ModelReader.ReaderException e) {
		}
		if (Double.isNaN(recoveryLat) != Double.isNaN(recoveryLng) ||
				Double.isNaN(recoveryLat) != Double.isNaN(recoveryKts)) {
			MainRunner.HandleFatal(simCase,
					new RuntimeException("Inconsistent recovery data."));
		}
		_recoveryLatLng = LatLng3.getLatLngB(recoveryLat, recoveryLng);
		_recoveryKts = recoveryKts;

		/** Read in the PatternVariables. */
		final ArrayList<PatternVariable> pttrnVblList =
				plannerModel.readPttrnVbls(nameIdMap, this, unintendedStandAlones,
						pvSeqElt, stringPluses);

		/** Clean up my own PttrnVbls. */
		final Comparator<PatternVariable> comparator =
				new Comparator<>() {

					@Override
					public int compare(final PatternVariable pv0,
							final PatternVariable pv1) {
						final int ordWithinPv0 = pv0.getOrdWithinPvSeq();
						final int ordWithinPv1 = pv1.getOrdWithinPvSeq();
						return ordWithinPv0 < ordWithinPv1 ? -1 :
								(ordWithinPv0 > ordWithinPv1 ? 1 : 0);
					}
				};
		Collections.sort(pttrnVblList, comparator);

		/**
		 * Delete duplicates. Note; it.remove below does a shift of the
		 * remaining arrayList so we maintain order.
		 */
		final Iterator<PatternVariable> it = pttrnVblList.iterator();
		for (PatternVariable recentPv = null; it.hasNext();) {
			final PatternVariable pv = it.next();
			final int ordWithinPv = pv.getOrdWithinPvSeq();
			if (recentPv == null || ordWithinPv > recentPv.getOrdWithinPvSeq()) {
				it.remove();
				continue;
			}
			recentPv = pv;
		}

		/**
		 * Reset ordWithinPvSeq for each PatternVariable, and store the array.
		 */
		final int nPttrnVbls = pttrnVblList.size();
		_myPttrnVbls = new PatternVariable[nPttrnVbls];
		for (int ordWithinPvSeq = 0; ordWithinPvSeq < nPttrnVbls;
				++ordWithinPvSeq) {
			final PatternVariable pv = pttrnVblList.get(ordWithinPvSeq);
			pv.resetOrdWithinPvSeq(ordWithinPvSeq);
			_myPttrnVbls[ordWithinPvSeq] = pv;
		}
	}

	/** Accessors. */
	public int getPvSeqOrd() {
		return _pvSeqOrd;
	}

	public void resetPvSeqOrd(final int pvSeqOrd) {
		_pvSeqOrd = pvSeqOrd;
	}

	public boolean hasRecoveryTransit() {
		return _recoveryKts > 0d && _recoveryLatLng != null;
	}

	public PatternVariable[] getMyPttrnVbls() {
		return _myPttrnVbls;
	}

	public PlannerModel getPlannerModel() {
		return (_myPttrnVbls == null || _myPttrnVbls.length == 0) ? null :
				_myPttrnVbls[0].getPlannerModel();
	}

	public int getNMyPttrnVbls() {
		return _myPttrnVbls.length;
	}

	public PatternVariable getPttrnVbl(final int ordWithinPvSeq) {
		return (ordWithinPvSeq < 0 || ordWithinPvSeq >= _myPttrnVbls.length) ?
				null : _myPttrnVbls[ordWithinPvSeq];
	}

	/** Computational routines. */
	/**
	 * Filters out duplicates, onMars, and inActives. Neither input nor output
	 * is full-canonical.
	 */
	public PvValue[] gatherMine(final PvValue[] pvValues) {
		final int nPvValues = pvValues == null ? 0 : pvValues.length;
		final ArrayList<PvValue> myPvValueList = new ArrayList<>();
		final BitSet alreadyThere = new BitSet();
		for (int k0 = 0; k0 < nPvValues; ++k0) {
			final PvValue pvValue = pvValues[k0];
			final PatternVariable pv = pvValue == null ? null : pvValue.getPv();
			if (pv == null || pvValue.onMars() || !pv.isActive() ||
					pv.getPvSeq() != this) {
				continue;
			}
			final int grandOrd = pv.getGrandOrd();
			/**
			 * This one is good, except that we don't take it if we've already
			 * taken a PvValue with PatternVariable pv.
			 */
			if (alreadyThere.get(grandOrd)) {
				continue;
			}
			myPvValueList.add(pvValue);
			alreadyThere.set(grandOrd);
		}
		final int nMine = myPvValueList.size();
		final PvValue[] myPvValues = myPvValueList.toArray(new PvValue[nMine]);
		Arrays.sort(myPvValues, PvValue._ByPvOrdWithinPvSeq);
		return myPvValues;
	}

	/**
	 * Input is assumed to be just for this and sorted by ordWithinPvSeq. If
	 * the first one is not frozen, then we push it back to the launch and
	 * work forward from there. Output is item-by-item overwrite.
	 */
	public PvValue[] adjustMyCsts(final PvValue[] myPvValuesIn) {
		final int nMyPvValues = myPvValuesIn == null ? 0 : myPvValuesIn.length;
		if (nMyPvValues == 0) {
			return new PvValue[0];
		}

		/** The launch time might be unset, so pvsRefSecs might start unset. */
		long pvsEstRefSecs = _launchRefSecs;
		LatLng3 pvsEnd = _launchLatLng;

		final PvValue[] myPvValuesOut = myPvValuesIn.clone();
		for (int k = 0; k < nMyPvValues; ++k) {
			final PvValue pvValue = myPvValuesIn[k];
			final PatternVariable pv = pvValue.getPv();
			final PvValue pvValueA;
			if (pv.getUserFrozenPvValue() != null) {
				/** We know what we have. */
				pvValueA = pvValue;
			} else {
				final Sortie sortie = pvValue.getSortie();
				final GreatCircleArc transitGca =
						GreatCircleArc.CreateGca(pvsEnd, sortie.getStart());
				final double transitNmi = transitGca.getTtlNmi();
				final double transitKts = pv.getTransitKts();
				final long transitSecs =
						Math.round(3600d * (transitNmi / transitKts));
				if (k == 0 && pvsEstRefSecs == ModelReader._UnsetTime) {
					/**
					 * It's assumed that pvs is the launch and the launch has an
					 * unknown time. Compute that pvsEstRefSecs.
					 */
					pvsEstRefSecs = _pvSeqCstRefSecs - transitSecs;
				}
				final long cstRefSecs = pvsEstRefSecs + transitSecs;
				if (cstRefSecs != pvValue.getCstRefSecs()) {
					pvValueA = pvValue.adjustCst(cstRefSecs);
				} else {
					pvValueA = pvValue;
				}
			}
			myPvValuesOut[k] = pvValueA;
			pvsEstRefSecs = pvValueA.getEstRefSecs();
			pvsEnd = pvValueA.getSortie().getEnd();
		}
		return myPvValuesOut;
	}

	/** myFloaters is assumed to be ordered by _ordWithinPvSeq. */
	public long getDurationSecs(final PvValue starter,
			final PvValue[] myFloaters, final PvValue stopper) {
		final int nMyFloaters = myFloaters == null ? 0 : myFloaters.length;
		if (nMyFloaters == 0) {
			return -1L;
		}

		/** If starter is null, then we start at launch. */
		LatLng3 pvsEnd =
				starter == null ? _launchLatLng : starter.getSortie().getEnd();

		long durationSecs = 0L;
		for (int k = 0; k < nMyFloaters; ++k) {
			final PvValue floater = myFloaters[k];
			final PatternVariable floaterPv = floater.getPv();
			final LatLng3 thisStart = floater.getSortie().getStart();
			final GreatCircleArc transitGca =
					GreatCircleArc.CreateGca(pvsEnd, thisStart);
			final double transitNmi = transitGca.getTtlNmi();
			final double transitKts = floaterPv.getTransitKts();
			final long transitSecs =
					Math.round(3600d * (transitNmi / transitKts));
			durationSecs += transitSecs + floater.getSearchDurationSecs();
			pvsEnd = floater.getSortie().getEnd();
		}
		/** If nowhere to go after the floaters, we're done. */
		if (stopper != null || _recoveryLatLng != null) {
			final LatLng3 thisStart = stopper == null ? _recoveryLatLng :
					stopper.getSortie().getStart();
			final double transitKts =
					stopper == null ? _recoveryKts : stopper.getPv().getTransitKts();
			final GreatCircleArc transitGca =
					GreatCircleArc.CreateGca(pvsEnd, thisStart);
			final double transitNmi = transitGca.getTtlNmi();
			final long transitSecs =
					Math.round(3600d * (transitNmi / transitKts));
			durationSecs += transitSecs;
		}
		return durationSecs;
	}

	/** Shifts the Cst's around. Input and output are full-canonical. */
	public static PvValue[] adjustCsts(final PvValue[] full) {
		if (full == null) {
			return null;
		}
		final PlannerModel plannerModel = PlannerModel.getPlannerModel(full);

		final PvValue[] returnValue = full.clone();
		final int nPvSeqs = plannerModel.getNPvSeqs();
		for (int pvSeqOrd = 0; pvSeqOrd < nPvSeqs; ++pvSeqOrd) {
			final PvSeq pvSeq = plannerModel.getPvSeq(pvSeqOrd);
			final PvValue[] myPvValuesIn = pvSeq.gatherMine(full);
			final PvValue[] myPvValuesOut = pvSeq.adjustMyCsts(myPvValuesIn);
			final int nMyPvValues = myPvValuesIn.length;
			for (int k = 0; k < nMyPvValues; ++k) {
				final PvValue pvValue = myPvValuesIn[k];
				final PatternVariable pv = pvValue.getPv();
				final int grandOrd = pv.getGrandOrd();
				returnValue[grandOrd] = myPvValuesOut[k];
			}
		}
		return returnValue;
	}

	public Block[] findBlocks(final PvValue[] pvValues) {
		final int nMyPttrnVbls = _myPttrnVbls.length;
		/** Count the number of drifters within each block. */
		PatternVariable[][] drifterSets = null;
		/**
		 * <pre>
		 * 1st time through, count the number of blocks.
		 * 2nd time through, count the number of drifters in each block.
		 * 3rd time through, accumulate the drifters and create the blocks.
		 * </pre>
		 */
		Block[] blocks = null;
		for (int iPass = 0; iPass < 3; ++iPass) {
			int nBlocks = 0;
			int nDrifters = 0;
			PvValue starter = null;
			for (int ordWithinPvSeq = 0; ordWithinPvSeq < nMyPttrnVbls;
					++ordWithinPvSeq) {
				final PatternVariable pv = _myPttrnVbls[ordWithinPvSeq];
				if (!pv.isActive()) {
					continue;
				}
				final int grandOrd = pv.getGrandOrd();
				final PvValue pvValue = pvValues[grandOrd];
				if (pvValue != null && pvValue.onMars()) {
					continue;
				}
				if (pvValue == null) {
					/** pv is a drifter. */
					if (iPass == 2) {
						drifterSets[nBlocks][nDrifters] = pv;
					}
					++nDrifters;
					continue;
				}
				/**
				 * We have a Block if nDrifters > 0. Regardless, we will then start
				 * a new Block with pvValue.
				 */
				if (nDrifters > 0) {
					if (iPass == 2) {
						blocks[nBlocks] = new Block(starter, drifterSets[nBlocks],
								/* stopper= */pvValue, pvValues);
					}
					if (iPass == 1) {
						drifterSets[nBlocks] = new PatternVariable[nDrifters];
					}
					++nBlocks;
					nDrifters = 0;
				}
				starter = pvValue;
			}
			if (nDrifters > 0) {
				/** We're at the end and have a block. */
				if (iPass == 2) {
					blocks[nBlocks] = new Block(starter, drifterSets[nBlocks],
							/* stopper= */null, pvValues);
				}
				if (iPass == 1) {
					drifterSets[nBlocks] = new PatternVariable[nDrifters];
				}
				++nBlocks;
				nDrifters = 0;
			}
			if (iPass == 0) {
				drifterSets = new PatternVariable[nBlocks][];
				blocks = new Block[nBlocks];
			}
		}
		return blocks;
	}

	public class Block {
		final public PvValue _starter;
		final public PatternVariable[] _drifters;
		final public PvValue _stopper;
		final public long _earliestPossibleCst;
		final public long _latestPossibleEst;
		final public int _nTimePads;
		final public PvValue[] _context;

		public Block(final PvValue starter, final PatternVariable[] drifters,
				final PvValue stopper, final PvValue[] context) {
			_starter = starter;
			_drifters = drifters;
			_stopper = stopper;
			_context = context == null ? null : context.clone();
			final PvSeq pvSeq = PvSeq.this;
			int nTimePads = 0;
			if (_starter != null) {
				_earliestPossibleCst = _starter.getEstRefSecs();
			} else {
				if (pvSeq._launchRefSecs != ModelReader._UnsetTime) {
					_earliestPossibleCst = pvSeq._launchRefSecs;
				} else {
					_earliestPossibleCst =
							pvSeq._pvSeqCstRefSecs - pvSeq._totalDurationSecs;
					++nTimePads;
				}
			}
			if (_stopper != null) {
				_latestPossibleEst = _stopper.getCstRefSecs();
			} else {
				if (pvSeq._launchRefSecs != ModelReader._UnsetTime) {
					_latestPossibleEst =
							pvSeq._launchRefSecs + pvSeq._totalDurationSecs;
				} else {
					/**
					 * See if we can figure out from _context, what launchRefSecs is.
					 * We can only do that if the first non-Mars is set.
					 */
					long latestPossibleEst = ModelReader._UnsetTime;
					final int nInContext = _context == null ? 0 : _context.length;
					final int nInPvSeq = pvSeq.getNMyPttrnVbls();
					for (int ordWithinPvSeq = 0; ordWithinPvSeq < nInPvSeq;
							++ordWithinPvSeq) {
						final PatternVariable pv = pvSeq.getPttrnVbl(ordWithinPvSeq);
						/** Is pv in context? */
						PvValue contextPvValue = null;
						for (int k = 0; k < nInContext; ++k) {
							final PvValue pvValue = _context[k];
							if (pvValue != null && pvValue.getPv() == pv) {
								contextPvValue = pvValue;
								break;
							}
						}
						if (contextPvValue == null) {
							/** We don't know what this PatternVariable is. */
							latestPossibleEst =
									pvSeq._pvSeqCstRefSecs + pvSeq._totalDurationSecs;
							++nTimePads;
							break;
						} else if (contextPvValue.onMars()) {
							/** This one is on Mars, so we look for the next one. */
							continue;
						} else {
							/**
							 * This is the first non-onMars PatternVariable for pvSeq, and
							 * it's known. We can get a launch time.
							 */
							final GreatCircleArc transitGca =
									GreatCircleArc.CreateGca(pvSeq._launchLatLng,
											contextPvValue.getSortie().getStart());
							final double kts = contextPvValue.getPv().getTransitKts();
							final long transitSecs =
									Math.round(3600d * transitGca.getTtlNmi() / kts);
							final long launchRefSecs =
									pvSeq._pvSeqCstRefSecs - transitSecs;
							latestPossibleEst = launchRefSecs + pvSeq._totalDurationSecs;
							break;
						}
					}
					if (latestPossibleEst == ModelReader._UnsetTime) {
						latestPossibleEst =
								pvSeq._pvSeqCstRefSecs + pvSeq._totalDurationSecs;
					}
					_latestPossibleEst = latestPossibleEst;
				}
			}
			_nTimePads = nTimePads;
		}

		public long getNominalCstRefSecs() {
			if (_starter == null) {
				final PvSeq pvSeq = PvSeq.this;
				if (pvSeq._launchRefSecs == ModelReader._UnsetTime) {
					return pvSeq._pvSeqCstRefSecs;
				}
				return pvSeq._launchRefSecs + _NominalTransitSecs;
			}
			return _starter.getEstRefSecs() + _NominalTransitSecs;
		}

		public PvSeq getOwningPvSeq() {
			return PvSeq.this;
		}
	}

	public static Block[] getAllBlocks(final PlannerModel plannerModel,
			final PvValue[] pvValues) {
		final ArrayList<Block> blockList = new ArrayList<>();
		final int nPvSeqs = plannerModel.getNPttrnVbls();
		for (int pvSeqOrd = 0; pvSeqOrd < nPvSeqs; ++pvSeqOrd) {
			final PvSeq pvSeq = plannerModel.getPvSeq(pvSeqOrd);
			blockList.addAll(Arrays.asList(pvSeq.findBlocks(pvValues)));
		}
		final int nBlocks = blockList.size();
		final Block[] blocks = blockList.toArray(new Block[nBlocks]);
		Arrays.sort(blocks, new Comparator<Block>() {

			@Override
			public int compare(final Block block0, final Block block1) {
				final long cstRefSecs0 = block0.getNominalCstRefSecs();
				final long cstRefSecs1 = block1.getNominalCstRefSecs();
				if (cstRefSecs0 != cstRefSecs1) {
					return cstRefSecs0 < cstRefSecs1 ? -1 : 1;
				}
				final PvSeq pvSeq0 = block0.getOwningPvSeq();
				final PvSeq pvSeq1 = block1.getOwningPvSeq();
				if (pvSeq0 != pvSeq1) {
					return pvSeq0._pvSeqOrd < pvSeq1._pvSeqOrd ? -1 : 1;
				}
				return 0;
			}
		});
		return blocks;
	}

}
