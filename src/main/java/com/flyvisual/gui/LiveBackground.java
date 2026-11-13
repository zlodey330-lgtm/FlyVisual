package com.flyvisual.gui;

import com.flyvisual.config.FlyConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import java.util.Random;

/**
 * Живой фон главного меню: твоя картинка, поверх неё медленное приближение и смещение за мышкой,
 * колыхание листвы и земли, лучи света, туман, падающие листья и искры.
 * Всё рисуется обычными прямоугольниками и кусками одной текстуры, поэтому почти не грузит FPS.
 */
public class LiveBackground {
	private static final Identifier TEXTURE = new Identifier("flyvisual", "textures/gui/title_clouds.png");
	private static final int TW = 1920;
	private static final int TH = 1080;

	private static final int LEAF_COUNT = 38;
	private static final int MOTE_COUNT = 46;

	private static LiveBackground shared;

	/** Один общий фон для главного меню и экранов поверх него: анимация не сбрасывается при переходах. */
	public static LiveBackground shared() {
		if (shared == null) {
			shared = new LiveBackground();
		}
		return shared;
	}

	private final Random random = new Random();
	private final long start = System.nanoTime();
	private long last = start;

	private float parallaxX;
	private float parallaxY;

	private Leaf[] leaves;
	private Mote[] motes;

	private static final class Leaf {
		float x, y, speed, sway, phase, rot, spin, alpha;
		int w, h, rgb;
	}

	private static final class Mote {
		float x, y, speed, phase, drift;
		int rgb;
	}

	public float seconds() {
		return (System.nanoTime() - start) / 1.0e9f;
	}

	private void setup(int w, int h) {
		leaves = new Leaf[LEAF_COUNT];
		int[] palette = {0x9E9E9E, 0xBDBDBD, 0x757575, 0xE0E0E0, 0x616161};
		for (int i = 0; i < leaves.length; i++) {
			Leaf l = new Leaf();
			l.x = random.nextFloat() * w;
			l.y = random.nextFloat() * h;
			l.speed = 14.0f + random.nextFloat() * 26.0f;
			l.sway = 0.6f + random.nextFloat() * 1.2f;
			l.phase = random.nextFloat() * 6.28f;
			l.rot = random.nextFloat() * 360.0f;
			l.spin = (random.nextFloat() - 0.5f) * 140.0f;
			l.alpha = 0.55f + random.nextFloat() * 0.4f;
			l.w = 1 + random.nextInt(3);
			l.h = random.nextInt(3) == 0 ? 2 : 1;
			l.rgb = palette[random.nextInt(palette.length)];
			leaves[i] = l;
		}

		motes = new Mote[MOTE_COUNT];
		for (int i = 0; i < motes.length; i++) {
			Mote m = new Mote();
			m.x = random.nextFloat() * w;
			m.y = random.nextFloat() * h;
			m.speed = 3.0f + random.nextFloat() * 8.0f;
			m.phase = random.nextFloat() * 6.28f;
			m.drift = (random.nextFloat() - 0.5f) * 6.0f;
			m.rgb = random.nextInt(3) == 0 ? 0xFFFFFF : 0xB0BEC5;
			motes[i] = m;
		}
	}

