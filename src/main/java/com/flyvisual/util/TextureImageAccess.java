package com.flyvisual.util;

import net.minecraft.client.texture.NativeImage;

/** Доступ к картинке внутри закрытого класса ResourceTexture.TextureData (реализуется миксином). */
public interface TextureImageAccess {
	NativeImage flyvisual$getImage();
}
