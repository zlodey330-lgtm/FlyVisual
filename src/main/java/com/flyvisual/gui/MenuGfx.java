package com.flyvisual.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

/**
 * Пакетный рисовальщик для декора меню. Все фигуры (прямоугольники, градиенты, круги, повёрнутые бруски)
 * копятся в один буфер и уходят на экран одним вызовом. Раньше каждая фигура была отдельным fill-вызовом
 * со своей отправкой на видеокарту, тысячи таких вызовов и давали лаги.
 * <p>
 * Умеет «подкрашивать» цвета матрицей 3x3 (так все стили меню получают любой выбранный цвет)
 * и сдвигать/масштабировать координаты (для глаза Blood).
 * Порядок слоёв: перед flush() обычные вызовы DrawContext уходят на экран, поэтому всё, что нарисовано
 * через DrawContext до flush(), окажется под фигурами этого буфера.
 */
final class MenuGfx {
	private static final float TWO_PI = 6.2831855f;

	private final BufferBuilder buf = new BufferBuilder(1 << 16);
	private boolean open;
	private DrawContext ctx;
	private Matrix4f matrix;
	private float[] tint;
	private float ox;
	private float oy;
	private float sc = 1.0f;
	/** Ограничение на число сегментов круга (на слабых устройствах меньше). */
	private int maxSegs = 28;

	void begin(DrawContext context) {
		if (open) discard();
		this.ctx = context;
		this.matrix = context.getMatrices().peek().getPositionMatrix();
		this.tint = null;
		this.ox = 0.0f;
		this.oy = 0.0f;
		this.sc = 1.0f;
	}

	void setLite(boolean lite) {
		maxSegs = lite ? 14 : 28;
	}

	/** Матрица подкраски (9 чисел по строкам) или null: цвета без изменений. */
	void setTint(float[] m) {
		this.tint = m;
	}

	void setXform(float x, float y, float scale) {
		ox = x;
		oy = y;
		sc = scale;
	}

	void clearXform() {
		ox = 0.0f;
		oy = 0.0f;
		sc = 1.0f;
	}

	private void discard() {
		open = false;
		try {
			buf.end().release();
		} catch (RuntimeException ignored) {
			// буфер уже закрыт: ничего страшного
		}
	}

	/** Отправляет накопленные фигуры на экран. */
	void flush() {
		if (!open) return;
		open = false;
		if (ctx != null) ctx.draw(); // всё, что было нарисовано раньше, должно лечь под фигуры
		BufferBuilder.BuiltBuffer built = buf.end();
		if (GameRenderer.getPositionColorProgram() == null) {
			built.release();
			return;
		}
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableDepthTest();
		RenderSystem.disableCull();
		BufferRenderer.drawWithGlobalProgram(built);
		RenderSystem.enableCull();
		RenderSystem.enableDepthTest();
	}

	// ---------- вершины ----------

	private void v(float x, float y, int c) {
		if (!open) {
			buf.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
			open = true;
		}
		int a = c >>> 24;
		int r = (c >> 16) & 0xFF;
		int g = (c >> 8) & 0xFF;
		int b = c & 0xFF;
		float[] t = tint;
		if (t != null) {
			int nr = (int) (t[0] * r + t[1] * g + t[2] * b + 0.5f);
			int ng = (int) (t[3] * r + t[4] * g + t[5] * b + 0.5f);
			int nb = (int) (t[6] * r + t[7] * g + t[8] * b + 0.5f);
			r = nr < 0 ? 0 : (nr > 255 ? 255 : nr);
			g = ng < 0 ? 0 : (ng > 255 ? 255 : ng);
			b = nb < 0 ? 0 : (nb > 255 ? 255 : nb);
		}
		buf.vertex(matrix, ox + x * sc, oy + y * sc, 0.0f).color(r, g, b, a).next();
	}

	void tri(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
		v(x1, y1, c1);
		v(x2, y2, c2);
		v(x3, y3, c3);
	}

	void quadC(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3, float x4, float y4, int c4) {
		tri(x1, y1, c1, x2, y2, c2, x3, y3, c3);
		tri(x1, y1, c1, x3, y3, c3, x4, y4, c4);
	}

	// ---------- фигуры ----------

	void rect(float x1, float y1, float x2, float y2, int c) {
		if ((c >>> 24) == 0 || x2 <= x1 || y2 <= y1) return;
		quadC(x1, y1, c, x2, y1, c, x2, y2, c, x1, y2, c);
	}

	void gradV(float x1, float y1, float x2, float y2, int top, int bottom) {
		if (((top | bottom) >>> 24) == 0 || x2 <= x1 || y2 <= y1) return;
		quadC(x1, y1, top, x2, y1, top, x2, y2, bottom, x1, y2, bottom);
	}

	void gradH(float x1, float y1, float x2, float y2, int left, int right) {
		if (((left | right) >>> 24) == 0 || x2 <= x1 || y2 <= y1) return;
		quadC(x1, y1, left, x2, y1, right, x2, y2, right, x1, y2, left);
	}

