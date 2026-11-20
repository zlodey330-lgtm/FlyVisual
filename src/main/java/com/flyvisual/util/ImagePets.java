package com.flyvisual.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Питомцы-картинки: плоская картинка (PNG, JPG, GIF, BMP), которая всегда повёрнута к камере, парит рядом с игроком,
 * слегка дышит и наклоняется по ходу движения. Под ней мягкая тень на земле.
 * Картинки лежат в папке «flyvisual/pets» внутри папки игры. Видно только тебе.
 */
public final class ImagePets {
	private static final Logger LOGGER = LoggerFactory.getLogger("flyvisual");
	private static final int MAX_FILES = 64;
	/** Большие картинки уменьшаем до этого размера по длинной стороне: экономит память. */
	private static final int MAX_SIDE = 512;

	private static final class Entry {
		final Identifier id;
		final float aspect;

		Entry(Identifier id, float aspect) {
			this.id = id;
			this.aspect = aspect;
		}
	}

	private static List<Path> files = new ArrayList<>();
	private static final Map<Integer, Entry> TEXTURES = new HashMap<>();
	private static final Map<Integer, Boolean> FAILED = new HashMap<>();

	private ImagePets() {
	}

	/** Папка с картинками. */
	public static Path dir() {
		return FabricLoader.getInstance().getGameDir().resolve("flyvisual").resolve("pets");
	}

	public static synchronized int count() {
		return files.size();
	}

	/**
	 * Ищет картинки в папке и заполняет подписи списка выбора. Если картинок меньше, чем пунктов в списке,
	 * имена повторяются по кругу, чтобы пустых пунктов не было.
	 *
	 * @return сколько картинок найдено
	 */
	public static synchronized int rescan(String[] labels) {
		releaseTextures();
		List<Path> found = new ArrayList<>();
		try {
			Files.createDirectories(dir());
			try (Stream<Path> stream = Files.list(dir())) {
				stream.filter(Files::isRegularFile)
						.filter(ImagePets::isImage)
						.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
						.limit(MAX_FILES)
						.forEach(found::add);
			}
		} catch (IOException e) {
			LOGGER.warn("FlyVisual: не удалось прочитать папку питомцев", e);
		}
		files = found;
		FAILED.clear();
		for (int i = 0; i < labels.length; i++) {
			labels[i] = found.isEmpty() ? "— положи картинки в папку —" : shortName(found.get(i % found.size()));
		}
		return found.size();
	}

	private static boolean isImage(Path p) {
		String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
		return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif") || n.endsWith(".bmp");
	}

	private static String shortName(Path p) {
		String n = p.getFileName().toString();
		int dot = n.lastIndexOf('.');
		if (dot > 0) n = n.substring(0, dot);
		return n.length() > 22 ? n.substring(0, 22) : n;
	}

	private static void releaseTextures() {
		MinecraftClient mc = MinecraftClient.getInstance();
		for (Entry e : TEXTURES.values()) {
			try {
				mc.getTextureManager().destroyTexture(e.id);
			} catch (Throwable ignored) {
			}
		}
		TEXTURES.clear();
	}

	/** Загружает картинку в видеопамять при первом показе. */
	private static synchronized Entry entry(int index) {
		if (files.isEmpty()) return null;
		int i = Math.floorMod(index, files.size());
		Entry e = TEXTURES.get(i);
		if (e != null) return e;
		if (FAILED.containsKey(i)) return null;
		try {
			BufferedImage src = ImageIO.read(files.get(i).toFile());
			if (src == null) throw new IOException("формат не поддерживается");
			int w = src.getWidth();
			int h = src.getHeight();
			float k = Math.min(1.0f, MAX_SIDE / (float) Math.max(w, h));
			int tw = Math.max(1, Math.round(w * k));
			int th = Math.max(1, Math.round(h * k));
			BufferedImage dst = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = dst.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(src, 0, 0, tw, th, null);
			g.dispose();

			int[] argb = dst.getRGB(0, 0, tw, th, null, 0, tw);
			NativeImage image = new NativeImage(NativeImage.Format.RGBA, tw, th, false);
			for (int y = 0; y < th; y++) {
				for (int x = 0; x < tw; x++) {
					int c = argb[y * tw + x];
					int a = c >>> 24;
					int r = (c >> 16) & 0xFF;
					int gr = (c >> 8) & 0xFF;
					int b = c & 0xFF;
					image.setColor(x, y, (a << 24) | (b << 16) | (gr << 8) | r);
				}
			}
			NativeImageBackedTexture tex = new NativeImageBackedTexture(image);
			Identifier id = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("flyvisual_pet", tex);
			try {
				tex.setFilter(true, false);
			} catch (Throwable ignored) {
			}
			e = new Entry(id, tw / (float) th);
			TEXTURES.put(i, e);
			return e;
		} catch (Throwable t) {
			FAILED.put(i, Boolean.TRUE);
			LOGGER.warn("FlyVisual: не удалось загрузить картинку питомца " + files.get(i).getFileName(), t);
			return null;
		}
	}

