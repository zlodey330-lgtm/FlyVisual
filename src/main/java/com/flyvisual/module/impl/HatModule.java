package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.Draw3d;
import com.flyvisual.util.HatModels;
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

/**
 * Шляпа на твоей голове. Рисуется только у тебя на экране и только в третьем лице (от первого лица
 * она закрывала бы обзор). Другие игроки её не видят: это не предмет и не броня, пакетов нет.
 */
public class HatModule extends Module {
	private final DoubleSetting hat = addChoice("Hat", 0, HatModels.NAMES);
	private final DoubleSetting size = addSetting("Hat size", 1.0, 0.6, 1.6, 0.1);
	private final DoubleSetting lift = addSetting("Hat height", 0.0, -0.1, 0.2, 0.01);

	private final Draw3d solid = new Draw3d(false);

	public HatModule() {
		super("Hat", "A hat on your head");
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

		float td = context.tickDelta();
		Vec3d pp = player.getLerpedPos(td);
		double eye = player.getEyeHeight(player.getPose());
		float headYaw = MathHelper.lerpAngleDegrees(td, player.prevHeadYaw, player.headYaw);
		float pitch = MathHelper.lerp(td, player.prevPitch, player.getPitch());

		BlockPos bp = BlockPos.ofFloored(pp.x, pp.y + eye, pp.z);
		int sky = context.world().getLightLevel(LightType.SKY, bp);
		int blk = context.world().getLightLevel(LightType.BLOCK, bp);
		float level = Math.max(blk, sky * context.world().getSkyBrightness(1.0f)) / 15.0f;
		float light = 0.5f + 0.5f * Math.max(0.0f, Math.min(1.0f, level));

		VoxelPart part = HatModels.get(hat.getInt());
		Vec3d cam = context.camera().getPos();
		matrices.push();
		// Центр головы, потом поворот головы, потом вверх к макушке
		matrices.translate(pp.x - cam.x, pp.y + eye + 0.03 - cam.y, pp.z - cam.z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-headYaw));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
		matrices.translate(0.0, 0.25 + lift.get(), 0.0);
		float s = size.getFloat();
		matrices.scale(s, s, s);
		part.draw(matrices, solid, light, 1.0f);
		matrices.pop();
		solid.flush();
	}
}
