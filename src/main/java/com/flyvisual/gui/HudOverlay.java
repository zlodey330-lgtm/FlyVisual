package com.flyvisual.gui;

import com.flyvisual.config.Theme;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.HudModule;
import com.flyvisual.module.Lang;
import com.flyvisual.module.Module;
import com.flyvisual.module.ModuleManager;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Перетаскивание HUD-модулей мышкой (в чате и в HUD-редакторе) и маленькое окошко настроек
 * по правому клику на модуль. Окошко плавно раскрывается.
 */
public final class HudOverlay {
	private static final int POP_W = 150;
	private static final int ROW_H = 14;
	private static final int HEAD_H = 16;

	private static HudModule dragging;
	private static int dragOx;
	private static int dragOy;

	private static HudModule popMod;
	private static int popX;
	private static int popY;
	private static long popOpened;
	private static int sliderRow = -1;

	private HudOverlay() {
	}

	/** Подключает мышь и рисование к экрану чата. */
	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof ChatScreen)) return;
			reset();
			ScreenEvents.afterRender(screen).register((s, ctx, mx, my, dt) -> render(ctx, mx, my, s.width, s.height, true));
			ScreenMouseEvents.allowMouseClick(screen).register((s, mx, my, b) -> !click(mx, my, b, s.width, s.height));
			ScreenMouseEvents.afterMouseRelease(screen).register((s, mx, my, b) -> release());
		});
	}

	public static void reset() {
		dragging = null;
		popMod = null;
		sliderRow = -1;
	}

	private static List<HudModule> huds() {
		List<HudModule> out = new ArrayList<>();
		for (Module m : ModuleManager.all()) {
			if (m.isEnabled() && m instanceof HudModule h) out.add(h);
		}
		return out;
	}

	private static List<DoubleSetting> rows(HudModule m) {
		List<DoubleSetting> out = new ArrayList<>();
		for (DoubleSetting s : m.getSettings()) {
			if (!s.isButton()) out.add(s);
		}
		return out;
	}

	private static int popH() {
		return HEAD_H + rows(popMod).size() * ROW_H + 4;
	}

	private static float anim() {
		float t = Math.min(1.0f, (System.nanoTime() - popOpened) / 1.8e8f);
		return 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
	}

	public static void render(DrawContext ctx, int mx, int my, int w, int h, boolean outlines) {
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		if (dragging != null) {
			int nx = Math.max(0, Math.min(mx - dragOx, w - dragging.getBoxW()));
			int ny = Math.max(0, Math.min(my - dragOy, h - dragging.getBoxH()));
			dragging.setPosition(nx, ny);
		}
		if (outlines) {
			for (HudModule hud : huds()) {
				boolean active = hud == dragging || (dragging == null && popMod == null && hud.contains(mx, my));
				int color = active ? Theme.accent() : 0x70FFFFFF;
				int x = hud.getX() - 1;
				int y = hud.getY() - 1;
				int bw = hud.getBoxW() + 2;
				int bh = hud.getBoxH() + 2;
				ctx.fill(x, y, x + bw, y + 1, color);
				ctx.fill(x, y + bh - 1, x + bw, y + bh, color);
				ctx.fill(x, y, x + 1, y + bh, color);
				ctx.fill(x + bw - 1, y, x + bw, y + bh, color);
			}
		}
		if (popMod == null) return;

		List<DoubleSetting> list = rows(popMod);
		float e = anim();
		int fullH = popH();
		int curH = Math.max(1, Math.round(fullH * e));
		int x = popX;
		int y = popY;
		int a = Math.round(255 * e);

		if (sliderRow >= 0 && sliderRow < list.size()) {
			DoubleSetting s = list.get(sliderRow);
			float f = Math.max(0.0f, Math.min(1.0f, (mx - (x + 6)) / (float) (POP_W - 12)));
			s.set(s.getMin() + (s.getMax() - s.getMin()) * f);
		}

		ctx.enableScissor(x - 1, y - 1, x + POP_W + 1, y + curH + 1);
		ctx.fill(x - 1, y - 1, x + POP_W + 1, y + fullH + 1, (a / 2 << 24) | 0x000000);
		ctx.fill(x, y, x + POP_W, y + fullH, (Math.min(a, 235) << 24) | 0x16161A);
		ctx.fill(x, y, x + POP_W, y + 2, (a << 24) | (Theme.accent() & 0xFFFFFF));
		ctx.drawText(tr, tr.trimToWidth(Lang.module(popMod.getName()), POP_W - 12), x + 6, y + 5, 0xFFFFFFFF, false);
		for (int i = 0; i < list.size(); i++) {
			DoubleSetting s = list.get(i);
			int ry = y + HEAD_H + i * ROW_H;
			boolean hov = mx >= x && mx < x + POP_W && my >= ry && my < ry + ROW_H;
			if (hov) ctx.fill(x, ry, x + POP_W, ry + ROW_H, 0x22FFFFFF);
			if (!s.isToggle() && !s.isChoice()) {
				float f = (float) ((s.get() - s.getMin()) / Math.max(1.0e-6, s.getMax() - s.getMin()));
				ctx.fill(x + 6, ry + ROW_H - 3, x + 6 + Math.round((POP_W - 12) * f), ry + ROW_H - 1, 0xFF000000 | (Theme.accent() & 0xFFFFFF));
				ctx.fill(x + 6 + Math.round((POP_W - 12) * f), ry + ROW_H - 3, x + POP_W - 6, ry + ROW_H - 1, 0x33FFFFFF);
			}
			String val = s.format();
			int vw = tr.getWidth(val);
			ctx.drawText(tr, tr.trimToWidth(Lang.setting(s.getName()), POP_W - vw - 20), x + 6, ry + 2, 0xFFDDDDDD, false);
			int vc = s.isToggle() ? (s.getBool() ? 0xFF000000 | (Theme.accent() & 0xFFFFFF) : 0xFF888888) : 0xFFFFFFFF;
			ctx.drawText(tr, val, x + POP_W - 6 - vw, ry + 2, vc, false);
		}
		ctx.disableScissor();
	}

	/** Возвращает true, если клик обработан (не отдавать его экрану). */
	public static boolean click(double mx, double my, int button, int w, int h) {
		if (popMod != null) {
			int x = popX;
			int y = popY;
			boolean inside = mx >= x && mx < x + POP_W && my >= y && my < y + popH();
			if (inside) {
				if (button == 0) {
					List<DoubleSetting> list = rows(popMod);
					int i = (int) ((my - y - HEAD_H) / ROW_H);
					if (my >= y + HEAD_H && i >= 0 && i < list.size()) {
						DoubleSetting s = list.get(i);
						if (s.isToggle()) {
							s.flip();
						} else if (s.isChoice()) {
							s.set(s.getInt() + 1 > s.getMax() ? s.getMin() : s.getInt() + 1);
						} else {
							sliderRow = i;
						}
					}
				}
				return true;
			}
			popMod = null;
			sliderRow = -1;
		}
		List<HudModule> list = huds();
		for (int i = list.size() - 1; i >= 0; i--) {
			HudModule hud = list.get(i);
			if (!hud.contains(mx, my)) continue;
			if (button == 0) {
				dragging = hud;
				dragOx = (int) mx - hud.getX();
				dragOy = (int) my - hud.getY();
				return true;
			}
			if (button == 1) {
				popMod = hud;
				popOpened = System.nanoTime();
				int ph = popH();
				popX = Math.max(2, Math.min((int) mx + 6, w - POP_W - 2));
				popY = Math.max(2, Math.min((int) my + 6, h - ph - 2));
				return true;
			}
		}
		return false;
	}

	public static void release() {
		if (dragging != null) {
			dragging.savePosition();
			dragging = null;
		}
		if (sliderRow >= 0) {
			sliderRow = -1;
			com.flyvisual.config.FlyConfig.save();
		}
	}
}
