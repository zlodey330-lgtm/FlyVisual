package com.flyvisual.module.impl;

import com.flyvisual.module.HudModule;
import net.minecraft.client.gui.DrawContext;

public class FpsModule extends HudModule {
	private int frames;
	private int fps;
	private long lastUpdate = System.currentTimeMillis();

	public FpsModule() {
		super("FPS", "Frames per second", 4, 22);
		accentBar = addToggle("Accent bar", true);
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		// Считаем кадры сами, чтобы не зависеть от внутренних полей игры
		frames++;
		long now = System.currentTimeMillis();
		if (now - lastUpdate >= 1000) {
			fps = frames;
			frames = 0;
			lastUpdate = now;
		}
		drawPanel(ctx, "FPS: " + fps);
	}
}
