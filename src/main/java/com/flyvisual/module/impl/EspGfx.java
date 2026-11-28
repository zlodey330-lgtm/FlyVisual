package com.flyvisual.module.impl;

/**
 * Набор 3D-примитивов для TargetESP: мягкие свечения, ленты, шары, трубки-кольца, кристаллы.
 * Каждая вершина имеет свой цвет и прозрачность, поэтому свечение получается плавным (без квадратов).
 * Координаты — относительно камеры (она в точке 0,0,0). Не зависит от Minecraft.
 */
final class EspGfx {
	interface V {
		void v(float x, float y, float z, float r, float g, float b, float a);
	}

	/** Обычное смешивание (шары, кристаллы). */
	V solid;
	/** Аддитивное смешивание (свечения, ленты, кольца). */
	V add;

	private static final float LX;
	private static final float LY;
	private static final float LZ;

	static {
		float x = 0.30f;
		float y = 0.85f;
		float z = 0.42f;
		float l = (float) Math.sqrt(x * x + y * y + z * z);
		LX = x / l;
		LY = y / l;
		LZ = z / l;
	}

	EspGfx(V solid, V add) {
		this.solid = solid;
		this.add = add;
	}

	// ---------- Вспомогательное ----------

	private static float[] norm(float x, float y, float z) {
		float l = (float) Math.sqrt(x * x + y * y + z * z);
		if (l < 1.0e-8f) return new float[]{0.0f, 1.0f, 0.0f};
		return new float[]{x / l, y / l, z / l};
	}

	private static float[] cross(float ax, float ay, float az, float bx, float by, float bz) {
		return new float[]{ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx};
	}

	/** Базис «лицом к камере» в точке p: {right, up}. */
	private static float[] basis(float px, float py, float pz) {
		float[] d = norm(-px, -py, -pz);
		float[] r = cross(0.0f, 1.0f, 0.0f, d[0], d[1], d[2]);
		if (r[0] * r[0] + r[1] * r[1] + r[2] * r[2] < 1.0e-8f) r = new float[]{1.0f, 0.0f, 0.0f};
		r = norm(r[0], r[1], r[2]);
		float[] u = cross(d[0], d[1], d[2], r[0], r[1], r[2]);
		return new float[]{r[0], r[1], r[2], u[0], u[1], u[2]};
	}

	/** Вектор «вправо» относительно камеры в точке p (для глаз, смещений в плоскости экрана). */
	static float[] camRight(float px, float py, float pz) {
		float[] b = basis(px, py, pz);
		return new float[]{b[0], b[1], b[2]};
	}

	private static float cl(float v) {
		return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
	}

	private static void quad(V o, float[] p0, float[] p1, float[] p2, float[] p3, float[] c0, float[] c1, float[] c2, float[] c3) {
		o.v(p0[0], p0[1], p0[2], c0[0], c0[1], c0[2], cl(c0[3]));
		o.v(p1[0], p1[1], p1[2], c1[0], c1[1], c1[2], cl(c1[3]));
		o.v(p2[0], p2[1], p2[2], c2[0], c2[1], c2[2], cl(c2[3]));
		o.v(p3[0], p3[1], p3[2], c3[0], c3[1], c3[2], cl(c3[3]));
	}

	// ---------- Свечение и точки ----------

	/** Мягкое круглое свечение лицом к камере: в центре яркое, к краю плавно гаснет. */
	void glow(float px, float py, float pz, float r, float[] rgb, float a) {
		if (a <= 0.003f || r <= 0.0f) return;
		float[] b = basis(px, py, pz);
		int seg = 14;
		for (int i = 0; i < seg; i++) {
			double a0 = Math.PI * 2.0 * i / seg;
			double a1 = Math.PI * 2.0 * (i + 1) / seg;
			float c0 = (float) Math.cos(a0) * r;
			float s0 = (float) Math.sin(a0) * r;
			float c1 = (float) Math.cos(a1) * r;
			float s1 = (float) Math.sin(a1) * r;
			float x0 = px + b[0] * c0 + b[3] * s0;
			float y0 = py + b[1] * c0 + b[4] * s0;
			float z0 = pz + b[2] * c0 + b[5] * s0;
			float x1 = px + b[0] * c1 + b[3] * s1;
			float y1 = py + b[1] * c1 + b[4] * s1;
			float z1 = pz + b[2] * c1 + b[5] * s1;
			add.v(px, py, pz, rgb[0], rgb[1], rgb[2], cl(a));
			add.v(x0, y0, z0, rgb[0], rgb[1], rgb[2], 0.0f);
			add.v(x1, y1, z1, rgb[0], rgb[1], rgb[2], 0.0f);
			add.v(x1, y1, z1, rgb[0], rgb[1], rgb[2], 0.0f);
		}
	}

