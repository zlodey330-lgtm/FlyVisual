package com.flyvisual.fpsboost;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EntityType;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Все настройки FPS Boost: дальность мобов, эффекты, частицы, размеры предметов. Хранится в config/fpsboost.json. */
public final class MobConfig {
	public static final int MAX = 128;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final Map<EntityType<?>, Integer> LIMITS = new HashMap<>();

	public static boolean hideFireSelf = false;
	public static boolean hideFireOthers = false;
	public static boolean hideGlow = false;
	public static boolean hideWaterAnim = false;
	public static boolean hideLavaAnim = false;
	public static boolean hidePortalAnim = false;
	public static boolean hideTotemAnim = false;
	public static boolean hideBreakAnim = false;
	public static boolean hideItemSpin = false;
	public static boolean hideHurtCam = false;
	public static boolean hideEnchantGlint = false;
	public static boolean hideFog = false;
	public static boolean hideShadows = false;
	public static boolean hidePumpkin = false;
	public static boolean hidePortalOverlay = false;

	public static final int SIGN_MIN = 4;
	public static final int SIGN_MAX = 64;
	public static int signDistance = 64;

	public static final int SCALE_MIN = 20;
	public static final int SCALE_MAX = 100;
	public static int scaleTotem = 100;
	public static int scaleSword = 100;
	public static int scaleShield = 100;
	public static int scalePotion = 100;
	public static int scaleFood = 100;

	public static final int SIMPLIFY_MIN = 8;
	public static final int SIMPLIFY_MAX = 64;
	public static boolean simplifyFar = false;
	public static int simplifyDistance = 24;
	public static boolean occlusionCull = false;

	public static boolean hideSky = false;
	public static boolean hideSunMoonStars = false;
	public static boolean hideClouds = false;
	public static boolean hideWeather = false;
	public static boolean hideBlockParticles = false;

	private static final Set<ParticleType<?>> HIDDEN_PARTICLES = new HashSet<>();

