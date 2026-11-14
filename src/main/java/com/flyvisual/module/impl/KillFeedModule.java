package com.flyvisual.module.impl;

import com.flyvisual.config.Theme;
import com.flyvisual.gui.HudEditorScreen;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.HudModule;
import com.flyvisual.util.Bold;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Килфид как в CS2: когда ты убиваешь моба или игрока, на экране появляется строка
 * «твой ник — оружие — ник жертвы». Позицию, размер и прозрачность можно менять.
 */
public class KillFeedModule extends HudModule {
	private static final int ROW_H = 18;
	private static final int ROW_GAP = 2;

	private static final class Tracked {
		LivingEntity entity;
		String name;
		ItemStack weapon = ItemStack.EMPTY;
		long lastHit;
	}

	private static final class Entry {
		String killer;
		String victim;
		ItemStack weapon;
		long time;
	}

	private final DoubleSetting duration = addSetting("Время показа, сек", 6.0, 2.0, 20.0, 1.0);
	private final DoubleSetting maxRows = addSetting("Макс. строк", 5.0, 1.0, 8.0, 1.0);
	private final DoubleSetting weaponIcon = addToggle("Иконка оружия", true);
	private final DoubleSetting killWord = addToggle("Слово «убил»", true);
	private final DoubleSetting redOutline = addToggle("Красная рамка", true);
	private final DoubleSetting playersOnly = addToggle("Только игроки", false);

	private final Map<Integer, Tracked> tracked = new HashMap<>();
	private final List<Entry> entries = new ArrayList<>();
	private long tick;

	public KillFeedModule() {
		super("Kill Feed", "Shows who you killed, like in CS2", 240, 30);
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (world.isClient() && isEnabled() && player == mc.player
					&& entity instanceof LivingEntity le && le != player && !(le instanceof ArmorStandEntity)
					&& (!playersOnly.getBool() || le instanceof PlayerEntity)) {
				Tracked t = tracked.computeIfAbsent(le.getId(), k -> new Tracked());
				t.entity = le;
				t.name = le.getName().getString();
				t.weapon = player.getMainHandStack().copy();
				t.lastHit = tick;
			}
			return ActionResult.PASS;
		});
	}

	@Override
	protected boolean enabledByDefault() {
		return false;
	}

	@Override
	public void onDisable() {
		tracked.clear();
		entries.clear();
	}

	@Override
	public void onTick(MinecraftClient client) {
		tick++;
		if (client.player == null || client.world == null) {
			tracked.clear();
			entries.clear();
			return;
		}
		Iterator<Tracked> it = tracked.values().iterator();
		while (it.hasNext()) {
			Tracked t = it.next();
			LivingEntity e = t.entity;
			boolean dead = e.isDead() || e.getHealth() <= 0.0f;
			if (dead) {
				Entry en = new Entry();
				en.killer = client.player.getName().getString();
				en.victim = t.name;
				en.weapon = t.weapon;
				en.time = System.currentTimeMillis();
				entries.add(en);
				it.remove();
			} else if (e.isRemoved() || tick - t.lastHit > 200) {
				it.remove();
			}
		}
		int cap = Math.max(1, maxRows.getInt());
		while (entries.size() > cap) entries.remove(0);
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = mc();
		TextRenderer tr = mc.textRenderer;
		long now = System.currentTimeMillis();
		long life = (long) (duration.get() * 1000.0);
		entries.removeIf(e -> now - e.time > life);

		List<Entry> show = new ArrayList<>(entries);
		boolean preview = false;
		if (show.isEmpty() && mc.currentScreen instanceof HudEditorScreen) {
			// В редакторе HUD показываем пример, чтобы было что двигать
			Entry sample = new Entry();
			sample.killer = mc.player != null ? mc.player.getName().getString() : "Player";
			sample.victim = "Enemy";
			sample.weapon = new ItemStack(Items.DIAMOND_SWORD);
			sample.time = now;
			show.add(sample);
			preview = true;
		}
		if (show.isEmpty()) {
			boxW = 60;
			boxH = ROW_H;
			return;
		}

		int maxW = 0;
		int yy = y;
		for (Entry e : show) {
			float fade = 1.0f;
			if (!preview) {
				long age = now - e.time;
				long left = life - age;
				fade = Math.min(Math.min(1.0f, age / 150.0f), Math.max(0.0f, left / 500.0f));
			}
			int w = drawRow(ctx, tr, e, x, yy, fade);
			maxW = Math.max(maxW, w);
			yy += ROW_H + ROW_GAP;
		}
		boxW = maxW;
		boxH = show.size() * (ROW_H + ROW_GAP) - ROW_GAP;
	}

	private static int withAlpha(int rgb, float a) {
		int al = Math.max(0, Math.min(255, Math.round(a * 255.0f)));
		return (al << 24) | (rgb & 0xFFFFFF);
	}

	private int drawRow(DrawContext ctx, TextRenderer tr, Entry e, int px, int py, float fade) {
		boolean icon = weaponIcon.getBool();
		boolean word = killWord.getBool();
		String wordText = "убил";
		int pad = 6;
		int gap = 5;
		int w = pad + Bold.width(tr, e.killer);
		if (word) w += gap + Bold.width(tr, wordText);
		if (icon) w += gap + 16;
		w += gap + Bold.width(tr, e.victim) + pad;

		float bgAlpha = (float) (opacity.get() / 100.0) * fade;
		ctx.fill(px, py, px + w, py + ROW_H, withAlpha(0x000000, bgAlpha));
		if (redOutline.getBool()) {
			int c = withAlpha(0xE23B3B, fade);
			ctx.fill(px, py, px + w, py + 1, c);
			ctx.fill(px, py + ROW_H - 1, px + w, py + ROW_H, c);
			ctx.fill(px, py, px + 1, py + ROW_H, c);
			ctx.fill(px + w - 1, py, px + w, py + ROW_H, c);
		}

		boolean visible = fade > 0.12f;
		int ty = py + (ROW_H - 8) / 2;
		int cx = px + pad;
		if (visible) Bold.draw(ctx, tr, e.killer, cx, ty, withAlpha(Theme.accent(), fade));
		cx += Bold.width(tr, e.killer);
		if (word) {
			cx += gap;
			if (visible) Bold.draw(ctx, tr, wordText, cx, ty, withAlpha(0xB8B8B8, fade));
			cx += Bold.width(tr, wordText);
		}
		if (icon) {
			cx += gap;
			if (fade > 0.45f && e.weapon != null && !e.weapon.isEmpty()) {
				ctx.drawItem(e.weapon, cx, py + (ROW_H - 16) / 2);
			}
			cx += 16;
		}
		cx += gap;
		if (visible) Bold.draw(ctx, tr, e.victim, cx, ty, withAlpha(0xFF5555, fade));
		return w;
	}
}
