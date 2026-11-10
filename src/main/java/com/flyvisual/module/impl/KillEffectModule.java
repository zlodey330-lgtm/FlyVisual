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
	private static final String[] STYLES = {"Классика", "Королевский", "Кость", "Кровавый", "Ледяной", "Золотой",
			"Изумруд", "Неон"};
	private static final String[] ANIMS = {"Из земли", "С неба", "Вихрь"};
	private static final float[][] WOOD = {{0.34f, 0.19f, 0.10f}, {0.09f, 0.08f, 0.11f}, {0.88f, 0.85f, 0.78f},
			{0.40f, 0.05f, 0.07f}, {0.58f, 0.80f, 0.93f}, {0.82f, 0.63f, 0.16f}, {0.07f, 0.36f, 0.23f},
			{0.10f, 0.10f, 0.13f}};
	private static final float[][] TRIM = {{0.88f, 0.68f, 0.24f}, {0.95f, 0.76f, 0.27f}, {0.55f, 0.52f, 0.48f},
			{0.12f, 0.02f, 0.03f}, {0.96f, 0.99f, 1.0f}, {1.0f, 0.92f, 0.55f}, {0.88f, 0.78f, 0.32f},
			{1f, 1f, 1f}};
	private static final float[] GLOW_HUE = {48f, 280f, 200f, 0f, 190f, 50f, 140f, 200f};

	private static final float LIFE = 3.7f;
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
	}

	private static final class Tracked {
		LivingEntity entity;
		long lastHit;
		double x;
		double y;
		double z;
	}

	private final DoubleSetting style = addChoice("Effect style", 0, STYLES);
	private final DoubleSetting anim = addChoice("Animation", 0, ANIMS);
	private final DoubleSetting hue = addSetting("Color (hue)", 200.0, 0.0, 355.0, 5.0);
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
		list.removeIf(f -> (now - f.start) / 1.0e9f > LIFE);

		for (Fx f : list) {
			float t = (now - f.start) / 1.0e9f;
			drawCoffin(f, t, (float) (f.x - cam.x), (float) (f.y - cam.y) + 0.02f, (float) (f.z - cam.z));
		}
		solid.flush();
		glow.flush();
	}

	private void drawCoffin(Fx f, float t, float cx, float cy, float cz) {
		float S = size.getFloat();
		int st = f.style;
		float h = st == 7 ? f.hue : GLOW_HUE[st];
		curHue = h;
		float[] trim = st == 7 ? Fx3d.color(f.hue, 0.85f, 1.0f) : TRIM[st];
		float[] wood = WOOD[st];

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
}
