package com.flyvisual.mixin;

import com.flyvisual.module.impl.OwnNametagModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Own Nametag: рисует твой ник над головой в виде от 3 лица. */
@Mixin(LivingEntityRenderer.class)
public abstract class OwnNametagMixin extends EntityRenderer<LivingEntity> {
	protected OwnNametagMixin(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("RETURN"), require = 0)
	private void flyvisual$ownName(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
								   VertexConsumerProvider consumers, int light, CallbackInfo ci) {
		OwnNametagModule m = OwnNametagModule.INSTANCE;
		if (m == null || !m.isEnabled()) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (entity != mc.player || mc.options.getPerspective().isFirstPerson()) return;
		if (!MinecraftClient.isHudEnabled() || entity.isInvisible()) return;
		this.renderLabelIfPresent(entity, entity.getDisplayName(), matrices, consumers, light);
	}
}
