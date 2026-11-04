package com.flyvisual.module.impl;

import com.flyvisual.config.Theme;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.ColorUtil;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;

/**
 * Анимированное небо: цвет неба и облаков плавно перетекает по выбранному стилю.
 * Это лёгкий режим: меняется только цвет, поэтому нагрузки на FPS практически нет.
 * Подмена цвета происходит в ClientWorldMixin. Видно только тебе.
 */
public class SkyModule extends Module {
	private static final String[] STYLES = {
			"Радуга", "Аврора", "Красный закат", "Цвет темы", "Космос",
			"Свой цвет", "Океан", "Лава", "Сакура", "Неон", "Лес", "Золотой рассвет",
			"Лёд", "Фиолетовый туман", "Кровавая луна", "Мята", "Сумерки", "Тёмная ночь", "Персик"
	};

	private final DoubleSetting style = addChoice("Sky style", 1, STYLES);
	private final DoubleSetting intensity = addSetting("Intensity", 100.0, 10.0, 100.0, 5.0);
	private final DoubleSetting speed = addSetting("Sky speed", 1.0, 0.1, 4.0, 0.1);
	private final DoubleSetting tintClouds = addToggle("Tint clouds", true);
	private final DoubleSetting customHue = addSetting("Color (hue)", 200.0, 0.0, 355.0, 5.0);

	public SkyModule() {
		super("Sky", "Smoothly animated sky colors");
	}

	/** Цвет стиля в данный момент, brightness = множитель яркости. */
	private Vec3d custom(double brightness) {
		double t = System.nanoTime() / 1.0e9 * speed.get() * 0.15;
		double wave = 0.5 + 0.5 * Math.sin(t * 2.0);

		float h;
		float s;
		float v;
		switch (style.getInt()) {
			case 0 -> {
				h = (float) ((t * 60.0) % 360.0);
				s = 0.55f;
				v = 0.95f;
			}
			case 2 -> {
				h = (float) ((355.0 + 45.0 * wave) % 360.0);
				s = 0.85f;
				v = (float) (0.55 + 0.35 * wave);
			}
			case 3 -> {
				int accent = Theme.accent();
				float[] hsb = Color.RGBtoHSB((accent >> 16) & 0xFF, (accent >> 8) & 0xFF, accent & 0xFF, null);
				h = hsb[0] * 360.0f;
				s = 0.85f;
				v = (float) (0.5 + 0.35 * wave);
			}
			case 4 -> {
				h = (float) (230.0 + 50.0 * wave);
				s = 0.8f;
				v = (float) (0.22 + 0.16 * wave);
			}
			case 5 -> {
				// Свой цвет: оттенок из ползунка «Цвет», слегка дышит по яркости
				h = customHue.getFloat();
				s = 0.75f;
				v = (float) (0.7 + 0.2 * wave);
			}
			case 6 -> {
				// Океан: бирюза и глубокая синева
				h = (float) (185.0 + 30.0 * wave);
				s = 0.8f;
				v = (float) (0.45 + 0.3 * wave);
			}
			case 7 -> {
				// Лава: красно-оранжевое пламя
				h = (float) (5.0 + 22.0 * wave);
				s = 0.95f;
				v = (float) (0.5 + 0.4 * (0.5 + 0.5 * Math.sin(t * 4.3)));
			}
			case 8 -> {
				// Сакура: нежно-розовый
				h = (float) (330.0 + 20.0 * wave);
				s = (float) (0.3 + 0.2 * wave);
				v = 0.98f;
			}
			case 9 -> {
				// Неон: быстрые яркие переливы розового, голубого и фиолетового
				h = (float) (270.0 + 80.0 * Math.sin(t * 2.6));
				if (h < 0.0f) h += 360.0f;
				s = 1.0f;
				v = (float) (0.75 + 0.25 * wave);
			}
			case 10 -> {
				// Лес: зелёные тона
				h = (float) (105.0 + 35.0 * wave);
				s = 0.7f;
				v = (float) (0.35 + 0.3 * wave);
			}
			case 11 -> {
				// Золотой рассвет: от янтарного к розово-золотому
				h = (float) (25.0 + 30.0 * wave);
				s = (float) (0.55 + 0.3 * (1.0 - wave));
				v = (float) (0.8 + 0.18 * wave);
			}
			case 12 -> {
				// Лёд: холодный светло-голубой
				h = (float) (190.0 + 20.0 * wave);
				s = (float) (0.25 + 0.25 * wave);
				v = 0.97f;
			}
			case 13 -> {
				// Фиолетовый туман
				h = (float) (265.0 + 30.0 * wave);
				s = 0.55f;
				v = (float) (0.4 + 0.3 * wave);
			}
			case 14 -> {
				// Кровавая луна: тёмный багровый
				h = (float) (352.0 + 8.0 * wave);
				s = 0.95f;
				v = (float) (0.25 + 0.3 * wave);
			}
			case 15 -> {
				// Мята
				h = (float) (150.0 + 25.0 * wave);
				s = 0.4f;
				v = 0.95f;
			}
			case 16 -> {
				// Сумерки: от синего к оранжевому у горизонта
				h = (float) ((wave < 0.5 ? 235.0 : 15.0) + 10.0 * wave);
				s = 0.7f;
				v = (float) (0.4 + 0.35 * wave);
			}
			case 17 -> {
				// Тёмная ночь: почти чёрное небо с лёгкой синевой
				h = 225.0f;
				s = 0.6f;
				v = (float) (0.08 + 0.05 * wave);
			}
			case 18 -> {
				// Персик
				h = (float) (12.0 + 14.0 * wave);
				s = (float) (0.45 + 0.15 * wave);
				v = 1.0f;
			}
			default -> {
				h = (float) (150.0 + 100.0 * wave);
				s = 0.7f;
				v = (float) (0.55 + 0.25 * (0.5 + 0.5 * Math.sin(t * 3.1)));
			}
		}

		int rgb = ColorUtil.hsv(h, s, (float) Math.min(1.0, v * brightness));
		return new Vec3d(ColorUtil.red(rgb), ColorUtil.green(rgb), ColorUtil.blue(rgb));
	}

	private Vec3d mix(Vec3d vanilla, Vec3d custom) {
		double k = intensity.get() / 100.0;
		return new Vec3d(
				vanilla.x + (custom.x - vanilla.x) * k,
				vanilla.y + (custom.y - vanilla.y) * k,
				vanilla.z + (custom.z - vanilla.z) * k);
	}

	public Vec3d applySky(Vec3d vanilla) {
		return mix(vanilla, custom(1.0));
	}

	public Vec3d applyClouds(Vec3d vanilla) {
		return tintClouds.getBool() ? mix(vanilla, custom(0.85)) : vanilla;
	}
}
