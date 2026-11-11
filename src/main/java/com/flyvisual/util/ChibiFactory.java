package com.flyvisual.util;

import com.flyvisual.util.PetModel.Part;
import com.flyvisual.util.Voxels.Paint;

/** Сборка мультяшных воксельных питомцев (ChibiPet). Кубики по 2,5 см, пиксельная текстура, большие головы. */
public final class ChibiFactory {
	private static final float R = 0.025f;
	public static final String[] NAMES = {"Кубокот", "Лисёнок", "Зайка", "Панда", "Совёнок", "Бу", "Слайм", "Дрон", "Тыква-нетопырь"};
	/** Парит ли питомец в воздухе. */
	public static final boolean[] FLYING = {false, false, false, false, true, true, false, true, true};

	private ChibiFactory() {
	}

	public static ChibiPet create(int kind) {
		return switch (kind) {
			case 0 -> walker(0);
			case 1 -> walker(1);
			case 2 -> walker(2);
			case 3 -> walker(3);
			case 4 -> owl();
			case 5 -> ghost();
			case 6 -> slime();
			case 7 -> drone();
			default -> pumpkin();
		};
	}

	// ---------- Помощники ----------

	private static Voxels vox() {
		return new Voxels(R, -0.6f, -0.02f, -0.5f, 0.6f, 0.95f, 0.5f);
	}

	/** Цвет с пиксельным шумом: каждый кубик чуть светлее или темнее, как текстура. */
	private static Paint tex(int rgb) {
		return (x, y, z) -> Voxels.lum(rgb, 0.9f + 0.17f * Voxels.grain(x, y, z, R));
	}

	private static Paint flat(int rgb) {
		return (x, y, z) -> rgb;
	}

	/** Скруглённый «кирпич»: супер-эллипсоид со степенью 4. */
	private static void rbox(Voxels v, float cx, float cy, float cz, float rx, float ry, float rz, Paint p) {
		v.fill(cx - rx, cy - ry, cz - rz, cx + rx, cy + ry, cz + rz, (x, y, z) -> {
			float a = (x - cx) / rx;
			float b = (y - cy) / ry;
			float c = (z - cz) / rz;
			a *= a;
			b *= b;
			c *= c;
			return a * a + b * b + c * c <= 1.0f;
		}, p);
	}

	private static void paintBox(Voxels v, float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
		v.paintOver(x0, y0, z0, x1, y1, z1, (x, y, z) -> x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1, flat(rgb));
	}

	/** Глаз на передней стороне: цвет радужки, зрачок, блик. zf — передняя плоскость головы. */
	private static void eye(Voxels v, float cx, float cy, float zf, float hw, float hh, int iris, int pupil, float pupilW) {
		paintBox(v, cx - hw, cy - hh, zf - 0.05f, cx + hw, cy + hh, zf + 0.03f, iris);
		paintBox(v, cx - pupilW, cy - hh, zf - 0.05f, cx + pupilW, cy + hh, zf + 0.03f, pupil);
		v.dot(cx + hw * 0.55f, cy + hh * 0.7f, zf + 0.005f, 0xFFFFFF);
	}

	private static void cone(Voxels v, float cx, float y0, float cz, float r0, float h, float rz, Paint p) {
		v.fill(cx - r0, y0, cz - rz, cx + r0, y0 + h, cz + rz, (x, y, z) -> {
			float t = (y - y0) / h;
			return Math.abs(x - cx) <= r0 * (1.0f - t) && Math.abs(z - cz) <= rz;
		}, p);
	}

	private static Voxels rbPart(float cx, float cy, float cz, float rx, float ry, float rz, int c) {
		Voxels v = vox();
		rbox(v, cx, cy, cz, rx, ry, rz, tex(c));
		return v;
	}

	// ---------- Ходячие: кот, лис, заяц, панда ----------

