package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.MouseFixModule;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mouse Fix: перед отрисовкой мира заново читаем мышь (логика в MouseFixModule). */
@Mixin(GameRenderer.class)
public class MouseLatencyMixin {

	@Inject(method = "renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V", at = @At("HEAD"))
	private void flyvisual$freshMouse(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci) {
		MouseFixModule fix = ModuleManager.mouseFix();
		if (fix != null) {
			fix.beforeWorld();
		}
	}
}
