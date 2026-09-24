package gtab;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.Sprite;

final class View extends Canvas implements Fling.Target {
	static final int[] GLYPH = {
		0x7B6F, 0x2C97, 0x73E7, 0x73CF, 0x5BC9, 0x79CF, 0x79EF, 0x7249, 0x7BEF, 0x7BCF, 0x0AA8,
		0x2BED, 0x6BAE, 0x3923, 0x6B6E, 0x79A7, 0x79A4, 0x396B, 0x5F7D
	};
	private static final int BG = 0xFFFFFF, FG = 0x000000, LINE = 0x808080, TXT = 0x0000A0, MARK = 0xA00000;

	private static final int K_NUM = 1, K_MARK = 2, K_TEMPO = 3, K_ALT = 4, K_CHORD = 5, K_TEXT = 6, K_REP = 7, K_SIG = 8,
			K_PM = 16, K_LR = 17, K_VIB = 18, K_BEND = 19, K_TAP = 20, K_TR = 21, K_ACC = 22, K_HACC = 23,
			K_STAC = 24, K_NH = 25, K_AH = 26, K_WBAR = 27, K_FADE = 28, K_PDOWN = 29, K_PUP = 30, K_TREMP = 31;
	private static final int LV = 6;
	private static final int[] RUN_MASK = { Track.N_PM, Track.N_RING, Track.N_VIB };
	private static final int[] RUN_KIND = { K_PM, K_LR, K_VIB };

