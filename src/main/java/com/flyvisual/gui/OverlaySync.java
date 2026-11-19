package com.flyvisual.gui;

/**
 * Связь между заставкой загрузки и главным меню: заставка записывает момент вспышки,
 * а меню в этот момент начинает плавно проявляться.
 */
public final class OverlaySync {
	/** Время (System.nanoTime) последней вспышки заставки; -1, если ещё не было. */
	public static volatile long revealNanos = -1L;

	private OverlaySync() {
	}
}
