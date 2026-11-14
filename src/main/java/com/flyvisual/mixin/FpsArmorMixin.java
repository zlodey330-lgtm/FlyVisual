package com.flyvisual.mixin;

import com.flyvisual.fpsboost.FpsHooks;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FPS Boost: у далёких мобов броня не рисуется. */
@Mixin(ArmorFeatureRenderer.class)
public class FpsArmorMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void fpsboost$farArmor(CallbackInfo ci) {
		if (FpsHooks.farNow) {
			ci.cancel();
		}
	}
}
