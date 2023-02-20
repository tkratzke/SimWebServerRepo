package com.skagit.sarops.model;

public class DebrisObjectType extends SearchObjectType {

	public DebrisObjectType(final int id, final String name) {
		super(id, name);
	}

	@Override
	public boolean isDebris() {
		return true;
	}

	@Override
	public String getString() {
		return String.format("DO-Name/ID[%s/%d]", getName(), getId());
	}

}
