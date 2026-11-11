package com.flyvisual.gui;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.config.Theme;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Lang;
import com.flyvisual.module.Module;
import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.MacroModule;
import com.flyvisual.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Главное меню FlyVisual (Right Shift). Широкая панель:
 * слева сетка плиток-модулей, справа настройки выбранного модуля.
 * ЛКМ по плитке: включить/выключить и выбрать. ПКМ: только выбрать. Колесо мыши: прокрутка сетки.
 * Меню плавно открывается и закрывается, переключатели плавно двигаются.
 */
public class FlyMenuScreen extends Screen {
	private static final int MAX_W = 500;
	private static final int MAX_H = 262;
	private static final int HEAD = 28;
	private static final int FOOT = 16;
	private static final int PAD = 8;
	private static final int PANE_W = 164;
	private static final int COLS = 3;
	private static final int TILE_H = 34;
	private static final int GAP = 6;
	private static final int SW = 12;
	private static final int SW_GAP = 5;
	private static final int BTN_W = 86;
	private static final int BTN_H = 16;
	private static final int ROW_H = 24;
	private static final float OPEN_TIME = 0.22f;


	private Module selected;
	private int scroll;
	private DoubleSetting dragging;

	// Выбор любого цвета темы
	private static final int PICK_W = 128;
	private static final int SV_W = 112;
	private static final int SV_H = 70;
	private static final int HUE_H = 10;
	private boolean pickerOpen;
	private boolean dragSv;
	private boolean dragHue;
	private float pickH;
	private float pickS;
	private float pickV;

	/** Прогресс открытия 0..1 (линейный), из него считаются прозрачность и размер. */
	private float open = 0.0f;
	private boolean closing;
	private long lastNanos = System.nanoTime();

	private float alpha = 0.0f;
	private float scale = 0.9f;
	/** Дополнительный множитель прозрачности (для плавной смены панели настроек). */
	private float extra = 1.0f;
	private float paneAlpha = 1.0f;

	private final Map<String, Float> anims = new HashMap<>();

	/** Экран, на который вернёмся при закрытии (главное меню или null в игре). */
	private final Screen parent;

	public FlyMenuScreen() {
		this(null);
	}

	public FlyMenuScreen(Screen parent) {
		super(Text.literal("FlyVisual"));
		this.parent = parent;
		List<Module> all = ModuleManager.all();
		if (!all.isEmpty()) {
			selected = all.get(0);
			String last = FlyConfig.get().lastModule;
			for (Module m : all) {
				if (m.getName().equals(last)) {
					selected = m;
					break;
				}
			}
			scroll = Math.max(0, FlyConfig.get().lastScroll);
		}
	}

	// ---------- Геометрия ----------

	private int pw() {
		return Math.min(width - 16, MAX_W);
	}

	private int ph() {
		return Math.min(height - 16, MAX_H);
	}

	private int left() {
		return (width - pw()) / 2;
	}

	private int top() {
		return (height - ph()) / 2;
	}

	private int right() {
		return left() + pw();
	}

	private int bottom() {
		return top() + ph();
	}

	private int gx1() {
		return left() + PAD;
	}

	private int gx2() {
		return right() - PAD - PANE_W - PAD;
	}

	private int gy1() {
		return top() + HEAD;
	}

	private int gy2() {
		return bottom() - FOOT;
	}

	private int px1() {
		return right() - PAD - PANE_W;
	}

	private int px2() {
		return right() - PAD;
	}

	private int tileW() {
		return (gx2() - gx1() - GAP * (COLS - 1)) / COLS;
	}

	private int rows() {
		int n = ModuleManager.all().size();
		return (n + COLS - 1) / COLS;
	}

	private int contentH() {
		return rows() * (TILE_H + GAP) - GAP;
	}

	private int viewH() {
		return gy2() - gy1();
	}

	private int maxScroll() {
		return Math.max(0, contentH() - viewH());
	}

	private int tileX(int i) {
		return gx1() + (i % COLS) * (tileW() + GAP);
	}

	private int tileY(int i) {
		return gy1() + (i / COLS) * (TILE_H + GAP) - scroll;
	}

