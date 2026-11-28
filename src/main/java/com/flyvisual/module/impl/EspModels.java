package com.flyvisual.module.impl;

/**
 * Сцены TargetESP: души, шляпа, гироскоп, кристалл, змей, сфера, воронка.
 * Все координаты — относительно камеры; (x,y,z) — точка у ног цели.
 */
final class EspModels {
	private EspModels() {
	}

	static final float TAU = (float) (Math.PI * 2.0);

	/** Параметры кадра. */
	static final class P {
		float x;
		float y;
		float z;
		/** Высота и ширина цели. */
		float h;
		float w;
		/** Время анимации, секунды. */
		float t;
		/** Общая прозрачность (плавное появление). */
		float a;
		/** Размер деталей. */
		float sc = 1.0f;
		/** Яркость свечения. */
		float gl = 1.0f;
		float[] c1 = {0.4f, 0.7f, 1.0f};
		float[] c2 = {0.7f, 0.5f, 1.0f};
		float[] cw = {0.9f, 0.96f, 1.0f};
	}

	static float sin(float v) {
		return (float) Math.sin(v);
	}

	static float cos(float v) {
		return (float) Math.cos(v);
	}

	private static float frac(float v) {
		return v - (float) Math.floor(v);
	}

	private static float[] mix(float[] a, float[] b, float t) {
		return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
	}

	private static final float[] HU = {1.0f, 0.0f, 0.0f};
	private static final float[] HV = {0.0f, 0.0f, 1.0f};

	/** Горизонтальный базис, наклонённый вокруг X на tilt и повёрнутый вокруг Y на spin. */
	private static float[][] basis(float tilt, float spin) {
		float ct = cos(tilt);
		float st = sin(tilt);
		float cs = cos(spin);
		float ss = sin(spin);
		float[][] out = new float[2][];
		float[][] src = {HU, HV};
		for (int i = 0; i < 2; i++) {
			float x = src[i][0];
			float y = src[i][1] * ct - src[i][2] * st;
			float z = src[i][1] * st + src[i][2] * ct;
			out[i] = new float[]{x * cs + z * ss, y, -x * ss + z * cs};
		}
		return out;
	}

	// ---------- Души ----------

	static void souls(EspGfx g, P p) {
		int n = 6;
		float R = p.w * 0.5f + 0.48f;
		float pulse = 0.85f + 0.15f * sin(p.t * 3.0f);
		int pts = 20;
		float[] x = new float[pts];
		float[] y = new float[pts];
		float[] z = new float[pts];
		float[] w = new float[pts];
		float[] a = new float[pts];
		for (int k = 0; k < n; k++) {
			float spd = 0.85f + 0.14f * (k % 3);
			float off = k * TAU / n;
			for (int j = 0; j < pts; j++) {
				float tau = p.t - j * 0.038f;
				float th = tau * spd + off;
				float rho = R * (1.0f + 0.16f * sin(tau * 1.3f + k * 1.7f));
				float yy = p.h * (0.52f + 0.42f * sin(tau * 0.75f + k * 2.1f)) + 0.06f * sin(tau * 4.0f + k);
				x[j] = p.x + cos(th) * rho;
				z[j] = p.z + sin(th) * rho;
				y[j] = p.y + yy;
				float fade = 1.0f - j / (pts - 1.0f);
				w[j] = (0.01f + 0.07f * (float) Math.pow(fade, 1.2)) * p.sc;
				a[j] = 0.75f * (float) Math.pow(fade, 1.5) * p.a * p.gl;
			}
			g.ribbon(x, y, z, w, a, pts, p.c1);
			float hx = x[0];
			float hy = y[0];
			float hz = z[0];
			g.glow(hx, hy, hz, 0.5f * p.sc, p.c1, 0.14f * p.gl * p.a * pulse);
			g.glow(hx, hy, hz, 0.22f * p.sc, p.c1, 0.5f * p.gl * p.a * pulse);
			g.glow(hx, hy, hz, 0.1f * p.sc, p.cw, 0.55f * p.a);
			g.sphere(hx, hy, hz, 0.052f * p.sc, p.cw, 0.95f * p.a, 5, 8, 0.45f);
			// мерцающие искорки в хвосте
			for (int s = 1; s <= 3; s++) {
				int j = s * 5;
				float tw = 0.5f + 0.5f * sin(p.t * 7.0f + k * 2.3f + s * 1.9f);
				g.glow(x[j], y[j] + 0.03f * sin(p.t * 3.0f + s), z[j], 0.045f * p.sc, p.cw, 0.5f * tw * p.a * (1.0f - j / (float) pts));
			}
			// глазки: душа смотрит на тебя
			float[] r = EspGfx.camRight(hx, hy, hz);
			float ex = 0.02f * p.sc;
			float[] dark = {0.02f, 0.03f, 0.06f};
			g.dot(hx - r[0] * ex, hy + 0.006f * p.sc, hz - r[2] * ex, 0.0085f * p.sc, dark, 0.85f * p.a, 0.052f * p.sc);
			g.dot(hx + r[0] * ex, hy + 0.006f * p.sc, hz + r[2] * ex, 0.0085f * p.sc, dark, 0.85f * p.a, 0.052f * p.sc);
		}
		g.glow(p.x, p.y + p.h * 0.5f, p.z, p.w * 1.3f + 0.3f, p.c1, 0.05f * p.gl * p.a);
		// кольца на земле
		g.ringTube(p.x, p.y + 0.03f, p.z, HU, HV, R * 1.02f, 0.012f * p.sc, 44, 4, p.c1, 0.6f * p.a * p.gl, frac(p.t * 0.28f), 0.22f);
		g.ringTube(p.x, p.y + 0.03f, p.z, HU, new float[]{0.0f, 0.0f, -1.0f}, R * 0.82f, 0.009f * p.sc, 36, 4, p.c2, 0.35f * p.a * p.gl,
				frac(p.t * 0.4f), 0.2f);
	}

