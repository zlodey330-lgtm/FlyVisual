package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.OptimizationModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Оптимизация: выпавшие предметы лежат на месте, не крутятся и не прыгают (только на клиенте). */
@Mixin(ItemEntity.class)
public class ItemEntityMixin {
	@Inject(method = "getRotation", at = @At("HEAD"), cancellable = true)
	private void flyvisual$noSpin(float tickDelta, CallbackInfoReturnable<Float> cir) {
		if (flyvisual$still()) cir.setReturnValue(0.0f);
	}

	@Inject(method = "getItemAge", at = @At("HEAD"), cancellable = true)
	private void flyvisual$noBob(CallbackInfoReturnable<Integer> cir) {
		if (flyvisual$still()) cir.setReturnValue(0);
	}

	private boolean flyvisual$still() {
		OptimizationModule opt = ModuleManager.optimization();
		if (opt == null || !opt.keepsItemsStill()) return false;
		ClientWorld w = MinecraftClient.getInstance().world;
		ItemEntity self = (ItemEntity) (Object) this;
		return w != null && w.getEntityById(self.getId()) == self;
	}
}
