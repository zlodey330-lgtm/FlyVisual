package com.flyvisual.module.impl;

import com.flyvisual.config.Theme;
import com.flyvisual.gui.UiShapes;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Экран смерти: поверх обычного рисуется панель с тем, кто тебя убил, и координатами смерти.
 * Координаты можно ещё записать в чат (видишь только ты). Кнопки возрождения остаются обычными.
 */
public class DeathScreenModule extends Module {
	private final DoubleSetting vignette = addToggle("Red vignette", true);
	private final DoubleSetting coordsInChat = addToggle("Write coords in chat", true);

	private String message = "";
	private boolean announced;

	public DeathScreenModule() {
		super("Death Screen", "Who killed you and where");
	}

	@Override
	protected boolean enabledByDefault() {
		return true;
	}

	/** Запоминает текст причины смерти, который игра передаёт в экран смерти. */
	public void setMessage(Text text) {
		message = text == null ? "" : text.getString();
		announced = false;
	}

	@Override
	public void onTick(MinecraftClient client) {
		if (client.player != null && client.player.isAlive()) {
			announced = false;
		}
	}

	/** Рисуется в конце render() экрана смерти. */
	public void render(DrawContext ctx, int width, int height) {
		MinecraftClient mc = mc();
		ClientPlayerEntity player = mc.player;
		if (player == null || mc.world == null) return;
		TextRenderer tr = mc.textRenderer;

		if (vignette.getBool()) {
			int red = 0xFF000000 | (Theme.accent() & 0xFFFFFF);
			ctx.fillGradient(0, 0, width, height / 4, UiShapes.mulA(red, 0.28f), 0x00000000);
			ctx.fillGradient(0, height * 3 / 4, width, height, 0x00000000, UiShapes.mulA(red, 0.28f));
		}

		int x = (int) Math.floor(player.getX());
		int y = (int) Math.floor(player.getY());
		int z = (int) Math.floor(player.getZ());
		String coords = "X " + x + "   Y " + y + "   Z " + z;
		String dim = dimension(mc);

		if (coordsInChat.getBool() && !announced) {
			announced = true;
			mc.inGameHud.getChatHud().addMessage(Text.literal("[FlyVisual] Ты умер: " + coords + " (" + dim + ")"));
		}

		int w = Math.min(width - 20, 230);
		List<OrderedText> lines = message.isEmpty() ? List.of() : tr.wrapLines(Text.literal(message), w - 20);
		int shown = Math.min(2, lines.size());
		int h = 14 + 12 + shown * 10 + 12 + 12 + 8;
		int px = (width - w) / 2;
		int py = Math.min(height - h - 8, height / 4 + 122);
		if (py < 8) py = 8;

		int accent = Theme.accent();
		UiShapes.roundRect(ctx, px - 1, py - 1, w + 2, h + 2, 8, UiShapes.mulA(accent, 0.9f));
		UiShapes.roundRect(ctx, px, py, w, h, 7, 0xF0121216);

		ctx.drawText(tr, "Информация о смерти", px + 10, py + 8, accent, true);
		int ty = py + 22;
		for (int i = 0; i < shown; i++) {
			ctx.drawText(tr, lines.get(i), px + 10, ty, Theme.TEXT, true);
			ty += 10;
		}
		ctx.drawText(tr, coords, px + 10, ty + 4, Theme.TEXT, true);
		ctx.drawText(tr, dim, px + 10, ty + 16, Theme.TEXT_DIM, true);
	}

	private static String dimension(MinecraftClient mc) {
		String path = mc.world.getRegistryKey().getValue().getPath();
		return switch (path) {
			case "overworld" -> "Верхний мир";
			case "the_nether" -> "Нижний мир";
			case "the_end" -> "Энд";
			default -> path;
		};
	}
}
