package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.Locale;

/** Список активных эффектов зелий с оставшимся временем. */
public class PotionHudModule extends HudModule {
	private static final int GAP = 2;

	private final DoubleSetting showTimer;

	public PotionHudModule() {
		super("Potion Status", "Active potion effects", 4, 204);
		showTimer = addToggle("Show timer", true);
		accentBar = addToggle("Accent bar", true);
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = mc();
		if (mc.player == null) return;

		int row = 0;
		int maxW = 60;
		for (StatusEffectInstance effect : mc.player.getStatusEffects()) {
			String text = effect.getEffectType().getName().getString() + " " + (effect.getAmplifier() + 1);

			if (showTimer.getBool()) {
				int seconds = effect.getDuration() / 20;
				String time = effect.getDuration() < 0
						? "inf"
						: String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
				text += "  " + time;
			}

			int w = drawPanelAt(ctx, text, x, y + row * (PANEL_HEIGHT + GAP));
			maxW = Math.max(maxW, w);
			row++;
		}

		boxW = maxW;
		boxH = row == 0 ? PANEL_HEIGHT : row * (PANEL_HEIGHT + GAP) - GAP;
	}
}
