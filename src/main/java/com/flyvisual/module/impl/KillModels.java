package com.flyvisual.module.impl;

/**
 * Сцены эффектов убийства: гроб, молния, рука из земли, тёмный жнец, щупальца, ангел, паук.
 * Все сцены рисуются в системе координат: начало — земля под жертвой, ось +Z смотрит на игрока,
 * +X — вправо от игрока. Время t — секунды от начала эффекта. Класс не зависит от Minecraft.
 */
final class KillModels {
	private KillModels() {
	}

	static final float[] LIFE = {3.7f, 2.6f, 3.9f, 5.1f, 4.4f, 5.6f, 5.3f};

	static final float[][] WOOD = {{0.34f, 0.19f, 0.10f}, {0.82f, 0.63f, 0.16f}, {0.10f, 0.10f, 0.12f},
			{0.40f, 0.05f, 0.07f}, {0.58f, 0.80f, 0.93f}, {0.07f, 0.36f, 0.23f}, {0.22f, 0.08f, 0.32f},
			{0.88f, 0.85f, 0.78f}};
	static final float[][] TRIM = {{0.80f, 0.62f, 0.22f}, {1.0f, 0.92f, 0.55f}, {0.70f, 0.72f, 0.82f},
			{0.20f, 0.03f, 0.04f}, {0.92f, 0.97f, 1.0f}, {0.88f, 0.78f, 0.32f}, {0.95f, 0.76f, 0.27f},
			{0.55f, 0.52f, 0.48f}};

	// ---------- Математика ----------

	static float cl(float v) {
		return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
	}

	static float sm(float e0, float e1, float x) {
		float u = cl((x - e0) / (e1 - e0));
		return u * u * (3.0f - 2.0f * u);
	}

	static float eo(float u) {
		u = cl(u);
		float k = 1.0f - u;
		return 1.0f - k * k * k;
	}

	static float backOut(float u) {
		u = cl(u);
		float x = u - 1.0f;
		return 1.0f + 2.4f * x * x * x + 1.4f * x * x;
	}

	static float lerp(float a, float b, float u) {
		return a + (b - a) * u;
	}

	static float rnd(int i, int salt) {
		double v = Math.sin(i * 12.9898 + salt * 78.233) * 43758.5453;
		return (float) (v - Math.floor(v));
	}

	static float sin(float v) {
		return (float) Math.sin(v);
	}

	static float cos(float v) {
		return (float) Math.cos(v);
	}

	static float hs(float hgt) {
		return Math.max(0.55f, Math.min(2.4f, hgt / 1.8f));
	}

	// ---------- Призрак жертвы ----------

	/** Поза призрака: вращения конечностей в радианах (отрицательный X — вперёд). */
	static final class Pose {
		float legL;
		float legR;
		float armLx;
		float armRx;
		float armLz;
		float armRz;
		float pitch;
		float head;
		float mouth;
		float lift;
		float yaw;
		/** Дрожь корпуса (для настоящей модели, у которой нельзя двигать руки). */
		float roll;

		/** Мечется и кричит. */
		Pose flail(float t, float amp) {
			legL = sin(t * 13.0f) * 0.55f * amp;
			legR = -sin(t * 13.0f + 1.0f) * 0.55f * amp;
			armLx = -1.5f * amp + sin(t * 11.0f) * 0.45f * amp;
			armRx = -1.5f * amp + sin(t * 11.0f + 2.0f) * 0.45f * amp;
			armLz = 0.35f * amp + sin(t * 9.0f) * 0.2f * amp;
			armRz = 0.35f * amp + sin(t * 9.0f + 1.3f) * 0.2f * amp;
			head = sin(t * 10.0f) * 0.2f * amp;
			mouth = amp;
			roll = sin(t * 12.0f) * 0.10f * amp;
			pitch += sin(t * 9.0f + 1.0f) * 0.06f * amp;
			return this;
		}

		/** Стоит и дрожит. */
		Pose tremble(float t, float amp) {
			armLx = -0.25f + sin(t * 23.0f) * 0.06f * amp;
			armRx = -0.25f + sin(t * 23.0f + 1.7f) * 0.06f * amp;
			armLz = 0.12f;
			armRz = 0.12f;
			head = sin(t * 17.0f) * 0.05f * amp;
			roll = sin(t * 23.0f) * 0.02f * amp;
			return this;
		}
	}

	static void ghost(KillGfx g, float hgt, float wid, Pose p, float alpha) {
		if (alpha <= 0.01f) return;
		if (g.realVictim) {
			// настоящая модель убитого: позу тела задаём здесь, саму модель рисует модуль
			if (alpha < 0.03f) return;
			float k = alpha * alpha * (3.0f - 2.0f * alpha);
			g.push();
			g.translate(0.0f, p.lift, 0.0f);
			g.rotY(p.yaw);
			float piv = Math.max(0.3f, hgt) * 0.42f;
			g.translate(0.0f, piv, 0.0f);
			g.rotX(p.pitch);
			g.rotZ(p.roll);
			g.translate(0.0f, -piv, 0.0f);
			float sc = g.victimScale * (0.2f + 0.8f * k);
			g.scale(sc, sc, sc);
			g.victim(alpha, false);
			g.pop();
			return;
		}
		g.push();
		g.translate(0.0f, p.lift, 0.0f);
		g.rotY(p.yaw);
		g.scale(Math.max(0.3f, wid) / 0.6f, Math.max(0.3f, hgt) / 1.8f, Math.max(0.3f, wid) / 0.6f);
		g.translate(0.0f, 0.75f, 0.0f);
		g.rotX(p.pitch);
		g.translate(0.0f, -0.75f, 0.0f);
		g.fresnel = true;
		g.mat(0.68f, 0.84f, 0.97f, alpha * 0.58f);
		for (int s = -1; s <= 1; s += 2) {
			g.push();
			g.translate(s * 0.125f, 0.75f, 0.0f);
			g.rotX(s < 0 ? p.legL : p.legR);
			g.box(0.0f, -0.375f, 0.0f, 0.125f, 0.375f, 0.125f);
			g.pop();
			g.push();
			g.translate(s * 0.375f, 1.4f, 0.0f);
			g.rotZ(s * (s < 0 ? p.armLz : p.armRz));
			g.rotX(s < 0 ? p.armLx : p.armRx);
			g.box(0.0f, -0.325f, 0.0f, 0.125f, 0.375f, 0.125f);
			g.pop();
		}
		g.box(0.0f, 1.125f, 0.0f, 0.25f, 0.375f, 0.125f);
		g.push();
		g.translate(0.0f, 1.5f, 0.0f);
		g.rotX(p.head);
		g.box(0.0f, 0.25f, 0.0f, 0.25f, 0.25f, 0.25f);
		g.fresnel = false;
		g.mat(0.02f, 0.04f, 0.07f, Math.min(1.0f, alpha * 0.95f));
		g.box(-0.1f, 0.3f, 0.252f, 0.055f, 0.05f, 0.008f);
		g.box(0.1f, 0.3f, 0.252f, 0.055f, 0.05f, 0.008f);
		g.box(0.0f, 0.12f, 0.252f, 0.07f, 0.02f + 0.06f * p.mouth, 0.008f);
		g.pop();
		g.fresnel = false;
		g.pop();
	}

	// ---------- Земля: трещины, комья, осколки ----------

	static void cracks(KillGfx g, float grow, float alpha, int seed, float radius) {
		if (alpha <= 0.01f || grow <= 0.0f) return;
		g.mat(0.03f, 0.022f, 0.02f, alpha * 0.95f);
		for (int k = 0; k < 8; k++) {
			float a = k * 0.785f + rnd(k, seed) * 0.5f;
			float px = 0.0f;
			float pz = 0.0f;
			float seg = radius * 0.28f * grow;
			for (int s = 0; s < 4; s++) {
				float na = a + (rnd(k * 7 + s, seed + 3) - 0.5f) * 0.9f;
				float nx = px + cos(na) * seg * (1.0f - s * 0.12f);
				float nz = pz + sin(na) * seg * (1.0f - s * 0.12f);
				float w0 = 0.05f * (1.0f - s * 0.2f);
				float w1 = 0.05f * (1.0f - (s + 1) * 0.2f);
				float dx = nx - px;
				float dz = nz - pz;
				float dl = (float) Math.sqrt(dx * dx + dz * dz);
				if (dl > 1.0e-4f) {
					float ox = -dz / dl;
					float oz = dx / dl;
					g.flat(px + ox * w0, 0.024f, pz + oz * w0, px - ox * w0, 0.024f, pz - oz * w0,
							nx - ox * w1, 0.024f, nz - oz * w1, nx + ox * w1, 0.024f, nz + oz * w1);
				}
				px = nx;
				pz = nz;
				a = na;
			}
		}
	}

	static void earth(KillGfx g, float radius, float alpha, int seed) {
		if (alpha <= 0.01f || radius <= 0.01f) return;
		g.mat(0.15f, 0.10f, 0.07f, alpha * 0.9f);
		g.disc(0.0f, 0.02f, 0.0f, radius, 18);
		g.mat(0.10f, 0.07f, 0.05f, alpha);
		g.ring(0.0f, 0.021f, 0.0f, radius * 0.72f, radius * 0.86f, 18);
	}

