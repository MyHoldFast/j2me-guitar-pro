package gtab;

final class ChordLib {
	static final String[] ROOT = { "C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B" };
	static final String[] NOTE = { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" };
	static final String[] TYPE = {
		"", "m", "5", "7", "maj7", "m7", "6", "m6", "sus2", "sus4", "7sus4", "dim", "dim7", "m7b5", "aug",
		"add9", "madd9", "9", "maj9", "m9", "7#9", "13", "mMaj7", "6/9"
	};
	private static final int[][] IV = {
		{ 0, 4, 7 }, { 0, 3, 7 }, { 0, 7 }, { 0, 4, 10, 7 }, { 0, 4, 11, 7 }, { 0, 3, 10, 7 }, { 0, 4, 9, 7 }, { 0, 3, 9, 7 },
		{ 0, 2, 7 }, { 0, 5, 7 }, { 0, 5, 10, 7 }, { 0, 3, 6 }, { 0, 3, 6, 9 }, { 0, 3, 6, 10 }, { 0, 4, 8 },
		{ 0, 4, 2, 7 }, { 0, 3, 2, 7 }, { 0, 4, 10, 2, 7 }, { 0, 4, 11, 2, 7 }, { 0, 3, 10, 2, 7 }, { 0, 4, 10, 3, 7 },
		{ 0, 4, 10, 9, 7, 2 }, { 0, 3, 11, 7 }, { 0, 4, 9, 2, 7 }
	};
	private static final int[] REQ = { 3, 3, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 3, 3, 3, 4, 4, 4, 4, 4, 3, 4 };
	private static final String[][] ALIAS = {
		{ "maj", "0" }, { "M", "0" }, { "major", "0" }, { "min", "1" }, { "-", "1" }, { "mi", "1" }, { "dom7", "3" },
		{ "M7", "4" }, { "Maj7", "4" }, { "ma7", "4" }, { "j7", "4" }, { "min7", "5" }, { "-7", "5" }, { "m7-5", "13" },
		{ "\u00f8", "13" }, { "o", "11" }, { "\u00b0", "11" }, { "o7", "12" }, { "\u00b07", "12" }, { "+", "14" },
		{ "sus", "9" }, { "7sus", "10" }, { "add2", "15" }, { "2", "15" }, { "m(add9)", "16" }, { "M9", "18" },
		{ "m(maj7)", "22" }, { "mM7", "22" }, { "mmaj7", "22" }, { "69", "23" }, { "min6", "7" }, { "min9", "19" }
	};

	static final int MAXV = 16;

	final int[] tune;
	final int n;
	int count;
	final byte[][] fr = new byte[MAXV][], fg = new byte[MAXV][];
	final int[] bar = new int[MAXV * 3];
	private final int[] cf, bf, bfg;
	private final int[] bestScore = new int[MAXV];
	private int[] set;
	private int req, bass, need;
	private boolean power;
	private final int[] tmpF, tmpBar = new int[3];

	ChordLib(int[] tune) {
		this.tune = tune;
		n = tune.length;
		cf = new int[n];
		bf = new int[n];
		bfg = new int[n];
		tmpF = new int[n];
	}

	static String name(int root, int type, int bassPc) {
		return ROOT[root] + TYPE[type] + (bassPc >= 0 && bassPc != root ? "/" + ROOT[bassPc] : "");
	}

	static String notes(int root, int type, int bassPc) {
		StringBuffer b = new StringBuffer();
		if (bassPc >= 0 && bassPc != root) b.append(NOTE[bassPc]).append(" / ");
		int[] iv = sorted(type);
		for (int i = 0; i < iv.length; i++) {
			if (i > 0) b.append(' ');
			b.append(NOTE[(root + iv[i]) % 12]);
		}
		return b.toString();
	}

	private static int[] sorted(int type) {
		int[] a = new int[IV[type].length];
		System.arraycopy(IV[type], 0, a, 0, a.length);
		for (int i = 1; i < a.length; i++)
			for (int j = i; j > 0 && a[j - 1] > a[j]; j--) {
				int x = a[j]; a[j] = a[j - 1]; a[j - 1] = x;
			}
		return a;
	}

	static int[] parse(String s) {
		StringBuffer c = new StringBuffer();
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			if (ch == '\u266f') ch = '#';
			if (ch == '\u266d') ch = 'b';
			if (ch != ' ') c.append(ch);
		}
		s = c.toString();
		int bass = -1, sl = s.lastIndexOf('/');
		if (sl > 0 && sl < s.length() - 1 && !s.endsWith("6/9")) {
			int[] bp = root(s.substring(sl + 1));
			if (bp == null || bp[1] != s.length() - sl - 1) return null;
			bass = bp[0];
			s = s.substring(0, sl);
		}
		int[] rp = root(s);
		if (rp == null) return null;
		String suf = s.substring(rp[1]);
		int type = -1;
		for (int i = 0; i < TYPE.length && type < 0; i++) if (TYPE[i].equals(suf)) type = i;
		for (int i = 0; i < ALIAS.length && type < 0; i++) if (ALIAS[i][0].equals(suf)) type = Integer.parseInt(ALIAS[i][1]);
		String low = suf.toLowerCase();
		for (int i = 0; i < TYPE.length && type < 0; i++) if (TYPE[i].toLowerCase().equals(low)) type = i;
		for (int i = 0; i < ALIAS.length && type < 0; i++) if (ALIAS[i][0].toLowerCase().equals(low)) type = Integer.parseInt(ALIAS[i][1]);
		if (type < 0) return null;
		return new int[] { rp[0], type, bass };
	}

