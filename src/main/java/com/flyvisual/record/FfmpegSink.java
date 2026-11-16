package com.flyvisual.record;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Кодирование через ffmpeg (H.264, mp4): маленький размер файла при хорошем качестве.
 * Кадры отправляются в ffmpeg по трубе, поэтому на диск лишнего не пишется.
 */
public final class FfmpegSink implements FrameSink {
	private final Process process;
	private final OutputStream out;
	private final int frameBytes;
	private final Path file;

	public FfmpegSink(String exe, int w, int h, int fps, int crf, String preset, Path file) throws IOException {
		this.frameBytes = w * h * 4;
		this.file = file;
		List<String> cmd = new ArrayList<>();
		cmd.add(exe);
		cmd.add("-y");
		cmd.add("-loglevel");
		cmd.add("error");
		cmd.add("-f");
		cmd.add("rawvideo");
		cmd.add("-pix_fmt");
		cmd.add("rgba");
		cmd.add("-s");
		cmd.add(w + "x" + h);
		cmd.add("-r");
		cmd.add(String.valueOf(fps));
		cmd.add("-i");
		cmd.add("-");
		cmd.add("-an");
		cmd.add("-c:v");
		cmd.add("libx264");
		cmd.add("-preset");
		cmd.add(preset);
		cmd.add("-crf");
		cmd.add(String.valueOf(crf));
		cmd.add("-pix_fmt");
		cmd.add("yuv420p");
		cmd.add("-movflags");
		cmd.add("+faststart");
		cmd.add(file.toString());

		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.redirectErrorStream(true);
		pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
		this.process = pb.start();
		this.out = new BufferedOutputStream(process.getOutputStream(), 1 << 20);
	}

	@Override
	public void write(byte[] data, int repeat) throws IOException {
		for (int i = 0; i < repeat; i++) {
			out.write(data, 0, frameBytes);
		}
	}

	@Override
	public void close() throws IOException {
		try {
			out.close();
		} finally {
			try {
				if (!process.waitFor(90, TimeUnit.SECONDS)) {
					process.destroyForcibly();
				}
			} catch (InterruptedException e) {
				process.destroyForcibly();
				Thread.currentThread().interrupt();
			}
		}
	}

	@Override
	public String fileName() {
		return file.getFileName().toString();
	}

	@Override
	public String filePath() {
		return file.toString();
	}

	// ------------------------------------------------------------------ поиск ffmpeg

	private static String cached;
	private static boolean searched;

	/** Ищет рабочий ffmpeg: в PATH, в папке записей и рядом с игрой. Если нет, вернёт null. */
	public static synchronized String find(Path gameDir, Path recordFolder) {
		if (searched && cached != null) return cached;
		List<String> candidates = new ArrayList<>();
		candidates.add("ffmpeg");
		Path[] dirs = {
				recordFolder,
				gameDir,
				gameDir == null ? null : gameDir.resolve("ffmpeg"),
				gameDir == null ? null : gameDir.resolve("ffmpeg").resolve("bin")
		};
		for (Path dir : dirs) {
			if (dir == null) continue;
			candidates.add(dir.resolve("ffmpeg.exe").toString());
			candidates.add(dir.resolve("ffmpeg").toString());
		}
		for (String c : candidates) {
			if (works(c)) {
				cached = c;
				searched = true;
				return c;
			}
		}
		searched = true;
		cached = null;
		return null;
	}

	private static boolean works(String exe) {
		try {
			if (!exe.equals("ffmpeg") && !Files.isRegularFile(Path.of(exe))) return false;
			Process p = new ProcessBuilder(exe, "-version")
					.redirectErrorStream(true)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.start();
			if (!p.waitFor(5, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				return false;
			}
			return p.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}
}
