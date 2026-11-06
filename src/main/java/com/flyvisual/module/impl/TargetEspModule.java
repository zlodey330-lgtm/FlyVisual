package com.flyvisual.module.impl;

import com.flyvisual.config.Theme;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.ColorUtil;
import com.flyvisual.util.Shape3D;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * TargetESP: красивый 3D-эффект вокруг цели (души, кольцо, куб, спираль). Чисто визуальный.
 * Появляется, когда ты ударил цель и урон реально прошёл (цель вздрогнула), а если включено
 * "При наведении", то ещё и просто когда смотришь на неё. Невидимых игроков без брони не показывает.
 */
public class TargetEspModule extends Module {
	private static final String[] STYLES = {"Души", "Кольцо", "Куб", "Спираль"};
	/** Сколько тиков ждём подтверждения урона после удара (запас на пинг). */
	private static final int HIT_WINDOW_TICKS = 20;

	private final DoubleSetting style = addChoice("Style", 0, STYLES);
	private final DoubleSetting onAim = addToggle("Show on aim", false);
	private final DoubleSetting holdTime = addSetting("Hold time (s)", 3.0, 0.5, 10.0, 0.5);
	private final DoubleSetting size = addSetting("Size", 0.12, 0.05, 0.3, 0.01);
	private final DoubleSetting themeColor = addToggle("Theme color", true);
	private final DoubleSetting hue = addSetting("Color (hue)", 200.0, 0.0, 355.0, 5.0);
	private final DoubleSetting rainbow = addToggle("Rainbow", false);
	private final DoubleSetting playersOnly = addToggle("Players only", false);

	// Удар, который ждёт подтверждения урона
	private LivingEntity pending;
	private int pendingHurt;
	private long pendingTick;
	private long tickCounter;

	// Цель после подтверждённого удара
	private LivingEntity hitTarget;
	private long hitUntilMs;

	private LivingEntity shown;
	private float alpha;
	private long lastNanos = System.nanoTime();

