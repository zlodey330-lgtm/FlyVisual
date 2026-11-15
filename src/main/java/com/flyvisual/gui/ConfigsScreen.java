package com.flyvisual.gui;

import com.flyvisual.config.Theme;
import com.flyvisual.config.VisualConfigs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Сохранение и загрузка конфигов визуала. */
public class ConfigsScreen extends Screen {
	private final Screen parent;
	private TextFieldWidget nameField;
	private String status = "";
	private int scroll = 0;

	public ConfigsScreen(Screen parent) {
		super(Text.literal("FlyVisual Configs"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		clearChildren();
		int w = 240;
		int x = (width - w) / 2;
		int top = height / 2 - 100;
		nameField = new TextFieldWidget(textRenderer, x, top + 20, w - 84, 18, Text.literal("Имя"));
		nameField.setMaxLength(24);
		nameField.setPlaceholder(Text.literal("Название конфига"));
		addDrawableChild(nameField);
		addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить"), b -> {
			boolean ok = VisualConfigs.save(nameField.getText());
			status = ok ? "Сохранено: " + VisualConfigs.clean(nameField.getText()) : "Введи название";
			init();
		}).dimensions(x + w - 80, top + 19, 80, 20).build());

		List<String> names = VisualConfigs.list();
		int rows = Math.min(names.size() - scroll, 6);
		for (int i = 0; i < rows; i++) {
			String n = names.get(i + scroll);
			int y = top + 52 + i * 24;
			addDrawableChild(ButtonWidget.builder(Text.literal("Загрузить"), b -> {
				status = VisualConfigs.load(n) ? "Загружено: " + n : "Не удалось загрузить";
			}).dimensions(x + w - 112, y, 72, 20).build());
			addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> {
				VisualConfigs.delete(n);
				status = "Удалено: " + n;
				init();
			}).dimensions(x + w - 36, y, 20, 20).build());
		}
		addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), b -> close())
				.dimensions(x, top + 52 + 6 * 24 + 6, w, 20).build());
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		int max = Math.max(0, VisualConfigs.list().size() - 6);
		int ns = Math.max(0, Math.min(max, scroll - (int) Math.signum(amount)));
		if (ns != scroll) {
			scroll = ns;
			init();
		}
		return true;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		renderBackground(ctx);
		int w = 240;
		int x = (width - w) / 2;
		int top = height / 2 - 100;
		ctx.drawText(textRenderer, "Конфиги визуала", x, top + 4, Theme.TEXT, true);
		List<String> names = VisualConfigs.list();
		if (names.isEmpty()) {
			ctx.drawText(textRenderer, "Пока нет сохранённых", x, top + 58, Theme.TEXT_DIM, true);
		}
		for (int i = 0; i < Math.min(names.size() - scroll, 6); i++) {
			ctx.drawText(textRenderer, textRenderer.trimToWidth(names.get(i + scroll), w - 130), x,
					top + 58 + i * 24, Theme.TEXT, true);
		}
		if (!status.isEmpty()) {
			ctx.drawText(textRenderer, status, x, top + 52 + 6 * 24 + 32, Theme.accent(), true);
		}
		super.render(ctx, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}
}
