package com.flyvisual.mixin;

import com.flyvisual.record.AudioTap;
import net.minecraft.client.sound.Source;
import net.minecraft.client.sound.StaticSound;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Запись звука: слушаем, что игра делает с каждым источником звука (громкость, высота, положение, запуск, остановка).
 * Сами звуки при этом работают как обычно.
 */
@Mixin(Source.class)
public class SourceAudioMixin {
	@Inject(method = "setPitch(F)V", at = @At("HEAD"))
	private void flyvisual$pitch(float pitch, CallbackInfo ci) {
		AudioTap.setPitch(this, pitch);
	}

	@Inject(method = "setLooping(Z)V", at = @At("HEAD"))
	private void flyvisual$looping(boolean looping, CallbackInfo ci) {
		AudioTap.setLooping(this, looping);
	}

	@Inject(method = "setVolume(F)V", at = @At("HEAD"))
	private void flyvisual$volume(float volume, CallbackInfo ci) {
		AudioTap.setVolume(this, volume);
	}

	@Inject(method = "disableAttenuation()V", at = @At("HEAD"))
	private void flyvisual$noAttenuation(CallbackInfo ci) {
		AudioTap.disableAttenuation(this);
	}

	@Inject(method = "setAttenuation(F)V", at = @At("HEAD"))
	private void flyvisual$attenuation(float distance, CallbackInfo ci) {
		AudioTap.setAttenuation(this, distance);
	}

	@Inject(method = "setRelative(Z)V", at = @At("HEAD"))
	private void flyvisual$relative(boolean relative, CallbackInfo ci) {
		AudioTap.setRelative(this, relative);
	}

	@Inject(method = "setPosition(Lnet/minecraft/util/math/Vec3d;)V", at = @At("HEAD"))
	private void flyvisual$position(Vec3d pos, CallbackInfo ci) {
		AudioTap.setPosition(this, pos.x, pos.y, pos.z);
	}

	@Inject(method = "setBuffer(Lnet/minecraft/client/sound/StaticSound;)V", at = @At("HEAD"))
	private void flyvisual$buffer(StaticSound sound, CallbackInfo ci) {
		AudioTap.setBuffer(this, sound);
	}

	@Inject(method = "play()V", at = @At("HEAD"))
	private void flyvisual$play(CallbackInfo ci) {
		AudioTap.play(this);
	}

	@Inject(method = "pause()V", at = @At("HEAD"))
	private void flyvisual$pause(CallbackInfo ci) {
		AudioTap.pause(this);
	}

	@Inject(method = "resume()V", at = @At("HEAD"))
	private void flyvisual$resume(CallbackInfo ci) {
		AudioTap.resume(this);
	}

	@Inject(method = "stop()V", at = @At("HEAD"))
	private void flyvisual$stop(CallbackInfo ci) {
		AudioTap.stop(this);
	}

	@Inject(method = "close()V", at = @At("HEAD"))
	private void flyvisual$close(CallbackInfo ci) {
		AudioTap.close(this);
	}
}
