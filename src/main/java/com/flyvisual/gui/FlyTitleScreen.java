package com.flyvisual.gui;

import com.flyvisual.module.impl.MenuStyleModule;
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
import org.lwjgl.glfw.GLFW;

import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

import static com.flyvisual.gui.QuadBatch.TWO_PI;
import static com.flyvisual.gui.QuadBatch.col;
import static com.flyvisual.gui.QuadBatch.fade;
import static com.flyvisual.gui.QuadBatch.lerp;

/**
 * Главное меню: приветствие и три стеклянные круглые кнопки (настройки, одиночная игра, сетевая игра),
 * маленькая кнопка выхода, имя игрока, время и дата, кнопка повтора анимации. Фон берётся из настройки «Main Menu».
 * <p>
 * Вся графика рисуется пакетным рендером (QuadBatch): несколько вызовов на кадр вместо тысяч, поэтому FPS не падает.
 * Анимации считаются по реальному времени. Выбранная кнопка не «прыгает» назад, когда курсор между кнопками.
 */
public class FlyTitleScreen extends Screen {
	/** Название клиента в приветствии. */
	private static final String NAME = "FlyVisual";
	private static final Style UI = Style.EMPTY.withFont(new Identifier("flyvisual", "ui"));
	private static final Locale RU = Locale.forLanguageTag("ru");

	private static final float PI = 3.1415927f;
	private static final float HALF_PI = 1.5707964f;
	private static final int VIOLET = 0x9B6BFF;
	private static final int LAV = 0xEFE8FF;

	// Кнопки: 0 настройки, 1 одиночная, 2 сетевая, 3 выход (в «виртуальных» единицах, центр в середине экрана),
	// 4 повтор анимации (у нижнего угла экрана)
	private static final int COUNT = 5;
	private static final float[] BX = {-72.0f, 0.0f, 72.0f, 0.0f};
	private static final float[] BY = {32.0f, 32.0f, 32.0f, 108.0f};
	private static final float[] BASE_R = {21.0f, 21.0f, 21.0f, 11.0f, 11.0f};
	private static final float[] GROW = {7.0f, 7.0f, 7.0f, 3.0f, 3.0f};
	private static final String[] LABELS = {"Настройки", "Одиночная игра", "Сетевая игра", "Выход", "Повторить анимацию"};

	// Интро: показывается один раз за запуск игры, потом по кнопке
	private static boolean introPlayed;
	private static final float INTRO_FLASH = 3.3f;
	private static final float INTRO_END = 4.6f;
	private static final float INTRO_SKIP_TO = 3.0f;

	private final QuadBatch fx = new QuadBatch();
	private final float[] hover = new float[COUNT];
	private final float[] focusAnim = {0.0f, 1.0f, 0.0f};
	private final float[] ripple = {-1.0f, -1.0f, -1.0f, -1.0f, -1.0f};
	private int focusMain = 1;
	private int hoveredIdx = -1;

	private final long start = System.nanoTime();
	private long lastNanos = start;
	private float time;
	private float menuT;
	private boolean intro;
	private long introStart = -1L;
	private boolean introChime;

	// Тексты создаём один раз, а не каждый кадр
	private final Text welcome = Text.literal("Добро пожаловать в ").setStyle(UI)
			.append(Text.literal(NAME).setStyle(UI.withColor(0xC9B6FF)));
	private final Text subtitle = Text.literal("Во что сегодня играем?").setStyle(UI);
	private final Text[] labels = new Text[COUNT];
	private final String userName;
	private final Text nameText;
	private final Text initialText;
	private Text clockTime = Text.empty();
	private Text clockDate = Text.empty();
	private int clockMinute = -1;
	private Text footer;
	private final Text skipHint = Text.literal("Нажми любую клавишу, чтобы пропустить").setStyle(UI);

