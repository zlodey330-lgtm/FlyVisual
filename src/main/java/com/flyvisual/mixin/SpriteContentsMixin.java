package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.OptimizationModule;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Оптимизация, уровни 2 и 3: текстуры блоков укрупняются в пиксели (блок 16x16 превращается
 * в 8x8 или 4x4 крупных квадратов). Срабатывает при загрузке ресурсов, поэтому смена уровня = одна перезагрузка.
 */
@Mixin(SpriteContents.class)
public class SpriteContentsMixin {
	@Shadow
	@Final
	private NativeImage image;

	@Shadow
	@Final
	private Identifier id;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void flyvisual$pixelate(CallbackInfo ci) {
		try {
			OptimizationModule opt = ModuleManager.optimization();
			if (opt == null || image == null || id == null) return;
			int target = opt.pixelTarget();
			if (target <= 0) return;
			String path = id.getPath();
			if (path.startsWith("item/") || path.startsWith("entity/")) {
				com.flyvisual.util.ImageFx.soften(image, opt.softenMix());
				return;
			}
			if (!path.startsWith("block/")) return;
			int w = image.getWidth();
			int h = image.getHeight();
			int k = w / target;
			if (k < 2 || h % k != 0 || w % k != 0) return;
			flyvisual$pixelateBlocks(w, h, k);
			if (opt.levelIndex() >= 2) com.flyvisual.util.ImageFx.soften(image, 0.5);
		} catch (Throwable ignored) {
		}
	}

	private void flyvisual$pixelateBlocks(int w, int h, int k) {
		{
			for (int by = 0; by < h; by += k) {
				for (int bx = 0; bx < w; bx += k) {
					long a = 0, r = 0, g = 0, b = 0;
					for (int y = 0; y < k; y++) {
						for (int x = 0; x < k; x++) {
							int c = image.getColor(bx + x, by + y); // порядок байт ABGR
							int al = (c >>> 24) & 255;
							a += al;
							r += (long) (c & 255) * al;
							g += (long) ((c >> 8) & 255) * al;
							b += (long) ((c >> 16) & 255) * al;
						}
					}
					int n = k * k;
					int ao = (int) (a / n);
					int ro = 0, go = 0, bo = 0;
					if (a > 0) {
						ro = (int) (r / a);
						go = (int) (g / a);
						bo = (int) (b / a);
					}
					// листва и стекло: полупрозрачные пиксели делаем либо целыми, либо прозрачными
					if (ao > 0 && ao < 255) ao = ao >= 128 ? 255 : 0;
					int out = (ao << 24) | (bo << 16) | (go << 8) | ro;
					for (int y = 0; y < k; y++) {
						for (int x = 0; x < k; x++) {
							image.setColor(bx + x, by + y, out);
						}
					}
				}
			}
		}
	}
}
