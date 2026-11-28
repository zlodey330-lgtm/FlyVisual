package com.flyvisual.module.impl;

import com.flyvisual.hud.Notifications;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.JsonEffectShaderProgram;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Сезоны и погода (только на твоём клиенте).
 * <p>
 * Работает в двух слоях, которые не зависят друг от друга:
 * 1) собственные эффекты: частицы (снег, листья, лепестки, дождь, блики), вихри ветра, снежный покров, лужи,
 * брызги, звуки, туман, цвет травы и листвы. Работают без шейдеров;
 * 2) шейдер: цветокоррекция сезона поверх картинки (можно выключить галочкой «Шейдер»).
 * Сезоны переключаются плавно: у каждого сезона есть «вес», который постепенно меняется.
 */
public final class WeatherModule extends Module {
	private static final Logger LOGGER = LoggerFactory.getLogger("FlyVisual/Weather");

	private static final String[] MODES = {"Весна", "Лето", "Осень", "Зима", "Дождь"};
	private static final String[] QUALITY = {"Низкое", "Среднее", "Высокое", "Ультра"};
	private static final String[] AUTUMN_PAL = {"Смешанная", "Жёлтая", "Оранжевая", "Красная"};
	private static final String[] SPRING_PAL = {"Сакура", "Белые", "Смешанные"};
	private static final String[] RAIN_TYPE = {"Лёгкий дождь", "Обычный дождь", "Ливень"};

	private static final float[] QRANGE = {0.6f, 0.8f, 1.0f, 1.1f};
	private static final float[] QGROUND = {0.6f, 0.8f, 1.0f, 1.2f};
	private static final float[] QDENS = {0.35f, 0.65f, 1.0f, 1.5f};
	private static final int[] QCAP = {800, 1800, 3500, 6500};
	private static final float[] RAIN_DENS = {0.35f, 0.7f, 1.2f};
	private static final float[] RAIN_GRAD = {0.5f, 0.8f, 1.0f};
	private static final float[] PUDDLE_MUL = {0.55f, 0.85f, 1.0f};

	// Шейдер по сезонам: оттенок, сила, насыщенность, виньетка (весна, лето, осень, зима, дождь)
	private static final float[][] TINT = {
			{0.98f, 1.06f, 0.98f}, {1.08f, 1.04f, 0.92f}, {1.10f, 0.96f, 0.82f},
			{0.82f, 0.93f, 1.10f}, {0.80f, 0.86f, 0.94f}
	};
	private static final float[] STR = {0.60f, 0.60f, 0.70f, 0.75f, 0.80f};
	private static final float[] SAT = {1.12f, 1.18f, 1.08f, 0.82f, 0.80f};
	private static final float[] VIG = {0.05f, 0.05f, 0.18f, 0.12f, 0.22f};

	// ---- общие настройки (первые две оставлены как были, чтобы сохранённый конфиг не потерялся) ----
	private final DoubleSetting mode = addChoice("Season / Weather", 1, MODES);
	private final DoubleSetting shader = addToggle("Shader", true);
	private final DoubleSetting shaderStrength = addSetting("Shader strength", 1.0, 0.0, 1.5, 0.05);
	private final DoubleSetting quality = addChoice("Weather quality", 2, QUALITY);
	private final DoubleSetting ownEffects = addToggle("Own effects", true);
	private final DoubleSetting effectRange = addSetting("Effect range", 32.0, 16.0, 64.0, 4.0);
	private final DoubleSetting effectDensity = addSetting("Effect density", 1.0, 0.2, 2.0, 0.1);
	private final DoubleSetting changeTime = addSetting("Season change time", 20.0, 2.0, 300.0, 1.0);
	private final DoubleSetting autoSeasons = addToggle("Auto seasons", false);
	private final DoubleSetting autoMinutes = addSetting("Auto season minutes", 5.0, 1.0, 60.0, 1.0);
	private final DoubleSetting windPower = addSetting("Wind power", 1.0, 0.0, 3.0, 0.1);
	private final DoubleSetting windAngle = addSetting("Wind angle", 45.0, 0.0, 355.0, 5.0);
	private final DoubleSetting windGusts = addToggle("Wind gusts", true);
	private final DoubleSetting soundVolume = addSetting("Weather sound volume", 1.0, 0.0, 1.5, 0.05);
	private final DoubleSetting blockColors = addToggle("Block colors", true);
	private final DoubleSetting colorInterval = addSetting("Block color interval", 6.0, 3.0, 30.0, 1.0);
	private final DoubleSetting weatherFog = addToggle("Weather fog", true);
	private final DoubleSetting fogDensity = addSetting("Weather fog density", 1.0, 0.3, 2.0, 0.1);
	private final DoubleSetting groundFx = addToggle("Ground effects", true);
	private final DoubleSetting groundRange = addSetting("Ground range", 20.0, 8.0, 32.0, 2.0);

