package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.CrosshairModule;
import com.flyvisual.module.impl.PotionHudModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {

	/** Crosshair: когда включён наш прицел, ванильный не рисуем. */
	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void flyvisual$hideVanillaCrosshair(DrawContext context, CallbackInfo ci) {
		CrosshairModule crosshair = ModuleManager.crosshair();
		if (crosshair != null && crosshair.isEnabled()) {
			ci.cancel();
		}
	}

	/** Potion Status: когда включён наш список эффектов, ванильные иконки справа сверху не рисуем. */
	@Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
	private void flyvisual$hideVanillaEffects(DrawContext context, CallbackInfo ci) {
		PotionHudModule potion = ModuleManager.potionHud();
		if (potion != null && potion.isEnabled()) {
			ci.cancel();
		}
	}
}
