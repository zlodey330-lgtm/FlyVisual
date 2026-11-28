package com.flyvisual.mixin;

import com.flyvisual.module.ModuleManager;
import com.flyvisual.module.impl.WeatherModule;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rain preset changes only the local client's rendered weather; it never sends anything to a server. */
@Mixin(World.class)
public abstract class WorldWeatherMixin {
    @Inject(method = "getRainGradient", at = @At("RETURN"), cancellable = true)
    private void flyvisual$localRain(float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientWorld)) return;
        WeatherModule weather = ModuleManager.weather();
        if (weather != null) {
            float g = weather.rainGradient();
            if (g > 0.001F && g > cir.getReturnValue()) {
                cir.setReturnValue(g);
            }
        }
    }
}
