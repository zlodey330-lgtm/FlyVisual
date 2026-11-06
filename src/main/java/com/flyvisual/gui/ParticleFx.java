package com.flyvisual.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.joml.Vector3f;

/** Список типов частиц для Aura и Trail. */
public final class ParticleFx {
	public static final String[] NAMES = {
			"Цветная пыль", "Звёзды", "Сердечки", "Огонь", "Синий огонь", "Магия", "Тотем", "Салют"
	};

	private ParticleFx() {
	}

	/**
	 * Создаёт частицу напрямую в менеджере частиц, в обход настройки Minecraft «Частицы» (все / меньше / минимум).
	 * Через world.addParticle при настройке «минимум» наши частицы просто не появлялись.
	 */
	public static void spawn(ParticleEffect effect, double x, double y, double z, double vx, double vy, double vz) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.particleManager == null) return;
		mc.particleManager.addParticle(effect, x, y, z, vx, vy, vz);
	}

	/** index из NAMES; rgb и size используются только для цветной пыли. */
	public static ParticleEffect create(int index, int rgb, float size) {
		return switch (index) {
			case 0 -> new DustParticleEffect(new Vector3f(ColorUtil.red(rgb), ColorUtil.green(rgb), ColorUtil.blue(rgb)), size);
			case 1 -> ParticleTypes.END_ROD;
			case 2 -> ParticleTypes.HEART;
			case 3 -> ParticleTypes.FLAME;
			case 4 -> ParticleTypes.SOUL_FIRE_FLAME;
			case 5 -> ParticleTypes.WITCH;
			case 6 -> ParticleTypes.TOTEM_OF_UNDYING;
			default -> ParticleTypes.FIREWORK;
		};
	}
}
