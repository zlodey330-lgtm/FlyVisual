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

	/** Названия тем меню (выбираются в модуле «Тема меню»). */
	public static final String[] THEME_NAMES = {"Графит", "Океан", "Закат", "Лес", "Сакура", "Чёрная (AMOLED)"};
	/** Для каждой темы: панель, плитка, плитка при наведении, правая панель, выключатель, фон экрана заголовка, акцент. */
	private static final int[][] THEMES = {
			{0xF0141416, 0xFF1C1C20, 0xFF27272C, 0xFF18181C, 0xFF3A3A3E, 0xFF08090B, 0xB4B8BF},
			{0xF00C1624, 0xFF122238, 0xFF1B3352, 0xFF0F1D30, 0xFF2C4260, 0xFF050B14, 0x29B6F6},
			{0xF01E1226, 0xFF2A1A36, 0xFF3B2550, 0xFF241530, 0xFF4A3558, 0xFF0C0610, 0xFF7A59},
			{0xF00E1A14, 0xFF15271D, 0xFF1F3A2A, 0xFF122119, 0xFF2F4A3A, 0xFF060D09, 0x43C26B},
			{0xF0221419, 0xFF331C25, 0xFF4A2735, 0xFF2B1820, 0xFF5A3A47, 0xFF0E0709, 0xF06292},
			{0xF0000000, 0xFF0A0A0A, 0xFF1A1A1A, 0xFF050505, 0xFF2A2A2A, 0xFF000000, 0xB4B8BF}
	};

	private Theme() {
	}

	/** Номер активной темы (0, если модуль «Тема меню» выключен). */
	public static int index() {
		com.flyvisual.module.impl.MenuThemeModule m = com.flyvisual.module.ModuleManager.menuTheme();
		if (m == null || !m.isEnabled()) return 0;
		return Math.max(0, Math.min(THEMES.length - 1, m.theme()));
	}

	public static int panelCol() {
		return THEMES[index()][0];
	}

	public static int tileCol() {
		return THEMES[index()][1];
	}

	public static int tileHoverCol() {
		return THEMES[index()][2];
	}

	public static int paneCol() {
		return THEMES[index()][3];
	}

	public static int offCol() {
		return THEMES[index()][4];
	}

	public static int titlePanelCol() {
		return THEMES[index()][5];
	}

	/** Акцент, который рекомендуется для темы. */
	public static int themeAccent(int i) {
		return THEMES[Math.max(0, Math.min(THEMES.length - 1, i))][6];
	}

	/** Акцентный цвет в формате ARGB. */
	public static int accent() {
		return 0xFF000000 | FlyConfig.get().accentColor;
	}
}
