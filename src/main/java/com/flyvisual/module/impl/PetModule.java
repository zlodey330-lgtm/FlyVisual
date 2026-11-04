package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.flyvisual.util.Draw3d;
import com.flyvisual.util.PetRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

/**
 * Питомец-компаньон со своей моделью (кошка, собака, лисичка, дракончик, призрак, огонёк).
 * Это не моб из Minecraft, а картинка из цветных кубиков: никаких сущностей, ИИ и тиков,
 * поэтому не лагает. Видно только тебе. Прицелиться в него и ударить его нельзя.
 * Позиция считается каждый кадр (а не раз в тик), поэтому движение плавное.
 */
public class PetModule extends Module {
	private final DoubleSetting pet = addChoice("Pet", 0, PetRenderer.NAMES);
	private final DoubleSetting size = addSetting("Size", 1.0, 0.5, 2.0, 0.1);
	private final DoubleSetting distance = addSetting("Pet distance", 1.6, 0.8, 4.0, 0.2);
	private final DoubleSetting side = addChoice("Side", 0, "Справа", "Слева");
	private final DoubleSetting ownColor = addToggle("Own color", false);
	private final DoubleSetting hue = addSetting("Color (hue)", 30.0, 0.0, 355.0, 5.0);

	private double px;
	private double py;
	private double pz;
	private float yaw;
	private float walkPhase;
	private float moving;
	private boolean placed;
	private ClientWorld lastWorld;
	private long lastNanos = System.nanoTime();

	public PetModule() {
		super("Pet", "A companion that follows you");
		WorldRenderEvents.AFTER_ENTITIES.register(this::render);
	}

	@Override
	public void onDisable() {
		placed = false;
	}

	private void render(WorldRenderContext context) {
		if (!isEnabled()) return;

		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		ClientWorld world = mc.world;
		MatrixStack matrices = context.matrixStack();
		if (player == null || world == null || matrices == null) return;

		long now = System.nanoTime();
		float dt = Math.min((now - lastNanos) / 1.0e9f, 0.1f);
		lastNanos = now;

		float tickDelta = context.tickDelta();
		Vec3d pp = player.getLerpedPos(tickDelta);
		int kind = Math.max(0, Math.min(PetRenderer.NAMES.length - 1, pet.getInt()));
		boolean flying = PetRenderer.FLYING[kind];
		float time = (float) ((now / 1.0e9) % 10000.0);

		// Куда должен встать питомец: чуть позади игрока сбоку
		double yawRad = Math.toRadians(player.getYaw());
		double fx = -Math.sin(yawRad);
		double fz = Math.cos(yawRad);
		double rx = -Math.cos(yawRad);
		double rz = -Math.sin(yawRad);
		double sideSign = side.getInt() == 0 ? 1.0 : -1.0;
		double back = distance.get();
		double tx = pp.x - fx * back + rx * sideSign * 0.9;
		double tz = pp.z - fz * back + rz * sideSign * 0.9;
		double ty = pp.y + (flying ? 1.25 + Math.sin(time * 1.6) * 0.1 : 0.0);

		if (!placed || lastWorld != world || Math.abs(tx - px) + Math.abs(tz - pz) > 24.0) {
			px = tx;
			py = ty;
			pz = tz;
			yaw = player.getYaw();
			placed = true;
			lastWorld = world;
		}

		// Плавное следование: чем дальше цель, тем быстрее догоняет
		double dx = tx - px;
		double dy = ty - py;
		double dz = tz - pz;
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		double k = 1.0 - Math.exp(-(4.0 + dist * 2.0) * dt);
		double moveX = dx * k;
		double moveZ = dz * k;
		px += moveX;
		py += dy * (1.0 - Math.exp(-(flying ? 5.0 : 12.0) * dt));
		pz += moveZ;

		double moved = Math.sqrt(moveX * moveX + moveZ * moveZ);
		float speed = dt > 0.0001f ? (float) (moved / dt) : 0.0f;
		moving += (MathHelper.clamp(speed / 1.4f, 0.0f, 1.0f) - moving) * (1.0f - (float) Math.exp(-10.0f * dt));
		walkPhase += (float) moved * 9.0f;

		// Поворот: по ходу движения, а на месте смотрит туда же, куда игрок
		float wantYaw;
		if (speed > 0.35f) {
			wantYaw = (float) Math.toDegrees(Math.atan2(-moveX, moveZ));
		} else {
			wantYaw = player.getYaw();
		}
		float diff = MathHelper.wrapDegrees(wantYaw - yaw);
		yaw += diff * (1.0f - (float) Math.exp(-8.0f * dt));

		// Освещение: ночью и в пещерах питомец темнее
		float lightFactor = 1.0f;
		BlockPos bp = new BlockPos((int) Math.floor(px), (int) Math.floor(py + 0.5), (int) Math.floor(pz));
		int blockLight = world.getLightLevel(LightType.BLOCK, bp);
		int skyLight = world.getLightLevel(LightType.SKY, bp);
		float day = MathHelper.clamp((float) Math.cos(world.getSkyAngle(1.0f) * Math.PI * 2.0) * 2.0f + 0.5f, 0.0f, 1.0f);
		float level = Math.max(blockLight / 15.0f, skyLight / 15.0f * day);
		lightFactor = 0.45f + 0.55f * level;

		Vec3d cam = context.camera().getPos();
		Draw3d draw = new Draw3d(false);
		matrices.push();
		matrices.translate(px - cam.x, py - cam.y, pz - cam.z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
		float s = size.getFloat();
		matrices.scale(s, s, s);
		PetRenderer.draw(kind, matrices, draw, lightFactor, time, walkPhase, moving, ownColor.getBool(), hue.getFloat());
		matrices.pop();
		draw.flush();
	}
}