	private MobConfig() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("fpsboost.json");
	}

	private static Path profilesDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("fpsboost-configs");
	}

	public static boolean handScalingActive() {
		return scaleTotem < 100 || scaleSword < 100 || scaleShield < 100 || scalePotion < 100 || scaleFood < 100;
	}

	/** В 1.20.1 анимации текстур нельзя разделить по видам: если включена любая, замораживаются все. */
	public static boolean freezeAnimations() {
		return hideWaterAnim || hideLavaAnim || hidePortalAnim;
	}

	public static void setFreezeAnimations(boolean value) {
		hideWaterAnim = value;
		hideLavaAnim = value;
		hidePortalAnim = value;
	}

	public static boolean hasHiddenParticles() {
		return !HIDDEN_PARTICLES.isEmpty();
	}

	public static boolean isParticleHidden(ParticleType<?> type) {
		return HIDDEN_PARTICLES.contains(type);
	}

	public static void setParticleHidden(ParticleType<?> type, boolean hidden) {
		if (hidden) {
			HIDDEN_PARTICLES.add(type);
		} else {
			HIDDEN_PARTICLES.remove(type);
		}
	}

	public static void hideAllParticles(Collection<ParticleType<?>> types) {
		HIDDEN_PARTICLES.addAll(types);
	}

	public static void showAllParticles() {
		HIDDEN_PARTICLES.clear();
	}

	public static boolean hasLimits() {
		return !LIMITS.isEmpty();
	}

	public static Integer limit(EntityType<?> type) {
		return LIMITS.get(type);
	}

	public static int get(EntityType<?> type) {
		return LIMITS.getOrDefault(type, MAX);
	}

	public static void set(EntityType<?> type, int distance) {
		if (distance >= MAX) {
			LIMITS.remove(type);
		} else {
			LIMITS.put(type, Math.max(0, distance));
		}
	}

	public static void resetAll() {
		LIMITS.clear();
	}

	public static void disableAll(Collection<EntityType<?>> types) {
		for (EntityType<?> type : types) {
			LIMITS.put(type, 0);
		}
	}

	private static JsonObject toJson() {
		JsonObject distances = new JsonObject();
		for (Map.Entry<EntityType<?>, Integer> entry : LIMITS.entrySet()) {
			distances.addProperty(Registries.ENTITY_TYPE.getId(entry.getKey()).toString(), entry.getValue());
		}
		JsonObject root = new JsonObject();
		root.addProperty("hideFireSelf", hideFireSelf);
		root.addProperty("hideFireOthers", hideFireOthers);
		root.addProperty("hideGlow", hideGlow);
		root.addProperty("hideWaterAnim", hideWaterAnim);
		root.addProperty("hideLavaAnim", hideLavaAnim);
		root.addProperty("hidePortalAnim", hidePortalAnim);
		root.addProperty("hideTotemAnim", hideTotemAnim);
		root.addProperty("hideBreakAnim", hideBreakAnim);
		root.addProperty("hideItemSpin", hideItemSpin);
		root.addProperty("hideHurtCam", hideHurtCam);
		root.addProperty("hideEnchantGlint", hideEnchantGlint);
		root.addProperty("hideFog", hideFog);
		root.addProperty("hideShadows", hideShadows);
		root.addProperty("hidePumpkin", hidePumpkin);
		root.addProperty("hidePortalOverlay", hidePortalOverlay);
		root.addProperty("signDistance", signDistance);
		root.addProperty("scaleTotem", scaleTotem);
		root.addProperty("scaleSword", scaleSword);
		root.addProperty("scaleShield", scaleShield);
		root.addProperty("scalePotion", scalePotion);
		root.addProperty("scaleFood", scaleFood);
		root.addProperty("simplifyFar", simplifyFar);
		root.addProperty("simplifyDistance", simplifyDistance);
		root.addProperty("occlusionCull", occlusionCull);
		root.addProperty("hideSky", hideSky);
		root.addProperty("hideSunMoonStars", hideSunMoonStars);
		root.addProperty("hideClouds", hideClouds);
		root.addProperty("hideWeather", hideWeather);
		root.addProperty("hideBlockParticles", hideBlockParticles);
		JsonArray hidden = new JsonArray();
		for (ParticleType<?> type : HIDDEN_PARTICLES) {
			hidden.add(Registries.PARTICLE_TYPE.getId(type).toString());
		}
		root.add("hiddenParticles", hidden);
		root.add("mobDistances", distances);
		return root;
	}

	private static boolean flag(JsonObject root, String key) {
		return root.has(key) && root.get(key).getAsBoolean();
	}

	private static int scale(JsonObject root, String key) {
		if (!root.has(key)) return 100;
		return Math.max(SCALE_MIN, Math.min(SCALE_MAX, root.get(key).getAsInt()));
	}

	private static void fromJson(JsonObject root) {
		LIMITS.clear();
		hideFireSelf = flag(root, "hideFireSelf");
		hideFireOthers = flag(root, "hideFireOthers");
		hideGlow = flag(root, "hideGlow");
		hideWaterAnim = flag(root, "hideWaterAnim");
		hideLavaAnim = flag(root, "hideLavaAnim");
		hidePortalAnim = flag(root, "hidePortalAnim");
		hideTotemAnim = flag(root, "hideTotemAnim");
		hideBreakAnim = flag(root, "hideBreakAnim");
		hideItemSpin = flag(root, "hideItemSpin");
		hideHurtCam = flag(root, "hideHurtCam");
		hideEnchantGlint = flag(root, "hideEnchantGlint");
		hideFog = flag(root, "hideFog");
		hideShadows = flag(root, "hideShadows");
		hidePumpkin = flag(root, "hidePumpkin");
		hidePortalOverlay = flag(root, "hidePortalOverlay");
		signDistance = root.has("signDistance") ? Math.max(SIGN_MIN, Math.min(SIGN_MAX, root.get("signDistance").getAsInt())) : 64;
		scaleTotem = scale(root, "scaleTotem");
		scaleSword = scale(root, "scaleSword");
		scaleShield = scale(root, "scaleShield");
		scalePotion = scale(root, "scalePotion");
		scaleFood = scale(root, "scaleFood");
		simplifyFar = flag(root, "simplifyFar");
		if (root.has("simplifyDistance")) {
			simplifyDistance = Math.max(SIMPLIFY_MIN, Math.min(SIMPLIFY_MAX, root.get("simplifyDistance").getAsInt()));
		}
		occlusionCull = flag(root, "occlusionCull");
		hideSky = flag(root, "hideSky");
		hideSunMoonStars = flag(root, "hideSunMoonStars");
		hideClouds = flag(root, "hideClouds");
		hideWeather = flag(root, "hideWeather");
		hideBlockParticles = flag(root, "hideBlockParticles");

		HIDDEN_PARTICLES.clear();
		if (root.has("hiddenParticles")) {
			Set<String> ids = new HashSet<>();
			root.getAsJsonArray("hiddenParticles").forEach(e -> ids.add(e.getAsString()));
			for (ParticleType<?> type : Registries.PARTICLE_TYPE) {
				if (ids.contains(Registries.PARTICLE_TYPE.getId(type).toString())) {
					HIDDEN_PARTICLES.add(type);
				}
			}
		}

		if (!root.has("mobDistances")) return;
		JsonObject distances = root.getAsJsonObject("mobDistances");
		for (EntityType<?> type : Registries.ENTITY_TYPE) {
			String id = Registries.ENTITY_TYPE.getId(type).toString();
			if (distances.has(id)) {
				set(type, distances.get(id).getAsInt());
			}
		}
	}

	private static boolean readInto(Path path) {
		if (!Files.exists(path)) return false;
		try (BufferedReader reader = Files.newBufferedReader(path)) {
			fromJson(JsonParser.parseReader(reader).getAsJsonObject());
			return true;
		} catch (Exception e) {
			FpsBoostInit.LOGGER.warn("Не удалось прочитать " + path.getFileName() + ", беру значения по умолчанию", e);
			LIMITS.clear();
			return false;
		}
	}

	private static boolean writeTo(Path path) {
		try {
			Files.createDirectories(path.getParent());
			try (BufferedWriter writer = Files.newBufferedWriter(path)) {
				GSON.toJson(toJson(), writer);
			}
			return true;
		} catch (IOException e) {
			FpsBoostInit.LOGGER.warn("Не удалось сохранить " + path.getFileName(), e);
			return false;
		}
	}

	public static void load() {
		LIMITS.clear();
		readInto(file());
	}

	public static void save() {
		writeTo(file());
	}

	/** Имя конфига: буквы, цифры, пробел, _ и -, до 24 символов. */
	public static String sanitizeName(String raw) {
		if (raw == null) return null;
		String name = raw.trim().replaceAll("[^\\p{L}\\p{N} _-]", "").trim();
		if (name.length() > 24) {
			name = name.substring(0, 24).trim();
		}
		return name.isEmpty() ? null : name;
	}

	private static Path profileFile(String name) {
		return profilesDir().resolve(name + ".json");
	}

	public static List<String> listProfiles() {
		List<String> names = new ArrayList<>();
		Path dir = profilesDir();
		if (!Files.isDirectory(dir)) return names;
		try (Stream<Path> files = Files.list(dir)) {
			files.map(p -> p.getFileName().toString())
					.filter(n -> n.endsWith(".json"))
					.map(n -> n.substring(0, n.length() - 5))
					.forEach(names::add);
		} catch (IOException e) {
			FpsBoostInit.LOGGER.warn("Не удалось прочитать список конфигов", e);
		}
		names.sort(Comparator.comparing(String::toLowerCase));
		return names;
	}

	public static boolean saveProfile(String name) {
		return writeTo(profileFile(name));
	}

	public static boolean loadProfile(String name) {
		if (!readInto(profileFile(name))) return false;
		save();
		return true;
	}

	public static boolean deleteProfile(String name) {
		try {
			return Files.deleteIfExists(profileFile(name));
		} catch (IOException e) {
			FpsBoostInit.LOGGER.warn("Не удалось удалить конфиг " + name, e);
			return false;
		}
	}
}
