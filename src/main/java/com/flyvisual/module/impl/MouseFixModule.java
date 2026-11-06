package com.flyvisual.module.impl;

import com.flyvisual.module.Module;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * Фикс мыши: перед отрисовкой мира заново читает мышь и сразу поворачивает камеру, поэтому поворот
 * не отстаёт на кадр. Работает только когда кадры долгие (меньше ~45 FPS) или окно во весь экран
 * (F11), иначе ничего не меняет. Вызывается из MouseLatencyMixin. При любой ошибке сам отключается.
 */
public class MouseFixModule extends Module {
	private long lastNanos;
	private float avgMs = 16.0f;
	private boolean busy;

	public MouseFixModule() {
		super("Mouse Fix", "Reduces mouse delay when FPS drops or in fullscreen");
	}

	/** Вызывается перед отрисовкой мира каждый кадр. */
	public void beforeWorld() {
		long now = System.nanoTime();
		if (lastNanos != 0L) {
			float ms = Math.min(200.0f, (now - lastNanos) / 1.0e6f);
			avgMs += (ms - avgMs) * 0.1f;
		}
		lastNanos = now;

		if (!isEnabled() || busy) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.currentScreen != null || !client.mouse.isCursorLocked()) return;
		if (avgMs < 22.0f && !client.getWindow().isFullscreen()) return;

		busy = true;
		try {
			GLFW.glfwPollEvents();
			client.mouse.updateMouse();
		} catch (Throwable t) {
			// Что-то пошло не так: выключаем функцию, чтобы не мешать игре
			setEnabled(false);
		} finally {
			busy = false;
		}
	}
}
