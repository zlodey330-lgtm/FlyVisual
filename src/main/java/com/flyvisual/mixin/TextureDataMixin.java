package com.flyvisual.mixin;

import com.flyvisual.util.TextureImageAccess;
import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Открывает картинку закрытого класса TextureData для OptimizationModule (размытие мобов). */
@Mixin(targets = "net.minecraft.client.texture.ResourceTexture$TextureData")
public class TextureDataMixin implements TextureImageAccess {
	@Shadow
	@Final
	private NativeImage image;

	@Override
	public NativeImage flyvisual$getImage() {
		return image;
	}
}
