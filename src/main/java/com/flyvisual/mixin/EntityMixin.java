package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.FreeLookModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {

	/** FreeLook: пока он активен, мышь крутит камеру, а не самого игрока. */
	@Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
	private void flyvisual$freeLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
		FreeLookModule freeLook = ModuleManager.freeLook();
		if (freeLook != null && freeLook.isActive() && (Object) this == MinecraftClient.getInstance().player) {
			freeLook.look(cursorDeltaX, cursorDeltaY);
			ci.cancel();
		}
	}
}
