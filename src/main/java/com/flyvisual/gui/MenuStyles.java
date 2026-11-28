package com.flyvisual.gui;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.config.Theme;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.Random;

/**
 * Стили оформления главного меню (Right Shift).
 * <p>
 * «Классика» это прежнее оформление: цвета берутся из Theme, как и раньше, ничего не заменено.
 * Остальные стили меняют палитру, фон, декор и анимации. Переключение плавное: у каждого стиля есть «вес»,
 * цвета смешиваются по весам, а декор проявляется и исчезает через прозрачность.
 * Все анимации считаются от времени и рисуются обычными прямоугольниками, текстуры не нужны.
 */
public final class MenuStyles {
	public static final int COUNT = 7;
	public static final String[] NAMES = {"Классика", "Blood", "Sakura", "Abyss", "Matrix", "Neon", "Galaxy"};
	public static final String[] DESCRIPTIONS = {
			"Классика: прежнее оформление меню, цвета берутся из «Тема меню» и выбранного цвета",
			"Blood: кровавая тьма, пульс, капли и глаз в углу, который следит за курсором",
			"Sakura: розовая Япония, падающие лепестки и цветущая ветка",
			"Abyss: глубокое море, пузыри, лучи света и медуза",
			"Matrix: зелёный дождь из символов и линия развёртки",
			"Neon: синтвейв, закат с сеткой и неоновые рамки",
			"Galaxy: звёздное небо, туманности и падающие звёзды"
	};

	private static final int CLASSIC = 0;
	private static final int BLOOD = 1;
	private static final int SAKURA = 2;
	private static final int ABYSS = 3;
	private static final int MATRIX = 4;
	private static final int NEON = 5;
	private static final int GALAXY = 6;

	// Поля палитры
	private static final int P_PANEL = 0;
	private static final int P_TILE = 1;
	private static final int P_HOVER = 2;
	private static final int P_PANE = 3;
	private static final int P_OFF = 4;
	private static final int P_ACCENT = 5;
	private static final int P_TEXT = 6;
	private static final int P_DIM = 7;
	private static final int P_BG = 8;
	private static final int P_LINE = 9;
	private static final int P_BTN = 10;
	private static final int P_BTNH = 11;
	private static final int P_TEXTOFF = 12;
	private static final int P_DISABLED = 13;
	private static final int P_ACCENT2 = 14;
	private static final int FIELDS = 15;

	/** Палитры стилей. Строка 0 (Классика) каждый кадр заполняется из Theme. */
	private static final int[][] STYLE = new int[COUNT][FIELDS];
	/** Текущая смешанная палитра. */
	private static final int[] pal = new int[FIELDS];
	/** Веса стилей (сумма всегда 1). */
	private static final float[] w = new float[COUNT];

	static {
		//                 панель      плитка      наведение   правая      выключ.     акцент      текст       тусклый     фон экрана  линия       кнопка      кнопка+     выкл.текст  затемнение  акцент 2
		set(BLOOD, 0xF0120405, 0xFF1F0809, 0xFF35100F, 0xFF180607, 0xFF4F2024, 0xFFE0242B, 0xFFF6E8E8, 0xFFB38888, 0xBB140000, 0x44FF3030, 0xFF2C0D10, 0xFF481519, 0xFFA87C7C, 0x99080102, 0xFFFF6A3A);
		set(SAKURA, 0xF03A1F2D, 0xFF542A3E, 0xFF6F3752, 0xFF47232F, 0xFF7A4A5E, 0xFFFF8FB8, 0xFFFFF1F6, 0xFFE6B8CA, 0xAA2A0A1A, 0x44FFC0D8, 0xFF5E2F45, 0xFF7C405C, 0xFFDDAABD, 0x99260E1A, 0xFFFFD0E0);
		set(ABYSS, 0xF006151F, 0xFF0B2634, 0xFF133C52, 0xFF08202C, 0xFF1E4A60, 0xFF2EE6D6, 0xFFE6FBFF, 0xFF8EBCC9, 0xB0001018, 0x442EE6D6, 0xFF0E3042, 0xFF164A62, 0xFF80ACBA, 0x99021019, 0xFF7FD8FF);
		set(MATRIX, 0xF0020A04, 0xFF05140A, 0xFF0B2A16, 0xFF04100A, 0xFF16391F, 0xFF2BFF6A, 0xFFCBFFDB, 0xFF6FBB86, 0xB0000A02, 0x442BFF6A, 0xFF06200F, 0xFF0C3A1B, 0xFF5EA676, 0x99020A04, 0xFFA8FFC4);
		set(NEON, 0xF00E0820, 0xFF180E34, 0xFF26165C, 0xFF120B2B, 0xFF3C2C6C, 0xFFFF3DDC, 0xFFF5ECFF, 0xFFAA97D8, 0xB0080018, 0x44FF3DDC, 0xFF201244, 0xFF301A66, 0xFF9C8AD0, 0x990A0620, 0xFF2DE2FF);
		set(GALAXY, 0xF0070A1C, 0xFF0E1331, 0xFF192153, 0xFF0A0E29, 0xFF2C3567, 0xFF8E9BFF, 0xFFEBEFFF, 0xFF9CA5D8, 0xB0020410, 0x448E9BFF, 0xFF141A40, 0xFF1F2858, 0xFF8E97CA, 0x99050818, 0xFFC58CFF);
		// Классика и стартовое состояние
		fillClassic();
		w[CLASSIC] = 1.0f;
		System.arraycopy(STYLE[CLASSIC], 0, pal, 0, FIELDS);
	}

	private static void set(int style, int... v) {
		System.arraycopy(v, 0, STYLE[style], 0, FIELDS);
	}

	private MenuStyles() {
	}

	// ---------- Выбор стиля ----------

	public static int current() {
		return Math.max(0, Math.min(COUNT - 1, FlyConfig.get().menuStyle));
	}

	public static void select(int i) {
		int n = Math.max(0, Math.min(COUNT - 1, i));
		if (FlyConfig.get().menuStyle != n) {
			FlyConfig.get().menuStyle = n;
			FlyConfig.save();
		}
	}

	// ---------- Цвета для меню ----------

	public static int panel() {
		return pal[P_PANEL];
	}

	public static int tile() {
		return pal[P_TILE];
	}

	public static int tileHover() {
		return pal[P_HOVER];
	}

	public static int pane() {
		return pal[P_PANE];
	}

	public static int off() {
		return pal[P_OFF];
	}

	public static int accent() {
		return pal[P_ACCENT];
	}

	public static int accent2() {
		return pal[P_ACCENT2];
	}

	public static int text() {
		return pal[P_TEXT];
	}

	public static int textDim() {
		return pal[P_DIM];
	}

	public static int textOff() {
		return pal[P_TEXTOFF];
	}

	public static int line() {
		return pal[P_LINE];
	}

	public static int btn() {
		return pal[P_BTN];
	}

	public static int btnHover() {
		return pal[P_BTNH];
	}

	public static int disabled() {
		return pal[P_DISABLED];
	}