	/** Прямоугольник с центром в cx, cy и полными размерами w, h, повёрнутый на rot радиан. */
	void box(float cx, float cy, float w, float h, float rot, int c) {
		if ((c >>> 24) == 0) return;
		float hw = w * 0.5f;
		float hh = h * 0.5f;
		if (rot == 0.0f) {
			quadC(cx - hw, cy - hh, c, cx + hw, cy - hh, c, cx + hw, cy + hh, c, cx - hw, cy + hh, c);
			return;
		}
		float cs = (float) Math.cos(rot);
		float sn = (float) Math.sin(rot);
		float ax = cs * hw;
		float ay = sn * hw;
		float bx = -sn * hh;
		float by = cs * hh;
		quadC(cx - ax - bx, cy - ay - by, c, cx + ax - bx, cy + ay - by, c, cx + ax + bx, cy + ay + by, c, cx - ax + bx, cy - ay + by, c);
	}

	/** Линия толщиной thick с разным цветом на концах. */
	void line(float x1, float y1, float x2, float y2, float thick, int c1, int c2) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 1.0e-4f) return;
		float nx = -dy / len * thick * 0.5f;
		float ny = dx / len * thick * 0.5f;
		quadC(x1 + nx, y1 + ny, c1, x2 + nx, y2 + ny, c2, x2 - nx, y2 - ny, c2, x1 - nx, y1 - ny, c1);
	}

	/** Сектор: в центре один цвет, по краю другой. a0..a1 в радианах. */
	void fan(float cx, float cy, float r, float a0, float a1, int cc, int ce) {
		if (r < 0.4f || ((cc | ce) >>> 24) == 0) return;
		int segs = Math.max(5, Math.min(maxSegs, (int) (r * 0.9f) + 6));
		segs = Math.max(3, (int) (segs * Math.abs(a1 - a0) / TWO_PI) + 1);
		float step = (a1 - a0) / segs;
		float px = cx + (float) Math.cos(a0) * r;
		float py = cy + (float) Math.sin(a0) * r;
		for (int i = 1; i <= segs; i++) {
			float a = a0 + step * i;
			float nx = cx + (float) Math.cos(a) * r;
			float ny = cy + (float) Math.sin(a) * r;
			tri(cx, cy, cc, px, py, ce, nx, ny, ce);
			px = nx;
			py = ny;
		}
	}

	void disc(float cx, float cy, float r, int c) {
		fan(cx, cy, r, 0.0f, TWO_PI, c, c);
	}

	/** Верхняя половина круга. */
	void halfDisc(float cx, float cy, float r, int c) {
		fan(cx, cy, r, (float) Math.PI, TWO_PI, c, c);
	}

	/** Мягкое свечение: яркий центр, прозрачный край. */
	void glow(float cx, float cy, float r, int rgb, float alpha) {
		int a = Math.round(Math.max(0.0f, Math.min(1.0f, alpha)) * 255.0f);
		if (a == 0) return;
		fan(cx, cy, r, 0.0f, TWO_PI, (a << 24) | (rgb & 0xFFFFFF), rgb & 0xFFFFFF);
	}

	/** Рамка в 1 пиксель. */
	void outline(float l, float t, float r, float b, int c) {
		rect(l, t, r, t + 1, c);
		rect(l, b - 1, r, b, c);
		rect(l, t + 1, l + 1, b - 1, c);
		rect(r - 1, t + 1, r, b - 1, c);
	}

	/** Кольцо толщиной от r0 до r1. */
	void ring(float cx, float cy, float r0, float r1, int c) {
		if (r1 <= r0 || (c >>> 24) == 0) return;
		int segs = Math.max(8, Math.min(maxSegs + 8, (int) (r1 * 0.9f) + 8));
		float step = TWO_PI / segs;
		float c0 = 1.0f;
		float s0 = 0.0f;
		for (int i = 1; i <= segs; i++) {
			float a = step * i;
			float c1 = (float) Math.cos(a);
			float s1 = (float) Math.sin(a);
			quadC(cx + c0 * r0, cy + s0 * r0, c, cx + c0 * r1, cy + s0 * r1, c, cx + c1 * r1, cy + s1 * r1, c, cx + c1 * r0, cy + s1 * r0, c);
			c0 = c1;
			s0 = s1;
		}
	}

	/** Вертикальная полоса света с мягкими краями (блик), обрезанная по [cl, cr]. */
	void softBand(float center, float half, float y1, float y2, int rgb, float alpha, float cl, float cr) {
		float l = center - half;
		float r = center + half;
		if (r <= cl || l >= cr || alpha <= 0.002f) return;
		int c0 = rgb & 0xFFFFFF;
		int cm = (Math.round(Math.min(1.0f, alpha) * 255.0f) << 24) | c0;
		float a1 = bandAlpha(Math.max(l, cl), center, half, alpha);
		float a2 = bandAlpha(Math.min(r, cr), center, half, alpha);
		int ca1 = (Math.round(a1 * 255.0f) << 24) | c0;
		int ca2 = (Math.round(a2 * 255.0f) << 24) | c0;
		if (center > cl && center < cr) {
			gradH(Math.max(l, cl), y1, center, y2, ca1, cm);
			gradH(center, y1, Math.min(r, cr), y2, cm, ca2);
		} else if (center <= cl) {
			gradH(cl, y1, Math.min(r, cr), y2, ca1, ca2);
		} else {
			gradH(Math.max(l, cl), y1, cr, y2, ca1, ca2);
		}
	}

	private static float bandAlpha(float x, float center, float half, float alpha) {
		float k = 1.0f - Math.abs(x - center) / half;
		return k <= 0.0f ? 0.0f : alpha * k;
	}
}
