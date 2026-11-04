package com.flyvisual.module;

import com.flyvisual.module.impl.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private static final List<Module> MODULES = new ArrayList<>();

    private static final SkyModule SKY_MODULE = new SkyModule();
    private static final DeathScreenModule DEATH_SCREEN_MODULE = new DeathScreenModule();
    private static final FreeLookModule FREE_LOOK_MODULE = new FreeLookModule();
    private static final DamageParticlesModule DAMAGE_PARTICLES_MODULE = new DamageParticlesModule();
    private static final HandOffsetModule HAND_OFFSET_MODULE = new HandOffsetModule();
    private static final LowerFireModule LOWER_FIRE_MODULE = new LowerFireModule();
    private static final ItemHighlightModule ITEM_HIGHLIGHT_MODULE = new ItemHighlightModule();
    private static final PetModule PET_MODULE = new PetModule();
    private static final TimeChangerModule TIME_CHANGER_MODULE = new TimeChangerModule();
    private static final FogModule FOG_MODULE = new FogModule();
    private static final NoHurtCamModule NO_HURT_CAM_MODULE = new NoHurtCamModule();
    private static final TotemAnimationModule TOTEM_ANIMATION_MODULE = new TotemAnimationModule();
    private static final ZoomModule ZOOM_MODULE = new ZoomModule();
    private static final CrosshairModule CROSSHAIR_MODULE = new CrosshairModule();
    private static final CapesModule CAPES_MODULE = new CapesModule();

    public static void init() {
        MODULES.add(SKY_MODULE);
        MODULES.add(DEATH_SCREEN_MODULE);
        MODULES.add(FREE_LOOK_MODULE);
        MODULES.add(DAMAGE_PARTICLES_MODULE);
        MODULES.add(HAND_OFFSET_MODULE);
        MODULES.add(LOWER_FIRE_MODULE);
        MODULES.add(ITEM_HIGHLIGHT_MODULE);
        MODULES.add(PET_MODULE);
        MODULES.add(TIME_CHANGER_MODULE);
        MODULES.add(FOG_MODULE);
        MODULES.add(NO_HURT_CAM_MODULE);
        MODULES.add(TOTEM_ANIMATION_MODULE);
        MODULES.add(ZOOM_MODULE);
        MODULES.add(CROSSHAIR_MODULE);
        MODULES.add(CAPES_MODULE);
    }

    public static List<Module> all() {
        return MODULES;
    }

    public static void tick(MinecraftClient client) {
        for (Module module : MODULES) {
            // Тики модулей
        }
    }

    public static void renderHud(DrawContext context, float tickDelta) {
        for (Module module : MODULES) {
            // Отрисовка HUD
        }
    }

    public static void shutdown() {
        // Завершение работы
    }

    public static SkyModule sky() { return SKY_MODULE; }
    public static DeathScreenModule deathScreen() { return DEATH_SCREEN_MODULE; }
    public static FreeLookModule freeLook() { return FREE_LOOK_MODULE; }
    public static DamageParticlesModule damageParticles() { return DAMAGE_PARTICLES_MODULE; }
    public static HandOffsetModule handOffset() { return HAND_OFFSET_MODULE; }
    public static LowerFireModule lowerFire() { return LOWER_FIRE_MODULE; }
    public static ItemHighlightModule itemHighlight() { return ITEM_HIGHLIGHT_MODULE; }
    public static PetModule pet() { return PET_MODULE; }
    public static TimeChangerModule timeChanger() { return TIME_CHANGER_MODULE; }
    public static FogModule fog() { return FOG_MODULE; }
    public static NoHurtCamModule noHurtCam() { return NO_HURT_CAM_MODULE; }
    public static TotemAnimationModule totem() { return TOTEM_ANIMATION_MODULE; }
    public static ZoomModule zoom() { return ZOOM_MODULE; }
    public static CrosshairModule crosshair() { return CROSSHAIR_MODULE; }
    public static CapesModule capes() { return CAPES_MODULE; }
}