	/** Комья земли, вылетающие из места разлома. */
	static void debris(KillGfx g, float t, float t0, int n, int seed, float spread, float up, float size, float alpha) {
		float age = t - t0;
		if (age < 0.0f || age > 1.7f) return;
		for (int k = 0; k < n; k++) {
			float a = rnd(k, seed) * 6.2832f;
			float sp = (0.35f + rnd(k, seed + 1)) * spread;
			float vy = (0.7f + rnd(k, seed + 2)) * up;
			float x = cos(a) * sp * (1.0f - (float) Math.exp(-2.5f * age)) * 0.8f;
			float z = sin(a) * sp * (1.0f - (float) Math.exp(-2.5f * age)) * 0.8f;
			float y = vy * age - 4.9f * age * age;
			boolean landed = y < 0.0f;
			if (landed) y = 0.0f;
			float sz = size * (0.5f + rnd(k, seed + 3)) * (1.0f - sm(1.2f, 1.7f, age)) * alpha;
			if (sz < 0.004f) continue;
			g.push();
			g.translate(x, y + sz, z);
			float spin = landed ? 0.0f : age * (3.0f + rnd(k, seed + 4) * 6.0f);
			g.rotY(spin + k);
			g.rotX(spin * 0.7f);
			float tone = 0.7f + 0.5f * rnd(k, seed + 5);
			if (k % 4 == 3) g.mat(0.18f * tone, 0.30f * tone, 0.10f * tone, 1.0f);
			else g.mat(0.30f * tone, 0.20f * tone, 0.13f * tone, 1.0f);
			g.box(0.0f, 0.0f, 0.0f, sz, sz * 0.8f, sz);
			g.pop();
		}
	}

	/** Клуб тёмного дыма. */
	static void smoke(KillGfx g, float cx, float cy, float cz, float r, float alpha, float tone) {
		if (alpha <= 0.01f) return;
		g.mat(0.05f * tone, 0.04f * tone, 0.07f * tone, alpha);
		g.ell(cx, cy, cz, r, r * 0.8f, r, 4, 6);
	}

	// ---------- Рука ----------

	/**
	 * Гнилая рука: локальное начало — запястье, пальцы вверх (+Y), ладонь смотрит в +Z.
	 * curl 0..1 — сжатие пальцев, spread — разведение.
	 */
	static void drawHand(KillGfx g, float curl, float spread) {
		g.mat(0.25f, 0.17f, 0.12f, 1.0f);
		g.tube(0.0f, -2.5f, 0.0f, 0.0f, -1.5f, 0.0f, 0.25f, 0.23f, 9);
		g.mat(0.34f, 0.27f, 0.24f, 1.0f);
		g.tube(0.0f, -1.5f, 0.0f, 0.0f, -0.75f, 0.0f, 0.23f, 0.18f, 9);
		g.mat(0.47f, 0.41f, 0.42f, 1.0f);
		g.tube(0.0f, -0.75f, 0.0f, 0.0f, -0.04f, 0.0f, 0.18f, 0.12f, 9);
		// вены
		g.mat(0.30f, 0.22f, 0.32f, 1.0f);
		g.tube(0.05f, -1.2f, 0.17f, 0.035f, -0.1f, 0.115f, 0.02f, 0.014f, 4);
		g.tube(-0.06f, -1.0f, 0.15f, -0.03f, -0.15f, 0.12f, 0.016f, 0.012f, 4);
		// манжета-обрывок
		g.mat(0.20f, 0.17f, 0.17f, 1.0f);
		g.tube(0.0f, -0.10f, 0.0f, 0.0f, 0.02f, 0.0f, 0.145f, 0.14f, 9);
		// ладонь
		g.mat(0.50f, 0.44f, 0.45f, 1.0f);
		g.box(0.0f, 0.18f, 0.0f, 0.175f, 0.17f, 0.07f);
		g.mat(0.36f, 0.29f, 0.31f, 1.0f);
		for (int k = 0; k < 4; k++) {
			float x = -0.125f + k * 0.083f;
			g.tube(x, 0.05f, -0.075f, x, 0.33f, -0.075f, 0.012f, 0.012f, 4);
		}
		float[] xs = {-0.125f, -0.042f, 0.042f, 0.125f};
		float[] lf = {0.95f, 1.1f, 1.0f, 0.8f};
		float[] r0 = {0.046f, 0.038f, 0.031f};
		for (int k = 0; k < 4; k++) {
			g.push();
			g.translate(xs[k], 0.34f, 0.0f);
			g.rotZ(-(k - 1.5f) * spread * 0.11f);
			float c = cl(curl * (1.0f + (k == 1 ? 0.04f : 0.0f) - (k == 3 ? 0.06f : 0.0f)));
			float[] seg = {0.17f * lf[k], 0.125f * lf[k], 0.10f * lf[k]};
			float[] ang = {c * 1.25f, c * 1.5f, c * 1.05f};
			g.mat(0.44f, 0.38f, 0.39f, 1.0f);
			g.ell(0.0f, 0.0f, 0.0f, 0.052f, 0.045f, 0.052f, 4, 6);
			for (int j = 0; j < 3; j++) {
				g.rotX(ang[j]);
				g.mat(0.49f, 0.43f, 0.44f, 1.0f);
				g.tube(0.0f, 0.0f, 0.0f, 0.0f, seg[j], 0.0f, r0[j] * 1.04f, r0[j] * 0.84f, 7);
				g.mat(0.40f, 0.34f, 0.35f, 1.0f);
				g.ell(0.0f, seg[j], 0.0f, r0[j] * 0.95f, r0[j] * 0.8f, r0[j] * 0.95f, 3, 6);
				g.translate(0.0f, seg[j], 0.0f);
			}
			g.mat(0.09f, 0.07f, 0.08f, 1.0f);
			g.tube(0.0f, -0.01f, 0.012f, 0.0f, 0.095f, 0.03f, 0.03f, 0.0f, 5);
			g.pop();
		}
		// большой палец
		g.push();
		g.translate(0.17f, 0.07f, 0.02f);
		g.rotZ(-0.85f - spread * 0.3f);
		g.rotX(curl * 0.7f);
		g.mat(0.49f, 0.43f, 0.44f, 1.0f);
		g.tube(0.0f, 0.0f, 0.0f, 0.0f, 0.17f, 0.0f, 0.056f, 0.046f, 7);
		g.translate(0.0f, 0.17f, 0.0f);
		g.rotX(curl * 0.9f);
		g.tube(0.0f, 0.0f, 0.0f, 0.0f, 0.13f, 0.0f, 0.046f, 0.034f, 7);
		g.mat(0.09f, 0.07f, 0.08f, 1.0f);
		g.tube(0.0f, 0.11f, 0.01f, 0.0f, 0.21f, 0.03f, 0.03f, 0.0f, 5);
		g.pop();
	}

	static void sceneHand(KillGfx g, float t, float hgt, float wid, int seed) {
		float hs = hs(hgt);
		float sink = 3.0f * hs * sm(1.5f, 3.1f, t);
		float rise = eo(t / 0.9f);
		float wy = -2.9f * hs * (1.0f - rise) + 0.10f * hs - sink;
		float grab = sm(0.9f, 1.4f, t);
		float curl = lerp(0.08f + 0.04f * sin(t * 9.0f), 0.72f, grab);
		if (t > 1.4f && t < 2.8f) curl += 0.04f * sin(t * 15.0f);
		float spread = 1.0f - grab;
		float ground = (1.0f - sm(3.0f, 3.8f, t));

		earth(g, 1.25f * hs * eo(t / 0.5f), sm(0.0f, 0.2f, t) * ground, seed);
		cracks(g, eo(t / 0.6f), sm(0.0f, 0.2f, t) * ground, seed, 2.6f * hs);

		Pose p = new Pose();
		if (t < 1.25f) p.tremble(t, 1.0f);
		else p.flail(t, sm(1.25f, 1.6f, t));
		p.lift = -sink;
		ghost(g, hgt, wid, p, g.in(sm(0.0f, 0.3f, t)) * (1.0f - sm(2.9f, 3.3f, t)));

		g.push();
		g.translate(0.0f, wy, -0.40f * hs);
		g.rotZ(sin(t * 7.0f) * 0.03f * (1.0f - rise));
		g.rotX(0.22f * sm(1.5f, 3.0f, t) - 0.12f * (1.0f - rise));
		g.scale(2.1f * hs);
		drawHand(g, curl, spread);
		g.pop();

		debris(g, t, 0.12f, 18, seed, 1.8f * hs, 3.4f, 0.09f * hs, ground);
		debris(g, t, 1.65f, 10, seed + 9, 1.5f * hs, 2.6f, 0.07f * hs, ground);
	}

	// ---------- Тёмный жнец ----------

