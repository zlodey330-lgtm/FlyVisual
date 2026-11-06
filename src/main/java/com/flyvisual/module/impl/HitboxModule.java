package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.ColorUtil;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Свой цвет хитбоксов (F3+B). Основную рамку перекрашивает EntityRenderDispatcherMixin.
 * Если подмена в игре не сработала, цветную рамку рисует сам модуль поверх ванильной
 * (только когда F3+B включён). Через стены ничего не рисуется.
 */
public class HitboxModule extends Module {
	private final DoubleSetting hue = addSetting("Color (hue)", 0.0, 0.0, 355.0, 5.0);
	private final DoubleSetting rainbow = addToggle("Rainbow", false);

	/** Время последнего вызова подмены из миксина. Если он свежий, своя рамка не нужна. */
	private static volatile long mixinNanos;

	public HitboxModule() {
		super("Hitboxes", "Custom hitbox color (F3+B)");
		WorldRenderEvents.AFTER_ENTITIES.register(this::render);
	}

	public static void markMixinWorked() {
		mixinNanos = System.nanoTime();
	}

	private int rgb() {
		float h = rainbow.getBool() ? (float) ((System.nanoTime() / 1.0e9 * 60.0) % 360.0) : hue.getFloat();
		return ColorUtil.hsv(h, 0.9f, 1.0f);
	}

	/** Цвет рамки: r, g, b, a в диапазоне 0..1. */
	public float[] color() {
		int rgb = rgb();
		return new float[]{ColorUtil.red(rgb), ColorUtil.green(rgb), ColorUtil.blue(rgb), 1.0f};
	}

	private void render(WorldRenderContext context) {
		if (!isEnabled()) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || context.world() == null) return;
		if (!mc.getEntityRenderDispatcher().shouldRenderHitboxes()) return;
		// Подмена цвета в миксине работает, вторая рамка не нужна
		if (System.nanoTime() - mixinNanos < 500_000_000L) return;

		VertexConsumerProvider consumers = context.consumers();
		MatrixStack matrices = context.matrixStack();
		if (consumers == null || matrices == null) return;

		VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
		Vec3d cam = context.camera().getPos();
		float tickDelta = context.tickDelta();
		float[] c = color();

		for (Entity e : context.world().getEntities()) {
			if (e == mc.player && mc.options.getPerspective().isFirstPerson()) continue;
			if (e.squaredDistanceTo(cam) > 96.0 * 96.0) continue;
			Vec3d shift = e.getLerpedPos(tickDelta).subtract(e.getPos()).subtract(cam);
			Box box = e.getBoundingBox().offset(shift).expand(0.004);
			WorldRenderer.drawBox(matrices, lines, box, c[0], c[1], c[2], c[3]);
		}
	}
}