	private final App app;
	private Song s;
	private Track t;
	private String busy, stat;
	private final Font f = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
	private final Font fb = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL);
	private final int fh = f.getHeight();

	private int rot;
	private Image buf;
	private String[] hd1, hd2;
	private Graphics bufG;
	private int sc, gap, stem, bot, headH, lm, ls, rm, n, w, h, sy, lines, cur = -1;
	private final Fling fling = new Fling(this, this);
	private boolean playing;
	private Image[] dig;
	private short[] bX, bLn, mX, mEX, mL, mEL, lnEnd, lnTop;
	private byte[] lnTl;
	private int[] lnY;

	private int ic;
	private short[] aX, aX2, aLn;
	private byte[] aK, aLv;
	private int[] aRef;
	private String[] aT;

	private final int[] te = new int[LV], ee = new int[LV];
	private int curLine, tl, el, px;
	private final int[] rX = new int[3], rLast = new int[3], rLv = new int[3];
	private final boolean[] rOpen = new boolean[3];

	private final int bar;
	private boolean barDown;
	private int pressed = -1;

	View(App app) {
		this.app = app;
		setFullScreenMode(true);
		int m = Math.min(getWidth(), getHeight());
		sc = m >= 360 ? 3 : m >= 240 ? 2 : 1;
		bar = hasPointerEvents() ? Bar.height() : 0;
	}

	private boolean failed;
	private String toast;
	private int toastId;

	synchronized void announce() {
		if (t == null || sy < headH / 2) return;
		toast = (t.index + 1) + "/" + s.tCount + "  " + Cp.s(s.tName[t.index]);
		final int id = ++toastId;
		repaint();
		new Thread(new Runnable() {
			public void run() {
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
				}
				synchronized (View.this) {
					if (toastId == id) {
						toast = null;
						repaint();
					}
				}
			}
		}).start();
	}

	synchronized void busy(String msg) {
		busy = msg;
		failed = false;
		repaint();
	}

	synchronized void fail(String msg) {
		busy = msg;
		failed = true;
		repaint();
	}

	synchronized void status(String m) {
		stat = m;
		repaint();
	}

	synchronized void playing(boolean p) {
		playing = p;
		repaint();
	}

	synchronized Song song() {
		return s;
	}

	synchronized void set(Song song) {
		s = song;
		t = song == null ? null : song.trk;
		bX = bLn = mX = mEX = mL = mEL = lnEnd = lnTop = null;
		aX = aX2 = aLn = null;
		sy = 0;
		busy = null;
		stat = null;
		playing = false;
		cur = -1;
		kBeat = -1;
		toast = null;
		fling.stop();
		if (t != null) {
			layout();
			cur = next(-1, 1);
		}
		repaint();
	}

	private int kBeat = -1, kFrac, kSy = -1;

	synchronized void relayout() {
		if (t == null) return;
		fling.stop();
		int anchor = -1, off = 0, frac = -1;
		if (kBeat >= 0 && kBeat < t.beats && sy == kSy) {
			anchor = kBeat;
			frac = kFrac;
		} else if (lnY != null && t.beats > 0) {
			if (cur >= 0 && cur < t.beats) {
				int cy = headH + lnY[bLn[cur]] - sy;
				if (cy >= 0 && cy < h) {
					anchor = cur;
					off = cy;
				}
			}
			if (anchor < 0 && sy > headH / 2) {
				int l0 = lineOf(sy - headH);
				anchor = firstOnLine(l0);
				frac = (sy - headH - lnY[l0]) * 1000 / Math.max(1, lnY[l0 + 1] - lnY[l0]);
			}
		}
		layout();
		if (anchor < 0) {
			scrollTo(0);
			return;
		}
		int l = bLn[anchor], lh = lnY[l + 1] - lnY[l];
		if (frac >= 0) {
			scrollTo(headH + lnY[l] + lh * frac / 1000);
			kBeat = anchor;
			kFrac = frac;
			kSy = sy;
			return;
		}
		kBeat = -1;
		if (off > h - lh) off = Math.max(0, h - lh);
		scrollTo(headH + lnY[l] - off);
	}

	private int firstOnLine(int line) {
		int lo = 0, hi = s.mCount - 1;
		while (lo < hi) {
			int mid = (lo + hi) >> 1;
			if (mEL[mid] < line) lo = mid + 1;
			else hi = mid;
		}
		for (int m = lo; m < s.mCount && mL[m] <= line; m++)
			for (int k = t.mBeat[m]; k < t.mBeat[m + 1]; k++)
				if (bLn[k] >= line) return k;
		return -1;
	}

	protected void sizeChanged(int nw, int nh) {
		relayout();
	}

	synchronized String cursorChord() {
		if (t == null || cur < 0) return null;
		for (int b = cur, k = 0; b >= 0 && k < 256; b--, k++) {
			Object c = t.chord.get(new Integer(b));
			if (c != null) return Cp.s((byte[]) c);
		}
		return null;
	}

	synchronized int cursorMeasure() {
		return cur < 0 ? 0 : measureOf(cur);
	}

	synchronized int cursorPos() {
		return cur < 0 ? 0 : t.bPos[cur];
	}

	synchronized void follow(int m, int tick) {
		if (t == null || m < 0 || m >= s.mCount) return;
		int b = -1;
		for (int k = t.mBeat[m], e = t.mBeat[m + 1]; k < e; k++) {
			if ((t.bDur[k] & Track.D_V2) != 0) continue;
			if (b >= 0 && t.bPos[k] > tick) break;
			b = k;
		}
		if (b >= 0 && b != cur) show(b);
	}

	private void glyphs() {
		dig = new Image[GLYPH.length];
		for (int i = 0; i < GLYPH.length; i++) {
			Image im = Image.createImage(3 * sc, 5 * sc);
			Graphics g = im.getGraphics();
			g.setColor(BG);
			g.fillRect(0, 0, 3 * sc, 5 * sc);
			g.setColor(FG);
			for (int k = 0; k < 15; k++)
				if ((GLYPH[i] & (1 << (14 - k))) != 0) g.fillRect((k % 3) * sc, (k / 3) * sc, sc, sc);
			dig[i] = im;
		}
	}

	private void layout() {
		w = vw();
		h = vh() - bar;
		glyphs();
		n = s.tStr[t.index];
		gap = 6 * sc + 2;
		stem = 7 * sc;
		bot = 3 * sc + stem + 6 * sc + 6;
		int ti0 = t.index;
		String ti = Cp.s(s.title), ar = Cp.s(s.artist);
		if (ar.length() > 0) ti = ti.length() > 0 ? ti + " - " + ar : ar;
		StringBuffer tb = new StringBuffer();
		tb.append(ti0 + 1).append('/').append(s.tCount).append(' ').append(Cp.s(s.tName[ti0]));
		if (s.tCapo[ti0] > 0) tb.append(" capo ").append(s.tCapo[ti0]);
		hd1 = wrap(ti, fb, w - 2 * (2 * sc + 1), 2);
		hd2 = wrap(tb.toString(), f, w - 2 * (2 * sc + 1), 2);
		headH = (hd1.length + hd2.length) * fh + 4;
		lm = 2 * sc + 1;
		rm = w - lm;
		int tw = 3 * sc;
		byte[] tune = s.tTune[t.index];
		for (int i = 0; i < tune.length; i++) if (note(tune[i]).length() > 1) tw = 7 * sc;
		ls = lm + tw + 3 * sc;
		int mc = s.mCount;
		bX = new short[t.beats];
		bLn = new short[t.beats];
		mX = new short[mc];
		mEX = new short[mc];
		mL = new short[mc];
		mEL = new short[mc];
		short[] le = new short[mc + t.beats + 1];
		int x = ls, line = 0;
		for (int m = 0; m < mc; m++) {
			int b0 = t.mBeat[m], b1 = t.mBeat[m + 1];
			int pad = 2 * sc;
			if ((s.mFlag[m] & Song.M_OPEN) != 0) pad += 3 * sc;
			int first = b0 < b1 ? groupW(b0, b1) : 10 * sc;
			if (x > st(line) && x + pad + first > rm) {
				le[line++] = (short) x;
				x = lm;
			}
			mL[m] = (short) line;
			mX[m] = (short) x;
			x += pad;
			if (b0 == b1) x += 10 * sc;
			for (int b = b0; b < b1;) {
				int e = b + 1;
				while (e < b1 && t.bPos[e] == t.bPos[b]) e++;
				int cw = groupW(b, e);
				if (x > st(line) && x + cw > rm) {
					le[line++] = (short) x;
					x = lm;
				}
				for (int k = b; k < e; k++) {
					bX[k] = (short) (x + cw / 2);
					bLn[k] = (short) line;
				}
				x += cw;
				b = e;
			}
			if (s.mRep[m] > 0) x += 3 * sc;
			mEX[m] = (short) x;
			mEL[m] = (short) line;
		}
		le[line] = (short) x;
		lines = line + 1;
		lnEnd = new short[lines];
		System.arraycopy(le, 0, lnEnd, 0, lines);
		annotate();
	}

	private void annotate() {
		lnTop = new short[lines];
		lnTl = new byte[lines];
		lnY = new int[lines + 1];
		int cap = t.beats + s.mCount + 16;
		aX = new short[cap];
		aX2 = new short[cap];
		aLn = new short[cap];
		aK = new byte[cap];
		aLv = new byte[cap];
		aRef = new int[cap];
		aT = new String[cap];
		ic = 0;
		curLine = -1;
		for (int r = 0; r < 3; r++) rOpen[r] = false;
		line(0);
		for (int m = 0; m < s.mCount; m++) {
			line(mL[m]);
			int x = mX[m];
			int lim = lnEnd[mL[m]];
			for (int m2 = m + 1; m2 < s.mCount && mL[m2] == mL[m]; m2++) {
				if (labeled(m2)) {
					lim = mX[m2];
					break;
				}
			}
			int flow = lim - x - 3 * sc;
			if (x == st(mL[m]) || m == 0) str(K_NUM, m, x, String.valueOf(m + 1), f, flow);
			if (showSig(m)) str(K_SIG, m, x, sig(m), fb, flow);
			if (s.mMark[m] != null) str(K_MARK, m, x, Cp.s(s.mMark[m]), fb, flow);
			if (m == 0 || s.mTempo[m] != s.mTempo[m - 1]) text(K_TEMPO, m, x, tempoW(m), flow);
			if (s.mAlt[m] != 0) {
				int end = mEL[m] == mL[m] ? mEX[m] : lnEnd[mL[m]];
				int lv = place(te, x, end - x, 0);
				add(K_ALT, m, x, end, mL[m], lv);
				if (lv >= tl) tl = lv + 1;
			}
			for (int b = t.mBeat[m]; b < t.mBeat[m + 1]; b++) beatItems(b);
			if (s.mRep[m] > 1) {
				line(mEL[m]);
				String rs = "x" + (s.mRep[m] + 1);
				str(K_REP, m, mEX[m] - f.stringWidth(rs), rs, f, 0);
			}
		}
		line(-1);
		for (int l = 0; l < lines; l++) {
			int lv = lnTl[l] + lnTop[l];
			int top = lv * fh + gap / 2 + 2;
			if (top < gap / 2 + 2) top = gap / 2 + 2;
			lnTop[l] = (short) top;
			lnY[l + 1] = lnY[l] + top + (n - 1) * gap + bot;
		}
	}

	private void line(int l) {
		if (l == curLine) return;
		if (curLine >= 0) {
			for (int r = 0; r < 3; r++) close(r);
			lnTl[curLine] = (byte) tl;
			lnTop[curLine] = (short) el;
		}
		curLine = l;
		tl = el = 0;
		for (int i = 0; i < LV; i++) te[i] = ee[i] = -9999;
	}

	private int place(int[] ends, int x0, int wd, int shift) {
		for (int i = 0; i < LV; i++) {
			int x = ends[i] > x0 ? ends[i] : x0;
			if (x - x0 <= shift && (x == x0 || x + wd <= w - 1)) {
				ends[i] = x + wd + 2 * sc;
				px = x;
				return i;
			}
		}
		px = x0;
		return LV - 1;
	}

	private int clampX(int x, int wd) {
		if (x + wd > w - 1) x = w - 1 - wd;
		return x < lm ? lm : x;
	}

	private void text(int k, int ref, int x, int wd, int shift) {
		int lv = place(te, clampX(x, wd), wd, shift);
		if (lv >= tl) tl = lv + 1;
		add(k, ref, px, px + wd, curLine, lv);
	}

	private static String squeeze(String a) {
		StringBuffer b = new StringBuffer(a.length());
		for (int i = 0; i < a.length(); i++) {
			char c = a.charAt(i);
			if (c != ' ' || (b.length() > 0 && b.charAt(b.length() - 1) != ' ')) b.append(c);
		}
		return b.toString().trim();
	}

	static String[] wrap(String txt, Font fn, int max, int maxLines) {
		String[] out = new String[maxLines];
		int len = txt.length(), start = 0, c = 0;
		while (c < maxLines && start < len) {
			int end = start, sp = -1;
			while (end < len && fn.stringWidth(txt.substring(start, end + 1)) <= max) {
				if (txt.charAt(end) == ' ') sp = end;
				end++;
			}
			if (end < len && sp > start) end = sp;
			if (end == start) end = start + 1;
			String part = txt.substring(start, end).trim();
			start = end;
			while (start < len && txt.charAt(start) == ' ') start++;
			if (c == maxLines - 1 && start < len) part = fit(part + " " + txt.substring(start), fn, max);
			out[c++] = part;
		}
		if (c == 0) out[c++] = "";
		String[] r = new String[c];
		System.arraycopy(out, 0, r, 0, c);
		return r;
	}

	private void str(int k, int ref, int x, String txt, Font fn, int shift) {
		txt = squeeze(txt);
		String[] parts = wrap(txt, fn, w - 1 - lm, k == K_TEXT || k == K_MARK ? 3 : 1);
		for (int c = 0; c < parts.length; c++) {
			text(k, ref, c == 0 ? x : aX[ic - 1], fn.stringWidth(parts[c]), c == 0 ? shift : 0);
			aT[ic - 1] = parts[c];
		}
	}

	private void eff(int k, int ref, int x, int wd, int x2) {
		int lv = place(ee, clampX(x, wd), wd, sc);
		if (lv >= el) el = lv + 1;
		add(k, ref, px, x2 < 0 ? px + wd : x2, curLine, lv);
	}

	private void add(int k, int ref, int x, int x2, int l, int lv) {
		if (ic == aX.length) {
			int c = ic + (ic >> 1) + 16;
			short[] a = new short[c]; System.arraycopy(aX, 0, a, 0, ic); aX = a;
			a = new short[c]; System.arraycopy(aX2, 0, a, 0, ic); aX2 = a;
			a = new short[c]; System.arraycopy(aLn, 0, a, 0, ic); aLn = a;
			byte[] b = new byte[c]; System.arraycopy(aK, 0, b, 0, ic); aK = b;
			b = new byte[c]; System.arraycopy(aLv, 0, b, 0, ic); aLv = b;
			int[] r = new int[c]; System.arraycopy(aRef, 0, r, 0, ic); aRef = r;
			String[] ts = new String[c]; System.arraycopy(aT, 0, ts, 0, ic); aT = ts;
		}
		aX[ic] = (short) x;
		aX2[ic] = (short) x2;
		aLn[ic] = (short) l;
		aK[ic] = (byte) k;
		aLv[ic] = (byte) lv;
		aRef[ic] = ref;
		ic++;
	}

	private void close(int r) {
		if (!rOpen[r]) return;
		rOpen[r] = false;
		int x2 = rLast[r] + 3 * sc, lab = r == 2 ? 6 * sc : f.stringWidth(r == 0 ? "PM" : "LR");
		if (x2 < rX[r] + lab) x2 = rX[r] + lab;
		if (x2 > w - 1) x2 = w - 1;
		ee[rLv[r]] = x2 + 2 * sc;
		add(RUN_KIND[r], 0, rX[r], x2, curLine, rLv[r]);
	}

	private void beatItems(int b) {
		line(bLn[b]);
		int cx = bX[b], fx = t.bFx[b];
		boolean v2 = (t.bDur[b] & Track.D_V2) != 0;
		Integer key = new Integer(b);
		Object c = t.chord.get(key), tx = t.text.get(key);
		if (c != null) str(K_CHORD, b, cx - 2 * sc, Cp.s((byte[]) c), fb, 2 * sc);
		if (tx != null) str(K_TEXT, b, cx - 2 * sc, Cp.s((byte[]) tx), f, 2 * sc);
		if (!v2) {
			for (int r = 0; r < 3; r++) {
				if (has(b, RUN_MASK[r])) {
					if (!rOpen[r]) {
						int x = clampX(cx - 3 * sc, 0);
						int lv = place(ee, x, 0, 0);
						if (lv >= el) el = lv + 1;
						ee[lv] = Integer.MAX_VALUE;
						rOpen[r] = true;
						rX[r] = x;
						rLv[r] = lv;
					}
					rLast[r] = cx;
				} else if ((t.bDur[b] & Track.D_REST) == 0 || r != 1) close(r);
			}
		}
		int lw = f.stringWidth("W");
		if ((fx & (Track.B_TAP | Track.B_SLAP | Track.B_POP)) != 0) eff(K_TAP, b, cx - lw / 2, lw, -1);
		if (has(b, Track.N_TRILL)) eff(K_TR, b, cx - lw / 2, f.stringWidth("tr"), -1);
		if (has(b, Track.N_HACC)) eff(K_HACC, b, cx - 2 * sc, 4 * sc, -1);
		else if (has(b, Track.N_ACC)) eff(K_ACC, b, cx - 2 * sc, 4 * sc, -1);
		if (has(b, Track.N_STAC)) eff(K_STAC, b, cx - sc, 2 * sc, -1);
		if (has(b, Track.N_NH)) eff(K_NH, b, cx - 3 * sc, f.stringWidth("NH"), -1);
		if (has(b, Track.N_AH)) eff(K_AH, b, cx - 3 * sc, f.stringWidth("AH"), -1);
		if ((fx & Track.B_TREM) != 0) eff(K_WBAR, b, cx - 2 * sc, f.stringWidth("w/bar"), -1);
		if ((fx & Track.B_FADE) != 0) eff(K_FADE, b, cx - 3 * sc, 8 * sc, -1);
		if ((fx & Track.B_PDOWN) != 0) eff(K_PDOWN, b, cx - 2 * sc, 4 * sc, -1);
		if ((fx & Track.B_PUP) != 0) eff(K_PUP, b, cx - 2 * sc, 4 * sc, -1);
		for (int i = t.bNote[b], e = i + t.bCnt[b]; i < e; i++) {
			if ((t.nFx[i] & Track.N_BEND) != 0) {
				int nw = numW(t.nFret[i]), bx = cx + nw / 2 + 3 * sc, bw = f.stringWidth(bendLab(t.nArg[i]));
				eff(K_BEND, i, bx - bw / 2, bw, -1);
				aX2[ic - 1] = (short) cx;
			}
		}
	}

	private int st(int l) {
		return l == 0 ? ls : lm;
	}

	private boolean labeled(int m) {
		return showSig(m) || s.mMark[m] != null || s.mTempo[m] != s.mTempo[m - 1] || mX[m] == st(mL[m]);
	}

	private String sig(int m) {
		return s.mNum[m] + "/" + s.mDen[m];
	}

	private int tempoW(int m) {
		return fh / 4 + 3 + f.stringWidth("=" + s.mTempo[m]);
	}

	private boolean showSig(int m) {
		return m == 0 || s.mNum[m] != s.mNum[m - 1] || s.mDen[m] != s.mDen[m - 1];
	}

	private int groupW(int b, int e) {
		int mw = 0;
		for (int k = b; k < e; k++) {
			int cw = colW(k);
			if (cw > mw) mw = cw;
		}
		return mw;
	}

	private int colW(int b) {
		int d = 1, extra = 0;
		for (int i = t.bNote[b], e = i + t.bCnt[b]; i < e; i++) {
			if (t.nFret[i] > 9 && (t.nFx[i] & Track.N_DEAD) == 0) d = 2;
			if ((t.nFx[i] & (Track.N_TIE | Track.N_GHOST | Track.N_NH | Track.N_AH)) != 0) extra = 2 * sc;
			if (t.nGrace[i] != -1 && extra < 5 * sc) extra = 5 * sc;
		}
		int code = t.bDur[b] & 7;
		int dw = d * 4 * sc - sc;
		return dw + 3 * sc + extra + (code == 0 ? 6 * sc : code == 1 ? 4 * sc : code == 2 ? 2 * sc : code == 3 ? sc : 0);
	}

	private int numW(int v) {
		return v > 9 ? 7 * sc : 3 * sc;
	}

	private void num(Graphics g, int v, int cx, int cy) {
		int nw = numW(v), x = cx - nw / 2, y = cy - (5 * sc) / 2;
		g.setColor(BG);
		g.fillRect(x - sc, y, nw + 2 * sc, 5 * sc);
		if (v > 9) {
			g.drawImage(dig[(v / 10) % 10], x, y, Graphics.TOP | Graphics.LEFT);
			x += 4 * sc;
		}
		g.drawImage(dig[v % 10], x, y, Graphics.TOP | Graphics.LEFT);
	}

	private void glyph(Graphics g, int i, int cx, int cy) {
		int x = cx - (3 * sc) / 2, y = cy - (5 * sc) / 2;
		g.setColor(BG);
		g.fillRect(x - sc, y, 5 * sc, 5 * sc);
		g.drawImage(dig[i], x, y, Graphics.TOP | Graphics.LEFT);
	}

	private int lineOf(int y) {
		if (lnY == null || y < 0) return 0;
		if (lines == 0) return 0;
		int lo = 0, hi = lines - 1;
		while (lo < hi) {
			int mid = (lo + hi + 1) >> 1;
			if (lnY[mid] <= y) lo = mid;
			else hi = mid - 1;
		}
		return lo;
	}

	private int ly(int l) {
		return headH - sy + lnY[l];
	}

	private int vw() {
		return rot == 0 ? getWidth() : getHeight();
	}

	private int vh() {
		return rot == 0 ? getHeight() : getWidth();
	}

	synchronized void rotate() {
		rot = rot == 0 ? 1 : rot == 1 ? 2 : 0;
		buf = null;
		bufG = null;
		if (rot != 0) {
			try {
				buf = Image.createImage(vw(), vh());
				bufG = buf.getGraphics();
			} catch (OutOfMemoryError e) {
				buf = null;
				rot = 0;
				stat = L.s("Мало памяти для поворота", "Not enough memory to rotate");
			}
		}
		if (t != null) relayout();
		else repaint();
	}

	protected synchronized void paint(Graphics g) {
		if (rot == 0) {
			draw(g);
			return;
		}
		if (buf == null || buf.getWidth() != vw() || buf.getHeight() != vh()) {
			try {
				buf = null;
				buf = Image.createImage(vw(), vh());
				bufG = buf.getGraphics();
			} catch (OutOfMemoryError e) {
				rot = 0;
				stat = L.s("Мало памяти для поворота", "Not enough memory to rotate");
				relayout();
				draw(g);
				return;
			}
		}
		draw(bufG);
		g.drawRegion(buf, 0, 0, buf.getWidth(), buf.getHeight(), rot == 1 ? Sprite.TRANS_ROT90 : Sprite.TRANS_ROT270, 0, 0, Graphics.TOP | Graphics.LEFT);
	}

	private void draw(Graphics g) {
		w = vw();
		h = vh() - bar;
		g.setColor(BG);
		g.fillRect(0, 0, w, vh());
		g.setClip(0, 0, w, h);
		content(g);
		g.setClip(0, 0, w, vh());
		toolbar(g);
	}

	private void content(Graphics g) {
		g.setColor(FG);
		g.setFont(f);
		if (busy != null || t == null) {
			g.drawString(busy == null ? "" : busy, w / 2, h / 2, Graphics.BASELINE | Graphics.HCENTER);
			return;
		}
		head(g);
		int l0 = lineOf(sy - headH), l1 = lineOf(sy - headH + h);
		for (int l = l0; l <= l1; l++) {
			int y0 = ly(l) + lnTop[l];
			g.setColor(LINE);
			int x0 = st(l);
			for (int i = 0; i < n; i++) g.drawLine(x0, y0 + i * gap, lnEnd[l], y0 + i * gap);
			g.setColor(FG);
			g.drawLine(x0, y0, x0, y0 + (n - 1) * gap);
			byte[] tune = s.tTune[t.index];
			for (int i = 0; l == 0 && i < n && i < tune.length; i++) {
				String nn = note(tune[i]);
				int cx = lm + (3 * sc) / 2, cy = y0 + i * gap;
				glyph(g, 11 + nn.charAt(0) - 'A', cx, cy);
				if (nn.length() > 1) glyph(g, 18, cx + 4 * sc, cy);
			}
		}
		int lo = 0, hi = s.mCount - 1;
		while (lo < hi) {
			int mid = (lo + hi) >> 1;
			if (mEL[mid] < l0) lo = mid + 1;
			else hi = mid;
		}
		for (int m = lo; m < s.mCount && mL[m] <= l1; m++) {
			measure(g, m);
			for (int b = t.mBeat[m]; b < t.mBeat[m + 1]; b++)
				if (bLn[b] >= l0 && bLn[b] <= l1) beat(g, b);
		}
		lo = 0;
		hi = ic;
		while (lo < hi) {
			int mid = (lo + hi) >> 1;
			if (aLn[mid] < l0) lo = mid + 1;
			else hi = mid;
		}
		for (int i = lo; i < ic && aLn[i] <= l1; i++) item(g, i);
		if (cur >= 0 && cur < t.beats && bLn[cur] >= l0 && bLn[cur] <= l1) {
			int l = bLn[cur], cy = ly(l) + lnTop[l] - gap / 2, cw = 6 * sc + 4, ch = n * gap;
			g.setColor(playing ? 0xE00000 : 0x0070E0);
			g.drawRect(bX[cur] - cw / 2, cy, cw, ch);
			g.drawRect(bX[cur] - cw / 2 + 1, cy + 1, cw - 2, ch - 2);
		}
		if (toast != null) {
			g.setFont(fb);
			String tx = fit(toast, fb, w - 28);
			int tw = fb.stringWidth(tx) + 20, th = fb.getHeight() + 10, tx0 = (w - tw) / 2;
			g.setColor(0x303030);
			g.fillRoundRect(tx0, 6, tw, th, 12, 12);
			g.setColor(0xFFFFFF);
			g.drawString(tx, w / 2, 11, Graphics.TOP | Graphics.HCENTER);
		}
		if (stat != null) {
			int sw = f.stringWidth(stat) + 8;
			g.setColor(0xFFFFE0);
			g.fillRect((w - sw) / 2, h - fh - 6, sw, fh + 4);
			g.setColor(FG);
			g.drawRect((w - sw) / 2, h - fh - 6, sw, fh + 4);
			g.setFont(f);
			g.drawString(stat, w / 2, h - fh - 4, Graphics.TOP | Graphics.HCENTER);
		}
	}

	private void head(Graphics g) {
		if (sy >= headH) return;
		int y = 2 - sy;
		g.setFont(fb);
		for (int i = 0; i < hd1.length; i++, y += fh) g.drawString(hd1[i], lm, y, Graphics.TOP | Graphics.LEFT);
		g.setFont(f);
		g.setColor(TXT);
		for (int i = 0; i < hd2.length; i++, y += fh) g.drawString(hd2[i], lm, y, Graphics.TOP | Graphics.LEFT);
	}

	static String fit(String a, Font fn, int max) {
		if (fn.stringWidth(a) <= max) return a;
		int e = a.length();
		while (e > 0 && fn.stringWidth(a.substring(0, e)) + fn.stringWidth("..") > max) e--;
		return a.substring(0, e) + "..";
	}

	private static String note(int v) {
		return "C C#D D#E F F#G G#A A#B ".substring((v % 12) * 2, (v % 12) * 2 + 2).trim();
	}

	private void item(Graphics g, int i) {
		int k = aK[i], l = aLn[i], x = aX[i], x2 = aX2[i], ref = aRef[i];
		int y = ly(l) + (k < K_PM ? aLv[i] : lnTl[l] + aLv[i]) * fh, my = y + fh / 2;
		g.setFont(f);
		g.setColor(FG);
		switch (k) {
			case K_NUM:
				g.setColor(LINE);
				g.drawString(aT[i], x, y, Graphics.TOP | Graphics.LEFT);
				break;
			case K_SIG:
				g.setFont(fb);
				g.drawString(aT[i], x, y, Graphics.TOP | Graphics.LEFT);
				break;
			case K_MARK:
				g.setColor(MARK);
				g.setFont(fb);
				g.drawString(aT[i], x, y, Graphics.TOP | Graphics.LEFT);
				break;
			case K_TEMPO: {
				int r = fh / 4 + 1;
				g.fillArc(x, y + fh - r - 1, r + 1, r, 0, 360);
				g.drawLine(x + r, y + 1, x + r, y + fh - r / 2 - 1);
				g.drawString("=" + s.mTempo[ref], x + r + 2, y, Graphics.TOP | Graphics.LEFT);
				break;
			}
			case K_ALT: {
				StringBuffer b = new StringBuffer();
				int alt = s.mAlt[ref] & 0xff;
				for (int j = 0; j < 8; j++) if ((alt & (1 << j)) != 0) b.append(j + 1).append('.');
				g.drawLine(x, y + 1, x2, y + 1);
				g.drawLine(x, y + 1, x, y + fh - 1);
				g.drawString(b.toString(), x + 2, y + 1, Graphics.TOP | Graphics.LEFT);
				break;
			}
			case K_CHORD:
				g.setColor(TXT);
				g.setFont(fb);
				g.drawString(aT[i], x, y, Graphics.TOP | Graphics.LEFT);
				break;
			case K_TEXT:
				g.setColor(TXT);
				g.drawString(aT[i], x, y, Graphics.TOP | Graphics.LEFT);
				break;
			case K_REP:
				g.drawString(aT[i], x, y, Graphics.TOP | Graphics.LEFT);
				break;
			case K_PM:
			case K_LR: {
				String lab = k == K_PM ? "PM" : "LR";
				g.drawString(lab, x, y, Graphics.TOP | Graphics.LEFT);
				for (int d = x + f.stringWidth(lab) + sc; d < x2; d += 3 * sc) g.drawLine(d, my, d + sc, my);
				if (x2 > x + f.stringWidth(lab) + 2 * sc) g.drawLine(x2, my - sc - 1, x2, my + sc + 1);
				break;
			}
			case K_VIB:
				for (int d = x; d + 2 * sc <= x2; d += 2 * sc) {
					g.drawLine(d, my, d + sc, my - sc);
					g.drawLine(d + sc, my - sc, d + 2 * sc, my);
				}
				break;
			case K_BEND: {
				int bl = aLn[i], nw = numW(t.nFret[ref]);
				int ny = ly(bl) + lnTop[bl] + (t.nStr[ref] - 1) * gap;
				int xr = x2 + nw / 2 + sc, ax = x + f.stringWidth(bendLab(t.nArg[ref])) / 2;
				g.drawLine(xr, ny, ax, y + fh);
				g.drawLine(ax, y + fh, ax - sc - 1, y + fh + sc + 1);
				g.drawLine(ax, y + fh, ax + sc + 1, y + fh + sc + 1);
				g.drawString(bendLab(t.nArg[ref]), x, y, Graphics.TOP | Graphics.LEFT);
				break;
			}
			case K_TAP: {
				int fx = t.bFx[ref];
				g.drawString((fx & Track.B_TAP) != 0 ? "T" : (fx & Track.B_SLAP) != 0 ? "S" : "P", x, y, Graphics.TOP | Graphics.LEFT);
				break;
			}
			case K_TR:
				g.drawString("tr", x, y, Graphics.TOP | Graphics.LEFT);
				break;
			case K_ACC:
				g.drawLine(x, my - sc - 1, x + 4 * sc, my);
				g.drawLine(x + 4 * sc, my, x, my + sc + 1);
				break;
			case K_HACC:
				g.drawLine(x, my + sc + 1, x + 2 * sc, my - sc - 1);
				g.drawLine(x + 2 * sc, my - sc - 1, x + 4 * sc, my + sc + 1);
				break;
			case K_STAC:
				g.fillRect(x, my - sc / 2, sc + 1, sc + 1);
				break;
			case K_NH:
			case K_AH:
				g.drawString(k == K_NH ? "NH" : "AH", x, y, Graphics.TOP | Graphics.LEFT);
				break;
			case K_WBAR:
				g.drawString("w/bar", x, y, Graphics.TOP | Graphics.LEFT);
				break;
			case K_FADE:
				g.drawLine(x, my, x + 8 * sc, my - sc - 1);
				g.drawLine(x, my, x + 8 * sc, my + sc + 1);
				break;
			case K_PDOWN:
				g.drawLine(x, my + sc + 1, x, my - sc - 1);
				g.drawLine(x, my - sc - 1, x + 4 * sc, my - sc - 1);
				g.drawLine(x + 4 * sc, my - sc - 1, x + 4 * sc, my + sc + 1);
				break;
			case K_PUP:
				g.drawLine(x, my - sc - 1, x + 2 * sc, my + sc + 1);
				g.drawLine(x + 2 * sc, my + sc + 1, x + 4 * sc, my - sc - 1);
				break;
		}
	}

	private void measure(Graphics g, int m) {
		int y0 = ly(mL[m]) + lnTop[mL[m]], yb = y0 + (n - 1) * gap, x = mX[m];
		int ey0 = ly(mEL[m]) + lnTop[mEL[m]], eyb = ey0 + (n - 1) * gap, ex = mEX[m];
		int mid = y0 + ((n - 1) * gap) / 2, emid = ey0 + ((n - 1) * gap) / 2;
		g.setColor(FG);
		g.drawLine(ex, ey0, ex, eyb);
		if ((s.mFlag[m] & Song.M_DOUBLE) != 0) g.drawLine(ex - 2 * sc, ey0, ex - 2 * sc, eyb);
		if ((s.mFlag[m] & Song.M_OPEN) != 0) {
			g.fillRect(x + 1, y0, sc + 1, yb - y0 + 1);
			g.fillRect(x + 2 * sc + 2, mid - gap / 2 - sc / 2, sc + 1, sc + 1);
			g.fillRect(x + 2 * sc + 2, mid + gap / 2 - sc / 2, sc + 1, sc + 1);
		}
		if (s.mRep[m] > 0) {
			g.fillRect(ex - sc - 1, ey0, sc + 1, eyb - ey0 + 1);
			g.fillRect(ex - 3 * sc - 2, emid - gap / 2 - sc / 2, sc + 1, sc + 1);
			g.fillRect(ex - 3 * sc - 2, emid + gap / 2 - sc / 2, sc + 1, sc + 1);
		}
	}

	private boolean has(int b, int mask) {
		for (int i = t.bNote[b], e = i + t.bCnt[b]; i < e; i++) if ((t.nFx[i] & mask) != 0) return true;
		return false;
	}

	private int noteOn(int b, int str) {
		for (int i = t.bNote[b], e = i + t.bCnt[b]; i < e; i++) if (t.nStr[i] == str) return i;
		return -1;
	}

	private void beat(Graphics g, int b) {
		int l = bLn[b], y0 = ly(l) + lnTop[l], cx = bX[b];
		int dur = t.bDur[b], code = dur & 7, fx = t.bFx[b];
		int yEnd = y0 + (n - 1) * gap;
		g.setColor(FG);
		if ((fx & (Track.B_UP | Track.B_DOWN)) != 0) {
			int ax = cx - 4 * sc;
			g.drawLine(ax, y0, ax, yEnd);
			if ((fx & Track.B_DOWN) != 0) {
				g.drawLine(ax, y0, ax - sc - 1, y0 + sc + 1);
				g.drawLine(ax, y0, ax + sc + 1, y0 + sc + 1);
			} else {
				g.drawLine(ax, yEnd, ax - sc - 1, yEnd - sc - 1);
				g.drawLine(ax, yEnd, ax + sc + 1, yEnd - sc - 1);
			}
		}
		if ((dur & Track.D_REST) != 0) {
			int ry = y0 + ((n - 1) * gap) / 2;
			if (code <= 1) g.fillRect(cx - 2 * sc, code == 0 ? ry - gap / 2 : ry - sc, 4 * sc, sc + 1);
			else {
				g.drawLine(cx - sc, ry - 2 * sc, cx + sc, ry);
				g.drawLine(cx + sc, ry, cx - sc, ry + 2 * sc);
				for (int i = 3; i <= code; i++) g.fillRect(cx - sc, ry - 2 * sc - (i - 2) * sc, sc + 1, sc);
			}
			if ((dur & Track.D_DOT) != 0) g.fillRect(cx + 2 * sc, ry, sc, sc);
			return;
		}
		for (int i = t.bNote[b], e = i + t.bCnt[b]; i < e; i++) noteDraw(g, b, i, cx, y0);
		if ((dur & Track.D_V2) == 0) rhythm(g, b, cx, yEnd, code, dur);
	}

	private void noteDraw(Graphics g, int b, int i, int cx, int y0) {
		int str = t.nStr[i], fret = t.nFret[i], nfx = t.nFx[i];
		int y = y0 + (str - 1) * gap;
		boolean dead = (nfx & Track.N_DEAD) != 0;
		int nw = dead ? 3 * sc : numW(fret);
		if (dead) glyph(g, 10, cx, y);
		else num(g, fret, cx, y);
		g.setColor(FG);
		int hh = (5 * sc) / 2, xl = cx - nw / 2 - sc - 1, xr = cx + nw / 2 + sc;
		if ((nfx & (Track.N_TIE | Track.N_GHOST)) != 0) {
			g.drawLine(xl, y - hh + 1, xl, y + hh - 1);
			g.drawLine(xr, y - hh + 1, xr, y + hh - 1);
		} else if ((nfx & (Track.N_NH | Track.N_AH)) != 0) {
			g.drawLine(xl, y, xl + sc + 1, y - hh);
			g.drawLine(xl, y, xl + sc + 1, y + hh);
			g.drawLine(xr, y, xr - sc - 1, y - hh);
			g.drawLine(xr, y, xr - sc - 1, y + hh);
		}
		int gr = t.nGrace[i];
		if (gr != -1) {
			int gx = xl - 2 * sc - (gr == -2 ? 3 * sc : numW(gr)) / 2;
			if (gr == -2) glyph(g, 10, gx, y - gap / 2);
			else num(g, gr, gx, y - gap / 2);
			g.setColor(FG);
		}
		if ((nfx & (Track.N_HAM | Track.N_SLIDE)) != 0) {
			int x2 = -1, f2 = fret;
			for (int k = b + 1, lim = Math.min(t.beats, b + 24); k < lim; k++) {
				int j = noteOn(k, str);
				if (j >= 0) {
					x2 = bLn[k] == bLn[b] ? bX[k] - numW(t.nFret[j]) / 2 - sc : lnEnd[bLn[b]];
					f2 = t.nFret[j];
					break;
				}
			}
			if (x2 > xr) {
				if ((nfx & Track.N_HAM) != 0) g.drawArc(cx, y - gap / 2 - sc, x2 - cx, gap / 2, 0, 180);
				else if (f2 >= fret) g.drawLine(xr, y + sc, x2, y - sc);
				else g.drawLine(xr, y - sc, x2, y + sc);
			}
		}
	}

	private static String bendLab(int q) {
		if (q == 4) return "full";
		if (q == 2) return "1/2";
		if (q == 1) return "1/4";
		if (q % 4 == 0) return String.valueOf(q / 4);
		if (q % 4 == 2) return (q / 4) + " 1/2";
		return q + "/4";
	}

	private boolean beamable(int b) {
		if (b < 0 || b >= t.beats) return false;
		int d = t.bDur[b];
		return (d & (Track.D_REST | Track.D_V2)) == 0 && (d & 7) >= 3;
	}

	private boolean linked(int a, int b) {
		if (!beamable(a) || !beamable(b) || bLn[a] != bLn[b]) return false;
		if (measureOf(a) != measureOf(b)) return false;
		return t.bPos[a] / 960 == t.bPos[b] / 960;
	}

	int measureOf(int b) {
		int lo = 0, hi = s.mCount - 1;
		while (lo < hi) {
			int mid = (lo + hi + 1) >> 1;
			if (t.mBeat[mid] <= b) lo = mid;
			else hi = mid - 1;
		}
		return lo;
	}

	private int nextV1(int b) {
		int k = b + 1;
		while (k < t.beats && (t.bDur[k] & Track.D_V2) != 0) k++;
		return k;
	}

	private int prevV1(int b) {
		int k = b - 1;
		while (k >= 0 && (t.bDur[k] & Track.D_V2) != 0) k--;
		return k;
	}

	private void rhythm(Graphics g, int b, int cx, int yEnd, int code, int dur) {
		g.setColor(FG);
		int y1 = yEnd + 3 * sc, y2 = y1 + stem;
		if (code == 0) return;
		g.drawLine(cx, code == 1 ? y1 + stem / 2 : y1, cx, y2);
		if ((dur & Track.D_DOT) != 0) g.fillRect(cx + sc + 1, y2 - sc, sc, sc);
		if (has(b, Track.N_TREMP))
			for (int i = 0; i < 3; i++) g.drawLine(cx - sc - 1, y1 + stem / 2 + i * 2 - 1, cx + sc + 1, y1 + stem / 2 + i * 2 - 3);
		int tup = t.bTup[b];
		if (tup > 0) {
			int p = prevV1(b);
			if (p < 0 || t.bTup[p] != tup || bLn[p] != bLn[b]) num(g, tup, cx, y2 + 4 * sc);
			g.setColor(FG);
		}
		if (code < 3) return;
		int k = code - 2, nx = nextV1(b), pv = prevV1(b);
		boolean toNext = nx < t.beats && linked(b, nx), fromPrev = pv >= 0 && linked(pv, b);
		if (toNext) {
			int k2 = (t.bDur[nx] & 7) - 2, common = Math.min(k, k2);
			for (int i = 0; i < common; i++) g.fillRect(cx, y2 - i * 2 * sc - sc, bX[nx] - cx + 1, sc);
			for (int i = common; i < k; i++) g.fillRect(cx, y2 - i * 2 * sc - sc, 3 * sc, sc);
		} else if (fromPrev) {
			int k2 = (t.bDur[pv] & 7) - 2;
			for (int i = k2; i < k; i++) g.fillRect(cx - 3 * sc, y2 - i * 2 * sc - sc, 3 * sc, sc);
		} else {
			for (int i = 0; i < k; i++) g.drawLine(cx, y2 - i * 2 * sc, cx + 2 * sc, y2 - i * 2 * sc - 2 * sc);
		}
	}

	private void scrollTo(int y) {
		int max = headH + lnY[lines] - h;
		if (y > max) y = max;
		if (y < 0) y = 0;
		sy = y;
		repaint();
	}

	private void show(int b) {
		cur = b;
		int l = bLn[b], y = headH + lnY[l], hh = lnY[l + 1] - lnY[l];
		if (y < sy || y + hh > sy + h) scrollTo(!playing && y > hh / 2 ? y - hh / 2 : y);
		else repaint();
	}

	private int next(int b, int d) {
		for (int k = b + d; k >= 0 && k < t.beats; k += d)
			if ((t.bDur[k] & Track.D_V2) == 0) return k;
		return b;
	}

	private int nearest(int line, int x) {
		if (line < 0 || line >= lines) return -1;
		int lo = 0, hi = s.mCount - 1;
		while (lo < hi) {
			int mid = (lo + hi) >> 1;
			if (mEL[mid] < line) lo = mid + 1;
			else hi = mid;
		}
		int best = -1, bd = Integer.MAX_VALUE;
		for (int m = lo; m < s.mCount && mL[m] <= line; m++) {
			for (int k = t.mBeat[m]; k < t.mBeat[m + 1]; k++) {
				if (bLn[k] != line || (t.bDur[k] & Track.D_V2) != 0) continue;
				int d = Math.abs(bX[k] - x);
				if (d < bd) {
					bd = d;
					best = k;
				}
			}
		}
		return best;
	}

	protected synchronized void keyPressed(int k) {
		fling.stop();
		key(k, false);
	}

	protected synchronized void keyRepeated(int k) {
		key(k, true);
	}

	private void key(int k, boolean rep) {
		stat = null;
		if (busy != null || t == null) {
			if (!rep && (busy == null || failed)) {
				busy = null;
				app.nav(App.MENU);
			}
			return;
		}
		switch (k) {
			case KEY_NUM1: if (!rep) app.nav(App.PREV); return;
			case KEY_NUM3: if (!rep) app.nav(App.NEXT); return;
			case KEY_NUM7: scrollTo(sy - h + fh); return;
			case KEY_NUM9: scrollTo(sy + h - fh); return;
			case KEY_NUM0:
				if (!playing) {
					cur = next(-1, 1);
					scrollTo(0);
				}
				return;
			case KEY_STAR:
				if (!rep) {
					sc = sc % 3 + 1;
					relayout();
				}
				return;
			case KEY_POUND:
				if (!rep) rotate();
				return;
		}
		if (k == -6 || k == -7 || k == -21 || k == -22) {
			if (!rep) app.nav(App.MENU);
			return;
		}
		int a;
		try {
			a = getGameAction(k);
		} catch (IllegalArgumentException e) {
			a = 0;
		}
		if (a == FIRE || k == KEY_NUM5) {
			if (!rep) app.nav(App.PLAY);
			return;
		}
		if (playing || cur < 0) {
			repaint();
			return;
		}
		int dir = a == UP || k == KEY_NUM2 ? 1 : a == RIGHT || k == KEY_NUM6 ? 2 : a == DOWN || k == KEY_NUM8 ? 3 : a == LEFT || k == KEY_NUM4 ? 4 : 0;
		if (dir == 0) {
			repaint();
			return;
		}
		if (rot == 1) dir = dir == 1 ? 4 : dir - 1;
		else if (rot == 2) dir = dir == 4 ? 1 : dir + 1;
		if (dir == 4) show(next(cur, -1));
		else if (dir == 2) show(next(cur, 1));
		else {
			int d = dir == 1 ? -1 : 1;
			int b = nearest(bLn[cur] + d, bX[cur]);
			if (b >= 0) show(b);
			else scrollTo(sy + d * h / 3);
		}
	}

	private int lx(int x, int y) {
		return rot == 0 ? x : rot == 1 ? y : getHeight() - 1 - y;
	}

	private int lyy(int x, int y) {
		return rot == 0 ? y : rot == 1 ? getWidth() - 1 - x : x;
	}

	private void toolbar(Graphics g) {
		if (bar == 0) return;
		boolean no = t == null;
		Bar.draw(g, vh() - bar, w, bar, new String[] { Bar.MENU, Bar.PREV, playing ? Bar.STOP : Bar.PLAY, Bar.NEXT, "x" + sc },
				new boolean[] { false, no, no, no, no }, pressed);
	}

	private void button(int i) {
		if (i == 0) {
			busy = null;
			app.nav(App.MENU);
			return;
		}
		if (t == null) return;
		if (i == 1) app.nav(App.PREV);
		else if (i == 2) app.nav(App.PLAY);
		else if (i == 3) app.nav(App.NEXT);
		else {
			sc = sc % 3 + 1;
			relayout();
		}
	}

	public boolean scrollBy(int dy) {
		if (t == null) return false;
		int old = sy;
		scrollTo(sy + dy);
		return sy != old;
	}

	protected synchronized void pointerPressed(int x, int y) {
		int ly = lyy(x, y);
		fling.press(ly);
		barDown = bar > 0 && ly >= vh() - bar;
		if (barDown) {
			pressed = Bar.hit(lx(x, y), vw(), 5);
			repaint();
		}
	}

	protected synchronized void pointerDragged(int x, int y) {
		if (!barDown) fling.drag(lyy(x, y));
	}

	protected synchronized void pointerReleased(int x, int y) {
		if (barDown) {
			barDown = false;
			pressed = -1;
			repaint();
			if (lyy(x, y) >= vh() - bar && (busy == null || failed)) button(Bar.hit(lx(x, y), vw(), 5));
			return;
		}
		if (t == null) {
			if (busy == null || failed) {
				busy = null;
				app.nav(App.MENU);
			}
			return;
		}
		if (fling.dragged()) {
			fling.release(!playing);
			return;
		}
		if (playing) return;
		int yy = lyy(x, y) + sy - headH;
		if (yy < 0) return;
		int b = nearest(lineOf(yy), lx(x, y));
		if (b >= 0) {
			cur = b;
			repaint();
		}
	}
}
