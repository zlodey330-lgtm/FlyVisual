package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.OptimizationModule;
import com.flyvisual.util.ImageFx;
import com.flyvisual.util.ImageHolder;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Оптимизация, уровни 2 и 3: текстуры мобов (textures/entity/) слегка размываются при загрузке.
 * Класс TextureData закрытый, поэтому цель задана строкой, а картинка достаётся через свой интерфейс.
 */
@Mixin(targets = "net.minecraft.client.texture.ResourceTexture$TextureData")
public abstract class ResourceTextureDataMixin implements ImageHolder {
	@Shadow
	public abstract NativeImage getImage();

	@Override
	public NativeImage flyvisual$image() {
		return getImage();
	}

	@Inject(method = "load", at = @At("RETURN"))
	private static void flyvisual$soften(ResourceManager manager, Identifier id, CallbackInfoReturnable<Object> cir) {
		try {
			OptimizationModule opt = ModuleManager.optimization();
			if (opt == null || id == null || !id.getPath().startsWith("textures/entity/")) return;
			double mix = opt.softenMix();
			if (mix <= 0.0) return;
			Object data = cir.getReturnValue();
			if (!(data instanceof ImageHolder holder)) return;
			NativeImage image = holder.flyvisual$image();
			if (image != null) ImageFx.soften(image, mix);
		} catch (Throwable ignored) {
		}
	}
}
