package com.flyvisual.record;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Собирает звук игры в WAV (48 кГц, стерео, 16 бит) в реальном времени, пока идёт запись.
 * Для каждого звука учитываются громкость, высота, зацикливание, расстояние до камеры (затухание)
 * и сторона (левый/правый канал), как это делает OpenAL в игре.
 */
public final class AudioMixer {
	public static final int RATE = 48000;
	private static final int CHUNK = 480; // 10 мс

	private static final class State {
		int gen;
		double cursor;
		boolean finished;
	}

	private final long startNanos;
	private final Path wavFile;
	private final Map<AudioTap.Voice, State> states = new IdentityHashMap<>();
	private final Thread thread;
	private volatile boolean running = true;
	private volatile long stopNanos;
	private volatile Throwable failure;

	public AudioMixer(long startNanos, Path wavFile) {
		this.startNanos = startNanos;
		this.wavFile = wavFile;
		this.thread = new Thread(this::run, "FlyVisual-audio");
		this.thread.setDaemon(true);
		this.thread.start();
	}

	/** Просит закончить запись звука; файл дописывается в фоновом потоке. */
	public synchronized void stop() {
		if (!running) return; // время остановки запоминаем только по первому вызову
		stopNanos = System.nanoTime();
		running = false;
	}

	/** Ждёт, пока WAV будет полностью записан. */
	public void join(long millis) {
		try {
			thread.join(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public Throwable failure() {
		return failure;
	}

	public Path file() {
		return wavFile;
	}

	// ------------------------------------------------------------------ поток микширования

	private void run() {
		long produced = 0;
		try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(wavFile), 1 << 16)) {
			out.write(header(0));
			float[] mix = new float[CHUNK * 2];
			byte[] bytes = new byte[CHUNK * 4];
			while (true) {
				boolean stopping = !running;
				long until = stopping ? stopNanos : System.nanoTime();
				long limit = (until - startNanos) * RATE / 1_000_000_000L;
				while (produced < limit) {
					mixChunk(mix);
					toBytes(mix, bytes);
					out.write(bytes);
					produced += CHUNK;
				}
				if (stopping) break;
				Thread.sleep(5);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Throwable t) {
			failure = t;
		}
		// Дописываем в заголовок настоящий размер
		try (RandomAccessFile raf = new RandomAccessFile(wavFile.toFile(), "rw")) {
			raf.seek(0);
			raf.write(header(produced * 4));
		} catch (Throwable t) {
			if (failure == null) failure = t;
		}
	}

	private static byte[] header(long dataBytes) {
		ByteBuffer b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
		b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt((int) (36 + dataBytes));
		b.put("WAVE".getBytes(StandardCharsets.US_ASCII));
		b.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16);
		b.putShort((short) 1).putShort((short) 2).putInt(RATE).putInt(RATE * 4).putShort((short) 4).putShort((short) 16);
		b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt((int) dataBytes);
		return b.array();
	}

	private static void toBytes(float[] mix, byte[] out) {
		for (int i = 0, p = 0; i < mix.length; i++, p += 2) {
			float s = mix[i];
			if (s > 1.0f) s = 1.0f;
			if (s < -1.0f) s = -1.0f;
			int v = (int) (s * 32767.0f);
			out[p] = (byte) v;
			out[p + 1] = (byte) (v >> 8);
		}
	}

	private void mixChunk(float[] mix) {
		java.util.Arrays.fill(mix, 0.0f);
		long now = System.nanoTime();
		for (AudioTap.Voice v : AudioTap.VOICES.values()) {
			AudioTap.Pcm p = v.pcm;
			if (p == null || !v.playing || v.paused) continue;
			int frames = p.frames();
			if (frames <= 0) continue;

			State st = states.get(v);
			int gen = v.gen;
			if (st == null || st.gen != gen) {
				st = new State();
				st.gen = gen;
				// Звук, который начался до записи (например, фоновый шум), продолжаем с примерного места
				if (v.playNanos != 0 && v.playNanos < startNanos) {
					double sec = (now - v.playNanos) / 1.0e9;
					st.cursor = sec * p.rate * Math.max(0.01f, v.pitch);
					if (v.looping) {
						st.cursor %= frames;
					} else if (st.cursor >= frames) {
						st.finished = true;
					}
				}
				states.put(v, st);
			}
			if (st.finished) continue;

			// Громкость и сторона
			float gain = v.volume;
			double dx;
			double dy;
			double dz;
			if (v.relative) {
				dx = v.x;
				dy = v.y;
				dz = v.z;
			} else {
				dx = v.x - AudioTap.listenerX;
				dy = v.y - AudioTap.listenerY;
				dz = v.z - AudioTap.listenerZ;
			}
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			float att = v.attenuation;
			if (att > 0.0f) {
				gain *= (float) Math.max(0.0, 1.0 - dist / att);
			}
			double pan = 0.0;
			if (dist > 1.0e-4) {
				pan = v.relative ? dx / dist : (dx * AudioTap.rightX + dz * AudioTap.rightZ) / dist;
			}
			pan = Math.max(-1.0, Math.min(1.0, pan));
			double angle = (pan + 1.0) * Math.PI / 4.0;
			float gl = (float) (Math.cos(angle) * gain);
			float gr = (float) (Math.sin(angle) * gain);
			boolean silent = gain <= 0.0001f;

			short[] d = p.data;
			int ch = p.channels;
			double step = Math.max(0.01f, v.pitch) * p.rate / (double) RATE;
			boolean loop = v.looping;
			double cursor = st.cursor;
			for (int i = 0; i < CHUNK; i++) {
				if (cursor >= frames) {
					if (loop) {
						cursor %= frames;
					} else {
						st.finished = true;
						break;
					}
				}
				if (!silent) {
					int i0 = (int) cursor;
					double f = cursor - i0;
					int i1 = i0 + 1;
					if (i1 >= frames) i1 = loop ? 0 : i0;
					if (ch == 1) {
						float s = (float) ((d[i0] + (d[i1] - d[i0]) * f) / 32768.0);
						mix[2 * i] += s * gl;
						mix[2 * i + 1] += s * gr;
					} else {
						float l = (float) ((d[2 * i0] + (d[2 * i1] - d[2 * i0]) * f) / 32768.0);
						float r = (float) ((d[2 * i0 + 1] + (d[2 * i1 + 1] - d[2 * i0 + 1]) * f) / 32768.0);
						mix[2 * i] += l * gain;
						mix[2 * i + 1] += r * gain;
					}
				}
				cursor += step;
			}
			st.cursor = cursor;
		}
		// Чистим состояния звуков, которых уже нет
		if (states.size() > 256) {
			states.keySet().removeIf(v -> !AudioTap.VOICES.containsValue(v));
		}
	}
}
