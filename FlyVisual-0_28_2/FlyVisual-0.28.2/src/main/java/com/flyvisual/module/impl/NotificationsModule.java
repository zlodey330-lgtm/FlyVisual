package com.flyvisual.module.impl;

import com.flyvisual.config.Theme;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Уведомления («модуль включён», «выключен», предупреждение о броне): угол экрана, время показа,
 * размер и цвета. Плашки плавно выезжают и плавно уходят. Если модуль выключить, уведомлений не будет совсем.
 */
public class NotificationsModule extends Module {
	/** Порядок: верх-лево, верх-право, низ-право, низ-лево. */
	private static final String[] CORNERS = {"Сверху слева", "Сверху справа", "Снизу справа", "Снизу слева"};

	private final DoubleSetting corner = addChoice("Notification corner", 1, CORNERS);
	private final DoubleSetting duration = addSetting("Notification time", 2.5, 1.0, 8.0, 0.5);
	private final DoubleSetting scale = addSetting("Notification scale", 1.0, 0.7, 1.6, 0.1);
	private static final String[] SOUNDS = {"Нет", "Динь", "Колокольчик", "Хрусталь", "Щелчок", "Флейта"};
	private static final String[] SOUND_IDS = {
			null, "block.note_block.pling", "block.note_block.bell", "block.amethyst_block.chime",
			"ui.button.click", "block.note_block.flute"
	};
	private static final SoundEvent[] EVENTS = new SoundEvent[SOUND_IDS.length];

	private final DoubleSetting sound = addChoice("Notification sound", 1, SOUNDS);
	private final DoubleSetting volume = addSetting("Notification volume", 50.0, 10.0, 100.0, 10.0);
	private final DoubleSetting custom = addToggle("Custom notification colors", false);
	private final DoubleSetting onHue = addSetting("Enabled color (hue)", 130.0, 0.0, 355.0, 5.0);
	private final DoubleSetting offHue = addSetting("Disabled color (hue)", 0.0, 0.0, 355.0, 5.0);
	private final DoubleSetting warnHue = addSetting("Warning color (hue)", 35.0, 0.0, 355.0, 5.0);

	public NotificationsModule() {
		super("Notifications", "Pop-up messages: corner, time, size and colors");
	}

	@Override
	protected boolean enabledByDefault() {
		return true;
	}

	/** 0 верх-лево, 1 верх-право, 2 низ-право, 3 низ-лево. */
	public int corner() {
		return corner.getInt();
	}

	public long lifetimeMs() {
		return Math.round(duration.get() * 1000.0);
	}

	public float scale() {
		return scale.getFloat();
	}

	/** Звук уведомления: включение выше, выключение ниже, предупреждение низкое. kind: 0 выкл, 1 вкл, 2 предупреждение. */
	public void playSound(int kind) {
		if (!isEnabled()) return;
		int i = sound.getInt();
		if (i <= 0 || i >= SOUND_IDS.length) return;
		try {
			if (EVENTS[i] == null) {
				EVENTS[i] = SoundEvent.of(new Identifier(SOUND_IDS[i]));
			}
			float pitch = kind == 1 ? 1.25f : kind == 0 ? 0.85f : 0.65f;
			MinecraftClient.getInstance().getSoundManager().play(
					PositionedSoundInstance.master(EVENTS[i], pitch, volume.getFloat() / 100.0f));
		} catch (Throwable ignored) {
		}
	}

	/** Цвет полоски: kind 0 = выключено, 1 = включено, 2 = предупреждение. */
	public int barColor(int kind) {
		if (custom.getBool()) {
			float h = kind == 1 ? onHue.getFloat() : kind == 0 ? offHue.getFloat() : warnHue.getFloat();
			return 0xFF000000 | ColorUtil.hsv(h, 0.85f, 1.0f);
		}
		return kind == 1 ? Theme.accent() : kind == 0 ? 0xFF888888 : 0xFFE8943A;
	}
}
