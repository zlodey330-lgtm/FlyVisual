package com.flyvisual.mixin;

import com.flyvisual.fpsboost.MobConfig;
import net.minecraft.block.entity.SignText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.SignBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FPS Boost: текст на табличках виден только до заданной дальности. */
@Mixin(SignBlockEntityRenderer.class)
public class FpsSignMixin {
	@Inject(method = "renderText(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/SignText;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IIIZ)V",
			at = @At("HEAD"), cancellable = true)
	private void fpsboost$signDistance(BlockPos pos, SignText text, MatrixStack matrices, VertexConsumerProvider provider,
								   int light, int lineHeight, int lineWidth, boolean front, CallbackInfo ci) {
		int limit = MobConfig.signDistance;
		if (limit >= MobConfig.SIGN_MAX) return;
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null) return;
		double d = (double) limit * limit;
		if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > d) {
			ci.cancel();
		}
	}
}