	/** Маленький плоский кружок лицом к камере (глаза), сдвинутый к камере на off. */
	void dot(float px, float py, float pz, float r, float[] rgb, float a, float off) {
		float[] d = norm(-px, -py, -pz);
		float cx = px + d[0] * off;
		float cy = py + d[1] * off;
		float cz = pz + d[2] * off;
		float[] b = basis(px, py, pz);
		int seg = 8;
		for (int i = 0; i < seg; i++) {
			double a0 = Math.PI * 2.0 * i / seg;
			double a1 = Math.PI * 2.0 * (i + 1) / seg;
			float c0 = (float) Math.cos(a0) * r;
			float s0 = (float) Math.sin(a0) * r;
			float c1 = (float) Math.cos(a1) * r;
			float s1 = (float) Math.sin(a1) * r;
			solid.v(cx, cy, cz, rgb[0], rgb[1], rgb[2], a);
			solid.v(cx + b[0] * c0 + b[3] * s0, cy + b[1] * c0 + b[4] * s0, cz + b[2] * c0 + b[5] * s0, rgb[0], rgb[1], rgb[2], a);
			solid.v(cx + b[0] * c1 + b[3] * s1, cy + b[1] * c1 + b[4] * s1, cz + b[2] * c1 + b[5] * s1, rgb[0], rgb[1], rgb[2], a);
			solid.v(cx + b[0] * c1 + b[3] * s1, cy + b[1] * c1 + b[4] * s1, cz + b[2] * c1 + b[5] * s1, rgb[0], rgb[1], rgb[2], a);
		}
	}

	// ---------- Ленты ----------

	/**
	 * Мягкая лента (аддитивная), всегда повёрнутая к камере. Ширина w[i] и яркость a[i] в каждой точке,
	 * края плавно гаснут.
	 */
	void ribbon(float[] x, float[] y, float[] z, float[] w, float[] a, int n, float[] rgb) {
		if (n < 2) return;
		float[][] l = new float[n][];
		float[][] m = new float[n][];
		float[][] r = new float[n][];
		for (int i = 0; i < n; i++) {
			int i0 = Math.max(0, i - 1);
			int i1 = Math.min(n - 1, i + 1);
			float[] t = norm(x[i1] - x[i0], y[i1] - y[i0], z[i1] - z[i0]);
			float[] d = norm(-x[i], -y[i], -z[i]);
			float[] s = cross(t[0], t[1], t[2], d[0], d[1], d[2]);
			s = norm(s[0], s[1], s[2]);
			l[i] = new float[]{x[i] - s[0] * w[i], y[i] - s[1] * w[i], z[i] - s[2] * w[i]};
			m[i] = new float[]{x[i], y[i], z[i]};
			r[i] = new float[]{x[i] + s[0] * w[i], y[i] + s[1] * w[i], z[i] + s[2] * w[i]};
		}
		for (int i = 0; i < n - 1; i++) {
			float a0 = cl(a[i]);
			float a1 = cl(a[i + 1]);
			float[] c0 = {rgb[0], rgb[1], rgb[2], 0.0f};
			float[] cm0 = {rgb[0], rgb[1], rgb[2], a0};
			float[] c1 = {rgb[0], rgb[1], rgb[2], 0.0f};
			float[] cm1 = {rgb[0], rgb[1], rgb[2], a1};
			quad(add, l[i], m[i], m[i + 1], l[i + 1], c0, cm0, cm1, c1);
			quad(add, m[i], r[i], r[i + 1], m[i + 1], cm0, c0, c1, cm1);
		}
	}

