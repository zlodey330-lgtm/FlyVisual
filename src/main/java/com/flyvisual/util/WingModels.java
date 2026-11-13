package com.flyvisual.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Крылья: ангельские, драконьи, бабочки, демонические. Каждое крыло собрано из частей (перья, кости, перепонки),
 * которые рисуются с поворотом вокруг своего корня, поэтому крыло можно складывать и махать им.
 * Описано для правого крыла (+X наружу), левое получается зеркалом. Корень крыла — у плеча.
 */
public final class WingModels {
	public static final String[] NAMES = {"Ангельские", "Драконьи", "Бабочки", "Демонические", "Кристальные", "Огненные", "Механические"};
	private static final Wing[] CACHE = new Wing[NAMES.length];

	private WingModels() {
	}

	/** Одна часть крыла: сетка, смещение корня, поворот в покое, насколько участвует в дополнительном махе. */
	public static final class Part {
		public final VoxelPart mesh;
		public final float ox;
		public final float oy;
		public final float oz;
		public final float spread;
		public final float weight;

		Part(VoxelPart mesh, float ox, float oy, float oz, float spread, float weight) {
			this.mesh = mesh;
			this.ox = ox;
			this.oy = oy;
			this.oz = oz;
			this.spread = spread;
			this.weight = weight;
		}
	}

	public static final class Wing {
		public final List<Part> parts = new ArrayList<>();
		public boolean translucent;
		/** true: машут вокруг вертикали (бабочки), иначе вверх-вниз. */
		public boolean flapAboutY;
		public float restSweep = 25.0f;
		public float restUp = 12.0f;
		public float alpha = 1.0f;
	}

	public static Wing get(int index) {
		int i = Math.max(0, Math.min(NAMES.length - 1, index));
		if (CACHE[i] == null) {
			CACHE[i] = switch (i) {
				case 1 -> membrane(false);
				case 2 -> butterfly();
				case 3 -> membrane(true);
				case 4 -> fan(0);
				case 5 -> fan(1);
				case 6 -> fan(2);
				default -> angel();
			};
		}
		return CACHE[i];
	}

	// ---------- Заготовки ----------

	private static VoxelPart feather(float len, float w, float th, int c0, int c1, int tip) {
		Sdf e = Sdf.ellipsoid(len / 2, 0, 0, len / 2, w, th);
		Sdf.Paint paint = (x, y, z) -> {
			float t = Math.max(0.0f, Math.min(1.0f, x / len));
			int c = Voxels.mix(c0, c1, t);
			if (t > 0.86f) c = Voxels.mix(c, tip, (t - 0.86f) / 0.14f);
			if (Math.abs(y) < 0.008f) c = Voxels.mix(c, 0xFFFFFF, 0.45f);
			return c;
		};
		return VoxelPart.build(0.02f, -0.03f, -w - 0.03f, -th - 0.03f, len + 0.03f, w + 0.03f, th + 0.03f, e, paint, 0, 0, 0);
	}

	private static VoxelPart bone(float len, float r0, float r1, int c0, int c1) {
		Sdf s = Sdf.capsule(0, 0, 0, len, 0, 0, r0, r1);
		Sdf.Paint paint = (x, y, z) -> Voxels.mix(c0, c1, Math.max(0.0f, Math.min(1.0f, x / len)));
		return VoxelPart.build(0.018f, -r0 - 0.02f, -r0 - 0.02f, -r0 - 0.02f, len + r0 + 0.02f, r0 + 0.02f, r0 + 0.02f,
				s, paint, 0, 0, 0);
	}

	/** Перепонка-клин от корня на длину len с половиной раскрыва half (в радианах). */
	private static VoxelPart wedge(float len, float half, float th, int c0, int c1, int vein) {
		float tanH = (float) Math.tan(half);
		Sdf s = (x, y, z) -> {
			// Дальний край слегка «дугой» внутрь, как у настоящей перепонки
			float edge = len * (1.0f - 0.1f * (float) Math.min(1.0, Math.abs(y) / Math.max(0.01f, len * tanH)));
			float d = Math.max(Math.abs(y) - x * tanH, x - edge);
			return Math.max(d, Math.max(Math.abs(z) - th, -x));
		};
		Sdf.Paint paint = (x, y, z) -> {
			float t = Math.max(0.0f, Math.min(1.0f, x / len));
			int c = Voxels.mix(c0, c1, t);
			if (Math.abs(y) < 0.01f + x * 0.02f) c = Voxels.mix(c, vein, 0.55f);
			return Voxels.mix(c, vein, Voxels.noise(x * 18, y * 18, 0.0f) * 0.25f);
		};
		float ext = len * tanH + 0.04f;
		return VoxelPart.build(0.02f, -0.02f, -ext, -th - 0.03f, len + 0.03f, ext, th + 0.03f, s, paint, 0, 0, 0);
	}

