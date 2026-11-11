package com.flyvisual.util;

/**
 * Маски на лицо. Начало координат — центр головы, ось Y вверх, перёд (лицо) смотрит в +Z.
 * Голова игрока — куб 0,5 блока, лицо на z = 0,25, поэтому маски выступают вперёд от 0,25.
 */
public final class MaskModels {
	public static final String[] NAMES = {"Хоккейная", "Ниндзя", "Карнавальная", "Хирургическая"};
	private static final VoxelPart[] CACHE = new VoxelPart[NAMES.length];
	private static final float V = 0.016f;

	private MaskModels() {
	}

	public static VoxelPart get(int index) {
		int i = Math.max(0, Math.min(NAMES.length - 1, index));
		if (CACHE[i] == null) {
			CACHE[i] = i == 1 ? ninja() : i == 2 ? carnival() : i == 3 ? surgical() : hockey();
		}
		return CACHE[i];
	}

	private static Sdf sphere(float cx, float cy, float cz, float r) {
		return Sdf.sphere(cx, cy, cz, r);
	}

	private static VoxelPart hockey() {
		// Выпуклая белая «тарелка» на лице с прорезями для глаз и вентиляцией
		Sdf shell = Sdf.ellipsoid(0, -0.02f, 0.2f, 0.235f, 0.27f, 0.12f);
		Sdf cut = Sdf.intersect(shell, Sdf.box(0, 0, 0.35f, 0.3f, 0.3f, 0.1f)); // только передняя часть
		Sdf eyes = Sdf.mirrorX(Sdf.ellipsoid(0.095f, 0.07f, 0.34f, 0.055f, 0.035f, 0.1f));
		Sdf holes = Sdf.mirrorX(sphere(0.07f, -0.1f, 0.34f, 0.018f));
		Sdf holes2 = Sdf.mirrorX(sphere(0.07f, -0.17f, 0.33f, 0.018f));
		Sdf holes3 = sphere(0, -0.13f, 0.34f, 0.018f);
		Sdf mask = Sdf.subtract(Sdf.subtract(Sdf.subtract(Sdf.subtract(cut, eyes), holes), holes2), holes3);
		Sdf.Paint paint = (x, y, z) -> {
			if (Math.abs(x) < 0.022f && y > 0.12f) return 0xC9372C; // красная полоса по центру
			if (Math.abs(x - 0.17f) < 0.012f || Math.abs(x + 0.17f) < 0.012f) return 0xC9372C;
			return Voxels.mix(0xE9E9E4, 0xFFFFFF, Voxels.noise(x * 18, y * 18, z * 18));
		};
		return VoxelPart.build(V, -0.3f, -0.34f, 0.2f, 0.3f, 0.3f, 0.4f, mask, paint, 0, 0, 0);
	}

	private static VoxelPart ninja() {
		// Чёрный платок на нижней части лица и повязка на лбу с красной нашивкой
		Sdf lower = Sdf.intersect(Sdf.ellipsoid(0, -0.1f, 0.0f, 0.265f, 0.2f, 0.275f),
				Sdf.box(0, -0.16f, 0.15f, 0.3f, 0.12f, 0.2f));
		Sdf lowerShell = Sdf.subtract(lower, Sdf.ellipsoid(0, -0.1f, 0.0f, 0.245f, 0.2f, 0.255f));
		Sdf band = Sdf.subtract(Sdf.ellipsoid(0, 0.15f, 0.0f, 0.268f, 0.065f, 0.268f),
				Sdf.ellipsoid(0, 0.15f, 0.0f, 0.245f, 0.065f, 0.245f));
		Sdf tail = Sdf.capsule(0.0f, 0.15f, -0.27f, 0.12f, 0.02f, -0.36f, 0.025f, 0.012f);
		Sdf all = Sdf.smooth(0.02f, lowerShell, band, tail);
		Sdf.Paint paint = (x, y, z) -> {
			if (y > 0.1f && z > 0.2f && Math.abs(x) < 0.05f) return 0xB0B6C2; // металлическая пластина
			return Voxels.mix(0x14141A, 0x24242C, Voxels.noise(x * 20, y * 20, z * 20));
		};
		return VoxelPart.build(V, -0.32f, -0.34f, -0.4f, 0.32f, 0.26f, 0.32f, all, paint, 0, 0, 0);
	}

	private static VoxelPart carnival() {
		// Золотая маска на глаза с вырезами и пером сбоку
		Sdf plate = Sdf.ellipsoid(0, 0.06f, 0.2f, 0.27f, 0.085f, 0.1f);
		Sdf front = Sdf.intersect(plate, Sdf.box(0, 0.06f, 0.33f, 0.3f, 0.1f, 0.12f));
		Sdf eyes = Sdf.mirrorX(Sdf.ellipsoid(0.1f, 0.06f, 0.33f, 0.055f, 0.03f, 0.1f));
		Sdf nose = Sdf.ellipsoid(0, 0.0f, 0.31f, 0.03f, 0.045f, 0.04f);
		Sdf feather = Sdf.capsule(0.24f, 0.1f, 0.2f, 0.34f, 0.3f, 0.05f, 0.025f, 0.008f);
		Sdf all = Sdf.smooth(0.02f, Sdf.subtract(front, eyes), nose, feather);
		Sdf.Paint paint = (x, y, z) -> {
			if (x > 0.26f && y > 0.12f) return 0xC8246B; // розовое перо
			return Voxels.mix(0xE2B33A, 0xFFE27A, Voxels.noise(x * 22, y * 22, z * 22));
		};
		return VoxelPart.build(V, -0.32f, -0.06f, 0.1f, 0.4f, 0.34f, 0.42f, all, paint, 0, 0, 0);
	}

	private static VoxelPart surgical() {
		// Голубая медицинская маска на нижней половине лица, с петлями за уши
		Sdf cover = Sdf.intersect(Sdf.ellipsoid(0, -0.11f, 0.2f, 0.25f, 0.15f, 0.12f),
				Sdf.box(0, -0.12f, 0.33f, 0.3f, 0.14f, 0.1f));
		Sdf loops = Sdf.mirrorX(Sdf.capsule(0.24f, -0.1f, 0.2f, 0.27f, -0.06f, -0.02f, 0.012f, 0.012f));
		Sdf all = Sdf.smooth(0.015f, cover, loops);
		Sdf.Paint paint = (x, y, z) -> {
			// Складки по горизонтали
			if (z > 0.25f && (Math.floor((y + 0.4f) / 0.045f) % 2 == 0)) return 0x9EC8E6;
			return Voxels.mix(0xBFE0F5, 0xDDF0FC, Voxels.noise(x * 20, y * 20, z * 20));
		};
		return VoxelPart.build(V, -0.32f, -0.32f, -0.05f, 0.32f, 0.04f, 0.4f, all, paint, 0, 0, 0);
	}
}
