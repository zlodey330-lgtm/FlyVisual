package com.flyvisual.util;

/**
 * Функции расстояния до формы (SDF): отрицательное значение = внутри.
 * Из них собираются тела животных: эллипсоиды, капсулы, плавные объединения.
 */
@FunctionalInterface
public interface Sdf {
	float d(float x, float y, float z);

	/** Цвет точки модели в формате 0xRRGGBB. */
	@FunctionalInterface
	interface Paint {
		int rgb(float x, float y, float z);
	}

	static Sdf ellipsoid(float cx, float cy, float cz, float rx, float ry, float rz) {
		return (x, y, z) -> {
			float px = (x - cx) / rx;
			float py = (y - cy) / ry;
			float pz = (z - cz) / rz;
			float k0 = (float) Math.sqrt(px * px + py * py + pz * pz);
			float qx = px / rx;
			float qy = py / ry;
			float qz = pz / rz;
			float k1 = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
			if (k1 < 1.0e-6f) return -Math.min(rx, Math.min(ry, rz));
			return k0 * (k0 - 1.0f) / k1;
		};
	}

	static Sdf sphere(float cx, float cy, float cz, float r) {
		return (x, y, z) -> {
			float dx = x - cx;
			float dy = y - cy;
			float dz = z - cz;
			return (float) Math.sqrt(dx * dx + dy * dy + dz * dz) - r;
		};
	}

	/** Капсула от a до b с радиусами ra и rb (конус со скруглёнными концами). */
	static Sdf capsule(float ax, float ay, float az, float bx, float by, float bz, float ra, float rb) {
		float bax = bx - ax;
		float bay = by - ay;
		float baz = bz - az;
		float len2 = bax * bax + bay * bay + baz * baz;
		return (x, y, z) -> {
			float pax = x - ax;
			float pay = y - ay;
			float paz = z - az;
			float h = (pax * bax + pay * bay + paz * baz) / len2;
			h = h < 0.0f ? 0.0f : (h > 1.0f ? 1.0f : h);
			float dx = pax - bax * h;
			float dy = pay - bay * h;
			float dz = paz - baz * h;
			return (float) Math.sqrt(dx * dx + dy * dy + dz * dz) - (ra + (rb - ra) * h);
		};
	}

	static Sdf box(float cx, float cy, float cz, float hx, float hy, float hz) {
		return (x, y, z) -> {
			float qx = Math.abs(x - cx) - hx;
			float qy = Math.abs(y - cy) - hy;
			float qz = Math.abs(z - cz) - hz;
			float ox = Math.max(qx, 0.0f);
			float oy = Math.max(qy, 0.0f);
			float oz = Math.max(qz, 0.0f);
			return (float) Math.sqrt(ox * ox + oy * oy + oz * oz) + Math.min(Math.max(qx, Math.max(qy, qz)), 0.0f);
		};
	}

	static Sdf union(Sdf... parts) {
		return (x, y, z) -> {
			float d = Float.MAX_VALUE;
			for (Sdf p : parts) {
				d = Math.min(d, p.d(x, y, z));
			}
			return d;
		};
	}

	/** Плавное объединение: формы «сливаются» без острого шва (k = ширина сглаживания). */
	static Sdf smooth(float k, Sdf... parts) {
		return (x, y, z) -> {
			float d = parts[0].d(x, y, z);
			for (int i = 1; i < parts.length; i++) {
				float b = parts[i].d(x, y, z);
				float h = Math.max(k - Math.abs(d - b), 0.0f) / k;
				d = Math.min(d, b) - h * h * k * 0.25f;
			}
			return d;
		};
	}

	static Sdf subtract(Sdf a, Sdf b) {
		return (x, y, z) -> Math.max(a.d(x, y, z), -b.d(x, y, z));
	}

	static Sdf intersect(Sdf a, Sdf b) {
		return (x, y, z) -> Math.max(a.d(x, y, z), b.d(x, y, z));
	}

	/** Зеркало по X: форма описывается для x >= 0, а работает с обеих сторон. */
	static Sdf mirrorX(Sdf s) {
		return (x, y, z) -> s.d(Math.abs(x), y, z);
	}

	/** Сплюснуть форму по оси Z вокруг cz (factor > 1 = тоньше). Нужно для ушей и крыльев. */
	static Sdf flatZ(Sdf s, float cz, float factor) {
		return (x, y, z) -> s.d(x, y, cz + (z - cz) * factor) / factor;
	}

	static Sdf flatY(Sdf s, float cy, float factor) {
		return (x, y, z) -> s.d(x, cy + (y - cy) * factor, z) / factor;
	}

	static Sdf flatX(Sdf s, float cx, float factor) {
		return (x, y, z) -> s.d(cx + (x - cx) * factor, y, z) / factor;
	}

	static Sdf offset(Sdf s, float dx, float dy, float dz) {
		return (x, y, z) -> s.d(x - dx, y - dy, z - dz);
	}
}