	// ---------- Шляпа ----------

	static void hat(EspGfx g, P p) {
		float baseY = p.y + p.h + 0.08f + 0.02f * sin(p.t * 2.0f);
		float height = 0.36f * p.sc + 0.1f;
		float R = (0.42f + 0.25f * p.w) * p.sc + 0.06f;
		int n = 40;
		float[] apex = {p.x, baseY + height, p.z};
		float[][] edge = new float[n + 1][];
		for (int i = 0; i <= n; i++) {
			float th = TAU * (i % n) / n + p.t * 0.9f;
			edge[i] = new float[]{p.x + cos(th) * R, baseY, p.z + sin(th) * R};
		}
		for (int i = 0; i < n; i++) {
			float th0 = TAU * i / n + p.t * 0.9f;
			float th1 = TAU * (i + 1) / n + p.t * 0.9f;
			float k0 = 0.5f + 0.5f * sin(th0 * 3.0f - p.t * 3.0f);
			float k1 = 0.5f + 0.5f * sin(th1 * 3.0f - p.t * 3.0f);
			float[] ca = {p.c1[0], p.c1[1], p.c1[2], 0.7f * p.a * p.gl};
			float[] ce0 = {p.c1[0], p.c1[1], p.c1[2], (0.10f + 0.16f * k0) * p.a * p.gl};
			float[] ce1 = {p.c1[0], p.c1[1], p.c1[2], (0.10f + 0.16f * k1) * p.a * p.gl};
			g.quadAdd(apex, edge[i], edge[i + 1], apex, ca, ce0, ce1, ca);
		}
		// рёбра-лучи
		for (int i = 0; i < 12; i++) {
			float th = TAU * i / 12 + p.t * 0.9f;
			float[] e = {p.x + cos(th) * R, baseY, p.z + sin(th) * R};
			float[] m = {(apex[0] + e[0]) * 0.5f, (apex[1] + e[1]) * 0.5f, (apex[2] + e[2]) * 0.5f};
			g.ribbon(new float[]{apex[0], m[0], e[0]}, new float[]{apex[1], m[1], e[1]}, new float[]{apex[2], m[2], e[2]},
					new float[]{0.008f * p.sc, 0.01f * p.sc, 0.008f * p.sc}, new float[]{0.5f * p.a * p.gl, 0.35f * p.a * p.gl, 0.12f * p.a * p.gl}, 3, p.cw);
		}
		float head = frac(p.t * 0.45f);
		g.ringTube(p.x, baseY, p.z, HU, HV, R, 0.016f * p.sc, 56, 4, p.c1, 0.95f * p.a * p.gl, head, 0.3f);
		g.ringTube(p.x, baseY + height * 0.42f, p.z, HU, new float[]{0.0f, 0.0f, -1.0f}, R * 0.58f, 0.009f * p.sc, 40, 4, p.c2,
				0.45f * p.a * p.gl, frac(p.t * 0.6f), 0.25f);
		float ha = head * TAU;
		g.glow(p.x + cos(ha) * R, baseY, p.z + sin(ha) * R, 0.1f * p.sc, p.cw, 0.7f * p.a * p.gl);
		g.glow(apex[0], apex[1], apex[2], 0.2f * p.sc, p.c1, 0.5f * p.a * p.gl);
		g.glow(p.x, baseY, p.z, R * 1.2f, p.c1, 0.06f * p.a * p.gl);
	}

