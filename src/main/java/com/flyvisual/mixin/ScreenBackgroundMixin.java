package com.flyvisual.mixin;

import com.flyvisual.gui.LiveBackground;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Свой живой фон (картинка title_clouds) на экранах меню вне мира: список серверов, настройки, одиночная игра.
 * В игре (Esc, настройки из паузы, инвентарь и т.д.) ничего не меняется: фон ванильный.
 */
@Mixin(Screen.class)
public class ScreenBackgroundMixin {

	@Inject(method = "renderBackground(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true)
	private void flyvisual$menuBackground(DrawContext context, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.world != null) return;
		// Наши экраны рисуют фон сами
		if (this.getClass().getName().startsWith("com.flyvisual.gui.")) return;

		int w = mc.getWindow().getScaledWidth();
		int h = mc.getWindow().getScaledHeight();
		int mx = (int) (mc.mouse.getX() * w / Math.max(1, mc.getWindow().getWidth()));
		int my = (int) (mc.mouse.getY() * h / Math.max(1, mc.getWindow().getHeight()));
		LiveBackground.shared().render(context, w, h, mx, my, true);
		ci.cancel();
	}
}
