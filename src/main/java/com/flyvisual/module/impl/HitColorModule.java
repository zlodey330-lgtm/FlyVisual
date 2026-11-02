package com.flyvisual.module.impl;

import com.flyvisual.mixin.OverlayTextureAccessor;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.ColorUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

/**
 * Свой цвет вспышки на существе, когда ему наносят урон (по умолчанию в игре красная).
 * Перекрашиваем верхнюю половину текстуры наложения: именно она отвечает за вспышку урона.
 */
public class HitColorModule extends Module {
	private static final int GL_TEXTURE0 = 33984;
	private static final int GL_TEXTURE1 = 33985;
	/** Ванильный красный (ABGR: прозрачность 178, красный 255). */
	private static final int VANILLA_ABGR = (178 << 24) | 255;

	private final DoubleSetting hue = addSetting("Color (hue)", 0.0, 0.0, 355.0, 5.0);
	private final DoubleSetting strength = addSetting("Strength", 70.0, 20.0, 100.0, 5.0);

	private int appliedAbgr = VANILLA_ABGR;

	public HitColorModule() {
		super("Hitcolor", "Custom color of the hit flash");
	}

	@Override
	public void onTick(MinecraftClient client) {
		int rgb = ColorUtil.hsv(hue.getFloat(), 1.0f, 1.0f);
		int alpha = Math.round(strength.getFloat() * 2.55f);
		int abgr = (alpha << 24) | ((rgb & 0xFF) << 16) | (((rgb >> 8) & 0xFF) << 8) | ((rgb >> 16) & 0xFF);
		if (abgr != appliedAbgr) {
			apply(client, abgr);
		}
	}

	@Override
	public void onDisable() {
		MinecraftClient client = mc();
		if (client != null && appliedAbgr != VANILLA_ABGR) {
			apply(client, VANILLA_ABGR);
		}
	}

	private void apply(MinecraftClient client, int abgr) {
		if (client.gameRenderer == null) return;

		NativeImageBackedTexture texture =
				((OverlayTextureAccessor) client.gameRenderer.getOverlayTexture()).flyvisual$getTexture();
		NativeImage image = texture.getImage();
		if (image == null) return;

		// Строки 0..7 отвечают за вспышку урона, строки 8..15 за белую вспышку (крипер)
		for (int row = 0; row < 8; row++) {
			for (int col = 0; col < 16; col++) {
				image.setColor(col, row, abgr);
			}
		}

		RenderSystem.activeTexture(GL_TEXTURE1);
		texture.upload();
		RenderSystem.activeTexture(GL_TEXTURE0);

		appliedAbgr = abgr;
	}
}
