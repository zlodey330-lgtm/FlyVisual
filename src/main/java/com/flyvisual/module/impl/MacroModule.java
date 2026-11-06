package com.flyvisual.module.impl;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.module.Module;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Макросы: 8 клавиш, каждая отправляет в чат своё сообщение или команду (если начинается с "/").
 * Клавиши назначаются в Управление, категория FlyVisual. Тексты задаются в меню модуля.
 */
public class MacroModule extends Module {
	public static final int COUNT = 8;

	private final KeyBinding[] keys = new KeyBinding[COUNT];

	public MacroModule() {
		super("Macro Keybinds", "Keys that send chat messages or commands");
		for (int i = 0; i < COUNT; i++) {
			keys[i] = KeyBindingHelper.registerKeyBinding(new KeyBinding(
					"key.flyvisual.macro" + (i + 1),
					InputUtil.Type.KEYSYM,
					GLFW.GLFW_KEY_UNKNOWN,
					"category.flyvisual"
			));
		}
	}

	@Override
	public void onTick(MinecraftClient client) {
		boolean canSend = client.player != null && client.currentScreen == null;
		for (int i = 0; i < COUNT; i++) {
			while (keys[i].wasPressed()) {
				if (canSend) {
					run(client, i);
				}
			}
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