	// ---------- Гироскоп ----------

	static void gyro(EspGfx g, P p) {
		float cy = p.y + p.h * 0.55f;
		float R0 = p.h * 0.38f + p.w * 0.3f;
		float[] radii = {R0, R0 * 1.09f, R0 * 1.18f};
		float[][][] bs = {
				basis(0.0f, 0.0f),
				basis(1.5708f, p.t * 0.7f),
				basis(1.05f + 0.25f * sin(p.t * 0.8f), -p.t * 0.9f)};
		float[] speeds = {0.5f, 0.38f, 0.62f};
		float[][] cols = {p.c1, p.c2, p.c1};
		for (int i = 0; i < 3; i++) {
			float head = frac(p.t * speeds[i] + i * 0.31f);
			g.ringTube(p.x, cy, p.z, bs[i][0], bs[i][1], radii[i], 0.014f * p.sc, 56, 4, cols[i], 0.95f * p.a * p.gl, head, 0.22f);
			float th = head * TAU;
			float c = cos(th) * radii[i];
			float s = sin(th) * radii[i];
			float bx = p.x + c * bs[i][0][0] + s * bs[i][1][0];
			float by = cy + c * bs[i][0][1] + s * bs[i][1][1];
			float bz = p.z + c * bs[i][0][2] + s * bs[i][1][2];
			g.sphere(bx, by, bz, 0.04f * p.sc, p.cw, 0.95f * p.a, 5, 8, 0.4f);
			g.glow(bx, by, bz, 0.2f * p.sc, cols[i], 0.5f * p.a * p.gl);
		}
		g.glow(p.x, cy, p.z, R0 * 0.9f, p.c1, 0.05f * p.a * p.gl);
	}

	// ---------- Кристалл ----------

	static void crystal(EspGfx g, P p) {
		float cy = p.y + p.h + 0.55f + 0.06f * sin(p.t * 2.0f);
		float tilt = 0.16f * sin(p.t * 0.9f);
		// связь с головой
		g.ribbon(new float[]{p.x, p.x}, new float[]{cy - 0.2f, p.y + p.h + 0.05f}, new float[]{p.z, p.z},
				new float[]{0.05f * p.sc, 0.05f * p.sc}, new float[]{0.16f * p.a * p.gl, 0.0f}, 2, p.c1);
		g.glow(p.x, cy, p.z, 0.6f * p.sc, p.c1, 0.3f * p.gl * p.a);
		g.bipyramid(p.x, cy, p.z, 0.075f * p.sc, 0.17f * p.sc, 0.12f * p.sc, 6, -p.t * 2.2f, -tilt, p.cw, 0.9f * p.a);
		g.bipyramid(p.x, cy, p.z, 0.15f * p.sc, 0.34f * p.sc, 0.21f * p.sc, 6, p.t * 1.2f, tilt, p.c1, 0.62f * p.a);
		g.glow(p.x, cy, p.z, 0.2f * p.sc, p.cw, 0.38f * p.gl * p.a);
		float R = 0.5f * p.sc + 0.1f;
		g.ringTube(p.x, cy, p.z, HU, HV, R, 0.008f * p.sc, 44, 4, p.c2, 0.3f * p.a * p.gl, frac(p.t * 0.4f), 0.2f);
		for (int k = 0; k < 5; k++) {
			float th = p.t * 1.1f + k * TAU / 5;
			float sx = p.x + cos(th) * R;
			float sz = p.z + sin(th) * R;
			float sy = cy + 0.12f * sin(p.t * 2.0f + k * 1.7f);
			g.bipyramid(sx, sy, sz, 0.04f * p.sc, 0.1f * p.sc, 0.06f * p.sc, 4, p.t * 3.0f + k, 0.5f, k % 2 == 0 ? p.c1 : p.c2, 0.85f * p.a);
			g.glow(sx, sy, sz, 0.12f * p.sc, k % 2 == 0 ? p.c1 : p.c2, 0.4f * p.a * p.gl);
		}
	}

