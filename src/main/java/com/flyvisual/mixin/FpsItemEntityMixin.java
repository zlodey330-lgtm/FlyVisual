package com.flyvisual.mixin;

import com.flyvisual.fpsboost.MobConfig;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** FPS Boost: предметы на земле без вращения. */
@Mixin(ItemEntity.class)
public class FpsItemEntityMixin {
	@Inject(method = "getRotation", at = @At("HEAD"), cancellable = true)
	private void fpsboost$noSpin(CallbackInfoReturnable<Float> cir) {
		if (MobConfig.hideItemSpin) {
			cir.setReturnValue(0.0f);
		}
	}
}
