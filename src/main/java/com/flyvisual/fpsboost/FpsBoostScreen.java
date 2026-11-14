package com.flyvisual.fpsboost;

import com.flyvisual.config.Theme;
import com.flyvisual.gui.UiShapes;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Меню FPS Boost (кнопка «Настроить» в функции «Оптимизация»). Пять вкладок: визуал и FPS, умная отрисовка,
 * мобы (дальность каждого), частицы и мир, конфиги. Все изменения сохраняются сразу в config/fpsboost.json.
 */
public class FpsBoostScreen extends Screen {
	private static final int PANEL_BG = 0xF0121820;
	private static final int PANEL_BORDER = 0xFF2A3342;
	private static final int ROW = 0xFF1B222D;
	private static final int ROW_HOVER = 0xFF232C3A;
	private static final int TRACK = 0xFF0E131A;
	private static final int TEXT = 0xFFEDEDF2;
	private static final int TEXT_DIM = 0xFF8C93A0;
	private static final int GREEN = 0xFF7FD88C;
	private static final int RED = 0xFFFF6B6B;

	private static final String[] TABS = {"Визуал и FPS", "Умная отрисовка", "Мобы", "Частицы и мир", "Конфиги"};
	private static final int PANEL_W = 460;
	private static final int PANEL_H = 264;
	private static final int TAB_H = 24;
	private static final int SB_W = 6;

	private static int lastTab = 0;

	private final Screen parent;
	private int tab = lastTab;
	private int px;
	private int py;
	private int ph;

	private TextFieldWidget search;
	private TextFieldWidget nameField;
	private final List<Row> rows = new ArrayList<>();
	private double scroll;
	private int totalH;

	private Row dragRow;
	private int dragX;
	private int dragW;
	private boolean draggingBar;
	private double grab;
	private String status = "";

	public FpsBoostScreen(Screen parent) {
		super(Text.literal("FPS Boost"));
		this.parent = parent;
	}

	// ---------- геометрия ----------

	private boolean hasSearch() {
		return tab == 2 || tab == 3 || tab == 4;
	}

	private int viewX() {
		return px + 10;
	}

	private int viewW() {
		return PANEL_W - 20 - SB_W - 4;
	}

	private int viewY() {
		return py + TAB_H + 8 + (hasSearch() ? 24 : 0);
	}

	private int viewH() {
		return py + ph - 16 - viewY();
	}

	private double maxScroll() {
		return Math.max(0, totalH - viewH());
	}

	// ---------- жизненный цикл ----------

	@Override
	protected void init() {
		ph = Math.min(PANEL_H, Math.max(160, this.height - 8));
		px = (this.width - PANEL_W) / 2;
		py = Math.max(4, (this.height - ph) / 2);

		search = new TextFieldWidget(this.textRenderer, px + 10, py + TAB_H + 8, 250, 16, Text.literal("Поиск"));
		search.setMaxLength(32);
		search.setSuggestion("Поиск...");
		search.setChangedListener(s -> {
			search.setSuggestion(s.isEmpty() ? "Поиск..." : "");
			scroll = 0;
			rebuild();
		});
		this.addDrawableChild(search);

		nameField = new TextFieldWidget(this.textRenderer, px + 10, py + TAB_H + 8, 250, 16, Text.literal("Имя конфига"));
		nameField.setMaxLength(24);
		nameField.setSuggestion("Имя нового конфига...");
		nameField.setChangedListener(s -> nameField.setSuggestion(s.isEmpty() ? "Имя нового конфига..." : ""));
		this.addDrawableChild(nameField);

		updateFields();
		rebuild();
	}

	private void updateFields() {
		search.setVisible(tab == 2 || tab == 3);
		search.active = tab == 2 || tab == 3;
		nameField.setVisible(tab == 4);
		nameField.active = tab == 4;
	}

	private void switchTab(int idx) {
		tab = idx;
		lastTab = idx;
		scroll = 0;
		status = "";
		search.setText("");
		updateFields();
		rebuild();
	}

