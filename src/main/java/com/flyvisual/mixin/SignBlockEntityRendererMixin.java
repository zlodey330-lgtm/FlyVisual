package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.OptimizationModule;
import net.minecraft.block.entity.SignText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.SignBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Оптимизация: текст на табличках рисуется только вблизи. */
@Mixin(SignBlockEntityRenderer.class)
public class SignBlockEntityRendererMixin {
	@Inject(method = "renderText(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/SignText;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IIIZ)V",
			at = @At("HEAD"), cancellable = true)
	private void flyvisual$farText(BlockPos pos, SignText text, MatrixStack matrices, VertexConsumerProvider provider,
								   int light, int lineHeight, int lineWidth, boolean front, CallbackInfo ci) {
		OptimizationModule opt = ModuleManager.optimization();
		if (opt == null || !opt.isEnabled()) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.gameRenderer == null) return;
		Vec3d cam = mc.gameRenderer.getCamera().getPos();
		if (opt.signTooFar(Vec3d.ofCenter(pos), cam)) {
			ci.cancel();
		}
	}
}
