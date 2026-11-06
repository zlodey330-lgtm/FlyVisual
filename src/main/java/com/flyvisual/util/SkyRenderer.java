package com.flyvisual.util;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Реалистичное объёмное небо: купол вокруг камеры с мягкими облаками (шум, самозатенение от солнца,
 * закатные и ночные оттенки), ореолом солнца и луны. Облака плывут по небу и немного смещаются при движении.
 * Рисуется поверх ванильного неба до земли, поэтому всё, что в мире, остаётся впереди.
 */
public final class SkyRenderer {
	private static final int AZ = 144;
	private static final int EL = 44;
	private static final float RADIUS = 90.0f;

	private static final float[] DX = new float[(AZ + 1) * (EL + 1)];
	private static final float[] DY = new float[DX.length];
	private static final float[] DZ = new float[DX.length];
	private static final float[] CR = new float[DX.length];
	private static final float[] CG = new float[DX.length];
	private static final float[] CB = new float[DX.length];
	private static final float[] CA = new float[DX.length];
	private static boolean ready;
	private static long lastBuild;

	private static final Draw3d DRAW = new Draw3d(true);

	private SkyRenderer() {
	}

	private static void init() {
		for (int j = 0; j <= EL; j++) {
			double t = Math.pow(j / (double) EL, 1.4);
			double el = Math.toRadians(-3.0 + 93.0 * t);
			double ce = Math.cos(el);
			double se = Math.sin(el);
			for (int i = 0; i <= AZ; i++) {
				double az = i * Math.PI * 2.0 / AZ;
				int k = j * (AZ + 1) + i;
				DX[k] = (float) (ce * Math.cos(az));
				DY[k] = (float) se;
				DZ[k] = (float) (ce * Math.sin(az));
			}
		}
		ready = true;
	}

