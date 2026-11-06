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
	private static final String[] STYLES = {"Лента", "Кубики", "Бриллианты", "Иглы", "Волна", "Кольца"};
	private static final String[] HEIGHTS = {"Ноги", "Тело", "Голова"};

	private final DoubleSetting style = addChoice("Style", 0, STYLES);
	private final DoubleSetting height = addChoice("Height", 1, HEIGHTS);
	private final DoubleSetting length = addSetting("Length", 30.0, 8.0, 80.0, 2.0);
	private final DoubleSetting size = addSetting("Size", 0.18, 0.05, 0.5, 0.01);
	private final DoubleSetting hue = addSetting("Color (hue)", 0.0, 0.0, 355.0, 5.0);
	private final DoubleSetting rainbow = addToggle("Rainbow", true);
	private final DoubleSetting glow = addToggle("Glow", true);
	private final DoubleSetting firstPerson = addToggle("Show in first person", false);

	/** Точки пути: x, y, z, номер тика, когда записана. */
	private final ArrayDeque<double[]> points = new ArrayDeque<>();
	private long tickCounter;
	/** Сколько фигур ещё можно нарисовать в этом кадре (защита FPS). */
	private int budget;
	private static final int MAX_SHAPES = 450;

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
		// Только вид от третьего лица (F5), от первого лица шлейф не рисуем
		if (!firstPerson.getBool() && mc.options.getPerspective().isFirstPerson()) return;
		budget = MAX_SHAPES;

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
		int st = style.getInt();
		boolean ribbon = st == 0 || st == 4;
		boolean wave = st == 4;
		boolean glowOn = glow.getBool();
		double cubeCarry = 0.0;

		for (int i = 0; i < path.size() - 1 && budget > 0; i++) {
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

			// Боковой вектор перпендикулярно движению
			double sx = horiz > 1.0e-4 ? -dz / horiz : 1.0;
			double sz = horiz > 1.0e-4 ? dx / horiz : 0.0;

			if (ribbon) {
				double ya = 0.0;
				double yb = 0.0;
				double la = 0.0;
				double lb = 0.0;
				double wA = half * fa;
				double wB = half * fb;
				if (wave) {
					// Волна: тонкая лента, которая извивается вверх-вниз и в стороны
					double amp = half * 2.6;
					ya = Math.sin(a[3] * 0.45 + time * 3.0) * amp * fa;
					yb = Math.sin(b[3] * 0.45 + time * 3.0) * amp * fb;
					la = Math.cos(a[3] * 0.45 + time * 3.0) * amp * 0.5 * fa;
					lb = Math.cos(b[3] * 0.45 + time * 3.0) * amp * 0.5 * fb;
					wA *= 0.4;
					wB *= 0.4;
				}
				double ta = half * (wave ? 0.3 : 0.18) * (0.3 + fa);
				double tb = half * (wave ? 0.3 : 0.18) * (0.3 + fb);
				double[][] sa = Shape3D.section(a[0] + sx * la, a[1] + ya, a[2] + sz * la, sx, sz, wA, ta);
				double[][] sb = Shape3D.section(b[0] + sx * lb, b[1] + yb, b[2] + sz * lb, sx, sz, wB, tb);
				Shape3D.prism(vc, m, sa, sb, rgb, (float) (0.9 * fa), (float) (0.9 * fb));
				budget -= 4;
				if (glowOn) {
					double[][] ga = Shape3D.section(a[0] + sx * la, a[1] + ya, a[2] + sz * la, sx, sz, wA * 1.9, ta * 4.0);
					double[][] gb = Shape3D.section(b[0] + sx * lb, b[1] + yb, b[2] + sz * lb, sx, sz, wB * 1.9, tb * 4.0);
					Shape3D.prism(vc, m, ga, gb, rgb, (float) (0.14 * fa), (float) (0.14 * fb));
					budget -= 4;
				}
				continue;
			}

			// Цепочка отдельных фигур вдоль отрезка с равным шагом: кубики, бриллианты, иглы или кольца
			double segLen = Math.sqrt(dx * dx + dy * dy + dz * dz);
			double step = st == 5 ? Math.max(0.3, half * 4.0) : st == 3 ? Math.max(0.1, half * 2.0) : Math.max(0.12, half * 2.6);
			double yaw = Math.atan2(dz, dx);
			double d = cubeCarry;
			while (d < segLen && budget > 0) {
				double f = d / segLen;
				double fade = fa + (fb - fa) * f;
				double age = a[3] + (b[3] - a[3]) * f;
				double cx = a[0] + dx * f;
				double cy = a[1] + dy * f;
				double cz = a[2] + dz * f;
				double hf = half * (0.3 + 0.7 * fade);
				switch (st) {
					case 2 -> {
						// Бриллианты: два ряда октаэдров идут двойной спиралью вокруг пути
						double rad = half * 2.2 * (0.4 + 0.6 * fade);
						for (int strand = 0; strand < 2; strand++) {
							double ph = age * 0.55 + time * 2.0 + strand * Math.PI;
							double ox = sx * Math.cos(ph) * rad;
							double oy = Math.sin(ph) * rad;
							double oz = sz * Math.cos(ph) * rad;
							Shape3D.diamond(vc, m, cx + ox, cy + oy, cz + oz, hf * 0.8, ph, rgb, (float) (0.95 * fade));
							budget--;
							if (glowOn && budget > 60) {
								Shape3D.diamond(vc, m, cx + ox, cy + oy, cz + oz, hf * 1.9, ph, rgb, (float) (0.13 * fade));
								budget--;
							}
						}
					}
					case 3 -> {
						// Иглы: тонкие длинные искры разлетаются в стороны и тянутся против движения
						long seed = (long) (Math.floor(a[3] * 3.0) * 31L + i * 17L + (long) (d * 100.0));
						double r1 = ((seed * 2654435761L) & 0xFFFF) / 65535.0 - 0.5;
						double r2 = (((seed * 40503L) >> 3) & 0xFFFF) / 65535.0 - 0.5;
						double spread = half * 3.0 * (1.0 - fade * 0.5);
						double ox = sx * r1 * spread;
						double oz = sz * r1 * spread;
						double oy = r2 * spread + (1.0 - fade) * half * 3.0;
						Shape3D.needle(vc, m, cx + ox, cy + oy, cz + oz, dx + r1 * 0.5, dy + 0.3 + r2 * 0.3, dz + r2 * 0.5,
								hf * 2.4, hf * 0.22, rgb, (float) (0.95 * fade));
						budget--;
						if (glowOn && budget > 60) {
							Shape3D.needle(vc, m, cx + ox, cy + oy, cz + oz, dx + r1 * 0.5, dy + 0.3 + r2 * 0.3, dz + r2 * 0.5,
									hf * 3.0, hf * 0.7, rgb, (float) (0.12 * fade));
							budget--;
						}
					}
					case 5 -> {
						// Кольца: через равные промежутки кольцо из точек поперёк пути
						double rad = half * 3.0 * (0.3 + 0.7 * fade);
						int pts = 10;
						for (int q = 0; q < pts; q++) {
							double ang = q * 2.0 * Math.PI / pts + time;
							double ox = sx * Math.cos(ang) * rad;
							double oy = Math.sin(ang) * rad;
							double oz = sz * Math.cos(ang) * rad;
							Shape3D.cube(vc, m, cx + ox, cy + oy, cz + oz, hf * 0.4, yaw, rgb, (float) (0.9 * fade));
						}
						budget -= pts;
					}
					default -> {
						Shape3D.cube(vc, m, cx, cy, cz, hf, yaw, rgb, (float) (0.95 * fade));
						budget--;
						if (glowOn && budget > 60) {
							Shape3D.cube(vc, m, cx, cy, cz, hf * 2.3, yaw, rgb, (float) (0.15 * fade));
							budget--;
						}
					}
				}
				d += step;
			}
			cubeCarry = d - segLen;
		}
	}
}
