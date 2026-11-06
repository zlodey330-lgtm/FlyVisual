package com.flyvisual.mixin;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.gui.FlyTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

	/**
	 * Подмена ванильного главного меню на меню FlyVisual.
	 * Отключается в config/flyvisual.json: "customTitleScreen": false.
	 */
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen flyvisual$replaceTitleScreen(Screen screen) {
		if (screen != null && screen.getClass() == TitleScreen.class && FlyConfig.get().customTitleScreen) {
			return new FlyTitleScreen();
		}
		return screen;
	}
}
