package com.flyvisual.fpsboost;

import com.google.gson.JsonObject;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Дополнительные настройки FPS Boost: блок-сущности, дальность, лимит игроков, графика, память. */
public final class FpsExtra {
	// Блок-сущности (сундуки, таблички, кровати, бочки и т.д.)
	public static boolean beHide = false;
	public static int beDist = 0; // 0 = не рисовать нигде, иначе только дальше N блоков

	// Дальность отрисовки (0 = выкл)
	public static int mobMaxDist = 0;
	public static int itemMaxDist = 0;
	public static int vehicleMaxDist = 0;

	// Игроки
	public static boolean playerCap = false;
	public static int playerFull = 8;
	public static int playerHideDist = 0;

	// Анимация вдали
	public static boolean farNoAnim = false;

	// Графика
	public static boolean fastGfx = false;
	public static boolean dynDist = false;
	public static int dynTarget = 60;
	public static int dynMin = 6;

	// Память и прогрев
	public static int autoCleanMin = 0;
	public static boolean warmup = false;

	private static long tickCount;
	private static long warmupUntil;
	private static ClientWorld lastWorld;

	private static boolean gfxApplied;
	private static GraphicsMode gfxPrev = GraphicsMode.FANCY;
	private static boolean dynInit;
	private static int dynOrig = 12;
	private static int dynCool;
	private static long lastClean;

	private FpsExtra() {
	}

	// ---------- запросы из миксинов ----------

	public static boolean warmupActive() {
		return warmup && tickCount < warmupUntil;
	}

