package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;

/** Анимация тотема бессмертия: можно уменьшить или скрыть совсем. Логика в GameRendererMixin. */
public class TotemAnimationModule extends Module {
	private final DoubleSetting hide = addToggle("Hide animation", false);
	private final DoubleSetting size = addSetting("Size", 0.6, 0.2, 2.0, 0.1);

	public TotemAnimationModule() {
		super("Totem Animation", "Resize or hide the totem pop animation");
	}

	public boolean shouldHide() {
		return hide.getBool();
	}

	public float getSize() {
		return size.getFloat();
	}
}