	// ---------- Змей ----------

	static void serpent(EspGfx g, P p) {
		int n = 32;
		float R = p.w * 0.5f + 0.5f;
		float[] x = new float[n];
		float[] y = new float[n];
		float[] z = new float[n];
		float[] rad = new float[n];
		float[] al = new float[n];
		float[] gw = new float[n];
		float[] ga = new float[n];
		float[][] col = new float[n][];
		for (int j = 0; j < n; j++) {
			float tau = p.t - j * 0.052f;
			float th = tau * 1.25f;
			float rho = R * (1.0f + 0.1f * sin(tau * 2.3f));
			x[j] = p.x + cos(th) * rho;
			z[j] = p.z + sin(th) * rho;
			y[j] = p.y + p.h * (0.5f + 0.44f * sin(tau * 0.7f));
			float u = j / (n - 1.0f);
			float body = 0.25f + 0.75f * (float) Math.pow(1.0f - u, 0.6f);
			rad[j] = 0.06f * p.sc * body * (1.0f + 0.1f * sin(j * 1.6f - p.t * 6.0f));
			al[j] = 0.95f * (1.0f - 0.7f * u * u) * p.a;
			float band = 0.8f + 0.2f * (((j / 2) % 2 == 0) ? 1.0f : 0.0f);
			float[] c = mix(p.c1, p.c2, u);
			col[j] = new float[]{c[0] * band, c[1] * band, c[2] * band};
			gw[j] = rad[j] * 2.2f;
			ga[j] = 0.22f * (1.0f - u) * p.a * p.gl;
		}
		g.strand(x, y, z, rad, col, al, n, 7);
		g.ribbon(x, y, z, gw, ga, n, p.c1);
		// голова
		float tx = x[0] - x[1];
		float ty = y[0] - y[1];
		float tz = z[0] - z[1];
		float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
		if (tl > 1.0e-6f) {
			tx /= tl;
			ty /= tl;
			tz /= tl;
		}
		float hr = 0.075f * p.sc;
		g.sphere(x[0] + tx * hr * 0.5f, y[0] + ty * hr * 0.5f, z[0] + tz * hr * 0.5f, hr, p.c1, 0.95f * p.a, 6, 9, 0.35f);
		float sx = ty * 0.0f - tz * 1.0f;
		float sz2 = tx * 1.0f;
		float sl = (float) Math.sqrt(sx * sx + sz2 * sz2);
		if (sl > 1.0e-6f) {
			sx /= sl;
			sz2 /= sl;
		}
		for (int s = -1; s <= 1; s += 2) {
			float ex = x[0] + tx * hr * 1.0f + sx * hr * 0.55f * s;
			float ey = y[0] + ty * hr * 1.0f + hr * 0.45f;
			float ez = z[0] + tz * hr * 1.0f + sz2 * hr * 0.55f * s;
			g.glow(ex, ey, ez, 0.06f * p.sc, p.cw, 0.9f * p.a * p.gl);
		}
		// раздвоенный язык
		float fl = sin(p.t * 14.0f) * 0.03f * p.sc;
		float bx = x[0] + tx * hr * 1.6f;
		float by = y[0] + ty * hr * 1.6f;
		float bz = z[0] + tz * hr * 1.6f;
		for (int s = -1; s <= 1; s += 2) {
			g.ribbon(new float[]{bx, bx + tx * 0.05f * p.sc + sx * fl * s, bx + tx * 0.1f * p.sc + sx * 0.04f * p.sc * s},
					new float[]{by, by + ty * 0.05f * p.sc, by + ty * 0.1f * p.sc},
					new float[]{bz, bz + tz * 0.05f * p.sc + sz2 * fl * s, bz + tz * 0.1f * p.sc + sz2 * 0.04f * p.sc * s},
					new float[]{0.006f * p.sc, 0.005f * p.sc, 0.003f * p.sc}, new float[]{0.8f * p.a, 0.6f * p.a, 0.2f * p.a}, 3, p.cw);
		}
		g.glow(x[0], y[0], z[0], 0.3f * p.sc, p.c1, 0.28f * p.a * p.gl);
	}

	// ---------- Сфера ----------