	private static int[] root(String s) {
		if (s.length() == 0) return null;
		int pc = "C D EF G A B".indexOf(Character.toUpperCase(s.charAt(0)));
		if (Character.toUpperCase(s.charAt(0)) == 'H') pc = 11;
		if (pc < 0) return null;
		int i = 1;
		while (i < s.length() && (s.charAt(i) == '#' || (s.charAt(i) == 'b' && !(i + 1 < s.length() && s.charAt(i + 1) == '5')))) {
			pc += s.charAt(i) == '#' ? 1 : -1;
			i++;
		}
		return new int[] { (pc + 12) % 12, i };
	}

	void build(int root, int type, int bassPc) {
		count = 0;
		for (int i = 0; i < MAXV; i++) bestScore[i] = Integer.MIN_VALUE;
		int[] iv = IV[type];
		set = new int[iv.length + (bassPc >= 0 ? 1 : 0)];
		for (int i = 0; i < iv.length; i++) set[i] = (root + iv[i]) % 12;
		if (bassPc >= 0) set[iv.length] = bassPc;
		req = 0;
		for (int i = 0; i < REQ[type]; i++) req |= 1 << set[i];
		if (bassPc >= 0) req |= 1 << bassPc;
		bass = bassPc >= 0 ? bassPc : root;
		power = type == 2;
		need = power ? 2 : n <= 4 ? 3 : 4;
		if (need > n) need = n;
		for (int b = 1; b <= 12; b++) walk(n - 1, b, false, false);
		byte[][] f2 = new byte[MAXV][], g2 = new byte[MAXV][];
		int[] b2 = new int[MAXV * 3];
		int c = 0, top = -1;
		for (int p = 0; p <= 5; p++) if (bestScore[p] != Integer.MIN_VALUE && (top < 0 || bestScore[p] > bestScore[top])) top = p;
		int open = -1;
		for (int p = 0; p <= 3; p++) if (bestScore[p] != Integer.MIN_VALUE && (open < 0 || bestScore[p] > bestScore[open])) open = p;
		if (open >= 0 && top >= 0 && bestScore[open] >= bestScore[top] - 4) top = open;
		for (int q = -1; q < MAXV; q++) {
			int p = q < 0 ? top : q;
			if (p < 0 || (q >= 0 && p == top) || fr[p] == null || bestScore[p] == Integer.MIN_VALUE) continue;
			f2[c] = fr[p];
			g2[c] = fg[p];
			b2[c * 3] = bar[p * 3];
			b2[c * 3 + 1] = bar[p * 3 + 1];
			b2[c * 3 + 2] = bar[p * 3 + 2];
			c++;
		}
		for (int i = 0; i < MAXV; i++) {
			fr[i] = f2[i];
			fg[i] = g2[i];
		}
		System.arraycopy(b2, 0, bar, 0, b2.length);
		count = c;
	}

	private boolean in(int pc) {
		for (int i = 0; i < set.length; i++) if (set[i] == pc) return true;
		return false;
	}

