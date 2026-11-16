package com.flyvisual.record;

import java.io.IOException;

/** Приёмник кадров: кодирует их в видеофайл. Работает в отдельном потоке записи. */
public interface FrameSink {
	/** data: кадр RGBA сверху вниз; repeat: сколько раз подряд показать этот кадр (для ровной скорости). */
	void write(byte[] data, int repeat) throws IOException;

	/** Дописывает и закрывает файл. */
	void close() throws IOException;

	/** Имя итогового файла (для уведомления). */
	String fileName();

	/** Полный путь итогового файла. */
	String filePath();
}
