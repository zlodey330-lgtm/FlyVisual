package com.flyvisual.gui;

import com.flyvisual.module.impl.MenuStyleModule;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Фоны главного меню: звёзды, закат, минимал, фиолетовый луч и своя картинка (config/flyvisual/menu.png). */
public final class TitleStyles {
	private static final Identifier CUSTOM = new Identifier("flyvisual", "custom_menu");
	private static int customState; // 0 не пробовали, 1 загружена, -1 нет файла
	private static int cw;
	private static int ch;
	private static final long START = System.nanoTime();

	private TitleStyles() {
	}

	public static Path customFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("flyvisual").resolve("menu.png");
	}

	private static void loadCustom() {
		if (customState != 0) return;
		customState = -1;
		try {
			Path p = customFile();
			if (!Files.exists(p)) {
				Files.createDirectories(p.getParent());
				return;
			}
			try (InputStream in = Files.newInputStream(p)) {
				NativeImage img = NativeImage.read(in);
				cw = img.getWidth();
				ch = img.getHeight();
				MinecraftClient.getInstance().getTextureManager().registerTexture(CUSTOM, new NativeImageBackedTexture(img));
				customState = 1;
			}
		} catch (Exception e) {
			customState = -1;
		}
	}

	private static float rnd(int i, int salt) {
		int h = i * 374761393 + salt * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFF) / 65535.0f;
	}

	/** Фиолетовый фон: широкий луч света из левого верхнего угла, пылинки и мягкие огоньки внизу. */
	private static void drawBeam(DrawContext ctx, int w, int h, float t) {
		ctx.fillGradient(0, 0, w, h, 0xFF06050D, 0xFF150D2B);

		// Луч: полосы по строкам, каждая из нескольких слоёв, поэтому центр ярче краёв
		float sway = (float) Math.sin(t * 0.35f) * w * 0.012f;
		float x0 = w * 0.07f;
		for (int y = 0; y < h; y += 3) {
			float fy = y / (float) h;
			float fade = 1.0f - fy * 0.92f;
			float cx = x0 + sway * fy + y * 0.55f;
			float half = w * 0.010f + y * 0.20f;
			int a = Math.round(15 * fade);
			if (a <= 0) continue;
			for (int k = 0; k < 4; k++) {
				int hw = Math.round(half * (1.0f - k * 0.25f));
				if (hw <= 0) continue;
				ctx.fill(Math.round(cx - hw), y, Math.round(cx + hw), y + 3, (a << 24) | 0xA774FF);
			}
		}

		// Яркое пятно у источника света
		int sx = Math.round(w * 0.05f);
		int sy = Math.round(h * 0.01f);
		for (int k = 0; k < 6; k++) {
			int r = 14 + k * 14;
			UiShapes.roundRect(ctx, sx - r, sy - r, r * 2, r * 2, r, ((10 + (5 - k) * 3) << 24) | 0xE2B8FF);
		}

		// Пылинки: медленно плывут и мерцают
		for (int i = 0; i < 110; i++) {
			float sp = 3.0f + rnd(i, 3) * 9.0f;
			float x = (rnd(i, 1) * w + t * sp * 0.5f) % w;
			float y = rnd(i, 2) * h - t * sp * 0.35f;
			y = ((y % h) + h) % h;
			float tw = 0.5f + 0.5f * (float) Math.sin(t * (0.6f + rnd(i, 4) * 1.8f) + i * 1.7f);
			int a = (int) (40 + 190 * tw * (0.35f + 0.65f * rnd(i, 5)));
			int size = rnd(i, 6) > 0.93f ? 2 : 1;
			int col = rnd(i, 7) > 0.55f ? 0xFFFFFF : 0xC9B3FF;
			ctx.fill((int) x, (int) y, (int) x + size, (int) y + size, (a << 24) | col);
		}

		// Крупные мягкие огоньки в нижней части
		for (int i = 0; i < 16; i++) {
			float x = (rnd(i, 11) * w + t * (2 + rnd(i, 12) * 4)) % w;
			float y = h * (0.62f + 0.38f * rnd(i, 13)) - ((t * (3 + rnd(i, 14) * 5)) % (h * 0.4f));
			int r = 2 + (int) (rnd(i, 15) * 4);
			int a = 30 + (int) (60 * (0.5f + 0.5f * Math.sin(t * 0.8f + i)));
			UiShapes.roundRect(ctx, (int) x - r, (int) y - r, r * 2, r * 2, r, (a << 24) | 0x9B6BFF);
		}

		ctx.fillGradient(0, h * 3 / 4, w, h, 0x00000000, 0x88050310);
	}

	/** Рисует фон выбранного стиля. Возвращает false, если нужно оставить ванильное (или лес рисует LiveBackground). */
	public static boolean draw(DrawContext ctx, int w, int h, int mx, int my) {
		int style = MenuStyleModule.current();
		float t = (System.nanoTime() - START) / 1.0e9f;
		switch (style) {
			case 2 -> {
				ctx.fillGradient(0, 0, w, h, 0xFF05060F, 0xFF141A33);
				for (int i = 0; i < 140; i++) {
					int x = (int) (rnd(i, 1) * w);
					int y = (int) (rnd(i, 2) * h * 0.9f);
					float tw = 0.5f + 0.5f * (float) Math.sin(t * (0.8f + rnd(i, 3) * 2.0f) + i);
					int a = (int) (70 + 185 * tw);
					int s = rnd(i, 4) > 0.9f ? 2 : 1;
					ctx.fill(x, y, x + s, y + s, (a << 24) | 0xE8ECFF);
				}
				float p = (t * 0.25f) % 4.0f;
				if (p < 1.0f) {
					int sx = (int) (w * (0.15f + 0.6f * p));
					int sy = (int) (h * (0.05f + 0.35f * p));
					for (int k = 0; k < 14; k++) {
						ctx.fill(sx - k * 3, sy - k * 2, sx - k * 3 + 2, sy - k * 2 + 1, ((int) (220 * (1 - k / 14.0f)) << 24) | 0xFFFFFF);
					}
				}
				ctx.fillGradient(0, h - h / 5, w, h, 0x00000000, 0x66000000);
				return true;
			}
			case 3 -> {
				ctx.fillGradient(0, 0, w, h / 2, 0xFF2B1B4A, 0xFFE0607E);
				ctx.fillGradient(0, h / 2, w, h, 0xFFE0607E, 0xFFFFB36B);
				int cx = w / 2;
				int cy = h * 55 / 100;
				int r = Math.max(30, h / 7);
				for (int yy = -r; yy <= r; yy += 2) {
					int half = (int) Math.sqrt((double) r * r - (double) yy * yy);
					ctx.fill(cx - half, cy + yy, cx + half, cy + yy + 2, 0xFFFFE9A8);
				}
				for (int i = 0; i < 4; i++) {
					int y = h * 62 / 100 + i * h / 14;
					int off = (int) (Math.sin(t * 0.5f + i) * 12);
					ctx.fill(0, y, w, y + 1 + i, 0x33FFFFFF);
					ctx.fill(cx - r + off, y, cx + r + off, y + 1, 0x55FFF4CC);
				}
				ctx.fillGradient(0, h * 3 / 4, w, h, 0x00000000, 0xAA1A0F2E);
				return true;
			}
			case 4 -> {
				ctx.fillGradient(0, 0, w, h, 0xFF1B1B1F, 0xFF2C2C32);
				for (int i = 0; i < 30; i++) {
					float x = (rnd(i, 5) * w + t * (4 + rnd(i, 6) * 10)) % w;
					float y = (rnd(i, 7) * h + t * 3) % h;
					int a = (int) (20 + 40 * rnd(i, 8));
					ctx.fill((int) x, (int) y, (int) x + 2, (int) y + 2, (a << 24) | 0xFFFFFF);
				}
				return true;
			}
			case 5 -> {
				loadCustom();
				if (customState != 1) {
					ctx.fillGradient(0, 0, w, h, 0xFF1B1B1F, 0xFF2C2C32);
					return true;
				}
				float sa = w / (float) h;
				float ta = cw / (float) ch;
				int rw = ta > sa ? Math.round(ch * sa) : cw;
				int rh = ta > sa ? ch : Math.round(cw / sa);
				ctx.drawTexture(CUSTOM, 0, 0, w, h, (cw - rw) / 2.0f, (ch - rh) / 2.0f, rw, rh, cw, ch);
				ctx.fill(0, 0, w, h, 0x30000000);
				return true;
			}
			case 6 -> {
				drawBeam(ctx, w, h, t);
				return true;
			}
			default -> {
				return false;
			}
		}
	}
}
