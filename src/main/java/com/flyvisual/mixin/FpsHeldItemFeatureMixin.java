package com.flyvisual.mixin;

import com.flyvisual.fpsboost.FpsHooks;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FPS Boost: у далёких мобов предметы в руках не рисуются. */
@Mixin(HeldItemFeatureRenderer.class)
public class FpsHeldItemFeatureMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void fpsboost$farItems(CallbackInfo ci) {
		if (FpsHooks.farNow) {
			ci.cancel();
		}
	}
}
