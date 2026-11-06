package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.HitboxModule;
import net.minecraft.client.render.VertexConsumer;
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
			float[] c = module.color();
			WorldRenderer.drawBox(matrices, vertices, box, c[0], c[1], c[2], c[3]);
		} else {
			WorldRenderer.drawBox(matrices, vertices, box, red, green, blue, alpha);
		}
	}
}
