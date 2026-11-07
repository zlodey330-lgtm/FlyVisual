package com.flyvisual.gui;

import com.flyvisual.config.Theme;
import com.flyvisual.module.HudModule;
import com.flyvisual.module.Module;
import com.flyvisual.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD-редактор: модули уже нарисованы игрой под этим экраном,
 * мы лишь показываем рамки и даём перетаскивать их мышкой.
 */
public class HudEditorScreen extends Screen {
	private HudModule dragging;
	private int dragOffsetX;
	private int dragOffsetY;

	/** Куда вернуться после меню FlyVisual (главное меню или null в игре). */
	private final Screen parent;

	public HudEditorScreen(Screen parent) {
		super(Text.literal("FlyVisual HUD Editor"));
		this.parent = parent;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private List<HudModule> hudModules() {
		List<HudModule> out = new ArrayList<>();
		for (Module m : ModuleManager.all()) {
			if (m.isEnabled() && m instanceof HudModule hud) {
				out.add(hud);
			}
		}
		return out;
	}

	private void outline(DrawContext ctx, int x, int y, int w, int h, int color) {
		ctx.fill(x, y, x + w, y + 1, color);
		ctx.fill(x, y + h - 1, x + w, y + h, color);
		ctx.fill(x, y, x + 1, y + h, color);
		ctx.fill(x + w - 1, y, x + w, y + h, color);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, 0x40000000);

		String hint = "Перетаскивай модули мышкой. ESC: назад в меню.";
		ctx.drawText(textRenderer, hint, (width - textRenderer.getWidth(hint)) / 2, height / 2, Theme.TEXT, true);

		for (HudModule hud : hudModules()) {
			boolean active = hud == dragging || (dragging == null && hud.contains(mouseX, mouseY));
			int color = active ? Theme.accent() : 0xFFFFFFFF;
			outline(ctx, hud.getX() - 1, hud.getY() - 1, hud.getBoxW() + 2, hud.getBoxH() + 2, color);
		}

		super.render(ctx, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			List<HudModule> list = hudModules();
			for (int i = list.size() - 1; i >= 0; i--) {
				HudModule hud = list.get(i);
				if (hud.contains(mouseX, mouseY)) {
					dragging = hud;
					dragOffsetX = (int) mouseX - hud.getX();
					dragOffsetY = (int) mouseY - hud.getY();
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (dragging != null) {
			int nx = (int) mouseX - dragOffsetX;
			int ny = (int) mouseY - dragOffsetY;
			nx = Math.max(0, Math.min(nx, width - dragging.getBoxW()));
			ny = Math.max(0, Math.min(ny, height - dragging.getBoxH()));
			dragging.setPosition(nx, ny);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (dragging != null) {
			dragging.savePosition();
			dragging = null;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(new FlyMenuScreen(parent));
	}
}
