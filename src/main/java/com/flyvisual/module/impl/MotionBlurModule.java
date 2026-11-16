package com.flyvisual.module.impl;

import com.flyvisual.hud.Notifications;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL14;

/**
 * Размытие в движении: картинка плавно «тянется» за движением камеры, как в кино. Смешиваем текущий кадр с прошлым
 * обычным наложением (без своих шейдеров), поэтому работает и без шейдерпаков, и вместе с ними (Iris, OptiFine):
 * мы смешиваем уже готовую картинку мира. Интерфейс остаётся чётким. Видно только тебе.
 * <p>
 * Режимы: «Всегда», «При повороте камеры» (на месте картинка идеально чёткая) и «При повороте и движении».
 * «Подстройка под FPS» делает размытие одинаковым на любом FPS (иначе на 240 FPS оно было бы почти незаметным).
 */
public class MotionBlurModule extends Module {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("flyvisual");
	private static final String[] MODES = {"Всегда", "При повороте камеры", "При повороте и движении"};

	private final DoubleSetting strength = addSetting("Blur strength", 45.0, 5.0, 100.0, 5.0);
	private final DoubleSetting mode = addChoice("Blur mode", 1, MODES);
	private final DoubleSetting sensitivity = addSetting("Blur sensitivity", 50.0, 10.0, 100.0, 10.0);
	private final DoubleSetting response = addSetting("Blur response", 8.0, 2.0, 20.0, 1.0);
	private final DoubleSetting adapt = addToggle("Adapt to FPS", true);

	/** Прошлый кадр. */
	private Framebuffer previous;
	private int width;
	private int height;
	private boolean failed;
	private boolean stale = true;

	private long lastNanos;
	private double fpsEma;
	private float lastYaw = Float.NaN;
	private float lastPitch;
	private float k;

	public MotionBlurModule() {
		super("Motion Blur", "Smooth cinematic motion blur");
	}

	@Override
	public void onEnable() {
		failed = false;
		stale = true;
		lastNanos = 0;
		lastYaw = Float.NaN;
	}

	@Override
	public void onDisable() {
		release();
	}

	private void release() {
		if (previous != null) {
			try {
				previous.delete();
			} catch (Throwable ignored) {
			}
		}
		previous = null;
		stale = true;
	}

	private static float smooth(float lo, float hi, float x) {
		float t = Math.max(0.0f, Math.min(1.0f, (x - lo) / (hi - lo)));
		return t * t * (3.0f - 2.0f * t);
	}

	private static float wrap(float deg) {
		return ((deg % 360.0f) + 540.0f) % 360.0f - 180.0f;
	}

	/** Вызывается из GameRendererMixin каждый кадр после отрисовки мира (до интерфейса). */
	public void process(float tickDelta) {
		if (!isEnabled() || failed) return;
		MinecraftClient mc = mc();
		if (mc.world == null || mc.player == null) return;

		long now = System.nanoTime();
		double dt = lastNanos == 0 ? 0.016 : Math.min(0.2, (now - lastNanos) / 1.0e9);
		lastNanos = now;
		if (dt <= 0.0) return;
		double fps = 1.0 / dt;
		fpsEma = fpsEma == 0.0 ? fps : fpsEma + (fps - fpsEma) * 0.05;

		// Скорость поворота камеры (градусы в секунду) и скорость движения (блоки в секунду)
		float yaw = mc.player.getYaw();
		float pitch = mc.player.getPitch();
		double turn = 0.0;
		if (!Float.isNaN(lastYaw)) {
			turn = Math.hypot(wrap(yaw - lastYaw), pitch - lastPitch) / dt;
		}
		lastYaw = yaw;
		lastPitch = pitch;
		Vec3d vel = mc.player.getVelocity();
		double move = Math.hypot(vel.x, vel.z) * 20.0;

		float target;
		int m = mode.getInt();
		if (m == 0) {
			target = 1.0f;
		} else {
			float thr = (float) (230.0 - sensitivity.get() * 2.0);
			target = smooth(thr * 0.12f, thr, (float) turn);
			if (m == 2) {
				target = Math.max(target, 0.55f * smooth(2.0f, 8.0f, (float) move));
			}
		}
		float rate = (float) response.get() * (target > k ? 2.0f : 1.0f);
		k += (target - k) * (1.0f - (float) Math.exp(-rate * dt));

		// Сила: доля прошлого кадра при 60 FPS, пересчитанная на текущий FPS
		double a60 = 0.15 + 0.80 * (strength.get() / 100.0);
		double a = a60;
		if (adapt.getBool()) {
			double f = Math.max(20.0, Math.min(360.0, fpsEma));
			a = Math.pow(a60, 60.0 / f);
		}
		a = Math.min(0.97, a * k);

		if (a < 0.02) {
			// Размытие не нужно: ничего не рисуем, а прошлый кадр потом сбросим, чтобы не было «призрака»
			stale = true;
			return;
		}

		try {
			int fw = mc.getWindow().getFramebufferWidth();
			int fh = mc.getWindow().getFramebufferHeight();
			Framebuffer main = mc.getFramebuffer();
			if (previous == null || fw != width || fh != height) {
				if (previous != null) previous.delete();
				previous = new SimpleFramebuffer(fw, fh, false, MinecraftClient.IS_SYSTEM_MAC);
				width = fw;
				height = fh;
				stale = true;
			}

			// 1. Накладываем прошлый кадр на текущий: результат = текущий * (1 - a) + прошлый * a
			if (!stale) {
				main.beginWrite(false);
				RenderSystem.enableBlend();
				RenderSystem.blendFunc(GL14.GL_CONSTANT_ALPHA, GL14.GL_ONE_MINUS_CONSTANT_ALPHA);
				GL14.glBlendColor(0.0f, 0.0f, 0.0f, (float) a);
				previous.draw(fw, fh, false);
				RenderSystem.defaultBlendFunc();
				RenderSystem.disableBlend();
			}

			// 2. Запоминаем получившийся кадр как «прошлый» для следующего раза
			previous.beginWrite(true);
			main.draw(fw, fh);
			main.beginWrite(true);
			stale = false;
		} catch (Throwable t) {
			failed = true;
			release();
			LOGGER.error("FlyVisual: Motion Blur отключён из-за ошибки", t);
			Notifications.push("Размытие: ошибка (" + t.getClass().getSimpleName() + "), подробности в latest.log", false);
		}
	}
}