	private void walk(int s, int base, boolean started, boolean ended) {
		if (s < 0) {
			eval(base);
			return;
		}
		cf[s] = -1;
		walk(s - 1, base, started, started);
		if (ended) return;
		if (base <= 5 && in(tune[s] % 12) && (started || tune[s] % 12 == bass)) {
			cf[s] = 0;
			walk(s - 1, base, true, false);
		}
		for (int f = base; f < base + 4 && f <= 15; f++) {
			int pc = (tune[s] + f) % 12;
			if (!in(pc) || (!started && pc != bass)) continue;
			cf[s] = f;
			walk(s - 1, base, true, false);
		}
		cf[s] = -1;
	}

	private void eval(int base) {
		int sounding = 0, mask = 0, minF = 99, maxF = 0, opens = 0, highMute = 0, inner = 0;
		boolean seen = false;
		for (int s = 0; s < n; s++) {
			if (cf[s] < 0) {
				if (!seen) highMute++;
				continue;
			}
			seen = true;
			sounding++;
			mask |= 1 << ((tune[s] + cf[s]) % 12);
			if (cf[s] == 0) opens++;
			else {
				if (cf[s] < minF) minF = cf[s];
				if (cf[s] > maxF) maxF = cf[s];
			}
		}
		if (sounding < need || (mask & req) != req || (power && sounding > 3)) return;
		if (minF == 99) minF = 1;
		if (minF != base) return;
		if (highMute > 1 && need > 2) return;
		for (int s = 1; s < n - 1; s++) {
			if (cf[s] != 0 || base <= 2) continue;
			boolean hi = false, lo = false;
			for (int u = 0; u < s; u++) if (cf[u] > 0) hi = true;
			for (int u = s + 1; u < n; u++) if (cf[u] > 0) lo = true;
			if (hi && lo) inner++;
		}
		int fingers = fingers(cf, tmpF, tmpBar);
		if (fingers < 0) return;
		int score = sounding * 8 + (base <= 2 ? opens * 6 : 0) - fingers * 2 - highMute * 4 - inner * 6;
		if (tmpBar[0] > 0 && base <= 3) score -= 4;
		if (tmpBar[0] > 0) {
			int bs = n - 1;
			while (bs > 0 && cf[bs] < 0) bs--;
			if (cf[bs] != tmpBar[0]) score -= 14;
		}
		if (maxF - minF >= 3) score -= 6;
		if (opens > 0 && maxF >= 4) score -= 20;
		if (opens > 0 && minF < 99) {
			int hiF = -1, loF = -1;
			for (int s = 0; s < n; s++) {
				if (cf[s] != minF) continue;
				if (hiF < 0) hiF = s;
				loF = s;
			}
			for (int s = hiF + 1; s < loF; s++) if (cf[s] == 0) score -= 12;
		}
		if ((mask & (1 << set[set.length - 1])) == 0) score -= 2;
		int p = base;
		if (p >= MAXV) return;
		if (score <= bestScore[p]) return;
		bestScore[p] = score;
		byte[] a = new byte[n], g = new byte[n];
		for (int s = 0; s < n; s++) {
			a[s] = (byte) cf[s];
			g[s] = (byte) tmpF[s];
		}
		fr[p] = a;
		fg[p] = g;
		bar[p * 3] = tmpBar[0];
		bar[p * 3 + 1] = tmpBar[1];
		bar[p * 3 + 2] = tmpBar[2];
	}

	private int fingers(int[] f, int[] out, int[] barre) {
		barre[0] = 0;
		int cnt = 0, f0 = 99;
		for (int s = 0; s < n; s++) {
			out[s] = 0;
			if (f[s] > 0) {
				cnt++;
				if (f[s] < f0) f0 = f[s];
			}
		}
		if (cnt == 0) return 0;
		int next = 1;
		if (cnt > 4) {
			int lo = -1, hi = -1;
			for (int s = 0; s < n; s++) {
				if (f[s] == f0) {
					if (hi < 0) hi = s;
					lo = s;
				}
			}
			for (int s = hi; s <= lo; s++) if (f[s] < f0) return -1;
			for (int s = hi; s <= lo; s++) if (f[s] == f0) out[s] = 1;
			barre[0] = f0;
			barre[1] = lo;
			barre[2] = hi;
			next = 2;
		}
		for (int fret = f0; fret <= 15; fret++) {
			for (int s = n - 1; s >= 0; s--) {
				if (f[s] != fret || out[s] != 0) continue;
				if (next > 4) return -1;
				out[s] = next++;
			}
		}
		return next - 1;
	}
}
