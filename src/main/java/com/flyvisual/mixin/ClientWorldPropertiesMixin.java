package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.TimeChangerModule;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.Properties.class)
public class ClientWorldPropertiesMixin {

	/** Time Changer: клиентский мир берёт время суток отсюда (небо, свет, звёзды). */
	@Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
	private void flyvisual$time(CallbackInfoReturnable<Long> cir) {
		TimeChangerModule module = ModuleManager.timeChanger();
		if (module != null && module.isEnabled()) {
			cir.setReturnValue(module.getTime());
		}
	}
}
