package com.flyvisual.mixin;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Позволяет записать значение опции в обход проверки диапазона.
 * Нужно для Fullbright: ванильная яркость ограничена 0..1, а нам нужно больше.
 */
@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor<T> {
	@Accessor("value")
	void flyvisual$setValue(T value);
}