	private int btnX() {
		return right() - PAD - BTN_W;
	}

	private int btnY() {
		return top() + 6;
	}

	private int swatchX(int i) {
		int n = Theme.PRESETS.length + 1;
		return btnX() - 12 - (n * (SW + SW_GAP) - SW_GAP) + i * (SW + SW_GAP);
	}

	private int swatchY() {
		return top() + 8;
	}

	private int pickX() {
		return right() - PAD - PICK_W;
	}

	private int pickY() {
		return top() + HEAD + 2;
	}

	private int pickHeight() {
		return 8 + SV_H + 6 + HUE_H + 8 + 12 + 6;
	}

	private int svX() {
		return pickX() + (PICK_W - SV_W) / 2;
	}

	private int svY() {
		return pickY() + 8;
	}

	private int hueY() {
		return svY() + SV_H + 6;
	}

	private void openPicker() {
		int rgb = FlyConfig.get().accentColor;
		float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
		pickH = hsb[0];
		pickS = hsb[1];
		pickV = hsb[2];
		pickerOpen = true;
	}

	private void applyPicker() {
		FlyConfig.get().accentColor = Color.HSBtoRGB(pickH, pickS, pickV) & 0xFFFFFF;
	}

	private void dragPicker(double mx, double my) {
		if (dragSv) {
			pickS = (float) Math.max(0.0, Math.min(1.0, (mx - svX()) / (double) (SV_W - 1)));
			pickV = 1.0f - (float) Math.max(0.0, Math.min(1.0, (my - svY()) / (double) (SV_H - 1)));
			applyPicker();
		} else if (dragHue) {
			pickH = (float) Math.max(0.0, Math.min(0.999, (mx - svX()) / (double) (SV_W - 1)));
			applyPicker();
		}
	}

	private boolean inPicker(double mx, double my) {
		return mx >= pickX() && mx < pickX() + PICK_W && my >= pickY() && my < pickY() + pickHeight();
	}

	private int settingsTop() {
		return gy1() + 48;
	}

