package com.flyvisual.module.impl;

import com.flyvisual.util.Bold;
import com.flyvisual.config.Theme;
import com.flyvisual.gui.HudEditorScreen;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.StatusEffectSpriteManager;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.Locale;

/**
 * Компактный список эффектов: иконка эффекта, уровень и время.
 * Название можно включить в настройках.
 */
public class PotionHudModule extends HudModule {
	private static final int ROW_H = 16;
	private static final int GAP = 2;
	private static final int ICON = 12;

	private final DoubleSetting showTimer;
	private final DoubleSetting showNames;

	public PotionHudModule() {
		super("Potion Status", "Active potion effects", 4, 204);
		showTimer = addToggle("Show timer", true);
		showNames = addToggle("Show names", false);
		accentBar = addToggle("Accent bar", true);
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = mc();
		if (mc.player == null) return;
		TextRenderer tr = mc.textRenderer;
		StatusEffectSpriteManager sprites = mc.getStatusEffectSpriteManager();

		int row = 0;
		int maxW = 40;
		for (StatusEffectInstance effect : mc.player.getStatusEffects()) {
			StringBuilder text = new StringBuilder();
			if (showNames.getBool()) {
				text.append(effect.getEffectType().getName().getString());
			}
			if (effect.getAmplifier() > 0) {
				if (text.length() > 0) text.append(' ');
				text.append(effect.getAmplifier() + 1);
			}

			boolean infinite = effect.isInfinite();
			boolean lowTime = !infinite && effect.getDuration() < 200;
			if (showTimer.getBool()) {
				int seconds = effect.getDuration() / 20;
				String time = infinite ? "inf" : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
				if (text.length() > 0) text.append("  ");
				text.append(time);
			}

			int ry = y + row * (ROW_H + GAP);
			String label = text.toString();
			int textW = label.isEmpty() ? 0 : Bold.width(tr, label) + 4;
			int w = 4 + ICON + 4 + textW + 2;

			ctx.fill(x, ry, x + w, ry + ROW_H, bg());
			if (showAccentBar()) {
				ctx.fill(x, ry, x + 2, ry + ROW_H, Theme.accent());
			}

			Sprite sprite = sprites.getSprite(effect.getEffectType());
			ctx.drawSprite(x + 4, ry + (ROW_H - ICON) / 2, 0, ICON, ICON, sprite);

			if (!label.isEmpty()) {
				Bold.draw(ctx, tr, label, x + 4 + ICON + 4, ry + (ROW_H - 8) / 2,
						lowTime ? 0xFFFFAA00 : Theme.TEXT);
			}

			maxW = Math.max(maxW, w);
			row++;
		}

		// Образец для HUD-редактора, когда эффектов нет
		if (row == 0 && mc.currentScreen instanceof HudEditorScreen) {
			String sample = "Эффекты зелий";
			int w = Bold.width(tr, sample) + 12;
			ctx.fill(x, y, x + w, y + ROW_H, bg());
			if (showAccentBar()) {
				ctx.fill(x, y, x + 2, y + ROW_H, Theme.accent());
			}
			Bold.draw(ctx, tr, sample, x + 6, y + (ROW_H - 8) / 2, Theme.TEXT_DIM);
			maxW = w;
			row = 1;
		}

		boxW = maxW;
		boxH = row == 0 ? ROW_H : row * (ROW_H + GAP) - GAP;
	}
}
