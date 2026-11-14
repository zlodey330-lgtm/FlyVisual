package com.flyvisual.util;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Шрифт FlyVisual для цифр и подписей HUD: гладкий Inter вместо пиксельного ванильного.
 * Если шрифт не загрузился, игра сама подставит обычный.
 */
public final class Bold {
	private static final Style STYLE = Style.EMPTY.withFont(new Identifier("flyvisual", "ui"));

	private Bold() {
	}

	public static Text of(String s) {
		return Text.literal(s).setStyle(STYLE);
	}

	public static int width(TextRenderer tr, String s) {
		return tr.getWidth(of(s));
	}

	public static void draw(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color) {
		ctx.drawText(tr, of(s), x, y, color, true);
	}

	/** Обрезает строку с «…», если она шире max пикселей. */
	public static String fit(TextRenderer tr, String s, int max) {
		if (width(tr, s) <= max) return s;
		String dots = "…";
		int limit = Math.max(0, max - width(tr, dots));
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			sb.append(s.charAt(i));
			if (width(tr, sb.toString()) > limit) {
				sb.setLength(sb.length() - 1);
				break;
			}
		}
		return sb + dots;
	}
}
