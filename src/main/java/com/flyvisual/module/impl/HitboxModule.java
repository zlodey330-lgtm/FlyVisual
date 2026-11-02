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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Свои хитбоксы с выбором цвета (вместо ванильных F3+B).
 * Рисуются только на твоей стороне и скрыты за блоками, как обычные линии, сервер ничего не узнаёт.
 */
public class HitboxModule extends Module {
	private final DoubleSetting hue = addSetting("Color (hue)", 0.0, 0.0, 355.0, 5.0);
	private final DoubleSetting brightness = addSetting("Brightness", 100.0, 30.0, 100.0, 5.0);
	private final DoubleSetting opacity = addSetting("Opacity", 100.0, 20.0, 100.0, 10.0);
	private final DoubleSetting range = addSetting("Range", 48.0, 8.0, 128.0, 8.0);
	private final DoubleSetting playersOnly = addToggle("Players only", true);

	public HitboxModule() {
		super("Hitboxes", "Colored hitboxes of entities");
		WorldRenderEvents.AFTER_ENTITIES.register(this::render);
	}

	private void render(WorldRenderContext context) {
		if (!isEnabled()) return;

		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || context.world() == null) return;

		VertexConsumerProvider consumers = context.consumers();
		MatrixStack matrices = context.matrixStack();
		if (consumers == null || matrices == null) return;

		VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
		Vec3d cam = context.camera().getPos();
		float tickDelta = context.tickDelta();

		int rgb = ColorUtil.hsv(hue.getFloat(), 0.85f, brightness.getFloat() / 100.0f);
		float r = ColorUtil.red(rgb);
		float g = ColorUtil.green(rgb);
		float b = ColorUtil.blue(rgb);
		float a = opacity.getFloat() / 100.0f;
		double maxDistSq = range.get() * range.get();

		for (Entity entity : context.world().getEntities()) {
			if (entity == mc.player || entity.isInvisible()) continue;
			if (playersOnly.getBool() && !(entity instanceof PlayerEntity)) continue;
			if (entity.squaredDistanceTo(cam) > maxDistSq) continue;

			// Плавная позиция между тиками и сдвиг в координаты камеры
			Vec3d lerped = entity.getLerpedPos(tickDelta);
			Vec3d shift = lerped.subtract(entity.getPos()).subtract(cam);
			Box box = entity.getBoundingBox().offset(shift);

			WorldRenderer.drawBox(matrices, lines, box, r, g, b, a);
		}
	}
}
