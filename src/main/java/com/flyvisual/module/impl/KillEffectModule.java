package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.Draw3d;
import com.flyvisual.util.Fx3d;
import com.flyvisual.util.Fx3d.V;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Эффект убийства: на месте смерти игрока или моба из земли появляется гроб, открывается,
 * из него поднимается свет и души, крышка захлопывается, и гроб уходит под землю.
 * Модель собрана из гладких многоугольников с затенением (не блоки). Виден только тебе.
 */
public class KillEffectModule extends Module {
	private static final String[] EFFECTS = {"Гроб", "Души", "Молния", "Осколки", "Вихрь", "Фейерверк", "Пентаграмма"};
	private static final float[] LIFES = {3.7f, 3.3f, 1.7f, 2.6f, 3.0f, 3.1f, 3.6f};
	private static final String[] STYLES = {"Классика", "Золотой", "Чёрный", "Кровавый", "Ледяной", "Изумруд",
			"Королевский", "Кость", "Свой цвет"};
	private static final String[] ANIMS = {"Из земли", "С неба", "Вихрь"};
	private static final float[][] WOOD = {{0.34f, 0.19f, 0.10f}, {0.82f, 0.63f, 0.16f}, {0.07f, 0.07f, 0.09f},
			{0.40f, 0.05f, 0.07f}, {0.58f, 0.80f, 0.93f}, {0.07f, 0.36f, 0.23f}, {0.22f, 0.08f, 0.32f},
			{0.88f, 0.85f, 0.78f}, {0.2f, 0.2f, 0.25f}};
	private static final float[][] TRIM = {{0.88f, 0.68f, 0.24f}, {1.0f, 0.92f, 0.55f}, {0.80f, 0.82f, 0.92f},
			{0.12f, 0.02f, 0.03f}, {0.96f, 0.99f, 1.0f}, {0.88f, 0.78f, 0.32f}, {0.95f, 0.76f, 0.27f},
			{0.55f, 0.52f, 0.48f}, {1f, 1f, 1f}};
	private static final float[] GLOW_HUE = {48f, 50f, 270f, 0f, 190f, 140f, 285f, 200f, 200f};

		private static final float OPEN_ANGLE = 1.85f;
	private static final float H = 0.42f;
	private static final float HX = 0.38f;
	private static final int N = 26;
	private static final float[] OX = new float[N];
	private static final float[] OZ = new float[N];

	static {
		int half = N / 2;
		for (int i = 0; i < half; i++) {
			float u = i / (float) (half - 1);
			float z = -0.95f + 1.9f * u;
			float w;
			if (z < 0.38f) {
				float k = (z + 0.95f) / 1.33f;
				w = 0.19f + (0.42f - 0.19f) * (k * k * (3 - 2 * k));
			} else {
				float k = (z - 0.38f) / 0.57f;
				w = 0.42f + (0.27f - 0.42f) * (k * k * (3 - 2 * k));
			}
			OX[i] = w;
			OZ[i] = z;
			OX[N - 1 - i] = -w;
			OZ[N - 1 - i] = z;
		}
	}

	private interface Tf {
		V at(float x, float y, float z);
	}

	private static final class Fx {
		double x;
		double y;
		double z;
		long start;
		float yaw;
		int style;
		int anim;
		float hue;
		int effect;
		int seed;
		float life = 3.7f;
	}

	private static final class Tracked {
		LivingEntity entity;
		long lastHit;
		double x;
		double y;
		double z;
	}

	private final DoubleSetting effect = addChoice("Effect", 0, EFFECTS);
	private final DoubleSetting style = addChoice("Color", 0, STYLES);
	private final DoubleSetting hue = addSetting("Color (hue)", 200.0, 0.0, 355.0, 5.0);
	private final DoubleSetting anim = addChoice("Animation", 0, ANIMS);
	private final DoubleSetting size = addSetting("Size", 1.0, 0.5, 2.0, 0.1);
	private final DoubleSetting playersOnly = addToggle("Players only", false);

	private final Map<Integer, Tracked> tracked = new HashMap<>();
	private final List<Fx> list = new ArrayList<>();
	private final Draw3d solid = new Draw3d(false);
	private final Draw3d glow = new Draw3d(true);
	private long tick;

	private Matrix4f cm;
	private float[] cur;
	private float curHue;

