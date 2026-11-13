package com.flyvisual.util;

import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

/**
 * Часть модели, собранная из мелких кубиков (вокселей) по форме (SDF) и раскраске (Paint).
 * Хранятся только видимые грани, у каждой вершины своё затенение (как мягкое затенение углов в Minecraft),
 * поэтому даже из кубиков получается объём и «шерсть». Сборка один раз, потом рисование почти бесплатное.
 */
public final class VoxelPart {
	// Яркость граней: верх, низ, +X, -X, +Z, -Z
	private static final float[] SHADE = {1.0f, 0.55f, 0.80f, 0.80f, 0.90f, 0.72f};
	private static final int[][] DIR = {{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};

	/** По 24 числа на квад: 4 вершины × (x, y, z, r, g, b). */
	private final float[] data;
	private final int quads;

	private VoxelPart(float[] data, int quads) {
		this.data = data;
		this.quads = quads;
	}

	/** Часть из готовой блочной сетки Voxels (так же собраны питомцы). */
	public static VoxelPart fromMesh(Voxels.Mesh m) {
		int quads = m.vertices / 4;
		float[] out = new float[m.vertices * 6];
		for (int i = 0; i < m.vertices; i++) {
			out[i * 6] = m.pos[i * 3];
			out[i * 6 + 1] = m.pos[i * 3 + 1];
			out[i * 6 + 2] = m.pos[i * 3 + 2];
			out[i * 6 + 3] = m.rgb[i * 3];
			out[i * 6 + 4] = m.rgb[i * 3 + 1];
			out[i * 6 + 5] = m.rgb[i * 3 + 2];
		}
		return new VoxelPart(out, quads);
	}

	/** Вершины для проверки/превью: по 6 чисел (x, y, z, r, g, b) на вершину. */
	public float[] rawData() {
		return data;
	}

	public int quadCount() {
		return quads;
	}

	/**
	 * Собирает часть. Координаты формы и раскраски задаются в общей системе модели,
	 * pivot (px, py, pz) становится началом координат самой части (вокруг него она будет поворачиваться).
	 * Область min..max должна охватывать всю форму.
	 */
	public static VoxelPart build(float v, float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
								  Sdf sdf, Sdf.Paint paint, float px, float py, float pz) {
		return build(v, minX, minY, minZ, maxX, maxY, maxZ, sdf, paint, px, py, pz, 1.0f);
	}

	public static VoxelPart build(float v0, float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
								  Sdf sdf, Sdf.Paint paint, float px, float py, float pz, float aoStrength) {
		// Блочная сетка (воксельный арт): клетка чуть крупнее исходной, чтобы кубики читались
		float v = Math.max(v0 * 1.35f, 0.028f);
		int nx = Math.max(1, (int) Math.ceil((maxX - minX) / v));
		int ny = Math.max(1, (int) Math.ceil((maxY - minY) / v));
		int nz = Math.max(1, (int) Math.ceil((maxZ - minZ) / v));

		boolean[] solid = new boolean[nx * ny * nz];
		int[] color = new int[nx * ny * nz];
		for (int i = 0; i < nx; i++) {
			float x = minX + (i + 0.5f) * v;
			for (int j = 0; j < ny; j++) {
				float y = minY + (j + 0.5f) * v;
				for (int k = 0; k < nz; k++) {
					float z = minZ + (k + 0.5f) * v;
					if (sdf.d(x, y, z) < 0.0f) {
						int idx = (i * ny + j) * nz + k;
						solid[idx] = true;
						// Лёгкая «зернистость» каждого кубика, как пиксельная текстура
						int h = (i * 73856093) ^ (j * 19349663) ^ (k * 83492791);
						float grain = 0.94f + 0.12f * (((h >> 8) & 0xFF) / 255.0f);
						color[idx] = Voxels.lum(paint.rgb(x, y, z), grain);
					}
				}
			}
		}

		float[] out = new float[4096];
		int count = 0;
		for (int i = 0; i < nx; i++) {
			for (int j = 0; j < ny; j++) {
				for (int k = 0; k < nz; k++) {
					int idx = (i * ny + j) * nz + k;
					if (!solid[idx]) continue;
					int c = color[idx];
					float cr = ((c >> 16) & 0xFF) / 255.0f;
					float cg = ((c >> 8) & 0xFF) / 255.0f;
					float cb = (c & 0xFF) / 255.0f;
					for (int f = 0; f < 6; f++) {
						if (is(solid, nx, ny, nz, i + DIR[f][0], j + DIR[f][1], k + DIR[f][2])) continue;
						if ((count + 1) * 24 > out.length) out = java.util.Arrays.copyOf(out, out.length * 2);
						count = emit(out, count, f, i, j, k, v, minX, minY, minZ, px, py, pz, cr, cg, cb, solid, nx, ny, nz, aoStrength);
					}
				}
			}
		}
		return new VoxelPart(java.util.Arrays.copyOf(out, count * 24), count);
	}

	private static boolean is(boolean[] solid, int nx, int ny, int nz, int i, int j, int k) {
		if (i < 0 || j < 0 || k < 0 || i >= nx || j >= ny || k >= nz) return false;
		return solid[(i * ny + j) * nz + k];
	}

	/** Вершины граней: для каждой грани четыре угла в обходе, плюс две оси для расчёта затенения углов. */
	private static int emit(float[] out, int count, int face, int i, int j, int k, float v, float minX, float minY, float minZ,
							float px, float py, float pz, float cr, float cg, float cb,
							boolean[] solid, int nx, int ny, int nz, float aoStrength) {
		int[] n = DIR[face];
		// Оси грани (u, w) перпендикулярны нормали
		int[] u;
		int[] w;
		if (n[1] != 0) {
			u = new int[]{1, 0, 0};
			w = new int[]{0, 0, 1};
		} else if (n[0] != 0) {
			u = new int[]{0, 0, 1};
			w = new int[]{0, 1, 0};
		} else {
			u = new int[]{1, 0, 0};
			w = new int[]{0, 1, 0};
		}

		// Центр грани
		float cx = minX + (i + 0.5f + n[0] * 0.5f) * v - px;
		float cy = minY + (j + 0.5f + n[1] * 0.5f) * v - py;
		float cz = minZ + (k + 0.5f + n[2] * 0.5f) * v - pz;
		float h = v * 0.5f;

		int[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
		int base = count * 24;
		for (int c = 0; c < 4; c++) {
			int su = corners[c][0];
			int sw = corners[c][1];

			// Затенение угла по трём соседям перед гранью
			int bi = i + n[0];
			int bj = j + n[1];
			int bk = k + n[2];
			boolean s1 = is(solid, nx, ny, nz, bi + su * u[0], bj + su * u[1], bk + su * u[2]);
			boolean s2 = is(solid, nx, ny, nz, bi + sw * w[0], bj + sw * w[1], bk + sw * w[2]);
			boolean s3 = is(solid, nx, ny, nz, bi + su * u[0] + sw * w[0], bj + su * u[1] + sw * w[1], bk + su * u[2] + sw * w[2]);
			int ao = (s1 && s2) ? 0 : 3 - ((s1 ? 1 : 0) + (s2 ? 1 : 0) + (s3 ? 1 : 0));
			float aoMul = 1.0f - (3 - ao) * 0.14f * aoStrength;

			float shade = SHADE[face] * aoMul;
			out[base] = cx + (u[0] * su + w[0] * sw) * h;
			out[base + 1] = cy + (u[1] * su + w[1] * sw) * h;
			out[base + 2] = cz + (u[2] * su + w[2] * sw) * h;
			out[base + 3] = cr * shade;
			out[base + 4] = cg * shade;
			out[base + 5] = cb * shade;
			base += 6;
		}
		return count + 1;
	}

	/** Рисует часть в текущей позиции стека матриц. light = освещённость, alpha = прозрачность. */
	public void draw(MatrixStack ms, Draw3d d, float light, float alpha) {
		Matrix4f m = ms.peek().getPositionMatrix();
		float[] a = data;
		for (int i = 0; i < a.length; i += 6) {
			d.vert(m, a[i], a[i + 1], a[i + 2], a[i + 3] * light, a[i + 4] * light, a[i + 5] * light, alpha);
		}
	}
}
