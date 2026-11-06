package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.ColorUtil;
import com.flyvisual.util.Shape3D;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 3D-шлейф: за тобой остаётся светящаяся объёмная лента или цепочка кубиков,
 * которая плавно сужается и тает. Рисуется только на твоей стороне.
 */
public class TrailModule extends Module {
	private static final String[] STYLES = {"Лента", "Кубики"};
	private static final String[] HEIGHTS = {"Ноги", "Тело", "Голова"};

	private final DoubleSetting style = addChoice("Style", 0, STYLES);
	private final DoubleSetting height = addChoice("Height", 1, HEIGHTS);
	private final DoubleSetting length = addSetting("Length", 30.0, 8.0, 80.0, 2.0);
	private final DoubleSetting size = addSetting("Size", 0.18, 0.05, 0.5, 0.01);
	private final DoubleSetting hue = addSetting("Color (hue)", 0.0, 0.0, 355.0, 5.0);
	private final DoubleSetting rainbow = addToggle("Rainbow", true);
	private final DoubleSetting glow = addToggle("Glow", true);

	/** Точки пути: x, y, z, номер тика, когда записана. */
	private final ArrayDeque<double[]> points = new ArrayDeque<>();
	private long tickCounter;

	public TrailModule() {
		super("Trail", "3D trail behind you");
		WorldRenderEvents.AFTER_ENTITIES.register(this::render);
	}

	@Override
	public void onDisable() {
		points.clear();
	}

	private double yOffset(ClientPlayerEntity p) {
		return switch (height.getInt()) {
			case 0 -> 0.12;
			case 2 -> p.getHeight() * 0.9;
			default -> p.getHeight() * 0.5;
		};
	}

	@Override
	public void onTick(MinecraftClient client) {
		ClientPlayerEntity p = client.player;
		if (p == null || client.world == null) {
			points.clear();
			return;
		}
		tickCounter++;

		double y = p.getY() + yOffset(p);
		double[] last = points.peekFirst();
		boolean moved = last == null || Math.hypot(Math.hypot(p.getX() - last[0], p.getZ() - last[2]), y - last[1]) > 0.04;
		if (moved) {
			points.addFirst(new double[]{p.getX(), y, p.getZ(), tickCounter});
		}

		double life = length.get();
		while (!points.isEmpty() && tickCounter - points.peekLast()[3] > life) {
			points.removeLast();
		}
	}

	private void render(WorldRenderContext context) {
		if (!isEnabled()) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity p = mc.player;
		if (p == null || context.world() == null || points.isEmpty()) return;

		VertexConsumerProvider consumers = context.consumers();
		MatrixStack matrices = context.matrixStack();
		if (consumers == null || matrices == null) return;

		VertexConsumer vc = consumers.getBuffer(RenderLayer.getLightning());
		Matrix4f m = matrices.peek().getPositionMatrix();
		Vec3d cam = context.camera().getPos();
		float tickDelta = context.tickDelta();
		double life = length.get();

		// Путь от тебя (возраст 0) к самым старым точкам, в координатах камеры
		List<double[]> path = new ArrayList<>(points.size() + 1);
		Vec3d now = p.getLerpedPos(tickDelta);
		path.add(new double[]{now.x - cam.x, now.y + yOffset(p) - cam.y, now.z - cam.z, 0.0});
		Iterator<double[]> it = points.iterator();
		while (it.hasNext()) {
			double[] pt = it.next();
			double age = tickCounter - pt[3] + tickDelta;
			path.add(new double[]{pt[0] - cam.x, pt[1] - cam.y, pt[2] - cam.z, Math.min(age, life)});
		}
		if (path.size() < 2) return;

		double half = size.get();
		float time = (float) (System.nanoTime() / 1.0e9);
		boolean ribbon = style.getInt() == 0;
		double cubeCarry = 0.0;

		for (int i = 0; i < path.size() - 1; i++) {
			double[] a = path.get(i);
			double[] b = path.get(i + 1);
			double dx = b[0] - a[0];
			double dy = b[1] - a[1];
			double dz = b[2] - a[2];
			double horiz = Math.hypot(dx, dz);
			if (horiz < 1.0e-4 && Math.abs(dy) < 1.0e-4) continue;

			double fa = 1.0 - a[3] / life;
			double fb = 1.0 - b[3] / life;
			float hA = rainbow.getBool() ? (float) (((a[3] * 8.0) + time * 40.0) % 360.0) : hue.getFloat();
			int rgb = ColorUtil.hsv(hA, 0.85f, 1.0f);

			if (ribbon) {
				// Боковой вектор перпендикулярно движению
				double sx;
				double sz;
				if (horiz > 1.0e-4) {
					sx = -dz / horiz;
					sz = dx / horiz;
				} else {
					sx = 1.0;
					sz = 0.0;
				}
				double[][] sa = Shape3D.section(a[0], a[1], a[2], sx, sz, half * fa, half * 0.18 * (0.3 + fa));
				double[][] sb = Shape3D.section(b[0], b[1], b[2], sx, sz, half * fb, half * 0.18 * (0.3 + fb));
				Shape3D.prism(vc, m, sa, sb, rgb, (float) (0.9 * fa), (float) (0.9 * fb));
				if (glow.getBool()) {
					double[][] ga = Shape3D.section(a[0], a[1], a[2], sx, sz, half * fa * 1.9, half * 0.7 * fa);
					double[][] gb = Shape3D.section(b[0], b[1], b[2], sx, sz, half * fb * 1.9, half * 0.7 * fb);
					Shape3D.prism(vc, m, ga, gb, rgb, (float) (0.14 * fa), (float) (0.14 * fb));
				}
			} else {
				// Цепочка кубиков вдоль отрезка с равным шагом
				double segLen = Math.sqrt(dx * dx + dy * dy + dz * dz);
				double step = Math.max(0.12, half * 2.6);
				double yaw = Math.atan2(dz, dx);
				double d = cubeCarry;
				while (d < segLen) {
					double f = d / segLen;
					double fade = fa + (fb - fa) * f;
					double cx = a[0] + dx * f;
					double cy = a[1] + dy * f;
					double cz = a[2] + dz * f;
					double hf = half * (0.3 + 0.7 * fade);
					Shape3D.cube(vc, m, cx, cy, cz, hf, yaw, rgb, (float) (0.95 * fade));
					if (glow.getBool()) {
						Shape3D.cube(vc, m, cx, cy, cz, hf * 2.3, yaw, rgb, (float) (0.15 * fade));
					}
					d += step;
				}
				cubeCarry = d - segLen;
			}
		}
	}
}
