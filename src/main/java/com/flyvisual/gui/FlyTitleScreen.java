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

	public FlyTitleScreen() {
		super(Text.literal(NAME));
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
		return ease((time - delay) / 0.55f);
	}

	// ------------------------------------------------------------------ отрисовка

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastNanos) / 1.0e9f, 0.05f);
		lastNanos = now;
		time = (now - start) / 1.0e9f;

		// Фон выбранного стиля
		if (!TitleStyles.draw(ctx, width, height, mouseX, mouseY)) {
			LiveBackground.shared().render(ctx, width, height, mouseX, mouseY, true);
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
		if ((argb >>> 24) == 0 || rx <= 0.0f || ry <= 0.0f) return;
		float x = cx * 2.0f;
		float y = cy * 2.0f;
		float rx2 = rx * 2.0f;
		float ry2 = ry * 2.0f;
		int rows = (int) Math.ceil(ry2);
		int yBase = Math.round(y);
		MatrixStack m = ctx.getMatrices();
		m.push();
		m.scale(0.5f, 0.5f, 1.0f);
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
		if (button == 0 && hoveredIdx >= 0) {
			activate(hoveredIdx);
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