	/** Рисует весь фон. dim = true делает его темнее (для экранов поверх меню). */
	public void render(DrawContext ctx, int w, int h, int mouseX, int mouseY, boolean dim) {
		long now = System.nanoTime();
		float dt = Math.min((now - last) / 1.0e9f, 0.05f);
		last = now;
		float t = (now - start) / 1.0e9f;

		if (leaves == null) {
			setup(w, h);
		}

		FlyConfig cfg = FlyConfig.get();

		// Смещение за мышкой (плавное)
		float tx = w > 0 ? (float) mouseX / w - 0.5f : 0.0f;
		float ty = h > 0 ? (float) mouseY / h - 0.5f : 0.0f;
		float k = 1.0f - (float) Math.exp(-3.0f * dt);
		parallaxX += (tx - parallaxX) * k;
		parallaxY += (ty - parallaxY) * k;

		// Масштаб "чтобы картинка закрывала экран" и медленное приближение
		float zoom = 1.08f + 0.025f * (float) Math.sin(t * 0.12f);
		float s = Math.max(w / (float) TW, h / (float) TH) * zoom;
		float dw = TW * s;
		float dh = TH * s;
		float maxPanX = (dw - w) / 2.0f * 0.8f;
		float maxPanY = (dh - h) / 2.0f * 0.8f;
		float panX = clamp(-parallaxX * 2.0f * maxPanX * 0.7f + (float) Math.sin(t * 0.07f) * maxPanX * 0.3f, -maxPanX, maxPanX);
		float panY = clamp(-parallaxY * 2.0f * maxPanY * 0.7f + (float) Math.cos(t * 0.05f) * maxPanY * 0.3f, -maxPanY, maxPanY);

		MatrixStack ms = ctx.getMatrices();
		ms.push();
		ms.translate((w - dw) / 2.0f + panX, (h - dh) / 2.0f + panY, 0.0f);
		ms.scale(s, s, 1.0f);
		drawImage(ctx, t, false);
		// Второй слой тех же облаков медленно плывёт поверх: картинка "дышит"
		ctx.setShaderColor(1.0f, 1.0f, 1.0f, 0.30f);
		ms.push();
		ms.translate(TW / 2.0f + (float) Math.sin(t * 0.05f) * 70.0f, TH / 2.0f - 16.0f + (float) Math.cos(t * 0.04f) * 14.0f, 0.0f);
		ms.scale(1.2f, 1.2f, 1.0f);
		ms.translate(-TW / 2.0f, -TH / 2.0f, 0.0f);
		ctx.drawTexture(TEXTURE, 0, 0, 0.0f, 0.0f, TW, TH, TW, TH);
		ms.pop();
		ctx.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
		ms.pop();

		// Затемнение по краям, чтобы кнопки и логотип читались
		ctx.fill(0, 0, w, h, dim ? 0xA0000000 : 0x38000000);
		ctx.fillGradient(0, 0, w, h / 3, 0x80000000, 0x00000000);
		ctx.fillGradient(0, h * 2 / 3, w, h, 0x00000000, 0x99000000);

		if (cfg.titleParticles) {
			drawBeams(ctx, w, h, t);
			drawMist(ctx, w, h, t);
			drawParticles(ctx, w, h, t, dt);
		}
	}

	private static float clamp(float v, float min, float max) {
		return Math.max(min, Math.min(max, v));
	}

	/**
	 * Картинка кусками по горизонтали. Верх (листва) и низ (трава и листья на земле)
	 * слегка качаются волной, середина стоит на месте.
	 */
	private void drawImage(DrawContext ctx, float t, boolean wave) {
		if (!wave) {
			ctx.drawTexture(TEXTURE, 0, 0, 0.0f, 0.0f, TW, TH, TW, TH);
			return;
		}

		MatrixStack ms = ctx.getMatrices();
		final int topEnd = 320;
		final int botStart = 700;
		final int step = 8;

		for (int v = 0; v < topEnd; v += step) {
			float f = (topEnd - v) / (float) topEnd;
			float amp = 2.6f * f * f;
			drawSlice(ctx, ms, v, Math.min(step, topEnd - v), waveOffset(t, v, amp));
		}

		ctx.drawTexture(TEXTURE, 0, topEnd, 0.0f, (float) topEnd, TW, botStart - topEnd, TW, TH);

		for (int v = botStart; v < TH; v += step) {
			float f = (v - botStart) / (float) (TH - botStart);
			float amp = 5.0f * f;
			drawSlice(ctx, ms, v, Math.min(step, TH - v), waveOffset(t, v, amp));
		}
	}

	private static float waveOffset(float t, int v, float amp) {
		return amp * ((float) Math.sin(t * 1.1f + v * 0.018f) + 0.4f * (float) Math.sin(t * 2.3f + v * 0.04f));
	}

	private void drawSlice(DrawContext ctx, MatrixStack ms, int v, int height, float dx) {
		ms.push();
		ms.translate(dx, 0.0f, 0.0f);
		ctx.drawTexture(TEXTURE, 0, v, 0.0f, (float) v, TW, height, TW, TH);
		ms.pop();
	}

	// ---------- Лучи света ----------

