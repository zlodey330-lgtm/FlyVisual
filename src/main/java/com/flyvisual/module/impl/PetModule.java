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
 * Питомец-компаньон: свой реалистичный питомец (кошка, собака, лисичка, дракончик, призрак, огонёк),
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
	private float moving;
	private long lastNanos;

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
	private static double groundY(ClientWorld world, double x, double y, double z) {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		int top = (int) Math.floor(y) + 1;
		for (int i = 0; i < 10; i++) {
			BlockPos pos = new BlockPos(bx, top - i, bz);
			VoxelShape shape = world.getBlockState(pos).getCollisionShape(world, pos);
			if (!shape.isEmpty()) {
				return pos.getY() + shape.getMax(Direction.Axis.Y);
			}
		}
		return y;
	}

	private void render(WorldRenderContext context) {
		if (!isEnabled()) return;
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
		double yawRad = Math.toRadians(player.getYaw());
		// Вперёд игрока = (-sin, cos); питомец чуть позади и сбоку
		double sideSign = side.getInt() == 0 ? 1.0 : -1.0;
		double back = distance.get();
		double tx = pp.x + Math.sin(yawRad) * back - Math.cos(yawRad) * sideSign * 0.9;
		double tz = pp.z - Math.cos(yawRad) * back - Math.sin(yawRad) * sideSign * 0.9;

		if (!placed || lastWorld != world || Math.hypot(tx - px, tz - pz) > 16.0) {
			px = tx;
			pz = tz;
			py = flying ? pp.y + 1.4 : groundY(world, tx, pp.y, tz);
			heading = player.getYaw() * -1.0f;
			placed = true;
			lastWorld = world;
		}

		double k = 1.0 - Math.exp(-4.0 * dt);
		double ox = px;
		double oz = pz;
		double dx = tx - px;
		double dz = tz - pz;
		double dist = Math.hypot(dx, dz);
		if (dist > 0.12) {
			// Скорость ограничена: не телепортируется, а бежит
			double step = Math.min(dist * k, 6.0 * dt);
			px += dx / dist * step;
			pz += dz / dist * step;
		}
		double ty = flying ? pp.y + 1.4 + Math.sin(now / 1.0e9 * 1.8) * 0.12 : groundY(world, px, Math.max(py, pp.y) + 0.5, pz);
		py += (ty - py) * (1.0 - Math.exp(-(flying ? 3.0 : 12.0) * dt));

		double moved = Math.hypot(px - ox, pz - oz);
		float speed = dt > 0 ? (float) (moved / dt) : 0.0f;
		float wantMove = Math.min(1.0f, speed / 1.0f);
		moving += (wantMove - moving) * Math.min(1.0f, 8.0f * dt);
		walkPhase += (float) moved * 7.5f;

		if (moved > 0.0005) {
			float want = (float) Math.toDegrees(Math.atan2(px - ox, pz - oz));
			heading += wrap(want - heading) * Math.min(1.0f, 9.0f * dt);
		} else {
			float want = (float) Math.toDegrees(Math.atan2(pp.x - px, pp.z - pz));
			heading += wrap(want - heading) * Math.min(1.0f, 2.5f * dt);
		}
		float lookYaw = Math.max(-60.0f, Math.min(60.0f, wrap((float) Math.toDegrees(Math.atan2(pp.x - px, pp.z - pz)) - heading)));

		BlockPos bp = BlockPos.ofFloored(px, py + 0.5, pz);
		int sky = world.getLightLevel(LightType.SKY, bp);
		int blk = world.getLightLevel(LightType.BLOCK, bp);
		float level = Math.max(blk, sky * world.getSkyBrightness(1.0f)) / 15.0f;
		float lightFactor = 0.45f + 0.55f * Math.max(0.0f, Math.min(1.0f, level));

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
