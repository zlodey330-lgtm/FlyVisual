package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.Draw3d;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.joml.Matrix3f;
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
 * Эффект убийства: на месте смерти игрока или моба разыгрывается 3D-сцена — гроб, молния,
 * рука из земли, тёмный жнец, щупальца, ангел или паук. Модели собраны из плоско затенённых граней
 * (см. KillGfx / KillModels). Виден только тебе.
 */
public class KillEffectModule extends Module {
	private static final String[] EFFECTS = {"Гроб", "Молния", "Рука из земли", "Тёмный жнец", "Щупальца", "Ангел", "Паук",
			"Случайный"};
	private static final String[] TARGETS = {"Игроки и мобы", "Только игроки", "Только мобы"};
	private static final String[] STYLES = {"Классика", "Золотой", "Чёрный", "Кровавый", "Ледяной", "Изумруд",
			"Королевский", "Кость"};

	private static final class Fx {
		double x;
		double y;
		double z;
		long start;
		int effect;
		int style;
		int seed;
		float life;
		float hgt;
		float wid;
		/** Угол, при котором локальная ось +Z смотрит на игрока. */
		float toPlayer;
		/** Угол для гроба: локальная ось +X уходит от игрока (на этой стороне петли крышки). */
		float coffinYaw;
		/** Убитый: его настоящая модель (моб или игрок со скином) рисуется внутри сцены. */
		LivingEntity victim;
		/** Модель уже показана: оригинал прячем, чтобы не было двойника. */
		boolean shown;
		/** Не удалось нарисовать настоящую модель: возвращаемся к призраку. */
		boolean broken;
	}

	/** Что нарисовать после сцены: модель убитого и её матрица. */
	private static final class VictimDraw {
		Fx fx;
		final float[] m = new float[12];
		boolean lying;
	}

	private static KillEffectModule inst;
	private final List<VictimDraw> victims = new ArrayList<>();

	/** Спрятать ли оригинал убитого (его заменяет модель внутри эффекта). Зовётся из миксина. */
	public static boolean hides(Entity e) {
		KillEffectModule k = inst;
		if (k == null || k.list.isEmpty()) return false;
		for (int i = 0; i < k.list.size(); i++) {
			Fx f = k.list.get(i);
			if (f.victim == e && f.shown && !f.broken) return true;
		}
		return false;
	}

	private static final class Tracked {
		LivingEntity entity;
		long lastHit;
		double x;
		double y;
		double z;
	}

	private final DoubleSetting effect = addChoice("Effect", 0, EFFECTS);
	private final DoubleSetting targets = addChoice("Kill targets", 0, TARGETS);
	private final DoubleSetting style = addChoice("Coffin color", 0, STYLES);
	private final DoubleSetting size = addSetting("Size", 1.0, 0.5, 2.0, 0.1);

	private final Map<Integer, Tracked> tracked = new HashMap<>();
	private final List<Fx> list = new ArrayList<>();
	private final Draw3d solid = new Draw3d(false);
	private final Draw3d glow = new Draw3d(true);
	private long tick;
	private Matrix4f cm;
	private final KillGfx gfx = new KillGfx((x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, r, g, b, a) -> {
		Matrix4f m = cm;
		if (m == null) return;
		Draw3d d = a >= 0.99f ? solid : glow;
		d.vertex(m, x0, y0, z0, r, g, b, a);
		d.vertex(m, x1, y1, z1, r, g, b, a);
		d.vertex(m, x2, y2, z2, r, g, b, a);
		d.vertex(m, x3, y3, z3, r, g, b, a);
	});

