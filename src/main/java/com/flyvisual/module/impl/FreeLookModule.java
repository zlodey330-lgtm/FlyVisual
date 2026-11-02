package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Свободный обзор на 360°: держи Left Alt и крути мышкой, тело игрока при этом не поворачивается.
 * Работает только на твоей стороне (камера), серверу ничего не отправляется.
 * Повороты ловятся в EntityMixin, камера подменяется в CameraMixin.
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

	private boolean active;
	private float yaw;
	private float pitch;
	private Perspective savedPerspective;

	public FreeLookModule() {
		super("FreeLook", "Hold Left Alt to look around freely");
	}

	@Override
	protected boolean enabledByDefault() {
		return true;
	}

	public boolean isActive() {
		return active;
	}

	public float getYaw() {
		return yaw;
	}

	public float getPitch() {
		return pitch;
	}

	/** Поворот камеры от движения мыши (ванильный коэффициент 0.15 умножается на Sensitivity). */
	public void look(double deltaX, double deltaY) {
		float factor = 0.15f * sensitivity.getFloat();
		yaw += (float) deltaX * factor;
		pitch += (float) deltaY * factor;
		pitch = Math.max(-90.0f, Math.min(90.0f, pitch));
	}

	@Override
	public void onTick(MinecraftClient client) {
		boolean want = client.player != null && client.currentScreen == null && key.isPressed();
		if (want && !active) {
			activate(client);
		} else if (!want && active) {
			deactivate(client);
		}
	}

	@Override
	public void onDisable() {
		MinecraftClient client = mc();
		if (client != null && active) {
			deactivate(client);
		}
	}

	private void activate(MinecraftClient client) {
		active = true;
		yaw = client.player.getYaw();
		pitch = client.player.getPitch();

		// В режиме от первого лица камера внутри головы, поэтому при желании переключаемся на вид сзади
		Perspective current = client.options.getPerspective();
		if (thirdPerson.getBool() && current == Perspective.FIRST_PERSON) {
			savedPerspective = current;
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		} else {
			savedPerspective = null;
		}
	}

	private void deactivate(MinecraftClient client) {
		active = false;
		if (savedPerspective != null) {
			client.options.setPerspective(savedPerspective);
			savedPerspective = null;
		}
	}
}
