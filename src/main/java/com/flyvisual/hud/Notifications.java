package com.flyvisual.hud;

import com.flyvisual.config.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Всплывающие уведомления (справа сверху), например "Fullbright enabled".
 */
public final class Notifications {
	private static final long LIFETIME_MS = 2500;
	private static final int MAX_VISIBLE = 5;
	private static final List<Entry> ENTRIES = new ArrayList<>();

	private record Entry(String text, boolean on, long created) {
	}

	private Notifications() {
	}

	public static void push(String text, boolean on) {
		ENTRIES.add(new Entry(text, on, System.currentTimeMillis()));
		while (ENTRIES.size() > MAX_VISIBLE) {
			ENTRIES.remove(0);
		}
	}

	public static void render(DrawContext ctx) {
		long now = System.currentTimeMillis();
		ENTRIES.removeIf(e -> now - e.created() > LIFETIME_MS);
		if (ENTRIES.isEmpty()) return;

		MinecraftClient mc = MinecraftClient.getInstance();
		TextRenderer tr = mc.textRenderer;
		int screenW = mc.getWindow().getScaledWidth();

		int y = 6;
		for (Entry e : ENTRIES) {
			int w = tr.getWidth(e.text()) + 14;
			int x = screenW - w - 6;
			// Цвет полоски: акцент при включении, серый при выключении
			int bar = e.on() ? Theme.accent() : 0xFF888888;
			ctx.fill(x, y, x + w, y + 16, Theme.PANEL_BG);
			ctx.fill(x, y, x + 2, y + 16, bar);
			ctx.drawText(tr, e.text(), x + 8, y + 4, Theme.TEXT, true);
			y += 20;
		}
	}
}
