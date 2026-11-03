package com.flyvisual.module.impl;

import com.flyvisual.module.Module;

/** Опускает огонь на экране, когда ты горишь. Настроек нет, только вкл/выкл. Логика в InGameOverlayRendererMixin. */
public class LowerFireModule extends Module {
	private static final double OFFSET = 0.3;

	public LowerFireModule() {
		super("Lower Fire", "Lowers the fire overlay on screen");
	}

	public double getOffset() {
		return OFFSET;
	}
}
