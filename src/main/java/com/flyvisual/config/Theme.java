package com.flyvisual.config;

/**
 * Тема интерфейса. Все элементы берут акцентный цвет отсюда,
 * поэтому смена цвета в настройках перекрашивает всё сразу.
 */
public final class Theme {
	/** Готовые цвета: серебро (по умолчанию), графит, красный, голубой, фиолетовый, зелёный, оранжевый. */
	public static final int[] PRESETS = {
			0xB4B8BF,
			0x6E7681,
			0xE53935,
			0x29B6F6,
			0x8E24AA,
			0x43A047,
			0xFB8C00
	};

	public static final int PANEL_BG = 0x90000000;
	public static final int TEXT = 0xFFFFFFFF;
	public static final int TEXT_DIM = 0xFFAAAAAA;

	private Theme() {
	}

	/** Акцентный цвет в формате ARGB. */
	public static int accent() {
		return 0xFF000000 | FlyConfig.get().accentColor;
	}
}
