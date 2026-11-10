package com.flyvisual.util;

import net.minecraft.client.texture.NativeImage;

/** Даёт достать картинку из закрытого класса TextureData (подмешивается миксином). */
public interface ImageHolder {
	NativeImage flyvisual$image();
}
