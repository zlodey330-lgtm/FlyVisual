package com.flyvisual.module.impl;

import com.flyvisual.module.Module;

/** Вид главного меню и экранов вне мира: лес, ванильная панорама, звёзды, закат, минимализм или своя картинка. */
public class MenuStyleModule extends Module {
	public static MenuStyleModule INSTANCE;
	public static final String[] STYLES = {"Лес", "Ванильная панорама", "Звёздная ночь", "Закат", "Минимал", "Своя картинка"};

	private final com.flyvisual.module.DoubleSetting style = addChoice("Menu style", 0, STYLES);

	public MenuStyleModule() {
		super("Main Menu", "Changes the look of the main menu");
		INSTANCE = this;
	}

	@Override
	protected boolean enabledByDefault() {
		return true;
	}

	/** 0 = лес (как раньше). Если модуль выключен, тоже лес. */
	public static int current() {
		MenuStyleModule m = INSTANCE;
		return m == null || !m.isEnabled() ? 0 : m.style.getInt();
	}
}