	static void drawScythe(KillGfx g) {
		g.mat(0.17f, 0.11f, 0.08f, g.ca);
		g.tube(0.0f, -1.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.032f, 0.028f, 6);
		g.mat(0.30f, 0.30f, 0.34f, g.ca);
		g.tube(0.0f, 1.9f, 0.0f, 0.0f, 2.08f, 0.0f, 0.045f, 0.04f, 6);
		g.tube(0.0f, -1.0f, 0.0f, 0.0f, -0.88f, 0.0f, 0.04f, 0.04f, 6);
		float[] bz = {0.0f, 0.28f, 0.56f, 0.82f, 1.05f, 1.22f, 1.30f};
		float[] by = {2.06f, 2.14f, 2.16f, 2.10f, 1.97f, 1.80f, 1.60f};
		float a = g.ca;
		for (int i = 0; i < bz.length - 1; i++) {
			float u0 = i / (float) (bz.length - 1);
			float u1 = (i + 1) / (float) (bz.length - 1);
			float w0 = 0.04f + 0.17f * (float) Math.pow(1.0f - u0, 0.8f);
			float w1 = 0.04f + 0.17f * (float) Math.pow(1.0f - u1, 0.8f);
			float tz = bz[i + 1] - bz[i];
			float ty = by[i + 1] - by[i];
			float tl = (float) Math.sqrt(tz * tz + ty * ty);
			float nz = ty / tl;
			float ny = -tz / tl;
			for (int side = -1; side <= 1; side += 2) {
				float x = side * 0.012f;
				g.mat(0.40f, 0.43f, 0.50f, a);
				g.flat(x, by[i], bz[i], x, by[i + 1], bz[i + 1], x, by[i + 1] + ny * w1, bz[i + 1] + nz * w1, x, by[i] + ny * w0, bz[i] + nz * w0);
			}
			boolean oe = g.emissive;
			g.emissive = true;
			g.mat(0.80f, 0.88f, 1.0f, a * 0.9f);
			g.tube(0.0f, by[i] + ny * w0, bz[i] + nz * w0, 0.0f, by[i + 1] + ny * w1, bz[i + 1] + nz * w1, 0.007f, 0.006f, 4);
			g.emissive = oe;
			g.mat(0.14f, 0.15f, 0.18f, a);
			g.tube(0.0f, by[i], bz[i], 0.0f, by[i + 1], bz[i + 1], 0.02f, 0.016f, 5);
		}
	}

	/** Костяная рука жнеца: локальное начало — запястье, пальцы вдоль +Y. */
	static void boneHand(KillGfx g, float curl, float a, float scale) {
		g.push();
		g.scale(scale);
		g.mat(0.86f, 0.83f, 0.74f, a);
		g.box(0.0f, 0.06f, 0.0f, 0.06f, 0.06f, 0.025f);
		for (int k = 0; k < 4; k++) {
			g.push();
			g.translate(-0.045f + k * 0.03f, 0.12f, 0.0f);
			float[] sg = {0.07f, 0.055f, 0.045f};
			for (int j = 0; j < 3; j++) {
				g.rotX(curl * (1.15f + j * 0.2f));
				g.tube(0.0f, 0.0f, 0.0f, 0.0f, sg[j], 0.0f, 0.014f, 0.011f, 5);
				g.translate(0.0f, sg[j], 0.0f);
			}
			g.pop();
		}
		g.push();
		g.translate(0.06f, 0.03f, 0.0f);
		g.rotZ(-0.7f);
		g.rotX(curl * 0.9f);
		g.tube(0.0f, 0.0f, 0.0f, 0.0f, 0.09f, 0.0f, 0.016f, 0.012f, 5);
		g.pop();
		g.pop();
	}

	static void drawReaper(KillGfx g, float t, float a, float reach, float lean, float[] gripLocal, float scythePitch) {
		if (a <= 0.01f) return;
		float[] hem = {0.0f, 0.25f, 0.62f, 1.0f, 1.35f, 1.62f, 1.78f};
		float[] rad = {0.66f, 0.56f, 0.43f, 0.31f, 0.27f, 0.30f, 0.21f};
		float[] amp = {0.16f, 0.12f, 0.07f, 0.03f, 0.01f, 0.0f, 0.0f};
		g.mat(0.045f, 0.04f, 0.055f, a);
		g.lathe(hem, rad, amp, 20, t * 1.8f, 5.0f, 1.0f, 0.85f);
		g.mat(0.06f, 0.05f, 0.075f, a);
		g.ell(0.0f, 1.66f, -0.02f, 0.40f, 0.14f, 0.26f, 4, 8);
		g.mat(0.035f, 0.03f, 0.045f, a);
		g.tube(0.0f, 1.98f, -0.16f, 0.0f, 1.72f, -0.62f + sin(t * 2.0f) * 0.05f, 0.17f, 0.02f, 7);
		g.mat(0.05f, 0.045f, 0.065f, a);
		g.ell(0.0f, 2.02f, 0.0f, 0.27f, 0.32f, 0.30f, 5, 9);
		g.mat(0.005f, 0.004f, 0.008f, a);
		g.ell(0.0f, 1.99f, 0.20f, 0.18f, 0.22f, 0.16f, 4, 8);
		boolean oe = g.emissive;
		g.emissive = true;
		g.mat(0.78f, 0.92f, 1.0f, a);
		g.box(-0.075f, 2.0f, 0.355f, 0.03f, 0.016f, 0.01f);
		g.box(0.075f, 2.0f, 0.355f, 0.03f, 0.016f, 0.01f);
		g.emissive = oe;

		// дальняя рука: коса
		float[] sl = KillGfx.ik(-0.30f, 1.62f, 0.0f, -0.34f, 1.02f, 0.30f, 0.62f, 0.62f, -0.6f, -0.5f, -0.4f);
		g.mat(0.05f, 0.045f, 0.06f, a);
		g.tube(-0.30f, 1.62f, 0.0f, sl[0], sl[1], sl[2], 0.115f, 0.10f, 7);
		g.tube(sl[0], sl[1], sl[2], sl[3], sl[4], sl[5], 0.10f, 0.13f, 7);
		g.push();
		g.translate(sl[3], sl[4], sl[5]);
		g.push();
		g.rotX(scythePitch);
		g.rotZ(0.03f);
		g.translate(0.0f, -0.25f, 0.0f);
		float old = g.ca;
		g.ca = a;
		drawScythe(g);
		g.ca = old;
		g.pop();
		g.rotX(-0.15f);
		boneHand(g, 0.95f, a, 1.0f);
		g.pop();

		// ближняя рука: хватает за шею
		float[] rest = {0.34f, 1.02f, 0.30f};
		float tx = lerp(rest[0], gripLocal[0], reach);
		float ty = lerp(rest[1], gripLocal[1], reach);
		float tz = lerp(rest[2], gripLocal[2], reach);
		float[] sr = KillGfx.ik(0.30f, 1.62f, 0.0f, tx, ty, tz, 0.62f, 0.62f, 0.6f, -0.6f, -0.3f);
		g.mat(0.05f, 0.045f, 0.06f, a);
		g.tube(0.30f, 1.62f, 0.0f, sr[0], sr[1], sr[2], 0.115f, 0.10f, 7);
		g.tube(sr[0], sr[1], sr[2], sr[3], sr[4], sr[5], 0.10f, 0.13f, 7);
		g.push();
		g.translate(sr[3], sr[4], sr[5]);
		float dx = gripLocal[0] - sr[0];
		float dy = gripLocal[1] - sr[1];
		float dz = gripLocal[2] - sr[2];
		float yaw = (float) Math.atan2(dx, dz);
		g.rotY(yaw);
		g.rotX(1.45f * reach + 0.3f * (1.0f - reach));
		boneHand(g, 0.35f + 0.55f * reach, a, 1.55f);
		g.pop();
	}

	static void riftDraw(KillGfx g, float t, float radius, float alpha) {
		if (radius < 0.03f || alpha <= 0.01f) return;
		g.mat(0.005f, 0.0f, 0.012f, 0.93f * alpha);
		g.disc(0.0f, 0.03f, 0.0f, radius, 30);
		boolean oe = g.emissive;
		g.emissive = true;
		g.mat(0.30f, 0.12f, 0.42f, 0.9f * alpha);
		g.ring(0.0f, 0.034f, 0.0f, radius * 0.94f, radius * 1.04f, 30);
		g.mat(0.45f, 0.25f, 0.6f, 0.6f * alpha);
		for (int k = 0; k < 5; k++) {
			float a0 = k * 1.2566f + t * 1.9f;
			float pr = 0.1f * radius;
			float pa = a0;
			for (int s = 1; s <= 7; s++) {
				float u = s / 7.0f;
				float na = a0 + u * 1.6f;
				float nr = radius * (0.1f + 0.84f * u);
				float w = 0.035f * (1.0f - u * 0.6f);
				float x0 = cos(pa) * pr;
				float z0 = sin(pa) * pr;
				float x1 = cos(na) * nr;
				float z1 = sin(na) * nr;
				float dx = x1 - x0;
				float dz = z1 - z0;
				float dl = (float) Math.sqrt(dx * dx + dz * dz);
				if (dl > 1.0e-5f) {
					float ox = -dz / dl * w;
					float oz = dx / dl * w;
					g.flat(x0 + ox, 0.036f, z0 + oz, x0 - ox, 0.036f, z0 - oz, x1 - ox, 0.036f, z1 - oz, x1 + ox, 0.036f, z1 + oz);
				}
				pr = nr;
				pa = na;
			}
		}
		g.emissive = oe;
	}

