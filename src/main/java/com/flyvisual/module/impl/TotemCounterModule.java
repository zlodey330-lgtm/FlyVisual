package com.flyvisual.module.impl;

import com.flyvisual.config.Theme;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Счётчик тотемов бессмертия: иконка и сколько их у тебя (инвентарь и левая рука). */
public class TotemCounterModule extends HudModule {
	private static final ItemStack ICON = new ItemStack(Items.TOTEM_OF_UNDYING);

	private static final String[] PLACES = {"Над сердцами", "Над едой", "Своё место"};

	private final DoubleSetting place = addChoice("Totem place", 0, PLACES);
	private final DoubleSetting hideZero = addToggle("Hide when zero", false);

	public TotemCounterModule() {
		super("Totem Counter", "How many totems you carry", 4, 76);
		accentBar = addToggle("Accent bar", true);
	}

	@Override
	protected boolean enabledByDefault() {
		return false;
	}

	private float curX = Float.NaN;
	private float curY = Float.NaN;
	private int freeX;
	private int freeY;
	private boolean freeKnown;
	private int lastMode = -1;
	private long lastNanos;

	/** Следуем за сердцами или едой: в «Своём месте» позиция берётся из HUD-редактора. */
	@Override
	public void preLayout() {
		MinecraftClient mc = mc();
		PlayerEntity player = mc.player;
		int mode = place.getInt();
		if (!freeKnown) {
			freeX = x;
			freeY = y;
			freeKnown = true;
		}
		if (mode == 2) {
			if (lastMode != 2 && lastMode != -1) {
				x = freeX;
				y = freeY;
			}
			freeX = x;
			freeY = y;
			lastMode = 2;
			curX = Float.NaN;
			return;
		}
		lastMode = mode;
		if (player == null) return;

		int sw = mc.getWindow().getScaledWidth();
		int sh = mc.getWindow().getScaledHeight();
		boolean bars = mc.interactionManager == null || mc.interactionManager.hasStatusBars();
		int targetX;
		int top;
		if (mode == 0) {
			// Левая сторона: сердца (с золотыми и лишними рядами), выше них броня
			targetX = sw / 2 - 91;
			if (!bars) {
				top = sh - 30;
			} else {
				float maxHp = player.getMaxHealth();
				int absorb = (int) Math.ceil(player.getAbsorptionAmount());
				int rows = (int) Math.ceil((maxHp + absorb) / 20.0f);
				int spacing = Math.max(10 - (rows - 2), 3);
				top = sh - 39 - Math.max(0, rows - 1) * spacing;
				if (player.getArmor() > 0) top -= 10;
			}
		} else {
			// Правая сторона: еда, выше пузырьки воздуха, если ты под водой
			targetX = sw / 2 + 91 - boxW;
			if (!bars) {
				top = sh - 30;
			} else {
				top = sh - 39;
				if (player.getAir() < player.getMaxAir()) top -= 10;
			}
		}
		int targetY = top - 3 - boxH;

		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
		lastNanos = now;
		if (Float.isNaN(curX)) {
			curX = targetX;
			curY = targetY;
		} else {
			float k = 1.0f - (float) Math.exp(-16.0f * dt);
			curX += (targetX - curX) * k;
			curY += (targetY - curY) * k;
		}
		x = Math.round(curX);
		y = Math.round(curY);
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = mc();
		PlayerEntity player = mc.player;
		if (player == null) return;

		int count = 0;
		for (ItemStack stack : player.getInventory().main) {
			if (stack.isOf(Items.TOTEM_OF_UNDYING)) count += stack.getCount();
		}
		if (player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) count += player.getOffHandStack().getCount();
		if (count == 0 && hideZero.getBool()) return;

		TextRenderer tr = mc.textRenderer;
		String text = "x" + count;
		int w = 6 + 16 + 4 + tr.getWidth(text) + 6;
		int h = 20;
		ctx.fill(x, y, x + w, y + h, bg());
		if (showAccentBar()) {
			ctx.fill(x, y, x + 2, y + h, Theme.accent());
		}
		ctx.drawItem(ICON, x + 6, y + 2);
		ctx.drawText(tr, text, x + 6 + 16 + 4, y + 6, count == 0 ? 0xFFFF5555 : Theme.TEXT, true);
		boxW = w;
		boxH = h;
	}
}
