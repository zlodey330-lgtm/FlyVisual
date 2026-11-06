package com.flyvisual.gui;

import net.minecraft.client.gui.DrawContext;

/** Небольшие помощники для рисования: закруглённые прямоугольники и работа с цветом. */
public final class UiShapes {
	private UiShapes() {
	}

	public static float clamp01(float v) {
		return Math.max(0.0f, Math.min(1.0f, v));
	}

	/** Умножает прозрачность цвета ARGB на m (0..1). */
	public static int mulA(int argb, float m) {
		int a = Math.round(((argb >>> 24) & 0xFF) * clamp01(m));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	public static int lerp(int c1, int c2, float t) {
		t = clamp01(t);
		int a = Math.round(((c1 >>> 24) & 0xFF) * (1 - t) + ((c2 >>> 24) & 0xFF) * t);
		int r = Math.round(((c1 >> 16) & 0xFF) * (1 - t) + ((c2 >> 16) & 0xFF) * t);
		int g = Math.round(((c1 >> 8) & 0xFF) * (1 - t) + ((c2 >> 8) & 0xFF) * t);
		int b = Math.round((c1 & 0xFF) * (1 - t) + (c2 & 0xFF) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** Сдвиг края от угла для строки row (0 = самая верхняя строка угла) у скругления радиуса r. */
	private static int cornerInset(int row, int r) {
		double dy = r - (row + 0.5);
		double dx = Math.sqrt(Math.max(0.0, (double) r * r - dy * dy));
		return (int) Math.round(r - dx);
	}

	/**
	 * Закруглённый прямоугольник. Середина рисуется одним куском, а в углах строки с одинаковым
	 * сдвигом склеиваются, поэтому вместо сотен вызовов fill получается около десятка (меню не тормозит).
	 */
	public static void roundRect(DrawContext ctx, int x, int y, int w, int h, int radius, int argb) {
		if (((argb >>> 24) & 0xFF) == 0 || w <= 0 || h <= 0) return;
		int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
		if (r == 0) {
			ctx.fill(x, y, x + w, y + h, argb);
			return;
		}

		if (h - 2 * r > 0) {
			ctx.fill(x, y + r, x + w, y + h - r, argb);
		}

		int runStart = 0;
		int runInset = cornerInset(0, r);
		for (int row = 1; row <= r; row++) {
			int inset = row < r ? cornerInset(row, r) : -1;
			if (inset != runInset) {
				ctx.fill(x + runInset, y + runStart, x + w - runInset, y + row, argb);
				ctx.fill(x + runInset, y + h - row, x + w - runInset, y + h - runStart, argb);
				runStart = row;
				runInset = inset;
			}
		}
	}
}