	static void sceneReaper(KillGfx g, float t, float hgt, float wid, int seed) {
		float hs = hs(hgt);
		float D = 3.3f - 2.3f * sm(1.0f, 2.05f, t);
		float ra = sm(0.0f, 1.0f, t) * (1.0f - sm(4.3f, 4.95f, t));
		float hover = 0.14f + 0.05f * sin(t * 2.2f) - 0.5f * (1.0f - sm(0.0f, 1.0f, t));
		float reach = sm(1.95f, 2.35f, t);
		float lift = 0.32f * sm(2.2f, 2.6f, t);
		float lower = 2.7f * hs * sm(2.9f, 3.85f, t) * sm(2.9f, 3.85f, t);
		float lean = 0.10f * sm(1.0f, 2.0f, t) + 0.30f * sm(2.9f, 3.6f, t) - 0.30f * sm(3.9f, 4.4f, t);
		float neckY = 1.5f * hs + lift - lower;
		float riftR = 1.3f * hs * sm(2.1f, 2.9f, t) * (1.0f - sm(3.95f, 4.6f, t));

		earth(g, 1.6f * sm(0.3f, 1.2f, t) * (1.0f - sm(4.4f, 5.0f, t)), 0.35f, seed);
		riftDraw(g, t, riftR, 1.0f);

		Pose p = new Pose();
		p.yaw = 1.5708f;
		if (t < 2.2f) p.tremble(t, 1.0f);
		else p.flail(t, sm(2.2f, 2.5f, t));
		p.lift = lift - lower;
		ghost(g, hgt, wid, p, g.in(sm(0.0f, 0.4f, t)) * (1.0f - sm(3.9f, 4.3f, t)));

		// реальная точка хвата в мире -> локальная система жнеца
		float wx = 0.18f * (wid / 0.6f) - D;
		float wy = neckY - hover;
		float wz = 0.0f;
		float lx = wz;
		float lz = -wx;
		float ly = wy;
		float cy = (float) Math.cos(lean);
		float sy = (float) Math.sin(lean);
		float[] grip = {lx, ly * cy + lz * sy, -ly * sy + lz * cy};

		g.push();
		g.translate(D, hover, 0.0f);
		g.rotY(-1.5708f);
		g.rotX(lean);
		drawReaper(g, t, ra, reach, lean, grip, 0.12f - 0.6f * sm(3.9f, 4.4f, t));
		g.pop();

		// дым вокруг подола
		for (int k = 0; k < 9; k++) {
			float ph = (t * 0.45f + rnd(k, seed) * 3.0f) % 1.0f;
			float ang = rnd(k, seed + 1) * 6.28f + t * 0.8f;
			float rr = 0.45f + 0.45f * rnd(k, seed + 2);
			smoke(g, D + cos(ang) * rr, hover + 0.1f + ph * 1.3f, sin(ang) * rr, 0.16f + 0.14f * ph, 0.34f * ra * (1.0f - ph), 1.0f);
		}
		if (t > 4.2f) {
			for (int k = 0; k < 10; k++) {
				float ph = cl((t - 4.2f) / 0.8f);
				float ang = rnd(k, seed + 7) * 6.28f;
				smoke(g, D + cos(ang) * 0.5f * ph, 0.8f + ph * (1.5f + rnd(k, seed + 8)), sin(ang) * 0.5f * ph, 0.3f * (1.0f - ph * 0.5f),
						0.4f * (1.0f - ph) * (1.0f - ph), 1.0f);
			}
		}
		debris(g, t, 2.9f, 8, seed + 5, 1.3f, 2.2f, 0.06f, 1.0f);
	}

	// ---------- Щупальца ----------

	static void sceneTentacles(KillGfx g, float t, float hgt, float wid, int seed) {
		float hs = hs(hgt);
		float sink = 3.3f * hs * sm(1.9f, 3.5f, t);
		float grow = eo(t / 0.95f);
		float wrap = sm(0.7f, 1.6f, t);
		float squeeze = sm(1.6f, 2.3f, t);
		float fade = 1.0f - sm(3.5f, 4.2f, t);

		g.mat(0.07f, 0.10f, 0.09f, 0.82f * fade * sm(0.0f, 0.4f, t));
		g.disc(0.0f, 0.02f, 0.0f, 1.65f * hs * eo(t / 0.7f), 22);
		g.mat(0.03f, 0.05f, 0.04f, 0.8f * fade * sm(0.0f, 0.4f, t));
		g.ring(0.0f, 0.021f, 0.0f, 1.0f * hs * eo(t / 0.7f), 1.2f * hs * eo(t / 0.7f), 22);

		Pose p = new Pose();
		if (t < 1.0f) p.tremble(t, 1.0f);
		else p.flail(t, sm(1.0f, 1.5f, t) * (1.0f - 0.4f * squeeze));
		p.lift = -sink * 0.97f;
		ghost(g, hgt, wid, p, g.in(sm(0.0f, 0.3f, t)) * (1.0f - sm(3.0f, 3.5f, t)));

		int count = 6;
		int n = 18;
		float[] x = new float[n];
		float[] y = new float[n];
		float[] z = new float[n];
		float[] r = new float[n];
		float baseR = 1.15f * hs;
		for (int k = 0; k < count; k++) {
			float th = k * 6.2832f / count + 0.4f * rnd(k, seed);
			float len = 0.8f + 0.2f * rnd(k, seed + 1);
			float g0 = cl(grow * 1.0f) * len;
			float phase = rnd(k, seed + 2) * 6.0f;
			for (int i = 0; i < n; i++) {
				float u = i / (float) (n - 1);
				float s = u * g0;
				float lean = 1.0f - 0.55f * s;
				float sw = sin(t * 2.3f + phase + s * 4.0f) * 0.30f * s * hs;
				float sw2 = cos(t * 2.0f + phase * 1.3f + s * 3.4f) * 0.30f * s * hs;
				float fx = cos(th) * baseR * lean + sw;
				float fz = sin(th) * baseR * lean + sw2;
				float fy = s * 3.1f * hs * (1.0f - 0.1f * s);
				float rho = (0.30f * (wid / 0.6f) + 0.14f) * hs * (1.0f - 0.2f * squeeze);
				float re = lerp(baseR, rho, sm(0.0f, 0.4f, s));
				float phi = th + s * 5.4f + t * 0.5f;
				float wx = cos(phi) * re;
				float wz = sin(phi) * re;
				float wy = 0.05f + s * 2.3f * hs;
				float w = wrap * sm(0.0f, 0.25f, s);
				x[i] = lerp(fx, wx, w);
				z[i] = lerp(fz, wz, w);
				y[i] = lerp(fy, wy, w) - sink;
				r[i] = (0.17f * (float) Math.pow(1.0f - u, 0.85f) + 0.016f) * hs;
			}
			float tone = 0.9f + 0.2f * rnd(k, seed + 3);
			g.mat(0.14f * tone, 0.10f * tone, 0.19f * tone, 1.0f);
			g.strand(x, y, z, r, n, 8);
			g.mat(0.60f, 0.42f, 0.50f, 1.0f);
			for (int i = 3; i < n - 2; i += 2) {
				float dx = -x[i];
				float dz = -z[i];
				float dl = (float) Math.sqrt(dx * dx + dz * dz);
				if (dl < 1.0e-4f) continue;
				float sx = x[i] + dx / dl * r[i] * 0.85f;
				float sz = z[i] + dz / dl * r[i] * 0.85f;
				g.ell(sx, y[i], sz, r[i] * 0.45f, r[i] * 0.45f, r[i] * 0.45f, 3, 5);
			}
		}
		debris(g, t, 0.1f, 16, seed, 1.9f * hs, 3.0f, 0.08f * hs, fade);
	}

	// ---------- Ангел ----------

	static void wing(KillGfx g, float a) {
		int nf = 10;
		for (int layer = 0; layer < 2; layer++) {
			for (int i = 0; i < nf; i++) {
				float u = i / (float) (nf - 1);
				float beta = 0.12f + u * 1.25f;
				float len = (layer == 0 ? 1.65f - 0.55f * u : 0.95f - 0.35f * u);
				float cb = cos(beta);
				float sb = sin(beta);
				float zoff = layer == 0 ? -0.04f : 0.03f;
				float shade = layer == 0 ? 1.0f : 0.93f;
				g.mat(0.97f * shade, 0.96f * shade, 0.93f * shade, a);
				float px = 0.0f;
				float py = 0.0f;
				int seg = 4;
				for (int s = 1; s <= seg; s++) {
					float v0 = (s - 1) / (float) seg;
					float v1 = s / (float) seg;
					float w0 = 0.11f * (float) Math.sin(Math.PI * Math.pow(Math.min(1.0f, v0 * 1.05f + 0.05f), 0.7));
					float w1 = 0.11f * (float) Math.sin(Math.PI * Math.pow(Math.min(1.0f, v1 * 1.05f + 0.05f), 0.7));
					float x0 = cb * len * v0;
					float y0 = sb * len * v0 - 0.15f * v0 * v0;
					float x1 = cb * len * v1;
					float y1 = sb * len * v1 - 0.15f * v1 * v1;
					float ox = -sb;
					float oy = cb;
					float z0 = zoff - 0.18f * v0 * v0;
					float z1 = zoff - 0.18f * v1 * v1;
					g.flat(x0 + ox * w0, y0 + oy * w0, z0, x0 - ox * w0, y0 - oy * w0, z0,
							x1 - ox * w1, y1 - oy * w1, z1, x1 + ox * w1, y1 + oy * w1, z1);
				}
			}
		}
		g.mat(0.99f, 0.98f, 0.96f, a);
		g.ell(0.22f, 0.14f, -0.02f, 0.34f, 0.15f, 0.07f, 4, 8);
	}

