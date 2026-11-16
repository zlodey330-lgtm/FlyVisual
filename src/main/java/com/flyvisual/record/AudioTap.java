package com.flyvisual.record;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Перехват звука игры для записи видео. Игра воспроизводит звуки через OpenAL, а готовый микс из него достать нельзя,
 * поэтому мы подслушиваем, что игра отдаёт на воспроизведение: какие звуки (их PCM-данные), с какой громкостью,
 * высотой, где находятся и когда запущены. Потом AudioMixer собирает из этого свой микс.
 * <p>
 * Захватываются обычные звуки (шаги, удары, взрывы, интерфейс). Музыка и пластинки идут потоком и не записываются.
 */
public final class AudioTap {
	/** Сохранять ли звуки. Включается модулем «Запись». */
	public static volatile boolean enabled = false;

	/** Данные звука: 16 бит, 1 или 2 канала. */
	static final class Pcm {
		final short[] data;
		final int channels;
		final int rate;

		Pcm(short[] data, int channels, int rate) {
			this.data = data;
			this.channels = channels;
			this.rate = rate;
		}

		int frames() {
			return data.length / channels;
		}
	}

	/** Один проигрываемый звук (источник OpenAL игры). */
	static final class Voice {
		volatile Pcm pcm;
		volatile float volume = 1.0f;
		volatile float pitch = 1.0f;
		volatile boolean looping;
		volatile boolean relative;
		/** Дальность слышимости; меньше или равно нулю = без затухания. */
		volatile float attenuation = -1.0f;
		volatile double x;
		volatile double y;
		volatile double z;
		volatile boolean playing;
		volatile boolean paused;
		volatile long playNanos;
		/** Номер запуска: растёт при каждом play(), чтобы микшер понял, что звук запущен заново. */
		volatile int gen;
	}

	private static final Map<Object, Pcm> SOUNDS = Collections.synchronizedMap(new WeakHashMap<>());
	static final Map<Object, Voice> VOICES = new ConcurrentHashMap<>();

	// Слушатель (камера игрока)
	static volatile double listenerX;
	static volatile double listenerY;
	static volatile double listenerZ;
	/** Вектор «вправо» от камеры по горизонтали. */
	static volatile double rightX = 1.0;
	static volatile double rightZ = 0.0;

	private AudioTap() {
	}

	/** Сбрасывает сохранённое (когда звук отключён). */
	public static void clear() {
		SOUNDS.clear();
		VOICES.clear();
	}

	/** Обновляет положение слушателя; вызывается каждый кадр во время записи. */
	public static void updateListener(MinecraftClient mc) {
		try {
			Camera cam = mc.gameRenderer.getCamera();
			if (cam == null || !cam.isReady()) return;
			Vec3d p = cam.getPos();
			listenerX = p.x;
			listenerY = p.y;
			listenerZ = p.z;
			double yaw = Math.toRadians(cam.getYaw());
			rightX = -Math.cos(yaw);
			rightZ = -Math.sin(yaw);
		} catch (Throwable ignored) {
		}
	}

	// ------------------------------------------------------------------ вызовы из миксинов

	/** Игра загрузила звук: копируем его данные, пока они не ушли в OpenAL. */
	public static void registerSound(Object sound, ByteBuffer sample, AudioFormat format) {
		if (!enabled || sound == null || sample == null || format == null) return;
		try {
			int channels = format.getChannels();
			if (format.getSampleSizeInBits() != 16 || channels < 1 || channels > 2) return;
			ByteBuffer d = sample.duplicate().order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
			short[] pcm = new short[d.remaining() / 2];
			d.asShortBuffer().get(pcm);
			SOUNDS.put(sound, new Pcm(pcm, channels, Math.round(format.getSampleRate())));
		} catch (Throwable ignored) {
		}
	}

	private static Voice voice(Object source) {
		return VOICES.computeIfAbsent(source, k -> new Voice());
	}

	public static void setBuffer(Object source, Object sound) {
		if (!enabled || source == null) return;
		voice(source).pcm = sound == null ? null : SOUNDS.get(sound);
	}

	public static void setVolume(Object source, float volume) {
		if (enabled) voice(source).volume = volume;
	}

	public static void setPitch(Object source, float pitch) {
		if (enabled) voice(source).pitch = pitch;
	}

	public static void setLooping(Object source, boolean looping) {
		if (enabled) voice(source).looping = looping;
	}

	public static void setRelative(Object source, boolean relative) {
		if (enabled) voice(source).relative = relative;
	}

	public static void setAttenuation(Object source, float distance) {
		if (enabled) voice(source).attenuation = distance;
	}

	public static void disableAttenuation(Object source) {
		if (enabled) voice(source).attenuation = -1.0f;
	}

	public static void setPosition(Object source, double x, double y, double z) {
		if (!enabled) return;
		Voice v = voice(source);
		v.x = x;
		v.y = y;
		v.z = z;
	}

	public static void play(Object source) {
		if (!enabled) return;
		Voice v = voice(source);
		v.playNanos = System.nanoTime();
		v.gen++;
		v.paused = false;
		v.playing = true;
	}

	public static void pause(Object source) {
		if (enabled) voice(source).paused = true;
	}

	public static void resume(Object source) {
		if (enabled) voice(source).paused = false;
	}

	public static void stop(Object source) {
		Voice v = VOICES.get(source);
		if (v != null) v.playing = false;
	}

	public static void close(Object source) {
		VOICES.remove(source);
	}
}
