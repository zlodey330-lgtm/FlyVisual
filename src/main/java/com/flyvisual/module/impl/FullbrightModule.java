package com.flyvisual.module.impl;

import com.flyvisual.mixin.SimpleOptionAccessor;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;

/**
 * Полная яркость: поднимаем гамму выше ванильного предела.
 * Работает только на твоей стороне, серверу ничего не отправляется.
 */
public class FullbrightModule extends Module {
	private final DoubleSetting brightness = addSetting("Brightness", 16.0, 2.0, 16.0, 1.0);

	/** Яркость до включения; отрицательное значение = ещё не сохраняли. */
	private double savedGamma = -1;

	public FullbrightModule() {
		super("Fullbright", "Maximum brightness everywhere");
	}

	@Override
	public void onTick(MinecraftClient client) {
		if (client.options == null) return;

		SimpleOption<Double> gamma = client.options.getGamma();
		double current = gamma.getValue();

		if (savedGamma < 0) {
			// Если в options.txt уже осталась огромная гамма, откатываем к обычной яркости
			savedGamma = Math.min(current, 1.0);
		}

		double target = brightness.get();
		if (Math.abs(current - target) > 0.01) {
			setGamma(gamma, target);
		}
	}

	@Override
	public void onDisable() {
		restore();
	}

	/** Возвращает прежнюю яркость. */
	public void restore() {
		MinecraftClient client = mc();
		if (client == null || client.options == null || savedGamma < 0) return;

		setGamma(client.options.getGamma(), savedGamma);
		savedGamma = -1;
	}

	@SuppressWarnings("unchecked")
	private static void setGamma(SimpleOption<Double> option, double value) {
		((SimpleOptionAccessor<Double>) (Object) option).flyvisual$setValue(value);
	}
}