	private static ChibiPet walker(int type) {
		ChibiPet m = new ChibiPet();
		m.mode = type == 2 ? ChibiPet.HOP : ChibiPet.WALK;
		m.tailStyle = type == 0 ? 1 : (type == 1 ? 2 : 3);
		m.swing = type == 2 ? 30.0f : 32.0f;
		final int main = type == 0 ? 0x2B2B34 : type == 1 ? 0xE2712B : type == 2 ? 0xF4EEF3 : 0xF4F4F4;
		final int dark = type == 0 ? 0x1B1B22 : type == 1 ? 0x3A241A : type == 2 ? 0xDCCFD8 : 0x1E1E24;
		final int light = type == 0 ? 0xEFE9E4 : type == 1 ? 0xFFF0DA : type == 2 ? 0xFFFFFF : 0xFFFFFF;

		// Тело
		Voxels b = vox();
		rbox(b, 0, 0.26f, -0.03f, type == 3 ? 0.14f : 0.125f, 0.105f, 0.17f, tex(main));
		rbox(b, 0, 0.2f, 0.0f, 0.09f, 0.07f, 0.15f, tex(light));
		if (type == 3) {
			// чёрный пояс на плечах панды
			b.paintOver(-0.2f, 0.1f, 0.03f, 0.2f, 0.45f, 0.14f, (x, y, z) -> true, tex(dark));
			b.paintOver(-0.2f, 0.1f, 0.03f, 0.2f, 0.2f, 0.14f, (x, y, z) -> y < 0.2f && Math.abs(x) < 0.07f, tex(light));
		}
		m.body = m.addBlocky("body", null, 0, 0.2f, -0.03f, b);

		// Голова
		Voxels h = vox();
		float hrx = type == 1 ? 0.17f : 0.16f;
		rbox(h, 0, 0.44f, 0.12f, hrx, 0.14f, 0.14f, tex(main));
		float zf = 0.26f;
		switch (type) {
			case 0 -> {
				paintBoxOval(h, 0, 0.395f, zf, 0.06f, 0.035f, 0xEFE9E4);
				h.dot(0, 0.42f, zf + 0.01f, 0xE48A9A);
				eye(h, 0.075f, 0.465f, zf, 0.032f, 0.04f, 0xD6E34A, 0x0A0A0A, 0.01f);
				eye(h, -0.075f, 0.465f, zf, 0.032f, 0.04f, 0xD6E34A, 0x0A0A0A, 0.01f);
			}
			case 1 -> {
				rbox(h, 0, 0.395f, 0.27f, 0.055f, 0.04f, 0.075f, tex(light));
				h.dot(0, 0.415f, 0.345f, 0x141414);
				h.dot(0.0125f, 0.415f, 0.345f, 0x141414);
				paintBoxOval(h, 0.125f, 0.40f, 0.18f, 0.045f, 0.05f, 0xFFF0DA);
				paintBoxOval(h, -0.125f, 0.40f, 0.18f, 0.045f, 0.05f, 0xFFF0DA);
				eye(h, 0.075f, 0.465f, zf, 0.03f, 0.036f, 0x2A1A10, 0x0A0A0A, 0.0f);
				eye(h, -0.075f, 0.465f, zf, 0.03f, 0.036f, 0x2A1A10, 0x0A0A0A, 0.0f);
			}
			case 2 -> {
				paintBoxOval(h, 0, 0.4f, zf, 0.04f, 0.03f, 0xFFFFFF);
				h.dot(0, 0.415f, zf + 0.01f, 0xF2829E);
				paintBox(h, -0.012f, 0.365f, zf - 0.03f, 0.012f, 0.385f, zf + 0.02f, 0xFFFFFF);
				paintBoxOval(h, 0.1f, 0.4f, zf - 0.01f, 0.04f, 0.03f, 0xF9B7CB);
				paintBoxOval(h, -0.1f, 0.4f, zf - 0.01f, 0.04f, 0.03f, 0xF9B7CB);
				eye(h, 0.07f, 0.465f, zf, 0.026f, 0.034f, 0x1A1218, 0x1A1218, 0.0f);
				eye(h, -0.07f, 0.465f, zf, 0.026f, 0.034f, 0x1A1218, 0x1A1218, 0.0f);
			}
			default -> {
				paintBoxOval(h, 0.075f, 0.465f, zf, 0.04f, 0.05f, 0x1E1E24);
				paintBoxOval(h, -0.075f, 0.465f, zf, 0.04f, 0.05f, 0x1E1E24);
				h.dot(0.075f, 0.47f, zf + 0.005f, 0xFFFFFF);
				h.dot(-0.075f, 0.47f, zf + 0.005f, 0xFFFFFF);
				paintBoxOval(h, 0, 0.405f, zf, 0.055f, 0.04f, 0xF1E6D8);
				h.dot(0, 0.42f, zf + 0.01f, 0x1E1E24);
				h.dot(0.0125f, 0.42f, zf + 0.01f, 0x1E1E24);
				h.dot(-0.0125f, 0.42f, zf + 0.01f, 0x1E1E24);
			}
		}
		m.head = m.addBlocky("head", m.body, 0, 0.36f, 0.08f, h);

		// Уши
		for (int s = -1; s <= 1; s += 2) {
			Voxels e = vox();
			float ex = s * (type == 3 ? 0.115f : 0.095f);
			float ey = 0.55f;
			switch (type) {
				case 0, 1 -> {
					float w = type == 1 ? 0.075f : 0.065f;
					float hh = type == 1 ? 0.17f : 0.14f;
					cone(e, ex, ey, 0.1f, w, hh, 0.03f, tex(main));
					if (type == 1) {
						e.paintOver(ex - w, ey + hh * 0.62f, 0.05f, ex + w, ey + hh, 0.15f, (x, y, z) -> true, tex(dark));
					}
					cone(e, ex, ey + 0.015f, 0.123f, w * 0.55f, hh * 0.6f, 0.01f, flat(type == 1 ? 0x3A241A : 0xE48A9A));
				}
				case 2 -> {
					rbox(e, ex * 0.8f, ey + 0.13f, 0.08f, 0.032f, 0.14f, 0.022f, tex(main));
					rbox(e, ex * 0.8f, ey + 0.13f, 0.1f, 0.016f, 0.11f, 0.008f, flat(0xF9B7CB));
				}
				default -> {
					rbox(e, ex, ey + 0.01f, 0.1f, 0.05f, 0.05f, 0.03f, tex(dark));
				}
			}
			Part ear = m.addBlocky(s > 0 ? "earL" : "earR", m.head, ex, ey - 0.01f, 0.1f, e);
			if (s > 0) m.earL = ear;
			else m.earR = ear;
		}

		// Ноги
		float[][] legs = {{0.075f, 0.075f}, {-0.075f, 0.075f}, {0.075f, -0.125f}, {-0.075f, -0.125f}};
		for (int i = 0; i < 4; i++) {
			Voxels l = vox();
			float lx = legs[i][0];
			float lz = legs[i][1];
			boolean back = i >= 2;
			int lc = type == 3 ? dark : (type == 1 ? main : main);
			float hw = type == 3 ? 0.048f : 0.042f;
			l.box(lx - hw, 0.0f, lz - hw, lx + hw, 0.19f, lz + hw, tex(lc));
			if (type == 1) {
				l.paintOver(lx - hw, 0.0f, lz - hw, lx + hw, 0.085f, lz + hw, (x, y, z) -> true, tex(dark));
			} else if (type == 0) {
				l.paintOver(lx - hw, 0.0f, lz - hw, lx + hw, 0.04f, lz + hw, (x, y, z) -> true, tex(light));
			} else if (type == 2) {
				if (back) l.box(lx - hw - 0.005f, 0.0f, lz - hw, lx + hw + 0.005f, 0.04f, lz + hw + 0.05f, tex(main));
			}
			Part leg = m.addBlocky("leg" + i, m.body, lx, 0.19f, lz, l);
			switch (i) {
				case 0 -> m.legFL = leg;
				case 1 -> m.legFR = leg;
				case 2 -> m.legBL = leg;
				default -> m.legBR = leg;
			}
		}

		// Хвост
		Voxels t = vox();
		switch (type) {
			case 0 -> {
				t.tube(0, 0.27f, -0.2f, 0.035f, 0, 0.36f, -0.31f, 0.032f, tex(main));
				t.tube(0, 0.36f, -0.31f, 0.032f, 0, 0.52f, -0.3f, 0.03f, tex(main));
			}
			case 1 -> {
				rbox(t, 0, 0.27f, -0.33f, 0.07f, 0.07f, 0.14f, tex(main));
				rbox(t, 0, 0.27f, -0.45f, 0.06f, 0.06f, 0.06f, tex(light));
			}
			case 2 -> rbox(t, 0, 0.27f, -0.23f, 0.05f, 0.05f, 0.05f, tex(light));
			default -> rbox(t, 0, 0.27f, -0.21f, 0.035f, 0.035f, 0.035f, tex(main));
		}
		m.tail = m.addBlocky("tail", m.body, 0, 0.27f, -0.18f, t);
		return m;
	}

