package com.flyvisual.util;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * Питомцы: свои реалистичные модели. Кошка, собака, лисичка и дракончик собраны из мелких кубиков с шерстью
 * и тенями, огонёк это живое пламя, призрак остался прежним. Не мобы из Minecraft, никаких сущностей и ИИ.
 * Система координат модели: +Z = вперёд, +Y = вверх.
 */
public final class PetRenderer {
	/** Что видно в списке выбора. */
	public static final String[] NAMES = {"Дракончик", "Призрак", "Огонёк", "Пантера", "Кубокот", "Лисёнок", "Зайка", "Панда", "Совёнок",
			"Бу", "Слайм", "Дрон", "Тыква-нетопырь"};
	/** Внутренний номер модели для каждой строки списка. */
	private static final int[] ID = {22, 4, 5, 23, 13, 14, 15, 16, 17, 18, 19, 20, 21};
	public static final boolean[] FLYING = {true, true, true, false, false, false, false, false, true, true, false, true, true};

	private static final ChibiPetFx[] CHIBI = new ChibiPetFx[11];
	private static final float[] CHIBI_SCALE = {1.5f, 1.5f, 1.5f, 1.7f, 1.4f, 1.5f, 1.6f, 1.9f, 1.4f, 1.5f, 1.5f};
	private static final Quadruped[] ANIMALS = new Quadruped[13];
	/** Масштаб моделей: кошка, хаски, лиса. */
	private static final float[] SCALE = {1.0f, 1.35f, 1.05f, 1.0f, 1.3f, 1.55f, 1.4f, 0.95f, 1.4f, 1.0f};

	private static final int MAIN = 0;
	private static final int LIGHT = 1;
	private static final int DARK = 2;
	private static final int EYE = 3;
	private static final int NOSE = 4;
	/** Палитра призрака (его модель не менялась). */
	private static final int[] GHOST = {0xF2F4FF, 0xFFFFFF, 0xB9C2E8, 0x1A1A2E, 0xFFB4C8};

	// Состояние отрисовки призрака
	private static MatrixStack ms;
	private static Draw3d d;
	private static float light = 1.0f;
	private static boolean unlit;
	private static float time;

	private static int quality;
	private static final float[] QUALITY_SCALE = {1.0f, 0.7f, 0.55f};

	private PetRenderer() {
	}

	/** Множитель размера кубика для текущего качества (1 = обычное, меньше = глаже). */
	public static float qualityScale() {
		return QUALITY_SCALE[quality];
	}

	/** Качество 0..2. При смене модели пересобираются заново (первый показ может чуть подвиснуть). */
	public static void setQuality(int q) {
		q = Math.max(0, Math.min(QUALITY_SCALE.length - 1, q));
		if (q == quality) return;
		quality = q;
		PetFactory.RES = 0.0135f * QUALITY_SCALE[q];
		java.util.Arrays.fill(ANIMALS, null);
		PetDragon.reset();
	}

	/** Рисует питомца в начале координат текущей матрицы. */
	public static void draw(int kind, MatrixStack matrices, Draw3d solid, Draw3d glass, float lightFactor, float seconds,
							float walkPhase, float movingAmount, float lookYaw, float lookPitch) {
		kind = ID[Math.max(0, Math.min(ID.length - 1, kind))];
		PetCtx c = new PetCtx();
		c.ms = matrices;
		c.solid = solid;
		c.glass = glass;
		c.light = lightFactor;
		c.time = seconds;
		c.walk = walkPhase;
		c.moving = movingAmount;
		c.lookYaw = lookYaw;
		c.lookPitch = lookPitch;

		switch (kind) {
			case 0, 1, 2, 6, 7, 8, 9, 10, 11, 12 -> {
				int breed = kind <= 2 ? kind : kind - 3;
				Quadruped q = ANIMALS[kind];
				if (q == null) {
					q = PetFactory.breed(breed);
					ANIMALS[kind] = q;
				}
				float sc = SCALE[breed];
				matrices.push();
				matrices.scale(sc, sc, sc);
				drawModel(q, matrices, solid, lightFactor, seconds, walkPhase, movingAmount);
				matrices.pop();
			}
			case 3 -> PetDragon.draw(c);
			case 4 -> {
				ms = matrices;
				d = solid;
				light = lightFactor;
				time = seconds;
				walk = walkPhase;
				moving = movingAmount;
				ghost(GHOST);
			}
			case 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23 -> {
				int ci = kind - 13;
				ChibiPetFx cp = CHIBI[ci];
				if (cp == null) {
					cp = ChibiFactory.create(ci);
					CHIBI[ci] = cp;
				}
				float sc = CHIBI_SCALE[ci];
				matrices.push();
				matrices.scale(sc, sc, sc);
				drawModel(cp, matrices, solid, lightFactor, seconds, walkPhase, movingAmount);
				matrices.pop();
			}
			default -> PetWisp.draw(c);
		}
	}

