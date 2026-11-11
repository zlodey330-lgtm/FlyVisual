package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

/**
 * Звук при ударе по цели. Слышишь только ты: звук играет на твоём клиенте, другим игрокам и серверу не отправляется.
 */
public class HitSoundsModule extends Module {
	private static final String[] NAMES = {
			"Опыт", "Колокольчик", "Динь", "Хрусталь", "Ксилофон", "Пиксель", "Попадание стрелы", "Уровень", "Щелчок", "Подбор"
	};
	private static final String[] IDS = {
			"entity.experience_orb.pickup", "block.note_block.bell", "block.note_block.pling", "block.amethyst_block.chime",
			"block.note_block.xylophone", "block.note_block.bit", "entity.arrow.hit_player", "entity.player.levelup",
			"ui.button.click", "entity.item.pickup"
	};
	private static final SoundEvent[] EVENTS = new SoundEvent[IDS.length];

	private final DoubleSetting sound = addChoice("Hit sound", 0, NAMES);
	private final DoubleSetting critSound = addChoice("Crit sound", 0, withSame());
	private final DoubleSetting volume = addSetting("Volume", 60.0, 5.0, 100.0, 5.0);
	private final DoubleSetting pitch = addSetting("Pitch", 100.0, 50.0, 200.0, 10.0);

	public HitSoundsModule() {
		super("Hit Sounds", "Sound when you hit a target");
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient() && isEnabled()) {
				onAttack(player, entity);
			}
			return ActionResult.PASS;
		});
	}

	private static String[] withSame() {
		String[] out = new String[NAMES.length + 1];
		out[0] = "Такой же";
		System.arraycopy(NAMES, 0, out, 1, NAMES.length);
		return out;
	}

	private static SoundEvent event(int index) {
		if (index < 0 || index >= IDS.length) return null;
		if (EVENTS[index] == null) {
			EVENTS[index] = SoundEvent.of(new Identifier(IDS[index]));
		}
		return EVENTS[index];
	}

	private void onAttack(PlayerEntity player, Entity target) {
		MinecraftClient mc = mc();
		if (mc.player == null || player != mc.player || !(target instanceof LivingEntity)) return;

		boolean crit = player.getAttackCooldownProgress(0.5f) > 0.9f
				&& player.fallDistance > 0.0f
				&& !player.isOnGround()
				&& !player.isClimbing()
				&& !player.isTouchingWater()
				&& !player.hasStatusEffect(StatusEffects.BLINDNESS)
				&& !player.hasVehicle()
				&& !player.isSprinting();

		int index = sound.getInt();
		if (crit && critSound.getInt() > 0) {
			index = critSound.getInt() - 1;
		}
		SoundEvent ev = event(index);
		if (ev == null) return;
		try {
			mc.getSoundManager().play(PositionedSoundInstance.master(ev, pitch.getFloat() / 100.0f, volume.getFloat() / 100.0f));
		} catch (Throwable ignored) {
		}
	}
}