	@Override
	public void close() {
		MobConfig.save();
		if (this.client != null) {
			this.client.setScreen(parent);
		}
	}

	@Override
	public void removed() {
		MobConfig.save();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	// ---------- строки ----------

	private void rebuild() {
		rows.clear();
		switch (tab) {
			case 0 -> buildVisual();
			case 1 -> buildSmart();
			case 2 -> buildMobs();
			case 3 -> buildWorld();
			default -> buildConfigs();
		}
		int h = 4;
		for (Row r : rows) h += r.h;
		totalH = h;
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
	}

	private void toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
		rows.add(new Toggle(label, get, set, "ВКЛ", "ВЫКЛ"));
	}

	private void buildVisual() {
		rows.add(new Heading("ОГОНЬ И СВЕЧЕНИЕ", TEXT_DIM));
		toggle("Убрать огонь на мне", () -> MobConfig.hideFireSelf, v -> MobConfig.hideFireSelf = v);
		toggle("Убрать огонь на других", () -> MobConfig.hideFireOthers, v -> MobConfig.hideFireOthers = v);
		toggle("Убрать свечение (эффект «Свечение»)", () -> MobConfig.hideGlow, v -> MobConfig.hideGlow = v);
		rows.add(new Heading("АНИМАЦИИ", TEXT_DIM));
		toggle("Остановить анимацию воды, лавы, портала", MobConfig::freezeAnimations, MobConfig::setFreezeAnimations);
		rows.add(new Note(() -> "В 1.20.1 замораживаются все анимированные текстуры сразу (вода, лава, портал, огонь)."));
		toggle("Убрать анимацию тотема на экране", () -> MobConfig.hideTotemAnim, v -> MobConfig.hideTotemAnim = v);
		toggle("Убрать трещины при ломании блока", () -> MobConfig.hideBreakAnim, v -> MobConfig.hideBreakAnim = v);
		toggle("Предметы на земле без вращения", () -> MobConfig.hideItemSpin, v -> MobConfig.hideItemSpin = v);
		rows.add(new Heading("КАМЕРА И КАРТИНКА", TEXT_DIM));
		toggle("Убрать тряску камеры при уроне", () -> MobConfig.hideHurtCam, v -> MobConfig.hideHurtCam = v);
		toggle("Убрать блеск зачарований", () -> MobConfig.hideEnchantGlint, v -> MobConfig.hideEnchantGlint = v);
		toggle("Убрать туман под водой и в лаве", () -> MobConfig.hideFog, v -> MobConfig.hideFog = v);
		toggle("Убрать тени под мобами", () -> MobConfig.hideShadows, v -> MobConfig.hideShadows = v);
		toggle("Убрать тыкву на экране", () -> MobConfig.hidePumpkin, v -> MobConfig.hidePumpkin = v);
		toggle("Убрать искажение от портала", () -> MobConfig.hidePortalOverlay, v -> MobConfig.hidePortalOverlay = v);
		rows.add(new Heading("ТАБЛИЧКИ (64 = текст виден отовсюду)", TEXT_DIM));
		rows.add(new Slider("Дальность текста табличек", () -> MobConfig.signDistance, v -> MobConfig.signDistance = v,
				MobConfig.SIGN_MIN, MobConfig.SIGN_MAX, v -> v >= MobConfig.SIGN_MAX ? "∞" : v + " бл."));
		rows.add(new Heading("ПРЕДМЕТЫ В РУКЕ (100% = обычный размер)", TEXT_DIM));
		rows.add(new Slider("Тотем", () -> MobConfig.scaleTotem, v -> MobConfig.scaleTotem = v, 20, 100, v -> v + "%"));
		rows.add(new Slider("Меч", () -> MobConfig.scaleSword, v -> MobConfig.scaleSword = v, 20, 100, v -> v + "%"));
		rows.add(new Slider("Щит", () -> MobConfig.scaleShield, v -> MobConfig.scaleShield = v, 20, 100, v -> v + "%"));
		rows.add(new Slider("Зелье (пока пьёшь)", () -> MobConfig.scalePotion, v -> MobConfig.scalePotion = v, 20, 100, v -> v + "%"));
		rows.add(new Slider("Еда (пока ешь)", () -> MobConfig.scaleFood, v -> MobConfig.scaleFood = v, 20, 100, v -> v + "%"));
		rows.add(new Heading("ПАМЯТЬ", TEXT_DIM));
		rows.add(new Action("Очистка памяти", "Очистить", MemoryCleaner::run,
				() -> "занято " + MemoryCleaner.usedMb() + " / " + MemoryCleaner.maxMb() + " МБ"));
		rows.add(new Note(() -> "Клавишу очистки памяти можно назначить в Настройки > Управление > FlyVisual."));
	}

