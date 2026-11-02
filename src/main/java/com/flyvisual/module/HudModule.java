package com.flyvisual.module;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.config.Theme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Модуль, который что-то рисует на экране (FPS, координаты и т.д.).
 * У каждого есть настройки Opacity (прозрачность фона) и Scale (размер).
 * Позицию можно менять в HUD-редакторе, она сохраняется в конфиге.
 */
public abstract class HudModule extends Module {
	protected static final int PANEL_HEIGHT = 14;

	protected int x;
	protected int y;

	/** Размер последней отрисовки (до масштабирования): нужен HUD-редактору. */
	protected int boxW = 60;
	protected int boxH = PANEL_HEIGHT;

	protected final DoubleSetting opacity;
	protected final DoubleSetting scale;

	/** Показывать цветную полоску слева. Включается в конструкторе тех модулей, где она есть. */
	protected DoubleSetting accentBar;

	protected HudModule(String name, String description, int defaultX, int defaultY) {
		super(name, description);
		this.x = defaultX;
		this.y = defaultY;
		this.opacity = addSetting("Opacity", 55, 0, 100, 5);
		this.scale = addSetting("Scale", 1.0, 0.5, 2.0, 0.1);
	}

	@Override
	protected boolean enabledByDefault() {
		return true;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public float getScale() {
		return scale.getFloat();
	}

	/** Ширина с учётом масштаба. */
	public int getBoxW() {
		return Math.round(boxW * getScale());
	}

	/** Высота с учётом масштаба. */
	public int getBoxH() {
		return Math.round(boxH * getScale());
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + getBoxW() && mouseY >= y && mouseY < y + getBoxH();
	}

	public void setPosition(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public void savePosition() {
		FlyConfig.get().positions.put(getName(), new int[]{x, y});
		FlyConfig.save();
	}

	public abstract void render(DrawContext ctx, float tickDelta);

	/** Цвет фона панели с учётом настройки Opacity. */
	protected int bg() {
		return ((int) Math.round(opacity.get() * 2.55)) << 24;
	}

	protected boolean showAccentBar() {
		return accentBar == null || accentBar.getBool();
	}

	/** Панель с одной строкой текста в заданной точке. Возвращает ширину панели. */
	protected int drawPanelAt(DrawContext ctx, String text, int px, int py) {
		TextRenderer tr = mc().textRenderer;
		int w = tr.getWidth(text) + 12;
		ctx.fill(px, py, px + w, py + PANEL_HEIGHT, bg());
		if (showAccentBar()) {
			ctx.fill(px, py, px + 2, py + PANEL_HEIGHT, Theme.accent());
		}
		ctx.drawText(tr, text, px + 6, py + 3, Theme.TEXT, true);
		return w;
	}

	/** Простая панель на позиции модуля. */
	protected void drawPanel(DrawContext ctx, String text) {
		boxW = drawPanelAt(ctx, text, x, y);
		boxH = PANEL_HEIGHT;
	}
}
