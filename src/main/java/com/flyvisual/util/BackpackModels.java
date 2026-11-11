package com.flyvisual.util;

/**
 * Рюкзаки. Начало координат — середина спины игрока (на поверхности тела), ось Y вверх,
 * перёд игрока смотрит в +Z, рюкзак выступает назад (в -Z). Собраны из гладких форм, как питомцы.
 */
public final class BackpackModels {
	public static final String[] NAMES = {"Походный", "Школьный", "Реактивный ранец"};
	private static final VoxelPart[] CACHE = new VoxelPart[NAMES.length];
	private static final float V = 0.02f;

	private BackpackModels() {
	}

	public static VoxelPart get(int index) {
		int i = Math.max(0, Math.min(NAMES.length - 1, index));
		if (CACHE[i] == null) {
			CACHE[i] = i == 1 ? school() : i == 2 ? jet() : hiking();
		}
		return CACHE[i];
	}

	/** Скруглённая коробка: половины размеров hx, hy, hz уже с учётом скругления r. */
	private static Sdf roundBox(float cx, float cy, float cz, float hx, float hy, float hz, float r) {
		Sdf b = Sdf.box(cx, cy, cz, hx - r, hy - r, hz - r);
		return (x, y, z) -> b.d(x, y, z) - r;
	}

	private static Sdf cylY(float cx, float cz, float r, float y0, float y1) {
		return (x, y, z) -> {
			float d = (float) Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz)) - r;
			return Math.max(d, Math.max(y0 - y, y - y1));
		};
	}

	private static VoxelPart hiking() {
		Sdf main = Sdf.ellipsoid(0, -0.02f, -0.15f, 0.2f, 0.28f, 0.14f);
		Sdf roll = Sdf.capsule(-0.19f, 0.31f, -0.14f, 0.19f, 0.31f, -0.14f, 0.07f, 0.07f);
		Sdf pockets = Sdf.mirrorX(Sdf.ellipsoid(0.2f, -0.13f, -0.14f, 0.06f, 0.12f, 0.09f));
		Sdf front = roundBox(0, -0.11f, -0.27f, 0.14f, 0.11f, 0.05f, 0.03f);
		Sdf all = Sdf.smooth(0.04f, main, roll, pockets, front);
		Sdf.Paint paint = (x, y, z) -> {
			if (y > 0.23f && z < 0.0f && Math.abs(x) < 0.22f && y > 0.23f) return 0xD9722B; // скатка сверху
			if (z < -0.245f && y < 0.0f && y > -0.23f) return 0x3F4A2A; // передний карман
			if (Math.abs(x) > 0.2f && y < 0.0f) return 0x58663A;
			return Voxels.mix(0x6B7A45, 0x7F8F52, Voxels.noise(x * 14, y * 14, z * 14));
		};
		return VoxelPart.build(V, -0.35f, -0.4f, -0.45f, 0.35f, 0.42f, 0.0f, all, paint, 0, 0, 0);
	}

	private static VoxelPart school() {
		Sdf main = roundBox(0, -0.03f, -0.13f, 0.19f, 0.25f, 0.1f, 0.05f);
		Sdf pocket = roundBox(0, -0.12f, -0.235f, 0.14f, 0.12f, 0.04f, 0.03f);
		Sdf handle = Sdf.capsule(-0.06f, 0.22f, -0.13f, 0.06f, 0.22f, -0.13f, 0.02f, 0.02f);
		Sdf all = Sdf.smooth(0.03f, main, pocket, handle);
		Sdf.Paint paint = (x, y, z) -> {
			if (y > 0.2f && Math.abs(x) < 0.08f) return 0x1F2A44;
			if (z < -0.22f) {
				if (Math.abs(y + 0.02f) < 0.012f) return 0xF2C230; // молния кармана
				return 0x3E7BD6;
			}
			if (Math.abs(y - 0.12f) < 0.012f) return 0xF2C230; // молния основного отделения
			return Voxels.mix(0x2A5FB0, 0x3472C9, Voxels.noise(x * 12, y * 12, z * 12));
		};
		return VoxelPart.build(V, -0.3f, -0.4f, -0.4f, 0.3f, 0.35f, 0.0f, all, paint, 0, 0, 0);
	}

	private static VoxelPart jet() {
		Sdf tanks = Sdf.mirrorX(Sdf.smooth(0.03f, cylY(0.1f, -0.15f, 0.085f, -0.2f, 0.28f),
				Sdf.ellipsoid(0.1f, 0.28f, -0.15f, 0.085f, 0.05f, 0.085f)));
		Sdf nozzles = Sdf.mirrorX(cylY(0.1f, -0.15f, 0.06f, -0.32f, -0.2f));
		Sdf plate = roundBox(0, 0.04f, -0.09f, 0.14f, 0.2f, 0.035f, 0.02f);
		Sdf all = Sdf.smooth(0.02f, tanks, plate, nozzles);
		Sdf.Paint paint = (x, y, z) -> {
			if (y < -0.2f) return y < -0.29f ? 0xFF8A1F : 0x3A3A42; // раскалённый срез сопла
			if (Math.abs(y - 0.18f) < 0.015f || Math.abs(y + 0.1f) < 0.015f) return 0xD4382C; // красные кольца
			if (Math.abs(x) < 0.07f && z > -0.14f) return 0x4A4F5C;
			return Voxels.mix(0xA7ADB8, 0xD2D7E0, Voxels.noise(x * 20, y * 20, z * 20));
		};
		return VoxelPart.build(V, -0.28f, -0.38f, -0.3f, 0.28f, 0.4f, 0.0f, all, paint, 0, 0, 0);
	}
}
