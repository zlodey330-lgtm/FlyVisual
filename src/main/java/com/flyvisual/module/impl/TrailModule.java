package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.ColorUtil;
import com.flyvisual.util.ParticleFx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * Шлейф: частицы остаются за тобой, пока ты двигаешься (бежишь, прыгаешь, летишь на элитрах).
 * Видны только тебе.
 */
public class TrailModule extends Module {
	private static final String[] HEIGHTS = {"Ноги", "Тело", "Голова"};

	private final DoubleSetting type = addChoice("Type", 0, ParticleFx.NAMES);
	private final DoubleSetting height = addChoice("Height", 0, HEIGHTS);
	private final DoubleSetting amount = addSetting("Amount", 3.0, 1.0, 10.0, 1.0);
	private final DoubleSetting size = addSetting("Particle size", 1.0, 0.5, 2.5, 0.1);
	private final DoubleSetting hue = addSetting("Color (hue)", 0.0, 0.0, 355.0, 5.0);
	private final DoubleSetting rainbow = addToggle("Rainbow", true);

	private int ticks;

	public TrailModule() {
		super("Trail", "Particle trail behind you");
	}

	@Override
	public void onTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;
		if (player == null || world == null) return;

		Vec3d velocity = player.getVelocity();
		double speed = Math.hypot(velocity.x, velocity.z);
		if (speed < 0.03 && !player.isFallFlying()) return;

		ticks++;
		Random random = world.getRandom();
		double yOffset = switch (height.getInt()) {
			case 1 -> player.getHeight() * 0.5;
			case 2 -> player.getHeight() * 0.9;
			default -> 0.1;
		};

		for (int i = 0; i < amount.getInt(); i++) {
			// Чуть позади по направлению движения и с небольшим разбросом
			double back = 0.2 + random.nextDouble() * 0.3;
			double x = player.getX() - velocity.x * back * 4.0 + (random.nextDouble() - 0.5) * 0.3;
			double y = player.getY() + yOffset + (random.nextDouble() - 0.5) * 0.2;
			double z = player.getZ() - velocity.z * back * 4.0 + (random.nextDouble() - 0.5) * 0.3;

			float h = rainbow.getBool() ? (ticks * 6 + i * 25) % 360 : hue.getFloat();
			int rgb = ColorUtil.hsv(h, 0.85f, 1.0f);
			world.addParticle(ParticleFx.create(type.getInt(), rgb, size.getFloat()), x, y, z, 0.0, 0.0, 0.0);
		}
	}
}
