package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.DeathScreenModule;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Death Screen: запоминаем причину смерти из ванильного экрана, который потом заменяется на наш. */
@Mixin(DeathScreen.class)
public class DeathScreenMixin {

	@Inject(method = "<init>", at = @At("RETURN"))
	private void flyvisual$captureMessage(Text message, boolean isHardcore, CallbackInfo ci) {
		DeathScreenModule module = ModuleManager.deathScreen();
		if (module != null && module.isEnabled()) {
			module.setMessage(message, isHardcore);
		}
	}
}
