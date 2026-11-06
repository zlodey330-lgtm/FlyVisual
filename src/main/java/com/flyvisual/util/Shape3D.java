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

	private static void vertex(VertexConsumer vc, Matrix4f m, double[] p, int rgb, float alpha) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0f)));
		vc.vertex(m, (float) p[0], (float) p[1], (float) p[2]).color(r, g, b, a).next();
	}

	/** Четырёхугольник с разной прозрачностью у каждой вершины, виден с обеих сторон. */
	public static void quad(VertexConsumer vc, Matrix4f m, double[] p0, double[] p1, double[] p2, double[] p3,
							int rgb, float a0, float a1, float a2, float a3) {
		vertex(vc, m, p0, rgb, a0);
		vertex(vc, m, p1, rgb, a1);
		vertex(vc, m, p2, rgb, a2);
		vertex(vc, m, p3, rgb, a3);
		vertex(vc, m, p3, rgb, a3);
		vertex(vc, m, p2, rgb, a2);
		vertex(vc, m, p1, rgb, a1);
		vertex(vc, m, p0, rgb, a0);
	}

	/** Кубик с центром в (cx, cy, cz), половиной стороны half, повёрнутый вокруг вертикали на yaw радиан. */
	public static void cube(VertexConsumer vc, Matrix4f m, double cx, double cy, double cz,
							double half, double yaw, int rgb, float alpha) {
		double cos = Math.cos(yaw);
		double sin = Math.sin(yaw);
		double[][] c = new double[8][3];
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
}
