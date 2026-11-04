package com.flyvisual.mixin;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.gui.FlyMenuScreen;
import com.flyvisual.gui.FlyTitleScreen;
import com.flyvisual.gui.HudEditorScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

	/**
	 * Пока открыто меню FlyVisual (или HUD-редактор), игра работает как обычно:
	 * берём лимит FPS из настроек, а не сниженный лимит "фонового режима".
	 */
	@Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
	private void flyvisual$menuFps(CallbackInfoReturnable<Integer> cir) {
		MinecraftClient self = (MinecraftClient) (Object) this;
		Screen current = self.currentScreen;
		if (current instanceof FlyMenuScreen || current instanceof HudEditorScreen) {
			cir.setReturnValue(self.getWindow().getFramerateLimit());
		}
	}
}
