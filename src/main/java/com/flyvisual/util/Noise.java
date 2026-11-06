package com.flyvisual.util;

/** Лёгкий шум без зависимостей: хэш, значение-шум и фрактальный шум (используется для шерсти, чешуи и неба). */
public final class Noise {
	private Noise() {
	}

	public static float hash(int x, int y, int z) {
		int h = x * 374761393 + y * 668265263 + z * 1274126177;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / 16777215.0f;
	}

	public static float hash(int x, int y) {
		return hash(x, y, 7919);
	}

	private static float fade(float t) {
		return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	/** Плавный шум 0..1. */
	public static float noise(float x, float y, float z) {
		int xi = (int) Math.floor(x);
		int yi = (int) Math.floor(y);
		int zi = (int) Math.floor(z);
		float xf = fade(x - xi);
		float yf = fade(y - yi);
		float zf = fade(z - zi);

		float c000 = hash(xi, yi, zi);
		float c100 = hash(xi + 1, yi, zi);
		float c010 = hash(xi, yi + 1, zi);
		float c110 = hash(xi + 1, yi + 1, zi);
		float c001 = hash(xi, yi, zi + 1);
		float c101 = hash(xi + 1, yi, zi + 1);
		float c011 = hash(xi, yi + 1, zi + 1);
		float c111 = hash(xi + 1, yi + 1, zi + 1);

		float x00 = lerp(c000, c100, xf);
		float x10 = lerp(c010, c110, xf);
		float x01 = lerp(c001, c101, xf);
		float x11 = lerp(c011, c111, xf);
		return lerp(lerp(x00, x10, yf), lerp(x01, x11, yf), zf);
	}

	public static float noise(float x, float y) {
		return noise(x, y, 3.17f);
	}

	/** Фрактальный шум 0..1 из нескольких слоёв. */
	public static float fbm(float x, float y, float z, int octaves) {
		float sum = 0.0f;
		float amp = 0.5f;
		float norm = 0.0f;
		for (int i = 0; i < octaves; i++) {
			sum += amp * noise(x, y, z);
			norm += amp;
			x = x * 2.03f + 17.1f;
			y = y * 2.03f + 9.7f;
			z = z * 2.03f + 3.3f;
			amp *= 0.5f;
		}
		return sum / norm;
	}

	public static float fbm(float x, float y, int octaves) {
		return fbm(x, y, 1.7f, octaves);
	}

	public static float clamp01(float v) {
		return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
	}

	public static float smoothstep(float a, float b, float v) {
		float t = clamp01((v - a) / (b - a));
		return t * t * (3.0f - 2.0f * t);
	}

	public static float mix(float a, float b, float t) {
		return a + (b - a) * t;
	}
}
