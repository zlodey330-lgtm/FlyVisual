package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.DeathScreenModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Death Screen: запоминаем причину смерти и дорисовываем свою панель. */
@Mixin(DeathScreen.class)
public class DeathScreenMixin {

	@Inject(method = "<init>", at = @At("RETURN"))
	private void flyvisual$captureMessage(Text message, boolean isHardcore, CallbackInfo ci) {
		DeathScreenModule module = ModuleManager.deathScreen();
		if (module != null && module.isEnabled()) {
			module.setMessage(message);
		}
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void flyvisual$info(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		DeathScreenModule module = ModuleManager.deathScreen();
		if (module != null && module.isEnabled()) {
			MinecraftClient mc = MinecraftClient.getInstance();
			module.render(context, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
		}
	}
}
