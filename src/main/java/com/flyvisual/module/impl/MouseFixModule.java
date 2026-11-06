package com.flyvisual.module.impl;

import com.flyvisual.module.Module;

/** Убирает задержку мыши при просадках FPS и в полноэкранном режиме (F11). Логика в MouseLatencyMixin. */
public class MouseFixModule extends Module {
	public MouseFixModule() {
		super("Mouse Fix", "Reduces mouse delay when FPS drops or in fullscreen");
	}
}
