package com.skagit.sarops.model;

import java.util.ArrayList;

import com.skagit.util.Graphics;

public class ExtraGraphicsClass {
	final private ArrayList<Graphics> _extraModelGraphics;

	public ExtraGraphicsClass() {
		_extraModelGraphics = new ArrayList<>();
	}

	public ArrayList<Graphics> getExtraModelGraphics() {
		return _extraModelGraphics;
	}

	public void addCircleGraphics(final double centerLat,
			final double centerLng, final double radiusInNmi,
			final String colorName) {
		_extraModelGraphics.add(new Graphics(Graphics.GraphicsType.CIRCLE,
				new double[] { centerLat, centerLng, radiusInNmi }, colorName));
	}

	public void addEllipseGraphics(final double centerLat,
			final double centerLng, final double smiMjrNmi,
			final double smiMnrNmi, final double smiMjrHdg,
			final String colorName) {
		_extraModelGraphics
				.add(
						new Graphics(
								Graphics.GraphicsType.ELLIPSE, new double[] { centerLat,
										centerLng, smiMjrNmi, smiMnrNmi, smiMjrHdg },
								colorName));
	}

	public void addPointToDrawGraphics(final double lat, final double lng,
			final String colorName) {
		_extraModelGraphics
				.add(new Graphics(Graphics.GraphicsType.POINT_TO_DRAW,
						new double[] { lat, lng }, colorName));
	}

	public void addLatLngEdgeGraphics(final double lat0, final double lng0,
			final double lat1, final double lng1, final int number,
			final long t0RefSecs, final long t1RefSecs, final String colorName) {
		_extraModelGraphics.add(
				new Graphics(Graphics.GraphicsType.LL_EDGE, new double[] { lat0,
						lng0, lat1, lng1, number, t0RefSecs, t1RefSecs }, colorName));
	}

	public void addExtentGraphics(final double left, final double low,
			final double right, final double high, final int number,
			final String colorName) {
		_extraModelGraphics.add(new Graphics(Graphics.GraphicsType.EXTENT,
				new double[] { left, low, right, high, number }, colorName));
	}

	public void addLatLngGridGaps(final double latGap, final double lngGap) {
		_extraModelGraphics.add(new Graphics(Graphics.GraphicsType.LL_GRID_GAPS,
				new double[] { latGap, lngGap }));
	}

	public double[] getDefaultLatAndLngGaps() {
		for (final Graphics graphics : _extraModelGraphics) {
			if (graphics._type == Graphics.GraphicsType.LL_GRID_GAPS) {
				return graphics._parameters.clone();
			}
		}
		return null;
	}

	public ArrayList<Graphics> getLatLngEdgesToDraw() {
		ArrayList<Graphics> returnValue = null;
		for (final Graphics graphics : _extraModelGraphics) {
			if (graphics._type == Graphics.GraphicsType.LL_EDGE) {
				if (returnValue == null) {
					returnValue = new ArrayList<>();
				}
				returnValue.add(graphics);
			}
		}
		return returnValue;
	}

	public ArrayList<Graphics> getPointsToDraw() {
		ArrayList<Graphics> returnValue = null;
		for (final Graphics graphics : _extraModelGraphics) {
			if (graphics._type == Graphics.GraphicsType.POINT_TO_DRAW) {
				if (returnValue == null) {
					returnValue = new ArrayList<>();
				}
				returnValue.add(graphics);
			}
		}
		return returnValue;
	}
}
