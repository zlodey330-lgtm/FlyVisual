package com.flyvisual.module.impl;

import com.flyvisual.gui.EmoteScreen;
import com.flyvisual.hud.Notifications;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Эмоции: клавиша открывает меню, выбранная эмоция проигрывается на твоей модели.
 * Камера сама переходит на вид сзади (F5) и после конца эмоции возвращается в первое лицо.
 * Каждая эмоция конечная. Видно только тебе: другие игроки анимацию не получают.
 * Позы накладываются в PlayerEntityModelMixin.
 */
public class EmoteModule extends Module {
	public static final String[] NAMES = {"Сикс сэвен", "Танец", "Приветствие", "Победа", "Хлопки", "Раздумье"};
	/** Длительность каждой эмоции в секундах при скорости 1.0. */
	private static final float[] DURATIONS = {6.0f, 5.0f, 3.0f, 3.0f, 4.0f, 4.0f};

	private final KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.flyvisual.emotes",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_UNKNOWN,
			"category.flyvisual"
	));

	private final DoubleSetting autoThird = addToggle("Auto third person", true);
	private final DoubleSetting cancelOnSneak = addToggle("Cancel on sneak", true);
	private final DoubleSetting speed = addSetting("Emote speed", 1.0, 0.5, 2.0, 0.1);

	private int index = -1;
	private long startNanos;
	private Perspective savedPerspective;
	private long lastApplyNanos;

	public EmoteModule() {
		super("Emotes", "Emotes menu: Six Seven, dance and more");
	}

	@Override
	protected boolean enabledByDefault() {
		return true;
	}

	public boolean isPlaying() {
		return index >= 0;
	}

	public int current() {
		return index;
	}

	/** Запускает эмоцию номер i и при необходимости переключает камеру на вид сзади. */
	public void start(int i) {
		MinecraftClient client = mc();
		if (client == null || client.player == null || i < 0 || i >= NAMES.length) return;

		if (index >= 0) {
			stop();
		}
		index = i;
		startNanos = System.nanoTime();
		Notifications.push("Эмоция: " + NAMES[i], true);

		Perspective current = client.options.getPerspective();
		if (autoThird.getBool() && current == Perspective.FIRST_PERSON) {
			savedPerspective = current;
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		} else {
			savedPerspective = null;
		}
	}

	/** Останавливает эмоцию и возвращает прежний вид камеры. */
	public void stop() {
		index = -1;
		MinecraftClient client = mc();
		if (savedPerspective != null && client != null && client.options != null) {
			client.options.setPerspective(savedPerspective);
		}
		savedPerspective = null;
	}

	@Override
	public void onDisable() {
		if (index >= 0) {
			stop();
		}
	}

	@Override
	public void onTick(MinecraftClient client) {
		if (client.player == null) {
			index = -1;
			savedPerspective = null;
			return;
		}

		while (key.wasPressed()) {
			if (client.currentScreen == null) {
				client.setScreen(new EmoteScreen());
			}
		}

		if (index >= 0) {
			float elapsed = (System.nanoTime() - startNanos) / 1.0e9f;
			boolean over = elapsed * speed.getFloat() >= DURATIONS[index];
			boolean cancel = cancelOnSneak.getBool() && client.player.isSneaking();
			if (over || cancel) {
				stop();
			}
		}
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	/** Плавно ведёт часть модели от ванильного положения к нужному (f = сила эмоции 0..1). */
	private static void set(ModelPart part, float pitch, float yaw, float roll, float f) {
		part.pitch = lerp(part.pitch, pitch, f);
		part.yaw = lerp(part.yaw, yaw, f);
		part.roll = lerp(part.roll, roll, f);
	}

	/** Накладывает позу текущей эмоции на модель игрока. Вызывается из PlayerEntityModelMixin. */
	public void apply(PlayerEntityModel<?> m) {
		if (index < 0) return;
		// Позу могут вызвать два миксина подряд в одном кадре: второй раз пропускаем
		long nowN = System.nanoTime();
		if (nowN - lastApplyNanos < 300_000L) return;
		lastApplyNanos = nowN;

		float elapsed = (System.nanoTime() - startNanos) / 1.0e9f;
		float t = elapsed * speed.getFloat();
		float dur = DURATIONS[index];
		// Плавный вход и выход (0.35 с), чтобы поза не дёргалась
		float f = Math.max(0.0f, Math.min(1.0f, Math.min(t / 0.35f, (dur - t) / 0.35f)));
		f = f * f * (3.0f - 2.0f * f);
		float tau = (float) (Math.PI * 2.0);

		switch (index) {
			case 0 -> {
				// Сикс сэвен: руки вперёд, ладони вверх, попеременно вверх-вниз
				float w = t * tau * 1.7f;
				float a = (float) Math.sin(w);
				set(m.rightArm, -1.45f + 0.55f * a, 0.0f, 0.12f, f);
				set(m.leftArm, -1.45f - 0.55f * a, 0.0f, -0.12f, f);
				m.head.pitch += 0.07f * (float) Math.sin(w * 2.0f) * f;
			}
			case 1 -> {
				// Танец: руки вверх по очереди, покачивание корпуса и ног
				float w = t * tau * 0.9f;
				float s = (float) Math.sin(w);
				set(m.rightArm, -2.4f + 0.7f * s, 0.0f, 0.3f, f);
				set(m.leftArm, -2.4f - 0.7f * s, 0.0f, -0.3f, f);
				set(m.body, 0.0f, 0.35f * s, 0.0f, f);
				m.head.yaw += 0.25f * s * f;
				set(m.rightLeg, 0.45f * s, 0.0f, 0.05f, f);
				set(m.leftLeg, -0.45f * s, 0.0f, -0.05f, f);
			}
			case 2 -> {
				// Приветствие: правая рука поднята и машет
				float w = t * tau * 1.6f;
				set(m.rightArm, -2.75f, 0.0f, 0.35f * (float) Math.sin(w) + 0.2f, f);
			}
			case 3 -> {
				// Победа: обе руки вверх, лёгкое покачивание
				float w = t * tau * 1.4f;
				float s = (float) Math.sin(w);
				set(m.rightArm, -3.0f + 0.15f * s, 0.0f, 0.25f, f);
				set(m.leftArm, -3.0f - 0.15f * s, 0.0f, -0.25f, f);
				m.head.pitch -= 0.2f * f;
			}
			case 4 -> {
				// Хлопки: руки перед собой сходятся и расходятся
				float c = 0.2f + 0.4f * (float) Math.sin(t * tau * 2.2f);
				set(m.rightArm, -1.5f, -c, 0.0f, f);
				set(m.leftArm, -1.5f, c, 0.0f, f);
			}
			default -> {
				// Раздумье: рука у подбородка, голова чуть наклонена
				set(m.rightArm, -2.05f, -0.55f, 0.0f, f);
				m.head.pitch += 0.15f * f;
				m.head.yaw += 0.25f * (float) Math.sin(t * 1.2f) * f;
			}
		}

		// Одежда и шляпа повторяют основные части
		m.leftSleeve.copyTransform(m.leftArm);
		m.rightSleeve.copyTransform(m.rightArm);
		m.leftPants.copyTransform(m.leftLeg);
		m.rightPants.copyTransform(m.rightLeg);
		m.jacket.copyTransform(m.body);
		m.hat.copyTransform(m.head);
	}
}
