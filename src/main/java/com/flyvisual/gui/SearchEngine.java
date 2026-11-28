package com.flyvisual.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Движок умного поиска по клиенту (без зависимостей от Minecraft).
 * Понимает: начало слова и часть названия, опечатки (в том числе перестановку соседних букв),
 * неправильную раскладку (Yt,j = Небо), русские и английские названия, несколько слов в запросе,
 * поиск по описанию и по «хозяину» настройки (название модуля).
 */
final class SearchEngine {
	static final int KIND_MODULE = 0;
	static final int KIND_SETTING = 1;
	static final int KIND_ACTION = 2;

	/** Одна запись индекса: модуль, настройка или действие. */
	static final class Item {
		final int kind;
		final int a;
		final int b;
		final String title;
		final String sub;
		final String key;
		private final String normTitle;
		private final String[] primary;
		private final String[] context;
		private final String[] desc;
		private final String joined;

		Item(int kind, int a, int b, String title, String sub, String[] names, String[] context, String desc) {
			this.kind = kind;
			this.a = a;
			this.b = b;
			this.title = title;
			this.sub = sub;
			this.key = kind + ":" + a + ":" + b;
			this.normTitle = norm(title);
			StringBuilder all = new StringBuilder();
			StringBuilder joinedSb = new StringBuilder();
			for (String n : names) {
				all.append(norm(n)).append(' ');
				joinedSb.append(norm(n).replace(" ", "")).append(' ');
			}
			this.primary = words(all.toString());
			this.joined = joinedSb.toString();
			StringBuilder ctx = new StringBuilder();
			for (String c : context) ctx.append(norm(c)).append(' ');
			this.context = words(ctx.toString());
			this.desc = desc == null ? new String[0] : words(norm(desc));
		}
	}

	static final class Hit {
		final Item item;
		final float score;
		final int hlStart;
		final int hlLen;

		Hit(Item item, float score, int hlStart, int hlLen) {
			this.item = item;
			this.score = score;
			this.hlStart = hlStart;
			this.hlLen = hlLen;
		}
	}

	private SearchEngine() {
	}

	// ---------- Раскладки ----------

	private static final String EN = "`qwertyuiop[]asdfghjkl;'zxcvbnm,./";
	private static final String RU = "ёйцукенгшщзхъфывапролджэячсмитьбю.";

