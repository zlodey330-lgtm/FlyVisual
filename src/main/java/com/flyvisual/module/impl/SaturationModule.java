package com.flyvisual.module.impl;

import com.flyvisual.hud.Notifications;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.util.Identifier;

/**
 * Насыщенность картинки: ярче цвета или, по приколу, чёрно-белый режим.
 * Работает через готовый ванильный шейдер color_convolve (файлы shaders/post/sat_*.json).
 * Применяется к картинке мира, интерфейс остаётся обычным. Видно только тебе.
 */
public class SaturationModule extends Module {
	private final DoubleSetting level = addSetting("Saturation %", 120.0, 50.0, 160.0, 10.0);
	private final DoubleSetting blackWhite = addToggle("Black and white", false);

	private PostEffectProcessor processor;
	private int currentLevel = -1;
	private int width;
	private int height;
	private boolean failed;

	public SaturationModule() {
		super("Saturation", "More vivid colors or black and white");
	}

	@Override
	public void onEnable() {
		failed = false;
	}

	@Override
	public void onDisable() {
		release();
	}

	private void release() {
		if (processor != null) {
			try {
				processor.close();
			} catch (Exception ignored) {
			}
			processor = null;
		}
		currentLevel = -1;
	}

	/** Вызывается из GameRendererMixin каждый кадр после отрисовки мира. */
	public void process(float tickDelta) {
		if (!isEnabled() || failed) return;
		MinecraftClient mc = mc();
		if (mc.world == null) return;

		int want = blackWhite.getBool() ? 0 : level.getInt();
		if (want == 100) {
			release();
			return;
		}

		try {
			int fw = mc.getWindow().getFramebufferWidth();
			int fh = mc.getWindow().getFramebufferHeight();
			if (processor == null || want != currentLevel) {
				release();
				processor = new PostEffectProcessor(mc.getTextureManager(), mc.getResourceManager(),
						mc.getFramebuffer(), new Identifier("flyvisual", "shaders/post/sat_" + want + ".json"));
				processor.setupDimensions(fw, fh);
				currentLevel = want;
				width = fw;
				height = fh;
			} else if (fw != width || fh != height) {
				processor.setupDimensions(fw, fh);
				width = fw;
				height = fh;
			}

			RenderSystem.disableBlend();
			RenderSystem.disableDepthTest();
			RenderSystem.resetTextureMatrix();
			processor.render(tickDelta);
		} catch (Exception e) {
			failed = true;
			processor = null;
			Notifications.push("Насыщенность: шейдер не загрузился", false);
		}
	}
}
