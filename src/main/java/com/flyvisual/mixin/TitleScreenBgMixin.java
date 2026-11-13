package com.flyvisual.mixin;

import com.flyvisual.gui.LiveBackground;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Главное меню: вместо ванильной панорамы рисуем твою картинку. Кнопки, логотип и текст остаются ванильными. */
@Mixin(TitleScreen.class)
public class TitleScreenBgMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void flyvisual$titleBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null) return;
		int w = mc.getWindow().getScaledWidth();
		int h = mc.getWindow().getScaledHeight();
		LiveBackground.shared().render(context, w, h, mouseX, mouseY, true);
	}
}