	private static String convert(String s, String from, String to) {
		StringBuilder sb = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			int p = from.indexOf(c);
			sb.append(p >= 0 ? to.charAt(p) : c);
		}
		return sb.toString();
	}

	private static boolean hasAny(String s, String set) {
		for (int i = 0; i < s.length(); i++) {
			if (set.indexOf(s.charAt(i)) >= 0) return true;
		}
		return false;
	}

	/** Что было бы набрано в другой раскладке (или null, если переводить нечего). */
	static String[] layoutVariants(String raw) {
		String low = raw.toLowerCase(Locale.ROOT);
		List<String> out = new ArrayList<>(2);
		if (hasAny(low, EN)) {
			String ru = convert(low, EN, RU);
			if (!ru.equals(low)) out.add(ru);
		}
		if (hasAny(low, RU)) {
			String en = convert(low, RU, EN);
			if (!en.equals(low)) out.add(en);
		}
		return out.toArray(new String[0]);
	}

	/** Транслит: zvuk = звук, nebo = небо. null, если в запросе есть не только латиница. */
	static String translit(String raw) {
		String low = raw.toLowerCase(Locale.ROOT);
		boolean latin = false;
		for (int i = 0; i < low.length(); i++) {
			char c = low.charAt(i);
			if (c >= 'a' && c <= 'z') latin = true;
			else if (c != ' ' && c != '-' && !Character.isDigit(c)) return null;
		}
		if (!latin) return null;
		String[][] di = {{"sch", "щ"}, {"sh", "ш"}, {"ch", "ч"}, {"zh", "ж"}, {"ya", "я"}, {"yu", "ю"}, {"yo", "ё"},
				{"ts", "ц"}, {"kh", "х"}, {"ye", "е"}, {"ey", "ей"}, {"iy", "ий"}};
		String one = "abvgdezijklmnoprstufhcyqwx";
		String[] to = {"а", "б", "в", "г", "д", "е", "з", "и", "й", "к", "л", "м", "н", "о", "п", "р", "с", "т", "у", "ф", "х",
				"ц", "ы", "к", "в", "кс"};
		StringBuilder sb = new StringBuilder();
		int i = 0;
		outer:
		while (i < low.length()) {
			for (String[] d : di) {
				if (low.startsWith(d[0], i)) {
					sb.append(d[1]);
					i += d[0].length();
					continue outer;
				}
			}
			char c = low.charAt(i);
			int p = one.indexOf(c);
			sb.append(p >= 0 ? to[p] : String.valueOf(c));
			i++;
		}
		return sb.toString();
	}

	// ---------- Нормализация ----------

	/** Нижний регистр, ё=е, всё кроме букв и цифр — пробел. Длина строки не меняется. */
	static String norm(String s) {
		char[] c = s.toLowerCase(Locale.ROOT).toCharArray();
		for (int i = 0; i < c.length; i++) {
			char ch = c[i];
			if (ch == 'ё') c[i] = 'е';
			else if (!Character.isLetterOrDigit(ch)) c[i] = ' ';
		}
		return new String(c);
	}

	static String[] words(String normalized) {
		List<String> w = new ArrayList<>();
		int start = -1;
		for (int i = 0; i <= normalized.length(); i++) {
			boolean sp = i == normalized.length() || normalized.charAt(i) == ' ';
			if (!sp && start < 0) start = i;
			if (sp && start >= 0) {
				w.add(normalized.substring(start, i));
				start = -1;
			}
		}
		return w.toArray(new String[0]);
	}

	// ---------- Сравнение ----------

	/** Расстояние Дамерау-Левенштейна (с перестановкой соседних букв). */
	static int distance(String a, String b) {
		int n = a.length();
		int m = b.length();
		int[][] d = new int[n + 1][m + 1];
		for (int i = 0; i <= n; i++) d[i][0] = i;
		for (int j = 0; j <= m; j++) d[0][j] = j;
		for (int i = 1; i <= n; i++) {
			for (int j = 1; j <= m; j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				int v = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
				if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
					v = Math.min(v, d[i - 2][j - 2] + 1);
				}
				d[i][j] = v;
			}
		}
		return d[n][m];
	}

	private static float wordScore(String t, String w, boolean fuzzy) {
		if (t.equals(w)) return 100.0f;
		if (w.startsWith(t)) return 90.0f - Math.min(10, w.length() - t.length());
		if (t.length() >= 2 && w.contains(t)) return 50.0f;
		if (fuzzy && t.length() >= 4) {
			int allow = t.length() >= 7 ? 2 : 1;
			int best = 99;
			for (int dl = -1; dl <= 1; dl++) {
				int len = t.length() + dl;
				if (len >= 3 && len <= w.length()) {
					best = Math.min(best, distance(t, w.substring(0, len)));
				}
			}
			if (w.length() <= t.length() + 2) best = Math.min(best, distance(t, w));
			if (best <= allow) return 72.0f - 12.0f * best;
		}
		return 0.0f;
	}

	private static float bestWord(String t, String[] words, boolean fuzzy) {
		float best = 0.0f;
		for (String w : words) {
			float s = wordScore(t, w, fuzzy);
			if (s > best) best = s;
		}
		return best;
	}

	private static boolean subsequence(String t, String s) {
		int p = 0;
		for (int i = 0; i < s.length() && p < t.length(); i++) {
			if (s.charAt(i) == t.charAt(p)) p++;
		}
		return p == t.length();
	}

	private static float scoreItem(Item it, String[] toks) {
		float total = 0.0f;
		for (String t : toks) {
			float s = bestWord(t, it.primary, true);
			float c = bestWord(t, it.context, true) * 0.5f;
			float d = bestWord(t, it.desc, false) * 0.32f;
			s = Math.max(s, Math.max(c, d));
			if (s <= 0.0f && toks.length == 1 && t.length() >= 3 && subsequence(t, it.joined)) s = 28.0f;
			if (s <= 0.0f) return 0.0f;
			total += s;
		}
		float score = total / toks.length;
		if (it.kind == KIND_MODULE) score += 6.0f;
		else if (it.kind == KIND_ACTION) score += 2.0f;
		if (it.primary.length > 0 && it.primary[0].startsWith(toks[0])) score += 4.0f;
		return score;
	}

	private static int[] highlight(Item it, String tok) {
		int idx = it.normTitle.indexOf(tok);
		if (idx >= 0) return new int[]{idx, tok.length()};
		String[] tw = words(it.normTitle);
		int pos = 0;
		float best = 0.0f;
		int[] out = {0, 0};
		for (String w : tw) {
			int at = it.normTitle.indexOf(w, pos);
			pos = at + w.length();
			float s = wordScore(tok, w, true);
			if (s > best) {
				best = s;
				out = new int[]{at, Math.min(w.length(), tok.length())};
			}
		}
		return out;
	}

	/** Ищет запрос среди записей, возвращает до limit лучших результатов (лучшие первыми). */
	static List<Hit> search(List<Item> items, String rawQuery, int limit) {
		List<Hit> out = new ArrayList<>();
		if (rawQuery == null) return out;
		String q = rawQuery.trim();
		if (q.isEmpty()) return out;
		List<String[]> variants = new ArrayList<>();
		List<Float> penalty = new ArrayList<>();
		String[] direct = words(norm(q));
		if (direct.length == 0) return out;
		variants.add(direct);
		penalty.add(0.0f);
		for (String v : layoutVariants(q)) {
			String[] w = words(norm(v));
			if (w.length > 0) {
				variants.add(w);
				penalty.add(4.0f);
			}
		}
		String tr = translit(q);
		if (tr != null) {
			String[] w = words(norm(tr));
			if (w.length > 0) {
				variants.add(w);
				penalty.add(9.0f);
			}
		}
		for (Item it : items) {
			float best = 0.0f;
			String[] bestToks = direct;
			for (int v = 0; v < variants.size(); v++) {
				float s = scoreItem(it, variants.get(v));
				if (s > 0.0f) {
					s -= penalty.get(v);
					if (s > best) {
						best = s;
						bestToks = variants.get(v);
					}
				}
			}
			if (best >= 30.0f) {
				int[] hl = highlight(it, bestToks[0]);
				out.add(new Hit(it, best, hl[0], hl[1]));
			}
		}
		out.sort((x, y) -> {
			int c = Float.compare(y.score, x.score);
			if (c != 0) return c;
			return Integer.compare(x.item.title.length(), y.item.title.length());
		});
		if (out.size() > limit) return new ArrayList<>(out.subList(0, limit));
		return out;
	}
}
