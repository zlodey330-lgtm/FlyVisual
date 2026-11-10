package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.GreenGrassPack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Оптимизация (этап 1): убираем с экрана то, на что тратится FPS. Каждая вещь включается отдельно
 * в панели настроек. Всё работает только на твоём клиенте: сервер и другие игроки ничего не замечают.
 * Сами вставки в игру лежат в миксинах (небо, частицы, блеск, анимации, предметы, таблички, моб за стеной).
 */
public class OptimizationModule extends Module {
	/** Наши частицы удара (Damage Particles) рисуются, даже если выключены «остальные частицы». */
	public static boolean ownSpawn;

	private final DoubleSetting level = addChoice("Quality level", 0,
			"1 — обычный", "2 — меньше пикселей", "3 — совсем мало");
	private final DoubleSetting allOn = addToggle("All on", false);
	private final DoubleSetting allOff = addToggle("All off", false);

	private final DoubleSetting hideSky = addToggle("Hide sky", true);
	private final DoubleSetting hideSkyEnd = addToggle("Hide sky in End", true);
	private final DoubleSetting hideClouds = addToggle("Hide clouds", true);
	private final DoubleSetting greenGrass = addToggle("Green grass", true);
	private final DoubleSetting noCrit = addToggle("No crit particles", true);
	private final DoubleSetting noHit = addToggle("No hit particles", true);
	private final DoubleSetting noOther = addToggle("No other particles", true);
	private final DoubleSetting noGlint = addToggle("No enchant glint", true);
	private final DoubleSetting freezeAnim = addToggle("Freeze animations", true);
	private final DoubleSetting stillItems = addToggle("Still items", true);
	private final DoubleSetting signLimit = addToggle("Limit sign text", true);
	private final DoubleSetting signDistance = addSetting("Sign distance", 3.0, 1.0, 16.0, 1.0);
	private final DoubleSetting wallCull = addToggle("Hide mobs behind walls", true);
	private final DoubleSetting frameLimit = addToggle("Limit far frames", true);
	private final DoubleSetting frameDistance = addSetting("Frames distance", 24.0, 8.0, 64.0, 4.0);
	private final DoubleSetting noShadows = addToggle("No entity shadows", true);
	private final DoubleSetting noFire = addToggle("No entity fire", true);
	private final DoubleSetting noWeather = addToggle("No weather", true);
	private final DoubleSetting noBob = addToggle("No view bob", true);
	private final DoubleSetting noCracks = addToggle("No block cracks", true);

	private final DoubleSetting[] features = {hideSky, hideClouds, greenGrass, noCrit, noHit, noOther, noGlint,
			freezeAnim, stillItems, signLimit, wallCull, frameLimit, noShadows, hideSkyEnd, noFire, noWeather, noBob, noCracks};

	private boolean bobSaved;
	private boolean prevBob = true;
	private boolean shadowsSaved;
	private boolean prevShadows = true;
	private boolean grassInit;
	private boolean grassApplied;
	private int pixelApplied;
	private long lastReloadMs;

	/** Результаты проверки «за стеной»: id сущности -> {время проверки мс, 1 = скрыта}. */
	private final Map<Integer, long[]> wallCache = new HashMap<>();
	private long lastCleanMs;

	public OptimizationModule() {
		super("Optimization", "Turns off effects that cost FPS");
	}

	// ---------- Запросы из миксинов ----------

	/** Небо прячется отдельно в обычном мире и в Энде (в Незере неба нет). */
	public boolean skySky() {
		if (!isEnabled()) return false;
		ClientWorld w = mc().world;
		boolean end = w != null && w.getRegistryKey() == World.END;
		return end ? hideSkyEnd.getBool() : hideSky.getBool();
	}

	public boolean hidesEntityFire() {
		return isEnabled() && noFire.getBool();
	}

	public boolean hidesWeather() {
		return isEnabled() && noWeather.getBool();
	}

	public boolean hidesBlockCracks() {
		return isEnabled() && noCracks.getBool();
	}