	private int rowY(int k) {
		return settingsTop() + k * ROW_H;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	// ---------- Цвета и анимации ----------

	private static float clamp01(float v) {
		return Math.max(0.0f, Math.min(1.0f, v));
	}

	private static int mulA(int argb, float m) {
		int a = Math.round(((argb >>> 24) & 0xFF) * clamp01(m));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	private static int lerp(int c1, int c2, float t) {
		t = clamp01(t);
		int a = Math.round(((c1 >>> 24) & 0xFF) * (1 - t) + ((c2 >>> 24) & 0xFF) * t);
		int r = Math.round(((c1 >> 16) & 0xFF) * (1 - t) + ((c2 >> 16) & 0xFF) * t);
		int g = Math.round(((c1 >> 8) & 0xFF) * (1 - t) + ((c2 >> 8) & 0xFF) * t);
		int b = Math.round((c1 & 0xFF) * (1 - t) + (c2 & 0xFF) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** Цвет с учётом общей прозрачности меню. */
	private int col(int argb) {
		return mulA(argb, alpha * extra);
	}

	private void rect(DrawContext ctx, int x1, int y1, int x2, int y2, int argb) {
		ctx.fill(x1, y1, x2, y2, col(argb));
	}

	/** Закруглённый прямоугольник с учётом прозрачности меню. */
	private void rr(DrawContext ctx, int x, int y, int w, int h, int r, int argb) {
		UiShapes.roundRect(ctx, x, y, w, h, r, col(argb));
	}

	/** Круглый переключатель: серый выключен, горит цветом темы включён. Без надписей. */
	private void pill(DrawContext ctx, int px, int py, int pw, int ph, float on) {
		if (on > 0.03f) {
			rr(ctx, px - 2, py - 2, pw + 4, ph + 4, ph / 2 + 2, mulA(Theme.accent(), 0.28f * on));
		}
		rr(ctx, px, py, pw, ph, ph / 2, lerp(Theme.offCol(), Theme.accent(), on));
		int knob = ph - 4;
		int kx = px + 2 + Math.round((pw - knob - 4) * on);
		rr(ctx, kx, py + 2, knob, knob, knob / 2, lerp(0xFFB0B0B0, 0xFFFFFFFF, on));
	}

	private void outline(DrawContext ctx, int x1, int y1, int x2, int y2, int argb) {
		rect(ctx, x1, y1, x2, y1 + 1, argb);
		rect(ctx, x1, y2 - 1, x2, y2, argb);
		rect(ctx, x1, y1, x1 + 1, y2, argb);
		rect(ctx, x2 - 1, y1, x2, y2, argb);
	}

	private void text(DrawContext ctx, String s, int x, int y, int argb) {
		int c = col(argb);
		// Ванильный шрифт считает почти прозрачный цвет непрозрачным, поэтому такой текст не рисуем
		if (((c >>> 24) & 0xFF) < 12) return;
		ctx.drawText(textRenderer, s, x, y, c, true);
	}

	private void textRight(DrawContext ctx, String s, int xRight, int y, int argb) {
		text(ctx, s, xRight - textRenderer.getWidth(s), y, argb);
	}

	/** Плавно двигает значение к цели; вызывается раз за кадр на каждый ключ. */
	private float animate(String key, float target, float dt, float speed) {
		float v = anims.getOrDefault(key, target);
		v += (target - v) * (1.0f - (float) Math.exp(-speed * dt));
		if (Math.abs(target - v) < 0.002f) v = target;
		anims.put(key, v);
		return v;
	}

	/** Обрезка по области с учётом масштаба анимации открытия. */
	private void scissor(DrawContext ctx, int x1, int y1, int x2, int y2) {
		float cx = left() + pw() / 2.0f;
		float cy = top() + ph() / 2.0f;
		int a = Math.round(cx + (x1 - cx) * scale);
		int b = Math.round(cy + (y1 - cy) * scale);
		int c = Math.round(cx + (x2 - cx) * scale);
		int d = Math.round(cy + (y2 - cy) * scale);
		ctx.enableScissor(a, b, c, d);
	}

	private void select(Module m) {
		if (selected != m) {
			selected = m;
			paneAlpha = 0.0f;
		}
		if (!m.getName().equals(FlyConfig.get().lastModule)) {
			FlyConfig.get().lastModule = m.getName();
			FlyConfig.save();
		}
	}

	// ---------- Отрисовка ----------

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastNanos) / 1.0e9f, 0.05f);
		lastNanos = now;

		open = clamp01(open + (closing ? -dt : dt) / OPEN_TIME);
		if (closing && open <= 0.0f) {
			// Возвращаемся на экран, откуда открыли (в главном меню это наше главное меню, а не ванильное)
			MinecraftClient.getInstance().setScreen(parent);
			return;
		}
		float inv = 1.0f - open;
		alpha = 1.0f - inv * inv * inv;
		scale = 0.9f + 0.1f * alpha;
		paneAlpha += (1.0f - paneAlpha) * (1.0f - (float) Math.exp(-16.0f * dt));
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
		extra = 1.0f;

		ctx.fill(0, 0, width, height, mulA(0x99000000, alpha));

		MatrixStack ms = ctx.getMatrices();
		ms.push();
		float cx = left() + pw() / 2.0f;
		float cy = top() + ph() / 2.0f;
		ms.translate(cx, cy, 0.0f);
		ms.scale(scale, scale, 1.0f);
		ms.translate(-cx, -cy, 0.0f);

		drawFrame(ctx, mouseX, mouseY);
		String hoverDesc = drawTiles(ctx, mouseX, mouseY, dt);
		drawPane(ctx, mouseX, mouseY, dt);
		drawFooter(ctx, hoverDesc);
		drawPicker(ctx);

		ms.pop();
		super.render(ctx, mouseX, mouseY, delta);
	}

	private void drawFrame(DrawContext ctx, int mouseX, int mouseY) {
		int l = left();
		int t = top();
		int r = right();
		int b = bottom();

		rr(ctx, l, t, r - l, b - t, 8, Theme.panelCol());
		rr(ctx, l + 8, t, r - l - 16, 2, 1, Theme.accent());
		rect(ctx, l, t + HEAD - 1, r, t + HEAD, 0x22FFFFFF);

		int flyW = textRenderer.getWidth("Fly");
		text(ctx, "Fly", l + PAD, t + 11, Theme.accent());
		text(ctx, "Visual", l + PAD + flyW, t + 11, Theme.TEXT);

		// Цвета темы + свой цвет (радужный квадрат)
		int cur = FlyConfig.get().accentColor;
		boolean preset = false;
		for (int c : Theme.PRESETS) {
			if (c == cur) preset = true;
		}
		int n = Theme.PRESETS.length;
		for (int i = 0; i <= n; i++) {
			int sx = swatchX(i);
			int sy = swatchY();
			boolean custom = i == n;
			boolean sel = custom ? (!preset || pickerOpen) : Theme.PRESETS[i] == cur;
			if (sel) {
				rr(ctx, sx - 2, sy - 2, SW + 4, SW + 4, 5, 0xFFFFFFFF);
			}
			if (custom) {
				for (int xx = 0; xx < SW; xx++) {
					rect(ctx, sx + xx, sy + 1, sx + xx + 1, sy + SW - 1, 0xFF000000 | ColorUtil.hsv(xx / (float) SW * 360.0f, 0.85f, 1.0f));
				}
			} else {
				rr(ctx, sx, sy, SW, SW, 4, 0xFF000000 | Theme.PRESETS[i]);
			}
		}

		// Кнопка HUD-редактора
		int bx = btnX();
		int by = btnY();
		boolean hover = mouseX >= bx && mouseX < bx + BTN_W && mouseY >= by && mouseY < by + BTN_H;
		rr(ctx, bx, by, BTN_W, BTN_H, 6, hover ? 0xFF3A3A3E : 0xFF2A2A2E);
		String label = "Редактор HUD";
		text(ctx, label, bx + (BTN_W - textRenderer.getWidth(label)) / 2, by + 4, Theme.TEXT);
	}

	/** Рисует сетку плиток. Возвращает описание модуля под курсором (или null). */
	private String drawTiles(DrawContext ctx, int mouseX, int mouseY, float dt) {
		List<Module> mods = ModuleManager.all();
		String hoverDesc = null;
		int gy1 = gy1();
		int gy2 = gy2();
		int tw = tileW();

		scissor(ctx, gx1() - 2, gy1, gx2() + 2, gy2);
		for (int i = 0; i < mods.size(); i++) {
			Module m = mods.get(i);
			int tx = tileX(i);
			int ty = tileY(i);
			if (ty + TILE_H <= gy1 || ty >= gy2) continue;

			boolean hover = mouseX >= tx && mouseX < tx + tw
					&& mouseY >= Math.max(ty, gy1) && mouseY < Math.min(ty + TILE_H, gy2);
			float en = animate(m.getName() + "#e", m.isEnabled() ? 1.0f : 0.0f, dt, 14.0f);
			float hv = animate(m.getName() + "#h", hover ? 1.0f : 0.0f, dt, 18.0f);

			if (m == selected) {
				rr(ctx, tx - 1, ty - 1, tw + 2, TILE_H + 2, 7, Theme.accent());
			}
			rr(ctx, tx, ty, tw, TILE_H, 6, lerp(Theme.tileCol(), Theme.tileHoverCol(), hv));

			String title = textRenderer.trimToWidth(Lang.module(m.getName()), tw - 14);
			text(ctx, title, tx + 8, ty + 6, lerp(0xFFB8B8B8, Theme.TEXT, en));

			// Переключатель
			pill(ctx, tx + 8, ty + 19, 26, 12, en);

			if (hover) {
				hoverDesc = Lang.description(m.getName(), m.getDescription());
			}
		}
		ctx.disableScissor();

		// Полоса прокрутки
		if (maxScroll() > 0) {
			int vh = viewH();
			int barH = Math.max(12, vh * vh / contentH());
			int barY = gy1 + (vh - barH) * scroll / maxScroll();
			rect(ctx, gx2() + 3, barY, gx2() + 5, barY + barH, 0x60FFFFFF);
		}
		return hoverDesc;
	}

	private void drawPane(DrawContext ctx, int mouseX, int mouseY, float dt) {
		int px1 = px1();
		int px2 = px2();
		int gy1 = gy1();
		int gy2 = gy2();

		rr(ctx, px1, gy1, px2 - px1, gy2 - gy1, 6, Theme.paneCol());
		if (selected == null) {
			String hint = "Выбери модуль";
			text(ctx, hint, px1 + (PANE_W - textRenderer.getWidth(hint)) / 2, gy1 + 20, Theme.TEXT_DIM);
			return;
		}

		extra = paneAlpha;

		text(ctx, textRenderer.trimToWidth(Lang.module(selected.getName()), PANE_W - 16), px1 + 8, gy1 + 8, Theme.accent());

		String desc = Lang.description(selected.getName(), selected.getDescription());
		List<OrderedText> lines = textRenderer.wrapLines(Text.literal(desc), PANE_W - 16);
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			int c = col(Theme.TEXT_DIM);
			if (((c >>> 24) & 0xFF) >= 12) {
				ctx.drawText(textRenderer, lines.get(i), px1 + 8, gy1 + 22 + i * 10, c, true);
			}
		}
		rect(ctx, px1 + 8, settingsTop() - 4, px2 - 8, settingsTop() - 3, 0x22FFFFFF);

		if (selected instanceof MacroModule) {
			drawMacroPane(ctx, mouseX, mouseY);
			extra = 1.0f;
			return;
		}

		List<DoubleSetting> settings = selected.getSettings();
		if (settings.isEmpty()) {
			text(ctx, "Настроек нет, только вкл/выкл", px1 + 8, settingsTop() + 4, Theme.TEXT_DIM);
			extra = 1.0f;
			return;
		}

		scissor(ctx, px1, gy1, px2, gy2);
		for (int k = 0; k < settings.size(); k++) {
			DoubleSetting s = settings.get(k);
			int ry = rowY(k);
			if (ry + ROW_H > gy2) break;

			String label = Lang.setting(s.getName());
			if (s.isToggle()) {
				float on = animate(selected.getName() + "." + s.getName(), s.getBool() ? 1.0f : 0.0f, dt, 16.0f);
				text(ctx, label, px1 + 8, ry + 8, Theme.TEXT);

				pill(ctx, px2 - 8 - 26, ry + 6, 26, 12, on);
			} else {
				text(ctx, label, px1 + 8, ry + 3, Theme.TEXT);
				textRight(ctx, s.format(), px2 - 8, ry + 3, Theme.TEXT_DIM);

				int x1 = px1 + 8;
				int x2 = px2 - 8;
				int cy = ry + 17;
				double frac = (s.get() - s.getMin()) / (s.getMax() - s.getMin());
				int fx = x1 + (int) Math.round(frac * (x2 - x1));
				if (s.getName().endsWith("(hue)")) {
					// Радужная дорожка: выбирай любой оттенок
					for (int xx = x1; xx < x2; xx += 2) {
						float hh = (xx - x1) / (float) (x2 - x1) * 360.0f;
						rect(ctx, xx, cy - 2, Math.min(xx + 2, x2), cy + 2, 0xFF000000 | ColorUtil.hsv(hh, 0.9f, 1.0f));
					}
				} else {
					rr(ctx, x1, cy - 2, x2 - x1, 4, 2, Theme.offCol());
					if (fx > x1) {
						rr(ctx, x1, cy - 2, fx - x1, 4, 2, Theme.accent());
					}
				}
				rr(ctx, fx - 4, cy - 4, 8, 8, 4, 0xFFFFFFFF);
			}
		}
		ctx.disableScissor();
		extra = 1.0f;
	}

