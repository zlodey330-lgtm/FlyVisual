package com.flyvisual.util;

/**
 * Огонёк: живое пламя без чёрных граней. Раскалённое белое ядро, слои тёплого свечения,
 * языки огня, которые колышутся, и искры, поднимающиеся вверх. Светится само, ночью не темнеет.
 */
public final class PetWisp {
	private static final int CORE = 0xFFF6D8;
	private static final int YELLOW = 0xFFD24A;
	private static final int ORANGE = 0xFF8A1F;
	private static final int RED = 0xE0361A;

	private PetWisp() {
	}

	public static void draw(PetCtx c) {
		float t = c.time;
		float bob = 0.5f + PetCtx.sin(t * 2.4f) * 0.07f;
		float pulse = 1.0f + PetCtx.sin(t * 7.0f) * 0.05f + PetCtx.sin(t * 11.3f) * 0.03f;

		c.push();
		c.tr(0, bob, 0);

		// Свечение: вложенные повёрнутые кубы дают круглое мягкое гало
		float[] size = {0.34f, 0.27f, 0.21f, 0.16f};
		int[] col = {RED, ORANGE, YELLOW, CORE};
		float[] alpha = {0.12f, 0.2f, 0.4f, 0.95f};
		for (int i = 0; i < size.length; i++) {
			float s = size[i] * pulse;
			for (int k = 0; k < 3; k++) {
				c.push();
				c.ry(t * (22.0f + i * 14.0f) + k * 60.0f);
				c.rx(35.0f + k * 40.0f);
				c.rz(k * 25.0f + t * 10.0f);
				c.cube(0, 0, 0, s, s, s, col[i], alpha[i] / (i == size.length - 1 ? 1.0f : 1.4f), 1.0f);
				c.pop();
			}
		}

		// Языки пламени вокруг и сверху
		for (int i = 0; i < 7; i++) {
			float ang = i * (float) (Math.PI * 2 / 7) + t * 0.6f;
			float lean = 0.5f + 0.5f * PetCtx.sin(t * 5.0f + i * 1.9f);
			float baseR = 0.06f;
			float h = 0.16f + 0.1f * lean;
			c.push();
			c.tr((float) Math.cos(ang) * baseR, 0.03f, (float) Math.sin(ang) * baseR);
			c.rx(-(float) Math.sin(ang) * 25.0f + PetCtx.sin(t * 6.0f + i) * 8.0f);
			c.rz((float) Math.cos(ang) * 25.0f + PetCtx.sin(t * 5.3f + i * 2.0f) * 8.0f);
			for (int k = 0; k < 5; k++) {
				float f = k / 4.0f;
				float w = 0.07f * (1.0f - f * 0.85f);
				int cc = f < 0.35f ? YELLOW : (f < 0.7f ? ORANGE : RED);
				c.cube(0, h * f, 0, w, h / 5.0f * 1.3f, w, cc, 0.7f * (1.0f - f * 0.6f), 1.0f);
			}
			c.pop();
		}

		// Искры взлетают и гаснут
		for (int i = 0; i < 12; i++) {
			float life = (t * (0.35f + (i % 3) * 0.12f) + i * 0.173f) % 1.0f;
			float ang = i * 2.399f + life * 3.0f;
			float r = 0.08f + 0.1f * life;
			float x = (float) Math.cos(ang) * r;
			float z = (float) Math.sin(ang) * r;
			float y = 0.05f + life * 0.45f;
			float s = 0.022f * (1.0f - life * 0.7f);
			c.cube(x, y, z, s, s, s, life < 0.5f ? YELLOW : ORANGE, (1.0f - life) * 0.95f, 1.0f);
		}
		c.pop();
	}
}