	private static float clamp(float v, float lo, float hi) {
		return v < lo ? lo : (v > hi ? hi : v);
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static float density(float x, float z, float threshold) {
		float n = Noise.fbm(x, z, 4);
		return Noise.smoothstep(threshold, threshold + 0.2f, n);
	}

	/** amount: облачность 0..100, speed: множитель скорости. */
	public static void render(WorldRenderContext context, float amount, float speed) {
		ClientWorld world = context.world();
		MatrixStack matrices = context.matrixStack();
		if (world == null || matrices == null) return;
		if (world.getDimensionEffects().getSkyType() != DimensionEffects.SkyType.NORMAL) return;
		if (context.camera().getSubmersionType() != CameraSubmersionType.NONE) return;
		if (!ready) init();

		float tickDelta = context.tickDelta();
		double now = System.nanoTime() / 1.0e9;
		long ms = System.nanoTime() / 1_000_000L;
		if (ms - lastBuild > 80L) {
			lastBuild = ms;
			build(world, context.camera().getPos(), tickDelta, (float) now * speed, amount);
		}

		Matrix4f m = matrices.peek().getPositionMatrix();
		for (int j = 0; j < EL; j++) {
			for (int i = 0; i < AZ; i++) {
				int a = j * (AZ + 1) + i;
				int b = a + 1;
				int c = b + (AZ + 1);
				int d = a + (AZ + 1);
				if (CA[a] < 0.004f && CA[b] < 0.004f && CA[c] < 0.004f && CA[d] < 0.004f) continue;
				vert(m, a);
				vert(m, b);
				vert(m, c);
				vert(m, d);
			}
		}
		DRAW.flush();
	}

	private static void vert(Matrix4f m, int k) {
		DRAW.vertex(m, DX[k] * RADIUS, DY[k] * RADIUS, DZ[k] * RADIUS, CR[k], CG[k], CB[k], CA[k]);
	}

	private static void build(ClientWorld world, Vec3d cam, float tickDelta, float t, float amount) {
		double theta = world.getSkyAngle(tickDelta) * Math.PI * 2.0;
		float sx = (float) -Math.sin(theta);
		float sy = (float) Math.cos(theta);
		float sz = 0.0f;
		float rain = world.getRainGradient(tickDelta);
		float thunder = world.getThunderGradient(tickDelta);

		float cover = clamp(amount / 100.0f + rain * 0.35f, 0.0f, 1.0f);
		float threshold = 0.66f - 0.36f * cover;

		float day = clamp(sy * 4.0f + 0.35f, 0.0f, 1.0f);
		float warm = clamp(1.0f - Math.abs(sy) * 3.5f, 0.0f, 1.0f);
		float dark = 1.0f - 0.45f * rain - 0.2f * thunder;

		float windX = t * 0.012f;
		float windZ = t * 0.004f;
		float camX = (float) cam.x * 0.0035f;
		float camZ = (float) cam.z * 0.0035f;

		float soX = sx;
		float soZ = 0.35f;
		float sl = (float) Math.sqrt(soX * soX + soZ * soZ);
		soX = soX / sl * 0.22f;
		soZ = soZ / sl * 0.22f;

		for (int j = 0; j <= EL; j++) {
			for (int i = 0; i <= AZ; i++) {
				int k = j * (AZ + 1) + i;
				float x = DX[k];
				float y = DY[k];
				float z = DZ[k];
				float h = Math.max(0.05f, y) + 0.22f;
				float px = x / h * 1.7f + camX + windX;
				float pz = z / h * 1.7f + camZ + windZ;

				float dens = density(px, pz, threshold);
				float dens2 = dens > 0.001f ? density(px + soX, pz + soZ, threshold) : 0.0f;
				// Тонкие вторые облака выше: добавляют рваность краю
				float wisps = Noise.smoothstep(0.55f, 0.85f, Noise.fbm(px * 2.3f - windX * 1.5f, pz * 2.3f, 3)) * 0.35f * (1.0f - cover * 0.5f);
				float body = Math.max(dens, wisps);

				float horizon = Noise.smoothstep(0.0f, 0.2f, y);
				float alpha = clamp(body * 0.94f, 0.0f, 1.0f) * horizon;

				float lit = clamp(1.12f - 0.8f * dens2 + 0.25f * (1.0f - dens), 0.0f, 1.0f);
				float toSun = Math.max(0.0f, x * sx + y * sy + z * sz);
				float edge = dens * (1.0f - dens2) * (0.4f + 0.6f * toSun);

				// День: белая освещённая сторона, голубовато-серая тень. Ночь: тёмно-синие.
				float litR = lerp(0.17f, 1.0f, day);
				float litG = lerp(0.19f, 0.99f, day);
				float litB = lerp(0.30f, 0.97f, day);
				float shR = lerp(0.07f, 0.58f, day);
				float shG = lerp(0.08f, 0.64f, day);
				float shB = lerp(0.14f, 0.76f, day);
				float wk = warm * (0.35f + 0.65f * toSun);
				litR = lerp(litR, 1.0f, wk);
				litG = lerp(litG, 0.60f, wk);
				litB = lerp(litB, 0.34f, wk);
				shR = lerp(shR, 0.55f, wk * 0.6f);
				shG = lerp(shG, 0.36f, wk * 0.6f);
				shB = lerp(shB, 0.40f, wk * 0.6f);

				float r = lerp(shR, litR, lit) * dark;
				float g = lerp(shG, litG, lit) * dark;
				float b = lerp(shB, litB, lit) * dark;
				// Светлая кромка (серебро) со стороны солнца
				r += edge * 0.25f * day;
				g += edge * 0.22f * day;
				b += edge * 0.18f * day;

				// Ореол солнца и луны
				float gs = Math.max(0.0f, x * sx + y * sy + z * sz);
				float sunK = clamp(sy * 3.0f + 0.7f, 0.0f, 1.0f) * (1.0f - 0.7f * rain);
				float glowA = ((float) Math.pow(gs, 110.0) * 0.6f + (float) Math.pow(gs, 14.0) * 0.2f + (float) Math.pow(gs, 3.0) * 0.05f) * sunK;
				float gr = lerp(1.0f, 1.0f, warm);
				float gg = lerp(0.93f, 0.55f, warm);
				float gb = lerp(0.78f, 0.28f, warm);
				float gm = Math.max(0.0f, -(x * sx + y * sy + z * sz));
				float moonK = clamp(-sy * 3.0f + 0.3f, 0.0f, 1.0f) * (1.0f - 0.7f * rain);
				float moonA = ((float) Math.pow(gm, 160.0) * 0.4f + (float) Math.pow(gm, 18.0) * 0.07f) * moonK;
				float gA = clamp(glowA + moonA, 0.0f, 0.9f) * horizon;
				if (gA > 0.0f) {
					float wsun = glowA / Math.max(0.0001f, glowA + moonA);
					float mr = lerp(0.72f, gr, wsun);
					float mg = lerp(0.80f, gg, wsun);
					float mb = lerp(1.0f, gb, wsun);
					float total = alpha + gA * (1.0f - alpha);
					r = (r * alpha + mr * gA * (1.0f - alpha)) / Math.max(0.0001f, total);
					g = (g * alpha + mg * gA * (1.0f - alpha)) / Math.max(0.0001f, total);
					b = (b * alpha + mb * gA * (1.0f - alpha)) / Math.max(0.0001f, total);
					alpha = total;
				}
				CR[k] = clamp(r, 0.0f, 1.0f);
				CG[k] = clamp(g, 0.0f, 1.0f);
				CB[k] = clamp(b, 0.0f, 1.0f);
				CA[k] = alpha;
			}
		}
	}
}
