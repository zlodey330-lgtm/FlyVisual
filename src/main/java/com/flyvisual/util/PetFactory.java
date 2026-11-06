package com.flyvisual.util;

import com.flyvisual.util.PetModel.Part;
import com.flyvisual.util.Voxels.Paint;

/**
 * Строит модели питомцев. Все размеры в блоках, система координат: +Z вперёд, +Y вверх,
 * +X влево от питомца; корень на земле под серединой тела. Модели рисуются мелкими кубиками (1.25 см)
 * с шерстью, глазами, ушами, усами и анимированными лапами и хвостом.
 */
public final class PetFactory {
	static final float RES = 0.0125f;

	private PetFactory() {
	}

	// ---------- Помощники ----------

	/** Сетка, охватывающая точки (тройки x, y, z) с запасом m. */
	static Voxels around(float m, float... p) {
		float x0 = 1e9f;
		float y0 = 1e9f;
		float z0 = 1e9f;
		float x1 = -1e9f;
		float y1 = -1e9f;
		float z1 = -1e9f;
		for (int i = 0; i + 2 < p.length; i += 3) {
			x0 = Math.min(x0, p[i]);
			y0 = Math.min(y0, p[i + 1]);
			z0 = Math.min(z0, p[i + 2]);
			x1 = Math.max(x1, p[i]);
			y1 = Math.max(y1, p[i + 1]);
			z1 = Math.max(z1, p[i + 2]);
		}
		return new Voxels(RES, x0 - m, y0 - m, z0 - m, x1 + m, y1 + m, z1 + m);
	}

	static Paint solid(int rgb) {
		return (x, y, z) -> Voxels.lum(rgb, 0.93f + 0.14f * Voxels.grain(x, y, z, RES));
	}

	/**
	 * Полосатый (табби) мех: шерстинки, тёмные полосы поперёк, тёмная линия по хребту, светлый живот.
	 * bellyY: высота, ниже которой мех светлый.
	 */
	static Paint tabby(int base, int dark, int light, float bellyY, float soft, float strength, float freq, float seed) {
		return (x, y, z) -> {
			float fur = 0.9f + 0.12f * Voxels.grain(x, y, z, RES) + 0.07f * Voxels.noise(x * 45 + seed, y * 45, z * 45);
			float top = Voxels.smooth(bellyY, bellyY + 0.05f, y);
			float wob = Voxels.noise(x * 13 + seed, y * 13, z * 13);
			float band = 0.5f + 0.5f * (float) Math.sin(z * freq + wob * 5.0f + y * 16.0f);
			float stripe = Voxels.smooth(0.5f, 0.72f, band) * strength * top;
			float dorsal = (1.0f - Voxels.smooth(0.0f, 0.022f, Math.abs(x))) * top;
			int c = Voxels.mix(base, dark, Math.max(stripe, dorsal * 0.85f));
			c = Voxels.mix(c, light, 1.0f - Voxels.smooth(bellyY - soft, bellyY + soft, y));
			return Voxels.lum(c, fur);
		};
	}

	/** Глаз: тёмный ободок, радужка с градиентом, зрачок (щель или круг) и блик. */
	static void eye(Voxels v, float cx, float cy, float cz, float rx, float ry, float rz,
					int iris, int rim, int pupil, boolean slit, float highlightSide) {
		v.paintEllipsoid(cx, cy, cz, rx * 1.2f, ry * 1.2f, rz * 1.5f, (x, y, z) -> rim);
		v.paintEllipsoid(cx, cy, cz, rx, ry, rz * 1.4f, (x, y, z) ->
				Voxels.mix(Voxels.lum(iris, 1.15f), Voxels.lum(iris, 0.7f), Voxels.smooth(cy + ry, cy - ry, y)));
		if (slit) {
			v.paintEllipsoid(cx, cy, cz, rx * 0.3f, ry * 1.0f, rz * 1.6f, (x, y, z) -> pupil);
		} else {
			v.paintEllipsoid(cx, cy, cz, rx * 0.55f, ry * 0.6f, rz * 1.6f, (x, y, z) -> pupil);
		}
		v.paintEllipsoid(cx + highlightSide * rx * 0.45f, cy + ry * 0.5f, cz + rz * 0.4f, rx * 0.2f, ry * 0.22f, rz * 1.6f,
				(x, y, z) -> 0xFFFFFF);
	}

