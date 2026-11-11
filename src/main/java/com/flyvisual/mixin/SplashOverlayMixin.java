package com.flyvisual.mixin;

import com.flyvisual.config.Theme;
import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.LoadingScreenModule;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.IntSupplier;

/**
 * Свой экран загрузки. Красный фон Mojang заменяется нашим цветом (с тем же плавным уходом),
 * логотип Mojang не рисуется, вместо него пишем FlyVisual с той же прозрачностью, что у логотипа.
 * Полоску прогресса оставляем ванильную.
 */
@Mixin(SplashOverlay.class)
public class SplashOverlayMixin {
	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
	private int flyvisual$background(IntSupplier original) {
		LoadingScreenModule m = ModuleManager.loadingScreen();
		if (m != null && m.isEnabled()) {
			return m.background();
		}
		return original.getAsInt();
	}

	/** Первая половина логотипа Mojang: вместо неё рисуем свою надпись. */
	@Redirect(method = "render", at = @At(value = "INVOKE", ordinal = 0,
			target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIFFIIII)V"))
	private void flyvisual$logoLeft(DrawContext ctx, Identifier texture, int x, int y, int w, int h,
									 float u, float v, int rw, int rh, int tw, int th) {
		LoadingScreenModule m = ModuleManager.loadingScreen();
		if (m == null || !m.isEnabled()) {
			ctx.drawTexture(texture, x, y, w, h, u, v, rw, rh, tw, th);
			return;
		}
		if (!m.showTitle()) return;
		float alpha = RenderSystem.getShaderColor()[3];
		if (alpha < 0.05f) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		int a = Math.round(Math.min(1.0f, alpha) * 255.0f) << 24;
		int sw = mc.getWindow().getScaledWidth();
		int sh = mc.getWindow().getScaledHeight();
		float scale = 3.0f;
		int flyW = mc.textRenderer.getWidth("Fly");
		int total = flyW + mc.textRenderer.getWidth("Visual");
		MatrixStack ms = ctx.getMatrices();
		ms.push();
		ms.translate(sw / 2.0f, sh / 2.0f - 14.0f, 0.0f);
		ms.scale(scale, scale, 1.0f);
		ctx.drawText(mc.textRenderer, "Fly", -total / 2, 0, a | (Theme.accent() & 0xFFFFFF), true);
		ctx.drawText(mc.textRenderer, "Visual", -total / 2 + flyW, 0, a | 0xFFFFFF, true);
		ms.pop();
	}

	/** Вторая половина логотипа: при нашем экране не рисуется. */
	@Redirect(method = "render", at = @At(value = "INVOKE", ordinal = 1,
			target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIFFIIII)V"))
	private void flyvisual$logoRight(DrawContext ctx, Identifier texture, int x, int y, int w, int h,
									  float u, float v, int rw, int rh, int tw, int th) {
		LoadingScreenModule m = ModuleManager.loadingScreen();
		if (m == null || !m.isEnabled()) {
			ctx.drawTexture(texture, x, y, w, h, u, v, rw, rh, tw, th);
		}
	}
}
