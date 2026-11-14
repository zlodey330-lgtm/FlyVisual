package com.flyvisual.mixin;

import com.flyvisual.fpsboost.MobConfig;
import net.minecraft.client.texture.SpriteAtlasTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FPS Boost: остановка анимации воды, лавы и портала (в 1.20.1 замораживаются все анимированные текстуры). */
@Mixin(SpriteAtlasTexture.class)
public class FpsAtlasMixin {
	@Inject(method = "tickAnimatedSprites", at = @At("HEAD"), cancellable = true)
	private void fpsboost$freeze(CallbackInfo ci) {
		if (MobConfig.freezeAnimations()) {
			ci.cancel();
		}
	}
}
