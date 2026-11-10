package com.flyvisual.module.impl;

import com.flyvisual.util.Bold;
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
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.client.util.math.MatrixStack;

import java.util.Locale;

/**
 * Здоровье, броня и расстояние до существа, на которое ты смотришь.
 * Скруглённая панель, плавное появление и исчезновение, полоска здоровья плавно едет,
 * а за ней тянется светлый "след" потерянного здоровья.
 */
public class TargetHudModule extends HudModule {
	private static final int W = 150;
	private static final int BASE_H = 38;
	private static final int CELL = 22;
	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private final DoubleSetting playersOnly = addToggle("Players only", false);
	private final DoubleSetting showArmor = addToggle("Show armor", true);
	private final DoubleSetting showBar = addToggle("Show durability bar", true);
	private final DoubleSetting showPercent = addToggle("Show durability %", true);

	private LivingEntity target;
	private long lastNanos = System.nanoTime();

	// Последние показанные данные (чтобы панель красиво гасла после потери цели)
	private String shownName = "";
	private float shownHp;
	private final ItemStack[] shownStacks = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
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

		Entity looked = mc.targetedEntity;
		// Показываем только пока смотришь на моба/игрока. Отвёл прицел: худ плавно скрывается.
		if (looked instanceof LivingEntity living && living != mc.player && !living.isRemoved()
				&& (!playersOnly.getBool() || living instanceof PlayerEntity)
				&& !(living.isInvisible() && !hasArmor(living))) {
			target = living;
		} else {
			target = null;
		}

		boxW = W;

		boolean preview = mc.currentScreen instanceof HudEditorScreen;
		boolean visible = target != null || preview;

		if (target != null) {
			float max = Math.max(1.0f, target.getMaxHealth());
			shownName = target.getName().getString();
			shownHp = target.getHealth();
			for (int i = 0; i < 4; i++) {
				shownStacks[i] = target.getEquippedStack(ARMOR_SLOTS[i]);
			}
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
			shownStacks[0] = damaged(Items.NETHERITE_HELMET, 0.12);
			shownStacks[1] = damaged(Items.DIAMOND_CHESTPLATE, 0.45);
			shownStacks[2] = ItemStack.EMPTY;
			shownStacks[3] = damaged(Items.IRON_BOOTS, 0.8);
			shownFrac = 0.7f;
			trailFrac = 0.85f;
			lastTarget = null;
		}

		alpha += ((visible ? 1.0f : 0.0f) - alpha) * (1.0f - (float) Math.exp(-12.0f * dt));
		if (alpha < 0.02f) {
			alpha = 0.0f;
			return;
		}

		int armorBlock = armorBlockHeight();
		int h = BASE_H + (showArmor.getBool() ? armorBlock + 4 : 0);
		boxH = h;

		// Лёгкий подъём при появлении
		int oy = Math.round((1.0f - alpha) * 5.0f);
		int px = x;
		int py = y + oy;

		int accent = Theme.accent();
		UiShapes.roundRect(ctx, px, py, W, h, 7, UiShapes.mulA(bg(), alpha));
		if (showAccentBar()) {
			UiShapes.roundRect(ctx, px + 4, py + 6, 2, h - 12, 1, UiShapes.mulA(accent, alpha));
		}

		int textX = px + (showAccentBar() ? 11 : 8);
		int nameColor = UiShapes.mulA(Theme.TEXT, alpha);
		if (((nameColor >>> 24) & 0xFF) >= 12) {
			Bold.draw(ctx, tr, shownName, textX, py + 5, nameColor);
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

		int infoColor = UiShapes.mulA(Theme.TEXT_DIM, alpha);
		if (((infoColor >>> 24) & 0xFF) >= 12) {
			Bold.draw(ctx, tr, String.format(Locale.ROOT, "%.1f ХП", shownHp), textX, py + 27, infoColor);
		}

		if (showArmor.getBool()) {
			drawArmor(ctx, tr, textX, py + 38, alpha);
		}
	}

	private static ItemStack damaged(net.minecraft.item.Item item, double fraction) {
		ItemStack stack = new ItemStack(item);
		stack.setDamage((int) (stack.getMaxDamage() * fraction));
		return stack;
	}

	private static boolean hasArmor(LivingEntity entity) {
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			if (!entity.getEquippedStack(slot).isEmpty()) return true;
		}
		return false;
	}

	/** Высота строки с бронёй: иконка, под ней шкала и/или проценты. */
	private int armorBlockHeight() {
		return 16 + (showBar.getBool() ? 3 : 0) + (showPercent.getBool() ? 7 : 0);
	}

	/** "Броня:" и иконки надетых предметов, под каждой шкала прочности и/или проценты. */
	private void drawArmor(DrawContext ctx, TextRenderer tr, int x0, int y0, float alpha) {
		int labelColor = UiShapes.mulA(Theme.TEXT_DIM, alpha);
		boolean any = false;
		for (ItemStack st : shownStacks) {
			if (!st.isEmpty()) any = true;
		}
		if (((labelColor >>> 24) & 0xFF) >= 12) {
			Bold.draw(ctx, tr, any ? "Броня:" : "Броня: нет", x0, y0 + 4, labelColor);
		}
		if (!any) return;

		int cx = x0 + Bold.width(tr, "Броня:") + 6;
		for (ItemStack st : shownStacks) {
			if (st.isEmpty()) continue;

			if (alpha > 0.6f) {
				ctx.drawItem(st, cx, y0);
			}
			int ny = y0 + 17;
			if (st.isDamageable()) {
				float frac = UiShapes.clamp01(1.0f - st.getDamage() / (float) Math.max(1, st.getMaxDamage()));
				if (showBar.getBool()) {
					UiShapes.roundRect(ctx, cx, ny, 16, 2, 1, UiShapes.mulA(0xFF2A2A2E, alpha));
					int w = Math.max(1, Math.round(16 * frac));
					UiShapes.roundRect(ctx, cx, ny, w, 2, 1,
							UiShapes.mulA(0xFF000000 | ColorUtil.hsv(frac * 120.0f, 0.85f, 0.95f), alpha));
					ny += 3;
				}
				int pctColor = UiShapes.mulA(Theme.TEXT, alpha);
				if (showPercent.getBool() && ((pctColor >>> 24) & 0xFF) >= 12) {
					String t = Math.round(frac * 100.0f) + "%";
					MatrixStack ms = ctx.getMatrices();
					ms.push();
					ms.translate(cx + 8.0f - Bold.width(tr, t) * 0.3f, ny + 1.0f, 0.0f);
					ms.scale(0.6f, 0.6f, 1.0f);
					Bold.draw(ctx, tr, t, 0, 0, pctColor);
					ms.pop();
				}
			}
			cx += CELL;
		}
	}
}
