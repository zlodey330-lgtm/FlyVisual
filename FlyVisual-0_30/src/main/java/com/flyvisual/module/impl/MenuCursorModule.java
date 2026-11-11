package com.flyvisual.module.impl;

import com.flyvisual.config.Theme;
import com.flyvisual.hud.Notifications;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.ColorUtil;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Свой курсор мыши в меню: форма, размер, свой цвет и след за курсором.
 * Рисуется поверх любого экрана. Видно только тебе.
 */
public class MenuCursorModule extends Module {
	private static final String[] STYLES = {"Стрелка", "Круг", "Точка", "Прицел", "Системный"};
	private static final String[] TRAILS = {"Нет", "Хвост", "Радужный хвост", "Искры"};

	private static final String[] ARROW = {
			"X",
			"XX",
			"XXX",
			"XXXX",
			"XXXXX",
			"XXXXXX",
			"XXXXXXX",
			"XXXXXXXX",
			"XXXXXXXXX",
			"XXXXXXXXXX",
			"XXXXXX",
			"XX XXX",
			"X  XXX",
			"    XXX",
			"    XXX"
	};
	private static final int AW = 10;
	private static final int AH = ARROW.length;
	/** Маска стрелки с рамкой: 0 пусто, 1 заливка, 2 контур. */
	private static final byte[][] MASK = buildMask();

	private final DoubleSetting style = addChoice("Cursor style", 0, STYLES);
	private final DoubleSetting size = addSetting("Cursor size", 18.0, 8.0, 40.0, 1.0);
	private final DoubleSetting useAccent = addToggle("Cursor accent color", true);
	private final DoubleSetting hue = addSetting("Cursor color (hue)", 200.0, 0.0, 355.0, 5.0);
	private final DoubleSetting rainbow = addToggle("Cursor rainbow", false);
	private final DoubleSetting trail = addChoice("Cursor trail", 1, TRAILS);
	private final DoubleSetting trailLen = addSetting("Trail length", 18.0, 4.0, 40.0, 1.0);
	private final DoubleSetting trailSize = addSetting("Trail size", 6.0, 2.0, 12.0, 1.0);
	private final DoubleSetting ripple = addToggle("Cursor click ripple", true);

	/** Точки следа: x, y, возраст в секундах. */
	private final List<float[]> points = new ArrayList<>();
	/** Искры: x, y, vx, vy, возраст, жизнь, оттенок. */
	private final List<float[]> sparks = new ArrayList<>();
	/** Волны от кликов: x, y, возраст. */
	private final List<float[]> waves = new ArrayList<>();
	private final Random random = new Random();

	private long lastNs;
	private float lastX = Float.NaN;
	private float lastY;
	private boolean wasDown;
	private Screen hiddenFor;
	private boolean failed;

