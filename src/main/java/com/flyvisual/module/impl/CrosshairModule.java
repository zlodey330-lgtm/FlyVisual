package com.flyvisual.module.impl;

import com.flyvisual.config.Theme;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.RotationAxis;

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
		boolean rotate = false;

		switch (style.getInt()) {
			case 0, 1 -> {
				rotate = style.getInt() == 1;
				int lo = -(t / 2);
				int hi = lo + t;
				rects.add(new Rect(-g - s, lo, -g, hi));
				rects.add(new Rect(g, lo, g + s, hi));
				rects.add(new Rect(lo, -g - s, hi, -g));
				rects.add(new Rect(lo, g, hi, g + s));
			}
			case 2 -> {
				int d = t + 1;
				int lo = -(d / 2);
				rects.add(new Rect(lo, lo, lo + d, lo + d));
			}
			default -> {
				int points = Math.max(16, s * 6);
				int lo = -(t / 2);
				for (int i = 0; i < points; i++) {
					double a = 2.0 * Math.PI * i / points;
					int px = (int) Math.round(Math.cos(a) * (s + g));
					int py = (int) Math.round(Math.sin(a) * (s + g));
					rects.add(new Rect(px + lo, py + lo, px + lo + t, py + lo + t));
				}
			}
		}

		MatrixStack ms = ctx.getMatrices();
		ms.push();
		ms.translate(cx, cy, 0.0f);
		if (rotate) {
			ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45.0f));
		}

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

	private int pickColor(MinecraftClient mc) {
		if (highlight.getBool() && mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
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
