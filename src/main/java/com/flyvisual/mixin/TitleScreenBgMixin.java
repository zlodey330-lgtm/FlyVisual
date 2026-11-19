package com.flyvisual.mixin;

import com.flyvisual.gui.BeamScene;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Запасной вариант: если новое главное меню отключилось из-за ошибки, у ванильного рисуем наш фон. */
@Mixin(TitleScreen.class)
public class TitleScreenBgMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void flyvisual$titleBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null) return;
		int w = mc.getWindow().getScaledWidth();
		int h = mc.getWindow().getScaledHeight();
		BeamScene.drawAuto(context, w, h, mouseX, mouseY);
	}
}
