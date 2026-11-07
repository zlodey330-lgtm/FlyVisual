package com.flyvisual.util;

import net.minecraft.client.render.VertexConsumer;

/** Оболочка над VertexConsumer: перекрашивает все вершины в заданный цвет и прозрачность. */
public final class TintConsumer implements VertexConsumer {
	private final VertexConsumer delegate;
	private final int r;
	private final int g;
	private final int b;
	private final int a;

	public TintConsumer(VertexConsumer delegate, float r, float g, float b, float a) {
		this.delegate = delegate;
		this.r = clamp(r);
		this.g = clamp(g);
		this.b = clamp(b);
		this.a = clamp(a);
	}

	private static int clamp(float v) {
		return Math.max(0, Math.min(255, Math.round(v * 255.0f)));
	}

	@Override
	public VertexConsumer vertex(double x, double y, double z) {
		delegate.vertex(x, y, z);
		return this;
	}

	@Override
	public VertexConsumer color(int red, int green, int blue, int alpha) {
		// Исходный цвет (текстура предмета) множим на наш оттенок, чтобы сохранить рисунок
		delegate.color(red * r / 255, green * g / 255, blue * b / 255, alpha * a / 255);
		return this;
	}

	@Override
	public VertexConsumer texture(float u, float v) {
		delegate.texture(u, v);
		return this;
	}

	@Override
	public VertexConsumer overlay(int u, int v) {
		delegate.overlay(u, v);
		return this;
	}

	@Override
	public VertexConsumer light(int u, int v) {
		delegate.light(u, v);
		return this;
	}

	@Override
	public VertexConsumer normal(float x, float y, float z) {
		delegate.normal(x, y, z);
		return this;
	}

	@Override
	public void next() {
		delegate.next();
	}

	@Override
	public void fixedColor(int red, int green, int blue, int alpha) {
		delegate.fixedColor(red, green, blue, alpha);
	}

	@Override
	public void unfixColor() {
		delegate.unfixColor();
	}
}
