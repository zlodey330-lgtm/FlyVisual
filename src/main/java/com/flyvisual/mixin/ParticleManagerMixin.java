package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.DamageParticlesModule;
import net.minecraft.client.particle.ParticleManager;
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

	private static boolean flyvisual$hide(ParticleEffect parameters) {
		DamageParticlesModule module = ModuleManager.damageParticles();
		return module != null && module.hidesVanilla()
				&& (parameters.getType() == ParticleTypes.CRIT || parameters.getType() == ParticleTypes.ENCHANTED_HIT);
	}
}
