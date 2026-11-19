package com.flyvisual.mixin;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.DeathScreenModule;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

	/** Подмена ванильных экранов смерти и главного меню на свои (если включено). */
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen flyvisual$replaceScreen(Screen screen) {
		if (screen != null && screen.getClass() == DeathScreen.class) {
			DeathScreenModule module = ModuleManager.deathScreen();
			if (module != null && module.isEnabled()) {
				return module.createScreen();
			}
		}
		// Новое главное меню вместо ванильного (если оно не отключилось из-за ошибки)
		if (screen != null && screen.getClass() == TitleScreen.class
				&& !com.flyvisual.gui.FlyTitleScreen.failed) {
			return new com.flyvisual.gui.FlyTitleScreen();
		}
		return screen;
	}
}
