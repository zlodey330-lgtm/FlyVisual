package com.flyvisual.module;

import com.flyvisual.gui.UiShapes;

import com.flyvisual.config.Theme;
import com.flyvisual.module.impl.DeathScreenModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Экран смерти FlyVisual. Заменяет ванильный целиком: красные края, панель с причиной смерти,
 * координатами и измерением, и две кнопки в стиле мода (цвет берётся из темы).
 */
public class DeathMenuFx extends Screen {
	private static final int BTN_W = 150;
	private static final int BTN_H = 22;
	/** Как в ваниле: первую секунду кнопки не нажимаются, чтобы случайно не пропустить экран. */
	private static final int LOCK_TICKS = 20;

	private final DeathScreenModule module;
	private final float[] hover = new float[2];
	private int ticks;
	private float appear;
	private long lastNanos = System.nanoTime();

	public DeathMenuFx(DeathScreenModule module) {
		super(Text.literal("Ты умер"));
		this.module = module;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	protected void init() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (module.takeChatAnnouncement() && mc.player != null && mc.world != null) {
			mc.inGameHud.getChatHud().addMessage(Text.literal("[FlyVisual] Ты умер: " + coords(mc.player) + " (" + dimension(mc) + ")"));
		}
	}

	@Override
	public void tick() {
		ticks++;
	}

	// ---------- Геометрия ----------

	private int panelW() {
		return Math.min(width - 20, 260);
	}

	private List<OrderedText> causeLines() {
		String message = module.getMessage();
		if (message == null || message.isEmpty()) return List.of();
		return textRenderer.wrapLines(Text.literal(message), panelW() - 24);
	}

	private int panelH() {
		int lines = Math.min(3, causeLines().size());
		return 30 + 8 + lines * 11 + 6 + 12 + 12 + 10 + BTN_H * 2 + 6 + 12;
	}

	private int panelX() {
		return (width - panelW()) / 2;
	}

	private int panelY() {
		return Math.max(10, height / 10);
	}

	private int btnX() {
		return (width - BTN_W) / 2;
	}

	private int btnY(int i) {
		return panelY() + panelH() - 12 - BTN_H * 2 - 6 + i * (BTN_H + 6);
	}

	private boolean over(int i, double mx, double my) {
		return mx >= btnX() && mx < btnX() + BTN_W && my >= btnY(i) && my < btnY(i) + BTN_H;
	}

	private static String coords(ClientPlayerEntity player) {
		return "X " + (int) Math.floor(player.getX()) + "   Y " + (int) Math.floor(player.getY())
				+ "   Z " + (int) Math.floor(player.getZ());
	}

	private static String dimension(MinecraftClient mc) {
		if (mc.world == null) return "";
		String path = mc.world.getRegistryKey().getValue().getPath();
		return switch (path) {
			case "overworld" -> "Верхний мир";
			case "the_nether" -> "Нижний мир";
			case "the_end" -> "Энд";
			default -> path;
		};
	}