	// ---------- Объёмные тела ----------

	/** Шар с освещением и лёгким свечением по краю. layer: true = аддитивный, false = обычный. */
	void sphere(float cx, float cy, float cz, float rad, float[] rgb, float a, int lat, int lon, float rimMix) {
		if (a <= 0.003f || rad <= 0.0f) return;
		float[] vd = norm(-cx, -cy, -cz);
		for (int i = 0; i < lat; i++) {
			double t0 = Math.PI * i / lat;
			double t1 = Math.PI * (i + 1) / lat;
			for (int j = 0; j < lon; j++) {
				double p0 = Math.PI * 2.0 * j / lon;
				double p1 = Math.PI * 2.0 * (j + 1) / lon;
				double[][] pts = {{t0, p0}, {t0, p1}, {t1, p1}, {t1, p0}};
				for (double[] pt : pts) {
					float nx = (float) (Math.sin(pt[0]) * Math.cos(pt[1]));
					float ny = (float) Math.cos(pt[0]);
					float nz = (float) (Math.sin(pt[0]) * Math.sin(pt[1]));
					float nl = Math.max(0.0f, nx * LX + ny * LY + nz * LZ);
					float rim = (float) Math.pow(1.0f - Math.abs(nx * vd[0] + ny * vd[1] + nz * vd[2]), 2.0) * rimMix;
					float k = 0.55f + 0.45f * nl;
					solid.v(cx + nx * rad, cy + ny * rad, cz + nz * rad, cl(rgb[0] * k + rim), cl(rgb[1] * k + rim), cl(rgb[2] * k + rim), a);
				}
			}
		}
	}

	/**
	 * Двойная пирамида (кристалл) с гранями: top/bot — высота вершин, sides — число граней,
	 * yaw — поворот вокруг вертикали, tilt — наклон. Плоское освещение + блик.
	 */
	void bipyramid(float cx, float cy, float cz, float rad, float top, float bot, int sides, float yaw, float tilt,
				   float[] rgb, float a) {
		if (a <= 0.003f) return;
		float ct = (float) Math.cos(tilt);
		float st = (float) Math.sin(tilt);
		float[][] ring = new float[sides][];
		for (int i = 0; i < sides; i++) {
			double ang = yaw + Math.PI * 2.0 * i / sides;
			ring[i] = rot(cx, cy, cz, (float) Math.cos(ang) * rad, 0.0f, (float) Math.sin(ang) * rad, ct, st);
		}
		float[] tp = rot(cx, cy, cz, 0.0f, top, 0.0f, ct, st);
		float[] bt = rot(cx, cy, cz, 0.0f, -bot, 0.0f, ct, st);
		for (int i = 0; i < sides; i++) {
			int j = (i + 1) % sides;
			face(tp, ring[i], ring[j], cx, cy, cz, rgb, a);
			face(bt, ring[j], ring[i], cx, cy, cz, rgb, a);
		}
	}

	private static float[] rot(float cx, float cy, float cz, float x, float y, float z, float ct, float st) {
		float y2 = y * ct - z * st;
		float z2 = y * st + z * ct;
		return new float[]{cx + x, cy + y2, cz + z2};
	}

