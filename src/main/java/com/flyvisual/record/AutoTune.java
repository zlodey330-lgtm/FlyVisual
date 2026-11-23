package com.flyvisual.record;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Автонастройка записи под твой компьютер: смотрит на ядра процессора, видеокарту, наличие ffmpeg, размер окна
 * и реальный FPS в игре, а потом подбирает кодировщик, разрешение и FPS так, чтобы запись успевала и видео не лагало.
 * <p>
 * Идея: у каждого способа кодирования есть предел «мегапикселей в секунду» (ширина x высота x FPS). Выбираем самое
 * высокое разрешение и FPS, которые в него укладываются. FPS записи не выше, чем реально выдаёт игра (иначе кадры
 * повторяются и видео дёргается). Если прошлые записи были с повторами, предел снижается (штраф).
 */
public final class AutoTune {
	private static final int[] FPS_TIERS = {60, 48, 30, 24};
	private static final int[] HEIGHTS = {2160, 1440, 1080, 900, 720, 540};

	/** Выбранные настройки. codec: 0 авто, 1 NVIDIA, 2 AMD, 3 Intel, 4 процессор, 5 Motion JPEG. */
	public static final class Config {
		/** Высота кадра; 0 означает «как в окне». */
		public final int resHeight;
		public final int fps;
		public final int quality;
		public final int codec;
		public final String summary;

		Config(int resHeight, int fps, int quality, int codec, String summary) {
			this.resHeight = resHeight;
			this.fps = fps;
			this.quality = quality;
			this.codec = codec;
			this.summary = summary;
		}
	}

	private AutoTune() {
	}

	/**
	 * @param winW,winH размер окна игры в пикселях
	 * @param gameFps   FPS игры (оценка с запасом); меньше нуля, если неизвестен
	 * @param cores     число потоков процессора
	 * @param hasFfmpeg есть ли ffmpeg
	 * @param gpuName   название видеокарты (из OpenGL), может быть null
	 * @param penalty   штраф за прошлые неудачные записи: 0..5
	 */
	public static Config choose(int winW, int winH, double gameFps, int cores, boolean hasFfmpeg, String gpuName, int penalty) {
		// 1. Кодировщик и его «потолок» в мегапикселях в секунду
		int codec;
		int quality;
		double budget;
		String encoder;
		if (!hasFfmpeg) {
			int threads = Math.max(1, Math.min(4, cores / 2));
			codec = 5;
			quality = 1;
			// На замере один поток сжимает ~85 Мпикс/с; берём около половины: игра тоже загружает процессор
			budget = threads * 45.0;
			encoder = "Motion JPEG";
		} else {
			Encoders.Kind kind = Encoders.resolveAuto(gpuName);
			if (kind != Encoders.Kind.X264) {
				codec = kind == Encoders.Kind.NVENC ? 1 : (kind == Encoders.Kind.AMF ? 2 : 3);
				quality = 2;
				budget = 140.0;
				encoder = kind.title;
			} else {
				codec = 4;
				quality = 1;
				budget = Math.min(130.0, cores * 12.0);
				encoder = "процессор (x264)";
			}
		}
		budget *= Math.pow(0.75, Math.max(0, Math.min(5, penalty)));

		// 2. Допустимый FPS записи: не выше, чем реально выдаёт игра
		double g = gameFps > 0 ? gameFps : 60.0;
		List<Integer> fpsOptions = new ArrayList<>();
		for (int f : FPS_TIERS) {
			if (f <= g * 1.05 || f == 24) fpsOptions.add(f);
		}

		// 3. Возможные высоты кадра: размер окна и стандартные ниже него
		int winHeight = Math.max(2, winH & ~1);
		List<Integer> heights = new ArrayList<>();
		heights.add(winHeight);
		for (int hh : HEIGHTS) {
			if (hh < winHeight) heights.add(hh);
		}

		// 4. Самый высокий FPS, при котором найдётся разрешение от 720p, укладывающееся в потолок
		int bestH = -1;
		int bestFps = 24;
		outer:
		for (int f : fpsOptions) {
			for (int hh : heights) {
				if (hh < 720 && hh != winHeight) continue;
				double mps = pixels(winW, winH, hh) * f / 1.0e6;
				if (mps <= budget) {
					bestH = hh;
					bestFps = f;
					break outer;
				}
			}
		}
		if (bestH < 0) {
			// Совсем слабый компьютер: минимум, 540p и 24 кадра
			bestH = Math.min(winHeight, 540);
			bestFps = 24;
		}

		int w = widthFor(winW, winH, bestH);
		String summary = String.format(Locale.ROOT, "%dx%d, %d FPS, %s (процессор: %d потоков, игра: ~%s FPS%s)",
				w, bestH, bestFps, encoder, cores, gameFps > 0 ? String.valueOf((int) gameFps) : "неизвестно",
				penalty > 0 ? ", учтены прошлые лаги" : "");
		return new Config(bestH == winHeight ? 0 : bestH, bestFps, quality, codec, summary);
	}

	private static int widthFor(int winW, int winH, int h) {
		int w = (int) Math.round(h * (double) winW / Math.max(1, winH));
		return w & ~1;
	}

	private static double pixels(int winW, int winH, int h) {
		return (double) widthFor(winW, winH, h) * h;
	}
}
