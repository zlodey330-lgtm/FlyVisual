package com.flyvisual.module.impl;

import com.flyvisual.gui.EmoteScreen;
import com.flyvisual.hud.Notifications;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.Draw3d;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Эмоции: клавиша открывает меню, выбранная эмоция проигрывается на твоей модели.
 * Камера сама переходит на вид спереди (видно своё лицо) и после конца эмоции возвращается в первое лицо.
 * Каждая эмоция конечная. Видно только тебе: другие игроки анимацию не получают.
 * Позы накладываются в PlayerEntityModelMixin.
 */
public class EmoteModule extends Module {
	public static final String[] NAMES = {"Сикс сэвен", "Танец", "Приветствие", "Победа", "Хлопки", "Раздумье", "Поклон", "Робот",
			"Волна", "Звезда", "Бокс", "Сердце", "Дирижёр", "Смех", "Пожать плечами", "Мунвок", "Салют", "Твист", "Флосс"};
	/** Длительность каждой эмоции в секундах при скорости 1.0. */
	private static final float[] DURATIONS = {6.0f, 5.0f, 3.0f, 3.0f, 4.0f, 4.0f, 3.0f, 5.0f, 4.0f, 4.0f, 4.0f, 4.0f, 5.0f, 4.0f, 3.0f, 5.0f, 3.0f, 5.0f, 5.0f};

