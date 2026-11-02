package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.HandOffsetModule;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

	/** Hand Offset: двигаем и масштабируем предмет в руке. Правая и левая рука сдвигаются зеркально по X. */
	@Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
	private void flyvisual$handStart(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
									 float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
									 VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		HandOffsetModule module = ModuleManager.handOffset();
		if (module == null || !module.isEnabled()) return;

		Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		float side = arm == Arm.RIGHT ? 1.0f : -1.0f;

		matrices.push();
		matrices.translate(side * module.getX(), module.getY(), module.getZ());
		float scale = module.getScale();
		matrices.scale(scale, scale, scale);
	}

	@Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
	private void flyvisual$handEnd(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
								   float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
								   VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		HandOffsetModule module = ModuleManager.handOffset();
		if (module != null && module.isEnabled()) {
			matrices.pop();
		}
	}
}
