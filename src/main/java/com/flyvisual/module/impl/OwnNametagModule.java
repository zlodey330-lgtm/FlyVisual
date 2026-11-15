package com.flyvisual.module.impl;

import com.flyvisual.module.Module;

/** Свой ник над головой при виде от 3 лица (F5). */
public class OwnNametagModule extends Module {
	public static OwnNametagModule INSTANCE;

	public OwnNametagModule() {
		super("Own Nametag", "Shows your own name in third person");
		INSTANCE = this;
	}

	@Override
	protected boolean enabledByDefault() {
		return true;
	}
}