	/** Усы: тонкие линии из кубиков. */
	static void whiskers(Voxels v, float side, float x, float y, float z, int rgb, float length, int count) {
		for (int k = 0; k < count; k++) {
			float dy = (k - (count - 1) / 2.0f) * 0.14f;
			float dz = -0.12f - k * 0.04f;
			for (float t = 0.0f; t <= length; t += RES * 0.9f) {
				v.dot(x + side * t, y + dy * t - 0.004f * (t / length), z + dz * t, rgb);
			}
		}
	}

	/** Звено лапы: трубка от a до b. */
	static void limb(Voxels v, float ax, float ay, float az, float ra, float bx, float by, float bz, float rb, Paint paint) {
		v.tube(ax, ay, az, ra, bx, by, bz, rb, paint);
	}

	// ---------- Кошка ----------

	public static Quadruped cat() {
		return animal(0);
	}

	public static Quadruped husky() {
		return animal(1);
	}

	public static Quadruped fox() {
		return animal(2);
	}

	/** kind: 0 кошка, 1 хаски, 2 лиса. */
	private static Quadruped animal(final int kind) {
		Quadruped q = new Quadruped();
		q.tailStyle = kind;
		q.foreSwing = kind == 0 ? 30.0f : 34.0f;
		q.hindSwing = kind == 0 ? 26.0f : 30.0f;
		q.bob = 0.010f;
		q.gait = kind == 0 ? 1.0f : 0.9f;

		final int BASE = kind == 0 ? 0x9A8C7A : kind == 1 ? 0x858A93 : 0xD9702A;
		final int DARK = kind == 0 ? 0x4B4237 : kind == 1 ? 0x30333A : 0x2A1A12;
		final int LIGHT = kind == 0 ? 0xEBE2D2 : kind == 1 ? 0xF4F4F2 : 0xF7EFE3;
		final int PINK = kind == 0 ? 0xE9A5A5 : kind == 1 ? 0xD8C6C0 : 0xE7D2C0;
		final int NOSE = kind == 0 ? 0xD98686 : 0x141414;
		final int IRIS = kind == 0 ? 0x9CCB3C : kind == 1 ? 0x86BFF0 : 0xD9A21B;
		final int RIM = 0x1F1B16;

		Paint coat = kind == 0 ? tabby(BASE, DARK, LIGHT, 0.17f, 0.025f, 0.85f, 72.0f, 1.0f) : (x, y, z) -> {
			float fur = 0.9f + 0.12f * Voxels.grain(x, y, z, RES) + 0.08f * Voxels.noise(x * 40, y * 40, z * 40);
			float saddle = Voxels.smooth(0.2f, 0.25f, y) * (kind == 1 ? 0.8f : 0.0f);
			int c = Voxels.mix(BASE, DARK, saddle);
			c = Voxels.mix(c, LIGHT, 1.0f - Voxels.smooth(0.15f, 0.2f, y));
			c = Voxels.mix(c, LIGHT, Voxels.smooth(0.12f, 0.17f, z) * (1.0f - Voxels.smooth(0.2f, 0.26f, y)) * 0.9f);
			return Voxels.lum(c, fur);
		};

		// ----- Тело -----
		Voxels b = around(0.02f, -0.1f, 0.1f, -0.23f, 0.1f, 0.32f, 0.26f);
		b.ellipsoid(0, 0.205f, -0.115f, 0.07f, 0.075f, 0.09f, coat);
		b.ellipsoid(0, 0.205f, -0.01f, 0.068f, 0.073f, 0.12f, coat);
		b.ellipsoid(0, 0.215f, 0.095f, 0.074f, 0.082f, 0.095f, coat);
		b.ellipsoid(0, 0.245f, 0.075f, 0.058f, 0.04f, 0.085f, coat);
		b.tube(0, 0.235f, 0.15f, 0.058f, 0, 0.268f, 0.205f, 0.05f, coat);
		for (int s = -1; s <= 1; s += 2) {
			b.ellipsoid(s * 0.052f, 0.185f, -0.125f, 0.032f, 0.07f, 0.065f, coat);
			b.ellipsoid(s * 0.05f, 0.185f, 0.09f, 0.03f, 0.06f, 0.055f, coat);
		}
		// белая грудка и светлое «жабо»
		b.paintEllipsoid(0, 0.19f, 0.185f, 0.05f, 0.085f, 0.05f, (x, y, z) ->
				Voxels.lum(LIGHT, 0.96f + 0.08f * Voxels.grain(x, y, z, RES)));
		q.body = q.add("body", null, 0, 0.2f, 0, b);

		// ----- Голова -----
		float hx = 0.0f;
		float hy = 0.268f;
		float hz = 0.205f;
		Paint headCoat = kind == 0 ? tabby(BASE, DARK, LIGHT, 0.0f, 0.001f, 0.0f, 70.0f, 3.0f) : (x, y, z) -> {
			float fur = 0.9f + 0.12f * Voxels.grain(x, y, z, RES) + 0.08f * Voxels.noise(x * 40, y * 40, z * 40);
			int c = BASE;
			if (kind == 1) {
				c = Voxels.mix(c, DARK, 0.7f * Voxels.smooth(0.3f, 0.335f, y) * (1.0f - Voxels.smooth(0.28f, 0.33f, z)));
				c = Voxels.mix(c, LIGHT, Voxels.smooth(0.29f, 0.3f, z) * (1.0f - Voxels.smooth(0.292f, 0.31f, y)) + (1.0f - Voxels.smooth(0.262f, 0.272f, y)));
			} else {
				c = Voxels.mix(c, LIGHT, Voxels.smooth(0.275f, 0.29f, Math.abs(x) * 2.0f + (0.31f - y) * 0.9f) * 0.0f
						+ (1.0f - Voxels.smooth(0.272f, 0.292f, y)) + Voxels.smooth(0.02f, 0.045f, Math.abs(x)) * Voxels.smooth(0.27f, 0.3f, z) * (1.0f - Voxels.smooth(0.28f, 0.3f, y)));
			}
			return Voxels.lum(c, fur);
		};
		Voxels h = around(0.02f, -0.1f, 0.24f, 0.18f, 0.1f, 0.4f, 0.4f);
		h.ellipsoid(0, 0.3f, 0.255f, 0.06f, 0.052f, 0.055f, headCoat);
		for (int s = -1; s <= 1; s += 2) {
			h.ellipsoid(s * 0.043f, 0.287f, 0.262f, 0.028f, 0.03f, 0.035f, headCoat);
		}
		if (kind == 0) {
			h.ellipsoid(0, 0.284f, 0.308f, 0.03f, 0.024f, 0.03f, headCoat);
		} else {
			h.ellipsoid(0, 0.283f, 0.316f, 0.027f, 0.023f, 0.04f, headCoat);
			h.ellipsoid(0, 0.287f, 0.345f, 0.017f, 0.015f, 0.025f, headCoat);
		}
		h.ellipsoid(0, 0.268f, 0.3f, 0.02f, 0.012f, 0.022f, headCoat);
		// светлая мордочка и подбородок
		if (kind == 0) {
			h.paintEllipsoid(0, 0.281f, 0.318f, 0.032f, 0.026f, 0.03f, (x, y, z) -> Voxels.lum(LIGHT, 0.95f + 0.08f * Voxels.grain(x, y, z, RES)));
		}
		h.paintEllipsoid(0, 0.266f, 0.3f, 0.026f, 0.014f, 0.03f, (x, y, z) -> LIGHT);
		// «М» на лбу и полосы на щеках
		for (int i = -1; i <= 1 && kind == 0; i++) {
			h.paintEllipsoid(i * 0.014f, 0.338f, 0.25f, 0.004f, 0.016f, 0.03f, (x, y, z) -> DARK);
		}
		for (int s = -1; s <= 1 && kind == 0; s += 2) {
			h.paintEllipsoid(s * 0.05f, 0.292f, 0.285f, 0.012f, 0.004f, 0.025f, (x, y, z) -> DARK);
			h.paintEllipsoid(s * 0.05f, 0.278f, 0.285f, 0.012f, 0.004f, 0.025f, (x, y, z) -> DARK);
		}
		// нос, рот
		float nz = kind == 0 ? 0.337f : 0.368f;
		h.paintEllipsoid(0, kind == 0 ? 0.293f : 0.299f, nz, 0.012f, 0.009f, 0.011f, (x, y, z) -> NOSE);
		h.paintEllipsoid(0, 0.281f, nz - 0.001f, 0.003f, 0.012f, 0.012f, (x, y, z) -> 0x5A3A3A);
		for (int s = -1; s <= 1; s += 2) {
			h.paintEllipsoid(s * 0.011f, kind == 0 ? 0.272f : 0.274f, nz - 0.008f, 0.011f, 0.003f, 0.014f, (x, y, z) -> 0x5A3A3A);
		}
		// глаза
		for (int s = -1; s <= 1; s += 2) {
			eye(h, s * 0.03f, 0.31f, 0.298f, 0.0135f, 0.0125f, 0.008f, IRIS, RIM, 0x0A0A0A, kind != 1, s);
		}
		if (kind != 1) {
			whiskers(h, 1, 0.02f, 0.283f, nz - 0.015f, 0xF1ECE2, 0.06f, 3);
			whiskers(h, -1, -0.02f, 0.283f, nz - 0.015f, 0xF1ECE2, 0.06f, 3);
		}
		q.head = q.add("head", q.body, hx, hy, hz, h);

		// веки (рисуются при моргании)
		Voxels lids = around(0.02f, -0.04f, 0.3f, 0.28f, 0.04f, 0.32f, 0.32f);
		for (int s = -1; s <= 1; s += 2) {
			lids.ellipsoid(s * 0.03f, 0.31f, 0.3f, 0.0165f, 0.0155f, 0.011f, coat);
		}
		q.lidL = q.add("lidL", q.head, hx, hy, hz, lids);
		q.lidR = q.add("lidR", q.head, hx, hy, hz, lids);

		// уши
		q.earL = cat_ear(q, 1, BASE, kind == 2 ? DARK : (kind == 1 ? BASE : DARK), PINK, kind == 0 ? 0.054f : 0.075f, kind == 0 ? 0.029f : 0.03f);
		q.earR = cat_ear(q, -1, BASE, kind == 2 ? DARK : (kind == 1 ? BASE : DARK), PINK, kind == 0 ? 0.054f : 0.075f, kind == 0 ? 0.029f : 0.03f);

		// ----- Лапы -----
		for (int s = -1; s <= 1; s += 2) {
			Paint leg = (x, y, z) -> {
				float fur = 0.92f + 0.12f * Voxels.grain(x, y, z, RES);
				int c;
				if (kind == 0) {
					c = Voxels.mix(BASE, DARK, 0.55f * Voxels.smooth(0.55f, 0.75f, 0.5f + 0.5f * (float) Math.sin(y * 75.0f)));
					c = Voxels.mix(c, LIGHT, 1.0f - Voxels.smooth(0.03f, 0.06f, y));
				} else if (kind == 1) {
					c = Voxels.mix(BASE, LIGHT, 1.0f - Voxels.smooth(0.08f, 0.13f, y));
				} else {
					c = Voxels.mix(BASE, DARK, 1.0f - Voxels.smooth(0.06f, 0.11f, y));
					c = Voxels.mix(c, LIGHT, Voxels.smooth(0.14f, 0.17f, y) * 0.0f);
				}
				return Voxels.lum(c, fur);
			};
			// передняя
			float jx = s * 0.048f;
			Voxels fu = around(0.03f, jx, 0.17f, 0.09f, jx, 0.095f, 0.085f);
			limb(fu, jx, 0.17f, 0.09f, 0.034f, jx, 0.095f, 0.085f, 0.025f, leg);
			Part up = q.add("fUp", q.body, jx, 0.17f, 0.09f, fu);
			Voxels fl = around(0.03f, jx, 0.095f, 0.085f, jx, 0.0f, 0.12f);
			limb(fl, jx, 0.095f, 0.085f, 0.024f, jx, 0.03f, 0.095f, 0.018f, leg);
			fl.ellipsoid(jx, 0.014f, 0.108f, 0.021f, 0.016f, 0.03f, leg);
			Part low = q.add("fLow", up, jx, 0.095f, 0.085f, fl);
			if (s > 0) {
				q.fUpL = up;
				q.fLowL = low;
			} else {
				q.fUpR = up;
				q.fLowR = low;
			}

			// задняя
			float hxx = s * 0.055f;
			Voxels tu = around(0.03f, hxx, 0.18f, -0.115f, hxx, 0.1f, -0.07f);
			limb(tu, hxx, 0.18f, -0.115f, 0.043f, hxx, 0.1f, -0.07f, 0.028f, leg);
			Part thigh = q.add("hUp", q.body, hxx, 0.18f, -0.115f, tu);
			Voxels sl = around(0.03f, hxx, 0.1f, -0.07f, hxx, 0.0f, -0.14f);
			limb(sl, hxx, 0.1f, -0.07f, 0.027f, hxx, 0.05f, -0.135f, 0.018f, leg);
			limb(sl, hxx, 0.05f, -0.135f, 0.018f, hxx, 0.022f, -0.12f, 0.016f, leg);
			sl.ellipsoid(hxx, 0.013f, -0.1f, 0.02f, 0.015f, 0.032f, leg);
			Part shin = q.add("hLow", thigh, hxx, 0.1f, -0.07f, sl);
			if (s > 0) {
				q.hUpL = thigh;
				q.hLowL = shin;
			} else {
				q.hUpR = thigh;
				q.hLowR = shin;
			}
		}

		// ----- Хвост -----
		int n = kind == 0 ? 5 : 6;
		q.tail = new Part[n];
		Part prev = q.body;
		float z0 = -0.19f;
		float seg = kind == 0 ? 0.056f : 0.058f;
		for (int i = 0; i < n; i++) {
			final int idx = i;
			Paint tailPaint = (x, y, z) -> {
				float fur = 0.92f + 0.12f * Voxels.grain(x, y, z, RES);
				float ring = Voxels.smooth(0.5f, 0.72f, 0.5f + 0.5f * (float) Math.sin(z * 95.0f));
				int c = Voxels.mix(BASE, DARK, 0.7f * ring);
				if (idx == n - 1) c = Voxels.mix(c, DARK, 0.85f);
				if (kind == 1) {
					c = Voxels.mix(BASE, LIGHT, 0.45f * (1.0f - Voxels.smooth(0.17f, 0.25f, y)) + 0.0f);
				} else if (kind == 2) {
					c = idx >= n - 2 ? LIGHT : (idx == n - 3 ? Voxels.mix(BASE, LIGHT, 0.4f) : BASE);
					if (idx == n - 3) c = Voxels.mix(c, DARK, 0.15f);
				}
				return Voxels.lum(c, fur);
			};
			float za = z0 - i * seg;
			float zb = za - seg;
			float ra = kind == 0 ? 0.024f - i * 0.0022f : 0.026f + 0.016f * (float) Math.sin(Math.PI * (i + 0.4f) / n);
			float rb = kind == 0 ? ra - 0.002f : 0.026f + 0.016f * (float) Math.sin(Math.PI * (i + 1.4f) / n);
			Voxels tv = around(0.03f, 0, 0.2f, za, 0, 0.2f, zb);
			tv.tube(0, 0.2f, za + 0.004f, ra, 0, 0.2f, zb, rb, tailPaint);
			if (i == n - 1) {
				tv.ellipsoid(0, 0.2f, zb, rb, rb, rb * 1.1f, tailPaint);
			}
			prev = q.add("tail" + i, prev, 0, 0.2f, za, tv);
			q.tail[i] = prev;
		}
		return q;
	}

