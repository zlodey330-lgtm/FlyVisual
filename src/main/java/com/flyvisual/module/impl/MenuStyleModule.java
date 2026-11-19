package com.flyvisual.module.impl;

import com.flyvisual.module.Module;

/** Вид главного меню и экранов вне мира: лес, ванильная панорама, звёзды, закат, минимализм или своя картинка. */
public class MenuStyleModule extends Module {
	public static MenuStyleModule INSTANCE;
	public static final String[] STYLES = {"Лес", "Ванильная панорама", "Звёздная ночь", "Закат", "Минимал", "Своя картинка", "Луч и частицы"};

	private final com.flyvisual.module.DoubleSetting style = addChoice("Menu style", 6, STYLES);
	private final com.flyvisual.module.DoubleSetting glass = addToggle("Круглые кнопки (новое меню)", true);
	private final com.flyvisual.module.DoubleSetting splash = addToggle("Живая заставка вместо Mojang", true);

	public MenuStyleModule() {
		super("Main Menu", "Changes the look of the main menu");
		INSTANCE = this;
	}

	@Override
	protected boolean enabledByDefault() {
		return true;
	}

	/** Живая анимация вместо экрана загрузки Mojang (запуск игры, смена ресурспака, F3+T). */
	public static boolean liveSplash() {
		MenuStyleModule m = INSTANCE;
		return m != null && m.isEnabled() && m.splash.getBool();
	}

	/** Новое главное меню с круглыми кнопками. Выключить можно переключателем в настройках (тогда будет обычное меню). */
	public static boolean glassMenu() {
		MenuStyleModule m = INSTANCE;
		return m != null && m.isEnabled() && m.glass.getBool() && !com.flyvisual.gui.FlyTitleScreen.failed;
	}

	/** 0 = лес (как раньше). Если модуль выключен, тоже лес. */
	public static int current() {
		MenuStyleModule m = INSTANCE;
		return m == null || !m.isEnabled() ? 0 : m.style.getInt();
	}
}
