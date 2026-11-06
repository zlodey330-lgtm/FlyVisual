package com.flyvisual.module;

import com.flyvisual.gui.UiShapes;

import com.flyvisual.config.Theme;
import com.flyvisual.module.ItemSelection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Окно выбора предметов для подсветки. Сверху строка поиска: начни писать (например «незерит»),
 * ниже появятся все подходящие предметы. Клик по предмету включает или выключает его подсветку.
 * Можно выбрать сколько угодно предметов.
 */
public class ItemSearchScreen extends Screen {
	private static final int MAX_W = 320;
	private static final int MAX_H = 260;
	private static final int PAD = 10;
	private static final int HEAD = 26;
	private static final int FIELD_H = 18;
	private static final int ROW_H = 20;
	private static final int FOOT = 30;

	private record Entry(Item item, ItemStack stack, String name, String search) {
	}

	private final Screen parent;
	private final ItemSelection selection;
	private final List<Entry> all = new ArrayList<>();
	private final List<Entry> shown = new ArrayList<>();
	private final StringBuilder query = new StringBuilder();

	private float scrollPos;
	private float scrollTarget;
	private long lastNanos = System.nanoTime();
	private float clearHover;
	private float doneHover;
	private float appear;

	public ItemSearchScreen(Screen parent, ItemSelection selection) {
		super(Text.literal("Выбор предметов"));
		this.parent = parent;
		this.selection = selection;

		for (Item item : Registries.ITEM) {
			if (item == Items.AIR) continue;
			String name = item.getName().getString();
			String id = Registries.ITEM.getId(item).toString();
			all.add(new Entry(item, new ItemStack(item), name, (name + " " + id).toLowerCase(Locale.ROOT)));
		}
		all.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
		refilter();
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

	private int fieldY() {
		return top() + HEAD;
	}

	private int listTop() {
		return fieldY() + FIELD_H + 8;
	}

	private int listBottom() {
		return top() + ph() - FOOT;
	}

	private int maxScroll() {
		return Math.max(0, shown.size() * ROW_H - (listBottom() - listTop()));
	}

	private int btnW() {
		return 80;
	}

	private int btnH() {
		return 16;
	}

	private int clearX() {
		return left() + PAD;
	}

	private int doneX() {
		return left() + pw() - PAD - btnW();
	}

	private int btnY() {
		return top() + ph() - FOOT + 8;
	}

	private boolean inRect(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// ---------- Поиск ----------

	private ItemSelection module() {
		return selection;
	}

	private void refilter() {
		shown.clear();
		String[] words = query.toString().toLowerCase(Locale.ROOT).trim().split("\\s+");
		ItemSelection module = module();

		List<Entry> chosen = new ArrayList<>();
		List<Entry> others = new ArrayList<>();
		for (Entry e : all) {
			boolean match = true;
			for (String w : words) {
				if (!w.isEmpty() && !e.search().contains(w)) {
					match = false;
					break;
				}
			}
			if (!match) continue;
			if (module != null && module.isPicked(e.item())) {
				chosen.add(e);
			} else {
				others.add(e);
			}
		}
		shown.addAll(chosen);
		shown.addAll(others);
		scrollTarget = 0.0f;
		scrollPos = 0.0f;
	}

	// ---------- Отрисовка ----------

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastNanos) / 1.0e9f, 0.05f);
		lastNanos = now;

		appear = Math.min(1.0f, appear + dt / 0.18f);
		scrollTarget = Math.max(0.0f, Math.min(scrollTarget, maxScroll()));
		scrollPos += (scrollTarget - scrollPos) * (1.0f - (float) Math.exp(-16.0f * dt));
		if (Math.abs(scrollTarget - scrollPos) < 0.3f) scrollPos = scrollTarget;

		ctx.fill(0, 0, width, height, UiShapes.mulA(0x99000000, appear));

		int l = left();
		int t = top();
		int w = pw();
		int h = ph();
		int accent = Theme.accent();
		ItemSelection module = module();

		UiShapes.roundRect(ctx, l, t, w, h, 8, 0xF0141416);
		UiShapes.roundRect(ctx, l + 8, t, w - 16, 2, 1, accent);

		ctx.drawText(textRenderer, "Выбор предметов", l + PAD, t + 9, accent, true);
		String count = "Выбрано: " + (module == null ? 0 : module.pickedCount());
		ctx.drawText(textRenderer, count, l + w - PAD - textRenderer.getWidth(count), t + 9, Theme.TEXT_DIM, true);

