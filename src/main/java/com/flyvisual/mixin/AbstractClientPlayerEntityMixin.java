package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.CapesModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public class AbstractClientPlayerEntityMixin {

	/** Capes: для тебя подставляем выбранную текстуру плаща. */
	@Inject(method = "getCapeTexture", at = @At("HEAD"), cancellable = true)
	private void flyvisual$cape(CallbackInfoReturnable<Identifier> cir) {
		Identifier id = flyvisual$ownTexture();
		if (id != null) {
			cir.setReturnValue(id);
		}
	}

	@Inject(method = "canRenderCapeTexture", at = @At("HEAD"), cancellable = true)
	private void flyvisual$canCape(CallbackInfoReturnable<Boolean> cir) {
		if (flyvisual$ownTexture() != null) {
			cir.setReturnValue(true);
		}
	}

	/** Элитры: тот же плащ ложится на крылья. */
	@Inject(method = "getElytraTexture", at = @At("HEAD"), cancellable = true)
	private void flyvisual$elytra(CallbackInfoReturnable<Identifier> cir) {
		Identifier id = flyvisual$ownTexture();
		if (id != null) {
			cir.setReturnValue(id);
		}
	}

	@Inject(method = "canRenderElytraTexture", at = @At("HEAD"), cancellable = true)
	private void flyvisual$canElytra(CallbackInfoReturnable<Boolean> cir) {
		if (flyvisual$ownTexture() != null) {
			cir.setReturnValue(true);
		}
	}

	/** Текстура нашего плаща, если это именно твой игрок и модуль включён. */
	private Identifier flyvisual$ownTexture() {
		CapesModule module = ModuleManager.capes();
		if (module == null || !module.isEnabled()) return null;
		if ((Object) this != MinecraftClient.getInstance().player) return null;
		return module.getTexture();
	}
}
