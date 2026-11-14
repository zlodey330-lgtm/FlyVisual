package com.flyvisual.mixin;

import com.flyvisual.fpsboost.MobConfig;
import com.flyvisual.fpsboost.OcclusionCuller;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** FPS Boost: дальность каждого моба, мобы за стенами, тени под мобами. */
@Mixin(EntityRenderDispatcher.class)
public class FpsEntityDispatcherMixin {
	@Inject(method = "shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z",
			at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void fpsboost$distance(E entity, Frustum frustum, double x, double y, double z,
													  CallbackInfoReturnable<Boolean> cir) {
		if (MobConfig.hasLimits()) {
			Integer limit = MobConfig.limit(entity.getType());
			if (limit != null) {
				if (limit <= 0) {
					cir.setReturnValue(false);
					return;
				}
				double dx = entity.getX() - x;
				double dy = entity.getY() - y;
				double dz = entity.getZ() - z;
				double max = limit.doubleValue();
				if (dx * dx + dy * dy + dz * dz > max * max) {
					cir.setReturnValue(false);
					return;
				}
			}
		}
		if (MobConfig.occlusionCull && OcclusionCuller.shouldHide(entity, x, y, z)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
	private static void fpsboost$noShadow(CallbackInfo ci) {
		if (MobConfig.hideShadows) {
			ci.cancel();
		}
	}
}
