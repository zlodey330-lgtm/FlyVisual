package com.flyvisual.module.impl;

import com.flyvisual.module.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ServerInfo;

/** Адрес сервера, на котором ты играешь. */
public class ServerIpModule extends HudModule {
	public ServerIpModule() {
		super("Server IP", "Address of the current server", 4, 60);
	}

	@Override
	protected boolean enabledByDefault() {
		return false;
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = mc();
		if (mc.player == null) return;
		ServerInfo info = mc.getCurrentServerEntry();
		String text = info != null ? info.address : "Одиночная игра";
		drawPanel(ctx, text);
	}
}
