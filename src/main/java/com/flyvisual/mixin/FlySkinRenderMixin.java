package com.flyvisual.mixin;

import com.flyvisual.module.impl.SkinModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Skin: уменьшает или увеличивает модель твоего персонажа (от ног). */
@Mixin(PlayerEntityRenderer.class)
public class FlySkinRenderMixin {
	@Unique
	private boolean flyvisual$scaled;

	@Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void flyvisual$scaleStart(AbstractClientPlayerEntity player, float yaw, float tickDelta, MatrixStack matrices,
								  VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		flyvisual$scaled = false;
		SkinModule m = SkinModule.INSTANCE;
		if (m != null && m.voxelActive(player)) {
			// Воксельный персонаж рисуется отдельно, обычную модель пропускаем
			ci.cancel();
			return;
		}
		if (m != null && m.isEnabled() && player == MinecraftClient.getInstance().player) {
			float k = m.scale();
			if (k < 0.99f || k > 1.01f) {
				matrices.push();
				matrices.scale(k, k, k);
				flyvisual$scaled = true;
			}
		}
	}

	@Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("RETURN"), require = 0)
	private void flyvisual$scaleEnd(AbstractClientPlayerEntity player, float yaw, float tickDelta, MatrixStack matrices,
								VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (flyvisual$scaled) {
			matrices.pop();
			flyvisual$scaled = false;
		}
	}
}
