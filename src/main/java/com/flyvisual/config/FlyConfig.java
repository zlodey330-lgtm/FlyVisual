package com.flyvisual.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Настройки мода. Файл: config/flyvisual.json
 */
public class FlyConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static FlyConfig instance = new FlyConfig();

	/** Акцентный цвет темы (RGB). По умолчанию серебристый (серо-чёрная тема). */
	public int accentColor = 0xB4B8BF;

	/** Версия темы: при переходе на серо-чёрную тему цвет один раз сбрасывается на серебристый. */
	public int themeVersion = 2;

	/** Заменять ли ванильное главное меню меню FlyVisual. */
	public boolean customTitleScreen = true;

	/** Колыхание листвы и земли на фоне главного меню. */
	public boolean titleWave = true;

	/** Лучи, туман, листья и искры на фоне главного меню. */
	public boolean titleParticles = true;

	/** Последний выбранный в меню модуль и прокрутка списка: меню открывается там, где ты закончил. */
	public String lastModule = "";
	public int lastScroll = 0;

	/** Тексты макросов (8 штук): сообщение или команда, если начинается с "/". */
	public String[] macros = {"", "", "", "", "", "", "", ""};

	/** Предметы для шейдера Hands (id), пусто = любые. */
	public java.util.List<String> handItems = new java.util.ArrayList<>();

	/** Клавиши макросов (GLFW-код, 0 = не назначена). */
	public int[] macroKeys = new int[8];

	/** Включён ли модуль: имя модуля -> состояние. */
	public Map<String, Boolean> modules = new LinkedHashMap<>();

	/** Позиции HUD-модулей: имя модуля -> [x, y]. */
	public Map<String, int[]> positions = new LinkedHashMap<>();

	/** Числовые настройки: "Модуль.Настройка" -> значение. */
	public Map<String, Double> settings = new LinkedHashMap<>();

	public static FlyConfig get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("flyvisual.json");
	}

	public static void load() {
		Path file = path();
		if (!Files.exists(file)) {
			save();
			return;
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			FlyConfig loaded = GSON.fromJson(reader, FlyConfig.class);
			if (loaded != null) {
				if (loaded.modules == null) loaded.modules = new LinkedHashMap<>();
				if (loaded.handItems == null) loaded.handItems = new java.util.ArrayList<>();
				if (loaded.positions == null) loaded.positions = new LinkedHashMap<>();
				if (loaded.settings == null) loaded.settings = new LinkedHashMap<>();
				if (loaded.themeVersion < 2) {
					loaded.accentColor = 0xB4B8BF;
					loaded.themeVersion = 2;
				}
				instance = loaded;
			}
		} catch (Exception e) {
			System.err.println("[FlyVisual] Не удалось прочитать конфиг, использую значения по умолчанию: " + e);
		}
	}

	public static void save() {
		try (Writer writer = Files.newBufferedWriter(path())) {
			GSON.toJson(instance, writer);
		} catch (Exception e) {
			System.err.println("[FlyVisual] Не удалось сохранить конфиг: " + e);
		}
	}
}
