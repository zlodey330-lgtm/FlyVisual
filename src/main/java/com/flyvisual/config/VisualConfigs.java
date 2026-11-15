package com.flyvisual.config;

import com.flyvisual.module.ModuleManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Именованные наборы настроек визуала: config/flyvisual-configs/&lt;имя&gt;.json */
public class VisualConfigs {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static class Snapshot {
		int accentColor;
		Map<String, Boolean> modules = new LinkedHashMap<>();
		Map<String, int[]> positions = new LinkedHashMap<>();
		Map<String, Double> settings = new LinkedHashMap<>();
	}

	private static Path dir() {
		Path d = FabricLoader.getInstance().getConfigDir().resolve("flyvisual-configs");
		try {
			Files.createDirectories(d);
		} catch (Exception ignored) {
		}
		return d;
	}

	public static String clean(String name) {
		return name == null ? "" : name.trim().replaceAll("[^\\p{L}\\p{N} _-]", "");
	}

	public static List<String> list() {
		List<String> out = new ArrayList<>();
		try (var s = Files.list(dir())) {
			s.forEach(p -> {
				String f = p.getFileName().toString();
				if (f.endsWith(".json")) out.add(f.substring(0, f.length() - 5));
			});
		} catch (Exception ignored) {
		}
		java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
		return out;
	}

	public static boolean save(String rawName) {
		String name = clean(rawName);
		if (name.isEmpty()) return false;
		FlyConfig c = FlyConfig.get();
		Snapshot s = new Snapshot();
		s.accentColor = c.accentColor;
		s.modules.putAll(c.modules);
		s.positions.putAll(c.positions);
		s.settings.putAll(c.settings);
		try (Writer w = Files.newBufferedWriter(dir().resolve(name + ".json"))) {
			GSON.toJson(s, w);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static boolean load(String name) {
		try (Reader r = Files.newBufferedReader(dir().resolve(clean(name) + ".json"))) {
			Snapshot s = GSON.fromJson(r, Snapshot.class);
			if (s == null) return false;
			FlyConfig c = FlyConfig.get();
			c.accentColor = s.accentColor;
			c.modules.clear();
			if (s.modules != null) c.modules.putAll(s.modules);
			c.positions.clear();
			if (s.positions != null) c.positions.putAll(s.positions);
			c.settings.clear();
			if (s.settings != null) c.settings.putAll(s.settings);
			FlyConfig.save();
			ModuleManager.reloadAll();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static void delete(String name) {
		try {
			Files.deleteIfExists(dir().resolve(clean(name) + ".json"));
		} catch (Exception ignored) {
		}
	}
}
