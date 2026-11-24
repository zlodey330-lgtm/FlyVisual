package com.flyvisual.module.impl;

import com.flyvisual.hud.Notifications;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.util.Identifier;

/** Клиентские сезоны и погода. Шейдер влияет только на изображение этого клиента. */
public final class WeatherModule extends Module {
    private static final String[] MODES = {"Весна", "Лето", "Осень", "Зима", "Дождь"};
    private final DoubleSetting mode = addChoice("Season / Weather", 1, MODES);
    private final DoubleSetting shader = addToggle("Shader", true);

    private PostEffectProcessor processor;
    private int currentMode = -1;
    private int width;
    private int height;
    private boolean failed;

    public WeatherModule() {
        super("Weather", "Client-side seasons, rain and seasonal color shader");
    }

    public boolean isRainMode() {
        return isEnabled() && mode.getInt() == 4;
    }

    @Override
    public void onEnable() {
        failed = false;
    }

    @Override
    public void onDisable() {
        release();
    }

    private void release() {
        if (processor != null) {
            try { processor.close(); } catch (Exception ignored) { }
            processor = null;
        }
        currentMode = -1;
    }

    /** Вызывается из GameRendererMixin после отрисовки мира. */
    public void process(float tickDelta) {
        if (!isEnabled() || !shader.getBool() || failed) {
            release();
            return;
        }
        MinecraftClient mc = mc();
        if (mc.world == null) return;

        int selected = mode.getInt();
        try {
            int fw = mc.getWindow().getFramebufferWidth();
            int fh = mc.getWindow().getFramebufferHeight();
            if (processor == null || selected != currentMode) {
                release();
                processor = new PostEffectProcessor(mc.getTextureManager(), mc.getResourceManager(),
                        mc.getFramebuffer(), new Identifier("flyvisual", "shaders/post/season_" + selected + ".json"));
                processor.setupDimensions(fw, fh);
                currentMode = selected;
                width = fw;
                height = fh;
            } else if (fw != width || fh != height) {
                processor.setupDimensions(fw, fh);
                width = fw;
                height = fh;
            }

            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            processor.render(tickDelta);
            net.minecraft.client.gl.Framebuffer swap = processor.getSecondaryTarget("swap");
            net.minecraft.client.gl.Framebuffer main = mc.getFramebuffer();
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, swap.fbo);
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, main.fbo);
            org.lwjgl.opengl.GL30.glBlitFramebuffer(0, 0, swap.textureWidth, swap.textureHeight,
                    0, 0, main.textureWidth, main.textureHeight,
                    org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT, org.lwjgl.opengl.GL11.GL_NEAREST);
            main.beginWrite(false);
        } catch (Throwable error) {
            failed = true;
            release();
            Notifications.push("Weather: шейдер не загрузился", false);
        }
    }
}