	static void drawAngel(KillGfx g, float t, float a, float flap, float[] handL, float[] handR, float reach) {
		if (a <= 0.01f) return;
		float[] ys = {0.0f, 0.35f, 0.8f, 1.2f, 1.5f, 1.72f};
		float[] rs = {0.52f, 0.43f, 0.31f, 0.25f, 0.27f, 0.2f};
		float[] amp = {0.07f, 0.05f, 0.03f, 0.01f, 0.0f, 0.0f};
		g.mat(0.97f, 0.96f, 0.93f, a);
		g.lathe(ys, rs, amp, 18, t * 1.2f, 6.0f, 1.0f, 0.9f);
		g.mat(0.86f, 0.70f, 0.26f, a);
		g.torus(0.0f, 0.04f, 0.0f, 0.52f, 0.018f, 18, 5);
		g.torus(0.0f, 0.82f, 0.0f, 0.31f, 0.018f, 16, 5);
		g.mat(0.97f, 0.96f, 0.93f, a);
		g.ell(0.0f, 1.64f, 0.0f, 0.31f, 0.12f, 0.19f, 4, 8);
		g.mat(0.96f, 0.84f, 0.74f, a);
		g.ell(0.0f, 1.93f, 0.0f, 0.15f, 0.17f, 0.15f, 5, 8);
		g.mat(0.88f, 0.72f, 0.36f, a);
		g.ell(0.0f, 1.97f, -0.035f, 0.165f, 0.17f, 0.16f, 4, 8);
		g.mat(0.2f, 0.15f, 0.12f, a);
		g.box(-0.055f, 1.94f, 0.146f, 0.03f, 0.005f, 0.008f);
		g.box(0.055f, 1.94f, 0.146f, 0.03f, 0.005f, 0.008f);
		boolean oe = g.emissive;
		g.emissive = true;
		g.mat(1.0f, 0.88f, 0.42f, a);
		g.push();
		g.translate(0.0f, 2.24f + 0.02f * sin(t * 3.0f), 0.0f);
		g.rotX(0.12f + 0.06f * sin(t * 2.0f));
		g.torus(0.0f, 0.0f, 0.0f, 0.21f, 0.018f, 18, 5);
		g.pop();
		g.emissive = oe;

		for (int s = -1; s <= 1; s += 2) {
			g.push();
			g.translate(s * 0.16f, 1.58f, -0.12f);
			g.rotY(s * 0.3f);
			g.rotZ(s * (0.32f + flap));
			g.scale(s, 1.0f, 1.0f);
			wing(g, a);
			g.pop();
		}
		// руки
		for (int s = -1; s <= 1; s += 2) {
			float[] h = s < 0 ? handL : handR;
			float[] rest = {s * 0.34f, 1.05f, 0.25f};
			float tx = lerp(rest[0], h[0], reach);
			float ty = lerp(rest[1], h[1], reach);
			float tz = lerp(rest[2], h[2], reach);
			float[] ik = KillGfx.ik(s * 0.30f, 1.62f, 0.0f, tx, ty, tz, 0.55f, 0.55f, s * 0.6f, -0.7f, -0.2f);
			g.mat(0.96f, 0.95f, 0.92f, a);
			g.tube(s * 0.30f, 1.62f, 0.0f, ik[0], ik[1], ik[2], 0.10f, 0.09f, 7);
			g.tube(ik[0], ik[1], ik[2], ik[3], ik[4], ik[5], 0.10f, 0.13f, 7);
			g.mat(0.96f, 0.84f, 0.74f, a);
			g.ell(ik[3], ik[4], ik[5], 0.07f, 0.07f, 0.07f, 3, 6);
		}
	}

	static float angelY(float t) {
		float down = 9.0f * (1.0f - eo(t / 1.4f));
		float up = 0.0f;
		if (t > 2.7f) {
			float u = (t - 2.7f) / 2.3f;
			up = 14.0f * u * u;
		}
		return 0.22f + 0.07f * sin(t * 2.4f) + down + up;
	}

	static void sceneAngel(KillGfx g, float t, float hgt, float wid, int seed) {
		float hs = hs(hgt);
		float ay = angelY(t);
		float ax = 1.15f - 0.52f * sm(1.9f, 2.5f, t);
		float aa = (1.0f - sm(4.7f, 5.45f, t)) * sm(0.0f, 0.25f, t);
		float flap = sin(t * (t < 1.5f ? 8.0f : (t > 2.7f ? 6.0f : 4.0f))) * (t < 2.0f ? 0.5f : 0.4f);
		float hold = sm(2.0f, 2.5f, t);
		float height = Math.max(0.0f, ay - 0.3f);
		float gy = (t > 2.5f ? Math.max(0.0f, ay - 0.3f) : 0.0f) * hold + 0.18f * hold * sm(2.3f, 2.7f, t);
		float ga = (1.0f - sm(6.0f, 11.0f, height)) * g.in(sm(0.0f, 0.3f, t));

		earth(g, 1.1f * sm(0.0f, 0.5f, t) * (1.0f - sm(1.0f, 2.2f, t)), 0.0f, seed);

		Pose p = new Pose();
		p.yaw = 1.5708f;
		if (t < 1.9f) {
			p.tremble(t, 0.6f);
			float look = sm(0.6f, 1.4f, t);
			p.head = -0.5f * look;
			p.armLx = -1.35f * sm(1.4f, 1.9f, t);
			p.armRx = -1.35f * sm(1.4f, 1.9f, t);
		} else {
			p.armLx = -0.5f;
			p.armRx = -0.5f;
			p.armLz = 0.9f;
			p.armRz = 0.9f;
			p.legL = 0.1f * sin(t * 2.0f);
			p.legR = -0.1f * sin(t * 2.0f);
			p.head = 0.25f;
		}
		p.lift = gy;
		ghost(g, hgt, wid, p, ga);

		float neck = 1.0f * hs + gy;
		float[] wl = {0.0f, 0.0f, 0.0f};
		// мировые цели -> локальная система ангела (повернут на -90°: x' = z, z' = -x)
		float[] tl = worldToAngel(-0.10f, neck - ay, 0.26f, ax);
		float[] tr = worldToAngel(-0.10f, neck - ay, -0.26f, ax);
		wl = tl;
		g.push();
		g.translate(ax, ay, 0.0f);
		g.rotY(-1.5708f);
		g.rotX(0.06f);
		// у ангела «левая» рука (s=-1) — со стороны -X локально = мирового -Z
		drawAngel(g, t, aa, flap, tr, wl, hold);
		g.pop();

		// перья
		for (int k = 0; k < 14; k++) {
			float t0 = 0.4f + k * 0.28f;
			float age = t - t0;
			if (age < 0.0f || age > 2.4f) continue;
			float oy = angelY(t0) + 1.2f;
			float ox = ax + (rnd(k, seed) - 0.5f) * 1.8f + sin(age * 2.0f + k) * 0.3f;
			float oz = (rnd(k, seed + 1) - 0.5f) * 1.8f + cos(age * 1.7f + k) * 0.3f;
			float yy = Math.max(0.03f, oy - 0.55f * age - 0.0f);
			float al = (1.0f - age / 2.4f) * aa;
			g.push();
			g.translate(ox, yy, oz);
			g.rotY(age * 1.5f + k);
			g.rotX(0.8f * sin(age * 3.0f + k));
			g.mat(0.99f, 0.98f, 0.96f, al * 0.9f);
			g.flat(-0.03f, 0.0f, -0.09f, 0.03f, 0.0f, -0.09f, 0.0f, 0.0f, 0.11f, 0.0f, 0.0f, 0.11f);
			g.pop();
		}
	}

	private static float[] worldToAngel(float wx, float wy, float wz, float ax) {
		float dx = wx - ax;
		return new float[]{wz, wy, -dx};
	}

	// ---------- Паук ----------

