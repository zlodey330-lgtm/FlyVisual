package com.flyvisual.record;

import com.flyvisual.hud.Notifications;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Запись видео с экрана игры (вместе с интерфейсом). Звук не записывается.
 * <p>
 * Как это работает: после отрисовки каждого кадра он сжимается на видеокарте до нужного разрешения и читается
 * в память через два буфера по очереди, поэтому игра не ждёт видеокарту. Дальше кадры уходят в отдельный поток,
 * который кодирует видео, так что FPS в игре почти не страдает. Скорость видео всегда равна реальной:
 * если игра выдаёт меньше кадров, чем нужно, кадр повторяется.
 */
public final class Recorder {
	private static final Logger LOGGER = LoggerFactory.getLogger("flyvisual");

	private static final float[] JPEG_QUALITY = {0.55f, 0.75f, 0.88f, 0.95f};

	private record Frame(byte[] data, int repeat) {
	}

	private boolean active;
	private int tw;
	private int th;
	private int fps;
	private int size;
	private String info = "";

	private Framebuffer capture;
	private final int[] pbo = new int[2];
	private final int[] pboRepeat = new int[2];
	private final boolean[] pboFull = new boolean[2];
	private int cur;

	private long startNanos;
	private long submitted;
	private int carry;
	/** Сколько кадров записано всего и сколько из них повторы (игра выдавала меньше FPS, чем записывается). */
	private volatile long statSlots;
	private volatile long statDup;

	private ArrayBlockingQueue<byte[]> free;
	private LinkedBlockingQueue<Frame> filled;
	private Worker worker;
	private AudioMixer audioMixer;
	private volatile Throwable failure;
	private volatile java.util.function.BiConsumer<Integer, Boolean> finishListener;

	/** Что делать по окончании записи: получает процент повторов кадров и признак ошибки. */
	public void setFinishListener(java.util.function.BiConsumer<Integer, Boolean> listener) {
		this.finishListener = listener;
	}

	/** Сколько процентов кадров были повторами (игра или кодировщик не успевали). */
	public int dupPercent() {
		long slots = statSlots;
		return slots > 0L ? (int) (statDup * 100L / slots) : 0;
	}

	/** Сбросить счётчик повторов (после «разогрева» пробной записи). */
	public void resetStats() {
		statSlots = 0L;
		statDup = 0L;
	}

	public boolean hasFailed() {
		return failure != null;
	}

	public boolean isActive() {
		return active;
	}

	/** Что сейчас пишется (для уведомления). */
	public String describe() {
		return info;
	}

	/** Сколько секунд идёт запись. */
	public long elapsedSeconds() {
		return active ? (System.nanoTime() - startNanos) / 1_000_000_000L : 0L;
	}

	// ------------------------------------------------------------------ старт

	/**
	 * Начинает запись. resHeight: высота кадра (0 = как в окне). quality: 0..3. audio: записывать ли звук игры.
	 * codec: 0 авто, 1 NVIDIA, 2 AMD, 3 Intel, 4 процессор (x264), 5 Motion JPEG.
	 * Возвращает текст ошибки или null, если всё хорошо.
	 */
	public String start(Path folder, int resHeight, int fps, int quality, boolean audio, int codec) {
		return begin(folder, resHeight, fps, quality, audio, codec, false);
	}

	/** Пробная запись для автонастройки: во временный файл, без звука и уведомлений; файл потом удаляется. */
	public String startTrial(Path folder, int resHeight, int fps, int quality, int codec) {
		return begin(folder, resHeight, fps, quality, false, codec, true);
	}

