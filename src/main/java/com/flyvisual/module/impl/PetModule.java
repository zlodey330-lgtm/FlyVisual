package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.Draw3d;
import com.flyvisual.util.PetRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.LightType;

/**
 * Питомец-компаньон: свой реалистичный питомец (дракончик, призрак, огонёк, пантера и воксельные зверьки),
 * который рисуется только у тебя на экране и плавно ходит или летает рядом. Это не моб из Minecraft:
 * нет сущности, ИИ и сетевых пакетов, поэтому он не лагает и другие игроки его не видят.
 */
public class PetModule extends Module {
	private final DoubleSetting pet = addChoice("Pet", 0, PetRenderer.NAMES);
	private final DoubleSetting size = addSetting("Size", 1.0, 0.5, 2.0, 0.1);
	private final DoubleSetting distance = addSetting("Pet distance", 1.6, 0.8, 4.0, 0.2);
	private final DoubleSetting side = addChoice("Side", 0, "Справа", "Слева");

	private final Draw3d solid = new Draw3d(false);
	private final Draw3d glass = new Draw3d(true);

	private boolean placed;
	private ClientWorld lastWorld;
	private double px;
	private double py;
	private double pz;
	private float heading;
	private float walkPhase;
	/** Плавная высота земли под игроком: прыжок не двигает летающего питомца. */
	private double smoothBase;
	private float moving;
	private long lastNanos;
	/** Скорость питомца и «якорь» направления: цель не болтается при повороте камеры. */
	private double vx;
	private double vz;
	private double anchorYaw;
	private double lastPx;
	private double lastPz;
	private float lightFactor = 1.0f;
	private float lightTimer;
	private float lightTarget = 1.0f;

	public PetModule() {
		super("Pet", "A companion that follows you");
		WorldRenderEvents.AFTER_ENTITIES.register(this::render);
	}

	@Override
	public void onDisable() {
		placed = false;
	}

	private static float wrap(float deg) {
		return ((deg % 360.0f) + 540.0f) % 360.0f - 180.0f;
	}

	/** Высота поверхности под точкой (верх ближайшего твёрдого блока ниже y + 1). */
	private static final BlockPos.Mutable GROUND_POS = new BlockPos.Mutable();

	private static double groundY(ClientWorld world, double x, double y, double z) {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		int top = (int) Math.floor(y) + 1;
		for (int i = 0; i < 10; i++) {
			BlockPos pos = GROUND_POS.set(bx, top - i, bz);
			VoxelShape shape = world.getBlockState(pos).getCollisionShape(world, pos);
			if (!shape.isEmpty()) {
				return pos.getY() + shape.getMax(Direction.Axis.Y);
			}
		}
		return y;
	}

	private void render(WorldRenderContext context) {
		if (!isEnabled()) return;
		try {
			renderPet(context);
		} catch (Throwable t) {
			// Любая ошибка в питомце: дорисовываем буферы и выключаем питомца, чтобы игра не вылетала
			try {
				solid.flush();
				glass.flush();
			} catch (Throwable ignored) {
			}
			setEnabled(false);
		}
	}

	private void renderPet(WorldRenderContext context) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		ClientWorld world = context.world();
		MatrixStack matrices = context.matrixStack();
		if (player == null || world == null || matrices == null) return;

		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
		lastNanos = now;

		int kind = Math.max(0, Math.min(PetRenderer.NAMES.length - 1, pet.getInt()));
		boolean flying = PetRenderer.FLYING[kind];

		Vec3d pp = player.getLerpedPos(context.tickDelta());
		// Скорость игрока (по кадрам) и плавный «якорь»: питомец ориентируется на куда идёшь, а не на камеру
		double pvx = dt > 0 ? (pp.x - lastPx) / dt : 0.0;
		double pvz = dt > 0 ? (pp.z - lastPz) / dt : 0.0;
		lastPx = pp.x;
		lastPz = pp.z;
		double playerSpeed = Math.min(30.0, Math.hypot(pvx, pvz));
		double playerYaw = Math.toRadians(player.getYaw());
		if (!placed) anchorYaw = playerYaw;
		if (playerSpeed > 0.5) {
			double diff = Math.atan2(Math.sin(playerYaw - anchorYaw), Math.cos(playerYaw - anchorYaw));
			anchorYaw += diff * (1.0 - Math.exp(-1.6 * dt));
		}
		double sideSign = side.getInt() == 0 ? 1.0 : -1.0;
		double back = distance.get();
		double tx = pp.x + Math.sin(anchorYaw) * back - Math.cos(anchorYaw) * sideSign * 0.9;
		double tz = pp.z - Math.cos(anchorYaw) * back - Math.sin(anchorYaw) * sideSign * 0.9;

