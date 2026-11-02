package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;

/** Опускает огонь на экране, когда ты горишь. Сама логика в InGameOverlayRendererMixin. */
public class LowerFireModule extends Module {
	private final DoubleSetting offset = addSetting("Offset", 0.3, 0.0, 0.6, 0.05);

	public LowerFireModule() {
		super("Lower Fire", "Lowers the fire overlay on screen");
	}

	public double getOffset() {
		return offset.get();
	}
}
