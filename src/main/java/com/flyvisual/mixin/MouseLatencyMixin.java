package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.MouseFixModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Перед отрисовкой мира заново читает мышь и сразу поворачивает камеру: поворот не отстаёт на кадр.
 * Работает только когда кадры долгие (меньше ~45 FPS) или окно во весь экран, иначе ничего не меняет.
 */
@Mixin(GameRenderer.class)
public class MouseLatencyMixin {
	@Unique
	private static long flyvisual$last;
	@Unique
	private static float flyvisual$avgMs = 16.0f;

	@Inject(method = "renderWorld", at = @At("HEAD"))
	private void flyvisual$freshMouse(CallbackInfo ci) {
		long now = System.nanoTime();
		if (flyvisual$last != 0L) {
			float ms = Math.min(200.0f, (now - flyvisual$last) / 1.0e6f);
			flyvisual$avgMs += (ms - flyvisual$avgMs) * 0.1f;
		}
		flyvisual$last = now;

		MouseFixModule fix = ModuleManager.mouseFix();
		if (fix == null || !fix.isEnabled()) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.currentScreen != null || !client.mouse.isCursorLocked()) return;
		if (flyvisual$avgMs < 22.0f && !client.getWindow().isFullscreen()) return;

		GLFW.glfwPollEvents();
		client.mouse.updateMouse();
	}
}
