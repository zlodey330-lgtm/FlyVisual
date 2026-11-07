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
 * Hands: шейдер прямо на предмете в руке (10 видов). Предмет ещё раз рисуется светящимися оболочками
 * с цветом и пульсацией. Предметы выбираются через поиск; если ничего не выбрано, эффект у любого предмета.
 * Переключатель «Шейдер» выключает сам эффект, а модуль остаётся, и FPS на него не тратится.
 * Рисование вызывается из HeldItemShaderMixin. Виден только тебе.
 */
public class HandsModule extends Module implements ItemSelection {
	private static final String[] STYLES = {"Ореол", "Неон", "Радуга", "Пульс", "Лёд", "Огонь", "Золото", "Призрак",
			"Глитч", "Тень"};
	private static final int FULL_BRIGHT = 15728880;

	private final Set<Item> picked = new HashSet<>();

	private final DoubleSetting shader = addToggle("Shader", true);
	private final DoubleSetting style = addChoice("Effect style", 0, STYLES);
	private final DoubleSetting hue = addSetting("Color (hue)", 280.0, 0.0, 355.0, 5.0);
	private final DoubleSetting power = addSetting("Intensity", 1.0, 0.3, 2.0, 0.1);
	private final DoubleSetting pick = addButton("Choose items", () -> picked.isEmpty() ? "Все" : String.valueOf(picked.size()),
			() -> MinecraftClient.getInstance().setScreen(new ItemSearchScreen(MinecraftClient.getInstance().currentScreen, this)));

	private boolean busy;

	public HandsModule() {
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
		if (busy || !isEnabled() || !shader.getBool()) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (entity != mc.player || !applies(stack)) return;

		busy = true;
		try {
			float t = (System.nanoTime() / 1.0e9f) % 1000.0f;
			float k = power.getFloat();
			float h = hue.getFloat();
			float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3.2f);
			float fast = 0.5f + 0.5f * (float) Math.sin(t * 9.0f);

			switch (style.getInt()) {
				case 0 -> {
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.08f, rgb(h, 0.7f, 1.0f), 0.40f * k, 0, 0);
					shell(entity, stack, mode, leftHanded, matrices, consumers, 1.18f, rgb(h, 0.8f, 1.0f), 0.18f * k, 0, 0);
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
		VertexConsumerProvider tinted = layer -> new TintConsumer(
				real.getBuffer(RenderLayer.getEntityTranslucentCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)),
				color[0], color[1], color[2], Math.min(1.0f, alpha));
		matrices.push();
		matrices.translate(dx, dy, 0.0f);
		matrices.scale(scale, scale, scale);
		MinecraftClient.getInstance().getItemRenderer().renderItem(entity, stack, mode, leftHanded, matrices, tinted,
				entity.getWorld(), FULL_BRIGHT, net.minecraft.client.render.OverlayTexture.DEFAULT_UV, 0);
		matrices.pop();
	}
}
