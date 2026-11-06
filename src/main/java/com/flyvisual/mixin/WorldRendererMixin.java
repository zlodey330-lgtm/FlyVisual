package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.SkyModule;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
	/** Реалистичное небо рисует свои облака, плоские ванильные тогда не нужны. */
	@Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
	private void flyvisual$hideVanillaClouds(CallbackInfo ci) {
		SkyModule sky = ModuleManager.sky();
		if (sky != null && sky.isRealistic()) {
			ci.cancel();
		}
	}
}
