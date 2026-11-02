package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.NoHurtCamModule;
import com.flyvisual.module.impl.ZoomModule;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

	/** NoHurtCam: не трясём камеру при уроне. */
	@Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
	private void flyvisual$noHurtCam(CallbackInfo ci) {
		NoHurtCamModule module = ModuleManager.noHurtCam();
		if (module != null && module.isEnabled()) {
			ci.cancel();
		}
	}

	/** Zoom: меняем поле зрения (только основное, не руку). */
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void flyvisual$zoomFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
		if (!changingFov) return;

		ZoomModule zoom = ModuleManager.zoom();
		if (zoom == null) return;

		double multiplier = zoom.getFovMultiplier();
		if (multiplier != 1.0) {
			cir.setReturnValue(cir.getReturnValue() * multiplier);
		}
	}
}
