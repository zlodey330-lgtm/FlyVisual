package com.flyvisual.util;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/**
 * Свои модели питомцев из цветных кубиков (не мобы из Minecraft). Рисуются через Draw3d,
 * поэтому почти не нагружают игру: это несколько десятков кубов за кадр, без сущностей и ИИ.
 * Система координат модели: +Z = вперёд, +Y = вверх, +X = влево от питомца.
 */
public final class PetRenderer {
	public static final String[] NAMES = {"Кошка", "Собака", "Лисичка", "Дракончик", "Призрак", "Огонёк"};
	public static final boolean[] FLYING = {false, false, false, true, true, true};

	private static final int MAIN = 0;
	private static final int LIGHT = 1;
	private static final int DARK = 2;
	private static final int EYE = 3;
	private static final int NOSE = 4;

	private static final int[][] PALETTES = {
			{0xE8944A, 0xFFF1DC, 0x9A5A28, 0x5EE07A, 0xFF9AAE},   // кошка
			{0xD9A15B, 0xF6E2BC, 0x6B4423, 0x2A1A10, 0x262626},   // собака
			{0xF07A22, 0xFFFFFF, 0x3A2418, 0x2A1A10, 0x1E1E1E},   // лисичка
			{0x5BD36B, 0xE9F7B0, 0x2E8B4A, 0xFFE14D, 0xFFE08A},   // дракончик
			{0xF2F4FF, 0xFFFFFF, 0xB9C2E8, 0x1A1A2E, 0xFFB4C8},   // призрак
			{0xFFD65C, 0xFFF6C8, 0xFF7A1F, 0xFFFFFF, 0xFF7A1F}    // огонёк
	};

	private record Quad(float bodyW, float bodyH, float bodyL, float legH, float legW, float head,
						float snout, int ear, int tail) {
	}

	// Состояние текущей отрисовки (рисование идёт в одном потоке)
	private static MatrixStack ms;
	private static Draw3d d;
	private static float light = 1.0f;
	private static boolean unlit;
	private static float time;
	private static float walk;
	private static float moving;
	private static float hy;
	private static float hz;

	private PetRenderer() {
	}

	/** Рисует питомца в начале координат текущей матрицы. */
	public static void draw(int kind, MatrixStack matrices, Draw3d draw, float lightFactor, float seconds,
							float walkPhase, float movingAmount, boolean custom, float hue) {
		ms = matrices;
		d = draw;
		light = lightFactor;
		time = seconds;
		walk = walkPhase;
		moving = movingAmount;
		unlit = false;

		int[] p = PALETTES[Math.max(0, Math.min(PALETTES.length - 1, kind))].clone();
		if (custom) {
			p[MAIN] = ColorUtil.hsv(hue, 0.55f, 0.95f);
			p[LIGHT] = ColorUtil.hsv(hue, 0.15f, 1.0f);
			p[DARK] = ColorUtil.hsv(hue, 0.75f, 0.5f);
		}

		switch (kind) {
			case 0 -> {
				shadow(0.4f);
				quadruped(new Quad(0.22f, 0.2f, 0.4f, 0.17f, 0.065f, 0.22f, 0.0f, 0, 0), p, true);
			}
			case 1 -> {
				shadow(0.5f);
				quadruped(new Quad(0.26f, 0.24f, 0.46f, 0.2f, 0.08f, 0.25f, 0.12f, 2, 1), p, false);
			}
			case 2 -> {
				shadow(0.46f);
				quadruped(new Quad(0.22f, 0.21f, 0.42f, 0.2f, 0.06f, 0.22f, 0.11f, 1, 2), p, false);
			}
			case 3 -> dragon(p);
			case 4 -> ghost(p);
			default -> wisp(p);
		}
	}

	// ---------- Помощники ----------

	private static void cube(float cx, float cy, float cz, float sx, float sy, float sz, int rgb) {
		cubeA(cx, cy, cz, sx, sy, sz, rgb, 1.0f);
	}