		if (!placed || lastWorld != world || Math.hypot(tx - px, tz - pz) > 24.0) {
			px = tx;
			pz = tz;
			vx = 0.0;
			vz = 0.0;
			smoothBase = groundY(world, pp.x, pp.y, pp.z);
			py = flying ? smoothBase + 1.4 : groundY(world, tx, pp.y, tz);
			heading = player.getYaw() * -1.0f;
			lastPx = pp.x;
			lastPz = pp.z;
			placed = true;
			lastWorld = world;
		}

		double ox = px;
		double oz = pz;
		double dx = tx - px;
		double dz = tz - pz;
		double dist = Math.hypot(dx, dz);
		// Желаемая скорость растёт с расстоянием, но не больше скорости игрока + запас. Скорость меняется плавно
		double maxSpeed = Math.max(3.5, playerSpeed * 1.25 + 1.0);
		double wantSpeed = dist < 0.25 ? 0.0 : Math.min(maxSpeed, (dist - 0.15) * 2.6);
		double wvx = dist > 1.0e-4 ? dx / dist * wantSpeed : 0.0;
		double wvz = dist > 1.0e-4 ? dz / dist * wantSpeed : 0.0;
		double kv = 1.0 - Math.exp(-5.0 * dt);
		vx += (wvx - vx) * kv;
		vz += (wvz - vz) * kv;
		px += vx * dt;
		pz += vz * dt;

		double ty;
		if (flying) {
			smoothBase += (groundY(world, pp.x, pp.y, pp.z) - smoothBase) * (1.0 - Math.exp(-1.2 * dt));
			ty = smoothBase + 1.4 + Math.sin(now / 1.0e9 * 1.8) * 0.1;
		} else {
			ty = groundY(world, px, Math.max(py, pp.y) + 0.5, pz);
		}
		py += (ty - py) * (1.0 - Math.exp(-(flying ? 3.0 : 10.0) * dt));

		double moved = Math.hypot(px - ox, pz - oz);
		float speed = Math.min(8.0f, (float) Math.hypot(vx, vz));
		float wantMove = Math.min(1.0f, speed / 1.2f);
		if (speed < 0.15f) wantMove = 0.0f;
		moving += (wantMove - moving) * Math.min(1.0f, 6.0f * dt);
		walkPhase += (float) moved * 7.5f;

		if (speed > 0.3f) {
			float want = (float) Math.toDegrees(Math.atan2(vx, vz));
			heading += wrap(want - heading) * Math.min(1.0f, 6.0f * dt);
		} else {
			float want = (float) Math.toDegrees(Math.atan2(pp.x - px, pp.z - pz));
			heading += wrap(want - heading) * Math.min(1.0f, 2.0f * dt);
		}
		float lookYaw = Math.max(-60.0f, Math.min(60.0f, wrap((float) Math.toDegrees(Math.atan2(pp.x - px, pp.z - pz)) - heading)));

		// От первого лица питомца не рисуем (он двигается и дальше, чтобы не телепортировался при смене вида)
		if (mc.options.getPerspective().isFirstPerson()) return;

		// Освещение считаем не каждый кадр, а 4 раза в секунду, и плавно меняем
		lightTimer -= dt;
		if (lightTimer <= 0.0f) {
			lightTimer = 0.25f;
			BlockPos bp = BlockPos.ofFloored(px, py + 0.5, pz);
			int sky = world.getLightLevel(LightType.SKY, bp);
			int blk = world.getLightLevel(LightType.BLOCK, bp);
			float level = Math.max(blk, sky * world.getSkyBrightness(1.0f)) / 15.0f;
			lightTarget = 0.45f + 0.55f * Math.max(0.0f, Math.min(1.0f, level));
		}
		lightFactor += (lightTarget - lightFactor) * Math.min(1.0f, 6.0f * dt);

		Vec3d cam = context.camera().getPos();
		matrices.push();
		matrices.translate(px - cam.x, py - cam.y, pz - cam.z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(heading));
		float s = size.getFloat();
		matrices.scale(s, s, s);
		PetRenderer.draw(kind, matrices, solid, glass, lightFactor, (float) (now / 1.0e9), walkPhase, moving, lookYaw, 0.0f);
		matrices.pop();
		solid.flush();
		glass.flush();
	}
}
