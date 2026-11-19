package com.flyvisual.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.RotatingCubeMapRenderer;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Отключает ванильную вращающуюся панораму на главном меню, чтобы была видна наша картинка. */
@Mixin(RotatingCubeMapRenderer.class)
public class CubeMapBgMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void flyvisual$skipPanorama(float delta, float alpha, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc != null && mc.currentScreen instanceof TitleScreen) {
			ci.cancel();
		}
	}
}