	static void drawSpider(KillGfx g, float t, float a, float w, float bodyY, float bite, int seed) {
		if (a <= 0.01f) return;
		g.mat(0.05f, 0.045f, 0.055f, a);
		g.ell(0.0f, 0.02f, 0.40f, 0.40f, 0.32f, 0.40f, 5, 9);
		g.ell(0.0f, 0.10f, -0.62f, 0.56f, 0.50f, 0.72f, 5, 10);
		g.mat(0.08f, 0.07f, 0.09f, a);
		g.ell(0.0f, 0.12f, -0.18f, 0.20f, 0.18f, 0.22f, 3, 6);
		boolean oe = g.emissive;
		g.emissive = true;
		g.mat(0.85f, 0.06f, 0.05f, a);
		g.flat(-0.09f, 0.55f, -0.62f, 0.09f, 0.55f, -0.62f, 0.0f, 0.55f, -0.50f, 0.0f, 0.55f, -0.50f);
		g.flat(-0.09f, 0.55f, -0.62f, 0.09f, 0.55f, -0.62f, 0.0f, 0.55f, -0.74f, 0.0f, 0.55f, -0.74f);
		float[][] eyes = {{-0.07f, 0.10f, 0.74f}, {0.07f, 0.10f, 0.74f}, {-0.15f, 0.07f, 0.70f}, {0.15f, 0.07f, 0.70f},
				{-0.05f, 0.18f, 0.72f}, {0.05f, 0.18f, 0.72f}, {-0.13f, 0.17f, 0.68f}, {0.13f, 0.17f, 0.68f}};
		for (int i = 0; i < eyes.length; i++) {
			float sz = i < 2 ? 0.045f : 0.032f;
			g.mat(0.95f, 0.08f, 0.06f, a);
			g.ell(eyes[i][0], eyes[i][1], eyes[i][2], sz, sz, sz, 3, 5);
		}
		g.emissive = oe;
		// хелицеры-клыки
		float open = 0.25f + 0.55f * bite;
		for (int s = -1; s <= 1; s += 2) {
			g.push();
			g.translate(s * 0.09f, -0.10f, 0.72f);
			g.rotZ(s * -open * 0.5f);
			g.rotX(0.15f);
			g.mat(0.12f, 0.10f, 0.10f, a);
			g.tube(0.0f, 0.0f, 0.0f, 0.0f, -0.14f, 0.02f, 0.055f, 0.045f, 6);
			g.mat(0.90f, 0.86f, 0.74f, a);
			g.tube(0.0f, -0.14f, 0.02f, 0.0f, -0.46f, 0.10f, 0.04f, 0.0f, 6);
			g.pop();
		}
		// ноги
		float groundRel = 0.03f - bodyY;
		for (int i = 0; i < 4; i++) {
			for (int s = -1; s <= 1; s += 2) {
				float hz = 0.62f - i * 0.30f;
				float hx = s * 0.26f;
				float hy = 0.0f;
				float gx = s * (1.25f + 0.25f * (i % 2));
				float gz = 0.20f + (1.5f - i) * 0.55f - 0.1f;
				float hxF = s * (0.85f + 0.14f * i);
				float hyF = -1.2f + 0.08f * sin(t * 6.0f + i * 1.7f + s);
				float hzF = (1.5f - i) * 0.5f + 0.2f * sin(t * 5.0f + i);
				float fx = lerp(hxF, gx, w);
				float fy = lerp(hyF, groundRel, w);
				float fz = lerp(hzF, gz, w);
				float[] ik = KillGfx.ik(hx, hy, hz + 0.0f, fx, fy, fz, 0.95f, 1.15f, s * 0.35f, 1.0f, 0.0f);
				g.mat(0.09f, 0.07f, 0.08f, a);
				g.tube(hx, hy, hz, ik[0], ik[1], ik[2], 0.075f, 0.06f, 6);
				g.tube(ik[0], ik[1], ik[2], ik[3], ik[4], ik[5], 0.058f, 0.018f, 6);
				g.mat(0.28f, 0.06f, 0.05f, a);
				g.ell(ik[0], ik[1], ik[2], 0.075f, 0.075f, 0.075f, 3, 6);
			}
		}
	}

	static void sceneSpider(KillGfx g, float t, float hgt, float wid, int seed) {
		float hs = hs(hgt);
		float by;
		if (t < 1.0f) by = 9.0f - 6.3f * eo(t / 1.0f);
		else if (t < 1.6f) by = lerp(2.7f, 2.15f, sm(1.0f, 1.6f, t));
		else if (t < 3.0f) by = 2.15f;
		else {
			float u = (t - 3.0f) / 1.8f;
			by = 2.15f + 12.0f * u * u;
		}
		by = by * (0.6f + 0.4f * hs);
		float bite = 0.0f;
		if (t >= 1.6f && t < 2.2f) {
			float u = (t - 1.6f) / 0.6f;
			bite = sin((float) Math.PI * u);
			by -= 0.13f * bite;
		}
		float grounded = sm(0.8f, 1.5f, t) * (1.0f - sm(3.0f, 3.5f, t));
		float a = (1.0f - sm(4.5f, 5.2f, t)) * sm(0.0f, 0.2f, t);
		float wrapH = sm(2.05f, 3.0f, t);
		float lift = Math.max(0.0f, by - 2.15f * (0.6f + 0.4f * hs));

		Pose p = new Pose();
		p.yaw = 0.0f;
		if (t < 1.6f) {
			p.tremble(t, 1.4f);
			p.head = -0.35f * sm(0.2f, 1.0f, t);
		} else p.flail(t, sm(1.6f, 1.9f, t) * (1.0f - 0.7f * wrapH));
		ghost(g, hgt, wid, p, g.in(sm(0.0f, 0.3f, t)) * (1.0f - sm(2.75f, 3.0f, t)));

		// нить
		float tz = -0.62f;
		boolean oe = g.emissive;
		g.mat(0.92f, 0.94f, 0.98f, 0.85f * a);
		g.bolt(0.0f, by + 0.2f, tz, 0.0f, by + 40.0f, tz, 0.012f);
		g.emissive = oe;

		g.push();
		g.translate(0.0f, by, -0.45f);
		g.rotY(0.25f * sin(t * 1.4f) * (1.0f - grounded));
		g.scale(0.85f + 0.15f * hs);
		drawSpider(g, t, a, grounded, by, bite, seed);
		g.pop();

		// кокон
		if (wrapH > 0.01f) {
			float hh = 1.95f * hs * wrapH;
			int rings = 8;
			float[] ys = new float[rings];
			float[] rs = new float[rings];
			float[] am = new float[rings];
			for (int i = 0; i < rings; i++) {
				float v = i / (float) (rings - 1);
				ys[i] = lift + v * hh;
				float prof = (float) Math.sin(Math.PI * (0.12f + 0.78f * v));
				rs[i] = (0.14f + 0.36f * prof) * (wid / 0.6f * 0.5f + 0.5f) * hs;
				am[i] = 0.05f;
			}
			g.mat(0.93f, 0.94f, 0.97f, a);
			g.lathe(ys, rs, am, 14, 0.5f, 3.0f, 1.0f, 0.9f);
			g.mat(0.60f, 0.62f, 0.68f, a);
			for (int i = 1; i < rings - 1; i++) {
				g.torus(0.0f, ys[i], 0.0f, rs[i] * 1.01f, 0.014f, 14, 4);
			}
			if (t > 3.0f) {
				g.mat(0.92f, 0.94f, 0.98f, 0.85f * a);
				g.tube(0.0f, ys[rings - 1], 0.0f, 0.0f, by - 0.2f, -0.45f, 0.014f, 0.014f, 4);
			}
		}
		debris(g, t, 3.0f, 6, seed + 2, 1.0f, 1.5f, 0.05f, 1.0f);
	}

	// ---------- Молния ----------

	static float lightningFlicker(float t) {
		if (t < 0.07f) return 1.0f;
		if (t < 0.11f) return 0.2f;
		if (t < 0.18f) return 1.0f;
		if (t < 0.5f) return 1.0f - (t - 0.18f) / 0.32f * 0.9f;
		return Math.max(0.0f, 0.1f * (1.0f - (t - 0.5f) / 0.2f));
	}

