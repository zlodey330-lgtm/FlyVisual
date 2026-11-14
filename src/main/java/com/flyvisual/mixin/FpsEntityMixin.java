package com.flyvisual.mixin;

import com.flyvisual.fpsboost.MobConfig;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** FPS Boost: огонь на сущностях и свечение (только на клиенте). */
@Mixin(Entity.class)
public class FpsEntityMixin {
	@Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
	private void fpsboost$noGlow(CallbackInfoReturnable<Boolean> cir) {
		if (!(MobConfig.on() && MobConfig.hideGlow)) return;
		Entity self = (Entity) (Object) this;
		if (self.getWorld().isClient) {
			cir.setReturnValue(false);
		}
	}
}
