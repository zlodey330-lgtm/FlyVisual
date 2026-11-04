package com.flyvisual.module.impl;

import com.flyvisual.module.Module;

public class DeathScreenModule extends Module {
    private boolean chatAnnouncement = true;
    private String deathMessage = "Ты умер!";
    private boolean vignette = true;
    private boolean hardcore = false;

    public DeathScreenModule() {
        super("DeathScreen", "Кастомизация экрана смерти");
    }

    public boolean takeChatAnnouncement() {
        return chatAnnouncement;
    }

    public String getMessage() {
        return deathMessage;
    }

    public boolean showVignette() {
        return vignette;
    }

    public boolean isHardcore() {
        return hardcore;
    }
}
