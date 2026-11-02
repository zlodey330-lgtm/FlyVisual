package com.flyvisual.util;

import java.awt.Color;

public final class ColorUtil {
	private ColorUtil() {
	}

	/** Цвет из тона (0..360), насыщенности и яркости (0..1). Возвращает 0xRRGGBB. */
	public static int hsv(float hueDegrees, float saturation, float value) {
		return Color.HSBtoRGB((hueDegrees % 360.0f) / 360.0f, saturation, value) & 0xFFFFFF;
	}

	public static float red(int rgb) {
		return ((rgb >> 16) & 0xFF) / 255.0f;
	}

	public static float green(int rgb) {
		return ((rgb >> 8) & 0xFF) / 255.0f;
	}

	public static float blue(int rgb) {
		return (rgb & 0xFF) / 255.0f;
	}
}
