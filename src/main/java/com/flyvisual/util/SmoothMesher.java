package com.flyvisual.util;

import java.util.Arrays;

/**
 * Превращает сетку кубиков в гладкую поверхность (метод Surface Nets): вершины ставятся на границе формы,
 * несколько раз сглаживаются, а нормали берутся из размытого поля заполнения. Освещение и тень в углах
 * запекаются в цвет вершин, поэтому питомец выглядит литым, без видимых блоков.
 * Нет зависимостей от Minecraft. Сборка происходит один раз при создании модели.
 */
public final class SmoothMesher {
	/** Доступ к сетке: занята ли ячейка и какой у неё цвет 0xRRGGBB. */
	public interface Grid {
		boolean solid(int i, int j, int k);

		int color(int i, int j, int k);
	}

	/** Результат: по четыре вершины на четырёхугольник, координаты уже относительно точки вращения. */
	public static final class Result {
		public final float[] pos;
		public final float[] rgb;
		public final int quads;

		Result(float[] pos, float[] rgb, int quads) {
			this.pos = pos;
			this.rgb = rgb;
			this.quads = quads;
		}
	}

	// Направление света в системе питомца (сверху, чуть спереди и сбоку)
	private static final float LX = 0.42f;
	private static final float LY = 0.82f;
	private static final float LZ = 0.38f;

	private SmoothMesher() {
	}

