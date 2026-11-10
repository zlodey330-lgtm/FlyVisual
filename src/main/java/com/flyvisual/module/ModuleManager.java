package com.flyvisual.module;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.module.impl.ArmorHudModule;
import com.flyvisual.module.impl.AspectRatioModule;
import com.flyvisual.module.impl.HealthIndicatorModule;
import com.flyvisual.module.impl.MacroModule;
import com.flyvisual.module.impl.MouseFixModule;
import com.flyvisual.module.impl.SaturationModule;
import com.flyvisual.module.impl.TargetEspModule;
import com.flyvisual.module.impl.AuraModule;
import com.flyvisual.module.impl.BlockOutlineModule;
import com.flyvisual.module.impl.CapesModule;
import com.flyvisual.module.impl.CrosshairModule;
import com.flyvisual.module.impl.CustomChatModule;
import com.flyvisual.module.impl.DeathScreenModule;
import com.flyvisual.module.impl.EmoteModule;
import com.flyvisual.module.impl.ItemHighlightModule;
import com.flyvisual.module.impl.PetModule;
import com.flyvisual.module.impl.DurabilityWarningModule;
import com.flyvisual.module.impl.ServerIpModule;
import com.flyvisual.module.impl.CoordinatesModule;
import com.flyvisual.module.impl.DamageParticlesModule;
import com.flyvisual.module.impl.OptimizationModule;
import com.flyvisual.module.impl.FpsModule;
import com.flyvisual.module.impl.FogModule;
import com.flyvisual.module.impl.FreeLookModule;
import com.flyvisual.module.impl.FullbrightModule;
import com.flyvisual.module.impl.HandOffsetModule;
import com.flyvisual.module.impl.HitColorModule;
import com.flyvisual.module.impl.HitboxModule;
import com.flyvisual.module.impl.KeybindHudModule;
import com.flyvisual.module.impl.LowerFireModule;
import com.flyvisual.module.impl.NoHurtCamModule;
import com.flyvisual.module.impl.PingModule;
import com.flyvisual.module.impl.PotionHudModule;
import com.flyvisual.module.impl.SkyModule;
import com.flyvisual.module.impl.TargetHudModule;
import com.flyvisual.module.impl.TimeChangerModule;
import com.flyvisual.module.impl.TotemAnimationModule;
import com.flyvisual.module.impl.TrailModule;
import com.flyvisual.module.impl.WatermarkModule;
import com.flyvisual.module.impl.ZoomModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleManager {
	private static final List<Module> MODULES = new ArrayList<>();

	private static FullbrightModule fullbright;
	private static NoHurtCamModule noHurtCam;
	private static LowerFireModule lowerFire;
	private static ZoomModule zoom;
	private static FreeLookModule freeLook;
	private static HandOffsetModule handOffset;
	private static FogModule fog;
	private static TimeChangerModule timeChanger;
	private static TotemAnimationModule totem;
	private static CrosshairModule crosshair;
	private static CapesModule capes;
	private static SkyModule sky;
	private static DamageParticlesModule damageParticles;
	private static OptimizationModule optimization;
	private static CustomChatModule customChat;
	private static PetModule pet;
	private static HitboxModule hitbox;
	private static AspectRatioModule aspectRatio;
	private static EmoteModule emotes;
	private static MouseFixModule mouseFix;
	private static com.flyvisual.module.impl.HandsModule hands;
	private static com.flyvisual.module.impl.KillEffectModule killEffect;
	private static com.flyvisual.module.impl.JumpEffectModule jumpEffect;
	private static SaturationModule saturation;
	private static PotionHudModule potionHud;
	private static DeathScreenModule deathScreen;

	private ModuleManager() {
	}

	public static void init() {
		fullbright = new FullbrightModule();
		noHurtCam = new NoHurtCamModule();
		lowerFire = new LowerFireModule();
		zoom = new ZoomModule();
		freeLook = new FreeLookModule();
		handOffset = new HandOffsetModule();
		fog = new FogModule();
		timeChanger = new TimeChangerModule();
		totem = new TotemAnimationModule();
		crosshair = new CrosshairModule();
		capes = new CapesModule();
		sky = new SkyModule();
		damageParticles = new DamageParticlesModule();
		optimization = new OptimizationModule();
		customChat = new CustomChatModule();
		pet = new PetModule();
		hitbox = new HitboxModule();
		aspectRatio = new AspectRatioModule();
		emotes = new EmoteModule();
		mouseFix = new MouseFixModule();
		hands = new com.flyvisual.module.impl.HandsModule();
		killEffect = new com.flyvisual.module.impl.KillEffectModule();
		jumpEffect = new com.flyvisual.module.impl.JumpEffectModule();
		saturation = new SaturationModule();
		potionHud = new PotionHudModule();
		deathScreen = new DeathScreenModule();

		// Чтобы добавить новую функцию, создай класс и добавь его сюда.
		// Порядок = порядок в меню.
		MODULES.add(new WatermarkModule());
		MODULES.add(new FpsModule());
		MODULES.add(new PingModule());
		MODULES.add(new CoordinatesModule());
		MODULES.add(new KeybindHudModule());
		MODULES.add(new ArmorHudModule());
		MODULES.add(potionHud);
		MODULES.add(new TargetHudModule());
		MODULES.add(fullbright);
		MODULES.add(noHurtCam);
		MODULES.add(lowerFire);
		MODULES.add(zoom);
		MODULES.add(freeLook);
		MODULES.add(handOffset);
		MODULES.add(hitbox);
		MODULES.add(damageParticles);
		MODULES.add(optimization);
		MODULES.add(new HitColorModule());
		MODULES.add(fog);
		MODULES.add(timeChanger);
		MODULES.add(new BlockOutlineModule());
		MODULES.add(totem);
		MODULES.add(crosshair);
		MODULES.add(capes);
		MODULES.add(new AuraModule());
		MODULES.add(new TrailModule());
		MODULES.add(sky);
		MODULES.add(customChat);
		MODULES.add(new DurabilityWarningModule());
		MODULES.add(new ServerIpModule());
		MODULES.add(new ItemHighlightModule());
		MODULES.add(deathScreen);
		MODULES.add(pet);
		MODULES.add(aspectRatio);
		MODULES.add(saturation);
		MODULES.add(new HealthIndicatorModule());
		MODULES.add(new MacroModule());
		MODULES.add(emotes);
		MODULES.add(mouseFix);
		MODULES.add(hands);
		MODULES.add(killEffect);
		MODULES.add(jumpEffect);
		MODULES.add(new TargetEspModule());

		FlyConfig cfg = FlyConfig.get();
		for (Module m : MODULES) {
			boolean on = cfg.modules.getOrDefault(m.getName(), m.enabledByDefault());
			m.loadState(on);

			if (m instanceof HudModule hud) {
				int[] pos = cfg.positions.get(m.getName());
				if (pos != null && pos.length == 2) {
					hud.setPosition(pos[0], pos[1]);
				}
			}
		}
	}

	// Геттеры для миксинов (могут вернуть null, если мод ещё не загрузился)
	public static NoHurtCamModule noHurtCam() {
		return noHurtCam;
	}

	public static LowerFireModule lowerFire() {
		return lowerFire;
	}

	public static ZoomModule zoom() {
		return zoom;
	}

	public static FreeLookModule freeLook() {
		return freeLook;
	}

	public static HandOffsetModule handOffset() {
		return handOffset;
	}

	public static FogModule fog() {
		return fog;
	}

	public static TimeChangerModule timeChanger() {
		return timeChanger;
	}

	public static TotemAnimationModule totem() {
		return totem;
	}

	public static CrosshairModule crosshair() {
		return crosshair;
	}

	public static CapesModule capes() {
		return capes;
	}

	public static OptimizationModule optimization() {
		return optimization;
	}

	public static DamageParticlesModule damageParticles() {
		return damageParticles;
	}

	public static PotionHudModule potionHud() {
		return potionHud;
	}

	public static com.flyvisual.module.impl.HandsModule hands() {
		return hands;
	}

	public static MouseFixModule mouseFix() {
		return mouseFix;
	}

	public static EmoteModule emotes() {
		return emotes;
	}

	public static AspectRatioModule aspectRatio() {
		return aspectRatio;
	}

	public static SaturationModule saturation() {
		return saturation;
	}

	public static HitboxModule hitbox() {
		return hitbox;
	}

	public static PetModule pet() {
		return pet;
	}

	public static DeathScreenModule deathScreen() {
		return deathScreen;
	}

	public static SkyModule sky() {
		return sky;
	}

	public static List<Module> all() {
		return Collections.unmodifiableList(MODULES);
	}

	public static void tick(MinecraftClient client) {
		for (Module m : MODULES) {
			if (m.isEnabled()) {
				m.onTick(client);
			}
		}
	}

	public static void renderHud(DrawContext ctx, float tickDelta) {
		for (Module m : MODULES) {
			if (m.isEnabled() && m instanceof HudModule hud) {
				// Масштаб считаем вокруг левого верхнего угла модуля
				float s = hud.getScale();
				MatrixStack ms = ctx.getMatrices();
				ms.push();
				if (s != 1.0f) {
					ms.translate((float) hud.getX(), (float) hud.getY(), 0.0f);
					ms.scale(s, s, 1.0f);
					ms.translate((float) -hud.getX(), (float) -hud.getY(), 0.0f);
				}
				hud.render(ctx, tickDelta);
				ms.pop();
			} else if (m.isEnabled()) {
				m.renderOverlay(ctx, tickDelta);
			}
		}
	}

	/** Вызывается при закрытии игры: возвращаем яркость, чтобы она не сохранилась в options.txt. */
	public static void shutdown() {
		if (fullbright != null) {
			fullbright.restore();
		}
		if (customChat != null) {
			customChat.restore();
		}
	}
}
