package com.flyvisual.module.impl;

import com.flyvisual.mixin.SimpleOptionAccessor;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;

/**
 * Свой чат: прозрачность текста, прозрачность фона и размер.
 * Меняет настройки чата игры на время работы модуля, при выключении возвращает прежние.
 */
public class CustomChatModule extends Module {
	private final DoubleSetting textOpacity = addSetting("Text opacity", 100.0, 10.0, 100.0, 5.0);
	private final DoubleSetting backOpacity = addSetting("Background opacity", 30.0, 0.0, 100.0, 5.0);
	private final DoubleSetting chatScale = addSetting("Chat scale", 100.0, 40.0, 100.0, 5.0);

	private boolean saved;
	private double oldText;
	private double oldBack;
	private double oldScale;

	public CustomChatModule() {
		super("Custom Chat", "Chat opacity, background and size");
	}

	@Override
	public void onTick(MinecraftClient client) {
		if (client.options == null) return;
		if (!saved) {
			oldText = client.options.getChatOpacity().getValue();
			oldBack = client.options.getTextBackgroundOpacity().getValue();
			oldScale = client.options.getChatScale().getValue();
			saved = true;
		}
		apply(client.options.getChatOpacity(), textOpacity.get() / 100.0);
		apply(client.options.getTextBackgroundOpacity(), backOpacity.get() / 100.0);
		apply(client.options.getChatScale(), chatScale.get() / 100.0);
	}

	@Override
	public void onDisable() {
		restore();
	}

	/** Возвращает прежние настройки чата. */
	public void restore() {
		MinecraftClient client = mc();
		if (client == null || client.options == null || !saved) return;
		apply(client.options.getChatOpacity(), oldText);
		apply(client.options.getTextBackgroundOpacity(), oldBack);
		apply(client.options.getChatScale(), oldScale);
		saved = false;
	}

	@SuppressWarnings("unchecked")
	private static void apply(SimpleOption<Double> option, double value) {
		if (Math.abs(option.getValue() - value) > 0.004) {
			((SimpleOptionAccessor<Double>) (Object) option).flyvisual$setValue(value);
		}
	}
}
