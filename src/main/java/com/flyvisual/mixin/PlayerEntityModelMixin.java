package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.EmoteModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emotes: после ванильной позы накладываем позу эмоции на модель твоего игрока. */
@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelMixin {

	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void flyvisual$emote(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
								 float headYaw, float headPitch, CallbackInfo ci) {
		EmoteModule emotes = ModuleManager.emotes();
		if (emotes != null && emotes.isPlaying() && entity == MinecraftClient.getInstance().player) {
			emotes.apply((PlayerEntityModel<?>) (Object) this);
		}
	}
}
