package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Свой плащ. Плащ виден только тебе, от третьего лица (F5). Если надеты элитры, плащ ложится и на них.
 * Свои плащи: положи PNG (формат как у плаща Minecraft, 64x32 или HD в том же соотношении 2:1)
 * в папку flyvisual/capes внутри папки игры и перезапусти игру.
 * Подмена текстуры происходит в AbstractClientPlayerEntityMixin.
 */
public class CapesModule extends Module {
	private record Cape(String label, Identifier builtin, Path file) {
	}

	private static final String[][] BUILT_IN = {
			{"fv_red", "Фирменный FlyVisual"},
			{"red_eye", "Красный глаз"},
			{"black", "Чёрный"},
			{"sunset", "Закат"},
			{"obsidian", "Обсидиан"},
			{"night_mountains", "Ночные горы"},
			{"blue_clouds", "Синие облака"}
	};

	private final List<Cape> capes = new ArrayList<>();
	private final Map<Path, Identifier> loaded = new HashMap<>();
	private final Set<Path> failed = new HashSet<>();
	private final DoubleSetting selected;
	private int counter;

	public CapesModule() {
		super("Capes", "Your own cape (only you see it, press F5)");

		for (String[] item : BUILT_IN) {
			capes.add(new Cape(item[1], new Identifier("flyvisual", "textures/capes/" + item[0] + ".png"), null));
		}
		scanCustomCapes();

		String[] labels = new String[capes.size()];
		for (int i = 0; i < labels.length; i++) {
			labels[i] = capes.get(i).label();
		}
		selected = addChoice("Cape", 0, labels);
	}

	@Override
	protected boolean enabledByDefault() {
		return true;
	}

	private void scanCustomCapes() {
		Path dir = FabricLoader.getInstance().getGameDir().resolve("flyvisual").resolve("capes");
		try {
			Files.createDirectories(dir);
			try (Stream<Path> files = Files.list(dir)) {
				files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
						.sorted()
						.forEach(p -> {
							String name = p.getFileName().toString();
							capes.add(new Cape(name.substring(0, name.length() - 4), null, p));
						});
			}
		} catch (Exception e) {
			System.err.println("[FlyVisual] Не удалось прочитать папку с плащами: " + e);
		}
	}

	/** Текстура выбранного плаща, или null, если её нельзя загрузить. Вызывается из рендера. */
	public Identifier getTexture() {
		int index = Math.max(0, Math.min(capes.size() - 1, selected.getInt()));
		Cape cape = capes.get(index);
		if (cape.builtin() != null) {
			return cape.builtin();
		}
		return loadCustom(cape.file());
	}

	private Identifier loadCustom(Path file) {
		Identifier cached = loaded.get(file);
		if (cached != null) return cached;
		if (failed.contains(file)) return null;

		try (InputStream in = Files.newInputStream(file)) {
			NativeImage image = NativeImage.read(in);
			Identifier id = new Identifier("flyvisual", "capes/custom_" + (counter++));
			MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
			loaded.put(file, id);
			return id;
		} catch (Exception e) {
			System.err.println("[FlyVisual] Не удалось загрузить плащ " + file + ": " + e);
			failed.add(file);
			return null;
		}
	}
}
