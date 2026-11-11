package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;

/**
 * Плавный чат: новое сообщение не «прыгает», а мягко выезжает снизу, остальные строки плавно поднимаются.
 * Сама анимация делается в ChatHudMixin, здесь только время и настройки.
 */
public class SmoothChatModule extends Module {
	private final DoubleSetting duration = addSetting("Duration ms", 220.0, 80.0, 600.0, 20.0);

	private volatile long lastMessage;

	public SmoothChatModule() {
		super("Smooth Chat", "Smooth new chat messages");
	}

	/** Пришло новое сообщение. */
	public void onMessage() {
		lastMessage = System.nanoTime();
	}

	/** Остаток смещения от 1 (только что пришло) до 0 (анимация закончилась), с плавным замедлением. */
	public float remaining() {
		if (!isEnabled() || lastMessage == 0) return 0.0f;
		double ms = (System.nanoTime() - lastMessage) / 1.0e6;
		double t = ms / Math.max(1.0, duration.get());
		if (t >= 1.0) return 0.0f;
		double k = 1.0 - t;
		return (float) (k * k * k);
	}
}
