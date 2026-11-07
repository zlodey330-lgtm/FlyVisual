package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.HitboxModule;
import com.flyvisual.module.impl.OptimizationModule;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Hitboxes: перекрашиваем основную рамку ванильного хитбокса (F3+B). */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

	/** Оптимизация: без огня вокруг горящих сущностей. */
	@Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
	private void flyvisual$noFire(CallbackInfo ci) {
		OptimizationModule opt = ModuleManager.optimization();
		if (opt != null && opt.hidesEntityFire()) {
			ci.cancel();
		}
	}

	/** Оптимизация: мобы за стенами и далёкие рамки не рисуются. */
	@Inject(method = "shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z",
			at = @At("RETURN"), cancellable = true)
	private <E extends Entity> void flyvisual$cull(E entity, Frustum frustum, double x, double y, double z,
												  CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) return;
		OptimizationModule opt = ModuleManager.optimization();
		if (opt != null && opt.isEnabled() && opt.shouldCull(entity)) {
			cir.setReturnValue(false);
		}
	}

	@Redirect(
			method = "renderHitbox",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/WorldRenderer;drawBox(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/util/math/Box;FFFF)V",
					ordinal = 0
			)
	)
	private static void flyvisual$hitboxColor(MatrixStack matrices, VertexConsumer vertices, Box box,
											  float red, float green, float blue, float alpha) {
		HitboxModule module = ModuleManager.hitbox();
		if (module != null && module.isEnabled()) {
			HitboxModule.markMixinWorked();
			float[] c = module.color();
			WorldRenderer.drawBox(matrices, vertices, box, c[0], c[1], c[2], c[3]);
		} else {
			WorldRenderer.drawBox(matrices, vertices, box, red, green, blue, alpha);
		}
	}
}
