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
 * Главное меню FlyVisual: живой фон, логотип и закруглённые кнопки
 * (серо-чёрные, с красной обводкой в цвет темы).
 */
public class FlyTitleScreen extends Screen {
	private static final String[] LABELS = {"Одиночная", "Играть", "Моды", "Меню", "Настройки", "Выход"};
	/** Кнопки на картинке меню (1517x818): x, y, ширина, высота, затем область текста x0, x1. */
	private static final int[][] BTN = {
			{270, 600, 325, 82, 350, 580},
			{605, 598, 305, 92, 690, 900},
			{920, 600, 330, 82, 1000, 1235},
			{355, 700, 262, 72, 430, 605},
			{627, 700, 256, 72, 700, 870},
			{895, 700, 265, 72, 910, 1070}
	};
	private static final int HILITE = 1;

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
		return Math.max(2.5f, Math.min(4.5f, height / 80.0f));
	}

	private int panelY() {
		return Math.round(height * 0.04f + 9.0f * logoScale() + 10.0f);
	}

	private int panelW() {
		int byHeight = Math.round((height - panelY() - 14) * TitleBackground.PW / (float) TitleBackground.PH);
		return Math.max(120, Math.min(Math.min(width - 16, 460), byHeight));
	}

	private int panelH() {
		return Math.round(panelW() * TitleBackground.PH / (float) TitleBackground.PW);
	}

	private int panelX() {
		return (width - panelW()) / 2;
	}

	private float k() {
		return panelW() / (float) TitleBackground.PW;
	}

	private int bx(int i) {
		return panelX() + Math.round(BTN[i][0] * k());
	}

	private int by(int i) {
		return panelY() + Math.round(BTN[i][1] * k());
	}

	private int bw(int i) {
		return Math.round(BTN[i][2] * k());
	}

	private int bh(int i) {
		return Math.round(BTN[i][3] * k());
	}

	private boolean over(int i, double mx, double my) {
		return mx >= bx(i) && mx < bx(i) + bw(i) && my >= by(i) && my < by(i) + bh(i);
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
		float panelAppear = UiShapes.clamp01((t - 0.2f) / 0.6f);

		drawLogo(ctx, t);

		if (panelAppear > 0.01f) {
			background.drawPanel(ctx, panelX(), panelY(), panelW(), panelH());
			if (panelAppear < 1.0f) {
				ctx.fill(panelX(), panelY(), panelX() + panelW(), panelY() + panelH(),
						UiShapes.mulA(0xFF000000, 1.0f - panelAppear));
			}
		} else {
			ctx.fill(panelX(), panelY(), panelX() + panelW(), panelY() + panelH(), 0xFF000000);
		}

		for (int i = 0; i < LABELS.length; i++) {
			float target = over(i, mouseX, mouseY) ? 1.0f : 0.0f;
			hover[i] += (target - hover[i]) * (1.0f - (float) Math.exp(-14.0f * dt));
			drawButton(ctx, i, UiShapes.clamp01((t - 0.6f - i * 0.07f) / 0.4f));
		}

		drawFooter(ctx, UiShapes.clamp01((t - 0.8f) / 0.6f));

		if (fadeIn > 0.0f) {
			ctx.fill(0, 0, width, height, UiShapes.mulA(0xFF000000, fadeIn * fadeIn));
		}
		super.render(ctx, mouseX, mouseY, delta);
	}

	private void drawLogo(DrawContext ctx, float t) {
		TextRenderer tr = textRenderer;
		float p = UiShapes.clamp01((t - 0.15f) / 0.9f);
		float ease = 1.0f - (1.0f - p) * (1.0f - p) * (1.0f - p);
		if (ease <= 0.01f) return;

		float sc = logoScale();
		float logoY = height * 0.04f + (1.0f - ease) * -10.0f;
		int flyW = tr.getWidth("Fly");
		int visW = tr.getWidth("Visual");
		float x0 = -(flyW + visW) / 2.0f;

		MatrixStack ms = ctx.getMatrices();
		ms.push();
		ms.translate(width / 2.0f, logoY, 0.0f);
		ms.scale(sc, sc, 1.0f);

		int glow = UiShapes.mulA(0xFF000000 | (Theme.accent() & 0xFFFFFF), 0.16f * ease);
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

	/** Подпись поверх готовой кнопки с картинки плюс красная подсветка при наведении. */
	private void drawButton(DrawContext ctx, int i, float appear) {
		if (appear <= 0.01f) return;
		float hv = hover[i];
		int x = bx(i);
		int y = by(i);
		int w = bw(i);
		int h = bh(i);
		int accent = 0xFF000000 | (Theme.accent() & 0xFFFFFF);

		if (hv > 0.02f) {
			ctx.fill(x, y, x + w, y + h, UiShapes.mulA(accent, 0.22f * hv));
		}

		String label = LABELS[i];
		int areaX0 = panelX() + Math.round(BTN[i][4] * k());
		int areaX1 = panelX() + Math.round(BTN[i][5] * k());
		float areaW = areaX1 - areaX0;
		float tw = textRenderer.getWidth(label);
		float sc = Math.min(1.0f, areaW / tw);
		if (i == HILITE) sc = Math.min(1.25f, Math.max(sc, areaW / tw) * 1.0f);
		sc = Math.min(sc, h / 11.0f);

		int color = UiShapes.mulA(UiShapes.lerp(0xFFE0E0E0, 0xFFFFFFFF, hv), appear);
		if (((color >>> 24) & 0xFF) < 12) return;

		MatrixStack ms = ctx.getMatrices();
		ms.push();
		ms.translate((areaX0 + areaX1) / 2.0f, y + h / 2.0f, 0.0f);
		ms.scale(sc, sc, 1.0f);
		ctx.drawText(textRenderer, label, Math.round(-tw / 2.0f), -4, color, true);
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
