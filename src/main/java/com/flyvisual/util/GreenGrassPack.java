package com.flyvisual.util;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Встроенный ресурс-пак «зелёный блок травы»: все грани блока травы как верхушка (с оттенком биома).
 * Пак лежит внутри мода. Модуль «Оптимизация» включает и выключает его сам и перезагружает ресурсы.
 * При любой ошибке просто ничего не происходит.
 */
public final class GreenGrassPack {
	private static final String ID = "green_grass";

	private GreenGrassPack() {
	}

	/** Регистрирует пак при запуске игры (в списке ресурс-паков он тоже появится). */
	public static void register() {
		try {
			FabricLoader.getInstance().getModContainer("flyvisual").ifPresent(container ->
					ResourceManagerHelper.registerBuiltinResourcePack(new Identifier("flyvisual", ID), container,
							ResourcePackActivationType.NORMAL));
		} catch (Throwable ignored) {
		}
	}

	private static String profileName(ResourcePackManager manager) {
		for (ResourcePackProfile profile : manager.getProfiles()) {
			if (profile.getName().contains(ID)) return profile.getName();
		}
		return null;
	}

	public static boolean isEnabled() {
		try {
			ResourcePackManager manager = MinecraftClient.getInstance().getResourcePackManager();
			String name = profileName(manager);
			return name != null && manager.getEnabledNames().contains(name);
		} catch (Throwable t) {
			return false;
		}
	}

	/** Просто перезагружает ресурсы (нужно, чтобы применить уровень качества текстур). */
	public static void reload() {
		try {
			MinecraftClient.getInstance().reloadResources();
		} catch (Throwable ignored) {
		}
	}

	/** Включает или выключает пак и перезагружает ресурсы. */
	public static void set(boolean on) {
		try {
			MinecraftClient mc = MinecraftClient.getInstance();
			ResourcePackManager manager = mc.getResourcePackManager();
			String name = profileName(manager);
			if (name == null) return;
			List<String> enabled = new ArrayList<>(manager.getEnabledNames());
			boolean has = enabled.contains(name);
			if (on == has) return;
			if (on) {
				enabled.add(name);
			} else {
				enabled.remove(name);
			}
			manager.setEnabledProfiles(enabled);
			mc.options.refreshResourcePacks(manager);
			mc.reloadResources();
		} catch (Throwable ignored) {
		}
	}
}