	private static Part cat_ear(Quadruped q, int s, int base, int dark, int pink, float height, float half) {
		float bx = s * 0.034f;
		float by = 0.343f;
		float bz = 0.243f;
		Voxels e = around(0.03f, bx, by, bz, bx + s * 0.02f, by + height, bz);
		e.fill(bx - 0.06f, by - 0.01f, bz - 0.03f, bx + 0.06f, by + height + 0.01f, bz + 0.03f, (x, y, z) -> {
			float yy = y - by;
			if (yy < -0.006f || yy > height) return false;
			float u = Math.max(0.0f, yy) / height;
			float cx = bx + s * 0.013f * u;
			float w = half * (1.0f - u * 0.93f) + 0.003f;
			return Math.abs(x - cx) <= w && Math.abs(z - bz) <= 0.008f * (1.0f - 0.5f * u) + 0.002f;
		}, (x, y, z) -> {
			float yy = y - by;
			float u = Math.max(0.0f, yy) / height;
			float tip = Voxels.smooth(0.7f, 1.0f, u);
			return Voxels.lum(Voxels.mix(base, dark, tip * 0.8f), 0.93f + 0.12f * Voxels.grain(x, y, z, RES));
		});
		// розовая внутренняя часть спереди
		e.paintOver(bx - 0.05f, by, bz, bx + 0.05f, by + height, bz + 0.03f, (x, y, z) -> {
			float yy = y - by;
			float u = Math.max(0.0f, yy) / height;
			float cx = bx + s * 0.013f * u;
			float w = half * (1.0f - u * 0.93f) * 0.62f;
			return z > bz + 0.002f && yy > 0.006f && yy < height * 0.8f && Math.abs(x - cx) <= w;
		}, (x, y, z) -> Voxels.lum(pink, 0.95f + 0.1f * Voxels.grain(x, y, z, RES)));
		return q.add("ear", q.head, bx, by, bz, e);
	}
}
