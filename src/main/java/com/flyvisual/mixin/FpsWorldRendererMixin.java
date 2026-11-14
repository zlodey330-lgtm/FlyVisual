package com.flyvisual.mixin;

import com.flyvisual.fpsboost.MobConfig;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.WorldRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FPS Boost: небо, светила и звёзды, облака, дождь и снег, трещины на блоках. */
@Mixin(WorldRenderer.class)
public class FpsWorldRendererMixin {
	private static final String SKY = "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V";

	@Shadow
	private VertexBuffer starsBuffer;

	@Inject(method = "renderClouds(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FDDD)V",
			at = @At("HEAD"), cancellable = true)
	private void fpsboost$noClouds(CallbackInfo ci) {
		if (MobConfig.hideClouds) {
			ci.cancel();
		}
	}

	@Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
	private void fpsboost$noWeather(CallbackInfo ci) {
		if (MobConfig.hideWeather) {
			ci.cancel();
		}
	}

	@Inject(method = "tickRainSplashing", at = @At("HEAD"), cancellable = true)
	private void fpsboost$noRainSplash(CallbackInfo ci) {
		if (MobConfig.hideWeather) {
			ci.cancel();
		}
	}

	@Inject(method = "setBlockBreakingInfo", at = @At("HEAD"), cancellable = true)
	private void fpsboost$noCracks(CallbackInfo ci) {
		if (MobConfig.hideBreakAnim) {
			ci.cancel();
		}
	}

	@Inject(method = SKY, at = @At("HEAD"), cancellable = true)
	private void fpsboost$noSky(CallbackInfo ci) {
		if (MobConfig.hideSky) {
			ci.cancel();
		}
	}

	/** Звёзды: пропускаем отрисовку буфера звёзд. */
	@Redirect(method = SKY,
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gl/VertexBuffer;draw(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/gl/ShaderProgram;)V"),
			require = 0)
	private void fpsboost$skyDraw(VertexBuffer buffer, Matrix4f modelView, Matrix4f projection, ShaderProgram program) {
		if (MobConfig.hideSunMoonStars && buffer == this.starsBuffer) {
			return;
		}
		buffer.draw(modelView, projection, program);
	}

	/** Солнце: размер квадрата 30 превращаем в 0, и оно не видно. */
	@ModifyConstant(method = SKY, constant = @Constant(floatValue = 30.0F), require = 0)
	private float fpsboost$sun(float original) {
		return MobConfig.hideSunMoonStars ? 0.0f : original;
	}

	/** Луна: размер квадрата 20 превращаем в 0. */
	@ModifyConstant(method = SKY, constant = @Constant(floatValue = 20.0F), require = 0)
	private float fpsboost$moon(float original) {
		return MobConfig.hideSunMoonStars ? 0.0f : original;
	}
}
