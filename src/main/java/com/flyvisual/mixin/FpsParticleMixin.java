package com.flyvisual.mixin;

import com.flyvisual.fpsboost.MobConfig;
import net.minecraft.block.BlockState;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** FPS Boost: отдельные виды частиц и частицы блоков можно выключить. */
@Mixin(ParticleManager.class)
public class FpsParticleMixin {
	@Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
			at = @At("HEAD"), cancellable = true)
	private void fpsboost$hideParticle(ParticleEffect parameters, double x, double y, double z,
									   double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
		if (MobConfig.hideBlockParticles && parameters instanceof BlockStateParticleEffect) {
			cir.setReturnValue(null);
			return;
		}
		if (MobConfig.hasHiddenParticles() && MobConfig.isParticleHidden(parameters.getType())) {
			cir.setReturnValue(null);
		}
	}

	@Inject(method = "addBlockBreakParticles(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V",
			at = @At("HEAD"), cancellable = true)
	private void fpsboost$noBreakParticles(BlockPos pos, BlockState state, CallbackInfo ci) {
		if (MobConfig.hideBlockParticles) ci.cancel();
	}

	@Inject(method = "addBlockBreakingParticles(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)V",
			at = @At("HEAD"), cancellable = true)
	private void fpsboost$noBreakingParticles(BlockPos pos, Direction direction, CallbackInfo ci) {
		if (MobConfig.hideBlockParticles) ci.cancel();
	}
}
