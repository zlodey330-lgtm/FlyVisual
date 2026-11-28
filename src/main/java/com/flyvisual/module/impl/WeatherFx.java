package com.flyvisual.module.impl;

import com.flyvisual.util.Draw3d;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Собственные погодные эффекты FlyVisual: снег, листья, лепестки, дождь, блики, вихри, лужи и снежный покров.
 * Всё считается и рисуется только на клиенте и не зависит от шейдеров.
 */
final class WeatherFx {
	static final int SNOW = 0;
	static final int LEAF = 1;
	static final int PETAL = 2;
	static final int RAIN = 3;
	static final int MOTE = 4;
	static final int RING = 5;
	static final int MAX = 6500;

	static final int[][] AUTUMN_PALETTES = {
			{0xE3B72F, 0xD9822B, 0xC5502A, 0xA83232, 0xB59B3A},
			{0xE8C43A, 0xD9B22A, 0xEED15A},
			{0xD9822B, 0xE08A30, 0xC96F1E},
			{0xB5382A, 0xA83232, 0xC9442E}
	};
	static final int[][] SPRING_PALETTES = {
			{0xF6B6D0, 0xF2A0C0, 0xFAD0E0},
			{0xFFFFFF, 0xFDF2F8, 0xF4F4F4},
			{0xF7C0D6, 0xFBE7EF, 0xA6D96F, 0xFFFFFF}
	};

	/** Настройки на этот тик: заполняет WeatherModule. */
	static final class Cfg {
		float range = 32.0f;
		float density = 1.0f;
		int cap = 3500;
		float windX;
		float windZ;
		float snow;
		float leaves;
		float petals;
		float rain;
		float motes;
		float snowSize = 1.0f;
		float leafSpeed = 1.0f;
		float leafSpin = 1.0f;
		float rainSlant = 1.0f;
		float soundVol = 1.0f;
		float puddleSize = 1.0f;
		float groundSnow;
		float wetness;
		float groundRange = 20.0f;
		float blizzard;
		int leafPalette;
		int petalPalette;
		boolean splashes = true;
		boolean puddles = true;
		boolean stepSplash = true;
		boolean ground = true;
	}

	private static final class P {
		boolean alive;
		boolean resting;
		int kind;
		int rgb;
		int age;
		int life;
		float x;
		float y;
		float z;
		float px;
		float py;
		float pz;
		float vx;
		float vy;
		float vz;
		float size;
		float rot;
		float rotV;
		float tumble;
		float tumbleV;
		float fall;
		float seed;
		float floor;
	}

	private static final class Vortex {
		float x;
		float z;
		float radius;
		float strength;
		int age;
		int life;
	}

	private final P[] pool = new P[MAX];
	private final List<Vortex> vortices = new ArrayList<>();
	private final Random rnd = new Random();
	private final Draw3d draw = new Draw3d(true);
	private int alive;
	private int cursor;
	private long tick;
	private float accSnow;
	private float accLeaf;
	private float accPetal;
	private float accRain;
	private float accMote;
	private boolean wasOnGround = true;

	// Кэш поверхности вокруг игрока: для снежного покрова и луж
	private int gOx;
	private int gOz;
	private int gSize;
	private float[] gY = new float[0];
	private byte[] gType = new byte[0];
	private long gBuilt = -1000L;
	private int gPx = Integer.MIN_VALUE;
	private int gPz = Integer.MIN_VALUE;

	// Векторы камеры для billboard-частиц
	private float rx;
	private float ry;
	private float rz;
	private float ux;
	private float uy;
	private float uz;

	private static final float[] COS8 = new float[8];
	private static final float[] SIN8 = new float[8];

	static {
		for (int i = 0; i < 8; i++) {
			COS8[i] = (float) Math.cos(i * Math.PI / 4.0);
			SIN8[i] = (float) Math.sin(i * Math.PI / 4.0);
		}
	}

	WeatherFx() {
		for (int i = 0; i < MAX; i++) {
			pool[i] = new P();
		}
	}

	void clear() {
		for (int i = 0; i < MAX; i++) {
			pool[i].alive = false;
		}
		alive = 0;
		vortices.clear();
		gSize = 0;
		gPx = Integer.MIN_VALUE;
		accSnow = accLeaf = accPetal = accRain = accMote = 0.0f;
	}

	// ---------- вспомогательные функции ----------

	static float hash(int x, int z) {
		int h = x * 374761393 + z * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / 16777216.0f;
	}

	static float hash3(int x, int y, int z) {
		int h = x * 374761393 + y * 1103515245 + z * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / 16777216.0f;
	}

	private static float vnoise(float x, float z) {
		int xi = (int) Math.floor(x);
		int zi = (int) Math.floor(z);
		float fx = x - xi;
		float fz = z - zi;
		fx = fx * fx * (3.0f - 2.0f * fx);
		fz = fz * fz * (3.0f - 2.0f * fz);
		float a = hash(xi, zi);
		float b = hash(xi + 1, zi);
		float c = hash(xi, zi + 1);
		float d = hash(xi + 1, zi + 1);
		float top = a + (b - a) * fx;
		float bottom = c + (d - c) * fx;
		return top + (bottom - top) * fz;
	}

