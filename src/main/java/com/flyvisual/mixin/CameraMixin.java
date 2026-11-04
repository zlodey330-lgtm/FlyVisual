package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.FreeLookModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	/**
	 * FreeLook: сразу после того, как камера взяла поворот игрока, подменяем его своим.
	 * Это до расчёта позиции камеры, поэтому вид от третьего лица считается правильно.
	 */
	@Inject(
			method = "update",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V",
					ordinal = 0,
					shift = At.Shift.AFTER
			)
	)
	private void flyvisual$freeLook(CallbackInfo ci) {
		FreeLookModule freeLook = ModuleManager.freeLook();
		if (freeLook != null && freeLook.overridesCamera()) {
			freeLook.updateFrame(MinecraftClient.getInstance());
			if (freeLook.overridesCamera()) {
				setRotation(freeLook.getYaw(), freeLook.getPitch());
			}
		}
	}

	/** FreeLook: камера в 3-м лице плавно отъезжает от головы и так же плавно возвращается. */
	@ModifyArg(
			method = "update",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/Camera;clipToSpace(D)D"
			),
			index = 0
	)
	private double flyvisual$freeLookDistance(double distance) {
		FreeLookModule freeLook = ModuleManager.freeLook();
		if (freeLook != null && freeLook.overridesCamera()) {
			return distance * freeLook.getDistanceFactor();
		}
		return distance;
	}
}
