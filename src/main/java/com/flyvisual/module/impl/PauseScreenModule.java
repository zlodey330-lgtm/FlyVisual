package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;

/**
 * Свой экран паузы (Esc в игре): фон в цветах темы меню, надпись FlyVisual и кнопки в стиле FlyVisual.
 * Функции кнопок остаются ванильными (вернуться, настройки, выйти и т.д.), меняется только вид.
 * Саму отрисовку делают PressableWidgetMixin и ScreenBackgroundMixin.
 */
public class PauseScreenModule extends Module {
	private final DoubleSetting buttons = addToggle("Pause buttons", true);
	private final DoubleSetting radius = addSetting("Pause corner radius", 5.0, 0.0, 9.0, 1.0);
	private final DoubleSetting tint = addSetting("Pause tint", 55.0, 0.0, 90.0, 5.0);
	private final DoubleSetting title = addToggle("Pause title", true);

	public PauseScreenModule() {
		super("Pause Screen", "Custom pause menu look");
	}

	@Override
	protected boolean enabledByDefault() {
		return true;
	}

	public boolean restylesButtons() {
		return isEnabled() && buttons.getBool();
	}

	public int cornerRadius() {
		return radius.getInt();
	}

	/** Сила затемнения 0..1. */
	public float tint() {
		return tint.getFloat() / 100.0f;
	}

	public boolean showTitle() {
		return isEnabled() && title.getBool();
	}
}