	private static void fillClassic() {
		int[] c = STYLE[CLASSIC];
		c[P_PANEL] = Theme.panelCol();
		c[P_TILE] = Theme.tileCol();
		c[P_HOVER] = Theme.tileHoverCol();
		c[P_PANE] = Theme.paneCol();
		c[P_OFF] = Theme.offCol();
		c[P_ACCENT] = Theme.accent();
		c[P_TEXT] = Theme.TEXT;
		c[P_DIM] = Theme.TEXT_DIM;
		c[P_BG] = 0x99000000;
		c[P_LINE] = 0x22FFFFFF;
		c[P_BTN] = 0xFF2A2A2E;
		c[P_BTNH] = 0xFF3A3A3E;
		c[P_TEXTOFF] = 0xFFB8B8B8;
		c[P_DISABLED] = 0x99141418;
		c[P_ACCENT2] = Theme.accent();
	}

	// ---------- Состояние и анимации ----------

	private static final long T0 = System.nanoTime();
	private static float t;
	private static float beatV;
	private static float blinkT = -1.0f;
	private static float nextBlink = 2.0f;
	private static float screenH = 270.0f;
	private static float gridOff;

	private static final Random R = new Random(20261126L);
	private static final int PET = 46;
	private static final int EMB = 44;
	private static final int BUB = 36;
	private static final int STAR = 120;
	private static final int COLS = 190;
	private static final int CELL = 10;

	private static final float[] petX = new float[PET];
	private static final float[] petY = new float[PET];
	private static final float[] petS = new float[PET];
	private static final float[] petR = new float[PET];
	private static final float[] petRv = new float[PET];
	private static final float[] petPh = new float[PET];
	private static final float[] petV = new float[PET];

	private static final float[] emX = new float[EMB];
	private static final float[] emY = new float[EMB];
	private static final float[] emS = new float[EMB];
	private static final float[] emV = new float[EMB];
	private static final float[] emPh = new float[EMB];

	private static final float[] buX = new float[BUB];
	private static final float[] buY = new float[BUB];
	private static final float[] buS = new float[BUB];
	private static final float[] buV = new float[BUB];
	private static final float[] buPh = new float[BUB];

	private static final float[] stX = new float[STAR];
	private static final float[] stY = new float[STAR];
	private static final float[] stS = new float[STAR];
	private static final float[] stPh = new float[STAR];
	private static final float[] stV = new float[STAR];

	private static final float[] cY = new float[COLS];
	private static final float[] cV = new float[COLS];
	private static final float[] cL = new float[COLS];

	private static final float[] ssX = new float[2];
	private static final float[] ssY = new float[2];
	private static final float[] ssVX = new float[2];
	private static final float[] ssVY = new float[2];
	private static final float[] ssLife = new float[2];
	private static float ssTimer = 2.0f;

	static {
		for (int i = 0; i < PET; i++) {
			petX[i] = R.nextFloat();
			petY[i] = R.nextFloat();
			petS[i] = 2.6f + R.nextFloat() * 3.4f;
			petR[i] = R.nextFloat() * 6.28f;
			petRv[i] = (R.nextBoolean() ? 1 : -1) * (0.6f + R.nextFloat() * 1.6f);
			petPh[i] = R.nextFloat() * 6.28f;
			petV[i] = 0.035f + R.nextFloat() * 0.06f;
		}
		for (int i = 0; i < EMB; i++) {
			emX[i] = R.nextFloat();
			emY[i] = R.nextFloat();
			emS[i] = 1.0f + R.nextFloat() * 1.8f;
			emV[i] = 0.02f + R.nextFloat() * 0.05f;
			emPh[i] = R.nextFloat() * 6.28f;
		}
		for (int i = 0; i < BUB; i++) {
			buX[i] = R.nextFloat();
			buY[i] = R.nextFloat();
			buS[i] = 1.5f + R.nextFloat() * 4.5f;
			buV[i] = 0.025f + R.nextFloat() * 0.06f;
			buPh[i] = R.nextFloat() * 6.28f;
		}
		for (int i = 0; i < STAR; i++) {
			stX[i] = R.nextFloat();
			stY[i] = R.nextFloat();
			stS[i] = R.nextFloat() < 0.12f ? 2.0f : 1.0f;
			stPh[i] = R.nextFloat() * 6.28f;
			stV[i] = 0.4f + R.nextFloat() * 1.8f;
		}
		for (int i = 0; i < COLS; i++) {
			cY[i] = -R.nextFloat() * 30.0f;
			cV[i] = 5.0f + R.nextFloat() * 11.0f;
			cL[i] = 7.0f + R.nextFloat() * 8.0f;
		}
	}

	// ---------- Свой цвет: все стили перекрашиваются под выбранный акцент ----------

	/** Матрицы подкраски стилей (null: цвета стиля без изменений). */
	private static final float[][] TM = new float[COUNT][];
	/** Палитры стилей после подкраски. */
	private static final int[][] STYLE_T = new int[COUNT][FIELDS];
	private static int tintKey = -1;

