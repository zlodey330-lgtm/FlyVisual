package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.ColorUtil;

/**
 * Свой цвет хитбоксов. Сами хитбоксы по-прежнему показываются ванильным F3+B:
 * модуль только перекрашивает основную рамку. Через стены ничего не рисуется.
 * Подмена цвета происходит в EntityRenderDispatcherMixin.
 */
public class HitboxModule extends Module {
	private final DoubleSetting hue = addSetting("Color (hue)", 0.0, 0.0, 355.0, 5.0);
	private final DoubleSetting brightness = addSetting("Brightness", 100.0, 30.0, 100.0, 5.0);
	private final DoubleSetting rainbow = addToggle("Rainbow", false);

	public HitboxModule() {
		super("Hitboxes", "Custom hitbox color (F3+B)");
	}

	/** Цвет рамки: r, g, b, a в диапазоне 0..1. */
	public float[] color() {
		float h = rainbow.getBool() ? (float) ((System.nanoTime() / 1.0e9 * 60.0) % 360.0) : hue.getFloat();
		int rgb = ColorUtil.hsv(h, 0.9f, brightness.getFloat() / 100.0f);
		return new float[]{ColorUtil.red(rgb), ColorUtil.green(rgb), ColorUtil.blue(rgb), 1.0f};
	}
}