	public KillEffectModule() {
		super("Kill Effect", "Coffin effect where you kill a player or mob");
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient() && isEnabled() && player == MinecraftClient.getInstance().player
					&& entity instanceof LivingEntity le && le != player && !(le instanceof ArmorStandEntity)
					&& (!playersOnly.getBool() || le instanceof PlayerEntity)) {
				Tracked t = tracked.computeIfAbsent(le.getId(), k -> new Tracked());
				t.entity = le;
				t.lastHit = tick;
				t.x = le.getX();
				t.y = le.getY();
				t.z = le.getZ();
			}
			return ActionResult.PASS;
		});
		WorldRenderEvents.AFTER_ENTITIES.register(this::render);
	}

	@Override
	public void onTick(MinecraftClient client) {
		tick++;
		if (client.player == null || client.world == null) {
			tracked.clear();
			list.clear();
			return;
		}
		Iterator<Tracked> it = tracked.values().iterator();
		while (it.hasNext()) {
			Tracked t = it.next();
			LivingEntity e = t.entity;
			boolean dead = e.isDead() || e.getHealth() <= 0.0f;
			if (dead) {
				if (isEnabled()) spawn(client, t);
				it.remove();
			} else if (e.isRemoved() || tick - t.lastHit > 200) {
				it.remove();
			} else {
				t.x = e.getX();
				t.y = e.getY();
				t.z = e.getZ();
			}
		}
	}

	private void spawn(MinecraftClient client, Tracked t) {
		Fx f = new Fx();
		f.x = t.x;
		f.y = t.y;
		f.z = t.z;
		f.start = System.nanoTime();
		f.style = Math.max(0, Math.min(STYLES.length - 1, style.getInt()));
		f.anim = anim.getInt();
		f.effect = Math.max(0, Math.min(EFFECTS.length - 1, effect.getInt()));
		f.life = LIFES[f.effect];
		f.seed = (int) (f.start % 1000);
		f.hue = hue.getFloat();
		// Крышка откидывается от игрока, чтобы внутрь было видно
		double dx = t.x - client.player.getX();
		double dz = t.z - client.player.getZ();
		f.yaw = (float) Math.atan2(-dz, dx);
		list.add(f);
		if (list.size() > 6) list.remove(0);
	}

	// ---------- Анимация ----------

	private static float clamp01(float v) {
		return Math.max(0.0f, Math.min(1.0f, v));
	}

	private static float easeOutBack(float u) {
		u = clamp01(u);
		float c1 = 1.4f;
		float c3 = c1 + 1.0f;
		float x = u - 1.0f;
		return 1.0f + c3 * x * x * x + c1 * x * x;
	}

	private void render(WorldRenderContext context) {
		if (list.isEmpty()) return;
		MatrixStack matrices = context.matrixStack();
		if (matrices == null) return;
		Vec3d cam = context.camera().getPos();
		cm = matrices.peek().getPositionMatrix();
		long now = System.nanoTime();
		list.removeIf(f -> (now - f.start) / 1.0e9f > f.life);

		for (Fx f : list) {
			float t = (now - f.start) / 1.0e9f;
			float gx = (float) (f.x - cam.x);
			float gy = (float) (f.y - cam.y);
			float gz = (float) (f.z - cam.z);
			palette(f);
			V g = new V(gx, gy + 0.02f, gz);
			float sz = size.getFloat();
			switch (f.effect) {
				case 0 -> drawCoffin(f, t, gx, gy + 0.02f, gz);
				case 1 -> drawSouls(t, g, sz);
				case 2 -> drawLightning(f, t, g, sz);
				case 3 -> drawShards(t, g, sz);
				case 4 -> drawVortex(t, g, sz);
				case 5 -> drawFirework(t, g, sz);
				default -> drawPentagram(t, g, sz);
			}
		}
		solid.flush();
		glow.flush();
	}

	private void drawCoffin(Fx f, float t, float cx, float cy, float cz) {
		float S = size.getFloat();
		int st = f.style;
		float h = st == 8 ? f.hue : GLOW_HUE[st];
		curHue = h;
		float[] trim = st == 8 ? Fx3d.color(f.hue, 0.85f, 1.0f) : TRIM[st];
		float[] wood = st == 8 ? Fx3d.color(f.hue, 0.6f, 0.42f) : WOOD[st];

		// --- Время ---
		float yOff = 0.0f;
		float scl = 1.0f;
		float spin = 0.0f;
		float landT = 0.55f;
		if (f.anim == 1) {
			float u = clamp01(t / landT);
			yOff = 7.0f * (1.0f - u * u);
			if (t > landT && t < landT + 0.28f) yOff += 0.09f * (float) Math.sin(Math.PI * (t - landT) / 0.28f);
		} else {
			float u = clamp01(t / 0.6f);
			float e = easeOutBack(u);
			yOff = -1.0f * (1.0f - e);
			if (f.anim == 2) {
				spin = (1.0f - e) * (float) (Math.PI * 2.0);
				scl = 0.45f + 0.55f * e;
			}
			landT = 0.42f;
		}
		float sink = 0.0f;
		if (t > 2.95f) {
			float v = clamp01((t - 2.95f) / 0.7f);
			sink = v * v;
			yOff -= 1.1f * sink;
		}
		float lid = 0.0f;
		if (t > 0.95f && t < 2.3f) {
			lid = easeOutBack((t - 0.95f) / 0.55f) * OPEN_ANGLE;
		} else if (t >= 2.3f) {
			float u = clamp01((t - 2.3f) / 0.4f);
			lid = OPEN_ANGLE * (1.0f - u * u * u);
			if (t > 2.7f && t < 2.88f) lid = 0.07f * (float) Math.sin(Math.PI * (t - 2.7f) / 0.18f);
		}
		float openAmt = clamp01(lid / OPEN_ANGLE);
		float shake = (t > 2.68f && t < 2.9f) ? (float) Math.sin((t - 2.68f) * 70.0f) * 0.012f * (1.0f - (t - 2.68f) / 0.22f) : 0.0f;

		final float yaw = f.yaw + spin;
		final float ca = (float) Math.cos(yaw);
		final float sa = (float) Math.sin(yaw);
		final float k = S * scl;
		final float oy = cy + yOff * S + shake;
		final float fcx = cx;
		final float fcz = cz;
		final float lc = (float) Math.cos(lid);
		final float ls = (float) Math.sin(lid);

		Tf base = (x, y, z) -> new V(fcx + (x * ca + z * sa) * k, oy + y * k, fcz + (-x * sa + z * ca) * k);
		Tf lidTf = (x, y, z) -> {
			float dx = x - HX;
			float xr = HX + dx * lc + y * ls;
			float yr = H + (-dx * ls + y * lc);
			return base.at(xr, yr, z);
		};

		// --- Корпус ---
		cur = wood;
		float[] dark = {wood[0] * 0.30f, wood[1] * 0.30f, wood[2] * 0.30f};
		strip(base, 0.82f, 0.0f, 1.0f, H, wood, 0.55f, 1.0f, true);
		strip(base, 0.897f, 0.18f, 0.918f, 0.225f, trim, 0.85f, 0.95f, false);
		strip(base, 1.0f, H, 0.84f, H, wood, 1.0f, 1.0f, true);
		strip(base, 1.0f, H + 0.006f, 0.92f, H + 0.006f, trim, 1.0f, 1.0f, false);
		strip(base, 0.84f, H, 0.72f, 0.1f, dark, 0.7f, 0.35f, false);
		cap(base, 0.72f, 0.1f, dark, 0.45f);
		cap(base, 0.82f, 0.0f, dark, 0.4f);

		// --- Крышка ---
		strip(lidTf, 1.003f, 0.0f, 1.003f, 0.05f, trim, 0.9f, 1.0f, false);
		strip(lidTf, 1.0f, 0.05f, 0.9f, 0.095f, wood, 0.95f, 1.0f, true);
		strip(lidTf, 0.9f, 0.095f, 0.66f, 0.13f, wood, 1.0f, 1.0f, true);
		cap(lidTf, 0.66f, 0.13f, wood, 1.0f);
		cap(lidTf, 1.0f, 0.0f, dark, 0.5f);
		box(lidTf, -0.035f, 0.13f, -0.6f, 0.035f, 0.15f, 0.42f, trim, 1.0f);
		box(lidTf, -0.15f, 0.13f, 0.17f, 0.15f, 0.15f, 0.24f, trim, 1.0f);
		box(lidTf, -0.07f, 0.13f, -0.38f, 0.07f, 0.145f, -0.33f, trim, 1.0f);

		// --- Свечение ---
		float g = openAmt * (t < 2.3f ? 1.0f : clamp01(1.0f - (t - 2.3f) / 0.35f) * 1.0f);
		g = Math.max(g, 0.0f) * (1.0f - sink);
		V c0 = base.at(0, H - 0.03f, 0);
		float[] gc = Fx3d.color(curHue, 0.55f, 1.0f);
		float[] gl = Fx3d.color(curHue, 0.2f, 1.0f);
		if (g > 0.02f) {
			capGlow(base, 0.84f, H - 0.02f, gc, 0.8f * g);
			float bh = 3.0f * S * clamp01(g * 1.3f);
			for (int i = 0; i < 3; i++) {
				float r = (0.24f - i * 0.07f) * S;
				float a = (0.34f + i * 0.2f) * g;
				Fx3d.band(glow, cm, c0, Fx3d.X, Fx3d.Z, Fx3d.Y, r, 0.0f, r * 0.55f, bh, 28, i == 2 ? gl : gc, a, 0.0f);
			}
			Fx3d.band(glow, cm, c0, Fx3d.X, Fx3d.Z, Fx3d.Y, 0.15f * S, 0.0f, 0.5f * S, 0.0f, 28, gc, 0.55f * g, 0.0f);
			for (int i = 0; i < 12; i++) {
				float p = ((t * 0.55f + i / 12.0f) % 1.0f);
				float ang = i * 2.4f + t * 2.6f;
				float rad = (0.1f + 0.2f * (float) Math.sin(p * Math.PI)) * S;
				V pos = new V(c0.x() + (float) Math.cos(ang) * rad, c0.y() + p * 2.7f * S, c0.z() + (float) Math.sin(ang) * rad);
				float a = (float) Math.sin(p * Math.PI) * g;
				octa(pos, (0.045f + 0.025f * Fx3d.rnd(i, 3)) * S, i % 3 == 0 ? gl : gc, a, ang);
			}
		}

		// --- Кольца: приземление и хлопок крышки ---
		V ground = new V(cx, cy + 0.015f, cz);
		float lu = (t - landT) / 0.5f;
		if (lu > 0.0f && lu < 1.0f) {
			float[] dust = f.anim == 1 ? gl : new float[]{0.75f, 0.7f, 0.62f};
			float r = (0.5f + 1.6f * lu) * S;
			Fx3d.band(glow, cm, ground, Fx3d.X, Fx3d.Z, Fx3d.Y, r - 0.18f * S, 0.0f, r, 0.0f, 40, dust, 0.0f, 0.55f * (1.0f - lu));
			Fx3d.band(glow, cm, ground, Fx3d.X, Fx3d.Z, Fx3d.Y, r, 0.0f, r * 0.96f, 0.14f * S * (1.0f - lu), 40, dust, 0.5f * (1.0f - lu), 0.0f);
		}
		float cu = (t - 2.68f) / 0.45f;
		if (cu > 0.0f && cu < 1.0f) {
			float r = (0.6f + 1.2f * cu) * S;
			Fx3d.band(glow, cm, ground, Fx3d.X, Fx3d.Z, Fx3d.Y, r - 0.1f * S, 0.0f, r, 0.0f, 40, gc, 0.0f, 0.7f * (1.0f - cu));
		}
	}

	// ---------- Рисование ----------

	private void emit(V a, V b, V c, V d, float[] col, float ao0, float ao1, float ao2, float ao3) {
		float ux = b.x() - a.x();
		float uy = b.y() - a.y();
		float uz = b.z() - a.z();
		float vx = c.x() - a.x();
		float vy = c.y() - a.y();
		float vz = c.z() - a.z();
		float nx = uy * vz - uz * vy;
		float ny = uz * vx - ux * vz;
		float nz = ux * vy - uy * vx;
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		float sh = 0.7f;
		if (len > 1.0e-7f) {
			nx /= len;
			ny /= len;
			nz /= len;
			float dl = Math.abs(nx * 0.35f + ny * 0.85f + nz * 0.4f) / 0.99f;
			float px = (a.x() + c.x()) * 0.5f;
			float py = (a.y() + c.y()) * 0.5f;
			float pz = (a.z() + c.z()) * 0.5f;
			float pl = (float) Math.sqrt(px * px + py * py + pz * pz);
			float dv = pl > 1.0e-4f ? Math.abs(nx * px + ny * py + nz * pz) / pl : 0.0f;
			sh = 0.36f + 0.52f * dl + 0.14f * dv;
		}
		put(a, col, sh * ao0);
		put(b, col, sh * ao1);
		put(c, col, sh * ao2);
		put(d, col, sh * ao3);
	}

	private void put(V p, float[] col, float k) {
		solid.vertex(cm, p.x(), p.y(), p.z(), Math.min(1.0f, col[0] * k), Math.min(1.0f, col[1] * k), Math.min(1.0f, col[2] * k), 1.0f);
	}

	private void strip(Tf tf, float s0, float y0, float s1, float y1, float[] col, float ao0, float ao1, boolean planks) {
		for (int i = 0; i < N; i++) {
			int j = (i + 1) % N;
			V a = tf.at(OX[i] * s0, y0, OZ[i] * s0);
			V b = tf.at(OX[j] * s0, y0, OZ[j] * s0);
			V c = tf.at(OX[j] * s1, y1, OZ[j] * s1);
			V d = tf.at(OX[i] * s1, y1, OZ[i] * s1);
			float[] cc = col;
			if (planks) {
				float m = 0.93f + 0.07f * (float) Math.sin(i * 2.3f);
				cc = new float[]{col[0] * m, col[1] * m, col[2] * m};
			}
			emit(a, b, c, d, cc, ao0, ao0, ao1, ao1);
		}
	}

	private void cap(Tf tf, float s, float y, float[] col, float ao) {
		V c0 = tf.at(0, y, 0);
		for (int i = 0; i < N; i++) {
			int j = (i + 1) % N;
			V a = tf.at(OX[i] * s, y, OZ[i] * s);
			V b = tf.at(OX[j] * s, y, OZ[j] * s);
			emit(c0, a, b, b, col, ao, ao, ao, ao);
		}
	}

	private void capGlow(Tf tf, float s, float y, float[] col, float alpha) {
		V c0 = tf.at(0, y, 0);
		for (int i = 0; i < N; i++) {
			int j = (i + 1) % N;
			V a = tf.at(OX[i] * s, y, OZ[i] * s);
			V b = tf.at(OX[j] * s, y, OZ[j] * s);
			Fx3d.quad(glow, cm, c0, a, b, b, col, alpha, alpha * 0.25f, alpha * 0.25f, alpha * 0.25f);
		}
	}

	private void box(Tf tf, float x0, float y0, float z0, float x1, float y1, float z1, float[] col, float ao) {
		V a = tf.at(x0, y0, z0);
		V b = tf.at(x1, y0, z0);
		V c = tf.at(x1, y0, z1);
		V d = tf.at(x0, y0, z1);
		V e = tf.at(x0, y1, z0);
		V f = tf.at(x1, y1, z0);
		V g = tf.at(x1, y1, z1);
		V h = tf.at(x0, y1, z1);
		emit(e, f, g, h, col, ao, ao, ao, ao);
		emit(a, b, f, e, col, ao * 0.8f, ao * 0.8f, ao, ao);
		emit(d, c, g, h, col, ao * 0.8f, ao * 0.8f, ao, ao);
		emit(a, d, h, e, col, ao * 0.8f, ao * 0.8f, ao, ao);
		emit(b, c, g, f, col, ao * 0.8f, ao * 0.8f, ao, ao);
	}

	/** Светящийся ромб-октаэдр (душа). */
	private void octa(V c, float s, float[] col, float alpha, float rot) {
		float cs = (float) Math.cos(rot) * s;
		float sn = (float) Math.sin(rot) * s;
		V px = new V(c.x() + cs, c.y(), c.z() + sn);
		V nx = new V(c.x() - cs, c.y(), c.z() - sn);
		V pz = new V(c.x() - sn, c.y(), c.z() + cs);
		V nz = new V(c.x() + sn, c.y(), c.z() - cs);
		V top = new V(c.x(), c.y() + s * 1.7f, c.z());
		V bot = new V(c.x(), c.y() - s * 1.7f, c.z());
		V[] ring = {px, pz, nx, nz};
		for (int i = 0; i < 4; i++) {
			V a = ring[i];
			V b = ring[(i + 1) % 4];
			Fx3d.quad(glow, cm, a, b, top, top, col, alpha, alpha, alpha, alpha);
			Fx3d.quad(glow, cm, a, b, bot, bot, col, alpha, alpha, alpha * 0.8f, alpha * 0.8f);
		}
	}

	// ---------- Другие эффекты ----------

	private float[] gMain = {1, 1, 1};
	private float[] gLight = {1, 1, 1};
	private float[] gAcc = {1, 1, 1};

	private void palette(Fx f) {
		float h = f.style == 8 ? f.hue : GLOW_HUE[f.style];
		gMain = Fx3d.color(h, 0.6f, 1.0f);
		gLight = Fx3d.color(h, 0.15f, 1.0f);
		gAcc = Fx3d.color(h + 30.0f, 0.75f, 1.0f);
	}

	/** Светящаяся палка между двумя точками: два перекрёстных прямоугольника, выглядит объёмно с любой стороны. */
	private void bolt(V a, V b, float w, float[] col, float a0, float a1) {
		float dx = b.x() - a.x();
		float dy = b.y() - a.y();
		float dz = b.z() - a.z();
		float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1.0e-5f || (a0 <= 0.001f && a1 <= 0.001f)) return;
		dx /= len;
		dy /= len;
		dz /= len;
		float rx = 0.0f;
		float ry = 1.0f;
		float rz = 0.0f;
		if (Math.abs(dy) > 0.9f) {
			rx = 1.0f;
			ry = 0.0f;
		}
		float p1x = dy * rz - dz * ry;
		float p1y = dz * rx - dx * rz;
		float p1z = dx * ry - dy * rx;
		float l1 = (float) Math.sqrt(p1x * p1x + p1y * p1y + p1z * p1z);
		p1x = p1x / l1 * w;
		p1y = p1y / l1 * w;
		p1z = p1z / l1 * w;
		float p2x = dy * p1z - dz * p1y;
		float p2y = dz * p1x - dx * p1z;
		float p2z = dx * p1y - dy * p1x;
		Fx3d.quad(glow, cm, new V(a.x() - p1x, a.y() - p1y, a.z() - p1z), new V(a.x() + p1x, a.y() + p1y, a.z() + p1z),
				new V(b.x() + p1x, b.y() + p1y, b.z() + p1z), new V(b.x() - p1x, b.y() - p1y, b.z() - p1z), col, a0, a0, a1, a1);
		Fx3d.quad(glow, cm, new V(a.x() - p2x, a.y() - p2y, a.z() - p2z), new V(a.x() + p2x, a.y() + p2y, a.z() + p2z),
				new V(b.x() + p2x, b.y() + p2y, b.z() + p2z), new V(b.x() - p2x, b.y() - p2y, b.z() - p2z), col, a0, a0, a1, a1);
	}

	/** Плоская полоска на земле. */
	private void flatBolt(V a, V b, float w, float[] col, float alpha) {
		float dx = b.x() - a.x();
		float dz = b.z() - a.z();
		float len = (float) Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0e-5f || alpha <= 0.001f) return;
		float nx = -dz / len * w;
		float nz = dx / len * w;
		Fx3d.quad(glow, cm, new V(a.x() - nx, a.y(), a.z() - nz), new V(a.x() + nx, a.y(), a.z() + nz),
				new V(b.x() + nx, b.y(), b.z() + nz), new V(b.x() - nx, b.y(), b.z() - nz), col, alpha, alpha, alpha, alpha);
	}

	private void groundRing(V g, float r, float width, float[] col, float alpha) {
		if (alpha <= 0.001f) return;
		Fx3d.band(glow, cm, g, Fx3d.X, Fx3d.Z, Fx3d.Y, r - width, 0.0f, r, 0.0f, 44, col, 0.0f, alpha);
		Fx3d.band(glow, cm, g, Fx3d.X, Fx3d.Z, Fx3d.Y, r, 0.0f, r + width * 0.35f, 0.0f, 44, col, alpha, 0.0f);
	}

	// Души: несколько светящихся душ закручиваются вверх и тянут за собой хвост
	private V soulPos(V g, float p, int k, float s) {
		float ang = k * 1.57f + p * 8.0f + k;
		float rad = (0.22f + 0.22f * (float) Math.sin(p * Math.PI)) * s;
		return new V(g.x() + (float) Math.cos(ang) * rad, g.y() + 0.1f + p * 3.2f * s, g.z() + (float) Math.sin(ang) * rad);
	}

	private void drawSouls(float t, V g, float s) {
		for (int k = 0; k < 4; k++) {
			float p = clamp01((t - k * 0.2f) / 2.3f);
			if (p <= 0.0f || p >= 1.0f) continue;
			int trail = 18;
			V prev = null;
			float prevFade = 0.0f;
			for (int i = 0; i <= trail; i++) {
				float pp = p - i * 0.016f;
				if (pp < 0.0f) break;
				V q = soulPos(g, pp, k, s);
				float fade = (float) Math.sin(Math.PI * pp) * (1.0f - i / (float) trail);
				if (prev != null) {
					bolt(prev, q, 0.04f * s * (1.0f - i / (float) trail * 0.7f), i % 2 == 0 ? gLight : gMain, prevFade * 0.9f, fade * 0.9f);
				}
				prev = q;
				prevFade = fade;
			}
			V head = soulPos(g, p, k, s);
			float a = (float) Math.pow(Math.sin(Math.PI * p), 0.7);
			octa(head, 0.09f * s, gLight, a * 0.95f, t * 3.0f + k);
			octa(head, 0.17f * s, gMain, a * 0.35f, -t * 2.0f);
		}
		float u = t / 0.8f;
		if (u < 1.0f) groundRing(g, (0.3f + 1.4f * u) * s, 0.35f * s, gMain, 0.7f * (1.0f - u));
	}

	// Молния: удар с неба, ветки, вспышка и искры
	private void drawLightning(Fx f, float t, V g, float s) {
		float flash = t < 0.13f ? 1.0f : (t < 0.24f ? 0.0f : (t < 0.38f ? 0.85f * (1.0f - (t - 0.24f) / 0.14f) : 0.0f));
		if (flash > 0.0f) {
			int n = 12;
			V[] pts = new V[n + 1];
			for (int i = 0; i <= n; i++) {
				float y = 15.0f * s * (1.0f - i / (float) n);
				float k = (i == 0 || i == n) ? 0.0f : 1.0f;
				float ox = (Fx3d.rnd(i, f.seed) - 0.5f) * 1.5f * s * k * (0.3f + 0.7f * (1.0f - i / (float) n) + 0.2f);
				float oz = (Fx3d.rnd(i, f.seed + 7) - 0.5f) * 1.5f * s * k * (0.3f + 0.7f * (1.0f - i / (float) n) + 0.2f);
				pts[i] = new V(g.x() + ox * (i > n - 2 ? 0.2f : 1.0f), g.y() + y, g.z() + oz * (i > n - 2 ? 0.2f : 1.0f));
			}
			for (int i = 0; i < n; i++) {
				bolt(pts[i], pts[i + 1], 0.22f * s, gMain, 0.3f * flash, 0.3f * flash);
				bolt(pts[i], pts[i + 1], 0.09f * s, gLight, 0.9f * flash, 0.9f * flash);
				bolt(pts[i], pts[i + 1], 0.035f * s, new float[]{1, 1, 1}, flash, flash);
			}
			for (int b = 0; b < 3; b++) {
				int idx = 4 + b * 2;
				float bx = (Fx3d.rnd(b, f.seed + 11) - 0.5f) * 3.0f * s;
				float bz = (Fx3d.rnd(b, f.seed + 13) - 0.5f) * 3.0f * s;
				V e = new V(pts[idx].x() + bx, pts[idx].y() - 3.0f * s, pts[idx].z() + bz);
				V mid = new V((pts[idx].x() + e.x()) * 0.5f + 0.3f * s, (pts[idx].y() + e.y()) * 0.5f, (pts[idx].z() + e.z()) * 0.5f);
				bolt(pts[idx], mid, 0.05f * s, gLight, 0.8f * flash, 0.7f * flash);
				bolt(mid, e, 0.04f * s, gLight, 0.7f * flash, 0.0f);
			}
			groundRing(g, 1.0f * s, 0.9f * s, gLight, 0.6f * flash);
		}
		float u = (t - 0.05f) / 1.4f;
		if (u > 0.0f && u < 1.0f) {
			groundRing(g, (0.4f + 2.3f * u) * s, 0.5f * s, gMain, 0.7f * (1.0f - u));
			for (int i = 0; i < 10; i++) {
				float a = Fx3d.rnd(i, f.seed + 3) * 6.283f;
				float rad = (0.15f + Fx3d.rnd(i, 5) * 0.9f) * s;
				float h = u * (0.6f + Fx3d.rnd(i, 9) * 1.8f) * s;
				octa(new V(g.x() + (float) Math.cos(a) * rad, g.y() + h, g.z() + (float) Math.sin(a) * rad), 0.04f * s,
						i % 2 == 0 ? gLight : gMain, 1.0f - u, t * 8.0f + i);
			}
		}
	}

	// Осколки: тело рассыпается светящимися гранями, они разлетаются и падают
	private void drawShards(float t, V g, float s) {
		int n = 32;
		for (int i = 0; i < n; i++) {
			float a = Fx3d.rnd(i, 1) * 6.283f;
			float sp = 1.1f + Fx3d.rnd(i, 2) * 2.6f;
			float vy = 2.2f + Fx3d.rnd(i, 3) * 3.4f;
			float dist = sp * (1.0f - (float) Math.exp(-2.4f * t)) / 2.4f * s;
			float y = 0.9f * s + (vy * t - 4.9f * t * t) * s;
			if (y < 0.03f * s) y = 0.03f * s;
			float size = (0.05f + 0.09f * Fx3d.rnd(i, 4)) * s;
			float[] col = i % 3 == 0 ? gAcc : (i % 3 == 1 ? gMain : gLight);
			float alpha = 1.0f - clamp01((t - 1.7f) / 0.8f);
			octa(new V(g.x() + (float) Math.cos(a) * dist, g.y() + y, g.z() + (float) Math.sin(a) * dist), size, col,
					alpha * 0.95f, t * (5.0f + 9.0f * Fx3d.rnd(i, 6)) + i);
		}
		if (t < 0.28f) {
			float u = t / 0.28f;
			octa(new V(g.x(), g.y() + 0.9f * s, g.z()), 0.55f * s * (0.5f + u), gLight, 0.9f * (1.0f - u), t * 4.0f);
			octa(new V(g.x(), g.y() + 0.9f * s, g.z()), 0.9f * s * (0.5f + u), gMain, 0.4f * (1.0f - u), -t * 3.0f);
		}
		float u = t / 0.9f;
		if (u < 1.0f) groundRing(g, (0.3f + 2.6f * u) * s, 0.4f * s, gMain, 0.7f * (1.0f - u));
	}

	// Вихрь: спирали света поднимаются столбом
	private void drawVortex(float t, V g, float s) {
		float env = clamp01(t / 0.45f) * (1.0f - clamp01((t - 2.2f) / 0.8f));
		if (env <= 0.0f) return;
		float height = 3.3f * s * clamp01(t / 0.7f);
		for (int r = 0; r < 3; r++) {
			V prev = null;
			float prevA = 0.0f;
			for (int i = 0; i <= 44; i++) {
				float u = i / 44.0f;
				float ang = r * 2.094f + u * 9.0f + t * 4.5f;
				float rad = (0.12f + 0.55f * u) * s;
				V q = new V(g.x() + (float) Math.cos(ang) * rad, g.y() + 0.05f + u * height, g.z() + (float) Math.sin(ang) * rad);
				float a = env * (float) Math.pow(Math.sin(Math.PI * u), 0.6);
				if (prev != null) bolt(prev, q, 0.05f * s, r == 1 ? gLight : gMain, prevA * 0.9f, a * 0.9f);
				prev = q;
				prevA = a;
			}
		}
		V base = new V(g.x(), g.y() + 0.04f, g.z());
		Fx3d.band(glow, cm, base, Fx3d.X, Fx3d.Z, Fx3d.Y, 0.25f * s, 0.0f, 0.65f * s, height, 32, gMain, 0.3f * env, 0.0f);
		for (int i = 0; i < 12; i++) {
			float p = (t * 0.45f + i / 12.0f) % 1.0f;
			float ang = i * 2.4f + t * 5.0f;
			float rad = (0.12f + 0.5f * p) * s;
			octa(new V(g.x() + (float) Math.cos(ang) * rad, g.y() + p * height, g.z() + (float) Math.sin(ang) * rad), 0.04f * s,
					i % 2 == 0 ? gLight : gAcc, env * (float) Math.sin(Math.PI * p), t * 6.0f + i);
		}
		groundRing(g, (0.7f + 0.15f * (float) Math.sin(t * 5.0f)) * s, 0.5f * s, gMain, 0.6f * env);
	}

	// Фейерверк: ракета взлетает и взрывается шаром искр
	private void drawFirework(float t, V g, float s) {
		float burstT = 0.75f;
		float top = 3.4f * s;
		V burst = new V(g.x(), g.y() + top, g.z());
		if (t < burstT) {
			float u = t / burstT;
			float yy = top * (1.0f - (1.0f - u) * (1.0f - u));
			V head = new V(g.x(), g.y() + yy, g.z());
			V prev = head;
			for (int i = 1; i <= 10; i++) {
				float u2 = Math.max(0.0f, u - i * 0.03f);
				float y2 = top * (1.0f - (1.0f - u2) * (1.0f - u2));
				V q = new V(g.x() + (Fx3d.rnd(i, 4) - 0.5f) * 0.06f * s, g.y() + y2, g.z() + (Fx3d.rnd(i, 8) - 0.5f) * 0.06f * s);
				float a = 1.0f - i / 10.0f;
				bolt(prev, q, 0.04f * s * a, i % 2 == 0 ? gAcc : gLight, a * 0.9f, (a - 0.1f) * 0.9f);
				prev = q;
			}
			octa(head, 0.07f * s, gLight, 1.0f, t * 20.0f);
			return;
		}
		float dt = t - burstT;
		float env = 1.0f - clamp01(dt / 1.9f);
		if (env <= 0.0f) return;
		if (dt < 0.22f) {
			float u = dt / 0.22f;
			octa(burst, 0.6f * s * (0.4f + u), gLight, 0.9f * (1.0f - u), dt * 8.0f);
			octa(burst, 1.0f * s * (0.4f + u), gMain, 0.35f * (1.0f - u), -dt * 5.0f);
		}
		int n = 44;
		for (int i = 0; i < n; i++) {
			float y = 1.0f - 2.0f * (i + 0.5f) / n;
			float rr = (float) Math.sqrt(1.0f - y * y);
			float phi = i * 2.39996f;
			float dx = (float) Math.cos(phi) * rr;
			float dz = (float) Math.sin(phi) * rr;
			float speed = (1.1f + 0.35f * Fx3d.rnd(i, 5)) * 1.9f * s;
			float dist = speed * (1.0f - (float) Math.exp(-3.0f * dt)) / 3.0f;
			float droop = 0.7f * dt * dt * s;
			V pos = new V(burst.x() + dx * dist, burst.y() + y * dist - droop, burst.z() + dz * dist);
			V tail = new V(pos.x() - dx * 0.22f * s * env, pos.y() - y * 0.22f * s * env + 0.02f * s, pos.z() - dz * 0.22f * s * env);
			float[] col = i % 3 == 0 ? gAcc : (i % 3 == 1 ? gMain : gLight);
			bolt(tail, pos, 0.028f * s, col, 0.0f, env);
			octa(pos, 0.035f * s, col, env, dt * 9.0f + i);
		}
	}

	// Пентаграмма: светящийся круг со звездой на земле и столб света
	private void drawPentagram(float t, V g, float s) {
		float env = clamp01(t / 0.5f) * (1.0f - clamp01((t - 2.9f) / 0.7f));
		if (env <= 0.0f) return;
		float sc = 0.3f + 0.7f * easeOutBack(clamp01(t / 0.6f));
		float rad = 1.25f * s * sc;
		V c = new V(g.x(), g.y() + 0.01f, g.z());
		float rot = t * 0.6f;
		groundRing(c, rad, 0.12f * s, gMain, 0.9f * env);
		groundRing(c, rad * 0.9f, 0.05f * s, gLight, 0.8f * env);
		groundRing(c, rad * 0.32f, 0.06f * s, gMain, 0.7f * env);
		V[] pts = new V[5];
		for (int i = 0; i < 5; i++) {
			double ang = rot + i * Math.PI * 2.0 / 5.0 - Math.PI / 2.0;
			pts[i] = new V(c.x() + (float) Math.cos(ang) * rad * 0.9f, c.y() + 0.01f, c.z() + (float) Math.sin(ang) * rad * 0.9f);
		}
		for (int i = 0; i < 5; i++) {
			flatBolt(pts[i], pts[(i + 2) % 5], 0.035f * s, gLight, env);
			flatBolt(pts[i], pts[(i + 2) % 5], 0.09f * s, gMain, 0.3f * env);
			octa(new V(pts[i].x(), pts[i].y() + 0.1f * s, pts[i].z()), 0.06f * s, gAcc, env * 0.9f, t * 3.0f + i);
		}
		float pillarEnv = clamp01((t - 0.5f) / 0.5f) * (1.0f - clamp01((t - 2.4f) / 0.5f));
		if (pillarEnv > 0.0f) {
			float hp = 2.8f * s * clamp01((t - 0.5f) / 0.7f);
			Fx3d.band(glow, cm, c, Fx3d.X, Fx3d.Z, Fx3d.Y, rad * 0.55f, 0.0f, rad * 0.35f, hp, 36, gMain, 0.35f * pillarEnv, 0.0f);
			Fx3d.band(glow, cm, c, Fx3d.X, Fx3d.Z, Fx3d.Y, rad * 0.25f, 0.0f, rad * 0.15f, hp, 36, gLight, 0.55f * pillarEnv, 0.0f);
			for (int i = 0; i < 14; i++) {
				float p = (t * 0.5f + i / 14.0f) % 1.0f;
				float ang = i * 2.4f + t * 2.0f;
				float rr = rad * (0.15f + 0.3f * Fx3d.rnd(i, 2));
				octa(new V(c.x() + (float) Math.cos(ang) * rr, c.y() + p * hp, c.z() + (float) Math.sin(ang) * rr), 0.04f * s,
						i % 2 == 0 ? gLight : gAcc, pillarEnv * (float) Math.sin(Math.PI * p), t * 5.0f + i);
			}
		}
	}
}