	private void buildSmart() {
		rows.add(new Heading("УПРОЩЁННЫЕ ДАЛЁКИЕ МОБЫ", TEXT_DIM));
		toggle("Упрощать далёких мобов", () -> MobConfig.simplifyFar, v -> MobConfig.simplifyFar = v);
		rows.add(new Note(() -> "У мобов вдали не рисуются броня, предметы в руках и ник."));
		rows.add(new Note(() -> "Сам моб виден, но отрисовать его дешевле. Игроки не затрагиваются."));
		rows.add(new Slider("Считать далёким от", () -> MobConfig.simplifyDistance, v -> MobConfig.simplifyDistance = v,
				MobConfig.SIMPLIFY_MIN, MobConfig.SIMPLIFY_MAX, v -> v + " бл."));
		rows.add(new Heading("МОБЫ ЗА СТЕНАМИ", TEXT_DIM));
		toggle("Не рисовать мобов за стенами", () -> MobConfig.occlusionCull, v -> MobConfig.occlusionCull = v);
		rows.add(new Note(() -> "Мобы в пещерах и за постройками не рисуются, пока их не видно."));
		rows.add(new Note(() -> "Пропадают с задержкой 0,5 сек, чтобы не выскакивать при повороте."));
		rows.add(new Note(() -> "Игроки и боссы всегда видны."));
	}

	private void buildMobs() {
		rows.add(new Note(() -> "Дальность: 0 = не видно, 128 = без лимита"));
		rows.add(new Pair("Все мобы", "Откл. всё", () -> {
			List<net.minecraft.entity.EntityType<?>> all = new ArrayList<>();
			for (MobCatalog.Mob m : MobCatalog.get()) all.add(m.type());
			MobConfig.disableAll(all);
			MobConfig.save();
			rebuild();
		}, "Вкл. всё", () -> {
			MobConfig.resetAll();
			MobConfig.save();
			rebuild();
		}));
		String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
		MobCatalog.Section last = null;
		boolean any = false;
		for (MobCatalog.Mob mob : MobCatalog.get()) {
			if (!q.isEmpty() && !mob.name().toLowerCase(Locale.ROOT).contains(q)) continue;
			any = true;
			if (mob.section() != last) {
				last = mob.section();
				rows.add(new Heading(last.title, last.color));
			}
			final net.minecraft.entity.EntityType<?> type = mob.type();
			rows.add(new Slider(mob.name(), () -> MobConfig.get(type), v -> MobConfig.set(type, v), 0, MobConfig.MAX,
					v -> v >= MobConfig.MAX ? "∞" : v == 0 ? "выкл" : String.valueOf(v)));
		}
		if (!any) rows.add(new Note(() -> "Ничего не найдено"));
	}

