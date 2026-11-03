package com.flyvisual.module.impl;

import com.flyvisual.config.Theme;
import com.flyvisual.module.HudModule;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.GameOptions;

/** Показывает нажатые клавиши: W A S D, мышь и пробел. */
public class KeybindHudModule extends HudModule {
	private static final int KEY = 18;
	private static final int GAP = 2;

	public KeybindHudModule() {
		super("Keybinds", "WASD, mouse buttons and space", 4, 80);
	}

	@Override
	protected boolean enabledByDefault() {
		return false;
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		GameOptions o = mc().options;
		int step = KEY + GAP;
		int total = 3 * KEY + 2 * GAP;
		int half = (total - GAP) / 2;

		boxW = total;
		boxH = 4 * KEY + 3 * GAP;

		drawKey(ctx, "W", x + step, y, KEY, o.forwardKey.isPressed());
		drawKey(ctx, "A", x, y + step, KEY, o.leftKey.isPressed());
		drawKey(ctx, "S", x + step, y + step, KEY, o.backKey.isPressed());
		drawKey(ctx, "D", x + 2 * step, y + step, KEY, o.rightKey.isPressed());

		drawKey(ctx, "ЛКМ", x, y + 2 * step, half, o.attackKey.isPressed());
		drawKey(ctx, "ПКМ", x + half + GAP, y + 2 * step, half, o.useKey.isPressed());

		drawKey(ctx, "ПРОБЕЛ", x, y + 3 * step, total, o.jumpKey.isPressed());
	}

	private void drawKey(DrawContext ctx, String label, int kx, int ky, int kw, boolean down) {
		TextRenderer tr = mc().textRenderer;
		int color = down ? (0xC0000000 | (Theme.accent() & 0xFFFFFF)) : bg();
		ctx.fill(kx, ky, kx + kw, ky + KEY, color);
		ctx.drawText(tr, label, kx + (kw - tr.getWidth(label)) / 2, ky + (KEY - 8) / 2, Theme.TEXT, true);
	}
}
