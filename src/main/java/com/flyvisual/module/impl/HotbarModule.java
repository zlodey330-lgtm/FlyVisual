package com.flyvisual.module.impl;

import com.flyvisual.config.Theme;
import com.flyvisual.gui.UiShapes;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Свой хотбар: скруглённая прозрачная панель и плавный выбор ячейки. Заменяет ванильный хотбар
 * (ванильный прячется в InGameHudMixin). Видно только тебе.
 */
public class HotbarModule extends Module {
	private final DoubleSetting backOpacity = addSetting("Background opacity", 55.0, 0.0, 100.0, 5.0);
	private final DoubleSetting radius = addSetting("Corner radius", 4.0, 0.0, 8.0, 1.0);
	private final DoubleSetting smooth = addToggle("Smooth selector", true);
	private final DoubleSetting scale = addSetting("Hotbar scale", 100.0, 70.0, 130.0, 5.0);
	private final DoubleSetting offhand = addToggle("Show offhand", true);

	private float selPos = -1.0f;
	private long lastNanos;

	public HotbarModule() {
		super("Hotbar", "Custom hotbar with smooth selector");
	}

	/** Нужно ли прятать ванильный хотбар (не в режиме наблюдателя). */
	public boolean replacesVanilla() {
		MinecraftClient mc = mc();
		return isEnabled() && mc.player != null && !mc.player.isSpectator();
	}

	@Override
	public void renderOverlay(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = mc();
		PlayerEntity player = mc.player;
		if (player == null || player.isSpectator() || mc.options.hudHidden) return;

		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
		lastNanos = now;

		int selected = player.getInventory().selectedSlot;
		if (selPos < 0 || !smooth.getBool()) {
			selPos = selected;
		} else {
			selPos += (selected - selPos) * (1.0f - (float) Math.exp(-22.0f * dt));
			if (Math.abs(selected - selPos) < 0.002f) selPos = selected;
		}

		float s = scale.getFloat() / 100.0f;
		int sw = mc.getWindow().getScaledWidth();
		int sh = mc.getWindow().getScaledHeight();
		int pw = 9 * 20 + 4;
		int ph = 24;
		int px = -pw / 2;

		MatrixStack ms = ctx.getMatrices();
		ms.push();
		ms.translate(sw / 2.0f, sh - 2.0f, 0.0f);
		ms.scale(s, s, 1.0f);
		// Начало координат — нижний центр панели
		int top = -ph;
		int r = radius.getInt();
		int bg = UiShapes.mulA(0xFF101014, backOpacity.getFloat() / 100.0f);
		UiShapes.roundRect(ctx, px, top, pw, ph, r, bg);

		// Выбранная ячейка
		int slotX = px + 2 + Math.round(selPos * 20.0f);
		UiShapes.roundRect(ctx, slotX, top + 2, 20, 20, Math.max(0, r - 1), UiShapes.mulA(Theme.accent(), 0.85f));

		TextRenderer tr = mc.textRenderer;
		for (int i = 0; i < 9; i++) {
			ItemStack stack = player.getInventory().main.get(i);
			if (stack.isEmpty()) continue;
			int ix = px + 2 + i * 20 + 2;
			int iy = top + 4;
			ctx.drawItem(stack, ix, iy);
			ctx.drawItemInSlot(tr, stack, ix, iy);
		}

		// Левая рука
		if (offhand.getBool()) {
			ItemStack off = player.getOffHandStack();
			if (!off.isEmpty()) {
				int ox = px - 26;
				UiShapes.roundRect(ctx, ox, top, 22, ph, r, bg);
				ctx.drawItem(off, ox + 3, top + 4);
				ctx.drawItemInSlot(tr, off, ox + 3, top + 4);
			}
		}
		ms.pop();
	}
}
