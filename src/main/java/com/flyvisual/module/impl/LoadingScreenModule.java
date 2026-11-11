package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.config.Theme;

/**
 * Свой экран загрузки: вместо красного экрана Mojang — тёмный фон и надпись FlyVisual
 * (при запуске игры и при каждой перезагрузке ресурсов). Саму отрисовку делает SplashOverlayMixin.
 */
public class LoadingScreenModule extends Module {
	private static final String[] STYLES = {"По теме меню", "Чёрный", "Графит", "Тёмно-синий"};

	private final DoubleSetting style = addChoice("Loading style", 0, STYLES);
	private final DoubleSetting title = addToggle("Loading title", true);

	public LoadingScreenModule() {
		super("Loading Screen", "Custom game loading screen");
	}

	@Override
	protected boolean enabledByDefault() {
		return true;
	}

	/** Цвет фона загрузки (ARGB, непрозрачный). */
	public int background() {
		return switch (style.getInt()) {
			case 1 -> 0xFF000000;
			case 2 -> 0xFF141416;
			case 3 -> 0xFF0A1424;
			default -> 0xFF000000 | (Theme.titlePanelCol() & 0xFFFFFF);
		};
	}

	public boolean showTitle() {
		return title.getBool();
	}
}
