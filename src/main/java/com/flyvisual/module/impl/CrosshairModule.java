package com.flyvisual.module.impl;

import com.flyvisual.config.Theme;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Свой прицел: форма, размер, толщина, зазор, цвет, чёрная обводка и покраснение на цели.
 * Ванильный прицел скрывается в InGameHudMixin. Вместе с ним скрывается и индикатор атаки.
 */
public class CrosshairModule extends Module {
	private static final String[] STYLES = {"Плюс", "Крест", "Точка", "Круг"};
	private static final String[] COLORS = {"Белый", "Красный", "Зелёный", "Голубой", "Жёлтый", "Радуга", "Цвет темы"};

	private final DoubleSetting style = addChoice("Style", 0, STYLES);
	private final DoubleSetting size = addSetting("Size", 6.0, 1.0, 20.0, 1.0);
	private final DoubleSetting thickness = addSetting("Thickness", 1.0, 1.0, 4.0, 1.0);
	private final DoubleSetting gap = addSetting("Gap", 3.0, 0.0, 12.0, 1.0);
	private final DoubleSetting color = addChoice("Color", 0, COLORS);
	private final DoubleSetting outline = addToggle("Outline", true);
	private final DoubleSetting highlight = addToggle("Highlight target", true);

	private record Rect(int x1, int y1, int x2, int y2) {
	}

	public CrosshairModule() {
		super("Crosshair", "Custom crosshair");
	}

	@Override
	public void renderOverlay(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = mc();
		if (mc.player == null || mc.currentScreen != null || !mc.options.getPerspective().isFirstPerson()) return;

		int cx = mc.getWindow().getScaledWidth() / 2;
		int cy = mc.getWindow().getScaledHeight() / 2;
		int rgb = pickColor(mc);

		int t = thickness.getInt();
		int s = size.getInt();
		int g = gap.getInt();
		List<Rect> rects = new ArrayList<>();

		// Центральный пиксель (или квадрат толщиной t) занимает [lo, hi). Все части считаются от него,
		// поэтому левая и правая, верхняя и нижняя части симметричны при любой толщине и зазоре.
		int lo = -(t / 2);
		int hi = lo + t;
		switch (style.getInt()) {
			case 0 -> {
				rects.add(new Rect(lo - g - s, lo, lo - g, hi));
				rects.add(new Rect(hi + g, lo, hi + g + s, hi));
				rects.add(new Rect(lo, lo - g - s, hi, lo - g));
				rects.add(new Rect(lo, hi + g, hi, hi + g + s));
			}
			case 1 -> {
				// диагональный крест ступеньками из квадратиков, без поворота (поворот делал края кривыми)
				for (int i = 0; i < s; i++) {
					int d = g + i;
					rects.add(new Rect(lo - d - t, lo - d - t, lo - d, lo - d));
					rects.add(new Rect(hi + d, lo - d - t, hi + d + t, lo - d));
					rects.add(new Rect(lo - d - t, hi + d, lo - d, hi + d + t));
					rects.add(new Rect(hi + d, hi + d, hi + d + t, hi + d + t));
				}
			}
			case 2 -> {
				int d = t + 1;
				int l2 = -(d / 2);
				rects.add(new Rect(l2, l2, l2 + d, l2 + d));
			}
			default -> {
				int points = Math.max(16, s * 6);
				double r = s + g;
				double half = t / 2.0;
				int lastX = Integer.MIN_VALUE;
				int lastY = Integer.MIN_VALUE;
				for (int i = 0; i < points; i++) {
					double a = 2.0 * Math.PI * i / points;
					int px = (int) Math.round(Math.cos(a) * r - half + (t % 2 == 0 ? 0.0 : 0.5));
					int py = (int) Math.round(Math.sin(a) * r - half + (t % 2 == 0 ? 0.0 : 0.5));
					if (px == lastX && py == lastY) continue;
					lastX = px;
					lastY = py;
					rects.add(new Rect(px, py, px + t, py + t));
				}
			}
		}

		MatrixStack ms = ctx.getMatrices();
		ms.push();
		ms.translate(cx, cy, 0.0f);

		if (outline.getBool()) {
			for (Rect r : rects) {
				ctx.fill(r.x1() - 1, r.y1() - 1, r.x2() + 1, r.y2() + 1, 0xFF000000);
			}
		}
		for (Rect r : rects) {
			ctx.fill(r.x1(), r.y1(), r.x2(), r.y2(), 0xFF000000 | rgb);
		}
		ms.pop();
	}

	/** Невидимых не подсвечиваем, кроме тех, на ком надета хоть одна часть брони (её видно). */
	private static boolean shouldHighlight(Entity entity) {
		if (!entity.isInvisible()) return true;
		if (entity instanceof LivingEntity living) {
			return !living.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
					|| !living.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
					|| !living.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
					|| !living.getEquippedStack(EquipmentSlot.FEET).isEmpty();
		}
		return false;
	}

	private int pickColor(MinecraftClient mc) {
		if (highlight.getBool() && mc.crosshairTarget instanceof EntityHitResult hit
				&& hit.getType() == HitResult.Type.ENTITY && shouldHighlight(hit.getEntity())) {
			return 0xFF3030;
		}
		return switch (color.getInt()) {
			case 0 -> 0xFFFFFF;
			case 1 -> 0xFF3B30;
			case 2 -> 0x30FF5A;
			case 3 -> 0x30C8FF;
			case 4 -> 0xFFE030;
			case 5 -> ColorUtil.hsv(System.currentTimeMillis() / 12 % 360, 0.8f, 1.0f);
			default -> Theme.accent() & 0xFFFFFF;
		};
	}
}
