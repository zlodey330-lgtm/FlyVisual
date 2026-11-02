package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.ZoomModule;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {

	/** Во время зума колёсико меняет приближение, а не листает хотбар. */
	@Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
	private void flyvisual$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		ZoomModule zoom = ModuleManager.zoom();
		if (zoom != null && zoom.onScroll(vertical)) {
			ci.cancel();
		}
	}
}
