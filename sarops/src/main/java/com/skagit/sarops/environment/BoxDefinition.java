package com.skagit.sarops.environment;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Stack;
import java.util.zip.ZipInputStream;

import org.apache.commons.codec.binary.Base64;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import com.skagit.sarops.model.Model;
import com.skagit.sarops.simCaseManager.MainRunner;
import com.skagit.sarops.simCaseManager.SimCaseManager;
import com.skagit.util.LsFormatter;
import com.skagit.util.MathX;
import com.skagit.util.TimeUtilities;
import com.skagit.util.navigation.Extent;
import com.skagit.util.navigation.LatLng3;

import ucar.nc2.NetcdfFile;

/**
 * A BoxDefinition is the guts of a call to the EDS. In fact, it does the
 * interfacing with the EDS and produces a NetcdfFile in memory. It needs a
 * DynamicEnvUvGetter to produce the "boilerplate" part of the URI.
 */
public class BoxDefinition {
	final private static int _MaxNHours = 24;
	final private SimCaseManager.SimCase _simCase;
	final private Model _model;
	final private String _tag;
	public long _lastAccessTimeInMillis;
	public final long _lowRefSecs;
	public final long _highRefSecs;
	public final Extent _extent;
	private URL _lastTriedUrl;
	public final Stack<BoxDefinition> _tries;
	final private Stack<NetCdfUvGetter.Summary> _tryResults;

	/** Called from union; cannot truncate _highRefSecs. */
	public BoxDefinition(final SimCaseManager.SimCase simCase, final Model model, final String tag,
			final long lowRefSecs, final long highRefSecs, final Extent extent) {
		_simCase = simCase;
		_model = model;
		_tag = tag;
		_lowRefSecs = lowRefSecs;
		_highRefSecs = highRefSecs;
		checkRefSecsInterval();
		_extent = extent;
		_tries = new Stack<>();
		_tryResults = new Stack<>();
		_tries.add(this);
		_lastTriedUrl = null;
		_lastAccessTimeInMillis = System.currentTimeMillis();
	}

	private void checkRefSecsInterval() {
		if (_highRefSecs > _lowRefSecs + 4 * _MaxNHours * 3600) {
			final boolean includeSeconds = false;
			final String s1 = TimeUtilities.formatTime(_lowRefSecs, includeSeconds);
			final String s2 = TimeUtilities.formatTime(_highRefSecs, includeSeconds);
			System.err.printf("Very large time interval: %s to %s", s1, s2);
		}
	}

	public BoxDefinition(final SimCaseManager.SimCase simCase, final Model model, final long lowRefSecs,
			final LatLng3 latLng, final double nmiBuffer) {
		_simCase = simCase;
		_model = model;
		_tag = null;
		_lowRefSecs = lowRefSecs;
		final long highRefSecs = model.getHighRefSecs();
		_highRefSecs = Math.min(_lowRefSecs + _MaxNHours * 3600, highRefSecs);
		checkRefSecsInterval();
		final double lat = latLng.getLat();
		final double deltaLat = nmiBuffer / 60d;
		final double s = lat - deltaLat;
		final double n = lat + deltaLat;
		final double lng = latLng.getLng();
		final double deltaLng = nmiBuffer / 60d / MathX.cosX(Math.toRadians(lat));
		final double w = LatLng3.roundToLattice0_360L(lng - deltaLng);
		final double e = LatLng3.roundToLattice0_360L(lng + deltaLng);
		_extent = new Extent(w, s, e, n);
		_tries = new Stack<>();
		_tryResults = new Stack<>();
		_tries.add(this);
		_lastTriedUrl = null;
		_lastAccessTimeInMillis = System.currentTimeMillis();
	}

	public BoxDefinition(final long lowRefSecs, final long highRefSecsX, final Extent extent,
			final double[] requiredBuffers) {
		_simCase = null;
		_model = null;
		_tag = null;
		final long highRefSecs;
		if (requiredBuffers != null) {
			_lowRefSecs = Math.round(lowRefSecs - requiredBuffers[0]);
			highRefSecs = Math.round(highRefSecsX + requiredBuffers[1]);
			final double wX = extent.getLeftLng() - requiredBuffers[2];
			final double w = LatLng3.roundToLattice180_180I(wX);
			final double s = extent.getMinLat() - requiredBuffers[3];
			final double eX = extent.getRightLng() + requiredBuffers[4];
			final double e = LatLng3.roundToLattice180_180I(eX);
			final double n = extent.getMaxLat() + requiredBuffers[5];
			_extent = new Extent(w, s, e, n);
		} else {
			_lowRefSecs = lowRefSecs;
			highRefSecs = highRefSecsX;
			_extent = extent;
		}
		_highRefSecs = Math.min(_lowRefSecs + _MaxNHours * 3600, highRefSecs);
		checkRefSecsInterval();
		_lastAccessTimeInMillis = Long.MIN_VALUE;
		_tries = null;
		_tryResults = null;
		_lastTriedUrl = null;
	}

	private double getVolume() {
		return _extent.getLngRng() * (_extent.getMaxLat() - _extent.getMinLat());
	}

	public double getRatio(final BoxDefinition boxDefinition) {
		final double separateVolumes = getVolume() + boxDefinition.getVolume();
		final BoxDefinition union = union(boxDefinition);
		final double unionVolume = union.getVolume();
		return unionVolume / separateVolumes;
	}

	public BoxDefinition union(final BoxDefinition boxDefinition) {
		final long lowRefSecs = Math.min(_lowRefSecs, boxDefinition._lowRefSecs);
		final long highRefSecs = Math.max(_highRefSecs, boxDefinition._highRefSecs);
		Extent extent = _extent.clone();
		extent = extent.buildExtension(boxDefinition._extent);
		return new BoxDefinition(_simCase, _model, _tag, lowRefSecs, highRefSecs, extent);
	}

