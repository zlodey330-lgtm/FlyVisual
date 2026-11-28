package com.flyvisual.module.impl;

import com.flyvisual.config.Theme;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.ColorUtil;
import com.flyvisual.util.Draw3d;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * TargetESP: объёмный 3D-эффект вокруг цели (души, шляпа, гироскоп, кристалл, змей, сфера, воронка).
 * Чисто визуальный. Появляется, когда ты ударил цель и урон реально прошёл (цель вздрогнула), а если
 * включено "При наведении", то ещё и просто когда смотришь на неё. Невидимых игроков без брони не показывает.
 * Модели собраны из мягких свечений, лент, шаров и трубок (см. EspGfx / EspModels).
 */
public class TargetEspModule extends Module {
	private static final String[] STYLES = {"Души", "Шляпа", "Гироскоп", "Кристалл", "Змей", "Сфера", "Воронка"};
	/** Сколько тиков ждём подтверждения урона после удара (запас на пинг). */
	private static final int HIT_WINDOW_TICKS = 20;

	private final DoubleSetting style = addChoice("Style", 0, STYLES);
	private final DoubleSetting onAim = addToggle("Show on aim", false);
	private final DoubleSetting holdTime = addSetting("Hold time (s)", 3.0, 0.5, 10.0, 0.5);
	private final DoubleSetting scale = addSetting("Scale", 1.0, 0.5, 2.0, 0.05);
	private final DoubleSetting glow = addSetting("Glow", 1.0, 0.3, 2.0, 0.05);
	private final DoubleSetting animSpeed = addSetting("Anim speed", 1.0, 0.3, 2.5, 0.05);
	private final DoubleSetting themeColor = addToggle("Theme color", true);
	private final DoubleSetting hue = addSetting("Color (hue)", 200.0, 0.0, 355.0, 5.0);
	private final DoubleSetting rainbow = addToggle("Rainbow", false);
	private final DoubleSetting playersOnly = addToggle("Players only", false);

	/** Накопитель вершин: сцену собираем целиком, потом рисуем двумя вызовами (обычный и аддитивный слой). */
	private static final class Buf {
		float[] d = new float[8 * 2048];
		int n;

		void v(float x, float y, float z, float r, float g, float b, float a) {
			if (n + 8 > d.length) d = java.util.Arrays.copyOf(d, d.length * 2);
			d[n++] = x;
			d[n++] = y;
			d[n++] = z;
			d[n++] = r;
			d[n++] = g;
			d[n++] = b;
			d[n++] = a;
			n++;
		}

		void emit(Draw3d out, Matrix4f m) {
			for (int i = 0; i < n; i += 8) {
				out.vertex(m, d[i], d[i + 1], d[i + 2], d[i + 3], d[i + 4], d[i + 5], d[i + 6]);
			}
			n = 0;
		}
	}

	private final Buf solidBuf = new Buf();
	private final Buf addBuf = new Buf();
	private final Draw3d solidOut = new Draw3d(true, false);
	private final Draw3d addOut = new Draw3d(true, true);
	private final EspGfx gfx = new EspGfx(solidBuf::v, addBuf::v);
	private final EspModels.P frame = new EspModels.P();

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
	private double animT;

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

	private static float[] toRgb(int rgb) {
		return new float[]{((rgb >> 16) & 0xFF) / 255.0f, ((rgb >> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f};
	}

	private static float[] lighten(float[] c, float k) {
		return new float[]{c[0] + (1.0f - c[0]) * k, c[1] + (1.0f - c[1]) * k, c[2] + (1.0f - c[2]) * k};
	}

	/** Выбирает цвета кадра: основной, второй и светлый. */
	private void pickColors(EspModels.P p, double t) {
		float[] c1;
		float[] c2;
		if (rainbow.getBool()) {
			c1 = toRgb(ColorUtil.hsv((float) ((t * 50.0) % 360.0), 0.75f, 1.0f));
			c2 = toRgb(ColorUtil.hsv((float) ((t * 50.0 + 70.0) % 360.0), 0.75f, 1.0f));
		} else if (themeColor.getBool()) {
			c1 = toRgb(Theme.accent() & 0xFFFFFF);
			c2 = lighten(c1, 0.4f);
		} else {
			c1 = toRgb(ColorUtil.hsv(hue.getFloat(), 0.85f, 1.0f));
			c2 = toRgb(ColorUtil.hsv((hue.getFloat() + 40.0f) % 360.0f, 0.8f, 1.0f));
		}
		p.c1 = c1;
		p.c2 = c2;
		p.cw = lighten(c1, 0.75f);
	}

	private void render(WorldRenderContext context) {
		if (!isEnabled()) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || context.world() == null) return;

		long nowNanos = System.nanoTime();
		float dt = Math.min((nowNanos - lastNanos) / 1.0e9f, 0.1f);
		lastNanos = nowNanos;
		animT = (animT + dt * animSpeed.get()) % 3600.0;

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
			alpha += (1.0f - alpha) * (1.0f - (float) Math.exp(-7.0f * dt));
		} else {
			alpha += (0.0f - alpha) * (1.0f - (float) Math.exp(-6.0f * dt));
			if (alpha < 0.02f) {
				alpha = 0.0f;
				shown = null;
				return;
			}
		}
		if (shown == null || shown.isRemoved()) return;

		MatrixStack matrices = context.matrixStack();
		if (matrices == null) return;
		Matrix4f m = matrices.peek().getPositionMatrix();
		Vec3d cam = context.camera().getPos();
		Vec3d pos = shown.getLerpedPos(context.tickDelta()).subtract(cam);

		EspModels.P p = frame;
		p.x = (float) pos.x;
		p.y = (float) pos.y;
		p.z = (float) pos.z;
		p.h = shown.getHeight();
		p.w = shown.getWidth();
		p.t = (float) animT;
		// плавное появление: растёт и проявляется
		float appear = alpha * alpha * (3.0f - 2.0f * alpha);
		p.a = appear;
		p.sc = scale.getFloat() * (0.8f + 0.2f * appear);
		p.gl = glow.getFloat();
		pickColors(p, animT);

		solidBuf.n = 0;
		addBuf.n = 0;
		try {
			switch (style.getInt()) {
				case 1 -> EspModels.hat(gfx, p);
				case 2 -> EspModels.gyro(gfx, p);
				case 3 -> EspModels.crystal(gfx, p);
				case 4 -> EspModels.serpent(gfx, p);
				case 5 -> EspModels.sphere(gfx, p);
				case 6 -> EspModels.funnel(gfx, p);
				default -> EspModels.souls(gfx, p);
			}
			solidBuf.emit(solidOut, m);
			addBuf.emit(addOut, m);
		} finally {
			solidBuf.n = 0;
			addBuf.n = 0;
			solidOut.flush();
			addOut.flush();
		}
	}
}
