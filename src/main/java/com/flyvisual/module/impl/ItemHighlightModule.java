package com.flyvisual.module.impl;

import com.flyvisual.config.FlyConfig;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.ItemSearchScreen;
import com.flyvisual.module.ItemSelection;
import com.flyvisual.util.Draw3d;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * Подсветка выпавших предметов: рамка и луч вверх. Выбираешь любые предметы из всех в игре
 * (если ничего не выбрано, подсвечиваются все). Остаётся только цвет, луч включён всегда.
 */
public class ItemHighlightModule extends Module implements ItemSelection {
	private final Set<Item> picked = new HashSet<>();

	private final DoubleSetting hue = addSetting("Color (hue)", 50.0, 0.0, 355.0, 5.0);
	private final DoubleSetting pick = addToggle("Choose items", false);
	private final Draw3d beams = new Draw3d(true);

	public ItemHighlightModule() {
		super("Item Highlight", "Highlights chosen dropped items");
		for (String id : FlyConfig.get().highlightItems) {
			Identifier identifier = Identifier.tryParse(id);
			if (identifier != null && Registries.ITEM.containsId(identifier)) {
				Item item = Registries.ITEM.get(identifier);
				if (item != Items.AIR) picked.add(item);
			}
		}
		WorldRenderEvents.AFTER_ENTITIES.register(this::render);
	}

	@Override
	public void onTick(MinecraftClient client) {
		super.onTick(client);
		if (pick.getBool()) {
			pick.set(0.0);
			client.setScreen(new ItemSearchScreen(client.currentScreen, this));
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
		List<String> saved = FlyConfig.get().highlightItems;
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
		double maxDistSq = 64.0 * 64.0;
		Matrix4f mat = matrices.peek().getPositionMatrix();

		int rgb = ColorUtil.hsv(hue.getFloat(), 0.9f, 1.0f);
		float r = ColorUtil.red(rgb);
		float g = ColorUtil.green(rgb);
		float b = ColorUtil.blue(rgb);
		boolean any = false;

		for (Entity entity : context.world().getEntities()) {
			if (!(entity instanceof ItemEntity item)) continue;
			if (item.squaredDistanceTo(cam) > maxDistSq) continue;
			if (!picked.isEmpty() && !picked.contains(item.getStack().getItem())) continue;

			Vec3d lerped = item.getLerpedPos(tickDelta);
			Vec3d shift = lerped.subtract(item.getPos()).subtract(cam);
			Box box = item.getBoundingBox().expand(0.12).offset(shift);
			WorldRenderer.drawBox(matrices, lines, box, r, g, b, 1.0f);

			float cx = (float) ((box.minX + box.maxX) / 2.0);
			float cz = (float) ((box.minZ + box.maxZ) / 2.0);
			beams.beam(mat, cx, (float) box.maxY, cz, 0.12f, 3.0f, r, g, b, 0.75f, 0.0f);
			beams.beam(mat, cx, (float) box.maxY, cz, 0.05f, 2.6f, 1.0f, 1.0f, 1.0f, 0.55f, 0.0f);
			any = true;
		}
		if (any) beams.flush();
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
