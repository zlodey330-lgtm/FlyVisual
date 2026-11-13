package com.flyvisual.module.impl;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.ItemSearchScreen;
import com.flyvisual.module.ItemSelection;
import com.flyvisual.module.Module;
import com.flyvisual.util.ColorUtil;
import com.flyvisual.util.TintConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.model.BakedModel;
import org.joml.Vector3f;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hands: шейдер прямо на предмете в руке (18 видов). Предмет ещё раз рисуется светящимися оболочками
 * с цветом и пульсацией. Предметы выбираются через поиск; если ничего не выбрано, эффект у любого предмета.
 * Переключатель «Шейдер» выключает переливы и лишние слои: остаётся один спокойный слой.
 * Рисование вызывается из HeldItemShaderMixin. Виден только тебе.
 */
public class HandsFx extends Module implements ItemSelection {
	private static final String[] STYLES = {"Ореол", "Неон", "Радуга", "Пульс", "Лёд", "Огонь", "Золото", "Призрак",
			"Глитч", "Тень", "Космос", "Кристалл", "Электро", "Кровь", "Токсик", "Сакура", "Закат", "Контур"};
	private static final int FULL_BRIGHT = 15728880;

	private final Set<Item> picked = new HashSet<>();

	private final DoubleSetting shader = addToggle("Shader", true);
	private final DoubleSetting style = addChoice("Effect style", 0, STYLES);
	private final DoubleSetting hue = addSetting("Color (hue)", 280.0, 0.0, 355.0, 5.0);
	private final DoubleSetting power = addSetting("Intensity", 1.0, 0.3, 2.0, 0.1);
	private final DoubleSetting pick = addToggle("Choose items", false);

	private boolean busy;
	private int shells;
	/** true: полный шейдер (несколько слоёв, переливы). false: один спокойный слой без анимации. */
	private boolean full;
	/** Центр предмета в системе координат руки: от него растягиваем оболочки, чтобы свечение ложилось ровно вокруг. */
	private float cx, cy, cz;

	@Override
	public void onTick(MinecraftClient client) {
		super.onTick(client);
		if (pick.getBool()) {
			pick.set(0.0);
			client.setScreen(new ItemSearchScreen(client.currentScreen, this));
		}
	}

