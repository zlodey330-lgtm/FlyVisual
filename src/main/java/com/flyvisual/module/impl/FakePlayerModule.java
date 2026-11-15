package com.flyvisual.module.impl;

import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.UUID;

/**
 * FakePlayer: манекен-игрок для тестов твоих функций (TargetHUD, TargetESP, звуки ударов, частицы,
 * килфид, эффект убийства и т.д.).
 *
 * Манекен существует только на твоём клиенте: сервер и другие игроки его не видят, а удары по нему
 * на сервер не отправляются. Урон считается по ванильным правилам (кулдаун, крит, броня).
 * Убили манекен: он сразу возрождается на своей точке. Бесконечный тотем: вместо смерти срабатывает
 * тотем (частицы и звук), у манекена остаётся 1 HP, и так без конца.
 */
public class FakePlayerModule extends Module {
	private static final String[] ARMOR_NAMES = {"Нет", "Кожаная", "Золотая", "Кольчужная", "Железная", "Алмазная", "Незеритовая"};
	private static final String[] HAND_NAMES = {"Нет", "Алмазный меч", "Незеритовый меч", "Незеритовый топор"};
	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private final DoubleSetting armor = addChoice("Броня", 5, ARMOR_NAMES);
	private final DoubleSetting enchanted = addToggle("Зачарованная броня (Защита IV)", false);
	private final DoubleSetting hand = addChoice("Предмет в руке", 0, HAND_NAMES);
	private final DoubleSetting totem = addToggle("Бесконечный тотем", false);
	private final DoubleSetting autoRespawn = addToggle("Автовозрождение", true);
	private final DoubleSetting health = addSetting("Здоровье", 20.0, 2.0, 40.0, 2.0);
	private final DoubleSetting distance = addSetting("Дистанция появления", 3.0, 1.5, 10.0, 0.5);
	private final DoubleSetting lookAtMe = addToggle("Смотрит на меня", true);
	private final DoubleSetting knockback = addToggle("Отбрасывание", true);
	private final DoubleSetting showDamage = addToggle("Урон в экшн-баре", true);

	@SuppressWarnings("unused")
	private final DoubleSetting respawnButton = addButton("Заспавнить заново", this::respawnPressed);
	@SuppressWarnings("unused")
	private final DoubleSetting killButton = addButton("Убить", this::killPressed);

	private OtherClientPlayerEntity fake;
	private boolean dead;
	private int idCounter;

	// Точка возрождения
	private double ax;
	private double ay;
	private double az;
	private boolean anchorSet;
	private ClientWorld anchorWorld;

	// Урон и неуязвимость после удара (как в ванили: 20 тиков, повторный урон только сверх прежнего)
	private int invul;
	private float lastDamage;

	// Отбрасывание (скорость манекена считаем сами: на клиенте у чужих сущностей нет физики)
	private double kbX;
	private double kbY;
	private double kbZ;

	// Что уже применено к манекену (чтобы менять броню и здоровье на лету)
	private int appliedGear = -1;
	private double appliedHealth = -1.0;

