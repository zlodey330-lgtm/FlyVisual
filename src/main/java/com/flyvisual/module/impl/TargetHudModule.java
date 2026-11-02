package com.flyvisual.module.impl;

import com.flyvisual.config.Theme;
import com.flyvisual.gui.HudEditorScreen;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.HudModule;
import com.flyvisual.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Locale;

/** Показывает здоровье, броню и расстояние до существа, на которое ты смотришь. */
public class TargetHudModule extends HudModule {
	private static final int W = 124;
	private static final int H = 38;

	private final DoubleSetting holdTime = addSetting("Hold time (s)", 3.0, 0.0, 10.0, 0.5);
	private final DoubleSetting playersOnly = addToggle("Players only", false);
	private final DoubleSetting showArmor = addToggle("Show armor", true);

	private LivingEntity target;
	private long lastSeen;

	public TargetHudModule() {
		super("TargetHUD", "Health and info of the entity you look at", 200, 120);
		accentBar = addToggle("Accent bar", true);
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = mc();
		if (mc.player == null) return;
		TextRenderer tr = mc.textRenderer;

		long now = System.currentTimeMillis();
		Entity looked = mc.targetedEntity;
		if (looked instanceof LivingEntity living && living != mc.player
				&& (!playersOnly.getBool() || living instanceof PlayerEntity)) {
			target = living;
			lastSeen = now;
		}
		if (target != null && (target.isRemoved() || now - lastSeen > holdTime.get() * 1000.0)) {
			target = null;
		}

		boxW = W;
		boxH = H;

		boolean preview = mc.currentScreen instanceof HudEditorScreen;
		if (target == null && !preview) return;

		String name = target != null ? target.getName().getString() : "Target preview";
		float hp = target != null ? target.getHealth() : 20.0f;
		float max = target != null ? Math.max(1.0f, target.getMaxHealth()) : 20.0f;
		float frac = Math.max(0.0f, Math.min(1.0f, hp / max));

		ctx.fill(x, y, x + W, y + H, bg());
		if (showAccentBar()) {
			ctx.fill(x, y, x + 2, y + H, Theme.accent());
		}
		ctx.drawText(tr, name, x + 8, y + 5, Theme.TEXT, true);

		// Полоска здоровья: от красного к зелёному
		int bx = x + 8;
		int by = y + 17;
		int bw = W - 16;
		int barColor = 0xFF000000 | ColorUtil.hsv(frac * 120.0f, 0.8f, 0.95f);
		ctx.fill(bx, by, bx + bw, by + 6, 0xFF2A2A2E);
		ctx.fill(bx, by, bx + Math.round(bw * frac), by + 6, barColor);

		float dist = target != null ? mc.player.distanceTo(target) : 3.0f;
		StringBuilder info = new StringBuilder(String.format(Locale.ROOT, "%.1f HP  %.1fm", hp, dist));
		if (showArmor.getBool()) {
			info.append("  AR ").append(target != null ? target.getArmor() : 20);
		}
		ctx.drawText(tr, info.toString(), x + 8, y + 27, Theme.TEXT_DIM, true);
	}
}
