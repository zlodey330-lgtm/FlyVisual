package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.OptimizationModule;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Оптимизация: без блеска зачарований. */
@Mixin(ItemStack.class)
public class ItemStackMixin {
	@Inject(method = "hasGlint", at = @At("HEAD"), cancellable = true)
	private void flyvisual$noGlint(CallbackInfoReturnable<Boolean> cir) {
		OptimizationModule opt = ModuleManager.optimization();
		if (opt != null && opt.blocksGlint()) {
			cir.setReturnValue(false);
		}
	}
}
