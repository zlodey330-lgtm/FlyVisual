package com.flyvisual.record;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Автонастройка записи под твой компьютер. Не гадает по характеристикам, а проверяет на деле:
 * <ol>
 *   <li>замеряет, сколько FPS даёт игра без записи;</li>
 *   <li>строит лесенку вариантов (разрешение и FPS) от самого тяжёлого к самому лёгкому;</li>
 *   <li>делает короткие пробные записи (во временный файл) и ищет самый тяжёлый вариант, при котором нет повторов
 *       кадров (запись успевает) и FPS в самой игре почти не падает;</li>
 *   <li>берёт его, но на ступень легче, для запаса.</li>
 * </ol>
 * Всего около пяти проб по ~4 секунды (поиск делением пополам). Работает, пока идут кадры игры: вызывай onFrame() каждый кадр.
 */
public final class Calibrator {
	private static final float BASELINE_SECONDS = 2.0f;
	private static final float WARMUP_SECONDS = 1.2f;
	private static final float MEASURE_SECONDS = 3.0f;
	private static final float COOLDOWN_SECONDS = 0.8f;
	/** Выше этого потока пикселей (мегапикселей в секунду) пробовать не имеет смысла: канал данных не потянет. */
	private static final double MAX_MPS = 250.0;
	private static final int[] FPS_TIERS = {60, 48, 30, 24};
	private static final int[] HEIGHTS = {2160, 1440, 1080, 900, 720, 540};

	/** Подобранные настройки. */
	public static final class Result {
		public final int height;
		public final int fps;
		public final int quality;
		public final int codec;
		public final String summary;

		Result(int height, int fps, int quality, int codec, String summary) {
			this.height = height;
			this.fps = fps;
			this.quality = quality;
			this.codec = codec;
			this.summary = summary;
		}
	}

	private static final class Candidate {
		final int w;
		final int h;
		final int fps;
		final double mps;

		Candidate(int w, int h, int fps) {
			this.w = w;
			this.h = h;
			this.fps = fps;
			this.mps = (double) w * h * fps / 1.0e6;
		}
	}

	private enum State { IDLE, BASELINE, WARMUP, MEASURE, COOLDOWN }

	private final Recorder trial = new Recorder();
	private State state = State.IDLE;
	private long stateStart;
	private int framesCounted;
	private double baselineFps;
	private double gameplayHint;
	private final List<Candidate> ladder = new ArrayList<>();
	private int lo;
	private int hi;
	private int current;
	private int trialsDone;
	private int trialsPlanned = 1;
	private int winW;
	private int winH;
	private int codec;
	private int quality;
	private String encoderTitle = "";
	private Path tmpDir;
	private Consumer<Result> onDone;
	private String status = "";
	private float progress;

	public boolean isRunning() {
		return state != State.IDLE;
	}

	public float progress() {
		return progress;
	}

	public String status() {
		return status;
	}

	/**
	 * Запускает подбор.
	 *
	 * @param exe          путь к ffmpeg или null, если его нет
	 * @param gpu          название видеокарты (из OpenGL), чтобы выбрать её кодировщик
	 * @param gameplayFps  FPS в обычной игре (из замеров); меньше нуля, если неизвестен
	 */
	public void start(Path tmpDir, String exe, String gpu, int winW, int winH, double gameplayFps, Consumer<Result> onDone) {
		this.tmpDir = tmpDir;
		this.winW = Math.max(2, winW);
		this.winH = Math.max(2, winH & ~1);
		this.gameplayHint = gameplayFps;
		this.onDone = onDone;
		if (exe != null) {
			Encoders.Kind kind = Encoders.resolveAuto(gpu);
			if (kind == Encoders.Kind.X264) {
				codec = 4;
				quality = 1;
			} else {
				codec = kind == Encoders.Kind.NVENC ? 1 : (kind == Encoders.Kind.AMF ? 2 : 3);
				quality = 2;
			}
			encoderTitle = kind.title;
		} else {
			codec = 5;
			quality = 1;
			encoderTitle = "Motion JPEG";
		}
		state = State.BASELINE;
		stateStart = System.nanoTime();
		framesCounted = 0;
		progress = 0.0f;
		status = "Замер FPS игры…";
	}

	/** Прервать подбор. */
	public void cancel(String reason) {
		if (trial.isActive()) trial.stop();
		state = State.IDLE;
		progress = 0.0f;
		status = reason;
	}