	public boolean skyClouds() {
		return isEnabled() && hideClouds.getBool();
	}

	public boolean blocksGlint() {
		return isEnabled() && noGlint.getBool();
	}

	public boolean freezesAnimations() {
		return isEnabled() && freezeAnim.getBool();
	}

	public boolean keepsItemsStill() {
		return isEnabled() && stillItems.getBool();
	}

	public boolean blocksCrit() {
		return isEnabled() && noCrit.getBool();
	}

	/** Сколько пикселей на сторону блока оставить: 0 = не трогать, 8 = уровень 2, 4 = уровень 3. */
	public int pixelTarget() {
		if (!isEnabled()) return 0;
		int l = level.getInt();
		return l == 1 ? 8 : l >= 2 ? 4 : 0;
	}

	/** Индекс уровня: 0 — обычный, 1 — уровень 2, 2 — уровень 3 (0, если модуль выключен). */
	public int levelIndex() {
		return isEnabled() ? level.getInt() : 0;
	}

	/** Сила размытия мобов и предметов: уровень 2 чуть-чуть, уровень 3 заметно, но узнаваемо. */
	public double softenMix() {
		int l = levelIndex();
		return l == 1 ? 0.3 : l >= 2 ? 0.85 : 0.0;
	}

	/** Не показывать частицы удара: ванильный индикатор урона и наши Damage Particles. */
	public boolean blocksHitParticles() {
		return isEnabled() && noHit.getBool();
	}

	public boolean blocksOtherParticles() {
		return isEnabled() && noOther.getBool();
	}

	public boolean blocksBlockParticles() {
		return blocksOtherParticles();
	}

	/** true, если эту частицу показывать не надо. */
	public boolean blocksParticle(ParticleEffect effect) {
		if (!isEnabled()) return false;
		Object type = effect.getType();
		if (type == ParticleTypes.CRIT || type == ParticleTypes.ENCHANTED_HIT) {
			return noCrit.getBool();
		}
		if (type == ParticleTypes.DAMAGE_INDICATOR) {
			return noHit.getBool();
		}
		return noOther.getBool() && !ownSpawn;
	}

	/** Текст таблички рисуется только вблизи. */
	public boolean signTooFar(Vec3d signCenter, Vec3d camera) {
		if (!isEnabled() || !signLimit.getBool()) return false;
		double d = signDistance.get();
		return signCenter.squaredDistanceTo(camera) > d * d;
	}

	/** true, если сущность не надо рисовать: далёкая рамка или стойка, либо моб за стеной. */
	public boolean shouldCull(Entity entity) {
		if (!isEnabled()) return false;
		MinecraftClient mc = mc();
		ClientWorld world = mc.world;
		if (world == null || mc.player == null || entity == mc.player) return false;
		Vec3d cam = mc.gameRenderer.getCamera().getPos();

		if (frameLimit.getBool() && (entity instanceof AbstractDecorationEntity || entity instanceof ArmorStandEntity)) {
			double d = frameDistance.get();
			if (entity.squaredDistanceTo(cam) > d * d) return true;
		}

		if (!wallCull.getBool() || !(entity instanceof LivingEntity) || entity.isGlowing()) return false;
		if (entity.squaredDistanceTo(cam) < 16.0) return false;

		long now = System.currentTimeMillis();
		if (now - lastCleanMs > 5000) {
			lastCleanMs = now;
			wallCache.values().removeIf(v -> now - v[0] > 5000);
		}
		long[] cached = wallCache.get(entity.getId());
		if (cached != null && now - cached[0] < 200) {
			return cached[1] == 1;
		}
		boolean hidden = behindWall(world, cam, entity, mc.player);
		wallCache.put(entity.getId(), new long[]{now, hidden ? 1 : 0});
		return hidden;
	}