	private static void paintBoxOval(Voxels v, float cx, float cy, float cz, float rx, float ry, int rgb) {
		v.paintOver(cx - rx, cy - ry, cz - 0.07f, cx + rx, cy + ry, cz + 0.04f, (x, y, z) -> {
			float a = (x - cx) / rx;
			float b = (y - cy) / ry;
			return a * a + b * b <= 1.0f;
		}, flat(rgb));
	}

	// ---------- Сова ----------

	private static ChibiPet owl() {
		ChibiPet m = new ChibiPet();
		m.mode = ChibiPet.WINGS;
		int brown = 0x8B5A33;
		int cream = 0xEBD6AB;
		Voxels b = vox();
		rbox(b, 0, 0.38f, 0, 0.14f, 0.17f, 0.125f, tex(brown));
		rbox(b, 0, 0.37f, 0.06f, 0.095f, 0.13f, 0.08f, tex(cream));
		// ряды перьев на пузе
		for (int j = 0; j < 5; j++) {
			float y = 0.27f + j * 0.05f;
			b.paintOver(-0.1f, y, 0.05f, 0.1f, y + 0.012f, 0.16f, (x, yy, z) -> ((int) Math.floor(x / R)) % 2 == 0, tex(0xC9A878));
		}
		m.body = m.addBlocky("body", null, 0, 0.35f, 0, b);

		Voxels h = vox();
		rbox(h, 0, 0.6f, 0.01f, 0.165f, 0.12f, 0.13f, tex(brown));
		float zf = 0.14f;
		for (int s = -1; s <= 1; s += 2) {
			float ex = s * 0.07f;
			paintBoxOval(h, ex, 0.6f, zf, 0.065f, 0.065f, 0xF6EEDC);
			paintBoxOval(h, ex, 0.6f, zf, 0.045f, 0.045f, 0xF2B91B);
			paintBoxOval(h, ex, 0.6f, zf, 0.022f, 0.025f, 0x0A0A0A);
			h.dot(ex + s * 0.012f, 0.62f, zf + 0.004f, 0xFFFFFF);
		}
		h.box(-0.012f, 0.535f, 0.12f, 0.012f, 0.585f, 0.18f, flat(0xF28A1E));
		m.head = m.addBlocky("head", m.body, 0, 0.52f, 0.0f, h);
		for (int s = -1; s <= 1; s += 2) {
			Voxels e = vox();
			cone(e, s * 0.1f, 0.7f, 0.0f, 0.04f, 0.1f, 0.03f, tex(0x6E4526));
			Part ear = m.addBlocky(s > 0 ? "earL" : "earR", m.head, s * 0.1f, 0.69f, 0.0f, e);
			if (s > 0) m.earL = ear;
			else m.earR = ear;
		}
		for (int s = -1; s <= 1; s += 2) {
			final float side = s;
			Voxels w2 = vox();
			w2.fill(-0.5f, 0.1f, -0.1f, 0.5f, 0.6f, 0.1f, (x, y, z) -> {
				float xa = x * side;
				if (xa < 0.12f || xa > 0.32f) return false;
				float k = (xa - 0.12f) / 0.2f;
				float top = 0.55f - 0.1f * k * k;
				float bottom = 0.5f - 0.3f * k + 0.03f * (((int) Math.floor(xa / R)) % 2);
				return y <= top && y >= bottom && Math.abs(z) <= 0.03f;
			}, (x, y, z) -> Voxels.lum(Voxels.mix(0x6E4526, 0xB98A55, Voxels.smooth(0.42f, 0.2f, y)), 0.9f + 0.17f * Voxels.grain(x, y, z, R)));
			Part wing = m.addBlocky(s > 0 ? "wingL" : "wingR", m.body, s * 0.13f, 0.5f, 0.0f, w2);
			if (s > 0) m.wingL = wing;
			else m.wingR = wing;
		}
		for (int s = -1; s <= 1; s += 2) {
			Voxels l = vox();
			l.box(s * 0.06f - 0.025f, 0.14f, 0.03f, s * 0.06f + 0.025f, 0.22f, 0.08f, flat(0xF28A1E));
			l.box(s * 0.06f - 0.04f, 0.14f, 0.03f, s * 0.06f + 0.04f, 0.165f, 0.11f, flat(0xF28A1E));
			Part leg = m.addBlocky("leg", m.body, s * 0.06f, 0.22f, 0.04f, l);
			if (s > 0) m.legFL = leg;
			else m.legFR = leg;
		}
		Voxels t = vox();
		t.box(-0.06f, 0.2f, -0.2f, 0.06f, 0.34f, -0.12f, tex(0x6E4526));
		m.tail = m.addBlocky("tail", m.body, 0, 0.3f, -0.12f, t);
		return m;
	}

