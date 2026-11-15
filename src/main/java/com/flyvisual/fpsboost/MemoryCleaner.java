package com.flyvisual.fpsboost;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Очистка памяти: чистит свои кэши и частицы, затем два полных прохода сборщика мусора.
 * Освобождённое считаем по данным самого сборщика (память сразу после очистки), а не по текущей
 * загрузке, которая тут же снова растёт.
 */
public final class MemoryCleaner {
	private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

	private MemoryCleaner() {
	}

	public static long usedMb() {
		Runtime rt = Runtime.getRuntime();
		return (rt.totalMemory() - rt.freeMemory()) / 1048576L;
	}

	public static long maxMb() {
		return Runtime.getRuntime().maxMemory() / 1048576L;
	}

	/** Сумма по пулам кучи: current = текущее использование, иначе использование после последней сборки. */
	private static long heapMb(boolean afterCollection) {
		long sum = 0;
		boolean any = false;
		try {
			for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
				if (pool.getType() != MemoryType.HEAP) continue;
				MemoryUsage u = afterCollection ? pool.getCollectionUsage() : pool.getUsage();
				if (u == null) continue;
				sum += u.getUsed();
				any = true;
			}
		} catch (Throwable ignored) {
		}
		return any ? sum / 1048576L : -1L;
	}

	public static void run() {
		if (!RUNNING.compareAndSet(false, true)) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		try {
			// То, что можно безопасно сбросить в игре
			if (mc.particleManager != null) mc.particleManager.clearParticles();
			com.flyvisual.module.ModuleManager.clearCaches();
		} catch (Throwable ignored) {
		}
		long before = Math.max(heapMb(false), usedMb());
		Thread thread = new Thread(() -> {
			long after = usedMb();
			try {
				System.gc();
				Thread.sleep(120);
				System.gc();
				Thread.sleep(80);
				long afterGc = heapMb(true);
				after = afterGc >= 0 ? afterGc : usedMb();
			} catch (Throwable ignored) {
			}
			final long used = after;
			final long freed = Math.max(0L, before - after);
			mc.execute(() -> {
				if (mc.player != null) {
					mc.player.sendMessage(Text.literal("Память очищена: освобождено ~" + freed + " МБ (занято " + used + " МБ)"), true);
				}
				RUNNING.set(false);
			});
		}, "fpsboost-memory-clean");
		thread.setDaemon(true);
		thread.start();
	}
}