	/** Рисует модель из частей: матрица каждой части умножается на текущую. */
	private static void drawModel(PetModel model, MatrixStack matrices, Draw3d solid, float lightFactor, float seconds,
								  float walkPhase, float movingAmount) {
		model.animate(seconds, walkPhase, movingAmount);
		model.updateWorld();
		Matrix4f base = matrices.peek().getPositionMatrix();
		Matrix4f m = TMP_M;
		Matrix4f part = TMP_P;
		for (PetModel.Part p : model.parts) {
			if (p.mesh == null || !p.visible) continue;
			part.set(p.world);
			m.set(base).mul(part);
			Voxels.Mesh mesh = p.mesh;
			float[] pos = mesh.pos;
			float[] rgb = mesh.rgb;
			for (int i = 0; i < mesh.vertices; i++) {
				int o = i * 3;
				solid.vertex(m, pos[o], pos[o + 1], pos[o + 2],
						Math.min(1.0f, rgb[o] * lightFactor), Math.min(1.0f, rgb[o + 1] * lightFactor),
						Math.min(1.0f, rgb[o + 2] * lightFactor), 1.0f);
			}
		}
	}

	private static final Matrix4f TMP_M = new Matrix4f();
	private static final Matrix4f TMP_P = new Matrix4f();
	private static float walk;
	private static float moving;

	private static void cube(float cx, float cy, float cz, float sx, float sy, float sz, int rgb) {
		cubeA(cx, cy, cz, sx, sy, sz, rgb, 1.0f);
	}

	private static void cubeA(float cx, float cy, float cz, float sx, float sy, float sz, int rgb, float a) {
		float l = unlit ? 1.0f : light;
		d.cube(ms.peek().getPositionMatrix(), cx - sx / 2, cy - sy / 2, cz - sz / 2, cx + sx / 2, cy + sy / 2, cz + sz / 2,
				ColorUtil.red(rgb) * l, ColorUtil.green(rgb) * l, ColorUtil.blue(rgb) * l, a);
	}

	private static void push() {
		ms.push();
	}

	private static void pop() {
		ms.pop();
	}

	private static void tr(float x, float y, float z) {
		ms.translate(x, y, z);
	}

	private static void rx(float deg) {
		ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(deg));
	}

	private static void ry(float deg) {
		ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(deg));
	}

	private static void rz(float deg) {
		ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(deg));
	}

	private static float sin(float v) {
		return (float) Math.sin(v);
	}


	// ---------- Призрак ----------

	private static void ghost(int[] p) {
		unlit = true;
		float hover = 0.5f + sin(time * 2.0f) * 0.07f;
		push();
		tr(0, hover, 0);
		rx(moving * 8.0f);
		float a = 0.92f;

		cubeA(0, 0.06f, 0, 0.34f, 0.34f, 0.3f, p[MAIN], a);
		cubeA(0, 0.25f, 0, 0.26f, 0.06f, 0.24f, p[MAIN], a);
		cubeA(0, 0.3f, 0, 0.16f, 0.04f, 0.14f, p[LIGHT], a);
		// Рваный край снизу
		for (int ix = -1; ix <= 1; ix++) {
			for (int iz = -1; iz <= 1; iz++) {
				float wave = sin(time * 3.0f + ix * 1.3f + iz * 0.9f) * 0.03f;
				cubeA(ix * 0.11f, -0.16f + wave, iz * 0.1f, 0.1f, 0.12f, 0.1f, p[LIGHT], a);
			}
		}
		// Ручки
		for (int s = -1; s <= 1; s += 2) {
			push();
			tr(s * 0.19f, 0.06f, 0.02f);
			rz(s * (-25.0f + sin(time * 5.0f + s) * 8.0f * moving));
			cubeA(s * 0.04f, -0.04f, 0, 0.07f, 0.12f, 0.07f, p[MAIN], a);
			pop();
		}
		// Лицо
		for (int s = -1; s <= 1; s += 2) {
			cube(s * 0.07f, 0.1f, 0.152f, 0.07f, 0.1f, 0.012f, p[EYE]);
			cube(s * 0.07f + 0.012f, 0.125f, 0.16f, 0.025f, 0.03f, 0.012f, 0xFFFFFF);
			cube(s * 0.13f, 0.03f, 0.152f, 0.06f, 0.035f, 0.012f, p[NOSE]);
		}
		cube(0, 0.02f, 0.152f, 0.06f, 0.04f, 0.012f, p[DARK]);
		pop();
		unlit = false;
	}
}
