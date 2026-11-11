package com.flyvisual.util;

/**
 * Мультяшный воксельный питомец (в стиле «воксельного арта»): большая голова, чёткие кубики с пиксельной текстурой.
 * Один класс для всех: ходят на четырёх лапах, прыгают, летают с крыльями, парят (призрак), прыгают как слайм
 * или висят в воздухе с винтами (дрон). Какие части есть, решает фабрика ChibiFactory. Без зависимостей от Minecraft.
 */
public final class ChibiPet extends PetModel {
	public static final int WALK = 0;
	public static final int HOP = 1;
	public static final int WINGS = 2;
	public static final int GHOST = 3;
	public static final int SLIME = 4;
	public static final int DRONE = 5;

	public int mode;
	public Part body;
	public Part head;
	public Part earL;
	public Part earR;
	public Part legFL;
	public Part legFR;
	public Part legBL;
	public Part legBR;
	public Part tail;
	public Part wingL;
	public Part wingR;
	public Part armL;
	public Part armR;
	public Part hat;
	public Part[] props = new Part[0];
	/** Амплитуда шага и покачивание тела. */
	public float swing = 30.0f;
	public float bob = 0.012f;
	/** Хвост: 0 нет, 1 кошачий (вверх), 2 пушистый (волочится), 3 помпон. */
	public int tailStyle;

	private static float sin(float v) {
		return (float) Math.sin(v);
	}

	@Override
	public void animate(float time, float walk, float moving) {
		for (Part p : parts) {
			p.reset();
		}
		float breathe = sin(time * 2.0f);
		switch (mode) {
			case WALK -> {
				body.ty = bob * Math.abs(sin(walk)) * moving;
				body.sy = 1.0f + 0.015f * breathe;
				legFL.rx = swing * sin(walk) * moving;
				legBR.rx = swing * sin(walk) * moving;
				legFR.rx = -swing * sin(walk) * moving;
				legBL.rx = -swing * sin(walk) * moving;
				head.rx = -2.5f * sin(2.0f * walk) * moving + 2.0f * sin(time * 1.3f);
				head.ry = 10.0f * sin(time * 0.6f) * (1.0f - 0.7f * moving);
				head.rz = 3.0f * sin(time * 0.8f + 1.0f) * (1.0f - moving);
				ears(time, moving);
				tailAnim(time, moving);
			}
			case HOP -> {
				float h = Math.abs(sin(walk * 0.8f)) * moving;
				body.ty = 0.07f * h;
				body.rx = -8.0f * sin(walk * 0.8f) * moving;
				legFL.rx = -28.0f * sin(walk * 0.8f) * moving;
				legFR.rx = -28.0f * sin(walk * 0.8f) * moving;
				legBL.rx = 34.0f * sin(walk * 0.8f) * moving;
				legBR.rx = 34.0f * sin(walk * 0.8f) * moving;
				body.sy = 1.0f + 0.02f * breathe;
				head.rx = 4.0f * sin(time * 1.5f) - 5.0f * h;
				head.ry = 12.0f * sin(time * 0.5f) * (1.0f - 0.6f * moving);
				ears(time, moving);
				tailAnim(time, moving);
			}
			case WINGS -> {
				// Стоит: крылья сложены и неподвижны. Идёт: мягкие небольшие взмахи
				float flap = sin(time * 8.0f);
				body.ty = 0.03f * sin(time * 1.9f);
				body.rx = 7.0f * moving;
				if (wingL != null) {
					wingL.rz = 14.0f + 24.0f * moving * flap;
					wingR.rz = -14.0f - 24.0f * moving * flap;
				}
				head.ry = 12.0f * sin(time * 0.7f) * (1.0f - 0.5f * moving);
				head.rz = 4.0f * sin(time * 1.1f);
				if (earL != null) {
					earL.rz = 2.0f * sin(time * 1.2f);
					earR.rz = -2.0f * sin(time * 1.2f + 1.0f);
				}
				if (tail != null) tail.rx = 10.0f * sin(time * 2.2f);
				if (legFL != null) {
					legFL.rx = -18.0f;
					legFR.rx = -18.0f;
				}
			}
			case GHOST -> {
				body.ty = 0.04f * sin(time * 2.0f);
				body.rx = 8.0f * moving;
				body.rz = 3.0f * sin(time * 1.4f);
				if (armL != null) {
					armL.rz = 25.0f + 14.0f * moving * sin(time * 4.0f);
					armR.rz = -25.0f - 14.0f * moving * sin(time * 4.0f + 1.2f);
				}
				if (hat != null) {
					hat.rz = 5.0f * sin(time * 1.6f);
					hat.rx = -6.0f * moving;
				}
				head.ry = 8.0f * sin(time * 0.9f);
			}
			case SLIME -> {
				float hop = Math.abs(sin(walk * 0.7f)) * moving;
				body.ty = 0.09f * hop;
				float squash = 1.0f - 0.14f * (1.0f - hop) * moving + 0.05f * breathe;
				body.sy = squash;
				body.sx = 1.0f / (float) Math.sqrt(squash);
				body.sz = 1.0f / (float) Math.sqrt(squash);
				if (head != null) head.ry = 6.0f * sin(time * 0.8f);
			}
			default -> {
				// Дрон: висит, чуть наклоняется вперёд на ходу, винты вращаются
				body.ty = 0.03f * sin(time * 2.2f);
				body.rx = 12.0f * moving + 2.0f * sin(time * 1.7f);
				body.rz = 3.0f * sin(time * 1.3f);
				for (int i = 0; i < props.length; i++) {
					props[i].ry = (i % 2 == 0 ? 1 : -1) * time * 2200.0f;
				}
			}
		}
	}

	private void ears(float time, float moving) {
		if (earL == null) return;
		float twL = (float) Math.pow(Math.max(0.0, Math.sin(time * 1.1)), 14.0);
		float twR = (float) Math.pow(Math.max(0.0, Math.sin(time * 0.83 + 2.0)), 14.0);
		earL.rz = -14.0f * twL;
		earR.rz = 14.0f * twR;
		earL.rx = 4.0f * moving - 5.0f * twL;
		earR.rx = 4.0f * moving - 5.0f * twR;
	}

	private void tailAnim(float time, float moving) {
		if (tail == null) return;
		float wave = sin(time * 2.4f);
		switch (tailStyle) {
			case 1 -> {
				tail.rx = 20.0f - 10.0f * moving;
				tail.ry = wave * (14.0f + 6.0f * moving);
			}
			case 2 -> {
				tail.rx = 8.0f + 10.0f * moving;
				tail.ry = wave * (10.0f + 6.0f * moving);
			}
			default -> {
				tail.ry = wave * 8.0f;
				tail.rx = 3.0f * sin(time * 3.0f);
			}
		}
	}
}
