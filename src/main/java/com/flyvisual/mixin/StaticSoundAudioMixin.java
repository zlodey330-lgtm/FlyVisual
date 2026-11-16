package com.flyvisual.mixin;

import com.flyvisual.record.AudioTap;
import net.minecraft.client.sound.StaticSound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

/** Запись звука: копируем данные звука, пока игра не передала их в OpenAL. */
@Mixin(StaticSound.class)
public class StaticSoundAudioMixin {
	@Inject(method = "<init>(Ljava/nio/ByteBuffer;Ljavax/sound/sampled/AudioFormat;)V", at = @At("TAIL"))
	private void flyvisual$capture(ByteBuffer sample, AudioFormat format, CallbackInfo ci) {
		AudioTap.registerSound(this, sample, format);
	}
}
