package com.flyvisual.fpsboost;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Не рисует мобов, которых не видно из-за блоков. Проверка идёт лучами в 7 точек моба раз в 4 тика,
 * моб пропадает с задержкой 0,5 сек, чтобы не выскакивать при повороте. Игроки и боссы всегда видны.
 */
public final class OcclusionCuller {
	private static final int RECHECK_TICKS = 4;
	private static final int GRACE_TICKS = 10;
	private static final double MIN_DIST_SQ = 36.0;
	private static final Map<Integer, long[]> CACHE = new HashMap<>();
	private static long lastCleanup = 0L;

	private OcclusionCuller() {
	}

	public static boolean shouldHide(Entity entity, double camX, double camY, double camZ) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld level = mc.world;
		if (level == null || mc.player == null) return false;
		if (!(entity instanceof LivingEntity) || entity instanceof PlayerEntity
				|| entity instanceof EnderDragonEntity || entity instanceof WitherEntity) {
			return false;
		}
		if (entity.isGlowing() || entity.hasVehicle() || entity.hasPassengers()) return false;
		double dx = entity.getX() - camX;
		double dy = entity.getY() - camY;
		double dz = entity.getZ() - camZ;
		if (dx * dx + dy * dy + dz * dz < MIN_DIST_SQ) return false;

		long now = level.getTime();
		long[] entry = CACHE.get(entity.getId());
		if (entry == null) {
			entry = new long[]{now - RECHECK_TICKS, now};
			CACHE.put(entity.getId(), entry);
		}
		if (now - entry[0] >= RECHECK_TICKS) {
			entry[0] = now;
			if (isVisible(level, entity, new Vec3d(camX, camY, camZ))) {
				entry[1] = now;
			}
		}
		if (now - lastCleanup > 200L) {
			lastCleanup = now;
			CACHE.values().removeIf(e -> now - e[0] > 100L);
		}
		return now - entry[1] > GRACE_TICKS;
	}

	private static boolean isVisible(World level, Entity entity, Vec3d cam) {
		Box box = entity.getBoundingBox();
		double cx = (box.minX + box.maxX) / 2.0;
		double cz = (box.minZ + box.maxZ) / 2.0;
		double h = box.maxY - box.minY;
		double mid = box.minY + h * 0.5;
		double inset = 0.05;
		Vec3d[] points = {
				new Vec3d(cx, mid, cz),
				new Vec3d(cx, box.maxY - inset, cz),
				new Vec3d(cx, box.minY + inset, cz),
				new Vec3d(box.minX + inset, mid, box.minZ + inset),
				new Vec3d(box.maxX - inset, mid, box.minZ + inset),
				new Vec3d(box.minX + inset, mid, box.maxZ - inset),
				new Vec3d(box.maxX - inset, mid, box.maxZ - inset)
		};
		for (Vec3d p : points) {
			RaycastContext ctx = new RaycastContext(cam, p, RaycastContext.ShapeType.VISUAL,
					RaycastContext.FluidHandling.NONE, entity);
			if (level.raycast(ctx).getType() == HitResult.Type.MISS) {
				return true;
			}
		}
		return false;
	}
}
