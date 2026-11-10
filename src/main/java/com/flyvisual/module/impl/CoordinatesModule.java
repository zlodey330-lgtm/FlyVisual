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

	private String cachedText;
	private long lx, ly, lz;
	private int ld = -1;

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = mc();
		if (mc.player == null) return;

		int d = (int) decimals.get();
		double scale = d == 0 ? 1.0 : d == 1 ? 10.0 : 100.0;
		long qx = Math.round(mc.player.getX() * scale);
		long qy = Math.round(mc.player.getY() * scale);
		long qz = Math.round(mc.player.getZ() * scale);
		if (cachedText == null || qx != lx || qy != ly || qz != lz || d != ld) {
			lx = qx;
			ly = qy;
			lz = qz;
			ld = d;
			String f = "%." + d + "f";
			cachedText = String.format(Locale.ROOT, "XYZ: " + f + " / " + f + " / " + f,
					mc.player.getX(), mc.player.getY(), mc.player.getZ());
		}
		drawPanel(ctx, cachedText);
	}
}