	public static boolean hideBlockEntity(BlockEntity be) {
		if (warmupActive()) return true;
		if (!beHide) return false;
		if (beDist <= 0) return true;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) return false;
		Vec3d cam = mc.gameRenderer.getCamera().getPos();
		BlockPos p = be.getPos();
		double dx = p.getX() + 0.5 - cam.x;
		double dy = p.getY() + 0.5 - cam.y;
		double dz = p.getZ() + 0.5 - cam.z;
		return dx * dx + dy * dy + dz * dz > (double) beDist * beDist;
	}

	/** true, если сущность дальше своей предельной дальности и рисовать её не нужно. */
	public static boolean hideByDistance(Entity entity, double cx, double cy, double cz) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || entity == mc.player) return false;
		int max;
		if (entity instanceof PlayerEntity) {
			max = playerHideDist;
		} else if (entity instanceof ItemEntity) {
			max = itemMaxDist;
		} else if (entity instanceof BoatEntity || entity instanceof AbstractMinecartEntity) {
			max = vehicleMaxDist;
		} else if (entity instanceof LivingEntity) {
			max = mobMaxDist;
		} else {
			return false;
		}
		if (max <= 0) return false;
		double dx = entity.getX() - cx;
		double dy = entity.getY() - cy;
		double dz = entity.getZ() - cz;
		return dx * dx + dy * dy + dz * dz > (double) max * max;
	}

	/** Игрок вне числа N ближайших (кроме того, на кого ты смотришь): рисуется упрощённо. */
	public static boolean playerOutsideCap(PlayerEntity player) {
		if (!playerCap) return false;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.player == null || player == mc.player || mc.world == null) return false;
		if (player == mc.targetedEntity) return false;
		double mine = player.squaredDistanceTo(mc.player);
		int closer = 0;
		for (PlayerEntity other : mc.world.getPlayers()) {
			if (other == mc.player || other == player) continue;
			if (other.squaredDistanceTo(mc.player) < mine) {
				closer++;
				if (closer >= playerFull) return true;
			}
		}
		return false;
	}

	/** Дальше «Считать далёким от»: для упрощения анимации. */
	public static boolean farForAnim(Entity entity) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.player == null || entity == mc.player) return false;
		double d = MobConfig.simplifyDistance;
		return entity.squaredDistanceTo(mc.player) >= d * d;
	}

	// ---------- тик ----------

	public static void tick(MinecraftClient mc) {
		tickCount++;
		boolean on = MobConfig.on();

		if (mc.world != lastWorld) {
			lastWorld = mc.world;
			warmupUntil = tickCount + 160; // около 8 секунд после входа в мир
		}

		if (mc.options == null) return;

		// Быстрая графика (листва и вода проще)
		SimpleOption<GraphicsMode> gfx = mc.options.getGraphicsMode();
		boolean wantFast = on && fastGfx;
		if (wantFast && !gfxApplied) {
			gfxApplied = true;
			gfxPrev = gfx.getValue();
			if (gfxPrev != GraphicsMode.FAST) gfx.setValue(GraphicsMode.FAST);
		} else if (!wantFast && gfxApplied) {
			gfxApplied = false;
			gfx.setValue(gfxPrev);
		}

		// Динамическая дальность прорисовки
		SimpleOption<Integer> vd = mc.options.getViewDistance();
		boolean wantDyn = on && dynDist;
		if (wantDyn) {
			if (!dynInit) {
				dynInit = true;
				dynOrig = vd.getValue();
				dynCool = 100;
			}
			if (--dynCool <= 0 && mc.world != null) {
				dynCool = 100; // не чаще раза в 5 секунд, чтобы чанки не перезагружались постоянно
				int fps = mc.getCurrentFps();
				int cur = vd.getValue();
				if (fps < dynTarget && cur > dynMin) {
					vd.setValue(cur - 1);
				} else if (fps > dynTarget * 1.4 && cur < dynOrig) {
					vd.setValue(cur + 1);
				}
			}
		} else if (dynInit) {
			dynInit = false;
			vd.setValue(dynOrig);
		}

		// Автоочистка памяти
		if (on && autoCleanMin > 0 && mc.world != null) {
			if (lastClean == 0) lastClean = tickCount;
			if (tickCount - lastClean >= autoCleanMin * 1200L) {
				lastClean = tickCount;
				MemoryCleaner.run();
			}
		} else {
			lastClean = 0;
		}
	}

	// ---------- сохранение ----------

	private static int num(JsonObject r, String k, int def, int min, int max) {
		return r.has(k) ? Math.max(min, Math.min(max, r.get(k).getAsInt())) : def;
	}

	private static boolean flag(JsonObject r, String k) {
		return r.has(k) && r.get(k).getAsBoolean();
	}

	public static void write(JsonObject r) {
		r.addProperty("beHide", beHide);
		r.addProperty("beDist", beDist);
		r.addProperty("mobMaxDist", mobMaxDist);
		r.addProperty("itemMaxDist", itemMaxDist);
		r.addProperty("vehicleMaxDist", vehicleMaxDist);
		r.addProperty("playerCap", playerCap);
		r.addProperty("playerFull", playerFull);
		r.addProperty("playerHideDist", playerHideDist);
		r.addProperty("farNoAnim", farNoAnim);
		r.addProperty("fastGfx", fastGfx);
		r.addProperty("dynDist", dynDist);
		r.addProperty("dynTarget", dynTarget);
		r.addProperty("dynMin", dynMin);
		r.addProperty("autoCleanMin", autoCleanMin);
		r.addProperty("warmup", warmup);
	}

	public static void read(JsonObject r) {
		beHide = flag(r, "beHide");
		beDist = num(r, "beDist", 0, 0, 64);
		mobMaxDist = num(r, "mobMaxDist", 0, 0, 128);
		itemMaxDist = num(r, "itemMaxDist", 0, 0, 64);
		vehicleMaxDist = num(r, "vehicleMaxDist", 0, 0, 128);
		playerCap = flag(r, "playerCap");
		playerFull = num(r, "playerFull", 8, 1, 30);
		playerHideDist = num(r, "playerHideDist", 0, 0, 128);
		farNoAnim = flag(r, "farNoAnim");
		fastGfx = flag(r, "fastGfx");
		dynDist = flag(r, "dynDist");
		dynTarget = num(r, "dynTarget", 60, 20, 240);
		dynMin = num(r, "dynMin", 6, 2, 16);
		autoCleanMin = num(r, "autoCleanMin", 0, 0, 30);
		warmup = flag(r, "warmup");
	}
}
