package com.flyvisual.gui;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.config.Theme;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Lang;
import com.flyvisual.module.Module;
import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.MacroModule;
import com.flyvisual.module.impl.RecordingModule;
import com.flyvisual.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
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
	private static final int MAX_H = 276;
	private static final int HEAD = 28;
	private static final int FOOT = 30;
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
	private float scrollF;
	private float scrollTarget;
	private Module descMod;
	private int descLines = 2;
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
			scrollF = scroll;
			scrollTarget = scroll;
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

	private static final int CFG_W = 64;

	private int cfgX() {
		return swatchX(0) - 10 - CFG_W;
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
		return gy1() + 30 + descLineCount() * 10;
	}

	/** Сколько строк занимает описание выбранной функции (до 4), считаем один раз на функцию. */
	private int descLineCount() {
		if (selected == null) return 2;
		if (descMod != selected) {
			descMod = selected;
			String d = Lang.description(selected.getName(), selected.getDescription());
			descLines = Math.max(1, Math.min(5, textRenderer.wrapLines(Text.literal(d), PANE_W - 16).size()));
		}
		return descLines;
	}

	/** Высота блока с кнопками вверху настроек (у записи это «Начать запись» и «Автонастройка»). */
	private static final int REC_HEAD_H = 90;

	private int settingsScroll;

	private int headerH() {
		return selected instanceof RecordingModule ? REC_HEAD_H : 0;
	}

	private int rowY(int k) {
		return settingsTop() + headerH() - settingsScroll + k * ROW_H;
	}

	/** Насколько можно прокрутить список настроек (если он не помещается в панель). */
	private int settingsMaxScroll() {
		if (selected == null) return 0;
		int total = headerH() + selected.getSettings().size() * ROW_H;
		return Math.max(0, total - (gy2() - settingsTop()) + 2);
	}

	/** Красивый блок записи: большая кнопка начала и остановки, автонастройка с полосой хода, статус. */
	private void drawRecordHeader(DrawContext ctx, RecordingModule rec, int mouseX, int mouseY, float dt) {
		int x1 = px1() + 8;
		int w = px2() - px1() - 16;
		int y0 = settingsTop() - settingsScroll;
		long now = System.nanoTime();
		boolean recording = rec.isRecording();
		boolean tuning = rec.isTuning();

		// 1. Большая кнопка записи (красная, когда идёт запись)
		int by = y0 + 2;
		int bh = 30;
		boolean bHover = mouseX >= x1 && mouseX < x1 + w && mouseY >= by && mouseY < by + bh;
		float bv = animate("rec#start", bHover ? 1.0f : 0.0f, dt, 16.0f);
		int red = 0xFFE5484D;
		int base = recording ? red : MenuStyles.accent();
		float pulse = recording ? 0.5f + 0.5f * (float) Math.sin(now / 1.0e9 * 5.0) : 0.0f;
		rr(ctx, x1 - 2, by - 2, w + 4, bh + 4, 11, mulA(base, 0.14f + 0.26f * bv + 0.28f * pulse));
		rr(ctx, x1, by, w, bh, 9, lerp(MenuStyles.tile(), base, 0.55f + 0.38f * bv));
		int cx = x1 + 19;
		int cy = by + bh / 2;
		if (recording) {
			rr(ctx, cx - 6, cy - 6, 12, 12, 3, 0xFFFFFFFF);
		} else {
			rr(ctx, cx - 8, cy - 8, 16, 16, 8, 0xFFFFFFFF);
			rr(ctx, cx - 5, cy - 5, 10, 10, 5, red);
		}
		String startLabel;
		if (recording) {
			long sec = rec.recordingSeconds();
			startLabel = String.format(java.util.Locale.ROOT, "Остановить  %02d:%02d", sec / 60, sec % 60);
		} else {
			startLabel = "Начать запись";
		}
		text(ctx, startLabel, x1 + 36, by + (bh - 8) / 2, 0xFFFFFFFF);
		textRight(ctx, "F9", x1 + w - 9, by + (bh - 8) / 2, 0xB0FFFFFF);

		// 2. Автонастройка: во время подбора полоса показывает ход
		int ay = by + bh + 6;
		int ah = 26;
		boolean aHover = !tuning && mouseX >= x1 && mouseX < x1 + w && mouseY >= ay && mouseY < ay + ah;
		float av = animate("rec#tune", aHover ? 1.0f : 0.0f, dt, 16.0f);
		rr(ctx, x1, ay, w, ah, 8, lerp(MenuStyles.tile(), MenuStyles.tileHover(), av));
		if (tuning) {
			int fill = Math.max(10, (int) (w * clamp01(rec.tuneProgress())));
			rr(ctx, x1, ay, fill, ah, 8, mulA(MenuStyles.accent(), 0.55f));
		}
		rr(ctx, x1, ay, 4, ah, 2, MenuStyles.accent());
		String tuneLabel = tuning ? "Подбор настроек… " + (int) (rec.tuneProgress() * 100.0f) + "%" : "Автонастройка под мой ПК";
		text(ctx, tuneLabel, x1 + 14, ay + (ah - 8) / 2, MenuStyles.text());

		// 3. Статус: ход подбора, результат или подсказка
		String status = rec.tuneStatus();
		if (status == null || status.isEmpty()) {
			status = "Подберёт качество, разрешение и FPS так, чтобы не лагало ни запись, ни игра";
		}
		java.util.List<net.minecraft.text.OrderedText> lines = textRenderer.wrapLines(net.minecraft.text.Text.literal(status), w);
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			ctx.draw();
			ctx.drawText(textRenderer, lines.get(i), x1 + 2, ay + ah + 7 + i * 10, MenuStyles.textDim(), false);
			ctx.draw();
		}
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
			rr(ctx, px - 2, py - 2, pw + 4, ph + 4, ph / 2 + 2, mulA(MenuStyles.accent(), 0.28f * on));
		}
		rr(ctx, px, py, pw, ph, ph / 2, lerp(MenuStyles.off(), MenuStyles.accent(), on));
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
		ctx.draw(); // порядок слоёв: всё нарисованное раньше уходит на экран до текста
		ctx.drawText(textRenderer, s, x, y, c, true);
		ctx.draw();
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

	/** Обрезает текст с «…», если он шире max пикселей. */
	private String fit(String s, int max) {
		if (textRenderer.getWidth(s) <= max) return s;
		return textRenderer.trimToWidth(s, Math.max(0, max - textRenderer.getWidth("…"))) + "…";
	}

	private void select(Module m) {
		if (selected != m) {
			selected = m;
			paneAlpha = 0.0f;
			settingsScroll = 0;
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
		scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll()));
		scrollF += (scrollTarget - scrollF) * (1.0f - (float) Math.exp(-14.0f * dt));
		if (Math.abs(scrollTarget - scrollF) < 0.4f) scrollF = scrollTarget;
		scroll = Math.max(0, Math.min(maxScroll(), Math.round(scrollF)));
		extra = 1.0f;

		MenuStyles.update(dt);
		MenuStyles.drawBackground(ctx, width, height, alpha);

		MatrixStack ms = ctx.getMatrices();
		ms.push();
		float cx = left() + pw() / 2.0f;
		float cy = top() + ph() / 2.0f;
		ms.translate(cx, cy, 0.0f);
		ms.scale(scale, scale, 1.0f);
		ms.translate(-cx, -cy, 0.0f);

		// Рисуем пачками: меньше вызовов отрисовки, меню не роняет FPS
		ctx.draw(() -> drawFrame(ctx, mouseX, mouseY));
		String[] hoverBox = new String[1];
		ctx.draw(() -> hoverBox[0] = drawTiles(ctx, mouseX, mouseY, dt));
		ctx.draw(() -> drawPane(ctx, mouseX, mouseY, dt));
		drawFooter(ctx, hoverBox[0], mouseX, mouseY, dt);
		drawPicker(ctx);
		MenuStyles.drawFront(ctx, left(), top(), right(), bottom(), alpha, mouseX, mouseY);

		ms.pop();
		super.render(ctx, mouseX, mouseY, delta);
	}

	private void drawFrame(DrawContext ctx, int mouseX, int mouseY) {
		int l = left();
		int t = top();
		int r = right();
		int b = bottom();

		rr(ctx, l, t, r - l, b - t, 8, MenuStyles.panel());
		scissor(ctx, l, t, r, b);
		MenuStyles.drawPanelDecor(ctx, l, t, r, b, alpha);
		ctx.disableScissor();
		MenuStyles.drawBorder(ctx, l, t, r, b, alpha);
		rr(ctx, l + 8, t, r - l - 16, 2, 1, MenuStyles.accent());
		rect(ctx, l, t + HEAD - 1, r, t + HEAD, MenuStyles.line());

		int flyW = textRenderer.getWidth("Fly");
		text(ctx, "Fly", l + PAD, t + 11, MenuStyles.accent());
		text(ctx, "Visual", l + PAD + flyW, t + 11, MenuStyles.text());

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
		rr(ctx, bx, by, BTN_W, BTN_H, 6, hover ? MenuStyles.btnHover() : MenuStyles.btn());
		String label = "Редактор HUD";
		text(ctx, label, bx + (BTN_W - textRenderer.getWidth(label)) / 2, by + 4, MenuStyles.text());

		int cx = cfgX();
		boolean hov2 = mouseX >= cx && mouseX < cx + CFG_W && mouseY >= by && mouseY < by + BTN_H;
		rr(ctx, cx, by, CFG_W, BTN_H, 6, hov2 ? MenuStyles.btnHover() : MenuStyles.btn());
		String l2 = "Конфиги";
		text(ctx, l2, cx + (CFG_W - textRenderer.getWidth(l2)) / 2, by + 4, MenuStyles.text());
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
				rr(ctx, tx - 1, ty - 1, tw + 2, TILE_H + 2, 7, MenuStyles.accent());
			}
			rr(ctx, tx, ty, tw, TILE_H, 6, lerp(MenuStyles.tile(), MenuStyles.tileHover(), hv));

			String title = textRenderer.trimToWidth(Lang.module(m.getName()), tw - 14);
			text(ctx, title, tx + 8, ty + 6, lerp(MenuStyles.textOff(), MenuStyles.text(), en));

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

		rr(ctx, px1, gy1, px2 - px1, gy2 - gy1, 6, MenuStyles.pane());
		if (selected == null) {
			String hint = "Выбери модуль";
			text(ctx, hint, px1 + (PANE_W - textRenderer.getWidth(hint)) / 2, gy1 + 20, MenuStyles.textDim());
			return;
		}

		extra = paneAlpha;

		text(ctx, textRenderer.trimToWidth(Lang.module(selected.getName()), PANE_W - 16), px1 + 8, gy1 + 8, MenuStyles.accent());

		String desc = Lang.description(selected.getName(), selected.getDescription());
		List<OrderedText> lines = textRenderer.wrapLines(Text.literal(desc), PANE_W - 16);
		for (int i = 0; i < Math.min(5, lines.size()); i++) {
			int c = col(MenuStyles.textDim());
			if (((c >>> 24) & 0xFF) >= 12) {
				ctx.draw();
				ctx.drawText(textRenderer, lines.get(i), px1 + 8, gy1 + 22 + i * 10, c, true);
				ctx.draw();
			}
		}
		rect(ctx, px1 + 8, settingsTop() - 4, px2 - 8, settingsTop() - 3, MenuStyles.line());

		if (selected instanceof MacroModule) {
			drawMacroPane(ctx, mouseX, mouseY);
			extra = 1.0f;
			return;
		}

		List<DoubleSetting> settings = selected.getSettings();
		if (settings.isEmpty()) {
			text(ctx, "Настроек нет, только вкл/выкл", px1 + 8, settingsTop() + 4, MenuStyles.textDim());
			extra = 1.0f;
			return;
		}

		settingsScroll = Math.max(0, Math.min(settingsMaxScroll(), settingsScroll));
		scissor(ctx, px1, settingsTop() - 3, px2, gy2);
		if (selected instanceof RecordingModule rec) {
			drawRecordHeader(ctx, rec, mouseX, mouseY, dt);
		}
		for (int k = 0; k < settings.size(); k++) {
			DoubleSetting s = settings.get(k);
			int ry = rowY(k);
			if (ry + ROW_H <= settingsTop() - 3) continue;
			if (ry >= gy2) break;

			String label = Lang.setting(s.getName());
			if (s.isButton()) {
				int bx1 = px1 + 8;
				int bw = px2 - px1 - 16;
				boolean bh = mouseX >= bx1 && mouseX < bx1 + bw && mouseY >= ry + 1 && mouseY < ry + ROW_H - 1
						&& mouseY >= gy1 && mouseY < gy2;
				float hv = animate(selected.getName() + "." + s.getName() + "#b", bh ? 1.0f : 0.0f, dt, 18.0f);
				rr(ctx, bx1 - 1, ry + 1, bw + 2, ROW_H - 2, 7, mulA(MenuStyles.accent(), 0.20f + 0.35f * hv));
				rr(ctx, bx1, ry + 2, bw, ROW_H - 4, 6, lerp(MenuStyles.tile(), MenuStyles.accent(), 0.45f + 0.4f * hv));
				String bl = label + "  >";
				text(ctx, bl, bx1 + (bw - textRenderer.getWidth(bl)) / 2, ry + 8, 0xFFFFFFFF);
			} else if (s.isToggle()) {
				float on = animate(selected.getName() + "." + s.getName(), s.getBool() ? 1.0f : 0.0f, dt, 16.0f);
				text(ctx, fit(label, px2 - px1 - 16 - 26 - 8), px1 + 8, ry + 8, MenuStyles.text());

				pill(ctx, px2 - 8 - 26, ry + 6, 26, 12, on);
			} else {
				String val = fit(s.format(), 70);
				text(ctx, fit(label, px2 - px1 - 16 - textRenderer.getWidth(val) - 8), px1 + 8, ry + 3, MenuStyles.text());
				textRight(ctx, val, px2 - 8, ry + 3, MenuStyles.textDim());

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
					rr(ctx, x1, cy - 2, x2 - x1, 4, 2, MenuStyles.off());
					if (fx > x1) {
						rr(ctx, x1, cy - 2, fx - x1, 4, 2, MenuStyles.accent());
					}
				}
				rr(ctx, fx - 4, cy - 4, 8, 8, 4, 0xFFFFFFFF);
			}
			if (!selected.isEnabled() && !s.isButton()) {
				// Функция выключена: настройки затемнены и не меняются
				rect(ctx, px1 + 4, ry, px2 - 4, ry + ROW_H, MenuStyles.disabled());
			}
		}
		ctx.disableScissor();
		extra = 1.0f;
	}

	private static final int MROW = 20;
	private int macroFocus = -1;
	private int macroBind = -1;

	private int mFieldX() {
		return px1() + 8;
	}

	private int mFieldW() {
		return PANE_W - 16 - 50;
	}

	private int mKeyX() {
		return px2() - 8 - 46;
	}

	private int mRowY(int i) {
		return settingsTop() + i * MROW;
	}

	/** Какая строка макросов под мышкой (-1 нет) и что именно: 0 поле, 1 клавиша. */
	private int[] macroHit(double mx, double my) {
		for (int i = 0; i < MacroModule.COUNT; i++) {
			int ry = mRowY(i);
			if (ry + 16 > gy2()) break;
			if (my >= ry && my < ry + 16) {
				if (mx >= mFieldX() && mx < mFieldX() + mFieldW()) return new int[]{i, 0};
				if (mx >= mKeyX() && mx < mKeyX() + 46) return new int[]{i, 1};
			}
		}
		return new int[]{-1, -1};
	}

	private String macroText(int i) {
		String[] m = FlyConfig.get().macros;
		return m != null && i < m.length && m[i] != null ? m[i] : "";
	}

	private void setMacroText(int i, String s) {
		FlyConfig cfg = FlyConfig.get();
		if (cfg.macros == null || cfg.macros.length < MacroModule.COUNT) {
			String[] n = new String[MacroModule.COUNT];
			for (int k = 0; k < n.length; k++) n[k] = macroText(k);
			cfg.macros = n;
		}
		cfg.macros[i] = s;
		FlyConfig.save();
	}

	private int macroKey(int i) {
		int[] k = FlyConfig.get().macroKeys;
		return k != null && i < k.length ? k[i] : 0;
	}

	private void setMacroKey(int i, int code) {
		FlyConfig cfg = FlyConfig.get();
		if (cfg.macroKeys == null || cfg.macroKeys.length < MacroModule.COUNT) cfg.macroKeys = new int[MacroModule.COUNT];
		cfg.macroKeys[i] = code;
		FlyConfig.save();
	}

	/** Макросы прямо в меню: строка = поле текста/команды + кнопка клавиши. */
	private void drawMacroPane(DrawContext ctx, int mouseX, int mouseY) {
		boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
		for (int i = 0; i < MacroModule.COUNT; i++) {
			int ry = mRowY(i);
			if (ry + 16 > gy2()) break;
			int fx = mFieldX();
			int fw = mFieldW();
			boolean focus = macroFocus == i;
			rr(ctx, fx - 1, ry - 1, fw + 2, 18, 5, focus ? mulA(MenuStyles.accent(), 0.8f) : 0xFF2A2A2E);
			rr(ctx, fx, ry, fw, 16, 4, 0xFF1C1C20);
			String s = macroText(i);
			if (s.isEmpty() && !focus) {
				text(ctx, "Текст или /команда", fx + 4, ry + 4, 0xFF666670);
			} else {
				String shown = s;
				while (shown.length() > 0 && textRenderer.getWidth(shown) > fw - 10) shown = shown.substring(1);
				text(ctx, shown, fx + 4, ry + 4, MenuStyles.text());
				if (focus && blink) {
					rect(ctx, fx + 4 + textRenderer.getWidth(shown), ry + 3, fx + 5 + textRenderer.getWidth(shown), ry + 13, MenuStyles.text());
				}
			}
			int kx = mKeyX();
			boolean binding = macroBind == i;
			boolean hover = mouseX >= kx && mouseX < kx + 46 && mouseY >= ry && mouseY < ry + 16;
			rr(ctx, kx, ry, 46, 16, 4, binding ? MenuStyles.accent() : (hover ? 0xFF3A3A40 : 0xFF2A2A2E));
			String label;
			if (binding) {
				label = "...";
			} else if (macroKey(i) == 0) {
				label = "Клавиша";
			} else {
				label = macroKey(i) >= 1000 ? "Мышь " + (macroKey(i) - 999)
						: InputUtil.fromKeyCode(macroKey(i), 0).getLocalizedText().getString();
			}
			label = textRenderer.trimToWidth(label, 40);
			text(ctx, label, kx + (46 - textRenderer.getWidth(label)) / 2, ry + 4, MenuStyles.text());
		}
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (macroFocus >= 0 && chr >= 32 && chr != 127) {
			String s = macroText(macroFocus);
			if (s.length() < 256) setMacroText(macroFocus, s + chr);
			return true;
		}
		return super.charTyped(chr, modifiers);
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
		text(ctx, String.format("#%06X", rgb), sx + 18, hy + HUE_H + 10, MenuStyles.text());
	}

	private void drawFooter(DrawContext ctx, String hoverDesc, int mouseX, int mouseY, float dt) {
		int fy = bottom() - FOOT + 2;
		int chip = chipAt(mouseX, mouseY);
		if (chip >= 0) {
			text(ctx, fit(MenuStyles.DESCRIPTIONS[chip], right() - left() - 2 * PAD), left() + PAD, fy, MenuStyles.text());
		} else if (hoverDesc != null) {
			text(ctx, fit(hoverDesc, right() - left() - 2 * PAD), left() + PAD, fy, MenuStyles.text());
		} else if (selected != null && !selected.isEnabled() && !selected.getSettings().isEmpty()
				&& !(selected instanceof MacroModule)) {
			text(ctx, fit("Включи функцию «" + Lang.module(selected.getName()) + "», чтобы менять её настройки",
					right() - left() - 2 * PAD), left() + PAD, fy, 0xFFFF6B6B);
		} else {
			text(ctx, fit("ЛКМ: вкл/выкл  |  ПКМ: настройки  |  Колесо: прокрутка  |  Right Shift: закрыть",
					right() - left() - 2 * PAD), left() + PAD, fy, MenuStyles.textDim());
		}
		drawStyleBar(ctx, mouseX, mouseY, dt);
	}

	// ---------- Переключатель стилей оформления (самый низ меню) ----------

	private static final int CHIP_H = 12;

	private int styleBarY() {
		return bottom() - CHIP_H - 4;
	}

	private int styleLabelW() {
		return textRenderer.getWidth("Стиль") + 10;
	}

	/** Отступ внутри кнопки стиля: на узком экране кнопки становятся компактнее. */
	private int chipPad() {
		int total = styleLabelW();
		for (int i = 0; i < MenuStyles.COUNT; i++) {
			total += textRenderer.getWidth(MenuStyles.NAMES[i]) + 12 + 4;
		}
		return total <= pw() - 2 * PAD ? 12 : 6;
	}

	private int chipW(int i) {
		return textRenderer.getWidth(MenuStyles.NAMES[i]) + chipPad();
	}

	private int chipX(int i) {
		int x = left() + PAD + styleLabelW();
		for (int k = 0; k < i; k++) {
			x += chipW(k) + 4;
		}
		return x;
	}

	/** Номер стиля под мышкой или -1. */
	private int chipAt(double mx, double my) {
		int y = styleBarY();
		if (my < y || my >= y + CHIP_H) return -1;
		for (int i = 0; i < MenuStyles.COUNT; i++) {
			int x = chipX(i);
			if (mx >= x && mx < x + chipW(i)) return i;
		}
		return -1;
	}

	private void drawStyleBar(DrawContext ctx, int mouseX, int mouseY, float dt) {
		int y = styleBarY();
		int cur = MenuStyles.current();
		rect(ctx, left() + PAD, y - 3, right() - PAD, y - 2, MenuStyles.line());
		text(ctx, "Стиль", left() + PAD, y + 2, MenuStyles.textDim());

		// Подсветка выбранного стиля плавно переезжает на новую кнопку
		float hx = animate("style#x", chipX(cur), dt, 14.0f);
		float hw = animate("style#w", chipW(cur), dt, 14.0f);
		int ihx = Math.round(hx);
		int ihw = Math.round(hw);
		rr(ctx, ihx - 1, y - 1, ihw + 2, CHIP_H + 2, 7, mulA(MenuStyles.accent(), 0.22f));
		rr(ctx, ihx, y, ihw, CHIP_H, 6, mulA(MenuStyles.accent(), 0.30f));
		rr(ctx, ihx + ihw / 2 - 6, y + CHIP_H - 2, 12, 2, 1, MenuStyles.accent());

		int hover = chipAt(mouseX, mouseY);
		for (int i = 0; i < MenuStyles.COUNT; i++) {
			int x = chipX(i);
			int cw = chipW(i);
			float hv = animate("style#h" + i, i == hover ? 1.0f : 0.0f, dt, 18.0f);
			if (hv > 0.02f && i != cur) {
				rr(ctx, x, y, cw, CHIP_H, 6, mulA(MenuStyles.btnHover(), hv));
			}
			int tc = i == cur ? MenuStyles.text() : lerp(MenuStyles.textDim(), MenuStyles.text(), hv);
			text(ctx, MenuStyles.NAMES[i], x + (cw - textRenderer.getWidth(MenuStyles.NAMES[i])) / 2, y + 2, tc);
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
			int chip = chipAt(mouseX, mouseY);
			if (chip >= 0) {
				MenuStyles.select(chip);
				pickerOpen = false;
				return true;
			}
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
			int cx = cfgX();
			if (mouseX >= cx && mouseX < cx + CFG_W && mouseY >= by && mouseY < by + BTN_H) {
				MinecraftClient.getInstance().setScreen(new ConfigsScreen(this));
				return true;
			}
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

		if (selected instanceof MacroModule) {
			if (macroBind >= 0) {
				// любая мышь отменяет ожидание клавиши (боковые кнопки мыши тоже можно назначить)
				if (button >= 2) setMacroKey(macroBind, 1000 + button);
				macroBind = -1;
				return true;
			}
			if (button == 0 && mouseX >= px1() && mouseX < px2()) {
				int[] hit = macroHit(mouseX, mouseY);
				macroFocus = hit[1] == 0 ? hit[0] : -1;
				macroBind = hit[1] == 1 ? hit[0] : -1;
				return true;
			}
		}

		// Настройки выбранного модуля
		if (button == 0 && selected != null && mouseX >= px1() && mouseX < px2()) {
			List<DoubleSetting> settings = selected.getSettings();
			if (selected instanceof RecordingModule rec) {
				int hy = settingsTop() - settingsScroll;
				boolean inView = mouseY >= settingsTop() - 3 && mouseY < gy2();
				if (inView && mouseY >= hy + 2 && mouseY < hy + 32) {
					rec.toggleRecording();
					return true;
				}
				if (inView && mouseY >= hy + 38 && mouseY < hy + 64) {
					rec.startTune();
					return true;
				}
			}
			for (int k = 0; k < settings.size(); k++) {
				int ry = rowY(k);
				if (ry >= gy2()) break;
				if (ry + ROW_H <= settingsTop()) continue;
				if (mouseY >= ry && mouseY < ry + ROW_H && mouseY >= settingsTop() - 3 && mouseY < gy2()) {
					DoubleSetting s = settings.get(k);
					if (s.isButton()) {
						s.press();
					} else if (!selected.isEnabled()) {
						// Настройки работают только у включённой функции (подсказка внизу)
						return true;
					} else if (s.isToggle()) {
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
		if (selected != null && !(selected instanceof MacroModule) && mouseX >= px1() && mouseX < px2()
				&& mouseY >= settingsTop() && mouseY < gy2() && settingsMaxScroll() > 0) {
			settingsScroll = Math.max(0, Math.min(settingsMaxScroll(), settingsScroll - (int) (amount * 18.0)));
			return true;
		}
		if (mouseX >= gx1() && mouseX < gx2() + 6 && mouseY >= gy1() && mouseY < gy2()) {
			scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - (float) (amount * 40.0)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (selected instanceof MacroModule) {
			if (macroBind >= 0) {
				if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
					// отмена
				} else if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
					setMacroKey(macroBind, 0);
				} else {
					setMacroKey(macroBind, keyCode);
				}
				macroBind = -1;
				return true;
			}
			if (macroFocus >= 0) {
				String s = macroText(macroFocus);
				if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
					macroFocus = -1;
				} else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
					if (!s.isEmpty()) setMacroText(macroFocus, s.substring(0, s.length() - 1));
				} else if (Screen.isPaste(keyCode)) {
					String clip = MinecraftClient.getInstance().keyboard.getClipboard().replaceAll("[\\r\\n]", " ");
					setMacroText(macroFocus, (s + clip).substring(0, Math.min(256, s.length() + clip.length())));
				} else if (Screen.isSelectAll(keyCode) || Screen.isCopy(keyCode) || Screen.isCut(keyCode)) {
					if (Screen.isCopy(keyCode) || Screen.isCut(keyCode)) {
						MinecraftClient.getInstance().keyboard.setClipboard(s);
						if (Screen.isCut(keyCode)) setMacroText(macroFocus, "");
					}
				}
				return true;
			}
		}
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
