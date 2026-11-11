package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.SmoothChatModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Плавный чат: при новом сообщении весь чат мягко «въезжает» на одну строку вверх. */
@Mixin(ChatHud.class)
public class ChatHudMixin {
	private boolean flyvisual$pushed;

	@Inject(method = "addMessage", at = @At("HEAD"))
	private void flyvisual$onMessage(CallbackInfo ci) {
		SmoothChatModule m = ModuleManager.smoothChat();
		if (m != null) m.onMessage();
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;III)V", at = @At("HEAD"))
	private void flyvisual$shiftStart(DrawContext context, int currentTick, int mouseX, int mouseY, CallbackInfo ci) {
		flyvisual$pushed = false;
		SmoothChatModule m = ModuleManager.smoothChat();
		if (m == null) return;
		float rem = m.remaining();
		if (rem <= 0.0f) return;
		double scale = MinecraftClient.getInstance().options.getChatScale().getValue();
		context.getMatrices().push();
		context.getMatrices().translate(0.0f, (float) (9.0 * scale * rem), 0.0f);
		flyvisual$pushed = true;
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;III)V", at = @At("RETURN"))
	private void flyvisual$shiftEnd(DrawContext context, int currentTick, int mouseX, int mouseY, CallbackInfo ci) {
		if (flyvisual$pushed) {
			context.getMatrices().pop();
			flyvisual$pushed = false;
		}
	}
}
