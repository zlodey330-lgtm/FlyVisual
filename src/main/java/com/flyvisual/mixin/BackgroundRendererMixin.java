package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.FogModule;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {

	/** Fog: после ванильной настройки тумана меняем его дальность. Вода, лава, слепота и Незер не трогаются. */
	@Inject(method = "applyFog", at = @At("RETURN"))
	private static void flyvisual$fog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance,
									  boolean thickFog, float tickDelta, CallbackInfo ci) {
		FogModule fog = ModuleManager.fog();
		if (fog == null || !fog.isEnabled()) return;
		if (fogType != BackgroundRenderer.FogType.FOG_TERRAIN || thickFog) return;
		if (camera.getSubmersionType() != CameraSubmersionType.NONE) return;

		Entity focused = camera.getFocusedEntity();
		if (focused instanceof LivingEntity living
				&& (living.hasStatusEffect(StatusEffects.BLINDNESS) || living.hasStatusEffect(StatusEffects.DARKNESS))) {
			return;
		}

		fog.apply(viewDistance);
	}
}
