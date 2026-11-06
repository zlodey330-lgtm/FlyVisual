package com.flyvisual.module;

import net.minecraft.item.Item;

/** Модуль с выбираемым списком предметов (для окна поиска предметов). */
public interface ItemSelection {
	boolean isPicked(Item item);

	int pickedCount();

	void togglePicked(Item item);

	void clearPicked();
}
