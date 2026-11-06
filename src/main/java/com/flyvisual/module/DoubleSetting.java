package com.flyvisual.module;

import com.flyvisual.config.FlyConfig;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Настройка модуля: ползунок, переключатель Вкл/Выкл или выбор из списка.
 * Значение хранится в конфиге.
 */
public class DoubleSetting {
	private final String name;
	private final String key;
	private final double min;
	private final double max;
	private final double step;
	private final boolean toggle;
	private final String[] labels;
	private double value;

	/** Для кнопок: действие при нажатии и текст справа (например, число выбранных предметов). */
	private Runnable action;
	private Supplier<String> valueText;

	public DoubleSetting(String moduleName, String name, double def, double min, double max, double step) {
		this(moduleName, name, def, min, max, step, false, null);
	}

	private DoubleSetting(String moduleName, String name, double def, double min, double max, double step,
						  boolean toggle, String[] labels) {
		this.name = name;
		this.key = moduleName + "." + name;
		this.min = min;
		this.max = max;
		this.step = step;
		this.toggle = toggle;
		this.labels = labels;

		Double saved = FlyConfig.get().settings.get(key);
		this.value = normalize(saved != null ? saved : def);
	}

	/** Переключатель Вкл/Выкл. */
	public static DoubleSetting createToggle(String moduleName, String name, boolean def) {
		return new DoubleSetting(moduleName, name, def ? 1.0 : 0.0, 0.0, 1.0, 1.0, true, null);
	}

	/** Выбор из списка (например, тип частиц). */
	public static DoubleSetting createChoice(String moduleName, String name, int defIndex, String... labels) {
		return new DoubleSetting(moduleName, name, defIndex, 0.0, labels.length - 1, 1.0, false, labels);
	}

	/** Кнопка в настройках модуля: по клику выполняет действие (например, открывает окно выбора). */
	public static DoubleSetting createButton(String moduleName, String name, Supplier<String> valueText, Runnable action) {
		DoubleSetting setting = new DoubleSetting(moduleName, name, 0.0, 0.0, 1.0, 1.0, false, null);
		setting.action = action;
		setting.valueText = valueText;
		return setting;
	}

	public boolean isButton() {
		return action != null;
	}

	public void press() {
		if (action != null) {
			action.run();
		}
	}

	private double normalize(double v) {
		double clamped = Math.max(min, Math.min(max, v));
		double stepped = Math.round((clamped - min) / step) * step + min;
		stepped = Math.max(min, Math.min(max, stepped));
		return Math.round(stepped * 10000.0) / 10000.0;
	}

	public String getName() {
		return name;
	}

	public double getMin() {
		return min;
	}

	public double getMax() {
		return max;
	}

	public boolean isToggle() {
		return toggle;
	}

	public double get() {
		return value;
	}

	public int getInt() {
		return (int) Math.round(value);
	}

	public float getFloat() {
		return (float) value;
	}

	public boolean getBool() {
		return value >= 0.5;
	}

	public void set(double v) {
		value = normalize(v);
		FlyConfig.get().settings.put(key, value);
	}

	public void flip() {
		set(getBool() ? 0.0 : 1.0);
	}

	/** Значение для показа в меню. */
	public String format() {
		if (valueText != null) {
			return valueText.get();
		}
		if (toggle) {
			return getBool() ? "Вкл" : "Выкл";
		}
		if (labels != null) {
			return labels[Math.max(0, Math.min(labels.length - 1, getInt()))];
		}
		int decimals = step >= 1.0 ? 0 : (step >= 0.1 ? 1 : 2);
		return String.format(Locale.ROOT, "%." + decimals + "f", value);
	}
}
