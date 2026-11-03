package com.flyvisual.gui;

import com.flyvisual.config.Theme;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Список установленных модов. Открывается кнопкой «Моды» в главном меню. */
public class ModsScreen extends Screen {
	private static final int ROW_H = 18;
	private static final int PAD = 10;
	private static final int TITLE_H = 28;
	private static final int BTN_W = 120;
	private static final int BTN_H = 22;

	private record Entry(String name, String version) {
	}

	private final Screen parent;
	private final TitleBackground background = new TitleBackground();
	private final List<Entry> entries = new ArrayList<>();
	private int scroll;
	private float backHover;
	private long lastNanos = System.nanoTime();

	public ModsScreen(Screen parent) {
		super(Text.literal("Моды"));
		this.parent = parent;

		for (ModContainer c : FabricLoader.getInstance().getAllMods()) {
			String id = c.getMetadata().getId();
			// Прячем служебные части (Fabric API состоит из десятков мелких модулей)
			boolean internal = id.equals("java") || id.equals("minecraft") || id.equals("fabricloader")
					|| id.equals("mixinextras") || (id.startsWith("fabric-") && !id.equals("fabric-api"));
			if (internal) continue;
			entries.add(new Entry(c.getMetadata().getName(), c.getMetadata().getVersion().getFriendlyString()));
		}
		entries.sort(Comparator.comparing(e -> e.name().toLowerCase()));
	}

	// ---------- Геометрия ----------

	private int pw() {
		return Math.min(width - 24, 380);
	}

	private int ph() {
		return Math.min(height - 24, 280);
	}

	private int left() {
		return (width - pw()) / 2;
	}

	private int top() {
		return (height - ph()) / 2;
	}

	private int listTop() {
		return top() + TITLE_H;
	}

	private int listBottom() {
		return top() + ph() - BTN_H - PAD * 2;
	}

	private int maxScroll() {
		return Math.max(0, entries.size() * ROW_H - (listBottom() - listTop()));
	}

	private int backX() {
		return (width - BTN_W) / 2;
	}

	private int backY() {
		return top() + ph() - BTN_H - PAD;
	}

	private boolean overBack(double mx, double my) {
		return mx >= backX() && mx < backX() + BTN_W && my >= backY() && my < backY() + BTN_H;
	}

	// ---------- Отрисовка ----------

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastNanos) / 1.0e9f, 0.05f);
		lastNanos = now;
		scroll = Math.max(0, Math.min(scroll, maxScroll()));

		background.render(ctx, width, height, mouseX, mouseY, true);

		int accent = 0xFF000000 | (Theme.accent() & 0xFFFFFF);
		int l = left();
		int t = top();
		int w = pw();
		int h = ph();

		UiShapes.roundRect(ctx, l, t, w, h, 8, UiShapes.lerp(accent, 0xFF000000, 0.25f));
		UiShapes.roundRect(ctx, l + 1, t + 1, w - 2, h - 2, 7, 0xF0141416);

		String title = "Установленные моды (" + entries.size() + ")";
		ctx.drawText(textRenderer, title, l + PAD, t + 11, Theme.accent(), true);

		int lt = listTop();
		int lb = listBottom();
		ctx.fill(l + PAD, lt - 3, l + w - PAD, lt - 2, 0x22FFFFFF);

		ctx.enableScissor(l, lt, l + w, lb);
		for (int i = 0; i < entries.size(); i++) {
			int ry = lt + i * ROW_H - scroll;
			if (ry + ROW_H <= lt || ry >= lb) continue;
			Entry e = entries.get(i);
			if (i % 2 == 0) {
				ctx.fill(l + PAD, ry, l + w - PAD, ry + ROW_H, 0x14FFFFFF);
			}
			String name = textRenderer.trimToWidth(e.name(), w - PAD * 2 - 90);
			ctx.drawText(textRenderer, name, l + PAD + 4, ry + 5, Theme.TEXT, true);
			String ver = textRenderer.trimToWidth(e.version(), 80);
			ctx.drawText(textRenderer, ver, l + w - PAD - 4 - textRenderer.getWidth(ver), ry + 5, Theme.TEXT_DIM, true);
		}
		ctx.disableScissor();

		if (maxScroll() > 0) {
			int vh = lb - lt;
			int barH = Math.max(12, vh * vh / (entries.size() * ROW_H));
			int barY = lt + (vh - barH) * scroll / maxScroll();
			ctx.fill(l + w - 6, barY, l + w - 4, barY + barH, 0x60FFFFFF);
		}

		// Кнопка «Назад»
		float target = overBack(mouseX, mouseY) ? 1.0f : 0.0f;
		backHover += (target - backHover) * (1.0f - (float) Math.exp(-14.0f * dt));
		int bx = backX();
		int by = backY();
		if (backHover > 0.02f) {
			UiShapes.roundRect(ctx, bx - 3, by - 3, BTN_W + 6, BTN_H + 6, 9, UiShapes.mulA(accent, 0.25f * backHover));
		}
		UiShapes.roundRect(ctx, bx, by, BTN_W, BTN_H, 6,
				UiShapes.lerp(UiShapes.lerp(accent, 0xFF000000, 0.25f), accent, backHover));
		UiShapes.roundRect(ctx, bx + 1, by + 1, BTN_W - 2, BTN_H - 2, 5, UiShapes.lerp(0xFF1B1B1F, 0xFF2B2B31, backHover));
		String back = "Назад";
		ctx.drawText(textRenderer, back, bx + (BTN_W - textRenderer.getWidth(back)) / 2, by + (BTN_H - 8) / 2 + 1,
				Theme.TEXT, true);

		super.render(ctx, mouseX, mouseY, delta);
	}

	// ---------- Ввод ----------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && overBack(mouseX, mouseY)) {
			close();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(amount * ROW_H)));
		return true;
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}
}
