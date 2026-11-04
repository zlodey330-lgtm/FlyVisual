package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Свободный обзор на 360°: держи Left Alt и крути мышкой, тело игрока при этом не поворачивается.
 * Работает только на твоей стороне (камера), серверу ничего не отправляется.
 * Повороты ловятся в EntityMixin, камера подменяется в CameraMixin.
 *
 * Плавность («Smoothness») относится к самому переходу: когда ты зажимаешь клавишу, камера плавно
 * отъезжает назад (вид от 3-го лица), а когда отпускаешь, плавно возвращается к направлению тела.
 * Движения мышкой вправо-влево при этом НЕ сглаживаются, они остаются мгновенными.
 */
public class FreeLookModule extends Module {
	private final KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.flyvisual.freelook",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_LEFT_ALT,
			"category.flyvisual"
	));

	private final DoubleSetting sensitivity = addSetting("Sensitivity", 1.0, 0.2, 2.0, 0.1);
	private final DoubleSetting thirdPerson = addToggle("Third person", true);
	private final DoubleSetting smoothness = addSetting("Smoothness", 60.0, 0.0, 95.0, 5.0);

	/** Клавиша зажата: мышь крутит камеру. */
	private boolean active;
	/** Идёт возвращение камеры после отпускания клавиши. */
	private boolean returning;
	/** Прогресс перехода 0..1 (0 = обычная камера, 1 = полностью свободная). */
	private float progress;
	/** Куда повёрнута мышь (свободная камера). */
	private float freeYaw;
	private float freePitch;
	/** Что видит камера сейчас. */
	private float shownYaw;
	private float shownPitch;
	/** Доля 3-го лица: 0 = камера в голове, 1 = полное расстояние. */
	private float distance = 1.0f;
	private long lastFrame = System.nanoTime();
	private Perspective savedPerspective;

	public FreeLookModule() {
		super("FreeLook", "Hold Left Alt to look around freely");
	}

	@Override
	protected boolean enabledByDefault() {
		return true;
	}

	/** Клавиша зажата (нужно EntityMixin, чтобы мышь крутила камеру, а не игрока). */
	public boolean isActive() {
		return active;
	}

	/** Камера сейчас подменена: клавиша зажата или камера ещё возвращается назад. */
	public boolean overridesCamera() {
		return active || returning;
	}

	public float getYaw() {
		return shownYaw;
	}

	public float getPitch() {
		return shownPitch;
	}

	/** Множитель расстояния камеры в 3-м лице (для плавного отъезда). */
	public float getDistanceFactor() {
		return savedPerspective != null ? distance : 1.0f;
	}

	/** Поворот камеры от движения мыши (ванильный коэффициент 0.15 умножается на Sensitivity). Без сглаживания. */
	public void look(double deltaX, double deltaY) {
		float factor = 0.15f * sensitivity.getFloat();
		freeYaw += (float) deltaX * factor;
		freePitch += (float) deltaY * factor;
		freePitch = Math.max(-90.0f, Math.min(90.0f, freePitch));
	}

	@Override
	public void onTick(MinecraftClient client) {
		boolean want = client.player != null && client.currentScreen == null && key.isPressed();
		if (want && !active) {
			activate(client);
		} else if (!want && active) {
			deactivate();
		}
	}

	@Override
	public void onDisable() {
		finish(MinecraftClient.getInstance());
	}

	private void activate(MinecraftClient client) {
		active = true;
		if (!returning && progress <= 0.0f) {
			// Начинаем с того, куда смотрит игрок: камера не дёргается
			freeYaw = client.player.getYaw();
			freePitch = client.player.getPitch();
			distance = 0.0f;
		}
		returning = false;

		// От первого лица камера внутри головы, поэтому при желании переключаемся на вид сзади
		Perspective current = client.options.getPerspective();
		if (savedPerspective == null && thirdPerson.getBool() && current == Perspective.FIRST_PERSON) {
			savedPerspective = current;
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		}
		lastFrame = System.nanoTime();
	}

	private void deactivate() {
		active = false;
		returning = true;
		lastFrame = System.nanoTime();
	}

	private void finish(MinecraftClient client) {
		active = false;
		returning = false;
		progress = 0.0f;
		distance = 1.0f;
		if (savedPerspective != null && client != null && client.options != null) {
			client.options.setPerspective(savedPerspective);
		}
		savedPerspective = null;
	}

	/**
	 * Вызывается раз за кадр из CameraMixin перед тем, как камера берёт углы.
	 * Двигает переход и считает, что должна показывать камера.
	 */
	public void updateFrame(MinecraftClient client) {
		if (client.player == null) {
			finish(client);
			return;
		}

		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1.0e9f, 0.1f);
		lastFrame = now;

		// Длительность перехода зависит от «Плавности»: 0 = мгновенно, 95 = около 0.75 секунды
		float s = smoothness.getFloat() / 100.0f;
		float target = active ? 1.0f : 0.0f;
		if (s <= 0.001f) {
			progress = target;
		} else {
			float duration = 0.05f + 0.75f * s;
			float step = dt / duration;
			progress = progress < target ? Math.min(target, progress + step) : Math.max(target, progress - step);
		}
		float eased = progress * progress * (3.0f - 2.0f * progress);
		distance = eased;

		float playerYaw = client.player.getYaw();
		float playerPitch = client.player.getPitch();
		shownYaw = playerYaw + MathHelper.wrapDegrees(freeYaw - playerYaw) * eased;
		shownPitch = playerPitch + (freePitch - playerPitch) * eased;

		if (returning && progress <= 0.0f) {
			finish(client);
		}
	}
}
