package com.flyvisual.module.impl;

import com.flyvisual.hud.Notifications;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

/** Предупреждает уведомлением, когда броня или предмет в руке почти сломаны. */
public class DurabilityWarningModule extends Module {
	private final DoubleSetting below = addSetting("Warn below %", 15.0, 5.0, 50.0, 5.0);
	private final DoubleSetting includeHand = addToggle("Include held item", true);

	/** 0-3 броня, 4 предмет в руке: уже предупреждали про эту вещь. */
	private final boolean[] warned = new boolean[5];
	private final String[] lastItem = new String[5];
	private int ticks;

	public DurabilityWarningModule() {
		super("Durability Warning", "Warns when armor or tool is almost broken");
	}

	@Override
	public void onTick(MinecraftClient client) {
		if (client.player == null) return;
		if (++ticks % 20 != 0) return;

		for (int i = 0; i < 4; i++) {
			check(i, client.player.getInventory().getArmorStack(i));
		}
		check(4, includeHand.getBool() ? client.player.getMainHandStack() : ItemStack.EMPTY);
	}

	private void check(int slot, ItemStack stack) {
		if (stack.isEmpty() || !stack.isDamageable()) {
			warned[slot] = false;
			lastItem[slot] = null;
			return;
		}
		String id = stack.getItem().toString();
		if (!id.equals(lastItem[slot])) {
			lastItem[slot] = id;
			warned[slot] = false;
		}
		int percent = 100 - stack.getDamage() * 100 / Math.max(1, stack.getMaxDamage());
		if (percent <= below.get()) {
			if (!warned[slot]) {
				warned[slot] = true;
				Notifications.push(stack.getName().getString() + ": прочность " + percent + "%", false);
			}
		} else {
			warned[slot] = false;
		}
	}
}
