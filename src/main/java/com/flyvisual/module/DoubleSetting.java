package com.flyvisual.module;

import com.flyvisual.config.FlyConfig;

import java.util.Locale;

/**
 * Настройка модуля: ползунок или переключатель Вкл/Выкл. Значение хранится в конфиге.
 */
public class DoubleSetting {
	private final String name;
	private final String key;
	private final double min;
	private final double max;
	private final double step;
	private final boolean toggle;
	private double value;

	public DoubleSetting(String moduleName, String name, double def, double min, double max, double step) {
		this(moduleName, name, def, min, max, step, false);
	}

	private DoubleSetting(String moduleName, String name, double def, double min, double max, double step, boolean toggle) {
		this.name = name;
		this.key = moduleName + "." + name;
		this.min = min;
		this.max = max;
		this.step = step;
		this.toggle = toggle;

		Double saved = FlyConfig.get().settings.get(key);
		this.value = normalize(saved != null ? saved : def);
	}

	/** Переключатель Вкл/Выкл. */
	public static DoubleSetting createToggle(String moduleName, String name, boolean def) {
		return new DoubleSetting(moduleName, name, def ? 1.0 : 0.0, 0.0, 1.0, 1.0, true);
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
		if (toggle) {
			return getBool() ? "On" : "Off";
		}
		int decimals = step >= 1.0 ? 0 : (step >= 0.1 ? 1 : 2);
		return String.format(Locale.ROOT, "%." + decimals + "f", value);
	}
}
