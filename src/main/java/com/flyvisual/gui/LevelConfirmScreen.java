package com.flyvisual.gui;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.config.Theme;
import com.flyvisual.module.DoubleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Подтверждение перед включением 2-го или 3-го уровня оптимизации (для слабых ПК). */
public class LevelConfirmScreen extends Screen {
	private final Screen parent;
	private final DoubleSetting setting;
	private final int target;

	public LevelConfirmScreen(Screen parent, DoubleSetting setting, int target) {
		super(Text.literal("Подтверждение"));
		this.parent = parent;
		this.setting = setting;
		this.target = target;
	}

	@Override
	protected void init() {
		int y = height / 2 + 28;
		addDrawableChild(ButtonWidget.builder(Text.literal("Да, продолжить"), b -> {
			setting.set(target);
			FlyConfig.save();
			close();
		}).dimensions(width / 2 - 104, y, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"), b -> close())
				.dimensions(width / 2 + 4, y, 100, 20).build());
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		renderBackground(ctx);
		int cy = height / 2;
		ctx.drawCenteredTextWithShadow(textRenderer, "Вы точно хотите продолжить?", width / 2, cy - 40, Theme.accent());
		ctx.drawCenteredTextWithShadow(textRenderer,
				"Уровень " + (target + 1) + " предназначен для слабых компьютеров.", width / 2, cy - 22, Theme.TEXT);
		ctx.drawCenteredTextWithShadow(textRenderer,
				"Текстуры блоков, предметов и мобов станут более грубыми и мыльными.", width / 2, cy - 10, Theme.TEXT_DIM);
		ctx.drawCenteredTextWithShadow(textRenderer,
				"Игра один раз перезагрузит ресурсы (несколько секунд).", width / 2, cy + 2, Theme.TEXT_DIM);
		super.render(ctx, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}
}
