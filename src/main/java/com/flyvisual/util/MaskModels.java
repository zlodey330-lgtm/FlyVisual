package com.flyvisual.util;

import static com.flyvisual.util.Art.*;

/**
 * Маски. Воксельные, как питомцы. Начало координат — центр головы по горизонтали, голова: x и z от -0.25 до 0.25,
 * y от -0.15 (подбородок) до 0.35 (макушка); лицо смотрит в +Z, глаза на высоте y = 0.1.
 */
public final class MaskModels {
	public static final String[] NAMES = {"Чумной доктор", "Кицунэ", "Череп", "Противогаз", "Ниндзя", "Солнечные очки", "Робот-визор",
			"Карнавальная"};
	private static final VoxelPart[] CACHE = new VoxelPart[NAMES.length];

	private MaskModels() {
	}

	public static VoxelPart get(int index) {
		int i = Math.max(0, Math.min(NAMES.length - 1, index));
		if (CACHE[i] == null) {
			CACHE[i] = switch (i) {
				case 1 -> kitsune();
				case 2 -> skull();
				case 3 -> gasMask();
				case 4 -> ninja();
				case 5 -> sunglasses();
				case 6 -> visor();
				case 7 -> carnival();
				default -> plague();
			};
		}
		return CACHE[i];
	}

	private static Voxels g() {
		return grid(-0.4f, -0.3f, -0.4f, 0.4f, 0.55f, 0.75f);
	}

	private static VoxelPart plague() {
		Voxels v = g();
		int bone = 0xD9C9A3;
		int dark = 0x3B2F2A;
		// Накладка на верх лица и длинный изогнутый клюв
		rbox(v, 0, 0.06f, 0.26f, 0.24f, 0.17f, 0.035f, tex(bone));
		v.tube(0, 0.04f, 0.27f, 0.085f, 0, 0.0f, 0.43f, 0.065f, tex(bone));
		v.tube(0, 0.0f, 0.43f, 0.065f, 0, -0.07f, 0.6f, 0.02f, tex(0xC4B58E));
		// Круглые стёкла-очки
		for (int s = -1; s <= 1; s += 2) {
			cylZ(v, s * 0.1f, 0.115f, 0.075f, 0.265f, 0.31f, tex(dark));
			cylZ(v, s * 0.1f, 0.115f, 0.052f, 0.29f, 0.325f, flat(0xE6D56A));
			v.dot(s * 0.1f + 0.015f, 0.14f, 0.326f, 0xFFFFFF);
		}
		// Ремни вокруг головы
		ring(v, 0, 0.05f, 0.0f, 0.275f, 0.255f, 0.04f, tex(dark));
		ring(v, 0, -0.04f, 0.0f, 0.275f, 0.255f, 0.04f, tex(dark));
		return part(v);
	}

	private static VoxelPart kitsune() {
		Voxels v = g();
		int white = 0xF4F1EA;
		int red = 0xD0312D;
		rbox(v, 0, 0.1f, 0.265f, 0.245f, 0.245f, 0.03f, tex(white));
		// Вытянутая морда
		v.tube(0, -0.02f, 0.28f, 0.1f, 0, -0.04f, 0.42f, 0.045f, tex(white));
		for (int s = -1; s <= 1; s += 2) {
			// Острые уши
			cone(v, s * 0.17f, 0.3f, 0.25f, 0.07f, 0.2f, 0.03f, tex(white));
			cone(v, s * 0.17f, 0.31f, 0.28f, 0.035f, 0.13f, 0.01f, flat(red));
			// Прорези глаз и красные полосы
			paint(v, s * 0.12f - 0.05f, 0.1f, 0.26f, s * 0.12f + 0.05f, 0.125f, 0.45f, 0x1A1A1A);
			paint(v, s * 0.12f - 0.05f, 0.125f, 0.26f, s * 0.12f + 0.05f, 0.15f, 0.45f, red);
			paint(v, s * 0.19f - 0.015f, -0.1f, 0.26f, s * 0.19f + 0.015f, 0.05f, 0.45f, red);
			paint(v, s * 0.14f - 0.015f, -0.08f, 0.26f, s * 0.14f + 0.015f, 0.03f, 0.45f, red);
		}
		paint(v, -0.04f, 0.2f, 0.26f, 0.04f, 0.3f, 0.45f, red);
		paint(v, -0.025f, -0.045f, 0.4f, 0.025f, -0.01f, 0.5f, 0x1A1A1A);
		// Завязки
		ring(v, 0, 0.12f, 0.0f, 0.28f, 0.255f, 0.03f, flat(red));
		return part(v);
	}

