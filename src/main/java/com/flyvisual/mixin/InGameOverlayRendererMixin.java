package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.LowerFireModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {

	/** Lower Fire: сдвигаем огонь вниз на время отрисовки, потом возвращаем матрицу. */
	@Inject(method = "renderFireOverlay", at = @At("HEAD"))
	private static void flyvisual$fireStart(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
		LowerFireModule module = ModuleManager.lowerFire();
		if (module != null && module.isEnabled()) {
			matrices.push();
			matrices.translate(0.0f, (float) -module.getOffset(), 0.0f);
		}
	}

	@Inject(method = "renderFireOverlay", at = @At("RETURN"))
	private static void flyvisual$fireEnd(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
		LowerFireModule module = ModuleManager.lowerFire();
		if (module != null && module.isEnabled()) {
			matrices.pop();
		}
	}
}
