package com.flyvisual.mixin;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.gui.TitleBackground;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Вне мира (одиночная игра, сетевая игра, настройки и другие экраны) вместо земляного фона рисуем тот же живой
 * фон, что и в главном меню FlyVisual. В мире ничего не меняется.
 */
@Mixin(Screen.class)
public class ScreenBackgroundMixin {

	@Inject(method = "renderBackground(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true)
	private void flyvisual$menuBackground(DrawContext context, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.world != null || !FlyConfig.get().customTitleScreen) return;
		// Наши экраны рисуют фон сами
		if (this.getClass().getName().startsWith("com.flyvisual.gui.")) return;

		int w = mc.getWindow().getScaledWidth();
		int h = mc.getWindow().getScaledHeight();
		int mx = (int) (mc.mouse.getX() * w / Math.max(1, mc.getWindow().getWidth()));
		int my = (int) (mc.mouse.getY() * h / Math.max(1, mc.getWindow().getHeight()));
		TitleBackground.shared().render(context, w, h, mx, my, true);
		ci.cancel();
	}
}
