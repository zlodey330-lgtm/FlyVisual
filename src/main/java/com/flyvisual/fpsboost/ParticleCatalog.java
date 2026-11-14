package com.flyvisual.fpsboost;

import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Все частицы игры с русскими названиями (для известных), остальные с обычным именем. */
public final class ParticleCatalog {
	private static final Map<String, String> RU = Map.ofEntries(
			Map.entry("crit", "Крит"),
			Map.entry("enchanted_hit", "Удар с зачарованием"),
			Map.entry("damage_indicator", "Индикатор урона"),
			Map.entry("sweep_attack", "Взмах мечом"),
			Map.entry("flame", "Пламя"),
			Map.entry("small_flame", "Малое пламя"),
			Map.entry("soul_fire_flame", "Огонь душ"),
			Map.entry("smoke", "Дым"),
			Map.entry("large_smoke", "Большой дым"),
			Map.entry("campfire_cosy_smoke", "Дым костра"),
			Map.entry("campfire_signal_smoke", "Сигнальный дым"),
			Map.entry("explosion", "Взрыв"),
			Map.entry("explosion_emitter", "Источник взрыва"),
			Map.entry("poof", "Облачко"),
			Map.entry("totem_of_undying", "Тотем бессмертия"),
			Map.entry("heart", "Сердце"),
			Map.entry("angry_villager", "Злой житель"),
			Map.entry("happy_villager", "Довольный житель"),
			Map.entry("bubble", "Пузырь"),
			Map.entry("splash", "Брызги"),
			Map.entry("rain", "Капли дождя"),
			Map.entry("cloud", "Облако"),
			Map.entry("dust", "Пыль"),
			Map.entry("lava", "Лава"),
			Map.entry("portal", "Портал"),
			Map.entry("enchant", "Чары"),
			Map.entry("effect", "Эффект зелья"),
			Map.entry("entity_effect", "Эффект зелья (моб)"),
			Map.entry("ambient_entity_effect", "Фоновый эффект"),
			Map.entry("instant_effect", "Мгновенный эффект"),
			Map.entry("witch", "Ведьма"),
			Map.entry("end_rod", "Стержень Края"),
			Map.entry("dragon_breath", "Дыхание дракона"),
			Map.entry("firework", "Фейерверк"),
			Map.entry("flash", "Вспышка"),
			Map.entry("note", "Нота"),
			Map.entry("block", "Осколки блока"),
			Map.entry("item", "Осколки предмета"),
			Map.entry("snowflake", "Снежинка"),
			Map.entry("soul", "Душа"),
			Map.entry("glow", "Свечение"),
			Map.entry("electric_spark", "Электрические искры"),
			Map.entry("squid_ink", "Чернила"),
			Map.entry("ash", "Пепел"),
			Map.entry("white_ash", "Белый пепел"),
			Map.entry("underwater", "Под водой"),
			Map.entry("bubble_column_up", "Столб пузырей"),
			Map.entry("dripping_water", "Капающая вода"),
			Map.entry("dripping_lava", "Капающая лава"),
			Map.entry("large_smoke_cloud", "Облако дыма"),
			Map.entry("trial_spawner_detection", "Спавнер испытаний"),
			Map.entry("gust", "Порыв ветра"),
			Map.entry("vault_connection", "Связь хранилища")
	);
	private static List<Entry> cache;

	private ParticleCatalog() {
	}

	public static List<Entry> get() {
		if (cache == null) {
			cache = build();
		}
		return cache;
	}

	private static List<Entry> build() {
		List<Entry> list = new ArrayList<>();
		for (ParticleType<?> type : Registries.PARTICLE_TYPE) {
			String full = Registries.PARTICLE_TYPE.getId(type).toString();
			String path = full.contains(":") ? full.substring(full.indexOf(':') + 1) : full;
			String name = RU.getOrDefault(path, pretty(path));
			list.add(new Entry(type, path, name));
		}
		list.sort(Comparator.comparing((Entry e) -> e.name().toLowerCase(Locale.ROOT)));
		return list;
	}

	private static String pretty(String path) {
		String s = path.replace('_', ' ');
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	public record Entry(ParticleType<?> type, String id, String name) {
	}
}
