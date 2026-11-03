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
	private static final String[] LABELS = {"Одиночная игра", "Сетевая игра", "Настройки", "Моды", "Выход"};
	private static final int BTN_H = 24;
	private static final int BTN_GAP = 8;
	private static final int BTN_RADIUS = 6;

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

	private int btnW() {
		return Math.min(width - 20, 200);
	}

	private int btnX() {
		return (width - btnW()) / 2;
	}

	private int firstBtnY() {
		int total = LABELS.length * BTN_H + (LABELS.length - 1) * BTN_GAP;
		int y = (int) (height * 0.44f);
		if (y + total > height - 22) {
			y = height - 22 - total;
		}
		return Math.max(y, 20);
	}

	private int btnY(int i) {
		return firstBtnY() + i * (BTN_H + BTN_GAP);
	}

	private boolean over(int i, double mx, double my) {
		return mx >= btnX() && mx < btnX() + btnW() && my >= btnY(i) && my < btnY(i) + BTN_H;
	}

	// ---------- Отрисовка ----------

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastNanos) / 1.0e9f, 0.05f);
		lastNanos = now;

		float t = background.seconds();
		background.render(ctx, width, height, mouseX, mouseY, false);

		// Плавное появление всего экрана из темноты
		float fadeIn = UiShapes.clamp01(1.0f - t / 0.8f);

		drawLogo(ctx, t);

		for (int i = 0; i < LABELS.length; i++) {
			float target = over(i, mouseX, mouseY) ? 1.0f : 0.0f;
			hover[i] += (target - hover[i]) * (1.0f - (float) Math.exp(-14.0f * dt));

			float appear = UiShapes.clamp01((t - 0.45f - i * 0.1f) / 0.45f);
			drawButton(ctx, i, appear);
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

		float sc = Math.max(3.0f, Math.min(7.0f, height / 80.0f));
		float logoY = height * 0.18f + (1.0f - ease) * -10.0f;
		int flyW = tr.getWidth("Fly");
		int visW = tr.getWidth("Visual");
		float x0 = -(flyW + visW) / 2.0f;

		MatrixStack ms = ctx.getMatrices();
		ms.push();
		ms.translate(width / 2.0f, logoY, 0.0f);
		ms.scale(sc, sc, 1.0f);

		// Красное свечение вокруг букв
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

		String sub = "ВИЗУАЛЬНЫЙ КЛИЕНТ  |  1.20.1";
		int subColor = UiShapes.mulA(0xFFFFB4B0, ease);
		if (((subColor >>> 24) & 0xFF) >= 12) {
			ctx.drawText(tr, sub, (width - tr.getWidth(sub)) / 2, Math.round(logoY + 9.0f * sc + 6.0f), subColor, true);
		}
	}

	private void drawButton(DrawContext ctx, int i, float appear) {
		if (appear <= 0.01f) return;
		int x = btnX();
		int y = btnY(i) + Math.round((1.0f - appear) * 6.0f);
		int w = btnW();
		float hv = hover[i];

		int accent = 0xFF000000 | (Theme.accent() & 0xFFFFFF);
		int border = UiShapes.lerp(UiShapes.lerp(accent, 0xFF000000, 0.25f), accent, hv);
		int fill = UiShapes.lerp(0xFF1B1B1F, 0xFF2B2B31, hv);

		if (hv > 0.02f) {
			UiShapes.roundRect(ctx, x - 3, y - 3, w + 6, BTN_H + 6, BTN_RADIUS + 3,
					UiShapes.mulA(accent, 0.25f * hv * appear));
		}
		UiShapes.roundRect(ctx, x, y, w, BTN_H, BTN_RADIUS, UiShapes.mulA(border, appear));
		UiShapes.roundRect(ctx, x + 1, y + 1, w - 2, BTN_H - 2, BTN_RADIUS - 1, UiShapes.mulA(fill, appear));

		String label = LABELS[i];
		int textColor = UiShapes.mulA(UiShapes.lerp(0xFFD0D0D0, 0xFFFFFFFF, hv), appear);
		if (((textColor >>> 24) & 0xFF) >= 12) {
			ctx.drawText(textRenderer, label, x + (w - textRenderer.getWidth(label)) / 2, y + (BTN_H - 8) / 2 + 1, textColor, true);
		}
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
			case 2 -> mc.setScreen(new OptionsScreen(this, mc.options));
			case 3 -> mc.setScreen(new ModsScreen(this));
			case 4 -> mc.scheduleStop();
			default -> {
			}
		}
	}
}
