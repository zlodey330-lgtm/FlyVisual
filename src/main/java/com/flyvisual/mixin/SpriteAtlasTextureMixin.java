package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.OptimizationModule;
import net.minecraft.client.texture.SpriteAtlasTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Оптимизация: замороженные анимации воды, лавы, портала и огня. */
@Mixin(SpriteAtlasTexture.class)
public class SpriteAtlasTextureMixin {
	@Inject(method = "tickAnimatedSprites", at = @At("HEAD"), cancellable = true)
	private void flyvisual$freeze(CallbackInfo ci) {
		OptimizationModule opt = ModuleManager.optimization();
		if (opt != null && opt.freezesAnimations()) {
			ci.cancel();
		}
	}
}