	private void buildWorld() {
		rows.add(new Heading("МИР", TEXT_DIM));
		rows.add(new Toggle("Небо", () -> MobConfig.hideSky, v -> MobConfig.hideSky = v, "убрано", "есть"));
		rows.add(new Toggle("Светила и звёзды", () -> MobConfig.hideSunMoonStars, v -> MobConfig.hideSunMoonStars = v, "убрано", "есть"));
		rows.add(new Toggle("Облака", () -> MobConfig.hideClouds, v -> MobConfig.hideClouds = v, "убрано", "есть"));
		rows.add(new Toggle("Дождь и снег", () -> MobConfig.hideWeather, v -> MobConfig.hideWeather = v, "убрано", "есть"));
		rows.add(new Heading("ЧАСТИЦЫ", TEXT_DIM));
		rows.add(new Pair("Все частицы", "Откл. все", () -> {
			List<net.minecraft.particle.ParticleType<?>> all = new ArrayList<>();
			for (ParticleCatalog.Entry e : ParticleCatalog.get()) all.add(e.type());
			MobConfig.hideAllParticles(all);
			MobConfig.hideBlockParticles = true;
			MobConfig.save();
			rebuild();
		}, "Вкл. все", () -> {
			MobConfig.showAllParticles();
			MobConfig.hideBlockParticles = false;
			MobConfig.save();
			rebuild();
		}));
		rows.add(new Toggle("Частицы блоков (ломание, удары, бег)", () -> !MobConfig.hideBlockParticles,
				v -> MobConfig.hideBlockParticles = !v, "ВКЛ", "ВЫКЛ"));
		String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
		boolean any = false;
		for (ParticleCatalog.Entry e : ParticleCatalog.get()) {
			if (!q.isEmpty() && !e.name().toLowerCase(Locale.ROOT).contains(q) && !e.id().contains(q)) continue;
			any = true;
			final net.minecraft.particle.ParticleType<?> type = e.type();
			rows.add(new Toggle(e.name(), () -> !MobConfig.isParticleHidden(type),
					v -> MobConfig.setParticleHidden(type, !v), "ВКЛ", "ВЫКЛ"));
		}
		if (!any) rows.add(new Note(() -> "Ничего не найдено"));
	}

	private void buildConfigs() {
		rows.add(new Note(() -> "Введи имя вверху и нажми «Сохранить», чтобы запомнить текущие настройки."));
		List<String> names = MobConfig.listProfiles();
		if (names.isEmpty()) {
			rows.add(new Note(() -> "Сохранённых конфигов пока нет."));
		}
		for (String name : names) {
			rows.add(new Pair(name, "Загрузить", () -> {
				status = MobConfig.loadProfile(name) ? "Загружен: " + name : "Не удалось загрузить: " + name;
				rebuild();
			}, "Удалить", () -> {
				status = MobConfig.deleteProfile(name) ? "Удалён: " + name : "Не удалось удалить: " + name;
				rebuild();
			}));
		}
	}

	private void saveProfile() {
		String name = MobConfig.sanitizeName(nameField.getText());
		if (name == null) {
			status = "Введи имя конфига";
			return;
		}
		status = MobConfig.saveProfile(name) ? "Сохранён: " + name : "Не удалось сохранить: " + name;
		nameField.setText("");
		rebuild();
	}

	// ---------- рисование ----------

