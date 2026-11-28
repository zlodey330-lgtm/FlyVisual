package com.flyvisual.module.impl;

/**
 * Мини-набор для сборки 3D-моделей эффектов убийства: стек преобразований и примитивы
 * (параллелепипед, эллипсоид, трубка, призма, тело вращения, нить). Грани плоско затеняются,
 * поэтому модели выглядят чёткими и объёмными. Класс не зависит от Minecraft: всё уходит в {@link Sink}.
 * Координаты вершин — относительно камеры (её положение в (0,0,0)).
 */
final class KillGfx {
	interface Sink {
		void quad(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2,
				  float x3, float y3, float z3, float r, float g, float b, float a);
	}

	private final Sink sink;
	private float[] m = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0};
	private final float[][] stack = new float[32][12];
	private final float[] tmp = new float[12];
	private int sp;

	float cr = 1.0f;
	float cg = 1.0f;
	float cb = 1.0f;
	float ca = 1.0f;
	/** Свет без затенения (глаза, лезвие, молния). */
	boolean emissive;
	/** Призрачный режим: края прозрачнее/ярче (эффект Френеля). */
	boolean fresnel;
	/** Общая яркость (для затухания модели). */
	float lum = 1.0f;

	private static final float LX = 0.30f;
	private static final float LY = 0.84f;
	private static final float LZ = 0.45f;

	KillGfx(Sink sink) {
		this.sink = sink;
	}

	// ---------- Цвет ----------

	void mat(float r, float g, float b, float a) {
		cr = r;
		cg = g;
		cb = b;
		ca = a;
	}

	void mat(float[] c, float a) {
		cr = c[0];
		cg = c[1];
		cb = c[2];
		ca = a;
	}

	// ---------- Преобразования ----------

	void reset() {
		sp = 0;
		m = new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0};
	}

	void push() {
		if (sp < stack.length) {
			System.arraycopy(m, 0, stack[sp], 0, 12);
			sp++;
		}
	}

	void pop() {
		if (sp > 0) {
			sp--;
			System.arraycopy(stack[sp], 0, m, 0, 12);
		}
	}

	void translate(float x, float y, float z) {
		m[3] += m[0] * x + m[1] * y + m[2] * z;
		m[7] += m[4] * x + m[5] * y + m[6] * z;
		m[11] += m[8] * x + m[9] * y + m[10] * z;
	}

	void scale(float sx, float sy, float sz) {
		m[0] *= sx;
		m[4] *= sx;
		m[8] *= sx;
		m[1] *= sy;
		m[5] *= sy;
		m[9] *= sy;
		m[2] *= sz;
		m[6] *= sz;
		m[10] *= sz;
	}

	void scale(float s) {
		scale(s, s, s);
	}

	private void mulRot(float r0, float r1, float r2, float r3, float r4, float r5, float r6, float r7, float r8) {
		for (int row = 0; row < 3; row++) {
			float a0 = m[row * 4];
			float a1 = m[row * 4 + 1];
			float a2 = m[row * 4 + 2];
			tmp[row * 4] = a0 * r0 + a1 * r3 + a2 * r6;
			tmp[row * 4 + 1] = a0 * r1 + a1 * r4 + a2 * r7;
			tmp[row * 4 + 2] = a0 * r2 + a1 * r5 + a2 * r8;
		}
		for (int row = 0; row < 3; row++) {
			m[row * 4] = tmp[row * 4];
			m[row * 4 + 1] = tmp[row * 4 + 1];
			m[row * 4 + 2] = tmp[row * 4 + 2];
		}
	}

	void rotX(float a) {
		float c = (float) Math.cos(a);
		float s = (float) Math.sin(a);
		mulRot(1, 0, 0, 0, c, -s, 0, s, c);
	}

	void rotY(float a) {
		float c = (float) Math.cos(a);
		float s = (float) Math.sin(a);
		mulRot(c, 0, s, 0, 1, 0, -s, 0, c);
	}

	void rotZ(float a) {
		float c = (float) Math.cos(a);
		float s = (float) Math.sin(a);
		mulRot(c, -s, 0, s, c, 0, 0, 0, 1);
	}

	/** Текущее положение точки (0,0,0) локальной системы в координатах камеры. */
	float[] origin() {
		return new float[]{m[3], m[7], m[11]};
	}

	// ---------- Грань ----------

	private final float[] q = new float[15];

	private void tf(int i, float x, float y, float z) {
		q[i * 3] = m[0] * x + m[1] * y + m[2] * z + m[3];
		q[i * 3 + 1] = m[4] * x + m[5] * y + m[6] * z + m[7];
		q[i * 3 + 2] = m[8] * x + m[9] * y + m[10] * z + m[11];
	}

	/**
	 * Грань из четырёх точек (в локальных координатах). Если задан центр (ox,oy,oz), нормаль
	 * направляется от центра наружу; иначе — к камере (двусторонние листы).
	 */
	private void face(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
					  float dx, float dy, float dz, boolean hasCenter, float ox, float oy, float oz) {
		if (ca <= 0.004f) return;
		tf(0, ax, ay, az);
		tf(1, bx, by, bz);
		tf(2, cx, cy, cz);
		tf(3, dx, dy, dz);
		float fx = (q[0] + q[3] + q[6] + q[9]) * 0.25f;
		float fy = (q[1] + q[4] + q[7] + q[10]) * 0.25f;
		float fz = (q[2] + q[5] + q[8] + q[11]) * 0.25f;
		float ux = q[6] - q[0];
		float uy = q[7] - q[1];
		float uz = q[8] - q[2];
		float vx = q[9] - q[3];
		float vy = q[10] - q[4];
		float vz = q[11] - q[5];
		float nx = uy * vz - uz * vy;
		float ny = uz * vx - ux * vz;
		float nz = ux * vy - uy * vx;
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		float sh = 0.7f;
		float alpha = ca;
		if (len > 1.0e-9f) {
			nx /= len;
			ny /= len;
			nz /= len;
			float side;
			if (hasCenter) {
				tf(4, ox, oy, oz);
				side = nx * (fx - q[12]) + ny * (fy - q[13]) + nz * (fz - q[14]);
			} else {
				side = -(nx * fx + ny * fy + nz * fz);
			}
			if (side < 0.0f) {
				nx = -nx;
				ny = -ny;
				nz = -nz;
			}
			if (!emissive) {
				float d = nx * LX + ny * LY + nz * LZ;
				sh = 0.36f + 0.64f * Math.max(0.0f, d * 0.5f + 0.5f);
				// лёгкая подсветка граней, смотрящих на камеру (контровой свет)
				float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
				if (fl > 1.0e-4f) {
					float vd = -(nx * fx + ny * fy + nz * fz) / fl;
					sh += 0.10f * Math.max(0.0f, vd);
				}
			} else {
				sh = 1.0f;
			}
			if (fresnel) {
				float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
				if (fl > 1.0e-4f) {
					float vd = Math.abs(nx * fx + ny * fy + nz * fz) / fl;
					alpha = Math.min(1.0f, ca * (0.55f + 0.95f * (1.0f - vd)));
				}
			}
		}
		float k = sh * lum;
		sink.quad(q[0], q[1], q[2], q[3], q[4], q[5], q[6], q[7], q[8], q[9], q[10], q[11],
				Math.min(1.0f, cr * k), Math.min(1.0f, cg * k), Math.min(1.0f, cb * k), alpha);
	}

	/** Двусторонний плоский четырёхугольник (лист, перо, трещина). */
	void flat(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
			  float dx, float dy, float dz) {
		face(ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, false, 0, 0, 0);
	}

	// ---------- Примитивы ----------

	void box(float cx, float cy, float cz, float hx, float hy, float hz) {
		float x0 = cx - hx;
		float x1 = cx + hx;
		float y0 = cy - hy;
		float y1 = cy + hy;
		float z0 = cz - hz;
		float z1 = cz + hz;
		face(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, true, cx, cy, cz);
		face(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, true, cx, cy, cz);
		face(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, true, cx, cy, cz);
		face(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, true, cx, cy, cz);
		face(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, true, cx, cy, cz);
		face(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, true, cx, cy, cz);
	}

	void ell(float cx, float cy, float cz, float rx, float ry, float rz, int st, int sl) {
		for (int i = 0; i < st; i++) {
			double t0 = Math.PI * i / st;
			double t1 = Math.PI * (i + 1) / st;
			float s0 = (float) Math.sin(t0);
			float s1 = (float) Math.sin(t1);
			float c0 = (float) Math.cos(t0);
			float c1 = (float) Math.cos(t1);
			for (int j = 0; j < sl; j++) {
				double p0 = Math.PI * 2.0 * j / sl;
				double p1 = Math.PI * 2.0 * (j + 1) / sl;
				float cp0 = (float) Math.cos(p0);
				float sp0 = (float) Math.sin(p0);
				float cp1 = (float) Math.cos(p1);
				float sp1 = (float) Math.sin(p1);
				face(cx + rx * s0 * cp0, cy + ry * c0, cz + rz * s0 * sp0,
						cx + rx * s0 * cp1, cy + ry * c0, cz + rz * s0 * sp1,
						cx + rx * s1 * cp1, cy + ry * c1, cz + rz * s1 * sp1,
						cx + rx * s1 * cp0, cy + ry * c1, cz + rz * s1 * sp0, true, cx, cy, cz);
			}
		}
	}

	void ell(float cx, float cy, float cz, float rx, float ry, float rz) {
		ell(cx, cy, cz, rx, ry, rz, 5, 8);
	}

	void tube(float x0, float y0, float z0, float x1, float y1, float z1, float r0, float r1, int seg) {
		float ax = x1 - x0;
		float ay = y1 - y0;
		float az = z1 - z0;
		float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
		if (len < 1.0e-6f) return;
		ax /= len;
		ay /= len;
		az /= len;
		float rx = 0.0f;
		float ry = 1.0f;
		float rz = 0.0f;
		if (Math.abs(ay) > 0.9f) {
			rx = 1.0f;
			ry = 0.0f;
		}
		float ux = ay * rz - az * ry;
		float uy = az * rx - ax * rz;
		float uz = ax * ry - ay * rx;
		float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
		ux /= ul;
		uy /= ul;
		uz /= ul;
		float vx = ay * uz - az * uy;
		float vy = az * ux - ax * uz;
		float vz = ax * uy - ay * ux;
		float mx = (x0 + x1) * 0.5f;
		float my = (y0 + y1) * 0.5f;
		float mz = (z0 + z1) * 0.5f;
		for (int i = 0; i < seg; i++) {
			double a0 = Math.PI * 2.0 * i / seg;
			double a1 = Math.PI * 2.0 * (i + 1) / seg;
			float c0 = (float) Math.cos(a0);
			float s0 = (float) Math.sin(a0);
			float c1 = (float) Math.cos(a1);
			float s1 = (float) Math.sin(a1);
			float p0x = ux * c0 + vx * s0;
			float p0y = uy * c0 + vy * s0;
			float p0z = uz * c0 + vz * s0;
			float p1x = ux * c1 + vx * s1;
			float p1y = uy * c1 + vy * s1;
			float p1z = uz * c1 + vz * s1;
			face(x0 + p0x * r0, y0 + p0y * r0, z0 + p0z * r0, x0 + p1x * r0, y0 + p1y * r0, z0 + p1z * r0,
					x1 + p1x * r1, y1 + p1y * r1, z1 + p1z * r1, x1 + p0x * r1, y1 + p0y * r1, z1 + p0z * r1,
					true, mx, my, mz);
			if (r1 > 0.002f) {
				face(x1, y1, z1, x1 + p0x * r1, y1 + p0y * r1, z1 + p0z * r1, x1 + p1x * r1, y1 + p1y * r1, z1 + p1z * r1,
						x1 + p1x * r1, y1 + p1y * r1, z1 + p1z * r1, true, mx, my, mz);
			}
			if (r0 > 0.002f) {
				face(x0, y0, z0, x0 + p0x * r0, y0 + p0y * r0, z0 + p0z * r0, x0 + p1x * r0, y0 + p1y * r0, z0 + p1z * r0,
						x0 + p1x * r0, y0 + p1y * r0, z0 + p1z * r0, true, mx, my, mz);
			}
		}
	}

	void tube(float x0, float y0, float z0, float x1, float y1, float z1, float r0, float r1) {
		tube(x0, y0, z0, x1, y1, z1, r0, r1, 7);
	}

	/**
	 * Призма с многоугольным основанием poly (пары x,z): от y0 (масштаб s0) до y1 (масштаб s1).
	 */
	void prism(float[] poly, float y0, float y1, float s0, float s1, boolean capTop, boolean capBot) {
		int n = poly.length / 2;
		float cy = (y0 + y1) * 0.5f;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			float ax = poly[i * 2];
			float az = poly[i * 2 + 1];
			float bx = poly[j * 2];
			float bz = poly[j * 2 + 1];
			face(ax * s0, y0, az * s0, bx * s0, y0, bz * s0, bx * s1, y1, bz * s1, ax * s1, y1, az * s1, true, 0, cy, 0);
			if (capTop) {
				face(0, y1, 0, ax * s1, y1, az * s1, bx * s1, y1, bz * s1, bx * s1, y1, bz * s1, true, 0, cy, 0);
			}
			if (capBot) {
				face(0, y0, 0, ax * s0, y0, az * s0, bx * s0, y0, bz * s0, bx * s0, y0, bz * s0, true, 0, cy, 0);
			}
		}
	}

	/**
	 * Тело вращения вокруг оси Y (мантия, юбка): кольца на высотах ys с радиусами rs.
	 * amp — амплитуда волн/лохмотьев на каждом кольце, waveK — число волн по окружности.
	 */
	void lathe(float[] ys, float[] rs, float[] amp, int sl, float phase, float waveK, float ex, float ez) {
		int rings = ys.length;
		float[] px = new float[sl + 1];
		float[] pz = new float[sl + 1];
		float[] ppx = new float[sl + 1];
		float[] ppz = new float[sl + 1];
		for (int r = 0; r < rings; r++) {
			for (int j = 0; j <= sl; j++) {
				double ang = Math.PI * 2.0 * (j % sl) / sl;
				float w = (float) (Math.sin(ang * waveK + phase) * 0.6 + Math.sin(ang * (waveK * 2.0 + 1.0) - phase * 1.7) * 0.4);
				float rad = rs[r] * (1.0f + amp[r] * w);
				px[j] = (float) Math.cos(ang) * rad * ex;
				pz[j] = (float) Math.sin(ang) * rad * ez;
			}
			if (r > 0) {
				float y0 = ys[r - 1];
				float y1 = ys[r];
				float cy = (y0 + y1) * 0.5f;
				for (int j = 0; j < sl; j++) {
					face(ppx[j], y0, ppz[j], ppx[j + 1], y0, ppz[j + 1], px[j + 1], y1, pz[j + 1], px[j], y1, pz[j],
							true, 0, cy, 0);
				}
			}
			System.arraycopy(px, 0, ppx, 0, sl + 1);
			System.arraycopy(pz, 0, ppz, 0, sl + 1);
		}
		// верхняя крышка
		float yt = ys[rings - 1];
		for (int j = 0; j < sl; j++) {
			face(0, yt, 0, ppx[j], yt, ppz[j], ppx[j + 1], yt, ppz[j + 1], ppx[j + 1], yt, ppz[j + 1], true, 0, yt - 0.1f, 0);
		}
	}

	/** Гибкая трубка по ломаной: точки (x,y,z) и радиусы в каждой. */
	void strand(float[] x, float[] y, float[] z, float[] rad, int n, int seg) {
		if (n < 2) return;
		float[][] rx = new float[n][seg];
		float[][] ry = new float[n][seg];
		float[][] rz = new float[n][seg];
		float ux = 0.0f;
		float uy = 0.0f;
		float uz = 0.0f;
		for (int i = 0; i < n; i++) {
			int a = Math.max(0, i - 1);
			int b = Math.min(n - 1, i + 1);
			float tx = x[b] - x[a];
			float ty = y[b] - y[a];
			float tz = z[b] - z[a];
			float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
			if (tl < 1.0e-6f) {
				tx = 0.0f;
				ty = 1.0f;
				tz = 0.0f;
			} else {
				tx /= tl;
				ty /= tl;
				tz /= tl;
			}
			if (i == 0) {
				float qx = Math.abs(ty) > 0.9f ? 1.0f : 0.0f;
				float qy = Math.abs(ty) > 0.9f ? 0.0f : 1.0f;
				ux = ty * 0 - tz * qy;
				uy = tz * qx - tx * 0;
				uz = tx * qy - ty * qx;
			}
			float dot = ux * tx + uy * ty + uz * tz;
			ux -= tx * dot;
			uy -= ty * dot;
			uz -= tz * dot;
			float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
			if (ul < 1.0e-6f) {
				ux = 1.0f;
				uy = 0.0f;
				uz = 0.0f;
				ul = 1.0f;
			}
			ux /= ul;
			uy /= ul;
			uz /= ul;
			float vx = ty * uz - tz * uy;
			float vy = tz * ux - tx * uz;
			float vz = tx * uy - ty * ux;
			for (int s = 0; s < seg; s++) {
				double ang = Math.PI * 2.0 * s / seg;
				float c = (float) Math.cos(ang) * rad[i];
				float sn = (float) Math.sin(ang) * rad[i];
				rx[i][s] = x[i] + ux * c + vx * sn;
				ry[i][s] = y[i] + uy * c + vy * sn;
				rz[i][s] = z[i] + uz * c + vz * sn;
			}
		}
		for (int i = 0; i < n - 1; i++) {
			float cx = (x[i] + x[i + 1]) * 0.5f;
			float cy = (y[i] + y[i + 1]) * 0.5f;
			float cz = (z[i] + z[i + 1]) * 0.5f;
			for (int s = 0; s < seg; s++) {
				int t = (s + 1) % seg;
				face(rx[i][s], ry[i][s], rz[i][s], rx[i][t], ry[i][t], rz[i][t], rx[i + 1][t], ry[i + 1][t], rz[i + 1][t],
						rx[i + 1][s], ry[i + 1][s], rz[i + 1][s], true, cx, cy, cz);
			}
		}
		// кончик
		int e = n - 1;
		for (int s = 0; s < seg; s++) {
			int t = (s + 1) % seg;
			face(x[e], y[e], z[e], rx[e][s], ry[e][s], rz[e][s], rx[e][t], ry[e][t], rz[e][t], rx[e][t], ry[e][t], rz[e][t],
					true, x[e - 1], y[e - 1], z[e - 1]);
		}
	}

	/** Тор (кольцо) вокруг оси Y. */
	void torus(float cx, float cy, float cz, float big, float small, int n, int seg) {
		float px = cx + big;
		float pz = cz;
		for (int i = 1; i <= n; i++) {
			double a = Math.PI * 2.0 * i / n;
			float nx = cx + (float) Math.cos(a) * big;
			float nz = cz + (float) Math.sin(a) * big;
			tube(px, cy, pz, nx, cy, nz, small, small, seg);
			px = nx;
			pz = nz;
		}
	}

	/** Светящаяся лента (две пересекающиеся плоскости) между двумя точками: молния, нить. */
	void bolt(float x0, float y0, float z0, float x1, float y1, float z1, float w) {
		float ax = x1 - x0;
		float ay = y1 - y0;
		float az = z1 - z0;
		float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
		if (len < 1.0e-6f) return;
		ax /= len;
		ay /= len;
		az /= len;
		float rx = 0.0f;
		float ry = 1.0f;
		float rz = 0.0f;
		if (Math.abs(ay) > 0.9f) {
			rx = 1.0f;
			ry = 0.0f;
		}
		float p1x = ay * rz - az * ry;
		float p1y = az * rx - ax * rz;
		float p1z = ax * ry - ay * rx;
		float l1 = (float) Math.sqrt(p1x * p1x + p1y * p1y + p1z * p1z);
		p1x = p1x / l1 * w;
		p1y = p1y / l1 * w;
		p1z = p1z / l1 * w;
		float p2x = ay * p1z - az * p1y;
		float p2y = az * p1x - ax * p1z;
		float p2z = ax * p1y - ay * p1x;
		boolean oldE = emissive;
		emissive = true;
		flat(x0 - p1x, y0 - p1y, z0 - p1z, x0 + p1x, y0 + p1y, z0 + p1z, x1 + p1x, y1 + p1y, z1 + p1z, x1 - p1x, y1 - p1y, z1 - p1z);
		flat(x0 - p2x, y0 - p2y, z0 - p2z, x0 + p2x, y0 + p2y, z0 + p2z, x1 + p2x, y1 + p2y, z1 + p2z, x1 - p2x, y1 - p2y, z1 - p2z);
		emissive = oldE;
	}

	/** Плоский диск на высоте y (пятно, портал). */
	void disc(float cx, float y, float cz, float r, int seg) {
		for (int i = 0; i < seg; i++) {
			double a0 = Math.PI * 2.0 * i / seg;
			double a1 = Math.PI * 2.0 * (i + 1) / seg;
			flat(cx, y, cz, cx + (float) Math.cos(a0) * r, y, cz + (float) Math.sin(a0) * r,
					cx + (float) Math.cos(a1) * r, y, cz + (float) Math.sin(a1) * r,
					cx + (float) Math.cos(a1) * r, y, cz + (float) Math.sin(a1) * r);
		}
	}

	/** Плоское кольцо на высоте y. */
	void ring(float cx, float y, float cz, float r0, float r1, int seg) {
		for (int i = 0; i < seg; i++) {
			double a0 = Math.PI * 2.0 * i / seg;
			double a1 = Math.PI * 2.0 * (i + 1) / seg;
			float c0 = (float) Math.cos(a0);
			float s0 = (float) Math.sin(a0);
			float c1 = (float) Math.cos(a1);
			float s1 = (float) Math.sin(a1);
			flat(cx + c0 * r0, y, cz + s0 * r0, cx + c0 * r1, y, cz + s0 * r1, cx + c1 * r1, y, cz + s1 * r1, cx + c1 * r0, y, cz + s1 * r0);
		}
	}

	// ---------- Вспомогательное ----------

	/**
	 * Двухзвенная кость (плечо-локоть-кисть): возвращает {ex,ey,ez, hx,hy,hz} — локоть и фактическая кисть.
	 * Цель подтягивается, если дальше длины руки. pole — куда «смотрит» локоть.
	 */
	static float[] ik(float sx, float sy, float sz, float tx, float ty, float tz, float l1, float l2,
					  float px, float py, float pz) {
		float dx = tx - sx;
		float dy = ty - sy;
		float dz = tz - sz;
		float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		float max = (l1 + l2) * 0.999f;
		if (d > max) {
			float k = max / d;
			dx *= k;
			dy *= k;
			dz *= k;
			d = max;
		}
		if (d < 0.05f) {
			d = 0.05f;
			if (dx == 0.0f && dy == 0.0f && dz == 0.0f) dy = -0.05f;
		}
		float ex = dx / d;
		float ey = dy / d;
		float ez = dz / d;
		float a = (l1 * l1 - l2 * l2 + d * d) / (2.0f * d);
		float h = (float) Math.sqrt(Math.max(0.0f, l1 * l1 - a * a));
		float dot = px * ex + py * ey + pz * ez;
		float qx = px - ex * dot;
		float qy = py - ey * dot;
		float qz = pz - ez * dot;
		float ql = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
		if (ql < 1.0e-5f) {
			qx = 0.0f;
			qy = 1.0f;
			qz = 0.0f;
			ql = 1.0f;
		}
		qx /= ql;
		qy /= ql;
		qz /= ql;
		return new float[]{sx + ex * a + qx * h, sy + ey * a + qy * h, sz + ez * a + qz * h, sx + dx, sy + dy, sz + dz};
	}
}