	/** Моб скрыт, только если ни одна из пяти его точек не видна из камеры. */
	private boolean behindWall(ClientWorld world, Vec3d cam, Entity e, Entity viewer) {
		double h = e.getHeight();
		double w = Math.max(0.3, e.getWidth() * 0.45);
		Vec3d pos = e.getPos();
		// Боковой вектор перпендикулярно взгляду из камеры на моба
		double dx = pos.x - cam.x;
		double dz = pos.z - cam.z;
		double len = Math.max(1.0e-4, Math.hypot(dx, dz));
		double sx = -dz / len * w;
		double sz = dx / len * w;
		Vec3d[] points = {
				new Vec3d(pos.x, pos.y + h * 0.5, pos.z),
				new Vec3d(pos.x, pos.y + h * 0.92, pos.z),
				new Vec3d(pos.x, pos.y + 0.12, pos.z),
				new Vec3d(pos.x + sx, pos.y + h * 0.5, pos.z + sz),
				new Vec3d(pos.x - sx, pos.y + h * 0.5, pos.z - sz)
		};
		for (Vec3d p : points) {
			RaycastContext ctx = new RaycastContext(cam, p, RaycastContext.ShapeType.VISUAL,
					RaycastContext.FluidHandling.NONE, viewer);
			if (world.raycast(ctx).getType() == HitResult.Type.MISS) {
				return false;
			}
		}
		return true;
	}

	// ---------- Жизненный цикл ----------

	@Override
	public void onTick(MinecraftClient client) {
		if (allOn.getBool()) {
			for (DoubleSetting s : features) s.set(1.0);
			allOn.set(0.0);
		}
		if (allOff.getBool()) {
			for (DoubleSetting s : features) s.set(0.0);
			allOff.set(0.0);
		}

		// Тени под мобами: временно выключаем обычную настройку игры, потом возвращаем как было
		SimpleOption<Boolean> shadows = client.options.getEntityShadows();
		if (noShadows.getBool()) {
			if (!shadowsSaved) {
				prevShadows = shadows.getValue();
				shadowsSaved = true;
			}
			if (shadows.getValue()) shadows.setValue(false);
		} else {
			restoreShadows();
		}

		// Покачивание камеры и руки при ходьбе (обычная настройка игры, потом возвращаем)
		SimpleOption<Boolean> bob = client.options.getBobView();
		if (noBob.getBool()) {
			if (!bobSaved) {
				prevBob = bob.getValue();
				bobSaved = true;
			}
			if (bob.getValue()) bob.setValue(false);
		} else {
			restoreBob();
		}

		// Зелёный блок травы: встроенный ресурс-пак, включение требует перезагрузки ресурсов
		if (!grassInit) {
			grassInit = true;
			grassApplied = GreenGrassPack.isEnabled();
			pixelApplied = pixelTarget(); // при запуске текстуры уже загружены с этим уровнем
		}
		boolean want = greenGrass.getBool();
		int wantPixels = pixelTarget();
		long now = System.currentTimeMillis();
		if ((want != grassApplied || wantPixels != pixelApplied) && now - lastReloadMs > 3000) {
			lastReloadMs = now;
			pixelApplied = wantPixels;
			if (want != grassApplied) {
				grassApplied = want;
				GreenGrassPack.set(want); // сам перезагружает ресурсы, уровень текстур подхватится
			} else {
				GreenGrassPack.reload();
			}
		}
	}

	@Override
	public void onDisable() {
		restoreShadows();
		restoreBob();
		wallCache.clear();
		boolean hadPixels = pixelApplied != 0;
		pixelApplied = 0;
		if (GreenGrassPack.isEnabled()) {
			grassApplied = false;
			GreenGrassPack.set(false);
		} else if (hadPixels) {
			GreenGrassPack.reload();
		}
	}

	private void restoreBob() {
		if (!bobSaved) return;
		bobSaved = false;
		MinecraftClient client = mc();
		if (client != null && client.options != null) {
			client.options.getBobView().setValue(prevBob);
		}
	}

	private void restoreShadows() {
		if (!shadowsSaved) return;
		shadowsSaved = false;
		MinecraftClient client = mc();
		if (client != null && client.options != null) {
			client.options.getEntityShadows().setValue(prevShadows);
		}
	}
}
