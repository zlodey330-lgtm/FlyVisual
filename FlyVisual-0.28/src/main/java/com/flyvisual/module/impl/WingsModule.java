package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.Draw3d;
import com.flyvisual.util.WingModels;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

/**
 * Крылья на спине с анимацией: в покое чуть покачиваются, при беге отводятся назад, в прыжке и падении машут.
 * Видны только тебе и только в третьем лице; другие игроки их не видят.
 */
public class WingsModule extends Module {
	private final DoubleSetting kind = addChoice("Wings", 0, WingModels.NAMES);
	private final DoubleSetting size = addSetting("Wings size", 1.0, 0.6, 1.6, 0.1);
	private final DoubleSetting speed = addSetting("Flap speed", 9.0, 3.0, 16.0, 1.0);

	private final Draw3d solid = new Draw3d(false);
	private final Draw3d glass = new Draw3d(true);

	private float air;
	private float move;
	private float phase;
	private long lastNanos;

	public WingsModule() {
		super("Wings", "Animated wings on your back");
		WorldRenderEvents.AFTER_ENTITIES.register(this::render);
	}

	private void render(WorldRenderContext context) {
		if (!isEnabled()) return;
		try {
			draw(context);
		} catch (Throwable t) {
			try {
				solid.flush();
				glass.flush();
			} catch (Throwable ignored) {
			}
			setEnabled(false);
		}
	}

	private void draw(WorldRenderContext context) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		MatrixStack ms = context.matrixStack();
		if (player == null || context.world() == null || ms == null) return;

		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
		lastNanos = now;

		// Состояния считаем всегда, чтобы при смене вида крылья не «прыгали»
		Vec3d vel = player.getVelocity();
		boolean flying = !player.isOnGround() && !player.isClimbing() && !player.isTouchingWater();
		boolean falling = flying && vel.y < -0.2;
		float k = 1.0f - (float) Math.exp(-8.0f * dt);
		air += ((flying ? 1.0f : 0.0f) - air) * k;
		float horiz = (float) Math.min(1.0, Math.hypot(vel.x, vel.z) / 0.22);
		move += (horiz - move) * k;
		phase += dt * speed.getFloat() * (0.35f + 0.65f * air);

		if (mc.options.getPerspective().isFirstPerson() || player.isSpectator()) return;
		if (player.isFallFlying() || player.isSwimming() || player.isCrawling() || player.isSleeping()) return;

		WingModels.Wing wing = WingModels.get(kind.getInt());
		float td = context.tickDelta();
		Vec3d pp = player.getLerpedPos(td);
		float bodyYaw = MathHelper.lerpAngleDegrees(td, player.prevBodyYaw, player.bodyYaw);
		boolean sneak = player.isInSneakingPose();

		BlockPos bp = BlockPos.ofFloored(pp.x, pp.y + 1.1, pp.z);
		int sky = context.world().getLightLevel(LightType.SKY, bp);
		int blk = context.world().getLightLevel(LightType.BLOCK, bp);
		float level = Math.max(blk, sky * context.world().getSkyBrightness(1.0f)) / 15.0f;
		float light = 0.5f + 0.5f * Math.max(0.0f, Math.min(1.0f, level));

		// Углы анимации (градусы)
		float t = (float) (now / 1.0e9);
		float idle = (float) Math.sin(t * 1.6) * 4.0f * (1.0f - air);
		float amp = falling ? 14.0f : 38.0f;
		float flap = (float) Math.sin(phase) * amp * air;
		float flex = (float) Math.sin(phase - 0.7f) * (amp * 0.45f) * air + idle * 0.6f;
		float up = wing.restUp + idle + flap + (falling ? 28.0f : 0.0f) - (sneak ? 14.0f : 0.0f);
		float sweep = wing.restSweep + move * 12.0f * (1.0f - air) - air * 6.0f + (sneak ? 12.0f : 0.0f);
		if (wing.flapAboutY) {
			// Бабочки: взмахи вокруг вертикали
			sweep = wing.restSweep + flap * 1.1f + idle * 2.0f + move * 8.0f * (1.0f - air);
			up = 0.0f;
		}

		Draw3d d = wing.translucent ? glass : solid;
		Vec3d cam = context.camera().getPos();
		ms.push();
		ms.translate(pp.x - cam.x, pp.y - cam.y, pp.z - cam.z);
		ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
		if (sneak) {
			ms.translate(0.0, 0.65, 0.0);
			ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(28.0f));
			ms.translate(0.0, 0.475, -0.125);
		} else {
			ms.translate(0.0, 1.125, -0.125);
		}
		float s = size.getFloat();
		ms.scale(s, s, s);

		for (int side = 0; side < 2; side++) {
			ms.push();
			ms.scale(side == 0 ? 1.0f : -1.0f, 1.0f, 1.0f);
			ms.translate(0.1f, 0.2f, -0.02f);
			ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sweep));
			ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(up));
			for (WingModels.Part p : wing.parts) {
				ms.push();
				ms.translate(p.ox, p.oy, p.oz);
				ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p.spread + p.weight * flex));
				p.mesh.draw(ms, d, light, wing.alpha);
				ms.pop();
			}
			ms.pop();
		}
		ms.pop();
		d.flush();
	}
}
