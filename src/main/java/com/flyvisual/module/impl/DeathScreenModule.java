package com.flyvisual.module.impl;

import com.flyvisual.module.Module;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class DeathScreenModule extends Module {
    private boolean chatAnnouncement = true;
    private String deathMessage = "Ты умер!";
    private boolean vignette = true;
    private boolean hardcore = false;
    private Text customDeathMessage;

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

    public void setMessage(Text message) {
        this.customDeathMessage = message;
    }

    public void render(DrawContext context, int width, int height) {
        // Отрисовка
    }
}
