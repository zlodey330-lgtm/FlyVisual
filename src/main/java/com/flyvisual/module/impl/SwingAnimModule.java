package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;

/** Анимации удара: меняют, как рука с предметом двигается при ударе (от первого лица). */
public class SwingAnimModule extends Module {
	public static SwingAnimModule INSTANCE;
	private static final String[] STYLES = {"Рубящий", "Колющий", "Размах", "Тяжёлый", "Волна", "Кручение", "Блок 1.8"};

	private final DoubleSetting style = addChoice("Swing style", 0, STYLES);
	private final DoubleSetting power = addSetting("Swing power", 100.0, 30.0, 200.0, 10.0);

	public SwingAnimModule() {
		super("Swing Animation", "Different hit animations");
		INSTANCE = this;
	}

	/** Применяет движение к матрице. s = прогресс удара 0..1. */
	public void apply(net.minecraft.client.util.math.MatrixStack m, float s, float side) {
		float k = power.getFloat() / 100.0f;
		float sn = (float) Math.sin(s * Math.PI);
		float sq = (float) Math.sin(Math.sqrt(s) * Math.PI);
		switch (style.getInt()) {
			case 1 -> {
				m.translate(0.0f, 0.05f * sn * k, -0.75f * sn * k);
				m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(-12.0f * sn * k));
			}
			case 2 -> {
				m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(side * 70.0f * (sq - 0.3f * sn) * k));
				m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(-side * 35.0f * sq * k));
				m.translate(-side * 0.2f * sn * k, 0.0f, 0.0f);
			}
			case 3 -> {
				float heavy = s < 0.35f ? -(s / 0.35f) * 0.5f : (float) Math.sin((s - 0.35f) / 0.65f * Math.PI) * 1.0f;
				m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(-70.0f * heavy * k));
				m.translate(0.0f, -0.25f * Math.max(0.0f, heavy) * k, -0.25f * Math.max(0.0f, heavy) * k);
			}
			case 4 -> {
				float w = (float) Math.sin(s * Math.PI * 3.0) * (1.0f - s);
				m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(side * 22.0f * w * k));
				m.translate(0.0f, 0.12f * w * k, -0.2f * sn * k);
			}
			case 5 -> {
				m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(360.0f * s * k * (s < 1.0f ? 1.0f : 0.0f)));
				m.translate(0.0f, 0.0f, -0.3f * sn * k);
			}
			default -> {
				if (style.getInt() == 6) {
					m.translate(-side * 0.1f * sn * k, -0.05f * sn * k, 0.0f);
					m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(-30.0f * sn * k));
					m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(side * 25.0f * sn * k));
					m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(-side * 20.0f * sn * k));
				} else {
					m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(-side * 60.0f * sq * k));
					m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(-25.0f * sn * k));
					m.translate(side * 0.1f * sn * k, 0.0f, 0.0f);
				}
			}
		}
	}
}
