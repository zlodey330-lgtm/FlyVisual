package com.flyvisual.mixin;

import com.flyvisual.fpsboost.FpsExtra;
import com.flyvisual.fpsboost.MobConfig;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FPS Boost: сундуки, таблички, кровати и другие блок-сущности можно не рисовать. */
@Mixin(BlockEntityRenderDispatcher.class)
public class FpsBlockEntityMixin {
	@Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
			at = @At("HEAD"), cancellable = true)
	private <E extends BlockEntity> void fpsboost$hideBlockEntity(E blockEntity, float tickDelta, MatrixStack matrices,
															  VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
		if (MobConfig.on() && FpsExtra.hideBlockEntity(blockEntity)) {
			ci.cancel();
		}
	}
}
