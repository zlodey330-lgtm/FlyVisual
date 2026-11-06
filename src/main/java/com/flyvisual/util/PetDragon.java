package com.flyvisual.util;

import static com.flyvisual.util.Sdf.capsule;
import static com.flyvisual.util.Sdf.ellipsoid;
import static com.flyvisual.util.Sdf.flatY;
import static com.flyvisual.util.Sdf.mirrorX;
import static com.flyvisual.util.Sdf.smooth;
import static com.flyvisual.util.Sdf.union;

/** Изумрудный дракон: чешуя, золотистое брюхо, рога, шипы по хребту и крылья с перепонкой. Парит в воздухе. */
public final class PetDragon {
	private static final int GREEN = 0x2F9A5B;
	private static final int GREEN_DARK = 0x1B5E3A;
	private static final int GOLD = 0xE6C97A;
	private static final int HORN = 0xEADFC4;
	private static final int MEMBRANE = 0x3B2A5E;
	private static final int MEMBRANE_EDGE = 0x7C4FA8;

	private static VoxelPart body;
	private static VoxelPart[] neck;
	private static VoxelPart head;
	private static VoxelPart[] tail;
	private static VoxelPart wingBones;
	private static VoxelPart legHind;
	private static VoxelPart legFront;

	private static final float NECK_LEN = 0.075f;
	private static final float TAIL_LEN = 0.085f;
	private static final int NECK_SEGS = 3;
	private static final int TAIL_SEGS = 6;
	private static final float[] NECK_REST = {-32.0f, -26.0f, 18.0f};
	private static final float[] TAIL_REST = {-6.0f, 2.0f, 6.0f, 8.0f, 6.0f, 2.0f};

	/** Контур перепонки (x, z) относительно плеча, для правого крыла. */
	private static final float[] WING = {
			0.03f, 0.0f, 0.28f, -0.05f, 0.56f, -0.16f, 0.8f, -0.3f, 0.63f, -0.35f, 0.62f, -0.51f,
			0.46f, -0.43f, 0.38f, -0.57f, 0.25f, -0.42f, 0.03f, -0.32f};
	private static final float[] WING_CENTER = {0.4f, -0.27f};

	private PetDragon() {
	}

	private static float scales(float x, float y, float z) {
		float n = Noise.noise(x * 55.0f, y * 55.0f, z * 55.0f);
		float m = Noise.noise(x * 28.0f + 5.0f, y * 28.0f, z * 28.0f);
		return 0.82f + 0.2f * n + 0.1f * m;
	}

