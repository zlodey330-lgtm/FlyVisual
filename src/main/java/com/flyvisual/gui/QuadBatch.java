package com.flyvisual.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Пакетный рендер цветных фигур. Обычный DrawContext.fill отправляет каждый прямоугольник видеокарте отдельным
 * вызовом, и когда их тысячи, FPS падает. Здесь все фигуры копятся в один буфер и рисуются разом.
 * Вершины могут иметь разный цвет, поэтому градиенты и мягкое свечение получаются плавными и дешёвыми.
 * <p>
 * Использование: begin(matrices), потом фигуры, в конце flush(). Для свечения включай additive(true).
 */
public final class QuadBatch {
	public static final float TWO_PI = 6.2831855f;

	private BufferBuilder buf;
	private boolean open;
	private boolean additive;
	private MatrixStack stack;

	// ------------------------------------------------------------------ цвета

	/** Цвет из прозрачности 0..1 и RGB. */
	public static int col(float alpha, int rgb) {
		int a = (int) (alpha * 255.0f);
		if (a < 0) a = 0;
		if (a > 255) a = 255;
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	/** Тот же цвет, но с прозрачностью, умноженной на f. */
	public static int fade(int c, float f) {
		int a = (int) ((c >>> 24) * f);
		if (a < 0) a = 0;
		if (a > 255) a = 255;
		return (a << 24) | (c & 0xFFFFFF);
	}

	public static int lerp(int c0, int c1, float t) {
		int a = (int) ((c0 >>> 24) + ((c1 >>> 24) - (c0 >>> 24)) * t);
		int r = (int) (((c0 >> 16) & 0xFF) + (((c1 >> 16) & 0xFF) - ((c0 >> 16) & 0xFF)) * t);
		int g = (int) (((c0 >> 8) & 0xFF) + (((c1 >> 8) & 0xFF) - ((c0 >> 8) & 0xFF)) * t);
		int b = (int) ((c0 & 0xFF) + ((c1 & 0xFF) - (c0 & 0xFF)) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	// ------------------------------------------------------------------ управление

	public void begin(MatrixStack stack) {
		flush();
		this.stack = stack;
		this.additive = false;
	}

	/** Режим сложения света: яркие места накладываются и светятся. Смена режима отправляет накопленное. */
	public void additive(boolean on) {
		if (additive != on) {
			flush();
			additive = on;
		}
	}

	/** Рисует всё накопленное. */
	public void flush() {
		if (!open) return;
		open = false;
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		RenderSystem.enableBlend();
		if (additive) {
			RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
		} else {
			RenderSystem.defaultBlendFunc();
		}
		RenderSystem.disableDepthTest();
		RenderSystem.disableCull();
		BufferRenderer.drawWithGlobalProgram(buf.end());
		RenderSystem.enableCull();
		RenderSystem.enableDepthTest();
		RenderSystem.defaultBlendFunc();
	}

	private void vertex(float x, float y, int c) {
		if (!open) {
			buf = Tessellator.getInstance().getBuffer();
			buf.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
			open = true;
		}
		buf.vertex(stack.peek().getPositionMatrix(), x, y, 0.0f)
				.color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, c >>> 24)
				.next();
	}

	// ------------------------------------------------------------------ фигуры

	public void tri(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
		vertex(x1, y1, c1);
		vertex(x2, y2, c2);
		vertex(x3, y3, c3);
	}

	/** Четырёхугольник, вершины по кругу; у каждой свой цвет. */
	public void quad(float x1, float y1, int c1, float x2, float y2, int c2,
					 float x3, float y3, int c3, float x4, float y4, int c4) {
		tri(x1, y1, c1, x2, y2, c2, x3, y3, c3);
		tri(x1, y1, c1, x3, y3, c3, x4, y4, c4);
	}

	public void rect(float x1, float y1, float x2, float y2, int c) {
		quad(x1, y1, c, x2, y1, c, x2, y2, c, x1, y2, c);
	}

	/** Прямоугольник с вертикальным градиентом. */
	public void gradV(float x1, float y1, float x2, float y2, int top, int bottom) {
		quad(x1, y1, top, x2, y1, top, x2, y2, bottom, x1, y2, bottom);
	}

	/** Прямоугольник с горизонтальным градиентом. */
	public void gradH(float x1, float y1, float x2, float y2, int left, int right) {
		quad(x1, y1, left, x2, y1, right, x2, y2, right, x1, y2, left);
	}

	private static int segments(float radius) {
		int n = (int) (radius * 1.3f) + 10;
		return n > 84 ? 84 : n;
	}

	/** Сектор эллипса: в центре один цвет, по краю другой (плавное свечение или градиентный круг). */
	public void fan(float cx, float cy, float rx, float ry, float a0, float a1, int centerColor, int edgeColor) {
		if (rx <= 0.0f || ry <= 0.0f) return;
		int segs = Math.max(6, (int) (segments(Math.max(rx, ry)) * Math.abs(a1 - a0) / TWO_PI) + 2);
		float step = (a1 - a0) / segs;
		float px = cx + (float) Math.cos(a0) * rx;
		float py = cy + (float) Math.sin(a0) * ry;
		for (int i = 1; i <= segs; i++) {
			float a = a0 + step * i;
			float nx = cx + (float) Math.cos(a) * rx;
			float ny = cy + (float) Math.sin(a) * ry;
			tri(cx, cy, centerColor, px, py, edgeColor, nx, ny, edgeColor);
			px = nx;
			py = ny;
		}
	}

	public void glow(float cx, float cy, float r, int centerColor, int edgeColor) {
		fan(cx, cy, r, r, 0.0f, TWO_PI, centerColor, edgeColor);
	}

	public void disc(float cx, float cy, float r, int c) {
		fan(cx, cy, r, r, 0.0f, TWO_PI, c, c);
	}

	/** Дуга толщиной от r0 до r1; цвет плавно меняется от c0 в начале до c1 в конце. */
	public void arc(float cx, float cy, float r0, float r1, float a0, float a1, int c0, int c1) {
		if (r1 <= r0) return;
		int segs = Math.max(3, (int) (segments(r1) * Math.abs(a1 - a0) / TWO_PI) + 1);
		float step = (a1 - a0) / segs;
		float cos0 = (float) Math.cos(a0);
		float sin0 = (float) Math.sin(a0);
		int colorPrev = c0;
		for (int i = 1; i <= segs; i++) {
			float a = a0 + step * i;
			float cos1 = (float) Math.cos(a);
			float sin1 = (float) Math.sin(a);
			int colorNext = lerp(c0, c1, (float) i / segs);
			quad(cx + cos0 * r0, cy + sin0 * r0, colorPrev,
					cx + cos0 * r1, cy + sin0 * r1, colorPrev,
					cx + cos1 * r1, cy + sin1 * r1, colorNext,
					cx + cos1 * r0, cy + sin1 * r0, colorNext);
			cos0 = cos1;
			sin0 = sin1;
			colorPrev = colorNext;
		}
	}

	public void ring(float cx, float cy, float r0, float r1, int c) {
		arc(cx, cy, r0, r1, 0.0f, TWO_PI, c, c);
	}

	/** Кольцо с градиентом поперёк: у внутреннего края один цвет, у внешнего другой (мягкие волны света). */
	public void ringBand(float cx, float cy, float r0, float r1, int inner, int outer) {
		if (r1 <= r0) return;
		int segs = segments(r1);
		float step = TWO_PI / segs;
		float cos0 = 1.0f;
		float sin0 = 0.0f;
		for (int i = 1; i <= segs; i++) {
			float a = step * i;
			float cos1 = (float) Math.cos(a);
			float sin1 = (float) Math.sin(a);
			quad(cx + cos0 * r0, cy + sin0 * r0, inner,
					cx + cos0 * r1, cy + sin0 * r1, outer,
					cx + cos1 * r1, cy + sin1 * r1, outer,
					cx + cos1 * r0, cy + sin1 * r0, inner);
			cos0 = cos1;
			sin0 = sin1;
		}
	}

	/** Линия с цветом на каждом конце (хвосты частиц). */
	public void line(float x1, float y1, float x2, float y2, float thick, int c1, int c2) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 1.0e-4f) return;
		float nx = -dy / len * thick * 0.5f;
		float ny = dx / len * thick * 0.5f;
		quad(x1 + nx, y1 + ny, c1, x2 + nx, y2 + ny, c2, x2 - nx, y2 - ny, c2, x1 - nx, y1 - ny, c1);
	}