	private void face(float[] p0, float[] p1, float[] p2, float cx, float cy, float cz, float[] rgb, float a) {
		float[] n = cross(p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2], p2[0] - p0[0], p2[1] - p0[1], p2[2] - p0[2]);
		n = norm(n[0], n[1], n[2]);
		float mx = (p0[0] + p1[0] + p2[0]) / 3.0f;
		float my = (p0[1] + p1[1] + p2[1]) / 3.0f;
		float mz = (p0[2] + p1[2] + p2[2]) / 3.0f;
		if (n[0] * (mx - cx) + n[1] * (my - cy) + n[2] * (mz - cz) < 0.0f) {
			n[0] = -n[0];
			n[1] = -n[1];
			n[2] = -n[2];
		}
		float nl = Math.max(0.0f, n[0] * LX + n[1] * LY + n[2] * LZ);
		float[] vd = norm(-mx, -my, -mz);
		float hx = LX + vd[0];
		float hy = LY + vd[1];
		float hz = LZ + vd[2];
		float[] h = norm(hx, hy, hz);
		float spec = (float) Math.pow(Math.max(0.0f, n[0] * h[0] + n[1] * h[1] + n[2] * h[2]), 14.0) * 0.7f;
		float rim = (float) Math.pow(1.0f - Math.abs(n[0] * vd[0] + n[1] * vd[1] + n[2] * vd[2]), 2.0) * 0.25f;
		float k = 0.38f + 0.62f * nl;
		float r = cl(rgb[0] * k + spec + rim);
		float g = cl(rgb[1] * k + spec + rim);
		float b = cl(rgb[2] * k + spec + rim);
		solid.v(p0[0], p0[1], p0[2], r, g, b, a);
		solid.v(p1[0], p1[1], p1[2], r, g, b, a);
		solid.v(p2[0], p2[1], p2[2], r, g, b, a);
		solid.v(p2[0], p2[1], p2[2], r, g, b, a);
	}

	/**
	 * Светящееся кольцо-трубка в плоскости (u,v) с центром c. trail >= 0: яркая «комета» с хвостом,
	 * бегущая по кольцу (trail — положение головы 0..1). Аддитивное.
	 */
	void ringTube(float cx, float cy, float cz, float[] u, float[] v, float rad, float tube, int n, int seg,
				  float[] rgb, float a, float trail, float baseK) {
		if (a <= 0.003f) return;
		float[] nrm = cross(u[0], u[1], u[2], v[0], v[1], v[2]);
		nrm = norm(nrm[0], nrm[1], nrm[2]);
		float[][][] pts = new float[n + 1][seg][];
		float[] col = new float[n + 1];
		for (int i = 0; i <= n; i++) {
			double th = Math.PI * 2.0 * (i % n) / n;
			float er0 = (float) Math.cos(th);
			float er1 = (float) Math.sin(th);
			float erx = er0 * u[0] + er1 * v[0];
			float ery = er0 * u[1] + er1 * v[1];
			float erz = er0 * u[2] + er1 * v[2];
			float px = cx + erx * rad;
			float py = cy + ery * rad;
			float pz = cz + erz * rad;
			for (int s = 0; s < seg; s++) {
				double ph = Math.PI * 2.0 * s / seg;
				float c = (float) Math.cos(ph) * tube;
				float sn = (float) Math.sin(ph) * tube;
				pts[i][s] = new float[]{px + erx * c + nrm[0] * sn, py + ery * c + nrm[1] * sn, pz + erz * c + nrm[2] * sn};
			}
			if (trail >= 0.0f) {
				float f = (i % n) / (float) n;
				float dist = trail - f;
				dist -= (float) Math.floor(dist);
				col[i] = baseK + (1.0f - baseK) * (float) Math.pow(1.0f - dist, 3.0);
			} else {
				col[i] = 1.0f;
			}
		}
		for (int i = 0; i < n; i++) {
			float a0 = a * col[i];
			float a1 = a * col[i + 1];
			float w0 = trail >= 0.0f ? cl((col[i] - baseK) / (1.0f - baseK)) : 0.0f;
			float w1 = trail >= 0.0f ? cl((col[i + 1] - baseK) / (1.0f - baseK)) : 0.0f;
			float r0 = Math.min(1.0f, rgb[0] + (1.0f - rgb[0]) * w0 * w0 * 0.7f);
			float g0 = Math.min(1.0f, rgb[1] + (1.0f - rgb[1]) * w0 * w0 * 0.7f);
			float b0 = Math.min(1.0f, rgb[2] + (1.0f - rgb[2]) * w0 * w0 * 0.7f);
			float r1 = Math.min(1.0f, rgb[0] + (1.0f - rgb[0]) * w1 * w1 * 0.7f);
			float g1 = Math.min(1.0f, rgb[1] + (1.0f - rgb[1]) * w1 * w1 * 0.7f);
			float b1 = Math.min(1.0f, rgb[2] + (1.0f - rgb[2]) * w1 * w1 * 0.7f);
			for (int s = 0; s < seg; s++) {
				int t = (s + 1) % seg;
				float[] p0 = pts[i][s];
				float[] p1 = pts[i][t];
				float[] p2 = pts[i + 1][t];
				float[] p3 = pts[i + 1][s];
				add.v(p0[0], p0[1], p0[2], r0, g0, b0, cl(a0));
				add.v(p1[0], p1[1], p1[2], r0, g0, b0, cl(a0));
				add.v(p2[0], p2[1], p2[2], r1, g1, b1, cl(a1));
				add.v(p3[0], p3[1], p3[2], r1, g1, b1, cl(a1));
			}
		}
	}

	/**
	 * Гибкая трубка по ломаной с цветом и прозрачностью в каждой точке (тело змея). Освещённая, обычное смешивание.
	 */
	void strand(float[] x, float[] y, float[] z, float[] rad, float[][] col, float[] alpha, int n, int seg) {
		if (n < 2) return;
		float[][][] ring = new float[n][seg][];
		float[][][] nor = new float[n][seg][];
		float ux = 0.0f;
		float uy = 0.0f;
		float uz = 0.0f;
		for (int i = 0; i < n; i++) {
			int a = Math.max(0, i - 1);
			int b = Math.min(n - 1, i + 1);
			float[] t = norm(x[b] - x[a], y[b] - y[a], z[b] - z[a]);
			if (i == 0) {
				float[] q = Math.abs(t[1]) > 0.9f ? new float[]{1.0f, 0.0f, 0.0f} : new float[]{0.0f, 1.0f, 0.0f};
				float[] c = cross(t[0], t[1], t[2], q[0], q[1], q[2]);
				ux = c[0];
				uy = c[1];
				uz = c[2];
			}
			float dot = ux * t[0] + uy * t[1] + uz * t[2];
			ux -= t[0] * dot;
			uy -= t[1] * dot;
			uz -= t[2] * dot;
			float[] un = norm(ux, uy, uz);
			ux = un[0];
			uy = un[1];
			uz = un[2];
			float[] vv = cross(t[0], t[1], t[2], ux, uy, uz);
			for (int s = 0; s < seg; s++) {
				double ang = Math.PI * 2.0 * s / seg;
				float c = (float) Math.cos(ang);
				float sn = (float) Math.sin(ang);
				float nx = ux * c + vv[0] * sn;
				float ny = uy * c + vv[1] * sn;
				float nz = uz * c + vv[2] * sn;
				nor[i][s] = new float[]{nx, ny, nz};
				ring[i][s] = new float[]{x[i] + nx * rad[i], y[i] + ny * rad[i], z[i] + nz * rad[i]};
			}
		}
		for (int i = 0; i < n - 1; i++) {
			for (int s = 0; s < seg; s++) {
				int t = (s + 1) % seg;
				int[][] idx = {{i, s}, {i, t}, {i + 1, t}, {i + 1, s}};
				for (int[] id : idx) {
					float[] p = ring[id[0]][id[1]];
					float[] nn = nor[id[0]][id[1]];
					float nl = Math.max(0.0f, nn[0] * LX + nn[1] * LY + nn[2] * LZ);
					float[] vd = norm(-p[0], -p[1], -p[2]);
					float rim = (float) Math.pow(1.0f - Math.abs(nn[0] * vd[0] + nn[1] * vd[1] + nn[2] * vd[2]), 2.0) * 0.22f;
					float k = 0.45f + 0.55f * nl;
					float[] c = col[id[0]];
					solid.v(p[0], p[1], p[2], cl(c[0] * k + rim), cl(c[1] * k + rim), cl(c[2] * k + rim), alpha[id[0]]);
				}
			}
		}
	}

	/** Произвольный четырёхугольник со своим цветом в каждой вершине. */
	void quadAdd(float[] p0, float[] p1, float[] p2, float[] p3, float[] c0, float[] c1, float[] c2, float[] c3) {
		quad(add, p0, p1, p2, p3, c0, c1, c2, c3);
	}

	void quadSolid(float[] p0, float[] p1, float[] p2, float[] p3, float[] c0, float[] c1, float[] c2, float[] c3) {
		quad(solid, p0, p1, p2, p3, c0, c1, c2, c3);
	}
}
