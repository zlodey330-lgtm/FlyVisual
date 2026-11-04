package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.ColorUtil;
import com.flyvisual.util.ParticleFx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.random.Random;

/**
 * Аура: частицы вокруг тебя (облако, вращающееся кольцо или поднимающаяся спираль).
 * Видна только тебе, другим игрокам эти частицы не отправляются.
 */
public class AuraModule extends Module {
	private static final String[] PATTERNS = {"Облако", "Кольцо", "Спираль"};

	private final DoubleSetting pattern = addChoice("Pattern", 0, PATTERNS);
	private final DoubleSetting type = addChoice("Type", 0, ParticleFx.NAMES);
	private final DoubleSetting amount = addSetting("Amount", 3.0, 1.0, 12.0, 1.0);
	private final DoubleSetting radius = addSetting("Radius", 0.8, 0.3, 2.0, 0.1);
	private final DoubleSetting hue = addSetting("Color (hue)", 0.0, 0.0, 355.0, 5.0);
	private final DoubleSetting rainbow = addToggle("Rainbow", false);
	private final DoubleSetting firstPerson = addToggle("Show in first person", true);

	private int ticks;

	public AuraModule() {
		super("Aura", "Particles around you");
	}

	@Override
	public void onTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;
		if (player == null || world == null) return;
		if (!firstPerson.getBool() && client.options.getPerspective().isFirstPerson()) return;

		ticks++;
		Random random = world.getRandom();
		int count = amount.getInt();
		double r = radius.get();
		double height = player.getHeight();

		for (int i = 0; i < count; i++) {
			double angle;
			double y;
			double dist = r;

			switch (pattern.getInt()) {
				case 1 -> {
					angle = ticks * 0.2 + i * 2.0 * Math.PI / count;
					y = player.getY() + height * 0.5;
				}
				case 2 -> {
					angle = ticks * 0.25 + i * 2.0 * Math.PI / count;
					y = player.getY() + ((ticks * 0.04 + i * 0.37) % 1.0) * height;
				}
				default -> {
					angle = random.nextDouble() * 2.0 * Math.PI;
					dist = r * (0.6 + 0.4 * random.nextDouble());
					y = player.getY() + random.nextDouble() * height;
				}
			}

			double x = player.getX() + Math.cos(angle) * dist;
			double z = player.getZ() + Math.sin(angle) * dist;

			float h = rainbow.getBool() ? (ticks * 4 + i * 20) % 360 : hue.getFloat();
			int rgb = ColorUtil.hsv(h, 0.85f, 1.0f);
			world.addParticle(ParticleFx.create(type.getInt(), rgb, 1.0f), x, y, z, 0.0, 0.01, 0.0);
		}
	}
}
