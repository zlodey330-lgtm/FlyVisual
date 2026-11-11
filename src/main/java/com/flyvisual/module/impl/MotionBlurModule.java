package com.flyvisual.module.impl;

import com.flyvisual.hud.Notifications;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.Uniform;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Размытие в движении: картинка плавно «тянется» за движением камеры, как в кино. Смешиваем текущий кадр с прошлым
 * своим шейдером. Размывается только мир, интерфейс остаётся чётким. Видно только тебе.
 * <p>
 * Режимы: «Всегда», «При повороте камеры» (на месте картинка идеально чёткая) и «При повороте и движении».
 * «Подстройка под FPS» делает размытие одинаковым на любом FPS (иначе на 240 FPS оно было бы почти незаметным).
 */
public class MotionBlurModule extends Module {
	private static final String[] MODES = {"Всегда", "При повороте камеры", "При повороте и движении"};

	private final DoubleSetting strength = addSetting("Blur strength", 45.0, 5.0, 100.0, 5.0);
	private final DoubleSetting mode = addChoice("Blur mode", 1, MODES);
	private final DoubleSetting sensitivity = addSetting("Blur sensitivity", 50.0, 10.0, 100.0, 10.0);
	private final DoubleSetting response = addSetting("Blur response", 8.0, 2.0, 20.0, 1.0);
	private final DoubleSetting adapt = addToggle("Adapt to FPS", true);

	private PostEffectProcessor processor;
	private PostEffectPass blurPass;
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
		if (processor != null) {
			try {
				processor.close();
			} catch (Exception ignored) {
			}
		}
		processor = null;
		blurPass = null;
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
			if (processor == null) {
				build(mc, fw, fh);
			} else if (fw != width || fh != height) {
				processor.setupDimensions(fw, fh);
				width = fw;
				height = fh;
				stale = true;
			}
			if (stale) {
				previous.clear(false);
				stale = false;
			}
			Uniform u = blurPass.getProgram().getUniformByNameOrDummy("Amount");
			u.set((float) a);
			processor.render(tickDelta);
			// Как после ванильных эффектов: снова пишем в основной буфер
			mc.getFramebuffer().beginWrite(true);
		} catch (Throwable t) {
			failed = true;
			release();
			Notifications.push("Размытие: шейдер не загрузился", false);
		}
	}

	private void build(MinecraftClient mc, int fw, int fh) throws Exception {
		release();
		Framebuffer main = mc.getFramebuffer();
		PostEffectProcessor p = new PostEffectProcessor(mc.getTextureManager(), mc.getResourceManager(), main,
				new Identifier("flyvisual", "shaders/post/motion_blur.json"));
		Framebuffer swap = p.getSecondaryTarget("swap");
		Framebuffer prev = p.getSecondaryTarget("previous");
		PostEffectPass pass = p.addPass("flyvisual:motion_blur", main, swap);
		pass.addAuxTarget("PrevSampler", prev::getColorAttachment, fw, fh);
		p.addPass("blit", swap, prev);
		p.addPass("blit", swap, main);
		p.setupDimensions(fw, fh);
		processor = p;
		blurPass = pass;
		previous = prev;
		width = fw;
		height = fh;
		stale = true;
	}
}
