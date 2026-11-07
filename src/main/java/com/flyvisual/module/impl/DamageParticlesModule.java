package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.random.Random;

/**
 * Частицы при ударе по цели (звёздочки, сердечки, огонь и т.д.).
 * Видны только тебе: это эффект на твоём клиенте, другим игрокам он не отправляется.
 */
public class DamageParticlesModule extends Module {
	private static final String[] NAMES = {
			"Крит", "Искры", "Сердечки", "Огонь", "Звёзды",
			"Тотем", "Дым", "Зелёные звёзды", "Салют", "Синий огонь"
	};
	private static final ParticleEffect[] TYPES = {
			ParticleTypes.CRIT, ParticleTypes.ENCHANTED_HIT, ParticleTypes.HEART, ParticleTypes.FLAME,
			ParticleTypes.END_ROD, ParticleTypes.TOTEM_OF_UNDYING, ParticleTypes.SMOKE,
			ParticleTypes.HAPPY_VILLAGER, ParticleTypes.FIREWORK, ParticleTypes.SOUL_FIRE_FLAME
	};

	private final DoubleSetting type = addChoice("Type", 4, NAMES);
	private final DoubleSetting amount = addSetting("Amount", 12.0, 1.0, 50.0, 1.0);
	private final DoubleSetting spread = addSetting("Spread", 0.5, 0.1, 1.5, 0.1);
	private final DoubleSetting critsOnly = addToggle("Crits only", false);
	private final DoubleSetting hideVanilla = addToggle("Hide vanilla crit", true);

	public DamageParticlesModule() {
		super("Damage Particles", "Particles when you hit a target");

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient() && isEnabled()) {
				onAttack(player, entity);
			}
			return ActionResult.PASS;
		});
	}

	/** Нужно ли прятать обычные частицы крита и зачарованного удара (их заменяют наши). */
	public boolean hidesVanilla() {
		return isEnabled() && hideVanilla.getBool();
	}

	private void onAttack(PlayerEntity player, Entity target) {
		MinecraftClient mc = mc();
		if (mc.world == null || player != mc.player || !(target instanceof LivingEntity)) return;
		OptimizationModule opt = com.flyvisual.module.ModuleManager.optimization();
		if (opt != null && opt.blocksHitParticles()) return;

		// Условия критического удара, как в ванильной игре
		boolean crit = player.getAttackCooldownProgress(0.5f) > 0.9f
				&& player.fallDistance > 0.0f
				&& !player.isOnGround()
				&& !player.isClimbing()
				&& !player.isTouchingWater()
				&& !player.hasStatusEffect(StatusEffects.BLINDNESS)
				&& !player.hasVehicle()
				&& !player.isSprinting();
		if (critsOnly.getBool() && !crit) return;

		ParticleEffect effect = TYPES[Math.max(0, Math.min(TYPES.length - 1, type.getInt()))];
		int count = amount.getInt();
		double power = spread.get();
		Random random = mc.world.getRandom();

		double cx = target.getX();
		double cy = target.getBodyY(0.6);
		double cz = target.getZ();
		double width = Math.max(0.3, target.getWidth());

		OptimizationModule.ownSpawn = true;
		try {
		for (int i = 0; i < count; i++) {
			double px = cx + (random.nextDouble() - 0.5) * width;
			double py = cy + (random.nextDouble() - 0.5) * 0.4;
			double pz = cz + (random.nextDouble() - 0.5) * width;

			double vx = random.nextGaussian() * power * 0.25;
			double vy = Math.abs(random.nextGaussian()) * power * 0.2 + 0.08;
			double vz = random.nextGaussian() * power * 0.25;

			mc.world.addParticle(effect, px, py, pz, vx, vy, vz);
		}
		} finally {
			OptimizationModule.ownSpawn = false;
		}
	}
}
