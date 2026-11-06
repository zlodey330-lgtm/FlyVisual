package com.flyvisual.util;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/** Всё, что нужно модели питомца за один кадр: стек матриц, буферы рисования и состояние анимации. */
public final class PetCtx {
	public MatrixStack ms;
	/** Непрозрачные грани и прозрачные (стекло, свечение) рисуются в разных буферах. */
	public Draw3d solid;
	public Draw3d glass;
	public float light = 1.0f;
	public float time;
	public float walk;
	public float moving;
	/** Куда питомец смотрит относительно тела (градусы): на игрока. */
	public float lookYaw;
	public float lookPitch;

	public void push() {
		ms.push();
	}

	public void pop() {
		ms.pop();
	}

	public void tr(float x, float y, float z) {
		ms.translate(x, y, z);
	}

	public void rx(float deg) {
		ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(deg));
	}

	public void ry(float deg) {
		ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(deg));
	}

	public void rz(float deg) {
		ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(deg));
	}

	public void scale(float x, float y, float z) {
		ms.scale(x, y, z);
	}

	/** Маленький цветной куб (глаза, нос, усы, искры). */
	public void cube(float cx, float cy, float cz, float sx, float sy, float sz, int rgb) {
		cube(cx, cy, cz, sx, sy, sz, rgb, 1.0f, light);
	}

	public void cube(float cx, float cy, float cz, float sx, float sy, float sz, int rgb, float alpha, float lightMul) {
		Draw3d target = alpha < 0.999f ? glass : solid;
		target.cube(ms.peek().getPositionMatrix(), cx - sx / 2, cy - sy / 2, cz - sz / 2, cx + sx / 2, cy + sy / 2, cz + sz / 2,
				ColorUtil.red(rgb) * lightMul, ColorUtil.green(rgb) * lightMul, ColorUtil.blue(rgb) * lightMul, alpha);
	}

	public static float sin(float v) {
		return (float) Math.sin(v);
	}

	public static float clamp(float v, float lo, float hi) {
		return v < lo ? lo : (v > hi ? hi : v);
	}

	public static int mix(int a, int b, float t) {
		t = clamp(t, 0.0f, 1.0f);
		int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return (r << 16) | (g << 8) | bl;
	}

	public static int mul(int c, float m) {
		int r = Math.min(255, Math.max(0, Math.round(((c >> 16) & 0xFF) * m)));
		int g = Math.min(255, Math.max(0, Math.round(((c >> 8) & 0xFF) * m)));
		int b = Math.min(255, Math.max(0, Math.round((c & 0xFF) * m)));
		return (r << 16) | (g << 8) | b;
	}
}
