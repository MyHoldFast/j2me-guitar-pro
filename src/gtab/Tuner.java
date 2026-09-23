package gtab;

import java.io.ByteArrayInputStream;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;

final class Tuner extends Canvas implements Runnable {
	private static final String[] PN = {
		L.s("Стандарт E", "Standard E"), L.s("Полтона ниже (Eb)", "Half step down (Eb)"), L.s("Тон ниже (D)", "Whole step down (D)"), "Drop D", "Drop C", "Open G", "Open D", "Open E", "DADGAD",
		L.s("7 струн: стандарт B", "7-string: standard B"), L.s("7 струн: Drop A", "7-string: Drop A"), L.s("Бас: EADG", "Bass: EADG"), L.s("Бас: полтона ниже", "Bass: half step down"), L.s("Бас: Drop D", "Bass: Drop D"), L.s("Бас 5: BEADG", "Bass 5: BEADG")
	};
	private static final int[][] PV = {
		{ 64, 59, 55, 50, 45, 40 }, { 63, 58, 54, 49, 44, 39 }, { 62, 57, 53, 48, 43, 38 }, { 64, 59, 55, 50, 45, 38 },
		{ 62, 57, 53, 48, 43, 36 }, { 62, 59, 55, 50, 43, 38 }, { 62, 57, 54, 50, 45, 38 }, { 64, 59, 56, 52, 47, 40 },
		{ 62, 57, 55, 50, 45, 38 }, { 64, 59, 55, 50, 45, 40, 35 }, { 64, 59, 55, 50, 45, 40, 33 },
		{ 43, 38, 33, 28 }, { 42, 37, 32, 27 }, { 43, 38, 33, 26 }, { 43, 38, 33, 28, 23 }
	};
	private static final double[] R = {
		1.0, 1.0594630943592953, 1.122462048309373, 1.189207115002721, 1.2599210498948732, 1.3348398541700344,
		1.4142135623730951, 1.4983070768766815, 1.5874010519681994, 1.681792830507429, 1.7817974362806785, 1.8877486436268287
	};
	private static final String[] NN = { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" };
	private static final String[] HINTS = { L.s("5 звук вкл/выкл", "5 sound on/off"), L.s("2/8 струна", "2/8 string"), L.s("4/6 \u00b1полтона", "4/6 \u00b1semitone"), L.s("1/3 строй", "1/3 tuning"), L.s("0 сброс", "0 reset"), L.s("* октава", "* octave"), L.s("# тембр", "# timbre") };
	private static final String[] SHORT = { L.s("5 звук", "5 sound"), L.s("1/3 строй", "1/3 tuning"), "4/6 \u00b11/2" };

	private final App app;
	private final Font fs = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
	private final Font fb = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL);
	private final Font fl = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_LARGE);

	private int[] own, cur;
	private String ownName, msg;
	private int capo, preset, sel, oct, rowY, rowH, headY;
	private boolean tone, custom;
	private volatile boolean on;
	private volatile int gen;
	private Player p;

	private final int bar;
	private int pressed = -1;

	Tuner(App app) {
		this.app = app;
		setFullScreenMode(true);
		bar = hasPointerEvents() ? Bar.height() : 0;
	}

	synchronized void open(byte[] tune, String name, int capo) {
		stop();
		msg = null;
		oct = 0;
		tone = false;
		if (tune == null) {
			own = null;
			ownName = null;
			this.capo = 0;
			preset = 0;
		} else {
			own = new int[tune.length];
			for (int i = 0; i < tune.length; i++) own[i] = tune[i];
			ownName = name;
			this.capo = capo;
			preset = -1;
		}
		load();
		sel = cur.length - 1;
	}

	private void load() {
		int[] src = preset < 0 ? own : PV[preset];
		cur = new int[src.length];
		System.arraycopy(src, 0, cur, 0, src.length);
		custom = false;
		if (sel >= cur.length) sel = cur.length - 1;
	}

	private void nextPreset(int d) {
		int i = preset;
		for (int k = 0; k <= PV.length; k++) {
			i += d;
			if (i < -1) i = PV.length - 1;
			if (i >= PV.length) i = -1;
			if (i < 0 ? own != null : own == null || PV[i].length == own.length) break;
		}
		preset = i;
		load();
	}

	private String name() {
		return custom ? L.s("Свой строй", "Custom") : preset < 0 ? L.s("Как в треке", "As in track") : PN[preset];
	}

	private static String noteName(int n) {
		return NN[n % 12] + (n / 12 - 1);
	}

	private static String freq(int n) {
		int d = n - 69, o = d >= 0 ? d / 12 : -((11 - d) / 12);
		double f = 440.0 * R[d - o * 12];
		for (; o > 0; o--) f *= 2;
		for (; o < 0; o++) f /= 2;
		long v = (long) (f * 10 + 0.5);
		return (v / 10) + "." + (v % 10) + L.s(" Гц", " Hz");
	}

	protected synchronized void paint(Graphics g) {
		int w = getWidth(), h = getHeight(), fh = fs.getHeight();
		g.setColor(0xFFFFFF);
		g.fillRect(0, 0, w, h);
		int y = 2;
		g.setColor(0x000000);
		g.setFont(fb);
		g.drawString(L.s("Тюнер", "Tuner"), 3, y, Graphics.TOP | Graphics.LEFT);
		g.setFont(fs);
		g.setColor(0x606060);
		g.drawString((tone ? L.s("тон", "tone") : L.s("гитара", "guitar")) + (oct > 0 ? " +" + oct + L.s(" окт", " oct") : ""), w - 3, y, Graphics.TOP | Graphics.RIGHT);
		y += fh + 2;
		headY = y + fh;
		g.setColor(0x0000A0);
		g.setFont(fb);
		g.drawString("<  " + View.fit(name(), fb, w - 6 - 2 * fb.stringWidth("<  ")) + "  >", w / 2, y, Graphics.TOP | Graphics.HCENTER);
		y += fh;
		if (preset < 0 && !custom && ownName != null) {
			g.setFont(fs);
			g.setColor(0x606060);
			String o = View.fit(ownName + (capo > 0 ? ", capo " + capo + L.s(" (строй без капо)", " (tuning without capo)") : ""), fs, w - 6);
			g.drawString(o, w / 2, y, Graphics.TOP | Graphics.HCENTER);
			y += fh;
		}
		y += 2;
		h -= bar;
		int n = cur.length, need = n * (fb.getHeight() + 1) + (msg != null ? fh : 0) + 3;
		String[] hints = flow(HINTS, w - 6);
		if (bar > 0) hints = new String[0];
		if (h - y - hints.length * fh < need) hints = flow(SHORT, w - 6);
		if (h - y - hints.length * fh < need) hints = new String[0];
		int bottom = h - hints.length * fh - 3 - (msg != null ? fh : 0);
		rowH = (bottom - y) / n;
		Font nf = rowH >= fl.getHeight() + 2 ? fl : fb;
		if (rowH > nf.getHeight() * 2) rowH = nf.getHeight() * 2;
		rowY = y;
		for (int i = 0; i < n; i++) {
			int ry = y + i * rowH, my = ry + rowH / 2;
			if (i == sel) {
				g.setColor(on ? 0xFFE0C0 : 0xD8E4FF);
				g.fillRect(0, ry, w, rowH);
			}
			g.setColor(0x808080);
			g.setFont(fs);
			g.drawString(String.valueOf(i + 1), 4, my - fh / 2, Graphics.TOP | Graphics.LEFT);
			if (i == sel && on) {
				g.setColor(0xC04000);
				int tx = 4 + fs.stringWidth("0") + 3;
				g.fillTriangle(tx, my - 4, tx, my + 4, tx + 6, my);
			}
			int nx = w / 4;
			g.setColor(0x000000);
			g.setFont(nf);
			g.drawString(noteName(cur[i]), nx, my - nf.getHeight() / 2, Graphics.TOP | Graphics.LEFT);
			int dx = nx + nf.stringWidth("C#0") + 4;
			if (own != null && own.length == n && cur[i] != own[i]) {
				int dd = cur[i] - own[i];
				g.setFont(fb);
				g.setColor(0xC00000);
				g.drawString((dd > 0 ? "+" : "") + dd, dx, my - fh / 2, Graphics.TOP | Graphics.LEFT);
			}
			g.setFont(fs);
			g.setColor(0x404040);
			g.drawString(freq(cur[i]), w - 4, my - fh / 2, Graphics.TOP | Graphics.RIGHT);
		}
		g.setFont(fs);
		int hy = h - hints.length * fh - 2;
		if (msg != null) {
			g.setColor(0xC00000);
			g.drawString(View.fit(msg, fs, w - 6), w / 2, hy - fh, Graphics.TOP | Graphics.HCENTER);
		}
		g.setColor(0x808080);
		if (hints.length > 0) g.drawLine(0, hy - 1, w, hy - 1);
		for (int i = 0; i < hints.length; i++) g.drawString(hints[i], 3, hy + i * fh, Graphics.TOP | Graphics.LEFT);
		if (bar > 0)
			Bar.draw(g, h, w, bar, new String[] { Bar.BACK, oct > 0 ? L.s("Окт +", "Oct +") + oct : L.s("Октава", "Octave"), tone ? L.s("Тон", "Tone") : L.s("Гитара", "Guitar"), L.s("Сброс", "Reset") }, null, pressed);
	}

	private String[] flow(String[] hs, int max) {
		String[] out = new String[hs.length];
		int c = 0;
		String line = null;
		for (int i = 0; i < hs.length; i++) {
			String next = line == null ? hs[i] : line + "   " + hs[i];
			if (line != null && fs.stringWidth(next) > max) {
				out[c++] = line;
				line = hs[i];
			} else line = next;
		}
		out[c++] = line;
		String[] r = new String[c];
		System.arraycopy(out, 0, r, 0, c);
		return r;
	}

	protected void keyPressed(int k) {
		key(k);
	}

	protected void keyRepeated(int k) {
		int a = action(k);
		if (a == UP || a == DOWN || a == LEFT || a == RIGHT || k == KEY_NUM2 || k == KEY_NUM8 || k == KEY_NUM4 || k == KEY_NUM6) key(k);
	}

	private int action(int k) {
		try {
			return getGameAction(k);
		} catch (IllegalArgumentException e) {
			return 0;
		}
	}

	private synchronized void key(int k) {
		msg = null;
		if (k == -6 || k == -7 || k == -21 || k == -22) {
			stop();
			app.nav(App.MENU);
			return;
		}
		int a = action(k), n = cur.length;
		boolean resound = true;
		if (k == KEY_NUM5 || a == FIRE) {
			if (on) stop();
			else start();
			resound = false;
		} else if (k == KEY_NUM2 || a == UP) sel = sel > 0 ? sel - 1 : n - 1;
		else if (k == KEY_NUM8 || a == DOWN) sel = sel < n - 1 ? sel + 1 : 0;
		else if (k == KEY_NUM4 || a == LEFT) {
			if (cur[sel] > 12) cur[sel]--;
			custom = true;
		} else if (k == KEY_NUM6 || a == RIGHT) {
			if (cur[sel] < 108) cur[sel]++;
			custom = true;
		} else if (k == KEY_NUM1) nextPreset(-1);
		else if (k == KEY_NUM3) nextPreset(1);
		else if (k == KEY_NUM0) load();
		else if (k == KEY_STAR) oct = (oct + 1) % 3;
		else if (k == KEY_POUND) tone = !tone;
		else resound = false;
		if (resound && on) start();
		repaint();
	}

	protected synchronized void pointerPressed(int x, int y) {
		if (bar > 0 && y >= getHeight() - bar) {
			pressed = Bar.hit(x, getWidth(), 4);
			repaint();
			return;
		}
		if (y < headY) {
			key(x < getWidth() / 2 ? KEY_NUM1 : KEY_NUM3);
			return;
		}
		int i = rowH > 0 ? (y - rowY) / rowH : -1;
		if (y < rowY || i >= cur.length) return;
		if (i == sel) key(KEY_NUM5);
		else {
			sel = i;
			if (on) start();
			repaint();
		}
	}

	protected synchronized void pointerReleased(int x, int y) {
		if (pressed < 0) return;
		int i = pressed;
		pressed = -1;
		repaint();
		if (y >= getHeight() - bar && Bar.hit(x, getWidth(), 4) == i) key(i == 0 ? -7 : i == 1 ? KEY_STAR : i == 2 ? KEY_POUND : KEY_NUM0);
	}

	protected void hideNotify() {
		stop();
	}

	synchronized void stop() {
		on = false;
		gen++;
		close();
	}

	private void start() {
		on = true;
		gen++;
		close();
		new Thread(this).start();
	}

	private void close() {
		Player pl = p;
		p = null;
		if (pl != null) {
			try {
				pl.close();
			} catch (Throwable e) {
			}
		}
	}

	public void run() {
		int g, note, low = 127;
		boolean pure;
		synchronized (this) {
			g = gen;
			note = cur[sel] + 12 * oct;
			pure = tone;
			for (int i = 0; i < cur.length; i++) if (cur[i] < low) low = cur[i];
		}
		if (note > 127) note = 127;
		try {
			if (pure) {
				while (on && g == gen) {
					Manager.playTone(note, 1500, 100);
					Thread.sleep(1800);
				}
			} else {
				Player pl = Manager.createPlayer(new ByteArrayInputStream(midi(note, low < 36 ? 33 : 25)), "audio/midi");
				pl.setLoopCount(-1);
				pl.realize();
				synchronized (this) {
					if (g != gen) {
						pl.close();
						return;
					}
					p = pl;
				}
				pl.start();
			}
		} catch (Throwable e) {
			synchronized (this) {
				if (g != gen) return;
				on = false;
				String m = e.getMessage();
				msg = m != null && m.toLowerCase().indexOf("not allowed") >= 0 ? L.s("Звук запрещён профилем", "Sound disabled by profile") : e.toString();
			}
			repaint();
		}
	}

	private static byte[] midi(int note, int prog) {
		return new byte[] {
			'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0x03, (byte) 0xC0,
			'M', 'T', 'r', 'k', 0, 0, 0, 17,
			0, (byte) 0xC0, (byte) prog,
			0, (byte) 0x90, (byte) note, 110,
			(byte) 0x9E, 0x00, (byte) 0x80, (byte) note, 0x40,
			(byte) 0x86, 0x00, (byte) 0xFF, 0x2F, 0x00
		};
	}
}
