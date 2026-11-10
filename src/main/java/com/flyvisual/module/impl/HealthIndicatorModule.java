package com.flyvisual.module.impl;

import com.flyvisual.util.Bold;
import com.flyvisual.config.Theme;
import com.flyvisual.gui.UiShapes;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Locale;

/**
 * Показатель здоровья: маленькая плашка с сердцем и числом ХП прямо под прицелом,
 * когда смотришь на существо. Плавно появляется и считает золотые сердца (поглощение).
 */
public class HealthIndicatorModule extends Module {
	private final DoubleSetting offset = addSetting("Offset", 14.0, 6.0, 60.0, 2.0);
	private final DoubleSetting scale = addSetting("Scale", 1.0, 0.6, 2.0, 0.1);
	private final DoubleSetting showMax = addToggle("Show max HP", false);
	private final DoubleSetting playersOnly = addToggle("Players only", false);

	private LivingEntity target;
	private float shownHp;
	private float shownAbsorb;
	private float shownMax = 20.0f;
	private float alpha;
	private long lastNanos = System.nanoTime();

	public HealthIndicatorModule() {
		super("Health Indicator", "HP of the target under the crosshair");
	}

	@Override
	public void renderOverlay(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = mc();
		if (mc.player == null) return;

		long now = System.nanoTime();
		float dt = Math.min((now - lastNanos) / 1.0e9f, 0.05f);
		lastNanos = now;

		Entity looked = mc.currentScreen == null ? mc.targetedEntity : null;
		boolean visible = false;
		if (looked instanceof LivingEntity living && living != mc.player
				&& (!playersOnly.getBool() || living instanceof PlayerEntity)
				&& !(living.isInvisible() && !hasArmor(living))) {
			if (target != living) {
				target = living;
				shownHp = living.getHealth();
				shownAbsorb = living.getAbsorptionAmount();
			}
			visible = true;
		}

		if (target != null) {
			float k = 1.0f - (float) Math.exp(-12.0f * dt);
			shownHp += (target.getHealth() - shownHp) * k;
			shownAbsorb += (target.getAbsorptionAmount() - shownAbsorb) * k;
			shownMax = Math.max(1.0f, target.getMaxHealth());
		}
		alpha += ((visible ? 1.0f : 0.0f) - alpha) * (1.0f - (float) Math.exp(-14.0f * dt));
		if (alpha < 0.03f) {
			alpha = 0.0f;
			if (!visible) target = null;
			return;
		}

		TextRenderer tr = mc.textRenderer;
		String hp = String.format(Locale.ROOT, "%.1f", Math.max(0.0f, shownHp));
		if (showMax.getBool()) {
			hp += String.format(Locale.ROOT, " / %.0f", shownMax);
		}
		String absorb = shownAbsorb >= 0.5f ? String.format(Locale.ROOT, " +%.0f", shownAbsorb) : "";

		int heartW = tr.getWidth("♥");
		int textW = Bold.width(tr, hp) + Bold.width(tr, absorb);
		int w = heartW + 4 + textW + 12;
		int h = 14;

		float frac = Math.max(0.0f, Math.min(1.0f, shownHp / shownMax));
		int heartColor = 0xFF000000 | ColorUtil.hsv(frac * 120.0f, 0.85f, 1.0f);

		float s = scale.getFloat();
		int cx = mc.getWindow().getScaledWidth() / 2;
		int cy = mc.getWindow().getScaledHeight() / 2 + offset.getInt();

		MatrixStack ms = ctx.getMatrices();
		ms.push();
		ms.translate(cx, cy, 0.0f);
		ms.scale(s, s, 1.0f);
		int x = -w / 2;
		UiShapes.roundRect(ctx, x, 0, w, h, 6, UiShapes.mulA(0xFF121216, 0.78f * alpha));
		UiShapes.roundRect(ctx, x + 1, h - 2, Math.max(2, Math.round((w - 2) * frac)), 1, 0,
				UiShapes.mulA(heartColor, alpha));

		int hc = UiShapes.mulA(heartColor, alpha);
		int wc = UiShapes.mulA(Theme.TEXT, alpha);
		int gc = UiShapes.mulA(0xFFFFC107, alpha);
		if (((hc >>> 24) & 0xFF) >= 12) {
			ctx.drawText(tr, "♥", x + 6, 3, hc, true);
			Bold.draw(ctx, tr, hp, x + 6 + heartW + 4, 3, wc);
			if (!absorb.isEmpty()) {
				Bold.draw(ctx, tr, absorb, x + 6 + heartW + 4 + Bold.width(tr, hp), 3, gc);
			}
		}
		ms.pop();
	}

	private static boolean hasArmor(LivingEntity entity) {
		return !entity.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
				|| !entity.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
				|| !entity.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
				|| !entity.getEquippedStack(EquipmentSlot.FEET).isEmpty();
	}
}