	static void sphere(EspGfx g, P p) {
		float cy = p.y + p.h * 0.5f;
		float R = p.h * 0.5f + 0.22f;
		float pulse = 0.85f + 0.15f * sin(p.t * 2.5f);
		g.sphere(p.x, cy, p.z, R * 0.985f, p.c1, 0.07f * p.a, 8, 14, 1.4f);
		// меридианы
		for (int i = 0; i < 4; i++) {
			float phi = i * 0.7854f + p.t * 0.3f;
			float[] u = {cos(phi), 0.0f, sin(phi)};
			float[] v = {0.0f, 1.0f, 0.0f};
			g.ringTube(p.x, cy, p.z, u, v, R, 0.010f * p.sc, 48, 4, i % 2 == 0 ? p.c1 : p.c2, 0.6f * p.a * p.gl * pulse,
					frac(p.t * 0.35f + i * 0.25f), 0.35f);
		}
		// параллели с бегущей по широтам волной яркости
		float[] lats = {-1.0472f, -0.5236f, 0.0f, 0.5236f, 1.0472f};
		for (int i = 0; i < lats.length; i++) {
			float lam = lats[i];
			float wave = 0.4f + 0.6f * (0.5f + 0.5f * sin(p.t * 2.0f - lam * 3.0f));
			float[] v = {0.0f, 0.0f, (i % 2 == 0) ? 1.0f : -1.0f};
			g.ringTube(p.x, cy + R * sin(lam), p.z, HU, v, R * cos(lam), 0.010f * p.sc, 44, 4, p.c1, 0.7f * p.a * p.gl * wave,
					frac(p.t * 0.3f + i * 0.2f), 0.3f);
		}
		g.glow(p.x, cy, p.z, R * 0.8f, p.c1, 0.07f * p.a * p.gl);
	}

	// ---------- Воронка ----------

	static void funnel(EspGfx g, P p) {
		int rings = 8;
		float base = p.w * 0.3f + 0.22f;
		for (int i = 0; i < rings; i++) {
			float f = i / (rings - 1.0f);
			float y = p.y + 0.05f + f * p.h * 1.2f;
			float rad = base + f * 0.6f;
			float dir = (i % 2 == 0) ? 1.0f : -1.0f;
			float[] v = {0.0f, 0.0f, dir};
			float al = (0.3f + 0.6f * sin(3.1416f * (0.12f + 0.88f * f))) * p.a * p.gl;
			g.ringTube(p.x, y, p.z, HU, v, rad, (0.012f + 0.008f * f) * p.sc, 40, 4, mix(p.c1, p.c2, f), al,
					frac(p.t * (0.45f + 0.35f * f) + i * 0.17f), 0.25f);
		}
		// восходящие потоки
		for (int k = 0; k < 4; k++) {
			int pts = 28;
			float[] x = new float[pts];
			float[] y = new float[pts];
			float[] z = new float[pts];
			float[] w = new float[pts];
			float[] a = new float[pts];
			for (int j = 0; j < pts; j++) {
				float f = j / (pts - 1.0f);
				float th = k * TAU / 4 + f * 7.5f + p.t * 2.2f;
				float rad = base + f * 0.6f + 0.06f;
				x[j] = p.x + cos(th) * rad;
				z[j] = p.z + sin(th) * rad;
				y[j] = p.y + 0.05f + f * p.h * 1.2f;
				w[j] = (0.03f + 0.05f * f) * p.sc;
				a[j] = 0.55f * sin(3.1416f * f) * p.a * p.gl;
			}
			g.ribbon(x, y, z, w, a, pts, p.cw);
		}
		// обломки в потоке
		for (int k = 0; k < 10; k++) {
			float f = frac(p.t * 0.33f + k / 10.0f);
			float th = k * 2.4f + p.t * 2.8f + f * 5.0f;
			float rad = base + f * 0.6f + 0.14f;
			float dx = p.x + cos(th) * rad;
			float dz = p.z + sin(th) * rad;
			float dy = p.y + 0.05f + f * p.h * 1.2f;
			float al = sin(3.1416f * f);
			g.bipyramid(dx, dy, dz, 0.032f * p.sc, 0.08f * p.sc, 0.05f * p.sc, 4, p.t * 4.0f + k, p.t * 2.0f + k, k % 2 == 0 ? p.c1 : p.c2, 0.85f * al * p.a);
			g.glow(dx, dy, dz, 0.09f * p.sc, p.c1, 0.3f * al * p.a * p.gl);
		}
		g.glow(p.x, p.y + 0.05f, p.z, 0.9f, p.c1, 0.12f * p.a * p.gl);
	}
}