	// ---------- Призрак Бу ----------

	private static ChibiPet ghost() {
		ChibiPet m = new ChibiPet();
		m.mode = ChibiPet.GHOST;
		int white = 0xEDE8FA;
		Voxels b = vox();
		rbox(b, 0, 0.34f, 0, 0.17f, 0.2f, 0.15f, tex(white));
		// «юбка» с зубцами снизу
		b.carve(-0.3f, 0.0f, -0.3f, 0.3f, 0.2f, 0.3f, (x, y, z) -> {
			float ang = (float) Math.atan2(z, x);
			float notch = 0.06f + 0.04f * (float) Math.max(0.0, Math.cos(ang * 5.0f));
			return y < notch + 0.08f && Math.abs(Math.cos(ang * 2.5f)) > 0.55f;
		});
		float zf = 0.15f;
		paintBoxOval(b, 0.065f, 0.38f, zf, 0.03f, 0.048f, 0x15121C);
		paintBoxOval(b, -0.065f, 0.38f, zf, 0.03f, 0.048f, 0x15121C);
		b.dot(0.07f, 0.4f, zf + 0.004f, 0xFFFFFF);
		b.dot(-0.06f, 0.4f, zf + 0.004f, 0xFFFFFF);
		paintBoxOval(b, 0.0f, 0.3f, zf, 0.022f, 0.026f, 0x15121C);
		paintBoxOval(b, 0.115f, 0.325f, zf - 0.02f, 0.03f, 0.02f, 0xF7B6D2);
		paintBoxOval(b, -0.115f, 0.325f, zf - 0.02f, 0.03f, 0.02f, 0xF7B6D2);
		m.body = m.addBlocky("body", null, 0, 0.3f, 0, b);
		m.head = m.body;
		for (int s = -1; s <= 1; s += 2) {
			Voxels a = vox();
			a.tube(s * 0.17f, 0.3f, 0.02f, 0.035f, s * 0.27f, 0.26f, 0.04f, 0.032f, tex(white));
			Part arm = m.addBlocky(s > 0 ? "armL" : "armR", m.body, s * 0.16f, 0.32f, 0.02f, a);
			if (s > 0) m.armL = arm;
			else m.armR = arm;
		}
		Voxels hat = vox();
		hat.box(-0.2f, 0.5f, -0.2f, 0.2f, 0.53f, 0.2f, tex(0x55308A));
		hat.fill(-0.2f, 0.5f, -0.2f, 0.2f, 0.53f, 0.2f, (x, y, z) -> x * x + z * z <= 0.2f * 0.2f, tex(0x6B3FA0));
		hat.carve(-0.3f, 0.49f, -0.3f, 0.3f, 0.54f, 0.3f, (x, y, z) -> x * x + z * z > 0.2f * 0.2f);
		cone(hat, 0, 0.53f, 0, 0.125f, 0.12f, 0.125f, tex(0x6B3FA0));
		cone(hat, 0, 0.65f, 0, 0.085f, 0.1f, 0.085f, tex(0x6B3FA0));
		cone(hat, 0.0f, 0.75f, 0, 0.05f, 0.07f, 0.05f, tex(0x6B3FA0));
		hat.paintOver(-0.2f, 0.53f, -0.2f, 0.2f, 0.56f, 0.2f, (x, y, z) -> true, flat(0xF2C230));
		m.hat = m.addBlocky("hat", m.body, 0, 0.5f, 0, hat);
		return m;
	}

