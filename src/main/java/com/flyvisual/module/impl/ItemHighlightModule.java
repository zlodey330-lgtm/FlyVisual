package com.flyvisual.module.impl;

import com.flyvisual.module.Module;
import net.minecraft.item.Item;
import java.util.HashSet;
import java.util.Set;

public class ItemHighlightModule extends Module {
    private final Set<Item> pickedItems = new HashSet<>();

    public ItemHighlightModule() {
        super("ItemHighlight", "Подсветка предметов");
    }

    public boolean isPicked(Item item) {
        return pickedItems.contains(item);
    }

    public int pickedCount() {
        return pickedItems.size();
    }

    public void clearPicked() {
        pickedItems.clear();
    }

    public void togglePicked(Item item) {
        if (pickedItems.contains(item)) {
            pickedItems.remove(item);
        } else {
            pickedItems.add(item);
        }
    }
}
