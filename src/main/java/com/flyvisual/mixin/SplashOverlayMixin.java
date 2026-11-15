package com.flyvisual.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Экран загрузки (Mojang и перезагрузка ресурспака): серый фон, «Приветствую, ник», «Во FlyVisual»
 * и полоска загрузки снизу. Логику загрузки не трогаем, просто рисуем поверх ванильного кадра.
 */
@Mixin(SplashOverlay.class)
public class SplashOverlayMixin {
	@Shadow
	private float progress;
	@Shadow
	private long reloadCompleteTime;
	@Shadow
	private long reloadStartTime;
	@Shadow
	@Final
	private boolean reloading;

	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("RETURN"), require = 0)
	private void flyvisual$splash(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.getWindow() == null) return;
		long now = Util.getMeasuringTimeMs();
		float alpha = 1.0f;
		if (reloadCompleteTime > -1L) {
			float g = (now - reloadCompleteTime) / 1000.0f;
			alpha = 1.0f - Math.max(0.0f, Math.min(1.0f, g - 1.0f));
		}
		if (reloading && reloadStartTime > -1L) {
			alpha = Math.min(alpha, Math.max(0.0f, Math.min(1.0f, (now - reloadStartTime) / 500.0f)));
		}
		int a = Math.round(255 * alpha);
		if (a <= 2) return;

		int w = mc.getWindow().getScaledWidth();
		int h = mc.getWindow().getScaledHeight();
		TextRenderer tr = mc.textRenderer;
		ctx.fill(0, 0, w, h, (a << 24) | 0x1F1F23);

		String name = mc.getSession() != null ? mc.getSession().getUsername() : "";
		String hello = "Приветствую, " + name;
		String sub = "Во FlyVisual";
		int ta = Math.max(4, a);

		ctx.getMatrices().push();
		ctx.getMatrices().translate(w / 2.0f, h / 2.0f - 22, 0);
		ctx.getMatrices().scale(2.0f, 2.0f, 1.0f);
		ctx.drawText(tr, hello, -tr.getWidth(hello) / 2, 0, (ta << 24) | 0xEDEDED, false);
		ctx.getMatrices().pop();

		ctx.getMatrices().push();
		ctx.getMatrices().translate(w / 2.0f, h / 2.0f + 4, 0);
		ctx.getMatrices().scale(1.5f, 1.5f, 1.0f);
		ctx.drawText(tr, sub, -tr.getWidth(sub) / 2, 0, (ta << 24) | 0x9A9AA2, false);
		ctx.getMatrices().pop();

		int bw = Math.min(w - 60, 260);
		int bx = (w - bw) / 2;
		int by = h - 34;
		ctx.fill(bx, by, bx + bw, by + 4, ((a * 2 / 5) << 24) | 0x808088);
		int fill = Math.round(bw * Math.max(0.0f, Math.min(1.0f, progress)));
		ctx.fill(bx, by, bx + fill, by + 4, (a << 24) | 0xD8D8DE);
	}
}
