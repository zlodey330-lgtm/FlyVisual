package com.flyvisual.mixin;

import com.flyvisual.fpsboost.MobConfig;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** FPS Boost: без блеска зачарований. */
@Mixin(ItemStack.class)
public class FpsItemStackMixin {
	@Inject(method = "hasGlint", at = @At("HEAD"), cancellable = true)
	private void fpsboost$noGlint(CallbackInfoReturnable<Boolean> cir) {
		if (MobConfig.hideEnchantGlint) {
			cir.setReturnValue(false);
		}
	}
}