	private String begin(Path folder, int resHeight, int fps, int quality, boolean audio, int codec, boolean trial) {
		if (active) return null;
		MinecraftClient mc = MinecraftClient.getInstance();
		int fw = mc.getWindow().getFramebufferWidth();
		int fh = mc.getWindow().getFramebufferHeight();
		if (fw <= 0 || fh <= 0) return "окно игры свёрнуто";

		int w;
		int h;
		if (resHeight <= 0) {
			w = fw;
			h = fh;
		} else {
			h = resHeight;
			w = (int) Math.round(resHeight * (double) fw / fh);
		}
		w &= ~1;
		h &= ~1;
		if (w < 16 || h < 16) return "слишком маленькое разрешение";

		try {
			Files.createDirectories(folder);
		} catch (IOException e) {
			return "не удалось создать папку: " + e.getMessage();
		}

		String stamp = trial ? "trial_" + System.nanoTime()
				: LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
		int q = Math.max(0, Math.min(3, quality));
		FrameSink sink;
		String how;
		String exe = null;
		Path finalFile;
		Path wavFile = folder.resolve("FlyVisual_" + stamp + ".wav");
		boolean mux = false;
		boolean muxMp4 = true;
		try {
			exe = FfmpegSink.find(FabricLoader.getInstance().getGameDir(), folder);
			if (exe != null && codec != 5) {
				// H.264: на видеокарте (почти не грузит процессор) или на процессоре
				if (!Encoders.isDetected()) Encoders.detect(exe);
				Encoders.Kind kind = Encoders.resolve(codec);
				finalFile = folder.resolve("FlyVisual_" + stamp + ".mp4");
				mux = audio;
				Path videoFile = audio ? folder.resolve("FlyVisual_" + stamp + ".video.mp4") : finalFile;
				sink = new FfmpegSink(exe, w, h, fps, Encoders.args(kind, q), videoFile);
				how = "mp4, " + kind.title + (audio ? ", со звуком" : ", без звука");
				if (codec >= 1 && codec <= 3 && kind == Encoders.Kind.X264) {
					how += " (выбранной видеокарты нет)";
				}
			} else {
				// Motion JPEG: звук записывается внутрь самого файла, ffmpeg не нужен
				finalFile = folder.resolve("FlyVisual_" + stamp + ".avi");
				sink = new AviSink(finalFile, w, h, fps, JPEG_QUALITY[q], audio);
				wavFile = null;
				how = "avi, Motion JPEG" + (audio ? ", со звуком" : ", без звука");
			}
		} catch (IOException e) {
			return "не удалось начать запись: " + e.getMessage();
		}

		this.tw = w;
		this.th = h;
		this.fps = fps;
		this.size = w * h * 4;
		this.info = w + "x" + h + ", " + fps + " FPS, " + how;

		int pool = size > 16_000_000 ? 3 : 5;
		free = new ArrayBlockingQueue<>(pool);
		for (int i = 0; i < pool; i++) {
			free.add(new byte[size]);
		}
		filled = new LinkedBlockingQueue<>();
		failure = null;

		try {
			capture = new SimpleFramebuffer(w, h, false, MinecraftClient.IS_SYSTEM_MAC);
			for (int i = 0; i < 2; i++) {
				pbo[i] = GL15.glGenBuffers();
				GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[i]);
				GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, (long) size, GL15.GL_STREAM_READ);
				pboFull[i] = false;
				pboRepeat[i] = 0;
			}
			GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
		} catch (Throwable t) {
			LOGGER.error("FlyVisual: не удалось подготовить запись", t);
			releaseGl();
			try {
				sink.close();
			} catch (Throwable ignored) {
			}
			return "не удалось подготовить видеокарту (" + t.getClass().getSimpleName() + ")";
		}

		cur = 0;
		carry = 0;
		submitted = 0;
		statSlots = 0L;
		statDup = 0L;
		startNanos = System.nanoTime();
		audioMixer = audio ? new AudioMixer(startNanos, wavFile) : null;
		if (sink instanceof AviSink avi) avi.setAudio(audioMixer);
		worker = new Worker(sink, audioMixer, mux ? exe : null, muxMp4, finalFile, wavFile, trial);
		worker.setDaemon(true);
		worker.start();
		active = true;
		return null;
	}

	// ------------------------------------------------------------------ каждый кадр игры

	/** Вызывается в конце отрисовки каждого кадра игры. */
	public void onFrame() {
		if (!active) return;
		if (failure != null) {
			stop();
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (audioMixer != null) AudioTap.updateListener(mc);
		Framebuffer main = mc.getFramebuffer();
		if (main == null || main.textureWidth <= 0 || main.textureHeight <= 0) return;

		long now = System.nanoTime();
		long target = (now - startNanos) * fps / 1_000_000_000L + 1;
		long need = target - submitted;
		if (need <= 0) return;

		// После долгой паузы (игра на паузе, окно свёрнуто) не раздуваем видео: пропускаем лишнее время
		long cap = fps * 2L;
		if (need > cap) {
			startNanos += (need - cap) * 1_000_000_000L / fps;
			need = cap;
		}

		try {
			int idx = cur;
			int prev = idx ^ 1;
			deliver(prev, false);
			issue(main, idx, (int) need);
			cur = prev;
		} catch (Throwable t) {
			LOGGER.error("FlyVisual: ошибка записи кадра", t);
			failure = t;
			stop();
		}
	}

	/** Сжимает кадр до нужного разрешения и запускает чтение в буфер (без ожидания видеокарты). */
	private void issue(Framebuffer main, int idx, int need) {
		int fw = main.textureWidth;
		int fh = main.textureHeight;
		main.beginWrite(false);
		GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.fbo);
		GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, capture.fbo);
		// Верх и низ меняем местами: OpenGL отдаёт строки снизу вверх, а видео нужно сверху вниз
		GL30.glBlitFramebuffer(0, fh, fw, 0, 0, 0, tw, th, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, capture.fbo);
		GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[idx]);
		GL11.glReadPixels(0, 0, tw, th, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
		GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
		main.beginWrite(true);
		pboRepeat[idx] = need;
		pboFull[idx] = true;
		submitted += need;
	}

	/** Забирает уже готовый кадр из буфера и отдаёт его кодировщику. */
	private void deliver(int i, boolean wait) {
		if (!pboFull[i]) return;
		pboFull[i] = false;
		int repeat = pboRepeat[i] + carry;
		byte[] buf;
		try {
			buf = wait ? free.poll(2, TimeUnit.SECONDS) : free.poll();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			buf = null;
		}
		if (buf == null) {
			// Кодировщик не успевает: этот кадр пропускаем, но время сохраняем (повторим следующий)
			carry = repeat;
			return;
		}
		GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[i]);
		ByteBuffer mapped = GL30.glMapBufferRange(GL21.GL_PIXEL_PACK_BUFFER, 0L, (long) size, GL30.GL_MAP_READ_BIT);
		if (mapped != null) {
			mapped.get(buf, 0, size);
		}
		GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
		GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
		if (mapped == null) {
			free.offer(buf);
			carry = repeat;
			return;
		}
		carry = 0;
		statSlots += repeat;
		statDup += repeat - 1;
		filled.offer(new Frame(buf, repeat));
	}

	// ------------------------------------------------------------------ остановка

	/** Останавливает запись; файл дописывается в фоне, потом придёт уведомление. */
	public void stop() {
		if (!active) return;
		active = false;
		try {
			deliver(cur ^ 1, true);
		} catch (Throwable t) {
			LOGGER.error("FlyVisual: не удалось забрать последний кадр", t);
		}
		if (audioMixer != null) audioMixer.stop();
		filled.offer(new Frame(null, 0));
		releaseGl();
	}

	/** Остановка при закрытии игры: ждём, пока файл полностью допишется. */
	public void stopAndWait() {
		Worker w = worker;
		stop();
		if (w != null) {
			try {
				w.join(30_000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private void releaseGl() {
		try {
			for (int i = 0; i < 2; i++) {
				if (pbo[i] != 0) {
					GL15.glDeleteBuffers(pbo[i]);
					pbo[i] = 0;
				}
			}
			if (capture != null) {
				capture.delete();
				capture = null;
			}
		} catch (Throwable ignored) {
		}
	}

	// ------------------------------------------------------------------ поток кодирования

	private final class Worker extends Thread {
		private final FrameSink sink;
		private final AudioMixer mixer;
		private final String muxExe;
		private final boolean muxMp4;
		private final Path finalFile;
		private final Path wavFile;
		private final boolean trial;
		private final AtomicBoolean reported = new AtomicBoolean();

		Worker(FrameSink sink, AudioMixer mixer, String muxExe, boolean muxMp4, Path finalFile, Path wavFile, boolean trial) {
			super("FlyVisual-recorder");
			this.sink = sink;
			this.mixer = mixer;
			this.muxExe = muxExe;
			this.muxMp4 = muxMp4;
			this.finalFile = finalFile;
			this.wavFile = wavFile;
			this.trial = trial;
		}

		@Override
		public void run() {
			ArrayBlockingQueue<byte[]> pool = free;
			LinkedBlockingQueue<Frame> queue = filled;
			try {
				while (true) {
					Frame f = queue.take();
					if (f.data() == null) break;
					if (failure == null) {
						try {
							sink.write(f.data(), f.repeat());
						} catch (Throwable t) {
							failure = t;
						}
					}
					pool.offer(f.data());
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			try {
				sink.close();
			} catch (Throwable t) {
				if (failure == null) failure = t;
			}
			if (trial) {
				// Пробная запись: просто удаляем временный файл
				try {
					Files.deleteIfExists(Path.of(sink.filePath()));
				} catch (Exception ignored) {
				}
				return;
			}
			if (mixer != null) {
				mixer.stop();
				mixer.join(20_000);
			}

			Throwable err = failure;
			String name = sink.fileName();
			String extra = "";
			if (err == null && mixer != null) {
				if (mixer.failure() != null) {
					LOGGER.error("FlyVisual: ошибка записи звука", mixer.failure());
					extra = " (звук записать не удалось)";
				} else if (muxExe != null) {
					Path video = Path.of(sink.filePath());
					if (mux(muxExe, video, wavFile, finalFile, muxMp4)) {
						try {
							Files.deleteIfExists(video);
							Files.deleteIfExists(wavFile);
						} catch (IOException ignored) {
						}
						name = finalFile.getFileName().toString();
					} else {
						extra = " (склеить звук не удалось: видео и .wav лежат отдельно)";
					}
				} else if (wavFile != null) {
					name = name + " + " + wavFile.getFileName();
				}
			}

			MinecraftClient mc = MinecraftClient.getInstance();
			String shownName = name + extra;
			boolean extraFailed = !extra.isEmpty();
			if (err != null) {
				LOGGER.error("FlyVisual: запись остановлена из-за ошибки", err);
				if (reported.compareAndSet(false, true)) {
					mc.execute(() -> Notifications.pushWarning("Запись прервана: " + shortMessage(err)));
				}
			} else if (reported.compareAndSet(false, true)) {
				long slots = statSlots;
				long dup = statDup;
				int dupPct = slots > 0L ? (int) (dup * 100L / slots) : 0;
				java.util.function.BiConsumer<Integer, Boolean> listener = finishListener;
				mc.execute(() -> {
					if (listener != null) listener.accept(dupPct, extraFailed);
					Notifications.push("Запись сохранена: " + shownName, true);
					if (dupPct >= 20) {
						Notifications.pushWarning("Повторено кадров: " + dupPct + "%. Компьютер не успевал: "
								+ "нажми «Автонастройка» в настройках записи, она подберёт настройки под твой ПК");
					}
				});
			}
		}
	}

	/** Склеивает видео и звук в один файл (видео не пережимается). */
	private static boolean mux(String exe, Path video, Path wav, Path out, boolean mp4) {
		try {
			java.util.List<String> cmd = new java.util.ArrayList<>(java.util.List.of(exe, "-y", "-loglevel", "error",
					"-i", video.toString(), "-i", wav.toString(),
					"-map", "0:v:0", "-map", "1:a:0", "-c:v", "copy"));
			if (mp4) {
				cmd.addAll(java.util.List.of("-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart"));
			} else {
				cmd.addAll(java.util.List.of("-c:a", "pcm_s16le"));
			}
			cmd.add(out.toString());
			Process p = new ProcessBuilder(cmd)
					.redirectErrorStream(true)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.start();
			if (!p.waitFor(180, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				return false;
			}
			return p.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	private static String shortMessage(Throwable t) {
		String m = t.getMessage();
		if (m == null || m.isBlank()) return t.getClass().getSimpleName();
		return m.length() > 80 ? m.substring(0, 80) : m;
	}
}
