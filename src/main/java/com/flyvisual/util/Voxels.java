package com.flyvisual.util;

import java.util.Arrays;

/**
 * Воксельный «запекатель» для моделей питомцев. Фигуры (эллипсоиды, трубки, коробки) рисуются
 * маленькими кубиками, потом из них собирается готовая сетка граней: у каждой грани свой цвет,
 * затенение по сторонам света и мягкая окклюзия (AO) в углах. Видимы только внешние грани.
 * Никаких зависимостей от Minecraft, поэтому модели можно проверять отдельно.
 */
public final class Voxels {
	/** Цвет в точке (x, y, z): 0xRRGGBB. */
	public interface Paint {
		int color(float x, float y, float z);
	}

	/** Форма: лежит ли точка внутри. */
	public interface Shape {
		boolean inside(float x, float y, float z);
	}

	/** Готовая сетка: по четыре вершины на грань, координаты относительно точки вращения. */
	public static final class Mesh {
		public final float[] pos;
		public final float[] rgb;
		public final int vertices;

		Mesh(float[] pos, float[] rgb, int vertices) {
			this.pos = pos;
			this.rgb = rgb;
			this.vertices = vertices;
		}
	}

	private final float res;
	private final float ox;
	private final float oy;
	private final float oz;
	private final int nx;
	private final int ny;
	private final int nz;
	private final int[] cells;

	/** Сетка охватывает прямоугольник (minX..maxX и т.д.) в координатах питомца, res = размер кубика. */
	public Voxels(float res, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		this.res = res;
		this.ox = minX;
		this.oy = minY;
		this.oz = minZ;
		this.nx = Math.max(1, (int) Math.ceil((maxX - minX) / res));
		this.ny = Math.max(1, (int) Math.ceil((maxY - minY) / res));
		this.nz = Math.max(1, (int) Math.ceil((maxZ - minZ) / res));
		this.cells = new int[nx * ny * nz];
	}

	// ---------- Цвета и шум ----------

	public static int mix(int a, int b, float t) {
		t = Math.max(0.0f, Math.min(1.0f, t));
		int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return (r << 16) | (g << 8) | bl;
	}

	/** Умножает яркость цвета. */
	public static int lum(int c, float f) {
		int r = Math.max(0, Math.min(255, Math.round(((c >> 16) & 0xFF) * f)));
		int g = Math.max(0, Math.min(255, Math.round(((c >> 8) & 0xFF) * f)));
		int b = Math.max(0, Math.min(255, Math.round((c & 0xFF) * f)));
		return (r << 16) | (g << 8) | b;
	}

	private static float hash(int x, int y, int z) {
		int h = x * 374761393 + y * 668265263 + z * 1274126177;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (float) 0x1000000;
	}

	/** Гладкий шум 0..1 (значения на решётке, плавная интерполяция). */
	public static float noise(float x, float y, float z) {
		int xi = (int) Math.floor(x);
		int yi = (int) Math.floor(y);
		int zi = (int) Math.floor(z);
		float fx = x - xi;
		float fy = y - yi;
		float fz = z - zi;
		fx = fx * fx * (3 - 2 * fx);
		fy = fy * fy * (3 - 2 * fy);
		fz = fz * fz * (3 - 2 * fz);
		float c00 = lerp(hash(xi, yi, zi), hash(xi + 1, yi, zi), fx);
		float c10 = lerp(hash(xi, yi + 1, zi), hash(xi + 1, yi + 1, zi), fx);
		float c01 = lerp(hash(xi, yi, zi + 1), hash(xi + 1, yi, zi + 1), fx);
		float c11 = lerp(hash(xi, yi + 1, zi + 1), hash(xi + 1, yi + 1, zi + 1), fx);
		return lerp(lerp(c00, c10, fy), lerp(c01, c11, fy), fz);
	}

