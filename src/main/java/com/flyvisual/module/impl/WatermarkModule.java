package com.flyvisual.module.impl;

import com.flyvisual.config.Theme;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public class WatermarkModule extends HudModule {
	private final DoubleSetting showName;

	public WatermarkModule() {
		super("Watermark", "FlyVisual logo with your nickname", 4, 4);
		showName = addToggle("Show nickname", true);
		accentBar = addToggle("Accent bar", true);
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = mc();
		TextRenderer tr = mc.textRenderer;

		String fly = "Fly";
		String visual = "Visual";
		String info = showName.getBool() ? " | " + mc.getSession().getUsername() : "";

		int w = tr.getWidth(fly) + tr.getWidth(visual) + tr.getWidth(info) + 12;
		boxW = w;
		boxH = PANEL_HEIGHT;

		ctx.fill(x, y, x + w, y + PANEL_HEIGHT, bg());
		if (showAccentBar()) {
			ctx.fill(x, y, x + 2, y + PANEL_HEIGHT, Theme.accent());
		}

		int tx = x + 6;
		int ty = y + 3;
		ctx.drawText(tr, fly, tx, ty, Theme.accent(), true);
		tx += tr.getWidth(fly);
		ctx.drawText(tr, visual, tx, ty, Theme.TEXT, true);
		tx += tr.getWidth(visual);
		if (!info.isEmpty()) {
			ctx.drawText(tr, info, tx, ty, Theme.TEXT_DIM, true);
		}
	}
}
