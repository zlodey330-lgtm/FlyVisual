package com.flyvisual.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

/**
 * Простое рисование закрашенных кубов, лучей и произвольных четырёхугольников в мире
 * (без текстур и освещения). Использование: new Draw3d(true), несколько вызовов, затем flush().
 * Если ничего не нарисовано, flush() ничего не делает.
 */
public final class Draw3d {
	private final boolean translucent;
	private BufferBuilder buf;
	/** Кто сейчас пишет в общий буфер Tessellator: двум Draw3d одновременно нельзя. */
	private static Draw3d active;

	public Draw3d(boolean translucent) {
		this.translucent = translucent;
	}

	private BufferBuilder buffer() {
		if (active != null && active != this) {
			// Общий буфер занят другим Draw3d: сначала дорисовываем его, иначе будет «Already building»
			active.flush();
		}
		if (buf == null) {
			active = this;
			RenderSystem.setShader(GameRenderer::getPositionColorProgram);
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.disableCull();
			RenderSystem.enableDepthTest();
			if (translucent) {
				RenderSystem.depthMask(false);
			}
			buf = Tessellator.getInstance().getBuffer();
			buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		}
		return buf;
	}

	/** Отправляет нарисованное на экран. */
	public void flush() {
		if (buf == null) return;
		Tessellator.getInstance().draw();
		if (translucent) {
			RenderSystem.depthMask(true);
		}
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
		buf = null;
		if (active == this) {
			active = null;
		}
	}

	private void v(Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
		buffer().vertex(m, x, y, z).color(r, g, b, a).next();
	}

	/** Одна вершина с готовым цветом (для заранее собранных моделей). */
	public void vertex(Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
		v(m, x, y, z, r, g, b, a);
	}

	/** Куб от (x0,y0,z0) до (x1,y1,z1). Грани слегка затемнены по сторонам, чтобы был виден объём. */
	public void cube(Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
					 float r, float g, float b, float a) {
		float top = 1.0f;
		float side1 = 0.84f;
		float side2 = 0.72f;
		float bottom = 0.58f;

		v(m, x0, y1, z0, r * top, g * top, b * top, a);
		v(m, x0, y1, z1, r * top, g * top, b * top, a);
		v(m, x1, y1, z1, r * top, g * top, b * top, a);
		v(m, x1, y1, z0, r * top, g * top, b * top, a);

		v(m, x0, y0, z0, r * bottom, g * bottom, b * bottom, a);
		v(m, x1, y0, z0, r * bottom, g * bottom, b * bottom, a);
		v(m, x1, y0, z1, r * bottom, g * bottom, b * bottom, a);
		v(m, x0, y0, z1, r * bottom, g * bottom, b * bottom, a);

		v(m, x0, y0, z1, r * side1, g * side1, b * side1, a);
		v(m, x1, y0, z1, r * side1, g * side1, b * side1, a);
		v(m, x1, y1, z1, r * side1, g * side1, b * side1, a);
		v(m, x0, y1, z1, r * side1, g * side1, b * side1, a);

		v(m, x1, y0, z0, r * side1, g * side1, b * side1, a);
		v(m, x0, y0, z0, r * side1, g * side1, b * side1, a);
		v(m, x0, y1, z0, r * side1, g * side1, b * side1, a);
		v(m, x1, y1, z0, r * side1, g * side1, b * side1, a);

		v(m, x1, y0, z1, r * side2, g * side2, b * side2, a);
		v(m, x1, y0, z0, r * side2, g * side2, b * side2, a);
		v(m, x1, y1, z0, r * side2, g * side2, b * side2, a);
		v(m, x1, y1, z1, r * side2, g * side2, b * side2, a);

		v(m, x0, y0, z0, r * side2, g * side2, b * side2, a);
		v(m, x0, y0, z1, r * side2, g * side2, b * side2, a);
		v(m, x0, y1, z1, r * side2, g * side2, b * side2, a);
		v(m, x0, y1, z0, r * side2, g * side2, b * side2, a);
	}

	/** Вертикальный луч: четыре боковые грани, внизу яркий, вверху прозрачный. */
	public void beam(Matrix4f m, float cx, float y0, float cz, float halfWidth, float height,
					 float r, float g, float b, float aBottom, float aTop) {
		float x0 = cx - halfWidth;
		float x1 = cx + halfWidth;
		float z0 = cz - halfWidth;
		float z1 = cz + halfWidth;
		float y1 = y0 + height;

		v(m, x0, y0, z1, r, g, b, aBottom);
		v(m, x1, y0, z1, r, g, b, aBottom);
		v(m, x1, y1, z1, r, g, b, aTop);
		v(m, x0, y1, z1, r, g, b, aTop);

		v(m, x1, y0, z0, r, g, b, aBottom);
		v(m, x0, y0, z0, r, g, b, aBottom);
		v(m, x0, y1, z0, r, g, b, aTop);
		v(m, x1, y1, z0, r, g, b, aTop);

		v(m, x1, y0, z1, r, g, b, aBottom);
		v(m, x1, y0, z0, r, g, b, aBottom);
		v(m, x1, y1, z0, r, g, b, aTop);
		v(m, x1, y1, z1, r, g, b, aTop);

		v(m, x0, y0, z0, r, g, b, aBottom);
		v(m, x0, y0, z1, r, g, b, aBottom);
		v(m, x0, y1, z1, r, g, b, aTop);
		v(m, x0, y1, z0, r, g, b, aTop);
	}

	/** Короткое имя для vertex (его используют модели питомцев). */
	public void vert(Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
		vertex(m, x, y, z, r, g, b, a);
	}
}
