package com.flyvisual.util;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Жирный ванильный шрифт для цифр и подписей HUD: чётче читается. */
public final class Bold {
	private Bold() {
	}

	public static Text of(String s) {
		return Text.literal(s).formatted(Formatting.BOLD);
	}

	public static int width(TextRenderer tr, String s) {
		return tr.getWidth(of(s));
	}

	public static void draw(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color) {
		ctx.drawText(tr, of(s), x, y, color, true);
	}
}