	/** Матрица поворота оттенка на theta градусов вместе с изменением насыщенности. */
	private static float[] hueSat(float theta, float satK) {
		float rad = theta * 0.017453292f;
		float c = (float) Math.cos(rad);
		float sn = (float) Math.sin(rad);
		float k = (1.0f - c) / 3.0f;
		float q = sn * 0.57735027f;
		float[] rot = {c + k, k - q, k + q, k + q, c + k, k - q, k - q, k + q, c + k};
		float[] lum = {0.299f, 0.587f, 0.114f};
		float[] sat = new float[9];
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				sat[i * 3 + j] = (1.0f - satK) * lum[j] + (i == j ? satK : 0.0f);
			}
		}
		float[] out = new float[9];
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				float sum = 0.0f;
				for (int m = 0; m < 3; m++) sum += sat[i * 3 + m] * rot[m * 3 + j];
				out[i * 3 + j] = sum;
			}
		}
		return out;
	}

	private static int applyM(float[] m, int argb) {
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		int nr = clampByte(m[0] * r + m[1] * g + m[2] * b);
		int ng = clampByte(m[3] * r + m[4] * g + m[5] * b);
		int nb = clampByte(m[6] * r + m[7] * g + m[8] * b);
		return (argb & 0xFF000000) | (nr << 16) | (ng << 8) | nb;
	}

	private static float hueOf(int rgb) {
		return Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null)[0];
	}

	private static float wrapDeg(float d) {
		while (d > 180.0f) d -= 360.0f;
		while (d < -180.0f) d += 360.0f;
		return d;
	}

	/** Пересчитывает палитры, только если цвет или переключатель изменились. */
	private static void refreshTint() {
		FlyConfig cfg = FlyConfig.get();
		int rgb = cfg.accentColor & 0xFFFFFF;
		int key = cfg.menuTint ? (0x1000000 | rgb) : 0;
		if (key == tintKey) return;
		tintKey = key;
		float[] ht = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
		for (int i = 1; i < COUNT; i++) {
			if (key == 0) {
				TM[i] = null;
				System.arraycopy(STYLE[i], 0, STYLE_T[i], 0, FIELDS);
				continue;
			}
			int acc = STYLE[i][P_ACCENT];
			float[] ha = Color.RGBtoHSB((acc >> 16) & 0xFF, (acc >> 8) & 0xFF, acc & 0xFF, null);
			float satK = ha[1] < 0.05f ? 1.0f : Math.min(1.25f, ht[1] / ha[1]);
			float theta = 0.0f;
			if (ht[1] > 0.08f && ha[1] > 0.05f) {
				theta = wrapDeg((ht[0] - ha[0]) * 360.0f);
				for (int it = 0; it < 3; it++) {
					float h = hueOf(applyM(hueSat(theta, satK), acc));
					theta += wrapDeg((ht[0] - h) * 360.0f);
				}
			}
			float[] m = hueSat(theta, satK);
			TM[i] = m;
			for (int f = 0; f < FIELDS; f++) {
				int c = STYLE[i][f];
				float[] hc = Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
				if (hc[2] < 0.5f && ht[1] > 0.08f) {
					// тёмные поверхности (панель, плитки, фон) получают оттенок выбранного цвета напрямую
					STYLE_T[i][f] = (c & 0xFF000000) | (Color.HSBtoRGB(ht[0], Math.min(1.0f, hc[1] * satK), hc[2]) & 0xFFFFFF);
				} else {
					STYLE_T[i][f] = applyM(m, c);
				}
			}
			STYLE_T[i][P_ACCENT] = 0xFF000000 | (Color.HSBtoRGB(ht[0], ht[1], Math.max(ht[2], 0.55f)) & 0xFFFFFF);
			// второй акцент: соседний оттенок, чтобы пара смотрелась согласованно
			STYLE_T[i][P_ACCENT2] = 0xFF000000 | (Color.HSBtoRGB((ht[0] + 0.08f) % 1.0f, Math.max(0.2f, ht[1] * 0.7f), 1.0f) & 0xFFFFFF);
		}
	}

	/** Включает облегчённый режим: меньше частиц и сегментов (для слабых устройств). */
	static void setLite(boolean on) {
		lite = on;
		G.setLite(on);
	}

	private static boolean lite;

	private static int liteN(int n) {
		return lite ? Math.max(1, n * 55 / 100) : n;
	}

	static MenuGfx gfx() {
		return G;
	}

	/** Вызывается каждый кадр меню: сдвигает веса стилей, смешивает палитру, двигает частицы только видимых стилей. */
	static void update(float dt) {
		t = (System.nanoTime() - T0) / 1.0e9f;
		refreshTint();
		fillClassic();
		int target = current();
		float k = 1.0f - (float) Math.exp(-dt * 5.0f);
		for (int i = 0; i < COUNT; i++) {
			float tv = i == target ? 1.0f : 0.0f;
			if (w[i] != tv) {
				w[i] += (tv - w[i]) * k;
				if (Math.abs(tv - w[i]) < 0.003f) w[i] = tv;
			}
		}
		if (w[target] == 1.0f) {
			// обычный случай: один стиль, смешивать нечего
			System.arraycopy(target == CLASSIC ? STYLE[CLASSIC] : STYLE_T[target], 0, pal, 0, FIELDS);
		} else {
			for (int f = 0; f < FIELDS; f++) {
				float a = 0.0f;
				float r = 0.0f;
				float g = 0.0f;
				float b = 0.0f;
				for (int i = 0; i < COUNT; i++) {
					float wi = w[i];
					if (wi <= 0.0f) continue;
					int c = (i == CLASSIC ? STYLE[CLASSIC] : STYLE_T[i])[f];
					a += ((c >>> 24) & 0xFF) * wi;
					r += ((c >> 16) & 0xFF) * wi;
					g += ((c >> 8) & 0xFF) * wi;
					b += (c & 0xFF) * wi;
				}
				pal[f] = (clampByte(a) << 24) | (clampByte(r) << 16) | (clampByte(g) << 8) | clampByte(b);
			}
		}

		if (w[BLOOD] > 0.0f) {
			// Пульс (два удара подряд, как сердце)
			float ph = (t % 1.15f) / 1.15f;
			beatV = clamp01((float) Math.exp(-sq((ph - 0.05f) / 0.05f)) + 0.55f * (float) Math.exp(-sq((ph - 0.27f) / 0.06f)));
			// Моргание
			if (blinkT >= 0.0f) {
				blinkT += dt / 0.24f;
				if (blinkT >= 1.0f) {
					blinkT = -1.0f;
					nextBlink = t + (R.nextFloat() < 0.2f ? 0.12f : 2.2f + R.nextFloat() * 4.0f);
				}
			} else if (t >= nextBlink) {
				blinkT = 0.0f;
			}
			for (int i = 0; i < EMB; i++) {
				emY[i] -= emV[i] * dt;
				emX[i] += (float) Math.sin(t * 1.1f + emPh[i]) * 0.008f * dt;
				if (emY[i] < -0.05f) {
					emY[i] = 1.05f;
					emX[i] = R.nextFloat();
				}
			}
		} else {
			beatV = 0.0f;
		}

		if (w[SAKURA] > 0.0f) {
			for (int i = 0; i < PET; i++) {
				petY[i] += petV[i] * dt;
				petX[i] += ((float) Math.sin(t * 0.7f + petPh[i]) * 0.035f + 0.03f) * dt;
				petR[i] += petRv[i] * dt;
				if (petY[i] > 1.06f) {
					petY[i] = -0.06f;
					petX[i] = R.nextFloat() * 1.1f - 0.1f;
				}
				if (petX[i] > 1.08f) petX[i] -= 1.16f;
			}
		}
		if (w[ABYSS] > 0.0f) {
			for (int i = 0; i < BUB; i++) {
				buY[i] -= buV[i] * dt;
				if (buY[i] < -0.05f) {
					buY[i] = 1.05f;
					buX[i] = R.nextFloat();
				}
			}
		}
		if (w[GALAXY] > 0.0f) {
			for (int i = 0; i < STAR; i++) {
				stX[i] += 0.0035f * (0.3f + stS[i] * 0.35f) * dt;
				if (stX[i] > 1.02f) stX[i] -= 1.04f;
			}
			ssTimer -= dt;
			for (int i = 0; i < 2; i++) {
				if (ssLife[i] > 0.0f) {
					ssLife[i] -= dt;
					ssX[i] += ssVX[i] * dt;
					ssY[i] += ssVY[i] * dt;
				} else if (ssTimer <= 0.0f && w[GALAXY] > 0.1f) {
					ssX[i] = R.nextFloat() * 0.7f;
					ssY[i] = R.nextFloat() * 0.35f;
					ssVX[i] = 0.55f + R.nextFloat() * 0.35f;
					ssVY[i] = 0.25f + R.nextFloat() * 0.2f;
					ssLife[i] = 0.9f;
					ssTimer = 2.5f + R.nextFloat() * 4.0f;
				}
			}
		}
		if (w[MATRIX] > 0.0f) {
			float cellsH = screenH / CELL;
			for (int i = 0; i < COLS; i++) {
				cY[i] += cV[i] * dt;
				if (cY[i] - cL[i] > cellsH + 1.0f) {
					cY[i] = -R.nextFloat() * 18.0f;
					cV[i] = 5.0f + R.nextFloat() * 11.0f;
					cL[i] = 7.0f + R.nextFloat() * 8.0f;
				}
			}
		}
		if (w[NEON] > 0.0f) {
			gridOff = (gridOff + dt * 0.55f) % 1.0f;
		}
	}

	// ---------- Помощники рисования ----------

	private static int clampByte(float v) {
		return Math.max(0, Math.min(255, Math.round(v)));
	}

	private static float clamp01(float v) {
		return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
	}

	private static float sq(float v) {
		return v * v;
	}

	private static int ca(int argb, float m) {
		int a = Math.round(((argb >>> 24) & 0xFF) * clamp01(m));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	private static int mix(int c1, int c2, float k) {
		return UiShapes.lerp(c1, c2, k);
	}

	private static float h01(int x) {
		x ^= x >>> 16;
		x *= 0x7feb352d;
		x ^= x >>> 15;
		x *= 0x846ca68b;
		x ^= x >>> 16;
		return (x & 0xFFFFFF) / 16777216.0f;
	}

	private static final MenuGfx G = new MenuGfx();

	/** Прямоугольник любого размера и поворота (центр в cx, cy). */
	private static void quad(float cx, float cy, float ww, float hh, float rot, int argb) {
		G.box(cx, cy, ww, hh, rot, argb);
	}

	private static void disc(float cx, float cy, float r, int argb) {
		G.disc(cx, cy, r, argb);
	}

	private static void halfDisc(float cx, float cy, float r, int argb) {
		G.halfDisc(cx, cy, r, argb);
	}

	/** Мягкое свечение с плавным краем. */
	private static void glow(float cx, float cy, float r, int rgb, float a) {
		G.glow(cx, cy, r * 1.15f, rgb, a * 0.55f);
	}

	private static void outline(int l, int t0, int r, int b, int argb) {
		G.outline(l, t0, r, b, argb);
	}

	// ---------- Фон экрана (за панелью) ----------

	static void drawBackground(DrawContext ctx, int sw, int sh, float a) {
		screenH = sh;
		screenWidthV = sw;
		G.begin(ctx);
		G.rect(0, 0, sw, sh, ca(pal[P_BG], a));
		for (int i = 1; i < COUNT; i++) {
			float aw = a * w[i];
			if (aw < 0.01f) continue;
			G.setTint(TM[i]);
			switch (i) {
				case BLOOD -> bgBlood(sw, sh, aw);
				case SAKURA -> bgSakura(sw, sh, aw);
				case ABYSS -> bgAbyss(sw, sh, aw);
				case MATRIX -> {
					matrixRain(0, sw, sh, aw * 0.8f);
					matrixGlitch(sw, sh, aw);
				}
				case NEON -> bgNeon(sw, sh, aw);
				case GALAXY -> bgGalaxy(sw, sh, aw);
				default -> {
				}
			}
		}
		G.setTint(null);
		G.flush();
	}

	private static void bgBlood(int sw, int sh, float a) {
		G.gradV(0, 0, sw, sh / 3, ca(0x78500008, a), 0x00000000);
		G.gradV(0, sh * 2 / 3, sw, sh, 0x00000000, ca(0x90300004, a));
		G.rect(0, 0, sw, sh, ca(0x28FF0010, a * beatV * 0.55f));
		// кровавый туман: два больших пятна медленно плывут и дышат в такт пульсу
		glow(sw * (0.18f + 0.05f * (float) Math.sin(t * 0.21f)), sh * 0.85f, sh * 0.62f, 0xFF8A0010, a * (0.16f + 0.14f * beatV));
		glow(sw * (0.82f + 0.05f * (float) Math.cos(t * 0.17f)), sh * 0.2f, sh * 0.5f, 0xFF600008, a * (0.14f + 0.12f * beatV));
		for (int i = 0, n = liteN(EMB); i < n; i++) {
			float x = (emX[i] + (float) Math.sin(t * 0.9f + emPh[i]) * 0.012f) * sw;
			float y = emY[i] * sh;
			float fl = 0.45f + 0.55f * (float) Math.sin(t * 3.0f + emPh[i] * 2.0f);
			int c = mix(0xFFFF4A1E, 0xFF8A0E10, emPh[i] / 6.28f);
			quad(x, y, emS[i], emS[i], 0.0f, ca(c, a * (0.2f + 0.45f * fl * fl)));
		}
	}

	private static final int[] PETAL_COL = {0xFFFFC4D8, 0xFFFFA8C6, 0xFFFFD9E6, 0xFFF48FB1};

	private static void petals(int sw, int sh, float a, int from, int to) {
		for (int i = from; i < to; i++) {
			float flut = Math.abs((float) Math.cos(petR[i] * 0.8f + petPh[i]));
			float wd = petS[i] * (0.55f + 0.45f * flut);
			float hd = petS[i] * 0.62f;
			float x = petX[i] * sw;
			float y = petY[i] * sh;
			float edge = clamp01(Math.min(petY[i] + 0.05f, 1.05f - petY[i]) * 8.0f);
			quad(x, y, wd, hd, petR[i], ca(PETAL_COL[i & 3], a * 0.9f * edge));
		}
	}

	private static void bgSakura(int sw, int sh, float a) {
		G.gradV(0, 0, sw, sh, ca(0x38FFA6C8, a), ca(0x50300A24, a));
		float sweep = 0.5f + 0.5f * (float) Math.sin(t * 0.35f);
		G.gradV(0, 0, sw, sh / 2, ca(0x20FFD0E4, a * sweep), 0x00000000);
		// мягкое солнце и лучи, которые покачиваются
		float sx = sw * 0.1f;
		float sy = sh * 0.06f;
		glow(sx, sy, sh * 0.75f, 0xFFFFC8DC, a * (0.34f + 0.06f * (float) Math.sin(t * 0.6f)));
		for (int k = 0; k < 4; k++) {
			float ang = 0.35f + k * 0.28f + 0.07f * (float) Math.sin(t * 0.25f + k * 1.7f);
			float len = sh * 1.5f;
			float al = 0.05f + 0.035f * (float) Math.sin(t * 0.5f + k * 2.0f);
			G.line(sx, sy, sx + (float) Math.cos(ang) * len, sy + (float) Math.sin(ang) * len, 22.0f + k * 8.0f,
					ca(0xFFFFE4EE, a * Math.max(0.0f, al)), 0x00FFE4EE);
		}
		petals(sw, sh, a, 0, lite ? (PET - 8) * 55 / 100 : PET - 8);
	}

	private static void bgAbyss(int sw, int sh, float a) {
		G.gradV(0, 0, sw, sh, ca(0x5000C8D8, a), ca(0x90001018, a));
		// лучи света сверху
		for (int k = 0; k < 6; k++) {
			float x = sw * (0.08f + 0.17f * k) + (float) Math.sin(t * 0.3f + k * 1.7f) * 22.0f;
			float wd = 26.0f + 18.0f * (float) Math.sin(t * 0.5f + k);
			float al = 0.035f + 0.03f * (float) Math.sin(t * 0.7f + k * 2.1f);
			quad(x, sh * 0.42f, Math.abs(wd), sh * 1.5f, 0.3f, ca(0xFFAEEFFF, a * Math.max(0.0f, al)));
		}
		// блики света на воде: волнистые линии у поверхности
		int pts = lite ? 7 : 12;
		for (int k = 0; k < 4; k++) {
			float yb = sh * (0.05f + 0.055f * k);
			float al = 0.10f - k * 0.02f;
			float px0 = 0.0f;
			float py0 = yb + (float) Math.sin(t * 0.9f + k * 1.3f) * 3.0f;
			for (int i = 1; i <= pts; i++) {
				float px1 = sw * i / (float) pts;
				float py1 = yb + (float) Math.sin(t * 0.9f + k * 1.3f + i * 0.8f) * 3.0f + (float) Math.sin(t * 1.7f + i * 1.9f) * 1.2f;
				float fl = 0.55f + 0.45f * (float) Math.sin(t * 1.3f + i * 1.1f + k);
				G.line(px0, py0, px1, py1, 1.6f, ca(0xFFD8FBFF, a * al * fl), ca(0xFFD8FBFF, a * al * fl));
				px0 = px1;
				py0 = py1;
			}
		}
		bubbles(0, 0, sw, sh, a, liteN(BUB));
		jelly(sw * 0.2f, sh * (0.55f + 0.06f * (float) Math.sin(t * 0.5f)), 1.0f, a, 0.0f);
		jelly(sw * 0.82f, sh * (0.35f + 0.05f * (float) Math.sin(t * 0.43f + 2.0f)), 0.75f, a, 1.7f);
	}

	private static void bubbles(int x0, int y0, int bw, int bh, float a, int n) {
		for (int i = 0; i < n; i++) {
			float x = x0 + (buX[i] + (float) Math.sin(t * 0.9f + buPh[i]) * 0.012f) * bw;
			float y = y0 + buY[i] * bh;
			float s = buS[i];
			disc(x, y, s, ca(0xFF8FE8FF, a * 0.10f));
			quad(x - s * 0.35f, y - s * 0.4f, Math.max(1.0f, s * 0.35f), Math.max(1.0f, s * 0.35f), 0.0f, ca(0xFFFFFFFF, a * 0.55f));
		}
	}

	private static void jelly(float x, float y, float s, float a, float ph) {
		float pulse = 1.0f + 0.12f * (float) Math.sin(t * 1.6f + ph);
		halfDisc(x, y, 11.0f * s * pulse, ca(0xFF7FE8FF, a * 0.30f));
		halfDisc(x, y, 7.0f * s * pulse, ca(0xFFC8F8FF, a * 0.22f));
		for (int k = -2; k <= 2; k++) {
			float sw = (float) Math.sin(t * 1.4f + ph + k * 0.9f) * 3.0f * s;
			quad(x + k * 3.6f * s + sw * 0.5f, y + 11.0f * s, 1.2f, 22.0f * s, sw * 0.04f, ca(0xFF7FE8FF, a * 0.22f));
		}
	}

	private static void matrixRain(int x0, int regionW, int regionH, float a) {
		int cols = Math.min(COLS, regionW / CELL + 1);
		int seedT = (int) (t * 2.5f);
		for (int c = 0; c < cols; c++) {
			float head = cY[c];
			int len = lite ? (int) cL[c] * 6 / 10 : (int) cL[c];
			int px = x0 + c * CELL + 1;
			for (int k = 0; k < len; k++) {
				float cy = (head - k) * CELL;
				if (cy < -CELL || cy > regionH) continue;
				float fade = 1.0f - k / (float) len;
				float al = a * fade * fade;
				if (al < 0.05f) continue;
				int cell = (int) Math.floor(head - k);
				int pat = (int) (h01(c * 131 + cell * 17 + (k == 0 ? seedT * 7 : (seedT / 3) * 7)) * 4.0f);
				int col = k == 0 ? 0xFFD8FFE6 : (k < 3 ? 0xFF6BFF9A : 0xFF1FD65A);
				int colA = ca(col, al);
				int py = Math.round(cy);
				switch (pat) {
					case 0 -> {
						G.rect(px + 2, py, px + 4, py + 7, colA);
						G.rect(px, py, px + 6, py + 2, colA);
					}
					case 1 -> {
						G.rect(px, py + 3, px + 6, py + 5, colA);
						G.rect(px, py, px + 2, py + 7, colA);
					}
					case 2 -> {
						G.rect(px, py, px + 6, py + 2, colA);
						G.rect(px, py + 5, px + 6, py + 7, colA);
						G.rect(px + 4, py, px + 6, py + 7, colA);
					}
					default -> {
						G.rect(px + 1, py, px + 3, py + 7, colA);
						G.rect(px + 3, py + 3, px + 6, py + 5, colA);
					}
				}
			}
		}
	}

	/** Редкий «сбой»: на долю секунды по экрану пробегают светлые полосы. */
	private static void matrixGlitch(int sw, int sh, float a) {
		float cyc = t % 4.3f;
		if (cyc > 0.22f) return;
		int n = (int) (t / 4.3f);
		float k = 1.0f - cyc / 0.22f;
		for (int i = 0; i < 3; i++) {
			float y = h01(n * 7 + i * 13) * sh;
			float hgt = 2.0f + h01(n * 5 + i * 3) * 9.0f;
			float off = (h01(n * 11 + i) - 0.5f) * 40.0f;
			G.rect(off, y, sw + off, y + hgt, ca(0xFF2BFF6A, a * 0.10f * k));
			G.rect(off * 0.5f, y + hgt, sw + off * 0.5f, y + hgt + 1, ca(0xFFD8FFE6, a * 0.18f * k));
		}
	}

	private static void neonGrid(int x1, int x2, int horizon, int bottom, float a) {
		if (bottom <= horizon + 4) return;
		int wd = x2 - x1;
		float cx = x1 + wd * 0.5f;
		for (int i = 0; i < 11; i++) {
			float f = (i + gridOff) / 11.0f;
			float yy = horizon + (bottom - horizon) * f * f * 1.0f;
			G.rect(x1, Math.round(yy), x2, Math.round(yy) + 1, ca(0xFFFF3DDC, a * (0.12f + 0.7f * f)));
		}
		for (int j = -10; j <= 10; j++) {
			float xh = cx + j * wd * 0.018f;
			float xb = cx + j * wd * 0.15f;
			float dx = xb - xh;
			float dy = bottom - horizon;
			float len = (float) Math.sqrt(dx * dx + dy * dy);
			quad((xh + xb) * 0.5f, (horizon + bottom) * 0.5f, 1.2f, len, (float) -Math.atan2(dx, dy), ca(0xFF2DE2FF, a * 0.42f));
		}
		G.rect(x1, horizon, x2, horizon + 1, ca(0xFFFF8AEE, a * 0.9f));
	}

	private static void bgNeon(int sw, int sh, float a) {
		int horizon = Math.round(sh * 0.60f);
		G.gradV(0, 0, sw, horizon, ca(0x40200060, a), ca(0x50FF2DB4, a));
		G.gradV(0, horizon, sw, sh, ca(0x60200040, a), ca(0xA0100024, a));
		// закат
		float cx = sw * 0.5f;
		float rad = Math.min(sh * 0.2f, sw * 0.18f);
		float cy = horizon - rad * 0.15f;
		float cyi = Math.round(cy);
		int rr = Math.round(rad);
		for (int dy = -rr; dy < rr; dy++) {
			float yc = dy + 0.5f;
			if (cy + dy >= horizon) break;
			float frac = (dy + rr) / (2.0f * rr);
			if (frac > 0.5f && (((int) ((dy + rr) * 0.5f + gridOff * 4.0f)) % 4) < (frac - 0.5f) * 8.0f) continue;
			int hi = Math.round((float) Math.sqrt(Math.max(0.0f, rad * rad - yc * yc)));
			if (hi > 0) G.rect(cx - hi, cyi + dy, cx + hi, cyi + dy + 1, ca(mix(0xFFFFE04A, 0xFFFF2D95, frac), a * 0.9f));
		}
		neonGrid(0, sw, horizon, sh, a);
		// плавающие неоновые ромбы
		for (int k = 0; k < (lite ? 3 : 6); k++) {
			float dx = sw * (0.08f + 0.17f * k) + (float) Math.sin(t * 0.3f + k * 1.9f) * 14.0f;
			float dy = horizon * (0.18f + 0.55f * ((k * 37 % 10) / 10.0f)) + (float) Math.sin(t * 0.45f + k) * 6.0f;
			float sz = 5.0f + (k % 3) * 2.5f;
			float rot = t * (0.35f + 0.1f * k) * (k % 2 == 0 ? 1.0f : -1.0f);
			int c1 = ca(k % 2 == 0 ? 0xFF2DE2FF : 0xFFFF3DDC, a * (0.28f + 0.14f * (float) Math.sin(t * 1.4f + k)));
			for (int j = 0; j < 4; j++) {
				float a0 = rot + j * 1.5707964f;
				float a1 = a0 + 1.5707964f;
				G.line(dx + (float) Math.cos(a0) * sz, dy + (float) Math.sin(a0) * sz,
						dx + (float) Math.cos(a1) * sz, dy + (float) Math.sin(a1) * sz, 1.2f, c1, c1);
			}
		}
	}

	private static void bgGalaxy(int sw, int sh, float a) {
		G.gradV(0, 0, sw, sh, ca(0x30202080, a), ca(0x40100030, a));
		disc(sw * (0.25f + 0.03f * (float) Math.sin(t * 0.07f)), sh * 0.35f, sh * 0.34f, ca(0xFF7A3CFF, a * 0.07f));
		disc(sw * (0.78f + 0.03f * (float) Math.cos(t * 0.06f)), sh * 0.62f, sh * 0.40f, ca(0xFF2F6BFF, a * 0.07f));
		disc(sw * 0.55f, sh * 0.2f, sh * 0.26f, ca(0xFFC03CFF, a * 0.05f));
		stars(0, 0, sw, sh, a, liteN(STAR));
		// далёкая планета с кольцом
		float pr = Math.max(8.0f, sh * 0.055f);
		float pcx = sw * 0.88f;
		float pcy = sh * (0.2f + 0.012f * (float) Math.sin(t * 0.3f));
		G.fan(pcx, pcy, pr, 0.0f, 6.2831855f, ca(0xFFB7A6FF, a * 0.55f), ca(0xFF3A2C8C, a * 0.5f));
		float rot = -0.42f;
		float rcs = (float) Math.cos(rot);
		float rsn = (float) Math.sin(rot);
		int segs = lite ? 16 : 26;
		float lx = 0.0f;
		float ly = 0.0f;
		for (int i = 0; i <= segs; i++) {
			float ang = i * 6.2831855f / segs;
			float ex = (float) Math.cos(ang) * pr * 1.9f;
			float ey = (float) Math.sin(ang) * pr * 0.55f;
			float qx = pcx + ex * rcs - ey * rsn;
			float qy = pcy + ex * rsn + ey * rcs;
			if (i > 0) G.line(lx, ly, qx, qy, 1.4f, ca(0xFFD9CCFF, a * 0.4f), ca(0xFFD9CCFF, a * 0.4f));
			lx = qx;
			ly = qy;
		}
		for (int i = 0; i < 2; i++) {
			if (ssLife[i] <= 0.0f) continue;
			float al = clamp01(ssLife[i] / 0.9f);
			float hx = ssX[i] * sw;
			float hy = ssY[i] * sh;
			float ang = (float) Math.atan2(ssVY[i] * sh, ssVX[i] * sw);
			float tail = 54.0f;
			quad(hx - (float) Math.cos(ang) * tail * 0.5f, hy - (float) Math.sin(ang) * tail * 0.5f, tail, 1.4f, ang, ca(0xFFBFD4FF, a * al * 0.55f));
			quad(hx, hy, 2.6f, 2.6f, 0.0f, ca(0xFFFFFFFF, a * al));
		}
	}

	private static void stars(int x0, int y0, int bw, int bh, float a, int n) {
		for (int i = 0; i < n; i++) {
			float tw = 0.5f + 0.5f * (float) Math.sin(t * stV[i] + stPh[i]);
			float al = 0.25f + 0.75f * tw * tw;
			float x = x0 + stX[i] * bw;
			float y = y0 + stY[i] * bh;
			int c = (i % 5 == 0) ? 0xFFBFD0FF : ((i % 7 == 0) ? 0xFFFFD9F0 : 0xFFFFFFFF);
			quad(x, y, stS[i], stS[i], 0.0f, ca(c, a * al));
			if (stS[i] > 1.5f && tw > 0.8f) {
				quad(x, y, 7.0f, 1.0f, 0.0f, ca(c, a * 0.35f * tw));
				quad(x, y, 1.0f, 7.0f, 0.0f, ca(c, a * 0.35f * tw));
			}
		}
	}

	// ---------- Декор внутри панели (вызывать с обрезкой по панели) ----------

	static void drawPanelDecor(DrawContext ctx, int l, int top, int r, int b, float a) {
		G.begin(ctx);
		for (int i = 1; i < COUNT; i++) {
			float aw = a * w[i];
			if (aw < 0.01f) continue;
			G.setTint(TM[i]);
			int pw = r - l;
			int ph = b - top;
			switch (i) {
				case BLOOD -> {
					G.gradV(l, top, r, top + 40, ca(0x50B00014, aw), 0x00000000);
					G.gradV(l, b - 34, r, b, 0x00000000, ca(0x60600008, aw));
					veins(l, top, r, b, aw);
					drips(l, top, pw, aw);
				}
				case SAKURA -> {
					G.gradV(l, top, r, top + 46, ca(0x34FFB4D2, aw), 0x00000000);
					G.gradV(l, b - 34, r, b, 0x00000000, ca(0x30FF8FB8, aw));
				}
				case ABYSS -> {
					for (int k = 0; k < 4; k++) {
						float x = l + pw * (0.12f + 0.27f * k) + (float) Math.sin(t * 0.35f + k * 1.9f) * 18.0f;
						float al = 0.03f + 0.025f * (float) Math.sin(t * 0.8f + k * 2.3f);
						quad(x, top + ph * 0.5f, 22.0f, ph * 1.6f, 0.28f, ca(0xFFAEEFFF, aw * Math.max(0.0f, al)));
					}
					bubbles(l, top, pw, ph, aw * 0.8f, liteN(18));
					G.gradV(l, b - 40, r, b, 0x00000000, ca(0x4000A0B0, aw));
				}
				case MATRIX -> {
					if (!lite) matrixRain(l, pw, ph + top, aw * 0.22f);
					float sc = (t * 0.22f) % 1.3f - 0.15f;
					int sy = top + Math.round(ph * sc);
					G.gradV(l, sy - 14, r, sy, 0x00000000, ca(0xFF2BFF6A, aw * 0.10f));
					G.rect(l, sy, r, sy + 1, ca(0xFF2BFF6A, aw * 0.22f));
					for (int y = top; y < b; y += 3) {
						G.rect(l, y, r, y + 1, ca(0xFF000000, aw * 0.10f));
					}
				}
				case NEON -> {
					int hz = top + Math.round(ph * 0.72f);
					neonGrid(l, r, hz, b, aw * 0.42f);
					float sc = (t * 0.3f) % 1.4f - 0.2f;
					int sy = top + Math.round(ph * sc);
					G.gradV(l, sy - 12, r, sy, 0x00000000, ca(0xFF2DE2FF, aw * 0.07f));
					G.gradV(l, top, r, top + 36, ca(0x30FF3DDC, aw), 0x00000000);
				}
				case GALAXY -> {
					disc(l + pw * 0.8f, top + ph * 0.3f, ph * 0.45f, ca(0xFF6A5CFF, aw * 0.06f));
					disc(l + pw * 0.2f, top + ph * 0.85f, ph * 0.4f, ca(0xFFC03CFF, aw * 0.05f));
					stars(l, top, pw, ph, aw * 0.8f, liteN(55));
				}
				default -> {
				}
			}
		}
		G.setTint(null);
		G.flush();
	}

	/** Жилки в углах панели: короткие ломаные линии, которые пульсируют вместе с сердцем. */
	private static void veins(int l, int top, int r, int b, float a) {
		float al = a * (0.28f + 0.5f * beatV);
		int c = ca(0xFF8E0A12, al);
		int c2 = ca(0xFFC0121C, al * 0.8f);
		for (int corner = 0; corner < 4; corner++) {
			int sx = (corner & 1) == 0 ? 1 : -1;
			int sy = (corner & 2) == 0 ? 1 : -1;
			int ox = (corner & 1) == 0 ? l : r;
			int oy = (corner & 2) == 0 ? top : b;
			for (int br = 0; br < 3; br++) {
				int x = 0;
				int y = 0;
				int seed = corner * 31 + br * 7;
				for (int s = 0; s < 8; s++) {
					boolean horiz = h01(seed + s * 5) < 0.5f;
					int len = 4 + (int) (h01(seed + s * 11 + 3) * 9.0f);
					int nx = horiz ? x + len : x;
					int ny = horiz ? y : y + len;
					if (br == 1) {
						nx = horiz ? x + len : x + 2;
						ny = horiz ? y + 2 : y + len;
					}
					int x1 = ox + sx * Math.min(x, nx);
					int x2 = ox + sx * Math.max(x, nx);
					int y1 = oy + sy * Math.min(y, ny);
					int y2 = oy + sy * Math.max(y, ny);
					int fx1 = Math.min(x1, x2);
					int fx2 = Math.max(x1, x2) + 1;
					int fy1 = Math.min(y1, y2);
					int fy2 = Math.max(y1, y2) + 1;
					G.rect(fx1, fy1, fx2, fy2, s < 4 ? c : c2);
					x = nx;
					y = ny;
				}
			}
		}
	}

	/** Капли крови, медленно стекающие с верхнего края панели. */
	private static void drips(int l, int top, int pw, float a) {
		for (int k = 0; k < 7; k++) {
			float period = 6.0f + (k % 3) * 1.7f;
			float ph = (t / period + k * 0.37f) % 1.0f;
			float maxLen = 8.0f + (k * 5 % 12);
			float x = l + pw * (0.24f + k * 0.085f) + (k % 2) * 3;
			float len;
			float dropY = -1.0f;
			float dropA = 0.0f;
			if (ph < 0.55f) {
				float e = ph / 0.55f;
				len = maxLen * (e * e * (3.0f - 2.0f * e));
			} else if (ph < 0.75f) {
				len = maxLen;
			} else {
				float e = (ph - 0.75f) / 0.25f;
				len = maxLen * (1.0f - e);
				dropY = top + maxLen + e * 26.0f;
				dropA = 1.0f - e;
			}
			if (len > 0.5f) {
				quad(x, top + len * 0.5f, 2.0f, len, 0.0f, ca(0xFFA0101A, a * 0.9f));
				disc(x, top + len, 2.2f, ca(0xFFC0141E, a));
				quad(x - 0.6f, top + len - 0.8f, 1.0f, 1.0f, 0.0f, ca(0xFFFFB0B0, a * 0.7f));
			}
			if (dropY > 0.0f) disc(x, dropY, 2.0f, ca(0xFFB0121C, a * dropA));
		}
	}

	// ---------- Рамка вокруг панели ----------

	static void drawBorder(DrawContext ctx, int l, int top, int r, int b, float a) {
		G.begin(ctx);
		for (int i = 1; i < COUNT; i++) {
			float aw = a * w[i];
			if (aw < 0.01f) continue;
			G.setTint(TM[i]);
			switch (i) {
				case BLOOD -> {
					float p = 0.30f + 0.55f * beatV;
					outline(l - 1, top - 1, r + 1, b + 1, ca(0xFFE0242B, aw * p * 0.35f));
					outline(l, top, r, b, ca(0xFFC0141E, aw * p));
				}
				case SAKURA -> {
					outline(l, top, r, b, ca(0xFFFFA8C8, aw * 0.55f));
					float hx = l + ((t * 0.12f) % 1.0f) * (r - l);
					G.gradV(Math.round(hx) - 30, top, Math.round(hx), top + 2, 0x00FFFFFF, ca(0xFFFFE4EE, aw * 0.9f));
					G.gradV(Math.round(hx), top, Math.round(hx) + 8, top + 2, ca(0xFFFFE4EE, aw * 0.9f), 0x00FFFFFF);
				}
				case ABYSS -> {
					float p = 0.5f + 0.5f * (float) Math.sin(t * 1.1f);
					outline(l, top, r, b, ca(0xFF2EE6D6, aw * (0.30f + 0.3f * p)));
				}
				case MATRIX -> {
					outline(l, top, r, b, ca(0xFF2BFF6A, aw * 0.5f));
					int c = ca(0xFFA8FFC4, aw * 0.9f);
					int s = 8;
					G.rect(l - 2, top - 2, l + s, top - 1, c);
					G.rect(l - 2, top - 2, l - 1, top + s, c);
					G.rect(r - s, top - 2, r + 2, top - 1, c);
					G.rect(r + 1, top - 2, r + 2, top + s, c);
					G.rect(l - 2, b + 1, l + s, b + 2, c);
					G.rect(l - 2, b - s, l - 1, b + 2, c);
					G.rect(r - s, b + 1, r + 2, b + 2, c);
					G.rect(r + 1, b - s, r + 2, b + 2, c);
				}
				case NEON -> {
					float p = 0.65f + 0.35f * (float) Math.sin(t * 2.2f);
					outline(l - 2, top - 2, r + 2, b + 2, ca(0xFF2DE2FF, aw * 0.30f * p));
					outline(l - 1, top - 1, r + 1, b + 1, ca(0xFF2DE2FF, aw * 0.9f * p));
					outline(l + 1, top + 1, r - 1, b - 1, ca(0xFFFF3DDC, aw * 0.55f));
				}
				case GALAXY -> {
					outline(l, top, r, b, ca(0xFF8E9BFF, aw * 0.35f));
					float per = 2.0f * ((r - l) + (b - top));
					float pos = ((t * 0.05f) % 1.0f) * per;
					float px;
					float py;
					float d = pos;
					if (d < r - l) {
						px = l + d;
						py = top;
					} else if ((d -= r - l) < b - top) {
						px = r;
						py = top + d;
					} else if ((d -= b - top) < r - l) {
						px = r - d;
						py = b;
					} else {
						d -= r - l;
						px = l;
						py = b - d;
					}
					glow(px, py, 5.0f, 0xFFBFD0FF, aw);
					disc(px, py, 1.6f, ca(0xFFFFFFFF, aw));
				}
				default -> {
				}
			}
		}
		G.setTint(null);
		G.flush();
	}

	// ---------- Передний план: глаз, ветка, лепестки ----------

	static void drawFront(DrawContext ctx, int l, int top, int r, int b, float a, float mouseX, float mouseY) {
		G.begin(ctx);
		float aw = a * w[BLOOD];
		if (aw >= 0.01f) {
			float s = clamp01((top + 5.0f) / 19.5f);
			G.setTint(TM[BLOOD]);
			if (s >= 0.45f) drawEye(r - 34.0f, top - 5.0f, s, mouseX, mouseY, aw);
		}
		aw = a * w[SAKURA];
		if (aw >= 0.01f) {
			G.setTint(TM[SAKURA]);
			drawBranch(r, top, aw);
			petals((int) (screenWidth()), (int) screenH, aw, PET - 8, PET);
		}
		G.setTint(null);
		G.flush();
	}

	private static float screenWidthV = 480.0f;

	private static float screenWidth() {
		return screenWidthV;
	}

	/** Ветка сакуры вдоль верхнего правого края панели. */
	private static void drawBranch(int r, int top, float a) {
		float[] bx = {r + 16, r - 22, r - 64, r - 104, r - 148};
		float[] by = {top - 3, top - 8, top - 4, top - 10, top - 5};
		for (int i = 0; i < 4; i++) {
			float x1 = bx[i];
			float y1 = by[i];
			float x2 = bx[i + 1];
			float y2 = by[i + 1];
			float dx = x2 - x1;
			float dy = y2 - y1;
			float len = (float) Math.sqrt(dx * dx + dy * dy);
			quad((x1 + x2) * 0.5f, (y1 + y2) * 0.5f, len + 1.0f, 2.4f - i * 0.3f, (float) Math.atan2(dy, dx), ca(0xFF4A2A22, a));
		}
		for (int i = 0; i < 5; i++) {
			float sway = (float) Math.sin(t * 0.8f + i * 1.3f) * 1.6f;
			blossom(bx[i] + sway * 0.4f, by[i] + 2.0f + sway * 0.3f, 4.6f - (i % 2) * 0.8f, t * 0.15f + i, a);
			if (i % 2 == 0) {
				blossom(bx[i] + 9.0f, by[i] - 3.0f + sway * 0.2f, 3.2f, i * 0.9f + t * 0.12f, a * 0.9f);
			}
		}
	}

	private static void blossom(float x, float y, float rad, float rot, float a) {
		for (int p = 0; p < 5; p++) {
			float ang = rot + p * 1.2566f;
			quad(x + (float) Math.cos(ang) * rad * 0.62f, y + (float) Math.sin(ang) * rad * 0.62f,
					rad * 1.05f, rad * 0.78f, ang, ca(p % 2 == 0 ? 0xFFFFB9D2 : 0xFFFFA2C2, a));
		}
		disc(x, y, rad * 0.32f, ca(0xFFE0568A, a));
	}

	/** Глаз в правом верхнем углу: моргает, следит за курсором, зрачок дышит в такт пульсу. */
	private static void drawEye(float cx, float cy, float s, float mx, float my, float a) {
		G.setXform(cx, cy, s);

		float open = blinkT < 0.0f ? 1.0f : 1.0f - (float) Math.sin(Math.PI * blinkT);
		open = clamp01(open) * (0.96f + 0.04f * beatV);
		final int rx = 18;
		final int ry = 9;

		disc(0.0f, 0.0f, 21.0f, ca(0xFFFF1010, a * (0.05f + 0.07f * beatV)));

		float ndx = Math.max(-1.0f, Math.min(1.0f, (mx - cx) / 220.0f));
		float ndy = Math.max(-1.0f, Math.min(1.0f, (my - cy) / 150.0f));
		float ix = ndx * 6.5f;
		float iy = ndy * 3.0f;
		float ri = 7.4f;
		float rp = ri * (0.40f + 0.10f * beatV);

		for (int x = -rx; x <= rx; x++) {
			float u = x / (float) rx;
			float curve = (float) Math.pow(Math.max(0.0f, 1.0f - u * u), 0.8f);
			float hh = ry * curve * open;
			int topY = Math.round(-hh);
			int botY = Math.round(hh);
			// веко
			G.rect(x, topY - 3, x + 1, topY, ca(0xFF4C0B11, a));
			G.rect(x, botY, x + 1, botY + 2, ca(0xFF3A080D, a));
			if (botY > topY) {
				float edge = (float) Math.pow(Math.abs(u), 2.2f);
				G.rect(x, topY, x + 1, botY, ca(mix(0xFFF3E8E0, 0xFFD27C7C, edge), a));
				G.rect(x, topY, x + 1, topY + Math.max(1, (botY - topY) / 3), ca(0x55000000, a));
				float dx = x - ix;
				if (Math.abs(dx) < ri) {
					float half = (float) Math.sqrt(ri * ri - dx * dx);
					int it = Math.max(topY, Math.round(iy - half));
					int ib = Math.min(botY, Math.round(iy + half));
					if (ib > it) {
						float rr = Math.abs(dx) / ri;
						float fib = h01(x * 13 + 7);
						int ic = mix(0xFFE63C2C, 0xFF6E0808, clamp01(rr * rr * 0.9f + (1.0f - fib) * 0.28f));
						if (rr > 0.86f) ic = 0xFF3A0505;
						G.rect(x, it, x + 1, ib, ca(ic, a));
						if (Math.abs(dx) < rp) {
							float pr = (float) Math.sqrt(rp * rp - dx * dx);
							int pt = Math.max(it, Math.round(iy - pr));
							int pb = Math.min(ib, Math.round(iy + pr));
							if (pb > pt) G.rect(x, pt, x + 1, pb, ca(0xFF050000, a));
						}
					}
				}
			}
			// линия века и ресницы
			G.rect(x, topY - 1, x + 1, topY, ca(0xFF0A0102, a));
			G.rect(x, botY, x + 1, botY + 1, ca(0xFF0A0102, a));
			if ((x & 3) == 0 && open > 0.12f) {
				int lash = 2 + (h01(x * 5 + 1) < 0.5f ? 1 : 0);
				G.rect(x, topY - 3 - lash, x + 1, topY - 3, ca(0xFF120204, a));
			}
		}
		// красные прожилки у уголков
		if (open > 0.5f) {
			for (int side = -1; side <= 1; side += 2) {
				for (int k = 0; k < 3; k++) {
					int vx = side * (rx - 3 - k * 2);
					int vy = (k - 1) * 3;
					float uu = vx / (float) rx;
					float hv = ry * (float) Math.pow(Math.max(0.0f, 1.0f - uu * uu), 0.8f) * open;
					if (Math.abs(vy) < hv - 1.0f) {
						int len = 5 + k * 2;
						int x1 = side < 0 ? vx : vx - len;
						int x2 = side < 0 ? vx + len : vx;
						G.rect(x1, vy, x2, vy + 1, ca(0xFFB02020, a * 0.55f));
					}
				}
			}
			float hx = ix - 2.4f;
			float hy = iy - 2.6f;
			float uh = hx / rx;
			float hhh = ry * (float) Math.pow(Math.max(0.0f, 1.0f - uh * uh), 0.8f) * open;
			if (Math.abs(hy) < hhh - 1.0f) {
				G.rect(Math.round(hx), Math.round(hy), Math.round(hx) + 2, Math.round(hy) + 2, ca(0xFFFFFFFF, a * 0.85f));
			}
		}
		G.clearXform();
	}
}
