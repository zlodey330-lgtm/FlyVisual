package com.flyvisual.gui;

import net.minecraft.client.gui.DrawContext;

import static com.flyvisual.gui.QuadBatch.TWO_PI;
import static com.flyvisual.gui.QuadBatch.col;

/**
 * Живой фиолетовый фон: два мягких луча света, которые медленно качаются, пылинки (внутри луча они ярче),
 * мерцающие звёзды, большие размытые огоньки внизу и редкие падающие звёзды. Слои чуть сдвигаются за мышкой.
 * Всё рисуется пакетом, примерно за три вызова на кадр.
 */
final class BeamScene {
	private static final QuadBatch FX = new QuadBatch();

	private BeamScene() {
	}

	private static float rnd(int i, int k) {
		float v = (float) Math.sin(i * 12.9898 + k * 78.233) * 43758.5453f;
		return v - (float) Math.floor(v);
	}

	private static float clamp01(float x) {
		return x < 0.0f ? 0.0f : (x > 1.0f ? 1.0f : x);
	}

	static void draw(DrawContext ctx, int w, int h, float t, int mx, int my) {
		float k = h / 540.0f;
		float px = (mx - w * 0.5f) / w;
		float py = (my - h * 0.5f) / h;

		FX.begin(ctx.getMatrices());
		try {
			// Основа: тёмный градиент
			FX.gradV(0, 0, w, h, 0xFF07050F, 0xFF150A2B);

			FX.additive(true);

			// Мягкое свечение снизу
			FX.fan(w * 0.5f, h * 1.08f, w * 0.78f, h * 0.85f, 0.0f, TWO_PI, col(0.30f, 0x7A4DFF), col(0.0f, 0x7A4DFF));

			// Главный луч из левого верхнего угла
			float a1 = 0.52f + (float) Math.sin(t * 0.30f) * 0.035f;
			float dx1 = (float) Math.sin(a1);
			float dy1 = (float) Math.cos(a1);
			float ox = w * 0.06f - px * 14.0f * k;
			float oy = -h * 0.03f - py * 8.0f * k;
			float w0 = 24.0f * k;
			float w1 = w * 0.46f;
			float len = h * 1.5f;
			beam(ox, oy, dx1, dy1, len, w0, w1, 0xA774FF, 0.36f);

			// Второй, более тонкий луч
			float a2 = 0.30f - (float) Math.sin(t * 0.23f) * 0.03f;
			beam(w * 0.20f - px * 8.0f * k, -h * 0.05f, (float) Math.sin(a2), (float) Math.cos(a2),
					h * 1.4f, 10.0f * k, w * 0.20f, 0xD08CFF, 0.14f);

			// Источник света: сияние и блик
			float sx = ox + w * 0.02f;
			float sy = h * 0.025f;
			FX.glow(sx, sy, h * 0.30f, col(0.46f, 0xE2B8FF), col(0.0f, 0xE2B8FF));
			FX.glow(sx, sy, h * 0.09f, col(0.80f, 0xFFFFFF), col(0.0f, 0xFFFFFF));
			float flicker = 0.85f + 0.15f * (float) Math.sin(t * 2.1f);
			FX.star(sx, sy, h * 0.085f * flicker, col(0.75f, 0xFFFFFF));

			// Звёзды: мерцают и смещаются за мышкой
			for (int i = 0; i < 130; i++) {
				float depth = 0.3f + rnd(i, 3) * 0.9f;
				float x = rnd(i, 1) * w - px * 16.0f * k * depth;
				float y = rnd(i, 2) * h * 0.92f - py * 10.0f * k * depth;
				float tw = 0.5f + 0.5f * (float) Math.sin(t * (0.8f + rnd(i, 4) * 2.0f) + i);
				float a = (0.18f + 0.62f * tw) * (0.4f + 0.6f * rnd(i, 5));
				float size = (rnd(i, 6) > 0.92f ? 1.8f : 1.0f) * Math.max(1.0f, k);
				FX.rect(x, y, x + size, y + size, col(a, rnd(i, 7) > 0.5f ? 0xFFFFFF : 0xC9B3FF));
			}

			// Пылинки в воздухе: внутри луча ярче
			float beamSlope = dx1 / dy1;
			for (int i = 0; i < 110; i++) {
				float sp = 3.0f + rnd(i, 13) * 9.0f;
				float x = (rnd(i, 11) * w + t * sp * 0.6f * k) % w;
				float y = rnd(i, 12) * h - t * sp * 0.45f * k;
				y = ((y % h) + h) % h;
				float along = (y - oy) / dy1 / len;
				float inBeam = 0.0f;
				if (along > 0.0f && along < 1.0f) {
					float xc = ox + (y - oy) * beamSlope;
					float hw = (w0 + (w1 - w0) * along) * 0.5f;
					inBeam = clamp01(1.0f - Math.abs(x - xc) / (hw * 1.05f)) * (1.0f - along * 0.8f);
				}
				float tw = 0.5f + 0.5f * (float) Math.sin(t * (0.7f + rnd(i, 14) * 1.6f) + i * 1.7f);
				float a = (0.10f + 0.45f * tw * rnd(i, 15)) * (0.35f + 1.9f * inBeam);
				float size = (rnd(i, 16) > 0.9f ? 2.0f : 1.2f) * Math.max(1.0f, k * 0.9f);
				FX.rect(x, y, x + size, y + size, col(a, 0xE0D2FF));
			}

			// Большие размытые огоньки внизу
			for (int i = 0; i < 16; i++) {
				float x = (rnd(i, 21) * w + t * (2.0f + rnd(i, 22) * 4.0f) * k) % w;
				float y = h * (0.62f + 0.38f * rnd(i, 23)) - ((t * (3.0f + rnd(i, 24) * 5.0f) * k) % (h * 0.4f));
				float r = (3.0f + rnd(i, 25) * 8.0f) * k;
				float a = 0.10f + 0.22f * (0.5f + 0.5f * (float) Math.sin(t * 0.8f + i));
				FX.fan(x, y, r, r, 0.0f, TWO_PI, col(a, 0x9B6BFF), col(a * 0.35f, 0x9B6BFF));
			}

			// Падающая звезда раз в несколько секунд
			float phase = (t % 8.5f) / 0.9f;
			if (phase < 1.0f) {
				float hx = w * (0.78f - 0.42f * phase);
				float hy = h * (0.04f + 0.38f * phase);
				float fade = (float) Math.sin(phase * Math.PI);
				FX.line(hx, hy, hx + w * 0.11f, hy - h * 0.075f, 1.6f * Math.max(1.0f, k), col(fade * 0.95f, 0xFFFFFF), col(0.0f, 0xC9B3FF));
				FX.glow(hx, hy, 5.0f * k, col(fade * 0.8f, 0xFFFFFF), col(0.0f, 0xFFFFFF));
			}

			// Тёмная виньетка по краям
			FX.additive(false);
			FX.gradV(0, h * 0.72f, w, h, 0x00040210, 0x99040210);
			FX.gradV(0, 0, w, h * 0.16f, 0x55000000, 0x00000000);
		} finally {
			FX.flush();
		}
	}

