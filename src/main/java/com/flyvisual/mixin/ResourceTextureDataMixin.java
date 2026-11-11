package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.OptimizationModule;
import com.flyvisual.util.ImageFx;
import com.flyvisual.util.TextureImageAccess;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Оптимизация, уровни 2 и 3: текстуры мобов (textures/entity/) слегка размываются при загрузке. */
@Mixin(ResourceTexture.class)
public class ResourceTextureDataMixin {
	@Shadow
	@Final
	protected Identifier location;

	@Inject(method = "loadTextureData", at = @At("RETURN"))
	private void flyvisual$soften(ResourceManager manager, CallbackInfoReturnable<Object> cir) {
		try {
			OptimizationModule opt = ModuleManager.optimization();
			Identifier id = this.location;
			if (opt == null || id == null || !id.getPath().startsWith("textures/entity/")) return;
			double mix = opt.softenMix();
			if (mix <= 0.0) return;
			Object data = cir.getReturnValue();
			if (!(data instanceof TextureImageAccess access)) return;
			NativeImage image = access.flyvisual$getImage();
			if (image != null) ImageFx.soften(image, mix);
		} catch (Throwable ignored) {
		}
	}
}
