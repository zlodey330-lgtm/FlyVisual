package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.EmoteModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emotes, запасная точка: если вставка в PlayerEntityModel не нашлась, позу накладываем здесь.
 * Дубль в одном кадре отсекает EmoteModule.apply.
 */
@Mixin(BipedEntityModel.class)
public class BipedEntityModelMixin {

	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void flyvisual$emoteBiped(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
									  float headYaw, float headPitch, CallbackInfo ci) {
		Object self = this;
		EmoteModule emotes = ModuleManager.emotes();
		if (self instanceof PlayerEntityModel<?> model && emotes != null && emotes.isPlaying()
				&& entity == MinecraftClient.getInstance().player) {
			emotes.apply(model);
		}
	}
}