	private void drawBeams(DrawContext ctx, int w, int h, float t) {
		float[] pos = {0.16f, 0.30f, 0.44f, 0.58f};
		float[] widths = {90.0f, 120.0f, 80.0f, 100.0f};
		MatrixStack ms = ctx.getMatrices();

		for (int i = 0; i < pos.length; i++) {
			float pulse = 0.55f + 0.45f * (float) Math.sin(t * 0.55f + i * 1.7f);
			float alpha = 0.085f * pulse;

			ms.push();
			ms.translate(w * pos[i], -30.0f, 0.0f);
			ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-32.0f));
			beam(ctx, widths[i] * (0.9f + 0.1f * pulse), h * 1.5f, 0xD0D6DC, alpha);
			ms.pop();
		}
	}

	/** Вертикальная полоса с мягкими краями (набор тонких полосок), тающая книзу. */
	private void beam(DrawContext ctx, float width, float length, int rgb, float alpha) {
		int n = 14;
		float sw = width / n;
		for (int i = 0; i < n; i++) {
			float e = (float) Math.sin(Math.PI * (i + 0.5) / n);
			int a = Math.round(alpha * e * e * 255.0f);
			if (a <= 0) continue;
			int x1 = Math.round(-width / 2.0f + i * sw);
			int x2 = Math.round(-width / 2.0f + (i + 1) * sw);
			ctx.fillGradient(x1, 0, x2, Math.round(length), (a << 24) | rgb, rgb);
		}
	}

	// ---------- Туман ----------

	private void drawMist(DrawContext ctx, int w, int h, float t) {
		float[] speed = {9.0f, -6.0f, 5.0f};
		float[] yFrac = {0.74f, 0.86f, 0.66f};
		float[] alpha = {0.10f, 0.08f, 0.06f};

		for (int i = 0; i < 3; i++) {
			float bw = w * 0.85f;
			float travel = w + bw;
			float cx = ((t * speed[i] + i * 237.0f) % travel + travel) % travel - bw / 2.0f;
			softBand(ctx, cx, h * yFrac[i], bw, 70.0f, 0xB4B9C4, alpha[i]);
		}
	}

	private void softBand(DrawContext ctx, float cx, float cy, float width, float height, int rgb, float alpha) {
		int n = 22;
		float sw = width / n;
		int y1 = Math.round(cy - height / 2.0f);
		int ym = Math.round(cy);
		int y2 = Math.round(cy + height / 2.0f);
		for (int i = 0; i < n; i++) {
			float e = (float) Math.sin(Math.PI * (i + 0.5) / n);
			int a = Math.round(alpha * e * e * 255.0f);
			if (a <= 0) continue;
			int x1 = Math.round(cx - width / 2.0f + i * sw);
			int x2 = Math.round(cx - width / 2.0f + (i + 1) * sw) + 1;
			ctx.fillGradient(x1, y1, x2, ym, rgb, (a << 24) | rgb);
			ctx.fillGradient(x1, ym, x2, y2, (a << 24) | rgb, rgb);
		}
	}

	// ---------- Листья и искры ----------

	private void drawParticles(DrawContext ctx, int w, int h, float t, float dt) {
		MatrixStack ms = ctx.getMatrices();

		for (Mote m : motes) {
			m.y -= m.speed * dt;
			m.x += m.drift * dt;
			if (m.y < -4.0f) {
				m.y = h + 4.0f;
				m.x = random.nextFloat() * w;
			}
			float twinkle = 0.5f + 0.5f * (float) Math.sin(t * 2.2f + m.phase);
			int a = Math.round(twinkle * 0.7f * 255.0f);
			if (a < 12) continue;
			int x = Math.round(m.x);
			int y = Math.round(m.y);
			ctx.fill(x, y, x + 1, y + 1, (a << 24) | m.rgb);
		}

		for (Leaf l : leaves) {
			l.y += l.speed * dt;
			l.x += (10.0f + (float) Math.sin(t * l.sway + l.phase) * 18.0f) * dt;
			l.rot += l.spin * dt;
			if (l.y > h + 8.0f) {
				l.y = -8.0f;
				l.x = random.nextFloat() * w;
			}
			if (l.x > w + 12.0f) l.x = -12.0f;
			if (l.x < -12.0f) l.x = w + 12.0f;

			int a = Math.round(l.alpha * 255.0f);
			ms.push();
			ms.translate(l.x, l.y, 0.0f);
			ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(l.rot));
			ctx.fill(-l.w, -l.h, l.w, l.h, (a << 24) | l.rgb);
			ms.pop();
		}
	}
}
