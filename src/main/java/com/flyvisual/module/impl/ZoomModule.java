package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Плавный зум: держи C. Колёсико мыши во время зума меняет приближение.
 * Применяется в GameRendererMixin (поле зрения), прокрутка ловится в MouseMixin.
 */
public class ZoomModule extends Module {
	private final KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.flyvisual.zoom",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_C,
			"category.flyvisual"
	));

	/** Стартовое приближение при нажатии клавиши: больше = дальше. */
	private final DoubleSetting level = addSetting("Zoom level", 4.0, 1.5, 30.0, 0.5);
	/** Предел приближения колёсиком. */
	private final DoubleSetting maxZoom = addSetting("Max zoom", 40.0, 5.0, 60.0, 5.0);
	/** Насколько быстро колёсико меняет приближение. */
	private final DoubleSetting scrollSpeed = addSetting("Scroll speed", 1.15, 1.05, 1.5, 0.05);
	private final DoubleSetting smoothZoom = addToggle("Smooth zoom", true);
	private final DoubleSetting smoothness = addSetting("Smoothness", 12.0, 2.0, 30.0, 1.0);

	/** Текущее приближение (1 = нет зума). */
	private double currentZoom = 1.0;
	/** Приближение, выбранное колёсиком; отрицательное = ещё не выбрано. */
	private double scrollZoom = -1.0;
	private long lastNanos = System.nanoTime();

	public ZoomModule() {
		super("Zoom", "Hold C to zoom, scroll to adjust");
	}

	@Override
	protected boolean enabledByDefault() {
		return true;
	}

	public boolean isZooming() {
		MinecraftClient client = mc();
		return isEnabled() && client.currentScreen == null && key.isPressed();
	}

	private double startZoom() {
		return Math.min(level.get(), maxZoom.get());
	}

	/** Колёсико во время зума. Возвращает true, если прокрутка использована (тогда хотбар не листается). */
	public boolean onScroll(double amount) {
		if (!isZooming()) return false;
		if (scrollZoom < 0) {
			scrollZoom = startZoom();
		}
		scrollZoom = Math.max(1.2, Math.min(maxZoom.get(), scrollZoom * Math.pow(scrollSpeed.get(), amount)));
		return true;
	}

	/** Множитель поля зрения. Вызывается раз за кадр из GameRendererMixin. */
	public double getFovMultiplier() {
		long now = System.nanoTime();
		double dt = Math.min((now - lastNanos) / 1.0e9, 0.1);
		lastNanos = now;

		double target;
		if (isZooming()) {
			if (scrollZoom < 0) {
				scrollZoom = startZoom();
			}
			target = Math.min(scrollZoom, maxZoom.get());
		} else {
			scrollZoom = -1.0;
			target = 1.0;
		}

		double k = smoothZoom.getBool() ? 1.0 - Math.exp(-smoothness.get() * dt) : 1.0;
		currentZoom += (target - currentZoom) * k;

		if (target == 1.0 && Math.abs(currentZoom - 1.0) < 0.002) {
			currentZoom = 1.0;
		}
		return 1.0 / currentZoom;
	}
}