	// ---------- Слайм ----------

	private static ChibiPet slime() {
		ChibiPet m = new ChibiPet();
		m.mode = ChibiPet.SLIME;
		Voxels b = vox();
		rbox(b, 0, 0.17f, 0, 0.18f, 0.16f, 0.18f, (x, y, z) -> Voxels.lum(Voxels.mix(0x4FC84F, 0x8BF08B, Voxels.smooth(0.0f, 0.34f, y)), 0.92f + 0.14f * Voxels.grain(x, y, z, R)));
		float zf = 0.18f;
		paintBox(b, 0.045f, 0.18f, zf - 0.05f, 0.1f, 0.25f, zf + 0.03f, 0x10261A);
		paintBox(b, -0.1f, 0.18f, zf - 0.05f, -0.045f, 0.25f, zf + 0.03f, 0x10261A);
		b.dot(0.09f, 0.235f, zf + 0.005f, 0xFFFFFF);
		b.dot(-0.055f, 0.235f, zf + 0.005f, 0xFFFFFF);
		paintBox(b, -0.03f, 0.115f, zf - 0.05f, 0.03f, 0.135f, zf + 0.03f, 0x10261A);
		paintBox(b, 0.11f, 0.13f, zf - 0.05f, 0.15f, 0.15f, zf + 0.03f, 0xB4F5A0);
		paintBox(b, -0.15f, 0.13f, zf - 0.05f, -0.11f, 0.15f, zf + 0.03f, 0xB4F5A0);
		for (int i = 0; i < 3; i++) {
			b.dot(-0.1f + i * 0.0f, 0.3f - i * 0.025f, 0.12f + i * 0.0f, 0xE9FFE0);
		}
		m.body = m.addBlocky("body", null, 0, 0.0f, 0, b);
		return m;
	}