	private static float clamp01(float v) {
		return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
	}

	private P alloc() {
		for (int k = 0; k < MAX; k++) {
			cursor++;
			if (cursor >= MAX) cursor = 0;
			P p = pool[cursor];
			if (!p.alive) {
				p.alive = true;
				p.resting = false;
				p.age = 0;
				p.vx = 0.0f;
				p.vy = 0.0f;
				p.vz = 0.0f;
				p.rot = 0.0f;
				p.rotV = 0.0f;
				p.tumble = 0.0f;
				p.tumbleV = 0.0f;
				p.seed = rnd.nextFloat() * 6.2831853f;
				alive++;
				return p;
			}
		}
		return null;
	}

	private void kill(P p) {
		if (p.alive) {
			p.alive = false;
			alive--;
		}
	}

	private static boolean loaded(ClientWorld w, int bx, int bz) {
		return w.getChunkManager().isChunkLoaded(bx >> 4, bz >> 4);
	}

	/** Высота, на которую падает осадок (первая свободная клетка над верхним блоком). NaN, если чанк не загружен. */
	private static float heightFloor(ClientWorld w, float x, float z) {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		if (!loaded(w, bx, bz)) return Float.NaN;
		return w.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz);
	}

	/** Земля под точкой, листва пропускается (для опадающих листьев). */
	private static float groundBelow(ClientWorld w, int bx, int y, int bz) {
		if (!loaded(w, bx, bz)) return Float.NaN;
		BlockPos.Mutable mp = new BlockPos.Mutable();
		int bottom = w.getBottomY();
		for (int yy = y; yy > bottom && yy > y - 90; yy--) {
			mp.set(bx, yy, bz);
			BlockState s = w.getBlockState(mp);
			if (s.isAir() || s.isIn(BlockTags.LEAVES)) continue;
			if (!s.getFluidState().isEmpty()) return yy + 0.9f;
			if (!s.getCollisionShape(w, mp).isEmpty()) return yy + 1.0f;
		}
		return Float.NaN;
	}

	private int pickColor(int kind, Cfg c) {
		if (kind == LEAF) {
			int[] pal = AUTUMN_PALETTES[Math.max(0, Math.min(AUTUMN_PALETTES.length - 1, c.leafPalette))];
			return pal[rnd.nextInt(pal.length)];
		}
		int[] pal = SPRING_PALETTES[Math.max(0, Math.min(SPRING_PALETTES.length - 1, c.petalPalette))];
		return pal[rnd.nextInt(pal.length)];
	}

	// ---------- тик ----------

	void tick(MinecraftClient mc, Cfg c) {
		ClientWorld w = mc.world;
		ClientPlayerEntity pl = mc.player;
		if (w == null || pl == null) {
			clear();
			return;
		}
		tick++;
		float plx = (float) pl.getX();
		float ply = (float) pl.getY();
		float plz = (float) pl.getZ();

		// 1. движение частиц
		for (int i = 0; i < MAX; i++) {
			P p = pool[i];
			if (p.alive) step(w, p, c, plx, plz);
		}
		for (int i = vortices.size() - 1; i >= 0; i--) {
			Vortex v = vortices.get(i);
			v.age++;
			if (v.age >= v.life) vortices.remove(i);
		}

		// 2. появление новых частиц
		float area = (c.range / 32.0f) * (c.range / 32.0f);
		float k = c.density * area;
		accSnow += c.snow * k * 0.0035f * 3217.0f;
		accRain += c.rain * k * 0.055f * 3217.0f;
		accLeaf += c.leaves * k * 40.0f;
		accPetal += c.petals * k * 34.0f;
		accMote += c.motes * k * 0.9f;
		int nSnow = Math.min(500, (int) accSnow);
		accSnow -= nSnow;
		int nRain = Math.min(700, (int) accRain);
		accRain -= nRain;
		int nLeaf = Math.min(200, (int) accLeaf);
		accLeaf -= nLeaf;
		int nPetal = Math.min(200, (int) accPetal);
		accPetal -= nPetal;
		int nMote = Math.min(20, (int) accMote);
		accMote -= nMote;
		if (c.snow <= 0.001f) accSnow = 0.0f;
		if (c.rain <= 0.001f) accRain = 0.0f;
		if (c.leaves <= 0.001f) accLeaf = 0.0f;
		if (c.petals <= 0.001f) accPetal = 0.0f;
		if (c.motes <= 0.001f) accMote = 0.0f;

		if (nSnow > 0) spawnPrecip(w, c, SNOW, nSnow, plx, ply, plz);
		if (nRain > 0) spawnPrecip(w, c, RAIN, nRain, plx, ply, plz);
		if (nLeaf > 0) spawnFoliage(w, c, LEAF, nLeaf, plx, ply, plz);
		if (nPetal > 0) spawnFoliage(w, c, PETAL, nPetal, plx, ply, plz);
		if (nMote > 0 && w.isDay()) spawnMotes(w, c, nMote, plx, ply, plz);

		// 3. поверхность вокруг игрока
		if (c.ground) {
			int pbx = (int) Math.floor(plx);
			int pbz = (int) Math.floor(plz);
			int dx = pbx - gPx;
			int dz = pbz - gPz;
			if (gSize == 0 || tick - gBuilt >= 12 || dx * dx + dz * dz >= 9) {
				rebuildGround(w, c, pbx, pbz);
				gBuilt = tick;
				gPx = pbx;
				gPz = pbz;
			}
		}

		// 4. звуки и брызги
		if (c.rain > 0.03f) soundTick(w, pl, c);
		stepSplashes(w, pl, c);
	}

	private void step(ClientWorld w, P p, Cfg c, float plx, float plz) {
		p.px = p.x;
		p.py = p.y;
		p.pz = p.z;
		p.age++;
		if (p.age >= p.life) {
			kill(p);
			return;
		}
		float dx = p.x - plx;
		float dz = p.z - plz;
		float lim = c.range * 1.3f + 4.0f;
		if (dx * dx + dz * dz > lim * lim) {
			kill(p);
			return;
		}
		if (p.kind == RING || p.resting) return;

		float gust = 1.0f + 0.45f * (float) Math.sin(p.x * 0.11f + p.z * 0.07f + tick * 0.045f + p.seed);
		float wx = c.windX * gust;
		float wz = c.windZ * gust;

		switch (p.kind) {
			case SNOW -> {
				p.vx += (wx * 1.1f - p.vx) * 0.06f + (float) Math.sin(p.age * 0.13f + p.seed) * 0.0035f;
				p.vz += (wz * 1.1f - p.vz) * 0.06f + (float) Math.cos(p.age * 0.11f + p.seed) * 0.0035f;
				p.vy = -p.fall * (1.0f + c.blizzard * 0.25f);
			}
			case LEAF, PETAL -> {
				float flat = 0.55f + 0.45f * Math.abs((float) Math.cos(p.tumble));
				float spin = p.kind == LEAF ? c.leafSpin : 1.0f;
				p.vx += (wx * 1.5f - p.vx) * 0.05f + (float) Math.sin(p.tumble) * 0.0045f * spin;
				p.vz += (wz * 1.5f - p.vz) * 0.05f + (float) Math.cos(p.tumble * 0.8f + p.seed) * 0.0045f * spin;
				p.vy = -p.fall * flat;
				p.rot += p.rotV * spin;
				p.tumble += p.tumbleV * spin;
				applyVortices(p);
			}
			case RAIN -> {
				p.vx = wx * 0.6f * c.rainSlant;
				p.vz = wz * 0.6f * c.rainSlant;
				p.vy = -p.fall;
			}
			case MOTE -> {
				p.vx = (p.vx + (rnd.nextFloat() - 0.5f) * 0.0025f + wx * 0.01f) * 0.96f;
				p.vz = (p.vz + (rnd.nextFloat() - 0.5f) * 0.0025f + wz * 0.01f) * 0.96f;
				p.vy = (float) Math.sin(p.age * 0.04f + p.seed) * 0.004f + 0.0015f;
			}
			default -> {
			}
		}

		p.x += p.vx;
		p.y += p.vy;
		p.z += p.vz;
		if (p.kind == MOTE) return;

		if ((p.age + (int) (p.seed * 3.0f)) % 4 == 0) {
			float f;
			if (p.kind == LEAF || p.kind == PETAL) {
				f = groundBelow(w, (int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z));
			} else {
				f = heightFloor(w, p.x, p.z);
			}
			if (Float.isNaN(f)) {
				kill(p);
				return;
			}
			p.floor = f;
		}
		if (p.y <= p.floor) land(w, p, c, plx, plz);
	}

	private void applyVortices(P p) {
		for (int i = 0; i < vortices.size(); i++) {
			Vortex v = vortices.get(i);
			float dx = p.x - v.x;
			float dz = p.z - v.z;
			float d2 = dx * dx + dz * dz;
			if (d2 >= v.radius * v.radius) continue;
			float d = (float) Math.sqrt(d2) + 0.001f;
			float fade = Math.min(1.0f, Math.min(v.age / 30.0f, (v.life - v.age) / 30.0f));
			float f = (1.0f - d / v.radius) * fade * v.strength;
			p.vx += (-dz / d) * f - (dx / d) * f * 0.12f;
			p.vz += (dx / d) * f - (dz / d) * f * 0.12f;
			p.vy += f * 1.6f;
		}
	}

	private void land(ClientWorld w, P p, Cfg c, float plx, float plz) {
		switch (p.kind) {
			case RAIN -> {
				float dx = p.x - plx;
				float dz = p.z - plz;
				float d2 = dx * dx + dz * dz;
				if (d2 < 18.0f * 18.0f) {
					int bx = (int) Math.floor(p.x);
					int bz = (int) Math.floor(p.z);
					int type = typeAt(bx, bz);
					if (c.splashes && c.ground) {
						float chance = 0.05f;
						if (type == 4) chance = 0.16f;
						else if ((type == 1 || type == 2) && c.puddles && puddleStrength(bx, bz, c) > 0.2f) chance = 0.14f;
						if (rnd.nextFloat() < chance) spawnRing(p.x, p.floor + (type == 4 ? 0.02f : 0.04f), p.z);
					}
					if (c.soundVol > 0.01f && rnd.nextFloat() < 0.012f) {
						float vol = c.soundVol * Math.min(1.0f, c.rain);
						float pitch;
						float loud = 0.07f;
						switch (type) {
							case 4 -> pitch = 1.15f;
							case 3 -> {
								pitch = 0.7f;
								loud = 0.05f;
							}
							case 2 -> pitch = 1.4f;
							case 1 -> pitch = 1.0f;
							default -> pitch = 0.9f;
						}
						pitch += (rnd.nextFloat() - 0.5f) * 0.15f;
						w.playSound(p.x, p.floor, p.z, SoundEvents.WEATHER_RAIN, SoundCategory.WEATHER, loud * vol, pitch, false);
					}
				}
				kill(p);
			}
			case LEAF, PETAL -> {
				p.y = p.floor + 0.02f;
				p.vx = 0.0f;
				p.vy = 0.0f;
				p.vz = 0.0f;
				p.resting = true;
				p.life = p.age + 50 + rnd.nextInt(90);
			}
			default -> kill(p);
		}
	}

	private void spawnRing(float x, float y, float z) {
		P p = alloc();
		if (p == null) return;
		p.kind = RING;
		p.x = x;
		p.y = y;
		p.z = z;
		p.px = x;
		p.py = y;
		p.pz = z;
		p.life = 12 + rnd.nextInt(6);
		p.size = 0.25f + rnd.nextFloat() * 0.2f;
		p.rgb = 0xDCEBFF;
	}

	private void spawnPrecip(ClientWorld w, Cfg c, int kind, int n, float plx, float ply, float plz) {
		for (int i = 0; i < n; i++) {
			if (alive >= c.cap) return;
			float ang = rnd.nextFloat() * 6.2831853f;
			float r = c.range * (float) Math.sqrt(rnd.nextFloat());
			float x = plx + (float) Math.cos(ang) * r;
			float z = plz + (float) Math.sin(ang) * r;
			float f = heightFloor(w, x, z);
			if (Float.isNaN(f)) continue;
			float top = Math.min(f + 18.0f, ply + 16.0f);
			if (top < f + 2.0f) continue;
			P p = alloc();
			if (p == null) return;
			p.kind = kind;
			p.x = x;
			p.z = z;
			p.y = top - rnd.nextFloat() * 1.5f;
			p.px = p.x;
			p.py = p.y;
			p.pz = p.z;
			p.floor = f;
			if (kind == RAIN) {
				p.fall = 0.85f + rnd.nextFloat() * 0.4f;
				p.size = 0.010f + rnd.nextFloat() * 0.005f;
				p.life = (int) ((top - f) / p.fall) + 6;
				p.rgb = 0xBFD4F2;
			} else {
				p.fall = 0.045f + rnd.nextFloat() * 0.06f;
				p.size = 0.022f + rnd.nextFloat() * 0.03f;
				p.life = (int) ((top - f) / p.fall) + 20;
				p.rgb = 0xFFFFFF;
			}
		}
	}

	private void spawnFoliage(ClientWorld w, Cfg c, int kind, int attempts, float plx, float ply, float plz) {
		BlockPos.Mutable mp = new BlockPos.Mutable();
		for (int i = 0; i < attempts; i++) {
			if (alive >= c.cap) return;
			float ang = rnd.nextFloat() * 6.2831853f;
			float r = c.range * 0.75f * (float) Math.sqrt(rnd.nextFloat());
			float x = plx + (float) Math.cos(ang) * r;
			float z = plz + (float) Math.sin(ang) * r;
			int bx = (int) Math.floor(x);
			int bz = (int) Math.floor(z);
			if (!loaded(w, bx, bz)) continue;
			float sy;
			float fl;
			if (rnd.nextFloat() < 0.18f) {
				// «ветром принесло»: лист или лепесток из воздуха над игроком
				float f = heightFloor(w, x, z);
				if (Float.isNaN(f)) continue;
				sy = Math.min(f + 14.0f, ply + 12.0f);
				if (sy < f + 2.0f) continue;
				fl = f;
			} else {
				int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz) - 1;
				mp.set(bx, top, bz);
				if (!w.getBlockState(mp).isIn(BlockTags.LEAVES)) continue;
				int y = top;
				int guard = 0;
				while (guard++ < 14) {
					mp.set(bx, y - 1, bz);
					if (w.getBlockState(mp).isIn(BlockTags.LEAVES)) y--;
					else break;
				}
				sy = y - 0.1f;
				fl = groundBelow(w, bx, y - 1, bz);
				if (Float.isNaN(fl) || sy < fl + 1.0f) continue;
			}
			P p = alloc();
			if (p == null) return;
			initFoliage(p, kind, c, x, sy, z, fl);
		}
	}

	private void initFoliage(P p, int kind, Cfg c, float x, float y, float z, float floor) {
		p.kind = kind;
		p.x = x;
		p.y = y;
		p.z = z;
		p.px = x;
		p.py = y;
		p.pz = z;
		p.floor = floor;
		p.rot = rnd.nextFloat() * 6.2831853f;
		p.rotV = (rnd.nextBoolean() ? 1.0f : -1.0f) * (0.04f + rnd.nextFloat() * 0.1f);
		p.tumble = rnd.nextFloat() * 6.2831853f;
		p.tumbleV = 0.07f + rnd.nextFloat() * 0.13f;
		p.rgb = pickColor(kind, c);
		if (kind == LEAF) {
			p.size = 0.085f + rnd.nextFloat() * 0.06f;
			p.fall = (0.028f + rnd.nextFloat() * 0.02f) * c.leafSpeed;
		} else {
			p.size = 0.05f + rnd.nextFloat() * 0.035f;
			p.fall = (0.02f + rnd.nextFloat() * 0.015f) * c.leafSpeed;
		}
		p.life = (int) (Math.max(1.0f, y - floor) / Math.max(0.005f, p.fall * 0.75f)) + 140;
	}

	private void spawnMotes(ClientWorld w, Cfg c, int n, float plx, float ply, float plz) {
		float rr = Math.min(18.0f, c.range * 0.6f);
		for (int i = 0; i < n; i++) {
			if (alive >= c.cap) return;
			float ang = rnd.nextFloat() * 6.2831853f;
			float r = rr * (float) Math.sqrt(rnd.nextFloat());
			float x = plx + (float) Math.cos(ang) * r;
			float z = plz + (float) Math.sin(ang) * r;
			float f = heightFloor(w, x, z);
			if (Float.isNaN(f)) continue;
			if (Math.abs(f - ply) > 6.0f) continue;
			P p = alloc();
			if (p == null) return;
			p.kind = MOTE;
			p.x = x;
			p.y = f + 0.4f + rnd.nextFloat() * 2.4f;
			p.z = z;
			p.px = p.x;
			p.py = p.y;
			p.pz = p.z;
			p.floor = f;
			p.size = 0.018f + rnd.nextFloat() * 0.02f;
			p.life = 120 + rnd.nextInt(140);
			p.rgb = 0xFFE9A0;
		}
	}

	/** Сильный порыв ветра: вихрь из листьев (осень) или лепестков (весна). */
	void startVortex(MinecraftClient mc, Cfg c) {
		ClientWorld w = mc.world;
		ClientPlayerEntity pl = mc.player;
		if (w == null || pl == null) return;
		int kind = c.leaves >= c.petals ? LEAF : PETAL;
		if (Math.max(c.leaves, c.petals) < 0.05f) return;
		if (vortices.size() >= 2) return;
		float ang = rnd.nextFloat() * 6.2831853f;
		float dist = 6.0f + rnd.nextFloat() * 12.0f;
		Vortex v = new Vortex();
		v.x = (float) pl.getX() + (float) Math.cos(ang) * dist;
		v.z = (float) pl.getZ() + (float) Math.sin(ang) * dist;
		v.radius = 4.5f + rnd.nextFloat() * 3.5f;
		v.strength = 0.045f;
		v.life = 140 + rnd.nextInt(100);
		vortices.add(v);
		float f = heightFloor(w, v.x, v.z);
		if (Float.isNaN(f)) return;
		int burst = 25 + rnd.nextInt(35);
		for (int i = 0; i < burst; i++) {
			if (alive >= c.cap) return;
			P p = alloc();
			if (p == null) return;
			float a = rnd.nextFloat() * 6.2831853f;
			float r = v.radius * 0.8f * (float) Math.sqrt(rnd.nextFloat());
			initFoliage(p, kind, c, v.x + (float) Math.cos(a) * r, f + 0.3f + rnd.nextFloat() * 1.8f,
					v.z + (float) Math.sin(a) * r, f);
		}
	}

	// ---------- поверхность ----------

	private void rebuildGround(ClientWorld w, Cfg c, int pbx, int pbz) {
		int g = Math.max(4, Math.round(c.groundRange));
		int size = g * 2 + 1;
		if (gY.length != size * size) {
			gY = new float[size * size];
			gType = new byte[size * size];
		}
		gOx = pbx - g;
		gOz = pbz - g;
		gSize = size;
		BlockPos.Mutable mp = new BlockPos.Mutable();
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				int idx = i * size + j;
				gType[idx] = 0;
				int ddx = i - g;
				int ddz = j - g;
				if (ddx * ddx + ddz * ddz > g * g) continue;
				int bx = gOx + i;
				int bz = gOz + j;
				if (!loaded(w, bx, bz)) continue;
				int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz);
				mp.set(bx, top - 1, bz);
				BlockState st = w.getBlockState(mp);
				byte t;
				if (!st.getFluidState().isEmpty()) {
					t = 4;
				} else if (st.isIn(BlockTags.LEAVES)) {
					t = 3;
				} else if (st.isSideSolidFullSquare(w, mp, Direction.UP)) {
					t = (st.isOf(Blocks.GRASS_BLOCK) || st.isOf(Blocks.DIRT) || st.isOf(Blocks.PODZOL)
							|| st.isOf(Blocks.MYCELIUM) || st.isOf(Blocks.COARSE_DIRT)) ? (byte) 1 : (byte) 2;
				} else {
					t = 0;
				}
				gType[idx] = t;
				gY[idx] = t == 4 ? top - 0.1f : top;
			}
		}
	}

	private int typeAt(int bx, int bz) {
		int i = bx - gOx;
		int j = bz - gOz;
		if (gSize == 0 || i < 0 || j < 0 || i >= gSize || j >= gSize) return 0;
		return gType[i * gSize + j];
	}

	private float puddleStrength(int bx, int bz, Cfg c) {
		if (c.wetness < 0.02f) return 0.0f;
		int t = typeAt(bx, bz);
		if (t != 1 && t != 2) return 0.0f;
		float n = 0.7f * vnoise(bx * 0.2f, bz * 0.2f) + 0.3f * vnoise(bx * 0.6f + 13.0f, bz * 0.6f + 7.0f);
		float thr = 0.74f - 0.2f * c.wetness * c.puddleSize;
		return clamp01((n - thr) * 10.0f) * Math.min(1.0f, c.wetness * 5.0f);
	}

	// ---------- звуки и брызги игрока ----------

	private void soundTick(ClientWorld w, ClientPlayerEntity pl, Cfg c) {
		if (c.soundVol <= 0.01f) return;
		float vol = c.soundVol * Math.min(1.0f, c.rain);
		int bx = (int) Math.floor(pl.getX());
		int bz = (int) Math.floor(pl.getZ());
		if (!loaded(w, bx, bz)) return;
		boolean exposed = w.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz) <= (int) Math.ceil(pl.getEyeY());
		if (rnd.nextFloat() < 0.35f * Math.min(1.0f, c.rain)) {
			if (exposed) {
				w.playSound(pl.getX(), pl.getEyeY(), pl.getZ(), SoundEvents.WEATHER_RAIN_ABOVE, SoundCategory.WEATHER,
						0.2f * vol, 0.95f + rnd.nextFloat() * 0.1f, false);
			} else {
				w.playSound(pl.getX(), pl.getEyeY(), pl.getZ(), SoundEvents.WEATHER_RAIN, SoundCategory.WEATHER,
						0.1f * vol, 0.6f + rnd.nextFloat() * 0.1f, false);
			}
		}
	}

	private void stepSplashes(ClientWorld w, ClientPlayerEntity pl, Cfg c) {
		boolean on = pl.isOnGround();
		Vec3d vel = pl.getVelocity();
		boolean landed = on && !wasOnGround;
		boolean jumped = !on && wasOnGround && vel.y > 0.1;
		wasOnGround = on;
		if (!c.ground || !c.puddles || !c.stepSplash || c.wetness < 0.25f) return;
		int bx = (int) Math.floor(pl.getX());
		int bz = (int) Math.floor(pl.getZ());
		if (puddleStrength(bx, bz, c) < 0.3f) return;
		double speed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
		int n = 0;
		if (landed) n = 14;
		else if (jumped) n = 7;
		else if (on && speed > 0.06 && tick % 3 == 0) n = 3;
		if (n == 0) return;
		OptimizationModule.ownSpawn = true;
		try {
			for (int i = 0; i < n; i++) {
				w.addParticle(ParticleTypes.SPLASH,
						pl.getX() + (rnd.nextDouble() - 0.5) * 0.5, pl.getY() + 0.05, pl.getZ() + (rnd.nextDouble() - 0.5) * 0.5,
						(rnd.nextDouble() - 0.5) * 0.2, 0.15 + rnd.nextDouble() * 0.15, (rnd.nextDouble() - 0.5) * 0.2);
			}
		} finally {
			OptimizationModule.ownSpawn = false;
		}
		if ((landed || jumped) && c.soundVol > 0.01f) {
			w.playSound(pl.getX(), pl.getY(), pl.getZ(), SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS,
					(landed ? 0.18f : 0.1f) * c.soundVol, 1.2f + rnd.nextFloat() * 0.2f, false);
		}
	}

	// ---------- рисование ----------

	void render(WorldRenderContext ctx, Cfg c) {
		if (alive == 0 && gSize == 0) return;
		MatrixStack ms = ctx.matrixStack();
		if (ms == null) return;
		Vec3d cam = ctx.camera().getPos();
		Matrix4f m = ms.peek().getPositionMatrix();
		float td = ctx.tickDelta();

		float yaw = (float) Math.toRadians(ctx.camera().getYaw());
		float pitch = (float) Math.toRadians(ctx.camera().getPitch());
		float cy = (float) Math.cos(yaw);
		float sy = (float) Math.sin(yaw);
		float cp = (float) Math.cos(pitch);
		float sp = (float) Math.sin(pitch);
		float fx = -sy * cp;
		float fy = -sp;
		float fz = cy * cp;
		rx = -cy;
		ry = 0.0f;
		rz = -sy;
		ux = -sy * sp;
		uy = cp;
		uz = cy * sp;

		try {
			if (c.ground) drawGround(m, c, cam);
			drawParticles(m, c, cam, td, fx, fy, fz);
		} finally {
			draw.flush();
		}
	}

	private void drawGround(Matrix4f m, Cfg c, Vec3d cam) {
		if (gSize == 0) return;
		boolean snow = c.groundSnow > 0.01f;
		boolean wet = c.puddles && c.wetness > 0.02f;
		if (!snow && !wet) return;
		int g = (gSize - 1) / 2;
		float gf = Math.max(1.0f, g);
		for (int i = 0; i < gSize; i++) {
			for (int j = 0; j < gSize; j++) {
				int t = gType[i * gSize + j];
				if (t == 0 || t == 4) continue;
				int bx = gOx + i;
				int bz = gOz + j;
				float cx = bx + 0.5f - (float) cam.x;
				float cz = bz + 0.5f - (float) cam.z;
				float dist = (float) Math.sqrt(cx * cx + cz * cz);
				float fade = clamp01(1.0f - (dist / gf - 0.65f) / 0.35f);
				if (fade <= 0.0f) continue;
				float y = gY[i * gSize + j] - (float) cam.y + 0.02f + dist * 0.0007f;

				if (snow) {
					float cover = t == 3 ? c.groundSnow * 0.85f : c.groundSnow;
					float h = hash(bx, bz);
					float local = clamp01((cover - h * 0.9f) / 0.25f);
					if (local > 0.0f) {
						float half = 0.52f * (0.6f + 0.4f * local);
						float shade = 0.93f + 0.07f * hash(bz, bx);
						float a = 0.95f * local * fade;
						float r = 0.95f * shade;
						float gg = 0.97f * shade;
						float b = 1.0f * shade;
						draw.vertex(m, cx - half, y, cz - half, r, gg, b, a);
						draw.vertex(m, cx - half, y, cz + half, r, gg, b, a);
						draw.vertex(m, cx + half, y, cz + half, r, gg, b, a);
						draw.vertex(m, cx + half, y, cz - half, r, gg, b, a);
					}
				}
				if (wet && t != 3) {
					float s = puddleStrength(bx, bz, c);
					if (s > 0.02f) {
						float a = 0.5f * s * fade;
						float rad = 0.55f * (0.5f + 0.5f * s);
						float yy = y + 0.004f;
						for (int q = 0; q < 4; q++) {
							int k0 = q * 2;
							int k1 = q * 2 + 1;
							int k2 = (q * 2 + 2) % 8;
							draw.vertex(m, cx, yy, cz, 0.30f, 0.42f, 0.56f, a);
							draw.vertex(m, cx + COS8[k0] * rad, yy, cz + SIN8[k0] * rad, 0.42f, 0.55f, 0.70f, a * 0.8f);
							draw.vertex(m, cx + COS8[k1] * rad, yy, cz + SIN8[k1] * rad, 0.42f, 0.55f, 0.70f, a * 0.8f);
							draw.vertex(m, cx + COS8[k2] * rad, yy, cz + SIN8[k2] * rad, 0.42f, 0.55f, 0.70f, a * 0.8f);
						}
					}
				}
			}
		}
	}

	private void drawParticles(Matrix4f m, Cfg c, Vec3d cam, float td, float fx, float fy, float fz) {
		float lim = c.range * 1.15f;
		float fadeStart = lim * 0.7f;
		for (int i = 0; i < MAX; i++) {
			P p = pool[i];
			if (!p.alive) continue;
			float x = p.px + (p.x - p.px) * td - (float) cam.x;
			float y = p.py + (p.y - p.py) * td - (float) cam.y;
			float z = p.pz + (p.z - p.pz) * td - (float) cam.z;
			if (x * fx + y * fy + z * fz < -0.5f) continue;
			float dist = (float) Math.sqrt(x * x + y * y + z * z);
			float a = clamp01((lim - dist) / (lim - fadeStart));
			if (dist < 1.6f) a *= dist / 1.6f;
			if (a <= 0.01f) continue;
			float fadeIn = Math.min(1.0f, p.age / 6.0f);
			float r = ((p.rgb >> 16) & 255) / 255.0f;
			float g = ((p.rgb >> 8) & 255) / 255.0f;
			float b = (p.rgb & 255) / 255.0f;

			switch (p.kind) {
				case SNOW -> {
					float s = p.size * 1.0f;
					bb(m, x, y, z, s, s, 0.0f, r, g, b, 0.9f * a * fadeIn);
				}
				case RAIN -> {
					float len = 0.35f + p.fall * 0.5f;
					streak(m, x, y, z, p.vx, p.vy, p.vz, len, p.size, r, g, b, 0.5f * a * fadeIn, 0.05f * a * fadeIn);
				}
				case LEAF, PETAL -> {
					float restFade = p.resting ? clamp01((p.life - p.age) / 30.0f) : 1.0f;
					float alpha = 0.95f * a * fadeIn * restFade;
					if (p.resting) {
						float ca = (float) Math.cos(p.rot);
						float sa = (float) Math.sin(p.rot);
						float hx = p.size;
						float hy = p.size * 0.6f;
						float ax = ca * hx;
						float az = sa * hx;
						float bx = -sa * hy;
						float bz = ca * hy;
						float yy = y + 0.01f;
						draw.vertex(m, x - ax - bx, yy, z - az - bz, r, g, b, alpha);
						draw.vertex(m, x + ax - bx, yy, z + az - bz, r, g, b, alpha);
						draw.vertex(m, x + ax + bx, yy, z + az + bz, r, g, b, alpha);
						draw.vertex(m, x - ax + bx, yy, z - az + bz, r, g, b, alpha);
					} else {
						float k = 0.35f + 0.65f * Math.abs((float) Math.cos(p.tumble));
						float shade = 0.82f + 0.18f * (float) Math.abs(Math.sin(p.tumble));
						bb(m, x, y, z, p.size * k, p.size * 0.75f, p.rot, r * shade, g * shade, b * shade, alpha);
					}
				}
				case MOTE -> {
					float life = p.age / (float) p.life;
					float pulse = (float) (0.5 + 0.5 * Math.sin(p.age * 0.12f + p.seed));
					float env = (float) Math.sin(Math.PI * Math.min(1.0f, life));
					float s = p.size * (0.8f + 0.5f * pulse);
					bb(m, x, y, z, s, s, 0.0f, r, g, b, 0.85f * a * env * (0.4f + 0.6f * pulse));
				}
				case RING -> {
					float t = clamp01(p.age / (float) p.life);
					float rad = 0.05f + t * p.size;
					float inner = rad * 0.78f;
					float alpha = 0.5f * (1.0f - t) * (1.0f - t) * a;
					float yy = y + 0.015f;
					for (int k = 0; k < 8; k++) {
						int k2 = (k + 1) % 8;
						draw.vertex(m, x + COS8[k] * inner, yy, z + SIN8[k] * inner, r, g, b, alpha);
						draw.vertex(m, x + COS8[k] * rad, yy, z + SIN8[k] * rad, r, g, b, alpha);
						draw.vertex(m, x + COS8[k2] * rad, yy, z + SIN8[k2] * rad, r, g, b, alpha);
						draw.vertex(m, x + COS8[k2] * inner, yy, z + SIN8[k2] * inner, r, g, b, alpha);
					}
				}
				default -> {
				}
			}
		}
	}

	/** Квадрат, повёрнутый лицом к камере; ang поворачивает его в плоскости экрана. */
	private void bb(Matrix4f m, float cx, float cy, float cz, float hx, float hy, float ang,
					float r, float g, float b, float a) {
		float ca = 1.0f;
		float sa = 0.0f;
		if (ang != 0.0f) {
			ca = (float) Math.cos(ang);
			sa = (float) Math.sin(ang);
		}
		float r1x = rx * ca + ux * sa;
		float r1y = ry * ca + uy * sa;
		float r1z = rz * ca + uz * sa;
		float r2x = -rx * sa + ux * ca;
		float r2y = -ry * sa + uy * ca;
		float r2z = -rz * sa + uz * ca;
		float ax = r1x * hx;
		float ay = r1y * hx;
		float az = r1z * hx;
		float bx = r2x * hy;
		float by = r2y * hy;
		float bz = r2z * hy;
		draw.vertex(m, cx - ax - bx, cy - ay - by, cz - az - bz, r, g, b, a);
		draw.vertex(m, cx + ax - bx, cy + ay - by, cz + az - bz, r, g, b, a);
		draw.vertex(m, cx + ax + bx, cy + ay + by, cz + az + bz, r, g, b, a);
		draw.vertex(m, cx - ax + bx, cy - ay + by, cz - az + bz, r, g, b, a);
	}

	/** Капля дождя: вытянутая полоска вдоль направления падения. */
	private void streak(Matrix4f m, float cx, float cy, float cz, float vx, float vy, float vz, float len,
						float halfW, float r, float g, float b, float aHead, float aTail) {
		float l = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
		if (l < 1.0e-4f) return;
		float dx = vx / l;
		float dy = vy / l;
		float dz = vz / l;
		float px = dy * cz - dz * cy;
		float py = dz * cx - dx * cz;
		float pz = dx * cy - dy * cx;
		float pl = (float) Math.sqrt(px * px + py * py + pz * pz);
		if (pl < 1.0e-4f) return;
		float k = halfW / pl;
		px *= k;
		py *= k;
		pz *= k;
		float tx = cx - dx * len;
		float ty = cy - dy * len;
		float tz = cz - dz * len;
		draw.vertex(m, cx + px, cy + py, cz + pz, r, g, b, aHead);
		draw.vertex(m, cx - px, cy - py, cz - pz, r, g, b, aHead);
		draw.vertex(m, tx - px, ty - py, tz - pz, r, g, b, aTail);
		draw.vertex(m, tx + px, ty + py, tz + pz, r, g, b, aTail);
	}
}
