package com.flyvisual.util;

/**
 * Шляпы для головы. Каждая собрана из простых форм и «запечена» в гладкую сетку (как питомцы).
 * Начало координат — центр верха головы, ось Y вверх, перёд шляпы смотрит в +Z. Размер голову игрока 0,5 блока.
 */
public final class HatModels {
	public static final String[] NAMES = {"Цилиндр", "Корона", "Ковбойская шляпа", "Колпак волшебника", "Рожки", "Шапка Санты", "Пиратская треуголка", "Кепка", "Нимб",
			"Кошачьи ушки", "Шлем викинга", "Гриб"};
	private static final VoxelPart[] CACHE = new VoxelPart[NAMES.length];
	private static final float V = 0.022f;

	private HatModels() {
	}

	public static VoxelPart get(int index) {
		int i = Math.max(0, Math.min(NAMES.length - 1, index));
		if (CACHE[i] == null) {
			CACHE[i] = build(i);
		}
		return CACHE[i];
	}

	/** Вертикальный цилиндр с центром (cx, cz), радиусом r, от y0 до y1. */
	private static Sdf cylinder(float cx, float cz, float r, float y0, float y1) {
		return (x, y, z) -> {
			float d = (float) Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz)) - r;
			return Math.max(d, Math.max(y0 - y, y - y1));
		};
	}

	private static int mix(int a, int b, float t) {
		return Voxels.mix(a, b, t);
	}

	private static VoxelPart build(int i) {
		switch (i) {
			case 1:
				return crown();
			case 2:
				return cowboy();
			case 3:
				return wizard();
			case 4:
				return horns();
			case 5:
				return santa();
			case 6:
				return pirate();
			case 7:
				return cap();
			case 8:
				return halo();
			case 9:
				return catEars();
			case 10:
				return viking();
			case 11:
				return mushroom();
			default:
				return topHat();
		}
	}

	private static VoxelPart topHat() {
		Sdf brim = cylinder(0, 0, 0.34f, -0.01f, 0.03f);
		Sdf crown = cylinder(0, 0, 0.2f, 0.0f, 0.34f);
		Sdf hat = Sdf.union(brim, crown);
		Sdf.Paint paint = (x, y, z) -> {
			if (y > 0.04f && y < 0.12f) return 0xB5222A; // красная лента
			if (y > 0.335f) return 0x2B2B33;
			return mix(0x1B1B22, 0x2E2E38, Voxels.noise(x * 20, y * 20, z * 20) * 0.6f);
		};
		return VoxelPart.build(V, -0.4f, -0.04f, -0.4f, 0.4f, 0.4f, 0.4f, hat, paint, 0, 0, 0);
	}

	private static VoxelPart crown() {
		Sdf ring = Sdf.subtract(cylinder(0, 0, 0.27f, 0.0f, 0.13f), cylinder(0, 0, 0.215f, -0.1f, 0.3f));
		Sdf[] spikes = new Sdf[5];
		for (int k = 0; k < 5; k++) {
			float a = k * (float) (Math.PI * 2 / 5);
			float sx = (float) Math.sin(a) * 0.245f;
			float sz = (float) Math.cos(a) * 0.245f;
			spikes[k] = Sdf.capsule(sx, 0.08f, sz, sx, 0.27f, sz, 0.05f, 0.012f);
		}
		Sdf hat = Sdf.smooth(0.04f, ring, Sdf.union(spikes));
		Sdf.Paint paint = (x, y, z) -> {
			float r = (float) Math.sqrt(x * x + z * z);
			// Камни на кончиках зубцов и на ободе
			for (int k = 0; k < 5; k++) {
				float a = k * (float) (Math.PI * 2 / 5);
				float sx = (float) Math.sin(a) * 0.245f;
				float sz = (float) Math.cos(a) * 0.245f;
				float dd = (x - sx) * (x - sx) + (z - sz) * (z - sz) + (y - 0.26f) * (y - 0.26f);
				if (dd < 0.0007f) return 0xD6243A;
				float a2 = a + (float) (Math.PI / 5);
				float gx = (float) Math.sin(a2) * 0.27f;
				float gz = (float) Math.cos(a2) * 0.27f;
				float d2 = (x - gx) * (x - gx) + (z - gz) * (z - gz) + (y - 0.065f) * (y - 0.065f);
				if (d2 < 0.0005f && r > 0.24f) return 0x2FB6E8;
			}
			return mix(0xE8B923, 0xFFE06A, Voxels.noise(x * 25, y * 25, z * 25));
		};
		return VoxelPart.build(V, -0.35f, -0.04f, -0.35f, 0.35f, 0.34f, 0.35f, hat, paint, 0, 0, 0);
	}

	private static VoxelPart cowboy() {
		// Поля загнуты вверх по бокам (по оси X), спереди и сзади чуть опущены
		Sdf brim = (x, y, z) -> {
			float r = (float) Math.sqrt(x * x * 0.9f + z * z * 1.15f);
			float lift = Math.abs(x) * Math.abs(x) * 1.7f;
			float yc = 0.0f + lift - Math.abs(z) * 0.12f;
			float d = Math.max(r - 0.42f, Math.abs(y - yc) - 0.016f);
			return d;
		};
		Sdf crown = Sdf.smooth(0.05f, cylinder(0, 0, 0.19f, 0.0f, 0.2f),
				Sdf.ellipsoid(0, 0.2f, 0, 0.17f, 0.04f, 0.17f));
		// Вмятина сверху вдоль оси Z
		Sdf dent = Sdf.box(0, 0.235f, 0, 0.035f, 0.04f, 0.3f);
		Sdf hat = Sdf.subtract(Sdf.smooth(0.03f, brim, crown), dent);
		Sdf.Paint paint = (x, y, z) -> {
			float r = (float) Math.sqrt(x * x + z * z);
			if (r < 0.2f && y > 0.03f && y < 0.075f) return 0x3A2414; // лента
			return mix(0x8A5A2B, 0xA9743C, Voxels.noise(x * 18, y * 18, z * 18));
		};
		return VoxelPart.build(V, -0.45f, -0.06f, -0.45f, 0.45f, 0.3f, 0.45f, hat, paint, 0, 0, 0);
	}

	private static VoxelPart wizard() {
		Sdf brim = cylinder(0, 0, 0.4f, -0.01f, 0.03f);
		Sdf base = cylinder(0, 0, 0.22f, 0.0f, 0.12f);
		// Колпак: две капсулы, острие слегка загнуто назад
		Sdf cone1 = Sdf.capsule(0, 0.1f, 0, 0, 0.36f, -0.03f, 0.2f, 0.11f);
		Sdf cone2 = Sdf.capsule(0, 0.36f, -0.03f, 0, 0.56f, -0.14f, 0.11f, 0.02f);
		Sdf hat = Sdf.smooth(0.05f, brim, Sdf.smooth(0.06f, base, cone1, cone2));
		Sdf.Paint paint = (x, y, z) -> {
			if (y > 0.035f && y < 0.1f && Math.sqrt(x * x + z * z) < 0.3f) return 0xE6B422; // лента
			int purple = mix(0x3A1E7A, 0x5B34B0, Voxels.noise(x * 16, y * 16, z * 16));
			// Звёздочки: редкие жёлтые пятна
			if (y > 0.1f && Voxels.grain(x, y, z, 0.07f) > 0.93f) return 0xFFE066;
			return purple;
		};
		return VoxelPart.build(V, -0.45f, -0.04f, -0.5f, 0.45f, 0.62f, 0.4f, hat, paint, 0, 0, 0);
	}

	private static VoxelPart horns() {
		// Правый рог (x >= 0), левый получается зеркалом
		Sdf lower = Sdf.capsule(0.15f, 0.02f, 0.02f, 0.27f, 0.12f, 0.0f, 0.065f, 0.05f);
		Sdf upper = Sdf.capsule(0.27f, 0.12f, 0.0f, 0.3f, 0.3f, -0.03f, 0.05f, 0.012f);
		Sdf horn = Sdf.mirrorX(Sdf.smooth(0.03f, lower, upper));
		Sdf.Paint paint = (x, y, z) -> {
			float t = Math.max(0.0f, Math.min(1.0f, y / 0.3f));
			return mix(0x2A1A1A, 0xD9C9A8, t * t);
		};
		return VoxelPart.build(V, -0.4f, -0.06f, -0.15f, 0.4f, 0.36f, 0.15f, horn, paint, 0, 0, 0);
	}

	// ---------- Новые шапки (воксельные, как питомцы) ----------

	private static Voxels gr() {
		return Art.grid(-0.55f, -0.12f, -0.55f, 0.55f, 0.7f, 0.6f);
	}

	private static VoxelPart santa() {
		Voxels v = gr();
		Art.spire(v, 0, 0.0f, 0.0f, 0.27f, 0.46f, 0.0f, -0.3f, Art.tex(0xD22B2B));
		Art.ring(v, 0, 0.03f, 0.0f, 0.3f, 0.23f, 0.1f, Art.tex(0xF5F5F5));
		Art.rbox(v, 0, 0.47f, -0.3f, 0.07f, 0.07f, 0.07f, Art.tex(0xFFFFFF));
		return Art.part(v);
	}

	private static VoxelPart pirate() {
		Voxels v = gr();
		int black = 0x1B1B20;
		Art.rbox(v, 0, 0.09f, 0.0f, 0.27f, 0.11f, 0.27f, Art.tex(black));
		// Загнутые поля: спереди, слева и справа
		Art.rbox(v, 0, 0.05f, 0.3f, 0.16f, 0.1f, 0.035f, Art.tex(black));
		Art.rbox(v, -0.34f, 0.08f, 0.0f, 0.04f, 0.1f, 0.28f, Art.tex(black));
		Art.rbox(v, 0.34f, 0.08f, 0.0f, 0.04f, 0.1f, 0.28f, Art.tex(black));
		Art.rbox(v, 0, 0.04f, -0.3f, 0.2f, 0.07f, 0.04f, Art.tex(black));
		// Золотая окантовка и череп
		Art.paint(v, -0.3f, 0.145f, 0.28f, 0.3f, 0.165f, 0.34f, 0xD6A93A);
		Art.paint(v, -0.04f, 0.04f, 0.3f, 0.04f, 0.12f, 0.34f, 0xF2F2F2);
		Art.paint(v, -0.03f, 0.075f, 0.32f, -0.01f, 0.095f, 0.36f, 0x1B1B20);
		Art.paint(v, 0.01f, 0.075f, 0.32f, 0.03f, 0.095f, 0.36f, 0x1B1B20);
		return Art.part(v);
	}

	private static VoxelPart cap() {
		Voxels v = gr();
		int blue = 0x2A5FB0;
		Art.rbox(v, 0, 0.0f, 0.0f, 0.275f, 0.2f, 0.275f, Art.tex(blue));
		v.carve(-0.4f, -0.2f, -0.4f, 0.4f, -0.03f, 0.4f, (x, y, z) -> true);
		Art.rbox(v, 0, -0.005f, 0.34f, 0.21f, 0.03f, 0.15f, Art.tex(0x1C447F));
		Art.rbox(v, 0, 0.2f, 0.0f, 0.04f, 0.03f, 0.04f, Art.tex(0xE9ECF2));
		Art.paint(v, -0.07f, 0.06f, 0.24f, 0.07f, 0.16f, 0.3f, 0xF2C230);
		Art.paint(v, -0.04f, 0.09f, 0.24f, 0.04f, 0.13f, 0.3f, 0x2A5FB0);
		return Art.part(v);
	}

	private static VoxelPart halo() {
		Voxels v = gr();
		Art.ring(v, 0, 0.25f, 0.0f, 0.22f, 0.15f, 0.05f, Art.flat(0xFFE066));
		Art.paint(v, -0.22f, 0.22f, 0.1f, 0.22f, 0.28f, 0.25f, 0xFFF2A8);
		Art.paint(v, -0.05f, 0.22f, -0.25f, 0.05f, 0.28f, -0.1f, 0xFFD23A);
		return Art.part(v);
	}

	private static VoxelPart catEars() {
		Voxels v = gr();
		int black = 0x1D1D22;
		v.tube(-0.265f, 0.0f, 0.0f, 0.022f, 0.0f, 0.1f, 0.0f, 0.022f, Art.tex(black));
		v.tube(0.265f, 0.0f, 0.0f, 0.022f, 0.0f, 0.1f, 0.0f, 0.022f, Art.tex(black));
		for (int s = -1; s <= 1; s += 2) {
			Art.cone(v, s * 0.15f, 0.04f, 0.0f, 0.09f, 0.26f, 0.035f, Art.tex(black));
			Art.cone(v, s * 0.15f, 0.05f, 0.035f, 0.055f, 0.19f, 0.012f, Art.flat(0xF29AB7));
		}
		return Art.part(v);
	}

	private static VoxelPart viking() {
		Voxels v = gr();
		int steel = 0x9AA3AE;
		Art.rbox(v, 0, 0.02f, 0.0f, 0.275f, 0.22f, 0.275f, Art.tex(steel));
		v.carve(-0.4f, -0.2f, -0.4f, 0.4f, -0.03f, 0.4f, (x, y, z) -> true);
		Art.ring(v, 0, 0.0f, 0.0f, 0.29f, 0.24f, 0.07f, Art.tex(0xB88A2E));
		Art.rbox(v, 0, -0.05f, 0.285f, 0.03f, 0.13f, 0.02f, Art.tex(steel));
		for (int s = -1; s <= 1; s += 2) {
			v.tube(s * 0.27f, 0.04f, 0.0f, 0.045f, s * 0.38f, 0.14f, 0.0f, 0.04f, Art.tex(0xE8DFC4));
			v.tube(s * 0.38f, 0.14f, 0.0f, 0.04f, s * 0.36f, 0.34f, 0.02f, 0.012f, Art.tex(0xF3EDDA));
		}
		return Art.part(v);
	}

	private static VoxelPart mushroom() {
		Voxels v = gr();
		Art.rbox(v, 0, 0.1f, 0.0f, 0.36f, 0.18f, 0.36f, Art.tex(0xD3322E));
		v.carve(-0.5f, -0.2f, -0.5f, 0.5f, -0.03f, 0.5f, (x, y, z) -> true);
		float[][] spots = {{0, 0.27f, 0, 0.07f}, {0.2f, 0.2f, 0.1f, 0.055f}, {-0.2f, 0.2f, 0.12f, 0.055f}, {0.1f, 0.2f, -0.22f, 0.05f},
				{-0.14f, 0.19f, -0.2f, 0.045f}, {0.0f, 0.15f, 0.3f, 0.05f}, {0.3f, 0.1f, -0.05f, 0.04f}, {-0.3f, 0.1f, -0.03f, 0.04f}};
		for (float[] sp : spots) {
			v.paintEllipsoid(sp[0], sp[1], sp[2], sp[3], sp[3], sp[3], Art.flat(0xFFF6E8));
		}
		return Art.part(v);
	}
}
