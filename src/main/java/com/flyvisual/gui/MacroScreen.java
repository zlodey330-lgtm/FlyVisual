package com.flyvisual.gui;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.config.Theme;
import com.flyvisual.module.impl.MacroModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Редактор текстов макросов: 8 строк. Сохраняется при закрытии. */
public class MacroScreen extends Screen {
	private final Screen parent;
	private final TextFieldWidget[] fields = new TextFieldWidget[MacroModule.COUNT];

	public MacroScreen(Screen parent) {
		super(Text.literal("Макросы"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		String[] saved = FlyConfig.get().macros;
		int rowH = 22;
		int top = Math.max(30, (height - (MacroModule.COUNT * rowH + 60)) / 2 + 20);
		int fieldW = Math.min(width - 120, 260);
		int x = (width - fieldW) / 2 + 30;

		for (int i = 0; i < MacroModule.COUNT; i++) {
			TextFieldWidget f = new TextFieldWidget(textRenderer, x, top + i * rowH, fieldW - 30, 18, Text.literal("Макрос " + (i + 1)));
			f.setMaxLength(256);
			f.setPlaceholder(Text.literal("Текст или /команда"));
			if (saved != null && i < saved.length && saved[i] != null) {
				f.setText(saved[i]);
			}
			fields[i] = f;
			addDrawableChild(f);
		}
		setInitialFocus(fields[0]);

		addDrawableChild(ButtonWidget.builder(Text.literal("Готово"), b -> close())
				.dimensions(width / 2 - 50, top + MacroModule.COUNT * rowH + 8, 100, 20).build());
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		renderBackground(ctx);
		ctx.drawCenteredTextWithShadow(textRenderer, "Макросы", width / 2, 12, Theme.accent());
		ctx.drawCenteredTextWithShadow(textRenderer, "Клавиши назначаются в Управление → FlyVisual", width / 2, 24, Theme.TEXT_DIM);
		for (int i = 0; i < fields.length; i++) {
			ctx.drawText(textRenderer, "Макрос " + (i + 1), fields[i].getX() - 62, fields[i].getY() + 5, Theme.TEXT, true);
		}
		super.render(ctx, mouseX, mouseY, delta);
	}

	@Override
	public void removed() {
		String[] out = new String[MacroModule.COUNT];
		for (int i = 0; i < out.length; i++) {
			out[i] = fields[i] == null ? "" : fields[i].getText();
		}
		FlyConfig.get().macros = out;
		FlyConfig.save();
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}
}