	public static Result build(Grid g, int nx, int ny, int nz, float res, float ox, float oy, float oz,
							   float px, float py, float pz, float aoStrength) {
		// Дуальные ячейки: от -1 до n-1 по каждой оси (с запасом, чтобы поверхность замкнулась на краях)
		final int dx = nx + 1;
		final int dy = ny + 1;
		final int dz = nz + 1;
		int[] vid = new int[dx * dy * dz];
		Arrays.fill(vid, -1);

		float[] vx = new float[1024];
		float[] vy = new float[1024];
		float[] vz = new float[1024];
		float[] cr = new float[1024];
		float[] cg = new float[1024];
		float[] cb = new float[1024];
		int vc = 0;

		for (int k = -1; k < nz; k++) {
			for (int j = -1; j < ny; j++) {
				for (int i = -1; i < nx; i++) {
					int mask = 0;
					for (int c = 0; c < 8; c++) {
						if (g.solid(i + (c & 1), j + ((c >> 1) & 1), k + ((c >> 2) & 1))) mask |= 1 << c;
					}
					if (mask == 0 || mask == 255) continue;

					// Среднее по серединам рёбер, где меняется заполнение
					float sx = 0;
					float sy = 0;
					float sz = 0;
					int crossings = 0;
					for (int e = 0; e < 12; e++) {
						int a = EDGE[e][0];
						int b = EDGE[e][1];
						boolean sa = (mask & (1 << a)) != 0;
						boolean sb = (mask & (1 << b)) != 0;
						if (sa == sb) continue;
						sx += ((a & 1) + (b & 1)) * 0.5f;
						sy += (((a >> 1) & 1) + ((b >> 1) & 1)) * 0.5f;
						sz += (((a >> 2) & 1) + ((b >> 2) & 1)) * 0.5f;
						crossings++;
					}
					float fx = sx / crossings;
					float fy = sy / crossings;
					float fz = sz / crossings;

					// Цвет: среднее по занятым углам
					float r = 0;
					float gg = 0;
					float bb = 0;
					int n = 0;
					for (int c = 0; c < 8; c++) {
						if ((mask & (1 << c)) == 0) continue;
						int col = g.color(i + (c & 1), j + ((c >> 1) & 1), k + ((c >> 2) & 1));
						r += ((col >> 16) & 0xFF);
						gg += ((col >> 8) & 0xFF);
						bb += (col & 0xFF);
						n++;
					}

					if (vc == vx.length) {
						int len = vc * 2;
						vx = Arrays.copyOf(vx, len);
						vy = Arrays.copyOf(vy, len);
						vz = Arrays.copyOf(vz, len);
						cr = Arrays.copyOf(cr, len);
						cg = Arrays.copyOf(cg, len);
						cb = Arrays.copyOf(cb, len);
					}
					// Центр ячейки (i, j, k) воксельной сетки = ox + (i + 0.5) * res
					vx[vc] = ox + (i + 0.5f + fx) * res;
					vy[vc] = oy + (j + 0.5f + fy) * res;
					vz[vc] = oz + (k + 0.5f + fz) * res;
					cr[vc] = r / n / 255.0f;
					cg[vc] = gg / n / 255.0f;
					cb[vc] = bb / n / 255.0f;
					vid[((k + 1) * dy + (j + 1)) * dx + (i + 1)] = vc;
					vc++;
				}
			}
		}

		if (vc == 0) {
			return new Result(new float[0], new float[0], 0);
		}

		// Сглаживание: вершина тянется к среднему положению соседей, но не уходит далеко от исходной
		float[] ax = Arrays.copyOf(vx, vc);
		float[] ay = Arrays.copyOf(vy, vc);
		float[] az = Arrays.copyOf(vz, vc);
		float[] nxp = new float[vc];
		float[] nyp = new float[vc];
		float[] nzp = new float[vc];
		float limit = res * 0.95f;
		for (int pass = 0; pass < 4; pass++) {
			for (int k = -1; k < nz; k++) {
				for (int j = -1; j < ny; j++) {
					for (int i = -1; i < nx; i++) {
						int id = vid[((k + 1) * dy + (j + 1)) * dx + (i + 1)];
						if (id < 0) continue;
						float sx = 0;
						float sy = 0;
						float sz = 0;
						int cnt = 0;
						for (int dk = -1; dk <= 1; dk++) {
							for (int dj = -1; dj <= 1; dj++) {
								for (int di = -1; di <= 1; di++) {
									if (di == 0 && dj == 0 && dk == 0) continue;
									int ii = i + di;
									int jj = j + dj;
									int kk = k + dk;
									if (ii < -1 || jj < -1 || kk < -1 || ii >= nx || jj >= ny || kk >= nz) continue;
									int o = vid[((kk + 1) * dy + (jj + 1)) * dx + (ii + 1)];
									if (o < 0) continue;
									sx += vx[o];
									sy += vy[o];
									sz += vz[o];
									cnt++;
								}
							}
						}
						if (cnt == 0) {
							nxp[id] = vx[id];
							nyp[id] = vy[id];
							nzp[id] = vz[id];
							continue;
						}
						float tx = vx[id] + (sx / cnt - vx[id]) * 0.6f;
						float ty = vy[id] + (sy / cnt - vy[id]) * 0.6f;
						float tz = vz[id] + (sz / cnt - vz[id]) * 0.6f;
						// Не уходим от исходного положения дальше limit
						float ex = tx - ax[id];
						float ey = ty - ay[id];
						float ez = tz - az[id];
						float d = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
						if (d > limit) {
							float s = limit / d;
							tx = ax[id] + ex * s;
							ty = ay[id] + ey * s;
							tz = az[id] + ez * s;
						}
						nxp[id] = tx;
						nyp[id] = ty;
						nzp[id] = tz;
					}
				}
			}
			System.arraycopy(nxp, 0, vx, 0, vc);
			System.arraycopy(nyp, 0, vy, 0, vc);
			System.arraycopy(nzp, 0, vz, 0, vc);
		}

		// Размытое поле заполнения (для нормалей и мягкой тени): два прохода коробчатого размытия
		final int bx = nx + 4;
		final int by = ny + 4;
		final int bz = nz + 4;
		float[] field = new float[bx * by * bz];
		for (int k = 0; k < nz; k++) {
			for (int j = 0; j < ny; j++) {
				for (int i = 0; i < nx; i++) {
					if (g.solid(i, j, k)) field[((k + 2) * by + (j + 2)) * bx + (i + 2)] = 1.0f;
				}
			}
		}
		field = blur(field, bx, by, bz);
		field = blur(field, bx, by, bz);

		// Освещение, запечённое в цвет вершин
		float ll = (float) Math.sqrt(LX * LX + LY * LY + LZ * LZ);
		float lx = LX / ll;
		float ly = LY / ll;
		float lz = LZ / ll;
		for (int id = 0; id < vc; id++) {
			int ci = Math.round((vx[id] - ox) / res - 0.5f);
			int cj = Math.round((vy[id] - oy) / res - 0.5f);
			int ck = Math.round((vz[id] - oz) / res - 0.5f);
			float gx = f(field, bx, by, bz, ci + 1, cj, ck) - f(field, bx, by, bz, ci - 1, cj, ck);
			float gy = f(field, bx, by, bz, ci, cj + 1, ck) - f(field, bx, by, bz, ci, cj - 1, ck);
			float gz = f(field, bx, by, bz, ci, cj, ck + 1) - f(field, bx, by, bz, ci, cj, ck - 1);
			float gl = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
			float nxn = 0;
			float nyn = 1;
			float nzn = 0;
			if (gl > 1.0e-5f) {
				// Внутрь поле растёт, поэтому наружу = минус градиент
				nxn = -gx / gl;
				nyn = -gy / gl;
				nzn = -gz / gl;
			}
			float diffuse = Math.max(0.0f, nxn * lx + nyn * ly + nzn * lz);
			float shade = 0.52f + 0.48f * diffuse;
			// Низ темнее (земля затеняет)
			if (nyn < 0) shade *= 0.86f + 0.14f * (1.0f + nyn);
			// Тень в впадинах: рядом с вершиной много заполненных ячеек
			float occ = f(field, bx, by, bz, ci, cj, ck);
			float ao = 1.0f - Math.max(0.0f, occ - 0.5f) * 0.9f * aoStrength;
			shade *= Math.max(0.5f, Math.min(1.0f, ao));
			cr[id] = Math.min(1.0f, cr[id] * shade);
			cg[id] = Math.min(1.0f, cg[id] * shade);
			cb[id] = Math.min(1.0f, cb[id] * shade);
		}

		// Грани: по одному четырёхугольнику на каждое ребро сетки, где заполнение меняется
		float[] pos = new float[1 << 14];
		float[] rgb = new float[1 << 14];
		int quads = 0;
		for (int k = -1; k < nz; k++) {
			for (int j = -1; j < ny; j++) {
				for (int i = -1; i < nx; i++) {
					boolean s0 = g.solid(i, j, k);
					for (int axis = 0; axis < 3; axis++) {
						int ni = i + (axis == 0 ? 1 : 0);
						int nj = j + (axis == 1 ? 1 : 0);
						int nk = k + (axis == 2 ? 1 : 0);
						boolean s1 = g.solid(ni, nj, nk);
						if (s0 == s1) continue;
						// Четыре дуальные ячейки вокруг ребра
						int[] ids = new int[4];
						boolean ok = true;
						for (int q = 0; q < 4; q++) {
							int a = q & 1;
							int b = (q >> 1) & 1;
							int ci;
							int cj;
							int ck;
							if (axis == 0) {
								ci = i;
								cj = j - 1 + a;
								ck = k - 1 + b;
							} else if (axis == 1) {
								ci = i - 1 + a;
								cj = j;
								ck = k - 1 + b;
							} else {
								ci = i - 1 + a;
								cj = j - 1 + b;
								ck = k;
							}
							if (ci < -1 || cj < -1 || ck < -1 || ci >= nx || cj >= ny || ck >= nz) {
								ok = false;
								break;
							}
							ids[q] = vid[((ck + 1) * dy + (cj + 1)) * dx + (ci + 1)];
							if (ids[q] < 0) {
								ok = false;
								break;
							}
						}
						if (!ok) continue;
						if ((quads + 1) * 12 > pos.length) {
							pos = Arrays.copyOf(pos, pos.length * 2);
							rgb = Arrays.copyOf(rgb, rgb.length * 2);
						}
						// Порядок обхода по кругу: 0, 1, 3, 2
						int[] order = {0, 1, 3, 2};
						for (int q = 0; q < 4; q++) {
							int id = ids[order[q]];
							int o = (quads * 4 + q) * 3;
							pos[o] = vx[id] - px;
							pos[o + 1] = vy[id] - py;
							pos[o + 2] = vz[id] - pz;
							rgb[o] = cr[id];
							rgb[o + 1] = cg[id];
							rgb[o + 2] = cb[id];
						}
						quads++;
					}
				}
			}
		}
		return new Result(Arrays.copyOf(pos, quads * 12), Arrays.copyOf(rgb, quads * 12), quads);
	}

