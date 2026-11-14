package com.flyvisual.mixin;

import com.flyvisual.fpsboost.MobConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FPS Boost: тыква на экране и искажение от портала. */
@Mixin(InGameHud.class)
public class FpsHudMixin {
	@Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
	private void fpsboost$noPumpkin(CallbackInfo ci) {
		if (!MobConfig.hidePumpkin) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null && mc.player.getInventory().getArmorStack(3).isOf(Items.CARVED_PUMPKIN)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
	private void fpsboost$noPortal(CallbackInfo ci) {
		if (MobConfig.hidePortalOverlay) {
			ci.cancel();
		}
	}
}
