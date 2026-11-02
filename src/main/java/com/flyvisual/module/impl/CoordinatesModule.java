package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;

public class CoordinatesModule extends HudModule {
	private final DoubleSetting decimals;

	public CoordinatesModule() {
		super("Coordinates", "Your XYZ position", 4, 58);
		decimals = addSetting("Decimals", 1, 0, 2, 1);
		accentBar = addToggle("Accent bar", true);
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = mc();
		if (mc.player == null) return;

		String f = "%." + (int) decimals.get() + "f";
		String text = String.format(Locale.ROOT, "XYZ: " + f + " / " + f + " / " + f,
				mc.player.getX(), mc.player.getY(), mc.player.getZ());
		drawPanel(ctx, text);
	}
}
