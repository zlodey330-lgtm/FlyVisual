package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.Draw3d;
import com.flyvisual.util.Fx3d;
import com.flyvisual.util.Fx3d.V;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Объёмный эффект под ногами при прыжке (10 видов). Виден только тебе. */
public class JumpEffectModule extends Module {
	private static final String[] STYLES = {"Кольцо", "Двойное кольцо", "Ударная волна", "Купол", "Столб света",
			"Спираль", "Руны", "Лотос", "Искры", "Шипы"};
	private static final float LIFE = 1.2f;

	private static final class Fx {
		double x;
		double y;
		double z;
		long start;
		float hue;
	}

	private final DoubleSetting style = addChoice("Effect style", 0, STYLES);
	private final DoubleSetting hue = addSetting("Color (hue)", 200.0, 0.0, 355.0, 5.0);
	private final DoubleSetting rainbow = addToggle("Rainbow", false);
	private final DoubleSetting size = addSetting("Size", 1.0, 0.5, 2.0, 0.1);

	private final List<Fx> list = new ArrayList<>();
	private final Draw3d draw = new Draw3d(true);
	private boolean wasGround = true;

	public JumpEffectModule() {
		super("Jump Effect", "3D effect under your feet when you jump");
		WorldRenderEvents.AFTER_ENTITIES.register(this::render);
	}

	@Override
	public void onTick(MinecraftClient client) {
		ClientPlayerEntity p = client.player;
		if (p == null) return;
		boolean ground = p.isOnGround();
		if (wasGround && !ground && p.getVelocity().y > 0.1 && !p.isTouchingWater() && !p.getAbilities().flying) {
			Fx fx = new Fx();
			fx.x = p.getX();
			fx.y = p.getY();
			fx.z = p.getZ();
			fx.start = System.nanoTime();
			fx.hue = rainbow.getBool() ? (float) ((System.nanoTime() / 1.0e7) % 360.0) : hue.getFloat();
			list.add(fx);
			if (list.size() > 8) list.remove(0);
		}
		wasGround = ground;
	}

	private void render(WorldRenderContext context) {
		if (!isEnabled() || list.isEmpty()) return;
		MatrixStack matrices = context.matrixStack();
		if (matrices == null) return;
		Vec3d cam = context.camera().getPos();
		Matrix4f m = matrices.peek().getPositionMatrix();
		long now = System.nanoTime();
		list.removeIf(f -> (now - f.start) / 1.0e9f > LIFE);

		for (Fx f : list) {
			float t = (now - f.start) / 1.0e9f / LIFE;
			V c = new V((float) (f.x - cam.x), (float) (f.y - cam.y) + 0.02f, (float) (f.z - cam.z));
			draw(m, c, t, f.hue);
		}
		draw.flush();
	}

