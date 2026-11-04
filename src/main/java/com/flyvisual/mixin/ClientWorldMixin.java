package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.SkyModule;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {

	/** Sky: меняем цвет неба. */
	@Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
	private void flyvisual$sky(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
		SkyModule sky = ModuleManager.sky();
		if (sky != null && sky.isEnabled()) {
			cir.setReturnValue(sky.applySky(cir.getReturnValue()));
		}
	}

	/** Sky: красим облака в тон небу. */
	@Inject(method = "getCloudsColor", at = @At("RETURN"), cancellable = true)
	private void flyvisual$clouds(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
		SkyModule sky = ModuleManager.sky();
		if (sky != null && sky.isEnabled()) {
			cir.setReturnValue(sky.applyClouds(cir.getReturnValue()));
		}
	}
}