	public TargetEspModule() {
		super("TargetESP", "Visual effect around your target");

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient() && isEnabled() && player == mc().player
					&& entity instanceof LivingEntity living && living != player) {
				pending = living;
				pendingHurt = living.hurtTime;
				pendingTick = tickCounter;
			}
			return ActionResult.PASS;
		});
		WorldRenderEvents.AFTER_ENTITIES.register(this::render);
	}

	@Override
	public void onDisable() {
		pending = null;
		hitTarget = null;
		shown = null;
		alpha = 0.0f;
	}

	@Override
	public void onTick(MinecraftClient client) {
		tickCounter++;
		if (client.player == null) {
			pending = null;
			hitTarget = null;
			return;
		}

		if (pending != null) {
			if (pending.isRemoved()) {
				pending = null;
			} else if (pending.hurtTime > pendingHurt) {
				// Цель вздрогнула после нашего удара: урон прошёл
				hitTarget = pending;
				hitUntilMs = System.currentTimeMillis() + (long) (holdTime.get() * 1000.0);
				pending = null;
			} else {
				pendingHurt = pending.hurtTime;
				if (tickCounter - pendingTick > HIT_WINDOW_TICKS) {
					pending = null;
				}
			}
		}
	}

	private boolean valid(LivingEntity e) {
		MinecraftClient mc = mc();
		if (e == null || e == mc.player || e.isRemoved() || !e.isAlive()) return false;
		if (playersOnly.getBool() && !(e instanceof PlayerEntity)) return false;
		if (e.isInvisible()) {
			boolean armor = !e.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
					|| !e.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
					|| !e.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
					|| !e.getEquippedStack(EquipmentSlot.FEET).isEmpty();
			if (!armor) return false;
		}
		return true;
	}

	private int rgb(double t, int k) {
		if (rainbow.getBool()) {
			return ColorUtil.hsv((float) ((t * 60.0 + k * 40.0) % 360.0), 0.8f, 1.0f);
		}
		if (themeColor.getBool()) {
			return Theme.accent() & 0xFFFFFF;
		}
		return ColorUtil.hsv(hue.getFloat(), 0.85f, 1.0f);
	}

	private void render(WorldRenderContext context) {
		if (!isEnabled()) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || context.world() == null) return;

		long nowNanos = System.nanoTime();
		float dt = Math.min((nowNanos - lastNanos) / 1.0e9f, 0.1f);
		lastNanos = nowNanos;

		// Кого показываем: цель под прицелом (если включено) либо цель после удара
		LivingEntity want = null;
		if (onAim.getBool() && mc.currentScreen == null) {
			Entity looked = mc.targetedEntity;
			if (looked instanceof LivingEntity living && valid(living)) {
				want = living;
			}
		}
		if (want == null && hitTarget != null && System.currentTimeMillis() < hitUntilMs && valid(hitTarget)) {
			want = hitTarget;
		}

		if (want != null) {
			if (shown != want) {
				shown = want;
				alpha = 0.0f;
			}
			alpha += (1.0f - alpha) * (1.0f - (float) Math.exp(-10.0f * dt));
		} else {
			alpha += (0.0f - alpha) * (1.0f - (float) Math.exp(-8.0f * dt));
			if (alpha < 0.02f) {
				alpha = 0.0f;
				shown = null;
				return;
			}
		}
		if (shown == null || shown.isRemoved()) return;

		VertexConsumerProvider consumers = context.consumers();
		MatrixStack matrices = context.matrixStack();
		if (consumers == null || matrices == null) return;

		VertexConsumer vc = consumers.getBuffer(RenderLayer.getLightning());
		Matrix4f m = matrices.peek().getPositionMatrix();
		Vec3d cam = context.camera().getPos();
		Vec3d pos = shown.getLerpedPos(context.tickDelta()).subtract(cam);

		double t = System.nanoTime() / 1.0e9;
		double h = shown.getHeight();
		double r = shown.getWidth() * 0.5 + 0.35;
		double half = size.get();

		switch (style.getInt()) {
			case 1 -> ring(vc, m, pos, h, r, half, t);
			case 2 -> crystal(vc, m, pos, h, half, t);
			case 3 -> spiral(vc, m, pos, h, r, half, t);
			default -> souls(vc, m, pos, h, r, half, t);
		}
	}

	/** Души: светящиеся шарики кружат вокруг цели на разной высоте и тянут за собой хвост. */
	private void souls(VertexConsumer vc, Matrix4f m, Vec3d pos, double h, double r, double half, double t) {
		int n = 6;
		for (int k = 0; k < n; k++) {
			int rgb = rgb(t, k);
			for (int j = 0; j < 7; j++) {
				double tt = t - j * 0.045;
				double a = tt * 1.7 + k * 2.0 * Math.PI / n;
				double y = pos.y + h * (0.5 + 0.42 * Math.sin(tt * 1.3 + k * 1.1));
				double fade = 1.0 - j / 7.0;
				double hf = half * (0.35 + 0.65 * fade);
				double x = pos.x + Math.cos(a) * r;
				double z = pos.z + Math.sin(a) * r;
				Shape3D.cube(vc, m, x, y, z, hf, a, rgb, (float) (0.95 * fade * alpha));
				if (j < 3) {
					Shape3D.cube(vc, m, x, y, z, hf * 2.4, a, rgb, (float) (0.16 * fade * alpha));
				}
			}
		}
	}

	/** Кольцо ездит вверх-вниз по телу цели, за ним тянутся две бледные копии. */
	private void ring(VertexConsumer vc, Matrix4f m, Vec3d pos, double h, double r, double half, double t) {
		int points = 30;
		for (int trail = 0; trail < 3; trail++) {
			double tt = t - trail * 0.12;
			double y = pos.y + h * (0.5 + 0.5 * Math.sin(tt * 1.6));
			float a = (float) (alpha * (trail == 0 ? 0.95 : trail == 1 ? 0.4 : 0.18));
			for (int i = 0; i < points; i++) {
				double ang = i * 2.0 * Math.PI / points + tt * 0.8;
				int rgb = rgb(t, i);
				Shape3D.cube(vc, m, pos.x + Math.cos(ang) * r, y, pos.z + Math.sin(ang) * r, half * 0.45, ang, rgb, a);
			}
		}
	}

	/** Куб-кристалл вращается над головой цели. */
	private void crystal(VertexConsumer vc, Matrix4f m, Vec3d pos, double h, double half, double t) {
		double y = pos.y + h + 0.5 + 0.08 * Math.sin(t * 2.2);
		int rgb = rgb(t, 0);
		double big = half * 1.8;
		Shape3D.cube(vc, m, pos.x, y, pos.z, big, t * 2.0, rgb, (float) (0.9 * alpha));
		Shape3D.cube(vc, m, pos.x, y, pos.z, big * 0.55, -t * 3.0, 0xFFFFFF, (float) (0.9 * alpha));
		Shape3D.cube(vc, m, pos.x, y, pos.z, big * 2.4, t * 2.0, rgb, (float) (0.14 * alpha));
	}

	/** Две светящиеся ленты-спирали поднимаются вокруг цели. */
	private void spiral(VertexConsumer vc, Matrix4f m, Vec3d pos, double h, double r, double half, double t) {
		int steps = 26;
		for (int strand = 0; strand < 2; strand++) {
			for (int i = 0; i < steps; i++) {
				double f = i / (double) steps;
				double a = t * 2.0 + f * 5.0 * Math.PI + strand * Math.PI;
				double y = pos.y + ((f + t * 0.25) % 1.0) * h;
				int rgb = rgb(t, i);
				double fade = Math.sin(((f + t * 0.25) % 1.0) * Math.PI);
				Shape3D.cube(vc, m, pos.x + Math.cos(a) * r, y, pos.z + Math.sin(a) * r, half * 0.5, a, rgb,
						(float) (0.95 * fade * alpha));
			}
		}
	}
}
