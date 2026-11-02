package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;

/**
 * Сдвиг и размер предмета в руке (оружие, щит, инструмент).
 * Сама логика в HeldItemRendererMixin.
 */
public class HandOffsetModule extends Module {
	private final DoubleSetting offsetX = addSetting("X (right)", 0.0, -1.0, 1.0, 0.05);
	private final DoubleSetting offsetY = addSetting("Y (up)", 0.0, -1.0, 1.0, 0.05);
	private final DoubleSetting offsetZ = addSetting("Z (closer)", 0.0, -1.0, 1.0, 0.05);
	private final DoubleSetting scale = addSetting("Scale", 1.0, 0.3, 1.5, 0.05);

	public HandOffsetModule() {
		super("Hand Offset", "Move and resize items in your hand");
	}

	public float getX() {
		return offsetX.getFloat();
	}

	public float getY() {
		return offsetY.getFloat();
	}

	public float getZ() {
		return offsetZ.getFloat();
	}

	public float getScale() {
		return scale.getFloat();
	}
}
