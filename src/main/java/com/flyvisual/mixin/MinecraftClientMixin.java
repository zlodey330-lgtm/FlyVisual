package com.flyvisual.mixin;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.DeathScreenModule;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

	/** Подмена ванильного экрана смерти на свой (если включён модуль). */
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen flyvisual$replaceScreen(Screen screen) {
		if (screen != null && screen.getClass() == DeathScreen.class) {
			DeathScreenModule module = ModuleManager.deathScreen();
			if (module != null && module.isEnabled()) {
				return module.createScreen();
			}
		}
		return screen;
	}
}
