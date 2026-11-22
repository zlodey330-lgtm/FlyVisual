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
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Кодирование в Motion JPEG (AVI): каждый кадр сжимается в JPEG, как делает Bandicam в режиме Motion JPEG.
 * Почти не нагружает видеокарту и легко воспроизводится, но файлы большие. Установка ничего не требует.
 * <p>
 * JPEG-сжатие идёт сразу в нескольких потоках, поэтому успевает даже на высоких разрешениях и FPS.
 * Повторные кадры (если игра выдаёт меньше кадров, чем записывается) пишутся обычными кадрами.
 * Каждые ~1.9 ГБ запись продолжается в новом файле (ограничение формата AVI).
 */
public final class AviSink implements FrameSink {
	private static final long MAX_BYTES = 1_900_000_000L;

	private static final class Buf extends ByteArrayOutputStream {
		Buf(int size) {
			super(size);
		}
	}

	/** Рабочее место одного потока: кодировщик JPEG. */
	private static final class Encoder {
		final ImageWriter writer;
		final ImageWriteParam param;
		final Buf buf = new Buf(1 << 20);

		Encoder(float quality) {
			Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpeg");
			this.writer = it.next();
			this.param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(quality);
		}
	}

	/** Кадр, ожидающий сжатия: картинка и массив пикселей под ней. */
	private static final class Slot {
		final BufferedImage img;
		final int[] px;

		Slot(int w, int h) {
			this.img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
			this.px = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
		}
	}

	private static final class Pending {
		final Future<byte[]> future;
		final int repeat;

		Pending(Future<byte[]> future, int repeat) {
			this.future = future;
			this.repeat = repeat;
		}
	}

	private final Path baseFile;
	private final int w;
	private final int h;
	private final int fps;
	private final ExecutorService pool;
	private final ThreadLocal<Encoder> encoders;
	private final ArrayBlockingQueue<Slot> freeSlots;
	private final ArrayDeque<Pending> pending = new ArrayDeque<>();
	private final int maxPending;
	private Segment segment;
	private int part;

	public AviSink(Path file, int w, int h, int fps, float quality) throws IOException {
		this.baseFile = file;
		this.w = w;
		this.h = h;
		this.fps = fps;
		if (!ImageIO.getImageWritersByFormatName("jpeg").hasNext()) {
			throw new IOException("в Java нет JPEG-кодека");
		}
		int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
		this.maxPending = threads * 2 + 1;
		this.encoders = ThreadLocal.withInitial(() -> new Encoder(quality));
		this.pool = Executors.newFixedThreadPool(threads, r -> {
			Thread t = new Thread(r, "FlyVisual-jpeg");
			t.setDaemon(true);
			return t;
		});
		this.freeSlots = new ArrayBlockingQueue<>(threads * 2);
		for (int i = 0; i < threads * 2; i++) {
			freeSlots.add(new Slot(w, h));
		}
		this.segment = new Segment(file, w, h, fps);
	}

	@Override
	public void write(byte[] data, int repeat) throws IOException {
		Slot slot;
		try {
			slot = freeSlots.take();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("запись прервана");
		}
		int count = w * h;
		int[] px = slot.px;
		for (int i = 0, p = 0; i < count; i++, p += 4) {
			px[i] = ((data[p] & 0xFF) << 16) | ((data[p + 1] & 0xFF) << 8) | (data[p + 2] & 0xFF);
		}
		Future<byte[]> f = pool.submit(() -> {
			try {
				return encode(slot.img);
			} finally {
				freeSlots.offer(slot);
			}
		});
		pending.add(new Pending(f, repeat));
		drain(maxPending);
	}

	private byte[] encode(BufferedImage img) throws IOException {
		Encoder e = encoders.get();
		e.buf.reset();
		try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(e.buf)) {
			e.writer.setOutput(ios);
			e.writer.write(null, new IIOImage(img, null, null), e.param);
		}
		return e.buf.toByteArray();
	}

	/** Записывает готовые кадры по порядку; если в очереди больше limit, ждёт самый старый. */
	private void drain(int limit) throws IOException {
		while (!pending.isEmpty() && (pending.size() > limit || pending.peekFirst().future.isDone())) {
			Pending p = pending.pollFirst();
			byte[] jpeg;
			try {
				jpeg = p.future.get();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException("запись прервана");
			} catch (ExecutionException e) {
				throw new IOException("не удалось сжать кадр: " + e.getCause(), e.getCause());
			}
			for (int r = 0; r < p.repeat; r++) {
				segment.addFrame(jpeg, jpeg.length);
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
	}

	@Override
	public void close() throws IOException {
		try {
			drain(0);
		} finally {
			pool.shutdown();
			segment.close();
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

		void addFrame(byte[] data, int len) throws IOException {
			if (frames == offsets.length) {
				offsets = java.util.Arrays.copyOf(offsets, frames * 2);
				sizes = java.util.Arrays.copyOf(sizes, frames * 2);
			}
			offsets[frames] = (int) (pos - 220);
			sizes[frames] = len;
			frames++;
			if (len > maxChunk) maxChunk = len;

			byte[] hd = new byte[8];
			hd[0] = '0';
			hd[1] = '0';
			hd[2] = 'd';
			hd[3] = 'c';
			hd[4] = (byte) len;
			hd[5] = (byte) (len >> 8);
			hd[6] = (byte) (len >> 16);
			hd[7] = (byte) (len >> 24);
			out.write(hd);
			out.write(data, 0, len);
			int pad = len & 1;
			if (pad != 0) out.write(0);
			pos += 8 + len + pad;
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
