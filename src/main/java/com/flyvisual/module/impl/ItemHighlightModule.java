package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.ColorUtil;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ToolItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Подсветка выпавших предметов: рамка и луч вверх. Элитры красным, остальные ценные другим цветом.
 * Рисуется только на твоей стороне.
 */
public class ItemHighlightModule extends Module {
	private static final String[] MODES = {"Только элитры", "Ценные", "Все предметы"};

	private final DoubleSetting mode = addChoice("Mode", 1, MODES);
	private final DoubleSetting elytraHue = addSetting("Elytra color (hue)", 0.0, 0.0, 355.0, 5.0);
	private final DoubleSetting otherHue = addSetting("Other color (hue)", 50.0, 0.0, 355.0, 5.0);
	private final DoubleSetting range = addSetting("Range", 48.0, 8.0, 128.0, 8.0);
	private final DoubleSetting beam = addToggle("Beam", true);

	public ItemHighlightModule() {
		super("Item Highlight", "Highlights valuable dropped items");
		WorldRenderEvents.AFTER_ENTITIES.register(this::render);
	}

	private void render(WorldRenderContext context) {
		if (!isEnabled()) return;

		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || context.world() == null) return;

		VertexConsumerProvider consumers = context.consumers();
		MatrixStack matrices = context.matrixStack();
		if (consumers == null || matrices == null) return;

		VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
		Vec3d cam = context.camera().getPos();
		float tickDelta = context.tickDelta();
		double maxDistSq = range.get() * range.get();
		int m = mode.getInt();

		for (Entity entity : context.world().getEntities()) {
			if (!(entity instanceof ItemEntity item)) continue;
			if (item.squaredDistanceTo(cam) > maxDistSq) continue;

			ItemStack stack = item.getStack();
			boolean elytra = stack.isOf(Items.ELYTRA);
			if (m == 0 && !elytra) continue;
			if (m == 1 && !elytra && !valuable(stack)) continue;

			float hue = elytra ? elytraHue.getFloat() : otherHue.getFloat();
			int rgb = ColorUtil.hsv(hue, 0.9f, 1.0f);
			float r = ColorUtil.red(rgb);
			float g = ColorUtil.green(rgb);
			float b = ColorUtil.blue(rgb);

			Vec3d lerped = item.getLerpedPos(tickDelta);
			Vec3d shift = lerped.subtract(item.getPos()).subtract(cam);
			Box box = item.getBoundingBox().expand(0.12).offset(shift);
			WorldRenderer.drawBox(matrices, lines, box, r, g, b, 1.0f);

			if (beam.getBool()) {
				Box pillar = new Box(box.minX + 0.2, box.maxY, box.minZ + 0.2, box.maxX - 0.2, box.maxY + 2.5, box.maxZ - 0.2)
						.offset(0.0, 0.0, 0.0);
				WorldRenderer.drawBox(matrices, lines, pillar, r, g, b, 0.8f);
			}
		}
	}

	private static boolean valuable(ItemStack stack) {
		Item i = stack.getItem();
		if (i == Items.TOTEM_OF_UNDYING || i == Items.ENCHANTED_GOLDEN_APPLE || i == Items.NETHERITE_INGOT
				|| i == Items.NETHERITE_SCRAP || i == Items.DIAMOND || i == Items.NETHER_STAR
				|| i == Items.TRIDENT || i == Items.ELYTRA) {
			return true;
		}
		if (i instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock) return true;
		if (i instanceof ArmorItem a && (a.getMaterial() == ArmorMaterials.DIAMOND || a.getMaterial() == ArmorMaterials.NETHERITE)) {
			return true;
		}
		if (i instanceof ToolItem t && (t.getMaterial() == ToolMaterials.DIAMOND || t.getMaterial() == ToolMaterials.NETHERITE)) {
			return true;
		}
		return stack.hasEnchantments();
	}
}
