package com.flyvisual.record;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Запасной кодировщик без установки чего-либо: AVI с кадрами в формате MJPEG (JPEG на каждый кадр).
 * Файлы получаются больше, чем у ffmpeg, но открываются в VLC, DaVinci Resolve, Premiere и других программах.
 * Каждые ~1.9 ГБ запись продолжается в новом файле (ограничение формата AVI).
 */
public final class AviSink implements FrameSink {
	private static final long MAX_BYTES = 1_900_000_000L;

	private static final class Buf extends ByteArrayOutputStream {
		Buf(int size) {
			super(size);
		}

		byte[] raw() {
			return buf;
		}

		int len() {
			return count;
		}
	}

	private final Path baseFile;
	private final int w;
	private final int h;
	private final int fps;
	private final float quality;
	private final ImageWriter writer;
	private final ImageWriteParam param;
	private final BufferedImage image;
	private final int[] pixels;
	private final Buf jpeg = new Buf(1 << 20);
	private Segment segment;
	private int part;

	public AviSink(Path file, int w, int h, int fps, float quality) throws IOException {
		this.baseFile = file;
		this.w = w;
		this.h = h;
		this.fps = fps;
		this.quality = quality;
		Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpeg");
		if (!it.hasNext()) throw new IOException("в Java нет JPEG-кодека");
		this.writer = it.next();
		this.param = writer.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(quality);
		this.image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		this.pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		this.segment = new Segment(file, w, h, fps);
	}

	@Override
	public void write(byte[] data, int repeat) throws IOException {
		int count = w * h;
		for (int i = 0, p = 0; i < count; i++, p += 4) {
			pixels[i] = ((data[p] & 0xFF) << 16) | ((data[p + 1] & 0xFF) << 8) | (data[p + 2] & 0xFF);
		}
		jpeg.reset();
		try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(jpeg)) {
			writer.setOutput(ios);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		segment.addFrame(jpeg.raw(), jpeg.len());
		for (int r = 1; r < repeat; r++) {
			segment.addRepeat();
		}
		if (segment.pos > MAX_BYTES) {
			segment.close();
			part++;
			String name = baseFile.getFileName().toString();
			int dot = name.lastIndexOf('.');
			String next = name.substring(0, dot) + "_part" + (part + 1) + name.substring(dot);
			segment = new Segment(baseFile.resolveSibling(next), w, h, fps);
		}
	}

	@Override
	public void close() throws IOException {
		try {
			segment.close();
		} finally {
			writer.dispose();
		}
	}

	@Override
	public String fileName() {
		return baseFile.getFileName().toString();
	}

	@Override
	public String filePath() {
		return baseFile.toString();
	}

	// ------------------------------------------------------------------ один файл AVI

	private static final class Segment {
		private final Path file;
		private final int w;
		private final int h;
		private final int fps;
		private final OutputStream out;
		long pos;
		private int frames;
		private int maxChunk;
		private int[] offsets = new int[4096];
		private int[] sizes = new int[4096];

		Segment(Path file, int w, int h, int fps) throws IOException {
			this.file = file;
			this.w = w;
			this.h = h;
			this.fps = fps;
			this.out = new BufferedOutputStream(Files.newOutputStream(file), 1 << 20);
			byte[] header = header();
			out.write(header);
			pos = header.length;
		}

		private static byte[] ascii(String s) {
			return s.getBytes(StandardCharsets.US_ASCII);
		}

		private byte[] header() {
			ByteBuffer b = ByteBuffer.allocate(224).order(ByteOrder.LITTLE_ENDIAN);
			int imageSize = w * h * 3;
			b.put(ascii("RIFF")).putInt(0).put(ascii("AVI "));
			b.put(ascii("LIST")).putInt(192).put(ascii("hdrl"));
			b.put(ascii("avih")).putInt(56);
			b.putInt(1_000_000 / fps);   // микросекунд на кадр
			b.putInt(0);                 // байт в секунду (заполним в конце)
			b.putInt(0);
			b.putInt(0x10);              // есть индекс
			b.putInt(0);                 // всего кадров (заполним в конце)
			b.putInt(0);
			b.putInt(1);                 // потоков
			b.putInt(imageSize);
			b.putInt(w).putInt(h);
			b.put(new byte[16]);
			b.put(ascii("LIST")).putInt(116).put(ascii("strl"));
			b.put(ascii("strh")).putInt(56);
			b.put(ascii("vids")).put(ascii("MJPG"));
			b.putInt(0).putInt(0).putInt(0);
			b.putInt(1).putInt(fps);     // масштаб и частота кадров
			b.putInt(0);
			b.putInt(0);                 // длина в кадрах (заполним в конце)
			b.putInt(imageSize);
			b.putInt(-1);
			b.putInt(0);
			b.putShort((short) 0).putShort((short) 0).putShort((short) w).putShort((short) h);
			b.put(ascii("strf")).putInt(40);
			b.putInt(40).putInt(w).putInt(h);
			b.putShort((short) 1).putShort((short) 24);
			b.put(ascii("MJPG"));
			b.putInt(imageSize);
			b.putInt(0).putInt(0).putInt(0).putInt(0);
			b.put(ascii("LIST")).putInt(0).put(ascii("movi"));
			return b.array();
		}

		private void remember(int size) {
			if (frames == offsets.length) {
				offsets = java.util.Arrays.copyOf(offsets, frames * 2);
				sizes = java.util.Arrays.copyOf(sizes, frames * 2);
			}
			offsets[frames] = (int) (pos - 220);
			sizes[frames] = size;
			frames++;
			if (size > maxChunk) maxChunk = size;
		}

		private void chunkHeader(int size) throws IOException {
			byte[] hd = new byte[8];
			hd[0] = '0';
			hd[1] = '0';
			hd[2] = 'd';
			hd[3] = 'c';
			hd[4] = (byte) size;
			hd[5] = (byte) (size >> 8);
			hd[6] = (byte) (size >> 16);
			hd[7] = (byte) (size >> 24);
			out.write(hd);
		}

		void addFrame(byte[] data, int len) throws IOException {
			remember(len);
			chunkHeader(len);
			out.write(data, 0, len);
			int pad = len & 1;
			if (pad != 0) out.write(0);
			pos += 8 + len + pad;
		}

		/** Пустой кадр: проигрыватель просто оставляет предыдущую картинку. */
		void addRepeat() throws IOException {
			remember(0);
			chunkHeader(0);
			pos += 8;
		}

		void close() throws IOException {
			out.flush();
			out.close();
			try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
				raf.seek(pos);
				ByteBuffer idx = ByteBuffer.allocate(8 + frames * 16).order(ByteOrder.LITTLE_ENDIAN);
				idx.put(ascii("idx1")).putInt(frames * 16);
				for (int i = 0; i < frames; i++) {
					idx.put(ascii("00dc")).putInt(0x10).putInt(offsets[i]).putInt(sizes[i]);
				}
				raf.write(idx.array());
				long total = pos + 8 + frames * 16L;
				patch(raf, 4, (int) (total - 8));
				patch(raf, 216, (int) (pos - 220));
				patch(raf, 36, maxChunk * fps);
				patch(raf, 48, frames);
				patch(raf, 60, maxChunk);
				patch(raf, 140, frames);
				patch(raf, 144, maxChunk);
			}
		}

		private static void patch(RandomAccessFile raf, long at, int value) throws IOException {
			raf.seek(at);
			raf.write(new byte[]{(byte) value, (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24)});
		}
	}
}