	// ---- зима ----
	private final DoubleSetting snowAmount = addSetting("Snow amount", 1.0, 0.0, 2.0, 0.1);
	private final DoubleSetting snowSize = addSetting("Snow flake size", 1.0, 0.5, 2.0, 0.1);
	private final DoubleSetting snowCover = addSetting("Snow cover", 0.85, 0.1, 1.0, 0.05);
	private final DoubleSetting freezeTime = addSetting("Freeze time", 60.0, 5.0, 600.0, 5.0);
	private final DoubleSetting blizzardOn = addToggle("Blizzard", false);
	private final DoubleSetting blizzardDensity = addSetting("Blizzard density", 2.5, 1.0, 4.0, 0.5);

	// ---- осень ----
	private final DoubleSetting leafAmount = addSetting("Leaf amount", 1.0, 0.0, 2.0, 0.1);
	private final DoubleSetting leafSpeed = addSetting("Leaf fall speed", 1.0, 0.3, 2.0, 0.1);
	private final DoubleSetting leafSpin = addSetting("Leaf spin", 1.0, 0.0, 2.0, 0.1);
	private final DoubleSetting leafPalette = addChoice("Leaf palette", 0, AUTUMN_PAL);
	private final DoubleSetting autumnStrength = addSetting("Autumn color strength", 0.85, 0.0, 1.0, 0.05);
	private final DoubleSetting leafVortices = addToggle("Leaf vortices", true);

	// ---- весна ----
	private final DoubleSetting petalAmount = addSetting("Petal amount", 1.0, 0.0, 2.0, 0.1);
	private final DoubleSetting petalPalette = addChoice("Petal palette", 0, SPRING_PAL);
	private final DoubleSetting bloomStrength = addSetting("Bloom strength", 0.7, 0.0, 1.0, 0.05);

	// ---- лето ----
	private final DoubleSetting sunMotes = addSetting("Sun motes", 1.0, 0.0, 2.0, 0.1);
	private final DoubleSetting lushStrength = addSetting("Lush green strength", 0.6, 0.0, 1.0, 0.05);

	// ---- дождь ----
	private final DoubleSetting rainType = addChoice("Rain type", 1, RAIN_TYPE);
	private final DoubleSetting rainAmount = addSetting("Rain amount", 1.0, 0.3, 2.0, 0.1);
	private final DoubleSetting rainSplashes = addToggle("Rain splashes", true);
	private final DoubleSetting puddles = addToggle("Puddles", true);
	private final DoubleSetting puddleSize = addSetting("Puddle size", 1.0, 0.5, 2.0, 0.1);
	private final DoubleSetting puddleTime = addSetting("Puddle time", 25.0, 5.0, 120.0, 5.0);
	private final DoubleSetting stepSplashes = addToggle("Step splashes", true);
	private final DoubleSetting rainSlant = addSetting("Rain slant", 1.0, 0.0, 2.0, 0.1);

	// ---- состояние ----
	private final float[] w = new float[5];
	private float frost;
	private float groundSnow;
	private float wetness;
	private float blizzard;
	private float gustBoost = 1.0f;
	private int gustTimer = 300;
	private boolean pendingVortex;
	private float windX;
	private float windZ;
	private long ticks;
	private int autoTicks;
	private int autoSeasonFallback;
	private final Random rnd = new Random();

	private final WeatherFx fx = new WeatherFx();
	private final WeatherFx.Cfg cfg = new WeatherFx.Cfg();
	private boolean fxFailed;
	private boolean fxWasOn;

	// Цвет травы и листвы: «снимок» значений, по которому построена текущая картинка мира
	private final float[] snapW = new float[5];
	private volatile float snapFrost;
	private volatile boolean colorsActive;
	private int colorKey;
	private long lastReload = -1000L;

