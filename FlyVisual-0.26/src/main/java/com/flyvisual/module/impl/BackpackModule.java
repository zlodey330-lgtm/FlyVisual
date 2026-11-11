package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.BackpackModels;
import com.flyvisual.util.Draw3d;
import com.flyvisual.util.VoxelPart;
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

/** Рюкзак на спине. Виден только тебе и только в третьем лице; другие игроки его не видят. */
public class BackpackModule extends Module {
	private final DoubleSetting kind = addChoice("Backpack", 0, BackpackModels.NAMES);
	private final DoubleSetting size = addSetting("Backpack size", 1.0, 0.6, 1.5, 0.1);

	private final Draw3d solid = new Draw3d(false);

	public BackpackModule() {
		super("Backpack", "A backpack on your back");
		WorldRenderEvents.AFTER_ENTITIES.register(this::render);
	}

	private void render(WorldRenderContext context) {
		if (!isEnabled()) return;
		try {
			draw(context);
		} catch (Throwable t) {
			try {
				solid.flush();
			} catch (Throwable ignored) {
			}
			setEnabled(false);
		}
	}

	private void draw(WorldRenderContext context) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		MatrixStack matrices = context.matrixStack();
		if (player == null || context.world() == null || matrices == null) return;
		if (mc.options.getPerspective().isFirstPerson() || player.isSpectator()) return;
		// Лёжа, в полёте на элитрах и в плавании тело повёрнуто: рюкзак там выглядел бы неверно
		if (player.isFallFlying() || player.isSwimming() || player.isCrawling() || player.isSleeping()) return;

		float td = context.tickDelta();
		Vec3d pp = player.getLerpedPos(td);
		float bodyYaw = MathHelper.lerpAngleDegrees(td, player.prevBodyYaw, player.bodyYaw);
		boolean sneak = player.isInSneakingPose();

		BlockPos bp = BlockPos.ofFloored(pp.x, pp.y + 1.1, pp.z);
		int sky = context.world().getLightLevel(LightType.SKY, bp);
		int blk = context.world().getLightLevel(LightType.BLOCK, bp);
		float level = Math.max(blk, sky * context.world().getSkyBrightness(1.0f)) / 15.0f;
		float light = 0.5f + 0.5f * Math.max(0.0f, Math.min(1.0f, level));

		VoxelPart part = BackpackModels.get(kind.getInt());
		Vec3d cam = context.camera().getPos();
		matrices.push();
		matrices.translate(pp.x - cam.x, pp.y - cam.y, pp.z - cam.z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
		if (sneak) {
			// Тело наклоняется вперёд вокруг бёдер
			matrices.translate(0.0, 0.65, 0.0);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(28.0f));
			matrices.translate(0.0, 0.475, -0.125);
		} else {
			matrices.translate(0.0, 1.125, -0.125);
		}
		float s = size.getFloat();
		matrices.scale(s, s, s);
		part.draw(matrices, solid, light, 1.0f);
		matrices.pop();
		solid.flush();
	}
}
