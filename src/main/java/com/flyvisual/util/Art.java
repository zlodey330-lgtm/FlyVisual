package com.flyvisual.util;

import com.flyvisual.util.Voxels.Paint;

/**
 * Набор помощников для воксельных вещей (шапки, маски, рюкзаки, крылья): те же кубики и текстура-«зерно»,
 * что и у питомцев. Координаты в блоках, R = размер кубика.
 */
public final class Art {
	public static final float R = 0.025f;

	private Art() {
	}

	public static Voxels grid(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		return new Voxels(R, minX, minY, minZ, maxX, maxY, maxZ);
	}

	public static VoxelPart part(Voxels v) {
		return VoxelPart.fromMesh(v.bakeBlocky(0, 0, 0));
	}

	/** Цвет с лёгким зерном, как пиксельная текстура. */
	public static Paint tex(int rgb) {
		return (x, y, z) -> Voxels.lum(rgb, 0.93f + 0.14f * Voxels.grain(x, y, z, R));
	}

	/** Ровный цвет без зерна. */
	public static Paint flat(int rgb) {
		return (x, y, z) -> rgb;
	}

	/** Скруглённый брусок. */
	public static void rbox(Voxels v, float cx, float cy, float cz, float rx, float ry, float rz, Paint p) {
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

	/** Конус вверх: основание на y0 (полуширина r0 по X, rz по Z), вершина на y0 + h. */
	public static void cone(Voxels v, float cx, float y0, float cz, float r0, float h, float rz, Paint p) {
		v.fill(cx - r0, y0, cz - rz, cx + r0, y0 + h, cz + rz, (x, y, z) -> {
			float t = (y - y0) / h;
			return Math.abs(x - cx) <= r0 * (1.0f - t) && Math.abs(z - cz) <= rz * (1.0f - t * 0.6f);
		}, p);
	}

	/** Круглый конус вверх (основание круг радиуса r0, вершина смещается на (dx, dz)). */
	public static void spire(Voxels v, float cx, float y0, float cz, float r0, float h, float dx, float dz, Paint p) {
		float m = Math.max(Math.abs(dx), Math.abs(dz));
		v.fill(cx - r0 - m, y0, cz - r0 - m, cx + r0 + m, y0 + h, cz + r0 + m, (x, y, z) -> {
			float t = (y - y0) / h;
			float ccx = cx + dx * t * t;
			float ccz = cz + dz * t * t;
			float r = r0 * (1.0f - t);
			return (x - ccx) * (x - ccx) + (z - ccz) * (z - ccz) <= r * r && r > 0.004f;
		}, p);
	}

	/** Вертикальный цилиндр (эллиптический). */
	public static void cyl(Voxels v, float cx, float cz, float rx, float rz, float y0, float y1, Paint p) {
		v.fill(cx - rx, y0, cz - rz, cx + rx, y1, cz + rz, (x, y, z) -> {
			float a = (x - cx) / rx;
			float b = (z - cz) / rz;
			return a * a + b * b <= 1.0f;
		}, p);
	}

	/** Горизонтальное кольцо (в плоскости XZ) с внешним радиусом ro и внутренним ri. */
	public static void ring(Voxels v, float cx, float cy, float cz, float ro, float ri, float h, Paint p) {
		v.fill(cx - ro, cy - h / 2, cz - ro, cx + ro, cy + h / 2, cz + ro, (x, y, z) -> {
			float d = (x - cx) * (x - cx) + (z - cz) * (z - cz);
			return d <= ro * ro && d >= ri * ri;
		}, p);
	}

	/** Цилиндр вдоль оси Z от z0 до z1 (круг в плоскости XY). */
	public static void cylZ(Voxels v, float cx, float cy, float r, float z0, float z1, Paint p) {
		v.fill(cx - r, cy - r, z0, cx + r, cy + r, z1, (x, y, z) -> (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r, p);
	}

	/** Перекрашивает прямоугольник (только уже закрашенные кубики). */
	public static void paint(Voxels v, float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
		v.paintOver(x0, y0, z0, x1, y1, z1, (x, y, z) -> x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1, flat(rgb));
	}
}
