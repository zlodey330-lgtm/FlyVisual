package com.flyvisual.mixin;

import com.flyvisual.config.Theme;
import com.flyvisual.gui.LiveBackground;
import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.PauseScreenModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Свой живой фон (картинка title_clouds) на всех экранах меню: вне мира (список серверов, настройки, одиночная игра)
 * и в игре на экране паузы (Esc) и экранах настроек, открытых из паузы. Так при входе на сервер и нажатии Esc
 * фон остаётся тем же, а не ванильным.
 */
@Mixin(Screen.class)
public class ScreenBackgroundMixin {

	@Inject(method = "renderBackground(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true)
	private void flyvisual$menuBackground(DrawContext context, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null) return;
		// Наши экраны рисуют фон сами
		String cls = this.getClass().getName();
		if (cls.startsWith("com.flyvisual.gui.")) return;

		boolean pause = (Object) this instanceof GameMenuScreen;
		if (mc.world != null) {
			// В мире: только пауза и настройки из неё
			if (!pause && !cls.startsWith("net.minecraft.client.gui.screen.option.")) return;
		}

		int w = mc.getWindow().getScaledWidth();
		int h = mc.getWindow().getScaledHeight();
		int mx = (int) (mc.mouse.getX() * w / Math.max(1, mc.getWindow().getWidth()));
		int my = (int) (mc.mouse.getY() * h / Math.max(1, mc.getWindow().getHeight()));
		LiveBackground.shared().render(context, w, h, mx, my, true);

		if (pause) {
			PauseScreenModule m = ModuleManager.pauseScreen();
			if (m != null && m.showTitle()) {
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
		ci.cancel();
	}
}