	// ---------- Дрон ----------

	private static ChibiPet drone() {
		ChibiPet m = new ChibiPet();
		m.mode = ChibiPet.DRONE;
		Voxels b = vox();
		b.box(-0.09f, 0.2f, -0.12f, 0.09f, 0.3f, 0.13f, tex(0x33363C));
		b.box(-0.05f, 0.3f, -0.08f, 0.05f, 0.34f, 0.08f, tex(0xF4C300));
		b.box(-0.07f, 0.17f, -0.05f, 0.07f, 0.2f, 0.07f, tex(0x24262B));
		b.box(-0.04f, 0.22f, 0.13f, 0.04f, 0.29f, 0.17f, flat(0x101216));
		b.box(-0.02f, 0.245f, 0.17f, 0.02f, 0.27f, 0.19f, flat(0x3B8CFF));
		b.paintOver(-0.09f, 0.28f, -0.12f, 0.09f, 0.3f, -0.1f, (x, y, z) -> true, flat(0xFF3A3A));
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				b.tube(sx * 0.07f, 0.25f, sz * 0.07f, 0.022f, sx * 0.19f, 0.26f, sz * 0.19f, 0.022f, tex(0x2A2C31));
				b.box(sx * 0.19f - 0.035f, 0.22f, sz * 0.19f - 0.035f, sx * 0.19f + 0.035f, 0.29f, sz * 0.19f + 0.035f, tex(0x1C1D21));
				b.box(sx * 0.19f - 0.025f, 0.29f, sz * 0.19f - 0.025f, sx * 0.19f + 0.025f, 0.31f, sz * 0.19f + 0.025f, flat(0xFF7A1A));
			}
		}
		m.body = m.addBlocky("body", null, 0, 0.25f, 0, b);
		java.util.List<Part> props = new java.util.ArrayList<>();
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				Voxels p = vox();
				float cx = sx * 0.19f;
				float cz = sz * 0.19f;
				p.box(cx - 0.12f, 0.32f, cz - 0.02f, cx + 0.12f, 0.335f, cz + 0.02f, flat(0xE8E8EE));
				p.box(cx - 0.02f, 0.32f, cz - 0.12f, cx + 0.02f, 0.335f, cz + 0.12f, flat(0xE8E8EE));
				p.paintOver(cx - 0.12f, 0.32f, cz - 0.12f, cx + 0.12f, 0.335f, cz + 0.12f, (x, y, z) -> Math.abs(x - cx) > 0.085f || Math.abs(z - cz) > 0.085f, flat(0xFF7A1A));
				props.add(m.addBlocky("prop", m.body, cx, 0.32f, cz, p));
			}
		}
		m.props = props.toArray(new Part[0]);
		return m;
	}

	// ---------- Тыква-нетопырь ----------

	private static ChibiPet pumpkin() {
		ChibiPet m = new ChibiPet();
		m.mode = ChibiPet.WINGS;
		Voxels b = vox();
		b.fill(-0.2f, 0.15f, -0.2f, 0.2f, 0.5f, 0.2f, (x, y, z) -> {
			float a = x / 0.18f;
			float c = (y - 0.32f) / 0.14f;
			float d = z / 0.17f;
			return a * a + c * c + d * d <= 1.0f;
		}, (x, y, z) -> {
			float ang = (float) Math.atan2(z, x);
			float rib = 0.88f + 0.14f * (0.5f + 0.5f * (float) Math.cos(ang * 8.0f));
			return Voxels.lum(0xF28A1E, rib * (0.93f + 0.14f * Voxels.grain(x, y, z, R)));
		});
		b.box(-0.025f, 0.45f, -0.025f, 0.025f, 0.52f, 0.025f, tex(0x4C8F2B));
		b.box(0.0f, 0.5f, -0.025f, 0.05f, 0.54f, 0.025f, tex(0x4C8F2B));
		float zf = 0.165f;
		for (int s = -1; s <= 1; s += 2) {
			final float cx = s * 0.07f;
			b.paintOver(cx - 0.05f, 0.32f, zf - 0.05f, cx + 0.05f, 0.42f, zf + 0.03f,
					(x, y, z) -> y >= 0.34f && y <= 0.41f && Math.abs(x - cx) <= 0.045f * (1.0f - (y - 0.34f) / 0.07f) + 0.005f, flat(0xFFE36A));
		}
		b.paintOver(-0.1f, 0.24f, zf - 0.05f, 0.1f, 0.32f, zf + 0.03f,
				(x, y, z) -> y >= 0.25f && y <= 0.3f && Math.abs(x) <= 0.09f && (((int) Math.floor(x / R)) % 2 == 0 || y < 0.275f), flat(0xFFE36A));
		m.body = m.addBlocky("body", null, 0, 0.3f, 0, b);
		m.head = m.body;
		for (int s = -1; s <= 1; s += 2) {
			final float side = s;
			Voxels w = vox();
			w.fill(-0.6f, 0.1f, -0.1f, 0.6f, 0.65f, 0.1f, (x, y, z) -> {
				float xa = x * side;
				if (xa < 0.15f || xa > 0.52f) return false;
				float k = (xa - 0.15f) / 0.37f;
				float top = 0.46f + 0.1f * k;
				float scallop = 0.045f * (float) Math.abs(Math.sin(k * 3.0f * Math.PI));
				float bottom = 0.4f - 0.18f * k + scallop;
				return y <= top && y >= bottom && Math.abs(z) <= 0.02f;
			}, (x, y, z) -> {
				float xa = Math.abs(x);
				boolean bone = Math.abs(y - (0.46f + 0.1f * (xa - 0.15f) / 0.37f)) < 0.02f || ((int) Math.floor(xa / R)) % 5 == 0;
				return Voxels.lum(bone ? 0x2A1B38 : 0x4F2E6B, 0.92f + 0.14f * Voxels.grain(x, y, z, R));
			});
			Part wing = m.addBlocky(s > 0 ? "wingL" : "wingR", m.body, s * 0.15f, 0.4f, 0.0f, w);
			if (s > 0) m.wingL = wing;
			else m.wingR = wing;
		}
		for (int s = -1; s <= 1; s += 2) {
			Voxels l = vox();
			l.box(s * 0.07f - 0.025f, 0.1f, 0.03f, s * 0.07f + 0.025f, 0.18f, 0.09f, flat(0x2A1B38));
			Part leg = m.addBlocky("leg", m.body, s * 0.07f, 0.18f, 0.05f, l);
			if (s > 0) m.legFL = leg;
			else m.legFR = leg;
		}
		return m;
	}
}