	// Шейдер
	private PostEffectProcessor processor;
	private int width;
	private int height;
	private boolean failed;
	private Field passesField;
	private boolean passesLookedUp;

	public WeatherModule() {
		super("Weather", "Seasons and weather: snow, leaves, petals, rain, puddles, wind and smooth season changes");
		WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
			if (!isEnabled() || fxFailed || !ownEffects.getBool()) return;
			try {
				fx.render(context, cfg);
			} catch (Throwable error) {
				failFx(error);
			}
		});
	}

	// ---------- публичные методы для миксинов ----------

	/** Сила дождя для ванильного неба (затемнение), плавно растёт и падает. */
	public float rainGradient() {
		if (!isEnabled()) return 0.0f;
		return Math.min(1.0f, w[4] * RAIN_GRAD[rainType.getInt()]);
	}

	public boolean isRainMode() {
		return rainGradient() > 0.01f;
	}

	/** Свои капли заменяют ванильный дождь, ванильные капли и их звук не нужны. */
	public boolean replacesVanillaRain() {
		return isEnabled() && ownEffects.getBool() && !fxFailed && w[4] > 0.02f;
	}

	public boolean colorsActive() {
		return colorsActive;
	}

	/** Туман погоды: вызывается из BackgroundRendererMixin после ванильной настройки тумана. */
	public void applyFog(float viewDistance) {
		if (!isEnabled() || !weatherFog.getBool()) return;
		float m = 1.0f - (0.04f * w[0] + 0.10f * w[2] + 0.15f * w[3] + 0.30f * w[4]);
		m -= 0.35f * blizzard;
		float d = fogDensity.getFloat();
		m = 1.0f - (1.0f - m) * d;
		m = Math.max(0.3f, Math.min(1.2f, m));
		if (Math.abs(m - 1.0f) < 0.01f) return;
		RenderSystem.setShaderFogStart(RenderSystem.getShaderFogStart() * m);
		RenderSystem.setShaderFogEnd(RenderSystem.getShaderFogEnd() * m);
	}

	/** Цвет травы с учётом сезона. Вызывается при построении чанков. */
	public int tintGrass(int base, BlockPos pos) {
		float h = WeatherFx.hash(pos.getX() >> 1, pos.getZ() >> 1);
		float lush = lushStrength.getFloat();
		float aut = autumnStrength.getFloat();
		int c = base;
		c = mixRgb(c, 0x86D443, snapW[0] * 0.30f);
		c = mixRgb(c, 0x4FB02A, snapW[1] * 0.45f * lush);
		c = mixRgb(c, h < 0.5f ? 0xB8A038 : 0xA88C2E, snapW[2] * 0.70f * aut);
		c = mixRgb(c, 0x3E7D3A, snapW[4] * 0.25f);
		c = mixRgb(c, 0xE0EBF0, snapFrost * 0.85f);
		return c;
	}

	/** Цвет листвы с учётом сезона: осенью у каждого кусочка кроны свой оттенок. */
	public int tintFoliage(int base, BlockPos pos) {
		float h = WeatherFx.hash3(pos.getX() >> 1, pos.getY() >> 1, pos.getZ() >> 1);
		int[] ap = WeatherFx.AUTUMN_PALETTES[Math.max(0, Math.min(WeatherFx.AUTUMN_PALETTES.length - 1, leafPalette.getInt()))];
		int[] sp = WeatherFx.SPRING_PALETTES[Math.max(0, Math.min(WeatherFx.SPRING_PALETTES.length - 1, petalPalette.getInt()))];
		float lush = lushStrength.getFloat();
		int c = base;
		c = mixRgb(c, sp[(int) (h * sp.length) % sp.length], snapW[0] * bloomStrength.getFloat() * 0.55f);
		c = mixRgb(c, 0x2F8F1F, snapW[1] * 0.45f * lush);
		c = mixRgb(c, ap[(int) (h * ap.length) % ap.length], snapW[2] * autumnStrength.getFloat() * 0.90f);
		c = mixRgb(c, 0x35662F, snapW[4] * 0.25f);
		c = mixRgb(c, 0xD0E2E8, snapFrost * 0.70f);
		return c;
	}

	private static int mixRgb(int a, int b, float t) {
		if (t <= 0.001f) return a;
		if (t > 1.0f) t = 1.0f;
		int ar = (a >> 16) & 255;
		int ag = (a >> 8) & 255;
		int ab = a & 255;
		int br = (b >> 16) & 255;
		int bg = (b >> 8) & 255;
		int bb = b & 255;
		int r = (int) (ar + (br - ar) * t);
		int g = (int) (ag + (bg - ag) * t);
		int bl = (int) (ab + (bb - ab) * t);
		return (r << 16) | (g << 8) | bl;
	}

	// ---------- включение / выключение ----------

	@Override
	public void onEnable() {
		failed = false;
		fxFailed = false;
	}

	@Override
	public void onDisable() {
		Arrays.fill(w, 0.0f);
		frost = 0.0f;
		groundSnow = 0.0f;
		wetness = 0.0f;
		blizzard = 0.0f;
		fx.clear();
		fxWasOn = false;
		release();
		if (colorsActive || colorKey != 0) {
			colorsActive = false;
			colorKey = 0;
			Arrays.fill(snapW, 0.0f);
			snapFrost = 0.0f;
			reloadWorld(mc());
		}
	}

	private void failFx(Throwable error) {
		fxFailed = true;
		fx.clear();
		LOGGER.error("Weather effects failed", error);
		Notifications.push("Weather: эффекты выключены из-за ошибки (" + error.getClass().getSimpleName() + ")", false);
	}

	private static void reloadWorld(MinecraftClient mc) {
		try {
			if (mc != null && mc.world != null && mc.worldRenderer != null) {
				mc.worldRenderer.reload();
			}
		} catch (Throwable error) {
			LOGGER.warn("Could not refresh world colors", error);
		}
	}

	// ---------- тик ----------

	@Override
	public void onTick(MinecraftClient client) {
		ticks++;
		if (client.world == null || client.player == null) {
			if (fxWasOn) {
				fx.clear();
				fxWasOn = false;
			}
			return;
		}
		try {
			updateSeason();
			updateGround();
			updateWind();
			updateColors(client);
		} catch (Throwable error) {
			LOGGER.error("Weather update failed", error);
		}

		if (ownEffects.getBool() && !fxFailed) {
			try {
				fillCfg();
				fx.tick(client, cfg);
				if (pendingVortex) {
					pendingVortex = false;
					fx.startVortex(client, cfg);
				}
				fxWasOn = true;
			} catch (Throwable error) {
				failFx(error);
			}
		} else if (fxWasOn) {
			fx.clear();
			fxWasOn = false;
		}
	}

	private void updateSeason() {
		if (autoSeasons.getBool()) {
			autoTicks++;
			if (autoTicks >= autoMinutes.getInt() * 1200) {
				autoTicks = 0;
				int cur = mode.getInt() < 4 ? mode.getInt() : autoSeasonFallback;
				int next = (cur + 1) & 3;
				autoSeasonFallback = next;
				mode.set(next);
			}
		} else {
			autoTicks = 0;
		}
		int target = Math.max(0, Math.min(4, mode.getInt()));
		float k = 3.0f / Math.max(40.0f, changeTime.getFloat() * 20.0f);
		for (int i = 0; i < 5; i++) {
			float t = i == target ? 1.0f : 0.0f;
			w[i] += (t - w[i]) * k;
			if (Math.abs(t - w[i]) < 0.0005f) w[i] = t;
		}
	}

	private static float approach(float cur, float target, float up, float down) {
		if (target > cur) return Math.min(target, cur + up);
		return Math.max(target, cur - down);
	}

	private void updateGround() {
		int target = mode.getInt();
		float freezeStep = 1.0f / Math.max(20.0f, freezeTime.getFloat() * 20.0f);
		float snowTarget = target == 3 ? snowCover.getFloat() : 0.0f;
		groundSnow = approach(groundSnow, snowTarget, freezeStep, freezeStep * 1.5f);
		frost = approach(frost, target == 3 ? 1.0f : 0.0f, freezeStep, freezeStep * 1.5f);

		float formStep = 1.0f / Math.max(20.0f, puddleTime.getFloat() * 20.0f);
		float wetTarget = (target == 4 && w[4] > 0.1f) ? PUDDLE_MUL[rainType.getInt()] : 0.0f;
		wetness = approach(wetness, wetTarget, formStep, formStep / 3.0f);

		float blizzTarget = (target == 3 && blizzardOn.getBool()) ? 1.0f : 0.0f;
		blizzard = approach(blizzard, blizzTarget, 0.01f, 0.01f);
	}

	private void updateWind() {
		float dir = (float) Math.toRadians(windAngle.get());
		float base = windPower.getFloat() * 0.035f;
		float g = 1.0f;
		if (windGusts.getBool()) {
			g = 1.0f + 0.35f * (float) Math.sin(ticks * 0.031) + 0.25f * (float) Math.sin(ticks * 0.073 + 1.3)
					+ 0.10f * (float) Math.sin(ticks * 0.19);
			g = Math.max(0.2f, g);
			gustTimer--;
			if (gustTimer <= 0) {
				gustBoost = 1.5f + rnd.nextFloat() * 0.9f;
				gustTimer = 500 + rnd.nextInt(900);
				if (leafVortices.getBool() && (w[2] > 0.3f || w[0] > 0.3f)) pendingVortex = true;
			}
			gustBoost += (1.0f - gustBoost) * 0.015f;
		} else {
			gustBoost = 1.0f;
		}
		float sum = w[0] + w[1] + w[2] + w[3] + w[4];
		float seasonMul = w[0] * 0.6f + w[1] * 0.5f + w[2] * 1.2f + w[3] * (1.1f + 1.5f * blizzard)
				+ w[4] * 1.3f + Math.max(0.0f, 1.0f - sum);
		float speed = base * g * gustBoost * seasonMul;
		windX = (float) Math.cos(dir) * speed;
		windZ = (float) Math.sin(dir) * speed;
	}

	private int computeKey() {
		int k = 17;
		int zero = 0;
		for (int i = 0; i < 5; i++) {
			int q = Math.round(w[i] * 6.0f);
			zero |= q;
			k = k * 31 + q;
		}
		int fq = Math.round(frost * 6.0f);
		zero |= fq;
		if (zero == 0) return 0;
		k = k * 31 + fq;
		k = k * 31 + leafPalette.getInt() * 7 + petalPalette.getInt() * 13;
		k = k * 31 + (int) (autumnStrength.get() * 20.0) + (int) (bloomStrength.get() * 20.0) * 3
				+ (int) (lushStrength.get() * 20.0) * 5;
		return k == 0 ? 1 : k;
	}

	private void updateColors(MinecraftClient mc) {
		int key = blockColors.getBool() ? computeKey() : 0;
		if (key == colorKey) return;
		if (key != 0 && ticks - lastReload < colorInterval.getInt() * 20L) return;
		System.arraycopy(w, 0, snapW, 0, 5);
		snapFrost = frost;
		colorsActive = key != 0;
		colorKey = key;
		lastReload = ticks;
		reloadWorld(mc);
	}

	private void fillCfg() {
		int qi = Math.max(0, Math.min(3, quality.getInt()));
		int rt = Math.max(0, Math.min(2, rainType.getInt()));
		cfg.range = effectRange.getFloat() * QRANGE[qi];
		cfg.density = QDENS[qi] * effectDensity.getFloat();
		cfg.cap = QCAP[qi];
		cfg.windX = windX;
		cfg.windZ = windZ;
		cfg.snow = w[3] * snowAmount.getFloat() * (1.0f + blizzard * (blizzardDensity.getFloat() - 1.0f));
		cfg.leaves = w[2] * leafAmount.getFloat();
		cfg.petals = w[0] * petalAmount.getFloat();
		cfg.motes = w[1] * sunMotes.getFloat();
		cfg.rain = w[4] * RAIN_DENS[rt] * rainAmount.getFloat();
		cfg.snowSize = snowSize.getFloat();
		cfg.leafSpeed = leafSpeed.getFloat();
		cfg.leafSpin = leafSpin.getFloat();
		cfg.rainSlant = rainSlant.getFloat();
		cfg.soundVol = soundVolume.getFloat();
		cfg.puddleSize = puddleSize.getFloat();
		cfg.groundSnow = groundSnow;
		cfg.wetness = wetness;
		cfg.groundRange = groundRange.getFloat() * QGROUND[qi];
		cfg.blizzard = blizzard;
		cfg.leafPalette = leafPalette.getInt();
		cfg.petalPalette = petalPalette.getInt();
		cfg.splashes = rainSplashes.getBool();
		cfg.puddles = puddles.getBool();
		cfg.stepSplash = stepSplashes.getBool();
		cfg.ground = groundFx.getBool();
	}

	// ---------- шейдер ----------

	private void release() {
		if (processor != null) {
			try {
				processor.close();
			} catch (Exception error) {
				LOGGER.warn("Could not close Weather post processor", error);
			}
			processor = null;
		}
		passesField = null;
		passesLookedUp = false;
	}

	/** Итоговые параметры шейдера: смесь сезонов по их весам. [0..2] оттенок, [3] сила, [4] насыщенность, [5] виньетка. */
	private float[] shaderMix() {
		float sum = w[0] + w[1] + w[2] + w[3] + w[4];
		float n = Math.max(0.0f, 1.0f - sum);
		float tr = n;
		float tg = n;
		float tb = n;
		float st = 0.0f;
		float sa = n;
		float vg = 0.0f;
		for (int i = 0; i < 5; i++) {
			tr += w[i] * TINT[i][0];
			tg += w[i] * TINT[i][1];
			tb += w[i] * TINT[i][2];
			st += w[i] * STR[i];
			sa += w[i] * SAT[i];
			vg += w[i] * VIG[i];
		}
		float s = shaderStrength.getFloat();
		return new float[]{tr, tg, tb, Math.min(1.0f, st * s), 1.0f + (sa - 1.0f) * s, vg * s};
	}

	@SuppressWarnings("unchecked")
	private List<PostEffectPass> passes() {
		if (processor == null) return null;
		try {
			if (passesField == null && !passesLookedUp) {
				passesLookedUp = true;
				for (Field f : PostEffectProcessor.class.getDeclaredFields()) {
					if (!List.class.isAssignableFrom(f.getType())) continue;
					f.setAccessible(true);
					Object o = f.get(processor);
					if (o instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof PostEffectPass) {
						passesField = f;
						break;
					}
				}
			}
			if (passesField == null) return null;
			return (List<PostEffectPass>) passesField.get(processor);
		} catch (Throwable error) {
			return null;
		}
	}

	private static void setUniform(JsonEffectShaderProgram program, String name, float a) {
		GlUniform u = program.getUniformByName(name);
		if (u != null) u.set(a);
	}

	private void applyUniforms(float[] mix) {
		List<PostEffectPass> list = passes();
		if (list == null) return;
		for (PostEffectPass pass : list) {
			JsonEffectShaderProgram program = pass.getProgram();
			GlUniform tint = program.getUniformByName("Tint");
			if (tint != null) tint.set(mix[0], mix[1], mix[2]);
			setUniform(program, "Strength", mix[3]);
			setUniform(program, "Sat", mix[4]);
			setUniform(program, "Vignette", mix[5]);
		}
	}

	/** Вызывается из GameRendererMixin после отрисовки мира. */
	public void process(float tickDelta) {
		if (!isEnabled()) {
			release();
			return;
		}
		if (!shader.getBool()) {
			// Выключить шейдер можно всегда; при повторном включении даём чистую попытку
			release();
			failed = false;
			return;
		}
		if (failed) return;

		MinecraftClient mc = mc();
		if (mc.world == null || mc.getFramebuffer() == null) return;

		float[] mix = shaderMix();
		if (mix[3] < 0.005f && Math.abs(mix[4] - 1.0f) < 0.003f && mix[5] < 0.003f) {
			release();
			return;
		}

		try {
			int fw = mc.getWindow().getFramebufferWidth();
			int fh = mc.getWindow().getFramebufferHeight();
			if (processor == null) {
				processor = new PostEffectProcessor(mc.getTextureManager(), mc.getResourceManager(),
						mc.getFramebuffer(), new Identifier("flyvisual", "shaders/post/season_0.json"));
				processor.setupDimensions(fw, fh);
				width = fw;
				height = fh;
			} else if (fw != width || fh != height) {
				processor.setupDimensions(fw, fh);
				width = fw;
				height = fh;
			}
			applyUniforms(mix);

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
			LOGGER.error("Weather shader failed (shaderEnabled={}, framebuffer={}x{})",
					shader.getBool(), mc.getWindow().getFramebufferWidth(),
					mc.getWindow().getFramebufferHeight(), error);
			Throwable root = error;
			while (root.getCause() != null && root.getCause() != root) root = root.getCause();
			String detail = String.valueOf(root.getMessage());
			if (detail.length() > 110) detail = detail.substring(0, 110);
			Notifications.push("Weather: " + root.getClass().getSimpleName() + ": " + detail, false);
		}
	}
}