	/** Повёрнутый прямоугольник: hw, hh полуразмеры, rot поворот в радианах. */
	public void rotRect(float cx, float cy, float hw, float hh, float rot, int c) {
		float cs = (float) Math.cos(rot);
		float sn = (float) Math.sin(rot);
		float ax = cs * hw;
		float ay = sn * hw;
		float bx = -sn * hh;
		float by = cs * hh;
		quad(cx - ax - bx, cy - ay - by, c,
				cx + ax - bx, cy + ay - by, c,
				cx + ax + bx, cy + ay + by, c,
				cx - ax + bx, cy - ay + by, c);
	}

	/** Блик-звёздочка с четырьмя лучами. */
	public void star(float cx, float cy, float size, int c) {
		int clear = c & 0xFFFFFF;
		float w = size * 0.11f;
		quad(cx - size, cy, clear, cx, cy - w, c, cx + size, cy, clear, cx, cy + w, c);
		quad(cx, cy - size, clear, cx + w, cy, c, cx, cy + size, clear, cx - w, cy, c);
	}

	/** Ромб-линза: в центре яркий цвет, к краям прозрачный (полоса света, блики). */
	public void lens(float cx, float cy, float hw, float hh, int centerColor, int edgeColor) {
		tri(cx - hw, cy, edgeColor, cx, cy - hh, edgeColor, cx, cy, centerColor);
		tri(cx, cy - hh, edgeColor, cx + hw, cy, edgeColor, cx, cy, centerColor);
		tri(cx + hw, cy, edgeColor, cx, cy + hh, edgeColor, cx, cy, centerColor);
		tri(cx, cy + hh, edgeColor, cx - hw, cy, edgeColor, cx, cy, centerColor);
	}
}
