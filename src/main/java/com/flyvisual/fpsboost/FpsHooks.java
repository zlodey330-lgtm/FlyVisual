package com.flyvisual.fpsboost;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Общие помощники для миксинов FPS Boost. */
public final class FpsHooks {
	/** true, пока рисуется далёкий моб: броня, предметы в руках и ник пропускаются. */
	public static boolean farNow;

	private FpsHooks() {
	}

	public static boolean isFar(Entity entity) {
		if (!MobConfig.simplifyFar || !(entity instanceof LivingEntity) || entity instanceof PlayerEntity) return false;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) return false;
		Vec3d cam = mc.gameRenderer.getCamera().getPos();
		double limit = MobConfig.simplifyDistance;
		return entity.squaredDistanceTo(cam) >= limit * limit;
	}
}
