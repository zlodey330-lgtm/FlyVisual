package com.flyvisual.module.impl;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.config.Theme;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Тема меню FlyVisual: цвета панелей, плиток и фона. Меняет только вид меню мода и экрана заголовка.
 * Если модуль выключить, возвращается обычная графитовая тема.
 */
public class MenuThemeModule extends Module {
	private final DoubleSetting theme = addChoice("Menu theme", 0, Theme.THEME_NAMES);
	private final DoubleSetting applyAccent = addToggle("Apply theme accent", true);

	private int lastTheme = -1;

	public MenuThemeModule() {
		super("Menu Theme", "Color themes for the FlyVisual menu");
	}

	@Override
	protected boolean enabledByDefault() {
		return true;
	}

	public int theme() {
		return theme.getInt();
	}

	@Override
	public void onTick(MinecraftClient client) {
		int t = theme.getInt();
		if (lastTheme == -1) {
			lastTheme = t; // при запуске не перекрашиваем акцент, который выбрал игрок
			return;
		}
		if (t != lastTheme) {
			lastTheme = t;
			if (applyAccent.getBool()) {
				FlyConfig.get().accentColor = Theme.themeAccent(t);
				FlyConfig.save();
			}
		}
	}
}
