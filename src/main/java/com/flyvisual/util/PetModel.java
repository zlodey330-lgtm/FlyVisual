package com.flyvisual.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Модель питомца из частей. У каждой части своя сетка (см. Voxels), точка вращения и родитель.
 * Части анимируются поворотами и сдвигами, а updateWorld() считает итоговые матрицы.
 * Без зависимостей от Minecraft.
 */
public class PetModel {
	public static final class Part {
		public final String name;
		public final Part parent;
		/** Точка вращения в координатах покоя (относительно корня питомца). */
		public final float px;
		public final float py;
		public final float pz;
		public final Voxels.Mesh mesh;

		/** Повороты в градусах (порядок: Z, затем X, затем Y), масштаб и добавочный сдвиг. */
		public float rx;
		public float ry;
		public float rz;
		public float sx = 1.0f;
		public float sy = 1.0f;
		public float sz = 1.0f;
		public float tx;
		public float ty;
		public float tz;
		public boolean visible = true;

		/** Матрица части (4x4, по столбцам): из системы части в систему питомца. */
		public final float[] world = new float[16];

		Part(String name, Part parent, float px, float py, float pz, Voxels.Mesh mesh) {
			this.name = name;
			this.parent = parent;
			this.px = px;
			this.py = py;
			this.pz = pz;
			this.mesh = mesh;
		}

		public void reset() {
			rx = 0.0f;
			ry = 0.0f;
			rz = 0.0f;
			sx = 1.0f;
			sy = 1.0f;
			sz = 1.0f;
			tx = 0.0f;
			ty = 0.0f;
			tz = 0.0f;
			visible = true;
		}
	}

	public final List<Part> parts = new ArrayList<>();

	/** Добавляет часть: сетка запекается относительно точки вращения (px, py, pz). */
	public Part add(String name, Part parent, float px, float py, float pz, Voxels voxels) {
		Voxels.Mesh mesh = voxels == null || voxels.isEmpty() ? null : voxels.bake(px, py, pz);
		Part part = new Part(name, parent, px, py, pz, mesh);
		parts.add(part);
		return part;
	}

	/** То же, но сетка блочная (воксельный арт, без сглаживания). */
	public Part addBlocky(String name, Part parent, float px, float py, float pz, Voxels voxels) {
		Voxels.Mesh mesh = voxels == null || voxels.isEmpty() ? null : voxels.bakeBlocky(px, py, pz);
		Part part = new Part(name, parent, px, py, pz, mesh);
		parts.add(part);
		return part;
	}

	/** Расставляет части для момента time (секунды); walk = фаза шага, moving = 0..1 (стоит или идёт). */
	public void animate(float time, float walk, float moving) {
	}

	public int vertexCount() {
		int n = 0;
		for (Part p : parts) {
			if (p.mesh != null) n += p.mesh.vertices;
		}
		return n;
	}

	private final float[] local = new float[16];

	/** Считает матрицы всех частей (родители идут в списке раньше детей). */
	public void updateWorld() {
		for (Part p : parts) {
			buildLocal(p, local);
			if (p.parent == null) {
				System.arraycopy(local, 0, p.world, 0, 16);
			} else {
				multiply(p.parent.world, local, p.world);
			}
		}
	}

	private static void buildLocal(Part p, float[] m) {
		double rx = Math.toRadians(p.rx);
		double ry = Math.toRadians(p.ry);
		double rz = Math.toRadians(p.rz);
		float cx = (float) Math.cos(rx);
		float sx = (float) Math.sin(rx);
		float cy = (float) Math.cos(ry);
		float sy = (float) Math.sin(ry);
		float cz = (float) Math.cos(rz);
		float sz = (float) Math.sin(rz);

		// R = Ry * Rx * Rz
		float r00 = cy * cz + sy * sx * sz;
		float r01 = -cy * sz + sy * sx * cz;
		float r02 = sy * cx;
		float r10 = cx * sz;
		float r11 = cx * cz;
		float r12 = -sx;
		float r20 = -sy * cz + cy * sx * sz;
		float r21 = sy * sz + cy * sx * cz;
		float r22 = cy * cx;

		float tx = p.tx;
		float ty = p.ty;
		float tz = p.tz;
		if (p.parent == null) {
			tx += p.px;
			ty += p.py;
			tz += p.pz;
		} else {
			tx += p.px - p.parent.px;
			ty += p.py - p.parent.py;
			tz += p.pz - p.parent.pz;
		}

		m[0] = r00 * p.sx;
		m[1] = r10 * p.sx;
		m[2] = r20 * p.sx;
		m[3] = 0.0f;
		m[4] = r01 * p.sy;
		m[5] = r11 * p.sy;
		m[6] = r21 * p.sy;
		m[7] = 0.0f;
		m[8] = r02 * p.sz;
		m[9] = r12 * p.sz;
		m[10] = r22 * p.sz;
		m[11] = 0.0f;
		m[12] = tx;
		m[13] = ty;
		m[14] = tz;
		m[15] = 1.0f;
	}

	/** out = a * b (матрицы 4x4 по столбцам). */
	private static void multiply(float[] a, float[] b, float[] out) {
		for (int col = 0; col < 4; col++) {
			for (int row = 0; row < 4; row++) {
				float s = 0.0f;
				for (int k = 0; k < 4; k++) {
					s += a[k * 4 + row] * b[col * 4 + k];
				}
				out[col * 4 + row] = s;
			}
		}
	}
}
