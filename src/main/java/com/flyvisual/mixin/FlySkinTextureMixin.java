package com.flyvisual.mixin;

import com.flyvisual.module.impl.SkinModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Custom Skin: подменяет твою текстуру скина на выбранную. */
@Mixin(AbstractClientPlayerEntity.class)
public class FlySkinTextureMixin {
	@Inject(method = "getSkinTexture", at = @At("HEAD"), cancellable = true, require = 0)
	private void flyvisual$skin(CallbackInfoReturnable<Identifier> cir) {
		SkinModule m = SkinModule.INSTANCE;
		if (m != null && m.isEnabled() && (Object) this == MinecraftClient.getInstance().player) {
			cir.setReturnValue(m.texture());
		}
	}
}
