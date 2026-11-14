package com.flyvisual.fpsboost;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.concurrent.atomic.AtomicBoolean;

/** Очистка памяти: просим сборщик мусора и показываем, сколько освободилось. */
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

	public static void run() {
		if (!RUNNING.compareAndSet(false, true)) return;
		long before = usedMb();
		Thread thread = new Thread(() -> {
			try {
				System.gc();
			} finally {
				long after = usedMb();
				long freed = Math.max(0L, before - after);
				MinecraftClient mc = MinecraftClient.getInstance();
				mc.execute(() -> {
					if (mc.player != null) {
						mc.player.sendMessage(Text.literal("Память очищена: освобождено ~" + freed + " МБ (занято " + after + " МБ)"), true);
					}
					RUNNING.set(false);
				});
			}
		}, "fpsboost-memory-clean");
		thread.setDaemon(true);
		thread.start();
	}
}
