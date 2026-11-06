package com.flyvisual.module.impl;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.module.ItemSearchScreen;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.ItemSelection;
import com.flyvisual.module.Module;
import com.flyvisual.util.Draw3d;
import com.flyvisual.util.Fx3d;
import com.flyvisual.util.Fx3d.V;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hands: объёмный шейдер-эффект вокруг предмета в руке (10 видов). Предметы выбираются через поиск;
 * если ничего не выбрано, эффект у любого предмета. Переключатель «Шейдер» выключает сам эффект,
 * а модуль остаётся, так что FPS не тратится. Виден только тебе.
 */
public class HandsModule extends Module implements ItemSelection {
	private static final String[] STYLES = {"Ореол", "Орбита искр", "Гироскоп", "Пламя", "Молнии", "Луч",
			"Кристаллы", "Спираль", "Пульс", "Звёздная пыль"};

	private final Set<Item> picked = new HashSet<>();

	private final DoubleSetting shader = addToggle("Shader", true);
	private final DoubleSetting style = addChoice("Effect style", 0, STYLES);
	private final DoubleSetting hue = addSetting("Color (hue)", 280.0, 0.0, 355.0, 5.0);
	private final DoubleSetting rainbow = addToggle("Rainbow", false);
	private final DoubleSetting size = addSetting("Size", 1.0, 0.5, 2.0, 0.1);
	private final DoubleSetting pick = addButton("Choose items", () -> picked.isEmpty() ? "Все" : String.valueOf(picked.size()),
			() -> MinecraftClient.getInstance().setScreen(new ItemSearchScreen(MinecraftClient.getInstance().currentScreen, this)));

	private final Draw3d draw = new Draw3d(true);

	public HandsModule() {
		super("Hands", "Shader effect around the item in your hand");
		for (String id : FlyConfig.get().handItems) {
			Identifier identifier = Identifier.tryParse(id);
			if (identifier != null && Registries.ITEM.containsId(identifier)) {
				Item item = Registries.ITEM.get(identifier);
				if (item != Items.AIR) picked.add(item);
			}
		}
		WorldRenderEvents.AFTER_ENTITIES.register(this::render);
	}

	@Override
	public boolean isPicked(Item item) {
		return picked.contains(item);
	}

	@Override
	public int pickedCount() {
		return picked.size();
	}

	@Override
	public void togglePicked(Item item) {
		String id = Registries.ITEM.getId(item).toString();
		List<String> saved = FlyConfig.get().handItems;
		if (picked.remove(item)) {
			saved.remove(id);
		} else {
			picked.add(item);
			if (!saved.contains(id)) saved.add(id);
		}
		FlyConfig.save();
	}

	@Override
	public void clearPicked() {
		picked.clear();
		FlyConfig.get().handItems.clear();
		FlyConfig.save();
	}

	private boolean applies(ItemStack stack) {
		if (stack.isEmpty()) return false;
		return picked.isEmpty() || picked.contains(stack.getItem());
	}

	private void render(WorldRenderContext context) {
		if (!isEnabled() || !shader.getBool()) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		MatrixStack matrices = context.matrixStack();
		if (player == null || matrices == null) return;

		boolean main = applies(player.getMainHandStack());
		boolean off = applies(player.getOffHandStack());
		if (!main && !off) return;

		Camera camera = context.camera();
		boolean first = mc.options.getPerspective().isFirstPerson();
		double yaw = Math.toRadians(first ? camera.getYaw() : player.getYaw());
		double pitch = Math.toRadians(first ? camera.getPitch() : 0.0);
		V f = new V((float) (-Math.sin(yaw) * Math.cos(pitch)), (float) -Math.sin(pitch), (float) (Math.cos(yaw) * Math.cos(pitch)));
		V r = new V((float) -Math.cos(yaw), 0.0f, (float) -Math.sin(yaw));
		V u = new V(r.y() * f.z() - r.z() * f.y(), r.z() * f.x() - r.x() * f.z(), r.x() * f.y() - r.y() * f.x());

		Matrix4f m = matrices.peek().getPositionMatrix();
		Vec3d cam = camera.getPos();
		float time = (System.nanoTime() / 1.0e9f) % 1000.0f;
		float hueDeg = rainbow.getBool() ? (time * 60.0f) % 360.0f : hue.getFloat();
		float s = 0.28f * size.getFloat();

		for (int hand = 0; hand < 2; hand++) {
			if (hand == 0 ? !main : !off) continue;
			boolean rightArm = (player.getMainArm() == Arm.RIGHT) == (hand == 0);
			float side = rightArm ? 1.0f : -1.0f;
			V p;
			if (first) {
				p = new V(f.x() * 0.62f + r.x() * 0.36f * side - u.x() * 0.26f, f.y() * 0.62f + r.y() * 0.36f * side - u.y() * 0.26f,
						f.z() * 0.62f + r.z() * 0.36f * side - u.z() * 0.26f);
			} else {
				Vec3d pp = player.getLerpedPos(context.tickDelta());
				p = new V((float) (pp.x - cam.x) + r.x() * 0.4f * side + f.x() * 0.35f, (float) (pp.y - cam.y) + 1.0f,
						(float) (pp.z - cam.z) + r.z() * 0.4f * side + f.z() * 0.35f);
			}
			effect(m, p, f, r, u, s, time, hueDeg);
		}
		draw.flush();
	}

