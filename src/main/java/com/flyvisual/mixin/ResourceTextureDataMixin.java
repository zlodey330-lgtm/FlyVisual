package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.OptimizationModule;
import com.flyvisual.util.ImageFx;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Оптимизация, уровни 2 и 3: текстуры мобов (textures/entity/) слегка размываются при загрузке. */
@Mixin(ResourceTexture.TextureData.class)
public class ResourceTextureDataMixin {
	@Inject(method = "load", at = @At("RETURN"))
	private static void flyvisual$soften(ResourceManager manager, Identifier id,
										 CallbackInfoReturnable<ResourceTexture.TextureData> cir) {
		try {
			OptimizationModule opt = ModuleManager.optimization();
			if (opt == null || id == null || !id.getPath().startsWith("textures/entity/")) return;
			double mix = opt.softenMix();
			if (mix <= 0.0) return;
			ResourceTexture.TextureData data = cir.getReturnValue();
			if (data == null) return;
			NativeImage image = data.getImage();
			ImageFx.soften(image, mix);
		} catch (Throwable ignored) {
		}
	}
}
