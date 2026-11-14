package com.flyvisual.mixin;

import com.flyvisual.fpsboost.MobConfig;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FPS Boost: тряска камеры при уроне и анимация тотема на экране. */
@Mixin(GameRenderer.class)
public class FpsGameRendererMixin {
	@Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
	private void fpsboost$noHurtShake(CallbackInfo ci) {
		if (MobConfig.hideHurtCam) {
			ci.cancel();
		}
	}

	@Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
	private void fpsboost$noTotem(CallbackInfo ci) {
		if (MobConfig.hideTotemAnim) {
			ci.cancel();
		}
	}
}