	private final KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.flyvisual.emotes",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_UNKNOWN,
			"category.flyvisual"
	));

	private final DoubleSetting autoThird = addToggle("Auto third person", true);
	private final DoubleSetting cancelOnSneak = addToggle("Cancel on sneak", true);
	private final DoubleSetting speed = addSetting("Emote speed", 1.0, 0.5, 2.0, 0.1);

	private int index = -1;
	private long startNanos;
	private Perspective savedPerspective;
	private long lastApplyNanos;

	public EmoteModule() {
		super("Emotes", "Emotes menu: Six Seven, dance and more");
		WorldRenderEvents.AFTER_ENTITIES.register(this::renderDigits);
	}

	@Override
	protected boolean enabledByDefault() {
		return true;
	}

	public boolean isPlaying() {
		return index >= 0;
	}

	public int current() {
		return index;
	}

	/** Запускает эмоцию номер i и при необходимости переключает камеру на вид сзади. */
	public void start(int i) {
		MinecraftClient client = mc();
		if (client == null || client.player == null || i < 0 || i >= NAMES.length) return;

		if (index >= 0) {
			stop();
		}
		index = i;
		startNanos = System.nanoTime();
		Notifications.push("Эмоция: " + NAMES[i], true);

		Perspective current = client.options.getPerspective();
		if (autoThird.getBool() && current == Perspective.FIRST_PERSON) {
			savedPerspective = current;
			client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
		} else {
			savedPerspective = null;
		}
	}

	/** Останавливает эмоцию и возвращает прежний вид камеры. */
	public void stop() {
		index = -1;
		MinecraftClient client = mc();
		if (savedPerspective != null && client != null && client.options != null) {
			client.options.setPerspective(savedPerspective);
		}
		savedPerspective = null;
	}

	@Override
	public void onDisable() {
		if (index >= 0) {
			stop();
		}
	}

	@Override
	public void onTick(MinecraftClient client) {
		if (client.player == null) {
			index = -1;
			savedPerspective = null;
			return;
		}

		while (key.wasPressed()) {
			if (client.currentScreen == null) {
				client.setScreen(new EmoteScreen());
			}
		}

		if (index >= 0) {
			float elapsed = (System.nanoTime() - startNanos) / 1.0e9f;
			boolean over = elapsed * speed.getFloat() >= DURATIONS[index];
			boolean cancel = cancelOnSneak.getBool() && client.player.isSneaking();
			if (over || cancel) {
				stop();
			}
		}
	}

	// Цифры 6 и 7 в руках: шрифт 5 x 7, единица = закрашенная клетка
	private static final String[] SIX = {".###.", "#....", "#....", "####.", "#...#", "#...#", ".###."};
	private static final String[] SEVEN = {"#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."};
	private final Draw3d digits = new Draw3d(false);

	/** Тангаж руки в эмоции «Сикс сэвен» (повторяет pose в apply). */
	private float armPitch(boolean right, float t) {
		float a = (float) Math.sin(t * (float) (Math.PI * 2.0) * 1.7f);
		return -1.45f + (right ? 0.55f : -0.55f) * a;
	}

	private static boolean on(String[] g, int row, int col) {
		return row >= 0 && row < g.length && col >= 0 && col < g[0].length() && g[row].charAt(col) == '#';
	}

	private void quad(Matrix4f m, double[] a, double[] b, double[] c, double[] d, float shade, float r, float g, float bl) {
		digits.vertex(m, (float) a[0], (float) a[1], (float) a[2], r * shade, g * shade, bl * shade, 1.0f);
		digits.vertex(m, (float) b[0], (float) b[1], (float) b[2], r * shade, g * shade, bl * shade, 1.0f);
		digits.vertex(m, (float) c[0], (float) c[1], (float) c[2], r * shade, g * shade, bl * shade, 1.0f);
		digits.vertex(m, (float) d[0], (float) d[1], (float) d[2], r * shade, g * shade, bl * shade, 1.0f);
	}

	private static double[] pt(double[] c, double[] u, double[] v, double[] w, double su, double sv, double sw) {
		return new double[]{c[0] + u[0] * su + v[0] * sv + w[0] * sw, c[1] + u[1] * su + v[1] * sv + w[1] * sw,
				c[2] + u[2] * su + v[2] * sv + w[2] * sw};
	}

	/** Рисует цифру: чёрные кубики-основа и белая лицевая сторона с чёрной окантовкой по краю. */
	private void drawGlyph(Matrix4f m, String[] glyph, double[] origin, double[] u, double[] v, double[] w, double px, double depth, float fade) {
		double h = px * 0.5;
		double hd = depth * 0.5;
		double margin = px * 0.2;
		for (int row = 0; row < glyph.length; row++) {
			for (int col = 0; col < glyph[0].length(); col++) {
				if (!on(glyph, row, col)) continue;
				double cu = (col - (glyph[0].length() - 1) * 0.5) * px;
				double cv = ((glyph.length - 1) * 0.5 - row) * px;
				double[] c = pt(origin, u, v, w, cu, cv, 0.0);
				// Чёрный кубик (грани по сторонам и сверху чуть светлее для объёма)
				if (!on(glyph, row, col - 1)) quad(m, pt(c, u, v, w, -h, -h, -hd), pt(c, u, v, w, -h, h, -hd), pt(c, u, v, w, -h, h, hd), pt(c, u, v, w, -h, -h, hd), 0.7f, 0.06f, 0.06f, 0.07f);
				if (!on(glyph, row, col + 1)) quad(m, pt(c, u, v, w, h, -h, -hd), pt(c, u, v, w, h, h, -hd), pt(c, u, v, w, h, h, hd), pt(c, u, v, w, h, -h, hd), 0.9f, 0.06f, 0.06f, 0.07f);
				if (!on(glyph, row - 1, col)) quad(m, pt(c, u, v, w, -h, h, -hd), pt(c, u, v, w, h, h, -hd), pt(c, u, v, w, h, h, hd), pt(c, u, v, w, -h, h, hd), 1.2f, 0.06f, 0.06f, 0.07f);
				if (!on(glyph, row + 1, col)) quad(m, pt(c, u, v, w, -h, -h, -hd), pt(c, u, v, w, h, -h, -hd), pt(c, u, v, w, h, -h, hd), pt(c, u, v, w, -h, -h, hd), 0.5f, 0.06f, 0.06f, 0.07f);
				// Лицевая и задняя стороны: чёрная подложка и белая вставка
				for (int side = -1; side <= 1; side += 2) {
					double z = side * (hd + 0.0004);
					quad(m, pt(c, u, v, w, -h, -h, z), pt(c, u, v, w, h, -h, z), pt(c, u, v, w, h, h, z), pt(c, u, v, w, -h, h, z), 0.9f, 0.06f, 0.06f, 0.07f);
					double l = on(glyph, row, col - 1) ? -h : -h + margin;
					double r = on(glyph, row, col + 1) ? h : h - margin;
					double b = on(glyph, row + 1, col) ? -h : -h + margin;
					double tp = on(glyph, row - 1, col) ? h : h - margin;
					double zz = side * (hd + 0.0012);
					quad(m, pt(c, u, v, w, l, b, zz), pt(c, u, v, w, r, b, zz), pt(c, u, v, w, r, tp, zz), pt(c, u, v, w, l, tp, zz), 1.0f, 1.0f, 1.0f, 1.0f);
				}
			}
		}
	}

	/** Рисует белые цифры с чёрной окантовкой: 6 в правой руке и 7 в левой во время эмоции «Сикс сэвен». */
	private void renderDigits(WorldRenderContext context) {
		if (!isEnabled() || index != 0) return;
		MinecraftClient client = mc();
		PlayerEntity p = client.player;
		if (p == null || context.world() == null) return;
		if (client.options.getPerspective().isFirstPerson()) return;

		MatrixStack matrices = context.matrixStack();
		if (matrices == null) return;
		Matrix4f m = matrices.peek().getPositionMatrix();
		Vec3d cam = context.camera().getPos();
		float td = context.tickDelta();

		float elapsed = (System.nanoTime() - startNanos) / 1.0e9f;
		float t = elapsed * speed.getFloat();
		float dur = DURATIONS[0];
		float fade = Math.max(0.0f, Math.min(1.0f, Math.min(t / 0.35f, (dur - t) / 0.35f)));
		if (fade <= 0.01f) return;
		float scale = 0.55f + 0.45f * fade;

		Vec3d base = p.getLerpedPos(td).subtract(cam);
		double bodyYaw = Math.toRadians(MathHelper.lerpAngleDegrees(td, p.prevBodyYaw, p.bodyYaw));
		double rx = -Math.cos(bodyYaw);
		double rz = -Math.sin(bodyYaw);
		double fx = -Math.sin(bodyYaw);
		double fz = Math.cos(bodyYaw);
		double camYaw = Math.toRadians(context.camera().getYaw());
		double[] u = {-Math.cos(camYaw), 0.0, -Math.sin(camYaw)};
		double[] v = {0.0, 1.0, 0.0};
		double[] w = {-Math.sin(camYaw), 0.0, Math.cos(camYaw)};

		double shoulderY = p.getHeight() * 0.78;
		double armLen = 0.62;
		double px = 0.04 * scale;
		for (int side = 0; side < 2; side++) {
			boolean right = side == 0;
			float pitch = armPitch(right, t);
			double forward = -Math.sin(pitch) * armLen;
			double down = Math.cos(pitch) * armLen;
			double sideOff = right ? 0.32 : -0.32;
			double[] origin = {base.x + rx * sideOff + fx * (forward + 0.08), base.y + shoulderY - down + 0.3 * scale,
					base.z + rz * sideOff + fz * (forward + 0.08)};
			drawGlyph(m, right ? SIX : SEVEN, origin, u, v, w, px, px * 1.5, fade);
		}
		digits.flush();
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	/** Плавно ведёт часть модели от ванильного положения к нужному (f = сила эмоции 0..1). */
	private static void set(ModelPart part, float pitch, float yaw, float roll, float f) {
		part.pitch = lerp(part.pitch, pitch, f);
		part.yaw = lerp(part.yaw, yaw, f);
		part.roll = lerp(part.roll, roll, f);
	}

	/** Накладывает позу текущей эмоции на модель игрока. Вызывается из PlayerEntityModelMixin. */
	public void apply(PlayerEntityModel<?> m) {
		if (index < 0) return;
		// Позу могут вызвать два миксина подряд в одном кадре: второй раз пропускаем
		long nowN = System.nanoTime();
		if (nowN - lastApplyNanos < 300_000L) return;
		lastApplyNanos = nowN;

		float elapsed = (System.nanoTime() - startNanos) / 1.0e9f;
		float t = elapsed * speed.getFloat();
		float dur = DURATIONS[index];
		// Плавный вход и выход (0.35 с), чтобы поза не дёргалась
		float f = Math.max(0.0f, Math.min(1.0f, Math.min(t / 0.35f, (dur - t) / 0.35f)));
		f = f * f * (3.0f - 2.0f * f);
		float tau = (float) (Math.PI * 2.0);

		switch (index) {
			case 0 -> {
				// Сикс сэвен: руки вперёд, ладони вверх, попеременно вверх-вниз
				float w = t * tau * 1.7f;
				float a = (float) Math.sin(w);
				set(m.rightArm, -1.45f + 0.55f * a, 0.0f, 0.12f, f);
				set(m.leftArm, -1.45f - 0.55f * a, 0.0f, -0.12f, f);
				m.head.pitch += 0.07f * (float) Math.sin(w * 2.0f) * f;
			}
			case 1 -> {
				// Танец: руки вверх по очереди, покачивание корпуса и ног
				float w = t * tau * 0.9f;
				float s = (float) Math.sin(w);
				set(m.rightArm, -2.4f + 0.7f * s, 0.0f, 0.3f, f);
				set(m.leftArm, -2.4f - 0.7f * s, 0.0f, -0.3f, f);
				set(m.body, 0.0f, 0.35f * s, 0.0f, f);
				m.head.yaw += 0.25f * s * f;
				set(m.rightLeg, 0.45f * s, 0.0f, 0.05f, f);
				set(m.leftLeg, -0.45f * s, 0.0f, -0.05f, f);
			}
			case 2 -> {
				// Приветствие: правая рука поднята и машет
				float w = t * tau * 1.6f;
				set(m.rightArm, -2.75f, 0.0f, 0.35f * (float) Math.sin(w) + 0.2f, f);
			}
			case 3 -> {
				// Победа: обе руки вверх, лёгкое покачивание
				float w = t * tau * 1.4f;
				float s = (float) Math.sin(w);
				set(m.rightArm, -3.0f + 0.15f * s, 0.0f, 0.25f, f);
				set(m.leftArm, -3.0f - 0.15f * s, 0.0f, -0.25f, f);
				m.head.pitch -= 0.2f * f;
			}
			case 4 -> {
				// Хлопки: руки перед собой сходятся и расходятся
				float c = 0.2f + 0.4f * (float) Math.sin(t * tau * 2.2f);
				set(m.rightArm, -1.5f, -c, 0.0f, f);
				set(m.leftArm, -1.5f, c, 0.0f, f);
			}
			case 6 -> {
				// Поклон: корпус и голова наклоняются вперёд и возвращаются
				float b = (float) Math.sin(Math.PI * Math.max(0.0f, Math.min(1.0f, t / dur)));
				set(m.body, 0.7f * b, 0.0f, 0.0f, f);
				m.head.pitch += 0.55f * b * f;
				set(m.rightArm, 0.5f * b, 0.0f, 0.1f, f);
				set(m.leftArm, 0.5f * b, 0.0f, -0.1f, f);
			}
			case 7 -> {
				// Робот: резкие повороты и руки под прямым углом
				int k = (int) (t * 3.0f) % 4;
				float[] ra = {-1.57f, 0.0f, -1.57f, 0.0f};
				float[] la = {0.0f, -1.57f, 0.0f, -1.57f};
				set(m.rightArm, ra[k], 0.0f, 0.1f, f);
				set(m.leftArm, la[k], 0.0f, -0.1f, f);
				set(m.body, 0.0f, k % 2 == 0 ? 0.35f : -0.35f, 0.0f, f);
				m.head.yaw += (k < 2 ? 0.6f : -0.6f) * f;
			}
			case 8 -> {
				// Волна: руки в стороны волнами
				float w = t * tau * 1.3f;
				set(m.rightArm, 0.15f * (float) Math.sin(w), 0.0f, 1.5f + 0.4f * (float) Math.sin(w), f);
				set(m.leftArm, 0.15f * (float) Math.sin(w + 1.5f), 0.0f, -1.5f - 0.4f * (float) Math.sin(w + 1.0f), f);
				set(m.body, 0.0f, 0.2f * (float) Math.sin(w), 0.0f, f);
			}
			case 9 -> {
				// Звезда: руки и ноги раскрываются и закрываются
				float ph = 0.5f + 0.5f * (float) Math.sin(t * tau * 1.2f);
				set(m.rightArm, 0.0f, 0.0f, 0.2f + 2.7f * ph, f);
				set(m.leftArm, 0.0f, 0.0f, -0.2f - 2.7f * ph, f);
				set(m.rightLeg, 0.0f, 0.0f, 0.05f + 0.4f * ph, f);
				set(m.leftLeg, 0.0f, 0.0f, -0.05f - 0.4f * ph, f);
			}
			case 10 -> {
				// Бокс: удары по очереди
				float w = t * tau * 1.8f;
				float a = Math.max(0.0f, (float) Math.sin(w));
				float b = Math.max(0.0f, -(float) Math.sin(w));
				set(m.rightArm, -1.1f - 0.7f * a, -0.25f, 0.0f, f);
				set(m.leftArm, -1.1f - 0.7f * b, 0.25f, 0.0f, f);
				set(m.body, 0.1f, 0.3f * (b - a), 0.0f, f);
				m.head.yaw += 0.1f * (a - b) * f;
			}
			case 11 -> {
				// Сердце: руки над головой образуют сердечко
				float pulse = 0.08f * (float) Math.sin(t * tau * 2.0f);
				set(m.rightArm, -2.9f + pulse, 0.0f, -0.6f, f);
				set(m.leftArm, -2.9f + pulse, 0.0f, 0.6f, f);
				m.head.pitch += 0.1f * f;
			}
			case 12 -> {
				// Дирижёр: правая рука рисует круги, левая на поясе
				float w = t * tau * 1.1f;
				set(m.rightArm, -1.8f + 0.5f * (float) Math.sin(w), 0.6f * (float) Math.cos(w), 0.25f, f);
				set(m.leftArm, -0.5f, 0.6f, -0.2f, f);
				m.head.yaw += 0.15f * (float) Math.sin(w) * f;
			}
			case 13 -> {
				// Смех: держится за живот, трясётся
				float sh = (float) Math.sin(t * tau * 5.0f);
				set(m.rightArm, -0.9f, -0.55f, 0.0f, f);
				set(m.leftArm, -0.9f, 0.55f, 0.0f, f);
				set(m.body, 0.15f + 0.06f * sh, 0.0f, 0.0f, f);
				m.head.pitch += (-0.5f + 0.12f * sh) * f;
			}
			case 14 -> {
				// Пожать плечами: руки в стороны ладонями вверх, голова вниз
				float up = (float) Math.pow(Math.sin(Math.PI * Math.max(0.0f, Math.min(1.0f, t / dur * 2.0f % 1.0f))), 2.0);
				set(m.rightArm, -0.5f, 0.0f, 0.7f + 0.3f * up, f);
				set(m.leftArm, -0.5f, 0.0f, -0.7f - 0.3f * up, f);
				m.head.pitch += 0.12f * up * f;
			}
			case 15 -> {
				// Мунвок: ноги скользят, корпус отклонён назад
				float w = t * tau * 0.9f;
				float s = (float) Math.sin(w);
				set(m.rightLeg, 0.45f * s, 0.0f, 0.03f, f);
				set(m.leftLeg, -0.45f * s, 0.0f, -0.03f, f);
				set(m.body, -0.15f, 0.0f, 0.0f, f);
				set(m.rightArm, -1.3f, 0.0f, 0.2f, f);
				set(m.leftArm, 0.5f * s, 0.0f, -0.1f, f);
			}
			case 16 -> {
				// Салют: рука у виска
				set(m.rightArm, -2.5f, -0.25f, 0.0f, f);
				set(m.leftArm, 0.0f, 0.0f, -0.05f, f);
				m.head.pitch -= 0.05f * f;
			}
			case 17 -> {
				// Твист: быстрые повороты корпуса и ног в разные стороны
				float s = (float) Math.sin(t * tau * 1.6f);
				set(m.body, 0.0f, 0.6f * s, 0.0f, f);
				set(m.rightLeg, 0.0f, -0.5f * s, 0.0f, f);
				set(m.leftLeg, 0.0f, -0.5f * s, 0.0f, f);
				set(m.rightArm, -1.2f, 0.0f, 0.3f * s + 0.2f, f);
				set(m.leftArm, -1.2f, 0.0f, 0.3f * s - 0.2f, f);
			}
			case 18 -> {
				// Флосс: руки машут поперёк корпуса, корпус в обратную сторону
				float s = (float) Math.sin(t * tau * 1.8f);
				set(m.rightArm, 0.2f, 0.0f, -0.5f + 1.0f * s, f);
				set(m.leftArm, 0.2f, 0.0f, -0.5f + 1.0f * s, f);
				set(m.body, 0.0f, -0.4f * s, 0.0f, f);
				set(m.rightLeg, 0.0f, 0.0f, 0.2f * s, f);
				set(m.leftLeg, 0.0f, 0.0f, 0.2f * s, f);
			}
			default -> {
				// Раздумье: рука у подбородка, голова чуть наклонена
				set(m.rightArm, -2.05f, -0.55f, 0.0f, f);
				m.head.pitch += 0.15f * f;
				m.head.yaw += 0.25f * (float) Math.sin(t * 1.2f) * f;
			}
		}

		// Одежда и шляпа повторяют основные части
		m.leftSleeve.copyTransform(m.leftArm);
		m.rightSleeve.copyTransform(m.rightArm);
		m.leftPants.copyTransform(m.leftLeg);
		m.rightPants.copyTransform(m.rightLeg);
		m.jacket.copyTransform(m.body);
		m.hat.copyTransform(m.head);
	}
}
