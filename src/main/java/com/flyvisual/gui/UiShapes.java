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

	/** Закруглённый прямоугольник, рисуется построчно. */
	public static void roundRect(DrawContext ctx, int x, int y, int w, int h, int radius, int argb) {
		if (((argb >>> 24) & 0xFF) == 0 || w <= 0 || h <= 0) return;
		int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));

		// Середина без скруглений рисуется одним прямоугольником (быстрее, меньше вызовов)
		int midTop = r;
		int midBottom = h - r;
		if (midBottom > midTop) {
			ctx.fill(x, y + midTop, x + w, y + midBottom, argb);
		}
		for (int row = 0; row < r; row++) {
			double dy = r - (row + 0.5);
			double dx = Math.sqrt(Math.max(0.0, (double) r * r - dy * dy));
			int inset = (int) Math.round(r - dx);
			ctx.fill(x + inset, y + row, x + w - inset, y + row + 1, argb);
			int rowB = h - 1 - row;
			if (rowB >= midTop && rowB != row) {
				ctx.fill(x + inset, y + rowB, x + w - inset, y + rowB + 1, argb);
			}
		}
	}
}
