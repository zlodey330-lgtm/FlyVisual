package com.flyvisual.module.impl;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

/**
 * Макросы: 8 клавиш, каждая отправляет в чат своё сообщение или команду (если начинается с "/").
 * Текст и клавиша задаются прямо в меню модуля.
 */
public class MacroModule extends Module {
	public static final int COUNT = 8;

	private final boolean[] down = new boolean[COUNT];

	public MacroModule() {
		super("Macro Keybinds", "Keys that send chat messages or commands");
	}

	@Override
	public void onTick(MinecraftClient client) {
		int[] keys = FlyConfig.get().macroKeys;
		if (keys == null) return;
		long window = client.getWindow().getHandle();
		boolean canSend = client.player != null && client.currentScreen == null;
		for (int i = 0; i < COUNT && i < keys.length; i++) {
			int code = keys[i];
			boolean pressed = false;
			if (code > 0 && code < 1000) {
				pressed = InputUtil.isKeyPressed(window, code);
			} else if (code >= 1000) {
				pressed = org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, code - 1000) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
			}
			if (pressed && !down[i] && canSend) {
				run(client, i);
			}
			down[i] = pressed;
		}
	}

	private void run(MinecraftClient client, int index) {
		String[] macros = FlyConfig.get().macros;
		if (macros == null || index >= macros.length || macros[index] == null) return;
		String text = macros[index].trim();
		if (text.isEmpty() || client.player == null) return;

		if (text.startsWith("/")) {
			client.player.networkHandler.sendChatCommand(text.substring(1));
		} else {
			client.player.networkHandler.sendChatMessage(text);
		}
	}
}
