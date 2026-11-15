package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.Draw3d;
import com.flyvisual.util.VoxelHuman;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

import java.io.InputStream;

/**
 * Свой скин персонажа с настраиваемым размером. Работает только у тебя на экране (F5 и инвентарь),
 * другие игроки видят твой обычный скин. В воксельном режиме персонаж собирается из кубиков.
 */
public class SkinModule extends Module {
	public static SkinModule INSTANCE;

	private static final String[] NAMES = {"Мальчишка в кепке", "Пухляш", "Папа", "Космонавт", "Ниндзя", "Студент", "Байкер"};
	private static final String[] FILES = {"boy", "puffy", "dad", "astro", "ninja", "student", "biker"};
	private static final Identifier[] TEX = new Identifier[FILES.length];

	private final DoubleSetting skin = addChoice("Скин", 0, NAMES);
	private final DoubleSetting size = addSetting("Размер модели", 0.6, 0.3, 1.2, 0.05);
	private final DoubleSetting voxel = addToggle("Воксельная модель", true);

	private final Draw3d solid = new Draw3d(false);
	private VoxelHuman model;
	private int builtSkin = -1;
	private boolean loadFailed;
	private float lightFactor = 1.0f;
	private float lightTimer;

	public SkinModule() {
		super("Custom Skin", "Changes your skin and size");
		INSTANCE = this;
		WorldRenderEvents.AFTER_ENTITIES.register(this::render);
	}

	@Override
	protected boolean enabledByDefault() {
		return false;
	}

	private int skinIndex() {
		return Math.max(0, Math.min(FILES.length - 1, skin.getInt()));
	}

	public Identifier texture() {
		int i = skinIndex();
		if (TEX[i] == null) {
			TEX[i] = new Identifier("flyvisual", "textures/skins/" + FILES[i] + ".png");
		}
		return TEX[i];
	}

	public float scale() {
		return size.getFloat();
	}

	/** Читает скин в массив ARGB 64x64. */
	private int[] loadPixels(Identifier id) {
		try (InputStream in = MinecraftClient.getInstance().getResourceManager().getResource(id).orElseThrow().getInputStream();
			 NativeImage img = NativeImage.read(in)) {
			if (img.getWidth() != 64 || img.getHeight() != 64) return null;
			int[] px = new int[64 * 64];
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					int c = img.getColor(x, y); // ABGR
					int a = (c >>> 24) & 0xFF;
					int b = (c >> 16) & 0xFF;
					int g = (c >> 8) & 0xFF;
					int r = c & 0xFF;
					px[y * 64 + x] = (a << 24) | (r << 16) | (g << 8) | b;
				}
			}
			return px;
		} catch (Throwable t) {
			return null;
		}
	}

	private boolean ensureModel() {
		int i = skinIndex();
		if (model != null && builtSkin == i) return true;
		if (loadFailed && builtSkin == i) return false;
		builtSkin = i;
		int[] px = loadPixels(texture());
		if (px == null) {
			loadFailed = true;
			model = null;
			return false;
		}
		loadFailed = false;
		model = new VoxelHuman(px);
		return true;
	}

	/** Рисуем ли мы твоего персонажа сами (вместо обычной модели). */
	public boolean voxelActive(net.minecraft.entity.Entity entity) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!isEnabled() || !voxel.getBool() || entity != mc.player || mc.player == null) return false;
		if (mc.currentScreen instanceof InventoryScreen) return false;
		EntityPose pose = entity.getPose();
		if (pose != EntityPose.STANDING && pose != EntityPose.CROUCHING) return false;
		return ensureModel();
	}

	private void render(WorldRenderContext context) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		ClientWorld world = context.world();
		MatrixStack matrices = context.matrixStack();
		if (player == null || world == null || matrices == null) return;
		if (mc.options.getPerspective().isFirstPerson()) return;
		if (!voxelActive(player)) return;
		try {
			float td = context.tickDelta();
			Vec3d pos = player.getLerpedPos(td);
			Vec3d cam = context.camera().getPos();
			float bodyYaw = MathHelper.lerpAngleDegrees(td, player.prevBodyYaw, player.bodyYaw);
			float headYaw = MathHelper.lerpAngleDegrees(td, player.prevHeadYaw, player.headYaw);
			float pitch = MathHelper.lerp(td, player.prevPitch, player.getPitch());

			model.headYaw = MathHelper.wrapDegrees(headYaw - bodyYaw);
			model.headPitch = pitch;
			model.limbPos = player.limbAnimator.getPos(td);
			model.limbSpeed = player.limbAnimator.getSpeed(td);
			model.swing = player.getHandSwingProgress(td);
			model.sneak = player.isInSneakingPose() ? 1.0f : 0.0f;

			lightTimer -= 0.016f;
			if (lightTimer <= 0.0f) {
				lightTimer = 0.25f;
				BlockPos bp = BlockPos.ofFloored(pos.x, pos.y + 1.0, pos.z);
				int sky = world.getLightLevel(LightType.SKY, bp);
				int blk = world.getLightLevel(LightType.BLOCK, bp);
				float level = Math.max(blk, sky * world.getSkyBrightness(1.0f)) / 15.0f;
				float target = 0.5f + 0.5f * Math.max(0.0f, Math.min(1.0f, level));
				lightFactor += (target - lightFactor) * 0.5f;
			}

			matrices.push();
			matrices.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
			float s = scale();
			matrices.scale(s, s, s);
			model.draw(matrices, solid, lightFactor);
			matrices.pop();
			solid.flush();
		} catch (Throwable t) {
			try {
				solid.flush();
			} catch (Throwable ignored) {
			}
			// Если что-то пошло не так, возвращаем обычную модель
			voxel.set(0.0);
		}
	}
}