	static void sceneLightning(KillGfx g, float t, int seed) {
		float F = lightningFlicker(t);
		int sd = seed + (t < 0.11f ? 0 : 5);
		float top = 28.0f;
		int n = 24;
		float[] px = new float[n + 1];
		float[] py = new float[n + 1];
		float[] pz = new float[n + 1];
		float ox = (rnd(1, sd) - 0.5f) * 3.0f;
		float oz = (rnd(2, sd) - 0.5f) * 3.0f;
		for (int i = 0; i <= n; i++) {
			float u = i / (float) n;
			py[i] = top * (1.0f - u);
			if (i == n) {
				px[i] = 0.0f;
				pz[i] = 0.0f;
			} else {
				float pull = (float) Math.pow(u, 3.0);
				ox = ox * 0.6f + (rnd(i, sd + 3) - 0.5f) * 1.5f * (1.0f - pull);
				oz = oz * 0.6f + (rnd(i, sd + 4) - 0.5f) * 1.5f * (1.0f - pull);
				px[i] = ox * (1.0f - pull * 0.8f);
				pz[i] = oz * (1.0f - pull * 0.8f);
			}
		}
		if (F > 0.01f) {
			boolean oe = g.emissive;
			g.emissive = true;
			for (int i = 0; i < n; i++) {
				g.mat(0.45f, 0.55f, 1.0f, 0.16f * F);
				g.bolt(px[i], py[i], pz[i], px[i + 1], py[i + 1], pz[i + 1], 0.20f);
				g.mat(0.75f, 0.85f, 1.0f, 0.55f * F);
				g.bolt(px[i], py[i], pz[i], px[i + 1], py[i + 1], pz[i + 1], 0.07f);
				g.mat(1.0f, 1.0f, 1.0f, F);
				g.bolt(px[i], py[i], pz[i], px[i + 1], py[i + 1], pz[i + 1], 0.026f);
			}
			for (int b = 0; b < 5; b++) {
				int idx = 4 + (int) (rnd(b, sd + 9) * 12.0f);
				float bx = px[idx];
				float by = py[idx];
				float bz = pz[idx];
				float ang = rnd(b, sd + 10) * 6.28f;
				float dx = cos(ang) * 0.9f;
				float dz = sin(ang) * 0.9f;
				float sw = 0.0f;
				for (int s = 0; s < 5; s++) {
					float nx = bx + dx + (rnd(s + b * 7, sd + 11) - 0.5f) * 0.7f;
					float nz = bz + dz + (rnd(s + b * 7, sd + 12) - 0.5f) * 0.7f;
					float ny = by - 0.9f - rnd(s + b * 5, sd + 13) * 0.7f;
					float k = 1.0f - s / 5.0f;
					g.mat(0.6f, 0.72f, 1.0f, 0.22f * F * k);
					g.bolt(bx, by, bz, nx, ny, nz, 0.07f * k + 0.02f);
					g.mat(1.0f, 1.0f, 1.0f, 0.9f * F * k);
					g.bolt(bx, by, bz, nx, ny, nz, 0.014f * k + 0.005f);
					bx = nx;
					by = ny;
					bz = nz;
					sw += 1.0f;
				}
			}
			g.mat(0.7f, 0.82f, 1.0f, 0.5f * F);
			g.disc(0.0f, 0.04f, 0.0f, 1.1f, 20);
			g.mat(1.0f, 1.0f, 1.0f, 0.8f * F);
			g.disc(0.0f, 0.045f, 0.0f, 0.4f, 16);
			g.emissive = oe;
		}
		float ring = t / 0.5f;
		if (ring > 0.0f && ring < 1.0f) {
			boolean oe = g.emissive;
			g.emissive = true;
			float r = 0.4f + 3.4f * eo(ring);
			g.mat(0.75f, 0.85f, 1.0f, 0.6f * (1.0f - ring));
			g.ring(0.0f, 0.05f, 0.0f, r - 0.25f * (1.0f - ring) - 0.06f, r, 36);
			g.emissive = oe;
		}
		// подпалины
		float sa = sm(0.05f, 0.25f, t) * (1.0f - sm(1.5f, 2.5f, t));
		if (sa > 0.01f) {
			g.mat(0.025f, 0.025f, 0.03f, 0.7f * sa);
			g.disc(0.0f, 0.026f, 0.0f, 0.55f, 16);
			g.mat(0.03f, 0.03f, 0.035f, 0.6f * sa);
			for (int k = 0; k < 9; k++) {
				float a = k * 0.698f + rnd(k, seed) * 0.4f;
				float l = 0.6f + 0.6f * rnd(k, seed + 1);
				float ca = cos(a);
				float sn = sin(a);
				g.flat(ca * 0.3f - sn * 0.06f, 0.025f, sn * 0.3f + ca * 0.06f, ca * 0.3f + sn * 0.06f, 0.025f, sn * 0.3f - ca * 0.06f,
						ca * l, 0.025f, sn * l, ca * l, 0.025f, sn * l);
			}
		}
		// искры
		if (t > 0.03f && t < 0.9f) {
			boolean oe = g.emissive;
			g.emissive = true;
			float age = t - 0.03f;
			for (int k = 0; k < 14; k++) {
				float a = rnd(k, seed + 20) * 6.28f;
				float sp = 1.0f + rnd(k, seed + 21) * 2.2f;
				float vy = 2.0f + rnd(k, seed + 22) * 3.0f;
				float y = vy * age - 4.9f * age * age;
				if (y < 0.0f) continue;
				float al = 1.0f - age / 0.87f;
				g.mat(0.85f, 0.92f, 1.0f, al);
				g.box(cos(a) * sp * age, y + 0.04f, sin(a) * sp * age, 0.025f, 0.025f, 0.025f);
			}
			g.emissive = oe;
		}
	}

	// ---------- Гроб ----------

	private static final float[] COF = {-0.20f, -0.95f, 0.20f, -0.95f, 0.42f, 0.35f, 0.27f, 0.95f, -0.27f, 0.95f, -0.42f, 0.35f};
	private static final float H = 0.42f;
	private static final float HINGE = 0.40f;

