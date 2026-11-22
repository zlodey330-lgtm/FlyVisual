package com.flyvisual.record;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Кодировщики видео для ffmpeg: процессор (x264) и видеокарты (NVIDIA NVENC, AMD AMF, Intel QSV).
 * Кодирование на видеокарте почти не нагружает процессор, поэтому игра не тормозит и запись не рвётся.
 * Какие видеокарты подходят, проверяется один раз в фоне небольшим пробным запуском ffmpeg.
 */
public final class Encoders {
	public enum Kind {
		NVENC("видеокарта NVIDIA", 2),
		AMF("видеокарта AMD", 2),
		QSV("видеокарта Intel", 2),
		X264("процессор", 1);

		public final String title;
		final int variants;

		Kind(String title, int variants) {
			this.title = title;
			this.variants = variants;
		}
	}

	private static final int[] CRF = {28, 23, 19, 16};
	private static final String[] X264_PRESET = {"ultrafast", "veryfast", "veryfast", "faster"};
	private static final int[] GPU_QP = {32, 27, 23, 19};

	private static final Map<Kind, Integer> WORKING = new ConcurrentHashMap<>();
	private static volatile boolean detected;
	private static volatile boolean detecting;

	private Encoders() {
	}

	public static boolean isDetected() {
		return detected;
	}

	/** Рабочий вариант параметров для этого кодировщика (или -1, если он не подходит). */
	private static int working(Kind kind) {
		Integer v = WORKING.get(kind);
		return v == null ? -1 : v;
	}

	/** Выбор кодировщика: 0 авто, 1 NVIDIA, 2 AMD, 3 Intel, 4 процессор. Если выбранного нет, берём процессор. */
	public static Kind resolve(int choice) {
		switch (choice) {
			case 1:
				return working(Kind.NVENC) >= 0 ? Kind.NVENC : Kind.X264;
			case 2:
				return working(Kind.AMF) >= 0 ? Kind.AMF : Kind.X264;
			case 3:
				return working(Kind.QSV) >= 0 ? Kind.QSV : Kind.X264;
			case 4:
				return Kind.X264;
			default:
				if (working(Kind.NVENC) >= 0) return Kind.NVENC;
				if (working(Kind.AMF) >= 0) return Kind.AMF;
				if (working(Kind.QSV) >= 0) return Kind.QSV;
				return Kind.X264;
		}
	}

	/** Параметры видеокодировщика для ffmpeg. quality: 0..3. */
	public static List<String> args(Kind kind, int quality) {
		return args(kind, Math.max(0, working(kind)), quality);
	}

	private static List<String> args(Kind kind, int variant, int quality) {
		int q = Math.max(0, Math.min(3, quality));
		List<String> a = new ArrayList<>();
		switch (kind) {
			case NVENC -> {
				a.add("-c:v");
				a.add("h264_nvenc");
				a.add("-preset");
				a.add(variant == 0 ? "p4" : "fast");
				a.add("-rc");
				a.add("constqp");
				a.add("-qp");
				a.add(String.valueOf(GPU_QP[q]));
				a.add("-pix_fmt");
				a.add("yuv420p");
			}
			case AMF -> {
				a.add("-c:v");
				a.add("h264_amf");
				if (variant == 0) {
					a.add("-usage");
					a.add("lowlatency");
				}
				a.add("-quality");
				a.add("speed");
				a.add("-rc");
				a.add("cqp");
				a.add("-qp_i");
				a.add(String.valueOf(GPU_QP[q]));
				a.add("-qp_p");
				a.add(String.valueOf(GPU_QP[q]));
				a.add("-pix_fmt");
				a.add("yuv420p");
			}
			case QSV -> {
				a.add("-c:v");
				a.add("h264_qsv");
				if (variant == 0) {
					a.add("-preset");
					a.add("veryfast");
				}
				a.add("-global_quality");
				a.add(String.valueOf(GPU_QP[q]));
				a.add("-pix_fmt");
				a.add("nv12");
			}
			default -> {
				a.add("-c:v");
				a.add("libx264");
				a.add("-preset");
				a.add(X264_PRESET[q]);
				a.add("-crf");
				a.add(String.valueOf(CRF[q]));
				a.add("-pix_fmt");
				a.add("yuv420p");
			}
		}
		return a;
	}

	/** Запускает проверку видеокарт в фоне (один раз за запуск игры). */
	public static void detectAsync(java.nio.file.Path gameDir) {
		if (detected || detecting) return;
		Thread t = new Thread(() -> {
			String exe = FfmpegSink.find(gameDir, null);
			if (exe != null) detect(exe);
		}, "FlyVisual-encoder-check");
		t.setDaemon(true);
		t.start();
	}

	/** Проверяет, какие видеокарты умеют кодировать (пробный запуск ffmpeg на пару кадров). */
	public static synchronized void detect(String exe) {
		if (detected || detecting) return;
		detecting = true;
		try {
			for (Kind kind : new Kind[]{Kind.NVENC, Kind.AMF, Kind.QSV}) {
				for (int v = 0; v < kind.variants; v++) {
					if (test(exe, args(kind, v, 2))) {
						WORKING.put(kind, v);
						break;
					}
				}
			}
		} finally {
			detected = true;
			detecting = false;
		}
	}

	private static boolean test(String exe, List<String> videoArgs) {
		try {
			List<String> cmd = new ArrayList<>();
			cmd.add(exe);
			cmd.add("-hide_banner");
			cmd.add("-loglevel");
			cmd.add("error");
			cmd.add("-f");
			cmd.add("lavfi");
			cmd.add("-i");
			cmd.add("color=c=black:s=256x256:r=30");
			cmd.add("-frames:v");
			cmd.add("5");
			cmd.addAll(videoArgs);
			cmd.add("-f");
			cmd.add("null");
			cmd.add("-");
			Process p = new ProcessBuilder(cmd)
					.redirectErrorStream(true)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.start();
			if (!p.waitFor(20, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				return false;
			}
			return p.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}
}
