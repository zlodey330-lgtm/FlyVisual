package com.flyvisual.module.impl;

import com.flyvisual.module.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;

public class PingModule extends HudModule {
	public PingModule() {
		super("Ping", "Latency to the server", 4, 40);
		accentBar = addToggle("Accent bar", true);
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = mc();
		int ping = -1;

		ClientPlayNetworkHandler handler = mc.getNetworkHandler();
		if (handler != null && mc.player != null) {
			PlayerListEntry entry = handler.getPlayerListEntry(mc.player.getUuid());
			if (entry != null) {
				ping = entry.getLatency();
			}
		}

		drawPanel(ctx, ping >= 0 ? "Ping: " + ping + " ms" : "Ping: -");
	}
}
