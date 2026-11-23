package com.flyvisual.module.impl;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.hud.Notifications;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.record.AutoTune;
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
 * Старт и стоп: клавиша F9 (меняется в настройках управления) или кнопка в настройках модуля.
 * Качество, разрешение и FPS выбираются в настройках, папку можно выбрать кнопкой.
 * <p>
 * С ffmpeg (в PATH или рядом с игрой) получается mp4 H.264 со звуком, без него встроенный AVI и отдельный WAV.
 */
public class RecordingModule extends Module {
	public static RecordingModule INSTANCE;

	private static final int[] RES_HEIGHT = {0, 720, 1080, 1440, 2160};
	private static final int[] FPS_VALUES = {24, 30, 48, 60, 90, 120, 144};

	private final KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"Запись: старт / стоп",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_F9,
			"category.flyvisual"
	));

	private final DoubleSetting autoTune = addToggle("Автонастройка под мой ПК", true);
	@SuppressWarnings("unused")
	private final DoubleSetting tuneButton = addButton("Подобрать настройки сейчас", this::showTune);
	private final DoubleSetting quality = addChoice("Качество", 2, "Низкое", "Среднее", "Высокое", "Максимальное");
	private final DoubleSetting resolution = addChoice("Разрешение", 2, "Как в окне", "720p", "1080p", "1440p", "4K");
	private final DoubleSetting fps = addChoice("FPS", 1, "24", "30", "48", "60", "90", "120", "144");
	private final DoubleSetting codec = addChoice("Кодировщик", 0, "Авто (видеокарта, если есть)", "NVIDIA (видеокарта)",
			"AMD (видеокарта)", "Intel (видеокарта)", "Процессор (x264)", "Motion JPEG (как в Bandicam)");
	private final DoubleSetting audio = addToggle("Записывать звук", true);

	@SuppressWarnings("unused")
	private final DoubleSetting startButton = addButton("Начать / остановить запись", this::toggleRecording);
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
	private boolean lastWasAuto;

	public RecordingModule() {
		super("Recording", "Record gameplay video");
		INSTANCE = this;
		// Заранее, в фоне, проверяем, какие видеокарты умеют кодировать видео
		com.flyvisual.record.Encoders.detectAsync(FabricLoader.getInstance().getGameDir());
		recorder.setFinishListener(this::onRecordingFinished);
	}

	@Override
	protected boolean enabledByDefault() {
		return true;
	}

	@Override
	public void onDisable() {
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

	private void toggleRecording() {
		if (recorder.isActive()) {
			recorder.stop();
			Notifications.push("Запись остановлена, сохраняю файл…", false);
			return;
		}
		int res = RES_HEIGHT[resolution.getInt()];
		int rate = FPS_VALUES[fps.getInt()];
		int q = quality.getInt();
		int codecChoice = codec.getInt();
		boolean auto = autoTune.getBool();
		if (auto) {
			AutoTune.Config cfg = tune();
			res = cfg.resHeight;
			rate = cfg.fps;
			q = cfg.quality;
			codecChoice = cfg.codec;
		}
		lastWasAuto = auto;
		String error = recorder.start(folder(), res, rate, q, audio.getBool(), codecChoice);
		if (error != null) {
			Notifications.pushWarning("Запись: " + error);
		} else {
			Notifications.push((auto ? "Запись началась (автонастройка): " : "Запись началась: ") + recorder.describe(), true);
		}
	}

	/** Подбирает настройки под этот компьютер. */
	private AutoTune.Config tune() {
		MinecraftClient mc = MinecraftClient.getInstance();
		int w = mc.getWindow().getFramebufferWidth();
		int h = mc.getWindow().getFramebufferHeight();
		int cores = Runtime.getRuntime().availableProcessors();
		String exe = FfmpegSink.find(FabricLoader.getInstance().getGameDir(), folder());
		if (exe != null && !com.flyvisual.record.Encoders.isDetected()) {
			com.flyvisual.record.Encoders.detect(exe);
		}
		String gpu = null;
		try {
			gpu = GL11.glGetString(GL11.GL_RENDERER);
		} catch (Throwable ignored) {
		}
		return AutoTune.choose(w, h, gameFps(), cores, exe != null, gpu, FlyConfig.get().recordingPenalty);
	}

	/** Кнопка в настройках: показывает, что автонастройка выбрала для этого компьютера. */
	private void showTune() {
		AutoTune.Config cfg = tune();
		Notifications.push("Автонастройка: " + cfg.summary, true);
	}

	/** После записи: если было много повторов кадров, в следующий раз автонастройка выберет настройки полегче. */
	private void onRecordingFinished(int dupPercent, boolean failed) {
		if (!lastWasAuto) return;
		FlyConfig cfg = FlyConfig.get();
		int before = cfg.recordingPenalty;
		if (dupPercent >= 15 || failed) {
			cfg.recordingPenalty = Math.min(5, before + 1);
			if (cfg.recordingPenalty != before) {
				Notifications.push("Автонастройка запомнила лаги: в следующий раз выберу настройки полегче", false);
			}
		} else if (dupPercent <= 2 && before > 0) {
			cfg.recordingPenalty = before - 1;
		}
		if (cfg.recordingPenalty != before) FlyConfig.save();
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