	public MenuCursorModule() {
		super("Menu Cursor", "Custom mouse cursor in menus with color and trail");
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) ->
				ScreenEvents.afterRender(screen).register(this::onRender));
	}

	@Override
	public void onEnable() {
		failed = false;
		reset();
	}

	@Override
	public void onDisable() {
		restoreSystemCursor();
		reset();
	}

	private void reset() {
		points.clear();
		sparks.clear();
		waves.clear();
		lastX = Float.NaN;
		lastNs = 0;
		wasDown = false;
	}

	private void restoreSystemCursor() {
		try {
			MinecraftClient mc = mc();
			if (hiddenFor != null && mc.currentScreen != null) {
				GLFW.glfwSetInputMode(mc.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
			}
		} catch (Throwable ignored) {
		}
		hiddenFor = null;
	}

	@Override
	public void onTick(MinecraftClient client) {
		if (client.currentScreen == null) {
			hiddenFor = null;
			if (!points.isEmpty() || !sparks.isEmpty()) reset();
			return;
		}
		// стиль «Системный» возвращает обычный курсор
		if (style.getInt() == 4 && hiddenFor != null) {
			restoreSystemCursor();
		}
	}

	private int color(float t) {
		if (rainbow.getBool()) {
			return 0xFF000000 | ColorUtil.hsv((System.nanoTime() / 8_000_000L) % 360L + t, 0.8f, 1.0f);
		}
		if (useAccent.getBool()) {
			return 0xFF000000 | (Theme.accent() & 0xFFFFFF);
		}
		return 0xFF000000 | ColorUtil.hsv(hue.getFloat(), 0.85f, 1.0f);
	}

	private static int alpha(int argb, float a) {
		int al = Math.max(0, Math.min(255, (int) (a * 255.0f)));
		return (al << 24) | (argb & 0xFFFFFF);
	}

	private void onRender(Screen screen, DrawContext ctx, int mx, int my, float delta) {
		if (!isEnabled() || failed) return;
		try {
			draw(screen, ctx);
		} catch (Throwable t) {
			failed = true;
			restoreSystemCursor();
			Notifications.push("Курсор: ошибка, модуль выключен", false);
		}
	}

	private void draw(Screen screen, DrawContext ctx) {
		MinecraftClient mc = mc();
		int styleIdx = style.getInt();
		if (styleIdx != 4 && hiddenFor != screen) {
			GLFW.glfwSetInputMode(mc.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
			hiddenFor = screen;
		}

		double sw = mc.getWindow().getScaledWidth();
		double sh = mc.getWindow().getScaledHeight();
		float x = (float) (mc.mouse.getX() * sw / mc.getWindow().getWidth());
		float y = (float) (mc.mouse.getY() * sh / mc.getWindow().getHeight());

		long now = System.nanoTime();
		float dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1.0e9f);
		lastNs = now;

		int trailMode = trail.getInt();
		float ts = trailSize.getFloat();
		float life = Math.max(0.1f, trailLen.getFloat() * 0.02f);

		// новые точки следа вдоль пути курсора
		if (!Float.isNaN(lastX)) {
			float dx = x - lastX;
			float dy = y - lastY;
			float dist = (float) Math.sqrt(dx * dx + dy * dy);
			if (trailMode == 1 || trailMode == 2) {
				float stepLen = Math.max(1.5f, ts * 0.5f);
				int n = Math.min(40, (int) (dist / stepLen));
				for (int i = 1; i <= n; i++) {
					float f = i / (float) n;
					points.add(new float[]{lastX + dx * f, lastY + dy * f, 0.0f});
				}
			} else if (trailMode == 3 && dist > 1.0f) {
				int n = Math.min(6, 1 + (int) (dist / 6.0f));
				for (int i = 0; i < n; i++) {
					float f = random.nextFloat();
					float ang = random.nextFloat() * 6.2831855f;
					float sp = 10.0f + random.nextFloat() * 30.0f;
					sparks.add(new float[]{lastX + dx * f, lastY + dy * f,
							(float) Math.cos(ang) * sp, (float) Math.sin(ang) * sp - 8.0f,
							0.0f, life * (0.6f + random.nextFloat() * 0.8f),
							random.nextFloat() * 60.0f});
				}
			}
		}
		lastX = x;
		lastY = y;
		if (sparks.size() > 300) sparks.subList(0, sparks.size() - 300).clear();
		if (points.size() > 400) points.subList(0, points.size() - 400).clear();

		// клик: волна
		boolean down = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		if (down && !wasDown && ripple.getBool()) {
			waves.add(new float[]{x, y, 0.0f});
		}
		wasDown = down;

		// след
		for (int i = points.size() - 1; i >= 0; i--) {
			float[] p = points.get(i);
			p[2] += dt;
			if (p[2] >= life) {
				points.remove(i);
				continue;
			}
			float k = 1.0f - p[2] / life;
			int col = trailMode == 2 ? 0xFF000000 | ColorUtil.hsv(p[2] / life * 300.0f + (System.nanoTime() / 8_000_000L) % 360L, 0.8f, 1.0f)
					: color(p[2] * 100.0f);
			disc(ctx, p[0], p[1], Math.max(0.6f, ts * 0.5f * k), alpha(col, 0.55f * k));
		}
		for (int i = sparks.size() - 1; i >= 0; i--) {
			float[] s = sparks.get(i);
			s[4] += dt;
			if (s[4] >= s[5]) {
				sparks.remove(i);
				continue;
			}
			s[0] += s[2] * dt;
			s[1] += s[3] * dt;
			s[3] += 40.0f * dt;
			float k = 1.0f - s[4] / s[5];
			int col = rainbow.getBool() ? 0xFF000000 | ColorUtil.hsv(s[6] * 6.0f, 0.8f, 1.0f) : color(s[6]);
			disc(ctx, s[0], s[1], Math.max(0.5f, ts * 0.35f * k), alpha(col, 0.9f * k));
		}
		for (int i = waves.size() - 1; i >= 0; i--) {
			float[] w = waves.get(i);
			w[2] += dt;
			if (w[2] >= 0.45f) {
				waves.remove(i);
				continue;
			}
			float k = w[2] / 0.45f;
			ring(ctx, w[0], w[1], 3.0f + k * size.getFloat() * 1.1f, 1.5f, alpha(color(0.0f), 1.0f - k));
		}

		drawCursor(ctx, x, y, styleIdx);
	}

	private void drawCursor(DrawContext ctx, float x, float y, int styleIdx) {
		if (styleIdx == 4) return;
		float sz = size.getFloat();
		int col = color(0.0f);
		int outline = 0xE0101018;
		if (styleIdx == 0) {
			float sc = sz / (float) AH;
			ctx.getMatrices().push();
			ctx.getMatrices().translate(x - sc, y - sc, 0.0f);
			ctx.getMatrices().scale(sc, sc, 1.0f);
			for (int row = 0; row < MASK.length; row++) {
				int c = 0;
				while (c < MASK[row].length) {
					byte v = MASK[row][c];
					if (v == 0) {
						c++;
						continue;
					}
					int start = c;
					while (c < MASK[row].length && MASK[row][c] == v) c++;
					ctx.fill(start, row, c, row + 1, v == 1 ? col : outline);
				}
			}
			ctx.getMatrices().pop();
		} else if (styleIdx == 1) {
			float r = sz * 0.5f;
			ring(ctx, x, y, r + 1.0f, 1.0f, outline);
			ring(ctx, x, y, r, 2.0f, col);
			disc(ctx, x, y, Math.max(1.5f, sz * 0.12f), col);
		} else if (styleIdx == 2) {
			float r = Math.max(2.0f, sz * 0.3f);
			disc(ctx, x, y, r + 1.5f, outline);
			disc(ctx, x, y, r, col);
		} else {
			float arm = sz * 0.5f;
			float gap = Math.max(2.0f, sz * 0.15f);
			int ix = Math.round(x);
			int iy = Math.round(y);
			int a = Math.round(arm);
			int g = Math.round(gap);
			ctx.fill(ix - a - 1, iy - 2, ix - g + 1, iy + 2, outline);
			ctx.fill(ix + g - 1, iy - 2, ix + a + 1, iy + 2, outline);
			ctx.fill(ix - 2, iy - a - 1, ix + 2, iy - g + 1, outline);
			ctx.fill(ix - 2, iy + g - 1, ix + 2, iy + a + 1, outline);
			ctx.fill(ix - a, iy - 1, ix - g, iy + 1, col);
			ctx.fill(ix + g, iy - 1, ix + a, iy + 1, col);
			ctx.fill(ix - 1, iy - a, ix + 1, iy - g, col);
			ctx.fill(ix - 1, iy + g, ix + 1, iy + a, col);
			ctx.fill(ix - 1, iy - 1, ix + 1, iy + 1, col);
		}
	}

	/** Закрашенный круг горизонтальными полосками. */
	private static void disc(DrawContext ctx, float cx, float cy, float r, int argb) {
		if (r <= 0.5f) {
			ctx.fill(Math.round(cx), Math.round(cy), Math.round(cx) + 1, Math.round(cy) + 1, argb);
			return;
		}
		int ir = (int) Math.ceil(r);
		int icx = Math.round(cx);
		int icy = Math.round(cy);
		for (int dy = -ir; dy <= ir; dy++) {
			float h2 = r * r - dy * dy;
			if (h2 < 0.0f) continue;
			int half = Math.round((float) Math.sqrt(h2));
			ctx.fill(icx - half, icy + dy, icx + half + 1, icy + dy + 1, argb);
		}
	}

	/** Кольцо толщиной thick. */
	private static void ring(DrawContext ctx, float cx, float cy, float r, float thick, int argb) {
		int ir = (int) Math.ceil(r);
		int icx = Math.round(cx);
		int icy = Math.round(cy);
		float inner = Math.max(0.0f, r - thick);
		for (int dy = -ir; dy <= ir; dy++) {
			float o2 = r * r - dy * dy;
			if (o2 < 0.0f) continue;
			int outer = Math.round((float) Math.sqrt(o2));
			float i2 = inner * inner - dy * dy;
			if (i2 <= 0.0f) {
				ctx.fill(icx - outer, icy + dy, icx + outer + 1, icy + dy + 1, argb);
			} else {
				int in = Math.round((float) Math.sqrt(i2));
				ctx.fill(icx - outer, icy + dy, icx - in + 1, icy + dy + 1, argb);
				ctx.fill(icx + in, icy + dy, icx + outer + 1, icy + dy + 1, argb);
			}
		}
	}

	private static byte[][] buildMask() {
		int w = AW + 2;
		int h = AH + 2;
		byte[][] m = new byte[h][w];
		for (int r = 0; r < AH; r++) {
			String row = ARROW[r];
			for (int c = 0; c < row.length(); c++) {
				if (row.charAt(c) == 'X') m[r + 1][c + 1] = 1;
			}
		}
		for (int r = 0; r < h; r++) {
			for (int c = 0; c < w; c++) {
				if (m[r][c] != 0) continue;
				boolean near = false;
				for (int dr = -1; dr <= 1 && !near; dr++) {
					for (int dc = -1; dc <= 1; dc++) {
						int rr = r + dr;
						int cc = c + dc;
						if (rr >= 0 && rr < h && cc >= 0 && cc < w && m[rr][cc] == 1) {
							near = true;
							break;
						}
					}
				}
				if (near) m[r][c] = 2;
			}
		}
		return m;
	}
}
