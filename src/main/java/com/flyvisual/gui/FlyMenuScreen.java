package com.flyvisual.gui;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.config.Theme;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Главное меню FlyVisual (Right Shift).
 * ЛКМ по модулю: включить/выключить. ПКМ (или стрелка): открыть настройки с ползунками.
 * Колесо мыши: прокрутка списка. Внизу: цвет темы и кнопка HUD-редактора.
 */
public class FlyMenuScreen extends Screen {
	private static final int PANEL_W = 270;
	private static final int ROW_H = 20;
	private static final int SET_H = 18;
	private static final int PAD = 10;
	private static final int HEADER_H = 28;
	private static final int FOOTER_H = 46;
	private static final int SWATCH = 14;
	private static final int SWATCH_GAP = 6;
	private static final int BTN_W = 70;
	private static final int BTN_H = 14;
	private static final int PILL_W = 28;

	private record Row(Module module, DoubleSetting setting, int y, int h) {
	}

	private Module expanded;
	private int scroll;
	private DoubleSetting dragging;

	public FlyMenuScreen() {
		super(Text.literal("FlyVisual"));
	}

	// ---------- Геометрия ----------

	private int contentHeight() {
		int h = 0;
		for (Module m : ModuleManager.all()) {
			h += ROW_H;
			if (m == expanded) {
				h += m.getSettings().size() * SET_H;
			}
		}
		return h;
	}

	private int panelH() {
		return Math.min(HEADER_H + contentHeight() + FOOTER_H, height - 16);
	}

	private int viewH() {
		return panelH() - HEADER_H - FOOTER_H;
	}

	private int maxScroll() {
		return Math.max(0, contentHeight() - viewH());
	}

	private int left() {
		return (width - PANEL_W) / 2;
	}

	private int top() {
		return (height - panelH()) / 2;
	}

	private int listTop() {
		return top() + HEADER_H;
	}

	private int listBottom() {
		return top() + panelH() - FOOTER_H;
	}

	private int sliderX1() {
		return left() + 112;
	}

	private int sliderX2() {
		return left() + PANEL_W - PAD - 34;
	}

	private int swatchX(int index) {
		return left() + PAD + index * (SWATCH + SWATCH_GAP);
	}

	private int swatchY() {
		return listBottom() + 22;
	}

	private int buttonX() {
		return left() + PANEL_W - PAD - BTN_W;
	}

	private List<Row> rows() {
		List<Row> out = new ArrayList<>();
		int y = listTop() - scroll;
		for (Module m : ModuleManager.all()) {
			out.add(new Row(m, null, y, ROW_H));
			y += ROW_H;
			if (m == expanded) {
				for (DoubleSetting s : m.getSettings()) {
					out.add(new Row(m, s, y, SET_H));
					y += SET_H;
				}
			}
		}
		return out;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	// ---------- Отрисовка ----------

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		scroll = Math.max(0, Math.min(scroll, maxScroll()));

		ctx.fill(0, 0, width, height, 0x88000000);

		int l = left();
		int t = top();
		int r = l + PANEL_W;
		int b = t + panelH();

		ctx.fill(l, t, r, b, 0xF0141416);
		ctx.fill(l, t, r, t + 2, Theme.accent());

		// Заголовок
		int flyW = textRenderer.getWidth("Fly");
		ctx.drawText(textRenderer, "Fly", l + PAD, t + 11, Theme.accent(), true);
		ctx.drawText(textRenderer, "Visual", l + PAD + flyW, t + 11, Theme.TEXT, true);

		// Список модулей (с обрезкой по области списка)
		String hoverDesc = null;
		int lt = listTop();
		int lb = listBottom();
		ctx.enableScissor(l, lt, r, lb);
		for (Row row : rows()) {
			if (row.y() + row.h() <= lt || row.y() >= lb) continue;

			boolean hover = mouseX >= l && mouseX < r
					&& mouseY >= Math.max(row.y(), lt) && mouseY < Math.min(row.y() + row.h(), lb);

			if (row.setting() == null) {
				drawModuleRow(ctx, row.module(), row.y(), hover, l, r);
				if (hover) {
					hoverDesc = row.module().getDescription();
				}
			} else {
				drawSettingRow(ctx, row.setting(), row.y(), l, r);
			}
		}
		ctx.disableScissor();

		if (hoverDesc != null) {
			int w = textRenderer.getWidth(hoverDesc);
			ctx.drawText(textRenderer, hoverDesc, r - PAD - w, t + 11, Theme.TEXT_DIM, true);
		}

		// Полоса прокрутки
		if (maxScroll() > 0) {
			int barH = Math.max(12, viewH() * viewH() / contentHeight());
			int barY = lt + (viewH() - barH) * scroll / maxScroll();
			ctx.fill(r - 3, barY, r - 1, barY + barH, 0x60FFFFFF);
		}

		// Нижняя часть
		ctx.fill(l, lb, r, lb + 1, 0x30FFFFFF);
		ctx.drawText(textRenderer, "Theme color", l + PAD, lb + 8, Theme.TEXT_DIM, true);
		String hint = "RMB: settings";
		ctx.drawText(textRenderer, hint, r - PAD - textRenderer.getWidth(hint), lb + 8, Theme.TEXT_DIM, true);

		for (int i = 0; i < Theme.PRESETS.length; i++) {
			int sx = swatchX(i);
			int sy = swatchY();
			if (Theme.PRESETS[i] == FlyConfig.get().accentColor) {
				ctx.fill(sx - 2, sy - 2, sx + SWATCH + 2, sy + SWATCH + 2, 0xFFFFFFFF);
			}
			ctx.fill(sx, sy, sx + SWATCH, sy + SWATCH, 0xFF000000 | Theme.PRESETS[i]);
		}

		// Кнопка HUD-редактора
		int bx = buttonX();
		int by = swatchY();
		boolean btnHover = mouseX >= bx && mouseX < bx + BTN_W && mouseY >= by && mouseY < by + BTN_H;
		ctx.fill(bx, by, bx + BTN_W, by + BTN_H, btnHover ? 0xFF3A3A3E : 0xFF2A2A2E);
		ctx.fill(bx, by + BTN_H - 1, bx + BTN_W, by + BTN_H, Theme.accent());
		String label = "HUD Editor";
		ctx.drawText(textRenderer, label, bx + (BTN_W - textRenderer.getWidth(label)) / 2, by + 3, Theme.TEXT, true);

		super.render(ctx, mouseX, mouseY, delta);
	}