	/** Диапазон z, в котором многоугольник (масштаб s) содержит линию x. */
	private static float[] polyZ(float s, float x) {
		float zmin = 1.0e9f;
		float zmax = -1.0e9f;
		int n = COF.length / 2;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			float x0 = COF[i * 2] * s;
			float z0 = COF[i * 2 + 1] * s;
			float x1 = COF[j * 2] * s;
			float z1 = COF[j * 2 + 1] * s;
			if ((x0 - x) * (x1 - x) <= 0.0f && Math.abs(x1 - x0) > 1.0e-6f) {
				float u = (x - x0) / (x1 - x0);
				float z = z0 + (z1 - z0) * u;
				zmin = Math.min(zmin, z);
				zmax = Math.max(zmax, z);
			}
		}
		return new float[]{zmin, zmax};
	}

	private static void edgeLine(KillGfx g, int i, float f0, float f1, float y, float th, float off, float s) {
		int n = COF.length / 2;
		int j = (i + 1) % n;
		float ax = COF[i * 2] * s;
		float az = COF[i * 2 + 1] * s;
		float bx = COF[j * 2] * s;
		float bz = COF[j * 2 + 1] * s;
		float dx = bx - ax;
		float dz = bz - az;
		float dl = (float) Math.sqrt(dx * dx + dz * dz);
		float nx = dz / dl;
		float nz = -dx / dl;
		float mx = (ax + bx) * 0.5f;
		float mz = (az + bz) * 0.5f;
		if (nx * mx + nz * mz < 0.0f) {
			nx = -nx;
			nz = -nz;
		}
		float x0 = ax + dx * f0 + nx * off;
		float z0 = az + dz * f0 + nz * off;
		float x1 = ax + dx * f1 + nx * off;
		float z1 = az + dz * f1 + nz * off;
		g.flat(x0, y - th, z0, x1, y - th, z1, x1, y + th, z1, x0, y + th, z0);
	}

	private static void handle(KillGfx g, int i, float y) {
		int n = COF.length / 2;
		int j = (i + 1) % n;
		float ax = COF[i * 2];
		float az = COF[i * 2 + 1];
		float bx = COF[j * 2];
		float bz = COF[j * 2 + 1];
		float dx = bx - ax;
		float dz = bz - az;
		float dl = (float) Math.sqrt(dx * dx + dz * dz);
		float nx = dz / dl;
		float nz = -dx / dl;
		float mx = (ax + bx) * 0.5f;
		float mz = (az + bz) * 0.5f;
		if (nx * mx + nz * mz < 0.0f) {
			nx = -nx;
			nz = -nz;
		}
		g.push();
		g.translate(mx, y, mz);
		g.rotY((float) Math.atan2(nx, nz));
		float hl = Math.min(0.13f, dl * 0.22f);
		g.box(-hl, 0.0f, 0.012f, 0.018f, 0.022f, 0.012f);
		g.box(hl, 0.0f, 0.012f, 0.018f, 0.022f, 0.012f);
		g.tube(-hl, 0.0f, 0.032f, hl, 0.0f, 0.032f, 0.013f, 0.013f, 5);
		g.pop();
	}

	static void sceneCoffin(KillGfx g, float t, int style, int seed, float hs) {
		float[] wood = WOOD[Math.max(0, Math.min(WOOD.length - 1, style))];
		float[] trim = TRIM[Math.max(0, Math.min(TRIM.length - 1, style))];
		float[] dark = {wood[0] * 0.32f, wood[1] * 0.32f, wood[2] * 0.32f};
		float[] line = {wood[0] * 0.45f, wood[1] * 0.45f, wood[2] * 0.45f};
		float e = backOut(t / 0.6f);
		float y = -(H + 0.1f) * (1.0f - e);
		float sink = sm(2.95f, 3.6f, t);
		y -= (H + 0.2f) * sink * sink;
		float shake = (t > 2.68f && t < 2.9f) ? sin((t - 2.68f) * 70.0f) * 0.012f * (1.0f - (t - 2.68f) / 0.22f) : 0.0f;
		float lid;
		if (t < 2.3f) lid = 1.72f * backOut((t - 0.95f) / 0.55f);
		else {
			float u = cl((t - 2.3f) / 0.4f);
			lid = 1.72f * (1.0f - u * u * u);
			if (t > 2.7f && t < 2.88f) lid = 0.06f * sin((float) Math.PI * (t - 2.7f) / 0.18f);
		}
		if (t < 0.95f) lid = 0.0f;
		float ground = 1.0f - sm(3.0f, 3.6f, t);
		earth(g, 0.0f, 0.0f, seed);
		g.mat(0.17f, 0.12f, 0.08f, 0.9f * ground * sm(0.0f, 0.2f, t));
		g.prism(COF, 0.0f, 0.02f, 1.5f * (0.6f + 0.4f * e), 1.5f * (0.6f + 0.4f * e), true, false);
		g.mat(0.08f, 0.06f, 0.05f, 0.8f * ground * sm(0.0f, 0.2f, t));
		g.prism(COF, 0.0f, 0.022f, 1.18f, 1.18f, true, false);

		g.push();
		g.translate(0.0f, y + 0.012f + shake, 0.0f);
		g.rotZ(sin(t * 9.0f) * 0.04f * (1.0f - cl(t / 0.6f)));

		// плинтус и корпус
		g.mat(dark[0] * 1.4f, dark[1] * 1.4f, dark[2] * 1.4f, 1.0f);
		g.prism(COF, 0.0f, 0.06f, 1.045f, 1.035f, false, true);
		for (int i = 0; i < 6; i++) {
			float v = 0.90f + 0.12f * rnd(i, seed);
			g.mat(wood[0] * v, wood[1] * v, wood[2] * v, 1.0f);
			float x0 = COF[i * 2];
			float z0 = COF[i * 2 + 1];
			int j = (i + 1) % 6;
			float x1 = COF[j * 2];
			float z1 = COF[j * 2 + 1];
			g.tex(x0 * 1.035f, 0.06f, z0 * 1.035f, x1 * 1.035f, 0.06f, z1 * 1.035f, x1, H, z1, x0, H, z0, 6, 7, 0.16f, seed * 7 + i);
			g.mat(line, 1.0f);
			for (int k = 0; k < 4; k++) {
				float f = 0.16f + k * 0.22f;
				edgeLine(g, i, 0.03f, 0.97f, 0.06f + (H - 0.06f) * (k + 1) / 5.0f, 0.004f, 0.003f + 0.002f, 1.03f - 0.03f * (k + 1) / 5.0f);
				edgeLine(g, i, f, f + 0.004f, (0.06f + H) * 0.5f, (H - 0.06f) * 0.5f, 0.004f, 1.0f);
			}
		}
		// металлический пояс и стойки
		g.mat(trim, 1.0f);
		g.prism(COF, 0.17f, 0.215f, 1.027f, 1.021f, false, false);
		for (int i = 0; i < 6; i++) {
			g.box(COF[i * 2] * 1.015f, H * 0.5f + 0.03f, COF[i * 2 + 1] * 1.015f, 0.022f, H * 0.5f - 0.02f, 0.022f);
		}
		g.mat(trim[0] * 0.85f, trim[1] * 0.85f, trim[2] * 0.85f, 1.0f);
		handle(g, 1, 0.27f);
		handle(g, 5, 0.27f);
		handle(g, 2, 0.27f);
		handle(g, 4, 0.27f);
		// верхний кант и внутренность
		g.mat(wood[0] * 1.1f, wood[1] * 1.1f, wood[2] * 1.1f, 1.0f);
		for (int i = 0; i < 6; i++) {
			int j = (i + 1) % 6;
			g.flat(COF[i * 2], H, COF[i * 2 + 1], COF[j * 2], H, COF[j * 2 + 1], COF[j * 2] * 0.86f, H, COF[j * 2 + 1] * 0.86f,
					COF[i * 2] * 0.86f, H, COF[i * 2 + 1] * 0.86f);
		}
		g.mat(0.06f, 0.04f, 0.04f, 1.0f);
		g.prism(COF, H - 0.34f, H, 0.86f, 0.86f, false, false);
		g.mat(0.30f, 0.04f, 0.09f, 1.0f);
		g.prism(COF, H - 0.34f, H - 0.339f, 0.86f, 0.86f, true, false);
		g.mat(0.46f, 0.09f, 0.15f, 1.0f);
		g.box(0.0f, H - 0.30f, 0.62f, 0.15f, 0.04f, 0.14f);

		// настоящая модель убитого лежит в гробу на боку (в позе смерти), по размеру гроба
		if (g.realVictim) {
			float vh = g.vh;
			float vw = g.vw;
			boolean tall = vh > vw * 1.4f;
			float len = tall ? vh : vw * 1.25f;
			float lateral = tall ? vw * 0.6f : vh;
			float sc = Math.min(1.0f, Math.min(0.26f / Math.max(0.2f, vw), Math.min(1.5f / Math.max(0.2f, len), 0.62f / Math.max(0.2f, lateral))));
			sc = Math.max(0.12f, sc);
			float vis = 1.0f - sm(3.3f, 3.6f, t);
			if (vis > 0.03f) {
				float k = vis * vis * (3.0f - 2.0f * vis);
				g.push();
				g.translate(0.0f, H - 0.27f + 0.5f * vw * sc, 0.0f);
				if (tall) {
					g.translate(0.0f, 0.0f, -vh * sc * 0.5f);
					g.rotY(1.5707964f);
				} else {
					g.translate(vh * sc * 0.5f, 0.0f, 0.0f);
				}
				float ss = sc * (0.2f + 0.8f * k);
				g.scale(ss, ss, ss);
				g.victim(vis, true);
				g.pop();
			}
		}

		// крышка
		g.push();
		g.translate(HINGE, H + 0.006f, 0.0f);
		g.rotZ(-lid);
		g.translate(-HINGE, 0.0f, 0.0f);
		float lh = 0.14f;
		g.mat(wood[0] * 0.85f, wood[1] * 0.85f, wood[2] * 0.85f, 1.0f);
		g.prism(COF, 0.0f, lh, 1.045f, 0.90f, false, true);
		g.mat(trim, 1.0f);
		g.prism(COF, 0.0f, 0.03f, 1.046f, 1.04f, false, false);
		// плашки крышки
		float[] xs = {-0.33f, -0.22f, -0.11f, 0.0f, 0.11f, 0.22f, 0.33f};
		for (int k = 0; k < xs.length - 1; k++) {
			float xa = xs[k] + 0.004f;
			float xb = xs[k + 1] - 0.004f;
			float[] za = polyZ(0.90f, xa);
			float[] zb = polyZ(0.90f, xb);
			float z0 = Math.max(za[0], zb[0]) + 0.01f;
			float z1 = Math.min(za[1], zb[1]) - 0.01f;
			if (z1 <= z0) continue;
			float v = 0.88f + 0.2f * rnd(k, seed + 4);
			g.mat(wood[0] * v, wood[1] * v, wood[2] * v, 1.0f);
			g.tex(xa, lh + 0.001f, z0, xb, lh + 0.001f, z0, xb, lh + 0.001f, z1, xa, lh + 0.001f, z1, 2, 14, 0.14f, seed * 5 + k);
			g.mat(line, 1.0f);
			g.flat(xa + 0.002f, lh + 0.0015f, z0 + 0.04f, xa + 0.006f, lh + 0.0015f, z0 + 0.04f, xa + 0.006f, lh + 0.0015f, z1 - 0.1f,
					xa + 0.002f, lh + 0.0015f, z1 - 0.1f);
		}
		// канты между плашками
		g.mat(line, 1.0f);
		for (int k = 1; k < xs.length - 1; k++) {
			float[] zr = polyZ(0.90f, xs[k]);
			g.flat(xs[k] - 0.004f, lh + 0.0012f, zr[0] + 0.01f, xs[k] + 0.004f, lh + 0.0012f, zr[0] + 0.01f,
					xs[k] + 0.004f, lh + 0.0012f, zr[1] - 0.01f, xs[k] - 0.004f, lh + 0.0012f, zr[1] - 0.01f);
		}
		// рамка, крест и шипы
		g.mat(trim, 1.0f);
		for (int i = 0; i < 6; i++) {
			int j = (i + 1) % 6;
			g.flat(COF[i * 2] * 0.90f, lh + 0.003f, COF[i * 2 + 1] * 0.90f, COF[j * 2] * 0.90f, lh + 0.003f, COF[j * 2 + 1] * 0.90f,
					COF[j * 2] * 0.85f, lh + 0.003f, COF[j * 2 + 1] * 0.85f, COF[i * 2] * 0.85f, lh + 0.003f, COF[i * 2 + 1] * 0.85f);
			g.box(COF[i * 2] * 0.875f, lh + 0.012f, COF[i * 2 + 1] * 0.875f, 0.026f, 0.012f, 0.026f);
		}
		g.box(0.0f, lh + 0.008f, 0.05f, 0.03f, 0.008f, 0.52f);
		g.box(0.0f, lh + 0.008f, 0.32f, 0.15f, 0.008f, 0.03f);
		g.pop();
		g.pop();

		debris(g, t, 0.05f, 14, seed, 1.4f, 2.4f, 0.07f, ground);
		debris(g, t, 2.7f, 6, seed + 3, 1.0f, 1.2f, 0.04f, ground);
	}
}
