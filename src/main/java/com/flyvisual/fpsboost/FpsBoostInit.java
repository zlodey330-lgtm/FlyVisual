package com.flyvisual.fpsboost;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Запуск FPS Boost внутри FlyVisual: загрузка настроек и клавиши (меню на H, очистка памяти без клавиши). */
public final class FpsBoostInit {
	public static final Logger LOGGER = LoggerFactory.getLogger("flyvisual-fpsboost");
	private static boolean done;
	private static KeyBinding openMenuKey;
	private static KeyBinding cleanMemoryKey;

	private FpsBoostInit() {
	}

	public static void init() {
		if (done) return;
		done = true;
		try {
			MobConfig.load();
		} catch (Throwable e) {
			LOGGER.warn("FPS Boost: не удалось загрузить настройки", e);
		}
		try {
			openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("FPS Boost: меню",
					InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "FlyVisual"));
			cleanMemoryKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("FPS Boost: очистить память",
					InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "FlyVisual"));
			ClientTickEvents.END_CLIENT_TICK.register(client -> {
				while (openMenuKey.wasPressed()) {
					if (client.currentScreen == null) {
						client.setScreen(new FpsBoostScreen(null));
					}
				}
				while (cleanMemoryKey.wasPressed()) {
					MemoryCleaner.run();
				}
			});
		} catch (Throwable e) {
			LOGGER.warn("FPS Boost: не удалось зарегистрировать клавиши", e);
		}
	}
}
