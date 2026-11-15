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
import org.spongepowered.asm.mixin.injection.Redirect;
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

	/** Ванильный логотип Mojang вообще не рисуем (иначе он мелькает на переходах). */
	@Redirect(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIFFIIII)V"), require = 0)
	private void flyvisual$noLogo(DrawContext ctx, net.minecraft.util.Identifier id, int x, int y, int w, int h,
								  float u, float v, int rw, int rh, int tw, int th) {
	}

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

		long ms = now;
		int ta = Math.max(4, a);

		if (!reloading) {
			// Первая загрузка игры: приветствие и кружок
			String name = mc.getSession() != null ? mc.getSession().getUsername() : "";
			String hello = "Приветствую, " + name;
			String sub = "Во FlyVisual";
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

			int cx = w / 2;
			int cy = h - 40;
			int n = 28;
			float p = Math.max(0.0f, Math.min(1.0f, progress));
			float spin = (ms % 1400L) / 1400.0f * n;
			for (int i = 0; i < n; i++) {
				double ang = i * Math.PI * 2.0 / n - Math.PI / 2.0;
				int dx = (int) Math.round(Math.cos(ang) * 11.0);
				int dy = (int) Math.round(Math.sin(ang) * 11.0);
				boolean done = i < p * n;
				float trail = ((spin - i) % n + n) % n;
				float glow = Math.max(0.0f, 1.0f - trail / 6.0f);
				int base = done ? 190 : 60;
				int c = Math.min(255, base + Math.round(65 * glow));
				int da = Math.round(a * (0.45f + 0.55f * (done ? 1.0f : glow)));
				ctx.fill(cx + dx - 1, cy + dy - 1, cx + dx + 1, cy + dy + 1, (da << 24) | (c << 16) | (c << 8) | c);
			}
		} else {
			// Перезагрузка (ресурспак, F3+T): FlyVisual и «Загрузка» с плавными точками
			String title = "FlyVisual";
			ctx.getMatrices().push();
			ctx.getMatrices().translate(w / 2.0f, h / 2.0f - 8, 0);
			ctx.getMatrices().scale(2.5f, 2.5f, 1.0f);
			ctx.drawText(tr, title, -tr.getWidth(title) / 2, 0, (ta << 24) | 0xEDEDED, false);
			ctx.getMatrices().pop();

			String word = "Загрузка";
			int dotsW = tr.getWidth("...");
			int x0 = (w - tr.getWidth(word) - dotsW) / 2;
			int y0 = h - 40;
			ctx.drawText(tr, word, x0, y0, (ta << 24) | 0xB4B4BA, false);
			int dx = x0 + tr.getWidth(word);
			int dw = tr.getWidth(".");
			for (int i = 0; i < 3; i++) {
				double ph = (ms / 1000.0) * 2.4 - i * 0.55;
				float k = (float) (0.5 + 0.5 * Math.sin(ph * Math.PI));
				int da = Math.max(4, Math.round(a * k));
				ctx.drawText(tr, ".", dx + i * dw, y0, (da << 24) | 0xB4B4BA, false);
			}
		}
	}
}
