package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;

/**
 * Соотношение сторон картинки (как "растянутое разрешение"): мир сжимается или растягивается
 * по ширине без смены разрешения окна. Подмена в GameRendererMixin.
 */
public class AspectRatioModule extends Module {
	private static final String[] PRESETS = {"Свой", "4:3", "5:4", "1:1", "16:10", "21:9", "32:9"};
	private static final float[] VALUES = {0.0f, 4.0f / 3.0f, 5.0f / 4.0f, 1.0f, 16.0f / 10.0f, 21.0f / 9.0f, 32.0f / 9.0f};

	private final DoubleSetting preset = addChoice("Aspect", 1, PRESETS);
	private final DoubleSetting custom = addSetting("Custom ratio", 1.33, 0.5, 3.0, 0.01);

	public AspectRatioModule() {
		super("Aspect Ratio", "Stretch or squeeze the picture");
	}

	/** Желаемое соотношение сторон, либо 0, если менять не нужно. */
	public float ratio() {
		int i = Math.max(0, Math.min(VALUES.length - 1, preset.getInt()));
		return i == 0 ? custom.getFloat() : VALUES[i];
	}
}
