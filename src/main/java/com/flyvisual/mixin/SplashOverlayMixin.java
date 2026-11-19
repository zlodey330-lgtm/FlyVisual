package com.flyvisual.mixin;

import com.flyvisual.gui.IntroFx;
import com.flyvisual.gui.OverlaySync;
import com.flyvisual.module.impl.MenuStyleModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Живая заставка вместо экрана загрузки Mojang: запуск игры, смена ресурспака, F3+T.
 * Пока ресурсы грузятся, анимация «заряжается» и крутится (кольцо показывает прогресс), а когда загрузка закончилась,
 * ядро вспыхивает, и под анимацией проявляется экран. Логику загрузки не трогаем, только подгоняем ванильный
 * отсчёт конца заставки, чтобы анимация доиграла до конца.
 */
@Mixin(SplashOverlay.class)
public class SplashOverlayMixin {
	@Shadow
	private float progress;
	@Shadow
	private long reloadCompleteTime;
	@Shadow
	@Final
	private boolean reloading;

	@Unique
	private IntroFx flyvisual$fx = new IntroFx();
	@Unique
	private long flyvisual$startNanos = -1L;
	@Unique
	private long flyvisual$triggerNanos = -1L;
	@Unique
	private boolean flyvisual$revealSent;

	/** Ванильный логотип Mojang не рисуем. */
	@Redirect(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIFFIIII)V"), require = 0)
	private void flyvisual$noLogo(DrawContext ctx, net.minecraft.util.Identifier id, int x, int y, int w, int h,
								  float u, float v, int rw, int rh, int tw, int th) {
		if (!MenuStyleModule.liveSplash()) {
			ctx.drawTexture(id, x, y, w, h, u, v, rw, rh, tw, th);
		}
	}

	/** Ванильную красную подложку и затухание тоже не рисуем: анимация закрывает экран сама. */
	@Redirect(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/DrawContext;fill(Lnet/minecraft/client/render/RenderLayer;IIIII)V"), require = 0)
	private void flyvisual$noBackground(DrawContext ctx, RenderLayer layer, int x1, int y1, int x2, int y2, int color) {
		if (!MenuStyleModule.liveSplash()) {
			ctx.fill(layer, x1, y1, x2, y2, color);
		}
	}

	/** То же для варианта вызова с глубиной (на случай другой версии вызова в игре). */
	@Redirect(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/DrawContext;fill(Lnet/minecraft/client/render/RenderLayer;IIIIII)V"), require = 0)
	private void flyvisual$noBackgroundZ(DrawContext ctx, RenderLayer layer, int x1, int y1, int x2, int y2, int z, int color) {
		if (!MenuStyleModule.liveSplash()) {
			ctx.fill(layer, x1, y1, x2, y2, z, color);
		}
	}

	/** Ванильную полоску загрузки не рисуем: прогресс показывает кольцо вокруг ядра. */
	@Inject(method = "renderProgressBar(Lnet/minecraft/client/gui/DrawContext;IIIIF)V", at = @At("HEAD"),
			cancellable = true, require = 0)
	private void flyvisual$noBar(DrawContext ctx, int minX, int minY, int maxX, int maxY, float opacity, CallbackInfo ci) {
		if (MenuStyleModule.liveSplash()) ci.cancel();
	}

	/**
	 * В начале кадра: пока анимация не дошла до вспышки, держим ванильный отсчёт конца заставки на нуле.
	 * Потом подгоняем его, чтобы ванильная заставка закончилась ровно тогда, когда доиграет наша.
	 */
	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"), require = 0)
	private void flyvisual$hold(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!MenuStyleModule.liveSplash()) return;
		long nowNs = System.nanoTime();
		if (flyvisual$startNanos < 0L) flyvisual$startNanos = nowNs;
		if (reloadCompleteTime > -1L) {
			float ts = (nowNs - flyvisual$startNanos) / 1.0e9f;
			if (flyvisual$triggerNanos < 0L && ts >= IntroFx.CHARGE_MIN) {
				flyvisual$triggerNanos = nowNs;
			}
			long nowMs = Util.getMeasuringTimeMs();
			if (flyvisual$triggerNanos < 0L) {
				reloadCompleteTime = nowMs;
			} else {
				float c = (nowNs - flyvisual$triggerNanos) / 1.0e9f;
				// Ванильное затухание начинается в момент f = 1, а у нас вспышка в момент c = BOOM
				reloadCompleteTime = nowMs - (long) ((c + 1.0f - IntroFx.BOOM) * 1000.0f);
			}
		}
	}

	/** В конце кадра рисуем анимацию поверх всего ванильного. */
	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("RETURN"), require = 0)
	private void flyvisual$draw(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!MenuStyleModule.liveSplash()) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.getWindow() == null) return;

		long nowNs = System.nanoTime();
		if (flyvisual$startNanos < 0L) flyvisual$startNanos = nowNs;
		float ti = (nowNs - flyvisual$startNanos) / 1.0e9f;
		float trigger = flyvisual$triggerNanos < 0L ? Float.MAX_VALUE
				: (flyvisual$triggerNanos - flyvisual$startNanos) / 1.0e9f;

		int w = mc.getWindow().getScaledWidth();
		int h = mc.getWindow().getScaledHeight();
		float s = MathHelper.clamp(Math.min(w / 400.0f, h / 230.0f), 0.6f, 1.7f);
		float p = progress < 0.0f ? 0.0f : (progress > 1.0f ? 1.0f : progress);
		// При перезагрузке (ресурспак, F3+T) под заставкой видна игра: чернота появляется плавно
		flyvisual$fx.setFadeIn(reloading ? 0.35f : 0.0f);
		flyvisual$fx.draw(ctx.getMatrices(), w, h, ti, trigger, s, p);

		// В момент вспышки сообщаем меню, что можно проявляться
		if (!flyvisual$revealSent && trigger < 1.0e8f && ti >= trigger + IntroFx.BOOM) {
			flyvisual$revealSent = true;
			OverlaySync.revealNanos = nowNs;
		}
	}
}
