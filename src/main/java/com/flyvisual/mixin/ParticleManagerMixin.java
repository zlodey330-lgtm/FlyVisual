package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.DamageParticlesModule;
import com.flyvisual.module.impl.OptimizationModule;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Damage Particles: прячет обычные частицы крита и зачарованного удара,
 * чтобы вместо них были только наши.
 */
@Mixin(ParticleManager.class)
public class ParticleManagerMixin {

	@Inject(method = "addEmitter(Lnet/minecraft/entity/Entity;Lnet/minecraft/particle/ParticleEffect;)V",
			at = @At("HEAD"), cancellable = true)
	private void flyvisual$hideEmitter(Entity entity, ParticleEffect parameters, CallbackInfo ci) {
		if (flyvisual$hide(parameters)) {
			ci.cancel();
		}
	}

	@Inject(method = "addEmitter(Lnet/minecraft/entity/Entity;Lnet/minecraft/particle/ParticleEffect;I)V",
			at = @At("HEAD"), cancellable = true)
	private void flyvisual$hideEmitterAged(Entity entity, ParticleEffect parameters, int maxAge, CallbackInfo ci) {
		if (flyvisual$hide(parameters)) {
			ci.cancel();
		}
	}

	/** Оптимизация: любые частицы, которые пользователь отключил. */
	@Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
			at = @At("HEAD"), cancellable = true)
	private void flyvisual$optParticle(ParticleEffect parameters, double x, double y, double z,
									   double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
		OptimizationModule opt = ModuleManager.optimization();
		if (opt != null && opt.blocksParticle(parameters)) {
			cir.setReturnValue(null);
		}
	}

	@Inject(method = "addBlockBreakParticles(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V",
			at = @At("HEAD"), cancellable = true)
	private void flyvisual$optBreak(BlockPos pos, BlockState state, CallbackInfo ci) {
		OptimizationModule opt = ModuleManager.optimization();
		if (opt != null && opt.blocksBlockParticles()) ci.cancel();
	}

	@Inject(method = "addBlockBreakingParticles(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)V",
			at = @At("HEAD"), cancellable = true)
	private void flyvisual$optBreaking(BlockPos pos, Direction direction, CallbackInfo ci) {
		OptimizationModule opt = ModuleManager.optimization();
		if (opt != null && opt.blocksBlockParticles()) ci.cancel();
	}

	private static boolean flyvisual$hide(ParticleEffect parameters) {
		boolean crit = parameters.getType() == ParticleTypes.CRIT || parameters.getType() == ParticleTypes.ENCHANTED_HIT;
		if (!crit) return false;
		OptimizationModule opt = ModuleManager.optimization();
		if (opt != null && opt.blocksCrit()) return true;
		DamageParticlesModule module = ModuleManager.damageParticles();
		return module != null && module.hidesVanilla();
	}
}