	/** Вызывается каждый кадр игры. */
	public void onFrame() {
		if (state == State.IDLE) return;
		long now = System.nanoTime();
		float t = (now - stateStart) / 1.0e9f;
		switch (state) {
			case BASELINE -> {
				framesCounted++;
				progress = 0.08f * Math.min(1.0f, t / BASELINE_SECONDS);
				if (t >= BASELINE_SECONDS) {
					baselineFps = framesCounted / t;
					buildLadder();
					lo = 0;
					hi = ladder.size() - 1;
					trialsDone = 0;
					trialsPlanned = Math.max(1, (int) Math.ceil(Math.log(Math.max(2, ladder.size())) / Math.log(2.0)));
					nextStep();
				}
			}
			case WARMUP -> {
				trial.onFrame();
				if (t >= WARMUP_SECONDS) {
					trial.resetStats();
					state = State.MEASURE;
					stateStart = now;
					framesCounted = 0;
				}
			}
			case MEASURE -> {
				trial.onFrame();
				framesCounted++;
				float frac = Math.min(1.0f, t / MEASURE_SECONDS);
				progress = 0.08f + 0.9f * Math.min(1.0f, (trialsDone + frac) / trialsPlanned);
				if (t >= MEASURE_SECONDS) finishTrial(t, now);
			}
			case COOLDOWN -> {
				if (t >= COOLDOWN_SECONDS) nextStep();
			}
			default -> {
			}
		}
	}

	/** Лесенка вариантов от самого тяжёлого (больше пикселей в секунду) к самому лёгкому. */
	private void buildLadder() {
		double eff = baselineFps;
		if (gameplayHint > 0) eff = Math.min(eff, gameplayHint);
		List<Integer> fpsOptions = new ArrayList<>();
		for (int f : FPS_TIERS) {
			// FPS записи не выше, чем реально выдаёт игра: иначе кадры повторяются и видео дёргается
			if (f <= eff * 1.05 || f == 24) fpsOptions.add(f);
		}
		List<Integer> heights = new ArrayList<>();
		heights.add(winH);
		for (int h : HEIGHTS) {
			if (h < winH) heights.add(h);
		}
		ladder.clear();
		for (int f : fpsOptions) {
			for (int h : heights) {
				int w = ((int) Math.round(h * (double) winW / winH)) & ~1;
				Candidate c = new Candidate(w, h, f);
				if (c.mps <= MAX_MPS) ladder.add(c);
			}
		}
		if (ladder.isEmpty()) {
			ladder.add(new Candidate(winW & ~1, winH, 24));
		}
		ladder.sort((a, b) -> Double.compare(b.mps, a.mps));
	}

	private void nextStep() {
		if (lo >= hi) {
			finish();
			return;
		}
		current = (lo + hi) / 2;
		startTrial(current);
	}

	private void startTrial(int index) {
		Candidate c = ladder.get(index);
		status = String.format(Locale.ROOT, "Проба %d из ~%d: %dx%d, %d FPS…", trialsDone + 1, trialsPlanned, c.w, c.h, c.fps);
		String err = trial.startTrial(tmpDir, c.h == winH ? 0 : c.h, c.fps, quality, codec);
		if (err != null) {
			if (trialsDone == 0) {
				cancel("Не удалось запустить пробную запись: " + err);
				return;
			}
			applyTrialResult(false);
			return;
		}
		state = State.WARMUP;
		stateStart = System.nanoTime();
		framesCounted = 0;
	}

	private void finishTrial(float measureSeconds, long now) {
		trial.stop();
		double trialFps = framesCounted / measureSeconds;
		int dup = trial.dupPercent();
		boolean failed = trial.hasFailed();
		double ratio = baselineFps > 0 ? trialFps / baselineFps : 1.0;
		// Проходит, если запись успевает (почти нет повторов кадров) и игра не просела больше чем на 10%
		boolean pass = !failed && dup <= 5 && ratio >= 0.90;
		applyTrialResult(pass);
	}

	private void applyTrialResult(boolean pass) {
		trialsDone++;
		if (pass) {
			hi = current;
		} else {
			lo = current + 1;
		}
		progress = 0.08f + 0.9f * Math.min(1.0f, trialsDone / (float) trialsPlanned);
		state = State.COOLDOWN;
		stateStart = System.nanoTime();
	}

	private void finish() {
		// lo: самый тяжёлый вариант, который прошёл. Берём на ступень легче, чтобы был запас.
		int idx = Math.min(ladder.size() - 1, lo + 1);
		Candidate c = ladder.get(idx);
		String summary = String.format(Locale.ROOT, "%dx%d, %d FPS, %s", c.w, c.h, c.fps, encoderTitle);
		state = State.IDLE;
		progress = 1.0f;
		status = "Готово: " + summary;
		if (onDone != null) onDone.accept(new Result(c.h, c.fps, quality, codec, summary));
	}
}