	@Override
	public void render(DrawContext ctx, int mx, int my, float delta) {
		ctx.fill(0, 0, this.width, this.height, 0x99000000);
		UiShapes.roundRect(ctx, px - 1, py - 1, PANEL_W + 2, ph + 2, 6, PANEL_BORDER);
		UiShapes.roundRect(ctx, px, py, PANEL_W, ph, 6, PANEL_BG);

		int accent = Theme.accent() | 0xFF000000;
		int tw = (PANEL_W - 20) / TABS.length;
		for (int i = 0; i < TABS.length; i++) {
			int x = px + 10 + i * tw;
			boolean active = i == tab;
			boolean hover = mx >= x && mx < x + tw - 2 && my >= py + 4 && my < py + 4 + TAB_H;
			UiShapes.roundRect(ctx, x, py + 4, tw - 2, TAB_H, 4, active ? accent : hover ? ROW_HOVER : ROW);
			ctx.drawCenteredTextWithShadow(this.textRenderer, TABS[i], x + (tw - 2) / 2, py + 4 + (TAB_H - 8) / 2,
					active ? 0xFF101010 : TEXT);
		}

		if (tab == 4) {
			// кнопка «Сохранить» справа от поля имени
			int bx = px + 10 + 256;
			boolean hover = mx >= bx && mx < bx + 84 && my >= py + TAB_H + 8 && my < py + TAB_H + 24;
			UiShapes.roundRect(ctx, bx, py + TAB_H + 8, 84, 16, 4, hover ? accent : ROW_HOVER);
			ctx.drawCenteredTextWithShadow(this.textRenderer, "Сохранить", bx + 42, py + TAB_H + 8 + 4, hover ? 0xFF101010 : TEXT);
		}

		int vx = viewX();
		int vy = viewY();
		int vw = viewW();
		int vh = viewH();
		ctx.enableScissor(vx, vy, vx + vw + SB_W + 4, vy + vh);
		int y = vy + 2 - (int) scroll;
		for (Row r : rows) {
			if (y + r.h >= vy && y <= vy + vh) {
				r.render(ctx, vx, y, vw, mx, my);
			}
			y += r.h;
		}
		ctx.disableScissor();

		if (maxScroll() > 0) {
			int sx = vx + vw + 4;
			ctx.fill(sx, vy, sx + SB_W, vy + vh, TRACK);
			int barH = Math.max(18, (int) (vh * (vh / (double) totalH)));
			int barY = vy + (int) ((vh - barH) * (scroll / maxScroll()));
			UiShapes.roundRect(ctx, sx, barY, SB_W, barH, 3, accent);
		}

		String foot = status.isEmpty() ? "Esc — закрыть. Изменения сохраняются сразу." : status;
		ctx.drawText(this.textRenderer, foot, px + 12, py + ph - 12, status.isEmpty() ? TEXT_DIM : TEXT, false);

		super.render(ctx, mx, my, delta);
	}

	// ---------- ввод ----------

	@Override
	public boolean mouseClicked(double mxD, double myD, int button) {
		int mx = (int) mxD;
		int my = (int) myD;
		if (button == 0) {
			int tw = (PANEL_W - 20) / TABS.length;
			if (my >= py + 4 && my < py + 4 + TAB_H) {
				for (int i = 0; i < TABS.length; i++) {
					int x = px + 10 + i * tw;
					if (mx >= x && mx < x + tw - 2) {
						if (i != tab) switchTab(i);
						return true;
					}
				}
			}
			if (tab == 4) {
				int bx = px + 10 + 256;
				if (mx >= bx && mx < bx + 84 && my >= py + TAB_H + 8 && my < py + TAB_H + 24) {
					saveProfile();
					return true;
				}
			}
			int vx = viewX();
			int vy = viewY();
			int vw = viewW();
			int vh = viewH();
			int sx = vx + vw + 4;
			if (maxScroll() > 0 && mx >= sx - 2 && mx < sx + SB_W + 2 && my >= vy && my < vy + vh) {
				draggingBar = true;
				int barH = Math.max(18, (int) (vh * (vh / (double) totalH)));
				int barY = vy + (int) ((vh - barH) * (scroll / maxScroll()));
				grab = (my >= barY && my < barY + barH) ? my - barY : barH / 2.0;
				dragBar(my);
				return true;
			}
			if (mx >= vx && mx < vx + vw && my >= vy && my < vy + vh) {
				int y = vy + 2 - (int) scroll;
				for (Row r : rows) {
					if (my >= y && my < y + r.h) {
						if (r.click(vx, y, vw, mx, my)) {
							MobConfig.save();
							return true;
						}
						break;
					}
					y += r.h;
				}
			}
		}
		return super.mouseClicked(mxD, myD, button);
	}

