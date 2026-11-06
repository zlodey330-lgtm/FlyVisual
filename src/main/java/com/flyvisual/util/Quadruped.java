package com.flyvisual.util;

/**
 * Четвероногое (кошка, хаски, лиса): тело, голова с ушами и веками, четыре двухсоставные лапы
 * и хвост из нескольких звеньев. Здесь только анимация, формы строит PetFactory.
 */
public final class Quadruped extends PetModel {
	public Part body;
	public Part head;
	public Part earL;
	public Part earR;
	public Part lidL;
	public Part lidR;
	public Part fUpL;
	public Part fLowL;
	public Part fUpR;
	public Part fLowR;
	public Part hUpL;
	public Part hLowL;
	public Part hUpR;
	public Part hLowR;
	public Part[] tail;

	/** Стиль хвоста: 0 кошка (подняты и изогнут), 1 хаски (баранкой), 2 лиса (низкий пушистый). */
	public int tailStyle;
	public float foreSwing = 32.0f;
	public float hindSwing = 28.0f;
	public float bob = 0.012f;
	/** Скорость шага относительно фазы (для разных животных). */
	public float gait = 1.0f;

	private static float sin(float v) {
		return (float) Math.sin(v);
	}

	private static float cos(float v) {
		return (float) Math.cos(v);
	}

	@Override
	public void animate(float time, float walk, float moving) {
		for (Part p : parts) {
			p.reset();
		}
		float w = walk * gait;

		// Тело: дыхание и покачивание на ходу
		float breathe = sin(time * 1.9f);
		body.sy = 1.0f + 0.014f * breathe;
		body.sz = 1.0f + 0.006f * breathe;
		body.ty = bob * Math.abs(sin(w)) * moving;
		body.rx = 2.2f * sin(2.0f * w) * moving;

		// Лапы: рысь по диагонали (лево-перед + право-зад, потом наоборот)
		leg(fUpL, fLowL, w, 0.0f, foreSwing, 1.0f, moving);
		leg(hUpR, hLowR, w, 0.0f, hindSwing, 0.8f, moving);
		leg(fUpR, fLowR, w, (float) Math.PI, foreSwing, 1.0f, moving);
		leg(hUpL, hLowL, w, (float) Math.PI, hindSwing, 0.8f, moving);

		// Голова смотрит по сторонам, на ходу чуть кивает
		head.rx = -2.5f * sin(2.0f * w) * moving + 1.8f * sin(time * 1.3f);
		head.ry = 9.0f * sin(time * 0.6f) * (1.0f - 0.7f * moving) + 2.0f * sin(time * 2.3f);
		head.rz = 2.5f * sin(time * 0.6f + 1.0f) * (1.0f - moving);

		// Уши иногда вздрагивают
		float twL = (float) Math.pow(Math.max(0.0, Math.sin(time * 1.1)), 14.0);
		float twR = (float) Math.pow(Math.max(0.0, Math.sin(time * 0.83 + 2.0)), 14.0);
		earL.rz = -16.0f * twL;
		earR.rz = 16.0f * twR;
		earL.rx = 5.0f * moving - 6.0f * twL;
		earR.rx = 5.0f * moving - 6.0f * twR;

		// Моргание
		float cycle = (time % 4.6f) / 4.6f;
		boolean blink = cycle < 0.026f || (cycle > 0.07f && cycle < 0.094f && time % 9.0f < 4.6f);
		lidL.visible = blink;
		lidR.visible = blink;

		animateTail(time, moving);
	}

	private void leg(Part up, Part low, float w, float phase, float swing, float flex, float moving) {
		float a = sin(w + phase);
		up.rx = swing * a * moving;
		float fold = Math.max(0.0f, -cos(w + phase));
		low.rx = 50.0f * flex * fold * moving;
	}

	private void animateTail(float time, float moving) {
		int n = tail.length;
		for (int i = 0; i < n; i++) {
			Part t = tail[i];
			float wave = sin(time * (tailStyle == 1 ? 2.2f : 2.6f) - i * 0.7f);
			switch (tailStyle) {
				case 0 -> {
					// Кошка: хвост поднят, кончик загнут, лениво качается
					float base = i == 0 ? 52.0f - 14.0f * moving : (i < n - 1 ? 7.0f : 22.0f);
					t.rx = base;
					t.ry = wave * (7.0f + 4.0f * moving) * (0.5f + i * 0.25f);
				}
				case 1 -> {
					// Хаски: пушистая баранка над спиной
					t.rx = i == 0 ? 55.0f : 24.0f;
					t.ry = wave * 3.5f * (0.4f + i * 0.15f);
				}
				default -> {
					// Лиса: тяжёлый хвост волочится низко и мягко качается
					t.rx = i == 0 ? 10.0f + 10.0f * moving : 2.0f;
					t.ry = wave * (9.0f + 6.0f * moving) * (0.4f + i * 0.2f);
				}
			}
		}
	}
}