	private boolean overMacroButton(double mx, double my) {
		return mx >= px1() + 8 && mx < px2() - 8 && my >= settingsTop() && my < settingsTop() + 18;
	}

	/** Вместо ползунков у макросов кнопка открытия редактора текстов. */
	private void drawMacroPane(DrawContext ctx, int mouseX, int mouseY) {
		int bx = px1() + 8;
		int by = settingsTop();
		int bw = PANE_W - 16;
		boolean hover = overMacroButton(mouseX, mouseY);
		rr(ctx, bx, by, bw, 18, 6, hover ? Theme.accent() : 0xFF2A2A2E);
		String label = "Редактировать макросы";
		text(ctx, label, bx + (bw - textRenderer.getWidth(label)) / 2, by + 5, Theme.TEXT);

		List<OrderedText> lines = textRenderer.wrapLines(
				Text.literal("Клавиши назначаются в Управление, категория FlyVisual."), PANE_W - 16);
		for (int i = 0; i < Math.min(3, lines.size()); i++) {
			int c = col(Theme.TEXT_DIM);
			if (((c >>> 24) & 0xFF) >= 12) {
				ctx.drawText(textRenderer, lines.get(i), bx, by + 26 + i * 10, c, true);
			}
		}
	}

	private void drawPicker(DrawContext ctx) {
		if (!pickerOpen) return;
		int x = pickX();
		int y = pickY();
		rr(ctx, x - 1, y - 1, PICK_W + 2, pickHeight() + 2, 8, 0xFF3A3A3E);
		rr(ctx, x, y, PICK_W, pickHeight(), 7, 0xFF1A1A1E);

		int sx = svX();
		int sy = svY();
		for (int i = 0; i < SV_W; i++) {
			float sat = i / (float) (SV_W - 1);
			int topColor = 0xFF000000 | (Color.HSBtoRGB(pickH, sat, 1.0f) & 0xFFFFFF);
			ctx.fillGradient(sx + i, sy, sx + i + 1, sy + SV_H, col(topColor), col(0xFF000000));
		}
		int cx = sx + Math.round(pickS * (SV_W - 1));
		int cy = sy + Math.round((1.0f - pickV) * (SV_H - 1));
		rr(ctx, cx - 4, cy - 4, 8, 8, 4, 0xFFFFFFFF);
		rr(ctx, cx - 3, cy - 3, 6, 6, 3, 0xFF000000 | (Color.HSBtoRGB(pickH, pickS, pickV) & 0xFFFFFF));

		int hy = hueY();
		for (int i = 0; i < SV_W; i++) {
			rect(ctx, sx + i, hy, sx + i + 1, hy + HUE_H, 0xFF000000 | (Color.HSBtoRGB(i / (float) SV_W, 1.0f, 1.0f) & 0xFFFFFF));
		}
		int hx = sx + Math.round(pickH * (SV_W - 1));
		rr(ctx, hx - 2, hy - 2, 4, HUE_H + 4, 2, 0xFFFFFFFF);

		int rgb = FlyConfig.get().accentColor & 0xFFFFFF;
		rr(ctx, sx, hy + HUE_H + 8, 12, 12, 4, 0xFF000000 | rgb);
		text(ctx, String.format("#%06X", rgb), sx + 18, hy + HUE_H + 10, Theme.TEXT);
	}

