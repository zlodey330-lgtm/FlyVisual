package com.flyvisual.util;

import net.minecraft.client.texture.NativeImage;

/** Простое смягчение («мыло») текстур для уровней оптимизации. Всё в try/catch у вызывающего. */
public final class ImageFx {
	private ImageFx() {
	}

	/**
	 * Размывает цвет по соседям 3x3. mix 0..1 — насколько заменить исходный цвет размытым.
	 * Прозрачность не меняется, прозрачные соседи в размытии не участвуют (нет тёмных краёв).
	 */
	public static void soften(NativeImage img, double mix) {
		int w = img.getWidth();
		int h = img.getHeight();
		if (w < 4 || h < 4 || mix <= 0.0) return;
		int[] src = new int[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				src[y * w + x] = img.getColor(x, y);
			}
		}
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int c = src[y * w + x];
				int a = (c >>> 24) & 255;
				if (a == 0) continue;
				long sr = 0, sg = 0, sb = 0, sw = 0;
				for (int dy = -1; dy <= 1; dy++) {
					int yy = y + dy;
					if (yy < 0 || yy >= h) continue;
					for (int dx = -1; dx <= 1; dx++) {
						int xx = x + dx;
						if (xx < 0 || xx >= w) continue;
						int n = src[yy * w + xx];
						int na = (n >>> 24) & 255;
						if (na == 0) continue;
						sr += (long) (n & 255) * na;
						sg += (long) ((n >> 8) & 255) * na;
						sb += (long) ((n >> 16) & 255) * na;
						sw += na;
					}
				}
				if (sw == 0) continue;
				int r = (int) Math.round((c & 255) * (1.0 - mix) + (sr / (double) sw) * mix);
				int g = (int) Math.round(((c >> 8) & 255) * (1.0 - mix) + (sg / (double) sw) * mix);
				int b = (int) Math.round(((c >> 16) & 255) * (1.0 - mix) + (sb / (double) sw) * mix);
				img.setColor(x, y, (a << 24) | (b << 16) | (g << 8) | r);
			}
		}
	}
}
