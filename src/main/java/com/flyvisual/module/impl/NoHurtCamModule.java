package com.flyvisual.module.impl;

import com.flyvisual.module.Module;

/** Убирает тряску камеры при получении урона. Сама логика в GameRendererMixin. */
public class NoHurtCamModule extends Module {
	public NoHurtCamModule() {
		super("NoHurtCam", "Removes camera shake when hit");
	}
}
