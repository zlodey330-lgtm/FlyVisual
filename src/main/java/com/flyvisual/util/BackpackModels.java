package com.flyvisual.util;

/**
 * Рюкзаки. Начало координат — середина спины игрока (на поверхности тела), ось Y вверх,
 * перёд игрока смотрит в +Z, рюкзак выступает назад (в -Z). Собраны из гладких форм, как питомцы.
 */
public final class BackpackModels {
	public static final String[] NAMES = {"Походный", "Школьный", "Реактивный ранец", "Колчан со стрелами", "Меч за спиной", "Щит", "Гитара", "Мешок Санты",
			"Медвежонок"};
	private static final VoxelPart[] CACHE = new VoxelPart[NAMES.length];
	private static final float V = 0.02f;

	private BackpackModels() {
	}

	public static VoxelPart get(int index) {
		int i = Math.max(0, Math.min(NAMES.length - 1, index));
		if (CACHE[i] == null) {
			CACHE[i] = switch (i) {
				case 1 -> school();
				case 2 -> jet();
				case 3 -> quiver();
				case 4 -> sword();
				case 5 -> shield();
				case 6 -> guitar();
				case 7 -> santaSack();
				case 8 -> bear();
				default -> hiking();
			};
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

	// ---------- Новые рюкзаки (воксельные, как питомцы) ----------

	private static Voxels gp() {
		return Art.grid(-0.5f, -0.55f, -0.55f, 0.5f, 0.7f, 0.1f);
	}

	private static void strap(Voxels v, int rgb) {
		v.tube(-0.25f, 0.33f, -0.012f, 0.022f, 0.25f, -0.3f, -0.012f, 0.022f, Art.tex(rgb));
	}

	private static VoxelPart quiver() {
		Voxels v = gp();
		int leather = 0x7A4B2A;
		// Колчан по диагонали
		v.tube(0.12f, -0.34f, -0.11f, 0.075f, -0.05f, 0.3f, -0.14f, 0.08f, Art.tex(leather));
		v.tube(0.1f, -0.3f, -0.11f, 0.08f, 0.1f, -0.26f, -0.11f, 0.082f, Art.tex(0x4A2D18));
		v.tube(-0.03f, 0.2f, -0.14f, 0.085f, -0.05f, 0.3f, -0.14f, 0.082f, Art.tex(0x4A2D18));
		// Стрелы с оперением
		int[] fl = {0xD33A3A, 0xF2F2F2, 0xD33A3A, 0xF2F2F2};
		for (int i = 0; i < 4; i++) {
			float ox = -0.07f + i * 0.045f;
			float tx = -0.14f + i * 0.09f;
			v.tube(ox, 0.28f, -0.14f, 0.012f, tx, 0.56f, -0.2f + (i % 2) * 0.05f, 0.012f, Art.tex(0xD9C28A));
			v.tube(tx, 0.56f, -0.2f + (i % 2) * 0.05f, 0.03f, tx, 0.64f, -0.2f + (i % 2) * 0.05f, 0.005f, Art.flat(fl[i]));
		}
		strap(v, 0x3A2616);
		return Art.part(v);
	}

	private static VoxelPart sword() {
		Voxels v = gp();
		float hx = -0.17f;
		float hy = 0.42f;
		float tx = 0.2f;
		float ty = -0.46f;
		// Клинок со светлым ребром
		v.tube(hx + 0.03f, hy - 0.08f, -0.07f, 0.03f, tx, ty, -0.07f, 0.012f, Art.tex(0xCFD6DE));
		v.tube(hx + 0.03f, hy - 0.08f, -0.095f, 0.012f, tx, ty, -0.095f, 0.006f, Art.flat(0xF4F7FA));
		// Гарда, рукоять и навершие
		v.tube(hx - 0.1f, hy - 0.02f, -0.07f, 0.02f, hx + 0.11f, hy - 0.11f, -0.07f, 0.02f, Art.tex(0xD9A93A));
		v.tube(hx - 0.01f, hy + 0.01f, -0.07f, 0.026f, hx - 0.05f, hy + 0.1f, -0.07f, 0.026f, Art.tex(0x5A3622));
		v.ellipsoid(hx - 0.055f, hy + 0.12f, -0.07f, 0.04f, 0.04f, 0.04f, Art.tex(0xD9A93A));
		v.dot(hx - 0.055f, hy + 0.14f, -0.1f, 0xD33A3A);
		strap(v, 0x3A2616);
		return Art.part(v);
	}

	private static VoxelPart shield() {
		Voxels v = gp();
		Art.cylZ(v, 0, 0.0f, 0.29f, -0.06f, -0.03f, Art.tex(0x4A4F58));
		Art.cylZ(v, 0, 0.0f, 0.26f, -0.1f, -0.055f, Art.tex(0x9A6A36));
		// Доски и железные полосы
		for (int i = -3; i <= 3; i++) {
			Art.paint(v, i * 0.075f - 0.003f, -0.3f, -0.12f, i * 0.075f + 0.003f, 0.3f, -0.05f, 0x6B4624);
		}
		Art.paint(v, -0.3f, -0.07f, -0.12f, 0.3f, -0.03f, -0.05f, 0x5A6068);
		Art.paint(v, -0.3f, 0.12f, -0.12f, 0.3f, 0.15f, -0.05f, 0x5A6068);
		Art.paint(v, -0.3f, -0.15f, -0.12f, 0.3f, -0.12f, -0.05f, 0x5A6068);
		Art.cylZ(v, 0, 0.0f, 0.09f, -0.14f, -0.09f, Art.tex(0xB9C0C9));
		Art.cylZ(v, 0, 0.0f, 0.04f, -0.16f, -0.13f, Art.tex(0xE3E8EE));
		strap(v, 0x3A2616);
		return Art.part(v);
	}

	private static VoxelPart guitar() {
		Voxels v = gp();
		int body = 0xD9782B;
		int edge = 0x4A2410;
		float z = -0.07f;
		// Корпус «восьмёркой» с тёмным ободом
		Art.rbox(v, -0.04f, -0.28f, z, 0.19f, 0.2f, 0.045f, Art.tex(edge));
		Art.rbox(v, -0.02f, -0.02f, z, 0.14f, 0.14f, 0.045f, Art.tex(edge));
		Art.rbox(v, -0.04f, -0.28f, z - 0.03f, 0.17f, 0.18f, 0.03f, Art.tex(body));
		Art.rbox(v, -0.02f, -0.02f, z - 0.03f, 0.12f, 0.12f, 0.03f, Art.tex(body));
		Art.cylZ(v, -0.03f, -0.2f, 0.06f, z - 0.075f, z - 0.05f, Art.flat(0x1A0F0A));
		v.box(-0.12f, -0.33f, z - 0.085f, 0.06f, -0.31f, z - 0.05f, Art.flat(0xF2E3C0));
		// Гриф и колки
		v.tube(0.0f, 0.1f, z, 0.025f, 0.22f, 0.5f, z, 0.025f, Art.tex(0x6B4426));
		v.box(0.19f, 0.5f, z - 0.03f, 0.27f, 0.62f, z + 0.03f, Art.tex(0x3A2616));
		strap(v, 0xD33A3A);
		return Art.part(v);
	}

	private static VoxelPart santaSack() {
		Voxels v = gp();
		int red = 0xB5322B;
		Art.rbox(v, 0, -0.12f, -0.14f, 0.24f, 0.3f, 0.14f, Art.tex(red));
		Art.rbox(v, 0, 0.18f, -0.12f, 0.15f, 0.13f, 0.11f, Art.tex(0x9E2A24));
		// Завязка и подарки, торчащие сверху
		Art.ring(v, 0, 0.26f, -0.12f, 0.12f, 0.07f, 0.05f, Art.tex(0xD9A93A));
		v.box(-0.09f, 0.28f, -0.19f, 0.01f, 0.4f, -0.09f, Art.tex(0x2F8F4E));
		Art.paint(v, -0.09f, 0.28f, -0.2f, 0.01f, 0.4f, -0.18f, 0xF2C230);
		v.box(0.0f, 0.28f, -0.17f, 0.11f, 0.35f, -0.07f, Art.tex(0x2B6FD6));
		Art.paint(v, 0.04f, 0.28f, -0.18f, 0.07f, 0.35f, -0.06f, 0xF2F2F2);
		// Меховая заплатка
		Art.paint(v, -0.1f, -0.25f, -0.29f, 0.1f, -0.12f, -0.2f, 0xF5F5F5);
		return Art.part(v);
	}

	private static VoxelPart bear() {
		Voxels v = gp();
		int fur = 0x8B5A2B;
		Art.rbox(v, 0, -0.04f, -0.14f, 0.23f, 0.28f, 0.13f, Art.tex(fur));
		for (int s = -1; s <= 1; s += 2) {
			v.ellipsoid(s * 0.19f, 0.27f, -0.12f, 0.075f, 0.075f, 0.05f, Art.tex(fur));
			v.ellipsoid(s * 0.19f, 0.27f, -0.165f, 0.04f, 0.04f, 0.02f, Art.flat(0xE8C9A0));
			// Лапки
			Art.rbox(v, s * 0.2f, -0.28f, -0.1f, 0.07f, 0.07f, 0.06f, Art.tex(0x75491F));
			// Глаза
			v.box(s * 0.09f - 0.025f, 0.05f, -0.275f, s * 0.09f + 0.025f, 0.1f, -0.25f, Art.flat(0x14100E));
			v.dot(s * 0.09f + 0.012f, 0.09f, -0.28f, 0xFFFFFF);
		}
		Art.rbox(v, 0, -0.07f, -0.27f, 0.1f, 0.075f, 0.05f, Art.tex(0xE8C9A0));
		v.box(-0.025f, -0.05f, -0.325f, 0.025f, -0.01f, -0.295f, Art.flat(0x14100E));
		Art.paint(v, -0.04f, -0.12f, -0.33f, 0.04f, -0.105f, -0.28f, 0x14100E);
		strap(v, 0x5A3622);
		return Art.part(v);
	}
}
