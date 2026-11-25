package com.flyvisual.module.impl;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.hud.Notifications;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.record.Calibrator;
import com.flyvisual.record.FfmpegSink;
import com.flyvisual.record.Recorder;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Запись: пишет видео с экрана игры прямо из клиента (вместе с интерфейсом) и звук игры.
 * Старт и стоп: клавиша F9 (меняется в настройках управления) или большая кнопка в настройках модуля.
 * Кнопка «Автонастройка» сама подбирает качество, разрешение, FPS и кодировщик под твой компьютер
 * (пробными записями) и записывает результат в настройки ниже.
 * <p>
 * С ffmpeg (в PATH или рядом с игрой) получается mp4 H.264 со звуком, без него avi (Motion JPEG) со звуком внутри.
 */
public class RecordingModule extends Module {
	public static RecordingModule INSTANCE;

	private static final int[] RES_HEIGHT = {0, 540, 720, 900, 1080, 1440, 2160};
	private static final int[] FPS_VALUES = {24, 30, 48, 60, 90, 120, 144};

	private final KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"Запись: старт / стоп",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_F9,
			"category.flyvisual"
	));

	private final DoubleSetting quality = addChoice("Качество", 2, "Низкое", "Среднее", "Высокое", "Максимальное");
	private final DoubleSetting resolution = addChoice("Разрешение", 4, "Как в окне", "540p", "720p", "900p", "1080p", "1440p", "4K");
	private final DoubleSetting fps = addChoice("FPS", 1, "24", "30", "48", "60", "90", "120", "144");
	private final DoubleSetting codec = addChoice("Кодировщик", 0, "Авто (видеокарта, если есть)", "NVIDIA (видеокарта)",
			"AMD (видеокарта)", "Intel (видеокарта)", "Процессор (x264)", "Motion JPEG (как в Bandicam)");
	private final DoubleSetting audio = addToggle("Записывать звук", true);

	@SuppressWarnings("unused")
	private final DoubleSetting pickButton = addButton("Выбрать папку…", this::pickFolder);
	@SuppressWarnings("unused")
	private final DoubleSetting openButton = addButton("Открыть папку", this::openFolder);

	private final Recorder recorder = new Recorder();

	// Замер реального FPS игры (по одному значению в секунду, только в мире и без открытых окон)
	private final double[] fpsSamples = new double[20];
	private int fpsCount;
	private int fpsPos;
	private int framesThisSecond;
	private long secondStart = System.nanoTime();
	private final Calibrator calibrator = new Calibrator();

	public RecordingModule() {
		super("Recording", "Record gameplay video");
		INSTANCE = this;
		// Заранее, в фоне, проверяем, какие видеокарты умеют кодировать видео
		com.flyvisual.record.Encoders.detectAsync(FabricLoader.getInstance().getGameDir());
	}

	@Override
	protected boolean enabledByDefault() {
		return true;
	}

	@Override
	public void onDisable() {
		if (calibrator.isRunning()) calibrator.cancel("");
		recorder.stop();
		com.flyvisual.record.AudioTap.enabled = false;
		com.flyvisual.record.AudioTap.clear();
	}

	@Override
	public void onTick(MinecraftClient client) {
		// Звуки копируем всё время, чтобы они были готовы к началу записи (занимает немного памяти)
		boolean wantAudio = audio.getBool();
		if (com.flyvisual.record.AudioTap.enabled != wantAudio) {
			com.flyvisual.record.AudioTap.enabled = wantAudio;
			if (!wantAudio) com.flyvisual.record.AudioTap.clear();
		}
		while (key.wasPressed()) {
			toggleRecording();
		}
	}

	/** Вызывается из GameRendererMixin в конце каждого кадра. */
	public void onFrame() {
		sampleFps();
		if (calibrator.isRunning() && MinecraftClient.getInstance().world == null) {
			calibrator.cancel("Автонастройка прервана: ты вышел из мира");
		}
		calibrator.onFrame();
		recorder.onFrame();
	}

	private void sampleFps() {
		framesThisSecond++;
		long now = System.nanoTime();
		long elapsed = now - secondStart;
		if (elapsed < 1_000_000_000L) return;
		double fps = framesThisSecond * 1.0e9 / elapsed;
		framesThisSecond = 0;
		secondStart = now;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world != null && mc.currentScreen == null) {
			fpsSamples[fpsPos] = fps;
			fpsPos = (fpsPos + 1) % fpsSamples.length;
			if (fpsCount < fpsSamples.length) fpsCount++;
		}
	}

	/** FPS игры с запасом: значение, ниже которого FPS падает лишь в пятой части времени. Меньше нуля, если замеров мало. */
	private double gameFps() {
		if (fpsCount < 3) return -1.0;
		double[] copy = java.util.Arrays.copyOf(fpsSamples, fpsCount);
		java.util.Arrays.sort(copy);
		return copy[(int) Math.floor((fpsCount - 1) * 0.2)];
	}

	/** Вызывается при закрытии игры: дописываем видео. */
	public static void shutdown() {
		if (INSTANCE != null) {
			INSTANCE.recorder.stopAndWait();
		}
	}

	// ------------------------------------------------------------------ действия

	// ------------------------------------------------------------------ для окна настроек

	public boolean isRecording() {
		return recorder.isActive();
	}

	public long recordingSeconds() {
		return recorder.elapsedSeconds();
	}

	public boolean isTuning() {
		return calibrator.isRunning();
	}

	public float tuneProgress() {
		return calibrator.progress();
	}

	/** Текст под кнопкой автонастройки: ход подбора или результат. */
	public String tuneStatus() {
		return calibrator.status();
	}

	// ------------------------------------------------------------------ запись

	/** Начать или остановить запись (кнопка в настройках и клавиша F9). */
	public void toggleRecording() {
		if (recorder.isActive()) {
			recorder.stop();
			Notifications.push("Запись остановлена, сохраняю файл…", false);
			return;
		}
		if (calibrator.isRunning()) {
			Notifications.pushWarning("Идёт автонастройка, запись можно начать после неё");
			return;
		}
		String error = recorder.start(folder(), RES_HEIGHT[resolution.getInt()], FPS_VALUES[fps.getInt()],
				quality.getInt(), audio.getBool(), codec.getInt());
		if (error != null) {
			Notifications.pushWarning("Запись: " + error);
		} else {
			Notifications.push("Запись началась: " + recorder.describe(), true);
		}
	}

	// ------------------------------------------------------------------ автонастройка

	/**
	 * Автонастройка: замеряет FPS игры и делает пробные записи, чтобы найти настройки, при которых запись не лагает и
	 * игра не тормозит. Результат записывается в настройки качества, разрешения, FPS и кодировщика.
	 */
	public void startTune() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (recorder.isActive()) {
			Notifications.pushWarning("Сначала останови запись");
			return;
		}
		if (calibrator.isRunning()) return;
		if (mc.world == null) {
			Notifications.pushWarning("Зайди в мир или на сервер и нажми «Автонастройка» ещё раз: так она учтёт нагрузку игры");
			return;
		}
		Path tmp = FabricLoader.getInstance().getGameDir().resolve("flyvisual").resolve("tmp");
		try {
			Files.createDirectories(tmp);
			try (java.util.stream.Stream<Path> old = Files.list(tmp)) {
				old.filter(p -> p.getFileName().toString().startsWith("trial_")).forEach(p -> {
					try {
						Files.deleteIfExists(p);
					} catch (java.io.IOException ignored) {
					}
				});
			}
		} catch (java.io.IOException e) {
			Notifications.pushWarning("Не удалось создать временную папку для автонастройки");
			return;
		}
		String exe = FfmpegSink.find(FabricLoader.getInstance().getGameDir(), folder());
		if (exe != null && !com.flyvisual.record.Encoders.isDetected()) {
			com.flyvisual.record.Encoders.detect(exe);
		}
		String gpu = null;
		try {
			gpu = GL11.glGetString(GL11.GL_RENDERER);
		} catch (Throwable ignored) {
		}
		calibrator.start(tmp, exe, gpu, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight(),
				gameFps(), this::applyTune);
		Notifications.push("Автонастройка запущена (около 20 секунд). Можно закрыть меню и играть как обычно", true);
	}

	/** Записывает подобранные значения в настройки. */
	private void applyTune(Calibrator.Result r) {
		int resIndex = 0;
		for (int i = 1; i < RES_HEIGHT.length; i++) {
			if (RES_HEIGHT[i] == r.height) resIndex = i;
		}
		int fpsIndex = 1;
		for (int i = 0; i < FPS_VALUES.length; i++) {
			if (FPS_VALUES[i] == r.fps) fpsIndex = i;
		}
		resolution.set(resIndex);
		fps.set(fpsIndex);
		quality.set(r.quality);
		codec.set(r.codec);
		FlyConfig.save();
		Notifications.push("Автонастройка готова: " + r.summary, true);
	}

	/** Папка для видео: выбранная тобой, иначе Видео/FlyVisual, иначе папка игры. */
	private Path folder() {
		try {
			String saved = FlyConfig.get().recordingFolder;
			if (saved != null && !saved.isBlank()) {
				return Path.of(saved);
			}
			Path videos = Path.of(System.getProperty("user.home", ".")).resolve("Videos");
			if (Files.isDirectory(videos)) {
				return videos.resolve("FlyVisual");
			}
		} catch (Exception ignored) {
		}
		return FabricLoader.getInstance().getGameDir().resolve("recordings");
	}

	private void openFolder() {
		try {
			Path dir = folder();
			Files.createDirectories(dir);
			Util.getOperatingSystem().open(dir.toFile());
		} catch (Throwable t) {
			Notifications.pushWarning("Не удалось открыть папку");
		}
	}

	/** Окно выбора папки. Открывается в отдельном потоке, чтобы игра не зависала. */
	private void pickFolder() {
		String start = folder().toString() + File.separator;
		Thread thread = new Thread(() -> {
			try {
				Class<?> dialogs = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
				Method select = dialogs.getMethod("tinyfd_selectFolderDialog", CharSequence.class, CharSequence.class);
				Object result = select.invoke(null, "Папка для записей FlyVisual", start);
				if (result != null) {
					String path = result.toString();
					MinecraftClient.getInstance().execute(() -> {
						FlyConfig.get().recordingFolder = path;
						FlyConfig.save();
						Notifications.push("Папка записи: " + path, true);
					});
				}
			} catch (Throwable t) {
				MinecraftClient.getInstance().execute(() -> Notifications.pushWarning(
						"Не удалось открыть окно выбора. Впиши путь в config/flyvisual.json (recordingFolder)"));
			}
		}, "FlyVisual-folder-picker");
		thread.setDaemon(true);
		thread.start();
	}
}