	private static void init() {
		if (body != null) return;
		final float v = 0.016f;

		// ---------- Туловище ----------
		Sdf torso = smooth(0.06f,
				ellipsoid(0, 0.0f, 0.07f, 0.092f, 0.088f, 0.15f),
				ellipsoid(0, -0.005f, -0.12f, 0.075f, 0.076f, 0.12f),
				capsule(0, 0.0f, 0.15f, 0, 0.02f, 0.2f, 0.07f, 0.055f));
		Sdf spikes = null;
		Sdf[] sp = new Sdf[9];
		for (int i = 0; i < sp.length; i++) {
			float z = 0.16f - i * 0.045f;
			sp[i] = capsule(0, 0.08f - Math.abs(i - 3) * 0.003f, z, 0, 0.125f - i * 0.003f, z - 0.025f, 0.014f, 0.002f);
		}
		spikes = union(sp);
		Sdf bodySdf = union(torso, spikes);
		final Sdf torsoRef = torso;
		Sdf.Paint bodyPaint = (x, y, z) -> {
			int c = PetCtx.mix(GREEN, GREEN_DARK, Noise.smoothstep(0.02f, 0.09f, y) * 0.8f);
			float belly = 1.0f - Noise.smoothstep(-0.07f, -0.015f, y);
			float bandLines = 0.5f + 0.5f * (float) Math.sin(z * 120.0f);
			c = PetCtx.mix(c, PetCtx.mix(GOLD, 0xC8A456, bandLines * 0.5f), belly);
			if (torsoRef.d(x, y, z) > 0.0f) {
				c = HORN;
			}
			return PetCtx.mul(c, scales(x, y, z));
		};
		body = VoxelPart.build(v, -0.14f, -0.12f, -0.27f, 0.14f, 0.14f, 0.3f, bodySdf, bodyPaint, 0, 0, 0);

		// ---------- Шея из трёх звеньев ----------
		neck = new VoxelPart[NECK_SEGS];
		for (int i = 0; i < NECK_SEGS; i++) {
			final int idx = i;
			float r0 = 0.062f - i * 0.008f;
			float r1 = 0.054f - i * 0.008f;
			Sdf seg = capsule(0, 0, 0, 0, 0, NECK_LEN, r0, r1);
			Sdf.Paint paint = (x, y, z) -> {
				int c = PetCtx.mix(GREEN, GOLD, 1.0f - Noise.smoothstep(-0.04f, 0.0f, y));
				return PetCtx.mul(c, scales(x + idx, y, z));
			};
			neck[i] = VoxelPart.build(v * 0.9f, -0.07f, -0.07f, -0.07f, 0.07f, 0.07f, NECK_LEN + 0.07f, seg, paint, 0, 0, 0);
		}

		// ---------- Голова ----------
		Sdf skull = smooth(0.025f,
				ellipsoid(0, 0.0f, 0.045f, 0.056f, 0.05f, 0.068f),
				capsule(0, -0.012f, 0.09f, 0, -0.016f, 0.165f, 0.036f, 0.022f),
				ellipsoid(0, -0.034f, 0.095f, 0.03f, 0.014f, 0.065f),
				mirrorX(ellipsoid(0.04f, 0.026f, 0.06f, 0.024f, 0.022f, 0.03f)));
		Sdf horns = mirrorX(smooth(0.01f,
				capsule(0.03f, 0.04f, 0.0f, 0.052f, 0.075f, -0.07f, 0.015f, 0.008f),
				capsule(0.052f, 0.075f, -0.07f, 0.05f, 0.07f, -0.13f, 0.008f, 0.002f)));
		Sdf crest = mirrorX(capsule(0.012f, 0.05f, 0.0f, 0.016f, 0.075f, -0.04f, 0.008f, 0.002f));
		Sdf headAll = union(skull, horns, crest);
		final Sdf skullRef = skull;
		Sdf.Paint headPaint = (x, y, z) -> {
			int c = GREEN;
			c = PetCtx.mix(c, GOLD, 1.0f - Noise.smoothstep(-0.03f, 0.0f, y));
			c = PetCtx.mix(c, GREEN_DARK, Noise.smoothstep(0.02f, 0.05f, y) * 0.5f);
			if (skullRef.d(x, y, z) > 0.0f) {
				c = PetCtx.mix(HORN, GREEN, 1.0f - Noise.smoothstep(0.03f, 0.05f, y));
			}
			return PetCtx.mul(c, scales(x, y, z));
		};
		head = VoxelPart.build(v * 0.75f, -0.1f, -0.06f, -0.15f, 0.1f, 0.1f, 0.19f, headAll, headPaint, 0, 0, 0);

		// ---------- Хвост ----------
		tail = new VoxelPart[TAIL_SEGS];
		for (int i = 0; i < TAIL_SEGS; i++) {
			final int idx = i;
			float r0 = 0.07f - i * 0.0105f;
			float r1 = 0.07f - (i + 1) * 0.0105f;
			Sdf seg = capsule(0, 0, 0, 0, 0, -TAIL_LEN, r0, Math.max(0.008f, r1));
			Sdf full = seg;
			if (i == TAIL_SEGS - 1) {
				// на конце плоский шип-лопасть
				full = union(seg, flatY(ellipsoid(0, 0, -TAIL_LEN - 0.02f, 0.045f, 0.01f, 0.05f), 0, 1.0f));
			}
			Sdf finalSeg = full;
			Sdf.Paint paint = (x, y, z) -> {
				int c = PetCtx.mix(GREEN, GOLD, (1.0f - Noise.smoothstep(-0.03f, 0.0f, y)) * 0.8f);
				if (idx == TAIL_SEGS - 1 && z < -TAIL_LEN - 0.005f) {
					c = PetCtx.mix(MEMBRANE_EDGE, GREEN_DARK, 0.3f);
				}
				return PetCtx.mul(c, scales(x + idx * 3, y, z));
			};
			tail[i] = VoxelPart.build(v * 0.85f, -0.08f, -0.08f, -TAIL_LEN - 0.1f, 0.08f, 0.08f, 0.07f, finalSeg, paint, 0, 0, 0);
		}

		// ---------- Кости крыла (правое, левое зеркалом) ----------
		Sdf bones = smooth(0.012f,
				capsule(0, 0, 0, 0.28f, 0.06f, -0.05f, 0.026f, 0.017f),
				capsule(0.28f, 0.06f, -0.05f, 0.56f, 0.0f, -0.16f, 0.017f, 0.011f),
				capsule(0.56f, 0.0f, -0.16f, 0.8f, 0.0f, -0.3f, 0.011f, 0.005f),
				capsule(0.56f, 0.0f, -0.16f, 0.62f, 0.0f, -0.51f, 0.011f, 0.005f),
				capsule(0.56f, 0.0f, -0.16f, 0.38f, 0.0f, -0.57f, 0.011f, 0.005f));
		Sdf.Paint bonePaint = (x, y, z) -> PetCtx.mul(PetCtx.mix(GREEN, HORN, Noise.smoothstep(0.5f, 0.8f, x) * 0.7f), scales(x, y, z));
		wingBones = VoxelPart.build(v * 0.75f, -0.02f, -0.04f, -0.6f, 0.84f, 0.1f, 0.04f, bones, bonePaint, 0, 0, 0);

		// ---------- Лапы ----------
		Sdf hind = smooth(0.03f,
				capsule(0, 0, 0, 0.012f, -0.08f, 0.02f, 0.04f, 0.028f),
				capsule(0.012f, -0.08f, 0.02f, -0.005f, -0.14f, -0.05f, 0.028f, 0.018f),
				capsule(-0.005f, -0.14f, -0.05f, 0.0f, -0.16f, 0.02f, 0.018f, 0.014f),
				ellipsoid(0.0f, -0.17f, 0.04f, 0.022f, 0.014f, 0.034f));
		Sdf.Paint legPaint = (x, y, z) -> PetCtx.mul(PetCtx.mix(GREEN, GREEN_DARK, 0.3f + Noise.smoothstep(-0.12f, -0.16f, y) * 0.5f), scales(x, y, z));
		legHind = VoxelPart.build(v * 0.85f, -0.07f, -0.2f, -0.1f, 0.07f, 0.04f, 0.1f, hind, legPaint, 0, 0, 0);
		Sdf front = smooth(0.025f,
				capsule(0, 0, 0, 0.01f, -0.06f, 0.05f, 0.03f, 0.02f),
				capsule(0.01f, -0.06f, 0.05f, 0.0f, -0.1f, 0.09f, 0.02f, 0.014f),
				ellipsoid(0.0f, -0.11f, 0.105f, 0.018f, 0.012f, 0.026f));
		legFront = VoxelPart.build(v * 0.8f, -0.05f, -0.15f, -0.04f, 0.05f, 0.04f, 0.16f, front, legPaint, 0, 0, 0);
	}

