package com.flyvisual.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Пока открыто меню FlyVisual (или его окно выбора, HUD-редактор), лимит кадров всегда равен тому,
 * что ты выставил в настройках «Макс. частота кадров». Игра и моды для снижения FPS
 * («фоновый режим», «меню», «нет действий») на это окно не действуют.
 *
 * Приоритет 1: наш обработчик срабатывает раньше остальных и сразу возвращает значение.
 */
@Mixin(value = MinecraftClient.class, priority = 1)
public class FpsLimitMixin {

	@Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
	private void flyvisual$fullFps(CallbackInfoReturnable<Integer> cir) {
		MinecraftClient client = MinecraftClient.getInstance();
		Screen screen = client.currentScreen;
		if (screen != null && screen.getClass().getName().startsWith("com.flyvisual.gui.")) {
			cir.setReturnValue(client.options.getMaxFps().getValue());
		}
	}
}
