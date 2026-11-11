package com.flyvisual.mixin;

import com.flyvisual.config.Theme;
import com.flyvisual.gui.UiShapes;
import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.PauseScreenModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Экран паузы: кнопки рисуются в стиле FlyVisual (скруглённые, цвета темы). Логика кнопок не меняется. */
@Mixin(PressableWidget.class)
public class PressableWidgetMixin {
	@Inject(method = "renderWidget(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"), cancellable = true)
	private void flyvisual$pauseButton(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		PauseScreenModule m = ModuleManager.pauseScreen();
		if (m == null || !m.restylesButtons()) return;
		if (!((Object) this instanceof ButtonWidget)) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!(mc.currentScreen instanceof GameMenuScreen)) return;

		ClickableWidget w = (ClickableWidget) (Object) this;
		int x = w.getX();
		int y = w.getY();
		int wd = w.getWidth();
		int ht = w.getHeight();
		boolean hover = w.isHovered() && w.active;
		int r = m.cornerRadius();
		int accent = Theme.accent();
		int border = hover ? accent : 0x33FFFFFF;
		int fill = UiShapes.lerp(Theme.tileCol() | 0xFF000000, Theme.tileHoverCol() | 0xFF000000, hover ? 1.0f : 0.0f);
		if (!w.active) fill = UiShapes.mulA(fill, 0.55f);
		UiShapes.roundRect(ctx, x - 1, y - 1, wd + 2, ht + 2, r + 1, border);
		UiShapes.roundRect(ctx, x, y, wd, ht, r, fill);
		int color = w.active ? 0xFFFFFFFF : 0xFF777777;
		ctx.drawCenteredTextWithShadow(mc.textRenderer, w.getMessage(), x + wd / 2, y + (ht - 8) / 2, color);
		ci.cancel();
	}
}
