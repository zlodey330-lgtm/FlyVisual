package com.flyvisual.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;

import static com.flyvisual.gui.QuadBatch.TWO_PI;
import static com.flyvisual.gui.QuadBatch.col;
import static com.flyvisual.gui.QuadBatch.lerp;

/**
 * Живая анимация загрузки: частицы с хвостами летят к центру и собираются в светящееся ядро, от него расходятся
 * волны света и растягивается полоса-блик. Пока что-то загружается, ядро «заряжается» и крутится по кругу
 * (вокруг него кольцо показывает прогресс загрузки). Когда загрузка закончилась, начинается взрыв: ядро разгорается,
 * вспышка, искры, и экран под анимацией проявляется.
 * <p>
 * Это один и тот же код для заставки вместо Mojang (запуск игры, смена ресурспака, F3+T) и для кнопки повтора в меню.
 * Всё рисуется пакетом и считается по реальному времени, поэтому плавно на любом FPS.
 */
public final class IntroFx {
	/** Раньше этого времени (в секундах от начала) взрыв не начнётся, чтобы анимацию успели увидеть. */
	public static final float CHARGE_MIN = 2.2f;
	/** Секунд от начала взрыва до вспышки. */
	public static final float BOOM = 0.8f;
	/** Секунд от вспышки до полностью открытого экрана. */
	public static final float FADE = 1.0f;

	private final QuadBatch fx = new QuadBatch();
	private boolean chime;
	private float fadeIn;

	/** За сколько секунд чёрная завеса появляется в начале (0 = сразу). */
	public void setFadeIn(float seconds) {
		this.fadeIn = seconds;
	}

	/** Сбросить состояние перед новым показом. */
	public void reset() {
		chime = false;
	}

	private static float clamp01(float x) {
		return x < 0.0f ? 0.0f : (x > 1.0f ? 1.0f : x);
	}

	private static float smooth(float e0, float e1, float x) {
		float t = clamp01((x - e0) / (e1 - e0));
		return t * t * (3.0f - 2.0f * t);
	}

	private static float ease(float x) {
		x = clamp01(x);
		float inv = 1.0f - x;
		return 1.0f - inv * inv * inv;
	}

	private static float easeInOut(float p) {
		return p < 0.5f ? 4.0f * p * p * p : 1.0f - (float) Math.pow(-2.0f * p + 2.0f, 3.0) / 2.0f;
	}

	private static float rnd(int i, int k) {
		float v = (float) Math.sin(i * 12.9898 + k * 78.233) * 43758.5453f;
		return v - (float) Math.floor(v);
	}