	public String getString(final boolean doTries) {
		String s = "";
		if (doTries) {
			s = String.format("External Form of URL's submission:\n\t%s", _lastTriedUrl.toExternalForm());
			s += "\n\t";
		}
		if (doTries && _tag != null) {
			s += String.format("Tag[%s], ", _tag);
		}
		final String lowTimeString = TimeUtilities.formatTime(_lowRefSecs, false);
		final String highTimeString = TimeUtilities.formatTime(_highRefSecs, false);
		s += String.format("\n\t\t%s→%s", lowTimeString, highTimeString);
		s += ", " + _extent.getString();
		if (doTries) {
			s += " Tries:";
			for (final BoxDefinition thisTry : _tries) {
				s += thisTry.getString(false);
			}
		}
		return s;
	}

	public NetcdfFile getNetCdfFile(final URI uri, final boolean zipped)
			throws MalformedURLException, DOMException, IOException {
		_lastTriedUrl = uri.toURL();
		final InputStream inputStream = _lastTriedUrl.openStream();
		Document document = null;
		try (final BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream)) {
			final InputSource inputSource = new InputSource(bufferedInputStream);
			document = LsFormatter._DocumentBuilder.parse(inputSource);
		} catch (final Exception e) {
			MainRunner.HandleFatal(_simCase, new RuntimeException(e));
		}
		final Element documentElement = document.getDocumentElement();
		final Node node = documentElement.getChildNodes().item(0);
		final String textContent = node.getTextContent();
		final byte[] decodedBytes = new Base64().decode(textContent.getBytes());
		final int length = decodedBytes.length;
		final byte[] allBytes;
		if (zipped) {
			byte[] allBytesX = null;
			final int offset = 0;
			final ByteArrayInputStream byteInputStream = new ByteArrayInputStream(decodedBytes, offset, length);
			try (final ZipInputStream zipInputStream = new ZipInputStream(byteInputStream)) {
				zipInputStream.getNextEntry();
				try (final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(1 << 13)) {
					final int bufferSize = 1 << 11;
					for (;;) {
						final byte buffer[] = new byte[bufferSize];
						final int count = zipInputStream.read(buffer, 0, bufferSize);
						if (count == -1) {
							break;
						}
						byteArrayOutputStream.write(buffer, 0, count);
					}
					zipInputStream.closeEntry();
					zipInputStream.close();
					allBytesX = byteArrayOutputStream.toByteArray();
				}
			} catch (final Exception e) {
				MainRunner.HandleFatal(_simCase, new RuntimeException(e));
			}
			allBytes = allBytesX;
		} else {
			allBytes = decodedBytes;
		}
		/** Read it into memory. */
		@SuppressWarnings("deprecation")
		final NetcdfFile netCdfFile = NetcdfFile.openInMemory(uri.toASCIIString(), allBytes);
		return netCdfFile;
	}

	public String[] getStringsInUse() {
		final String[] returnValue = new String[6];
		final BoxDefinition inUse = _tries.lastElement();
		final long lowRefSecs = inUse._lowRefSecs;
		final long highRefSecs = inUse._highRefSecs;
		final Extent extent = inUse._extent;
		returnValue[0] = TimeUtilities.formatTime(lowRefSecs, false);
		returnValue[1] = TimeUtilities.formatTime(highRefSecs, false);
		returnValue[2] = String.format("%.3f degs", extent.getLeftLng());
		returnValue[3] = String.format("%.3f degs", extent.getMinLat());
		returnValue[4] = String.format("%.3f degs", extent.getRightLng());
		returnValue[5] = String.format("%.3f degs", extent.getMaxLat());
		return returnValue;
	}

	public boolean contains(final long refSecs, final Extent extent) {
		return contains(refSecs) && contains(extent);
	}

	public boolean contains(final long refSecs) {
		return _lowRefSecs <= refSecs && refSecs <= _highRefSecs;
	}

	public boolean contains(final Extent extent) {
		final boolean mustBeClean = false;
		return _extent.surrounds(extent, mustBeClean);
	}

	public boolean contains(final LatLng3 latLng) {
		return _extent.contains(latLng);
	}

	public boolean contains(final long refSecs, final LatLng3 latLng) {
		final boolean haveTime = contains(refSecs);
		final boolean haveLatLng = contains(latLng);
		return haveTime && haveLatLng;
	}

	public void addTry(final BoxDefinition inUse) {
		_tries.push(inUse);
	}

	public void addUvGetterSummary(final NetCdfUvGetter.Summary summary) {
		_tryResults.push(summary);
	}

	public BoxDefinition getLastTry() {
		return _tries.lastElement();
	}

	public NetCdfUvGetter.Summary getLastTryResult() {
		return _tryResults.lastElement();
	}

	public SimCaseManager.SimCase getSimCase() {
		return _simCase;
	}

	public Model getModel() {
		return _model;
	}

	public long getLastAccessTimeInMillis() {
		return _lastAccessTimeInMillis;
	}

	public void setLastAccessTimeInMillis(final long lastAccessTimeInMillis) {
		_lastAccessTimeInMillis = lastAccessTimeInMillis;
	}

	public Extent getExtent() {
		return _extent;
	}

	public void clearExtraTries() {
		while (_tries.size() > 1) {
			_tries.pop();
		}
		_tryResults.clear();
	}

	public long getLowRefSecs() {
		return _lowRefSecs;
	}

	public long getHighRefSecs() {
		return _highRefSecs;
	}
}
