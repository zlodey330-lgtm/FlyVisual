package com.flyvisual.util;

import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;

/**
 * Рисование настоящих 3D-фигур (кубики и призмы) цветными гранями.
 * Грани рисуются с обеих сторон, поэтому видны с любого угла.
 * Координаты задаются относительно камеры (как в WorldRenderEvents).
 */
public final class Shape3D {
	private static final int[][] FACES = {
			{0, 2, 6, 4}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 5, 7, 6}
	};

	private Shape3D() {
	}

	// Рабочие массивы: рисуем только из потока рендера, поэтому можно переиспользовать без new каждый кадр
	private static final double[][] CUBE = new double[8][3];
	private static final double[][] OCTA = new double[6][3];

	private static int alpha8(float alpha) {
		return Math.max(0, Math.min(255, Math.round(alpha * 255.0f)));
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, double[] p, int r, int g, int b, int a) {
		vc.vertex(m, (float) p[0], (float) p[1], (float) p[2]).color(r, g, b, a).next();
	}

	/** Четырёхугольник с разной прозрачностью у каждой вершины, виден с обеих сторон. */
	public static void quad(VertexConsumer vc, Matrix4f m, double[] p0, double[] p1, double[] p2, double[] p3,
							int rgb, float a0, float a1, float a2, float a3) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		int i0 = alpha8(a0);
		int i1 = alpha8(a1);
		int i2 = alpha8(a2);
		int i3 = alpha8(a3);
		vertex(vc, m, p0, r, g, b, i0);
		vertex(vc, m, p1, r, g, b, i1);
		vertex(vc, m, p2, r, g, b, i2);
		vertex(vc, m, p3, r, g, b, i3);
		vertex(vc, m, p3, r, g, b, i3);
		vertex(vc, m, p2, r, g, b, i2);
		vertex(vc, m, p1, r, g, b, i1);
		vertex(vc, m, p0, r, g, b, i0);
	}

	/** Кубик с центром в (cx, cy, cz), половиной стороны half, повёрнутый вокруг вертикали на yaw радиан. */
	public static void cube(VertexConsumer vc, Matrix4f m, double cx, double cy, double cz,
							double half, double yaw, int rgb, float alpha) {
		double cos = Math.cos(yaw);
		double sin = Math.sin(yaw);
		double[][] c = CUBE;
		for (int i = 0; i < 8; i++) {
			double sx = (i & 1) == 0 ? -half : half;
			double sy = (i & 2) == 0 ? -half : half;
			double sz = (i & 4) == 0 ? -half : half;
			c[i][0] = cx + sx * cos - sz * sin;
			c[i][1] = cy + sy;
			c[i][2] = cz + sx * sin + sz * cos;
		}
		for (int[] f : FACES) {
			quad(vc, m, c[f[0]], c[f[1]], c[f[2]], c[f[3]], rgb, alpha, alpha, alpha, alpha);
		}
	}

	/**
	 * Призма между двумя сечениями (по 4 угла у каждого): боковые грани, прозрачность плавно
	 * меняется от aA на первом сечении до aB на втором.
	 */
	public static void prism(VertexConsumer vc, Matrix4f m, double[][] sectionA, double[][] sectionB,
							 int rgb, float aA, float aB) {
		for (int k = 0; k < 4; k++) {
			int n = (k + 1) % 4;
			quad(vc, m, sectionA[k], sectionA[n], sectionB[n], sectionB[k], rgb, aA, aA, aB, aB);
		}
	}

	/** Сечение ленты в точке: ширина w в сторону s (горизонталь), толщина t по вертикали. */
	public static double[][] section(double x, double y, double z, double sx, double sz, double w, double t) {
		return new double[][]{
				{x - sx * w, y + t, z - sz * w},
				{x + sx * w, y + t, z + sz * w},
				{x + sx * w, y - t, z + sz * w},
				{x - sx * w, y - t, z - sz * w}
		};
	}

	/**
	 * Октаэдр (бриллиант/игла): вершины на концах трёх осей A, B, C от центра. Если одна ось
	 * длинная, а две другие короткие, получается игла.
	 */
	public static void octa(VertexConsumer vc, Matrix4f m, double cx, double cy, double cz,
							double[] a, double[] b, double[] c, int rgb, float alpha) {
		double[][] v = OCTA;
		for (int i = 0; i < 3; i++) {
			double[] axis = i == 0 ? a : i == 1 ? b : c;
			v[i * 2][0] = cx + axis[0];
			v[i * 2][1] = cy + axis[1];
			v[i * 2][2] = cz + axis[2];
			v[i * 2 + 1][0] = cx - axis[0];
			v[i * 2 + 1][1] = cy - axis[1];
			v[i * 2 + 1][2] = cz - axis[2];
		}
		for (int i = 0; i < 2; i++) {
			for (int j = 2; j < 4; j++) {
				for (int k = 4; k < 6; k++) {
					quad(vc, m, v[i], v[j], v[k], v[k], rgb, alpha, alpha, alpha, alpha);
				}
			}
		}
	}

	/** Бриллиант: октаэдр с равными осями по вертикали и горизонтали, повёрнутый на yaw. */
	public static void diamond(VertexConsumer vc, Matrix4f m, double cx, double cy, double cz,
							   double size, double yaw, int rgb, float alpha) {
		double cos = Math.cos(yaw) * size;
		double sin = Math.sin(yaw) * size;
		octa(vc, m, cx, cy, cz, new double[]{cos, 0.0, sin}, new double[]{0.0, size * 1.5, 0.0},
				new double[]{-sin, 0.0, cos}, rgb, alpha);
	}

	/** Игла длиной len вдоль направления (dx, dy, dz), толщиной thick. */
	public static void needle(VertexConsumer vc, Matrix4f m, double cx, double cy, double cz,
							  double dx, double dy, double dz, double len, double thick, int rgb, float alpha) {
		double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (l < 1.0e-6) return;
		dx /= l;
		dy /= l;
		dz /= l;
		// Любой вектор, перпендикулярный направлению
		double px = -dz;
		double pz = dx;
		double pl = Math.sqrt(px * px + pz * pz);
		if (pl < 1.0e-6) {
			px = 1.0;
			pz = 0.0;
			pl = 1.0;
		}
		px /= pl;
		pz /= pl;
		// Второй перпендикуляр = направление x первый
		double qx = dy * pz;
		double qy = dz * px - dx * pz;
		double qz = -dy * px;
		octa(vc, m, cx, cy, cz, new double[]{dx * len, dy * len, dz * len},
				new double[]{px * thick, 0.0, pz * thick}, new double[]{qx * thick, qy * thick, qz * thick}, rgb, alpha);
	}
}