	/**
	 * Рисует кадр анимации.
	 *
	 * @param ti       секунд с начала анимации
	 * @param trigger  момент начала взрыва (секунды от начала); Float.MAX_VALUE, пока загрузка ещё идёт
	 * @param s        масштаб интерфейса
	 * @param progress прогресс загрузки 0..1 (для кольца вокруг ядра)
	 */
	public void draw(MatrixStack m, int width, int height, float ti, float trigger, float s, float progress) {
		float cx = width / 2.0f;
		float cy = height / 2.0f;
		boolean fired = trigger < 1.0e8f;
		float c = fired ? ti - trigger : -1.0f;       // секунд от начала взрыва
		float after = fired ? ti - (trigger + BOOM) : -1.0f; // секунд от вспышки
		float boom = fired ? smooth(0.0f, BOOM - 0.02f, c) : 0.0f;
		float fadeAll = 1.0f - smooth(0.0f, 0.35f, after);

		fx.begin(m);
		try {
			// Чёрная завеса: прячет экран под анимацией, после вспышки тает
			float veilIn = fadeIn > 0.0f ? smooth(0.0f, fadeIn, ti) : 1.0f;
			float veil = veilIn * (1.0f - smooth(0.0f, FADE, after));
			if (veil > 0.003f) fx.rect(0.0f, 0.0f, width, height, col(veil, 0x000000));

			fx.additive(true);

			// Звёзды на заднем плане
			float starIn = smooth(0.0f, 1.0f, ti) * fadeAll;
			if (starIn > 0.01f) {
				for (int i = 0; i < 80; i++) {
					float tw = 0.5f + 0.5f * (float) Math.sin(ti * (1.0f + rnd(i, 3) * 2.0f) + i);
					float a = starIn * (0.12f + 0.45f * tw) * rnd(i, 4);
					float x = rnd(i, 1) * width;
					float y = rnd(i, 2) * height;
					fx.rect(x, y, x + 1.3f, y + 1.3f, col(a, 0xD8C8FF));
				}
			}

			float rmax = Math.max(width, height) * 0.62f;

			// 1. Первая волна частиц: летят к центру по спирали
			float fadeP = 1.0f - smooth(1.6f, 2.2f, ti);
			if (fadeP > 0.01f) {
				float g = ti / 1.7f;
				for (int i = 0; i < 320; i++) {
					float r0 = rmax * (0.2f + 0.8f * rnd(i, 1));
					float a0 = rnd(i, 2) * TWO_PI;
					float delay = rnd(i, 3) * 0.30f;
					float rt = rnd(i, 4) * 5.0f * s;
					float p = easeInOut(clamp01((g - delay) / 0.70f));
					float p2 = easeInOut(clamp01((g - delay - 0.045f) / 0.70f));
					float al = (0.2f + 0.8f * p) * fadeP * (0.6f + 0.4f * rnd(i, 5));
					particle(cx, cy, r0, rt, a0, p, p2, al, 1.1f + rnd(i, 6) * 0.9f);
				}
			}

			// 1б. Непрерывный поток частиц, пока идёт загрузка
			float stream = smooth(1.3f, 2.3f, ti) * (1.0f - smooth(0.0f, 0.5f, c));
			if (stream > 0.01f) {
				for (int i = 0; i < 140; i++) {
					float cyc = (ti * 0.7f + rnd(i, 40)) % 1.0f;
					if (cyc < 0.05f) continue;
					float r0 = rmax * (0.25f + 0.75f * rnd(i, 41));
					float a0 = rnd(i, 42) * TWO_PI;
					float rt = rnd(i, 43) * 5.0f * s;
					float p = easeInOut(cyc);
					float p2 = easeInOut(cyc - 0.045f);
					float al = stream * (float) Math.sin(cyc * Math.PI) * 0.8f;
					particle(cx, cy, r0, rt, a0, p, p2, al, 1.0f + rnd(i, 44) * 0.8f);
				}
			}

			// 2. Светящееся ядро: разгорается, пульсирует, перед вспышкой резко растёт
			float coreIn = smooth(0.2f, 1.9f, ti);
			float inten = coreIn * (1.0f - smooth(0.05f, 0.45f, after));
			float prog = clamp01(progress);
			float rc = 0.0f;
			if (inten > 0.01f) {
				float pulse = ti > 1.7f ? 0.5f + 0.5f * (float) Math.sin(ti * 8.0f) : 0.0f;
				rc = (4.0f + 9.0f * coreIn + 2.2f * pulse + 5.0f * prog + 46.0f * boom * boom) * s;
				fx.glow(cx, cy, rc * 3.4f, col(inten * 0.30f, 0x8E63FF), col(0.0f, 0x8E63FF));
				fx.glow(cx, cy, rc * 1.8f, col(inten * 0.55f, 0xB896FF), col(0.0f, 0xB896FF));
				fx.glow(cx, cy, rc * 0.95f, col(inten * 0.95f, 0xFFFFFF), col(0.0f, 0xFFFFFF));
				fx.disc(cx, cy, rc * 0.40f, col(inten, 0xFFFFFF));
			}

			// 2б. Кольцо прогресса загрузки вокруг ядра
			float ringA = coreIn * (1.0f - boom) * fadeAll;
			if (ringA > 0.02f) {
				float rr = 34.0f * s;
				fx.ring(cx, cy, rr, rr + 1.2f * s, col(ringA * 0.14f, 0xB896FF));
				if (prog > 0.003f) {
					fx.arc(cx, cy, rr - 0.4f * s, rr + 1.8f * s, -1.5707964f, -1.5707964f + TWO_PI * prog,
							col(ringA * 0.35f, 0x8E63FF), col(ringA * 0.85f, 0xFFFFFF));
				}
			}

			// 3. Волны света расходятся от ядра, пока заряжается
			float waveFade = 1.0f - smooth(0.0f, 0.3f, after + 0.1f);
			if (ti > 1.5f && waveFade > 0.01f) {
				for (int k = 0; k < 3; k++) {
					float ph = (((ti - 1.5f) / 1.1f - k * 0.34f) % 1.0f + 1.0f) % 1.0f;
					float rr = (6.0f + 190.0f * ease(ph)) * s;
					float ra = (1.0f - ph) * 0.45f * waveFade * coreIn;
					if (ra <= 0.01f) continue;
					fx.ringBand(cx, cy, rr - 4.0f * s, rr, col(0.0f, 0xB896FF), col(ra, 0xB896FF));
					fx.ringBand(cx, cy, rr, rr + 4.0f * s, col(ra, 0xB896FF), col(0.0f, 0xB896FF));
				}
			}

			// 4. Полоса-блик через центр
			float fl = smooth(2.0f, 3.0f, ti) * (0.55f + 0.45f * boom) * (1.0f - smooth(0.0f, 0.5f, after));
			if (fl > 0.01f) {
				float len = width * (0.10f + 0.50f * fl);
				float th = 1.5f * s;
				int core = col(fl * 0.95f, 0xFFFFFF);
				int clear = col(0.0f, 0xB896FF);
				fx.gradH(cx - len, cy - th, cx, cy + th, clear, core);
				fx.gradH(cx, cy - th, cx + len, cy + th, core, clear);
				fx.lens(cx, cy, len, 16.0f * s, col(fl * 0.30f, 0xB896FF), clear);
				fx.lens(cx, cy, 13.0f * s, len * 0.22f, col(fl * 0.22f, 0xD8C8FF), clear);
			}

			// 5. После вспышки: взрыв искр с хвостами и ударные волны
			if (after > 0.0f && after < 1.6f) {
				for (int i = 0; i < 180; i++) {
					float ang = rnd(i, 31) * TWO_PI;
					float speed = (90.0f + 520.0f * rnd(i, 32)) * s;
					float life = 0.6f + 0.8f * rnd(i, 33);
					float u = clamp01(after / life);
					if (u >= 1.0f) continue;
					float u0 = clamp01((after - 0.07f) / life);
					float d1 = speed * life * 0.55f * ease(u);
					float d0 = speed * life * 0.55f * ease(u0);
					float al = (1.0f - u) * (1.0f - u);
					int cc = lerp(col(al, 0xC9B3FF), col(al, 0xFFFFFF), rnd(i, 34));
					fx.line(cx + (float) Math.cos(ang) * d0, cy + (float) Math.sin(ang) * d0,
							cx + (float) Math.cos(ang) * d1, cy + (float) Math.sin(ang) * d1,
							(1.0f + 1.6f * rnd(i, 35)) * Math.max(1.0f, s * 0.8f), cc & 0xFFFFFF, cc);
				}
				for (int k = 0; k < 2; k++) {
					float ph = (after - k * 0.12f) / 0.9f;
					if (ph <= 0.0f || ph >= 1.0f) continue;
					float rr = (10.0f + 520.0f * ease(ph)) * s;
					float ra = (1.0f - ph) * 0.5f;
					fx.ringBand(cx, cy, rr - 8.0f * s, rr, col(0.0f, 0xC9B3FF), col(ra, 0xC9B3FF));
					fx.ringBand(cx, cy, rr, rr + 8.0f * s, col(ra, 0xC9B3FF), col(0.0f, 0xC9B3FF));
				}
			}

			// 6. Вспышка
			fx.additive(false);
			float flash = fired ? Math.max(0.0f, 1.0f - Math.abs(after + 0.02f) / 0.30f) : 0.0f;
			if (flash > 0.0f) {
				fx.rect(0.0f, 0.0f, width, height, col(flash * 0.92f, 0xEDE6FF));
				if (!chime) {
					chime = true;
					try {
						MinecraftClient.getInstance().getSoundManager()
								.play(PositionedSoundInstance.master(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f));
					} catch (Throwable ignored) {
					}
				}
			}
		} finally {
			fx.flush();
		}
	}

	/** Одна частица с хвостом: летит от внешнего круга к центру по спирали. */
	private void particle(float cx, float cy, float r0, float rt, float a0, float p, float p2, float alpha, float thick) {
		if (alpha <= 0.004f) return;
		float r = r0 + (rt - r0) * p;
		float ang = a0 + p * 2.2f;
		float r2 = r0 + (rt - r0) * p2;
		float ang2 = a0 + p2 * 2.2f;
		int c = lerp(col(alpha, 0x8E63FF), col(alpha, 0xFFFFFF), p);
		fx.line(cx + (float) Math.cos(ang2) * r2, cy + (float) Math.sin(ang2) * r2,
				cx + (float) Math.cos(ang) * r, cy + (float) Math.sin(ang) * r,
				thick, c & 0xFFFFFF, c);
	}
}