	private static final int[][] EDGE = {
			{0, 1}, {2, 3}, {4, 5}, {6, 7},
			{0, 2}, {1, 3}, {4, 6}, {5, 7},
			{0, 4}, {1, 5}, {2, 6}, {3, 7}
	};

	/** Значение размытого поля в ячейке (i, j, k) исходной сетки (с отступом 2 по краям). */
	private static float f(float[] a, int bx, int by, int bz, int i, int j, int k) {
		int x = Math.max(0, Math.min(bx - 1, i + 2));
		int y = Math.max(0, Math.min(by - 1, j + 2));
		int z = Math.max(0, Math.min(bz - 1, k + 2));
		return a[(z * by + y) * bx + x];
	}

	private static float[] blur(float[] a, int bx, int by, int bz) {
		float[] out = new float[a.length];
		for (int k = 0; k < bz; k++) {
			for (int j = 0; j < by; j++) {
				for (int i = 0; i < bx; i++) {
					float s = 0;
					for (int dk = -1; dk <= 1; dk++) {
						for (int dj = -1; dj <= 1; dj++) {
							for (int di = -1; di <= 1; di++) {
								int x = i + di;
								int y = j + dj;
								int z = k + dk;
								if (x < 0 || y < 0 || z < 0 || x >= bx || y >= by || z >= bz) continue;
								s += a[(z * by + y) * bx + x];
							}
						}
					}
					out[(k * by + j) * bx + i] = s / 27.0f;
				}
			}
		}
		return out;
	}
}
