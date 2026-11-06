package com.flyvisual.util;

import org.joml.Matrix4f;

/** Объёмные примитивы для эффектов: кольца, стенки, ленты, пирамиды. Всё из полупрозрачных граней. */
public final class Fx3d {
	public record V(float x, float y, float z) {
		public V add(V o) {
			return new V(x + o.x, y + o.y, z + o.z);
		}

		public V mul(float k) {
			return new V(x * k, y * k, z * k);
		}
	}

	public static final V X = new V(1, 0, 0);
	public static final V Y = new V(0, 1, 0);
	public static final V Z = new V(0, 0, 1);

	private Fx3d() {
	}

	public static float[] color(float hue, float sat, float val) {
		int c = ColorUtil.hsv(hue, sat, val);
		return new float[]{ColorUtil.red(c), ColorUtil.green(c), ColorUtil.blue(c)};
	}

	/** Псевдослучайное число 0..1 по номеру. */
	public static float rnd(int i, int salt) {
		double v = Math.sin(i * 12.9898 + salt * 78.233) * 43758.5453;
		return (float) (v - Math.floor(v));
	}

	public static V p(V c, V a, V b, V n, float rad, float h, double th) {
		float cs = (float) Math.cos(th) * rad;
		float sn = (float) Math.sin(th) * rad;
		return new V(c.x + a.x * cs + b.x * sn + n.x * h, c.y + a.y * cs + b.y * sn + n.y * h, c.z + a.z * cs + b.z * sn + n.z * h);
	}

	public static void quad(Draw3d d, Matrix4f m, V p0, V p1, V p2, V p3, float[] col, float a0, float a1, float a2, float a3) {
		d.vertex(m, p0.x, p0.y, p0.z, col[0], col[1], col[2], a0);
		d.vertex(m, p1.x, p1.y, p1.z, col[0], col[1], col[2], a1);
		d.vertex(m, p2.x, p2.y, p2.z, col[0], col[1], col[2], a2);
		d.vertex(m, p3.x, p3.y, p3.z, col[0], col[1], col[2], a3);
	}

	/**
	 * Кольцевая лента вокруг центра c в плоскости осей a, b (n = нормаль): от (r0, h0) с прозрачностью a0
	 * до (r1, h1) с прозрачностью a1. Плоское кольцо: h0 = h1, стенка цилиндра: r0 = r1.
	 */
	public static void band(Draw3d d, Matrix4f m, V c, V a, V b, V n, float r0, float h0, float r1, float h1,
							int seg, float[] col, float a0, float a1) {
		for (int i = 0; i < seg; i++) {
			double t0 = i * Math.PI * 2.0 / seg;
			double t1 = (i + 1) * Math.PI * 2.0 / seg;
			quad(d, m, p(c, a, b, n, r0, h0, t0), p(c, a, b, n, r0, h0, t1), p(c, a, b, n, r1, h1, t1), p(c, a, b, n, r1, h1, t0),
					col, a0, a0, a1, a1);
		}
	}

	/** Плоская лента между двумя точками шириной w в плоскости XZ. */
	public static void ribbon(Draw3d d, Matrix4f m, V s, V e, float w, float[] col, float alpha) {
		float dx = e.x - s.x;
		float dz = e.z - s.z;
		float len = (float) Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0e-5f) return;
		float px = -dz / len * w * 0.5f;
		float pz = dx / len * w * 0.5f;
		quad(d, m, new V(s.x + px, s.y, s.z + pz), new V(s.x - px, s.y, s.z - pz), new V(e.x - px, e.y, e.z - pz),
				new V(e.x + px, e.y, e.z + pz), col, alpha, alpha, alpha, alpha);
	}

	/** Четырёхгранная пирамида: основание вокруг base (полуширина w в осях a, b), вершина apex. */
	public static void pyramid(Draw3d d, Matrix4f m, V base, V a, V b, float w, V apex, float[] col, float aBase, float aTip) {
		V p0 = new V(base.x + (a.x + b.x) * w, base.y + (a.y + b.y) * w, base.z + (a.z + b.z) * w);
		V p1 = new V(base.x + (-a.x + b.x) * w, base.y + (-a.y + b.y) * w, base.z + (-a.z + b.z) * w);
		V p2 = new V(base.x + (-a.x - b.x) * w, base.y + (-a.y - b.y) * w, base.z + (-a.z - b.z) * w);
		V p3 = new V(base.x + (a.x - b.x) * w, base.y + (a.y - b.y) * w, base.z + (a.z - b.z) * w);
		quad(d, m, p0, p1, apex, apex, col, aBase, aBase, aTip, aTip);
		quad(d, m, p1, p2, apex, apex, col, aBase, aBase, aTip, aTip);
		quad(d, m, p2, p3, apex, apex, col, aBase, aBase, aTip, aTip);
		quad(d, m, p3, p0, apex, apex, col, aBase, aBase, aTip, aTip);
	}

	/** Маленький куб с центром в c. */
	public static void spark(Draw3d d, Matrix4f m, V c, float size, float[] col, float alpha) {
		float h = size / 2.0f;
		d.cube(m, c.x - h, c.y - h, c.z - h, c.x + h, c.y + h, c.z + h, col[0], col[1], col[2], alpha);
	}
}
