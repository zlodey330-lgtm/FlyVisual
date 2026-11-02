package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.mojang.blaze3d.systems.RenderSystem;

/** Настройка тумана на земле: убрать совсем или сделать ближе/дальше. Логика в BackgroundRendererMixin. */
public class FogModule extends Module {
	private final DoubleSetting removeFog = addToggle("Remove fog", false);
	private final DoubleSetting distance = addSetting("Distance", 1.0, 0.3, 3.0, 0.1);

	public FogModule() {
		super("Fog", "Change or remove the distance fog");
	}

	/** Вызывается из миксина после того, как игра выставила свой туман. */
	public void apply(float viewDistance) {
		if (removeFog.getBool()) {
			RenderSystem.setShaderFogStart(viewDistance * 8.0f);
			RenderSystem.setShaderFogEnd(viewDistance * 16.0f);
		} else {
			float k = distance.getFloat();
			RenderSystem.setShaderFogStart(RenderSystem.getShaderFogStart() * k);
			RenderSystem.setShaderFogEnd(RenderSystem.getShaderFogEnd() * k);
		}
	}
}
