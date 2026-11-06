package com.flyvisual.module.impl;

import com.flyvisual.config.Theme;
import com.flyvisual.gui.HudEditorScreen;
import com.flyvisual.gui.UiShapes;
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

/**
 * Здоровье, броня и расстояние до существа, на которое ты смотришь.
 * Скруглённая панель, плавное появление и исчезновение, полоска здоровья плавно едет,
 * а за ней тянется светлый "след" потерянного здоровья.
 */
public class TargetHudModule extends HudModule {
	private static final int W = 140;
	private static final int H = 38;

	private final DoubleSetting holdTime = addSetting("Hold time (s)", 3.0, 0.0, 10.0, 0.5);
	private final DoubleSetting playersOnly = addToggle("Players only", false);
	private final DoubleSetting showArmor = addToggle("Show armor", true);

	private LivingEntity target;
	private long lastSeen;
	private long lastNanos = System.nanoTime();

	// Последние показанные данные (чтобы панель красиво гасла после потери цели)
	private String shownName = "";
	private float shownHp;
	private float shownDist;
	private int shownArmor;
	private float shownFrac = 1.0f;
	private float trailFrac = 1.0f;
	private float alpha;
	private LivingEntity lastTarget;

	public TargetHudModule() {
		super("TargetHUD", "Health and info of the entity you look at", 200, 120);
		accentBar = addToggle("Accent bar", true);
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = mc();
		if (mc.player == null) return;
		TextRenderer tr = mc.textRenderer;

		long nowNanos = System.nanoTime();
		float dt = Math.min((nowNanos - lastNanos) / 1.0e9f, 0.05f);
		lastNanos = nowNanos;

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
		boolean visible = target != null || preview;

		if (target != null) {
			float max = Math.max(1.0f, target.getMaxHealth());
			shownName = target.getName().getString();
			shownHp = target.getHealth();
			shownDist = mc.player.distanceTo(target);
			shownArmor = target.getArmor();
			float frac = UiShapes.clamp01(shownHp / max);
			if (target != lastTarget || alpha < 0.05f) {
				shownFrac = frac;
				trailFrac = frac;
			}
			lastTarget = target;
			// Полоска плавно догоняет значение, след отстаёт и тает медленнее
			shownFrac += (frac - shownFrac) * (1.0f - (float) Math.exp(-10.0f * dt));
			if (trailFrac < shownFrac) {
				trailFrac = shownFrac;
			} else {
				trailFrac += (shownFrac - trailFrac) * (1.0f - (float) Math.exp(-2.5f * dt));
			}
		} else if (preview) {
			shownName = "Цель (пример)";
			shownHp = 14.0f;
			shownDist = 3.0f;
			shownArmor = 12;
			shownFrac = 0.7f;
			trailFrac = 0.85f;
			lastTarget = null;
		}

		alpha += ((visible ? 1.0f : 0.0f) - alpha) * (1.0f - (float) Math.exp(-12.0f * dt));
		if (alpha < 0.02f) {
			alpha = 0.0f;
			return;
		}

		// Лёгкий подъём при появлении
		int oy = Math.round((1.0f - alpha) * 5.0f);
		int px = x;
		int py = y + oy;

		int accent = Theme.accent();
		UiShapes.roundRect(ctx, px, py, W, H, 7, UiShapes.mulA(bg() | 0x000000, alpha));
		if (showAccentBar()) {
			UiShapes.roundRect(ctx, px + 4, py + 6, 2, H - 12, 1, UiShapes.mulA(accent, alpha));
		}

		int textX = px + (showAccentBar() ? 11 : 8);
		int nameColor = UiShapes.mulA(Theme.TEXT, alpha);
		if (((nameColor >>> 24) & 0xFF) >= 12) {
			ctx.drawText(tr, shownName, textX, py + 5, nameColor, true);
		}

		// Полоска здоровья со скруглением: след, затем текущее здоровье
		int bx = textX;
		int by = py + 17;
		int bw = px + W - 8 - bx;
		int bh = 6;
		UiShapes.roundRect(ctx, bx, by, bw, bh, 3, UiShapes.mulA(0xFF2A2A2E, alpha));
		int trailW = Math.round(bw * trailFrac);
		if (trailW > 1) {
			UiShapes.roundRect(ctx, bx, by, trailW, bh, 3, UiShapes.mulA(0xFFFFFFFF, 0.35f * alpha));
		}
		int fillW = Math.round(bw * shownFrac);
		if (fillW > 1) {
			int barColor = 0xFF000000 | ColorUtil.hsv(shownFrac * 120.0f, 0.8f, 0.95f);
			UiShapes.roundRect(ctx, bx, by, fillW, bh, 3, UiShapes.mulA(barColor, alpha));
		}

		StringBuilder info = new StringBuilder(String.format(Locale.ROOT, "%.1f ХП  %.1f м", shownHp, shownDist));
		if (showArmor.getBool()) {
			info.append("  Бр ").append(shownArmor);
		}
		int infoColor = UiShapes.mulA(Theme.TEXT_DIM, alpha);
		if (((infoColor >>> 24) & 0xFF) >= 12) {
			ctx.drawText(tr, info.toString(), textX, py + 27, infoColor, true);
		}
	}
}
