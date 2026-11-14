package com.flyvisual.fpsboost;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;

/** Размер предмета в руке от первого лица (20..100 %). */
public final class HandScale {
	private HandScale() {
	}

	public static float scaleFor(AbstractClientPlayerEntity player, Hand hand, ItemStack stack) {
		if (stack.isEmpty()) return 1.0f;
		if (stack.isOf(Items.TOTEM_OF_UNDYING)) return percent(MobConfig.scaleTotem);
		if (stack.getItem() instanceof SwordItem) return percent(MobConfig.scaleSword);
		if (stack.isOf(Items.SHIELD)) return percent(MobConfig.scaleShield);
		if (player.isUsingItem() && player.getActiveHand() == hand) {
			UseAction action = stack.getUseAction();
			if (action == UseAction.DRINK) return percent(MobConfig.scalePotion);
			if (action == UseAction.EAT) return percent(MobConfig.scaleFood);
		}
		return 1.0f;
	}

	private static float percent(int value) {
		return Math.max(20, Math.min(100, value)) / 100.0f;
	}
}