	private void drawFooter(DrawContext ctx, String hoverDesc) {
		int fy = bottom() - FOOT + 4;
		if (hoverDesc != null) {
			text(ctx, hoverDesc, left() + PAD, fy, Theme.TEXT);
		} else {
			text(ctx, "ЛКМ: вкл/выкл  |  ПКМ: настройки  |  Колесо: прокрутка  |  Right Shift: закрыть",
					left() + PAD, fy, Theme.TEXT_DIM);
		}
	}

	// ---------- Ввод ----------

	private void dragTo(double mouseX) {
		if (dragging == null) return;
		double x1 = px1() + 8;
		double x2 = px2() - 8;
		double t = Math.max(0.0, Math.min(1.0, (mouseX - x1) / (x2 - x1)));
		dragging.set(dragging.getMin() + t * (dragging.getMax() - dragging.getMin()));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (closing || open < 0.6f) return true;

		if (pickerOpen && button == 0 && inPicker(mouseX, mouseY)) {
			if (mouseX >= svX() && mouseX < svX() + SV_W && mouseY >= svY() && mouseY < svY() + SV_H) {
				dragSv = true;
			} else if (mouseX >= svX() && mouseX < svX() + SV_W && mouseY >= hueY() - 3 && mouseY < hueY() + HUE_H + 3) {
				dragHue = true;
			}
			dragPicker(mouseX, mouseY);
			return true;
		}

		if (button == 0) {
			// Цвет темы
			for (int i = 0; i <= Theme.PRESETS.length; i++) {
				int sx = swatchX(i);
				int sy = swatchY();
				if (mouseX >= sx && mouseX < sx + SW && mouseY >= sy && mouseY < sy + SW) {
					if (i == Theme.PRESETS.length) {
						if (pickerOpen) {
							pickerOpen = false;
						} else {
							openPicker();
						}
					} else {
						FlyConfig.get().accentColor = Theme.PRESETS[i];
						FlyConfig.save();
						pickerOpen = false;
					}
					return true;
				}
			}
			// Клик мимо окна выбора цвета закрывает его
			pickerOpen = false;

			// HUD-редактор
			int bx = btnX();
			int by = btnY();
			if (mouseX >= bx && mouseX < bx + BTN_W && mouseY >= by && mouseY < by + BTN_H) {
				MinecraftClient.getInstance().setScreen(new HudEditorScreen(parent));
				return true;
			}
		}

		// Плитки модулей
		if ((button == 0 || button == 1) && mouseY >= gy1() && mouseY < gy2()) {
			List<Module> mods = ModuleManager.all();
			int tw = tileW();
			for (int i = 0; i < mods.size(); i++) {
				int tx = tileX(i);
				int ty = tileY(i);
				if (mouseX >= tx && mouseX < tx + tw && mouseY >= ty && mouseY < ty + TILE_H) {
					if (button == 0) {
						mods.get(i).toggle();
					}
					select(mods.get(i));
					return true;
				}
			}
		}

		if (button == 0 && selected instanceof MacroModule && overMacroButton(mouseX, mouseY)) {
			MinecraftClient.getInstance().setScreen(new MacroScreen(this));
			return true;
		}

		// Настройки выбранного модуля
		if (button == 0 && selected != null && mouseX >= px1() && mouseX < px2()) {
			List<DoubleSetting> settings = selected.getSettings();
			for (int k = 0; k < settings.size(); k++) {
				int ry = rowY(k);
				if (ry + ROW_H > gy2()) break;
				if (mouseY >= ry && mouseY < ry + ROW_H) {
					DoubleSetting s = settings.get(k);
					if (s.isToggle()) {
						s.flip();
						FlyConfig.save();
					} else if (s.getName().equals("Quality level")) {
						// Уровни 2 и 3: сначала спрашиваем подтверждение
						double x1 = px1() + 8;
						double x2 = px2() - 8;
						double t = Math.max(0.0, Math.min(1.0, (mouseX - x1) / (x2 - x1)));
						int pick = (int) Math.round(s.getMin() + t * (s.getMax() - s.getMin()));
						if (pick != s.getInt()) {
							if (pick >= 1) {
								MinecraftClient.getInstance().setScreen(new LevelConfirmScreen(this, s, pick));
							} else {
								s.set(pick);
								FlyConfig.save();
							}
						}
					} else {
						dragging = s;
						dragTo(mouseX);
					}
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (dragSv || dragHue) {
			dragPicker(mouseX, mouseY);
			return true;
		}
		if (dragging != null) {
			dragTo(mouseX);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (dragSv || dragHue) {
			dragSv = false;
			dragHue = false;
			FlyConfig.save();
			return true;
		}
		if (dragging != null) {
			dragging = null;
			FlyConfig.save();
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (mouseX >= gx1() && mouseX < gx2() + 6 && mouseY >= gy1() && mouseY < gy2()) {
			scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(amount * 16)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	/** Закрытие с анимацией: сам экран закроется, когда меню полностью растворится. */
	@Override
	public void close() {
		FlyConfig.get().lastScroll = scroll;
		FlyConfig.save();
		closing = true;
	}
}