	/** Луч света: расширяется и гаснет вдоль направления, по краям прозрачный, в середине ярче. */
	private static void beam(float ox, float oy, float dirX, float dirY, float len, float w0, float w1, int rgb, float alpha) {
		float nx = -dirY;
		float ny = dirX;
		int segs = 16;
		int edge = col(0.0f, rgb);
		for (int i = 0; i < segs; i++) {
			float t0 = (float) i / segs;
			float t1 = (float) (i + 1) / segs;
			float px0 = ox + dirX * len * t0;
			float py0 = oy + dirY * len * t0;
			float px1 = ox + dirX * len * t1;
			float py1 = oy + dirY * len * t1;
			float hw0 = (w0 + (w1 - w0) * t0) * 0.5f;
			float hw1 = (w0 + (w1 - w0) * t1) * 0.5f;
			int c0 = col(alpha * (float) Math.pow(1.0f - t0, 1.5f), rgb);
			int c1 = col(alpha * (float) Math.pow(1.0f - t1, 1.5f), rgb);
			FX.quad(px0 - nx * hw0, py0 - ny * hw0, edge, px0, py0, c0, px1, py1, c1, px1 - nx * hw1, py1 - ny * hw1, edge);
			FX.quad(px0, py0, c0, px0 + nx * hw0, py0 + ny * hw0, edge, px1 + nx * hw1, py1 + ny * hw1, edge, px1, py1, c1);
		}
	}
}
