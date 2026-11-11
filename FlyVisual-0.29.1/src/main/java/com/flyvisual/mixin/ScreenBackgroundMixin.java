package com.flyvisual.mixin;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.config.Theme;
import com.flyvisual.gui.TitleBackground;
import com.flyvisual.gui.UiShapes;
import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.PauseScreenModule;
import net.minecraft.client.gui.screen.GameMenuScreen;
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

	/** Экран паузы в игре: затемнение в цветах темы и надпись FlyVisual (поверх ванильного фона, под кнопками). */
	@Inject(method = "renderBackground(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("TAIL"))
	private void flyvisual$pauseBackground(DrawContext context, CallbackInfo ci) {
		if (!((Object) this instanceof GameMenuScreen)) return;
		PauseScreenModule m = ModuleManager.pauseScreen();
		MinecraftClient mc = MinecraftClient.getInstance();
		if (m == null || !m.isEnabled() || mc == null || mc.world == null) return;

		int w = mc.getWindow().getScaledWidth();
		int h = mc.getWindow().getScaledHeight();
		int base = 0xFF000000 | (Theme.titlePanelCol() & 0xFFFFFF);
		float t = m.tint();
		context.fillGradient(0, 0, w, h, UiShapes.mulA(base, t * 0.7f), UiShapes.mulA(base, t));
		if (m.showTitle()) {
			var ms = context.getMatrices();
			int flyW = mc.textRenderer.getWidth("Fly");
			int total = flyW + mc.textRenderer.getWidth("Visual");
			ms.push();
			ms.translate(w / 2.0f, 14.0f, 0.0f);
			ms.scale(2.5f, 2.5f, 1.0f);
			context.drawText(mc.textRenderer, "Fly", -total / 2, 0, Theme.accent(), true);
			context.drawText(mc.textRenderer, "Visual", -total / 2 + flyW, 0, 0xFFFFFFFF, true);
			ms.pop();
		}
	}
}
