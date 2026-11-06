package com.flyvisual.module.impl;

import com.flyvisual.config.Theme;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.ColorUtil;
import com.flyvisual.util.SkyRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;

/**
 * Анимированное небо: цвет неба и облаков плавно перетекает по выбранному стилю.
 * Это лёгкий режим: меняется только цвет, поэтому нагрузки на FPS практически нет.
 * Подмена цвета происходит в ClientWorldMixin. Видно только тебе.
 */
public class SkyModule extends Module {
	private static final String[] STYLES = {"Радуга", "Аврора", "Красный закат", "Цвет темы", "Космос", "Реалистичное 3D", "Розовый рассвет", "Тропики", "Туман", "Грозовое", "Золотой час", "Лавандовое"};

	private final DoubleSetting style = addChoice("Sky style", 5, STYLES);
	private final DoubleSetting intensity = addSetting("Intensity", 100.0, 10.0, 100.0, 5.0);
	private final DoubleSetting speed = addSetting("Sky speed", 1.0, 0.1, 4.0, 0.1);
	private final DoubleSetting cloudAmount = addSetting("Cloud amount", 45.0, 0.0, 100.0, 5.0);
	private final DoubleSetting cloudSpeed = addSetting("Cloud speed", 1.0, 0.0, 5.0, 0.25);
	private final DoubleSetting tintClouds = addToggle("Tint clouds", true);

	public SkyModule() {
		super("Sky", "Smoothly animated sky colors");
		WorldRenderEvents.AFTER_SETUP.register(context -> {
			if (isRealistic()) {
				try {
					SkyRenderer.render(context, cloudAmount.getFloat(), cloudSpeed.getFloat());
				} catch (Throwable t) {
					// Ошибка в 3D-небе: переключаемся на простой стиль, чтобы игра не вылетала
					safeMode();
				}
			}
		});
	}

	/** Аварийный режим: при ошибке в 3D-небе переключаемся на простой стиль «Аврора». */
	private void safeMode() {
		style.set(1.0);
	}

	/** Включён режим «Реалистичное 3D»: настоящие облака и ореол солнца рисует SkyRenderer. */
	public boolean isRealistic() {
		return isEnabled() && style.getInt() == 5;
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
			case 6 -> {
				h = (float) (320.0 + 40.0 * wave);
				s = (float) (0.35 + 0.25 * wave);
				v = 0.95f;
			}
			case 7 -> {
				h = (float) (170.0 + 25.0 * wave);
				s = 0.6f;
				v = 0.9f;
			}
			case 8 -> {
				h = 210.0f;
				s = 0.08f;
				v = (float) (0.7 + 0.08 * wave);
			}
			case 9 -> {
				h = (float) (240.0 + 20.0 * wave);
				s = 0.35f;
				v = (float) (0.2 + 0.1 * wave);
			}
			case 10 -> {
				h = (float) (30.0 + 15.0 * wave);
				s = 0.75f;
				v = 0.95f;
			}
			case 11 -> {
				h = (float) (265.0 + 25.0 * wave);
				s = 0.4f;
				v = 0.85f;
			}
			case 4 -> {
				h = (float) (230.0 + 50.0 * wave);
				s = 0.8f;
				v = (float) (0.22 + 0.16 * wave);
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
		if (style.getInt() == 5) return vanilla;
		return mix(vanilla, custom(1.0));
	}

	public Vec3d applyClouds(Vec3d vanilla) {
		if (style.getInt() == 5) return vanilla;
		return tintClouds.getBool() ? mix(vanilla, custom(0.85)) : vanilla;
	}
}
