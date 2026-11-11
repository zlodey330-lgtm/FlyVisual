package com.flyvisual.hud;

import com.flyvisual.config.Theme;
import com.flyvisual.gui.UiShapes;
import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.NotificationsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Всплывающие уведомления: «Модуль включён», «выключен» и предупреждения. Плавно выезжают из края экрана
 * и плавно уходят; угол, время, размер и цвета берутся из модуля «Уведомления».
 */
public final class Notifications {
	private static final long IN_MS = 280;
	private static final long OUT_MS = 320;
	private static final int MAX_VISIBLE = 5;
	private static final List<Entry> ENTRIES = new ArrayList<>();

	private static final class Entry {
		final String text;
		final int kind;
		final long created;
		float curY = Float.NaN;

		Entry(String text, int kind, long created) {
			this.text = text;
			this.kind = kind;
			this.created = created;
		}
	}

	private Notifications() {
	}

	/** Включено (on = true) или выключено (on = false). */
	public static void push(String text, boolean on) {
		add(text, on ? 1 : 0);
	}

	/** Предупреждение (например, броня почти сломана). */
	public static void pushWarning(String text) {
		add(text, 2);
	}

	private static void add(String text, int kind) {
		NotificationsModule module = ModuleManager.notifications();
		if (module != null) module.playSound(kind);
		ENTRIES.add(new Entry(text, kind, System.currentTimeMillis()));
		while (ENTRIES.size() > MAX_VISIBLE) {
			ENTRIES.remove(0);
		}
	}

	private static float easeOut(float t) {
		float k = 1.0f - Math.max(0.0f, Math.min(1.0f, t));
		return 1.0f - k * k * k;
	}

	public static void render(DrawContext ctx) {
		NotificationsModule module = ModuleManager.notifications();
		if (module != null && !module.isEnabled()) {
			ENTRIES.clear();
			return;
		}
		long now = System.currentTimeMillis();
		long life = module == null ? 2500 : module.lifetimeMs();
		ENTRIES.removeIf(e -> now - e.created > life + OUT_MS);
		if (ENTRIES.isEmpty()) return;

		MinecraftClient mc = MinecraftClient.getInstance();
		TextRenderer tr = mc.textRenderer;
		int sw = mc.getWindow().getScaledWidth();
		int sh = mc.getWindow().getScaledHeight();
		int corner = module == null ? 1 : module.corner();
		float s = module == null ? 1.0f : module.scale();
		boolean right = corner == 1 || corner == 2;
		boolean bottom = corner == 2 || corner == 3;
		float h = 16.0f * s;
		float gap = 4.0f * s;
		float margin = 6.0f;

		// Новое — у самого угла, старые отодвигаются (и плавно едут на своё место)
		int count = ENTRIES.size();
		for (int i = 0; i < count; i++) {
			Entry e = ENTRIES.get(count - 1 - i);
			float slot = i * (h + gap);
			float targetY = bottom ? sh - margin - h - slot : margin + slot;
			if (Float.isNaN(e.curY)) {
				e.curY = targetY;
			} else {
				e.curY += (targetY - e.curY) * 0.22f;
			}

			long age = now - e.created;
			float appear = easeOut(age / (float) IN_MS);
			float leave = age > life ? easeOut((age - life) / (float) OUT_MS) : 0.0f;
			float vis = appear * (1.0f - leave);
			if (vis <= 0.02f) continue;

			float w = (tr.getWidth(e.text) + 14) * s;
			// Из угла выезжает наружу экрана и обратно
			float shown = Math.min(appear, 1.0f - leave);
			float off = (1.0f - shown) * (w + margin + 4.0f);
			float x = right ? sw - w - margin + off : margin - off;

			int bar = module == null ? (e.kind == 1 ? Theme.accent() : 0xFF888888) : module.barColor(e.kind);
			float alpha = Math.min(1.0f, vis * 1.4f);

			MatrixStack ms = ctx.getMatrices();
			ms.push();
			ms.translate(x, e.curY, 0.0f);
			ms.scale(s, s, 1.0f);
			int iw = tr.getWidth(e.text) + 14;
			UiShapes.roundRect(ctx, 0, 0, iw, 16, 4, UiShapes.mulA(Theme.PANEL_BG, alpha));
			UiShapes.roundRect(ctx, 3, 3, 2, 10, 1, UiShapes.mulA(bar, alpha));
			if (alpha > 0.1f) {
				ctx.drawText(tr, e.text, 9, 4, UiShapes.mulA(Theme.TEXT, alpha), true);
			}
			ms.pop();
		}
	}
}