	/** Резкий случайный шум на одну ячейку (для «шерстинок»). */
	public static float grain(float x, float y, float z, float cell) {
		return hash((int) Math.floor(x / cell), (int) Math.floor(y / cell), (int) Math.floor(z / cell));
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	public static float smooth(float e0, float e1, float x) {
		float t = Math.max(0.0f, Math.min(1.0f, (x - e0) / (e1 - e0)));
		return t * t * (3 - 2 * t);
	}

	// ---------- Рисование ----------

	private int index(int i, int j, int k) {
		return (k * ny + j) * nx + i;
	}

	private boolean solid(int i, int j, int k) {
		return i >= 0 && j >= 0 && k >= 0 && i < nx && j < ny && k < nz && cells[index(i, j, k)] != 0;
	}

	/** Заполняет форму цветом (поверх того, что уже нарисовано). */
	public void fill(float x0, float y0, float z0, float x1, float y1, float z1, Shape shape, Paint paint) {
		int i0 = Math.max(0, (int) Math.floor((x0 - ox) / res));
		int i1 = Math.min(nx - 1, (int) Math.ceil((x1 - ox) / res));
		int j0 = Math.max(0, (int) Math.floor((y0 - oy) / res));
		int j1 = Math.min(ny - 1, (int) Math.ceil((y1 - oy) / res));
		int k0 = Math.max(0, (int) Math.floor((z0 - oz) / res));
		int k1 = Math.min(nz - 1, (int) Math.ceil((z1 - oz) / res));
		for (int k = k0; k <= k1; k++) {
			for (int j = j0; j <= j1; j++) {
				for (int i = i0; i <= i1; i++) {
					float x = ox + (i + 0.5f) * res;
					float y = oy + (j + 0.5f) * res;
					float z = oz + (k + 0.5f) * res;
					if (shape.inside(x, y, z)) {
						cells[index(i, j, k)] = 0xFF000000 | (paint.color(x, y, z) & 0xFFFFFF);
					}
				}
			}
		}
	}

	public void ellipsoid(float cx, float cy, float cz, float rx, float ry, float rz, Paint paint) {
		fill(cx - rx, cy - ry, cz - rz, cx + rx, cy + ry, cz + rz, (x, y, z) -> {
			float a = (x - cx) / rx;
			float b = (y - cy) / ry;
			float c = (z - cz) / rz;
			return a * a + b * b + c * c <= 1.0f;
		}, paint);
	}

	public void box(float x0, float y0, float z0, float x1, float y1, float z1, Paint paint) {
		fill(x0, y0, z0, x1, y1, z1, (x, y, z) -> x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1, paint);
	}

	/** Сужающаяся трубка с круглыми концами от точки A (радиус ra) до точки B (радиус rb). */
	public void tube(float ax, float ay, float az, float ra, float bx, float by, float bz, float rb, Paint paint) {
		float dx = bx - ax;
		float dy = by - ay;
		float dz = bz - az;
		float len2 = dx * dx + dy * dy + dz * dz;
		float rmax = Math.max(ra, rb);
		fill(Math.min(ax, bx) - rmax, Math.min(ay, by) - rmax, Math.min(az, bz) - rmax,
				Math.max(ax, bx) + rmax, Math.max(ay, by) + rmax, Math.max(az, bz) + rmax, (x, y, z) -> {
					float t = len2 < 1e-9f ? 0.0f : ((x - ax) * dx + (y - ay) * dy + (z - az) * dz) / len2;
					t = Math.max(0.0f, Math.min(1.0f, t));
					float r = ra + (rb - ra) * t;
					float px = ax + dx * t - x;
					float py = ay + dy * t - y;
					float pz = az + dz * t - z;
					return px * px + py * py + pz * pz <= r * r;
				}, paint);
	}

	/** Перекрашивает уже нарисованные кубики внутри формы (пятна, полосы, глаза), объём не добавляет. */
	public void paintOver(float x0, float y0, float z0, float x1, float y1, float z1, Shape shape, Paint paint) {
		int i0 = Math.max(0, (int) Math.floor((x0 - ox) / res));
		int i1 = Math.min(nx - 1, (int) Math.ceil((x1 - ox) / res));
		int j0 = Math.max(0, (int) Math.floor((y0 - oy) / res));
		int j1 = Math.min(ny - 1, (int) Math.ceil((y1 - oy) / res));
		int k0 = Math.max(0, (int) Math.floor((z0 - oz) / res));
		int k1 = Math.min(nz - 1, (int) Math.ceil((z1 - oz) / res));
		for (int k = k0; k <= k1; k++) {
			for (int j = j0; j <= j1; j++) {
				for (int i = i0; i <= i1; i++) {
					if (cells[index(i, j, k)] == 0) continue;
					float x = ox + (i + 0.5f) * res;
					float y = oy + (j + 0.5f) * res;
					float z = oz + (k + 0.5f) * res;
					if (shape.inside(x, y, z)) {
						cells[index(i, j, k)] = 0xFF000000 | (paint.color(x, y, z) & 0xFFFFFF);
					}
				}
			}
		}
	}

	public void paintEllipsoid(float cx, float cy, float cz, float rx, float ry, float rz, Paint paint) {
		paintOver(cx - rx, cy - ry, cz - rz, cx + rx, cy + ry, cz + rz, (x, y, z) -> {
			float a = (x - cx) / rx;
			float b = (y - cy) / ry;
			float c = (z - cz) / rz;
			return a * a + b * b + c * c <= 1.0f;
		}, paint);
	}

	/** Вырезает форму (убирает кубики). */
	public void carve(float x0, float y0, float z0, float x1, float y1, float z1, Shape shape) {
		int i0 = Math.max(0, (int) Math.floor((x0 - ox) / res));
		int i1 = Math.min(nx - 1, (int) Math.ceil((x1 - ox) / res));
		int j0 = Math.max(0, (int) Math.floor((y0 - oy) / res));
		int j1 = Math.min(ny - 1, (int) Math.ceil((y1 - oy) / res));
		int k0 = Math.max(0, (int) Math.floor((z0 - oz) / res));
		int k1 = Math.min(nz - 1, (int) Math.ceil((z1 - oz) / res));
		for (int k = k0; k <= k1; k++) {
			for (int j = j0; j <= j1; j++) {
				for (int i = i0; i <= i1; i++) {
					float x = ox + (i + 0.5f) * res;
					float y = oy + (j + 0.5f) * res;
					float z = oz + (k + 0.5f) * res;
					if (shape.inside(x, y, z)) {
						cells[index(i, j, k)] = 0;
					}
				}
			}
		}
	}

	/** Ставит один кубик в точке. */
	public void dot(float x, float y, float z, int rgb) {
		int i = (int) Math.floor((x - ox) / res);
		int j = (int) Math.floor((y - oy) / res);
		int k = (int) Math.floor((z - oz) / res);
		if (i >= 0 && j >= 0 && k >= 0 && i < nx && j < ny && k < nz) {
			cells[index(i, j, k)] = 0xFF000000 | (rgb & 0xFFFFFF);
		}
	}

	public boolean isEmpty() {
		for (int c : cells) {
			if (c != 0) return false;
		}
		return true;
	}

	// ---------- Сборка сетки ----------

	private static final int[][] FACE = {
			// нормаль (x, y, z), касательная u, касательная v
			{1, 0, 0, 0, 1, 0, 0, 0, 1},
			{-1, 0, 0, 0, 0, 1, 0, 1, 0},
			{0, 1, 0, 0, 0, 1, 1, 0, 0},
			{0, -1, 0, 1, 0, 0, 0, 0, 1},
			{0, 0, 1, 1, 0, 0, 0, 1, 0},
			{0, 0, -1, 0, 1, 0, 1, 0, 0}
	};
	private static final float[] SHADE = {0.80f, 0.70f, 1.0f, 0.52f, 0.90f, 0.78f};
	private static final float[] AO = {0.55f, 0.72f, 0.88f, 1.0f};
	private static final int[][] CORNER = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};

	/** Собирает гладкую сетку (SmoothMesher). Координаты вершин считаются от точки вращения (px, py, pz). */
	public Mesh bake(float px, float py, float pz) {
		SmoothMesher.Result r = SmoothMesher.build(new SmoothMesher.Grid() {
			@Override
			public boolean solid(int i, int j, int k) {
				return Voxels.this.solid(i, j, k);
			}

			@Override
			public int color(int i, int j, int k) {
				return cells[index(i, j, k)] & 0xFFFFFF;
			}
		}, nx, ny, nz, res, ox, oy, oz, px, py, pz, 1.0f);
		return new Mesh(r.pos, r.rgb, r.quads * 4);
	}
}