	private void dragBar(double my) {
		int vy = viewY();
		int vh = viewH();
		int barH = Math.max(18, (int) (vh * (vh / (double) totalH)));
		double t = (my - grab - vy) / Math.max(1, vh - barH);
		scroll = Math.max(0, Math.min(maxScroll(), t * maxScroll()));
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (draggingBar) {
			dragBar(my);
			return true;
		}
		if (dragRow instanceof Slider s) {
			s.drag((int) mx, dragX, dragW);
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		if (dragRow != null || draggingBar) {
			dragRow = null;
			draggingBar = false;
			MobConfig.save();
			return true;
		}
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double amount) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll - amount * 22.0));
		return true;
	}

	// ---------- виды строк ----------

	private abstract class Row {
		final int h;

		Row(int h) {
			this.h = h;
		}

		abstract void render(DrawContext c, int x, int y, int w, int mx, int my);

		boolean click(int x, int y, int w, int mx, int my) {
			return false;
		}

		String fit(String s, int width) {
			return textRenderer.getWidth(s) <= width ? s : textRenderer.trimToWidth(s, Math.max(0, width - 6)) + "…";
		}
	}

	private final class Heading extends Row {
		private final String text;
		private final int color;

		Heading(String text, int color) {
			super(22);
			this.text = text;
			this.color = color;
		}

		@Override
		void render(DrawContext c, int x, int y, int w, int mx, int my) {
			c.drawText(textRenderer, text, x + 2, y + 10, color, false);
			c.fill(x + 2, y + 20, x + w, y + 21, 0x33FFFFFF);
		}
	}

	private final class Note extends Row {
		private final Supplier<String> text;

		Note(Supplier<String> text) {
			super(14);
			this.text = text;
		}

		@Override
		void render(DrawContext c, int x, int y, int w, int mx, int my) {
			c.drawText(textRenderer, fit(text.get(), w - 8), x + 4, y + 3, TEXT_DIM, false);
		}
	}

	private final class Toggle extends Row {
		private final String label;
		private final BooleanSupplier get;
		private final Consumer<Boolean> set;
		private final String onText;
		private final String offText;

		Toggle(String label, BooleanSupplier get, Consumer<Boolean> set, String onText, String offText) {
			super(22);
			this.label = label;
			this.get = get;
			this.set = set;
			this.onText = onText;
			this.offText = offText;
		}

		@Override
		void render(DrawContext c, int x, int y, int w, int mx, int my) {
			boolean hover = mx >= x && mx < x + w && my >= y && my < y + h - 2;
			UiShapes.roundRect(c, x, y, w, h - 2, 4, hover ? ROW_HOVER : ROW);
			c.drawText(textRenderer, fit(label, w - 86), x + 8, y + (h - 2 - 8) / 2, TEXT, false);
			boolean on = get.getAsBoolean();
			int pw = 60;
			int accent = Theme.accent() | 0xFF000000;
			UiShapes.roundRect(c, x + w - pw - 6, y + 3, pw, h - 8, 4, on ? accent : TRACK);
			c.drawCenteredTextWithShadow(textRenderer, on ? onText : offText, x + w - pw / 2 - 6, y + (h - 2 - 8) / 2,
					on ? 0xFF101010 : TEXT_DIM);
		}

		@Override
		boolean click(int x, int y, int w, int mx, int my) {
			set.accept(!get.getAsBoolean());
			return true;
		}
	}

	private final class Slider extends Row {
		private final String label;
		private final IntSupplier get;
		private final IntConsumer set;
		private final int min;
		private final int max;
		private final IntFunction<String> fmt;

		Slider(String label, IntSupplier get, IntConsumer set, int min, int max, IntFunction<String> fmt) {
			super(22);
			this.label = label;
			this.get = get;
			this.set = set;
			this.min = min;
			this.max = max;
			this.fmt = fmt;
		}

		private int trackX(int x, int w) {
			return x + w - 210;
		}

		private static final int TRACK_W = 140;

		@Override
		void render(DrawContext c, int x, int y, int w, int mx, int my) {
			boolean hover = mx >= x && mx < x + w && my >= y && my < y + h - 2;
			UiShapes.roundRect(c, x, y, w, h - 2, 4, hover ? ROW_HOVER : ROW);
			c.drawText(textRenderer, fit(label, w - 236), x + 8, y + (h - 2 - 8) / 2, TEXT, false);
			int tx = trackX(x, w);
			int ty = y + (h - 2) / 2 - 2;
			int accent = Theme.accent() | 0xFF000000;
			UiShapes.roundRect(c, tx, ty, TRACK_W, 4, 2, TRACK);
			int v = get.getAsInt();
			float t = max == min ? 0f : (v - min) / (float) (max - min);
			int fill = Math.max(4, (int) (TRACK_W * t));
			UiShapes.roundRect(c, tx, ty, fill, 4, 2, accent);
			UiShapes.roundRect(c, tx + fill - 3, ty - 3, 6, 10, 3, 0xFFFFFFFF);
			String txt = fmt.apply(v);
			c.drawText(textRenderer, txt, x + w - 56, y + (h - 2 - 8) / 2, TEXT, false);
		}

		@Override
		boolean click(int x, int y, int w, int mx, int my) {
			int tx = trackX(x, w);
			if (mx >= tx - 4 && mx <= tx + TRACK_W + 4) {
				dragRow = this;
				dragX = x;
				dragW = w;
				drag(mx, x, w);
				return true;
			}
			return false;
		}

		void drag(int mx, int x, int w) {
			int tx = trackX(x, w);
			double t = Math.max(0.0, Math.min(1.0, (mx - tx) / (double) TRACK_W));
			set.accept(min + (int) Math.round(t * (max - min)));
		}
	}

	private final class Action extends Row {
		private final String label;
		private final String button;
		private final Runnable action;
		private final Supplier<String> info;

		Action(String label, String button, Runnable action, Supplier<String> info) {
			super(24);
			this.label = label;
			this.button = button;
			this.action = action;
			this.info = info;
		}

		@Override
		void render(DrawContext c, int x, int y, int w, int mx, int my) {
			UiShapes.roundRect(c, x, y, w, h - 2, 4, ROW);
			c.drawText(textRenderer, label, x + 8, y + 7, TEXT, false);
			c.drawText(textRenderer, info.get(), x + 110, y + 7, TEXT_DIM, false);
			int bx = x + w - 96;
			boolean hover = mx >= bx && mx < bx + 90 && my >= y + 2 && my < y + h - 4;
			UiShapes.roundRect(c, bx, y + 2, 90, h - 6, 4, hover ? (Theme.accent() | 0xFF000000) : ROW_HOVER);
			c.drawCenteredTextWithShadow(textRenderer, button, bx + 45, y + 7, hover ? 0xFF101010 : TEXT);
		}

		@Override
		boolean click(int x, int y, int w, int mx, int my) {
			int bx = x + w - 96;
			if (mx >= bx && mx < bx + 90) {
				action.run();
				return true;
			}
			return false;
		}
	}

	private final class Pair extends Row {
		private final String label;
		private final String textA;
		private final Runnable actionA;
		private final String textB;
		private final Runnable actionB;

		Pair(String label, String textA, Runnable actionA, String textB, Runnable actionB) {
			super(24);
			this.label = label;
			this.textA = textA;
			this.actionA = actionA;
			this.textB = textB;
			this.actionB = actionB;
		}

		@Override
		void render(DrawContext c, int x, int y, int w, int mx, int my) {
			UiShapes.roundRect(c, x, y, w, h - 2, 4, ROW);
			c.drawText(textRenderer, fit(label, w - 200), x + 8, y + 7, TEXT, false);
			drawButton(c, x + w - 192, y, mx, my, textA);
			drawButton(c, x + w - 96, y, mx, my, textB);
		}

		private void drawButton(DrawContext c, int bx, int y, int mx, int my, String text) {
			boolean hover = mx >= bx && mx < bx + 90 && my >= y + 2 && my < y + h - 4;
			UiShapes.roundRect(c, bx, y + 2, 90, h - 6, 4, hover ? (Theme.accent() | 0xFF000000) : ROW_HOVER);
			c.drawCenteredTextWithShadow(textRenderer, text, bx + 45, y + 7, hover ? 0xFF101010 : TEXT);
		}

		@Override
		boolean click(int x, int y, int w, int mx, int my) {
			if (mx >= x + w - 192 && mx < x + w - 102) {
				actionA.run();
				return true;
			}
			if (mx >= x + w - 96 && mx < x + w - 6) {
				actionB.run();
				return true;
			}
			return false;
		}
	}
}
