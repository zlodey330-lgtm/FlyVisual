package com.flyvisual.module.impl;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.hud.Notifications;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.record.Recorder;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

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

	private final DoubleSetting quality = addChoice("Качество", 2, "Низкое", "Среднее", "Высокое", "Максимальное");
	private final DoubleSetting resolution = addChoice("Разрешение", 0, "Как в окне", "720p", "1080p", "1440p", "4K");
	private final DoubleSetting fps = addChoice("FPS", 1, "24", "30", "48", "60", "90", "120", "144");
	private final DoubleSetting audio = addToggle("Записывать звук", true);

	@SuppressWarnings("unused")
	private final DoubleSetting startButton = addButton("Начать / остановить запись", this::toggleRecording);
	@SuppressWarnings("unused")
	private final DoubleSetting pickButton = addButton("Выбрать папку…", this::pickFolder);
	@SuppressWarnings("unused")
	private final DoubleSetting openButton = addButton("Открыть папку", this::openFolder);

	private final Recorder recorder = new Recorder();

	public RecordingModule() {
		super("Recording", "Record gameplay video");
		INSTANCE = this;
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
		recorder.onFrame();
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
		String error = recorder.start(folder(), RES_HEIGHT[resolution.getInt()], FPS_VALUES[fps.getInt()], quality.getInt(), audio.getBool());
		if (error != null) {
			Notifications.pushWarning("Запись: " + error);
		} else {
			Notifications.push("Запись началась: " + recorder.describe(), true);
		}
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