	public KillEffectModule() {
		super("Kill Effect", "3D effect where you kill a player or mob");
		inst = this;
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient() && isEnabled() && player == MinecraftClient.getInstance().player
					&& entity instanceof LivingEntity le && le != player && !(le instanceof ArmorStandEntity)
					&& matches(le)) {
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

	private boolean matches(LivingEntity le) {
		int mode = targets.getInt();
		if (mode == 1) return le instanceof PlayerEntity;
		if (mode == 2) return !(le instanceof PlayerEntity);
		return true;
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
				if (isEnabled() && matches(e)) {
					try {
						spawn(client, t);
					} catch (Throwable fxErr) {
						com.flyvisual.fpsboost.FpsBoostInit.LOGGER.warn("Kill Effect: ошибка при создании эффекта", fxErr);
					}
				}
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

	/** Высота поверхности под точкой: модель стоит на блоке, а не висит и не проваливается. */
	private static double groundY(net.minecraft.client.world.ClientWorld w, double x, double y, double z) {
		net.minecraft.util.math.BlockPos.Mutable p = new net.minecraft.util.math.BlockPos.Mutable(
				net.minecraft.util.math.MathHelper.floor(x), net.minecraft.util.math.MathHelper.floor(y + 0.1),
				net.minecraft.util.math.MathHelper.floor(z));
		for (int i = 0; i < 12 && p.getY() > w.getBottomY(); i++) {
			net.minecraft.block.BlockState state = w.getBlockState(p);
			net.minecraft.util.shape.VoxelShape shape = state.getCollisionShape(w, p);
			if (!shape.isEmpty()) {
				return p.getY() + shape.getMax(net.minecraft.util.math.Direction.Axis.Y);
			}
			p.move(net.minecraft.util.math.Direction.DOWN);
		}
		return y;
	}

	private void spawn(MinecraftClient client, Tracked t) {
		Fx f = new Fx();
		f.x = t.x;
		f.y = t.y;
		f.z = t.z;
		f.start = System.nanoTime();
		f.style = Math.max(0, Math.min(STYLES.length - 1, style.getInt()));
		int e = effect.getInt();
		if (e >= KillModels.LIFE.length) e = (int) (f.start % KillModels.LIFE.length);
		f.effect = Math.max(0, Math.min(KillModels.LIFE.length - 1, e));
		f.life = KillModels.LIFE[f.effect];
		f.seed = (int) (f.start % 1000);
		LivingEntity le = t.entity;
		f.victim = le;
		f.hgt = Math.max(0.4f, Math.min(4.0f, le.getHeight()));
		f.wid = Math.max(0.3f, Math.min(2.0f, le.getWidth()));
		if (client.world != null) {
			try {
				double gy = groundY(client.world, t.x, t.y, t.z);
				if (f.effect == 0) {
					// гроб широкий: берём самую высокую опору рядом, чтобы он не проваливался в край блока
					double top = gy;
					for (int i = 0; i < 4; i++) {
						double ox = (i & 1) == 0 ? -0.5 : 0.5;
						double oz = (i & 2) == 0 ? -0.8 : 0.8;
						double g2 = groundY(client.world, t.x + ox, t.y, t.z + oz);
						if (g2 > top && g2 <= gy + 0.6) top = g2;
					}
					gy = top;
				}
				f.y = gy;
			} catch (Throwable fxErr) {
				com.flyvisual.fpsboost.FpsBoostInit.LOGGER.warn("Kill Effect: не удалось найти землю", fxErr);
			}
		}
		double dx = t.x - client.player.getX();
		double dz = t.z - client.player.getZ();
		f.toPlayer = (float) Math.atan2(-dx, -dz);
		f.coffinYaw = (float) Math.atan2(-dz, dx);
		list.add(f);
		if (list.size() > 5) list.remove(0);
	}

	private void render(WorldRenderContext context) {
		if (list.isEmpty()) return;
		MatrixStack matrices = context.matrixStack();
		if (matrices == null) return;
		Vec3d cam = context.camera().getPos();
		cm = matrices.peek().getPositionMatrix();
		long now = System.nanoTime();
		list.removeIf(f -> (now - f.start) / 1.0e9f > f.life);
		float sz = size.getFloat();
		try {
			for (Fx f : list) {
				float t = (now - f.start) / 1.0e9f;
				float gx = (float) (f.x - cam.x);
				float gy = (float) (f.y - cam.y);
				float gz = (float) (f.z - cam.z);
				if (gx * gx + gy * gy + gz * gz > 120.0f * 120.0f) continue;
				try {
					draw(f, t, gx, gy, gz, sz);
					if (gfx.vOn && gfx.realVictim) {
						VictimDraw vd = new VictimDraw();
						vd.fx = f;
						System.arraycopy(gfx.vm, 0, vd.m, 0, 12);
						vd.lying = gfx.vLying;
						victims.add(vd);
					}
				} catch (Throwable fxErr) {
					com.flyvisual.fpsboost.FpsBoostInit.LOGGER.warn("Kill Effect: ошибка отрисовки", fxErr);
					f.start = 0L;
				}
			}
		} finally {
			solid.flush();
			glow.flush();
		}
		if (!victims.isEmpty()) {
			try {
				for (VictimDraw vd : victims) drawVictim(context, matrices, vd);
			} finally {
				victims.clear();
			}
		}
	}

	/** Рисует настоящую модель убитого (моба или игрока со скином) в месте, которое задала сцена. */
	private void drawVictim(WorldRenderContext context, MatrixStack ms, VictimDraw vd) {
		Fx f = vd.fx;
		LivingEntity e = f.victim;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (e == null || mc.world == null) return;
		EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
		int deathTime = e.deathTime;
		int hurtTime = e.hurtTime;
		float bodyYaw = e.bodyYaw;
		float prevBodyYaw = e.prevBodyYaw;
		float headYaw = e.headYaw;
		float prevHeadYaw = e.prevHeadYaw;
		float pitch = e.getPitch();
		float prevPitch = e.prevPitch;
		boolean pushed = false;
		try {
			// Модель стоит (или лежит на боку) лицом к игроку, без красной вспышки урона
			e.deathTime = vd.lying ? 20 : 0;
			e.hurtTime = 0;
			e.bodyYaw = 0.0f;
			e.prevBodyYaw = 0.0f;
			e.headYaw = 0.0f;
			e.prevHeadYaw = 0.0f;
			e.setPitch(0.0f);
			e.prevPitch = 0.0f;

			float[] m = vd.m;
			Matrix4f local = new Matrix4f();
			local.m00(m[0]).m10(m[1]).m20(m[2]).m30(m[3]);
			local.m01(m[4]).m11(m[5]).m21(m[6]).m31(m[7]);
			local.m02(m[8]).m12(m[9]).m22(m[10]).m32(m[11]);
			local.m03(0.0f).m13(0.0f).m23(0.0f).m33(1.0f);
			Matrix3f normal = new Matrix3f().set(local).normalize();

			ms.push();
			pushed = true;
			ms.peek().getPositionMatrix().mul(local);
			ms.peek().getNormalMatrix().mul(normal);

			net.minecraft.util.math.BlockPos bp = net.minecraft.util.math.BlockPos.ofFloored(f.x, f.y + 1.0, f.z);
			int light = WorldRenderer.getLightmapCoordinates(mc.world, bp);
			VertexConsumerProvider.Immediate vc = mc.getBufferBuilders().getEntityVertexConsumers();
			dispatcher.setRenderShadows(false);
			dispatcher.render(e, 0.0, 0.0, 0.0, 0.0f, 1.0f, ms, vc, light);
			vc.draw();
			f.shown = true;
		} catch (Throwable err) {
			f.broken = true;
			com.flyvisual.fpsboost.FpsBoostInit.LOGGER.warn("Kill Effect: не удалось нарисовать модель убитого", err);
		} finally {
			if (pushed) ms.pop();
			dispatcher.setRenderShadows(mc.options.getEntityShadows().getValue());
			e.deathTime = deathTime;
			e.hurtTime = hurtTime;
			e.bodyYaw = bodyYaw;
			e.prevBodyYaw = prevBodyYaw;
			e.headYaw = headYaw;
			e.prevHeadYaw = prevHeadYaw;
			e.setPitch(pitch);
			e.prevPitch = prevPitch;
		}
	}

	private void draw(Fx f, float t, float gx, float gy, float gz, float sz) {
		KillGfx g = gfx;
		g.reset();
		g.emissive = false;
		g.fresnel = false;
		g.lum = 1.0f;
		g.realVictim = f.victim != null && !f.broken;
		g.vh = f.victim != null ? Math.max(0.3f, f.victim.getHeight()) : f.hgt;
		g.vw = f.victim != null ? Math.max(0.2f, f.victim.getWidth()) : f.wid;
		g.victimScale = Math.min(1.0f, 3.0f / Math.max(g.vh, g.vw));
		g.translate(gx, gy, gz);
		float hgt = f.hgt;
		float wid = f.wid;
		switch (f.effect) {
			case 0 -> {
				g.rotY(f.coffinYaw);
				g.scale(sz);
				KillModels.sceneCoffin(g, t, f.style, f.seed, KillModels.hs(hgt));
			}
			case 1 -> {
				g.scale(sz);
				KillModels.sceneLightning(g, t, f.seed);
			}
			default -> {
				g.rotY(f.toPlayer);
				g.scale(sz);
				switch (f.effect) {
					case 2 -> KillModels.sceneHand(g, t, hgt, wid, f.seed);
					case 3 -> KillModels.sceneReaper(g, t, hgt, wid, f.seed);
					case 4 -> KillModels.sceneTentacles(g, t, hgt, wid, f.seed);
					case 5 -> KillModels.sceneAngel(g, t, hgt, wid, f.seed);
					default -> KillModels.sceneSpider(g, t, hgt, wid, f.seed);
				}
			}
		}
	}
}
