package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;

/**
 * Питомец-компаньон: настоящий моб (лиса, кот, попугай и т.д.), который существует только
 * на твоём клиенте и плавно ходит или летает рядом с тобой. Другие игроки его не видят,
 * серверу ничего не отправляется. До питомца нельзя ударить или прицелиться (см. EntityMixin).
 */
public class PetModule extends Module {
	private static final String[] NAMES = {"Лиса", "Кот", "Волк", "Попугай", "Аллей", "Пчела", "Кролик", "Аксолотль"};
	private static final boolean[] FLYING = {false, false, false, true, true, true, false, false};
	private static final int PET_ID = -424242;

	private final DoubleSetting pet = addChoice("Pet", 0, NAMES);
	private final DoubleSetting distance = addSetting("Pet distance", 1.6, 0.8, 4.0, 0.2);
	private final DoubleSetting side = addChoice("Side", 0, "Справа", "Слева");
	private final DoubleSetting baby = addToggle("Baby", false);

	private LivingEntity current;
	private int currentIndex = -1;
	private boolean currentBaby;
	private ClientWorld currentWorld;
	private int ticks;

	public PetModule() {
		super("Pet", "A companion that follows you");
	}

	public boolean isPet(Entity entity) {
		return current != null && entity == current;
	}

	@Override
	public void onDisable() {
		remove();
	}

	@Override
	public void onTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;
		if (player == null || world == null) {
			current = null;
			currentWorld = null;
			return;
		}

		int index = Math.max(0, Math.min(NAMES.length - 1, pet.getInt()));
		if (current == null || currentWorld != world || currentIndex != index || currentBaby != baby.getBool()
				|| current.isRemoved()) {
			remove();
			spawn(world, player, index);
		}
		if (current == null) return;

		ticks++;
		double yawRad = Math.toRadians(player.getYaw());
		// Вперёд игрока = (-sin, cos); вправо = (cos, sin) -- питомец чуть позади сбоку
		double back = distance.get();
		double sideOffset = side.getInt() == 0 ? 1.0 : -1.0;
		double tx = player.getX() + Math.sin(yawRad) * back + Math.cos(yawRad) * sideOffset * 0.9 * -1.0;
		double tz = player.getZ() - Math.cos(yawRad) * back + Math.sin(yawRad) * sideOffset * 0.9 * -1.0;
		boolean flying = FLYING[index];
		// Аллея (призрака) не трогаем: он двигается как раньше.
		boolean ghost = index == 4;
		double ty;
		if (flying) {
			ty = player.getY() + 1.5 + Math.sin(ticks * 0.12) * 0.18;
		} else if (ghost) {
			ty = player.getY();
		} else {
			ty = groundY(world, tx, tz, player.getY());
		}

		double dx = tx - current.getX();
		double dy = ty - current.getY();
		double dz = tz - current.getZ();
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

		if (dist > 14.0) {
			current.refreshPositionAndAngles(tx, ty, tz, player.getYaw(), 0.0f);
		} else {
			double oldX = current.getX();
			double oldY = current.getY();
			double oldZ = current.getZ();
			float oldYaw = current.getYaw();

			double k = ghost ? 0.14 : 0.10;
			double nx = oldX + dx * k;
			double ny = oldY + dy * k;
			double nz = oldZ + dz * k;

			float yaw = oldYaw;
			double horizontal = Math.hypot(dx, dz);
			boolean moving = horizontal > 0.15;
			if (moving) {
				float want = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
				yaw = approach(yaw, want, ghost ? 0.25f : 0.18f);
			} else {
				yaw = approach(yaw, player.getYaw(), 0.1f);
			}
			current.refreshPositionAndAngles(nx, ny, nz, yaw, 0.0f);

			if (ghost) {
				current.setHeadYaw(yaw);
				current.setBodyYaw(yaw);
			} else {
				// Плавность между тиками: игра сама интерполирует положение и поворот при любом FPS.
				current.prevX = oldX;
				current.prevY = oldY;
				current.prevZ = oldZ;
				current.lastRenderX = oldX;
				current.lastRenderY = oldY;
				current.lastRenderZ = oldZ;
				current.prevYaw = oldYaw;
				current.prevBodyYaw = current.bodyYaw;
				current.prevHeadYaw = current.headYaw;

				current.setBodyYaw(yaw);
				// Когда стоит на месте, голова плавно поворачивается к тебе
				float headTarget = yaw;
				if (!moving) {
					double lx = player.getX() - nx;
					double lz = player.getZ() - nz;
					headTarget = (float) Math.toDegrees(Math.atan2(lz, lx)) - 90.0f;
					float limit = 55.0f;
					float diff = ((headTarget - yaw) % 360.0f + 540.0f) % 360.0f - 180.0f;
					headTarget = yaw + Math.max(-limit, Math.min(limit, diff));
				}
				current.setHeadYaw(approach(current.getHeadYaw(), headTarget, 0.12f));
			}
		}
		current.setOnGround(!flying);
		current.setVelocity(0.0, 0.0, 0.0);
	}

	/** Высота земли под питомцем: он поднимается по ступенькам и склонам, а не висит на уровне игрока. */
	private static double groundY(ClientWorld world, double x, double z, double fallback) {
		int bx = MathHelper.floor(x);
		int bz = MathHelper.floor(z);
		int top = MathHelper.floor(fallback) + 1;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int y = top; y >= top - 4; y--) {
			pos.set(bx, y, bz);
			VoxelShape shape = world.getBlockState(pos).getCollisionShape(world, pos);
			if (!shape.isEmpty()) {
				return y + shape.getMax(Direction.Axis.Y);
			}
		}
		return fallback;
	}

	private static float approach(float from, float to, float k) {
		float diff = ((to - from) % 360.0f + 540.0f) % 360.0f - 180.0f;
		return from + diff * k;
	}

	private void spawn(ClientWorld world, ClientPlayerEntity player, int index) {
		EntityType<?> type = switch (index) {
			case 1 -> EntityType.CAT;
			case 2 -> EntityType.WOLF;
			case 3 -> EntityType.PARROT;
			case 4 -> EntityType.ALLAY;
			case 5 -> EntityType.BEE;
			case 6 -> EntityType.RABBIT;
			case 7 -> EntityType.AXOLOTL;
			default -> EntityType.FOX;
		};
		Entity entity = type.create(world);
		if (!(entity instanceof LivingEntity living)) return;

		living.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), 0.0f);
		living.setSilent(true);
		living.setNoGravity(true);
		living.noClip = true;
		living.setInvulnerable(true);
		if (living instanceof PassiveEntity passive && baby.getBool()) {
			passive.setBaby(true);
		}
		living.setId(PET_ID);
		world.addEntity(PET_ID, living);

		current = living;
		currentWorld = world;
		currentIndex = index;
		currentBaby = baby.getBool();
	}

	private void remove() {
		MinecraftClient client = mc();
		if (current != null && client != null && client.world != null && currentWorld == client.world) {
			client.world.removeEntity(PET_ID, Entity.RemovalReason.DISCARDED);
		}
		current = null;
		currentWorld = null;
		currentIndex = -1;
	}
}