	private static void cubeA(float cx, float cy, float cz, float sx, float sy, float sz, int rgb, float a) {
		float l = unlit ? 1.0f : light;
		d.cube(ms.peek().getPositionMatrix(), cx - sx / 2, cy - sy / 2, cz - sz / 2, cx + sx / 2, cy + sy / 2, cz + sz / 2,
				ColorUtil.red(rgb) * l, ColorUtil.green(rgb) * l, ColorUtil.blue(rgb) * l, a);
	}

	private static void push() {
		ms.push();
	}

	private static void pop() {
		ms.pop();
	}

	private static void tr(float x, float y, float z) {
		ms.translate(x, y, z);
	}

	private static void rx(float deg) {
		ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(deg));
	}

	private static void ry(float deg) {
		ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(deg));
	}

	private static void rz(float deg) {
		ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(deg));
	}

	private static float sin(float v) {
		return (float) Math.sin(v);
	}

	/** Мягкая тень на земле под питомцем. */
	private static void shadow(float size) {
		boolean prev = unlit;
		unlit = true;
		cubeA(0, 0.012f, 0, size * 0.8f, 0.004f, size, 0x000000, 0.22f);
		cubeA(0, 0.012f, 0, size * 0.55f, 0.005f, size * 0.7f, 0x000000, 0.16f);
		unlit = prev;
	}

	// ---------- Четвероногие: кошка, собака, лисичка ----------

	private static void leg(float x, float z, Quad q, float phase, int main, int paw) {
		push();
		tr(x, q.legH, z);
		rx(sin(walk + phase) * 38.0f * moving);
		cube(0, -q.legH / 2, 0, q.legW, q.legH, q.legW, main);
		cube(0, -q.legH + 0.025f, 0.012f, q.legW + 0.012f, 0.05f, q.legW + 0.03f, paw);
		pop();
	}

	private static void quadruped(Quad q, int[] p, boolean cat) {
		float bob = (float) Math.abs(Math.sin(walk)) * 0.025f * moving + sin(time * 2.2f) * 0.004f;
		float lh = q.legH;
		float bw = q.bodyW;
		float bh = q.bodyH;
		float bl = q.bodyL;
		int pawColor = q.tail == 2 ? p[DARK] : p[LIGHT];

		push();
		tr(0, bob, 0);

		float lx = bw / 2 - q.legW / 2 - 0.005f;
		float lz = bl / 2 - q.legW / 2 - 0.02f;
		leg(lx, lz, q, 0.0f, p[MAIN], pawColor);
		leg(-lx, lz, q, (float) Math.PI, p[MAIN], pawColor);
		leg(lx, -lz, q, (float) Math.PI, p[MAIN], pawColor);
		leg(-lx, -lz, q, 0.0f, p[MAIN], pawColor);

		// Тело и светлый живот
		cube(0, lh + bh / 2, 0, bw, bh, bl, p[MAIN]);
		cube(0, lh + 0.026f, 0.01f, bw * 0.74f, 0.064f, bl * 0.72f, p[LIGHT]);
		cube(0, lh + bh * 0.5f, bl / 2 - 0.01f, bw * 0.7f, bh * 0.62f, 0.03f, p[LIGHT]);
		if (cat) {
			// Полоски на спине, как у рыжего кота
			for (int i = -1; i <= 1; i++) {
				cube(0, lh + bh + 0.004f, i * 0.1f, bw * 0.62f, 0.008f, 0.035f, p[DARK]);
			}
		} else {
			cube(0.05f, lh + bh + 0.004f, 0.04f, 0.11f, 0.008f, 0.12f, p[DARK]);
		}

		// Голова
		float head = q.head;
		hz = head * 0.42f;
		hy = head * 0.1f;
		push();
		tr(0, lh + bh * 0.78f, bl / 2 - 0.02f);
		rx(sin(time * 1.3f) * 2.5f + (float) Math.abs(Math.sin(walk)) * 3.0f * moving);
		ry(sin(time * 0.9f) * 8.0f);

		cube(0, hy, hz, head * 1.1f, head, head, p[MAIN]);
		float front = hz + head / 2;

		// Глаза с бликами
		for (int s = -1; s <= 1; s += 2) {
			cube(s * head * 0.26f, hy + head * 0.08f, front + 0.004f, head * 0.2f, head * 0.24f, 0.012f, p[EYE]);
			cube(s * head * 0.26f + head * 0.04f, hy + head * 0.14f, front + 0.01f, head * 0.07f, head * 0.08f, 0.012f, 0xFFFFFF);
		}

		if (q.snout > 0.0f) {
			cube(0, hy - head * 0.2f, front + q.snout / 2 - 0.01f, head * 0.52f, head * 0.36f, q.snout, p[LIGHT]);
			cube(0, hy - head * 0.1f, front + q.snout, head * 0.2f, head * 0.14f, 0.03f, p[NOSE]);
			if (q.ear == 2) {
				// Собачий язычок, когда бежит
				float tongue = 0.025f + 0.05f * moving;
				cube(0, hy - head * 0.38f - tongue / 2, front + q.snout * 0.55f, head * 0.16f, tongue, head * 0.2f, 0xFF7A94);
			}
		} else {
			cube(0, hy - head * 0.2f, front + 0.004f, head * 0.4f, head * 0.22f, 0.012f, p[LIGHT]);
			cube(0, hy - head * 0.08f, front + 0.012f, head * 0.1f, head * 0.07f, 0.012f, p[NOSE]);
			for (int s = -1; s <= 1; s += 2) {
				for (int i = 0; i < 2; i++) {
					push();
					tr(s * head * 0.66f, hy - head * 0.2f + (i - 0.5f) * 0.03f, front - 0.02f);
					rz(s * (i == 0 ? 8 : -8));
					cube(0, 0, 0, 0.1f, 0.008f, 0.006f, p[LIGHT]);
					pop();
				}
			}
		}

		ears(q.ear, head, p);
		pop();

		tail(q, p);
		pop();
	}

	private static void ears(int type, float head, int[] p) {
		for (int s = -1; s <= 1; s += 2) {
			push();
			switch (type) {
				case 0 -> {
					tr(s * head * 0.3f, hy + head / 2, hz - head * 0.05f);
					rz(-s * 8.0f);
					cube(0, 0.04f, 0, head * 0.32f, 0.08f, 0.05f, p[MAIN]);
					cube(0, 0.1f, 0, head * 0.18f, 0.05f, 0.04f, p[MAIN]);
					cube(0, 0.055f, 0.026f, head * 0.16f, 0.07f, 0.008f, p[NOSE]);
				}
				case 1 -> {
					tr(s * head * 0.3f, hy + head / 2, hz - head * 0.05f);
					rz(-s * 6.0f);
					cube(0, 0.05f, 0, head * 0.34f, 0.1f, 0.05f, p[MAIN]);
					cube(0, 0.14f, 0, head * 0.2f, 0.08f, 0.045f, p[DARK]);
					cube(0, 0.06f, 0.026f, head * 0.16f, 0.08f, 0.008f, p[DARK]);
				}
				default -> {
					tr(s * head * 0.58f, hy + head * 0.4f, hz - 0.02f);
					rz(s * (18.0f + sin(time * 2.0f + s) * 3.0f + (float) Math.abs(Math.sin(walk)) * 8.0f * moving));
					cube(s * 0.02f, -0.07f, 0, 0.05f, 0.15f, 0.1f, p[DARK]);
				}
			}
			pop();
		}
	}

	private static void tail(Quad q, int[] p) {
		float lh = q.legH;
		float bl = q.bodyL;
		float bh = q.bodyH;
		int segments;
		float thick;
		float length;
		float lift;
		float wagSpeed;
		float wagAmount;
		switch (q.tail) {
			case 1 -> {
				segments = 2;
				thick = 0.06f;
				length = 0.1f;
				lift = 50.0f;
				wagSpeed = 10.0f;
				wagAmount = 22.0f + 18.0f * moving;
			}
			case 2 -> {
				segments = 3;
				thick = 0.12f;
				length = 0.12f;
				lift = 12.0f;
				wagSpeed = 2.4f;
				wagAmount = 10.0f + 12.0f * moving;
			}
			default -> {
				segments = 4;
				thick = 0.05f;
				length = 0.09f;
				lift = 20.0f;
				wagSpeed = 2.6f;
				wagAmount = 14.0f;
			}
		}

		push();
		tr(0, lh + bh * 0.62f, -bl / 2 + 0.01f);
		for (int i = 0; i < segments; i++) {
			push();
			rx(lift + (q.tail == 0 ? 6.0f : 0.0f));
			ry(sin(time * wagSpeed + i * 0.6f) * wagAmount / segments * 1.6f);
			float t = q.tail == 2 ? thick * (1.0f + 0.35f * (float) Math.sin(Math.PI * (i + 0.5f) / segments)) : thick;
			int color = (i == segments - 1 && q.tail == 2) ? p[LIGHT] : (i == segments - 1 && q.tail == 0 ? p[DARK] : p[MAIN]);
			cube(0, 0, -length / 2, t, t, length, color);
			tr(0, 0, -length);
		}
		for (int i = 0; i < segments; i++) {
			pop();
		}
		pop();
	}

	// ---------- Дракончик ----------

	private static void dragon(int[] p) {
		float flap = sin(time * 7.5f);
		float bob = sin(time * 2.0f) * 0.02f;

		push();
		tr(0, 0.28f + bob, 0);
		rx(sin(time * 2.0f) * 2.0f - 3.0f * moving);

		cube(0, 0, 0, 0.22f, 0.2f, 0.4f, p[MAIN]);
		cube(0, -0.082f, 0.01f, 0.16f, 0.044f, 0.34f, p[LIGHT]);
		for (int i = 0; i < 4; i++) {
			cube(0, 0.108f, 0.14f - i * 0.09f, 0.04f, 0.04f, 0.05f, p[DARK]);
		}

		// Голова
		push();
		tr(0, 0.1f, 0.22f);
		rx(sin(time * 1.4f) * 3.0f - 8.0f);
		ry(sin(time * 0.9f) * 8.0f);
		cube(0, 0.04f, 0.07f, 0.2f, 0.17f, 0.18f, p[MAIN]);
		cube(0, 0.0f, 0.19f, 0.12f, 0.08f, 0.1f, p[LIGHT]);
		cube(0, 0.025f, 0.245f, 0.1f, 0.03f, 0.02f, p[NOSE]);
		for (int s = -1; s <= 1; s += 2) {
			cube(s * 0.065f, 0.075f, 0.163f, 0.045f, 0.055f, 0.014f, p[EYE]);
			cube(s * 0.065f, 0.075f, 0.172f, 0.02f, 0.04f, 0.012f, 0x1A1A1A);
			push();
			tr(s * 0.07f, 0.13f, 0.03f);
			rz(-s * 14.0f);
			rx(-24.0f);
			cube(0, 0.04f, 0, 0.035f, 0.09f, 0.035f, p[NOSE]);
			pop();
		}
		pop();

		// Крылья
		for (int s = -1; s <= 1; s += 2) {
			push();
			tr(s * 0.09f, 0.09f, -0.02f);
			rz(s * (20.0f + flap * 38.0f));
			cube(s * 0.24f, 0.0f, 0.0f, 0.46f, 0.025f, 0.05f, p[DARK]);
			cube(s * 0.22f, -0.004f, -0.09f, 0.4f, 0.012f, 0.16f, p[MAIN]);
			cube(s * 0.4f, -0.004f, -0.06f, 0.14f, 0.012f, 0.1f, p[LIGHT]);
			pop();
		}

		// Лапки
		for (int s = -1; s <= 1; s += 2) {
			cube(s * 0.07f, -0.14f, 0.1f, 0.05f, 0.09f, 0.05f, p[MAIN]);
			cube(s * 0.07f, -0.14f, -0.1f, 0.05f, 0.09f, 0.05f, p[MAIN]);
		}

		// Хвост волной, на конце шип
		push();
		tr(0, 0.0f, -0.2f);
		int segments = 5;
		for (int i = 0; i < segments; i++) {
			push();
			ry(sin(time * 3.0f + i * 0.7f) * 14.0f);
			rx(-4.0f);
			float t = 0.11f - i * 0.016f;
			cube(0, 0, -0.06f, t, t, 0.12f, p[MAIN]);
			tr(0, 0, -0.12f);
		}
		cube(0, 0, -0.05f, 0.06f, 0.06f, 0.08f, p[NOSE]);
		for (int i = 0; i < segments; i++) {
			pop();
		}
		pop();

		pop();
	}

	// ---------- Призрак ----------

	private static void ghost(int[] p) {
		unlit = true;
		float hover = 0.5f + sin(time * 2.0f) * 0.07f;
		push();
		tr(0, hover, 0);
		rx(moving * 8.0f);
		float a = 0.92f;

		cubeA(0, 0.06f, 0, 0.34f, 0.34f, 0.3f, p[MAIN], a);
		cubeA(0, 0.25f, 0, 0.26f, 0.06f, 0.24f, p[MAIN], a);
		cubeA(0, 0.3f, 0, 0.16f, 0.04f, 0.14f, p[LIGHT], a);
		// Рваный край снизу
		for (int ix = -1; ix <= 1; ix++) {
			for (int iz = -1; iz <= 1; iz++) {
				float wave = sin(time * 3.0f + ix * 1.3f + iz * 0.9f) * 0.03f;
				cubeA(ix * 0.11f, -0.16f + wave, iz * 0.1f, 0.1f, 0.12f, 0.1f, p[LIGHT], a);
			}
		}
		// Ручки
		for (int s = -1; s <= 1; s += 2) {
			push();
			tr(s * 0.19f, 0.06f, 0.02f);
			rz(s * (-25.0f + sin(time * 3.0f + s) * 12.0f));
			cubeA(s * 0.04f, -0.04f, 0, 0.07f, 0.12f, 0.07f, p[MAIN], a);
			pop();
		}
		// Лицо
		for (int s = -1; s <= 1; s += 2) {
			cube(s * 0.07f, 0.1f, 0.152f, 0.07f, 0.1f, 0.012f, p[EYE]);
			cube(s * 0.07f + 0.012f, 0.125f, 0.16f, 0.025f, 0.03f, 0.012f, 0xFFFFFF);
			cube(s * 0.13f, 0.03f, 0.152f, 0.06f, 0.035f, 0.012f, p[NOSE]);
		}
		cube(0, 0.02f, 0.152f, 0.06f, 0.04f, 0.012f, p[DARK]);
		pop();
		unlit = false;
	}

	// ---------- Огонёк ----------

	private static void wisp(int[] p) {
		unlit = true;
		float bob = 0.5f + sin(time * 2.4f) * 0.08f;
		push();
		tr(0, bob, 0);

		// Мерцающая оболочка, ядро, вращающиеся искры
		push();
		ry(time * 50.0f);
		rx(35.0f);
		cubeA(0, 0, 0, 0.3f, 0.3f, 0.3f, p[DARK], 0.32f);
		pop();
		push();
		ry(-time * 90.0f);
		rx(35.0f);
		cubeA(0, 0, 0, 0.19f, 0.19f, 0.19f, p[MAIN], 0.95f);
		pop();
		cube(0, 0, 0, 0.1f, 0.1f, 0.1f, p[LIGHT]);

		for (int i = 0; i < 4; i++) {
			float ang = time * 2.2f + i * (float) (Math.PI / 2);
			cube((float) Math.cos(ang) * 0.27f, sin(time * 3.0f + i) * 0.06f, (float) Math.sin(ang) * 0.27f, 0.05f, 0.05f, 0.05f, p[MAIN]);
		}

		// Язычки пламени сверху
		for (int i = 0; i < 3; i++) {
			float flick = 0.7f + 0.3f * sin(time * 9.0f + i * 2.1f);
			float h = (0.14f - i * 0.03f) * flick;
			cubeA((i - 1) * 0.05f, 0.17f + h / 2, 0, 0.07f - i * 0.01f, h, 0.07f - i * 0.01f, p[DARK], 0.8f);
		}
		pop();
		unlit = false;
	}
}