	// ---------- Виды ----------

	private static Wing angel() {
		Wing w = new Wing();
		w.restSweep = 22.0f;
		w.restUp = 10.0f;
		// Рука: толстая кость вверх-наружу
		w.parts.add(new Part(bone(0.38f, 0.045f, 0.03f, 0xE8E8EE, 0xD0D4E0), 0, 0, 0, 40.0f, 0.0f));
		// Маховые перья веером: сверху вниз, самые длинные ближе к низу
		int n = 8;
		for (int i = 0; i < n; i++) {
			float f = i / (float) (n - 1);
			float spread = 38.0f - f * 118.0f;
			float len = 0.92f - 0.045f * Math.abs(i - 5);
			float zoff = (i % 2 == 0) ? -0.006f : 0.006f;
			w.parts.add(new Part(feather(len, 0.075f, 0.02f, 0xF7F7FA, 0xDDE2EE, 0xF3D88A),
					0.05f, 0.03f, zoff, spread, 1.0f));
		}
		// Второй ряд — короче, выше по руке
		int m = 6;
		for (int i = 0; i < m; i++) {
			float f = i / (float) (m - 1);
			float spread = 56.0f - f * 90.0f;
			w.parts.add(new Part(feather(0.52f, 0.06f, 0.018f, 0xFFFFFF, 0xEEF1F8, 0xFFF1C2),
					0.16f, 0.09f, 0.016f, spread, 0.7f));
		}
		return w;
	}

	private static Wing membrane(boolean demon) {
		Wing w = new Wing();
		w.restSweep = 28.0f;
		w.restUp = 14.0f;
		int boneA = demon ? 0x2A0F12 : 0x1E3B2C;
		int boneB = demon ? 0x120608 : 0x15291F;
		int memA = demon ? 0x7A0F1F : 0x2E7A52;
		int memB = demon ? 0xC42433 : 0x59BE82;
		int vein = demon ? 0x1B0509 : 0x113022;
		float scale = demon ? 1.12f : 1.0f;

		// Рука: плечо → локоть → запястье. Запястье W = (0.55, 0.2)
		w.parts.add(new Part(bone(0.382f, 0.045f, 0.035f, boneA, boneB), 0, 0, 0, 42.9f, 0.0f));
		w.parts.add(new Part(bone(0.277f, 0.035f, 0.028f, boneA, boneB), 0.28f, 0.26f, 0, -12.5f, 0.0f));

		float[] ang = {25.0f, -5.0f, -35.0f, -65.0f};
		float[] len = {0.8f, 0.9f, 0.8f, 0.62f};
		float wx = 0.55f;
		float wy = 0.2f;
		for (int i = 0; i < 4; i++) {
			w.parts.add(new Part(bone(len[i] * scale, 0.024f, 0.008f, boneA, boneB), wx, wy, 0, ang[i], 1.0f));
		}
		// Перепонки между пальцами
		for (int i = 0; i < 3; i++) {
			float mid = (ang[i] + ang[i + 1]) / 2.0f;
			float half = (float) Math.toRadians((ang[i] - ang[i + 1]) / 2.0f);
			float l = (len[i] + len[i + 1]) / 2.0f * 0.96f * scale;
			w.parts.add(new Part(wedge(l, half, 0.012f, memA, memB, vein), wx, wy, -0.004f, mid, 1.0f));
		}
		// Перепонка к телу: от последнего пальца вниз к спине
		float lastHalf = (float) Math.toRadians(32.0f);
		w.parts.add(new Part(wedge(0.62f * scale, lastHalf, 0.012f, memA, memB, vein), wx, wy, -0.004f, -97.0f, 1.0f));
		return w;
	}

	private static Wing butterfly() {
		Wing w = new Wing();
		w.translucent = true;
		w.alpha = 0.9f;
		w.flapAboutY = true;
		w.restSweep = 24.0f;
		w.restUp = 0.0f;
		Sdf upper = Sdf.ellipsoid(0.3f, 0.2f, 0, 0.3f, 0.26f, 0.012f);
		Sdf lower = Sdf.ellipsoid(0.2f, -0.2f, 0, 0.2f, 0.24f, 0.012f);
		Sdf s = Sdf.smooth(0.05f, upper, lower);
		Sdf.Paint paint = (x, y, z) -> {
			boolean up = y > -0.02f;
			float cx = up ? 0.3f : 0.2f;
			float cy = up ? 0.2f : -0.2f;
			float rx = up ? 0.3f : 0.2f;
			float ry = up ? 0.26f : 0.24f;
			float u = (float) Math.sqrt(((x - cx) / rx) * ((x - cx) / rx) + ((y - cy) / ry) * ((y - cy) / ry));
			if (u > 0.88f) return 0x14102A;
			if (u > 0.7f && Voxels.grain(x, y, z, 0.05f) > 0.72f) return 0xFFFFFF;
			if (u > 0.55f) return up ? 0xF08A1E : 0xF5A93A;
			return Voxels.mix(0x4A6BE0, 0x8A4AD8, u / 0.55f);
		};
		w.parts.add(new Part(VoxelPart.build(0.02f, -0.03f, -0.5f, -0.04f, 0.65f, 0.5f, 0.04f, s, paint, 0, 0, 0),
				0, 0, 0, 0.0f, 0.0f));
		return w;
	}

