package com.flyvisual.mixin;

import com.flyvisual.fpsboost.MobConfig;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FPS Boost: огонь на экране (когда горишь). Приоритет ниже, чем у Lower Fire, поэтому наш обработчик идёт первым
 * и при отмене Lower Fire не успевает сдвинуть матрицу.
 */
@Mixin(value = InGameOverlayRenderer.class, priority = 500)
public class FpsOverlayMixin {
	@Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
	private static void fpsboost$noScreenFire(CallbackInfo ci) {
		if (MobConfig.hideFireSelf) {
			ci.cancel();
		}
	}
}