	private static VoxelPart skull() {
		Voxels v = g();
		int bone = 0xEDE7D6;
		rbox(v, 0, 0.1f, 0.255f, 0.255f, 0.255f, 0.04f, tex(bone));
		// Верхняя часть лба и скулы
		rbox(v, 0, -0.1f, 0.3f, 0.2f, 0.07f, 0.05f, tex(bone));
		for (int s = -1; s <= 1; s += 2) {
			// Глазницы
			paint(v, s * 0.105f - 0.065f, 0.045f, 0.25f, s * 0.105f + 0.065f, 0.155f, 0.45f, 0x0C0C10);
			paint(v, s * 0.105f - 0.02f, 0.085f, 0.25f, s * 0.105f + 0.02f, 0.115f, 0.45f, 0xB02020);
		}
		// Нос
		paint(v, -0.02f, -0.025f, 0.25f, 0.02f, 0.035f, 0.45f, 0x0C0C10);
		// Зубы
		for (int i = -4; i <= 4; i++) {
			paint(v, i * 0.04f - 0.012f, -0.17f, 0.25f, i * 0.04f + 0.012f, -0.07f, 0.45f, 0x2A2A30);
		}
		paint(v, -0.2f, -0.115f, 0.25f, 0.2f, -0.105f, 0.45f, 0x2A2A30);
		return part(v);
	}

	private static VoxelPart gasMask() {
		Voxels v = g();
		int rubber = 0x2C3036;
		rbox(v, 0, 0.0f, 0.27f, 0.235f, 0.18f, 0.04f, tex(rubber));
		for (int s = -1; s <= 1; s += 2) {
			cylZ(v, s * 0.1f, 0.1f, 0.075f, 0.28f, 0.34f, tex(0x15171B));
			cylZ(v, s * 0.1f, 0.1f, 0.055f, 0.32f, 0.35f, flat(0x7FD68A));
			v.dot(s * 0.1f + 0.02f, 0.13f, 0.35f, 0xFFFFFF);
		}
		// Фильтр-банка
		cylZ(v, 0, -0.07f, 0.075f, 0.28f, 0.44f, tex(0x4B5560));
		cylZ(v, 0, -0.07f, 0.055f, 0.44f, 0.48f, tex(0x6F7B87));
		ring(v, 0, -0.07f, 0.33f, 0.1f, 0.0f, 0.0f, tex(0x4B5560));
		paint(v, -0.08f, -0.075f, 0.36f, 0.08f, -0.06f, 0.42f, 0xC9A227);
		// Ремни
		ring(v, 0, 0.14f, 0.0f, 0.275f, 0.255f, 0.05f, tex(rubber));
		ring(v, 0, 0.02f, 0.0f, 0.275f, 0.255f, 0.04f, tex(rubber));
		return part(v);
	}

	private static VoxelPart ninja() {
		Voxels v = g();
		int cloth = 0x1B1B22;
		// Платок на нижней части лица
		rbox(v, 0, -0.06f, 0.0f, 0.275f, 0.1f, 0.275f, tex(cloth));
		v.carve(-0.2f, -0.2f, -0.2f, 0.2f, 0.2f, 0.2f, (x, y, z) -> y < 0.3f && Math.abs(x) < 0.25f && Math.abs(z) < 0.25f);
		// Повязка на лоб с металлической пластиной
		ring(v, 0, 0.22f, 0.0f, 0.28f, 0.25f, 0.07f, tex(cloth));
		rbox(v, 0, 0.22f, 0.275f, 0.1f, 0.05f, 0.02f, tex(0xB7BEC9));
		paint(v, -0.015f, 0.17f, 0.28f, 0.015f, 0.27f, 0.32f, 0x22252B);
		paint(v, -0.07f, 0.215f, 0.28f, 0.07f, 0.23f, 0.32f, 0x22252B);
		// Концы повязки сзади
		v.tube(0.05f, 0.22f, -0.27f, 0.025f, 0.16f, 0.1f, -0.42f, 0.015f, tex(cloth));
		v.tube(0.0f, 0.22f, -0.27f, 0.025f, -0.1f, 0.15f, -0.44f, 0.015f, tex(cloth));
		return part(v);
	}