	private void drawModuleRow(DrawContext ctx, Module m, int ry, boolean hover, int l, int r) {
		if (hover) {
			ctx.fill(l, ry, r, ry + ROW_H, 0x30FFFFFF);
		}
		ctx.drawText(textRenderer, m.getName(), l + PAD, ry + 6, Theme.TEXT, true);

		int ph = 12;
		int px = r - PAD - PILL_W;
		int py = ry + 4;
		ctx.fill(px, py, px + PILL_W, py + ph, m.isEnabled() ? Theme.accent() : 0xFF3A3A3E);
		int knob = ph - 2;
		int kx = m.isEnabled() ? px + PILL_W - knob - 1 : px + 1;
		ctx.fill(kx, py + 1, kx + knob, py + 1 + knob, 0xFFFFFFFF);

		if (!m.getSettings().isEmpty()) {
			String arrow = m == expanded ? "v" : ">";
			ctx.drawText(textRenderer, arrow, px - 12, ry + 6, Theme.TEXT_DIM, true);
		}
	}

	private void drawSettingRow(DrawContext ctx, DoubleSetting s, int ry, int l, int r) {
		ctx.drawText(textRenderer, s.getName(), l + PAD + 8, ry + 5, Theme.TEXT_DIM, true);

		// Переключатель Вкл/Выкл
		if (s.isToggle()) {
			int ph = 12;
			int px = r - PAD - PILL_W;
			int py = ry + (SET_H - ph) / 2;
			ctx.fill(px, py, px + PILL_W, py + ph, s.getBool() ? Theme.accent() : 0xFF3A3A3E);
			int knob = ph - 2;
			int kx = s.getBool() ? px + PILL_W - knob - 1 : px + 1;
			ctx.fill(kx, py + 1, kx + knob, py + 1 + knob, 0xFFFFFFFF);
			return;
		}

		int x1 = sliderX1();
		int x2 = sliderX2();
		int cy = ry + SET_H / 2;
		ctx.fill(x1, cy - 1, x2, cy + 1, 0xFF3A3A3E);

		double frac = (s.get() - s.getMin()) / (s.getMax() - s.getMin());
		int fx = x1 + (int) Math.round(frac * (x2 - x1));
		ctx.fill(x1, cy - 1, fx, cy + 1, Theme.accent());
		ctx.fill(fx - 2, cy - 4, fx + 2, cy + 4, 0xFFFFFFFF);

		String value = s.format();
		ctx.drawText(textRenderer, value, r - PAD - textRenderer.getWidth(value), ry + 5, Theme.TEXT, true);
	}

	// ---------- Мышь ----------

	private void dragTo(double mouseX) {
		if (dragging == null) return;
		double t = (mouseX - sliderX1()) / (double) (sliderX2() - sliderX1());
		t = Math.max(0.0, Math.min(1.0, t));
		dragging.set(dragging.getMin() + t * (dragging.getMax() - dragging.getMin()));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int l = left();
		int r = l + PANEL_W;

		if ((button == 0 || button == 1)
				&& mouseX >= l && mouseX < r && mouseY >= listTop() && mouseY < listBottom()) {
			for (Row row : rows()) {
				if (mouseY < row.y() || mouseY >= row.y() + row.h()) continue;

				if (row.setting() != null) {
					if (row.setting().isToggle()) {
						if (button == 0) {
							row.setting().flip();
							FlyConfig.save();
						}
						return true;
					}
					if (button == 0 && mouseX >= sliderX1() - 4 && mouseX <= sliderX2() + 4) {
						dragging = row.setting();
						dragTo(mouseX);
					}
					return true;
				}

				Module m = row.module();
				boolean hasSettings = !m.getSettings().isEmpty();
				int px = r - PAD - PILL_W;
				boolean onArrow = hasSettings && mouseX >= px - 16 && mouseX < px - 2;

				if (button == 1 || onArrow) {
					if (hasSettings) {
						expanded = (expanded == m) ? null : m;
					}
				} else {
					m.toggle();
				}
				return true;
			}
		}

		if (button == 0) {
			// Цвет темы
			for (int i = 0; i < Theme.PRESETS.length; i++) {
				int sx = swatchX(i);
				int sy = swatchY();
				if (mouseX >= sx && mouseX < sx + SWATCH && mouseY >= sy && mouseY < sy + SWATCH) {
					FlyConfig.get().accentColor = Theme.PRESETS[i];
					FlyConfig.save();
					return true;
				}
			}

			// HUD-редактор
			int bx = buttonX();
			int by = swatchY();
			if (mouseX >= bx && mouseX < bx + BTN_W && mouseY >= by && mouseY < by + BTN_H) {
				MinecraftClient.getInstance().setScreen(new HudEditorScreen());
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (dragging != null) {
			dragTo(mouseX);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (dragging != null) {
			dragging = null;
			FlyConfig.save();
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(amount * 14)));
		return true;
	}
}