		// Строка поиска
		int fx = l + PAD;
		int fy = fieldY();
		int fw = w - PAD * 2;
		UiShapes.roundRect(ctx, fx - 1, fy - 1, fw + 2, FIELD_H + 2, 6, UiShapes.mulA(accent, 0.8f));
		UiShapes.roundRect(ctx, fx, fy, fw, FIELD_H, 5, 0xFF1C1C20);
		String typed = query.toString();
		if (typed.isEmpty()) {
			ctx.drawText(textRenderer, "Начни писать название предмета...", fx + 6, fy + 5, 0xFF707078, true);
		} else {
			ctx.drawText(textRenderer, textRenderer.trimToWidth(typed, fw - 14, true), fx + 6, fy + 5, Theme.TEXT, true);
		}
		if ((System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = fx + 6 + Math.min(fw - 14, textRenderer.getWidth(typed));
			ctx.fill(cx, fy + 4, cx + 1, fy + FIELD_H - 4, Theme.TEXT);
		}

		// Список
		int lt = listTop();
		int lb = listBottom();
		int sp = Math.round(scrollPos);
		ctx.enableScissor(l, lt, l + w, lb);
		if (shown.isEmpty()) {
			ctx.drawText(textRenderer, "Ничего не найдено", l + PAD + 4, lt + 8, Theme.TEXT_DIM, true);
		}
		for (int i = 0; i < shown.size(); i++) {
			int ry = lt + i * ROW_H - sp;
			if (ry + ROW_H <= lt || ry >= lb) continue;

			Entry e = shown.get(i);
			boolean on = module != null && module.isPicked(e.item());
			boolean hover = mouseX >= l + PAD && mouseX < l + w - PAD - 6 && mouseY >= Math.max(ry, lt)
					&& mouseY < Math.min(ry + ROW_H, lb);

			int bg = on ? UiShapes.mulA(accent, 0.22f) : (hover ? 0xFF27272C : (i % 2 == 0 ? 0xFF1A1A1E : 0xFF18181C));
			UiShapes.roundRect(ctx, l + PAD, ry + 1, w - PAD * 2 - 6, ROW_H - 2, 5, bg);
			if (on) {
				UiShapes.roundRect(ctx, l + PAD, ry + 1, 2, ROW_H - 2, 1, accent);
			}

			ctx.drawItem(e.stack(), l + PAD + 6, ry + 2);
			String name = textRenderer.trimToWidth(e.name(), w - PAD * 2 - 70);
			ctx.drawText(textRenderer, name, l + PAD + 28, ry + 6, on ? Theme.TEXT : 0xFFD0D0D0, true);

			// Переключатель справа: горит цветом темы, если предмет выбран
			int px = l + w - PAD - 6 - 28;
			int py = ry + 4;
			UiShapes.roundRect(ctx, px, py, 22, 12, 6, on ? accent : 0xFF3A3A3E);
			int kx = on ? px + 11 : px + 1;
			UiShapes.roundRect(ctx, kx, py + 1, 10, 10, 5, on ? 0xFFFFFFFF : 0xFFB0B0B0);
		}
		ctx.disableScissor();

		if (maxScroll() > 0) {
			int vh = lb - lt;
			int barH = Math.max(12, vh * vh / (shown.size() * ROW_H));
			int barY = lt + (vh - barH) * sp / maxScroll();
			ctx.fill(l + w - 7, barY, l + w - 5, barY + barH, 0x60FFFFFF);
		}

		// Кнопки внизу
		ctx.fill(l + PAD, lb + 2, l + w - PAD, lb + 3, 0x22FFFFFF);
		clearHover += ((inRect(mouseX, mouseY, clearX(), btnY(), btnW(), btnH()) ? 1.0f : 0.0f) - clearHover)
				* (1.0f - (float) Math.exp(-14.0f * dt));
		doneHover += ((inRect(mouseX, mouseY, doneX(), btnY(), btnW(), btnH()) ? 1.0f : 0.0f) - doneHover)
				* (1.0f - (float) Math.exp(-14.0f * dt));
		drawButton(ctx, clearX(), btnY(), "Очистить", clearHover, false);
		drawButton(ctx, doneX(), btnY(), "Готово", doneHover, true);

		super.render(ctx, mouseX, mouseY, delta);
	}

	private void drawButton(DrawContext ctx, int x, int y, String label, float hover, boolean primary) {
		int accent = Theme.accent();
		int base = primary ? UiShapes.lerp(accent, 0xFF000000, 0.35f) : 0xFF2A2A2E;
		int lit = primary ? accent : 0xFF3A3A3E;
		UiShapes.roundRect(ctx, x, y, btnW(), btnH(), 6, UiShapes.lerp(base, lit, hover));
		ctx.drawText(textRenderer, label, x + (btnW() - textRenderer.getWidth(label)) / 2, y + 4, Theme.TEXT, true);
	}

	// ---------- Ввод ----------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			if (inRect(mouseX, mouseY, doneX(), btnY(), btnW(), btnH())) {
				close();
				return true;
			}
			if (inRect(mouseX, mouseY, clearX(), btnY(), btnW(), btnH())) {
				ItemSelection module = module();
				if (module != null) {
					module.clearPicked();
				}
				refilter();
				return true;
			}

			int lt = listTop();
			int lb = listBottom();
			int l = left();
			if (mouseX >= l + PAD && mouseX < l + pw() - PAD - 6 && mouseY >= lt && mouseY < lb) {
				int index = (int) Math.floor((mouseY - lt + Math.round(scrollPos)) / ROW_H);
				if (index >= 0 && index < shown.size()) {
					ItemSelection module = module();
					if (module != null) {
						module.togglePicked(shown.get(index).item());
					}
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		scrollTarget = Math.max(0.0f, Math.min(maxScroll(), scrollTarget - (float) amount * ROW_H * 1.5f));
		return true;
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (chr >= ' ' && chr != 127 && query.length() < 40) {
			query.append(chr);
			refilter();
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
			if (query.length() > 0) {
				if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
					query.setLength(0);
				} else {
					query.deleteCharAt(query.length() - 1);
				}
				refilter();
			}
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
			String clip = MinecraftClient.getInstance().keyboard.getClipboard();
			if (clip != null) {
				for (char c : clip.toCharArray()) {
					if (c >= ' ' && c != 127 && query.length() < 40) {
						query.append(c);
					}
				}
				refilter();
			}
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}
}