	private static V at(V p, V a, float ka, V b, float kb, V c, float kc) {
		return new V(p.x() + a.x() * ka + b.x() * kb + c.x() * kc, p.y() + a.y() * ka + b.y() * kb + c.y() * kc,
				p.z() + a.z() * ka + b.z() * kb + c.z() * kc);
	}

	private void effect(Matrix4f m, V p, V f, V r, V u, float S, float T, float hueDeg) {
		float[] col = Fx3d.color(hueDeg, 0.8f, 1.0f);
		float[] light = Fx3d.color(hueDeg, 0.3f, 1.0f);
		float pulse = 0.5f + 0.5f * (float) Math.sin(T * 3.0f);

		switch (style.getInt()) {
			case 0 -> {
				Fx3d.band(draw, m, p, r, u, f, S * 0.5f, 0, S * (1.0f + 0.15f * pulse), 0, 28, col, 0.55f, 0.0f);
				Fx3d.band(draw, m, p, r, u, f, S * (1.0f + 0.15f * pulse), 0, S * 1.7f, 0, 28, col, 0.25f, 0.0f);
				Fx3d.band(draw, m, p, r, u, f, 0.0f, 0, S * 0.55f, 0, 20, light, 0.35f, 0.0f);
			}
			case 1 -> {
				for (int k = 0; k < 9; k++) {
					double a = T * 2.2 + k * Math.PI * 2.0 / 9.0;
					V pos = at(p, r, (float) Math.cos(a) * S * 1.1f, f, (float) Math.sin(a) * S * 1.1f, u, (float) Math.sin(a * 2.0) * S * 0.25f);
					Fx3d.spark(draw, m, pos, 0.035f * (1.0f + (k % 3) * 0.3f), k % 2 == 0 ? col : light, 0.95f);
				}
			}
			case 2 -> {
				double a = T * 1.6;
				V a1 = new V((float) (r.x() * Math.cos(a) + f.x() * Math.sin(a)), (float) (r.y() * Math.cos(a) + f.y() * Math.sin(a)),
						(float) (r.z() * Math.cos(a) + f.z() * Math.sin(a)));
				V a2 = new V((float) (r.x() * Math.cos(a + 1.9) + f.x() * Math.sin(a + 1.9)), (float) (r.y() * Math.cos(a + 1.9) + f.y() * Math.sin(a + 1.9)),
						(float) (r.z() * Math.cos(a + 1.9) + f.z() * Math.sin(a + 1.9)));
				Fx3d.band(draw, m, p, r, u, f, S * 1.15f, 0, S * 1.22f, 0, 32, col, 0.85f, 0.85f);
				Fx3d.band(draw, m, p, a1, u, f, S * 1.0f, 0, S * 1.07f, 0, 32, light, 0.85f, 0.85f);
				Fx3d.band(draw, m, p, a2, f, u, S * 0.85f, 0, S * 0.92f, 0, 32, col, 0.85f, 0.85f);
			}
			case 3 -> {
				for (int k = 0; k < 14; k++) {
					float ph = (T * 1.3f + k * 0.37f) % 1.0f;
					float ox = (Fx3d.rnd(k, 4) - 0.5f) * S * 1.1f;
					float oz = (Fx3d.rnd(k, 5) - 0.5f) * S * 0.8f;
					V pos = at(p, r, ox * (1.0f - ph * 0.6f), u, ph * S * 1.7f - S * 0.4f, f, oz);
					float[] c = Fx3d.color(hueDeg + 40.0f * ph, 0.9f, 1.0f);
					Fx3d.spark(draw, m, pos, 0.06f * (1.0f - ph * 0.8f), c, 0.9f * (1.0f - ph));
				}
			}
			case 4 -> {
				int bucket = (int) (T * 16.0f);
				for (int b = 0; b < 4; b++) {
					double az = Fx3d.rnd(bucket * 4 + b, 6) * Math.PI * 2.0;
					double el = (Fx3d.rnd(bucket * 4 + b, 7) - 0.5) * 2.4;
					V dir = at(new V(0, 0, 0), r, (float) (Math.cos(az) * Math.cos(el)), u, (float) Math.sin(el), f, (float) (Math.sin(az) * Math.cos(el)));
					V cur = p;
					for (int k = 1; k <= 6; k++) {
						V nxt = at(p, dir, S * 1.5f * k / 6.0f, r, (Fx3d.rnd(bucket * 40 + b * 6 + k, 8) - 0.5f) * S * 0.35f,
								u, (Fx3d.rnd(bucket * 40 + b * 6 + k, 9) - 0.5f) * S * 0.35f);
						for (int q = 0; q < 3; q++) {
							float w = q / 3.0f;
							V sp = new V(cur.x() + (nxt.x() - cur.x()) * w, cur.y() + (nxt.y() - cur.y()) * w, cur.z() + (nxt.z() - cur.z()) * w);
							Fx3d.spark(draw, m, sp, 0.025f, k % 2 == 0 ? light : col, 0.95f);
						}
						cur = nxt;
					}
				}
				Fx3d.band(draw, m, p, r, u, f, 0.0f, 0, S * 0.5f, 0, 16, col, 0.3f, 0.0f);
			}
			case 5 -> {
				V top = at(p, u, S * 2.2f, u, 0, u, 0);
				for (int k = 0; k < 8; k++) {
					float h0 = k * S * 0.28f;
					float a = 0.7f * (1.0f - k / 8.0f);
					Fx3d.band(draw, m, at(p, u, h0, u, 0, u, 0), r, f, u, S * 0.22f * (1.0f - k * 0.06f), 0, S * 0.22f * (1.0f - (k + 1) * 0.06f), S * 0.28f,
							12, k % 2 == 0 ? col : light, a, a * 0.85f);
				}
				Fx3d.spark(draw, m, top, 0.04f, light, 0.6f);
			}
			case 6 -> {
				for (int k = 0; k < 5; k++) {
					double a = T * 1.5 + k * Math.PI * 2.0 / 5.0;
					V c = at(p, r, (float) Math.cos(a) * S * 1.25f, f, (float) Math.sin(a) * S * 1.25f, u, (float) Math.sin(T * 2.0 + k) * S * 0.2f);
					float hgt = S * 0.28f;
					float w = S * 0.12f;
					V top = at(c, u, hgt, u, 0, u, 0);
					V bot = at(c, u, -hgt, u, 0, u, 0);
					V p1 = at(c, r, w, r, 0, r, 0);
					V p2 = at(c, f, w, f, 0, f, 0);
					V p3 = at(c, r, -w, r, 0, r, 0);
					V p4 = at(c, f, -w, f, 0, f, 0);
					float[] cc = k % 2 == 0 ? col : light;
					Fx3d.quad(draw, m, top, p1, p2, p2, cc, 0.85f, 0.85f, 0.85f, 0.85f);
					Fx3d.quad(draw, m, top, p2, p3, p3, cc, 0.8f, 0.8f, 0.8f, 0.8f);
					Fx3d.quad(draw, m, top, p3, p4, p4, cc, 0.85f, 0.85f, 0.85f, 0.85f);
					Fx3d.quad(draw, m, top, p4, p1, p1, cc, 0.8f, 0.8f, 0.8f, 0.8f);
					Fx3d.quad(draw, m, bot, p1, p2, p2, col, 0.7f, 0.7f, 0.7f, 0.7f);
					Fx3d.quad(draw, m, bot, p2, p3, p3, col, 0.65f, 0.65f, 0.65f, 0.65f);
					Fx3d.quad(draw, m, bot, p3, p4, p4, col, 0.7f, 0.7f, 0.7f, 0.7f);
					Fx3d.quad(draw, m, bot, p4, p1, p1, col, 0.65f, 0.65f, 0.65f, 0.65f);
				}
			}
			case 7 -> {
				for (int s = 0; s < 2; s++) {
					for (int k = 0; k < 18; k++) {
						float al = k / 17.0f;
						double a = k * 0.6 + T * 4.0 + s * Math.PI;
						V pos = at(p, f, al * S * 2.2f - S * 0.6f, r, (float) Math.cos(a) * S * 0.55f, u, (float) Math.sin(a) * S * 0.55f);
						Fx3d.spark(draw, m, pos, 0.04f, s == 0 ? col : light, 0.95f * (0.4f + 0.6f * al));
					}
				}
			}
			case 8 -> {
				for (int k = 0; k < 3; k++) {
					float ph = (T * 0.8f + k / 3.0f) % 1.0f;
					float rad = S * (0.3f + 1.7f * ph);
					float al = 0.8f * (1.0f - ph);
					Fx3d.band(draw, m, p, r, u, f, rad * 0.93f, 0, rad, 0, 32, col, al * 0.2f, al);
					Fx3d.band(draw, m, p, r, f, u, rad * 0.93f, 0, rad, 0, 32, light, al * 0.2f, al * 0.7f);
				}
			}
			default -> {
				for (int k = 0; k < 24; k++) {
					double az = Fx3d.rnd(k, 10) * Math.PI * 2.0;
					double el = Math.acos(2.0 * Fx3d.rnd(k, 11) - 1.0);
					float ph = (T * 0.45f + Fx3d.rnd(k, 12)) % 1.0f;
					float rad = S * (0.35f + 1.3f * ph);
					V pos = at(p, r, (float) (Math.sin(el) * Math.cos(az)) * rad, u, (float) Math.cos(el) * rad, f, (float) (Math.sin(el) * Math.sin(az)) * rad);
					float tw = 0.5f + 0.5f * (float) Math.sin(T * 8.0f + k);
					Fx3d.spark(draw, m, pos, 0.03f + 0.02f * tw, k % 3 == 0 ? light : col, (1.0f - ph) * (0.5f + 0.5f * tw));
				}
			}
		}
	}
}
