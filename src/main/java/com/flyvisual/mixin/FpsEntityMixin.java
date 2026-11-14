package com.flyvisual.mixin;

import com.flyvisual.fpsboost.MobConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** FPS Boost: огонь на сущностях и свечение (только на клиенте). */
@Mixin(Entity.class)
public class FpsEntityMixin {
	@Inject(method = "doesRenderOnFire", at = @At("HEAD"), cancellable = true)
	private void fpsboost$noFire(CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (!self.getWorld().isClient) return;
		boolean mine = self == MinecraftClient.getInstance().player;
		if (mine ? MobConfig.hideFireSelf : MobConfig.hideFireOthers) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
	private void fpsboost$noGlow(CallbackInfoReturnable<Boolean> cir) {
		if (!MobConfig.hideGlow) return;
		Entity self = (Entity) (Object) this;
		if (self.getWorld().isClient) {
			cir.setReturnValue(false);
		}
	}
}