	// ---------- Новые крылья-веера: кристаллы, огонь, механика (воксельные) ----------

	/** Одно «перо»: вытянутый воксельный клинок вдоль +X. style: 0 кристалл, 1 пламя, 2 механика. */
	private static VoxelPart blade(int style, float len, float w, float th) {
		Voxels v = Art.grid(-0.03f, -w - 0.03f, -th - 0.03f, len + 0.03f, w + 0.03f, th + 0.03f);
		float hz = Math.max(th, Art.R * 0.6f);
		v.fill(0, -w, -hz, len, w, hz, (x, y, z) -> {
			float t = x / len;
			float prof;
			if (style == 0) {
				prof = t < 0.3f ? 0.15f + 0.85f * (t / 0.3f) : 1.0f - 0.95f * ((t - 0.3f) / 0.7f);
			} else if (style == 1) {
				prof = (float) Math.pow(Math.sin(Math.PI * Math.min(1.0f, t * 0.98f + 0.02f)), 0.7f);
			} else {
				prof = t < 0.82f ? 1.0f - 0.3f * t : (1.0f - t) / 0.18f * 0.75f;
			}
			return Math.abs(y) <= w * prof;
		}, (x, y, z) -> {
			float t = Math.max(0.0f, Math.min(1.0f, x / len));
			int c;
			if (style == 0) {
				c = Voxels.mix(0x3FB6E8, 0xE8FBFF, t);
				if (Math.abs(y) < 0.014f) c = Voxels.mix(c, 0xFFFFFF, 0.6f);
			} else if (style == 1) {
				c = t < 0.5f ? Voxels.mix(0xC81E1E, 0xFF7A1A, t / 0.5f) : Voxels.mix(0xFF7A1A, 0xFFE35A, (t - 0.5f) / 0.5f);
				if (Math.abs(y) < 0.012f) c = Voxels.mix(c, 0xFFF3B0, 0.5f);
			} else {
				c = Voxels.mix(0x8E98A6, 0xC9D1DC, t);
				if (Math.abs(y) < 0.012f) c = 0x45E0FF;
				else if (Math.abs(y) > w * 0.8f) c = 0xD9A93A;
				if (((int) Math.floor(x / 0.1f)) % 2 == 0 && Math.abs(y) >= 0.012f) c = Voxels.lum(c, 0.88f);
			}
			return Voxels.lum(c, 0.94f + 0.12f * Voxels.grain(x, y, z, Art.R));
		});
		return Art.part(v);
	}

	private static Wing fan(int style) {
		Wing w = new Wing();
		w.restSweep = style == 2 ? 30.0f : 24.0f;
		w.restUp = 10.0f;
		w.translucent = style == 0;
		w.alpha = style == 0 ? 0.82f : 1.0f;
		int a = style == 0 ? 0xBFEFFF : style == 1 ? 0x7A1010 : 0x7A8492;
		int b = style == 0 ? 0x8ADBFF : style == 1 ? 0xB43014 : 0x56606E;
		w.parts.add(new Part(bone(0.38f, 0.045f, 0.03f, a, b), 0, 0, 0, 40.0f, 0.0f));
		int n = 6;
		for (int i = 0; i < n; i++) {
			float f = i / (float) (n - 1);
			float len = 0.95f - 0.06f * Math.abs(i - 3);
			w.parts.add(new Part(blade(style, len, 0.08f, 0.02f), 0.05f, 0.03f, (i % 2 == 0) ? -0.012f : 0.012f, 38.0f - f * 118.0f, 1.0f));
		}
		int m = 4;
		for (int i = 0; i < m; i++) {
			float f = i / (float) (m - 1);
			w.parts.add(new Part(blade(style, 0.52f, 0.065f, 0.018f), 0.16f, 0.09f, 0.018f, 56.0f - f * 90.0f, 0.7f));
		}
		return w;
	}
}
