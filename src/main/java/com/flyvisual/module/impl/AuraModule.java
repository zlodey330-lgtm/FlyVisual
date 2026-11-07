package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.ColorUtil;
import com.flyvisual.util.Shape3D;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * 3D-аура: настоящие светящиеся кубики, которые вращаются вокруг тебя
 * (сфера, кольцо, спираль или орбиты). Рисуется только на твоей стороне.
 */
public class AuraModule extends Module {
	private static final String[] PATTERNS = {"Сфера", "Кольцо", "Спираль", "Орбиты"};

	private final DoubleSetting pattern = addChoice("Pattern", 0, PATTERNS);
	private final DoubleSetting amount = addSetting("Amount", 4.0, 1.0, 12.0, 1.0);
	private final DoubleSetting radius = addSetting("Radius", 0.9, 0.4, 2.5, 0.1);
	private final DoubleSetting size = addSetting("Size", 0.07, 0.03, 0.25, 0.01);
	private final DoubleSetting spin = addSetting("Spin speed", 1.0, 0.2, 4.0, 0.1);
	private final DoubleSetting hue = addSetting("Color (hue)", 0.0, 0.0, 355.0, 5.0);
	private final DoubleSetting rainbow = addToggle("Rainbow", false);
	private final DoubleSetting glow = addToggle("Glow", true);
	private final DoubleSetting firstPerson = addToggle("Show in first person", true);

	public AuraModule() {
		super("Aura", "3D glowing shapes around you");
		WorldRenderEvents.AFTER_ENTITIES.register(this::render);
	}

	private void render(WorldRenderContext context) {
		if (!isEnabled()) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || context.world() == null) return;
		if (!firstPerson.getBool() && mc.options.getPerspective().isFirstPerson()) return;

		VertexConsumerProvider consumers = context.consumers();
		MatrixStack matrices = context.matrixStack();
		if (consumers == null || matrices == null) return;

		VertexConsumer vc = consumers.getBuffer(RenderLayer.getLightning());
		Matrix4f m = matrices.peek().getPositionMatrix();
		Vec3d cam = context.camera().getPos();
		Vec3d pos = mc.player.getLerpedPos(context.tickDelta()).subtract(cam);

		double t = (System.nanoTime() / 1.0e9) * spin.get();
		double height = mc.player.getHeight();
		double r = radius.get();
		int n = amount.getInt() * 6;
		double half = size.get();

		switch (pattern.getInt()) {
			case 1 -> {
				// Кольцо на уровне пояса
				for (int i = 0; i < n; i++) {
					double a = t * 1.4 + i * 2.0 * Math.PI / n;
					cube(vc, m, pos.x + Math.cos(a) * r, pos.y + height * 0.5 + Math.sin(t * 2.0 + i) * 0.03,
							pos.z + Math.sin(a) * r, half, a, i, n, t);
				}
			}
			case 2 -> {
				// Спираль вверх, два витка
				for (int i = 0; i < n; i++) {
					double f = i / (double) n;
					double a = t * 1.6 + f * 4.0 * Math.PI;
					cube(vc, m, pos.x + Math.cos(a) * r, pos.y + f * height,
							pos.z + Math.sin(a) * r, half, a, i, n, t);
				}
			}
			case 3 -> {
				// Три орбиты под разными углами
				int per = Math.max(2, n / 3);
				for (int k = 0; k < 3; k++) {
					double tilt = k * Math.PI / 3.0;
					for (int j = 0; j < per; j++) {
						double a = t * (1.2 + k * 0.3) + j * 2.0 * Math.PI / per;
						double ox = Math.cos(a) * r;
						double oy = Math.sin(a) * r * Math.sin(tilt + 0.5);
						double oz = Math.sin(a) * r * Math.cos(tilt + 0.5);
						cube(vc, m, pos.x + ox, pos.y + height * 0.55 + oy, pos.z + oz, half * 1.3, a, j + k * per, per * 3, t);
					}
				}
			}
			default -> {
				// Сфера: точки на шаре (спираль Фибоначчи), медленно вращается
				for (int i = 0; i < n; i++) {
					double y = 1.0 - 2.0 * (i + 0.5) / n;
					double ring = Math.sqrt(1.0 - y * y);
					double a = i * 2.399963 + t * 0.8;
					cube(vc, m, pos.x + Math.cos(a) * ring * r, pos.y + height * 0.5 + y * r * 0.9,
							pos.z + Math.sin(a) * ring * r, half, a, i, n, t);
				}
			}
		}
	}

	private void cube(VertexConsumer vc, Matrix4f m, double x, double y, double z, double half, double yaw,
					  int index, int total, double t) {
		float h = rainbow.getBool() ? (float) ((index * 360.0 / Math.max(1, total) + t * 50.0) % 360.0) : hue.getFloat();
		int rgb = ColorUtil.hsv(h, 0.85f, 1.0f);
		Shape3D.cube(vc, m, x, y, z, half, yaw, rgb, 0.95f);
		if (glow.getBool()) {
			Shape3D.cube(vc, m, x, y, z, half * 2.4, yaw, rgb, 0.16f);
		}
	}
}
