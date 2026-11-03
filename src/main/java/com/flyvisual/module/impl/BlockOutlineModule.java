package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.ColorUtil;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

/**
 * Свой цвет обводки блока, на который ты смотришь (вместо тёмной ванильной).
 * Возможна радуга и пульсация. Рисуется только у тебя.
 */
public class BlockOutlineModule extends Module {
	private final DoubleSetting hue = addSetting("Color (hue)", 0.0, 0.0, 355.0, 5.0);
	private final DoubleSetting opacity = addSetting("Opacity", 100.0, 10.0, 100.0, 10.0);
	private final DoubleSetting rainbow = addToggle("Rainbow", false);
	private final DoubleSetting pulse = addToggle("Pulse", false);

	public BlockOutlineModule() {
		super("Block Outline", "Custom color of the block outline");
		WorldRenderEvents.BLOCK_OUTLINE.register(this::onOutline);
	}

	/** Возвращает false, чтобы отменить ванильную обводку, когда рисуем свою. */
	private boolean onOutline(WorldRenderContext worldContext, WorldRenderContext.BlockOutlineContext outline) {
		if (!isEnabled()) return true;

		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) return true;

		MatrixStack matrices = worldContext.matrixStack();
		if (matrices == null) return true;

		VoxelShape shape = outline.blockState().getOutlineShape(mc.world, outline.blockPos(), ShapeContext.of(outline.entity()));
		if (shape.isEmpty()) return true;

		float h = rainbow.getBool() ? (System.currentTimeMillis() / 12 % 360) : hue.getFloat();
		int rgb = ColorUtil.hsv(h, 0.85f, 1.0f);
		float alpha = opacity.getFloat() / 100.0f;
		if (pulse.getBool()) {
			alpha *= 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 250.0);
		}

		double ox = outline.blockPos().getX() - outline.cameraX();
		double oy = outline.blockPos().getY() - outline.cameraY();
		double oz = outline.blockPos().getZ() - outline.cameraZ();

		for (Box box : shape.getBoundingBoxes()) {
			// Чуть увеличиваем, чтобы линии не мерцали на гранях блока
			Box moved = box.offset(ox, oy, oz).expand(0.002);
			WorldRenderer.drawBox(matrices, outline.vertexConsumer(), moved,
					ColorUtil.red(rgb), ColorUtil.green(rgb), ColorUtil.blue(rgb), alpha);
		}
		return false;
	}
}
