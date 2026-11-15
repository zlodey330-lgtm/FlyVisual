package com.flyvisual.mixin;

import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * В 1.20.1 метод clearParticles() у ParticleManager закрытый (private).
 * Этот accessor даёт вызвать его из MemoryCleaner (очистка памяти).
 */
@Mixin(ParticleManager.class)
public interface ParticleManagerAccessor {
	@Invoker("clearParticles")
	void flyvisual$clearParticles();
}
