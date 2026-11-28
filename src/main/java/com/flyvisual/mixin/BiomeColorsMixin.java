package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.WeatherModule;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Сезонный цвет травы и листвы (модуль Weather). Работает только на клиенте. */
@Mixin(BiomeColors.class)
public class BiomeColorsMixin {
	@Inject(method = "getGrassColor", at = @At("RETURN"), cancellable = true)
	private static void flyvisual$grass(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		WeatherModule weather = ModuleManager.weather();
		if (weather != null && weather.colorsActive()) {
			cir.setReturnValue(weather.tintGrass(cir.getReturnValue(), pos));
		}
	}

	@Inject(method = "getFoliageColor", at = @At("RETURN"), cancellable = true)
	private static void flyvisual$foliage(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		WeatherModule weather = ModuleManager.weather();
		if (weather != null && weather.colorsActive()) {
			cir.setReturnValue(weather.tintFoliage(cir.getReturnValue(), pos));
		}
	}
}
