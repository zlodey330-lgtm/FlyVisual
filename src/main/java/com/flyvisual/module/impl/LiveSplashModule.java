package com.flyvisual.module.impl;

import com.flyvisual.gui.IntroFx;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;

/**
 * Живая заставка: красивая анимация вместо экрана загрузки Mojang (запуск игры, смена ресурспака, F3+T).
 * Если выключить модуль, будет обычная заставка Mojang.
 */
public class LiveSplashModule extends Module {
	public static LiveSplashModule INSTANCE;

	private final DoubleSetting minSeconds = addSetting("Минимум, секунд", 2.2, 1.0, 6.0, 0.1);
	private final DoubleSetting chime = addToggle("Звук вспышки", true);

	public LiveSplashModule() {
		super("Live Splash", "Animated splash instead of the Mojang loading screen");
		INSTANCE = this;
	}

	@Override
	protected boolean enabledByDefault() {
		return true;
	}

	/** Включена ли живая заставка. */
	public static boolean active() {
		LiveSplashModule m = INSTANCE;
		return m != null && m.isEnabled();
	}

	/** Минимум секунд, сколько показывается анимация (даже если загрузка быстрая). */
	public static float minSeconds() {
		LiveSplashModule m = INSTANCE;
		return m == null ? IntroFx.CHARGE_MIN : (float) m.minSeconds.get();
	}

	/** Играть ли звук в момент вспышки. */
	public static boolean chime() {
		LiveSplashModule m = INSTANCE;
		return m == null || m.chime.getBool();
	}
}
