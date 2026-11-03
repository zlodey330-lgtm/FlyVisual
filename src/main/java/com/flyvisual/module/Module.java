package com.flyvisual.module;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.hud.Notifications;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Базовый класс любой функции мода. Новая функция = новый класс-наследник
 * + одна строка в ModuleManager.
 */
public abstract class Module {
	private final String name;
	private final String description;
	private final List<DoubleSetting> settings = new ArrayList<>();
	private boolean enabled;

	protected Module(String name, String description) {
		this.name = name;
		this.description = description;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public List<DoubleSetting> getSettings() {
		return settings;
	}

	/** Добавляет ползунок в настройки модуля (правый клик по модулю в меню). */
	protected DoubleSetting addSetting(String settingName, double def, double min, double max, double step) {
		DoubleSetting setting = new DoubleSetting(name, settingName, def, min, max, step);
		settings.add(setting);
		return setting;
	}

	/** Добавляет переключатель Вкл/Выкл в настройки модуля. */
	protected DoubleSetting addToggle(String settingName, boolean def) {
		DoubleSetting setting = DoubleSetting.createToggle(name, settingName, def);
		settings.add(setting);
		return setting;
	}

	/** Добавляет выбор из списка (например, тип частиц). */
	protected DoubleSetting addChoice(String settingName, int defIndex, String... labels) {
		DoubleSetting setting = DoubleSetting.createChoice(name, settingName, defIndex, labels);
		settings.add(setting);
		return setting;
	}

	/** Включён ли модуль при первом запуске. */
	protected boolean enabledByDefault() {
		return false;
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	public void setEnabled(boolean value) {
		if (enabled == value) return;
		enabled = value;
		if (value) {
			onEnable();
		} else {
			onDisable();
		}
		FlyConfig.get().modules.put(name, value);
		FlyConfig.save();
		Notifications.push(Lang.module(name) + (value ? " включён" : " выключен"), value);
	}

	/** Загрузка состояния из конфига без уведомления. Работа начнётся в onTick. */
	void loadState(boolean value) {
		enabled = value;
	}

	protected MinecraftClient mc() {
		return MinecraftClient.getInstance();
	}

	public void onEnable() {
	}

	public void onDisable() {
	}

	/** Рисование поверх экрана для модулей, которые не являются HudModule (например, прицел). */
	public void renderOverlay(DrawContext ctx, float tickDelta) {
	}

	/** Вызывается каждый тик, пока модуль включён. */
	public void onTick(MinecraftClient client) {
	}
}
