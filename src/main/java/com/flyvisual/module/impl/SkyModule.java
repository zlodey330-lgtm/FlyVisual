package com.flyvisual.module.impl;

import com.flyvisual.config.Theme;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.ColorUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;

/**
 * Анимированное небо: цвет неба и облаков плавно перетекает по выбранному стилю.
 * Это лёгкий режим: меняется только цвет, поэтому нагрузки на FPS практически нет.
 * Подмена цвета происходит в ClientWorldMixin. Видно только тебе.
 */
public class SkyModule extends Module {
	private static final String[] STYLES = {"Радуга", "Аврора", "Красный закат", "Цвет темы", "Космос", "Реалистичное"};

	private final DoubleSetting style = addChoice("Sky style", 5, STYLES);
	private final DoubleSetting intensity = addSetting("Intensity", 100.0, 10.0, 100.0, 5.0);
	private final DoubleSetting speed = addSetting("Sky speed", 1.0, 0.1, 4.0, 0.1);
	private final DoubleSetting tintClouds = addToggle("Tint clouds", true);

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

	private boolean realistic() {
		return style.getInt() == 5;
	}

	private static Vec3d lerp(Vec3d a, Vec3d b, double t) {
		return new Vec3d(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
	}

	private static double smooth(double edge0, double edge1, double x) {
		double t = MathHelper.clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}

	/**
	 * Реалистичное небо: цвет зависит от высоты солнца. Днём глубокая синева, на закате и рассвете
	 * тёплые розово-оранжевые тона, ночью тёмно-синий. Дождь приглушает цвета.
	 * Это только расчёт цвета (несколько умножений за кадр), поэтому на FPS не влияет.
	 */
	private Vec3d realisticColor(ClientWorld world, float tickDelta, boolean clouds) {
		double angle = world.getSkyAngle(tickDelta) * Math.PI * 2.0;
		double elevation = Math.cos(angle); // 1 = полдень, 0 = горизонт, -1 = полночь
		double day = smooth(-0.12, 0.35, elevation);
		double twilight = Math.max(0.0, 1.0 - Math.abs(elevation) / 0.28);
		twilight = twilight * twilight * (3.0 - 2.0 * twilight);
		double rain = world.getRainGradient(tickDelta);

		Vec3d night = clouds ? new Vec3d(0.07, 0.08, 0.13) : new Vec3d(0.015, 0.025, 0.085);
		Vec3d noon = clouds ? new Vec3d(1.0, 1.0, 1.0) : new Vec3d(0.20, 0.42, 0.86);
		Vec3d dusk = clouds ? new Vec3d(1.0, 0.58, 0.40) : new Vec3d(0.42, 0.30, 0.58);

		Vec3d result = lerp(night, noon, day);
		result = lerp(result, dusk, twilight * 0.75);

		double gray = 0.5 * (result.x + result.y + result.z);
		double dim = 1.0 - 0.45 * rain;
		return new Vec3d(
				lerp(result.x, gray, rain * 0.6) * dim,
				lerp(result.y, gray, rain * 0.6) * dim,
				lerp(result.z, gray, rain * 0.6) * dim);
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	public Vec3d applySky(Vec3d vanilla, ClientWorld world, float tickDelta) {
		if (realistic()) {
			return mix(vanilla, realisticColor(world, tickDelta, false));
		}
		return mix(vanilla, custom(1.0));
	}

	public Vec3d applyClouds(Vec3d vanilla, ClientWorld world, float tickDelta) {
		if (!tintClouds.getBool()) return vanilla;
		if (realistic()) {
			return mix(vanilla, realisticColor(world, tickDelta, true));
		}
		return mix(vanilla, custom(0.85));
	}
}