	public FlyTitleScreen() {
		super(Text.literal(NAME));
		this.intro = !introPlayed && MenuStyleModule.introEnabled();
		for (int i = 0; i < COUNT; i++) {
			labels[i] = Text.literal(LABELS[i]).setStyle(UI);
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		String name = mc.getSession() != null ? mc.getSession().getUsername() : "";
		this.userName = (name == null || name.isEmpty()) ? "Player" : name;
		this.nameText = Text.literal(userName).setStyle(UI);
		this.initialText = Text.literal(userName.substring(0, 1).toUpperCase(Locale.ROOT)).setStyle(UI);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	// ------------------------------------------------------------------ геометрия и состояние

	private float uiScale() {
		return MathHelper.clamp(Math.min(width / 400.0f, height / 230.0f), 0.6f, 1.7f);
	}

	private float originY(float s) {
		return height / 2.0f - 46.0f * s;
	}

	private float cornerX() {
		return width - 26.0f;
	}

	private float cornerY() {
		return height - 26.0f;
	}

	private static float ease(float x) {
		x = clamp01(x);
		float inv = 1.0f - x;
		return 1.0f - inv * inv * inv;
	}

	private static float easeInOut(float p) {
		return p < 0.5f ? 4.0f * p * p * p : 1.0f - (float) Math.pow(-2.0f * p + 2.0f, 3.0) / 2.0f;
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

	private float appear(float delay) {
		return ease((menuT - delay) / 0.55f);
	}

	/** Текущий радиус кнопки. */
	private float radius(int i) {
		if (i < 3) return BASE_R[i] + GROW[i] * focusAnim[i] + 1.2f * hover[i];
		return BASE_R[i] + GROW[i] * hover[i];
	}

	/**
	 * Что под курсором. У трёх главных кнопок зоны без пустот: между ними граница посередине,
	 * поэтому курсор всегда над какой-то из них и выбор не дёргается.
	 */
	private int pick(float mouseX, float mouseY, float s, float ox, float oy) {
		if (!intro) {
			float dx = mouseX - cornerX();
			float dy = mouseY - cornerY();
			if (dx * dx + dy * dy <= 15.0f * 15.0f) return 4;
		}
		float vx = (mouseX - ox) / s;
		float vy = (mouseY - oy) / s;
		float qx = vx - BX[3];
		float qy = vy - BY[3];
		if (qx * qx + qy * qy <= 17.0f * 17.0f) return 3;
		if (vy > BY[0] - 40.0f && vy < BY[0] + 58.0f && Math.abs(vx) < 112.0f) {
			return vx < -36.0f ? 0 : (vx < 36.0f ? 1 : 2);
		}
		return -1;
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

		// Фон (в начале интро он закрыт чёрным, поэтому не рисуем)
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

		hoveredIdx = intro ? -1 : pick(mouseX, mouseY, s, ox, oy);
		if (hoveredIdx >= 0 && hoveredIdx < 3) focusMain = hoveredIdx;

		// Плавные состояния (не зависят от FPS)
		float k = 1.0f - (float) Math.exp(-dt * 14.0f);
		for (int i = 0; i < 3; i++) {
			focusAnim[i] += ((i == focusMain ? 1.0f : 0.0f) - focusAnim[i]) * k;
		}
		for (int i = 0; i < COUNT; i++) {
			hover[i] += ((i == hoveredIdx ? 1.0f : 0.0f) - hover[i]) * k;
			if (ripple[i] >= 0.0f) {
				ripple[i] += dt * 1.8f;
				if (ripple[i] > 1.0f) ripple[i] = -1.0f;
			}
		}

		MatrixStack m = ctx.getMatrices();
		drawShapes(m, s, ox, oy);
		drawTexts(ctx, s, ox, oy);
		if (intro) {
			drawIntroShapes(m, ti);
			drawIntroText(ctx, ti);
		}
	}

	// ------------------------------------------------------------------ фигуры меню

	private void drawShapes(MatrixStack m, float s, float ox, float oy) {
		fx.begin(m);
		try {
			for (int pass = 0; pass < 3; pass++) {
				// pass 0: свечение, pass 1: тела, значки и плашки, pass 2: блики и анимации
				fx.additive(pass != 1);
				m.push();
				m.translate(ox, oy, 0.0f);
				m.scale(s, s, 1.0f);
				if (pass == 0) {
					float a = appear(0.10f);
					if (a > 0.01f) {
						fx.fan(0.0f, -36.0f, 132.0f, 30.0f, 0.0f, TWO_PI, col(a * 0.11f, VIOLET), col(0.0f, VIOLET));
					}
				}
				for (int i = 0; i < 4; i++) {
					drawButton(i, pass);
				}
				m.pop();
				if (!intro) drawButton(4, pass);
				if (pass == 1) drawNameChip();
			}
		} finally {
			fx.flush();
		}
	}

	private void drawButton(int i, int pass) {
		float a = i == 4 ? appear(0.9f) : appear(0.25f + i * 0.08f);
		if (a <= 0.01f) return;
		boolean main = i < 3;
		float f = main ? focusAnim[i] : 0.0f;
		float hv = hover[i];
		float e = main ? f : hv;
		float r = radius(i) * (0.85f + 0.15f * a);
		float cx;
		float cy;
		if (i < 4) {
			cx = BX[i];
			cy = BY[i] + (1.0f - a) * 10.0f;
		} else {
			cx = cornerX();
			cy = cornerY() + (1.0f - a) * 8.0f;
		}
		float breathe = 0.5f + 0.5f * (float) Math.sin(time * 1.5f + i * 1.3f);

		if (pass == 0) {
			float ga = a * (0.07f + 0.26f * e + 0.06f * hv + 0.03f * breathe * e);
			fx.glow(cx, cy, r * 2.3f, col(ga, VIOLET), col(0.0f, VIOLET));
			if (ripple[i] >= 0.0f) {
				float rp = ripple[i];
				float rr = r * (1.0f + 1.4f * rp);
				float ra = (1.0f - rp) * 0.6f * a;
				fx.ringBand(cx, cy, rr - 2.2f, rr, col(0.0f, 0xC9B3FF), col(ra, 0xC9B3FF));
				fx.ringBand(cx, cy, rr, rr + 2.2f, col(ra, 0xC9B3FF), col(0.0f, 0xC9B3FF));
			}
		} else if (pass == 1) {
			// Тёмное стекло: в центре чуть светлее
			fx.fan(cx, cy, r, r, 0.0f, TWO_PI, col(a * (0.30f + 0.16f * e), 0x4A3A86), col(a * (0.20f + 0.12f * e), 0x2A2055));
			// Обводка и блик на ней сверху слева
			fx.ring(cx, cy, r - 0.7f, r, col(a * (0.22f + 0.30f * e + 0.10f * hv), 0xD9CCFF));
			float hl = a * (0.35f + 0.45f * e);
			fx.arc(cx, cy, r - 1.1f, r + 0.2f, PI, 1.25f * PI, col(0.0f, 0xFFFFFF), col(hl, 0xFFFFFF));
			fx.arc(cx, cy, r - 1.1f, r + 0.2f, 1.25f * PI, 1.5f * PI, col(hl, 0xFFFFFF), col(0.0f, 0xFFFFFF));
			drawIcon(i, cx, cy, r / 24.0f, col(a * (0.65f + 0.35f * e), LAV), e);
		} else {
			// Блик на стекле
			fx.fan(cx - r * 0.30f, cy - r * 0.34f, r * 0.52f, r * 0.36f, 0.0f, TWO_PI,
					col(a * (0.10f + 0.16f * e), 0xFFFFFF), col(0.0f, 0xFFFFFF));
			// Вращающиеся штрихи вокруг выбранной кнопки
			if (main && e > 0.4f) {
				float oa = a * (e - 0.4f) / 0.6f * 0.45f;
				for (int k = 0; k < 14; k++) {
					float ang = time * 0.55f + k * (TWO_PI / 14.0f);
					fx.arc(cx, cy, r * 1.30f, r * 1.30f + 1.2f, ang, ang + 0.12f, col(oa, 0xC9B3FF), col(oa, 0xC9B3FF));
				}
			}
		}
	}

	/** Плашка с именем игрока в левом верхнем углу. */
	private void drawNameChip() {
		float a = appear(0.5f);
		if (a <= 0.01f || textRenderer == null) return;
		float x = 14.0f;
		float y = 12.0f;
		float hgt = 22.0f;
		float r = hgt / 2.0f;
		float w = hgt + 4.0f + textRenderer.getWidth(nameText) + 12.0f;
		int c = col(a * 0.34f, 0x1A1233);
		fx.rect(x + r, y, x + w - r, y + hgt, c);
		fx.fan(x + r, y + r, r, r, HALF_PI, 3.0f * HALF_PI, c, c);
		fx.fan(x + w - r, y + r, r, r, -HALF_PI, HALF_PI, c, c);
		fx.fan(x + r, y + r, r - 2.5f, r - 2.5f, 0.0f, TWO_PI, col(a, 0xB48CFF), col(a, 0x7A4DFF));
		fx.ring(x + r, y + r, r - 2.5f, r - 1.7f, col(a * 0.5f, 0xFFFFFF));
	}

	// ------------------------------------------------------------------ значки

	private void drawIcon(int i, float cx, float cy, float u, int c, float e) {
		switch (i) {
			case 0 -> {
				// Шестерёнка
				fx.ring(cx, cy, 4.1f * u, 8.2f * u, c);
				float rot = time * 0.5f * e;
				for (int k = 0; k < 8; k++) {
					float ang = rot + k * (TWO_PI / 8.0f);
					fx.rotRect(cx + (float) Math.cos(ang) * 9.6f * u, cy + (float) Math.sin(ang) * 9.6f * u,
							2.0f * u, 2.4f * u, ang - HALF_PI, c);
				}
			}
			case 1 -> person(cx, cy, u, 1.0f, c);
			case 2 -> {
				int side = fade(c, 0.6f);
				person(cx - 11.0f * u, cy + 3.0f * u, u, 0.62f, side);
				person(cx + 11.0f * u, cy + 3.0f * u, u, 0.62f, side);
				person(cx, cy + 1.0f * u, u, 0.8f, c);
			}
			case 3 -> {
				// Кнопка питания
				fx.arc(cx, cy, 5.6f * u, 7.6f * u, -HALF_PI + 0.7f, -HALF_PI + TWO_PI - 0.7f, c, c);
				fx.rect(cx - 1.0f * u, cy - 9.5f * u, cx + 1.0f * u, cy - 0.5f * u, c);
			}
			default -> {
				// Круговая стрелка: повторить
				float a = 0.55f;
				fx.arc(cx, cy, 6.0f * u, 8.0f * u, a, 5.4f, c, c);
				float cs = (float) Math.cos(a);
				float sn = (float) Math.sin(a);
				float px = cx + cs * 7.0f * u;
				float py = cy + sn * 7.0f * u;
				fx.tri(px + cs * 4.2f * u, py + sn * 4.2f * u, c,
						px - cs * 4.2f * u, py - sn * 4.2f * u, c,
						px + sn * 5.2f * u, py - cs * 5.2f * u, c);
			}
		}
	}

	/** Силуэт человека: голова и плечи. */
	private void person(float cx, float cy, float u, float sp, int c) {
		fx.disc(cx, cy - 7.0f * u * sp, 5.4f * u * sp, c);
		fx.fan(cx, cy + 12.5f * u * sp, 11.0f * u * sp, 9.5f * u * sp, PI, TWO_PI, c, c);
	}

	// ------------------------------------------------------------------ тексты меню

	private void drawTexts(DrawContext ctx, float s, float ox, float oy) {
		MatrixStack m = ctx.getMatrices();

		m.push();
		m.translate(ox, oy, 0.0f);
		m.scale(s, s, 1.0f);

		float ta = appear(0.10f);
		int alpha = (int) (255.0f * ta);
		float rise = (1.0f - ta) * 8.0f;
		if (alpha >= 8) {
			int tw = textRenderer.getWidth(welcome);
			m.push();
			m.translate(0.0f, -46.0f + rise, 0.0f);
			m.scale(1.5f, 1.5f, 1.0f);
			ctx.drawText(textRenderer, welcome, -tw / 2, 0, (alpha << 24) | 0xFFFFFF, false);
			m.pop();
		}
		int subAlpha = (int) (alpha * 0.62f);
		if (subAlpha >= 8) {
			int sw = textRenderer.getWidth(subtitle);
			m.push();
			m.translate(0.0f, -21.0f + rise, 0.0f);
			m.scale(0.9f, 0.9f, 1.0f);
			ctx.drawText(textRenderer, subtitle, -sw / 2, 0, (subAlpha << 24) | 0xCFC6EE, false);
			m.pop();
		}

		// Подписи под кнопками
		for (int i = 0; i < 4; i++) {
			float vis = (i < 3 ? focusAnim[i] : hover[i]) * (i < 3 ? appear(0.25f + i * 0.08f) : appear(0.4f));
			int la = (int) (255.0f * vis * 0.92f);
			if (la < 8) continue;
			int lw = textRenderer.getWidth(labels[i]);
			float r = radius(i);
			m.push();
			m.translate(BX[i], BY[i] + r + 7.0f + (1.0f - vis) * 3.0f, 0.0f);
			m.scale(0.9f, 0.9f, 1.0f);
			ctx.drawText(textRenderer, labels[i], -lw / 2, 0, (la << 24) | LAV, false);
			m.pop();
		}
		m.pop();

		// Имя игрока (буква в аватаре и ник)
		float ca = appear(0.5f);
		int cAlpha = (int) (255.0f * ca);
		if (cAlpha >= 8) {
			int iw = textRenderer.getWidth(initialText);
			m.push();
			m.translate(25.0f, 23.0f, 0.0f);
			m.scale(0.9f, 0.9f, 1.0f);
			ctx.drawText(textRenderer, initialText, -iw / 2, -4, (cAlpha << 24) | 0xFFFFFF, false);
			m.pop();
			ctx.drawText(textRenderer, nameText, 40, 19, (cAlpha << 24) | LAV, false);
		}

		// Время и дата справа вверху
		updateClock();
		float ka = appear(0.6f);
		int kAlpha = (int) (200.0f * ka);
		if (kAlpha >= 8) {
			int tw = textRenderer.getWidth(clockTime);
			m.push();
			m.translate(width - 14.0f, 10.0f, 0.0f);
			m.scale(1.6f, 1.6f, 1.0f);
			ctx.drawText(textRenderer, clockTime, -tw, 0, (kAlpha << 24) | 0xFFFFFF, false);
			m.pop();
			int dw = textRenderer.getWidth(clockDate);
			m.push();
			m.translate(width - 14.0f, 27.0f, 0.0f);
			m.scale(0.9f, 0.9f, 1.0f);
			ctx.drawText(textRenderer, clockDate, -dw, 0, (((int) (kAlpha * 0.7f)) << 24) | 0xCFC6EE, false);
			m.pop();
		}

		// Версия внизу слева
		float fa = appear(0.7f);
		int fAlpha = (int) (110.0f * fa);
		if (fAlpha >= 8) {
			if (footer == null) {
				String version = FabricLoader.getInstance().getModContainer("flyvisual")
						.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
				footer = Text.literal(NAME + (version.isEmpty() ? "" : " " + version) + "  ·  Minecraft "
						+ MinecraftClient.getInstance().getGameVersion()).setStyle(UI);
			}
			ctx.drawText(textRenderer, footer, 14, height - 18, (fAlpha << 24) | 0xFFFFFF, false);
		}

		// Подпись у кнопки повтора
		if (!intro && hover[4] > 0.05f) {
			int ra = (int) (255.0f * hover[4] * 0.92f);
			if (ra >= 8) {
				int rw = textRenderer.getWidth(labels[4]);
				ctx.drawText(textRenderer, labels[4], (int) (cornerX() - 20.0f - rw), (int) cornerY() - 4, (ra << 24) | LAV, false);
			}
		}
	}

	/** Время и дата обновляются раз в минуту, а не каждый кадр. */
	private void updateClock() {
		LocalDateTime now = LocalDateTime.now();
		int minute = now.getHour() * 60 + now.getMinute();
		if (minute == clockMinute) return;
		clockMinute = minute;
		clockTime = Text.literal(String.format(Locale.ROOT, "%02d:%02d", now.getHour(), now.getMinute())).setStyle(UI);
		String day = now.getDayOfWeek().getDisplayName(TextStyle.FULL, RU);
		if (!day.isEmpty()) day = Character.toUpperCase(day.charAt(0)) + day.substring(1);
		String month = now.getMonth().getDisplayName(TextStyle.FULL, RU);
		clockDate = Text.literal(day + ", " + now.getDayOfMonth() + " " + month).setStyle(UI);
	}

	// ------------------------------------------------------------------ интро

	private void restartIntro() {
		intro = true;
		introStart = System.nanoTime();
		introChime = false;
	}

	private void skipIntro() {
		if (!intro || introStart < 0L) return;
		float ti = (System.nanoTime() - introStart) / 1.0e9f;
		if (ti < INTRO_SKIP_TO) {
			introStart -= (long) ((INTRO_SKIP_TO - ti) * 1.0e9f);
		}
	}

	/**
	 * Интро: частицы с хвостами летят по спирали к центру и собираются в светящееся ядро, от него расходятся волны
	 * света и растягивается полоса-блик, ядро разгорается, вспышка и взрыв искр, и проявляются фон и меню.
	 */
	private void drawIntroShapes(MatrixStack m, float ti) {
		float s = uiScale();
		float cx = width / 2.0f;
		float cy = height / 2.0f;
		fx.begin(m);
		try {
			// Чёрная завеса: прячет фон и меню, потом тает
			float veil = ti < INTRO_FLASH ? 1.0f : 1.0f - smooth(0.0f, 1.0f, (ti - INTRO_FLASH) / 0.9f);
			if (veil > 0.003f) fx.rect(0.0f, 0.0f, width, height, col(veil, 0x000000));

			fx.additive(true);
			float fadeAll = 1.0f - smooth(INTRO_FLASH, INTRO_FLASH + 0.35f, ti);

			// Звёзды на заднем плане
			float starIn = smooth(0.0f, 1.0f, ti) * fadeAll;
			if (starIn > 0.01f) {
				for (int i = 0; i < 80; i++) {
					float tw = 0.5f + 0.5f * (float) Math.sin(ti * (1.0f + rnd(i, 3) * 2.0f) + i);
					float a = starIn * (0.12f + 0.45f * tw) * rnd(i, 4);
					float x = rnd(i, 1) * width;
					float y = rnd(i, 2) * height;
					fx.rect(x, y, x + 1.3f, y + 1.3f, col(a, 0xD8C8FF));
				}
			}

			// 1. Частицы с хвостами летят к центру по спирали
			float fadeP = 1.0f - smooth(1.6f, 2.2f, ti);
			if (fadeP > 0.01f) {
				float rmax = Math.max(width, height) * 0.62f;
				float g = ti / 1.7f;
				for (int i = 0; i < 320; i++) {
					float r0 = rmax * (0.2f + 0.8f * rnd(i, 1));
					float a0 = rnd(i, 2) * TWO_PI;
					float delay = rnd(i, 3) * 0.30f;
					float rt = rnd(i, 4) * 5.0f * s;
					float p = easeInOut(clamp01((g - delay) / 0.70f));
					float p2 = easeInOut(clamp01((g - delay - 0.045f) / 0.70f));
					float r = r0 + (rt - r0) * p;
					float ang = a0 + p * 2.2f;
					float r2 = r0 + (rt - r0) * p2;
					float ang2 = a0 + p2 * 2.2f;
					float al = (0.2f + 0.8f * p) * fadeP * (0.6f + 0.4f * rnd(i, 5));
					int c = lerp(col(al, 0x8E63FF), col(al, 0xFFFFFF), p);
					fx.line(cx + (float) Math.cos(ang2) * r2, cy + (float) Math.sin(ang2) * r2,
							cx + (float) Math.cos(ang) * r, cy + (float) Math.sin(ang) * r,
							1.1f + rnd(i, 6) * 0.9f, c & 0xFFFFFF, c);
				}
			}

			// 2. Светящееся ядро: разгорается и перед вспышкой резко растёт
			float coreIn = smooth(0.2f, 1.9f, ti);
			float boom = smooth(2.5f, INTRO_FLASH - 0.02f, ti);
			float inten = coreIn * (1.0f - smooth(INTRO_FLASH + 0.05f, INTRO_FLASH + 0.45f, ti));
			if (inten > 0.01f) {
				float pulse = ti > 1.7f ? 0.5f + 0.5f * (float) Math.sin(ti * 8.0f) : 0.0f;
				float rc = (4.0f + 9.0f * coreIn + 2.2f * pulse + 46.0f * boom * boom) * s;
				fx.glow(cx, cy, rc * 3.4f, col(inten * 0.30f, 0x8E63FF), col(0.0f, 0x8E63FF));
				fx.glow(cx, cy, rc * 1.8f, col(inten * 0.55f, 0xB896FF), col(0.0f, 0xB896FF));
				fx.glow(cx, cy, rc * 0.95f, col(inten * 0.95f, 0xFFFFFF), col(0.0f, 0xFFFFFF));
				fx.disc(cx, cy, rc * 0.40f, col(inten, 0xFFFFFF));
			}

			// 3. Волны света расходятся от ядра
			float waveFade = 1.0f - smooth(INTRO_FLASH - 0.1f, INTRO_FLASH + 0.2f, ti);
			for (int k = 0; k < 4; k++) {
				float ph = (ti - 1.5f - k * 0.38f) / 1.1f;
				if (ph <= 0.0f || ph >= 1.0f) continue;
				float rr = (6.0f + 190.0f * ease(ph)) * s;
				float ra = (1.0f - ph) * 0.5f * waveFade;
				if (ra <= 0.01f) continue;
				fx.ringBand(cx, cy, rr - 4.0f * s, rr, col(0.0f, 0xB896FF), col(ra, 0xB896FF));
				fx.ringBand(cx, cy, rr, rr + 4.0f * s, col(ra, 0xB896FF), col(0.0f, 0xB896FF));
			}

			// 4. Полоса-блик через центр (как на широкоформатном кино)
			float fl = smooth(2.0f, 3.0f, ti) * (1.0f - smooth(INTRO_FLASH, INTRO_FLASH + 0.5f, ti));
			if (fl > 0.01f) {
				float len = width * (0.10f + 0.50f * fl);
				float th = 1.5f * s;
				int core = col(fl * 0.95f, 0xFFFFFF);
				int clear = col(0.0f, 0xB896FF);
				fx.gradH(cx - len, cy - th, cx, cy + th, clear, core);
				fx.gradH(cx, cy - th, cx + len, cy + th, core, clear);
				fx.lens(cx, cy, len, 16.0f * s, col(fl * 0.30f, 0xB896FF), clear);
				fx.lens(cx, cy, 13.0f * s, len * 0.22f, col(fl * 0.22f, 0xD8C8FF), clear);
			}

			// 5. После вспышки: взрыв искр с хвостами и ударные волны
			float bt = ti - INTRO_FLASH;
			if (bt > 0.0f && bt < 1.6f) {
				for (int i = 0; i < 180; i++) {
					float ang = rnd(i, 31) * TWO_PI;
					float speed = (90.0f + 520.0f * rnd(i, 32)) * s;
					float life = 0.6f + 0.8f * rnd(i, 33);
					float u = clamp01(bt / life);
					if (u >= 1.0f) continue;
					float u0 = clamp01((bt - 0.07f) / life);
					float d1 = speed * life * 0.55f * ease(u);
					float d0 = speed * life * 0.55f * ease(u0);
					float al = (1.0f - u) * (1.0f - u);
					int c = lerp(col(al, 0xC9B3FF), col(al, 0xFFFFFF), rnd(i, 34));
					fx.line(cx + (float) Math.cos(ang) * d0, cy + (float) Math.sin(ang) * d0,
							cx + (float) Math.cos(ang) * d1, cy + (float) Math.sin(ang) * d1,
							(1.0f + 1.6f * rnd(i, 35)) * Math.max(1.0f, s * 0.8f), c & 0xFFFFFF, c);
				}
				for (int k = 0; k < 2; k++) {
					float ph = (bt - k * 0.12f) / 0.9f;
					if (ph <= 0.0f || ph >= 1.0f) continue;
					float rr = (10.0f + 520.0f * ease(ph)) * s;
					float ra = (1.0f - ph) * 0.5f;
					fx.ringBand(cx, cy, rr - 8.0f * s, rr, col(0.0f, 0xC9B3FF), col(ra, 0xC9B3FF));
					fx.ringBand(cx, cy, rr, rr + 8.0f * s, col(ra, 0xC9B3FF), col(0.0f, 0xC9B3FF));
				}
			}

			// 6. Вспышка
			fx.additive(false);
			float flash = Math.max(0.0f, 1.0f - Math.abs(ti - (INTRO_FLASH - 0.02f)) / 0.30f);
			if (flash > 0.0f) {
				fx.rect(0.0f, 0.0f, width, height, col(flash * 0.92f, 0xEDE6FF));
				if (!introChime) {
					introChime = true;
					try {
						MinecraftClient.getInstance().getSoundManager()
								.play(PositionedSoundInstance.master(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f));
					} catch (Throwable ignored) {
					}
				}
			}
		} finally {
			fx.flush();
		}
	}

	private void drawIntroText(DrawContext ctx, float ti) {
		if (ti > 0.8f && ti < INTRO_SKIP_TO) {
			int ha = (int) (110.0f * smooth(0.8f, 1.4f, ti));
			if (ha >= 8) {
				ctx.drawText(textRenderer, skipHint, (width - textRenderer.getWidth(skipHint)) / 2, height - 22,
						(ha << 24) | 0xFFFFFF, false);
			}
		}
	}

	// ------------------------------------------------------------------ управление

	private void activate(int i) {
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
		ripple[i] = 0.0f;
		switch (i) {
			case 0 -> mc.setScreen(new OptionsScreen(this, mc.options));
			case 1 -> mc.setScreen(new SelectWorldScreen(this));
			case 2 -> {
				Screen next = mc.options.skipMultiplayerWarning ? new MultiplayerScreen(this) : new MultiplayerWarningScreen(this);
				mc.setScreen(next);
			}
			case 3 -> mc.scheduleStop();
			default -> restartIntro();
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
				focusMain = Math.max(0, focusMain - 1);
				return true;
			}
			case GLFW.GLFW_KEY_RIGHT -> {
				focusMain = Math.min(2, focusMain + 1);
				return true;
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
				activate(hoveredIdx >= 0 ? hoveredIdx : focusMain);
				return true;
			}
			default -> {
				return super.keyPressed(keyCode, scanCode, modifiers);
			}
		}
	}
}
