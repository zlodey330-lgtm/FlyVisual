package com.flyvisual.util;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;

/**
 * Живое небо: к реалистичным облакам добавляет мерцающие звёзды, северное сияние,
 * падающие звёзды и плавно «дышащее» свечение у горизонта. Только цветные четырёхугольники, FPS почти не трогает.
 */
public final class LiveSky {
	private static final float R = 88.0f;
	private static final Draw3d DRAW = new Draw3d(true);
	private static final int STARS = 90;

	private LiveSky() {
	}

	private static float rnd(int i, int salt) {
		int h = i * 374761393 + salt * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFF) / 65535.0f;
	}

	private static float clamp(float v, float lo, float hi) {
		return v < lo ? lo : (v > hi ? hi : v);
	}

	private static void pt(Matrix4f m, float az, float el, float r, float g, float b, float a) {
		float ce = (float) Math.cos(el);
		DRAW.vertex(m, (float) (ce * Math.cos(az)) * R, (float) Math.sin(el) * R, (float) (ce * Math.sin(az)) * R, r, g, b, a);
	}

	public static void render(WorldRenderContext context, float speed) {
		ClientWorld world = context.world();
		MatrixStack matrices = context.matrixStack();
		if (world == null || matrices == null) return;
		if (world.getDimensionEffects().getSkyType() != DimensionEffects.SkyType.NORMAL) return;
		if (context.camera().getSubmersionType() != CameraSubmersionType.NONE) return;

		float td = context.tickDelta();
		float sy = (float) Math.cos(world.getSkyAngle(td) * Math.PI * 2.0);
		float rain = world.getRainGradient(td);
		float night = clamp(-sy * 3.0f + 0.3f, 0.0f, 1.0f) * (1.0f - rain);
		float t = (System.nanoTime() / 1.0e9f) * speed;
		Matrix4f m = matrices.peek().getPositionMatrix();

		// Мягкое «дыхание» у горизонта: тёплый-холодный отлив
		float breathe = 0.5f + 0.5f * (float) Math.sin(t * 0.35f);
		float day = clamp(sy * 3.0f + 0.2f, 0.0f, 1.0f) * (1.0f - rain * 0.7f);
		if (day > 0.02f) {
			int seg = 64;
			for (int i = 0; i < seg; i++) {
				float a0 = i * (float) Math.PI * 2.0f / seg;
				float a1 = (i + 1) * (float) Math.PI * 2.0f / seg;
				float k = 0.5f + 0.5f * (float) Math.sin(a0 * 2.0f + t * 0.5f);
				float r = 1.0f;
				float g = 0.75f + 0.2f * k;
				float b = 0.55f + 0.4f * breathe;
				float al = 0.16f * day * (0.6f + 0.4f * k);
				pt(m, a0, 0.0f, r, g, b, al);
				pt(m, a1, 0.0f, r, g, b, al);
				pt(m, a1, 0.35f, r, g, b, 0.0f);
				pt(m, a0, 0.35f, r, g, b, 0.0f);
			}
		}

		if (night > 0.02f) {
			// Мерцающие звёзды
			for (int i = 0; i < STARS; i++) {
				float az = rnd(i, 1) * (float) Math.PI * 2.0f;
				float el = 0.12f + rnd(i, 2) * 1.3f;
				float tw = 0.5f + 0.5f * (float) Math.sin(t * (1.0f + rnd(i, 3) * 3.0f) + i * 1.7f);
				float al = night * (0.25f + 0.75f * tw);
				float s = 0.012f + 0.012f * rnd(i, 4);
				float tint = rnd(i, 5);
				float r = 0.85f + 0.15f * tint;
				float g = 0.88f;
				float b = 1.0f - 0.2f * tint;
				pt(m, az - s, el - s, r, g, b, al);
				pt(m, az + s, el - s, r, g, b, al);
				pt(m, az + s, el + s, r, g, b, al);
				pt(m, az - s, el + s, r, g, b, al);
			}

			// Северное сияние: три колышущиеся занавеси
			for (int band = 0; band < 3; band++) {
				float centre = (float) Math.PI * (1.3f + 0.28f * band) + 0.2f * (float) Math.sin(t * 0.07f + band);
				int seg = 36;
				for (int i = 0; i < seg; i++) {
					float f0 = i / (float) seg;
					float f1 = (i + 1) / (float) seg;
					float a0 = centre + (f0 - 0.5f) * 1.5f;
					float a1 = centre + (f1 - 0.5f) * 1.5f;
					float w0 = (float) Math.sin(t * 0.8f + f0 * 7.0f + band * 2.0f);
					float w1 = (float) Math.sin(t * 0.8f + f1 * 7.0f + band * 2.0f);
					float low = 0.18f + 0.05f * band;
					float top0 = low + 0.28f + 0.14f * w0;
					float top1 = low + 0.28f + 0.14f * w1;
					float hue0 = 0.5f + 0.5f * (float) Math.sin(t * 0.2f + f0 * 3.0f + band);
					float hue1 = 0.5f + 0.5f * (float) Math.sin(t * 0.2f + f1 * 3.0f + band);
					float edge0 = (float) Math.sin(f0 * Math.PI);
					float edge1 = (float) Math.sin(f1 * Math.PI);
					float al0 = 0.55f * night * edge0 * (0.6f + 0.4f * w0);
					float al1 = 0.55f * night * edge1 * (0.6f + 0.4f * w1);
					pt(m, a0, low + 0.02f * w0, 0.2f + 0.5f * hue0, 1.0f - 0.35f * hue0, 0.45f + 0.5f * hue0, al0);
					pt(m, a1, low + 0.02f * w1, 0.2f + 0.5f * hue1, 1.0f - 0.35f * hue1, 0.45f + 0.5f * hue1, al1);
					pt(m, a1, top1, 0.4f + 0.5f * hue1, 0.5f, 1.0f, 0.0f);
					pt(m, a0, top0, 0.4f + 0.5f * hue0, 0.5f, 1.0f, 0.0f);
				}
			}

			// Падающая звезда раз в ~9 секунд
			float cyc = (t / 9.0f);
			int n = (int) cyc;
			float p = (cyc - n) * 9.0f / 1.2f;
			if (p < 1.0f) {
				float az0 = rnd(n, 9) * (float) Math.PI * 2.0f;
				float el0 = 0.6f + rnd(n, 10) * 0.6f;
				float az = az0 + p * 0.5f;
				float el = el0 - p * 0.35f;
				float al = night * (1.0f - p);
				for (int k = 0; k < 10; k++) {
					float f = k / 10.0f;
					float s = 0.008f * (1.0f - f * 0.6f);
					float aa = az - f * 0.18f;
					float ee = el + f * 0.12f;
					float ak = al * (1.0f - f);
					pt(m, aa - s, ee - s, 1.0f, 1.0f, 1.0f, ak);
					pt(m, aa + s, ee - s, 1.0f, 1.0f, 1.0f, ak);
					pt(m, aa + s, ee + s, 1.0f, 1.0f, 1.0f, ak);
					pt(m, aa - s, ee + s, 1.0f, 1.0f, 1.0f, ak);
				}
			}
		}
		DRAW.flush();
	}
}