	public FakePlayerModule() {
		super("FakePlayer", "Test dummy player on your client");

		// Регистрируется последним среди модулей, поэтому остальные модули (звуки ударов, частицы,
		// килфид, TargetESP) уже увидели удар. Мы же отменяем настоящий удар, чтобы на сервер
		// не ушёл пакет с несуществующей сущностью.
		AttackEntityCallback.EVENT.register((player, world, usedHand, entity, hitResult) -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (world.isClient() && isEnabled() && fake != null && entity == fake && player == mc.player) {
				onHit(player);
				return ActionResult.SUCCESS;
			}
			return ActionResult.PASS;
		});
	}

	// ------------------------------------------------------------------ включение

	@Override
	public void onEnable() {
		dead = false;
		spawn(false);
	}

	@Override
	public void onDisable() {
		remove();
		dead = false;
	}

	private void respawnPressed() {
		if (!isEnabled()) {
			setEnabled(true);
		} else {
			dead = false;
			spawn(false);
		}
	}

	private void killPressed() {
		if (isEnabled() && fake != null) {
			fake.setHealth(0.0f);
		}
	}

	// ------------------------------------------------------------------ тик

	@Override
	public void onTick(MinecraftClient client) {
		ClientWorld world = client.world;
		ClientPlayerEntity player = client.player;
		if (world == null || player == null) {
			fake = null;
			return;
		}

		// Сменился мир, манекен пропал или упал в бездну: ставим заново
		boolean lost = fake == null || fake.isRemoved() || fake.getWorld() != world;
		if (!lost && fake.getY() < world.getBottomY() - 10) {
			remove();
			lost = true;
		}
		if (lost) {
			fake = null;
			if (!dead) spawn(true);
			if (fake == null) return;
		}

		refreshSettings();

		if (invul > 0) invul--;

		if (lookAtMe.getBool()) {
			double dx = player.getX() - fake.getX();
			double dz = player.getZ() - fake.getZ();
			double dy = player.getEyeY() - fake.getEyeY();
			float yaw = (float) (MathHelper.atan2(dz, dx) * 57.29577951308232) - 90.0f;
			float pitch = (float) -(MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 57.29577951308232);
			fake.setYaw(yaw);
			fake.setPitch(pitch);
			fake.setHeadYaw(yaw);
			fake.setBodyYaw(yaw);
		}

		stepPhysics();

		if (fake.getHealth() <= 0.0f) {
			onKilled(world);
		}
	}

	/** Применяет броню, предмет в руке и здоровье, если ты поменял настройки. */
	private void refreshSettings() {
		int gear = armor.getInt() * 1000 + hand.getInt() * 100 + (enchanted.getBool() ? 10 : 0) + (totem.getBool() ? 1 : 0);
		if (gear != appliedGear) {
			applyGear(fake);
			appliedGear = gear;
		}
		if (Math.abs(health.get() - appliedHealth) > 0.001) {
			applyMaxHealth(fake);
			fake.setHealth(fake.getMaxHealth());
			appliedHealth = health.get();
		}
	}

	/** Отбрасывание и падение: на клиенте у чужих игроков нет своей физики, считаем сами. */
	private void stepPhysics() {
		boolean moving = Math.abs(kbX) > 0.003 || Math.abs(kbZ) > 0.003 || Math.abs(kbY) > 0.003 || !fake.isOnGround();
		if (!moving) {
			kbX = 0.0;
			kbY = 0.0;
			kbZ = 0.0;
			return;
		}
		kbY -= 0.08;
		fake.move(MovementType.SELF, new Vec3d(kbX, kbY, kbZ));
		double friction = fake.isOnGround() ? 0.546 : 0.91;
		kbX *= friction;
		kbZ *= friction;
		kbY *= 0.98;
		if (fake.isOnGround() && kbY < 0.0) {
			kbY = 0.0;
		}
	}

	// ------------------------------------------------------------------ удар

	private void onHit(PlayerEntity player) {
		MinecraftClient mc = mc();
		if (mc.world == null || fake == null) return;

		float cooldown = player.getAttackCooldownProgress(0.5f);
		float damage = (float) player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		damage *= 0.2f + cooldown * cooldown * 0.8f;

		// Условия крита как в ванили
		boolean crit = cooldown > 0.9f
				&& player.fallDistance > 0.0f
				&& !player.isOnGround()
				&& !player.isClimbing()
				&& !player.isTouchingWater()
				&& !player.hasStatusEffect(StatusEffects.BLINDNESS)
				&& !player.hasVehicle()
				&& !player.isSprinting();
		if (crit) {
			damage *= 1.5f;
			mc.particleManager.addEmitter(fake, ParticleTypes.CRIT);
		}

		// Настоящий удар отменён, поэтому кулдаун оружия сбрасываем сами
		player.resetLastAttackedTicks();

		// Броня по ванильной формуле
		float armorPoints = 0.0f;
		float toughness = 0.0f;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack stack = fake.getEquippedStack(slot);
			if (stack.getItem() instanceof ArmorItem piece) {
				armorPoints += piece.getProtection();
				toughness += piece.getToughness();
			}
		}
		float dealt = damage;
		if (armorPoints > 0.0f) {
			float f = 2.0f + toughness / 4.0f;
			float g = MathHelper.clamp(armorPoints - dealt / f, armorPoints * 0.2f, 20.0f);
			dealt *= 1.0f - g / 25.0f;
		}
		if (enchanted.getBool() && armorPoints > 0.0f) {
			dealt *= 1.0f - 16.0f / 25.0f; // Защита IV на четырёх вещах
		}

		// Неуязвимость после удара: в первые 10 тиков проходит только урон сверх прежнего
		float applied;
		if (invul > 10) {
			if (dealt <= lastDamage) return;
			applied = dealt - lastDamage;
			lastDamage = dealt;
		} else {
			applied = dealt;
			lastDamage = dealt;
			invul = 20;
		}

		fake.hurtTime = 10;
		fake.maxHurtTime = 10;
		mc.world.playSound(fake.getX(), fake.getY(), fake.getZ(), SoundEvents.ENTITY_PLAYER_HURT,
				SoundCategory.PLAYERS, 1.0f, 1.0f, false);

		if (knockback.getBool()) {
			double dx = fake.getX() - player.getX();
			double dz = fake.getZ() - player.getZ();
			double len = Math.sqrt(dx * dx + dz * dz);
			if (len < 1.0e-4) {
				float yawRad = player.getYaw() * 0.017453292f;
				dx = -MathHelper.sin(yawRad);
				dz = MathHelper.cos(yawRad);
				len = 1.0;
			}
			double strength = 0.4 + (player.isSprinting() ? 0.5 : 0.0);
			kbX = kbX / 2.0 + dx / len * strength;
			kbZ = kbZ / 2.0 + dz / len * strength;
			if (fake.isOnGround()) {
				kbY = Math.min(0.4, kbY / 2.0 + strength);
			}
		}

		float left = fake.getHealth() - applied;
		String note;
		if (left <= 0.0f) {
			if (totem.getBool()) {
				popTotem(mc);
				fake.setHealth(1.0f);
				note = " | тотем сработал, 1 HP";
			} else {
				fake.setHealth(0.0f); // смерть обработает onTick
				note = " | убит";
			}
		} else {
			fake.setHealth(left);
			note = " | HP " + String.format(java.util.Locale.ROOT, "%.1f", left) + "/" + String.format(java.util.Locale.ROOT, "%.0f", fake.getMaxHealth());
		}

		if (showDamage.getBool() && mc.player != null) {
			String text = "Урон " + String.format(java.util.Locale.ROOT, "%.1f", applied) + (crit ? " (крит)" : "") + note;
			mc.player.sendMessage(Text.literal(text), true);
		}
	}

	private void popTotem(MinecraftClient mc) {
		mc.particleManager.addEmitter(fake, ParticleTypes.TOTEM_OF_UNDYING, 30);
		mc.world.playSound(fake.getX(), fake.getY(), fake.getZ(), SoundEvents.ITEM_TOTEM_USE,
				SoundCategory.PLAYERS, 1.0f, 1.0f, false);
	}

	// ------------------------------------------------------------------ смерть и возрождение

	private void onKilled(ClientWorld world) {
		double x = fake.getX();
		double y = fake.getY();
		double z = fake.getZ();
		world.playSound(x, y, z, SoundEvents.ENTITY_PLAYER_DEATH, SoundCategory.PLAYERS, 1.0f, 1.0f, false);
		for (int i = 0; i < 12; i++) {
			world.addParticle(ParticleTypes.POOF,
					x + (world.getRandom().nextDouble() - 0.5) * 0.6,
					y + 0.2 + world.getRandom().nextDouble() * 1.6,
					z + (world.getRandom().nextDouble() - 0.5) * 0.6,
					0.0, 0.03, 0.0);
		}
		remove();
		if (autoRespawn.getBool()) {
			spawn(true);
		} else {
			dead = true;
		}
	}

	// ------------------------------------------------------------------ создание и удаление

	/** keepAnchor: true = на старой точке возрождения, false = новая точка перед тобой. */
	private void spawn(boolean keepAnchor) {
		MinecraftClient mc = mc();
		ClientWorld world = mc.world;
		ClientPlayerEntity player = mc.player;
		if (world == null || player == null) return;
		remove();

		if (!keepAnchor || !anchorSet || anchorWorld != world) {
			float yawRad = player.getYaw() * 0.017453292f;
			double dist = distance.get();
			ax = player.getX() - MathHelper.sin(yawRad) * dist;
			az = player.getZ() + MathHelper.cos(yawRad) * dist;
			ay = groundY(world, ax, player.getY(), az);
			anchorSet = true;
			anchorWorld = world;
		}

		// Одинаковый hashCode у всех UUID манекена: внешний вид (скин по умолчанию) не меняется при возрождении
		long bits = world.getRandom().nextLong();
		UUID uuid = new UUID(bits, bits);
		OtherClientPlayerEntity e = new OtherClientPlayerEntity(world, new GameProfile(uuid, "FakePlayer"));
		e.setId(-7000 - (idCounter++ & 0xFFFF));

		float yaw = (float) (MathHelper.atan2(player.getZ() - az, player.getX() - ax) * 57.29577951308232) - 90.0f;
		e.refreshPositionAndAngles(ax, ay, az, yaw, 0.0f);
		e.setHeadYaw(yaw);
		e.setBodyYaw(yaw);
		e.prevHeadYaw = yaw;
		e.prevBodyYaw = yaw;

		applyMaxHealth(e);
		e.setHealth(e.getMaxHealth());
		applyGear(e);

		world.addEntity(e.getId(), e);
		fake = e;
		appliedGear = armor.getInt() * 1000 + hand.getInt() * 100 + (enchanted.getBool() ? 10 : 0) + (totem.getBool() ? 1 : 0);
		appliedHealth = health.get();
		invul = 0;
		lastDamage = 0.0f;
		kbX = 0.0;
		kbY = 0.0;
		kbZ = 0.0;
	}

	private void remove() {
		if (fake != null) {
			try {
				ClientWorld world = mc().world;
				if (world != null && !fake.isRemoved()) {
					world.removeEntity(fake.getId(), Entity.RemovalReason.DISCARDED);
				}
			} catch (Throwable ignored) {
			}
			fake = null;
		}
	}

	private void applyMaxHealth(OtherClientPlayerEntity e) {
		EntityAttributeInstance inst = e.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (inst != null) inst.setBaseValue(health.get());
	}

	private void applyGear(OtherClientPlayerEntity e) {
		Item[] set = armorSet(armor.getInt());
		for (int i = 0; i < ARMOR_SLOTS.length; i++) {
			ItemStack stack = ItemStack.EMPTY;
			if (i < set.length) {
				stack = new ItemStack(set[i]);
				if (enchanted.getBool()) {
					stack.addEnchantment(Enchantments.PROTECTION, 4);
				}
			}
			e.equipStack(ARMOR_SLOTS[i], stack);
		}

		Item main = null;
		switch (hand.getInt()) {
			case 1 -> main = Items.DIAMOND_SWORD;
			case 2 -> main = Items.NETHERITE_SWORD;
			case 3 -> main = Items.NETHERITE_AXE;
			default -> {
			}
		}
		e.equipStack(EquipmentSlot.MAINHAND, main == null ? ItemStack.EMPTY : new ItemStack(main));
		e.equipStack(EquipmentSlot.OFFHAND, totem.getBool() ? new ItemStack(Items.TOTEM_OF_UNDYING) : ItemStack.EMPTY);
	}

	private static Item[] armorSet(int index) {
		return switch (index) {
			case 1 -> new Item[]{Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS};
			case 2 -> new Item[]{Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS};
			case 3 -> new Item[]{Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS};
			case 4 -> new Item[]{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
			case 5 -> new Item[]{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
			case 6 -> new Item[]{Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
			default -> new Item[0];
		};
	}

	/** Высота земли под точкой: манекен стоит на блоке, а не висит в воздухе. */
	private static double groundY(ClientWorld w, double x, double y, double z) {
		BlockPos.Mutable p = new BlockPos.Mutable(MathHelper.floor(x), MathHelper.floor(y + 1.5), MathHelper.floor(z));
		for (int i = 0; i < 24 && p.getY() > w.getBottomY(); i++) {
			BlockState state = w.getBlockState(p);
			VoxelShape shape = state.getCollisionShape(w, p);
			if (!shape.isEmpty()) {
				return p.getY() + shape.getMax(Direction.Axis.Y);
			}
			p.move(Direction.DOWN);
		}
		return y;
	}
}
