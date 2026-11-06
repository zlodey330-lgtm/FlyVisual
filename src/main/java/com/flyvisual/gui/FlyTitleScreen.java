package com.flyvisual.gui;

import com.flyvisual.config.Theme;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerWarningScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * Главное меню FlyVisual: живой фон с тучами, логотип и сетка стеклянных скруглённых кнопок
 * (серо-чёрные, с тонкой серебристой обводкой в цвет темы).
 */
public class FlyTitleScreen extends Screen {
	private static final String[] LABELS = {
			"Одиночная игра", "Сетевая игра", "Моды", "Меню FlyVisual", "Настройки", "Выход"
	};
	private static final int COLS = 2;
	private static final int BTN_H = 26;
	private static final int GAP = 6;
	private static final int PAD = 10;
	private static final int RADIUS = 7;

	private final TitleBackground background = new TitleBackground();
	private final float[] hover = new float[LABELS.length];
	private long lastNanos = System.nanoTime();

	public FlyTitleScreen() {
		super(Text.literal("FlyVisual"));
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	// ---------- Геометрия ----------

	private float logoScale() {
		return Math.max(2.5f, Math.min(6.0f, height / 60.0f));
	}

	private int rows() {
		return (LABELS.length + COLS - 1) / COLS;
	}

	private int btnW() {
		return Math.max(70, Math.min(104, (width - 2 * PAD - 20 - GAP) / COLS));
	}

	private int blockW() {
		return COLS * btnW() + (COLS - 1) * GAP;
	}

	private int blockH() {
		return rows() * BTN_H + (rows() - 1) * GAP;
	}

	private int blockX() {
		return (width - blockW()) / 2;
	}

	private int blockY() {
		int logoBottom = Math.round(height * 0.14f + 9.0f * logoScale());
		int y = logoBottom + 18 + PAD;
		int maxY = height - blockH() - PAD - 18;
		return Math.max(PAD + 4, Math.min(y, maxY));
	}

	private int bx(int i) {
		return blockX() + (i % COLS) * (btnW() + GAP);
	}

	private int by(int i) {
		return blockY() + (i / COLS) * (BTN_H + GAP);
	}

	private boolean over(int i, double mx, double my) {
		return mx >= bx(i) && mx < bx(i) + btnW() && my >= by(i) && my < by(i) + BTN_H;
	}

	// ---------- Отрисовка ----------

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastNanos) / 1.0e9f, 0.05f);
		lastNanos = now;

		float t = background.seconds();
		background.render(ctx, width, height, mouseX, mouseY, false);

		float fadeIn = UiShapes.clamp01(1.0f - t / 0.8f);

		drawLogo(ctx, t);
		drawFrame(ctx, UiShapes.clamp01((t - 0.35f) / 0.5f));

		for (int i = 0; i < LABELS.length; i++) {
			float target = over(i, mouseX, mouseY) ? 1.0f : 0.0f;
			hover[i] += (target - hover[i]) * (1.0f - (float) Math.exp(-14.0f * dt));
			drawButton(ctx, i, UiShapes.clamp01((t - 0.5f - i * 0.07f) / 0.4f));
		}

		drawFooter(ctx, UiShapes.clamp01((t - 0.8f) / 0.6f));

		if (fadeIn > 0.0f) {
			ctx.fill(0, 0, width, height, UiShapes.mulA(0xFF000000, fadeIn * fadeIn));
		}
		super.render(ctx, mouseX, mouseY, delta);
	}

	/** Матовая подложка под кнопками. */
	private void drawFrame(DrawContext ctx, float appear) {
		if (appear <= 0.01f) return;
		int x = blockX() - PAD;
		int y = blockY() - PAD;
		int w = blockW() + 2 * PAD;
		int h = blockH() + 2 * PAD;
		UiShapes.roundRect(ctx, x - 1, y - 1, w + 2, h + 2, RADIUS + 5, UiShapes.mulA(0xFFFFFFFF, 0.10f * appear));
		UiShapes.roundRect(ctx, x, y, w, h, RADIUS + 4, UiShapes.mulA(0xFF08090B, 0.62f * appear));
	}

	private void drawLogo(DrawContext ctx, float t) {
		TextRenderer tr = textRenderer;
		float p = UiShapes.clamp01((t - 0.15f) / 0.9f);
		float ease = 1.0f - (1.0f - p) * (1.0f - p) * (1.0f - p);
		if (ease <= 0.01f) return;

		float sc = logoScale();
		float logoY = height * 0.14f + (1.0f - ease) * -10.0f;
		int flyW = tr.getWidth("Fly");
		int visW = tr.getWidth("Visual");
		float x0 = -(flyW + visW) / 2.0f;

		MatrixStack ms = ctx.getMatrices();
		ms.push();
		ms.translate(width / 2.0f, logoY, 0.0f);
		ms.scale(sc, sc, 1.0f);

		// Мягкое свечение вокруг букв
		int glow = UiShapes.mulA(0xFF000000 | (Theme.accent() & 0xFFFFFF), 0.14f * ease);
		float[][] offsets = {{-0.8f, 0}, {0.8f, 0}, {0, -0.8f}, {0, 0.8f}, {-0.6f, -0.6f}, {0.6f, 0.6f}, {-0.6f, 0.6f}, {0.6f, -0.6f}};
		for (float[] o : offsets) {
			ms.push();
			ms.translate(o[0], o[1], 0.0f);
			ctx.drawText(tr, "Fly", Math.round(x0), 0, glow, false);
			ctx.drawText(tr, "Visual", Math.round(x0 + flyW), 0, glow, false);
			ms.pop();
		}

		ctx.drawText(tr, "Fly", Math.round(x0), 0, UiShapes.mulA(Theme.accent(), ease), true);
		ctx.drawText(tr, "Visual", Math.round(x0 + flyW), 0, UiShapes.mulA(0xFFFFFFFF, ease), true);
		ms.pop();
	}

	private void drawButton(DrawContext ctx, int i, float appear) {
		if (appear <= 0.01f) return;
		int x = bx(i);
		int y = by(i) + Math.round((1.0f - appear) * 6.0f);
		int w = btnW();
		int h = BTN_H;
		float hv = hover[i];
		boolean primary = i == 0;
		int accent = 0xFF000000 | (Theme.accent() & 0xFFFFFF);

		// Тень и свечение при наведении
		UiShapes.roundRect(ctx, x, y + 2, w, h, RADIUS, UiShapes.mulA(0xFF000000, 0.35f * appear));
		if (hv > 0.02f) {
			UiShapes.roundRect(ctx, x - 2, y - 2, w + 4, h + 4, RADIUS + 2, UiShapes.mulA(accent, 0.20f * hv * appear));
		}

		// Тонкая обводка и стеклянная заливка
		int border = UiShapes.lerp(primary ? UiShapes.lerp(0xFF3C4046, accent, 0.55f) : 0xFF33363B, accent, hv);
		int fillBase = primary ? 0xFF1E2023 : 0xFF141518;
		int fill = UiShapes.lerp(fillBase, 0xFF2A2D31, hv);
		UiShapes.roundRect(ctx, x, y, w, h, RADIUS, UiShapes.mulA(border, appear));
		UiShapes.roundRect(ctx, x + 1, y + 1, w - 2, h - 2, RADIUS - 1, UiShapes.mulA(fill, appear));
		// Блик сверху
		ctx.fill(x + RADIUS, y + 1, x + w - RADIUS, y + 2, UiShapes.mulA(0x30FFFFFF, appear));

		// Полоска-индикатор слева растёт при наведении
		int barH = 6 + Math.round((h - 16) * hv);
		UiShapes.roundRect(ctx, x + 6, y + (h - barH) / 2, 2, barH, 1,
				UiShapes.mulA(accent, (primary ? 0.9f : 0.35f + 0.65f * hv) * appear));

		String label = LABELS[i];
		int textColor = UiShapes.mulA(UiShapes.lerp(primary ? 0xFFEDEFF2 : 0xFFC4C8CE, 0xFFFFFFFF, hv), appear);
		if (((textColor >>> 24) & 0xFF) < 12) return;

		float tw = textRenderer.getWidth(label);
		float room = w - 20.0f;
		float sc = Math.min(1.0f, room / tw);
		MatrixStack ms = ctx.getMatrices();
		ms.push();
		ms.translate(x + w / 2.0f + 2.0f, y + h / 2.0f, 0.0f);
		ms.scale(sc, sc, 1.0f);
		ctx.drawText(textRenderer, label, Math.round(-tw / 2.0f), -4, textColor, true);
		ms.pop();
	}

	private void drawFooter(DrawContext ctx, float alpha) {
		if (alpha <= 0.02f) return;
		String version = FabricLoader.getInstance().getModContainer("flyvisual")
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
		String left = "FlyVisual " + version;
		String right = "Minecraft 1.20.1";
		int color = UiShapes.mulA(0xFFAAAAAA, alpha * 0.8f);
		if (((color >>> 24) & 0xFF) < 12) return;
		ctx.drawText(textRenderer, left, 6, height - 12, color, true);
		ctx.drawText(textRenderer, right, width - 6 - textRenderer.getWidth(right), height - 12, color, true);
	}

	// ---------- Ввод ----------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && background.seconds() > 0.7f) {
			for (int i = 0; i < LABELS.length; i++) {
				if (over(i, mouseX, mouseY)) {
					MinecraftClient mc = MinecraftClient.getInstance();
					mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
					activate(mc, i);
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void activate(MinecraftClient mc, int index) {
		switch (index) {
			case 0 -> mc.setScreen(new SelectWorldScreen(this));
			case 1 -> mc.setScreen(mc.options.skipMultiplayerWarning
					? new MultiplayerScreen(this)
					: new MultiplayerWarningScreen(this));
			case 2 -> mc.setScreen(new ModsScreen(this));
			case 3 -> mc.setScreen(new FlyMenuScreen());
			case 4 -> mc.setScreen(new OptionsScreen(this, mc.options));
			case 5 -> mc.scheduleStop();
			default -> {
			}
		}
	}
}
