package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Свое время суток только у тебя на экране (сервер и другие игроки его не видят).
 * Подмена происходит в ClientWorldPropertiesMixin.
 */
public class TimeChangerModule extends Module {
	/** Часы на игровых часах: 0 = полночь, 6 = рассвет, 12 = полдень, 18 = закат. */
	private final DoubleSetting hour = addSetting("Time", 12.0, 0.0, 24.0, 0.5);
	/** 0 = время стоит на месте, больше = время идёт быстрее. */
	private final DoubleSetting speed = addSetting("Speed", 0.0, 0.0, 20.0, 0.5);

	private long ticks;

	public TimeChangerModule() {
		super("Time Changer", "Your own time of day (only you see it)");
	}

	@Override
	public void onTick(MinecraftClient client) {
		ticks++;
	}

	/** Время в игровых тиках: 0 = 6:00, 6000 = полдень, 18000 = полночь. */
	public long getTime() {
		double base = ((hour.get() + 18.0) % 24.0) * 1000.0;
		return Math.max(0L, (long) (base + ticks * speed.get()));
	}
}
