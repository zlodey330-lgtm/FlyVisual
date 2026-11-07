package com.flyvisual;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.gui.FlyMenuScreen;
import com.flyvisual.hud.Notifications;
import com.flyvisual.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class FlyVisualClient implements ClientModInitializer {
	public static final String MOD_ID = "flyvisual";

	private static KeyBinding menuKey;

	@Override
	public void onInitializeClient() {
		FlyConfig.load();
		ModuleManager.init();
		com.flyvisual.util.GreenGrassPack.register();

		menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.flyvisual.menu",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				"category.flyvisual"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (menuKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new FlyMenuScreen());
				}
			}
			ModuleManager.tick(client);
		});

		HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.player == null || mc.options.hudHidden) return;

			ModuleManager.renderHud(ctx, tickDelta);
			Notifications.render(ctx);
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ModuleManager.shutdown());
	}
}