	/** Рисует дракона. Начало координат = центр туловища; питомец парит выше земли за счёт PetModule. */
	public static void draw(PetCtx c) {
		init();
		float t = c.time;
		float m = c.moving;
		float flap = PetCtx.sin(t * (5.5f + 2.0f * m));

		c.push();
		c.rx(-4.0f * m + PetCtx.sin(t * 1.2f) * 1.5f);
		c.scale(0.85f, 0.85f, 0.85f);

		body.draw(c.ms, c.solid, c.light, 1.0f);

		// Лапы подобраны к брюху
		for (int s = -1; s <= 1; s += 2) {
			c.push();
			c.scale(s, 1, 1);
			c.tr(0.075f, -0.035f, -0.12f);
			c.rx(-10.0f + PetCtx.sin(t * 1.3f) * 3.0f);
			legHind.draw(c.ms, c.solid, c.light, 1.0f);
			c.pop();
			c.push();
			c.scale(s, 1, 1);
			c.tr(0.07f, -0.04f, 0.17f);
			c.rx(-25.0f);
			legFront.draw(c.ms, c.solid, c.light, 1.0f);
			c.pop();
		}

		// Шея и голова
		c.push();
		c.tr(0, 0.02f, 0.2f);
		for (int i = 0; i < NECK_SEGS; i++) {
			c.ry(PetCtx.clamp(c.lookYaw, -50.0f, 50.0f) / NECK_SEGS * 0.8f + PetCtx.sin(t * 0.7f - i * 0.5f) * 3.0f);
			c.rx(NECK_REST[i] + (i == NECK_SEGS - 1 ? -PetCtx.clamp(c.lookPitch, -20.0f, 30.0f) : 0.0f) + PetCtx.sin(t * 1.1f - i * 0.4f) * 2.0f);
			neck[i].draw(c.ms, c.solid, c.light, 1.0f);
			c.tr(0, 0, NECK_LEN);
		}
		head.draw(c.ms, c.solid, c.light, 1.0f);
		headDetails(c);
		c.pop();

		// Хвост
		c.push();
		c.tr(0, 0.0f, -0.23f);
		for (int i = 0; i < TAIL_SEGS; i++) {
			c.ry(PetCtx.sin(t * 2.0f - i * 0.8f) * (9.0f + 4.0f * m));
			c.rx(TAIL_REST[i] + PetCtx.sin(t * 1.5f - i * 0.6f) * 2.0f);
			tail[i].draw(c.ms, c.solid, c.light, 1.0f);
			c.tr(0, 0, -TAIL_LEN);
		}
		c.pop();

		// Крылья
		for (int s = -1; s <= 1; s += 2) {
			c.push();
			c.scale(s, 1, 1);
			c.tr(0.075f, 0.075f, 0.06f);
			c.rz(18.0f + flap * 34.0f);
			c.ry(-8.0f + flap * 6.0f);
			c.scale(0.8f, 0.8f, 0.8f);
			wingBones.draw(c.ms, c.solid, c.light, 1.0f);
			membrane(c);
			c.pop();
		}
		c.pop();
	}

