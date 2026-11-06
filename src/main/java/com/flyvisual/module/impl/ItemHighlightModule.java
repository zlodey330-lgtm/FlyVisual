package com.flyvisual.module.impl;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.module.ItemSearchScreen;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.ColorUtil;
import com.flyvisual.util.Draw3d;
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
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Подсветка выпавших предметов: рамка и луч вверх. Элитры красным, остальные ценные другим цветом.
 * Можно выбрать свои предметы через поиск (кнопка «Выбрать предметы»), их можно несколько.
 * Рисуется только на твоей стороне.
 */
public class ItemHighlightModule extends Module implements com.flyvisual.module.ItemSelection {
	private static final String[] MODES = {"Только элитры", "Ценные", "Все предметы", "Только выбранные"};

	/** Выбранные предметы. Подсвечиваются при любом режиме. */
	private final Set<Item> picked = new HashSet<>();

	private final DoubleSetting mode = addChoice("Mode", 1, MODES);
	private final DoubleSetting elytraHue = addSetting("Elytra color (hue)", 0.0, 0.0, 355.0, 5.0);
	private final DoubleSetting otherHue = addSetting("Other color (hue)", 50.0, 0.0, 355.0, 5.0);
	private final DoubleSetting beamHue = addSetting("Beam color (hue)", 0.0, 0.0, 355.0, 5.0);
	private final DoubleSetting beamHeight = addSetting("Beam height", 1.5, 0.25, 4.0, 0.25);
	private final DoubleSetting beamWidth = addSetting("Beam width", 0.3, 0.05, 1.0, 0.05);
	private final DoubleSetting range = addSetting("Range", 48.0, 8.0, 128.0, 8.0);
	private final DoubleSetting pick = addButton("Choose items", () -> String.valueOf(picked.size()),
			() -> MinecraftClient.getInstance().setScreen(new ItemSearchScreen(MinecraftClient.getInstance().currentScreen, this)));

	public ItemHighlightModule() {
		super("Item Highlight", "Highlights valuable dropped items");
		for (String id : FlyConfig.get().highlightItems) {
			Item item = parse(id);
			if (item != null) {
				picked.add(item);
			}
		}
		WorldRenderEvents.AFTER_ENTITIES.register(this::render);
	}

	private static Item parse(String id) {
		Identifier identifier = Identifier.tryParse(id);
		if (identifier == null || !Registries.ITEM.containsId(identifier)) return null;
		Item item = Registries.ITEM.get(identifier);
		return item == Items.AIR ? null : item;
	}

	@Override
	public boolean isPicked(Item item) {
		return picked.contains(item);
	}

	@Override
	public int pickedCount() {
		return picked.size();
	}

	/** Добавляет или убирает предмет из выбранных. */
	@Override
	public void togglePicked(Item item) {
		String id = Registries.ITEM.getId(item).toString();
		List<String> saved = FlyConfig.get().highlightItems;
		if (picked.remove(item)) {
			saved.remove(id);
		} else {
			picked.add(item);
			if (!saved.contains(id)) {
				saved.add(id);
			}
		}
		FlyConfig.save();
	}

	@Override
	public void clearPicked() {
		picked.clear();
		FlyConfig.get().highlightItems.clear();
		FlyConfig.save();
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

		int beamRgb = ColorUtil.hsv(beamHue.getFloat(), 0.9f, 1.0f);
		float br = ColorUtil.red(beamRgb);
		float bg = ColorUtil.green(beamRgb);
		float bb = ColorUtil.blue(beamRgb);
		float bh = beamHeight.getFloat();
		float bw = beamWidth.getFloat() / 2.0f;
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		Draw3d beams = new Draw3d(true);

		for (Entity entity : context.world().getEntities()) {
			if (!(entity instanceof ItemEntity item)) continue;
			if (item.squaredDistanceTo(cam) > maxDistSq) continue;

			ItemStack stack = item.getStack();
			boolean elytra = stack.isOf(Items.ELYTRA);
			boolean chosen = picked.contains(stack.getItem());
			boolean show = chosen
					|| m == 2
					|| (m == 1 && (elytra || valuable(stack)))
					|| (m == 0 && elytra);
			if (!show) continue;

			float hue = elytra ? elytraHue.getFloat() : otherHue.getFloat();
			int rgb = ColorUtil.hsv(hue, 0.9f, 1.0f);
			float r = ColorUtil.red(rgb);
			float g = ColorUtil.green(rgb);
			float b = ColorUtil.blue(rgb);

			Vec3d lerped = item.getLerpedPos(tickDelta);
			Vec3d shift = lerped.subtract(item.getPos()).subtract(cam);
			Box box = item.getBoundingBox().expand(0.12).offset(shift);
			WorldRenderer.drawBox(matrices, lines, box, r, g, b, 1.0f);

			// Луч вверх: полупрозрачный столб своего цвета, внизу ярче, вверху растворяется
			float cx = (float) ((box.minX + box.maxX) / 2.0);
			float cz = (float) ((box.minZ + box.maxZ) / 2.0);
			beams.beam(matrix, cx, (float) box.maxY, cz, bw, bh, br, bg, bb, 0.7f, 0.0f);
		}
		beams.flush();
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