	private void draw(Matrix4f m, V c, float t, float hueDeg) {
		float e = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
		float fade = Math.max(0.0f, 1.0f - t);
		float late = Math.min(1.0f, (1.0f - t) / 0.35f);
		float R = size.getFloat();
		float[] col = Fx3d.color(hueDeg, 0.75f, 1.0f);
		float[] light = Fx3d.color(hueDeg, 0.25f, 1.0f);
		int seg = 48;

		switch (style.getInt()) {
			case 0 -> {
				float r = 0.25f + 1.5f * R * e;
				Fx3d.band(draw, m, c, Fx3d.X, Fx3d.Z, Fx3d.Y, r - 0.22f * R, 0, r, 0, seg, col, 0.0f, 0.85f * fade);
				Fx3d.band(draw, m, c, Fx3d.X, Fx3d.Z, Fx3d.Y, r, 0, r, 0.16f * R, seg, light, 0.8f * fade, 0.0f);
			}
			case 1 -> {
				for (int k = 0; k < 2; k++) {
					float tt = Math.max(0.0f, Math.min(1.0f, t * 1.4f - k * 0.3f));
					float ee = 1.0f - (1.0f - tt) * (1.0f - tt) * (1.0f - tt);
					float f2 = 1.0f - tt;
					float r = 0.2f + (1.3f + 0.5f * k) * R * ee;
					Fx3d.band(draw, m, c, Fx3d.X, Fx3d.Z, Fx3d.Y, r - 0.12f * R, 0, r, 0, seg, k == 0 ? col : light, 0.0f, 0.9f * f2);
					Fx3d.band(draw, m, c, Fx3d.X, Fx3d.Z, Fx3d.Y, r, 0, r, 0.1f * R, seg, col, 0.7f * f2, 0.0f);
				}
			}
			case 2 -> {
				float r = 0.2f + 1.9f * R * e;
				Fx3d.band(draw, m, c, Fx3d.X, Fx3d.Z, Fx3d.Y, r, 0, r, 0.5f * R * (1.0f - 0.5f * t), seg, col, 0.8f * fade, 0.0f);
				Fx3d.band(draw, m, c, Fx3d.X, Fx3d.Z, Fx3d.Y, r * 0.8f, 0, r, 0, seg, light, 0.0f, 0.5f * fade);
			}
			case 3 -> {
				float rad = 0.3f + 1.2f * R * e;
				for (int k = 0; k < 6; k++) {
					double a0 = Math.toRadians(k * 15.0);
					double a1 = Math.toRadians((k + 1) * 15.0);
					float alpha = 0.5f * fade * (1.0f - k / 7.0f);
					Fx3d.band(draw, m, c, Fx3d.X, Fx3d.Z, Fx3d.Y, (float) Math.cos(a0) * rad, (float) Math.sin(a0) * rad * 0.9f,
							(float) Math.cos(a1) * rad, (float) Math.sin(a1) * rad * 0.9f, 36, k % 2 == 0 ? col : light, alpha, alpha * 0.8f);
				}
			}
			case 4 -> {
				float h = 2.6f * e;
				float w = 0.32f * R * (1.0f - 0.5f * t);
				draw.beam(m, c.x(), c.y(), c.z(), w, h, col[0], col[1], col[2], 0.65f * fade, 0.0f);
				draw.beam(m, c.x(), c.y(), c.z(), w * 0.45f, h * 0.9f, light[0], light[1], light[2], 0.8f * fade, 0.0f);
				Fx3d.band(draw, m, c, Fx3d.X, Fx3d.Z, Fx3d.Y, 0.2f, 0, 0.35f * R + 0.5f * e, 0, seg, col, 0.7f * fade, 0.0f);
			}
			case 5 -> {
				for (int s = 0; s < 2; s++) {
					for (int k = 0; k < 24; k++) {
						float u = k / 24.0f;
						double ang = k * 0.45 + s * Math.PI + t * 7.0;
						float rad = 0.7f * R * (1.0f - 0.4f * u);
						float hh = u * 2.0f * e;
						V pos = new V(c.x() + (float) Math.cos(ang) * rad, c.y() + hh, c.z() + (float) Math.sin(ang) * rad);
						Fx3d.spark(draw, m, pos, 0.09f * (1.0f - 0.5f * u), s == 0 ? col : light, 0.9f * fade * (1.0f - 0.6f * u));
					}
				}
				Fx3d.band(draw, m, c, Fx3d.X, Fx3d.Z, Fx3d.Y, 0.1f, 0, 0.8f * R, 0, seg, col, 0.4f * fade, 0.0f);
			}
			case 6 -> {
				float appear = Math.min(1.0f, t * 6.0f);
				float rr = 1.25f * R * appear;
				float a = 0.85f * late;
				float rot = t * 2.2f;
				Fx3d.band(draw, m, c, Fx3d.X, Fx3d.Z, Fx3d.Y, rr - 0.06f, 0, rr, 0, seg, col, a, a);
				Fx3d.band(draw, m, c, Fx3d.X, Fx3d.Z, Fx3d.Y, rr * 0.5f - 0.04f, 0, rr * 0.5f, 0, seg, light, a, a);
				for (int k = 0; k < 5; k++) {
					double a0 = rot + k * Math.PI * 2.0 / 5.0;
					double a1 = rot + ((k + 2) % 5) * Math.PI * 2.0 / 5.0;
					V s = new V(c.x() + (float) Math.cos(a0) * rr, c.y() + 0.01f, c.z() + (float) Math.sin(a0) * rr);
					V en = new V(c.x() + (float) Math.cos(a1) * rr, c.y() + 0.01f, c.z() + (float) Math.sin(a1) * rr);
					Fx3d.ribbon(draw, m, s, en, 0.06f, light, a);
					Fx3d.spark(draw, m, new V(s.x(), s.y() + 0.04f, s.z()), 0.08f, col, a);
				}
			}
			case 7 -> {
				float open = Math.min(1.0f, t * 3.0f);
				float a = 0.7f * late;
				for (int k = 0; k < 8; k++) {
					double ang = k * Math.PI / 4.0 + t * 0.6;
					V dir = new V((float) Math.cos(ang), 0, (float) Math.sin(ang));
					V side = new V(-dir.z(), 0, dir.x());
					float rb = 0.15f;
					float rm = 0.15f + 0.45f * R * open;
					float rt = 0.15f + 1.0f * R * open;
					V bl = new V(c.x() + dir.x() * rb - side.x() * 0.07f, c.y(), c.z() + dir.z() * rb - side.z() * 0.07f);
					V br = new V(c.x() + dir.x() * rb + side.x() * 0.07f, c.y(), c.z() + dir.z() * rb + side.z() * 0.07f);
					V ml = new V(c.x() + dir.x() * rm - side.x() * 0.2f * R, c.y() + 0.3f * R * open, c.z() + dir.z() * rm - side.z() * 0.2f * R);
					V mr = new V(c.x() + dir.x() * rm + side.x() * 0.2f * R, c.y() + 0.3f * R * open, c.z() + dir.z() * rm + side.z() * 0.2f * R);
					V tip = new V(c.x() + dir.x() * rt, c.y() + 0.65f * R * open, c.z() + dir.z() * rt);
					Fx3d.quad(draw, m, bl, br, mr, ml, col, a, a, a, a);
					Fx3d.quad(draw, m, ml, mr, tip, tip, light, a, a, a * 0.5f, a * 0.5f);
				}
			}
			case 8 -> {
				for (int k = 0; k < 30; k++) {
					double ang = Fx3d.rnd(k, 1) * Math.PI * 2.0;
					float speed = (0.8f + 1.6f * Fx3d.rnd(k, 2)) * R;
					float up = 1.2f + 2.4f * Fx3d.rnd(k, 3);
					float r = speed * t * 1.1f;
					float y = up * t - 4.2f * t * t;
					if (y < -0.02f) continue;
					V pos = new V(c.x() + (float) Math.cos(ang) * r, c.y() + y, c.z() + (float) Math.sin(ang) * r);
					Fx3d.spark(draw, m, pos, 0.07f, k % 3 == 0 ? light : col, 0.95f * late);
				}
				Fx3d.band(draw, m, c, Fx3d.X, Fx3d.Z, Fx3d.Y, 0.2f + 0.8f * R * e - 0.1f, 0, 0.2f + 0.8f * R * e, 0, seg, col, 0.0f, 0.6f * fade);
			}
			default -> {
				int n = 14;
				float grow = (float) Math.sin(Math.PI * Math.min(1.0f, t * 1.3f));
				float a = 0.85f * late;
				for (int k = 0; k < n; k++) {
					double ang = k * Math.PI * 2.0 / n;
					float rr = (0.9f + 0.25f * (k % 2)) * R;
					V base = new V(c.x() + (float) Math.cos(ang) * rr, c.y(), c.z() + (float) Math.sin(ang) * rr);
					float h = (0.75f + 0.35f * (k % 2)) * R * grow;
					V apex = new V(base.x() - (float) Math.cos(ang) * 0.1f, base.y() + h, base.z() - (float) Math.sin(ang) * 0.1f);
					Fx3d.pyramid(draw, m, base, Fx3d.X, Fx3d.Z, 0.08f * R, apex, k % 2 == 0 ? col : light, a, a * 0.7f);
				}
				Fx3d.band(draw, m, c, Fx3d.X, Fx3d.Z, Fx3d.Y, 0.0f, 0, 1.0f * R, 0, seg, col, 0.35f * late, 0.0f);
			}
		}
	}
}
