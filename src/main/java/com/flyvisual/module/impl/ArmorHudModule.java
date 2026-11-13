package com.flyvisual.module.impl;

import com.flyvisual.util.Bold;
import com.flyvisual.config.Theme;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/** Броня на тебе: иконки с процентом прочности. Цифры становятся оранжевыми при низкой прочности. */
public class ArmorHudModule extends HudModule {
	private static final int SLOT = 30;

	private final DoubleSetting showPercent;
	private final DoubleSetting warnBelow;

	public ArmorHudModule() {
		super("Armor HUD", "Your armor and its durability", 4, 166);
		showPercent = addToggle("Show durability %", true);
		warnBelow = addSetting("Warn below %", 20, 0, 50, 5);
	}

	@Override
	protected boolean enabledByDefault() {
		return false;
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = mc();
		if (mc.player == null) return;
		TextRenderer tr = mc.textRenderer;

		boxW = SLOT * 4;
		boxH = showPercent.getBool() ? 26 : 16;

		// Слоты брони: 3 = шлем, 0 = ботинки. Рисуем слева направо от шлема к ботинкам.
		for (int i = 0; i < 4; i++) {
			ItemStack stack = mc.player.getInventory().getArmorStack(3 - i);
			int cellX = x + i * SLOT;
			int ix = cellX + (SLOT - 16) / 2;
			if (stack.isEmpty()) continue;

			ctx.drawItem(stack, ix, y);

			if (showPercent.getBool() && stack.isDamageable()) {
				int percent = 100 - stack.getDamage() * 100 / stack.getMaxDamage();
				int color = percent <= warnBelow.get() ? 0xFFFFAA00 : Theme.TEXT;
				String text = percent + "%";
				Bold.draw(ctx, tr, text, cellX + (SLOT - Bold.width(tr, text)) / 2, y + 18, color);
			}
		}
	}
}