	// ---------- Отрисовка ----------

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastNanos) / 1.0e9f, 0.05f);
		lastNanos = now;
		appear = Math.min(1.0f, appear + dt / 0.35f);
		float ease = 1.0f - (1.0f - appear) * (1.0f - appear) * (1.0f - appear);

		MinecraftClient mc = MinecraftClient.getInstance();
		int accent = Theme.accent();

		ctx.fillGradient(0, 0, width, height, UiShapes.mulA(0xFF100606, 0.55f * ease), UiShapes.mulA(0xFF2A0808, 0.80f * ease));
		if (module.showVignette()) {
			int red = 0xFF000000 | (accent & 0xFFFFFF);
			ctx.fillGradient(0, 0, width, height / 3, UiShapes.mulA(red, 0.30f * ease), 0x00000000);
			ctx.fillGradient(0, height * 2 / 3, width, height, 0x00000000, UiShapes.mulA(red, 0.30f * ease));
		}

		int pw = panelW();
		int ph = panelH();
		int px = panelX();
		int py = panelY() + Math.round((1.0f - ease) * -8.0f);

		UiShapes.roundRect(ctx, px - 1, py - 1, pw + 2, ph + 2, 9, UiShapes.mulA(accent, 0.9f * ease));
		UiShapes.roundRect(ctx, px, py, pw, ph, 8, UiShapes.mulA(0xFF121216, 0.96f * ease));
		UiShapes.roundRect(ctx, px + 8, py, pw - 16, 2, 1, UiShapes.mulA(accent, ease));

		TextRenderer tr = textRenderer;

		String title = module.isHardcore() ? "Ты умер (хардкор)" : "Ты умер";
		float scale = 2.0f;
		MatrixStack ms = ctx.getMatrices();
		ms.push();
		ms.translate(width / 2.0f, py + 12, 0.0f);
		ms.scale(scale, scale, 1.0f);
		int titleColor = UiShapes.mulA(accent, ease);
		if (((titleColor >>> 24) & 0xFF) >= 12) {
			ctx.drawText(tr, title, -tr.getWidth(title) / 2, 0, titleColor, true);
		}
		ms.pop();

		int ty = py + 30 + 8;
		List<OrderedText> lines = causeLines();
		for (int i = 0; i < Math.min(3, lines.size()); i++) {
			ctx.drawText(tr, lines.get(i), px + 12, ty, UiShapes.mulA(Theme.TEXT, ease), true);
			ty += 11;
		}

		ClientPlayerEntity player = mc.player;
		if (player != null) {
			ty += 6;
			int dim = UiShapes.mulA(Theme.TEXT_DIM, ease);
			ctx.drawText(tr, coords(player), px + 12, ty, UiShapes.mulA(Theme.TEXT, ease), true);
			ctx.drawText(tr, dimension(mc), px + 12, ty + 12, dim, true);
		}

		boolean locked = ticks < LOCK_TICKS;
		String[] labels = {module.isHardcore() ? "Наблюдать" : "Возродиться", "В главное меню"};
		for (int i = 0; i < 2; i++) {
			float target = !locked && over(i, mouseX, mouseY) ? 1.0f : 0.0f;
			hover[i] += (target - hover[i]) * (1.0f - (float) Math.exp(-14.0f * dt));
			drawButton(ctx, btnX(), btnY(i) + Math.round((1.0f - ease) * -8.0f), labels[i], hover[i], i == 0, locked, ease);
		}

		super.render(ctx, mouseX, mouseY, delta);
	}

	private void drawButton(DrawContext ctx, int x, int y, String label, float hv, boolean primary, boolean locked, float ease) {
		int accent = 0xFF000000 | (Theme.accent() & 0xFFFFFF);
		if (hv > 0.02f) {
			UiShapes.roundRect(ctx, x - 3, y - 3, BTN_W + 6, BTN_H + 6, 9, UiShapes.mulA(accent, 0.25f * hv * ease));
		}
		int border = UiShapes.lerp(UiShapes.lerp(accent, 0xFF000000, 0.35f), accent, hv);
		int inner = primary ? UiShapes.lerp(0xFF2A1416, 0xFF3A1B1E, hv) : UiShapes.lerp(0xFF1B1B1F, 0xFF2B2B31, hv);
		if (locked) {
			border = 0xFF3A3A3E;
			inner = 0xFF1B1B1F;
		}
		UiShapes.roundRect(ctx, x, y, BTN_W, BTN_H, 6, UiShapes.mulA(border, ease));
		UiShapes.roundRect(ctx, x + 1, y + 1, BTN_W - 2, BTN_H - 2, 5, UiShapes.mulA(inner, ease));
		int color = UiShapes.mulA(locked ? 0xFF808088 : Theme.TEXT, ease);
		if (((color >>> 24) & 0xFF) >= 12) {
			ctx.drawText(textRenderer, label, x + (BTN_W - textRenderer.getWidth(label)) / 2, y + (BTN_H - 8) / 2 + 1, color, true);
		}
	}

	// ---------- Ввод ----------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && ticks >= LOCK_TICKS) {
			MinecraftClient mc = MinecraftClient.getInstance();
			for (int i = 0; i < 2; i++) {
				if (over(i, mouseX, mouseY)) {
					mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
					if (i == 0) {
						respawn(mc);
					} else {
						toTitle(mc);
					}
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private static void respawn(MinecraftClient mc) {
		if (mc.player != null) {
			mc.player.requestRespawn();
		}
		mc.setScreen(null);
	}

	private static void toTitle(MinecraftClient mc) {
		if (mc.world != null) {
			mc.world.disconnect();
		}
		mc.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
		mc.setScreen(new TitleScreen());
	}
}