	private static VoxelPart sunglasses() {
		Voxels v = g();
		int black = 0x15151A;
		for (int s = -1; s <= 1; s += 2) {
			v.box(s * 0.105f - 0.08f, 0.05f, 0.255f, s * 0.105f + 0.08f, 0.16f, 0.3f, tex(black));
			v.box(s * 0.105f - 0.065f, 0.065f, 0.3f, s * 0.105f + 0.065f, 0.145f, 0.315f, tex(0x1E3A5F));
			paint(v, s * 0.105f - 0.05f, 0.12f, 0.3f, s * 0.105f - 0.02f, 0.14f, 0.32f, 0x6AA6E8);
			// Дужки назад
			v.box(s * 0.255f - 0.012f, 0.1f, -0.1f, s * 0.255f + 0.012f, 0.14f, 0.28f, tex(black));
		}
		v.box(-0.03f, 0.11f, 0.26f, 0.03f, 0.14f, 0.3f, tex(black));
		return part(v);
	}

	private static VoxelPart visor() {
		Voxels v = g();
		rbox(v, 0, 0.1f, 0.27f, 0.27f, 0.075f, 0.045f, tex(0x2B2F38));
		rbox(v, 0, 0.1f, 0.3f, 0.23f, 0.045f, 0.02f, flat(0x1A0A0A));
		// Светящаяся красная полоса с «бегущим» огоньком
		v.box(-0.2f, 0.085f, 0.315f, 0.2f, 0.115f, 0.33f, flat(0xFF2A2A));
		v.box(-0.05f, 0.085f, 0.32f, 0.05f, 0.115f, 0.335f, flat(0xFFB0A0));
		// Боковые блоки и антенна
		for (int s = -1; s <= 1; s += 2) {
			v.box(s * 0.27f - 0.025f, 0.03f, -0.02f, s * 0.27f + 0.025f, 0.17f, 0.25f, tex(0x3A404B));
			paint(v, s * 0.27f - 0.03f, 0.08f, 0.1f, s * 0.27f + 0.03f, 0.12f, 0.2f, 0x39E0FF);
		}
		v.tube(0.27f, 0.17f, 0.0f, 0.012f, 0.3f, 0.36f, 0.0f, 0.008f, tex(0x8A93A0));
		v.dot(0.3f, 0.375f, 0.0f, 0xFF3030);
		return part(v);
	}

	private static VoxelPart carnival() {
		Voxels v = g();
		int gold = 0xE0B13C;
		for (int s = -1; s <= 1; s += 2) {
			rbox(v, s * 0.11f, 0.1f, 0.265f, 0.13f, 0.085f, 0.03f, tex(gold));
			paint(v, s * 0.11f - 0.065f, 0.09f, 0.25f, s * 0.11f + 0.065f, 0.13f, 0.45f, 0x120A08);
		}
		v.box(-0.03f, 0.06f, 0.255f, 0.03f, 0.14f, 0.3f, tex(gold));
		// Узор
		paint(v, -0.02f, 0.14f, 0.28f, 0.02f, 0.18f, 0.32f, 0xC8246B);
		paint(v, -0.24f, 0.17f, 0.25f, -0.17f, 0.185f, 0.32f, 0x7A2BC2);
		paint(v, 0.17f, 0.17f, 0.25f, 0.24f, 0.185f, 0.32f, 0x7A2BC2);
		// Перья сбоку
		v.tube(0.22f, 0.17f, 0.25f, 0.02f, 0.38f, 0.38f, 0.1f, 0.008f, flat(0xC8246B));
		v.tube(0.22f, 0.17f, 0.25f, 0.02f, 0.34f, 0.32f, 0.12f, 0.008f, flat(0x2BB6C8));
		v.tube(0.22f, 0.17f, 0.25f, 0.02f, 0.3f, 0.42f, 0.14f, 0.008f, flat(0xF2C230));
		// Лента вокруг головы
		ring(v, 0, 0.1f, 0.0f, 0.27f, 0.255f, 0.03f, tex(0x7A2BC2));
		return part(v);
	}
}