	/**
	 * Рисует питомца-картинку.
	 *
	 * @param matrices стек матриц мира; cam позиция камеры; camRot поворот камеры (чтобы картинка смотрела на нас)
	 * @param x,y,z    центр картинки в мире; ground высота земли под ней (для тени)
	 * @param height   высота картинки в блоках
	 * @param lean     наклон в градусах (по ходу движения)
	 * @return false, если картинку не удалось нарисовать
	 */
	public static boolean draw(MatrixStack matrices, Vec3d cam, Quaternionf camRot, double x, double y, double z,
							   double ground, float height, int index, float light, float seconds, float lean) {
		Entry e = entry(index);
		if (e == null) return false;

		float breathe = 1.0f + 0.03f * (float) Math.sin(seconds * 2.2f);
		float hh = height * 0.5f * (2.0f - breathe);
		float hw = height * e.aspect * 0.5f * breathe;
		float wobble = 3.0f * (float) Math.sin(seconds * 1.4f);
		int c = Math.max(0, Math.min(255, (int) (light * 255.0f)));

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.disableCull();
		BufferBuilder buf = Tessellator.getInstance().getBuffer();

		// Мягкая тень на земле: чем выше питомец, тем она слабее
		float fadeShadow = (float) Math.max(0.0, 1.0 - (y - hh - ground) / 3.0);
		int sa = (int) (115.0f * light * fadeShadow);
		if (sa > 2) {
			matrices.push();
			matrices.translate(x - cam.x, ground + 0.02 - cam.y, z - cam.z);
			Matrix4f m = matrices.peek().getPositionMatrix();
			float r = Math.max(0.25f, hw * 0.75f);
			RenderSystem.setShader(GameRenderer::getPositionColorProgram);
			buf.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
			buf.vertex(m, 0.0f, 0.0f, 0.0f).color(0, 0, 0, sa).next();
			int n = 24;
			for (int i = 0; i <= n; i++) {
				double a = i * (Math.PI * 2.0) / n;
				buf.vertex(m, (float) Math.cos(a) * r, 0.0f, (float) Math.sin(a) * r * 0.6f).color(0, 0, 0, 0).next();
			}
			BufferRenderer.drawWithGlobalProgram(buf.end());
			matrices.pop();
		}

		// Сама картинка: всегда лицом к камере
		matrices.push();
		matrices.translate(x - cam.x, y - cam.y, z - cam.z);
		matrices.multiply(camRot);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(wobble + lean));
		Matrix4f m = matrices.peek().getPositionMatrix();
		RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
		RenderSystem.setShaderTexture(0, e.id);
		buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		buf.vertex(m, -hw, -hh, 0.0f).texture(0.0f, 1.0f).color(c, c, c, 255).next();
		buf.vertex(m, hw, -hh, 0.0f).texture(1.0f, 1.0f).color(c, c, c, 255).next();
		buf.vertex(m, hw, hh, 0.0f).texture(1.0f, 0.0f).color(c, c, c, 255).next();
		buf.vertex(m, -hw, hh, 0.0f).texture(0.0f, 0.0f).color(c, c, c, 255).next();
		BufferRenderer.drawWithGlobalProgram(buf.end());
		matrices.pop();

		RenderSystem.enableCull();
		return true;
	}
}