	private static void headDetails(PetCtx c) {
		float blink = (c.time % 5.7f) < 0.14f ? 0.1f : 1.0f;
		for (int s = -1; s <= 1; s += 2) {
			c.cube(s * 0.034f, 0.026f, 0.062f, 0.018f, 0.016f * blink, 0.012f, 0xFFC21A, 1.0f, 1.0f);
			if (blink > 0.5f) {
				c.cube(s * 0.034f, 0.026f, 0.069f, 0.005f, 0.015f, 0.006f, 0x120A00, 1.0f, 1.0f);
			}
			c.cube(s * 0.012f, 0.0f, 0.165f, 0.01f, 0.008f, 0.008f, 0x120A0A, 1.0f, c.light);
		}
	}

	/** Перепонка крыла: веер из треугольников с мягким градиентом цвета, чуть прозрачная. */
	private static void membrane(PetCtx c) {
		org.joml.Matrix4f m = c.ms.peek().getPositionMatrix();
		int n = WING.length / 2;
		float cx = WING_CENTER[0];
		float cz = WING_CENTER[1];
		float l = c.light;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			float x0 = WING[i * 2];
			float z0 = WING[i * 2 + 1];
			float x1 = WING[j * 2];
			float z1 = WING[j * 2 + 1];
			int inner = MEMBRANE;
			int edge = PetCtx.mix(MEMBRANE, MEMBRANE_EDGE, 0.8f);
			float ri = ((inner >> 16) & 0xFF) / 255.0f * l;
			float gi = ((inner >> 8) & 0xFF) / 255.0f * l;
			float bi = (inner & 0xFF) / 255.0f * l;
			float re = ((edge >> 16) & 0xFF) / 255.0f * l;
			float ge = ((edge >> 8) & 0xFF) / 255.0f * l;
			float be = (edge & 0xFF) / 255.0f * l;
			Draw3d d = c.glass;
			d.vert(m, cx, 0.0f, cz, ri, gi, bi, 0.93f);
			d.vert(m, x0, 0.0f, z0, re, ge, be, 0.93f);
			d.vert(m, x1, 0.0f, z1, re, ge, be, 0.93f);
			d.vert(m, x1, 0.0f, z1, re, ge, be, 0.93f);
		}
	}
}
