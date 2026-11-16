package com.flyvisual.gui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerWarningScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import com.flyvisual.module.impl.MenuStyleModule;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Новое главное меню: приветствие и три круглые стеклянные кнопки (настройки, одиночная игра, сетевая игра)
 * плюс маленькая кнопка выхода. Кнопка под курсором (или выбранная стрелками) увеличивается, под ней появляется подпись.
 * Время и день недели в углу. Фон берётся из настройки «Main Menu» (стиль «Луч и частицы» или любой другой).
 */
public class FlyTitleScreen extends Screen {
	/** Название клиента в приветствии. */
	private static final String NAME = "FlyVisual";
	private static final Style UI = Style.EMPTY.withFont(new Identifier("flyvisual", "ui"));

	private static final String[] LABELS = {"Настройки", "Одиночная игра", "Сетевая игра", "Выход"};
	// Положение кнопок в «виртуальных» единицах: ноль по центру экрана, весь блок масштабируется по размеру окна
	private static final float[] BX = {-66.0f, 0.0f, 66.0f, 0.0f};
	private static final float[] BY = {30.0f, 30.0f, 30.0f, 104.0f};
	private static final float[] BASE_R = {20.0f, 20.0f, 20.0f, 11.0f};
	private static final float[] GROW = {7.0f, 7.0f, 7.0f, 3.0f};

	private static final int VIOLET = 0xA67CFF;

	private final float[] hover = new float[4];
	private int kbFocus = 1;
	private int hoveredIdx = -1;
	private final long start = System.nanoTime();
	private long lastNanos = start;
	private float time;
	/** Время для плавного появления меню (после интро отсчитывается от вспышки). */
	private float menuT;

	// Интро: показывается один раз за запуск игры
	private static boolean introPlayed;
	private static final float INTRO_FLASH = 3.6f;
	private static final float INTRO_END = 4.7f;
	private static final float INTRO_SKIP_TO = 3.3f;
	private boolean intro;
	private long introStart = -1L;
	private boolean introChime;

	public FlyTitleScreen() {
		super(Text.literal(NAME));
		this.intro = !introPlayed && MenuStyleModule.introEnabled();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	// ------------------------------------------------------------------ геометрия

	private float uiScale() {
		return MathHelper.clamp(Math.min(width / 400.0f, height / 230.0f), 0.6f, 1.7f);
	}

	private float originY(float s) {
		return height / 2.0f - 44.0f * s;
	}

	private float radius(int i) {
		return BASE_R[i] + GROW[i] * hover[i];
	}

	private int pick(float vx, float vy) {
		int best = -1;
		float bestD = Float.MAX_VALUE;
		for (int i = 0; i < 4; i++) {
			float dx = vx - BX[i];
			float dy = vy - BY[i];
			float d = (float) Math.sqrt(dx * dx + dy * dy);
			if (d <= radius(i) + 3.0f && d < bestD) {
				best = i;
				bestD = d;
			}
		}
		return best;
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0f, 1.0f);
		float inv = 1.0f - x;
		return 1.0f - inv * inv * inv;
	}

	private float appear(float delay) {
		return ease((menuT - delay) / 0.55f);
	}

