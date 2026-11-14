package com.flyvisual.mixin;

import com.flyvisual.fpsboost.MobConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FPS Boost: без тумана под водой и в лаве. */
@Mixin(BackgroundRenderer.class)
public class FpsFogMixin {
	@Inject(method = "applyFog", at = @At("RETURN"))
	private static void fpsboost$noLiquidFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance,
											 boolean thickFog, float tickDelta, CallbackInfo ci) {
		if (!MobConfig.hideFog) return;
		CameraSubmersionType type = camera.getSubmersionType();
		if (type == CameraSubmersionType.WATER || type == CameraSubmersionType.LAVA) {
			RenderSystem.setShaderFogStart(100000.0f);
			RenderSystem.setShaderFogEnd(100001.0f);
		}
	}
}
