package com.flyvisual.fpsboost;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Список мобов для вкладки «Мобы»: мирные, враждебные и боссы. */
public final class MobCatalog {
	private static final Set<String> EXTRA_PEACEFUL = Set.of("minecraft:villager", "minecraft:wandering_trader",
			"minecraft:iron_golem", "minecraft:snow_golem");
	private static List<Mob> cache;

	private MobCatalog() {
	}

	public static List<Mob> get() {
		if (cache == null) {
			cache = build();
		}
		return cache;
	}

	private static List<Mob> build() {
		List<Mob> mobs = new ArrayList<>();
		for (EntityType<?> type : Registries.ENTITY_TYPE) {
			if (type == EntityType.PLAYER || !type.isSummonable()) continue;
			Section section = classify(type);
			if (section == null) continue;
			mobs.add(new Mob(type, section, type.getName().getString()));
		}
		mobs.sort(Comparator.comparingInt((Mob m) -> m.section().ordinal())
				.thenComparing(Mob::name, String.CASE_INSENSITIVE_ORDER));
		return mobs;
	}

	private static Section classify(EntityType<?> type) {
		if (type == EntityType.ENDER_DRAGON || type == EntityType.WITHER) {
			return Section.BOSSES;
		}
		String id = Registries.ENTITY_TYPE.getId(type).toString();
		if (EXTRA_PEACEFUL.contains(id)) {
			return Section.PEACEFUL;
		}
		SpawnGroup group = type.getSpawnGroup();
		if (group == SpawnGroup.MONSTER) {
			return Section.HOSTILE;
		}
		if (group == SpawnGroup.CREATURE || group == SpawnGroup.AMBIENT || group == SpawnGroup.AXOLOTLS
				|| group == SpawnGroup.UNDERGROUND_WATER_CREATURE || group == SpawnGroup.WATER_CREATURE
				|| group == SpawnGroup.WATER_AMBIENT) {
			return Section.PEACEFUL;
		}
		return null;
	}

	public enum Section {
		PEACEFUL("МИРНЫЕ МОБЫ", 0xFF7FD88C),
		HOSTILE("ВРАЖДЕБНЫЕ МОБЫ", 0xFFFF6B6B),
		BOSSES("БОССЫ", 0xFFFFC857);

		public final String title;
		public final int color;

		Section(String title, int color) {
			this.title = title;
			this.color = color;
		}
	}

	public record Mob(EntityType<?> type, Section section, String name) {
	}
}
