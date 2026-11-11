package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.OptimizationModule;
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
		OptimizationModule opt = ModuleManager.optimization();
		if ((sky != null && sky.isRealistic()) || (opt != null && opt.skyClouds())) {
			ci.cancel();
		}
	}

	/** Оптимизация: без дождя и снега. */
	@Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
	private void flyvisual$noWeather(CallbackInfo ci) {
		OptimizationModule opt = ModuleManager.optimization();
		if (opt != null && opt.hidesWeather()) {
			ci.cancel();
		}
	}

	/** Оптимизация: без брызг дождя на земле. */
	@Inject(method = "tickRainSplashing", at = @At("HEAD"), cancellable = true)
	private void flyvisual$noRainSplash(CallbackInfo ci) {
		OptimizationModule opt = ModuleManager.optimization();
		if (opt != null && opt.hidesWeather()) {
			ci.cancel();
		}
	}

	/** Оптимизация: без трещин на ломаемом блоке. */
	@Inject(method = "setBlockBreakingInfo", at = @At("HEAD"), cancellable = true)
	private void flyvisual$noCracks(CallbackInfo ci) {
		OptimizationModule opt = ModuleManager.optimization();
		if (opt != null && opt.hidesBlockCracks()) {
			ci.cancel();
		}
	}

	/** Оптимизация: без неба, звёзд, солнца и луны. */
	@Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
	private void flyvisual$hideSky(CallbackInfo ci) {
		OptimizationModule opt = ModuleManager.optimization();
		if (opt != null && opt.skySky()) {
			ci.cancel();
		}
	}
}
