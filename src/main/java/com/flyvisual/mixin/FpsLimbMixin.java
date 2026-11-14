package com.flyvisual.mixin;

import com.flyvisual.fpsboost.FpsExtra;
import com.flyvisual.fpsboost.MobConfig;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** FPS Boost: у далёких мобов и игроков не двигаются руки и ноги. */
@Mixin(LivingEntityRenderer.class)
public class FpsLimbMixin {
	@Redirect(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;setAngles(Lnet/minecraft/entity/Entity;FFFFF)V"),
			require = 0)
	@SuppressWarnings("unchecked")
	private void fpsboost$limbs(EntityModel<?> model, Entity entity, float limbAngle, float limbDistance,
								float animationProgress, float headYaw, float headPitch) {
		if (MobConfig.on() && FpsExtra.farNoAnim && FpsExtra.farForAnim(entity)) {
			limbAngle = 0.0f;
			limbDistance = 0.0f;
		}
		((EntityModel<Entity>) model).setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
	}
}
