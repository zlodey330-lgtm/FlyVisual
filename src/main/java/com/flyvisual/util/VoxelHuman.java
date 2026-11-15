package com.flyvisual.util;

import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

/**
 * Воксельный персонаж: тело игрока из кубиков размером с пиксель скина. Каждый кубик берёт цвет из текстуры
 * скина (64x64), поэтому лицо, одежда и шляпа видны так же, как на обычном скине, только объёмно.
 * Система координат: +Z вперёд, +Y вверх, +X влево от персонажа; корень на земле под центром тела.
 */
public final class VoxelHuman extends PetModel {
	private static final float R = 1.0f / 16.0f;

	/** Положение головы и ног, задаётся перед каждым кадром. */
	public float headYaw;
	public float headPitch;
	public float limbPos;
	public float limbSpeed;
	public float swing;
	public float sneak;

	private final Part body;
	private final Part head;
	private final Part rArm;
	private final Part lArm;
	private final Part rLeg;
	private final Part lLeg;
	private final int[] skin;

	private final Matrix4f tmpM = new Matrix4f();
	private final Matrix4f tmpP = new Matrix4f();

	/** @param skin пиксели скина 64x64 в формате ARGB */
	public VoxelHuman(int[] skin) {
		this.skin = skin;
		body = make("body", null, 16, 16, 16, 32, -4, 12, -2, 4, 24, 2, 0, 12, 0, 8, 12, 4);
		head = make("head", body, 0, 0, 32, 0, -4, 24, -4, 4, 32, 4, 0, 24, 0, 8, 8, 8);
		rArm = make("rarm", body, 40, 16, 40, 32, -8, 12, -2, -4, 24, 2, -6, 22, 0, 4, 12, 4);
		lArm = make("larm", body, 32, 48, 48, 48, 4, 12, -2, 8, 24, 2, 6, 22, 0, 4, 12, 4);
		rLeg = make("rleg", null, 0, 16, 0, 32, -4, 0, -2, 0, 12, 2, -2, 12, 0, 4, 12, 4);
		lLeg = make("lleg", null, 16, 48, 0, 48, 0, 0, -2, 4, 12, 2, 2, 12, 0, 4, 12, 4);
	}

	private int px(int x, int y) {
		return skin[Math.max(0, Math.min(63, y)) * 64 + Math.max(0, Math.min(63, x))];
	}

	/** Цвет кубика: берём пиксель той грани, к которой он ближе всего; слой одежды/шляпы поверх основного. */
	private int sample(int dx, int dy, int ox, int oy, int w, int h, int d, float fx, float fy, float fz) {
		float dTop = fy;
		float dBottom = h - fy;
		float dFront = d - fz;
		float dBack = fz;
		float dRight = fx;
		float dLeft = w - fx;
		float best = dTop;
		int face = 0;
		if (dFront < best) {
			best = dFront;
			face = 1;
		}
		if (dBack < best) {
			best = dBack;
			face = 2;
		}
		if (dRight < best) {
			best = dRight;
			face = 3;
		}
		if (dLeft < best) {
			best = dLeft;
			face = 4;
		}
		if (dBottom < best) {
			face = 5;
		}
		int tx;
		int ty;
		switch (face) {
			case 0 -> {
				tx = ox + d + (int) fx;
				ty = oy + (int) fz;
			}
			case 1 -> {
				tx = ox + d + (int) fx;
				ty = oy + d + (int) fy;
			}
			case 2 -> {
				tx = ox + 2 * d + w + (int) (w - fx);
				ty = oy + d + (int) fy;
			}
			case 3 -> {
				tx = ox + (int) fz;
				ty = oy + d + (int) fy;
			}
			case 4 -> {
				tx = ox + d + w + (int) (d - fz);
				ty = oy + d + (int) fy;
			}
			default -> {
				tx = ox + d + w + (int) fx;
				ty = oy + (int) (d - fz);
			}
		}
		// Тот же пиксель в слое поверх (смещение между слоями dx, dy)
		int over = px(tx + dx, ty + dy);
		if (((over >>> 24) & 0xFF) >= 128) return over & 0xFFFFFF;
		int base = px(tx, ty);
		return base & 0xFFFFFF;
	}

	private Part make(String name, Part parent, int ox, int oy, int overX, int overY, int x0, int y0, int z0, int x1, int y1, int z1,
					  float pivX, float pivY, float pivZ, int w, int h, int d) {
		Voxels v = new Voxels(R, x0 * R, y0 * R, z0 * R, x1 * R, y1 * R, z1 * R);
		final int dx = overX - ox;
		final int dy = overY - oy;
		v.box(x0 * R, y0 * R, z0 * R, x1 * R, y1 * R, z1 * R, (x, y, z) -> {
			float fx = x / R - x0;
			float fy = y1 - y / R;
			float fz = z / R - z0;
			return sample(dx, dy, ox, oy, w, h, d, fx, fy, fz);
		});
		return addBlocky(name, parent, pivX * R, pivY * R, pivZ * R, v);
	}

	private void pose() {
		for (Part p : parts) p.reset();
		float k = Math.max(0.0f, Math.min(1.0f, limbSpeed));
		float ph = limbPos * 0.6662f;
		float a = (float) Math.toDegrees(Math.cos(ph) * 1.3 * k);
		rArm.rx = a;
		lArm.rx = -a;
		rLeg.rx = -a;
		lLeg.rx = a;
		if (swing > 0.0f) {
			float s = (float) Math.sin(Math.sqrt(swing) * Math.PI);
			rArm.rx = a * (1.0f - s) - s * 85.0f;
		}
		head.ry = -Math.max(-75.0f, Math.min(75.0f, headYaw));
		head.rx = Math.max(-89.0f, Math.min(89.0f, headPitch));
		if (sneak > 0.0f) {
			body.rx = 26.0f * sneak;
			head.rx -= 10.0f * sneak;
		}
	}

	/** Рисует персонажа в начале координат текущей матрицы. */
	public void draw(MatrixStack matrices, Draw3d solid, float light) {
		pose();
		updateWorld();
		Matrix4f base = matrices.peek().getPositionMatrix();
		for (Part p : parts) {
			if (p.mesh == null || !p.visible) continue;
			tmpP.set(p.world);
			tmpM.set(base).mul(tmpP);
			Voxels.Mesh mesh = p.mesh;
			float[] pos = mesh.pos;
			float[] rgb = mesh.rgb;
			for (int i = 0; i < mesh.vertices; i++) {
				int o = i * 3;
				solid.vertex(tmpM, pos[o], pos[o + 1], pos[o + 2],
						Math.min(1.0f, rgb[o] * light), Math.min(1.0f, rgb[o + 1] * light),
						Math.min(1.0f, rgb[o + 2] * light), 1.0f);
			}
		}
	}
}
