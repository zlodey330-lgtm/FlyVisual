package com.flyvisual.module;

import com.flyvisual.module.impl.ItemHighlightModule;

public class ModuleManager {
    private static final ItemHighlightModule itemHighlightModule = new ItemHighlightModule();

    public static ItemHighlightModule itemHighlight() {
        return itemHighlightModule;
    }
}
