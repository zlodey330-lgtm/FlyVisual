package com.flyvisual.module.impl;

import com.flyvisual.module.Module;

/**
 * Главное меню FlyVisual (кнопки, фон) и живой фон в других экранах вне мира (одиночная игра, сетевая игра, настройки).
 * По умолчанию ВЫКЛЮЧЕНО: если у тебя уже стоит другое главное меню, FlyVisual его не трогает.
 * Включи модуль, чтобы вернуть меню FlyVisual (нужен перезапуск экрана: выйди в меню и зайди снова).
 */
public class TitleScreenModule extends Module {
	public TitleScreenModule() {
		super("Title Screen", "FlyVisual main menu and background");
	}

	@Override
	protected boolean enabledByDefault() {
		return false;
	}
}
