package com.flyvisual.gui;

import com.flyvisual.config.Theme;
import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.EmoteModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Меню эмоций: сетка скруглённых кнопок. Клик или цифры 1-6 запускают эмоцию. */
public class EmoteScreen extends Screen {
	private static final int COLS = 2;
	private static final int BTN_W = 112;
	private static final int BTN_H = 26;
	private static final int GAP = 6;
	private static final int PAD = 12;

	private final float[] hover = new float[EmoteModule.NAMES.length];
	private long lastNanos = System.nanoTime();
	private float open;

	public EmoteScreen() {
		super(Text.literal("Эмоции"));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private int rows() {
		return (EmoteModule.NAMES.length + COLS - 1) / COLS;
	}

	private int blockW() {
		return COLS * BTN_W + (COLS - 1) * GAP;
	}

	private int blockH() {
		return rows() * BTN_H + (rows() - 1) * GAP;
	}

	private int left() {
		return (width - blockW()) / 2;
	}

	private int top() {
		return (height - blockH()) / 2 + 6;
	}

	private int bx(int i) {
		return left() + (i % COLS) * (BTN_W + GAP);
	}

	private int by(int i) {
		return top() + (i / COLS) * (BTN_H + GAP);
	}

	private boolean over(int i, double mx, double my) {
		return mx >= bx(i) && mx < bx(i) + BTN_W && my >= by(i) && my < by(i) + BTN_H;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastNanos) / 1.0e9f, 0.05f);
		lastNanos = now;
		open = Math.min(1.0f, open + dt / 0.18f);
		float a = open;

		ctx.fill(0, 0, width, height, UiShapes.mulA(0xFF000000, 0.45f * a));

		int fx = left() - PAD;
		int fy = top() - PAD - 14;
		int fw = blockW() + 2 * PAD;
		int fh = blockH() + 2 * PAD + 14;
		UiShapes.roundRect(ctx, fx - 1, fy - 1, fw + 2, fh + 2, 10, UiShapes.mulA(0xFFFFFFFF, 0.12f * a));
		UiShapes.roundRect(ctx, fx, fy, fw, fh, 9, UiShapes.mulA(0xFF0C0D0F, 0.92f * a));

		int titleColor = UiShapes.mulA(Theme.accent(), a);
		if (((titleColor >>> 24) & 0xFF) >= 12) {
			ctx.drawText(textRenderer, "Эмоции", fx + PAD, fy + 8, titleColor, true);
		}

		EmoteModule module = ModuleManager.emotes();
		for (int i = 0; i < EmoteModule.NAMES.length; i++) {
			float target = over(i, mouseX, mouseY) ? 1.0f : 0.0f;
			hover[i] += (target - hover[i]) * (1.0f - (float) Math.exp(-14.0f * dt));
			drawButton(ctx, i, a, module != null && module.current() == i);
		}

		String hint = "Цифры 1-6 или клик  |  Shift: остановить";
		int hc = UiShapes.mulA(Theme.TEXT_DIM, a);
		if (((hc >>> 24) & 0xFF) >= 12) {
			ctx.drawText(textRenderer, hint, (width - textRenderer.getWidth(hint)) / 2, fy + fh + 6, hc, true);
		}
		super.render(ctx, mouseX, mouseY, delta);
	}

	private void drawButton(DrawContext ctx, int i, float a, boolean playing) {
		int x = bx(i);
		int y = by(i);
		float hv = hover[i];
		int accent = 0xFF000000 | (Theme.accent() & 0xFFFFFF);

		if (hv > 0.02f) {
			UiShapes.roundRect(ctx, x - 2, y - 2, BTN_W + 4, BTN_H + 4, 8, UiShapes.mulA(accent, 0.2f * hv * a));
		}
		int border = UiShapes.lerp(playing ? accent : 0xFF33363B, accent, hv);
		UiShapes.roundRect(ctx, x, y, BTN_W, BTN_H, 7, UiShapes.mulA(border, a));
		UiShapes.roundRect(ctx, x + 1, y + 1, BTN_W - 2, BTN_H - 2, 6,
				UiShapes.mulA(UiShapes.lerp(0xFF141518, 0xFF2A2D31, hv), a));

		int num = UiShapes.mulA(accent, (0.5f + 0.5f * hv) * a);
		int tc = UiShapes.mulA(UiShapes.lerp(0xFFC4C8CE, 0xFFFFFFFF, hv), a);
		if (((tc >>> 24) & 0xFF) >= 12) {
			ctx.drawText(textRenderer, String.valueOf(i + 1), x + 8, y + 9, num, true);
			ctx.drawText(textRenderer, EmoteModule.NAMES[i], x + 20, y + 9, tc, true);
		}
	}

	private void play(int i) {
		EmoteModule module = ModuleManager.emotes();
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.setScreen(null);
		if (module != null) {
			module.start(i);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			for (int i = 0; i < EmoteModule.NAMES.length; i++) {
				if (over(i, mouseX, mouseY)) {
					play(i);
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		int i = keyCode - GLFW.GLFW_KEY_1;
		if (i >= 0 && i < EmoteModule.NAMES.length) {
			play(i);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
}
