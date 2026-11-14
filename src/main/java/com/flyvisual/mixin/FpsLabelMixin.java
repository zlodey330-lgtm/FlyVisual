package com.flyvisual.mixin;

import com.flyvisual.fpsboost.FpsHooks;
import net.minecraft.client.render.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FPS Boost: у далёких мобов не рисуется ник над головой. */
@Mixin(EntityRenderer.class)
public class FpsLabelMixin {
	@Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true)
	private void fpsboost$farLabel(CallbackInfo ci) {
		if (FpsHooks.farNow) {
			ci.cancel();
		}
	}
}
