package com.flyvisual.mixin;

import com.flyvisual.fpsboost.FpsHooks;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FPS Boost: отмечаем, что сейчас рисуется далёкий моб (для упрощения). */
@Mixin(LivingEntityRenderer.class)
public class FpsLivingRendererMixin {
	@Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"))
	private void fpsboost$farStart(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
								   VertexConsumerProvider consumers, int light, CallbackInfo ci) {
		FpsHooks.farNow = FpsHooks.isFar(entity);
	}

	@Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("RETURN"))
	private void fpsboost$farEnd(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
								 VertexConsumerProvider consumers, int light, CallbackInfo ci) {
		FpsHooks.farNow = false;
	}
}