	public HandsFx() {
		super("Hands", "Shader effect on the item in your hand");
		for (String id : FlyConfig.get().handItems) {
			Identifier identifier = Identifier.tryParse(id);
			if (identifier != null && Registries.ITEM.containsId(identifier)) {
				Item item = Registries.ITEM.get(identifier);
				if (item != Items.AIR) picked.add(item);
			}
		}
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

	private static float[] rgb(float hueDeg, float sat, float val) {
		int c = ColorUtil.hsv(hueDeg, sat, val);
		return new float[]{ColorUtil.red(c), ColorUtil.green(c), ColorUtil.blue(c)};
	}

	/** Рисует оболочки поверх предмета. Вызывается после обычной отрисовки предмета у игрока. */
	public void renderShader(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded,
							 MatrixStack matrices, VertexConsumerProvider consumers) {
		if (busy || !isEnabled()) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (entity != mc.player || !applies(stack)) return;

		busy = true;
		shells = 0;
		full = shader.getBool();
		try {
			BakedModel bm = mc.getItemRenderer().getModel(stack, entity.getWorld(), entity, 0);
			Vector3f tr = bm.getTransformation().getTransformation(mode).translation;
			cx = leftHanded ? -tr.x() : tr.x();
			cy = tr.y();
			cz = tr.z();
		} catch (Throwable e) {
			cx = 0.0f;
			cy = 0.0f;
			cz = 0.0f;
		}
		try {
			// Шейдер выключен: время стоит на месте, переливов и пульсации нет
			float t = full ? (System.nanoTime() / 1.0e9f) % 1000.0f : 0.0f;
			float k = power.getFloat();
			float h = hue.getFloat();
			if (full) {
				// Переливание: оттенок плавно плывёт вокруг выбранного цвета, сила дышит
				h = (h + 45.0f * (float) Math.sin(t * 1.3f) + 360.0f) % 360.0f;
				k *= 0.85f + 0.3f * (0.5f + 0.5f * (float) Math.sin(t * 2.3f));
			}
			float pulse = full ? 0.5f + 0.5f * (float) Math.sin(t * 3.2f) : 0.5f;
			float fast = full ? 0.5f + 0.5f * (float) Math.sin(t * 9.0f) : 0.5f;

			switch (style.getInt()) {
				case 0 -> {
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.07f, rgb(h, 0.75f, 1.0f), 0.42f * k, 0, 0);
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.15f + 0.02f * pulse, rgb((h + 50.0f) % 360.0f, 0.35f, 1.0f), 0.30f * k, 0, 0);
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.23f - 0.02f * pulse, rgb((h + 110.0f) % 360.0f, 0.5f, 1.0f), 0.14f * k, 0, 0);
				}
				case 1 -> shell(entity, stack, mode, leftHanded, matrices, consumers, 1.05f, rgb(h, 0.9f, 1.0f), (0.55f + 0.2f * pulse) * k, 0, 0);
				case 2 -> {
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.07f, rgb((t * 90.0f) % 360.0f, 0.85f, 1.0f), 0.50f * k, 0, 0);
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.16f, rgb((t * 90.0f + 120.0f) % 360.0f, 0.85f, 1.0f), 0.22f * k, 0, 0);
				}
				case 3 -> shell(entity, stack, mode, leftHanded, matrices, consumers, 1.04f + 0.18f * pulse, rgb(h, 0.8f, 1.0f), (0.6f - 0.35f * pulse) * k, 0, 0);
				case 4 -> {
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.06f, new float[]{0.55f, 0.9f, 1.0f}, (0.42f + 0.12f * fast) * k, 0, 0);
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.15f, new float[]{0.85f, 0.97f, 1.0f}, 0.16f * k, 0, 0);
				}
				case 5 -> {
					float j = 0.012f * (float) Math.sin(t * 17.0f);
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.07f + j, new float[]{1.0f, 0.55f, 0.1f}, 0.45f * k, 0, 0);
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.15f - j, new float[]{1.0f, 0.25f, 0.05f}, 0.28f * k, 0, 0.01f * fast);
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.24f + j, new float[]{1.0f, 0.8f, 0.2f}, 0.12f * k, 0, 0.02f * fast);
				}
				case 6 -> {
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.05f, new float[]{1.0f, 0.82f, 0.25f}, (0.5f + 0.15f * pulse) * k, 0, 0);
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.12f, new float[]{1.0f, 0.95f, 0.6f}, 0.16f * k, 0, 0);
				}
				case 7 -> shell(entity, stack, mode, leftHanded, matrices, consumers, 1.12f + 0.03f * pulse, new float[]{0.85f, 0.92f, 1.0f}, (0.25f + 0.1f * pulse) * k, 0, 0);
				case 8 -> {
					float o = 0.012f + 0.01f * fast;
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.03f, new float[]{1.0f, 0.1f, 0.2f}, 0.45f * k, -o, 0);
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.03f, new float[]{0.1f, 0.9f, 1.0f}, 0.45f * k, o, 0);
				}
				case 10 -> {
					float hh = 250.0f + 50.0f * pulse;
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.06f, rgb(hh, 0.8f, 1.0f), 0.48f * k, 0, 0);
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.14f, rgb(hh + 40.0f, 0.6f, 1.0f), 0.24f * k, 0, 0);
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.22f, new float[]{0.9f, 0.85f, 1.0f}, 0.10f * k, 0, 0);
				}
				case 11 -> {
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.05f, new float[]{0.6f, 1.0f, 1.0f}, (0.45f + 0.2f * fast) * k, 0, 0);
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.13f, new float[]{1.0f, 1.0f, 1.0f}, 0.22f * k, 0, 0);
				}
				case 12 -> {
					float o = 0.01f * (float) Math.sin(t * 40.0f);
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.05f, new float[]{1.0f, 0.95f, 0.3f}, (0.4f + 0.3f * fast) * k, o, -o);
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.12f, new float[]{0.7f, 0.9f, 1.0f}, 0.2f * k, -o, o);
				}
				case 13 -> {
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.06f, new float[]{0.8f, 0.05f, 0.08f}, (0.5f + 0.2f * pulse) * k, 0, 0);
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.15f, new float[]{0.35f, 0.0f, 0.03f}, 0.3f * k, 0, 0);
				}
				case 14 -> {
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.06f, new float[]{0.3f, 1.0f, 0.2f}, 0.45f * k, 0, 0);
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.16f + 0.02f * pulse, new float[]{0.7f, 1.0f, 0.3f}, 0.2f * k, 0, 0);
				}
				case 15 -> {
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.06f, new float[]{1.0f, 0.55f, 0.75f}, 0.45f * k, 0, 0);
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.15f, new float[]{1.0f, 0.85f, 0.92f}, 0.22f * k, 0, 0);
				}
				case 16 -> {
					float hh = 15.0f + 320.0f * pulse;
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.06f, rgb(hh % 360.0f, 0.85f, 1.0f), 0.48f * k, 0, 0);
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.15f, rgb((hh + 50.0f) % 360.0f, 0.7f, 1.0f), 0.2f * k, 0, 0);
				}
				case 17 -> shell(entity, stack, mode, leftHanded, matrices, consumers, 1.035f, new float[]{1.0f, 1.0f, 1.0f}, 0.85f * k, 0, 0);
				default -> {
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.12f, new float[]{0.35f, 0.1f, 0.55f}, 0.45f * k, 0, 0);
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.04f, new float[]{0.05f, 0.0f, 0.1f}, 0.5f * k, 0, 0);
				}
			}
		} finally {
			busy = false;
		}
	}

	private void shell(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded,
					   MatrixStack matrices, VertexConsumerProvider real, float scale, float[] color, float alpha, float dx, float dy) {
		// Шейдер выключен: только один слой вместо двух-трёх
		if (!full && shells >= 1) return;
		shells++;
		VertexConsumerProvider tinted = layer -> new TintConsumer(
				real.getBuffer(RenderLayer.getEntityTranslucentCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)),
				color[0], color[1], color[2], Math.min(1.0f, alpha));
		matrices.push();
		// Растягиваем от центра предмета, а не от руки: свечение обливает предмет со всех сторон поровну
		float k = scale - 1.0f;
		matrices.translate(dx - k * cx, dy - k * cy, -k * cz);
		matrices.scale(scale, scale, scale);
		MinecraftClient.getInstance().getItemRenderer().renderItem(entity, stack, mode, leftHanded, matrices, tinted,
				entity.getWorld(), FULL_BRIGHT, net.minecraft.client.render.OverlayTexture.DEFAULT_UV, 0);
		matrices.pop();
	}
}