	// ------------------------------------------------------------------ отрисовка

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastNanos) / 1.0e9f, 0.05f);
		lastNanos = now;
		time = (now - start) / 1.0e9f;

		float ti = 0.0f;
		if (intro) {
			// Пока идёт загрузка игры (красный экран Mojang), интро ждёт
			if (MinecraftClient.getInstance().getOverlay() != null) {
				ctx.fill(0, 0, width, height, 0xFF000000);
				return;
			}
			if (introStart < 0L) introStart = now;
			ti = (now - introStart) / 1.0e9f;
			if (ti >= INTRO_END) {
				intro = false;
				introPlayed = true;
			}
		}
		menuT = introStart >= 0L ? (now - introStart) / 1.0e9f - INTRO_FLASH : time;

		// Фон выбранного стиля (пока идёт начало интро, он закрыт чёрным, поэтому не рисуем)
		if (!intro || ti > INTRO_FLASH - 0.5f) {
			if (!TitleStyles.draw(ctx, width, height, mouseX, mouseY)) {
				LiveBackground.shared().render(ctx, width, height, mouseX, mouseY, true);
			}
		} else {
			ctx.fill(0, 0, width, height, 0xFF000000);
		}

		float s = uiScale();
		float ox = width / 2.0f;
		float oy = originY(s);
		hoveredIdx = pick((mouseX - ox) / s, (mouseY - oy) / s);
		int focus = hoveredIdx >= 0 ? hoveredIdx : kbFocus;
		for (int i = 0; i < 4; i++) {
			hover[i] += ((i == focus ? 1.0f : 0.0f) - hover[i]) * Math.min(1.0f, dt * 12.0f);
		}

		MatrixStack m = ctx.getMatrices();
		m.push();
		m.translate(ox, oy, 0.0f);
		m.scale(s, s, 1.0f);
		drawHeader(ctx);
		for (int i = 0; i < 4; i++) {
			drawButton(ctx, i);
		}
		m.pop();

		drawClock(ctx);
		drawFooter(ctx);

		if (intro) drawIntro(ctx, ti);
	}

	private void drawHeader(DrawContext ctx) {
		float a = appear(0.10f);
		int alpha = (int) (255.0f * a);
		if (alpha < 8) return;
		float rise = (1.0f - a) * 8.0f;
		MatrixStack m = ctx.getMatrices();

		Text welcome = Text.literal("Добро пожаловать в ").setStyle(UI)
				.append(Text.literal(NAME).setStyle(UI.withColor(0xC9B6FF)));
		int tw = textRenderer.getWidth(welcome);
		m.push();
		m.translate(0.0f, -42.0f + rise, 0.0f);
		m.scale(1.45f, 1.45f, 1.0f);
		ctx.drawText(textRenderer, welcome, -tw / 2, 0, (alpha << 24) | 0xFFFFFF, false);
		m.pop();

		int subAlpha = (int) (alpha * 0.62f);
		if (subAlpha >= 8) {
			Text sub = Text.literal("Во что сегодня играем?").setStyle(UI);
			int sw = textRenderer.getWidth(sub);
			m.push();
			m.translate(0.0f, -20.0f + rise, 0.0f);
			m.scale(0.9f, 0.9f, 1.0f);
			ctx.drawText(textRenderer, sub, -sw / 2, 0, (subAlpha << 24) | 0xCFC6EE, false);
			m.pop();
		}
	}

	private void drawButton(DrawContext ctx, int i) {
		float a = appear(0.25f + i * 0.08f);
		if (a <= 0.0f) return;
		float f = hover[i];
		float r = radius(i) * (0.85f + 0.15f * a);
		float cx = BX[i];
		float cy = BY[i] + (1.0f - a) * 10.0f;

		// Мягкое свечение вокруг выбранной кнопки
		for (int k = 3; k >= 1; k--) {
			int ga = (int) (f * a * (8 + (3 - k) * 7));
			if (ga > 0) disc(ctx, cx, cy, r + k * 3.5f, (ga << 24) | VIOLET);
		}
		// Тело и обводка
		disc(ctx, cx, cy, r, ((int) ((34.0f + 86.0f * f) * a) << 24) | 0x9C86D8);
		ring(ctx, cx, cy, r, 0.7f + 0.5f * f, ((int) ((40.0f + 90.0f * f) * a) << 24) | 0xE2D8FF);

		// Значок
		int iconColor = ((int) ((170.0f + 85.0f * f) * a) << 24) | 0xEFE8FF;
		float u = r / 24.0f;
		switch (i) {
			case 0 -> drawGear(ctx, cx, cy, u, iconColor);
			case 1 -> drawPerson(ctx, cx, cy, u, 1.0f, iconColor);
			case 2 -> {
				int side = (((iconColor >>> 24) * 62 / 100) << 24) | (iconColor & 0xFFFFFF);
				drawPerson(ctx, cx - 11.0f * u, cy + 3.0f * u, u, 0.62f, side);
				drawPerson(ctx, cx + 11.0f * u, cy + 3.0f * u, u, 0.62f, side);
				drawPerson(ctx, cx, cy + 1.0f * u, u, 0.8f, iconColor);
			}
			default -> drawCross(ctx, cx, cy, u, iconColor);
		}

		// Подпись под кнопкой
		int la = (int) (255.0f * f * a * 0.92f);
		if (la >= 8) {
			Text label = Text.literal(LABELS[i]).setStyle(UI);
			int lw = textRenderer.getWidth(label);
			MatrixStack m = ctx.getMatrices();
			m.push();
			m.translate(cx, cy + r + 7.0f, 0.0f);
			m.scale(0.9f, 0.9f, 1.0f);
			ctx.drawText(textRenderer, label, -lw / 2, 0, (la << 24) | 0xEFE8FF, false);
			m.pop();
		}
	}

	private void drawClock(DrawContext ctx) {
		float a = appear(0.6f);
		int alpha = (int) (190.0f * a);
		if (alpha < 8) return;
		LocalDateTime now = LocalDateTime.now();
		String day = now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("ru"));
		if (!day.isEmpty()) day = Character.toUpperCase(day.charAt(0)) + day.substring(1);
		String s = String.format(Locale.ROOT, "%02d:%02d, %s", now.getHour(), now.getMinute(), day);
		Text t = Text.literal(s).setStyle(UI);
		ctx.drawText(textRenderer, t, width - 10 - textRenderer.getWidth(t), 10, (alpha << 24) | 0xEFE8FF, false);
	}

	private void drawFooter(DrawContext ctx) {
		float a = appear(0.7f);
		int alpha = (int) (110.0f * a);
		if (alpha < 8) return;
		String version = FabricLoader.getInstance().getModContainer("flyvisual")
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
		String s = NAME + (version.isEmpty() ? "" : " " + version) + "  ·  Minecraft " + MinecraftClient.getInstance().getGameVersion();
		ctx.drawText(textRenderer, Text.literal(s).setStyle(UI), 10, height - 16, (alpha << 24) | 0xFFFFFF, false);
	}

	// ------------------------------------------------------------------ рисование фигур (в 2 раза чётче обычного)

	/** Диск или его верхняя половина, рисуется построчно в удвоенном разрешении. */
	private static void ellipse(DrawContext ctx, float cx, float cy, float rx, float ry, boolean topOnly, int argb) {
		ellipseRes(ctx, cx, cy, rx, ry, topOnly, argb, 2);
	}

	/** То же, но с выбором чёткости: 1 = обычная (быстрее, для мягкого свечения), 2 = чёткая. */
	private static void ellipseRes(DrawContext ctx, float cx, float cy, float rx, float ry, boolean topOnly, int argb, int res) {
		if ((argb >>> 24) == 0 || rx <= 0.0f || ry <= 0.0f) return;
		float x = cx * res;
		float y = cy * res;
		float rx2 = rx * res;
		float ry2 = ry * res;
		int rows = (int) Math.ceil(ry2);
		int yBase = Math.round(y);
		MatrixStack m = ctx.getMatrices();
		m.push();
		m.scale(1.0f / res, 1.0f / res, 1.0f);
		for (int i = -rows; i < (topOnly ? 0 : rows); i++) {
			float dy = i + 0.5f;
			float t = 1.0f - (dy * dy) / (ry2 * ry2);
			if (t <= 0.0f) continue;
			float hx = rx2 * (float) Math.sqrt(t);
			ctx.fill(Math.round(x - hx), yBase + i, Math.round(x + hx), yBase + i + 1, argb);
		}
		m.pop();
	}

	private static void disc(DrawContext ctx, float cx, float cy, float r, int argb) {
		ellipse(ctx, cx, cy, r, r, false, argb);
	}

	/** Кольцо: внешний радиус r, толщина thick. */
	private static void ring(DrawContext ctx, float cx, float cy, float r, float thick, int argb) {
		if ((argb >>> 24) == 0 || r <= 0.0f) return;
		float x = cx * 2.0f;
		float y = cy * 2.0f;
		float ro = r * 2.0f;
		float ri = Math.max(0.0f, ro - thick * 2.0f);
		int rows = (int) Math.ceil(ro);
		int yBase = Math.round(y);
		MatrixStack m = ctx.getMatrices();
		m.push();
		m.scale(0.5f, 0.5f, 1.0f);
		for (int i = -rows; i < rows; i++) {
			float dy = i + 0.5f;
			float outer = ro * ro - dy * dy;
			if (outer <= 0.0f) continue;
			float ho = (float) Math.sqrt(outer);
			float inner = ri * ri - dy * dy;
			if (inner <= 0.0f) {
				ctx.fill(Math.round(x - ho), yBase + i, Math.round(x + ho), yBase + i + 1, argb);
			} else {
				float hi = (float) Math.sqrt(inner);
				ctx.fill(Math.round(x - ho), yBase + i, Math.round(x - hi), yBase + i + 1, argb);
				ctx.fill(Math.round(x + hi), yBase + i, Math.round(x + ho), yBase + i + 1, argb);
			}
		}
		m.pop();
	}

	/** Прямоугольник с дробными координатами (в учётверённом разрешении), работает и под поворотом. */
	private static void rectF(DrawContext ctx, float x1, float y1, float x2, float y2, int argb) {
		MatrixStack m = ctx.getMatrices();
		m.push();
		m.scale(0.25f, 0.25f, 1.0f);
		ctx.fill(Math.round(x1 * 4.0f), Math.round(y1 * 4.0f), Math.round(x2 * 4.0f), Math.round(y2 * 4.0f), argb);
		m.pop();
	}

	// ------------------------------------------------------------------ значки

	private static void drawGear(DrawContext ctx, float cx, float cy, float u, int color) {
		ring(ctx, cx, cy, 8.2f * u, 4.6f * u, color);
		MatrixStack m = ctx.getMatrices();
		for (int k = 0; k < 8; k++) {
			m.push();
			m.translate(cx, cy, 0.0f);
			m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(k * 45.0f));
			rectF(ctx, -2.0f * u, -11.6f * u, 2.0f * u, -7.0f * u, color);
			m.pop();
		}
	}

	/** Силуэт человека: голова и плечи. sp: размер относительно обычного. */
	private static void drawPerson(DrawContext ctx, float cx, float cy, float u, float sp, int color) {
		disc(ctx, cx, cy - 7.0f * u * sp, 5.5f * u * sp, color);
		ellipse(ctx, cx, cy + 14.0f * u * sp, 11.0f * u * sp, 10.0f * u * sp, true, color);
	}

	private static void drawCross(DrawContext ctx, float cx, float cy, float u, int color) {
		MatrixStack m = ctx.getMatrices();
		for (int k = 0; k < 2; k++) {
			m.push();
			m.translate(cx, cy, 0.0f);
			m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(k == 0 ? 45.0f : -45.0f));
			rectF(ctx, -1.4f * u, -7.5f * u, 1.4f * u, 7.5f * u, color);
			m.pop();
		}
	}


	// ------------------------------------------------------------------ интро

	private void skipIntro() {
		if (!intro || introStart < 0L) return;
		float ti = (System.nanoTime() - introStart) / 1.0e9f;
		if (ti < INTRO_SKIP_TO) {
			introStart -= (long) ((INTRO_SKIP_TO - ti) * 1.0e9f);
		}
	}

	private static float clamp01(float x) {
		return x < 0.0f ? 0.0f : (x > 1.0f ? 1.0f : x);
	}

	private static float smooth(float e0, float e1, float x) {
		float t = clamp01((x - e0) / (e1 - e0));
		return t * t * (3.0f - 2.0f * t);
	}

	private static float rnd(int i, int k) {
		float v = (float) Math.sin(i * 12.9898 + k * 78.233) * 43758.5453f;
		return v - (float) Math.floor(v);
	}

	/** Ромб (контур или заливка): для логотипа. */
	private static void diamond(DrawContext ctx, float cx, float cy, float hw, float hh, float thick, boolean fill, int argb) {
		if ((argb >>> 24) == 0 || hw <= 0.0f || hh <= 0.0f) return;
		float x = cx * 2.0f;
		float y = cy * 2.0f;
		float w2 = hw * 2.0f;
		float h2 = hh * 2.0f;
		float t2 = thick * 2.0f;
		int rows = (int) Math.ceil(h2);
		int yBase = Math.round(y);
		MatrixStack m = ctx.getMatrices();
		m.push();
		m.scale(0.5f, 0.5f, 1.0f);
		for (int i = -rows; i < rows; i++) {
			float dy = Math.abs(i + 0.5f);
			float ho = w2 * (1.0f - dy / h2);
			if (ho <= 0.0f) continue;
			if (fill || ho <= t2) {
				ctx.fill(Math.round(x - ho), yBase + i, Math.round(x + ho), yBase + i + 1, argb);
			} else {
				ctx.fill(Math.round(x - ho), yBase + i, Math.round(x - ho + t2), yBase + i + 1, argb);
				ctx.fill(Math.round(x + ho - t2), yBase + i, Math.round(x + ho), yBase + i + 1, argb);
			}
		}
		m.pop();
	}

	/**
	 * Интро: частицы летят к центру и собираются в светящийся шар, на нём тянется полоса пыли,
	 * шар превращается в логотип, потом вспышка, и проявляются фон с меню. Всё считается по реальному времени.
	 */
	private void drawIntro(DrawContext ctx, float ti) {
		float s = uiScale();
		float cx = width / 2.0f;
		float cy = height / 2.0f;

		// Чёрная завеса: прячет фон и меню, пока идёт интро, потом тает
		float veil = ti < INTRO_FLASH ? 1.0f : 1.0f - smooth(0.0f, 1.0f, (ti - INTRO_FLASH) / 0.9f);
		int va = (int) (255.0f * veil);
		if (va > 0) ctx.fill(0, 0, width, height, va << 24);

		// 1. Частицы летят к центру по спирали
		float fadeP = 1.0f - smooth(1.55f, 2.1f, ti);
		if (fadeP > 0.01f) {
			float rmax = Math.max(width, height) * 0.62f;
			float gather = ti / 1.7f;
			for (int i = 0; i < 240; i++) {
				float r0 = rmax * (0.25f + 0.75f * rnd(i, 1));
				float a0 = rnd(i, 2) * 6.2831855f;
				float p = clamp01((gather - rnd(i, 3) * 0.30f) / 0.70f);
				p = p < 0.5f ? 4.0f * p * p * p : 1.0f - (float) Math.pow(-2.0f * p + 2.0f, 3.0) / 2.0f;
				float rt = rnd(i, 4) * 6.0f * s;
				float r = r0 + (rt - r0) * p;
				float ang = a0 + p * 2.2f;
				int x = Math.round(cx + (float) Math.cos(ang) * r);
				int y = Math.round(cy + (float) Math.sin(ang) * r);
				int a = (int) ((0.2f + 0.8f * p) * fadeP * 255.0f);
				if (a <= 0) continue;
				int size = rnd(i, 5) > 0.9f ? 2 : 1;
				ctx.fill(x, y, x + size, y + size, (a << 24) | (rnd(i, 6) > 0.5f ? 0xFFFFFF : 0xC9B3FF));
			}
		}

		// 2. Светящийся шар
		float coreIn = smooth(0.0f, 1.7f, ti);
		float shrink = 1.0f - smooth(2.5f, 3.2f, ti);
		float inten = coreIn * shrink;
		if (inten > 0.01f) {
			float pulse = ti > 1.7f ? 0.5f + 0.5f * (float) Math.sin(ti * 7.0f) : 0.0f;
			float rc = (4.0f + 10.0f * coreIn + 2.5f * pulse) * s * shrink;
			for (int k = 5; k >= 1; k--) {
				int a = (int) (inten * (10 + (5 - k) * 5));
				ellipseRes(ctx, cx, cy, rc * (1.0f + k * 0.7f), rc * (1.0f + k * 0.7f), false, (a << 24) | 0x9B6BFF, 1);
			}
			ellipseRes(ctx, cx, cy, rc * 1.25f, rc * 1.25f, false, ((int) (inten * 120.0f) << 24) | 0xC9B3FF, 1);
			ellipseRes(ctx, cx, cy, rc * 0.8f, rc * 0.8f, false, ((int) (inten * 255.0f) << 24) | 0xFFFFFF, 2);
		}

		// 3. Полоса пыли через центр
		float band = smooth(1.9f, 2.8f, ti) * (1.0f - smooth(3.3f, 3.6f, ti));
		if (band > 0.01f) {
			float half = width * 0.36f * band;
			for (int k = 0; k < 3; k++) {
				int a = (int) (band * (14 - k * 4));
				ctx.fill(Math.round(cx - half), Math.round(cy) - 1 - k, Math.round(cx + half), Math.round(cy) + 1 + k, (a << 24) | 0xB89CFF);
			}
			for (int i = 0; i < 150; i++) {
				float u = rnd(i, 7) * 2.0f - 1.0f;
				float x = cx + u * half + (float) Math.sin(ti * 0.8f + i) * 3.0f;
				float y = cy + (rnd(i, 8) - 0.5f) * (rnd(i, 9) - 0.5f) * 44.0f * s;
				float tw = 0.5f + 0.5f * (float) Math.sin(ti * (2.0f + rnd(i, 10) * 3.0f) + i);
				int a = (int) (band * (40.0f + 190.0f * tw) * (1.0f - Math.abs(u) * 0.6f));
				if (a <= 0) continue;
				int xi = Math.round(x);
				int yi = Math.round(y);
				ctx.fill(xi, yi, xi + (rnd(i, 11) > 0.8f ? 2 : 1), yi + 1, (a << 24) | 0xD8C8FF);
			}
		}

		// 4. Логотип с неоновым контуром
		float la = smooth(2.3f, 3.0f, ti) * (1.0f - smooth(3.5f, 3.75f, ti));
		if (la > 0.01f) {
			float sz = 30.0f * s * (0.8f + 0.2f * smooth(2.3f, 3.2f, ti));
			float hw = sz * 0.62f;
			for (int k = 4; k >= 1; k--) {
				diamond(ctx, cx, cy, hw + k * 1.6f * s, sz + k * 1.6f * s, 1.6f * s + k * 1.4f * s, false,
						((int) (la * 20.0f) << 24) | 0x9B6BFF);
			}
			diamond(ctx, cx, cy, hw, sz, 2.4f * s, false, ((int) (la * 255.0f) << 24) | 0xEFE8FF);
			diamond(ctx, cx, cy, hw * 0.30f, sz * 0.30f, 0.0f, true, ((int) (la * 255.0f) << 24) | 0xFFFFFF);
			diamond(ctx, cx, cy, hw * 0.52f, sz * 0.52f, 1.0f * s, false, ((int) (la * 110.0f) << 24) | 0xC9B3FF);
		}

		// 5. Вспышка
		float fl = Math.max(0.0f, 1.0f - Math.abs(ti - (INTRO_FLASH - 0.02f)) / 0.32f);
		if (fl > 0.0f) {
			ctx.fill(0, 0, width, height, ((int) (fl * 235.0f) << 24) | 0xEDE6FF);
			if (!introChime) {
				introChime = true;
				try {
					MinecraftClient.getInstance().getSoundManager()
							.play(PositionedSoundInstance.master(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f));
				} catch (Throwable ignored) {
				}
			}
		}

		// Подсказка про пропуск
		if (ti > 0.8f && ti < INTRO_SKIP_TO) {
			int ha = (int) (110.0f * smooth(0.8f, 1.4f, ti));
			if (ha >= 8) {
				Text hint = Text.literal("Нажми любую клавишу, чтобы пропустить").setStyle(UI);
				ctx.drawText(textRenderer, hint, (width - textRenderer.getWidth(hint)) / 2, height - 22, (ha << 24) | 0xFFFFFF, false);
			}
		}
	}

	// ------------------------------------------------------------------ управление

	private void activate(int i) {
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
		switch (i) {
			case 0 -> mc.setScreen(new OptionsScreen(this, mc.options));
			case 1 -> mc.setScreen(new SelectWorldScreen(this));
			case 2 -> {
				Screen next = mc.options.skipMultiplayerWarning ? new MultiplayerScreen(this) : new MultiplayerWarningScreen(this);
				mc.setScreen(next);
			}
			default -> mc.scheduleStop();
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (intro) {
			skipIntro();
			return true;
		}
		if (button == 0 && hoveredIdx >= 0) {
			activate(hoveredIdx);
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (intro) {
			skipIntro();
			return true;
		}
		switch (keyCode) {
			case GLFW.GLFW_KEY_LEFT -> {
				if (kbFocus < 3) kbFocus = Math.max(0, kbFocus - 1);
				return true;
			}
			case GLFW.GLFW_KEY_RIGHT -> {
				if (kbFocus < 3) kbFocus = Math.min(2, kbFocus + 1);
				return true;
			}
			case GLFW.GLFW_KEY_DOWN -> {
				kbFocus = 3;
				return true;
			}
			case GLFW.GLFW_KEY_UP -> {
				if (kbFocus == 3) kbFocus = 1;
				return true;
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
				activate(hoveredIdx >= 0 ? hoveredIdx : kbFocus);
				return true;
			}
			default -> {
				return super.keyPressed(keyCode, scanCode, modifiers);
			}
		}
	}
}
