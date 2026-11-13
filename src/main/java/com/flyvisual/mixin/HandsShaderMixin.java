package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.HandsFx;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HandsShaderMixin {
	/** Hands: после обычной отрисовки предмета рисуем поверх него светящиеся оболочки. */
	@Inject(
			method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("TAIL")
	)
	private void flyvisual$handsShader(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded,
									   MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
		HandsFx hands = ModuleManager.hands();
		if (hands != null) {
			hands.renderShader(entity, stack, mode, leftHanded, matrices, consumers);
		}
	}
}
