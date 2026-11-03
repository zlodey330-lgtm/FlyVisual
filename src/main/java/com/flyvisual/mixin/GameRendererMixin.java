package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.NoHurtCamModule;
import com.flyvisual.module.impl.TotemAnimationModule;
import com.flyvisual.module.impl.ZoomModule;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

	/** NoHurtCam: не трясём камеру при уроне. */
	@Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
	private void flyvisual$noHurtCam(CallbackInfo ci) {
		NoHurtCamModule module = ModuleManager.noHurtCam();
		if (module != null && module.isEnabled()) {
			ci.cancel();
		}
	}

	/** Totem Animation: полностью скрываем анимацию тотема, если так выбрано. */
	@Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
	private void flyvisual$hideTotem(ItemStack stack, CallbackInfo ci) {
		TotemAnimationModule totem = ModuleManager.totem();
		if (totem != null && totem.isEnabled() && totem.shouldHide()) {
			ci.cancel();
		}
	}

	/** Totem Animation: размер предмета в анимации (в ванили 50 + 175 * sin). Меняем обе константы. */
	@ModifyConstant(method = "renderFloatingItem", constant = @Constant(floatValue = 50.0F))
	private float flyvisual$totemBase(float original) {
		TotemAnimationModule totem = ModuleManager.totem();
		return totem != null && totem.isEnabled() ? original * totem.getSize() : original;
	}

	@ModifyConstant(method = "renderFloatingItem", constant = @Constant(floatValue = 175.0F))
	private float flyvisual$totemPeak(float original) {
		TotemAnimationModule totem = ModuleManager.totem();
		return totem != null && totem.isEnabled() ? original * totem.getSize() : original;
	}

	/** Zoom: меняем поле зрения (только основное, не руку). */
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void flyvisual$zoomFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
		if (!changingFov) return;

		ZoomModule zoom = ModuleManager.zoom();
		if (zoom == null) return;

		double multiplier = zoom.getFovMultiplier();
		if (multiplier != 1.0) {
			cir.setReturnValue(cir.getReturnValue() * multiplier);
		}
	}
}
