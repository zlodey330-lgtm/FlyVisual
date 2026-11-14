package com.flyvisual.mixin;

import com.flyvisual.fpsboost.HandScale;
import com.flyvisual.fpsboost.MobConfig;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FPS Boost: размер предмета в руке от первого лица (тотем, меч, щит, зелье, еда). */
@Mixin(HeldItemRenderer.class)
public class FpsHandMixin {
	@Unique
	private boolean fpsboost$pushed = false;

	@Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
	private void fpsboost$scaleStart(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
									 float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
									 VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		this.fpsboost$pushed = false;
		if (!MobConfig.handScalingActive()) return;
		float scale = HandScale.scaleFor(player, hand, item);
		if (scale >= 0.999f) return;
		Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		float side = arm == Arm.RIGHT ? 1.0f : -1.0f;
		float px = side * 0.56f;
		float py = -0.52f;
		float pz = -0.72f;
		matrices.push();
		matrices.translate(px, py, pz);
		matrices.scale(scale, scale, scale);
		matrices.translate(-px, -py, -pz);
		this.fpsboost$pushed = true;
	}

	@Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
	private void fpsboost$scaleEnd(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
								   float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
								   VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (this.fpsboost$pushed) {
			matrices.pop();
			this.fpsboost$pushed = false;
		}
	}
}
